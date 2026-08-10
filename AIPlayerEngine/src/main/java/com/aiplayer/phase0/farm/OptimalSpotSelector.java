package com.aiplayer.phase0.farm;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.GameStateMirror.EntitySnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.movement.MovementController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects optimal farming spots within a zone based on mob clustering,
 * respawn prediction, player avoidance, and path efficiency.
 *
 * A "spot" is a local area within a zone with good mob density
 * and minimal competition. The selector evaluates candidate spots
 * and picks the best one, then monitors it for degradation.
 *
 * Integration: DynamicZoneManager calls selectSpot() when entering
 * a zone. AIBrain calls evaluateCurrentSpot() every 10s to decide
 * if rotation is needed.
 */
public final class OptimalSpotSelector {

    public static final class Spot {
        public final int centerX;
        public final int centerY;
        public final int centerZ;
        public final int radius; // typically 1000-2000
        public final int mobCount;
        public final int playerCount;
        public final double avgMobLevel;
        public final long discoveredAt;
        public long lastVisited;
        public int visitCount;
        public double avgXpPerKill;
        public double qualityScore;

        public Spot(int centerX, int centerY, int centerZ, int radius,
                    int mobCount, int playerCount, double avgMobLevel,
                    long discoveredAt) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.mobCount = mobCount;
            this.playerCount = playerCount;
            this.avgMobLevel = avgMobLevel;
            this.discoveredAt = discoveredAt;
            this.qualityScore = calculateInitialScore();
        }

        private double calculateInitialScore() {
            double score = mobCount * 100.0;
            score -= playerCount * 150.0;
            if (playerCount == 0) score += 200;
            return score;
        }

        public boolean contains(int x, int y, int z) {
            double dist = Math.hypot(x - centerX, y - centerY);
            return dist <= radius && Math.abs(z - centerZ) < 300;
        }

        public double distanceTo(int x, int y) {
            return Math.hypot(centerX - x, centerY - y);
        }

        public boolean isOvercrowded() {
            return playerCount > 2;
        }

