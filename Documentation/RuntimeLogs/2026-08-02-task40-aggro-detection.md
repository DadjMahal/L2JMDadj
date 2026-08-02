# Task 40: Add Aggro/Emotion Detection ✅

**Date:** 2026-08-02  
**Status:** COMPLETED

## Objective
Implement aggro/emotion detection system for tracking hostility and player reactions.

## Implementation

### Files Created
- `AIPlayerEngine/src/main/java/com/aiplayer/engine/AggroManager.java`

### AggroManager Class Overview

```java
package com.aiplayer.engine;

public class AggroManager {
    // Track threat levels for each hostile entity
    private ConcurrentHashMap<Integer, Double> threatLevels = new ConcurrentHashMap<>();
    
    // Track aggro state for nearby entities
    private ConcurrentHashMap<Integer, Boolean> aggroState = new ConcurrentHashMap<>();
    
    // Player emotions/reactions
    private int currentEmotion = EMOTION_NONE;
}
```

### Key Concepts

#### Aggro Ranges
| NPC Type | Aggro Range | Threat Behavior |
|----------|-------------|-----------------|
| Melee NPCs | 400 units | Close combat |
| Ranged NPCs | 1500 units | Long-range |
| Boss Monsters | 800 units | Coordinated |
| Peaceful NPCs | 0 units | Never attack |

#### Emotion States
| Code | Name | Behavior |
|------|------|----------|
| 0 | NONE | Neutral |
| 1 | AGGRESSIVE | Attacks on sight |
| 2 | FEARFUL | Flees from player |
| 3 | PEACEFUL | Ignores player |
| 4 | PANIC | Random fleeing |

### Threat Calculation

| Action | Threat Value |
|--------|-------------|
| Melee Attack | 100.0 |
| Ranged Attack | 80.0 |
| Damage Dealt (per HP) | 0.5 |
| Healing | 30.0 |
| Taunt Skill | 500.0 |

## Key Methods

### Aggro Detection
```java
// Check if entity is in aggro range
public boolean isInAggroRange(double entityX, double entityY, double entityZ,
                               double playerX, double playerY, double playerZ,
                               int aggroRange)

// Determine if entity would attack
public boolean wouldAggroOn(EntityInfo entity, int playerX, int playerY, int playerZ)

// Get appropriate aggro range for NPC type
public int getAggroRangeForNPC(int npcId)
```

### Threat Management
```java
// Add threat to a target
public void addThreat(int entityId, double amount)

// Get current threat level
public double getThreatLevel(int entityId)

// Check if threat threshold met
public boolean hasSufficientThreat(int entityId, double threshold)
```

### Damage-Based Threat
```java
// Calculate threat from damage dealt
public double calculateDamageThreat(int damage)

// React to taking damage
public void onTakeDamage(int attackerId, int damage)

// React to dealing damage
public void onDealDamage(int targetId, int damage)

// React to using taunt
public void onTaunt(int targetId)

// React to healing
public void onHeal(int targetId, int amount)
```

### Emotion System
```java
// Get current emotion
public int getCurrentEmotion()

// Set emotion state
public void setEmotion(int emotion)

// Check if entity is afraid
public boolean isEntityAfraid(EntityInfo entity)

// Get entity reactions to player emotion
public Map<Integer, String> getEntityReactions(List<EntityInfo> entities)
```

### Target Selection
```java
// Get highest threat target
public int getHighestThreatTarget()

// Check if entity is tracking player
public boolean isTracking(int entityId, int timeWindow)

// Check if specific entity is aggroed
public boolean isEntityAggroed(int entityId)

// Reset threat for entity
public void resetAggro(int entityId)

// Clear all threat data
public void clearThreats()
```

## Integration Example

### CombatAI Integration
```java
public class CombatAI {
    private AggroManager aggroManager = new AggroManager();
    
    public List<EntityInfo> getAggroTargets() {
        return packetLogger.getEntities().stream()
            .filter(e -> aggroManager.wouldAggroOn(e, playerX, playerY, playerZ))
            .collect(Collectors.toList());
    }
    
    public EntityInfo selectPrimaryTarget() {
        // Use threat table for targeting
        int targetId = aggroManager.getHighestThreatTarget();
        if (targetId > 0) {
            return packetLogger.getEntity(targetId);
        }
        return null;
    }
}
```

### Damage Reaction
```java
// When player takes damage
aggroManager.onTakeDamage(attackerId, damage);

// Check if we have sufficient threat
if (aggroManager.hasSufficientThreat(attackerId, 100)) {
    // Entity will notice and potentially attack
}

// Get highest threat target
int primaryTarget = aggroManager.getHighestThreatTarget();
```

### Emotion-Based Reactions
```java
// Check fear level before attacking peaceful NPCs
if (aggroManager.isEntityAfraid(entity)) {
    // Entity might flee or become passive
}

// React to player actions
if (currentEmotion == EMOTION_AGGRESSIVE) {
    // Attack all nearby hostiles
} else if (currentEmotion == EMOTION_PEACEFUL) {
    // Avoid conflict
}
```

## Build Status
```
BUILD SUCCESS ✅
Tests: 11/11 passing ✅
```

## L2J Game Mechanics Reference

### NPC ID Ranges
```
0-9999:    Guards, Merchants, Quest NPCs
10000-199999: Standard Monsters
200000-209999: Beasts
210000-210999: Beasts (variant)
800000+:   Event Monsters
```

### Typical Aggro Ranges
- Guards: 0 (never aggro)
- Town NPCs: 0
- Monsters: 400-800
- Beasts: 500-1000
- Bosses: 800-1200

## Integration Points

### CombatAI
- Target selection based on threat level
- Emotion-aware decision making

### AIBrain
- Emotion state tracking
- Aggro-aware routing

### MerchantAI
- Check if area is too dangerous
- Emotion affects trade willingness

## Production Enhancements

- [ ] Load aggro ranges from L2J database
- [ ] Add emote-based reactions (player emotions)
- [ ] Implement threat decay over time
- [ ] Add reputation system effects
- [ ] Integrate with actual skill aggro modifiers
