package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class ClassQuestAI {
    private static final Logger LOGGER = Logger.getLogger(ClassQuestAI.class.getName());

    public static class QuestChain {
        public final String questId;
        public final String[] prerequisites;
        public final boolean isRequired;

        public QuestChain(String id, String[] pre, boolean req) {
            questId = id; prerequisites = pre; isRequired = req;
        }
    }

    public static Map<String, QuestChain> CLASS_QUESTS = new HashMap<>();
    static {
        CLASS_QUESTS.put("COMMON_BOSS", new QuestChain("COMMON_BOSS", new String[]{}, true));
        CLASS_QUESTS.put("WARRIOR_PATH_1", new QuestChain("WARRIOR_PATH_1", new String[]{"COMMON_BOSS"}, true));
        CLASS_QUESTS.put("WARRIOR_PATH_2", new QuestChain("WARRIOR_PATH_2", new String[]{"WARRIOR_PATH_1"}, true));
        CLASS_QUESTS.put("ROGUE_INITIATION", new QuestChain("ROGUE_INITIATION", new String[]{"COMMON_BOSS"}, true));
        CLASS_QUESTS.put("ELF_MYSTERY", new QuestChain("ELF_MYSTERY", new String[]{"COMMON_BOSS"}, true));
    }

    public static boolean canCompleteClassChange(String questId, Set<String> completed) {
        QuestChain qc = CLASS_QUESTS.get(questId);
        if (qc == null) return false;
        for (String pre : qc.prerequisites) {
            if (!completed.contains(pre)) return false;
        }
        return true;
    }

    public static String getNextQuest(String currentQuest, Set<String> completed) {
        for (Map.Entry<String, QuestChain> e : CLASS_QUESTS.entrySet()) {
            QuestChain qc = e.getValue();
            if (Arrays.asList(qc.prerequisites).contains(currentQuest) && !completed.contains(qc.questId)) {
                return qc.questId;
            }
        }
        return null;
    }
}
