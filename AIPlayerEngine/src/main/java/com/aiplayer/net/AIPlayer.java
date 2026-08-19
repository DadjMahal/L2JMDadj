package com.aiplayer.net;

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
import com.aiplayer.behavior.AIAction;
import com.aiplayer.behavior.AIActionQueue;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.AIDecision;
import com.aiplayer.behavior.AchievementAI;
import com.aiplayer.behavior.ActivityScheduler;
import com.aiplayer.behavior.BehaviorSeeder;
import com.aiplayer.behavior.EventCalendarAI;
import com.aiplayer.behavior.GoalTree;
import com.aiplayer.behavior.HeroTitleAI;
import com.aiplayer.behavior.HumanReactionSimulator;
import com.aiplayer.behavior.LongTermGoalsAI;
import com.aiplayer.behavior.MovementPatternAI;
import com.aiplayer.behavior.ResourceHoardingAI;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.behavior.quest.QuestAI;
import com.aiplayer.behavior.social.SocialAI;
import com.aiplayer.behavior.town.MerchantAI;
import com.aiplayer.core.AIConfiguration;
import com.aiplayer.core.AIPlayerState;
import com.aiplayer.core.PersistenceManager;

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

    // Stream E task 89: stored credentials so the bot can gracefully reconnect after a drop.
    private String lastAccount;
    private String lastPassword;
    private int reconnectAttempts;
    private long lastDisconnectMs;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

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
    private final LongTermGoalsAI longTermGoals; // Stream D: long-term goal selection (task 65)
    private final GoalTree goalTree; // Stream D: short-term goal selection + scheduling (tasks 65,68,69)
    private final ActivityScheduler activityScheduler; // Stream E task 88: periodic activity rotation

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

    // Stream G (G-Content + G-Behavior): wire the previously-dead content/behavior simulators in.
    // All had ZERO callers before G (the recurring instantiated-but-uncalled defect).
    private final AchievementAI achievementAI = new AchievementAI();
    private final EventCalendarAI eventCalendarAI = new EventCalendarAI();
    private final HeroTitleAI heroTitleAI = new HeroTitleAI();
    private final HumanReactionSimulator humanReaction = new HumanReactionSimulator();
    private final BehaviorSeeder behaviorSeeder = new BehaviorSeeder();
    private final MovementPatternAI movementPatternAI = new MovementPatternAI();
    private final ResourceHoardingAI resourceHoardingAI = new ResourceHoardingAI();

    public AIPlayer(String name, int accountId, int classId, int race) {
        this.name = name;
        this.accountId = accountId;
        this.classId = classId;
        this.race = race;
        this.persist = new PersistenceManager("aiplayer-" + name + ".state"); // Stream E 89
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
        this.longTermGoals = new LongTermGoalsAI();
        this.goalTree = new GoalTree(this);
        this.activityScheduler = new ActivityScheduler(this);
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
                this.lastAccount = accountName;   // Stream E 89: store for graceful reconnect
                this.lastPassword = password;
                this.reconnectAttempts = 0;
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

    /**
     * Stream E task 89: graceful reconnect. Re-uses the last successful credentials after a
     * disconnect, with a bounded retry + a minimum cooldown so we don't hammer the server.
     */
    public boolean reconnect() {
        if (isConnected && isLoggedIn) {
            return true; // already connected
        }
        if (lastAccount == null) {
            LOGGER.warning("[" + name + "] Can't reconnect: no stored credentials");
            return false;
        }
        long sinceDrop = System.currentTimeMillis() - lastDisconnectMs;
        if (sinceDrop < 3000L) {
            LOGGER.info("[" + name + "] reconnect cooldown in progress (" + sinceDrop + "ms)");
            return false;
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.severe("[" + name + "] Give up: exceeded " + MAX_RECONNECT_ATTEMPTS + " reconnect attempts");
            return false;
        }
        reconnectAttempts++;
        boolean ok = connectToServer(lastAccount, lastPassword, characterId);
        if (ok) {
            LOGGER.info("[" + name + "] RECONNECTED on attempt " + reconnectAttempts);
            // The executor/driver re-attaches the live packet logger after reconnect.
        }
        return ok;
    }

    /** Last disconnect timestamp (for reconnect cooldown). */
    public void markDisconnect() { this.lastDisconnectMs = System.currentTimeMillis(); }
    public int getReconnectAttempts() { return reconnectAttempts; }

    public void disconnect() {
        try {
            protocol.disconnect();
        } catch (Exception e) {
            LOGGER.warning("Disconnect error: " + e.getMessage());
        }
        this.isConnected = false;
        this.isLoggedIn = false;
        this.state = AIPlayerState.OFFLINE;
        markDisconnect(); // Stream E 89: record when, for reconnect cooldown
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

    // --- Stream G: expose the previously-dead G-Content / G-Behavior simulators (tasks 92,103) ---
    public AchievementAI getAchievementAI() { return achievementAI; }
    public EventCalendarAI getEventCalendarAI() { return eventCalendarAI; }
    public HeroTitleAI getHeroTitleAI() { return heroTitleAI; }
    public HumanReactionSimulator getHumanReaction() { return humanReaction; }
    public BehaviorSeeder getBehaviorSeeder() { return behaviorSeeder; }
    public MovementPatternAI getMovementPatternAI() { return movementPatternAI; }
    public ResourceHoardingAI getResourceHoardingAI() { return resourceHoardingAI; }

    /**
     * Stream G (G-Content): completing an achievement records it AND advances the
     * ACHIEVEMENT_RAID long-term goal (wiring AchievementAI into the goal system).
     */
    public void markAchievementCompleted(String id) {
        achievementAI.completeAchievement(id);
        longTermGoals.advanceGoal(LongTermGoalsAI.Goal.ACHIEVEMENT_RAID, 1);
    }

    // --- Stream D: expose the advanced intelligence subsystems (tasks 70-76) ---
    // These were always instantiated in the constructor but had NO getters, so the live combat
    // loop could never feed real outcomes into them. CombatAI/QuestAI hooks now use these to
    // drive EmotionalState + ReinforcementEngine + AdaptiveLearner from real game events.
    public PersonalityProfile getPersonality() { return personality; }
    public EmotionalState getEmotions() { return emotions; }
    public AdaptiveLearner getAdaptiveLearner() { return adaptiveLearner; }
    public ReinforcementEngine getReinforcement() { return reinforcement; }
    public DeepLearningCore getDeepLearning() { return deepLearning; }
    public LongTermGoalsAI getLongTermGoals() { return longTermGoals; }
    public GoalTree getGoalTree() { return goalTree; }
    public ActivityScheduler getActivityScheduler() { return activityScheduler; }

    // --- Stream E task 89: session persistence across restarts ---
    // Uses the (previously dead) PersistenceManager to save/restore the bot's persistent state so
    // a bot can gracefully resume where it left off instead of starting from scratch each launch.
    private PersistenceManager persist; // Stream E 89: initialized in ctor (needs `name`)

    /** Persist current session state (level, position, goal progress, adena snapshot). */
    public void saveSessionState() {
        try {
            persist.save("level", level);
            persist.save("charId", characterId);
            persist.save("x", x); persist.save("y", y); persist.save("z", z);
            persist.save("goal", aiPlayerGoalsSnapshot());
            persist.save("savedAt", System.currentTimeMillis());
            LOGGER.info("[" + name + "] SESSION SAVED (lv" + level + " @ " + x + "," + y + ")");
        } catch (Exception e) {
            LOGGER.warning("[" + name + "] Session save failed: " + e.getMessage());
        }
    }

    /** Restore persisted session state if present; returns true if anything was restored. */
    @SuppressWarnings("unchecked")
    public boolean loadSessionState() {
        try {
            Object lvl = persist.load("level");
            Object cx = persist.load("x"), cy = persist.load("y"), cz = persist.load("z");
            Object g = persist.load("goal");
            if (lvl == null) return false;
            setLevel((Integer) lvl);
            if (cx != null) x = (Integer) cx;
            if (cy != null) y = (Integer) cy;
            if (cz != null) z = (Integer) cz;
            if (g instanceof java.util.Map) {
                for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) g).entrySet()) {
                    longTermGoals.advanceGoal(LongTermGoalsAI.Goal.valueOf((String) e.getKey()),
                            (Integer) e.getValue());
                }
            }
            LOGGER.info("[" + name + "] SESSION RESTORED (lv" + level + " @ " + x + "," + y + ")");
            return true;
        } catch (Exception e) {
            LOGGER.info("[" + name + "] No prior session state to restore");
            return false;
        }
    }

    private java.util.Map<String, Object> aiPlayerGoalsSnapshot() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        for (LongTermGoalsAI.Goal g : LongTermGoalsAI.Goal.values()) {
            m.put(g.name(), longTermGoals.getGoalProgress(g));
        }
        return m;
    }

    // --- Stream E: expose the social & economy subsystems (tasks 83-87) ---
    // Same dead-wiring problem as Stream D: these singletons were instantiated in the ctor but had
    // NO getters, so MerchantAI/SocialAI could never reach them to record prices, form swarms, etc.
    public CollectiveKnowledge getCollectiveKnowledge() { return collectiveKnowledge; }
    public SwarmCoordinator getSwarmCoordinator() { return swarmCoordinator; }
    public DiplomacyEngine getDiplomacy() { return diplomacy; }
    public MarketEngine getMarketEngine() { return marketEngine; }
    public EconomicEngine getEconomicEngine() { return economicEngine; }
    public NetWorthOptimizer getNetWorthOptimizer() { return netWorthOptimizer; }

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
