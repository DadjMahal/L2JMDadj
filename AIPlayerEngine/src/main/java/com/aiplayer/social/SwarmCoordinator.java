package com.aiplayer.social;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Swarm Intelligence & Team Coordination - Tasks 78, 80, 82
 *
 * Coordinates groups of AI players so they work TOGETHER like a real
 * party or raid group. Includes:
 *
 *  - Task 78: Swarm intelligence (groups coordinate automatically)
 *  - Task 80: Swarm leader selection (best player leads the party)
 *  - Task 82: Team coordination (tactical combat positioning, assist)
 *
 * In Lineage 2, parties need a leader who coordinates targets, healing,
 * and positioning. This module makes AI players do that automatically.
 */
public class SwarmCoordinator {
    private static final Logger LOGGER = Logger.getLogger(SwarmCoordinator.class.getName());
    private static final SwarmCoordinator INSTANCE = new SwarmCoordinator();

    /** A coordinated group of AI players (like a Lineage 2 party). */
    public static class Swarm {
        public final String swarmId;
        public final String leaderId;
        public final List<String> memberIds;
        public final String objective;  // "hunt", "quest", "raid", "pvp", "trade"
        public final long formedAt;

        public Swarm(String swarmId, String leaderId, List<String> memberIds, String objective) {
            this.swarmId = swarmId;
            this.leaderId = leaderId;
            this.memberIds = Collections.unmodifiableList(new ArrayList<>(memberIds));
            this.objective = objective;
            this.formedAt = System.currentTimeMillis();
        }
    }

    private final Map<String, Swarm> activeSwarms = new ConcurrentHashMap<>();

    private SwarmCoordinator() {
        LOGGER.info("[SwarmCoordinator] Swarm intelligence initialized");
    }

    public static SwarmCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Form a new swarm (party) with automatic leader selection.
     * The highest-level player becomes the leader (Task 80).
     */
    public Swarm formSwarm(List<String> candidateIds, Map<String, Integer> playerLevels, String objective) {
        // Select leader: highest level player (Task 80 - swarm leader selection)
        String leaderId = candidateIds.stream()
                .max(Comparator.comparingInt(id -> playerLevels.getOrDefault(id, 1)))
                .orElse(candidateIds.get(0));

        String swarmId = "swarm_" + System.currentTimeMillis();
        Swarm swarm = new Swarm(swarmId, leaderId, candidateIds, objective);
        activeSwarms.put(swarmId, swarm);

        LOGGER.info("[Swarm] Formed " + swarmId + " with leader " + leaderId
                + " (" + candidateIds.size() + " members, objective: " + objective + ")");
        return swarm;
    }

    /** Get the swarm a player belongs to. */
    public Swarm getSwarmForMember(String playerId) {
        for (Swarm swarm : activeSwarms.values()) {
            if (swarm.memberIds.contains(playerId)) {
                return swarm;
            }
        }
        return null;
    }

    /** Disband a swarm when the objective is complete. */
    public void disbandSwarm(String swarmId) {
        Swarm removed = activeSwarms.remove(swarmId);
        if (removed != null) {
            LOGGER.info("[Swarm] Disbanded " + swarmId + " (leader: " + removed.leaderId + ")");
        }
    }

    /**
     * Assign roles within a swarm (Task 82 - team coordination).
     * In L2: tank, healer, DD (damage dealer), buffer, puller.
     */
    public Map<String, String> assignRoles(Swarm swarm, Map<String, String> playerClasses) {
        Map<String, String> roles = new HashMap<>();
        for (String memberId : swarm.memberIds) {
            String clazz = playerClasses.getOrDefault(memberId, "FIGHTER");
            String role = classifyRole(clazz);
            roles.put(memberId, role);
        }
        LOGGER.info("[Swarm] Roles assigned for " + swarm.swarmId + ": " + roles);
        return roles;
    }

    private String classifyRole(String className) {
        String c = className.toUpperCase();
        if (c.contains("TANK") || c.contains("KNIGHT") || c.contains("PALADIN")) return "TANK";
        if (c.contains("HEAL") || c.contains("CLERIC") || c.contains("BISHOP")) return "HEALER";
        if (c.contains("BUFF") || c.contains("PROPHET") || c.contains("SHILLIEN_ELDER")) return "BUFFER";
        if (c.contains("ARCHER") || c.contains("ROGUE") || c.contains("DAGGER")) return "PULLER";
        return "DD"; // Damage Dealer (default)
    }

    public int getActiveSwarmCount() {
        return activeSwarms.size();
    }

    public Collection<Swarm> getActiveSwarms() {
        return Collections.unmodifiableCollection(activeSwarms.values());
    }
}
