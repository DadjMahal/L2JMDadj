package com.aiplayer.behavior.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Fighter combat rotation: Power Strike -> Mortal Blow -> normal attack.
 * Uses soulshots. Melee range. No kiting.
 */
public class FighterRotation implements CombatRotation {
    private int comboStep = 0;

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance,
                           boolean targetIsMob, int targetHpPct) {
        if (distance > 60) return -1; // too far, will move closer first
        if (mpPercent < 10) return -1;  // conserve MP

        comboStep++;

        // Priority: Mortal Blow on cooldown cycle, Power Strike on alternate
        if (comboStep % 3 == 0) {
            if (mpPercent >= 15 && !isOnCooldown(SkillDatabase.get(16))) return 16; // Mortal Blow
        }
        if (comboStep % 2 == 0) {
            if (mpPercent >= 10 && !isOnCooldown(SkillDatabase.get(3))) return 3; // Power Strike
        }

        return -1; // normal attack
    }

    @Override
    public double getOptimalDistance() { return 40.0; }

    @Override
    public boolean useShots() { return true; }

    @Override
    public int getShotType() { return 1; } // soulshot

    @Override
    public boolean shouldKite() { return false; }

    @Override
    public int getFleeThreshold() { return 25; }

    @Override
    public void onSkillUsed(int skillId) {
        // comboStep already incremented in selectSkill
    }

    @Override
    public void reset() { comboStep = 0; }

    private boolean isOnCooldown(SkillInfo skill) {
        if (skill == null) return true;
        // Cooldown check is done by CooldownTracker externally;
        // this method is a safety fallback if called without tracker
        return false;
    }
}
