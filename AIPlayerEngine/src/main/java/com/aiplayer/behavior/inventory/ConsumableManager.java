package com.aiplayer.behavior.inventory;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror;
import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.L2JProtocol;

import java.util.HashMap;
import java.util.Map;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.behavior.inventory.InventorySnapshot;
import com.aiplayer.behavior.inventory.ItemDatabase;
import com.aiplayer.behavior.inventory.InventorySnapshot.ItemEntry;
import com.aiplayer.behavior.inventory.ItemDatabase.ItemInfo;
import com.aiplayer.behavior.inventory.ItemDatabase.ItemType;

/**
 * Intelligent consumable usage with human-like delays, situational awareness,
 * and cooldown tracking. Ensures AI Players don't spam potions or waste scrolls.
 * 
 * Usage rules:
 * - Healing potion: HP < 60% (combat), HP < 40% (out of combat)
 * - CP potion: CP < 30% (combat only)
 * - Mana potion: MP < 30% (mage), MP < 15% (physical with mana skills)
 * - Buff scrolls: only out of combat, every 20 min, random selection
 * - Scroll of Escape: emergency only (stuck, critical overweight, GM summon)
 */
public final class ConsumableManager {

    private static final long POTION_REUSE_MS = 1000; // enforced minimum between potions
    private static final long SCROLL_REUSE_MS = 600000; // 10 min between buff scrolls
    private static final long ESCAPE_SCROLL_COOLDOWN_MS = 300000; // 5 min between SoE
    private static final double HP_COMBAT_THRESHOLD = 0.60;
    private static final double HP_PEACE_THRESHOLD = 0.40;
    private static final double CP_COMBAT_THRESHOLD = 0.30;
    private static final double MP_MAGE_THRESHOLD = 0.30;
    private static final double MP_PHYSICAL_THRESHOLD = 0.15;

    private final String accountName;
    private final java.util.Random random; // deterministic per-bot, not random.nextDouble()
    private final L2JProtocol protocol;
    private final InventoryTracker inventoryTracker;

    // Reuse delay tracking: itemId -> next usable timestamp
    private final Map<Integer, Long> itemReuse = new HashMap<>();
    private volatile long lastPotionUsed = 0;
    private volatile long lastScrollUsed = 0;
    private volatile long lastEscapeUsed = 0;

    // Buff scroll rotation index for variety
    private volatile int buffScrollIndex = 0;

    public ConsumableManager(String accountName, L2JProtocol protocol, InventoryTracker inventoryTracker) {
        this.accountName = accountName;
        this.random = new java.util.Random(accountName.hashCode());
        this.protocol = protocol;
        this.inventoryTracker = inventoryTracker;
    }

