package com.aiplayer.engine;

import java.util.logging.Logger;

import com.aiplayer.protocol.PacketLogger;

/**
 * Quest AI Module.
 *
 * Handles quest acceptance, tracking, and completion for AI players.
 * Integrates with L2JMobius quest system via RequestBypassToServer(0x21).
 *
 * Telemetry: PacketLogger tracks QuestInfo, NPC_INFO, and
 * StatusUpdate packets to drive real quest decisions. A shared PacketLogger
 * must be set via setPacketLogger so the AI sees live server quest state.
 *
 * The decision flow mirrors CombatAI: makeDecision() returns a QuestDecision,
 * which QuestFramePlanner.plan() maps to wire packets.
 *
 * Stream C7 wiring: QuestAI.makeDecision() -> QuestFramePlanner.plan() ->
 *   QuestFramePlanner.QuestFrame[] -> GameServerClient.sendGameFrame().
 */
public class QuestAI {
    private static final Logger LOGGER = Logger.getLogger(QuestAI.class.getName());

    private final AIPlayer aiPlayer;
    private final QuestConfig config;
    private PacketLogger packetLogger;
    private QuestState currentQuestState;
    private QuestGoalDetail currentGoal;

    public QuestAI(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.config = QuestConfig.getInstance();
        this.packetLogger = new PacketLogger(aiPlayer.getName());
        this.currentQuestState = new QuestState();
    }

    /** Attach the live PacketLogger so quest decisions see real server state. */
    public void setPacketLogger(PacketLogger logger) {
        this.packetLogger = logger;
        LOGGER.fine("[QuestAI] packetLogger attached for " + aiPlayer.getName());
    }

    /** Get the packet logger for telemetry. */
    public PacketLogger getPacketLogger() { return packetLogger; }

    /** Check if the bot is alive based on real HP from PacketLogger. */
    public boolean isBotAlive() {
        return packetLogger != null && packetLogger.getCurHp() > 0;
    }

    /**
     * Main quest decision method.
     * Decides what quest actions to take based on current state and live telemetry.
     */
    public QuestDecision makeDecision() {
        if (!config.isEnabled()) {
            return QuestDecision.idle();
        }

        try {
            if (hasActiveQuests()) {
                return manageActiveQuest();
            }
            if (shouldAcceptQuest()) {
                return findAndAcceptQuest();
            }
            if (shouldAbandonQuest()) {
                return abandonQuest();
            }
            return QuestDecision.idle();
        } catch (Exception e) {
            LOGGER.warning("Quest AI error for " + aiPlayer.getName() + ": " + e.getMessage());
            return QuestDecision.idle();
        }
    }

