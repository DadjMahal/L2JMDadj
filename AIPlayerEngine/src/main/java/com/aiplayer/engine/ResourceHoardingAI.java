package com.aiplayer.engine;
import java.util.logging.Logger;

public class ResourceHoardingAI {
    private static final Logger LOGGER = Logger.getLogger(ResourceHoardingAI.class.getName());
    private int savedAdena = 0;

    public enum HoardingStyle { SAVE, SPEND_BALANCED, SPEND_FREELY }

    public boolean shouldSave(int adena, int marketPrice, HoardingStyle style) {
        switch (style) {
            case SAVE: return true;
            case SPEND_BALANCED: return adena > marketPrice * 10;
            case SPEND_FREELY: return adena < marketPrice * 2;
            default: return false;
        }
    }

    public void saveResource(int amount) {
        savedAdena += amount;
    }

    public int getSavedAmount() { return savedAdena; }
}
