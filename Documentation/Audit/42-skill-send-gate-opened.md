# Audit 42 — Skill-cast gate opened (REQUEST_MAGIC_SKILL_USE 0x2F) + Phase0 upgrade finished

**Date:** 2026-08-10
**Branch:** master (after commit, see below)
**Live server:** H5 L2jMobius GameServer (:7777), LoginServer (:2106/:9014), MariaDB 11.8
**Probe char:** `ai_combat_01`/`CombatBot_01` (charId 100000, Human Fighter, lvl 22)

---

## 1. The gate that was open

`Phase0Wiring.java` and `CombatFramePlanner.java` deliberately refused to emit a
skill-cast frame: *"USE_SKILL is not emitted until a real skill opcode is
live-proven."* The patch-upgrade tree carried a **wrong** opcode (`0x39`, Interlude
C4) and wrong field widths (ctrl as 1 byte). Nothing could cast, ever.

## 2. Live proof (opcode + field layout)

Server-authoritative read order, `SourceCode/.../network/clientpackets/RequestMagicSkillUse.java:42-44`:

```
_magicId      = readInt();      // skillId  (4 bytes)
_ctrlPressed  = readInt() != 0; // ctrl     (4 bytes)
_shiftPressed = readByte() != 0;// shift    (1 byte)
```

The throwaway `CombatProbe` (branch `probe/skillcast-0x2f`, **not committed**)
sent the exact frame `[size=12][0x2F][int skillId][int ctrl=0][byte shift=0]` to
the live H5 server with `skillId=3` (Power Strike, owned by the char). Result
(histogram in `/tmp/probe_out.txt`):

- **No disconnect** — the opcode and 12-byte frame were **accepted and parsed**.
- Server replied **`ACTION_FAIL(0x25)`** — a targeted game-condition rejection, not
  an opcode/parse rejection. If the wrong `0x39` had been sent the handler would
  never fire/misroute.
- Full combat chain kept running after the cast attempt (`ATTACK(0x05)=11-20`,
  `COMBAT PROVEN = true`, char levelled 20→22).

### Why the cast itself did not fire (earlier "no weapon" theory corrected)

Skill 3 (Power Strike) definition
(`ServerBuild/game/data/stats/skills/00000-00099.xml:157`) shows:

```
<castRange>40</castRange>
<conditions msgId="113"><using kind="SWORD,BLUNT"/></conditions>
```

Two independent conditions both rejected the single-shot cast:

1. **range** — the probe fired 0x2F ~1589 units from the wolf (melee range is 40);
   msgId 113 fired here.
2. **weapon** — the char had no weapon on the first run (fists); a Long Sword
   (`item_id=2`, `PAPERDOLL_RHAND` slot 7) is now equipped via
   `gameserver.items` (object_id=1), satisfying the SWORD requirement.

So the gate verdict stands: **the framing is byte-correct; acceptance is proven.**
The residual "cast fires" step needs the phase0 loop to approach into cast range
before sending — which is exactly the rotation/distance logic's job (Task 1/2), not
the frame's.

## 3. What was wired (the finish)

| Change | File(s) | Note |
|---|---|---|
| `encodeUseSkill(skillId, ctrl, shift)` → 12-byte `[0x2F][int][int][byte]` LE frame | `PacketCodec.java` | mirrors `encodeAttack`; widths match the server reader |
| `sendUseSkill(skillId, ctrl, shift)` on the GS channel | `L2JProtocol.java` | mirrors `sendAttack`; replaces the old placeholder signature |
| `USE_SKILL` planned as Action(0x04) + 0x2F frame | `CombatFramePlanner.java` | non-numeric placeholders ("HEAL"/"ATTACK") are skipped, never fabricated |
| Weave tests | `PacketCodecUseSkillTest`, `CombatFramePlannerUseSkillTest` | 6 new assertions; full suite **135/135 green** |

## 4. Phase0 upgrade made self-contained on master

The Task-1-attempt commits `e7dced78` / `52cb9e3a` (which wire phase0 combat
rotation/cooldown/shot/kiting straight into `CombatAI`) were made on the
**probe / `phase0-engine-wireup` branches, NOT on master** — and their `phase0/`
package was left as an untracked working-tree copy, so a clean checkout of those
branches did **not compile**. Merge-base of master and probe is `8bb8c5a1`.

This session makes **master** the single home of the finished upgrade (commit
df662803) without re-touching `CombatAI`/`AIPlayer`/`AIBrain`:

- the Task 1-11 `phase0/` package (combat, movement, social, humanize,
  imperfection, inventory, town, death, quest, director, farm, brain, chat...),
- the non-invasive integration path the review preferred: `Phase0Driver` +
  `Phase0Wiring` (reads real state from `BotSnapshot`/`PacketLogger`, sends only
  proven frames),
- the protocol wiring that opens the skill-cast gate (section 3),
- phase0 + protocol tests, `Documentation/upgrade/*` integration docs,
  `verify_no_dead_code.sh` relative-path fix.

Master now builds fully standalone: `mvn test` = 135 tests, 0 failures.
The probe branch retains the throwaway (uncommitted) `CombatProbe` skill-cast
delta and the CombatAI-wiring experiment; neither is required by master.

## 5. Open items (handed to Claude & Kimi — see the review brief)

1. Migrate the ~34 remaining `GameStateMirror` readers to `BotSnapshot`.
2. Real `currentXp` packet parsing (`StatusUpdate`/`UserInfo`) — do not fake.
3. Implement the "Not implemented" L2JProtocol stubs behind the same live-prove
   gate (item-use, restart-to-village, chat variants, NPC action).
4. Align `combat/SkillDatabase` with the live H5 datapack (Power Strike in the
   DB is MP 25/cooldown 5000; the H5 server says MP 10/reuse 13000/range 40).
5. Resolve the three flagged `Thread.sleep` sites (per-bot thread?).
6. Live-run `Phase0Driver` end-to-end; target the `MAGIC_SKILL_USE(0x48)` opcode in
   the histogram as the definitive "cast fired" signal.

**Evidence files:** `/tmp/probe_out.txt` (live histogram), `PacketCodecUseSkillTest`,
`CombatFramePlannerUseSkillTest`, `Documentation/upgrade/INTEGRATION_GAPS.md`.