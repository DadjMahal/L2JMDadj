# 🚀 START HERE — read first, EVERY session, EVERY Cline instance

> This file is the **fast orientation** for any Cline working in this repo, including multiple
> instances working in parallel. Depth lives in `Documentation/SESSION_HANDOFF.md`; **the ONLY task
> board is `Documentation/TASKS.md`** — pick work there, never create your own task list.

## 0. Multi-Cline working together (IMPORTANT)
We may run up to 4 Cline instances at once (Cline#1 orchestrator/backend, #2 frontend, #3 protocol,
#4 ops/docs). Coordination is NOT shared memory — it is the **git repo + TASKS.md**:
1. Always `git pull --ff-only origin master` before starting.
2. Read `Documentation/TASKS.md` → §5 kickoff + §4 file-ownership map.
3. **Claim a task FIRST** (set Status to `IN_PROGRESS (Cline#X)`, commit+push the claim) BEFORE coding,
   so no one else starts it.
4. **One task = one commit**, pushed immediately; then mark `DONE-PUSHED <hash>` + push.
5. **Never edit a file you don't own** (§4). On merge conflict: `git pull --rebase`, resolve, ask the
   owner if it's their file.
6. `mvn -o -f AIPlayerEngine/pom.xml test` must stay green (currently 145 + new).

## 1. Project (one line)
L2JMobius **Interlude** server + **external-socket AI Player Engine** (`AIPlayerEngine/`) — a fleet of
AI clients (no L2J server-code edits) that log in, hunt, level, and report live state to a web
dashboard. All work is committed on `master` and pushed to GitHub.

## 2. Where things live (repo root `/home/dadj/Projects/l24lude`)
- `AIPlayerEngine/` — Java engine (login/GS protocol, combat AI, phase0 wiring, fleet, dashboard).
- `SourceCode/` — L2J server source (server packet writers we mirror: `UserInfo`, `CharInfo`, ...).
- `ServerBuild/login|game/` — running LoginServer/GameServer (`LoginServerTask.sh`/`GameServerTask.sh`).
- `Documentation/` — audits, upgrade brief, `SESSION_HANDOFF.md`, `PRIORITY_TASKS.md` (TIM-001 detail),
  and **`TASKS.md` — the ONLY task board**. `scripts/` — helper shell tools.

## 3. Honest current state (proofs in `Documentation/Audit/*` + this session)
- ✅ Interlude (protocol 746) handshake + enter-world proven — real external sockets, no server edits.
- ✅ phase0 integrated; skill-cast gate `0x2F` proven; SkillDatabase/combat AI/target selector live.
- ✅ **5-bot fleet** (`FleetPlay`) + **web dashboard** :8080 — Map/Grid views, real packets
  (UserInfo/CharInfo/ValidateLocation/ItemList/StatusUpdate), real coords & town landmarks.
- ✅ **218 tests green** (2026-08-13). **All 33 WPT web-panel tasks DONE-PUSHED** (Phase A API
  WPT-01..08, Phase B UX, Phase C telemetry, Phase D ops). **Anti-redo registry:
  `Documentation/REVIEWED_TASKS.md`** — check it before starting anything.
- ⚠️ **TIM-001 (HIGH)** — movement-persistence (H1) **PROVEN with a real DB delta (2026-08-13, run #3)**:
  `ZoneRouter.buildHops()` short-multi-hop (≤4800u) + `isRouteStuck()` stuck-hop recovery + `moveTo`
  via the standard mouse-move path landed. `/telemetry` shows `serverMoved=4569 u` (was 0) and the fleet
  log confirms the char reaching its hop (`USER_INFO` == HOP target); `gameserver.characters` moved
  `CombatBot_04 (-109393,245900) → (-116158,242929)` Δ≈7390u once the probe waits for the disconnect-save
  flush (`DB_FLUSH_SEC`, default 12) — run #2's "identical before/after" was a probe timing bug. Suite
  **219/219 green**. **H5 organic XP remains open.** Log:
  `RuntimeLogs/2026-08-13-tim001-h1-persistence-proven.md`.
- 🛑 **Server state 2026-08-13:** was **DOWN** at session open; brought **UP** on JDK25
  (`/home/dadj/.jdk/jdk-25.0.4+7` on PATH — system `java` is JDK21 but server JARs are JDK25).
  Login :2106/:9014, game :7777 up (`Server loaded`, `Registered on login`). Use
  `https://github.com/DadjMahal/l24lude` as the remote.

## 5. How to run (when servers are OFF)
```bash
# LoginServer (auth 2106/9014) and GameServer (7777)
cd /home/dadj/Projects/l24lude/ServerBuild/login && ./LoginServerTask.sh
cd /home/dadj/Projects/l24lude/ServerBuild/game  && ./GameServerTask.sh

# Fleet + dashboard (5 bots, :8080 — needs JDK25 at ~/.jdk/jdk-25.0.4+7)
cd /home/dadj/Projects/l24lude/AIPlayerEngine \
  && setsid -f /home/dadj/.jdk/jdk-25.0.4+7/bin/java -cp target/classes \
       com.aiplayer.examples.FleetPlay 5 127.0.0.1 7777 2106 8080 \
     </dev/null >/tmp/fleet.log 2>&1
# open http://localhost:8080
```
Build / verify:
```bash
cd /home/dadj/Projects/l24lude/AIPlayerEngine && mvn -o compile && mvn -o test
```

## 6. Reality check — paste output before claiming anything "works"
```bash
git -C /home/dadj/Projects/l24lude status --short
ss -tlnp 2>/dev/null | grep -E '2106|9014|7777|8080'
curl -s http://localhost:8080/api/v1/health ; echo
```

## 7. Routing table (read the right file before writing code)
| Task touches | Read first |
|---|---|
| Dashboard / web API | `examples/FleetPlay.java`, `com/aiplayer/web/DashboardApi.java`, `resources/dashboard/index.html` |
| Protocol / packets | `protocol/PacketLogger.java`, `protocol/L2JProtocol.java`, `SourceCode/.../serverpackets/UserInfo.java` |
| Combat AI / phase0 | `engine/CombatAI.java`, `phase0/combat/FighterRotation.java`, `ShotManager.java`, `SkillDatabase.java`, `Audit/*` |
| Quest / W5 | `engine/QuestAI.java`, `Audit/30-quest-progression.md` |
| Multi-agent / QA / meta | `Documentation/MultiAgentQA.md`, `scripts/`, `Documentation/Streams.md` |
| Docs / workflow | `AGENT_ONBOARDING.md`, `Documentation/WORKFLOW.md` |
| Audit / deep review | `Documentation/AUDIT_ORIENTATION.md`, `Documentation/DONE_SUMMARY.md`, `STATUS.md` |
| **Task board** | **`Documentation/TASKS.md` (always)** |

## 8. Rules (7 hard rules — full text in `AGENT_ONBOARDING.md`)
1. Verify before claim (no "working" without pasted output). 2. No fake logs. 3. Usage validation.
4. Audit-first. 5. Document before code. 6. Leave cleaner than you found it.
7. Milestone doc-sync — update this file / `STATUS.md` / `SESSION_HANDOFF.md` / **`TASKS.md`** /
   `ai_progress_report.txt` + commit after EVERY milestone.
## 4. Current phase / next task
- Board: **`Documentation/TASKS.md`**.
- **Phase A (WPT-01..08) DONE-PUSHED**; protocol WPT-23/25/26 DONE-PUSHED. In-flight now:
  frontend **WPT-09/10/13/14/20/31** (Cline#2), protocol **WPT-22/24/29** (Cline#3), ops
  **WPT-30** (Cline#4 — `scripts/position_crosscheck.sh` written); **TIM-001** evidence work active.
- Ops+docs lanes done: WPT-32/33/34 (`dashboard/ops.html`, README/e2e, `server_health.sh`).