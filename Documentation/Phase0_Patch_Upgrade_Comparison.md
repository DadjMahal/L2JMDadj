# Phase 0 Patch Upgrade — Upgrade vs. Previous Version

**Scope:** `vBaseline` (engine-only `CombatAI`/`AIBrain`/`AIPlayer`, MySQL login+game, no Phase 0 layer) **→** state after the 2026‑08‑10 phase‑0 integration pass (`tmp/patch0/final_upgrade`, Layers 0‑4 / Tasks 1‑4).

**Gate:** `mvn -q test` → **129/129 PASS, 0 fail, 0 err**, 0 skipped (`AIPlayerEngine`); offline `mvn -o compile` clean; `verify_no_dead_code.sh` → 292 files, 7 TODOs. Live proof: `Phase0Driver ai_combat_01` ran 18 s vs. the JDK‑25 server and parsed real `STATUS_UPDATE` (HP 100→115, live CP regen) + `DELETE_OBJECT`.

## 1. What the patch ADDED (new capability since vBaseline)

**Phase 0 package (~109 files):** `com.aiplayer.phase0/{brain,cabinet,chat,director,imperfection}` + `combat` (Task 1‑2) + `movement` (Task 3) + `death` (Task 4) + `GameStateMirror/BotStateSnapshot/BotSnapshot`.

- **Live snapshot:** `BotSnapshot` over `PacketLogger` getters (`getLevel/getAdena/getCurHp/getMaxHp/getCurMp/getMaxMp/getHpPercentage/getMpPercentage/getPlayerX/Y/Z/Heading/getInventoryItems/getHostileEntities/findNearestHostile/getLastNpcHtml`).
- **Outbound codec:** `PacketCodec.encodeAction/encodeAttackRequest/encodeMoveToLocation/encodeChat/encodeBypass` + handshake builders (`encodeProtocolVersion/encodeAuthLogin/encodeCharacterSelect/encodeEnterWorld`) — B4/B8‑proven.
- **Timing:** `CombatFramePlanner.FLOOD_PROTECTOR_DELAY_MS = 1000`; `Phase0Wiring` delegates to it (skips un‑proven frames → logs `SKIP‑UNPROVEN`).
- **Task 1 (skills):** `SkillDatabase`/`CooldownTracker`/`CombatRotation`/Fighter‑Mage‑Archer‑Healer‑Buffer rotations/`RotationFactory`/`ShotManager`.
- **Task 2 (target):** `TargetSelector`/`AggroTracker`/`PartyAssistTracker`.
- **Task 3 (movement):** `MovementState`/`PathNode`/`BezierCurve`/`HumanizedPath`/`StuckDetector`.
- **Task 4 (death):** `DeathHandler`/`Graveyard`/`GraveyardRegistry`/`RespawnManager`/`ReturnToFarmPath`/`RecoveryFlow`.
- **Humanization:** `AntiDetectionEngine` (humanized delays, seeded `Random`, `ResponseTemplate`).
- **Store:** `ProfileStore` (in‑memory) replaces Redis/JDBC; `CabinetService`/`RedisCache` deleted; `pom_additions.xml` (jedis+postgresql) deleted → pom stays `junit`‑only.
- **Determinism:** 9× `Math.random()` → seeded `Random` (`accountName.hashCode()`), `seed`/`long` param on static helpers + call‑site updates; 3× `System.out.println` → `Logger`.
- **Reference driver:** `examples/Phase0Driver.java` (login → enter‑world → `BotSnapshot` → `CombatAI.makeDecision()` → `Phase0Wiring`) — deliberately avoids editing the engine.

**Infra (required to run the patch this session):** `scripts/start_local.sh`/`StartServer.sh` now force JDK 25 (jars are class v69; PATH `java` was 21); `StartServer.sh` wait pattern fixed (`stdout.log`→`java0.log` — old `grep "Server loaded"` always failed → GS killed at the 120 s timeout); 12 AI accounts seeded with `password = Base64(SHA‑1('ai123pass'))` (matches `LoginController`; plaintext would fail) + 12 Human‑Fighter chars at the TI farm zone.

