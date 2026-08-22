package com.aiplayer.behavior;
import java.util.Random;
import java.util.logging.Logger;

import com.aiplayer.core.DeterministicRandom;

public class HumanReactionSimulator {
    private static final Logger LOGGER = Logger.getLogger(HumanReactionSimulator.class.getName());
    private final Random random;

    /** Default: deterministic fleet-wide seed (EB-02 reproducible runs). */
    public HumanReactionSimulator() {
        this(DeterministicRandom.forFleet("human-reaction").nextLong());
    }

    /** Seeded: pass a per-bot seed for a bot-specific, reproducible reaction stream. */
    public HumanReactionSimulator(long seed) {
        this.random = new Random(seed);
    }

    public static class ReactionTiming {
        public final long minMs;
        public final long maxMs;
        public final long avgMs;

        public ReactionTiming(long min, long max) {
            minMs = min; maxMs = max; avgMs = (min + max) / 2;
        }
    }

    public ReactionTiming generateTiming() {
        // Human reaction: 150-300ms + thinking: 50-100ms
        long base = 150 + random.nextInt(150);
        long thinking = 50 + random.nextInt(50);
        return new ReactionTiming(base, base + 100);
    }

    public long getHumanDelay() {
        return generateTiming().avgMs + random.nextInt(100);
    }

    public boolean shouldStutter(int ping, int actionComplexity) {
        return random.nextDouble() < (ping / 1000.0) * (actionComplexity / 10.0);
    }
}
