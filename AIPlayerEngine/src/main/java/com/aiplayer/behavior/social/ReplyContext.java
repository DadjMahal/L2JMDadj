package com.aiplayer.behavior.social;

import java.util.Collections;
import java.util.Map;

/**
 * EB-08 — the immutable CONTEXT handed to a {@link ReplySource}.
 *
 * <p>Carries everything a reply decision needs: who said what, the classified intent, the
 * persona the bot plays, template variables ({name}, {level}, {class}...), the previous reply
 * the bot gave to this same speaker (anti-parrot guard) and the wall-clock now for cooldowns.
 */
public final class ReplyContext
{
    public final String botAccount;
    public final String speaker;
    public final String message;
    public final Intent intent;
    public final Persona persona;
    public final Map<String, String> vars;
    public final String lastReply;
    public final long nowMs;

    public ReplyContext(String botAccount, String speaker, String message, Intent intent,
                        Persona persona, Map<String, String> vars,
                        String lastReply, long nowMs)
    {
        this.botAccount = botAccount;
        this.speaker = speaker;
        this.message = message;
        this.intent = intent;
        this.persona = persona != null ? persona : Persona.VETERAN;
        this.vars = vars != null ? Collections.unmodifiableMap(vars) : Collections.emptyMap();
        this.lastReply = lastReply;
        this.nowMs = nowMs;
    }
}