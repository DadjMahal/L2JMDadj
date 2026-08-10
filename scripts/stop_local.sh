#!/bin/bash
# stop_local.sh — stop the L2JM server processes (JDK-25 PATH for matching).
set -u
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${JAVA25_HOME:=$HOME/.jdk/jdk-25.0.4+7}"
export JAVA_HOME="$JAVA25_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
exec "$REPO/StopServer.sh" "$@"
