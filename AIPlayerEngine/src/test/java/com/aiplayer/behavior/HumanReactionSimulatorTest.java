package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * EB-05: locks the LIVE humanize knob (HumanReactionSimulator) — deterministic per seed
 * (reproducible runs) and within a human reaction band (the value BotSession now adds to the
 * per-tick sleep, so it must never turn a 300 ms tick into a multi-second stall).
 */
class HumanReactionSimulatorTest
{
    @Test
    void sameSeedReproducesSameDelays()
    {
        HumanReactionSimulator a = new HumanReactionSimulator(12345L);
        HumanReactionSimulator b = new HumanReactionSimulator(12345L);
        for (int i = 0; i < 20; i++)
        {
            assertEquals(a.getHumanDelay(), b.getHumanDelay(),
                "reaction stream diverged at draw " + i);
        }
    }

    @Test
    void delayIsBoundedAndHumanSized()
    {
        HumanReactionSimulator r = new HumanReactionSimulator(42L);
        for (int i = 0; i < 100; i++)
        {
            long d = r.getHumanDelay();
            assertTrue(d >= 150 && d <= 600, "human reaction delay sane, got " + d);
        }
    }

    @Test
    void stutterChanceIsBounded()
    {
        HumanReactionSimulator r = new HumanReactionSimulator(7L);
        int yes = 0;
        for (int i = 0; i < 1000; i++)
        {
            if (r.shouldStutter(40, 5)) yes++;
        }
        assertTrue(yes > 0 && yes < 300, "stutter mostly off at low ping, got " + yes);
    }
}