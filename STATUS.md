# 📊 Status — L2JM

> Single live snapshot. Mirrored by `START_HERE.md`. Overwritten every session.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (rate-limited mid-work).

## Phase: 2 — Combat AI (live PvE + PvP + quest + movement + chat PROVEN; B7 trade IN PROGRESS)
## Last completed: B9 — live chat proof (ChatProbe): bot A (CombatBot_01) whispered token B9WHISPER_621452 to bot B (CombatBot_02) via Say2(0x38); server delivered CREATURE_SAY(0x4A) containing the token to B's connection (received=true) + echoed to A (echo=true). No level gate via WHISPER (GENERAL gated to L20). (Audit/40)
## Current (in progress): **B7 trade** — TradeProbe live-targets Trader (Silvia 30003) + full merchant-buy protocol mapped (Buy 3000301 bypass → BuyList 0x11 → RequestBuyItem 0x1F); buy-dialog OPEN blocked: RequestBypassToServer drops un-validated bypasses (validateHtmlAction) and this merchant emits no HTML menu on plain-click (Audit/38). Not PROVEN.
## Next (after B7): wire proven packets into CombatAI/QuestAI/PacketLogger (Stream C); B6b (bot earns quest via NPC talk).
## Blockers: B7 buy-open needs a merchant that emits an HTML Buy menu (or a bypass exception); fake-test tasks 54/63 (Stream C); ~145 stub classes (Stream G); PacketLogger.parseNpcInfo off-by-one; 23 ai_% chars in the void spawn.

## Honest state (source: real_status.sh + live probe evidence)
Server UP (LoginServer :2106, GameServer :7777). Live PvE combat (B4, exp 0→105), live PvP (B5, two bots
mutual hits + damage), AND live quest (B6, server-side Q00255 state mutation persisted) all PROVEN.
Combat/Quest/Merchant/Social AI decision classes still use mock data internally, but the external-socket
path to real PvE/PvP/quest packets is now demonstrated end-to-end.

## Recent RuntimeLogs (most recent first)
- 2026-08-03-122945-b6-quest.md
- 2026-08-03-114841-b5-pvp.md
- 2026-08-03-102759-b4-combat.md
