#!/bin/bash
# b4_combat_prove.sh — PROVE B4: an AI player attacks a real NPC monster via the external
# socket flow, verified by (a) server->client combat packets after our Action(0x04) and
# (b) the player's `exp` increasing in the `gameserver` DB. Requires CombatProbe compiled.
#
# Mirrors b3_enter_world_prove.sh's LoginServer-restart (clears the "account in use" map
# left by a prior PlayOk). Spec: Documentation/Audit/35-b4-live-npc-combat.md
set -u
ENGINE=/home/volodro/L2JM/AIPlayerEngine
ACCOUNT="${1:-ai_combat_01}"
CHAR="${2:-CombatBot_01}"
PASS="${3:-ai123pass}"
OUT=/tmp/b4_prove_out.txt

cd "$ENGINE"

# Baseline: player exp/sp/online BEFORE (should be exp=0, online=0 for a fresh CombatBot_01).
echo "[b4] >>> BEFORE: $CHAR"
BEFORE=$(sudo mysql -u root gameserver -e "SELECT exp,sp,level,online FROM characters WHERE char_name='${CHAR}';" 2>/dev/null | tail -1)
echo "[b4]     exp,sp,level,online = $BEFORE"
EXP_BEFORE=$(echo "$BEFORE" | awk '{print $1}')

# Restart LoginServer to clear the in-memory "account in use" map (see Audit/33). Poll the LS log
# for a NEW readiness line (after the kill) instead of a blind sleep, to avoid the transient
# "No GGAuth reply" race seen when the probe hits the LS before it finishes loading.
LSLOG="/home/volodro/L2JM/ServerBuild/login/log/stdout.log"
LSPID=$(pgrep -f 'java .*-jar ../libs/LoginServer.jar' | head -1)
if [ -n "$LSPID" ]; then
    # Baseline: number of readiness lines already in the log (so we detect a NEW one post-restart).
    before_lines=$(grep -c 'Login client listener started' "$LSLOG" 2>/dev/null)
    before_lines=${before_lines:-0}
    echo "[b4] Restarting LoginServer (pid $LSPID) to clear stale auth state..."
    kill -9 "$LSPID"
    # Wait for the watchdog (LoginServerTask.sh) to relaunch it + a NEW readiness line.
    ready=0
    for i in $(seq 1 40); do
        sleep 1
        now_lines=$(grep -c 'Login client listener started' "$LSLOG" 2>/dev/null)
        now_lines=${now_lines:-0}
        if [ "$now_lines" -gt "$before_lines" ] && ss -tlnp 2>/dev/null | grep -q ':2106 '; then
            ready=1
            break
        fi
    done
    if [ "$ready" -ne 1 ]; then
        echo "[FAIL] LoginServer not ready after restart (no new 'listener started' line)"
        exit 2
    fi
    # Extra settle time for the GS<->LS re-registration ("Updated Gameserver").
    sleep 4
    echo "[b4] LoginServer ready (new listener started + :2106 LISTEN + settle)."
else
    echo "[b4] No LoginServer process found to restart; assuming external management."
    sleep 2
fi

echo "[b4] Launching CombatProbe ($ACCOUNT)..."
nohup timeout 70 java -cp target/classes com.aiplayer.examples.CombatProbe \
    "$ACCOUNT" "$PASS" 127.0.0.1 7777 > "$OUT" 2>&1 < /dev/null &
PROBE=$!

wait "$PROBE" 2>/dev/null

echo "[b4] >>> CombatProbe stdout (combat-relevant lines):"
grep -E 'CHAR SELECTED|TARGET acquired|sent Action|sent AttackRequest|NPC_INFO|COMBAT pkt|COMBAT TALLY|ATTACK\(0x05\)|DIE\(0x06\)|STATUS_UPDATE|COMBAT PROVEN|FAIL|WARN' "$OUT" | head -60

echo "[b4] >>> AFTER: $CHAR"
AFTER=$(sudo mysql -u root gameserver -e "SELECT exp,sp,level,online FROM characters WHERE char_name='${CHAR}';" 2>/dev/null | tail -1)
echo "[b4]     exp,sp,level,online = $AFTER"
EXP_AFTER=$(echo "$AFTER" | awk '{print $1}')

COMBAT_PROVEN=$(grep -c 'COMBAT PROVEN.*= true' "$OUT" 2>/dev/null || true)
echo "[b4] probe combatProven flag = ${COMBAT_PROVEN:-0}; exp before=${EXP_BEFORE:-?} after=${EXP_AFTER:-?}"

if [ "${COMBAT_PROVEN:-0}" -ge 1 ] || [ "${EXP_AFTER:-0}" -gt "${EXP_BEFORE:-0}" ] 2>/dev/null; then
    echo "[OK] B4 PROVEN: combat packets observed AND/OR exp increased (${EXP_BEFORE:-?}->${EXP_AFTER:-?})."
    exit 0
else
    echo "[FAIL] B4 not proven: no combat packets and exp unchanged (${EXP_BEFORE:-?}->${EXP_AFTER:-?})."
    echo "      Full probe output: $OUT"
    exit 2
fi
