package com.aiplayer.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * GK-8 — GearGuide: the gear/build recommender behind RestockPlanner's gear order. Picks must
 * respect the class's weapon line, the level's grade band, shop availability and the adena
 * budget (4/5 of the purse; the rest is ammo/potion money). Assertions check PROPERTIES of the
 * pick (grade/weaponType/shop-sold/affordable), not fixed item ids, so regenerating the
 * knowledge JSON cannot break the contract.
 */
class GearGuideTest
{
    private static final KnowledgeBase KB = KnowledgeBase.getInstance();

    @Test
    void gradeBandsMatchInterlude()
    {
        assertEquals("NONE", GearGuide.gradeFor(1));
        assertEquals("NONE", GearGuide.gradeFor(19));
        assertEquals("D", GearGuide.gradeFor(20));
        assertEquals("D", GearGuide.gradeFor(39));
        assertEquals("C", GearGuide.gradeFor(40));
        assertEquals("C", GearGuide.gradeFor(51));
        assertEquals("B", GearGuide.gradeFor(52));
        assertEquals("B", GearGuide.gradeFor(60));
        assertEquals("A", GearGuide.gradeFor(61));
        assertEquals("A", GearGuide.gradeFor(75));
        assertEquals("S", GearGuide.gradeFor(76));
    }

    @Test
    void noRecommendationWithoutAdena()
    {
        assertNull(GearGuide.recommendWeapon(0, 40, 0), "broke bot gets no pick");
        assertNull(GearGuide.recommendWeapon(0, 40, -1), "unknown purse (-1) gets no pick");
    }

    @Test
    void noRecommendationBelowFirstClassMilestone()
    {
        // chains.json gates the gear ladder at the first-class step (L20 on every chain).
        assertNull(GearGuide.recommendWeapon(0, 19, 2_000_000), "L19 is pre-first-class: no gear order");
    }

    @Test
    void noPickWhenNothingAffordable()
    {
        // 100k purse -> 80k budget: no D-grade shop weapon costs that little.
        assertNull(GearGuide.recommendWeapon(0, 25, 100_000), "no shop weapon in budget -> null");
    }

    @Test
    void fighterBaseGetsShopSoldDGradeSword()
    {
        GearGuide.GearPick p = GearGuide.recommendWeapon(0, 25, 2_000_000); // Human Fighter
        assertNotNull(p, "rich L25 fighter gets a pick");
        assertEquals("D", p.grade, "L25 -> D-grade");
        assertEquals("SWORD", p.weaponType, "Human Fighter default line is the sword");
        assertTrue(KB.isSoldInShop(p.itemId), "pick must be buylist-sold");
        assertTrue(p.price > 0 && p.price <= 2_000_000 * 4 / 5, "pick fits the 4/5 budget");
    }

    @Test
    void weaponTypeFollowsClassLine()
    {
        // (classId, expected weaponType) — ids verified against classes.json.
        assertLine(7, "DAGGER");      // Rogue
        assertLine(9, "BOW");         // Hawkeye
        assertLine(3, "POLE");        // Warlord
        assertLine(2, "DUAL");        // Gladiator
        assertLine(48, "DUALFIST");   // Tyrant
        assertLine(10, "BLUNT");      // Human Mystic
        assertLine(42, "BLUNT");      // Shillien Oracle (mystic of base 38)
    }

    private static void assertLine(int classId, String weaponType)
    {
        GearGuide.GearPick p = GearGuide.recommendWeapon(classId, 25, 2_000_000);
        assertNotNull(p, "class " + classId + " gets a pick");
        assertEquals(weaponType, p.weaponType, "class " + classId + " weapon line");
        assertTrue(KB.isSoldInShop(p.itemId), "pick is shop-sold");
        assertEquals("D", p.grade, "L25 -> D-grade");
    }

    @Test
    void cGradeOncePastSecondClass()
    {
        GearGuide.GearPick p = GearGuide.recommendWeapon(0, 40, 10_000_000);
        assertNotNull(p, "L40 with a fat purse gets a pick");
        assertEquals("C", p.grade, "L40 -> C-grade");
        assertTrue(p.price <= 10_000_000 * 4 / 5, "C-grade pick fits the budget");
    }

    @Test
    void budgetLeavesAmmoReserve()
    {
        // Cheapest D-grade shop sword is ~409k, so 600k (budget 480k) affords one.
        GearGuide.GearPick p = GearGuide.recommendWeapon(0, 25, 600_000);
        assertNotNull(p, "600k purse affords a D-grade weapon");
        assertTrue(p.price <= 600_000 * 4 / 5, "only 4/5 of the purse may go to gear");
    }

    @Test
    void deterministicAcrossCalls()
    {
        GearGuide.GearPick a = GearGuide.recommendWeapon(31, 30, 3_000_000); // Dark Fighter
        GearGuide.GearPick b = GearGuide.recommendWeapon(31, 30, 3_000_000);
        assertNotNull(a);
        assertEquals(a.itemId, b.itemId, "same input -> same pick");
        assertEquals(a.price, b.price);
    }

    @Test
    void mysticClassification()
    {
        assertTrue(GearGuide.isMysticClass(10), "Human Mystic");
        assertTrue(GearGuide.isMysticClass(42), "Shillien Oracle");
        assertTrue(GearGuide.isMysticClass(49), "Orc Mystic");
        assertTrue(!GearGuide.isMysticClass(0), "Human Fighter");
        assertTrue(!GearGuide.isMysticClass(7), "Rogue");
        assertTrue(!GearGuide.isMysticClass(48), "Tyrant");
    }
}
