#!/bin/bash
# ============================================================
# WPT-34 — server_health.sh  (ops corner, owner Cline#4)
# Real health snapshot: ports + DB pings + character stats +
# login-server account counts. Read-only, no fabricated data.
#
# Exit code: 0 = all critical checks OK, 1 = something down.
# Intended for watchdog/cron use (e.g. `./server_health.sh`).
#
# Optional env overrides:
#   DB_USER, DB_PASS, DB_HOST   (defaults match ServerBuild config)
# ============================================================
L2JM_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_USER="${DB_USER:-l2j}"
DB_PASS="${DB_PASS:-StrongPasswordHere}"
DB_HOST="${DB_HOST:-localhost}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "  [ ${GREEN}OK${NC} ] $1"; }
fail() { echo -e "  [ ${RED}FAIL${NC} ] $1"; }
warn() { echo -e "  [ ${YELLOW}!!${NC} ] $1"; }
log()  { echo -e "  [ .. ] $1"; }

MYSQL_BIN="$(command -v mysql || true)"
[ -n "$MYSQL_BIN" ] || { echo "FATAL: mysql client not found in PATH"; exit 1; }
MYSQL="$MYSQL_BIN -u$DB_USER -p$DB_PASS -h$DB_HOST --connect-timeout=5"

FAILED=0

echo "============================================================"
echo "  L2JM SERVER HEALTH  ($(date '+%F %T'))"
echo "============================================================"

# --- 1. Ports -------------------------------------------------
echo ""
echo "=== 1. SERVER PORTS ==="
for port_spec in "LoginServer client:2106" "LoginServer GS listener:9014" "GameServer client:7777" "Web dashboard:8080"; do
    name="${port_spec%%:*}"; port="${port_spec##*:}"
    if ss -tln 2>/dev/null | grep -qE "[:.]${port}[[:space:]]"; then
        pass "$name :$port LISTENING"
    else
        if [ "$port" = "8080" ]; then
            warn "$name :$port not listening (optional — only when fleet/dashboard runs)"
        else
            fail "$name :$port NOT listening"; FAILED=1
        fi
    fi
done

# --- 2. Gameserver DB + characters ----------------------------
echo ""
echo "=== 2. GAMESERVER DB / CHARACTERS ==="
if $MYSQL gameserver -N -e "SELECT 1" >/dev/null 2>&1; then
    pass "gameserver DB ping OK"
    TOTAL_CHARS=$($MYSQL gameserver -N -e "SELECT COUNT(*) FROM characters;" 2>/dev/null | tr -d ' \t')
    AI_CHARS=$($MYSQL gameserver -N -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%';" 2>/dev/null | tr -d ' \t')
    AI_ONLINE=$($MYSQL gameserver -N -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null | tr -d ' \t')
    echo "  [ .. ] total characters : ${TOTAL_CHARS:-?}"
    echo "  [ .. ] ai_% characters  : ${AI_CHARS:-?}"
    if [ -n "$AI_ONLINE" ] && [ "$AI_ONLINE" -gt 0 ]; then
        pass "ai_% bots online: $AI_ONLINE"
    else
        warn "ai_% bots online: 0"
    fi
    echo ""
    echo "  --- current ai_% bot positions (x,y,z / online / last level) ---"
    $MYSQL gameserver -e "SELECT account_name, char_name, level, online, x, y, z FROM characters WHERE account_name LIKE 'ai_%' ORDER BY account_name;" 2>/dev/null \
        | sed 's/^/  /'
    echo ""
    echo "  --- level distribution (all chars) ---"
    $MYSQL gameserver -e "SELECT level, COUNT(*) FROM characters GROUP BY level ORDER BY level;" 2>/dev/null | sed 's/^/  /'
else
    fail "gameserver DB ping FAILED (user=$DB_USER host=$DB_HOST)"; FAILED=1
fi

# --- 3. Loginserver DB / account counts ("LN counts") ---------
echo ""
echo "=== 3. LOGINSERVER DB / ACCOUNTS ==="
if $MYSQL loginserver -N -e "SELECT 1" >/dev/null 2>&1; then
    pass "loginserver DB ping OK"
    LN_ACCOUNTS=$($MYSQL loginserver -N -e "SELECT COUNT(*) FROM accounts;" 2>/dev/null | tr -d ' \t')
    LN_AI=$($MYSQL loginserver -N -e "SELECT COUNT(*) FROM accounts WHERE login LIKE 'ai_%';" 2>/dev/null | tr -d ' \t')
    LN_GS=$($MYSQL loginserver -N -e "SELECT COUNT(*) FROM gameservers;" 2>/dev/null | tr -d ' \t')
    echo "  [ .. ] total accounts  : ${LN_ACCOUNTS:-?}"
    echo "  [ .. ] ai_% accounts   : ${LN_AI:-?}"
    echo "  [ .. ] gameservers reg : ${LN_GS:-?}"
else
    fail "loginserver DB ping FAILED"; FAILED=1
fi

# --- 4. Summary ------------------------------------------------
echo ""
echo "============================================================"
if [ $FAILED -eq 0 ]; then
    echo -e "  ${GREEN}HEALTH: ALL CRITICAL CHECKS OK${NC}"
else
    echo -e "  ${RED}HEALTH: $FAILED critical check(s) FAILED${NC}"
fi
echo "============================================================"
exit $FAILED