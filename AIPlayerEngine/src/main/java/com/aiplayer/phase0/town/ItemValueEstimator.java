package com.aiplayer.phase0.town;

/** MODE: COMPLETE. Pure function over ItemSnapshot, no state dependency — import fix alone was sufficient, now compiles clean. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.ItemSnapshot; // real class now, was never defined under GameStateMirror

import java.util.*;

/**
 * Determines item value classification for sell/keep/warehouse decisions.
 * Uses item grade, level, category, and player class to decide fate.
 *
 * Categories:
 * - JUNK: Always sell (monster drops, low-level gear, quest leftovers)
 * - CONSUMABLE: Keep in inventory (potions, shots, arrows, scrolls)
 * - EQUIPMENT_UPGRADE: Keep if better than current (weapons, armor, jewelry)
 * - MATERIAL: Warehouse or sell depending on craft interest
 * - QUEST: Keep if active quest needs it
 * - VALUABLE: Never sell (rare drops, enchant scrolls, boss items)
 */
public final class ItemValueEstimator {

    public enum ItemFate {
        SELL,           // Sell to nearest appropriate vendor
        KEEP,           // Keep in inventory
        WAREHOUSE,      // Deposit to warehouse
        DESTROY         // Drop if overweight and worthless
    }

    // Item categories derived from itemId ranges and names
    private static final Set<Integer> VALUABLE_ITEM_IDS = new HashSet<>();
    static {
        // Enchant scrolls (various grades)
        VALUABLE_ITEM_IDS.add(959);  // Scroll: Enchant Weapon (S)
        VALUABLE_ITEM_IDS.add(960);  // Scroll: Enchant Armor (S)
        VALUABLE_ITEM_IDS.add(6577); // Blessed Scroll: Enchant Weapon (S)
        VALUABLE_ITEM_IDS.add(6578); // Blessed Scroll: Enchant Armor (S)
        // Life stones
        VALUABLE_ITEM_IDS.add(8723); // High-Grade Life Stone: level 76
        VALUABLE_ITEM_IDS.add(8733);
        VALUABLE_ITEM_IDS.add(8743);
        VALUABLE_ITEM_IDS.add(8753);
        // Boss jewels (core examples)
        VALUABLE_ITEM_IDS.add(6660); // Ring of Queen Ant
        VALUABLE_ITEM_IDS.add(6661); // Earring of Orfen
        VALUABLE_ITEM_IDS.add(6662); // Ring of Core
        VALUABLE_ITEM_IDS.add(6658); // Necklace of Valakas
        VALUABLE_ITEM_IDS.add(6659); // Earring of Antharas
        // Attribute stones/crystals
        VALUABLE_ITEM_IDS.add(9546); // Fire Stone
        VALUABLE_ITEM_IDS.add(9547); // Water Stone
        VALUABLE_ITEM_IDS.add(9548); // Earth Stone
        VALUABLE_ITEM_IDS.add(9549); // Wind Stone
        VALUABLE_ITEM_IDS.add(9550); // Dark Stone
        VALUABLE_ITEM_IDS.add(9551); // Holy Stone
    }

    // Junk item name patterns (lowercase)
    private static final List<String> JUNK_PATTERNS = Arrays.asList(
            "broken", "rusty", "torn", "cracked", "damaged",
            "animal bone", "animal skin", "charcoal", "thread", "suede",
            "stem", "braided hemp", "cord", "leather", "iron ore",
            "coal", "stone", "varnish", "cokes", "steel",
            "bronze", "silver", "mithril ore", "adamantite", "oriharukon"
    );

    // Consumable patterns
    private static final List<String> CONSUMABLE_PATTERNS = Arrays.asList(
            "potion", "healing", "mana", "antidote", "bandage",
            "soulshot", "spiritshot", "blessed spiritshot",
            "arrow", "bolt", "scroll of escape", "scroll of resurrection"
    );

    // Equipment slots for comparison
    private static final Set<String> EQUIPMENT_CATEGORIES = new HashSet<>(Arrays.asList(
            "weapon", "armor", "shield", "jewelry", "accessory", "helmet", "boots", "gloves"
    ));

