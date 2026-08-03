# 40 — B9: live chat proof (spec + PROVEN) — 2026-08-03

> **B9.** Prove an AI player sends chat that the real server processes and delivers to another player.
> `ChatProbe` enters CombatBot_01 + CombatBot_02, bot A whispers a run-unique token to bot B via the client
> `Say2`(0x38) packet; the server routes it and bot B's connection observes the delivered `CREATURE_SAY`(0x4A)
> containing the token. No L2JM server source changes.

## Server facts (audited, SourceCode + handler scripts)
- Client `Say2`(0x38) readImpl: `[_text:readString][_type:int][_target:readString if whisper]`;
  `readString()` = null-terminated UTF-16LE 2-byte chars (`ReadablePacket.readString`).
- Server `CREATURE_SAY`(0x4A) writeImpl: `[0x4A][senderObjId:int][chatType:int][senderName][text]`.
- ChatType: `GENERAL(0)`, `SHOUT(1)`, `WHISPER(2)`, ... (`network/enums/ChatType.java`).
- `ChatWhisper` (script handler) delivers to `World.getPlayer(target)` with **no level gate / no range limit**;
  `ChatGeneral` is gated to `MinimumChatLevel=20`, so WHISPER is the right channel for low-level bots.
- Whisper sends `CreatureSay` to the receiver (senderName) and an echo to the sender (`"->"+receiverName`).

## Implementation (`AIPlayerEngine/.../examples/ChatProbe.java`)
Two-bot flow mirroring the proven B5 PvPProbe login/enter-world: enter bot A + bot B → reader threads on both
connections scanning decoded payloads for the token's UTF-16LE bytes → bot A sends `Say2`(0x38) whisper
`[0x38][token\0 utf16le][type:int=2][target\0 utf16le]` → close → report.

## ✅ Result (2026-08-03) — PROVEN
- A sent `Say2(0x38)` whisper to CombatBot_02 with token `B9WHISPER_621452`.
- **B's connection:** CREATURE_SAY(0x4A)=3, **1 with the token** (len 69) — the delivered whisper.
- **A's connection:** CREATURE_SAY(0x4A)=3, **1 with the token** (len 73) — its `->CombatBot_02` echo.
- `B received token = true`, `A echo = true` → **CHAT PROVEN**: the server processed A's chat packet and
  delivered it to another player's client.

## Reproduce
```
cd /home/volodro/L2JM/AIPlayerEngine && mvn compile
nohup timeout 45 bash -c 'java -cp target/classes com.aiplayer.examples.ChatProbe ai_combat_01 ai123pass ai_combat_02 ai123pass 127.0.0.1 7777 CombatBot_02' > /tmp/chat_probe.out 2>&1 &
# expect: "B received the whisper token = true" and "CHAT PROVEN ... = true"
```
