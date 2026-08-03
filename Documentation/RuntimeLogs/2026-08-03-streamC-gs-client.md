# Stream C — slice 4: reusable in-engine GameServer client (handshake + reader + send attach)

**Agent:** System (volodro) · **Board:** Stream C (wire proven packets into the engine) · Date: 2026-08-03

## Goal (this slice)
Give the engine a **reusable GameServer client** it can actually hold, instead of relying on the
probes' private `main()` sockets — so a `CombatAI.makeDecision()` → `executeCombatDecision` loop can
drive real combat live.

## Changes
1. **`protocol/PacketCodec.java`** — extracted the proven GS handshake payload builders:
   - `encodeProtocolVersion(int)` → `[0x00][version]` (5 B, plaintext)
   - `encodeAuthLogin(account, playOk2, playOk1, loginOk1, loginOk2)` → AuthLogin payload
   - `encodeCharacterSelect(slot)` → `[0x0D][slot][0][0][0][0]` (19 B)
   - `encodeEnterWorld()` → 105-byte EnterWorld (all-zero tracert)
2. **`engine/GameServerClient.java`** (NEW) — classic-`Socket` client that retains the proven
   B3/B4 flow: ProtocolVersion(746) → KeyPacket (key8 + suffix, packetEncryption flag) → AuthLogin
   (with the `L2JProtocol` SessionKey) → CharSelectInfo → CharacterSelect(slot) → CharSelected →
   EnterWorld. Exposes:
   - `startReader()` — background thread feeding every inbound packet (decrypted when crypt enabled)
     into the `PacketLogger`.
   - `attachToConnection(conn)` — attaches a `GameServerFrameWriter` over this socket so
     `CombatFramePlanner` frames (Action/AttackRequest/MoveToLocation) are sent here.
   - `sendGameFrame(byte[])` — write a pre-framed packet; `disconnect()`.

## Tests (added; 49 → 54 total, all pass)
- `PacketCodecCombatFrameTest` (+3): ProtocolVersion/AuthLogin/CharacterSelect/EnterWorld byte layouts.
- `GameServerClientTest` (NEW, 2): in-process fake GS server completes the full handshake and the
  client then sends a real `encodeAction`(0x04) frame the server receives; refuses to connect when the
  login is not established.

## Result (verified, not claimed)
```
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```
The handshake + send path is exercised end-to-end against a real socket (in-process fake GS).

## Honest scope note
The reusable client is proven against a scripted fake GS server; the live server proof still requires
the L2JM server up (LoginServer :2106 / GameServer :7777) and a bot relocated/healed out of the void,
run via a new `scripts/` driver that does login → `GameServerClient.connectAndEnterWorld` → `startReader`
→ `CombatAI.makeDecision` loop.

## Next (Stream C continuation)
- A `scripts/` driver (and/or `AIPlayerEngine` integration) that wires login → `GameServerClient` →
  `CombatAI.makeDecision()` → `executeCombatDecision` in a live loop, and run the live proof.
- Then StatusUpdate/QuestList feedback and B6b (NPC talk + RequestBypassToServer(0x21)).
