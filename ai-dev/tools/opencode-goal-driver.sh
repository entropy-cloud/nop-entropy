#!/usr/bin/env bash
#
# opencode-goal-driver.sh
#
# crontab 调用一次，脚本内部管控工作流循环。
# --dry-run 模式：不调 opencode，输出完整提示词供审查。
#
# 用法: ./opencode-goal-driver.sh <module-name> [--dry-run]
# 退出码: 0=完成, 1=出错, 2=超过最大循环次数

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="${STATE_DIR:-$PROJECT_ROOT/ai-dev/.task-state}"
MODULE="${1:-}"
DRY_RUN="${2:-}"

MAX_CYCLES="${MAX_CYCLES:-20}"
MAX_INNER_CYCLES="${MAX_INNER_CYCLES:-5}"
OPENCODE_AGENT="${OPENCODE_AGENT:-build}"
OPENCODE_MODEL="${OPENCODE_MODEL:-zhipuai-coding-plan/glm-5.1}"

[[ -n "$MODULE" ]] || { echo "Usage: $0 <module-name> [--dry-run]" >&2; exit 1; }
[[ -d "$PROJECT_ROOT/$MODULE" ]] || { echo "Error: module '$MODULE' not found" >&2; exit 1; }
[[ "$DRY_RUN" == "--dry-run" ]] && DRY_RUN=1 || DRY_RUN=0

mkdir -p "$STATE_DIR" "$PROJECT_ROOT/ai-dev/audits" "$PROJECT_ROOT/ai-dev/plans"
LOG_FILE="$STATE_DIR/$MODULE.log"

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG_FILE"; }

# ──────────────────────────────────────────────────
# Prompt 生成器（每步独立函数，便于审查）
# ──────────────────────────────────────────────────

gen_fix_prompt() {
  cat <<PROMPT
你负责修复模块 $MODULE 的编译错误。

请运行 mvnw clean install -pl $MODULE -am -T 1C 获取错误详情，
修复所有编译错误使构建通过。

输出标记（末尾单独一行）：
- ##HEALTH_STATUS: FIXED
- ##HEALTH_STATUS: FAILED
PROMPT
}

gen_audit_prompt() {
  cat <<PROMPT
你负责对模块 $MODULE 进行审计。

## 步骤 1：阅读技能文件
- ai-dev/skills/deep-audit-prompts.md（21 个审计维度）
- ai-dev/skills/open-ended-adversarial-review-prompt.md（开放性对抗审查）

## 步骤 2：多维度深度审计
根据模块类型选择合适维度，派发独立子 agent（task 工具）执行审计。
审计结果写入 ai-dev/audits/{DATE}-deep-audit-$MODULE/。

## 步骤 3：开放式对抗审查
派发独立子 agent 执行对抗性审查，结果写入 ai-dev/audits/{DATE}-adversarial-review-$MODULE/。

## 步骤 4：综合评估
读取审计结果，判断是否存在 P0/P1 问题。
在输出末尾单独一行打印：
- ##AUDIT_RESULT: CLEAN（无 P0/P1 问题）
- ##AUDIT_RESULT: ISSUES（存在 P0/P1 问题）
PROMPT
}

gen_plan_prompt() {
  cat <<PROMPT
你负责为模块 $MODULE 的审计发现拟定修复计划。

## 步骤 1：读取审计结果
读取 ai-dev/audits/ 下最近针对 $MODULE 的审计报告，找出所有 P0/P1 问题。

## 步骤 2：问题分组
按依赖关系和影响范围将问题分组（1-3 个 plan，每个 Goals ≤ 5 条）。

## 步骤 3：拟定 plan
阅读 ai-dev/plans/00-plan-authoring-and-execution-guide.md 了解格式，
在 ai-dev/plans/ 下创建 plan 文件。

## 步骤 4：plan 审核
阅读 ai-dev/skills/plan-reviewer-prompt.md，派发子 agent（task）审核。
反复直到无 blocking findings。

## 步骤 5：输出标记（末尾单独一行）
- ##PLAN_RESULT: CREATED
- ##PLAN_RESULT: NONE
PROMPT
}

