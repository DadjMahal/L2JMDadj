package com.aiplayer.behavior.movement;

/**
 * MODE: COMPLETE. EB-07 — the pure TOWN/REGION TRAVEL decision planner (behavior/movement).
 *
 * <p>Turns an abstract goal ("reach town X") into an executable sequence the fleet loop can
 * drive: teleport legs from RaceGuide's BFS gatekeeper/boat network + a per-leg WALK vs TELEPORT
 * decision (walk when close / teleport when affordable + level-ok / fallback walk).
 *
 * <p>Pure and deterministic: no IO, no sockets, no threads.
 */
public final class TravelPlanner
{
    private TravelPlanner()
    {
    }

    /** Euclidean distance at/below which we walk straight to the goal on foot. */
    public static final int WALK_THRESHOLD = 12_000;
    /** Maximum teleport cost the planner accepts outright (beyond: prefer walking). */
    public static final int MAX_TELEPORT_COST = 50_000;

    /** The chosen action for the next leg of a trip. */
    public enum Mode
    {
        /** Walk straight to the goal (short hop distance or no usable gatekeeper leg). */
        WALK,
        /** Teleport through the gatekeeper/boat network (leg exists, affordable, level-ok). */
        TELEPORT,
        /** No route at all — fall back to walking toward the goal's coordinates. */
        FALLBACK_WALK
    }

    /** A decided travel plan. */
    public static final class Plan
    {
        public final String fromTown;
        public final String toTown;
        /** What to do for the next leg (WALK or TELEPORT). */
        public final Mode mode;
        /** Walking destination for {@link Mode#WALK} / {@link Mode#FALLBACK_WALK} (xyz). */
        public final int walkX;
        public final int walkY;
        public final int walkZ;
        /** When teleporting: the exact gatekeeper leg to use (null in walk mode). */
        public final com.aiplayer.knowledge.TeleportLeg leg;
        /** Remaining legs to chain after the first (empty on a direct trip). */
        public final java.util.List<com.aiplayer.knowledge.TeleportLeg> tail;

        private Plan(String fromTown, String toTown, Mode mode, int walkX, int walkY, int walkZ,
                     com.aiplayer.knowledge.TeleportLeg leg,
                     java.util.List<com.aiplayer.knowledge.TeleportLeg> tail)
        {
            this.fromTown = fromTown;
            this.toTown = toTown;
            this.mode = mode;
            this.walkX = walkX;
            this.walkY = walkY;
            this.walkZ = walkZ;
            this.leg = leg;
            this.tail = tail != null ? tail : java.util.Collections.emptyList();
        }

        public boolean shouldTeleport()
        {
            return mode == Mode.TELEPORT;
        }

        public boolean shouldWalk()
        {
            return mode == Mode.WALK || mode == Mode.FALLBACK_WALK;
        }
    }

    // ================================================================
    // PLANNING (pure, deterministic)
    // ================================================================

    /**
     * Plan the next leg toward {@code goalTown}.
     *
     * @param goalTown   the town/zone name to reach (a town RaceGuide's teleport network knows)
     * @param fromTown   the bot's current town (may be "" when the loop has no labeled zone)
     * @param fromX/Y/Z  the bot's current position (walk-distance test vs the goal)
     * @param goalX/Y/Z  the goal's coordinates (walk target when we walk)
     * @param coins      current adena (teleport affordability)
     * @param level      current level (teleport level gate)
     * @param teleportEnabled whether gatekeeper teleports are enabled at all
     */
    public static Plan plan(String goalTown, String fromTown,
                            int fromX, int fromY, int fromZ,
                            int goalX, int goalY, int goalZ,
                            int coins, int level, boolean teleportEnabled)
    {
        if (goalTown == null || goalTown.isEmpty())
        {
            return new Plan(fromTown, "", Mode.FALLBACK_WALK, goalX, goalY, goalZ, null, null);
        }

        java.util.List<com.aiplayer.knowledge.TeleportLeg> route =
            com.aiplayer.knowledge.RaceGuide.route(fromTown == null ? "" : fromTown, goalTown);

        // 1. Walk when close (faster than walking to a gatekeeper then teleporting).
        long dx = (long) goalX - fromX;
        long dy = (long) goalY - fromY;
        long dz = (long) goalZ - fromZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= WALK_THRESHOLD)
        {
            return new Plan(fromTown, goalTown, Mode.WALK, goalX, goalY, goalZ, null, route);
        }

        // 2. Teleport when a usable first leg exists (affordable + level-ok).
        if (teleportEnabled && !route.isEmpty())
        {
            com.aiplayer.knowledge.TeleportLeg leg = route.get(0);
            if (coins >= leg.cost && level >= leg.requiredLevel && leg.cost <= MAX_TELEPORT_COST)
            {
                java.util.List<com.aiplayer.knowledge.TeleportLeg> tail = new java.util.ArrayList<>();
                for (int i = 1; i < route.size(); i++)
                {
                    tail.add(route.get(i));
                }
                return new Plan(fromTown, goalTown, Mode.TELEPORT, goalX, goalY, goalZ, leg, tail);
            }
        }

        // 3. Fallback: walk toward the goal's coordinates (reachable even without gatekeepers).
        return new Plan(fromTown, goalTown, Mode.FALLBACK_WALK, goalX, goalY, goalZ, null, route);
    }

    /** Convenience when the loop knows only coordinates (no town labels): plan a walk. */
    public static Plan planByCoords(int fromX, int fromY, int fromZ,
                                    int goalX, int goalY, int goalZ,
                                    int coins, int level, boolean teleportEnabled)
    {
        return new Plan("", "", Mode.FALLBACK_WALK, goalX, goalY, goalZ, null, null);
    }
}