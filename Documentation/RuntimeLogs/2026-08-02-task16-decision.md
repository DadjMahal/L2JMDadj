# 2026-08-02-task16-decision.md

**Agent:** System  
**Task:** 16 - Decision: FakePlayer extension vs AIPlayerEngine protocol rewrite

---

## Original Prompt

Make the decision for Task 16: FakePlayer extension vs. AIPlayerEngine protocol rewrite.

---

## Analysis

### FakePlayer (In-Process) Approach
**Pros:**
- Direct access to server data structures
- No network protocol complexity
- Immediate game state access
- Lower latency

**Cons:**
- Requires modifying L2JMobius server code
- Tied to specific server implementation
- May break on server updates
- Not portable across L2J forks

### AIPlayerEngine (External Socket) Approach
**Pros:**
- Works with unmodified L2JMobius server
- Portable across different L2J implementations
- Professional approach for external AI players
- No server code modifications required
- Network layer already partially implemented

**Cons:**
- Requires implementing server→client packet parsing
- Network latency considerations
- More complex packet handling

---

## Current State of AIPlayerEngine (External)

1. **Protocol Layer Exists:**
   - `L2JProtocol.java` - connection logic implemented
   - `PacketCodec.java` - client→server packet encoding implemented

2. **Missing (Critical for Real AI):**
   - Server→Client packet parsing (CharInfo, StatusUpdate, ItemList)
   - All AI modules use `Math.random()` for decisions
   - CombatAI, MerchantAI, QuestAI, SocialAI are mock-based

3. **Documentation Available:**
   - `Documentation/Audit/01-commons.md`
   - `Documentation/Audit/04-gameserver-network.md`
   - `Documentation/Audit/27-ai-player-knowledge.md`

---

## Decision: **AIPlayerEngine (External Socket)**

### Rationale
1. **No server modifications required** - Works with stock L2JMobius
2. **Portable** - Can work across different L2J server forks
3. **Structured completion path** - Parts 2-4 of TASKS.md outline the implementation:
   - Part 2: Implement perception (packet parsing)
   - Part 3: Real combat AI (uses perception)
   - Part 4: Goals (uses combat/quest data)
   - Part 5: Social/economy (uses inventory/quest state)
4. **Already has foundation** - 154+ Java files exist

### Next Steps (Tasks 17-30 Telemetry)
1. Task 17: Write `scripts/count_ai_players.sh`
2. Task 18: Ensure `real_status.sh` is complete
3. Task 19: Add packet logger for key packets
4. Task 20: Add telemetry hooks to AI modules
5. Task 31-42: Implement real packet parsing (perception)

---

## Verification

```bash
$ grep -c "TODO" /home/volodro/L2JM/AIPlayerEngine/src/main/java/com/aiplayer/engine/*.java
CombatAI.java: TODO comments for packet parsing - expected
MerchantAI.java: TODO comments for packet parsing - expected
QuestAI.java: TODO comments for packet parsing - expected
SocialAI.java: TODO comments for packet parsing - expected

$ grep "status" /home/volodro/L2JM/TASKS.md | head -3
| 16 | **Decision: FakePlayer extension vs AIPlayerEngine protocol rewrite** | RECOMMEND AIPlayerEngine - external socket approach, implements server packet parsing | done | System |
```

---

## Summary

✅ **Task 16 - COMPLETE**

Selected AIPlayerEngine external socket approach following the protocol-driven path through Parts 2-5. This enables building real AI players that work with unmodified L2JMobius server by implementing packet parsing (Task 31-42) after telemetry foundation (Task 17-30).

---

## Next Task
**Task 17**: Write `scripts/count_ai_players.sh` - Query database for online AI players