#!/bin/bash
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
. "$(dirname "$0")/_dash_curl.sh"
# ============================================================
# WPT-30 — position_crosscheck.sh  (ops corner, owner Cline#4)
# TIM-001 evidence instrument: cross-check the *live* bot coords
# the fleet reports via the v1 API against the authoritative
# `gameserver.characters` MySQL rows for the same char.
#
# Every N minutes it:
#   curl GET /api/v1/bots             -> API x/y/z per bot (name)
#   SELECT x,y,z FROM characters      -> DB x/y/z per char_name
# and prints any bot whose API coords drift from its DB row
# beyond a threshold (i.e. server never persisted the movement).
#
# Read-only. No fabricated data. Fails gracefully when the fleet
# API or DB (or both) is down / not yet running.
#
# Usage:
#   ./position_crosscheck.sh                 # loop every 5 min
#   ./position_crosscheck.sh --interval 1    # loop every 1 min
#   ./position_crosscheck.sh --once          # single pass, exit
#
# Optional env/flag overrides (mirror server_health.sh):
#   API_URL, THRESHOLD, DB_USER, DB_PASS, DB_HOST
# ============================================================
L2JM_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_URL="${API_URL:-http://localhost:8080/api/v1/bots}"
THRESHOLD="${THRESHOLD:-1000}"      # drift in map units before flag
DB_USER="${DB_USER:-l2j}"
: "${DB_PASS:?set DB_PASS (scripts/fleet_env.local — see fleet_env.local.example)}"
DB_HOST="${DB_HOST:-localhost}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "  [ ${GREEN}OK${NC} ] $1"; }
fail() { echo -e "  [ ${RED}FAIL${NC} ] $1"; }
warn() { echo -e "  [ ${YELLOW}!!${NC} ] $1"; }
log()  { echo -e "  [ .. ] $1"; }

# --- CLI flags ---------------------------------------------------
INTERVAL=5          # minutes between passes (default readable)
ONCE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --interval) INTERVAL="${2:-5}"; shift 2 ;;
        --threshold) THRESHOLD="${2:-$THRESHOLD}"; shift 2 ;;
        --api) API_URL="${2:-$API_URL}"; shift 2 ;;
        --once) ONCE=1; shift ;;
        -h|--help)
            echo "usage: $0 [--interval MIN] [--threshold UNITS] [--api URL] [--once]"
            echo "  --interval MIN    sleep MIN minutes between passes (default 5)"
            echo "  --once            single pass then exit"
            exit 0 ;;
        *) echo "unknown arg: $1 (try --help)"; exit 2 ;;
    esac
done

command -v jq >/dev/null 2>&1 || { echo "FATAL: jq not found (needed to parse /api/v1/bots)"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "FATAL: curl not found in PATH"; exit 1; }
MYSQL_BIN="$(command -v mysql || true)"
[ -n "$MYSQL_BIN" ] || { echo "FATAL: mysql client not found in PATH"; exit 1; }
MYSQL="$MYSQL_BIN -u$DB_USER -p$DB_PASS -h$DB_HOST --connect-timeout=5"

