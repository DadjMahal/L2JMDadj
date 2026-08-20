package com.aiplayer.behavior.hunting;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.EntitySnapshot;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.quest.ZoneRecommender;
import com.aiplayer.core.BotSnapshot;

/**
 * Tracks mob density, player density, and competition levels
 * across farming zones in real-time.
 *
 * Uses GameStateMirror entity snapshots to build a spatial
 * heatmap of mob presence and player competition.
 *
 * Integration: AIBrain polls every 5s. ZoneRecommender uses
 * density scores for zone ranking.
 */
public final class ZoneDensityTracker {

    // Zone definition: center + radius
    public static final class Zone {
        public final String name;
        public final int centerX;
        public final int centerY;
        public final int centerZ;
        public final int radius;
        public final int minLevel;
        public final int maxLevel;
        public final boolean isSolo;
        public final boolean isDangerous;
        public final Set<Integer> preferredMobIds; // empty = any

        public Zone(String name, int centerX, int centerY, int centerZ, int radius,
                    int minLevel, int maxLevel, boolean isSolo, boolean isDangerous,
                    Set<Integer> preferredMobIds) {
            this.name = name;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.isSolo = isSolo;
            this.isDangerous = isDangerous;
            this.preferredMobIds = preferredMobIds != null
                ? Collections.unmodifiableSet(preferredMobIds)
                : Collections.emptySet();
        }

        public boolean contains(int x, int y, int z) {
            double dist = Math.hypot(x - centerX, y - centerY);
            return dist <= radius && Math.abs(z - centerZ) < 500;
        }

        public double distanceFrom(int x, int y) {
            return Math.hypot(x - centerX, y - centerY);
        }
    }

    // Per-zone density snapshot
    public static final class DensitySnapshot {
        public final String zoneName;
        public final int mobCount;
        public final int playerCount;
        public final int aliveMobCount;
        public final double mobsPerArea; // mobs per 1000x1000 units
        public final double competitionRatio; // players per mob
        public final long timestamp;

        public DensitySnapshot(String zoneName, int mobCount, int playerCount,
                               int aliveMobCount, double mobsPerArea,
                               double competitionRatio, long timestamp) {
            this.zoneName = zoneName;
            this.mobCount = mobCount;
            this.playerCount = playerCount;
            this.aliveMobCount = aliveMobCount;
            this.mobsPerArea = mobsPerArea;
            this.competitionRatio = competitionRatio;
            this.timestamp = timestamp;
        }

        public boolean isOvercrowded() {
            return competitionRatio > 2.0 || playerCount > 8;
        }

        public boolean isDepleted() {
            return aliveMobCount < 3 && mobCount > 5;
        }
    }

    private final List<Zone> zones;
    private final Map<String, DensitySnapshot> latestSnapshots = new ConcurrentHashMap<>();
    private final Map<String, List<DensitySnapshot>> history = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PER_ZONE = 12; // 1 minute at 5s intervals

    public ZoneDensityTracker(List<Zone> zones) {
        this.zones = new ArrayList<>(zones);
        for (Zone z : zones) {
            history.put(z.name, new ArrayList<>());
        }
    }

