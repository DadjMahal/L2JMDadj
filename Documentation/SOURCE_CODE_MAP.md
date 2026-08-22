# Source Code Map

Quick-reference map of `SourceCode/`. Use this to locate where a feature lives,
which module owns it, and where to make a change.

## Layout overview

```
SourceCode/
├── java/org/l2jmobius/   # Java source (2,473 files, 207k lines)
│   ├── commons/          # Shared infrastructure (DB, network, threads, config, UI)
│   ├── loginserver/      # Login server
│   ├── gameserver/       # Game server (the big one)
│   ├── tools/            # Standalone tools (DB installer, account manager, GS register, search)
│   └── log/              # Logging manager + handlers/formatters/filters
├── dist/                 # Datapack template (copied into ServerBuild/)
│   ├── game/             # Game server runtime (config, data, launchers, .cfg)
│   ├── login/            # Login server runtime (config, data, launchers, .cfg)
│   ├── db_installer/     # DB installer tool + sql/
│   ├── libs/             # Third-party jars (HikariCP, mysql-connector, slf4j)
│   ├── images/           # Splash/icons
│   └── backup/           # DB backup target
├── launcher/             # Eclipse .launch files
├── build.xml             # Ant build script
├── readme.txt
└── .classpath / .project / .settings/  # Eclipse project files
```

## Entry points (main classes)

| Jar | Main class | File |
|-----|-----------|------|
| LoginServer.jar | `org.l2jmobius.loginserver.LoginServer` | `loginserver/LoginServer.java` |
| GameServer.jar | `org.l2jmobius.gameserver.GameServer` | `gameserver/GameServer.java` |
| DatabaseInstaller.jar | `org.l2jmobius.tools.DatabaseInstaller` | `tools/DatabaseInstaller.java` |

- `LoginServer.main()` creates the singleton; it loads config, DB, thread pool,
  `LoginController`, `GameServerTable`, IP bans, then opens listeners on 2106/9014.
- `GameServer.main()` creates the singleton; it loads all config + datapack
  (items, skills, NPCs, spawns, quests, scripts, sieges...), then opens 7777
  and connects to the LoginServer on 9014 to register.
- `LoginServerThread` (`gameserver/LoginServerThread.java`) is the client side
  of the Login<->Game link inside the game server process.

## commons/ — shared infrastructure

Used by both servers and tools. Small, stable package.

| Subpackage | Responsibility |
|-----------|----------------|
| `config/` | `DatabaseConfig`, `InterfaceConfig`, `ThreadConfig` — static config holders loaded from `.ini` |
| `crypt/` | `BlowfishEngine`, `NewCrypt` — packet encryption |
| `database/` | `DatabaseFactory` (HikariCP pool), `DatabaseBackup` |
| `network/` | NIO network core: `ConnectionManager`, `Client`, `PacketHandler`, `ReadablePacket`/`WritablePacket`, buffers |
| `threads/` | `ThreadPool` (scheduled + instant executors), `ThreadPriority`, `ThreadProvider` |
| `time/` | `TimeUtil`, `SchedulingPattern` (cron-like) |
| `ui/` | `DarkTheme`, `SplashScreen`, `LineLimitListener` (Swing) |
| `util/` | `ConfigReader` (.ini parser), `IXmlReader`, `StringUtil`, `Rnd`, `HexUtil`, `Subnet`, `DeadlockWatcher` |

**To change DB connection settings:** `commons/config/DatabaseConfig.java` + the `Database.ini` files in each `config/`.
**To change thread pool tuning:** `commons/threads/ThreadPool.java` + `ThreadConfig` + `Threads.ini`.

## loginserver/

