package com.aiplayer.behavior.quest;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable metadata describing a Lineage II quest.
 * Covers all quest types: kill, collect, talk, escort, class change, and multi-step chains.
 *
 * Each quest is broken into ordered steps. The executor advances through
 * steps as objectives are completed, with human-like pauses between actions.
 */
public final class QuestInfo {

    public enum QuestType {
        KILL,           // Kill specific mobs
        COLLECT,        // Collect drops from mobs
        TALK,           // Talk to NPC(s)
        ESCORT,         // Escort / protect NPC
        CLASS_CHANGE,   // 1st / 2nd / 3rd class transfer
        COMBAT_ABILITY, // Proof of combat skill (exaltation etc.)
        MULTI_STEP      // Mixed chain of the above
    }

    public enum StepType {
        NAVIGATE,       // Move to zone / coordinates
        KILL,           // Kill mob(s)
        COLLECT,        // Gather item(s) from mobs or ground
        TALK,           // Interact with NPC
        USE_ITEM,       // Use a quest item
        WAIT,           // Wait for spawn / respawn / event
        COMBAT,         // Defeat a specific boss / mob
        RETURN          // Return to quest giver
    }

    public static final class QuestStep {
        public final StepType stepType;
        public final int targetId;      // NPC ID, mob ID, or item ID
        public final int targetCount;   // Kill count / collect count
        public final String zoneName;   // Zone hint for navigation
        public final int zoneX;         // Approximate X
        public final int zoneY;         // Approximate Y
        public final int zoneZ;         // Approximate Z
        public final String stepDesc;   // Human-readable description

        public QuestStep(StepType stepType, int targetId, int targetCount,
                         String zoneName, int zoneX, int zoneY, int zoneZ,
                         String stepDesc) {
            this.stepType = stepType;
            this.targetId = targetId;
            this.targetCount = targetCount;
            this.zoneName = zoneName;
            this.zoneX = zoneX;
            this.zoneY = zoneY;
            this.zoneZ = zoneZ;
            this.stepDesc = stepDesc;
        }
    }

    public static final class QuestReward {
        public final long xp;
        public final long sp;
        public final long adena;
        public final List<Integer> itemIds;
        public final List<Integer> itemCounts;

        public QuestReward(long xp, long sp, long adena,
                           List<Integer> itemIds, List<Integer> itemCounts) {
            this.xp = xp;
            this.sp = sp;
            this.adena = adena;
            this.itemIds = itemIds != null ? itemIds : Collections.emptyList();
            this.itemCounts = itemCounts != null ? itemCounts : Collections.emptyList();
        }
    }

    // Core identity
    public final int questId;
    public final String name;
    public final QuestType type;

    // Requirements
    public final int minLevel;
    public final int maxLevel;
    public final int raceMask;          // Bitmask: 1=Human, 2=Elf, 4=DarkElf, 8=Orc, 16=Dwarf, 32=Kamael
    public final int classMask;         // Bitmask for valid classes (0 = any)
    public final Set<Integer> prerequisiteQuestIds;

    // Execution
    public final List<QuestStep> steps;
    public final QuestReward reward;

    // Meta
    public final String startNpc;       // NPC name who gives the quest
    public final int startNpcId;
    public final String zoneName;       // Primary zone
    public final int recommendedLevel;
    public final boolean isRepeatable;
    public final int estimatedMinutes; // Rough human estimate for planning

    public QuestInfo(int questId, String name, QuestType type,
                     int minLevel, int maxLevel, int raceMask, int classMask,
                     Set<Integer> prerequisiteQuestIds,
                     List<QuestStep> steps, QuestReward reward,
                     String startNpc, int startNpcId, String zoneName,
                     int recommendedLevel, boolean isRepeatable, int estimatedMinutes) {
        this.questId = questId;
        this.name = name;
        this.type = type;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.raceMask = raceMask;
        this.classMask = classMask;
        this.prerequisiteQuestIds = prerequisiteQuestIds != null ? prerequisiteQuestIds : Collections.emptySet();
        this.steps = steps != null ? steps : Collections.emptyList();
        this.reward = reward;
        this.startNpc = startNpc;
        this.startNpcId = startNpcId;
        this.zoneName = zoneName;
        this.recommendedLevel = recommendedLevel;
        this.isRepeatable = isRepeatable;
        this.estimatedMinutes = estimatedMinutes;
    }

    /**
     * Check if this quest is available to a player of given level/race/class.
     */
    public boolean isAvailable(int level, int raceId, int classId, Set<Integer> completedQuests) {
        if (level < minLevel || level > maxLevel) return false;
        if (raceMask != 0 && (raceMask & (1 << (raceId - 1))) == 0) return false;
        if (classMask != 0 && (classMask & (1 << classId)) == 0) return false;
        if (!prerequisiteQuestIds.isEmpty() && !completedQuests.containsAll(prerequisiteQuestIds)) return false;
        return true;
    }

    /**
     * XP efficiency: XP reward per estimated minute.
     */
    public double xpPerMinute() {
        if (estimatedMinutes <= 0) return 0;
        return reward.xp / (double) estimatedMinutes;
    }

    @Override
    public String toString() {
        return String.format("Quest[%d: %s, Lv%d-%d, %s, %d steps, ~%dmin]",
            questId, name, minLevel, maxLevel, type, steps.size(), estimatedMinutes);
    }
}
