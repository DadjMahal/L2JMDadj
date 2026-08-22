# RuntimeLog — 2026-08-22 F-04 root README header

**Task:** F-04 — root `README.md`: project header + pointer to `START_HERE`/`Architecture`
(content stays candid).

## What changed
1. Added header `# L24Lude — Living Server for L2JMobius (Interlude)` with the agent-reading
   order: `START_HERE.md` → `Architecture.md` → `WORKFLOW.md` → `TASKS.md` (100-task board).
2. Kept the owner's candid note (7 lines) **verbatim** under "Owner's note (kept)".
3. Replaced the old dashboard blockquote (its agent-pointer role now lives in the header)
   with "dashboard/API face for human operators".
4. "Architecture (one line)" gains a **Full picture** pointer: `Architecture.md` ·
   `AIPlayerEngine/README.md` · `Documentation/SOURCE_CODE_MAP.md`.

## Verification
```bash
mvn -o -f AIPlayerEngine/pom.xml test   # all green (gate; docs-only change)
```
No `SourceCode/`/`ServerBuild/` touched; root README only. Commit: `f3982ad9`.