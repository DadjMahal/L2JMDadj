# RuntimeLog — 2026-08-22 EB-08 — Chat reply engine v0 + LLM seam

## Task
EB-08 — Chat reply engine v0: template + canned replies per context, LLM-ready seam.

## What was done
The engine already HAD a chat stack (ChatEngine → IntentClassifier → ResponseTemplate) but it was
hardcoded (no source seam) and context-blind (no cooldown/repeat guards). Built the v0 reply engine:

1. **`behavior/social/ReplySource.java`** (new) — THE LLM-READY SEAM: every reply decision funnels
   through this interface. Default = canned; a future `LlmReplySource` (BR-6) only implements it.
2. **`behavior/social/ReplyContext.java`** (new, immutable) — botAccount, speaker, message, intent,
   persona, template vars, previous reply to this speaker (anti-parrot), nowMs.
3. **`behavior/social/TemplateReplySource.java`** (new) — pure adapter over existing
   IntentClassifier + ResponseTemplate pools; says nothing on UNKNOWN (never answers "...").
4. **`behavior/social/ChatReplyEngine.java`** (new, pure + deterministic) — orchestration with
   COOLDOWN (same speaker < 15s → silent) + REPEAT-GUARD (never re-emit the identical previous
   reply to a speaker); per-bot seeded picks stay reproducible.
5. **`ChatEngine.onIncomingChat`** now delegates through `ChatReplyEngine` (profile-vars / 10%
   humanize-ignore / typo injection keep their place in the IO facade). Socket feeding remains
   SKIP-SOCIAL per Audit/43 (PacketLogger incoming-chat source + live-proven sendSay) — CO-4.

## Evidence / gate
- New `ChatReplyEngineTest` (11): canned greet, var substitution, silent unknown, blank, cooldown
  suppress, cooldown expiry, repeat-guard, independent speakers, LLM seam consulted, seam sees
  context, null-source stays silent. Classifier gotcha found: "yo" substring of "you".
- **GATE GREEN — 488/488 tests** (was 477), style 0 violations, secret-lint clean (exit=0).
- One commit set, pushed to master.