package com.aiplayer.behavior.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.aiplayer.behavior.party.PartyRole.Positioning;


/** S10-T08: locks PartyRole (flipped MODE:PARTIAL -> COMPLETE). */
class PartyRoleTest
{
    @Test
    void fromClassIdMapsAllArchetypes()
    {
        assertEquals(PartyRole.TANK, PartyRole.fromClassId(5));
        assertEquals(PartyRole.TANK, PartyRole.fromClassId(90));
        assertEquals(PartyRole.HEALER, PartyRole.fromClassId(97));
        assertEquals(PartyRole.RANGED_DAMAGE, PartyRole.fromClassId(93));
        assertEquals(PartyRole.SUPPORT, PartyRole.fromClassId(107));
        assertEquals(PartyRole.DAMAGE_DEALER, PartyRole.fromClassId(43), "unknown ids default to DD");
    }

    @Test
    void positionsAndFlagsAreSensible()
    {
        assertTrue(PartyRole.TANK.shouldPull);
        assertFalse(PartyRole.DAMAGE_DEALER.shouldPull);
        assertTrue(PartyRole.HEALER.isRanged);
        assertFalse(PartyRole.TANK.isRanged);
        assertEquals(Positioning.FRONT_LINE, PartyRole.TANK.preferredPosition);
        assertEquals(Positioning.BACK_LINE, PartyRole.HEALER.preferredPosition);
    }

    @Test
    void abilityListsAreImmutable()
    {
        assertTrue(PartyRole.TANK.abilities.contains(PartyRole.Ability.TAUNT));
        assertThrowsUnmodifiable(PartyRole.TANK);
    }

    private static void assertThrowsUnmodifiable(PartyRole role)
    {
        try
        {
            role.abilities.add(PartyRole.Ability.KITE);
            throw new AssertionError("abilities list must be immutable");
        }
        catch (UnsupportedOperationException expected)
        {
            // good
        }
    }
}