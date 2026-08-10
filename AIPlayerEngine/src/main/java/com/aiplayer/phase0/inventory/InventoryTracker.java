package com.aiplayer.phase0.inventory;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.io.IOException;

/**
 * Receives inventory-related server packets and maintains InventorySnapshot.
 * Also triggers automated actions (soulshot enable, overweight alerts).
 * 
 * Packet hooks:
 * - InventoryUpdate (add/modify/remove items)
 * - ItemList (full inventory refresh)
 * - StatusUpdate (weight, adena)
 * - ExAutoSoulShot (soulshot auto-enable confirmation)
 */
public final class InventoryTracker {

    private static final long STALE_INVENTORY_MS = 300000; // 5 min before requesting refresh
    private static final int SOULSHOT_ENABLE_THRESHOLD = 100; // enable auto-shots when >= 100

    private final String accountName;
    private final L2JProtocol protocol;
    private final InventorySnapshot snapshot;
    private final WeightMonitor weightMonitor;

    private volatile int weaponGrade = 0; // cached from paperdoll
    private volatile boolean autoSoulshotEnabled = false;
    private volatile boolean autoSpiritshotEnabled = false;
    private volatile long lastFullRefresh = 0;

    public InventoryTracker(String accountName, L2JProtocol protocol) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.snapshot = new InventorySnapshot();
        this.weightMonitor = new WeightMonitor(accountName, snapshot);
    }

    /**
     * Call when InventoryUpdate packet arrives (add/modify).
     */
    public void onItemAdded(int objectId, int itemId, int count, int slot, boolean isEquipped) {
        snapshot.addOrUpdate(new InventorySnapshot.ItemEntry(objectId, itemId, count, slot, isEquipped));
        if (isEquipped && isWeaponSlot(slot)) {
            updateWeaponGrade(itemId);
        }
        maybeEnableSoulshots();
    }

    /**
     * Call when InventoryUpdate packet arrives (modify count).
     */
    public void onItemModified(int objectId, int newCount) {
        InventorySnapshot.ItemEntry existing = snapshot.getByObjectId(objectId);
        if (existing != null) {
            snapshot.addOrUpdate(existing.withCount(newCount));
        }
        maybeEnableSoulshots();
    }

    /**
     * Call when InventoryUpdate packet arrives (remove).
     */
    public void onItemRemoved(int objectId) {
        snapshot.remove(objectId);
    }

    /**
     * Call when ItemList packet arrives (full inventory dump).
     */
    public void onItemList(InventorySnapshot.ItemEntry[] entries, int adena, int currentWeight, int maxWeight) {
        snapshot.clear();
        for (InventorySnapshot.ItemEntry e : entries) {
            snapshot.addOrUpdate(e);
            if (e.isEquipped && isWeaponSlot(e.slot)) {
                updateWeaponGrade(e.itemId);
            }
        }
        snapshot.setAdena(adena);
        snapshot.setWeight(currentWeight, maxWeight);
        lastFullRefresh = System.currentTimeMillis();
        maybeEnableSoulshots();
    }

    /**
     * Call when StatusUpdate packet carries weight info.
     */
    public void onWeightUpdate(int currentWeight, int maxWeight) {
        snapshot.setWeight(currentWeight, maxWeight);
        weightMonitor.check();
    }

    /**
     * Call when ExAutoSoulShot packet confirms auto-shot state.
     */
    public void onAutoSoulshotState(int itemId, boolean enabled) {
        ItemDatabase.ItemInfo info = ItemDatabase.get(itemId);
        if (info.type == ItemDatabase.ItemType.SOULSHOT) {
            autoSoulshotEnabled = enabled;
        } else if (info.type == ItemDatabase.ItemType.SPIRITSHOT || info.type == ItemDatabase.ItemType.BLESSED_SPIRITSHOT) {
            autoSpiritshotEnabled = enabled;
        }
    }

    /**
     * Periodic tick — request full refresh if stale, check weight.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastFullRefresh > STALE_INVENTORY_MS) {
            // Request full inventory refresh
            try {
                protocol.sendRequestItemList();
                lastFullRefresh = now;
            } catch (IOException e) {
                // stale refresh; retried on the next tick
            }
        }
        weightMonitor.tick();
    }

    public InventorySnapshot getSnapshot() {
        return snapshot;
    }

    public WeightMonitor getWeightMonitor() {
        return weightMonitor;
    }

    public int getWeaponGrade() {
        return weaponGrade;
    }

    public boolean isAutoSoulshotEnabled() {
        return autoSoulshotEnabled;
    }

    public boolean isAutoSpiritshotEnabled() {
        return autoSpiritshotEnabled;
    }

    public int getSoulshotCount() {
        ItemDatabase.ItemInfo ss = ItemDatabase.findSoulshotForGrade(weaponGrade);
        return ss != null ? snapshot.getCount(ss.itemId) : 0;
    }

    public int getSpiritshotCount() {
        ItemDatabase.ItemInfo ss = ItemDatabase.findSpiritshotForGrade(weaponGrade, false);
        return ss != null ? snapshot.getCount(ss.itemId) : 0;
    }

    public int getBlessedSpiritshotCount() {
        ItemDatabase.ItemInfo ss = ItemDatabase.findSpiritshotForGrade(weaponGrade, true);
        return ss != null ? snapshot.getCount(ss.itemId) : 0;
    }

    public boolean hasSoulshots() {
        return getSoulshotCount() >= SOULSHOT_ENABLE_THRESHOLD;
    }

    public boolean hasSpiritshots() {
        return getSpiritshotCount() >= SOULSHOT_ENABLE_THRESHOLD;
    }

    /**
     * Force re-check and enable auto-soulshots if available.
     * Called after respawn recovery (Task 4) or on inventory refresh.
     */
    public void restockShots() {
        maybeEnableSoulshots();
    }

    // ------------------------------------------------------------------

    private void maybeEnableSoulshots() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Enable soulshots if we have enough and not already enabled
        if (!autoSoulshotEnabled && self.isPhysicalClass) {
            ItemDatabase.ItemInfo ss = ItemDatabase.findSoulshotForGrade(weaponGrade);
            if (ss != null && snapshot.getCount(ss.itemId) >= SOULSHOT_ENABLE_THRESHOLD) {
                try {
                    protocol.sendAutoSoulShot(ss.itemId, true);
                    autoSoulshotEnabled = true;
                } catch (IOException e) {
                    // best-effort; retried on next restock check
                }
            }
        }

        // Enable spiritshots if mage
        if (!autoSpiritshotEnabled && self.isMageClass) {
            ItemDatabase.ItemInfo ss = ItemDatabase.findSpiritshotForGrade(weaponGrade, false);
            if (ss != null && snapshot.getCount(ss.itemId) >= SOULSHOT_ENABLE_THRESHOLD) {
                try {
                    protocol.sendAutoSoulShot(ss.itemId, true);
                    autoSpiritshotEnabled = true;
                } catch (IOException e) {
                    // best-effort; retried on next restock check
                }
            }
        }
    }

    private void updateWeaponGrade(int weaponItemId) {
        // Phase 0: infer grade from weapon item ID ranges (simplified)
        // Phase 1: lookup weapon grade from L2JMobius item DB
        // This is a stub — real implementation needs weapon item ID -> grade mapping
        // For now, keep cached grade or infer from equipped weapon
    }

    private boolean isWeaponSlot(int slot) {
        // L2JMobius paperdoll slots: 3 = rhand, 5 = lrhand
        return slot == 3 || slot == 5;
    }
}
