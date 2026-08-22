# RuntimeLog — 2026-08-22 EB-02 deterministic seeds (reproducible runs)

**Task:** EB-02 — every random source routed through a per-bot seeded RNG so a run reproduces
from the same inputs (same accounts + same config → same stream every launch).

## What changed
1. **`core/DeterministicRandom.java`** (new): the ONE sanctioned way to build a Random in the
   engine.
   - `forBot(accountId, source)` — stable per-bot, per-source stream (same account → same
     stream every cold start).
   - `forFleet(source)` — fixed documented base seed for fleet-global sources (singletons,
     config shuffles) → whole run reproduces.
   - `seed(context)` — JLS-stable String-based FNV-ish mixer.
2. **Wired every unseeded source** (was 14 live `new Random()`/`Math.random()`):
   - Per-bot: `net/AIPlayer` now seeds `HumanReactionSimulator` + `BehaviorSeeder` from the
     bot accountId (BotSession/ZoneRouter/RelocationPlanner/ResponseTemplate/ChatPersonality
     were already account-seeded).
   - Fleet-global: `ChatEngine`, `EconomicEngine`, `MarketEngine`, `Director.RNG`,
     `Humanization` (ImperfectionInjector/ReactionDelay/AFKModule), `HumanizedPath`,
     `BehaviorSeeder.getVariance()`, `PacketJitter`, `FleetConfig` race shuffle.
3. **Gate enforcement** (`scripts/check_style.sh`): the engine-randomness check now covers the
   CURRENT live package tree (the old list was stale/pre-EP-2) and flags BOTH `Math.random()`
   and no-arg `new Random()` → all fresh randomness must be seeded. `System.out` check re-scoped
   to plumbing packages (bracket proof-markers allowed; behavior/ pile is EB-12's tracked work).

## Tests
- New `DeterministicRandomTest` (5): same account+source → identical stream; different
  source/account → divergent; fleet seeds stable + in-range.
- Gate: **439/439 green** (was 434), style 0 violations, secret-lint clean.

## Notes
- Behavior byte-identical otherwise (same seeds constants; only the Random construction
  changed). `examples/ExampleAIPlayer` (demo) stays exempt from the determinism rule.