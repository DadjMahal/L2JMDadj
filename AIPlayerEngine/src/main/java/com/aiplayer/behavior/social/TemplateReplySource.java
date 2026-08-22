package com.aiplayer.behavior.social;

/**
 * EB-08 — the Canned (v0) {@link ReplySource}: template + canned replies per context.
 *
 * <p>Thin pure adapter over the existing {@link IntentClassifier} + {@link ResponseTemplate}
 * pools. Says nothing (returns null) for {@link Intent#UNKNOWN}, so the engine never answers
 * with a generic "...". Seeded per bot (account hash) for deterministic, reproducible replies.
 */
public final class TemplateReplySource implements ReplySource
{
    @Override
    public String reply(ReplyContext context)
    {
        if (context == null || context.intent == Intent.UNKNOWN)
        {
            return null;
        }
        String reply = ResponseTemplate.pick(context.intent, context.persona, context.vars,
            context.botAccount == null ? 0L : context.botAccount.hashCode());
        if (reply == null || reply.isBlank())
        {
            return null;
        }
        return reply.trim();
    }
}