package com.aiplayer.phase0.combat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;

/**
 * Factory for creating the appropriate CombatRotation based on class ID.
 */
public final class RotationFactory {

    public static CombatRotation forClassId(int classId) {
        // Healers
        if (isHealer(classId)) return new HealerRotation();
        // Buffers
        if (isBuffer(classId)) return new BufferRotation();
        // Mages
        if (isMage(classId)) return new MageRotation();
        // Archers
        if (isArcher(classId)) return new ArcherRotation();
        // Default: Fighter
        return new FighterRotation();
    }

    public static CombatRotation forProfile(BotProfile profile) {
        if (profile == null) return new FighterRotation();
        return forClassId(profile.getClassCurrent());
    }

    private static boolean isHealer(int classId) {
        return classId == 25 || classId == 29 || classId == 15 || classId == 17;
    }

    private static boolean isBuffer(int classId) {
        return classId == 16 || classId == 21 || classId == 30 || classId == 32;
    }

    private static boolean isMage(int classId) {
        return (classId >= 10 && classId <= 14) ||
               (classId >= 26 && classId <= 29) ||
               classId == 11 || classId == 12 || classId == 43;
    }

    private static boolean isArcher(int classId) {
        return classId == 18 || classId == 22 || classId == 35;
    }

    private RotationFactory() {}
}
