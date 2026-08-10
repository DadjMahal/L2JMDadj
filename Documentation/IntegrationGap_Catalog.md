# Phase-0 Upgrade Integration Gap Catalog
**Date:** 2026-08-10 (end of session)
**Artifact:** reconciliation of the Claude-produced upgrade package in `tmp/patch0/final_upgrade/` against the live `AIPlayerEngine/` sources.

> Goal: take the Phase-0 patch made by Claude, integrate the best of it into our existing
> sources (keeping our stronger pieces), and list what was **not** integrated and **why** — with evidence.
> Every claim below is verifiable against the referenced files.

## 1. Where we are in the (reconstructed) 12-step upgrade plan
No literal "12-step" list is stored in the repo (the closest authored plan, `NEXT_ITERATION_PLAN.md`,
is the *Phase-3 HuntBot/PK* roadmap, not this Phase-0 work). The twelve steps below are reconstructed
from the upgrade objective in `Documentation/RuntimeLogs/2026-08-10-phase0-upgrade.md` and the
integration brief in `tmp/patch0/final_upgrade/INTEGRATION_ORDER.md`.

| # | Step | Status | Evidence |
|---|------|--------|----------|
| 1 | Environment: JDK 25 + local MariaDB | ✅ Done | Java 25.0.4; MariaDB on socket `:3306` |
| 2 | Databases + user | ✅ Done | `loginserver`, `gameserver` created |
| 3 | Import L2JMobius schema (login → game, ordered) | ✅ Done | 4 login tables + 96 game tables, 0 errors |
| 4 | Seed AI accounts (correct `Base64(SHA-1)` pw) + chars + `lastServer` | ✅ Done | 12 accounts/12 chars; `pw_matches=1` |
| 5 | Repoint launch scripts (JDK 25, repo paths, 3306 detection) | ✅ Done | `scripts/start_local.sh`; `StartServer.sh` edits |
| 6 | Relaunch Login + Game server, confirm registration | ✅ Done | LS `:2106/:9014`, GS `:7777`, "Registered on login as Server 2: Sieghardt", loaded 22 s |
| 7 | Reconcile patch vs live (file-by-file diff) | ✅ Done | 112 files: **78 identical, 34 differ, 0 missing** |
| 8 | Per-file keep/patch-best decisions | 🔄 In-progress | see §3 |
| 9 | Compile + test gate | ✅ Done | `mvn -q test` exit 0, **129/129 PASS**; pom clean (only `junit-jupiter`) |
| 10 | Live-prove Phase0Driver against running server | ✅ Done | `ai_combat_01` entered world; live `STATUS_UPDATE` + `DELETE_OBJECT` parsed (see §5) |
| 11 | Write the "not integrated / why" catalog | ✅ This document | — |
| 12 | Document + commit | 🔄 Pending | — |


## 2. Reconciliation method (file-level)
Compared every Java file under `tmp/patch0/final_upgrade/AIPlayerEngine/src/` (`phase0/`, `examples/`, `test/`)
against the live `AIPlayerEngine/src/`, normalising one rename in our tree
(`social/SocialBehavior.java` → `social/SocialBehaviorEngine.java`).

- **112 patch files total.**
- **78 identical** — no action needed; the patch is already our code.
- **34 differ** — divergence points (classified in §3).
- **0 missing** — every patch file exists in the live tree (nothing was dropped).

Build baseline is green, so none of the 34 divergences are compile-blocking.

## 3. The 34 differing files — classified
Signal key: `g` = reads GameStateMirror (old singleton) · `b` = reads BotSnapshot (new per-bot view) · `cur` = currentXp · `out` = System.out · `rand` = Math.random · `jedis` = Redis · `pkt` = PacketLogger.

- **Keep-Ours (our version is ahead of the patch).** Live migrated/fixed these past the patch:
  - `town/{WarehouseManager,BuyManager,SellManager,TownNavigator,TeleportManager,ItemValueEstimator,TownBehaviorEngine}` — live `pkt=Y` (PacketLogger/BotSnapshot/ItemSnapshot); patch still leans `GameStateMirror`.
  - `ItemSnapshot` — live is the real `ItemDatabase`-backed version; patch is pre-fix placeholder. `ItemSnapshotTest` asserts the placeholder fields explicitly.
  - `GameStateMirror` — our version added `currentXp` (patch has none) → see §4.1.
  - `ItemDatabase` — our soulshot/spiritshot/blessed ID alignment; patch IDs shifted/missing.
  - `humanize/*`, `movement/{StuckDetector,HumanizedPath,KiteController}`, `death/*`, `inventory/*`, `farm/ZoneDensityTracker` — live carries the 12-file compile-fix set (incorrect imports, `hpCurrent`/`mpCurrent` writes, `sendMove` wrap).
- **Adopt-Patch (patch ahead):** none high-confidence. `PartyMemberInfo` is the only candidate where the patch documents a `BotSnapshot` path while live still reads `GameStateMirror` — but live already declares `public final int level` (the §4c/4d decision), so this is a **decide-then-migrate**, not a blind adopt.
- **Rename divergence (keep-Ours):** `social/SocialBehavior` → ours is `SocialBehaviorEngine` (compiles, tested).
- **Review (per-file diff required):** `ClanChatHandler, PartyLootDistributor, PartyCoordinationEngine, PartyManager, PartyMemberInfo, HumanizedPath, InventorySnapshot, ChatPersonality` — differ for reasons not captured by the token signals; flag for a content diff before any merge decision.

