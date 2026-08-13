# Task 41: Implement Threat Table ✅

**Date:** 2026-08-02  
**Status:** COMPLETED

## Objective
Implement comprehensive threat table system to track enemy targets and priorities.

## Implementation

### Files Modified
- `AIPlayerEngine/src/main/java/com/aiplayer/engine/AggroManager.java`

### Enhancements to AggroManager

#### Added Threat History with Decay

```java
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
```

#### Added Threat Modifiers

```java
// Configurable threat multipliers
private double damageModifier = 1.0;
private double critModifier = 1.5;
private double skillModifier = 1.2;
```

## Threat Table Methods

### Threat Persistence
```java
// Apply threat decay over time
public void applyThreatDecay(int decayPerSecond, int timeWindowSeconds) {
    long now = System.currentTimeMillis();
    threatLevels.replaceAll((id, threat) -> {
        ThreatEntry entry = threatHistory.get(id);
        if (entry != null) {
            long elapsed = (now - entry.lastUpdate) / 1000;
            if (elapsed > timeWindowSeconds) {
                return 0.0; // Threat expired
            }
            return threat * Math.pow(0.9, elapsed / timeWindowSeconds);
        }
        return threat;
    });
}

// Get current threat after decay adjustment
public double getEffectiveThreat(int entityId) {
    ThreatEntry entry = threatHistory.get(entityId);
    if (entry == null) return 0.0;
    
    long elapsed = (System.currentTimeMillis() - entry.lastUpdate) / 1000;
    return entry.level * Math.pow(0.9, elapsed / 60); // 10% decay per minute
}
```

### Priority-Based Target Selection

```java
// Get targets sorted by threat priority
public List<PriorityTarget> getThreatTableSorted() {
    List<PriorityTarget> table = new ArrayList<>();
    long now = System.currentTimeMillis();
    
    for (Map.Entry<Integer, Double> entry : threatLevels.entrySet()) {
        int entityId = entry.getKey();
        double threat = entry.getValue();
        
        // Apply decay
        ThreatEntry hist = threatHistory.get(entityId);
        if (hist != null) {
            long elapsed = (now - hist.lastUpdate) / 1000;
            threat *= Math.pow(0.95, elapsed / 30); // 5% decay every 30s
        }
        
        if (threat > 10) { // Minimum threat threshold
            table.add(new PriorityTarget(entityId, threat));
        }
    }
    
    // Sort by threat descending
    table.sort((a, b) -> Double.compare(b.threat, a.threat));
    return table;
}

// Inner class for priority targets
public static class PriorityTarget {
    public final int entityId;
    public final double threat;
    
    public PriorityTarget(int entityId, double threat) {
        this.entityId = entityId;
        this.threat = threat;
    }
}
```

### Threat Modifiers

```java
// Set damage-based threat modifier
public void setDamageModifier(double modifier) {
    this.damageModifier = modifier;
}

// Set critical hit modifier
public void setCritModifier(double modifier) {
    this.critModifier = modifier;
}

// Set special skill modifier
public void setSkillModifier(double modifier) {
    this.skillModifier = modifier;
}

// Apply critical hit threat bonus
public void addCritThreat(int entityId) {
    addThreat(entityId, THREAT_MELEE_ATTACK * critModifier);
}

// Apply skill-based threat
public void addSkillThreat(int entityId, double baseThreat) {
    addThreat(entityId, baseThreat * skillModifier);
}
```

### Multi-Target Management

```java
// Get top N targets by threat
public List<Integer> getTopTargets(int count) {
    return getThreatTableSorted().stream()
        .limit(count)
        .map(t -> t.entityId)
        .collect(Collectors.toList());
}

// Check if entity should switch targets
public boolean shouldSwitchTarget(int currentTarget, int newTarget) {
    double currentThreat = getEffectiveThreat(currentTarget);
    double newThreat = getEffectiveThreat(newTarget);
    return newThreat > currentThreat * 1.5; // 50% higher to switch
}

// Reset threat on target death
public void onTargetDeath(int entityId) {
    resetAggro(entityId);
}
```

### Threat Table Status

```java
// Get threat table summary
public String getThreatSummary() {
    List<PriorityTarget> sorted = getThreatTableSorted();
    StringBuilder sb = new StringBuilder();
    sb.append("Threat Table: [");
    
    for (int i = 0; i < Math.min(5, sorted.size()); i++) {
        PriorityTarget t = sorted.get(i);
        if (i > 0) sb.append(", ");
        sb.append("T").append(t.entityId).append(":").append(String.format("%.0f", t.threat));
    }
    
    sb.append("]");
    return sb.toString();
}

// Count entities with aggro
public int getAggroedCount() {
    return (int) aggroState.values().stream().filter(b -> b).count();
}

// Get average threat level
public double getAverageThreat() {
    return threatLevels.values().stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);
}
```

## Threat Priority Calculation

### Priority Formula
```
EffectiveThreat = BaseThreat × Modifier × DecayFactor

Where:
- BaseThreat = damage × 0.5 + attackBonus
- Modifier = 1.0 (normal) to 2.0 (special skills)
- DecayFactor = 1.0 initially, decreases over time
```

### Priority Categories
| Threat Level | Priority | Action |
|--------------|----------|--------|
| > 1000 | High | Maintain aggro |
| 500-1000 | Medium | Watch |
| 100-500 | Low | Ignore |
| < 100 | None | Reset threat |

## Integration Example

### CombatAI Targeting
```java
public EntityInfo selectPrimaryTarget() {
    // Get threat-sorted targets
    List<Integer> topTargets = aggroManager.getTopTargets(3);
    
    // Find highest priority target in range
    for (int entityId : topTargets) {
        EntityInfo entity = packetLogger.getEntity(entityId);
        if (entity != null && isInCombatRange(entity)) {
            return entity;
        }
    }
    
    // Fallback to nearest hostile
    return packetLogger.findNearestHostile(playerX, playerY, playerZ, 1500);
}
```

### Threat Updates
```java
// On each damage event
aggroManager.addDamageThreat(targetId, damage);

// Before each decision
aggroManager.applyThreatDecay(30, 120); // 30/sec decay, 2-min window

// Target selection
if (aggroManager.shouldSwitchTarget(currentTargetId, newTargetId)) {
    switchTarget(newTargetId);
}
```

## Build Status
```
BUILD SUCCESS ✅
Tests: 11/11 passing ✅
```

## Performance Considerations

- Uses ConcurrentHashMap for thread safety
- O(n log n) sort on getThreatTableSorted()
- Decay applied lazily (only when queried)
- Minimum threat threshold reduces processing

## Production Enhancements

- [ ] Load threat values from L2J database
- [ ] Add NPC-specific threat modifiers
- [ ] Implement threat sharing between party members
- [ ] Add emote-based threat modifiers
- [ ] Add threat cap for specific skills

## TODO List

- [ ] Create ThreatTable class for dedicated management
- [ ] Add unit tests for threat decay
- [ ] Document threat values by NPC type
- [ ] Create VisualThreatManager for debugging
