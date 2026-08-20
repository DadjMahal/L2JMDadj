package com.aiplayer.behavior;

/** MODE: COMPLETE. Pure quest accept→do→turn-in dialog state machine (STEP 2). No IO, no packets,
 *  no threads — it turns the exact bypass links a quest NPC actually SHOWED us (parsed from the live
 *  NpcHtmlMessage by {@code PacketLogger.extractBypassLinks}) into the ONE next validated bypass to
 *  send, mirroring the proven {@code scripts/c7_live_quest_proof.sh} / QuestFlowLoop rule:
 *     1. "Script" first (open the quest window) unless already sent,
 *     2. the link naming our quest,
 *     3. the objective's completion token (accept-event for ACCEPT, turn-in/finish for TURN_IN),
 *     4. any not-yet-sent ".htm"/"Quest" link as a safe fallback.
 *  It NEVER invents a command the server did not display, and NEVER re-sends a link from the current
 *  dialog session (the caller supplies the set of already-sent commands). When nothing new is
 *  validated it returns "" so the fleet loop can pause for the server's next dialog instead of
 *  guessing. */
import java.util.Set;
import com.aiplayer.examples.QuestFlowLoop;
import com.aiplayer.protocol.PacketCodec;
import com.aiplayer.protocol.PacketLogger;

public final class QuestDialogDriver
{
    /** Whether we are opening the quest acceptance path or the turn-in/completion path. */
    public enum Objective
    {
        /** No active quest yet at this NPC: drive the accept chain (Script → quest → accept event). */
        ACCEPT,
        /** Active quest at its turn-in NPC: drive to the completion/finish bypass. */
        TURN_IN
    }

    /** Immutable definition of the quest dialog we are driving (from quest registry + live config). */
    public static final class QuestDialog
    {
        /** Quest id in the journal space (e.g. 40001 / 30008) — carried for traceability only. */
        public final int questId;
        /** Live dialog token that names the quest inside the NPC html, e.g. "Q00101_SwordOfSolidarity". */
        public final String questName;
        /** What we are driving: accept a new quest vs turn in a completed one. */
        public final Objective objective;
        /** Optional substring that finalizes acceptance (e.g. the start event htm); "" = no preference. */
        public final String acceptToken;
        /** Optional substring that finalizes turn-in (e.g. a "complete"/"finish" bypass); "" = none. */
        public final String turnInToken;

        public QuestDialog(int questId, String questName, Objective objective,
                           String acceptToken, String turnInToken)
        {
            this.questId = questId;
            this.questName = questName != null ? questName : "";
            this.objective = objective != null ? objective : Objective.ACCEPT;
            this.acceptToken = acceptToken != null ? acceptToken : "";
            this.turnInToken = turnInToken != null ? turnInToken : "";
        }
    }

    private QuestDialogDriver()
    {
    }

    /**
     * Pick the single next validated bypass to send, exactly reproducing the proven QuestFlowLoop
     * link rule and extending it with an objective-aware completion token.
     *
     * @param htmlLinks the full command strings of every bypass link the server showed (may be empty)
     * @param def       the quest dialog definition being driven (may be null → no-op "")
     * @param sent      commands already sent in THIS dialog session (never re-sent); mutated only by
     *                  the caller after it actually sends the returned command
     * @return the exact next command to send via {@code PacketCodec.encodeBypass}, or "" when there is
     *         no new validated link (caller pauses and waits for the server's next dialog).
     */
    public static String next(String[] htmlLinks, QuestDialog def, Set<String> sent)
    {
        if (htmlLinks == null || def == null || htmlLinks.length == 0)
        {
            return "";
        }
        Set<String> used = sent != null ? sent : java.util.Collections.emptySet();

        // 1. Open the quest window first (once), exactly like the proven acceptance chain.
        String script = firstExact(htmlLinks, "Script", used);
        if (script != null)
        {
            return script;
        }

        // 2. The link that names the quest we care about.
        if (!def.questName.isEmpty())
        {
            String named = firstContaining(htmlLinks, def.questName, used);
            if (named != null)
            {
                return named;
            }
        }

        // 3. Objective completion token: accepting (start-event) vs turning in (finish).
        String completion = def.objective == Objective.ACCEPT
            ? (!def.acceptToken.isEmpty() ? firstContaining(htmlLinks, def.acceptToken, used) : null)
            : (!def.turnInToken.isEmpty() ? firstContaining(htmlLinks, def.turnInToken, used) : null);
        if (completion != null)
        {
            return completion;
        }

        // 4. Safe fallback: any not-yet-sent quest/dialog link (never an unshown command).
        for (String link : htmlLinks)
        {
            if (link != null && !used.contains(link)
                    && (link.contains(".htm") || containsIgnoreCase(link, "Quest")))
            {
                return link;
            }
        }
        return "";
    }
/**
     * Pure verdict on whether a command we sent completed its side of the dialog (acceptance sent /
     * turn-in sent). Used by the fleet loop to close the dialog session and let the journal refresh.
     */
    public static boolean completes(QuestDialog def, String command)
    {
        if (def == null || command == null)
        {
            return false;
        }
        String token = def.objective == Objective.ACCEPT ? def.acceptToken : def.turnInToken;
        return !token.isEmpty() && command.contains(token);
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private static String firstExact(String[] links, String value, Set<String> used)
    {
        for (String link : links)
        {
            if (link != null && link.equals(value) && !used.contains(link))
            {
                return link;
            }
        }
        return null;
    }

    private static String firstContaining(String[] links, String needle, Set<String> used)
    {
        for (String link : links)
        {
            if (link != null && link.contains(needle) && !used.contains(link))
            {
                return link;
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(String hay, String needle)
    {
        return hay.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }
}
