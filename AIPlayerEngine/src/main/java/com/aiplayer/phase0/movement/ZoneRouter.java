package com.aiplayer.phase0.movement;

/** MODE: COMPLETE. Pure decision-maker: when a bot is idle with no target, pick a real FAR
 *  destination so it demonstrably "travels" (TIM-001 H3). Uses level-appropriate farm-zone
 *  centers from the real Interlude zone database (ZoneRecommender); falls back to a bounded
 *  random far point. No IO, no packet sending — returns a planned {@link RouteGoal} whose
 *  {@link RouteGoal#nextHop()} yields waypoints the fleet loop sends through the proven
 *  Phase0Wiring.moveTo() path.
 *
 *  <p>SERVER CAP (live-verified finding, 2026-08-11): the Interlude {@code MoveToLocation}
 *  handler rejects any single move whose target is &gt; 9900 units away
 *  (SourceCode/.../clientpackets/MoveToLocation.java:156-163). A far goal must be walked as a
 *  <b>sequence of hops</b>, each &le; {@link #MAX_HOP_DIST}. */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.aiplayer.phase0.quest.ZoneRecommender;
import com.aiplayer.phase0.quest.ZoneRecommender.ZoneInfo;

public final class ZoneRouter
{
    /** Largest single MoveToLocation hop the server accepts (server cap 9900, safe margin). */
    public static final double MAX_HOP_DIST = 4800.0;

    /** Prefer a zone-center route when it is at least this far away (otherwise it's not "travel"). */
    private static final double MIN_ZONE_TRAVEL_DIST = 1500.0;

    /**
     * TIM-001: how many consecutive server-move timeouts on the SAME waypoint (each {@code >= hop_timeout_ms},
     * 45s in FleetPlay) before a hop is declared unreachable and the whole route is abandoned so the bot
     * re-plans instead of stalling forever. A hop that the server never walks the char toward (e.g. a
     * geo-blocked / into-the-ocean destination) would otherwise resend every 45s and freeze the bot for
     * minutes — exactly the movesSent=2-in-2-min symptom seen in the live run.
     */
    public static final int MAX_HOP_TIMEOUTS = 2;

    /** True once a hop has timed out {@code MAX_HOP_TIMEOUTS} times without being approached. */
    public static boolean isRouteStuck(int consecutiveTimeouts)
    {
        return consecutiveTimeouts >= MAX_HOP_TIMEOUTS;
    }

    /** A planned far-travel route = ordered waypoints (hops) ending at the chosen destination. */
    public static final class RouteGoal
    {
        public final int destX;
        public final int destY;
        public final int destZ;
        public final String label;   // e.g. "farm:Ruins of Agony" or "far-point"
        public final String reason;  // human-readable why

        private final List<int[]> hops; // {x,y,z} per hop; each <= MAX_HOP_DIST; last == dest
        private int hopIndex = 0;

        RouteGoal(int destX, int destY, int destZ, String label, String reason, List<int[]> hops)
        {
            this.destX = destX;
            this.destY = destY;
            this.destZ = destZ;
            this.label = label;
            this.reason = reason;
            this.hops = hops;
        }

        /** True while there are still waypoints left to send. */
        public boolean hasMoreHops()
        {
            return hopIndex < hops.size();
        }

        /** Next waypoint {x,y,z}, or null when the route is complete. */
        public int[] nextHop()
        {
            if (hopIndex >= hops.size())
            {
                return null;
            }
            return hops.get(hopIndex++);
        }

        /** Total number of hops in this route (>= 1). */
        public int totalHops()
        {
            return hops.size();
        }
    }

    private final String accountName;
    private final Random random;

    /**
     * Destinations the fleet abandoned as geo-unreachable (a route that was re-planned with zero
     * forward progress, e.g. a farm zone center across unwalkable terrain/ocean). Each entry maps a
     * destination key to the millis until which it is suppressed. Keyed by exact zone center/far-point
     * coords, so {@link #plan} will NOT deterministically re-choose the SAME unreachable destination on
     * the next re-plan (the post-respawn 54-HOP / 27-abandon loop seen in the 2026-08-15 live run).
     */
    private final java.util.Map<Long, Long> abandonedUntilMs = new java.util.HashMap<>();

