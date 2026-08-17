# MODE:PARTIAL triage index (S1-T06)

The engine repo marks files as `MODE: PARTIAL` = "compiles, follows reviewed patterns, NOT re-verified line-by-line".
This is a triage map (not an audit) so waves can target them one at a time. Total: **95 files** in 16 packages.

| Package | Files | Count | Action hint (see S10-T08) |
|---|---|---|---|
| `phase0/brain` | ArcherPreset.java, BotState.java, BufferPreset.java, ClassPreset.java, FighterPreset.java, HealerPreset.java, MagePreset.java, Phase0Brain.java, PresetFactory.java, StateMachine.java | 10 | re-verify with tests -> COMPLETE |
| `phase0/cabinet` | BotProfile.java | 1 | re-verify with tests -> COMPLETE |
| `phase0/chat` | ChatEngine.java, Intent.java, IntentClassifier.java, Persona.java, ResponseTemplate.java | 5 | re-verify with tests -> COMPLETE |
| `phase0/combat` | ArcherRotation.java, BufferRotation.java, CombatRotation.java, FighterRotation.java, HealerRotation.java, MageRotation.java, PartyAssistTracker.java, RotationFactory.java, ShotManager.java, SkillDatabase.java, SkillInfo.java, TargetScore.java, TargetSelector.java | 13 | re-verify with tests -> COMPLETE |
| `phase0/death` | DeathHandler.java, Graveyard.java, GraveyardRegistry.java, RespawnManager.java | 4 | re-verify with tests -> COMPLETE |
| `phase0/director` | DirectorAI.java, NameGenerator.java | 2 | re-verify with tests -> COMPLETE |
| `phase0/farm` | DynamicZoneManager.java, FarmSessionRecorder.java, FarmZoneScorer.java, OptimalSpotSelector.java, RespawnTimer.java, ZoneDensityTracker.java | 6 | re-verify with tests -> COMPLETE |
| `phase0/humanize` | AntiDetectionEngine.java, BehavioralFingerprint.java, HumanizedRandom.java, InputRandomizer.java, SessionVariance.java, TimingJitter.java | 6 | re-verify with tests -> COMPLETE |
| `phase0/imperfection` | AFKModule.java, ImperfectionInjector.java, ReactionDelay.java | 3 | re-verify with tests -> COMPLETE |
| `phase0/inventory` | AutoLootHandler.java, ConsumableManager.java, InventorySnapshot.java, InventoryTracker.java, ItemDatabase.java, SoulshotRestocker.java, WeightMonitor.java | 7 | re-verify with tests -> COMPLETE |
| `phase0/movement` | BezierCurve.java, HumanizedPath.java, KiteController.java, MovementController.java, MovementState.java, PathNode.java, StuckDetector.java | 7 | re-verify with tests -> COMPLETE |
| `phase0/party` | ClanChatHandler.java, PartyCoordinationEngine.java, PartyLootDistributor.java, PartyManager.java, PartyRole.java, SiegeParticipationStub.java | 6 | re-verify with tests -> COMPLETE |
| `phase0/protocol` | PacketJitter.java, Phase0ProtocolExt.java | 2 | re-verify with tests -> COMPLETE |
| `phase0/quest` | ClassChangeManager.java, LevelingPlanner.java, QuestDatabase.java, QuestExecutor.java, QuestInfo.java, QuestProgressTracker.java, QuestRewardEvaluator.java, ZoneRecommender.java | 8 | re-verify with tests -> COMPLETE |
| `phase0/social` | ChatFilter.java, ChatHistory.java, ChatMessage.java, ChatPersonality.java, ChatResponder.java, PartyInviteHandler.java, SocialBehaviorEngine.java, SocialTimer.java | 8 | re-verify with tests -> COMPLETE |
| `phase0/town` | BuyManager.java, SellManager.java, TeleportManager.java, TownBehaviorEngine.java, TownNavigator.java, VendorDatabase.java, WarehouseManager.java | 7 | re-verify with tests -> COMPLETE |

Rule: a file moves off this index ONLY by gaining test coverage and flipping its header to `MODE: COMPLETE` (or being archived via S10-T01/T02).
