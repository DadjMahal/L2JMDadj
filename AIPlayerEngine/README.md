# AI Player Engine

External-socket AI player system for the L2JMobius **Interlude** server. Connects to the running
server as normal client sockets — **no server code modifications**.

## Honest status (2026-08-02)
- **Compiles** (`mvn -f AIPlayerEngine/pom.xml compile` → BUILD SUCCESS, 155 files).
- Combat / Quest / Merchant / Social AI exist but use **mock data** — **not connected to real gameplay**.
- Live connection path exists (`AIPlayerManager.spawnAIPlayer → AIPlayer.connectToServer`) but is
  **unverified**: no session has proven AI players actually online/in-game.
- Deep/neural/advanced/social/economy classes (~100) compile but are **largely unwired**.

> Source of truth: `AIStatusLogs/ai_progress_report.txt` + `scripts/real_status.sh`.
> Do NOT trust any "✅ COMPLETE" doc in `Documentation/_archive_fabricated/`.

## Build
```bash
cd /home/volodro/L2JM/AIPlayerEngine && mvn clean compile
```

## Run (servers must be up first)
```bash
cd /home/volodro/L2JM && ./StartServer.sh
mvn -f AIPlayerEngine/pom.xml exec:java -Dexec.mainClass=com.aiplayer.engine.AIPlayerEngine -Dexec.args=--spawn-all
```

## Real status check (no fabricated data)
```bash
/home/volodro/L2JM/AIPlayerEngine/AIStatusLogs/real_status.sh
```

## Agent entry points
- Project orientation: `../START_HERE.md`  • Task board: `../TASKS.md`  • Rules: `../AGENT_ONBOARDING.md`
