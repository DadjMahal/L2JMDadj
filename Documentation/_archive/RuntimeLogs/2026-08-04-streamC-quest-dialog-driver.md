# Stream C7/C8 · B6b — NPC-Dialog Quest Driver (honest rewrite)

**Date:** 2026-08-04
**Sev:** Stream C, slices C7 (quest frame planning) and C8 (full quest AI flow) + B6b (quest start via NPC dialog).

## Status: ENGINE WIRED & UNIT-TESTED (63/63) · LIVE DIALOG PROVEN · quest acceptance chain in progress

---

## Why the earlier C7 "proof" was WRONG (honest audit)

The previously committed `QuestFlowLoop` was producing a **false positive**:
- It sent a *cold* `RequestBypassToServer(0x21)` for `Q00255_Tutorial` and printed
  `QUEST_COMPLETED` whenever `activeQuests==0`. Because the tutorial quest is `Ex`-flagged
  and excluded from `QuestList`, `activeQuests` is **always** 0, so it claimed success trivially.
- **DB truth:** `character_quests` for charId 2 stayed EMPTY after every run — the server
  never accepted anything.

### Root cause (verified in L2JMobius source)
1. `RequestBypassToServer.runImpl()` calls `player.validateHtmlAction(_command)`. A bypass is
   **silently dropped** unless the exact command string was previously **shown to the player**
   in an `NpcHtmlMessage` from that NPC, and the player is within `Npc.INTERACTION_DISTANCE` (250).
2. `Q00255_Tutorial` has **no `onTalk` / no `addStartNpc`** — it auto-starts on enter-world
   (that is B6). There is nothing to bypass.

## The correct flow — now implemented

Chose a quest that is genuinely startable by NPC dialog:
**`Q00101_SwordOfSolidarity`**, given by **Roien (NPC 30008)** at `(-71384, 258304, -3104)`,
Human only, **level >= 9** (onTalk returns `30008-01.htm` for level < 9).

Verified server-side dialog chain (from `NpcClick`, `ScriptLink`, and quest html):
```
Action(0x04) #1 on Roien  -> SELECTS the NPC (NpcClick: target != player.getTarget() => setTarget)
Action(0x04) #2 on Roien  -> opens dialog (showChatWindow -> NpcHtmlMessage 0x0F)
  html = default/30008.htm, contains "bypass Script"
send "Script"                          (validated: was shown in the html)
  -> ScriptLink.showQuestWindow -> quest choose window
send "Script Q00101_SwordOfSolidarity"
  -> onTalk (30008-02.htm if Human + level>=9)
send "Script Q00101_SwordOfSolidarity 30008-02a.htm"
send "Script Q00101_SwordOfSolidarity 30008-02b.htm"
send "Script Q00101_SwordOfSolidarity 30008-03.htm"   -> onEvent -> st.startQuest() + giveItems(ROIENS_LETTER=796)
```
Every bypass is read from the html the server **actually** sent, so each one is validated.

## Code changes
- **`PacketLogger`** — parse `NpcHtmlMessage` (0x0F): `[npcObjId:int][html UTF-16LE NUL][itemId:int]`,
  expose `getLastNpcHtml()`, `getLastNpcHtmlOriginObjId()`, `getNpcHtmlCount()`,
  static `extractBypassLinks(html)` (strips `-h`, requires word boundary so "bypassed" is ignored),
  and `findEntityByNpcId(int)` + `addEntityForTest(...)`.
- **`QuestFlowLoop`** (rewritten) — genuine dialog driver: keep `Action(0x04)` clicking until the
  server returns an `NpcHtmlMessage` from that NPC, then follow the validated bypass chain.
  Removed the fabricated `QUEST_COMPLETED`/`activeQuests==0` success signal.
- **`scripts/c7_live_quest_proof.sh`** — honest rewrite targeting Q00101; fixture sets
  Human + level 9 + heal + position at Roien; asserts a NEW `character_quests` row (source of truth).

## Tests (PacketLoggerNpcHtmlTest — 4 new, => 63/63 pass)
- parse default Roien dialog (0x0F) + origin objId + `Script` link
- extract `-h` stripped / real Q00101 chain / "bypassed" ignored
- `findEntityByNpcId` locates quest-giver

## Live result — NODE-PROVEN (2026-08-04, second run)

The UTF-16LE bypass encoding fix closed the gap. The engine walked the FULL validated
dialog chain against the live server and **the server accepted the quest**:

```
SENT opcode=0x04 Action on NPC 30008 (click #1 -> selects)
SENT opcode=0x04 Action on NPC 30008 (click #2 -> opens dialog)
NPC_HTML -> default/30008.htm (links=1, first="Script")
SENT RequestBypassToServer "Script"
NPC_HTML -> quest window (links=1, first="Script Q00101_SwordOfSolidarity 30008-02a.htm")
SENT "Script Q00101_SwordOfSolidarity 30008-02a.htm"
NPC_HTML -> (links=1, first="... 30008-02b.htm")
SENT "... 30008-02b.htm"
NPC_HTML -> (links=1, first="... 30008-03.htm")
SENT "... 30008-03.htm"  -> START_EVENT_SENT (startQuest path)
```

**DB truth after run (charId=2):**
```
character_quests:  Q00101_SwordOfSolidarity  <state>  Started
                   Q00101_SwordOfSolidarity  cond      1
items:             item_id=796 (Roien's Letter)  count=1  <- granted by startQuest()
```
=> **B6b / C7 / C8 fully LIVE-PROVEN: a real quest started by genuine NPC dialog.**

## The root cause that was REALLY blocking (found 2026-08-04)
The engine WAS opening the dialog correctly (2x Action -> NpcHtmlMessage). The blocker was
`PacketCodec.encodeBypass`: it sent the command as **UTF-8 + single-null**, but L2JMobius reads
client strings as **UTF-16LE** (`BaseReadablePacket.readString()` = `readShort()` per 16-bit char,
short==0 terminator). So `"Script"` arrived as garbage shorts (0x6353, 0x6972, ...) and the server
dropped it — hence no quest window ever returned.
Fix: `encodeBypass` now writes `UTF_16LE` + `putShort(0)` terminator (matching the B9-proven
`encodeChat` convention). Regression test `testEncodeBypassUsesUtf16Le` added. **64/64 tests PASS.**

