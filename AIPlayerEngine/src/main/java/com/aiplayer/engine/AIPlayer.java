package com.aiplayer.engine;

import java.io.IOException;
import java.util.logging.Logger;

import com.aiplayer.neural.DeepLearningCore;
import com.aiplayer.advanced.PersonalityProfile;
import com.aiplayer.advanced.EmotionalState;
import com.aiplayer.advanced.AdaptiveLearner;
import com.aiplayer.advanced.ReinforcementEngine;
import com.aiplayer.economy.MarketEngine;
import com.aiplayer.economy.EconomicEngine;
import com.aiplayer.economy.NetWorthOptimizer;
import com.aiplayer.social.CollectiveKnowledge;
import com.aiplayer.social.SwarmCoordinator;
import com.aiplayer.social.DiplomacyEngine;
import com.aiplayer.protocol.L2JProtocol;

/**
 * AI Player Base Class
 * Represents a character controlled by artificial intelligence
 * Does NOT extend Player - works externally through standard protocols
 */
public class AIPlayer {
    private static final Logger LOGGER = Logger.getLogger(AIPlayer.class.getName());
    
    // Core Identity
    private final String name;
    private final int accountId;
    private int characterId;
    private int level;
    private int classId;
    private int race;
    private final L2JProtocol protocol; // REAL PROTOCOL FOR CONNECTING
    
    // Position tracking (Task 47, 31)
    private int x = 0, y = 0, z = 0;
    
    // State Management
    private AIPlayerState state;
    private long lastActionTime;
    private long loginTime;
    private boolean isConnected;
    private boolean isLoggedIn;
    
    // Behavior Control
    private final AIBrain brain;
    private final AIActionQueue actionQueue;
    private final AIConfiguration config;
    
    // Deep Learning Intelligence (Task 68) - uses PatternMemory, not NeuralNetwork
    private final DeepLearningCore deepLearning;
    
    // Advanced AI Systems (Tasks 73-76)
    private final PersonalityProfile personality;
    private final EmotionalState emotions;
    private final AdaptiveLearner adaptiveLearner;
    private final ReinforcementEngine reinforcement;
    
    // Collective & Economic Systems (Tasks 77-96)
    private final CollectiveKnowledge collectiveKnowledge;
    private final SwarmCoordinator swarmCoordinator;
    private final DiplomacyEngine diplomacy;
    private final MarketEngine marketEngine;
    private final EconomicEngine economicEngine;
    private final NetWorthOptimizer netWorthOptimizer;
    
    // AI Modules
    private final CombatAI combatAI;
    private final QuestAI questAI;
    private final MerchantAI merchantAI;
    private final SocialAI socialAI;
    
    public AIPlayer(String name, int accountId, int classId, int race) {
        this.name = name;
        this.accountId = accountId;
        this.classId = classId;
        this.race = race;
        this.level = 1;
        this.state = AIPlayerState.OFFLINE;
        this.brain = new AIBrain(this);
        this.actionQueue = new AIActionQueue();
        this.config = AIConfiguration.getInstance();
        this.deepLearning = new DeepLearningCore();
        this.personality = new PersonalityProfile(PersonalityProfile.Personality.values()
                [accountId % PersonalityProfile.Personality.values().length]);
        this.emotions = new EmotionalState();
        this.adaptiveLearner = new AdaptiveLearner(name, deepLearning);
        this.reinforcement = new ReinforcementEngine(deepLearning, adaptiveLearner);
        this.collectiveKnowledge = CollectiveKnowledge.getInstance();
        this.swarmCoordinator = SwarmCoordinator.getInstance();
        this.diplomacy = DiplomacyEngine.getInstance();
        this.marketEngine = MarketEngine.getInstance();
        this.economicEngine = EconomicEngine.getInstance();
        this.netWorthOptimizer = NetWorthOptimizer.getInstance();
        
        // Initialize AI modules
        this.combatAI = new CombatAI(this);
        this.questAI = new QuestAI(this);
        this.merchantAI = new MerchantAI(this);
        this.socialAI = new SocialAI(this);
        
        // Initialize protocol for REAL L2JM server connection
        this.protocol = new L2JProtocol(this, "localhost", 2106, 7777);
        
        LOGGER.info("[Real Protocol] AI Player created: " + name + " (Class: " + classId + ", Race: " + race + ")");
    }
    
