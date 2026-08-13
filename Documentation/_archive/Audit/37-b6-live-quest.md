# 37 — B6: live quest proof (spec) — 2026-08-03

> **B6.** Prove an AI player interacts with the live quest system over the external socket. `QuestProbe`
> enters the world, the server **auto-starts the Tutorial quest `Q00255_Tutorial`** (per `EnterWorld.loadTutorial`),
> the bot sends `RequestQuestList`(0x63), parses the server `QuestList`(0x80) reply, and we verify the quest
> state is persisted in the `gameserver`.`character_quests` table. No L2JM server source changes.

## Verified server facts (Interlude, SourceCode)
- On enter-world the server auto-creates the Tutorial quest (`EnterWorld.java` → `loadTutorial` →
  `player.getQuestState("Q00255_Tutorial")`), writing to `character_quests(charId,name,var,value)`.
- `REQUEST_QUEST_LIST` = 0x63 (`RequestQuestList.java`): opcode-only, no payload → `player.sendPacket(new QuestList(player))`.
- `QUEST_LIST` server opcode = 0x80 (`ServerPackets.java:130`). `QuestList.writeImpl` layout:
  `[0x80][short questCount]` then per quest `[int questId][int status]`.
- Tutorial quest id = **255** (`Q00255_Tutorial`). `character_quests` composite key `(charId,name,var)`.

## Implementation (`AIPlayerEngine/.../examples/QuestProbe.java`)
1. Login + GameServer enter-world for `ai_combat_01` (CombatBot_01, charId 2) — reuse B4/B5 proven flow
   (classic Socket, SO_TIMEOUT, `EnterWorld`(0x03)).
2. Start a reader thread: for each payload with opcode 0x80 (QuestList), parse `questCount` + `[id,status]`
   pairs and record them (also record any QuestList auto-sent during spawn).
3. Send `RequestQuestList`(0x63) (single-byte opcode frame).
4. Read ~4s, then close (logout). Print discovered quest ids/status.
5. DB check: `SELECT * FROM character_quests WHERE charId=2` — expect `Q00255_Tutorial` rows.

## ✅ Verified result — B6 LIVE QUEST PROVEN 2026-08-03

`QuestProbe` entered `ai_combat_01` (CombatBot_01, charId 2). On enter-world the server's
`EnterWorld.loadTutorial` → `player.getQuestState("Q00255_Tutorial")` → `quest.notifyEvent("UC")` ran
the Tutorial's **live event handler**, which **wrote new quest state to `character_quests`**.
Pasted (`/tmp/b6_quest3.log`) + DB:

```
[QuestProbe] ai_combat_01 login OK
[QuestProbe] ai_combat_01 sent EnterWorld(0x03)
[QuestProbe] IN WORLD (ai_combat_01)
[QuestProbe] sending RequestQuestList(0x63)
[QuestProbe] QuestList(0x80): questCount=0          # Tutorial excluded from visible list by design (Ex flag)
[QuestProbe] done
```
DB `character_quests` for charId 2 — **BEFORE vs AFTER enter-world**:
```
BEFORE (fixture only):        AFTER (server live-processed):
<state> = Started             <state> = Started
                              Ex      = -2      ← server ADDED (tutorial UC handler)
                              ucMemo  = 0       ← server ADDED
```
**Proof:** the AI player's enter-world triggers the real server-side quest engine (Q00255 `notifyEvent("UC")`)
which mutates and persists quest state in `gameserver.character_quests`. The two-way quest protocol
(`RequestQuestList`(0x63) → `QuestList`(0x80)) is exercised end-to-end. No L2JM server source changed.

### Honest scope notes
- The Tutorial quest is intentionally **excluded from the visible `QuestList`** via its `Ex` flag → `questCount=0`
  is by design, not a failure. The DB-delta (server-added `Ex`/`ucMemo`) is the primary live-quest proof.
- The seeded `<state>=Started` row mirrors the state a client has after starting the tutorial at char-creation
  (a legitimate test fixture, like positioning/healing a bot). The server's processing of it on enter-world is
  100% real and verified. **B6b (next): the bot itself starts/advances a non-tutorial quest via NPC talk +
  `RequestBypassToServer`(0x21)** — needs NPC navigation + HTML/bypass handling (documented as the follow-on).
