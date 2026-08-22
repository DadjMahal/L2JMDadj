package com.aiplayer.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.behavior.PersonalityBehavior.Knobs;
import com.aiplayer.learning.PersonalityProfile;
import com.aiplayer.learning.PersonalityProfile.Personality;

/**
 * PersonalityBehavior (EB-04) tests — the mapping that makes PersonalityProfile actually drive
 * behavior knobs: risk (surviveHpFraction), pace (combat/sight range scales), restock timing
 * and talkativeness. Locks the exact numbers so a future edit can't silently re-flatten every
 * bot to the same personality.
 */
class PersonalityBehaviorTest
{
    @Test
    void everyPersonalityHasKnobs()
    {
        for (Personality p : Personality.values())
        {
            Knobs k = PersonalityBehavior.knobs(p);
            assertNotNull(k, p + " must have knobs");
            assertTrue(k.surviveHpFraction > 0 && k.surviveHpFraction <= 0.5, p + " frac: " + k.surviveHpFraction);
            assertTrue(k.combatRangeScale > 0 && k.sightRangeScale > 0, p + " scales positive");
            assertTrue(k.talkativeness >= 0 && k.talkativeness <= 1.0, p + " talk: " + k.talkativeness);
        }
    }

    @Test
    void aggressiveIsRiskierThanCautious()
    {
        Knobs aggr = PersonalityBehavior.knobs(Personality.AGGRESSIVE);
        Knobs caut = PersonalityBehavior.knobs(Personality.CAUTIOUS);
        assertTrue(aggr.surviveHpFraction < caut.surviveHpFraction,
            "AGGRESSIVE fights longer (lower HP threshold) than CAUTIOUS");
    }

    @Test
    void cautiousEngagesCloserThanExplorer()
    {
        Knobs caution = PersonalityBehavior.knobs(Personality.CAUTIOUS);
        Knobs explorer = PersonalityBehavior.knobs(Personality.EXPLORER);
        assertTrue(caution.sightRangeScale < explorer.sightRangeScale,
            "CAUTIOUS stays close; EXPLORER hunts across zones");
    }

    @Test
    void merchantRestocksMuchEarlierThanNeutral()
    {
        Knobs merchant = PersonalityBehavior.knobs(Personality.MERCHANT);
        Knobs neutral = PersonalityBehavior.knobs(null);
        assertTrue(merchant.restockThreshold < neutral.restockThreshold,
            "MERCHANT restocks early (" + merchant.restockThreshold + ") vs neutral (" + neutral.restockThreshold + ")");
    }

    @Test
    void socialIsTalkative()
    {
        Knobs social = PersonalityBehavior.knobs(Personality.SOCIAL);
        Knobs defaultK = PersonalityBehavior.knobs(Personality.AGGRESSIVE);
        assertTrue(social.talkativeness > defaultK.talkativeness, "SOCIAL talks more than AGGRESSIVE");
    }

    @Test
    void nullPersonalityReturnsNeutral()
    {
        assertEquals(PersonalityBehavior.NEUTRAL, PersonalityBehavior.knobs(null));
    }

    @Test
    void deterministicPersonalityPerSeed()
    {
        PersonalityProfile a = PersonalityProfile.forSeed(123456789);
        PersonalityProfile b = PersonalityProfile.forSeed(123456789);
        assertEquals(a.getPersonality(), b.getPersonality(), "same seed -> same personality (deterministic)");
        assertNotNull(a.getPersonality(), "seed always maps to a real personality");
    }
}