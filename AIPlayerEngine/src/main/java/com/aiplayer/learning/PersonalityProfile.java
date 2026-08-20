package com.aiplayer.learning;
import com.aiplayer.behavior.AIBrain;

/**
 * Personality Profile - Task 74
 *
 * Defines distinct AI personalities so each AI player feels UNIQUE,
 * like a real human player with their own playstyle.
 *
 * Personalities influence decision weights in the AIBrain:
 *  - AGGRESSIVE: prioritizes combat, takes risks, fights higher-level mobs
 *  - CAUTIOUS:   prioritizes safety, avoids danger, grinds conservatively
 *  - SOCIAL:     prioritizes party/clan activities, chats frequently
 *  - MERCHANT:   prioritizes trading, profit optimization, market scanning
 *  - EXPLORER:   prioritizes discovery, travels to new zones, maps areas
 *  - COMPLETIONIST: prioritizes quests, completes every quest chain
 */
public class PersonalityProfile {
    public enum Personality {
        AGGRESSIVE,
        CAUTIOUS,
        SOCIAL,
        MERCHANT,
        EXPLORER,
        COMPLETIONIST
    }

    private final Personality personality;

    // Decision weight modifiers (0.0 to 2.0, where 1.0 = neutral)
    private final double combatWeight;
    private final double safetyWeight;
    private final double socialWeight;
    private final double tradeWeight;
    private final double exploreWeight;
    private final double questWeight;

    public PersonalityProfile(Personality personality) {
        this.personality = personality;
        // Configure weights based on personality type
        switch (personality) {
            case AGGRESSIVE:
                this.combatWeight = 1.8; this.safetyWeight = 0.4;
                this.socialWeight = 0.6; this.tradeWeight = 0.5;
                this.exploreWeight = 1.0; this.questWeight = 0.8;
                break;
            case CAUTIOUS:
                this.combatWeight = 0.6; this.safetyWeight = 1.9;
                this.socialWeight = 0.8; this.tradeWeight = 1.0;
                this.exploreWeight = 0.5; this.questWeight = 1.2;
                break;
            case SOCIAL:
                this.combatWeight = 0.7; this.safetyWeight = 1.0;
                this.socialWeight = 2.0; this.tradeWeight = 0.8;
                this.exploreWeight = 0.6; this.questWeight = 1.0;
                break;
            case MERCHANT:
                this.combatWeight = 0.5; this.safetyWeight = 1.2;
                this.socialWeight = 1.0; this.tradeWeight = 2.0;
                this.exploreWeight = 0.4; this.questWeight = 0.6;
                break;
            case EXPLORER:
                this.combatWeight = 0.8; this.safetyWeight = 0.9;
                this.socialWeight = 0.7; this.tradeWeight = 0.5;
                this.exploreWeight = 2.0; this.questWeight = 1.0;
                break;
            case COMPLETIONIST:
                this.combatWeight = 0.9; this.safetyWeight = 1.0;
                this.socialWeight = 0.8; this.tradeWeight = 0.7;
                this.exploreWeight = 0.8; this.questWeight = 2.0;
                break;
            default:
                this.combatWeight = 1.0; this.safetyWeight = 1.0;
                this.socialWeight = 1.0; this.tradeWeight = 1.0;
                this.exploreWeight = 1.0; this.questWeight = 1.0;
                break;
        }
    }

    public Personality getPersonality() { return personality; }
    public double getCombatWeight() { return combatWeight; }
    public double getSafetyWeight() { return safetyWeight; }
    public double getSocialWeight() { return socialWeight; }
    public double getTradeWeight() { return tradeWeight; }
    public double getExploreWeight() { return exploreWeight; }
    public double getQuestWeight() { return questWeight; }

    @Override
    public String toString() {
        return "Personality{" + personality + "}";
    }
}
