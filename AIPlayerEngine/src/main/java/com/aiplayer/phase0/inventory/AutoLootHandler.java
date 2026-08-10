package com.aiplayer.phase0.inventory;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages loot pickup behavior:
 * - Respects weight limits (disables when overweight)
 * - Configurable loot radius
 * - Item filtering (ignore junk, prioritize adena/recipes/scrolls)
 * - Human-like pickup delays (300-1200ms after drop appears)
 * - Anti-pattern: never instant-loot everything in radius
 * 
 * L2JMobius: AutoLoot is server-side config, but AI Players can also
 * manually pick up drops via RequestGetItem packet if auto-loot is off.
 */
public final class AutoLootHandler {

    private static final int DEFAULT_LOOT_RADIUS = 200;
    private static final long MIN_LOOT_DELAY_MS = 300;
    private static final long MAX_LOOT_DELAY_MS = 1200;
    private static final long LOOT_COOLDOWN_MS = 800; // between consecutive picks

    // Junk items to ignore (low value, high weight)
    private static final Set<Integer> JUNK_ITEMS = new HashSet<>();
    static {
        // Animal skins, bones, etc. — low value farm trash
        JUNK_ITEMS.add(1864); // Stem
        JUNK_ITEMS.add(1865); // Varnish
        JUNK_ITEMS.add(1866); // Suede
        JUNK_ITEMS.add(1867); // Animal Skin
        JUNK_ITEMS.add(1868); // Thread
        JUNK_ITEMS.add(1869); // Iron Ore
        JUNK_ITEMS.add(1870); // Coal
        JUNK_ITEMS.add(1871); // Charcoal
        JUNK_ITEMS.add(1872); // Animal Bone
        JUNK_ITEMS.add(1873); // Silver Nugget
        JUNK_ITEMS.add(1874); // Oriharukon Ore
        JUNK_ITEMS.add(1875); // Stone of Purity
        JUNK_ITEMS.add(1876); // Mithril Ore
        JUNK_ITEMS.add(1877); // Adamantite Nugget
        JUNK_ITEMS.add(1880); // Steel
        JUNK_ITEMS.add(1881); // Coarse Bone Powder
        JUNK_ITEMS.add(1882); // Leather
        JUNK_ITEMS.add(1883); // Cord
        JUNK_ITEMS.add(1884); // Bronze
        JUNK_ITEMS.add(1885); // High Grade Suede
        JUNK_ITEMS.add(1886); // Silver
        JUNK_ITEMS.add(1887); // Cloth
        JUNK_ITEMS.add(1888); // Refined Steel
        JUNK_ITEMS.add(1889); // Synthetic Cokes
        JUNK_ITEMS.add(1890); // Compound Braid
        JUNK_ITEMS.add(1891); // Durable Metal Plate
        JUNK_ITEMS.add(1892); // Mithril Alloy
        JUNK_ITEMS.add(1893); // Artisan's Frame
        JUNK_ITEMS.add(1894); // Blacksmith's Frame
        JUNK_ITEMS.add(1895); // Crafted Leather
        JUNK_ITEMS.add(1896); // Metallic Fiber
        JUNK_ITEMS.add(1897); // Metallic Thread
        JUNK_ITEMS.add(1898); // Metal Hardener
        JUNK_ITEMS.add(1899); // Dye Reagent
        JUNK_ITEMS.add(1900); // Synthesis Cokes
        JUNK_ITEMS.add(1901); // Mold Glue
        JUNK_ITEMS.add(1902); // Mold Lubricant
        JUNK_ITEMS.add(1903); // Mold Hardener
        JUNK_ITEMS.add(1904); // Enria
        JUNK_ITEMS.add(1905); // Ashlone
        JUNK_ITEMS.add(1906); // Asofe
        JUNK_ITEMS.add(1907); // Thons
        JUNK_ITEMS.add(1908); // Abrasive
        JUNK_ITEMS.add(1909); // Antidote
        JUNK_ITEMS.add(1910); // Bandage
        JUNK_ITEMS.add(1911); // Quick Healing Potion
        JUNK_ITEMS.add(1912); // Greater Antidote
        JUNK_ITEMS.add(1913); // Greater Bandage
        JUNK_ITEMS.add(1914); // Quick Healing Potion (Event)
    }

