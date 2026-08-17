# 🚀 START HERE — read first, every session, every Cline instance

> Fast binary orientation for any agent/session in this repo. Depth = only the files the routing
> table (§3) links to. The ONLY task board is `Documentation/TASKS.md` — pick work there.

## 0. The ONE goal
Make 3–5 AI player bots **actually PLAY the game** — fight, level, travel, pass quests — **never idle**.
No more audits.
**Current state (1-liner):** **374/374 tests green**, Sessions 1 & 9 complete (code hygiene +
ops), **36/100 board tasks done**, 50 random-race bots farming live (dashboard :8210),
server stack on JDK25. All prior historical/audit/evidence docs are archived — do not redo them.

## 1. Run it (bring the fleet to life)
```bash
# Login/Game on JDK25 (system java is JDK21; server JARs need JDK25)
export PATH=~/.jdk/jdk-25.0.4+7/bin:$PATH
cd /home/dadj/Projects/l24lude/ServerBuild/login && ./LoginServerTask.sh
cd /home/dadj/Projects/l24lude/ServerBuild/game  && ./GameServerTask.sh

# 50 random-race bot fleet + web dashboard (Servers must be up first)
cd /home/dadj/Projects/l24lude && scripts/fleet_launch.sh 50 8210 ai_rand_ 500000 ELF,DARK_ELF,ORC,DWARF,HUMAN
# open http://<host-ip>:8210  (not localhost from another machine)
# ops during the run: scripts/health_check.sh 50   scripts/rotate_logs.sh   scripts/keep_alive.sh   scripts/backup_db.sh
# stats: scripts/watch_fleet.sh http://localhost:8210/json /tmp/watch_fleet.log 60 120
```
Build/verify:
```bash
cd /home/dadj/Projects/l24lude/AIPlayerEngine && mvn -o compile && mvn -o test
```

## 2. Active lanes (the board pointers to drive next)
- **Sessions 1 & 9 complete.** Next: **Session 10 remnants** (archive legacy probes/PARTIAL decision,
  config consolidation, doc final sync), then **the live P0 cluster** — S3 live quest accept→complete→
  turn-in, S5 solo-bot relocation dead-end (the fleet's periodic idle-wall), S7 town/economy wiring.
  Watch progress on `Documentation/TASKS.md` (36/100 done).

## 3. Routing table (live files only)
| You want to touch | Read first |
|---|---|
| Fleet launcher / fleet behavior | `examples/FleetPlay.java` |
| Phase0 wiring | `phase0/Phase0Wiring.java` |
| Live bot state | `phase0/BotSnapshot.java` |
| **BotPlay controller (new)** | `phase0/play/**` (STEP 1) |
| Quest NPC navigation | `phase0/quest/QuestNpcNavigator.java` |
| **Task board** | **`Documentation/TASKS.md` (always)** |

## 4. Hard rules
1. **Never edit server source** (`SourceCode/`, `ServerBuild/`) — the engine is external sockets only.
2. `mvn -o -f AIPlayerEngine/pom.xml test` must stay **green** (242/242).
3. **One task = one commit**, claimed on the board first, pushed immediately.
4. Always `git pull --rebase origin master` before push; `git push origin master` right after.
5. **No new audits** — prove via the board + live bot evidence, not audit piles.
6. Verify before you claim: paste real output, never fake logs, leave the repo cleaner than found.
