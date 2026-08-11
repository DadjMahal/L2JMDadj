package com.aiplayer.phase0.movement;

/** MODE: COMPLETE. Pure decision-maker: when a bot is idle with no target, pick a real FAR
 *  destination so it demonstrably "travels" (TIM-001 H3). Uses level-appropriate farm-zone
 *  centers from the real Interlude zone database (ZoneRecommender); falls back to a bounded
 *  random far point. No IO, no packet sending — returns a goal for the fleet loop to send
 *  through the proven Phase0Wiring.moveTo() path. Seeded Random per bot for determinism. */

import java.util.List;
import java.util.Random;

import com.aiplayer.phase0.quest.ZoneRecommender;
import com.aiplayer.phase0.quest.ZoneRecommender.ZoneInfo;

public final class ZoneRouter
{
    /** A chosen far-travel destination. */
    public static final class RouteGoal
    {
        public final int x;
        public final int y;
        public final int z;
        public final String label;   // e.g. "farm:Gludio Plains" or "far-point"
        public final String reason;  // human-readable why

        RouteGoal(int x, int y, int z, String label, String reason)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
            this.reason = reason;
        }
    }

    /** Prefer a zone-center route when it is at least this far away (otherwise it's not "travel"). */
    private static final double MIN_ZONE_TRAVEL_DIST = 1500.0;

    private final String accountName;
    private final Random random;

    /** @param accountName per-bot name used to seed the (deterministic) random. */
    public ZoneRouter(String accountName)
    {
        this.accountName = accountName;
        this.random = new Random(accountName.hashCode());
    }

    /**
     * Pick the next far destination for a bot that is idle (no hostile target in range).
     *
     * @param level      current bot level (seeded chars come in at 20/22; organic later)
     * @param fromX/Y/Z  current live position (must be real server-acked coords, never 0/0/0)
     * @param minRadius  smallest acceptable travel distance
     * @param maxRadius  largest acceptable travel distance
     * @return a RouteGoal, or {@code null} if the caller should stay put (degenerate input)
     */
    public RouteGoal pick(int level, int fromX, int fromY, int fromZ,
                          double minRadius, double maxRadius)
    {
        if (fromX == 0 && fromY == 0)
        {
            return null; // not in-world yet
        }

        // 1) Nearest level-appropriate farm zone center that is far enough to count as travel.
        List<ZoneInfo> zones = ZoneRecommender.getZonesForLevel(level);
        if (!zones.isEmpty())
        {
            ZoneInfo best = null;
            double bestDist = Double.MAX_VALUE;
            for (ZoneInfo z : zones)
            {
                double d = hypot(z.centerX - fromX, z.centerY - fromY);
                if (d >= MIN_ZONE_TRAVEL_DIST && d < bestDist)
                {
                    best = z;
                    bestDist = d;
                }
            }
            if (best != null && bestDist <= maxRadius)
            {
                return new RouteGoal(best.centerX, best.centerY, best.centerZ,
                    "farm:" + best.name,
                    String.format("L%d farm zone %s at %.0f u", level, best.name, bestDist));
            }
        }

        // 2) Bounded random far point (validator-friendly: server accepts MoveToLocation for
        //    arbitrary distance; position samples will confirm actual travel).
        double r = minRadius + random.nextDouble() * Math.max(0.0, maxRadius - minRadius);
        double a = random.nextDouble() * 2.0 * Math.PI;
        int tx = fromX + (int) Math.round(Math.cos(a) * r);
        int ty = fromY + (int) Math.round(Math.sin(a) * r);
        return new RouteGoal(tx, ty, fromZ, "far-point",
            String.format("%s random far point ~%.0f u", accountName, r));
    }

    private static double hypot(double dx, double dy)
    {
        return Math.sqrt(dx * dx + dy * dy);
    }
}