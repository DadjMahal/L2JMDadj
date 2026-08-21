#!/usr/bin/env bash
# ============================================================================
# tim001_reposition_fleet.sh — move the 5 TIM-001 CombatBots onto the Talking
# Island Keltir/Wolf FARM FIELD and heal them, so a fresh login drops them in
# real combat terrain (not the town Guard peace-zone where H5 does not fire).
#
# Target is the gludio32_1725_03 field (Bearded Keltir 20481 + Elder Keltir
# 20544 + Wolf 20120): TARGET=(-82759, 250149, -3600) — same combat spawn used
# by relocate_void_ai.sh. Only safe to run with the bots OFFLINE (a logout flush
# overwrites in-memory position, so reposition then log in fresh).
#
# Usage: bash scripts/tim001_reposition_fleet.sh [--apply]
# Env: MYSQL_ARGS
# ============================================================================
set -uo pipefail
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
: "${DB_USER:?set DB_USER (scripts/fleet_env.local — see fleet_env.local.example)}"
: "${DB_PASS:?set DB_PASS (scripts/fleet_env.local — see fleet_env.local.example)}"
MYSQL_ARGS="${MYSQL_ARGS:-mysql -u "$DB_USER" -p"$DB_PASS" gameserver}"
CHARS="CombatBot_01 CombatBot_02 CombatBot_03 CombatBot_04 CombatBot_05"
TX=-82759; TY=250149; TZ=-3600

echo "[reposition] dry-run target=($TX,$TY,$TZ) for: $CHARS"
echo "--- current ---"
$MYSQL_ARGS -e "SELECT char_name,x,y,z,curHp,maxHp FROM characters WHERE char_name IN ($(printf '\"%s\",' $CHARS | sed 's/,$//'));" 2>/dev/null

if [[ "${1:-}" == "--apply" ]]; then
  $MYSQL_ARGS -e "UPDATE characters SET x=$TX, y=$TY, z=$TZ, curHp=COALESCE(maxHp,100), curMp=COALESCE(maxMp,50) WHERE char_name IN ($(printf '\"%s\",' $CHARS | sed 's/,$//'));"
  echo "--- after ---"
  $MYSQL_ARGS -e "SELECT char_name,x,y,z,curHp,maxHp FROM characters WHERE char_name IN ($(printf '\"%s\",' $CHARS | sed 's/,$//'));" 2>/dev/null
  echo "[reposition] APPLIED — next FleetPlay login drops them on the Keltir/Wolf field."
else
  echo "[reposition] DRY RUN (no change). Re-run with --apply after the fleet is stopped."
fi
