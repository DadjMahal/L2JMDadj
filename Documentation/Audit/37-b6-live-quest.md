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

## Verification (paste both)
- Probe stdout: parsed QuestList shows quest id **255** (Q00255_Tutorial) active.
- DB: `character_quests` for charId 2 contains `Q00255_Tutorial` (state STARTED / cond).

## Reproduce
`scripts/b6_quest_prove.sh` (position+heal bot → restart LS → run `QuestProbe` → assert QuestList contains
255 and `character_quests` row for Q00255_Tutorial, or cond advances).
