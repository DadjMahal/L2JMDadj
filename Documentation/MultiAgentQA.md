# Multi-Agent QA & Meta Scope — Stream F (tasks 92-95, 99, 101-103)

> Consolidates the QA / meta / planning deliverables of Stream F. Companion to `STATUS.md`,
> `TASKS.md`, and `Documentation/Streams.md`.

---

## Task 92 — Remaining backlog → agent work packages

The remaining (post-Stream-F) work is dominated by the **~145 unwired stub classes** (Stream G)
and a handful of live-proof integrations. Proposed work packages for concurrent agents:

| Package | Scope | Touches | Requires |
|---|---|---|---|
| **G-Combat** | Wire stub combat helpers (RangedKiteAI, PvPSkillRotation, AntiGriefing, AggroManager, SkillAllocator) into CombatAI | `engine/` | Stream C (done) |
| **G-Content** | Wire content/event stubs (EventCalendarAI, AchievementAI, HeroTitleAI, Siege*, ClanHallRegister, Castle*) | `engine/` | Stream D |
| **G-Behavior** | Wire behavior simulators (HumanReactionSimulator, BehaviorSeeder, MovementPatternAI, ResourceHoardingAI) | `engine/` | Stream D |
| **F-Live** | Live-proof the D/E/F hooks + scheduler on real packets | `examples/`, scripts | D/E/F (done) |
| **E-Extra** | DeepLearningCore.predict() consultation; PatternMemory persistence | `neural/`, `engine/` | Stream E |

Each package is independently shippable and testable; parallel agents can take disjoint packages.

---

## Task 93 — Onboard 2nd concurrent agent as pilot

A second agent should NOT start from zero. Follow `AGENT_ONBOARDING.md` for the hard rules and
`START_HERE.md` for fast orientation (the "resume check" pattern saves ~73k→~1.3k tokens). For a
concurrent pilot specifically:
1. **Claim one work package** from task 92 (never overlap — packages are disjoint).
2. **Read first**: `START_HERE.md` → `Documentation/SESSION_HANDOFF.md` → the package's routing
   table row → the matching `Audit/*.md` for any protocol work.
3. **Contract**: run `scripts/check_style.sh` (task 95) + `verify_no_dead_code.sh` (task 96)
   before and after; never touch another package's files; keep `mvn test` green; commit per
   milestone with a `StreamGate-<pkg>` tag.
4. **Handoff**: on completion, update `STATUS.md` + the package's RuntimeLog + this doc.

---

## Task 94 — Merge-conflict resolution protocol

Because concurrent agents edit distinct files, conflicts are rare but possible. Protocol:
1. **Prevent**: agents claim disjoint packages (task 92); never two agents editing the same
   `engine/*.java` in one cycle.
