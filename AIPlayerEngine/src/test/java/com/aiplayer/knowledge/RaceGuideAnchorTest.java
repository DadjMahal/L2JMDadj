package com.aiplayer.knowledge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * GK-6 — audit-required anchor test: each race's hand-written RaceGuide L1-5 NEWBIE HUNT-FIELD
 * anchor must correspond to a REAL spawn cluster in npcs.json (nearest spawn <= 3,000 units).
 * (The tutorial helper NPCs are static town guards, correctly absent from spawns/*.xml, so the
 * hunt-field anchors — what the guide actually points bots at — are the right oracle.)
 */
class RaceGuideAnchorTest
{
    private static final KnowledgeBase KB = KnowledgeBase.getInstance();

    @Test
    void everyRaceNewbieAnchorNearARealSpawn()
    {
        boolean allOk = true;
        for (PlayerRace race : PlayerRace.values())
        {
            List<HuntZone> zones = RaceGuide.huntZones(race, 1, 5);
            assertTrue(!zones.isEmpty(), "newbie zone exists for " + race);
            HuntZone z = zones.get(0);
            double nearest = nearestSpawnDistance(z.x, z.y);
            System.out.println("[anchor] " + race + " " + z.name + " at (" + z.x + "," + z.y
                + ") nearest spawn = " + (int) nearest + "u " + (nearest <= 3_000 ? "OK" : "TOO-FAR"));
            allOk &= nearest <= 3_000;
        }
        assertTrue(allOk, "every race newbie hunt anchor must be <=3,000u from a real spawn");
    }

    private static double nearestSpawnDistance(int x, int y)
    {
        double best = Double.MAX_VALUE;
        for (KnowledgeBase.Npc n : KB.allNpcs())
        {
            for (KnowledgeBase.Spawn s : n.spawns)
            {
                double dx = s.x - x;
                double dy = s.y - y;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < best)
                {
                    best = d;
                }
            }
        }
        return best;
    }
}
