# RuntimeLog — 2026-08-22 EB-10 — Graceful fleet shutdown + resume (feeds keep_alive.sh)

## Task
EB-10 — Graceful fleet shutdown + resume: drains bots on stop, safe restart (feeds keep_alive.sh).

## What was done
FleetPlay.main previously spawned the bot threads and blocked forever in `FleetPlay.class.wait()` —
a SIGTERM/SIGINT killed the JVM instantly with NO drain (bots mid-action) and keep_alive.sh
relaunched blindly. Ships the graceful stop + visible resume:

1. **`behavior/lifecycle/FleetShutdown.java`** (new, pure) — drain state machine
   `IDLE → DRAINING → DRAINED`, plus `CRASHED`; idempotent `requestDrain(reason)`,
   `completeDrain()`, `noteCrash(reason)`; `resumeFully()` true only when DRAINED or IDLE;
   `exitLabel()` (GRACEFUL/CRASHED/DRAINING/NONE) + `fromLabel()` for marker round-trip
   (a found `DRAINING` marker ⇒ process died mid-drain ⇒ next boot treats as CRASHED).
2. **`behavior/lifecycle/FleetDrain.java`** (new, small) — the drain executor: interrupts each
   live bot thread (BotSession.run() checks `Thread.interrupted()` each outer loop), awaits
   termination within a bounded budget, returns the drained count.
3. **`examples/FleetPlay`** — captures the spawn threads; on boot reads + clears the exit
   marker (`/tmp/l24lude_fleet_last_exit`, never committed) and logs "last exit: GRACEFUL —
   resuming fully" / "guarded resume"; installs a JVM **shutdown hook** that writes a DRAINING
   marker first, drains the threads, then writes a GRACEFUL marker so the next boot respects it.
   CAUGHT + FIXED during editing: a marker-helper insert landed INSIDE `spawnFleet` (would not
   compile) — relocated to class level; the gate compiles + proves it.
4. **`scripts/keep_alive.sh`** — before relaunching a downed fleet it now reads the marker and
   logs the last exit (GRACEFUL vs CRASHED vs no-marker), feeding the operator a clean signal.

## Evidence / gate
- New `FleetShutdownTest` (6) + `FleetDrainTest` (3) — idempotent drain, complete→GRACEFUL,
  crash→guarded, unfinished-drain=crash, marker round-trips, drain stops cooperative workers,
  deadline-bounded, null-safe.
- **GATE GREEN — 534/534 tests** (was 525), style 0, secret-lint clean, `bash -n keep_alive.sh` OK.
- One commit set, pushed to master.