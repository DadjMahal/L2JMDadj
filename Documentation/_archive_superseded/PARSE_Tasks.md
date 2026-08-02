# L2JMDadj — 100-Task Roadmap: Top-Tier AI Players + Multi-Agent Workflow

**Goal of this document:** one file that defines the next 100 concrete tasks toward
"ultimate L2 Interlude server," AND the operating system multiple AI agents (starting
with Laguna, scaling to 10+) use to work on this project without re-deriving context
every session and without stepping on each other.

This roadmap assumes the pivot recommended in the prior audit: **build on the in-process
FakePlayer system in `SourceCode/gameserver`, not the external-socket `AIPlayerEngine`**,
unless Task 16 (the explicit decision task) concludes otherwise. Tasks are written so
they're valid either way, with the FakePlayer path as default.

---

## How to use this file (read this part first, every time)

- This is the master task board. Status values: `pending` / `in_progress` / `done` / `blocked`.
- One task = one agent session, ideally. Don't batch unrelated tasks in one context window.
- Before claiming a task: set it to `in_progress` and write your agent name next to it.
  This is what makes 10+ parallel agents safe — **never start a task someone else has
  set to `in_progress`.**
- After finishing: set `done`, write one line in the Result column, and follow the
  Session Protocol below.
- If blocked: set `blocked` and write why in one line. Don't leave it `in_progress` with no note.

---

## Part 0 — The Bootstrap System (build this first, Tasks 1-15)

This is the answer to "how do we make every new agent context orient fast, cheaply,
consistently." The design: **one small always-read file, everything else read on demand.**

```
AGENT_ONBOARDING.md   <- read FIRST, every session, no exceptions (~400 tokens)
STATUS.md             <- current snapshot: phase, last task, blockers, next task (~200 tokens)
STYLEGUIDE.md         <- naming, package layout, logging format, commit format (~600 tokens)
TASKS.md              <- this file, or its successor once these 100 are done
Documentation/Audit/  <- read ONLY the specific file relevant to the current task, not all of it
```

Total mandatory read for a routine task: **~1,200 tokens**, not the ~30 audit docs.
An agent only opens a deep audit doc when the task actually touches that subsystem.

