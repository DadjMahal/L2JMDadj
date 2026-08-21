#!/bin/bash
# S9-T09: DB backup before mass provisioning / each night. Dumps loginserver+gameserver.
# Creds via DB_USER/DB_PASS env or scripts/fleet_env.local (EP-6: no hardcoded default).
set -u
[ -f "$(dirname "$0")/fleet_env.local" ] && . "$(dirname "$0")/fleet_env.local"
DIR="${1:-/home/dadj/Projects/l24lude/backups}"
: "${DB_USER:?set DB_USER (scripts/fleet_env.local — see fleet_env.local.example)}"
: "${DB_PASS:?set DB_PASS (scripts/fleet_env.local — see fleet_env.local.example)}"
mkdir -p "$DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$DIR/db-$STAMP.sql"
mysqldump -u"$DB_USER" -p"$DB_PASS" --databases loginserver gameserver > "$OUT" 2>/dev/null && echo "backup: $OUT ($(du -h "$OUT" | cut -f1))" || { echo "backup FAILED"; exit 1; }
# keep last 10
ls -1t "$DIR"/db-*.sql 2>/dev/null | tail -n +11 | xargs -r rm -f