package com.aiplayer.phase0.movement;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;

/**
 * Detects when AI Player is stuck on geometry or obstacles.
 * Triggers recovery with escalating strategies.
 */
public final class StuckDetector {
    private static final long CHECK_INTERVAL_MS = 1000;
    private static final long STUCK_THRESHOLD_MS = 3500;
    private static final double MIN_PROGRESS = 40.0;
    private static final int MAX_STUCK_COUNT = 3;

    private final String accountName;
    private final java.util.Random random; // deterministic per-bot, not random.nextDouble()
    private long lastCheckTime = 0;
    private int lastX, lastY, lastZ;
    private long stuckSince = 0;
    private int stuckCount = 0;
    private boolean isStuck = false;

    public StuckDetector(String accountName) {
        this.accountName = accountName;
        this.random = new java.util.Random(accountName.hashCode());
    }

    /**
     * Call every tick. Returns true if stuck state detected.
     */
    public boolean update() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return isStuck;
        lastCheckTime = now;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        double moved = distance(lastX, lastY, self.x, self.y);
        lastX = self.x;
        lastY = self.y;
        lastZ = self.z;

        if (moved < MIN_PROGRESS) {
            if (stuckSince == 0) stuckSince = now;
            if (now - stuckSince > STUCK_THRESHOLD_MS) {
                stuckCount++;
                isStuck = true;
            }
        } else {
            stuckSince = 0;
            isStuck = false;
            if (stuckCount > 0) stuckCount--;
        }

        return isStuck;
    }

    /**
     * Get recovery destination based on stuck count.
     * Escalating randomness: first try small hop, then larger, then random.
     */
    public int[] getRecoveryPosition(int currentX, int currentY, int currentZ) {
        int hopDistance = 150 + stuckCount * 100;
        double angle = random.nextDouble() * 2 * Math.PI;
        int rx = currentX + (int) (Math.cos(angle) * hopDistance);
        int ry = currentY + (int) (Math.sin(angle) * hopDistance);
        return new int[]{rx, ry, currentZ};
    }

    public boolean shouldGiveUp() {
        return stuckCount >= MAX_STUCK_COUNT;
    }

    public void reset() {
        stuckSince = 0;
        stuckCount = 0;
        isStuck = false;
    }

    public int getStuckCount() {
        return stuckCount;
    }

    public void recordStartPosition(int x, int y, int z) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.stuckSince = 0;
    }

    private static double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
