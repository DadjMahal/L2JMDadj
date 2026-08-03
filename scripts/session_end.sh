#!/bin/bash
# Session End — non-interactive commit + scratchpad cleanup.
# Usage: ./scripts/session_end.sh "commit message"
# If SESSION_IN_PROGRESS.md exists, it is folded into a handoff RuntimeLog and removed (clean state).
set -uo pipefail
REPO=/home/volodro/L2JM
MSG="${1:-feat: completed task}"

echo "=========================================="
echo "  SESSION END"
echo "=========================================="

# 1. RuntimeLog presence check (warn, don't fail)
LATEST_LOG=$(ls -t "$REPO"/Documentation/RuntimeLogs/*.md 2>/dev/null | head -1)
echo "[1] Latest RuntimeLog: ${LATEST_LOG:+$(basename "$LATEST_LOG")}"
[ -n "$LATEST_LOG" ] || echo "    ⚠️ No RuntimeLog found — create one per Documentation/WORKFLOW.md"

# 2. Stage + commit (non-interactive)
echo ""
echo "[2] Staging + committing: $MSG"
git -C "$REPO" add -A
if git -C "$REPO" diff --cached --quiet 2>/dev/null; then
  echo "    No changes to commit"
else
  git -C "$REPO" commit -m "$MSG" 2>&1 | tail -2 | sed 's/^/    /'
fi

# 3. Fold SESSION_IN_PROGRESS.md into a handoff log and remove (return to clean state)
echo ""
if [ -f "$REPO/SESSION_IN_PROGRESS.md" ]; then
  echo "[3] Folding SESSION_IN_PROGRESS.md into handoff log + removing (clean state)..."
  LOG_FILE="$REPO/Documentation/RuntimeLogs/$(date +%Y-%m-%d)-session-end-handoff.md"
  { echo "# Session End Handoff"; echo; cat "$REPO/SESSION_IN_PROGRESS.md"; } > "$LOG_FILE"
  git -C "$REPO" rm -q SESSION_IN_PROGRESS.md 2>/dev/null || rm -f "$REPO/SESSION_IN_PROGRESS.md"
  git -C "$REPO" add -A
  git -C "$REPO" diff --cached --quiet 2>/dev/null || git -C "$REPO" commit -m "chore: fold session scratchpad into handoff log, restore clean state" 2>&1 | tail -2 | sed 's/^/    /'
else
  echo "[3] No SESSION_IN_PROGRESS.md (already clean)."
fi

echo ""
echo "=========================================="
echo "  NEXT SESSION"
echo "=========================================="
echo "  1. ./scripts/session_start.sh   (auto-resumes if a scratchpad exists)"
echo "  2. Read START_HERE.md + TASKS.md"
