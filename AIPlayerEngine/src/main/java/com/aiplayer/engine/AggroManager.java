package com.aiplayer.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import com.aiplayer.protocol.PacketLogger.EntityInfo;

/**
 * Aggro/Emotion Detection System (Task 40)
 * 
 * Tracks enemy threat levels, detects aggro ranges, and handles
 * player reactions based on game mechanics.
 * 
 * Key concepts:
 * - Aggro range: Distance at which monsters begin attacking
 * - Hate/Threat table: Priority for target selection
 * - Emotion system: Player reactions (aggression, fear, etc.)
 */
public class AggroManager {
    private static final Logger LOGGER = Logger.getLogger(AggroManager.class.getName());
    
    // Aggro radius constants (L2J standard values in game units)
    public static final int MELEE_AGRO_RANGE = 400;     // Close combat NPCs
    public static final int RANGED_AGRO_RANGE = 1500;    // Ranged attackers
    public static final int BOSS_AGRO_RANGE = 800;       // Boss monsters
    public static final int PEACE_AGRO_RANGE = 0;        // Peaceful NPCs
    
    // Threat weight constants
    public static final double THREAT_MELEE_ATTACK = 100.0;
    public static final double THREAT_RANGED_ATTACK = 80.0;
    public static final double THREAT_DAMAGE = 0.5;      // Damage dealt
    public static final double THREAT_HEALING = 30.0;    // Healing allies
    public static final double THREAT_TAUNT = 500.0;     // Taunt skills
    
    // Emotion codes (simplified)
    public static final int EMOTION_NONE = 0;
    public static final int EMOTION_AGGRESSIVE = 1;
    public static final int EMOTION_FEARFUL = 2;
    public static final int EMOTION_PEACEFUL = 3;
    public static final int EMOTION_PANIC = 4;
    
    // Track threat levels for each hostile entity
    private ConcurrentHashMap<Integer, Double> threatLevels = new ConcurrentHashMap<>();
    
