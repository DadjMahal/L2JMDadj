package com.aiplayer.engine;

import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified AI Player - Minimal Working Version
 * This is a stripped-down version that WILL compile and run
 */
public class AIPlayerSimple {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerSimple.class.getName());

    private final String name;
    private final int accountId;
    private AtomicBoolean running = new AtomicBoolean(false);

    public AIPlayerSimple(String name, int accountId) {
        this.name = name;
        this.accountId = accountId;
    }

    public void start() {
        running.set(true);
        LOGGER.info("[AI] " + name + " starting... (ID: " + accountId + ")");

        new Thread(() -> {
            int counter = 0;
            while (running.get()) {
                try {
                    counter++;
                    // Simple AI decision making
                    makeDecision(counter);
                    Thread.sleep(2000); // Think every 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, name + "-AI-Thread").start();
    }

    public void stop() {
        running.set(false);
        LOGGER.info("[AI] " + name + " stopping...");
    }

    private void makeDecision(int cycle) {
        // Merchant Behavior
        if (cycle % 5 == 0) {
            LOGGER.info("[MERCHANT] " + name + " checking market prices...");
            performMerchantAction();
        }

        // Quest Behavior
        if (cycle % 10 == 0) {
            LOGGER.info("[QUEST] " + name + " working on quest objectives...");
            performQuestAction();
        }

        // Combat Behavior — Stream G (task 110): deterministic (no Math.random) for decision determinism.
        if (cycle % 3 == 0 && cycle % 2 == 1) {
            LOGGER.info("[COMBAT] " + name + " scanning for enemies...");
            performCombatAction();
        }

        // Social Behavior
        if (cycle % 20 == 0) {
            LOGGER.info("[SOCIAL] " + name + " saying hello!");
            performSocialAction();
        }

        // Idle behavior
        if (cycle % 7 == 0) {
            LOGGER.info("[IDLE] " + name + " taking a break...");
        }
    }

    private void performMerchantAction() {
        LOGGER.info("[MERCHANT] " + name + " would: scan merchants, compare prices, buy low/sell high");
        // This logs what the AI would do
    }

    private void performQuestAction() {
        LOGGER.info("[QUEST] " + name + " would: check quest status, move to objective, complete task");
    }

    private void performCombatAction() {
        LOGGER.info("[COMBAT] " + name + " would: detect enemies, engage, use skills, heal if needed");
    }

    private void performSocialAction() {
        String[] messages = {
            "Hello adventurers!",
            "Enjoying the journey!",
            "What a beautiful day!",
            "May the blessings be upon you!"
        };
        LOGGER.info("[CHAT] " + name + " says: \"" + messages[(int)(Math.random() * messages.length)] + "\"");
    }

    public String getName() { return name; }
    public int getAccountId() { return accountId; }
    public boolean isRunning() { return running.get(); }
}
