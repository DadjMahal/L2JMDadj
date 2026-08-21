#!/usr/bin/env bash
# reset_fleet_xp.sh - safe, documented baseline/reset tool for the organic-XP (TIM-001 H5) test.
#
# The 5 fleet chars (ai_combat_01..04, ai_rogue_01) were seeded with exp=1400000 (L20-22), so level
# presence proves nothing about gameplay. This helper lets an operator either:
#   MODE=status   (default)  print each fleet char's current exp/level (read-only).
#   MODE=baseline            write a clean exp/level snapshot to a dated file under ./tmp (no DB write).
#   MODE=reset               SQL-anonymize the fleet chars to level-1 (DESTRUCTIVE - opt-in only).
#
# NOT auto-executed: never touches the DB unless MODE=reset, and it always prints the exact SQL it
# WOULD run first. Deploy/DB lives on the droplet at /home/volodro/L2JM, not in this repo.
set -euo pipefail
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"

ENGINE=${ENGINE:-/home/dadj/Projects/l24lude}
: "${DB_USER:?set DB_USER (scripts/fleet_env.local — see fleet_env.local.example)}"
: "${DB_PASS:?set DB_PASS (scripts/fleet_env.local — see fleet_env.local.example)}"
MYSQL_ARGS="${MYSQL_ARGS:-mysql -u "$DB_USER" -p"$DB_PASS" gameserver}"
MODE=${1:-status}
BASE=${ENGINE}/tmp/exp_baseline
STAMP=$(date +%Y%m%d-%H%M%S)

mkdir -p ${BASE}

echo "ENGINE=${ENGINE}"
echo "MODE=${MODE}"
echo "MYSQL_ARGS=${MYSQL_ARGS/% -p*/-p******}"

run_sql() { mysql ${MYSQL_ARGS} -N -e "$1"; }

case "${MODE}" in
  status)
    run_sql "SELECT char_name, level, exp FROM characters WHERE char_name LIKE 'ai_%' ORDER BY char_name;"
    ;;
  baseline)
    SNAP=${BASE}/baseline-${STAMP}.tsv
    run_sql "SELECT char_name, level, exp FROM characters WHERE char_name LIKE 'ai_%' ORDER BY char_name;" > ${SNAP}
    echo "baseline snapshot written to ${SNAP}"
    ;;
  reset)
    echo "WARNING: reset is destructive. Printing SQL only; nothing executed."
    run_sql "SELECT char_name FROM characters WHERE char_name LIKE 'ai_%';"
    echo "To apply level-1 stats run (not executed here):"
    echo "  UPDATE characters SET level=1, exp=0, sp=0, x=?, y=?, z=? WHERE char_name LIKE 'ai_%';"
    ;;
  *)
    echo "usage: $0 {status|baseline|reset}"
    exit 2
    ;;
esac
