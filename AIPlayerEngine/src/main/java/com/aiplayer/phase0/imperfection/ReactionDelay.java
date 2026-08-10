package com.aiplayer.phase0.imperfection;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;

public class ReactionDelay {
    private final Random rng = new Random();
    private long delayUntil = 0;

    public boolean shouldDelay() {
        long now = System.currentTimeMillis();
        if (now < delayUntil) return true;
        if (rng.nextDouble() < 0.30) {
            int delayMs = 50 + rng.nextInt(250);
            delayUntil = now + delayMs;
            return true;
        }
        return false;
    }
}
