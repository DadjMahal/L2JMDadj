# RuntimeLog — 2026-08-22 F-03 engine README sync

**Task:** F-03 — sync `AIPlayerEngine/README.md` package diagram + link root `Architecture.md`.

## What changed
1. Added "Core basis" banner linking root `../Architecture.md` (the three-plane model lives
   there now; the README stays the engine-level detail).
2. Fixed stale monitoring line: `EventRing`/`HistoryRing`/`FleetMetrics` live in **`web/`**
   (not `metrics/`); `metrics/` holds `PerformanceMetrics`; `monitor/` = AIMonitorDashboard +
   AILogCollector.
3. Noted the standalone headless entry `cli/AIPlayerEngine`.
4. Added `../Architecture.md` to the "Learn more" section.
- Package tree verified live (218 files): `behavior/` 149 (10 subpackages: combat 26, social 21,
  town 15, quest 15, movement 12, party 7, inventory 7, lifecycle 6, hunting 6, humanize 4),
  `examples/` 19, `core/` 14, `protocol/` 13, `learning/` 6, `web/` 5, `knowledge/` 5, `net/` 3,
  `monitor/` 2, `metrics/` 1, `cli/` 1 — matches the diagram/notes now.

## Verification
```bash
mvn -o -f AIPlayerEngine/pom.xml test   # all green (gate, see output)
```
No `SourceCode/`/`ServerBuild/` touched; docs-only change. Commit: `38aefc90`.