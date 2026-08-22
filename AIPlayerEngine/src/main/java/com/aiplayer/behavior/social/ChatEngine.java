package com.aiplayer.behavior.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import com.aiplayer.core.BotProfile;
import com.aiplayer.behavior.ProfileStore;
import com.aiplayer.core.DeterministicRandom;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public class ChatEngine {
    private static final Logger LOGGER = Logger.getLogger(ChatEngine.class.getName());
    private static ChatEngine instance;
    private final ProfileStore cabinet; // also replaces the old RedisCache — see ProfileStore
    private final Random rng = DeterministicRandom.forFleet("chat-engine");

    private ChatEngine() {
        this.cabinet = ProfileStore.getInstance();
    }

    public static synchronized ChatEngine getInstance() {
        if (instance == null) instance = new ChatEngine();
        return instance;
    }

    public String onIncomingChat(String botAccount, String speaker, String message) {
        BotProfile profile = cabinet.loadProfile(botAccount);
        if (profile == null) return null;

        cabinet.pushChat(botAccount, speaker + ": " + message);

        Intent intent = IntentClassifier.classify(message);
        if (intent == Intent.UNKNOWN) return null;

        Map<String, String> vars = new HashMap<>();
        vars.put("name", profile.getName());
        vars.put("level", String.valueOf(profile.getLevel()));
        vars.put("class", className(profile.getClassCurrent()));
        vars.put("race", raceName(profile.getRace()));
        vars.put("zone", "Cruma Marshlands");
        vars.put("level_range", (profile.getLevel() - 5) + "-" + (profile.getLevel() + 5));

        Persona persona = Persona.valueOf(profile.getPersona().toUpperCase());

        if (rng.nextDouble() < 0.10) {
            LOGGER.info("[" + botAccount + "] Ignoring chat (human behavior)");
            return null;
        }

        String reply = ResponseTemplate.pick(intent, persona, vars, botAccount.hashCode());
        if (rng.nextDouble() < 0.10) reply = injectTypo(reply);

        cabinet.recordEpisode(profile.getBotId(), "CHAT", "Said: " + reply, vars.get("zone"), speaker, "neutral");
        return reply;
    }

    public String ambientChat(BotProfile profile) {
        if (rng.nextDouble() > 0.3) return null;
        Persona p = Persona.valueOf(profile.getPersona().toUpperCase());
        if (p == Persona.MERCHANT) return "wtb dc robe | selling ss c-grade cheap";
        if (p == Persona.TROLL && rng.nextDouble() < 0.2) return "ez game";
        return null;
    }

    private String injectTypo(String s) {
        if (s.length() < 4) return s;
        int idx = rng.nextInt(s.length() - 1);
        char[] arr = s.toCharArray();
        char tmp = arr[idx]; arr[idx] = arr[idx + 1]; arr[idx + 1] = tmp;
        return new String(arr);
    }

    private String className(int classId) {
        switch (classId) {
            case 0: return "Fighter"; case 1: return "Warrior"; case 2: return "Gladiator";
            case 3: return "Warlord"; case 10: return "Mage"; case 11: return "Wizard";
            case 12: return "Sorcerer"; case 18: return "Scout"; case 19: return "Assassin";
            case 22: return "Rogue"; case 25: return "Cleric"; case 29: return "Bishop";
            case 32: return "Swordsinger"; case 43: return "Spellhowler";
            default: return "Adventurer";
        }
    }

    private String raceName(int race) {
        switch (race) {
            case 0: return "Human"; case 1: return "Elf"; case 2: return "Dark Elf";
            case 3: return "Orc"; case 4: return "Dwarf"; default: return "Human";
        }
    }
}
