# EP-7 RuntimeLog — Virtual threads for the fleet

**Date**: 2026-08-20
**Commit**: 933204d2
**Task**: EP-7 (AUDIT_01) — BotSession loops + dashboard executors on Thread.ofVirtual

## What changed
- `examples/FleetPlay.java`: spawn loop extracted to `static Thread[] spawnFleet(...)`; every
  BotSession now starts on `Thread.ofVirtual().name("bot-"+account)` (was platform thread).
  main() behavior unchanged (parse → boot → spawnFleet → park).
- `net/GameServerClient.startReader()`: per-bot blocking readLoop on
  `Thread.ofVirtual().name("gs-reader-"+name)` (was daemon platform thread; virtuals are always
  daemon, so setDaemon dropped).
- `web/DashboardBoot`: HttpServer executor = `newThreadPerTaskExecutor(Thread.ofVirtual()
  .name("dash-",0).factory())` — one virtual thread per dashboard request (was the default
  dispatcher-thread executor).
- `core/AIPlayerManager`: 2 spawn sites → `Thread.ofVirtual().name("ai-player-spawn")`
  (scheduler pool untouched — 10 threads, fine).
- NEW test `examples/VirtualThreadFleetTest`: spawns a 2-bot fleet against a closed port,
  asserts every session thread isVirtual + alive + named bot-*, then interrupts and asserts
  clean exit (locks both the EP-7 carrier AND the interrupt shutdown path).

## Pinning / shared-state audit (prompt requirement)
- BotSession: **0 synchronized blocks**; shared state = ConcurrentHashMap bots, volatile BotInfo
  fields, EventRing/HistoryRing/FleetMetrics (lock-based rings — short critical sections) —
  no monitor held across blocking IO.
- GameServerClient: 2 synchronized(writeLock) blocks around short frame write+flush — a full
  socket send buffer could pin a carrier while blocked in flush inside the monitor; frames are
  small so this is theoretical. JDK 25 also no longer pins on most synchronized+blocking-IO
  cases the way JDK 21 did.
- Sockets stay blocking — that is the virtual-thread design point; no NIO rewrite needed.

## Verification
- `mvn -o -f AIPlayerEngine/pom.xml test` → **415 tests, 0 failures, 0 errors** (414 + 1 new)
- VirtualThreadFleetTest is the repeatable in-CI proof of virtual carriers + interruptibility
- 20-bot 5-min live run + jcmd thread dump + RSS: SKIPPED — game/login servers not running
  this session. Run when servers are next up: `scripts/fleet_launch.sh 20` then
  `jcmd <pid> Thread.dump_to_file - <file>` and count `virtual` entries; compare xp/min via
  watch_fleet.py against the 20-bot baseline.

## Notes
- Deprecated example probes (PvP/Party/Quest/Chat/Trade readers, MultiPlayerSession) keep
  platform threads — single-run diagnostic tools, not worth the churn; noted for a later sweep.
- BotSession's reconnect loop catches connection failures (closed port) and backs off — that's
  what keeps the test deterministic without a live server.
