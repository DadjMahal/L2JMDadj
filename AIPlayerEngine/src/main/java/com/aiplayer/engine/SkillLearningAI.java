package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class SkillLearningAI {
    private static final Logger LOGGER = Logger.getLogger(SkillLearningAI.class.getName());
    
    public static class SkillTemplate {
        public final int skillId;
        public final String name;
        public final int level;
        public final int price;
        public final int requiredLevel;
        public final String masterName;
        
        public SkillTemplate(int id, String n, int lvl, int p, int req, String master) {
            skillId = id; name = n; level = lvl; price = p; requiredLevel = req; masterName = master;
        }
    }
    
    public static boolean shouldLearnSkill(SkillTemplate skill, int playerLevel, int adena, boolean alreadyHas) {
        if (alreadyHas) return false;
        if (playerLevel < skill.requiredLevel) return false;
        if (adena < skill.price) return false;
        return true;
    }
    
    public static String findSkillMaster(String skillType) {
        switch (skillType) {
            case "combat": return "Warrior Instructor";
            case "magic": return "Magic Instructor";
            case "archery": return "Archery Master";
            default: return "Skill Master";
        }
    }
}
