# Build Process

The project uses **Apache Ant** (not Gradle). The build script is
`SourceCode/build.xml`.

## Requirements

- JDK 25 (Temurin/OpenJDK 25)
- Apache Ant 1.10+
- MariaDB/MySQL for runtime (the build itself does not need a database)

## How to build

```bash
cd ~/L2JM/SourceCode
ant
```

The default target (`cleanup`) runs the full chain:

1. `checkRequirements` - verifies Ant >= 1.8.2 and Java 25.
2. `init` - creates `~/L2JM/build/bin` (compiled `.class` output).
3. `compile` - compiles all Java sources from `java/` into `build/bin`.
4. `jar` - produces three jars directly into `ServerBuild/`:
   - `ServerBuild/libs/LoginServer.jar` (main: `org.l2jmobius.loginserver.LoginServer`)
   - `ServerBuild/libs/GameServer.jar` (main: `org.l2jmobius.gameserver.GameServer`)
   - `ServerBuild/db_installer/DatabaseInstaller.jar` (main: `org.l2jmobius.tools.DatabaseInstaller`)
5. `adding-core` - packs `ServerBuild/` into `~/L2JM/build/L2J_Mobius_CT_0_Interlude.zip`.
6. `cleanup` - deletes the `build/bin` scratch directory.

## How jars are produced

The `jar` target splits the compiled classes from `build/bin`:

- **LoginServer.jar** - everything except `gameserver/**`,
  `tools/DatabaseInstaller*`, `tools/Search*`.
- **GameServer.jar** - everything except `loginserver/**`,
  `tools/AccountManager*`, `tools/DatabaseInstaller*`, `tools/GameServerRegister*`.
- **DatabaseInstaller.jar** - only the config/database/ui/util classes and
  `tools/DatabaseInstaller*`.

Each jar manifest declares `Class-Path` referencing `../libs/*.jar` (the third-party
dependencies) and the appropriate `Main-Class`.

## Build output locations

- Compiled classes (scratch): `~/L2JM/build/bin/` (deleted after build)
- Distribution zip: `~/L2JM/build/L2J_Mobius_CT_0_Interlude.zip`
- Runnable jars: `~/L2JM/ServerBuild/libs/` and `~/L2JM/ServerBuild/db_installer/`

## Key build.xml properties

| Property | Location |
|----------|----------|
| `build` | `../../build` (relative to the project) |
| `build.bin` | `${build}/bin` |
| `server.build` | `../../ServerBuild` |
| `server.build.libs` | `${server.build}/libs` |
| `server.build.databaseinstaller` | `${server.build}/db_installer` |
| `datapack` | `dist` (the datapack template in SourceCode) |
| `libs` | `${datapack}/libs` (third-party dependency jars) |
| `src` | `java` (Java source root) |
