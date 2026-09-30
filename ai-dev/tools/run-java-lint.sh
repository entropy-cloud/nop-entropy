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
  cat >&2 <<EOF
Error: ast-grep (sg) not found at $SG_BIN.

Java lint (pre-commit hook / 'pnpm lint:java') runs the native sg binary
shipped by the @ast-grep/cli npm package. node_modules/ is NOT tracked by
git, so every fresh clone or worktree needs a one-time install.

Fix (one command, ~30s, copy-paste from anywhere):
  pnpm --dir "$SCRIPT_DIR" install

If pnpm is missing (this repo pins pnpm via packageManager, corepack can provide it):
  corepack enable && pnpm --dir "$SCRIPT_DIR" install
  # or as fallback (ignores pnpm-lock.yaml): npm --prefix "$SCRIPT_DIR" install

Then retry the commit. To bypass this gate once (not recommended):
  git commit --no-verify
EOF
  exit 1
fi

cd "$PROJECT_ROOT"

"$SG_BIN" scan "$@" --config "$SCRIPT_DIR/sgconfig.yml"
