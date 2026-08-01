package com.aiplayer.engine;
import java.util.logging.Logger;

public class ArmorProficiency {
    private static final Logger LOGGER = Logger.getLogger(ArmorProficiency.class.getName());
    
    public enum ArmorType { NONE, LIGHT, HEAVY, ROBES, MAGICAL }
    public enum ClassArmor { SMALL, NORMAL, LARGE, HOOD, NONE }
    
    public static boolean canEquip(String className, ArmorType armor) {
        switch (armor) {
            case NONE: return true;
            case LIGHT: return className.equals("Rogue") || className.equals("Elf");
            case HEAVY: return className.equals("Fighter") || className.equals("Warrior") || className.equals("DarkKnight");
            case ROBES: return className.equals("Wizard") || className.equals("Cleric");
            case MAGICAL: return className.equals("Wizard");
            default: return false;
        }
    }
    
    public static double getProficiencyPenalty(String className, ArmorType armor) {
        return canEquip(className, armor) ? 1.0 : 0.5;
    }
}
