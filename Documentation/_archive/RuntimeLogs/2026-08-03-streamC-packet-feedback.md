# Stream C — Slice 6: Packet Feedback into the Decision Loop (LIVE PROVEN 2026-08-03)

## Result

Slice 6 closes the "the loop doesn't react to the server" gaps exposed by Slice 5's live run
(where the engine kept firing Action/AttackRequest frames for the full window even after the bot died).
Now the decision loop consumes real `StatusUpdate` / `DeleteObject` feedback. 59/59 tests, BUILD SUCCESS.

Live evidence (40s run, `/tmp/c5_combat40.txt`):
```
INFO: STATUS_UPDATE: objId=2 [self=true] [MAX_HP=145, CUR_HP=19, ...]  <- real self HP declining
INFO: STATUS_UPDATE: objId=2 [self=true] [MAX_HP=145, CUR_HP=8,  ...]
INFO: STATUS_UPDATE: objId=2 [self=true] [MAX_HP=145, CUR_HP=11, ...]
INFO: STATUS_UPDATE: objId=2 [self=true] [MAX_HP=145, CUR_HP=0,  ...]  <- server reports death
[CombatLoop] DEAD — server StatusUpdate shows self HP 0; pausing combat sends
[CombatLoop] LIVE COMBAT LOOP COMPLETE sentActions=46 ...
```
Last Action/AttackRequest frame was sent BEFORE the HP-0 StatusUpdate; **0 frames sent after death**
(was: continuous firing for the whole window). DB: curHp=0 afterwards.

## Fixes

1. **`PacketLogger` objId-aware self tracking** — the server sends `StatusUpdate` for the player AND
   every monster (live: 23 `self=false` updates per run). `parseStatusUpdate` previously let any
   entity's update overwrite the bot's HP/MP (a wolf's 107→103 would clobber the bot). Added
   `setSelfObjectId(int)` (+ getter) and only let `objectId == selfObjectId` drive self HP/MP
   (legacy behavior kept when unset, so existing single-buffer tests still pass).
2. **`CombatAI.makeDecision()` death gate** — when self HP hits 0, end combat + return `IDLE`; new
   `CombatAI.isBotAlive()`. The live driver gates on it and prints `[CombatLoop] DEAD ...` /
   `[CombatLoop] ALIVE ...` (resume if HP returns).
3. **`CombatAI.handleCombatEnd()` now actually ends combat** — it calls `onCombatEnd()` (clears
   `inCombat` + `currentTarget`) instead of returning LEAVE_COMBAT while staying in combat. Combined
   with `DeleteObject` (0x12) already removing the entity, a dead/despawned target → out-of-range →
   combat end → re-acquire the next enemy (RE_TARGET log on target switch).
4. **`PacketLogger.parseDeleteObject` log → INFO** so the live proof can observe target despawn.
5. **`CombatLoop`** sets `setSelfObjectId(charId)` and adds the alive gate; proof script now reports
   `slice-6 death gate: fired=N; frames after death = M`.

## New tests (55 -> 59)

- `PacketLoggerNpcInfoTest.testSelfStatusUpdateNotClobberedByTarget` — target update does not clobber self HP.
- `PacketLoggerNpcInfoTest.testDeleteObjectRemovesTrackedEntity` — entity removed from tracking.
- `CombatAITest.testDeathGateReturnsIdle` — dead bot returns IDLE, selects no target.
- `CombatAITest.testRetargetsAfterTargetDeath` — DeleteObject → LEAVE_COMBAT → re-attack next enemy.

## Note
RE_TARGET did not fire live (wolves don't despawn within the window / the bot dies first); the
re-target path is unit-tested and fires whenever a DeleteObject removes the engaged target while alive.
