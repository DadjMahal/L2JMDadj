package com.aiplayer.behavior;

/** MODE: COMPLETE. Mid-level goal a bot is currently pursuing — the top of its priority ladder. */
public enum PlayerGoal
{
    /** Stay alive: retreat / stop fighting when dangerously low on HP. */
    SURVIVE,
    /** Advance the active quest: travel to the NPC, farm its objectives, return, turn in. */
    QUEST,
    /** No active quest: go get (accept) the level-appropriate quest from its giver. */
    ACQUIRE,
    /** Plain combat farming for XP/adena when no meaningful quest step is pending. */
    FARM,
    /** Short pause (cast breathing room / restock), still intentional, never idle-wander. */
    REST
}
