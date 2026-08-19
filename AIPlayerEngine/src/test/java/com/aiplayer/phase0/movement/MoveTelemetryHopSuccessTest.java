package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** S5-T06: hop-persistence telemetry — the metric that surfaces the solo far-hop problem live. */
class MoveTelemetryHopSuccessTest
{
    @Test
    void tracksHopSuccessPercentage()
    {
        MoveTelemetry t = MoveTelemetry.getInstance();
        String acct = "hop_metric_" + System.nanoTime(); // unique -> isolated from other tests
        assertEquals(100, t.hopSuccessPct(acct), "no data yet = assume healthy");
        t.recordHopResult(acct, true);
        t.recordHopResult(acct, true);
        t.recordHopResult(acct, false);
        assertEquals(66, t.hopSuccessPct(acct), "2 of 3 hops acked = 66%");
        t.recordHopResult(acct, false);
        assertEquals(50, t.hopSuccessPct(acct), "2 of 4 hops acked = 50%");
    }
}