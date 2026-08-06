package com.aiplayer.engine;

import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

/**
 * Task 100: PvP Skill Rotation Engine
 *
 * Provides class-specific PvP skill rotations and optimization.
 * Based on Lineage 2 Interlude class mechanics.
 */
public class PvPSkillRotation {
    private static final Logger LOGGER = Logger.getLogger(PvPSkillRotation.class.getName());

    public enum SkillType { HIGH_BURST, CONTROL, SHOOT, DOOM, SILENCE }

    // Skill ID mappings for common Lineage 2 skills
    private static final int POWER_STRIKE = 117;  // High burst damage
    private static final int POISON_STRIKE = 120; // Damage over time
    private static final int FALLING_STAFF = 129; // AoE damage
    private static final int HEAL = 9;            // Self heal
    private static final int BARRIER = 121;       // Defense boost
    private static final int SHIELD = 114;        // Defensive stance

    public static List<SkillType> getOptimalRotation(String enemyClass, boolean hasBurstReady) {
        List<SkillType> rotation = new ArrayList<>();
        switch(enemyClass) {
            case "Wizard": case "Cleric": rotation.addAll(List.of(SkillType.SILENCE, SkillType.HIGH_BURST, SkillType.CONTROL)); break;
            case "Warrior": case "Gladiator": rotation.addAll(List.of(SkillType.CONTROL, SkillType.HIGH_BURST, SkillType.SHOOT)); break;
            default: rotation.addAll(List.of(SkillType.HIGH_BURST, SkillType.SHOOT, SkillType.SHOOT));
        }
        return rotation;
    }

    /** Get highest burst damage skill */
    public static int getHighestBurstSkill() {
        return POWER_STRIKE;
    }

    /** Get high damage skill */
    public static int getHighSkill() {
        return POISON_STRIKE;
    }

    /** Get defensive skill */
    public static int getDefensiveSkill() {
        return BARRIER;
    }

    /** Get control/disable skill */
    public static int getControlSkill() {
        return SHIELD;
    }

    /** Get optimal skill based on enemy class and MP percentage */
    public static int getSkillForClass(String enemyClass, int mpPercentage) {
        if (mpPercentage > 60) {
            return getHighestBurstSkill();
        } else if (mpPercentage > 30) {
            return getHighSkill();
        } else if (mpPercentage > 15) {
            return getControlSkill();
        }
        return 0; // Basic attack
    }

    /** Check if skill is available based on cooldown */
    public static boolean isSkillAvailable(long lastUseTime, long cooldownMs) {
        return System.currentTimeMillis() - lastUseTime >= cooldownMs;
    }
}