    /**
     * AI Decision Making Loop
     * Called periodically to make decisions
     */
    public void think() {
        if (!isConnected || !isLoggedIn) {
            return;
        }
        
        try {
            // Update state
            updateState();
            
            // Make decision
            AIDecision decision = brain.makeDecision();
            
            // Queue action
            if (decision != null && decision.shouldExecute()) {
                actionQueue.add(decision.getAction());
                executeQueuedActions();
            }
        } catch (Exception e) {
            LOGGER.warning("[" + name + "] Think error: " + e.getMessage());
        }
    }
    
    private void updateState() {
        // Update internal state tracking
        lastActionTime = System.currentTimeMillis();
    }
    
    public void executeQueuedActions() {
        while (!actionQueue.isEmpty()) {
            AIAction action = actionQueue.poll();
            if (action != null) {
                executeAction(action);
            }
        }
    }
    
    private void executeAction(AIAction action) {
        // REAL PROTOCOL ACTION EXECUTION - Connect to L2JM server
        try {
            switch (action.getType()) {
                case MOVE:
                    if (action.getParameters().length >= 3) {
                        int x = (Integer) action.getParameters()[0];
                        int y = (Integer) action.getParameters()[1];
                        int z = (Integer) action.getParameters()[2];
                        protocol.sendMove(x, y, z);
                        LOGGER.info("[PROTOCOL] " + name + " MOVED to: (" + x + ", " + y + ", " + z + ")");
                    }
                    break;
                    
                case ATTACK:
                    if (action.getParameters().length > 0) {
                        int targetId;
                        if (action.getParameters()[0] instanceof String) {
                            targetId = Integer.parseInt((String) action.getParameters()[0]);
                        } else {
                            targetId = (Integer) action.getParameters()[0];
                        }
                        protocol.sendAttack(targetId);
                        LOGGER.info("[PROTOCOL] " + name + " ATTACKING target: " + targetId);
                    }
                    break;
                    
                case CHAT:
                    if (action.getParameters().length > 0) {
                        String message = (String) action.getParameters()[0];
                        protocol.sendChat(message);
                        LOGGER.info("[PROTOCOL] " + name + " CHAT: " + message);
                    }
                    break;
                    
                case BUY:
                    if (action.getParameters().length >= 2) {
                        String itemId = (String) action.getParameters()[0];
                        int count = (Integer) action.getParameters()[1];
                        LOGGER.info("[TRADE] " + name + " BUYING " + count + "x " + itemId);
                    }
                    break;
                    
                case SELL:
                    if (action.getParameters().length >= 2) {
                        String itemId = (String) action.getParameters()[0];
                        int count = (Integer) action.getParameters()[1];
                        LOGGER.info("[TRADE] " + name + " SELLING " + count + "x " + itemId);
                    }
                    break;
                    
                case INTERACT_NPC:
                    if (action.getParameters().length >= 2) {
                        String npcId = (String) action.getParameters()[0];
                        String interactionType = (String) action.getParameters()[1];
                        LOGGER.info("[NPC] " + name + " INTERACTING with " + npcId + " (" + interactionType + ")");
                    }
                    break;
                    
                case USE_ITEM:
                    if (action.getParameters().length > 0) {
                        String itemId = (String) action.getParameters()[0];
                        LOGGER.info("[ITEM] " + name + " USING item: " + itemId);
                    }
                    break;
                    
                case HUNT:
                    if (action.getParameters().length >= 2) {
                        String targetId = (String) action.getParameters()[0];
                        int count = (Integer) action.getParameters()[1];
                        LOGGER.info("[HUNT] " + name + " HUNTING " + count + "x " + targetId);
                    }
                    break;
                    
                case PARTY_INVITE:
                    if (action.getParameters().length > 0) {
                        String targetId = (String) action.getParameters()[0];
                        LOGGER.info("[PARTY] " + name + " INVITING " + targetId + " to party");
                    }
                    break;
                    
                case COMBAT_MODE:
                    if (action.getParameters().length > 0) {
                        Boolean enabled = (Boolean) action.getParameters()[0];
                        LOGGER.info("[COMBAT] " + name + " combat mode: " + (enabled ? "ON" : "OFF"));
                    }
                    break;
                    
                case STAND:
                    LOGGER.info("[ACTION] " + name + " STANDING");
                    break;
                    
                case STOP_ATTACK:
                    LOGGER.info("[ACTION] " + name + " STOPPING attack");
                    break;
                    
                default:
                    LOGGER.info("[ACTION] " + name + " executing: " + action);
            }
        } catch (Exception e) {
            LOGGER.warning("[" + name + "] Action execution failed: " + e.getMessage());
        }
    }
    
