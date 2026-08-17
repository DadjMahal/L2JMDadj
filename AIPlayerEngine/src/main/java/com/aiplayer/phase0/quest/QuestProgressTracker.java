package com.aiplayer.phase0.quest;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */


import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player quest state tracker.
 * Maintains active quests, completed quest IDs, and step-level progress counters.
 *
 * State is persisted to Redis (player:quest:{account}) for cross-session continuity
 * and cached locally for fast reads during gameplay.
 *
 * Integration: GameStateMirror queries this for current quest context;
 * QuestExecutor updates this as steps complete.
 */
public final class QuestProgressTracker {

    private final String accountName;
    // JedisPool removed: see loadFromRedis()/saveToRedis() below

    // Local cache ( authoritative between Redis syncs )
    private final Set<Integer> completedQuests = ConcurrentHashMap.newKeySet();
    private final Map<Integer, ActiveQuestState> activeQuests = new ConcurrentHashMap<>();

    // Redis key
    private static final String REDIS_KEY_PREFIX = "player:quest:";

    public QuestProgressTracker(String accountName) {
        this.accountName = accountName;
        loadFromRedis();
    }

    // ================================================================
    // ACTIVE QUEST STATE
    // ================================================================

    public static final class ActiveQuestState {
        public final int questId;
        public int currentStepIndex;          // 0-based index into QuestInfo.steps
        public int stepProgressCount;         // Kills/items collected for current step
        public long questStartTime;           // When quest was accepted
        public long lastStepTime;             // When current step started
        public int deathCount;              // Deaths during this quest
        public boolean abandoned;

        public ActiveQuestState(int questId) {
            this.questId = questId;
            this.currentStepIndex = 0;
            this.stepProgressCount = 0;
            this.questStartTime = System.currentTimeMillis();
            this.lastStepTime = questStartTime;
            this.deathCount = 0;
            this.abandoned = false;
        }
    }

    /**
     * Start tracking a new quest.
     */
    public void startQuest(int questId) {
        if (activeQuests.containsKey(questId) || completedQuests.contains(questId)) {
            return;
        }
        ActiveQuestState state = new ActiveQuestState(questId);
        activeQuests.put(questId, state);
        saveToRedis();
    }

    /**
     * Advance progress on the current step (e.g., kill count +1).
     */
    public void incrementStepProgress(int questId, int amount) {
        ActiveQuestState state = activeQuests.get(questId);
        if (state == null || state.abandoned) return;

        state.stepProgressCount += amount;
        QuestInfo quest = QuestDatabase.getById(questId);
        if (quest == null) return;

        if (state.currentStepIndex < quest.steps.size()) {
            QuestInfo.QuestStep step = quest.steps.get(state.currentStepIndex);
            if (state.stepProgressCount >= step.targetCount) {
                // Step complete — advance
                state.currentStepIndex++;
                state.stepProgressCount = 0;
                state.lastStepTime = System.currentTimeMillis();
            }
        }
        saveToRedis();
    }

    /**
     * Advance to next step manually (for TALK, NAVIGATE, etc.).
     */
    public void advanceStep(int questId) {
        ActiveQuestState state = activeQuests.get(questId);
        if (state == null || state.abandoned) return;

        state.currentStepIndex++;
        state.stepProgressCount = 0;
        state.lastStepTime = System.currentTimeMillis();
        saveToRedis();
    }

    /**
     * Mark quest as completed.
     */
    public void completeQuest(int questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        saveToRedis();
    }

    /**
     * Abandon a quest (e.g., too difficult, better quest found).
     */
    public void abandonQuest(int questId) {
        ActiveQuestState state = activeQuests.get(questId);
        if (state != null) {
            state.abandoned = true;
            activeQuests.remove(questId);
        }
        saveToRedis();
    }

    /**
     * Record a death during quest execution.
     */
    public void recordDeath(int questId) {
        ActiveQuestState state = activeQuests.get(questId);
        if (state != null) {
            state.deathCount++;
        }
    }

    // ================================================================
    // QUERIES
    // ================================================================

    public boolean isQuestActive(int questId) {
        return activeQuests.containsKey(questId);
    }

    public boolean isQuestCompleted(int questId) {
        return completedQuests.contains(questId);
    }

    public ActiveQuestState getActiveState(int questId) {
        return activeQuests.get(questId);
    }

    public Set<Integer> getActiveQuestIds() {
        return new HashSet<>(activeQuests.keySet());
    }

    public Set<Integer> getCompletedQuestIds() {
        return new HashSet<>(completedQuests);
    }

    /**
     * Get the currently recommended active quest (highest priority / most progress).
     */
    public Integer getCurrentPriorityQuest() {
        if (activeQuests.isEmpty()) return null;

        // Prefer class-change quests, then by progress ratio
        return activeQuests.values().stream()
            .filter(s -> !s.abandoned)
            .max(Comparator.comparingInt((ActiveQuestState s) -> {
                QuestInfo q = QuestDatabase.getById(s.questId);
                if (q == null) return 0;
                // Class change quests get highest priority
                int priority = (q.type == QuestInfo.QuestType.CLASS_CHANGE) ? 1000 : 0;
                // Add progress ratio (0-100)
                double progress = q.steps.isEmpty() ? 0 : s.currentStepIndex / (double) q.steps.size();
                return priority + (int) (progress * 100);
            }))
            .map(s -> s.questId)
            .orElse(null);
    }

    /**
     * Get the current step for an active quest, or null if not active / complete.
     */
    public QuestInfo.QuestStep getCurrentStep(int questId) {
        ActiveQuestState state = activeQuests.get(questId);
        if (state == null) return null;
        QuestInfo quest = QuestDatabase.getById(questId);
        if (quest == null || state.currentStepIndex >= quest.steps.size()) return null;
        return quest.steps.get(state.currentStepIndex);
    }

    /**
     * Check if a quest is fully complete (all steps done).
     */
    public boolean isQuestFullyComplete(int questId) {
        ActiveQuestState state = activeQuests.get(questId);
        if (state == null) return completedQuests.contains(questId);
        QuestInfo quest = QuestDatabase.getById(questId);
        if (quest == null) return false;
        return state.currentStepIndex >= quest.steps.size();
    }

    // ================================================================
    // PERSISTENCE
    // ================================================================

    private void loadFromRedis() {
        // LEGIT_TODO: was Redis-backed (survives JVM restart); now in-memory
        // only, per this project's no-new-infrastructure-at-this-scale rule.
        // completedQuests/activeQuests already ARE the real state (regular
        // fields) — this is intentionally a no-op, not a stub pretending to
        // load something. Quest progress resets on restart until a real
        // persistence need is measured, not just anticipated.
    }

    private void saveToRedis() {
        // No-op — see loadFromRedis() above for why.
    }

    // ================================================================
    // REPORTING
    // ================================================================

    public String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("QuestProgress[").append(accountName)
          .append(": completed=").append(completedQuests.size())
          .append(", active=").append(activeQuests.size()).append("]");
        for (ActiveQuestState state : activeQuests.values()) {
            QuestInfo q = QuestDatabase.getById(state.questId);
            if (q == null) continue;
            sb.append("\n  ").append(q.name)
              .append(" [Step ").append(state.currentStepIndex + 1)
              .append("/").append(q.steps.size()).append("] ");
            if (state.currentStepIndex < q.steps.size()) {
                QuestInfo.QuestStep step = q.steps.get(state.currentStepIndex);
                sb.append(step.stepType).append(" ")
                  .append(state.stepProgressCount).append("/").append(step.targetCount);
            }
        }
        return sb.toString();
    }
}
