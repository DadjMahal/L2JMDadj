#!/usr/bin/env bash
# ============================================================================
# WPT-31 — Build the AI Fleet dashboard as a single self-contained bundle.
# ----------------------------------------------------------------------------
#  * Validates the modular sources (JS syntax via node --check, map data JSON
#    via python3 -m json.tool).
#  * Regenerates regions.json + landmarks.json if the datapack/API coords
#    changed (scripts/gen_mapdata.py).
#  * Bundles + minifies css/js/map-data and writes a VERSIONED tag into the
#    served dashboard/index.html via scripts/_build.js.
#
# The SERVED index.html is self-contained (inlined CSS + JS + embedded map
# data), so it runs on FleetPlay's single-resource "/" handler with no
# external asset fetches. Cache busting = the build version tag written into
# the bundle as window.__BUILD__ and an HTML comment.
# ============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DASH="$REPO/AIPlayerEngine/src/main/resources/dashboard"
NODE="${NODE:-node}"

echo "[build_dashboard.sh] repo: $REPO"

# --- 1. (Re)generate map data from the datapack (idempotent) --------------
if [ -f "$REPO/AIPlayerEngine/scripts/gen_mapdata.py" ]; then
  echo "[build_dashboard.sh] regenerating map data (gen_mapdata.py)..."
  python3 "$REPO/AIPlayerEngine/scripts/gen_mapdata.py"
fi

# --- 2. Validate modular sources ------------------------------------------
echo "[build_dashboard.sh] validating JS syntax (node --check)..."
"$NODE" --check "$DASH/js/map.js"
"$NODE" --check "$DASH/js/app.js"

echo "[build_dashboard.sh] validating map data JSON (python3 -m json.tool)..."
python3 -m json.tool "$DASH/data/regions.json" >/dev/null
python3 -m json.tool "$DASH/data/landmarks.json" >/dev/null

# --- 3. Bundle + minify + version tag -------------------------------------
echo "[build_dashboard.sh] bundling + minifying via scripts/_build.js..."
"$NODE" "$REPO/scripts/_build.js"

echo "[build_dashboard.sh] done."