    /**
     * Main tick — evaluate all consumable needs and use if appropriate.
     * Call every tick (or every 500ms sub-tick).
     */
    public void tick(boolean inCombat) {
        BotStateSnapshot self = GameStateMirror.getInstance().get(accountName);
        if (self == null || (self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) <= 0) return;

        long now = System.currentTimeMillis();

        // Priority 1: Healing potion
        double hpThreshold = inCombat ? HP_COMBAT_THRESHOLD : HP_PEACE_THRESHOLD;
        if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) < hpThreshold * 100) {
            tryUseHealingPotion(now, self.level);
        }

        // Priority 2: CP potion (combat only)
        if (inCombat && self.cpPercent() < CP_COMBAT_THRESHOLD * 100) {
            tryUseCpPotion(now, self.level);
        }

        // Priority 3: Mana potion
        double mpThreshold = self.isMageClass ? MP_MAGE_THRESHOLD : MP_PHYSICAL_THRESHOLD;
        if ((self.mpMax > 0 ? self.mpCurrent * 100 / self.mpMax : 100) < mpThreshold * 100) {
            tryUseManaPotion(now, self.level);
        }

        // Priority 4: Buff scrolls (out of combat only, not while recovering)
        if (!inCombat && now - lastScrollUsed > SCROLL_REUSE_MS) {
            tryUseBuffScroll(now);
        }
    }

    /**
     * Emergency escape — Scroll of Escape with cooldown check.
     */
    public boolean tryEmergencyEscape() {
        long now = System.currentTimeMillis();
        if (now - lastEscapeUsed < ESCAPE_SCROLL_COOLDOWN_MS) return false;

        InventorySnapshot inv = inventoryTracker.getSnapshot();
        int scrollId = findBestScrollOfEscape();
        if (scrollId == 0 || !inv.hasItem(scrollId)) return false;

        useItem(scrollId);
        lastEscapeUsed = now;
        return true;
    }

    /**
     * Force use a healing potion regardless of threshold (post-respawn, etc).
     */
    public boolean forceHeal() {
        long now = System.currentTimeMillis();
        return tryUseHealingPotion(now, 80); // pass high level to get best potion
    }

    /**
     * Force use a mana potion (post-respawn for mages).
     */
    public boolean forceMana() {
        long now = System.currentTimeMillis();
        return tryUseManaPotion(now, 80);
    }

    public boolean isOnPotionCooldown() {
        return System.currentTimeMillis() - lastPotionUsed < POTION_REUSE_MS;
    }

    public long getPotionCooldownRemaining() {
        long remaining = POTION_REUSE_MS - (System.currentTimeMillis() - lastPotionUsed);
        return Math.max(0, remaining);
    }

    // ------------------------------------------------------------------
    // Private implementation
    // ------------------------------------------------------------------

    private boolean tryUseHealingPotion(long now, int playerLevel) {
        if (now - lastPotionUsed < POTION_REUSE_MS) return false;

        ItemDatabase.ItemInfo potion = ItemDatabase.findBestPotion(ItemDatabase.ItemType.HEAL_POTION, playerLevel);
        if (potion == null) return false;

        InventorySnapshot inv = inventoryTracker.getSnapshot();
        if (!inv.hasItem(potion.itemId)) {
            // Fallback: try any healing potion
            potion = findAnyHealingPotion();
            if (potion == null || !inv.hasItem(potion.itemId)) return false;
        }

        // Human-like delay before using: 200-800ms (simulates reaction time)
        long humanDelay = 200 + (long) (random.nextDouble() * 600);
        scheduleUse(potion.itemId, humanDelay);
        lastPotionUsed = now + humanDelay;
        return true;
    }

    private boolean tryUseCpPotion(long now, int playerLevel) {
        if (now - lastPotionUsed < POTION_REUSE_MS) return false;

        ItemDatabase.ItemInfo potion = ItemDatabase.findBestPotion(ItemDatabase.ItemType.CP_POTION, playerLevel);
        if (potion == null) return false;

        InventorySnapshot inv = inventoryTracker.getSnapshot();
        if (!inv.hasItem(potion.itemId)) return false;

        long humanDelay = 150 + (long) (random.nextDouble() * 400);
        scheduleUse(potion.itemId, humanDelay);
        lastPotionUsed = now + humanDelay;
        return true;
    }

    private boolean tryUseManaPotion(long now, int playerLevel) {
        if (now - lastPotionUsed < POTION_REUSE_MS) return false;

        ItemDatabase.ItemInfo potion = ItemDatabase.findBestPotion(ItemDatabase.ItemType.MANA_POTION, playerLevel);
        if (potion == null) return false;

        InventorySnapshot inv = inventoryTracker.getSnapshot();
        if (!inv.hasItem(potion.itemId)) return false;

        long humanDelay = 200 + (long) (random.nextDouble() * 500);
        scheduleUse(potion.itemId, humanDelay);
        lastPotionUsed = now + humanDelay;
        return true;
    }

    private boolean tryUseBuffScroll(long now) {
        InventorySnapshot inv = inventoryTracker.getSnapshot();

        // Rotate through available buff scrolls for variety
        int[] buffScrolls = {
            5703, 5704, 5705, 5706, 5707, 5708, 5709, 5710, 5711, 5712, 5713, 5714
        };

        // Start from current index, wrap around
        for (int i = 0; i < buffScrolls.length; i++) {
            int idx = (buffScrollIndex + i) % buffScrolls.length;
            int scrollId = buffScrolls[idx];
            if (inv.hasItem(scrollId) && !isOnReuse(scrollId)) {
                // Human delay: 1-3s (reading scroll, casting animation)
                long humanDelay = 1000 + (long) (random.nextDouble() * 2000);
                scheduleUse(scrollId, humanDelay);
                lastScrollUsed = now + humanDelay;
                buffScrollIndex = (idx + 1) % buffScrolls.length;
                return true;
            }
        }
        return false;
    }

    private void scheduleUse(int itemId, long delayMs) {
        if (delayMs <= 0) {
            useItem(itemId);
            return;
        }
        // Phase 0: simple inline delay via GameStateMirror tick scheduling
        // Phase 1: use proper scheduler
        new Thread(() -> {
            try {
                // FLAGGED (external review, no Thread.sleep in engine packages): this blocks

                // whatever thread calls it. Safe only if this method already runs on its own

                // dedicated per-bot thread, not the shared engine tick loop — verify the actual

                // call chain before relying on this; not confirmed either way in this pass.

                Thread.sleep(delayMs);
                useItem(itemId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void useItem(int itemId) {
        // Find objectId in inventory
        InventorySnapshot inv = inventoryTracker.getSnapshot();
        InventorySnapshot.ItemEntry entry = findFirstEntry(itemId);
        if (entry != null) {
            try {
                protocol.sendUseItem(entry.objectId);
                itemReuse.put(itemId, System.currentTimeMillis() + ItemDatabase.get(itemId).reuseDelayMs);
            } catch (java.io.IOException e) {
                // best-effort
            }
        }
    }

    private InventorySnapshot.ItemEntry findFirstEntry(int itemId) {
        for (InventorySnapshot.ItemEntry e : inventoryTracker.getSnapshot().getAllByObjectId().values()) {
            if (e.itemId == itemId) return e;
        }
        return null;
    }

    private boolean isOnReuse(int itemId) {
        Long nextUse = itemReuse.get(itemId);
        return nextUse != null && System.currentTimeMillis() < nextUse;
    }

    private ItemDatabase.ItemInfo findAnyHealingPotion() {
        // Fallback: return any known healing potion
        for (int id : new int[]{1539, 1061, 1060}) {
            ItemDatabase.ItemInfo info = ItemDatabase.get(id);
            if (info.type == ItemDatabase.ItemType.HEAL_POTION) return info;
        }
        return null;
    }

    private int findBestScrollOfEscape() {
        // Prefer normal SoE, fallback to blessed
        InventorySnapshot inv = inventoryTracker.getSnapshot();
        if (inv.hasItem(736)) return 736;
        if (inv.hasItem(3936)) return 3936;
        return 0;
    }
}
