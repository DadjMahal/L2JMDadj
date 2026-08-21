#!/bin/bash
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
# c7_live_quest_proof.sh — PROVE C7/B6b: quest STARTED via a genuine NPC dialog bypass.
#
# HONEST AUDIT (2026-08-04): the previous C7 "proof" was a FALSE POSITIVE — it sent a cold
# RequestBypassToServer for Q00255_Tutorial and printed QUEST_COMPLETED whenever
# activeQuests==0 (always true, since the tutorial quest is Ex-flagged and excluded from
# QuestList). No character_quests row was ever written by the server.
#
# Correct flow, verified against the L2JMobius source:
#   - RequestBypassToServer only accepts a bypass string that was previously SHOWN to the
#     player in an NpcHtmlMessage from that NPC, within Npc.INTERACTION_DISTANCE (250).
#   - Q00255_Tutorial auto-starts on enter-world (B6). It has NO onTalk/addStartNpc, so
#     there is nothing to bypass.
#   - Q00101_SwordOfSolidarity IS a genuine talk-start quest, given by Roien (NPC 30008):
#       Action(0x04) on Roien -> default html -> "Script" -> quest window ->
#       "Script Q00101_SwordOfSolidarity" -> 30008-02a.htm -> 30008-02b.htm ->
#       30008-03.htm (startQuest + gives item 796). Requires Human + level >= 9.
#   - QuestFlowLoop reads every bypass from the html the server ACTUALLY shows, so each one
#     is validated. The proof asserts a NEW character_quests row for Q00101 (source of truth).
set -u
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
ACCOUNT=${1:-ai_combat_01}
CHARID=${2:-2}
PASS=${3:-${AI_ACCOUNT_PASSWORD:-}}
OUT=/tmp/c7_quest_out.txt
# Roien (NPC 30008) spawn at Talking Island
ROIEN_X=-71384; ROIEN_Y=258304; ROIEN_Z=-3104
NPC_ID=30008
QUEST=Q00101_SwordOfSolidarity
START_EVENT=30008-03.htm

cd "$ENGINE"

echo "[C7] === GENUINE NPC-talk quest proof: $QUEST via NPC $NPC_ID (Roien) ==="
echo "[C7] Q00101 requires Human + level>=9; fixture sets level=9, heals, and positions at Roien."

# Health check
ss -tlnp 2>/dev/null | grep -q ':2106 ' && ss -tlnp 2>/dev/null | grep -q ':7777 ' || { echo "[FAIL] Servers not listening"; exit 2; }

# Clean slate: remove any previous Q00101 state for this char
sudo mysql -u root gameserver -e "DELETE FROM character_quests WHERE charId=$CHARID AND name='$QUEST';" 2>/dev/null
sudo mysql -u root gameserver -e "DELETE FROM items WHERE owner_id=$CHARID AND item_id=796;" 2>/dev/null

# Fixture: level>=9, full heal, position exactly at Roien (alive, offline to avoid kick)
sudo mysql -u root gameserver -e "UPDATE characters SET x=$ROIEN_X, y=$ROIEN_Y, z=$ROIEN_Z, level=9, exp=0, curHp=maxHp, curMp=maxMp, online=0 WHERE charId=$CHARID;" 2>/dev/null

ROWS_BEFORE=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM character_quests WHERE charId=$CHARID AND name='$QUEST';" 2>/dev/null || echo 0)
echo "[C7] character_quests rows BEFORE = $ROWS_BEFORE"

echo "[C7] Launching QuestFlowLoop (dialogue-driven, validated bypasses only)..."
timeout 60 java -cp target/classes com.aiplayer.examples.QuestFlowLoop \
    "$ACCOUNT" "$PASS" 127.0.0.1 7777 "$CHARID" 0 "$ROIEN_X" "$ROIEN_Y" "$ROIEN_Z" 40 \
    "$NPC_ID" "$QUEST" "$START_EVENT" > "$OUT" 2>&1 < /dev/null
RC=$?
echo "[C7] QuestFlowLoop exit code = $RC"

echo "[C7] >>> QuestFlowLoop dialogue trace:"
grep -E 'SENT opcode=0x04|SENT opcode=0x21|NPC_HTML links|START_EVENT_SENT|QUEST FLOW COMPLETE|waiting for NPC' "$OUT" | head -40
echo "[C7] >>> html excerpts (validate server-side dialog):"
grep -E 'html excerpt' "$OUT" | head -10

# Source of truth: DB — did the server actually accept the quest?
sleep 2
echo "[C7] >>> character_quests for charId=$CHARID:"
sudo mysql -u root gameserver -e "SELECT charId,name,var,value FROM character_quests WHERE charId=$CHARID;" 2>/dev/null
ROWS_AFTER=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM character_quests WHERE charId=$CHARID AND name='$QUEST';" 2>/dev/null || echo 0)
ITEM_COUNT=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM items WHERE owner_id=$CHARID AND item_id=796;" 2>/dev/null || echo 0)
echo "[C7] Q00101 rows BEFORE=$ROWS_BEFORE AFTER=$ROWS_AFTER ; item 796 (Roien's Letter) count=$ITEM_COUNT"

if [ "${ROWS_AFTER:-0}" -gt "${ROWS_BEFORE:-0}" ]; then
    echo "[OK] C7 PROVEN: server wrote a Q00101_SwordOfSolidarity quest state after the NPC-dialog bypass chain."
    if [ "${ITEM_COUNT:-0}" -ge 1 ]; then
        echo "[OK] Bonus: Roien's Letter (item 796) was granted to the bot by startQuest()."
    fi
    exit 0
else
    echo "[FAIL] C7 not proven: no new character_quests row. See $OUT"
    tail -40 "$OUT"
    exit 1
fi
