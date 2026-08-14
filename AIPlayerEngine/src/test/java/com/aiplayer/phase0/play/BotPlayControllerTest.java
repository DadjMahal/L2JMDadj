package com.aiplayer.phase0.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.play.BotPlayController.Hostile;
import com.aiplayer.phase0.play.BotPlayController.PlayContext;

/**
 * BotPlayController (STEP 1B) — the decision ladder must always pick ONE deliberate action per tick
 * and never fall through to NONE/idle. Mirrors QuestGoalPlannerTest conventions (JUnit 5, registry
 * quest 40001 "Spider Silk Collection" at Gludio). Default ladder config: survive 25%, combat 400,
 * sight 2000.
 */
class BotPlayControllerTest
{
    private static final int QUEST_ID = 40001;
    private static final int NPC_X = -14440;
    private static final int NPC_Y = 121064;

    /** Active-journal helper as {questId, state} pairs (same form as PacketLogger.getActiveQuestList). */
    private static List<int[]> journal(int... questIdState)
    {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < questIdState.length; i += 2)
        {
            l.add(new int[] { questIdState[i], questIdState[i + 1] });
        }
        return l;
    }

    private static PlayContext ctx(int level, int x, int y, int hp, int hpMax,
                                   List<int[]> j, List<Hostile> hostiles)
    {
        return new PlayContext(level, x, y, 0, hp, hpMax, j, hostiles, 0);
    }

    // ================================================================
    // SURVIVE
    // ================================================================

    @Test
    void lowHpWithHostilesNearbyStopsAndHolds()
    {
        // 20/100 = 20% HP < 25% survive threshold, hostile inside sight range -> SURVIVE hold.
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 20, 100, journal(QUEST_ID, 1),
                hostiles(new Hostile(50, 0, 500, 0))));
        assertNotNull(d);
        assertEquals(PlayerGoal.SURVIVE, d.goal, "low-HP + hostiles near -> survival goal");
        assertEquals(GoalAction.WAIT, d.action, "survival = hold the beat to disengage");
        assertNotEquals(GoalAction.NONE, d.action);
    }

    @Test
    void lowHpWithoutHostilesKeepsQuesting()
    {
        // Low HP but nothing hostile nearby: not a dangerous spot -> do NOT drop out of the quest.
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 20, 100, journal(QUEST_ID, 1), hostiles()));
        assertNotNull(d);
        assertNotEquals(PlayerGoal.SURVIVE, d.goal, "no hostiles near -> keep playing, not survive");
        assertEquals(GoalAction.MOVE_TO, d.action);
    }

    // ================================================================
    // COMBAT / HUNT
    // ================================================================

    @Test
    void hostileInCombatRangeIsAttacked()
    {
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(), hostiles(new Hostile(42, 200, 0, 0))));
        assertNotNull(d);
        assertEquals(GoalAction.COMBAT_TARGET, d.action);
        assertEquals(PlayerGoal.FARM, d.goal, "fighting is a FARM goal");
        assertEquals(42, d.targetObjId, "attacks the hostile in range");
    }

    @Test
    void nearestHostileIsChosenFromSeveral()
    {
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(),
                hostiles(new Hostile(1, 5000, 5000, 0), new Hostile(2, 100, 0, 0))));
        assertNotNull(d);
        assertEquals(GoalAction.COMBAT_TARGET, d.action);
        assertEquals(2, d.targetObjId, "the closer hostile (100) wins over 5000");
    }

    @Test
    void hostileInSightButOutOfRangeTriggersHuntAdvance()
    {
        // 1000 < sight 2000 but > combat 400 -> walk to it instead of attacking point-blank.
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(), hostiles(new Hostile(7, 1000, 0, 0))));
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.FARM, d.goal);
        assertEquals(1000, d.targetX, "hunt-advance walks toward the visible hostile");
        assertTrue(d.label.startsWith("hunt:"), "marked as hunt-advance, got " + d.label);
    }

    @Test
    void hostileBeyondSightRangeDoesNotTriggerHunt()
    {
        // 5000 > sight 2000: too far to be worth walking to yet -> quest decision drives.
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(QUEST_ID, 1),
                hostiles(new Hostile(9, 5000, 0, 0))));
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.QUEST, d.goal, "far hostile ignored; quest drives");
    }

    // ================================================================
    // QUEST / ACQUIRE / REST
    // ================================================================

    @Test
    void activeQuestMovesToGiver()
    {
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(QUEST_ID, 1), hostiles()));
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action);
        assertEquals(PlayerGoal.QUEST, d.goal);
        assertEquals(NPC_X, d.targetX);
        assertEquals(NPC_Y, d.targetY);
        assertEquals(30002, d.questTargetId);
    }

    @Test
    void noActiveQuestAcquiresLevelAppropriateQuest()
    {
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(), hostiles()));
        assertNotNull(d, "no quest + no target -> go acquire");
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.MOVE_TO, d.action);
    }

    @Test
    void nothingToDoAnywhereFallsBackToDeliberateRest()
    {
        // level 900 exceeds every quest's maxLevel (QuestGoalPlanner returns null) and no hostiles.
        GoalDecision d = BotPlayController.decide(
            ctx(900, 0, 0, 90, 100, journal(), hostiles()));
        assertNotNull(d);
        assertEquals(PlayerGoal.REST, d.goal, "fallback is a deliberate hold, a real goal");
        assertEquals(GoalAction.WAIT, d.action);
        assertNotEquals(GoalAction.NONE, d.action, "never idle");
    }

    // ================================================================
    // NEVER IDLE
    // ================================================================

    @Test
    void nullContextAndNullHostilesStillProduceANonNoneDecision()
    {
        GoalDecision nullCtx = BotPlayController.decide(null);
        assertNotNull(nullCtx);
        assertNotEquals(GoalAction.NONE, nullCtx.action);

        GoalDecision nullHostiles = BotPlayController.decide(
            new PlayContext(900, 0, 0, 0, 90, 100, journal(), null, 0));
        assertNotNull(nullHostiles);
        assertNotEquals(GoalAction.NONE, nullHostiles.action);
    }

    @Test
    void configurationKnobsAreRespected()
    {
        // combatRange 0 -> NO hostile is ever "in range", so a nearby one only triggers hunt-advance.
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 90, 100, journal(), hostiles(new Hostile(3, 200, 0, 0))),
            new BotPlayController.BotPlayConfig(0.25, 0, 2000));
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action, "combatRange 0 -> no combat, hunt-advance instead");
        assertEquals(PlayerGoal.FARM, d.goal);

        // surviveHpFraction 0 -> survival gate disabled even at 5% HP with hostiles around.
        GoalDecision d2 = BotPlayController.decide(
            ctx(10, 0, 0, 5, 100, journal(), hostiles(new Hostile(4, 200, 0, 0))),
            new BotPlayController.BotPlayConfig(0.0, 400, 2000));
        assertNotNull(d2);
        assertEquals(GoalAction.COMBAT_TARGET, d2.action, "survive gate off -> fights at 5% HP");
    }

    private static List<Hostile> hostiles(Hostile... hs)
    {
        return new ArrayList<>(Arrays.asList(hs));
    }

    private static double dist(BotPlayController.Chase c, int fx, int fy, int fz)
    {
        double dx = c.x - fx;
        double dy = c.y - fy;
        double dz = c.z - fz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ================================================================
    // STEP 3 gap-close: chaseStep clamps a single move-hop toward the target
    // so a bot can close distance to an out-of-melee hostile. Pure math.
    // ================================================================

    @Test
    void chaseStepReturnsTargetWhenWithinHop()
    {
        BotPlayController.Chase c = BotPlayController.chaseStep(0, 0, 0, 2000, 3000, 0, 4800);
        assertTrue(dist(c, 0, 0, 0) <= 4800, "hop never exceeds the configured cap");
        assertEquals(2000, c.x, "within one hop -> land exactly on target x");
        assertEquals(3000, c.y, "within one hop -> land exactly on target y");
    }

    @Test
    void chaseStepClampsLengthForFarTarget()
    {
        // Target far beyond the ~9900u server single-move cap: the step must be clamped
        // to maxHop=4800 along the same direction, and still point at the target.
        BotPlayController.Chase c = BotPlayController.chaseStep(0, 0, 0, 12000, 16000, 0, 4800);
        double d = dist(c, 0, 0, 0);
        assertTrue(d > 4000 && d <= 4800, "clamped step lands inside the hop window, got " + d);
        // Direction preserved: y/x ratio from an origin-step equals the target ratio (3:4).
        assertEquals(4.0 / 3.0, (double) c.y / c.x, 0.02, "clamping preserves direction toward target");
    }

    @Test
    void chaseStepZeroDistanceStaysPut()
    {
        BotPlayController.Chase c = BotPlayController.chaseStep(5, 6, 7, 5, 6, 7, 4800);
        assertEquals(5, c.x, "no-op hop when already at target");
        assertEquals(6, c.y, "no-op hop when already at target");
        assertEquals(7, c.z, "no-op hop when already at target");
    }
}