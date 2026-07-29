# L2JM - L2J Mobius Interlude Server / Forked by Dadj Mahal

This workspace contains the active **Lineage 2 Interlude** server based on the L2J Mobius
project. Only the Interlude chronicle is kept under active development. All other chronicles,
backups and old builds are archived under `L2JMobiusServers/`.

See the topic-specific documents in this folder:

- [REPOSITORY_STRUCTURE.md](REPOSITORY_STRUCTURE.md) - directory layout and contents
- [BUILD_PROCESS.md](BUILD_PROCESS.md) - how to build and how jars are produced
- [RUNTIME_LAYOUT.md](RUNTIME_LAYOUT.md) - runtime layout and where configuration is stored
- [SERVER_STARTUP.md](SERVER_STARTUP.md) - how LoginServer and GameServer start
- [SOURCE_CODE_MAP.md](SOURCE_CODE_MAP.md) - source code map: modules, packages, and where to change things
- [Audit/](Audit/) - deep per-subsystem audit in progress; start at [Audit/PROGRESS.md](Audit/PROGRESS.md)
- [GIT.md](GIT.md) - Git state after cleanup

## Quick reference

```
~/L2JM/
├── SourceCode/          # The only place where source code is edited
├── ServerBuild/         # The runnable server (runtime) after build
├── L2JMobiusServers/    # Archived branches, old builds and backups
├── Documentation/       # This documentation
├── build/               # Ant build scratch (compiled classes + distribution zip)
├── .git/                # Local Git repository (no remotes configured)
└── README.md            # Original Mobius project README
```

Build:  `cd ~/L2JM/SourceCode && ant`
Start Login: `cd ~/L2JM/ServerBuild/login && ./LoginServer.sh`
Start Game:  `cd ~/L2JM/ServerBuild/game && ./GameServer.sh`