gen_exec_prompt() {
  local remaining="${1:-}"
  if [[ -n "$remaining" ]]; then
    cat <<PROMPT
你负责继续执行模块 $MODULE 的修复计划中的未完成项。

以下是独立验证agent发现的未完成项：
$remaining

按依赖顺序执行每个工作项。完成后将 - [ ] 改为 - [x]。
每次改动后运行 ./mvnw test -pl $MODULE -am -T 1C。

输出标记（末尾单独一行）：
- ##EXECUTE_RESULT: SUCCESS
- ##EXECUTE_RESULT: FAILED
PROMPT
  else
    cat <<PROMPT
你负责执行模块 $MODULE 的修复计划。

## 步骤 1：读取 plan
读取 ai-dev/plans/ 下关于 $MODULE 的 plan 文件。

## 步骤 2：逐个执行
按依赖顺序执行每个工作项。完成后将 - [ ] 改为 - [x]。
每次改动后运行 ./mvnw test -pl $MODULE -am -T 1C。

## 步骤 3：提交代码
git 提交，格式：<plan编号>: <plan标题>

## 步骤 4：输出标记（末尾单独一行）
- ##EXECUTE_RESULT: SUCCESS
- ##EXECUTE_RESULT: FAILED
PROMPT
  fi
}

gen_closure_audit_prompt() {
  cat <<PROMPT
你是一个独立验证者，没有参与过之前的计划执行和代码编写。
请验证模块 $MODULE 的修复计划是否真正完成。

步骤：
1. 读取 ai-dev/plans/ 下最新的活跃计划（Status 不是 completed/superseded/cancelled）
2. 逐条检查每个 Phase 的 Exit Criteria 是否在 live repo 代码中真正满足
3. 阅读 ai-dev/skills/plan-closure-audit-prompt.md
4. 运行 node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict（如存在）
5. 检查 Anti-Hollow：新增组件在运行时被调用，无空方法体/静默跳过

如果所有项目都已完成，输出（末尾单独一行）：
##CLOSURE_RESULT: COMPLETE

如果有未完成项目，输出（末尾单独一行）：
##CLOSURE_RESULT: INCOMPLETE

并在之前输出 XML 块：
<REMAINING>
  <item>具体未完成项描述（包含文件路径、期望行为、当前状态）</item>
</REMAINING>
PROMPT
}

gen_eval_prompt() {
  cat <<PROMPT
你负责判断模块 $MODULE 是否已达到完善目标。

## 检查清单（全部满足才算 COMPLETE）：
1. 构建通过：./mvnw clean install -pl $MODULE -am -T 1C（已验证）
2. 全量测试通过：./mvnw test -pl $MODULE -am -T 1C（已运行）
3. 多维度深度审计收敛：读取 ai-dev/audits/ 下最新审计报告，确认连续一轮无新 P0/P1
4. 对抗性审查收敛：确认最新一轮无新发现
5. 所有 plan 已 completed：检查 ai-dev/plans/ 下 $MODULE 相关 plan，确认所有 checklist 已勾选
6. 设计目标均已实现：读取 ai-dev/design/$MODULE/README.md（如有），对照检查

## 输出标记（末尾单独一行）：
- ##EVAL_RESULT: COMPLETE（全部满足）
- ##EVAL_RESULT: CONTINUE（还有未完成项或新发现问题）
PROMPT
}

# ──────────────────────────────────────────────────
# opencode 调用（真实实现）
# ──────────────────────────────────────────────────

_real_run_step() {
  local step_name="$1"
  local command="$2"
  local prompt_file="$3"
  shift 3

  local step_log="$STATE_DIR/$MODULE-$(date +%Y%m%d-%H%M%S)-$step_name.log"
  log ">>> [STEP] $step_name (log: $step_log)"

  local args=("--dir" "$PROJECT_ROOT" "--agent" "$OPENCODE_AGENT" "-m" "$OPENCODE_MODEL"
              "--dangerously-skip-permissions" "$command")
  [[ -n "${prompt_file:-}" && -f "${prompt_file:-}" ]] && args+=("-f" "$prompt_file")

  local output
  output=$(opencode run "${args[@]}" 2>&1) || true
  echo "$output" > "$step_log"
  echo "$output"
  return 0
}

# ──────────────────────────────────────────────────
# Mock 实现（仅打印，不调 opencode）
# ──────────────────────────────────────────────────

