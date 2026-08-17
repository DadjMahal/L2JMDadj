package com.aiplayer.phase0.chat;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ResponseTemplate {
    private static final Map<Intent, Map<Persona, List<String>>> POOL = new HashMap<>();

    static {
        put(Intent.COMMAND_FOLLOW, Persona.VETERAN, Arrays.asList("omw", "following", "kk"));
        put(Intent.COMMAND_FOLLOW, Persona.NEWBIE, Arrays.asList("ok, following you", "sure, im coming", "on my way!"));
        put(Intent.COMMAND_FOLLOW, Persona.TROLL, Arrays.asList("lol ok", "u lead noob", "fine"));

        put(Intent.COMMAND_BUFF, Persona.VETERAN, Arrays.asList("buffing", "1 sec", "rebuff inc"));
        put(Intent.COMMAND_BUFF, Persona.NEWBIE, Arrays.asList("sure! buffing now", "ok one moment please", "there you go!"));
        put(Intent.COMMAND_BUFF, Persona.ROLEPLAYER, Arrays.asList("By the grace of Einhasad, receive my blessing.", "May light guide you."));

        put(Intent.COMMAND_INVITE, Persona.VETERAN, Arrays.asList("inv", "sent", "invited"));
        put(Intent.COMMAND_INVITE, Persona.NEWBIE, Arrays.asList("invited you", "party sent", "join us!"));

        put(Intent.COMMAND_TRADE, Persona.MERCHANT, Arrays.asList("wtb cheap", "selling ss c-grade", "trade open"));
        put(Intent.COMMAND_TRADE, Persona.VETERAN, Arrays.asList("trade", "show", "ok"));

        put(Intent.SOCIAL_GREET, Persona.VETERAN, Arrays.asList("sup", "yo", "hey"));
        put(Intent.SOCIAL_GREET, Persona.NEWBIE, Arrays.asList("hi there!", "hello :)", "hey!"));
        put(Intent.SOCIAL_GREET, Persona.ROLEPLAYER, Arrays.asList("Greetings, traveler.", "Well met."));

        put(Intent.SOCIAL_THANKS, Persona.VETERAN, Arrays.asList("np", "yw", "anytime"));
        put(Intent.SOCIAL_THANKS, Persona.NEWBIE, Arrays.asList("you're welcome!", "no problem :)", "happy to help!"));

        put(Intent.SOCIAL_TRASH, Persona.TROLL, Arrays.asList("mad?", "cry more", "ez", "1v1 me then"));
        put(Intent.SOCIAL_TRASH, Persona.VETERAN, Arrays.asList("stfu", "?", "lol"));

        put(Intent.QUESTION_LEVEL, Persona.VETERAN, Arrays.asList("im lvl {level}", "lvl {level}", "{level}"));
        put(Intent.QUESTION_LEVEL, Persona.NEWBIE, Arrays.asList("im level {level} right now", "currently {level}"));

        put(Intent.QUESTION_CLASS, Persona.VETERAN, Arrays.asList("{class}", "im {class}"));
        put(Intent.QUESTION_CLASS, Persona.NEWBIE, Arrays.asList("im a {race} {class}", "playing {class}"));

        put(Intent.QUESTION_FARM, Persona.VETERAN, Arrays.asList("{zone} good for {level_range}", "try {zone}"));
        put(Intent.QUESTION_FARM, Persona.NEWBIE, Arrays.asList("i heard {zone} is good for levels {level_range}", "maybe {zone}?"));
    }

    private static void put(Intent i, Persona p, List<String> lines) {
        POOL.computeIfAbsent(i, k -> new HashMap<>()).put(p, lines);
    }

    public static String pick(Intent intent, Persona persona, Map<String, String> vars, long seed) {
        // seed, not Math.random(): caller passes accountName.hashCode() (or similar
        // per-bot-deterministic value) so the same bot in the same situation is
        // reproducible for testing.
        Map<Persona, List<String>> byPersona = POOL.getOrDefault(intent, Collections.emptyMap());
        List<String> lines = byPersona.get(persona);
        if (lines == null || lines.isEmpty()) {
            lines = byPersona.getOrDefault(Persona.VETERAN, Arrays.asList("..."));
        }
        String tpl = lines.get(new Random(seed).nextInt(lines.size()));
        for (Map.Entry<String, String> e : vars.entrySet()) {
            tpl = tpl.replace("{" + e.getKey() + "}", e.getValue());
        }
        return tpl;
    }
}
