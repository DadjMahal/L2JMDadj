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

## Live result
Dialogue path **proven**: the engine found Roien, clicked twice, received the genuine
`NpcHtmlMessage` (`Grand Master Roien...` with `bypass Script`), and sent the validated
`RequestBypassToServer: "Script"`. The quest-acceptance tail (Script Q00101 -> 30008-03.htm)
is the remaining live step.
