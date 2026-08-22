#!/bin/bash
# Golden Gate — the one offline command that proves a tree is merge-ready.
#   scripts/gate.sh            → run all stages (tests + style + secret-lint)
#   scripts/gate.sh tests      → only the Maven tests  (mvn -o, offline)
#   scripts/gate.sh style      → only the style checker
#   scripts/gate.sh secret     → only the hardcoded-secret lint
# Exit code: 0 = all requested stages green; 1 = any stage failed.
#
# Why this exists (F-07): every task must leave `mvn -o -f AIPlayerEngine/pom.xml test`
# green, follow the style conventions (check_style.sh) and carry zero hardcoded secrets
# (EP-6 policy). One command, fully offline, no network needed.
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || { echo "cannot cd to $ROOT"; exit 1; }

GREEN="\033[1;32m"; RED="\033[1;31m"; CYA="\033[1;36m"; RESET="\033[0m"
FAILED=0
pass(){ echo -e "${GREEN}[PASS]${RESET} $1"; }
fail(){ echo -e "${RED}[FAIL]${RESET} $1"; FAILED=1; }
header(){ echo -e "\n${CYA}=== $1 ===${RESET}"; }

stage_tests(){
  header "STAGE 1/3 — TESTS (mvn -o test, offline)"
  local log=/tmp/gate_mvn_test.log
  if mvn -o -f AIPlayerEngine/pom.xml test > "$log" 2>&1; then
    local n
    n=$(grep -E 'Tests run: [0-9]+, Failures' "$log" | tail -1 | sed -E 's/.*Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+).*/| Tests run: \1 | Failures: \2 | Errors: \3 |/')
    pass "mvn test green  $n"
  else
    fail "mvn test failed — tail of $log:"
    tail -n 25 "$log"
  fi
}

stage_style() {
  header "STAGE 2/3 — STYLE (scripts/check_style.sh)"
  if bash scripts/check_style.sh; then
    pass "style check green"
  else
    fail "style violations (see output above)"
  fi
}

stage_secret() {
  header "STAGE 3/3 — SECRET LINT (no hardcoded credentials in tracked code)"
  # Scan tracked code/config files for likely secret literals; allow example /
  # template / archived / attic files that legitimately show placeholders.
  local allow='\.example|_archive/|attic/|fleet_env\.local\.example'
  local bad
  bad=$(git ls-files | grep -E '\.(java|py|sh|properties|json|yml|yaml|xml|html|js)$' \
    | grep -vE "$allow" \
    | xargs grep -nEI 'ai123pass|changeme|change_me|password[[:space:]]*=[[:space:]]*[\[u"'"'"'][A-Za-z0-9@#$%^&*/+._-]{8,}|secret[[:space:]]*=[[:space:]]*[\[u"'"'"'][A-Za-z0-9@#$%^&*/+._-]{8,}' 2>/dev/null)
  if [ -z "${bad:-}" ]; then
    pass "no hardcoded secrets in tracked code/config"
  else
    fail "possible hardcoded secrets:"
    echo "$bad"
  fi
}

case "${1:-all}" in
  all)     stage_tests; stage_style; stage_secret ;;
  tests)   stage_tests ;;
  style)   stage_style ;;
  secret)  stage_secret ;;
  *)       echo "usage: $0 [tests|style|secret]"; exit 2 ;;
esac

echo -e "\n${CYA}=== GATE SUMMARY ===${RESET}"
if [ "$FAILED" -eq 0 ]; then
  echo -e "${GREEN}✔ GATE GREEN — merge-ready${RESET}"
  exit 0
else
  echo -e "${RED}✘ GATE FAILED — fix the stages above before pushing${RESET}"
  exit 1
fi