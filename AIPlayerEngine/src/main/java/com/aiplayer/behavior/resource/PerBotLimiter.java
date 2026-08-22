package com.aiplayer.behavior.resource;

import java.util.ArrayDeque;

/**
 * EB-13 — PER-BOT RATE / BACKPRESSURE GUARD (pure, deterministic, thread-safe).
 *
 * <p>A sliding-window token budget on how many actions a single bot may send to the server per
 * time window. Once the window is full, {@link #tryAcquire(long)} returns {@code false} — the
 * caller must SKIP the action (backpressure) instead of bursting more frames at the server.
 *
 * <p>This is the belt-and-suspenders layer. Per-feature pacing already exists (CombatFramePlanner
 * enforces the 1000ms server flood protector; the hop/dialog drivers have their own reclick
 * gates). This guard is the umbrella: even if one feature's pacing regresses, no single bot can
 * ever exceed {@code maxPerWindow} actions in {@code windowMs}.
 *
 * <p>Life-critical actions (revive, emergency potion) are exempt BY DESIGN — the caller decides
 * which channels are rate-guarded, not this class.
 */
public final class PerBotLimiter
{
    /** Safe default: 20 actions per second — far above a healthy bot's send rate (~2-5/s). */
    public static final int DEFAULT_MAX_PER_WINDOW = 20;
    public static final long DEFAULT_WINDOW_MS = 1_000L;

    private final int maxPerWindow;
    private final long windowMs;
    private final ArrayDeque<Long> window = new ArrayDeque<>();
    private long throttledCount = 0L;

    public PerBotLimiter()
    {
        this(DEFAULT_MAX_PER_WINDOW, DEFAULT_WINDOW_MS);
    }

    public PerBotLimiter(int maxPerWindow, long windowMs)
    {
        if (maxPerWindow <= 0)
        {
            throw new IllegalArgumentException("maxPerWindow must be > 0");
        }
        if (windowMs <= 0)
        {
            throw new IllegalArgumentException("windowMs must be > 0");
        }
        this.maxPerWindow = maxPerWindow;
        this.windowMs = windowMs;
    }

    /**
     * Try to spend one action slot at {@code nowMs}.
     *
     * @return {@code true} when under budget (the action may be sent), {@code false} when the
     *         window is full — the caller must NOT send (backpressure).
     */
    public synchronized boolean tryAcquire(long nowMs)
    {
        evictExpired(nowMs);
        if (window.size() >= maxPerWindow)
        {
            throttledCount++;
            return false;
        }
        window.addLast(nowMs);
        return true;
    }

    /** Would {@link #tryAcquire(long)} currently pass? Does NOT spend a slot. */
    public synchronized boolean isAvailable(long nowMs)
    {
        evictExpired(nowMs);
        return window.size() < maxPerWindow;
    }

    /** How many more actions are allowed in the current window (>= 0). */
    public synchronized int available(long nowMs)
    {
        evictExpired(nowMs);
        return maxPerWindow - window.size();
    }

    /** How many actions were refused so far (telemetry). */
    public synchronized long throttledCount()
    {
        return throttledCount;
    }

    /** Drop all history (caller may reset the guard on reconnect/session start). */
    public synchronized void reset()
    {
        window.clear();
    }

    private void evictExpired(long nowMs)
    {
        long cutoff = nowMs - windowMs;
        while (!window.isEmpty() && window.peekFirst() < cutoff)
        {
            window.pollFirst();
        }
    }
}