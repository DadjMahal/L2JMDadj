package com.aiplayer.behavior.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * EB-13 — locks the per-bot sliding-window rate/backpressure guard: budget honored, window
 * expiry frees slots, backpressure counts refusals, reset clears history.
 */
class PerBotLimiterTest
{
    @Test
    void permitsUpToBudget()
    {
        PerBotLimiter limiter = new PerBotLimiter(3, 1_000L);
        long t = 10_000L;
        assertTrue(limiter.tryAcquire(t));
        assertTrue(limiter.tryAcquire(t + 1));
        assertTrue(limiter.tryAcquire(t + 2));
        assertFalse(limiter.tryAcquire(t + 3), "window full after 3 actions");
        assertEquals(0, limiter.available(t + 3));
    }

    @Test
    void windowSlidesAfterTimeout()
    {
        PerBotLimiter limiter = new PerBotLimiter(2, 1_000L);
        long t = 10_000L;
        limiter.tryAcquire(t);
        limiter.tryAcquire(t + 100);
        assertFalse(limiter.tryAcquire(t + 200), "2 of 2 used -> full");

        // After the 1s window elapses the first slot expires and frees budget.
        assertTrue(limiter.tryAcquire(t + 1_001), "oldest slot expired -> slot freed");
    }

    @Test
    void backpressureCountsRefusals()
    {
        PerBotLimiter limiter = new PerBotLimiter(2, 1_000L);
        long t = 5_000L;
        limiter.tryAcquire(t);
        limiter.tryAcquire(t + 1);
        assertEquals(0, limiter.throttledCount());
        assertFalse(limiter.tryAcquire(t + 2));
        assertFalse(limiter.tryAcquire(t + 2));
        assertEquals(2, limiter.throttledCount(), "two refused actions counted");
    }

    @Test
    void isAvailableConsumesNothing()
    {
        PerBotLimiter limiter = new PerBotLimiter(1, 1_000L);
        long t = 7_000L;
        assertTrue(limiter.isAvailable(t));
        assertEquals(1, limiter.available(t));
        limiter.tryAcquire(t);
        assertFalse(limiter.isAvailable(t));
        assertEquals(0, limiter.available(t));
        assertTrue(limiter.tryAcquire(t + 1_001), "window's only slot expired");
    }

    @Test
    void resetClearsHistory()
    {
        PerBotLimiter limiter = new PerBotLimiter(1, 1_000L);
        limiter.tryAcquire(100L);
        assertFalse(limiter.tryAcquire(101L));
        limiter.reset();
        assertTrue(limiter.tryAcquire(200L), "reset frees budget immediately");
    }

    @Test
    void rejectsNonPositiveArguments()
    {
        assertThrows(IllegalArgumentException.class, () -> new PerBotLimiter(0, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> new PerBotLimiter(5, 0L));
    }

    @Test
    void defaultsAreHealthy()
    {
        PerBotLimiter limiter = new PerBotLimiter();
        long t = 1_000L;
        for (int i = 0; i < PerBotLimiter.DEFAULT_MAX_PER_WINDOW; i++)
        {
            assertTrue(limiter.tryAcquire(t), "default budget holds " + i);
        }
        assertFalse(limiter.tryAcquire(t));
    }
}