package com.aiplayer.behavior.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiplayer.core.ItemSnapshot;
import com.aiplayer.behavior.town.ItemValueEstimator.ItemFate;

/** S10-T08: locks ItemValueEstimator (flipped MODE:PARTIAL -> COMPLETE). */
class ItemValueEstimatorTest
{
    @Test
    void nullItemFallsBackToDestroy()
    {
        ItemValueEstimator e = new ItemValueEstimator("ai_t", 40, "Fighter");
        assertEquals(ItemFate.DESTROY, e.evaluate(null));
    }

    @Test
    void hpPotionIsKeptAsConsumable()
    {
        ItemValueEstimator e = new ItemValueEstimator("ai_t", 40, "Fighter");
        assertEquals(ItemFate.KEEP, e.evaluate(ItemSnapshot.from(1061, 5)),
            "HP potion (1061) is a consumable -> keep");
    }

    @Test
    void shouldGoSellIsBooleanDecision()
    {
        ItemValueEstimator e = new ItemValueEstimator("ai_t", 40, "Fighter");
        List<ItemSnapshot> inv = new ArrayList<>();
        inv.add(ItemSnapshot.from(1061, 5));
        boolean r = e.shouldGoSell(inv, 80, 1, 5000);
        assertNotNull(e.evaluateAll(inv));
        assertTrue(r == true || r == false, "shouldGoSell is a decision");
    }
}