# 14 — model/olympiad & sevensigns

Resume checkpoint
- Read files:
  - model/olympiad/Olympiad.java top
  - model/olympiad/Hero.java
  - model/sevensigns/SevenSigns.java top
  - model/sevensigns/SevenSignsFestival.java top
  - package file lists
- Still to read:
  - OlympiadGame/OlympiadStadium/OlympiadManager, SevenSigns subevents.
- Key findings so far:
  - Olympiad uses DB-backed nobles/ranks, scheduled tasks, registries by class/non-class, winner hero promotion, periodic data persistence.
  - SevenSigns has cyclic periods, festival spawn tables, sealed cabal state, DB-backed data.
- Next: write structural summary and continue.

---

## Olympiad.java
Purpose: Top-level manager for Olympiad cycle, noble rankings, registrations, matches, hero elections, and DB persistence.
Fields/State: nobles map, rank map, hero list, class/non-class registrant maps, periodic timers, competition windows, validation/cycle/end dates.
Public API: scheduled cycle tasks; noble load/save/update; registration entry/removal by class/non-class; game start/stop/finish; reward points/hero calc.
I/O: olympiad_data, olympiad_nobles, characters tables; broadcast packets for matches; event listeners; zone/manager mapping.
Gotchas: large static state spanning multiple maps; periodic DB write on cycle validation end; thread pools/scheduled futures for match management; effects on siege/hero system which are very coupled with game subspecies; potential stale registrations.

## Hero.java
Purpose: H track accumulable hero-related state and system message updates for recognized hero counts/winners.
Fields/State: hero count and counts by hero type, pending counts; sentence counts by hero counts.
Public API Surface: getters/setters for counts; broadcast system messages on counts changed; count accessors for percent-based counts and hero range helpers.
I/O: DB-backed data retrieval; broadcast packets.

## SevenSigns.java
Purpose: Main Seven Signs world-state engine: cabal selection, seal status, period progression, DB persistence, player contribution counting, schedule-driven period changes, festival manager/teleport buffering.
Fields/State: cabal/owner maps, seal owners, contribution counts, period schedule, buffs/times, DB flags, player demand lists, revival room mappings.
Public API: init/load/save from DB; period/state updates; player contribution registration; seal owner change/switch handling; schedule-driven period calc; festival access; period startup/close/record settings; cabal reward buffs; event dispatch.
I/O: DB back for period/status/player counts; event dispatcher for seal changes; scheduled tasks; packet/system message broadcasts; DimensionalRift usage.
Gotchas: enormous state container with periodic DB writes; tight caldendar state machine; date math for validation/period start/end; very cross-coupled with siege and castle reward systems.

## SevenSignsFestival.java
Purpose: Festival instance/manager for Seven Signs mini-game periods: level-based spawns, monster spawn control, player teleports, score accumulation, chest opening, witch interactions.
Fields/State: festival-level spawn tables by dawn/dusk/number; monster containers, player instances, music/chest headers, festival scores/level indicators.
Public API: start/stop festivals, spawn monster sets, calculate level/start location, increment player score, open chest notification, end festival; event dispatch; DB save.
I/O: spawns/npc data; packet/system messages; DB-backed scores; event broadcasting; timed task sequences.
Gotchas: hard-coded spawn table triples by level; state transition logic depends on Witch survival; composition by date driven by config constants.

## DimensionalRift.java / DimensionalRiftRoom.java
Purpose: Rift zone state container for seven-signs mini-instances: room state, party teleport completion, room refill timers, party creation failures, room updates.
Fields/State: instance room map, teleport room sets, timed completion tasks; room info holders by instance level/partysize/type; complete/change/fail dispatchers; rooms list DB/world-room mapping.
Public API: start/complete/fail/change stage/lock-room/forgiveness/teleport/set-color; save/load rift state; timed task refills; room selector.
I/O: DB load/save; event dispatch; scheduled tasks; packets; instance/zone manager links.

## Where to change X
- Balance olympiad rewards/hero elections? Olympiad points calc/rank maps; saved DB tables + broadcast.
- Adjust seven signs periods? SevenSigns schedule/cabal state; festival managers/siege rewards integration.
- Modify festival difficulty? SevenSignsFestival spawn tables/type categories; scores by level thresholds.
- Add new event tied to player global state? SevenSigns or Olympiad event listeners + packet broadcast.

---