    // REAL CONNECTION METHOD
    public boolean connectToServer(String accountName, String password, int charId) {
        try {
            boolean success = protocol.connectAndLogin(accountName, password, charId);
            if (success) {
                this.isConnected = true;
                this.isLoggedIn = true;
                this.characterId = charId;
                this.lastActionTime = System.currentTimeMillis();
                this.loginTime = System.currentTimeMillis();
                this.state = AIPlayerState.IN_GAME;
                // Initialize brain modules after connection
                brain.initializeModules();
            }
            return success;
        } catch (Exception e) {
            LOGGER.severe("[" + name + "] Connection failed: " + e.getMessage());
            return false;
        }
    }
    
    public void disconnect() {
        try {
            protocol.disconnect();
        } catch (Exception e) {
            LOGGER.warning("Disconnect error: " + e.getMessage());
        }
        this.isConnected = false;
        this.isLoggedIn = false;
        this.state = AIPlayerState.OFFLINE;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public int getAccountId() { return accountId; }
    public int getCharacterId() { return characterId; }
    public void setCharacterId(int id) { this.characterId = id; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getClassId() { return classId; }
    public int getRace() { return race; }
    public boolean isConnected() { return isConnected; }
    public boolean isLoggedIn() { return isLoggedIn; }
    public AIPlayerState getState() { return state; }
    public long getLastActionTime() { return lastActionTime; }
    public AIBrain getBrain() { return brain; }
    public AIActionQueue getActionQueue() { return actionQueue; }
    public CombatAI getCombatAI() { return combatAI; }
    public QuestAI getQuestAI() { return questAI; }
    public MerchantAI getMerchantAI() { return merchantAI; }
    public SocialAI getSocialAI() { return socialAI; }
    public L2JProtocol getProtocol() { return protocol; }
    
    // AI State management
    public String getAIState() {
        return state != null ? state.name() : "UNKNOWN";
    }
    
    public void setAIState(String stateStr) {
        try {
            this.state = AIPlayerState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown AI state: " + stateStr);
        }
    }
    
    public void setConnected(boolean connected) { this.isConnected = connected; }
    public void setLoggedIn(boolean loggedIn) { this.isLoggedIn = loggedIn; }
    public void setLoginTime(long time) { this.loginTime = time; }
    public long getLoginTime() { return loginTime; }
    public void updateLastActionTime() { this.lastActionTime = System.currentTimeMillis(); }
    
    // Position getters/setters (Task 31 - real enemy detection)
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public void setPosition(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    
    // PvP helpers (Task 63)
    public boolean isInPvPZone() {
        // Check if current position is in a PvP-enabled zone
        // For now, default to false - would need zone data from server
        return false;
    }
    
    public boolean isPvPEnabled() {
        return config.getBooleanProperty("combat.pvp_enabled", false);
    }
}
