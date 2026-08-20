package com.aiplayer.behavior.quest;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.core.GameStateMirror.BotStateSnapshot;
import com.aiplayer.behavior.humanize.AntiDetectionEngine;
import com.aiplayer.behavior.humanize.TimingJitter;
import com.aiplayer.behavior.movement.MovementController;
import com.aiplayer.behavior.AIBrain;
import com.aiplayer.behavior.combat.CombatAI;
import com.aiplayer.behavior.inventory.InventoryTracker;
import com.aiplayer.behavior.lifecycle.DeathHandler;
import com.aiplayer.behavior.town.TownBehaviorEngine;
import com.aiplayer.core.BotSnapshot;
import com.aiplayer.core.GameStateMirror;
import com.aiplayer.behavior.quest.QuestInfo;
import com.aiplayer.behavior.quest.QuestProgressTracker;
import com.aiplayer.behavior.quest.QuestInfo.QuestStep;
import com.aiplayer.behavior.quest.QuestProgressTracker.ActiveQuestState;
import com.aiplayer.behavior.quest.QuestInfo.StepType;


/**
 * Executes quest steps with human-like pacing and failure recovery.
 * Integrates with MovementController (navigation), CombatAI (kill steps),
 * TownBehaviorEngine (NPC talk steps), and InventoryTracker (collect steps).
 *
 * Step execution is state-machine driven:
 * IDLE -> ACCEPT -> [NAVIGATE -> KILL/COLLECT/TALK/USE_ITEM/WAIT] -> RETURN -> COMPLETE
 *
 * Humanization:
 * - Random delays between steps (think time, inventory check, camera look)
 * - Occasional detours (anti-pattern)
 * - Death recovery: respawn, rebuff, return to step zone
 * - Retry limit: abandon quest after 3 deaths on same step
 */
public final class QuestExecutor {

    public enum ExecutorState {
        IDLE,           // No active quest execution
        ACCEPTING,      // Accepting quest from NPC
        NAVIGATING,     // Moving to step target zone
        EXECUTING_STEP, // Performing kill/collect/talk/use_item
        RETURNING,      // Returning to quest giver
        COMPLETING,     // Turning in quest
        FAILED,         // Too many deaths / errors, quest abandoned
        PAUSED          // Paused for break or town restock
    }

    private final String accountName;
    private final QuestProgressTracker progress;
    private final AntiDetectionEngine anti;
    private final MovementController movement;

    private ExecutorState state = ExecutorState.IDLE;
    private int activeQuestId = -1;
    private long nextActionTime = 0;
    private int stepDeathCount = 0;
    private long stepStartTime = 0;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    public QuestExecutor(String accountName, QuestProgressTracker progress,
                         AntiDetectionEngine anti, MovementController movement) {
        this.accountName = accountName;
        this.progress = progress;
        this.anti = anti;
        this.movement = movement;
    }

    /**
     * Main tick — call every 500ms from AIBrain.
     */
    public void tick(BotStateSnapshot self) {
        if (state == ExecutorState.IDLE || state == ExecutorState.FAILED) return;
        if (System.currentTimeMillis() < nextActionTime) return;

        QuestInfo quest = QuestDatabase.getById(activeQuestId);
        if (quest == null) {
            reset();
            return;
        }

        switch (state) {
            case ACCEPTING:
                handleAccepting(quest);
                break;
            case NAVIGATING:
                handleNavigating(quest, self);
                break;
            case EXECUTING_STEP:
                handleExecutingStep(quest, self);
                break;
            case RETURNING:
                handleReturning(quest, self);
                break;
            case COMPLETING:
                handleCompleting(quest);
                break;
            case PAUSED:
                handlePaused(self);
                break;
        }
    }

