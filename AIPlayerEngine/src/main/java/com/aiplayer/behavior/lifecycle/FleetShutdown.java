package com.aiplayer.behavior.lifecycle;

/**
 * EB-10 — GRACEFUL FLEET SHUTDOWN + RESUME state machine (pure, immutable, deterministic).
 *
 * <p>Tracks why the fleet stopped and what the next boot should assume:
 *
 * <pre>
 *   IDLE --requestDrain(reason)--> DRAINING --completeDrain()--> DRAINED (graceful)
 *      \                                                          |
 *       \--noteCrash(reason)-----------------------------> CRASHED
 * </pre>
 *
 * <p>A DRAINED exit means each bot session was interrupted at its next safe boundary and its
 * per-session state persisted (Stream E 89) — the next boot can RESUME cleanly. A DRAINING state
 * observed by a NEW boot means the process died mid-drain (treat as CRASHED — resume guarded).
 * The CLI layer owns the actual marker file IO ({@link #stateLabel()} / {@link #reason()} feed it);
 * this class stays pure so it is trivially testable.
 */
public final class FleetShutdown
{
    public enum Phase
    {
        IDLE, DRAINING, DRAINED, CRASHED
    }

    private static final FleetShutdown IDLE = new FleetShutdown(Phase.IDLE, "");

    public final Phase phase;
    public final String reason;

    private FleetShutdown(Phase phase, String reason)
    {
        this.phase = phase;
        this.reason = reason == null ? "" : reason;
    }

    /** The fleet has not been asked to stop yet. */
    public static FleetShutdown idle()
    {
        return IDLE;
    }

    /** Begin a graceful drain. Idempotent: a second request keeps the first reason. */
    public FleetShutdown requestDrain(String reason)
    {
        if (phase == Phase.IDLE)
        {
            return new FleetShutdown(Phase.DRAINING, reason);
        }
        return this; // already DRAINING/DRAINED/CRASHED — ignore
    }

    /** Drain finished cleanly. Only valid from DRAINING. */
    public FleetShutdown completeDrain()
    {
        if (phase == Phase.DRAINING)
        {
            return new FleetShutdown(Phase.DRAINED, reason);
        }
        return this;
    }

    /** The process died without completing a drain (or mid-drain). */
    public FleetShutdown noteCrash(String reason)
    {
        return new FleetShutdown(Phase.CRASHED, reason);
    }

    // ----------------------------------------------------------------
    // Resume semantics
    // ----------------------------------------------------------------

    /** True only after a COMPLETED graceful drain or a pristine boot (never mid-drain, never crashed). */
    public boolean resumeFully()
    {
        return phase == Phase.DRAINED || phase == Phase.IDLE;
    }

    /** Label for the marker file / keep_alive log: {@code GRACEFUL} vs {@code CRASHED} vs {@code NONE}. */
    public String exitLabel()
    {
        switch (phase)
        {
            case DRAINED:
                return "GRACEFUL";
            case CRASHED:
                return "CRASHED";
            case DRAINING:
                return "DRAINING";
            case IDLE:
            default:
                return "NONE";
        }
    }

    /** A new boot should call {@code instance.noteCrash(...)} if it finds a marker that was never DRAINED. */
    public static FleetShutdown fromLabel(String label, String reason)
    {
        switch (label == null ? "NONE" : label)
        {
            case "GRACEFUL":
                return new FleetShutdown(Phase.DRAINED, reason);
            case "DRAINING":
            case "CRASHED":
                return new FleetShutdown(Phase.CRASHED, reason);
            default:
                return idle();
        }
    }
}