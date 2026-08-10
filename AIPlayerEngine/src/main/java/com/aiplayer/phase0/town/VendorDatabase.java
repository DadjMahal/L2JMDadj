package com.aiplayer.phase0.town;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.*;

/**
 * Static database of NPC vendors across all towns in Interlude.
 * Maps vendor types to NPC IDs, locations, and item categories they handle.
 *
 * Phase 0: Hardcoded core Interlude towns (Giran, Aden, Dion, Gludio, Oren, Hunters).
 * Phase 1: Load from L2JMobius XML npcdata + merchant buylists.
 */
public final class VendorDatabase {

    private VendorDatabase() {}

    public enum VendorType {
        WEAPON_SHOP,      // Sells weapons, buys weapons/armor
        ARMOR_SHOP,       // Sells armor, buys armor
        GROCERY,          // Sells potions, scrolls, arrows, shots, buys misc
        MAGIC_SHOP,       // Sells spellbooks, mats, buys mats
        BLACKSMITH,       // Sells crafting materials, weapon/armor crafting
        WAREHOUSE_KEEPER, // Storage only
        GATEKEEPER,       // Teleport only
        PET_MANAGER,      // Pet items
        ACCESSORY_SHOP    // Jewelry, accessories
    }

    public static final class VendorInfo {
        public final int npcId;
        public final String name;
        public final String town;
        public final VendorType type;
        public final int x;
        public final int y;
        public final int z;
        public final Set<String> buyCategories;
        public final Set<String> sellCategories;
        public final int interactRange;

        public VendorInfo(int npcId, String name, String town, VendorType type,
                          int x, int y, int z,
                          Set<String> buyCategories, Set<String> sellCategories,
                          int interactRange) {
            this.npcId = npcId;
            this.name = name;
            this.town = town;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.buyCategories = Collections.unmodifiableSet(buyCategories);
            this.sellCategories = Collections.unmodifiableSet(sellCategories);
            this.interactRange = interactRange;
        }

        public boolean buysCategory(String category) {
            return buyCategories.contains(category);
        }

        public boolean sellsCategory(String category) {
            return sellCategories.contains(category);
        }
    }

    // ------------------------------------------------------------------
    // Core towns with primary vendor coordinates (Interlude approximate)
    // ------------------------------------------------------------------

    private static final Map<String, List<VendorInfo>> TOWN_VENDORS = new HashMap<>();
    private static final Map<Integer, VendorInfo> BY_NPC_ID = new HashMap<>();

    static {
        initGiran();
        initAden();
        initDion();
        initGludio();
        initOren();
        initHuntersVillage();
    }

    private static void initGiran() {
        String town = "Giran";
        add(town, v(30084, "Giran Weapon Shop", town, VendorType.WEAPON_SHOP,
                80874, 147946, -3464,
                set("weapon", "armor"), set("weapon"), 150));
        add(town, v(30085, "Giran Armor Shop", town, VendorType.ARMOR_SHOP,
                80874, 148546, -3464,
                set("armor", "shield"), set("armor", "shield"), 150));
        add(town, v(30086, "Giran Grocery", town, VendorType.GROCERY,
                79774, 146346, -3464,
                set("misc", "quest", "junk"), set("potion", "scroll", "arrow", "shot", "food"), 150));
        add(town, v(30087, "Giran Magic Shop", town, VendorType.MAGIC_SHOP,
                82874, 147346, -3464,
                set("material", "spellbook", "crystal"), set("material", "spellbook", "crystal"), 150));
        add(town, v(30088, "Giran Blacksmith", town, VendorType.BLACKSMITH,
                81874, 149346, -3464,
                set("weapon", "armor", "material"), set("material", "recipe"), 150));
        add(town, v(30090, "Giran Warehouse", town, VendorType.WAREHOUSE_KEEPER,
                78874, 147346, -3464,
                set(), set(), 150));
        add(town, v(30080, "Giran Gatekeeper", town, VendorType.GATEKEEPER,
                82874, 148746, -3464,
                set(), set(), 150));
    }

