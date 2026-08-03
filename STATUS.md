# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (ALL live proofs PROVEN: PvE + PvP + quest + trade + movement + chat + party)
## Last completed: **Stream C slice 3 (2026-08-03)** — decision→send: `CombatFramePlanner` maps a combat decision to ordered wire frames (Action 0x04 + flood-gap + AttackRequest 0x0A; FLEE → MoveToLocation 0x01), `GameServerFrameWriter` emits framed packets, `PacketCodec.encodeMoveToLocation` added (B8-proven), `AIPlayerConnection.executeCombatDecision(...)` + `setGameServerWriter(...)`. **49/49 tests PASS, BUILD SUCCESS**. (Earlier: slice 1 parseNpcInfo fix 36/36 → slice 2 real encoders + no-Math.random decisions 41/41)
## Current (in progress): **Stream C** — continue wiring the proven packets into the engine. Next target: the in-engine reusable GameServer client (retain the proven B3 handshake over a classic Socket, attach it to `setGameServerWriter`) so `CombatAI.makeDecision()` → `executeCombatDecision` drives real combat live.
## Next: in-engine GS client + live decision→send proof; then StatusUpdate/QuestList feedback; then B6b.
## Blockers: ~145 unwired stub classes (Stream G); 23 ai_% chars still in the void spawn.

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105), live PvP (B5, two bots
mutual hits + damage), AND live quest (B6, server-side Q00255 state mutation persisted) all PROVEN by the
probes. The engine's parsing, decision logic, and packet encoders are now real (no Math.random in CombatAI);
the remaining gap is attaching a persistent GameServer socket inside the engine and a live decision→send run.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-streamC-decision-to-send.md
- 2026-08-03-streamC-combat-decisions.md
- 2026-08-03-streamC-npc-info-fix.md
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
