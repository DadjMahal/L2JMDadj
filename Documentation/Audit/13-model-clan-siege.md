# 13 — model/clan & siege + residences

Resume checkpoint
- Read files:
  - gameserver/model/clan/{Clan,ClanMember,ClanPrivileges,ClanAccess,Crest}.java
  - gameserver/model/siege/{Siege,Castle,SiegeClan,SiegeScheduleDate,TowerSpawn,Residence}.java
  - gameserver/model/siege/clanhalls/{SiegableHall,ClanHallSiegeEngine,SiegeStatus}.java
  - gameserver/model/siege/manor/{CropProcure,Seed,SeedProduction,ManorMode}.java
- Still to read:
  - Siege teleport/listener edge paths, Manor schedule hooks, DB loader classes for siege tables, ClanHall residence zone coupling details.
- Next: continue 14+.

---

## Clan.java
Purpose: Runtime representation of a player clan; social/cooperative container for members, reputation, warehouse, alliances, and castle ownership.
Fields/State: id/name/leaderId, level, crests, ally refs, concurrent member map, skills/privs/subpledges maps, notice, online member lists, siege kill/death atomic counters, warehouse ItemContainer, war sets.
Public API Surface: member add/remove/get, leader change, level/exp, skill/rank/privilege management, reputation/penalty countdown, warehouse access, notice/title management, online notify, quitting flow, DB load/save, ally operations, siege broadcast helpers.
Control Flow: Created/loaded by ClanTable; member lifecycle via ClanMember; leader change fires events and updates siege skills/privs; online status changes maintain concurrent map; siege/effect skills applied on login via SiegeManager; packets broadcast for clan/ally info.
I/O: JDBC load/save of clan_data; warehouse persistence items/crests; packet broadcasts for clan/ally info; event listeners dispatch clan events.
Gotchas/Refactor Candidates: Large monolith mixing persistence, online tracking, skills, warehouse, and diplomacy. Lifecycle changes are not localized. Tight coupling with managers and packet layer.

## ClanMember.java
Purpose: Persistent proxy for a player within a clan across online/offline boundaries.
Fields/State: back-refs Clan/Player, cached name/level/classId/objectId/pledgeType/powerGrade/title/apprentice/sponsor/sex/race.
Public API Surface: synchronized setPlayer with offline cache preservation; online check; cached getters with fallback to live player; pledge/sponsor updates; clan skill/effect apply on login.
Control Flow: on login setPlayer links live instance, reapplies clan/siege effects/skills; on logout preserves last visible cached values; sponsor/apprentice cleanup on remove.
Gotchas/Refactor Candidates: Mutable cache fields require synchronization on offline transitions; caching semantics can diverge from player state if events change class/level without logout.

## Siege.java
Purpose: State machine for one castle/clan-hall siege event with attacker/defender clans, control towers, registration stages, scheduled start/end/countdown.
Fields/State: attacker/defender concurrent collections, control/flame tower lists, Castle ref, progress/registration flags, siege end calendar, ScheduledFuture start task, siege guard manager, victory/first-owner tracking.
Public API Surface: clan registration/removal, attacker/defender queries, teleport/attack access, siege start/end middle steps, attacker broadcast, control tower updates, guard manager lifecycle, DB persistence, scheduled tasks manage end/registration.
Control Flow: SiegeManager invokes scheduled phases; death/respawn modify mid-siege behavior; siege end changes owner; control tower death changes attacker count; broadcasts via SystemMessage packets.
I/O: DB-backed clan lists/state; ThreadPool scheduled tasks; event dispatchers/packets; zone/teleport access via managers.
Gotchas/Refactor Candidates: Many mutable concurrent collections; complex scheduled countdown nesting in ScheduleEndSiegeTask; side effects tied to tower deaths may desync attacker counts if exceptions occur.

## Castle.java
Purpose: Castle residence with owned/defendable territory, tax, siege scheduling, castle functions, teleport zones, crests, artefacts.
Fields/State: doors, ownerId, siege ref/date, registration end date, tax percent/rate, treasury, NPC crest flag, zone refs, former owner, artefacts set, functions map, ticket count, victory flags, castle circle items.
Public API Surface: ownership change, treasury add/tax split across capitals, banish foreigners, siege zone query, engrave artefact, door/functions/upgrades load/save, spawn flame towers, siege teleport/middle steps expand.
Control Flow: Manor/tax system uses Castle as tax root for Aden/Rune/Routes; siege starts through Siege.schedule; treasury DB updates; functions map cached on load; castle-specific broadcast to players.
I/O: castle DB rows; treasury updates via PreparedStatement; tax/ npc/crest functions.
Gotchas/Refactor Candidates: Castle tax paths hardcode names like schuttgart/goddard/aden/rune in multiple branches; treasury DB write can race on concurrent income/expense.

## SiegeClan.java
Purpose: Minimal flag-bearing clan wrapper for attackers/defenders during a siege.
Fields/State: clanId, SiegeClanType, Set<Npc> flags.
Public API Surface: add/remove/clear flags, type get/set.
Control Flow: removed flags deleteMe on instance; used by Siege attacker/defender maps.

## SiegeScheduleDate.java
Purpose: Data holder for scheduled siege day/hour/limit/enable flags loaded from dataset.
Fields/State: day, hour, maxConcurrent, siegeEnabled booleans.
Public API: constructor from StatSet; getters only.

## TowerSpawn.java
Purpose: Control/flame tower runtime descriptor within a castle siege area.
Fields/State: npcId, location, castle id, zone id.
Public API: constructor with castle; getters only.

## ClanHall / SiegableHall
Purpose: ClanHall with ownership DB, functions, doors, zone; SiegableHall adds next siege date/status, clan-hall siege engine hook, schedule config parse, spawn doors on revive, db persistence by owner/nextSiege.
Public API SiegableHall: set/get siege, siege date/length/schedule update, owner-driven function load, attacker registration, siege status, DB update.
Control Flow: SiegableHall ties SiegeStatus enum registration/progress/end; ClanHallSiegeEngine middle steps schedule/attackers/end.
Gotchas: SiegableHall becomes partially coupled to outside Castle state via inheritance differences.

## Manor system
Purpose: Manages crop taxes/production and seed production per castle period.
Fields/State: seed/shop costs, crop type costs, yields by seed cost class, taxedADena rates, clan taxes.
Public API: calculate procure/shop taxes, calculate/return production yields per owner type.
Control Flow: Seed/CropProcure hold shop names/costs; manor uses per-period; configurable by manor save mode.

## Where to change X
- Clan leave/join/leader change? Clan.removeClanMember / ClanMember setPlayer + SiegeManager skill update.
- Siege schedule/registration? Siege schedule methods + ClanTable load + SiegeManager.
- Castle treasury/tax? Castle.addToTreasuryNoTax/tax split methods; persist castle table.
- Clan hall siege? SiegableHall + ClanHallSiegeEngine schedule/status.
- Manor/crop yields? manor/siege classes and Seed/CropProcure holders.
- Residence functions? ClanHall/Castle function maps and door upgrade DB.

---
