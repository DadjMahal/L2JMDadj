# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (ALL live proofs PROVEN: PvE + PvP + quest + trade + movement + chat + party)
## Last completed: B7 — live trade proof (TradeProbe): CombatBot_01 bought item 118 (price 75) from Trader Silvia via the genuine merchant flow (2x Action -> NpcHtmlMessage 0x0F -> npc_<objId>_Buy 3000300 bypass 0x21 -> BuyList 0x11 -> RequestBuyItem 0x1F); server deducted adena 500000->499925->499850 and added item-118 rows. Corrects the earlier "blocker" misdiagnosis (needed 2 clicks + full npc_<objId>_Buy bypass). (Audit/38)
## Current (in progress): **Stream C** — wire the now-proven PvE/PvP/quest/movement/chat/party/trade packets from the probes into the engine's real decision classes (CombatAI/QuestAI/PacketLogger), replacing mock data. B6b (bot earns a quest via NPC talk + RequestBypassToServer 0x21) is a follow-on.
## Next: Stream C wiring; then reconcile fake-test tasks (54/63) with real assertions.
## Blockers: fake-test tasks 54/63 (Stream C); ~145 stub classes (Stream G); PacketLogger.parseNpcInfo off-by-one; 23 ai_% chars in the void spawn (others need relocate+heal before gameplay).

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105), live PvP (B5, two bots
mutual hits + damage), AND live quest (B6, server-side Q00255 state mutation persisted) all PROVEN.
Combat/Quest/Merchant/Social AI decision classes still use mock data internally, but the external-socket
path to real PvE/PvP/quest packets is now demonstrated end-to-end.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
