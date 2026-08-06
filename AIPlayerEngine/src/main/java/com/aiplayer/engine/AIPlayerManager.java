package com.aiplayer.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.monitor.AIMonitorDashboard;
import com.aiplayer.metrics.PerformanceMetrics;

/**
 * AI Player Manager
 * Manages all AI players in the system
 */
public class AIPlayerManager {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerManager.class.getName());

    private static final AIPlayerManager INSTANCE = new AIPlayerManager();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final java.util.Map<Integer, AIPlayer> aiPlayers = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean running = false;
    private final String serverHost = "localhost";
    private final int loginPort = 2106;
    private final int gamePort = 7777;

    // Character ID lookup tables (based on database setup)
    private static final int[] COMBAT_CHAR_IDS = {2, 3, 9, 10, 11, 12};
    private static final int[] QUEST_CHAR_IDS = {6, 7, 13, 14, 15, 16};
    private static final int[] MERCHANT_CHAR_IDS = {5, 17, 18, 19, 20, 21};
    private static final int[] SOCIAL_CHAR_IDS = {8, 22, 23, 24, 25, 26};

    // Type-specific counters for database account mapping
    private int combatCount = 0;
    private int questCount = 0;
    private int merchantCount = 0;
    private int socialCount = 0;

    private AIPlayerManager() {
        // Private constructor for singleton
    }

    public static AIPlayerManager getInstance() {
        return INSTANCE;
    }

    /**
     * Spawn a new AI Player with REAL connection to L2JM server
     */
    public AIPlayer spawnAIPlayer(String playerName, int accountId, int classId, int race, int charId) {
        AIPlayer aiPlayer = new AIPlayer(playerName, accountId, classId, race);
        aiPlayers.put(accountId, aiPlayer);
        LOGGER.info("Spawned AI Player: " + playerName);

        // ACTUAL CONNECTION TO L2JM SERVER
        new Thread(() -> {
            try {
                // Connect to database account - account name should match the account parameter
                String account = playerName.toLowerCase(); // e.g., "ai_combat_01"
                String password = "ai123pass";

                // Login to L2JM server
                if (aiPlayer.connectToServer(account, password, charId)) {
                    LOGGER.info("[REAL CONNECTION] " + playerName + " connected to L2JM server!");
                } else {
                    LOGGER.warning(playerName + " failed to connect - account may not exist");
                }
            } catch (Exception e) {
                LOGGER.severe("Connection failed for " + playerName + ": " + e.getMessage());
            }
        }).start();

        return aiPlayer;
    }

    /**
     * Despawn an AI Player — Stream F (task 97): graceful shutdown. Disconnects the bot from the
     * server and persists its session state before removing it, so a despawn isn't a data loss.
     */
    public void despawnAIPlayer(int accountId) {
        AIPlayer removed = aiPlayers.remove(accountId);
        if (removed != null) {
            try {
                removed.saveSessionState(); // Stream E 89: persist level/pos/goals
                removed.disconnect();       // Stream E 89: clean socket close + record drop time
            } catch (Exception e) {
                LOGGER.warning("Error during graceful despawn of " + removed.getName() + ": " + e.getMessage());
            }
            LOGGER.info("Despawned AI Player: " + removed.getName());
        }
    }

    /**
     * Stream F (task 97): gracefully shut down ALL managed AI players — disconnect + persist each,
     * then stop the think scheduler. Safe to call once at process exit.
     */
    public void shutdownAll() {
        LOGGER.info("Graceful shutdown of " + aiPlayers.size() + " AI players...");
        for (AIPlayer player : aiPlayers.values()) {
            try {
                player.saveSessionState();
                player.disconnect();
            } catch (Exception e) {
                LOGGER.warning("Error shutting down " + player.getName() + ": " + e.getMessage());
            }
        }
        aiPlayers.clear();
        stop();
        LOGGER.info("All AI players shut down.");
    }

    /**
     * Start all AI players
     */
    public void start() {
        if (running) {
            LOGGER.warning("AI Player Manager already running!");
            return;
        }

        running = true;
        LOGGER.info("Starting AI Player Manager...");

        // Schedule AI think cycles
        scheduler.scheduleAtFixedRate(this::thinkAllPlayers,
            0, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop all AI players
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        LOGGER.info("AI Player Manager stopped");
    }

    private void thinkAllPlayers() {
        if (!running) return;

        for (AIPlayer player : aiPlayers.values()) {
            try {
                // Stream F (task 98): measure + record decision latency via PerformanceMetrics
                // (was previously dead code with no callers).
                long startNanos = System.nanoTime();
                player.think();
                long latencyNanos = System.nanoTime() - startNanos;
                PerformanceMetrics.getInstance().recordAction(player.getName(), latencyNanos);
                // Stream F (task 98): feed the (previously dead) monitor dashboard so live stats exist.
                AIMonitorDashboard.getInstance().updatePlayerStats(player);
            } catch (Exception e) {
                LOGGER.severe("Error thinking for AI Player " + player.getName() +
                    ": " + e.getMessage());
            }
        }
    }

    public AIPlayer getAIPlayer(int accountId) {
        return aiPlayers.get(accountId);
    }

    public int getAIPlayerCount() {
        return aiPlayers.size();
    }

    /** Stream F (task 97): all currently-managed players (for integration tests / monitoring). */
    public java.util.Collection<AIPlayer> getManagedPlayers() {
        return aiPlayers.values();
    }

    // Specialized spawn methods for different AI player types

    /**
     * Spawn a Combat AI Player
     */
    public AIPlayer spawnCombatPlayer() {
        combatCount++;
        String name = "CombatBot_" + combatCount;
        String account = "ai_combat_" + String.format("%02d", combatCount);
        int classId = combatCount % 3 == 0 ? 1 : combatCount % 3 == 1 ? 2 : 3;
        int race = combatCount % 4;
        int charId = COMBAT_CHAR_IDS[Math.min(combatCount - 1, COMBAT_CHAR_IDS.length - 1)];

        AIPlayer player = new AIPlayer(name, 100 + combatCount, classId, race);
        aiPlayers.put(100 + combatCount, player);

        connectPlayer(player, account, charId);
        LOGGER.info("[COMBAT AI] Spawned Combat AI Player: " + name + " (account=" + account + ", charId=" + charId + ")");
        return player;
    }

    /**
     * Spawn a Quest AI Player
     */
    public AIPlayer spawnQuestPlayer() {
        questCount++;
        String name = "QuestBot_" + questCount;
        String account = "ai_quest_" + String.format("%02d", questCount);
        int classId = 1; // Hero class for quest completion
        int race = 0;
        int charId = QUEST_CHAR_IDS[Math.min(questCount - 1, QUEST_CHAR_IDS.length - 1)];

        AIPlayer player = new AIPlayer(name, 300 + questCount, classId, race);
        aiPlayers.put(300 + questCount, player);

        connectPlayer(player, account, charId);
        LOGGER.info("[QUEST AI] Spawned Quest AI Player: " + name + " (account=" + account + ", charId=" + charId + ")");
        return player;
    }

    /**
     * Spawn a Merchant AI Player
     */
    public AIPlayer spawnMerchantPlayer() {
        merchantCount++;
        String name = "MerchantBot_" + merchantCount;
        String account = "ai_merchant_" + String.format("%02d", merchantCount);
        int classId = 1;
        int race = 0;
        int charId = MERCHANT_CHAR_IDS[Math.min(merchantCount - 1, MERCHANT_CHAR_IDS.length - 1)];

        AIPlayer player = new AIPlayer(name, 400 + merchantCount, classId, race);
        aiPlayers.put(400 + merchantCount, player);

        connectPlayer(player, account, charId);
        LOGGER.info("[MERCHANT AI] Spawned Merchant AI Player: " + name + " (account=" + account + ", charId=" + charId + ")");
        return player;
    }

    /**
     * Spawn a Social AI Player
     */
    public AIPlayer spawnSocialPlayer() {
        socialCount++;
        String name = "SocialBot_" + socialCount;
        String account = "ai_social_" + String.format("%02d", socialCount);
        int classId = 1;
        int race = 0;
        int charId = SOCIAL_CHAR_IDS[Math.min(socialCount - 1, SOCIAL_CHAR_IDS.length - 1)];

        AIPlayer player = new AIPlayer(name, 500 + socialCount, classId, race);
        aiPlayers.put(500 + socialCount, player);

        connectPlayer(player, account, charId);
        LOGGER.info("[SOCIAL AI] Spawned Social AI Player: " + name + " (account=" + account + ", charId=" + charId + ")");
        return player;
    }

    /**
     * Connect a player to the L2JM server
     */
    private void connectPlayer(AIPlayer player, String name, int accountId) {
        new Thread(() -> {
            try {
                String account = name.toLowerCase(); // name already full account (e.g. ai_combat_01); fix double-prefix bug (B1)
                String password = "ai123pass";
                int charId = accountId; // Character ID matches account

                if (player.connectToServer(account, password, charId)) {
                    LOGGER.info("[L2JM CONNECTION] " + name + " successfully connected to L2JM server at localhost:7777!");
                } else {
                    LOGGER.warning("[" + name + "] Connection failed - check server and account exists");
                }
            } catch (Exception e) {
                LOGGER.severe("Connection failed for " + name + ": " + e.getMessage());
            }
        }).start();
    }
}