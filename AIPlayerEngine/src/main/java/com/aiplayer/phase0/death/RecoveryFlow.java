package com.aiplayer.phase0.death;

/** MODE: PLACEHOLDER. Buff and heal-potion sends are commented out; REBUFFING/HEALING states do nothing yet. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.phase0.combat.ShotManager;
import com.aiplayer.phase0.movement.MovementController;

/**
 * Orchestrates post-respawn recovery:
 * 1. Wait for world load (CharInfo/ValidateLocation)
 * 2. Rebuff (self-buffs first, then buff potions if available)
 * 3. Restock soulshots
 * 4. Heal to full if needed
 * 5. Begin return-to-farm path
 *
 * FIXES applied during integration (2026-08-08):
 *  - import was com.aiplayer.phase0.L2JProtocol — real class is
 *    com.aiplayer.protocol.L2JProtocol (same issue as MovementController.java).
 *  - self.hpPercent does not exist on BotStateSnapshot (only hpCurrent/hpMax) —
 *    computed inline instead, same pattern already used in the Task 1 CombatAI
 *    patch's selectBestSkill().
 *  - shotManager.restockAfterDeath() does not exist anywhere in the four update
 *    files or in ShotManager.java as delivered. Replaced with disableShots(),
 *    which lets the normal tick()/enableShots() cycle re-engage on the next
 *    combat — functionally reasonable, but flagged here rather than silently
 *    assumed to be exactly what was intended. See INTEGRATION_ORDER.md item 4c.
 */
public final class RecoveryFlow {
    private static final long WORLD_LOAD_WAIT_MS = 3000;
    private static final long REBUFF_INTERVAL_MS = 800;
    private static final long HEAL_CHECK_INTERVAL_MS = 2000;

    public enum RecoveryState {
        IDLE,
        WAITING_FOR_WORLD,
        REBUFFING,
        RESTOCKING,
        HEALING,
        RETURNING,
        READY
    }

    private final String accountName;
    private final L2JProtocol protocol;
    private final MovementController movementController;
    private final ShotManager shotManager;

    private RecoveryState state = RecoveryState.IDLE;
    private long stateEnteredAt = 0;
    private int rebuffIndex = 0;
    private int farmCenterX, farmCenterY, farmCenterZ;
    private Graveyard respawnedAt;

    public RecoveryFlow(String accountName, L2JProtocol protocol,
                        MovementController movementController, ShotManager shotManager) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.movementController = movementController;
        this.shotManager = shotManager;
    }

    /**
     * Start recovery after respawn. farmCenter is the original hunting zone.
     */
    public void begin(Graveyard graveyard, int farmCenterX, int farmCenterY, int farmCenterZ) {
        this.respawnedAt = graveyard;
        this.farmCenterX = farmCenterX;
        this.farmCenterY = farmCenterY;
        this.farmCenterZ = farmCenterZ;
        transitionTo(RecoveryState.WAITING_FOR_WORLD);
    }

    /**
     * Call every tick. Returns true when recovery is complete (READY).
     */
    public boolean update() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        long now = System.currentTimeMillis();

        switch (state) {
            case WAITING_FOR_WORLD:
                // Wait for world to load + small human delay
                if (now - stateEnteredAt >= WORLD_LOAD_WAIT_MS) {
                    transitionTo(RecoveryState.REBUFFING);
                }
                break;

            case REBUFFING:
                // Phase 0: send self-buff skills from rotation
                if (now - stateEnteredAt >= REBUFF_INTERVAL_MS * (rebuffIndex + 1)) {
                    // TODO: integrate with CombatRotation self-buff list
                                        // protocol.sendUseSkill(buffSkillId, false, false); // no target field in 0x2F; target set via Action first
                    rebuffIndex++;
                    if (rebuffIndex >= 3) { // assume 3 self-buffs max
                        transitionTo(RecoveryState.RESTOCKING);
                    }
                }
                break;

            case RESTOCKING:
                // Re-enable soulshots / summon shots on next combat tick.
                shotManager.disableShots();
                transitionTo(RecoveryState.HEALING);
                break;

            case HEALING:
                if (now - stateEnteredAt >= HEAL_CHECK_INTERVAL_MS) {
                    int hpPct = self.hpMax > 0 ? (self.hpCurrent * 100 / self.hpMax) : 100;
                    if (hpPct < 90) {
                        // Use heal potion if available
                        // protocol.sendUseItem(healPotionItemId);
                        // If healer class, could self-heal here
                    } else {
                        transitionTo(RecoveryState.RETURNING);
                    }
                }
                break;

            case RETURNING:
                // Generate path from graveyard back to farm center
                ReturnToFarmPath path = ReturnToFarmPath.create(
                    respawnedAt.x, respawnedAt.y, respawnedAt.z,
                    farmCenterX, farmCenterY, farmCenterZ,
                    accountName.hashCode()
                );
                movementController.moveTo(path.getFirstWaypointX(),
                                          path.getFirstWaypointY(),
                                          path.getFirstWaypointZ());
                transitionTo(RecoveryState.READY);
                break;

            case READY:
                return true;

            case IDLE:
            default:
                break;
        }

        return state == RecoveryState.READY;
    }

    public RecoveryState getState() {
        return state;
    }

    public boolean isRecovering() {
        return state != RecoveryState.IDLE && state != RecoveryState.READY;
    }

    public void reset() {
        state = RecoveryState.IDLE;
        rebuffIndex = 0;
        respawnedAt = null;
    }

    private void transitionTo(RecoveryState newState) {
        this.state = newState;
        this.stateEnteredAt = System.currentTimeMillis();
    }
}
