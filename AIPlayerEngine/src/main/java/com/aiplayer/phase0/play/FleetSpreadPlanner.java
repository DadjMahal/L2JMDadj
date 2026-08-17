package com.aiplayer.phase0.play;

import java.util.Collections;
import java.util.List;

import com.aiplayer.phase0.guide.HuntZone;
import com.aiplayer.phase0.guide.PlayerRace;
import com.aiplayer.phase0.guide.QuestNode;
import com.aiplayer.phase0.guide.RaceGuide;

/**
 * MODE: COMPLETE. Pure anti-clustering helper: given this bot's position/level and the fleet's
 * current positions, it picks a REAL hunt anchor that avoids where fleet mates already are, so the
 * fleet spreads across zones like players instead of camping one gang spot. Stateless (mirrors the
 * standalone RelocationPlanner pattern the fleet launcher calls) — no IO, no sockets, deterministic.
 */
public final class FleetSpreadPlanner
{
    /** Sanity cap on how far a bot will relocate just to spread out (world units). */
    private static final int MAX_RELOCATE_DIST = 300_000;

    private FleetSpreadPlanner()
    {
    }

    /** A fleet mate's current position (from BotSnapshot). */
    public static final class FleetPeer
    {
        public final int x;
        public final int y;
        public final int z;

        public FleetPeer(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** A chosen hunt spot plus how many mates are already inside it. */
    public static final class SpreadAnchor
    {
        public final int x;
        public final int y;
        public final int z;
        public final String zoneName;
        public final int mateCount;

        public SpreadAnchor(int x, int y, int z, String zoneName, int mateCount)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.zoneName = zoneName != null ? zoneName : "";
            this.mateCount = mateCount;
        }
    }

    /**
     * Pick the least-crowded reachable hunt zone for this level band and position. Ties break by
     * nearest distance, then lowest x, then lowest y (deterministic — no Random). Falls back to a
     * real guide landmark (never the void) when no zone is reachable.
     */
    public static SpreadAnchor pickAnchor(int level, int selfX, int selfY, int selfZ,
                                          List<FleetPeer> fleet)
    {
        List<HuntZone> zones = RaceGuide.huntZones(Math.max(1, level - 5), level + 5);
        List<FleetPeer> peers = fleet != null ? fleet : Collections.emptyList();

        if (zones.isEmpty())
        {
            return landmarkFallback(level, peers.size());
        }

        HuntZone best = null;
        int bestMates = Integer.MAX_VALUE;
        long bestDistSq = Long.MAX_VALUE;
        long maxSq = (long) MAX_RELOCATE_DIST * MAX_RELOCATE_DIST;

        for (HuntZone z : zones)
        {
            long d2 = distSq(selfX, selfY, selfZ, z.x, z.y, z.z);
            if (d2 > maxSq)
            {
                continue; // too far to bother relocating
            }
            int mates = countMates(z, peers);
            if (best == null || mates < bestMates
                || (mates == bestMates && (d2 < bestDistSq
                    || (d2 == bestDistSq && (z.x < best.x
                        || (z.x == best.x && z.y < best.y))))))
            {
                best = z;
                bestMates = mates;
                bestDistSq = d2;
            }
        }

        if (best == null)
        {
            // No hunt zone reachable; hand back a real guide landmark so the bot never idles on void.
            return landmarkFallback(level, peers.size());
        }
        return new SpreadAnchor(best.x, best.y, best.z, best.name, bestMates);
    }

    /** Count how many fleet peers fall inside the hunt zone's radius. */
    private static int countMates(HuntZone z, List<FleetPeer> peers)
    {
        int n = 0;
        long r2 = (long) z.radius * z.radius;
        for (FleetPeer p : peers)
        {
            if (p != null && distSq(p.x, p.y, p.z, z.x, z.y, z.z) <= r2)
            {
                n++;
            }
        }
        return n;
    }

    /** Real-world landmark fallback (never the void spot). */
    private static SpreadAnchor landmarkFallback(int level, int mateCount)
    {
        QuestNode a = RaceGuide.idleAnchor(PlayerRace.HUMAN, level);
        return new SpreadAnchor(a.x, a.y, a.z, a.name, mateCount);
    }

    private static long distSq(int ax, int ay, int az, int bx, int by, int bz)
    {
        long dx = (long) ax - bx;
        long dy = (long) ay - by;
        long dz = (long) az - bz;
        return dx * dx + dy * dy + dz * dz;
    }
}
