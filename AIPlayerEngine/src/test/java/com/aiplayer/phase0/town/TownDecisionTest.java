package com.aiplayer.phase0.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * S7-T02/T06/T07/T09: pure town/economy decision layers are locked so the live protocol managers
 * (BuyManager / SellManager / WarehouseManager / TeleportManager) inherit tested policy.
 */
class TownDecisionTest
{
    @Test
    void buyQtyClampsToAffordableAdena()
    {
        assertTrue(BuyManager.canAfford(1000, 10, 50), "10x50 = 500 <= 1000");
        assertFalse(BuyManager.canAfford(100, 10, 50), "500 > 100 -> cannot buy");
        assertTrue(BuyManager.buyQty(10, 1000, 50) == 10, "affordable full need");
        assertTrue(BuyManager.buyQty(10, 100, 50) == 2, "clamp to adena/price = 2");
        assertTrue(BuyManager.buyQty(0, 100, 50) == 0, "no need -> 0");
        assertTrue(BuyManager.buyQty(10, 1000, 0) == 0, "free/zero price -> 0");
    }

    @Test
    void sellOverflowAndJunkValuePolicy()
    {
        assertTrue(SellManager.autoSellOverflow(78, 80), "bag nearly full -> auto-sell");
        assertFalse(SellManager.autoSellOverflow(40, 80), "half bag -> no trip");
        assertTrue(SellManager.junkWorthSelling(5000, 4000), "junk above threshold sells");
        assertFalse(SellManager.junkWorthSelling(100, 4000), "cheap junk waits");
    }

    @Test
    void warehouseDepositsOnOverflow()
    {
        assertTrue(WarehouseManager.depositOverflow(79, 80), "nearly full -> deposit overflow");
        assertFalse(WarehouseManager.depositOverflow(60, 80), "plenty of space -> hold");
    }

    @Test
    void teleportWhenWalkIsTooLong()
    {
        assertTrue(TeleportManager.farEnoughToTeleport(7200, 6000), "2h walk -> use gatekeeper");
        assertFalse(TeleportManager.farEnoughToTeleport(3000, 6000), "short walk stays grounded");
    }
}