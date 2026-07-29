# Repository Structure

## Top level

```
~/L2JM/
├── SourceCode/          # The only place where source code is edited
├── ServerBuild/         # The runnable server (runtime) after build
├── L2JMobiusServers/    # Archived branches, old builds and backups
├── L2JMobiusUpstream/   # Clean upstream Mobius clone (reference only)
├── Documentation/       # Project documentation
├── build/               # Ant build scratch (compiled classes + distribution zip)
├── .git/                # Local Git repository (remote: L2JMDadj)
├── StartServer.sh       # Startup script
├── StopServer.sh        # Shutdown script
└── README.md            # Original Mobius project README
```

## SourceCode/

Contains the single active project directly:

```
SourceCode/
├── build.xml            # Ant build script
├── java/                # Java source (org.l2jmobius.*)
├── dist/                # Datapack template (config, data, sql, libs w/o compiled jars)
├── launcher/            # Eclipse .launch files
├── readme.txt
├── .classpath / .project / .settings/  # Eclipse project files
└── .gitignore
```

- `java/` holds all server source: `commons/`, `loginserver/`, `gameserver/`,
  `tools/`, `log/`.
- `dist/` is the datapack template that was copied into `ServerBuild/`. It contains
  `config/`, `data/`, `db_installer/`, `html`, `sql`, runtime `.cfg` files and the
  dependency `libs/` (third-party jars). The compiled `LoginServer.jar`,
  `GameServer.jar` and `DatabaseInstaller.jar` are NOT kept here; they are produced
  by the build directly into `ServerBuild/`.

## ServerBuild/

The runnable server. This is a copy of `SourceCode/.../dist/` populated with the
compiled jars. Structure:

```
ServerBuild/
├── libs/                # LoginServer.jar, GameServer.jar + third-party jars
├── login/               # LoginServer runtime (start here)
├── game/                # GameServer runtime (start here)
├── db_installer/        # Database installer tool + DatabaseInstaller.jar + sql/
├── backup/              # Database backup target
└── images/              # Splash/icons
```

`login/` and `game/` each contain their own `config/`, `data/`, `log/`, `.cfg`
files and shell/VBS launchers. Both reference the shared `../libs/` directory.

## L2JMobiusUpstream/

Contains the full Mobius upstream repository (all chronicles) as a clean reference
copy. This is a shallow clone (`--depth 1`) of the original GitLab repository.
Updated with `git pull` when needed. Not tracked in the L2JMDadj git repo.

```
L2JMobiusUpstream/
├── L2J_Mobius_CT_0_Interlude/   # Our Interlude chronicle (upstream version)
├── L2J_Mobius_CT_2.6_HighFive/
├── L2J_Mobius_01.0_Ertheia/
├── L2J_Mobius_Classic_2.0_Saviors/
├── ... (all chronicles)
└── README.md
```

Archive of everything not part of active Interlude development:
- All non-Interlude Mobius branches (Ertheia, HighFive, Classic, Essence, etc.)
- `Account_Manager/` (PHP account management tool)
- `build_old/` (previous mixed build artifacts)

These are kept for reference only and are not built or run.
