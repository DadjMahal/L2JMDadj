package com.aiplayer.advanced;

import java.util.logging.Logger;

import com.aiplayer.neural.DeepLearningCore;

/**
 * Adaptive Learner - Task 75
 *
 * Wraps the DeepLearningCore with game-specific learning logic.
 * The AI player gets SMARTER over time by learning which actions
 * produce the best outcomes.
 *
 * This is what makes our AI players feel like they're "getting better"
 * the more they play - just like a real human player who learns
 * from experience.
 */
public class AdaptiveLearner {
    private static final Logger LOGGER = Logger.getLogger(AdaptiveLearner.class.getName());

    private final DeepLearningCore deepLearning;
    private final String playerName;

    // Learning statistics
    private int totalActionsLearned = 0;
    private int combatActionsLearned = 0;
    private int questActionsLearned = 0;
    private int tradeActionsLearned = 0;
    private int movementActionsLearned = 0;

    public AdaptiveLearner(String playerName, DeepLearningCore deepLearning) {
        this.playerName = playerName;
        this.deepLearning = deepLearning;
    }

    /**
     * Learn from a combat outcome.
     * @param monsterType the type of monster fought
     * @param skillUsed the skill that was used
     * @param reward positive for win/kill, negative for death/retreat
     */
    public void learnCombat(String monsterType, String skillUsed, double reward) {
        String context = "combat:" + monsterType;
        deepLearning.learn(context, skillUsed, reward);
        combatActionsLearned++;
        totalActionsLearned++;
        LOGGER.fine("[Adaptive] " + playerName + " learned combat: " + monsterType
                + " + " + skillUsed + " = " + reward);
    }

    /**
     * Learn from a quest outcome.
     * @param questId the quest that was worked on
     * @param action the action taken (accept, collect, turn_in, abandon)
     * @param reward positive for completion, negative for failure
     */
    public void learnQuest(String questId, String action, double reward) {
        String context = "quest:" + questId;
        deepLearning.learn(context, action, reward);
        questActionsLearned++;
        totalActionsLearned++;
        LOGGER.fine("[Adaptive] " + playerName + " learned quest: " + questId
                + " + " + action + " = " + reward);
    }

    /**
     * Learn from a trade outcome.
     * @param town the town where the trade happened
     * @param action buy/sell/hold
     * @param profit adena profit (positive or negative)
     */
    public void learnTrade(String town, String action, double profit) {
        String context = "merchant:" + town;
        deepLearning.learn(context, action, profit);
        tradeActionsLearned++;
        totalActionsLearned++;
        LOGGER.fine("[Adaptive] " + playerName + " learned trade: " + town
                + " + " + action + " = " + profit + " adena");
    }

    /**
     * Learn from a movement/route outcome.
     * @param fromZone starting zone
     * @param toZone destination zone
     * @param route the route taken
     * @param timeSaved positive if route was faster than expected
     */
    public void learnMovement(String fromZone, String toZone, String route, double timeSaved) {
        String context = "route:" + fromZone + "->" + toZone;
        deepLearning.learn(context, route, timeSaved);
        movementActionsLearned++;
        totalActionsLearned++;
    }

    public int getTotalActionsLearned() { return totalActionsLearned; }
    public int getCombatActionsLearned() { return combatActionsLearned; }
    public int getQuestActionsLearned() { return questActionsLearned; }
    public int getTradeActionsLearned() { return tradeActionsLearned; }
    public int getMovementActionsLearned() { return movementActionsLearned; }

    /** Get a summary of what this AI has learned. */
    public String getLearningSummary() {
        return playerName + " has learned " + totalActionsLearned + " actions ("
                + combatActionsLearned + " combat, " + questActionsLearned + " quest, "
                + tradeActionsLearned + " trade, " + movementActionsLearned + " movement)";
    }
}
