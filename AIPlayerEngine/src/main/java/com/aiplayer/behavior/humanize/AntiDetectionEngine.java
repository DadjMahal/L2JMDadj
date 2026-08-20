package com.aiplayer.behavior.humanize;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.movement.MovementController;
import com.aiplayer.behavior.party.PartyCoordinationEngine;
import com.aiplayer.behavior.social.SocialBehaviorEngine;
import com.aiplayer.behavior.town.TownBehaviorEngine;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.behavior.humanize.InputRandomizer;
import com.aiplayer.behavior.humanize.InputRandomizer.ClickResult;
import com.aiplayer.behavior.humanize.InputRandomizer.MouseProfile;
import com.aiplayer.behavior.humanize.TimingJitter.ActionContext;

/**
 * Main orchestrator for the Anti-Detection & Humanization Layer.
 * Coordinates BehavioralFingerprint, InputRandomizer, TimingJitter,
 * and SessionVariance to provide a unified API for all other modules.
 *
 * Core principle: Every AI Player must have a unique, consistent,
 * human-like behavioral signature that persists across sessions.
 *
 * Integration points:
 * - AIPlayer: owns the engine, passes to all subsystems
 * - AIBrain: queries for action delays, applies input randomization
 * - CombatAI: uses jittered cooldowns, reaction times
 * - MovementController: perturbs waypoints, camera noise
 * - SocialBehaviorEngine: uses session-aware chat intervals
 * - TownBehaviorEngine: applies humanized shop timing
 *
 * Detection vectors mitigated:
 * 1. Timing analysis -> TimingJitter with non-uniform distributions
 * 2. Input pattern analysis -> InputRandomizer with curves/overshoot
 * 3. Behavioral clustering -> BehavioralFingerprint uniqueness
 * 4. Session fingerprinting -> SessionVariance daily/weekly shifts
 * 5. Perfect execution -> Error injection, pause simulation
 */
public final class AntiDetectionEngine {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(AntiDetectionEngine.class.getName());

    private final String accountName;
    private final BehavioralFingerprint fingerprint;
    private final SessionVariance session;
    private final TimingJitter timing;
    private final InputRandomizer input;

    private volatile boolean enabled = true;
    private volatile long sessionStartMs;

    // Suspicious pattern counters (self-monitoring)
    private volatile int consecutiveIdenticalDelays = 0;
    private volatile int lastDelay = -1;
    private volatile long identicalDelayResetTime = 0;

    public AntiDetectionEngine(String accountName) {
        this.accountName = accountName;
        this.fingerprint = new BehavioralFingerprint(accountName);
        this.session = new SessionVariance(accountName, fingerprint);
        this.timing = new TimingJitter(accountName, fingerprint, session);
        this.input = new InputRandomizer(accountName, fingerprint);
        this.sessionStartMs = System.currentTimeMillis();

        LOGGER.info("[AntiDetection] Initialized for " + accountName + " with " + fingerprint);
    }

