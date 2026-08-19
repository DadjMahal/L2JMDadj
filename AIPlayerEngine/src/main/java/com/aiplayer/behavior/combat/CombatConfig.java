package com.aiplayer.behavior.combat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Combat AI Configuration
 * Controls combat behavior, skill usage, and engagement rules
 */
public class CombatConfig {
    private static final Logger LOGGER = Logger.getLogger(CombatConfig.class.getName());
    private static final CombatConfig INSTANCE = new CombatConfig();

    private final Properties properties = new Properties();

    private CombatConfig() {
        loadConfiguration();
    }

    public static CombatConfig getInstance() {
        return INSTANCE;
    }

    private void loadConfiguration() {
        try {
            InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/ai-player.properties");

            if (in != null) {
                properties.load(in);
                LOGGER.info("Combat configuration loaded");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load combat config, using defaults");
            loadDefaults();
        }
    }

    private void loadDefaults() {
        // Combat AI defaults
        setProperty("combat.enabled", "true");
        setProperty("combat.target_distance", "1500");
        setProperty("combat.attack_range", "1500");
        setProperty("combat.detect_range", "3000");
        setProperty("combat.skill_cooldown", "5000");
        setProperty("combat.pvp_enabled", "false");
        setProperty("combat.pvp_karma_threshold", "500");
        setProperty("combat.health_threshold", "50");
        setProperty("combat.mana_threshold", "20");
        setProperty("combat.defensive_threshold", "40");
        setProperty("combat.retreat_threshold", "15");
        setProperty("combat.max_targets", "3");
        setProperty("combat.auto_play_enabled", "true");
        setProperty("combat.skill_priority", "ATTACK:1,HEAL:2,POWER_STRIKE:3");
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
    private String getProperty(String key) {
        return properties.getProperty(key);
    }

    private String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    private int getIntProperty(String key) {
        return Integer.parseInt(getProperty(key));
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanProperty(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private long getLongProperty(String key) {
        return Long.parseLong(getProperty(key));
    }

    private long getLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
