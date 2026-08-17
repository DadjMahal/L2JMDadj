package com.aiplayer.phase0.director;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class NameGenerator {
    private static final String[] PREFIXES = {"Dark","Shadow","Blood","Death","Holy","Light","Fire","Ice","Storm","Thunder"};
    private static final String[] CORES = {"Slayer","Walker","Hunter","Mage","Knight","Lord","Reaper","Wing","Blade","Soul"};
    private static final String[] SUFFIXES = {"xX","XX","x","iI","II","ofDoom","theGreat","xD","pro"};
    private static final Set<String> USED = new HashSet<>();
    private static final Random RNG = new Random();

    public static synchronized String generateUnique() {
        for (int i = 0; i < 1000; i++) {
            String name = build();
            if (!USED.contains(name)) { USED.add(name); return name; }
        }
        return "Bot" + System.currentTimeMillis();
    }

    private static String build() {
        int mode = RNG.nextInt(4);
        StringBuilder sb = new StringBuilder();
        if (mode == 0) sb.append(PREFIXES[RNG.nextInt(PREFIXES.length)]);
        sb.append(CORES[RNG.nextInt(CORES.length)]);
        if (mode == 1) sb.append(SUFFIXES[RNG.nextInt(SUFFIXES.length)]);
        if (mode == 2) sb.append(RNG.nextInt(99));
        String n = sb.toString();
        return n.substring(0, Math.min(n.length(), 16));
    }
}
