#!/bin/bash
# tools/mission-driver.sh — Mission driver launcher
#
# Thin wrapper that forwards to the mission-driver engine. The tool itself
# lives at tools/mission-driver/; this script sets the project root and
# missions dir, then hands off to src/main.js.
#
# Usage:
#   ./tools/mission-driver.sh run <mission>              Run a mission (default)
#   ./tools/mission-driver.sh run <mission> --step <S>   Run a single step only
#   ./tools/mission-driver.sh list                       List available missions
#   ./tools/mission-driver.sh list-steps <mission>       List a mission's steps
#   ./tools/mission-driver.sh draft "<description>"      Generate a mission.json
#   ./tools/mission-driver.sh analyze [run-dir]          Postmortem a run
#   ./tools/mission-driver.sh monitor                    Standalone monitor mode
#   ./tools/mission-driver.sh --help                     Full CLI help
#
# <mission> is the name in missions/<mission>.json (e.g. "components").

# --- Tool location (defaults to this repo's tools/mission-driver) ---
DIR="$(cd "$(dirname "$0")" && pwd | tr -d '\r')"
MISSION_DRIVER_HOME="${MISSION_DRIVER_HOME:-$DIR/mission-driver}"

# --- Project root (derived from this script's location: tools/mission-driver.sh) ---
PROJECT_ROOT="$(cd "$DIR/.." && pwd)"

exec node "$MISSION_DRIVER_HOME/src/main.js" \
  --dir "$PROJECT_ROOT" \
  --missions-dir "missions" \
  "$@"
