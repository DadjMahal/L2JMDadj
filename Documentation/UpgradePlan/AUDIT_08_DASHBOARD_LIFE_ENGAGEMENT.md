# ✨ AUDIT 08 — The Living Server: souls, dashboard control & player engagement

> Owner vision (interpreted at full ambition): *"A really cool Interlude server where regular
> players join and play WITH AI players and have fun. AI players have their own space, their own
> process power and timers to wake up or sleep, freedom + random generation. Dashboard to
> monitor and admin (create/disconnect/connect players). Bring AI+human gameplay to more fun and
> engagement. Start with 1-5 truly smart bots on a 15GB/i5-11th-gen laptop, free-tier LLM ok."*

## 1. The product thesis

The server should feel **inhabited**. Not "50 mobs farming" — a small cast of *citizens* with
names, moods, schedules, careers, and relationships with real players. Quality over quantity:
**five memorable souls beat fifty anonymous grinders** (and that is exactly what the laptop
supports). Every design below serves one question: *would a real player log in tomorrow hoping
to meet them again?*

## 2. The Soul model — AI's own space (owner: "own database space")

New schema `ai_souls` (engine-owned, same MariaDB instance, separate from game schemas):

```
soul(id, name, race, class_path, birth_ts, seed, personality_json,  // traits, voice, style
     persona_version, is_active)
schedule(soul_id, circadian_json,      // wake windows, play hours, days off, jitter
         next_wake_ts, next_sleep_ts)
career(soul_id, chain_progress_json, skills_goals, craft_books, wealth_goal)
memory(soul_id, hunting_ewma_json, spots_json,                      // learned play
       humans_json)                  // {playerName: {met, helped, gifts, relation}}
state_mirror(soul_id, level, adena, location, updated_ts)           // dashboards only
```

