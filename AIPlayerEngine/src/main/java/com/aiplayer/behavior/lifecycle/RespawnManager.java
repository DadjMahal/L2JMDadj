package com.aiplayer.behavior.lifecycle;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.behavior.movement.MovementController;
import com.aiplayer.core.BotSnapshot;

/**
 * Handles graveyard selection and respawn packet dispatch.
 *
 * FIXES applied during integration (2026-08-08): import path corrected (same
 * issue as MovementController.java/RecoveryFlow.java); hpPercent/mpPercent
 * writes replaced with the real hpCurrent/hpMax/mpCurrent/mpMax fields.
 *
 * NOT fixed, flagged instead: findNearest() below takes self.level, but
 * BotStateSnapshot has no level field anywhere in Phase 0 or any of the four
 * patch sets — this isn't a rename, it's a genuinely missing capability. Left
 * as-is rather than guessed; see INTEGRATION_ORDER.md item 4d for the two
 * reasonable options.
 */
public final class RespawnManager {
    private final String accountName;
    private final L2JProtocol protocol;

    public RespawnManager(String accountName, L2JProtocol protocol) {
        this.accountName = accountName;
        this.protocol = protocol;
    }

    /**
     * Select nearest graveyard to death location and send respawn request.
     */
    public Graveyard selectAndRespawn() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return null;

        Graveyard gy = GraveyardRegistry.findNearest(self.x, self.y, self.level); // self.level: see class-level note above
        if (gy == null) {
            // Fallback: use a generic low-level graveyard
            gy = new Graveyard("Fallback", -14225, 123540, -3120, 1, 80);
        }

        // L2JMobius: RequestRestartPoint packet (type = 1 = to village)
        try {
            protocol.sendRequestRestartPoint(1);
        } catch (java.io.IOException e) {
            // best-effort; a dead socket is surfaced upstream
        }

        return gy;
    }

    /**
     * After respawn, update GameStateMirror with new position.
     */
    public void confirmRespawnLocation(Graveyard gy) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Update mirror with graveyard position (approximate until CharInfo packet)
        self.x = gy.x;
        self.y = gy.y;
        self.z = gy.z;
        self.hpCurrent = self.hpMax; // respawn at full HP
        self.mpCurrent = self.mpMax;
        self.isDead = false;
        self.isMoving = false;
    }
}
