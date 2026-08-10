package com.aiplayer.phase0.movement;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a humanized movement path with Bezier curves,
 * micro-deviations, and optional micro-pauses.
 */
public final class HumanizedPath {
    private static final double CURVE_STRENGTH = 0.18;
    private static final double MICRO_DEVIATION = 15.0;
    private static final double MICRO_PAUSE_CHANCE = 0.08;
    private static final int MICRO_PAUSE_MS = 150;

    private final List<PathNode> waypoints;
    private final boolean hasMicroPause;
    private final long pauseAtMs;
    private final Random random = new Random();

    private HumanizedPath(List<PathNode> waypoints, boolean hasMicroPause, long pauseAtMs) {
        this.waypoints = waypoints;
        this.hasMicroPause = hasMicroPause;
        this.pauseAtMs = pauseAtMs;
    }

    /**
     * Create a humanized path from start to destination.
     */
    public static HumanizedPath create(
            int fromX, int fromY, int fromZ,
            int toX, int toY, int toZ,
            double speed) {

        double dist = distance(fromX, fromY, toX, toY);
        int segments = Math.max(3, (int) (dist / 300.0)); // waypoint every ~300 units

        Random random = new Random();

        List<PathNode> raw = BezierCurve.generate(
                fromX, fromY, fromZ, toX, toY, toZ,
                CURVE_STRENGTH, segments, speed, random.nextLong());

        // Add micro-deviations
        List<PathNode> deviated = new ArrayList<>(raw.size());
        for (PathNode n : raw) {
            int dx = (int) ((random.nextDouble() * 2 - 1) * MICRO_DEVIATION);
            int dy = (int) ((random.nextDouble() * 2 - 1) * MICRO_DEVIATION);
            deviated.add(new PathNode(n.x + dx, n.y + dy, n.z, n.expectedArrivalMs, n.speed));
        }

        // 8% chance of a 150ms micro-pause mid-path (human hesitation)
        boolean pause = random.nextDouble() < MICRO_PAUSE_CHANCE;
        long pauseAt = pause ? (long) (deviated.get(deviated.size() / 2).expectedArrivalMs * 0.6) : 0;

        return new HumanizedPath(deviated, pause, pauseAt);
    }

    public List<PathNode> getWaypoints() {
        return waypoints;
    }

    public boolean hasMicroPause() {
        return hasMicroPause;
    }

    public long getPauseAtMs() {
        return pauseAtMs;
    }

    public PathNode getFinalNode() {
        return waypoints.get(waypoints.size() - 1);
    }

    private static double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
