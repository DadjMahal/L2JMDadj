package com.aiplayer.advanced;

import java.util.logging.Logger;

import com.aiplayer.neural.DeepLearningCore;

/**
 * Reinforcement Engine - Task 76
 *
 * Implements reward-based learning (Q-learning inspired) so the AI
 * optimizes its strategies over time. Every action gets a reward
 * signal, and the AI learns to prefer high-reward actions.
 *
 * Reward signals in Lineage 2 context:
 *  + XP gained from kills        -> positive combat reward
 *  + Adena profit from trades    -> positive trade reward
 *  + Quest completion            -> positive quest reward
 *  + Death / HP loss             -> negative combat reward
 *  + Failed quest / time wasted  -> negative quest reward
 *  + Trade loss                  -> negative trade reward
 */
public class ReinforcementEngine {
    private static final Logger LOGGER = Logger.getLogger(ReinforcementEngine.class.getName());

    private final DeepLearningCore deepLearning;
    private final AdaptiveLearner adaptiveLearner;

    // Reward scaling factors (tune how much each outcome matters)
    private static final double XP_REWARD_SCALE = 0.01;    // 100 XP = 1.0 reward
    private static final double ADENA_REWARD_SCALE = 0.001; // 1000 adena = 1.0 reward
    private static final double DEATH_PENALTY = -2.0;
    private static final double QUEST_COMPLETE_REWARD = 3.0;
    private static final double QUEST_FAIL_PENALTY = -1.0;

    public ReinforcementEngine(DeepLearningCore deepLearning, AdaptiveLearner adaptiveLearner) {
        this.deepLearning = deepLearning;
        this.adaptiveLearner = adaptiveLearner;
    }

    /** Reward for killing a monster. */
    public void rewardKill(String monsterType, String skillUsed, long xpGained) {
        double reward = xpGained * XP_REWARD_SCALE;
        adaptiveLearner.learnCombat(monsterType, skillUsed, reward);
        LOGGER.fine("[RL] Kill reward: " + monsterType + " +" + reward);
    }

    /** Penalty for dying. */
    public void penalizeDeath(String monsterType, String lastAction) {
        adaptiveLearner.learnCombat(monsterType, lastAction, DEATH_PENALTY);
        LOGGER.fine("[RL] Death penalty: " + monsterType + " " + DEATH_PENALTY);
    }

    /** Reward for a profitable trade. */
    public void rewardTrade(String town, String action, long adenaProfit) {
        double reward = adenaProfit * ADENA_REWARD_SCALE;
        adaptiveLearner.learnTrade(town, action, reward);
        LOGGER.fine("[RL] Trade reward: " + town + " +" + reward);
    }

    /** Reward for completing a quest. */
    public void rewardQuestComplete(String questId, String finalAction) {
        adaptiveLearner.learnQuest(questId, finalAction, QUEST_COMPLETE_REWARD);
        LOGGER.fine("[RL] Quest complete: " + questId + " +" + QUEST_COMPLETE_REWARD);
    }

    /** Penalty for failing/abandoning a quest. */
    public void penalizeQuestFail(String questId, String action) {
        adaptiveLearner.learnQuest(questId, action, QUEST_FAIL_PENALTY);
        LOGGER.fine("[RL] Quest fail: " + questId + " " + QUEST_FAIL_PENALTY);
    }

    /** Reward for finding a fast route. */
    public void rewardFastRoute(String fromZone, String toZone, String route, double timeSaved) {
        adaptiveLearner.learnMovement(fromZone, toZone, route, timeSaved);
    }

    /** Get the best known strategy for a situation. */
    public DeepLearningCore.Prediction getBestStrategy(String context, java.util.List<String> options) {
        return deepLearning.predict(context, options);
    }
}
