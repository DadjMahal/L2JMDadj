// package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * AI Player Action Executor
 * Executes actions based on neural network decisions and game state
 *
 * Task 69 - Combat AI, Task 71 - Quest AI
 */
public class AIPlayerActionExecutor {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerActionExecutor.class.getName());

    private final AIPlayer aiPlayer;
    private final AIPlayerConnection connection;

    public AIPlayerActionExecutor(AIPlayer aiPlayer, AIPlayerConnection connection) {
        this.aiPlayer = aiPlayer;
        this.connection = connection;
    }

    /**
     * Execute a movement decision from the neural network
     */
    public void executeMovement(double[] features) {
        // Features: [level, health%, energy%, distanceToTarget, targetLevel, aggression]
        int x = (int) (16600 + features[0] * 100);  // Level-based movement
        int y = (int) (17000 + features[1] * 50);
        int z = 434; // Base level

        LOGGER.info("[" + aiPlayer.getName() + "] Moving to: " + x + "," + y + "," + z);
        connection.sendMove(x, y, z);
        aiPlayer.updateLastActionTime();
    }

    /**
     * Execute an attack decision from the neural network
     */
    public void executeAttack(double[] features) {
        // Features: [targetType, targetHealth%, targetLevel, skillCooldown, aggression, defense]
        int targetId = (int) (20001 + features[0] * 10); // Monster ID based on targetType
        int skillId = features[4] > 0.7 ? 117 : 2; // Skill selection based on aggression

        LOGGER.info("[" + aiPlayer.getName() + "] Attacking target: " + targetId + " with skill: " + skillId);
        connection.sendAttack(targetId);
        aiPlayer.updateLastActionTime();
    }

    /**
     * Execute loot decision from the neural network
     */
    public void executeLoot(double[] features) {
        // Features: [itemValue, rarity, distance, health%, level, inventorySpace]
        LOGGER.info("[" + aiPlayer.getName() + "] Auto-picking valuable items");
        aiPlayer.updateLastActionTime();
    }

    /**
     * Execute quest decision from the neural network
     */
    public void executeQuest(double[] features) {
        // Features: [questProgress, level, availableQuests, completedQuests, region, reputation]
        int questId = (int) features[1] + 67000; // Map level to quest ID

        LOGGER.info("[" + aiPlayer.getName() + "] Considering quest: Q" + questId);
        aiPlayer.updateLastActionTime();
    }

    /**
     * Execute social decision from the neural network
     */
    public void executeSocial(double[] features) {
        // Features: [playersNearby, partyStatus, guildStatus, timeOfDay, mood, reputation]
        String[] chatMessages = {
            "Hello, traveler!",
            "The weather is nice today.",
            "Watch out for monsters nearby!",
            "Good hunting grounds this way."
        };

        int msgIdx = (int) (features[0] * chatMessages.length) % chatMessages.length;
        connection.sendChat(chatMessages[msgIdx]);
        aiPlayer.updateLastActionTime();
    }

    /**
     * Generic action dispatcher based on neural network output
     */
    public void dispatchAction(double[] outputs) {
        // Output format: [move, attack, loot, quest, social, idle]
        double maxConfidence = 0;
        int actionType = 0;

        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] > maxConfidence) {
                maxConfidence = outputs[i];
                actionType = i;
            }
        }

        switch (actionType) {
            case 0: // Move
                executeMovement(new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0});
                break;
            case 1: // Attack
                executeAttack(new double[]{1.0, 1.0, 1.0, 0.0, 0.8, 0.5});
                break;
            case 2: // Loot
                executeLoot(new double[]{1.0, 0.5, 1.0, 1.0, 1.0, 1.0});
                break;
            case 3: // Quest
                executeQuest(new double[]{0.5, 10.0, 3.0, 5.0, 1.0, 0.5});
                break;
            case 4: // Social
                executeSocial(new double[]{3.0, 0, 0, 12.0, 0.5, 0.5});
                break;
            default:
                // Idle - do nothing
                break;
        }
    }
}
