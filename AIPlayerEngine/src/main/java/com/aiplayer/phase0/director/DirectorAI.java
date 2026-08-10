package com.aiplayer.phase0.director;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;
import com.aiplayer.phase0.cabinet.ProfileStore;

import java.util.*;
import java.util.logging.Logger;

public class DirectorAI {
    private static final Logger LOGGER = Logger.getLogger(DirectorAI.class.getName());
    private static DirectorAI instance;
    private final ProfileStore cabinet;

    private DirectorAI() { this.cabinet = ProfileStore.getInstance(); }

    public static synchronized DirectorAI getInstance() {
        if (instance == null) instance = new DirectorAI();
        return instance;
    }

    public List<BotProfile> getSpawnQueue() {
        List<BotProfile> all = cabinet.loadAllActive();
        LOGGER.info("Director: " + all.size() + " bot profiles in cabinet");
        return all;
    }

    public String suggestNextRole(List<BotProfile> currentOnline) {
        long healers = currentOnline.stream().filter(BotProfile::isHealer).count();
        long fighters = currentOnline.stream().filter(p -> !p.isHealer() && !p.isBuffer()).count();
        if (fighters > 0 && healers < fighters * 0.15) return "HEALER";
        if (currentOnline.size() % 5 == 0) return "BUFFER";
        return "FIGHTER";
    }
}
