#!/bin/bash
# start_local.sh — start the L2JM server using the user-local JDK 25 + local MariaDB.
# Usage: bash scripts/start_local.sh [--restart]
set -u
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${JAVA25_HOME:=$HOME/.jdk/jdk-25.0.4+7}"
if [ ! -x "$JAVA25_HOME/bin/java" ]; then
    echo "JDK 25 not found at $JAVA25_HOME — set JAVA25_HOME or install it." >&2
    exit 1
fi
export JAVA_HOME="$JAVA25_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
echo "[start_local] JAVA: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
exec "$REPO/StartServer.sh" "$@"
