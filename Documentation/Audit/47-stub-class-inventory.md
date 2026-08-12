# Audit 47 - stub / unwired / dead-code inventory (honest)

Date: 2026-08-12 . Lane: task_0020 . No refactors or deletions performed.

## A. Explicit protocol stubs (LOGGER.warning "Not implemented - stub")
- AIPlayerEngine/.../protocol/L2JProtocol.java has 26 stub methods (lines ~414-531):
  talkToNpc, sendUseItem, sendAutoSoulShot, sendPickupItem, sendSellItem, sendBuyItem,
  sendDepositItem, sendWithdrawItem, sendAnswerJoinParty, acceptPartyInvite, declinePartyInvite,
  sendTeleportRequest, sendTeleportConfirm, sendRollRequest, sendRequestRestartPoint,
  sendRequestItemList, sendMoveBackwardToLocation, sendCameraDelta.
  - Status: STUB. Reference: not called (grep finds no callers). W4 (protocol) workstream dependency.

## B. MODE: PARTIAL classes (compiled, patterns-reviewed, not independently re-verified)
- phase0/brain: PresetFactory, Phase0Brain, BotState, StateMachine, ClassPreset, MagePreset,
  ArcherPreset, HealerPreset, BufferPreset, FighterPreset (header MODE: PARTIAL).
- phase0/social: ChatMessage, SocialBehaviorEngine, PartyInviteHandler, ChatResponder, SocialTimer,
  ChatHistory (read GameStateMirror, not-yet-migrated -- see INTEGRATION_GAPS.md).
- phase0/town/WarehouseManager (deposit/withdraw use a placeholder objId per ItemSnapshot javadoc).
- phase0/ItemSnapshot: objId is a placeholder, not a real instance id (lines ~53-62).
- phase0/quest/QuestRewardEvaluator: item-reward utility is Phase-1 placeholder.
- phase0/inventory/SoulshotRestocker: automated restock returns false (line ~188 "not yet implemented").

## C. Small / likely-unwired classes (plausibly-wired vs truly-dead to confirm by callgraph)
- advanced/AcademyAI (30L), advanced/AchievementAI (34L), economy/NetWorthOptimizer (97L),
  advanced/ReinforcementEngine (81L), advanced/AdaptiveLearner (107L), social/SwarmCoordinator (119L),
  social/CollectiveKnowledge (101L), economy/EconomicEngine (125L), engine/ActivityScheduler (100L).
  - These are referenced by specs/goal trees but need a callgraph pass to separate wired from truly-dead.

## Count
- Explicit protocol stubs: 26 . MODE/PARTIAL+placeholder headers: ~28 . Candidate unwired small classes: ~9.
- Recommendation: dedicated W-lane to (1) wire or delete the 26 protocol stubs, (2) run a callgraph to
  classify section C, (3) migrate social/brain off GameStateMirror.
