# Server Startup

## How LoginServer Starts

1. Working directory: `~/L2JM/ServerBuild/login/`
2. Launch: `./LoginServer.sh` (runs `./LoginServerTask.sh &`)
3. `LoginServerTask.sh` reads `java.cfg` and runs:
   `java $(cat java.cfg) -jar ../libs/LoginServer.jar`
4. The server loads `config/*.ini`, initializes the HikariCP database pool,
   loads server names, registered game servers and RSA/Blowfish keys, then opens:
   - **Port 2106** - client login listener (bind `0.0.0.0`)
   - **Port 9014** - GameServer listener (bind `127.0.0.1`)
5. `LoginServerTask.sh` loops: if the process exits non-zero it restarts after 10s.

### LoginServerTask.sh (key line)
```bash
java $(cat "java.cfg") -jar ../libs/LoginServer.jar > log/stdout.log 2>&1
```

## How GameServer Starts

1. Working directory: `~/L2JM/ServerBuild/game/`
2. Launch: `./GameServer.sh` (runs `./GameServerTask.sh &`)
3. `GameServerTask.sh` reads `java.cfg` and runs:
   `java $(cat java.cfg) -jar ../libs/GameServer.jar`
4. The server loads `config/*.ini`, initializes the database, loads all datapack
   (items, skills, NPCs, spawns, quests, scripts, sieges, etc.), then:
   - Connects to the LoginServer on `127.0.0.1:9014` (configured in `Server.ini`).
   - Registers itself (e.g. "Server 2: Sieghardt").
   - Opens **Port 7777** - game client listener (bind `0.0.0.0`).
5. `GameServerTask.sh` loops: on exit code `2` it reboots, otherwise it stops.

### GameServerTask.sh (key line)
```bash
java $(cat "java.cfg") -jar ../libs/GameServer.jar > log/stdout.log 2>&1
```

## Typical startup sequence

1. Start MariaDB (system service).
2. Start LoginServer:
   ```bash
   cd ~/L2JM/ServerBuild/login && ./LoginServer.sh
   ```
3. Wait for the log line:
   `LoginServer: Login client listener started on 0.0.0.0:2106`
4. Start GameServer:
   ```bash
   cd ~/L2JM/ServerBuild/game && ./GameServer.sh
   ```
5. Wait for the log lines:
   - `GameServer: Server loaded in N seconds.`
   - `LoginServerThread: Registered on login as Server N: <name>`

## Ports

| Port | Service | Bound to |
|------|---------|----------|
| 2106 | LoginServer client listener | 0.0.0.0 |
| 9014 | LoginServer GameServer listener | 127.0.0.1 |
| 7777 | GameServer client listener | 0.0.0.0 |

## Stopping the servers

The task scripts loop and auto-restart. To stop them cleanly, terminate the Java
process and the task script, e.g.:
```bash
pkill -f "GameServerTask.sh"; pkill -f "LoginServerTask.sh"
pkill -f "GameServer.jar"; pkill -f "LoginServer.jar"
```

## Validation checklist

- LoginServer: port 2106 and 9014 listening, no exceptions in `log/error*.log`.
- GameServer: port 7777 listening, no exceptions in `log/error*.log`.
- GameServer log contains `LoginServerThread: Registered on login as Server N`.
- JAR files exist in `ServerBuild/libs/` (`LoginServer.jar`, `GameServer.jar`).
