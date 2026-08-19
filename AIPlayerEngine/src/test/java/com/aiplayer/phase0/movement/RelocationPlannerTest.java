package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.guide.PlayerRace;
import com.aiplayer.phase0.guide.QuestNode;
import com.aiplayer.phase0.guide.RaceGuide;
import com.aiplayer.phase0.movement.RelocationPlanner.Target;

/**
 * STEP 6 unit tests: idle-relocation chooses last-XP / nearest-mate when frozen, a real guide-map
 * landmark otherwise, and the consecutive-abandon escape gate holds the bot to break the frozen
 * far-travel loop. All destinations must be REAL in-world points (never the void (16600,17000,434)).
 */
class RelocationPlannerTest
{
    private static final int VOID_X = 16600;
    private static final int VOID_Y = 17000;

    private static boolean isVoid(int x, int y)
    {
        return x == VOID_X && y == VOID_Y;
    }

    @Test
    void startsUnfrozenAndNoEscapeHold()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_01");
        assertFalse(p.escapeHoldActive(), "fresh planner must not be holding");
        assertFalse(p.isFrozen(), "fresh planner must not be frozen");
        assertFalse(p.hasLastXp(), "fresh planner has no XP memory");
    }

    @Test
    void recordingXpRemembersPosition()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_01");
        p.recordLastXp(-82759, 250149, -3600);
        assertTrue(p.hasLastXp());
        assertEquals(-82759, p.lastXpX());
        assertEquals(250149, p.lastXpY());
        assertEquals(-3600, p.lastXpZ());
    }

    @Test
    void oneAbandonMarksFrozenButNotEscaped()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_01");
        p.noteAbandonedRoute();
        assertTrue(p.isFrozen(), "one abandon = frozen (zero server movement)");
        assertFalse(p.escapeHoldActive(), "first abandon must not trip the escape gate yet");
    }

    @Test
    void consecutiveAbandonsTripEscapeGateAndProgressClearsIt()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_01");
        for (int i = 0; i < RelocationPlanner.MAX_CONSECUTIVE_ABANDONS; i++)
        {
            p.noteAbandonedRoute();
        }
        assertTrue(p.escapeHoldActive(), "MAX abandons must trip the escape gate");
        // Forward progress (server moved us / XP earned) clears the freeze + hold.
        p.noteProgress();
        assertFalse(p.escapeHoldActive(), "progress must lift the escape hold");
        assertFalse(p.isFrozen(), "progress must clear the freeze counter");
        assertEquals(0, p.consecutiveAbandons());
    }

    @Test
    void frozenWithLastXpRoutesBackToXpSpot()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_02");
        p.recordLastXp(2000, 2000, -3000);
        p.noteAbandonedRoute(); // freeze
        Target t = p.choose(20, 1000, 1000, -3000, p.isFrozen(),
            nothing(), null, 900, 30000);
        assertNotNull(t, "frozen bot with a last-XP spot must route back to it");
        assertEquals("reloc:lastxp", t.label);
        assertEquals(2000, t.x);
        assertEquals(2000, t.y);
        assertEquals(-3000, t.z);
        assertFalse(isVoid(t.x, t.y), "last-XP route must not be the void spot");
    }

    @Test
    void frozenWithoutLastXpRoutesToNearestMate()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_03");
        p.noteAbandonedRoute(); // freeze, but never earned XP this session
        List<int[]> mates = new ArrayList<>();
        mates.add(new int[] { 0, 0, 0 });          // not in-world -> skipped
        mates.add(new int[] { 4000, 1000, -3000 }); // nearest valid mate
        mates.add(new int[] { 9000, 1000, -3000 }); // farther mate
        Target t = p.choose(20, 1000, 1000, -3000, p.isFrozen(),
            mates, null, 900, 30000);
        assertNotNull(t, "frozen bot must route to a fleet mate when it has no last-XP spot");
        assertEquals("reloc:mate", t.label);
        assertEquals(4000, t.x);
        assertEquals(1000, t.y);
        assertFalse(isVoid(t.x, t.y));
    }

    @Test
    void escapeGateHoldsTheBotStill()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_04");
        for (int i = 0; i < RelocationPlanner.MAX_CONSECUTIVE_ABANDONS; i++)
        {
            p.noteAbandonedRoute();
        }
        assertTrue(p.escapeHoldActive());
        Target t = p.choose(20, 1000, 1000, -3000, true,
            nothing(), PlayerRace.HUMAN, 900, 30000);
        assertNull(t, "escape gate must refuse to issue another far relocation");
    }

    @Test
    void idlePrefersRealGuideLandmarkNotVoid()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_05");
        Target t = p.choose(20, 1000, 1000, -3000, false,
            nothing(), PlayerRace.HUMAN, 900, 1_000_000);
        assertNotNull(t, "idle bot must get a real relocation target");
        assertFalse(isVoid(t.x, t.y), "guide-map / far-point target must never be the void spot");
        assertTrue(t.label.startsWith("reloc:"), "unexpected relocation label: " + t.label);
    }

    @Test
    void idleWithoutRaceStillGetsRealNonVoidTarget()
    {
        RelocationPlanner p = new RelocationPlanner("ai_combat_06");
        Target t = p.choose(20, 1000, 1000, -3000, false,
            nothing(), null, 900, 30000);
        assertNotNull(t, "idle bot with unknown race still relocates");
        assertFalse(isVoid(t.x, t.y), "fallback target must not be the void spot");
        assertTrue(t.reason != null && !t.reason.isEmpty());
    }

    @Test
    void escapeGateDoesNotFireOnAHealthyFrozenRecovery()
    {
        // Reaching a hop (progress) between abandons must keep the counter from tripping.
        RelocationPlanner p = new RelocationPlanner("ai_combat_07");
        p.noteAbandonedRoute();
        p.noteProgress();
        p.noteAbandonedRoute();
        p.noteProgress();
        assertFalse(p.escapeHoldActive(), "healthy recoveries must stop the gate tripping");
        assertFalse(p.isFrozen());
    }

    @Test
    void anchorTooCloseReturnsNullInsteadOfReplanningSameLandmark()
    {
        // Proximity gate: the bot sits effectively AT the guide landmark (inside the min relocation
        // distance), so choose() must NOT be re-sent to that same landmark. choose() may still return
        // a bounded far-point (the designed last resort) — the gate's contract is "no reuse of the
        // occupied landmark", not "always null".
        RelocationPlanner p = new RelocationPlanner("ai_combat_08");
        QuestNode a = RaceGuide.idleAnchor(PlayerRace.HUMAN, 20);
        Target t = p.choose(20, a.x, a.y, a.z, false,
            nothing(), PlayerRace.HUMAN, 900, 30000);
        assertTrue(t == null || !t.reason.contains("guide landmark"),
            "must not re-hop to the same guide landmark; got " + (t == null ? "null" : t.reason));
    }

    @Test
    void anchorWithinReachStillChosenWhenNotFrozen()
    {
        // Bot parked a guaranteed in-reach offset from the landmark (distance between min and
        // max), so the real guide landmark is still preferred over any random point.
        RelocationPlanner p = new RelocationPlanner("ai_combat_09");
        QuestNode a = RaceGuide.idleAnchor(PlayerRace.HUMAN, 20);
        Target t = p.choose(20, a.x + 20000, a.y, a.z, false,
            nothing(), PlayerRace.HUMAN, 900, 30000);
        assertNotNull(t, "landmark within reach must still be chosen when not frozen");
        assertTrue(t.label.startsWith("reloc:"), "unexpected relocation label: " + t.label);
    }

    @Test
    void doesNotReHopToSameLandmark_antiOscillation()
    {
        // S5-T08: at the same spot again, prefer a REAL landmark but never re-hop the SAME one.
        RelocationPlanner p = new RelocationPlanner("ai_combat_10");
        QuestNode a = RaceGuide.idleAnchor(PlayerRace.HUMAN, 20);
        Target first = p.choose(20, a.x + 20000, a.y, a.z, false,
            nothing(), PlayerRace.HUMAN, 900, 30000);
        Target second = p.choose(20, a.x + 20000, a.y, a.z, false,
            nothing(), PlayerRace.HUMAN, 900, 30000);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.label.startsWith("reloc:") && !first.label.contains("far-point"),
            "first pick should be the real landmark, got " + first.label);
        assertFalse(first.label.equals(second.label),
            "second pick must not re-hop the same landmark (anti-oscillation), got " + second.label);
    }

    @Test
    void farResortPrefersRealAnchorOverRandomPoint()
    {
        // S5-T01: pure random far points land on unwalkable terrain -> server rejects -> freeze.
        // When a real walkable anchor (landmark or hunt zone) is in range, choose() must NOT pick
        // a reloc:far-point.
        RelocationPlanner p = new RelocationPlanner("ai_combat_11");
        Target t = p.choose(20, -65000, 150000, -3000, false,
            nothing(), PlayerRace.HUMAN, 1000, 60000);
        assertNotNull(t);
        assertFalse(t.label.contains("far-point"),
            "must prefer a real walkable anchor over a random point, got " + t.label);
        assertTrue(t.label.startsWith("reloc:"), "unexpected relocation label " + t.label);
    }

    @Test
    void nudgeIsShortNearbyStep()
    {
        // S5-T04: escape-gate nudge must be a SHORT step (persists server-side), near the current spot.
        RelocationPlanner p = new RelocationPlanner("ai_combat_12");
        RelocationPlanner.Target n = p.nudge(10_000, 20_000, -3000);
        assertNotNull(n);
        double d = Math.hypot(10_000 - n.x, 20_000 - n.y);
        assertTrue(d >= 1000 && d <= 2000, "nudge should be ~1200-1800u, was " + d);
        assertTrue(n.label.contains("nudge"), "nudge label, got " + n.label);
    }

    private static List<int[]> nothing()
    {
        return new ArrayList<>();
    }
}

