#!/bin/bash
# tools/run-goal-driver.sh — Run opencode goal driver from anywhere
DIR="$(cd "$(dirname "$0")" && pwd)"
exec node "$DIR/opencode-goal-driver/src/main.js" "$@"
