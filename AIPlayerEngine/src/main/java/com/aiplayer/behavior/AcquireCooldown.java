package com.aiplayer.behavior;
import com.aiplayer.behavior.movement.ZoneRouter;

/**
 * MODE: COMPLETE. ACQUIRE-failure cooldown — per-bot state that stops the "re-plan the same
 * geo-unreachable quest every tick" loop. The fleet loop records each abandoned ACQUIRE route
 * (hop-unreachable giver, e.g. Wolf Hunt at Gludio ~148k units across the ocean) here; once
 * {@code maxUnreachable} aborts accumulate it suppresses new ACQUIRE goals for {@code cooldownMs}
 * so ZoneRouter.plan falls back to plain far-travel farming instead of re-issuing the dead
 * ocean-hop route. Pure POJO: no IO, no threads; a clock is always injected as an argument.
 */
public final class AcquireCooldown
{
    /** Default unreachable-route aborts (2) before the cooldown window arms. */
    public static final int DEFAULT_MAX_UNREACHABLE = 2;
    /** Default suppression window: 5 minutes. */
    public static final long DEFAULT_COOLDOWN_MS = 5 * 60 * 1000L;

    private final int maxUnreachable;
    private final long cooldownMs;
    private int unreachableAbandons;
    private long cooldownUntilMs;

    public AcquireCooldown()
    {
        this(DEFAULT_MAX_UNREACHABLE, DEFAULT_COOLDOWN_MS);
    }

    public AcquireCooldown(int maxUnreachable, long cooldownMs)
    {
        this.maxUnreachable = Math.max(1, maxUnreachable);
        this.cooldownMs = Math.max(0, cooldownMs);
        this.unreachableAbandons = 0;
        this.cooldownUntilMs = 0;
    }

    /** Convenience no-arg variant that reads the wall clock. */
    public void noteUnreachableAbandon()
    {
        noteUnreachableAbandon(System.currentTimeMillis());
    }

    /** Fleet-loop spelling for recording an abandoned ACQUIRE route; same as {@link #noteUnreachableAbandon(long)}. */
    public void recordUnreachableAbort(long nowMs)
    {
        noteUnreachableAbandon(nowMs);
    }

    /**
     * Record one ACQUIRE-route abandonment at {@code nowMs}. When the counter first reaches
     * {@code maxUnreachable}, arms the suppression window ({@code cooldownUntilMs = nowMs + cooldownMs}).
     * A later abort that arrives after a previous window has fully elapsed re-arms it (a repeated
     * failure pattern must keep cooling down, or the ocean-hop loop restarts the instant the window
     * ends); an abort arriving inside a live window never extends the deadline.
     */
    public void noteUnreachableAbandon(long nowMs)
    {
        unreachableAbandons++;
        if (unreachableAbandons >= maxUnreachable
                && (cooldownUntilMs <= 0 || nowMs > cooldownUntilMs))
        {
            cooldownUntilMs = nowMs + cooldownMs;
        }
    }

    /** True once aborts >= maxUnreachable while {@code nowMs} is inside the armed window. */
    public boolean isSuppressed(long nowMs)
    {
        return unreachableAbandons >= maxUnreachable && nowMs <= cooldownUntilMs;
    }

    /** End of the current suppression window (0 = not armed). */
    public long cooldownUntilMs()
    {
        return cooldownUntilMs;
    }

    /** Clear the abort count and any armed window (real quest progress / fresh session). */
    public void reset()
    {
        unreachableAbandons = 0;
        cooldownUntilMs = 0;
    }

    /** Abort counter so far (for "N/H" diagnostics). */
    public int unreachableCount()
    {
        return unreachableAbandons;
    }

    /** Configured threshold (H of the "N/H" diagnostics). */
    public int maxUnreachable()
    {
        return maxUnreachable;
    }

    /** Configured window length in ms. */
    public long cooldownMs()
    {
        return cooldownMs;
    }
}
