# INTEGRATION_ORDER.md — Phase 0 + Task 1–11 Upgrade (merged)

**For:** Deepseek v4 Flash / Laguna XS 2.1 (via Cline)
**Source:** 11 Kimi sessions across two upload batches (`Cline_to_Parse_-_Kimi_Update__1-4_.md`,
`L2-Kimi-T5.md` through `T11.md`), extracted and cross-checked against the live
repository on 2026-08-08.
**Do not apply out of order.** Each layer depends on the ones before it.
**Compile after every layer.** Stop at the first failure and report the exact
compiler error before continuing — don't guess ahead.

---

## 0. What changed since the last package

The earlier `upgrade_phase0_task1-4.zip` covered Layers 0–4. This package adds
Layers 5–11 and folds them into the same tree. If you already applied Layers
0–4, skip to Layer 5. `INTEGRATION_ORDER_LAYERS_0-4.md` in this same folder has
the full step-by-step detail for those layers, preserved as-is rather than
rewritten here — one document per already-verified stage, so nothing here
contradicts it.

**28 concrete bugs were found and fixed while packaging this**, all from the
same root cause already identified in the earlier package: independent Kimi
sessions, with no memory of each other and no compiler, drift out of sync —
both with the real repo and with each other. Full list in §12. This is not
a one-off; expect the same pattern in any future Kimi output and check for it
the same way (§13 has the exact method used).

---

## Layers 0–4 (unchanged)

Phase 0 foundation → Task 1 (Skills/Combat) → Task 2 (Targeting) → Task 3
(Movement) → Task 4 (Death/Respawn). See `INTEGRATION_ORDER_LAYERS_0-4.md`.
Compile checkpoint after Layer 4 before continuing below.

---

## Layer 5 — Task 5 (Inventory & Consumables)

**Files:** `phase0/inventory/{ItemDatabase,InventorySnapshot,InventoryTracker,
ConsumableManager,SoulshotRestocker,WeightMonitor,AutoLootHandler}.java`

1. Copy the files above into `phase0/inventory/`.
2. Apply `patches/PATCH_GameStateMirror_Task5.txt`.
3. Apply `patches/PATCH_AIPlayer_Task5.txt`, `PATCH_AIBrain_Task5.txt`,
   `PATCH_CombatAI_Task5.txt`.
4. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 6 — Task 6 (Social & Chat Humanization)

**Files:** `phase0/social/{ChatMessage,ChatHistory,ChatPersonality,SocialTimer,
PartyInviteHandler,ChatFilter,SocialBehavior,ChatResponder}.java`

1. Copy the files above into `phase0/social/`.
2. Apply `patches/PATCH_GameStateMirror_Task6.txt`, `PATCH_AIPlayer_Task6.txt`,
   `PATCH_AIBrain_Task6.txt`.
3. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 7 — Task 7 (Vendor & Town Automation)

**Files:** `phase0/town/{VendorDatabase,ItemValueEstimator,SellManager,
BuyManager,WarehouseManager,TeleportManager,TownNavigator,
TownBehaviorEngine}.java`

1. Copy the files above into `phase0/town/`.
2. Apply `patches/PATCH_GameStateMirror_Task7.txt`, `PATCH_AIPlayer_Task7.txt`,
   `PATCH_AIBrain_Task7.txt`.
3. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 8 — Task 8 (Anti-Detection & Humanization)

**Files:** `phase0/humanize/{BehavioralFingerprint,HumanizedRandom,
InputRandomizer,TimingJitter,SessionVariance,AntiDetectionEngine}.java`

1. Copy the files above into `phase0/humanize/`.
   Note: the source transcript named this class's *file* `AntiDetectEngine`
   while the *class inside it* is `AntiDetectionEngine` — Java requires these
   to match. Already renamed to `AntiDetectionEngine.java` in this package;
   nothing to do here, just don't rename it back.
2. Apply `patches/PATCH_GameStateMirror_Task8.txt`, `PATCH_AIPlayer_Task8.txt`,
   `PATCH_AIBrain_Task8.txt`.
3. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 9 — Task 9 (Quest & Leveling Automation)

**Files:** `phase0/quest/{QuestInfo,QuestDatabase,QuestProgressTracker,
ZoneRecommender,QuestRewardEvaluator,QuestExecutor,ClassChangeManager,
LevelingPlanner}.java`

