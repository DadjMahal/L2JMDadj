package com.aiplayer.phase0.humanize;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides human-like timing jitter for all AI Player actions.
 * Detection systems flag perfectly consistent intervals between packets.
 * Humans have natural variance in:
 * - Reaction time (alertness, focus, distraction)
 * - Action intervals (rhythm changes, fatigue)
 * - Skill rotation timing (not machine-perfect)
 * - Movement updates (lag, stutter, micro-pauses)
 *
 * This class uses the player's BehavioralFingerprint combined with
 * session variance to produce believable, non-uniform timing.
 */
public final class TimingJitter {

    // Context types affect baseline timing
    public enum ActionContext {
        COMBAT_ATTACK,      // Attacking a target
        COMBAT_SKILL,       // Using a skill
        COMBAT_BUFF,        // Self/party buff
        MOVEMENT_START,     // Beginning to move
        MOVEMENT_UPDATE,    // Ongoing movement waypoint
        MOVEMENT_ARRIVE,    // Reaching destination
        INVENTORY_USE,      // Using potion/scroll
        INVENTORY_SORT,     // Moving items around
        NPC_INTERACT,       // Talking to NPC
        NPC_SHOP_ACTION,    // Buy/sell
        SOCIAL_CHAT,        // Typing message
        SOCIAL_EMOTE,       // Using emote
        IDLE_PAUSE,         // Doing nothing
        CAMERA_ADJUST,      // Looking around
        LOOT_PICKUP         // Picking up drop
    }

    private final String accountName;
    private final BehavioralFingerprint fingerprint;
    private final SessionVariance session;

    // Session-wide modifiers (applied to all timings)
    private double sessionSpeedModifier = 1.0;
    private double sessionVarianceBoost = 1.0;
    private long lastModifierUpdate = 0;

    // Running statistics (for self-monitoring, not detection)
    private long totalActions = 0;
    private long totalDelayMs = 0;

    public TimingJitter(String accountName, BehavioralFingerprint fingerprint, SessionVariance session) {
        this.accountName = accountName;
        this.fingerprint = fingerprint;
        this.session = session;
        refreshSessionModifiers();
    }

    /**
     * Get delay before next action in given context.
     * This is the PRIMARY API — all modules should use this instead of raw ThreadLocalRandom.
     */
    public int getDelay(ActionContext context) {
        refreshSessionModifiers();

        int base = getBaseDelay(context);
        int variance = getVariance(context, base);

        // Apply fingerprint modifiers
        double reactMult = lerp(fingerprint.reactionTime, 0.7, 1.4);
        double accuracyMult = lerp(fingerprint.inputAccuracy, 1.15, 0.85);

        // Apply session modifiers
        reactMult *= sessionSpeedModifier;
        variance *= sessionVarianceBoost;

        // Add occasional "distraction" pause
        if (fingerprint.shouldPause() && context != ActionContext.COMBAT_ATTACK && context != ActionContext.COMBAT_SKILL) {
            base += fingerprint.getPauseDurationMs();
        }

        double result = HumanizedRandom.normal(base * reactMult * accuracyMult, variance);
        int delay = Math.max(getMinDelay(context), (int) result);

        totalActions++;
        totalDelayMs += delay;
        return delay;
    }

    /**
     * Get delay with explicit stress factor.
     * Higher stress = faster but more erratic timing.
     */
    public int getDelay(ActionContext context, double stressFactor) {
        int base = getDelay(context);
        if (stressFactor <= 0) return base;

        // Under stress: faster mean, higher variance
        double stressSpeed = 1.0 - stressFactor * 0.25;
        double stressVariance = 1.0 + stressFactor * 0.5;

        return Math.max(getMinDelay(context), (int) (base * stressSpeed * HumanizedRandom.normal(1.0, 0.1 * stressVariance)));
    }

    /**
     * Get cooldown jitter — prevents perfectly synced skill rotations.
     */
    public int getCooldownJitter(int baseCooldownMs) {
        // Humans don't re-cast the instant cooldown expires
        double waitRatio = lerp(fingerprint.reactionTime, 0.02, 0.12);
        int extraWait = (int) (baseCooldownMs * waitRatio);
        int variance = (int) (baseCooldownMs * 0.03);
        return extraWait + Math.max(0, (int) HumanizedRandom.normal(0, variance));
    }