    /**
     * Start executing a quest.
     */
    public void startQuest(int questId) {
        if (state != ExecutorState.IDLE) return;
        this.activeQuestId = questId;
        this.state = ExecutorState.ACCEPTING;
        this.stepDeathCount = 0;
        this.retryCount = 0;
        progress.startQuest(questId);
        nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.NPC_INTERACT);
    }

    /**
     * Notify that a mob was killed — may advance kill steps.
     */
    public void onMobKilled(int mobTemplateId) {
        if (state != ExecutorState.EXECUTING_STEP) return;
        QuestInfo quest = QuestDatabase.getById(activeQuestId);
        if (quest == null) return;

        QuestInfo.QuestStep step = progress.getCurrentStep(activeQuestId);
        if (step != null && step.stepType == QuestInfo.StepType.KILL && step.targetId == mobTemplateId) {
            progress.incrementStepProgress(activeQuestId, 1);
            checkStepCompletion(quest);
        }
    }

    /**
     * Notify that an item was collected.
     */
    public void onItemCollected(int itemId, int count) {
        if (state != ExecutorState.EXECUTING_STEP) return;
        QuestInfo quest = QuestDatabase.getById(activeQuestId);
        if (quest == null) return;

        QuestInfo.QuestStep step = progress.getCurrentStep(activeQuestId);
        if (step != null && step.stepType == QuestInfo.StepType.COLLECT && step.targetId == itemId) {
            progress.incrementStepProgress(activeQuestId, count);
            checkStepCompletion(quest);
        }
    }

    /**
     * Notify death — may pause quest for recovery.
     */
    public void onDeath() {
        if (state == ExecutorState.IDLE) return;
        stepDeathCount++;
        progress.recordDeath(activeQuestId);
        if (anti != null) anti.recordDeath();

        if (stepDeathCount >= MAX_RETRIES) {
            failQuest("Too many deaths on step");
        } else {
            // Pause for recovery flow (DeathHandler will take over)
            state = ExecutorState.PAUSED;
            nextActionTime = System.currentTimeMillis() + 30000; // Wait for respawn + rebuff
        }
    }

    /**
     * Called by DeathHandler after rebuff/restock to resume quest.
     */
    public void resumeAfterRecovery() {
        if (state == ExecutorState.PAUSED) {
            state = ExecutorState.NAVIGATING;
            nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.MOVEMENT_START);
        }
    }

    // ================================================================
    // STATE HANDLERS
    // ================================================================

    private void handleAccepting(QuestInfo quest) {
        // In real integration: protocol.talkToNpc(quest.startNpcId, "quest_accept")
        // For now, advance to first step
        state = ExecutorState.NAVIGATING;
        nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.NPC_INTERACT);
    }

    private void handleNavigating(QuestInfo quest, BotStateSnapshot self) {
        QuestInfo.QuestStep step = progress.getCurrentStep(activeQuestId);
        if (step == null) {
            // All steps done — return to NPC
            state = ExecutorState.RETURNING;
            return;
        }

        // Perturb destination for humanization
        int[] dest = anti.perturbDestination(step.zoneX, step.zoneY, step.zoneZ, 50);
        movement.moveTo(dest[0], dest[1], dest[2]);

        // Check arrival
        double dist = Math.hypot(self.x - dest[0], self.y - dest[1]);
        if (dist < 200) {
            state = ExecutorState.EXECUTING_STEP;
            stepStartTime = System.currentTimeMillis();
            nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.MOVEMENT_ARRIVE);
        } else {
            nextActionTime = System.currentTimeMillis() + anti.getMovementInterval();
        }
    }

    private void handleExecutingStep(QuestInfo quest, BotStateSnapshot self) {
        QuestInfo.QuestStep step = progress.getCurrentStep(activeQuestId);
        if (step == null) {
            state = ExecutorState.RETURNING;
            return;
        }

        switch (step.stepType) {
            case KILL:
                // CombatAI handles targeting; we just verify progress via onMobKilled
                // If stuck too long, consider retry
                if (System.currentTimeMillis() - stepStartTime > 300000) { // 5 min timeout
                    retryStep("Kill timeout");
                }
                nextActionTime = System.currentTimeMillis() + 2000;
                break;

            case COLLECT:
                // Similar to kill but with loot check
                if (System.currentTimeMillis() - stepStartTime > 300000) {
                    retryStep("Collect timeout");
                }
                nextActionTime = System.currentTimeMillis() + 2000;
                break;

            case TALK:
                // Talk to NPC and advance immediately
                nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.NPC_INTERACT);
                progress.advanceStep(activeQuestId);
                state = ExecutorState.NAVIGATING;
                break;

            case USE_ITEM:
                // Use item and advance
                nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.INVENTORY_USE);
                progress.advanceStep(activeQuestId);
                state = ExecutorState.NAVIGATING;
                break;

            case WAIT:
                // Wait for spawn or event
                if (System.currentTimeMillis() - stepStartTime > 60000) {
                    progress.advanceStep(activeQuestId);
                    state = ExecutorState.NAVIGATING;
                }
                nextActionTime = System.currentTimeMillis() + 5000;
                break;

            case NAVIGATE:
                // Pure navigation step
                progress.advanceStep(activeQuestId);
                state = ExecutorState.NAVIGATING;
                break;

            case RETURN:
                // This is handled by RETURNING state
                state = ExecutorState.RETURNING;
                break;
        }
    }

    private void handleReturning(QuestInfo quest, BotStateSnapshot self) {
        int[] dest = anti.perturbDestination(
            quest.steps.get(0).zoneX, quest.steps.get(0).zoneY, quest.steps.get(0).zoneZ, 30);
        movement.moveTo(dest[0], dest[1], dest[2]);

        double dist = Math.hypot(self.x - dest[0], self.y - dest[1]);
        if (dist < 200) {
            state = ExecutorState.COMPLETING;
            nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.NPC_INTERACT);
        } else {
            nextActionTime = System.currentTimeMillis() + anti.getMovementInterval();
        }
    }

    private void handleCompleting(QuestInfo quest) {
        // In real integration: protocol.talkToNpc(quest.startNpcId, "quest_complete")
        progress.completeQuest(activeQuestId);
        state = ExecutorState.IDLE;
        activeQuestId = -1;
        nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.NPC_INTERACT);
    }

    private void handlePaused(BotStateSnapshot self) {
        // Wait for recovery or manual resume
        // If health/mana good and not in town, auto-resume
        if ((self.hpMax > 0 ? self.hpCurrent * 100 / self.hpMax : 100) > 80 && (self.mpMax > 0 ? self.mpCurrent * 100 / self.mpMax : 100) > 60 && !self.isInTown) {
            resumeAfterRecovery();
        }
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    private void checkStepCompletion(QuestInfo quest) {
        QuestInfo.QuestStep step = progress.getCurrentStep(activeQuestId);
        if (step == null) {
            state = ExecutorState.RETURNING;
            return;
        }
        // QuestProgressTracker auto-advances when count >= target
        // Just check if we need to transition
        QuestProgressTracker.ActiveQuestState stateObj = progress.getActiveState(activeQuestId);
        if (stateObj != null && stateObj.currentStepIndex >= quest.steps.size()) {
            state = ExecutorState.RETURNING;
        } else if (stateObj != null && stateObj.currentStepIndex > getCurrentStepIndexFromState()) {
            // Step advanced
            state = ExecutorState.NAVIGATING;
            stepDeathCount = 0;
            nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.MOVEMENT_START);
        }
    }

    private int getCurrentStepIndexFromState() {
        QuestProgressTracker.ActiveQuestState s = progress.getActiveState(activeQuestId);
        return s != null ? s.currentStepIndex : 0;
    }

    private void retryStep(String reason) {
        retryCount++;
        if (retryCount >= MAX_RETRIES) {
            failQuest("Max retries: " + reason);
            return;
        }
        stepDeathCount = 0;
        stepStartTime = System.currentTimeMillis();
        // Re-navigate to same step
        state = ExecutorState.NAVIGATING;
        nextActionTime = System.currentTimeMillis() + anti.getDelay(TimingJitter.ActionContext.MOVEMENT_START);
    }

    private void failQuest(String reason) {
        progress.abandonQuest(activeQuestId);
        state = ExecutorState.FAILED;
        activeQuestId = -1;
        retryCount = 0;
        // Will be picked up by LevelingPlanner to choose next activity
    }

    private void reset() {
        state = ExecutorState.IDLE;
        activeQuestId = -1;
        nextActionTime = 0;
        stepDeathCount = 0;
        retryCount = 0;
    }

    // ================================================================
    // GETTERS
    // ================================================================

    public ExecutorState getState() {
        return state;
    }

    public int getActiveQuestId() {
        return activeQuestId;
    }

    public boolean isBusy() {
        return state != ExecutorState.IDLE && state != ExecutorState.FAILED;
    }

    public String getStatus() {
        if (state == ExecutorState.IDLE) return "IDLE";
        QuestInfo q = QuestDatabase.getById(activeQuestId);
        String qName = q != null ? q.name : "Unknown";
        return String.format("%s[%s, quest=%s, retries=%d]", state, accountName, qName, retryCount);
    }
}
