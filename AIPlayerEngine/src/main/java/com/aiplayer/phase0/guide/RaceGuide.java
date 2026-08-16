package com.aiplayer.phase0.guide;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * MODE: COMPLETE. The per-race / per-profession guide map for AI bots.
 *
 * <p>Grounded entirely in the real L2 Interlude sources bundled with this datapack:
 * <ul>
 *   <li>race start towns/centroids &rarr; custom_town.xml</li>
 *   <li>profession tree (classId/name/parent) &rarr; classList.xml</li>
 *   <li>newbie chain (Q999 tutorial + Q1..Q10) &rarr; quest scripts + spawns/*.xml</li>
 *   <li>1st class change &rarr; "Path of the ..." quests Q401..Q418</li>
 *   <li>2nd class change &rarr; Trial/Testimony quests Q211..Q235</li>
 *   <li>3rd class change &rarr; Saga quests Q70..Q100</li>
 *   <li>every quest NPC world coordinate resolved from the datapack spawn files</li>
 *   <li>teleport legs re-use the real routes of town/TeleportManager</li>
 * </ul>
 *
 * <p>The registry is a pure, side-effect-free data+lookup layer. It does not move the bot; the
 * navigation components (QuestNpcNavigator / TeleportManager / TownNavigator) query it.
 */
public final class RaceGuide
{
    private RaceGuide() { }

    // -------------------------------------------------------------------------
    // Profession tree (classList.xml)
    // -------------------------------------------------------------------------

    private static final int[] TIER1_IDS = { 1, 4, 7, 11, 15, 19, 22, 26, 29, 32, 35, 39, 42, 45, 47, 50, 54, 56 };

    private static int tierOf(int classId, int parentClassId)
    {
        if (parentClassId < 0)
            return 0;                       // base race class
        if (classId >= 88)
            return 3;                       // 3rd profession
        for (int t : TIER1_IDS)
            if (t == classId)
                return 1;                   // 1st profession
        return 2;                           // 2nd profession
    }

    /** A node of the profession tree; {@code parentClassId == -1} for a base race class. */
    public static final class ProfessionNode
    {
        public final int classId;
        public final String name;
        public final PlayerRace race;
        public final int parentClassId;
        public final int tier; // 0 base, 1 first, 2 second, 3 third

        ProfessionNode(int classId, String name, int parentClassId)
        {
            this.classId = classId;
            this.name = name;
            this.parentClassId = parentClassId;
            this.race = PlayerRace.ofClassId(classId);
            this.tier = tierOf(classId, parentClassId);
        }

        @Override
        public String toString()
        {
            return classId + " " + name + "[" + race + "/tier" + tier + "]";
        }
    }

    private static final Map<Integer, ProfessionNode> PROFESSIONS = new TreeMap<>();

    private static void addClass(int id, String name, int parent)
    {
        PROFESSIONS.put(id, new ProfessionNode(id, name, parent));
    }

    private static void registerClassTree()
    {
        // Base races
        addClass(0, "Human Fighter", -1);   addClass(10, "Human Mystic", -1);
        addClass(18, "Elven Fighter", -1);  addClass(25, "Elven Mystic", -1);
        addClass(31, "Dark Fighter", -1);   addClass(38, "Dark Mystic", -1);
        addClass(44, "Orc Fighter", -1);    addClass(49, "Orc Mystic", -1);
        addClass(53, "Dwarf Fighter", -1);
        // Human 1st
        addClass(1, "Warrior", 0);          addClass(11, "Human Wizard", 10);
        addClass(4, "Human Knight", 0);     addClass(15, "Cleric", 10);
        addClass(7, "Rogue", 0);
        // Elven 1st
        addClass(19, "Elven Knight", 18);   addClass(26, "Elven Wizard", 25);
        addClass(22, "Elven Scout", 18);    addClass(29, "Elven Oracle", 25);
        // Dark Elven 1st
        addClass(32, "Palus Knight", 31);   addClass(39, "Dark Wizard", 38);
        addClass(35, "Assassin", 31);       addClass(42, "Shillien Oracle", 38);
        // Orc 1st
        addClass(45, "Orc Raider", 44);     addClass(50, "Orc Shaman", 49);
        addClass(47, "Orc Monk", 44);
        // Dwarf 1st
        addClass(54, "Scavenger", 53);      addClass(56, "Artisan", 53);
        // Human 2nd
        addClass(2, "Gladiator", 1);        addClass(3, "Warlord", 1);
        addClass(5, "Paladin", 4);          addClass(6, "Dark Avenger", 4);
        addClass(8, "Treasure Hunter", 7);  addClass(9, "Hawkeye", 7);
        addClass(12, "Sorcerer", 11);       addClass(13, "Necromancer", 11);
        addClass(14, "Warlock", 11);        addClass(16, "Bishop", 15); addClass(17, "Prophet", 15);
        // Elven 2nd
        addClass(20, "Temple Knight", 19);  addClass(21, "Sword Singer", 19);
        addClass(23, "Plains Walker", 22);  addClass(24, "Silver Ranger", 22);
        addClass(27, "Spellsinger", 26);    addClass(28, "Elemental Summoner", 26);
        addClass(30, "Elven Elder", 29);
        // Dark Elven 2nd
        addClass(33, "Shillien Knight", 32); addClass(34, "Bladedancer", 32);
        addClass(36, "Abyss Walker", 35);   addClass(37, "Phantom Ranger", 35);
        addClass(40, "Spellhowler", 39);    addClass(41, "Phantom Summoner", 39);
        addClass(43, "Shillien Elder", 42);
        // Orc 2nd
        addClass(46, "Destroyer", 45);      addClass(48, "Tyrant", 47);
        addClass(51, "Overlord", 50);       addClass(52, "Warcryer", 50);
        // Dwarf 2nd
        addClass(55, "Bounty Hunter", 54);  addClass(57, "Warsmith", 56);
        // Human 3rd
        addClass(88, "Duelist", 2);         addClass(89, "Dreadnought", 3);
        addClass(90, "Phoenix Knight", 5);  addClass(91, "Hell Knight", 6);
        addClass(92, "Sagittarius", 9);     addClass(93, "Adventurer", 8);
        addClass(94, "Archmage", 12);       addClass(95, "Soultaker", 13);
        addClass(96, "Arcana Lord", 14);    addClass(97, "Cardinal", 16); addClass(98, "Hierophant", 17);
        // Elven 3rd
        addClass(99, "Eva's Templar", 20);  addClass(100, "Sword Muse", 21);
        addClass(101, "Wind Rider", 23);    addClass(102, "Moonlight Sentinel", 24);
        addClass(103, "Mystic Muse", 27);   addClass(104, "Elemental Master", 26);
        addClass(105, "Eva's Saint", 30);
        // Dark Elven 3rd
        addClass(106, "Shillien Templar", 33); addClass(107, "Spectral Dancer", 34);
        addClass(108, "Ghost Hunter", 36);  addClass(109, "Ghost Sentinel", 37);
        addClass(110, "Storm Screamer", 40); addClass(111, "Spectral Master", 41);
        addClass(112, "Shillien Saint", 43);
        // Orc 3rd
        addClass(113, "Titan", 46);         addClass(114, "Grand Khavatari", 48);
        addClass(115, "Dominator", 51);     addClass(116, "Doomcryer", 52);
        // Dwarf 3rd
        addClass(117, "Fortune Seeker", 55); addClass(118, "Maestro", 57);
    }

