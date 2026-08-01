# 3 BAIL 中断语义（Middleware 第三态：中断并丢弃响应）

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Draft Consensus: 2 轮独立子 agent 对抗性审查通过（r1 修 5 Major+3 Minor；r2 CONSENSUS yes，4 项非阻断 Minor 已并入）
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W5-3；`ai-dev/analysis/agent-survey/2026-08-01-parlant-conversation-control-analysis.md` §2.3/§三.5；`ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §2.5
> Mission: nop-ai-agent-harness-evolution
> Work Item: W5-3
> Related: W5-1 GuardrailTestSuite（successor，攻击语料验收）、W5-2 Guideline 关系图（规则建模）

## Supersession Note

`nop-ai-agent-middleware-design.md` §三（line 156）现写「**不修改** HookResult 密封层级」。该约束的 scope 是 plan 296 的会话级洋葱链实现（在**已有**返回态上启用链式拦截），**非永久禁令**。本计划**显式 supersede 该约束**：新增 `BailResult`（第四返回态）是 W5-3 的核心交付物，Phase 1 裁定 C 会把 design §三:156 更新为"允许 BailResult 作为显式文档化的扩展"。附注：核对 live `HookResult.java` 实为**非 sealed 抽象类 + package-private 构造**（无 `sealed`/`permits`），BailResult 须作为 `HookResult.java` 内的静态嵌套子类（受 package-private 构造可见性约束）。

## Purpose

为中间件/Hook 增加统一的"硬阻断"返回语义 BAIL（中断并**不作用于此响应**）——把今日**硬编码**在执行器里的"丢弃输出 + 重新提示"（`ReActAgentExecutor:731-734` 的 `promptAssembly.checkOutputGuardrail`）**泛化**为中间件/Hook 可返回的标准态 `BailResult`，使任意 guardrail 中间件（而非仅 promptAssembly）能触发硬阻断，并补 `POST_CALL` 的最终响应阻断。区别于 Veto（PRE 侧拒绝、core 不执行）与"改写式"拦截（修改响应内容）。本计划交付 `BailResult`（HookResult 第四态）+ POST 侧消费接线（修复当前返回值丢弃缺陷）+ 防循环保护。

## Current Baseline

基于 live repo（`nop-ai/nop-ai-agent/`）核对：

- **HookResult（已有，三态）**：`HookResult`（hook 包）是**非 sealed 抽象类 + package-private 构造**（无 `sealed`/`permits` 修饰），三个静态嵌套子类：`PassResult`（单例）、`VetoResult(reason)`、`ReenterResult(message)`；方法 `isPass()`/`isVeto()`/`isReenter()`。**无第四态**。package-private 构造意味着 BailResult 必须是 `HookResult.java` 内的静态嵌套子类。
- **Veto 语义（已有，PRE 侧）**：`ReActAgentExecutor` 在 `PRE_CALL`（`:383-384`）与 `PRE_REASONING`（`:504-505`）**检查** `isVeto()` 并中止。Veto = 该生命周期点拒绝、core 不执行。
- **POST_REASONING 已有硬编码"丢弃输出+重新提示"（关键遗漏项）**：`ReActAgentExecutor:731-734` 在 `POST_REASONING`（`:729`）**紧接其后**有：
  `if (promptAssembly.checkOutputGuardrail(ctx, assistantMsg)) { ctx.setCurrentIteration(+1); continue; }`
  即一个**已存在的、语义高度重叠**的"丢弃该轮输出 + 重新提示"机制，但它**硬编码在执行器里、仅 promptAssembly 可触发**，不是中间件返回态。本计划的 BAIL-at-POST_REASONING 与之重叠——Phase 1 必须裁定二者关系（见裁定 A）。
- **POST_REASONING 触发时消息已入历史（执行顺序现实）**：`POST_REASONING`（`:729`）触发**前**已完成（`:680-722`）：assistant 消息**已加入** `ctx.getMessages()`、LLM_TURN checkpoint **已保存**、session store **已持久化**、token 计账已完成。故 POST_REASONING BAIL **不能承诺"不进入历史"**——与 checkOutputGuardrail 一样，BAIL 是"不作用于该响应 + 重新提示"（消息已落账，但 agent 不执行其 tool_calls、不视为最终答案）。
- **POST 侧中间件返回值被丢弃（已有缺陷，Anti-Hollow 红线）**：`POST_REASONING`（`:729`）与 `POST_CALL`（`:946`）的 `executeWithMiddleware(...)` 返回值**未捕获、未检查**——直接丢弃。即便加 BailResult 也不生效（须同时接线）。注意：本计划的 Anti-Hollow 修复**仅针对 BAIL**（`isBail()`）；Veto/Reenter 在 POST 点的消费是独立问题（见 Non-Goals），今日 POST 侧 hook 均返回 PassResult（Phase 1 裁定 F 确认）。
- **Reenter 语义（已有）**：仅在 `BEFORE_TOOL_RESULT_PROCESSED`/`AFTER_TOOL_RESULT_PROCESSED` 有效，带重入计数器防死循环（middleware §2.5）。
- **执行级 veto 防循环（已有，可参照）**：`LlmCallCoordinator.MAX_EXECUTION_VETOES = 3`（`LlmCallCoordinator.java:776`，middleware §5.1 D3）。**但更重要**：POST_REASONING 的 re-prompt（含 checkOutputGuardrail 触发的）**本就受 `maxIterations` 上界约束**（checkOutputGuardrail 的 `continue` 消耗 iteration）——故无真无限循环；bail cap 是 BAIL 专属的"早 fail"（在烧完 maxIterations 前快速失败），非唯一循环防线（见裁定 D）。
- **POST_CALL 时序（执行已结束）**：`POST_CALL`（`:946`）在循环退出后、`publishEvent(EXECUTION_COMPLETED)` 前后触发，之后 `execute()` 返回 `AgentExecutionResult.fromContext(ctx)`。此时响应可能已经 REASONING_CHUNK **流式发出**——"丢弃最终响应"对流式场景语义受限（见裁定 E）。
- **真正剩余的 gap**：
  - 无 `BailResult`（第四态）；"丢弃输出+重新提示"硬编码在执行器（checkOutputGuardrail），非中间件可返回的标准态。
  - `POST_REASONING`/`POST_CALL` 返回值未消费（即便加 BailResult 也不生效）。
  - 无 BAIL 专属的 bail cap 早-fail（maxIterations 是兜底上界，非 BAIL 专属）。
  - design §三:156 "不修改 HookResult 密封层级"与本计划冲突，须 supersede。

## Goals

- 新增 `HookResult.BailResult`（第四态，`HookResult.java` 内静态嵌套子类）+ `isBail()`，向后兼容（既有 isPass/isVeto/isReenter 语义不变）。
- 把"丢弃输出+重新提示"从**执行器硬编码**（`checkOutputGuardrail:731-734`）**泛化为中间件可返回的标准态** BAIL（按裁定 A 决定二者关系），使任意 guardrail 中间件能触发硬阻断。
- 消费侧接线：`POST_REASONING`/`POST_CALL` 的 `executeWithMiddleware` 返回值被捕获并检查 `isBail()`（修复当前 BAIL 维度的丢弃缺陷）。
- BAIL 语义落地：POST_REASONING BAIL → **不作用于该轮响应**（不执行其 tool_calls、不视为最终答案）+ 重新提示（承认消息已落账，与 checkOutputGuardrail 同语义边界）；POST_CALL BAIL → 最终结果被标记为 guardrail 阻断（按裁定 E 处理流式已发场景），调用方得到显式阻断状态。
- BAIL 专属 bail cap 早-fail（参照 `MAX_EXECUTION_VETOES`）+ maxIterations 兜底上界（裁定 D 厘清与 checkOutputGuardrail re-prompt 的循环边界）。
- 零回归：无 BailResult / 无 BAIL 中间件时，POST_REASONING/POST_CALL 行为与今日一致。

## Non-Goals

- **不做** W5-1 GuardrailTestSuite（60+ 攻击语料 + AttackPlugin + Grader）——独立 successor work item。
- **不做** W5-2 Guideline 依赖/排除关系图——独立 work item（规则关系建模）。
- **不改变** `AgentLifecyclePoint` 枚举的 12 个值与语义（BAIL 是已有 POST 点上的新返回态，非新点）。
- **不改变** Veto/Reenter/Pass 的既有语义与既有消费点（PRE_CALL/PRE_REASONING 的 Veto 处理不变）。
- **不修复** Veto/Reenter 在 POST 点的丢弃——本计划的 Anti-Hollow 修复**仅 BAIL 维度**（`isBail()`）；Veto/Reenter 在 POST 点的消费是独立问题（Phase 1 裁定 F 确认今日 POST 侧 hook 均返 Pass，零回归成立）。
- **不实现** "改写式"拦截到 BAIL 的迁移——改写与 BAIL 共存，本计划只新增 BAIL。
- **不做** BAIL 的可观测面板/指标上报系统（仅产出阻断结果 + LOG；面板是 observability 增强）。
- **不扩展** BAIL 到执行级 `ExecutionPoint`（首版仅会话级 POST_REASONING/POST_CALL；执行级是独立增强）。
- **不承诺** POST_REASONING BAIL "不进入历史"——消息/checkpoint/session-store 在 POST_REASONING 前已落账（执行顺序现实），BAIL 是"不作用 + 重新提示"非"未发生"。

## Scope

### In Scope

- `HookResult.BailResult`（第四态，含 reason）+ `isBail()`；向后兼容的构造/判定。
- `POST_REASONING`/`POST_CALL` 消费侧接线：捕获并检查返回值（修复丢弃缺陷）。
- BAIL 在两点的语义实现：POST_REASONING（丢弃该轮响应 + 阻断结果 + re-prompt bail cap）、POST_CALL（丢弃最终响应 + 显式阻断结果给调用方）。
- 防无限循环：POST_REASONING bail cap，超限 fail-loud（`NopAiAgentException`，不静默 continue）。
- design `nop-ai-agent-middleware-design.md` §2.5 回写 BAIL 语义；guardrail-contract.md 记录 BAIL 作为硬阻断手段。

### Out Of Scope

- 执行级 `ExecutionPoint` 的 BAIL（首版会话级）。
- BAIL 触发的自动降级/重试策略编排（首版 BAIL 即阻断 + 显式结果；编排是 guardrail 策略层）。
- W5-1/W5-2 的攻击语料与规则关系建模。

## Execution Plan

### Phase 1 - 设计裁定：BAIL 语义、与 checkOutputGuardrail 关系、防循环

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §2.5 + §三:156（supersede）；`guardrail-contract.md`

- Item Types: `Decision`

- [x] **裁定 A — BAIL 与 `checkOutputGuardrail`（:731-734）关系**（共存：BAIL 在前，BAIL 命中则跳过 checkOutputGuardrail）——裁定结论见 design §5.4 裁定 A
- [x] **裁定 B — POST_REASONING BAIL 语义**（不作用 + 重新提示，`setCurrentIteration(+1)` 对齐 checkOutputGuardrail，`reason` 仅日志）——裁定结论见 design §5.4 裁定 B
- [x] **裁定 C — supersede design §三:156**（允许 BailResult 作为第四态扩展）——design §三:156 已更新，§2.5/§5.4 回写
- [x] **裁定 D — bail cap 范围**（cap=3，per-request，不与 checkOutputGuardrail 共享计数）——裁定结论见 design §5.4 裁定 D
- [x] **裁定 E — POST_CALL BAIL 机制**（`AgentExecutionContext`/`AgentExecutionResult` 新增 `bailReason`；EXECUTION_COMPLETED payload 加 `guardrailBlocked` 标记；流式已发限制显式接受）——裁定结论见 design §5.4 裁定 E
- [x] **裁定 F — 零回归确认（Veto/Reenter 在 POST 点）**（POST 侧既有 hook 均返 Pass，零回归成立）——裁定结论见 design §5.4 裁定 F

Exit Criteria:

- [x] middleware-design §2.5 回写 BAIL 最终语义（含 A–F 六条裁定结论 + 拒绝的替代方案，如"为什么不复用 Veto 做 POST 侧丢弃"）
- [x] middleware-design §三:156 已按裁定 C 更新（supersede "不修改 HookResult 密封层级"），与 Supersession Note 一致
- [x] guardrail-contract.md 记录 BAIL 作为硬阻断手段，与 Veto/改写/checkOutputGuardrail 并列分类
- [x] design 描述的 BAIL 语义、与 checkOutputGuardrail 关系、bail cap 范围、POST_CALL 流式限制可在 Phase 2/3 直接落地（无臆想空间）
- [x] **无静默跳过**：design 明确 BAIL 超限 fail-loud、BAIL 在非 POST 点 fail-loud，不静默忽略（见 Minimum Rules #24）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - BailResult 与 POST 侧消费接线

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/HookResult.java`（新增 BailResult 静态嵌套子类）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（POST_REASONING:729 / POST_CALL:946 / checkOutputGuardrail:731-734 / EXECUTION_COMPLETED:948）；`AgentExecutionResult` + `AgentExecutionContext`（按裁定 E 加阻断字段）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentHookInvoker.java`

- Item Types: `Fix | Proof`

> Item Types 含 `Fix`：POST 侧返回值丢弃是已确认的 Anti-Hollow 缺陷（middleware §5.1 D4 红线），本 Phase 修复其 BAIL 维度。

- [x] 新增 `HookResult.BailResult(reason)` 第四态（`HookResult.java` 内静态嵌套子类，受 package-private 构造约束）+ `isBail()`；既有 `isPass()`/`isVeto()`/`isReenter()` 语义不变（向后兼容）
- [x] `POST_REASONING`（`:729`）：捕获 `executeWithMiddleware` 返回值 + 检查 `isBail()`，按裁定 A/B 处理与 `checkOutputGuardrail`（:731）的顺序/关系；BAIL → 不作用于该轮响应 + 重新提示（按裁定 B 对齐 iteration 计数）
- [x] `POST_CALL`（`:946`）：捕获返回值 + 检查 `isBail()`，按裁定 E 在 `AgentExecutionResult.fromContext` 反映阻断状态（处理流式已发限制）
- [x] BAIL 在非 POST_REASONING/POST_CALL 点返回时：fail-loud（显式错误"BAIL 仅在 POST 点有效"，不静默忽略）—— `AgentHookInvoker.validateBailPoint` + 执行级中间件 fail-loud
- [x] POST_REASONING bail cap 实现（per-request 计数 + 超限 fail-loud，按裁定 D 范围）

Exit Criteria:

- [x] BailResult 是 HookResult 的第四子类；`isBail()` 对 BailResult 返回 true、对其余三态返回 false
- [x] POST_REASONING/POST_CALL 的返回值不再被丢弃（单测断言：返回 BailResult 时两点的行为按语义改变）
- [x] POST_REASONING BAIL：该轮响应按裁定被丢弃 + 产出阻断结果；连续 BAIL 触达 cap 时 fail-loud（不静默 continue、不无限循环）
- [x] POST_CALL BAIL：`AgentExecutionResult` 反映阻断状态（按裁定 E；流式已发限制已显式处理/接受）
- [x] BAIL 与 `checkOutputGuardrail`（:731）关系按裁定 A 落地（顺序/优先级/是否迁移），二者不冲突
- [x] BAIL 在非 POST 点 fail-loud（单测覆盖）
- [x] **接线验证**：POST_REASONING/POST_CALL 确消费 BailResult（断言返回 BailResult 时两点行为改变，而非仅类型存在，见 Minimum Rules #23）——这是修复"返回值丢弃"Anti-Hollow 红线（BAIL 维度）的关键验证
- [x] **无静默跳过**：超 cap / 非 POST 点 BAIL 均 fail-loud，无空体/吞异常/静默 continue（见 Minimum Rules #24）
- [x] 向后兼容：既有 HookResult 测试 + POST 侧中间件/hook 测试全过（无 BailResult 时两点行为与今日一致；裁定 F 的零回归确认成立）
- [x] 新增 BailResult/POST 接线有单测：POST_REASONING BAIL（不作用+重新提示）/ POST_CALL BAIL（结果阻断状态）/ 非 POST 点 BAIL fail-loud / cap 超限 fail-loud / 无 BAIL 向后兼容 五条（见 Minimum Rules #25）
- [x] design §2.5 与落地一致；§三:156 supersede 已落地
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端 guardrail 阻断验证

Status: completed
Targets: 端到端 guardrail→BAIL→阻断结果路径；`nop-ai-agent-middleware-design.md`、`guardrail-contract.md`

- Item Types: `Proof`

- [x] 端到端集成：一个 POST_REASONING 中间件检测到违规内容 → 返回 BailResult → 该轮响应不被作用（不执行 tool_calls）+ 重新提示 + cap 保护（与 `checkOutputGuardrail` 关系按裁定 A 验证）
- [x] 端到端集成：一个 POST_CALL 中间件检测到最终响应违规 → 返回 BailResult → `AgentExecutionResult` 反映阻断状态（按裁定 E）
- [x] guardrail 场景验证：BAIL（POST 不作用+重新提示/结果阻断）与 Veto（PRE 拒绝）、改写（修改）、`checkOutputGuardrail`（硬编码丢弃）四者在同一测试中语义可区分

Exit Criteria:

- [x] **端到端验证**（见 Minimum Rules #22）：从中间件 `execute(ctx, next)` 返回 BailResult → `POST_REASONING`/`POST_CALL` 消费 → 响应不被作用/结果阻断，完整路径有集成测试
- [x] **接线验证**：BailResult 确经 `MiddlewareChain.proceed()` 返回到 `ReActAgentExecutor` 并被 `isBail()` 消费（断言中间件返回 BailResult 时执行器行为改变，见 Minimum Rules #23）
- [x] **无静默跳过**：端到端路径中阻断状态显式可见（LOG + 结构化结果），无静默丢弃/空返回（见 Minimum Rules #24）
- [x] 新增端到端测试覆盖 POST_REASONING 与 POST_CALL 两条 BAIL 路径（见 Minimum Rules #25）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] design §2.5（含 §三:156 supersede）/ guardrail-contract.md 与落地一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划涉及代码变更，构建验证条目必填。

- [x] BailResult（第四态，HookResult 静态嵌套子类）已落地 + 向后兼容（既有三态语义不变）
- [x] POST_REASONING/POST_CALL 返回值丢弃缺陷已在 BAIL 维度修复（返回值被捕获检查 `isBail()`）
- [x] BAIL 与 `checkOutputGuardrail`（:731）关系已按裁定 A 落地（无冲突、语义对齐）
- [x] BAIL 语义在两点生效：POST_REASONING（不作用+重新提示）、POST_CALL（结果阻断状态）
- [x] bail cap（BAIL 专属早-fail）+ maxIterations 兜底已落地 + 超限 fail-loud（裁定 D 范围明确）
- [x] 端到端 guardrail→BAIL→阻断已验证（Anti-Hollow：中间件返回 BailResult 确被执行器消费，运行时连通）
- [x] 零回归：既有 HookResult/middleware/POST 侧测试全过；无 BailResult 时行为不变（裁定 F）
- [x] design §2.5 已回写 BAIL 最终语义；§三:156 supersede 已落地；guardrail-contract.md 记录 BAIL
- [x] 受影响 owner docs 已同步
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 BailResult 被 POST_REASONING/POST_CALL 运行时消费（非仅类型存在），且 POST 侧返回值丢弃缺陷确在 BAIL 维度修复
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 执行级 `ExecutionPoint` 的 BAIL

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首版会话级 POST_REASONING/POST_CALL BAIL 即可使 guardrail 硬阻断端到端可用；执行级（PRE/POST_LLM_ATTEMPT、PRE/POST_TOOL_ATTEMPT）的 BAIL 是独立维度（attempt 级丢弃），与本计划正交。
- Successor Required: yes
- Successor Path: 执行级 BAIL successor（依赖 middleware §5.1 执行级 scope）

### BAIL 触发的自动降级/重试策略编排

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 BAIL 即阻断 + 显式结果；自动降级（BAIL→换模型/换策略）是 guardrail 策略编排层增强。
- Successor Required: no

## Non-Blocking Follow-ups

- BAIL 可观测性指标（BAIL 触发率、cap 触达率、阻断分布）——observability 增强。
- W5-1 GuardrailTestSuite 对 BAIL 路径的攻击语料验收——独立 work item。

## Closure

Status Note: W5-3 BAIL 中断语义全部落地。新增 `HookResult.BailResult`（第四态）+ `isBail()`，把"丢弃输出+重新提示"从执行器硬编码（`checkOutputGuardrail`）泛化为中间件可返回的标准态。POST 侧返回值丢弃缺陷（Anti-Hollow 红线）在 BAIL 维度修复：POST_REASONING/POST_CALL 返回值现被捕获检查 `isBail()`。六条裁定 A–F 全部落地。无 BailResult 时行为与今日一致（裁定 F 零回归）。所有 in-scope 项已 landed，无 deferred live defect。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，task ses_0414bd3e5ffeu6Rx98uyimPUjC，read-only）
- Audit Session: ses_0414bd3e5ffeu6Rx98uyimPUjC
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）：
    - Phase 1：design §2.5 回写 BAIL 语义、§5.4 A–F 裁定、§三:156 supersede、guardrail-contract 增量 3 标 final——全 PASS（live 文档核对）
    - Phase 2：`HookResult.BailResult`（`HookResult.java:80-90`）+ `isBail()`（`:25-27`）；POST_REASONING 捕获检查（`ReActAgentExecutor.java:750,758`）+ bail cap（`:761`）+ checkOutputGuardrail 跳过（BAIL 分支在 `:773` 前 continue）；POST_CALL 捕获检查（`:988,996`）+ `ctx.setBailReason`（`:999`）+ payload `guardrailBlocked`（`:1012`）；`validateBailPoint`（`AgentHookInvoker.java:212-219`）覆盖 middleware+hook 两路径 + 执行级 fail-loud（`:133-137`）；bail 字段（`AgentExecutionResult.java:20,71,115` + `AgentExecutionContext.java:71,343,347`）——全 PASS
    - Phase 3：端到端测试（`TestBailEndToEnd.java` 4 测试，含 `wiringProofBailResultIsConsumedByExecutor` 断言 `toolCallCount==0` 证明返回值确被消费）——PASS
  - 每条 Closure Gate 的验证结果：全 PASS（见 Closure Gates checklist 全 `[x]`）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果：BailResult 运行时确被 POST_REASONING/POST_CALL 消费（`wiringProofBailResultIsConsumedByExecutor` 断言 tool_calls 跳过；POST_CALL bailReason 流入 result）；无静默跳过（非 POST 点 fail-loud、cap 超 fail-loud，测试断言 `AgentExecStatus.failed` + error 含 "bail cap"）；`scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码为 0（W5-3 文件无 high 发现）
  - 构建验证：`./mvnw compile -pl nop-ai/nop-ai-agent -am -q` exit 0；`./mvnw test -pl nop-ai/nop-ai-agent -am` → 3216 tests, 0 failures, 0 errors；`./mvnw clean install -DskipTests -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS
  - Deferred 项分类检查：两项 Deferred（执行级 BAIL = out-of-scope improvement；自动降级/重试编排 = optimization candidate）均带 non-blocking 理由，无 in-scope live defect 被降级

Follow-up:

- 执行级 `ExecutionPoint` 的 BAIL（successor，依赖 middleware §5.1 执行级 scope）
- BAIL 可观测性指标（observability 增强，non-blocking）
- W5-1 GuardrailTestSuite 对 BAIL 路径的攻击语料验收（独立 work item，non-blocking）
