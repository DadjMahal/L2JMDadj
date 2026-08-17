package com.aiplayer.phase0.town;

/** MODE: PARTIAL. PacketLogger now threaded through to BuyManager/WarehouseManager this pass. Rest of the class still unmigrated. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.util.List;

/**
 * Main orchestrator for all town/vendor automation.
 * Integrates sell, buy, warehouse, teleport, and town navigation.
 *
 * Trigger conditions:
 * - Inventory full or heavy -> go to town, sell junk, warehouse valuables
 * - Low consumables -> buy shots/potions/arrows
 * - Death respawn in town -> sell/warehouse/buy in sequence
 * - Manual return to farm -> teleport if worth cost
 *
 * State machine:
 * IDLE -> SELL -> WAREHOUSE -> BUY -> TELEPORT -> IDLE
 */
public final class TownBehaviorEngine {

    public enum TownState {
        IDLE,           // Not in town mode
        TRAVELING,      // Walking to town
        SELLING,        // Selling junk items
        WAREHOUSING,    // Depositing valuables
        BUYING,         // Buying consumables
        TELEPORTING,    // Using gatekeeper
        DONE            // Finished, return to farm
    }

    private final String accountName;
    private final L2JProtocol protocol;
    private ItemValueEstimator estimator; // level-dependent; refreshed each tick
    private final TownNavigator navigator;
    private final SellManager sellManager;
    private final BuyManager buyManager;
    private final WarehouseManager warehouseManager;
    private final TeleportManager teleportManager;

    private volatile TownState state = TownState.IDLE;
    private volatile String targetTown = null;
    private volatile String returnDestination = null;
    private volatile boolean enabled = true;

    private final com.aiplayer.protocol.PacketLogger packetLogger;

    public TownBehaviorEngine(String accountName, L2JProtocol protocol,
                              com.aiplayer.protocol.PacketLogger packetLogger, String playerClass) {
        this.packetLogger = packetLogger;
        this.accountName = accountName;
        this.protocol = protocol;
        this.navigator = new TownNavigator(accountName, protocol);
        this.estimator = new ItemValueEstimator(accountName, 1, playerClass); // level updated dynamically
        this.sellManager = new SellManager(accountName, protocol, estimator, navigator);
        this.buyManager = new BuyManager(accountName, protocol, packetLogger, navigator, playerClass);
        this.warehouseManager = new WarehouseManager(accountName, protocol, packetLogger, estimator, navigator);
        this.teleportManager = new TeleportManager(accountName, protocol, navigator);
    }

    /**
     * Trigger town run. Call when inventory full, death respawn, or low consumables.
     */
    public void startTownRun(String town, String returnDest) {
        this.targetTown = town;
        this.returnDestination = returnDest;
        this.state = TownState.SELLING;
        sellManager.startSelling();
    }

    /**
     * Main tick — call every 500ms.
     */
    public void tick() {
        if (!enabled || state == TownState.IDLE) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Update estimator with current level
        estimator = new ItemValueEstimator(accountName, self.level, self.playerClass);

        // Navigator tick always runs
        navigator.tick();

        switch (state) {
            case TRAVELING:
                tickTraveling(self);
                break;
            case SELLING:
                tickSelling(self);
                break;
            case WAREHOUSING:
                tickWarehousing(self);
                break;
            case BUYING:
                tickBuying(self);
                break;
            case TELEPORTING:
                tickTeleporting(self);
                break;
            case DONE:
                state = TownState.IDLE;
                break;
            default:
                break;
        }
    }

    /**
     * Check if we should trigger a town run.
     */
    public boolean shouldGoToTown() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return false;

        boolean inventoryFull = self.inventorySlotsUsed >= self.inventorySlotsMax - 2;
        boolean heavyJunk = estimator.shouldGoSell(self.inventory, self.inventorySlotsMax, self.inventorySlotsUsed, self.adena);
        boolean needRestock = estimator.shouldRestock(self.inventory);

        return inventoryFull || heavyJunk || needRestock;
    }

    public boolean isIdle() {
        return state == TownState.IDLE;
    }

    public TownState getState() {
        return state;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void reset() {
        state = TownState.IDLE;
        sellManager.reset();
        buyManager.reset();
        warehouseManager.reset();
        teleportManager.reset();
        navigator.clearTarget();
    }

    // ------------------------------------------------------------------

    private void tickTraveling(BotStateSnapshot self) {
        // Phase 1: Use ReturnToFarmPath in reverse, or Scroll of Escape
        // Phase 0: Assume we're already in town (death respawn or manual walk)
        if (targetTown != null && targetTown.equals(VendorDatabase.detectTown(self.x, self.y, self.z))) {
            state = TownState.SELLING;
            sellManager.startSelling();
        }
    }

    private void tickSelling(BotStateSnapshot self) {
        sellManager.tick();
        if (sellManager.isDone()) {
            state = TownState.WAREHOUSING;
            warehouseManager.startDeposit();
        }
    }

    private void tickWarehousing(BotStateSnapshot self) {
        warehouseManager.tick();
        if (warehouseManager.isDone()) {
            state = TownState.BUYING;
            buyManager.startShopping();
        }
    }

    private void tickBuying(BotStateSnapshot self) {
        buyManager.tick();
        if (buyManager.isDone()) {
            // Decide: teleport back or walk
            if (returnDestination != null && teleportManager.shouldTeleport(returnDestination, 600)) {
                state = TownState.TELEPORTING;
                teleportManager.teleportTo(returnDestination);
            } else {
                state = TownState.DONE;
            }
        }
    }

    private void tickTeleporting(BotStateSnapshot self) {
        teleportManager.tick();
        if (teleportManager.isDone()) {
            state = TownState.DONE;
        }
    }
}
