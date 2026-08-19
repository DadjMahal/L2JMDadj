package com.aiplayer.behavior.social;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Social AI Configuration
 * Controls clan joining, party formation, and chat behavior
 */
public class SocialConfig {
    private static final Logger LOGGER = Logger.getLogger(SocialConfig.class.getName());
    private static final SocialConfig INSTANCE = new SocialConfig();

    private final Properties properties = new Properties();

    private SocialConfig() {
        loadConfiguration();
    }

    public static SocialConfig getInstance() {
        return INSTANCE;
    }

    private void loadConfiguration() {
        try {
            InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/ai-player.properties");

            if (in != null) {
                properties.load(in);
                LOGGER.info("Social configuration loaded");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load social config, using defaults");
            loadDefaults();
        }
    }

    private void loadDefaults() {
        // Social AI defaults
        setProperty("social.enabled", "true");
        setProperty("social.clan.join_enabled", "true");
        setProperty("social.party.invite_enabled", "true");
        setProperty("social.chat.enabled", "true");
        setProperty("social.clan.join_probability", "0.3");
        setProperty("social.party.invite_probability", "0.5");
        setProperty("social.chat_probability", "0.1");
        setProperty("social.chat_interval", "300000");
        setProperty("social.party_search_radius", "10000");
    }

    // Configuration getters
    public boolean isEnabled() {
        return getBooleanProperty("social.enabled", true);
    }

    public boolean isClanJoinEnabled() {
        return getBooleanProperty("social.clan.join_enabled", true);
    }

    public boolean isPartyInviteEnabled() {
        return getBooleanProperty("social.party.invite_enabled", true);
    }

    public boolean isChatEnabled() {
        return getBooleanProperty("social.chat.enabled", true);
    }

    public double getClanJoinProbability() {
        return getDoubleProperty("social.clan.join_probability", 0.3);
    }

    public double getPartyInviteProbability() {
        return getDoubleProperty("social.party.invite_probability", 0.5);
    }

    public double getChatProbability() {
        return getDoubleProperty("social.chat_probability", 0.1);
    }

    public long getChatInterval() {
        return getLongProperty("social.chat_interval", 300000);
    }

    public int getPartySearchRadius() {
        return getIntProperty("social.party.search_radius", 10000);
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

    private double getDoubleProperty(String key) {
        return Double.parseDouble(getProperty(key));
    }

    private double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
