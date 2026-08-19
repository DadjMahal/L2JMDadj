// package com.aiplayer.engine;
import java.util.logging.Logger;

public class TerritoryNPCDefense {
    private static final Logger LOGGER = Logger.getLogger(TerritoryNPCDefense.class.getName());

    public enum NpcRole { GUARD, MERCHANT, TRAINERS, GOVERNOR }

    public static class NpcGuard {
        public final int npcId;
        public final String name;
        public NpcRole role;
        public boolean isProtected;

        public NpcGuard(int id, String name, NpcRole role) {
            this.npcId = id; this.name = name; this.role = role; this.isProtected = true;
        }
    }

    public static boolean shouldDefend(NpcGuard npc, int attackerCount) {
        return npc.role == NpcRole.GUARD && attackerCount > 5;
    }
}
