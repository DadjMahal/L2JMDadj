#!/usr/bin/env bash
# relocate_void_ai.sh — move the ai_% characters out of the default ("void") spawn and heal them.
#
# Stream G blocker (TASKS 109 / STATUS.md): 23 of the 25 ai_% bots sit at the default
# char-creation spawn (16600,17000,434 = "void") with no coords/HP set. Before multi-bot gameplay
# they must be relocated to the live B4 wolf-zone combat spawn and healed.
#
# The AI players are identified by ACCOUNT_NAME like 'ai_%' (their char_name is CombatBot_01 etc.).
# Only the ones stuck at the default spawn are moved; CombatBot_01/02 (already at the tested zone)
# are left untouched. ENV/server-DB operation (sudo mysql, gameserver db) — run on the L2JM host.
#
# Usage: bash scripts/relocate_void_ai.sh            # dry-run by default
#        bash scripts/relocate_void_ai.sh --apply   # actually UPDATE

set -euo pipefail
DB=gameserver
TARGET_X=-82759; TARGET_Y=250149; TARGET_Z=-3600   # B4 wolf-zone combat spawn
STUCK_X=16600; STUCK_Y=17000; STUCK_Z=434           # the "void" default spawn
DRY=1
[[ "${1:-}" == "--apply" ]] && DRY=0

echo "[relocate] move stuck bots from ($STUCK_X,$STUCK_Y,$STUCK_Z) -> combat zone ($TARGET_X,$TARGET_Y,$TARGET_Z), heal (db=$DB, dry-run=$DRY)"

if [[ $DRY -eq 1 ]]; then
  echo "--- stuck ai_% bots (to be moved) ---"
  sudo mysql -u root "$DB" -e "SELECT char_name, account_name, x, y, z, curHp, maxHp FROM characters WHERE account_name LIKE 'ai_%' AND x=$STUCK_X AND y=$STUCK_Y AND z=$STUCK_Z;"
  echo "[relocate] DRY RUN — no changes. Re-run with --apply to move + heal."
  exit 0
fi

sudo mysql -u root "$DB" -e "UPDATE characters SET x=$TARGET_X, y=$TARGET_Y, z=$TARGET_Z, curHp=COALESCE(maxHp,100), curMp=COALESCE(maxMp,50) WHERE account_name LIKE 'ai_%' AND x=$STUCK_X AND y=$STUCK_Y AND z=$STUCK_Z;"
echo "--- after: any ai_% still at default spawn? (should be 0) ---"
sudo mysql -u root "$DB" -e "SELECT COUNT(*) AS still_stuck FROM characters WHERE account_name LIKE 'ai_%' AND x=$STUCK_X AND y=$STUCK_Y AND z=$STUCK_Z;"
echo "--- all ai_% bots now ---"
sudo mysql -u root "$DB" -e "SELECT char_name, account_name, x, y, z, curHp, maxHp FROM characters WHERE account_name LIKE 'ai_%' ORDER BY account_name;"


