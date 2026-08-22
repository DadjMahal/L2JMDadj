package com.aiplayer.behavior.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * EB-08 — locks the v0 chat reply engine: context-aware canned replies, cooldown, repeat-guard
 * and the LLM-ready {@link ReplySource} seam.
 */
class ChatReplyEngineTest
{
    private static Map<String, String> vars()
    {
        Map<String, String> v = new HashMap<>();
        v.put("name", "Varen");
        v.put("level", "22");
        v.put("class", "Palus Knight");
        v.put("race", "Dark Elf");
        return v;
    }

    // ================================================================
    // Core reply path (canned source)
    // ================================================================

    @Test
    void repliesToKnownGreetingFromCannedPool()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        String reply = e.reply("bot_1", "HumanPlayer", "hi there",
            Persona.VETERAN, vars(), 1_000L);
        assertNotNull(reply);
        assertTrue(Arrays.asList("sup", "yo", "hey").contains(reply), "canned veteran greeting: " + reply);
    }

    @Test
    void substitutesVarsIntoReply()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        String reply = e.reply("bot_1", "HumanPlayer", "ur lvl?",
            Persona.VETERAN, vars(), 1_000L);
        assertNotNull(reply);
        assertTrue(reply.contains("22"), "level var substituted: " + reply);
    }

    @Test
    void staysSilentOnUnknownIntent()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        assertNull(e.reply("bot_1", "AB", "zzqq", Persona.VETERAN, vars(), 1_000L));
        assertEquals(0, e.trackedSpeakers(), "unknown intent must not track a speaker");
    }

    @Test
    void staysSilentOnBlankMessage()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        assertNull(e.reply("bot_1", "AB", "   ", Persona.VETERAN, vars(), 1_000L));
    }

    // ================================================================
    // Cooldown + repeat-guard (per speaker)
    // ================================================================

    @Test
    void suppressSecondReplyWithinCooldown()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        assertNotNull(e.reply("bot_1", "AB", "hi", Persona.VETERAN, vars(), 1_000L));
        assertNull(e.reply("bot_1", "AB", "ty", Persona.VETERAN, vars(), 5_000L),
            "speaker still on cooldown");
    }

    @Test
    void repliesAgainAfterCooldown()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        String first = e.reply("bot_1", "AB", "hi there", Persona.VETERAN, vars(), 1_000L);
        assertNotNull(first);
        String second = e.reply("bot_1", "AB", "hello again", Persona.NEWBIE, vars(), 30_000L);
        assertNotNull(second, "cooldown elapsed and intent differs -> reply again");
    }

    @Test
    void repeatsGuardSkipsExactRepeat()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        String first = e.reply("bot_1", "AB", "hi", Persona.VETERAN, vars(), 1_000L);
        assertNotNull(first);
        // Same bot, same message, same seed -> the canned pool yields the SAME reply.
        assertNull(e.reply("bot_1", "AB", "hi", Persona.VETERAN, vars(), 30_000L),
            "must not parrot the identical reply back-to-back");
    }

    @Test
    void differentSpeakersAreIndependent()
    {
        ChatReplyEngine e = new ChatReplyEngine();
        assertNotNull(e.reply("bot_1", "Alice", "hi", Persona.VETERAN, vars(), 1_000L));
        assertNotNull(e.reply("bot_1", "Bob", "hi", Persona.VETERAN, vars(), 2_000L));
        assertEquals(2, e.trackedSpeakers());
    }

    // ================================================================
    // LLM-ready seam
    // ================================================================

    @Test
    void llmSourceIsConsultedThroughSeam()
    {
        ReplySource llm = context -> "LLM: the Forest of Mirrors is north of Dion.";
        ChatReplyEngine e = new ChatReplyEngine(llm, 1_000L);
        String reply = e.reply("bot_2", "AB", "hey", Persona.VETERAN, vars(), 1_000L);
        assertNotNull(reply);
        assertTrue(reply.startsWith("LLM:"), "custom source reply: " + reply);
    }

    @Test
    void llmSourceSeesIntentAndContext()
    {
        ChatReplyEngine e = new ChatReplyEngine(context -> {
            assertEquals(Intent.COMMAND_FOLLOW, context.intent);
            assertEquals(Persona.TROLL, context.persona);
            assertEquals("AB", context.speaker);
            return context.persona + "/" + context.intent;
        }, 0L);
        assertEquals("TROLL/COMMAND_FOLLOW", e.reply("bot_2", "AB", "follow me", Persona.TROLL, vars(), 5L));
    }

    @Test
    void nullFromSourceStaysSilent()
    {
        ChatReplyEngine e = new ChatReplyEngine(context -> null, 5_000L);
        assertNull(e.reply("bot_2", "AB", "hi", Persona.VETERAN, vars(), 1_000L));
    }
}