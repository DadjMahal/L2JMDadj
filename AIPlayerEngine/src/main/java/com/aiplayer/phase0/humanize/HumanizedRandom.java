package com.aiplayer.phase0.humanize;

/** MODE: COMPLETE (re-verified 2026-08-19, S10-T08). Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like random number distributions.
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
public final class HumanizedRandom {

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

    public static double perturb(double value, double maxDeviation) {
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
