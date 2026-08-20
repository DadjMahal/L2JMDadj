package com.aiplayer.behavior.movement;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.core.GameStateMirror.EntitySnapshot;
import com.aiplayer.core.BotSnapshot;

/**
 * Maintains optimal distance from target for ranged classes.
 * Adds slight random angle variation to look natural.
 */
public final class KiteController {
    private static final double ANGLE_VARIATION = 0.25; // radians

    private final String accountName;
    private final java.util.Random random; // deterministic per-bot, not random.nextDouble()

    public KiteController(String accountName) {
        this.accountName = accountName;
        this.random = new java.util.Random(accountName.hashCode());
    }

    /**
     * Returns kite destination [x,y,z] or null if no kite needed.
     */
    public int[] getKitePosition(int targetObjId, int optimalDistance) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return null;

        EntitySnapshot target = self.nearby.get(targetObjId);
        if (target == null) return null;

        double dx = self.x - target.x;
        double dy = self.y - target.y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist >= optimalDistance + 100) return null; // already far enough

        // Vector away from target, with random angle jitter
        double angle = Math.atan2(dy, dx) + (random.nextDouble() * 2 - 1) * ANGLE_VARIATION;
        int nx = self.x + (int) (Math.cos(angle) * optimalDistance);
        int ny = self.y + (int) (Math.sin(angle) * optimalDistance);

        return new int[]{nx, ny, self.z};
    }

    /**
     * Check if currently too close to target.
     */
    public boolean shouldKite(int targetObjId, int optimalDistance) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        EntitySnapshot target = self.nearby.get(targetObjId);
        if (target == null) return false;

        double dist = distance(self.x, self.y, target.x, target.y);
        return dist < optimalDistance - 100;
    }

    private static double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
