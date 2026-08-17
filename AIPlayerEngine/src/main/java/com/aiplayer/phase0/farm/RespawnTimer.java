package com.aiplayer.phase0.farm;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.EntitySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks mob respawn timing per zone and per spawn point.
 * Builds a learned model of respawn intervals for each zone
 * to predict when mobs will respawn and optimize camp timing.
 *
 * Integration: Called when mobs die (CombatAI kill confirm).
 * FarmZoneScorer uses predicted respawn for spot ranking.
 */
public final class RespawnTimer {

    // Per-mob-template respawn data
    public static final class MobRespawnData {
        public final int mobTemplateId;
        public final String mobName;
        public long estimatedRespawnMs;     // learned average
        public long minObservedMs;
        public long maxObservedMs;
        public int sampleCount;
        public long lastDeathTime;
        public long predictedRespawnTime;   // next expected respawn

        public MobRespawnData(int mobTemplateId, String mobName) {
            this.mobTemplateId = mobTemplateId;
            this.mobName = mobName;
            this.estimatedRespawnMs = 60000;  // default 60s guess
            this.minObservedMs = Long.MAX_VALUE;
            this.maxObservedMs = 0;
            this.sampleCount = 0;
        }

        public void recordDeath(long deathTime) {
            if (lastDeathTime > 0) {
                long interval = deathTime - lastDeathTime;
                if (interval > 5000 && interval < 600000) { // sanity: 5s to 10m
                    sampleCount++;
                    estimatedRespawnMs = (estimatedRespawnMs * (sampleCount - 1) + interval) / sampleCount;
                    minObservedMs = Math.min(minObservedMs, interval);
                    maxObservedMs = Math.max(maxObservedMs, interval);
                }
            }
            lastDeathTime = deathTime;
            predictedRespawnTime = deathTime + estimatedRespawnMs;
        }

        public long getTimeUntilRespawn(long now) {
            return predictedRespawnTime - now;
        }

        public boolean isRespawnImminent(long now, long windowMs) {
            long remaining = getTimeUntilRespawn(now);
            return remaining > 0 && remaining < windowMs;
        }
    }

    // Per-spawn-point tracking (approximate location clustering)
    public static final class SpawnPoint {
        public final int approxX;
        public final int approxY;
        public final int approxZ;
        public final int mobTemplateId;
        public long lastSeenAlive;
        public long lastSeenDead;
        public boolean isCurrentlyDead;
        public int killCount;

        public SpawnPoint(int approxX, int approxY, int approxZ, int mobTemplateId) {
            this.approxX = approxX;
            this.approxY = approxY;
            this.approxZ = approxZ;
            this.mobTemplateId = mobTemplateId;
        }

        public boolean matches(int x, int y, int z, int templateId, int tolerance) {
            return this.mobTemplateId == templateId
                && Math.abs(x - approxX) < tolerance
                && Math.abs(y - approxY) < tolerance
                && Math.abs(z - approxZ) < 200;
        }
    }

    private final Map<Integer, MobRespawnData> mobData = new ConcurrentHashMap<>();
    private final List<SpawnPoint> spawnPoints = new ArrayList<>();
    private static final int SPAWN_TOLERANCE = 300;
    private static final int MAX_SPAWN_POINTS = 200;

    /**
     * Record a mob death. Called from CombatAI on kill confirmation.
     */
    public void onMobDeath(int mobTemplateId, String mobName,
                           int deathX, int deathY, int deathZ, long deathTime) {
        MobRespawnData data = mobData.computeIfAbsent(
            mobTemplateId, k -> new MobRespawnData(mobTemplateId, mobName));
        data.recordDeath(deathTime);

        // Update or create spawn point
        SpawnPoint point = findOrCreateSpawnPoint(deathX, deathY, deathZ, mobTemplateId);
        point.lastSeenDead = deathTime;
        point.isCurrentlyDead = true;
        point.killCount++;
    }

    /**
     * Record a mob seen alive. Called from GameStateMirror entity scan.
     */
    public void onMobSeenAlive(EntitySnapshot mob, long now) {
        SpawnPoint point = findSpawnPoint(mob.x, mob.y, mob.z, mob.templateId);
        if (point != null) {
            point.lastSeenAlive = now;
            point.isCurrentlyDead = false;
        }
    }