    // -------------------------------------------------------------------------
    // Quest-node tables (quest scripts + spawns/*.xml)
    // -------------------------------------------------------------------------

    private static final Map<Integer, QuestNode> QUESTS = new LinkedHashMap<>();
    private static final Map<Integer, QuestNode> FIRST_CLASS_CHANGE = new LinkedHashMap<>();
    private static final Map<Integer, List<QuestNode>> SECOND_CLASS_CHANGE = new LinkedHashMap<>();
    private static final Map<Integer, QuestNode> THIRD_CLASS_CHANGE = new LinkedHashMap<>();
    private static final Map<Integer, QuestNode> SECOND_CLASS_POOL = new LinkedHashMap<>();
    private static final Map<PlayerRace, List<QuestNode>> NEWBIE = new EnumMap<>(PlayerRace.class);

    private static QuestNode q(int id, String name, QuestNode.Kind kind, boolean core, int npc, String npcName, int x, int y, int z, String town, String zone, int lvl)
    {
        return new QuestNode(id, name, kind, core, npc, npcName, x, y, z, town, zone, lvl);
    }

    private static void regFirst(int profClassId, QuestNode n)
    {
        QUESTS.put(n.questId, n);
        FIRST_CLASS_CHANGE.put(profClassId, n);
    }

    private static void regSecondPool(QuestNode n)
    {
        QUESTS.put(n.questId, n);
        SECOND_CLASS_POOL.put(n.questId, n);
    }

    private static void addSecond(int profClassId, int questId)
    {
        QuestNode n = SECOND_CLASS_POOL.get(questId);
        if (n != null)
            SECOND_CLASS_CHANGE.computeIfAbsent(profClassId, k -> new ArrayList<>()).add(n);
    }

    private static void regThird(int thirdClassId, QuestNode n)
    {
        QUESTS.put(n.questId, n);
        THIRD_CLASS_CHANGE.put(thirdClassId, n);
    }

    private static void regNewbie(PlayerRace race, QuestNode n)
    {
        QUESTS.put(n.questId, n);
        NEWBIE.computeIfAbsent(race, k -> new ArrayList<>()).add(n);
    }

