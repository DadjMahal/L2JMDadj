# REQUIREMENTS

## 1. Startup

Before starting any task:

1. First read all files inside `Documentation/`.
2. Use the documentation as the primary source of truth.
3. Do not rescan the repository unless the documentation is missing or outdated.
4. Check whether LoginServer or GameServer are already running.
5. If a restart is required, stop existing processes cleanly before starting new ones.
6. Never leave duplicate LoginServer or GameServer processes running.
7. Clear linux server's cache and free memory to be faster.
8. Reuse running services whenever possible.

Always understand the current project state before making changes.

### Resuming the source code audit

If `Documentation/Audit/PROGRESS.md` exists and lists any iteration with status
`pending` or `in_progress`, the **current task is to continue that audit** unless the
user explicitly requests something else. Follow the resume protocol in
`Documentation/Audit/PROGRESS.md` exactly:

1. Read `Documentation/Audit/PROGRESS.md`.
2. Find the first `pending` iteration (or finish the `in_progress` one first).
3. Read every file in that iteration's Scope.
4. Write its Output doc under `Documentation/Audit/`.
5. Update `PROGRESS.md` (mark done, set next pending → in_progress).
6. Repeat while token budget allows, then save `PROGRESS.md`.

No further user instruction is required to resume — the protocol is self-contained.


---

## 2. Development Rules

- Work only inside the current project.
- Modify source code only where intended by the project architecture.
- Never make manual runtime fixes that should instead come from the source code.
- If a rebuild is required, rebuild the project.
- Deploy the build into the runtime directory.
- Verify that the server still works.

Never leave the project in a broken state.

---

## 3. Validation

Before finishing every task verify:

- No startup exceptions remain.
- GameServer starts successfully.
- LoginServer starts successfully.
- "Server loaded" appears in the logs.
- GameServer registers on LoginServer.
- Runtime behavior matches the expected state.

If validation fails, continue working until the issue is resolved or clearly document why it cannot be resolved.

---

## 4. Documentation

Documentation is part of the project.

After every completed task:

- Keep documentation short, accurate and useful.
- Update existing files inside `Documentation/` if they became outdated.
- Never delete or replace existing documentation files unless explicitly requested.
- Document only information that will help future work.

---

## 5. Runtime Logs

After every completed prompt create a new report:

`Documentation/RuntimeLogs/<timestamp>-<task>.md`

The report should contain:

- Original user prompt (short version).
- Objective.
- Files modified.
- Problems encountered.
- How the problems were solved.
- Remaining issues (if any).
- Summary of completed work.
- Recommended next steps.

Keep the report concise (typically 10-70 lines).

These reports provide context for future sessions and should not duplicate the main documentation.

---

## 6. General Rules

- Minimize token usage.
- Prefer simple solutions.
- Prefer clean architecture.
- Minimize unnecessary complexity.
- Trust documentation before scanning source code.
- If documentation is outdated, update it immediately.
- Leave the repository cleaner than you found it.
