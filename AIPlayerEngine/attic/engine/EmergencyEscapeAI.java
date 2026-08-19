// package com.aiplayer.engine;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class EmergencyEscapeAI {
    private static final Logger LOGGER = Logger.getLogger(EmergencyEscapeAI.class.getName());
    private final List<String> escapeRoutes = new ArrayList<>();

    public static class EscapePoint {
        public final int x, y, z;
        public final String name;
        public final int safetyLevel;

        public EscapePoint(int x, int y, int z, String n, int safety) {
            this.x = x; this.y = y; this.z = z; name = n; safetyLevel = safety;
        }
    }

    public void addEscapeRoute(EscapePoint point) {
        escapeRoutes.add(point.name + ":" + point.x + "," + point.y);
    }

    public EscapePoint getBestEscape(EscapePoint[] available, int dangerLevel) {
        EscapePoint best = null;
        for (EscapePoint ep : available) {
            if (ep.safetyLevel > dangerLevel) {
                if (best == null || ep.safetyLevel > best.safetyLevel) best = ep;
            }
        }
        return best;
    }

    public boolean shouldUseEscape(int healthPercent, int enemyCount) {
        return healthPercent < 20 || enemyCount > 10;
    }
}
