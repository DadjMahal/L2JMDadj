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

    /** @param accountName per-bot name used to seed the (deterministic) random. */
    public ZoneRouter(String accountName)
    {
        this.accountName = accountName;
        this.random = new Random(accountName.hashCode());
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
            // 2) Bounded random far point (walked in hops below).
            double r = minRadius + random.nextDouble() * Math.max(0.0, maxRadius - minRadius);
            double a = random.nextDouble() * 2.0 * Math.PI;
            destX = fromX + (int) Math.round(Math.cos(a) * r);
            destY = fromY + (int) Math.round(Math.sin(a) * r);
            destZ = fromZ;
            label = "far-point";
            reason = String.format("%s random far point ~%.0f u", accountName, r);
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
     * @return ordered {x,y,z} waypoints; the last always equals dest; empty when dest == origin
     */
    static List<int[]> buildHops(int fromX, int fromY, int fromZ,
                                 int destX, int destY, int destZ)
    {
        double dx = destX - fromX;
        double dy = destY - fromY;
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
                destZ
            });
        }
        return hops;
    }

    private static double hypot(double dx, double dy)
    {
        return Math.sqrt(dx * dx + dy * dy);
    }
}
