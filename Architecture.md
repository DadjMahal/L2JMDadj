# 🏛️ ARCHITECTURE — The core of our basis

> Read this after `START_HERE.md`. This is the ONE place where the whole system is explained:
> what the project is, which parts exist, how AI players are built, how they integrate with
> the (vanilla) L2J server, and the hard lines that must never be crossed. Evergreen — state
> lives in `STATUS.md`, open work on `Documentation/TASKS.md`, rules in
> `Documentation/WORKFLOW.md`.

---

## 0. The product in one paragraph

**L24Lude = a Living Server.** A small cast of *smart AI citizens* (souls) that quest, party
with real humans, trade, schedule their lives and have memory — running **next to** a vanilla
L2JMobius Interlude server, so real players join and play *with* them. Quality over quantity:
**the Golden Five** (a handful of memorable souls on the owner's laptop) beat a thousand
anonymous grinders.

## 1. Repo anatomy (what lives where)

| Path | Nature | Rule |
|---|---|---|
| `SourceCode/` | L2JMobius Interlude **source** (java + datapack template) | ⛔ **READ-ONLY** ground truth. Read/parse its data; never edit. |
| `ServerBuild/` | Prebuilt Ant JARs + runtime (login :2106, game :7777) | ⛔ **READ-ONLY**. Never rebuild, never edit. |
| `AIPlayerEngine/` | **Our engine** (Maven, Java, JDK25). The AI lives here. | ✅ editable; all tests must stay green. |
| `AIWebDashboard/` | Dashboard front-end assets (SPA served by the engine) | ✅ editable |
| `scripts/` | Ops: launch/health/rotate/backup watchers, provisioning | ✅ editable; secrets via `fleet_env.local` |
| `Documentation/` | Docs: board, workflow, audits, runtime logs, archive | ✅ editable |
| `Architecture.md` / `START_HERE.md` | This doc + the orientation doc | ✅ editable |

## 2. The integration model — three planes, two hard fences

```
                 Internet (TLS only via reverse proxy)
        real players  │  browser/admin (ControlPlane UI)
            :2106/:7777│             │
        ┌──────────────▼─────────┐ ┌──▼────────────────────────┐
        │ GameServer JVM         │ │  ControlPlane (middleware)│ ← admin brain,
        │ (VANILLA core)         │ │  auth + audit + orchestration│  merges both APIs
        │ + scripts/custom/      │ └──┬───────────────┬────────┘
        │   ServerBridge.java    │    │ EngineAPI     │ ServerBridge API
        └────────────────────────┘    │ (:8210, loop) │ (:9300, loop, token)
        MariaDB (game + ai_souls)   ┌──▼──────────────▼────────┐
                                   │   AIPlayerEngine (own JVM)│
                                   │   bots speak REAL client  │
                                   │   packets — the ONLY      │
                                   │   gameplay path           │
                                   └───────────────────────────┘
```

- **AIPlayerEngine talks to the server exactly like a real client**: network sockets on the
  login/game ports, real packets in, real packets out. That is the **PlayerAPI** — the only
  gameplay path.
- **The server core stays 100% vanilla.** The sanctioned extension point is L2JMobius's
  runtime-compiled **datapack scripts**; a future `scripts/custom/ServerBridge` exposes server
  *truth* (read-mostly) + a whitelisted admin relay, loopback-only, token-gated, every call
  audited.
- **Four non-negotiable principles (detail: Audit 07):**
  1. **Client-parity gameplay only** — the bridge never moves/kills/grants items for gameplay.
  2. **ServerBridge is read-mostly + admin relay** — fail-closed, unknown command → refuse.
  3. **ControlPlane is the single brain** — auth, audit trail, kill-switch.
  4. **Zero trust** — every inter-process hop = loopback + bearer token; the only external door
     = TLS reverse proxy; MariaDB never exposed.

## 3. Engine internals — how a bot "thinks"

Per-bot loop (virtual threads since EP-7, ~300 ms tick):

---

## 4. The datastore — knowledge, learning, souls

| Store | Owner | Content |
|---|---|---|
| `AIPlayerEngine/src/main/resources/knowledge/` | engine | generated JSON (npcs/items/skills/quests/shops/chains/map) — produced by `scripts/datapack/` (GK-*) |
| `AIPlayerEngine/data/` | engine | runtime state: sessions, observations, progress (LW-*) |
| `logs/fleet/` | engine | rotated structured logs/events (`events.jsonl`) |
| MariaDB game schemas | server | vanilla server state (read-only for the engine, via bridge) |
| `ai_souls` schema | engine | soul identities, relationships, schedules (LI-*) |

All JSON has a frozen schema documented in `SCHEMAS.md` (GK-1); every change bumps a
version field; the validator (`validate.py`) runs before merge.

## 5. Development invariants (the "hard rules")

1. `SourceCode/` and `ServerBuild/` are **never edited** — the engine is external sockets only.
2. `mvn -o -f AIPlayerEngine/pom.xml test` must stay **green** before AND after every task.
3. **One task = one commit**, `type(scope): brief`, pushed to master; the TASKS.md row and a
   RuntimeLog are updated on every completion.
4. All secrets come from `scripts/fleet_env.local` (gitignored) or environment — zero
   hardcoded credentials.
5. AI decision logic belongs in `behavior/`/`knowledge/`/`learning/` — **never** in packet
   classes (keep transport pure data).
6. Evidence is real: actual command output is pasted, never fabricated.

## 6. The roadmap to the Living Server (phases P0–P9)

Every task for every phase lives on `Documentation/TASKS.md` (100 rows, phases, priorities):

- **P0 Foundations** — this doc + START_HERE core basis, gate scripts, hygiene.
- **P1 Quest pillar** — the live quest-progress/turn-in proof rows (S3-T02/T03).
- **P2 Engine & behavior core** — session/decision separation, personality hooks, chat, restock.
- **P3 Knowledge & datapack** — GK-* extractors → JSON → KnowledgeBase → chains/recommender.
- **P4 Intelligence & learning** — close the learning loop (bandit, utility gates, memory, gossip).
- **P5 Observability & watchers** — events.jsonl, WATCHER_RULES, persistence, no /tmp.
- **P6 Integration & APIs** — ServerBridge, PlayerAPI, EngineAdmin API, ControlPlane, TLS.
- **P7 Living Server & community** — souls schema, scheduler, relationships, mentors, events.
- **P8 Content coverage** — CO-* (party, shop, fishing, duels, crafting, clan…).
- **P9 Scale & proof** — KnowledgeService, autonomous bootstrap, 100/500 profiles, Golden Five.

## 7. Where to go next

- Orientation + run books: `START_HERE.md`
- The live task board: `Documentation/TASKS.md`
- Engine (package-level) architecture: `AIPlayerEngine/README.md`
- Server source/datapack map: `Documentation/SOURCE_CODE_MAP.md`
- Program research/audits: `Documentation/UpgradePlan/`
```
GameServer socket
  → protocol/PacketLogger (decode + self/world state)
  → core/BotSnapshot (per-tick world view) / core/GameStateMirror
  → per-bot tick (core/BotSession):
       goal ladder  QUEST > HUNT > RELOCATE > RESTOCK > IDLE > SLEEP
       planners → CombatFramePlanner → GameServerFrameWriter → packets out
```

- **Decisions** live in `behavior/` (+ `combat/`, `movement/`, `quest/`, `social/`, `humanize/`).
- **Knowledge** (static game facts) lives in `knowledge/`; the plan is to replace hardcoded
  tables with JSON extracted from the datacopy in `SourceCode/dist/game/data/` (tasks GK-*).
- **Learning** lives in `learning/` (`ReinforcementEngine`, `AdaptiveLearner`). The learning
  loop is currently **open** — the write path works, no decision maker consults it yet; closing
  it is the IN-* wave.
- **Observations** come from the dashboard events pipeline: `web/EventRing` → (future)
  `logs/fleet/events.jsonl` + watchers (LW-*) + `data/observations/`.
- **Souls** (the Living Server layer) get their own identity in the DB (`ai_souls`), a
  wake/sleep schedule, relationships and human-presence awareness — LI-*/EN-* tasks.