    private static void registerNewbieChain()
    {
        // Human - Talking Island
        regNewbie(PlayerRace.HUMAN, q(1, "Letters of Love", QuestNode.Kind.NEWBIE, true, 30048, "Darin", -84436, 242793, -3728, "Talking Island", "TalkingIsland", 5));
        regNewbie(PlayerRace.HUMAN, q(6, "Step into the Future", QuestNode.Kind.NEWBIE, true, 30006, "Roxxy", -84108, 244604, -3728, "Talking Island", "TalkingIsland", 10));
        // Elven - Elven Village
        regNewbie(PlayerRace.ELF, q(2, "What Women Want", QuestNode.Kind.NEWBIE, true, 30223, "Arujien", 42978, 49115, -2992, "Elven Village", "ElvenTerritory", 5));
        regNewbie(PlayerRace.ELF, q(7, "A Trip Begins", QuestNode.Kind.NEWBIE, true, 30146, "Mirabel", 46926, 51511, -2976, "Elven Village", "ElvenTerritory", 10));
        // Dark Elf - Dark Elven Village
        regNewbie(PlayerRace.DARK_ELF, q(3, "Will the Seal be Broken", QuestNode.Kind.NEWBIE, true, 30141, "Talloth", 11012, 14128, -4240, "Dark Elven Village", "DarkElfTerritory", 5));
        regNewbie(PlayerRace.DARK_ELF, q(8, "An Adventure Begins", QuestNode.Kind.NEWBIE, true, 30134, "Jasmine", 9670, 15537, -4568, "Dark Elven Village", "DarkElfTerritory", 10));
        // Orc - Orc Village
        regNewbie(PlayerRace.ORC, q(4, "Long live the Pa'agrio Lord", QuestNode.Kind.NEWBIE, true, 30578, "Wings of Flame", -47360, -113791, -224, "Orc Village", "OrcTerritory", 5));
        regNewbie(PlayerRace.ORC, q(9, "Into the City of Humans", QuestNode.Kind.NEWBIE, true, 30583, "Petukai", -45731, -113844, -240, "Orc Village", "OrcTerritory", 10));
        // Dwarf - Dwarven Village
        regNewbie(PlayerRace.DWARF, q(5, "Miner's Favor", QuestNode.Kind.NEWBIE, true, 30554, "Bolter", 112656, -174864, -608, "Dwarven Village", "DwarvenTerritory", 5));
        regNewbie(PlayerRace.DWARF, q(10, "Into the World", QuestNode.Kind.NEWBIE, true, 30533, "Balanki", 116016, -178615, -944, "Dwarven Village", "DwarvenTerritory", 10));
    }

    private static void registerFirstClassChange()
    {
        // "Path of the ..." quests Q401-418, NPC coords from spawns/*.xml
        regFirst(1,  q(401, "Path to a Warrior", QuestNode.Kind.CLASS_CHANGE_1, true, 30010, "Auron", -81861, 149197, -3125, "Gludin Village", "Gludin", 19));
        regFirst(4,  q(402, "Path to a Knight", QuestNode.Kind.CLASS_CHANGE_1, true, 30417, "Klaus Vasper", -82437, 150282, -3129, "Gludin Village", "Gludin", 19));
        regFirst(7,  q(403, "Path to a Rogue", QuestNode.Kind.CLASS_CHANGE_1, true, 30379, "Bezique", -85019, 152887, -3178, "Gludin Village", "Gludin", 19));
        regFirst(11, q(404, "Path to a Wizard", QuestNode.Kind.CLASS_CHANGE_1, true, 30391, "Parina", -80430, 150694, -3040, "Gludin Village", "Gludin", 19));
        regFirst(15, q(405, "Path to a Cleric", QuestNode.Kind.CLASS_CHANGE_1, true, 30022, "Zigaunt", -79256, 150676, -3043, "Gludin Village", "Gludin", 19));
        regFirst(19, q(406, "Path to an Elven Knight", QuestNode.Kind.CLASS_CHANGE_1, true, 30327, "Sorius", -13440, 122643, -3103, "Gludio Castle Town", "Gludio", 19));
        regFirst(22, q(407, "Path to an Elven Scout", QuestNode.Kind.CLASS_CHANGE_1, true, 30328, "Reisa", -13693, 122583, -3103, "Gludio Castle Town", "Gludio", 19));
        regFirst(26, q(408, "Path to an Elven Wizard", QuestNode.Kind.CLASS_CHANGE_1, true, 30414, "Rosella", 43673, 49683, -3048, "Elven Village", "ElvenTerritory", 19));
        regFirst(29, q(409, "Path to an Oracle", QuestNode.Kind.CLASS_CHANGE_1, true, 30293, "Manuel", -13487, 121541, -2966, "Gludio Castle Town", "Gludio", 19));
        regFirst(32, q(410, "Path to a Palus Knight", QuestNode.Kind.CLASS_CHANGE_1, true, 30329, "Virgil", -12791, 123259, -3102, "Gludio Castle Town", "Gludio", 19));
        regFirst(35, q(411, "Path to an Assassin", QuestNode.Kind.CLASS_CHANGE_1, true, 30416, "Triskel", -12506, 123405, -3111, "Gludio Castle Town", "Gludio", 19));
        regFirst(39, q(412, "Path to a Dark Wizard", QuestNode.Kind.CLASS_CHANGE_1, true, 30421, "Varika", -44225, 79721, -3648, "Dark Elven Village", "DarkElfTerritory", 19));
        regFirst(42, q(413, "Path to a Shillien Oracle", QuestNode.Kind.CLASS_CHANGE_1, true, 30330, "Sidra", -12503, 123054, -3102, "Gludio Castle Town", "Gludio", 19));
        regFirst(45, q(414, "Path to an Orc Raider", QuestNode.Kind.CLASS_CHANGE_1, true, 30570, "Karukia", -45952, -114496, -200, "Orc Village", "OrcTerritory", 19));
        regFirst(47, q(415, "Path to a Monk", QuestNode.Kind.CLASS_CHANGE_1, true, 30587, "Gantaki", -44624, -111873, -240, "Orc Village", "OrcTerritory", 19));
        regFirst(50, q(416, "Path to an Orc Shaman", QuestNode.Kind.CLASS_CHANGE_1, true, 30585, "Tataru", -45328, -114736, -240, "Orc Village", "OrcTerritory", 19));
        regFirst(54, q(417, "Path to a Scavenger", QuestNode.Kind.CLASS_CHANGE_1, true, 30524, "Pippi", 115618, -183265, -1472, "Dwarven Village", "DwarvenTerritory", 19));
        regFirst(56, q(418, "Path to an Artisan", QuestNode.Kind.CLASS_CHANGE_1, true, 30527, "Silvera", 115271, -182692, -1440, "Dwarven Village", "DwarvenTerritory", 19));
    }