| File/Subpackage | Responsibility |
|----------------|----------------|
| `LoginServer.java` | Bootstrap + lifecycle (main) |
| `LoginController.java` | Login attempts, account cache, IP bans, key pairs (RSA/Blowfish) |
| `GameServerTable.java` | Registered game servers (servername.xml + DB `gameservers` table) |
| `GameServerListener.java` | TCP listener on 9014 for game server connections |
| `GameServerThread.java` | Per-game-server connection handler |
| `FloodProtectorListener.java` | Login flood protection on 2106 |
| `SessionKey.java`, `HackingException.java` | Auth session / hack detection |
| `config/LoginConfig.java` | Login config holder |
| `crypt/` | Blowfish (login packet level) |
| `enums/` | `LoginFailReason`, `PlayFailReason`, `AccountKickedReason`, `LoginResult` |
| `model/data/` | Account data access |
| `network/` | `LoginClient`, `LoginPacketHandler`, `LoginEncryption`, `ScrambledKeyPair`, client/server/gameserver packets |
| `ui/Gui.java`, `AboutFrame.java` | Optional Swing GUI |

**To change login ports/auth rules:** `config/Server.ini` + `loginserver/config/LoginConfig.java`.
**To add a login packet:** `loginserver/network/clientpackets/` + register in `LoginClientPackets`.

## gameserver/ — the core

The largest module. Subpackages:

| Subpackage | Responsibility |
|-----------|----------------|
| `GameServer.java`, `LoginServerThread.java`, `Shutdown.java` | Bootstrap, login link, graceful shutdown |
| `ai/` | AI controllers per actor type (`PlayerAI`, `AttackableAI`, `CreatureAI`, `DoorAI`, `SummonAI`, `SiegeGuardAI`...) + `Intention`/`Action`/`NextAction` |
| `cache/` | `HtmCache` (HTML dialog cache) |
| `communitybbs/` | Community board (BBS) handlers + managers |
| `config/` | All game config holders (`GeneralConfig`, `PlayerConfig`, `ServerConfig`, `NpcConfig`, `FeatureConfig`, `RatesConfig`, `OlympiadConfig`, `SiegeConfig`, `PvpConfig`, `GeoEngineConfig`, `FloodProtectorConfig`, `IdManagerConfig`, `DevelopmentConfig`, `GrandBossConfig`, `ConquerableHallSiegeConfig`) + `ConfigLoader` + `custom/` (41 custom feature configs) |
| `data/` | Data tables: `SpawnTable`, `AugmentationData`, `HeroSkillTable`, `MerchantPriceConfigTable`, `SchemeBufferTable` + `sql/` (DB-backed tables) + `xml/` (XML data loaders: items, skills, NPCs, buylists, doors, armor sets, etc.) |
| `geoengine/` | Geodata engine (pathfinding, region loading) |
| `handler/` | Dispatcher registries: `AdminCommandHandler`, `BypassHandler`, `ChatHandler`, `ItemHandler`, `EffectHandler`, `UserCommandHandler`, `VoicedCommandHandler`, `TargetHandler`, `PunishmentHandler`, `CommunityBoardHandler`, `ActionClickHandler`, `ActionShiftHandler` + interfaces (`I*Handler`) |
| `managers/` | ~40 managers: `CastleManager`, `SiegeManager`, `ZoneManager`, `GrandBossManager`, `RaidBossSpawnManager`, `Olympiad`-related, `CursedWeaponsManager`, `InstanceManager`, `IdManager`, `PunishmentManager`, `DailyResetManager`, `ServerRestartManager`, `WalkingManager`, `CustomMailManager`, etc. + `games/` (minigames) |
| `model/` | Domain model (see below) |
| `network/` | NIO client/server: `GameClient`, `GamePacketHandler`, `Encryption`, `BlowFishKeygen`, `SystemMessageId`, `ClientPackets`, `ServerPackets` + `clientpackets/` (215) + `serverpackets/` (279) + `loginserverpackets/` |
| `scripting/` | Script engine (quests/scripts compilation/loading) + `engine/`, `annotations/` |
| `taskmanagers/` | ~18 periodic task managers (movement, decay, respawn, AI think, PvP flag, autosave, game time, item lifetime...) |
| `ui/` | Game server Swing GUI |
| `util/` | XML document parsers (`DocumentBase`, `DocumentSkill`...), utilities |

