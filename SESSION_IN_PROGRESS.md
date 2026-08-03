# SESSION IN PROGRESS — B7: live trade proof

> Resume check: if this file exists, the last turn was cut off. Resume "Current step". On clean completion
> fold into the final RuntimeLog + `git rm` this file.

## Goal
Prove an AI bot buys an item from a merchant NPC live: target Trader → `BuyList`(0x11) → `RequestBuyItem`(0x1F)
→ DB (item row added / adena dropped). Spec: `Audit/38-b7-live-trade.md`.

## Idempotent checklist
- [x] Audit-first: BuyList(0x11) layout; RequestBuyItem(0x1F)/RequestSellItem(0x1E); Trader NPCs 30003/30004
      (+ locations); Merchant.showBuyWindow; adena=item 57; bots have empty inventory.
- [x] Document before code: wrote `Audit/38`.
- [ ] DB: position+heal CombatBot_01 next to Trader 30003; seed adena (item 57).
- [ ] Implement `TradeProbe.java` (enter-world → NPC_INFO Trader objId → Action → parse BuyList → RequestBuyItem).
- [ ] `mvn clean compile` → BUILD SUCCESS; write `scripts/b7_trade_prove.sh`.
- [ ] Run live; paste BuyList parse + DB before/after.
- [ ] RuntimeLog + sync KB (START_HERE, STATUS, SESSION_HANDOFF, ai_progress_report); `git rm` this; commit.

## Current step
Audit + spec done. Next: seed adena + position bot, then code `TradeProbe`.

## If resuming: do this next
1. DB: UPDATE CombatBot_01 to Trader 30003 location (-83789,240799,-3717) + heal; INSERT adena (item 57,
   owner_id 2, loc='INVENTORY', count e.g. 500000, with a free object_id).
2. Finish `TradeProbe.java`: enter world → find npcType 1003003 in NPC_INFO → Action → parse BuyList(0x11)
   listId + first item/price → RequestBuyItem(0x1F). Build, restart LS, run via setsid; verify DB.
