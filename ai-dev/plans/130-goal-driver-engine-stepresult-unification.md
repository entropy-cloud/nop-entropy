# 129 Goal Driver Engine StepResult Unification

> Plan Status: completed
> Last Reviewed: 2026-06-10
> Source: `ai-dev/design/opencode-goal-driver/flow-engine-design.md` (v7)
> Related: `ai-dev/tools/opencode-goal-driver/` (src/ and test/)

## Purpose

将 FlowEngine 的步骤执行器重构为统一返回 `StepResult { marker, text?, vars?, ok }`，引擎不再做类型特定的 marker 提取（消除 `resolveMarker`），每个步骤类型内部自行解析 marker 和 vars。

## Current Baseline

- `engine.js` 有 `_resolveMarker()` 方法，根据 `stepDef.type` 做不同提取：tool 看 `ok`，script 看 `result.marker`，agent 解析 XML tag + parse agent fallback + correction，group/subflow 直接取 `result.marker`
- `_executeToolStep()` 返回 delegates 的原始结果 `{ ok, logFile }`，无 marker，无 vars。executor.js 对非零退出码和 spawn error 都返回 `{ ok: false }`，无法区分
- `_executeAgentStep()` 返回 `{ text, ok, sessionId }`。`<FLOW_VARS>` 在 `_executeAgentStep` 内部提取并直接写入 `this.flowVars`（不在主循环中）。marker 由引擎主循环的 `_resolveMarker` 提取（包括 XML tag、parse agent fallback、correction retry）
- `_executeScriptStep()` 返回 `{ ok, marker, text }`，支持 `ret.vars`。Wrapper `_executeScriptStepWithOverride` 先委托给 `delegates.runScript`（可能返回旧格式），再 fallback 到 `_executeScriptStep`
- `_executeGroupStep()` 返回 `{ ok, marker, text }`，无 vars 传播。内部 L279 有 `if (!result.ok && subDef.type !== "tool")` 硬编码。L291 调用 `_resolveMarker`
- `_executeSubflowStep()` 返回 `{ ok, marker, text }`。`_runChildSubflow` 通过 `this.flowVars.set()` 旁路写入变量。forEach 模式下每个子运行都旁路写入
- 引擎主循环 `run()` L465 有 `if (!result.ok && stepDef.type !== "tool")` 硬编码
- 测试覆盖：49 个测试
- 设计文档 v7 已定稿

## Goals

- 每个步骤执行器返回统一格式的 `StepResult { marker, text?, vars?, ok }`
- `StepResult.marker` 由步骤执行器内部解析（包括 agent 的 XML tag 解析、parse agent fallback、correction retry），引擎直接使用
- `StepResult.vars` 由步骤执行器内部提取，引擎统一合并到 flowVars
- 引擎主循环消除 `_resolveMarker()` 调用和 `stepDef.type !== "tool"` 硬编码
- 所有现有测试继续通过（行为不变，内部结构改变）

## Non-Goals

- 不改变 goal-driver.json 流程定义
- 不改变 prompt 文件内容
- 不改变 executor.js / runner.js / config.js / main.js / flow-loader.js
- 不改变外部调用者（delegates）的接口——mock delegate 返回格式不变

## Scope

### In Scope

- `engine.js` — 所有 `_executeXxxStep` 方法、`_executeScriptStepWithOverride`、`_runChildSubflow`、`run()` 主循环、`_resolveMarker()` 删除
- `engine.test.js` — 适配新结构，补全测试

### Out Of Scope

- prompt 文件、flow-loader.js、executor.js、runner.js

## Design Decisions