    /** A non-reachable destination stays suppressed this long before it may be retried. */
    private static final long ABANDON_TTL_MS = 15 * 60 * 1000L;
    /** Upper bound on remembered abandoned destinations (prunes oldest-expiring first). */
    private static final int MAX_ABANDONED = 32;

    /** @param accountName per-bot name used to seed the (deterministic) random. */
    public ZoneRouter(String accountName)
    {
        this.accountName = accountName;
        this.random = new Random(accountName.hashCode());
    }

    /**
     * Record that a far-travel destination turned out unreachable (route abandoned without the server
     * ever walking the char toward it). {@link #plan} will skip it (and its zone center) so the bot
     * re-plans to a reachable zone / far-point instead of re-issuing the same dead route forever.
     *
     * <p>Per-instance state: a fresh {@code ZoneRouter} (created per {@code runSession}) starts clean,
     * so a reconnect naturally forgets stale blacklists while a single session's re-loop is broken.</p>
     */
    public void noteUnreachableDestination(int destX, int destY)
    {
        abandonedUntilMs.put(key(destX, destY), System.currentTimeMillis() + ABANDON_TTL_MS);
        prune();
    }

    private static long key(int x, int y)
    {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private boolean isAbandoned(int x, int y)
    {
        Long until = abandonedUntilMs.get(key(x, y));
        return until != null && until > System.currentTimeMillis();
    }

    private void prune()
    {
        long now = System.currentTimeMillis();
        abandonedUntilMs.entrySet().removeIf(e -> e.getValue() <= now);
        while (abandonedUntilMs.size() > MAX_ABANDONED)
        {
            Long oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (java.util.Map.Entry<Long, Long> e : abandonedUntilMs.entrySet())
            {
                if (e.getValue() < oldest)
                {
                    oldest = e.getValue();
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey == null)
            {
                break;
            }
            abandonedUntilMs.remove(oldestKey);
        }
    }

    /**
     * Plan a far-travel route for a bot that is idle (no hostile target in range).
     *
     * @param level      current bot level (seeded chars come in at 20/22; organic later)
     * @param fromX/Y/Z  current live position (must be real server-acked coords, never 0/0/0)
     * @param minRadius  smallest acceptable total travel distance
     * @param maxRadius  largest acceptable total travel distance
     * @return a RouteGoal with per-hop waypoints, or {@code null} if the caller should stay put
     */
    public RouteGoal plan(int level, int fromX, int fromY, int fromZ,
                          double minRadius, double maxRadius)
    {
        if (fromX == 0 && fromY == 0)
        {
            return null; // not in-world yet
        }

        // 1) Nearest level-appropriate farm zone center that is far enough to count as travel.
        List<ZoneInfo> zones = ZoneRecommender.getZonesForLevel(level);
        ZoneInfo best = null;
        double bestDist = Double.MAX_VALUE;
        if (!zones.isEmpty())
        {
            for (ZoneInfo z : zones)
            {
                if (isAbandoned(z.centerX, z.centerY))
                {
                    continue; // previously geo-unreachable — don't deterministically re-select it
                }
                double d = hypot(z.centerX - fromX, z.centerY - fromY);
                if (d >= MIN_ZONE_TRAVEL_DIST && d < bestDist)
                {
                    best = z;
                    bestDist = d;
                }
            }
        }

        final int destX;
        final int destY;
        final int destZ;
        final String label;
        final String reason;
        if (best != null && bestDist <= maxRadius)
        {
            destX = best.centerX;
            destY = best.centerY;
            destZ = best.centerZ;
            label = "farm:" + best.name;
            reason = String.format("L%d farm zone %s at %.0f u", level, best.name, bestDist);
        }
        else
        {
            // 2) Bounded random far point (walked in hops below). Re-roll a handful of times to
            //    avoid a far point that the fleet already abandoned as unreachable.
            int fx = fromX;
            int fy = fromY;
            double rx = 0;
            double ry = 0;
            boolean placed = false;
            for (int attempt = 0; attempt < 8; attempt++)
            {
                double r = minRadius + random.nextDouble() * Math.max(0.0, maxRadius - minRadius);
                double a = random.nextDouble() * 2.0 * Math.PI;
                int cx = fromX + (int) Math.round(Math.cos(a) * r);
                int cy = fromY + (int) Math.round(Math.sin(a) * r);
                if (!isAbandoned(cx, cy))
                {
                    fx = cx;
                    fy = cy;
                    rx = r;
                    ry = a;
                    placed = true;
                    break;
                }
            }
            if (!placed)
            {
                rx = minRadius + random.nextDouble() * Math.max(0.0, maxRadius - minRadius);
                ry = random.nextDouble() * 2.0 * Math.PI;
                fx = fromX + (int) Math.round(Math.cos(ry) * rx);
                fy = fromY + (int) Math.round(Math.sin(ry) * rx);
            }
            destX = fx;
            destY = fy;
            destZ = fromZ;
            label = "far-point";
            reason = String.format("%s random far point ~%.0f u", accountName, rx);
        }

        // Split origin->dest into server-accepted hops (see buildHops).
        List<int[]> hops = buildHops(fromX, fromY, fromZ, destX, destY, destZ);
        return new RouteGoal(destX, destY, destZ, label, reason, hops);
    }

    /**
     * Split the straight line from origin to dest into consecutive hops the server accepts
     * (each &le; MAX_HOP_DIST). Package-private so tests can drive the split directly.
     * Degenerate routes (zero-length) produce NO hops - the caller should stay put rather than
     * send a useless zero-progress MoveToLocation frame (server would reject/no-op it anyway).
     *
     * <p>Z is interpolated along the slope too (not carried as the destination's Z on every
     * intermediate hop): on a sloped route — e.g. a live TI char at z=-3619 headed for the
     * Ruins of Agony zone center at z=-3000 — stamping destZ onto the FIRST hop fabricated a
     * target z far above the ground under its x/y, which can trip the server's geo-cell check
     * in MoveToLocation.java:90 ({@code isCompletelyBlocked(geoX, geoY, _targetZ)}) and stall a
     * reachable route as \"hop unreachable\". Flat routes (dz == 0) are unchanged.</p>
     *
     * @return ordered {x,y,z} waypoints; the last always equals dest; empty when dest == origin
     */
    public static List<int[]> buildHops(int fromX, int fromY, int fromZ,
                                 int destX, int destY, int destZ)
    {
        double dx = destX - fromX;
        double dy = destY - fromY;
        double dz = destZ - fromZ;
        double total = hypot(dx, dy);
        if (total < 1.0)
        {
            return java.util.Collections.emptyList(); // degenerate: no travel, no hops
        }
        int n = Math.max(1, (int) Math.ceil(total / MAX_HOP_DIST));
        List<int[]> hops = new ArrayList<>(n);
        for (int i = 1; i <= n; i++)
        {
            double t = (double) i / n;
            hops.add(new int[]{
                fromX + (int) Math.round(dx * t),
                fromY + (int) Math.round(dy * t),
                fromZ + (int) Math.round(dz * t)
            });
        }
        return hops;
    }
/**
     * Plan a route to a SPECIFIC destination (e.g. a quest NPC, or a hostile we chose to hunt),
     * reusing the same hop-splitting as {@link #plan}. Returns null for a degenerate destination
     * (equal to the origin -> no travel), so the caller can fall back to a random far point.
     */
    public static RouteGoal routeTo(int fromX, int fromY, int fromZ,
                                    int destX, int destY, int destZ,
                                    String label, String reason)
    {
        if (fromX == 0 && fromY == 0)
        {
            return null; // not in-world yet
        }
        List<int[]> hops = buildHops(fromX, fromY, fromZ, destX, destY, destZ);
        if (hops.isEmpty())
        {
            return null; // already at the destination; nothing to route
        }
        return new RouteGoal(destX, destY, destZ, label, reason, hops);
    }

    private static double hypot(double dx, double dy)
    {
        return Math.sqrt(dx * dx + dy * dy);
    }
}
