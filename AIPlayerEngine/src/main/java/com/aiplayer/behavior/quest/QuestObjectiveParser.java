package com.aiplayer.behavior.quest;

import java.util.Locale;

/**
 * S3-T02 — extracts the "next objective from the live server" as a structured value.
 *
 * <p>The server expresses a quest's current objective through (a) the QUEST_LIST (0x80) journal
 * state and (b) the NPC-dialog HTML body it actually sends (which the engine already captures via
 * {@code PacketLogger.getLastNpcHtml()}). This parser turns that live HTML + journal state into a
 * plain-text objective + an inferred step flavor (TALK / KILL / COLLECT / UNKNOWN).
 *
 * <p>Pure + deterministic: no IO, no sockets. STEP recognition uses the same vocabulary the
 * datapack quest classes write (deliver/talk vs kill/defeat vs collect/find/bring), so it is
 * testable against real quest html (see QuestObjectiveParserTest fixtures from
 * Q00101_SwordOfSolidarity).
 */
public final class QuestObjectiveParser
{
    /** Inferred nature of the current objective step. */
    public enum StepType
    {
        TALK, KILL, COLLECT, UNKNOWN
    }

    /** A parsed objective. */
    public static final class Parsed
    {
        public final int questId;
        public final int state;
        public final String text;
        public final StepType step;

        private Parsed(int questId, int state, String text, StepType step)
        {
            this.questId = questId;
            this.state = state;
            this.text = text;
            this.step = step;
        }

        @Override
        public String toString()
        {
            return "q" + questId + "[s" + state + "] " + step + ": " + text;
        }
    }

    private static final String[] TALK_HINTS = {
        "talk to", "speak with", "deliver", "bring this to", "give this to", "go and see", "take this to",
        "go to", "return to"
    };
    private static final String[] KILL_HINTS = {"kill", "defeat", "slay", "destroy", "vanquish", "hunt"};
    private static final String[] COLLECT_HINTS = {"collect", "gather", "bring back", "recover", "look for", "search for", "find the"};

    private QuestObjectiveParser()
    {
    }

    /**
     * Parse the live quest dialog body into an objective.
     *
     * @param questId journal quest id (from QUEST_LIST)
     * @param state   journal state value for that quest
     * @param html    the LAST NPC html the server sent (may carry the current objective text)
     * @return a Parsed objective, or null when html is blank (nothing to conclude — never fabricate).
     */
    public static Parsed parse(int questId, int state, String html)
    {
        String text = toPlainText(html);
        if (text == null || text.isEmpty())
        {
            return null;
        }
        StepType step = inferStep(text);
        return new Parsed(questId, state, text, step);
    }

    /** Strip HTML tags/entities to plain text (compact, single-spaced). */
    static String toPlainText(String html)
    {
        if (html == null || html.isBlank())
        {
            return "";
        }
        String s = html.replaceAll("(?s)<[^>]*>", " ");   // drop tags
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&amp;", "&").replace("&quot;", "\"").replaceAll("\\s+", " ");
        return s.trim();
    }

    static StepType inferStep(String plainText)
    {
        String lower = plainText.toLowerCase(Locale.ROOT);
        for (String hint : KILL_HINTS)
        {
            if (lower.contains(hint))
            {
                return StepType.KILL;
            }
        }
        for (String hint : COLLECT_HINTS)
        {
            if (lower.contains(hint))
            {
                return StepType.COLLECT;
            }
        }
        for (String hint : TALK_HINTS)
        {
            if (lower.contains(hint))
            {
                return StepType.TALK;
            }
        }
        return StepType.UNKNOWN;
    }
}