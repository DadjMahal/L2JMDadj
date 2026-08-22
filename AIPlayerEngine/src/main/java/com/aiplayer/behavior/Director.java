package com.aiplayer.behavior;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import com.aiplayer.core.BotProfile;
import com.aiplayer.core.DeterministicRandom;

/**
 * Fleet casting director (EP-5: former DirectorAI + NameGenerator one-class files merged).
 * Decovers which role the fleet is short of and mints believable character names for it.
 */
public class Director {
    private static final Logger LOGGER = Logger.getLogger(Director.class.getName());
    private static Director instance;
    private final ProfileStore cabinet;

    private Director() { this.cabinet = ProfileStore.getInstance(); }

    public static synchronized Director getInstance() {
        if (instance == null) instance = new Director();
        return instance;
    }

    /** Was DirectorAI.getSpawnQueue. */
    public List<BotProfile> getSpawnQueue() {
        List<BotProfile> all = cabinet.loadAllActive();
        LOGGER.info("Director: " + all.size() + " bot profiles in cabinet");
        return all;
    }

    /** Was DirectorAI.suggestNextRole — healer-starved -> HEALER, every 5th slot -> BUFFER. */
    public String suggestNextRole(List<BotProfile> currentOnline) {
        long healers = currentOnline.stream().filter(BotProfile::isHealer).count();
        long fighters = currentOnline.stream().filter(p -> !p.isHealer() && !p.isBuffer()).count();
        if (fighters > 0 && healers < fighters * 0.15) return "HEALER";
        if (currentOnline.size() % 5 == 0) return "BUFFER";
        return "FIGHTER";
    }

    /** Was NameGenerator — unique plausible player names ("DarkSlayer", "ShadowKnight7"...). */
    public static final class NameGenerator {
        private static final String[] PREFIXES = {"Dark","Shadow","Blood","Death","Holy","Light","Fire","Ice","Storm","Thunder"};
        private static final String[] CORES = {"Slayer","Walker","Hunter","Mage","Knight","Lord","Reaper","Wing","Blade","Soul"};
        private static final String[] SUFFIXES = {"xX","XX","x","iI","II","ofDoom","theGreat","xD","pro"};
        private static final Set<String> USED = new HashSet<>();
        private static final Random RNG = DeterministicRandom.forFleet("director-names");

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

        private NameGenerator() {}
    }
}
