package com.aiplayer.phase0.quest;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.phase0.GameStateMirror;
import com.aiplayer.phase0.GameStateMirror.BotStateSnapshot;
import com.aiplayer.phase0.humanize.AntiDetectionEngine;
import com.aiplayer.phase0.humanize.TimingJitter;
import com.aiplayer.phase0.humanize.SessionVariance;

import java.util.*;

/**
 * High-level leveling strategy orchestrator.
 * Decides quest vs grind, zone transitions, and session goals.
 *
 * Core loop every 30s:
 * 1. Check class change priority (ClassChangeManager)
 * 2. Evaluate active quests — continue if efficient
 * 3. Find new quests if questing is better than grinding
 * 4. Recommend zone if grinding is better
 * 5. Set session XP target based on stamina fingerprint
 *
 * Respects:
 * - BehavioralFingerprint (risk tolerance affects danger zone selection)
 * - SessionVariance (fatigue reduces ambition, shorter sessions = fewer long quests)
 * - AntiDetectionEngine (human-like decision delays)
 */
public final class LevelingPlanner {

    public enum ActivityMode {
        QUESTING,       // Prioritize quest execution
        GRINDING,       // Pure farm in recommended zone
        MIXED,          // Grind while waiting for quest mob spawns
        TOWN_RESTOCK,   // Need consumables before continuing
        CLASS_CHANGE,   // Mandatory class change in progress
        IDLE            // No plan (should not happen)
    }

    private final String accountName;
    private final QuestProgressTracker progress;
    private final QuestExecutor executor;
    private final ClassChangeManager classChange;
    private final AntiDetectionEngine anti;
    private final SessionVariance session;

    private ActivityMode currentMode = ActivityMode.IDLE;
    private long lastPlanTime = 0;
    private long nextPlanTime = 0;
    private String currentZoneName = null;
    private int sessionXpTarget = 0;
    private int sessionXpGained = 0;

    // Minimum delay between re-planning
    private static final long PLAN_INTERVAL_MS = 30000;

    public LevelingPlanner(String accountName, QuestProgressTracker progress,
                           QuestExecutor executor, ClassChangeManager classChange,
                           AntiDetectionEngine anti, SessionVariance session) {
        this.accountName = accountName;
        this.progress = progress;
        this.executor = executor;
        this.classChange = classChange;
        this.anti = anti;
        this.session = session;
    }

    /**
     * Main tick — call every 5s from AIBrain.
     */
    public void tick(BotStateSnapshot self) {
        long now = System.currentTimeMillis();

        // Update XP tracking
        sessionXpGained = self.xpGainedThisSession;

        // Re-plan periodically or when mode is idle
        if (now >= nextPlanTime || currentMode == ActivityMode.IDLE) {
            replan(self);
            nextPlanTime = now + PLAN_INTERVAL_MS + anti.getDelay(TimingJitter.ActionContext.IDLE_PAUSE);
        }

        // Execute current mode logic
        switch (currentMode) {
            case CLASS_CHANGE:
                // ClassChangeManager already started quest; executor handles it
                if (!executor.isBusy()) {
                    currentMode = ActivityMode.IDLE; // Will replan next tick
                }
                break;

            case QUESTING:
                if (!executor.isBusy()) {
                    // Quest finished or failed — replan immediately
                    currentMode = ActivityMode.IDLE;
                    nextPlanTime = now;
                }
                break;

            case GRINDING:
                // Grinding is handled by CombatAI + MovementController
                // We just monitor if we should switch to questing
                if (shouldSwitchToQuest(self)) {
                    currentMode = ActivityMode.IDLE;
                    nextPlanTime = now;
                }
                break;

            case MIXED:
                // Mixed: grind with quest awareness
                if (!executor.isBusy() && !hasEfficientActiveQuest(self)) {
                    currentMode = ActivityMode.IDLE;
                }
                break;

            case TOWN_RESTOCK:
                // TownBehaviorEngine handles restock; we wait
                if (self.isInTown && (self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) > 90 && (self.mpMax > 0 ? self.mpCurrent * 100 / self.mpMax : 100) > 80 && !self.isOverweight) {
                    currentMode = ActivityMode.IDLE;
                    nextPlanTime = now;
                }
                break;

            case IDLE:
                // Will replan on next tick
                break;
        }
    }

