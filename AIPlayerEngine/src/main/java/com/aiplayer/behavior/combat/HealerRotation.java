package com.aiplayer.behavior.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Healer combat rotation: NEVER attacks. Prioritizes healing self and party.
 * Uses no shots. Stays at mid-range.
 */
public class HealerRotation implements CombatRotation {
    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance,
                           boolean targetIsMob, int targetHpPct) {
        // Healers do not attack
        if (targetIsMob) return -1;

        // Self-heal priority
        if (hpPercent < 50 && mpPercent > 20) {
            SkillInfo battleHeal = SkillDatabase.get(1027);
            if (battleHeal != null) return 1027;
        }

        // Sustain healing
        if (mpPercent > 15) {
            SkillInfo heal = SkillDatabase.get(1011);
            if (heal != null) return 1011;
        }

        return -1;
    }

    @Override
    public double getOptimalDistance() { return 400.0; }

    @Override
    public boolean useShots() { return false; }

    @Override
    public int getShotType() { return 0; }

    @Override
    public boolean shouldKite() { return false; }

    @Override
    public int getFleeThreshold() { return 40; } // Healers flee earlier to survive

    @Override
    public void onSkillUsed(int skillId) {}

    @Override
    public void reset() {}
}
