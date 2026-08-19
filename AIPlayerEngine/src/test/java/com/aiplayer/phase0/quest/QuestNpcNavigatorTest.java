package com.aiplayer.phase0.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.movement.HopGate;
import com.aiplayer.phase0.movement.ZoneRouter;
import com.aiplayer.phase0.quest.QuestNpcNavigator.NpcRoute;
import com.aiplayer.phase0.quest.QuestNpcNavigator.NpcTarget;

/**
 * AUDIT 48 STAGE A tests — the quest-NPC navigation planner must:
 * (a) plan hops from a server-acked position to a quest-NPC coordinate, each hop &le;
 *     ZoneRouter.MAX_HOP_DIST (4800u, server-cap safe) and the last hop landing exactly on the NPC;
 * (b) be ack-gated exactly like TIM-001 rozes (HopGate SEND/ADVANCE/RESEND + stuck abandonment);
 * (c) expose nextHop()/done observations.
 * Mirrors ZoneRouterTest conventions for hop destructuring and HopGateTest for ack semantics.
 */
class QuestNpcNavigatorTest
{
    /** Quest 40001 "Spider Silk Collection": step0 TALK to Trader, step1 COLLECT, step2 RETURN to Trader. */
    private static final int QUEST_ID = 40001;
    private static final int NPC_X = -86733;
    private static final int NPC_Y = 242918;
    private static final int NPC_Z = -3720;
    private static final long NOW = 2_000_000L;

    // ================================================================
    // TARGET RESOLUTION FROM EXISTING QUEST/LANDMARK DATA
    // ================================================================

    @Test
    void resolvesQuestNpcLandmarkFromTalkStep()
    {
        NpcTarget t = QuestNpcNavigator.resolveNpcTarget(QUEST_ID, 0); // TALK to Trader
        assertNotNull(t, "quest-NPC landmark resolves from the TALK step");
        assertEquals(NPC_X, t.x);
        assertEquals(NPC_Y, t.y);
        assertEquals(NPC_Z, t.z);
        assertEquals(QUEST_ID, t.questId);
        assertEquals(0, t.stepIndex);
        assertTrue(t.label.contains("Spider Silk Collection"), "label names the quest, got " + t.label);
    }

    @Test
    void resolvesReturnStepAsTurnInNpc()
    {
        NpcTarget t = QuestNpcNavigator.resolveNpcTarget(QUEST_ID, 2); // RETURN to Trader
        assertNotNull(t, "turn-in NPC resolves from the RETURN step");
        assertEquals(NPC_X, t.x);
        assertEquals(NPC_Y, t.y);
    }

    @Test
    void nonNpcStepAndUnknownQuestResolveToNull()
    {
        assertNull(QuestNpcNavigator.resolveNpcTarget(QUEST_ID, 1), "COLLECT step has no quest NPC");
        assertNull(QuestNpcNavigator.resolveNpcTarget(QUEST_ID, 99), "step out of range -> null");
        assertNull(QuestNpcNavigator.resolveNpcTarget(999_999, 0), "unknown quest -> null");
    }

    @Test
    void resolvesActiveQuestTargetFromTrackerState()
    {
        QuestProgressTracker tracker = new QuestProgressTracker("ai_quest_01");
        tracker.startQuest(QUEST_ID);
        NpcTarget t = QuestNpcNavigator.resolveActiveQuestTarget(tracker);
        assertNotNull(t, "active quest's current TALK step resolves to the giver NPC");
        assertEquals(NPC_X, t.x);
        assertEquals(NPC_Y, t.y);
        assertNull(QuestNpcNavigator.resolveActiveQuestTarget(new QuestProgressTracker("ai_quest_empty")),
            "no active quest -> null");
        assertNull(QuestNpcNavigator.resolveActiveQuestTarget(null), "null tracker -> null");
    }

    // ================================================================
    // ROUTE PLANNING + HOP DESTRUCTURING (<= 4800u)
    // ================================================================

