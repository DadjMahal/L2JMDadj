package com.aiplayer.engine;

import java.util.logging.Logger;

import com.aiplayer.protocol.PacketLogger;

/**
 * Task 63: PvP Combat Enhancements
 * 
 * Combat AI Module - Handles combat decisions, skill usage, and target selection for AI players
 * Enhanced with PvP-specific behaviors: buff awareness, stance detection, karma handling, and skill rotation.
 * 
 * Telemetry: PacketLogger tracks CharInfo/StatusUpdate/NPC_INFO packets for decision making
 */
public class CombatAI {
    private static final Logger LOGGER = Logger.getLogger(CombatAI.class.getName());
    
    private final AIPlayer aiPlayer;
    private final CombatConfig config;
    private final PacketLogger packetLogger; // For PvP entity tracking
    private CombatState combatState;
    private String currentTarget;
    private long lastSkillUseTime;
    private int comboCount;
    
    public CombatAI(AIPlayer aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.config = CombatConfig.getInstance();
        this.packetLogger = new PacketLogger(aiPlayer.getName());
        this.combatState = new CombatState();
    }
    
    /**
     * Main combat decision method
     * Decides what action to take in combat
     */
    public CombatDecision makeDecision() {
        if (!config.isEnabled()) {
            return CombatDecision.idle();
        }
        
        try {
            // Check if we're in combat
            if (combatState.isInCombat()) {
                return manageActiveCombat();
            }
            
            // Check for enemies nearby
            String enemy = detectNearbyEnemy();
            if (enemy != null) {
                return engageEnemy(enemy);
            }
            
            // AutoPlay mode (if player chose this)
            if (config.isAutoPlayEnabled()) {
                return CombatDecision.autoPlay();
            }
            
            // Passive - just monitor surroundings
            return CombatDecision.idle();
            
        } catch (Exception e) {
            LOGGER.warning("Combat AI error for " + aiPlayer.getName() + ": " + e.getMessage());
            return CombatDecision.idle();
        }
    }
    
    private CombatDecision manageActiveCombat() {
        // Check if target is dead or out of range
        if (isTargetDead() || isTargetOutOfRange()) {
            return handleCombatEnd();
        }
        
        // Check if we need healing
        if (shouldHeal()) {
            return heal();
        }
        
        // Check if we should use offensive skill
        if (shouldUseSkill()) {
            return useOffensiveSkill();
        }
        
        // Basic auto-attack
        return CombatDecision.attack();
    }
    
    private boolean isTargetDead() {
        // TODO: REQUIRES PROTOCOL IMPLEMENTATION - Prompt 1
        // Currently always returns false because no packet is parsed
        // Need: StatusUpdate packet from server for target health
        //   OR: Death packet (opcode 0x0F from clientpackets)
        // Once protocol implemented:
        //   return aiPlayer.getProtocol().getTargetHealth(currentTarget) <= 0;
        return false; // NOT YET TESTED - Placeholder
    }
    
    private boolean isTargetOutOfRange() {
        double distance = calculateDistanceTo(currentTarget);
        return distance > config.getTargetDistance();
    }
    
    /**
     * Real 3D distance from the player to a target, computed from PacketLogger entity positions
     * (Stream C: replaced the mock {@code 100 + Math.random()*50}). Returns MAX_VALUE when the
     * target id is missing or the entity is no longer tracked (e.g. despawned), so it reads as
     * "out of range".
     */
    public double calculateDistanceTo(String targetId) {
        int objId = parseTargetObjId(targetId);
        if (objId < 0)
        {
            return Double.MAX_VALUE;
        }
        PacketLogger.EntityInfo entity = packetLogger.getEntity(objId);
        if (entity == null)
        {
            return Double.MAX_VALUE;
        }
        double dx = entity.x - aiPlayer.getX();
        double dy = entity.y - aiPlayer.getY();
        double dz = entity.z - aiPlayer.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Extract the objId from a target id like "objId=12345"; -1 if not parseable. */
    private int parseTargetObjId(String targetId) {
        if (targetId == null)
        {
            return -1;
        }
        String prefix = "objId=";
        int idx = targetId.indexOf(prefix);
        if (idx < 0)
        {
            return -1;
        }
        try
        {
            return Integer.parseInt(targetId.substring(idx + prefix.length()));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /**
     * The objId of the currently selected combat target (from {@code currentTarget}),
     * or -1 if none selected. Used by the executor to send the real Action/AttackRequest frames.
     */
    public int getSelectedTargetObjId() {
        return parseTargetObjId(currentTarget);
    }

    /** Expose the packet logger (telemetry + tests can feed real parsed packets). */
    public PacketLogger getPacketLogger() {
        return packetLogger;
    }
    
    private CombatDecision handleCombatEnd() {
        return CombatDecision.leaveCombat();
    }
    
    private boolean shouldHeal() {
        int hpPercent = getCurrentHPPercentage();
        return hpPercent < config.getHealthThreshold();
    }
    
    private int getCurrentHPPercentage() {
        // Use PacketLogger for real HP tracking from StatusUpdate packet
        // StatusUpdate packet (opcode 0x0E) contains HP values
        return (int) packetLogger.getHpPercentage();
    }
    
    private boolean shouldUseSkill() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSkillUseTime < config.getCooldown()) {
            return false;
        }
        return getCurrentMPPercentage() > 20;
    }
    
    private int getCurrentMPPercentage() {
        // Use PacketLogger for real MP tracking from StatusUpdate packet
        // StatusUpdate packet (opcode 0x0E) contains MP values
        return (int) packetLogger.getMpPercentage();
    }
    
    private String detectNearbyEnemy() {
        // Get player position for enemy detection
        int playerX = aiPlayer.getX();
        int playerY = aiPlayer.getY();
        int playerZ = aiPlayer.getZ();
        
        // Use PacketLogger to find nearest hostile entity (NPC or player in PvP)
        // This requires NPC_INFO packets to have been parsed
        PacketLogger.EntityInfo nearestHostile = packetLogger.findNearestHostile(
            playerX, playerY, playerZ, config.getTargetDistance());
        
        if (nearestHostile != null) {
            // Return the object ID as target identifier
            return "objId=" + nearestHostile.objectId;
        }
        
        // Check for PvP targets (players) if in PvP zone
        if (aiPlayer.isInPvPZone() && aiPlayer.isPvPEnabled()) {
            // Would check PvP player targets here when protocol supports it
            // For now, fall back to hostile NPCs
        }
        
        return null;
    }
    
    private CombatDecision engageEnemy(String enemyId) {
        currentTarget = enemyId;
        combatState.setInCombat(true);
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] ATTACK_START: target=" + enemyId);
        return CombatDecision.attack();
    }
    
