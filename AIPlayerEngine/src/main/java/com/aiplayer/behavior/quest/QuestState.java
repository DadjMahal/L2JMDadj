package com.aiplayer.behavior.quest;

/**
 * Quest State Management
 * Tracks the current state of quests for an AI player
 */
public class QuestState {
    private String questId;
    private int state; // 0 = CREATED, 1 = STARTED, 2 = COMPLETED
    private int cond;  // Condition variable
    private long startTime;
    private long deadline;
    private boolean repeatable;
    private boolean completedToday;

    public QuestState() {
        this.state = 0;
        this.cond = 0;
    }

    public boolean isActive() {
        return state > 0 && state < 2;
    }

    public boolean canAcceptNew() {
        // Logic to determine if we should accept new quest
        // Returns true when no quest is currently active (deterministic)
        return state == 0;
    }

    public boolean isImpossible() {
        // Check if quest is impossible to complete
        // e.g., required items not spawning, monster too strong
        if (deadline > 0 && System.currentTimeMillis() > deadline) {
            return true;
        }
        return false;
    }

    public void acceptQuest(String questId, int deadlineHours) {
        this.questId = questId;
        this.state = 1;
        this.cond = 1;
        this.startTime = System.currentTimeMillis();
        this.deadline = System.currentTimeMillis() + (deadlineHours * 3600 * 1000L);
    }

    public void completeQuest() {
        this.state = 2;
        this.completedToday = true;
    }

    public void abandonQuest() {
        this.state = 0;
        this.cond = 0;
        this.questId = null;
    }

    // Getters
    public String getQuestId() { return questId; }
    public int getState() { return state; }
    public int getCond() { return cond; }
    public long getStartTime() { return startTime; }
    public long getDeadline() { return deadline; }
    public boolean isRepeatable() { return repeatable; }
    public boolean isCompletedToday() { return completedToday; }

    // Setters
    public void setCond(int cond) { this.cond = cond; }

    public void setQuestId(String questId) { this.questId = questId; }
}
