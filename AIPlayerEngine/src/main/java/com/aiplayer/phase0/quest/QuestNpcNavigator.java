package com.aiplayer.phase0.quest;

/** MODE: COMPLETE. Pure engine-side quest-NPC navigation planner (Audit 48 Stage A). */

import java.util.List;

import com.aiplayer.phase0.movement.HopGate;
import com.aiplayer.phase0.movement.ZoneRouter;
import com.aiplayer.phase0.quest.QuestInfo.QuestStep;
import com.aiplayer.phase0.quest.QuestProgressTracker.ActiveQuestState;

/**
 * AUDIT 48 STAGE A — quest-NPC navigation planner.
 *
 * <p>Pure engine-side planner that routes a bot to a quest-giver / turn-in NPC coordinate and
 * walks the route with the SAME server-ack-gated hop loop semantics as TIM-001 (FleetPlay's
 * ZoneRouter {@code <= 4800u} hops + {@link HopGate} send/advance/resend). It has no IO and no
 * packet sending — it returns a self-contained {@link NpcRoute} whose {@link NpcRoute#nextHop()}
 * yields the next waypoint to dispatch as a MoveToLocation frame and whose {@link NpcRoute#tick}
 * decides, from the server ack (ValidateLocation near-ack) and the send timestamp, whether to
 * send / advance / resend / abandon — exactly the "rozes" lesson: movement-to-NPC must stay
 * server-ack-gated or NPC nav will not persist.
 *
 * <p>Target selection is driven from the EXISTING quest/landmark data:
 * <ul>
 *   <li>{@link #resolveNpcTarget(int, int)} pulls the NPC coordinate from a
 *       {@link QuestDatabase} quest step (the quest-NPC landmark; TALK/RETURN steps carry the
 *       giver's x/y/z in {@link QuestStep#zoneX}/{@link QuestStep#zoneY}/{@link QuestStep#zoneZ});</li>
 *   <li>{@link #resolveActiveQuestTarget(QuestProgressTracker)} resolves the current step of the
 *       tracker's high-priority active quest, i.e. "driven by quest state";</li>
 *   <li>{@link #planRoute(int, int, int, int, int, int, String)} accepts explicit target coords
 *       as a fallback when no quest-NPC landmark is available.</li>
 * </ul>
 *
 * <p>Stages B (talkToNpc 0xb0), C (RequestBypassToServer 0x21) and D (quest-loop goal planning)
 * of Audit 48 are deliberately NOT implemented here — this class stops at "reach the NPC".
 */
public final class QuestNpcNavigator
{
    /** How close (u) the server must ack us to a hop before the next hop is dispatched (mirrors FleetPlay). */
    public static final double ARRIVE_DIST = 150.0;

    /** NPC coordinate resolved from a quest's NPC-facing step; the landmark for this planner. */
    public static final class NpcTarget
    {
        public final int x;
        public final int y;
        public final int z;
        public final String label;   // e.g. "quest:Spider Silk Collection step2 (Return to Trader)"
        public final int questId;
        public final int stepIndex;

