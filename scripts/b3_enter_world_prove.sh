#!/bin/bash
# b3_enter_world_prove.sh — PROVE B3: 1 AI player ONLINE (online=1) via the full external
# LoginServer auth + GameServer enter-world flow. Requires EnterWorldProbe compiled.
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
ACCOUNT="${1:-ai_combat_01}"
PASS="${2:-${AI_ACCOUNT_PASSWORD:-}}"
OUT=/tmp/b3_prove_out.txt
cd "$ENGINE"

echo "[b3] Restarting LoginServer to clear stale auth state..."
LSPID=$(pgrep -f 'java .*-jar ../libs/LoginServer.jar' | head -1)
[ -n "$LSPID" ] && kill -9 "$LSPID"
sleep 16
ss -tlnp 2>/dev/null | grep -q ':2106 ' || { echo "[FAIL] LoginServer not up"; exit 2; }

echo "[b3] Launching EnterWorldProbe ($ACCOUNT)..."
nohup timeout 55 java -cp target/classes com.aiplayer.examples.EnterWorldProbe \
    "$ACCOUNT" "$PASS" 127.0.0.1 7777 > "$OUT" 2>&1 < /dev/null &
PROBE=$!

for i in $(seq 1 30); do
    grep -q 'CHAR SELECTED' "$OUT" 2>/dev/null && break
    sleep 1
done

echo "[b3] >>> DB check while GS connection is held:"
sudo mysql -u root gameserver -e "SELECT char_name, account_name, online FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null
COUNT=$(sudo mysql -u root gameserver -e "SELECT COUNT(*) FROM characters WHERE account_name LIKE 'ai_%' AND online = 1;" 2>/dev/null | tail -1 | tr -d ' \t')
echo "[b3] AI players online (DB): ${COUNT:-0}"

kill -9 "$PROBE" 2>/dev/null
grep -E 'GS reply opcode|CHAR SELECTED|LoginFail' "$OUT" | head -10

if [ "${COUNT:-0}" = "1" ]; then
    echo "[OK] B3 PROVEN: 1 AI player online (online=1)."
    exit 0
else
    echo "[FAIL] Expected online=1, got '${COUNT:-0}'."
    exit 2
fi
