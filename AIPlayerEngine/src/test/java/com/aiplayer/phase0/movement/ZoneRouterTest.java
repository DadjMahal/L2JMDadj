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
}