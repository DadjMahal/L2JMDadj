package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class LongTermGoalsAI {
    private static final Logger LOGGER = Logger.getLogger(LongTermGoalsAI.class.getName());

    public enum Goal { CASTLE_OWNERSHIP, MAX_LEVEL, ACHIEVEMENT_RAID, GUILD_LEADERSHIP, NOBLESCE_TITLE }

    private final Map<Goal, Integer> goalProgress = new HashMap<>();

    public Goal getPrimaryGoal(int level, int castleCount, boolean isNoblesse) {
        if (isNoblesse) return Goal.NOBLESCE_TITLE;
        if (castleCount > 0) return Goal.CASTLE_OWNERSHIP;
        if (level > 80) return Goal.ACHIEVEMENT_RAID;
        if (level > 60) return Goal.GUILD_LEADERSHIP;
        return Goal.MAX_LEVEL;
    }

    public void advanceGoal(Goal goal, int amount) {
        goalProgress.merge(goal, amount, Integer::sum);
    }

    public int getGoalProgress(Goal goal) {
        return goalProgress.getOrDefault(goal, 0);
    }
}
