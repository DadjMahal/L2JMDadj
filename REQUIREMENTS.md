# REQUIREMENTS

> **Read `START_HERE.md` first** for orientation, and `Documentation/WORKFLOW.md` for all rules.
> This file keeps project-specific development requirements.

## 1. Startup
1. Read `START_HERE.md` (orient; resume check if `SESSION_IN_PROGRESS.md` exists).
2. Read `Documentation/WORKFLOW.md` (rules + session workflow + resumability).
3. Read ONLY the specific `Documentation/Audit/*.md` your task touches (audit-first); **do not read all docs**.
4. Check if LoginServer/GameServer are running (reuse; never start duplicates).
5. Clear server cache / free memory only if a restart is needed.

### Resuming the source-code audit
If `Documentation/Audit/PROGRESS.md` lists an iteration `pending`/`in_progress`, continue that audit unless the user requests otherwise. Follow PROGRESS.md's resume protocol.

## 2. Development
- Work only inside this project. Modify source only where intended by the architecture.
- No manual runtime fixes that should come from source. If a rebuild is required, rebuild and deploy into the runtime dir.
- Never leave the project in a broken state.

## 3. Validation (before finishing every task)
- No startup exceptions; GameServer + LoginServer start; "Server loaded" in logs; GameServer registers on LoginServer; runtime matches expected state.
- If validation fails, fix it or clearly document why it can't be resolved.

## 4. Documentation & runtime logs
- Keep docs short, accurate, useful. Update outdated files. Never delete — quarantine to `_archive_*`.
- After every prompt create `Documentation/RuntimeLogs/<timestamp>-<task>.md` (prompt, objective, files, problems, solutions, remaining issues, summary, next steps; ≤70 lines).
- **Milestone doc-sync (MANDATORY at every milestone, even mid-session):** update `START_HERE.md`,
  `STATUS.md`, `Documentation/SESSION_HANDOFF.md`, the `TASKS.md` board, and
  `AIPlayerEngine/AIStatusLogs/ai_progress_report.txt`, then git-commit — BEFORE the turn/session ends,
  so a fresh session never starts from stale context. Never defer knowledge-base updates to session end.

## 5. General
- Minimize token usage. Prefer simple, clean solutions. Trust docs before scanning source; update stale docs immediately. Leave the repo cleaner than you found it.
