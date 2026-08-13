# Audit 37 — Social & Economy Systems (Stream E, tasks 78-87)

> Audit performed 2026-08-04 before Stream E implementation. Method: grep callers + read each class.

## Scope
Audited the Part 5 classes the task board references:
`economy/MarketEngine`, `economy/EconomicEngine`, `economy/NetWorthOptimizer`,
`social/CollectiveKnowledge`, `social/SwarmCoordinator`, `social/DiplomacyEngine`,
plus the live decision classes `engine/MerchantAI` and `engine/SocialAI`.

## Verdict: same "instantiated-but-not-driven" pattern as Stream D
Every economy/social singleton was **constructed inside `AIPlayer`'s constructor** (compiled,
existed as fields) but their **behavioural methods were never invoked from the live path**, and
`AIPlayer` exposed **no getters** for any of them (only `getMerchantAI()` existed).

| Class | Constructed | Getters before | Live-path callers | Verdict |
|---|---|---|---|---|
| `MarketEngine` | `AIPlayer` ctor | NO | 0 callers of `recordPrice`/`shouldBuy` | dead; no prices ever recorded |
| `EconomicEngine` | `AIPlayer` ctor | NO | 0 callers of `scanArbitrage`/`assessTradeRisk` | dead; never fed prices |
| `NetWorthOptimizer` | `AIPlayer` ctor | NO | 0 callers | dead |
| `CollectiveKnowledge` | `AIPlayer` ctor | NO | 0 callers of `share`/`bestInCategory` | dead |
| `SwarmCoordinator` | `AIPlayer` ctor | NO | 0 callers of `formSwarm`/`assignRoles` | dead |
| `DiplomacyEngine` | `AIPlayer` ctor | NO | 0 callers | dead |
| `MerchantAI` | `AIPlayer` ctor | `getMerchantAI()` | **MOCK**: `getInventoryUsagePercentage()` = `50 + Math.random()*30`; `findItemToBuy/Sell` return hardcoded "COMMON_ITEM"/"BASIC_SUPPLY" | live decision class but runs on mock data + random |
| `SocialAI` | `AIPlayer` ctor | `getSocialAI()` | **MOCK**: `shouldSeekParty/shouldSeekClan/shouldChat` = `Math.random() < prob`; fake "NEARBY_PLAYER" targets | live decision class but random/fake |

## Root finding
1. The economy/social **singletons were never reachable** — no getters, so `MerchantAI`/`SocialAI`
   couldn't call `MarketEngine.recordPrice()` or `CollectiveKnowledge.share()` even if they wanted to.
2. `PacketLogger` tracked `adena`/`inventoryUsagePercent` fields, but its **ItemList(0x1B) parse only
   counted items** — it never extracted the adena stack (item id 57), so `getAdena()` always returned 0.
3. `MerchantAI` and `SocialAI` used **`Math.random()`** and **hardcoded fake item/target strings** for
   their decisions (exactly the "still runs on mock data" flag in START_HERE).

## Stream E slice 1 fix (this commit)
1. **`AIPlayer` getters** for all six social/economy subsystems (mirrors Stream D).
2. **`PacketLogger.parseItemList`** now parses the full item list (32 bytes/item) and extracts the
   adena stack (item id 57) into `adena`, plus keeps a real `inventoryItems` map (itemId->count).
   Added `getInventoryItems()`, `setAdena`, `setInventoryUsagePercent` test/telemetry hooks.
3. **`MerchantAI`**: `setPacketLogger()` to attach the live shared logger; `getInventoryUsagePercentage()`
   and `getInventoryAdena()` now return REAL values from PacketLogger (removed the `Math.random()` mocks).
   Added trade outcome hooks `recordPrice()` (feeds `MarketEngine`), `onTradeProfit()`/`onTradeLoss()`
   (feed `EmotionalState` + `ReinforcementEngine`/`AdaptiveLearner`).
4. **`SocialAI`** — see slice 2 (deterministic party/chat decisions).

## Proof — `StreamETradeTest` (5 tests, all PASS)
- social/economy subsystems exposed via AIPlayer getters
- ItemList parse extracts adena (777 from the last adena stack) + 2 distinct item ids + counts
- merchant BUY intent on low-inventory + high adena
- merchant SELL intent on full inventory
- trade outcomes feed MarketEngine (trackedItemCount, bestSellTown) + emotion (excitement) +
  reinforcement (learned trade action)

**81/81 tests PASS (was 76/76), BUILD SUCCESS.** No regressions.

## Remaining (Stream E slices 2-3)
- `SocialAI`: replace `Math.random()` shouldSeekParty/shouldChat with deterministic
  emotion/personality state; wire `SwarmCoordinator.formSwarm` for party formation and
  `CollectiveKnowledge.share` from real outcomes (tasks 80, 82-85, 90).
- Activity scheduling, reconnect/persistence, telemetry/tuning (tasks 88-90).
- Task 91 docs.
