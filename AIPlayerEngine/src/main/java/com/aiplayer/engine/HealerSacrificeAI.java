package com.aiplayer.engine;
import java.util.logging.Logger;

public class HealerSacrificeAI {
    private static final Logger LOGGER = Logger.getLogger(HealerSacrificeAI.class.getName());
    
    public static boolean shouldSacrifice(int ownHp, int allyHp) {
        if (ownHp < 20) return false; // Don't suicide
        if (allyHp < 10) return true;  // Save ally
        return ownHp < 50 && allyHp < 30; // Risk to save
    }
    
    public static int calculateProtectPriority(int allyLevel, int allyHp, int enemyCount) {
        return (int)(allyLevel * 0.5 + allyHp + enemyCount * 10);
    }
}
