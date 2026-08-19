# 🧠 AUDIT 04 — Bot intelligence & self-learning (goal 4)

> Owner question: *"Players should be smart in taking in-game actions; the engine should be
> perfect for self-learning; smart behavior must not be mandatory-hardcoded."*

## 1. Real decision flow (verified in code)

```
GameServer socket → protocol/PacketLogger (decode + self/world state, 2,190 lines)
  → phase0/BotSnapshot (per-tick world view) → Phase0Wiring
  → per-bot tick (examples/FleetPlay.BotLoop, ~300 ms):
       BotPlayController (phase0/play) — goal ladder: QUEST > HUNT > RELOCATE > RESTOCK > IDLE
       CombatAI.makeDecision (engine/) — target pick + action, hardcoded thresholds
       planners: RelocationPlanner · RestockPlanner · FleetSpreadPlanner · QuestGoalPlanner/
                 QuestChainPlanner · TownBehaviorEngine
  → CombatFramePlanner → GameServerFrameWriter → packets out
```

## 2. Verdict: the learning loop is OPEN (the core finding)

- **Write path works**: every kill feeds
  `FleetPlay:640 → CombatAI:341 → ReinforcementEngine.rewardKill(target, "ATTACK", xp)` →
  `AdaptiveLearner` → `DeepLearningCore` (+ EmotionalState) — 200+ learning events/min live.
- **Read path missing**: `ReinforcementEngine.getBestStrategy()`, `DeepLearningCore.predict*()`
  are **never called by any decision maker** (verified by grep — zero call sites outside
  learning classes). Bots learn diligently and then ignore everything they learned.
- Consequence: `DeepLearningCore` is pattern-frequency bookkeeping (context → action counts),
  not learning; `EmotionalState`/`PersonalityProfile` are decorative (no decision reads them);
  the only real "variety" is the `varietySeed`.

## 3. Hardcode inventory (worst offenders)

| Decision | Hardcoded as | File |
|---|---|---|
| engage/attack ranges | constants ×level factor 0.6 for L1-3 | FleetPlay / CombatAI |
| potion use | HP < 0.45, 20 s cooldown, item 1061 | FleetPlay.java:263-265 |
| retreat/regen | fixed HP fraction + REGEN_HOLD_MS | FleetPlay.java:127 |
| overwhelm back-off | SURROUND_CAP mobs | FleetPlay.java:129 |
| stale-target budget | level-scaled seconds | FleetPlay.java:91-99 |
| hunt zones & anchors | hand-entered tables | RaceGuide.java (679 lines) |
| skill rotations | preset tables | phase0/combat (13 files) |
| death guard | 3 deaths/60 s → 90 s hold | FleetPlay.java:131-133 |

All defensible bootstrap values — the problem is they are the **final** values, not priors the
engine can tune. (Hand-coded knowledge itself is being replaced by generated data in Audit 03.)

## 4. Capability gaps (ranked by gameplay impact)

1. **Learning never influences behavior** (§2) — kills the entire "smart" claim.
2. No per-bot memory beyond the live session: good spots, dangerous spots, mob difficulty —
   re-learned (or never) every tick.
3. Danger model = own HP only: no mob-density, level-difference, or aggro assessment before
   engaging (causes the 33-mob pulls that SURROUND_CAP papers over).
4. No mid-term plan: no "level 20 → class transfer quest" arc driver (chains.json exists only
   after GK-7; nothing consumes it yet).
5. No economy reasoning: adena income tracked (S7-T04) but spending is threshold-fixed.
6. No exploration: bots exploit the same anchors forever (anti-clustering is spatial, not
   informational).

## 5. Task prompts for Deepseek-v4-flash

| ID | Task | Effort | Depends | Status |
|---|---|---|---|---|
| IN-1 | Close the loop: decisions consult learned stats (bandit v0) | M | LW-3 | TODO |
| IN-2 | Utility-based combat gates (replace fixed thresholds with tunable utilities) | M | – | TODO |
| IN-3 | Per-bot HuntingMemory persisted + consulted for zone/mob pick | M | LW-5, GK-2 | TODO |
| IN-4 | Exploration (ε-greedy with decay) + risk personalities | S | IN-1 | TODO |
| IN-5 | Fleet-level policy sharing (gossip best arms between bots) | M | IN-1 | TODO |
| IN-6 | Consume chains.json: quest-arc driver overriding generic HUNT | M | GK-7 | TODO |

### PROMPT IN-1
```
TASK IN-1: Close the learning loop (contextual bandit v0)

CONTEXT
- Audit 04 §2: rewardKill feeds ReinforcementEngine/DeepLearningCore but getBestStrategy() is
  never consulted. The learning classes already keep per-context action scores.
- Reward stream for validation = data/observations/*.jsonl from LW-3 behavior watcher.

GOAL
Two decisions become learned (bandit style, ε from IN-4 later; here always-exploit with the
existing counts as priors):
1. Mob-type preference: when multiple hostile types are in range, target pick orders by
   DeepLearningCore score for context "combat:<mobType>" (XP/min proxy), falling back to
   current logic when a type has < 5 samples.
2. Hunt-zone choice: when RelocationPlanner needs a new zone, order candidate anchors by
   learned score "zone:<anchorId>" (reward = XP gained while stationed there).
Both behind one interface DecisionPolicy (learning/) so IN-4/IN-5 extend it, with a
PolicyMode flag: OFF | SHADOW (log-only, no behavior change) | ENFORCED.

STEPS
1. Add XP-per-minute attribution: on kill, credit the current zone anchor and mob type
   (ReinforcementEngine already receives xp — pass the anchor id too).
2. Wire the two picks behind DecisionPolicy; default SHADOW. Add a dashboard field showing
   top-3 learned zones/mobs per bot.
3. 30-min A/B live run: 10 bots SHADOW vs 10 bots OFF — paste per-group xp/min from the
   behavior dataset; then flip default to ENFORCED if no regression.

ACCEPTANCE
- Tests green (+policy unit tests incl. cold-start fallback); A/B table pasted; dashboard shows
  learned preferences; RuntimeLog.
```

