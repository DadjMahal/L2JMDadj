package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.aiplayer.behavior.FleetSpreadPlanner.FleetPeer;
import com.aiplayer.behavior.FleetSpreadPlanner.SpreadAnchor;
import com.aiplayer.knowledge.RaceGuide;


/**
 * FleetSpreadPlanner (ultra-smart) tests — the anti-clustering picker must give each bot a real hunt
 * anchor that avoids where fleet mates already are, tie-break deterministically, and never fall back
 * to the void spot. Real hunt zones (RaceGuide): Talking Island @(-99500,237500), Elven Forest
 * @(10000,50000), Dark Elven Swampland @(20000,10000), Gludio Plains @(-60000,140000) — all are in the
 * level-8 band (3..13).
 */
class FleetSpreadPlannerTest
{
    private static final int TALKING_X = -99500;
    private static final int TALKING_Y = 237500;
    private static final int ELVEN_X = 10000;
    private static final int ELVEN_Y = 50000;
    private static final int DARK_X = 20000;
    private static final int DARK_Y = 10000;

    private static List<FleetPeer> peers(FleetPeer... ps)
    {
        List<FleetPeer> l = new ArrayList<>();
        for (FleetPeer p : ps)
        {
            l.add(p);
        }
        return l;
    }

    @Test
    void emptyFleetPicksNearestZone()
    {
        // Level 8, solo at Talking Island: every zone is uncrowded, so the NEAREST wins.
        SpreadAnchor a = FleetSpreadPlanner.pickAnchor(8, TALKING_X, TALKING_Y, -3500,
            peers());
        assertNotNull(a);
        assertEquals("Talking Island", a.zoneName, "nearest empty zone, got " + a.zoneName);
        assertEquals(0, a.mateCount);
    }

    @Test
    void clusteredFleetSpreadsToLeastCrowdedNearestZone()
    {
        // Level 8 bot at Elven Forest; the whole fleet gangs at Talking Island.
        List<FleetPeer> fleet = peers(
            new FleetPeer(TALKING_X, TALKING_Y, -3500),
            new FleetPeer(TALKING_X + 1000, TALKING_Y + 2000, -3500),
            new FleetPeer(TALKING_X - 4000, TALKING_Y + 1000, -3500));
        SpreadAnchor a = FleetSpreadPlanner.pickAnchor(8, ELVEN_X, ELVEN_Y, -3000, fleet);
        assertNotNull(a);
        assertFalse(a.zoneName.equals("Talking Island"),
            "must avoid the crowded gang zone, got " + a.zoneName);
        assertEquals("Elven Forest", a.zoneName, "nearest uncrowded zone, got " + a.zoneName);
        assertEquals(0, a.mateCount);
    }

    @Test
    void avoidsZoneWhereAMateAlreadyLives()
    {
        // Level 8 bot at Dark Elven swampland; one mate is also camped there -> avoid it.
        List<FleetPeer> fleet = peers(new FleetPeer(DARK_X, DARK_Y, -3000));
        SpreadAnchor a = FleetSpreadPlanner.pickAnchor(8, DARK_X, DARK_Y, -3000, fleet);
        assertNotNull(a);
        assertFalse(a.zoneName.equals("Dark Elven Swampland"),
            "must leave the mate's zone, got " + a.zoneName);
        assertEquals("Elven Forest", a.zoneName, "nearest uncrowded zone, got " + a.zoneName);
        assertEquals(0, a.mateCount);
    }

    @Test
    void deterministicForSameInput()
    {
        SpreadAnchor a = FleetSpreadPlanner.pickAnchor(8, ELVEN_X, ELVEN_Y, -3000, peers());
        SpreadAnchor b = FleetSpreadPlanner.pickAnchor(8, ELVEN_X, ELVEN_Y, -3000, peers());
        assertNotNull(a);
        assertEquals(a.x, b.x);
        assertEquals(a.y, b.y);
        assertEquals(a.z, b.z);
        assertEquals(a.zoneName, b.zoneName);
    }

    @Test
    void neverReturnsNullForAnyLevel()
    {
        for (int level = 1; level <= 80; level += 7)
        {
            assertNotNull(FleetSpreadPlanner.pickAnchor(level, 0, 0, -3000, peers()),
                "level " + level + " must yield a real anchor");
        }
    }

    @Test
    void farSelfFallsBackToRealLandmarkNotVoid()
    {
        // Self so far from every zone that relocating is pointless -> a real guide landmark.
        SpreadAnchor a = FleetSpreadPlanner.pickAnchor(8, 2_000_000, 2_000_000, 0,
            peers(new FleetPeer(TALKING_X, TALKING_Y, -3500)));
        assertNotNull(a);
        assertFalse(a.zoneName.isEmpty(), "fallback must be a named real landmark");
        assertEquals(1, a.mateCount);
        assertTrue(a.zoneName.length() > 0);
    }
}
