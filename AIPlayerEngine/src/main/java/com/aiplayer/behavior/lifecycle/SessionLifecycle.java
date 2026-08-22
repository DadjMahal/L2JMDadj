package com.aiplayer.behavior.lifecycle;

/**
 * EB-09 — SESSION LIFECYCLE STATE MACHINE (pure, immutable, deterministic).
 *
 * <p>Spawn / connect / play / sleep / disconnect as FIRST-CLASS session states, separate from
 * gameplay states (AIPlayerState.OFFLINE/IN_GAME/etc.). This is the seam the SoulScheduler
 * (LI-2) hooks into: wake/sleep the session, keep it disconnected while "asleep", reconnect on
 * wake.
 *
 * <pre>
 *   SPAWNED --SPAWN----------> CONNECTING --CONNECT_OK--> PLAYING
 *      ^                          |  |                       |
 *      |--------CONNECT_FAIL------+  |--DISCONNECT           |--GO_SLEEP
 *                                    v                       v
 *                              DISCONNECTED (terminal)   SLEEPING --WAKE--> CONNECTING
 *                                                             |
 *                                                             +--DISCONNECT--> DISCONNECTED
 * </pre>
 *
 * <p>A transition() that is not allowed returns {@code this} unchanged (invalid events are
 * no-ops, never crashes). A new session starts at SPAWNED.
 */
public final class SessionLifecycle
{
    /** The session phases (not gameplay states). */
    public enum SessionPhase
    {
        /** Registered as a soul/limb but not yet handed a socket. */
        SPAWNED,
        /** Socket connect/login attempt in flight. */
        CONNECTING,
        /** Connected and behaving in the world. */
        PLAYING,
        /** Voluntarily away (soul asleep / scheduler put it to bed) — socket closed. */
        SLEEPING,
        /** Terminal: session ended (despawn, disconnect, or too many failed connects). */
        DISCONNECTED
    }

    /** Events that drive the lifecycle. */
    public enum LifecycleEvent
    {
        SPAWN,
        CONNECT_OK,
        CONNECT_FAIL,
        SOCKET_LOST,
        GO_SLEEP,
        WAKE,
        DISCONNECT
    }

    private static final SessionLifecycle SPAWNED = new SessionLifecycle(SessionPhase.SPAWNED, 0L);

    public final SessionPhase phase;
    public final long sinceMs;

    private SessionLifecycle(SessionPhase phase, long sinceMs)
    {
        this.phase = phase;
        this.sinceMs = sinceMs;
    }

    /** A fresh session, starting at SPAWNED. */
    public static SessionLifecycle spawn(long nowMs)
    {
        return new SessionLifecycle(SessionPhase.SPAWNED, nowMs);
    }

    /** Copy-at-state-change helper. */
    public SessionLifecycle withPhase(SessionPhase next, long nowMs)
    {
        return new SessionLifecycle(next, nowMs);
    }

    // ----------------------------------------------------------------
    // Transition table
    // ----------------------------------------------------------------

    /** Whether {@code event} is currently allowed. */
    public boolean canTransition(LifecycleEvent event)
    {
        switch (phase)
        {
            case SPAWNED:
                return event == LifecycleEvent.SPAWN || event == LifecycleEvent.DISCONNECT;
            case CONNECTING:
                return event == LifecycleEvent.CONNECT_OK
                    || event == LifecycleEvent.CONNECT_FAIL
                    || event == LifecycleEvent.DISCONNECT;
            case PLAYING:
                return event == LifecycleEvent.GO_SLEEP
                    || event == LifecycleEvent.SOCKET_LOST
                    || event == LifecycleEvent.DISCONNECT;
            case SLEEPING:
                return event == LifecycleEvent.WAKE || event == LifecycleEvent.DISCONNECT;
            case DISCONNECTED:
            default:
                return false; // terminal — last write wins
        }
    }

    /**
     * Apply {@code event}; returns a NEW lifecycle at {@code nowMs} when allowed, otherwise
     * {@code this} unchanged (invalid events are no-ops).
     */
    public SessionLifecycle transition(LifecycleEvent event, long nowMs)
    {
        if (!canTransition(event))
        {
            return this;
        }
        switch (event)
        {
            case SPAWN:
                return withPhase(SessionPhase.CONNECTING, nowMs);
            case CONNECT_OK:
                return withPhase(SessionPhase.PLAYING, nowMs);
            case CONNECT_FAIL:
                return withPhase(SessionPhase.SPAWNED, nowMs); // retry later (back off externally)
            case SOCKET_LOST:
            case DISCONNECT:
                return withPhase(SessionPhase.DISCONNECTED, nowMs);
            case GO_SLEEP:
                return withPhase(SessionPhase.SLEEPING, nowMs);
            case WAKE:
                return withPhase(SessionPhase.CONNECTING, nowMs);
            default:
                return this;
        }
    }

    // Convenience predicates
    public boolean isSpawned() { return phase == SessionPhase.SPAWNED; }
    public boolean isConnecting() { return phase == SessionPhase.CONNECTING; }
    public boolean isPlaying() { return phase == SessionPhase.PLAYING; }
    public boolean isSleeping() { return phase == SessionPhase.SLEEPING; }
    public boolean isTerminal() { return phase == SessionPhase.DISCONNECTED; }
    /** A session the rest of the engine should actively tick. */
    public boolean isActive()
    {
        return phase == SessionPhase.PLAYING || phase == SessionPhase.CONNECTING;
    }
}