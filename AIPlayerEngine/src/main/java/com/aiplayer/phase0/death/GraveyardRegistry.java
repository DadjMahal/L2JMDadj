package com.aiplayer.phase0.death;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of known graveyards. Phase 0: hardcoded common zones.
 * Phase 1: load from L2JMobius zone data.
 */
public final class GraveyardRegistry {
    private static final List<Graveyard> GRAVEYARDS = new ArrayList<>();

    static {
        // Common Interlude hunting zones
        GRAVEYARDS.add(new Graveyard("Gludio Plains", -14225, 123540, -3120, 1, 20));
        GRAVEYARDS.add(new Graveyard("Ruins of Agony", -41250, 122000, -2900, 10, 25));
        GRAVEYARDS.add(new Graveyard("Ruins of Despair", -91500, 248000, -3580, 15, 30));
        GRAVEYARDS.add(new Graveyard("Ant Nest", -14000, 187000, -3200, 25, 40));
        GRAVEYARDS.add(new Graveyard("Cruma Tower", 17720, 114095, -11600, 30, 50));
        GRAVEYARDS.add(new Graveyard("Dragon Valley", 73000, 118000, -3700, 40, 60));
        GRAVEYARDS.add(new Graveyard("Devastated Castle", 125000, 66000, -2700, 55, 75));
        GRAVEYARDS.add(new Graveyard("Wall of Argos", 183000, -12000, -2700, 65, 80));
    }

    public static Graveyard findNearest(int x, int y, int level) {
        Graveyard best = null;
        double bestDist = Double.MAX_VALUE;

        for (Graveyard g : GRAVEYARDS) {
            if (level < g.levelMin || level > g.levelMax) continue;
            double d = g.distanceTo(x, y);
            if (d < bestDist) {
                bestDist = d;
                best = g;
            }
        }
        return best;
    }

    public static List<Graveyard> all() {
        return new ArrayList<>(GRAVEYARDS);
    }
}
