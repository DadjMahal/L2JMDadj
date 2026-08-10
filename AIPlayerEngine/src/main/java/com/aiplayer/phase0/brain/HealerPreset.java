package com.aiplayer.phase0.brain;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;

public class HealerPreset extends ClassPreset {
    public HealerPreset(BotProfile profile) { super(profile); }

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob) {
        if (hpPercent < 50 && mpPercent > 20) return BATTLE_HEAL;
        if (mpPercent > 10) return HEAL;
        return -1;
    }

    @Override
    public double getOptimalDistance() { return 400.0; }

    @Override
    public boolean useShots() { return false; }
}
