package com.aiplayer.behavior.social;

import java.util.HashMap;
import java.util.Map;

/**
 * EB-08 — the v0 CHAT REPLY ENGINE (pure, deterministic, LLM-ready).
 *
 * <p>Orchestrates context → reply through the {@link ReplySource} seam, with two guards
 * that make the bot a believable conversationalist instead of a parrot:
 * <ul>
 *   <li><b>cooldown</b> — never answer the same speaker twice inside {@code cooldownMs};</li>
 *   <li><b>repeat-guard</b> — never re-emit the previous reply given to that speaker.</li>
 * </ul>
 * Everything is in-memory + deterministic: no sockets, no IO, no threads.
 */
public final class ChatReplyEngine
{
    /** Default min gap between two replies to the same speaker. */
    public static final long DEFAULT_COOLDOWN_MS = 15_000;

    private final ReplySource source;
    private final long cooldownMs;
    private final Map<String, Turn> lastBySpeaker = new HashMap<>();

    private static final class Turn
    {
        final String reply;
        final long atMs;

        Turn(String reply, long atMs)
        {
            this.reply = reply;
            this.atMs = atMs;
        }
    }

    public ChatReplyEngine()
    {
        this(new TemplateReplySource(), DEFAULT_COOLDOWN_MS);
    }

    public ChatReplyEngine(ReplySource source, long cooldownMs)
    {
        this.source = source != null ? source : new TemplateReplySource();
        this.cooldownMs = Math.max(0, cooldownMs);
    }

    /**
     * Classify and answer {@code what} said by {@code speaker} (if a reply is due).
     *
     * @return the reply to send, or {@code null} when the engine stays silent
     *         (unknown intent / speaker on cooldown / source had nothing to say).
     */
    public String reply(String botAccount, String speaker, String what,
                        Persona persona, Map<String, String> vars, long nowMs)
    {
        if (what == null || what.isBlank())
        {
            return null;
        }
        Intent intent = IntentClassifier.classify(what);

        Turn prev = speaker == null ? null : lastBySpeaker.get(speaker);
        if (prev != null && nowMs - prev.atMs < cooldownMs)
        {
            return null; // speaker on cooldown
        }

        ReplyContext ctx = new ReplyContext(botAccount, speaker, what, intent,
            persona, vars, prev == null ? null : prev.reply, nowMs);

        String reply = source.reply(ctx);
        if (reply == null || reply.isBlank())
        {
            return null;
        }
        if (prev != null && prev.reply.equals(reply))
        {
            return null; // repeat-guard: already said exactly this to this speaker
        }

        lastBySpeaker.put(speaker == null ? "" : speaker, new Turn(reply, nowMs));
        return reply;
    }

    /** How many speakers are currently tracked (test/telemetry helper). */
    public int trackedSpeakers()
    {
        return lastBySpeaker.size();
    }
}