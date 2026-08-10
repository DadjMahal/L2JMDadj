package com.aiplayer.phase0.inventory;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Thread-safe snapshot of an AI Player's inventory state.
 * Updated by InventoryTracker as InventoryUpdate packets arrive.
 */
public final class InventorySnapshot {

    public static final class ItemEntry {
        public final int objectId;
        public final int itemId;
        public int count;
        public final int slot; // inventory slot / paperdoll slot
        public final boolean isEquipped;

        public ItemEntry(int objectId, int itemId, int count, int slot, boolean isEquipped) {
            this.objectId = objectId;
            this.itemId = itemId;
            this.count = count;
            this.slot = slot;
            this.isEquipped = isEquipped;
        }

        public ItemEntry withCount(int newCount) {
            return new ItemEntry(objectId, itemId, newCount, slot, isEquipped);
        }
    }

    // objectId -> ItemEntry for quick lookup by server object ID
    private final Map<Integer, ItemEntry> byObjectId = new ConcurrentHashMap<>();
    // itemId -> total count (aggregated across stacks)
    private final Map<Integer, Integer> countByItemId = new ConcurrentHashMap<>();

    private volatile int totalWeight = 0;
    private volatile int maxWeight = 1; // avoid div/0
    private volatile int adena = 0;
    private volatile long lastUpdate = 0;

    public void addOrUpdate(ItemEntry entry) {
        byObjectId.put(entry.objectId, entry);
        recalcCounts();
        lastUpdate = System.currentTimeMillis();
    }

    public void remove(int objectId) {
        ItemEntry removed = byObjectId.remove(objectId);
        if (removed != null) {
            recalcCounts();
            lastUpdate = System.currentTimeMillis();
        }
    }

    public void clear() {
        byObjectId.clear();
        countByItemId.clear();
        totalWeight = 0;
        adena = 0;
        lastUpdate = System.currentTimeMillis();
    }

    public ItemEntry getByObjectId(int objectId) {
        return byObjectId.get(objectId);
    }

    public int getCount(int itemId) {
        return countByItemId.getOrDefault(itemId, 0);
    }

    public boolean hasItem(int itemId) {
        return getCount(itemId) > 0;
    }

    public boolean hasItem(int itemId, int minimumCount) {
        return getCount(itemId) >= minimumCount;
    }

    public Map<Integer, ItemEntry> getAllByObjectId() {
        return new ConcurrentHashMap<>(byObjectId);
    }

    public Map<Integer, Integer> getAllCounts() {
        return new ConcurrentHashMap<>(countByItemId);
    }

    public void setWeight(int current, int max) {
        this.totalWeight = current;
        this.maxWeight = Math.max(max, 1);
        lastUpdate = System.currentTimeMillis();
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    public int getMaxWeight() {
        return maxWeight;
    }

    public double getWeightPercent() {
        return (totalWeight * 100.0) / maxWeight;
    }

    public boolean isOverweight() {
        return getWeightPercent() >= 80.0;
    }

    public boolean isCriticalOverweight() {
        return getWeightPercent() >= 95.0;
    }

    public void setAdena(int amount) {
        this.adena = amount;
        lastUpdate = System.currentTimeMillis();
    }

    public int getAdena() {
        return adena;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public int getUniqueItemCount() {
        return byObjectId.size();
    }

    private void recalcCounts() {
        countByItemId.clear();
        for (ItemEntry e : byObjectId.values()) {
            countByItemId.merge(e.itemId, e.count, Integer::sum);
        }
    }

    @Override
    public String toString() {
        return String.format("Inventory[%d items, %d adena, %.1f%% weight]",
                byObjectId.size(), adena, getWeightPercent());
    }
}
