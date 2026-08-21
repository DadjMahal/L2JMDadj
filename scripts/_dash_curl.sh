#!/bin/bash
# EP-6 shared helper: source this from scripts that curl the fleet dashboard.
#   . "$(dirname "$0")/_dash_curl.sh"
# then wrap dashboard URLs:  curl -s "$(durl "http://127.0.0.1:8210/json")"
# durl appends ?token=$DASH_TOKEN when configured (fleet_env.local), else returns the URL unchanged.
[ -f "$(dirname "${BASH_SOURCE[0]}")/fleet_env.local" ] && . "$(dirname "${BASH_SOURCE[0]}")/fleet_env.local"

durl() {
  local url="$1"
  if [ -n "${DASH_TOKEN:-}" ] && [[ "$url" != *token=* ]]; then
    case "$url" in
      *\?*) url="$url&token=$DASH_TOKEN" ;;
      *)    url="$url?token=$DASH_TOKEN" ;;
    esac
  fi
  printf '%s' "$url"
}