    private final String accountName;
    private final int playerLevel;
    private final String playerClass;

    public ItemValueEstimator(String accountName, int playerLevel, String playerClass) {
        this.accountName = accountName;
        this.playerLevel = playerLevel;
        this.playerClass = playerClass;
    }

    /**
     * Evaluate a single item and decide its fate.
     */
    public ItemFate evaluate(ItemSnapshot item) {
        if (item == null) return ItemFate.DESTROY;

        // 1. Hardcoded valuable items never sell
        if (VALUABLE_ITEM_IDS.contains(item.itemId)) {
            return ItemFate.KEEP;
        }

        // 2. Active quest items
        if (item.isQuestItem) {
            return ItemFate.KEEP;
        }

        // 3. Consumables always keep
        String lowerName = item.name.toLowerCase();
        if (matchesAny(lowerName, CONSUMABLE_PATTERNS)) {
            // But sell low-level shots if player level is much higher
            if (isObsoleteShot(item)) {
                return ItemFate.SELL;
            }
            return ItemFate.KEEP;
        }

        // 4. Equipment evaluation
        String category = categorizeItem(item);
        if (EQUIPMENT_CATEGORIES.contains(category)) {
            return evaluateEquipment(item, category);
        }

        // 5. Materials
        if (category.equals("material")) {
            // Keep if player is crafter/spoiler
            if (isCrafterClass()) {
                return ItemFate.WAREHOUSE;
            }
            return ItemFate.SELL;
        }

        // 6. Recipe evaluation
        if (category.equals("recipe")) {
            if (isCrafterClass() && item.levelRequirement <= playerLevel + 5) {
                return ItemFate.WAREHOUSE;
            }
            return ItemFate.SELL;
        }

        // 7. Spellbooks
        if (category.equals("spellbook")) {
            if (item.levelRequirement <= playerLevel && item.levelRequirement > playerLevel - 10) {
                return ItemFate.KEEP;
            }
            return ItemFate.SELL;
        }

        // 8. Junk detection
        if (matchesAny(lowerName, JUNK_PATTERNS) || (item.grade == 0 && item.levelRequirement < playerLevel - 15)) {
            return ItemFate.SELL;
        }

        // 9. Default: sell low value, keep medium, warehouse high
        if (item.sellPrice < 100) {
            return ItemFate.DESTROY;
        } else if (item.sellPrice < 5000) {
            return ItemFate.SELL;
        } else if (item.sellPrice > 50000) {
            return ItemFate.WAREHOUSE;
        }

        return ItemFate.SELL;
    }

    /**
     * Batch evaluate all inventory items and return categorized lists.
     */
    public Map<ItemFate, List<ItemSnapshot>> evaluateAll(List<ItemSnapshot> inventory) {
        Map<ItemFate, List<ItemSnapshot>> result = new EnumMap<>(ItemFate.class);
        for (ItemFate fate : ItemFate.values()) {
            result.put(fate, new ArrayList<>());
        }
        for (ItemSnapshot item : inventory) {
            result.get(evaluate(item)).add(item);
        }
        return result;
    }

    /**
     * Quick check: should we go to town to sell?
     * Triggers when inventory is full or junk value exceeds threshold.
     */
    public boolean shouldGoSell(List<ItemSnapshot> inventory, int maxSlots, int usedSlots, int adena) {
        if (usedSlots >= maxSlots - 2) return true;

        int junkValue = 0;
        for (ItemSnapshot item : inventory) {
            if (evaluate(item) == ItemFate.SELL) {
                junkValue += item.sellPrice * item.count;
            }
        }
        // Go sell if junk is worth more than 50k or inventory is tight
        return junkValue > 50000 || (usedSlots >= maxSlots - 5 && junkValue > 10000);
    }

