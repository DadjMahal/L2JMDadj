#!/bin/bash
# Session Start — resume-aware orientation. Run: ./scripts/session_start.sh [--build]
# If SESSION_IN_PROGRESS.md exists, it means the last session was rate-limited mid-work → resume it.
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=========================================="
echo "  SESSION START"
echo "=========================================="

# 0. RESUME CHECK (rate-limit-safe) — do this FIRST, before anything else
if [ -f "$REPO/SESSION_IN_PROGRESS.md" ]; then
  echo ""
  echo "⚠️  INTERRUPTED WORK DETECTED — last session was rate-limited mid-work."
  echo "Resuming (do NOT pick a new task):"
  echo "------------------------------------------------------------"
  cat "$REPO/SESSION_IN_PROGRESS.md"
  echo "------------------------------------------------------------"
  echo "→ Do the 'Current step' / first unchecked item, mark it, then WIP-commit."
  exit 0
fi

# 1. Orientation pointer
echo ""
echo "[1] Orientation: read $REPO/START_HERE.md (~800 tokens; honest state + routing table)"

# 2. Reality check (paste these outputs before claiming anything works)
echo ""
echo "[2] Reality check:"
echo "  -- git status (short) --"
git -C "$REPO" status --short | head | sed 's/^/    /'
echo "  -- ports (expect 2106 + 7777 LISTEN) --"
ss -tlnp 2>/dev/null | grep -E '2106|7777' | sed 's/^/    /' || echo "    (login/game ports not listening)"
echo "  -- real_status.sh --"
"$REPO/AIPlayerEngine/AIStatusLogs/real_status.sh" 2>/dev/null | sed 's/^/    /' || echo "    (real_status.sh failed)"

# 3. Next open task
echo ""
echo "[3] Open tasks (TODO/IN_PROGRESS/BLOCKED rows in Documentation/TASKS.md §3):"
grep -E '^\| (S[0-9]+-T|UP-)[^|]*\|.*\| *(TODO|IN_PROGRESS|BLOCKED)' "$REPO/Documentation/TASKS.md" | head -3 | sed 's/^/    /' || echo "    (none open — check Documentation/UpgradePlan/README.md §3 for next wave)"

# 4. Optional build check
if [ "${1:-}" = "--build" ]; then
  echo ""
  echo "[4] Build check:"
  (cd "$REPO/AIPlayerEngine" && mvn compile -q 2>&1 | tail -3 | sed 's/^/    /') || echo "    BUILD FAILED"
else
  echo ""
  echo "[4] (skip --build; run './scripts/session_start.sh --build' to compile)"
fi

echo ""
echo "=========================================="
echo "  READY TO START WORK"
echo "=========================================="
echo "Token budget: docs 500-1.5k | code+docs 2-5k | full feature 5-10k | audit 1.5-3k"
echo "Read START_HERE.md + the task carefully before starting."
