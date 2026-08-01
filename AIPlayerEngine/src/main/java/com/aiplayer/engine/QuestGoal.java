package com.aiplayer.engine;

/**
 * Quest Goal Types
 * Defines what the AI needs to do to complete a quest
 */
public enum QuestGoal {
    COLLECT_ITEMS("Collect Items", true),
    KILL_MONSTERS("Kill Monsters", true),
    TALK_TO_NPC("Talk to NPC", false),
    CONDITION_CHECK("Check Conditions", false),
    TURN_IN("Turn In Quest", false),
    NONE("No Goal", false);
    
    private final String description;
    private final boolean requiresTracking;
    
    QuestGoal(String description, boolean requiresTracking) {
        this.description = description;
        this.requiresTracking = requiresTracking;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean requiresTracking() {
        return requiresTracking;
    }
}

/**
 * Goal Types enum for QuestDecision compatibility
 */
enum GoalType {
    COLLECT_ITEMS,
    KILL_MONSTERS,
    TALK_TO_NPC,
    CONDITION_CHECK,
    TURN_IN,
    NONE
}

/**
 * Quest Goal Detail - holds specific details for a quest goal
 */
class QuestGoalDetail {
    private final GoalType type;
    private final int itemId;
    private final int requiredCount;
    private final int monsterId;
    private final int count;
    private final int npcId;
    private final int x, y, z;
    
    public QuestGoalDetail(GoalType type) {
        this.type = type;
        this.itemId = 0;
        this.requiredCount = 0;
        this.monsterId = 0;
        this.count = 0;
        this.npcId = 0;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }
    
    public GoalType getType() { return type; }
    public int getItemId() { return itemId; }
    public int getRequiredCount() { return requiredCount; }
    public int getMonsterId() { return monsterId; }
    public int getCount() { return count; }
    public int getNpcId() { return npcId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}