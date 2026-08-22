package com.aiplayer.behavior.restock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.restock.RestockDecider.Reason;
import com.aiplayer.behavior.restock.RestockDecider.Verdict;

/**
 * EB-06 — the restock-intent decision module. Must be pure and deterministic: given only real
 * consumable counts + inventory %, it decides WHY the bot should hit the vendor (ammo / potions /
 * full / urgent) and the exact top-off shortages that feed RestockPlanner.
 */
class RestockDeciderTest
{
    // ================================================================
    // NONE
    // ================================================================

    @Test
    void wellStockedBagDoesNotRestock()
    {
        Verdict v = RestockDecider.decide(1500, 30, 40);
        assertFalse(v.shouldRestock());
        assertEquals(Reason.NONE, v.reason);
    }

    @Test
    void unknownSignalsDoNotRestock()
    {
        // -1 = fleet loop has no inventory data yet: never invent a restock trip from nothing.
        Verdict v = RestockDecider.decide(-1, -1, -1);
        assertFalse(v.shouldRestock());
    }

    // ================================================================
    // AMMO / CONSUMABLES
    // ================================================================

    @Test
    void lowSoulshotsTriggerAmmoTrip()
    {
        Verdict v = RestockDecider.decide(120, 50, 30);
        assertEquals(Reason.SOULSHOTS, v.reason);
        assertTrue(v.soulshotShort > 0, "buys toward the soulshot target");
        assertEquals(0, v.hpPotionShort, "potions fine -> no potion order");
    }

    @Test
    void lowPotionsTriggerSustainTrip()
    {
        Verdict v = RestockDecider.decide(2000, 3, 30);
        assertEquals(Reason.POTIONS, v.reason);
        assertTrue(v.hpPotionShort > 0, "buys potions toward the target");
        assertEquals(0, v.soulshotShort, "shots fine -> no ammo order");
    }

    @Test
    void emptySoulshotsBuyFullTarget()
    {
        Verdict v = RestockDecider.decide(0, 50, 30);
        assertEquals(Reason.SOULSHOTS, v.reason);
        assertEquals(RestockDecider.SOULSHOT_TARGET, v.soulshotShort, "empty -> whole 2k target");
    }

    // ================================================================
    // FULL / URGENT
    // ================================================================

    @Test
    void fullBagTriggersFullTrip()
    {
        Verdict v = RestockDecider.decide(2000, 50, 90);
        assertEquals(Reason.FULL, v.reason);
        assertEquals(0, v.soulshotShort, "ammo fine -> no ammo qty");
        assertEquals(0, v.hpPotionShort, "potions fine -> no potion qty");
    }

    @Test
    void fullBagAndLowAmmoIsUrgent()
    {
        Verdict v = RestockDecider.decide(60, 50, 92);
        assertEquals(Reason.URGENT, v.reason, "full + low ammo -> urgent");
        assertTrue(v.soulshotShort > 0, "urgent still buys the missing ammo");
    }

    // ================================================================
    // SHORTAGE MATH
    // ================================================================

    @Test
    void shortageTopsOffToTargetOnly()
    {
        assertEquals(200, RestockDecider.shortage(1800, 2000), "near target -> buy the small gap");
        assertEquals(500, RestockDecider.shortage(1500, 2000));
        assertEquals(2000, RestockDecider.shortage(0, 2000), "empty bag -> whole target");
        assertEquals(0, RestockDecider.shortage(2500, 2000), "over target -> nothing");
        assertEquals(0, RestockDecider.shortage(-1, 0), "no-op target");
    }

    @Test
    void customThresholdsDriveDecider()
    {
        // An early-restock profile (merchant): full-at=30 restocks even at 40% bag.
        Verdict v = RestockDecider.decide(2000, 50, 40, 500, 10, 30);
        assertEquals(Reason.FULL, v.reason);
    }
}