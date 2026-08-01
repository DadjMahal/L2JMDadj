# Next Iteration Plan - AI Player Engine Enhancement

**Date:** 2026-08-01 21:15 UTC
**Status:** READY FOR NEXT SESSION
**Next Session Start Command:** `./StartServer.sh` → `mvn compile exec:java ... --spawn-all`

---

## 📋 SAVED PLAN: Iteration 1 - Phase 3 Completion

### Priority Goal
Complete Phase 3 Integration (Tasks 97-132) - Advanced AI Player Behaviors

### Tasks to Complete

#### Combat & PvP Enhancement (Task 97-108)
| Task # | Description | Priority | Next Action |
|--------|-------------|----------|-------------|
| 97 | PK decision engine | ✅ COMPLETE | INTEGRATE into AI loop |
| 98 | Safe-zone awareness | ⚠️ IN PROGRESS | Add to PKDecision.safeZoneCheck() |
| 99 | PvP target prioritization | ⚠️ NEEDED | Improve CombatAI.prioritizeTarget() |
| 100 | PvP skill rotation | ⚠️ NEEDED | Create SkillRotationEngine |
| 101 | Olympiad participation | ✅ EXISTS | Connect to OlympiadAI |
| 102 | Hero title optimization | ✅ EXISTS | Connect to HeroTitleAI |
| 103 | Siege positioning | ✅ EXISTS | Integrate with siege events |
| 104 | Zone buff awareness | ⚠️ NEEDED | Add to CombatAI buff check |
| 105 | Karma/death handling | ⚠️ NEEDED | Integrate with combat end |
| 106 | PvP survivability | ⚠️ NEEDED | Improve heal logic |
| 107 | Arena/Coliseum AI | ✅ EXISTS | Test combat scenarios |
| 108 | Anti-griefing | ✅ EXISTS | Test edge cases |

#### Dungeons & Instances (Task 109-120)
| Task # | Description | Priority | Next Action |
|--------|-------------|----------|-------------|
| 109 | Dimensional Rift AI | ✅ EXISTS | Test rift entry |
| 110 | Four Sepulchers AI | ✅ EXISTS | Test necropolis system |
| 111 | Coliseum participation | ✅ EXISTS | Test arena logic |
| 112 | Proxima/Seal hunting | ⚠️ NEEDED | Create HuntBot.java |
| 113 | Hunting grounds routing | ✅ EXISTS | Integrate HuntingRotation |
| 114 | Catacombs access | ⚠️ NEEDED | Add dungeon entry points |
| 115 | Key/drop management | ⚠️ NEEDED | Enhance LootDistributor |
| 116 | Group coordination | ⚠️ NEEDED | Improve party sync |
| 117 | Instance coordination | ⚠️ NEEDED | Add coordinate methods |
| 118 | Reset/checkpoint | ⚠️ NEEDED | Add safe point saving |
| 119 | Loot distribution | ✅ EXISTS | Test fair distribution |
| 120 | Instance exit | ⚠️ NEEDED | Add escape routes |

---

## 📁 FILES TO CREATE

### 1. HuntBot.java
**Purpose:** Intelligent hunting grounds navigation
**Location:** `/src/main/java/com/aiplayer/engine/HuntBot.java`
**Methods:**
- `findOptimalHuntingZone()` - Select best mob spawn area
- `calculateHuntingRoute()` - Efficient path around hunting grounds
- `handleSpawnWaves()` - Manage monster respawn cycles

### 2. SkillRotationEngine.java
**Purpose:** Class-specific PvP skill rotations
**Location:** `/src/main/java/com/aiplayer/engine/SkillRotationEngine.java`
**Methods:**
- `getSkillRotationForClass(int classId)` - Return optimal skill sequence
- `executeSkillChain()` - Perform burst damage combo
- `counterSkillCheck()` - Detect and counter enemy buffs

### 3. PKDecisionExt.java
**Purpose:** Enhanced PK decision making with safe-zone integration
**Extenders PKDecision.java**
**Methods:**
- `isSafeZone(int x, int y)` - Town/coordinate check
- `canPK(PKBot attacker, PKBot target)` - Full status check
- `suggestEscapeRoute()` - Recommend safe path

