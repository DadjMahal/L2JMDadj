package com.aiplayer.phase0.town;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.ItemSnapshot;
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.phase0.town.VendorDatabase.VendorInfo;
import com.aiplayer.phase0.town.ItemValueEstimator.ItemFate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles selling junk items to NPC vendors.
 * Groups items by vendor type, navigates to each vendor, sells appropriate items.
 *
 * Humanization:
 * - Random delay between each item sold (200-800ms)
 * - Occasionally skips selling a low-value item ("I'll keep this")
 * - Sells in batches with small pauses
 * - Does NOT sell everything at once — humans browse
 */
public final class SellManager {

    private static final long SELL_DELAY_MIN_MS = 200;
    private static final long SELL_DELAY_MAX_MS = 800;
    private static final long BATCH_PAUSE_MS = 1500;
    private static final int BATCH_SIZE = 5;
    private static final double SKIP_LOW_VALUE_CHANCE = 0.15;

    private final String accountName;
    private final L2JProtocol protocol;
    private final ItemValueEstimator estimator;
    private final TownNavigator navigator;

    private volatile SellState state = SellState.IDLE;
    private volatile VendorInfo currentVendor = null;
    private volatile List<ItemSnapshot> pendingItems = new ArrayList<>();
    private volatile int sellIndex = 0;
    private volatile long nextActionTime = 0;

    enum SellState {
        IDLE, NAVIGATING, INTERACTING, SELLING, DONE
    }

