package com.aiplayer.behavior.humanize;

/**
 * EP-5 consolidation: the low-traffic humanization helpers that previously lived as one-class
 * files — HumanizedRandom, BehavioralFingerprint, SessionVariance, ImperfectionInjector (with
 * its ReactionDelay/AFKModule parts) — now nest here. The high-traffic facades
 * (AntiDetectionEngine, TimingJitter, InputRandomizer) stay as their own files.
 * Import a member directly, e.g. {@code import ...humanize.Humanization.HumanizedRandom;},
 * so call sites keep using the simple name.
 */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class Humanization {

    private Humanization() {}

    /** MODE: COMPLETE (re-verified 2026-08-19, S10-T08). Human-like random number distributions.
     * Uniform random looks artificial to detection systems.
     * Humans exhibit:
     * - Normal (Gaussian) distributions for reaction times
     * - Log-normal for action intervals (many short, few long pauses)
     * - Bimodal distributions for some behaviors (fast vs deliberate modes)
     * - Occasional outliers (distractions, lag spikes, bathroom breaks)
     *
     * All methods use ThreadLocalRandom by default but can accept a seed
     * for reproducible testing.
     */
    public static final class HumanizedRandom {

        private HumanizedRandom() {}

        /**
         * Normal (Gaussian) distribution.
         * Most values cluster around mean with spread controlled by sigma.
         */
        public static double normal(double mean, double sigma) {
            return mean + ThreadLocalRandom.current().nextGaussian() * sigma;
        }

        public static double normal(double mean, double sigma, Random rnd) {
            return mean + rnd.nextGaussian() * sigma;
        }

        /**
         * Log-normal distribution.
         * Skewed toward lower values with occasional long tails.
         * Perfect for: time between actions, session lengths, pause durations.
         */
        public static double logNormal(double mu, double sigma) {
            return Math.exp(ThreadLocalRandom.current().nextGaussian() * sigma + mu);
        }

        public static double logNormal(double mu, double sigma, Random rnd) {
            return Math.exp(rnd.nextGaussian() * sigma + mu);
        }

        /**
         * Bimodal distribution — two peaks.
         * Humans often switch between "fast mode" and "careful mode".
         * Example: combat (fast) vs inventory management (careful).
         */
        public static double bimodal(double mean1, double sigma1, double mean2, double sigma2, double weight1) {
            Random rnd = ThreadLocalRandom.current();
            if (rnd.nextDouble() < weight1) {
                return normal(mean1, sigma1, rnd);
            } else {
                return normal(mean2, sigma2, rnd);
            }
        }

        /**
         * Human reaction time with occasional outliers.
         * Models: normal reaction + rare distraction/lag spikes.
         */
        public static int reactionTime(int baseMs, double sigmaMs, double outlierChance, int outlierExtraMs) {
            Random rnd = ThreadLocalRandom.current();
            double val = normal(baseMs, sigmaMs, rnd);
            if (rnd.nextDouble() < outlierChance) {
                val += logNormal(Math.log(outlierExtraMs), 0.5);
            }
            return Math.max(50, (int) val);
        }

        /**
         * Action interval with human-like clustering.
         * Most actions are quick, but occasional long pauses occur.
         */
        public static int actionInterval(int baseMs, double varianceRatio) {
            double sigma = baseMs * varianceRatio;
            double val = logNormal(Math.log(baseMs), sigma / baseMs);
            return Math.max(20, (int) val);
        }

        /**
         * Perturb a value with human-like noise.
         * Used for: mouse coordinates, camera angles, movement waypoints.
         */
        public static int perturb(int value, int maxDeviation) {
            if (maxDeviation <= 0) return value;
            double noise = normal(0, maxDeviation / 3.0);
            return value + (int) Math.round(noise);
        }

        public static double perturb(double value, int maxDeviation) {
            if (maxDeviation <= 0) return value;
            return value + normal(0, maxDeviation / 3.0);
        }

        /**
         * Skewed random — more likely to return values toward one end.
         * factor > 0: skewed toward max
         * factor < 0: skewed toward min
         */
        public static int skewedInt(int min, int max, double skewFactor) {
            if (min >= max) return min;
            double u = ThreadLocalRandom.current().nextDouble();
            double skewed = Math.pow(u, Math.exp(-skewFactor));
            return min + (int) (skewed * (max - min));
        }

        /**
         * Occasionally returns a value from the extreme end.
         * Models human inconsistency — usually consistent, sometimes wild.
         */
        public static int withOccasionalExtreme(int base, int deviation, double extremeChance, int extremeMultiplier) {
            Random rnd = ThreadLocalRandom.current();
            if (rnd.nextDouble() < extremeChance) {
                return base + (rnd.nextBoolean() ? 1 : -1) * deviation * extremeMultiplier;
            }
            return perturb(base, deviation);
        }

        /**
         * Poisson-like distribution for discrete events.
         * Good for: chat messages per minute, emote frequency, skill usage variance.
         */
        public static int poissonLike(double lambda) {
            // Knuth's method for Poisson
            double L = Math.exp(-lambda);
            double p = 1.0;
            int k = 0;
            Random rnd = ThreadLocalRandom.current();
            do {
                k++;
                p *= rnd.nextDouble();
            } while (p > L);
            return k - 1;
        }

        /**
         * Human-like APM (actions per minute) with fatigue curve.
         * Returns APM that slowly degrades over session duration.
         */
        public static int apmWithFatigue(int baseApm, double stamina, int sessionMinutes) {
            // Fatigue: linear degradation based on stamina
            double fatigueRate = (1.0 - stamina) * 0.008; // per minute
            double currentApm = baseApm * Math.max(0.4, 1.0 - fatigueRate * sessionMinutes);
            // Add noise
            currentApm = normal(currentApm, currentApm * 0.15);
            return Math.max(10, (int) currentApm);
        }

        /**
         * Random delay that mimics human "thinking" before acting.
         * Combines base reaction + situational processing time.
         */
        public static int thinkDelay(int baseMs, int complexityMs, double stressFactor) {
            double total = baseMs + complexityMs * stressFactor;
            // Under stress, variance increases but mean decreases (panic vs focus)
            double sigma = total * 0.2 * (1.0 + stressFactor);
            double val = normal(total, sigma);
            return Math.max(50, (int) val);
        }
    }

    /** MODE: PARTIAL.
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
    public static final class BehavioralFingerprint {

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

    /** MODE: PARTIAL.
     * Models session-level variance in AI Player behavior.
     * Real humans don't play identically every session:
     * - Morning sessions: slower, more cautious
     * - Late night sessions: tired, more mistakes, longer pauses
     * - Weekend sessions: longer, more social
     * - Weekday sessions: shorter, focused, efficient
     * - Session fatigue: APM degrades, reaction times increase
     * - Day-to-day variance: some days you're "on", some days you're off
     *
     * This class provides session-wide modifiers that shift the baseline
     * behavior of the AI Player for the entire session, creating
     * believable long-term patterns.
     */
    public static final class SessionVariance {

        public enum PlayTime {
            MORNING,      // 06:00 - 11:00
            AFTERNOON,    // 11:00 - 17:00
            EVENING,      // 17:00 - 22:00
            NIGHT,        // 22:00 - 02:00
            LATE_NIGHT    // 02:00 - 06:00
        }

        public enum DayType {
            WEEKDAY,
            WEEKEND,
            HOLIDAY     // Phase 1: configurable holidays
        }

        private final String accountName;
        private final BehavioralFingerprint fingerprint;

        // Session start metadata
        private final long sessionStartMs;
        private final PlayTime playTime;
        private final DayType dayType;
        private final int sessionSeed;

        // Session state
        private long lastActivityMs;
        private int actionsThisSession = 0;
        private int chatMessagesThisSession = 0;
        private int deathsThisSession = 0;

        // Cached modifiers (recalculated periodically)
        private double cachedSpeedMod = 1.0;
        private double cachedVarianceMod = 1.0;
        private double cachedAccuracyMod = 1.0;
        private long lastCacheUpdate = 0;

        public SessionVariance(String accountName, BehavioralFingerprint fingerprint) {
            this.accountName = accountName;
            this.fingerprint = fingerprint;
            this.sessionStartMs = System.currentTimeMillis();
            this.lastActivityMs = sessionStartMs;

            // Determine session characteristics from real-world time
            LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
            this.playTime = classifyPlayTime(now.getHour());
            this.dayType = classifyDayType(now.getDayOfWeek());

            // Deterministic session seed from account + date
            this.sessionSeed = accountName.hashCode() + (int) (sessionStartMs / 86400000L);

            // Initial modifiers
            recalculateModifiers();
        }

        /**
         * Get session fatigue factor (0.0 = fresh, 1.0 = exhausted).
         * Increases over session duration based on stamina fingerprint.
         */
        public double getSessionFatigue() {
            long sessionMinutes = (System.currentTimeMillis() - sessionStartMs) / 60000L;
            int expectedDuration = fingerprint.avgSessionMinutes;

            if (sessionMinutes <= expectedDuration * 0.3) {
                return 0.0; // Warmup phase
            }

            double fatigue = (sessionMinutes - expectedDuration * 0.3) / (expectedDuration * 0.7);
            // Stamina fingerprint extends session endurance
            fatigue *= (1.0 - fingerprint.sessionStamina * 0.5);

            return clamp(fatigue);
        }

        /**
         * Get speed modifier for this session.
         * Morning = slower, Night = faster but sloppier
         */
        public double getSpeedModifier() {
            recalculateIfNeeded();
            return cachedSpeedMod;
        }

        /**
         * Get variance boost for this session.
         * Tired/sleepy sessions have higher variance.
         */
        public double getVarianceBoost() {
            recalculateIfNeeded();
            return cachedVarianceMod;
        }

        /**
         * Get accuracy modifier for this session.
         * Late night = less accurate
         */
        public double getAccuracyModifier() {
            recalculateIfNeeded();
            return cachedAccuracyMod;
        }

        /**
         * Should the player take a break soon?
         */
        public boolean shouldTakeBreak() {
            long sessionMinutes = (System.currentTimeMillis() - sessionStartMs) / 60000L;
            double fatigue = getSessionFatigue();

            // Natural break points based on stamina
            int breakInterval = (int) lerp(fingerprint.sessionStamina, 30, 90);
            boolean intervalTrigger = sessionMinutes > 0 && sessionMinutes % breakInterval < 2;
            boolean fatigueTrigger = fatigue > 0.75;

            return intervalTrigger || fatigueTrigger;
        }

        /**
         * Get recommended break duration in minutes.
         */
        public int getBreakDurationMinutes() {
            double fatigue = getSessionFatigue();
            return (int) lerp(fatigue, 3, 15);
        }

        /**
         * Should player end session soon?
         */
        public boolean shouldEndSession() {
            long sessionMinutes = (System.currentTimeMillis() - sessionStartMs) / 60000L;
            double fatigue = getSessionFatigue();

            // End session if exceeded expected duration + variance
            int maxDuration = fingerprint.avgSessionMinutes + ThreadLocalRandom.current().nextInt(15, 45);
            return sessionMinutes > maxDuration || fatigue > 0.9;
        }

        /**
         * Get social modifier for this session.
         * Weekends/evenings = more social
         */
        public double getSocialModifier() {
            double base = fingerprint.socialTendency;

            switch (playTime) {
                case EVENING: base *= 1.3; break;
                case NIGHT:   base *= 1.2; break;
                case MORNING: base *= 0.7; break;
                case LATE_NIGHT: base *= 0.5; break;
                default: break;
            }

            if (dayType == DayType.WEEKEND) {
                base *= 1.25;
            }

            return clamp(base);
        }

        /**
         * Get risk modifier for this session.
         * Late night = more reckless, Morning = more cautious
         */
        public double getRiskModifier() {
            double base = fingerprint.riskTolerance;

            switch (playTime) {
                case NIGHT:
                case LATE_NIGHT:
                    base *= 1.2; // More reckless when tired
                    break;
                case MORNING:
                    base *= 0.85; // Cautious when groggy
                    break;
                default:
                    break;
            }

            // Deaths make player more cautious
            base *= Math.max(0.5, 1.0 - deathsThisSession * 0.15);

            return clamp(base);
        }

        /**
         * Record an action for fatigue tracking.
         */
        public void recordAction() {
            actionsThisSession++;
            lastActivityMs = System.currentTimeMillis();
        }

        /**
         * Record a chat message.
         */
        public void recordChat() {
            chatMessagesThisSession++;
        }

        /**
         * Record a death.
         */
        public void recordDeath() {
            deathsThisSession++;
        }

        /**
         * Get current play time classification.
         */
        public PlayTime getPlayTime() {
            return playTime;
        }

        /**
         * Get current day type.
         */
        public DayType getDayType() {
            return dayType;
        }

        /**
         * Get session duration in minutes.
         */
        public long getSessionDurationMinutes() {
            return (System.currentTimeMillis() - sessionStartMs) / 60000L;
        }

        /**
         * Get total actions taken in this session (for status reporting).
         */
        public int getActionsThisSession() {
            return actionsThisSession;
        }

        /**
         * Get session summary for logging/monitoring.
         */
        public String getSessionSummary() {
            return String.format(
                "Session[%s: %s %s, %dmin, actions=%d, chats=%d, deaths=%d, fatigue=%.2f]",
                accountName, dayType, playTime, getSessionDurationMinutes(),
                actionsThisSession, chatMessagesThisSession, deathsThisSession,
                getSessionFatigue()
            );
        }

        // ------------------------------------------------------------------

        private void recalculateIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - lastCacheUpdate < 60000) return; // Recalc every minute
            recalculateModifiers();
        }

        private void recalculateModifiers() {
            lastCacheUpdate = System.currentTimeMillis();

            double speed = 1.0;
            double variance = 1.0;
            double accuracy = 1.0;

            // Time-of-day effects
            switch (playTime) {
                case MORNING:
                    speed *= 0.92;
                    variance *= 1.05;
                    accuracy *= 0.95;
                    break;
                case AFTERNOON:
                    speed *= 1.0;
                    variance *= 1.0;
                    accuracy *= 1.0;
                    break;
                case EVENING:
                    speed *= 1.05;
                    variance *= 0.95;
                    accuracy *= 1.02;
                    break;
                case NIGHT:
                    speed *= 1.08;
                    variance *= 1.15;
                    accuracy *= 0.92;
                    break;
                case LATE_NIGHT:
                    speed *= 0.95;
                    variance *= 1.3;
                    accuracy *= 0.85;
                    break;
            }

            // Day type effects
            if (dayType == DayType.WEEKEND) {
                speed *= 0.95; // More relaxed
                variance *= 1.1;
            }

            // Fatigue effects
            double fatigue = getSessionFatigue();
            speed *= Math.max(0.7, 1.0 - fatigue * 0.25);
            variance *= 1.0 + fatigue * 0.4;
            accuracy *= Math.max(0.75, 1.0 - fatigue * 0.2);

            // Day-to-day variance (some days you're "on", some you're not)
            // Deterministic from session seed so it's consistent for the whole session
            double dailyVariance = ((sessionSeed % 100) / 100.0 - 0.5) * 0.2;
            speed *= (1.0 + dailyVariance);
            accuracy *= (1.0 - dailyVariance * 0.5);

            cachedSpeedMod = clamp(speed);
            cachedVarianceMod = clamp(variance);
            cachedAccuracyMod = clamp(accuracy);
        }

        private static PlayTime classifyPlayTime(int hour) {
            if (hour >= 6 && hour < 11) return PlayTime.MORNING;
            if (hour >= 11 && hour < 17) return PlayTime.AFTERNOON;
            if (hour >= 17 && hour < 22) return PlayTime.EVENING;
            if (hour >= 22 || hour < 2) return PlayTime.NIGHT;
            return PlayTime.LATE_NIGHT;
        }

        private static DayType classifyDayType(DayOfWeek day) {
            return (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                ? DayType.WEEKEND
                : DayType.WEEKDAY;
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private static double clamp(double v) {
            return Math.max(0.1, Math.min(2.0, v));
        }
    }

    /** MODE: PARTIAL. Occasional reaction stalls + AFK windows, BotBrain's imperfection seam. */
    public static final class ImperfectionInjector {
        private final Random rng = new Random();
        private final ReactionDelay reactionDelay;
        private final AFKModule afkModule;

        public ImperfectionInjector() {
            this.reactionDelay = new ReactionDelay();
            this.afkModule = new AFKModule();
        }

        public boolean shouldDelay() { return reactionDelay.shouldDelay(); }
        public boolean isAFK() { return afkModule.isAFK(); }
        public long getAFKDurationMs() { return afkModule.getDurationMs(); }

        /** 30% chance of a 50-300ms stall. */
        private static final class ReactionDelay {
            private final Random rng = new Random();
            private long delayUntil = 0;

            public boolean shouldDelay() {
                long now = System.currentTimeMillis();
                if (now < delayUntil) return true;
                if (rng.nextDouble() < 0.30) {
                    int delayMs = 50 + rng.nextInt(250);
                    delayUntil = now + delayMs;
                    return true;
                }
                return false;
            }
        }

        /** ~5% chance per minute of a 2-5min AFK window. */
        private static final class AFKModule {
            private final Random rng = new Random();
            private long afkUntil = 0;
            private long nextCheck = 0;

            public boolean isAFK() {
                long now = System.currentTimeMillis();
                if (now < afkUntil) return true;
                if (now < nextCheck) return false;
                nextCheck = now + 60_000;
                if (rng.nextDouble() < 0.05 / 60.0) {
                    int durationSec = 120 + rng.nextInt(180);
                    afkUntil = now + durationSec * 1000L;
                    return true;
                }
                return false;
            }

            public long getDurationMs() {
                return Math.max(0, afkUntil - System.currentTimeMillis());
            }
        }
    }
}
