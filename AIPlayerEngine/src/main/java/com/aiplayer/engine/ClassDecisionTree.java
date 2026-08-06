package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class ClassDecisionTree {
    private static final Logger LOGGER = Logger.getLogger(ClassDecisionTree.class.getName());

    public enum Subclass { WARRIOR, KNIGHT, ROGUE, ARCHER, CLERIC, WIZARD, SUMMONER, ARTISAN, BARD }

    public static class DecisionNode {
        public final String question;
        public final Map<Boolean, DecisionNode> branches = new HashMap<>();
        public final Subclass result;

        public DecisionNode(String q, Subclass result) {
            question = q; this.result = result;
        }

        public DecisionNode branch(boolean yes, DecisionNode node) {
            branches.put(yes, node); return this;
        }

        public Subclass decide() {
            if (result != null) return result;
            DecisionNode next = branches.get(true);
            return next != null ? next.decide() : Subclass.WARRIOR;
        }
    }

    public static DecisionNode buildDefaultTree() {
        return new DecisionNode("PvP?", Subclass.KNIGHT)
            .branch(true, new DecisionNode("Group?", Subclass.ARCHER))
            .branch(false, new DecisionNode("Support?", Subclass.CLERIC));
    }
}
