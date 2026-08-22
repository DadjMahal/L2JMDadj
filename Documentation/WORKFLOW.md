# 📋 WORKFLOW — the ONE rules reference

> Merged 2026-08-22 from `SESSION_PROTOCOL`+`MULTI_AGENT_RULES`+`WORKFLOW_RULES` (2026-08-02)
> and from `AGENT_ONBOARDING.md` + `REQUIREMENTS.md` + `STYLEGUIDE.md` (now archived).
> Orientation lives in `START_HERE.md`; current state in `STATUS.md`; the board in `TASKS.md`.
> When in doubt, follow this file first.

## 1. Session startup (in this order)
1. `START_HERE.md` (orient). **If `SESSION_IN_PROGRESS.md` exists at repo root → resume it first** (§5).
2. `STATUS.md` (current state) → `Documentation/TASKS.md` (pick an open row you don't collide with).
3. `git pull --rebase origin master` — always start from latest.
4. Run `scripts/session_start.sh` for a reality-check (ports, git status, real server state);
   paste output before claiming anything works.

## 2. The Hard Rules (non-negotiable)
1. **Verify before claim** — never say "working" without pasted command output.
2. **No fake logs** — all status from real DB queries + log greps; no simulated/injected data.
3. **Never edit server source** — `SourceCode/` and `ServerBuild/` are read-only ground truth
   (reading/parsing their data files is allowed and is the point of the GK-* tasks).
4. `mvn -o -f AIPlayerEngine/pom.xml test` **green before and after** every task.
5. **One task = one commit** (`type(scope): brief`), pushed to master right after; update the
   TASKS.md row + a RuntimeLog. No unrelated changes in the diff.
6. **Usage validation** — a class isn't "complete" unless something calls its public methods (grep callers).
7. **Leave cleaner** — remove dead code, update stale docs, leave the repo better than found.
   If something contradicts reality, STOP and report in the RuntimeLog instead of improvising.

## 3. Session workflow (one task per session)
1. Claim one row in `Documentation/TASKS.md`: set `IN_PROGRESS (owner)` → commit → push the claim.
2. Do the work; modify only intended source. Task prompts for the active program are embedded in
   `Documentation/UpgradePlan/AUDIT_*.md` (`### PROMPT EP-4` etc.) — read the prompt before starting.
3. Verify with a real command; paste output BEFORE claiming anything works.
4. **Milestone doc-sync (mandatory at every milestone, even mid-session):** update
   `STATUS.md` (state) + the `TASKS.md` row (status + one-line Result/Evidence) + write the
   RuntimeLog, then commit. A fresh session must never read stale context.
5. Done row → `DONE-PUSHED <hash>` → commit → push → move the row to `REVIEWED_TASKS.md` (registry).

## 4. Documentation rules
- Every doc states ONE thing in ONE place; others link to it (no duplicated rule lists / state).
- Keep docs short, accurate, useful; fix a stale doc the moment you see it.
- Never delete docs — `git mv` to `Documentation/_archive/` + add a row to `_ARCHIVE_INDEX.md`.
- RuntimeLog convention: `Documentation/RuntimeLogs/YYYY-MM-DD-<task-id>-<slug>.md`, ≤70 lines
  (prompt, objective, files, problems & solutions, verification output, next steps).
  Raw run output stays OUT of git (untracked local logs only).

## 5. Rate-limit-safe resumability
- Start of multi-step work: write `SESSION_IN_PROGRESS.md` at repo root (goal, idempotent
  checklist, current step, last command output, "if resuming: do X next").
- Update it before AND after every atomic step; `WIP(<step>): ...` checkpoint commits are valid.
- Keep steps idempotent (a half-applied step must be safe to re-run).
- On clean completion, fold it into the final RuntimeLog and `git rm SESSION_IN_PROGRESS.md`.

## 6. Multi-agent rules
- Naming: `Laguna` (lead), `Alpha`, `Bravo`, `Charlie`… Set your name in the TASKS Owner column,
  git author and RuntimeLog.
- Lock: never edit a task row another agent set `IN_PROGRESS`. One agent per subsystem folder
  (see the ownership map in `TASKS.md`).
- Merge conflict: pull first; second agent commits WIP and re-syncs. Never force-push.
- Token budget per session: docs-only 500–1.5k · code+docs 2–5k · full feature 5–10k.
  Fresh-agent orientation via `START_HERE.md`+routing ≈ 1.3k tokens — prefer the routing table
  over bulk reads.

## 7. Git workflow
- Commit types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`.
- Format: `type(scope): brief description` (imperative mood; body optional, separated by blank line).
- Paste "BUILD SUCCESS"/"TEST PASS" in the message or RuntimeLog.
- `git pull --rebase origin master` before push; push right after committing a task.

## 8. Code style (engine)
- Naming: packages lowercase (`com.aiplayer.<feature>`); classes PascalCase (`CombatAI`,
  `BotSession`); methods/fields camelCase; constants UPPER_SNAKE. AI decision modules end in `AI`.
- AI logic belongs in `behavior/` (+domain subpackages), `knowledge/`, `learning/` — NEVER in
  packet classes, network handlers, or data models (keep those pure data/transport).
- Logging: `java.util.logging` only (`Logger.get/Severe/Warning/Info/Fine`); no slf4j/log4j in
  the engine. Javadoc on public API of new classes.
- `MODE:PARTIAL` files: check `MODE_PARTIAL_INDEX.md` before touching; a file leaves the index
  only by gaining tests + flipping its header to `MODE: COMPLETE`.

## 9. Verification commands
```bash
scripts/gate.sh                           # GOLDEN GATE: tests + style + secret-lint, one offline command (F-07)
mvn -o -f AIPlayerEngine/pom.xml test     # the gate (see STATUS.md for count)
mvn -o -f AIPlayerEngine/pom.xml compile            # build only
grep -r "ClassName" --include="*.java" AIPlayerEngine/src   # usage validation
AIPlayerEngine/AIStatusLogs/real_status.sh          # real server state (DB=gameserver)
git status                                          # tree state
```
