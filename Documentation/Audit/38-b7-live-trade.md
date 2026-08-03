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

## Verification (paste both)
- Probe stdout: parsed BuyList (listId, item, price) + "buy sent".
- DB before/after: `items` for charId 2 gains the item; adena (item 57) count decreased.
- B7 PROVEN if the server processed the buy (item row added / adena dropped / ItemList/SystemMessage confirms).

## Reproduce
`scripts/b7_trade_prove.sh` (position/heal bot + seed adena → restart LS → run `TradeProbe` → assert
item row added or adena decreased in DB).
