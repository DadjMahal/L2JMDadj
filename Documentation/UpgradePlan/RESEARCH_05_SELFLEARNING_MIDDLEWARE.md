# 🔬 RESEARCH 05 — Smart self-learning engine: middleware, bootstrap, scale (goal 5)

> Owner question: *"How can we go forward toward a smart AI engine that learns on its own —
> maybe we don't need a big hardcoded database; maybe a middleware where AI Players request
> 'what's the next quest / NPC / location?' I want to launch the server and watch bots start
> their journey by themselves: random race, random style, random name, connect on their own and
> play. I want to input 'launch 10/100/500/2000' and the system handles it smartly. Players
> should navigate through SourceCode like through a real Lineage client."*
>
> **Provenance note:** web search was rate-limited while writing this; the analysis below is
> grounded in (a) verified codebase facts (README §5 + per-audit evidence) and (b) established
> engineering practice (contextual bandits, virtual threads, L2J server behavior). Numbers
> marked **[measure]** must be verified empirically by RS-5/RS-6 before being trusted.

## 1. Executive summary

1. **Don't choose between "big database" and "middleware" — layer them.** Generate the
   knowledge JSON from the server datapack (Audit 03), serve it to bots through ONE query API
   (`KnowledgeService`) inside the engine — that IS the middleware, without network hops. An
   HTTP/LLM layer is optional and goes on top, not in the decision hot path.
