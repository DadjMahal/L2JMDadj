package com.aiplayer.behavior.movement;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.ArrayList;
import java.util.List;

/**
 * Quadratic Bezier curve generator for human-like smooth paths.
 * Real players rarely move in perfectly straight lines.
 */
public final class BezierCurve {

    /**
     * Generate curved waypoints between start and end.
     * @param curveStrength 0.0=straight, 0.5=strong curve (typical 0.15-0.25)
     */
    public static List<PathNode> generate(
            int x1, int y1, int z1,
            int x2, int y2, int z2,
            double curveStrength,
            int segments,
            double speed,
            long seed) {

        // seed, not random.nextDouble(): callers pass a deterministic per-bot seed
        // (e.g. accountName.hashCode() + a call counter) so path generation
        // is reproducible for testing, per the determinism rule.
        java.util.Random random = new java.util.Random(seed);
        List<PathNode> nodes = new ArrayList<>(segments + 1);

        // Control point: midpoint + perpendicular offset
        double midX = (x1 + x2) / 2.0;
        double midY = (y1 + y2) / 2.0;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) {
            nodes.add(new PathNode(x2, y2, z2, 0, speed));
            return nodes;
        }

        // Perpendicular vector, normalized
        double perpX = -dy / dist;
        double perpY = dx / dist;

        // Random offset: ±curveStrength * distance
        double offset = (random.nextDouble() * 2 - 1) * curveStrength * dist;
        double cx = midX + perpX * offset;
        double cy = midY + perpY * offset;

        long totalTimeMs = (long) (dist / speed * 1000.0);

        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double it = 1.0 - t;

            // Quadratic Bezier: B(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂
            double bx = it * it * x1 + 2 * it * t * cx + t * t * x2;
            double by = it * it * y1 + 2 * it * t * cy + t * t * y2;
            double bz = z1 + (z2 - z1) * t;

            long arrival = (long) (totalTimeMs * t);
            nodes.add(new PathNode((int) bx, (int) by, (int) bz, arrival, speed));
        }

        return nodes;
    }
}
