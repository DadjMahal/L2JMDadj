package com.aiplayer.phase0.town;

/** MODE: PARTIAL. Migrated to BotSnapshot/ItemSnapshot this pass (was GameStateMirror). Not independently re-verified beyond the migration itself. */

import com.aiplayer.phase0.ItemSnapshot;
import com.aiplayer.phase0.BotSnapshot;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.protocol.PacketLogger; // real class now, was never defined under GameStateMirror
import com.aiplayer.protocol.L2JProtocol;
import com.aiplayer.phase0.town.VendorDatabase.VendorInfo;
import com.aiplayer.phase0.town.VendorDatabase.VendorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles buying consumables from NPC vendors.
 * Tracks desired stock levels, finds appropriate vendors, purchases with human-like delays.
 *
 * Humanization:
 * - Buys in small batches (not max stack at once)
 * - Occasionally buys 1 extra "just in case"
 * - Waits between purchases
 * - Checks adena before buying
 */
public final class BuyManager {

    private static final long BUY_DELAY_MIN_MS = 250;
    private static final long BUY_DELAY_MAX_MS = 700;
    private static final long BATCH_PAUSE_MS = 1200;
    private static final int BATCH_SIZE = 3;

    // Target stock levels
    private static final int TARGET_SOULSHOTS = 3000;
    private static final int TARGET_SPIRITSHOTS = 3000;
    private static final int TARGET_ARROWS = 2000;
    private static final int TARGET_HEALING_POTIONS = 50;
    private static final int TARGET_MANA_POTIONS = 30;
    private static final int TARGET_SCROLL_ESCAPE = 5;

    private final String accountName;
    private final L2JProtocol protocol;
    private final TownNavigator navigator;
    private final String playerClass;

    private volatile BuyState state = BuyState.IDLE;
    private volatile VendorInfo currentVendor = null;
    private volatile List<BuyOrder> pendingOrders = new ArrayList<>();
    private volatile int orderIndex = 0;
    private volatile long nextActionTime = 0;

    enum BuyState {
        IDLE, NAVIGATING, INTERACTING, BUYING, DONE
    }

    private static final class BuyOrder {
        final int itemId;
        final String itemName;
        final int quantity;
        final int unitPrice;
        final String category;

        BuyOrder(int itemId, String itemName, int quantity, int unitPrice, String category) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.category = category;
        }

