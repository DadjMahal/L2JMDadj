package com.aiplayer.behavior.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.aiplayer.behavior.BotPlayController.PlayContext;
import com.aiplayer.behavior.quest.QuestProgressTracker.ActiveQuestState;

/** S3-T05: quest stepIndex persistence tracker is pure and locked (feeds PlayContext). */
class QuestProgressTrackerTest
{
    @Test
    void startQuestResetsState()
    {
        QuestProgressTracker t = new QuestProgressTracker("t");
        t.startQuest(21);
        assertTrue(t.isQuestActive(21), "quest becomes active");
        QuestProgressTracker.ActiveQuestState s = t.getActiveState(21);
        assertEquals(0, s.currentStepIndex, "fresh quest starts at first step");
        assertEquals(0, s.stepProgressCount, "no kills/items collected yet");
        assertFalse(t.isQuestActive(99), "untracked quest is not active");
    }

    @Test
    void progressAndAdvanceStep()
    {
        QuestProgressTracker t = new QuestProgressTracker("t");
        t.startQuest(21);
        t.incrementStepProgress(21, 3);
        t.incrementStepProgress(21, 2);
        assertEquals(5, t.getActiveState(21).stepProgressCount, "kill/collect counters accumulate");
        t.advanceStep(21);
        assertEquals(1, t.getActiveState(21).currentStepIndex, "stepIndex persists through advance");
    }

    @Test
    void completeAndAbandonClear()
    {
        QuestProgressTracker t = new QuestProgressTracker("t");
        t.startQuest(21);
        t.completeQuest(21);
        assertFalse(t.isQuestActive(21), "complete clears the active journal");
        t.startQuest(22);
        t.abandonQuest(22);
        assertFalse(t.isQuestActive(22), "abandon clears the active journal");
    }
}