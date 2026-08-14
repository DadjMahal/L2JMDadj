package com.aiplayer.phase0.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * QuestGoalPlanner (STEP 1) tests — the pure planner must turn the real quest journal
 * ({questId, state} from the QUEST_LIST parse) into ONE deliberate move per tick:
 *  - TALK/RETURN steps -> QUEST move toward the NPC landmark (startNpc coords from the QuestStep),
 *  - KILL/COLLECT/COMBAT steps -> FARM move into the step's zone, carrying step.targetId,
 *  - no playable active quest -> ACQUIRE move toward the level-appropriate quest giver,
 *  - nothing available at all -> null (caller falls back to plain farming).
 * Mirrors QuestNpcNavigatorTest conventions (JUnit 5, registry quest 40001 "Spider Silk Collection").
 */
class QuestGoalPlannerTest
{
    /** Quest 40001 "Spider Silk Collection": step0 TALK, step1 COLLECT, step2 RETURN. */
    private static final int QUEST_ID = 40001;
    // Real registry coords for 40001 (QuestDatabase): giver at Gludio, farm at spider fields.
    private static final int NPC_X = -14440;
    private static final int NPC_Y = 121064;
    private static final int NPC_Z = -3000;
    private static final int FARM_X = -60000;
    private static final int FARM_Y = 140000;
    private static final int FARM_Z = -3000;

    private static List<int[]> journal(int... questIdState)
    {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < questIdState.length; i += 2)
        {
            l.add(new int[] { questIdState[i], questIdState[i + 1] });
        }
        return l;
    }

    // ================================================================
    // ACTIVE QUEST STEPS
    // ================================================================

    @Test
    void talkStepWalksToQuestGiver()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, journal(QUEST_ID, 1), 0, 0, 0, 0);
        assertNotNull(d, "pickable TALK step yields a move");
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.QUEST, d.goal);
        assertEquals(NPC_X, d.targetX);
        assertEquals(NPC_Y, d.targetY);
        assertEquals(NPC_Z, d.targetZ);
        assertEquals(30002, d.questTargetId, "quest target = the quest NPC (Trader)");
        assertTrue(d.label.startsWith("quest:Spider Silk Collection"), "label names the quest, got " + d.label);
    }

    @Test
    void returnStepWalksToTurnInNpc()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, journal(QUEST_ID, 3), 0, 0, 0, 2);
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.QUEST, d.goal);
        assertEquals(NPC_X, d.targetX, "turn-in goes back to the giver NPC");
        assertEquals(NPC_Y, d.targetY);
    }

    @Test
    void collectStepFarmsTheZoneCarryingQuestTarget()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, journal(QUEST_ID, 2), 0, 0, 0, 1);
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.FARM, d.goal, "kill/collect is a FARM decision");
        assertEquals(FARM_X, d.targetX);
        assertEquals(FARM_Y, d.targetY);
        assertEquals(FARM_Z, d.targetZ);
        assertEquals(20140, d.questTargetId, "quest target = the spider mob to farm");
        assertTrue(d.label.startsWith("farm:Gludio"), "label names the farm zone, got " + d.label);
    }

    @Test
    void outOfRangeStepClampsToLastReturnStep()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, journal(QUEST_ID, 1), 0, 0, 0, 99);
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.QUEST, d.goal, "stepIndex out of range clamps to last (RETURN) step");
        assertEquals(NPC_X, d.targetX);
    }

    @Test
    void unknownQuestIsSkippedAndFallsThrough()
    {
        // Bogus quest id first in the journal -> skipped; a playable one after it wins.
        GoalDecision d = QuestGoalPlanner.decide(10,
            journal(999_999, 1, QUEST_ID, 1), 0, 0, 0, 0);
        assertNotNull(d);
        assertEquals(PlayerGoal.QUEST, d.goal, "unknown quest skipped, real one drives the move");
        assertTrue(d.label.contains("Spider Silk"));
    }

    // ================================================================
    // ACQUIRE / NO-QUEST
    // ================================================================

    @Test
    void emptyJournalFallsThroughToAcquire()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, new ArrayList<>(), 0, 0, 0);
        assertNotNull(d, "empty journal falls through to ACQUIRE (level 10 has quests)");
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.MOVE_TO, d.action);
    }

    @Test
    void noActiveQuestAcquiresLevelAppropriateQuest()
    {
        // level 10 (Human, any class): 40001 (5-15) is available -> ACQUIRE toward its giver.
        GoalDecision d = QuestGoalPlanner.decide(10, journal(), 0, 0, 0);
        assertNotNull(d);
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertTrue(d.label.startsWith("acquire:"), "label marks it as a quest to pick up, got " + d.label);
        assertTrue(d.targetX != 0 || d.targetY != 0, "acquire has a real destination");
    }

    @Test
    void nullJournalIsTreatedAsEmpty()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, null, 0, 0, 0);
        assertNotNull(d);
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
    }

    @Test
    void noAvailableQuestAnywhereReturnsNull()
    {
        // level 900 exceeds every quest's maxLevel -> nothing to acquire -> null (plain farm).
        assertNull(QuestGoalPlanner.decide(900, journal(), 0, 0, 0),
            "no acquirable quest -> null so the controller falls back to farming");
    }

    // ================================================================
    // VALUE OBJECTS
    // ================================================================

    @Test
    void goalDecisionFactoriesPopulateCorrectly()
    {
        GoalDecision mv = GoalDecision.moveTo(PlayerGoal.FARM, 1, 2, 3, "farm:x", "r");
        assertEquals(GoalAction.MOVE_TO, mv.action);
        assertEquals(1, mv.targetX);
        assertEquals(2, mv.targetY);
        assertEquals(3, mv.targetZ);
        assertEquals("farm:x", mv.label);

        GoalDecision ct = GoalDecision.combatTarget(PlayerGoal.FARM, 42, "hit:42", "r");
        assertEquals(GoalAction.COMBAT_TARGET, ct.action);
        assertEquals(42, ct.targetObjId);

        GoalDecision by = GoalDecision.bypass(PlayerGoal.QUEST, "Quest 40001 accept", "accept", "r");
        assertEquals(GoalAction.BYPASS, by.action);
        assertEquals("Quest 40001 accept", by.bypassCommand);

        GoalDecision w = GoalDecision.wait(PlayerGoal.REST, "rest", "r");
        assertEquals(GoalAction.WAIT, w.action);

        GoalDecision n = GoalDecision.none("nothing");
        assertEquals(GoalAction.NONE, n.action);
        assertEquals("idle", n.label);
    }
}
