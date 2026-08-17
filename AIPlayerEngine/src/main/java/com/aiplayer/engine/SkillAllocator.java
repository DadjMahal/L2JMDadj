package com.aiplayer.engine;
import java.util.logging.Logger;

public class SkillAllocator {
    private static final Logger LOGGER = Logger.getLogger(SkillAllocator.class.getName());

    public static int[] allocateSkillPoints(int level, int classId, int currentSp) {
        int[] allocation = new int[10]; // 10 skill categories
        if (level < 20) return allocation;

        // Follow class build
        switch (classId) {
            case 0: // Fighter
                allocation[0] = currentSp; break; // Weapon skills
            case 1: // Warrior
                allocation[0] = currentSp; break;
            case 2: // Rogue
                allocation[1] = currentSp; break; // Sub Weapon skills
            case 3: // Elf
                allocation[2] = currentSp; break; // bow/critical
            default:
                allocation[0] = currentSp; break;
        }
        return allocation;
    }
}
