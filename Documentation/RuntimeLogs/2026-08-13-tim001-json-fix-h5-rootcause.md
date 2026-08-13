# RuntimeLog — 2026-08-13 TIM-001: /json structural JSON bug FIXED + H5 root cause (bots dead)

Date: 2026-08-13 · Lane: lead (Cline) · Type: live evidence + code fix (positive, paste-worthy).

## What this run found and fixed

### 1. The real `/json` parse bug (root cause, not the quests-space)
The probe's inline parser had reported `Expecting ',' delimiter: line 1 column ~1198`. Root cause
was NOT the cosmetic `\" quests\"` key (JSON keys may legally contain spaces). It was in
`DashboardApi.legacyBotsBody()` / `botObject()`:

```java
sb.append(botObject(b));                  // closes the bot object with '}'
if (telemetry != null) {
  sb.append(",\"movedLast60\":...")        // <-- appended AFTER the closing '}' ...
    .append(",\"movesSent\":...");        // <-- => bare comma values in the "bots" ARRAY
}
```

So `/json` (and `/api/players`) emitted `[{"account":...}, "movedLast60":0,"movesSent":0, {...}]` —
the two counters landed as bare array elements → invalid JSON. This only happened when a
`MoveTelemetry` was wired (i.e. live FleetPlay), so the unit test (telemetry null) never caught it.

**Fix:** `botObject()` now takes `includeTelemetry` and emits `movedLast60`/`movesSent` as fields
INSIDE the object, before the closing brace. `/api/v1/bots` keeps the frozen section-11 shape
(call site passes `false`); `/json` + `/api/players` pass `true` and stay valid. Also corrected the
`\" quests\"` key to `\"quests\"` (matches frontend read).

**Verification (all passed):**
- New regression test `legacyJsonWithWiredTelemetryKeepsCountersInsideBotObjects` wires a real
  `MoveTelemetry` singleton and asserts `legacyJson()` parses as valid JSON with both counters
  present as **object fields**. Full suite green.
- Live end-to-end (rebuilt classes, 5-bot FleetPlay, movement ON): `curl /json` → **JSON VALID**
  with `movedLast60`/`movesSent` inside each bot object; `curl /api/v1/bots` → frozen shape, no
  telemetry counters. Both routes now well-formed.

### 2. H5 organic-XP root cause: the bots are DEAD and can't grind
Live `/json` states during the runs: every fleet bot is `state=dead`, `action=AUTO_PLAY`, with
10–23 hostile mobs in range. Dead bots land no kills ⇒ **no organic XP** (exp stayed at the seeded
1381342/1385671 the whole run). Earlier "exp changed" was the server correcting CombatBot_04's
seeded `L20/exp=1400000` back to its consistent `L22/exp=1385671` — a reconciliation, not gameplay.

So H5 is blocked by survivability: bots spawn into a heavy Orc-Fighter zone, die, and (AUTO_PLAY)
don't recover → nothing lands kills. Not a telemetry/pipeline bug.

## Persistence reconfirmation (H1)
CombatBot_04 moved during an earlier run and its DB row persisted after a clean disconnect-save
flush: `x/y = (-116158,242929) → (-120075,241209)` in `gameserver.characters`. H1 move-persistence
stays PROVEN.

## Verdict
- **/json + /api/players JSON validity: FIXED** (root-cause structural fix + regression test + live proof).
- **H5 organic XP: still NOT proven — now with a root cause** (bots die, cannot kill). Next step is a
  survivability fix: grant gear/revive-and-reposition, or fight lower-level mobs so bots actually
  land kills and accumulate organic exp.

---

## Follow-up (same day, COMMITTED): survivability fix for the H5 blocker

This run implemented + unit-tested the survivability fix that was blocking H5. All changes below
are covered by regression tests; full suite green **222/222**.

1. **Flee gate in `CombatAI.detectNearbyEnemy()`** — a bot at/under `combat.health_threshold` (raised
   30→50) no longer returns a target, so it can't re-engage a hostile pack while low. Previously only
   `shouldHeal()` consulted the threshold, so a 1-HP bot between fights would still "detect" a target
   and spiral into guaranteed death. Regression: `CombatAITest.testFleeGateHoldsLowHpOutOfCombat`
   (full HP / above gate → ATTACK; at / below gate → no engage).

2. **Auto-revive in `FleetPlay`** — on detecting `hpCurrent <= 0` the driver flips state to `dead` and
   calls `wiring.revive()` exactly once (rate-limited by the `dead`-state guard), and flips back to
   `alive` when HP recovers, instead of leaving a corpse permanently in `AUTO_PLAY`. Previously a dead
   bot never recovered (the observed H5 blocker).

3. **Revive transport** — `Phase0Wiring.revive()` → `PacketCodec.encodeRestartPoint(0)` (REQUEST_RESTART_POINT
   0x6D, `c`-style `pointType` as readInt matching `RequestRestartPoint.readImpl()`). Wire format locked by
   `PacketCodecCombatFrameTest.testEncodeRestartPointLayout`.

Net effect: bots now survive (flee before death) and self-recover (revive) so they can actually land
kills and accumulate organic XP — the precondition for H5 evidence. H5 proof itself is the next live
run (reposition + re-grind then diff `characters.exp`). H1 move-persistence remains PROVEN from run #3.