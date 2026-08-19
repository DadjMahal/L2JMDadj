package com.aiplayer.phase0.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.guide.PlayerRace;
import com.aiplayer.phase0.guide.QuestNode;
import com.aiplayer.phase0.guide.RaceGuide;
import com.aiplayer.phase0.play.RestockPlanner.BuyOrder;
import com.aiplayer.phase0.play.RestockPlanner.RestockPlan;

/**
 * RestockPlanner — the pure helper behind the ladder's RESTOCK branch. Must be deterministic,
 * always return a plan, and route to the race's real vendor landmark.
 */
class RestockPlannerTest
{
    /** Vendor landmark the race would pick at the given level (shared with controller tests). */
    public static QuestNode humanAnchor(int level)
    {
        return anchor(PlayerRace.HUMAN, level);
    }

    /** Vendor landmark for an arbitrary race at the given level (shared helper). */
    public static QuestNode anchor(PlayerRace race, int level)
    {
        return RaceGuide.idleAnchor(race, level);
    }

    private static RestockPlan plan(int level, int inventoryPct, int coins, PlayerRace race)
    {
        return RestockPlanner.plan(level, inventoryPct, coins, race);
    }

    private static boolean ordersContain(RestockPlan p, int itemId)
    {
        for (BuyOrder o : p.orders)
        {
            if (o.itemId == itemId)
            {
                return true;
            }
        }
        return false;
    }

    @Test
    void alwaysReturnsNonNullPlan()
    {
        assertNotNull(plan(10, 80, 0, PlayerRace.HUMAN), "plan is never null");
    }

    @Test
    void ordersAlwaysIncludeSoulshots()
    {
        RestockPlan p = plan(10, 80, 0, PlayerRace.HUMAN);
        assertNotNull(p.orders);
        assertTrue(ordersContain(p, 1835), "soulshots are always in the order list");
    }

    @Test
    void gearUpgradeAppearsAtHighLevelAndCoins()
    {
        // Level 40 and rich -> a gear upgrade order is added.
        RestockPlan p = plan(40, 80, 200000, PlayerRace.HUMAN);
        assertTrue(ordersContain(p, 2375), "gear upgrade expected at high level + high coins");
    }

    @Test
    void noGearUpgradeWhenPoorDespiteHighLevel()
    {
        // High level but broke -> no gear upgrade order.
        RestockPlan p = plan(40, 80, 1000, PlayerRace.HUMAN);
        assertTrue(!ordersContain(p, 2375), "no gear upgrade when coins are low");
    }

    @Test
    void vendorLandmarkMatchesIdleAnchorForRace()
    {
        QuestNode anchor = anchor(PlayerRace.HUMAN, 10);
        RestockPlan p = plan(10, 80, 0, PlayerRace.HUMAN);
        assertEquals(anchor.x, p.vendorX, "vendor X matches idleAnchor");
        assertEquals(anchor.y, p.vendorY, "vendor Y matches idleAnchor");
        assertEquals(anchor.z, p.vendorZ, "vendor Z matches idleAnchor");
    }

    @Test
    void vendorLandmarkHonorsConfiguredRace()
    {
        QuestNode elf = anchor(PlayerRace.ELF, 20);
        RestockPlan p = plan(20, 80, 0, PlayerRace.ELF);
        assertEquals(elf.x, p.vendorX, "ELF vendor X");
        assertEquals(elf.y, p.vendorY, "ELF vendor Y");
    }

    @Test
    void deterministicAcrossCalls()
    {
        RestockPlan a = plan(30, 80, 150000, PlayerRace.HUMAN);
        RestockPlan b = plan(30, 80, 150000, PlayerRace.HUMAN);
        assertEquals(a.vendorX, b.vendorX, "vendor X deterministic");
        assertEquals(a.vendorY, b.vendorY, "vendor Y deterministic");
        assertEquals(a.orders.size(), b.orders.size(), "order list size deterministic");
        for (int i = 0; i < a.orders.size(); i++)
        {
            assertEquals(a.orders.get(i).itemId, b.orders.get(i).itemId, "order item id stable");
            assertEquals(a.orders.get(i).qty, b.orders.get(i).qty, "order qty stable");
        }
    }

    @Test
    void vendorLandmarkIsRealPerRace()
    {
        // S4-T05: each race's restock trip targets a REAL in-world vendor landmark (not the void).
        for (PlayerRace race : PlayerRace.values())
        {
            RestockPlanner.RestockPlan p = RestockPlanner.plan(20, 85, 50_000, race);
            assertNotNull(p, race + " always returns a plan");
            boolean isVoid = p.vendorX == 0 && p.vendorY == 0;
            assertFalse(isVoid, race + " vendor must be a real point, got " + p.vendorX + "," + p.vendorY);
        }
    }

    @Test
    void fightersRestockMorePotionsThanMystics()
    {
        // S7-T05: per-class restock — a melee fighter should carry more HP potions than a mystic.
        assertTrue(RestockPlanner.potionsFor(10, 1000, true) > RestockPlanner.potionsFor(10, 1000, false),
            "fighter potion order exceeds mystic at the same level/coins");
        assertEquals(RestockPlanner.potionsFor(10, 1000, true), RestockPlanner.potionsFor(10, 1000, true),
            "class-aware qty is deterministic");
    }
}
