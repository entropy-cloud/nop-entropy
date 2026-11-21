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

cat > "$DEST/bin/ib-run" <<'IBRUN'
#!/usr/bin/env bash
# ib-run — thin HTTP facade over the nop-ib serve service (port 8188).
#
# Why this exists: callers must go through the HTTP service (queueing +
# concurrency governance + Maven fallback), never the raw `ib` CLI.
# This wrapper keeps that guarantee while giving a CLI-shaped UX:
#
#   ib-run check --paths src/Foo.java --json
#   ib-run compile --paths nop-auth/...          # from any cwd inside the repo
#   ib-run --dir /path/to/repo test --plans X    # explicit project dir
#   ib-run model refresh
#   ib-run status
#
# It infers projectDir from the enclosing git root, POSTs /v1/run with
# wait=true, prints exit/fallback summary, and propagates the build exit code.
set -euo pipefail

PORT="${IB_PORT:-8188}"
BASE="http://127.0.0.1:$PORT"

DIR_OVERRIDE=""
if [[ "${1:-}" == "--dir" ]]; then
  DIR_OVERRIDE="${2:?--dir requires a path}"
  shift 2
fi

if [[ $# -lt 1 ]]; then
  echo "usage: ib-run [--dir <projectDir>] <cmd> [args...]" >&2
  echo "  cmd: status|plan|compile|test|check|model|clean|mvn" >&2
  exit 3
fi

CMD="$1"; shift

if [[ -n "$DIR_OVERRIDE" ]]; then
  PROJECT_DIR="$(cd "$DIR_OVERRIDE" && pwd)"
else
  PROJECT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
fi

# health check with start hint
if ! curl -sf "$BASE/v1/health" >/dev/null 2>&1; then
  echo "nop-ib service not reachable at $BASE — run: $(dirname "${BASH_SOURCE[0]}")/start.sh" >&2
  exit 75
fi

PAYLOAD="$(CMD="$CMD" PROJECT_DIR="$PROJECT_DIR" ARGS="$*" python3 - <<'PY'
import json, os, shlex
args = shlex.split(os.environ["ARGS"]) if os.environ["ARGS"].strip() else []
print(json.dumps({
    "projectDir": os.environ["PROJECT_DIR"],
    "cmd": os.environ["CMD"],
    "args": args,
    "wait": True,
}))
PY
)"

HTTP="$(curl -s -X POST "$BASE/v1/run" -d "$PAYLOAD" -w "\n%{http_code}")"
CODE="$(printf '%s' "$HTTP" | tail -1)"
RESP="$(printf '%s' "$HTTP" | sed '$d')"

python3 - "$RESP" "$CODE" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
if str(d.get("error") or ""):
    print(f"error ({sys.argv[2]}): {d['error']}", file=sys.stderr)
    sys.exit(3)
job = d.get("job") or d
state = d.get("state") or job.get("state") or ""
exit_code = d.get("exit")
if exit_code is None:
    exit_code = job.get("exit")
if exit_code is None or exit_code == "":
    # cancelled/failed jobs may report no exit at top level — never print a
    # bare "exit=" and never exit 0 on a non-done state
    exit_code = 0 if state == "done" else 1
fb = d.get("fallback") or {}
print(f"exit={exit_code} state={state or '-'} fallback={fb.get('reason') or '-'}")
print(f"log: {job.get('logPath') or '-'}")
for key in ("stderr", "logTail", "error"):
    v = job.get(key) or d.get(key)
    if v:
        print(f"--- {key} ---")
        print(v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))
        break
sys.exit(int(exit_code) if str(exit_code).lstrip('-').isdigit() else 1)
PY
IBRUN

chmod +x "$DEST/bin/ib" "$DEST/bin/ib-run" "$DEST/bin/start.sh" "$DEST/bin/stop.sh"

cp "$BIN_DIR/README.md" "$DEST/README.md"
echo "== done: $DEST =="
