#!/usr/bin/env bash
# package-ib.sh — build nop-ib and install a self-contained distribution to
# ~/nop-ib (owner directive 2026-11-17).
#
# Layout produced:
#   ~/nop-ib/nop-ib.jar        the self-contained CLI/service jar (shaded)
#   ~/nop-ib/bin/ib            client CLI wrapper (any project, any cwd)
#   ~/nop-ib/bin/start.sh      start the HTTP service
#   ~/nop-ib/bin/stop.sh       stop the HTTP service
#   ~/nop-ib/README.md         usage guide
#
# Usage: nop-tools/nop-ib/bin/package-ib.sh [target-dir]   (default ~/nop-ib)
set -euo pipefail
BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$(cd "$BIN_DIR/.." && pwd)"
ROOT="$(cd "$MOD_DIR/../.." && pwd)"
DEST="${1:-$HOME/nop-ib}"

echo "== building nop-tools/nop-ib (skip tests) =="
(cd "$ROOT" && ./mvnw -q -pl nop-tools/nop-ib -am -DskipTests package)

echo "== staging $DEST =="
mkdir -p "$DEST/bin"
# shade produces the self-contained uber jar (owner 2026-11-18: lib/ dropped —
# the shaded nop-ib.jar needs no dependency dir; the plain versioned jar has NO
# main-class manifest, so prefer the shaded one explicitly)
if [ -f "$MOD_DIR/target/nop-ib.jar" ]; then
    cp "$MOD_DIR/target/nop-ib.jar" "$DEST/nop-ib.jar"
else
    echo "ERROR: shaded target/nop-ib.jar missing (shade plugin did not run)" >&2
    exit 1
fi

echo "== writing scripts =="
cat > "$DEST/bin/ib" <<'WRAPPER'
#!/usr/bin/env bash
# ib — nop-ib CLI client against the ~/nop-ib distribution.
# Works for ANY Maven project from ANY cwd (pass the project dir via CLI cwd
# or use the HTTP service with projectDir).
set -euo pipefail
DIST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CP="$DIST/nop-ib.jar"
exec java -Xmx2g -cp "$CP" io.nop.ib.NopIbMain "$@"
WRAPPER

cat > "$DEST/bin/start.sh" <<'STARTER'
#!/usr/bin/env bash
# start.sh — start the nop-ib HTTP service (default port 8188, env NOP_IB_PORT).
# Idempotent: a healthy running instance is reported and left alone.
set -euo pipefail
DIST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${NOP_IB_PORT:-8188}"
PIDFILE="$DIST/nop-ib.pid"
LOG="$DIST/nop-ib.log"

if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    echo "nop-ib already running (pid $(cat "$PIDFILE"))"
    curl -sf "http://127.0.0.1:$PORT/v1/health" && echo
    exit 0
fi
nohup java -Xmx4g -cp "$DIST/nop-ib.jar" \
    io.nop.ib.NopIbMain serve --http --port "$PORT" \
    >> "$LOG" 2>&1 &
echo $! > "$PIDFILE"
for i in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:$PORT/v1/health" > /dev/null 2>&1; then
        echo "nop-ib started (pid $(cat "$PIDFILE"), port $PORT, log $LOG)"
        curl -s "http://127.0.0.1:$PORT/v1/health"; echo
        exit 0
    fi
    sleep 0.5
done
echo "nop-ib failed to become healthy — see $LOG" >&2
exit 1
STARTER

cat > "$DEST/bin/stop.sh" <<'STOPPER'
#!/usr/bin/env bash
# stop.sh — stop the nop-ib HTTP service started by start.sh.
set -euo pipefail
DIST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PIDFILE="$DIST/nop-ib.pid"
if [ ! -f "$PIDFILE" ]; then
    echo "no pidfile — not started via start.sh?"; exit 0
fi
PID=$(cat "$PIDFILE")
if kill -0 "$PID" 2>/dev/null; then
    kill "$PID"
    for i in $(seq 1 20); do kill -0 "$PID" 2>/dev/null || break; sleep 0.5; done
    kill -0 "$PID" 2>/dev/null && kill -9 "$PID"
    echo "nop-ib stopped (pid $PID)"
else
    echo "stale pidfile (pid $PID not running)"
fi
rm -f "$PIDFILE"
STOPPER

chmod +x "$DEST/bin/ib" "$DEST/bin/start.sh" "$DEST/bin/stop.sh"

cp "$BIN_DIR/README.md" "$DEST/README.md"
echo "== done: $DEST =="
