package com.aiplayer.phase0.death;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;

/**
 * Detects death, manages death-state transitions, and coordinates
 * respawn timing with human-like delays.
 */
public final class DeathHandler {
    private static final long MIN_RESPAWN_DELAY_MS = 7000;   // 7s minimum
    private static final long MAX_RESPAWN_DELAY_MS = 20000;  // 20s maximum
    private static final long DEATH_CONFIRMATION_MS = 1500;  // confirm death after 1.5s

    private final String accountName;
    private final java.util.Random random; // deterministic per-bot, not random.nextDouble()
    private long deathDetectedAt = 0;
    private long respawnScheduledAt = 0;
    private boolean isDead = false;
    private boolean respawnPending = false;

    public DeathHandler(String accountName) {
        this.accountName = accountName;
        this.random = new java.util.Random(accountName.hashCode());
    }

    /**
     * Call every tick. Returns true if death state changed this tick.
     *
     * FIX applied during integration (2026-08-08): self.hpPercent does not exist
     * on BotStateSnapshot (only hpCurrent/hpMax) — computed inline below, same
     * pattern used in the Task 1 CombatAI patch and in RecoveryFlow.java.
     */
    public boolean update() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        int hpPct = self.hpMax > 0 ? (self.hpCurrent * 100 / self.hpMax) : 100;

        // Death detection: HP == 0 and confirmation window passed
        if (hpPct <= 0 && !isDead) {
            if (deathDetectedAt == 0) {
                deathDetectedAt = System.currentTimeMillis();
                return false; // wait for confirmation
            }
            if (System.currentTimeMillis() - deathDetectedAt >= DEATH_CONFIRMATION_MS) {
                isDead = true;
                respawnPending = true;
                // Human-like respawn delay: real players don't insta-respawn
                long delay = MIN_RESPAWN_DELAY_MS 
                    + (long) (random.nextDouble() * (MAX_RESPAWN_DELAY_MS - MIN_RESPAWN_DELAY_MS));
                respawnScheduledAt = System.currentTimeMillis() + delay;
                return true;
            }
        }

        // If HP restored while waiting for confirmation, cancel
        if (hpPct > 0 && deathDetectedAt != 0 && !isDead) {
            deathDetectedAt = 0;
        }

        return false;
    }

    public boolean isDead() {
        return isDead;
    }

    public boolean isRespawnReady() {
        return isDead && respawnPending && System.currentTimeMillis() >= respawnScheduledAt;
    }

    public void markRespawned() {
        isDead = false;
        respawnPending = false;
        deathDetectedAt = 0;
        respawnScheduledAt = 0;
    }

    public long getRespawnDelayRemaining() {
        if (!respawnPending) return 0;
        long remaining = respawnScheduledAt - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public long getDeathTime() {
        return isDead ? deathDetectedAt : 0;
    }
}