    private QuestDecision manageActiveQuest() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_STEP: active questId="
            + currentQuestState.getQuestId() + " cond=" + currentQuestState.getCond());

        if (currentGoal == null) {
            return getNextQuestAction();
        }

        switch (currentGoal.getType()) {
            case COLLECT_ITEMS:
                LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_STEP: COLLECT_ITEMS questId=" + currentQuestState.getQuestId());
                return handleItemCollection();
            case KILL_MONSTERS:
                LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_STEP: KILL_MONSTERS questId=" + currentQuestState.getQuestId());
                return handleMonsterHunt();
            case TALK_TO_NPC:
                LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_STEP: TALK_TO_NPC questId=" + currentQuestState.getQuestId());
                return handleNPCLocation();
            case CONDITION_CHECK:
                LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_STEP: CONDITION_CHECK questId=" + currentQuestState.getQuestId());
                return handleConditionCheck();
            case TURN_IN:
                LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_STEP: TURN_IN questId=" + currentQuestState.getQuestId());
                return handleQuestTurnIn();
            default:
                return QuestDecision.idle();
        }
    }

    private boolean hasActiveQuests() {
        int serverQuestCount = packetLogger != null ? packetLogger.getActiveQuestCount() : 0;
        boolean locallyActive = currentQuestState.isActive();

        if (serverQuestCount > 0) {
            LOGGER.fine("[QuestAI] Server reports " + serverQuestCount + " active quests for " + aiPlayer.getName());
            return true;
        }
        return locallyActive;
    }

    private boolean shouldAcceptQuest() {
        if (hasActiveQuests()) {
            return false;
        }
        return currentQuestState.canAcceptNew();
    }

    private boolean shouldAbandonQuest() {
        return currentQuestState.isImpossible();
    }

    /**
     * Find and accept a suitable quest.
     * If no quest is active, accept the Tutorial quest (Q00255) by talking to
     * the Newbie Helper NPC (npcId 30530) at the tutorial village spawn (-84058, 243239, -3730).
     */
    private QuestDecision findAndAcceptQuest() {
        int level = aiPlayer.getLevel();

        if (!hasActiveQuests()) {
            String questId = "Q00255_Tutorial";
            LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName()
                + "] QUEST_ACCEPT: questId=" + questId + " level=" + level);
            currentQuestState.acceptQuest(questId, 24);
            return QuestDecision.acceptQuest(questId, "30530", -84058, 243239, -3730);
        }

        return analyzeQuestOpportunities();
    }

    private QuestDecision abandonQuest() {
        String questId = currentQuestState.getQuestId();
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_ABANDONED: questId=" + questId);
        return QuestDecision.abandonQuest(questId);
    }

    private QuestDecision getNextQuestAction() {
        int cond = currentQuestState.getCond();
        String questId = currentQuestState.getQuestId();

        if (questId != null && questId.equals("Q00255_Tutorial")) {
            LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] TUTORIAL_STEP: cond=" + cond);
            return QuestDecision.talkToNPC(questId, "30530", -84058, 243239, -3730);
        }

        return analyzeQuestOpportunities();
    }

    private QuestDecision handleItemCollection() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] COLLECT_ITEMS: itemId=" + currentGoal.getItemId()
            + " count=" + currentGoal.getRequiredCount());
        return QuestDecision.collectItem(String.valueOf(currentGoal.getItemId()), currentGoal.getRequiredCount());
    }

    private QuestDecision handleMonsterHunt() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] KILL_MONSTER: monsterId=" + currentGoal.getMonsterId()
            + " count=" + currentGoal.getCount());
        return QuestDecision.killMonster(String.valueOf(currentGoal.getMonsterId()), currentGoal.getCount());
    }

    private QuestDecision handleNPCLocation() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] TALK_TO_NPC: npcId=" + currentGoal.getNpcId()
            + " pos=" + currentGoal.getX() + "," + currentGoal.getY() + "," + currentGoal.getZ());
        return QuestDecision.talkToNPC(currentQuestState.getQuestId(), String.valueOf(currentGoal.getNpcId()),
            currentGoal.getX(), currentGoal.getY(), currentGoal.getZ());
    }

    private QuestDecision handleConditionCheck() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] CHECK_CONDITIONS: questId=" + currentQuestState.getQuestId());
        return QuestDecision.checkConditions(currentQuestState.getQuestId());
    }

    private QuestDecision handleQuestTurnIn() {
        String questId = currentQuestState.getQuestId();
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_COMPLETED: questId=" + questId);
        return QuestDecision.turnInQuest(questId);
    }

    public QuestDecision analyzeQuestOpportunities() {
        int level = aiPlayer.getLevel();
        if (level < 10) {
            LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] QUEST_RECOMMEND: Q00255_Tutorial");
            return QuestDecision.findBestQuest();
        }
        return QuestDecision.findBestQuest();
    }

    public QuestDecision handleDailyQuests() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] DAILY_QUEST_CYCLE");
        return QuestDecision.dailyQuestCycle();
    }

    public QuestDecision handleClassChange() {
        LOGGER.info("[QUEST-LOG] [" + aiPlayer.getName() + "] CLASS_CHANGE_QUEST");
        return QuestDecision.classChangeQuest();
    }

    public void updateFromLiveState() {
        if (packetLogger == null) return;

        int newQuestCount = packetLogger.getActiveQuestCount();
        int curCount = currentQuestState.isActive() ? 1 : 0;
        if (newQuestCount > curCount) {
            LOGGER.info("[QuestAI] Quest count changed: " + curCount + " -> " + newQuestCount
                + " for " + aiPlayer.getName());
        }
    }
}
