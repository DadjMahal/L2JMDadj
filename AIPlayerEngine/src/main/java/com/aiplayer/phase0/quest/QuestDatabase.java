package com.aiplayer.phase0.quest;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.quest.QuestInfo.QuestReward;
import com.aiplayer.phase0.quest.QuestInfo.QuestStep;
import com.aiplayer.phase0.quest.QuestInfo.QuestType;
import com.aiplayer.phase0.quest.QuestInfo.StepType;

import java.util.*;

/**
 * Static registry of essential Lineage II Interlude quests.
 * Includes class-change chains, key leveling quests, and adena/item quests.
 *
 * Not every Interlude quest is listed — only those with high XP/min efficiency
 * or mandatory for progression (class changes). AI Players use this database
 * to plan their leveling route.
 *
 * Race bitmask: 1=Human, 2=Elf, 4=DarkElf, 8=Orc, 16=Dwarf, 32=Kamael
 * Class bitmask uses internal class IDs (0=any).
 */
public final class QuestDatabase {

    private static final Map<Integer, QuestInfo> BY_ID = new HashMap<>();
    private static final List<QuestInfo> ALL = new ArrayList<>();

    // Race masks
    public static final int RACE_HUMAN    = 1;
    public static final int RACE_ELF      = 2;
    public static final int RACE_DARK_ELF = 4;
    public static final int RACE_ORC      = 8;
    public static final int RACE_DWARF    = 16;
    public static final int RACE_KAMAEL   = 32;
    public static final int RACE_ALL      = 63;

    static {
        registerClassChangeQuests();
        registerLevelingQuests();
        registerItemAdenaQuests();
    }

    private QuestDatabase() {}

    // ================================================================
    // PUBLIC API
    // ================================================================

    public static QuestInfo getById(int questId) {
        return BY_ID.get(questId);
    }

    public static List<QuestInfo> getAll() {
        return Collections.unmodifiableList(ALL);
    }

    /**
     * Find quests available to this player, sorted by recommended level.
     */
    public static List<QuestInfo> findAvailable(int level, int raceId, int classId,
                                                 Set<Integer> completedQuests) {
        List<QuestInfo> result = new ArrayList<>();
        for (QuestInfo q : ALL) {
            if (q.isAvailable(level, raceId, classId, completedQuests)) {
                result.add(q);
            }
        }
        result.sort(Comparator.comparingInt(q -> q.recommendedLevel));
        return result;
    }

    /**
     * Find the best next quest for leveling (highest XP/min within level range).
     */
    public static List<QuestInfo> findBestLeveling(int level, int raceId, int classId,
                                                    Set<Integer> completedQuests, int topN) {
        List<QuestInfo> available = findAvailable(level, raceId, classId, completedQuests);
        available.sort((a, b) -> Double.compare(b.xpPerMinute(), a.xpPerMinute()));
        return available.subList(0, Math.min(topN, available.size()));
    }

