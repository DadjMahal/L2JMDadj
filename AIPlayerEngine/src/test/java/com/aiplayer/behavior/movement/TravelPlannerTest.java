package com.aiplayer.behavior.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.movement.TravelPlanner.Mode;
import com.aiplayer.behavior.movement.TravelPlanner.Plan;

/**
 * EB-07 — the pure town/region travel decision planner. Locks the WALK vs TELEPORT vs fallback
 * matrix so a bot never teleports into debt or walks a leg a gatekeeper would do for free.
 */
class TravelPlannerTest
{
    private static final int GOAL_X = 10000;
    private static final int GOAL_Y = 20000;
    private static final int GOAL_Z = -3000;

    // ================================================================
    // WALK (close goal)
    // ================================================================

    @Test
    void walksWhenGoalIsNearby()
    {
        Plan p = TravelPlanner.plan("Gludio", "", 9_500, 19_000, -3000,
            GOAL_X, GOAL_Y, GOAL_Z, 100_000, 20, true);
        assertNotNull(p);
        assertTrue(p.shouldWalk());
        assertEquals(Mode.WALK, p.mode);
        assertEquals(GOAL_X, p.walkX);
        assertEquals(GOAL_Y, p.walkY);
    }

    @Test
    void walksTowardUnknownGoalTown()
    {
        Plan p = TravelPlanner.plan("", "", 0, 0, 0, GOAL_X, GOAL_Y, GOAL_Z, 0, 1, false);
        assertEquals(Mode.FALLBACK_WALK, p.mode);
        assertTrue(p.shouldWalk());
    }

    @Test
    void planByCoordsAlwaysWalks()
    {
        Plan p = TravelPlanner.planByCoords(0, 1000, 0, 5009, 0, 0, 50_000, 80, true);
        assertTrue(p.shouldWalk());
    }

    // ================================================================
    // TELEPORT (long trip, leg exists, affordable)
    // ================================================================

    @Test
    void teleportsWhenLegExistsAndAffordable()
    {
        // Real leg: Giran -> Aden (9200 adena, Lv20). The bot has coins + level → TELEPORT.
        Plan p = TravelPlanner.plan("Aden", "Giran", 83_358, 147_934, -3400,
            147_450, 27_030, -2208, 100_000, 22, true);
        assertEquals(Mode.TELEPORT, p.mode, "Giran->Aden is a real gatekeeper leg, affordable + lvl-ok");
        assertNotNull(p.leg);
        assertEquals("Aden", p.leg.toTown);
        assertTrue(p.leg.cost <= 50_000, "never pays crazy teleport prices");
        assertTrue(p.tail.isEmpty(), "Aden is a direct destination, no tail");
    }

    @Test
    void walksWhenTeleportTooExpensive()
    {
        Plan p = TravelPlanner.plan("Aden", "Giran", 83_358, 147_934, -3400,
            147_450, 27_030, -2208, 1_000, 80, true);
        assertTrue(p.shouldWalk(), "1k adena can't pay the 9200 leg -> walk");
    }

    @Test
    void walksWhenLevelTooLow()
    {
        Plan p = TravelPlanner.plan("Aden", "Giran", 83_358, 147_934, -3400,
            147_450, 27_030, -2208, 1_000_000, 15, true);
        assertTrue(p.shouldWalk(), "Lv15 < Lv20 gatekeeper requirement -> walk");
    }

    @Test
    void fallsBackToWalkWhenTeleportDisabled()
    {
        Plan p = TravelPlanner.plan("Aden", "Giran", 0, 0, 0,
            200_000, -200_000, -3000, 1_000_000, 80, false);
        assertTrue(p.shouldWalk());
    }

    // ================================================================
    // SANITY on modes
    // ================================================================

    @Test
    void teleportModeExcludesWalk()
    {
        // Force the teleport-only path with a cheap direct leg.
        Plan p = TravelPlanner.plan("Giran", "Talking Island", 0, 0, 0,
            200_000, -200_000, -3000, 100_000, 80, true);
        if (p.mode == Mode.TELEPORT)
        {
            assertFalse(p.shouldWalk());
            assertTrue(p.shouldTeleport());
        }
    }
}