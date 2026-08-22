# RuntimeLog — 2026-08-22 EB-06 restock decision module

**Task:** EB-06 — Restock/Inventory planner as pure decision module (behavior/restock) feeding
RestockPlanner. Commit `d81ae488`.

## What changed
1. **`behavior/restock/RestockDecider.java`** (new, pure, deterministic): restock INTENT decision
   from real consumable counts (not just the binary inventoryPct gate):
   - `decide(soulshotCount, hpPotionCount, inventoryPct[, soulshotAt, hpAt, fullAt])` →
     `Reason` NONE / SOULSHOTS / POTIONS / FULL / URGENT (full+both → urgent) + exact top-off
     shortages (`soulshotShort` / `hpPotionShort`).
   - `shortage(current, target)` — never-negative top-off quantity.
   - Unknown inputs (-1) are treated as "NOT low/full" — never invent a trip from nothing.
2. **`RestockPlanner`** — new shortage-aware overload `plan(level, invPct, coins, race, isFighter,
   soulshotShort, hpShort)`; a 0/0 shortage returns exactly the historical base plan (no
   regression). Has the fighter/class-aware base intact.
3. **Ladder `rungRestock`** now feeds `RestockDecider → RestockPlanner` (the pure decision the
   profile's restockThreshold knob drives; ammo/potion low-marks from the decider's defaults).
4. **`PlayContext`** gains `soulshotCount` / `hpPotionCount` (old constructor defaults -1 = unknown,
   so all existing call sites are source-compatible). **BotSession** fills them from the live
   logger's inventory records (Soulshot itemId 1835 + Healing Potion 1061).

## Tests
- New `RestockDeciderTest` (8): well-stocked/unknown → NONE; low shots → SOULSHOTS w/ top-off;
  low potions → POTIONS; empty → full target; full bag → FULL; full+low ammo → URGENT; shortage
  math; custom (merchant-early) thresholds.
- `RestockPlannerTest` +2: shortage-aware plan tops off exact qty; 0-shortage == historical plan.
- Gate: **469/469 green** (was 458), style 0 violations, secret-lint clean.