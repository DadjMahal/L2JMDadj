# Audit 48 - W5 quest-NPC navigation: staged feasibility plan

Date: 2026-08-12 . Lane: task_0024 . Planning only, NO code changes.

## Goal
Bot "runs to" quest NPCs / towns and drives quests via RequestBypassToServer (0x21) + HTML/bypass.
Today this is UNIMPLEMENTED (bots only attack/wander; see Audit/30 + Audit/15).

## Dependencies
- W1 (data): NPC/teleport/landmark data resolved so the nav layer knows WHERE quest NPCs/towns are.
  Today ZoneRecommender/LandmarkResolver provide movement targets for farming, not quest givers.
- W4 (protocol stubs): L2JProtocol has 26 "Not implemented - stub" methods; the quest path needs
  talkToNpc + bypass (RequestBypassToServer 0x21) and HTML/bypass response parsing wired for real.
- Tim003 hop nav: reuse ZoneRouter.buildHops() to walk to the NPC in <=4800u hops (server-cap safe).

## Minimal viable path (staged)
1. Stage A - reach quest giver: pick NPC from landmark/quest data, ZoneRouter.plan to its coords from
   server-acked pos, walk hops (FleetPlay hop loop, reuse).
2. Stage B - talk: implement L2JProtocol.talkToNpc (0xb0 talk / 0xb8 NPC_HTML), parse NPC_HTML reply.
3. Stage C - bypass: implement RequestBypassToServer (0x21) sending the chosen bypass; parse resulting
   HTML/quest journal (QuestProgressTracker + WPT-27 QUEST_LIST 0x80 already parse journal).
4. Stage D - loop: wander-free goal planning that alternates travel - talk - accept - kill - turn in.

## Files to touch
- protocol/L2JProtocol.java (+talkToNpc/+RequestBypass 0x21), protocol/PacketLogger.java (HTML),
  phase0/quest/QuestProgressTracker.java + LevelingPlanner.java (goal wiring),
  phase0/town/WarehouseManager.java (town-side), examples/FleetPlay.java (bot loop staging),
  scripts/reset_fleet_xp.sh baseline unaffected.

## Risks
- Server may rate/categorize repeated 0x21 by a bot; keep hops ack-gated and bypasses sequential.
- HTML/bypass grammar is server-datapack-specific; parse defensively, never hard-code full scripts.
- Quest state can branch; log at each transition and keep evidence hooks (WPT-27 telemetry) so a
  turn-in failure is visible rather than silently looping.
- Movement-to-NPC must remain server-ack-gated (TIM-001 lesson) or NPC nav will not persist.