    /**
     * Check if we need to restock consumables.
     */
    public boolean shouldRestock(List<ItemSnapshot> inventory) {
        int ssCount = countItem(inventory, "soulshot");
        int arrowCount = countItem(inventory, "arrow");
        int potionCount = countItem(inventory, "potion");

        boolean needsSS = usesSoulshots() && ssCount < 500;
        boolean needsArrows = usesArrows() && arrowCount < 500;
        boolean needsPotions = potionCount < 20;

        return needsSS || needsArrows || needsPotions;
    }

    // ------------------------------------------------------------------

    private ItemFate evaluateEquipment(ItemSnapshot item, String category) {
        // Keep if close to player level and better grade
        int levelDiff = item.levelRequirement - playerLevel;

        if (levelDiff > 5) {
            // Too high to use, warehouse if valuable
            return item.sellPrice > 20000 ? ItemFate.WAREHOUSE : ItemFate.SELL;
        }
        if (levelDiff >= -5) {
            // Near level, might be upgrade
            return ItemFate.KEEP;
        }
        if (levelDiff >= -15) {
            // Somewhat outdated, sell
            return ItemFate.SELL;
        }
        // Very outdated
        return ItemFate.SELL;
    }

    private String categorizeItem(ItemSnapshot item) {
        String name = item.name.toLowerCase();
        if (name.contains("recipe")) return "recipe";
        if (name.contains("spellbook") || name.contains("spell book")) return "spellbook";
        if (name.contains("soulshot") || name.contains("spiritshot")) return "shot";
        if (name.contains("arrow") || name.contains("bolt")) return "arrow";
        if (name.contains("potion")) return "potion";
        if (name.contains("scroll")) return "scroll";
        if (name.contains("weapon") || name.contains("sword") || name.contains("bow")
                || name.contains("dagger") || name.contains("blunt") || name.contains("polearm")) return "weapon";
        if (name.contains("armor") || name.contains("breastplate") || name.contains("leather")
                || name.contains("tunic")) return "armor";
        if (name.contains("shield")) return "shield";
        if (name.contains("helmet") || name.contains("circlet")) return "helmet";
        if (name.contains("boot")) return "boots";
        if (name.contains("glove")) return "gloves";
        if (name.contains("ring") || name.contains("earring") || name.contains("necklace")) return "jewelry";
        if (name.contains("material") || name.contains("ore") || name.contains("thread")
                || name.contains("leather") || name.contains("bone")) return "material";
        if (name.contains("crystal")) return "crystal";
        return "misc";
    }

    private boolean isObsoleteShot(ItemSnapshot item) {
        String name = item.name.toLowerCase();
        // Extract grade from name: "Soulshot: No-Grade", "Soulshot: D-Grade", etc.
        if (name.contains("no-grade") || name.contains("no grade")) {
            return playerLevel > 20;
        }
        if (name.contains("d-grade")) return playerLevel > 40;
        if (name.contains("c-grade")) return playerLevel > 52;
        if (name.contains("b-grade")) return playerLevel > 61;
        if (name.contains("a-grade")) return playerLevel > 76;
        return false;
    }

    private boolean isCrafterClass() {
        String c = playerClass.toLowerCase();
        return c.contains("artisan") || c.contains("warsmith") || c.contains("spoil");
    }

    private boolean usesSoulshots() {
        String c = playerClass.toLowerCase();
        return !c.contains("mage") && !c.contains("wizard") && !c.contains("sorcerer")
                && !c.contains("necromancer") && !c.contains("summoner");
    }

    private boolean usesArrows() {
        String c = playerClass.toLowerCase();
        return c.contains("archer") || c.contains("hawkeye") || c.contains("silver ranger")
                || c.contains("phantom ranger");
    }

    private int countItem(List<ItemSnapshot> inventory, String pattern) {
        int count = 0;
        for (ItemSnapshot item : inventory) {
            if (item.name.toLowerCase().contains(pattern)) {
                count += (int) item.count;
            }
        }
        return count;
    }

    private boolean matchesAny(String text, List<String> patterns) {
        for (String p : patterns) {
            if (text.contains(p)) return true;
        }
        return false;
    }
}