    @Test
    void farRouteToNpcDegradesIntoServerAcceptableHopsLandingExactlyOnNpc()
    {
        // NE corner of Talking Island -> Jackson (~56k u): needs many hops.
        NpcTarget target = QuestNpcNavigator.resolveNpcTarget(QUEST_ID, 0);
        NpcRoute r = QuestNpcNavigator.planRoute(-35000, 265000, -3600, target);

        assertNotNull(r);
        assertTrue(r.totalHops() >= 3, "far NPC route splits into >=3 hops, got " + r.totalHops());
        assertEquals(NPC_X, r.destX, "route destination is the NPC x");
        assertEquals(NPC_Y, r.destY, "route destination is the NPC y");

        int[] prev = null;
        int hopsSeen = 0;
        while (r.hasNext())
        {
            int[] hop = r.nextHop();
            if (prev != null)
            {
                double step = Math.hypot(hop[0] - prev[0], hop[1] - prev[1]);
                assertTrue(step <= ZoneRouter.MAX_HOP_DIST + 1, "each hop <= server cap, got " + step);
                assertTrue(step > 0, "no zero-length step");
            }
            prev = hop;
            hopsSeen++;
            r.markHopSent(NOW + hopsSeen * 1_000L);
            assertEquals(HopGate.Action.ADVANCE,
                r.tick(NOW + hopsSeen * 1_000L + 500, true),
                "near-ack advances the route to the next hop");
        }
        assertTrue(hopsSeen >= 3, "far NPC route requires multiple hops, got " + hopsSeen);
        assertEquals(NPC_X, prev[0], "last hop lands exactly on the NPC x");
        assertEquals(NPC_Y, prev[1], "last hop lands exactly on the NPC y");
        assertEquals(NPC_Z, prev[2], "last hop lands exactly on the NPC z");
        assertTrue(r.isDone(), "route is done once the final hop was handed out past the end");
    }

    @Test
    void explicitTargetRouteHopsStayBelowServerCap()
    {
        // Explicit coords input path: NE corner of Talking Island -> Jackson (~56k u).
        NpcRoute r = QuestNpcNavigator.planRoute(-35000, 265000, -3600, NPC_X, NPC_Y, NPC_Z, "npc:30002 Trader");
        assertNotNull(r);
        assertTrue(r.label.startsWith("npc:"), "explicit-target label kept, got " + r.label);

        int[] prev = null;
        while (r.hasNext())
        {
            int[] hop = r.nextHop();
            if (prev != null)
            {
                assertTrue(Math.hypot(hop[0] - prev[0], hop[1] - prev[1]) <= ZoneRouter.MAX_HOP_DIST + 1);
            }
            prev = hop;
            r.markHopSent(NOW);
            assertEquals(HopGate.Action.ADVANCE, r.tick(NOW + 5_000, true),
                "near-ack advances to the next explicit-target hop");
        }
        assertEquals(NPC_X, prev[0], "last explicit-target hop lands exactly on the NPC");
        assertEquals(NPC_Y, prev[1], "last explicit-target hop lands exactly on the NPC");
    }

    @Test
    void planRouteRefusesPreWorldOrigin()
    {
        NpcTarget target = QuestNpcNavigator.resolveNpcTarget(QUEST_ID, 0);
        assertNull(QuestNpcNavigator.planRoute(0, 0, 0, target), "0,0 = not spawned yet -> stay put");
        assertNull(QuestNpcNavigator.planRoute(0, 0, 0, NPC_X, NPC_Y, NPC_Z, "npc"),
            "0,0 explicit-target route refused too");
        assertNull(QuestNpcNavigator.planRoute(-82759, 250149, -3600, (NpcTarget) null),
            "null target -> no route");
    }

    @Test
    void degenerateRouteWhenBotAlreadyAtNpcYieldsNoHopsAndDone()
    {
        NpcRoute r = QuestNpcNavigator.planRoute(NPC_X, NPC_Y, NPC_Z, NPC_X, NPC_Y, NPC_Z, "npc:30002");
        assertNotNull(r);
        assertEquals(0, r.totalHops(), "zero-length route plans zero hops");
        assertNull(r.nextHop(), "no hop when already at the NPC");
        assertTrue(r.isDone(), "route observed done immediately");
        assertFalse(r.hasNext(), "nothing left to dispatch");
    }
// ================================================================
    // ACK-GATING (TIM-001 rozes semantics via HopGate)
    // ================================================================

    @Test
    void freshHopSendsThenAckAdvancesAndCompletesRoute()
    {
        NpcRoute r = QuestNpcNavigator.planRoute(-82759, 250149, -3600, -82759 + 3000, 250149, -3600, "npc");
        assertNotNull(r);

        int[] hop = r.nextHop();
        assertNotNull(hop, "first hop pending");
        assertEquals(HopGate.Action.SEND, r.tick(NOW, false), "never-sent hop -> SEND");
        r.markHopSent(NOW);

        assertNull(r.tick(NOW + 10_000, false), "sent + within ack window -> keep waiting");

        assertEquals(HopGate.Action.ADVANCE, r.tick(NOW + 15_000, true),
            "server acked us near the hop -> ADVANCE");
        assertTrue(r.isDone(), "single-hop route is done once the hop is acked");
        assertNull(r.nextHop(), "no hops remain after completion");
        assertFalse(r.hasNext(), "hasNext false when done");
    }