## gameserver/model/ — domain model

| Subpackage | Responsibility |
|-----------|----------------|
| `actor/` | Actor hierarchy: `Creature` (base) -> `Playable` -> `Player` / `Summon`; `Npc` -> `Attackable`; `Vehicle`, `Tower` + `appearance/`, `stat/`, `status/`, `tasks/`, `templates/`, `holders/`, `enums/` |
| `actor/instance/` | 59 concrete NPC/actor types (`Door`, `Boat`, `Cubic`, `Defender`, `Fisherman`, `ClanHallManager`, `FestivalMonster`, `Trap`, `TamedBeast`...) |
| `item/` | Item model: `ItemTemplate` -> `Armor`/`Weapon`/`EtcItem`; `Henna`; `instance/Item`; `recipe/`, `enchant/`, `type/`, `holders/`, `enums/` |
| `skill/` | `Skill`, `BuffInfo`, `SkillChannelizer`/`SkillChannelized`, `SkillOperateType`, `AbnormalType`, `targets/`, `holders/`, `enums/` |
| `effects/` | `AbstractEffect`, `EffectType`, `EffectFlag`, tick tasks |
| `clan/` | `Clan`, subpledges, clan skills, wars |
| `olympiad/` | Olympiad system |
| `siege/` | Castle/fortress siege logic |
| `sevensigns/` | Seven Signs + festival |
| `residences/` | Castle/clan hall residences |
| `events/` | Event dispatcher + event types |
| `zone/` | Zone types (town, PvP, siege, damage, no-land...) |
| `spawns/` | Spawn group definitions |
| `html/`, `announce/`, `buylist/`, `captcha/`, `clientstrings/`, `conditions/`, `fishing/`, `groups/`, `instancezone/`, `interfaces/`, `itemcontainer/`, `multisell/`, `options/`, `petition/`, `punishment/`, `script/`, `stats/`, `teleporter/`, `variables/` | Feature-specific model |
| `World.java`, `WorldObject.java`, `WorldRegion.java`, `Location.java`, `StatSet.java` | World container + base entity |

**Biggest files** (likely touchpoints for gameplay): `actor/Player.java` (14k lines),
`actor/Creature.java` (7k), `network/SystemMessageId.java` (6.5k),
`model/script/Quest.java` (5.6k), `clan/Clan.java` (3k), `ai/AttackableAI.java` (2.7k).

## tools/ — standalone utilities

| Tool | Purpose |
|------|---------|
| `DatabaseInstaller.java` | Swing GUI to install/dump the login & game DB schemas from `dist/db_installer/sql/` |
| `AccountManager.java` | GUI + console tool to create/delete/list/update login accounts |
| `GameServerRegister.java` | GUI + console tool to register/remove game servers in the login DB |
| `Search.java` | Searches datapack text (HTML/dialogs) |

## log/

`ServerLogManager` (custom `java.util.logging` manager set via `-Djava.util.logging.manager`)
+ `filter/`, `formatter/`, `handler/` for log routing.

## Datapack — dist/

The `dist/` folder is the runtime template copied into `ServerBuild/`. Runtime config
is edited in `ServerBuild/`, but the source template lives here.

### dist/game/data/ — game datapack (114MB)

| Path | Content |
|------|---------|
| `*.xml` (root) | Top-level data: `CategoryData`, `CursedWeapons`, `Doors`, `EnchantItemData`, `Recipes`, `SkillLearn`, `StaticObjects`, `MerchantPriceConfig`, `Seeds`... |
| `stats/` | Item/skill/armor/weapon stats XML |
| `scripts/` | 864 Java scripts: `quests/`, `ai/`, `village_master/`, `vehicles/`, `events/`, `handlers/`, `custom/`, `conquerablehalls/` |
| `html/` | NPC dialog HTML |
| `spawns/` | Spawn lists by region |
| `buylists/`, `multisell/`, `teleporters/` | Shops, multisell, teleport lists |
| `zones/` | Zone definitions |
| `instances/` | Instance templates |
| `geodata/` | Geodata region files |
| `lang/` | Localization |
| `mapregion/`, `xsd/` | Region mapping, XML schemas |