### PROMPT IN-2
```
TASK IN-2: Utility gates instead of magic thresholds

CONTEXT
- Audit 04 §3 lists the hardcoded gates (potion 0.45 HP, SURROUND_CAP, retreat fraction, engage
  range). They work but are untunable per situation/bot.

GOAL
behavior/combat/DangerModel: per-tick score from observable state — hostile density in 800u,
average mob level vs bot level, own HP/MP fraction, death-window history. A UtilityGates class
turns DangerModel into decisions: engageUtility, retreatUtility, potionUtility (three numbers in
[0,1] + argmax). Thresholds become weights in AIConfiguration (tunable, per-class overrides),
seeded with TODAY'S values so default behavior is unchanged. FleetPlay's fixed checks call the
utility versions.

STEPS
1. DangerModel from BotSnapshot only (no new packets); unit tests with synthetic snapshots
   (empty field, 3 even mobs, 6 higher-level mobs, low HP after deaths).
2. UtilityGates with config-driven weights; golden tests asserting current default behavior
   (potion at 0.45-equivalent, retreat unchanged) so this refactor is provably neutral.
3. Log gate decisions to events.jsonl (type=gate_decision) so the behavior dataset can later
   learn the weights (IN-5+).

ACCEPTANCE
- Tests green incl. golden behavior tests (paste); 10-min live run: no rise in deaths or retreat
  frequency vs baseline (watcher rows pasted); RuntimeLog.
```

### PROMPT IN-3
```
TASK IN-3: HuntingMemory — bots remember good spots

CONTEXT
- data/sessions/<account>.json exists (LW-5). npcs.json (GK-2) gives spawn clusters/mob levels.

GOAL
Per-bot HuntingMemory persisted in the session file: map anchorId → {visits, xpPerMin ewma,
deaths}; map mobType → {kills, xp, avgKillMs, deaths}. RelocationPlanner prefers anchors with
top ewma (exploit) subject to IN-4 exploration; CombatAI target pick uses avgKillMs (avoid
tanky-for-level mobs). Cold start = uniform, 3-visit warm-up before influencing picks.

ACCEPTANCE
- Tests green (+memory unit tests: ewma update, warm-up, death penalty); 30-min live run shows
  ≥60% of relocation hops going to previously-profitable anchors (paste hop telemetry);
  RuntimeLog.
```

### PROMPT IN-4
```
TASK IN-4: Exploration + risk personalities

CONTEXT
- DecisionPolicy (IN-1) currently always exploits. PersonalityProfile exists but is decorative.

GOAL
1. ε-greedy exploration on zone/mob picks with per-bot ε starting 0.3 decaying to 0.05 over
   ~2h play (persisted in session file).
2. PersonalityProfile actually biases utilities: brave (retreat weight ×0.7), cautious (×1.4),
   greedy (adena-targeting weight up)… assigned randomly at bot creation (random race/style
   theme), visible on dashboard. Emotions modulate ε transiently (EXCITED → +0.1 explore).

ACCEPTANCE
- Tests green; live run: dashboard shows per-bot personality; dataset shows zone diversity
  (distinct anchors visited/hour) higher for high-ε bots (paste correlation rows); RuntimeLog.
```

### PROMPT IN-5
```
TASK IN-5: Fleet policy gossip — bots teach each other

CONTEXT
- Per-bot learned scores (IN-1/IN-3) live in memory/session files. Same-server bots share a
  world; knowledge should flow.

GOAL
Every 5 min a FleetPolicy gossiper merges: per-zone and per-mobType ewma across bots (weighted
by sample count) into a fleet-level prior file data/fleet_policy.json; bots with < N personal
samples fall back to the fleet prior (not uniform). Conflicts resolved by sample count, not
recency. Dashboard ops page shows top fleet zones/mobs.

ACCEPTANCE
- Tests green (+merge unit tests: counts weighting, cold-start-from-prior); live run: new bot
  joined at t=30min goes to the fleet's best zone within 2 hops (paste hops); RuntimeLog.
```

### PROMPT IN-6
```
TASK IN-6: Quest-arc driver (zero→hero actually drives behavior)

CONTEXT
- chains.json (GK-7) exists but nothing consumes it. Goal ladder today: QUEST only when an NPC
  is configured nearby.

GOAL
behavior/quest/CareerDirector: each bot loads its chain from chains.json at creation; the goal
ladder gains ARC state — when level ≥ chain's next quest minLevel and the quest isn't done,
QUEST goal routes the bot to startNpc (via KnowledgeBase.nearestNpcOf + existing
QuestNpcNavigator), uses QuestDialogDriver to accept, tracks objectives via QUEST_LIST, and
turns in (existing S3 primitives). Class-transfer quests trigger the class-change automation
(S3-T09/T10 primitives). Progress persists via session files.

ACCEPTANCE
- Tests green (+arc state-machine tests: level gate, accept, progress, turn-in, next);
  live run: one Human bot follows its chain 0→Q6→…≥3 quests deep (paste journal timeline from
  events.jsonl); RuntimeLog.
```
