# SESSION IN PROGRESS — B7: live trade proof (PARTIAL — buy-open blocked)

> Resume: B7 not PROVEN. TradeProbe is committed WIP; merchant targeting works live; buy-window OPEN is
> blocked by bypass validation (details below). Resume from the "Next step".

## What works (live-verified)
- `TradeProbe` logs in, enters world, and **locates + targets Trader Silvia (30003)** via live `NPC_INFO`.
- Merchant-buy protocol fully traced: `Buy 3000301` bypass → `BuyList`(0x11) → `RequestBuyItem`(0x1F).
- Bot is positioned at Silvia's spot (-83789,240799,-3717) with **500k adena** (item 57, object_id 900000001).

## Blocker
`RequestBypassToServer.runImpl` → `player.validateHtmlAction(<bypass>)`; an un-issued bypass is **silently
dropped** (`bypassOriginId==-1`). Generic Trader 30003 emits **no HTML menu on a plain `Action` click**
(no onFirstTalk), so `Buy 3000301` is dropped → no `BuyList`. See `Audit/38-b7-live-trade.md`.

## Next step (choose one)
1. **Use a merchant/shop NPC that HAS an onFirstTalk HTML menu** (emits a Buy link) → bot clicks → parses the
   validated Buy action from NpcHtmlMessage(0x0F) → sends it → BuyList → RequestBuyItem. (Most "genuine" path.)
2. **Operator convenience:** add "Buy"/`_possibleNonHtmlCommands` bypass exception (test server only) so the
   bot's `Buy <listId>` is accepted directly. (Fast, but a server-behavior change — needs user OK.)

Run: `cd AIPlayerEngine && mvn clean compile`; `scripts/b7` (TBD) or `setsid bash -c 'java -cp target/classes
com.aiplayer.examples.TradeProbe ai_combat_01 ai123pass 127.0.0.1 7777'`; then verify DB adena (57) / item row.

