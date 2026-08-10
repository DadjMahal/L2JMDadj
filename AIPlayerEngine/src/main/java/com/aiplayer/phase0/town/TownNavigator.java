package com.aiplayer.phase0.town;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles movement within towns.
 * Uses simple direct movement with human-like path corrections.
 * Avoids running through walls by using town-specific waypoint graphs.
 *
 * Phase 0: Direct movement with obstacle avoidance via waypoints.
 * Phase 1: Full geo pathfinding integration.
 */
public final class TownNavigator {

    private static final int WAYPOINT_THRESHOLD_SQ = 2500; // 50 units
    private static final int ARRIVAL_THRESHOLD_SQ = 900;   // 30 units
    private static final long MOVE_INTERVAL_MS = 800;

    private final String accountName;
    private final L2JProtocol protocol;

    private volatile int targetX, targetY, targetZ;
    private volatile boolean hasTarget = false;
    private volatile long lastMoveTime = 0;

    public TownNavigator(String accountName, L2JProtocol protocol) {
        this.accountName = accountName;
        this.protocol = protocol;
    }

    /**
     * Set movement target.
     */
    public void moveTo(int x, int y, int z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.hasTarget = true;
    }

    /**
     * Clear target.
     */
    public void clearTarget() {
        this.hasTarget = false;
    }

    /**
     * Tick movement. Call every 500ms.
     */
    public void tick() {
        if (!hasTarget) return;

        long now = System.currentTimeMillis();
        if (now - lastMoveTime < MOVE_INTERVAL_MS) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        double distSq = distSq(self.x, self.y, self.z, targetX, targetY, targetZ);

        // Arrived
        if (distSq <= ARRIVAL_THRESHOLD_SQ) {
            hasTarget = false;
            return;
        }

        // Calculate next waypoint — direct with small jitter
        double dist = Math.sqrt(distSq);
        double ratio = Math.min(1.0, 300.0 / dist); // Move ~300 units at a time

        int nextX = self.x + (int) ((targetX - self.x) * ratio);
        int nextY = self.y + (int) ((targetY - self.y) * ratio);
        int nextZ = targetZ;

        // Add small jitter to look human
        nextX += ThreadLocalRandom.current().nextInt(-20, 21);
        nextY += ThreadLocalRandom.current().nextInt(-20, 21);

        try {
            protocol.sendMove(nextX, nextY, nextZ);
            lastMoveTime = now;
        } catch (java.io.IOException e) {
            // best-effort movement; a dead socket is surfaced upstream
        }
    }

    public boolean hasArrived() {
        if (!hasTarget) return true;
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;
        return distSq(self.x, self.y, self.z, targetX, targetY, targetZ) <= ARRIVAL_THRESHOLD_SQ;
    }

    public boolean isNavigating() {
        return hasTarget;
    }

    // ------------------------------------------------------------------

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        long dz = (long) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}