2. **Self-learning: contextual bandits + per-bot memory + fleet gossip, NOT deep RL.** The
   action spaces here (which zone, which mob, which skill, when to retreat) are small and the
   reward (XP/min, deaths, adena) is dense — frequency-count learning with ε-exploration is
   enough and is 95% about closing the loop (Audit 04 showed it's open today).
3. **Autonomous bootstrap is 80% built**: accounts/provisioning, race rotation, name generation
   exist; the missing 20% is the character-CREATE packet flow + a "new life" director that
   starts a bot's journey with zero human input.
4. **Scaling**: virtual threads + one connection supervisor make 100–500 bots per box realistic;
   2000 needs a FleetSupervisor sharding across FleetPlay JVMs/boxes. The game server, not the
   engine, likely breaks first **[measure RS-6]**.
5. **"Navigate like a real client"** = three parity layers: protocol parity (done — the engine
   speaks raw client packets), data parity (GK-*: parse the same files the server loads),
   wisdom parity (learned guides on top). A real client + a good player wiki = exactly this
   stack.

## 2. Knowledge layer — middleware vs generated DB (verdict)

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| Hand-coded Java DBs (today) | zero infra | unmaintainable, <2% coverage, stale | retire (GK-6) |
| **Generated JSON + in-process query API** | ground truth, diffable, fast (μs), offline | regeneration step on datapack change | ✅ core (GK-* + RS-1) |
| External knowledge service (HTTP) | language-agnostic, multi-fleet, inspectable | network hop, new failure domain, ops burden | later, only if 2+ fleets |
| LLM/RAG middleware | flexible questions, no schema | slow (100ms+), nondeterministic, cost, hallucination in coordinates | advisory/offline only (RS-4) |

**Design — the owner's middleware, concretely:** `com.aiplayer.knowledge.KnowledgeService`
(the API bots "ask questions" of) over the generated JSON:

```
nextQuest(bot) · nearestNpc(matcher, pos) · droppersOf(itemId) · vendorSelling(itemId)
huntZoneFor(level, race) · route(from, to) · skillLadder(classId) · dialogGraph(npcId)
```

The service owns caching/indexes and is the ONLY door to knowledge — so swapping the backing
store (JSON today, HTTP service or LLM-assisted tomorrow) touches one class. This keeps the
"middleware" dream alive at zero operating cost until a second fleet justifies the network form.

## 3. Learning layer — realistic self-learning design

**Reject**: deep RL (needs simulators/massive samples; MMO latency is seconds; state partially
observable), supervised "clone the human" (no human teacher available).

**Adopt** (maps to IN-* tasks):
1. **Contextual bandits** per decision type: context = (level, class, zone, mobType), arms =
   candidate actions, reward = normalized XP/min − death penalty (+ adena term for economy).
   The existing `DeepLearningCore` frequency tables ARE a bandit memory — IN-1 finally reads
   them in decisions.
2. **EWMA memories** per bot (HuntingMemory IN-3): recent performance dominates; cheap;
   interpretable on the dashboard.
3. **Fleet gossip** (IN-5): new bots start from the fleet's pooled priors — the fleet as a
   whole "knows" the map within hours, individual bots stay cheap.
4. **Exploration** (IN-4): ε-greedy decaying 0.3→0.05; personalities bias it (brave/cautious),
   emotions nudge it transiently — variability without chaos.
5. **Credit assignment stays simple**: attribute XP to (zone, mobType) at kill time; no
   trajectory replay needed at these decision granularities.

Why this is genuinely "self-learning": behavior measurably diverges from the initial constants
given different reward landscapes, and the divergence is visible in the behavior dataset
(LW-3) — that's the acceptance bar, not the word "neural" in a class name.

## 4. Autonomous bootstrap — "they start their journey by themselves"

Pipeline (stage → status today → gap):

| Stage | Today | Gap |
|---|---|---|
| 1. Account exists/created | scripts/provision_fleet.{sh,py} (DB-side) | auto-register via login protocol OR DB tool call from engine — verify server's auto-create setting **[RS-2 step 1]** |
| 2. Login + session | ✅ proven (PlayOk flow) | – |
| 3. Character CREATE (0x0B) | ❌ engine only SELECTs existing chars | implement CharacterCreate + name-uniqueness retry |
| 4. Random identity | ✅ resolveRaces rotation; NameGenerator exists (director/) | wire: random race → random base class incl. fighter/mystic "style" → generated name (Lore-friendly per race) |
| 5. Tutorial/newbie start | ✅ spawn relocation works; quest accept proven | "new life" script: walk out of spawn, first kill, first quest via CareerDirector (IN-6) |
| 6. Play forever | ✅ farming loop + session resume (LW-5) | – |

End state (RS-2): `java com.aiplayer.cli.FleetLauncher --bots 25 --self-contained` → the fleet
creates its own accounts/characters/names and starts playing with zero pre-provisioning.

## 5. Scaling — "launch 10/100/500/2000"

- **Concurrency**: EP-7 virtual threads per bot session (blocking socket IO is exactly their
  design point). Engine-side, 500 bots ≈ 500 virtual threads + a handful platform threads.
- **Memory**: budget ~1–3 MB/bot engine-side (socket buffers + snapshot + memories) → 500 bots
  ≈ 0.5–1.5 GB heap **[measure]**. Session files + knowledge JSON are shared/read-only.
- **The game server breaks first**: every bot is a full Player actor with AI think ticks, mob
  aggro processing, and DB writes; L2JMobius single-instance comfort zone is likely low
  hundreds of actors **[measure RS-5→RS-6 ladder: 50 → 100 → 200 → 500]**. Mitigations:
  staggered launch with jitter (reuse S2-T07 backoff), tick-rate scaling (300 ms → 500 ms for
  large fleets), spread bots across zones (FleetSpreadPlanner already does this), keep
  dashboard polling at fleet level (not per-bot).
- **2000 bots** = `FleetSupervisor` launching N FleetPlay JVMs (e.g., 4×500) per box / across
  boxes, sharing nothing but the fleet-policy file (IN-5) and one dashboard aggregator.
  Design it in RS-3, build only if measurements justify.
- **Launch UX**: one input (`--bots N`) drives everything: stagger interval, tick rate, memory
  flags, zone spread — encoded in a scaling profile table, not human tuning.

## 6. Layered world model — "navigate SourceCode like a real client"

```
L0 Protocol parity   ✅ done   — raw client packets (login→enter-world→actions)
L1 Data parity       GK-*      — parse the SAME files the server loads (npcs/drops/quests/
                                 shops/skills/spawns) → JSON knowledge; server datapack update
                                 = re-run extractors, zero code drift
L2 Dialog parity     GK-4/IN-6 — htm dialog graphs let bots click NPCs like players do
L3 Wisdom parity     IN-* + guide.json — learned zones/mobs/builds + curated new-player
                                 heuristics = "a friend who played before"
```

A real Lineage client is L0 + a human with a wiki is L1–L3. The end state: a bot dropped
anywhere in the world asks KnowledgeService where to go and why, and its own memory tells it
what actually worked.

## 7. Phased roadmap

- **Phase A (hygiene + data)**: EP-1..6, GK-1..5, LW-1..4 → clean engine, generated knowledge,
  structured events. *Exit: 383+ tests green on new packages; npcs/quests/items JSON in repo.*
- **Phase B (query + loop)**: GK-6..8, RS-1, IN-1..4, LW-5 → KnowledgeService live, learning
  closed, sessions resume. *Exit: A/B xp/min improvement from IN-1; bots resume quests.*
- **Phase C (autonomy)**: RS-2, IN-5, IN-6 → self-contained launch, career arcs, fleet
  teaching. *Exit: `--bots 25 --self-contained` runs unattended 24 h, zero-touch.*
- **Phase D (scale + polish)**: EP-7/8, RS-3..6 → virtual threads, 100/500 measurement ladder,
  supervisor design, final docs. *Exit: measured scaling curve + tuned profiles.*

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Mass rename (EP-3) breaks live fleet muscle-memory/scripts | scripts updated in-task; changelog table old→new; one wave, one week |
| Extractor drift vs datapack updates | committed JSON + validator + extract_all.py in CI-style check (GK-1) |
| Learned behavior degrades (bad rewards) | SHADOW mode first (IN-1), A/B gates at every flip, dataset replay |
| Server overload at 500 | staggered ladder + tick scaling; stop at the first comfort ceiling [measure] |
| CharacterCreate packet anti-cheat/kick | study org.l2jmobius CharacterCreate handler (read-only) before RS-2 |
| Security exposure on LAN | EP-6 token enforcement precedes everything (already Wave 1) |

## 9. How the owner should drive AI agents (prompt protocol)

1. **Never prompt architecture whole.** Feed one AUDIT task ID at a time — the prompts are
   already written; paste them verbatim.
2. **Demand evidence, not claims** — every prompt ends with "paste output". If the agent shows
   no green test tail or no live-run output, the task is not done (repo hard rule #1).
3. **Architecture decisions stay with the owner**, execution with the agent: when an agent
   proposes a design change beyond its task, reply "record it as a proposal in the RuntimeLog,
   don't implement."
4. **Meta-prompt for new ad-hoc work:**
   ```
   You are working in /home/dadj/Projects/l24lude. Read Documentation/UpgradePlan/README.md §2
   (hard rules) and §5 (facts). Task: <one sentence>. Constraints: tests stay green; never edit
   SourceCode//ServerBuild/; one commit; evidence pasted. If reality differs from this prompt,
   STOP and report instead of improvising.
   ```
5. **Weekly**: read `Documentation/UpgradePlan/README.md` status tables + watcher dashboards
   for 10 minutes — that's the whole control loop for a non-architect owner.

## 10. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| RS-1 | KnowledgeService query facade (the in-process middleware) | M | GK-6 | TODO |
| RS-2 | Self-contained autonomous bootstrap (`--self-contained`) | L | RS-1, GK-6 | TODO |
| RS-3 | Scaling profiles + supervisor design doc | M | EP-7 | TODO |
| RS-4 | LLM advisory hook (offline dataset analysis, optional) | S | LW-3 | TODO |
| RS-5 | 100-bot soak measurement | M | RS-3 | TODO |
| RS-6 | 500-bot profile ladder + ceiling report | L | RS-5 | TODO |

### PROMPT RS-1
```
TASK RS-1: KnowledgeService — the query facade bots "ask"

CONTEXT
- RESEARCH_05 §2: generated JSON (GK-*) exists; consumers should ask ONE service, never parse
  JSON themselves.

GOAL
com.aiplayer.knowledge.KnowledgeService wrapping KnowledgeBase with the stable query surface:
nextQuest(botState), nearestNpc(predicate, pos), droppersOf(itemId), vendorSelling(itemId),
huntZoneFor(level, race), route(from, to), skillLadder(classId), dialogGraph(npcId).
Queries logged (FINE) with latency; all decision code migrated to it (BotPlayController,
RelocationPlanner, RestockPlanner, CareerDirector-to-be); a QualityGate test asserts: every
knowledge consumer imports KnowledgeService and nothing else from knowledge.*.

ACCEPTANCE
- Tests green (+facade tests, +QualityGate grep-test); boot log prints per-query p50 (paste);
  RuntimeLog.
```

### PROMPT RS-2
```
TASK RS-2: Fully autonomous bootstrap

CONTEXT
- RESEARCH_05 §4 pipeline table. Engine logs in and selects chars today; CharacterCreate is
  missing. Read (ONLY) org.l2jmobius gameserver CharacterCreate handler + login auto-create
  config to learn exact constraints (name charset/length, hair/face fields, classId per race,
  server-side validation) and document them in the RuntimeLog BEFORE coding.

GOAL
cli/FleetLauncher --bots N --self-contained: for each bot — ensure account (login-protocol
auto-register if the server allows it; else one-time DB helper with the same rules as
provision_fleet.py), CREATE a fresh character: random race → random base class + fighter/mystic
style → name from NameGenerator with per-race flavor + uniqueness retry; enter world; run the
"new life" script (leave spawn point, first kill, accept first chain quest); from then on the
normal loop. All randomness seeded and logged per bot (race/class/name/seed on one line).

STEPS
1. RuntimeLog research note (constraints from server source).
2. CharacterCreate encoder + response handling (CharCreateFail reasons mapped); unit tests with
   fake GS. 3. Identity generator + new-life script. 4. Live proof.

ACCEPTANCE
- Tests green (+create/identity tests); live run `--bots 10 --self-contained` on the real
  server: 10 NEW named characters enter world and each lands ≥1 kill + ≥1 quest accept within
  15 min (paste per-bot one-liners); RuntimeLog.
```

### PROMPT RS-3
```
TASK RS-3: Scaling profiles + supervisor design

CONTEXT
- RESEARCH_05 §5. Today fleet size is the only knob; tick rate, stagger, memory flags are
  manual.

GOAL
1. FleetConfig scaling profiles: size→{tickMs, staggerMs, connectJitterMs, heapHint} table
   (≤50/100/200/500) applied automatically from --bots N.
2. DESIGN DOC ONLY (no multi-JVM code yet): docs/SCALING.md — FleetSupervisor architecture for
   N FleetPlay JVMs: shared fleet-policy file (IN-5), dashboard aggregation, per-shard zone
   assignment, failure handling; include the measurement plan for RS-5/RS-6.

ACCEPTANCE
- Profile unit tests (size 250 → 500 ms tick etc., paste); SCALING.md ≤120 lines committed;
  RuntimeLog.
```

### PROMPT RS-4
```
TASK RS-4: LLM advisory hook (offline, optional analysis)

CONTEXT
- RESEARCH_05 §2: LLMs are advisory, never in the hot path. Behavior dataset exists (LW-3).

GOAL
scripts/advisors/llm_advisor.py: reads a day of data/observations/*.jsonl, produces a markdown
"coach report": per-race/class xp curves, anomaly bots (stalls, death loops), 3 suggested
parameter changes WITH the evidence lines. LLM endpoint/key from env (no key in repo); works
also in --stats-only mode with no LLM (pure aggregates) so it's useful offline.

ACCEPTANCE
- Run on a real dataset day: paste the report head; --stats-only mode works keyless (paste);
  RuntimeLog.
```

### PROMPT RS-5
```
TASK RS-5: 100-bot soak

CONTEXT
- RS-3 profiles ready. Servers on JDK25. Goal: first real scaling datapoint.

GOAL
Run --bots 100 for ≥4 h with the full watcher suite. Record every 30 min: fleet xp/min,
per-shard none (single JVM), engine RSS/threads, GS process CPU + top threads (jcmd), DB
threads, packet-loss/idle-timeout counters, dashboard latency. Output:
Documentation/RuntimeLogs/<date>-RS5-100bot-soak.md with a time-series table + verdict
(comfort | ceiling-approaching | broken-at-N).

ACCEPTANCE
- Soak log with ≥8 timepoints pasted; explicit ceiling verdict; any bot-stall incidents
  enumerated with causes; RuntimeLog.
```

### PROMPT RS-6
```
TASK RS-6: 500-bot ladder + ceiling report

CONTEXT
- RS-5 green or issues fixed. This task MEASURES, doesn't hope.

GOAL
Ladder 200 → 350 → 500 (each ≥90 min, same metrics as RS-5). Stop the ladder at the first
level where: GS CPU > 80% sustained, or xp/min per bot drops > 30% vs 100-bot baseline, or
packet loss > 1%. Final deliverable Documentation/UpgradePlan/SCALING_RESULTS.md: measured
curve, first-breaking component, recommendation (max single-JVM size, supervisor need y/n,
box specs if 2000 is the goal).

ACCEPTANCE
- SCALING_RESULTS.md with the ladder table + charts-as-tables; verdicts backed by pasted
  metric lines; RuntimeLog.
```
