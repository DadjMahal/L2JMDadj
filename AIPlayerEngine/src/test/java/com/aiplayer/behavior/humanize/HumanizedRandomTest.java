package com.aiplayer.behavior.humanize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplayer.behavior.humanize.Humanization.HumanizedRandom;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** S10-T08: locks HumanizedRandom (flipped MODE:PARTIAL -> COMPLETE). */
class HumanizedRandomTest
{
    @Test
    void zeroSigmaIsStable()
    {
        double v = HumanizedRandom.normal(42.0, 0.0, new Random(1));
        assertTrue(Double.isFinite(v), "sigma 0 must not produce NaN/inf");
    }

    @Test
    void seededNormalIsFiniteAndNearMean()
    {
        double v = HumanizedRandom.normal(100.0, 5.0, new Random(7));
        assertTrue(Double.isFinite(v) && v > 80 && v < 120, "seeded normal near the mean, got " + v);
    }

    @Test
    void logNormalIsPositive()
    {
        for (int i = 0; i < 20; i++)
        {
            double v = HumanizedRandom.logNormal(3.0, 0.5, new Random(i));
            assertTrue(v > 0, "logNormal must be positive, got " + v);
        }
    }

    @Test
    void bimodalIsFinite()
    {
        double v = HumanizedRandom.bimodal(10, 1, 30, 1, 0.5);
        assertTrue(Double.isFinite(v), "bimodal produces a finite number");
    }
// ================================================================
    // EB-05 — the no-arg (fleet-stream) forms must be reproducible AND varied
    // ================================================================

    @Test
    void noArgNormalIsFiniteAroundMean()
    {
        for (int i = 0; i < 50; i++)
        {
            double v = HumanizedRandom.normal(100.0, 5.0);
            assertTrue(Double.isFinite(v) && v > 60 && v < 140, "no-arg normal near mean, got " + v);
        }
    }

    @Test
    void noArgDrawsAdvanceSoTheyVary()
    {
        // The shared fleet stream must ADVANCE (not be a frozen constant per call).
        java.util.Set<Double> seen = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++)
        {
            seen.add((double) Math.round(HumanizedRandom.actionInterval(1000, 0.2)));
        }
        assertTrue(seen.size() >= 3, "no-arg draws should vary across calls, saw " + seen.size() + " distinct");
    }

    @Test
    void noArgPoissonIsNonNegative()
    {
        for (int i = 0; i < 20; i++)
        {
            int v = HumanizedRandom.poissonLike(3.0);
            assertTrue(v >= 0 && v < 30, "poisson sane, got " + v);
        }
    }

    @Test
    void noArgReactionTimeIsSane()
    {
        for (int i = 0; i < 20; i++)
        {
            int v = HumanizedRandom.reactionTime(200, 40, 0.2, 500);
            // base 200 + Gaussian spread + occasional log-normal outlier (can reach ~1s): real humans
            // DO have lag/distraction spikes — bound only the extremes we must never hit.
            assertTrue(v >= 50 && v <= 1400, "reaction time sane, got " + v);
        }
    }
}