1. **Tool executor 始终返回 ok: true**：将 delegate 的 `{ ok: false }` 映射为 `{ marker: "fail", ok: true }`。理由：非零退出码是正常业务结果（build failed），不是致命错误。Spawn error 在当前架构下无法与退出码区分（executor.js 两者都返回 ok: false），作为 Non-Blocking Follow-up 记录
2. **Mock delegate 不变**：执行器负责将 delegate 返回值转换为 StepResult
3. **Marker correction 递归保护**：agent executor 内部使用独立辅助方法 `_runCorrectionAgent`，不调用 `_executeAgentStep` 自身
4. **Parse agent fallback 移入 agent executor**：当前在 `_resolveMarker` 中对 agent 类型调用 `delegates.runParseAgent`，一并移入 `_executeAgentStep`
5. **forEach subflow vars**：所有子运行的 childFlowVars 按 last-write-wins 聚合为 `Object.fromEntries(childEngine.flowVars)`
6. **Group vars 即时可见**：group 内部每执行一个子步骤后，立即将其 `result.vars` 合并到 `this.flowVars`（保持当前行为不变）。同时本地累加器跨所有轮次按 last-write-wins 聚合所有子步骤 vars，最终返回经所有轮次 last-write-wins 聚合的完整 vars。这确保子步骤间 vars 立即可见，且 group 返回的 StepResult.vars 包含完整聚合结果
7. **`_runCorrectionAgent` 签名和语义**：当 `_executeAgentStep` 提取到 marker 后，检查是否在 `stepDef.transitions` 中。不在则触发 correction：`_runCorrectionAgent(marker, resultText, resultTag, transitions, maxRetries, sessionId)` — 直接调用 `this.delegates.runAgent`（不调用 `_executeAgentStep` 自身）在同一 session 重新 prompt 要求输出有效 marker 值。`maxRetries` 取 `stepDef.onUnknownMaxRetries ?? 2`。correction prompt 包含 `"The value '${marker}' is not valid"` 消息和有效值列表。最多触发 `maxRetries` 次。correction 成功后返回修正后的 marker；仍无效则返回 `marker: null`
8. **`_executeScriptStepWithOverride` 包装**：若 `delegates.runScript` 返回 string → 包装为 `{ marker: ret, ok: true, vars: {}, text: String(ret) }`；若返回 object 且含 `marker` → 补全缺失字段（`vars: ret.vars || {}`, `ok: true`, `text: ret.text || String(ret.marker)`）。Script 步骤始终 ok: true（异常由 try/catch 捕获，走 onError）
9. **Phase 1 实施以本 Plan 的 Design Decisions 和 Checklist 为准**，design doc 将在 Phase 2 对齐。Design doc §2.3 agent executor 流程图当前缺少 correction 步骤，将在 Phase 2 补全

## Execution Plan

### Phase 1 - Unified StepResult Refactoring (atomic)

Status: completed
Targets: `ai-dev/tools/opencode-goal-driver/src/engine.js`, `ai-dev/tools/opencode-goal-driver/test/engine.test.js`

- Item Types: `Fix`, `Proof`

> **本 Phase 是原子操作**：executor 改动 + 主循环简化 + `_resolveMarker` 删除 + 测试适配必须一起完成，否则 vars 传播断裂或 marker 双重提取。

**executor changes:**

- [x] `_executeToolStep()`: 将 delegate 返回值转换为 `{ marker: delegate.ok ? "pass" : "fail", ok: true, vars: {}, text: delegate.logFile || "" }`
- [x] `_executeScriptStep()`: 函数返回 string → `{ marker, ok: true, vars: {}, text }`；返回 object with marker → `{ marker: ret.marker, ok: true, vars: ret.vars || {}, text: ret.text || String(ret.marker) }`
- [x] `_executeScriptStepWithOverride()`: 若 `delegates.runScript` 返回值，按 Design Decision #8 包装为 StepResult
- [x] `_executeAgentStep()`:
  - `<FLOW_VARS>` 提取（已有）改为放入返回的 `vars` 字段，不再直接写 `this.flowVars`
  - `<resultTag>` XML tag 解析（从 `_resolveMarker` 移入）
  - Parse agent fallback：无 tag 时调用 `delegates.runParseAgent`（从 `_resolveMarker` 移入）
  - Marker correction retry：使用独立辅助方法 `_runCorrectionAgent`（递归保护）
  - 返回 `{ marker, vars, ok, text }`
- [x] `_executeGroupStep()`:
  - L279 `if (!result.ok && subDef.type !== "tool")` 改为 `if (!result.ok)`
  - L291 `this._resolveMarker(result, subDef)` 改为直接使用 `result.marker`
  - 保留 `_tryAliasMarker` 归一化调用（L297-300 不变）
  - 每个子步骤执行后立即将其 `result.vars` 合并到 `this.flowVars`（保持当前行为）
  - 本地累加器按 last-write-wins 聚合 vars，最终返回 `{ marker, vars: 累加结果, ok, text }`
- [x] `_runChildSubflow()`: 返回 `{ childResult, childFlowVars: Object.fromEntries(childEngine.flowVars) }` 而非直接写入 `this.flowVars`
- [x] `_executeSubflowStep()`: 从 `_runChildSubflow` 获取 childFlowVars 放入 `vars` 字段。forEach 模式：所有子运行的 vars 按 last-write-wins 聚合

**engine main loop changes (same phase):**

- [x] `run()` 主循环：直接读 `result.marker`，删除 `_resolveMarker()` 调用
- [x] `run()` 主循环：添加 `if (result.vars) merge` 统一合并逻辑
- [x] `run()` 主循环：`ok` 检查统一为 `if (!result.ok)`（tool 已始终返回 ok: true，无需特殊处理）
- [x] 删除 `_resolveMarker()` 方法
- [x] 删除主循环中 marker correction 代码块（当前 L517-553：`markerCorrectionCounts`、case-insensitive fallback 循环、`_executeAgentStep` correction retry）——此逻辑已移入 `_executeAgentStep` + `_runCorrectionAgent`
- [x] 删除 `this.markerCorrectionCounts` Map 初始化（L43）

