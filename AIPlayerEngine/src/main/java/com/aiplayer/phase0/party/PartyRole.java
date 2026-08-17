package com.aiplayer.phase0.party;

/** MODE: COMPLETE (re-verified 2026-08-17, S10-T08). Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines party roles for AI Player coordination.
 * Each role has tactical responsibilities, target priorities,
 * and positioning preferences during combat.
 *
 * Roles are assigned based on class archetype but can be
 * overridden by party leader strategy or player preference.
 */
public enum PartyRole {

    TANK(
        "Tank",
        Arrays.asList(Ability.TAUNT, Ability.DAMAGE_REDUCTION, Ability.CROWD_CONTROL),
        Positioning.FRONT_LINE,
        TargetPriority.HIGHEST_THREAT,
        true,
        false
    ),

    HEALER(
        "Healer",
        Arrays.asList(Ability.HEAL, Ability.BUFF, Ability.CLEANSE),
        Positioning.BACK_LINE,
        TargetPriority.LOWEST_HP_ALLY,
        false,
        true
    ),

    DAMAGE_DEALER(
        "Damage Dealer",
        Arrays.asList(Ability.DAMAGE_BURST, Ability.DAMAGE_SUSTAINED),
        Positioning.FLANK,
        TargetPriority.LOWEST_HP_ENEMY,
        false,
        false
    ),

    RANGED_DAMAGE(
        "Ranged Damage",
        Arrays.asList(Ability.DAMAGE_BURST, Ability.KITE),
        Positioning.BACK_LINE,
        TargetPriority.LOWEST_HP_ENEMY,
        false,
        true
    ),

    SUPPORT(
        "Support",
        Arrays.asList(Ability.BUFF, Ability.DEBUFF, Ability.CROWD_CONTROL),
        Positioning.MID_LINE,
        TargetPriority.CALLER_TARGET,
        false,
        true
    ),

    LEADER(
        "Party Leader",
        Arrays.asList(Ability.CALL_TARGET, Ability.BUFF, Ability.TACTICAL_COMMAND),
        Positioning.MID_LINE,
        TargetPriority.CALLER_TARGET,
        true,
        false
    );

    public final String displayName;
    public final List<Ability> abilities;
    public final Positioning preferredPosition;
    public final TargetPriority targetPriority;
    public final boolean shouldPull;
    public final boolean isRanged;

    PartyRole(String displayName, List<Ability> abilities,
              Positioning preferredPosition, TargetPriority targetPriority,
              boolean shouldPull, boolean isRanged) {
        this.displayName = displayName;
        this.abilities = Collections.unmodifiableList(abilities);
        this.preferredPosition = preferredPosition;
        this.targetPriority = targetPriority;
        this.shouldPull = shouldPull;
        this.isRanged = isRanged;
    }

    public static PartyRole fromClassId(int classId) {
        switch (classId) {
            case 5: case 6: case 90: case 91: case 99: case 100:
                return TANK;
            case 97: case 98: case 105: case 106: case 112: case 113: case 115: case 116:
                return HEALER;
            case 93: case 94: case 101: case 102: case 108: case 109:
                return RANGED_DAMAGE;
            case 107: case 114:
                return SUPPORT;
            default:
                return DAMAGE_DEALER;
        }
    }

    public enum Ability {
        TAUNT, DAMAGE_REDUCTION, CROWD_CONTROL,
        HEAL, BUFF, CLEANSE,
        DAMAGE_BURST, DAMAGE_SUSTAINED, KITE,
        DEBUFF, CALL_TARGET, TACTICAL_COMMAND
    }

    public enum Positioning {
        FRONT_LINE(0),
        MID_LINE(300),
        BACK_LINE(600),
        FLANK(400);

        public final int preferredDistance;
        Positioning(int preferredDistance) {
            this.preferredDistance = preferredDistance;
        }
    }

    public enum TargetPriority {
        HIGHEST_THREAT,
        LOWEST_HP_ALLY,
        LOWEST_HP_ENEMY,
        CALLER_TARGET,
        SELF_DEFENSE
    }
}
