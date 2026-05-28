#!/bin/bash
# tools/run-goal-driver-test.sh — Test mode (simplified prompts, fast AI)
DIR="$(cd "$(dirname "$0")" && pwd)"
exec node "$DIR/opencode-goal-driver/src/main.js" --test --model zhipuai-coding-plan/glm-4.7-flash "$@"
