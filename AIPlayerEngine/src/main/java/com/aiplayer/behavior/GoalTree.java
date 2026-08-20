package com.aiplayer.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import com.aiplayer.net.AIPlayer;
import com.aiplayer.learning.EmotionalState;
import com.aiplayer.behavior.LongTermGoalsAI.Goal;

/**
 * Goal Tree — Stream D (tasks 65, 68, 69).
 *
 * <p>Holds the set of currently-relevant SHORT-TERM goals for an AI player and selects the single
 * active one (by priority + scheduling) that should drive the live decision loop this tick.
 *
 * <p>Before Stream D there was no goal tree at all — the bot just reacted to the nearest mob.
 * {@code LongTermGoalsAI} existed but selected only the long-term aspiration (MAX_LEVEL, etc.).
 * This class bridges the two: the long-term goal seeds which short-term goals are eligible, and
 * the tree schedules one to act on now.
 *
 * <p>Priority order (highest first): SURVIVE (flee/heal) &gt; ACTIVE_QUEST (turn-in/collect) &gt;
 * GRIND_XP (kill nearest mob) &gt; EXPLORE (move to a new spot) &gt; SOCIAL (party/seek players) &gt; IDLE.
 */
public class GoalTree {
    private static final Logger LOGGER = Logger.getLogger(GoalTree.class.getName());

    public enum ShortTermGoal {
        SURVIVE(100, "flee/heal - HP critical or overwhelmed"),
        ACTIVE_QUEST(80, "advance an accepted quest (collect / turn-in / talk)"),
        GRIND_XP(60, "kill nearest hostile mob for experience"),
        EXPLORE(40, "move to a fresh hunting spot"),
        SOCIAL(20, "form/join a party or seek players"),
        IDLE(0, "nothing to do right now");

        private final int priority;
        private final String description;
        ShortTermGoal(int p, String d) { this.priority = p; this.description = d; }
        public int getPriority() { return priority; }
        public String getDescription() { return description; }
    }

    public static class GoalNode {
        public final ShortTermGoal goal;
        private double weight;        // 1.0 = base; personality/emotion can scale it
        private long deadlineMs;      // 0 = no deadline
        GoalNode(ShortTermGoal g) { this.goal = g; this.weight = 1.0; this.deadlineMs = 0; }
        public double getWeight() { return weight; }
        public void setWeight(double w) { this.weight = w; }
        public void setDeadline(long ms) { this.deadlineMs = System.currentTimeMillis() + ms; }
        public long getDeadlineMs() { return deadlineMs; }
    }

    private final AIPlayer aiPlayer;
    private final List<GoalNode> eligible = new ArrayList<>();
    private ShortTermGoal activeGoal = ShortTermGoal.IDLE;
    private long activeSinceMs = System.currentTimeMillis();

    public GoalTree(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    public AIPlayer getAiPlayer() { return aiPlayer; }
    public List<GoalNode> getEligible() { return eligible; }

    /** Rebuild the eligible-goal set from the player's live state (HP, quests, level, personality). */
    public void refresh() {
        eligible.clear();
        if (aiPlayer.getCombatAI() != null && aiPlayer.getCombatAI().shouldDefend()) {
            add(ShortTermGoal.SURVIVE);
        }
        add(ShortTermGoal.GRIND_XP);
        if (aiPlayer.getEmotions().getCurrentEmotion()
                == com.aiplayer.learning.EmotionalState.Emotion.BORED) {
            add(ShortTermGoal.EXPLORE);
        }
        if (aiPlayer.getPersonality().getSocialWeight() > 1.5) {
            add(ShortTermGoal.SOCIAL);
        }
        applyPersonalityWeights();
    }

    private void add(ShortTermGoal g) { eligible.add(new GoalNode(g)); }

    private void applyPersonalityWeights() {
        for (GoalNode n : eligible) {
            switch (n.goal) {
                case GRIND_XP:      n.weight *= aiPlayer.getPersonality().getCombatWeight(); break;
                case ACTIVE_QUEST:  n.weight *= aiPlayer.getPersonality().getQuestWeight(); break;
                case EXPLORE:       n.weight *= aiPlayer.getPersonality().getExploreWeight(); break;
                case SOCIAL:        n.weight *= aiPlayer.getPersonality().getSocialWeight(); break;
                case SURVIVE:       n.weight *= aiPlayer.getPersonality().getSafetyWeight(); break;
                default: break;
            }
        }
    }

    public ShortTermGoal selectActiveGoal() {
        refresh();
        long now = System.currentTimeMillis();
        if (activeGoal != ShortTermGoal.IDLE && now - activeSinceMs > 60_000L) {
            for (GoalNode n : eligible) {
                if (n.goal == activeGoal) { n.weight *= 0.1; break; }
            }
        }
        GoalNode best = null;
        double bestScore = -1;
        for (GoalNode n : eligible) {
            double score = n.goal.getPriority() * n.weight;
            if (n.deadlineMs > 0 && now > n.deadlineMs) {
                score = Double.MAX_VALUE;
            }
            if (score > bestScore) { bestScore = score; best = n; }
        }
        if (best == null) {
            activeGoal = ShortTermGoal.IDLE;
        } else if (best.goal != activeGoal) {
            LOGGER.info("[GOAL] " + aiPlayer.getName() + ": " + activeGoal + " -> " + best.goal);
            activeGoal = best.goal;
            activeSinceMs = now;
        }
        return activeGoal;
    }

    public ShortTermGoal getActiveGoal() { return activeGoal; }

    public void markProgress() { activeSinceMs = System.currentTimeMillis(); }


}
