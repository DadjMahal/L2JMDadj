package com.aiplayer.behavior.lifecycle;

import java.util.concurrent.TimeUnit;

/**
 * EB-10 — the DRAIN EXECUTOR (small, deterministic, interruptible).
 *
 * <p>A graceful fleet stop = ask every bot thread to finish at its next safe boundary (each
 * {@code BotSession.run()} checks {@code Thread.interrupted()} at the top of its loop) and wait
 * a bounded time for them to comply. Returns how many threads actually stopped; the remaining
 * ones are left to the JVM exit (their sockets are torn down by the process anyway).
 */
public final class FleetDrain
{
    public static final long DEFAULT_WAIT_MS = 3_000L;

    private FleetDrain()
    {
    }

    /**
     * Interrupt each live bot thread and await its termination up to {@code waitMs}.
     *
     * @return the number of threads that terminated within the window.
     */
    public static int drain(Thread[] bots, long waitMs)
    {
        if (bots == null)
        {
            return 0;
        }
        long budget = Math.max(0, waitMs);
        for (Thread t : bots)
        {
            if (t != null && t.isAlive())
            {
                t.interrupt();
            }
        }
        int stopped = 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget);
        for (Thread t : bots)
        {
            if (t == null)
            {
                continue;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0)
            {
                break;
            }
            try
            {
                t.join(TimeUnit.NANOSECONDS.toMillis(Math.max(1, remaining)));
                if (!t.isAlive())
                {
                    stopped++;
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return stopped;
    }
}