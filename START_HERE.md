# 🚀 START HERE — L2JM (read this first, every session)

> Single source of truth to orient a fresh session in ~800 tokens.
> Rules live in `AGENT_ONBOARDING.md`; deep context is read **on demand** via the routing table.

## ⚠️ Resume check (do this first)
If `SESSION_IN_PROGRESS.md` exists at repo root → the last session was **rate-limited mid-work**.
Read it FIRST and resume the "Current step" (don't pick a new task). If absent, continue below.

## Project (one line)
L2JMobius **Interlude** server (`/home/volodro/L2JM`) + external-socket **AI Player Engine** (`AIPlayerEngine/`)
that connects as real client sockets — **no server code modifications**.

## Honest current state *(source: `AIPlayerEngine/AIStatusLogs/ai_progress_report.txt`)*
- ✅ Server **UP**: LoginServer :2106, GameServer :7777 (since Jul 31).
- ✅ Source-code **audit complete** — iterations 1–30 in `Documentation/Audit/`.
- ✅ AIPlayerEngine **compiles** (155 files). Bootstrap + telemetry + perception scaffolding done.
- 🔴 AI is **NOT live-verified**: "CombatAI/QuestAI/MerchantAI/SocialAI implemented with **mock data**,
  not connected to real gameplay. Awaiting protocol packet parsing to complete." **No AI players proven online.**
- 🔴 Fabricated status docs were quarantined (see Blockers).

## Current phase / next task
- **Phase:** 2 — Combat AI (scaffolding done; **live verification is the real gap**).
- **Stream A DONE (2026-08-03):** A1 cold-start test (17/17 PASS; bootup ~73k→~1.3k tokens); A2 `real_status.sh` fix; A3 `count_ai_players.sh` fix (real DB: 25 registered, 0 online).
- **Next task:** **B1** — verify AI account passwords/auth so a player can connect live. Stream B = the critical path: prove 1 AI player actually plays on the running server (25 chars exist at level 1, 0 online).

## Blockers / open issues
1. **Fabricated docs quarantined** in `Documentation/_archive_fabricated/`:
   `PHASE2_COMPLETE.md`, `README-MAGIC.md`, `REFACTORED_ROADMAP.md` (333-task), `WorkLog/SMARTPROJECT.md`,
   2 fake `AIStatusLogs` reports. **Trust only** `ai_progress_report.txt`, `MORNING_REPORT_*.txt`, `real_status.sh`.
2. **Recent work uncommitted** until the cleanup commit lands.
3. **DB name is `gameserver`** (NOT `l2jmobius`); `real_status.sh` uses `sudo mysql -u root gameserver`.
4. Tasks 54 & 63 downgraded to `in_progress` — their tests contain `assertTrue(true)` (fake); need real assertions.

## Reality check — run these and paste the output
```bash
git -C /home/volodro/L2JM status --short | head
ss -tlnp 2>/dev/null | grep -E '2106|7777'   # expect both LISTEN
/home/volodro/L2JM/AIPlayerEngine/AIStatusLogs/real_status.sh
```

## Routing table (read ONLY the file your task touches)
| Task touches | Read first |
|---|---|
| Combat AI | `engine/CombatAI.java`, `Audit/15-combat-ai.md`, `Audit/04-gameserver-network.md` |
| Network / protocol | `Audit/01-commons.md`, `Audit/04-gameserver-network.md`, `protocol/L2JProtocol.java` |
| Perception | `Audit/PART2-01-perception-systems.md`, `protocol/PacketLogger.java` |
| Quest system | `Audit/30-quest-progression.md`, `engine/QuestAI.java` |
| Docs / workflow only | `AGENT_ONBOARDING.md`, `Documentation/WORKFLOW.md` |
| Anything L2JMobius protocol | **audit-first** — read the matching `Audit/*.md` before writing code |

## Rules (6 hard rules — full text in `AGENT_ONBOARDING.md`)
1. Verify before claim (no "working" without pasted output). 2. No fake logs. 3. Usage validation (grep for callers).
4. Audit-first. 5. Document before code. 6. Leave cleaner than you found it.
