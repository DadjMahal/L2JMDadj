package com.aiplayer.behavior.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class IntentClassifier {
    private static final List<Map.Entry<Intent, Pattern>> PATTERNS = Arrays.asList(
        Map.entry(Intent.COMMAND_FOLLOW, Pattern.compile("(follow|come|follow me|come here)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.COMMAND_BUFF, Pattern.compile("(buff|buffs|buff me|rebuff|rebuff pls)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.COMMAND_INVITE, Pattern.compile("(party|invite|pt|party me)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.COMMAND_TRADE, Pattern.compile("(trade|wtb|wts|sell|buy|shop)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.SOCIAL_GREET, Pattern.compile("(hi|hello|hey|sup|yo|hola)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.SOCIAL_THANKS, Pattern.compile("(ty|thx|thanks|thank you|tyvm)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.SOCIAL_TRASH, Pattern.compile("(noob|ez|mad|get rekt|cry|l2p)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.QUESTION_LEVEL, Pattern.compile("(what level|ur lvl|your level|what lvl)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.QUESTION_CLASS, Pattern.compile("(what class|ur class|your class)", Pattern.CASE_INSENSITIVE)),
        Map.entry(Intent.QUESTION_FARM, Pattern.compile("(where.*farm|where.*xp|where.*level|good spot)", Pattern.CASE_INSENSITIVE))
    );

    public static Intent classify(String input) {
        for (Map.Entry<Intent, Pattern> e : PATTERNS) {
            if (e.getValue().matcher(input).find()) return e.getKey();
        }
        return Intent.UNKNOWN;
    }
}
