package com.aiplayer.phase0.movement;

/** MODE: COMPLETE. Evidence harness for TIM-001 — records, per bot, every MoveToLocation (0x01)
 *  frame the fleet schedules AND every server position the PacketLogger observed (ValidateLocation
 *  0x61 / CharInfo 0x03), plus EXP level samples. Nothing here invents data: callers feed it real
 *  values from the proven live path (FleetPlay loop + PacketLogger). The {@link #report()} output
 *  is the paste-able evidence the TIM-001 checklist (H1/H2/H5) requires. */

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MoveTelemetry
{
    private static final MoveTelemetry INSTANCE = new MoveTelemetry();

    /** Per-account position sample captured from the live PacketLogger (ValidateLocation etc.). */
    public static final class PositionSample
    {
        public final long ts;
        public final int x;
        public final int y;
        public final int z;
        public final long exp;

        PositionSample(long ts, int x, int y, int z, long exp)
        {
            this.ts = ts;
            this.x = x;
            this.y = y;
            this.z = z;
            this.exp = exp;
        }
    }

    /** Per-account scheduled MoveToLocation frame (exactly what the fleet sent). */
    public static final class MoveRecord
    {
        public final long ts;
        public final int fx, fy, fz, tx, ty, tz;
        public final double dist;
        public final String label;
        public final String reason;

        MoveRecord(long ts, int fx, int fy, int fz, int tx, int ty, int tz, String label, String reason)
        {
            this.ts = ts;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.dist = Math.hypot(tx - fx, ty - fy);
            this.label = label == null ? "" : label;
            this.reason = reason == null ? "" : reason;
        }
    }

    /** Destinations shorter than this are "degenerate" for the TIM-001 H2 review. */
    public static final double DEGENERATE_DIST = 500.0;

    private static final int CAP = 4096;

    private final Map<String, Deque<MoveRecord>> moves = new ConcurrentHashMap<>();
    private final Map<String, Deque<PositionSample>> positions = new ConcurrentHashMap<>();

    private MoveTelemetry()
    {
    }

    public static MoveTelemetry getInstance()
    {
        return INSTANCE;
    }

    /** S5-T06: hop persistence telemetry — [acked, total] per account. */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> hopStats =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Record whether the server actually walked us toward a hop (acked=true) or timed it out. */
    public void recordHopResult(String account, boolean reached)
    {
        long[] s = hopStats.computeIfAbsent(account, k -> new long[2]);
        synchronized (s)
        {
            s[1]++;
            if (reached)
            {
                s[0]++;
            }
        }
    }

    /** Percentage of hops the server acknowledged (100 when no data yet). */
    public int hopSuccessPct(String account)
    {
        long[] s = hopStats.get(account);
        if (s == null || s[1] == 0)
        {
            return 100;
        }
        synchronized (s)
        {
            return (int) (s[0] * 100 / s[1]);
        }
    }

    /** Record a MoveToLocation frame we actually sent. Called by the fleet loop. */
    public void recordMove(String account, int fx, int fy, int fz, int tx, int ty, int tz, String label, String reason)
    {
        Deque<MoveRecord> q = moves.computeIfAbsent(account, k -> new ArrayDeque<>());
        synchronized (q)
        {
            if (q.size() >= CAP) q.removeFirst();
            q.addLast(new MoveRecord(System.currentTimeMillis(), fx, fy, fz, tx, ty, tz, label, reason));
        }
    }

    /** Record the server-observed position + exp every tick. 0,0,0 (pre-UserInfo) samples are skipped. */
    public void recordPosition(String account, int x, int y, int z, long exp)
    {
        if (x == 0 && y == 0)
        {
            return; // not in-world yet; a real coordinate would never be (0,0) on Interlude
        }
        Deque<PositionSample> q = positions.computeIfAbsent(account, k -> new ArrayDeque<>());
        synchronized (q)
        {
            if (q.size() >= CAP) q.removeFirst();
            q.addLast(new PositionSample(System.currentTimeMillis(), x, y, z, exp));
        }
    }

    /** Reset all per-account history (used between proof runs). */
    public void reset()
    {
        moves.clear();
        positions.clear();
    }

    /** Number of MoveToLocation frames recorded for an account. */
    public int moveCount(String account)
    {
        Deque<MoveRecord> q = moves.get(account);
        return q == null ? 0 : q.size();
    }

    /** Sum of planned (frame) distances for an account. */
    public double totalMoveDistance(String account)
    {
        Deque<MoveRecord> q = moves.get(account);
        if (q == null) return 0.0;
        double total = 0;
        synchronized (q)
        {
            for (MoveRecord r : q) total += r.dist;
        }
        return total;
    }

    /** How many scheduled moves were "degenerate" (destination < DEGENERATE_DIST away) — TIM-001 H2. */
    public int degenerateMoveCount(String account)
    {
        Deque<MoveRecord> q = moves.get(account);
        if (q == null) return 0;
        int n = 0;
        synchronized (q)
        {
            for (MoveRecord r : q) if (r.dist < DEGENERATE_DIST) n++;
        }
        return n;
    }

    /**
     * Server-acknowledged distance traveled in the last windowMs, computed from the position samples
     * the server actually sent back. This is the TIM-001 H1 core evidence.
     */
    public double movedLast(long windowMs, String account)
    {
        Deque<PositionSample> q = positions.get(account);
        if (q == null) return 0.0;
        long now = System.currentTimeMillis();
        double total = 0;
        PositionSample prev = null;
        synchronized (q)
        {
            for (PositionSample s : q)
            {
                if (now - s.ts > windowMs) continue;
                if (prev != null) total += dist(prev, s);
                prev = s;
            }
        }
        return total;
    }

    /** Total server-acknowledged path length since the first sample (H1 "did it move at all"). */
    public double totalMoved(String account)
    {
        Deque<PositionSample> q = positions.get(account);
        if (q == null) return 0.0;
        double total = 0;
        PositionSample prev = null;
        synchronized (q)
        {
            for (PositionSample s : q)
            {
                if (prev != null) total += dist(prev, s);
                prev = s;
            }
        }
        return total;
    }

    /** Organic XP gained since the first sample — TIM-001 H5 evidence. */
    public long expGained(String account)
    {
        Deque<PositionSample> q = positions.get(account);
        if (q == null || q.size() < 2) return 0;
        synchronized (q)
        {
            PositionSample first = q.peekFirst();
            PositionSample last = q.peekLast();
            if (first == null || last == null) return 0;
            return last.exp - first.exp;
        }
    }

    /** Number of server position samples seen for an account. */
    public int positionSampleCount(String account)
    {
        Deque<PositionSample> q = positions.get(account);
        return q == null ? 0 : q.size();
    }

    /**
     * Paste-able evidence report, one block per account + a global summary. Grep keys:
     * EVIDENCE-H1 (server moved), EVIDENCE-H2 (degenerate destinations), EVIDENCE-H5 (organic XP).
     */
    public String report()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MOVE TELEMETRY ").append(java.time.Instant.now()).append(" ===\n");
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (String a : moves.keySet()) remaining.putIfAbsent(a, 1);
        for (String a : positions.keySet()) remaining.putIfAbsent(a, 1);
        if (remaining.isEmpty())
        {
            sb.append("(no data — bots not running / no samples yet)\n");
            return sb.toString();
        }
        for (String account : remaining.keySet())
        {
            int mc = moveCount(account);
            int deg = degenerateMoveCount(account);
            int ps = positionSampleCount(account);
            double moved60 = movedLast(60_000, account);
            double movedTot = totalMoved(account);
            long xp = expGained(account);

            sb.append("--- ").append(account).append(" ---\n");
            sb.append(String.format("  movesSent=%d  degraded<%3.0f u=%d  samples=%d%n", mc, DEGENERATE_DIST, deg, ps));
            sb.append(String.format("  [EVIDENCE-H1] serverMoved=%.0f u (last 60s)  total=%.0f u%n", moved60, movedTot));
            sb.append(String.format("  [EVIDENCE-H2] degenerateDestinations=%d / %d%n", deg, mc));
            sb.append(String.format("  [EVIDENCE-H5] expGained=%d%n", xp));
        }
        return sb.toString();
    }

    private static double dist(PositionSample a, PositionSample b)
    {
        return Math.hypot(b.x - a.x, b.y - a.y);
    }
}
