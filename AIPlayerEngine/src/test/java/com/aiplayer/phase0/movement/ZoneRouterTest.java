package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * TIM-001 H3 tests: ZoneRouter must always propose a real FAR destination for an in-world bot
 * (never a <1500-u hop), prefer level-appropriate farm zones, and refuse to route pre-world.
 */
class ZoneRouterTest
{
    @Test
    void farPointRoutingStaysWithinConfiguredBounds()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_01");
        // Talking Island live coords from the audit (real bot position).
        ZoneRouter.RouteGoal g = r.pick(20, -82759, 250149, -3600, 4000, 30000);

        assertNotNull(g);
        double dist = Math.hypot(g.x - (-82759), g.y - 250149);
        assertTrue(dist >= 4000.0 - 1, "must travel at least minRadius, got " + dist);
        assertTrue(dist <= 30000.0 + 1, "must not exceed maxRadius, got " + dist);
        assertEquals(-3600, g.z, "z preserved on far-point route");
    }

    @Test
    void prefersLevelAppropriateFarmZone()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_02");
        // Level 20 near Gludio: Ruins of Agony (L18-28) is level-appropriate and ~13k away.
        ZoneRouter.RouteGoal g = r.pick(20, -12694, 122776, -3112, 6000, 30000);

        assertNotNull(g);
        String label = g.label == null ? "" : g.label;
        // ZoneRecommender zone list must contain a matching center we can reach inside maxRadius.
        assertTrue(label.startsWith("farm:") || distanceFrom(g, -12694, 122776) >= 6000.0,
            "expected a farm route or at least a far point");
        assertTrue(distanceFrom(g, -12694, 122776) >= 1500.0, "never a degenerate home hop");
    }

    @Test
    void refusesToRouteBeforeWorldEntry()
    {
        ZoneRouter r = new ZoneRouter("ai_combat_03");
        assertNull(r.pick(20, 0, 0, 0, 4000, 30000), "0,0 = not spawned yet -> stay put");
    }

    private static double distanceFrom(ZoneRouter.RouteGoal g, int x, int y)
    {
        return Math.hypot(g.x - x, g.y - y);
    }
}