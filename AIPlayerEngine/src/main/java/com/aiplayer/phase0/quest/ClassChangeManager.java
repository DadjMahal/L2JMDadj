package com.aiplayer.phase0.quest;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;

/**
 * Automates 1st, 2nd, and 3rd class change quest chains.
 * Monitors level milestones and triggers the appropriate class-change
 * quest when the player reaches the required level.
 *
 * Interlude class changes:
 * - 1st class: Level 19-20 (varies by race)
 * - 2nd class: Level 35-39 (varies by class)
 * - 3rd class: Level 75-76 (very long quest chains, optional for Phase 0)
 *
 * Integration: LevelingPlanner queries this every level-up.
 * When a class change is available, it gets highest priority.
 */
public final class ClassChangeManager {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ClassChangeManager.class.getName());

    private final String accountName;
    private final QuestProgressTracker progress;
    private final QuestExecutor executor;
    private final AntiDetectionEngine anti;

    // Milestones
    private static final int FIRST_CLASS_LEVEL = 19;
    private static final int SECOND_CLASS_LEVEL = 38;
    private static final int THIRD_CLASS_LEVEL = 76;

    public ClassChangeManager(String accountName, QuestProgressTracker progress,
                              QuestExecutor executor, AntiDetectionEngine anti) {
        this.accountName = accountName;
        this.progress = progress;
        this.executor = executor;
        this.anti = anti;
    }

    /**
     * Check if a class change should be initiated.
     * Call on level-up and periodically in town.
     */
    public void checkAndStartClassChange(BotStateSnapshot self) {
        if (executor.isBusy()) return; // Already doing a quest

        int level = self.level;
        int classId = self.classId;

        // 1st class change
        if (level >= FIRST_CLASS_LEVEL && isFirstClass(classId)) {
            QuestInfo quest = QuestDatabase.getClassChangeQuest(classId);
            if (quest != null && !progress.isQuestCompleted(quest.questId) && !progress.isQuestActive(quest.questId)) {
                LOGGER.info("[ClassChange] " + accountName + " starting 1st class change: " + quest.name);
                executor.startQuest(quest.questId);
                return;
            }
        }

        // 2nd class change
        if (level >= SECOND_CLASS_LEVEL && isSecondClass(classId)) {
            QuestInfo quest = QuestDatabase.getClassChangeQuest(classId);
            if (quest != null && !progress.isQuestCompleted(quest.questId) && !progress.isQuestActive(quest.questId)) {
                LOGGER.info("[ClassChange] " + accountName + " starting 2nd class change: " + quest.name);
                executor.startQuest(quest.questId);
                return;
            }
        }

        // 3rd class change (Interlude) — Phase 0: only if already 75+
        if (level >= THIRD_CLASS_LEVEL && isThirdClassReady(classId)) {
            // Phase 0: 3rd class change quests are extremely long; we skip them
            // Phase 1 can enable Saga quests
            LOGGER.info("[ClassChange] " + accountName + " at 3rd class level (skipped in Phase 0)");
        }
    }

    /**
     * Get recommended preparation before class change:
     * - Pre-farm any required items
     * - Ensure enough adena for travel
     * - Restock consumables
     */
    public boolean isReadyForClassChange(BotStateSnapshot self) {
        // Phase 0: basic readiness check
        if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) < 90 || (self.mpMax > 0 ? self.mpCurrent * 100 / self.mpMax : 100) < 70) return false;
        if (self.isOverweight) return false;
        // Could add gear score check here
        return true;
    }

    /**
     * Get the class change quest ID for current class, or -1 if none.
     */
    public int getPendingClassChangeQuestId(BotStateSnapshot self) {
        QuestInfo quest = QuestDatabase.getClassChangeQuest(self.classId);
        return (quest != null && !progress.isQuestCompleted(quest.questId)) ? quest.questId : -1;
    }

    /**
     * Returns true if player has completed all class changes up to their level.
     */
    public boolean isClassChangeUpToDate(BotStateSnapshot self) {
        int level = self.level;
        int classId = self.classId;

        if (level >= FIRST_CLASS_LEVEL && isFirstClass(classId)) {
            QuestInfo q = QuestDatabase.getClassChangeQuest(classId);
            if (q != null && !progress.isQuestCompleted(q.questId)) return false;
        }
        if (level >= SECOND_CLASS_LEVEL && isSecondClass(classId)) {
            QuestInfo q = QuestDatabase.getClassChangeQuest(classId);
            if (q != null && !progress.isQuestCompleted(q.questId)) return false;
        }
        return true;
    }

    // ================================================================
    // CLASS ID HELPERS (Interlude class IDs)
    // ================================================================

    private boolean isFirstClass(int classId) {
        // First classes are IDs 0-54 (before 1st change) and 88+ for special
        // Simplified: base classes are 0-54, first change classes start at 88
        return classId >= 0 && classId <= 54;
    }

    private boolean isSecondClass(int classId) {
        // 2nd change classes: 88-117 approx (varies by chronicle)
        // Actually in Interlude, 1st classes are 0-54, 2nd are 88+, 3rd are 132+
        // For Phase 0 we use a simpler heuristic:
        // If level >= 38 and class is still a 1st class (0-54), need 2nd change
        return classId >= 0 && classId <= 54;
    }

    private boolean isThirdClassReady(int classId) {
        // 3rd class available at 76 for classes that have it
        return classId >= 88 && classId < 132;
    }
}