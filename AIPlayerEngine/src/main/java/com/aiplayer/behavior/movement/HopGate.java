package com.aiplayer.behavior.movement;
import com.aiplayer.examples.FleetPlay;

/** MODE: COMPLETE. Pure hop-gating decision for the fleet's ack-gated route loop. Extracted from
 *  FleetPlay's previously integration-only nearHop/timedOut/neverSent ladder (Audit 46 P0 #2) so the
 *  "send vs advance vs resend" decision is a tiny, unit-testable function with no IO and no state.
 *  {@link FleetPlay} feeds it the current tick time, whether the server has acked the bot near the
 *  pending hop (ValidateLocation), and when the hop was last sent; the returned {@link Action} (or
 *  {@code null} = keep waiting) drives the loop. Timeout window MUST mirror MoveToLocation's
 *  expectations: 45s with no ack and the hop is re-sent (stuck-hop recovery lives in FleetPlay via
 *  {@link ZoneRouter#isRouteStuck}). */
public final class HopGate
{
    /** A hop is considered stale (re-sent) after this long with no server ack (was a literal 45_000 in FleetPlay). */
    public static final long HOP_TIMEOUT_MS = 45_000;

    public enum Action
    {
        /** Hop was never sent (sentAt == 0) and we are not near it: send the MoveToLocation frame. */
        SEND,
        /** Server acked us near the hop: advance to the next waypoint or finish the route. */
        ADVANCE,
        /** Hop was sent but the ack window elapsed without the server walking us there: re-send. */
        RESEND
    }

    private HopGate()
    {
    }

    /**
     * Decide the next hop action for the ack-gated fleet loop.
     *
     * @param now     current tick time (ms)
     * @param near    whether the server has acked us within arriveDist of the pending hop
     * @param sentAt  when the pending hop was last sent (0 = never sent)
     * @return {@link Action#SEND} when never sent; {@link Action#ADVANCE} when near; {@link Action#RESEND}
     *         when sent but waited {@code > HOP_TIMEOUT_MS} with no ack; {@code null} when sent and still
     *         within the ack window (keep waiting, do nothing this tick).
     */
    public static Action nextAction(long now, boolean near, long sentAt)
    {
        if (near)
        {
            return Action.ADVANCE;
        }
        if (sentAt == 0)
        {
            return Action.SEND;
        }
        if (now - sentAt > HOP_TIMEOUT_MS)
        {
            return Action.RESEND;
        }
        return null; // in flight: sent, not near yet, still inside the ack window
    }
}
