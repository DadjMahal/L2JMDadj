package com.aiplayer.phase0.movement;

/**
 * MODE: COMPLETE. STEP 6 idle-relocation decision maker (pure, unit-testable, no IO).
 *
 * <p><b>Problem (live-verified 2026-08-16):</b> an out-of-hostile bot that gets sent on a
 * <i>random far-travel</i> relocation can stay frozen at {@code movedLast60=0}: the single
 * far {@code MoveToLocation} moves up close to the 9900u server cap toward a spot the server
 * never walks the char to (e.g. across an ocean / geo-block), so {@code movesSent} climbs but
 * the server-acked position never moves — the char idles forever in the void spot
 * {@code (16600,17000,434)}.
 *
 * <p><b>Fix:</b> instead of always choosing a random far point, when the bot is frozen
 * (its previous relocation route(s) were abandoned = zero server movement) route <b>back
 * toward the last XP-earning position</b> (where hostiles actually were) or, failing that,
 * the <b>nearest in-world fleet mate</b> (a clustered, reachable real target) before ever
 * falling back to a random point. A <b>consecutive-abandon escape gate</b> stops the re-plan
 * churn entirely: after {@value #MAX_CONSECUTIVE_ABANDONS} consecutive abandoned routes the
 * planner holds the bot still for {@value #ESCAPE_HOLD_MS}ms instead of re-issuing a doomed
 * far move every idle tick.
 *
 * <p>The final fallback (non-frozen, no last-XP, no mate) is a <b>real in-world landmark</b>
 * from the guide map ({@link com.aiplayer.phase0.guide.RaceGuide#idleAnchor}) rather than a
 * raw random point, so an idle bot always has a real, reachable displacement target (fixes the
 * void-spot idle). All coordinates returned are real server-space points; {@link ZoneRouter}
 * still slices them into server-accepted &le;{@value ZoneRouter#MAX_HOP_DIST}u hops.
 */

import java.util.List;
import java.util.Random;

import com.aiplayer.phase0.guide.PlayerRace;
import com.aiplayer.phase0.guide.QuestNode;
import com.aiplayer.phase0.guide.RaceGuide;

public final class RelocationPlanner
{
    /** Consecutive abandoned relocation routes before the escape gate trips (holds still). */
    public static final int MAX_CONSECUTIVE_ABANDONS = 3;

    /** How long the escape gate holds the bot still after tripping (breaks the re-plan churn). */
    public static final long ESCAPE_HOLD_MS = 60_000L;

    /** A relocation aim is only worth routing if it is at least this far (otherwise it's not "travel"). */
    private static final double MIN_RELOC_DIST = 900.0;

    /** Sanity bound: a fleet mate farther than this off-map is ignored. */
    private static final double MAX_MATE_DIST = 25_000.0;

    private final Random random;

    // --- last XP-earning position memory (where hostiles actually were) ---
    private boolean haveLastXp = false;
    private int lastXpX, lastXpY, lastXpZ;
    private long lastXpAtMs = Long.MIN_VALUE;

    // --- consecutive-abandon escape gate ---
    private int consecutiveAbandons = 0;
    private long escapeUntilMs = Long.MIN_VALUE;

    public RelocationPlanner(String accountName)
    {
        this.random = new Random(accountName.hashCode());
    }

    /** Remember where the bot was when it last earned XP (called on a StatusUpdate XP gain). */
    public void recordLastXp(int x, int y, int z)
    {
        this.haveLastXp = true;
        this.lastXpX = x;
        this.lastXpY = y;
        this.lastXpZ = z;
        this.lastXpAtMs = System.currentTimeMillis();
    }

    public boolean hasLastXp()
    {
        return haveLastXp;
    }

    public int lastXpX() { return lastXpX; }
    public int lastXpY() { return lastXpY; }
    public int lastXpZ() { return lastXpZ; }

    /**
     * Server actually walked the char (a hop was reached / combat resumed) — clear the freeze
     * counter so the escape gate re-arms only after a genuinely fresh set of abandons.
     */
    public void noteProgress()
    {
        consecutiveAbandons = 0;
        escapeUntilMs = Long.MIN_VALUE;
    }

    /** A relocation route was abandoned (server never walked us toward it = zero server movement). */
    public void noteAbandonedRoute()
    {
        consecutiveAbandons++;
        if (consecutiveAbandons >= MAX_CONSECUTIVE_ABANDONS)
        {
            escapeUntilMs = System.currentTimeMillis() + ESCAPE_HOLD_MS;
        }
    }

    public int consecutiveAbandons()
    {
        return consecutiveAbandons;
    }

    /** Escape gate is currently holding the bot still (no far relocation should be issued). */
    public boolean escapeHoldActive()
    {
        return System.currentTimeMillis() < escapeUntilMs;
    }

