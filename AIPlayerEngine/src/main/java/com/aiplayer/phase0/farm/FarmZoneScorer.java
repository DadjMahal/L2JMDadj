package com.aiplayer.phase0.farm;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.farm.ZoneDensityTracker.DensitySnapshot;
import com.aiplayer.phase0.farm.ZoneDensityTracker.Zone;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic zone scoring engine that ranks farming zones based on
 * multiple weighted factors: density, competition, level fit,
 * respawn timing, danger, travel distance, and historical XP/h.
 *
 * Scores are recalculated every 30s or on significant events.
 * ZoneRecommender uses these scores to suggest the best zone.
 *
 * Integration: AIBrain calls calculateScores() every 30s.
 * DynamicZoneManager consumes scores for spot selection.
 */
public final class FarmZoneScorer {

    public static final class ZoneScore {
        public final String zoneName;
        public final double totalScore;      // 0-1000
        public final double densityScore;    // mob availability
        public final double competitionScore; // inverse of competition
        public final double levelFitScore;   // how well level matches
        public final double respawnScore;    // predicted respawn health
        public final double safetyScore;     // danger inverse
        public final double travelScore;     // distance penalty
        public final double historyScore;    // past XP/h performance
        public final long calculatedAt;

        public ZoneScore(String zoneName, double totalScore,
                         double densityScore, double competitionScore,
                         double levelFitScore, double respawnScore,
                         double safetyScore, double travelScore,
                         double historyScore, long calculatedAt) {
            this.zoneName = zoneName;
            this.totalScore = totalScore;
            this.densityScore = densityScore;
            this.competitionScore = competitionScore;
            this.levelFitScore = levelFitScore;
            this.respawnScore = respawnScore;
            this.safetyScore = safetyScore;
            this.travelScore = travelScore;
            this.historyScore = historyScore;
            this.calculatedAt = calculatedAt;
        }

        public boolean isViable() {
            return totalScore > 200;
        }

        public boolean isExcellent() {
            return totalScore > 700;
        }
    }

    // Weight configuration (tuneable per account archetype)
    public static final class ScoreWeights {
        public double densityWeight = 0.25;
        public double competitionWeight = 0.25;
        public double levelFitWeight = 0.15;
        public double respawnWeight = 0.15;
        public double safetyWeight = 0.10;
        public double travelWeight = 0.05;
        public double historyWeight = 0.05;

        // Risk-tolerant archetype (e.g., aggressive DPS)
        public static ScoreWeights aggressive() {
            ScoreWeights w = new ScoreWeights();
            w.densityWeight = 0.30;
            w.competitionWeight = 0.15;
            w.safetyWeight = 0.05;
            w.travelWeight = 0.05;
            return w;
        }

        // Risk-averse archetype (e.g., careful support)
        public static ScoreWeights cautious() {
            ScoreWeights w = new ScoreWeights();
            w.densityWeight = 0.20;
            w.competitionWeight = 0.30;
            w.safetyWeight = 0.20;
            w.travelWeight = 0.05;
            return w;
        }
    }

    private final ZoneDensityTracker densityTracker;
    private final RespawnTimer respawnTimer;
    private final AntiDetectionEngine anti;
    private final ScoreWeights weights;

    private final Map<String, ZoneScore> latestScores = new ConcurrentHashMap<>();
    private final Map<String, Double> xpPerHourHistory = new ConcurrentHashMap<>();
    private final Map<String, Double> adenaPerHourHistory = new ConcurrentHashMap<>();
    private long lastCalculationTime = 0;
    private static final long CALC_INTERVAL_MS = 30000;

    public FarmZoneScorer(ZoneDensityTracker densityTracker,
                          RespawnTimer respawnTimer,
                          AntiDetectionEngine anti,
                          ScoreWeights weights) {
        this.densityTracker = densityTracker;
        this.respawnTimer = respawnTimer;
        this.anti = anti;
        this.weights = weights != null ? weights : new ScoreWeights();
    }

