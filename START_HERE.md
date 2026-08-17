# 🚀 START HERE — read first, every session, every Cline instance

> Fast binary orientation for any agent/session in this repo. Depth = only the files the routing
> table (§3) links to. The ONLY task board is `Documentation/TASKS.md` — pick work there.

## 0. The ONE goal
Make 3–5 AI player bots **actually PLAY the game** — fight, level, travel, pass quests — **never idle**.
No more audits.
**Current state (1-liner):** **345/345 tests green**, TIM-001 done (the fleet farms organically,
plus ultra-smart vol.1 — restock-to-vendor, hunt-zone spread, diverse quest pick), server stack
runnable on JDK25. All prior historical/audit/evidence docs are archived — do not redo them.

## 1. Run it (bring the fleet to life)
```bash
# Login/Game on JDK25 (system java is JDK21; server JARs need JDK25)
export PATH=~/.jdk/jdk-25.0.4+7/bin:$PATH
cd /home/dadj/Projects/l24lude/ServerBuild/login && ./LoginServerTask.sh
cd /home/dadj/Projects/l24lude/ServerBuild/game  && ./GameServerTask.sh

# Fleet of 5 + web dashboard :8080
cd /home/dadj/Projects/l24lude/AIPlayerEngine \
  && setsid -f ~/.jdk/jdk-25.0.4+7/bin/java -cp target/classes \
       com.aiplayer.examples.FleetPlay 5 127.0.0.1 7777 2106 8080 \
     </dev/null >/tmp/fleet.log 2>&1
# open http://localhost:8080
```
Build/verify:
```bash
cd /home/dadj/Projects/l24lude/AIPlayerEngine && mvn -o compile && mvn -o test
```

## 2. Active lanes (the two board pointers to drive next)
- **Live quests + ultra-smart wiring** (`STEP 7 done`; next): STEP 2's quest accept/turn-in loop is
  still gated off by default (`phase0.quest.npcId=0`) — prove accept → complete → turn-in → reward on a
  LIVE char, then wire the smart planners (RestockPlanner BUY, FleetSpreadPlanner, quest seed) into
  the running FleetPlay loop and capture live evidence (bots actually walk to vendors, split zones,
  take different quests).

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
