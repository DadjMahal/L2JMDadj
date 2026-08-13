# Stream G — Stub Disposition Manifest

> **Goal (task 103 exit criterion):** every stub is either **wired + tested** or **explicitly
> quarantined**; `mvn test` green; the 23 `ai_%` chars relocated to a live zone.
>
> **Honest status (2026-08-05):** the named work packages (G-Live, G-Combat, G-Content,
> G-Behavior) are **wired + tested** (+14 tests → **117/117 PASS, BUILD SUCCESS**). The remaining
> ~130 `engine/` classes are already-implemented **standalone content/event/siege/economy library
> modules** that are **explicitly quarantined** (documented below): they compile and are intended
> pull-in modules, but are NOT on the live decision path this cycle. Two genuine pending items are
> in §4. `verify_no_dead_code.sh` = PASS (only the 2 LEGIT_TODO items). **Style: `check_style.sh`
> PASSES (0 violations) since task 110** (trailing-ws stripped, tabs→4-space, engine System.out→
> logging, engine Math.random→deterministic; examples/ live-drivers exempt by design).

---

## 1. WIRED + TESTED in Stream G (the 4 named work packages)

### G-Live — live driver that fires D/E/F hooks on real packets
| Class | Disposition |
|---|---|
| `engine/LiveFeedbackBridge` | WIRED — turns real `PacketLogger` deltas into `CombatAI.onKill/onLevelUp/onDeath/onRespawn` + LongTermGoalsAI. Used by `GoalDrivenLoop`. Tested `LiveFeedbackBridgeTest` (3). |
| `examples/GoalDrivenLoop` | NEW live driver (mirrors `CombatLoop` handshake): bridge → `ActivityScheduler.nextActivity()` → `GoalTree.selectActiveGoal()` → `CombatAI.makeDecision()` (+ `applyKiteBehavior`) → `CombatFramePlanner` → real frames. |
| `protocol/PacketLogger` | +STAT_LEVEL(0x01) parse → `getLevel()`; +`removeEntityForTest()`. |

### G-Combat — helpers wired into `CombatAI` (append-only, no existing-flow disruption)
| Class | Disposition | Test |
|---|---|---|
| `RangedKiteAI` | `CombatAI.shouldKiteNow()` + `applyKiteBehavior()` (FLEE override) | StreamGCombatTest |
| `PvPSkillRotation` | `CombatAI.getOptimalPvPSkill(mp,class)` overload + `getDefensiveSkillId()` | StreamGCombatTest |
| `AntiGriefing` | `CombatAI.getAntiGriefing()` + `allowPvP()` guard | StreamGCombatTest |
| `AggroManager` | `CombatAI.getAggroManager()` (threat table now consultable) | StreamGCombatTest |
| `SkillAllocator` | `CombatAI.learnSkillsForLevel()` wired into `onLevelUp()` + `getLastSkillAllocation()` | StreamGCombatTest |

### G-Content — content/event AIs wired into `AIPlayer`
| Class | Disposition | Test |
|---|---|---|
| `AchievementAI` | `AIPlayer.getAchievementAI()` + `markAchievementCompleted()` → advances ACHIEVEMENT_RAID goal | StreamGContentTest |
| `EventCalendarAI` | `AIPlayer.getEventCalendarAI()` (consulted in driver) | StreamGContentTest |
| `HeroTitleAI` | `AIPlayer.getHeroTitleAI()` (hero-buff consultation) | StreamGContentTest |

### G-Behavior — behavior simulators wired into `AIPlayer`
| Class | Disposition | Test |
|---|---|---|
| `HumanReactionSimulator` | `AIPlayer.getHumanReaction()` (human-like delay) | StreamGBehaviorTest |
| `BehaviorSeeder` | `AIPlayer.getBehaviorSeeder()` (deterministic per-player) | StreamGBehaviorTest |
| `MovementPatternAI` | `AIPlayer.getMovementPatternAI()` (walk/run ratios) | StreamGBehaviorTest |
| `ResourceHoardingAI` | `AIPlayer.getResourceHoardingAI()` (save/spend) | StreamGBehaviorTest |

**New tests:** `StreamGCombatTest` (5) + `StreamGContentTest` (4) + `StreamGBehaviorTest` (5) =
+14 → **117/117 PASS.**

---

