# Runtime Layout

The server runs entirely from `ServerBuild/`. There is no dependency on `SourceCode/`
at runtime (apart from the source-provided datapack that was copied in).

```
ServerBuild/login/   -> LoginServer working directory
ServerBuild/game/    -> GameServer working directory
ServerBuild/libs/    -> shared jars (both servers use ../libs/)
```

## ServerBuild/login/

```
login/
├── LoginServer.sh / LoginServerTask.sh   # Linux launchers
├── LoginServer.vbs                       # Windows launcher
├── AccountManager.sh / .vbs              # Account management tool
├── GameServerRegister.sh / .vbs          # GameServer registration tool
├── java.cfg                              # JVM options for LoginServer
├── console.cfg                           # Console logging config
├── log.cfg                               # File logging config
├── banned_ip.cfg                         # Banned IP list
├── config/                               # LoginServer configuration (.ini)
├── data/                                 # LoginServer data (servername.xml)
└── log/                                  # Runtime logs
```

## ServerBuild/game/

```
game/
├── GameServer.sh / GameServerTask.sh     # Linux launchers
├── GameServer.vbs                        # Windows launcher
├── Search.sh / .vbs                      # Search tool
├── java.cfg                              # JVM options for GameServer
├── console.cfg                           # Console logging config
├── log.cfg                               # File logging config
├── config/                               # GameServer configuration (.ini/.xml)
├── data/                                 # Game datapack (items, skills, NPCs, scripts...)
└── log/                                  # Runtime logs
```

## Where configuration is stored

Configuration is stored as `.ini` / `.xml` files inside each server's `config/` folder.

### LoginServer config (`ServerBuild/login/config/`)
- `Database.ini` - database connection (driver, URL, user, password, pool size, backup)
- `Server.ini` - login networking (host, ports 2106/9014), security, account settings
- `Network.ini` - network/buffer pool tuning
- `Threads.ini` - thread pool settings
- `Interface.ini` - GUI/console interface settings

### GameServer config (`ServerBuild/game/config/`)
- `Database.ini` - database connection (driver, URL, user, password, pool size, backup)
- `Server.ini` - gameserver networking (LoginHost, LoginPort 9014, GameserverPort 7777),
  server ID, protocol, datapack/script roots
- `Network.ini` - network/buffer pool tuning
- `General.ini`, `Player.ini`, `Rates.ini`, `NPC.ini`, `Feature.ini`, `Olympiad.ini`,
  `Siege.ini`, `PVP.ini`, `GeoEngine.ini`, `FloodProtector.ini`, `IdManager.ini`,
  `Development.ini` - gameplay/server settings
- `AccessLevels.xml`, `AdminCommands.xml`, `Scripts.xml`, `ipconfig.xml`,
  `SiegeSchedule.xml`, `ClassMaster.xml`, `DynamicExpRates.xml`, etc.
- `Custom/` subfolder holds optional custom feature configs

### JVM options
JVM options live in `java.cfg` next to each launcher:
- `ServerBuild/login/java.cfg` - `-server -Dfile.encoding=UTF-8 ... -Xms128m -Xmx256m`
- `ServerBuild/game/java.cfg`  - `-server -Dfile.encoding=UTF-8 -Djava.util.logging.manager=org.l2jmobius.log.ServerLogManager ... -Xmx4g -Xms2g`

### Logging
Logging is configured via `log.cfg` and `console.cfg` in each server folder; logs are
written to the `log/` subfolder of each server.

## Database

Both servers connect to a local MariaDB/MySQL instance (see `Database.ini` in each
`config/` folder). Default connection details in the current setup:
- LoginServer database: `loginserver`
- GameServer database: `gameserver`
- User: `l2j`

The database schema is installed via the Database Installer:
```bash
cd ~/L2JM/ServerBuild/db_installer
./DatabaseInstaller.sh
```
SQL scripts are in `db_installer/sql/login/` and `db_installer/sql/game/`.
