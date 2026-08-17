package com.aiplayer.phase0.guide;

/**
 * MODE: COMPLETE. A gatekeeper teleport leg between two towns/zones.
 *
 * <p>Coordinates are the destination's anchored point from the datapack town zones / teleporter
 * data, and the cost/level mirror {@code com.aiplayer.phase0.town.TeleportManager}. Used by the
 * guide to route the AI across lands (boat/gatekeeper) when all quests of the current region are done.
 */
public final class TeleportLeg
{
    public final String fromTown;
    public final String toTown;
    public final int cost;
    public final int requiredLevel;
    public final int x;
    public final int y;
    public final int z;
    public final String description;

    public TeleportLeg(String fromTown, String toTown, int cost, int requiredLevel, int x, int y, int z, String description)
    {
        this.fromTown = fromTown;
        this.toTown = toTown;
        this.cost = cost;
        this.requiredLevel = requiredLevel;
        this.x = x;
        this.y = y;
        this.z = z;
        this.description = description;
    }

    @Override
    public String toString()
    {
        return fromTown + " -> " + toTown + " (" + cost + "a, Lv" + requiredLevel + ")";
    }
}
