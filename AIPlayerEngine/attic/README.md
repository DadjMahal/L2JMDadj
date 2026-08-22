# attic/ — archived legacy code (not compiled)

Everything under `attic/` is OUTSIDE `src/`, so Maven does not compile it and the
test suite does not see it. Files were moved here by task **EP-1** of
`Documentation/UpgradePlan/AUDIT_01_ENGINE_CLEANUP.md` (2026-08-20) because no
live code path references them (verified by transitive reference closure from
FleetPlay, phase0/**, web/** and the audit keep-set; see the EP-1 RuntimeLog).

Layout:

- `engine/`   — 91 legacy `com.aiplayer.engine` classes (old Stream C–G AI
  subsystems and their helpers). 50 classes stayed in `src/` — those are
  reachable from live code.
- `examples/` — deprecated demo probes that existed only to exercise archived
  classes. First batch: FivePlayerMagicShow, GoalDrivenLoop, TenMorePlayersDemo
  (EP-1). Second batch (F-10, 2026-08-22): ChatProbe, CombatLoop, CombatProbe,
  EnterWorldProbe, InitDecodeProbe, LoginProbe, MoveProbe, NightlyProgressReport,
  PartyProbe, PvPProbe, QuestFlowLoop, QuestLoop, QuestProbe, RawInitProbe,
  TradeProbe — these exercised the `scripts/_probes/` one-shot harnesses, which
  are also quarantined. To run any such harness you'd need to resurrect the class
  first.
- `tests/`    — JUnit tests whose subjects were archived (LiveFeedbackBridgeTest).

Each file's original `package` line is kept as a `// package ...` comment.

## To resurrect a file

1. `git mv attic/engine/X.java src/main/java/com/aiplayer/engine/X.java`
2. Uncomment the `package` line at the top.
3. Fix imports/callers as needed, run `mvn -o -f AIPlayerEngine/pom.xml test`.

Do NOT resurrect wholesale: if you need behavior that lived here, prefer
reimplementing it in the current package structure (see AUDIT_01 §3).
