# 15 — model/zone, world & misc model

Resume checkpoint
- Read files:
  - gameserver/model/World.java 221-460
  - gameserver/model/WorldRegion.java 1-220
  - gameserver/model/Location.java
  - gameserver/model/zone/Zone.java 1-200
  - gameserver/model/zone/type/{TownZone,SiegeZone,BossZone,FishingZone}.java top
  - gameserver/model/spawns/Spawn.java top
- Still to read:
  - remaining zone types, spawn trees, world/regions visibility details if needed.
- Next:
  - Write 15 and continue 16+.

---

## World/WorldRegion/Location
Purpose: Spatial index for all world objects and region-based observers.
Fields/State: _allObjects/_allPlayers in World; region grids in WorldRegion; adjacent neighbor lists; zone/instance info by region.
Public API: add/remove/find object; region init; foreach visible; get visible players/npcs/objects.
Control Flow: object add->region add->neighbor notify; remove->forget neighbors; region activation driven by visibility checks.
I/O: none.
Gotchas: add/remove semantics differ between _allObjects/_region object maps; region neighbor activation is critical for performance and visibility.

## Zone system
Purpose: Encapsulate gameplay regions with typed behavior and settings.
Fields/State: ZoneForm shapes; ZoneSettings includes damage/heal/action/block flags; zone id/map; entering/leaving counters; dynamic object references; instance/castle sieges/siege references for integration.
Public API: isInside/check/validate area; creature/player enter/exit; get zone id/type; set/get dynamic/custom parameters; apply abnormal/damage/heal effects; listeners for enter/exit; zone creation from DB/data.
Control Flow: Creature.setInsideZone increments counters synchronized; Spawn/AI/managers register listeners; damage/heal hooks for DamageZone/FishingZone; ZoneManager by id mapper; thread safety on counters.
I/O: DB-backed zone loaders for castle/siege/town; packet broadcasts for zone effects/managers.
Gotchas: many concrete types override specific hooks; performance decisions in tight loops via shape caching.

## Spawn.java
Purpose: Runtime NPC/resource spawn manager with respawn/location/template linkage.
Fields/State: template, constructor, name, max/current count, scheduled/respawn delays, chase/random walk flags, location id, territory, spawn listeners, spawned npc deque.
Public API: amount, name, respawn delays, spawn/despawn/respawn/teleport coordination; template-based constructor resolution; territory bounds; event listeners.
Control Flow: spawning via template.class forEach or deterministic constructors; timed respawn tasks; territory bounds checks; listeners registry.
I/O: template XML/data; tasks scheduled at fixed rate/delay; DB updates for stateful spawns if needed.
Gotchas: class name resolution from template type is fragile; constructor caching throws ClassNotFound/Cast across versions; spawn listeners are shared static set.

## Where to change X
- Region visibility tuning? World/WorldRegion neighbor activation + forEachVisibleObject helper.
- Zone gameplay rule? Type-specific zone class + ZoneList effects; check isInside vs shape forms.
- NPC spawn campaign? Spawn class + territory/npc spawn loader; respawn timing via task.
- Location teleport/geo? Location immutable holder; GeoEngine pathfinding hooks from Creature.
- Global world state queries? World.getPlayers/findObject/npc getters.

---
