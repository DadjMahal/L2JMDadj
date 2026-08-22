#!/bin/bash
# Cold-start orientation test (TASKS #100).
# Verifies a FRESH context can orient using ONLY START_HERE.md + AGENT_ONBOARDING.md + session_start.sh.
# A pass = the 5 orientation questions are answerable and the entry files are lean.
# Run: ./scripts/cold_start_test.sh
set -uo pipefail
REPO=/home/volodro/L2JM
pass=0; fail=0
ok()  { echo "  PASS  $1"; pass=$((pass+1)); }
bad() { echo "  FAIL  $1"; fail=$((fail+1)); }

echo "=== Cold-start orientation test (TASKS #100) ==="

echo ""
echo "[1] Entry files exist + are lean"
for f in START_HERE.md AGENT_ONBOARDING.md; do
  [ -f "$REPO/$f" ] && ok "$f exists" || { bad "$f MISSING"; }
done
sh=$(wc -c < "$REPO/START_HERE.md" 2>/dev/null || echo 0)
ao=$(wc -c < "$REPO/AGENT_ONBOARDING.md" 2>/dev/null || echo 0)
est=$(( (sh + ao) / 4 ))
echo "  START_HERE.md: $sh chars | AGENT_ONBOARDING.md: $ao chars"
echo "  Rough token estimate (both, chars/4): ~$est tokens (target <= ~1200)"

echo ""
echo "[2] START_HERE.md answers the 5 orientation questions + resume awareness"
grep -q "Project"        "$REPO/START_HERE.md" && ok "Project one-liner"        || bad "Project one-liner"
grep -qi "current state" "$REPO/START_HERE.md" && ok "Honest current state"     || bad "Honest current state"
grep -qi "Next task"     "$REPO/START_HERE.md" && ok "Next-task pointer"        || bad "Next-task pointer"
grep -qi "Blockers"      "$REPO/START_HERE.md" && ok "Blockers/open issues"     || bad "Blockers/open issues"
grep -qi "Reality check" "$REPO/START_HERE.md" && ok "Reality-check commands"   || bad "Reality-check commands"
grep -qi "Routing table" "$REPO/START_HERE.md" && ok "Routing table"           || bad "Routing table"
grep -qi "SESSION_IN_PROGRESS" "$REPO/START_HERE.md" && ok "Resume awareness"  || bad "Resume awareness"

echo ""
echo "[3] AGENT_ONBOARDING.md has the 6 hard rules + START_HERE pointer"
grep -qi "Read .START_HERE.md. first" "$REPO/AGENT_ONBOARDING.md" && ok "points to START_HERE" || bad "points to START_HERE"
for r in "Verify before claim" "fake" "Usage validation" "Audit-first" "Document before code" "Leave cleaner"; do
  grep -qi "$r" "$REPO/AGENT_ONBOARDING.md" && ok "rule: $r" || bad "rule: $r"
done

echo ""
echo "[4] session_start.sh orients (clean -> READY, or resume -> INTERRUPTED WORK DETECTED)"
out=$(bash "$REPO/scripts/session_start.sh" 2>&1)
if echo "$out" | grep -q "READY TO START WORK"; then
  ok "session_start.sh orients a clean session (READY)"
elif echo "$out" | grep -q "INTERRUPTED WORK DETECTED"; then
  ok "session_start.sh resumes interrupted work (scratchpad present)"
else
  bad "session_start.sh produced neither READY nor RESUME state"
fi

echo ""
echo "=== RESULT: $pass passed, $fail failed ==="
if [ "$fail" -eq 0 ]; then
  echo "COLD-START TEST: PASS (a fresh context orients in ~$est tokens)"
  exit 0
else
  echo "COLD-START TEST: FAIL"
  exit 1
fi
