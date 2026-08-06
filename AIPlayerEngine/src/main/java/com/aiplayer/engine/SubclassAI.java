package com.aiplayer.engine;
import java.util.logging.Logger;

public class SubclassAI {
    private static final Logger LOGGER = Logger.getLogger(SubclassAI.class.getName());

    public static boolean canSubclass(int level, int classId) {
        if (level < 37) return false;
        return classId < 8;
    }

    public static String getSubclassForClass(int baseClassId) {
        switch (baseClassId) {
            case 0: return "WARRIOR";
            case 1: return "GLADIATOR";
            case 2: return "ROGUE";
            case 3: return "ARCHER";
            case 4: return "POISON_FLOWER";
            case 5: return "WIZARD";
            case 6: return "CLERIC";
            case 7: return "WEAPON";
            default: return "WARRIOR";
        }
    }

    public static int getSubclassPenalty(int subclassLevel) {
        return subclassLevel * 3;
    }
}
