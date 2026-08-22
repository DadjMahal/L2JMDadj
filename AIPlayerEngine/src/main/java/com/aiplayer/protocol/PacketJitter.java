package com.aiplayer.protocol;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;
import com.aiplayer.core.DeterministicRandom;

public class PacketJitter {
    private final Random rng;
    private final double sigmaMs;

    /** Default constructor: deterministic fleet-wide seed (EB-02 reproducible runs). */
    public PacketJitter(double sigmaMs) {
        this(sigmaMs, DeterministicRandom.forFleet("packet-jitter").nextLong());
    }

    /** Seeded constructor: pass a per-bot seed for a bot-specific, reproducible jitter stream. */
    public PacketJitter(double sigmaMs, long seed) {
        this.sigmaMs = sigmaMs;
        this.rng = new Random(seed);
    }

    public long nextDelayMs() {
        long d = Math.round(rng.nextGaussian() * sigmaMs);
        return Math.max(0, d);
    }

    public void sleep() {
        try {
            Thread.sleep(nextDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
