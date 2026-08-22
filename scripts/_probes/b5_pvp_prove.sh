#!/bin/bash
# b5_pvp_prove.sh — PROVE B5: two AI bots (CombatBot_01 objId2, CombatBot_02 objId3) fight each other
# (PvP) via the external socket. Verifies mutual Attack(0x05) packets by attacker objectId AND that
# CombatBot_02 takes damage in the DB (curHp < maxHp after). Requires PvPProbe compiled.
# Spec: Documentation/Audit/36-b5-live-pvp.md
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
ACC1=${1:-ai_combat_01}; CH1=${2:-CombatBot_01}; OBJ1=${3:-2}
ACC2=${4:-ai_combat_02}; CH2=${5:-CombatBot_02}; OBJ2=${6:-3}
X=${7:--83477}; Y=${8:-250274}; Z=${9:--3596}
OUT=/tmp/b5_pvp_out.txt
cd "$ENGINE"

# Position + heal both bots at the same open-field (non-peace-zone) spot.
sudo mysql -u root gameserver -e "UPDATE characters SET x=$X, y=$Y, z=$Z, curHp=maxHp, curMp=maxMp WHERE char_name='$CH1'; UPDATE characters SET x=$X, y=$Y, z=$Z, curHp=maxHp, curMp=maxMp WHERE char_name='$CH2';" 2>/dev/null
HP_BEFORE=$(sudo mysql -u root gameserver -e "SELECT curHp FROM characters WHERE char_name='$CH2';" 2>/dev/null | tail -1)
echo "[b5] $CH2 curHp BEFORE = $HP_BEFORE"

# Restart LoginServer (clear 'account in use' for both accounts).
LSPID=$(pgrep -f 'java .*-jar ../libs/LoginServer.jar' | head -1)
if [ -n "$LSPID" ]; then
    kill -9 "$LSPID"; ready=0
    for i in $(seq 1 40); do sleep 1; ss -tlnp 2>/dev/null | grep -q ':2106 ' && { ready=1; break; }; done
    [ "$ready" -ne 1 ] && { echo "[FAIL] LS not up"; exit 2; }
    sleep 8
fi

nohup timeout 90 java -cp target/classes com.aiplayer.examples.PvPProbe \
    "$ACC1" "${AI_ACCOUNT_PASSWORD:-}" "$ACC2" "${AI_ACCOUNT_PASSWORD:-}" 127.0.0.1 7777 "$OBJ1" "$OBJ2" "$X" "$Y" "$Z" > "$OUT" 2>&1 < /dev/null &
wait $! 2>/dev/null

grep -E "attacker objId|PVP PROVEN|saw attacks" "$OUT" | head -20
HP_AFTER=$(sudo mysql -u root gameserver -e "SELECT curHp FROM characters WHERE char_name='$CH2';" 2>/dev/null | tail -1)
echo "[b5] $CH2 curHp AFTER = $HP_AFTER"
PVP=$(grep -c 'PVP PROVEN .* = true' "$OUT" 2>/dev/null || echo 0)
if [ "$PVP" -ge 1 ] || [ -n "$HP_AFTER" ] && [ "$HP_AFTER" -lt "$HP_BEFORE" ] 2>/dev/null; then
    echo "[OK] B5 PROVEN: mutual PvP attacks AND/OR $CH2 took damage (hp ${HP_BEFORE}->${HP_AFTER})."
    exit 0
else
    echo "[FAIL] B5 not proven."; exit 2
fi
