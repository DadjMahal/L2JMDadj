package com.aiplayer.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI Player Configuration
 * Central configuration for all AI players
 */
public class AIConfiguration {
    private static final Logger LOGGER = Logger.getLogger(AIConfiguration.class.getName());
    
    private static final AIConfiguration INSTANCE = new AIConfiguration();
    
    private final Properties properties = new Properties();
    
    private AIConfiguration() {
        loadConfiguration();
    }
    
    public static AIConfiguration getInstance() {
        return INSTANCE;
    }
    
    private void loadConfiguration() {
        try {
            InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/ai-player.properties");
            
            if (in != null) {
                properties.load(in);
                LOGGER.info("Loaded AI Player configuration");
            } else {
                LOGGER.warning("No configuration file found, using defaults");
                loadDefaults();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load configuration", e);
            loadDefaults();
        }
    }
    
    private void loadDefaults() {
        // Default AI Player settings
        setProperty("ai.max_players", "10");
        setProperty("ai.think_interval_ms", "100");
        setProperty("ai.idle_timeout_ms", "300000");
        setProperty("ai.log_interval_ms", "60000");
        setProperty("ai.connection_attempts", "3");
        setProperty("ai.reconnect_delay_ms", "5000");
        setProperty("ai.client_protocol", "L2JMobius_v13");
        
        // Behavior defaults
        setProperty("behavior.merchant.min_profit_margin", "0.10");
        setProperty("behavior.quest.enabled", "true");
        setProperty("behavior.combat.enabled", "true");
        setProperty("behavior.social.enabled", "true");
        
        LOGGER.info("Loaded default configuration");
    }
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    public int getIntProperty(String key) {
        return Integer.parseInt(getProperty(key));
    }
    
    public int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public boolean getBooleanProperty(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }
    
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    public long getLongProperty(String key) {
        return Long.parseLong(getProperty(key));
    }
    
    public long getLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // Specific getters
    public int getMaxPlayers() {
        return getIntProperty("ai.max_players", 10);
    }
    
    public long getThinkInterval() {
        return getLongProperty("ai.think_interval_ms", 100);
    }
    
    public long getIdleTimeout() {
        return getLongProperty("ai.idle_timeout_ms", 300000);
    }
    
    public long getLogInterval() {
        return getLongProperty("ai.log_interval_ms", 60000);
    }
}