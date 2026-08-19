package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * TIM-001 H3 tests: ZoneRouter must always plan a real FAR route for an in-world bot, prefer
 * level-appropriate farm zones, split the route into hops the server accepts (each <= MAX_HOP_DIST,
 * live-verified server cap 9900u — MoveToLocation.java:156), and refuse to route pre-world.
 */
class ZoneRouterTest
{
    @Test
    void plansFarRouteWithinConfiguredBounds()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_01");
        // Talking Island live coords from the audit (real bot position).
        ZoneRouter.RouteGoal g = r.plan(20, -82759, 250149, -3600, 4000, 30000);

        assertNotNull(g);
        double dist = Math.hypot(g.destX - (-82759), g.destY - 250149);
        assertTrue(dist >= 4000.0 - 1, "must travel at least minRadius, got " + dist);
        assertTrue(dist <= 30000.0 + 1, "must not exceed maxRadius, got " + dist);
        assertEquals(-3600, g.destZ, "z preserved on far route");
        assertTrue(g.totalHops() >= 1, "route has at least one hop");
        assertTrue(g.hasMoreHops(), "first hop is pending");
    }

    @Test
    void hopsStayBelowServerCapAndEndAtDestination()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_01");
        // Very far route (level 20, from TI): needs many hops.
        ZoneRouter.RouteGoal g = r.plan(20, -82759, 250149, -3600, 20000, 30000);

        assertNotNull(g);
        int[] prev = null;
        int hopsSeen = 0;
        int[] hop;
        while ((hop = g.nextHop()) != null)
        {
            if (prev != null)
            {
                double step = Math.hypot(hop[0] - prev[0], hop[1] - prev[1]);
                assertTrue(step <= ZoneRouter.MAX_HOP_DIST + 1, "each hop <= server cap, got " + step);
            }
            prev = hop;
            hopsSeen++;
        }
        assertTrue(hopsSeen >= 2, "far route requires multiple hops, got " + hopsSeen);
        assertEquals(g.destX, prev[0], "last hop lands exactly at destination");
        assertEquals(g.destY, prev[1], "last hop lands exactly at destination");
    }

    @Test
    void prefersLevelAppropriateFarmZone()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_02");
        // Level 20 near Ruins of Agony (L18-28 farm zone) should be picked as the route.
        ZoneRouter.RouteGoal g = r.plan(20, -50000, 120000, -3000, 4000, 30000);

        assertNotNull(g);
        String label = g.label == null ? "" : g.label;
        assertTrue(label.startsWith("farm:"), "level-appropriate farm zone preferred, got " + label);
    }

    @Test
    void refusesToRouteBeforeWorldEntry()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_03");
        assertNull(r.plan(20, 0, 0, 0, 4000, 30000), "0,0 = not spawned yet -> stay put");
    }

    @Test
    void twentyOneKiloRouteDegradesIntoManyShortServerAcceptableHops()
    {
        java.util.List<int[]> hops = ZoneRouter.buildHops(0, 0, 0, 21000, 0, 0);
        assertTrue(hops.size() >= 3, "21k route splits into >=3 hops, got " + hops.size());
        int[] prev = null;
        for (int[] hop : hops)
        {
            if (prev != null)
            {
                double step = Math.hypot(hop[0] - prev[0], hop[1] - prev[1]);
                assertTrue(step <= ZoneRouter.MAX_HOP_DIST + 1, "each hop <= server cap, got " + step);
            }
            prev = hop;
        }
        int[] first = hops.get(0);
        int[] last = hops.get(hops.size() - 1);
        assertTrue(Math.hypot(first[0], first[1]) > 0, "first hop must be real progress, not origin");
        assertTrue(last[0] > first[0], "destination strictly past the origin");
        assertEquals(21000, last[0], "last hop lands exactly on the destination");
        assertEquals(0, last[1], "last hop lands exactly on the destination");
        prev = null;
        for (int[] hop : hops)
        {
            if (prev != null)
            {
                assertTrue(Math.hypot(hop[0] - prev[0], hop[1] - prev[1]) > 0, "no zero-length step");
            }
            prev = hop;
        }
    }

    @Test
    void degenerateZeroLengthRouteYieldsNoHops()
    {
        java.util.List<int[]> hops = ZoneRouter.buildHops(100, 200, -3600, 100, 200, -3600);
        assertTrue(hops.isEmpty(), "zero-length route must produce zero hops");
    }

    @Test
    void routeIsStuckOnlyAfterEnoughConsecutiveTimeouts()
    {
        // TIM-001 stuck-hop recovery: a hop that the server never walks the char toward must NOT
        // resend forever (the movesSent=2-in-2min stall); it becomes "stuck" only after
        // MAX_HOP_TIMEOUTS consecutive timeouts, so legitimately slow moves still get retries.
        assertFalse(ZoneRouter.isRouteStuck(0), "fresh hop is not stuck");
        assertFalse(ZoneRouter.isRouteStuck(1), "one timeout still allows a retry");
        assertTrue(ZoneRouter.isRouteStuck(2), "MAX_HOP_TIMEOUTS timeouts -> stuck, abandon & re-plan");
        assertTrue(ZoneRouter.isRouteStuck(5), "more timeouts stay stuck");
        assertEquals(2, ZoneRouter.MAX_HOP_TIMEOUTS, "policy threshold is stable for test/debugging");
    }

    @Test
    void hopsAreDeliveredOneAtATimeAndExhaustExactlyAtDestination()
    {
        java.util.List<int[]> hops = ZoneRouter.buildHops(0, 0, 0, 21000, 0, 0);
        ZoneRouter.RouteGoal g = new ZoneRouter.RouteGoal(21000, 0, 0, "far-point", "task_0016 test", hops);
        assertTrue(g.hasMoreHops(), "route starts with a pending hop");
        int[] prev = null;
        int pulled = 0;
        int[] h;
        while ((h = g.nextHop()) != null)
        {
            if (prev != null)
            {
                assertFalse(java.util.Arrays.equals(prev, h), "a hop is never handed out twice");
                assertTrue(Math.hypot(h[0] - prev[0], h[1] - prev[1]) > 0, "each delivered hop is real progress");
            }
            prev = h;
            pulled++;
        }
        assertEquals(hops.size(), pulled, "every planned hop is delivered exactly once");
        assertFalse(g.hasMoreHops(), "route is complete after the last hop");
        assertTrue(prev != null && prev[0] == 21000 && prev[1] == 0, "last delivered hop is the destination");
    }
