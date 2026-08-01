package com.aiplayer.engine;

/**
 * Combat Decision Result
 * Contains the decision about what combat action to take
 */
public class CombatDecision {
    public enum Action {
        IDLE,
        ATTACK,
        USE_SKILL,
        HEAL,
        DEFEND,
        FLEE,
        ENGAGE_TARGET,
        LEAVE_COMBAT,
        AUTO_PLAY
    }
    
    private final Action action;
    private final String targetId;
    private final String skillId;
    private final boolean shouldExecute;
    private final long timestamp;
    
    private CombatDecision(Action action, String targetId, String skillId, boolean shouldExecute) {
        this.action = action;
        this.targetId = targetId;
        this.skillId = skillId;
        this.shouldExecute = shouldExecute;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Factory methods
    public static CombatDecision idle() {
        return new CombatDecision(Action.IDLE, null, null, false);
    }
    
    public static CombatDecision attack() {
        return new CombatDecision(Action.ATTACK, null, null, true);
    }
    
    public static CombatDecision attackTarget(String targetId) {
        return new CombatDecision(Action.ATTACK, targetId, null, true);
    }
    
    public static CombatDecision useSkill(String skillId, String targetId) {
        return new CombatDecision(Action.USE_SKILL, targetId, skillId, true);
    }
    
    public static CombatDecision heal() {
        return new CombatDecision(Action.HEAL, "SELF", "HEAL", true);
    }
    
    public static CombatDecision defend() {
        return new CombatDecision(Action.DEFEND, null, null, true);
    }
    
    public static CombatDecision flee() {
        return new CombatDecision(Action.FLEE, null, null, true);
    }
    
    public static CombatDecision engageTarget(String targetId) {
        return new CombatDecision(Action.ENGAGE_TARGET, targetId, null, true);
    }
    
    public static CombatDecision leaveCombat() {
        return new CombatDecision(Action.LEAVE_COMBAT, null, null, true);
    }
    
    public static CombatDecision autoPlay() {
        return new CombatDecision(Action.AUTO_PLAY, null, null, true);
    }
    
    // Getters
    public Action getAction() { return action; }
    public String getTargetId() { return targetId; }
    public String getSkillId() { return skillId; }
    public boolean shouldExecute() { return shouldExecute; }
    public long getTimestamp() { return timestamp; }
}