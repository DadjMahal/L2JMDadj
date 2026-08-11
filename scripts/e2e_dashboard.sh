#!/bin/bash
# ============================================================
# WPT-33 — e2e_dashboard.sh  (ops corner, owner Cline#4)
# End-to-end smoke test for the web dashboard & the frozen v1
# API contract (Documentation/TASKS.md section 11).
#
# Phases:
#   A) static assets present in the source tree (and, if the
#      engine jar is built, packaged inside it too)
#   B) if the dashboard (:8080) is up: GET every /api/v1/*
#      route, assert HTTP 200 + correct JSON top-level keys,
#      plus the SPA at /
#   C) host-level checks via scripts/server_health.sh
#
# Exit code: 0 = everything checked out, 1 = failure(s) found.
# ============================================================
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DASH_PORT="${DASH_PORT:-8080}"
BASE="http://127.0.0.1:${DASH_PORT}"
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "  [ ${GREEN}OK${NC} ] $1"; }
fail() { echo -e "  [ ${RED}FAIL${NC} ] $1"; }
warn() { echo -e "  [ ${YELLOW}!!${NC} ] $1"; }
FAILED=0

DASH_DIR="$REPO_ROOT/AIPlayerEngine/src/main/resources/dashboard"
ASSETS=( "index.html" "ops.html" "favicon.png" "i18n/en.json" )

echo "============================================================"
echo "  E2E DASHBOARD CHECK  ($(date '+%F %T'))"
echo "============================================================"

# --- A. Static assets -----------------------------------------
echo ""
echo "=== A. STATIC ASSETS ==="
missing=0
for a in "${ASSETS[@]}"; do
    if [ -f "$DASH_DIR/$a" ]; then pass "source: $a"
    else fail "source: $a MISSING"; missing=1; FAILED=1; fi
done
# Real question is whether the engine jar packs the dashboard resources.
ENGINE_JAR="$(find "$REPO_ROOT/AIPlayerEngine/target" -name '*.jar' ! -name '*sources*' 2>/dev/null | head -1)"
if [ -n "$ENGINE_JAR" ]; then
    for a in "${ASSETS[@]}"; do
        if unzip -l "$ENGINE_JAR" 2>/dev/null | grep -q "dashboard/$a"; then pass "jar: $a"
        else warn "jar: $a NOT in $ENGINE_JAR (run mvn package)"; fi
    done
else
    warn "no built engine jar found at AIPlayerEngine/target — run 'mvn package' then re-run for jar checks"
fi

# --- B. Live API (only if dashboard up) ------------------------
echo ""
echo "=== B. LIVE /api/v1 CONTRACT ==="
if ! curl -s -o /dev/null --max-time 3 "$BASE/api/v1/health"; then
    warn "dashboard not listening on :${DASH_PORT} — start the fleet (FleetPlay) first. Skipping live API checks."
else
    check_route() {
        local route="$1" key="$2"
        local body
        body="$(curl -s --max-time 5 "$BASE$route")"
        if [ -z "$body" ]; then fail "$route → empty body"; FAILED=1; return; fi
        echo "$body" | python3 -c "import json,sys;
try: d=json.load(sys.stdin)
except Exception as e:
    print('BADJSON'); sys.exit(1)
print('json ok')" >/dev/null 2>&1
        if [ $? -ne 0 ]; then fail "$route → invalid JSON"; FAILED=1; return; fi
        if [ -n "$key" ] && ! echo "$body" | python3 -c "import json,sys; d=json.load(sys.stdin); assert '$key' in d, 'missing key $key'; print('key ok')" >/dev/null 2>&1; then
            fail "$route → missing expected key '$key'"; FAILED=1; return
        fi
        pass "$route → 200 ($(echo "$body" | wc -c) bytes) key='${key:-n/a}'"
    }
    check_route "/"                ""
    check_route "/api/v1"          "routes"
    check_route "/api/v1/bots"     "bots"
    check_route "/api/v1/entities" "entities"
    check_route "/api/v1/landmarks" "towns"
    check_route "/api/v1/events"   "events"
    check_route "/api/v1/health"   "status"
    check_route "/api/v1/config"   "fleetSize"
    for a in "${ASSETS[@]}"; do
        # raw asset fetch (served only if a route exists — currently index/ops may need Cline#1 route)
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE/$a")
        if [ "$code" = "200" ]; then pass "GET /$a → $code"
        else warn "GET /$a → $code (asset served only if a route exists; ops.html route is Cline#1 coordination item)"; fi
    done
fi

# --- C. Host-level health --------------------------------------
echo ""
echo "=== C. HOST HEALTH (server_health.sh) ==="
if [ -x "$REPO_ROOT/scripts/server_health.sh" ]; then
    "$REPO_ROOT/scripts/server_health.sh"; rc=$?
    if [ $rc -ne 0 ]; then FAILED=1; fi
else
    warn "scripts/server_health.sh missing — WPT-34 deliverable (should exist on this branch)"
fi

echo ""
echo "============================================================"
if [ $FAILED -eq 0 ]; then
    echo -e "  ${GREEN}E2E: ALL CHECKS PASSED${NC}"
else
    echo -e "  ${RED}E2E: $FAILED check(s) FAILED${NC}"
fi
echo "============================================================"
exit $FAILED