package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}