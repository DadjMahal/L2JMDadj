package com.aiplayer.behavior;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */


public class BufferPreset extends ClassPreset {
    private int buffCycle = 0;

    public BufferPreset(BotProfile profile) { super(profile); }

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob) {
        if (mpPercent < 15) return -1;
        buffCycle++;
        if (buffCycle % 3 == 0) return MIGHT;
        if (buffCycle % 3 == 1) return HASTE;
        return FOCUS;
    }

    @Override
    public double getOptimalDistance() { return 200.0; }

    @Override
    public boolean useShots() { return false; }
}
