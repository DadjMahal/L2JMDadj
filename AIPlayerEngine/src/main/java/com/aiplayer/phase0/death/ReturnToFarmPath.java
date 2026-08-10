package com.aiplayer.phase0.death;

/** MODE: PLACEHOLDER. Uses only the first generated waypoint, not the full path. */

import com.aiplayer.phase0.movement.BezierCurve;
import com.aiplayer.phase0.movement.HumanizedPath;
import com.aiplayer.phase0.movement.PathNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a segmented return path from graveyard to farm zone.
 * Uses HumanizedPath for natural movement, with intermediate waypoints
 * to avoid suspicious straight-line runs across the entire map.
 */
public final class ReturnToFarmPath {
    private static final double RUN_SPEED = 180.0;
    private static final int MAX_SEGMENT_DISTANCE = 2000; // waypoint every ~2k units

    private final List<PathNode> waypoints;
    private final int firstWaypointX;
    private final int firstWaypointY;
    private final int firstWaypointZ;

    private ReturnToFarmPath(List<PathNode> waypoints) {
        this.waypoints = waypoints;
        if (!waypoints.isEmpty()) {
            PathNode first = waypoints.get(0);
            this.firstWaypointX = first.x;
            this.firstWaypointY = first.y;
            this.firstWaypointZ = first.z;
        } else {
            this.firstWaypointX = 0;
            this.firstWaypointY = 0;
            this.firstWaypointZ = 0;
        }
    }

    /**
     * Create a return path broken into segments for natural movement.
     */
    public static ReturnToFarmPath create(int fromX, int fromY, int fromZ,
                                          int toX, int toY, int toZ, long seed) {
        // seed, not random.nextDouble(): pass a deterministic per-bot/per-call seed
        java.util.Random random = new java.util.Random(seed);
        double totalDist = Math.sqrt(Math.pow(toX - fromX, 2) + Math.pow(toY - fromY, 2));
        int segments = Math.max(1, (int) (totalDist / MAX_SEGMENT_DISTANCE));

        List<PathNode> allWaypoints = new ArrayList<>();

        int prevX = fromX, prevY = fromY, prevZ = fromZ;

        for (int i = 1; i <= segments; i++) {
            double ratio = i / (double) segments;
            int segX = fromX + (int) ((toX - fromX) * ratio);
            int segY = fromY + (int) ((toY - fromY) * ratio);
            int segZ = fromZ + (int) ((toZ - fromZ) * ratio);

            // Add small random offset to intermediate waypoints (not final)
            if (i < segments) {
                segX += (int) ((random.nextDouble() * 2 - 1) * 200);
                segY += (int) ((random.nextDouble() * 2 - 1) * 200);
            }

            HumanizedPath segPath = HumanizedPath.create(
                prevX, prevY, prevZ, segX, segY, segZ, RUN_SPEED);
            allWaypoints.addAll(segPath.getWaypoints());

            prevX = segX;
            prevY = segY;
            prevZ = segZ;
        }

        return new ReturnToFarmPath(allWaypoints);
    }

    public int getFirstWaypointX() { return firstWaypointX; }
    public int getFirstWaypointY() { return firstWaypointY; }
    public int getFirstWaypointZ() { return firstWaypointZ; }

    public List<PathNode> getWaypoints() {
        return waypoints;
    }

    public int getWaypointCount() {
        return waypoints.size();
    }
}
