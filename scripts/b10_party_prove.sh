#!/usr/bin/env bash
# B10 party-proof runner: PartyProbe forms a party CombatBot_01(leader)->CombatBot_02(joiner);
# assert the joiner B got PARTY_SMALL_WINDOW_ALL(0x4E) and/or leader A got PARTY_SMALL_WINDOW_ADD(0x4F).
# Pre: both bots co-located (see the DB UPDATE inside), GS+LS running.
set -uo pipefail
ENGINE=/home/volodro/L2JM/AIPlayerEngine
# Ensure co-location (party invite requires target.isVisibleFor(requestor)).
sudo mysql -u root gameserver -e "UPDATE characters SET x=-82515,y=241221,z=-3728,online=0 WHERE char_name IN ('CombatBot_01','CombatBot_02');" 2>/dev/null

cd "$ENGINE" \
  && nohup timeout 45 bash -c 'java -cp target/classes com.aiplayer.examples.PartyProbe ai_combat_01 ai123pass ai_combat_02 ai123pass 127.0.0.1 7777 CombatBot_02' > /tmp/party_probe.out 2>&1 &
sleep 28

echo "--- PartyProbe summary ---"
grep -E 'RequestJoinParty|ASK_JOIN_PARTY|SMALL_WINDOW|PARTY PROVEN' /tmp/party_probe.out

if grep -q 'PARTY PROVEN (server created a real party, windows pushed) = true' /tmp/party_probe.out; then
  echo "B10: PARTY PROVEN (server created a real party)"
else
  echo "B10: NOT PROVEN !"
  exit 1
fi