    /** True once the freeze counter reports the bot is not making forward progress. */
    public boolean isFrozen()
    {
        return consecutiveAbandons > 0;
    }

    /** A chosen relocation aim: real destination + human-readable label/reason. */
    public static final class Target
    {
        public final int x;
        public final int y;
        public final int z;
        public final String label;
        public final String reason;

        Target(int x, int y, int z, String label, String reason)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
            this.reason = reason;
        }
    }

    /**
     * Pick an idle-relocation destination.
     *
     * @param level      current bot level (guide-map hunt tier)
     * @param fromX/Y/Z  current position
     * @param frozen     true when the previous relocation route was abandoned (zero server movement)
     * @param mates      in-world fleet-mate {x,y,z} positions (may be empty / null)
     * @param race       bot race for the guide-map anchor (may be null -> skip anchor tier)
     * @param minRadius  smallest acceptable aim distance
     * @param maxRadius  largest acceptable aim distance
     * @return a real destination, or {@code null} when the caller should stay put (escape hold)
     */
    public Target choose(int level, int fromX, int fromY, int fromZ, boolean frozen,
                         List<int[]> mates, PlayerRace race,
                         double minRadius, double maxRadius)
    {
        if (escapeHoldActive())
        {
            return null; // escape gate: hold still, break the re-plan churn
        }

        double min = Math.max(MIN_RELOC_DIST, minRadius);

        // 1) Frozen: route back to where last XP was earned (hostiles live there) or to a mate.
        if (frozen)
        {
            Target t = lastXpTarget(fromX, fromY, fromZ, min, maxRadius);
            if (t != null)
            {
                return t;
            }
            t = mateTarget(fromX, fromY, fromZ, mates, min, maxRadius);
            if (t != null)
            {
                return t;
            }
        }

        // 2) Real guide-map landmark (race known) — always a reachable in-world point.
        Target t = anchorTarget(level, fromX, fromY, fromZ, race, min, maxRadius);
        if (t != null)
        {
            return t;
        }

        // 3) Bounded random far point (last resort), excluding a void/zero destination.
        return farPoint(fromX, fromY, fromZ, min, maxRadius);
    }

    private Target lastXpTarget(int fx, int fy, int fz, double min, double max)
    {
        if (!haveLastXp || (lastXpX == 0 && lastXpY == 0))
        {
            return null;
        }
        double d = dist(fx, fy, lastXpX, lastXpY);
        if (d < min || d > Math.min(max, 30_000.0))
        {
            return null; // too close to count, or absurdly far
        }
        return new Target(lastXpX, lastXpY, lastXpZ, "reloc:lastxp",
            String.format("frozen -> back to last XP spot (%.0f u)", d));
    }

    private Target mateTarget(int fx, int fy, int fz, List<int[]> mates, double min, double max)
    {
        if (mates == null || mates.isEmpty())
        {
            return null;
        }
        Target best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] m : mates)
        {
            if (m == null || m.length < 3 || (m[0] == 0 && m[1] == 0))
            {
                continue; // not in-world
            }
            double d = dist(fx, fy, m[0], m[1]);
            if (d >= min && d <= Math.min(max, MAX_MATE_DIST) && d < bestD)
            {
                best = new Target(m[0], m[1], m[2], "reloc:mate",
                    String.format("frozen -> nearest fleet mate (%.0f u)", d));
                bestD = d;
            }
        }
        return best;
    }

    private Target anchorTarget(int level, int fx, int fy, int fz, PlayerRace race,
                                double min, double max)
    {
        if (race == null)
        {
            return null;
        }
        QuestNode n = RaceGuide.idleAnchor(race, level);
        if (n == null || (n.x == 0 && n.y == 0))
        {
            return null;
        }
        double d = dist(fx, fy, n.x, n.y);
        if (d < min || d > max)
        {
            return null;
        }
        return new Target(n.x, n.y, n.z, "reloc:" + n.town,
            String.format("L%d guide landmark %s (%.0f u)", level, n.town, d));
    }

    private Target farPoint(int fx, int fy, int fz, double min, double max)
    {
        double r = min + random.nextDouble() * Math.max(0.0, max - min);
        double a = random.nextDouble() * 2.0 * Math.PI;
        int x = fx + (int) Math.round(Math.cos(a) * r);
        int y = fy + (int) Math.round(Math.sin(a) * r);
        return new Target(x, y, fz, "reloc:far-point",
            String.format("bounded random relocation ~%.0f u", r));
    }

    private static double dist(int x1, int y1, int x2, int y2)
    {
        return Math.sqrt((double) (x2 - x1) * (x2 - x1) + (double) (y2 - y1) * (y2 - y1));
    }
}

