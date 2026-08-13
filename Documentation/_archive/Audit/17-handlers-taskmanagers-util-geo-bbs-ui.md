# 17 — handlers, taskmanagers, scripting/util/geo/cache/bbs/ui

Resume checkpoint
- Read files:
  - handlers tree listing, ItemHandler top
  - taskmanagers tree listing, AttackStanceTaskManager full
  - util/geo/cache/communitybbs/ui trees
- Still to read:
  - ScriptManager top missing; retry read later.
- Next:
  - write structured mapping and move on.

---

## Handlers
Purpose: Dispatch typed client/server/quest/admin actions without embedding logic in packet classes.
Public API: registry by name or item type/class; lookup by item/packet/command key; threaded invocation.
Key classes:
- IHandler<I,H> contract with register/remove/getHandler/size.
- ItemHandler/BypassHandler/ChatHandler/AdminCommandHandler/VoicedCommandHandler/TargetHandler/ActionClick/ActionShift/EffectHandler/CommunityBoardHandler/PunishmentHandler.
Control Flow: packet/business logic calls handler.getHandler(x).handle(y); handlers resolved at runtime via name mapping/ETC type and config overrides.
I/O: config-bound availability; itemhandler reads from item type metadata; some handlers send packets/log.
Gotchas: keyed by simple class name string -> fragile refactor; missing missing-handler path logging.

## Taskmanagers
Purpose: Periodic/system timers driving gameplay loops and cleanup.
Representative implementations:
- AttackStanceTaskManager: 1s fixed-rate scan of combat stance timestamps; after 15s issues AutoAttackStop; include summon support; uses ConcurrentHashMap + working flag serialization.
- GameTimeTaskManager: global game ticks based on real seconds; provides ticks/seconds/day-night phases; consumers adapt movement/magic/login.
- MovementTaskManager/DecayTaskManager/ItemsAutoDestroyTaskManager/ItemLifeTimeTaskManager/ItemManaTaskManager/PlayerAutoSaveTaskManager/PvpFlagTaskManager/CreatureSeeTaskManager/CreatureFollowTaskManager/BuyListTaskManager/RandomAnimationTaskManager/AutoPlayTaskManager/AutoPotionTaskManager/AutoUseTaskManager/WaterTaskManager/FishingTaskManager/RestoreItemTaskManager/MountReuseTaskManager.
Control Flow: mostly `scheduleAtFixedRate`/`schedulePriorityTaskAtFixedRate`; consume ThreadPool from commons.
I/O: network sends sometimes; iterate world collections, may touch DB on automations.
Gotchas: many static concurrent maps with creature keys; missing shutdown cancellation; heavy world iteration in single threads.

## Scripting/util/geo/cache/bbs/ui snapshot
Purpose: scripting, geodata, caching, community board UI.
Scripting: ScriptEngine, annotations Disabled indicate gating; saves scripts/timers/quest states; script loaders read from dist or classpath.
Util: Broadcast central broadcast helper; FloodProtectorSettings/Action per command enforces rate-limit normality; FormatUtil/LocationUtil/GeoUtils/MapUtil/MathUtil helpers.
Geo: GeoEngine height/canSee/canMove/getValidLocation; pathfinding by PathFinding/GeoNode/NodeBuffer/GeoLocation/IRegion/Cell/IBlock.
Cache: HtmCache html; RelationCache relation.
CommunityBBS: BB model Post/Topic/Mail/Forum + manager Orchestration ForumsBBSManager/Post/Topic; board access with DB-backed posts.
UI: Swing Gui/LogPanel/AboutFrame/SystemPanel.
I/O: geodata files parsed by engine; cache over disk/data; BBS DB-backed; gui redirection.
Gotchas: Singleton everywhere; lazy inits； geodata misses may teleport incorrectly.

## Where to change X
- Add new player action hook? corresponding handler class entry + packet routing.
- Periodic rule/timer? taskmanagers singleton, schedule in GameServer bootstrap.
- Pathfinding/height correctness? GeoEngine + pathfinding classes.
- Community board new features? BB managers + forum/forum type config.
- UI behavior? Gui/LogPanel + redirected stdout wrappers.

---
