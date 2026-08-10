# 🚀 CLAUDE UPGRADE BRIEF — "Times" (the big upgrade)

**Handed to:** Claude (via Cline lead) · **Date:** 2026-08-10 · **Branch base:** master
**You are the upgrade engineer.** Below is everything you need to know and exactly what to do
and why. Work in branches, PR-style commits, keep the live stack untouched.

---

## 1. The project right now (verified 2026-08-10)

- **Repo:** /home/dadj/Projects/l24lude — Java **AIPlayerEngine** (a real L2 bot client) + a
  running H5 L2jMobius **ServerBuild** (GameServer :7777, LoginServer :2106/:9014, MariaDB 11.8).
- **Master (d2cd7607, 79a1de41, b4b96f9e):** the Phase-0 Task1-11 package is committed and
  builds standalone; **skill-cast gate is open** (REQUEST_MAGIC_SKILL_USE `0x2F` live-proven:
  `RequestMagicSkillUse.java:42-44` = readInt skillId, readInt ctrl, readByte shift → 12-byte LE
  frame; `PacketCodec.encodeUseSkill`/`L2JProtocol.sendUseSkill`/`CombatFramePlanner.USE_SKILL`).
  Runtime seam `Phase0Integration` + gated `CombatAI` hooks + `Phase0Config` flags (all
  `phase0.*` OFF by default) wire Tasks 1/2/5/8 for real and expose honest SKIP seams for
  4/6/9/11; `SkillDatabase` fighter path realigned to H5; **141/141 tests green.**
- **Live fleet:** `examples/FleetPlay` runs 5 bots (ai_combat_01..05 / CombatBot_01..05) that
  wander, target attackables, auto-attack/kill and level. Dashboard: **http://localhost:8080**
  (in-JVM: `/`, `/json`, `/report`).

### Genuinely NOT done (your upgrade surface)
| Area | Gap | Blocker |
|---|---|---|
| Data layer | `PacketLogger` has **no XP**, **no level from CharInfo**, **self-position parse offset**, no quest-state, no incoming-chat | real H5 packet research + parsing + **live-probe before claiming** |
| Migration | ~34 files still read legacy `GameStateMirror` (unpopulated parallel state) | constructor/call-site rewrites → `BotSnapshot` |
| Skill data | `SkillDatabase` is a hand-curated subset misaligned with H5 | full datapack/class-tree alignment |
| Protocol stubs | ~20 `L2JProtocol` methods `Not implemented - stub` (chat variants, item-use, NPC action, restart-to-village, warehouse…) | each opcode needs the **prove-then-wire** gate (like 0x2F) |
| Lifecycle | death/respawn, consumables/soulshots, vendor/town, chat | blocked by the stubs above |
| Quest-NPC play | bots don't navigate to quest NPCs | needs quest packets + navigation wiring |
| Fleet | `AIPlayerManager` spawns but doesn't run the decision loop; dashboard has placeholders | adapt the proven FleetPlay loop |

---

## 2. Invariant rules (breaking one = failed task)

1. **No fabricated data, ever.** A field that needs a new packet parser gets a real parsing task
   + live-probe evidence. Never `return 1;` "for now". This project was burned by fakes twice —
   `Documentation/upgrade/INTEGRATION_GAPS.md` says so; believe it.
2. **Prove-then-wire every frame.** New outbound opcode → find its handler in
   `ServerBuild`/`SourceCode` `clientpackets/`, confirm field order/length, live-probe with a
   throwaway probe, then wire into `PacketCodec` + `L2JProtocol` with a byte-exact JUnit test.
   A GameServer `ACTION_FAIL` is a game condition (fine); a **disconnect or misparse is a
   wiring failure**.
3. **Brain edits are allowed but additive + behind a `phase0.*` flag defaulting OFF.** Never
   change default behavior. Follow conventions: MODE: COMPLETE|PARTIAL|PLACEHOLDER headers,
   JUnit 5, little-endian byte tests, `java.util.logging` (no System.out).
4. **The live stack is shared and precious.** Read-only probes, short timeouts, never restart
   GameServer/LoginServer destructively. A 5-bot fleet may be running; don't assume it is.
5. **Baseline:** `cd AIPlayerEngine && mvn -o test` must be **141/141** before you start and
   never regress. Any drop = stop and report; don't work around regressions.

---

## 3. Workstreams (in this order; commit + document each)

### W1 — Real packet data layer (the foundation)
- `PacketLogger`: parse **level from CharInfo** (live-probe the H5 CharInfo layout), parse
  **`getCurrentXp()`/`getExp()`** (find which packet carries XP in H5 — StatusUpdate / UserInfo;
  probe it), parse **self X/Y/Z correctly** (the existing parse is offset — diagnose the real
  CharInfo field order), parse **incoming chat** packets.