    /**
     * Get movement update interval with human-like stutter.
     */
    public int getMovementInterval() {
        int base = 600; // Base 600ms between move packets
        int variance = (int) (base * 0.25 * sessionVarianceBoost);
        double fatigue = session.getSessionFatigue();
        double fatigueMult = 1.0 + fatigue * 0.3; // Slower when tired
        return Math.max(300, (int) HumanizedRandom.normal(base * fatigueMult, variance));
    }

    /**
     * Get packet send jitter — adds noise to all outgoing packet timing.
     */
    public int getPacketJitter() {
        // Tiny jitter for every packet (1-15ms)
        return HumanizedRandom.skewedInt(1, 15, -0.5);
    }

    /**
     * Get "thinking" delay before a decision.
     * Humans pause before switching targets or using big cooldowns.
     */
    public int getDecisionDelay(boolean isImportant) {
        int base = isImportant ? 400 : 150;
        int complexity = isImportant ? 300 : 50;
        double stress = isImportant ? 0.6 : 0.2;
        return HumanizedRandom.thinkDelay(base, complexity, stress);
    }

    /**
     * Get interval between chat messages.
     */
    public int getChatInterval() {
        double social = fingerprint.socialTendency;
        int base = (int) lerp(social, 8000, 2500); // Less social = longer gaps
        int variance = (int) (base * 0.4);
        return Math.max(1000, (int) HumanizedRandom.logNormal(Math.log(base), 0.3));
    }

    /**
     * Get random emote interval.
     */
    public int getEmoteInterval() {
        double social = fingerprint.socialTendency;
        int base = (int) lerp(social, 300000, 60000); // 5min to 1min
        return Math.max(30000, (int) HumanizedRandom.logNormal(Math.log(base), 0.4));
    }

    /**
     * Force refresh session modifiers (called periodically).
     */
    public void refreshSessionModifiers() {
        long now = System.currentTimeMillis();
        if (now - lastModifierUpdate < 30000) return; // Update every 30s

        lastModifierUpdate = now;
        sessionSpeedModifier = session.getSpeedModifier();
        sessionVarianceBoost = session.getVarianceBoost();
    }

    /**
     * Get average delay for monitoring (not for gameplay logic).
     */
    public double getAverageDelay() {
        if (totalActions == 0) return 0;
        return totalDelayMs / (double) totalActions;
    }

    // ------------------------------------------------------------------

    private int getBaseDelay(ActionContext ctx) {
        switch (ctx) {
            case COMBAT_ATTACK:     return 120;
            case COMBAT_SKILL:      return 200;
            case COMBAT_BUFF:       return 350;
            case MOVEMENT_START:    return 180;
            case MOVEMENT_UPDATE:   return 80;
            case MOVEMENT_ARRIVE:   return 250;
            case INVENTORY_USE:     return 400;
            case INVENTORY_SORT:    return 600;
            case NPC_INTERACT:      return 800;
            case NPC_SHOP_ACTION:   return 500;
            case SOCIAL_CHAT:       return 1500;
            case SOCIAL_EMOTE:      return 300;
            case IDLE_PAUSE:        return 2000;
            case CAMERA_ADJUST:     return 150;
            case LOOT_PICKUP:       return 300;
            default:                return 200;
        }
    }

    private int getVariance(ActionContext ctx, int base) {
        double varianceRatio;
        switch (ctx) {
            case COMBAT_ATTACK:
            case COMBAT_SKILL:
                varianceRatio = 0.20; // Tighter in combat
                break;
            case NPC_SHOP_ACTION:
            case INVENTORY_SORT:
                varianceRatio = 0.35; // More relaxed
                break;
            case SOCIAL_CHAT:
                varianceRatio = 0.50; // Typing is very variable
                break;
            case IDLE_PAUSE:
                varianceRatio = 0.80; // Huge variance
                break;
            default:
                varianceRatio = 0.30;
        }
        return (int) (base * varianceRatio);
    }

    private int getMinDelay(ActionContext ctx) {
        switch (ctx) {
            case COMBAT_ATTACK: return 50;
            case COMBAT_SKILL:  return 80;
            case MOVEMENT_UPDATE: return 50;
            default:            return 20;
        }
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