    private static void registerSecondClassChange()
    {
        // Trial / Testimony quests Q211-235, NPC coords from spawns/*.xml.
        // Per-profession NPC lists come from each quest script's class lists. Quest ids with an
        // EMPTY class list (217-222, 234, 235) are branch/state-machine quests and are kept in the
        // pool but not force-attached here (see Documentation on 2nd-class branching).
        regSecondPool(q(211, "Trial of the Challenger", QuestNode.Kind.CLASS_CHANGE_2, true, 30644, "Kashi", 19831, 144514, -3098, "Dion Castle Town", "Dion", 35));
        regSecondPool(q(212, "Trial of the Duty",     QuestNode.Kind.CLASS_CHANGE_2, true, 30109, "Hannavalt", 85655, 146471, -3399, "Giran Castle Town", "Giran", 35));
        regSecondPool(q(213, "Trial of the Seeker",   QuestNode.Kind.CLASS_CHANGE_2, true, 30106, "Dufner", 85234, 146634, -3399, "Giran Castle Town", "Giran", 35));
        regSecondPool(q(214, "Trial of the Scholar",  QuestNode.Kind.CLASS_CHANGE_2, true, 30461, "Mirien", 16427, 142131, -2688, "Dion Castle Town", "Dion", 35));
        regSecondPool(q(215, "Trial of the Pilgrim",  QuestNode.Kind.CLASS_CHANGE_2, true, 30648, "Santiago", -85002, 105749, -3593, "Gludin Village", "Gludin", 35));
        regSecondPool(q(216, "Trial of the Guildsman", QuestNode.Kind.CLASS_CHANGE_2, true, 30103, "Valkon", 83264, 146602, -3464, "Giran Castle Town", "Giran", 35));
        regSecondPool(q(217, "Testimony of Trust",    QuestNode.Kind.CLASS_CHANGE_2, true, 30191, "Cronos", 83634, 52927, -1443, "Town of Oren", "Oren", 38));
        regSecondPool(q(218, "Testimony of Life",     QuestNode.Kind.CLASS_CHANGE_2, true, 30460, "Cardien", 19472, 144334, -3088, "Dion Castle Town", "Dion", 38));
        regSecondPool(q(219, "Testimony of Fate",     QuestNode.Kind.CLASS_CHANGE_2, true, 30476, "Kairia", 78928, 149041, -3596, "Giran Castle Town", "Giran", 38));
        regSecondPool(q(220, "Testimony of Glory",    QuestNode.Kind.CLASS_CHANGE_2, true, 30514, "Vokian", 80464, 144368, -3533, "Giran Castle Town", "Giran", 38));
        regSecondPool(q(221, "Testimony of Prosperity", QuestNode.Kind.CLASS_CHANGE_2, true, 30104, "Parman", 83263, 146736, -3464, "Giran Castle Town", "Giran", 38));
        regSecondPool(q(222, "Test of the Duelist",   QuestNode.Kind.CLASS_CHANGE_2, true, 30623, "KaiEn", 79610, 56717, -1510, "Town of Oren", "Oren", 36));
        regSecondPool(q(223, "Test of the Champion",  QuestNode.Kind.CLASS_CHANGE_2, true, 30624, "Ascalon", 79253, 147049, -3529, "Giran Castle Town", "Giran", 36));
        regSecondPool(q(224, "Test of the Searcher",  QuestNode.Kind.CLASS_CHANGE_2, true, 30702, "Bernard", 117598, 77264, -2585, "Hunters Village", "Hunters", 38));
        regSecondPool(q(225, "Test of the Reformer",  QuestNode.Kind.CLASS_CHANGE_2, true, 30690, "Luther", 119472, 77835, -2243, "Hunters Village", "Hunters", 38));
        regSecondPool(q(226, "Test of the Healer",    QuestNode.Kind.CLASS_CHANGE_2, true, 30473, "Bandellos", 85829, 148365, -3392, "Giran Castle Town", "Giran", 38));
        regSecondPool(q(227, "Test of the Magus",     QuestNode.Kind.CLASS_CHANGE_2, true, 30118, "Pupina", 86485, 148384, -3392, "Giran Castle Town", "Giran", 36));
        regSecondPool(q(228, "Test of the Summoner",  QuestNode.Kind.CLASS_CHANGE_2, true, 30629, "Rukal", 16680, 142368, -2688, "Dion Castle Town", "Dion", 38));
        regSecondPool(q(229, "Test of Witchcraft",    QuestNode.Kind.CLASS_CHANGE_2, true, 30630, "Orim", 69976, 109692, -3702, "Giran Castle Town", "Giran", 36));
        regSecondPool(q(230, "Test of the Maestro",   QuestNode.Kind.CLASS_CHANGE_2, true, 30634, "Gallint", -81199, 151904, -3118, "Gludin Village", "Gludin", 38));
        regSecondPool(q(231, "Test of the Auctioner", QuestNode.Kind.CLASS_CHANGE_2, true, 30531, "Vagobond", 116226, -178529, -944, "Dwarven Village", "DwarvenTerritory", 36));
        regSecondPool(q(232, "Test of the Father",    QuestNode.Kind.CLASS_CHANGE_2, true, 30565, "Athena", -46768, -113610, -3, "Orc Village", "OrcTerritory", 36));
        regSecondPool(q(233, "Test of the Mother",    QuestNode.Kind.CLASS_CHANGE_2, true, 30510, "Vlasty", 19924, 143196, -3040, "Dion Castle Town", "Dion", 38));
        regSecondPool(q(234, "Testimony of Blood",    QuestNode.Kind.CLASS_CHANGE_2, true, 31002, "Dying Soldier", 92743, 55523, -3370, "Town of Oren", "Oren", 38));
        regSecondPool(q(235, "Testimony of War",      QuestNode.Kind.CLASS_CHANGE_2, true, 30721, "Dying Soldier", 84985, 15991, -1769, "Town of Oren", "Oren", 38));

        // Verified per-profession applicability (from quest script class lists)
        addSecond(1, 211);                                              // Warrior
        addSecond(19, 211); addSecond(19, 212); addSecond(19, 226);     // Elven Knight
        addSecond(47, 211);                                             // Orc Monk
        addSecond(45, 211); addSecond(45, 223);                         // Orc Raider
        addSecond(32, 211); addSecond(32, 212); addSecond(32, 229);     // Palus Knight
        addSecond(4, 212);  addSecond(4, 226);  addSecond(4, 229);      // Knight
        addSecond(35, 213); addSecond(35, 224); addSecond(35, 225);     // Assassin
        addSecond(22, 213); addSecond(22, 224); addSecond(22, 225);     // Elven Scout
        addSecond(7, 213);  addSecond(7, 224);  addSecond(7, 225);      // Rogue
        addSecond(39, 214); addSecond(39, 228); addSecond(39, 230);     // Dark Wizard
        addSecond(26, 214); addSecond(26, 228); addSecond(26, 230);     // Elven Wizard
        addSecond(11, 214); addSecond(11, 228); addSecond(11, 229); addSecond(11, 230); // Wizard
        addSecond(15, 215); addSecond(15, 226); addSecond(15, 227);     // Cleric
        addSecond(29, 215); addSecond(29, 226);                         // Oracle
        addSecond(50, 215); addSecond(50, 232); addSecond(50, 233);     // Orc Shaman
        addSecond(42, 215); addSecond(42, 227);                         // Shillien Oracle
        addSecond(56, 216); addSecond(56, 231);                         // Artisan
        addSecond(54, 216); addSecond(54, 225); addSecond(54, 231);     // Scavenger
    }

