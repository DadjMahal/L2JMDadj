package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.QuestDialogDriver.QuestDialog;
import com.aiplayer.behavior.QuestDialogDriver.Objective;
import com.aiplayer.behavior.QuestDialogSession.Action;
import com.aiplayer.behavior.QuestDialogSession.Result;

/**
 * QuestDialogSession tests — the EB-01 extracted per-session STEP-2 driving machine. Given the
 * exact live signals the fleet loop sees (giver's dialog on screen, a NEW NpcHtmlMessage, its
 * bypass links, the accept/turn-in definition, time), it returns exactly one next step:
 * OPEN / CLICK_GIVER / SEND_BYPASS / WAIT — honoring the S3-T02 stale re-click window and the
 * never-re-send set across ticks. No IO, no packets, no threads.
 */
class QuestDialogSessionTest
{
    private static final String QUEST_NAME = "Q00101_SwordOfSolidarity";
    private static final long RECLICK_MS = 10_000L;

    private static QuestDialog acceptDef()
    {
        return new QuestDialog(40001, QUEST_NAME, Objective.ACCEPT, "accept", "");
    }

    private static QuestDialog turnInDef()
    {
        return new QuestDialog(40001, QUEST_NAME, Objective.TURN_IN, "", "finish");
    }
// ================================================================
    // OPENING
    // ================================================================

    @Test
    void clicksGiverWhenNotTrackedOnScreen()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        Result r = s.step(true, false, null, null, acceptDef(), 1_000L);
        assertEquals(Action.CLICK_GIVER, r.action);
        assertFalse(s.isOpen());
    }

    @Test
    void opensWhenGiverDialogAlreadyOnScreen()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        Result r = s.step(true, true, null, null, acceptDef(), 1_000L);
        assertEquals(Action.OPEN, r.action);
        assertTrue(s.isOpen());
    }

    @Test
    void waitsWhileGiverNotYetTracked()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        assertNull(s.step(false, false, null, null, acceptDef(), 1_000L));
    }

    // ================================================================
    // DRIVING (accept chain)
    // ================================================================

    @Test
    void acceptChainSendsScriptThenCompletingLink()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        s.step(true, true, null, null, acceptDef(), 1_000L); // open

        // 1. "Script" opens the quest window first.
        Result r1 = s.step(true, true,
            "<html><a action=\"bypass -h Script\">...</a></html>",
            new String[] { "Script", "Quest " + QUEST_NAME + " accept", "Quest " + QUEST_NAME + " declined" },
            acceptDef(), 2_000L);
        assertEquals(Action.SEND_BYPASS, r1.action);
        assertEquals("Script", r1.bypass);
        assertFalse(r1.done);

        // 2. The link naming our quest carries the accept token → completes the dialog.
        Result r2 = s.step(true, true,
            "<html>quest window</html>",
            new String[] { "Quest " + QUEST_NAME + " accept", "Quest " + QUEST_NAME + " declined" },
            acceptDef(), 3_000L);
        assertEquals(Action.SEND_BYPASS, r2.action);
        assertEquals("Quest " + QUEST_NAME + " accept", r2.bypass);
        assertTrue(r2.done, "accept-token link completes the dialog");
        assertFalse(s.isOpen(), "completed dialog closes the session");
    }

    @Test
    void neverReSendsAlreadySentLink()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        s.step(true, true, null, null, acceptDef(), 1_000L);

        Result r1 = s.step(true, true, "<h>1</h>", new String[] { "Script", "Quest x" }, acceptDef(), 2_000L);
        assertEquals("Script", r1.bypass);

        // Same dialog content re-sent: session must WAIT (no re-send) until new content arrives.
        Result r2 = s.step(true, true, "<h>1</h>", new String[] { "Script", "Quest x" }, acceptDef(), 15_000L);
        assertEquals(Action.WAIT, r2.action);
    }

    @Test
    void holdsOldHtmlUntilNewDialogArrives()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        s.step(true, true, null, null, acceptDef(), 1_000L);

        Result r1 = s.step(true, true, "<h>same</h>", new String[] { "Script" }, acceptDef(), 2_000L);
        assertEquals(Action.SEND_BYPASS, r1.action);

        // Same html again → nothing new → WAIT.
        Result r2 = s.step(true, true, "<h>same</h>", new String[] { "Script" }, acceptDef(), 3_000L);
        assertEquals(Action.WAIT, r2.action);
    }

    // ================================================================
    // STALE RE-CLICK (S3-T02)
    // ================================================================

    @Test
    void staleDialogClearsAndReturnsWaitUntilReclickable()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        s.step(true, true, null, null, acceptDef(), 1_000L);
        // No new html for > re-click window.
        Result r = s.step(true, true, null, null, acceptDef(), 1_000L + RECLICK_MS + 1L);
        assertEquals(Action.WAIT, r.action);
        assertFalse(s.isOpen(), "stale dialog must reset the open state so the loop can re-click");
    }

    // ================================================================
    // TURN-IN path
    // ================================================================

    @Test
    void turnInPicksCompletionTokenAndCloses()
    {
        QuestDialogSession s = new QuestDialogSession(RECLICK_MS);
        s.step(true, true, null, null, turnInDef(), 1_000L);

        Result r = s.step(true, true, "<h>done</h>",
            new String[] { "Quest " + QUEST_NAME + " finish", "Quest " + QUEST_NAME },
            turnInDef(), 2_000L);
        assertEquals(Action.SEND_BYPASS, r.action);
        assertTrue(r.done);
        assertFalse(s.isOpen());
    }
}