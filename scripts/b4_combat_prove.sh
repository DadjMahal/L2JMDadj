#!/bin/bash
# b4_combat_prove.sh — PROVE B4: an AI player attacks a real NPC monster via the external
# socket flow, verified by (a) server->client combat packets (ATTACK 0x05) after our
# Action(0x04)+AttackRequest(0x0A) and (b) the player's `exp` increasing in the `gameserver` DB.
# Requires CombatProbe compiled (mvn clean compile -f AIPlayerEngine/pom.xml).
#
# Key facts baked in (see Documentation/Audit/35-b4-live-npc-combat.md):
#   * The ai_* chars were created at (16600,17000,434) — NOT on a real map (void; player dies,
#     curHp=0). We reposition + heal CombatBot_01 to the Talking Island Wolf monster zone.
#   * The client MUST send EnterWorld(0x03) after CharSelected to spawn (CombatProbe does).
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
ACCOUNT="${1:-ai_combat_01}"
CHAR="${2:-CombatBot_01}"
PASS="${3:-${AI_ACCOUNT_PASSWORD:-}}"
WX="${4:--82759}"
WY="${5:-250149}"
WZ="${6:--3600}"
OUT=/tmp/b4_prove_out.txt

cd "$ENGINE"

# 0) Position + heal the bot at the monster zone (idempotent): copy full HP/MP so it spawns alive.
sudo mysql -u root gameserver -e "UPDATE characters SET x=${WX}, y=${WY}, z=${WZ}, curHp=maxHp, curMp=maxMp WHERE char_name='${CHAR}';" 2>/dev/null

echo "[b4] >>> BEFORE: $CHAR"
BEFORE=$(sudo mysql -u root gameserver -e "SELECT exp,sp,level,online FROM characters WHERE char_name='${CHAR}';" 2>/dev/null | tail -1)
echo "[b4]     exp,sp,level,online = $BEFORE"
EXP_BEFORE=$(echo "$BEFORE" | awk '{print $1}')

# Restart LoginServer to clear the in-memory "account in use" map (see Audit/33).
LSPID=$(pgrep -f 'java .*-jar ../libs/LoginServer.jar' | head -1)
if [ -n "$LSPID" ]; then
    echo "[b4] Restarting LoginServer (pid $LSPID) to clear stale auth state..."
    kill -9 "$LSPID"
    ready=0
    for i in $(seq 1 40); do
        sleep 1
        if ss -tlnp 2>/dev/null | grep -q ':2106 '; then ready=1; break; fi
    done
    if [ "$ready" -ne 1 ]; then echo "[FAIL] LoginServer not back on :2106"; exit 2; fi
    sleep 8
    echo "[b4] LoginServer ready (:2106 LISTEN + 8s settle)."
else
    echo "[b4] No LoginServer process found; external management (sleep 5)."; sleep 5
fi

echo "[b4] Launching CombatProbe ($ACCOUNT) at ($WX,$WY,$WZ)..."
nohup timeout 90 java -cp target/classes com.aiplayer.examples.CombatProbe \
    "$ACCOUNT" "$PASS" 127.0.0.1 7777 "$WX" "$WY" "$WZ" > "$OUT" 2>&1 < /dev/null &
PROBE=$!
wait "$PROBE" 2>/dev/null

echo "[b4] >>> CombatProbe stdout (combat-relevant lines):"
grep -E 'CHAR SELECTED|TARGET acquired|sent Action|sent AttackRequest|NPC_INFO .*attackable=1|COMBAT pkt|COMBAT TALLY|ATTACK\\(0x05\\)|DIE\\(0x06\\)|SYSTEM_MESSAGE|COMBAT PROVEN|FAIL|WARN' "$OUT" | head -40

echo "[b4] >>> AFTER: $CHAR"
AFTER=$(sudo mysql -u root gameserver -e "SELECT exp,sp,level,online FROM characters WHERE char_name='${CHAR}';" 2>/dev/null | tail -1)
echo "[b4]     exp,sp,level,online = $AFTER"
EXP_AFTER=$(echo "$AFTER" | awk '{print $1}')

COMBAT_PROVEN=$(grep -c 'COMBAT PROVEN.*= true' "$OUT" 2>/dev/null || true)
echo "[b4] probe combatProven flag = ${COMBAT_PROVEN:-0}; exp before=${EXP_BEFORE:-?} after=${EXP_AFTER:-?}"

if [ "${COMBAT_PROVEN:-0}" -ge 1 ] || [ "${EXP_AFTER:-0}" -gt "${EXP_BEFORE:-0}" ] 2>/dev/null; then
    echo "[OK] B4 PROVEN: combat packets AND/OR exp increase (${EXP_BEFORE:-?}->${EXP_AFTER:-?})."
    exit 0
else
    echo "[FAIL] B4 not proven: no combat packets and exp unchanged (${EXP_BEFORE:-?}->${EXP_AFTER:-?})."
    echo "      Full probe output: $OUT"; exit 2
fi