    /**
     * Get estimated respawn time for a mob template.
     */
    public MobRespawnData getRespawnData(int mobTemplateId) {
        return mobData.get(mobTemplateId);
    }

    /**
     * Get all spawn points for a zone (by approximate bounds).
     */
    public List<SpawnPoint> getSpawnPointsInZone(int centerX, int centerY, int centerZ, int radius) {
        List<SpawnPoint> result = new ArrayList<>();
        for (SpawnPoint sp : spawnPoints) {
            double dist = Math.hypot(sp.approxX - centerX, sp.approxY - centerY);
            if (dist <= radius && Math.abs(sp.approxZ - centerZ) < 500) {
                result.add(sp);
            }
        }
        return result;
    }

    /**
     * Count how many mobs are predicted to respawn in the next N seconds.
     */
    public int countImminentRespawns(String zoneName, int centerX, int centerY, int centerZ,
                                      int radius, long windowMs, long now) {
        List<SpawnPoint> points = getSpawnPointsInZone(centerX, centerY, centerZ, radius);
        int count = 0;
        for (SpawnPoint sp : points) {
            if (sp.isCurrentlyDead) {
                MobRespawnData data = mobData.get(sp.mobTemplateId);
                if (data != null && data.isRespawnImminent(now, windowMs)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Calculate average respawn time for a zone.
     */
    public long getAverageRespawnForZone(int centerX, int centerY, int centerZ, int radius) {
        List<SpawnPoint> points = getSpawnPointsInZone(centerX, centerY, centerZ, radius);
        long total = 0;
        int count = 0;
        for (SpawnPoint sp : points) {
            MobRespawnData data = mobData.get(sp.mobTemplateId);
            if (data != null && data.sampleCount > 0) {
                total += data.estimatedRespawnMs;
                count++;
            }
        }
        return count > 0 ? total / count : 60000;
    }

    /**
     * Get percentage of dead spawn points in a zone.
     */
    public double getDepletionRatio(int centerX, int centerY, int centerZ, int radius) {
        List<SpawnPoint> points = getSpawnPointsInZone(centerX, centerY, centerZ, radius);
        if (points.isEmpty()) return 0.0;
        int dead = 0;
        for (SpawnPoint sp : points) {
            if (sp.isCurrentlyDead) dead++;
        }
        return (double) dead / points.size();
    }

    /**
     * Estimate optimal camp time before switching spots.
     */
    public long getOptimalCampDuration(String zoneName, int centerX, int centerY, int centerZ,
                                        int radius, long now) {
        long avgRespawn = getAverageRespawnForZone(centerX, centerY, centerZ, radius);
        int imminent = countImminentRespawns(zoneName, centerX, centerY, centerZ, radius, 30000, now);

        // If many respawns coming, stay longer
        if (imminent >= 3) {
            return avgRespawn + 15000;
        }
        // If few respawns, rotate sooner
        return Math.max(30000, avgRespawn / 2);
    }

    /**
     * Clear old spawn points to prevent memory bloat.
     */
    public void pruneOldSpawnPoints(long olderThanMs, long now) {
        spawnPoints.removeIf(sp -> sp.lastSeenDead > 0
            && (now - sp.lastSeenDead) > olderThanMs
            && sp.killCount < 3);
    }

    private SpawnPoint findOrCreateSpawnPoint(int x, int y, int z, int templateId) {
        for (SpawnPoint sp : spawnPoints) {
            if (sp.matches(x, y, z, templateId, SPAWN_TOLERANCE)) {
                return sp;
            }
        }
        SpawnPoint sp = new SpawnPoint(x, y, z, templateId);
        spawnPoints.add(sp);
        if (spawnPoints.size() > MAX_SPAWN_POINTS) {
            spawnPoints.remove(0); // FIFO eviction
        }
        return sp;
    }

    private SpawnPoint findSpawnPoint(int x, int y, int z, int templateId) {
        for (SpawnPoint sp : spawnPoints) {
            if (sp.matches(x, y, z, templateId, SPAWN_TOLERANCE)) {
                return sp;
            }
        }
        return null;
    }

    public String getStatusReport() {
        int totalKills = mobData.values().stream().mapToInt(d -> d.sampleCount).sum();
        int trackedMobs = mobData.size();
        int trackedSpawns = spawnPoints.size();
        return String.format("Respawn[kills=%d mobs=%d spawns=%d]",
            totalKills, trackedMobs, trackedSpawns);
    }
}
