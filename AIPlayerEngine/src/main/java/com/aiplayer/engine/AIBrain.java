package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * AI Brain - Decision Making Engine
 * Core intelligence that drives AI player behavior
 * 
 * Orchestrates all AI modules: CombatAI, QuestAI, MerchantAI, SocialAI
 * with priority-based decision making for smart AI behaviors
 */
public class AIBrain {
    private static final Logger LOGGER = Logger.getLogger(AIBrain.class.getName());
    
    private final AIPlayer aiPlayer;
    private final AIModuleLoader moduleLoader;
    
    // AI Module references
    private CombatAI combatAI;
    private QuestAI questAI;
    private MerchantAI merchantAI;
    private SocialAI socialAI;
    private PKDecision pkDecision;
    
    public AIBrain(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.moduleLoader = new AIModuleLoader();
        this.pkDecision = new PKDecision();
    }
    
    /**
     * Initialize AI modules - called once after AIPlayer setup
     */
    public void initializeModules() {
        this.combatAI = new CombatAI(aiPlayer);
        this.questAI = new QuestAI(aiPlayer);
        this.merchantAI = new MerchantAI(aiPlayer);
        this.socialAI = new SocialAI(aiPlayer);
        LOGGER.info("[" + aiPlayer.getName() + "] AI Modules initialized - READY FOR COMBAT, QUEST, TRADE, SOCIAL ACTIONS");
    }
    
    /**
     * Main decision making method
     * Priority order: Emergency > Combat > Quest > Merchant > Social > Idle
     */
    public AIDecision makeDecision() {
        try {
            // Priority 1: Emergency situations
            AIDecision emergency = handleEmergency();
            if (emergency != null && emergency.shouldExecute()) {
                return emergency;
            }
            
            // Priority 2: Combat decisions
            if (AIConfiguration.getInstance().getBooleanProperty("behavior.combat.enabled", true)) {
                AIDecision combat = handleCombat();
                if (combat != null && combat.shouldExecute()) {
                    return combat;
                }
            }
            
            // Priority 3: Quest progression
            if (AIConfiguration.getInstance().getBooleanProperty("behavior.quest.enabled", true)) {
                AIDecision quest = handleQuest();
                if (quest != null && quest.shouldExecute()) {
                    return quest;
                }
            }
            
            // Priority 4: Merchant/Trade behavior
            if (AIConfiguration.getInstance().getBooleanProperty("behavior.merchant.enabled", true)) {
                AIDecision trade = handleMerchant();
                if (trade != null && trade.shouldExecute()) {
                    return trade;
                }
            }
            
            // Priority 5: Social decisions
            if (AIConfiguration.getInstance().getBooleanProperty("behavior.social.enabled", true)) {
                AIDecision social = handleSocial();
                if (social != null && social.shouldExecute()) {
                    return social;
                }
            }
            
            // Priority 6: Default behavior
            return handleDefaultBehavior();
            
        } catch (Exception e) {
            LOGGER.severe("AI Brain error for " + aiPlayer.getName() + ": " + e.getMessage());
            return new AIDecision(false);
        }
    }
    
    /**
     * Handle emergency situations
     */
    private AIDecision handleEmergency() {
        int currentHP = getSimulatedHP();
        if (currentHP < 30) {
            LOGGER.info("[" + aiPlayer.getName() + "] EMERGENCY: Low HP (" + currentHP + "%) - HEALING!");
            return new AIDecision(true, AIAction.ActionType.USE_ITEM, "HEAL_POTION", 1);
        }
        return new AIDecision(false);
    }
    
    /**
     * Handle COMBAT AI decisions
     */
    private AIDecision handleCombat() {
        CombatDecision decision = getCombatAI().makeDecision();
        if (decision.shouldExecute()) {
            switch (decision.getAction()) {
                case ATTACK:
                    return new AIDecision(true, AIAction.ActionType.ATTACK, 
                        decision.getTargetId() != null ? decision.getTargetId() : "AUTO_TARGET");
                case USE_SKILL:
                case HEAL:
                    return new AIDecision(true, AIAction.ActionType.USE_ITEM, 
                        decision.getSkillId() != null ? decision.getSkillId() : "HEAL_POTION", 1);
                case DEFEND:
                    return new AIDecision(true, AIAction.ActionType.STAND, true);
                case FLEE:
                    return new AIDecision(true, AIAction.ActionType.MOVE, 15000, 15000, 434);
                case ENGAGE_TARGET:
                    return new AIDecision(true, AIAction.ActionType.ATTACK, decision.getTargetId());
                case LEAVE_COMBAT:
                    return new AIDecision(true, AIAction.ActionType.STOP_ATTACK);
                case AUTO_PLAY:
                    return new AIDecision(true, AIAction.ActionType.ATTACK, "AUTO_TARGET");
                case IDLE:
                default:
                    return new AIDecision(false);
            }
        }
        return new AIDecision(false);
    }
    
