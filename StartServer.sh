#!/bin/bash
# ~/L2JM/StartServer.sh - Start LoginServer and GameServer

L2JM_HOME="$HOME/L2JM"
LBREAK="============================================================"
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
pass() { echo -e "  [ ${GREEN}OK${NC} ] $1"; }
fail() { echo -e "  [ ${RED}FAIL${NC} ] $1"; }
info() { echo -e "  [ ${CYAN}..${NC} ] $1"; }
warn() { echo -e "  [ ${YELLOW}!!${NC} ] $1"; }

SCRIPT_SRC="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
cd "$SCRIPT_SRC"
SERVERBUILD="$L2JM_HOME/ServerBuild"
LOGIN_DIR="$SERVERBUILD/login"
GAME_DIR="$SERVERBUILD/game"

if [ ! -f "$LOGIN_DIR/LoginServer.sh" ] || [ ! -f "$GAME_DIR/GameServer.sh" ]; then
	echo -e "${RED}Error: ServerBuild not found or incomplete.${NC}"
	exit 1
fi

proc_running() { pgrep -f "$1" >/dev/null 2>&1; }

wait_for_port() {
	local port="$1" waited=0
	while [ $waited -lt 60 ]; do
		if ss -tlnp "sport = :$port" 2>/dev/null | grep -q ":$port "; then return 0; fi
		sleep 1; waited=$((waited + 1))
	done
	return 1
}

wait_for_log() {
	local logfile="$1" pattern="$2" waited=0
	while [ $waited -lt 120 ]; do
		if [ -f "$logfile" ] && grep -q "$pattern" "$logfile" 2>/dev/null; then return 0; fi
		sleep 1; waited=$((waited + 1))
	done
	return 1
}

wait_for_proc() {
	local pattern="$1" waited=0
	while [ $waited -lt 20 ]; do
		if pgrep -f "$pattern" >/dev/null 2>&1; then return 0; fi
		sleep 1; waited=$((waited + 1))
	done
	return 1
}

stop_all() {
	echo ""; echo "  $LBREAK"; echo "  Stopping running server processes..."; echo "  $LBREAK"
	pkill -f "GameServerTask.sh" 2>/dev/null || true
	pkill -f "LoginServerTask.sh" 2>/dev/null || true
	sleep 1
	pkill -f "GameServer.jar" 2>/dev/null || true; sleep 2
	pkill -f "LoginServer.jar" 2>/dev/null || true; sleep 2
	if proc_running "GameServer.jar" || proc_running "LoginServer.jar"; then
		warn "Some processes still running, force killing..."
		pkill -9 -f "GameServer.jar" 2>/dev/null || true
		pkill -9 -f "LoginServer.jar" 2>/dev/null || true
		pkill -9 -f "GameServerTask.sh" 2>/dev/null || true
		pkill -9 -f "LoginServerTask.sh" 2>/dev/null || true
		sleep 1
	fi
	if ! proc_running "GameServer.jar" && ! proc_running "LoginServer.jar"; then
		pass "All server processes stopped."; return 0
	else
		fail "Some processes could not be terminated."; return 1
	fi
}

