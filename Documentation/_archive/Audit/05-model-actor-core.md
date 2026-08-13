# 05 — model/actor core

Resume checkpoint
- Read files:
  - gameserver/model/actor/Creature.java 1-1040
  - gameserver/model/actor/Player.java 1-120
  - gameserver/model/actor/Npc.java 1-120
  - gameserver/model/actor/Playable.java 1-120
  - gameserver/model/actor/Attackable.java 1-120
  - gameserver/model/actor/Summon.java 1-120
- Still to read:
  - Remaining key sections of Player/Creature, templates, stat/status classes, influence of AI classes.
- Key structural findings so far:
  - Creature is the base concrete abstraction for all dynamic world objects with template-linked stats/status.
  - WorldObject -> Creature -> Playable -> Player/Summon; Npc/Attackable branch from Creature.
  - Actor model delegates heavily to CreatureAI/Intention, event holders, network packets, calculators, geo/pathfinding.
- Next step:
  - Write template-structured summary for inspected actor core subset and move on.

---

## Creature.java
Purpose: Base concrete character object in the world for PCs, NPCs, doors, static objects, traps, vehicles, summons.
Fields/State: attackByList, casting flags, dead/immobilized/overloaded/paralyzed/teleporting/invul/mortal/flying; charStatus/charStat; template; calculators; zones enum counts; AI; effects; movement; target; attack time values; seenCreatures; relation cache; fake drops; decoupled event holders.
Public API Surface: inventory hook, destroyItem checks, zone getters/setters, isInsideZone, GM/access default stubs, onDecay, onTeleported, broadcastPacket/SocialAction/MoveToLocation, hpStatus scheduling, teleportToLocation with geo/pathfinding offset logic, doAttack with event interception and casting/bow logic, doAttackHit with damage calc/soulshots/skills, magic failures completion, various death helpers.
Control Flow: Construction binds template, skills, and calculators based on instance subtype. Attack uses StampedLock. Magic uses scheduleAtFixedRate-style task queue. Broadcast throttles move packets per game ticks.
I/O: network packet sends; GeoEngine/pathfinding calls; ThreadPool task scheduling; event listeners.
Gotchas/Refactor Candidates: very large class over 7k lines, contains all combat/zone/move/magic behavior including Player-specific concepts; likely causes feature cross-talk; many TODOs suggesting incomplete cleanups.

## Playable.java
Purpose: Intermediate abstraction between Creature and Player/Summon for shared playable-state behaviors.
Fields/State: lockedTarget, transferDmgTo holder.
Public API Surface: stat/status typed overrides; doDie intercepts death event and kills target multitime.
Control Flow: dispatches kora death checks.
Gotchas/Refactor Candidates: thin wrapper so far.

## Summon.java
Purpose: Abstract pet/summon runtime entity owned by a player.
Fields/State: owner, attackRange, follow flags, restoreSummon, shotsMask, abnormal effect task, passive summon IDs.
Public API Surface: constructor sets instance id, spawns valid location; attack range control; shot type; owner tracking; death/disappear helpers.
I/O: geo/pathfinding; packet streams for pet info; item/shot consumption via handler; manage timed tasks.

## Player.java
Purpose: Core human-controlled character; largest runtime object in the server.
Fields/State: Based on imports/size 14k lines: account/session, GM/admin permissions, translations, DB-heavy primitives, collections for skills/effects/quests/recipes/warehouses/inventory/multisell/timers/olympiad/fishing/marriage/achievements/variables/punishments/commands/HTML state/hardware/premium/offline/pay/billing interfaces; many ScheduledFutures; atomic booleans/ints; concurrent collections; BBS forum states.
Public API Surface: lifecycle load/create/delete/select/restore/enter; actions for movement/interact/chat/trade/store/combat; packet send helpers; quest/instance/Olympiad/Raid state getters; targeting, title, mood, access levels, script event notify methods, etc.
Control Flow: CharacterSelect.load creates Player from DB, sets client, restores location, sends CharSelected; sends multiple state packets.
I/O: heavy DB access via GameClient and tables; network ServerPacket streams; event/dispatch; scheduled tasks; multithreaded online/offline loops.
Gotchas: 14,096 lines is highest-case-file; changes require understanding many systems in one class; many overloaded methods.

## Npc.java
Purpose: Base non-player character runtime entity with gameplay interactions outside direct player scripts.
Fields/State: depotId, chestId, instance/enemy/leader references; bans/is preventing/against relation; minimap flags, private store fields, menus, timers; DecayTaskManager handles lifespan.
Public API Surface: templates, skills/items overloaded/broadcasts, buying/selling handling via broadcast menus, teleporting chat handling, custom interactions, respawn behavior.
Control Flow: inherits Attackable methods through subclass tree; AI drives intents; timer-driven decay; events around spawn/death/teleport.
Gotchas: large; many override points for merchant/teleporter/trainer/etc instance types; changes affect any subclass via overrides.

