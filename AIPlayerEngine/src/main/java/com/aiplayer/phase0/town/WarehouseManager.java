package com.aiplayer.phase0.town;

/** MODE: PARTIAL. Migrated to BotSnapshot/ItemSnapshot this pass. Deposit/withdraw still use a placeholder objId — see ItemSnapshot's javadoc. */

import com.aiplayer.phase0.BotSnapshot;
import com.aiplayer.protocol.PacketLogger;
import com.aiplayer.phase0.BotSnapshot;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.ItemSnapshot; // real class now, was never defined under GameStateMirror
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.phase0.town.VendorDatabase.VendorInfo;
import com.aiplayer.phase0.town.VendorDatabase.VendorType;
import com.aiplayer.phase0.town.ItemValueEstimator.ItemFate;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles warehouse deposit and withdraw operations.
 * Deposits valuable items that aren't immediately needed.
 * Withdraws items when needed (e.g., higher level equipment, materials for craft).
 *
 * Humanization:
 * - Deposits in small batches with delays
 * - Organizes by category (equipment, materials, valuables)
 * - Withdraws only when specifically needed
 */
public final class WarehouseManager {

    private static final long ACTION_DELAY_MIN_MS = 400;
    private static final long ACTION_DELAY_MAX_MS = 900;
    private static final long BATCH_PAUSE_MS = 2000;
    private static final int BATCH_SIZE = 4;

    private final String accountName;
    private final L2JProtocol protocol;
    private final ItemValueEstimator estimator;
    private final TownNavigator navigator;

    private volatile WarehouseState state = WarehouseState.IDLE;
    private volatile VendorInfo currentWarehouse = null;
    private volatile List<ItemSnapshot> pendingItems = new ArrayList<>();
    private volatile int actionIndex = 0;
    private volatile long nextActionTime = 0;
    private volatile boolean depositMode = true; // true = deposit, false = withdraw

    enum WarehouseState {
        IDLE, NAVIGATING, INTERACTING, DEPOSITING, WITHDRAWING, DONE
    }

    private final PacketLogger packetLogger;

    public WarehouseManager(String accountName, L2JProtocol protocol, PacketLogger packetLogger,
                            ItemValueEstimator estimator, TownNavigator navigator) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.packetLogger = packetLogger;
        this.estimator = estimator;
        this.navigator = navigator;
    }

    /**
     * Start depositing valuable items to warehouse.
     */
    public void startDeposit() {
        BotSnapshot self = BotSnapshot.from(accountName, packetLogger);
        if (self == null) {
            state = WarehouseState.DONE;
            return;
        }

        List<ItemSnapshot> toDeposit = new ArrayList<>();
        for (ItemSnapshot item : self.getInventory(packetLogger)) {
            if (estimator.evaluate(item) == ItemFate.WAREHOUSE) {
                toDeposit.add(item);
            }
        }

        if (toDeposit.isEmpty()) {
            state = WarehouseState.DONE;
            return;
        }

        this.pendingItems = toDeposit;
        this.actionIndex = 0;
        this.depositMode = true;
        this.state = WarehouseState.NAVIGATING;
        this.currentWarehouse = null;
    }

    /**
     * Start withdrawing specific items (called when crafting or equipping).
     * Phase 0: stub — Phase 1 queries warehouse contents.
     */
    public void startWithdraw(List<Integer> itemIds) {
        // Phase 0: No warehouse content tracking yet
        // Phase 1: Query in-memory warehouse snapshot (no Redis), build withdraw list
        this.pendingItems = new ArrayList<>();
        this.actionIndex = 0;
        this.depositMode = false;
        this.state = WarehouseState.DONE; // Nothing to withdraw in Phase 0
    }

    /**
     * Main tick — call every 500ms while in warehouse mode.
     */
    public void tick() {
        if (state == WarehouseState.IDLE || state == WarehouseState.DONE) return;

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        BotSnapshot self = BotSnapshot.from(accountName, packetLogger);
        if (self == null) return;

        switch (state) {
            case NAVIGATING:
                tickNavigate(self);
                break;
            case INTERACTING:
                tickInteract(self);
                break;
            case DEPOSITING:
                tickDeposit(self);
                break;
            case WITHDRAWING:
                tickWithdraw(self);
                break;
            default:
                break;
        }
    }

    private void tickNavigate(BotSnapshot self) {
        String town = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (town == null) {
            state = WarehouseState.IDLE;
            return;
        }

        VendorInfo target = VendorDatabase.findNearestVendor(town, VendorType.WAREHOUSE_KEEPER, self.x, self.y, self.z);
        if (target == null) {
            state = WarehouseState.IDLE;
            return;
        }

        this.currentWarehouse = target;

        double distSq = distSq(self.x, self.y, self.z, target.x, target.y, target.z);
        if (distSq <= target.interactRange * target.interactRange) {
            state = WarehouseState.INTERACTING;
            nextActionTime = System.currentTimeMillis() + jitter(500, 1000);
        } else {
            navigator.moveTo(target.x, target.y, target.z);
        }
    }

    private void tickInteract(BotSnapshot self) {
        if (currentWarehouse == null) {
            state = WarehouseState.NAVIGATING;
            return;
        }
        try {
            protocol.sendNpcAction(currentWarehouse.npcId);
        } catch (java.io.IOException e) {
            // best-effort
        }

        if (depositMode) {
            state = WarehouseState.DEPOSITING;
        } else {
            state = WarehouseState.WITHDRAWING;
        }
        nextActionTime = System.currentTimeMillis() + jitter(1000, 1800);
    }

    private void tickDeposit(BotSnapshot self) {
        if (currentWarehouse == null || actionIndex >= pendingItems.size()) {
            state = WarehouseState.DONE;
            return;
        }

        ItemSnapshot item = pendingItems.get(actionIndex);

        // Send deposit packet
        try {
            protocol.sendDepositItem(item.objId, item.count);
        } catch (java.io.IOException e) {
            // best-effort
        }

        actionIndex++;

        if (actionIndex % BATCH_SIZE == 0) {
            nextActionTime = System.currentTimeMillis() + jitter(BATCH_PAUSE_MS, BATCH_PAUSE_MS + 1000);
        } else {
            nextActionTime = System.currentTimeMillis() + jitter(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
        }
    }

    private void tickWithdraw(BotSnapshot self) {
        if (currentWarehouse == null || actionIndex >= pendingItems.size()) {
            state = WarehouseState.DONE;
            return;
        }

        ItemSnapshot item = pendingItems.get(actionIndex);
        try {
            protocol.sendWithdrawItem(item.objId, item.count);
        } catch (java.io.IOException e) {
            // best-effort
        }

        actionIndex++;
        nextActionTime = System.currentTimeMillis() + jitter(ACTION_DELAY_MIN_MS, ACTION_DELAY_MAX_MS);
    }

    public boolean isDone() {
        return state == WarehouseState.DONE;
    }

    public boolean isIdle() {
        return state == WarehouseState.IDLE;
    }

    public void reset() {
        state = WarehouseState.IDLE;
        currentWarehouse = null;
        pendingItems.clear();
        actionIndex = 0;
    }

    // ------------------------------------------------------------------

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        long dz = (long) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private static long jitter(long base, long max) {
        return ThreadLocalRandom.current().nextLong(base, max + 1);
    }
}
