package com.aiplayer.phase0.imperfection;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;

public class ImperfectionInjector {
    private final Random rng = new Random();
    private final ReactionDelay reactionDelay;
    private final AFKModule afkModule;

    public ImperfectionInjector() {
        this.reactionDelay = new ReactionDelay();
        this.afkModule = new AFKModule();
    }

    public boolean shouldDelay() { return reactionDelay.shouldDelay(); }
    public boolean isAFK() { return afkModule.isAFK(); }
    public long getAFKDurationMs() { return afkModule.getDurationMs(); }
}