## Attackable.java
Purpose: Aggressive/monster NPC behaviour including drops and over-hit.
Fields/State: raid flags, champion, aggro list, returnToSpawn, seeThroughSilentMove, seeded/sweep items/overhit data, command channel timer, soul crystal absorbers, damage done tracking, rewards, rewardables lists, quest item holders, elite type.
Public API Surface: aggro handling, think/attack commands, attackableThinkAI hooks, social IDs, reward definitions, corpse decay sweep, champ/elite features, command channel coordination, over-hit tracking, raid/minion behavior, respawn/event reward dispatching.
Control Flow: aggro list modifications by AI intent; event dispatches for kill/attack/aggro-range enter; rewards drop handling leveraging ItemManager, ScheduledDrop; state transitions overridden from Npc/EventDispatcher AND from AI tasks.
I/O: timer, items/spawn DB-backed reward generation,_packet broadcasts; manager hooks to castle/zone/instance; multi-current event holders wrapped with TerminateReturn.

## Vehicle.java / Tower.java
Purpose: specialized placeholders; Vehicle adds flight/vehicle occupant data; Tower adds siege minimal behavior.

## Inheritance overview

| Class | Extends | Key subtype behavior |
|-------|---------|---------------------|
| Creature | WorldObject | base life stats, movement, attacks, spells |
| Playable | Creature | split attackable/monster logic; death+cancel semantics |
| Player | Playable | account, UI, social/party/marriage/quest systems |
| Npc | Creature | merchant, interaction, menus, custom AI/buy lists |
| Attackable | Npc | aggro, drops, over-hit, command channel, rewards |
| Summon | Playable | owner, pet inventory, follow, shots, passives |
| Vehicle | Playable | air/sea/craft/flying mechanics |
| Tower | Creature | siege structure |

## Where to change X

- Add player-specific runtime behavior? Player class 14k; expect cross-domain effects.
- Change combat/zone/AI behavior? Creature/Attackable base methods and event holders.
- Change merchant talk/shop logic? Npc template hooks / handler system.
- Change drop/aggro? Attackable only; holders/aggro list + reward dispatching.
- Add new controllable unit? subclass Playable or Npc depending on behavior category.
- Change world registration or target tracking? Creature target/move methods, World.addVisibleObject/removeVisibleObject.
- Modify exp/loot formulas stat tables? stat/status/template + Formulas usage from Creature.

---

## Targeted deeper Creature slices

### Core fields focused
- `_stat`/`_status`: per-creature runtime stat/status objects initialised in constructor.
- `_skills`, `_reuseTimeStampsSkills`, `_reuseTimeStampsItems`, `_disabledSkills`: concurrent skill/item rollover maps.
- `_zones` byte array indexed by `ZoneId.ordinal()`; zone accumulation under `synchronized (_zones)`.
- `_attackLock` is a `StampedLock` for doAttack serialization.
- Casting state uses `volatile` flags plus pending futures.
- Relation cache / seen creatures reuse concurrent containers; fake player drop list uses `CopyOnWriteArrayList`.

### Constructor/template contract
- `Creature(int objectId, CreatureTemplate)` delegates: `id`, `InstanceType`, empty stat/status init, template assignment.
- Door/NPC/Player/Summon branches set calculator arrays differently: door standard, NPC default set, playable blank+addition functions, plus copy skills for NPC/summon.
- All paths set invul true on construction; invul cleared later by specific overrides/actions.

### Behavior entry points seen
- `onDecay` -> death logout OR decayMe + zone unlink under `PlayerConfig.DISCONNECT_AFTER_DEATH`.
- `onSpawn` -> buff finish task, revalidate zone, grand boss/raid announcement rules via broadcast.
- Decay/task/spawn event hooks available as listeners.
- `broadcastPacket` uses `World.forEachVisibleObject` player subset.
- `broadcastMoveToLocation` throttles moderate ticks; switches between MoveToLocation and MoveToPawn depending on intention/geodata path.
- `broadcastStatusUpdate` iterates status listeners; HP bar update throttling tied to MAX_HP_BAR_PX.

## Targeted deeper Npc slice

- Weapon accessors choose NpcTemplate RHand/LHand and require `Weapon` casting; otherwise null; supports merchant-like lookups.
- Chat/menu system: HTML path resolution under `data/html/default/<npcId>-page.htm` or `data/html/npcdefault.htm`; cache aware if configured.
- Special-case seven signs convergence for Mammon NPCs at 31113/31126; player karma checks block select NPC types by config.
- Notable patterns: heavy NPC uses `instanceof Merchant/Teleporter/Warehouse/Doorman/ClanHallManager/Fisherman` — if you add merchant-like behavior expect these branches.

## File size signals in iteration 05

| File | Lines | Implication for next steps |
|------|-------|----------------------------|
| Player.java | ~14.1k | Largest class; keep reading in key regions rather than whole file |
| Creature.java | ~7.2k | Core instability target; changes affect everyone |
| Npc.java | ~2.1k | Merchant/chat/teleporter concrete hook per subclass |
| Playable.java | ~352 | Stable split point |
| Attackable.java | ~1.8k | Drop/aggro/overhit region |
| Summon.java | ~1.2k | Pet behavior |
| Vehicle.java | ~508 | Vehicle mechanics |
| Tower.java | ~78 | Siege placeholder |

