# Docs consolidation — one fact, one place (2026-08-22)

**Task:** make onboarding docs simple + informative; kill duplication across sources so a fresh
AI understands the repo fast. (Owner request; doc-sweeper lane.)

## Problems found
1. **Hard rules in 4 drifting copies** — START_HERE §4 (6), AGENT_ONBOARDING (7), WORKFLOW §2 (6),
   REQUIREMENTS/STYLEGUIDE (checklists) — different lists, contradictory framing.
2. **Current-state/changelog in 3 places** — START_HERE §0 EP-hash blob, STATUS.md, TASKS §3/§6.
3. **Live board 97% done-noise** — TASKS.md carried 100 rows, ~97 DONE + a changelog duplicating
   RuntimeLogs.
4. **Whole stale docs from old workspaces** — REPOSITORY_STRUCTURE/RUNTIME_LAYOUT/
   SERVER_STARTUP/BUILD_PROCESS/GIT (`~/L2JM/` paths, dirs that don't exist here);
   AIPlayerEngine/WorkLog/* (2026-08-01 `/home/volodro/` relics citing fabricated SMARTPROJECT.md);
   Documentation/audit/ (superseded by UpgradePlan AUDITs); Baselines/ (unfilled template);
   STYLEGUIDE (dead packages engine/neural/advanced); AIWebDashboard README (droplet-era).
5. **Docs pointing at archived/moved files** — Documentation/README, REQUIREMENTS, WORKFLOW,
   AGENT_ONBOARDING, REVIEWED_TASKS (TIM-001 "OPEN" reminder vs §B RESOLVED header),
   root README ("TASKS.md §11" which no longer exists).
6. **Broken onboarding scripts** — session_start.sh/session_end.sh/real_status.sh hardcoded
   `/home/volodro/L2JM`; session_start grepped root TASKS.md with dead `pending` vocabulary.

## Changes
- **Archived (git mv → `Documentation/_archive/`, indexed in _ARCHIVE_INDEX.md):** the 5 server-era
  docs, audit-2026-08/, Baselines/, WorkLog/*, REQUIREMENTS.md, STYLEGUIDE.md, AGENT_ONBOARDING.md
  (28 files, ~1,570 lines off the live tree).
- **START_HERE.md rewritten evergreen** — goal, honest one-paragraph state (→ STATUS.md for detail),
  run/stop/validate, doc map, code routing, hard rules, session boot. No hashes/changelogs.
- **WORKFLOW.md = the ONE rules doc** — canonical hard rules (7, merged), session flow, doc-sync
  (STATUS + TASKS row + RuntimeLog only — dropped dead ai_progress_report/SESSION_HANDOFF
  conventions), resumability, multi-agent, RuntimeLog/git conventions, code style (absorbed
  STYLEGUIDE, corrected to live packages, java.util.logging reality).
- **TASKS.md = open work only** (5 rows, 50 lines); 97 done rows + STEP history + changelog →
  **REVIEWED_TASKS.md §E** (registry); fixed its TIM-001 contradiction + stale refs.
- **AGENTS.md (new, root)** — standard AI-agent entry point routing to the 4 core docs.
- **Documentation/README.md** — accurate 2-table index (live / archive).
- **Fixes:** session_start/end.sh + real_status.sh self-locate repo (no volodro paths);
  session_start reads Documentation/TASKS.md with live vocabulary; root README stale §11 pointer;
  SOURCE_CODE_MAP BUILD_PROCESS pointer; AIWebDashboard README marked legacy.

## Verification
- Link checker over 24 live .md files: 0 real dangling refs (remaining "unresolved" = future
  UpgradePlan deliverables of TODO tasks + prose mentions + conditional SESSION_IN_PROGRESS.md).
- grep: no live doc references REQUIREMENTS/STYLEGUIDE/AGENT_ONBOARDING/WorkLog/audit/Baselines
  (only WORKFLOW's intentional "now archived" provenance note).
- `./scripts/session_start.sh` dry-run: correct repo, git status, ports, real_status.sh path OK.
- `mvn -o -f AIPlayerEngine/pom.xml test` (docs+scripts only change):
  `Tests run: 415, Failures: 0, Errors: 0, Skipped: 0` — **BUILD SUCCESS** (15.0s, 2026-08-22).
- Live boot path: AGENTS.md (19) + START_HERE (88) + STATUS (21) + WORKFLOW (96) + TASKS (50)
  ≈ 274 lines, vs ≈ 499 before with contradictions.

## Next steps / remaining issues
- ~24 legacy droplet-era scripts still hardcode volodro paths (telemetry/watchdog/probes — not
  referenced by live docs): candidate cleanup task `git rm` or self-locate.
- 383MB untracked raw `.log` files sit in Documentation/RuntimeLogs/ (local-only, not in git);
  safe to delete locally anytime.
- UpgradePlan README §5 audit facts say "383 tests" (now 415) — planning artifact, left as-is.