## 4. Subjects NOT integrated — with reason (the core answer)
| # | Subject | Location | Why not integrated | Verdict |
|---|---------|----------|--------------------|---------|
| 4.1 | **XP tracking (`currentXp`)** | `GameStateMirror.currentXp` (`public int currentXp = 0`); readers `DynamicZoneManager:164,352`, `FarmSessionRecorder:127,141`; PacketLogger: no XP parser | `PacketLogger` exposes `getLevel/getCurHp/…` but **no XP field/parser** — `StatusUpdate`/`UserInfo` XP not parsed. Not a rename; needs a live-probe parse task. | **New work.** Our version added the field so the gap stays visible; do not fake a value. |
| 4.2 | **`Phase0Brain`** | `phase0/brain/Phase0Brain.java` | **0 callers** anywhere (`grep` clean outside the file); `Phase0Wiring` does **not** reference it. Dead weight or the future personality engine — undetermined. | **Decision gate.** Delete for cleanliness, *or* wire as the Streams-D personality layer. |
| 4.3 | **`GameStateMirror → BotSnapshot` full migration** | ~37 of 109 phase0 files still read `GameStateMirror` (farm/*, party/*, inventory/*, movement/*, death/*) | Per `INTEGRATION_GAPS.md` pass-3: migrate only high-confidence files because each remaining file needs its own constructor/call-site check; tests should follow migration. | **In progress (sequenced).** Migrate → then test, not the reverse. |
| 4.4 | **Inventory shape / item value** | `PacketLogger.getInventoryItems()` = `Map<id,count>`; `ItemSnapshot` metadata; `BuyManager/TownBehaviorEngine` via `ItemSnapshot`+`getEstimatedPrice` | No vendor-price table exists in parsed L2J data; buy decisions use heuristic estimates, not real prices. | **Partial — acceptable for Phase 0.** Real price tables = later data-parsing task. |
| 4.5 | **Skill-cast / item-use opcodes** | `Phase0Wiring` | By design: `Phase0Wiring` only sends frames proven live (Attack, Move, Chat, Bypass). `USE_SKILL`/item-use log `SKIP-UNPROVEN`. | **Intentional gate.** Keep. |
| 4.6 | **`PartyManager` dead Redis names** | `PartyManager.java` (`syncToRedis/syncFromRedis`) | Stubs are in-memory (`LEGAT_TODO: was Redis-backed … In-memory`); **no `import jedis`/`import redis` anywhere**; build green. Naming misleading, not a bug. | **Cleanup.** Rename stubs / drop Redis wording. Low priority. |
| 4.7 | **Phase0Driver is single-bot** | `examples/Phase0Driver.java` header | Reference driver only; fleet spawning lives in `AIPlayerManager` and is not adapted from this loop. Header states this explicitly. | **Follow-up task**, not a gap. Leave as reference. |

## 5. Live-proof evidence (Step 10)
`bash /tmp/run_driver.sh` ran `Phase0Driver ai_combat_01 ai123pass 100000 127.0.0.1 7777` for 18 s against the running JDK-25 server. Captured in `/tmp/phase0.log`:
```
[Phase0Driver] ai_combat_01 entered world, starting decision loop
[PACKET-LOG] [ai_combat_01] STATUS_UPDATE: objId=100000 [self=true] [MAX_HP=126, CUR_HP=100, MAX_MP=38, CUR_MP=38, MAX_CP=50, CUR_PC=0] hp=100/126 mp=38/38
[PACKET-LOG] [ai_combat_01] STATUS_UPDATE: ... CUR_HP=103 ... CUR_CP=3
... CUR_HP=106/109/112/115, CUR_CP=6/9/12/15   # live CP regen
[PACKET-LOG] [ai_combat_01] parseDeleteObject DELETE_OBJECT: objId=268461925 (removed 20481)  # live mob despawn
```
Interpretation: `ai_combat_01` authenticated (LoginOk/SessionKey via `L2JProtocol`), selected char (`CharacterSelect`), `setOnlineStatus(true)` on the GS, entered world, and `PacketLogger` parsed **real** `STATUS_UPDATE` (self HP/MP/CP rising) + `DELETE_OBJECT` from the live world — i.e. the phase0 loop (BotSnapshot → `CombatAI.makeDecision()` → `Phase0Wiring`) is fed by **live server state**, not mocks. (`CombatAI.makeDecision()` logs at `FINE`; server-side `online` returned `0` only because the driver was killed at the 18 s cutoff — expected.)

## 6. Verification commands run this session
```bash
bash /tmp/diffpatch.sh      # reconciliation -> 78 MATCH, 34 DIFFER, 0 MISSING
bash /tmp/classify.sh       # token signals per differing file
bash /tmp/verify_gaps.sh    # currentXp / jedis / Phase0Brain / PartyMemberInfo checks
mvn -f AIPlayerEngine/pom.xml -q test   # exit 0
bash /tmp/tcount2.sh        # run=129 failures=0 errors=0 skipped=0
bash /tmp/run_driver.sh     # live Phase0Driver proof (evidence in §5)
```



