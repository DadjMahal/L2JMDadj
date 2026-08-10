package com.aiplayer.phase0.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.Random;

/**
 * Personality profile for an AI Player that drives chat style, verbosity,
 * social aggressiveness, and response likelihood.
 *
 * Phase 0: 8 hardcoded archetypes seeded from account name hash.
 * Phase 1: Load from player config DB.
 */
public final class ChatPersonality {

    public enum Archetype {
        FRIENDLY,    // Helpful, uses smileys, greets often
        TROLL,       // Sarcastic, contrarian, provokes
        QUIET,       // Rarely speaks, short replies
        TRADER,      // Always selling/buying, trade chat focused
        LEADER,      // Gives orders, invites to party, commanding
        NEWBIE,      // Asks questions, naive tone, grateful
        GRINDER,     // Focused on farm, minimal chat, efficiency
        ROLEPLAYER   // Uses /s emotes, lore-appropriate language
    }

    // Personality dimensions (0.0 - 1.0)
    public final Archetype archetype;
    public final double talkativeness;     // base chance to initiate chat
    public final double replyLikelihood;   // chance to reply when addressed
    public final double verbosity;         // short vs long messages
    public final double politeness;        // rude vs polite tone
    public final double humor;             // joke / meme inclination
    public final double tradeFocus;        // preference for trade chat
    public final double socialAggression;  // invite spam, shout frequency
    public final boolean usesEmotes;       // uses /sit, /bow, /dance in chat
    public final boolean usesShortForms;   // "u", "lol", "brb", "afk"
    public final boolean usesPunctuation;  // excessive ??? or !!!

    private final Random rng;

    private ChatPersonality(Archetype archetype, double talkativeness,
                            double replyLikelihood, double verbosity,
                            double politeness, double humor,
                            double tradeFocus, double socialAggression,
                            boolean usesEmotes, boolean usesShortForms,
                            boolean usesPunctuation, Random rng) {
        this.archetype = archetype;
        this.talkativeness = talkativeness;
        this.replyLikelihood = replyLikelihood;
        this.verbosity = verbosity;
        this.politeness = politeness;
        this.humor = humor;
        this.tradeFocus = tradeFocus;
        this.socialAggression = socialAggression;
        this.usesEmotes = usesEmotes;
        this.usesShortForms = usesShortForms;
        this.usesPunctuation = usesPunctuation;
        this.rng = rng;
    }

    /**
     * Generate a deterministic personality from account name.
     * Same account always gets same personality (consistent behavior).
     */
    public static ChatPersonality fromAccount(String accountName) {
        int seed = accountName.hashCode();
        Random rng = new Random(seed);

        Archetype[] types = Archetype.values();
        Archetype archetype = types[Math.abs(seed) % types.length];

        switch (archetype) {
            case FRIENDLY:
                return new ChatPersonality(archetype, 0.35, 0.80, 0.60, 0.85, 0.50, 0.20, 0.30,
                        true, true, false, rng);
            case TROLL:
                return new ChatPersonality(archetype, 0.25, 0.70, 0.50, 0.20, 0.80, 0.10, 0.60,
                        false, true, true, rng);
            case QUIET:
                return new ChatPersonality(archetype, 0.08, 0.30, 0.20, 0.60, 0.10, 0.10, 0.10,
                        false, false, false, rng);
            case TRADER:
                return new ChatPersonality(archetype, 0.30, 0.50, 0.50, 0.70, 0.20, 0.95, 0.40,
                        false, true, false, rng);
            case LEADER:
                return new ChatPersonality(archetype, 0.40, 0.75, 0.70, 0.75, 0.30, 0.30, 0.80,
                        true, false, true, rng);
            case NEWBIE:
                return new ChatPersonality(archetype, 0.30, 0.90, 0.70, 0.90, 0.40, 0.40, 0.50,
                        true, true, true, rng);
            case GRINDER:
                return new ChatPersonality(archetype, 0.10, 0.20, 0.30, 0.50, 0.10, 0.30, 0.15,
                        false, false, false, rng);
            case ROLEPLAYER:
                return new ChatPersonality(archetype, 0.20, 0.60, 0.80, 0.80, 0.30, 0.10, 0.25,
                        true, false, false, rng);
            default:
                return new ChatPersonality(Archetype.FRIENDLY, 0.25, 0.60, 0.50, 0.60, 0.40, 0.30, 0.30,
                        false, true, false, rng);
        }
    }

    public boolean rollTalkative() {
        return rng.nextDouble() < talkativeness;
    }

    public boolean rollReply() {
        return rng.nextDouble() < replyLikelihood;
    }

    public boolean rollVerbose() {
        return rng.nextDouble() < verbosity;
    }

    public boolean rollHumor() {
        return rng.nextDouble() < humor;
    }

    public boolean rollTrade() {
        return rng.nextDouble() < tradeFocus;
    }

    public boolean rollSocialAggression() {
        return rng.nextDouble() < socialAggression;
    }

    /** Returns social tendency (0.0-1.0): how likely this personality is to respond socially. */
    public double getSocialTendency() {
        return replyLikelihood;
    }

    /** Returns helpfulness (0.0-1.0): how likely this personality is to help others. */
    public double getHelpfulness() {
        return politeness * 0.7 + talkativeness * 0.3;
    }

    public Random getRng() {
        return rng;
    }

    public String formatMessage(String base) {
        String msg = base;

        if (usesShortForms && rng.nextDouble() < 0.3) {
            msg = applyShortForms(msg);
        }

        if (usesPunctuation && rng.nextDouble() < 0.2) {
            msg = msg + (rng.nextBoolean() ? "!" : "?");
            if (rng.nextDouble() < 0.3) {
                msg = msg + (msg.endsWith("!") ? "!" : "?");
            }
        }

        return msg;
    }

    private String applyShortForms(String msg) {
        return msg.replace("you", "u")
                  .replace("are", "r")
                  .replace("please", "pls")
                  .replace("thanks", "thx")
                  .replace("be right back", "brb")
                  .replace("away from keyboard", "afk");
    }
}