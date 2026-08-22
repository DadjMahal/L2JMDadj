# RuntimeLog — 2026-08-22 EB-09 — Session lifecycle API (SoulScheduler hook)

## Task
EB-09 — Session lifecycle API: spawn/connect/play/sleep/disconnect as first-class states
(SoulScheduler hook).

## What was done
Session state used to live ONLY in `AIPlayerState` — a gameplay-flavored enum (COMBAT, QUESTING,
IDLE...) that conflated "what the bot is doing" with "is the session even alive". Built a
first-class SESSION lifecycle that is independent of gameplay:

1. **`behavior/lifecycle/SessionLifecycle.java`** (new, pure/immutable/deterministic):
   - Phases: `SPAWNED → CONNECTING → PLAYING ⇄ SLEEPING`, plus terminal `DISCONNECTED`.
   - Events: `SPAWN, CONNECT_OK, CONNECT_FAIL, SOCKET_LOST, GO_SLEEP, WAKE, DISCONNECT`.
   - `transition(event, nowMs)` is copy-on-write and NO-OPs illegal transitions (never crashes);
     `canTransition`, `isActive/isPlaying/isSleeping/isTerminal...` predicates.
   - `CONNECT_FAIL → SPAWNED` (retry later), `WAKE → CONNECTING` (reconnect to resume).
2. **`net/AIPlayer` wiring** — `sessionLifecycle` field; `connectToServer` transitions into the
   socket attempt then `CONNECT_OK`/`CONNECT_FAIL` (a reconnect/wake from terminal/sleep spawns a
   FRESH session); `disconnect` → `DISCONNECTED`. Added the **SoulScheduler hooks**:
   - `requestSleep()` — PLAYING → SLEEPING (no-op otherwise)
   - `wakeUp()` — SLEEPING → CONNECTING (no-op otherwise) — LI-2 drives these.
   - `sessionPhase()` getter.
   `AIPlayerManager` spawn/login/despawn all run through connect/disconnect, so phases flow
   automatically — no manager edits needed.

## Evidence / gate
- New `SessionLifecycleTest` (10): spawn→login→playing, connect-fail→spawned, socket-loss→terminal,
  sleep→wake→reconnect, sleep-can-disconnect, invalid transitions no-op, terminal frozen (reconnect =
  fresh session), playing-can't-spawn/wake, active predicate, canTransition table.
- **GATE GREEN — 498/498 tests** (was 488), style 0 violations, secret-lint clean (exit=0).
- One commit set, pushed to master.