## 2. What the patch FIXED vs vBaseline (per `INTEGRATION_ORDER_LAYERS_0-4.md §5`)
- `TargetSelector.objectId` → real field `objId` (×5).
- `MovementController`: wrong import → `protocol.L2JProtocol`; calls real `sendMove(x,y,z)` wrapping `IOException`.
- `RecoveryFlow`/`RespawnManager`/`DeathHandler`: `self.hpPercent` (non‑existent) → inline from `hpCurrent/hpMax`; `restockAfterDeath()` (undefined) → `disableShots()`.
- `GameStateMirror.getVisibleEntities()` (undefined) → `BotSnapshot.getNearbyEntities(...)` via `PacketLogger.getNearbyEntities`.
- Layer 4 duplicate‑field trap avoided: `isDead` already on `BotStateSnapshot`, skip patch's duplicate add.

## 3. NOT yet integrated (honest gap) — needs your permission
The patch code is **present and compiles**, but your **brain & main engine are pristine** — no engine file was edited this pass:

| Engine file | Patch intended change (Layer) | Applied? | Evidence |
|---|---|---|---|
| `engine/CombatAI.java` | Task 1/2/3/4 engine patches | **No** | `grep` for `SkillDatabase/CombatRotation/CooldownTracker/TargetSelector/AggroTracker/DeathHandler/RespawnManager/HumanizedPath/StuckDetector/Phase0Wiring/Phase0Brain` → **NONE** — yet Layer‑1 anchors exist (`selectBestSkill/shouldUseSkill/useOffensiveSkill/shouldHeal/manageActiveCombat/lastSkillUseTime/combatState/LOGGER`) → it *can* apply. |
| `engine/AIBrain.java` | `PATCH_AIBrain_Task2/3/4/5..8` | **No** | engine‑side `NONE`. |
| `engine/AIPlayer.java` | `PATCH_AIPlayer_Task2/3/4/5..8` | **No** | `NONE`. |

**Consequence:** `Phase0Driver` runs **BotSnapshot → your unmodified `CombatAI.makeDecision()` → Phase0Wiring**, so the live loop uses *your* decision logic, **not** the patch's `RotationFactory`/`TargetSelector`/`DeathHandler`/`RecoveryFlow`. Those modules are built + isolated‑tested, but not invoked by the real engine.

Other genuine gaps: **`self.currentXp`** — `PacketLogger` has no XP field/source at all (not even a name to rename) → real `StatusUpdate`/`UserInfo` parse task needed, not faked; **~37 files still read `GameStateMirror`** (unmigrated to `BotSnapshot`); **4c** `ShotManager.restockAfterDeath()` undefined → currently `disableShots()` workaround; **4d** `RespawnManager.findNearest(self.level)` — `level` absent from `BotStateSnapshot` → pick (a) add field, or (b) pass from `AIPlayer.getLevel()`; **Tasks 5‑8** patches exist but were outside the Layers‑0‑4 scope for this pass.

## 4. Patch hygiene wins
- `pom_additions.xml` deleted (jedis+postgresql); `CabinetService`/`RedisCache` deleted; nothing imports them.
- `ItemSnapshot.sellPrice/objId/isQuestItem` asserted as placeholders in `ItemSnapshotTest` (visible gap); `Spot` confirmed present (`OptimalSpotSelector`) — `DynamicZoneManager` import fixed.
- Every file carries `MODE: COMPLETE|PARTIAL|PLACEHOLDER` (6 COMPLETE, 5 PLACEHOLDER, 98 PARTIAL).

## 5. Bottom line
**Yes — real upgrade, safely.** We gained the whole Phase 0 architecture (snapshot/codec/frame‑planner/anti‑detect/Task 1‑4 modules/Phase0Driver) + a working JDK‑25 relaunch + seeded login — **without altering a single line of your proven `CombatAI`/`AIBrain`/`AIPlayer`/Streams A‑C engines**. 129/129 tests pass; live driver proves end‑to‑end state flow.

The remaining lift — **wiring the patch's Task logic into your actual engine loop** — is exactly the set of edits you asked me to bring to you for permission first.<!-- SPLIT_MARKER -->