    @Test
    void ackGatesEveryHopBeforeTheNextIsDispatched()
    {
        // 10k u route -> >=3 hops; each hop is dispatched only after the server acked the previous.
        NpcRoute r = QuestNpcNavigator.planRoute(-82759, 250149, -3600, -72759, 250149, -3600, "npc");
        assertNotNull(r);
        assertTrue(r.totalHops() >= 3, "10k route splits into >=3 hops, got " + r.totalHops());

        int[] prev = null;
        int dispatched = 0;
        while (r.hasNext())
        {
            HopGate.Action act = r.tick(NOW + dispatched * 1_000L, false);
            if (act == HopGate.Action.SEND)
            {
                int[] h = r.nextHop();
                assertNotNull(h, "route hasNext -> nextHop must offer a hop");
                if (prev != null)
                {
                    assertFalse(java.util.Arrays.equals(prev, h), "a hop is never handed out twice");
                    assertEquals(-3600, h[2], "z preserved across hops");
                }
                prev = h;
                dispatched++;
                r.markHopSent(NOW + dispatched * 1_000L);
            }
            else if (act == null)
            {
                // in flight, no ack yet: simulate the server acking us near the pending hop.
                int[] pending = r.pendingHop();
                assertNotNull(pending, "in-flight hop observable via pendingHop()");
                assertEquals(HopGate.Action.ADVANCE, r.tick(NOW + dispatched * 1_000L + 500, true),
                    "near-ack advances without a new SEND");
                prev = pending; // acked hop = our last known position
                if (r.hasNext())
                {
                    int[] next = r.pendingHop();
                    assertNotNull(next, "route advanced to a new pending hop");
                    assertFalse(java.util.Arrays.equals(prev, next),
                        "advance moved to a real new hop, not a re-dispatch");
                    double step = Math.hypot(next[0] - prev[0], next[1] - prev[1]);
                    assertTrue(step > 0 && step <= ZoneRouter.MAX_HOP_DIST + 1,
                        "advance step is real progress within the server cap");
                }
            }
            else
            {
                fail("unexpected action " + act);
            }
        }
        assertTrue(r.isDone(), "route completes after the last hop is acked");
    }

    @Test
    void staleUnackedHopResendsThenRouteIsAbandonedAfterMaxTimeouts()
    {
        NpcRoute r = QuestNpcNavigator.planRoute(-82759, 250149, -3600, -82759 + 3000, 250149, -3600, "npc");
        assertNotNull(r);
        r.nextHop();
        r.markHopSent(NOW);

        // First stale window: RESEND (one timeout) — route survives, hop stays.
        assertEquals(HopGate.Action.RESEND, r.tick(NOW + HopGate.HOP_TIMEOUT_MS + 1, false),
            "stale hop -> RESEND");
        assertFalse(r.isDone(), "one timeout is not enough to abandon (stuck only at MAX_HOP_TIMEOUTS)");
        assertNotNull(r.pendingHop(), "pending hop kept after one RESEND");

        // Second stale window: RESEND again -> hits MAX_HOP_TIMEOUTS -> route abandoned (done).
        r.markHopSent(NOW + HopGate.HOP_TIMEOUT_MS + 1);
        assertEquals(HopGate.Action.RESEND, r.tick(NOW + 2 * HopGate.HOP_TIMEOUT_MS + 2, false),
            "second stale window -> RESEND (and abandonment)");
        assertTrue(r.isDone(), "route observed done after MAX_HOP_TIMEOUTS stale resends (re-plan)");
        assertNull(r.pendingHop(), "abandoned route has no in-flight hop");
        assertNull(r.nextHop(), "abandoned route yields no more hops");
        assertFalse(r.hasNext(), "abandoned route has nothing left");
    }

    @Test
    void reachingNpcExactlyIsObservedAsDoneWithoutExtraHop()
    {
        // Bot is already (near) the NPC: only 1500u away -> single hop; ack it -> done at the NPC.
        NpcRoute r = QuestNpcNavigator.planRoute(NPC_X + 1500, NPC_Y, NPC_Z, NPC_X, NPC_Y, NPC_Z, "npc");
        assertNotNull(r);
        assertEquals(1, r.totalHops(), "1500u reach -> exactly one hop");
        int[] last = r.nextHop();
        assertEquals(NPC_X, last[0], "single hop lands on the NPC");
        r.markHopSent(NOW);
        assertEquals(HopGate.Action.ADVANCE, r.tick(NOW + 5_000, true));
        assertTrue(r.isDone(), "route done exactly when the NPC hop is acked");
    }
}