---

## 📁 FILES TO MODIFY

### 1. CombatAI.java
- Integrate PKDecision for PvP checks
- Add safe-zone awareness
- Implement skill cooldown tracking
- Add heal timing optimization

### 2. AIBrain.java
- Connect HuntBot for dungeon scenarios
- Add siege mode detection
- Implement karma-based behavior

### 3. AIPlayerManager.java
- Add `spawnPKBot()` for PvP testing
- Add `spawnHuntBot()` for farming tests
- Add `spawnSiegeBot()` for castle wars

---

## 🔧 IMPLEMENTATION ORDER

### Step 1: Kill Existing Processes
```bash
pkill -f "GameServer.jar" || true
pkill -f "LoginServer.jar" || true
```

### Step 2: Create HuntBot.java
```bash
# Create file with pathfinding for hunting grounds
# Integrate with HuntingRotation.java
```

### Step 3: Enhance CombatAI
```java
// Add safe zone check
// Add skill rotation
// Add karma handling
```

### Step 4: Update AIBrain
```java
// Add PKDecision integration
// Add HuntBot decision path
```

### Step 5: Test Compilation
```bash
cd /home/volodro/AIPlayerEngine
mvn compile
```

---

## 🧪 TESTING PLAN

### Test 1: Server Connection
- Verify L2JM server connects
- Verify 4 AI players connect successfully
- Check for protocol errors

### Test 2: Combat Test
- Spawn 2 Combat AI Players
- Move to hunting grounds
- Verify combat decisions execute

### Test 3: Quest Test
- Spawn 1 Quest AI Player
- Verify quest acceptance logic
- Check NPC interaction packets

### Test 4: Trade Test
- Spawn 1 Merchant AI Player
- Verify movement to merchant
- Check buy/sell decision

### Test 5: Social Test
- Spawn 1 Social AI Player
- Verify chat messages
- Check IDLE pattern

---

## 📊 SUCCESS METRICS

| Metric | Target | Measurement |
|--------|--------|-------------|
| Build Success | 100% | `mvn compile` exit code 0 |
| Server Connection | 4/4 | Player count in logs |
| Combat Actions | 10/min/player | Log activity count |
| Quest Actions | 2/min/player | Quest decision logs |
| Trade Actions | 1/min/player | Merchant AI logs |
| Social Actions | 5/min/player | Chat/Party logs |

---

## 🚨 RISK MITIGATION

### Risk: Server Connection Failure
**Mitigation:** 
- Pre-check database accounts exist
- Start server first, wait for "Server loaded"
- Use connection retry logic

### Risk: Protocol Errors
**Mitigation:**
- Log all packet sends/receives
- Implement packet validation
- Add connection health checks

### Risk: AI Gets Stuck
**Mitigation:**
- Add anti-stuck detection
- Implement emergency teleport
- Add state reset on timeout

---

## 📝 DAILY WORKFLOW REMINDER

1. **Start:** Read `SMARTPROJECT.md` for context
2. **Work:** Implement tasks following existing patterns
3. **Verify:** Run `mvn compile` - must show `BUILD SUCCESS`
4. **Update:** Edit this plan with progress
5. **Save:** Git commit with clear message
6. **Log:** Update WorkLog with completed work

---

## 🎯 NEXT SESSION CHECKLIST

- [ ] Kill existing server processes
- [ ] Backup current AIPlayer.java/AIBrain.java
- [ ] Create HuntBot.java
- [ ] Create SkillRotationEngine.java
- [ ] Modify CombatAI.java
- [ ] Modify AIBrain.java
- [ ] Compile and verify BUILD SUCCESS
- [ ] Create new runtime log
- [ ] Update SESSION_STATUS.md

---

**Saved by:** AI Agent
**Repository:** /home/volodro/AIPlayerEngine
**Branch:** master
**Last Commit:** 2f5515c Refactored Phase 3+ to L2 Interlude relevance

---
*End of saved plan*
