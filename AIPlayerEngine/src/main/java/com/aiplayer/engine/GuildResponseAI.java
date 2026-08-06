package com.aiplayer.engine;
import java.util.logging.Logger;

public class GuildResponseAI {
    private static final Logger LOGGER = Logger.getLogger(GuildResponseAI.class.getName());

    public static boolean shouldRespondToWarDeclaration(String declaredBy, boolean isAlly) {
        return !isAlly;
    }

    public static String processGuildEmblem(String emblemId, int ownerLevel) {
        if (ownerLevel > 80) return "RESPECTED";
        if (ownerLevel > 50) return "NEUTRAL";
        return "IGNORE";
    }

    public static void updateGuildRelations(String guild, int delta) {
        LOGGER.info("Guild " + guild + " relation changed by " + delta);
    }
}