# --- drift check --------------------------------------------------
# args: name apiX apiY apiZ  -> prints result; returns 1 if drift
drift_check()
{
    local name="$1" ax="$2" ay="$3" az="$4"
    local row dx dy dz dist dbx dby dbz
    row=$($MYSQL gameserver -N -B -e \
        "SELECT x,y,z FROM characters WHERE char_name='$(printf '%s' "$name" | sed "s/'/''/g")' LIMIT 1;" 2>/dev/null)
    if [ -z "$row" ]; then
        warn "bot '$name' reported by API but NO row in gameserver.characters (new char not persisted?)"
        return 0
    fi
    dx=$(awk -v a="$ax" -v b="$row" 'BEGIN{split(b,c,/\t/); print a-c[1]}')
    dy=$(awk -v a="$ay" -v b="$row" 'BEGIN{split(b,c,/\t/); print a-c[2]}')
    dz=$(awk -v a="$az" -v b="$row" 'BEGIN{split(b,c,/\t/); print a-c[3]}')
    dx=$(awk -v v="$dx" 'BEGIN{print (v<0?-v:v)}'); dy=$(awk -v v="$dy" 'BEGIN{print (v<0?-v:v)}')
    dz=$(awk -v v="$dz" 'BEGIN{print (v<0?-v:v)}')
    dist=$(awk -v dx="$dx" -v dy="$dy" 'BEGIN{printf "%.0f", sqrt(dx*dx+dy*dy)}')
    dbx=$(awk -v b="$row" 'BEGIN{split(b,c,/\t/); print c[1]}')
    dby=$(awk -v b="$row" 'BEGIN{split(b,c,/\t/); print c[2]}')
    dbz=$(awk -v b="$row" 'BEGIN{split(b,c,/\t/); print c[3]}')
    if [ "$dist" -gt "$THRESHOLD" ]; then
        fail "DRIFT '${name}' API(${ax},${ay},${az}) vs DB(${dbx},${dby},${dbz}) planarΔ=${dist}u (zΔ=${dz}) > ${THRESHOLD}u [TIM-001]"
        return 1
    fi
    pass "'${name}' API(${ax},${ay},${az}) vs DB(${dbx},${dby},${dbz}) planarΔ=${dist}u within ${THRESHOLD}u"
    return 0
}

# --- single pass --------------------------------------------------
run_pass()
{
    local stamp bots i n name ax ay az rc=0
    stamp="$(date '+%F %T')"
    echo "============================================================"
    echo "  POSITION CROSS-CHECK  ($stamp)  API=$API_URL  threshold=${THRESHOLD}u"
    echo "============================================================"

    # 1. fetch live fleet coords
    bots="$(curl -sS --max-time 10 "$(durl "$API_URL")" 2>/dev/null || true)"
    if [ -z "$bots" ]; then
        warn "fleet API unreachable at $API_URL (fleet down/starting?) — skipping this pass"
        return 0
    fi
    n=$(printf '%s' "$bots" | jq '.bots | length' 2>/dev/null || echo 0)
    if [ "$n" = "null" ] || [ "$n" -le 0 ] 2>/dev/null; then
        warn "no online bots reported by API (botCount=0) — nothing to cross-check"
        return 0
    fi
    log "live fleet reports $n bot(s)"

    # 2. DB reachable?
    if ! $MYSQL gameserver -N -e "SELECT 1" >/dev/null 2>&1; then
        fail "gameserver DB ping FAILED (user=$DB_USER host=$DB_HOST) — cannot cross-check"
        return 1
    fi

    # 3. compare each bot
    for i in $(seq 0 $((n-1))); do
        name=$(printf '%s' "$bots" | jq -r ".bots[$i].name // empty")
        ax=$(printf '%s' "$bots" | jq -r ".bots[$i].x // 0")
        ay=$(printf '%s' "$bots" | jq -r ".bots[$i].y // 0")
        az=$(printf '%s' "$bots" | jq -r ".bots[$i].z // 0")
        [ -n "$name" ] || continue
        drift_check "$name" "$ax" "$ay" "$az" || rc=1
    done

    echo "------------------------------------------------------------"
    if [ $rc -eq 0 ]; then
        echo -e "  ${GREEN}PASS: all ${n} live bot(s) within ${THRESHOLD}u of DB rows${NC}"
    else
        echo -e "  ${RED}CROSS-CHECK: drift detected (see DRIFT lines above) — TIM-001 evidence${NC}"
    fi
    return $rc
}

# --- main loop ----------------------------------------------------
fail_final=0
while true; do
    run_pass; [ $? -ne 0 ] && fail_final=1
    if [ "$ONCE" = "1" ] || [ "$INTERVAL" -le 0 ]; then
        echo "============================================================"
        exit $fail_final
    fi
    echo "  -- next pass in ${INTERVAL} min(s) (Ctrl-C to stop) --"
    sleep $((INTERVAL * 60))
    echo ""
done

exit $fail_final