    /**
     * Handle QUEST AI decisions
     */
    private AIDecision handleQuest() {
        QuestDecision decision = getQuestAI().makeDecision();
        if (decision.shouldExecute()) {
            switch (decision.getAction()) {
                case ACCEPT_QUEST:
                    return new AIDecision(true, AIAction.ActionType.INTERACT_NPC, 
                        decision.getQuestId(), "ACCEPT");
                case KILL_MONSTER:
                    return new AIDecision(true, AIAction.ActionType.HUNT, 
                        decision.getItemId(), decision.getCount());
                case COLLECT_ITEM:
                    return new AIDecision(true, AIAction.ActionType.HUNT, 
                        decision.getItemId(), decision.getCount());
                case FIND_NPC:
                    return new AIDecision(true, AIAction.ActionType.MOVE, 
                        decision.getX(), decision.getY(), decision.getZ());
                case TURN_IN_QUEST:
                    return new AIDecision(true, AIAction.ActionType.INTERACT_NPC, 
                        decision.getQuestId(), "TURN_IN");
                case DAILY_QUEST_CYCLE:
                case CLASS_CHANGE_QUEST:
                    return new AIDecision(true, AIAction.ActionType.MOVE, 16600, 17000, 434);
                case IDLE:
                default:
                    return new AIDecision(false);
            }
        }
        return new AIDecision(false);
    }
    
    /**
     * Handle MERCHANT AI decisions
     */
    private AIDecision handleMerchant() {
        MerchantDecision decision = getMerchantAI().makeDecision();
        if (decision.shouldExecute()) {
            switch (decision.getAction()) {
                case BUY_ITEM:
                    return new AIDecision(true, AIAction.ActionType.BUY, 
                        decision.getItemId(), decision.getCount());
                case SELL_ITEM:
                    return new AIDecision(true, AIAction.ActionType.SELL, 
                        decision.getItemId(), decision.getCount());
                case INTERACT_MERCHANT:
                    return new AIDecision(true, AIAction.ActionType.INTERACT_NPC, "MERCHANT", "TRADE");
                case FIND_MERCHANT:
                    MerchantNPC merchant = decision.getMerchant();
                    if (merchant != null) {
                        return new AIDecision(true, AIAction.ActionType.MOVE, 
                            merchant.getX(), merchant.getY(), merchant.getZ());
                    }
                    break;
                case EMERGENCY_SELL:
                    return new AIDecision(true, AIAction.ActionType.SELL, "ANY_VALUABLE", 5);
                case ARBITRAGE:
                case BULK_BUY:
                    return new AIDecision(true, AIAction.ActionType.MOVE, 16600, 17000, 434);
                case IDLE:
                default:
                    return new AIDecision(false);
            }
        }
        return new AIDecision(false);
    }
    
    /**
     * Handle SOCIAL AI decisions
     */
    private AIDecision handleSocial() {
        SocialDecision decision = getSocialAI().makeDecision();
        if (decision.shouldExecute()) {
            switch (decision.getAction()) {
                case CHAT:
                    return new AIDecision(true, AIAction.ActionType.CHAT, decision.getMessage());
                case INVITE_TO_PARTY:
                    return new AIDecision(true, AIAction.ActionType.PARTY_INVITE, 
                        decision.getTargetId());
                case JOIN_PARTY:
                case JOIN_CLAN:
                    return new AIDecision(true, AIAction.ActionType.MOVE, 16600, 17000, 434);
                case COORDINATE_PARTY:
                    return new AIDecision(true, AIAction.ActionType.CHAT, "Party activity started!");
                case FOLLOW_LEADER:
                    return new AIDecision(true, AIAction.ActionType.MOVE, 
                        decision.getX(), decision.getY(), decision.getZ());
                case ASSIST_PARTY:
                    return new AIDecision(true, AIAction.ActionType.COMBAT_MODE, true);
                case IDLE:
                default:
                    return new AIDecision(false);
            }
        }
        return new AIDecision(false);
    }
    
    private AIDecision handleDefaultBehavior() {
        if (Math.random() > 0.5) {
            int newX = 16600 + (int)(Math.random() * 100);
            int newY = 17000 + (int)(Math.random() * 100);
            return new AIDecision(true, AIAction.ActionType.MOVE, newX, newY, 434);
        }
        return new AIDecision(false);
    }
    
    // Helper methods
    private int getSimulatedHP() {
        return 85 + (int)(Math.random() * 15);
    }
    
    public CombatAI getCombatAI() { return combatAI; }
    public QuestAI getQuestAI() { return questAI; }
    public MerchantAI getMerchantAI() { return merchantAI; }
    public SocialAI getSocialAI() { return socialAI; }
    public PKDecision getPKDecision() { return pkDecision; }
}