    /**
     * Force re-evaluation of strategy.
     */
    public void forceReplan() {
        nextPlanTime = System.currentTimeMillis();
    }

    // ================================================================
    // REPLANNING LOGIC
    // ================================================================

    private void replan(BotStateSnapshot self) {
        long now = System.currentTimeMillis();
        lastPlanTime = now;

        // 1. Class change check (highest priority)
        if (!classChange.isClassChangeUpToDate(self)) {
            if (classChange.isReadyForClassChange(self)) {
                classChange.checkAndStartClassChange(self);
                currentMode = ActivityMode.CLASS_CHANGE;
                return;
            } else {
                currentMode = ActivityMode.TOWN_RESTOCK;
                return;
            }
        }

        // 2. Evaluate active quests
        Integer priorityQuest = progress.getCurrentPriorityQuest();
        if (priorityQuest != null) {
            QuestInfo quest = QuestDatabase.getById(priorityQuest);
            if (quest != null && executor.getState() == QuestExecutor.ExecutorState.IDLE) {
                // Resume / start execution
                if (!progress.isQuestActive(priorityQuest)) {
                    progress.startQuest(priorityQuest);
                }
                executor.startQuest(priorityQuest);
                currentMode = ActivityMode.QUESTING;
                return;
            } else if (executor.isBusy()) {
                currentMode = ActivityMode.QUESTING;
                return;
            }
        }

        // 3. Find new quest vs grind decision
        List<QuestInfo> available = QuestDatabase.findAvailable(
            self.level, self.raceId, self.classId, progress.getCompletedQuestIds());

        // Filter out quests we already completed or have active
        available.removeIf(q -> progress.isQuestCompleted(q.questId) || progress.isQuestActive(q.questId));

        // Check if any active quest zone matches our current zone
        boolean sameZoneQuest = hasActiveQuestInCurrentZone(self);

        QuestRewardEvaluator.ActivityType recommendation = QuestRewardEvaluator.recommendActivity(
            available, self.level, self.x, self.y, sameZoneQuest, 0.5, true); // gearScore placeholder

        if (recommendation == QuestRewardEvaluator.ActivityType.QUEST && !available.isEmpty()) {
            // Pick best quest
            QuestInfo best = available.stream()
                .max(Comparator.comparingDouble(q ->
                    QuestRewardEvaluator.scoreQuest(q, self.level, self.x, self.y, sameZoneQuest, 0.5, true)))
                .orElse(null);

            if (best != null) {
                executor.startQuest(best.questId);
                currentMode = ActivityMode.QUESTING;
                return;
            }
        }

        // 4. Grinding mode
        currentMode = (recommendation == QuestRewardEvaluator.ActivityType.MIXED)
            ? ActivityMode.MIXED : ActivityMode.GRINDING;

        // Recommend zone for grinding
        ZoneRecommender.ZoneInfo zone = ZoneRecommender.recommendZone(
            self.level, isMage(self.classId), isRanged(self.classId),
            true, self.x, self.y, progress.getActiveQuestIds());

        if (zone != null) {
            currentZoneName = zone.name;
        }
    }

    // ================================================================
    // ZONE / MODE QUERIES
    // ================================================================

    public ActivityMode getCurrentMode() {
        return currentMode;
    }

    public String getRecommendedZone() {
        return currentZoneName;
    }

