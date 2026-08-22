# RuntimeLog — 2026-08-22 EB-13 — Per-bot resource / backpressure guard

## Task
EB-13 — Per-bot resource guard: rate/backpressure limits (actions per tick, queue caps).

## What was done
Surveyed the send paths: combat is already flood-protector paced (CombatFramePlanner, 1000ms
between client actions) and movement/dialog/chat have per-feature gates; `CoreWiring.send()` is
the ONE funnel every non-combat frame travels through. Added the umbrella backpressure guard:

1. **`behavior/resource/PerBotLimiter.java`** (new, pure, deterministic, thread-safe) — a
   sliding-window action budget: `tryAcquire(nowMs)` spends one slot and refuses (backpressure)
   once the window is full; `isAvailable/available` inspect without spending; `throttledCount()`
   exposes telemetry; `reset()` clears history. Defaults 20 actions/1000 ms — far above a healthy
   bot (2-5 sends/s), so it is a pure safety net, never a bottleneck.
2. **`core/CoreWiring.send()`** — the funnel now consults the per-bot limiter (one instance per
   session). When the budget is exhausted the send is DROPPED and a compact `[rate-guard]`
   (bracket-tagged, EB-12-compliant) line logs the throttle count — the driver retries next tick.
   `revive()`/potion-use stay exempt by design (life-critical: once per death / cooldown-gated),
   and `executeCombat()` keeps its 1000ms flood-protector pacing.

## Evidence / gate
- New `PerBotLimiterTest` (7): budget honored, window slides after expiry, refusals counted,
  isAvailable consumes nothing, reset frees immediately, non-positive args rejected, defaults
  healthy.
- **GATE GREEN — 509/509 tests** (was 502), style 0 (whole-tree scan; `[rate-guard]` passes),
  secret-lint clean (exit=0).
- One commit set, pushed to master.