_mock_run_step() {
  local step_name="$1"
  local command="$2"
  local prompt_file="$3"
  shift 3

  >&2 echo ""
  >&2 echo "╔═══════════════════════════════════════════════"
  >&2 echo "║ MOCK STEP: $step_name"
  >&2 echo "╠═══════════════════════════════════════════════"
  >&2 echo "║ Command sent to opencode:"
  >&2 echo "║   opencode run --agent $OPENCODE_AGENT -m $OPENCODE_MODEL \\"
  >&2 echo "║     --dangerously-skip-permissions \\"
  >&2 echo "║     \"${command:0:120}...\" \\"
  [[ -n "${prompt_file:-}" && -f "${prompt_file:-}" ]] && >&2 echo "║     -f $prompt_file"
  >&2 echo "║"
  if [[ -n "${prompt_file:-}" && -f "${prompt_file:-}" ]]; then
    >&2 echo "║ Prompt file content ($prompt_file):"
    sed 's/^/║   /' "$prompt_file" >&2
  fi
  >&2 echo "╚═══════════════════════════════════════════════"

  # 模拟响应，让脚本走通全部分支
  case "$step_name" in
    fix-build)       echo "##HEALTH_STATUS: FIXED" ;;
    deep-audit)      echo "##AUDIT_RESULT: ISSUES" ;;
    plan)            echo "##PLAN_RESULT: CREATED" ;;
    execute)         echo "##EXECUTE_RESULT: SUCCESS" ;;
    closure-audit)
      local ca_count_file="$STATE_DIR/.mock_ca_count"
      local ca=0
      [[ -f "$ca_count_file" ]] && ca=$(<"$ca_count_file")
      if [[ "$ca" -lt 1 ]]; then
        echo $((ca + 1)) > "$ca_count_file"
        echo -e "<REMAINING><item>mock: 测试覆盖不足</item></REMAINING>\n##CLOSURE_RESULT: INCOMPLETE"
      else
        rm -f "$ca_count_file"
        echo "##CLOSURE_RESULT: COMPLETE"
      fi
      ;;
    eval)
      local count_file="$STATE_DIR/.mock_eval_count"
      local c=0
      [[ -f "$count_file" ]] && c=$(<"$count_file")
      if [[ "$c" -lt 1 ]]; then
        echo $((c + 1)) > "$count_file"
        echo "##EVAL_RESULT: CONTINUE"
      else
        rm -f "$count_file"
        echo "##EVAL_RESULT: COMPLETE"
      fi
      ;;
    *) echo "##MOCK_OK" ;;
  esac
  return 0
}

# ──────────────────────────────────────────────────
# 选择实现
# ──────────────────────────────────────────────────

if [[ "$DRY_RUN" == "1" ]]; then
  run_step() { _mock_run_step "$@"; }
  # mock 模式跳过 mvnw（假成功）
  run_build() { log "[MOCK] skip: mvnw clean install -pl $MODULE -am -T 1C"; return 0; }
  run_test()  { log "[MOCK] skip: mvnw test -pl $MODULE -am -T 1C"; return 0; }
else
  run_step() { _real_run_step "$@"; }
  run_build() { cd "$PROJECT_ROOT" && ./mvnw clean install -pl "$MODULE" -am -T 1C -q 2>&1 | tail -5 >> "$LOG_FILE"; }
  run_test()  { cd "$PROJECT_ROOT" && ./mvnw test -pl "$MODULE" -am -T 1C -q 2>&1 | tail -10 >> "$LOG_FILE"; }
fi

has_status() { echo "$1" | grep -Eq "##$2"; }

# ══════════════════════════════════════════════════
# 主循环
# ══════════════════════════════════════════════════

log "=== OpenCode Goal Driver: $MODULE ==="
log "Agent: $OPENCODE_AGENT  Model: $OPENCODE_MODEL  MaxCycles: $MAX_CYCLES  MaxInnerCycles: $MAX_INNER_CYCLES  DryRun: $DRY_RUN"

