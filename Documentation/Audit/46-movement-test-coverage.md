# Audit 46 - Movement test-coverage gap assessment

Date: 2026-08-12 . Lane: task_0019 . Scope: read-only assessment of the movement path tests.

## What IS covered (task_0016 hardening, now in ZoneRouterTest)
- 21k route degrades into >= 3 hops, each within MAX_HOP_DIST (4800).
- Hop delivery is one-at-a-time and exhausts exactly at the destination (ack-gated pull contract).
- Degenerate (zero-length) routes produce zero hops (buildHops guard).
- Far-route bounds, farm-zone preference, pre-world refusal.

## Concrete missing test cases (with the exact methods they would target)
1. MoveTelemetry.recordMove / MoveTelemetry.report -- no test asserts the telemetry correctly tallies
   movesSent vs serverMoved per bot after a degraded (multi-hop) route. Add: send 5 hops, ack 3, assert
   report shows 5 sent / 3 server-moved / 2 degraded.
2. FleetPlay hop-gating branch (in the live class, not unit-covered) -- the nearHop/timedOut/neverSent
   ladder around ZoneRouter is only integration-tested. Suggest extracting the "advance vs resend vs
   abandon" decision into a small pure helper (e.g. HopGate.nextAction(now, near, sentAt)) and unit test
   it: not-sent->send, sent-and-near->advance, sent-but-stale->resend.
3. Phase0MovementConfig getMovementMinRadius/getMovementMaxRadius edge case (min equal max) -- ensure
   ZoneRouter.plan still yields a valid single-radius route (regression guard).
4. ZoneRouter.plan when ZoneRecommender returns zones but none within radius -- confirm it falls back to
   far-point rather than returning a null/goal with zero hops (currently covered implicitly by factory).
5. RouteGoal state after exhaustion -- assert nextHop() stays null and hasMoreHops() stays false after
   the last hop (partially in the new one-at-a-time test).

## Suggested priority
P0: item 1 (telemetry honesty) and item 2 (extract + unit-test the hop gate). P1: items 3-5.