    private static void registerThirdClassChange()
    {
        // Saga quests Q70-100 for the 3rd profession. Sagas are multi-zone event quests launched
        // from the faction towns, so they carry no single start-NPC spawn; npcId/coords are left as
        // 0 and the AI assembles in the main town. Quest id per class is authoritative (sage id list).
        regThird(90, q(70, "Saga of the Phoenix Knight", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(99, q(71, "Saga of the Eva's Templar", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(100, q(72, "Saga of the Sword Muse", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(88, q(73, "Saga of the Duelist", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(89, q(74, "Saga of the Dreadnought", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(113, q(75, "Saga of the Titan", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(114, q(76, "Saga of the Grand Khavatari", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(115, q(77, "Saga of the Dominator", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(116, q(78, "Saga of the Doomcryer", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(93, q(79, "Saga of the Adventurer", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(101, q(80, "Saga of the Wind Rider", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(108, q(81, "Saga of the Ghost Hunter", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(92, q(82, "Saga of the Sagittarius", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(102, q(83, "Saga of the Moonlight Sentinel", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(109, q(84, "Saga of the Ghost Sentinel", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(97, q(85, "Saga of the Cardinal", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(98, q(86, "Saga of the Hierophant", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(105, q(87, "Saga of the Eva's Saint", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(94, q(88, "Saga of the Archmage", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(103, q(89, "Saga of the Mystic Muse", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(110, q(90, "Saga of the Storm Screamer", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(96, q(91, "Saga of the Arcana Lord", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(104, q(92, "Saga of the Elemental Master", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(111, q(93, "Saga of the Spectral Master", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(95, q(94, "Saga of the Soultaker", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(91, q(95, "Saga of the Hell Knight", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(107, q(96, "Saga of the Spectral Dancer", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(106, q(97, "Saga of the Shillien Templar", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(112, q(98, "Saga of the Shillien Saint", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(117, q(99, "Saga of the Fortune Seeker", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
        regThird(118, q(100, "Saga of the Maestro", QuestNode.Kind.CLASS_CHANGE_3, true, 0, "event", 0, 0, 0, "Town of Aden", "Saga", 76));
    }

    // -------------------------------------------------------------------------
    // Travel (teleport legs) + hunting zones
    // -------------------------------------------------------------------------

    private static final Map<String, TeleportLeg> TELEPORT_LEGS = new LinkedHashMap<>();

    private static void addLeg(String from, String to, int cost, int lvl, int x, int y, int z, String desc)
    {
        TELEPORT_LEGS.put(from + "|" + to, new TeleportLeg(from, to, cost, lvl, x, y, z, desc));
    }

    private static void registerTravel()
    {
        // Core land routes mirror TeleportManager (real gatekeeper prices, level 20).
        addLeg("Giran", "Aden", 9200, 20, 147450, 27030, -2208, "gatekeeper");
        addLeg("Giran", "Dion", 3400, 20, 15671, 142983, -2704, "gatekeeper");
        addLeg("Giran", "Gludio", 3700, 20, -14608, 123920, -3120, "gatekeeper");
        addLeg("Giran", "Oren", 5900, 20, 82956, 53162, -1496, "gatekeeper");
        addLeg("Giran", "Hunters Village", 4400, 20, 116819, 76966, -2714, "gatekeeper");
        addLeg("Aden", "Giran", 9200, 20, 83358, 147934, -3400, "gatekeeper");
        addLeg("Aden", "Dion", 7100, 20, 15671, 142983, -2704, "gatekeeper");
        addLeg("Aden", "Gludio", 7600, 20, -14608, 123920, -3120, "gatekeeper");
        addLeg("Aden", "Oren", 6300, 20, 82956, 53162, -1496, "gatekeeper");
        addLeg("Aden", "Hunters Village", 5600, 20, 116819, 76966, -2714, "gatekeeper");
        addLeg("Dion", "Giran", 3400, 20, 83358, 147934, -3400, "gatekeeper");
        addLeg("Dion", "Aden", 7100, 20, 147450, 27030, -2208, "gatekeeper");
        addLeg("Dion", "Gludio", 1800, 20, -14608, 123920, -3120, "gatekeeper");
        addLeg("Dion", "Oren", 4800, 20, 82956, 53162, -1496, "gatekeeper");
        addLeg("Gludio", "Giran", 3700, 20, 83358, 147934, -3400, "gatekeeper");
        addLeg("Gludio", "Aden", 7600, 20, 147450, 27030, -2208, "gatekeeper");
        addLeg("Gludio", "Dion", 1800, 20, 15671, 142983, -2704, "gatekeeper");
        addLeg("Gludio", "Oren", 5300, 20, 82956, 53167, -1496, "gatekeeper");
        addLeg("Oren", "Giran", 5900, 20, 83358, 147934, -3400, "gatekeeper");
        addLeg("Oren", "Aden", 6300, 20, 147450, 27030, -2208, "gatekeeper");
        addLeg("Oren", "Dion", 4800, 20, 15671, 142983, -2704, "gatekeeper");
        addLeg("Oren", "Gludio", 5300, 20, -14608, 123920, -3120, "gatekeeper");
        addLeg("Oren", "Hunters Village", 3400, 20, 116819, 76977, -2714, "gatekeeper");
        addLeg("Hunters Village", "Giran", 4400, 20, 83358, 147934, -3400, "gatekeeper");
        addLeg("Hunters Village", "Aden", 5600, 20, 147450, 27030, -2208, "gatekeeper");
        addLeg("Hunters Village", "Oren", 3400, 20, 82956, 53162, -1496, "gatekeeper");
        // Endgame hub (Aden <-> Goddard; classic Interlude, approx prices).
        addLeg("Aden", "Goddard", 15000, 30, 147728, -56548, -3075, "gatekeeper (approx)");
        addLeg("Goddard", "Aden", 15000, 30, 147450, 27030, -2208, "gatekeeper (approx)");
        // Home-village exits (classic Interlude; costs approximate - verify against server GK).
        addLeg("Talking Island", "Gludin", 0, 1, -80417, 151635, -3150, "boat (free)");
        addLeg("Elven Village", "Gludin", 8000, 1, -80417, 151635, -3150, "post-mission path (approx)");
        addLeg("Elven Village", "Talking Island", 0, 1, -84327, 242833, -3750, "boat (free)");
        addLeg("Dark Elven Village", "Gludio", 8000, 1, -14522, 123414, -3050, "gatekeeper (approx)");
        addLeg("Orc Village", "Gludin", 8000, 1, -80417, 151635, -3150, "gatekeeper (approx)");
        addLeg("Dwarven Village", "Gludin", 8000, 1, -80417, 151635, -3150, "gatekeeper (approx)");
        addLeg("Gludin", "Gludio", 3000, 1, -14522, 123414, -3050, "gatekeeper (approx)");
    }

    private static final List<HuntZone> HUNT_ZONES = new ArrayList<>();

    private static void addHunt(String name, int min, int max, int avg, int x, int y, int z, int r, String town)
    {
        HUNT_ZONES.add(new HuntZone(name, min, max, avg, x, y, z, r, town));
    }

    private static void registerZones()
    {
        // Mirrors com.aiplayer.phase0.quest.ZoneRecommender (real Interlude zone catalogue).
        // Town names are canonical and match the TeleportLeg keys so idleAnchor can resolve a
        // real gatekeeper anchor for every band.
        addHunt("Talking Island", 1, 10, 5, -99500, 237500, -3500, 15000, "Talking Island");
        addHunt("Elven Forest", 5, 15, 10, 10000, 50000, -3000, 20000, "Elven Village");
        addHunt("Dark Elven Swampland", 8, 18, 13, 20000, 10000, -3000, 18000, "Dark Elven Village");
        addHunt("Gludio Plains", 10, 20, 15, -60000, 140000, -3000, 25000, "Gludin");
        addHunt("Ruins of Agony", 18, 28, 23, -50000, 120000, -3000, 20000, "Gludin");
        addHunt("Ruins of Despair", 20, 30, 25, -45000, 115000, -3000, 18000, "Gludin");
        addHunt("Abandoned Camp", 25, 35, 30, -20000, 130000, -3000, 22000, "Gludin");
        addHunt("Ant Nest", 30, 40, 35, 30000, 160000, -3000, 15000, "Dion");
        addHunt("Execution Grounds", 35, 45, 40, 50000, 150000, -3000, 25000, "Dion");
        addHunt("Cruma Marshlands", 40, 50, 45, 80000, 180000, -3000, 30000, "Dion");
        addHunt("Cruma Tower", 40, 55, 48, 85000, 185000, -3000, 10000, "Dion");
        addHunt("Dragon Valley", 45, 55, 50, 120000, 110000, -3000, 35000, "Giran");
        addHunt("Forest of Outlaws", 50, 60, 55, 90000, 80000, -3000, 28000, "Giran");
        addHunt("Devastated Castle", 50, 62, 56, 95000, 75000, -3000, 20000, "Giran");
        addHunt("Timak Outpost", 55, 65, 60, 70000, 60000, -3000, 30000, "Oren");
        addHunt("Wall of Argos", 60, 70, 65, 150000, 50000, -3000, 35000, "Aden");
        addHunt("Blazing Swamp", 60, 72, 66, 140000, 40000, -3000, 25000, "Aden");
        addHunt("Varka Silenos", 65, 76, 70, 100000, -100000, -3000, 40000, "Goddard");
        addHunt("Ketra Orc", 65, 76, 70, 120000, -120000, -3000, 40000, "Goddard");
        addHunt("Imperial Tomb", 70, 80, 74, 50000, -50000, -3000, 35000, "Goddard");
        addHunt("Monastery of Silence", 70, 80, 75, 60000, -60000, -3000, 30000, "Goddard");
        addHunt("Stakato Nest", 72, 80, 76, 80000, -80000, -3000, 25000, "Goddard");
    }

    static
    {
        registerClassTree();
        registerNewbieChain();
        registerFirstClassChange();
        registerSecondClassChange();
        registerThirdClassChange();
        registerTravel();
        registerZones();
    }

    // -------------------------------------------------------------------------
    // Public lookup API
    // -------------------------------------------------------------------------

    /** Look up a profession by server class id. */
    public static Optional<ProfessionNode> profession(int classId)
    {
        return Optional.ofNullable(PROFESSIONS.get(classId));
    }

    /** The whole profession tree of a race, ordered by class id. */
    public static List<ProfessionNode> professionTree(PlayerRace race)
    {
        List<ProfessionNode> out = new ArrayList<>();
        for (ProfessionNode p : PROFESSIONS.values())
            if (p.race == race)
                out.add(p);
        return out;
    }

    /** The race newbie-helper (Q999 tutorial), a real NPC spawn. */
    public static QuestNode tutorial(PlayerRace race)
    {
        return new QuestNode(999, "Into the World - Newbie Guide", QuestNode.Kind.NEWBIE_TUTORIAL, true,
            race.helperNpcId(), race.helperNpcName(), race.helperX(), race.helperY(), race.helperZ(),
            race.startTown(), race.startZone(), 1);
    }

    /** The level 5-20 village quest chain of the race (Q1/Q6, Q2/Q7, ...). */
    public static List<QuestNode> newbieChain(PlayerRace race)
    {
        List<QuestNode> chain = new ArrayList<>();
        chain.add(tutorial(race));
        List<QuestNode> n = NEWBIE.get(race);
        if (n != null)
            chain.addAll(n);
        return chain;
    }

    /** The 1st class change ("Path of the ...") quest for a first profession class id. */
    public static Optional<QuestNode> firstClassChangeQuest(int professionClassId)
    {
        return Optional.ofNullable(FIRST_CLASS_CHANGE.get(professionClassId));
    }

    /** The 2nd-class-change trials applicable to a first profession (verified class lists). */
    public static List<QuestNode> secondClassChangeQuests(int professionClassId)
    {
        List<QuestNode> list = SECOND_CLASS_CHANGE.get(professionClassId);
        return list == null ? java.util.Collections.emptyList() : java.util.Collections.unmodifiableList(list);
    }

    /** All second-class-change quests incl. branch/Testimony quests (state-machine driven). */
    public static List<QuestNode> allSecondClassQuests()
    {
        return new ArrayList<>(SECOND_CLASS_POOL.values());
    }

    /** The 3rd profession (Saga) quest for a third class id. */
    public static Optional<QuestNode> thirdClassChangeQuest(int thirdClassId)
    {
        return Optional.ofNullable(THIRD_CLASS_CHANGE.get(thirdClassId));
    }

    /** Direct teleport leg between two towns (may be absent for non-adjacent towns). */
    public static Optional<TeleportLeg> teleportLeg(String fromTown, String toTown)
    {
        return Optional.ofNullable(TELEPORT_LEGS.get(fromTown + "|" + toTown));
    }

    /** BFS path through the gatekeeper/boat network from one town to another. */
    public static List<TeleportLeg> route(String fromTown, String toTown)
    {
        if (fromTown.equals(toTown))
            return java.util.Collections.emptyList();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        Map<String, String> cameFrom = new java.util.HashMap<>();
        queue.add(fromTown);
        cameFrom.put(fromTown, null);
        while (!queue.isEmpty())
        {
            String cur = queue.poll();
            for (String key : TELEPORT_LEGS.keySet())
            {
                String[] parts = key.split("\\|");
                String next = null;
                if (parts[0].equals(cur))
                    next = parts[1];
                else if (parts[1].equals(cur))
                    next = parts[0];
                if (next == null || cameFrom.containsKey(next))
                    continue;
                cameFrom.put(next, cur);
                if (next.equals(toTown))
                {
                    java.util.LinkedList<String> towns = new java.util.LinkedList<>();
                    String step = toTown;
                    while (step != null)
                    {
                        towns.addFirst(step);
                        step = cameFrom.get(step);
                    }
                    List<TeleportLeg> legs = new ArrayList<>();
                    for (int i = 0; i < towns.size() - 1; i++)
                    {
                        TeleportLeg leg = TELEPORT_LEGS.get(towns.get(i) + "|" + towns.get(i + 1));
                        if (leg == null)
                            leg = TELEPORT_LEGS.get(towns.get(i + 1) + "|" + towns.get(i));
                        if (leg != null)
                            legs.add(leg);
                    }
                    return legs;
                }
                queue.add(next);
            }
        }
        return java.util.Collections.emptyList();
    }

    /** Hunting zones overlapping the given level band (highest average first). */
    public static List<HuntZone> huntZones(int minLevel, int maxLevel)
    {
        List<HuntZone> out = new ArrayList<>();
        for (HuntZone z : HUNT_ZONES)
            if (z.inBand(minLevel) || z.inBand(maxLevel) || (z.maxLevel >= minLevel && z.minLevel <= maxLevel))
                out.add(z);
        out.sort((a, b) -> Integer.compare(b.avgMobLevel, a.avgMobLevel));
        return out;
    }

    /**
     * The next quest goal for a bot of the given race/class/level.
     *
     * <p>Order: newbie+tutorial chain (while incomplete) &rarr; Path quest (base class, Lv19) &rarr;
     * 2nd-class trials of the current profession &rarr; (nothing; hunt/level and the AI's own state
     * machine picks the branch). The returned node always carries real world coordinates.
     */
    public static Optional<QuestNode> nextQuestGoal(PlayerRace race, int classId, int level, Set<Integer> completed)
    {
        ProfessionNode pn = PROFESSIONS.get(classId);
        if (pn == null)
            return Optional.empty();

        // Stage 1: newbie + tutorial chain of the race.
        for (QuestNode node : newbieChain(race))
            if (node.levelMin <= level && !completed.contains(node.questId))
                return Optional.of(node);

        // Stage 2: base class -> "Path of the ..." (1st change, Lv19).
        if (pn.tier == 0)
        {
            for (ProfessionNode child : PROFESSIONS.values())
                if (child.tier == 1 && child.parentClassId == classId)
                {
                    Optional<QuestNode> path = Optional.ofNullable(FIRST_CLASS_CHANGE.get(child.classId));
                    if (path.isPresent() && level >= path.get().levelMin && !completed.contains(path.get().questId))
                        return path;
                }
        }
        // Stage 3: 1st profession -> its applicable trials (2nd change).
        List<QuestNode> trials = pn.tier == 1 ? secondClassChangeQuests(classId)
                : (pn.tier == 2 ? secondClassChangeQuests(pn.parentClassId) : java.util.Collections.emptyList());
        for (QuestNode t : trials)
            if (level >= t.levelMin && !completed.contains(t.questId))
                return Optional.of(t);

        return Optional.empty();
    }

    /**
     * A real, safe idle/displacement landmark for a bot instead of void coordinates:
     * below Lv15 the race newbie helper, otherwise the gatekeeper anchor of the town serving
     * the best matching hunt zone, or the race home town. Always a real in-world coordinate.
     */
    public static QuestNode idleAnchor(PlayerRace race, int level)
    {
        if (level < 15)
            return tutorial(race);
        List<HuntZone> zones = huntZones(Math.max(1, level - 5), level + 5);
        if (!zones.isEmpty())
        {
            HuntZone z = zones.get(0);
            int[] coord = townCoord(z.nearestTown);
            if (coord != null)
                return new QuestNode(0, z.nearestTown + " landmark", QuestNode.Kind.STORY, true, 0,
                    "town", coord[0], coord[1], coord[2], z.nearestTown, z.name, Math.max(1, z.minLevel));
        }
        return new QuestNode(0, race.startTown() + " landmark", QuestNode.Kind.STORY, true, 0,
            "town", race.townX(), race.townY(), race.townZ(), race.startTown(), race.startZone(), 1);
    }

    private static int[] townCoord(String town)
    {
        for (TeleportLeg leg : TELEPORT_LEGS.values())
            if (leg.toTown.equals(town))
                return new int[] { leg.x, leg.y, leg.z };
        return null;
    }
}