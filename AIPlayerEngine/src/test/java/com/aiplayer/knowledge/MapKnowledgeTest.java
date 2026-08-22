package com.aiplayer.knowledge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * GK-9 — locks map.json (travel-nav datapack) against the real generated extractor output.
 * Parsed with the same dependency-free JsonResource the engine uses.
 */
class MapKnowledgeTest
{
    private static final List<Map<String, Object>> MAP = JsonResource.autoObjectList("map.json");

    private static int count(String kind)
    {
        int n = 0;
        for (Map<String, Object> r : MAP)
        {
            if (kind.equals(r.get("kind")))
            {
                n++;
            }
        }
        return n;
    }

    private static int i(Map<?, ?> m, String key)
    {
        return ((Number) m.get(key)).intValue();
    }

    private static boolean inBounds(int x, int y, int z)
    {
        return x >= -204800 && x <= 204800
            && y >= -262144 && y <= 262144
            && z >= -16000 && z <= 16000;
    }

    @Test
    void allFourKindsPresent()
    {
        assertTrue(MAP.size() > 2000, "map records: " + MAP.size());
        assertTrue(count("teleporter") >= 40, "teleporters: " + count("teleporter"));
        assertTrue(count("zone") >= 500, "zones: " + count("zone"));
        assertTrue(count("route") >= 5, "routes: " + count("route"));
        assertTrue(count("spawnRegion") >= 100, "spawnRegions: " + count("spawnRegion"));
    }

    @Test
    void roxxyTownTeleportHasNineDestinations()
    {
        Map<String, Object> roxxy = null;
        for (Map<String, Object> r : MAP)
        {
            if (Integer.valueOf(30006).equals(r.get("npcId")) && "NORMAL".equals(r.get("type")))
            {
                roxxy = r;
                break;
            }
        }
        assertNotNull(roxxy, "Roxxy (30006) town teleporter record exists");
        List<?> dests = (List<?>) roxxy.get("destinations");
        assertNotNull(dests, "Roxxy has destinations");
        assertTrue(dests.size() >= 9, "Roxxy destinations: " + dests.size());
        boolean dwarven = false;
        for (Object o : dests)
        {
            Map<?, ?> d = (Map<?, ?>) o;
            if ("Dwarven Village".equals(d.get("name"))
                    && Integer.valueOf(115120).equals(d.get("x"))
                    && Integer.valueOf(-178112).equals(d.get("y")))
            {
                dwarven = true;
            }
        }
        assertTrue(dwarven, "Roxxy can teleport to Dwarven Village (real coords)");
    }

    @Test
    void townZonesPresent()
    {
        boolean giran = false;
        for (Map<String, Object> r : MAP)
        {
            if ("zone".equals(r.get("kind")) && "Giran Castle Town".equals(r.get("name")))
            {
                giran = true;
                assertTrue(((List<?>) r.get("nodes")).size() >= 10, "Giran polygon nodes");
                break;
            }
        }
        assertTrue(giran, "Giran Castle Town zone present");
    }

    @Test
    void teleporterDestinationsStrictlyInWorld()
    {
        for (Map<String, Object> r : MAP)
        {
            if (!"teleporter".equals(r.get("kind")))
            {
                continue;
            }
            for (Object o : (List<?>) r.get("destinations"))
            {
                Map<?, ?> c = (Map<?, ?>) o;
                assertTrue(inBounds(i(c, "x"), i(c, "y"), i(c, "z")),
                    "teleport dest in world: " + r.get("id") + " " + c.get("name"));
            }
        }
    }

    @Test
    void zoneCentroidsInWorldXyBoundsUnlessFlagged()
    {
        for (Map<String, Object> r : MAP)
        {
            if (!"zone".equals(r.get("kind"))
                    || Boolean.TRUE.equals(r.get("needsReview")))
            {
                continue; // needsReview = documented boss/raid/deep outliers (GK-5 pattern)
            }
            assertTrue(inBounds(i(r, "x"), i(r, "y"), 0),
                "zone centroid x/y in bounds: " + r.get("name"));
        }
    }

    @Test
    void spawnRegionsCarryLevelBands()
    {
        for (Map<String, Object> r : MAP)
        {
            if (!"spawnRegion".equals(r.get("kind")))
            {
                continue;
            }
            assertTrue(i(r, "minLevel") <= i(r, "maxLevel"),
                "level band sane: " + r.get("name"));
            assertTrue(((Number) r.get("spawnCount")).intValue() > 0);
            assertTrue(inBounds(i(r, "x"), i(r, "y"), 0),
                "region centroid x/y in bounds: " + r.get("name"));
        }
    }

    @Test
    void routesHaveWaypoints()
    {
        int total = 0;
        int withPoints = 0;
        for (Map<String, Object> r : MAP)
        {
            if (!"route".equals(r.get("kind")))
            {
                continue;
            }
            total++;
            List<?> pts = (List<?>) r.get("points");
            if (pts != null && !pts.isEmpty())
            {
                withPoints++;
                for (Object o : pts)
                {
                    Map<?, ?> p = (Map<?, ?>) o;
                    assertTrue(inBounds(i(p, "x"), i(p, "y"), i(p, "z")),
                        "route waypoint in bounds: " + r.get("id"));
                }
            }
        }
        assertTrue(total >= 5, "routes present: " + total);
        assertTrue(withPoints >= 5, "routes with waypoints: " + withPoints);
    }
}