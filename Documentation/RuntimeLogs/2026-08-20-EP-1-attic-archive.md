# EP-1 — Archive dead legacy engine classes to attic/

**Date:** 2026-08-20 · **Agent:** zcode (GLM 5.3) · **Status:** DONE

## Prompt (AUDIT_01 PROMPT EP-1)
Move every engine/*.java class not reachable from live code to
`AIPlayerEngine/attic/engine/` (outside src/, not compiled), package line kept as
comment; fix compile fallout; tests stay green; compute the dead closure yourself.

## What was done
1. Built the reference closure myself instead of trusting the audit list:
   - Roots = audit keep-10 + `QuestDecision`/`QuestFramePlanner` (see below)
     + every engine class directly referenced from live main code (all non-engine
     main files except `examples/*` probes; `FleetPlay.java` is live).
   - Transitive closure over class-name references inside engine/ (no reflective
     instantiation found — grep for `forName|newInstance|getClass(` is clean).
2. **Audit deviation found and resolved:** the audit claimed 10 live / 131 dead
   (direct references only). The compile-true transitive closure is **51 live /
   90 dead**: the keep-10's own tree (`AIPlayerEngine`→`AIPlayerManager`→
   Quest/Social/Merchant AI subsystem cluster; `CombatAI`→`CombatState`,
   `CombatConfig`, `PKDecision`, `PvPSkillRotation`; …) must stay compiled or the
   build breaks. Deviation accepted in favor of reality; EP-2/EP-5 relocate and
   merge this set.
   - `QuestDecision` + `QuestFramePlanner` are ZERO-main-ref but were test-locked
     2 days ago (S3-T09/T10, commit 3560e560) and AUDIT_04 IN-6 plans to consume
     them → kept (not dead).
3. `git mv` 90 dead classes → `attic/engine/`; 3 probes that only exercised dead
   classes (FivePlayerMagicShow, GoalDrivenLoop→LiveFeedbackBridge,
   TenMorePlayersDemo→AIPlayerReal) → `attic/examples/`; 1 dead-subject test
   (LiveFeedbackBridgeTest) → `attic/tests/`. Package lines commented per prompt.
   Wrote `attic/README.md` (what/why/how to resurrect).
4. Fallout fixed: `QuestGoal.java` had to come back — `QuestAI` uses its nested
   `QuestGoalDetail` by simple name only, invisible to a file-level closure.
   Re-verified: no remaining keep file references any archived class name.

## Evidence
```
$ find src/main/java/com/aiplayer/engine -name '*.java' | wc -l
51
$ ls attic/engine | wc -l && ls attic/examples attic/tests
90
attic/examples: FivePlayerMagicShow.java GoalDrivenLoop.java TenMorePlayersDemo.java
attic/tests: LiveFeedbackBridgeTest.java
$ mvn -o -f pom.xml clean test
[INFO] Tests run: 409, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
Baseline before task: 412 tests green (plan README said 383 — suite grew since
the audit). After: 409 green (−3 = LiveFeedbackBridgeTest's 3 tests archived
with its dead subject). No other test lost.

## Problems / notes for next tasks
- File-count acceptance "→ 10" is unachievable without breaking compilation;
  honest number is 51 (see deviation above). AUDIT_01 acceptance table updated.
- Nested-class deps are invisible to file-level greps — EP-2 must re-run the
  dependency check after every relocation batch, not just once.

## Next
EP-2: relocate the 51 live engine classes into target packages (net/, behavior/,
core/, cli/), delete engine/. Then EP-3 (phase0 rename wave).