    /**
     * Main tick — call every 500ms to update session state.
     */
    public void tick() {
        if (!enabled) return;

        long now = System.currentTimeMillis();

        // Reset identical delay counter periodically
        if (now - identicalDelayResetTime > 5000) {
            consecutiveIdenticalDelays = 0;
            identicalDelayResetTime = now;
        }

        // Refresh session modifiers
        timing.refreshSessionModifiers();

        // Random camera movement (humans look around)
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self != null && input.shouldMoveCamera()) {
            int[] delta = input.nextCameraDelta();
            // In real integration, this would send camera rotation packet
            // protocol.sendCameraDelta(delta[0], delta[1]);
        }
    }

    // ================================================================
    // TIMING API (All modules MUST use these instead of raw Random)
    // ================================================================

    public int getDelay(TimingJitter.ActionContext context) {
        int delay = timing.getDelay(context);
        trackDelayVariance(delay);
        session.recordAction();
        return delay;
    }

    public int getDelay(TimingJitter.ActionContext context, double stressFactor) {
        int delay = timing.getDelay(context, stressFactor);
        trackDelayVariance(delay);
        session.recordAction();
        return delay;
    }

    public int getCooldownJitter(int baseCooldownMs) {
        return timing.getCooldownJitter(baseCooldownMs);
    }

    /** Jitter helper used by PartyCoordinationEngine (alias for cooldown jitter). */
    public int getJitter(int baseMs) {
        return getCooldownJitter(baseMs);
    }

    /** Returns a shared Random for modules that need random decisions. */
    public java.util.Random getRandom() {
        return new java.util.Random();
    }

    public int getMovementInterval() {
        return timing.getMovementInterval();
    }

    public int getPacketJitter() {
        return timing.getPacketJitter();
    }

    public int getDecisionDelay(boolean isImportant) {
        return timing.getDecisionDelay(isImportant);
    }

    public int getChatInterval() {
        int interval = timing.getChatInterval();
        session.recordChat();
        return interval;
    }

    public int getEmoteInterval() {
        return timing.getEmoteInterval();
    }

    // ================================================================
    // INPUT API
    // ================================================================

    public int[] perturbClick(int targetX, int targetY, int targetRadius) {
        return input.perturbClick(targetX, targetY, targetRadius);
    }

    public int[] perturbDestination(int x, int y, int z, int arrivalRadius) {
        return input.perturbDestination(x, y, z, arrivalRadius);
    }

    public java.util.List<int[]> generateMousePath(int fromX, int fromY, int toX, int toY) {
        return input.generateMousePath(fromX, fromY, toX, toY);
    }

    public InputRandomizer.ClickResult humanizeClick(int intendedClicks, int intendedDurationMs) {
        return input.humanizeClick(intendedClicks, intendedDurationMs);
    }

    public boolean shouldStutterStep() {
        return input.shouldStutterStep();
    }

    public int getStutterDuration() {
        return input.getStutterDuration();
    }

    public void setMouseProfile(InputRandomizer.MouseProfile profile, long durationMs) {
        input.setTemporaryProfile(profile, durationMs);
    }

    // ================================================================
    // SESSION API
    // ================================================================

    public double getSessionFatigue() {
        return session.getSessionFatigue();
    }

    public boolean shouldTakeBreak() {
        return session.shouldTakeBreak();
    }

    public int getBreakDurationMinutes() {
        return session.getBreakDurationMinutes();
    }

    public boolean shouldEndSession() {
        return session.shouldEndSession();
    }

    public double getSocialModifier() {
        return session.getSocialModifier();
    }

    public double getRiskModifier() {
        return session.getRiskModifier();
    }

    public void recordDeath() {
        session.recordDeath();
    }

    // ================================================================
    // FINGERPRINT API
    // ================================================================

    public BehavioralFingerprint getFingerprint() {
        return fingerprint;
    }

    public boolean shouldMakeMistake(double stressFactor) {
        return fingerprint.shouldMakeMistake(stressFactor);
    }

    public boolean shouldPause() {
        return fingerprint.shouldPause();
    }

    public int getPauseDurationMs() {
        return fingerprint.getPauseDurationMs();
    }

    // ================================================================
    // SELF-MONITORING (Detect if we're becoming too predictable)
    // ================================================================

    /**
     * Check if recent delays are suspiciously identical.
     * If so, force extra variance on next delay.
     */
    public boolean isPatternSuspicious() {
        return consecutiveIdenticalDelays >= 3;
    }

    public void forceExtraVariance() {
        consecutiveIdenticalDelays = 0;
    }

    // ------------------------------------------------------------------

    private void trackDelayVariance(int delay) {
        if (Math.abs(delay - lastDelay) < 10) {
            consecutiveIdenticalDelays++;
        } else {
            consecutiveIdenticalDelays = 0;
        }
        lastDelay = delay;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getStatusReport() {
        return String.format(
            "AntiDetection[%s: session=%dmin, fatigue=%.2f, actions=%d, avgDelay=%.0fms, suspicious=%s]",
            accountName,
            session.getSessionDurationMinutes(),
            getSessionFatigue(),
            session.getActionsThisSession(),
            timing.getAverageDelay(),
            isPatternSuspicious()
        );
    }
}