2. **Detect**: `git pull --rebase` before starting work and before committing; if rebase reports
   conflicts, STOP and resolve synchronously (don't force-push).
3. **Resolve**: git keeps both changes; merge hunks manually, preserving both intents. For the two
   recurring conflict hotspots (`AIPlayer.java` getters, `PacketLogger.java` parse additions),
   append new methods rather than editing existing signatures — this makes hunks non-overlapping.
4. **Verify**: after any manual resolution run `mvn -o test` (must stay green) + the style/dead-code
   checkers; note the resolution in the commit message (`Conflicts: <files>`).
5. **Never**: `git push --force` to `master`; rebase onto a teammate's in-flight branch.

---

## Task 99 — Security / abuse review
- **Credential handling**: passwords read from DB config / CLI; `ai123pass` is a dev-only default
  (flagged for rotation in production).
- **Rate / abuse**: `reconnect()` enforces a 3s cooldown + 3-retry bound (Stream E 89); the manager
  think loop is single-scheduled.
- **Server impact**: each bot is a normal client socket; commerce/bypass are server-authoritative
  and validated before acting — the AI cannot inject wealth or bypass restrictions.
- **Isolation**: per-agent state is in-memory per `AIPlayer` (proven by `MultiAgentIntegrationTest`);
  no shared mutable decision state leaks.
- **Open**: rotate dev passwords, per-account session limit, cap concurrent bots per account.

---

## Task 101 — Token budget

**Actual measured orientation budget** (from `cold_start_test.sh`, task 100): a fresh agent
orients via `START_HERE.md` + routing table in **~1.3k tokens** vs the **~73k** full handoff.
Ongoing budget discipline (see `workflow.md` §3):
- One `SESSION_HANDOFF.md` read per session (~2-3k) — read the routing table a-la-carte after that.
- Each Stream F/G package carries a `RuntimeLogs/*.md` ≤ ~40 lines so a handoff is a single read.
- Baseline telemetry: `scripts/baseline_metrics.sh` records token/step usage at each milestone.

---

## Task 102 — Retrospective on the original roadmap

Original plan: a 103-task roadmap (Parts 0-6) with "Level 0-9" milestones. Reality after D/E/F:
- **Accuracy improved**: many "tasks" were scaffolding that compiled but ran on `Math.random()`
  mocks or dead (0-caller) subsystems. Streams D/E/F fixed the recurring root defect — classes
  instantiated-but-never-driven — and removed every `Math.random()` from the decision classes.
- **Sequencing held**: Part 4 (goals) → Part 5 (social/economy) → Part 6 (multi-agent) built on
  the proven Part 1-3 (telemetry, network, combat). The declared D→E→F order worked.
- **Effort distribution**: the real cost was not writing new modules but **wiring existing ones to
  real data** (getters, live logger attachment, outcome hooks). Future estimate should budget for
  "integration" as ≥60% of each package.
- **103-task vs Level 0-9**: the task-list granularity (103 items) is better for tracking than the
  coarse Level 0-9 milestones; both agree stream D is the pivot from reactive to intentional bots.

---

## Task 103 — Define next task-cycle scope

**Stream G — Wire the remaining ~145 stub classes** (the block list since session start) is the
next cycle, split into the disjoint packages of task 92. Priorities:
1. **G-Live first**: a live run that calls the D/E/F hooks + `activityScheduler.nextActivity()` on
   real packets — converts the unit-proven chains into a server-verified proof.
2. **G-Combat** (highest behavioral value — most bots are combat bots).
3. **G-Content / G-Behavior** (event, siege, personality stubs).
Exit criteria for Stream G: every stub either wired + tested or explicitly quarantined; style +
dead-code checkers clean; `mvn test` green; the 23 `ai_%` chars relocated to a live zone.

### Stream G status (2026-08-05) ✅ CODE DONE, run-proof/env/style pending
- **Code scope complete:** G-Live (`GoalDrivenLoop` + wired `LiveFeedbackBridge`), G-Combat
  (RangedKiteAI/PvPSkillRotation/AntiGriefing/AggroManager/SkillAllocator → CombatAI), G-Content
  (EventCalendarAI/AchievementAI/HeroTitleAI → AIPlayer), G-Behavior (HumanReactionSimulator/
  BehaviorSeeder/MovementPatternAI/ResourceHoardingAI → AIPlayer). +14 tests → **117/117 PASS**.
- **Disposition manifest:** `Documentation/StreamGDisposition.md` (wire+test OR quarantine every stub).
- **Remaining:** run `GoalDrivenLoop` live (server proof); `relocate_void_ai.sh --apply` on the L2JM
  host for the 23 void chars; style-normalize the legacy baseline (task 110); E-Extra (PatternMemory
  persistence + `DeepLearningCore.predict()` consultation in `makeDecision()`).


---

## Route map
- Stream D (goals/personality): `Documentation/goal-personality-system.md`
- Stream E (social/economy): `Documentation/social-economy-system.md`
- Streams A-F status: `Documentation/Streams.md`, `STATUS.md`
- Hard rules: `AGENT_ONBOARDING.md`