        int totalCost() {
            return quantity * unitPrice;
        }
    }

    private final PacketLogger packetLogger;

    public BuyManager(String accountName, L2JProtocol protocol, PacketLogger packetLogger,
                      TownNavigator navigator, String playerClass) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.packetLogger = packetLogger;
        this.navigator = navigator;
        this.playerClass = playerClass.toLowerCase();
    }

    /**
     * Evaluate inventory and generate buy list.
     */
    public void startShopping() {
        BotSnapshot self = BotSnapshot.from(accountName, packetLogger);
        if (self == null) {
            state = BuyState.DONE;
            return;
        }

        List<BuyOrder> orders = generateOrders(self);
        if (orders.isEmpty()) {
            state = BuyState.DONE;
            return;
        }

        // Sort by vendor proximity — group by category to minimize walking
        String town = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (town != null) {
            orders.sort((a, b) -> {
                VendorInfo va = VendorDatabase.findNearestVendorSelling(town, a.category, self.x, self.y, self.z);
                VendorInfo vb = VendorDatabase.findNearestVendorSelling(town, b.category, self.x, self.y, self.z);
                double da = va == null ? Double.MAX_VALUE : distSq(self.x, self.y, self.z, va.x, va.y, va.z);
                double db = vb == null ? Double.MAX_VALUE : distSq(self.x, self.y, self.z, vb.x, vb.y, vb.z);
                return Double.compare(da, db);
            });
        }

        this.pendingOrders = orders;
        this.orderIndex = 0;
        this.state = BuyState.NAVIGATING;
        this.currentVendor = null;
    }

    /**
     * Main tick — call every 500ms while in town buy mode.
     */
    public void tick() {
        if (state == BuyState.IDLE || state == BuyState.DONE) return;

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
            case BUYING:
                tickBuy(self);
                break;
            default:
                break;
        }
    }

    private void tickNavigate(BotSnapshot self) {
        if (orderIndex >= pendingOrders.size()) {
            state = BuyState.DONE;
            return;
        }

        BuyOrder order = pendingOrders.get(orderIndex);
        String town = VendorDatabase.detectTown(self.x, self.y, self.z);
        if (town == null) {
            state = BuyState.IDLE;
            return;
        }

        VendorInfo target = VendorDatabase.findNearestVendorSelling(town, order.category, self.x, self.y, self.z);
        if (target == null) {
            // Skip this order
            orderIndex++;
            return;
        }

        this.currentVendor = target;

        double distSq = distSq(self.x, self.y, self.z, target.x, target.y, target.z);
        if (distSq <= target.interactRange * target.interactRange) {
            state = BuyState.INTERACTING;
            nextActionTime = System.currentTimeMillis() + jitter(400, 800);
        } else {
            navigator.moveTo(target.x, target.y, target.z);
        }
    }

    private void tickInteract(BotSnapshot self) {
        if (currentVendor == null) {
            state = BuyState.NAVIGATING;
            return;
        }
        try {
            protocol.sendNpcAction(currentVendor.npcId);
        } catch (java.io.IOException e) {
            // best-effort
        }
        state = BuyState.BUYING;
        nextActionTime = System.currentTimeMillis() + jitter(800, 1500);
    }

    private void tickBuy(BotSnapshot self) {
        if (currentVendor == null || orderIndex >= pendingOrders.size()) {
            state = BuyState.NAVIGATING;
            return;
        }

        BuyOrder order = pendingOrders.get(orderIndex);

        // Verify we're still at the right vendor category
        if (!currentVendor.sellsCategory(order.category)) {
            state = BuyState.NAVIGATING;
            return;
        }

        // Check adena
        if (self.adena < order.totalCost()) {
            // Skip — not enough adena
            orderIndex++;
            state = BuyState.NAVIGATING;
            return;
        }

        // Human: buy in smaller batches, not full quantity at once
        int buyQty = Math.min(order.quantity, batchSizeForItem(order.itemName));

        // Occasionally buy 1 extra
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            buyQty++;
        }

        try {
            protocol.sendBuyItem(order.itemId, buyQty);
        } catch (java.io.IOException e) {
            // best-effort
        }

        order = new BuyOrder(order.itemId, order.itemName, order.quantity - buyQty, order.unitPrice, order.category);
        pendingOrders.set(orderIndex, order);

        if (order.quantity <= 0) {
            orderIndex++;
        }

        // Delay
        if ((orderIndex + 1) % BATCH_SIZE == 0) {
            nextActionTime = System.currentTimeMillis() + jitter(BATCH_PAUSE_MS, BATCH_PAUSE_MS + 600);
        } else {
            nextActionTime = System.currentTimeMillis() + jitter(BUY_DELAY_MIN_MS, BUY_DELAY_MAX_MS);
        }

        // Check if category changed for next order
        if (orderIndex < pendingOrders.size()) {
            String nextCat = pendingOrders.get(orderIndex).category;
            if (!currentVendor.sellsCategory(nextCat)) {
                state = BuyState.NAVIGATING;
            }
        }
    }

    public boolean isDone() {
        return state == BuyState.DONE;
    }

    public boolean isIdle() {
        return state == BuyState.IDLE;
    }

    public void reset() {
        state = BuyState.IDLE;
        currentVendor = null;
        pendingOrders.clear();
        orderIndex = 0;
    }

    // ------------------------------------------------------------------

    private List<BuyOrder> generateOrders(BotSnapshot self) {
        List<BuyOrder> orders = new ArrayList<>();
        Map<String, Integer> counts = countConsumables(self.getInventory(packetLogger));
        int adena = self.adena;

        // Soulshots / Spiritshots
        if (usesSoulshots()) {
            int have = counts.getOrDefault("soulshot", 0);
            if (have < TARGET_SOULSHOTS) {
                int need = TARGET_SOULSHOTS - have;
                int itemId = getSoulshotIdForLevel(self.level);
                int price = getEstimatedPrice(itemId);
                if (adena >= need * price) {
                    orders.add(new BuyOrder(itemId, "Soulshot", need, price, "shot"));
                    adena -= need * price;
                }
            }
        } else if (usesSpiritshots()) {
            int have = counts.getOrDefault("spiritshot", 0);
            if (have < TARGET_SPIRITSHOTS) {
                int need = TARGET_SPIRITSHOTS - have;
                int itemId = getSpiritshotIdForLevel(self.level);
                int price = getEstimatedPrice(itemId);
                if (adena >= need * price) {
                    orders.add(new BuyOrder(itemId, "Spiritshot", need, price, "shot"));
                    adena -= need * price;
                }
            }
        }

        // Arrows
        if (usesArrows()) {
            int have = counts.getOrDefault("arrow", 0);
            if (have < TARGET_ARROWS) {
                int need = TARGET_ARROWS - have;
                int itemId = getArrowIdForLevel(self.level);
                int price = getEstimatedPrice(itemId);
                if (adena >= need * price) {
                    orders.add(new BuyOrder(itemId, "Arrow", need, price, "arrow"));
                    adena -= need * price;
                }
            }
        }

        // Healing potions
        int healPotions = counts.getOrDefault("healing_potion", 0);
        if (healPotions < TARGET_HEALING_POTIONS) {
            int need = TARGET_HEALING_POTIONS - healPotions;
            int itemId = getHealingPotionIdForLevel(self.level);
            int price = getEstimatedPrice(itemId);
            if (adena >= need * price) {
                orders.add(new BuyOrder(itemId, "Healing Potion", need, price, "potion"));
                adena -= need * price;
            }
        }

        // Mana potions (mages)
        if (usesSpiritshots()) {
            int manaPotions = counts.getOrDefault("mana_potion", 0);
            if (manaPotions < TARGET_MANA_POTIONS) {
                int need = TARGET_MANA_POTIONS - manaPotions;
                int itemId = getManaPotionIdForLevel(self.level);
                int price = getEstimatedPrice(itemId);
                if (adena >= need * price) {
                    orders.add(new BuyOrder(itemId, "Mana Potion", need, price, "potion"));
                    adena -= need * price;
                }
            }
        }

        // Scrolls of Escape
        int soe = counts.getOrDefault("scroll_escape", 0);
        if (soe < TARGET_SCROLL_ESCAPE) {
            int need = TARGET_SCROLL_ESCAPE - soe;
            int itemId = 736; // Scroll of Escape
            int price = getEstimatedPrice(itemId);
            if (adena >= need * price) {
                orders.add(new BuyOrder(itemId, "Scroll of Escape", need, price, "scroll"));
            }
        }

        return orders;
    }

    private Map<String, Integer> countConsumables(List<ItemSnapshot> inventory) {
        Map<String, Integer> map = new HashMap<>();
        for (ItemSnapshot item : inventory) {
            String name = item.name.toLowerCase();
            if (name.contains("soulshot") && !name.contains("spirit")) {
                map.merge("soulshot", (int) item.count, Integer::sum);
            } else if (name.contains("spiritshot")) {
                map.merge("spiritshot", (int) item.count, Integer::sum);
            } else if (name.contains("arrow") || name.contains("bolt")) {
                map.merge("arrow", (int) item.count, Integer::sum);
            } else if (name.contains("healing potion") || name.contains("lesser healing")
                    || name.contains("greater healing") || name.contains("superior healing")) {
                map.merge("healing_potion", (int) item.count, Integer::sum);
            } else if (name.contains("mana potion") || name.contains("mana drug")) {
                map.merge("mana_potion", (int) item.count, Integer::sum);
            } else if (name.contains("scroll of escape")) {
                map.merge("scroll_escape", (int) item.count, Integer::sum);
            }
        }
        return map;
    }

    private int batchSizeForItem(String itemName) {
        String lower = itemName.toLowerCase();
        if (lower.contains("soulshot") || lower.contains("spiritshot") || lower.contains("arrow")) {
            return ThreadLocalRandom.current().nextInt(200, 501);
        }
        if (lower.contains("potion")) {
            return ThreadLocalRandom.current().nextInt(5, 21);
        }
        if (lower.contains("scroll")) {
            return ThreadLocalRandom.current().nextInt(1, 4);
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // Class / level helpers

    private boolean usesSoulshots() {
        return !playerClass.contains("mage") && !playerClass.contains("wizard")
                && !playerClass.contains("sorcerer") && !playerClass.contains("necromancer")
                && !playerClass.contains("summoner") && !playerClass.contains("bishop")
                && !playerClass.contains("prophet") && !playerClass.contains("elder");
    }

    private boolean usesSpiritshots() {
        return playerClass.contains("mage") || playerClass.contains("wizard")
                || playerClass.contains("sorcerer") || playerClass.contains("necromancer")
                || playerClass.contains("summoner") || playerClass.contains("bishop")
                || playerClass.contains("prophet") || playerClass.contains("elder")
                || playerClass.contains("spellsinger") || playerClass.contains("spellhowler");
    }

    private boolean usesArrows() {
        return playerClass.contains("archer") || playerClass.contains("hawkeye")
                || playerClass.contains("silver ranger") || playerClass.contains("phantom ranger");
    }

    // ------------------------------------------------------------------
    // Item ID mapping (Phase 0: hardcoded core Interlude IDs)

    private int getSoulshotIdForLevel(int level) {
        if (level <= 20) return 1835;   // Soulshot: No-Grade
        if (level <= 40) return 1463;   // Soulshot: D-Grade
        if (level <= 52) return 1464;   // Soulshot: C-Grade
        if (level <= 61) return 1465;   // Soulshot: B-Grade
        if (level <= 76) return 1466;   // Soulshot: A-Grade
        return 1467;                    // Soulshot: S-Grade
    }

    private int getSpiritshotIdForLevel(int level) {
        if (level <= 20) return 2509;   // Spiritshot: No-Grade
        if (level <= 40) return 2510;   // Spiritshot: D-Grade
        if (level <= 52) return 2511;   // Spiritshot: C-Grade
        if (level <= 61) return 2512;   // Spiritshot: B-Grade
        if (level <= 76) return 2513;   // Spiritshot: A-Grade
        return 2514;                    // Spiritshot: S-Grade
    }

    private int getArrowIdForLevel(int level) {
        if (level <= 20) return 17;     // Wooden Arrow
        if (level <= 40) return 1341;   // Bone Arrow
        if (level <= 52) return 1342;   // Steel Arrow
        if (level <= 61) return 1343;   // Silver Arrow
        if (level <= 76) return 1344;   // Mithril Arrow
        return 1345;                    // Shining Arrow
    }

    private int getHealingPotionIdForLevel(int level) {
        if (level <= 20) return 1060;   // Lesser Healing Potion
        if (level <= 40) return 1061;   // Healing Potion
        if (level <= 55) return 1062;   // Greater Healing Potion
        return 1539;                    // Superior Healing Potion (or 5277 for greater CP)
    }

    private int getManaPotionIdForLevel(int level) {
        if (level <= 40) return 726;    // Mana Potion (old)
        return 726;                     // Interlude has limited mana potion options
    }

    private int getEstimatedPrice(int itemId) {
        // Phase 0: rough estimates. Phase 1: query ItemDatabase.
        switch (itemId) {
            case 1835: return 10;   // No-Grade SS
            case 1463: return 15;   // D-Grade SS
            case 1464: return 30;   // C-Grade SS
            case 1465: return 50;   // B-Grade SS
            case 1466: return 80;   // A-Grade SS
            case 1467: return 120;  // S-Grade SS
            case 2509: return 15;   // No-Grade SpS
            case 2510: return 25;   // D-Grade SpS
            case 2511: return 50;   // C-Grade SpS
            case 2512: return 80;   // B-Grade SpS
            case 2513: return 120;  // A-Grade SpS
            case 2514: return 180;  // S-Grade SpS
            case 17:   return 2;    // Wooden Arrow
            case 1341: return 3;    // Bone Arrow
            case 1342: return 4;    // Steel Arrow
            case 1343: return 5;    // Silver Arrow
            case 1344: return 6;    // Mithril Arrow
            case 1345: return 8;    // Shining Arrow
            case 1060: return 20;   // Lesser Healing
            case 1061: return 50;   // Healing
            case 1062: return 100;  // Greater Healing
            case 1539: return 200;  // Superior Healing
            case 726:  return 80;   // Mana Potion
            case 736:  return 500;  // Scroll of Escape
            default:   return 50;
        }
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
}
