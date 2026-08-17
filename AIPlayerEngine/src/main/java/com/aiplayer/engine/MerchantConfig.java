package com.aiplayer.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Merchant AI Configuration
 * Controls buying, selling, and trading behavior
 */
public class MerchantConfig {
    private static final Logger LOGGER = Logger.getLogger(MerchantConfig.class.getName());
    private static final MerchantConfig INSTANCE = new MerchantConfig();

    private final Properties properties = new Properties();

    private MerchantConfig() {
        loadConfiguration();
    }

    public static MerchantConfig getInstance() {
        return INSTANCE;
    }

    private void loadConfiguration() {
        try {
            InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/ai-player.properties");

            if (in != null) {
                properties.load(in);
                LOGGER.info("Merchant configuration loaded");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load merchant config, using defaults");
            loadDefaults();
        }
    }

    private void loadDefaults() {
        // Merchant AI defaults
        setProperty("merchant.enabled", "true");
        setProperty("merchant.min_buy_amount", "10000");
        setProperty("merchant.min_sell_amount", "5000");
        setProperty("merchant.min_emergency_amount", "1000");
        setProperty("merchant.max_items_to_trade", "10");
        setProperty("merchant.search_radius", "5000");
        setProperty("merchant.profit_margin_threshold", "0.10");
        setProperty("merchant.restock_enabled", "true");
    }

    // Configuration getters
    public boolean isEnabled() {
        return getBooleanProperty("merchant.enabled", true);
    }

    public int getMinBuyAmount() {
        return getIntProperty("merchant.min_buy_amount", 10000);
    }

    public int getMinSellAmount() {
        return getIntProperty("merchant.min_sell_amount", 5000);
    }

    public int getMinEmergencyAmount() {
        return getIntProperty("merchant.min_emergency_amount", 1000);
    }

    public int getMaxItemsToTrade() {
        return getIntProperty("merchant.max_items_to_trade", 10);
    }

    public int getSearchRadius() {
        return getIntProperty("merchant.search_radius", 5000);
    }

    public double getProfitMarginThreshold() {
        return getDoubleProperty("merchant.profit_margin_threshold", 0.10);
    }

    public boolean isRestockEnabled() {
        return getBooleanProperty("merchant.restock_enabled", true);
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
}
