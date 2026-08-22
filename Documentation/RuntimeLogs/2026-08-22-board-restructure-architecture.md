# RuntimeLog — 2026-08-22 board-restructure + architecture docs

**Task:** F-01 + F-02 (board restructure) — the board becomes a 100-task master plan, root
facts get a canonical home.

## What changed
1. **`Architecture.md`** (new, repo root) — the core basis: repo anatomy, three-plane
   integration model (PlayerAPI / ControlPlane / vanilla core + ServerBridge), engine internals
   (tick loop, goal ladder), datastore table, dev invariants, phase roadmap P0–P9.
2. **`START_HERE.md`** — new §0.5 "core basis" (five bullets AIs must internalize) + Doc map
   row for `Architecture.md`.
3. **`Documentation/TASKS.md`** — replaced 5-row board with **exactly 100 tasks** across 10
   phases (P0 foundations, P1 quest, P2 engine, P3 knowledge, P4 intelligence, P5 observability,
   P6 integration, P7 living server, P8 content, P9 scale), each with Size · Priority · Dep ·
   Status. Preserved live rows: S3-T02 `IN_PROGRESS (play-builder)`, S3-T03 `BLOCKED`.
4. **Archive** — old board moved to
   `Documentation/_archive/TASKS-2026-08-22-pre-100board.md` (git mv, history kept);
   `_ARCHIVE_INDEX.md` registers it.
5. GK-1/GK-2/GK-3/GK-4/GK-5, LW-1…LW-5, IN-1…IN-6, RS/RES-*, BR-*, DA-*, LI-*, CO-*
   audit tasks are preserved 1:1 inside phases; new tasks fill the gaps to 100.

## Verification
```bash
grep -oE '^\| (F|S3|EB|GK|IN|LW|BR|DA|LI|EN|CO|RS)-[0-9A-Z]+' Documentation/TASKS.md | wc -l  # 100
# duplicate row IDs: 0
mvn -o -f AIPlayerEngine/pom.xml test  # all green (see gate output below)
```

## Notes
- Docs-only change; no `SourceCode/`/`ServerBuild/` touched, no Java modified.
- F-01/F-02 marked `DONE (2026-08-22 restructure)` on the board (this commit).