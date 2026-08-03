# Stream C — slice 3: decision → send (combat frame planner + GS frame writer + movement encoder)

**Agent:** System (volodro) · **Board:** Stream C (wire proven packets into the engine) · Date: 2026-08-03

## Goal (this slice)
Close the loop from a `CombatDecision` to actual wire bytes: a pure, testable planner that maps a
decision to the exact ordered client frames (with the server flood-protector gap), plus a frame writer
that emits them over a GameServer socket, plus the proven `MoveToLocation` encoder.

## Changes
1. **`protocol/PacketCodec.java`** — added `encodeMoveToLocation(targetX,Y,Z, originX,Y,Z, moveType)`
   → `[0x01][target][origin][moveType]` (31 B), matching the B8-proven MoveProbe layout.
2. **`engine/CombatFramePlanner.java`** (NEW) — pure decision→frame mapping returning ordered
   `FrameStep(frame, delayAfterMs)`:
   - ENGAGE_TARGET / ATTACK → `Action`(0x04) then, after `FLOOD_PROTECTOR_DELAY_MS`(1000), `AttackRequest`(0x0A).
   - FLEE / RETREAT / BLOCK → `MoveToLocation`(0x01) to a deterministic escape offset.
   - IDLE / USE_SKILL (skill opcode not yet proven) → no frame.
3. **`protocol/GameServerFrameWriter.java`** (NEW) — writes an already-framed packet (2-byte size +
   payload) to an OutputStream and flushes; rejects malformed frames.
4. **`engine/AIPlayerConnection.java`** — added `setGameServerWriter(...)` and
   `executeCombatDecision(decision, targetObjId)` which plans with `CombatFramePlanner`, writes the
   frames via the attached GS writer (when present), respects the flood gap, and logs each frame
   (`COMBAT_SEND opcode=0x..`).

## Tests (added; 41 → 49 total, all pass)
- `CombatFramePlannerTest` (5): attack → Action+AttackRequest with 1000ms gap; no-target → empty;
  flee → single MoveToLocation(0x01, 31 B); idle → empty; null → empty.
- `GameServerFrameWriterTest` (2): writes a frame verbatim + flush; rejects null/short frames.
- `PacketCodecCombatFrameTest` (+1): `encodeMoveToLocation` full byte-layout (real B8 coords).

## Result (verified, not claimed)
```
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```

## Honest scope note
This slice makes the send path **real and correctly framed** (planner + writer + encoders), but the
engine does not yet hold a persistent GameServer socket to attach the writer to — the probes do that via
their own classic `Socket`. Attaching a reusable in-engine GS client (reusing the proven B3 Phase-2
handshake) and running a live decision→send proof is the next step.

## Next (Stream C continuation)
- In-engine reusable GameServer client: retain the proven handshake (ProtocolVersion → KeyPacket →
  AuthLogin → CharSelect → CharSelected → EnterWorld) over a classic `Socket`, expose an OutputStream to
  `setGameServerWriter`, and drive `executeCombatDecision` from `CombatAI.makeDecision()` in a live loop.
- Then StatusUpdate/QuestList feedback into CombatAI/QuestAI, and B6b (NPC talk + RequestBypassToServer).