| # | Task | Notes |
|---|---|---|
| 1 | Write `AGENT_ONBOARDING.md` at repo root | Contents: 1-paragraph project summary, the 6 hard rules (no fake logs, no "done" without proof, etc. — reuse from prior prompts doc), and a routing table: "task touches combat → also read STYLEGUIDE.md §AI + relevant Audit doc; task touches network → read 01-commons.md + 04-gameserver-network.md; task touches docs only → read nothing else." Must stay under ~500 tokens forever — if it grows, move detail into a linked doc instead of inlining it. |
| 2 | Write `STATUS.md` | Single source of truth for "where are we right now": current phase (0-9 from original roadmap + this 100-task track), last completed task #, in-progress tasks + owner, known blockers, pointer to the last 3 RuntimeLogs. Overwritten every session, not appended to. |
| 3 | Write `STYLEGUIDE.md` | Package/class naming conventions, where AI logic lives vs where it must NOT live (no logic in packet classes), logging format (see Part 1), required Javadoc on public methods, commit message format, PR/diff size limits per session. |
| 4 | Migrate this file to `TASKS.md` at repo root | Single board, not scattered across RuntimeLogs. |
| 5 | Write `Documentation/SESSION_PROTOCOL.md` | Exact step list every agent follows: (1) read onboarding+status, (2) claim one task in TASKS.md, (3) do the work, (4) verify with real command output — no claim without a pasted result, (5) update STATUS.md, (6) write one RuntimeLog entry (≤40 lines), (7) release the task lock (`done`/`blocked`). |
| 6 | Add a pre-session token budget note to `AGENT_ONBOARDING.md` | e.g. "If your context window is small/free-tier, do not read more than 2 Audit docs per session — pick the smallest task that unblocks the next one." |
| 7 | Add a "Definition of Done" checklist to `STYLEGUIDE.md` | Compiles + actual command output pasted + STATUS.md updated + RuntimeLog written + no new dead/unreferenced classes introduced. |
| 8 | Write a `scripts/verify_no_dead_code.sh` | Greps for classes with zero external references (the exact check used in the audit) and fails if new dead classes appear vs. last baseline. Run at end of every AI-behavior session. |
| 9 | Write a `scripts/session_start.sh` | Prints STATUS.md, first `pending` task in TASKS.md, and reminds the agent of the 6 rules — so a human or agent gets the orientation without reading files manually. |
| 10 | Write a `scripts/session_end.sh` | Checklist runner: checks STATUS.md was modified, checks a new RuntimeLog file exists, runs `verify_no_dead_code.sh`, warns if any of these are missing. |
| 11 | Retire the "333 STAR TREK" style status docs | Replace `AIStatusLogs/FINAL_SUMMARY.md` and `333_STAR_TREK_MISSION.md` with a link to `STATUS.md`. One canonical status file, not several competing narratives. |
| 12 | Establish RuntimeLog naming + size convention in `SESSION_PROTOCOL.md` | `Documentation/RuntimeLogs/<YYYY-MM-DD>-<agentname>-<task#>.md`, hard cap ~40 lines, must include one pasted real command output. |
| 13 | Write `Documentation/MULTI_AGENT_RULES.md` | For the 10+ agent future: task claiming via TASKS.md status field, one agent per subsystem folder at a time (avoid two agents editing `engine/CombatAI.java` simultaneously), merge/conflict protocol, and a rule that no agent renames/deletes another agent's in-progress files. |
| 14 | Dry-run the whole bootstrap with Laguna on a throwaway task | Time/token-box it: does she reach "ready to work" in under ~1,500 tokens using only Onboarding+Status+Styleguide? Record the actual number in `STATUS.md` as a baseline. |
| 15 | Review and trim `AGENT_ONBOARDING.md` based on the dry run | If Laguna needed to ask a clarifying question that a missing sentence would've answered, add exactly that sentence — resist the urge to add more "just in case." |

---

## Part 1 — Logging & Telemetry System (Tasks 16-30)

Goal: every AI player action becomes structured, queryable data you can use to actually
improve behavior — not prose status reports.

