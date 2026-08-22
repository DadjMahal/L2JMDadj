L2JMobius fork by Dadj Mahal.
I'm trying to be good but God's Devil inside mind make my crazy.
Hope 4 Readhead, touch my tralolo one more time and it will become your final $PATH. 
Day by day military by military each nano tanto manto dandy mandy pizdi vizdi. 
Doebeshsja do menja ja do tebja doebusia.
Papa skazal ia ne messiah, to ia Dadj.
Moje vypravlus ale v mene REST.

---

# 🖥️ Web Panel (AI Fleet Dashboard)

> **AI agents start at `START_HERE.md`** (orientation → `Documentation/WORKFLOW.md` rules →
> `Documentation/TASKS.md` open work). The rest of this README is the dashboard/API face
> for human operators.

The fleet launcher (`AIPlayerEngine/.../examples/FleetPlay.java`) serves a live single-page
dashboard on **http://localhost:8080/**.

| View | URL | Source asset | Purpose |
|---|---|---|---|
| Map + Grid | `/` | `AIPlayerEngine/src/main/resources/dashboard/index.html` | live bot positions, targets, stats |
| **Ops** | `/ops.html` | `AIPlayerEngine/src/main/resources/dashboard/ops.html` | health cards, event feed, config, **TIM-001 stagnant-bot detector** |
| API | `/api/v1/*` | `com.aiplayer.web.DashboardApi` | frozen JSON contract (routes below; tests lock the shape) |
| Host health | (CLI) | `scripts/server_health.sh` | ports 2106/9014/7777 + DB pings + character/account counts |

## API routes (v1 contract — frozen)
```
GET /api/v1/bots       -> {"bots":[{account,charId,name,level,exp,hp,x,y,z,state,online,...}]}
GET /api/v1/entities   -> {"entities":[{objId,kind,label,x,y,z}]}
GET /api/v1/landmarks  -> {"towns":[{name,x,y,z}]}
GET /api/v1/events     -> {"events":[{seq,t,type,bot,data}]}
GET /api/v1/health     -> {"status","uptimeSec","botCount","onlineCount","requestCount","routes"}
GET /api/v1/config     -> {"fleetSize","wanderRadius","wanderIntervalMs","pollMs","bind","tokenAuth"}
```
Legacy endpoints `/json` and `/report` keep the pre-v1 SPA shape for compatibility.

## Operations
```bash
./scripts/server_health.sh   # ports + DB + character/account snapshot (exit 0 = healthy)
./scripts/e2e_dashboard.sh   # end-to-end dashboard/API smoke test
./StartServer.sh             # requires JDK 25 on PATH (see below)
```
> **Java note:** the server JARs in `ServerBuild/libs/` are compiled for **JDK 25** (class file
> 69.0). The system default JDK is 21, so start the server with JDK 25 on PATH, e.g.
> `PATH="$HOME/.jdk/jdk-25.0.4+7/bin:$PATH" ./StartServer.sh`.

## Architecture (one line)
External-socket AI players (no server source changes): `LoginServer` auth → `GameServer` enter-world
→ real packet I/O through `com.aiplayer.protocol` (L2JProtocol + PacketLogger), decisions from
`com.aiplayer.behavior` (BotBrain, BotPlayController, CombatAI etc.), one virtual-thread session
per bot via `com.aiplayer.core.BotSession`, state surfaced over HTTP to this web panel
(`com.aiplayer.web.DashboardApi` + `DashboardBoot`; LAN exposure requires `DASH_TOKEN` — EP-6).