    /**
     * Calculate scores for all eligible zones.
     * Call every 30s from AIBrain.
     */
    public List<ZoneScore> calculateScores(BotStateSnapshot self, boolean preferSolo) {
        long now = System.currentTimeMillis();
        List<Zone> eligible = densityTracker.findEligibleZones(self.level, preferSolo);
        List<ZoneScore> scores = new ArrayList<>();

        for (Zone zone : eligible) {
            ZoneScore score = scoreZone(zone, self, now);
            latestScores.put(zone.name, score);
            scores.add(score);
        }

        // Sort by total score descending
        scores.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));
        lastCalculationTime = now;
        return scores;
    }

    private ZoneScore scoreZone(Zone zone, BotStateSnapshot self, long now) {
        DensitySnapshot density = densityTracker.getDensity(zone.name);

        // Density score: more alive mobs = better (0-1000)
        double densityScore = 500;
        if (density != null) {
            densityScore = Math.min(1000, density.aliveMobCount * 80.0);
            if (density.isDepleted()) densityScore *= 0.3;
        }

        // Competition score: fewer players per mob = better (0-1000)
        double competitionScore = 500;
        if (density != null) {
            if (density.competitionRatio <= 0.5) competitionScore = 1000;
            else if (density.competitionRatio <= 1.0) competitionScore = 800;
            else if (density.competitionRatio <= 2.0) competitionScore = 500;
            else if (density.competitionRatio <= 3.0) competitionScore = 300;
            else competitionScore = 100;
        }

        // Level fit score: sweet spot is zone mid-level (0-1000)
        double levelFitScore = 500;
        int zoneMid = (zone.minLevel + zone.maxLevel) / 2;
        int levelDiff = Math.abs(self.level - zoneMid);
        if (levelDiff <= 2) levelFitScore = 1000;
        else if (levelDiff <= 4) levelFitScore = 800;
        else if (levelDiff <= 6) levelFitScore = 600;
        else if (levelDiff <= 8) levelFitScore = 400;
        else levelFitScore = 200;

        // Respawn score: healthy respawn prediction = better (0-1000)
        double respawnScore = 500;
        long avgRespawn = respawnTimer.getAverageRespawnForZone(
            zone.centerX, zone.centerY, zone.centerZ, zone.radius);
        int imminent = respawnTimer.countImminentRespawns(
            zone.name, zone.centerX, zone.centerY, zone.centerZ, zone.radius, 60000, now);
        if (avgRespawn > 0 && avgRespawn < 45000) respawnScore = 900; // fast respawn
        else if (avgRespawn < 90000) respawnScore = 700;
        else if (avgRespawn < 180000) respawnScore = 500;
        else respawnScore = 300;
        respawnScore += imminent * 50; // bonus for imminent respawns
        respawnScore = Math.min(1000, respawnScore);

        // Safety score: inverse of danger (0-1000)
        double safetyScore = zone.isDangerous ? 300 : 900;
        if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) < 50) safetyScore *= 1.5; // value safety more when hurt

        // Travel score: closer = better (0-1000)
        double distance = zone.distanceFrom(self.x, self.y);
        double travelScore = 1000;
        if (distance > 50000) travelScore = 200;
        else if (distance > 30000) travelScore = 400;
        else if (distance > 15000) travelScore = 600;
        else if (distance > 5000) travelScore = 800;
        else travelScore = 1000;

        // History score: past XP/h performance (0-1000)
        double historyScore = 500;
        Double pastXp = xpPerHourHistory.get(zone.name);
        if (pastXp != null) {
            if (pastXp > 500000) historyScore = 1000;
            else if (pastXp > 300000) historyScore = 800;
            else if (pastXp > 150000) historyScore = 600;
            else if (pastXp > 50000) historyScore = 400;
            else historyScore = 200;
        }

        // Weighted total
        double total =
            densityScore * weights.densityWeight +
            competitionScore * weights.competitionWeight +
            levelFitScore * weights.levelFitWeight +
            respawnScore * weights.respawnWeight +
            safetyScore * weights.safetyWeight +
            travelScore * weights.travelWeight +
            historyScore * weights.historyWeight;

        // Add small randomization to prevent identical decisions across AI Players
        total += anti.getJitter(30);

        return new ZoneScore(
            zone.name, total,
            densityScore, competitionScore, levelFitScore,
            respawnScore, safetyScore, travelScore, historyScore,
            now
        );
    }

    /**
     * Get the highest scored zone.
     */
    public ZoneScore getBestZone() {
        return latestScores.values().stream()
            .max(Comparator.comparingDouble(s -> s.totalScore))
            .orElse(null);
    }

    /**
     * Get top N zones.
     */
    public List<ZoneScore> getTopZones(int n) {
        return latestScores.values().stream()
            .sorted((a, b) -> Double.compare(b.totalScore, a.totalScore))
            .limit(n)
            .toList();
    }

    /**
     * Record XP/h for a zone after a session.
     */
    public void recordXpPerHour(String zoneName, double xpPerHour) {
        Double existing = xpPerHourHistory.get(zoneName);
        if (existing == null) {
            xpPerHourHistory.put(zoneName, xpPerHour);
        } else {
            // Exponential moving average
            xpPerHourHistory.put(zoneName, existing * 0.7 + xpPerHour * 0.3);
        }
    }

    public void recordAdenaPerHour(String zoneName, double adenaPerHour) {
        Double existing = adenaPerHourHistory.get(zoneName);
        if (existing == null) {
            adenaPerHourHistory.put(zoneName, adenaPerHour);
        } else {
            adenaPerHourHistory.put(zoneName, existing * 0.7 + adenaPerHour * 0.3);
        }
    }

    public ZoneScore getScore(String zoneName) {
        return latestScores.get(zoneName);
    }

    public boolean shouldRecalculate(long now) {
        return now - lastCalculationTime > CALC_INTERVAL_MS;
    }

    public String getStatusReport() {
        ZoneScore best = getBestZone();
        if (best == null) return "ZoneScorer[no scores yet]";
        return String.format("ZoneScorer[best=%s score=%.0f viable=%d]",
            best.zoneName, best.totalScore,
            latestScores.values().stream().filter(ZoneScore::isViable).count());
    }
}
