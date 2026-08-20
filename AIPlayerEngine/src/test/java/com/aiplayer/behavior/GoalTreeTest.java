package com.aiplayer.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.net.AIPlayer;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.learning.EmotionalState;

/**
 * Stream D goal-tree + personality-weighted combat tests (tasks 65, 68, 69, 73).
 *
 * <p>Proves the GoalTree schedules the right short-term goal by priority + personality weights,
 * and that CombatAI's defend threshold / engage distance are actually biased by personality +
 * emotion (not the pre-Stream-D constants).
 */
public class GoalTreeTest {

    @Test
    public void defaultGoalIsGrindXpForLowLevelPlayer() {
        // A fresh, healthy, non-bored player should pursue GRIND_XP (the leveling default).
        AIPlayer p = new AIPlayer("GoalBot", 1, 1, 0); // AGGRESSIVE personality (acct 1 % 6 = 1? index1=CAUTIOUS)
        GoalTree tree = p.getGoalTree();
        GoalTree.ShortTermGoal g = tree.selectActiveGoal();
        // Not defending (full HP) and not bored -> GRIND_XP should win.
        assertEquals(GoalTree.ShortTermGoal.GRIND_XP, g,
                "healthy low-level player should pursue GRIND_XP");
    }

    @Test
    public void surviveBeatsGrindWhenDefending() {
        // Force a defend condition by lowering HP in the packet logger.
        AIPlayer p = new AIPlayer("SurviveBot", 1, 1, 0);
        p.getCombatAI().getPacketLogger().setSelfObjectId(2);
        p.getCombatAI().getPacketLogger().setCurHp(10); // very low HP -> shouldDefend true
        GoalTree.ShortTermGoal g = p.getGoalTree().selectActiveGoal();
        assertEquals(GoalTree.ShortTermGoal.SURVIVE, g,
                "low-HP player must prioritize SURVIVE over GRIND_XP (priority 100 > 60)");
    }

    @Test
    public void exploreEligibleWhenBored() {
        // Drive the bot bored via repeated idle, then EXPLORE should be eligible.
        AIPlayer p = new AIPlayer("BoredBot", 4, 1, 0); // index 4 = EXPLORER (exploreWeight 2.0)
        for (int i = 0; i < 12; i++) {
            p.getEmotions().onIdle(); // boredom climbs 0.1/step; >0.6 after ~7 steps
        }
        p.getGoalTree().refresh();
        boolean exploreEligible = p.getGoalTree().getEligible().stream()
                .anyMatch(n -> n.goal == GoalTree.ShortTermGoal.EXPLORE);
        assertTrue(exploreEligible, "a bored bot should have EXPLORE eligible");
    }

    @Test
    public void aggressivePersonalityLowersDefendThreshold() {
        // AGGRESSIVE personality (combatWeight 1.8, safetyWeight 0.4) should defend LATER
        // (higher threshold) than CAUTIOUS (safetyWeight 1.9).
        AIPlayer aggressive = new AIPlayer("Agg", 0, 1, 0);  // acct 0 -> AGGRESSIVE
        AIPlayer cautious   = new AIPlayer("Cau", 1, 1, 0);  // acct 1 -> CAUTIOUS
        double aggThreshold = aggressive.getCombatAI().getEffectiveDefendThreshold();
        double cauThreshold = cautious.getCombatAI().getEffectiveDefendThreshold();
        assertTrue(aggThreshold > cauThreshold,
                "AGGRESSIVE defend threshold (" + aggThreshold + ") should be HIGHER (defends later) "
                        + "than CAUTIOUS (" + cauThreshold + ")");
    }

    @Test
    public void aggressivePersonalityReachesFarther() {
        // AGGRESSIVE should engage at a longer distance than CAUTIOUS.
        AIPlayer aggressive = new AIPlayer("Agg", 0, 1, 0);
        AIPlayer cautious   = new AIPlayer("Cau", 1, 1, 0);
        int aggRange = aggressive.getCombatAI().getEffectiveEngageDistance();
        int cauRange = cautious.getCombatAI().getEffectiveEngageDistance();
        assertTrue(aggRange > cauRange,
                "AGGRESSIVE engage distance (" + aggRange + ") should exceed CAUTIOUS (" + cauRange + ")");
    }

    @Test
    public void frustrationShortensEngageRange() {
        // After repeated deaths (frustration crosses the 0.6 -> FRUSTRATED threshold), the bot
        // should reach LESS far than when neutral. A single death (0.3) is not enough; three
        // deaths (0.9) push the emotion to FRUSTRATED.
        AIPlayer p = new AIPlayer("FrustrBot", 0, 1, 0); // AGGRESSIVE base
        int neutralRange = p.getCombatAI().getEffectiveEngageDistance();
        for (int i = 0; i < 3; i++) {
            p.getCombatAI().onDeath(); // each adds 0.3 frustration -> 0.9 after 3, >0.6 => FRUSTRATED
        }
        assertEquals(com.aiplayer.learning.EmotionalState.Emotion.FRUSTRATED,
                p.getEmotions().getCurrentEmotion(), "3 deaths must push emotion to FRUSTRATED");
        int frustratedRange = p.getCombatAI().getEffectiveEngageDistance();
        assertTrue(frustratedRange < neutralRange,
                "frustrated engage range (" + frustratedRange + ") should be < neutral ("
                        + neutralRange + ")");
    }
}
