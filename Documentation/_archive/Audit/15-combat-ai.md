# 15 — Combat AI Audit

**Part of Task 56** - Document combat AI in Audit docs

---

## Overview

Combat AI enables AI players to engage with NPCs and other players using real game state data instead of random decisions.

---

## Part 2 — Core Components

### CombatAI.java (engine/CombatAI.java)

Main combat decision engine. Implements the decision tree:

detectNearbyEnemy() -> engageEnemy()
                 |
        manageActiveCombat()
                 |
    shouldRetreat() -> retreat()
                 |
    shouldDefend() -> defensiveAction()
                 |
    shouldHeal() -> heal()
                 |
    shouldUseSkill() -> useOffensiveSkill()
                 |
    default -> attack()

#### Key Methods

| Method | Purpose | Real Data Source |
|--------|---------|------------------|
| detectNearbyEnemy() | Finds nearest hostile entity | PacketLogger.findNearestHostile() |
| calculateDistanceTo() | Distance to target | Entity position from NPC_INFO |
| shouldHeal() | HP threshold check | PacketLogger.getHpPercentage() |
| shouldRetreat() | Flee at | PacketLogger HP/MP tracking |
| shouldDefend() | Block when threatened | HP + target NPC ID |
| shouldUseSkill() | MP threshold check | PacketLogger.getMpPercentage() |
| selectBestSkill() | Skill priority selection | CombatConfig skill priority |

---

## Part 3 — Data Flow

### Enemy Detection Flow

Server -> NPC_INFO packet (0x16)
         |
    PacketLogger.parseNpcInfo()
         |
    EntityTracker.addEntity()
         |
    isHostileNpc(npcId) - classify hostile
         |
CombatAI.detectNearbyEnemy()
         |
CombatDecision.engageTarget()

### HP/MP Tracking Flow

Server -> StatusUpdate packet (0x0E)
         |
    PacketLogger.parseStatusUpdate()
         |
    Track curHp, maxHp, curMp, maxMp
         |
CombatAI.getCurrentHPPercentage()
CombatAI.getCurrentMPPercentage()
         |
    Used in shouldHeal(), shouldUseSkill()

---

## Part 4 — Telemetry

All combat decisions logged:

[COMBAT-TELEMETRY] [PlayerName] action=ATTACK target=12345 
    hp=85% mp=35% decision_ms=23 entities=5 hostile=1

Logged when:
- Combat decision made
- HP/MP changes
- Target acquired
- Combat state change

---

## Part 5 — Real vs Mock Implementation

| Before | After |
|--------|-------|
| Math.random() > 0.3 enemy detection | Entity tracking from NPC_INFO |
| 85 + (int)(Math.random() * 15) HP | StatusUpdate packet HP |
| 60 + (int)(Math.random() * 40) MP | StatusUpdate packet MP |
| Random defend chance | HP-based threat assessment |
| No retreat logic | HP < 15% or surrounded |
| No distance calculation | Real entity position tracking |

---

## Part 6 — Testing

Unit tests in CombatAITest.java:

- testCombatStateTransitions() - State machine verification
- testCombatDecisionFactoryMethods() - All decision types
- testCombatDecisionToString() - Serialization

---

## Related Documentation

- _archive_superseded/TASK_ROADMAP_110.md - archived 110-task roadmap
- SESSION_PROTOCOL.md - Agent session workflow  
- MULTI_AGENT_RULES.md - Multi-agent coordination
- SCRIPT/verify_no_dead_code.sh - Dead code verification