        public boolean isDepleted() {
            return mobCount < 2;
        }
    }

    private final RespawnTimer respawnTimer;
    private final AntiDetectionEngine anti;
    private final MovementController movement;

    private final List<Spot> knownSpots = new ArrayList<>();
    private final Map<String, Spot> currentSpotByZone = new ConcurrentHashMap<>();
    private Spot currentSpot = null;
    private long spotEnterTime = 0;
    private long nextEvaluationTime = 0;
    private static final long EVAL_INTERVAL_MS = 10000;
    private static final long MIN_SPOT_TIME_MS = 60000; // stay at least 60s
    private static final int SPOT_RADIUS = 1500;
    private static final int SPOT_MERGE_DISTANCE = 800;

    public OptimalSpotSelector(RespawnTimer respawnTimer,
                             AntiDetectionEngine anti,
                             MovementController movement) {
        this.respawnTimer = respawnTimer;
        this.anti = anti;
        this.movement = movement;
    }

    /**
     * Select the best spot in a zone. Called when entering a zone.
     */
    public Spot selectSpot(String zoneName, int zoneCenterX, int zoneCenterY, int zoneCenterZ,
                           int zoneRadius, List<EntitySnapshot> entities,
                           BotStateSnapshot self, long now) {
        List<Spot> candidates = discoverSpots(zoneName, zoneCenterX, zoneCenterY, zoneCenterZ,
                                              zoneRadius, entities, now);

        if (candidates.isEmpty()) {
            // Fallback: use zone center as a spot
            Spot fallback = new Spot(zoneCenterX, zoneCenterY, zoneCenterZ, SPOT_RADIUS,
                                      0, 0, self.level, now);
            currentSpot = fallback;
            spotEnterTime = now;
            return fallback;
        }

        // Score candidates
        Spot best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Spot spot : candidates) {
            double score = scoreSpot(spot, self, now);
            if (score > bestScore) {
                bestScore = score;
                best = spot;
            }
        }

        if (best != null) {
            currentSpot = best;
            spotEnterTime = now;
            best.lastVisited = now;
            best.visitCount++;
            currentSpotByZone.put(zoneName, best);
        }

        return best;
    }

    /**
     * Evaluate if we should rotate to a new spot.
     * Returns true if current spot is degraded and rotation recommended.
     */
    public boolean shouldRotateSpot(String zoneName, List<EntitySnapshot> entities,
                                     BotStateSnapshot self, long now) {
        if (now < nextEvaluationTime) return false;
        nextEvaluationTime = now + EVAL_INTERVAL_MS;

        if (currentSpot == null) return true;
        if (now - spotEnterTime < MIN_SPOT_TIME_MS) return false;

        // Re-scan current spot
        int mobCount = 0;
        int playerCount = 0;
        for (EntitySnapshot e : entities) {
            if (!currentSpot.contains(e.x, e.y, e.z)) continue;
            if (e.isMob && !e.isDead) mobCount++;
            if (e.isPlayer && !e.name.equalsIgnoreCase(self.name)) playerCount++;
        }

        // Update current spot stats
        currentSpot = new Spot(
            currentSpot.centerX, currentSpot.centerY, currentSpot.centerZ,
            currentSpot.radius, mobCount, playerCount, currentSpot.avgMobLevel, now
        );

        // Rotation triggers
        if (currentSpot.isDepleted()) return true;
        if (currentSpot.isOvercrowded()) return true;
        if (mobCount < 3 && now - spotEnterTime > MIN_SPOT_TIME_MS * 2) return true;

        // Check if there's a much better spot nearby
        List<Spot> alternatives = discoverSpots(zoneName, currentSpot.centerX,
            currentSpot.centerY, currentSpot.centerZ, SPOT_RADIUS * 3, entities, now);
        for (Spot alt : alternatives) {
            if (alt.qualityScore > currentSpot.qualityScore * 1.5 && alt.playerCount < currentSpot.playerCount) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get a nearby spot to rotate to.
     */
    public Spot getRotationTarget(String zoneName, List<EntitySnapshot> entities,
                                   BotStateSnapshot self, long now) {
        List<Spot> candidates = discoverSpots(zoneName, currentSpot != null ? currentSpot.centerX : self.x,
            currentSpot != null ? currentSpot.centerY : self.y, self.z,
            SPOT_RADIUS * 4, entities, now);

        Spot best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Spot spot : candidates) {
            if (currentSpot != null && spot.contains(currentSpot.centerX, currentSpot.centerY, currentSpot.centerZ)) {
                continue; // Don't rotate to same spot
            }
            double score = scoreSpot(spot, self, now);
            if (score > bestScore) {
                bestScore = score;
                best = spot;
            }
        }

        return best;
    }

    /**
     * Move to a spot with humanized arrival.
     */
    public void moveToSpot(Spot spot, BotStateSnapshot self) {
        if (spot == null) return;
        int[] dest = anti.perturbDestination(spot.centerX, spot.centerY, spot.centerZ, 200);
        movement.moveTo(dest[0], dest[1], dest[2]);
    }

    public Spot getCurrentSpot() {
        return currentSpot;
    }

    public long getSpotEnterTime() {
        return spotEnterTime;
    }

    public void clearCurrentSpot() {
        currentSpot = null;
        spotEnterTime = 0;
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private List<Spot> discoverSpots(String zoneName, int centerX, int centerY, int centerZ,
                                      int radius, List<EntitySnapshot> entities, long now) {
        // Cluster mobs using simple grid-based clustering
        Map<String, List<EntitySnapshot>> clusters = new HashMap<>();
        int gridSize = 800;

        for (EntitySnapshot e : entities) {
            if (!e.isMob || e.isDead) continue;
            double dist = Math.hypot(e.x - centerX, e.y - centerY);
            if (dist > radius) continue;

            int gx = e.x / gridSize;
            int gy = e.y / gridSize;
            String key = gx + "," + gy;
            clusters.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<Spot> spots = new ArrayList<>();
        for (List<EntitySnapshot> cluster : clusters.values()) {
            if (cluster.size() < 2) continue;

            int sumX = 0, sumY = 0, sumZ = 0, sumLevel = 0;
            for (EntitySnapshot e : cluster) {
                sumX += e.x;
                sumY += e.y;
                sumZ += e.z;
                sumLevel += e.level;
            }

            int cx = sumX / cluster.size();
            int cy = sumY / cluster.size();
            int cz = sumZ / cluster.size();

            // Count players in this cluster
            int players = 0;
            for (EntitySnapshot e : entities) {
                if (!e.isPlayer) continue;
                double d = Math.hypot(e.x - cx, e.y - cy);
                if (d < SPOT_RADIUS) players++;
            }

            Spot spot = new Spot(cx, cy, cz, SPOT_RADIUS, cluster.size(), players,
                                  (double) sumLevel / cluster.size(), now);

            // Merge with existing known spot if close
            Spot merged = mergeWithKnown(spot);
            if (merged != null) {
                spots.add(merged);
            } else {
                knownSpots.add(spot);
                spots.add(spot);
            }
        }

        // Prune old spots
        knownSpots.removeIf(s -> now - s.discoveredAt > 3600000 && s.visitCount == 0);

        return spots;
    }

    private Spot mergeWithKnown(Spot newSpot) {
        for (Spot known : knownSpots) {
            if (known.distanceTo(newSpot.centerX, newSpot.centerY) < SPOT_MERGE_DISTANCE
                && Math.abs(known.centerZ - newSpot.centerZ) < 300) {
                // Update known spot with new data
                return new Spot(
                    known.centerX, known.centerY, known.centerZ,
                    known.radius,
                    Math.max(known.mobCount, newSpot.mobCount),
                    newSpot.playerCount,
                    newSpot.avgMobLevel,
                    known.discoveredAt
                );
            }
        }
        return null;
    }

    private double scoreSpot(Spot spot, BotStateSnapshot self, long now) {
        double score = spot.qualityScore;

        // Distance penalty: prefer closer spots
        double dist = spot.distanceTo(self.x, self.y);
        score -= dist * 0.01;

        // Respawn bonus: if respawns are imminent here
        int imminent = respawnTimer.countImminentRespawns(
            "local", spot.centerX, spot.centerY, spot.centerZ, spot.radius, 45000, now);
        score += imminent * 80;

        // Visit penalty: avoid recently visited spots
        if (spot.lastVisited > 0) {
            long sinceVisit = now - spot.lastVisited;
            if (sinceVisit < 300000) score -= 200; // visited < 5 min ago
            else if (sinceVisit < 600000) score -= 100;
        }

        // Fresh spot bonus
        if (spot.visitCount == 0) score += 150;

        // Add jitter for human-like variation
        score += anti.getJitter(50);

        return score;
    }

    public String getStatusReport() {
        if (currentSpot == null) return "SpotSelector[no spot]";
        return String.format("SpotSelector[spot=(%d,%d) mobs=%d players=%d time=%ds]",
            currentSpot.centerX, currentSpot.centerY,
            currentSpot.mobCount, currentSpot.playerCount,
            (System.currentTimeMillis() - spotEnterTime) / 1000);
    }
}