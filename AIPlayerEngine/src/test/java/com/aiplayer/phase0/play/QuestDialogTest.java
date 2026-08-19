package com.aiplayer.phase0.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.play.QuestDialogDriver.Objective;
import com.aiplayer.phase0.play.QuestDialogDriver.QuestDialog;

/**
 * QuestDialogDriver (STEP 2) tests — the pure dialog state machine must, given ONLY the bypass
 * links a quest NPC actually displayed, return the ONE next validated command (never re-sending,
 * never fabricating), and must mirror the proven QuestFlowLoop rule:
 *   Script -> quest-name link -> objective completion token -> safe ".htm"/"Quest" fallback.
 * Mirrors QuestGoalPlannerTest conventions (JUnit 5, quest 40001 "Spider Silk Collection").
 */
class QuestDialogTest
{
    /** Registry quest dialog token for 40001, as it appears in the live NpcHtmlMessage bypass links. */
    private static final String QUEST_NAME = "Q00101_SwordOfSolidarity";

    // ================================================================
    // ACCEPT PATH
    // ================================================================

    @Test
    void acceptOpensWindowThenPicksQuestLink()
    {
        QuestDialog def = new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "accept", "");
        String[] links = { "Script", "Quest " + QUEST_NAME + " accept", "Quest " + QUEST_NAME + " declined" };
        Set<String> sent = new HashSet<>();

        // 1. "Script" opens the quest window first.
        assertEquals("Script", QuestDialogDriver.next(links, def, sent));
        sent.add("Script");

        // 2. The link naming our quest, then completes() says the accept side is done.
        String accept = QuestDialogDriver.next(links, def, sent);
        assertEquals("Quest " + QUEST_NAME + " accept", accept);
        assertTrue(QuestDialogDriver.completes(def, accept), "accept token sent -> accept side complete");
    }

    @Test
    void acceptPrefersCompletionTokenOverFallback()
    {
        // accept token present and on screen -> chosen over a generic ".htm" fallback.
        QuestDialog def = new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "startQuest__accept", "");
        String[] links = { "Quest something.htm", "startQuest__accept_x100" };
        String next = QuestDialogDriver.next(links, def, new HashSet<>());
        assertEquals("startQuest__accept_x100", next);
    }

    // ================================================================
    // TURN-IN PATH
    // ================================================================

    @Test
    void turnInUsesTurnInTokenAndCompletes()
    {
        QuestDialog def = new QuestDialog(40001, QUEST_NAME, Objective.TURN_IN, "", "finish");
        String[] links = { "Quest " + QUEST_NAME + " finish", "Quest " + QUEST_NAME + " continue" };
        String next = QuestDialogDriver.next(links, def, new HashSet<>());
        assertEquals("Quest " + QUEST_NAME + " finish", next, "turn-in token picked over continue");
        assertTrue(QuestDialogDriver.completes(def, next), "finish sent -> turn-in side complete");
    }

    // ================================================================
    // ALREADY-SENT / NOTHING-NEW
    // ================================================================

    @Test
    void neverReSendsALinkThatWasAlreadySent()
    {
        QuestDialog def = new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "accept", "");
        String[] links = { "Script", "Quest " + QUEST_NAME + " accept" };
        Set<String> sent = new HashSet<>();
        sent.add("Script");       // already sent the window open
        sent.add("Quest " + QUEST_NAME + " accept"); // and already accepted

        assertEquals("", QuestDialogDriver.next(links, def, sent), "nothing new -> pause");
    }

    @Test
    void multiStepMenuThenQuestListThenAccept()
    {
        // S3-T06: some givers (e.g. gatekeeper ROXXY) present a MENU first; the driver must drill the
        // "Quest" menu link, then accept from the quest list.
        QuestDialog def = new QuestDialog(6, "Q00006_StepIntoTheFuture", Objective.ACCEPT, "accept", "");
        String[] menu = { "teleport Somewhere", "Quest" };
        Set<String> sent = new HashSet<>();
        assertEquals("Quest", QuestDialogDriver.next(menu, def, sent), "drill into the quest menu");
        sent.add("Quest");

        String[] questList = { "Q00006_StepIntoTheFuture", "SomeOtherQuest" };
        assertEquals("Q00006_StepIntoTheFuture", QuestDialogDriver.next(questList, def, sent),
            "accept link named by the quest");
    }

    @Test
    void emptyLinksReturnsNoCommand()
    {
        QuestDialog def = new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "accept", "");
        assertEquals("", QuestDialogDriver.next(new String[0], def, new HashSet<>()));
        assertEquals("", QuestDialogDriver.next(null, def, new HashSet<>()));
    }

    @Test
    void missingQuestNameFallsBackToSafeQuestLink()
    {
        // No quest name configured: still safely opens via a not-yet-sent ".htm"/"Quest" link.
        QuestDialog def = new QuestDialog(0, "", Objective.ACCEPT, "", "");
        String[] links = { "Quest accept_x1", "unrelated other.htm" };
        String next = QuestDialogDriver.next(links, def, new HashSet<>());
        assertEquals("Quest accept_x1", next, "safe Quest/.htm fallback chosen");
    }

    @Test
    void safeFallbackPicksADistinctQuestLinkWhenNameAndTokenMiss()
    {
        // A link for some OTHER quest — still a safe "Quest" link — is the documented step-4 fallback
        // when neither our quest name nor our completion token is on screen. Never fabricates content.
        QuestDialog def = new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "startQ40001_accept", "");
        String[] links = { "Quest something_else accept", "other.htm" };
        String next = QuestDialogDriver.next(links, def, new HashSet<>());
        assertEquals("Quest something_else accept", next, "safe Quest/.htm fallback when ours is not shown");
        assertFalse(QuestDialogDriver.completes(def, "Quest something_else accept"),
            "fallback is not our accept token");
    }

    @Test
    void completesRequiresMatchingTokenOnly()
    {
        QuestDialog accept = new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "startX", "");
        assertFalse(QuestDialogDriver.completes(accept, "Script"), "opening window is not accepting");
        assertFalse(QuestDialogDriver.completes(null, "whatever"), "null def never completes");

        QuestDialog turnIn = new QuestDialog(40001, QUEST_NAME, Objective.TURN_IN, "", "req_finish_quest");
        assertTrue(QuestDialogDriver.completes(turnIn, "Quest x req_finish_quest_done"));
        assertFalse(QuestDialogDriver.completes(turnIn, "Quest is finished"), "mere mention is not a token");
    }
}
