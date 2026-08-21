#!/bin/bash
# b3_login_probe.sh — run the live LoginServer auth probe (Phase 1 proof) and assert success.
# Usage: ./scripts/b3_login_probe.sh [account] [password] [charId]
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
ENGINE=/home/dadj/Projects/l24lude/AIPlayerEngine
ACCOUNT="${1:-ai_combat_01}"
PASS="${2:-${AI_ACCOUNT_PASSWORD:-}}"
CHAR="${3:-2}"

cd "$ENGINE"
OUT=$(timeout 60 java -cp target/classes com.aiplayer.examples.LoginProbe "$ACCOUNT" "$PASS" "$CHAR" 2>&1)
echo "$OUT" | grep -iE 'LoginOk|ServerList|PlayOk|RESULT|LoginFail|EXCEPTION'
RC=$?
if echo "$OUT" | grep -q 'RESULT connectAndLogin=true'; then
  echo "[OK] B3 Phase 1 login auth PASS for $ACCOUNT"
  exit 0
else
  echo "[FAIL] B3 Phase 1 login auth did not complete for $ACCOUNT"
  exit 2
fi