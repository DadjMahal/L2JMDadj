# Social & Economy System — Stream E (task 91)

> Consolidated doc for the AI player social & economy system. Implements `TASKS.md` Part 5
> (tasks 78-91). Status: 14/14 tasks done.

## 1. Overview
After Stream D made bots pursue goals with personality/emotion, Stream E wired the **economy**
(real inventory-aware buy/sell, price arbitrage) and **social behavior** (party formation, clan,
contextual chat) — and removed the last `Math.random()` mocks from the decision classes. The two
pipelines:

```
ECONOMY:  live ItemList(0x1B) -> PacketLogger (adena+items) -> MerchantAI.makeDecision()
              -> buy/sell on real inventory -> outcome hooks -> MarketEngine + Reinforcement + Emotion

SOCIAL:   personality (socialWeight) + emotion (bored) + nearby entities -> SocialAI.makeDecision()
              -> party/chat deterministically -> outcome hooks -> SwarmCoordinator + CollectiveKnowledge + Emotion
```

## 2. Economy components

### 2.1 Inventory parsing — `protocol/PacketLogger.parseItemList` (task 78)
Before Stream E the ItemList(0x1B) parse **only counted items** and never extracted adena. Now it
parses each item (32 bytes: type1/objId/itemId/count/type2/coobj/bodypart/enchant/c1/c2) and:
- extracts the adena stack (itemId 57) into `adena`;
- keeps a real `inventoryItems` map (itemId -> count) exposed via `getInventoryItems()`.

### 2.2 Merchant decisions — `engine/MerchantAI` (tasks 78, 79)
`setPacketLogger()` attaches the live reader's logger (was a private empty one).
`getInventoryUsagePercentage()` and `getInventoryAdena()` now return REAL values (removed
`50+Math.random()*30` and `10000+Math.random()*50000` mocks). Decisions: full inventory -> SELL;
low inventory + adena -> BUY; adena < emergency -> EMERGENCY_SELL.

### 2.3 Market intelligence — `economy/MarketEngine` / `EconomicEngine` / `NetWorthOptimizer` (tasks 79, 86, 87)
`MerchantAI.recordPrice(itemId, town, buy, sell)` feeds `MarketEngine` (recordPrice /
findBestSellTown / shouldBuy / predictTrend). `EconomicEngine.scanArbitrage` detects cross-town
price gaps; `NetWorthOptimizer` handles after-tax profit + net worth. Before Stream E all three
were instantiated in AIPlayer with **no getters and 0 callers**.

## 3. Social components

### 3.1 Deterministic decisions — `engine/SocialAI` (tasks 80, 81, 82)
Removed all `Math.random()` from the social decision path:
- `shouldSeekParty()`: SOCIAL personality (socialWeight>1.5) OR bored + a nearby non-hostile
  candidate + not in combat -> seek party.
- `shouldSeekClan()`: socialWeight > 1.2.
- `shouldChat()`: socialWeight>1.3 OR bored, and not in combat.
- `seekParty()`: targets a REAL nearby non-hostile entity (`objId=..`) via
  `PacketLogger.getNearbyEntities()` instead of fake "NEARBY_PLAYER".
- `seekClan()`: applies to `<name>-guild` instead of fake "NOVICE_CLAN".
- `generateChat()`: contextual message (bored / confident / neutral) instead of a random pick.

### 3.2 Swarm + collective knowledge — `social/SwarmCoordinator` / `CollectiveKnowledge` / `DiplomacyEngine` (tasks 80, 84, 85)
`onPartyJoined(partyId)` now: sets party state, **forms a swarm** in `SwarmCoordinator`
(auto leader = highest level), and **shares into `CollectiveKnowledge`**
(`share(category, key, value, rating)`). `DiplomacyEngine` is exposed for inter-group relations /
treaties. Before Stream E all three were instantiated with **no getters and 0 callers**.

## 4. Scheduling, reconnect, persistence (tasks 88, 89)

### 4.1 ActivityScheduler (task 88)
New `engine/ActivityScheduler`: rotates activities (GRIND/MERCHANT/QUEST/SOCIAL/REST) on
per-player intervals with deterministic jitter (from accountId + ordinal, so bots drift off the
same cadence). `nextActivity()` is **goal-aware** — it consults `GoalTree.getActiveGoal()` and
prefers the activity matching the current goal, falling back to any due activity (GRIND priority).
`markDone(a)` reschedules; `isDue(a)` checks the interval.

### 4.2 Reconnect (task 89)
`AIPlayer.reconnect()` reuses credentials stored at `connectToServer()` time, with a **3-retry
bound** and a **3s cooldown** (so a freshly-dropped bot doesn't hammer the server).
`disconnect()` records the disconnect time. Returns false gracefully if no credentials stored.

### 4.3 Persistence (task 89)
`AIPlayer.saveSessionState()` / `loadSessionState()` use the (previously dead) `PersistenceManager`
to save/restore level, position, and long-term goal progress across restarts, so a bot resumes
where it left off instead of restarting from level 1.

## 5. Outcome hooks (the wiring)
| Event | Hook | Drives |
|---|---|---|
| Price observed | `MerchantAI.recordPrice(item, town, buy, sell)` | MarketEngine price history |
| Profitable trade | `MerchantAI.onTradeProfit(item, action, profit)` | EmotionalState.onProfitableTrade, ReinforcementEngine.rewardTrade |
| Losing trade | `MerchantAI.onTradeLoss(item, action, loss)` | ReinforcementEngine.rewardTrade (negative) |
| Party joined | `SocialAI.onPartyJoined(partyId)` | partyState, SwarmCoordinator.formSwarm, CollectiveKnowledge.share, emotion decay |
| Party left | `SocialAI.onPartyLeft(partyId)` | partyState.leaveParty |

## 6. Verification
- `StreamETradeTest` (5): subsystems exposed; ItemList extracts adena+items; merchant BUY/SELL on
  real inventory; trade outcomes feed MarketEngine + emotion + reinforcement.
- `StreamESocialTest` (5): SOCIAL seeks party deterministically; non-social doesn't; onPartyJoined
  drives swarm + collective; chat deterministic + contextual.
- `StreamESchedulerTest` (6): scheduler due/markDone; goal-aware nextActivity; reconnect
  credentials/cooldown/bounds; session state persists across save/load.
- Full suite **92/92 PASS, BUILD SUCCESS.**

## 7. Open / future work
- Call these trade/party hooks from the LIVE driver (TradeProbe B7 path / PartyProbe) on real
  packet outcomes — unit tests prove the chain; a live run proves real-server events drive it.
- Have the live loop consult `activityScheduler.nextActivity()` to rotate behavior.
- Wire `DeepLearningCore.predict()` into merchant/social "which option" selection (currently fed
  but not consulted, same as Stream D).

