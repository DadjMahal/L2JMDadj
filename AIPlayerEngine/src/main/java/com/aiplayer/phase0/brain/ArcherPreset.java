package com.aiplayer.phase0.brain;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;

public class ArcherPreset extends ClassPreset {
    public ArcherPreset(BotProfile profile) { super(profile); }

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob) {
        if (mpPercent < 10) return -1;
        return POWER_SHOT;
    }

    @Override
    public double getOptimalDistance() { return 650.0; }

    @Override
    public boolean useShots() { return true; }
}
