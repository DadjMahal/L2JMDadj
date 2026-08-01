package com.aiplayer.neural;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Pattern Memory - Deep Learning Model Storage
 * Stores learned gameplay patterns with adaptive weights.
 * This is the "experience" the AI accumulates over time.
 *
 * Task 68 (Deep Learning Models) foundation component.
 */
public class PatternMemory {
    private static final Logger LOGGER = Logger.getLogger(PatternMemory.class.getName());

    /** A learned pattern: a behavior that produced a measurable outcome. */
    public static class Pattern {
        public final String context;      // e.g. "combat", "quest:Q00028", "merchant:Gludio"
        public final String action;       // e.g. "use_skill:POWER_STRIKE", "buy:IronOre"
        public final double reward;       // accumulated reward (XP gained, profit, success rate)
        public final long occurrences;    // how many times observed
        public final long lastSeen;       // timestamp of last observation

        public Pattern(String context, String action, double reward, long occurrences, long lastSeen) {
            this.context = context;
            this.action = action;
            this.reward = reward;
            this.occurrences = occurrences;
            this.lastSeen = lastSeen;
        }

        /** Confidence grows with occurrences (more data = more trust). */
        public double confidence() {
            return Math.min(1.0, occurrences / 50.0);
        }

        /** Score balances reward magnitude against confidence. */
        public double score() {
            return reward * confidence();
        }
    }

    // context -> action -> pattern (one map per context for fast lookup)
    private final Map<String, Map<String, Pattern>> memory = new ConcurrentHashMap<>();
    private final double learningRate;
    private final double decayFactor;

    public PatternMemory() {
        this(0.1, 0.95); // sensible defaults
        LOGGER.info("[PatternMemory] Initialized (learningRate=0.1, decay=0.95)");
    }

    public PatternMemory(double learningRate, double decayFactor) {
        this.learningRate = learningRate;
        this.decayFactor = decayFactor;
    }

    /**
     * Record an outcome: the AI took an action in a context and received a reward.
     * Updates the stored pattern using exponential moving average so recent
     * experience matters more than old experience.
     */
    public void record(String context, String action, double reward) {
        memory.computeIfAbsent(context, k -> new HashMap<>());
        Map<String, Pattern> ctxPatterns = memory.get(context);

        Pattern existing = ctxPatterns.get(action);
        long now = System.currentTimeMillis();
        if (existing == null) {
            ctxPatterns.put(action, new Pattern(context, action, reward, 1, now));
        } else {
            double newReward = (existing.reward * (1 - learningRate)) + (reward * learningRate);
            long newOccurrences = existing.occurrences + 1;
            ctxPatterns.put(action, new Pattern(context, action, newReward, newOccurrences, now));
        }
    }

    /**
     * Retrieve the best known action for a given context.
     * Returns null if no patterns have been learned for this context.
     */
    public Pattern bestPattern(String context) {
        Map<String, Pattern> ctxPatterns = memory.get(context);
        if (ctxPatterns == null || ctxPatterns.isEmpty()) {
            return null;
        }
        Pattern best = null;
        for (Pattern p : ctxPatterns.values()) {
            if (best == null || p.score() > best.score()) {
                best = p;
            }
        }
        return best;
    }

    /**
     * Top-N patterns for a context, sorted by score descending.
     */
    public java.util.List<Pattern> topPatterns(String context, int n) {
        Map<String, Pattern> ctxPatterns = memory.get(context);
        if (ctxPatterns == null) {
            return java.util.Collections.emptyList();
        }
        return ctxPatterns.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Apply time-based decay so stale patterns lose influence.
     * Called periodically (e.g. once per hour) to keep memory fresh.
     */
    public void decay() {
        for (Map<String, Pattern> ctxPatterns : memory.values()) {
            for (Map.Entry<String, Pattern> entry : ctxPatterns.entrySet()) {
                Pattern p = entry.getValue();
                double decayedReward = p.reward * decayFactor;
                entry.setValue(new Pattern(p.context, p.action, decayedReward, p.occurrences, p.lastSeen));
            }
        }
    }

    /** Total number of learned patterns across all contexts. */
    public int size() {
        return memory.values().stream().mapToInt(Map::size).sum();
    }

    /** All known contexts (for diagnostics and reporting). */
    public java.util.Set<String> knownContexts() {
        return memory.keySet();
    }
}