    /**
     * Main tick — call every 5s from AIBrain.
     * Scans GameStateMirror entities and updates all zone densities.
     */
    public void tick(GameStateMirror mirror, BotStateSnapshot self) {
        List<EntitySnapshot> entities = mirror.getVisibleEntities();
        long now = System.currentTimeMillis();

        for (Zone zone : zones) {
            if (!zone.contains(self.x, self.y, self.z)) {
                // Only update zones we're in or very near
                if (zone.distanceFrom(self.x, self.y) > zone.radius * 2) {
                    continue;
                }
            }

            AtomicInteger mobCount = new AtomicInteger(0);
            AtomicInteger aliveMobCount = new AtomicInteger(0);
            AtomicInteger playerCount = new AtomicInteger(0);

            for (EntitySnapshot e : entities) {
                if (!zone.contains(e.x, e.y, e.z)) continue;

                if (e.isPlayer) {
                    if (!e.name.equalsIgnoreCase(self.name)) {
                        playerCount.incrementAndGet();
                    }
                } else if (e.isMob) {
                    mobCount.incrementAndGet();
                    if (!e.isDead) {
                        aliveMobCount.incrementAndGet();
                    }
                }
            }

            double area = (Math.PI * zone.radius * zone.radius) / 1_000_000.0;
            double mobsPerArea = area > 0 ? aliveMobCount.get() / area : 0;
            double competition = aliveMobCount.get() > 0
                ? (double) playerCount.get() / aliveMobCount.get()
                : playerCount.get();

            DensitySnapshot snap = new DensitySnapshot(
                zone.name,
                mobCount.get(),
                playerCount.get(),
                aliveMobCount.get(),
                mobsPerArea,
                competition,
                now
            );

            latestSnapshots.put(zone.name, snap);

            List<DensitySnapshot> hist = history.get(zone.name);
            hist.add(snap);
            while (hist.size() > MAX_HISTORY_PER_ZONE) {
                hist.remove(0);
            }
        }
    }

    /**
     * Get current density for a zone.
     */
    public DensitySnapshot getDensity(String zoneName) {
        return latestSnapshots.get(zoneName);
    }

    /**
     * Get trend for a zone: -1 = worsening, 0 = stable, 1 = improving.
     * Based on mob count change over last 30s.
     */
    public int getTrend(String zoneName) {
        List<DensitySnapshot> hist = history.get(zoneName);
        if (hist == null || hist.size() < 3) return 0;

        DensitySnapshot recent = hist.get(hist.size() - 1);
        DensitySnapshot older = hist.get(Math.max(0, hist.size() - 3));

        double diff = recent.aliveMobCount - older.aliveMobCount;
        if (diff > 3) return 1;
        if (diff < -3) return -1;
        return 0;
    }

    /**
     * Check if a zone is currently viable for farming.
     */
    public boolean isViable(String zoneName, BotStateSnapshot self) {
        Zone zone = findZone(zoneName);
        if (zone == null) return false;
        if (self.level < zone.minLevel || self.level > zone.maxLevel) return false;

        DensitySnapshot snap = latestSnapshots.get(zoneName);
        if (snap == null) return true; // Unknown = give it a try

        return !snap.isOvercrowded() && !snap.isDepleted();
    }

    /**
     * Find all zones matching level range and solo/party preference.
     */
    public List<Zone> findEligibleZones(int level, boolean preferSolo) {
        List<Zone> result = new ArrayList<>();
        for (Zone z : zones) {
            if (level >= z.minLevel && level <= z.maxLevel) {
                if (preferSolo && z.isSolo) {
                    result.add(z);
                } else if (!preferSolo && !z.isSolo) {
                    result.add(z);
                } else if (preferSolo == z.isSolo) {
                    result.add(z);
                }
            }
        }
        return result;
    }

    public Zone findZone(String name) {
        for (Zone z : zones) {
            if (z.name.equalsIgnoreCase(name)) return z;
        }
        return null;
    }

    public Zone findZoneByPosition(int x, int y, int z) {
        for (Zone zone : zones) {
            if (zone.contains(x, y, z)) return zone;
        }
        return null;
    }

    public List<String> getOvercrowdedZones() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, DensitySnapshot> e : latestSnapshots.entrySet()) {
            if (e.getValue().isOvercrowded()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    public List<String> getDepletedZones() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, DensitySnapshot> e : latestSnapshots.entrySet()) {
            if (e.getValue().isDepleted()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    public void clearHistory(String zoneName) {
        List<DensitySnapshot> hist = history.get(zoneName);
        if (hist != null) hist.clear();
        latestSnapshots.remove(zoneName);
    }

    public String getStatusReport() {
        StringBuilder sb = new StringBuilder("ZoneDensity[");
        for (Map.Entry<String, DensitySnapshot> e : latestSnapshots.entrySet()) {
            DensitySnapshot s = e.getValue();
            sb.append(String.format("%s:m%d/p%d/a%d ",
                s.zoneName, s.mobCount, s.playerCount, s.aliveMobCount));
        }
        sb.append("]");
        return sb.toString();
    }
}
