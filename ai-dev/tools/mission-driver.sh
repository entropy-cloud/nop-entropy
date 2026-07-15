#!/bin/bash
# tools/mission-driver.sh — Mission driver launcher
#
# Usage:
#   ./mission-driver.sh run <mission>              Run full mission-driver flow
#   ./mission-driver.sh run <mission> --step <S>   Run a single step only
#   ./mission-driver.sh draft <description>        Generate a new mission.json
#   ./mission-driver.sh list [missions|steps]      List missions (default) or steps
#   ./mission-driver.sh help [command]             Show help (top-level or per-command)
#
# <mission> is the name in missions/<mission>.json (e.g. "ai-agent").
# Run './mission-driver.sh --help' for the full option list.

MISSION_DRIVER_HOME="${MISSION_DRIVER_HOME:-$HOME/app/attractor-guided-engineering-template/tools/mission-driver}"
DIR="$(cd "$(dirname "$0")" && pwd)"

exec node "$MISSION_DRIVER_HOME/src/main.js" \
  "$@" \
  --dir "$DIR/../.." \
  --missions-dir "missions"
