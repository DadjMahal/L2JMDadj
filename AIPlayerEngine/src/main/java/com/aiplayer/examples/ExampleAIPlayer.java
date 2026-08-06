package com.aiplayer.examples;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.engine.AIPlayerManager;
import com.aiplayer.engine.AIPlayerState;
import com.aiplayer.protocol.L2ProtocolHandler;
import com.aiplayer.protocol.ProtocolPacket;
import com.aiplayer.protocol.ProtocolFactory;

import java.util.logging.Logger;

/**
 * Example AI Player Implementation
 * Demonstrates how to connect and control an AI player
 * This is where the "magic" begins!
 */
public class ExampleAIPlayer {
    private static final Logger LOGGER = Logger.getLogger(ExampleAIPlayer.class.getName());

    public static void main(String[] args) {
        LOGGER.info("=================================");
        LOGGER.info("  AI PLAYER ENGINE - STARTING UP  ");
        LOGGER.info("=================================");

        // 1. Create AI player
        AIPlayer aiPlayer = new AIPlayer("MagicBot_01", 1, 1, 0); // Human Fighter
        LOGGER.info("Created AI Player: " + aiPlayer.getName());

        // 2. Connect to server
        L2ProtocolHandler protocol = new L2ProtocolHandler(
            aiPlayer,
            "127.0.0.1",    // Server IP
            2106,           // Login port
            7777            // Game port
        );

        boolean connected = protocol.connect();
        if (!connected) {
            LOGGER.warning("Failed to connect - check server is running!");
            return;
        }

        // 3. Set initial state
        aiPlayer.setAIState("CONNECTED");

        // 4. Main AI loop - THIS IS WHERE THE MAGIC HAPPENS!
        LOGGER.info("Starting AI player loop...");
        long startTime = System.currentTimeMillis();
        int tick = 0;

        while (connected && (System.currentTimeMillis() - startTime) < 30000) { // Run for 30 seconds
            tick++;

            // Run AI decision making
            aiPlayer.think();

            // Send packets
            protocol.processOutgoing();

            // Log every 100 ticks
            if (tick % 100 == 0) {
                LOGGER.info("AI Player active - Tick: " + tick +
                             " State: " + aiPlayer.getAIState() +
                             " Queue: " + aiPlayer.getActionQueue().size());

                // Demonstrate random actions
                demonstrateActions(aiPlayer, protocol);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 5. Disconnect
        protocol.disconnect();
        LOGGER.info("AI Player disconnected after " + tick + " ticks");
    }

    /**
     * Demonstrate AI player capabilities with fun actions!
     */
    private static void demonstrateActions(AIPlayer player, L2ProtocolHandler protocol) {

        // 🎮 ACTION 1: Move around!
        if (Math.random() > 0.7) {
            int newX = 16600 + (int)(Math.random() * 100);
            int newY = 17000 + (int)(Math.random() * 100);
            int newZ = 434;

            ProtocolPacket movePacket = ProtocolFactory.createMovementPacket(player, newX, newY, newZ);
            protocol.sendPacket(movePacket);
            player.setAIState("MOVING TO (" + newX + ", " + newY + ")");
        }

        // 💬 ACTION 2: Say hello!
        if (Math.random() > 0.9) {
            String[] messages = {
                "Hello there!",
                "Nice day for hunting!",
                "Watch your step!",
                "Good XP today!",
                "See you around!"
            };

            String msg = messages[(int)(Math.random() * messages.length)];
            ProtocolPacket chatPacket = ProtocolFactory.createChatPacket(player, msg);
            protocol.sendPacket(chatPacket);
        }

        // ⚔️ ACTION 3: Practice attacks (if we had a target)
        // In real implementation, we'd detect nearby enemies
        if (Math.random() > 0.95 && Math.random() > 0.5) {
            // Mock target ID for demonstration
            int targetId = 12345678;
            ProtocolPacket attackPacket = ProtocolFactory.createAttackPacket(player, targetId);
            protocol.sendPacket(attackPacket);
            player.setAIState("ATTACKING");
        }
    }
}