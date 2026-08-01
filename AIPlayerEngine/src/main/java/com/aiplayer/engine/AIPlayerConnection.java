package com.aiplayer.engine;

import java.io.IOException;
import java.util.logging.Logger;

import com.aiplayer.protocol.L2JProtocol;

/**
 * AI Player Connection Manager
 * Handles connection between AIPlayer and L2J server
 * 
 * Task 67 Extension - Protocol Integration
 */
public class AIPlayerConnection {
    private static final Logger LOGGER = Logger.getLogger(AIPlayerConnection.class.getName());
    
    private final AIPlayer aiPlayer;
    private final L2JProtocol protocol;
    
    public AIPlayerConnection(AIPlayer aiPlayer, String serverHost, int loginPort, int gamePort) {
        this.aiPlayer = aiPlayer;
        this.protocol = new L2JProtocol(aiPlayer, serverHost, loginPort, gamePort);
    }
    
    /**
     * Connect this AI player to the L2JM server
     */
    public boolean connect(String accountName, String password, int characterId) {
        LOGGER.info("[" + aiPlayer.getName() + "] Attempting connection...");
        
        boolean result = protocol.connectAndLogin(accountName, password, characterId);
        
        if (result) {
            aiPlayer.setConnected(true);
            aiPlayer.setLoggedIn(true);
            aiPlayer.setLoginTime(System.currentTimeMillis());
            LOGGER.info("[" + aiPlayer.getName() + "] Successfully connected and logged in");
        } else {
            LOGGER.warning("[" + aiPlayer.getName() + "] Connection/login failed");
        }
        
        return result;
    }
    
    /**
     * Send movement command
     */
    public void sendMove(int x, int y, int z) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot move - not logged in");
            return;
        }
        try {
            protocol.sendMove(x, y, z);
        } catch (IOException e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Move error: " + e.getMessage());
        }
    }
    
    /**
     * Send attack command
     */
    public void sendAttack(int targetId) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot attack - not logged in");
            return;
        }
        try {
            protocol.sendAttack(targetId);
        } catch (IOException e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Attack error: " + e.getMessage());
        }
    }
    
    /**
     * Send chat message
     */
    public void sendChat(String message) {
        if (!aiPlayer.isLoggedIn()) {
            LOGGER.warning("[" + aiPlayer.getName() + "] Cannot chat - not logged in");
            return;
        }
        try {
            protocol.sendChat(message);
        } catch (IOException e) {
            LOGGER.severe("[" + aiPlayer.getName() + "] Chat error: " + e.getMessage());
        }
    }
    
    /**
     * Disconnect the AI player
     */
    public void disconnect() {
        protocol.disconnect();
        aiPlayer.setConnected(false);
        aiPlayer.setLoggedIn(false);
        LOGGER.info("[" + aiPlayer.getName() + "] Disconnected");
    }
    
    public boolean isConnected() {
        return aiPlayer.isConnected();
    }
    
    public boolean isLoggedIn() {
        return aiPlayer.isLoggedIn();
    }
    
    public AIPlayer getPlayer() {
        return aiPlayer;
    }
}