    /**
     * Get class-change quest for current class ID.
     */
    public static QuestInfo getClassChangeQuest(int currentClassId) {
        // Map class IDs to their change quest IDs
        switch (currentClassId) {
            // Human fighters -> Warrior, Knight, Rogue
            case 0:  return BY_ID.get(10001); // Path of the Warrior (Human Fighter)
            case 1:  return BY_ID.get(10002); // Path of the Knight (Human Fighter)
            case 2:  return BY_ID.get(10003); // Path of the Rogue (Human Fighter)
            // Human mystics -> Wizard, Cleric
            case 10: return BY_ID.get(10004); // Path of the Wizard (Human Mystic)
            case 11: return BY_ID.get(10005); // Path of the Cleric (Human Mystic)
            // Elf fighters -> Knight, Scout
            case 18: return BY_ID.get(10006); // Path of the Knight (Elf Fighter)
            case 19: return BY_ID.get(10007); // Path of the Scout (Elf Fighter)
            // Elf mystics -> Wizard, Oracle
            case 25: return BY_ID.get(10008); // Path of the Wizard (Elf Mystic)
            case 26: return BY_ID.get(10009); // Path of the Oracle (Elf Mystic)
            // Dark Elf fighters -> Palus Knight, Assassin
            case 31: return BY_ID.get(10010); // Path of the Palus Knight (Dark Fighter)
            case 32: return BY_ID.get(10011); // Path of the Assassin (Dark Fighter)
            // Dark Elf mystics -> Dark Wizard, Shillien Oracle
            case 38: return BY_ID.get(10012); // Path of the Dark Wizard (Dark Mystic)
            case 39: return BY_ID.get(10013); // Path of the Shillien Oracle (Dark Mystic)
            // Orc fighters -> Raider, Monk
            case 44: return BY_ID.get(10014); // Path of the Raider (Orc Fighter)
            case 45: return BY_ID.get(10015); // Path of the Monk (Orc Fighter)
            // Orc mystics -> Orc Shaman
            case 49: return BY_ID.get(10016); // Path of the Orc Shaman (Orc Mystic)
            // Dwarf -> Scavenger, Artisan
            case 53: return BY_ID.get(10017); // Path of the Scavenger (Dwarf Fighter)
            case 54: return BY_ID.get(10018); // Path of the Artisan (Dwarf Fighter)
            // 2nd class changes (level 35-39)
            case 3:  return BY_ID.get(20001); // Trial of the Gladiator (Warrior)
            case 4:  return BY_ID.get(20002); // Trial of the Warlord (Warrior)
            case 5:  return BY_ID.get(20003); // Trial of the Paladin (Knight)
            case 6:  return BY_ID.get(20004); // Trial of the Dark Avenger (Knight)
            case 7:  return BY_ID.get(20005); // Trial of the Treasure Hunter (Rogue)
            case 8:  return BY_ID.get(20006); // Trial of the Hawkeye (Rogue)
            case 12: return BY_ID.get(20007); // Trial of the Sorcerer (Wizard)
            case 13: return BY_ID.get(20008); // Trial of the Necromancer (Wizard)
            case 14: return BY_ID.get(20009); // Trial of the Warlock (Wizard)
            case 15: return BY_ID.get(20010); // Trial of the Bishop (Cleric)
            case 16: return BY_ID.get(20011); // Trial of the Prophet (Cleric)
            default: return null;
        }
    }

    // ================================================================
    // QUEST REGISTRATION
    // ================================================================

    private static void register(QuestInfo quest) {
        BY_ID.put(quest.questId, quest);
        ALL.add(quest);
    }

    // ------------------------------------------------------------------
    // 1ST CLASS CHANGE QUESTS (Level 19-20)
    // ------------------------------------------------------------------