### dist/game/config/ — game server config (71 files)

| Type | Files |
|------|-------|
| `.ini` | `Server.ini`, `Database.ini`, `Network.ini`, `Threads.ini`, `General.ini`, `Player.ini`, `NPC.ini`, `Feature.ini`, `Rates.ini`, `Olympiad.ini`, `Siege.ini`, `PVP.ini`, `GeoEngine.ini`, `FloodProtector.ini`, `IdManager.ini`, `Development.ini`, `GrandBoss.ini`, `ConquerableHallSiege.ini`, `Interface.ini` |
| `.xml` | `AccessLevels.xml`, `AdminCommands.xml`, `Scripts.xml`, `ClassMaster.xml`, `DynamicExpRates.xml`, `SiegeSchedule.xml`, `ipconfig.xml`, `default-ipconfig.xml` |
| `Custom/` | 41 optional feature configs (AutoPlay, Banking, Captcha, ChampionMonsters, CommunityBoard, CustomMailManager, FactionSystem, FakePlayers, OfflineTrade, PremiumSystem, SchemeBuffer, SellBuffs, Transmog, Wedding...) |

### dist/login/config/ — login server config

`Database.ini`, `Server.ini`, `Network.ini`, `Threads.ini`, `Interface.ini`.

### dist/db_installer/

`DatabaseInstaller.sh`/`.vbs`, `config/` (Interface.ini), `sql/login/` + `sql/game/` (100 SQL files).

### dist/libs/ — third-party dependencies

| Jar | Purpose |
|-----|---------|
| `HikariCP-7.0.2.jar` | Connection pooling |
| `mysql-connector-j-9.5.0.jar` | JDBC driver |
| `slf4j-api-2.0.17.jar` + `slf4j-simple-2.0.17.jar` | Logging facade |

## Quick lookup — "where do I change X?"

| I want to... | Go to |
|--------------|-------|
| Change a gameplay rate (exp/sp/drop) | `dist/game/config/Rates.ini` + `gameserver/config/RatesConfig.java` |
| Change max online players / server port | `dist/game/config/Server.ini` + `gameserver/config/ServerConfig.java` |
| Change login ports/auth | `dist/login/config/Server.ini` + `loginserver/config/LoginConfig.java` |
| Change DB connection | `dist/*/config/Database.ini` + `commons/config/DatabaseConfig.java` |
| Add/edit an admin command | `gameserver/handler/AdminCommandHandler.java` (registry) + handler script in `dist/game/data/scripts/handlers/chat/commands/admin/` (commands declared in `dist/game/config/AdminCommands.xml`) |
| Add a client packet handler | `gameserver/network/clientpackets/` + register in `gameserver/network/ClientPackets.java` |
| Add a server packet | `gameserver/network/serverpackets/` + register in `gameserver/network/ServerPackets.java` |
| Modify player behavior | `gameserver/model/actor/Player.java` |
| Modify NPC AI | `gameserver/ai/` + `gameserver/model/actor/instance/` |
| Add a quest | `dist/game/data/scripts/quests/` (Java script, compiled at runtime) |
| Add a custom feature | `gameserver/config/custom/` + `dist/game/config/Custom/` |
| Change an NPC dialog | `dist/game/data/html/` |
| Change spawns | `dist/game/data/spawns/` |
| Change item/skill stats | `dist/game/data/stats/` |
| Register a game server | run `tools/GameServerRegister` (or edit `gameservers` table) |
| Install/refresh DB schema | run `tools/DatabaseInstaller` |

## Build wiring (recap)

`build.xml` compiles `java/` -> `build/bin`, then produces three jars into
`ServerBuild/`: `LoginServer.jar`, `GameServer.jar` (in `libs/`),
`DatabaseInstaller.jar` (in `db_installer/`). The jars are prebuilt and read-only for
this project (engine policy: never edit/rebuild server source).