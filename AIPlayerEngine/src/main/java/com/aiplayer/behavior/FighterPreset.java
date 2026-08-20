package com.aiplayer.behavior;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */


public class FighterPreset extends ClassPreset {
    private int comboStep = 0;

    public FighterPreset(BotProfile profile) { super(profile); }

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob) {
        if (mpPercent < 10) return -1;
        comboStep++;
        if (comboStep % 4 == 0) return MORTAL_BLOW;
        if (comboStep % 2 == 0) return POWER_STRIKE;
        return -1;
    }

    @Override
    public double getOptimalDistance() { return 40.0; }

    @Override
    public boolean useShots() { return true; }
}
