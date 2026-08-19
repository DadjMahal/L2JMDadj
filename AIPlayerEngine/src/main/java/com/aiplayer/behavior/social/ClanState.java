package com.aiplayer.behavior.social;

/**
 * Clan State Management
 * Tracks AI player's clan affiliation and status
 */
public class ClanState {
    private boolean inClan;
    private String clanId;
    private String clanName;
    private int clanRank;
    private int clanLevel;
    private boolean isLeader;
    private long joinedTime;

    public ClanState() {
        this.inClan = false;
        this.clanId = null;
        this.clanName = null;
        this.clanRank = 0;
        this.clanLevel = 0;
        this.isLeader = false;
        this.joinedTime = 0;
    }

    public boolean isInClan() {
        return inClan;
    }

    public void joinClan(String clanId, String clanName, int rank, int level) {
        this.inClan = true;
        this.clanId = clanId;
        this.clanName = clanName;
        this.clanRank = rank;
        this.clanLevel = level;
        this.isLeader = rank == 0; // Assuming rank 0 is leader
        this.joinedTime = System.currentTimeMillis();
    }

    public void leaveClan() {
        this.inClan = false;
        this.clanId = null;
        this.clanName = null;
        this.clanRank = 0;
        this.clanLevel = 0;
        this.isLeader = false;
        this.joinedTime = 0;
    }

    public String getClanId() { return clanId; }
    public String getClanName() { return clanName; }
    public int getClanRank() { return clanRank; }
    public int getClanLevel() { return clanLevel; }
    public boolean isLeader() { return isLeader; }
    public long getJoinedTime() { return joinedTime; }
}
