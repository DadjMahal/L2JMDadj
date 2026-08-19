# 🧩 Deepseek prompt template (v4-flash via Cline)

> Every task prompt in the AUDIT_*/RESEARCH_* files is an instance of this wrapper.
> When writing NEW tasks, copy this skeleton and fill the brackets. Deepseek gets the whole
> block verbatim — it must be self-contained (Cline starts with zero conversation context).

```
TASK <ID>: <title>

CONTEXT (do not skip)
- Repo: /home/dadj/Projects/l24lude. L2JMobius Interlude server + external-socket AI bot engine.
- Read first: Documentation/UpgradePlan/README.md §5 (audit facts) and §2 (hard rules).
- Relevant files: <list the exact files for THIS task>
- Depends on: <task IDs or "none">

GOAL
<one paragraph, outcome-focused, no ambiguity>

STEPS
1. <concrete step>
2. <concrete step>
...

CONSTRAINTS
- mvn -o -f AIPlayerEngine/pom.xml test MUST be green before and after (paste tail output).
- NEVER edit SourceCode/ or ServerBuild/ (read/parse only).
- Keep public APIs of live classes stable unless the task says otherwise; update all callers.
- One task = one commit: `type(scope): brief` + push. No unrelated changes in the diff.
- If something contradicts reality (file missing, different structure), STOP and report in the
  RuntimeLog instead of improvising around it.

ACCEPTANCE CRITERIA (all must be true, paste evidence for each)
- <measurable criterion + the command that proves it>
- ...

AFTER
- Write Documentation/RuntimeLogs/<date>-<ID>-<slug>.md (≤70 lines: prompt, files, problems,
  solutions, verification output, next steps).
- Update the task status in Documentation/UpgradePlan/<audit file> to DONE-PUSHED <hash>.
- Mirror status on Documentation/TASKS.md board row UP-<ID>.
```

## Writing rules for good prompts (for the owner and future planners)

1. **One task = one skill.** Renames, extraction, and wiring are separate tasks — never "refactor
   and also implement".
2. **Name exact files.** Deepseek performs best with a file list, not "the logging classes".
3. **Acceptance criteria are commands**, not adjectives ("`grep -r 'ai123pass' src/main` returns
   0 hits", not "no more hardcoded passwords").
4. **Sequence big moves.** Archive → rename → merge → re-test. Each step committed separately, so
   a bad step is one `git revert` away.
5. **Cap improvisation.** The "STOP and report" clause is what keeps an aggressive model from
   inventing architecture the owner can't review.
6. **Keep the test gate absolute.** If tests go red, the task is not done — no exceptions.
