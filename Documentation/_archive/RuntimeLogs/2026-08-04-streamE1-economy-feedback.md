# Runtime Log — Stream E slice 1: Economy feedback + real inventory (2026-08-04)

## Goal
Stream E (Part 5, tasks 78-91) wires real social & economy behavior. **Slice 1** makes trading
inventory-aware and feeds real trade data into the economy/emotion/reinforcement systems — the
same "instantiated-but-not-driven" fix Stream D applied to goals/personality.

## What was wrong (Audit 37)
The `economy/` + `social/` singletons (`MarketEngine`, `EconomicEngine`, `NetWorthOptimizer`,
`CollectiveKnowledge`, `SwarmCoordinator`, `DiplomacyEngine`) were **instantiated in `AIPlayer` but
had no getters and were never invoked from the live path**. Worse, `MerchantAI` ran on
**`Math.random()` mock inventory** + hardcoded fake items ("COMMON_ITEM"), and `PacketLogger`'s
ItemList(0x1B) parse **never extracted adena** (item id 57), so `getAdena()` always returned 0.

## What slice 1 changed
1. **`AIPlayer` getters** for all six social/economy subsystems (Stream D pattern).
2. **`PacketLogger.parseItemList`**: now parses the full item list (32 bytes/item) and extracts the
   adena stack (item 57) + a real `inventoryItems` map. Added `getInventoryItems()`,
   `setAdena`, `setInventoryUsagePercent`.
3. **`MerchantAI`**: added `setPacketLogger()` (attach live reader); `getInventoryUsagePercentage()`
   and `getInventoryAdena()` now return REAL values (removed `Math.random()` mocks). Added trade
   outcome hooks: `recordPrice()` -> `MarketEngine`, `onTradeProfit()`/`onTradeLoss()` ->
   `EmotionalState` + `ReinforcementEngine`.
4. `SocialAI` deterministic party/chat decisions -> slice 2.

## Proof — `StreamETradeTest` (5 tests, all PASS)
```
socialAndEconomySubsystemsAreExposed     PASS   (6 getters non-null)
itemListParseExtractsAdenaAndItems       PASS   (adena=777, itemIds 57+186, counts)
merchantUsesRealInventoryForBuyDecision  PASS   (low inv + high adena -> BUY intent)
merchantSellsWhenInventoryFull           PASS   (full inv -> SELL intent)
tradeOutcomesFeedEconomyEmotionAndReinforcement PASS (MarketEngine tracks, excitement+, trade learned+1)
```
Full suite: **81/81 tests PASS (was 76/76), BUILD SUCCESS.** No regressions.

## What's NOT done yet (Stream E slices 2-3)
- `SocialAI`: replace `Math.random()` with deterministic emotion/personality-driven party/chat
  decisions; wire `SwarmCoordinator.formSwarm` + `CollectiveKnowledge.share` (tasks 80, 82-85, 90).
- Activity scheduling, reconnect/persistence, telemetry/tuning (tasks 88-90).
- Task 91 docs.
