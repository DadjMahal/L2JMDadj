package com.aiplayer.engine;

import java.util.logging.Logger;

import com.aiplayer.protocol.PacketLogger;

/**
 * Social AI Module
 * Handles clan joining, party formation, chat, and social interactions for AI players
 * Integrates with L2JMobius clan, party, and communication systems
 * Telemetry: PacketLogger tracks CharInfo/NPC_INFO packets for social detection
 */
public class SocialAI {
    private static final Logger LOGGER = Logger.getLogger(SocialAI.class.getName());
    
    private final AIPlayer aiPlayer;
    private final SocialConfig config;
    private PacketLogger packetLogger;
    private ClanState clanState;
    private PartyState partyState;
    
    public SocialAI(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.config = SocialConfig.getInstance();
        this.packetLogger = new PacketLogger(aiPlayer.getName());
        this.clanState = new ClanState();
        this.partyState = new PartyState();
    }

    /** Get the packet logger for telemetry. */
    public PacketLogger getPacketLogger() { return packetLogger; }

    /** Stream E (task 80): attach the LIVE reader's packet logger so social decisions see real
     *  nearby players/NPCs instead of an empty private buffer. */
    public void setPacketLogger(PacketLogger logger) {
        if (logger != null) this.packetLogger = logger;
    }
    /** Expose party state for telemetry / tests. */
    public PartyState getPartyState() { return partyState; }
    public boolean isInParty() { return partyState.isInParty(); }

    // --- Stream E (tasks 80, 85, 90): social outcome feedback + collective knowledge hooks ---
    // These mirror the Stream D/E1 pattern: party/comms outcomes now drive emotion and share the
    // discovery with the shared collective knowledge base (impossible before — no getter existed).

