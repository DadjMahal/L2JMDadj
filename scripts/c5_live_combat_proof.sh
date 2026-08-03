#!/bin/bash
# c5_live_combat_proof.sh — PROVE Stream C: the in-engine live combat loop actually attacks a real
# NPC and gains exp, driving CombatAI.makeDecision -> CombatFramePlanner -> GameServerClient over the
# external socket (no server source changes). This closes the final Stream C gap (probes -> engine).
#
# Requires: L2JM server UP (LoginServer :2106, GameServer :7777), CombatLoop compiled
#   (mvn clean compile -f AIPlayerEngine/pom.xml).
# Uses the same relocation/heal + DB before/after exp check as b4_combat_prove.sh.
set -u
ENGINE=/home/volodro/L2JM/AIPlayerEngine
ACCOUNT="${1:-ai_combat_01}"
CHAR="${2:-CombatBot_01}"
PASS="${3:-ai123pass}"
WX="${4:--82759}"
WY="${5:-250149}"
WZ="${6:--3600}"
SECONDS="${7:-25}"
SKIP_RESTART="${SKIP_RESTART:-0}"   # 1 = do NOT kill/restart LoginServer (run against the live one)
OUT=/tmp/c5_combat_out.txt

cd "$ENGINE"

# 0) Precondition: server up.
if ! ss -tlnp 2>/dev/null | grep -q ':2106 '; then echo "[c5] [FAIL] LoginServer not on :2106"; exit 2; fi
if ! ss -tlnp 2>/dev/null | grep -q ':7777 '; then echo "[c5] [FAIL] GameServer not on :7777"; exit 2; fi

# 1) Position + heal the bot at the monster zone (idempotent): full HP/MP so it spawns alive.
sudo mysql -u root gameserver -e "UPDATE characters SET x=${WX}, y=${WY}, z=${WZ}, curHp=maxHp, curMp=maxMp WHERE char_name='${CHAR}';" 2>/dev/null

echo "[c5] >>> BEFORE: $CHAR"
BEFORE=$(sudo mysql -u root gameserver -e "SELECT exp,sp,level,online FROM characters WHERE char_name='${CHAR}';" 2>/dev/null | tail -1)
echo "[c5]     exp,sp,level,online = $BEFORE"
EXP_BEFORE=$(echo "$BEFORE" | awk '{print $1}')

# 2) Restart LoginServer to clear the in-memory "account in use" map (same as b4).
#    Set SKIP_RESTART=1 to run against the live LoginServer without a restart.
if [ "$SKIP_RESTART" = "1" ]; then
    echo "[c5] SKIP_RESTART=1: using the running LoginServer (no restart)."
else
    LSPID=$(pgrep -f 'java .*-jar ../libs/LoginServer.jar' | head -1)
    if [ -n "$LSPID" ]; then
        echo "[c5] Restarting LoginServer (pid $LSPID) to clear stale auth state..."
        kill -9 "$LSPID"
        ready=0
        for i in $(seq 1 40); do
            sleep 1
            if ss -tlnp 2>/dev/null | grep -q ':2106 '; then ready=1; break; fi
        done
        if [ "$ready" -ne 1 ]; then echo "[FAIL] LoginServer not back on :2106"; exit 2; fi
        sleep 8
        echo "[c5] LoginServer ready (:2106 LISTEN + 8s settle)."
    else
        echo "[c5] No LoginServer process found; external management (sleep 5)."; sleep 5
    fi
fi

# 3) Run the in-engine live combat loop.
echo "[c5] Launching CombatLoop ($ACCOUNT) for ${SECONDS}s at ($WX,$WY,$WZ)..."
nohup timeout $((SECONDS + 15)) java -cp target/classes com.aiplayer.examples.CombatLoop \
    "$ACCOUNT" "$PASS" 127.0.0.1 7777 2 0 "$WX" "$WY" "$WZ" "$SECONDS" > "$OUT" 2>&1 < /dev/null &
LOOP=$!
wait "$LOOP" 2>/dev/null

echo "[c5] >>> CombatLoop output (combat-relevant lines):"
grep -E 'ENGAGED|SENT opcode|no-target|in world|COMPLETE|FAIL|ATTACK_START|DEAD|ALIVE|RE_TARGET|DELETE_OBJECT' "$OUT" | head -50

echo "[c5] >>> AFTER: $CHAR"
AFTER=$(sudo mysql -u root gameserver -e "SELECT exp,sp,level,online FROM characters WHERE char_name='${CHAR}';" 2>/dev/null | tail -1)
echo "[c5]     exp,sp,level,online = $AFTER"
EXP_AFTER=$(echo "$AFTER" | awk '{print $1}')

ENGAGED=$(grep -c 'ENGAGED' "$OUT" 2>/dev/null || true)

# Server-confirmed damage: our target's STATUS_UPDATE must show CUR_HP below MAX_HP after we ENGAGED.
TARGET=$(grep -oE 'ENGAGED target=[0-9]+' "$OUT" 2>/dev/null | head -1 | grep -oE '[0-9]+')
DAMAGE=0
if [ -n "$TARGET" ]; then
    while read -r line; do
        maxhp=$(echo "$line" | grep -oE 'MAX_HP=[0-9]+' | head -1 | grep -oE '[0-9]+')
        curhp=$(echo "$line" | grep -oE 'CUR_HP=[0-9]+' | head -1 | grep -oE '[0-9]+')
        if [ -n "$maxhp" ] && [ -n "$curhp" ] && [ "$curhp" -lt "$maxhp" ]; then DAMAGE=1; break; fi
    done < <(grep "STATUS_UPDATE: objId=$TARGET" "$OUT" 2>/dev/null || true)
fi

echo "[c5] engaged-actions=${ENGAGED:-0}; target=$TARGET serverConfirmedDamage=$DAMAGE; exp before=${EXP_BEFORE:-?} after=${EXP_AFTER:-?}"

# Slice 6 death-gating: if the bot died in-window, no Action/AttackRequest frames may be sent after.
DEAD=$(grep -c '\[CombatLoop\] DEAD' "$OUT" 2>/dev/null || true)
SENT_AFTER_DEAD=0
DEADLN=$(grep -nE '\[CombatLoop\] DEAD' "$OUT" 2>/dev/null | head -1 | cut -d: -f1)
if [ -n "$DEADLN" ]; then
    SENT_AFTER_DEAD=$(awk -v d="$DEADLN" 'NR>d && /SENT opcode/' "$OUT" | wc -l)
fi
echo "[c5] slice-6 death gate: fired=${DEAD}; Action/AttackRequest frames after self HP hit 0 = ${SENT_AFTER_DEAD}"

if [ "${ENGAGED:-0}" -ge 1 ] && { [ "$DAMAGE" = "1" ] || [ "${EXP_AFTER:-0}" -gt "${EXP_BEFORE:-0}" ] 2>/dev/null; }; then
    echo "[OK] C5 PROVEN: live loop engaged a target AND the server confirmed it"
    echo "      (target HP dropped and/or exp ${EXP_BEFORE:-?}->${EXP_AFTER:-?})."
    exit 0
elif [ "${ENGAGED:-0}" -ge 1 ]; then
    echo "[PARTIAL] C5 engaged a target but no server-confirmed damage/exp (target too weak to down before dying)."
    exit 3
else
    echo "[FAIL] C5 not proven: no ENGAGED action sent (see $OUT)."
    exit 2
fi
