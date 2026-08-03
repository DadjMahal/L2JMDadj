# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (live PvE combat + live PvP PROVEN; live gap now only quest/trade and mock-data wiring)
## Last completed: B5 — live PvP proof (PvPProbe): CombatBot_01↔CombatBot_02 mutual attacks, attacker objId2:13/objId3:12 hits, CombatBot_02 took PvP damage (curHp 126→120) (Audit/36, scripts/b5_pvp_prove.sh)
## Next task: **B6** — live quest proof (or wire CombatAI/PacketLogger to the proven packets). B7–B10 (trade etc.) after.
## Blockers: fake-test tasks 54/63 (Stream C); ~145 unwired stub classes (Stream G); PacketLogger.parseNpcInfo off-by-one — needs fix; 23 remaining ai_% chars still in the void spawn (16600,17000,434).

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105, level 1→2) AND live PvP
(B5, two bots mutual hits + CombatBot_02 damage) PROVEN. Combat/Quest/Merchant/Social AI decision classes
still use mock data internally, but the external-socket path to real PvE + PvP packets is now demonstrated.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
- 2026-08-03-a1-cold-start-test.md
