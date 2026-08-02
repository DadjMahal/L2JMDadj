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
    private final PacketLogger packetLogger;
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
        // Analyze current situation
        // Check level compatibility
        // Assess loneliness (time since last party)
        return Math.random() < config.getPartyInviteProbability();
    }
    
    private boolean shouldSeekClan() {
        // Check if we need clan benefits
        // Assess clan worthiness
        // Determine if lonely
        return Math.random() < config.getClanJoinProbability();
    }
    
    private SocialDecision seekParty() {
        // Find players at similar level
        // Send party invitation
        // Wait for responses
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] PARTY_INVITE: target=NEARBY_PLAYER");
        return SocialDecision.inviteToParty("NEARBY_PLAYER", 16600, 17000, 434);
    }
    
    private SocialDecision seekClan() {
        // Find suitable clans (level, reputation, activities)
        // Apply to clan
        // Accept clan invites
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] CLAN_APPLICATION: clan=NOVICE_CLAN");
        return SocialDecision.applyToClan("NOVICE_CLAN");
    }
    
    private boolean shouldChat() {
        // Chat randomly to appear human
        return Math.random() < config.getChatProbability();
    }
    
        private SocialDecision generateChat() {
        // Generate natural-sounding chat
        String[] messages = {
            "Hello there!",
            "Good hunting today?",
            "Anyone need help?",
            "Beautiful weather we're having...",
            "Watch out for that mob!",
            "Good XP gain!",
            "Thanks for the party!",
            "See you around!"
        };
        
        String message = messages[(int)(Math.random() * messages.length)];
        LOGGER.info("[SOCIAL-LOG] [" + aiPlayer.getName() + "] CHAT: message=\"" + message + "\"");
        return SocialDecision.chat(message);
    }
}