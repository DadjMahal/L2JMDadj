package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Immutable metadata for an Interlude C4 skill.
 */
public final class SkillInfo {
    public final int skillId;
    public final String name;
    public final int mpCost;
    public final int cooldownMs;      // Base cooldown in milliseconds
    public final int castRange;       // Maximum cast range in game units
    public final SkillTarget targetType;
    public final int requiredLevel;   // Minimum level to use this skill
    public final boolean isOffensive;
    public final boolean isBuff;
    public final boolean isHeal;

    public enum SkillTarget {
        SELF, SINGLE_ENEMY, AREA_ENEMY, SINGLE_ALLY, PARTY, NONE
    }

    public SkillInfo(int skillId, String name, int mpCost, int cooldownMs,
                     int castRange, SkillTarget targetType, int requiredLevel,
                     boolean isOffensive, boolean isBuff, boolean isHeal) {
        this.skillId = skillId;
        this.name = name;
        this.mpCost = mpCost;
        this.cooldownMs = cooldownMs;
        this.castRange = castRange;
        this.targetType = targetType;
        this.requiredLevel = requiredLevel;
        this.isOffensive = isOffensive;
        this.isBuff = isBuff;
        this.isHeal = isHeal;
    }

    @Override
    public String toString() {
        return name + "(" + skillId + ")";
    }
}
