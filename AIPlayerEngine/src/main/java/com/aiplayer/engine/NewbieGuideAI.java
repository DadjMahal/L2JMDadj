package com.aiplayer.engine;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class NewbieGuideAI {
    private static final Logger LOGGER = Logger.getLogger(NewbieGuideAI.class.getName());

    public enum GuideStep {
        CHOOSE_CLASS, BASIC_MOVEMENT, BASIC_COMBAT, SKILL_USAGE,
        QUEST_INTRO, SOCIAL_FEATURES, GAME_MECHANICS, COMPLETE
    }

    public static class GuideProgress {
        public Map<GuideStep, Boolean> completed = new HashMap<>();
        public int currentStep = 0;

        public GuideProgress() {
            for (GuideStep s : GuideStep.values()) completed.put(s, false);
        }
    }

    public static GuideStep getCurrentStep(int level, GuideProgress progress) {
        if (level < 10) return GuideStep.BASIC_MOVEMENT;
        if (level < 20) return GuideStep.QUEST_INTRO;
        if (level < 40) return GuideStep.SKILL_USAGE;
        if (progress.completed.get(GuideStep.COMPLETE)) return GuideStep.COMPLETE;
        return GuideStep.GAME_MECHANICS;
    }

    public static boolean shouldCompleteGuide(int level, GuideProgress progress) {
        return level >= 40 && progress.completed.get(GuideStep.SKILL_USAGE) && progress.completed.get(GuideStep.QUEST_INTRO);
    }
}