**No integration patches exist for this task** — none were produced in the
source session (confirmed: no `PATCH_*_Task9.txt` anywhere in the transcript,
unlike every other task). `QuestExecutor` and `LevelingPlanner` take a
`MovementController` via their constructors (real class, already in Layer 3),
so they're built to be wired in, but nothing tells you where to construct and
call them from `AIBrain`'s decision loop. That has to be written fresh, not
just applied from a patch file — treat this as new work, not integration.

1. Copy the files above into `phase0/quest/`.
2. Apply `patches/PATCH_GameStateMirror_level_field.txt` (this is new, written
   during packaging, not from Kimi — see §12 for why it exists and which
   other tasks also need it).
3. **`mvn -pl AIPlayerEngine compile`.** This layer should compile clean with
   no wiring into `AIBrain` yet — the classes are self-contained until you
   decide how to call them.

---

## Layer 10 — Task 10 (Party & Clan Integration)

**Files:** `phase0/party/{PartyRole,PartyMemberInfo,PartyManager,
PartyCoordinationEngine,PartyLootDistributor,ClanChatHandler,
SiegeParticipationStub}.java`

**Same gap as Layer 9 — no integration patches were produced.**

1. Copy the files above into `phase0/party/`.
2. **`mvn -pl AIPlayerEngine compile`.**

---

## Layer 11 — Task 11 (Farm Zone Intelligence)

**Files:** `phase0/farm/{ZoneDensityTracker,RespawnTimer,FarmZoneScorer,
OptimalSpotSelector,DynamicZoneManager,FarmSessionRecorder}.java`

**Same gap again — no integration patches were produced.**

1. Copy the files above into `phase0/farm/`.
2. **`mvn -pl AIPlayerEngine compile`.**

---

## 12. Every fix applied while packaging this (28 total)

| Pattern | Files affected | Fix |
|---|---|---|
| Wrong import `com.aiplayer.phase0.L2JProtocol` (real package is `com.aiplayer.protocol`) | 13 files across Tasks 5–8 | Import path corrected |
| `entity.objectId` (real field is `objId`) | 6 files, Tasks 2 & 5–7 | Renamed, all occurrences |
| `self.hpPercent` / `self.mpPercent` (field doesn't exist; real fields are `hpCurrent`/`hpMax`/`mpCurrent`/`mpMax`) | 10 files across Tasks 1–9 | Replaced with inline `(x.hpMax > 0 ? x.hpCurrent*100/x.hpMax : 100)` computation everywhere |
| `protocol.sendMoveToLocation(...)` (real method is `sendMove(x,y,z)`) | 3 files, Tasks 3 & 8 | Renamed |
| File named `AntiDetectEngine.java`, class declared `AntiDetectionEngine` | 1 file, Task 8 | File renamed to match the class (Java requirement) |
| `self.level` / `state.level` — field never added by any of 11 independent sessions, all of which assumed it existed | 11 files across Tasks 5–11 | New `PATCH_GameStateMirror_level_field.txt`, not from Kimi — see its own file for why a shared field beat rewriting 11 call sites |
| `shotManager.restockAfterDeath()` — method never defined anywhere | 1 file, Task 4 (carried from the earlier package) | Replaced with `disableShots()`; flagged as an open decision, not silently invented |
| Trailing prompt/handoff boilerplate text appended after the real class's closing brace (an extraction artifact — the last class in Task 8's and Task 9's source transcripts had no following header to bound where its code stopped) | `AntiDetectionEngine.java`, `LevelingPlanner.java` | Truncated at the real final `}` |

None of this was found by reading Kimi's descriptions more carefully — all of
it was found by extracting the actual code and checking it against the real
files already in the repository, the same way every previous package in this
project was checked. That's the method, not a one-time cleanup: apply it
again to anything the next Kimi session produces.

## 13. Two structural gaps, not bugs — flagged for a decision

- **Tasks 9, 10, 11 have code but no integration patches.** They'll compile
  standalone but do nothing until something in `AIBrain` actually constructs
  and calls them. This is real, unfinished work, not a mistake to fix — budget
  it as its own task rather than assuming it's covered by "compile clean."
- **The `level` field is now added but not yet populated from real packet
  data** (§12, item 6). Everything depending on it will run using the default
  value of 1 until that's wired — same "compiles but the data isn't real yet"
  category as the original mock-data findings in the very first audit of this
  project.

## 14. After every layer compiles clean

Same smoke test as the previous package, extended: spawn → target → move →
fight → die → respawn → return to farm (Layers 0–4), then check inventory
management kicks in during that loop (Layer 5), then try a chat interaction
(Layer 6) and a town/vendor visit (Layer 7). Layers 9–11 have no hook to test
yet — that's the point of §13's first item.