- Each parser = unit test with a captured real packet byte array (from the fleet log/probe) +
  a live histogram line in the audit note. Wire the fields into `BotSnapshot` (no new class).
- **Why first:** everything downstream — quest/farm scoring, dashboard, chat, level-up hooks —
  is blocked on it. Do not touch W2-W6 before W1.

### W2 — Delete the state-copy architecture
- Migrate the ~34 remaining `GameStateMirror` readers to `BotSnapshot` (per-file list in
  `Documentation/upgrade/INTEGRATION_GAPS.md`); thread `PacketLogger` into constructors where
  needed; keep `GameStateMirror` deprecated until ZERO readers remain, then delete it.
- `mvn test` green after every ~5 files; update the INTEGRATION_GAPS "Done" table as you go.

### W3 — SkillDatabase + class trees = the H5 truth
- Verification task that reads `ServerBuild/game/data/stats/skills/*.xml` + class skill trees
  and produces a corrected `SkillDatabase` (names, lv1 mpConsume/reuseDelay/castRange/minLevel,
  targetType). Fix every registered skill; hang real Gladiator/Warlord/archer/healer/buffer
  trees. Add a test asserting ≥3 skills match the XML exactly.

### W4 — Un-gate the protocol stubs (each under prove-then-wire)
Priority: **chat say/shout/tell**, **item-use (potion/soulshot)**, **restart-to-village**,
**NPC action/talk**, **warehouse/vendor**, **party**. For each: handler source → live-probe →
`PacketCodec.encodeX` + `L2JProtocol.sendX` (replace the stub) → byte test → wire the waiting
phase0 module (ChatResponder, ConsumableManager/SoulshotRestocker, RecoveryFlow/RespawnManager,
TownBehaviorEngine, PartyManager). Histogram goal per opcode in the audit note.

### W5 — Quest-NPC play (the visible "running to quest NPCs" goal)
- With W1+W4 done: use `TownNavigator`/`DynamicZoneManager` + quest packet state to find a
  nearby quest NPC, navigate to it, send `RequestBypassToServer` (proven) via
  `Phase0Wiring.bypass`, accept/turn-in, return to farm. Live proof in the fleet log/dashboard.

### W6 — Movement humanization (Task 3) behind flag
- Wire `MovementController`/`HumanizedPath`/`KiteController`/`StuckDetector` into the fleet
  decision loop behind `phase0.movement`; archer/mage kite correctly (already half-hooked).

### W7 — Fleet manager + dashboard truth
- Move the proven FleetPlay loop into `AIPlayerManager.spawn*` so role spawn/despawn manages
  real bots; feed the dashboard real data from W1 (level, xp-rate, kills, coords) and track
  per-bot kill/level events from real packets (DeleteObject/SystemMessage).

### W8 — Lifecycle hardening
- Resolve the 3 flagged `Thread.sleep` sites (PacketJitter, ConsumableManager, AutoLootHandler)
  via a per-bot thread audit — a decision, not a shrug.
- Death/respawn: with the restart opcode proven (W4), implement the full
  die → graveyard → respawn-at-village → return-to-farm loop (RecoveryFlow, RespawnManager).

---

## 4. Definition of Done (per workstream)
- `mvn -o test` green (total grows, never shrinks); `scripts/verify_no_dead_code.sh` passes.
- Each module: honest MODE header, JUnit test, **file:line evidence** in the audit note
  (`Documentation/Audit/4X-<workstream>.md`); live claims backed by a probe histogram or
  dashboard excerpt saved to the note.
- No fakes, no silent placeholders (if placeholder: PARTIAL + exact blocker).
- Branch `upgrade/wN-<slug>`, rebase on master, request merge (never merge master alone).

## 5. Explicit DON'Ts
- Don't spawn/invoke other agents; don't guess skill/reward numbers — read the XMLs.
- Don't claim "fixed" for a packet you didn't capture and test.
- Don't remove `phase0.*` flags "to simplify"; defaults stay OFF.
- Don't rewrite `AIPlayer`/`CombatAI` wholesale; extend additively.
- Don't leave `System.out`; use `java.util.logging`. Don't commit to master directly.

---

*Why this order:* data first (W1) → remove fake state (W2) → trust the skills (W3) → un-gate
frames (W4) → then the features users see (W5-W7) and hardening (W8). Each layer depends on the
one before; compile+test after every layer and stop on the first failure, exactly like the
original `Documentation/upgrade/INTEGRATION_ORDER.md` demanded.