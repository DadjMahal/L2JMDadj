# INTEGRATION_ORDER.md — Phase 0 + Task 1-4 Upgrade

**For:** Deepseek v4 Flash / Laguna XS 2.1 (via Cline)
**Source:** Four Kimi session transcripts (`Cline_to_Parse_-_Kimi_Update__1_.md` through `__4_.md`),
extracted and cross-checked against the live repository on 2026-08-08.
**Do not apply out of order.** Each layer depends on the one before it. Compile
after every layer — do not wait until the end to find out which layer broke.

---

## 0. Why layers, not one big patch

Task 1 code imports Phase 0 classes. Task 2 code imports Task 1 classes and
patches a file Task 3 and Task 4 *also* patch. Applying everything at once and
compiling once means a single error anywhere hides which layer caused it. Five
concrete bugs were already found and fixed by going layer-by-layer during
packaging (listed in §5) — more may exist that only a real compiler will catch.
Stop at the first layer that fails and report the exact compiler error before
continuing.

---

## Layer 0 — Phase 0 foundation

**Files:** `AIPlayerEngine/src/main/java/com/aiplayer/phase0/**` (everything
already under `phase0/` except `combat/`, `movement/`, `death/`, which belong to
later layers).

1. Merge `pom_additions.xml` into `AIPlayerEngine/pom.xml` — adds `jedis`
   (5.1.0) and `postgresql` (42.7.2). Nothing below compiles without these.
2. Copy the `phase0/{brain,cabinet,chat,director,imperfection}` folders and
   `phase0/GameStateMirror.java` into
   `AIPlayerEngine/src/main/java/com/aiplayer/phase0/`.
3. Run the two SQL files in `sql/` against your PostgreSQL instance (not MySQL
   — this is deliberately a second database; see the architecture doc for why).
4. **`mvn -pl AIPlayerEngine compile`.** Fix whatever it flags before moving on.

---

## Layer 1 — Task 1 (Skills & Combat Rotation)

**Files:** `phase0/combat/{SkillInfo,SkillDatabase,CooldownTracker,
CombatRotation,FighterRotation,MageRotation,ArcherRotation,HealerRotation,
BufferRotation,RotationFactory,ShotManager}.java`

1. Copy the files above into `phase0/combat/`.
2. Apply `patches/TASK1_PATCH_CombatAI.txt` to
   `AIPlayerEngine/src/main/java/com/aiplayer/engine/CombatAI.java`.
   **Before applying:** confirm `CombatAI.java` actually has methods named
   `selectBestSkill()`, `shouldUseSkill()`, `useOffensiveSkill()`,
   `shouldHeal()`, `manageActiveCombat()`, and fields `LOGGER`,
   `lastSkillUseTime`, `combatState` — this was not re-verified line-by-line
   this session (everything else in this guide was). If any name doesn't
   match, that's a real mismatch to resolve, not a typo to paper over.
3. Apply `patches/TASK1_PATCH_AIBrain.txt` to `AIBrain.java`.
4. Apply `patches/TASK1_PATCH_AIPlayer.txt` to `AIPlayer.java`.
5. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 2 — Task 2 (Target Selection & Aggro)

**Files:** `phase0/combat/{TargetScore,TargetSelector,AggroTracker,
PartyAssistTracker}.java`

1. Copy the files above into `phase0/combat/`.
2. Apply `patches/PATCH_GameStateMirror.txt` to `phase0/GameStateMirror.java`
   — adds `level`, `aggroRange`, `isAggressive`, `isBoss`, `isElite`,
   `isAttackable`, `isDead`, `isPlayer`, `isEnemy`, `mobType` to
   `EntitySnapshot`, and a `PartyMemberSnapshot` inner class.
3. Apply `patches/PATCH_CombatAI_Task2.txt`, `PATCH_AIBrain_Task2.txt`,
   `PATCH_AIPlayer_Task2.txt`.
4. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 3 — Task 3 (Movement & Pathfinding)

**Files:** `phase0/movement/{MovementState,PathNode,BezierCurve,HumanizedPath,
StuckDetector,GeoPathfinder,KiteController,MovementController}.java`

