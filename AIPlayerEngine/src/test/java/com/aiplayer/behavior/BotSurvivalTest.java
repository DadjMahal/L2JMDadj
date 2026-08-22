package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.aiplayer.core.BotSnapshot;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.behavior.BotSurvival.FleeHop;
import com.aiplayer.behavior.BotSurvival.Guard;
import com.aiplayer.behavior.BotSurvival.Item;

/**
 * BotSurvival tests — the per-tick survival/supply decisions (EB-01 extraction from
 * BotSession): potion sipping ownership, death/regen/overwhelm guards, flee-away hop
 * direction, and HP-potion inventory search. Pure logic, no IO/threads.
 */
class BotSurvivalTest
{
    private static final int ITEM_HP_POTION = BotSurvival.HP_POTION_ID;
    private static final int ITEM_SOULSHOT = 1835;

    private static BotSnapshot snap(int hp, int hpMax)
    {
        PacketLogger logger = new PacketLogger("TestBot");
        logger.setCurHp(hp);
        logger.setMaxHp(hpMax);
        return BotSnapshot.from("TestBot", logger);
    }

    // ================================================================
    // POTION DECISION
    // ================================================================

    @Test
    void sipsWhenLowHpAndCooldownSpent()
    {
        BotSnapshot s = snap(20, 100); // 0.20 < 0.45
        assertTrue(BotSurvival.shouldSipPotion(s, 0L, 60_000L));
    }

    @Test
    void doesNotSipWhenHpIsHealthy()
    {
        BotSnapshot s = snap(80, 100); // 0.80 > 0.45
        assertFalse(BotSurvival.shouldSipPotion(s, 0L, 60_000L));
    }

    @Test
    void doesNotSipWhileCooldownActive()
    {
        BotSnapshot s = snap(20, 100);
        assertFalse(BotSurvival.shouldSipPotion(s, 59_000L, 60_000L)); // 1s since last sip < 20s
    }

    // ================================================================
    // SURVIVAL GUARD
    // ================================================================

    @Test
    void deathLoopGuardHoldsUntilWindowExpires()
    {
        BotSnapshot s = snap(50, 100);
        Guard g = BotSurvival.survivalGuard(s, 1_000L, 2_000L, 0L, 0, 8);
        assertTrue(g.active);
        assertTrue(g.reason.contains("death-loop"));
    }

    @Test
    void regenHoldGuardFiresBelowSixtyPercent()
    {
        BotSnapshot s = snap(55, 100); // 0.55 < 0.60
        Guard g = BotSurvival.survivalGuard(s, 1_000L, 0L, 5_000L, 1, 8);
        assertTrue(g.active);
        assertTrue(g.reason.contains("regen"));
    }

    @Test
    void overwhelmGuardFiresBelowSeventyWithManyMobs()
    {
        BotSnapshot s = snap(65, 100); // 0.65 < 0.70
        Guard g = BotSurvival.survivalGuard(s, 1_000L, 0L, 0L, 9, 8);
        assertTrue(g.active);
        assertTrue(g.reason.contains("overwhelm"));
    }

    @Test
    void noGuardWhenHealthy()
    {
        BotSnapshot s = snap(95, 100);
        Guard g = BotSurvival.survivalGuard(s, 1_000L, 0L, 0L, 1, 8);
        assertFalse(g.active);
    }

    // ================================================================
    // FLEE DECISION
    // ================================================================

    @Test
    void fleesAwayDoublingDisplacement()
    {
        // Bot at (100,100), hostal at (90,95) → flee to (120,110) (2× away on both axes).
        FleeHop hop = BotSurvival.fleeHop(100, 100, 0, 90, 95, 0);
        assertEquals(120, hop.x);
        assertEquals(110, hop.y);
        assertEquals(0, hop.z);
    }

    // ================================================================
    // INVENTORY SEARCH
    // ================================================================

    @Test
    void findsStockedHpPotion()
    {
        Item potion = new ItemStub(77, ITEM_HP_POTION, 3);
        assertNotNull(BotSurvival.findPotion(Collections.singletonList(potion)));
    }

    @Test
    void ignoresOtherItemsAndOutOfStockPotions()
    {
        Item soulshot = new ItemStub(1, ITEM_SOULSHOT, 100);
        Item emptyPotion = new ItemStub(2, ITEM_HP_POTION, 0);
        assertNull(BotSurvival.findPotion(Arrays.asList(soulshot, emptyPotion)));
    }

    @Test
    void returnsNullOnEmptyInventory()
    {
        assertNull(BotSurvival.findPotion(null));
        assertNull(BotSurvival.findPotion(Collections.emptyList()));
    }

    // ----------------------------------------------------------------
    // test stubs
    // ----------------------------------------------------------------

    private static final class ItemStub implements Item
    {
        private final int objectId;
        private final int itemId;
        private final long count;

        ItemStub(int objectId, int itemId, long count)
        {
            this.objectId = objectId;
            this.itemId = itemId;
            this.count = count;
        }

        @Override
        public int getObjectId()
        {
            return objectId;
        }

        @Override
        public int getItemId()
        {
            return itemId;
        }

        @Override
        public long getCount()
        {
            return count;
        }
    }
}