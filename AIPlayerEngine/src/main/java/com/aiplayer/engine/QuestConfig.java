package com.aiplayer.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Quest AI Configuration
 * Controls quest selection, acceptance, and completion behavior
 */
public class QuestConfig {
    private static final Logger LOGGER = Logger.getLogger(QuestConfig.class.getName());
    private static final QuestConfig INSTANCE = new QuestConfig();

    private final Properties properties = new Properties();

    private QuestConfig() {
        loadConfiguration();
    }

    public static QuestConfig getInstance() {
        return INSTANCE;
    }

    private void loadConfiguration() {
        try {
            InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/ai-player.properties");

            if (in != null) {
                properties.load(in);
                LOGGER.info("Quest configuration loaded");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load quest config, using defaults");
            loadDefaults();
        }
    }

    private void loadDefaults() {
        // Quest AI defaults
        setProperty("quest.enabled", "true");
        setProperty("quest.max_active", "3");
        setProperty("quest.search_radius", "2000");
        setProperty("quest.daily_priority", "true");
        setProperty("quest.class_change_enabled", "true");
        setProperty("quest.abandon_on_failure", "true");
        setProperty("quest.deadline_hours", "24");
    }

    // Configuration getters
    public boolean isEnabled() {
        return getBooleanProperty("quest.enabled", true);
    }

    public int getMaxActiveQuests() {
        return getIntProperty("quest.max_active", 3);
    }

    public int getSearchRadius() {
        return getIntProperty("quest.search_radius", 2000);
    }

    public boolean isDailyPriority() {
        return getBooleanProperty("quest.daily_priority", true);
    }

    public boolean isClassChangeEnabled() {
        return getBooleanProperty("quest.class_change_enabled", true);
    }

    public boolean shouldAbandonOnFailure() {
        return getBooleanProperty("quest.abandon_on_failure", true);
    }

    public int getDeadlineHours() {
        return getIntProperty("quest.deadline_hours", 24);
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
}