    /** Called when the bot forms or joins a party. */
    public void onPartyJoined(String partyId) {
        partyState.joinParty(partyId, aiPlayer.getName());
        // Form/join a swarm in the shared coordinator (party = a coordinated swarm).
        aiPlayer.getSwarmCoordinator().formSwarm(
                java.util.Collections.singletonList(aiPlayer.getName()),
                java.util.Collections.singletonMap(aiPlayer.getName(), aiPlayer.getLevel()),
                "hunt");
        // Share the party/spot knowledge with the collective.
        aiPlayer.getCollectiveKnowledge().share(aiPlayer.getName(), "hunting_spot",
                "party_" + partyId, "formed parties at lv" + aiPlayer.getLevel(), 1.0);
        // A successful social join is mildly positive for emotion (reduces boredom).
        if (aiPlayer.getEmotions().getCurrentEmotion()
                == com.aiplayer.advanced.EmotionalState.Emotion.BORED) {
            aiPlayer.getEmotions().decay();
        }
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] PARTY_JOINED: " + partyId
                + " emotion=" + aiPlayer.getEmotions().getCurrentEmotion());
    }

    /** Called when the bot leaves/disbands a party. */
    public void onPartyLeft(String partyId) {
        partyState.leaveParty();
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] PARTY_LEFT: " + partyId);
    }

    
    /**
     * Main social decision method
     * Decides what social action to take
     */
    public SocialDecision makeDecision() {
        if (!config.isEnabled()) {
            return SocialDecision.idle();
        }
        
        try {
            // Check party status
            if (inParty()) {
                return managePartyActivity();
            }
            
            // Check clan status
            if (inClan()) {
                return manageClanActivity();
            }
            
            // Look for party invitation opportunities
            if (config.isPartyInviteEnabled() && shouldSeekParty()) {
                return seekParty();
            }
            
            // Look for clan opportunities
            if (config.isClanJoinEnabled() && shouldSeekClan()) {
                return seekClan();
            }
            
            // Chat/IDLE behavior
            if (config.isChatEnabled() && shouldChat()) {
                return generateChat();
            }
            
            return SocialDecision.idle();
            
        } catch (Exception e) {
            LOGGER.warning("Social AI error for " + aiPlayer.getName() + ": " + e.getMessage());
            return SocialDecision.idle();
        }
    }
    
    private SocialDecision managePartyActivity() {
        // Help party leader
        if (partyState.isLeader()) {
            return managePartyLeadership();
        }
        
        // Follow leader
        if (partyState.getLeader() != null) {
            return followPartyLeader();
        }
        
        // Participate in party activities (loot, quests, etc.)
        return participateInParty();
    }
    
    private boolean inParty() {
        return partyState.isInParty();
    }
    
    private SocialDecision managePartyLeadership() {
        // Invite helpful players
        // Coordinate activities
        // Distribute loot
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] PARTY_COORDINATION");
        return SocialDecision.coordinateParty();
    }
    
    private SocialDecision followPartyLeader() {
        // Follow leader to hunting grounds
        // Assist in combat
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] FOLLOW_PARTY_LEADER");
        return SocialDecision.followLeader(partyState.getLeader());
    }
    
    private SocialDecision participateInParty() {
        // Share loot fairly
        // Assist nearby members
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] PARTICIPATE_IN_PARTY");
        return SocialDecision.assistParty();
    }
    
    private boolean inClan() {
        return clanState.isInClan();
    }
    
    private SocialDecision manageClanActivity() {
        // Participate in clan events
        // Use clan warehouse
        // Help other members
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] CLAN_ACTIVITY");
        return SocialDecision.clanActivity();
    }
    private boolean shouldSeekParty() {
        // Stream E (task 80): DETERMINISTIC — replaced Math.random(). A SOCIAL personality
        // (socialWeight>1.5) or a bored bot seeks a party when there are nearby candidates.
        double socialWeight = aiPlayer.getPersonality().getSocialWeight();
        boolean socialPersonality = socialWeight > 1.5;
        boolean bored = aiPlayer.getEmotions().getCurrentEmotion()
                == com.aiplayer.advanced.EmotionalState.Emotion.BORED;
        if (aiPlayer.getCombatAI() != null && aiPlayer.getCombatAI().getSelectedTargetObjId() > 0) {
            return false; // busy fighting — don't drop combat to party-chat
        }
        return (socialPersonality || bored) && hasNearbyCandidates();
    }

    private boolean shouldSeekClan() {
        // Deterministic: clan-seeking scales with the bot's social weight.
        return aiPlayer.getPersonality().getSocialWeight() > 1.2;
    }

    private boolean hasNearbyCandidates() {
        if (packetLogger == null) return false;
        PacketLogger.EntityInfo[] nearby = packetLogger.getNearbyEntities(
                aiPlayer.getX(), aiPlayer.getY(), 1200);
        for (PacketLogger.EntityInfo e : nearby) {
            if (!e.isHostile) return true;
        }
        return false;
    }

    private SocialDecision seekParty() {
        // Find a non-hostile nearby entity as the invite target (real data, not "NEARBY_PLAYER").
        String targetId = "NEARBY_PLAYER";
        PacketLogger.EntityInfo[] nearby = packetLogger.getNearbyEntities(
                aiPlayer.getX(), aiPlayer.getY(), 1200);
        for (PacketLogger.EntityInfo e : nearby) {
            if (!e.isHostile) {
                targetId = "objId=" + e.objectId;
                break;
            }
        }
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] PARTY_INVITE: target=" + targetId
                + " socialWeight=" + aiPlayer.getPersonality().getSocialWeight());
        return SocialDecision.inviteToParty(targetId, aiPlayer.getX(), aiPlayer.getY(), aiPlayer.getZ());
    }

    private SocialDecision seekClan() {
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] CLAN_APPLICATION: "
                + aiPlayer.getName() + "-guild");
        return SocialDecision.applyToClan(aiPlayer.getName() + "-guild");
    }

    private boolean shouldChat() {
        boolean socialOrBored = aiPlayer.getPersonality().getSocialWeight() > 1.3
                || aiPlayer.getEmotions().getCurrentEmotion()
                   == com.aiplayer.advanced.EmotionalState.Emotion.BORED;
        boolean inCombat = aiPlayer.getCombatAI() != null
                && aiPlayer.getCombatAI().getSelectedTargetObjId() > 0;
        return socialOrBored && !inCombat;
    }

    private SocialDecision generateChat() {
        String message;
        if (aiPlayer.getEmotions().getCurrentEmotion()
                == com.aiplayer.advanced.EmotionalState.Emotion.BORED) {
            message = "Anyone around to hunt with?";
        } else if (aiPlayer.getEmotions().getConfidenceLevel() > 0.7) {
            message = "I'm feeling strong today - good hunting spot open!";
        } else {
            message = "Hello there!";
        }
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] CHAT: message=\"" + message + "\"");
        return SocialDecision.chat(message);
    }

    
}