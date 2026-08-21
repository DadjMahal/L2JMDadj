package com.aiplayer.core;

/** MODE: COMPLETE (re-verified 2026-08-17, S10-T08). Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.UUID;

/**
 * Immutable identity snapshot for Phase 0.
 * Mirrors ai_cabinet.ai_profiles row.
 */
public class BotProfile {
    private final UUID botId;
    private final String name;
    private final String accountName;
    private final int race;
    private final int classCurrent;
    private final int classTarget;
    private final int level;
    private final int levelGoal;
    private final String persona;
    private final double aggressive;
    private final double friendly;
    private final double troll;

    public BotProfile(UUID botId, String name, String accountName, int race,
                      int classCurrent, int classTarget, int level, int levelGoal,
                      String persona, double aggressive, double friendly, double troll) {
        this.botId = botId;
        this.name = name;
        this.accountName = accountName;
        this.race = race;
        this.classCurrent = classCurrent;
        this.classTarget = classTarget;
        this.level = level;
        this.levelGoal = levelGoal;
        this.persona = persona;
        this.aggressive = aggressive;
        this.friendly = friendly;
        this.troll = troll;
    }

    public UUID getBotId() { return botId; }
    public String getName() { return name; }
    public String getAccountName() { return accountName; }
    public int getRace() { return race; }
    public int getClassCurrent() { return classCurrent; }
    public int getClassTarget() { return classTarget; }
    public int getLevel() { return level; }
    public int getLevelGoal() { return levelGoal; }
    public String getPersona() { return persona; }
    public double getAggressive() { return aggressive; }
    public double getFriendly() { return friendly; }
    public double getTroll() { return troll; }

    public boolean isHealer() {
        int cid = classCurrent;
        return cid == 25 || cid == 29 || cid == 15 || cid == 17;
    }

    public boolean isBuffer() {
        int cid = classCurrent;
        return cid == 16 || cid == 21 || cid == 30 || cid == 32;
    }

    public boolean isMage() {
        int cid = classCurrent;
        return cid >= 10 && cid <= 14 || cid >= 26 && cid <= 29 || cid == 11 || cid == 12;
    }

    public boolean isArcher() {
        int cid = classCurrent;
        return cid == 18 || cid == 22 || cid == 35;
    }
}
