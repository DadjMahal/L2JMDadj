package com.aiplayer.behavior.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.quest.QuestObjectiveParser.Parsed;
import com.aiplayer.behavior.quest.QuestObjectiveParser.StepType;

/**
 * S3-T02 — locks extracting the "next objective from the live server" (dialog HTML + journal
 * state) into a structured objectives value. Fixtures are real text from the datapack
 * Q00101_SwordOfSolidarity html plus live-format count strings.
 */
class QuestObjectiveParserTest
{
    // Real 30008-04.htm — talk step (deliver letter to Blacksmith Altran).
    private static final String REAL_TALK_HTML =
        "Grand Master Roien:<br1>Please <font color=\"LEVEL\">deliver</font> my letter to "
            + "Blacksmith Altran... You can find him at the village forge.";

    // Real 30008-05.htm — talk step (deliver done).
    private static final String REAL_TALK_2_HTML = "Grand Master Roien: What wonderful news! Take this to Altran.";

    // Real 30283-02.htm — prose with no direct action keyword (server is vague about Keltirs).
    private static final String REAL_VAGUE_HTML =
        "Blacksmith Altran: the blade was broken into two parts, and they are probably somewhere "
            + "in the Elven Ruins...";

    // Synthetic kill-count format the live server can send mid-quest.
    private static final String KILL_COUNT_HTML = "Kill Keltirs (3/10)";

    // ================================================================

    @Test
    void parsesRealTalkObjective()
    {
        Parsed p = QuestObjectiveParser.parse(20004, 1, REAL_TALK_HTML);
        assertNotNull(p);
        assertEquals(20004, p.questId);
        assertEquals(1, p.state);
        assertEquals(StepType.TALK, p.step, "\"deliver ... to\" is a TALK step");
        assertTrue(p.text.contains("deliver my letter"), p.text);
    }

    @Test
    void parsesTakeToObjective()
    {
        Parsed p = QuestObjectiveParser.parse(20004, 2, REAL_TALK_2_HTML);
        assertNotNull(p);
        assertEquals(StepType.TALK, p.step, "\"take this to\" is a TALK step");
    }

    @Test
    void vagueProseKnownUnknownNotFabricated()
    {
        Parsed p = QuestObjectiveParser.parse(20004, 1, REAL_VAGUE_HTML);
        assertNotNull(p);
        assertEquals(StepType.UNKNOWN, p.step, "prose without an action keyword -> UNKNOWN, never invent");
        assertTrue(p.text.contains("Elven Ruins"), p.text);
    }

    @Test
    void detectsKillCounterObjective()
    {
        Parsed p = QuestObjectiveParser.parse(6, 1, KILL_COUNT_HTML);
        assertEquals(StepType.KILL, p.step);
        assertTrue(p.text.contains("(3/10)"), "counter text preserved: " + p.text);
    }

    @Test
    void detectsCollectObjective()
    {
        Parsed p = QuestObjectiveParser.parse(6, 2, "Find the broken blade pieces (2/5)");
        assertNotNull(p);
        assertEquals(StepType.COLLECT, p.step, "\"find ... pieces\" -> COLLECT");
    }

    @Test
    void killWinsOverCollectWhenBothPresent()
    {
        assertEquals(StepType.KILL,
            QuestObjectiveParser.parse(6, 1, "Kill and collect the remains (1/4)").step);
    }

    @Test
    void stripsTagsAndEntities()
    {
        String html = "<font>&nbsp;&nbsp;Take this <b>letter</b> to Altran&nbsp;</font>";
        assertNotNull(QuestObjectiveParser.parse(6, 1, html).text);
        assertEquals("Take this letter to Altran",
            QuestObjectiveParser.toPlainText(html));
    }

    @Test
    void nullOrBlankHtmlYieldsNoObjective()
    {
        assertNull(QuestObjectiveParser.parse(6, 1, null));
        assertNull(QuestObjectiveParser.parse(6, 1, "   "));
        assertNull(QuestObjectiveParser.parse(6, 1, "<br><br>"));
    }

    @Test
    void toStringIsDashboardFriendly()
    {
        Parsed p = QuestObjectiveParser.parse(6, 1, KILL_COUNT_HTML);
        assertNotNull(p);
        String s = p.toString();
        assertTrue(s.startsWith("q6[s1] KILL:"), s);
    }
}