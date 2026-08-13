package com.aiplayer.phase0.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Audit 46 P0 #2: the fleet's ack-gated hop loop used to be integration-only; now the
 * send/advance/resend decision lives in the pure {@link HopGate} helper and is unit-testable.
 */
class HopGateTest
{
    private static final long NOW = 1_000_000L;

    @Test
    void notSentYieldsSend()
    {
        assertEquals(HopGate.Action.SEND, HopGate.nextAction(NOW, false, 0));
    }

    @Test
    void nearYieldsAdvanceEvenIfNeverSent()
    {
        // near takes priority over never-sent: server acked us at the hop already.
        assertEquals(HopGate.Action.ADVANCE, HopGate.nextAction(NOW, true, 0));
    }

    @Test
    void sentAndNearYieldsAdvance()
    {
        assertEquals(HopGate.Action.ADVANCE, HopGate.nextAction(NOW, true, NOW - 5_000));
    }

    @Test
    void sentButWithinAckWindowWaits()
    {
        // sent, not near, still inside the 45s window -> no action this tick.
        assertNull(HopGate.nextAction(NOW, false, NOW - 10_000));
    }

    @Test
    void sentButStaleYieldsResend()
    {
        // just past the 45s ack window with no server ack -> re-send the hop.
        assertEquals(HopGate.Action.RESEND,
            HopGate.nextAction(NOW, false, NOW - HopGate.HOP_TIMEOUT_MS - 1));
    }

    @Test
    void exactlyAtTimeoutIsNotYetStale()
    {
        // strict ">" matches FleetPlay's 45_000 exclusion: exactly at the window is still waiting.
        assertNull(HopGate.nextAction(NOW, false, NOW - HopGate.HOP_TIMEOUT_MS));
    }
}