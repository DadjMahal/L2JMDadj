package com.aiplayer.behavior;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.core.BotProfile;

/**
 * Interlude C4 class combat presets (EP-5: the former ArcherPreset/FighterPreset/MagePreset/
 * HealerPreset/BufferPreset/PresetFactory one-class files folded into nested classes here).
 * Use {@link #forProfile(BotProfile)} to get the preset matching a bot's current class.
 */
public abstract class ClassPreset {
    protected final BotProfile profile;

    public ClassPreset(BotProfile profile) {
        this.profile = profile;
    }

    public abstract int selectSkill(int hpPercent, int mpPercent, double distance, boolean targetIsMob);
    public abstract double getOptimalDistance();
    public abstract boolean useShots();

    /** Was PresetFactory.forProfile — same dispatch, same fallbacks. */
    public static ClassPreset forProfile(BotProfile p) {
        if (p == null) return new Fighter(null);
        if (p.isHealer()) return new Healer(p);
        if (p.isBuffer()) return new Buffer(p);
        if (p.isMage()) return new Mage(p);
        if (p.isArcher()) return new Archer(p);
        return new Fighter(p);
    }

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

    /** Was ArcherPreset. */
    public static final class Archer extends ClassPreset {
        public Archer(BotProfile profile) { super(profile); }

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

    /** Was FighterPreset. */
    public static final class Fighter extends ClassPreset {
        private int comboStep = 0;

        public Fighter(BotProfile profile) { super(profile); }

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

    /** Was MagePreset. */
    public static final class Mage extends ClassPreset {
        private int nukeToggle = 0;

        public Mage(BotProfile profile) { super(profile); }

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

    /** Was HealerPreset. */
    public static final class Healer extends ClassPreset {
        public Healer(BotProfile profile) { super(profile); }

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

    /** Was BufferPreset. */
    public static final class Buffer extends ClassPreset {
        private int buffCycle = 0;

        public Buffer(BotProfile profile) { super(profile); }

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
}
