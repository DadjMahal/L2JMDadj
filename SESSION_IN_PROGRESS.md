# SESSION IN PROGRESS — B5: live PvP proof

> If this file exists on resume: the last turn was cut off. Resume "Current step". On clean completion
> fold into the final RuntimeLog + `git rm` this file.

## Goal
Prove two AI bots PvP (A=CombatBot_01 objId2, B=CombatBot_02 objId3) via mutual `Attack`(0x05)
packets attributed to each other. Spec: `Documentation/Audit/36-b5-live-pvp.md`.

## Idempotent checklist (safe to re-run)
- [x] Audit-first: onForcedAttack PvP path + Attack(0x05) layout + DB pvp columns confirmed.
- [x] Document before code: wrote `Audit/36`.
- [ ] Position + heal both bots in DB (same open field; neither in peace zone; alive).
- [ ] Implement `PvPProbe.java` (two connections, mutual Action/AttackRequest, reader threads tally attacker ids).
- [ ] `mvn clean compile -f AIPlayerEngine/pom.xml` → BUILD SUCCESS.
- [ ] Write `scripts/b5_pvp_prove.sh`.
- [ ] Run live; paste per-connection attacker-objId tallies.
- [ ] RuntimeLog + update START_HERE/STATUS/SESSION_HANDOFF/TASKS(task 61)/ai_progress_report; `git rm` this file; commit.

## Current step
Audit + spec done. Next: DB position/heal both bots, then code `PvPProbe`.

## If resuming: do this next
1. DB: position+heal CombatBot_01 (objId2) & CombatBot_02 (objId3) at same open field (e.g.
   -83477,250274,-3596 and -83440,250260,-3596), `curHp=maxHp, curMp=maxMp` (obj2 is level1/new).
2. Finish `PvPProbe.java` per Audit/36; build; restart LS; run via setsid; verify mutual attacker ids 2 & 3.
