package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.*;

/**
 * Interlude C4 Skill Database.
 * Maps skill IDs to metadata and provides class-specific skill lookups.
 *
 * Phase 0: Static in-memory database. Phase 1 may load from DB.
 */
public final class SkillDatabase {
    private static final Map<Integer, SkillInfo> SKILLS = new HashMap<>();
    private static final Map<Integer, List<Integer>> CLASS_SKILLS = new HashMap<>();

    static {
        // Interlude (L2jMobius datapack, ServerBuild/game/data/stats/skills/00000-00099.xml) lv-1 values,
        // cross-checked 2026-08-10. 3 and 16 are the Human Fighter path actually used by
        // FighterRotation (via Phase0Integration/CombatAI when phase0 combat rotation is on).
        register(3,   "Power Strike",      10,  13000, 40,  SkillInfo.SkillTarget.SINGLE_ENEMY, 3,  true,  false, false);
        register(16,  "Mortal Blow",       9,   11000, 40,  SkillInfo.SkillTarget.SINGLE_ENEMY, 3,  true,  false, false);
        // NOTE (Interlude realignment): Interlude skill id 36 is "Whirlwind" and id 70 is "Drain Health" — the
        // Kimi draft's "Power Shot"(36)/"Iron Punch"(70) do NOT exist under those ids in the Interlude
        // datapack, so those wrong rows were removed rather than re-mapped with guessed numbers;
        // the full Gladiator/Warlord skill trees are a follow-up datapack task (Audit/43).
        register(56,  "Power Shot (Archer)",35, 6000,  700, SkillInfo.SkillTarget.SINGLE_ENEMY, 5,  true,  false, false);

        // === MAGE SKILLS ===
        register(1177, "Wind Strike",    20,  3000,  600, SkillInfo.SkillTarget.SINGLE_ENEMY, 5,  true,  false, false);
        register(1178, "Blaze",           25,  3000,  600, SkillInfo.SkillTarget.SINGLE_ENEMY, 5,  true,  false, false);
        register(1181, "Flame Strike",    35,  4000,  600, SkillInfo.SkillTarget.SINGLE_ENEMY, 10, true,  false, false);
        register(1184, "Ice Bolt",        25,  3000,  600, SkillInfo.SkillTarget.SINGLE_ENEMY, 5,  true,  false, false);
        register(1189, "Vampiric Touch",  40,  8000,  600, SkillInfo.SkillTarget.SINGLE_ENEMY, 15, true,  false, false);

        // === HEALER SKILLS ===
        register(1011, "Heal",            40,  3000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, false, true);
        register(1027, "Battle Heal",       55,  4000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  15, false, false, true);
        register(1028, "Group Heal",      90,  6000,  400, SkillInfo.SkillTarget.PARTY,        25, false, false, true);
        register(1069, "Resurrection",    200, 30000, 400, SkillInfo.SkillTarget.SINGLE_ALLY,  20, false, false, true);

        // === BUFFER SKILLS ===
        register(1068, "Might",           20,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, true,  false);
        register(1077, "Focus",           20,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, true,  false);
        register(1086, "Haste",           25,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, true,  false);
        register(1242, "Death Whisper",   25,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  20, false, true,  false);
        register(1040, "Shield",          20,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, true,  false);
        register(1035, "Mental Shield",   20,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  10, false, true,  false);
        register(1045, "Blessed Body",    25,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, true,  false);
        register(1048, "Blessed Soul",    25,  2000,  400, SkillInfo.SkillTarget.SINGLE_ALLY,  5,  false, true,  false);

        // === COMMON / DEFENSIVE ===
        register(111, "Ultimate Defense", 0,  300000,0,   SkillInfo.SkillTarget.SELF,         20, false, true,  false);
        register(191, "Focus Attack",     0,  30000, 0,   SkillInfo.SkillTarget.SELF,         5,  false, true,  false);

        // === CLASS SKILL MAPPINGS ===
        // Human Fighter (0) / Warrior (1) / Gladiator (2) / Warlord (3)
        // (36/70 dropped — see the Interlude-realignment note above; FighterRotation path = skills 3/16)
        addClassSkills(0, 3, 16);
        addClassSkills(1, 3, 16);
        addClassSkills(2, 3, 16);
        addClassSkills(3, 3, 16);

        // Human Mage (10) / Wizard (11) / Sorcerer (12)
        addClassSkills(10, 1177, 1178, 1184);
        addClassSkills(11, 1177, 1178, 1184, 1181);
        addClassSkills(12, 1177, 1178, 1184, 1181, 1189);

        // Elven Scout (18) / Elven Ranger (22) / Assassin (19) / Abyss Walker (35)
        addClassSkills(18, 56);
        addClassSkills(22, 56);
        addClassSkills(19, 3, 16);
        addClassSkills(35, 3, 16);

        // Cleric (25) / Bishop (15) / Cardinal (29)
        addClassSkills(25, 1011, 1027);
        addClassSkills(15, 1011, 1027, 1028, 1069);
        addClassSkills(29, 1011, 1027, 1028, 1069);

        // Prophet (16) / Bladedancer (32) / Other buffers
        addClassSkills(16, 1068, 1077, 1086, 1242, 1040, 1035, 1045, 1048);
        addClassSkills(32, 1068, 1077, 1086, 1242, 1040, 1035);
    }

    private static void register(int id, String name, int mpCost, int cooldownMs,
                               int castRange, SkillInfo.SkillTarget targetType,
                               int requiredLevel, boolean offensive, boolean buff, boolean heal) {
        SKILLS.put(id, new SkillInfo(id, name, mpCost, cooldownMs, castRange,
                targetType, requiredLevel, offensive, buff, heal));
    }

    private static void addClassSkills(int classId, Integer... skillIds) {
        CLASS_SKILLS.put(classId, Arrays.asList(skillIds));
    }

    public static SkillInfo get(int skillId) {
        return SKILLS.get(skillId);
    }

    public static List<SkillInfo> getSkillsForClass(int classId) {
        List<Integer> ids = CLASS_SKILLS.getOrDefault(classId, Collections.emptyList());
        List<SkillInfo> result = new ArrayList<>();
        for (int id : ids) {
            SkillInfo s = SKILLS.get(id);
            if (s != null) result.add(s);
        }
        return result;
    }

    public static boolean hasSkill(int classId, int skillId) {
        List<Integer> ids = CLASS_SKILLS.getOrDefault(classId, Collections.emptyList());
        return ids.contains(skillId);
    }

    public static List<SkillInfo> getOffensiveSkills(int classId) {
        List<SkillInfo> result = new ArrayList<>();
        for (SkillInfo s : getSkillsForClass(classId)) {
            if (s.isOffensive) result.add(s);
        }
        return result;
    }

    public static List<SkillInfo> getHealSkills(int classId) {
        List<SkillInfo> result = new ArrayList<>();
        for (SkillInfo s : getSkillsForClass(classId)) {
            if (s.isHeal) result.add(s);
        }
        return result;
    }

    public static List<SkillInfo> getBuffSkills(int classId) {
        List<SkillInfo> result = new ArrayList<>();
        for (SkillInfo s : getSkillsForClass(classId)) {
            if (s.isBuff) result.add(s);
        }
        return result;
    }
}
