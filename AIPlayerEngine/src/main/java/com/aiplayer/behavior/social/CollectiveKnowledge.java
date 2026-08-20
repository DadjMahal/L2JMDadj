package com.aiplayer.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Collective Knowledge Base - Tasks 77, 79, 81
 *
 * Shared knowledge database that ALL AI players can contribute to and
 * learn from. This is the "collective intelligence" of the AI swarm.
 *
 * When one AI player discovers a good hunting spot, profitable trade
 * route, or effective combat tactic, it shares that knowledge so all
 * other AI players benefit - just like a real guild/clan sharing tips.
 *
 * Combines:
 *  - Task 77: Genetic algorithms (best strategies evolve and survive)
 *  - Task 79: Collective AI (shared knowledge pool)
 *  - Task 81: Knowledge sharing (AI-to-AI information exchange)
 */
public class CollectiveKnowledge {
    private static final Logger LOGGER = Logger.getLogger(CollectiveKnowledge.class.getName());
    private static final CollectiveKnowledge INSTANCE = new CollectiveKnowledge();

    /** A piece of shared knowledge contributed by an AI player. */
    public static class Knowledge {
        public final String category;    // "hunting_spot", "trade_route", "quest_tip", "combat_tactic"
        public final String key;         // unique identifier
        public final String value;       // the actual knowledge (e.g. "Gludio_fields_lvl5-10")
        public final double rating;      // community rating (higher = better)
        public final String contributor; // which AI player shared this
        public final long timestamp;

        public Knowledge(String category, String key, String value, double rating, String contributor) {
            this.category = category;
            this.key = key;
            this.value = value;
            this.rating = rating;
            this.contributor = contributor;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<String, List<Knowledge>> knowledgeBase = new ConcurrentHashMap<>();

    private CollectiveKnowledge() {
        LOGGER.info("[CollectiveKnowledge] Shared knowledge base initialized");
    }

    public static CollectiveKnowledge getInstance() {
        return INSTANCE;
    }

    /** An AI player shares knowledge with the collective. */
    public void share(String contributor, String category, String key, String value, double rating) {
        Knowledge k = new Knowledge(category, key, value, rating, contributor);
        knowledgeBase.computeIfAbsent(category, c -> Collections.synchronizedList(new ArrayList<>())).add(k);
        LOGGER.fine("[Collective] " + contributor + " shared " + category + ":" + key + " (rating=" + rating + ")");
    }

    /** Query the best knowledge in a category (genetic survival: highest rated wins). */
    public Knowledge bestInCategory(String category) {
        List<Knowledge> list = knowledgeBase.get(category);
        if (list == null || list.isEmpty()) return null;
        return list.stream().max(Comparator.comparingDouble(k -> k.rating)).orElse(null);
    }

    /** Query top-N knowledge in a category. */
    public List<Knowledge> topInCategory(String category, int n) {
        List<Knowledge> list = knowledgeBase.get(category);
        if (list == null) return Collections.emptyList();
        return list.stream()
                .sorted((a, b) -> Double.compare(b.rating, a.rating))
                .limit(n)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /** Upvote knowledge (genetic algorithm: good knowledge gets higher rating). */
    public void upvote(String category, String key) {
        List<Knowledge> list = knowledgeBase.get(category);
        if (list != null) {
            for (Knowledge k : list) {
                if (k.key.equals(key)) {
                    list.remove(k);
                    list.add(new Knowledge(k.category, k.key, k.value, k.rating + 0.1, k.contributor));
                    break;
                }
            }
        }
    }

    /** Total knowledge entries across all categories. */
    public int totalKnowledge() {
        return knowledgeBase.values().stream().mapToInt(List::size).sum();
    }

    public Set<String> getCategories() {
        return knowledgeBase.keySet();
    }
}
