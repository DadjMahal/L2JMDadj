# WPT-27 — Quest telemetry (protocol + v1 contract)

**Date:** 2026-08-12 · **Commit:** `514f05c5` (pushed) · **State:** DONE-PUSHED
**Board:** team `task_0008` (WPT-27 Quest telemetry) + `task_0014` (WPT-27 quest telemetry protocol) → **completed**

## Goal
Parse the quest-list packets the server actually sends and expose the live per-bot quest log in the
v1 dashboard contract as **optional** keys (backward compatible with the frozen section-11 shape).

## Protocol verified
`SourceCode/java/org/l2jmobius/gameserver/network/ServerPackets.java` maps `QUEST_LIST → 0x80`
(top-level) and `EX_QUEST_INFO → 0xFE 0x19`. The `QuestList.java writeImpl` layout is:
`[count:2][per quest: questId:4][state:4]` — where the 4-byte state is either the positive current
**cond** step or, when bit 31 is set, the `__compltdStateFlags` bitmask (some steps were skipped).
A fresh `0x80` **replaces** the whole journal (matches `writeImpl` rebuilding the list each send).

## Changes
- `protocol/PacketLogger.java`
  - Added `OP_QUEST_LIST = 0x80` + main-switch dispatch case.
  - `parseQuestList(ByteBuffer)` records questId→state into a `ConcurrentHashMap` journal; clears on
    each new 0x80; counts `totalQuestCount` (all journal entries) and `activeQuestCount`
    (non-zero-state = in-progress/completed-step) quests.
  - New getters: `getQuestListCount()`, `getTotalQuestCount()`, `getActiveQuestList()` (list of
    `{questId, state}` pairs).
  - Existing `getActiveQuestCount()`/`getQuestInfoCount()` unchanged (no regressions).
- `examples/BotInfo.java` — added `questCount`, `totalQuestCount`, `activeQuests` (`{questId,state}`).
- `examples/FleetPlay.java` — feeds the live journal from `PacketLogger.getActiveQuestList()` each tick.
- `web/DashboardApi.java` — `botObject` gains the optional v1 key
  `"quests":{"active":N,"total":N,"list":[[id,state],...]}` (additive; frozen shape test still green).
- `test/.../PacketLoggerWpt27Test.java` — journal parse (counts + flag survival) + replace-on-reparse.

## Verification
- `mvn -o test`: **215 tests, 0 failures, 0 errors, BUILD SUCCESS** (213 prior + 2 new).
- `git push origin master`: `f784ff2f..514f05c5`.
- Live logs during the new test show `parseQuestList … QUEST_LIST: total=3 active=2` etc.
