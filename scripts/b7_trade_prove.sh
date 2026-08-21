#!/usr/bin/env bash
# B7 trade-proof runner: TradeProbe has CombatBot_01 buy item 118 from Silvia (30003) via the genuine
# merchant flow (2 clicks -> HTML -> npc_<objId>_Buy bypass -> BuyList -> RequestBuyItem).
# Assert adena(57) decreased by the item price AND a new item-118 row was added. Idempotent.
set -uo pipefail
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
CHARID=2

ADENA_BEFORE=$(sudo mysql -u root gameserver -N -e "SELECT count FROM items WHERE owner_id=$CHARID AND item_id=57;" 2>/dev/null || echo 0)
ITEMS_BEFORE=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM items WHERE owner_id=$CHARID AND item_id=118;" 2>/dev/null || echo 0)
echo "BEFORE adena=$ADENA_BEFORE items118=$ITEMS_BEFORE"

# Position CombatBot_01 at Silvia (within Npc.INTERACTION_DISTANCE=250).
sudo mysql -u root gameserver -e "UPDATE characters SET x=-83789,y=240799,z=-3717,online=0 WHERE char_name='CombatBot_01';" 2>/dev/null
(cd "$ENGINE" && nohup timeout 60 bash -c 'java -cp target/classes com.aiplayer.examples.TradeProbe ai_combat_01 '"${AI_ACCOUNT_PASSWORD:-}"' 127.0.0.1 7777' > /tmp/trade_probe.out 2>&1 &)
sleep 26

ADENA_AFTER=$(sudo mysql -u root gameserver -N -e "SELECT count FROM items WHERE owner_id=$CHARID AND item_id=57;" 2>/dev/null || echo 0)
ITEMS_AFTER=$(sudo mysql -u root gameserver -N -e "SELECT COUNT(*) FROM items WHERE owner_id=$CHARID AND item_id=118;" 2>/dev/null || echo 0)
echo "AFTER adena=$ADENA_AFTER items118=$ITEMS_AFTER"
grep -E 'Buy bypass|BuyList|RequestBuyItem' /tmp/trade_probe.out | tail -4

if [ "$ADENA_AFTER" -lt "$ADENA_BEFORE" ] && [ "$ITEMS_AFTER" -gt "$ITEMS_BEFORE" ]; then
  echo "B7: TRADE PROVEN (adena decreased + new item row)"
else
  echo "B7: NOT PROVEN !"
  exit 1
fi
