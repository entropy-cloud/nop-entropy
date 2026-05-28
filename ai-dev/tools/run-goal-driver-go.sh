#!/bin/bash
# tools/run-goal-driver-go.sh — Production mode (full prompts, fast AI)
DIR="$(cd "$(dirname "$0")" && pwd)"
exec node "$DIR/opencode-goal-driver/src/main.js" --model zhipuai-coding-plan/glm-4.7-flash "$@"