**test changes (same phase):**

- [x] 确保所有 49 个现有测试通过
- [x] 新增测试：group step 聚合子步骤 vars 到 StepResult.vars
- [x] 新增测试：subflow step 通过 StepResult.vars 传播变量（单个和 forEach）
- [x] 新增测试：agent marker correction（含 parse agent fallback）在 executor 内部完成
- [x] 新增测试：group 内包含 tool sub-step 时 ok:false 不触发 error 路径
- [x] 运行 `node --test ai-dev/tools/opencode-goal-driver/test/engine.test.js`，全部通过

Exit Criteria:

- [x] 每个 `_executeXxxStep` 返回 `{ marker, vars, ok, text }` 四字段
- [x] `_executeAgentStep` 包含完整 marker 提取链（XML tag + parse agent fallback + correction），不再依赖引擎的 `_resolveMarker`
- [x] `_executeAgentStep` 不再将 vars 直接写入 `this.flowVars`
- [x] `_executeGroupStep` 内部无 `subDef.type !== "tool"` 检查，使用 `result.marker` 而非 `_resolveMarker`
- [x] `_executeSubflowStep` 通过 `result.vars` 传播变量，`_runChildSubflow` 不再旁路写入
- [x] `_executeScriptStepWithOverride` 保证返回 StepResult 格式
- [x] `run()` 中无 `_resolveMarker` 调用、无 `stepDef.type` 条件分支（除 dispatch）
- [x] `_resolveMarker()` 方法已删除
- [x] 所有原有 49 个测试 + 新增测试通过
- [x] `node --test ai-dev/tools/opencode-goal-driver/test/engine.test.js` 退出码为 0

### Phase 2 - Update Design Doc to Match Implementation

Status: planned
Targets: `ai-dev/design/opencode-goal-driver/flow-engine-design.md`

- Item Types: `Follow-up`

- [ ] 确认 v7 设计文档与最终实现一致。如有偏差（如 tool executor 始终返回 ok: true 而设计文档 §2.3 写了 spawn error → ok: false），修正文档
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 设计文档中每个步骤执行器的描述与实际代码行为一致
- [ ] No owner-doc update required

## Closure Gates

- [x] 所有 `_executeXxxStep` 返回统一 `StepResult` 格式
- [x] 引擎 `run()` 中无类型硬编码和 `_resolveMarker` 调用
- [x] Marker correction 在 agent executor 内部完成，有递归保护
- [x] Vars 传播对所有步骤类型统一通过 `StepResult.vars`
- [x] `node --test ai-dev/tools/opencode-goal-driver/test/engine.test.js` 全部通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### Tool spawn error detection

- Classification: `watch-only residual`
- Why Not Blocking Closure: executor.js 对 spawn error 和非零退出码都返回 `{ ok: false }`，在 engine.js 层面无法区分。Tool executor 返回 `ok: true` + `marker: "fail"` 对所有现有行为正确（spawn error 也会被正确路由到 "fail" transition）。如果未来需要区分，需修改 executor.js 和 runner.js
- Successor Required: `no`

## Non-Blocking Follow-ups

- 如果未来需要区分 tool 的 spawn error 和非零退出码，需修改 executor.js 和 runner.js
- `_executeToolStep` 的 `_templateVar` 仅使用 `delegates.vars`，未合并 `flowVars`（现有 bug，非本 plan 引入）

## Closure

Status Note: Phase 1 (Unified StepResult Refactoring) completed. All 12 exit criteria verified by independent closure audit. Phase 2 (design doc update) remains as follow-up.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (explore agent, session ses_14dcdf0beffeLNSZQq12yyxyae)
- Evidence:
  - Exit Criteria: 12/12 PASS — all verified against live code
  - Closure Gates: 8/8 PASS — StepResult unification complete, no type hardcoding, no `_resolveMarker`, marker correction internalized with recursion protection, vars propagation unified
  - Anti-Hollow Check: CLEAN — no empty methods, no silent no-ops, no placeholders
  - Test Results: 54/54 pass (49 original + 5 new StepResult-specific tests)
  - Deferred item verified: "Tool spawn error detection" is watch-only residual, not an in-scope live defect

Follow-up:

- Phase 2: Update `ai-dev/design/opencode-goal-driver/flow-engine-design.md` to match implementation (§2.3 agent executor, tool executor ok:true semantics)
- `_executeToolStep` `_templateVar` only uses `delegates.vars`, not `flowVars` (pre-existing, non-blocking)
