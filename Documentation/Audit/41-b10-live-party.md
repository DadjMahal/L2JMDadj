# 41 — B10: live party proof (spec) — 2026-08-03

> **B10.** Prove two AI players form a real party with the live server: bot A invites bot B via the client
> `RequestJoinParty`(0x29), the server sends `AskJoinParty`(0x39) to B, bot B accepts via
> `RequestAnswerJoinParty`(0x2A), and the server creates the party, sending party-window packets to both
> players (`PartySmallWindowAll` 0x4E to the leader, `PartySmallWindowAdd` 0x4F to the joiner). No L2JM server
> source changes.

## Server facts (audited, SourceCode)
- Client `REQUEST_JOIN_PARTY`(0x29) readImpl: `[_name:readString][_partyDistributionTypeId:int]` (target by NAME,
  not objId). Distribution ids: FINDERS_KEEPERS=0, RANDOM=1, RANDOM_INCLUDING_SPOIL=2, BY_TURN=3, ..=4.
- Client `REQUEST_ANSWER_JOIN_PARTY`(0x2A) readImpl: `[_response:int]` (1 = accept).
- Server `ASK_JOIN_PARTY`(0x39): `[requestorName][distributionTypeId:int]` → sent to the invitee.
- Server `JOIN_PARTY`(0x3A) → sent to the invitee on success.
- Server `PARTY_SMALL_WINDOW_ALL`(0x4E): `[leaderObjId][distId][memberCount-1][{objId,name,cp,maxcp,hp,maxhp,mp,maxmp,level,classId,unk,race}...]` → leader.
- Server `PARTY_SMALL_WINDOW_ADD`(0x4F) → joiner on success.
- Requirement: `RequestJoinParty.runImpl` does `World.getPlayer(name)` + `target.isVisibleFor(requestor)` →
  the two bots must be at the same location (both moved to -82515,241221,-3728).

## Implementation (`AIPlayerEngine/.../examples/PartyProbe.java`)
Two-bot flow (proven B5/B9 skeleton): enter A + B → reader threads tallying party opcodes on each connection →
A sends RequestJoinParty(0x29){target:"CombatBot_02", dist:1} → B sends RequestAnswerJoinParty(0x2A){1} →
close after ~6s → report per-connection party packet counts.

## ✅ Result — B10 PROVEN (2026-08-03)

`PartyProbe` (two bots, proven B3/B5 flow) at the same location (-82515,241221,-3728):

1. A sends `RequestJoinParty`(0x29) `[name "CombatBot_02" utf16le+null][distId=1]` → server routes
   `ASK_JOIN_PARTY`(0x39) to B (observed, len 31).
2. B sends `RequestAnswerJoinParty`(0x2A) `[response=1]`.
3. Server `Party.addPartyMember`: **B receives `PARTY_SMALL_WINDOW_ALL`(0x4E) len 83** (the joiner's full
   party window) and **A (leader) receives `PARTY_SMALL_WINDOW_ADD`(0x4F) len 79** (the Add for the new
   member). A also received `JOIN_PARTY`(0x3A).

Those party-window packets are only sent by `Party.addPartyMember` **after** a real `Party` object is created
(`requestor.setParty(new Party(...))` + `player.joinParty(...)`), so observing them is conclusive.

**B10 ══ PROVEN**: two AI players formed a real party with the live L2JM server.

> Note (debug detail): `PartySmallWindowAll`(0x4E) goes to the **joiner** and `PartySmallWindowAdd`(0x4F) to the
> **existing members** (`Party.addPartyMember`); the first probe build watched the wrong connections and showed
> false — fixed by counting both opcodes on both connections. Verified.

## Reproduce
```
# ensure both bots co-located (DB UPDATE), then:
cd /home/volodro/L2JM/AIPlayerEngine && mvn compile
nohup timeout 45 bash -c 'java -cp target/classes com.aiplayer.examples.PartyProbe ai_combat_01 ai123pass ai_combat_02 ai123pass 127.0.0.1 7777 CombatBot_02' > /tmp/party_probe.out 2>&1 &
# expect "B got PartySmallWindowAll (0x4E)=true" and "PARTY PROVEN ... = true"
```

