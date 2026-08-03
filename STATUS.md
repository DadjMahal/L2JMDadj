# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (ALL live proofs PROVEN: PvE + PvP + quest + trade + movement + chat + party)
## Last completed: **Stream C slice 2 (2026-08-03)** — real combat packet encoders `PacketCodec.encodeAction(0x04)`/`encodeAttackRequest(0x0A)` in the B4-proven framing (self-inclusive size + fields); `CombatAI.calculateDistanceTo` now real 3D distance from PacketLogger coords and `shouldDefend()` deterministic from real HP+hostile count (**no Math.random**); added `getPacketLogger()`/`getSelectedTargetObjId()`. **41/41 tests PASS, BUILD SUCCESS**. (slice 1: parseNpcInfo off-by-one fix + fake tests 54/63 → real → 36/36)
## Current (in progress): **Stream C** — continue wiring the proven PvE/PvP/quest/movement/chat/party/trade packets from the probes into the engine's real decision classes (CombatAI/QuestAI/PacketLogger), replacing the remaining mock paths. Next target: route a decision → send (executor that emits `encodeAction` + `encodeAttackRequest` over the real GS socket, as the probes do).
## Next: decision→send executor over the real GS classic socket; then B6b (bot earns a quest via NPC talk + `RequestBypassToServer`(0x21)).
## Blockers: ~145 unwired stub classes (Stream G); 23 ai_% chars still in the void spawn (relocate+heal before multi-bot gameplay).

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105), live PvP (B5, two bots
mutual hits + damage), AND live quest (B6, server-side Q00255 state mutation persisted) all PROVEN.
Combat/Quest/Merchant/Social AI decision classes still use mock data internally, but the external-socket
path to real PvE/PvP/quest packets is now demonstrated end-to-end. Stream C is closing the mock->real gap
(parsing + encoders now real; decision→send routing next).

## Recent RuntimeLogs (most recent first)
- 2026-08-03-streamC-combat-decisions.md
- 2026-08-03-streamC-npc-info-fix.md
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
