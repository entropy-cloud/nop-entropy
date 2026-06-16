#!/bin/bash
# tools/goal-driver.sh — Goal driver launcher
#
# Usage:
#   ./goal-driver.sh <module>                      Run full goal-driver flow
#   ./goal-driver.sh <module> --list-steps         List available top-level steps
#   ./goal-driver.sh <module> --step <STEP>        Run single step only
#   ./goal-driver.sh <module> --help               Show this help
#
# Examples:
#   ./goal-driver.sh nop-ai-agent
#   ./goal-driver.sh nop-stream --step AUDIT
#   ./goal-driver.sh nop-stream --step PLANS
#   ./goal-driver.sh nop-stream --step CHECK

DIR="$(cd "$(dirname "$0")" && pwd)"
MAIN="$DIR/opencode-goal-driver/src/main.js"

show_help() {
  echo "Goal Driver — Nop平台自动化开发流程引擎"
  echo ""
  echo "Usage:"
  echo "  $(basename "$0") <module>                      Run full goal-driver flow"
  echo "  $(basename "$0") <module> --list-steps         List available top-level steps"
  echo "  $(basename "$0") <module> --step <STEP>        Run single step only (stops after step completes)"
  echo "  $(basename "$0") <module> --help               Show this help"
  echo ""
  echo "Other flags (passed through to main.js):"
  echo "  --dry-run          Simulate without running AI agents"
  echo "  --agent <name>     AI agent to use (default: build)"
  echo "  --model <name>     AI model to use"
  echo "  --max-cycles <N>   Max visits per step (default: 30)"
  echo ""
  echo "Available steps:"
  node -e "
    const m = require('fs').readFileSync('$DIR/opencode-goal-driver/flows/goal-driver.json','utf8');
    const f = JSON.parse(m);
    Object.keys(f.steps||{}).forEach(s => console.log('  '+s));
  "
  echo ""
  echo "Examples:"
  echo "  $(basename "$0") nop-stream"
  echo "  $(basename "$0") nop-ai-agent --step PLANS"
  echo "  $(basename "$0") nop-ai-agent --step AUDIT"
  echo "  $(basename "$0") nop-ai-agent --dry-run"
}

# If --help flag is the only special arg before the module name
for arg in "$@"; do
  if [ "$arg" = "--help" ] || [ "$arg" = "-h" ]; then
    show_help "$@"
    exit 0
  fi
done

# Pass all arguments to main.js
exec node "$MAIN" "$@"
