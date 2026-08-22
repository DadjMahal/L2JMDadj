# 📋 TASK BOARD — the single source of truth for OPEN work

> Pick work here. Completed work is NOT on this board — it lives in the anti-redo registry
> `Documentation/REVIEWED_TASKS.md` (check it before starting anything). Program context:
> `Documentation/UpgradePlan/README.md`. Rules: `Documentation/WORKFLOW.md`.

## 1. How to use
1. `git pull --rebase origin master` — always start from latest.
2. Read `START_HERE.md` (orientation) + `STATUS.md` (current state).
3. Pick an open row below you don't collide with (check `Owner` + §3 file map).
4. **Claim it**: set the row to `IN_PROGRESS (owner)` → commit → push the claim.
5. Implement with tests (`mvn -o -f AIPlayerEngine/pom.xml test` stays green).
6. **One task = one commit**, push right after; set the row to `DONE-PUSHED <hash>`, move it to
   `REVIEWED_TASKS.md`, and write a RuntimeLog.

## 2. Status vocabulary
`TODO` · `IN_PROGRESS (owner)` · `BLOCKED (reason)` · `DONE-PUSHED <hash>` → row moves to the registry.

## 3. OPEN TASKS
| ID | Task | Diff | Prio | Status |
|---|---|---|---|---|
| S3-T02 | Live-prove quest objective progress (kill/collect counters via QUEST_LIST; quest persists [[6,1]], dialog re-click works — next objective target from live server) | H | P0 | IN_PROGRESS (play-builder) |
| S3-T03 | Live-prove quest TURN-IN + reward receipt (exp/adena/item) | H | P0 | BLOCKED (on S3-T02) |
| UP-GK-1 | Datapack knowledge extractor skeleton (prompt: `UpgradePlan/AUDIT_03_GAMEKNOWLEDGE.md`) | S | P1 | TODO |
| UP-LW-1 | Structured event file sink events.jsonl (prompt: `UpgradePlan/AUDIT_02_LOGS_WATCHERS.md`) | M | P1 | TODO |
| UP-LW-2 | WATCHER_RULES.md + watcher template (prompt: `UpgradePlan/AUDIT_02_LOGS_WATCHERS.md`) | S | P1 | TODO |

**Next up after these:** UpgradePlan Waves 2–5 — the full execution order, dependency graph and
task prompts are in `Documentation/UpgradePlan/README.md` §3 (claim by adding a `UP-*` row here).

## 4. File ownership map
| Path (repo-relative) | Owner | Notes |
|---|---|---|
| `AIPlayerEngine/src/main/java/com/aiplayer/behavior/**` (+tests) | **play-builder** | decision ladder, combat/quest/social/... domains |
| `AIPlayerEngine/src/main/java/com/aiplayer/core/**` (+tests) | **play-builder** | FleetConfig, BotSession, BotInfo, wiring, snapshots |
| `AIPlayerEngine/src/main/java/com/aiplayer/{net,protocol,web,monitor,metrics,cli,knowledge,learning,examples}/**` | **play-builder** | engine plumbing + fleet launcher (FleetPlay) |
| `AIPlayerEngine/src/main/resources/**` (config, dashboard SPA) | **play-builder** | ai-player.properties (`engine.*` keys), dashboard html |
| `scripts/**` | **play-builder** | helper tools; secrets via `fleet_env.local` (gitignored) |
| `Documentation/**` (incl. this board) | **doc-sweeper** | docs + board upkeep |
| `AIPlayerEngine/attic/**` | — | dead code, do not edit (see `AIPlayerEngine/attic/README.md`) |

## 5. Registry & history
- Done/in-flight registry (never redo work): `Documentation/REVIEWED_TASKS.md`.
- Per-task evidence: `Documentation/RuntimeLogs/`.
- Pre-board history (STEP 0–8, GUIDE-MAP, LIVE-RUN): `REVIEWED_TASKS.md` §E.3.
