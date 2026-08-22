package com.aiplayer.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * GK-10 — locks dialog.json (per-page NPC talk graph from quest html) against the real
 * generated extractor output, parsed with the engine's dependency-free JsonResource.
 */
class DialogKnowledgeTest
{
    private static final List<Map<String, Object>> DIALOG = JsonResource.autoObjectList("dialog.json");

    private static int count(String kind)
    {
        int n = 0;
        for (Map<String, Object> r : DIALOG)
        {
            if (kind.equals(r.get("kind")))
            {
                n++;
            }
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o)
    {
        return (List<Map<String, Object>>) o;
    }

    private static int num(Map<?, ?> m, String key)
    {
        return ((Number) m.get(key)).intValue();
    }

    @Test
    void bothKindsPresent()
    {
        assertTrue(count("questDialog") >= 300, "questDialogs: " + count("questDialog"));
        assertTrue(count("dialogPage") >= 8000, "dialogPages: " + count("dialogPage"));
    }

    @Test
    void questSixHasGiverAndTalkGraph()
    {
        Map<String, Object> summary = null;
        for (Map<String, Object> r : DIALOG)
        {
            if ("questDialog".equals(r.get("kind")) && Integer.valueOf(6).equals(r.get("id")))
            {
                summary = r;
                break;
            }
        }
        assertNotNull(summary, "quest 6 summary exists");
        assertEquals(30006, num(summary, "startNpc"), "giver is Roxxy");
        assertEquals("Q00006_StepIntoTheFuture", summary.get("quest"));
        assertTrue(((List<?>) summary.get("startPages")).contains("30006-01.htm"));
        boolean hasBaulro = false;
        for (Map<?, ?> npc : list(summary.get("npcPages")))
        {
            if (num(npc, "npcId") == 30311)
            {
                hasBaulro = true;
            }
        }
        assertTrue(hasBaulro, "talk graph includes 30311 (Baulro)");
    }

    @Test
    void roxxyPageFiveLinksToPageSix()
    {
        Map<String, Object> page = null;
        for (Map<String, Object> r : DIALOG)
        {
            if ("6/30006-05.htm".equals(r.get("id")))
            {
                page = r;
                break;
            }
        }
        assertNotNull(page, "quest 6 page 30006-05 exists");
        assertTrue(((List<?>) page.get("links")).size() >= 1, "page has a link");
        Map<?, ?> link = list(page.get("links")).get(0);
        assertEquals("script", link.get("kind"));
        assertEquals("Q00006_StepIntoTheFuture", link.get("script"));
        assertEquals("30006-06.htm", link.get("target"));
        assertEquals(30006, num(link, "targetNpc"));
    }

    @Test
    void firstPageIsLowestStepForItsNpc()
    {
        // page names repeat across quests, so per-(questId, npcId) min step
        java.util.Map<String, Integer> minStep = new java.util.HashMap<>();
        for (Map<String, Object> r : DIALOG)
        {
            if ("dialogPage".equals(r.get("kind"))
                    && r.get("npcId") instanceof Integer
                    && r.get("step") instanceof Integer)
            {
                String key = r.get("questId") + "@" + r.get("npcId");
                minStep.merge(key, num(r, "step"), Math::min);
            }
        }
        for (Map<String, Object> r : DIALOG)
        {
            if ("dialogPage".equals(r.get("kind")) && Boolean.TRUE.equals(r.get("isFirstPage")))
            {
                String key = r.get("questId") + "@" + r.get("npcId");
                assertEquals(minStep.get(key).intValue(), num(r, "step"),
                    "first page has the lowest step for " + key + ": " + r.get("page"));
            }
        }
    }

    @Test
    void terminalPagesHaveNoTargetLinks()
    {
        for (Map<String, Object> r : DIALOG)
        {
            if (!"dialogPage".equals(r.get("kind")) || !Boolean.TRUE.equals(r.get("isTerminal")))
            {
                continue;
            }
            for (Map<?, ?> link : list(r.get("links")))
            {
                assertEquals(null, link.get("target"), "terminal page has no target: " + r.get("id"));
            }
        }
    }

    @Test
    void questSixTurnInCandidatesAreRealPages()
    {
        Map<String, Object> summary = null;
        for (Map<String, Object> r : DIALOG)
        {
            if ("questDialog".equals(r.get("kind")) && Integer.valueOf(6).equals(r.get("id")))
            {
                summary = r;
                break;
            }
        }
        assertNotNull(summary);
        List<Map<String, Object>> candidates = list(summary.get("turnInCandidates"));
        assertTrue(!candidates.isEmpty(), "quest 6 has turn-in candidates");
        for (Map<String, Object> c : candidates)
        {
            String expectId = 6 + "/" + c.get("page");
            boolean found = false;
            for (Map<String, Object> r : DIALOG)
            {
                if (expectId.equals(r.get("id")))
                {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "candidate page exists in the index: " + expectId);
        }
    }

    @Test
    void linkKindsAreClosedSet()
    {
        for (Map<String, Object> r : DIALOG)
        {
            if (!"dialogPage".equals(r.get("kind")))
            {
                continue;
            }
            for (Map<?, ?> link : list(r.get("links")))
            {
                String kind = (String) link.get("kind");
                assertTrue(kind.equals("script") || kind.equals("token")
                        || kind.equals("npc") || kind.equals("other"),
                    "link kind in closed set: " + kind + " @" + r.get("id"));
            }
        }
    }
}