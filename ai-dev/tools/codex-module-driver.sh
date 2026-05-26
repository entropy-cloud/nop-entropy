#!/usr/bin/env bash
#
# codex-module-driver.sh
#
# 通用 codex goal 驱动脚本。针对指定模块启动 codex TUI，
# 利用内置 /goal 机制自动驱动模块完善。
#
# 用法:
#   ./ai-dev/tools/codex-module-driver.sh nop-stream
#   ./ai-dev/tools/codex-module-driver.sh nop-code
#
# 零参数时显示帮助。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# ── 参数 ──────────────────────────────────────────────────

MODULE="${1:-}"

if [[ -z "$MODULE" ]]; then
    echo "Usage: $0 <module-name>"
    echo ""
    echo "Examples:"
    echo "  $0 nop-stream"
    echo "  $0 nop-code"
    echo "  $0 nop-auth"
    echo "  $0 nop-job"
    echo ""
    echo "Reads the goal prompt from ai-dev/skills/codex-goal-driven-development-prompt.md"
    echo "and launches codex TUI with /goal auto-continuation."
    exit 1
fi

# ── 验证模块存在 ──────────────────────────────────────────

if [[ ! -d "$PROJECT_ROOT/$MODULE" ]]; then
    echo "Error: Module '$MODULE' not found at $PROJECT_ROOT/$MODULE" >&2
    exit 1
fi

if [[ ! -f "$PROJECT_ROOT/AGENTS.md" ]]; then
    echo "Error: AGENTS.md not found at $PROJECT_ROOT" >&2
    exit 1
fi

SKILL_FILE="$PROJECT_ROOT/ai-dev/skills/codex-goal-driven-development-prompt.md"
if [[ ! -f "$SKILL_FILE" ]]; then
    echo "Error: Skill prompt not found at $SKILL_FILE" >&2
    exit 1
fi

# ── 检查 codex ────────────────────────────────────────────

if ! command -v codex &>/dev/null; then
    echo "Error: codex not found. Install: npm install -g @openai/codex" >&2
    exit 1
fi

# ── 从 config.toml 读取 model ─────────────────────────────

CODEX_MODEL="${CODEX_MODEL:-}"
if [[ -z "$CODEX_MODEL" ]]; then
    CODEX_MODEL=$(awk '/^model[[:space:]]*=/{gsub(/"/,""); print $3}' ~/.codex/config.toml 2>/dev/null || echo "")
fi
CODEX_MODEL="${CODEX_MODEL:-o3}"

# ── 检查 model provider 所需的环境变量 ──────────────────

CONFIG_FILE="$HOME/.codex/config.toml"
if [[ -f "$CONFIG_FILE" ]]; then
    PROVIDER_NAME=$(awk '/^model_provider[[:space:]]*=/{gsub(/"/,""); print $3}' "$CONFIG_FILE" 2>/dev/null || echo "")
    if [[ -n "$PROVIDER_NAME" ]]; then
        ENV_KEY=$(awk "/\[model_providers\.${PROVIDER_NAME}\]/,/\[/{if(/^env_key[[:space:]]*=/){gsub(/\"/,\"\"); print \$3; exit}}" "$CONFIG_FILE" 2>/dev/null || echo "")
        if [[ -n "$ENV_KEY" && -z "${!ENV_KEY:-}" ]]; then
            echo "Error: Missing environment variable: $ENV_KEY" >&2
            echo "       Required by codex model_provider '$PROVIDER_NAME'" >&2
            echo "" >&2
            echo "Fix: export $ENV_KEY=\"your-key\" (add to ~/.zshrc)" >&2
            exit 1
        fi
    fi
fi

# ── 构建 prompt ───────────────────────────────────────────

# 读取 skill 文件，替换 {MODULE} 占位符
PROMPT=$(sed "s/{MODULE}/$MODULE/g" "$SKILL_FILE")

# 检查模块是否有专属审计提示词
MODULE_AUDIT="$PROJECT_ROOT/ai-dev/skills/${MODULE}/audit-prompt.md"
if [[ -f "$MODULE_AUDIT" ]]; then
    PROMPT="${PROMPT}"$'\n\n'"## 模块专属审计提示词"$'\n\n'"该模块有专属审计提示词 \`ai-dev/skills/${MODULE}/audit-prompt.md\`，在阶段 A 第 3 步中必须一并执行。"
fi

# 检查模块是否有设计文档目录
DESIGN_DIR="$PROJECT_ROOT/ai-dev/design/$MODULE"
if [[ -d "$DESIGN_DIR" ]]; then
    PROMPT="${PROMPT}"$'\n\n'"## 模块设计文档"$'\n\n'"该模块有设计文档目录 \`ai-dev/design/${MODULE}/\`，规划前必读其中的 README.md 和 component-roadmap.md（如有）。"
fi

echo "=== Codex Module Driver ==="
echo "Module:  $MODULE"
echo "Model:   $CODEX_MODEL"
echo "Skill:   ai-dev/skills/codex-goal-driven-development-prompt.md"
if [[ -f "$MODULE_AUDIT" ]]; then
    echo "Audit:   ai-dev/skills/${MODULE}/audit-prompt.md"
fi
if [[ -d "$DESIGN_DIR" ]]; then
    echo "Design:  ai-dev/design/$MODULE/"
fi
echo "Codex:   $(codex --version 2>/dev/null || echo 'unknown')"
echo ""

# ── 启动 codex TUI ────────────────────────────────────────

exec codex \
    --model "$CODEX_MODEL" \
    --sandbox danger-full-access \
    -C "$PROJECT_ROOT" \
    "$(cat <<HEADER
请用 create_goal 工具创建以下目标，然后立即开始工作：

HEADER
echo "$PROMPT"
)"
