# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (ALL live proofs PROVEN: PvE + PvP + quest + trade + movement + chat + party)
## Last completed: **Stream C slice 1 (2026-08-03)** — `PacketLogger.parseNpcInfo` OFF-BY-ONE fixed to the real `AbstractNpcInfo` layout `[objId][displayId+1000000][isAttackable][x][y][z][heading]` (proven by B4/Audit/35); hostility now packet-derived (attackable) with heuristic fallback; fake tests TASKS 54 & 63 → real assertions; `PerceptionAccuracyTest` (42) reconciled to the real layout; **36/36 tests PASS, BUILD SUCCESS**. (RuntimeLog/2026-08-03-streamC-npc-info-fix.md)
## Current (in progress): **Stream C** — continue wiring the proven PvE/PvP/quest/movement/chat/party/trade packets from the probes into the engine's real decision classes (CombatAI/QuestAI/PacketLogger), replacing the remaining mock `Math.random()` paths. Next target: `Action`(0x04)+`AttackRequest`(0x0A) + `StatusUpdate`/`QuestList` routed into `CombatAI`/`QuestAI`.
## Next: wire probe-proven sends into the decision classes; then B6b (bot earns a quest via NPC talk + `RequestBypassToServer`(0x21)).
## Blockers: ~145 unwired stub classes (Stream G); 23 ai_% chars still in the void spawn (relocate+heal before multi-bot gameplay).

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105), live PvP (B5, two bots
mutual hits + damage), AND live quest (B6, server-side Q00255 state mutation persisted) all PROVEN.
Combat/Quest/Merchant/Social AI decision classes still use mock data internally, but the external-socket
path to real PvE/PvP/quest packets is now demonstrated end-to-end. Stream C is closing the mock->real gap.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-streamC-npc-info-fix.md
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
