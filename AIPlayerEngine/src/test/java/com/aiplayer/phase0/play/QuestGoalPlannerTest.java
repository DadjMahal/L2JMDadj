package com.aiplayer.phase0.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.quest.QuestDatabase;
import com.aiplayer.phase0.quest.QuestInfo;
import com.aiplayer.phase0.quest.QuestInfo.QuestStep;

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
    // 30002 "Lizardmen Hunt" giver (Sentinel, Elven Village) — the other Human quest up at level 10.
    private static final int ELVEN_GIVER_X = 46936;
    private static final int ELVEN_GIVER_Y = 51520;
    // Player spot with a real "some quests ARE available" level but NO reachable giver: > 20k from
    // every quest a Human can take at level 10 (the nearer Dwarven Village givers are race-locked).
    private static final int FAR_PLAYER_X = 100000;
    private static final int FAR_PLAYER_Y = -180000;

    private static List<int[]> journal(int... questIdState)
    {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < questIdState.length; i += 2)
        {
            l.add(new int[] { questIdState[i], questIdState[i + 1] });
        }
        return l;
    }

    /** Euclidean distance used to double-check the planner's reachability gate in assertions. */
    private static double dist(int ax, int ay, int az, int bx, int by, int bz)
    {
        long dx = (long) ax - bx;
        long dy = (long) ay - by;
        long dz = (long) az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
        // Player stands on the Gludio giver so 40001 (5-15) is reachable (from world origin every
        // giver is > 20k away and the gate would correctly return null instead of a doomed route).
        GoalDecision d = QuestGoalPlanner.decide(10, new ArrayList<>(), NPC_X, NPC_Y, NPC_Z);
        assertNotNull(d, "empty journal falls through to ACQUIRE (level 10 has quests)");
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.MOVE_TO, d.action);
    }

    @Test
    void noActiveQuestAcquiresLevelAppropriateQuest()
    {
        // level 10 (Human, any class): 40001 (5-15) is available and its giver is right here ->
        // ACQUIRE toward the local reachable giver, not the far-away one.
        GoalDecision d = QuestGoalPlanner.decide(10, journal(), NPC_X, NPC_Y, NPC_Z);
        assertNotNull(d);
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertTrue(d.label.startsWith("acquire:"), "label marks it as a quest to pick up, got " + d.label);
        assertEquals(NPC_X, d.targetX, "nearest reachable giver drives the destination");
        assertEquals(NPC_Y, d.targetY);
    }

    @Test
    void nullJournalIsTreatedAsEmpty()
    {
        GoalDecision d = QuestGoalPlanner.decide(10, null, NPC_X, NPC_Y, NPC_Z);
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
    // ACQUIRE REACHABILITY GATE (MAX_ACQUIRE_DIST)
    // ================================================================

    @Test
    void unreachableQuestGiverSkippedReturnsNull()
    {
        // Talking Island-style spot far from every giver a Human can take at level 10: quests 40001
        // (Gludio) and 30002 (Elven Village) ARE available but sit 200k+ units away (the nearer
        // Dwarven Village givers are RACE_DWARF-locked, so not candidates). No reachable giver ->
        // null so the caller falls back to plain farming instead of re-looping the same doomed route.
        assertNull(QuestGoalPlanner.decide(10, journal(), FAR_PLAYER_X, FAR_PLAYER_Y, -3000),
            "all available quest givers farther than " + QuestGoalPlanner.MAX_ACQUIRE_DIST
                + " -> null (plain farm)");
    }

    @Test
    void reachableQuestGiverPickedWhenAvailable()
    {
        // Player stands right on the 40001 giver (Trader, Gludio) -> the gate lets it through.
        GoalDecision d = QuestGoalPlanner.decide(10, journal(), NPC_X, NPC_Y, NPC_Z);
        assertNotNull(d, "a quest giver within reach yields an ACQUIRE decision");
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.MOVE_TO, d.action);
        double toGiver = dist(NPC_X, NPC_Y, NPC_Z, d.targetX, d.targetY, d.targetZ);
        assertTrue(toGiver <= QuestGoalPlanner.MAX_ACQUIRE_DIST,
            "best target within MAX_ACQUIRE_DIST of the player, was " + toGiver);
        assertTrue(d.label.startsWith("acquire:Spider Silk Collection"),
            "reachable local quest is the one picked, got " + d.label);
    }

    @Test
    void nearestReachableQuestChosenWhenSeveral()
    {
        // Level 10 (Human) offers exactly two quests: 40001 "Spider Silk" (giver Gludio, rec 10) and
        // 30002 "Lizardmen Hunt" (giver Elven Village, rec 12). Their givers are ~93k units apart, so
        // no single position is within 20k of BOTH — this asserts both halves of the pick: the far
        // quest is gated out and, among the reachable ones, the NEAREST giver wins (not the highest
        // recommendedLevel, which would be the findAvailable sort-first behavior).
        GoalDecision atGludio = QuestGoalPlanner.decide(10, journal(), NPC_X, NPC_Y, NPC_Z);
        assertNotNull(atGludio);
        assertEquals(PlayerGoal.ACQUIRE, atGludio.goal);
        assertEquals(NPC_X, atGludio.targetX, "player at Gludio picks the local giver (40001)");
        assertEquals(NPC_Y, atGludio.targetY);
        assertTrue(atGludio.label.contains("Spider Silk Collection"), "got " + atGludio.label);

        GoalDecision atElven = QuestGoalPlanner.decide(10, journal(), ELVEN_GIVER_X, ELVEN_GIVER_Y, NPC_Z);
        assertNotNull(atElven);
        assertEquals(PlayerGoal.ACQUIRE, atElven.goal);
        assertEquals(ELVEN_GIVER_X, atElven.targetX,
            "player at Elven Village picks the local giver (30002), not the rec-10 40001");
        assertEquals(ELVEN_GIVER_Y, atElven.targetY);
        assertTrue(atElven.label.contains("Lizardmen Hunt"), "got " + atElven.label);
    }

    @Test
    void sameDistanceGiverTieBreaksByRecommendedLevel()
    {
        // Level 35 (Human): many quests share the single Gludio giver — 40004 "Undead Ash" (rec 42)
        // must win the distance tie over 30007 (rec 38) and the rec-37 class-change trials.
        GoalDecision d = QuestGoalPlanner.decide(35, journal(), NPC_X, NPC_Y, NPC_Z);
        assertNotNull(d);
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(NPC_X, d.targetX);
        assertEquals(NPC_Y, d.targetY);
        assertTrue(d.label.startsWith("acquire:Undead Ash Collection"),
            "equal-distance givers tie-break by higher recommendedLevel, got " + d.label);
    }

    // ================================================================
    // REWARD-AWARE + DIVERSE ACQUIRE (seed overload)
    // ================================================================

    @Test
    void diversePathRewardAwarePicksMaxAdenaAmongReachable()
    {
        // Player at the Gludio giver (level 35): the diverse path (any seed) must pick a REACHABLE
        // quest whose reward adena equals the max across all reachable givers from that spot.
        int maxReachable = Integer.MIN_VALUE;
        for (QuestInfo q : QuestDatabase.findAvailable(35, 1, 0, Collections.emptySet()))
        {
            if (q == null || q.steps.isEmpty() || q.reward == null)
            {
                continue;
            }
            QuestStep s = q.steps.get(0);
            int cx = s.zoneX != 0 ? s.zoneX : NPC_X;
            int cy = s.zoneY != 0 ? s.zoneY : NPC_Y;
            int cz = s.zoneZ;
            if (dist(NPC_X, NPC_Y, NPC_Z, cx, cy, cz) <= 20000)
            {
                maxReachable = Math.max(maxReachable, (int) q.reward.adena);
            }
        }

        GoalDecision d = QuestGoalPlanner.decide(35, journal(), NPC_X, NPC_Y, NPC_Z, 0, 7);
        assertNotNull(d);
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(NPC_X, d.targetX);
        assertEquals(NPC_Y, d.targetY);

        // Recover the picked quest by name from the label and check its adena is the reachable max.
        String pickedName = d.label.startsWith("acquire:")
            ? d.label.substring("acquire:".length()) : "";
        long pickedAdena = -1;
        for (QuestInfo q : QuestDatabase.findAvailable(35, 1, 0, Collections.emptySet()))
        {
            if (q != null && q.name.equals(pickedName) && q.reward != null)
            {
                pickedAdena = q.reward.adena;
            }
        }
        assertEquals(maxReachable, (int) pickedAdena,
            "diverse path must pick a max-adena reachable quest, got " + d.label);
    }

    @Test
    void diversePathDeterministicForSameSeed()
    {
        GoalDecision a = QuestGoalPlanner.decide(35, journal(), NPC_X, NPC_Y, NPC_Z, 0, 11);
        GoalDecision b = QuestGoalPlanner.decide(35, journal(), NPC_X, NPC_Y, NPC_Z, 0, 11);
        assertNotNull(a);
        assertEquals(a.label, b.label, "same seed must be deterministic");
        assertEquals(a.targetX, b.targetX);
        assertEquals(a.targetY, b.targetY);
    }

    @Test
    void diversePathFallsBackToNullWhenNothingReachable()
    {
        // From FAR_PLAYER (level 10, no giver within 20k) the diverse path must fall back to null.
        assertNull(QuestGoalPlanner.decide(10, journal(), FAR_PLAYER_X, FAR_PLAYER_Y, NPC_Z, 0, 3));
    }

    @Test
    void zeroSeedDelegatesToClassicNearest()
    {
        // Seed 0 must behave exactly like the classic (no-seed) nearest-giver path at level 10.
        GoalDecision seed0 = QuestGoalPlanner.decide(10, journal(), NPC_X, NPC_Y, NPC_Z, 0, 0);
        GoalDecision classic = QuestGoalPlanner.decide(10, journal(), NPC_X, NPC_Y, NPC_Z, 0);
        assertNotNull(seed0);
        assertEquals(classic.targetX, seed0.targetX);
        assertEquals(classic.targetY, seed0.targetY);
        assertEquals(classic.label, seed0.label);
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
