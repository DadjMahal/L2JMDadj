package com.aiplayer.engine;

/**
 * Social Decision Result
 * Contains the decision about what social action to take
 */
public class SocialDecision {
    public enum Action {
        IDLE,
        INVITE_TO_PARTY,
        JOIN_PARTY,
        LEAVE_PARTY,
        APPLY_TO_CLAN,
        JOIN_CLAN,
        LEAVE_CLAN,
        CHAT,
        COORDINATE_PARTY,
        FOLLOW_LEADER,
        ASSIST_PARTY,
        CLAN_ACTIVITY
    }
    
    private final Action action;
    private final String targetId;
    private final String message;
    private final int x, y, z;
    private final boolean shouldExecute;
    private final long timestamp;
    
    private SocialDecision(Action action, String targetId, String message, int x, int y, int z, boolean shouldExecute) {
        this.action = action;
        this.targetId = targetId;
        this.message = message;
        this.x = x;
        this.y = y;
        this.z = z;
        this.shouldExecute = shouldExecute;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Factory methods
    public static SocialDecision idle() {
        return new SocialDecision(Action.IDLE, null, null, 0, 0, 0, false);
    }
    
    public static SocialDecision inviteToParty(String targetId, int x, int y, int z) {
        return new SocialDecision(Action.INVITE_TO_PARTY, targetId, null, x, y, z, true);
    }
    
    public static SocialDecision joinParty(String partyId) {
        return new SocialDecision(Action.JOIN_PARTY, partyId, null, 0, 0, 0, true);
    }
    
    public static SocialDecision leaveParty() {
        return new SocialDecision(Action.LEAVE_PARTY, null, null, 0, 0, 0, true);
    }
    
    public static SocialDecision applyToClan(String clanName) {
        return new SocialDecision(Action.APPLY_TO_CLAN, clanName, null, 0, 0, 0, true);
    }
    
    public static SocialDecision joinClan(String clanId) {
        return new SocialDecision(Action.JOIN_CLAN, clanId, null, 0, 0, 0, true);
    }
    
    public static SocialDecision leaveClan() {
        return new SocialDecision(Action.LEAVE_CLAN, null, null, 0, 0, 0, true);
    }
    
    public static SocialDecision chat(String message) {
        return new SocialDecision(Action.CHAT, null, message, 0, 0, 0, true);
    }
    
    public static SocialDecision coordinateParty() {
        return new SocialDecision(Action.COORDINATE_PARTY, null, null, 0, 0, 0, true);
    }
    
    public static SocialDecision followLeader(String leaderId) {
        return new SocialDecision(Action.FOLLOW_LEADER, leaderId, null, 0, 0, 0, true);
    }
    
    public static SocialDecision assistParty() {
        return new SocialDecision(Action.ASSIST_PARTY, null, null, 0, 0, 0, true);
    }
    
    public static SocialDecision clanActivity() {
        return new SocialDecision(Action.CLAN_ACTIVITY, null, null, 0, 0, 0, true);
    }
    
    // Getters
    public Action getAction() { return action; }
    public String getTargetId() { return targetId; }
    public String getMessage() { return message; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public boolean shouldExecute() { return shouldExecute; }
    public long getTimestamp() { return timestamp; }
}