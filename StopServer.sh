#!/bin/bash
# ~/L2JM/StopServer.sh - Stop LoginServer and GameServer

L2JM_HOME="$HOME/L2JM"
LBREAK="============================================================"
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
pass() { echo -e "  [ ${GREEN}OK${NC} ] $1"; }
fail() { echo -e "  [ ${RED}FAIL${NC} ] $1"; }
info() { echo -e "  [ ${CYAN}..${NC} ] $1"; }
warn() { echo -e "  [ ${YELLOW}!!${NC} ] $1"; }

proc_running() { pgrep -f "$1" >/dev/null 2>&1; }

FORCE=false
[ "${1:-}" = "--force" ] && FORCE=true

KILL_CMD="pkill"
$FORCE && KILL_CMD="pkill -9"

echo ""; echo "  $LBREAK"; echo "  L2JM Server Shutdown"; echo "  $LBREAK"

info "Stopping GameServer watchdog..."
pkill -f "GameServerTask.sh" 2>/dev/null || true; sleep 1

info "Stopping GameServer..."
if proc_running "GameServer\\.jar"; then
	$KILL_CMD -f "GameServer.jar" 2>/dev/null || true
	sleep 3
	if ! $FORCE; then
		waited=0
		while [ $waited -lt 15 ] && proc_running "GameServer\\.jar"; do
			sleep 1; waited=$((waited + 1))
		done
	fi
	if proc_running "GameServer\\.jar"; then
		warn "GameServer still running, using SIGKILL..."
		pkill -9 -f "GameServer.jar" 2>/dev/null || true
		sleep 1
	fi
fi

info "Stopping LoginServer watchdog..."
pkill -f "LoginServerTask.sh" 2>/dev/null || true; sleep 1

info "Stopping LoginServer..."
if proc_running "LoginServer\\.jar"; then
	$KILL_CMD -f "LoginServer.jar" 2>/dev/null || true
	sleep 3
	if ! $FORCE; then
		waited=0
		while [ $waited -lt 15 ] && proc_running "LoginServer\\.jar"; do
			sleep 1; waited=$((waited + 1))
		done
	fi
	if proc_running "LoginServer\\.jar"; then
		warn "LoginServer still running, using SIGKILL..."
		pkill -9 -f "LoginServer.jar" 2>/dev/null || true
		sleep 1
	fi
fi

info "Cleaning up remaining watchdog scripts..."
pkill -f "GameServerTask.sh" 2>/dev/null || true
pkill -f "LoginServerTask.sh" 2>/dev/null || true
sleep 1

echo ""; echo "  $LBREAK"; echo "  Verification"; echo "  $LBREAK"
all_ok=true
if proc_running "GameServer\\.jar"; then
	fail "GameServer is still running."; all_ok=false
else
	pass "GameServer stopped."
fi
if proc_running "LoginServer\\.jar"; then
	fail "LoginServer is still running."; all_ok=false
else
	pass "LoginServer stopped."
fi
if proc_running "GameServerTask\\.sh"; then
	fail "GameServer watchdog is still running."; all_ok=false
else
	pass "GameServer watchdog stopped."
fi
if proc_running "LoginServerTask\\.sh"; then
	fail "LoginServer watchdog is still running."; all_ok=false
else
	pass "LoginServer watchdog stopped."
fi
echo "  $LBREAK"
if $all_ok; then
	echo -e "  ${GREEN}All servers stopped successfully.${NC}"
	exit 0
else
	echo -e "  ${RED}Some servers could not be stopped. Use --force to force kill.${NC}"
	exit 1
fi