    public boolean shouldSwitchToQuest(BotStateSnapshot self) {
        // If we're grinding and a high-value quest becomes available in same zone
        List<QuestInfo> available = QuestDatabase.findAvailable(
            self.level, self.raceId, self.classId, progress.getCompletedQuestIds());
        available.removeIf(q -> progress.isQuestCompleted(q.questId) || progress.isQuestActive(q.questId));

        for (QuestInfo q : available) {
            double score = QuestRewardEvaluator.scoreQuest(q, self.level, self.x, self.y, true, 0.5, true);
            if (score > 100) return true; // Very good quest appeared
        }
        return false;
    }

    public boolean hasEfficientActiveQuest(BotStateSnapshot self) {
        Set<Integer> active = progress.getActiveQuestIds();
        for (int qid : active) {
            QuestInfo q = QuestDatabase.getById(qid);
            if (q != null) {
                double score = QuestRewardEvaluator.scoreQuest(q, self.level, self.x, self.y, true, 0.5, true);
                if (score > 40) return true;
            }
        }
        return false;
    }

    public boolean hasActiveQuestInCurrentZone(BotStateSnapshot self) {
        Set<Integer> active = progress.getActiveQuestIds();
        for (int qid : active) {
            QuestInfo q = QuestDatabase.getById(qid);
            if (q != null && q.zoneName != null && q.zoneName.equalsIgnoreCase(currentZoneName)) {
                return true;
            }
        }
        return false;
    }

    /**
n     * Get session XP target based on level and stamina.
     */
    public int getSessionXpTarget(int level, double stamina) {
        // Interlude XP table is roughly exponential
        // Level 1-20: ~200k total, 20-40: ~2M, 40-60: ~10M, 60-76: ~30M
        // Per-session target: enough for 10-20% of a level
        double levelXp = getLevelXpRequirement(level);
        double targetPercent = 0.10 + stamina * 0.15; // 10-25% per session
        return (int) (levelXp * targetPercent);
    }

    public int getSessionXpGained() {
        return sessionXpGained;
    }

    public boolean isSessionGoalMet() {
        return sessionXpGained >= sessionXpTarget;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private boolean isMage(int classId) {
        // Mystic classes: Human Mystic(10), Elf Mystic(25), Dark Mystic(38), Orc Shaman(49)
        return classId == 10 || classId == 11 || classId == 25 || classId == 26 ||
               classId == 38 || classId == 39 || classId == 49 || classId == 50;
    }

    private boolean isRanged(int classId) {
        // Rogue/Scout/Assassin/Raider and their upgrades, plus some archers
        return classId == 2 || classId == 3 || classId == 7 || classId == 8 ||
               classId == 19 || classId == 20 || classId == 22 || classId == 23 ||
               classId == 32 || classId == 33 || classId == 35 || classId == 36 ||
               classId == 44 || classId == 45 || classId == 47 || classId == 48;
    }

    private double getLevelXpRequirement(int level) {
        // Simplified Interlude XP curve (cumulative XP to reach this level)
        // These are rough estimates for session target calculation
        if (level <= 1) return 0;
        if (level <= 10) return (level - 1) * 5000;
        if (level <= 20) return 45000 + (level - 10) * 20000;
        if (level <= 30) return 245000 + (level - 20) * 80000;
        if (level <= 40) return 1045000 + (level - 30) * 250000;
        if (level <= 50) return 3545000 + (level - 40) * 600000;
        if (level <= 60) return 9545000 + (level - 50) * 1500000;
        if (level <= 70) return 24545000 + (level - 60) * 3500000;
        return 59545000 + (level - 70) * 8000000;
    }

    public String getStatusReport() {
        return String.format(
            "LevelingPlanner[%s: mode=%s zone=%s xp=%d/%d quests=%d/%d]",
            accountName, currentMode, currentZoneName,
            sessionXpGained, sessionXpTarget,
            progress.getActiveQuestIds().size(), progress.getCompletedQuestIds().size()
        );
    }
}