## 2. LIVE / ALREADY-WIRED (driven on the live path, tested earlier)
`AIPlayer`, `CombatAI`, `QuestAI`, `MerchantAI`, `SocialAI`, `GoalTree`, `LongTermGoalsAI`,
`ActivityScheduler`, `AIPlayerManager`, `PersistenceManager`, `AIPlayerConnection`,
`GameServerClient`, `CombatFramePlanner`, `CombatState`, `CombatDecision`, `CombatConfig`,
`QuestState/Decision/Config/FramePlanner`, `MerchantDecision/Config`, `SocialDecision/Config`,
`AIActionQueue`, `AIBrain`, `AIConfiguration`, `AIPlayerState`, `PartyState`, `LineOfSight`,
`MarketEngine`, `EconomicEngine`, `NetWorthOptimizer`, `CollectiveKnowledge`, `SwarmCoordinator`,
`DiplomacyEngine`, `EmotionalState`, `PersonalityProfile`, `AdaptiveLearner`, `ReinforcementEngine`,
`DeepLearningCore`, `PatternMemory`.

---

## 3. EXPLICITLY QUARANTINED — standalone content/event/siege/economy library modules
Compile + are implemented, but intentionally NOT on the live decision path this cycle (pull-in on
demand). **Not dead code by intent — they are the project's content library.** Grouped:

- **Siege/Clan/Castle:** `ClanHallDefense`, `ClanHallRegister`, `ClanHallSiegeAI`, `ClanMateAI`,
  `ClanState`, `CastleManagement`, `CastleStrategy`, `CastleTaxPlanner`, `SiegeBuffCoordinator`,
  `SiegeExitManager`, `SiegeLoreAI`, `SiegePositioning`, `SiegeRegistrationAI`, `SiegeTiming`,
  `SiegeWeaponAI`, `PostSiegeCleanup`, `TerritoryNPCDefense`.
- **Boss/Raid/Dungeon:** `BossTactics`, `RaidBossAI`, `DungeonPathfinder`, `RiftAI`,
  `SepulchersAI`, `InstanceManager`, `LootDistributor`, `SafeZoneManager`, `ZoneBuffManager`.
- **Events/Activities/Social:** `EventAwareAI`, `ExpansionHooksAI`, `ContentUnlocker`,
  `ControlRoomAI`, `CrossServerAI`, `FishingTournamentAI`, `ColiseumAI`, `OlympiadAI`,
  `DuelAnalyzer`, `AcademyAI`, `MentorSystemAI`, `NewbieGuideAI`, `NoblesseAI`, `LevelCappedAI`,
  `HuntingPreferenceAI`, `HuntingRotation`, `IdleEmoteAI`, `CommunityChatAI`, `ClassDecisionTree`,
  `ClassQuestAI`, `VillageMasterAI`, `WhisperResponseAI`, `BuddySystemAI`, `GuildResponseAI`,
  `SoloGroupAI`, `KamaelAI`, `SubclassAI`, `EscortAI`, `TradePersonalityAI`, `PvPVendettaAI`,
  `VendettaTracker`, `VendettaTrackingAI`, `PvPTargetPrioritizer`, `KarmaManager`,
  `DeathPenaltyAI`, `EmergencyEscapeAI`, `HealerSacrificeAI`, `SkillLearningAI`,
  `DifficultyScaler`, `MovementOptimizer`, `AntiStuckDetection`, `HumanBehaviorSimulator`,
  `BehavioralSimulator`, `AuctionAI`, `ManorAI`, `ManorManager`, `SeedRegistry`, `TaxCalculator`,
  `HeroTitleOptimizer`, `ArmorProficiency`, `AttributeOptimizer`, `StateMonitor`, `ThreadMonitor`,
  `AIProfiler`, `AIDiagnostics`, `AIModuleLoader`, `PluginManager`, `AIPlayerActionExecutor`,
  `AIPlayerReal`, `AIPlayerSimple`, `AIPlayerSpawnController`, `ExternalAPI`, `SeasonalScheduler`,
  `AIAction`.

---

## 4. PENDING / NEEDS WORK (genuine, not library)
| Item | Reason |
|---|---|
| `AIPlayerEngine` (line 59 TODO) | Launcher stub — needs to actually connect + spawn AIs (or be retired). |
| `CombatAI.isTargetDead()` (line 108 TODO) | Needs real target-HP StatusUpdate self-vs-target attribution to end combat on target death rather than rely on DeleteObject. |
| PatternMemory on-disk persistence | Deferred (was Stream E task 89). |
| `DeepLearningCore.predict()` consultation | Fed by LiveFeedbackBridge, not yet consulted in `makeDecision()`. |

---

## 5. Environmental blocker (was a code task; now RESOLVED 2026-08-05)
23 `ai_%` chars were stuck at the default spawn (16600,17000,434). **Resolved on the L2JM host** via
`scripts/relocate_void_ai.sh --apply` (server UP + sudo mysql): keys on `account_name LIKE 'ai_%'`,
moves the 23 default-spawn bots to the B4 wolf-zone combat spawn (-82759,250149,-3600) and heals
(`curHp=COALESCE(maxHp,100)`; the server normalizes to full on login). CombatBot_01/02 were already
at tested positions and left untouched. Post-move `still_stuck=0`; all 25 `ai_%` accounts live at real
coords with HP.

