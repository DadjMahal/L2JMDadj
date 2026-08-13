# Runtime Log — Stream E slice 2: Deterministic social behavior + party/swarm/collective (2026-08-04)

## Goal
Slice 1 wired the economy. **Slice 2** removes the last `Math.random()` mocks from the social
decision path and wires party formation → SwarmCoordinator + CollectiveKnowledge.

## What changed
1. **`SocialAI.setPacketLogger()`** — attach the live reader's logger (was a private empty one),
   mirroring CombatAI/QuestAI/MerchantAI.
2. **Deterministic decisions** (removed `Math.random()`):
   - `shouldSeekParty()`: SOCIAL personality (socialWeight>1.5) OR bored + a nearby
     non-hostile candidate + not in combat → seek party.
   - `shouldSeekClan()`: socialWeight > 1.2.
   - `shouldChat()`: socialWeight>1.3 OR bored, and not in combat.
   - `seekParty()`: targets a REAL nearby non-hostile entity (`objId=..`) instead of fake
     "NEARBY_PLAYER"; uses the bot's real position.
   - `seekClan()`: applies to `<name>-guild` instead of fake "NOVICE_CLAN".
   - `generateChat()`: contextual (bored / confident / neutral) instead of random array pick.
3. **Social outcome hooks**: `onPartyJoined(partyId)` now sets party state, **forms a swarm** in
   `SwarmCoordinator`, **shares into `CollectiveKnowledge`**, and reduces boredom;
   `onPartyLeft(partyId)` clears party state. (Before, AIPlayer's social singletons had no getters
   so this was impossible.)

## Proof — `StreamESocialTest` (5 tests, all PASS)
```
socialPersonalitySeeksPartyWhenCandidateNearby PASS (SOCIAL + nearby -> INVITE_TO_PARTY)
nonSocialBotDoesNotSeekParty                 PASS (CAUTIOUS -> not INVITE_TO_PARTY)
socialOutcomeDrivesPartySwarmAndCollectiveKnowledge PASS (party state + swarm +1 + knowledge)
chatOutcomeIsDeterministicAndContextual       PASS (quiet bot -> no spontaneous CHAT)
chatWhenBored                                PASS (bored bot -> not idle)
```
Full suite: **86/86 tests PASS (was 81/81), BUILD SUCCESS.** No regressions.

## What's NOT done yet (Stream E slice 3)
- Activity scheduling (task 88) + graceful reconnect/persistence (task 89).
- Task 91 docs (this RuntimeLog + Audit 37 + consolidated doc at stream end).
