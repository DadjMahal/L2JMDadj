# 📋 TASK BOARD — THE single source of truth for all work

> Every task for every agent lives here. Read `START_HERE.md` (repo root) first, then come back
> here to pick work. Do **not** create your own task lists anywhere else.

---

## 1. How to use
1. `git pull --rebase origin master` — always start from latest.
2. Read `START_HERE.md` (fast orientation, §0 the ONE goal).
3. Open THIS file → pick a `TODO` row you don't collide with (check `Owner` + §4 file map).
4. **Claim it**: set your row to `IN_PROGRESS (owner)` → commit → push the claim so everyone sees it taken.
5. Implement with tests. Rule: `mvn -o -f AIPlayerEngine/pom.xml test` stays fully green (**242/242**).
6. **One task = one commit**, push right after; set the row to `DONE-PUSHED <hash>` → commit → push.
7. Never leave `master` dirty. On conflict: `git pull --rebase`, resolve; if it's another owner's
   file (§4) ask before merging.

## 2. Status vocabulary
`TODO` · `IN_PROGRESS (owner)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>`

## 3. LIVE BOARD
| ID | Task | Status | Owner |
|---|---|---|---|
| **STEP 0** | Archive history pile + lean START_HERE/TASKS board for the PLAY goal | `DONE-PUSHED 7e820756` | doc-sweeper |
| **STEP 1** | **BotPlay controller** — bots pick goals and act (fight/level/travel), never idle | `DONE-PUSHED 9b0d34f6` | play-builder |
| **STEP 2** | Quest accept/turn-in live loop (accept → do → turn-in) | `DONE-PUSHED 072110f1` | play-builder |
| **STEP 3** | 5-bot live run + play evidence (fleet actually plays) | `TODO` | play-builder |
| **STEP 4** | Smartness polish: death/respawn, low-HP, restock | `TODO` | play-builder |

## 4. File ownership map
| Path (repo-relative) | Owner | Notes |
|---|---|---|
| `AIPlayerEngine/src/main/java/com/aiplayer/phase0/play/**` | **play-builder** | new BotPlay controller (STEP 1) |
| `AIPlayerEngine/src/test/java/com/aiplayer/phase0/play/**` | **play-builder** | controller tests |
| `AIPlayerEngine/src/main/java/com/aiplayer/examples/FleetPlay.java` | **play-builder** | fleet launcher |
| `AIPlayerEngine/src/main/java/com/aiplayer/phase0/**` (rest) | **play-builder** | shared phase0 engine |
| `Documentation/**` (incl. this board) | **doc-sweeper** | docs + board upkeep |
| `scripts/**` | **play-builder** | helper tools |

## 5. Changelog (newest last)
- **2026-08-13 · doc-sweeper:** archived the full historical/audit/evidence pile to `Documentation/_archive/`,
  deleted the stray team-runtime json, rewrote `START_HERE.md` + this board to be lean around the ONE goal
  (3–5 AI players that actually PLAY), `STATUS.md` = Phase: PLAY. **242/242 green.**
- **2026-08-13 · play-builder:** STEP 1 claimed `IN_PROGRESS` — BotPlay controller build starts.
- **2026-08-14 · play-builder:** STEP 1 sub-landed and pushed — 1A QuestGoalPlanner+GoalDecision value objects
  (`1dbf68e6`, 252/252), 1B BotPlayController decision ladder survive/combat/hunt/quest/rest + 11 tests
  (`df03840a`), 1C wired controller into FleetPlay idle loop via new pure ZoneRouter.routeTo (`9b0d34f6`). **265/265 green.**
- **2026-08-14 · play-builder:** STEP 2 landed — pure quest accept→do→turn-in dialog driver
  (`QuestDialogDriver`: `Script → quest-name → objective token → safe .htm/Quest` fallback; never
  re-sends, never fabricates) + `GoalDecision.questMove`/`questTargetId`/`bypass` surfaced at giver,
  `BotPlayController` emits `BYPASS` when a QUEST/ACQUIRE MOVE_TO lands within `talkRange`, and
  `FleetPlay`'s idle loop drives the live NPC dialog (click giver → read NpcHtmlMessage → send the
  single validated bypass) gated behind `phase0.quest.npcId` (off by default). 8 new tests.
  *273/273 green.* `072110f1`.
