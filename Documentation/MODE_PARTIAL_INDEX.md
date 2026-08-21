# MODE:PARTIAL triage index (S1-T06; paths refreshed EP-8)

The engine repo marks files as `MODE: PARTIAL` = "compiles, follows reviewed patterns, NOT re-verified line-by-line".
This is a triage map (not an audit) so waves can target them one at a time. Total: **95 files** in 16 packages
(file count unchanged by the EP-3 rename / EP-5 merges — classes moved or nested, not deleted).

| Package (post-EP-3) | Files | Count | Action hint (see S10-T08) |
|---|---|---|---|
| `behavior/` root (brain row) | BotBrain.java (was Phase0Brain), BotState.java, ClassPreset.java (absorbed ArcherPreset/FighterPreset/MagePreset/HealerPreset/BufferPreset/PresetFactory), StateMachine.java | 4 | re-verify with tests -> COMPLETE |
| `core/` (cabinet row) | BotProfile.java | 1 | re-verify with tests -> COMPLETE |
| `behavior/` (director row) | Director.java (DirectorAI + NameGenerator merged) | 1 | re-verify with tests -> COMPLETE |
| `behavior/social` (chat row) | ChatEngine.java, Intent.java, IntentClassifier.java, Persona.java, ResponseTemplate.java | 5 | re-verify with tests -> COMPLETE |
| `behavior/combat` | ArcherRotation.java, BufferRotation.java, CombatRotation.java, FighterRotation.java, HealerRotation.java, MageRotation.java, PartyAssistTracker.java, RotationFactory.java, ShotManager.java, SkillDatabase.java, SkillInfo.java, TargetScore.java, TargetSelector.java | 13 | re-verify with tests -> COMPLETE |
| `behavior/lifecycle` (death row) | DeathHandler.java, Graveyard.java, GraveyardRegistry.java, RespawnManager.java | 4 | re-verify with tests -> COMPLETE |
| `behavior/hunting` (farm row) | DynamicZoneManager.java, FarmSessionRecorder.java, FarmZoneScorer.java, OptimalSpotSelector.java, RespawnTimer.java, ZoneDensityTracker.java | 6 | re-verify with tests -> COMPLETE |
| `behavior/humanize` | AntiDetectionEngine.java, Humanization.java (nested: HumanizedRandom, BehavioralFingerprint, SessionVariance, ImperfectionInjector + ReactionDelay/AFKModule), InputRandomizer.java, TimingJitter.java | 4 | re-verify with tests -> COMPLETE |
| `behavior/inventory` | AutoLootHandler.java, ConsumableManager.java, InventorySnapshot.java, InventoryTracker.java, ItemDatabase.java, SoulshotRestocker.java, WeightMonitor.java | 7 | re-verify with tests -> COMPLETE |
| `behavior/movement` | BezierCurve.java, HumanizedPath.java, KiteController.java, MovementController.java, MovementState.java, PathNode.java, StuckDetector.java | 7 | re-verify with tests -> COMPLETE |
| `behavior/party` | ClanChatHandler.java, PartyCoordinationEngine.java, PartyLootDistributor.java, PartyManager.java, PartyRole.java, SiegeParticipationStub.java | 6 | re-verify with tests -> COMPLETE |
| `protocol` | PacketJitter.java, ProtocolExt.java (was Phase0ProtocolExt) | 2 | re-verify with tests -> COMPLETE |
| `behavior/quest` | ClassChangeManager.java, LevelingPlanner.java, QuestDatabase.java, QuestExecutor.java, QuestInfo.java, QuestProgressTracker.java, QuestRewardEvaluator.java, ZoneRecommender.java | 8 | re-verify with tests -> COMPLETE |
| `behavior/social` (social row) | ChatFilter.java, ChatHistory.java, ChatMessage.java, ChatPersonality.java, ChatResponder.java, PartyInviteHandler.java, SocialBehaviorEngine.java, SocialTimer.java | 8 | re-verify with tests -> COMPLETE |
| `behavior/town` | BuyManager.java, SellManager.java, TeleportManager.java, TownBehaviorEngine.java, TownNavigator.java, VendorDatabase.java, WarehouseManager.java | 7 | re-verify with tests -> COMPLETE |

Rule: a file moves off this index ONLY by gaining test coverage and flipping its header to `MODE: COMPLETE` (or being archived via S10-T01/T02).
