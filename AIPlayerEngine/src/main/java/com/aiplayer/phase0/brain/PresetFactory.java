package com.aiplayer.phase0.brain;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.phase0.cabinet.BotProfile;

public class PresetFactory {
    public static ClassPreset forProfile(BotProfile p) {
        if (p == null) return new FighterPreset(null);
        if (p.isHealer()) return new HealerPreset(p);
        if (p.isBuffer()) return new BufferPreset(p);
        if (p.isMage()) return new MagePreset(p);
        if (p.isArcher()) return new ArcherPreset(p);
        return new FighterPreset(p);
    }
}
