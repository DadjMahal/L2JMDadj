package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * AI Player Engine - Main Entry Point
 *
 * This engine creates and manages AI players that connect to the L2JMobius server
 * as legitimate player clients without modifying server code.
 *
 * Usage:
 * java -jar ai-player-engine.jar --config=config/ai-player.properties
 */
public class AIPlayerEngine {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerEngine.class.getName());

    private final AIPlayerManager playerManager;
    private volatile boolean running = false;

    public AIPlayerEngine() {
        this.playerManager = AIPlayerManager.getInstance();
    }

    /**
     * Initialize the AI Player Engine
     */
    public void initialize() {
        LOGGER.info("========================================");
        LOGGER.info("  L2JMobius AI Player Engine v1.0");
        LOGGER.info("========================================");

        // Load configuration
        AIConfiguration config = AIConfiguration.getInstance();
        LOGGER.info("Configuration loaded");
        LOGGER.info("Max AI Players: " + config.getMaxPlayers());

        // Initialize module loader
        AIModuleLoader moduleLoader = new AIModuleLoader();
        moduleLoader.loadModules();

        LOGGER.info("AI Player Engine initialized successfully");
    }

    /**
     * Start the AI Player Engine
     */
    public void start() {
        if (running) {
            LOGGER.warning("AI Player Engine already running!");
            return;
        }

        running = true;
        LOGGER.info("AI Player Engine started");

        // Start player manager
        playerManager.start();

        // LEGIT_TODO: Connect to server and spawn AI players (launcher stub — tracked in StreamGDisposition.md §4)
        // This will happen in next phase

        LOGGER.info("AI Player Engine running - ready to spawn players");
    }

    /**
     * Stop the AI Player Engine
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        LOGGER.info("Stopping AI Player Engine...");

        // Stop player manager
        playerManager.stop();

        LOGGER.info("AI Player Engine stopped");
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        try {
            AIPlayerEngine engine = new AIPlayerEngine();
            engine.initialize();

            // Parse command line arguments
            boolean spawnAll = false;
            for (String arg : args) {
                if ("--spawn-all".equals(arg) || "-s".equals(arg)) {
                    spawnAll = true;
                }
            }

            // Start the engine
            engine.start();

            // Spawn AI players if --spawn-all flag is provided
            if (spawnAll) {
                spawnDefaultAIPlayers();
            }

            // Keep running until interrupted
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                engine.stop();
            }));

            // Print usage info
            LOGGER.info("\n========================================");
            LOGGER.info("  AI Player Engine Ready!");
            LOGGER.info("========================================");
            LOGGER.info("Connected AI Players: " + AIPlayerManager.getInstance().getAIPlayerCount());
            LOGGER.info("\nAvailable spawn commands:");
            LOGGER.info("  AIPlayerManager.getInstance().spawnAIPlayer(name, accountId, classId, race, charId)");
            LOGGER.info("  AIPlayerManager.getInstance().spawnCombatPlayer()");
            LOGGER.info("  AIPlayerManager.getInstance().spawnQuestPlayer()");
            LOGGER.info("  AIPlayerManager.getInstance().spawnMerchantPlayer()");
            LOGGER.info("========================================\n");

        } catch (Exception e) {
            LOGGER.severe("Failed to start AI Player Engine: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Spawn default AI players for L2JM server
     */
    private static void spawnDefaultAIPlayers() {
        AIPlayerManager manager = AIPlayerManager.getInstance();

        // Combat AI Players (6)
        for (int i = 1; i <= 6; i++) {
            manager.spawnCombatPlayer();
        }

        // Quest AI Players (6)
        for (int i = 1; i <= 6; i++) {
            manager.spawnQuestPlayer();
        }

        // Merchant AI Players (6)
        for (int i = 1; i <= 6; i++) {
            manager.spawnMerchantPlayer();
        }

        // Social AI Players (6)
        for (int i = 1; i <= 6; i++) {
            manager.spawnSocialPlayer();
        }

        LOGGER.info("Spawned 24 AI Players (Combat:6, Quest:6, Merchant:6, Social:6)");
    }
}
