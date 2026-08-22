# AI Player Engine

External-socket AI player system for the L2JMobius **Interlude** server. The engine connects to
the running Login/Game server as normal client sockets — **no server source modifications**
(`SourceCode/`, `ServerBuild/` are never edited).

Current state: **415/415 tests green**; a 50-bot mixed-race fleet farms organically with a live
web dashboard (see `../START_HERE.md` §1 for the exact bring-up commands).

> 🏛️ **Core basis:** for the three-plane integration model, the hard lines and the full
> roadmap, read **`../Architecture.md`** (repo root) — this README is the engine-level detail.

## Architecture

```
                ┌────────────────────────────────────────────────────────┐
                │                    examples/FleetPlay                  │
                │   thin launcher: parse → boot dashboard → spawn fleet  │
                └──────┬──────────────────────────────┬──────────────────┘
                       │                              │
        ┌──────────────▼────────────┐    ┌────────────▼─────────────────┐
        │ core/FleetConfig          │    │ web/DashboardBoot            │
        │ args + tuning knobs       │    │ + web/DashboardApi (/api/v1) │
        └──────────────┬────────────┘    └────────────▲─────────────────┘
                       │ one virtual thread per bot   │ reads shared rings
        ┌──────────────▼──────────────────────────────┴─────────────────┐
        │ core/BotSession  (the session machine)                        │
        │  login → enter-world → tick loop: quest dialog driver,        │
        │  combat chase, potions, relocation, survival guards,          │
        │  reconnect backoff + death guard        writes → core/BotInfo │
        └──┬──────────────────────────────────────────────┬────────────┘
           │ packets                                    │ decisions
┌──────────▼──────────┐   ┌────────────────────┐   ┌────▼─────────────────────┐
│ protocol/           │   │ net/               │   │ behavior/ (+10 domain    │
│ L2JProtocol,        │──▶│ GameServerClient,  │   │ subpackages: combat,     │
│ PacketLogger        │   │ AIPlayer           │   │ quest, movement, party…) │
└─────────────────────┘   └────────────────────┘   └────┬────────────────────┘
                                                        │ consults
                                        ┌───────────────▼──────────────┐
                                        │ knowledge/  learning/        │
                                        │ static game data, self-impro │
                                        └──────────────────────────────┘
```

Monitoring: `monitor/` (AIMonitorDashboard, AILogCollector), `metrics/` (PerformanceMetrics).
The live event/state rings (`EventRing`, `HistoryRing`, `FleetMetrics`) live in `web/` (shared by
the dashboard API and watchers). A standalone headless entry point lives in `cli/AIPlayerEngine`.

## Package map (old → new)

The `phase0.*` namespace was purged in EP-3; `engine/` (EP-1/EP-2) is gone. If an old doc or
commit talks about these paths, translate:

| Old | New |
|---|---|
| `phase0.play.*` | `behavior/` root + `behavior/movement/`, `behavior/hunting/` |
| `phase0.brain.*` | `behavior/` root (BotBrain, ClassPreset, StateMachine) |
| `phase0.combat.*` | `behavior/combat/` |
| `phase0.quest.*` | `behavior/quest/` |
| `phase0.social.*`, `phase0.chat.*` | `behavior/social/` |
| `phase0.town.*` | `behavior/town/` |
| `phase0.party.*` | `behavior/party/` |
| `phase0.inventory.*` | `behavior/inventory/` |
| `phase0.movement.*` | `behavior/movement/` |
| `phase0.humanize.*`, `phase0.imperfection.*` | `behavior/humanize/` (Humanization nests the lows) |
| `phase0.death.*` | `behavior/lifecycle/` |
| `phase0.farm.*` | `behavior/hunting/` |
| `phase0.guide.*` | `knowledge/` |
| `phase0.neural.*`, `phase0.advanced.*` | `learning/` |
| `phase0.cabinet`, `phase0.director` | `core/BotProfile`, `behavior/Director` |
| `engine/*` (live classes) | `net/`, `core/`, `cli/`, `behavior/*` |
| `Phase0Config` / `Phase0Wiring` / `Phase0Integration` / `Phase0Driver` | `EngineConfig` / `CoreWiring` / `EngineWiring` / `EngineDriver` |
| config keys `phase0.*` | `engine.*` (src/main/resources/config/ai-player.properties) |

Dead pre-refactor classes live in `AIPlayerEngine/attic/` (not compiled) — do not resurrect lightly.

## Build / test / run

```bash
# tests (must stay green: 415/415)
cd /home/dadj/Projects/l24lude && mvn -o -f AIPlayerEngine/pom.xml test

# fleet + dashboard (servers up first; secrets from scripts/fleet_env.local — see EP-6)
scripts/fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN
# dashboard: http://<host>:8210/?token=<DASH_TOKEN>   ops: scripts/health_check.sh 50
```

Config: `src/main/resources/config/ai-player.properties` (keys `engine.*`, `bot.*`).
Secrets (bot password, DB, dashboard token) come from `scripts/fleet_env.local` — never committed.

## Learn more
- **Core basis (three planes, hard lines, roadmap): `../Architecture.md`**
- Orientation + hard rules: `../START_HERE.md`
- Task board: `../Documentation/TASKS.md` • UpgradePlan: `../Documentation/UpgradePlan/README.md`
- Runtime evidence: `../Documentation/RuntimeLogs/` (one file per task)
