#!/usr/bin/env bash
# B9 chat-proof runner: ChatProbe whispers a token from CombatBot_01 -> CombatBot_02;
# assert the receiver B connection observed a CREATURE_SAY(0x4A) containing the token.
set -uo pipefail
ENGINE=/home/volodro/L2JM/AIPlayerEngine

cd "$ENGINE" \
  && nohup timeout 45 bash -c 'java -cp target/classes com.aiplayer.examples.ChatProbe ai_combat_01 ai123pass ai_combat_02 ai123pass 127.0.0.1 7777 CombatBot_02' > /tmp/chat_probe.out 2>&1 &
sleep 32

echo "--- ChatProbe summary ---"
grep -E 'token=|whisper to|with token|received the whisper|own echo|CHAT PROVEN' /tmp/chat_probe.out

if grep -q 'B received the whisper token = true' /tmp/chat_probe.out; then
  echo "B9: CHAT PROVEN (B received A's whisper)"
else
  echo "B9: NOT PROVEN !"
  exit 1
fi
