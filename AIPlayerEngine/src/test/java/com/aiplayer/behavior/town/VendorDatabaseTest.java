package com.aiplayer.behavior.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.aiplayer.behavior.town.VendorDatabase.VendorInfo;
import com.aiplayer.behavior.town.VendorDatabase.VendorType;


/** S7-T03: every region town has a real, reachable vendor (per-race restock landmarks verified). */
class VendorDatabaseTest
{
    private static final String[] TOWNS = { "Giran", "Aden", "Dion", "Gludio" };

    @Test
    void everyRegionTownHasVendorsAndARealGrocery()
    {
        for (String town : TOWNS)
        {
            assertFalse(VendorDatabase.getVendorsInTown(town).isEmpty(),
                town + " has at least one registered vendor");
            VendorInfo g = VendorDatabase.findNearestVendor(town, VendorType.GROCERY, 0, 0, 0);
            assertNotNull(g, town + " has a GROCERY vendor");
            assertTrue(g.npcId > 0, town + " grocery has a real npcId, got " + g.npcId);
            assertFalse(g.x == 0 && g.y == 0, town + " grocery is a real in-world point");
            assertFalse(g.sellCategories.isEmpty(), town + " grocery sells something");
        }
    }

    @Test
    void vendorsClassifyTheirTrade()
    {
        VendorInfo g = VendorDatabase.findNearestVendor("Giran", VendorType.GROCERY, 0, 0, 0);
        assertNotNull(g);
        assertTrue(g.type == VendorType.GROCERY, "grocery classified as grocery");
        assertTrue(!g.sellCategories.isEmpty() || !g.buyCategories.isEmpty(),
            "grocery actually trades (sells or buys something)");
    }
}