        NpcTarget(int x, int y, int z, String label, int questId, int stepIndex)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
            this.questId = questId;
            this.stepIndex = stepIndex;
        }
    }

    private QuestNpcNavigator()
    {
    }

    // ================================================================
    // TARGET RESOLUTION (from existing quest/landmark data)
    // ================================================================

    /**
     * Resolve the NPC coordinate of a quest step from {@link QuestDatabase} (the quest-NPC
     * landmark). Only steps that carry a coordinate (TALK / RETURN / NAVIGATE) resolve; other
     * step types (KILL / COLLECT / WAIT / COMBAT / USE_ITEM) have no NPC to walk to and return
     * {@code null} so the caller falls back to an explicit target or stays put.
     *
     * @return the NPC target, or {@code null} when the quest/step is unknown or not NPC-facing
     */
    public static NpcTarget resolveNpcTarget(int questId, int stepIndex)
    {
        QuestInfo q = QuestDatabase.getById(questId);
        if (q == null || stepIndex < 0 || stepIndex >= q.steps.size())
        {
            return null;
        }
        QuestStep step = q.steps.get(stepIndex);
        if (!hasNpcCoord(step))
        {
            return null;
        }
        String label = String.format("quest:%s step%d (%s)", q.name, stepIndex, step.stepDesc);
        return new NpcTarget(step.zoneX, step.zoneY, step.zoneZ, label, questId, stepIndex);
    }

    /**
     * Quest-state-driven target: resolve the CURRENT step of the tracker's highest-priority
     * active quest. When that step is an NPC-facing step (e.g. "talk to the giver" / "return to
     * the giver"), its coordinate IS the quest NPC the bot must reach next.
     *
     * @return the NPC target for the active quest's current step, or {@code null} when there is
     *         no active quest, the step is not NPC-facing, or the quest data is unknown
     */
    public static NpcTarget resolveActiveQuestTarget(QuestProgressTracker tracker)
    {
        if (tracker == null)
        {
            return null;
        }
        Integer questId = tracker.getCurrentPriorityQuest();
        if (questId == null)
        {
            return null;
        }
        ActiveQuestState state = tracker.getActiveState(questId);
        if (state == null)
        {
            return null;
        }
        return resolveNpcTarget(questId, state.currentStepIndex);
    }

    /** True when the step is NPC-facing (TALK/RETURN) AND carries a usable coordinate. */
    private static boolean hasNpcCoord(QuestStep step)
    {
        if (step == null)
        {
            return false;
        }
        QuestInfo.StepType type = step.stepType;
        boolean npcFacing = type == QuestInfo.StepType.TALK || type == QuestInfo.StepType.RETURN;
        return npcFacing && (step.zoneX != 0 || step.zoneY != 0);
    }
// ================================================================
    // ROUTE PLANNING (server-ack-gated, mirrors ZoneRouter/HopGate)
    // ================================================================

    /**
     * Plan the ack-gated walk route to a resolved quest-NPC target.
     *
     * @param fromX/Y/Z current SERVER-ACKED position (never 0,0 — not in world yet)
     * @return the route, or {@code null} when the origin is not in-world yet (0,0) or the
     *         target is null
     */
    public static NpcRoute planRoute(int fromX, int fromY, int fromZ, NpcTarget target)
    {
        if (fromX == 0 && fromY == 0 || target == null)
        {
            return null;
        }
        List<int[]> hops = ZoneRouter.buildHops(fromX, fromY, fromZ, target.x, target.y, target.z);
        return new NpcRoute(target.x, target.y, target.z, target.label, hops);
    }

    /**
     * Plan the ack-gated walk route to an EXPLICIT quest-NPC coordinate (fallback when no
     * quest-NPC landmark resolved; e.g. an NPC id -&gt; coords lookup from a future datapack table).
     *
     * @param label human-readable NPC description (e.g. "npc:30002 Trader")
     * @return the route, or {@code null} when the origin is not in-world yet (0,0)
     */
    public static NpcRoute planRoute(int fromX, int fromY, int fromZ,
                                     int npcX, int npcY, int npcZ, String label)
    {
        if (fromX == 0 && fromY == 0)
        {
            return null;
        }
        List<int[]> hops = ZoneRouter.buildHops(fromX, fromY, fromZ, npcX, npcY, npcZ);
        return new NpcRoute(npcX, npcY, npcZ, label, hops);
    }

