package com.aiplayer.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.behavior.LongTermGoalsAI;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.net.AIPlayer;

/**
 * Stream D feedback wiring tests (tasks 70-76).
 *
 * <p>Proves that the previously-dead emotion/learning subsystems are now genuinely driven by
 * combat + quest outcomes. Before Stream D, CombatAI.onKill/onDeath/onLevelUp only logged and the
 * advanced/ + neural/ classes were instantiated-but-never-fed. These tests assert the real
 * feedback chain: outcome -&gt; EmotionalState mutation + ReinforcementEngine reward -&gt;
 * PatternMemory record + AdaptiveLearner counter.
 */
public class StreamDFeedbackTest {

    private AIPlayer newPlayer() {
        return new AIPlayer("StreamDBot", 3, 1, 0);
    }

    @Test
    public void killDrivesEmotionAndLearning() {
        AIPlayer p = newPlayer();
        double frustrationBefore = p.getEmotions().getFrustrationLevel();
        int learnedBefore = p.getAdaptiveLearner().getCombatActionsLearned();

        // A kill with XP gained should bump excitement, record a reward, and learn a combat action.
        p.getCombatAI().onKill("Wolf", 105L);

        assertTrue(p.getEmotions().getExcitementLevel() > 0.0,
                "kill must raise excitement above 0");
        assertEquals(learnedBefore + 1, p.getAdaptiveLearner().getCombatActionsLearned(),
                "kill must increment the combat-learned counter");
        // The reward was recorded under "combat:Wolf" in PatternMemory (DeepLearningCore).
        assertTrue(p.getDeepLearning().getMemory().size() > 0,
                "PatternMemory must hold at least one pattern after a kill");
        // Frustration should NOT rise from a kill (it's a positive event).
        assertTrue(p.getEmotions().getFrustrationLevel() <= frustrationBefore,
                "a kill must not raise frustration");
    }

    @Test
    public void deathRaisesFrustrationAndPenalizesLearning() {
        AIPlayer p = newPlayer();
        double frustrationBefore = p.getEmotions().getFrustrationLevel();
        int learnedBefore = p.getAdaptiveLearner().getCombatActionsLearned();

        p.getCombatAI().onDeath();

        assertTrue(p.getEmotions().getFrustrationLevel() > frustrationBefore,
                "death must raise frustration");
        assertEquals(learnedBefore + 1, p.getAdaptiveLearner().getCombatActionsLearned(),
                "death must record a (negative) learning action");
    }

    @Test
    public void levelUpRaisesConfidenceAndAdvancesLongTermGoal() {
        AIPlayer p = newPlayer();
        double confidenceBefore = p.getEmotions().getConfidenceLevel();
        int goalProgressBefore = p.getLongTermGoals().getGoalProgress(LongTermGoalsAI.Goal.MAX_LEVEL);

        p.getCombatAI().onLevelUp(2);

        assertTrue(p.getEmotions().getConfidenceLevel() > confidenceBefore,
                "level-up must raise confidence");
        assertEquals(2, p.getLevel(), "onLevelUp must set the player level");
        assertEquals(goalProgressBefore + 1, p.getLongTermGoals().getGoalProgress(LongTermGoalsAI.Goal.MAX_LEVEL),
                "level-up must advance the MAX_LEVEL long-term goal");
    }

    @Test
    public void questCompletionDrivesEmotionAndReward() {
        AIPlayer p = newPlayer();
        // First get the player frustrated via a death, so a quest completion has something to lower.
        p.getCombatAI().onDeath();
        double frustrationBefore = p.getEmotions().getFrustrationLevel();
        assertTrue(frustrationBefore > 0.0, "precondition: death must raise frustration above 0");
        double confidenceBefore = p.getEmotions().getConfidenceLevel();
        int questLearnedBefore = p.getAdaptiveLearner().getQuestActionsLearned();

        p.getQuestAI().onQuestCompleted("Q00101_SwordOfSolidarity");

        assertTrue(p.getEmotions().getConfidenceLevel() > confidenceBefore,
                "quest completion must raise confidence");
        assertTrue(p.getEmotions().getFrustrationLevel() < frustrationBefore,
                "quest completion must lower frustration after a death");
        assertEquals(questLearnedBefore + 1, p.getAdaptiveLearner().getQuestActionsLearned(),
                "quest completion must increment the quest-learned counter");
    }

    @Test
    public void longTermGoalSelectionUsesLevelAndCastle() {
        AIPlayer p = newPlayer();
        // A fresh level-1 player with no castle should pursue MAX_LEVEL.
        LongTermGoalsAI.Goal g = p.getLongTermGoals().getPrimaryGoal(1, 0, false);
        assertEquals(LongTermGoalsAI.Goal.MAX_LEVEL, g,
                "low-level non-noblesse player should pursue MAX_LEVEL");

        // A high-level player should pivot toward guild leadership / raid achievement.
        LongTermGoalsAI.Goal high = p.getLongTermGoals().getPrimaryGoal(81, 0, false);
        assertEquals(LongTermGoalsAI.Goal.ACHIEVEMENT_RAID, high,
                "level>80 player should pursue ACHIEVEMENT_RAID");
    }

    @Test
    public void personalityIsAssignedFromAccountId() {
        // AIPlayer assigns personality by accountId % 6.
        // PersonalityProfile.Personality.values() = {AGGRESSIVE, CAUTIOUS, SOCIAL, MERCHANT, EXPLORER, COMPLETIONIST}
        // Account 3 -> index 3 -> MERCHANT.
        AIPlayer p = newPlayer();
        assertNotNull(p.getPersonality(), "player must have a personality");
        assertEquals(com.aiplayer.advanced.PersonalityProfile.Personality.MERCHANT,
                p.getPersonality().getPersonality(),
                "accountId 3 % 6 should map to MERCHANT (weights prioritise trade)");
        // And the merchant weights must actually favour trade.
        assertTrue(p.getPersonality().getTradeWeight() > p.getPersonality().getCombatWeight(),
                "MERCHANT personality should weight trade above combat");
    }
}
