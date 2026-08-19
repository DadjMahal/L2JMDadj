# 🚀 UPGRADE PLAN — Smart AI Engine (2026-08-19)

> Owner-directed program (Dadj, 2026-08-19). It **supersedes the old "no new audits" rule for
> this initiative only** — the audits below were ordered by the owner and are one-time planning
> artifacts, not a return to audit-driven development. Execution goes back to
> one-task-one-commit on the board once these tasks are claimed.

## 0. The goal (owner's words, structured)

1. **Clean up the project, especially `AIPlayerEngine`** — architecture/folders clear to a human
   developer; no `phase0`-style naming; best practices + security audit; fewer files via logical
   merging; perfect documentation.
2. **Smarter log & watcher system** — rules so Deepseek (Cline) can create proper watchers;
   watchers observe HOW bots play and write structured data; refactor the engine's
   session/progress/task files.
3. **Deep dive into `SourceCode` game data** — improve the Guide Map and build a full knowledge
   base for bots: (a) entire quest path zero→hero, (b) items/builds per race+profession+level,
   (c) all NPCs + locations + priorities, (d) which NPC drops which item.
4. **Deep review of the AI engine itself** — smarter in-game decisions, real self-learning,
   perfect log/watcher integration, not everything hardcoded.
5. **Research direction** — self-learning engine; possibly a knowledge *middleware* (bots ask
   "what's my next quest / nearest NPC / where does item X drop?") instead of huge hardcoded
   tables; fully autonomous bootstrap (bot connects itself, picks random race/style/name, starts
   playing); scale by one input "launch 10/100/500/2000"; bots navigate the game via SourceCode
   data like a real client.
6. **Full-content integration (added 2026-08-20)** — AI players must observe and play ALL game
   content in SourceCode; three-plane API architecture (Server-side API via sanctioned datapack
   scripts — core stays vanilla; Player-side API; ControlPlane middleware to shake hands);
   available from outside but absolutely secured.
7. **The Living Server (added 2026-08-20)** — the real product: a cool Interlude server where
   regular players join and play WITH AI citizens and have fun. Souls with own space (DB),
   wake/sleep timers, freedom + random generation; dashboard with admin rights (spawn/
   connect/disconnect) and engagement perks. **Hardware phase 1 = owner's laptop (15 GB RAM,
   i5 11th gen): 1-5 truly smart bots, free-tier LLM advisory.** Success milestone:
   "The Golden Five" (AUDIT_08 §7). Scale to big fleets only after the laptop phase proves fun.

## 1. Document index

| File | Maps to goal | Content |
|---|---|---|
| `DEEPSEEK_PROMPT_TEMPLATE.md` | all | The standard prompt wrapper every task prompt below is built from |
| `AUDIT_01_ENGINE_CLEANUP.md` | 1 | Audit of AIPlayerEngine architecture/naming/dead code/security + task prompts `EP-*` |
| `AUDIT_02_LOGS_WATCHERS.md` | 2 | Audit of logging/watchers/session-progress + task prompts `LW-*` |
| `AUDIT_03_GAMEKNOWLEDGE.md` | 3 | Datapack inventory + knowledge-base design + task prompts `GK-*` |
| `AUDIT_04_INTELLIGENCE.md` | 4 | Decision/learning review + task prompts `IN-*` |
| `RESEARCH_05_SELFLEARNING_MIDDLEWARE.md` | 5 | Big research audit: middleware vs DB, self-learning design, autonomous bootstrap, scaling + task prompts `RS-*` |
| `AUDIT_06_SERVER_CONTENT_COVERAGE.md` | 6 | Full SourceCode content inventory × AI capability matrix (24 rows) + task prompts `CO-*` |
| `AUDIT_07_INTEGRATION_APIS.md` | 6 | Three-plane architecture (ServerBridge script / PlayerAPI / ControlPlane) + security model + task prompts `BR-*` |
| `AUDIT_08_DASHBOARD_LIFE_ENGAGEMENT.md` | 7 | The Living Server: souls, life scheduler, dashboard control, engagement portfolio, laptop budget, Golden Five + task prompts `DA-*, LI-*, EN-*` |

## 2. Hard rules for every task (repeat inside each Deepseek prompt)

1. `mvn -o -f AIPlayerEngine/pom.xml test` must be **green before and after** (currently 383 tests).
2. **Never modify server source**: `SourceCode/` and `ServerBuild/` are read-only ground truth
   (reading/parsing their data files is allowed and is the whole point of the GK-* tasks).
3. **One task = one commit**, format `type(scope): brief`, pushed to `master` right after.
4. **No fake logs/evidence** — paste real command output in the RuntimeLog.
5. After each task: write `Documentation/RuntimeLogs/<date>-<task-id>-<slug>.md` (≤70 lines) and
   update this plan (mark the task `DONE-PUSHED <hash>` in its status table).
