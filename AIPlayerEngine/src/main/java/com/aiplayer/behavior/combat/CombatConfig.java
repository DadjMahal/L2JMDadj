package com.aiplayer.behavior.combat;

import com.aiplayer.core.AIConfiguration;

/**
 * Combat AI Configuration — controls combat behavior, skill usage, and engagement rules.
 *
 * <p>EB-11 SINGLE-SOURCE: this class no longer parses its own copy of
 * {@code config/ai-player.properties}. Every read delegates to the ONE loaded store,
 * {@link AIConfiguration}, which the engine loads exactly once. Public getters keep the same
 * keys + defaults, so callers (CombatAI, tests) are unaffected — but the duplicate file parse
 * is gone.
 */
public class CombatConfig {
    private static final CombatConfig INSTANCE = new CombatConfig();

    private CombatConfig() {
    }

    public static CombatConfig getInstance() {
        return INSTANCE;
    }

    // Configuration getters
    public boolean isEnabled() {
        return getBooleanProperty("combat.enabled", true);
    }

    public int getTargetDistance() {
        return getIntProperty("combat.target_distance", 1500);
    }

    /**
     * Get the target distance (attack range).
     * @return attack range in game units
     */
    public int getAttackRange() {
        return getIntProperty("combat.attack_range", 1500);
    }

    /**
     * Get the detect range for enemy detection.
     * @return detection range in game units
     */
    public int getDetectRange() {
        return getIntProperty("combat.detect_range", 3000);
    }

    public long getCooldown() {
        return getLongProperty("combat.skill_cooldown", 5000);
    }

    public boolean isPvPenabled() {
        return getBooleanProperty("combat.pvp_enabled", false);
    }

    public int getHealthThreshold() {
        return getIntProperty("combat.health_threshold", 30);
    }

    public int getManaThreshold() {
        return getIntProperty("combat.mana_threshold", 20);
    }

    public int getMaxTargets() {
        return getIntProperty("combat.max_targets", 3);
    }

    public boolean isAutoPlayEnabled() {
        return getBooleanProperty("combat.auto_play_enabled", true);
    }

    // PvP configuration
    public boolean isPvPEnabled() {
        return getBooleanProperty("combat.pvp_enabled", false);
    }

    public int getPvPKarmaThreshold() {
        return getIntProperty("combat.pvp_karma_threshold", 500);
    }

    // Defensive thresholds
    public int getDefensiveThreshold() {
        return getIntProperty("combat.defensive_threshold", 40);
    }

    public int getRetreatThreshold() {
        return getIntProperty("combat.retreat_threshold", 15);
    }

    // Skill priority
    public String getSkillPriority() {
        return getProperty("combat.skill_priority", "ATTACK:1,HEAL:2,POWER_STRIKE:3");
    }

    public String getPreferredSkill() {
        return getProperty("combat.preferred_skill", "POWER_STRIKE");
    }

    public int getSkillMpCost() {
        return getIntProperty("combat.skill_mp_cost", 10);
    }

    public String getHealSkill() {
        return getProperty("combat.heal_skill", "HEAL");
    }

    // Utility methods
    // EB-11 SINGLE-SOURCE: delegate every read to the ONE loaded AIConfiguration store.
    private String getProperty(String key, String defaultValue) {
        return AIConfiguration.getInstance().getProperty(key, defaultValue);
    }

    private int getIntProperty(String key, int defaultValue) {
        return AIConfiguration.getInstance().getIntProperty(key, defaultValue);
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        return AIConfiguration.getInstance().getBooleanProperty(key, defaultValue);
    }

    private long getLongProperty(String key, long defaultValue) {
        return AIConfiguration.getInstance().getLongProperty(key, defaultValue);
    }
}
