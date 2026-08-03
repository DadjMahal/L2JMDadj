# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (live PvE combat PROVEN; live gap now only PvP/quest/trade and mock-data wiring)
## Last completed: B4 — live NPC combat proof (CombatProbe): AI player attacked real Wolf/Keltir, 18 ATTACK(0x05) hits, exp 0→105, level 1→2 (Audit/35, scripts/b4_combat_prove.sh)
## Next task: **B5** — live PvP proof (or wire CombatAI/PacketLogger to the real packets CombatProbe proved). B6–B10 (quest, trade) after.
## Blockers: fake-test tasks 54/63 (Stream C); ~145 unwired stub classes (Stream G); PacketLogger.parseNpcInfo off-by-one (real AbstractNpcInfo layout) — needs fix; 24 remaining ai_% chars still in the void spawn (16600,17000,434).

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live combat PROVEN: CombatBot_01 fought a Talking Island
Wolf/Elder Keltir — server ATTACK(0x05) packets + exp 0→105 + level 1→2. Combat/Quest/Merchant/Social AI engine
classes still use mock data internally, but the external-socket path to real packets is now demonstrated.
25 AI chars in DB; CombatBot_01 relocated to Wolf zone + healed for combat.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-102759-b4-combat.md
- 2026-08-03-a1-cold-start-test.md
- 2026-08-02-doc-gap-fix.md
