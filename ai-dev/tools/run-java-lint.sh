#!/usr/bin/env bash
# ============================================================
# run-java-lint — Run ast-grep Java lint rules against the project
# ============================================================
# Usage:
#   ./run-java-lint.sh                    # scan entire project
#   ./run-java-lint.sh nop-ai             # scan specific module
#   ./run-java-lint.sh --filter empty-catch  # run a subset of rules
#
# Paths are relative to the project root (where this script is).
# All extra arguments are forwarded to `sg scan`.
# ============================================================

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
SG_BIN="$SCRIPT_DIR/node_modules/.bin/sg"

if [ ! -x "$SG_BIN" ]; then
  echo "Error: ast-grep not found at $SG_BIN. Run 'pnpm install' first." >&2
  exit 1
fi

cd "$PROJECT_ROOT"

"$SG_BIN" scan "$@" --config "$SCRIPT_DIR/sgconfig.yml"
