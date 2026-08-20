package com.aiplayer.behavior.inventory;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.HashMap;
import java.util.Map;
import com.aiplayer.behavior.combat.ShotManager;
import com.aiplayer.behavior.town.BuyManager;

/**
 * Static database of consumable items, soulshots, and scrolls for Interlude.
 * Maps item IDs to metadata for quick lookup without DB round-trips.
 * 
 * Phase 0: Hardcoded common Interlude items.
 * Phase 1: Load from L2JMobius item XMLs.
 */
public final class ItemDatabase {

    public enum ItemType {
        HEAL_POTION, MANA_POTION, CP_POTION,
        SOULSHOT, SPIRITSHOT, BLESSED_SPIRITSHOT,
        SCROLL_OF_ESCAPE, SCROLL_OF_RESURRECTION,
        BUFF_SCROLL, FOOD, ARROW, BOLT,
        UNKNOWN
    }

    public static final class ItemInfo {
        public final int itemId;
        public final String name;
        public final ItemType type;
        public final int grade; // 0=none, 1=D, 2=C, 3=B, 4=A, 5=S
        public final int weight; // in hundredths of weight units
        public final int stackSize;
        public final int reuseDelayMs; // item reuse delay
        public final int minLevel; // minimum level to use

        public ItemInfo(int itemId, String name, ItemType type, int grade,
                        int weight, int stackSize, int reuseDelayMs, int minLevel) {
            this.itemId = itemId;
            this.name = name;
            this.type = type;
            this.grade = grade;
            this.weight = weight;
            this.stackSize = stackSize;
            this.reuseDelayMs = reuseDelayMs;
            this.minLevel = minLevel;
        }
    }

    private static final Map<Integer, ItemInfo> ITEMS = new HashMap<>();

