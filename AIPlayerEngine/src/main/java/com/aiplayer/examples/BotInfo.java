package com.aiplayer.examples;

/**
 * Live per-bot row shown on the dashboard. Written by the fleet game loop, read by
 * DashboardApi (single writer per bot = the bot's own thread, no cross-thread mutation).
 * WPT-01: extracted from the nested FleetPlay.BotInfo so the web layer can serialize it
 * without coupling to the launcher internals.
 */
public final class BotInfo
{
    public final String account;
    public final int charId;

    public volatile String charName = "";
    public volatile int level;
    public volatile long exp;
    public volatile int sp;
    public volatile int hp, hpMax, mp, mpMax, cp, cpMax;
    public volatile int x, y, z, heading;
    public volatile int load, maxLoad;
    public volatile boolean weapon;
    public volatile int adena, invPct, itemCount;
    public volatile int[][] items;      // {itemId, count} top-5 by count
    public volatile int mobs, npcs;
    public volatile int[][] ents;       // {objId, kind(0 npc 1 hostile 2 player), x, y, z}
    public volatile String action = "";
    public volatile String thought = "";
    public volatile int targetObjId;
    public volatile String targetLabel = "";
    public volatile int targetKind;
    public volatile int targetX, targetY, targetZ;
    public volatile double targetDist;
    public volatile long sessionStartMs = System.currentTimeMillis();
    public volatile String state = "connecting";
    public volatile boolean connected;
    public volatile boolean loggedIn;
    public volatile long lastSeenMs = System.currentTimeMillis();

    public BotInfo(String account, int charId)
    {
        this.account = account;
        this.charId = charId;
    }

    /** Millis since the last raw packet updated this row (0 when just refreshed). */
    public long getPktAgeMs()
    {
        return Math.max(0, System.currentTimeMillis() - lastSeenMs);
    }
}