package com.aiplayer.core;

import java.util.Random;

/**
 * EB-02: the ONE place that derives a deterministic {@link Random} for every random source in
 * the engine, so a run is reproducible from its inputs (same accounts + same config → same
 * random stream every launch).
 *
 * Three classes of source:
 *  <ul>
 *   <li>Per-bot: seed = stable mix of the bot account id + a per-source salt, so every bot
 *       gets its own stream but always the same stream for the same account.</li>
 *   <li>Fleet-global (singletons / config shuffles): fixed documented base seed, so the
 *       whole run is reproducible.</li>
 *  </ul>
 *
 * <p>Rules enforced by gate: live engine code must NOT call {@code new Random()} (no-arg) or
 * {@code Math.random()} directly — always route randomness through this class (or a
 * bot-owned seeded {@link Random} built here).
 */
public final class DeterministicRandom
{
    private DeterministicRandom()
    {
    }

    /**
     * Stable 64-bit seed from a context key. {@code String.hashCode()} is specified by the Java
     * Language Spec and is identical across JVMs/runs, so this is fully reproducible; the FNV-ish
     * mixing only spreads consecutive keys that would otherwise collide.
     */
    public static long seed(String context)
    {
        long h = 0x9E3779B97F4A7C15L; // golden-ratio-ish constant, non-zero start
        for (int i = 0; i < context.length(); i++)
        {
            h = (h ^ context.charAt(i)) * 0x100000001B3L; // FNV prime
        }
        return h ^ (h >>> 32);
    }

    /** A deterministic per-bot Random: same account + source → same stream, every run. */
    public static Random forBot(String accountId, String source)
    {
        return new Random(seed(accountId + "::" + source));
    }

    /** A deterministic fleet-global Random: fixed seed → the whole run reproduces. */
    public static Random forFleet(String source)
    {
        return new Random(seed("FLEET::" + source));
    }

    /** Reproducible non-negative int in [0, bound) for fleet-level config (e.g. shuffle seeds). */
    public static int nextInt(String context, int bound)
    {
        int v = Math.floorMod(seed(context), bound);
        return v < 0 ? -v : v;
    }
}