check_status() {
	echo ""; echo "  $LBREAK"; echo "  Server Status Overview"; echo "  $LBREAK"
	local all_ok=true
	if systemctl is-active --quiet mariadb 2>/dev/null || systemctl is-active --quiet mysql 2>/dev/null; then
		pass "MariaDB/MySQL is running."
	else
		warn "MariaDB/MySQL is not running."; all_ok=false
	fi
	if proc_running "LoginServer\\.jar"; then
		pass "LoginServer process is running."
		if ss -tlnp "sport = :2106" 2>/dev/null | grep -q ":2106 "; then pass "Port 2106 (client) is listening."
		else fail "Port 2106 (client) is NOT listening."; all_ok=false; fi
		if ss -tlnp "sport = :9014" 2>/dev/null | grep -q ":9014 "; then pass "Port 9014 (GS listener) is listening."
		else fail "Port 9014 (GS listener) is NOT listening."; all_ok=false; fi
	else
		fail "LoginServer is NOT running."; all_ok=false
	fi
	if proc_running "GameServer\\.jar"; then
		pass "GameServer process is running."
		if ss -tlnp "sport = :7777" 2>/dev/null | grep -q ":7777 "; then pass "Port 7777 (client) is listening."
		else fail "Port 7777 (client) is NOT listening."; all_ok=false; fi
		if [ -f "$GAME_DIR/log/stdout.log" ] && grep -q "Registered on login" "$GAME_DIR/log/stdout.log" 2>/dev/null; then
			local regline=$(grep "Registered on login" "$GAME_DIR/log/stdout.log" | tail -1)
			pass "GameServer registration: $regline"
		else fail "GameServer is NOT registered on LoginServer."; all_ok=false; fi
		if [ -f "$GAME_DIR/log/stdout.log" ] && grep -q "Server loaded" "$GAME_DIR/log/stdout.log" 2>/dev/null; then
			pass "GameServer loaded successfully."
		else warn "Could not confirm GameServer load completion."; fi
	else
		fail "GameServer is NOT running."; all_ok=false
	fi
	echo "  $LBREAK"
	if $all_ok; then echo -e "  ${GREEN}All servers are operational.${NC}"; return 0
	else echo -e "  ${RED}Some checks failed.${NC}"; return 1; fi
}

if [ "${1:-}" = "--status" ]; then check_status; exit $?; fi
if [ "${1:-}" = "--restart" ]; then stop_all || true
elif proc_running "GameServer\\.jar" || proc_running "LoginServer\\.jar"; then
	echo -e "${RED}Error: Servers are already running. Use --restart to restart or --status to check status.${NC}"
	exit 1
fi

echo ""; echo "  $LBREAK"; echo "  L2JM Server Startup"; echo "  $LBREAK"
info "Checking MariaDB/MySQL..."
if systemctl is-active --quiet mariadb 2>/dev/null; then pass "MariaDB is already running."
elif systemctl is-active --quiet mysql 2>/dev/null; then pass "MySQL is already running."
else
	info "Starting MariaDB..."
	if systemctl start mariadb 2>/dev/null || systemctl start mysql 2>/dev/null; then
		sleep 2; pass "MariaDB started."
	else fail "Could not start MariaDB."; exit 1; fi
fi
info "Clearing Linux cache..."
sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true
pass "Cache cleared."

echo ""; info "Starting LoginServer..."
cd "$LOGIN_DIR"; ./LoginServer.sh
sleep 2
if wait_for_proc "LoginServer\\.jar"; then
	PID_LOGIN=$(pgrep -f "LoginServer\\.jar" | head -1)
	pass "LoginServer started (PID $PID_LOGIN)."
else
	fail "LoginServer failed to start."; exit 1
fi

info "Waiting for LoginServer to be ready..."
if wait_for_port 2106; then pass "LoginServer client port 2106 is listening."
else fail "LoginServer port 2106 did not become ready."; stop_all || true; exit 1; fi
if wait_for_port 9014; then pass "LoginServer GS port 9014 is listening."
else fail "LoginServer port 9014 did not become ready."; stop_all || true; exit 1; fi
sleep 2

info "Starting GameServer..."
cd "$GAME_DIR"; ./GameServer.sh
sleep 2
if wait_for_proc "GameServer\\.jar"; then
	PID_GAME=$(pgrep -f "GameServer\\.jar" | head -1)
	pass "GameServer started (PID $PID_GAME)."
else
	fail "GameServer failed to start."; stop_all || true; exit 1
fi

info "Waiting for GameServer to load (this may take a while)..."
if wait_for_log "$GAME_DIR/log/stdout.log" "Registered on login"; then
	pass "GameServer registered on LoginServer."
else fail "GameServer did not register within 120s."; stop_all || true; exit 1; fi
if wait_for_log "$GAME_DIR/log/stdout.log" "Server loaded"; then pass "GameServer loaded successfully."
else warn "Could not confirm 'Server loaded' in log, but proceeding."; fi

echo ""; echo "  $LBREAK"; echo "  Final Verification"; echo "  $LBREAK"
check_status; RESULT=$?
echo ""
if [ $RESULT -eq 0 ]; then echo -e "  ${GREEN}Startup completed successfully.${NC}"
else echo -e "  ${RED}Startup completed with issues.${NC}"; fi
echo "  $LBREAK"; echo ""
exit $RESULT
