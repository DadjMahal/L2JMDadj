# 38 — B7: live trade proof (spec) — 2026-08-03

> **B7.** Prove an AI player buys an item from a merchant NPC over the external socket. `TradeProbe`
> positions CombatBot_01 near a Talking Island Trader, targets it, gets the `BuyList`(0x11), then sends
> `RequestBuyItem`(0x1F); the server deducts adena and adds the item → verified in `gameserver` DB.
> No L2JM server source changes.

## Verified server facts (Interlude, SourceCode)
- Talking Island Trader NPCs: `30003` (Silvia, -83789,240799,-3717), `30004` (Katerina, -84204,240403,-3720),
  `30001`/`30002` etc. (`stats/npcs/30000-30099.xml`, type="Merchant").
- `Merchant.showBuyWindow(player, listId)` → `player.sendPacket(new BuyList(...))`.
- `BUY_LIST` server opcode = 0x11. `BuyList.writeImpl`: `[0x11][money:int][listId:int][listSize:short]`
  then per item `[type1:short][objId:int=0][itemId:int][count:int][type2:short][equipped:short][body:int]
  [enchant:short][short][short][price:int]`.
- `REQUEST_BUY_ITEM` = 0x1F: `[0x1F][listId:int][n:int][{itemId:int,count:int}×n]` (`RequestBuyItem.readImpl`).
- `REQUEST_SELL_ITEM` = 0x1E: `[0x1E][listId:int][n:int][{objectId:int,itemId:int,count:int}×n]`.
- Adena item_id = 57. Bots have empty inventory (DB-created) → seed adena (fixture, like positioning).

## Implementation (`AIPlayerEngine/.../examples/TradeProbe.java`)
1. Relocate + heal CombatBot_01 next to Trader NPC `30003` (or resolve its live objectId from `NPC_INFO`).
2. Enter world; scan `NPC_INFO`(0x16) for a Trader NPC (npcType = id+1000000 → 1003003) to get its objectId.
3. `Action`(0x04) on the trader → read `BuyList`(0x11): parse `listId` + a cheap affordable item id + price.
4. Seed adena (≥ price). Send `RequestBuyItem`(0x1F)[listId][1][itemId][1].
5. Read resulting packets; verify DB (adena decreased + new item row) via the script.

## ✅/🟡 Result — B7 (2026-08-03): merchant TARGETING + full protocol traced; buy-window OPEN blocked

**Live-proven:** `TradeProbe` (single bot) logs in, enters the world, and **locates + targets a real Trader
NPC (Silvia 30003, objId seen via NPC_INFO)** — the AI can identify and interact-target a merchant.

**Protocol fully mapped (audited, SourceCode):**
- Merchant buy window opens via the `Buy <listId>` bypass → `BuyList(0x11)` → then `RequestBuyItem`(0x1F).
- Silvia (30003) sells buy-list **3000301** (`data/buylists/3000301.xml`, e.g. Spellbook item 1055).
- `BuyList.writeImpl` (0x11): `[money][listId][size:short]` + per item `[..][itemId][..][price]`.
- `RequestBuyItem`(0x1F): `[listId:int][n:int][{itemId,count}]`. Adena = item 57.

**Blocker (why no buy completed):** `RequestBypassToServer.runImpl` calls `player.validateHtmlAction(_command)`;
an unused bypass is **silently dropped (bypassOriginId==-1)** unless it came from a server-issued HTML action.
This generic "Merchant" Trader (Silvia) does **not** send an HTML menu on a plain `Action` click (no `onFirstTalk`),
so the `Buy 3000301` bypass is rejected → no `BuyList(0x11)` arrives. (Sending the buy list id requires a prior
validated HTML action.)

**Honest status:** B7 is IN PROGRESS — merchant locating/targeting done; the buy-dialog open is blocked on
obtaining a validated buy action. Next options (documented): (a) use a merchant with an `onFirstTalk` HTML
menu (quest/shop NPC that emits a Buy link) so the bypass is validated; or (b) if a test-only convenience is
acceptable, the operator can add "Buy"/non-HTML bypass exceptions. No server source changed; not claimed PROVEN.

## Reproduce (partial)
`AIPlayerEngine/.../examples/TradeProbe.java` (CD in scripts dir pending); current run: `scripts/TradeProbe` not finalized.

