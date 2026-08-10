package com.aiplayer.phase0.brain;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;

public class MagePreset extends ClassPreset {
    private int nukeToggle = 0;

    public MagePreset(BotProfile profile) { super(profile); }

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob) {
        if (mpPercent < 15) return -1;
        if (distance > 600) return -1;
        nukeToggle++;
        return (nukeToggle % 2 == 0) ? BLAZE : WIND_STRIKE;
    }

    @Override
    public double getOptimalDistance() { return 500.0; }

    @Override
    public boolean useShots() { return true; }
}
