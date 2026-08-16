package com.aiplayer.phase0.guide;

/**
 * MODE: COMPLETE. A single quest node on a race/profession guide path.
 *
 * <p>Carries the real quest id (from the datapack quest script {@code super(id, "name")}), the
 * start/return NPC id and its real in-world coordinate (resolved from the datapack {@code spawns/*.xml}),
 * the town/zone the node lives in, and a recommended starting level.
 */
public final class QuestNode
{
    public enum Kind
    {
        NEWBIE_TUTORIAL, // race newbie-helper (Q999) and starting village quests
        NEWBIE,          // level 5-20 village quest
        CLASS_CHANGE_1,  // first profession: "Path of the X" quests (Q401-418)
        CLASS_CHANGE_2,  // second profession: Trial/Testimony quests (Q211-233)
        CLASS_CHANGE_3,  // third profession: Saga quests (Q70-100)
        SIDE,            // optional side quest near a primary location
        STORY;           // main storyline quest (e.g. Fate's Whisper)
    }

    public final int questId;
    public final String name;
    public final Kind kind;
    public final boolean core;      // in the primary guide line vs. optional
    public final int npcId;
    public final String npcName;
    public final int x;
    public final int y;
    public final int z;
    public final String town;
    public final String zone;
    public final int levelMin;

    public QuestNode(int questId, String name, Kind kind, boolean core, int npcId, String npcName, int x, int y, int z, String town, String zone, int levelMin)
    {
        this.questId = questId;
        this.name = name;
        this.kind = kind;
        this.core = core;
        this.npcId = npcId;
        this.npcName = npcName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.town = town != null ? town : "";
        this.zone = zone != null ? zone : "";
        this.levelMin = levelMin;
    }

    /** Human readable signature, e.g. {@code "Q401 Path to a Warrior @ Gludin (npc 30010)"}. */
    public String signature()
    {
        return "Q" + questId + " " + name + " @" + town + " (npc " + npcId + ")";
    }

    @Override
    public String toString()
    {
        return signature();
    }
}