package com.aiplayer.behavior.humanize;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;

public class AFKModule {
    private final Random rng = new Random();
    private long afkUntil = 0;
    private long nextCheck = 0;

    public boolean isAFK() {
        long now = System.currentTimeMillis();
        if (now < afkUntil) return true;
        if (now < nextCheck) return false;
        nextCheck = now + 60_000;
        if (rng.nextDouble() < 0.05 / 60.0) {
            int durationSec = 120 + rng.nextInt(180);
            afkUntil = now + durationSec * 1000L;
            return true;
        }
        return false;
    }

    public long getDurationMs() {
        return Math.max(0, afkUntil - System.currentTimeMillis());
    }
}
