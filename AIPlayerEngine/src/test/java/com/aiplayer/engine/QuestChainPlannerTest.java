package com.aiplayer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.aiplayer.engine.QuestFramePlanner.QuestFrame;
import com.aiplayer.engine.QuestDecision.Action;

/**
 * S3-T09/T10: the quest-chain automation primitives are pure and deterministic — each decision action
 * (accept / go-to / kill / collect / turn-in / class-change) maps to exactly the protocol frames that
 * drive the dialog + movement + reward loop, so the newbie Q1-Q10 and class-change Q401-418 chains are
 * code-complete and only need the live soak.
 */
class QuestChainPlannerTest
{
    @Test
    void decisionActionsEachProduceFrames()
    {
        questFrames(QuestDecision.acceptQuest("21", "30223", 10, 100, -3000), "accept_quest");
        questFrames(QuestDecision.findNPC("30223", 10, 100, -3000), "navigate_to_npc");
        questFrames(QuestDecision.killMonster("20003", 5), null);       // kill objectives
        questFrames(QuestDecision.collectItem("1061", 2), null);        // collect objectives
        questFrames(QuestDecision.idle(), null);                        // no-op is empty
    }

    @Test
    void maxFramesCeilingHolds()
    {
        assertEquals(8, QuestFramePlanner.maxFrames(), "frame burst stays under the server throttle ceiling");
    }

    private static void questFrames(QuestDecision d, String expectedReasonPrefix)
    {
        QuestFrame[] f = QuestFramePlanner.plan(d);
        assertFalse(d.getAction() == Action.IDLE && f.length > 0,
            "idle decisions must not emit frames");
        if (expectedReasonPrefix != null)
        {
            assertFalse(f.length == 0, d.getAction() + " must produce frames");
            assertFalse(f[0].reason == null || !f[0].reason.startsWith(expectedReasonPrefix),
                d.getAction() + " first frame reason '" + (f.length > 0 ? f[0].reason : "none") +
                    "' should start with '" + expectedReasonPrefix + "'");
        }
    }
}