    private static void initAden() {
        String town = "Aden";
        add(town, v(30840, "Aden Weapon Shop", town, VendorType.WEAPON_SHOP,
                147446, 27046, -2208,
                set("weapon", "armor"), set("weapon"), 150));
        add(town, v(30841, "Aden Armor Shop", town, VendorType.ARMOR_SHOP,
                147446, 27646, -2208,
                set("armor", "shield"), set("armor", "shield"), 150));
        add(town, v(30842, "Aden Grocery", town, VendorType.GROCERY,
                146446, 26446, -2208,
                set("misc", "quest", "junk"), set("potion", "scroll", "arrow", "shot", "food"), 150));
        add(town, v(30843, "Aden Magic Shop", town, VendorType.MAGIC_SHOP,
                148446, 26846, -2208,
                set("material", "spellbook", "crystal"), set("material", "spellbook", "crystal"), 150));
        add(town, v(30844, "Aden Blacksmith", town, VendorType.BLACKSMITH,
                148446, 27246, -2208,
                set("weapon", "armor", "material"), set("material", "recipe"), 150));
        add(town, v(30845, "Aden Warehouse", town, VendorType.WAREHOUSE_KEEPER,
                145446, 27046, -2208,
                set(), set(), 150));
        add(town, v(30836, "Aden Gatekeeper", town, VendorType.GATEKEEPER,
                148446, 27646, -2208,
                set(), set(), 150));
    }

    private static void initDion() {
        String town = "Dion";
        add(town, v(30073, "Dion Weapon Shop", town, VendorType.WEAPON_SHOP,
                18574, 145346, -3096,
                set("weapon", "armor"), set("weapon"), 150));
        add(town, v(30074, "Dion Armor Shop", town, VendorType.ARMOR_SHOP,
                18574, 145946, -3096,
                set("armor", "shield"), set("armor", "shield"), 150));
        add(town, v(30075, "Dion Grocery", town, VendorType.GROCERY,
                17574, 144746, -3096,
                set("misc", "quest", "junk"), set("potion", "scroll", "arrow", "shot", "food"), 150));
        add(town, v(30076, "Dion Magic Shop", town, VendorType.MAGIC_SHOP,
                19574, 145346, -3096,
                set("material", "spellbook", "crystal"), set("material", "spellbook", "crystal"), 150));
        add(town, v(30077, "Dion Warehouse", town, VendorType.WAREHOUSE_KEEPER,
                16574, 145346, -3096,
                set(), set(), 150));
        add(town, v(30078, "Dion Gatekeeper", town, VendorType.GATEKEEPER,
                19574, 145946, -3096,
                set(), set(), 150));
    }

    private static void initGludio() {
        String town = "Gludio";
        add(town, v(30056, "Gludio Weapon Shop", town, VendorType.WEAPON_SHOP,
                -14674, 123346, -3112,
                set("weapon", "armor"), set("weapon"), 150));
        add(town, v(30057, "Gludio Armor Shop", town, VendorType.ARMOR_SHOP,
                -14674, 123946, -3112,
                set("armor", "shield"), set("armor", "shield"), 150));
        add(town, v(30058, "Gludio Grocery", town, VendorType.GROCERY,
                -15674, 122746, -3112,
                set("misc", "quest", "junk"), set("potion", "scroll", "arrow", "shot", "food"), 150));
        add(town, v(30059, "Gludio Magic Shop", town, VendorType.MAGIC_SHOP,
                -13674, 123346, -3112,
                set("material", "spellbook", "crystal"), set("material", "spellbook", "crystal"), 150));
        add(town, v(30060, "Gludio Warehouse", town, VendorType.WAREHOUSE_KEEPER,
                -16674, 123346, -3112,
                set(), set(), 150));
        add(town, v(30054, "Gludio Gatekeeper", town, VendorType.GATEKEEPER,
                -13674, 123946, -3112,
                set(), set(), 150));
    }

    private static void initOren() {
        String town = "Oren";
        add(town, v(30150, "Oren Weapon Shop", town, VendorType.WEAPON_SHOP,
                83074, 53246, -1568,
                set("weapon", "armor"), set("weapon"), 150));
        add(town, v(30151, "Oren Armor Shop", town, VendorType.ARMOR_SHOP,
                83074, 53846, -1568,
                set("armor", "shield"), set("armor", "shield"), 150));
        add(town, v(30152, "Oren Grocery", town, VendorType.GROCERY,
                82074, 52646, -1568,
                set("misc", "quest", "junk"), set("potion", "scroll", "arrow", "shot", "food"), 150));
        add(town, v(30153, "Oren Magic Shop", town, VendorType.MAGIC_SHOP,
                84074, 53046, -1568,
                set("material", "spellbook", "crystal"), set("material", "spellbook", "crystal"), 150));
        add(town, v(30154, "Oren Warehouse", town, VendorType.WAREHOUSE_KEEPER,
                81074, 53246, -1568,
                set(), set(), 150));
        add(town, v(30146, "Oren Gatekeeper", town, VendorType.GATEKEEPER,
                84074, 53846, -1568,
                set(), set(), 150));
    }

