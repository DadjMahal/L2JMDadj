package com.aiplayer.behavior;

import com.aiplayer.learning.PersonalityProfile;
import com.aiplayer.learning.PersonalityProfile.Personality;

/**
 * MODE: COMPLETE. EB-04 — the bridge from a bot's {@link PersonalityProfile} (which was
 * decorative: nothing read its weights) to concrete behavior knobs the decision loop actually
 * uses:
 *
 * <ul>
 *   <li>{@link Knobs#surviveHpFraction} — RISK: how low HP a bot tolerates before it stops
 *       fighting. Riskier personalities fight longer (lower fraction).</li>
 *   <li>{@link Knobs#sightRangeScale} / {@link Knobs#combatRangeScale} — PACE: how eagerly a bot
 *       seeks out engagement (larger = advances/hunts more aggressively).</li>
 *   <li>{@link Knobs#restockThreshold} — how soon a bot breaks off to restock its inventory
 *       (merchants/hoarders restock earlier).</li>
 *   <li>{@link Knobs#talkativeness} — how chatty the bot is (0..1; drives the chat-emission
 *       interval).</li>
 * </ul>
 *
 * <p>Pure mapping: no IO, no packets, no threads. {@code BotSession} feeds the knobs into
 * {@link BotPlayController.BotPlayConfig} so they actually change the per-bot decisions; the
 * chat path can use {@link #knobs(Personality).talkativeness} to throttle chatter. The legacy
 * six weights on PersonalityProfile stay untouched (still exposed), but the LIVE behavior now
 * comes from these knobs.
 */
public final class PersonalityBehavior
{
    private PersonalityBehavior()
    {
    }

    /** Base knob set = the historical/neutral fleet behaviour (a Human-level grinder). */
    public static final class Knobs
    {
        /** Fraction of HP at/below which the bot retreats (lower = riskier = fights longer). */
        public final double surviveHpFraction;
        /** Multiplier on the base sight range (how far it will walk to hunt). */
        public final double sightRangeScale;
        /** Multiplier on the base combat range (how close an enemy must be to attack). */
        public final double combatRangeScale;
        /** Inventory % at which the bot walks to the vendor to restock (0..100). */
        public final int restockThreshold;
        /** 0..1 how chatty the bot is (higher = talks more often). */
        public final double talkativeness;

        public Knobs(double surviveHpFraction, double sightRangeScale, double combatRangeScale,
                     int restockThreshold, double talkativeness)
        {
            this.surviveHpFraction = surviveHpFraction;
            this.sightRangeScale = sightRangeScale;
            this.combatRangeScale = combatRangeScale;
            this.restockThreshold = restockThreshold;
            this.talkativeness = talkativeness;
        }
    }

    /** The neutral / default personality knobs (Human, balanced). */
    public static final Knobs NEUTRAL = new Knobs(0.25, 1.0, 1.0, 100, 0.5);

    /** EB-04: the one mapping — Personality → concrete behavior knobs. */
    public static Knobs knobs(Personality p)
    {
        if (p == null)
        {
            return NEUTRAL;
        }
        switch (p)
        {
            case AGGRESSIVE:
                return new Knobs(0.15, 1.25, 1.15, 100, 0.4);  // fights long, hunts wide, rarely restocks
            case CAUTIOUS:
                return new Knobs(0.40, 0.80, 0.85, 80, 0.4);   // retreats early, engages close
            case SOCIAL:
                return new Knobs(0.30, 1.0, 1.0, 90, 1.0);     // chatterbox
            case MERCHANT:
                return new Knobs(0.30, 0.95, 0.95, 30, 0.6);   // restocks very early
            case EXPLORER:
                return new Knobs(0.30, 1.40, 1.20, 95, 0.7);   // wide sight -> hunts across zones
            case COMPLETIONIST:
                return new Knobs(0.28, 1.0, 1.0, 60, 0.6);     // restocks modest, steady
            default:
                return NEUTRAL;
        }
    }
}