# 18 — tools, log, and script packages

Resume checkpoint
- Read files:
  - trees for `tools/`, `log/`, `dist/game/data/scripts/`
- Still to read:
  - representative tools/log classes only if needed; script package count is 864 so handled by partition planning.
- Next:
  - write structural map and move to 19+.

---

## tools/
Purpose: auxiliary utilities/build bridges and standalone tools outside live server core.
Structure: multiple standalone tools under `org.l2jmobius.tools`; some for data conversion/admin utilities.
Public API surface: main-class style tools with CLI-like entry points; helper methods for EP/DDS/html/geo/script conversion/debugging.
Coupling: some tools import gameserver data loaders; most are offline batch utilities.
Gotchas: version mismatches with server runtime can corrupt data; run offline.

## log/
Purpose: secondary logging framework/adapters under `org.l2jmobius.log`.
Structure: appender/filter helpers; custom log record/formatter for gmaudit/accounting/chat.
Public API: static logger setup/write/append; integrations to `java.util.logging`; per-channel accounting logger usage.
I/O: writes to rolling/text/appender files depending on code config; no DB.
Gotchas: synchronous/concurrent file logging influences startup latency; change formats carefully.

## dist/game/data/scripts/
Purpose: gameplay custom logic package containing deployable scripts for quests, handlers, AI, village masters, vehicles, events.
Structure: top-level partitions into `quests/`, `ai/`, `village_master/`, `vehicles/`, `events/`, `handlers/`, `custom/`, `conquerablehalls/`; script framework entry in `gameserver/scripting` corresponding to `dist` injectors.
Public API surface: quest script classes with event-driven lifecycle hooks; handler classes registration via handlers package; AI script controllers for NPC behaviors.
Gotchas: script ↔ server core coupling via event dispatcher and packet class availability; classloader issues on hot reload.

## Where to change X
- Tooling/data conversion? edit standalone tools + static imports; rerun offline.
- Logging format/rotation? log adapter/appender classes; восстановление.
- Script behavior without recompiling core? edit `dist/game/data/scripts/**` classes; use serverside script reload if available.
- Bridge between core and scripts? scripts depend on event framework + handlers; change one side carefully to preserve package contract.

---
