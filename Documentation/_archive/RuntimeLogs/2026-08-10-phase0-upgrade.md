# RuntimeLog — 2026-08-10 phase0 upgrade: reconcile + compile + selective wiring

## Prompt / objective
User: "do an upgrade first what you think we need to upgrade; then write what we
didn't integrate and why; state iterations needed." Local machine, paths differ
(laguna's applied `phase0` ≠ Claude reference; nothing wired; uncommitted deps).

## What was done (verified)
- Diagnosed: laguna's `phase0` (111 files) did NOT compile + was NOT wired into
  the live `AIPlayer`/`CombatAI` path + pom.xml added dead jedis/postgres deps.
- Fixed compile across 12 files (uncaught `IOException` from `L2JProtocol`,
  final-field/init-order bugs):
  SoulshotRestocker, InventoryTracker, StuckDetector, Phase0Brain,
  ConsumableManager, AutoLootHandler, DeathHandler, KiteController,
  SocialBehaviorEngine, PartyInviteHandler, PartyMemberInfo, TownNavigator,
  WarehouseManager, RespawnManager, BuyManager, SellManager, TeleportManager.
- Removed dead `jedis`+`postgresql` deps from pom.xml (no real imports anywhere).
- Restored missing `examples/Phase0Driver.java` (reference driver entry point).
- Fixed 3 latent data/correctness bugs:
  * ItemSnapshot.from now treats `ItemType.UNKNOWN` as unknown -> honest "item#<id>".
  * BotSnapshotTest empty-logger assertions now round-trip real getters.
  * ItemDatabase soulshot/spiritshot/blessed IDs aligned to ShotManager
    (was shifted/missing; 1835 No-Grade was absent; spiritshots 3947+ were
    mislabeled as regular instead of blessed).
- Fixed `scripts/verify_no_dead_code.sh` to be path-independent (was /home/volodro).

## Results
- `mvn test`: **129/129 PASS, BUILD SUCCESS**.
- `check_style.sh`: PASSED (0 violations).
- `verify_no_dead_code.sh`: BUILD SUCCESS (7 tracked LEGIT_TODO only).

## NOT wired (by design / gated — see chat report)
- Phase0Brain (regression vs proven CombatAI/AIBrain).
- Farm/zone/quest XP analytics (need real StatusUpdate/UserInfo XP parse = `currentXp`).
- Inventory/action wire opcodes (skill-cast, item-use not live-proven).
- GameStateMirror->BotSnapshot migration of remaining ~files (large, blind).
- Fleet wiring into AIPlayerManager (Phase0Driver is single-bot reference).

## Remaining
- Repoint ~36 more scripts + dashboard from /home/volodro/L2JM to this repo.
- Server relaunch needs JDK 25 (jars are class-version 69) + MariaDB setup + DB import.
- Live-run `Phase0Driver` against the running server to prove the phase0 loop.

## Next steps
Server relaunch (JDK 25 + MariaDB), path migration bulk, then live Phase0Driver.
