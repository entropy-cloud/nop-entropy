#!/usr/bin/env sh
set -euo pipefail

# Run the direct in-process exec using global `bun`.
# Usage:
#   ./scripts/run_direct_exec.sh <projectPath> <prompt> [sessionId]
# Optional env vars you can set before invoking:
#   PROMPT="your task"              # default prompt text (overridden by arg 2)
#   OPENCODE_MODEL="provider/model" # override model (e.g., opencode/big-pickle)
#   OPENCODE_AGENT="explore"        # agent name (falls back to AGENT or "explore")
#   OPENCODE_DISABLE_MCP=1           # skip MCP tools
#   OPENCODE_NO_INSTALL=1            # skip dependency installs
#   OPENCODE_NO_SPAWN=1              # block spawning (not recommended if you want bash/git)

OPENCODE_AGENT=build

SCRIPT_DIR=$(cd -- "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd -- "${SCRIPT_DIR}/.." && pwd)

cd "${REPO_ROOT}"

if [ $# -lt 2 ]; then
	echo "Usage: $0 <projectPath> <prompt> [sessionId]" >&2
	exit 1
fi

PROJECT_PATH=$1
PROMPT_ARG=$2
SESSION_ID=${3:-}

PROMPT=${PROMPT:-""}
export PROMPT

if [ -n "$SESSION_ID" ]; then
	exec bun scripts/direct_exec_test.mjs "$PROJECT_PATH" "$PROMPT_ARG" "$SESSION_ID"
else
	exec bun scripts/direct_exec_test.mjs "$PROJECT_PATH" "$PROMPT_ARG"
fi
