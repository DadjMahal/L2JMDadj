// package com.aiplayer.engine;
import java.util.logging.Logger;

public class AttributeOptimizer {
    private static final Logger LOGGER = Logger.getLogger(AttributeOptimizer.class.getName());

    public static int[] optimizeAttributes(int classId, int level, int STR, int DEX, int CON, int INT, int WIT, int MEM) {
        int[] attrs = new int[]{STR, DEX, CON, INT, WIT, MEM};

        if (level < 20) return attrs;

        switch (classId) {
            case 0: attrs[0] += 2; attrs[2] += 1; break;
            case 1: attrs[0] += 2; attrs[2] += 1; break;
            case 2: attrs[0] += 1; attrs[1] += 2; break;
            case 3: attrs[0] += 1; attrs[1] += 1; attrs[4] += 1; break;
            case 4: attrs[1] += 2; attrs[4] += 1; break;
            case 5: attrs[3] += 3; break;
            case 6: attrs[5] += 2; attrs[2] += 1; break;
            default: attrs[2] += 1; attrs[0] += 1; break;
        }
        return attrs;
    }

    public static boolean shouldRespec(int current, int recommended) {
        return Math.abs(current - recommended) > 20;
    }
}
