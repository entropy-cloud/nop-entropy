#!/bin/bash
# tools/mission-driver.sh — Mission driver launcher
#
# Usage:
#   ./mission-driver.sh <mission>                  Run full mission-driver flow
#   ./mission-driver.sh <mission> --list-steps     List available top-level steps
#   ./mission-driver.sh <mission> --step <STEP>    Run single step only
#   ./mission-driver.sh --list-missions            List available missions
#   ./mission-driver.sh --draft-mission <desc>     Generate a new mission.json
#
# <mission> is the name in missions/<mission>.json (e.g. "ai-agent").

MISSION_DRIVER_HOME="${MISSION_DRIVER_HOME:-$HOME/app/attractor-guided-engineering-template/tools/mission-driver}"
DIR="$(cd "$(dirname "$0")" && pwd)"

exec node "$MISSION_DRIVER_HOME/src/main.js" \
  --dir "$DIR/../.." \
  --missions-dir "ai-dev/missions" \
  "$@"
