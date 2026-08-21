#!/bin/bash
# S9-T03: provision N random-race accounts+chars (balanced across the 5 races), 10 each over 50.
# The DB race/class is set per account; NOTE: a true in-world random race also needs
# character_subclasses rows (see TASKS S4-T06/S10-T09) — this provisions the base characters row.
# Credentials via env DB_USER/DB_PASS or scripts/fleet_env.local (EP-6: no default).
# Usage: scripts/provision_fleet.sh [count] [prefix] [charIdBase] [passwordHash]
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
COUNT="${1:-50}"
PREFIX="${2:-ai_rand_}"
CHARID_BASE="${3:-500000}"
PWHASH="${4:-CBaKoSACCN4c8lxxnen4gH2jHh8=}"
: "${DB_USER:?set DB_USER (scripts/fleet_env.local — see fleet_env.local.example)}"
: "${DB_PASS:?set DB_PASS (scripts/fleet_env.local — see fleet_env.local.example)}"
SDIR="$(cd "$(dirname "$0")" && pwd)"

SQL="$(mktemp /tmp/provision.XXXXXX.sql)"
trap 'rm -f "$SQL"' EXIT
python3 "$SDIR"/provision_fleet.py "$COUNT" "$PREFIX" "$CHARID_BASE" "$PWHASH" > "$SQL" || { echo "generate failed"; exit 1; }
mysql -u"$DB_USER" -p"$DB_PASS" < "$SQL" || { echo "mysql apply failed"; exit 1; }
echo "provisioned: $COUNT chars (prefix=$PREFIX, charId base=$CHARID_BASE)"
