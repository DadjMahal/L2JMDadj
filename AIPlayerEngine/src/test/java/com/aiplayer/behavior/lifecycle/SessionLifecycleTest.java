package com.aiplayer.behavior.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.lifecycle.SessionLifecycle.LifecycleEvent;
import com.aiplayer.behavior.lifecycle.SessionLifecycle.SessionPhase;

/**
 * EB-09 — locks the session lifecycle state machine: spawn/connect/play/sleep/disconnect as
 * first-class phases, plus the SoulScheduler wake/sleep transitions.
 */
class SessionLifecycleTest
{
    // ----------------------------------------------------------------
    // Happy path
    // ----------------------------------------------------------------

    @Test
    void spawnThenLoginReachesPlaying()
    {
        SessionLifecycle lc = SessionLifecycle.spawn(1_000L);
        assertTrue(lc.isSpawned());

        lc = lc.transition(LifecycleEvent.SPAWN, 2_000L);
        assertTrue(lc.isConnecting());

        lc = lc.transition(LifecycleEvent.CONNECT_OK, 3_000L);
        assertTrue(lc.isPlaying());
    }

    @Test
    void connectFailReturnsToSpawned()
    {
        SessionLifecycle lc = SessionLifecycle.spawn(1_000L)
            .transition(LifecycleEvent.SPAWN, 2_000L);
        lc = lc.transition(LifecycleEvent.CONNECT_FAIL, 2_100L);
        assertTrue(lc.isSpawned(), "failed connect -> back to SPAWNED for retry");
    }

    @Test
    void socketLossDisconnectsTerminally()
    {
        SessionLifecycle lc = playing();
        lc = lc.transition(LifecycleEvent.SOCKET_LOST, 5_000L);
        assertTrue(lc.isTerminal());
    }

    @Test
    void sleepThenWakeReconnects()
    {
        SessionLifecycle lc = playing()
            .transition(LifecycleEvent.GO_SLEEP, 5_000L);
        assertTrue(lc.isSleeping());

        lc = lc.transition(LifecycleEvent.WAKE, 6_000L);
        assertTrue(lc.isConnecting(), "wake -> CONNECTING");

        lc = lc.transition(LifecycleEvent.CONNECT_OK, 7_000L);
        assertTrue(lc.isPlaying());
        assertEquals(7_000L, lc.sinceMs);
    }

    @Test
    void sleepCanStillDisconnect()
    {
        SessionLifecycle lc = playing()
            .transition(LifecycleEvent.GO_SLEEP, 5_000L)
            .transition(LifecycleEvent.DISCONNECT, 9_000L);
        assertTrue(lc.isTerminal());
    }

    // ----------------------------------------------------------------
    // Guards: invalid transitions no-op
    // ----------------------------------------------------------------

    @Test
    void invalidTransitionsAreNoOps()
    {
        // SPAWNED cannot wake, sleep, or confirm connect.
        SessionLifecycle spawned = SessionLifecycle.spawn(1_000L);
        assertEquals(spawned, spawned.transition(LifecycleEvent.WAKE, 2_000L));
        assertEquals(spawned, spawned.transition(LifecycleEvent.GO_SLEEP, 2_000L));
        assertEquals(spawned, spawned.transition(LifecycleEvent.CONNECT_OK, 2_000L));
    }

    @Test
    void terminalIsFrozen()
    {
        SessionLifecycle done = playing()
            .transition(LifecycleEvent.DISCONNECT, 5_000L);
        assertTrue(done.isTerminal());
        // Nothing may leave the terminal phase (reconnect spawns a FRESH session instead).
        assertEquals(done, done.transition(LifecycleEvent.WAKE, 6_000L));
        assertEquals(done, done.transition(LifecycleEvent.SPAWN, 6_000L));
        assertEquals(done, done.transition(LifecycleEvent.CONNECT_OK, 6_000L));
    }

    @Test
    void playingCannotSpawnOrWake()
    {
        SessionLifecycle lc = playing();
        assertEquals(lc, lc.transition(LifecycleEvent.SPAWN, 5_000L));
        assertEquals(lc, lc.transition(LifecycleEvent.WAKE, 5_000L));
        assertTrue(lc.isPlaying());
    }

    // ----------------------------------------------------------------
    // Predicates
    // ----------------------------------------------------------------

    @Test
    void activePredicate()
    {
        assertTrue(SessionLifecycle.spawn(1L).transition(LifecycleEvent.SPAWN, 2L).isActive());
        assertTrue(playing().isActive());
        assertFalse(SessionLifecycle.spawn(1L).isActive(), "SPAWNED not yet active");
        assertFalse(playing().transition(LifecycleEvent.GO_SLEEP, 5L).isActive());
        assertFalse(playing().transition(LifecycleEvent.DISCONNECT, 5L).isActive());
    }

    @Test
    void canTransitionTable()
    {
        SessionLifecycle base = SessionLifecycle.spawn(1_000L);
        assertTrue(base.canTransition(LifecycleEvent.SPAWN));
        assertFalse(base.canTransition(LifecycleEvent.GO_SLEEP));

        SessionLifecycle connecting = base.transition(LifecycleEvent.SPAWN, 2_000L);
        assertTrue(connecting.canTransition(LifecycleEvent.CONNECT_OK));
        assertTrue(connecting.canTransition(LifecycleEvent.CONNECT_FAIL));
        assertFalse(connecting.canTransition(LifecycleEvent.WAKE));

        SessionLifecycle sleeping = playing().transition(LifecycleEvent.GO_SLEEP, 5_000L);
        assertTrue(sleeping.canTransition(LifecycleEvent.WAKE));
        assertFalse(sleeping.canTransition(LifecycleEvent.CONNECT_OK));
    }

    // ----------------------------------------------------------------

    private static SessionLifecycle playing()
    {
        return SessionLifecycle.spawn(1_000L)
            .transition(LifecycleEvent.SPAWN, 2_000L)
            .transition(LifecycleEvent.CONNECT_OK, 3_000L);
    }
}