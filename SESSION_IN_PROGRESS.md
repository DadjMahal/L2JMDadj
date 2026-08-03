# SESSION IN PROGRESS — B4: live NPC combat proof

> Resume check: this file existing means the last session was rate-limited mid-work. Resume the
> "Current step". If clean completion, `git rm` this file (it's folded into the final RuntimeLog).

## Goal
Prove B4: an AI player (CombatBot_01) attacks a real NPC monster via the external-socket flow,
verified by (a) server→client combat packets after our `Action`(0x04) and (b) DB `exp` increase.
Spec: `Documentation/Audit/35-b4-live-npc-combat.md`.

## Idempotent checklist (each safe to re-run)
- [x] Audit-first: read Action.java, AttackRequest.java, MoveToLocation.java, ClientPackets/ServerPackets
      opcodes, confirm `PacketEncryption = False`, CombatBot_01 DB baseline (exp=0).
- [x] Document before code: wrote `Documentation/Audit/35-b4-live-npc-combat.md`.
- [ ] Implement `CombatProbe.java` (extend EnterWorldProbe: enter world → read NPC_INFO → Action(0x04)
      + AttackRequest(0x0A) → read combat packets ~20s → print tally).
- [ ] `mvn clean compile -f AIPlayerEngine/pom.xml` → BUILD SUCCESS.
- [ ] Write `scripts/b4_combat_prove.sh` (restart LS, run probe, before/after exp, assert).
- [ ] Run against live server; PASTE probe output + before/after exp.
- [ ] RuntimeLog `Documentation/RuntimeLogs/2026-08-03-<HHMMSS>-b4-combat.md`.
- [ ] Update `START_HERE.md` + `STATUS.md` (B4 done); `git rm SESSION_IN_PROGRESS.md`; commit.

## Current step
Implementing `CombatProbe.java` (audit complete; spec written; ready to code).

## Last command output (baseline)
```
$ sudo mysql ... 'SELECT char_name,level,exp,sp,x,y,z,online FROM characters WHERE char_name="CombatBot_01"'
CombatBot_01  level=1  exp=0  sp=0  x=16600 y=17000 z=434  online=0   <- BEFORE
```
Server config: `PacketEncryption = False` (plaintext GS channel). Ports 2106+7777 LISTENING.

## If resuming: do this next
1. If `CombatProbe.java` not present/compiling → finish it per Audit/35 spec (Action 0x04 + AttackRequest 0x0A,
   plaintext framing via EnterWorldProbe.sendFrame pattern).
2. `mvn clean compile -f AIPlayerEngine/pom.xml`.
3. Run `scripts/b4_combat_prove.sh`; paste output. If no combat packets, the spawn area may lack monsters —
   move player with MoveToLocation(0x01) toward a NPC_INFO position, then retry Action.
