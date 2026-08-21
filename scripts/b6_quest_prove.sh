#!/bin/bash
# b6_quest_prove.sh — PROVE B6: an AI player's enter-world triggers the live server quest engine.
# QuestProbe enters CombatBot_01 (charId 2); on enter-world the server's loadTutorial runs the
# Q00255_Tutorial 'UC' event handler and writes NEW quest state (Ex, ucMemo) to character_quests.
# Asserts: after run, character_quests has MORE Q00255 rows than the seeded baseline.
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
ACCT=${1:-ai_combat_01}; CHARID=${2:-2}; OUT=/tmp/b6_quest_out.txt
cd "$ENGINE"

# Fixture: a player who started the tutorial has this state (mirrors char-creation opt-in).
sudo mysql -u root gameserver -e "DELETE FROM character_quests WHERE charId=$CHARID AND name='Q00255_Tutorial'; INSERT INTO character_quests (charId, name, var, value) VALUES ($CHARID, 'Q00255_Tutorial', '<state>', 'Started');" 2>/dev/null
ROWS_BEFORE=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM character_quests WHERE charId=$CHARID AND name='Q00255_Tutorial';" 2>/dev/null || echo 0)
echo "[b6] character_quests Q00255 rows BEFORE = $ROWS_BEFORE (fixture only)"

# Restart LoginServer (clear 'account in use').
LSPID=$(pgrep -f 'java .*-jar ../libs/LoginServer.jar' | head -1)
if [ -n "$LSPID" ]; then
    kill -9 "$LSPID"; ready=0
    for i in $(seq 1 40); do sleep 1; ss -tlnp 2>/dev/null | grep -q ':2106 ' && { ready=1; break; }; done
    [ "$ready" -ne 1 ] && { echo "[FAIL] LS not up"; exit 2; }
    sleep 8
fi

nohup timeout 60 java -cp target/classes com.aiplayer.examples.QuestProbe \
    "$ACCT" "${AI_ACCOUNT_PASSWORD:-}" 127.0.0.1 7777 > "$OUT" 2>&1 < /dev/null &
wait $! 2>/dev/null
grep -E 'IN WORLD|QuestList|done' "$OUT" | head

echo "[b6] >>> character_quests for charId $CHARID AFTER enter-world:"
sudo mysql -u root gameserver -e "SELECT charId,name,var,value FROM character_quests WHERE charId=$CHARID;" 2>/dev/null
ROWS_AFTER=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM character_quests WHERE charId=$CHARID AND name='Q00255_Tutorial';" 2>/dev/null || echo 0)
echo "[b6] Q00255 rows BEFORE=$ROWS_BEFORE AFTER=$ROWS_AFTER"

if [ "${ROWS_AFTER:-0}" -gt "${ROWS_BEFORE:-0}" ]; then
    echo "[OK] B6 PROVEN: server added Q00255_Tutorial state on enter-world (loadTutorial UC handler ran live)."
    exit 0
else
    echo "[FAIL] B6 not proven: no server-added quest state."; exit 2
fi
