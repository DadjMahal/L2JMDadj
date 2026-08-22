package com.aiplayer.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * GK-6 — locks the KnowledgeBase loader + queries against the real generated JSON:
 * at least 10 distinct queries per the audit acceptance.
 */
class KnowledgeBaseTest
{
    private static final KnowledgeBase KB = KnowledgeBase.getInstance();

    @Test
    void loadedCountsSanity()
    {
        assertTrue(KB.npcCount() >= 5000, "npcs loaded: " + KB.npcCount());
        assertTrue(KB.itemCount() >= 5000, "items loaded: " + KB.itemCount());
        assertTrue(KB.questCount() >= 300, "quests loaded: " + KB.questCount());
    }

    @Test
    void loadIsSubSecond()
    {
        assertTrue(KB.loadMillis() < 1000, "load took " + KB.loadMillis() + "ms");
    }

    @Test
    void itemById()
    {
        KnowledgeBase.Item i = KB.item(1);
        assertNotNull(i);
        assertEquals(1, i.id);
        assertEquals("Short Sword", i.name);
        assertTrue(i.price > 0);
    }

    @Test
    void itemUnknownIsNull()
    {
        assertEquals(null, KB.item(Integer.MAX_VALUE));
    }

    @Test
    void npcByIdWithSpawnsAndDrops()
    {
        KnowledgeBase.Npc n = KB.npc(20223); // Mandragora Sprout (validated in GK-2)
        assertNotNull(n);
        assertTrue(n.drops.size() >= 20, "has drops: " + n.drops.size());
        assertTrue(n.spawns.size() >= 1, "has spawns");
        assertEquals("Mandragora Sprout", n.name);
    }

    @Test
    void droppersOfKnownDropItem()
    {
        // item 9142 is dropped by npc 13031 (verified in GK-2 spot-checks).
        List<KnowledgeBase.Npc> droppers = KB.droppersOf(9142);
        assertFalse(droppers.isEmpty());
        assertTrue(droppers.stream().anyMatch(n -> n.id == 13031), "13031 is a dropper of 9142");
    }

    @Test
    void questSixMatchesKnownGood()
    {
        KnowledgeBase.Quest q = KB.quest(6);
        assertNotNull(q);
        assertEquals(30006, q.startNpc.intValue()); // ROXXY
        assertTrue(q.minLevel != null && q.minLevel <= 3, "minLevel <= 3: " + q.minLevel);
        assertTrue(q.talkNpcs.contains(30033) && q.talkNpcs.contains(30311));
    }

    @Test
    void questsForLevelFilters()
    {
        List<KnowledgeBase.Quest> byLevel = KB.questsFor(10, null);
        assertFalse(byLevel.isEmpty());
        for (KnowledgeBase.Quest q : byLevel)
        {
            assertTrue(q.minLevel == null || q.minLevel <= 10, "fits level 10: " + q.id);
            assertNotNull(q.startNpc, "has a giver: " + q.id);
        }
    }

    @Test
    void questsForDoesNotLeakReviewQuests()
    {
        for (KnowledgeBase.Quest q : KB.questsFor(80, null))
        {
            assertFalse(q.needsReview, "review quest not offered: " + q.id);
        }
    }

    @Test
    void skillLadderByClass()
    {
        List<KnowledgeBase.Skill> ladder = KB.skillLadder(0); // Human Fighter
        assertFalse(ladder.isEmpty(), "class ladder non-empty");
        assertTrue(ladder.stream().allMatch(s -> s.skillLevel >= 1));
    }

    @Test
    void skillLadderUnknownClassEmpty()
    {
        assertTrue(KB.skillLadder(99999).isEmpty());
    }

    @Test
    void npcSpawnCoordsInWorldBounds()
    {
        for (KnowledgeBase.Npc n : java.util.Collections.singletonList(KB.npc(20223)))
        {
            for (KnowledgeBase.Spawn s : n.spawns)
            {
                assertTrue(Math.abs(s.x) < 200000 && Math.abs(s.y) < 270000, "spawn in bounds");
            }
        }
    }

    // ------------------------------------------------------------------
    // GK-8: classes.json + chains.json + shops.json loaders
    // ------------------------------------------------------------------

    @Test
    void classInfoByKnownId()
    {
        KnowledgeBase.ClassInfo c = KB.classInfo(0); // Human Fighter, base of its own tree
        assertNotNull(c);
        assertEquals("Human Fighter", c.name);
        assertEquals(0, c.tier);
        assertEquals(0, c.baseClassId);
    }

    @Test
    void classInfoCarriesItsBase()
    {
        KnowledgeBase.ClassInfo c = KB.classInfo(42); // Shillien Oracle, tier 1 of base 38
        assertNotNull(c);
        assertEquals(38, c.baseClassId);
        assertTrue(c.tier >= 1);
    }

    @Test
    void classInfoUnknownIsNull()
    {
        assertEquals(null, KB.classInfo(99999));
    }

    @Test
    void classTreeHoldsTiersOfBase()
    {
        List<KnowledgeBase.ClassInfo> tree = KB.classTree(0); // Human Fighter tree
        assertTrue(tree.size() >= 10, "tier tree has all branches: " + tree.size());
        assertTrue(tree.stream().allMatch(c -> c.baseClassId == 0), "tree rows belong to base 0");
        assertTrue(tree.stream().anyMatch(c -> c.name.equals("Gladiator")));
    }

    @Test
    void chainStepsIncludeFirstClassMilestone()
    {
        List<KnowledgeBase.ChainStep> steps = KB.chainSteps(0); // Human Fighter zero->hero chain
        assertFalse(steps.isEmpty());
        boolean firstClass = false;
        for (KnowledgeBase.ChainStep s : steps)
        {
            if ("firstClass".equals(s.kind))
            {
                assertEquals(20, s.level, "first-class transfer at L20");
                assertTrue(s.questId > 0, "milestone carries its quest");
                firstClass = true;
            }
        }
        assertTrue(firstClass, "chain has a firstClass step");
    }

    @Test
    void chainStepsUnknownBaseEmpty()
    {
        assertTrue(KB.chainSteps(99999).isEmpty());
    }

    @Test
    void shopAvailabilityKnownItems()
    {
        assertTrue(KB.isSoldInShop(1835), "soulshots are buylist goods");
        assertFalse(KB.isSoldInShop(Integer.MAX_VALUE), "unknown id is not sold");
    }

    @Test
    void allItemsMatchesItemCount()
    {
        assertEquals(KB.itemCount(), KB.allItems().size(), "allItems exposes every loaded item");
    }
}