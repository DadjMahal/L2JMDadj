package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.knowledge.PlayerRace;
import com.aiplayer.knowledge.QuestNode;
import com.aiplayer.knowledge.RaceGuide;
import com.aiplayer.behavior.BotPlayController.BotPlayConfig;
import com.aiplayer.behavior.BotPlayController.Chase;
import com.aiplayer.behavior.BotPlayController.Hostile;
import com.aiplayer.behavior.BotPlayController.PlayContext;
import com.aiplayer.behavior.movement.RelocationPlanner.Target;
import com.aiplayer.protocol.PacketLogger;

/**
 * BotPlayController (STEP 1B) — the decision ladder must always pick ONE deliberate action per tick
 * and never fall through to NONE/idle. Mirrors QuestGoalPlannerTest conventions (JUnit 5, registry
 * quest 40001 "Spider Silk Collection" at Gludio). Default ladder config: survive 25%, combat 400,
 * sight 2000.
 */
class BotPlayControllerTest
{
    private static final int QUEST_ID = 40001;
    private static final int NPC_X = -86733;
    private static final int NPC_Y = 242918;

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
        return new PlayContext(level, x, y, 0, hp, hpMax, j, hostiles, 0, 0);
    }

    // ================================================================
    // SURVIVE
    // ================================================================

    @Test
    void lowHpWithHostilesNearbyRetreatsAway()
    {
        // 20/100 = 20% HP < 25% survive threshold, hostile inside sight range -> SURVIVE retreat.
        // Hostile at (0,500), player at (0,0) -> flee away is directly south: (0,-500).
        GoalDecision d = BotPlayController.decide(
            ctx(10, 0, 0, 20, 100, journal(QUEST_ID, 1),
                hostiles(new Hostile(50, 0, 500, 0))));
        assertNotNull(d);
        assertEquals(PlayerGoal.SURVIVE, d.goal, "low-HP + hostiles near -> survival goal");
        assertEquals(GoalAction.RETREAT, d.action, "survival = retreat away from danger");
        assertEquals(0, d.targetObjId, "retreat has no target objId");
        assertEquals(0, d.targetX, "retreat X is away from hostile (south): player 0 -> hostile 0 => away 0");
        assertTrue(d.targetY < 0, "retreat Y is south (negative): player 0, hostile 500 => away -500, got " + d.targetY);
        assertTrue(d.reason.contains("retreat from"), "reason mentions retreat: " + d.reason);
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
        // Player stands ON the giver (Jackson, Talking Island) so quest 40001 is reachable AND the
        // bot is within talkRange -> the controller emits BYPASS (open the NPC dialog) instead of a
        // plain MOVE_TO: a real player TALKS when standing on the giver.
        GoalDecision d = BotPlayController.decide(
            ctx(10, NPC_X, NPC_Y, 90, 100, journal(), hostiles()));
        assertNotNull(d, "no quest + no target -> go acquire");
        assertEquals(PlayerGoal.ACQUIRE, d.goal);
        assertEquals(GoalAction.BYPASS, d.action,
            "standing on the giver opens its dialog, not a raw move");
        assertTrue(d.label.startsWith("quest-dialog:"),
            "bypass label names the dialog, got " + d.label);
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
            new PlayContext(900, 0, 0, 0, 90, 100, journal(), null, 0, 0));
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

    // ================================================================
    // RESTOCK INTENT
    // ================================================================

    @Test
    void fullInventoryReturnsRestockBeforeCombat()
    {
        // Inventory 96% >= threshold 95, hostile in combat range -> REST-reason="restock" to the
        // vendor landmark (MOVE_TO, not the old WAIT).
        QuestNode anchor = RestockPlannerTest.humanAnchor(10);
        GoalDecision d = BotPlayController.decide(
            new PlayContext(10, 0, 0, 0, 90, 100, journal(),
                hostiles(new Hostile(42, 200, 0, 0)), 0, 96),
            new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 95));
        assertNotNull(d);
        assertEquals(PlayerGoal.REST, d.goal, "full inventory with hostile near -> rest over fight");
        assertEquals(GoalAction.MOVE_TO, d.action, "restock intent -> move to town vendor");
        assertEquals(anchor.x, d.targetX, "targets the vendor landmark X");
        assertEquals(anchor.y, d.targetY, "targets the vendor landmark Y");
        assertEquals(anchor.z, d.targetZ, "targets the vendor landmark Z");
        assertTrue(d.reason.contains("restock"), "reason mentions restock: " + d.reason);
        assertTrue(d.label.contains("restock"), "label mentions restock: " + d.label);
    }

    @Test
    void restockBelowThresholdLetsCombatProceed()
    {
        // Inventory 80% < threshold 95, hostile in combat range -> normal COMBAT.
        GoalDecision d = BotPlayController.decide(
            new PlayContext(10, 0, 0, 0, 90, 100, journal(),
                hostiles(new Hostile(7, 200, 0, 0)), 0, 80),
            new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 95));
        assertNotNull(d);
        assertEquals(GoalAction.COMBAT_TARGET, d.action,
            "inventory below threshold -> normal combat");
        assertEquals(7, d.targetObjId, "attacks the hostile in range");
    }

    @Test
    void restockAtThresholdMovesToVendorLandmark()
    {
        // Inventory 80% >= threshold 60 with no hostiles -> REST to the vendor landmark (MOVE_TO).
        QuestNode anchor = RestockPlannerTest.humanAnchor(20);
        GoalDecision d = BotPlayController.decide(
            new PlayContext(20, 0, 0, 0, 90, 100, journal(), hostiles(), 0, 80),
            new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 60));
        assertNotNull(d);
        assertEquals(PlayerGoal.REST, d.goal, "full inventory -> rest goal");
        assertEquals(GoalAction.MOVE_TO, d.action, "restock is a walk to the vendor, not a wait");
        assertEquals(anchor.x, d.targetX, "vendor landmark X");
        assertEquals(anchor.y, d.targetY, "vendor landmark Y");
        assertEquals(anchor.z, d.targetZ, "vendor landmark Z");
        assertTrue(d.reason.contains("restock"), "reason mentions restock: " + d.reason);
    }

    @Test
    void restockUsesConfiguredRaceLandmark()
    {
        // An ELF bot with an over-full inventory walks to the ELF race's own landmark.
        QuestNode anchor = RestockPlannerTest.anchor(PlayerRace.ELF, 20);
        GoalDecision d = BotPlayController.decide(
            new PlayContext(20, 0, 0, 0, 90, 100, journal(), hostiles(), 0, 80),
            new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 60, PlayerRace.ELF));
        assertNotNull(d);
        assertEquals(GoalAction.MOVE_TO, d.action, "restock -> move to vendor");
        assertEquals(anchor.x, d.targetX, "ELF vendor landmark X");
        assertEquals(anchor.y, d.targetY, "ELF vendor landmark Y");
        assertEquals(anchor.z, d.targetZ, "ELF vendor landmark Z");
    }

    @Test
    void restockWinsOverCombatInRange()
    {
        // Inventory 80% >= threshold 60 and hostile in combat range -> still restock (walk to vendor),
        // proving restock outranks combat.
        QuestNode anchor = RestockPlannerTest.humanAnchor(20);
        GoalDecision d = BotPlayController.decide(
            new PlayContext(20, 0, 0, 0, 90, 100, journal(),
                hostiles(new Hostile(7, 200, 0, 0)), 0, 80),
            new BotPlayController.BotPlayConfig(0.25, 400, 2000, 300, 60));
        assertNotNull(d);
        assertEquals(PlayerGoal.REST, d.goal, "restock outranks combat when inventory full");
        assertEquals(GoalAction.MOVE_TO, d.action,
            "restock intent sends us to the vendor, not to the hostile");
        assertEquals(anchor.x, d.targetX, "vendor landmark X");
        assertEquals(anchor.y, d.targetY, "vendor landmark Y");
    }

    // ================================================================
    // RETREAT MATH (reuses chaseStep internally)
    // ================================================================

    @Test
    void retreatFromHostileMovesAway()
    {
        // Player at (0,0), hostile at (500,0). Retreat away: (0 + (0-500), 0 + (0-0)) = (-500, 0).
        // RETREAT_HOP is 4800, distance 500 < 4800 -> lands exactly at (-500, 0).
        // Use the same pattern as the SURVIVE ladder: flee point via chaseStep.
        int rx = 0 + (0 - 500);
        int ry = 0 + (0 - 0);
        BotPlayController.Chase flee = BotPlayController.chaseStep(0, 0, 0, rx, ry, 0, 4800);
        assertTrue(flee.x < 0, "flee away from hostile on X: hostile east -> retreat west, got " + flee.x);
        assertEquals(-500, flee.x, "within 4800 -> lands exactly at away point");
        assertEquals(0, flee.y, "Y unchanged when hostile is purely east");
    }

    @Test
    void retreatClampsAtMaxHop()
    {
        // Player at (0,0), hostile at (-10000, 0). Away = (0+(0-(-10000)), 0) = (10000, 0).
        // Distance 10000 > 4800 -> clamped to 4800 east.
        int rx = 0 + (0 - (-10000));
        int ry = 0 + (0 - 0);
        BotPlayController.Chase flee = BotPlayController.chaseStep(0, 0, 0, rx, ry, 0, 4800);
        double dist = Math.sqrt(flee.x * (double) flee.x + flee.y * (double) flee.y);
        assertTrue(dist > 4000 && dist <= 4800, "clamped retreat within RETREAT_HOP, got " + dist);
        assertTrue(flee.x > 0, "retreating east (positive X): hostile west, away east, got " + flee.x);
    }
