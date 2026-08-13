# Stream C — slice 1: PacketLogger.parseNpcInfo off-by-one fix + real test reconciliation

**Agent:** System (volodro) · **Board:** Stream C (wiring proven packets into the engine) · Date: 2026-08-03

## Goal (this slice)
Fix the long-documented `PacketLogger.parseNpcInfo` OFF-BY-ONE vs the real Interlude
`AbstractNpcInfo` wire layout (Audit/35 discovery #3), and reconcile the fake/scaffolded
tests (TASKS 54, 63, 42) to the proven format. This is the first concrete Stream C step toward
wiring real combat packets from the B4–B10 probes into the engine decision classes.

## Real wire layout (proven by CombatProbe / Audit/35)
`[0x16][objectId][displayId = npcId+1000000][isAttackable][x][y][z][heading]...`
- objectId@1, displayId@5, **isAttackable@9**, x@13, y@17, z@21, heading@25 (offsets after opcode).
- The old code read `[objectId][id][x][y][z]` → it misread `isAttackable` as x (position shifted 4 bytes early).

## Changes
1. **`protocol/PacketLogger.java` — `parseNpcInfo` fixed** to the real layout: now reads
   `objectId, displayId, isAttackable, x, y, z, heading`; sets `npcId = displayId - 1000000`;
   derives hostility from the real packet flag (`isAttackable != 0 || isHostileNpc(npcId)`, keeping the
   range heuristic as a fallback); stores real heading in `EntityInfo` instead of always 0.
2. **NEW `test/java/com/aiplayer/protocol/PacketLoggerNpcInfoTest.java`** — 4 regression tests using
   real B4 values (Elder Keltir objId 268439316, displayId 1020544, pos -83479,250275,-3596):
   attackable-hostility, correct position (off-by-one regression), heading parse, nearest-hostile uses
   real coords.
3. **`test/java/com/aiplayer/engine/CombatAITest.java`** — replaced the two fake `assertTrue(true)`
   tests with real assertions:
   - task 54 `testCombatDecisionNotNull` → calls `makeDecision()` and asserts non-null decision + action.
   - task 63 `testCombatAI_PvPMethods` → calls `makePvPDuidedDecision()` and asserts non-null decision +
     action, plus non-null karma/skill helpers.
4. **`test/java/com/aiplayer/engine/PerceptionAccuracyTest.java`** (task 42) — reconciled to the real
   NPC_INFO layout (builders now emit `[objectId][npcId+1000000][isAttackable][x][y][z][heading]`);
   fixed 3 pre-existing broken test-data bytes (CharInfo heading 0→32768, position x/y 10192/20016→
   10000/20000, HP threshold `>50`→`>=50` to match the 50% fixture).

## Result (verified, not claimed)
```
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
- CombatAITest (11) — the two formerly-fake tests now do real assertions.
- PacketLoggerNpcInfoTest (4) — passes under the fixed parser (would fail under the old one).
- PerceptionAccuracyTest (17) — green again after layout reconciliation.

## Next (Stream C continuation)
- Wire real `Action`/`AttackRequest`/`StatusUpdate`/`QuestList`/trade/movement/chat/party sends into
  the engine decision classes (`CombatAI`/`QuestAI`/`PacketLogger`), replacing the remaining mock
  `Math.random()` paths (`calculateDistanceTo`, `shouldDefend`). B6b (NPC talk + `RequestBypassToServer`).
