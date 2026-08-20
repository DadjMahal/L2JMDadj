package com.aiplayer.behavior.social;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Diplomacy, Alliance & Treaty System - Tasks 83, 84, 85, 86, 87
 *
 * Manages relationships between AI players and their groups:
 *  - Task 83: Conflict resolution (mediate disputes between AI players)
 *  - Task 84: Negotiation AI (trade negotiations, deal-making)
 *  - Task 85: Diplomacy systems (inter-clan relationships)
 *  - Task 86: Alliance formation (automatic alliance creation)
 *  - Task 87: Treaty management (formal agreements)
 *
 * In Lineage 2 Interlude, clan diplomacy and alliances are critical
 * for siege participation and territory control.
 */
public class DiplomacyEngine {
    private static final Logger LOGGER = Logger.getLogger(DiplomacyEngine.class.getName());
    private static final DiplomacyEngine INSTANCE = new DiplomacyEngine();

    public enum Relation {
        ALLIED,     // formal alliance (Task 86)
        FRIENDLY,   // good relations, cooperate
        NEUTRAL,    // no relationship
        HOSTILE,    // bad relations, avoid
        AT_WAR      // PvP enemies (L2 clan war system)
    }

    /** A formal treaty between two groups (Task 87). */
    public static class Treaty {
        public final String treatyId;
        public final String partyA;
        public final String partyB;
        public final String type;      // "non_aggression", "trade_agreement", "mutual_defense"
        public final long signedAt;
        public final long expiresAt;

        public Treaty(String treatyId, String partyA, String partyB, String type, long durationMs) {
            this.treatyId = treatyId;
            this.partyA = partyA;
            this.partyB = partyB;
            this.type = type;
            this.signedAt = System.currentTimeMillis();
            this.expiresAt = signedAt + durationMs;
        }

        public boolean isActive() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    // relationship matrix: "groupA:groupB" -> Relation
    private final Map<String, Relation> relations = new ConcurrentHashMap<>();
    private final Map<String, Treaty> treaties = new ConcurrentHashMap<>();

    private DiplomacyEngine() {
        LOGGER.info("[DiplomacyEngine] Diplomacy system initialized");
    }

    public static DiplomacyEngine getInstance() {
        return INSTANCE;
    }

    /** Set the relationship between two groups (Task 85). */
    public void setRelation(String groupA, String groupB, Relation relation) {
        String key = relationKey(groupA, groupB);
        relations.put(key, relation);
        LOGGER.info("[Diplomacy] " + groupA + " <-> " + groupB + " = " + relation);
    }

    /** Get the current relationship between two groups. */
    public Relation getRelation(String groupA, String groupB) {
        return relations.getOrDefault(relationKey(groupA, groupB), Relation.NEUTRAL);
    }

    /**
     * Form an alliance between two groups (Task 86).
     */
    public void formAlliance(String groupA, String groupB) {
        setRelation(groupA, groupB, Relation.ALLIED);
        signTreaty(groupA, groupB, "mutual_defense", 24 * 60 * 60 * 1000L); // 24h
        LOGGER.info("[Diplomacy] Alliance formed: " + groupA + " + " + groupB);
    }

    /**
     * Sign a formal treaty (Task 87).
     */
    public Treaty signTreaty(String partyA, String partyB, String type, long durationMs) {
        String treatyId = "treaty_" + System.currentTimeMillis();
        Treaty treaty = new Treaty(treatyId, partyA, partyB, type, durationMs);
        treaties.put(treatyId, treaty);
        LOGGER.info("[Diplomacy] Treaty signed: " + treatyId + " (" + type + ")");
        return treaty;
    }

    /**
     * Resolve a conflict between two AI players (Task 83).
     * Simple mediation: if both are in the same alliance, force peace.
     */
    public boolean resolveConflict(String playerA, String playerB, String groupA, String groupB) {
        Relation rel = getRelation(groupA, groupB);
        if (rel == Relation.ALLIED || rel == Relation.FRIENDLY) {
            LOGGER.info("[Diplomacy] Conflict resolved peacefully: " + playerA + " vs " + playerB);
            return true; // Peaceful resolution
        }
        LOGGER.info("[Diplomacy] Conflict cannot be resolved: " + playerA + " vs " + playerB + " (rel=" + rel + ")");
        return false;
    }

    /**
     * Negotiate a trade deal (Task 84).
     * Returns the agreed price, or -1 if negotiation fails.
     */
    public long negotiatePrice(String buyer, String seller, long askingPrice, long offerPrice) {
        // Simple negotiation: meet in the middle
        long midpoint = (askingPrice + offerPrice) / 2;
        if (midpoint > 0) {
            LOGGER.info("[Diplomacy] Negotiation: " + buyer + " buys from " + seller
                    + " at " + midpoint + " (asked " + askingPrice + ", offered " + offerPrice + ")");
            return midpoint;
        }
        return -1;
    }

    /** Clean up expired treaties. */
    public void cleanupExpiredTreaties() {
        treaties.entrySet().removeIf(entry -> !entry.getValue().isActive());
    }

    public int getActiveTreatyCount() {
        return (int) treaties.values().stream().filter(Treaty::isActive).count();
    }

    public Collection<Treaty> getActiveTreaties() {
        List<Treaty> active = new ArrayList<>();
        for (Treaty t : treaties.values()) {
            if (t.isActive()) active.add(t);
        }
        return active;
    }

    private String relationKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