- **Identity is permanent**: a soul respawns into the same character forever (character =
  the soul's body; session resume from LW-5 keeps progress).
- **Personality drift is bounded** (BR-6 weekly LLM reflection may nudge traits ±10% — a soul
  can grow, never mutate).
- Souls are visible on the dashboard as identity cards: name, race/class, age, mood, schedule,
  career arc, relationships.

## 3. Life scheduler — "own process power and timers" (wake/sleep)

Engine-side `SoulScheduler` (a tiny service in the engine JVM — no extra processes on the laptop):

1. Each soul has a **circadian schedule** (e.g. "evenings + weekends, 2-4h sessions") with
   random jitter and rare "life events" (day off, fishing mood, market day).
2. Scheduler wakes souls via `PlayerApi.connect(soul)` and puts them to sleep (clean logout in
   town) — sessions persist (LW-5), so waking = resuming a life, not rebooting a bot.
3. **Population policy**: min/max active souls; optional **prime-time coupling**: when bridge
   `/v1/players` shows humans online, wake more souls (server feels alive exactly when it
   matters); when empty, most souls sleep — saving laptop resources honestly.
4. Manual override anytime from the dashboard ("spawn whenever we want").

## 4. Dashboard evolution (from telemetry → mission control)

| Page | Today | Target |
|---|---|---|
| Map + Grid | ✅ live bots | + soul badges (mood/persona icon), human players via bridge overlay |
| Ops | ✅ health, TIM-001 | + Coverage page (CO-1), + audit-log viewer (BR-4), kill switches |
| **Souls** (new) | – | identity cards, career arcs, schedules, wake/sleep controls, relationship lists |
| **Control** (new) | – | spawn/connect/disconnect soul, fleet policy, event host buttons (EN-2) |
| **Community** (new) | – | leaderboard merge (raid points/fishing via bridge), recent parties/conversations |

Admin actions flow ONLY through ControlPlane (BR-4) — audited, token-gated, proxied (BR-5).

## 5. Engagement portfolio (what makes it FUN — ranked for laptop phase)

| # | Feature | Why it hooks real players | Cost |
|---|---|---|---|
| EN-1 | **Party buddies** (CO-3) | never LFG alone; bots that assist, follow, loot politely | flagship |
| EN-2 | **Newbie mentors** | greet new players, gift a starter bag, escort to first hunt spot, answer "where…?" (CO-4+KnowledgeService) | small |
| EN-3 | **Citizen economy** (CO-2/6) | town market stalls with souls' names; dwarfs craft; bots BUY materials from humans | medium |
| EN-4 | **Relationships** (LI-3) | souls remember names/favors, greet regulars, friendship perks (better prices) | small, huge charm |
| EN-5 | **AI-hosted events** (EN-2 tasks) | trivia hour with mailed prizes, duel nights (CO-8), fishing rivalry (CO-7) | medium |
| EN-6 | **Leaderboard rivals** | souls climb raid-points/fishing boards legitimately — humans have someone to catch | near-free |
| EN-7 | **Transparency policy** | decide labeling ("AI companions" badge vs incognito) + community rules; trust = retention | decision + doc |

Deliberately deferred (fleet-scale, revisit after hardware upgrade): sieges/clan wars,
Olympiad, Seven Signs factions, cursed-weapon drama (audit 06 rows 20-22).

## 6. Laptop reality check (15 GB RAM, i5 11th gen)

| Component | Budget | Note |
|---|---|---|
| OS + desktop | ~2.5 GB | |
| MariaDB | 0.5 GB | game schemas + ai_souls |
| GameServer | 2-4 GB (-Xmx3g) | dominant CPU (AI think ticks, geodata) |
| Engine: 5 souls + ControlPlane + watchers | 0.75-1 GB | virtual threads; knowledge JSON shared |
| Caddy proxy | ~30 MB | TLS termination |
| **Total** | **~7-8 GB** | headroom ~7 GB — comfortable, NOT a 50-bot box anymore |

Rules for this phase: 5 souls max (2-3 active concurrently), tick 500 ms, LLM = external free
tier (BR-6 budgets), watchers at 60 s+. The old 50-bot farming fleet is a *test mode*, not the
product. Measure before growing (RS-5/RS-6 ladder generalizes).

## 7. Milestone: **The Golden Five** (definition of success)

Five named souls — e.g. a Human mentor-knight, a Dwarf crafter-merchant, an Elf archer-fisher,
a Dark Elf mystic duelist, an Orc brawler-party-animal — running **one week unattended**:
- each: ≥1 quest arc advanced, ≥1 party with a human, ≥1 store sale or craft, ≥5 chat
  conversations, honest sleep/wake schedule;
- server: 0 crashes, audit log clean, dashboard fully operable from outside over TLS;
- the human test (the owner + a friend): *"I logged in, they recognized me, we played, it was
  fun."* That sentence is the product. Everything in this plan serves it.

## 8. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| DA-1 | Engine admin API: spawn/connect/disconnect/policy + audit | M | EP-4 | TODO |
| DA-2 | Dashboard pages: Souls, Control, Community, audit viewer | M | DA-1, BR-4 | TODO |
| LI-1 | ai_souls schema + soul identity generator (birth certificates) | M | – | TODO |
| LI-2 | SoulScheduler: circadian wake/sleep + population policy (+human-presence coupling) | M | LI-1, BR-1 | TODO |
| LI-3 | Relationships: souls remember humans (greeting, favors, perks) | M | LI-1, CO-4 | TODO |
| EN-1 | Mentor behavior pack (greet/gift/escort/answer) | M | LI-3 | TODO |
| EN-2 | Event host: trivia hour + duel night + mailed prizes | L | BR-2, CO-8 | TODO |
| EN-3 | Golden Five integration: cast, week-long soak, verdict report | L | all | TODO |

### PROMPT DA-1
```
TASK DA-1: Engine admin API

CONTEXT
- Audit 07 §2 EngineAPI admin routes; Audit 08 §4. Today DashboardApi is read-only telemetry.

GOAL
POST /api/v1/admin/{spawn,connect,disconnect,setPolicy,stopFleet} on DashboardApi (loopback,
token-gated): spawn{count|soulIds} → creates sessions via PlayerApi (RS-2 identity path),
connect/disconnect{soulId} (clean logout in town when possible), setPolicy{pvp, exploration,
llm…}, stopFleet. Every call: audit line to logs/control/audit.jsonl + EventRing event.
Idempotency keys. Unauthorized → 401 + audit.

ACCEPTANCE
- Tests green (+auth/idempotency/audit tests); live: spawn 1 → disconnect 1 → audit lines and
  dashboard events pasted; RuntimeLog.
```

### PROMPT DA-2
```
TASK DA-2: Mission-control dashboard pages

CONTEXT
- DA-1 + BR-4 backends ready; existing dashboard assets under AIPlayerEngine resources
  (index.html map/grid, ops.html).

GOAL
souls.html: identity cards (name/race/class/age/mood/schedule/relationships), wake/sleep/now
buttons, career arc sparkline. control.html: fleet policy form, kill switches, coverage page
(CO-1 YAML render), audit viewer (last 200). community.html: merged leaderboard (bridge raid/
fishing), recent parties/conversations feed. All via ControlPlane; no direct engine/bridge
calls from the browser; ops-style zero-dependency pattern kept.

ACCEPTANCE
- Pages served behind proxy auth (BR-5); happy path screenshots-as-text (key DOM rows) pasted
  for each page with 3 live souls; no token leaks in client JS (grep); RuntimeLog.
```

### PROMPT LI-1
```
TASK LI-1: Souls are born — schema + identity generator

CONTEXT
- Audit 08 §2 schema. RS-2 already creates characters; souls OWN their identity permanently.

GOAL
1. SQL: ai_souls schema (§2 tables) + migration script scripts/souls/init_souls.sql; engine
   SoulStore (DAO, HikariCP-style pooling or simple DataSource) with tests on a temp schema.
2. SoulFactory: from (race, styleSeed): name (per-race syllable lore tables, collision-safe),
   personality (traits: bravery/greed/chattiness/risk + voice style), circadian defaults,
   career goals from chains.json. Deterministic from seed — reproducible souls. Writes the
   birth certificate (one JSON line in logs/souls/births.jsonl).

ACCEPTANCE
- Tests green (+round-trip, +determinism, +name-collision); generate 5 souls incl. the Golden
  Five cast, paste their certificates; RuntimeLog.
```

### PROMPT LI-2
```
TASK LI-2: SoulScheduler — wake, sleep, live

CONTEXT
- LI-1 souls exist; PlayerApi connect/disconnect ready; bridge /v1/players shows humans.

GOAL
SoulScheduler service (engine): per-soul circadian engine (wake windows + jitter + life events:
day_off, fishing_mood, market_day), population policy {minActive, maxActive, primeTimeCouple:
humans>0 → bias wake}, manual overrides queue from DA-1. Wakes souls staggered (login backoff
reuse S2-T07), sleeps them (walk to town → clean logout) at window end. Dashboard Souls page
shows next_wake/next_sleep; all transitions audited + evented (type=soul_wake/soul_sleep).

ACCEPTANCE
- Tests green (+circadian unit tests, +policy coupling with mocked bridge); 48 h live: paste
  the wake/sleep timeline of 3 souls + one prime-time coupling moment (humans online → extra
  soul woke); zero stuck sessions at sleep; RuntimeLog.
```

### PROMPT LI-3
```
TASK LI-3: Souls remember people

CONTEXT
- memory.humans_json (LI-1); chat pipeline (CO-4) gives names/text; relationship = the charm
  layer (Audit 08 §5 EN-4).

GOAL
RelationshipEngine: on meaningful interaction (party together, gift, helped in chat, bought
from store) update humans_json[name] {met_count, last_seen, relation −100..100, notes}. Drives:
greeting by name + last-context line, friend pricing in stores (−10%), mentor priority,
farewell when the human logs out (bridge event or friend-list). Privacy: names only, no chat
transcripts stored beyond 20-line rolling context.

ACCEPTANCE
- Tests green (+relation updates, +decay over weeks); live script with the owner: party → log
  out → next day bot greets by name with context (paste chat); RuntimeLog.
```

### PROMPT EN-1
```
TASK EN-1: The mentor pack

CONTEXT
- LI-3 + CO-3/CO-4 done. New-player retention is the #1 private-server problem; souls fix it.

GOAL
MentorBehavior (a career/persona slot): detects newbie (level ≤ 10 human near start town via
bridge/packets) → greeting whisper with offer, gift starter bag (from mentor's own stock:
potions + early weapon via CO-2 store or trade window), escort to first hunt anchor, answer 3
common questions via KnowledgeService (where X drops, where to level, what quest next). Rate
limits: one mentee at a time, cooldown, never follows into PvP.

ACCEPTANCE
- Tests green (+trigger conditions, gift economics); live: fresh level-1 human account walked
  through first 15 minutes by a mentor soul (paste whisper/chat timeline + gift evidence);
  RuntimeLog.
```

### PROMPT EN-2
```
TASK EN-2: AI-hosted events

CONTEXT
- Bridge relay (BR-2 announce), mail system server-side, duels (CO-8), trivia content from
  KnowledgeService facts.

GOAL
EventHost service: scheduled events (dashboard button + cron): (1) Trivia Hour — soul asks
questions in shout channel, first correct answer via whisper wins a mailed prize (bridge/admin
mail path — verify mail sending mechanism server-side first, document in RuntimeLog);
(2) Duel Night — soul hosts arena, pairs participants, referees, mails prizes; (3) Market Day —
souls stock rare goods at discount. Event scripts in ai_souls/event_log for post-mortems.

ACCEPTANCE
- One full Trivia Hour live with ≥1 human participant (paste chat log + mail delivery
  evidence); duel-night dry-run with 2 souls (paste); RuntimeLog.
```

### PROMPT EN-3
```
TASK EN-3: The Golden Five — integration week

CONTEXT
- All lanes above done (or explicitly deferred with a note). This is the product release test.

GOAL
1. Cast the five souls (mentor-knight, dwarf crafter-merchant, elf archer-fisher, DE mystic
   duelist, orc brawler), distinct races/classes/schedules/personas.
2. 7-day unattended run under real conditions: watchers + LLM nightly coach + scheduler own
   the fleet; owner plays 15 min/day as the human.
3. Daily auto-report (watcher-generated): per-soul quests/parties/sales/chats/sleep honesty +
   incidents. Final verdict report against Audit 08 §7 criteria — honest PASS/FAIL per line.

ACCEPTANCE
- 7 daily reports + final Golden Five verdict doc committed (Documentation/UpgradePlan/
  GOLDEN_FIVE_REPORT.md); every §7 criterion has evidence or an honest FAIL with cause;
  RuntimeLog.
```
