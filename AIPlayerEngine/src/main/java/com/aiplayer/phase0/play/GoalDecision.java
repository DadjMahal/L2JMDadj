package com.aiplayer.phase0.play;

/** MODE: COMPLETE. Immutable per-tick decision produced by the goal planners. No IO — just data. */
public final class GoalDecision
{
    public final PlayerGoal goal;
    public final GoalAction action;

    /** Target object id for COMBAT_TARGET (else 0). */
    public final int targetObjId;
    /** Move destination for MOVE_TO (else 0). */
    public final int targetX;
    public final int targetY;
    public final int targetZ;
    /** The quest mob/item/npc that this step is about (QuestStep.targetId), 0 when none. */
    public final int questTargetId;
    /** Exact bypass command (after "-h " ) for BYPASS, else "". */
    public final String bypassCommand;
    /** Human-readable "why" for dashboards / logs. */
    public final String label;
    /** Terse reason string (also used in telemetry). */
    public final String reason;

    private GoalDecision(PlayerGoal goal, GoalAction action, int targetObjId,
                         int targetX, int targetY, int targetZ, int questTargetId,
                         String bypassCommand, String label, String reason)
    {
        this.goal = goal;
        this.action = action;
        this.targetObjId = targetObjId;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.questTargetId = questTargetId;
        this.bypassCommand = bypassCommand != null ? bypassCommand : "";
        this.label = label != null ? label : action.name();
        this.reason = reason != null ? reason : "";
    }

    // ================================================================
    // FACTORIES
    // ================================================================

    public static GoalDecision moveTo(PlayerGoal goal, int x, int y, int z, String label, String reason)
    {
        return new GoalDecision(goal, GoalAction.MOVE_TO, 0, x, y, z, 0, "", label, reason);
    }

    /** Quest-aware move that also carries the step's quest target id (npc/mob/item). */
    public static GoalDecision questMove(PlayerGoal goal, int x, int y, int z, int questTargetId,
                                         String label, String reason)
    {
        return new GoalDecision(goal, GoalAction.MOVE_TO, 0, x, y, z, questTargetId, "", label, reason);
    }

    public static GoalDecision combatTarget(PlayerGoal goal, int objId, String label, String reason)
    {
        return new GoalDecision(goal, GoalAction.COMBAT_TARGET, objId, 0, 0, 0, 0, "", label, reason);
    }

    public static GoalDecision bypass(PlayerGoal goal, String bypassCommand, String label, String reason)
    {
        return new GoalDecision(goal, GoalAction.BYPASS, 0, 0, 0, 0, 0, bypassCommand, label, reason);
    }

    public static GoalDecision retreat(PlayerGoal goal, int x, int y, int z, String label, String reason)
    {
        return new GoalDecision(goal, GoalAction.RETREAT, 0, x, y, z, 0, "", label, reason);
    }

    public static GoalDecision wait(PlayerGoal goal, String label, String reason)
    {
        return new GoalDecision(goal, GoalAction.WAIT, 0, 0, 0, 0, 0, "", label, reason);
    }

    public static GoalDecision none(String reason)
    {
        return new GoalDecision(null, GoalAction.NONE, 0, 0, 0, 0, 0, "", "idle", reason);
    }

    @Override
    public String toString()
    {
        return label;
    }
}
