#!/usr/bin/env bash
# relocate_void_ai.sh — move the 23 ai_% characters out of the void spawn and heal them.
#
# Stream G blocker (STATUS.md): the ai_% bots spawn in the void. Before multi-bot gameplay they
# must be relocated to a live zone (matching the known wolf-zone spawn used by CombatLoop/B4) and
# healed. This is an ENVIRONMENTAL/server-DB operation (mysql on the gameserver db via sudo),
# NOT a code change — run it on the L2JM host, not blindly in CI.
#
# Usage: bash scripts/relocate_void_ai.sh            # dry-run by default
#        bash scripts/relocate_void_ai.sh --apply   # actually UPDATE

set -euo pipefail
DB=gameserver
X=-82759; Y=250149; Z=-3600   # wolf-zone spawn (B4 / CombatLoop seed)
DRY=1
[[ "${1:-}" == "--apply" ]] && DRY=0

echo "[relocate] target zone ($X,$Y,$Z) on db=$DB (dry-run=$DRY)"

if [[ $DRY -eq 1 ]]; then
  sudo mysql -u root "$DB" -e "SELECT character_name, x, y, z FROM characters WHERE character_name LIKE 'ai_%'"
  echo "[relocate] DRY RUN — no changes. Re-run with --apply to move + heal the ai_% chars."
  exit 0
fi

sudo mysql -u root "$DB" -e "UPDATE characters SET x=$X, y=$Y, z=$Z, curHp=maxHp, curMp=maxMp WHERE character_name LIKE 'ai_%';"
echo "[relocate] moved + healed:"
sudo mysql -u root "$DB" -e "SELECT character_name, x, y, z, curHp, maxHp FROM characters WHERE character_name LIKE 'ai_%'"

