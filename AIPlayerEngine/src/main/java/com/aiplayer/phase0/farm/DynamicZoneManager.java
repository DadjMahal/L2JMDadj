package com.aiplayer.phase0.farm;

/** MODE: PARTIAL. Missing Spot import fixed this pass (genuine compile error). Rest of the class still unmigrated. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.GameStateMirror.EntitySnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;
import com.aiplayer.phase0.movement.MovementController;
import com.aiplayer.phase0.party.PartyManager;
import com.aiplayer.phase0.farm.OptimalSpotSelector.Spot; // was missing entirely — genuine compile error, confirmed by external review

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central orchestrator for farm zone intelligence.
 * Coordinates ZoneDensityTracker, RespawnTimer, FarmZoneScorer,
 * and OptimalSpotSelector to make zone and spot decisions.
 *
 * State machine:
 *   EVALUATING -> TRAVELING -> FARMING -> ROTATING_SPOT -> (FARMING or EVALUATING)
 *   FARMING -> ZONE_SWITCH -> TRAVELING
 *
 * Integration: AIBrain calls tick() every 5s. CombatAI checks
 * isInFarmMode(). MovementController receives travel targets.
 */
public final class DynamicZoneManager {

    public enum ZoneState {
        EVALUATING,      // Calculating zone scores
        TRAVELING,       // Moving to target zone/spot
        FARMING,         // Actively farming current spot
        ROTATING_SPOT,   // Moving to new spot in same zone
        ZONE_SWITCH,     // Deciding to change zones
        WAITING_RESPAWN  // Camped, waiting for respawns
    }

    private final String accountName;
    private final ZoneDensityTracker densityTracker;
    private final RespawnTimer respawnTimer;
    private final FarmZoneScorer zoneScorer;
    private final OptimalSpotSelector spotSelector;
    private final AntiDetectionEngine anti;
    private final MovementController movement;
    private final PartyManager party;

    private ZoneState state = ZoneState.EVALUATING;
    private String currentZone = null;
    private String targetZone = null;
    private long stateEnterTime = 0;
    private long nextTickTime = 0;
    private long farmStartTime = 0;
    private long xpAtFarmStart = 0;
    private long adenaAtFarmStart = 0;
    private int killsInZone = 0;
    private int deathsInZone = 0;

    // Zone performance tracking
    private final Map<String, ZonePerformance> performanceLog = new ConcurrentHashMap<>();
    private static final long ZONE_EVAL_INTERVAL_MS = 30000;
    private static final long SPOT_ROTATE_CHECK_MS = 15000;
    private static final long MIN_FARM_TIME_MS = 120000; // 2 min minimum
    private static final long MAX_ZONE_TIME_MS = 1800000; // 30 min maximum
    private static final double ZONE_SWITCH_THRESHOLD = 1.8; // new zone must be 1.8x better

    public DynamicZoneManager(String accountName,
                              ZoneDensityTracker densityTracker,
                              RespawnTimer respawnTimer,
                              FarmZoneScorer zoneScorer,
                              OptimalSpotSelector spotSelector,
                              AntiDetectionEngine anti,
                              MovementController movement,
                              PartyManager party) {
        this.accountName = accountName;
        this.densityTracker = densityTracker;
        this.respawnTimer = respawnTimer;
        this.zoneScorer = zoneScorer;
        this.spotSelector = spotSelector;
        this.anti = anti;
        this.movement = movement;
        this.party = party;
    }

    /**
     * Main tick — call every 5s from AIBrain.
     */
    public void tick(BotStateSnapshot self, GameStateMirror mirror) {
        long now = System.currentTimeMillis();
        if (now < nextTickTime) return;

        // Update density tracking
        densityTracker.tick(mirror, self);

        // Update respawn tracking from visible mobs
        for (EntitySnapshot e : mirror.getVisibleEntities()) {
            if (e.isMob && !e.isDead) {
                respawnTimer.onMobSeenAlive(e, now);
            }
        }

        // Recalculate zone scores periodically
        if (zoneScorer.shouldRecalculate(now)) {
            boolean preferSolo = !party.isInParty();
            zoneScorer.calculateScores(self, preferSolo);
        }

        switch (state) {
            case EVALUATING:
                handleEvaluating(self, now);
                break;
            case TRAVELING:
                handleTraveling(self, now);
                break;
            case FARMING:
                handleFarming(self, mirror, now);
                break;
            case ROTATING_SPOT:
                handleRotatingSpot(self, mirror, now);
                break;
            case ZONE_SWITCH:
                handleZoneSwitch(self, now);
                break;
            case WAITING_RESPAWN:
                handleWaitingRespawn(self, now);
                break;
        }

        nextTickTime = now + 5000 + anti.getJitter(2000);
    }

