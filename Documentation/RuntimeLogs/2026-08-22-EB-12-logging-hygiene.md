# RuntimeLog — 2026-08-22 EB-12 — Logging hygiene (java.util.logging everywhere)

## Task
EB-12 — Logging hygiene: java.util.logging everywhere, no System.out in main.

## What was done
Surveyed the whole tree: the audit-era "235 System.out in behavior/" pile is GONE (now **0** in
behavior/). Only **19** System.out remain in main, all in `examples/` (FleetPlay banner + the
deprecated-but-live MultiPlayerSession grep markers) and `core/` (FleetConfig banners +
BotSession `[EVIDENCE-H5]` proof-markers). Every one of them is (a) a line-oriented grep-able
proof marker consumed by live scripts (watchdog/analyze/proofs), or (b) in examples/.

1. **`core/Logging.java`** (new) — the engine's single JUL convention: `getLogger(Class)` returns
   a `java.util.logging.Logger` named `com.aiplayer.<pkg>.<Class>` with a compact single-line
   `CompactFormatter` (timestamp level [ShortSource] message, flush-on-publish). Everything
   non-marker must use a JUL logger from here.
2. **`scripts/check_style.sh`** — the System.out scan was **plumbing-only** (protocol/net/web/
   monitor/metrics/core/cli/learning/knowledge); a stray `System.out` in behavior/ etc. would
   sail through. **Widened to the WHOLE com.aiplayer tree** (same `$ENGINE_PKGS` set as the
   RNG check, examples/ exempt by design), keeping the bracket-tag exemption — so
   "no System.out in main except grep-able proof markers" is now ENFORCED everywhere.
   Comment updated: EB-12 resolved; behavior pile gone.
3. **`MultiPlayerSession`** — left on System.out deliberately: its `[MP]`/`[MP-SUM]` markers are
   grep-consumed by `scripts/analyze_ai_run.sh` / `watchdog_ai_run.sh` on raw stdout (a JUL
   formatter would prefix `INFO:` and BREAK the reports). Documented exemption, not a conversion.

## Evidence / gate
- New `LoggingTest` (4): JUL logger named after class + level + handler; same instance across
  calls; CompactFormatter line = timestamp+INFO+[LoggingTest]+message+\n; null-source -> [-].
- Widened style check still 0 violations (partial proof: all 19 System.out are markers/exempt).
- **GATE GREEN — 502/502 tests** (was 498), style 0, secret-lint clean (exit=0).
- One commit set, pushed to master.