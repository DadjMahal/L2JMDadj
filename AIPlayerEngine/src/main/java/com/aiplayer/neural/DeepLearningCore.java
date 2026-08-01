package com.aiplayer.neural;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Deep Learning Core - Task 68
 *
 * Cross-cutting intelligence layer that makes AI players behave like REAL players.
 * It sits above the individual modules (Combat/Quest/Merchant/Social) and
 * provides predictive intelligence:
 *  - Predicts optimal leveling paths (where to farm for the player's level)
 *  - Smart movement behavior (avoids danger, picks efficient routes)
 *  - Smart teleport selection (when to gate vs. walk)
 *  - Loot selection intelligence (keep valuable, drop junk)
 *  - Combat tactic prediction (which skill worked best last time)
 *  - Quest efficiency (shortest path to completion)
 *
 * It learns from outcomes via PatternMemory and exposes simple prediction
 * helpers that the other AI modules call before making a decision.
 */
public class DeepLearningCore {
    private static final Logger LOGGER = Logger.getLogger(DeepLearningCore.class.getName());

    private final PatternMemory memory;
    private final double explorationRate; // epsilon: chance to try something new

    public DeepLearningCore() {
        this(new PatternMemory(), 0.15);
        LOGGER.info("[DeepLearningCore] Initialized (exploration=0.15)");
    }

    public DeepLearningCore(PatternMemory memory, double explorationRate) {
        this.memory = memory;
        this.explorationRate = explorationRate;
    }

    /**
     * Recommend the next best action in a context, using epsilon-greedy
     * exploration: mostly use the best known pattern, but occasionally
     * explore to discover better strategies (like a real player trying
     * new things).
     */
    public Prediction predict(String context, List<String> candidateActions) {
        if (candidateActions == null || candidateActions.isEmpty()) {
            return Prediction.unknown();
        }
        // Exploration: try a random action sometimes
        if (ThreadLocalRandom.current().nextDouble() < explorationRate) {
            String explore = candidateActions.get(
                    ThreadLocalRandom.current().nextInt(candidateActions.size()));
            return Prediction.explore(explore);
        }
        // Exploitation: use the best learned pattern for this context
        PatternMemory.Pattern best = memory.bestPattern(context);
        if (best != null && candidateActions.contains(best.action)) {
            return Prediction.exploit(best.action, best.score(), best.confidence());
        }
        // No learned pattern yet: pick the first candidate as a safe default
        return Prediction.unknown();
    }

    /** Record the outcome of an action so future predictions improve. */
    public void learn(String context, String action, double reward) {
        memory.record(context, action, reward);
    }

    /** Periodic maintenance (call ~once per hour of game time). */
    public void decayMemory() {
        memory.decay();
    }

    public PatternMemory getMemory() {
        return memory;
    }

    // ------------------------------------------------------------------
    //  SPECIALIZED PREDICTORS (convenience helpers for each module)
    // ------------------------------------------------------------------

    /** Smart leveling: predict the best hunting ground for the player's level. */
    public Prediction predictLevelingSpot(int playerLevel, List<String> knownSpots) {
        String context = "levelup:lv" + (playerLevel / 5); // bucket every 5 levels
        return predict(context, knownSpots);
    }

    /** Smart movement: predict the safest/efficient route between two zones. */
    public Prediction predictRoute(String fromZone, String toZone, List<String> routes) {
        String context = "route:" + fromZone + "->" + toZone;
        return predict(context, routes);
    }

    /** Smart teleport: decide whether to use a Gatekeeper teleport or walk. */
    public Prediction predictTeleportChoice(String zone, boolean hasAdena, List<String> options) {
        String context = "teleport:" + zone + ":adena=" + hasAdena;
        return predict(context, options);
    }

    /** Smart loot: predict whether to keep or discard an item. */
    public Prediction predictLootValue(String itemId, List<String> choices) {
        String context = "loot:" + itemId;
        return predict(context, choices);
    }

    /** Smart combat: predict which skill rotation to use against a monster type. */
    public Prediction predictCombatSkill(String monsterType, List<String> skills) {
        String context = "combat:" + monsterType;
        return predict(context, skills);
    }

    /** Smart quest: predict the most efficient quest to accept next. */
    public Prediction predictQuestChoice(int playerLevel, List<String> availableQuests) {
        String context = "quest:lv" + (playerLevel / 5);
        return predict(context, availableQuests);
    }

    /** Smart merchant: predict whether to buy, sell, or hold in a market. */
    public Prediction predictMerchantAction(String town, List<String> options) {
        String context = "merchant:" + town;
        return predict(context, options);
    }

    /** Smart PvP: predict the best tactic against an opponent class. */
    public Prediction predictPVPTactic(String opponentClass, List<String> tactics) {
        String context = "pvp:vs_" + opponentClass;
        return predict(context, tactics);
    }

    // ------------------------------------------------------------------
    //  DIAGNOSTICS
    // ------------------------------------------------------------------

    public int memorySize() {
        return memory.size();
    }

    public Set<String> knownContexts() {
        return memory.knownContexts();
    }

    // ------------------------------------------------------------------
    //  PREDICTION RESULT TYPE
    // ------------------------------------------------------------------

    /**
     * A prediction result: what the AI believes is the best action and how
     * confident it is. Confidence is 0.0 (no data) to 1.0 (very sure).
     */
    public static class Prediction {
        public enum Kind { EXPLOIT, EXPLORE, UNKNOWN }

        public final Kind kind;
        public final String recommendedAction;
        public final double expectedReward;
        public final double confidence;

        private Prediction(Kind kind, String recommendedAction,
                           double expectedReward, double confidence) {
            this.kind = kind;
            this.recommendedAction = recommendedAction;
            this.expectedReward = expectedReward;
            this.confidence = confidence;
        }

        public static Prediction exploit(String action, double reward, double confidence) {
            return new Prediction(Kind.EXPLOIT, action, reward, confidence);
        }

        public static Prediction explore(String action) {
            return new Prediction(Kind.EXPLORE, action, 0.0, 0.0);
        }

        public static Prediction unknown() {
            return new Prediction(Kind.UNKNOWN, null, 0.0, 0.0);
        }

        @Override
        public String toString() {
            return "Prediction{" + kind + ", action=" + recommendedAction
                    + ", reward=" + String.format("%.2f", expectedReward)
                    + ", confidence=" + String.format("%.2f", confidence) + "}";
        }
    }
}
