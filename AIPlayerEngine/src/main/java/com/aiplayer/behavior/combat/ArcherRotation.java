package com.aiplayer.behavior.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Archer combat rotation: Power Shot at optimal range, kiting.
 * Uses soulshots. Maintains ~650 range.
 */
public class ArcherRotation implements CombatRotation {
    private int shotsWithoutSkill = 0;
    private static final int SHOTS_BETWEEN_SKILLS = 3;

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance,
                           boolean targetIsMob, int targetHpPct) {
        // Archers want distance — if target is too close, kite instead of skill
        if (distance < 200) return -1;

        // Only use Power Shot every few attacks to conserve MP and simulate human pacing
        shotsWithoutSkill++;
        if (shotsWithoutSkill < SHOTS_BETWEEN_SKILLS) return -1;

        SkillInfo skill = SkillDatabase.get(56); // Power Shot
        if (skill != null && mpPercent >= 10) {
            shotsWithoutSkill = 0;
            return 56;
        }

        return -1;
    }

    @Override
    public double getOptimalDistance() { return 650.0; }

    @Override
    public boolean useShots() { return true; }

    @Override
    public int getShotType() { return 1; } // soulshot

    @Override
    public boolean shouldKite() { return true; }

    @Override
    public int getFleeThreshold() { return 30; }

    @Override
    public void onSkillUsed(int skillId) {
        shotsWithoutSkill = 0;
    }

    @Override
    public void reset() {
        shotsWithoutSkill = 0;
    }
}
