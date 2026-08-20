package com.aiplayer.protocol;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;

public class PacketJitter {
    private final Random rng = new Random();
    private final double sigmaMs;

    public PacketJitter(double sigmaMs) {
        this.sigmaMs = sigmaMs;
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
