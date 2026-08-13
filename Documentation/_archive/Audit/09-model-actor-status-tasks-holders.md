# 09 — model/actor status, tasks & holders

Resume checkpoint
- Read files:
  - gameserver/model/actor/status/* tree
  - gameserver/model/actor/tasks/* tree
  - gameserver/model/actor/holders/* tree
- Still to read:
  - Representative classes: CreatureStatus, PlayerStatus, AttackableStatus, tasks/player, holders/npc, holders/player
- Key findings so far:
  - status/ tasks/ holders split actor logic into lifecycle, scheduled tasks, and auxiliary data.
  - Many specialized holders indicate high state fragmentation; changes need holder mapping.
- Next: continue direct iteration by adding representative reads.

---

## status map

CreatureStatus -> base HP/MP/CP synchronization, death, packet updates.
PlayableStatus -> extends death, CP exhaustion, alive checks for player-like roles.
PlayerStatus -> PvP/duel/summon damage transfer, mana shield, seated/private break; numerous packet events.
AttackableStatus -> aggro/decay/hate state mapping and reward broadcasting.
DoorStatus, NpcStatus -> interpolation between generic world object and NPC-specific unit tasks.
FolkStatus, StaticObjectStatus, SiegeFlagStatus -> serializer light actions, unqiue entity status speech/outputs.
Client-facing status updates are sent through StatusUpdate implementations; patch large updates.

## tasks map

Player tasks: StandUpTask, SitDownTask, DismountTask, RentPetTask, VitalityTask/FameTask are period updates affecting behavior and loads; IllegalPlayerActionTask enforces gameplay behavioral patterns.
Creature tasks: MagicUseTask/QueuedMagicUseTask form skill delivery stack; HitTask ensures combat delivered sequentially; NotifyAITask drives state transitions after attacks or events.
NPC tasks: Walker arrived tasks for pathing; trap tasks for triggers/de-summoning are summarized per type.
Cubic tasks manage healing and active cubic action states.

## holders map

NPC holders: AggroInfo captures aggro targets afterwards; DropHolder holds item templates and rates; DropGroupHolder organizes grouped drop tables. MinionHolder/minion list/route grouping manage minions, random walks, and cultivated walk routes. FakePlayerHolder rewrites fake player personality display; EventDropHolder connects dropped items to events and world states.
Player holders: BlockList/contact lists reference friends/blocked; macros define macro command names; Shortcut/MacroList store action shortcuts; Duel tracks duel state; couple/heart bonds; class info holds preferred class state; cursed weapon holder supports specific scripts; AutoPlay/AutoUse support system hooks; recovery bonuses track daily/weekly returns. SubClassHolder drives subclass metadata; SummonRequestHolder queues summons.

## where-to-change table

- Change daily reset/auto tasks? -- task classes per role + scheduled timer wrappers.
- Extend aggregated party/duel state? -- duel/class-type holders.
- Alter aggro/hate data? -- AggroInfo + AttackableAI.
- Adjust NPC drop/route/pathing? -- DropHolder/FakePlayerHolder/WalkRoute.
- Persist player social/toolbar state? -- Shortcut/MacroList/BlockList holders.
- Extend buff/effect/cubic state or schedule? -- cubic tasks + effect holders from elsewhere in model.

---