    static {
        // --- Healing Potions ---
        register(1060, "Lesser Healing Potion", ItemType.HEAL_POTION, 0, 20, 20, 0, 1);
        register(1061, "Healing Potion", ItemType.HEAL_POTION, 0, 30, 20, 0, 1);
        register(1539, "Greater Healing Potion", ItemType.HEAL_POTION, 0, 40, 20, 0, 1);
        register(5591, "CP Potion (Low)", ItemType.CP_POTION, 0, 25, 20, 0, 1);
        register(5592, "CP Potion (High)", ItemType.CP_POTION, 0, 35, 20, 0, 1);

        // --- Mana Potions ---
        register(728, "Mana Potion", ItemType.MANA_POTION, 0, 30, 20, 0, 1);
        register(726, "Greater Mana Potion", ItemType.MANA_POTION, 0, 40, 20, 0, 1);

        // --- Soulshots (grade = weapon grade); IDs match ShotManager/BuyManager ---
        register(1835, "Soulshot: No-Grade", ItemType.SOULSHOT, 0, 10, 1000, 0, 1);
        register(1463, "Soulshot: D-Grade", ItemType.SOULSHOT, 1, 12, 1000, 0, 1);
        register(1464, "Soulshot: C-Grade", ItemType.SOULSHOT, 2, 14, 1000, 0, 1);
        register(1465, "Soulshot: B-Grade", ItemType.SOULSHOT, 3, 16, 1000, 0, 1);
        register(1466, "Soulshot: A-Grade", ItemType.SOULSHOT, 4, 18, 1000, 0, 1);
        register(1467, "Soulshot: S-Grade", ItemType.SOULSHOT, 5, 20, 1000, 0, 1);

        // --- Spiritshots (IDs match ShotManager) ---
        register(2509, "Spiritshot: No-Grade", ItemType.SPIRITSHOT, 0, 10, 1000, 0, 1);
        register(2510, "Spiritshot: D-Grade", ItemType.SPIRITSHOT, 1, 12, 1000, 0, 1);
        register(2511, "Spiritshot: C-Grade", ItemType.SPIRITSHOT, 2, 14, 1000, 0, 1);
        register(2512, "Spiritshot: B-Grade", ItemType.SPIRITSHOT, 3, 16, 1000, 0, 1);
        register(2513, "Spiritshot: A-Grade", ItemType.SPIRITSHOT, 4, 18, 1000, 0, 1);
        register(2514, "Spiritshot: S-Grade", ItemType.SPIRITSHOT, 5, 20, 1000, 0, 1);

        // --- Blessed Spiritshots (IDs match ShotManager) ---
        register(3947, "Blessed Spiritshot: No-Grade", ItemType.BLESSED_SPIRITSHOT, 0, 12, 1000, 0, 1);
        register(3948, "Blessed Spiritshot: D-Grade", ItemType.BLESSED_SPIRITSHOT, 1, 14, 1000, 0, 1);
        register(3949, "Blessed Spiritshot: C-Grade", ItemType.BLESSED_SPIRITSHOT, 2, 16, 1000, 0, 1);
        register(3950, "Blessed Spiritshot: B-Grade", ItemType.BLESSED_SPIRITSHOT, 3, 18, 1000, 0, 1);
        register(3951, "Blessed Spiritshot: A-Grade", ItemType.BLESSED_SPIRITSHOT, 4, 20, 1000, 0, 1);
        register(3952, "Blessed Spiritshot: S-Grade", ItemType.BLESSED_SPIRITSHOT, 5, 22, 1000, 0, 1);

        // --- Scrolls ---
        register(736, "Scroll of Escape", ItemType.SCROLL_OF_ESCAPE, 0, 120, 1, 30000, 1);
        register(737, "Scroll of Resurrection", ItemType.SCROLL_OF_RESURRECTION, 0, 150, 1, 30000, 1);
        register(3936, "Blessed Scroll of Resurrection", ItemType.SCROLL_OF_RESURRECTION, 0, 200, 1, 30000, 1);

        // --- Buff Scrolls (common event items) ---
        register(5703, "Scroll of Guidance", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5704, "Scroll of Death Whisper", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5705, "Scroll of Focus", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5706, "Scroll of Acumen", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5707, "Scroll of Haste", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5708, "Scroll of Agility", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5709, "Scroll of Empower", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5710, "Scroll of Might", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5711, "Scroll of Shield", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5712, "Scroll of Magic Barrier", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5713, "Scroll of Blessed Body", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);
        register(5714, "Scroll of Blessed Soul", ItemType.BUFF_SCROLL, 0, 50, 1, 600000, 1);

        // --- Arrows / Bolts ---
        register(17, "Wooden Arrow", ItemType.ARROW, 0, 5, 1000, 0, 1);
        register(1341, "Bone Arrow", ItemType.ARROW, 0, 6, 1000, 0, 1);
        register(1342, "Steel Arrow", ItemType.ARROW, 1, 7, 1000, 0, 1);
        register(1343, "Silver Arrow", ItemType.ARROW, 2, 8, 1000, 0, 1);
        register(1344, "Mithril Arrow", ItemType.ARROW, 3, 9, 1000, 0, 1);
        register(1345, "Shining Arrow", ItemType.ARROW, 4, 10, 1000, 0, 1);
        register(9632, "Bone Bolt", ItemType.BOLT, 0, 6, 1000, 0, 1);
        register(9633, "Steel Bolt", ItemType.BOLT, 1, 7, 1000, 0, 1);
        register(9634, "Silver Bolt", ItemType.BOLT, 2, 8, 1000, 0, 1);
        register(9635, "Mithril Bolt", ItemType.BOLT, 3, 9, 1000, 0, 1);
        register(9636, "Shining Bolt", ItemType.BOLT, 4, 10, 1000, 0, 1);

        // --- Food ---
        register(4039, "Soft Hinomaki Steak", ItemType.FOOD, 0, 30, 1, 0, 1);
        register(4040, "Soft Hinomaki Steak (Event)", ItemType.FOOD, 0, 30, 1, 0, 1);
    }

    private static void register(int itemId, String name, ItemType type, int grade,
                                  int weight, int stackSize, int reuseDelayMs, int minLevel) {
        ITEMS.put(itemId, new ItemInfo(itemId, name, type, grade, weight, stackSize, reuseDelayMs, minLevel));
    }

    public static ItemInfo get(int itemId) {
        return ITEMS.getOrDefault(itemId, new ItemInfo(itemId, "Unknown", ItemType.UNKNOWN, 0, 0, 1, 0, 1));
    }

    public static boolean isKnown(int itemId) {
        return ITEMS.containsKey(itemId);
    }

    public static ItemInfo findBestPotion(ItemType type, int playerLevel) {
        ItemInfo best = null;
        for (ItemInfo info : ITEMS.values()) {
            if (info.type != type) continue;
            if (info.minLevel > playerLevel) continue;
            if (best == null || info.minLevel > best.minLevel) {
                best = info;
            }
        }
        return best;
    }

    public static ItemInfo findSoulshotForGrade(int grade) {
        for (ItemInfo info : ITEMS.values()) {
            if (info.type == ItemType.SOULSHOT && info.grade == grade) {
                return info;
            }
        }
        return null;
    }

    public static ItemInfo findSpiritshotForGrade(int grade, boolean blessed) {
        ItemType target = blessed ? ItemType.BLESSED_SPIRITSHOT : ItemType.SPIRITSHOT;
        for (ItemInfo info : ITEMS.values()) {
            if (info.type == target && info.grade == grade) {
                return info;
            }
        }
        return null;
    }

    private ItemDatabase() {}
}
