package com.aiplayer.phase0.movement;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.GameStateMirror.EntitySnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.io.IOException;

/**
 * Orchestrates all AI Player movement.
 * Generates humanized paths, monitors progress, handles stuck recovery.
 *
 * FIXES applied during integration (2026-08-08), verified against the real
 * protocol class:
 *  - import was com.aiplayer.phase0.L2JProtocol — the real class lives in
 *    com.aiplayer.protocol, not under phase0.
 *  - sendMovePacket() called protocol.sendMoveBackwardToLocation(x,y,z,0,0,0),
 *    a method that does not exist. The real L2JProtocol only exposes
 *    sendMove(int x, int y, int z), which throws IOException — wrapped below.
 * See INTEGRATION_ORDER.md item 4b.
 */
public final class MovementController {
    private static final double WALK_SPEED_BASE = 120.0;
    private static final double RUN_SPEED_BASE = 180.0;
    private static final int ARRIVAL_THRESHOLD = 80;
    private static final long WAYPOINT_SEND_INTERVAL = 400;

    private final String accountName;
    private final L2JProtocol protocol;
    private final StuckDetector stuckDetector;
    private final KiteController kiteController;
    private final GeoPathfinder geoPathfinder;

    private MovementState state = MovementState.IDLE;
    private HumanizedPath currentPath;
    private int pathIndex = 0;
    private long lastWaypointTime = 0;
    private int destinationX, destinationY, destinationZ;
    private int currentTargetObjId = 0;

    public MovementController(String accountName, L2JProtocol protocol) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.stuckDetector = new StuckDetector(accountName);
        this.kiteController = new KiteController(accountName);
        this.geoPathfinder = new GeoPathfinder();
    }

    /**
     * Move to absolute coordinates.
     */
    public void moveTo(int x, int y, int z) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        double speed = self.isRunning ? RUN_SPEED_BASE : WALK_SPEED_BASE;
        currentPath = HumanizedPath.create(self.x, self.y, self.z, x, y, z, speed);
        pathIndex = 0;
        destinationX = x;
        destinationY = y;
        destinationZ = z;
        state = MovementState.MOVING;
        stuckDetector.recordStartPosition(self.x, self.y, self.z);
        sendNextWaypoint();
    }

    /**
     * Move within attack range of an entity.
     */
    public void moveToEntity(int objectId, int desiredDistance) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        EntitySnapshot ent = self.nearby.get(objectId);
        if (ent == null) return;

        currentTargetObjId = objectId;
        double dx = self.x - ent.x;
        double dy = self.y - ent.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= desiredDistance + 50) {
            state = MovementState.ARRIVING;
            return;
        }

        // Destination: desiredDistance away from entity, toward self
        double ratio = (dist - desiredDistance) / dist;
        int tx = ent.x + (int) (dx * ratio);
        int ty = ent.y + (int) (dy * ratio);
        moveTo(tx, ty, ent.z);
    }

    /**
     * Kite away from target (ranged combat).
     */
    public void kiteFrom(int targetObjId, int optimalDistance) {
        int[] pos = kiteController.getKitePosition(targetObjId, optimalDistance);
        if (pos != null) {
            state = MovementState.KITING;
            moveTo(pos[0], pos[1], pos[2]);
        }
    }

    /**
     * Follow a party member at given distance.
     */
    public void followEntity(int objectId, int followDistance) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        EntitySnapshot leader = self.nearby.get(objectId);
        if (leader == null) return;

        double dist = distance(self.x, self.y, leader.x, leader.y);
        if (dist < followDistance + 50) return; // close enough

        state = MovementState.FOLLOWING;
        currentTargetObjId = objectId;
        moveToEntity(objectId, followDistance);
    }

    /**
     * Flee from all aggro mobs.
     */
    public void flee() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Calculate average threat position from aggro mobs
        double avgX = 0, avgY = 0;
        int count = 0;
        for (EntitySnapshot e : self.nearby.values()) {
            if (e.isAggressive && !e.isDead) {
                avgX += e.x;
                avgY += e.y;
                count++;
            }
        }

        if (count == 0) {
            state = MovementState.IDLE;
            return;
        }

        avgX /= count;
        avgY /= count;

        // Run opposite direction
        double dx = self.x - avgX;
        double dy = self.y - avgY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int fx = self.x + (int) ((dx / dist) * 800);
        int fy = self.y + (int) ((dy / dist) * 800);

        state = MovementState.FLEEING;
        moveTo(fx, fy, self.z);
    }

    /**
     * Call every tick (100-200ms).
     */
    public void update() {
        if (state == MovementState.IDLE || state == MovementState.ARRIVING) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Stuck detection
        if (stuckDetector.update()) {
            state = MovementState.STUCK;
            if (stuckDetector.shouldGiveUp()) {
                // Abandon this path, go idle
                state = MovementState.IDLE;
                currentPath = null;
                stuckDetector.reset();
                return;
            }

            int[] recovery = stuckDetector.getRecoveryPosition(self.x, self.y, self.z);
            state = MovementState.RECOVERING;
            sendMovePacket(recovery[0], recovery[1], recovery[2]);
            return;
        }

        // Check arrival
        double distToDest = distance(self.x, self.y, destinationX, destinationY);
        if (distToDest < ARRIVAL_THRESHOLD) {
            state = MovementState.ARRIVING;
            currentPath = null;
            return;
        }

        // Dynamic target update (entity moved)
        if (currentTargetObjId != 0 && (state == MovementState.MOVING || state == MovementState.FOLLOWING)) {
            EntitySnapshot ent = self.nearby.get(currentTargetObjId);
            if (ent != null && (Math.abs(ent.x - destinationX) > 100 || Math.abs(ent.y - destinationY) > 100)) {
                // Target moved significantly, recalculate
                if (state == MovementState.FOLLOWING) {
                    followEntity(currentTargetObjId, 200);
                } else {
                    moveToEntity(currentTargetObjId, 100); // default approach distance
                }
                return;
            }
        }

        // Send next waypoint
        if (currentPath != null && pathIndex < currentPath.getWaypoints().size()) {
            long now = System.currentTimeMillis();
            if (now - lastWaypointTime > WAYPOINT_SEND_INTERVAL) {
                sendNextWaypoint();
            }
        }
    }

    private void sendNextWaypoint() {
        if (currentPath == null || pathIndex >= currentPath.getWaypoints().size()) return;

        PathNode node = currentPath.getWaypoints().get(pathIndex++);
        sendMovePacket(node.x, node.y, node.z);
        lastWaypointTime = System.currentTimeMillis();
    }

    private void sendMovePacket(int x, int y, int z) {
        try {
            protocol.sendMove(x, y, z);
        } catch (IOException e) {
            // Fail-soft: a dropped movement packet shouldn't crash the AI Player's
            // decision loop. Next tick will attempt the next waypoint regardless.
            System.err.println("[MovementController] sendMove failed for " + accountName + ": " + e.getMessage());
        }
    }

    public void stop() {
        state = MovementState.IDLE;
        currentPath = null;
        pathIndex = 0;
        currentTargetObjId = 0;
        stuckDetector.reset();
    }

    public boolean isMoving() {
        return state == MovementState.MOVING
            || state == MovementState.KITING
            || state == MovementState.FOLLOWING
            || state == MovementState.FLEEING
            || state == MovementState.RECOVERING;
    }

    public boolean hasArrived() {
        return state == MovementState.ARRIVING;
    }

    public MovementState getState() {
        return state;
    }

    public StuckDetector getStuckDetector() {
        return stuckDetector;
    }

    public KiteController getKiteController() {
        return kiteController;
    }

    private static double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
