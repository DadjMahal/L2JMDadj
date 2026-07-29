# 16 — managers part1 + data loaders

Resume checkpoint
- Read files:
  - managers listing 42 files, CastleManager top, ClanTable top
- Still to read:
  - remaining managers/data sql/xml census, handlers/taskmanagers in next doc.
- Next:
  - write structured map.

---

## Managers catalog
Purpose: Shared singleton runtime systems owned by gameserver.
Representative entries:
- CastleManager/SiegeManager/CHSiegeManager: castle/siege logic + timers + rewards
- ZoneManager/ZoneBuildManager: region/zone/cache queries + dynamic territory manufacture
- InstanceManager: dungeon/instance creation/cooldown/recycle destroy timers
- GrandBossManager/RaidBossSpawnManager/RaidBossPointsManager: boss scheduler + points/crystal absorption rewards
- ScriptManager: quest/handler timing; global script persistence store
- ItemManager/PetitionManager/DuelManager/TownManager/CoupleManager/AntiFeedManager/PremiumManager/CaptchaManager/EventDropManager/PcCafePointsManager/FishingChampionshipManager/CustomMailManager/WalkingManager/SellBuffsManager/PunishmentManager/GlobalVariablesManager/PrecautionaryRestartManager/ServerRestartManager/DatabaseIdManager

Control flow: mostly singleton holder pattern; constructors load config/DB/XML and register listeners; many scheduleAtFixedRate housekeeping; expose typed lookup APIs.
I/O: DB-backed tables for persistence (clans, castles, zones, instances, bosses, items, quests, variables); XML data from datapack.
Gotchas: 42 singleton managers with overlapping lifecycle requirements; order-sensitive init hidden inside GameServer bootstrap.

## Data loaders
Purpose: One-shot cache for read-mostly world data and DB-backed state tables.
Structure: `sql/` wraps JDBC tables; `xml/` wraps datapack XML loaders; top-level holders for enums/sets.
Key classes:
- ClanTable: loads all clans, init forums root, dissolve timers, wars, scheduled rank updates.
- ZoneManager/ZoneBuildManager: zones by id, dynamic forms, instance zones, allowance checks.
- NpcData/ArmorSetData/AdminData/CategoryData/DoorData/FenceData/MapRegionData/PlayerTemplateData/SkillData/SkillTreeData/RecipeData/MapRegion/ExperienceData... load XML templates.
- OfflinePlayTable/OfflineTraderTable: restore offline traders/players on startup or persistence save.

I/O: direct JDBC for tables; XML via DocumentBuilder from `ServerConfig.DATAPACK_ROOT`.
Gotchas: loader init order is critical, e.g., forums before clans; no single dependency graph enforced.

## Where to change X
- Siege/clan reward timing? Siege/CHSiegeManager.
- Instance lifecycle? InstanceManager.create/destroy/timer APIs.
- NPC/item/zone data models? corresponding data loader classes.
- Scheduled cleanup tasks? specific manager schedule methods; tune in configs.

---
