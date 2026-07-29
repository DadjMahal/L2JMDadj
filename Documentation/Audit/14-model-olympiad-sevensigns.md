# 14 — model/olympiad & sevensigns

Resume checkpoint
- Read files:
  - gameserver/model/olympiad/Olympiad.java 1-520
  - gameserver/model/sevensigns/SevenSigns.java 1-520
- Still to read:
  - Olympiad match handler classes, SevenSigns festival/siege class, DB loader details if needed.
- Next: continue 15+.

---

## Olympiad.java
Purpose: Singleton manager for Olympiad competition cycle, nobles tracking, rankings, and rewards.
Fields/State: DB queries nobles/matches/heritage, concurrent nobles/rank/monitor maps, period state, scheduled validation/end/weekly tasks, hero/noble instance holders.
Public API Surface: nobles get/register/save, competition registration/validation, match start/end/broadcast, calculations for points/r Rankings, hero title management, olympiad shield/shop checks, send packet helpers.
Control Flow: constructor restores from DB and schedules period/validation switch; weekly calculations; match queue dispatched via OlympiadGameTaskManager; participants removed on validation end.
I/O: heavy JDBC load nobles/matches; scheduled tasks at fixed rate; broadcast packets for olympiad status/monitor.
Gotchas/Refactor Candidates: large singleton property violating SRP; nobless rank recalculation doubles tmpPlace; many date math in constructor.

## SevenSigns.java
Purpose: Global Seven Signs state machine with cabal/season/seal validation, festival scheduling, and mercenary spawns.
Fields/State: cabal scores, seals state, period/cycle dates, scheduled period change, DB persistence strings, NPC spawn sets, festival schedule cache, player contribution/type/ADB flags.
Public API Surface: period getter, cabal high score/mineral contribution, seal/owner getters, festival schedule/type, monster reward multipliers, period change listeners, DB save, spawn/destroy NPCs.
Control Flow: constructor loads DB and schedules next period exchange; seal validation/manor uses cabal scores; monster reward multipliers applied in gameplay; spawnSevenSignsNPC coordinated with AutoSpawnHandler; period change uses ThreadPool schedule.
I/O: DB-backed cabal/npc/state persistence; monster reward DB updates; spawn management.
Gotchas/Refactor Candidates: period change happens every calendar cycle and should tolerate old-time gaps; coupled to AutoSpawnHandler IDs hardcoded; many static calc methods.

## Where to change X
- Olympiad balance? Olympiad rating/points constants and match schedule logic.
- Seven Signs schedule/festival? SevenSigns.setCalendarForNextPeriodChange + spawn/config data.
- Olympiad reward items? Olympiad.getHero/vote/rank reward mappings + heritage table.
- Seven Signs cabal winner? SevenSigns.getCabalHighestScore and validation length.

---