/**
     * A self-contained ack-gated walk route to a quest NPC. Owns the FleetPlay hop-loop state
     * (pending hop, send timestamp, timeout counter) so the caller only feeds:
     * <pre>
     *   int[] h;
     *   while ((h = route.nextHop()) != null) {   // never-sent hop -&gt; dispatch MoveToLocation
     *       route.markHopSent(now);
     *       ...
     *   }
     *   // per tick, with the server's ValidateLocation near-ack:
     *   HopGate.Action act = route.tick(now, nearHop); // SEND/ADVANCE/RESEND/null
     * </pre>
     */
    public static final class NpcRoute
    {
        public final int destX;
        public final int destY;
        public final int destZ;
        public final String label;   // e.g. "quest:Spider Silk Collection step2 (Return to Trader)"

        private final List<int[]> hops; // ordered waypoints from ZoneRouter.buildHops(); each &lt;= 4800u
        private int hopIndex = 0;       // next hop to dispatch
        private int[] pending = null;   // hop in flight, awaiting server near-ack
        private long hopSentAtMs = 0;   // 0 = pending hop never dispatched
        private int hopTimeouts = 0;    // consecutive RESENDs on the same hop (stuck-hop recovery)
        private boolean done = false;

        NpcRoute(int destX, int destY, int destZ, String label, List<int[]> hops)
        {
            this.destX = destX;
            this.destY = destY;
            this.destZ = destZ;
            this.label = label;
            this.hops = hops;
        }

        /** Total number of waypoints planned (0 for a degenerate zero-length route). */
        public int totalHops()
        {
            return hops.size();
        }

        /** True while the route can still yield a hop; false once every hop was sent or the route was abandoned. */
        public boolean hasNext()
        {
            return !done && (pending != null || hopIndex < hops.size());
        }

        /** True once the route is exhausted (final hop acked and no more hops) or abandoned as stuck. */
        public boolean isDone()
        {
            return done;
        }

        /**
         * Next hop {x,y,z} to dispatch, or {@code null} when the route is complete. Marks the hop
         * as in-flight with sentAt=0 so the next {@link #tick} reports SEND.
         */
        public int[] nextHop()
        {
            if (done)
            {
                return null;
            }
            if (pending != null)
            {
                return pending; // already in flight (e.g. re-dispatch after RESEND)
            }
            if (hopIndex >= hops.size())
            {
                done = true;
                return null;
            }
            pending = hops.get(hopIndex++);
            hopSentAtMs = 0;
            hopTimeouts = 0;
            return pending;
        }

        /** The waypoint the server must ack us near right now, or null when none in flight. */
        public int[] pendingHop()
        {
            return pending;
        }

        /** Record that the pending hop's MoveToLocation frame was actually sent at {@code now}. */
        public void markHopSent(long now)
        {
            if (pending != null)
            {
                hopSentAtMs = now;
            }
        }

        /**
         * Ack-gated decision for the pending hop — TIM-001/rozes semantics via the pure
         * {@link HopGate} helper, plus stuck-hop abandonment mirroring FleetPlay
         * ({@link ZoneRouter#isRouteStuck}).
         *
         * @param now  current tick time (ms)
         * @param near whether the server acked us within {@link #ARRIVE_DIST} of the pending hop
         * @return {@link HopGate.Action#SEND} when pending was never dispatched,
         *         {@link HopGate.Action#ADVANCE} when near (route internally advances; on the last
         *         hop the route becomes {@link #isDone() done}),
         *         {@link HopGate.Action#RESEND} when sent but stale {@code > HOP_TIMEOUT_MS} —
         *         after {@link ZoneRouter#MAX_HOP_TIMEOUTS} consecutive stale resends the route is
         *         abandoned (done, so the caller re-plans instead of stalling forever),
         *         or {@code null} to keep waiting.
         */
        public HopGate.Action tick(long now, boolean near)
        {
            if (done)
            {
                return null;
            }
            if (pending == null)
            {
                // no hop in flight: caller should have pulled nextHop() first; report as needing send
                return HopGate.Action.SEND;
            }

            HopGate.Action action = HopGate.nextAction(now, near, hopSentAtMs);

            if (action == HopGate.Action.ADVANCE)
            {
                hopTimeouts = 0; // a reached hop is not stuck
                if (hopIndex < hops.size())
                {
                    pending = hops.get(hopIndex++);
                    hopSentAtMs = 0;
                    hopTimeouts = 0;
                }
                else
                {
                    pending = null;
                    done = true; // NPC reached: route complete
                }
                return HopGate.Action.ADVANCE;
            }

            if (action == HopGate.Action.RESEND)
            {
                hopTimeouts++;
                if (ZoneRouter.isRouteStuck(hopTimeouts))
                {
                    // unreachable hop: abandon the whole route so the caller re-plans.
                    pending = null;
                    hopSentAtMs = 0;
                    hopTimeouts = 0;
                    done = true;
                }
                return HopGate.Action.RESEND;
            }

            return action; // SEND, or null (in flight, still inside the ack window)
        }

        /**
         * Convenience observation: straight-line distance (u) from the pending hop to the NPC
         * destination, 0 when nothing is in flight.
         */
        public double remainingDistToDest()
        {
            if (pending == null)
            {
                return 0.0;
            }
            return Math.hypot(destX - pending[0], destY - pending[1]);
        }
    }
}