# 28 — model deep line‑by‑line phase 2 expansion

Resume checkpoint
- Current status read from PROGRESS.md → 28 pending.
- Scope: deep, file‑by‑file audit of all thin‑summary docs that were generated as phase 1.
- Goal: transform each existing `NN‑*.md` from high‑level bullet lists into a full per‑class / per‑package audit that follows the exact template:
  - Purpose
  - Fields / State
  - Public API Surface
  - Control Flow
  - I/O
  - Gotchas / Refactor Candidates
- Where we are now:
  - All phase‑1 docs (`01‑commons.md` through `27‑ai-player-knowledge.md`) exist.
  - Many of them still contain only header‑level bullets.
- Procedural flow:
  1. **Pick the next unfinished doc** in numeric order.
  2. **Read all source files** referenced in its "Scope" field.
  3. **Expand each bullet** into concrete sentences that map to actual methods, fields, or config files.
  4. **Append a "Where to change X" table** that ties each actionable item to the exact class/method name.
  5. **Write a concise summary** (≈5‑10 lines) at the top.
  6. **Update PROGRESS.md** → move this iteration from `pending` to `done` and set the next pending iteration to the following number.
  7. **Create a runtime‑log entry** (`Documentation/RuntimeLogs/YYYY‑MM‑DD‑iterationXX‑<slug>.md`).
  8. **Commit** both the new markdown and the updated PROGRESS.md.
  9. **Continue** to the next pending number.
- Files already completed in phase 1 (keep for reference):
  - 01‑commons.md
  - 02‑loginserver.md
  - 03‑gameserver.md
  - 04‑gameserver‑network.md
  - 05‑model‑actor‑core.md
  - 06‑template‑layer.md
  - 07‑model‑actor‑stat.md
  - 08‑model‑actor‑status.md
  - 09‑model‑actor‑tasks‑holders.md
  - 10‑model‑actor‑instances.md
  - 11‑model‑item.md
  - 12‑model‑skill‑effects.md
  - 13‑model‑clan‑siege.md
  - 14‑model‑olympiad‑sevensigns.md
  - 15‑model‑zone‑world‑misc.md
  - 16‑ai.md
  - 17‑managers‑1.md
  - 18‑managers‑2.md
  - 19‑handlers‑taskmanagers.md
  - 20‑scripting‑util‑geo‑misc.md
  - 21‑tools‑log.md
  - 22‑scripts‑quests‑1.md
  - 23‑scripts‑quests‑2.md
  - 24‑scripts‑ai‑vehicles‑events.md
  - 25‑scripts‑handlers‑custom.md
  - 26‑game‑mechanics‑synthesis.md
  - 27‑ai‑player‑knowledge.md
- Current deepest pending number: **28**.

## How a phase‑2 entry looks (example template) – used for every new doc

```markdown
# <NN> — <slug> 

## Summary

- One‑sentence role of the whole package.
- Why it matters for the server engine and for AI‑player reasoning.

## Expanded per‑class / per‑package audit

### <ClassName> – <short‑purpose>
- **Purpose** – one‑sentence description.
- **Fields / State** – list of key attributes, mutability, concurrency notes.
- **Public API Surface** – method signatures with short behavioural summary.
- **Control Flow** – how it is created, invoked, lifecycle events.
- **I/O** – databases, XML/JSON config reads/writes, network packets.
- **Gotchas / Refactor Candidates** – structural concerns, tight coupling, duplication.

### <NextClass> – <short‑purpose>
- (repeat the same bullet list structure)

…

## Where to change X

| Concern | Class / Method | Actionable Change | Related Files |
|---------|----------------|-------------------|--------------|
| Example | Creature AI | Adjust intention timeout | CreatureAI.java, AI task manager |
| … |

## Cross‑cutting Impact

- How modifications here ripple into other docs (e.g., changing `CreatureTemplate` affects `Npc` and `PlayerTemplate`).

## Where to change X (continued)

---

## Runtime Log
