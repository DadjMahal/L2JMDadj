package com.aiplayer.knowledge;

/**
 * MODE: COMPLETE. GK-8 — gear/build recommender: the best next WEAPON a bot of a given class,
 * level and adena purse should buy. Replaces RestockPlanner's old hardcoded placeholder item
 * with a data-driven pick assembled from the generated knowledge JSON:
 *
 * <ul>
 *   <li><b>items.json</b> — weapon candidates (type/weaponType/grade/price),</li>
 *   <li><b>shops.json</b> — only weapons a buylist actually sells are recommendable,</li>
 *   <li><b>chains.json</b> — the gear ladder starts at the race×base chain's first-class
 *       milestone (L20 on every chain), so a fresh bot doesn't blow its adena on gear,</li>
 *   <li><b>classes.json</b> — class names drive the weapon-type preference
 *       (Hawkeye→BOW, Warlord→POLE, mystics→BLUNT, …).</li>
 * </ul>
 *
 * <p>Pure and deterministic for a fixed KnowledgeBase state: the same (classId, level, adena)
 * always yields the same pick. Ranking within a preference: primary weapon type first, then
 * the highest affordable price (within one grade, price tracks quality), then lowest item id.
 * The bot keeps 20% of its purse for consumables: the budget is 4/5 of the adena passed in.
 */
public final class GearGuide
{
    /** Interlude weapon-type a class name token implies; first token contained in the class name wins. */
    private static final String[][] WEAPON_TOKENS =
    {
        // archers
        {"Hawkeye", "BOW"}, {"Silver Ranger", "BOW"}, {"Phantom Ranger", "BOW"},
        {"Sagittarius", "BOW"}, {"Moonlight Sentinel", "BOW"}, {"Ghost Sentinel", "BOW"},
        // daggers (rogues, scouts, dwarf spoilers)
        {"Treasure Hunter", "DAGGER"}, {"Plains Walker", "DAGGER"}, {"Abyss Walker", "DAGGER"},
        {"Wind Rider", "DAGGER"}, {"Ghost Hunter", "DAGGER"}, {"Adventurer", "DAGGER"},
        {"Scavenger", "DAGGER"}, {"Bounty Hunter", "DAGGER"}, {"Fortune Seeker", "DAGGER"},
        {"Rogue", "DAGGER"}, {"Assassin", "DAGGER"}, {"Elven Scout", "DAGGER"},
        // polearm
        {"Warlord", "POLE"}, {"Dreadnought", "POLE"},
        // dual swords
        {"Gladiator", "DUAL"}, {"Duelist", "DUAL"}, {"Bladedancer", "DUAL"}, {"Spectral Dancer", "DUAL"},
        // fists
        {"Tyrant", "DUALFIST"}, {"Grand Khavatari", "DUALFIST"}, {"Monk", "DUALFIST"},
        // dwarf crafters swing hammers
        {"Warsmith", "BLUNT"}, {"Maestro", "BLUNT"}, {"Artisan", "BLUNT"},
    };

    /** Name fragments marking a spell-casting class (drives the BLUNT default + potion sizing). */
    private static final String[] MYSTIC_TOKENS =
    {
        "Mystic", "Oracle", "Wizard", "Sorcerer", "Necromancer", "Spellsinger", "Spellhowler",
        "Cleric", "Bishop", "Elder", "Prophet", "Summoner", "Warlock", "Shaman", "Warcryer",
        "Overlord", "Archmage", "Soultaker", "Muse", "Screamer", "Saint", "Dominator",
        "Cardinal", "Hierophant",
    };

    /** Fallback first-class milestone when the chain for a base is unknown. */
    private static final int DEFAULT_FIRST_CLASS_LEVEL = 20;

    /** Fraction of the purse the bot may spend on gear (rest keeps ammo/potion money). */
    private static final long GEAR_BUDGET_NUMERATOR = 4;
    private static final long GEAR_BUDGET_DENOMINATOR = 5;

    private GearGuide()
    {
    }

    /** One recommended purchase: what to buy, at what reference price, and why it fits. */
    public static final class GearPick
    {
        public final int itemId;
        public final String name;
        public final long price;
        public final String grade;
        public final String weaponType;

        public GearPick(int itemId, String name, long price, String grade, String weaponType)
        {
            this.itemId = itemId;
            this.name = name;
            this.price = price;
            this.grade = grade;
            this.weaponType = weaponType;
        }
    }

