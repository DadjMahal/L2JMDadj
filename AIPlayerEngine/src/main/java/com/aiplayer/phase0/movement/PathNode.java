package com.aiplayer.phase0.movement;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * A waypoint in a movement path.
 */
public final class PathNode {
    public final int x;
    public final int y;
    public final int z;
    public final long expectedArrivalMs;
    public final double speed;

    public PathNode(int x, int y, int z, long expectedArrivalMs, double speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.expectedArrivalMs = expectedArrivalMs;
        this.speed = speed;
    }

    @Override
    public String toString() {
        return String.format("Node[%d,%d,%d]", x, y, z);
    }
}
