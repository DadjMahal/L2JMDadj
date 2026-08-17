package com.aiplayer.engine;

/**
 * Party State Management
 * Tracks AI player's party affiliation
 */
public class PartyState {
    private boolean inParty;
    private String partyId;
    private String leader;
    private int memberCount;
    private int maxMembers;
    private long joinedTime;

    public PartyState() {
        this.inParty = false;
        this.partyId = null;
        this.leader = null;
        this.memberCount = 0;
        this.maxMembers = 8;
        this.joinedTime = 0;
    }

    public boolean isInParty() {
        return inParty;
    }

    public void joinParty(String partyId, String leader) {
        this.inParty = true;
        this.partyId = partyId;
        this.leader = leader;
        this.memberCount = 1; // Start with just us
        this.joinedTime = System.currentTimeMillis();
    }

    public void leaveParty() {
        this.inParty = false;
        this.partyId = null;
        this.leader = null;
        this.memberCount = 0;
        this.joinedTime = 0;
    }

    public boolean isLeader() {
        return leader != null && "us".equals(leader); // Placeholder - will check actual leader
    }

    public String getLeader() { return leader; }
    public String getPartyId() { return partyId; }
    public int getMemberCount() { return memberCount; }
    public int getMaxMembers() { return maxMembers; }
    public long getJoinedTime() { return joinedTime; }
}
