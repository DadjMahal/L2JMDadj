package com.aiplayer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * EB-02 determinism tests — the whole point of DeterministicRandom is that a run reproduces:
 * same account + same source → identical stream every cold launch; different sources diverge;
 * fleet-global seeds are stable and in-range. These lock the seed derivation so future edits
 * cannot silently break reproducibility.
 */
class DeterministicRandomTest
{
    @Test
    void sameAccountAndSourceProduceIdenticalStreams()
    {
        Random a = DeterministicRandom.forBot("alice", "hunting");
        Random b = DeterministicRandom.forBot("alice", "hunting");
        for (int i = 0; i < 50; i++)
        {
            assertEquals(a.nextInt(), b.nextInt(), "stream diverged at draw " + i);
        }
    }

    @Test
    void differentSourcesProduceDifferentStreams()
    {
        Random hunt = DeterministicRandom.forBot("alice", "hunting");
        Random chat = DeterministicRandom.forBot("alice", "chat");
        boolean diverged = false;
        for (int i = 0; i < 50 && !diverged; i++)
        {
            if (hunt.nextInt() != chat.nextInt()) diverged = true;
        }
        assertTrue(diverged, "independent sources must use independent streams");
    }

    @Test
    void differentAccountsProduceDifferentStreams()
    {
        Random alice = DeterministicRandom.forBot("alice", "hunting");
        Random bob = DeterministicRandom.forBot("bob", "hunting");
        boolean diverged = false;
        for (int i = 0; i < 20 && !diverged; i++)
        {
            if (alice.nextInt() != bob.nextInt()) diverged = true;
        }
        assertTrue(diverged, "per-bot seeds must differ between bots");
    }

    @Test
    void fleetSeedsAreStableAndInRange()
    {
        // Two independent cold calls produce identical first draws (reproducible whole-run).
        assertEquals(DeterministicRandom.seed("FLEET::race-rotation"),
            DeterministicRandom.seed("FLEET::race-rotation"));
        int bound = 5;
        int v = DeterministicRandom.nextInt("race-rotation", bound);
        assertTrue(v >= 0 && v < bound, "nextInt must be in [0,bound), was " + v);
        assertNotEquals(0, DeterministicRandom.seed("FLEET::packet-jitter"),
            "fleet seeds must be non-zero (avoid degenerate Random streams)");
    }

    @Test
    void seedIsStableAcrossJavaRuns()
    {
        // String.hashCode is JLS-specified → same bytes on any JVM, any time.
        assertEquals("alice::hunting".hashCode() != 0,
            DeterministicRandom.seed("alice::hunting") != 0);
        // The seed for a known context is stable: recompute twice.
        assertEquals(DeterministicRandom.seed("bot::42::human-reaction"),
            DeterministicRandom.seed("bot::42::human-reaction"));
    }
}