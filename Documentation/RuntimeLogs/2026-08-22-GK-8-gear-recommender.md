# RuntimeLog — 2026-08-22 GK-8 — gear/build recommender wired into RestockPlanner

## Task
GK-8 — Gear/build recommender wired into RestockPlanner (uses items/shops/chains). Deps GK-3,
GK-5 (both DONE). Claim `1d74141b`, feat `31760085`.

## What was done
- **`knowledge/GearGuide.java`** (new, MODE: COMPLETE): best next WEAPON for (classId, level,
  adena). Grade band by level (NONE<20, D 20-39, C 40-51, B 52-60, A 61-75, S 76+); weapon line
  from the class NAME in classes.json via token table (Hawkeye→BOW, Rogue/Scavenger→DAGGER,
  Warlord→POLE, Gladiator→DUAL, Tyrant/Monk→DUALFIST, Warsmith→BLUNT, default fighter SWORD /
  mystic BLUNT); candidates from items.json filtered to buylist-sold (shops.json) with usable
  names (skips `"0"`, "Monster Only"); budget = 4/5 of the purse (ammo reserve); gear ladder
  gated at the chain's firstClass step level (chains.json — L20 on all 9 chains, data-driven).
  Ranking: primary weapon type, then price desc, then id asc — deterministic.
- **`KnowledgeBase`**: loads classes.json (classInfo/classTree) + chains.json (chainSteps) +
  shops buylist item set (`isSoldInShop`); `allItems()` for candidate scans. (Includes the
  shop-set WIP found uncommitted at session start — same task, folded in.)
- **`RestockPlanner`**: hardcoded 2375 placeholder branch REPLACED by a `GearPick` param
  overload; null pick = no gear order (old callers unchanged).
- **`BotPlayController.rungRestock`**: consults GearGuide on restock verdicts; `PlayContext`
  gains `classId` + `adena`; `BotSession.buildPlayContext` feeds real values
  (`PacketLogger.getCharSelectClassId()` / `getAdena()`). Mystic classes now size HP-potion
  orders as mystics (was hardcoded `isFighter=true`); gear orders finally fire live (coins were
  hardcoded 0 before).

## Pasted (verification, real output)
```
$ mvn -o -f AIPlayerEngine/pom.xml test
Tests run: 591, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS   (+18 vs 573)
$ scripts/gate.sh
✔ GATE GREEN — merge-ready (style 0 violations, secret lint pass)
$ jshell --class-path target/classes  (GearGuide probe, 2M adena purse)
class 0 L25 -> 129 Sword of Revolution (D SWORD, 1400000)
class 7 L25 -> 224 Maingauche (D DAGGER, 1400000)
class 9 L25 -> 279 Reinforced Long Bow (D BOW, 1400000)
class 3 L25 -> 93 Winged Spear (D POLE, 1400000)
class 48 L25 -> 261 Bich'Hwa (D DUALFIST, 1400000)
class 10 L25 -> 88 Morning Star (D BLUNT, 1400000)
class 0 L40 -> null   (C-grade starts at 2.29M > 1.6M budget — honest null)
L19 -> null; broke(5k) -> null
```
Tests: GearGuideTest 10 (property-based, no hardcoded ids), KnowledgeBaseTest +8
(classInfo/classTree/chainSteps/isSoldInShop/allItems), RestockPlannerTest 2375 assertions
rewritten for the GearPick contract.

## Problems & solutions
- First run failed `budgetLeavesAmmoReserve` (500k purse → 400k budget < 409k cheapest D shop
  sword) → test purse 600k; behavior was correct, expectation was wrong.
- `GearPick` ctor made public so the behavior-package planner test can fabricate picks.

## Next steps
- Ownership dedup: skip picks the bot already wields (needs equipped-item tracking in
  PacketLogger; noted for IN-3/LW-5 follow-up).
- Armor/jewelry lines (slot field exists in items.json) once weapon line is proven live.