    // ================================================================
    // STATE HANDLERS
    // ================================================================

    private void handleEvaluating(BotStateSnapshot self, long now) {
        FarmZoneScorer.ZoneScore best = zoneScorer.getBestZone();
        if (best == null || !best.isViable()) {
            // No viable zones — stay put and retry
            nextTickTime = now + 15000;
            return;
        }

        targetZone = best.zoneName;
        state = ZoneState.TRAVELING;
        stateEnterTime = now;
    }

    private void handleTraveling(BotStateSnapshot self, long now) {
        ZoneDensityTracker.Zone zone = densityTracker.findZone(targetZone);
        if (zone == null) {
            state = ZoneState.EVALUATING;
            return;
        }

        double dist = zone.distanceFrom(self.x, self.y);
        if (dist < zone.radius) {
            // Arrived
            currentZone = targetZone;
            state = ZoneState.FARMING;
            stateEnterTime = now;
            farmStartTime = now;
            xpAtFarmStart = self.currentXp;
            adenaAtFarmStart = self.adena;
            killsInZone = 0;
            deathsInZone = 0;

            // Select initial spot
            List<EntitySnapshot> entities = GameStateMirror.getInstance().getVisibleEntities();
            spotSelector.selectSpot(zone.name, zone.centerX, zone.centerY, zone.centerZ,
                                    zone.radius, entities, self, now);
            return;
        }

        // Continue traveling
        int[] dest = anti.perturbDestination(zone.centerX, zone.centerY, zone.centerZ, 300);
        movement.moveTo(dest[0], dest[1], dest[2]);
    }

    private void handleFarming(BotStateSnapshot self, GameStateMirror mirror, long now) {
        long farmTime = now - farmStartTime;

        // Check if we should rotate spots
        if (farmTime > MIN_FARM_TIME_MS / 2) {
            List<EntitySnapshot> entities = mirror.getVisibleEntities();
            if (spotSelector.shouldRotateSpot(currentZone, entities, self, now)) {
                Spot nextSpot = spotSelector.getRotationTarget(currentZone, entities, self, now);
                if (nextSpot != null) {
                    spotSelector.moveToSpot(nextSpot, self);
                    state = ZoneState.ROTATING_SPOT;
                    stateEnterTime = now;
                    return;
                }
            }
        }

        // Check if we should switch zones
        if (farmTime > MIN_FARM_TIME_MS) {
            FarmZoneScorer.ZoneScore currentScore = zoneScorer.getScore(currentZone);
            FarmZoneScorer.ZoneScore bestScore = zoneScorer.getBestZone();

            if (bestScore != null && !bestScore.zoneName.equals(currentZone)) {
                double ratio = bestScore.totalScore / Math.max(1, currentScore != null ? currentScore.totalScore : 100);
                if (ratio > ZONE_SWITCH_THRESHOLD || farmTime > MAX_ZONE_TIME_MS) {
                    // Log performance before leaving
                    logZonePerformance(currentZone, now);
                    targetZone = bestScore.zoneName;
                    state = ZoneState.ZONE_SWITCH;
                    stateEnterTime = now;
                    spotSelector.clearCurrentSpot();
                    return;
                }
            }
        }

        // Check if spot is depleted — wait for respawn
        Spot current = spotSelector.getCurrentSpot();
        if (current != null && current.isDepleted()) {
            int imminent = respawnTimer.countImminentRespawns(
                currentZone, current.centerX, current.centerY, current.centerZ,
                current.radius, 60000, now);
            if (imminent > 0) {
                state = ZoneState.WAITING_RESPAWN;
                stateEnterTime = now;
                return;
            }
        }

        // Ensure we're at the spot
        if (current != null) {
            double dist = current.distanceTo(self.x, self.y);
            if (dist > current.radius) {
                spotSelector.moveToSpot(current, self);
            }
        }
    }

    private void handleRotatingSpot(BotStateSnapshot self, GameStateMirror mirror, long now) {
        Spot target = spotSelector.getCurrentSpot();
        if (target == null) {
            state = ZoneState.FARMING;
            return;
        }

        double dist = target.distanceTo(self.x, self.y);
        if (dist < target.radius * 0.8) {
            state = ZoneState.FARMING;
            return;
        }

        // Continue moving
        spotSelector.moveToSpot(target, self);
    }

