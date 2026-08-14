package com.aiplayer.phase0.play;

/** MODE: COMPLETE. Pure goal planner: turns the active quest journal (real PacketLogger data) +
 *  player position + level into ONE deliberate move/action per tick. No IO, no packets —
 *  it returns a {@link GoalDecision} the fleet loop executes through already-proven primitives. */
import java.util.Collections;
import java.util.List;

import com.aiplayer.phase0.quest.QuestDatabase;
import com.aiplayer.phase0.quest.QuestInfo;
import com.aiplayer.phase0.quest.QuestInfo.QuestStep;

public final class QuestGoalPlanner
{
    /** Race param used when hunting for an acquirable quest (1 = Human). */
    private static final int RACE_HUMAN = 1;
    /** Class param 0 = any class. */
    private static final int CLASS_ANY = 0;

    private QuestGoalPlanner()
    {
    }

    /**
     * Decide the next playable move from the {questId, state} journal (the real QUEST_LIST parse),
     * using the {@code stepIndex}-th step of the first playable active quest.
     *
     * @param playerLevel the bot's real level (PacketLogger.getLevel)
     * @param activeJournal list of {questId, state} int-pairs, non-null (may be empty)
     * @param stepIndex    0-based step to play; clamped to the quest's actual step count.
     *                     Defaults to 0 (freshly accepted quest → TALK to the giver) when the
     *                     caller has no richer progress signal.
     * @return a MOVE_TO decision (QUEST goal for npc steps, FARM goal for kill/collect steps),
     *         an ACQUIRE decision when no playable quest is active, or {@code null} when neither
     *         a quest nor any acquirable quest exists (caller falls back to plain farming).
     */
    public static GoalDecision decide(int playerLevel, List<int[]> activeJournal,
                                      int playerX, int playerY, int playerZ, int stepIndex)
    {
        if (activeJournal != null)
        {
            for (int[] entry : activeJournal)
            {
                if (entry == null || entry.length < 1)
                {
                    continue;
                }
                GoalDecision d = decideActiveQuest(entry[0], stepIndex);
                if (d != null)
                {
                    return d;
                }
            }
        }

        // No playable active quest -> go acquire the level-appropriate one.
        return pickQuestToAcquire(playerLevel, playerX, playerY, playerZ);
    }

    /** Convenience: step 0 (fresh quest -> TALK to the giver). */
    public static GoalDecision decide(int playerLevel, List<int[]> activeJournal,
                                      int playerX, int playerY, int playerZ)
    {
        return decide(playerLevel, activeJournal, playerX, playerY, playerZ, 0);
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private static GoalDecision decideActiveQuest(int questId, int stepIndex)
    {
        QuestInfo q = QuestDatabase.getById(questId);
        if (q == null || q.steps.isEmpty())
        {
            return null; // unknown / empty quest -> not playable here, try next
        }

        int idx = Math.max(0, Math.min(stepIndex, q.steps.size() - 1));
        QuestStep s = q.steps.get(idx);

        switch (s.stepType)
        {
            case TALK:
            case NAVIGATE:
            case RETURN:
                // Walk to the quest NPC landmark (step coords = the giver/turn-in NPC).
                return GoalDecision.questMove(
                    PlayerGoal.QUEST, s.zoneX, s.zoneY, s.zoneZ, s.targetId,
                    "quest:" + q.name + " " + truncated(s.stepDesc),
                    "quest " + q.name + " step " + (idx + 1) + ": " + truncated(s.stepDesc));

            case KILL:
            case COLLECT:
            case COMBAT:
                // Objectives live in the step's farm zone; the step carries the mob/item to target.
                return GoalDecision.questMove(
                    PlayerGoal.FARM, s.zoneX, s.zoneY, s.zoneZ, s.targetId,
                    "farm:" + zone(s) + " " + truncated(q.name),
                    "quest " + q.name + " objectives: " + truncated(s.stepDesc));

            case USE_ITEM:
            case WAIT:
            default:
                // No clear destination; walk toward the step's zone so the bot keeps pushing the quest.
                return GoalDecision.questMove(
                    PlayerGoal.QUEST, s.zoneX, s.zoneY, s.zoneZ, s.targetId,
                    "quest:" + q.name + " " + truncated(s.stepDesc),
                    "quest " + q.name + " step " + (idx + 1) + " (" + s.stepType + "): " + truncated(s.stepDesc));
        }
    }

    private static GoalDecision pickQuestToAcquire(int playerLevel, int playerX, int playerY, int playerZ)
    {
        List<QuestInfo> available =
            QuestDatabase.findAvailable(playerLevel, RACE_HUMAN, CLASS_ANY, Collections.emptySet());
        for (QuestInfo q : available)
        {
            if (q.steps.isEmpty())
            {
                continue;
            }
            QuestStep s = q.steps.get(0);
            int cx = s.zoneX != 0 ? s.zoneX : playerX;
            int cy = s.zoneY != 0 ? s.zoneY : playerY;
            int cz = s.zoneZ;
            return GoalDecision.moveTo(
                PlayerGoal.ACQUIRE, cx, cy, cz,
                "acquire:" + q.name,
                "no active quest; go get " + q.name + " from " + q.startNpc);
        }
        return null; // nothing to acquire -> caller falls through to plain farming
    }

    private static String zone(QuestStep s)
    {
        return (s.zoneName == null || s.zoneName.isEmpty()) ? "zone" : s.zoneName;
    }

    private static String truncated(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.length() <= 48 ? s : s.substring(0, 48) + "...";
    }
}