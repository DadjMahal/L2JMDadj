package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Buffer combat rotation: Buff rotation (Might -> Haste -> Focus -> Death Whisper).
 * Does not attack. Follows party leader. Uses no shots.
 */
public class BufferRotation implements CombatRotation {
    private int buffCycle = 0;
    private static final int[] BUFF_ROTATION = {
        1068,  // Might
        1086,  // Haste
        1077,  // Focus
        1242   // Death Whisper
    };

    @Override
    public int selectSkill(int hpPercent, int mpPercent, double distance,
                           boolean targetIsMob, int targetHpPct) {
        // Buffers do not attack
        if (targetIsMob) return -1;

        if (mpPercent < 15) return -1;

        // Cycle through buffs
        int skillId = BUFF_ROTATION[buffCycle % BUFF_ROTATION.length];
        buffCycle++;

        SkillInfo skill = SkillDatabase.get(skillId);
        if (skill != null && mpPercent >= skill.mpCost / 5) {
            return skillId;
        }

        return -1;
    }

    @Override
    public double getOptimalDistance() { return 200.0; }

    @Override
    public boolean useShots() { return false; }

    @Override
    public int getShotType() { return 0; }

    @Override
    public boolean shouldKite() { return false; }

    @Override
    public int getFleeThreshold() { return 35; }

    @Override
    public void onSkillUsed(int skillId) {}

    @Override
    public void reset() { buffCycle = 0; }
}
