# RuntimeLog — 2026-08-22 EB-04 Personality → behavior mapping

**Task:** EB-04 — PersonalityProfile actually drives behavior params (risk, pace,
talkativeness), not decorative. Commit `38e607b3`.

## What changed
1. **`behavior/PersonalityBehavior.java`** (new, pure): the bridge from Personality →
   concrete decision knobs:
   - `Knobs` = surviveHpFraction (RISK), combat/sight range scales (PACE),
     restockThreshold, talkativeness.
   - `knobs(Personality)` mapping — AGGRESSIVE fights longer/wider, CAUTIOUS engages close,
     MERCHANT restocks very early (30%), SOCIAL is a chatterbox (1.0), EXPLORER hunts huge,
     null → NEUTRAL.
2. **`BotPlayConfig.withPersonality(Knobs)`** (BotPlayController): derives the config's real
   combatRange/sightRange/restockThreshold/surviveHpFraction from the personality knobs.
3. **`BotSession`**: deterministic per-bot personality (`PersonalityProfile.forSeed(100 + charId%100)`
   — same seed AIPlayer uses) → cfg = base-race-cfg.withPersonality(knobs). Each bot's decisions
   NOW actually vary by personality.
4. **`PersonalityProfile.forSeed(int)`** (learning): deterministic seed → personality.
5. **`AIPlayer`**: personality now via forSeed(accountId) (deterministic + consistent with
   BotSession); chat emission throttled by talkativeness (quiet≤60s, chatty≤8s interval).

## Tests
- New `PersonalityBehaviorTest` (7): every personality has sane knobs; aggressive riskier than
  cautious; cautious stays close vs explorer; merchant restocks early; social talkative; null →
  neutral; deterministic seed.
- Gate: **451/451 green** (was 444), style 0 violations, secret-lint clean.