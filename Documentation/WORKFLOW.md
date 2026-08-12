# 📋 Workflow — L2JM (single rules reference)

> Merged 2026-08-02 from `SESSION_PROTOCOL.md` + `MULTI_AGENT_RULES.md` + `WORKFLOW_RULES.md`
> (those now live in `Documentation/_archive_superseded/`). This is the ONE rules doc.
> Orientation lives in `START_HERE.md`; the task board is `Documentation/TASKS.md`.

## 1. Session Startup (read in this order)
1. `START_HERE.md` (orient; ~800 tokens). **If `SESSION_IN_PROGRESS.md` exists → resume it first** (rate-limited mid-work).
2. `AGENT_ONBOARDING.md` (6 hard rules).
3. `Documentation/TASKS.md` (pick the first `pending` task) — unless resuming.
4. Run START_HERE's Reality-check commands; paste output before claiming anything works.

## 2. The Hard Rules (non-negotiable)
1. **Verify before claim** — never say "working" without pasted command output.
2. **No fake logs** — all status from real DB queries + log greps; no simulated/injected data.
3. **Usage validation** — a class isn't "complete" unless something calls its public methods (grep callers).
4. **Audit-first** — for any L2JMobius protocol/network code, read the matching `Documentation/Audit/*.md` first; it overrides conflicting new code.
5. **Document before code** — write the doc, then implement.
6. **Leave cleaner** — remove dead code, update stale docs, leave the repo better than found.

## 3. Session Workflow (one task per session)
1. Claim one task: set `in_progress` + your agent name in `Documentation/TASKS.md`.
2. Do the work; modify only intended source.
3. Verify with a real command; paste the output BEFORE claiming anything works.
4. **Sync the knowledge base at every milestone** — update ALL of: `Documentation/TASKS.md` (status + one-line Result +
   Evidence), `STATUS.md`, `START_HERE.md` (orient), `Documentation/SESSION_HANDOFF.md` (depth), and
   `AIPlayerEngine/AIStatusLogs/ai_progress_report.txt` (the trusted status source). Never leave a fresh
   session able to read stale context.
5. Write a RuntimeLog (`Documentation/RuntimeLogs/YYYY-MM-DD-<task>.md`) recording problems & solutions.
6. Git commit (`type(scope): brief`) WITH the doc updates + RuntimeLog + evidence.

> ⚠️ **Milestone doc-sync is MANDATORY at every milestone even when the whole session is NOT finished.**
> Do NOT defer doc updates to the end of a complete session. If a turn is cut off (e.g., by a
> rate-limit), you MUST still have committed: the milestone's report (`ai_progress_report.txt`),
> `STATUS.md`/`START_HERE.md` orient, `SESSION_HANDOFF.md` depth, `Documentation/TASKS.md` board status, the new
> Audit/RuntimeLog evidence, and (for multi-step work) a `SESSION_IN_PROGRESS.md` checkpoint. The next
> session then resumes from accurate, current context — no re-deriving and no stale claims.

## 4. Rate-limit-safe resumability (IMPORTANT)
Multi-step work MUST be recoverable if a session is cut off mid-work:
- At the start of multi-step work, write `SESSION_IN_PROGRESS.md` at repo root: goal, idempotent checklist, current step, last command output, and "if resuming: do X next".
- Update it **before AND after** every atomic step (mark `← IN PROGRESS`, then `[x]`).
- Commit a WIP checkpoint after each step: `git add -A && git commit -m "WIP(<step>): ..."`.
- Keep steps idempotent (a half-applied step must be safe to re-run).
- On clean completion, fold the scratchpad into the final RuntimeLog and `git rm SESSION_IN_PROGRESS.md`.
- A fresh session: `START_HERE.md` tells it to check for the file first; `session_start.sh` prints it if present.

## 5. Multi-Agent Rules
- Naming: `Laguna` (lead), `Alpha`, `Bravo`, `Charlie`… Set your name in TASKS Owner + git author + RuntimeLog.
- Lock: never edit a task line another agent set `in_progress`.
- Token budget: docs-only 500–1.5k; code+docs 2–5k; full feature 5–10k; audit deep dive 1.5–3k. Measured (Stream F task 100/101): fresh-agent orientation via `START_HERE.md`+routing ≈ **1.3k tokens** vs the **~73k** full handoff — prefer the routing table over bulk reads.
- Merge conflict: pull first; second agent commits WIP and re-syncs. One agent per subsystem folder at a time for multi-file changes. Full protocol in `Documentation/MultiAgentQA.md` task 94 (prevent/detect/resolve/verify/never-force-push).

## 6. RuntimeLog convention
`Documentation/RuntimeLogs/YYYY-MM-DD-HHMMSS-<agent>-task<N>.md`, ≤40–70 lines:
original prompt, objective, files modified, problems & solutions, verification output, next steps.
Echo the prior log's "remaining issues" for continuity.

## 7. Verification commands
```bash
mvn clean compile -f AIPlayerEngine/pom.xml          # build
grep -r "ClassName" --include="*.java" AIPlayerEngine/src   # usage validation
AIPlayerEngine/AIStatusLogs/real_status.sh           # real server state (DB=`gameserver`)
git -C /home/dadj/Projects/l24lude status             # tree state
```

## 8. Git workflow
- Smaller commits: `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `perf:`, `refactor:`, `style:`.
- Paste "BUILD SUCCESS"/"TEST PASS" in the message or RuntimeLog.
- WIP commits (`WIP(<step>): ...`) are valid checkpoints for long tasks.

## 9. Documentation rules
- Keep docs short, accurate, useful. Update stale files immediately.
- Never delete docs — quarantine to `Documentation/_archive_*` with an index entry.
- Document only what helps future work.

*This document is the master rules reference. When in doubt, follow it first.*
