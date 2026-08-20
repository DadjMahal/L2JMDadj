package com.aiplayer.behavior.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * TIM-001 evidence-harness tests: MoveTelemetry must count real MoveToLocation frames and
 * server-acked positions, detect degenerate destinations (H2) and organic XP gain (H5), and
 * ignore pre-in-world (0,0) samples.
 */
class MoveTelemetryTest
{
    @Test
    void recordsSentMovesAndDegenerateDetection()
    {
        MoveTelemetry t = MoveTelemetry.getInstance();
        // "reset" via fresh instance is not exposed to public ctor; use the singleton but reset it.
        t.reset();

        t.recordMove("botA", 0, 0, 0, 3000, 4000, 0, "farm:X", "test far");
        t.recordMove("botA", 10, 10, 0, 20, 20, 0, "far-point", "degenerate-ish");
        t.recordMove("botB", 0, 0, 0, 5000, 0, 0, "farm:Y", "test far");

        assertEquals(2, t.moveCount("botA"));
        assertEquals(1, t.moveCount("botB"));
        assertTrue(t.totalMoveDistance("botA") >= 5000.0, "2 moves must sum >= 5000 (3000+4000 leg + 14 leg)");
        // Second botA move is length ~14 units < DEGENERATE_DIST (500) -> flagged.
        assertEquals(1, t.degenerateMoveCount("botA"));
        assertEquals(0, t.degenerateMoveCount("botB"));
    }

    @Test
    void serverPositionAcksDriveH1AndIgnoresPreWorldZeros()
    {
        MoveTelemetry t = MoveTelemetry.getInstance();
        t.reset();

        // pre-in-world (0,0) must be skipped -> consecutive real acks sum to the moved distance.
        t.recordPosition("botA", 0, 0, 0, 0);
        t.recordPosition("botA", 1000, 0, 0, 1400000);
        t.recordPosition("botA", 1000, 3000, 0, 1400010);
        t.recordPosition("botA", 1000, 6000, 0, 1400050);

        assertEquals(3, t.positionSampleCount("botA"), "only non-zero samples count");
        assertTrue(t.movedLast(60_000, "botA") >= 6000.0, "server-acked path must sum to 6000 within window");
        assertTrue(t.totalMoved("botA") >= 6000.0);
    }

    @Test
    void organicExpGainH5()
    {
        MoveTelemetry t = MoveTelemetry.getInstance();
        t.reset();

        t.recordPosition("botA", 100, 100, 0, 1400000); // seeded exp at login
        t.recordPosition("botA", 110, 105, 0, 1400500); // organic +500 after some kills
        t.recordPosition("botA", 120, 110, 0, 1401200); // organic +1200 total

        assertEquals(1200L, t.expGained("botA"), "XP gained = last-first sample");
    }

    @Test
    void reportContainsEvidenceKeys()
    {
        MoveTelemetry t = MoveTelemetry.getInstance();
        t.reset();
        t.recordMove("botA", 0, 0, 0, 3000, 4000, 0, "farm:X", "t");
        t.recordPosition("botA", 100, 100, 0, 5);
        t.recordPosition("botA", 500, 100, 0, 6);

        String r = t.report();
        assertTrue(r.contains("EVIDENCE-H1"), "H1 key must be present");
        assertTrue(r.contains("EVIDENCE-H2"), "H2 key must be present");
        assertTrue(r.contains("EVIDENCE-H5"), "H5 key must be present");
    }

    @Test
    void talliesMovesSentVsServerMovedAcrossDegradedMultiHopRoute()
    {
        // Audit 46 P0 #1 — telemetry honesty on a degraded multi-hop route:
        // 5 hops are sent, the server ack-walks only 3 -> report must show 5 sent,
        // 3 server-moved hops worth of distance, and 2 degraded (sent but unacked).
        MoveTelemetry t = MoveTelemetry.getInstance();
        t.reset();
        String bot = "botA";

        // 5 planned hops of exactly 4800u each (none degenerate); origin is non-zero so
        // the first server sample is never mistaken for the pre-in-world (0,0) skip.
        int[][] hops = {
            {1000, 0, 5800, 0},
            {5800, 0, 10600, 0},
            {10600, 0, 15400, 0},
            {15400, 0, 20200, 0},
            {20200, 0, 25000, 0},
        };
        for (int[] h : hops)
        {
            t.recordMove(bot, h[0], h[1], 0, h[2], h[3], 0, "farm:X", "multi-hop");
        }
        assertEquals(5, t.moveCount(bot), "movesSent: all 5 hops recorded");

        // Server acked position only through the first 3 hops (the route degraded: 2 hops unacked).
        t.recordPosition(bot, 1000, 0, 0, 1400000);
        t.recordPosition(bot, 5800, 0, 0, 1400001);
        t.recordPosition(bot, 10600, 0, 0, 1400002);
        t.recordPosition(bot, 15400, 0, 0, 1400003);

        // serverMoved must reflect exactly the 3 acked hops (3 x 4800 = 14400u),
        // and must NOT include the 2 unacked hops.
        assertEquals(14400.0, t.totalMoved(bot), 0.001, "serverMoved = 3 acked hops x 4800u");

        // 5 sent, 3 server-moved -> 2 degraded hops (sent but never acked).
        assertEquals(2, t.moveCount(bot) - 3, "2 of 5 hops were never acked -> degraded");

        // Honesty in the paste-able report too: it states the 5 sent and the acked distance.
        String r = t.report();
        assertTrue(r.contains("movesSent=5"), "report must show movesSent=5, got:\n" + r);
        assertTrue(r.contains("serverMoved=14400"), "report must show serverMoved=14400 u, got:\n" + r);
    }
}