    private static void initHuntersVillage() {
        String town = "HuntersVillage";
        add(town, v(30252, "Hunters Weapon Shop", town, VendorType.WEAPON_SHOP,
                116974, 76246, -2728,
                set("weapon", "armor"), set("weapon"), 150));
        add(town, v(30253, "Hunters Armor Shop", town, VendorType.ARMOR_SHOP,
                116974, 76846, -2728,
                set("armor", "shield"), set("armor", "shield"), 150));
        add(town, v(30254, "Hunters Grocery", town, VendorType.GROCERY,
                115974, 75646, -2728,
                set("misc", "quest", "junk"), set("potion", "scroll", "arrow", "shot", "food"), 150));
        add(town, v(30255, "Hunters Warehouse", town, VendorType.WAREHOUSE_KEEPER,
                114974, 76246, -2728,
                set(), set(), 150));
        add(town, v(30248, "Hunters Gatekeeper", town, VendorType.GATEKEEPER,
                117974, 76846, -2728,
                set(), set(), 150));
    }

    // ------------------------------------------------------------------

    private static void add(String town, VendorInfo vi) {
        TOWN_VENDORS.computeIfAbsent(town, k -> new ArrayList<>()).add(vi);
        BY_NPC_ID.put(vi.npcId, vi);
    }

    private static VendorInfo v(int npcId, String name, String town, VendorType type,
                                int x, int y, int z,
                                Set<String> buyCats, Set<String> sellCats, int range) {
        return new VendorInfo(npcId, name, town, type, x, y, z, buyCats, sellCats, range);
    }

    @SafeVarargs
    private static <T> Set<T> set(T... items) {
        Set<T> s = new HashSet<>();
        Collections.addAll(s, items);
        return s;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public static List<VendorInfo> getVendorsInTown(String town) {
        return TOWN_VENDORS.getOrDefault(town, Collections.emptyList());
    }

    public static VendorInfo getVendorByNpcId(int npcId) {
        return BY_NPC_ID.get(npcId);
    }

    public static VendorInfo findNearestVendor(String town, VendorType type, int fromX, int fromY, int fromZ) {
        List<VendorInfo> list = getVendorsInTown(town);
        VendorInfo best = null;
        double bestDist = Double.MAX_VALUE;
        for (VendorInfo vi : list) {
            if (vi.type != type) continue;
            double d = distSq(fromX, fromY, fromZ, vi.x, vi.y, vi.z);
            if (d < bestDist) {
                bestDist = d;
                best = vi;
            }
        }
        return best;
    }

    public static VendorInfo findNearestVendorBuying(String town, String category, int fromX, int fromY, int fromZ) {
        List<VendorInfo> list = getVendorsInTown(town);
        VendorInfo best = null;
        double bestDist = Double.MAX_VALUE;
        for (VendorInfo vi : list) {
            if (!vi.buysCategory(category)) continue;
            double d = distSq(fromX, fromY, fromZ, vi.x, vi.y, vi.z);
            if (d < bestDist) {
                bestDist = d;
                best = vi;
            }
        }
        return best;
    }

    public static VendorInfo findNearestVendorSelling(String town, String category, int fromX, int fromY, int fromZ) {
        List<VendorInfo> list = getVendorsInTown(town);
        VendorInfo best = null;
        double bestDist = Double.MAX_VALUE;
        for (VendorInfo vi : list) {
            if (!vi.sellsCategory(category)) continue;
            double d = distSq(fromX, fromY, fromZ, vi.x, vi.y, vi.z);
            if (d < bestDist) {
                bestDist = d;
                best = vi;
            }
        }
        return best;
    }

    public static List<String> getAllTowns() {
        return new ArrayList<>(TOWN_VENDORS.keySet());
    }

    public static String detectTown(int x, int y, int z) {
        // Simple region detection based on proximity to town centers
        // Phase 0: Hardcoded town centers
        Map<String, int[]> centers = new HashMap<>();
        centers.put("Giran", new int[]{80874, 147346, -3464});
        centers.put("Aden", new int[]{147446, 27046, -2208});
        centers.put("Dion", new int[]{18574, 145346, -3096});
        centers.put("Gludio", new int[]{-14674, 123346, -3112});
        centers.put("Oren", new int[]{83074, 53246, -1568});
        centers.put("HuntersVillage", new int[]{116974, 76246, -2728});

        String best = null;
        double bestDist = 200000; // ~450 units radius
        for (Map.Entry<String, int[]> e : centers.entrySet()) {
            int[] c = e.getValue();
            double d = distSq(x, y, z, c[0], c[1], c[2]);
            if (d < bestDist) {
                bestDist = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        long dz = (long) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}