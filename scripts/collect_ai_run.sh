#!/bin/bash
# collect_ai_run.sh — sample all AI players every 60s for a 2-3h run into samples.tsv.
# Usage: collect_ai_run.sh <minutes> <outdir>
set -u
MIN=${1:-170}
OUT=${2:-/home/volodro/L2JM/AIStatusLogs/multiplayer_run}
mkdir -p "$OUT"
TSV="$OUT/samples.tsv"
# DB rows for our 12 roster accounts
ACCTS="('ai_combat_01','ai_combat_02','ai_combat_03','ai_combat_04','ai_combat_05','ai_combat_06','ai_explorer_01','ai_quest_01','ai_quest_02','ai_merchant_01','ai_merchant_02','ai_social_01')"
# header once
echo -e "ts\taccount\tonline\tlevel\texp\tx\ty\tz\tcurHp\tmaxHp" > "$TSV"
echo "collector: writing to $TSV for $MIN min"
ITER=$((MIN))
i=0
while [ $i -lt $ITER ]; do
    TS=$(date +%s)
    sudo mysql -u root gameserver -N -e "SELECT '$TS',account_name,online,level,exp,x,y,z,COALESCE(curHp,0),COALESCE(maxHp,0) FROM characters WHERE account_name IN $ACCTS ORDER BY charId;" 2>/dev/null >> "$TSV"
    i=$((i+1))
    [ $i -lt $ITER ] && sleep 60
done
echo "collector done: $TSV"