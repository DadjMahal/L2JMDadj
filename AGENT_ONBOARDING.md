# 🤖 AI Player Agent Onboarding

> **Read `START_HERE.md` first** for current state + routing table + reality-check. This file holds the rules only.
> If `SESSION_IN_PROGRESS.md` exists at repo root, resume it (the last session was rate-limited). See `Documentation/WORKFLOW.md`.

## Project Summary
L2JMobius **Interlude** server + external-socket **AI Player Engine** (`AIPlayerEngine/`).
Build autonomous AI players that connect to the running game server as normal client sockets —
**no server code modifications**. AI players perform combat, questing, trading, and social interactions.
**Honest status:** engine compiles; a player can boot, enter the world, and fight a real monster (live
PvE combat PROVEN, B4). The CombatAI/QuestAI/MerchantAI/SocialAI decision classes still use mock data
internally — see `START_HERE.md`.

## The 7 Hard Rules
1. **Verify before claim** — never say "working" without pasted command output.
2. **Zero fake logs** — all status from real DB queries + log greps; no simulated/injected data.
3. **Usage validation** — a class isn't "complete" unless something calls its public methods (grep callers).
4. **Audit-first** — for any L2JMobius protocol/network code, read the matching `Documentation/Audit/*.md` first.
5. **Document before code** — write the doc, then implement.
6. **Leave cleaner** — remove dead code, update stale docs, leave the repo better than found.
7. **Milestone doc-sync** — after EVERY milestone, update the knowledge base (`START_HERE.md`, `STATUS.md`,
   `SESSION_HANDOFF.md`, `TASKS.md` board, `ai_progress_report.txt`) and git-commit, even if the whole
   session isn't finished. A fresh session must never read stale context.

## Token budget (per session)
Docs only 500–1.5k · code+docs 2–5k · full feature 5–10k · audit deep dive 1.5–3k.
Bootup (`START_HERE.md` + this file) target ≤ ~1,200 tokens.

## Quick Start
```bash
cd /home/volodro/L2JM && ./scripts/session_start.sh   # orients + resume-aware
```
Routing table + reality-check commands live in `START_HERE.md`. Full process/rules in `Documentation/WORKFLOW.md`.
