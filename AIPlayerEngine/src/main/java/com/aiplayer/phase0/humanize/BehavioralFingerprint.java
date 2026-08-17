package com.aiplayer.phase0.humanize;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a unique, deterministic behavioral fingerprint for each AI Player.
 * The fingerprint is derived from the account name hash, ensuring consistency
 * across sessions while maintaining uniqueness per player.
 *
 * Each AI Player has distinct traits:
 * - Reaction time (fast/medium/slow reflexes)
 * - Input accuracy (precise vs sloppy clicks/movement)
 * - Risk tolerance (aggressive vs cautious play)
 * - Social tendency (chatty vs quiet)
 * - Session stamina (short vs long play sessions)
 * - APM baseline (actions per minute tendency)
 * - Error proneness (occasional mistakes, misclicks)
 *
 * These traits create a consistent "personality" that makes each AI Player
 * indistinguishable from a real human with their own play style.
 */
public final class BehavioralFingerprint {

    // Trait ranges (0.0 = minimum, 1.0 = maximum)
    public final double reactionTime;      // 0.0=fast(150ms), 1.0=slow(800ms)
    public final double inputAccuracy;     // 0.0=sloppy, 1.0=precise
    public final double riskTolerance;     // 0.0=cautious, 1.0=aggressive
    public final double socialTendency;    // 0.0=quiet, 1.0=chatty
    public final double sessionStamina;    // 0.0=short sessions, 1.0=marathon
    public final double apmBaseline;       // 0.0=low APM, 1.0=high APM
    public final double errorProneness;    // 0.0=perfect, 1.0=error-prone
    public final double multitasking;      // 0.0=focused, 1.0=distracted
    public final double cameraActivity;    // 0.0=static camera, 1.0=always rotating
    public final double lootPriority;      // 0.0=ignore loot, 1.0=loot everything

    // Derived values (cached)
    public final int baseReactionMs;
    public final int missclickChancePercent;
    public final int avgSessionMinutes;
    public final int targetApm;
    public final double pauseFrequency;    // Chance to pause between actions

    private final String accountName;
    private final long fingerprintSeed;

    public BehavioralFingerprint(String accountName) {
        this.accountName = accountName;
        this.fingerprintSeed = deriveSeed(accountName);

        // Generate traits deterministically from seed
        // Use a simple LCG to derive each trait so they're independent but consistent
        long s = fingerprintSeed;

        this.reactionTime = clamp(normFromSeed(s = lcg(s), 0.35, 0.75));
        this.inputAccuracy = clamp(normFromSeed(s = lcg(s), 0.50, 0.85));
        this.riskTolerance = clamp(normFromSeed(s = lcg(s), 0.30, 0.70));
        this.socialTendency = clamp(normFromSeed(s = lcg(s), 0.20, 0.60));
        this.sessionStamina = clamp(normFromSeed(s = lcg(s), 0.25, 0.80));
        this.apmBaseline = clamp(normFromSeed(s = lcg(s), 0.30, 0.70));
        this.errorProneness = clamp(normFromSeed(s = lcg(s), 0.10, 0.40));
        this.multitasking = clamp(normFromSeed(s = lcg(s), 0.20, 0.55));
        this.cameraActivity = clamp(normFromSeed(s = lcg(s), 0.30, 0.75));
        this.lootPriority = clamp(normFromSeed(s = lcg(s), 0.40, 0.90));

        // Derive concrete values
        this.baseReactionMs = (int) lerp(reactionTime, 180, 650);
        this.missclickChancePercent = (int) lerp(errorProneness, 1, 12);
        this.avgSessionMinutes = (int) lerp(sessionStamina, 45, 240);
        this.targetApm = (int) lerp(apmBaseline, 25, 85);
        this.pauseFrequency = lerp(multitasking, 0.02, 0.15);
    }

    /**
     * Get a seeded random value for this fingerprint.
     * Used when you need the SAME random sequence across sessions
     * for this specific player (e.g., consistent "luck" patterns).
     */
    public long getConsistentRandom(long salt) {
        return lcg(fingerprintSeed + salt);
    }

    /**
     * Check if this player should make a mistake right now.
     * Uses error proneness + current context to decide.
     */
    public boolean shouldMakeMistake(double stressFactor) {
        double chance = errorProneness * stressFactor * 0.5;
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /**
     * Should this player pause/distract right now?
     */
    public boolean shouldPause() {
        return ThreadLocalRandom.current().nextDouble() < pauseFrequency;
    }

    /**
     * Get recommended pause duration in ms based on multitasking trait.
     */
    public int getPauseDurationMs() {
        return (int) lerp(multitasking, 800, 5000);
    }

    public String getAccountName() {
        return accountName;
    }

    public long getSeed() {
        return fingerprintSeed;
    }

    // ------------------------------------------------------------------

    private static long deriveSeed(String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(name.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            // Fallback
            return name.hashCode();
        }
    }

    private static long lcg(long seed) {
        // Parameters from Numerical Recipes
        return (seed * 1664525L + 1013904223L) & 0xFFFFFFFFL;
    }

    private static double normFromSeed(long seed, double mean, double max) {
        // Simple normal-ish distribution using central limit theorem
        double sum = 0;
        long s = seed;
        for (int i = 0; i < 6; i++) {
            s = lcg(s);
            sum += (s / (double) 0xFFFFFFFFL);
        }
        double normal = sum / 6.0; // Approx normal, mean ~0.5
        double val = mean + (normal - 0.5) * 2.0 * (max - mean);
        return val;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Override
    public String toString() {
        return String.format(
            "Fingerprint[%s: react=%.2f acc=%.2f risk=%.2f social=%.2f stamina=%.2f apm=%d err=%.2f]",
            accountName, reactionTime, inputAccuracy, riskTolerance,
            socialTendency, sessionStamina, targetApm, errorProneness
        );
    }
}
