package com.aiplayer.behavior.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

/**
 * Interface for class-specific combat rotations.
 * Implementations decide which skill to use next based on game state.
 */
public interface CombatRotation {

    /**
     * Select the next skill to use, or -1 for normal attack.
     *
     * @param hpPercent    AI Player's current HP (0-100)
     * @param mpPercent    AI Player's current MP (0-100)
     * @param distance     Distance to current target in game units
     * @param targetIsMob  True if target is a mob, false if player
     * @param targetHpPct  Target's HP percentage (0-100), or -1 if unknown
     * @return Skill ID to use, or -1 for normal physical attack
     */
    int selectSkill(int hpPercent, int mpPercent, double distance,
                    boolean targetIsMob, int targetHpPct);

    /**
     * Optimal engagement distance for this class.
     */
    double getOptimalDistance();

    /**
     * Whether this class should use soulshots/spiritshots.
     */
    boolean useShots();

    /**
     * Shot type: 0 = none, 1 = soulshot, 2 = blessed spiritshot
     */
    int getShotType();

    /**
     * Whether this class should kite (maintain distance) instead of melee.
     */
    boolean shouldKite();

    /**
     * Minimum HP percentage before prioritizing self-heal over offense.
     */
    int getFleeThreshold();

    /**
     * Called after a skill is successfully used — implementations may update internal state.
     */
    void onSkillUsed(int skillId);

    /**
     * Reset internal combo/state counters (e.g., after combat ends).
     */
    void reset();
}
