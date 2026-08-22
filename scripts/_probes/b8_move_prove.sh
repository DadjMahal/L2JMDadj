#!/usr/bin/env bash
# B8 movement-proof runner (fixed 2026-08-08): MoveProbe walks a bot a SHORT distance from its
# CURRENT live position (origin read from the DB, not a stale hardcoded spot). Assert the server
# confirmed the walk (MOVE_PROVEN=true from the CHAR_MOVE/VALIDATE/STOP tally) and/or the DB
# characters.x/y/z changed (server persists the walked position on logout). Idempotent; no L2JM
# server source changed.
set -uo pipefail
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
CHAR="${1:-CombatBot_01}"
ACC="${2:-ai_combat_01}"
PASS="${3:-${AI_ACCOUNT_PASSWORD:-}}"

BEFORE=$(sudo mysql -u root gameserver -N -e "SELECT CONCAT(x,',',y,',',z) FROM characters WHERE char_name='${CHAR}';" 2>/dev/null)
echo "[b8] BEFORE: $BEFORE"
OX=$(echo "$BEFORE" | cut -d, -f1); OY=$(echo "$BEFORE" | cut -d, -f2); OZ=$(echo "$BEFORE" | cut -d, -f3)

cd "$ENGINE" \
  && nohup timeout 40 java -cp target/classes com.aiplayer.examples.MoveProbe "$ACC" "$PASS" 127.0.0.1 7777 "$OX" "$OY" "$OZ" > /tmp/move_probe.out 2>&1 &
sleep 30

AFTER=$(sudo mysql -u root gameserver -N -e "SELECT CONCAT(x,',',y,',',z) FROM characters WHERE char_name='${CHAR}';" 2>/dev/null)
echo "[b8] AFTER:  $AFTER"
echo "--- MoveProbe movement tally ---"
grep -E 'MOVEMENT TALLY|CHAR_MOVE_TO_LOCATION|VALIDATE_LOCATION|STOP_MOVE|MOVE_PROVEN' /tmp/move_probe.out | tail -8

MOVED=0
[ "$BEFORE" != "$AFTER" ] && MOVED=1
REPLIED=$(grep -c 'MOVE_PROVEN=true' /tmp/move_probe.out 2>/dev/null || true)

if [ "$MOVED" = "1" ]; then
  echo "[b8] B8 MOVEMENT PROVEN: DB position changed ($BEFORE -> $AFTER)"
  exit 0
elif [ "$REPLIED" -ge 1 ]; then
  echo "[b8] B8 MOVEMENT EVENT CONFIRMED: DB unchanged but the server replied to our walk with movement packets"
  exit 3
else
  echo "[b8] B8 NOT MOVED: no server movement reply and DB unchanged (see /tmp/move_probe.out)"
  exit 1
fi
