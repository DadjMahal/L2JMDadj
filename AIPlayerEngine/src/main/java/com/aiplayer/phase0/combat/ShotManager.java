package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.engine.AIPlayer;

/**
 * Manages soulshot and spiritshot auto-enable logic for AI Players.
 * Phase 0: Simulated shot state. Phase 1: Read from real inventory.
 */
public class ShotManager {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ShotManager.class.getName());
    private final AIPlayer aiPlayer;
    private boolean shotsEnabled = false;
    private int shotObjectId = 0;      // Item object ID in inventory
    private long lastShotCheckMs = 0;
    private static final long SHOT_CHECK_INTERVAL_MS = 30000;

    // Static shot item IDs (Interlude C4)
    public static final int SOULSHOT_NOGRADE = 1835;
    public static final int SOULSHOT_D = 1463;
    public static final int SOULSHOT_C = 1464;
    public static final int SOULSHOT_B = 1465;
    public static final int SOULSHOT_A = 1466;
    public static final int SOULSHOT_S = 1467;
    public static final int SPIRITSHOT_NOGRADE = 2509;
    public static final int SPIRITSHOT_D = 2510;
    public static final int SPIRITSHOT_C = 2511;
    public static final int SPIRITSHOT_B = 2512;
    public static final int SPIRITSHOT_A = 2513;
    public static final int SPIRITSHOT_S = 2514;
    public static final int BLESSED_SPIRITSHOT_NOGRADE = 3947;
    public static final int BLESSED_SPIRITSHOT_D = 3948;
    public static final int BLESSED_SPIRITSHOT_C = 3949;
    public static final int BLESSED_SPIRITSHOT_B = 3950;
    public static final int BLESSED_SPIRITSHOT_A = 3951;
    public static final int BLESSED_SPIRITSHOT_S = 3952;

    public ShotManager(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    /**
     * Enable shots for combat. Should be called when entering combat.
     */
    public void enableShots(int shotType) {
        if (shotsEnabled) return;
        shotsEnabled = true;
        // Phase 0: Log only. Phase 1: sendUseItem(shotObjectId)
        String shotName = shotType == 2 ? "Blessed Spiritshot" :
                         shotType == 1 ? "Soulshot" : "None";
        LOGGER.info("[SHOTS] " + aiPlayer.getName() + " enabled " + shotName);
    }

    /**
     * Disable shots when leaving combat or to conserve.
     */
    public void disableShots() {
        shotsEnabled = false;
    }

    /**
     * Check if shots should be re-enabled (called periodically).
     */
    public void tick(int desiredShotType) {
        long now = System.currentTimeMillis();
        if (now - lastShotCheckMs < SHOT_CHECK_INTERVAL_MS) return;
        lastShotCheckMs = now;

        if (desiredShotType > 0 && !shotsEnabled) {
            enableShots(desiredShotType);
        }
    }

    public boolean isShotsEnabled() {
        return shotsEnabled;
    }

    /**
     * Determine the best shot grade for the AI Player's level.
     */
    public static int getBestSoulshotGrade(int level) {
        if (level >= 76) return SOULSHOT_S;
        if (level >= 61) return SOULSHOT_A;
        if (level >= 52) return SOULSHOT_B;
        if (level >= 40) return SOULSHOT_C;
        if (level >= 20) return SOULSHOT_D;
        return SOULSHOT_NOGRADE;
    }

    public static int getBestSpiritshotGrade(int level) {
        if (level >= 76) return SPIRITSHOT_S;
        if (level >= 61) return SPIRITSHOT_A;
        if (level >= 52) return SPIRITSHOT_B;
        if (level >= 40) return SPIRITSHOT_C;
        if (level >= 20) return SPIRITSHOT_D;
        return SPIRITSHOT_NOGRADE;
    }

    public static int getBestBlessedSpiritshotGrade(int level) {
        if (level >= 76) return BLESSED_SPIRITSHOT_S;
        if (level >= 61) return BLESSED_SPIRITSHOT_A;
        if (level >= 52) return BLESSED_SPIRITSHOT_B;
        if (level >= 40) return BLESSED_SPIRITSHOT_C;
        if (level >= 20) return BLESSED_SPIRITSHOT_D;
        return BLESSED_SPIRITSHOT_NOGRADE;
    }

    // NOTE (added during integration, not in Kimi's original file): Task 4's
    // RecoveryFlow.java calls shotManager.restockAfterDeath() — that method does
    // not exist above and was never defined anywhere in the four update files.
    // Left out rather than guessed. See INTEGRATION_ORDER.md item 4c.
}
