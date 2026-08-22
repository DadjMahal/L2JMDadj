package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.knowledge.PlayerRace;
import com.aiplayer.knowledge.QuestNode;
import com.aiplayer.knowledge.RaceGuide;
import com.aiplayer.behavior.RestockPlanner.BuyOrder;
import com.aiplayer.behavior.RestockPlanner.RestockPlan;

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
    void gearOrderAppearsWhenGearGuidePickProvided()
    {
        // GK-8: the plan carries whatever GearGuide recommended (class+level+adena-driven).
        com.aiplayer.knowledge.GearGuide.GearPick pick =
            new com.aiplayer.knowledge.GearGuide.GearPick(70, "Claymore", 1_800_000, "D", "SWORD");
        RestockPlan p = RestockPlanner.plan(40, 80, 200_000, PlayerRace.HUMAN, true, 0, 0, pick);
        assertTrue(ordersContain(p, 70), "gear pick flows into the buy list");
        boolean labeled = false;
        for (BuyOrder o : p.orders)
        {
            if (o.itemId == 70)
            {
                labeled = o.label.contains("Claymore");
            }
        }
        assertTrue(labeled, "gear order label names the recommended weapon");
    }

    @Test
    void noGearOrderWithoutAPick()
    {
        // GK-8: no recommendation -> exactly the two consumable orders, nothing else.
        RestockPlan p = plan(40, 80, 200_000, PlayerRace.HUMAN);
        assertEquals(2, p.orders.size(), "only soulshots + potions when no gear pick");
        assertTrue(ordersContain(p, 1835) && ordersContain(p, 1061), "consumables still present");
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
@Test
    void shortageAwarePlanTopsOffExactQuantities()
    {
        // EB-06: when the RestockDecider says "buy toward target", the plan orders that exact qty.
        RestockPlanner.RestockPlan p = RestockPlanner.plan(20, 85, 50_000, PlayerRace.HUMAN,
            true, 1500, 17);
        int ss = 0;
        int hp = 0;
        for (RestockPlanner.BuyOrder o : p.orders)
        {
            if (o.itemId == 1835) ss = o.qty;
            if (o.itemId == 1061) hp = o.qty;
        }
        assertEquals(1500, ss, "soul-shot order tops off to the shortage");
        assertEquals(17, hp, "potion order tops off to the shortage");
    }

    @Test
    void zeroShortageKeepsHistoricalPlan()
    {
        // EB-06: a 0/0 shortage must be byte-identical to the pre-EB-06 base plan (no regression).
        RestockPlanner.RestockPlan base = RestockPlanner.plan(20, 85, 50_000, PlayerRace.HUMAN, true);
        RestockPlanner.RestockPlan top = RestockPlanner.plan(20, 85, 50_000, PlayerRace.HUMAN,
            true, 0, 0);
        assertEquals(base.vendorX, top.vendorX);
        assertEquals(base.vendorY, top.vendorY);
        assertEquals(base.orders.size(), top.orders.size());
    }
}