// ================================================================
    // EB-03 — CONFIGURABLE LADDER (per-profile priorities)
    // ================================================================

    @Test
    void defaultLadderPreservesHistoricalOrder()
    {
        List<BotPlayController.Rung> ladder = BotPlayController.DEFAULT_LADDER;
        assertEquals(BotPlayController.Rung.SURVIVE, ladder.get(0), "SURVIVE stays the hard safety rung");
        assertTrue(ladder.contains(BotPlayController.Rung.COMBAT));
        assertTrue(ladder.contains(BotPlayController.Rung.HUNT));
        assertTrue(ladder.contains(BotPlayController.Rung.QUEST));
        assertEquals(6, ladder.size());
    }

    @Test
    void profileCanDropQuestRungEntirely()
    {
        // Player stands ON the quest giver (talkRange) AND a hostile is in combat range (300 < 400).
        // Default ladder: QUEST_TALK (rung 2) fires before COMBAT (rung 4) -> BYPASS the dialog.
        // Grind ladder (no quest rungs): COMBAT fires first -> FARM the mob.
        BotPlayConfig grind = new BotPlayConfig(0.25, 400, 2000, 300, 100, PlayerRace.HUMAN, 99,
            Arrays.asList(BotPlayController.Rung.SURVIVE, BotPlayController.Rung.COMBAT,
                BotPlayController.Rung.HUNT, BotPlayController.Rung.RESTOCK));
        GoalDecision d = BotPlayController.decide(
            new PlayContext(10, NPC_X, NPC_Y, 0, 90, 100, journal(),
                hostiles(new Hostile(7, NPC_X, NPC_Y + 300, 0)), 0, 0),
            grind);
        assertEquals(PlayerGoal.FARM, d.goal, "grind profile ignores quest rungs -> fights the mob");

        // Same position, default ladder: QUEST_TALK comes before COMBAT -> dialog wins.
        GoalDecision dd = BotPlayController.decide(
            new PlayContext(10, NPC_X, NPC_Y + 300, 0, 90, 100, journal(),
                hostiles(new Hostile(7, NPC_X, NPC_Y + 300, 0)), 0, 0),
            BotPlayConfig.DEFAULT);
        assertEquals(GoalAction.BYPASS, dd.action, "default ladder talks to the giver before fighting");
    }

    @Test
    void raceLaddersReorderCombatAboveQuestForOrc()
    {
        List<BotPlayController.Rung> orc = BotPlayController.BotPlayConfig.ladderForRace(PlayerRace.ORC);
        assertTrue(orc.indexOf(BotPlayController.Rung.COMBAT) < orc.indexOf(BotPlayController.Rung.QUEST),
            "ORC melee-first: COMBAT before QUEST");
        assertEquals(BotPlayController.Rung.SURVIVE, orc.get(0), "SURVIVE safety stays first");
    }

    @Test
    void elfLadderPrefersQuestTalkBeforeCombat()
    {
        List<BotPlayController.Rung> elf = BotPlayController.BotPlayConfig.ladderForRace(PlayerRace.ELF);
        assertTrue(elf.indexOf(BotPlayController.Rung.QUEST_TALK) < elf.indexOf(BotPlayController.Rung.COMBAT),
            "ELF caster-first: QUEST_TALK before COMBAT");
    }

    @Test
    void nullRaceLadderFallsBackToDefault()
    {
        assertEquals(BotPlayController.DEFAULT_LADDER, BotPlayController.BotPlayConfig.ladderForRace(null));
    }
}
