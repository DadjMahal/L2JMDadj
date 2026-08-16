# 2026-08-15 Task 0005 — P0: gear the farm char (CombatBot_01) + verify kills/XP

> **P0 goal (from follow-ups):** raise CombatBot_01 to ~L20, equip proper weapon/armor via DB,
> and re-run to confirm kills/XP rate.
>
> **Result: DONE — with two root causes found that the original hypothesis missed.**
> The real blocker for "kills impossible" was never the loadout power — it was that **no equipped
> item ever loaded in-game** (two distinct server-side data defects, fixed below). Once fixed,
> kills + XP flow at *Level 5* with one shot per mob. Raising to L20 was, counter-productively,
> *worse* (level-gap penalty zeroes XP in this zone).

## 1. Root causes found & fixed (both apply to ALL 5 bots)

| # | Bug | Detail | Fix |
|---|---|---|---|
| RC1 | **items.loc was lowercase `paperdoll` → item restore silently failed for every character** | `Item.restoreFromDb` does `ItemLocation.valueOf(rs.getString("loc"))` — Java enum case-sensitive (`PAPERDOLL`). All 10 `items` rows in `gameserver` had `loc='paperdoll'` → `IllegalArgumentException` per row → row dropped → **every char logged in with an EMPTY inventory**. GS log: `SEVERE … Item: Could not restore an item owned by 100000 from DB:` (5-6 lines per login). The "Short Sword / 4 dmg" reports were actually **bare-fist damage** — the weapon never existed in-game. | `UPDATE items SET loc='PAPERDOLL' WHERE loc='paperdoll';` (applied globally) |
| RC2 | **`mana_left=0` marks an item as a *fully-expired shadow item*** | `Item.isShadowItem() ⟺ _mana >= 0`. Inserting new items with `mana_left=0` → equip flow starts the 60s shadow tick → `sysmsg#1982` "$s1's remaining Mana is now 0, and the item has disappeared." → the 5 armor pieces were destroyed in the first minute and DELETED from DB on next save. | Insert/keep regular items with `mana_left = -1` |

Relevant code: `SourceCode/java/org/l2jmobius/gameserver/model/item/enums/ItemLocation.java`,
`.../item/instance/Item.java` (`restoreFromDb:1312`, `isShadowItem:1067`), `Inventory.restore:1465`,
`ItemManaTaskManager` (60s tick), `EnterWorld:474`.

## 2. Gear applied to `characters.charId=100000` (items, all `loc=PAPERDOLL`)

| item_id | item | slot | note |
|---|---|---|---|
| 79 | Sword of Damascus (pAtk **194**, grade B, one-hand) | 7 (rhand) | replaced the Long Sword (24) that never loaded |
| 44 | Leather Helmet | 6 (head) | |
| 18 | Leather Shield | 8 (lhand) | |
| 50 | Leather Gloves | 9 | |
| 24 | Bone Breastplate | 10 (chest) | |
| 40 | Leather Boots | 12 | |

All rows: `count=1, enchant=0, mana_left=-1, time=0`. **Verified:** `ITEM_LIST itemCount=6 equipped=6`
on login, and all 6 rows survive disconnect+reload (no 1982 messages).

## 3. Level chosen = **5** (not 20) — farm-zone math

Farm field (`gludio32_1725_03` @ (-82759,250149,-3600)) mobs:
- Bearded Keltir 20481 — **Lv1**, `accept exp=35`
- Elder Keltir 20544 — **Lv3**, `exp=105`
- Wolf 20120 — **Lv4**, `exp=141`
- Wolf 12077 — **Lv15 Pet**, no `exp`

Lv20/22 char (1.4M exp) vs Lv1-4 mobs → hard level-gap penalty → **0 XP** (verified: 13+ hits,
multiple kills, DB exp frozen). Lower = XP flows. Baseline restored to the Task-0005 level:
`level=5, exp=4732`; the Damascus one-shots (62-90 HP mobs) so the old "died at 12/107 HP" problem
is gone without needing L20.

## 4. Re-run evidence (`/tmp/fleet-run6.log`, `/tmp/fleet-run7.log`)

- Hits: `sysmsg#35` damage 418 → 977 (one-shot).
- Kills: multiple targets with `CUR_HP=0` StatusUpdates (62&90-HP mobs).
- XP accrual in-game: `EVIDENCE-H5 EXP +8/+23 …` chain, e.g. `+23 (now 4755, level=5)`.
- **DB flush check:** after ~2 min: `exp 4732 → 4824` (**+92 XP persisted**), items 6/6 intact.

## 5. Instructions for P2 (fresh single-bot run, XP/min + reconnect)

- Farm bot currently running: `FleetPlay 1 … movement` (PID per pgrep), telemetry in
  `/tmp/fleet-run7.log` (restart line before the measurement for a clean window).
- Char now: Lv5, exp grows from 4824, equipped Damascus+leather set.
- Expected rate at L5 ≈ 23 XP per Elder-Keltir kill (level-charge). For a long measurement use
  the EVIDENCE-H5 EXP deltas over a ≥10 min window; P1 (far-point `destZ`) still governs how much
  time is spent fighting vs wandering.
- All 5 bots' items now restore (global `loc` fix); other bots are still L5 with a Long Sword if a
  full 5-bot fleet re-run is wanted, re-apply the same gear rows (copy `item=79 …`) per bot.