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

## ✅ Result — B7 PROVEN (2026-08-03) — genuine buy, DB-verified, no server source changed

> **Correction of the earlier "blocker":** the prior analysis claimed Silvia "does not send an HTML menu on a
> plain click" and that the bypass could not be validated. This was a **misdiagnosis**. Silvia IS a `Merchant`
> (`Merchant extends Folk`, shop HTML `data/html/merchant/30003.htm`) and DOES open its menu on click. The real
> requirements that were missing:
> 1. **Two `Action`(0x04) clicks** — `NpcClick.onAction` only TARGETS on the 1st click; the dialog opens on the
>    2nd click (when the NPC is already the target). A single click never opens the HTML.
> 2. **The full, server-cached bypass** `npc_<objId>_Buy <listId>` (NOT `Buy <listId>`). The HTML contains
>    `bypass -h npc_268439739_Buy 3000300`; `HtmlUtil.buildHtmlBypassCache` strips the `-h ` and caches exactly
>    `npc_268439739_Buy 3000300`. `RequestBypassToServer.validateHtmlAction` exact-matches it, so the client must
>    send that exact string (which the probe now extracts verbatim from the received `NpcHtmlMessage` 0x0F).
> 3. The bot must be within `Npc.INTERACTION_DISTANCE` (250) — it is at Silvia's spawn.

**End-to-end live flow (TradeProbe, single bot):**
1. Located Silvia (30003, objId 268439739) from `NPC_INFO`.
2. 2× `Action`(0x04) → server sent `NpcHtmlMessage`(0x0F) with `data/html/merchant/30003.htm` (Buy links).
3. Extracted validated bypass `npc_268439739_Buy 3000300` → sent `RequestBypassToServer`(0x21).
4. Server → `BuyList`(0x11): money=500000, listId=3000300, 13 items.
5. Picked itemId **118, price 75** → sent `RequestBuyItem`(0x1F) `[listId=3000300][1][itemId=118][count=1]`.

**DB proof (`gameserver.items`, owner_id 2 / CombatBot_01), two consecutive runs:**
- Adena (item 57): **500000 → 499925 → 499850** (each run −75, exactly item 118's price).
- Item 118: **two NEW rows added** (one per run, count 1 each, INVENTORY).

**B7 ══ PROVEN**: the server processed the AI's buy — deducted adena and added the item to the bot's inventory.

## Reproduce
```bash
# 1) point CombatBot_01 at Silvia (within 250), 2) run the probe:
sudo mysql -u root gameserver -e "UPDATE characters SET x=-83789,y=240799,z=-3717,online=0 WHERE char_name='CombatBot_01';"
cd /home/volodro/L2JM/AIPlayerEngine && mvn compile
nohup timeout 60 bash -c 'java -cp target/classes com.aiplayer.examples.TradeProbe ai_combat_01 ai123pass 127.0.0.1 7777' > /tmp/trade_probe.out 2>&1 &
# verify: adena(57) -= item price AND a new item row appears:
sudo mysql -u root gameserver -N -e "SELECT item_id,count FROM items WHERE owner_id=2 AND item_id IN (57,118) ORDER BY item_id;"
```
`scripts/b7_trade_prove.sh` automates position + run + DB assert.

