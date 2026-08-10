package com.aiplayer.phase0.brain;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;

/**
 * Base class for Interlude C4 class combat presets.
 */
public abstract class ClassPreset {
    protected final BotProfile profile;

    public ClassPreset(BotProfile profile) {
        this.profile = profile;
    }

    public abstract int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob);
    public abstract double getOptimalDistance();
    public abstract boolean useShots();

    protected static final int POWER_STRIKE = 3;
    protected static final int MORTAL_BLOW = 16;
    protected static final int WIND_STRIKE = 1177;
    protected static final int BLAZE = 1178;
    protected static final int POWER_SHOT = 3;
    protected static final int HEAL = 1011;
    protected static final int BATTLE_HEAL = 1027;
    protected static final int MIGHT = 1068;
    protected static final int HASTE = 1086;
    protected static final int FOCUS = 1077;
    protected static final int DEATH_WHISPER = 1242;
}
