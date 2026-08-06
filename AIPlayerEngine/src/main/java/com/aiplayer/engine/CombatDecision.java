package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * Combat Decision Result
 * Contains the decision about what combat action to take
 * Enhanced with PvP support and telemetry
 */
public class CombatDecision {
    private static final Logger LOGGER = Logger.getLogger(CombatDecision.class.getName());

    public enum Action {
        IDLE,
        ATTACK,
        USE_SKILL,
        HEAL,
        DEFEND,
        BLOCK,
        FLEE,
        ENGAGE_TARGET,
        LEAVE_COMBAT,
        AUTO_PLAY,
        CAMPAIGN,
        RETREAT
    }

    private final Action action;
    private final String targetId;
    private final String skillId;
    private final boolean shouldExecute;
    private final long timestamp;
    private final String reason;

    private CombatDecision(Action action, String targetId, String skillId, boolean shouldExecute, String reason) {
        this.action = action;
        this.targetId = targetId;
        this.skillId = skillId;
        this.shouldExecute = shouldExecute;
        this.timestamp = System.currentTimeMillis();
        this.reason = reason != null ? reason : "";
    }

    // Factory methods
    public static CombatDecision idle() {
        return new CombatDecision(Action.IDLE, null, null, false, "No action needed");
    }

    public static CombatDecision attack() {
        return new CombatDecision(Action.ATTACK, null, null, true, "Standard attack");
    }

    public static CombatDecision attackTarget(String targetId) {
        return new CombatDecision(Action.ATTACK, targetId, null, true, "Attack target " + targetId);
    }

    public static CombatDecision useSkill(String skillId, String targetId) {
        return new CombatDecision(Action.USE_SKILL, targetId, skillId, true, "Using skill " + skillId);
    }

    public static CombatDecision useSkill(String skillId, String targetId, String reason) {
        return new CombatDecision(Action.USE_SKILL, targetId, skillId, true, "Using skill " + skillId + ": " + reason);
    }

    public static CombatDecision heal() {
        return new CombatDecision(Action.HEAL, "SELF", "HEAL", true, "Low health recovery");
    }

    public static CombatDecision defend() {
        return new CombatDecision(Action.DEFEND, null, null, true, "Threat detected");
    }

    public static CombatDecision block() {
        return new CombatDecision(Action.BLOCK, null, null, true, "Incoming attack blocked");
    }

    public static CombatDecision flee() {
        return new CombatDecision(Action.FLEE, null, null, true, "Critical health escape");
    }

    public static CombatDecision retreat() {
        return new CombatDecision(Action.RETREAT, null, null, true, "Strategic withdrawal");
    }

    public static CombatDecision engageTarget(String targetId) {
        return new CombatDecision(Action.ENGAGE_TARGET, targetId, null, true, "New enemy detected");
    }

    public static CombatDecision leaveCombat() {
        return new CombatDecision(Action.LEAVE_COMBAT, null, null, true, "Combat ended");
    }

    public static CombatDecision leaveCombat(String reason) {
        return new CombatDecision(Action.LEAVE_COMBAT, null, null, true, "Combat ended: " + reason);
    }

    public static CombatDecision autoPlay() {
        return new CombatDecision(Action.AUTO_PLAY, null, null, true, "AutoPlay mode active");
    }

    // Getters
    public Action getAction() { return action; }
    public String getTargetId() { return targetId; }
    public String getSkillId() { return skillId; }
    public boolean shouldExecute() { return shouldExecute; }
    public long getTimestamp() { return timestamp; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return "CombatDecision{" +
                "action=" + action +
                ", targetId='" + targetId + '\'' +
                ", skillId='" + skillId + '\'' +
                ", shouldExecute=" + shouldExecute +
                ", timestamp=" + timestamp +
                ", reason='" + reason + '\'' +
                '}';
    }
}