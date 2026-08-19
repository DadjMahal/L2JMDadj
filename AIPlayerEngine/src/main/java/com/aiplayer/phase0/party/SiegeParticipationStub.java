package com.aiplayer.phase0.party;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.movement.MovementController;
import com.aiplayer.behavior.combat.CombatAI;

/**
 * Phase 0 stub for siege participation.
 * Provides framework for castle siege, territory war, and
 * fortress combat automation.
 *
 * Phase 0 scope:
 * - Move to siege zone when clan siege is active
 * - Basic flag attack/defend proximity
 * - Death and respawn at siege camp
 * - NO advanced tactics (reserved for Phase 1)
 */
public final class SiegeParticipationStub {

    public enum SiegeState {
        IDLE,           // No active siege
        TRAVELING,      // Moving to siege location
        ATTACKING,      // Attacking enemy/castle
        DEFENDING,      // Defending friendly castle
        DEAD_WAITING,   // Dead, waiting for rez or respawn
        RETREATING      // Too dangerous, falling back
    }

    private final String accountName;
    private final ClanChatHandler clanChat;
    private final AntiDetectionEngine anti;
    private final MovementController movement;

    private SiegeState state = SiegeState.IDLE;
    private String targetCastle = null;
    private int siegeX = 0;
    private int siegeY = 0;
    private int siegeZ = 0;
    private long nextActionTime = 0;
    private int deathCount = 0;
    private static final int MAX_SIEGE_DEATHS = 5;

    public SiegeParticipationStub(String accountName, ClanChatHandler clanChat,
                                  AntiDetectionEngine anti, MovementController movement) {
        this.accountName = accountName;
        this.clanChat = clanChat;
        this.anti = anti;
        this.movement = movement;
    }

    /**
     * Main tick — call every 5s during active siege.
     */
    public void tick(BotStateSnapshot self) {
        if (!clanChat.isSiegeActive()) {
            state = SiegeState.IDLE;
            return;
        }
        if (System.currentTimeMillis() < nextActionTime) return;

        switch (state) {
            case IDLE:
                enterSiege(self);
                break;
            case TRAVELING:
                handleTraveling(self);
                break;
            case ATTACKING:
            case DEFENDING:
                handleCombat(self);
                break;
            case DEAD_WAITING:
                handleDead(self);
                break;
            case RETREATING:
                handleRetreat(self);
                break;
        }
    }

    /**
n     * Notify death during siege.
     */
    public void onDeath() {
        deathCount++;
        if (deathCount >= MAX_SIEGE_DEATHS) {
            state = SiegeState.RETREATING;
        } else {
            state = SiegeState.DEAD_WAITING;
        }
        nextActionTime = System.currentTimeMillis() + 15000; // Wait for rez
    }

    // ================================================================
    // STATE HANDLERS
    // ================================================================

    private void enterSiege(BotStateSnapshot self) {
        targetCastle = clanChat.getSiegeCastle();
        if (targetCastle == null) return;

        // Look up castle coordinates (simplified)
        int[] coords = getCastleCoords(targetCastle);
        siegeX = coords[0];
        siegeY = coords[1];
        siegeZ = coords[2];

        state = SiegeState.TRAVELING;
        nextActionTime = System.currentTimeMillis() + anti.getDelay(null);
    }

    private void handleTraveling(BotStateSnapshot self) {
        movement.moveTo(siegeX, siegeY, siegeZ);
        double dist = Math.hypot(self.x - siegeX, self.y - siegeY);
        if (dist < 1000) {
            // Arrived — determine attack or defend based on clan castle ownership
            state = SiegeState.ATTACKING; // Simplified
        }
        nextActionTime = System.currentTimeMillis() + anti.getMovementInterval();
    }

    private void handleCombat(BotStateSnapshot self) {
        // Phase 0: CombatAI handles actual fighting
        // This stub just monitors for retreat conditions
        if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) < 20) {
            state = SiegeState.RETREATING;
        }
        nextActionTime = System.currentTimeMillis() + 5000;
    }

    private void handleDead(BotStateSnapshot self) {
        if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) > 0) {
            // Resurrected
            state = SiegeState.TRAVELING;
        } else {
            // Wait for rez or respawn at siege camp
            nextActionTime = System.currentTimeMillis() + 10000;
        }
    }

    private void handleRetreat(BotStateSnapshot self) {
        // Move to siege camp or safe zone
        int[] safe = getSafeRetreatCoords(targetCastle);
        movement.moveTo(safe[0], safe[1], safe[2]);
        double dist = Math.hypot(self.x - safe[0], self.y - safe[1]);
        if (dist < 500) {
            // Safe — stay here until siege ends or HP recovers
            if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) > 80) {
                state = SiegeState.ATTACKING; // Re-engage cautiously
                deathCount = 0;
            }
        }
        nextActionTime = System.currentTimeMillis() + anti.getMovementInterval();
    }

    // ================================================================
    // COORDINATES (Interlude castles)
    // ================================================================

    private int[] getCastleCoords(String castle) {
        switch (castle.toLowerCase()) {
            case "gludio": return new int[]{-14440, 121064, -3000};
            case "dion": return new int[]{22252, 156376, -3000};
            case "giran": return new int[]{117024, 145272, -3000};
            case "oren": return new int[]{83040, 37184, -3000};
            case "aden": return new int[]{147960, 26928, -3000};
            case "goddard": return new int[]{147704, -45432, -3000};
            case "rune": return new int[]{43936, -47760, -3000};
            case "schuttgart": return new int[]{87024, -142480, -3000};
            default: return new int[]{0, 0, 0};
        }
    }

    private int[] getSafeRetreatCoords(String castle) {
        int[] base = getCastleCoords(castle);
        return new int[]{base[0] + 2000, base[1] + 2000, base[2]};
    }

    public SiegeState getState() {
        return state;
    }

    public String getStatusReport() {
        return String.format("Siege[%s: state=%s castle=%s deaths=%d]",
            accountName, state, targetCastle, deathCount);
    }
}