    public SellManager(String accountName, L2JProtocol protocol,
                       ItemValueEstimator estimator, TownNavigator navigator) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.estimator = estimator;
        this.navigator = navigator;
    }

    /**
     * Start the sell process. Call when in town and decision to sell is made.
     */
    public void startSelling() {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        List<ItemSnapshot> toSell = new ArrayList<>();
        for (ItemSnapshot item : self.inventory) {
            if (estimator.evaluate(item) == ItemFate.SELL) {
                toSell.add(item);
            }
        }

        if (toSell.isEmpty()) {
            state = SellState.DONE;
            return;
        }

        // Sort by value descending — sell expensive first (human behavior)
        toSell.sort((a, b) -> Long.compare(
                (long) b.sellPrice * b.count,
                (long) a.sellPrice * a.count
        ));

        this.pendingItems = toSell;
        this.sellIndex = 0;
        this.state = SellState.NAVIGATING;
        this.currentVendor = null;
    }

    /**
     * Main tick — call every 500ms while in town sell mode.
     */
    public void tick() {
        if (state == SellState.IDLE || state == SellState.DONE) return;

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        switch (state) {
            case NAVIGATING:
                tickNavigate(self);
                break;
            case INTERACTING:
                tickInteract(self);
                break;
            case SELLING:
                tickSell(self);
                break;
            default:
                break;
        }
    }

    private void tickNavigate(BotStateSnapshot self) {
        // Group remaining items by vendor category
        Map<String, List<ItemSnapshot>> byCategory = groupByCategory(pendingItems.subList(sellIndex, pendingItems.size()));

        // Find best next vendor
        String bestCategory = null;
        int bestValue = 0;
        for (Map.Entry<String, List<ItemSnapshot>> e : byCategory.entrySet()) {
            int value = e.getValue().stream().mapToInt(i -> (int) ((long) i.sellPrice * i.count)).sum();
            if (value > bestValue) {
                bestValue = value;
                bestCategory = e.getKey();
            }
        }

        if (bestCategory == null) {
            state = SellState.DONE;
            return;
        }

        String town = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (town == null) {
            // Not in a known town — abort
            state = SellState.IDLE;
            return;
        }

        VendorInfo target = VendorDatabase.findNearestVendorBuying(town, bestCategory, self.x, self.y, self.z);
        if (target == null) {
            // No vendor for this category, skip these items
            skipCategory(bestCategory);
            return;
        }

        this.currentVendor = target;

        // Check if we're close enough
        double distSq = distSq(self.x, self.y, self.z, target.x, target.y, target.z);
        if (distSq <= target.interactRange * target.interactRange) {
            state = SellState.INTERACTING;
            nextActionTime = System.currentTimeMillis() + jitter(300, 600);
        } else {
            // Navigate toward vendor
            navigator.moveTo(target.x, target.y, target.z);
        }
    }

    private void tickInteract(BotStateSnapshot self) {
        if (currentVendor == null) {
            state = SellState.NAVIGATING;
            return;
        }

        // Send interact packet (Action packet on NPC)
        try {
            protocol.sendNpcAction(currentVendor.npcId);
        } catch (java.io.IOException e) {
            // best-effort
        }

        // Small delay before opening shop
        state = SellState.SELLING;
        nextActionTime = System.currentTimeMillis() + jitter(600, 1200);
    }

    private void tickSell(BotStateSnapshot self) {
        if (currentVendor == null || sellIndex >= pendingItems.size()) {
            state = SellState.NAVIGATING; // Check for more categories
            return;
        }

        ItemSnapshot item = pendingItems.get(sellIndex);
        String category = categorizeItem(item);

        // If vendor changed (different category), re-navigate
        if (!currentVendor.buysCategory(category)) {
            state = SellState.NAVIGATING;
            return;
        }

        // Human: occasionally skip very low value items
        if (item.sellPrice * item.count < 500 && ThreadLocalRandom.current().nextDouble() < SKIP_LOW_VALUE_CHANCE) {
            sellIndex++;
            nextActionTime = System.currentTimeMillis() + jitter(200, 500);
            return;
        }

        // Send sell packet
        try {
            protocol.sendSellItem(item.objId, item.count);
        } catch (java.io.IOException e) {
            // best-effort
        }

        sellIndex++;

        // Batch pause every N items
        if (sellIndex % BATCH_SIZE == 0) {
            nextActionTime = System.currentTimeMillis() + jitter(BATCH_PAUSE_MS, BATCH_PAUSE_MS + 800);
        } else {
            nextActionTime = System.currentTimeMillis() + jitter(SELL_DELAY_MIN_MS, SELL_DELAY_MAX_MS);
        }

        // Check if this category is done
        boolean categoryDone = true;
        for (int i = sellIndex; i < pendingItems.size(); i++) {
            if (categorizeItem(pendingItems.get(i)).equals(category)) {
                categoryDone = false;
                break;
            }
        }
        if (categoryDone) {
            state = SellState.NAVIGATING;
        }
    }

    public boolean isDone() {
        return state == SellState.DONE;
    }

    public boolean isIdle() {
        return state == SellState.IDLE;
    }

    public void reset() {
        state = SellState.IDLE;
        currentVendor = null;
        pendingItems.clear();
        sellIndex = 0;
    }

    // ------------------------------------------------------------------

    private Map<String, List<ItemSnapshot>> groupByCategory(List<ItemSnapshot> items) {
        Map<String, List<ItemSnapshot>> map = new HashMap<>();
        for (ItemSnapshot item : items) {
            String cat = categorizeItem(item);
            map.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    private void skipCategory(String category) {
        while (sellIndex < pendingItems.size()) {
            if (categorizeItem(pendingItems.get(sellIndex)).equals(category)) {
                sellIndex++;
            } else {
                break;
            }
        }
        state = SellState.NAVIGATING;
    }

    private String categorizeItem(ItemSnapshot item) {
        String name = item.name.toLowerCase();
        if (name.contains("weapon") || name.contains("sword") || name.contains("bow")
                || name.contains("dagger") || name.contains("blunt") || name.contains("polearm")) {
            return "weapon";
        }
        if (name.contains("armor") || name.contains("breastplate") || name.contains("leather")
                || name.contains("tunic")) {
            return "armor";
        }
        if (name.contains("shield")) return "shield";
        if (name.contains("material") || name.contains("ore") || name.contains("thread")) return "material";
        if (name.contains("recipe")) return "recipe";
        if (name.contains("jewelry") || name.contains("ring") || name.contains("earring")) return "jewelry";
        return "misc";
    }

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        long dz = (long) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private static long jitter(long base, long max) {
        return ThreadLocalRandom.current().nextLong(base, max + 1);
    }
/** S7-T06: pure auto-sell-overflow trigger — go sell once the bag is nearly full. */
    public static boolean autoSellOverflow(int usedSlots, int maxSlots)
    {
        return maxSlots > 0 && usedSlots >= maxSlots - 2;
    }

    /** S7-T06: is the junk valuable enough to justify the merchant trip? */
    public static boolean junkWorthSelling(int junkValue, int minValue)
    {
        return junkValue >= minValue;
    }
}
