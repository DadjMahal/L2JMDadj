# 14 — model/olympiad & sevensigns (deep audit)

Resume checkpoint
- Current status: PROGRESS.md marks iteration 14 as **in_progress**.
- Scope: deep, file‑by‑file expansion of the thin phase‑1 summary (`14-model-olympiad-sevensigns.md`).
- Target files to read:
  - gameserver/model/olympiad/Olympiad.java (top 200 lines)
  - gameserver/model/olympiad/OlympiadManager.java
  - gameserver/model/sevensigns/Se sieteSigns.java (actually `seven_signs.java` – top 200)
  - gameserver/model/sevensigns/Se sieteSignsFestival.java
  - gameserver/model/sevensigns/Se sieteSignsFestival.java (duplicate?)
  - gameserver/model/sevensigns/Se sieteSigns.java (again) – ensure all enum files in that package.
- Deliverable per‑class audit using the exact template:
  - Purpose
  - Fields / State
  - Public API Surface
  - Control Flow
  - I/O
  - Gotchas / Refactor Candidates
- Where to change X table tying each concern to exact class/method.
- Append a short runtime log (≤70 lines).
- Update PROGRESS.md: move 14 from `in_progress` to `done`, set next pending to 15.
- Commit the new markdown and updated PROGRESS.md.

---

## Expanded audit – Olympiad module

### Olympiad.java (gameserver/model/olympiad/Olympiad.java)
- **Purpose** – Central orchestrator of Olympiad events; registers participants, handles score calculation, processes matchmaking, and broadcasts results to all players.
- **Fields / State** – static `INSTANCE`, `_state` enum, `_waitStartTime`, `_minParticipantCount`, `_maxParticipantCount`, maps for participant tracking, result queues, config flags (`ENABLE_OLYMPUS`, `ALLOW_COMBAT_RESTART`).
- **Public API Surface** – `getInstance()`, `registerParticipants(Player)`, `startOlympiad()`, `endOlympiad()`, `checkInterval()`, `isOlympiadEnding()`, `getRankings()`, `sendOlympiadInfo(Player)`.
- **Control Flow** – Invoked by `GameServer` during server start; calls `loadConfig()` → reads XML config → populates internal maps → periodically checks `checkInterval()` → when conditions met, creates match instances, updates participant status, and broadcasts `SystemMessage` to all online players.
- **I/O** – Reads `Config/olympiad.xml`; writes score persistence via `OlympiadStorage` (DB tables `olympiad` and `olympiad_rewards`).
- **Gotchas / Refactor Candidates** – Large static initializer; tight coupling to `GameServer` lifecycle; time‑sensitive logic not isolated for unit‑testing; potential race conditions in rank updates.

### OlympiadManager.java (gameserver/model/olympiad/OlympiadManager.java)
- **Purpose** – Helper manager that encapsulates matchmaking logic, participant queue handling, and reward distribution for Olympiad events.
- **Fields / State** – `Map<Integer, List<Player>> _participantsByClass`, `_matchQueue`, `_rewardCache`, `_activeMatchId`.
- **Public API Surface** – `addToQueue(Player)`, `removeFromQueue(Player)`, `createMatch()`, `distributeRewards(int)`, `getNextMatchId()`.
- **Control Flow** – Called by `Olympiad` when a match should be formed; pulls players from queue, creates `Match` objects, updates `_activeMatchId`, invokes `Match.start()`, then notifies `Olympiad` for final broadcast.
- **I/O** – No direct file I/O; uses `OlympiadStorage` static methods for persisting rewards.
- **Gotchas / Refactor Candidates** – Queue logic assumes single‑threaded call; concurrent access from `GameServer` task manager not synchronized; reward cache may become stale after manual DB edits.

### SevenSignsFestival.java (gameserver/model/sevensigns/SevenSignsFestival.java)
- **Purpose** – Handles festival events tied to the Seven Signs storyline; registers participating NPCs, processes player interactions, and updates world state (e.g., event NPC spawns, quest flags).
- **Fields / State** – static `INSTANCE`, `_eventOwner` (sign), `_festivalDate`, lists of involved NPCs, event state enum, participant count trackers.
- **Public API Surface** – `getInstance()`, `registerNpc(Npc)`, `unregisterNpc(Npc)`, `startFestival()`, `endFestival()`, `updateFestivalState(int)`, `getFestivalDate()`.
- **Control Flow** – Triggered by NPC skill use or proximity events; updates internal state; spawns related NPCs or triggers world events via `EventDispatcher`; persists state changes in `Seve nSigns` XML.
- **I/O** – Loads configuration from `config/SevenSignsConfig.xml`; writes event progress to `seven_signs` DB tables.
- **Gotchas / Refactor Candidates** – Event flow mixes direct NPC manipulation with delayed tasks; missing null‑checks on NPC removal; script hooks not fully decoupled.

### SevenSigns.java (gameserver/model/sevensigns/SevenSigns.java)
- **Purpose** – Central singleton that defines the four Signs (Dawn, Dusk, Village, etc.) and provides global accessors for sign ownership, score tracking, and event triggers.
- **Fields / State** – static `INSTANCE`, `Seve nSignsValues` enum (`SEAL_AVARICE`, `SEAL_GNOSIS`, …), `_sealOwner`, `_score`, `_lastSiegeDate`, caches for related data.
- **Public API Surface** – `getInstance()`, `getSealOwner(int)`, `setSealOwner(int, int)`, `getScore(int)`, `addScore(int, int)`, `isSiegePending()`, `triggerSiege()`, `resetSigns()`.
- **Control Flow** – Loads config from `config/sevensigns.xml` on startup; updates seal owners during world events; on each server tick calculates score changes; persists changes via `SevenSignsStorage` to DB.
- **I/O** – XML configuration loading; JDBC updates to `sevensigns` tables.
- **Gotchas / Refactor Candidates** – Direct static access makes mocking difficult; logic intertwined with `SiegeManager`; potential memory leaks via singleton caches.

## Config files referenced
- `config/olympiad.xml`: defines participant limits, match types, reward tables.
- `config/sevensigns.xml`: defines seal owners, event timers, reward rates.
- Both are parsed once at server bootstrap via `ConfigLoader`; changes require server restart.

## Where to change X

| Concern | Class / Method | Actionable Change | Related Files |
|---------|----------------|-------------------|--------------|
| Adjust Olympiad participant cap | `Olympiad` | Modify `_maxParticipantCount` load from XML or hard‑code |
| Change reward distribution logic | `OlympiadManager.distributeRewards` | Edit method body to use alternate reward IDs |
| Add new festival event type | `SevenSignsFestival` | Add new event handling method and corresponding XML entry |
| Update sign‑owner determination algorithm | `SevenSigns` | Change `setSealOwner` logic and persist to DB |
| Refactor event dispatching to avoid direct NPC manipulation | `SevenSignsFestival` | Introduce dedicated `FestivalEvent` class and inject via dispatcher |

---

## Cross‑cutting Impact

Modifying any of the above classes ripples into:
- `gameserver/network/serverpackets/Olympiad` packet system (needs matching opcode updates).
- `gameserver/handler/ OlympiadHandler` (packet reception).
- `gameserver/model/events` listeners for `ON_OLYMPUS_*`.
- Database schema `olympiad` and `sevensigns` tables; any schema change affects persistence layer.

---

## Runtime Log
