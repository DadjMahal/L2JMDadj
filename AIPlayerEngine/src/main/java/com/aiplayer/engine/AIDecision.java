package com.aiplayer.engine;

/**
 * AI Decision Result
 * Contains decision and whether it should be executed
 */
public class AIDecision {
    private final boolean shouldExecute;
    private final AIAction action;
    private final long timestamp;

    public AIDecision() {
        this.shouldExecute = false;
        this.action = null;
        this.timestamp = System.currentTimeMillis();
    }

    public AIDecision(boolean shouldExecute) {
        this.shouldExecute = shouldExecute;
        this.action = null;
        this.timestamp = System.currentTimeMillis();
    }

    public AIDecision(boolean shouldExecute, AIAction action) {
        this.shouldExecute = shouldExecute;
        this.action = action;
        this.timestamp = System.currentTimeMillis();
    }

    public AIDecision(boolean shouldExecute, AIAction.ActionType type, Object... params) {
        this.shouldExecute = shouldExecute;
        this.action = new AIAction(type, params);
        this.timestamp = System.currentTimeMillis();
    }

    public boolean shouldExecute() {
        return shouldExecute && action != null;
    }

    public AIAction getAction() {
        return action;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
