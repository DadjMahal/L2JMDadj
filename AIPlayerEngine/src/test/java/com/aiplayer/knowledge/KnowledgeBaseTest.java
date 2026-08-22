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
}