package com.aiplayer.behavior.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * EB-10 — locks the drain executor with real threads: interruption stops a cooperative worker,
 * the count honours the deadline, null/empty are safe.
 */
class FleetDrainTest
{
    @Test
    void interruptsAndStopsCooperativeWorkers()
    {
        Thread[] bots = new Thread[3];
        for (int i = 0; i < bots.length; i++)
        {
            bots[i] = Thread.ofVirtual().name("drain-test-" + i).start(() ->
            {
                try
                {
                    while (!Thread.currentThread().isInterrupted())
                    {
                        Thread.sleep(10);
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            });
        }
        int stopped = FleetDrain.drain(bots, 2_000L);
        assertEquals(3, stopped, "all cooperative workers must drain");
        for (Thread t : bots)
        {
            assertTrue(!t.isAlive(), "thread must be stopped after drain");
        }
    }

    @Test
    void drainIsBoundedByDeadline()
    {
        // A worker that ignores interruption must NOT block the drain past the budget.
        Thread[] bots = new Thread[] { Thread.ofVirtual().start(() -> {
            long end = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < end)
            {
                Thread.onSpinWait();
            }
        }) };
        Thread.currentThread().setName("drain-deadline-test");
        long start = System.currentTimeMillis();
        int stopped = FleetDrain.drain(bots, 200L);
        long took = System.currentTimeMillis() - start;
        assertTrue(took < 3_000, "drain must respect the deadline: took " + took + "ms");
        assertEquals(0, stopped, "ignorant worker is not stopped within the window");
        bots[0].interrupt();
    }

    @Test
    void nullOrEmptyIsZero()
    {
        assertEquals(0, FleetDrain.drain(null, 100L));
        assertEquals(0, FleetDrain.drain(new Thread[0], 100L));
    }
}