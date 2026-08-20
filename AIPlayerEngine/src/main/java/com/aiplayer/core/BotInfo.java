package com.aiplayer.core;


/**
 * Live per-bot row shown on the dashboard. Written by the fleet game loop, read by
 * DashboardApi (single writer per bot = the bot's own thread, no cross-thread mutation).
 * WPT-01: extracted from the nested FleetPlay.BotInfo so the web layer can serialize it
 * without coupling to the launcher internals. EP-4: moved examples -> core (the session
 * machine owns its row; the dashboard only reads it).
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
    public volatile long adenaEarned;   // S7-T04: session adena income (deltas over time)
    public volatile int[][] items;      // {itemId, count} top-5 by count
    public volatile int mobs, npcs;
    public volatile int[][] ents;       // {objId, kind(0 npc 1 hostile 2 player), x, y, z}
    // WPT-27: quest journal telemetry (fed from PacketLogger.getActiveQuestList() — QUEST_LIST 0x80).
    public volatile int questCount;     // quests in-progress (non-zero journal state)
    public volatile int totalQuestCount;// all quests in the journal
    public volatile int[][] activeQuests;// {{questId, state}, ...} from the live journal
    public volatile String action = "";
    public volatile String thought = "";
    /** Race label (ELF/DARK_ELF/ORC/DWARF/HUMAN) for dashboard race filter + badge (S9-T05). */
    public volatile String race = "";
    /** Life-kills counter, bumped on each EVIDENCE-H5 EXP receipt (S9-T06 kills/min). */
    public volatile long killCount;
    /** S2-T04: per-bot packet health (fed from GameServerClient). */
    public volatile long packetsRead;
    public volatile int idleTimeouts;
    /** S5-T06: % of relocation hops the server acked (100 = healthy, low = the CC/freeze problem). */
    public volatile int hopSuccessPct = 100;
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