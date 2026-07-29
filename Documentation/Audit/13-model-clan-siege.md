# 13 — model/clan & siege + residences

Resume checkpoint
- Read files:
  - gameserver/model/clan/{Clan,ClanMember,ClanAccess,ClanPrivileges,Crest,ClanInfo}.java
  - gameserver/model/siege/{Siege,Castle,SiegeClan,Siegable,SiegeScheduleDate}.java
  - gameserver/model/residences/{AbstractResidence,AuctionableHall,ClanHall,ClanHallAuction}.java
- Still to read:
  - manor subtype files if needed later.
- Key findings so far:
  - Clan is central social group container with skills/warehouse/coalition state; Siege/Castle embed owner/defender/attacker flow with DB persistence and scheduled events.
- Next step:
  - Write structured summary for 13, then continue 14+.

---

## Clan.java
Purpose: Runtime representation of a player clan; core social/cooperative container for members, reputation, warehouse, and castle ownership.
Fields/State: id/name/leaderId, level, crests, reputation/diplomacy/alliance references, member map, skills map, notice/subtitle, warehouse instance, online member lists, various flags and timestamps, DB-backed state.
Public API Surface: member add/remove/get, leader change, level/exp, skill management, reputation/penalty countdown, warehouse access, notices and titles, online notify/quitting flow.
Control Flow: Created/loaded by ClanTable; member lifecycle via ClanMember; leader change triggers events; online status changes maintain concurrent online-member map; siege skills granted via SiegeManager.
I/O: JDBC load/save of clan state; warehouse persists items; crests stored in DB; packet broadcasts for alliance/clan info updates.
Gotchas/Refactor Candidates: large monolith with DB persistence + online tracking + skills + warehouse; lifecycle changes not fully localized.

## ClanMember.java
Purpose: Persistent proxy for a player within a clan when offline/online transitions occur.
Fields/State: back-refs Clan/Player, cached fields name/level/classId/objectId/pledgeType/powerGrade/title/apprentice/sponsor/sex/raceOrdinal.
Public API Surface: synchronized setPlayer with data preservation on offline; online check; class/level/name/profession getters with cached fallback; pledge/sponsor updates; clan skill effect apply on login.
Control Flow: when player logs in, setPlayer links live instance and reapplies effects/skills as needed; offline retains final visible values for name/class/level.
Gotchas: mutable cache fields must be synchronized for offline transitions.

## Castle.java
Purpose: Castle residence type with owned/defendable territory tax, siege scheduling, castle functions, teleport zones, crest/artefacts.
Fields/State: ownerId, doors list, siege instance, dates, tax percent/rate, treasury, npc crest showing, zone references, former owner, artefact set, functions map, ticket count, victory flags.
Public API Surface: owner change, siege period/registration/time validation, function schedule/fee management, DB save, artefact registration, tax/treasury update, castle func initialization.
Control Flow: DB-loaded by CastleManager; CastleFunction task loop runs fee collection/removal; siege lifecycle coordinated by Siege and managers.
I/O: JDBC load/save; scheduled tasks via ThreadPool for fees/functions.
Gotchas: timezone/registration end logic is complex and tightly coupled to scheduler; function map keyed by int type with no validation outside constants.

## Siege.java
Purpose: State machine for one castle/clan hall siege event with attacker/defender clans, control tower count, registration stages, scheduled start/end events.
Fields/State: attacker/defender clans concurrent collections, control tower count, siege zone reference, flags/teleport lists, siege date/time, respawn modifiers.
Public API Surface: clan registration/removal, attacker/defender queries, zone teleport/attackers access, siege start/end middle steps, attacker broadcast, control tower updates.
Control Flow: SiegeManager invokes scheduled siege phases; Death/Respawn respectively modify flag to middle steps; siege end changes owner.
I/O: DB-backed clan lists; threads scheduling siege phases; event dispatchers; packet broadcasts for siege status; teleport/zone access via zone manager.
Gotchas: many stateful mutable collections—require concurrency discipline; teleport where check split by SiegeTeleportWhoType.

## AbstractResidence.java
Purpose: Base abstract persistence for controllable residence territory data.
Fields/State: id, name, owner, zone, tax/rate, functions, DB-backed ownerId, transient persistent state.
Public API Surface: owner getter/setter, zone registration, function load, DB save/remove, owner change broadcast.
Control Flow: Subclasses Castle/ClanHall implement specialized subbehavior.
I/O: JDBC on state change.

## AuctionableHall.java / ClanHall.java / ClanHallAuction.java
Purpose: Clan hall as auctionable residence with ownership auction/rental; ClanHallAuction handles bidding/auction state.
Fields/State: owner/bidders, auction timers, minimum bid, item/adenabid, location, teleport zones.
Public API Surface: auction start/end, bid registration, owner change, DB save, ba袭击 broadcast.
I/O: DB for halls + auction state; scheduled auction end tasks.

## Where to change X
- Clan social system? Clan class; skills/effects apply on login via ClanMember/Clan effects path.
- Castle/siege logic? Siege.java state machine; Castle owner/tax; SiegeManager orchestrates calendar and stages.
- Hall auctions? ClanHallAuction bidders/timers; ClanHall ownership persistence.
- Add new residence types? extend AbstractResidence + CastleManager/SiegeManager and DB loaders.

---