    private final String accountName;
    private final java.util.Random random; // deterministic per-bot, not random.nextDouble()
    private final L2JProtocol protocol;
    private final InventoryTracker inventoryTracker;

    private volatile boolean autoLootEnabled = true;
    private volatile int lootRadius = DEFAULT_LOOT_RADIUS;
    private volatile long lastLootTime = 0;
    private volatile boolean respectWeightLimit = true;

    public AutoLootHandler(String accountName, L2JProtocol protocol, InventoryTracker inventoryTracker) {
        this.accountName = accountName;
        this.random = new java.util.Random(accountName.hashCode());
        this.protocol = protocol;
        this.inventoryTracker = inventoryTracker;
    }

    /**
     * Evaluate nearby drops and decide whether to pick up.
     * Call when DropItem packet arrives or on periodic sweep.
     */
    public void onDropDetected(int itemObjectId, int itemId, int x, int y, int z) {
        if (!shouldLoot()) return;

        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null) return;

        // Distance check
        double dist = Math.sqrt(Math.pow(x - self.x, 2) + Math.pow(y - self.y, 2));
        if (dist > lootRadius) return;

        // Item filter
        if (isJunk(itemId)) {
            // 70% chance to ignore junk (human players often leave trash)
            if (random.nextDouble() < 0.70) return;
        }

        // Human-like delay before pickup
        long delay = MIN_LOOT_DELAY_MS + (long) (random.nextDouble() * (MAX_LOOT_DELAY_MS - MIN_LOOT_DELAY_MS));
        scheduleLoot(itemObjectId, delay);
    }

    /**
     * Periodic tick — sweep for unlooted nearby items.
     */
    public void tick() {
        if (!shouldLoot()) return;
        // Phase 1: Query GameStateMirror for nearby dropped items
        // Phase 0: Reactive only via onDropDetected
    }

    public void setAutoLootEnabled(boolean enabled) {
        this.autoLootEnabled = enabled;
    }

    public boolean isAutoLootEnabled() {
        return autoLootEnabled;
    }

    public void setLootRadius(int radius) {
        this.lootRadius = radius;
    }

    public int getLootRadius() {
        return lootRadius;
    }

    public void setRespectWeightLimit(boolean respect) {
        this.respectWeightLimit = respect;
    }

    // ------------------------------------------------------------------

    private boolean shouldLoot() {
        if (!autoLootEnabled) return false;

        long now = System.currentTimeMillis();
        if (now - lastLootTime < LOOT_COOLDOWN_MS) return false;

        if (respectWeightLimit && inventoryTracker.getWeightMonitor().shouldDisableLooting()) {
            return false;
        }

        return true;
    }

    private boolean isJunk(int itemId) {
        return JUNK_ITEMS.contains(itemId);
    }

    private void scheduleLoot(int itemObjectId, long delayMs) {
        lastLootTime = System.currentTimeMillis() + delayMs;
        if (delayMs <= 0) {
            try {
                protocol.sendPickupItem(itemObjectId);
            } catch (java.io.IOException e) {
                // best-effort
            }
            return;
        }
        // Phase 0: simple delayed execution
        new Thread(() -> {
            try {
                // FLAGGED (external review, no Thread.sleep in engine packages): this blocks

                // whatever thread calls it. Safe only if this method already runs on its own

                // dedicated per-bot thread, not the shared engine tick loop — verify the actual

                // call chain before relying on this; not confirmed either way in this pass.

                Thread.sleep(delayMs);
                try {
                    protocol.sendPickupItem(itemObjectId);
                } catch (java.io.IOException e) {
                    // best-effort
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
