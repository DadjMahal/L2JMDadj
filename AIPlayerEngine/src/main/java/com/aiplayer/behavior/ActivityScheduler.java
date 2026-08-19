package com.aiplayer.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import com.aiplayer.net.AIPlayer;

/**
 * Activity Scheduler — Stream E (task 88).
 *
 * <p>Schedules periodic activities for an AI player so its behavior isn't a fixed single loop but
 * a rotating set of interests on a schedule (like a real player who grinds, then visits a
 * merchant, then socialises). Each activity has an interval; when its interval elapses it becomes
 * "due". {@link #nextActivity()} returns the highest-priority activity that is currently due,
 * consulting the shared GoalTree so schedule + goals agree.
 *
 * <p>Activities, default intervals (ms), and the GoalTree short-term goal they map to:
 * <ul>
 *   <li>GRIND     — 20s — everything is secondary to survival/XP grinding</li>
 *   <li>MERCHANT  — 120s — visit a merchant when inventory/adena warrant it (economy)</li>
 *   <li>QUEST     — 90s  — advance an active quest (goals)</li>
 *   <li>SOCIAL    — 180s — party/chat when social/bored (social)</li>
 *   <li>REST      — 300s — idle/recover (emotional cooldown)</li>
 * </ul>
 * Intervals carry small deterministic jitter (personality-scaled) so bots drift off the same
 * cadence and don't act in lockstep.
 */
public class ActivityScheduler {
    private static final Logger LOGGER = Logger.getLogger(ActivityScheduler.class.getName());

    public enum Activity {
        GRIND("combat/grind", 20_000L),
        MERCHANT("economy/trade", 120_000L),
        QUEST("quests", 90_000L),
        SOCIAL("social", 180_000L),
        REST("recover", 300_000L);

        private final String label;
        private final long baseIntervalMs;
        Activity(String label, long baseIntervalMs) { this.label = label; this.baseIntervalMs = baseIntervalMs; }
        public String label() { return label; }
        public long baseIntervalMs() { return baseIntervalMs; }
    }

    private final AIPlayer aiPlayer;
    private final Map<Activity, Long> nextDue = new HashMap<>();

    public ActivityScheduler(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        long start = System.currentTimeMillis();
        for (Activity a : Activity.values()) nextDue.put(a, start); // all due immediately at start
        LOGGER.info("[Scheduler] " + aiPlayer.getName() + " initialized with "
                + Activity.values().length + " activities");
    }

    /** Effective interval for an activity, jittered by personality (0.8x..1.2x). */
    private long effectiveInterval(Activity a) {
        // Deterministic per-player jitter from accountId + personality so bots aren't in lockstep.
        double jitter = 0.8 + ((aiPlayer.getAccountId() * 7L + a.ordinal() * 13L) % 100) / 250.0;
        return (long) (a.baseIntervalMs() * jitter);
    }

    /** Mark an activity as just performed (reschedules its next due time). */
    public void markDone(Activity a) {
        nextDue.put(a, System.currentTimeMillis() + effectiveInterval(a));
    }

    /** True if the activity's interval has elapsed. */
    public boolean isDue(Activity a) {
        return System.currentTimeMillis() >= nextDue.getOrDefault(a, 0L);
    }

    /**
     * Pick the next activity to perform, respecting both schedule AND the GoalTree. An activity is
     * a candidate only if it is due; among due activities the one matching the current active goal
     * wins, otherwise GRIND (priority) is a safe default.
     */
    public Activity nextActivity() {
        GoalTree.ShortTermGoal goal = aiPlayer.getGoalTree().getActiveGoal();
        // Map the active goal to the most fitting activity, if it is due.
        for (Activity a : Activity.values()) {
            if (isDue(a) && matchesGoal(a, goal)) return a;
        }
        // Fall back: any due activity, preferring GRIND.
        if (isDue(Activity.GRIND)) return Activity.GRIND;
        for (Activity a : Activity.values()) if (isDue(a)) return a;
        return Activity.GRIND;
    }

    private boolean matchesGoal(Activity a, GoalTree.ShortTermGoal goal) {
        if (goal == null) return false;
        switch (goal) {
            case SURVIVE:      return a == Activity.GRIND;
            case ACTIVE_QUEST: return a == Activity.QUEST;
            case GRIND_XP:     return a == Activity.GRIND;
            case EXPLORE:      return a == Activity.GRIND;
            case SOCIAL:       return a == Activity.SOCIAL;
            default:           return false;
        }
    }
}