    private static void registerClassChangeQuests() {
        // Human Fighter -> Warrior
        register(new QuestInfo(10001, "Path of the Warrior", QuestType.CLASS_CHANGE,
            19, 25, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Grand Master Ramos in Gludin Village"),
                new QuestStep(StepType.KILL, 20006, 10, "Gludio", -60000, 140000, -3000, "Kill Poison Spiders around Gludin"),
                new QuestStep(StepType.KILL, 20038, 5, "Gludio", -60000, 140000, -3000, "Kill Giant Poison Spiders"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Ramos")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Ramos", 30002, "Gludio", 20, false, 25));

        // Human Fighter -> Knight
        register(new QuestInfo(10002, "Path of the Knight", QuestType.CLASS_CHANGE,
            19, 25, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30003, 1, "Gludio", -14440, 121064, -3000, "Talk to Grand Master Rains in Gludin"),
                new QuestStep(StepType.KILL, 20008, 12, "Gludio", -60000, 140000, -3000, "Kill Orcs near Gludin"),
                new QuestStep(StepType.RETURN, 30003, 1, "Gludio", -14440, 121064, -3000, "Return to Rains")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rains", 30003, "Gludio", 20, false, 20));

        // Human Fighter -> Rogue
        register(new QuestInfo(10003, "Path of the Rogue", QuestType.CLASS_CHANGE,
            19, 25, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30004, 1, "Gludio", -14440, 121064, -3000, "Talk to Grand Master Rupio"),
                new QuestStep(StepType.KILL, 20009, 15, "Gludio", -60000, 140000, -3000, "Kill Ol Mahum raiders"),
                new QuestStep(StepType.RETURN, 30004, 1, "Gludio", -14440, 121064, -3000, "Return to Rupio")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rupio", 30004, "Gludio", 20, false, 20));

        // Human Mystic -> Wizard
        register(new QuestInfo(10004, "Path of the Wizard", QuestType.CLASS_CHANGE,
            19, 25, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30005, 1, "Gludio", -14440, 121064, -3000, "Talk to Grand Master Valleria"),
                new QuestStep(StepType.KILL, 20010, 8, "Gludio", -60000, 140000, -3000, "Kill Skeletons"),
                new QuestStep(StepType.RETURN, 30005, 1, "Gludio", -14440, 121064, -3000, "Return to Valleria")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Valleria", 30005, "Gludio", 20, false, 18));

        // Human Mystic -> Cleric
        register(new QuestInfo(10005, "Path of the Cleric", QuestType.CLASS_CHANGE,
            19, 25, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30006, 1, "Gludio", -14440, 121064, -3000, "Talk to Grand Master Magister"),
                new QuestStep(StepType.KILL, 20011, 8, "Gludio", -60000, 140000, -3000, "Kill Zombies"),
                new QuestStep(StepType.RETURN, 30006, 1, "Gludio", -14440, 121064, -3000, "Return to Magister")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Magister", 30006, "Gludio", 20, false, 18));

        // Elf Fighter -> Knight (Elf Knight)
        register(new QuestInfo(10006, "Path of the Knight", QuestType.CLASS_CHANGE,
            19, 25, RACE_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30109, 1, "Elven Village", 46936, 51520, -3000, "Talk to Grand Master Siria"),
                new QuestStep(StepType.KILL, 20012, 10, "Elven Forest", 10000, 50000, -3000, "Kill Lireins in Elven Forest"),
                new QuestStep(StepType.RETURN, 30109, 1, "Elven Village", 46936, 51520, -3000, "Return to Siria")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Siria", 30109, "Elven Village", 20, false, 20));

        // Elf Fighter -> Scout
        register(new QuestInfo(10007, "Path of the Scout", QuestType.CLASS_CHANGE,
            19, 25, RACE_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30109, 1, "Elven Village", 46936, 51520, -3000, "Talk to Grand Master Siria"),
                new QuestStep(StepType.KILL, 20013, 12, "Elven Forest", 10000, 50000, -3000, "Kill Skeletons in Elven Forest"),
                new QuestStep(StepType.RETURN, 30109, 1, "Elven Village", 46936, 51520, -3000, "Return to Siria")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Siria", 30109, "Elven Village", 20, false, 20));

        // Elf Mystic -> Wizard
        register(new QuestInfo(10008, "Path of the Wizard", QuestType.CLASS_CHANGE,
            19, 25, RACE_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30110, 1, "Elven Village", 46936, 51520, -3000, "Talk to Magister Greenis"),
                new QuestStep(StepType.KILL, 20014, 8, "Elven Forest", 10000, 50000, -3000, "Kill Dryads"),
                new QuestStep(StepType.RETURN, 30110, 1, "Elven Village", 46936, 51520, -3000, "Return to Greenis")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Greenis", 30110, "Elven Village", 20, false, 18));

        // Elf Mystic -> Oracle
        register(new QuestInfo(10009, "Path of the Oracle", QuestType.CLASS_CHANGE,
            19, 25, RACE_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30110, 1, "Elven Village", 46936, 51520, -3000, "Talk to Magister Greenis"),
                new QuestStep(StepType.KILL, 20015, 8, "Elven Forest", 10000, 50000, -3000, "Kill Lireins"),
                new QuestStep(StepType.RETURN, 30110, 1, "Elven Village", 46936, 51520, -3000, "Return to Greenis")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Greenis", 30110, "Elven Village", 20, false, 18));

        // Dark Elf Fighter -> Palus Knight
        register(new QuestInfo(10010, "Path of the Palus Knight", QuestType.CLASS_CHANGE,
            19, 25, RACE_DARK_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30131, 1, "Dark Elven Village", 28232, 11056, -4000, "Talk to Grand Master Xenos"),
                new QuestStep(StepType.KILL, 20016, 10, "Swampland", 20000, 10000, -3000, "Kill Marsh Stakatos"),
                new QuestStep(StepType.RETURN, 30131, 1, "Dark Elven Village", 28232, 11056, -4000, "Return to Xenos")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Xenos", 30131, "Dark Elven Village", 20, false, 20));

        // Dark Elf Fighter -> Assassin
        register(new QuestInfo(10011, "Path of the Assassin", QuestType.CLASS_CHANGE,
            19, 25, RACE_DARK_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30131, 1, "Dark Elven Village", 28232, 11056, -4000, "Talk to Grand Master Xenos"),
                new QuestStep(StepType.KILL, 20017, 12, "Swampland", 20000, 10000, -3000, "Kill Lesser Dark Horrors"),
                new QuestStep(StepType.RETURN, 30131, 1, "Dark Elven Village", 28232, 11056, -4000, "Return to Xenos")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Xenos", 30131, "Dark Elven Village", 20, false, 20));

        // Dark Elf Mystic -> Dark Wizard
        register(new QuestInfo(10012, "Path of the Dark Wizard", QuestType.CLASS_CHANGE,
            19, 25, RACE_DARK_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30132, 1, "Dark Elven Village", 28232, 11056, -4000, "Talk to Magister Vlasty"),
                new QuestStep(StepType.KILL, 20018, 8, "Swampland", 20000, 10000, -3000, "Kill Marsh Stakatos"),
                new QuestStep(StepType.RETURN, 30132, 1, "Dark Elven Village", 28232, 11056, -4000, "Return to Vlasty")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Vlasty", 30132, "Dark Elven Village", 20, false, 18));

        // Dark Elf Mystic -> Shillien Oracle
        register(new QuestInfo(10013, "Path of the Shillien Oracle", QuestType.CLASS_CHANGE,
            19, 25, RACE_DARK_ELF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30132, 1, "Dark Elven Village", 28232, 11056, -4000, "Talk to Magister Vlasty"),
                new QuestStep(StepType.KILL, 20019, 8, "Swampland", 20000, 10000, -3000, "Kill Dark Horrors"),
                new QuestStep(StepType.RETURN, 30132, 1, "Dark Elven Village", 28232, 11056, -4000, "Return to Vlasty")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Vlasty", 30132, "Dark Elven Village", 20, false, 18));

        // Orc Fighter -> Raider
        register(new QuestInfo(10014, "Path of the Raider", QuestType.CLASS_CHANGE,
            19, 25, RACE_ORC, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30500, 1, "Orc Village", -45000, -113600, -200, "Talk to Grand Master Somak"),
                new QuestStep(StepType.KILL, 20020, 10, "Orc Barracks", -50000, -120000, -200, "Kill Goblins"),
                new QuestStep(StepType.RETURN, 30500, 1, "Orc Village", -45000, -113600, -200, "Return to Somak")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Somak", 30500, "Orc Village", 20, false, 20));

        // Orc Fighter -> Monk
        register(new QuestInfo(10015, "Path of the Monk", QuestType.CLASS_CHANGE,
            19, 25, RACE_ORC, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30500, 1, "Orc Village", -45000, -113600, -200, "Talk to Grand Master Somak"),
                new QuestStep(StepType.KILL, 20021, 10, "Orc Barracks", -50000, -120000, -200, "Kill Werewolves"),
                new QuestStep(StepType.RETURN, 30500, 1, "Orc Village", -45000, -113600, -200, "Return to Somak")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Somak", 30500, "Orc Village", 20, false, 20));

        // Orc Mystic -> Orc Shaman
        register(new QuestInfo(10016, "Path of the Orc Shaman", QuestType.CLASS_CHANGE,
            19, 25, RACE_ORC, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30501, 1, "Orc Village", -45000, -113600, -200, "Talk to High Prefect Tushku"),
                new QuestStep(StepType.KILL, 20022, 8, "Orc Barracks", -50000, -120000, -200, "Kill Stone Golems"),
                new QuestStep(StepType.RETURN, 30501, 1, "Orc Village", -45000, -113600, -200, "Return to Tushku")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Tushku", 30501, "Orc Village", 20, false, 18));

        // Dwarf -> Scavenger
        register(new QuestInfo(10017, "Path of the Scavenger", QuestType.CLASS_CHANGE,
            19, 25, RACE_DWARF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30502, 1, "Dwarven Village", 116472, -182673, -1500, "Talk to Warehouse Chief Rikyi"),
                new QuestStep(StepType.KILL, 20023, 10, "Dwarven Mines", 110000, -180000, -1500, "Kill Bugear Bears"),
                new QuestStep(StepType.RETURN, 30502, 1, "Dwarven Village", 116472, -182673, -1500, "Return to Rikyi")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rikyi", 30502, "Dwarven Village", 20, false, 20));

        // Dwarf -> Artisan
        register(new QuestInfo(10018, "Path of the Artisan", QuestType.CLASS_CHANGE,
            19, 25, RACE_DWARF, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30503, 1, "Dwarven Village", 116472, -182673, -1500, "Talk to Head Blacksmith Tapoy"),
                new QuestStep(StepType.KILL, 20024, 10, "Dwarven Mines", 110000, -180000, -1500, "Kill Kobolds"),
                new QuestStep(StepType.RETURN, 30503, 1, "Dwarven Village", 116472, -182673, -1500, "Return to Tapoy")
            ),
            new QuestReward(80000, 16000, 0, Collections.emptyList(), Collections.emptyList()),
            "Tapoy", 30503, "Dwarven Village", 20, false, 20));
    }

    // ------------------------------------------------------------------
    // 2ND CLASS CHANGE QUESTS (Level 35-39) — abbreviated, key ones
    // ------------------------------------------------------------------

    private static void register2ndClassChanges() {
        // Human Warrior -> Gladiator
        register(new QuestInfo(20001, "Trial of the Gladiator", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Ramos"),
                new QuestStep(StepType.KILL, 20100, 20, "Execution Grounds", 50000, 150000, -3000, "Kill Execution Grounds mobs"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Ramos")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Ramos", 30002, "Execution Grounds", 37, false, 60));

        // Human Warrior -> Warlord
        register(new QuestInfo(20002, "Trial of the Warlord", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Ramos"),
                new QuestStep(StepType.KILL, 20101, 25, "Execution Grounds", 50000, 150000, -3000, "Kill Execution Grounds mobs"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Ramos")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Ramos", 30002, "Execution Grounds", 37, false, 65));

        // Human Knight -> Paladin
        register(new QuestInfo(20003, "Trial of the Paladin", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30003, 1, "Gludio", -14440, 121064, -3000, "Talk to Rains"),
                new QuestStep(StepType.KILL, 20102, 20, "Execution Grounds", 50000, 150000, -3000, "Kill undead at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30003, 1, "Gludio", -14440, 121064, -3000, "Return to Rains")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rains", 30003, "Execution Grounds", 37, false, 60));

        // Human Knight -> Dark Avenger
        register(new QuestInfo(20004, "Trial of the Dark Avenger", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30003, 1, "Gludio", -14440, 121064, -3000, "Talk to Rains"),
                new QuestStep(StepType.KILL, 20103, 20, "Execution Grounds", 50000, 150000, -3000, "Kill undead at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30003, 1, "Gludio", -14440, 121064, -3000, "Return to Rains")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rains", 30003, "Execution Grounds", 37, false, 60));

        // Human Rogue -> Treasure Hunter
        register(new QuestInfo(20005, "Trial of the Treasure Hunter", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30004, 1, "Gludio", -14440, 121064, -3000, "Talk to Rupio"),
                new QuestStep(StepType.KILL, 20104, 20, "Execution Grounds", 50000, 150000, -3000, "Kill mobs at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30004, 1, "Gludio", -14440, 121064, -3000, "Return to Rupio")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rupio", 30004, "Execution Grounds", 37, false, 60));

        // Human Rogue -> Hawkeye
        register(new QuestInfo(20006, "Trial of the Hawkeye", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30004, 1, "Gludio", -14440, 121064, -3000, "Talk to Rupio"),
                new QuestStep(StepType.KILL, 20105, 20, "Execution Grounds", 50000, 150000, -3000, "Kill mobs at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30004, 1, "Gludio", -14440, 121064, -3000, "Return to Rupio")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Rupio", 30004, "Execution Grounds", 37, false, 60));

        // Human Wizard -> Sorcerer
        register(new QuestInfo(20007, "Trial of the Sorcerer", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30005, 1, "Gludio", -14440, 121064, -3000, "Talk to Valleria"),
                new QuestStep(StepType.KILL, 20106, 20, "Execution Grounds", 50000, 150000, -3000, "Kill mobs at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30005, 1, "Gludio", -14440, 121064, -3000, "Return to Valleria")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Valleria", 30005, "Execution Grounds", 37, false, 60));

        // Human Wizard -> Necromancer
        register(new QuestInfo(20008, "Trial of the Necromancer", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30005, 1, "Gludio", -14440, 121064, -3000, "Talk to Valleria"),
                new QuestStep(StepType.KILL, 20107, 20, "Execution Grounds", 50000, 150000, -3000, "Kill undead at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30005, 1, "Gludio", -14440, 121064, -3000, "Return to Valleria")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Valleria", 30005, "Execution Grounds", 37, false, 60));

        // Human Wizard -> Warlock
        register(new QuestInfo(20009, "Trial of the Warlock", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30005, 1, "Gludio", -14440, 121064, -3000, "Talk to Valleria"),
                new QuestStep(StepType.KILL, 20108, 20, "Execution Grounds", 50000, 150000, -3000, "Kill mobs at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30005, 1, "Gludio", -14440, 121064, -3000, "Return to Valleria")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Valleria", 30005, "Execution Grounds", 37, false, 60));

        // Human Cleric -> Bishop
        register(new QuestInfo(20010, "Trial of the Bishop", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30006, 1, "Gludio", -14440, 121064, -3000, "Talk to Magister"),
                new QuestStep(StepType.KILL, 20109, 20, "Execution Grounds", 50000, 150000, -3000, "Kill undead at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30006, 1, "Gludio", -14440, 121064, -3000, "Return to Magister")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Magister", 30006, "Execution Grounds", 37, false, 60));

        // Human Cleric -> Prophet
        register(new QuestInfo(20011, "Trial of the Prophet", QuestType.CLASS_CHANGE,
            35, 39, RACE_HUMAN, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30006, 1, "Gludio", -14440, 121064, -3000, "Talk to Magister"),
                new QuestStep(StepType.KILL, 20110, 20, "Execution Grounds", 50000, 150000, -3000, "Kill mobs at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30006, 1, "Gludio", -14440, 121064, -3000, "Return to Magister")
            ),
            new QuestReward(600000, 120000, 0, Collections.emptyList(), Collections.emptyList()),
            "Magister", 30006, "Execution Grounds", 37, false, 60));
    }

    // ------------------------------------------------------------------
    // LEVELING QUESTS (High XP efficiency, various levels)
    // ------------------------------------------------------------------

    private static void registerLevelingQuests() {
        // Low level: Gludin area
        register(new QuestInfo(30001, "Wolf Hunt", QuestType.KILL,
            3, 8, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20120, 20, "Gludio", -60000, 140000, -3000, "Kill Wolves near Gludin"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(5000, 1000, 500, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Gludio", 5, true, 15));

        // Level 10-15: Elven Forest
        register(new QuestInfo(30002, "Lizardmen Hunt", QuestType.KILL,
            10, 16, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30109, 1, "Elven Village", 46936, 51520, -3000, "Talk to Sentinel"),
                new QuestStep(StepType.KILL, 20121, 25, "Elven Forest", 10000, 50000, -3000, "Kill Lizardmen"),
                new QuestStep(StepType.RETURN, 30109, 1, "Elven Village", 46936, 51520, -3000, "Return to Sentinel")
            ),
            new QuestReward(25000, 5000, 2000, Collections.emptyList(), Collections.emptyList()),
            "Sentinel", 30109, "Elven Forest", 12, true, 20));

        // Level 15-20: Dark Elf area
        register(new QuestInfo(30003, "Stakato Hunt", QuestType.KILL,
            15, 22, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30131, 1, "Dark Elven Village", 28232, 11056, -4000, "Talk to Tetrarch"),
                new QuestStep(StepType.KILL, 20122, 30, "Swampland", 20000, 10000, -3000, "Kill Marsh Stakatos"),
                new QuestStep(StepType.RETURN, 30131, 1, "Dark Elven Village", 28232, 11056, -4000, "Return to Tetrarch")
            ),
            new QuestReward(60000, 12000, 5000, Collections.emptyList(), Collections.emptyList()),
            "Tetrarch", 30131, "Swampland", 18, true, 25));

        // Level 20-25: Ruins of Agony / Despair
        register(new QuestInfo(30004, "Ruins of Agony Cleansing", QuestType.KILL,
            20, 28, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard Leikan"),
                new QuestStep(StepType.KILL, 20123, 40, "Ruins of Agony", -50000, 120000, -3000, "Kill undead in Ruins of Agony"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Leikan")
            ),
            new QuestReward(120000, 24000, 10000, Collections.emptyList(), Collections.emptyList()),
            "Leikan", 30002, "Ruins of Agony", 23, true, 35));

        // Level 25-30: Abandoned Camp
        register(new QuestInfo(30005, "Ol Mahum Suppression", QuestType.KILL,
            25, 32, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20124, 50, "Abandoned Camp", -20000, 130000, -3000, "Kill Ol Mahum at Abandoned Camp"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(200000, 40000, 15000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Abandoned Camp", 28, true, 40));

        // Level 30-35: Ant Nest
        register(new QuestInfo(30006, "Ant Nest Extermination", QuestType.KILL,
            30, 38, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20125, 60, "Ant Nest", 30000, 160000, -3000, "Kill Ants in Ant Nest"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(350000, 70000, 25000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Ant Nest", 33, true, 50));

        // Level 35-40: Execution Grounds
        register(new QuestInfo(30007, "Execution Grounds Patrol", QuestType.KILL,
            35, 42, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20126, 70, "Execution Grounds", 50000, 150000, -3000, "Kill undead at Execution Grounds"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(500000, 100000, 35000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Execution Grounds", 38, true, 55));

        // Level 40-45: Cruma Tower entrance
        register(new QuestInfo(30008, "Cruma Marshlands", QuestType.KILL,
            40, 48, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20127, 80, "Cruma Marshlands", 80000, 180000, -3000, "Kill marsh creatures"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(750000, 150000, 50000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Cruma Marshlands", 43, true, 60));

        // Level 45-50: Dragon Valley
        register(new QuestInfo(30009, "Dragon Valley Expedition", QuestType.KILL,
            45, 54, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20128, 90, "Dragon Valley", 120000, 110000, -3000, "Kill valley creatures"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(1000000, 200000, 75000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Dragon Valley", 48, true, 70));

        // Level 50-55: Forest of Outlaws
        register(new QuestInfo(30010, "Outlaw Purge", QuestType.KILL,
            50, 58, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20129, 100, "Forest of Outlaws", 90000, 80000, -3000, "Kill outlaws"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(1400000, 280000, 100000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Forest of Outlaws", 53, true, 75));

        // Level 55-60: Timak Outpost
        register(new QuestInfo(30011, "Timak Outpost Raid", QuestType.KILL,
            55, 64, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20130, 110, "Timak Outpost", 70000, 60000, -3000, "Kill Timak orcs"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(2000000, 400000, 150000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Timak Outpost", 58, true, 80));

        // Level 60-65: Wall of Argos
        register(new QuestInfo(30012, "Argos Wall Defense", QuestType.KILL,
            60, 70, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20131, 120, "Wall of Argos", 150000, 50000, -3000, "Kill creatures near Argos"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(2800000, 560000, 200000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Wall of Argos", 63, true, 85));

        // Level 65-70: Varka Silenos / Ketra Orc
        register(new QuestInfo(30013, "Silenos Suppression", QuestType.KILL,
            65, 75, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20132, 130, "Varka Silenos", 100000, -100000, -3000, "Kill Silenos"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(3800000, 760000, 300000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Varka Silenos", 68, true, 90));

        // Level 70-76: Imperial Tomb / Monastery
        register(new QuestInfo(30014, "Imperial Tomb Exploration", QuestType.KILL,
            70, 80, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Guard"),
                new QuestStep(StepType.KILL, 20133, 150, "Imperial Tomb", 50000, -50000, -3000, "Kill tomb undead"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Guard")
            ),
            new QuestReward(5000000, 1000000, 500000, Collections.emptyList(), Collections.emptyList()),
            "Guard", 30002, "Imperial Tomb", 73, true, 100));
    }

    // ------------------------------------------------------------------
    // ITEM / ADENA QUESTS (Good money, various levels)
    // ------------------------------------------------------------------

    private static void registerItemAdenaQuests() {
        // Adena quest: Collect spider silk (low level)
        register(new QuestInfo(40001, "Spider Silk Collection", QuestType.COLLECT,
            5, 15, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Trader"),
                new QuestStep(StepType.COLLECT, 20140, 50, "Gludio", -60000, 140000, -3000, "Collect Spider Silk from spiders"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Trader")
            ),
            new QuestReward(5000, 1000, 15000, Collections.emptyList(), Collections.emptyList()),
            "Trader", 30002, "Gludio", 10, true, 25));

        // Adena quest: Collect bone fragments
        register(new QuestInfo(40002, "Bone Fragment Collection", QuestType.COLLECT,
            15, 30, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Blacksmith"),
                new QuestStep(StepType.COLLECT, 20141, 80, "Ruins of Agony", -50000, 120000, -3000, "Collect Bone Fragments"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Blacksmith")
            ),
            new QuestReward(30000, 6000, 50000, Collections.emptyList(), Collections.emptyList()),
            "Blacksmith", 30002, "Ruins of Agony", 22, true, 40));

        // Adena quest: Collect animal skin
        register(new QuestInfo(40003, "Animal Skin Collection", QuestType.COLLECT,
            25, 40, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Trader"),
                new QuestStep(StepType.COLLECT, 20142, 100, "Abandoned Camp", -20000, 130000, -3000, "Collect Animal Skins"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Trader")
            ),
            new QuestReward(60000, 12000, 100000, Collections.emptyList(), Collections.emptyList()),
            "Trader", 30002, "Abandoned Camp", 32, true, 50));

        // Adena quest: Collect undead ashes
        register(new QuestInfo(40004, "Undead Ash Collection", QuestType.COLLECT,
            35, 50, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Magister"),
                new QuestStep(StepType.COLLECT, 20143, 120, "Execution Grounds", 50000, 150000, -3000, "Collect Undead Ashes"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Magister")
            ),
            new QuestReward(100000, 20000, 200000, Collections.emptyList(), Collections.emptyList()),
            "Magister", 30002, "Execution Grounds", 42, true, 60));

        // Adena quest: Collect dragon scales
        register(new QuestInfo(40005, "Dragon Scale Collection", QuestType.COLLECT,
            50, 70, RACE_ALL, 0, Collections.emptySet(),
            Arrays.asList(
                new QuestStep(StepType.TALK, 30002, 1, "Gludio", -14440, 121064, -3000, "Talk to Blacksmith"),
                new QuestStep(StepType.COLLECT, 20144, 100, "Dragon Valley", 120000, 110000, -3000, "Collect Dragon Scales"),
                new QuestStep(StepType.RETURN, 30002, 1, "Gludio", -14440, 121064, -3000, "Return to Blacksmith")
            ),
            new QuestReward(250000, 50000, 500000, Collections.emptyList(), Collections.emptyList()),
            "Blacksmith", 30002, "Dragon Valley", 58, true, 80));
    }
}
