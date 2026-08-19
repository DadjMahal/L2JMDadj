package com.aiplayer.phase0.humanize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}