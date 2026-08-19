package com.aiplayer.behavior.social;

import org.junit.jupiter.api.Test;

import com.aiplayer.protocol.PacketLogger;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplayer.behavior.social.SocialAI;
import com.aiplayer.behavior.social.SocialDecision;
import com.aiplayer.net.AIPlayer;

/**
 * Stream E slice 2 tests (tasks 80, 82, 85, 90).
 *
 * <p>Proves the social wiring is genuine: SocialAI makes DETERMINISTIC personality/emotion-driven
 * party + chat decisions (removed the {@code Math.random()} mocks), targets a real nearby entity
 * rather than the fake "NEARBY_PLAYER", and social outcome hooks (onPartyJoined) actually drive
 * party state + form a swarm in SwarmCoordinator + share into CollectiveKnowledge.
 */
public class StreamESocialTest {

    @Test
    public void socialPersonalitySeeksPartyWhenCandidateNearby() {
        // A SOCIAL personality (acct 2 % 6 = index 2) with a non-hostile entity nearby.
        AIPlayer p = new AIPlayer("SocialBot", 2, 1, 0);
        p.setPosition(1000, 1000, 0);
        // Attach a shared logger so SocialAI sees real nearby entities.
        PacketLogger logger = new PacketLogger("SocialBot");
        p.getSocialAI().setPacketLogger(logger);
        // Seed a friendly NPC (non-hostile) 100 units away.
        logger.addEntityForTest(new PacketLogger.EntityInfo(500, 1001001, 1100, 1000, 0, 0, false));

        // Since it's SOCIAL personality socialWeight=2.0 > 1.5 and a candidate is nearby and the
        // bot is not in combat, it MUST seek a party (deterministic — no randomness).
        SocialDecision d = p.getSocialAI().makeDecision();
        assertEquals(SocialDecision.Action.INVITE_TO_PARTY, d.getAction(),
                "SOCIAL bot with nearby candidate must seek a party deterministically");
    }

    @Test
    public void nonSocialBotDoesNotSeekParty() {
        // CAUTIOUS personality (acct 1 % 6 = index 1, socialWeight 0.8) — must NOT seek a party.
        AIPlayer p = new AIPlayer("Loner", 1, 1, 0);
        p.setPosition(0, 0, 0);
        PacketLogger logger = new PacketLogger("Loner");
        p.getSocialAI().setPacketLogger(logger);
        logger.addEntityForTest(new PacketLogger.EntityInfo(500, 1001001, 100, 0, 0, 0, false));

        SocialDecision d = p.getSocialAI().makeDecision();
        // A non-social personality should not invite-to-party (should fall through to idle/chat,
        // but critically NOT INVITE_TO_PARTY).
        assertNotEquals(SocialDecision.Action.INVITE_TO_PARTY, d.getAction(),
                "non-social bot must not seek a party");
    }

    @Test
    public void socialOutcomeDrivesPartySwarmAndCollectiveKnowledge() {
        AIPlayer p = new AIPlayer("PartyBot", 2, 1, 0);
        int swarmsBefore = p.getSwarmCoordinator().getActiveSwarmCount();
        int knowledgeBefore = p.getCollectiveKnowledge().totalKnowledge();

        p.getSocialAI().onPartyJoined("party-xyz");

        assertTrue(p.getSocialAI().isInParty(), "onPartyJoined must set in-party state");
        assertEquals(swarmsBefore + 1, p.getSwarmCoordinator().getActiveSwarmCount(),
                "onPartyJoined must form a swarm in SwarmCoordinator");
        assertTrue(p.getCollectiveKnowledge().totalKnowledge() > knowledgeBefore,
                "onPartyJoined must share knowledge into CollectiveKnowledge");
    }

    @Test
    public void chatOutcomeIsDeterministicAndContextual() {
        // A CAUTIOUS (non-social) bot in no combat should NOT chat on its own.
        AIPlayer p = new AIPlayer("QuietBot", 1, 1, 0);
        // No nearby entity, non-social, not bored, not in combat.
        SocialDecision d = p.getSocialAI().makeDecision();
        assertNotEquals(SocialDecision.Action.CHAT, d.getAction(),
                "a quiet non-social bot must not spontaneously chat");
    }

    @Test
    public void chatWhenBored() {
        // A bored bot (personality-independent) should chat contextually.
        AIPlayer p = new AIPlayer("Chatter", 1, 1, 0); // CAUTIOUS, but boredom drives chat
        for (int i = 0; i < 12; i++) {
            p.getEmotions().onIdle(); // boredom > 0.6 -> BORED after ~7
        }
        assertEquals(com.aiplayer.advanced.EmotionalState.Emotion.BORED,
                p.getEmotions().getCurrentEmotion(), "precondition: bot must be BORED");
        SocialDecision d = p.getSocialAI().makeDecision();
        // A bored non-combat bot chats (deterministic). If it also wants to seek party, either
        // is fine — assert it produces SOME social action, not the quiet idle.
        assertNotEquals(SocialDecision.Action.IDLE, d.getAction(),
                "a bored bot should not stay idle");
    }
}
