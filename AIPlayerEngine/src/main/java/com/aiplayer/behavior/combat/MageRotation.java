package com.aiplayer.behavior.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Mage combat rotation: Wind Strike / Blaze nuke alternating.
 * Uses blessed spiritshots. Maintains 500+ range. MP management critical.
 */
public class MageRotation implements CombatRotation {
    private int nukeToggle = 0;
    private int consecutiveNukes = 0;
    private static final int MAX_CONSECUTIVE_NUKES = 5;
    private static final int MP_RESERVE_PCT = 20;

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance,
                           boolean targetIsMob, int targetHpPct) {
        // Mages need distance — if too close, prefer normal attack while repositioning
        if (distance < 100) return -1;

        // MP management: stop nuking if below reserve
        if (mpPercent < MP_RESERVE_PCT) return -1;

        // Consecutive nuke limit to simulate "casting fatigue" / human behavior
        if (consecutiveNukes >= MAX_CONSECUTIVE_NUKES) {
            consecutiveNukes = 0;
            return -1; // take a breath, normal attack (or wand hit)
        }

        // Alternate between Wind Strike and Blaze
        nukeToggle++;
        int skillId = (nukeToggle % 2 == 0) ? 1178 : 1177; // Blaze : Wind Strike

        SkillInfo skill = SkillDatabase.get(skillId);
        if (skill != null && mpPercent >= skill.mpCost / 5) { // rough MP check
            return skillId;
        }

        return -1;
    }

    @Override
    public double getOptimalDistance() { return 500.0; }

    @Override
    public boolean useShots() { return true; }

    @Override
    public int getShotType() { return 2; } // blessed spiritshot

    @Override
    public boolean shouldKite() { return true; }

    @Override
    public int getFleeThreshold() { return 20; }

    @Override
    public void onSkillUsed(int skillId) {
        consecutiveNukes++;
    }

    @Override
    public void reset() {
        nukeToggle = 0;
        consecutiveNukes = 0;
    }
}
