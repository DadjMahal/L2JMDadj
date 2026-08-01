package com.aiplayer.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.aiplayer.protocol.L2JProtocol;

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
                // Connect to database account
                String account = "ai_" + playerName.toLowerCase();
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
     * Despawn an AI Player
     */
    public void despawnAIPlayer(int accountId) {
        AIPlayer removed = aiPlayers.remove(accountId);
        if (removed != null) {
            LOGGER.info("Despawned AI Player: " + removed.getName());
        }
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
                player.think();
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
    
    // Specialized spawn methods for different AI player types
    
    /**
     * Spawn a Combat AI Player
     */
    public AIPlayer spawnCombatPlayer() {
        int accountId = 200 + aiPlayers.size();
        String name = "CombatBot_" + accountId;
        int classId = accountId % 3 == 0 ? 1 : accountId % 3 == 1 ? 2 : 3; // Fighter, Warrior, etc.
        int race = accountId % 4;
        
        AIPlayer player = new AIPlayer(name, accountId, classId, race);
        aiPlayers.put(accountId, player);
        
        connectPlayer(player, name, accountId);
        LOGGER.info("[COMBAT AI] Spawned Combat AI Player: " + name);
        return player;
    }
    
    /**
     * Spawn a Quest AI Player
     */
    public AIPlayer spawnQuestPlayer() {
        int accountId = 300 + aiPlayers.size();
        String name = "QuestBot_" + accountId;
        int classId = 1; // Hero class for quest completion
        int race = 0;
        
        AIPlayer player = new AIPlayer(name, accountId, classId, race);
        aiPlayers.put(accountId, player);
        
        connectPlayer(player, name, accountId);
        LOGGER.info("[QUEST AI] Spawned Quest AI Player: " + name);
        return player;
    }
    
    /**
     * Spawn a Merchant AI Player
     */
    public AIPlayer spawnMerchantPlayer() {
        int accountId = 400 + aiPlayers.size();
        String name = "MerchantBot_" + accountId;
        int classId = 1;
        int race = 0;
        
        AIPlayer player = new AIPlayer(name, accountId, classId, race);
        aiPlayers.put(accountId, player);
        
        connectPlayer(player, name, accountId);
        LOGGER.info("[MERCHANT AI] Spawned Merchant AI Player: " + name);
        return player;
    }
    
    /**
     * Spawn a Social AI Player
     */
    public AIPlayer spawnSocialPlayer() {
        int accountId = 500 + aiPlayers.size();
        String name = "SocialBot_" + accountId;
        int classId = 1;
        int race = 0;
        
        AIPlayer player = new AIPlayer(name, accountId, classId, race);
        aiPlayers.put(accountId, player);
        
        connectPlayer(player, name, accountId);
        LOGGER.info("[SOCIAL AI] Spawned Social AI Player: " + name);
        return player;
    }
    
    /**
     * Connect a player to the L2JM server
     */
    private void connectPlayer(AIPlayer player, String name, int accountId) {
        new Thread(() -> {
            try {
                String account = "ai_" + name.toLowerCase();
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