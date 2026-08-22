# RuntimeLog — 2026-08-22 EB-05 humanization pack audit

**Task:** EB-05 — Humanization + imperfection params audited, wired to feedback (no dead knobs).
Commit `fb11f4ce`.

## Audit findings (verified)
- HumanizedRandom's no-arg forms used `ThreadLocalRandom` (non-deterministic) — EB-02 had not
  covered them.
- The humanize knobs were **mostly dead in the live path**: `BotBrain` is constructed nowhere in
  main, `AntiDetectionEngine`/`LevelingPlanner`/`QuestExecutor` only in tests, and
  `HumanReactionSimulator` (wired in AIPlayer) was never READ — only a test called getHumanDelay().

## What changed
1. **Humanization**: added one deterministic **advancing** fleet stream (`FLEET_STREAM`, fixed
   seed) for all no-arg distribution helpers — reproducible across runs (EB-02) but still varied
   per draw (each call advances), unlike a frozen per-call constant. Removed the
   `ThreadLocalRandom` usage + import. The `Random`-taking overloads stay for per-bot streams.
2. **BotSession**: per-bot `HumanReactionSimulator` (deterministic, account-seeded) + the tick
   sleep now adds `getHumanDelay()` — the LIVE loop consumes the humanize reaction knob every
   tick (~150–600 ms jitter on a ~300 ms tick), which closes the biggest dead-knob gap without
   touching packet timings.

## Tests
- `HumanizedRandomTest` +4: no-arg normal finite/near-mean; draws vary (advancing stream);
  poisson/reactionTime sane bounds.
- `HumanReactionSimulatorTest` (new, 3): same seed reproduces same delays; delay bounded
  150–600 ms (never turns a tick into a stall); stutter chance low at low ping.
- Gate: **458/458 green** (was 451), style 0 violations, secret-lint clean.

## Notes
- `BotBrain`/AntiDetectionEngine/LevelingPlanner remain not-yet-wired-in-main (their revival is
  a quest/behavior-arc task, EB-06/IN-*); this task makes the humanize core deterministic and
  gives the reaction knob a real live consumer.