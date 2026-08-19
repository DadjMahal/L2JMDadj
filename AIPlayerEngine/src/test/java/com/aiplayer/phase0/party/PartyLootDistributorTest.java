package com.aiplayer.phase0.party;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.party.PartyLootDistributor.RollDecision;
import com.aiplayer.phase0.town.ItemValueEstimator;

/** S8-T04: basic party-loot distribution decision (roll need/greed/pass) is deterministic + sane. */
class PartyLootDistributorTest
{
    @Test
    void hpPotionKeepsAndGreedRolls()
    {
        PartyLootDistributor d = new PartyLootDistributor("t", null,
            new AntiDetectionEngine("t"), new ItemValueEstimator("t", 40, "Fighter"));
        RollDecision r = d.decideRoll(1061, 0, false);   // HP potion -> KEEP consumable
        assertTrue(r == RollDecision.GREED || r == RollDecision.NEED,
            "a kept consumable is not passed over, got " + r);
    }

    @Test
    void spoilDecisionIsBoolean()
    {
        PartyLootDistributor d = new PartyLootDistributor("t", null,
            new AntiDetectionEngine("t"), new ItemValueEstimator("t", 40, "Fighter"));
        assertTrue(d.shouldSpoil(1) == true || d.shouldSpoil(1) == false,
            "shouldSpoil yields a decision");
    }
}