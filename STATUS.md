# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (ALL live proofs PROVEN: PvE + PvP + quest + trade + movement + chat + party)
## Last completed: **Stream C slice 4 (2026-08-03)** — reusable in-engine `GameServerClient` (classic Socket): retained the proven B3/B4 handshake (ProtocolVersion → KeyPacket → AuthLogin → CharSelectInfo → CharacterSelect → CharSelected → EnterWorld), background reader feeding `PacketLogger`, `attachToConnection` + `sendGameFrame`. Handshake payload builders added to `PacketCodec` (ProtocolVersion/AuthLogin/CharacterSelect/EnterWorld). **54/54 tests PASS, BUILD SUCCESS** — incl. in-process fake-GS integration test of the whole handshake + a real `Action`(0x04) send. (Earlier slices: 1 parseNpcInfo fix 36/36 → 2 encoders+no-random 41/41 → 3 decision→send 49/49)
## Current (in progress): **Stream C** — wire the pieces live: a driver that does login → `GameServerClient.connectAndEnterWorld` → `startReader` → `CombatAI.makeDecision()` → `executeCombatDecision` in a loop, then run the live proof against the server. Then StatusUpdate/QuestList feedback + B6b.
## Next: live driver + proof script; then StatusUpdate/QuestList feedback; then B6b.
## Blockers: ~145 unwired stub classes (Stream G); 23 ai_% chars still in the void spawn (relocate+heal before multi-bot gameplay).

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105), live PvP (B5, two bots
mutual hits + damage), AND live quest (B6, server-side Q00255 state mutation persisted) all PROVEN by the
probes. The engine now has real parsing (slice 1), real encoders + no-random decisions (slice 2),
decision→send planning (slice 3), a reusable in-engine GS client (slice 4), and a **live combat loop
driver + proof (slice 5)** — `CombatLoop` logs in, enters the world, detects real hostiles via the shared
`PacketLogger` (`CombatAI.setPacketLogger`), and sends real Action/AttackRequest frames in a loop.
**C5 PROVEN live**: `scripts/c5_live_combat_proof.sh` scored `engaged-actions=18`,
`serverConfirmedDamage=1` (target wolf HP 107→103 in server StatusUpdate). 55/55 tests, BUILD SUCCESS.

**Slice 6 (packet feedback) — DONE & LIVE PROVEN (59/59 tests):** the decision loop now *reacts* to real
`StatusUpdate`/`DeleteObject` packets. `PacketLogger` self-tracking is objId-aware (`setSelfObjectId`;
a target wolf's StatusUpdate no longer clobbers the bot's HP — live saw 23 `self=false` updates ignored);
`CombatAI` death-gates `makeDecision()` + `isBotAlive()` (live: self HP 145→…→0 → `[CombatLoop] DEAD` →
**0 Action frames after death**, vs unlimited before); `handleCombatEnd()` now really ends combat so a
DeleteObject'd target leads to re-acquiring the next enemy (RE_TARGET). RuntimeLog/2026-08-03-streamC-packet-feedback.md.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-streamC-packet-feedback.md
- 2026-08-03-streamC-live-driver.md
- 2026-08-03-streamC-gs-client.md
- 2026-08-03-streamC-decision-to-send.md
- 2026-08-03-streamC-combat-decisions.md
- 2026-08-03-streamC-npc-info-fix.md
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