    private void handleZoneSwitch(BotStateSnapshot self, long now) {
        // Same as traveling but with performance logged
        handleTraveling(self, now);
    }

    private void handleWaitingRespawn(BotStateSnapshot self, long now) {
        long waited = now - stateEnterTime;
        if (waited > 120000) { // Max wait 2 min
            state = ZoneState.ROTATING_SPOT;
            return;
        }

        Spot current = spotSelector.getCurrentSpot();
        if (current != null) {
            int imminent = respawnTimer.countImminentRespawns(
                currentZone, current.centerX, current.centerY, current.centerZ,
                current.radius, 30000, now);
            if (imminent >= 2) {
                state = ZoneState.FARMING;
            }
        }
    }

    // ================================================================
    // EVENT HANDLERS
    // ================================================================

    /**
     * Call when a mob is killed.
     */
    public void onMobKill(int mobTemplateId, int xpGained, int adenaGained) {
        killsInZone++;
    }

    /**
     * Call when the AI Player dies.
     */
    public void onDeath() {
        deathsInZone++;
        // Consider zone switch after multiple deaths
        if (deathsInZone >= 3) {
            ZoneDensityTracker.Zone zone = densityTracker.findZone(currentZone);
            if (zone != null) {
                zoneScorer.recordXpPerHour(currentZone, 0); // Mark as bad
            }
        }
    }

    /**
     * Call when entering a new zone manually (e.g., via teleport).
     */
    public void onZoneEntered(String zoneName) {
        currentZone = zoneName;
        state = ZoneState.FARMING;
        stateEnterTime = System.currentTimeMillis();
        farmStartTime = stateEnterTime;
        killsInZone = 0;
        deathsInZone = 0;
    }

    // ================================================================
    // QUERIES
    // ================================================================

    public boolean isInFarmMode() {
        return state == ZoneState.FARMING || state == ZoneState.WAITING_RESPAWN;
    }

    public boolean isTraveling() {
        return state == ZoneState.TRAVELING || state == ZoneState.ZONE_SWITCH;
    }

    public String getCurrentZone() {
        return currentZone;
    }

    public ZoneState getState() {
        return state;
    }

    public Spot getCurrentSpot() {
        return spotSelector.getCurrentSpot();
    }

    public FarmZoneScorer.ZoneScore getCurrentZoneScore() {
        return currentZone != null ? zoneScorer.getScore(currentZone) : null;
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private void logZonePerformance(String zoneName, long now) {
        long duration = now - farmStartTime;
        if (duration < 60000) return; // Ignore short visits

        long xpGained = GameStateMirror.getInstance().getSelfSnapshot().currentXp - xpAtFarmStart;
        long adenaGained = GameStateMirror.getInstance().getSelfSnapshot().adena - adenaAtFarmStart;

        double xpPerHour = (xpGained * 3600000.0) / duration;
        double adenaPerHour = (adenaGained * 3600000.0) / duration;

        zoneScorer.recordXpPerHour(zoneName, xpPerHour);
        zoneScorer.recordAdenaPerHour(zoneName, adenaPerHour);

        ZonePerformance perf = new ZonePerformance(
            zoneName, duration, killsInZone, deathsInZone, xpPerHour, adenaPerHour, now
        );
        performanceLog.put(zoneName, perf);
    }

    private static final class ZonePerformance {
        final String zoneName;
        final long durationMs;
        final int kills;
        final int deaths;
        final double xpPerHour;
        final double adenaPerHour;
        final long loggedAt;

        ZonePerformance(String zoneName, long durationMs, int kills, int deaths,
                        double xpPerHour, double adenaPerHour, long loggedAt) {
            this.zoneName = zoneName;
            this.durationMs = durationMs;
            this.kills = kills;
            this.deaths = deaths;
            this.xpPerHour = xpPerHour;
            this.adenaPerHour = adenaPerHour;
            this.loggedAt = loggedAt;
        }
    }

    public String getStatusReport() {
        Spot spot = spotSelector.getCurrentSpot();
        return String.format("ZoneMgr[%s: state=%s zone=%s spot=(%s) kills=%d deaths=%d]",
            accountName, state, currentZone,
            spot != null ? spot.centerX + "," + spot.centerY : "none",
            killsInZone, deathsInZone);
    }
}
