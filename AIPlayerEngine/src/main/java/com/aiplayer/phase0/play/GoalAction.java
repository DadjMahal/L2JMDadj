package com.aiplayer.phase0.play;

/** MODE: COMPLETE. Atomic executable intent a bot emits each tick; the fleet loop maps it to a proven primitive. */
public enum GoalAction
{
    /** Move toward targetX/Y/Z (via the server-ack-gated hop loop). */
    MOVE_TO,
    /** Attack targetObjId via the proven combat frame path. */
    COMBAT_TARGET,
    /** Send an exact bypass command (quest accept / turn-in) previously shown in an NpcHtmlMessage. */
    BYPASS,
    /** Stop / hold a beat (respawn, low-HP disengage, between steps). */
    WAIT,
    /** Retreat from an away point (computed as away from nearest hostile, clamped). */
    RETREAT,
    /** Buy from a vendor (restock intent: walk to the town vendor and shop). */
    BUY,
    /** No actionable step this tick (should be rare; the controller suppresses idling). */
    NONE
}