6. Also mirror claimed tasks onto the live board `Documentation/TASKS.md` (§3 roadmap) so parallel
   agents don't collide — add a row `UP-<ID> (UpgradePlan) IN_PROGRESS (deepseek)`.

## 3. Execution order (dependencies)

```
Wave 1 (foundations, parallel-safe):
  EP-1 archive legacy engine/*      LW-1 log schema + LW-2 WATCHER_RULES.md
  GK-1 extraction pipeline skeleton IN-1 learning-loop census (small)
  LI-1 souls schema + identity      BR-1 ServerBridge (read-only server truth)
Wave 2 (structure):
  EP-2..EP-6 renames/merges/security GK-2..GK-5 extractors (npc/quest/item/spawn)
  LW-3..LW-5 watchers + dataset     RS-1 knowledge service API
  DA-1 engine admin API             BR-2 bridge admin relay
Wave 3 (intelligence on the new structure):
  GK-6..GK-9 loaders + consumers    IN-2..IN-5 closed-loop learning
  RS-2..RS-4 bootstrap + scale      EP-7 docs unification
  CO-1..CO-4 presence cluster (stores, human parties, chat)
Wave 4 (the Living Server — laptop phase):
  BR-3..BR-5 PlayerAPI + ControlPlane + TLS   DA-2 dashboard pages
  LI-2 SoulScheduler  LI-3 relationships      CO-5..CO-8 (warehouse/craft/fishing/duels)
  BR-6 LLM advisory   EN-1 mentors  EN-2 events
Wave 5 (proof):
  EN-3 Golden Five week  RS-5 100-bot soak  RS-6 500-bot profile  CO-9 coverage suite
```

Rule of thumb: **EP-* and GK-* first** (they change the terrain everything else stands on),
**LW-* interleaves freely**, **IN-* and RS-* build on both**, and the **Living Server wave
(4)** is where the laptop phase product takes shape — Golden Five (EN-3) is the release gate
before any fleet-scale ambition (RS-5/RS-6).

## 4. Status vocabulary

`TODO` · `IN_PROGRESS (agent)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>` — same as the board.

## 5. Key audit facts (shared context, cited once)

- `AIPlayerEngine` main: **320 files / 45,402 LOC**; tests: 67 files (383 tests green).
- `engine/` legacy pile = **141 files, of which 131 have ZERO references** from live code
  (FleetPlay/phase0/web). Used ones: CombatAI, AIBrain, AIPlayer, GameServerClient,
  CombatFramePlanner, CombatDecision, Phase0Integration, Phase0Config, AIPlayerEngine,
  AIConfiguration.
- God classes: `protocol/PacketLogger.java` 2,190 lines; `examples/FleetPlay.java` 1,387 lines
  (contains the whole per-bot `BotLoop` session machine); `engine/CombatAI.java` 742;
  `phase0/quest/QuestDatabase.java` 699; `web/DashboardApi.java` 694; `phase0/guide/RaceGuide.java` 679.
- 15 deprecated probe classes in `examples/`, 8+ still default password `ai123pass` in source.
- Dashboard: `tokenAuth` defaults **false**; fleet runs bound to LAN (`:8210`) — unauthenticated.
- Learning loop is **open**: `ReinforcementEngine.rewardKill()` is fed on every kill
  (FleetPlay:640), but `getBestStrategy()`/`predict*()` are **never called** by any live decision.
- Threading: classic `new Thread(`/fixed pools — no virtual threads despite JDK 25.
- Quest progress persistence: `QuestProgressTracker.saveToRedis()/loadFromRedis()` are documented
  no-ops → **progress resets on restart**.
- Watchers: `scripts/watch_fleet.py` polls `/json`, writes human lines (or JSONL) to
  **/tmp** (lost on reboot), race guessed from account-name index; logs land in
  `/tmp/fleet_launch_<prefix>.log` — also /tmp.
- Datapack (`SourceCode/dist/game/data/`): **346 quest scripts** (Java, extractable
  giver/talk NPC ids, items, rewards), 83 NPC stat XMLs with `<dropLists>`, 95 item XMLs,
  34 skill XMLs, 186 spawn XMLs, 616 buylists, 93 multisells, 6 teleporter files,
  3,082 HTML dialogs, `SkillLearn.xml` class trees.
- Engine knowledge today: RaceGuide 679 hard-coded lines, QuestDatabase ~49 entries,
  VendorDatabase ~8 vendors, ItemDatabase 168 lines, SkillDatabase 143 lines — a rounding
  error vs the datapack.
- Server-side systems (verified): **43 managers** (sieges, duels, manor, fishing championship,
  raid/grand bosses, cursed weapons, boats, walking routes…), **42 Custom features**
  (FakePlayers, SellBuffs, Wedding, Banking, OfflineTrade…), community board with mail/forums,
  215 client / 279 server packet types, 89 AI scripts, 380 handlers, 16 village masters.
  Admin commands are scriptable datapack handlers — the sanctioned extension point used by
  AUDIT_07's ServerBridge (server core stays vanilla).