    // Track threat history with timestamps (threat decay)
    private class ThreatEntry {
        double level;
        long lastUpdate;
        ThreatEntry(double level) {
            this.level = level;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    private ConcurrentHashMap<Integer, ThreatEntry> threatHistory = new ConcurrentHashMap<>();
    
    // Track aggro state for nearby entities
    private ConcurrentHashMap<Integer, Boolean> aggroState = new ConcurrentHashMap<>();
    
    // Player emotions/reactions
    private int currentEmotion = EMOTION_NONE;
    
    // Threat modifiers
    private double damageModifier = 1.0;
    private double critModifier = 1.5;
    private double skillModifier = 1.2;
    
    /**
     * Check if an entity is in aggro range
     * @param entityX Entity X position
     * @param entityY Entity Y position
     * @param entityZ Entity Z position
     * @param playerX Player X position
     * @param playerY Player Y position
     * @param playerZ Player Z position
     * @param aggroRange Maximum aggro distance
     * @return true if entity is in aggro range
     */
    public boolean isInAggroRange(int entityX, int entityY, int entityZ,
                                   int playerX, int playerY, int playerZ,
                                   int aggroRange) {
        double distanceSq = Math.pow(entityX - playerX, 2) +
                           Math.pow(entityY - playerY, 2) +
                           Math.pow(entityZ - playerZ, 2);
        return distanceSq <= (aggroRange * aggroRange);
    }
    
    /**
     * Check if a specific hostile entity would aggro on player
     * @param entity Entity to check
     * @param playerX Player X position
     * @param playerY Player Y position
     * @param playerZ Player Z position
     * @return true if entity would attack
     */
    public boolean wouldAggroOn(EntityInfo entity, int playerX, int playerY, int playerZ) {
        if (!entity.isHostile) {
            return false;
        }
        
        // Determine aggro range based on NPC type
        int aggroRange = getAggroRangeForNPC(entity.npcId);
        
        // Check distance
        boolean inRange = isInAggroRange(entity.x, entity.y, entity.z,
                                         playerX, playerY, playerZ, aggroRange);
        
        // Update aggro state
        aggroState.put(entity.objectId, inRange);
        
        return inRange;
    }
    
    /**
     * Get aggro range for a specific NPC ID
     * @param npcId NPC template ID
     * @return aggro range in game units
     */
    public int getAggroRangeForNPC(int npcId) {
        // Boss monsters (ID range 200000+)
        if (npcId >= 200000) {
            return BOSS_AGRO_RANGE;
        }
        // Beasts (ID range 210000-210999)
        if (npcId >= 210000 && npcId < 211000) {
            return MELEE_AGRO_RANGE;
        }
        // Event monsters
        if (npcId >= 800000) {
            return MELEE_AGRO_RANGE;
        }
        // Standard monsters
        if (npcId >= 1 && npcId < 200000) {
            return MELEE_AGRO_RANGE;
        }
        // Peaceful NPCs (shops, etc.)
        return PEACE_AGRO_RANGE;
    }
    
    /**
     * Add threat to a target
     * @param entityId Target entity ID
     * @param amount Threat amount
     */
    public void addThreat(int entityId, double amount) {
        double currentThreat = threatLevels.getOrDefault(entityId, 0.0);
        threatLevels.put(entityId, currentThreat + amount);
        
        LOGGER.fine("[Aggro] Added " + amount + " threat to entity " + entityId + 
                   " (total: " + (currentThreat + amount) + ")");
    }
    
    /**
     * Get threat level for an entity
     * @param entityId Entity ID
     * @return Threat level (0 if not tracked)
     */
    public double getThreatLevel(int entityId) {
        return threatLevels.getOrDefault(entityId, 0.0);
    }
    
    /**
     * Check if entity has enough threat to be targeted
     * @param entityId Entity ID
     * @param threshold Minimum threat threshold
     * @return true if threat exceeds threshold
     */
    public boolean hasSufficientThreat(int entityId, double threshold) {
        return getThreatLevel(entityId) >= threshold;
    }
    
    /**
     * Calculate threat from damage dealt
     * @param damage Amount of damage dealt
     * @return Threat value
     */
    public double calculateDamageThreat(int damage) {
        return damage * THREAT_DAMAGE;
    }
    
    /**
     * Get emotion state of the player
     * @return Current emotion code
     */
    public int getCurrentEmotion() {
        return currentEmotion;
    }
    
    /**
     * Set player emotion state
     * @param emotion Emotion code (EMOTION_*)
     */
    public void setEmotion(int emotion) {
        this.currentEmotion = emotion;
        LOGGER.info("[Aggro] Emotion changed to: " + emotionToString(emotion));
    }
    
    /**
     * React to taking damage from an entity
     * @param attackerId Attacker entity ID
     * @param damage Amount of damage taken
     */
    public void onTakeDamage(int attackerId, int damage) {
        // Aggressive response by default when taking damage
        setEmotion(EMOTION_AGGRESSIVE);
        
        // Build threat on attacker
        double threat = damage * 2.0; // Higher threat factor when damaged
        addThreat(attackerId, threat);
    }
    
    /**
     * React to dealing damage
     * @param targetId Target entity ID
     * @param damage Amount of damage dealt
     */
    public void onDealDamage(int targetId, int damage) {
        double threat = calculateDamageThreat(damage);
        addThreat(targetId, threat);
    }
    
    /**
     * React to using a taunt skill
     * @param targetId Target entity ID
     */
    public void onTaunt(int targetId) {
        addThreat(targetId, THREAT_TAUNT);
    }
    
    /**
     * React to healing
     * @param targetId Target entity ID (0 for self)
     * @param amount Amount healed
     */
    public void onHeal(int targetId, int amount) {
        addThreat(targetId, THREAT_HEALING);
    }
    
    /**
     * Check if entity is afraid of player
     * @param entity Entity to check
     * @return true if entity shows fear behavior
     */
    public boolean isEntityAfraid(EntityInfo entity) {
        if (!entity.isHostile) {
            return currentEmotion == EMOTION_PEACEFUL;
        }
        
        // High threat player might cause fear
        double threat = getThreatLevel(entity.objectId);
        return threat > 1000; // Fear threshold
    }
    
    /**
     * Get the most aggroed target based on threat levels
     * @return Entity ID with highest threat, or -1 if none
     */
    public int getHighestThreatTarget() {
        return threatLevels.entrySet().stream()
            .max(Comparator.comparingDouble(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(-1);
    }
    
    /**
     * Check if entity is tracking player (recent threat)
     * @param entityId Entity ID
     * @param timeWindow Seconds to consider for recent threat
     * @return true if entity has recent threat on player
     */
    public boolean isTracking(int entityId, int timeWindow) {
        double threat = getThreatLevel(entityId);
        // Simplified - in production would track timestamps
        return threat > 50;
    }
    
    /**
     * Simulate emotion-based reactions
     * Determines how NPCs react to player based on emotion state
     */
    public Map<Integer, String> getEntityReactions(List<EntityInfo> entities) {
        Map<Integer, String> reactions = new HashMap<>();
        
        for (EntityInfo entity : entities) {
            if (!entity.isHostile) {
                if (currentEmotion == EMOTION_PEACEFUL || currentEmotion == EMOTION_NONE) {
                    reactions.put(entity.objectId, "IGNORE");
                } else if (currentEmotion == EMOTION_FEARFUL) {
                    reactions.put(entity.objectId, "FLEE");
                }
            } else {
                if (isEntityAfraid(entity)) {
                    reactions.put(entity.objectId, "FLEE");
                } else if (isInAggroRange(entity.x, entity.y, entity.z, 
                                          0, 0, 0, MELEE_AGRO_RANGE)) { // Placeholder player pos
                    reactions.put(entity.objectId, "ATTACK");
                } else {
                    reactions.put(entity.objectId, "WATCH");
                }
            }
        }
        
        return reactions;
    }
    
    /**
     * Clear all threat data (for reset/zone change)
     */
    public void clearThreats() {
        threatLevels.clear();
        aggroState.clear();
        currentEmotion = EMOTION_NONE;
    }
    
    /**
     * Reset aggro state for specific entity
     */
    public void resetAggro(int entityId) {
        threatLevels.remove(entityId);
        aggroState.remove(entityId);
    }
    
    /**
     * Convert emotion code to string
     */
    private String emotionToString(int emotion) {
        switch (emotion) {
            case EMOTION_AGGRESSIVE: return "AGGRESSIVE";
            case EMOTION_FEARFUL: return "FEARFUL";
            case EMOTION_PEACEFUL: return "PEACEFUL";
            case EMOTION_PANIC: return "PANIC";
            default: return "NONE";
        }
    }
    
    /**
     * Get aggro status for an entity
     */
    public boolean isEntityAggroed(int entityId) {
        return aggroState.getOrDefault(entityId, false);
    }
}
