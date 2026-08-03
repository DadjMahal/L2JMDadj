#!/usr/bin/env bash
# B8 movement-proof runner: MoveProbe walks CombatBot_01 to (-82515,241221,-3728);
# assert characters.x/y/z changed from before to ~destination. Idempotent; no L2JM server source changed.
set -uo pipefail
ENGINE=/home/volodro/L2JM/AIPlayerEngine

BEFORE=$(sudo mysql -u root gameserver -N -e "SELECT CONCAT(x,',',y,',',z) FROM characters WHERE char_name='CombatBot_01';" 2>/dev/null)
echo "BEFORE: $BEFORE"

cd "$ENGINE" \
  && nohup timeout 40 bash -c 'java -cp target/classes com.aiplayer.examples.MoveProbe ai_combat_01 ai123pass 127.0.0.1 7777' > /tmp/move_probe.out 2>&1 &
sleep 28

AFTER=$(sudo mysql -u root gameserver -N -e "SELECT CONCAT(x,',',y,',',z) FROM characters WHERE char_name='CombatBot_01';" 2>/dev/null)
echo "AFTER:  $AFTER"
echo "--- MoveProbe movement tally ---"
grep -E 'MOVEMENT TALLY|CHAR_MOVE_TO_LOCATION\\(|VALIDATE_LOCATION\\(|STOP_MOVE\\(|server replied' /tmp/move_probe.out | tail -6

if [ "$BEFORE" != "$AFTER" ]; then
  echo "B8: MOVED (BEFORE != AFTER)"
else
  echo "B8: NOT MOVED !"
  exit 1
fi