1. Copy the files above into `phase0/movement/`.
2. Apply `patches/PATCH_GameStateMirror_Task3.txt` — adds `isMoving`,
   `isRunning`, `destX/Y/Z`, `lastMoveTime` to `BotStateSnapshot`. This is
   additive to Layer 2's patch, not a replacement — apply both, in order.
3. Apply `patches/PATCH_AIPlayer_Task3.txt`, `PATCH_AIBrain_Task3.txt`,
   `PATCH_CombatAI_Task3.txt`.
4. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 4 — Task 4 (Death, Respawn & Recovery)

**Files:** `phase0/death/{DeathHandler,Graveyard,GraveyardRegistry,
RespawnManager,ReturnToFarmPath,RecoveryFlow}.java`

1. Copy the files above into `phase0/death/`.
2. Apply `patches/PATCH_GameStateMirror_Task4.txt` **with one change**: skip
   its first instruction (`ADD to BotStateSnapshot: public boolean isDead =
   false;`) — that field already exists in the Layer 0 base. Adding it again
   is a duplicate-field compile error. Apply the rest of the patch
   (`deathTime`, `respawnX/Y/Z`, `isRecentlyRespawned()`) normally.
3. Apply `patches/PATCH_AIPlayer_Task4.txt`, `PATCH_AIBrain_Task4.txt`,
   `PATCH_CombatAI_Task4.txt`.
4. **`mvn -pl AIPlayerEngine compile`.**

---

## 5. Fixes already applied during packaging (verified against live repo)

| File | Problem | Fix |
|---|---|---|
| `TargetSelector.java` | `entity.objectId` — real field is `objId` | All 5 references renamed |
| `MovementController.java` | Wrong import (`phase0.L2JProtocol`); called a `sendMoveBackwardToLocation(...)` method that doesn't exist | Import → `com.aiplayer.protocol.L2JProtocol`; calls real `sendMove(x,y,z)`, wrapped for its checked `IOException` |
| `RecoveryFlow.java` | Same wrong import; `self.hpPercent` doesn't exist; `shotManager.restockAfterDeath()` doesn't exist anywhere | Import fixed; HP computed inline from `hpCurrent`/`hpMax`; call replaced with `disableShots()` (see item 4c) |
| `RespawnManager.java` | Same wrong import; `self.hpPercent`/`self.mpPercent` writes to non-existent fields | Import fixed; writes redirected to `hpCurrent = hpMax` / `mpCurrent = mpMax` |
| `DeathHandler.java` | `self.hpPercent` reads (×2), same non-existent field | Computed inline, same pattern as the rest |

All five were found by re-checking every file against the actual `GameStateMirror.java`
and `L2JProtocol.java` already in the repo, not by re-reading Kimi's four files more
carefully — this is exactly the "independent sessions drift out of sync" risk flagged
in the earlier workflow audit, now with concrete instances.

## 4c/4d — Two items intentionally left for a decision, not guessed

- **4c — `ShotManager.restockAfterDeath()`:** referenced by `RecoveryFlow.java`,
  defined nowhere in any of the four files or in `ShotManager.java` as delivered.
  Currently calls `disableShots()` instead, which lets the normal combat-tick
  cycle re-enable shots next fight. If you want shots re-enabled immediately on
  respawn instead, add a real `restockAfterDeath()` method to `ShotManager.java`
  that calls `enableShots(...)` with the AI Player's preferred shot type.
- **4d — `RespawnManager.findNearest(self.x, self.y, self.level)`:**
  `BotStateSnapshot` has no `level` field anywhere in Phase 0 or any patch set.
  Two reasonable fixes, pick one: (a) add `level` to `BotStateSnapshot` via a
  small patch, populated wherever `CharInfo` is parsed; (b) change
  `selectAndRespawn()` to take the AI Player's level as a parameter, sourced
  from `AIPlayer.getLevel()` at the call site instead of from GameStateMirror.
  Left as-is (uncompiled) rather than picking one unilaterally.

---

## 6. After all four layers compile clean

- Smoke test one AI Player through a full cycle: spawn → target → move to
  target → fight (uses a real skill, not just basic attack) → die → respawn →
  return to farm zone. This exercises every layer in sequence.
- Update `STATUS.md` (or `DONE_SUMMARY.md`) with the real result — pass, or
  the specific compile/runtime error, not "done." That's the entire point of
  routing this through a compile step instead of a status table.