    private void onAttackLanded(int damage) {
        combatState.addDamage(damage);
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] DAMAGE_DEALT: amount=" + damage + " total=" + combatState.getDamageDealt());
    }
    
    public void onKill(String targetName) {
        combatState.incrementKillCount();
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] KILL: target=" + targetName + " total_kills=" + combatState.getKillCount());
    }
    
    public void onDeath() {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] DEATH");
    }
    
    public void onRespawn(int level) {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] RESPAWN: level=" + level);
    }
    
    public void onLevelUp(int newLevel) {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] LEVEL_UP: new_level=" + newLevel);
    }
    
    public void onItemDrop(String itemId) {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] ITEM_DROP: item=" + itemId);
    }
    
    private boolean shouldUseOffensiveSkill() {
        // Skill selection logic using priority system
        String skillPriority = config.getSkillPriority();
        
        // Parse priority config: "ATTACK:1,HEAL:2,POWER_STRIKE:3"
        // Select highest priority skill we can afford
        
        int mpPercent = getCurrentMPPercentage();
        if (mpPercent < config.getManaThreshold()) {
            return false;  // Not enough MP
        }
        
        // Check preferred skill first
        if (config.getPreferredSkill() != null && 
            mpPercent > config.getSkillMpCost()) {
            return true;
        }
        
        return false;
    }
    
    private CombatDecision useOffensiveSkill() {
        // Select best skill based on skill priority
        String skill = selectBestSkill();
        LOGGER.info(aiPlayer.getName() + " using skill: " + skill);
        lastSkillUseTime = System.currentTimeMillis();
        combatState.incrementCombo();
        return CombatDecision.useSkill(skill, null, "Skill rotation priority");
    }
    
    /**
     * Select the best skill based on priority and MP cost
     */
    private String selectBestSkill() {
        // Simple implementation - use preferred skill
        // Future enhancement: parse skill_priority config and select based on MP/health
        String preferred = config.getPreferredSkill();
        return preferred != null ? preferred : "ATTACK";
    }
    
    private boolean shouldDefendDecision() {
        // Deterministic, real-data threat model (Stream C: removed Math.random()).
        int currentHp = getCurrentHPPercentage();           // real HP from StatusUpdate
        int hostilesNearby = packetLogger.getHostileEntityCount(); // real NPC_INFO hostile count

        // High threat when HP is low or multiple enemies
        double threatLevel = (100 - currentHp) / 100.0;
        if (hostilesNearby > 1)
        {
            threatLevel += (hostilesNearby - 1) * 0.2;
        }

        return threatLevel >= 0.3;
    }

    /** Public deterministic threat check (Stream C: real HP + hostile count, no randomness). */
    public boolean shouldDefend() {
        return shouldDefendDecision();
    }
    
    private boolean isHighThreatTarget() {
        // Check if this target poses high threat
        String target = combatState.getTarget();
        if (target == null) return false;
        
        // Could check target HP, level difference, etc.
        // For now, check if we're in a dangerous situation
        return getCurrentHPPercentage() < 50 || 
               combatState.getHostileEntitiesNearby() > 2;
    }
    
    private CombatDecision defensiveAction() {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] DEFEND_ACTION: threat level high");
        return CombatDecision.defend();
    }
    
    private boolean shouldBlock() {
        // Block when we predict incoming damage
        return getCurrentHPPercentage() < config.getDefensiveThreshold();
    }
    
    private CombatDecision blockAction() {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] BLOCK_ACTION");
        return CombatDecision.block();
    }
    
    private CombatDecision heal() {
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] HEAL: skill=" + config.getHealSkill());
        return CombatDecision.heal();
    }
    
    public void onCombatStart() {
        combatState.setInCombat(true);
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] COMBAT_START: target=" + currentTarget);
    }
    
    public void onCombatEnd() {
        combatState.setInCombat(false);
        float damageDealt = comboCount > 0 ? comboCount * 100 : 0;
        LOGGER.info("[COMBAT-LOG] [" + aiPlayer.getName() + "] COMBAT_END: damage_dealt=" + damageDealt + " kills=" + combatState.getKillCount() + " deaths=0");
        currentTarget = null;
        comboCount = 0;
    }
    
    /** Log combat telemetry event */
    private void logCombatTelemetry(String event, Object... args) {
        try {
            StringBuilder msg = new StringBuilder("[COMBAT-LOG] [" + aiPlayer.getName() + "] " + event);
            for (Object arg : args) {
                msg.append(" ").append(arg);
            }
            LOGGER.info(msg.toString());
        } catch (Exception e) {
            LOGGER.warning("Combat telemetry log failed: " + e.getMessage());
        }
    }
    
    public CombatState getCombatState() {
        return combatState;
    }
    
    // ============================================
    // PvP-SPECIFIC ENHANCEMENTS (Task 100)
    // ============================================
    
    /** Check if PvP is enabled in configuration */
    public boolean isPvPEnabled() {
        return config.isPvPenabled();
    }
    
    /** Check if target is a player (for PvP decisions) */
    public boolean isHostilePlayer() {
        // Check if current target is a player character (npcId 0 = player)
        PacketLogger.EntityInfo entity = packetLogger.getEntity(
            currentTarget != null && currentTarget.contains("objId=") ? 
            Integer.parseInt(currentTarget.split("objId=")[1]) : -1);
        return entity != null && entity.isHostile && entity.npcId == 0;
    }
    
    /** Check if we're in a PvP context (game has PvP enabled in area) */
    public boolean isInPvPContext() {
        return aiPlayer.isInPvPZone();
    }
    
    /** Check if current position is in safe zone */
    public boolean isInSafeZone() {
        int x = aiPlayer.getX();
        int y = aiPlayer.getY();
        // Safe zones: towns, capital cities, etc.
        // Gludio town area (safe from PK)
        if (x >= 16300 && x <= 16700 && y >= 16900 && y <= 17300) {
            return true;
        }
        // Dion town area
        if (x >= 12000 && x <= 15000 && y >= 12000 && y <= 15000) {
            return true;
        }
        return false;
    }
    
    /** Check if target is in a safe zone (cannot be attacked) */
    public boolean isTargetInSafeZone() {
        // Would need target position parsing from protocol
        return isInSafeZone();
    }
    
    /** Make karma-based PK decision */
    public String getPvPKarmaDecision(int attackerKarma, int targetKarma) {
        if (isInSafeZone()) {
            return "FLEE: In safe zone";
        }
        if (isTargetInSafeZone()) {
            return "FLEE: Target in safe zone";
        }
        // Good players (karma < 0) can PK evil players (karma < -16000)
        if (attackerKarma >= 0 && targetKarma >= -16000) {
            return "FLAG_ONLY: Need to PK flag first";
        }
        if (attackerKarma < 0 && targetKarma < -16000) {
            return "ATTACK: Legal PK on evil player";
        }
        return "OBSERVE: Cannot PK this target";
    }
    
    /** Get optimal skill for PvP situation */
    public String getOptimalPvPSkill(int mpPercentage) {
        // Priority: Burst > Control > Basic attack
        if (mpPercentage > 60) {
            return "POWER_STRIKE:97"; // Burst damage
        } else if (mpPercentage > 30) {
            return "POISON_STRIKE:120"; // Damage over time
        }
        return "ATTACK: Basic attack";
    }
    
    /** Check if we should use defensive buff */
    public boolean shouldUseDefensiveBuff() {
        // Use defensive buff when: low buffs, high HP, out of combat
        return !combatState.isInCombat() && getCurrentHPPercentage() > 50;
    }
    
    /** Get defensive skill */
    public String getDefensiveSkill() {
        return "BARRIER:101"; // Defense boost
    }
    
    /** Enhanced combat decision with PvP awareness */
    public CombatDecision makePvPDuidedDecision() {
        // Skip if not PvP enabled
        if (!isPvPEnabled()) {
            return makeDecision();
        }
        
        // Check safe zones
        if (isInSafeZone()) {
            LOGGER.info("[" + aiPlayer.getName() + "] In safe zone - cannot PK");
            return CombatDecision.idle();
        }
        
        // If in combat, use normal combat logic
        if (combatState.isInCombat()) {
            return manageActiveCombat();
        }
        
        // Check for PvP targets
        String enemy = detectNearbyEnemy();
        if (enemy != null) {
            return engageEnemy(enemy);
        }
        
        return CombatDecision.idle();
    }
}