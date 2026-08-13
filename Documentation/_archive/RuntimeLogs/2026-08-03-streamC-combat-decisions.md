# Stream C — slice 2: real combat packet encoders + deterministic (non-mock) CombatAI decisions

**Agent:** System (volodro) · **Board:** Stream C (wire proven packets into the engine) · Date: 2026-08-03

## Goal (this slice)
Remove the remaining `Math.random()` mock paths in `CombatAI` and give the engine the **real,
probe-proven client combat frames** so a decision can actually become a wire packet.

## Changes
1. **`protocol/PacketCodec.java`** — added real client combat encoders matching the B4-proven
   `CombatProbe` framing (2-byte self-inclusive size + `[opcode][fields]`):
   - `encodeAction(targetObjId, originX, originY, originZ)` → `[0x04][objId][oX][oY][oZ][0x00]` (20 B).
   - `encodeAttackRequest(targetObjId)` → `[0x0A][objId][0][0][0][0x00]` (20 B).
   (The old `encodeAttack` prepended an attacker objId and used 2-byte-opcode framing — avoided.)
2. **`engine/CombatAI.java`** — replaced the two mock `Math.random()` paths with real data:
   - `calculateDistanceTo(targetId)` now computes real 3D Euclidean distance from the `PacketLogger`
     `EntityInfo` position to the player's position (`Double.MAX_VALUE` for untracked/missing target).
   - `shouldDefend()` now deterministic: threat from **real** HP% (`StatusUpdate`) + **real** hostile
     count (`getHostileEntityCount()`), threshold `>= 0.3` (was `Math.random() < threat`).
   - Added `getPacketLogger()` (telemetry/inject real packets) + `getSelectedTargetObjId()` (feeds the
     real `Action`/`AttackRequest` frames to the executor).

## Tests (added)
- `PacketCodecCombatFrameTest` (2): byte-layout checks for `encodeAction`/`encodeAttackRequest`
  (size header, opcode, field offsets).
- `CombatAITest` (3 new): real-distance from PacketLogger coords (3000,4000,0 → 5000), unknown/null
  target → MAX_VALUE, deterministic `shouldDefend()` (low HP + 2 hostiles → true; healthy + 1 → false).

## Result (verified, not claimed)
```
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```
(CombatAITest grew 11→14; PacketCodecCombatFrameTest 2 new.)

## Next (Stream C continuation)
- Route decisions → sends: an executor that, on an `ATTACK`/`ENGAGE_TARGET` decision, sends
  `encodeAction` + (after ~1s flood-protector delay) `encodeAttackRequest` over the real GS socket
  (classic `Socket`, game-crypt plaintext — as the probes do), and `StatusUpdate`/`QuestList` parsed
  into `CombatAI`/`QuestAI`. Then B6b (NPC talk + `RequestBypassToServer`(0x21)).
