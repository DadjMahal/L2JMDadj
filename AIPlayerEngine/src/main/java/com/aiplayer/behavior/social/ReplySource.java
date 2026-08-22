package com.aiplayer.behavior.social;

/**
 * EB-08 — the LLM-READY REPLY SEAM.
 *
 * <p>Every reply decision in the v0 engine funnels through a {@link ReplySource}. The default
 * implementation ({@link TemplateReplySource}) answers from the canned intent+persona pools;
 * a future {@code LlmReplySource} (BR-6) only has to implement this interface and be handed to
 * the engine — no reply-path code changes.
 */
public interface ReplySource
{
    /**
     * Produce a reply for the context, or {@code null} when the source has nothing to say
     * (the engine then stays silent — never forces a non-answer).
     */
    String reply(ReplyContext context);
}