for (( outer=1; outer<=MAX_CYCLES; outer++ )); do
  log "╔══ Outer Cycle $outer/$MAX_CYCLES ══"

  # ── 0. 基础健康检查 ──
  log "[健康检查] 构建模块 $MODULE ..."
  if ! run_build; then
    log "[健康检查] 构建失败，尝试修复编译错误"
    pf=$(mktemp); trap 'rm -f "$pf"' EXIT; gen_fix_prompt > "$pf"
    fix_output=$(run_step "fix-build" \
      "模块 $MODULE 构建失败。修复编译错误。输出 ##HEALTH_STATUS: FIXED 或 FAILED" \
      "$pf")
    has_status "$fix_output" "HEALTH_STATUS: FIXED" \
      || { log "[健康检查] 修复失败，退出"; exit 1; }
  fi

  # ── 1. 审计 ──
  log "[深度审计] 对 $MODULE 执行多维度深度审计 + 对抗性审查 ..."
  pf=$(mktemp); trap 'rm -f "$pf"' EXIT; gen_audit_prompt > "$pf"
  audit_output=$(run_step "deep-audit" "请按 prompt 文件执行审计" "$pf")
  audit_has_issues=$(has_status "$audit_output" "AUDIT_RESULT: ISSUES" && echo "yes" || echo "no")

  # ── 2. 无问题 → DONE ──
  if [[ "$audit_has_issues" != "yes" ]]; then
    log "╚══ $MODULE 审计无问题，完善完成 (outer cycle $outer) ══"
    echo "$MODULE:GOAL_COMPLETE" > "$STATE_DIR/done-$MODULE"
    exit 0
  fi

  # ── 3. 拟制计划 ──
  log "[规划] 基于审计结果拟定修复计划 ..."
  pf=$(mktemp); trap 'rm -f "$pf"' EXIT; gen_plan_prompt > "$pf"
  plan_output=$(run_step "plan" "请按 prompt 文件为 $MODULE 拟定修复计划" "$pf")

  if ! has_status "$plan_output" "PLAN_RESULT: CREATED"; then
    log "[规划] 未创建计划，跳过执行，进入下一轮审计"
    continue
  fi

  # ── 4. 内循环：执行 → 独立验证 → 未完成则提取XML重试 ──
  remaining_xml=""
  plan_complete=false

  for (( inner=1; inner<=MAX_INNER_CYCLES; inner++ )); do
    log "║ Inner Cycle $inner/$MAX_INNER_CYCLES"

    # ── 4a. 执行 ──
    pf=$(mktemp); trap 'rm -f "$pf"' EXIT; gen_exec_prompt "$remaining_xml" > "$pf"
    exec_output=$(run_step "execute" "请执行 $MODULE 的修复计划" "$pf")
    has_status "$exec_output" "EXECUTE_RESULT: SUCCESS" \
      || log "[执行] 执行未完全成功"

    # ── 4b. 独立验证 (closure audit) ──
    log "[验证] 独立子agent验证计划完成度 ..."
    pf=$(mktemp); trap 'rm -f "$pf"' EXIT; gen_closure_audit_prompt > "$pf"
    ca_output=$(run_step "closure-audit" "请独立验证 $MODULE 的计划完成度" "$pf")

    if has_status "$ca_output" "CLOSURE_RESULT: COMPLETE"; then
      log "[验证] 独立验证通过，计划完成"
      plan_complete=true
      break
    fi

    # ── 4c. 未完成 → 提取 REMAINING XML → 重试 ──
    log "[验证] 计划未完成，提取未完成项 ..."
    remaining_xml=$(echo "$ca_output" | sed -n '/<REMAINING>/,/<\/REMAINING>/p')
    if [[ -n "$remaining_xml" ]]; then
      local_item_count=$(echo "$remaining_xml" | grep -c '<item>' || true)
      log "[验证] 发现 $local_item_count 个未完成项，进入下一轮内循环"
    else
      remaining_xml="<REMAINING><item>未完成项未知（closure audit 未输出 REMAINING 块），请重新检查计划</item></REMAINING>"
      log "[验证] 未找到 REMAINING XML 块，使用默认提示"
    fi
  done

  if [[ "$plan_complete" != "true" ]]; then
    log "[警告] 内循环 $MAX_INNER_CYCLES 轮后计划仍未完成，进入下一轮外循环重新审计"
  fi

  # ── 5. 构建验证 ──
  log "[构建验证] 全量构建和测试 ..."
  if ! run_build; then
    log "[构建验证] 失败，下一轮外循环重试"
    continue
  fi

  log "╚══ Outer cycle $outer 完成，下一轮将重新审计 ══"
done

log "=== FAIL: $MODULE 超过最大外循环次数 $MAX_CYCLES ==="
echo "$MODULE:MAX_CYCLES_REACHED" > "$STATE_DIR/done-$MODULE"
exit 2
