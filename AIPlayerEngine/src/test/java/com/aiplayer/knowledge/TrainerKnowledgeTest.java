package com.aiplayer.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * GK-11 — locks trainers.json (skill-trainer NPC → class mapping + world position)
 * against the real generated extractor output (SkillLearn.xml + point spawns).
 */
class TrainerKnowledgeTest
{
    private static final List<Map<String, Object>> TRAINERS =
        JsonResource.autoObjectList("trainers.json");
    private static final List<Map<String, Object>> CLASSES =
        JsonResource.autoObjectList("classes.json");

    private static int num(Map<?, ?> m, String key)
    {
        return ((Number) m.get(key)).intValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o)
    {
        return (List<Object>) o;
    }

    private static Map<String, Object> trainer(int npcId)
    {
        for (Map<String, Object> r : TRAINERS)
        {
            if (Integer.valueOf(npcId).equals(r.get("id")))
            {
                return r;
            }
        }
        return null;
    }

    @Test
    void allTrainersHaveClassIdsAndWorldLocation()
    {
        assertTrue(TRAINERS.size() >= 100, "trainers: " + TRAINERS.size());
        for (Map<String, Object> r : TRAINERS)
        {
            assertEquals("trainer", r.get("kind"));
            assertTrue(!((List<?>) r.get("classIds")).isEmpty(),
                "trainer has classes: " + r.get("id"));
            Object spawn = r.get("spawn");
            assertNotNull(spawn, "trainer has a spawn: " + r.get("id"));
            Map<?, ?> p = (Map<?, ?>) spawn;
            assertTrue(num(p, "x") >= -204800 && num(p, "x") <= 204800
                    && num(p, "y") >= -262144 && num(p, "y") <= 262144
                    && num(p, "z") >= -16000 && num(p, "z") <= 16000,
                "trainer spawn in world: " + r.get("id"));
        }
    }

    @Test
    void auronTeachesHumanFighterLineAtGludin()
    {
        Map<String, Object> auron = trainer(30010);
        assertNotNull(auron, "Auron exists");
        assertEquals("Auron", auron.get("name"));
        Set<Integer> classes = new HashSet<>();
        for (Object c : list(auron.get("classIds")))
        {
            classes.add(((Number) c).intValue());
        }
        assertTrue(classes.containsAll(java.util.Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
            "Auron teaches the Human Fighter chain (0..9): " + classes);
        Map<?, ?> p = (Map<?, ?>) auron.get("spawn");
        assertEquals(-81861, num(p, "x"));
        assertEquals(149197, num(p, "y"));
        assertEquals(-3125, num(p, "z"));
    }

    @Test
    void zigauntTeachesHumanMysticLine()
    {
        Map<String, Object> z = trainer(30022);
        assertNotNull(z, "Zigaunt exists");
        Set<Integer> classes = new HashSet<>();
        for (Object c : list(z.get("classIds")))
        {
            classes.add(((Number) c).intValue());
        }
        assertTrue(classes.containsAll(java.util.Arrays.asList(10, 15, 16, 17)),
                "Zigaunt teaches the Human Mystic line (10,15,16,17): " + classes);
    }

    @Test
    void classIdsAllExistInClassChains()
    {
        Set<Integer> chainIds = new HashSet<>();
        for (Map<String, Object> c : CLASSES)
        {
            for (Object o : list(c.get("chain")))
            {
                chainIds.add(((Number) ((Map<?, ?>) o).get("classId")).intValue());
            }
        }
        for (Map<String, Object> r : TRAINERS)
        {
            for (Object o : list(r.get("classIds")))
            {
                assertTrue(chainIds.contains(((Number) o).intValue()),
                    "class " + o + " of trainer " + r.get("id") + " is in classes.json chains");
            }
        }
    }

    @Test
    void trainerNamesResolvedFromNpcs()
    {
        for (Map<String, Object> r : TRAINERS)
        {
            assertTrue(!String.valueOf(r.get("name")).isEmpty(),
                "trainer name resolved: " + r.get("id"));
        }
    }

    @Test
    void trainersSpreadAcrossTowns()
    {
        Set<Integer> towns = new HashSet<>();
        for (Map<String, Object> r : TRAINERS)
        {
            Map<?, ?> p = (Map<?, ?>) r.get("spawn");
            // buckets: Talking Island (Human), Elven, Dark Elven, Orc, Dwarven village areas
            long y = num(p, "y");
            if (y > 100000)
            {
                towns.add(1); // human/gludio side
            }
            else if (y > 0)
            {
                towns.add(2); // elven side
            }
            else if (y > -150000)
            {
                towns.add(3); // dark elven / orc-ish
            }
            else
            {
                towns.add(4); // dwarven side
            }
        }
        assertTrue(towns.size() >= 3, "trainers spread across town clusters: " + towns.size());
    }
}