    /**
     * Best next weapon for this bot, or null when none applies (no adena, below the chain's
     * first-class milestone, no shop-sold weapon of the right grade within budget).
     *
     * @param classId the bot's class (base class from CharSelectInfo is fine; tier names match too)
     * @param level   current level (drives the grade band)
     * @param adena   current purse; only 4/5 of it may go to gear, 0 or negative disables
     */
    public static GearPick recommendWeapon(int classId, int level, long adena)
    {
        if (adena <= 0)
        {
            return null;
        }
        String grade = gradeFor(level);
        if ("NONE".equals(grade))
        {
            return null; // no-grade shopping sprees: the gear ladder starts at first class
        }
        KnowledgeBase kb = KnowledgeBase.getInstance();
        if (level < firstClassLevel(kb, classId))
        {
            return null;
        }
        String[] prefs = weaponPreference(classId);
        long budget = adena * GEAR_BUDGET_NUMERATOR / GEAR_BUDGET_DENOMINATOR;

        KnowledgeBase.Item best = null;
        int bestPref = Integer.MAX_VALUE;
        for (KnowledgeBase.Item it : kb.allItems())
        {
            if (!"Weapon".equals(it.type) || !grade.equals(it.grade) || it.price <= 0 || it.price > budget)
            {
                continue;
            }
            if (!usableName(it.name) || !kb.isSoldInShop(it.id))
            {
                continue;
            }
            int pref = prefIndex(prefs, it.weaponType);
            if (pref < 0)
            {
                continue;
            }
            if (best == null || pref < bestPref
                || (pref == bestPref && (it.price > best.price
                    || (it.price == best.price && it.id < best.id))))
            {
                best = it;
                bestPref = pref;
            }
        }
        return best != null
            ? new GearPick(best.id, best.name, best.price, best.grade, best.weaponType)
            : null;
    }

    /** True when the class casts spells (mystic line) — sizes HP-potion restocks and the BLUNT default. */
    public static boolean isMysticClass(int classId)
    {
        KnowledgeBase.ClassInfo info = KnowledgeBase.getInstance().classInfo(classId);
        return info != null && matchesAny(info.name, MYSTIC_TOKENS);
    }

    /** Interlude grade band for a level: NONE &lt;20, D 20-39, C 40-51, B 52-60, A 61-75, S 76+. */
    public static String gradeFor(int level)
    {
        if (level < 20)
        {
            return "NONE";
        }
        if (level < 40)
        {
            return "D";
        }
        if (level < 52)
        {
            return "C";
        }
        if (level < 61)
        {
            return "B";
        }
        if (level < 76)
        {
            return "A";
        }
        return "S";
    }

    /** Ordered weapon types for the class: token match first, then the line's default as fallback. */
    private static String[] weaponPreference(int classId)
    {
        KnowledgeBase.ClassInfo info = KnowledgeBase.getInstance().classInfo(classId);
        boolean mystic = info != null && matchesAny(info.name, MYSTIC_TOKENS);
        String primary = null;
        if (info != null)
        {
            for (String[] tok : WEAPON_TOKENS)
            {
                if (info.name.contains(tok[0]))
                {
                    primary = tok[1];
                    break;
                }
            }
        }
        String fallback = mystic ? "BLUNT" : "SWORD";
        return primary != null && !primary.equals(fallback)
            ? new String[] {primary, fallback}
            : new String[] {fallback};
    }

    /** Level of the chain's first-class transfer step (all 9 chains say 20); default 20 when unknown. */
    private static int firstClassLevel(KnowledgeBase kb, int classId)
    {
        KnowledgeBase.ClassInfo info = kb.classInfo(classId);
        int base = info != null ? info.baseClassId : classId;
        for (KnowledgeBase.ChainStep s : kb.chainSteps(base))
        {
            if ("firstClass".equals(s.kind) && s.level > 0)
            {
                return s.level;
            }
        }
        return DEFAULT_FIRST_CLASS_LEVEL;
    }

    private static int prefIndex(String[] prefs, String weaponType)
    {
        for (int i = 0; i < prefs.length; i++)
        {
            if (prefs[i].equals(weaponType))
            {
                return i;
            }
        }
        return -1;
    }

    /** Placeholder names from the datapack ("0", "Monster Only…") are not player gear. */
    private static boolean usableName(String name)
    {
        return name != null && !name.isEmpty() && !"0".equals(name) && !name.contains("Monster Only");
    }

    private static boolean matchesAny(String name, String[] tokens)
    {
        for (String t : tokens)
        {
            if (name.contains(t))
            {
                return true;
            }
        }
        return false;
    }
}
