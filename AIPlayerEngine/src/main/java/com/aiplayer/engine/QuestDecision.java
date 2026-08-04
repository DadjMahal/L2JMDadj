package com.aiplayer.engine;

/**
 * Quest Decision Result
 * Contains the decision about what quest action to take
 */
public class QuestDecision {
    public enum Action {
        IDLE,
        ACCEPT_QUEST,
        TALK_TO_NPC,
        KILL_MONSTER,
        COLLECT_ITEM,
        FIND_NPC,
        CHECK_CONDITIONS,
        TURN_IN_QUEST,
        ABANDON_QUEST,
        DAILY_QUEST_CYCLE,
        CLASS_CHANGE_QUEST
    }
    
    private final Action action;
    private final String questId;
    private final String itemId;
    private final int count;
    private final String npcId;
    private final int x, y, z;
    private final boolean shouldExecute;
    private final long timestamp;
    
    private QuestDecision(Action action, String questId, String itemId, int count,
                         String npcId, int x, int y, int z, boolean shouldExecute) {
        this.action = action;
        this.questId = questId;
        this.itemId = itemId;
        this.count = count;
        this.npcId = npcId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.shouldExecute = shouldExecute;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Factory methods
    public static QuestDecision talkToNPC(String questId, String npcId, int x, int y, int z) {
        return new QuestDecision(Action.TALK_TO_NPC, questId, null, 0, npcId, x, y, z, true);
    }
    
    // Factory methods
    public static QuestDecision idle() {
        return new QuestDecision(Action.IDLE, null, null, 0, null, 0, 0, 0, false);
    }
    
    public static QuestDecision acceptQuest(String questId, String npcId, int x, int y, int z) {
        return new QuestDecision(Action.ACCEPT_QUEST, questId, null, 0, npcId, x, y, z, true);
    }
    
    public static QuestDecision killMonster(String monsterId, int count) {
        return new QuestDecision(Action.KILL_MONSTER, null, null, count, monsterId, 0, 0, 0, true);
    }
    
    public static QuestDecision collectItem(String itemId, int count) {
        return new QuestDecision(Action.COLLECT_ITEM, null, itemId, count, null, 0, 0, 0, true);
    }
    
    public static QuestDecision findNPC(String npcId, int x, int y, int z) {
        return new QuestDecision(Action.FIND_NPC, null, null, 0, npcId, x, y, z, true);
    }
    
    public static QuestDecision checkConditions(String questId) {
        return new QuestDecision(Action.CHECK_CONDITIONS, questId, null, 0, null, 0, 0, 0, true);
    }
    
    public static QuestDecision turnInQuest(String questId) {
        return new QuestDecision(Action.TURN_IN_QUEST, questId, null, 0, null, 0, 0, 0, true);
    }
    
    public static QuestDecision abandonQuest(String questId) {
        return new QuestDecision(Action.ABANDON_QUEST, questId, null, 0, null, 0, 0, 0, true);
    }
    
    public static QuestDecision dailyQuestCycle() {
        return new QuestDecision(Action.DAILY_QUEST_CYCLE, null, null, 0, null, 0, 0, 0, true);
    }
    
    public static QuestDecision classChangeQuest() {
        return new QuestDecision(Action.CLASS_CHANGE_QUEST, null, null, 0, null, 0, 0, 0, true);
    }

    // Stream C7 helper: Request quest list (0x63)
    public static QuestDecision requestQuestList() {
        return new QuestDecision(Action.IDLE, null, null, 0, null, 0, 0, 0, false);
    }

    public static QuestDecision findBestQuest() {
        return new QuestDecision(Action.FIND_NPC, "BEST_QUEST_ID", null, 0, 
                                "RECOMMEND_NPC", 16600, 17000, 434, true);
    }
    
    // Getters
    public Action getAction() { return action; }
    public String getQuestId() { return questId; }
    public String getItemId() { return itemId; }
    public int getCount() { return count; }
    public String getNpcId() { return npcId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public boolean shouldExecute() { return shouldExecute; }
    public long getTimestamp() { return timestamp; }
}