| # | Task | Notes |
|---|---|---|
| 16 | **Decision task:** FakePlayer extension vs. AIPlayerEngine protocol rewrite | Use Prompt 5 from the prior audit doc. This gates everything below — do this before Part 2. Result must be written into `STATUS.md` and this file. |
| 17 | Define the AI telemetry event schema | One JSON/struct format for every loggable event: `{timestamp, ai_player_id, event_type, subsystem, state_before, decision, state_after, outcome}`. Applies to combat, movement, quest, trade, social. |
| 18 | Pick a storage backend for telemetry | Recommend: structured log file (one JSON line per event, log-rotated) + optional async write to a local SQLite/Postgres table for querying. Not the human-readable server stdout.log — a separate `ai_telemetry.log`. |
| 19 | Implement `AITelemetryLogger` (or equivalent in FakePlayer path) | One writer, thread-safe, non-blocking (don't stall AI decision loop on disk I/O). |
| 20 | Wire combat decisions into telemetry | Every attack/skill/flee/heal decision logs: what state triggered it, what was chosen, HP before/after, outcome (kill/death/flee). |
| 21 | Wire movement decisions into telemetry | Pathing choices, stuck detection, waypoint reached/failed. |
| 22 | Wire quest decisions into telemetry | Quest accepted/progressed/turned in, with real quest IDs and state, not placeholders. |
| 23 | Wire economic/trade decisions into telemetry | Buy/sell/price reasoning, inventory thresholds hit. |
| 24 | Wire social decisions into telemetry | Party/clan invites sent/accepted, chat triggers, "recruit a losing player" hook (your Level 7 goal) — log the trigger condition and outcome, not just that a message was sent. |
| 25 | Build a daily/weekly aggregation script | Turns raw telemetry into a short report: win rate by class, most common death cause, average time-to-quest-completion, recruitment message response rate. This is what actually "improves your algorithms," not a narrative summary. |
| 26 | Add anomaly flags to telemetry | Auto-flag suspicious patterns (an AI player looping the same failed action 50x, zero HP changes over an hour = likely stuck/disconnected) — cheap automated QA. |
| 27 | Retrofit `29-known-bugs-interlude.md` findings into telemetry checks | e.g. watch for the AttackableAI aggro-oscillation edge case in real data, not just static analysis. |
| 28 | Write `Documentation/TELEMETRY_SCHEMA.md` | The canonical reference for the event schema, so every future agent logs consistently instead of inventing a new format. |
| 29 | Add a lightweight local dashboard (optional, low priority) | Simple HTML/artifact reading the aggregated report — visual sanity check without needing to grep logs by hand. |
| 30 | Verification pass: run 1 real AI player for 1 hour, confirm telemetry is complete and truthful | Compare telemetry claims against actual server DB state (character level, inventory) — this is the anti-"333 STAR TREK" check. Must pass before Part 1 is considered done. |

---

## Part 2 — Perception & Movement (Tasks 31-42)

| # | Task | Notes |
|---|---|---|
| 31 | Implement real state-read layer: HP/MP/CP, position, target, nearby entities | Sourced from real `Player`/`Npc`/`World` objects (FakePlayer path) or real parsed packets (protocol path). No `Math.random()` anywhere past this point. |
| 32 | Implement nearby-entity awareness (radius scan) | Real distance calculation from real coordinates, not mocked. |
| 33 | Implement pathfinding via existing GeoEngine | Reuse `SourceCode/gameserver/geoengine/GeoEngine.java` (`canSeeTarget`, `canMoveToTarget`) — don't reimplement pathing. |
| 34 | Implement anti-stuck detection using real position history | Replace the current stub logic with real position deltas over time. |
| 35 | Implement waypoint/patrol behavior for idle AI players | So they're not just standing still or teleporting randomly. |
| 36 | Implement zone-awareness (town vs. field vs. dungeon vs. PvP zone behavior differences) | AI players should act differently in a peace zone vs. a hunting ground. |
| 37 | Implement follow/escort movement (for party behavior) | Real distance-keeping to a leader, not the current stub. |
| 38 | Implement fall-back-to-town / recall logic on low HP or death | Uses real death/respawn state. |
| 39 | Implement obstacle/terrain-aware combat positioning (kiting distance for casters, melee gap-closing) | |
| 40 | Load-test movement decisions at scale (10-20 concurrent AI players) | Confirm no CPU spike from naive per-tick scans; add throttling/tick-batching if needed. |
| 41 | Telemetry check: movement events match real position deltas | Cross-verify against Part 1's telemetry. |
| 42 | Document the perception/movement API in `Documentation/Audit/` as a new iteration | So future agents don't re-derive "how do I know where things are." |

---

## Part 3 — Combat AI (Tasks 43-58)

| # | Task | Notes |
|---|---|---|
| 43 | Define per-class combat profiles (tank/dps/healer/support baseline behavior) | Uses real class data already in `SourceCode` (`PlayerTemplateData`, `SkillTreeData`). |
| 44 | Implement real target selection (threat/priority based, not `Math.random()`) | |
| 45 | Implement real skill-usage decision (cooldown, MP cost, situational trigger) sourced from `SkillData` | |
| 46 | Implement heal-threshold logic reading real HP% | |
| 47 | Implement flee/emergency-escape logic reading real HP% + real nearby-enemy count | |
| 48 | Implement buff/debuff management (self-buffing on spawn, re-buffing on expiry) | |
| 49 | Implement basic party combat coordination (focus-fire target, don't pull aggro off tank) | |
| 50 | Implement PvP-specific behavior (karma/PK risk awareness, safe-zone logic) | |
| 51 | Implement raid/boss-fight positioning basics (using `BossTactics`-style logic, but wired to real boss data, not hardcoded strings) | |
| 52 | Decide fate of `neural/NeuralNetwork.java` per prior audit Prompt 3 — implement or delete | Don't leave it dead. |
| 53 | If wired in: define reward signal for `ReinforcementEngine` from real combat outcomes (win/loss/damage taken) | |
| 54 | Add difficulty/skill-level variance between AI players (not all AI players play identically — ties into "personality") | |
| 55 | Combat regression test: scripted encounter, compare AI decisions to expected behavior tree output | |
| 56 | Telemetry-driven tuning pass: review Part 1 aggregation report, adjust one weak behavior (e.g. heal threshold too late/early) | |
| 57 | Document final combat decision tree/utility scoring in `Documentation/Audit/` | |
| 58 | Verification: run AI players against real mobs for 30 min, confirm death rate/kill rate is sane (not instant death, not literally unkillable) | |

---

## Part 4 — Goals, Wishes & Long-Term Behavior (Tasks 59-74)

This is the part that makes them feel like players with intent, not scripts.

| # | Task | Notes |
|---|---|---|
| 59 | Design the long-term goal model (Utility AI or GOAP — pick one, document why) | e.g. goals: "reach level N," "afford item X," "join/lead a clan," "win N olympiad matches." |
| 60 | Implement goal scoring/selection (which goal does this AI player pursue right now) | Should read real state: current level, adena, clan status. |
| 61 | Implement quest-goal integration | Real quest state machine (Level 26/30 doc — quest progression synthesis already exists, use it). |
| 62 | Implement gear-progression goals (target item, path to afford/earn it) | |
| 63 | Implement clan-related goals (join, contribute, participate in siege) | |
| 64 | Implement olympiad/PvP-achievement goals for competitive-personality AI players | |
| 65 | Implement "personality" trait system that biases goal selection (already partially present in `advanced/PersonalityProfile.java` — audit whether it's wired to real decisions or also decorative, then fix) | |
| 66 | Implement short-term/long-term goal interruption logic (combat interrupts questing, emergency interrupts everything) | |
| 67 | Implement the "recruit real players" hook from your original Level 7 design | Trigger: real player loses repeatedly to same-clan AI player -> offline-designed rule fires an in-game invite/message. Must be tuned so it doesn't feel spammy — rate-limit and vary phrasing. |
| 68 | Implement a lightweight "teaching" behavior | AI players give real players actionable tips (e.g., "you're standing in the aggro radius") based on observed real-player state, not scripted broadcast spam. |
| 69 | Add guardrails: rate limits, cooldowns, and an opt-out/mute mechanism for the teaching/recruiting messages | Nobody wants harassment-by-bot; this protects your server's reputation. |
| 70 | Implement daily/weekly goal-cycle reset (dailies, weekly raid attempts) | |
| 71 | Implement long-horizon memory: AI player remembers past deaths/losses to a specific player/clan (grudge/respect system) tied into `advanced/EmotionalState.java` — audit whether wired, fix if not | |
| 72 | Telemetry check: log goal selection + goal completion rate | Feeds back into Part 1 reporting. |
| 73 | Tuning pass based on 1 week of real goal-completion telemetry | |
| 74 | Document the full goal/personality system in `Documentation/Audit/` | |

---

## Part 5 — Social, Economy & World Presence (Tasks 75-88)

| # | Task | Notes |
|---|---|---|
| 75 | Implement real inventory-aware buy/sell logic (replace `MerchantAI` placeholders) | |
| 76 | Implement real price-awareness / simple arbitrage using actual marketplace data | |
| 77 | Implement party-formation behavior (AI players inviting each other or real players for suitable content) | |
| 78 | Implement clan-application/response behavior for AI players as clan members | |
| 79 | Implement chat behavior grounded in real context (not random flavor text) — reacts to real events (level up, item drop, death) | |
| 80 | Implement collective knowledge sharing between AI players (`social/CollectiveKnowledge.java` — audit real wiring, fix if decorative) | e.g. one AI player "learns" a good hunting spot, others benefit |
| 81 | Implement basic swarm/clan-coordination for sieges (`social/SwarmCoordinator.java` — audit + fix) | |
| 82 | Implement simple diplomacy behavior between AI-controlled clans (`social/DiplomacyEngine.java` — audit + fix) | |
| 83 | Implement economic engine sanity checks | Prevent AI players from destabilizing your server economy (adena sinks/faucets stay balanced — check against `EconomicEngine.java`, verify it isn't decorative). |
| 84 | Add population/spawn control (how many AI players active at once, per zone caps) | Ties to `AIPlayerSpawnController.java` — audit + fix. |
| 85 | Implement day/night or schedule-based activity variance (already attempted via the fake night-injection script — replace with real scheduled AI player activity, not fabricated logs) | |
| 86 | Implement graceful reconnect/persistence (AI player resumes state after server restart) | |
| 87 | Telemetry + tuning pass on social/economy behavior | |
| 88 | Document social/economy systems in `Documentation/Audit/` | |

---

## Part 6 — Multi-Agent Scale-Out & Final QA (Tasks 89-100)

| # | Task | Notes |
|---|---|---|
| 89 | Split remaining backlog into agent-sized, non-overlapping work packages | One folder/subsystem per concurrent agent, per `MULTI_AGENT_RULES.md`. |
| 90 | Onboard a 2nd concurrent agent as a pilot (before scaling to 10+) | Confirm the TASKS.md locking convention actually prevents collisions in practice. |
| 91 | Add a merge-conflict resolution protocol doc | What happens when two agents touch overlapping files despite the locking convention. |
| 92 | Build a "style consistency" checker | Script that flags code not matching `STYLEGUIDE.md` conventions (naming, package placement, missing telemetry hooks on new AI decision classes). |
| 93 | Run `verify_no_dead_code.sh` across the whole AI player codebase, clean up remaining dead classes from the original 104 | |
| 94 | Full integration test: spawn N AI players (start small, e.g. 5), run for several hours, verify via telemetry (not narrative reports) | |
| 95 | Load/performance test on your actual Azure 16GB/4vCPU box at target AI-player count | Confirm headroom before scaling AI player count further. |
| 96 | Security/abuse review: can AI players be exploited to duplicate items, break economy, or grief real players | |
| 97 | Write a "new agent cold-start" test: fresh context window, only reads Onboarding+Status+Styleguide, completes one real task correctly | Final validation of Part 0's design goal. |
| 98 | Update `AI_models_reyting_i_stacks_2026.md`-style budget doc with actual token spend observed for a typical task under the new bootstrap | Confirms whether the "minimum token spending" goal was actually hit, with real numbers. |
| 99 | Retrospective: compare this 100-task run against the original Level 0-9 roadmap, update `L2J_Mobius_TZ_StepByStep.md` if scope has shifted | |
| 100 | Define the next 100-task cycle scope based on what telemetry says is weakest | Keep the roadmap a living document, not a one-time list. |

---

## Notes on sequencing

- **Part 0 (1-15) is non-negotiable and goes first** — it's the multiplier that makes
  every later task cheaper and more consistent across sessions and agents.
- **Task 16 (FakePlayer vs. protocol rewrite decision) gates Parts 2-5.** Don't let an
  agent start combat/movement work on top of an undecided foundation.
- Parts 2-5 (perception → combat → goals → social) are ordered so each one has real
  data to build on from the previous — don't let an agent jump to "goals" before
  "perception" is real, or you'll just get a new flavor of `Math.random()`.
- Part 1 (telemetry) is listed before Parts 2-5 deliberately: build the instrumentation
  *before* the behaviors, so every behavior you add is measurable from day one instead
  of being audited for truthfulness after the fact.