@Test
    void routeToDestinationBuildsHopsEndingAtExactTarget()
    {
        ZoneRouter.RouteGoal g = ZoneRouter.routeTo(
            -82759, 250149, -3600,   // Talking Island live origin
            -14440, 121064, -2900,   // Gludio quest NPC (40001 giver, planner coords)
            "goal:quest:40001", "quest navigation for 1C");
        assertNotNull(g);
        assertEquals(-14440, g.destX, "destination x preserved");
        assertEquals(121064, g.destY, "destination y preserved");
        assertEquals(-2900, g.destZ, "destination z preserved");
        assertTrue(g.totalHops() >= 2, "long Gludio route splits into multiple hops, got " + g.totalHops());
        assertTrue(g.hasMoreHops(), "first hop pending");

        int[] prev = null;
        int[] hop;
        int seen = 0;
        while ((hop = g.nextHop()) != null)
        {
            if (prev != null)
            {
                double step = Math.hypot(hop[0] - prev[0], hop[1] - prev[1]);
                assertTrue(step <= ZoneRouter.MAX_HOP_DIST + 1, "each hop <= server cap, got " + step);
            }
            prev = hop;
            seen++;
        }
        assertEquals(prev[0], -14440, "last hop lands exactly on the quest NPC");
        assertEquals(prev[1], 121064, "last hop lands exactly on the quest NPC");
        assertTrue(seen == g.totalHops(), "every planned hop delivered once");
    }

    @Test
    void skipsRecentlyAbandonedFarmZoneInsteadOfReplanningToSameDestination()
    {
        // Regression: post-respawn re-loop. When a farm-zone route is abandoned as unreachable, the
        // NEXT plan must NOT deterministically re-select the SAME zone center (54 HOP / 27-abandon loop).
        ZoneRouter r = new ZoneRouter("ai_combat_04");
        ZoneRouter.RouteGoal first = r.plan(5, -87792, 216639, -3619, 4000, 30000);
        assertNotNull(first);
        assertTrue(first.label.startsWith("farm:"), "level-5 picks a farm zone first, got " + first.label);

        // Simulate FleetPlay recording that this route was abandoned as unreachable.
        r.noteUnreachableDestination(first.destX, first.destY);

        ZoneRouter.RouteGoal second = r.plan(5, -87792, 216639, -3619, 4000, 30000);
        assertNotNull(second, "still plans a route after abandoning one destination");
        boolean sameDest = second.destX == first.destX && second.destY == first.destY;
        assertFalse(sameDest, "must not re-plan to the abandoned farm-zone center on the next tick");
    }

    @Test
    void abandonedFarPointIsAvoidedByRandomReroll()
    {
        // The far-point fallback must also avoid a destination the fleet abandoned as unreachable.
        ZoneRouter r = new ZoneRouter("ai_combat_05");
        // Force the far-point branch: abandon every level-5 farm zone center first by re-planning and
        // abandoning until a far-point is produced, then abandon that far point and re-roll.
        int[] abandoned = new int[]{-1, -1};
        for (int i = 0; i < 16; i++)
        {
            ZoneRouter.RouteGoal g = r.plan(5, -87792, 216639, -3619, 4000, 30000);
            assertNotNull(g);
            if (g.label.equals("far-point"))
            {
                abandoned[0] = g.destX;
                abandoned[1] = g.destY;
                break;
            }
            r.noteUnreachableDestination(g.destX, g.destY);
        }
        assertTrue(abandoned[0] != -1, "a far-point was produced after abandoning all farm zones");

        r.noteUnreachableDestination(abandoned[0], abandoned[1]);
        ZoneRouter.RouteGoal next = r.plan(5, -87792, 216639, -3619, 4000, 30000);
        assertNotNull(next, "still plans after abandoning the far point");
        boolean same = next.destX == abandoned[0] && next.destY == abandoned[1];
        assertFalse(same, "re-roll must avoid the abandoned far point");
    }

    @Test
    void routeToDegenerateDestinationReturnsNullToAllowFallback()
    {
        // Same spot -> nothing to route; FleetPlay falls back to a random far-travel point.
        assertNull(ZoneRouter.routeTo(-82759, 250149, -3600, -82759, 250149, -3600, "goal:x", "dup"));
        // Not in-world -> refuse to route.
        assertNull(ZoneRouter.routeTo(0, 0, 0, 100, 100, 0, "goal:x", "pre-world"));
    }

    @Test
    void slopedRouteInterpolatesZAcrossHopsAndLandsExactlyAtDestZ()
    {
        // Regression: intermediate hops used to carry destZ directly, so on a sloped route the
        // first hop was sent at the final (cliff) Z over the intermediate terrain — the server's
        // isCompletelyBlocked(geoX, geoY, _targetZ) geo-cell check in MoveToLocation.java:90 could
        // reject it and stall a reachable route as "hop unreachable". Each hop's Z must follow the
        // linear slope; the last hop lands exactly on destZ. Realistic case: TI char at z=-3619
        // routing to Ruins of Agony (z=-3000) — the level-20 farm route from the live run.
        java.util.List<int[]> hops = ZoneRouter.buildHops(0, 0, -3619, 21000, 0, -3000);
        assertTrue(hops.size() >= 3, "sloped 21k route splits into >=3 hops, got " + hops.size());
        int[] first = hops.get(0);
        int[] last = hops.get(hops.size() - 1);
        assertTrue(first[2] > -3619 && first[2] < -3000,
            "first hop Z is partway up the slope (got " + first[2] + "), not the destination Z");
        assertTrue(first[2] != -3000, "first hop must NOT carry the destination Z (was the pre-fix bug)");
        assertEquals(-3000, last[2], "last hop lands exactly on the destination Z");
        int prevZ = -3619;
        for (int[] hop : hops)
        {
            assertTrue(hop[2] > prevZ && hop[2] <= -3000,
                "Z climbs monotonically toward dest along the slope (got " + hop[2] + ")");
            prevZ = hop[2];
        }
        // A truly flat route keeps z == fromZ on every hop (existing behaviour preserved).
        for (int[] hop : ZoneRouter.buildHops(0, 0, -3619, 21000, 0, -3619))
        {
            assertEquals(-3619, hop[2], "flat route keeps the origin Z on every hop");
        }
    }

    @Test
    void rejectsUnwalkableVoidAndOceanDestinations()
    {
        // S5-T05: never route onto the void spawn or known ocean/void bands.
        assertFalse(ZoneRouter.isWalkableTarget(ZoneRouter.VOID_X, ZoneRouter.VOID_Y, 434));
        assertFalse(ZoneRouter.isWalkableTarget(300000, -300000, 0), "ocean band is unwalkable");
        assertFalse(ZoneRouter.isWalkableTarget(2_000_000, 2_000_000, 0), "far-off-map is rejected");
        assertTrue(ZoneRouter.isWalkableTarget(-84108, 244604, -3728), "Talking Island village is fine");
        assertNull(ZoneRouter.routeTo(-84000, 244500, -3728, ZoneRouter.VOID_X, ZoneRouter.VOID_Y, 434, "x", "y"),
            "routeTo refuses the void destination");
    }
}