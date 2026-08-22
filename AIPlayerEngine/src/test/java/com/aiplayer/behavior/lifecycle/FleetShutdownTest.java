package com.aiplayer.behavior.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * EB-10 — locks the fleet drain state machine: idempotent request, terminal transitions,
 * crash handling, and the resume semantics (graceful = resume fully, crash = guarded).
 */
class FleetShutdownTest
{
    @Test
    void idempotentDrainKeepsFirstReason()
    {
        FleetShutdown fs = FleetShutdown.idle()
            .requestDrain("SIGTERM")
            .requestDrain("second request");
        assertEquals(FleetShutdown.Phase.DRAINING, fs.phase);
        assertEquals("SIGTERM", fs.reason);
        assertFalse(fs.resumeFully(), "still draining — not a clean resume state");
    }

    @Test
    void completeDrainReachesGraceful()
    {
        FleetShutdown fs = FleetShutdown.idle().requestDrain("keep_alive").completeDrain();
        assertEquals(FleetShutdown.Phase.DRAINED, fs.phase);
        assertEquals("GRACEFUL", fs.exitLabel());
        assertTrue(fs.resumeFully(), "clean drain -> next boot resumes fully");
    }

    @Test
    void completeDrainWithoutRequestIsNoOp()
    {
        FleetShutdown fs = FleetShutdown.idle().completeDrain();
        assertEquals(FleetShutdown.Phase.IDLE, fs.phase);
        assertEquals("NONE", fs.exitLabel());
    }

    @Test
    void crashIsGuardedResume()
    {
        FleetShutdown fs = FleetShutdown.idle().noteCrash("OOM");
        assertEquals(FleetShutdown.Phase.CRASHED, fs.phase);
        assertEquals("CRASHED", fs.exitLabel());
        assertFalse(fs.resumeFully(), "crash -> guarded resume");
    }

    @Test
    void unfinishedDrainIsTreatedAsCrashOnReboot()
    {
        // Marker says DRAINING when the process came up again -> the drain never finished.
        FleetShutdown fs = FleetShutdown.fromLabel("DRAINING", "unfinished");
        assertEquals(FleetShutdown.Phase.CRASHED, fs.phase);
        assertFalse(fs.resumeFully());
    }

    @Test
    void fromLabelRoundTrips()
    {
        assertEquals(FleetShutdown.Phase.DRAINED, FleetShutdown.fromLabel("GRACEFUL", "x").phase);
        assertEquals(FleetShutdown.Phase.CRASHED, FleetShutdown.fromLabel("CRASHED", "x").phase);
        assertEquals(FleetShutdown.Phase.IDLE, FleetShutdown.fromLabel(null, "x").phase);
        assertEquals(FleetShutdown.Phase.IDLE, FleetShutdown.fromLabel("BOGUS", "x").phase);
    }
}