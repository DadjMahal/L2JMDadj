package com.aiplayer.engine;
import java.util.logging.Logger;

public class VillageMasterAI {
    private static final Logger LOGGER = Logger.getLogger(VillageMasterAI.class.getName());

    public enum ClassChangeState { VISITED, QUEST_ACCEPTED, QUEST_COMPLETED, READY_TO_CHANGE, CHANGED }

    public static class ClassChangeFlow {
        public final int npcId;
        public final ClassChangeState state;
        public final String[] requiredQuests;

        public ClassChangeFlow(int npc, ClassChangeState state, String[] quests) {
            npcId = npc; this.state = state; requiredQuests = quests;
        }
    }

    public static boolean shouldVisitVillageMaster(int level, int classId, ClassChangeState state) {
        if (state == ClassChangeState.CHANGED) return false;
        if (level >= 20 && classId == 0) return true; // First class change at level 20
        if (level >= 40 && classId > 0) return true;  // Second class change
        return false;
    }

    public static String[] getRequiredQuests(int classId) {
        switch (classId) {
            case 0: return new String[]{"COMMON_BOSS", "KILL_100_MONSTERS"};
            case 1: return new String[]{"WARRIOR_PATH_1", "WARRIOR_PATH_2"};
            case 2: return new String[]{"ROGUE_INITIATION"};
            case 3: return new String[]{"ELF_MYSTERY"};
            default: return new String[]{};
        }
    }
}
