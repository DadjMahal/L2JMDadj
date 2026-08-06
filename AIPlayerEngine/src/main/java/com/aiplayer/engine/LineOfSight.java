package com.aiplayer.engine;

import java.util.logging.Logger;

/**
 * Line of Sight and Terrain Collision Detection (Task 39)
 *
 * Provides methods for checking visibility between positions and detecting
 * potential obstacles (walls, terrain) that block line of sight.
 *
 * Uses Bresenham's line algorithm for ray casting between two points.
 */
public class LineOfSight {
    private static final Logger LOGGER = Logger.getLogger(LineOfSight.class.getName());

    // Cell size for grid-based collision detection (L2J uses 128x128 cell size)
    private static final int CELL_SIZE = 128;

    // Default collision map - would be loaded from server data in production
    // Format: cellX, cellY -> collision flags
    // This is a simplified implementation for demonstration
    private boolean[][] collisionGrid = new boolean[1000][1000]; // Simplified grid

    /**
     * Check if there is line of sight between two points (2D, ignoring Z)
     * Uses Bresenham's line algorithm to check for obstacles
     */
    public boolean hasLineOfSight(double x1, double y1, double x2, double y2) {
        int x1Int = (int) Math.floor(x1);
        int y1Int = (int) Math.floor(y1);
        int x2Int = (int) Math.floor(x2);
        int y2Int = (int) Math.floor(y2);

        int dx = Math.abs(x2Int - x1Int);
        int dy = Math.abs(y2Int - y1Int);
        int sx = x1Int < x2Int ? 1 : -1;
        int sy = y1Int < y2Int ? 1 : -1;
        int err = dx - dy;

        int x = x1Int;
        int y = y1Int;

        while (x != x2Int || y != y2Int) {
            // Check if this cell is blocked
            if (isCellBlocked(x, y)) {
                return false;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }

        return true;
    }

    /**
     * Check if there is line of sight considering height difference (3D)
     * Returns true if the height difference is not blocking (within climbable range)
     */
    public boolean hasLineOfSight3D(double x1, double y1, int z1, double x2, double y2, int z2) {
        // First check 2D line of sight
        if (!hasLineOfSight(x1, y1, x2, y2)) {
            return false;
        }

        // Check height difference
        int dz = Math.abs(z2 - z1);
        if (dz > 256) { // Too much height difference - can't target through walls
            return false;
        }

        return true;
    }

    /**
     * Check if a target path is clear for movement
     * Used for pathfinding and escape route calculation
     */
    public boolean isPathClear(double startX, double startY, double endX, double endY) {
        return hasLineOfSight(startX, startY, endX, endY);
    }

    /**
     * Check if the player can see an entity (for combat decisions)
     * Considers both spatial obstruction and height differences
     */
    public boolean canSeeEntity(double playerX, double playerY, int playerZ,
                                 double entityX, double entityY, int entityZ) {
        // Check basic line of sight first
        if (!hasLineOfSight(playerX, playerY, entityX, entityY)) {
            return false;
        }

        // Check if entity is behind a wall at player's position (height check)
        int dz = Math.abs(entityZ - playerZ);
        if (dz > 100) { // Entity is too high/low to see
            return false;
        }

        return true;
    }

    /**
     * Get the closest clear tile to a target position
     * Useful for finding a position with LOS when target is blocked
     */
    public double[] findClearTile(double startX, double startY, double targetX, double targetY, int maxDistance) {
        double angle = Math.atan2(targetY - startY, targetX - startX);

        for (int dist = 50; dist <= maxDistance; dist += 50) {
            double checkX = startX + Math.cos(angle) * dist;
            double checkY = startY + Math.sin(angle) * dist;

            if (hasLineOfSight(startX, startY, checkX, checkY)) {
                return new double[]{checkX, checkY};
            }
        }

        // No clear tile found
        return null;
    }

    /**
     * Check if a cell is blocked (wall, building, etc.)
     * In production, this would use actual map data from the game server
     */
    private boolean isCellBlocked(int x, int y) {
        // Convert world coordinates to cell coordinates
        int cellX = x / CELL_SIZE;
        int cellY = y / CELL_SIZE;

        // Bounds check
        if (cellX < 0 || cellY < 0 || cellX >= 1000 || cellY >= 1000) {
            return true; // Outside map
        }

        // Check collision grid
        // In production, this would be loaded from L2J map data
        return collisionGrid[cellX][cellY];
    }

    /**
     * Set a cell as blocked (for testing or dynamic obstacles)
     */
    public void setCellBlocked(int x, int y, boolean blocked) {
        int cellX = x / CELL_SIZE;
        int cellY = y / CELL_SIZE;

        if (cellX >= 0 && cellY >= 0 && cellX < 1000 && cellY < 1000) {
            collisionGrid[cellX][cellY] = blocked;
        }
    }

    /**
     * Check if a position is in water (additional obstacle factor)
     */
    public boolean isInWater(double x, double y) {
        // Simplified check - in production would use actual water zone data
        // Town areas typically have water features
        return (x > 15000 && x < 17000 && y > 15000 && y < 17000); // Example: near Gludio
    }

    /**
     * Check if movement is obstructed by terrain
     */
    public boolean isTerrainBlocking(double x, double y, double targetX, double targetY) {
        // Simple diagonal check
        double dx = Math.abs(targetX - x);
        double dy = Math.abs(targetY - y);
        double diag = Math.sqrt(dx * dx + dy * dy);

        // If moving diagonally through a narrow passage
        if (dx > 30 && dy > 30) {
            double midX = (x + targetX) / 2;
            double midY = (y + targetY) / 2;
            return hasLineOfSight(x, y, midX, midY) && hasLineOfSight(midX, midY, targetX, targetY);
        }

        return !hasLineOfSight(x, y, targetX, targetY);
    }

    /**
     * Calculate effective distance accounting for obstacles
     * Returns actual travel distance needed
     */
    public double getEffectiveDistance(double x1, double y1, double x2, double y2) {
        if (hasLineOfSight(x1, y1, x2, y2)) {
            return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        }

        // If blocked, estimate detour distance
        double directDist = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        return directDist * 1.5; // Estimate 50% extra for detour
    }

    /**
     * Clear all collision data (for zone changes)
     */
    public void reset() {
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j < 1000; j++) {
                collisionGrid[i][j] = false;
            }
        }
        LOGGER.fine("[LineOfSight] Collision grid reset");
    }
}
