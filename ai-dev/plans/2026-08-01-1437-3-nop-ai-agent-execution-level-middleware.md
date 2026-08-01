# 3 nop-ai-agent 双层中间件：执行级中间件（W3-1）

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W3-1；`ai-dev/analysis/agent-survey/2026-08-01-hive-dual-middleware-analysis.md`；`ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §5.1
> Mission: nop-ai-agent-harness-evolution
> Work Item: W3-1 双层中间件：执行级（每次工具/模型尝试，retry 时重新评估安全检查）
> Related: `296-nop-ai-agent-middleware-and-tool-tag-system-implementation.md`（前置，已完成）；`2026-08-01-1437-4-nop-ai-agent-declarative-filter-chain.md`（后继，依赖本计划建立 scope 概念）

## Purpose

将 nop-ai-agent 中间件从单层（每请求一次）升级为双层：在现有会话级（per-request）基础上新增**执行级**（per-attempt）中间件层，使每次 LLM/工具调用尝试都经过中间件拦截，**retry 时安全检查重新评估**（"resurrection retry" = 失败后重新尝试，包括 provider failover 后的切换重试）。这是 hive 双层中间件（PipelineStage + ExecutionMiddleware）揭示的核心增量——retry 时前一次尝试可能已改变状态，安全/熔断检查必须重跑。

## Current Baseline

> 以下事实基于 live repo 核对（2026-08-01），由 explore agent 验证。

- **会话级中间件已落地**（plan 296）：`IAgentMiddleware.execute(HookContext, MiddlewareChain)`（`middleware/IAgentMiddleware.java:43`），`MiddlewareChain`（`middleware/MiddlewareChain.java`，core = `Function<HookContext, HookResult>`，`proceed(HookContext)` at `:55`，空链直通 core 零开销）。
- **9 个生命周期点启用链式拦截**，经 `AgentHookInvoker.executeWithMiddleware(point, ctx, agentName, toolName, toolCallId)`（`engine/AgentHookInvoker.java:60-73`）内联构建链（`new MiddlewareChain(mws, 0, core)`），**不**经 `DefaultHookRegistry.buildChain`（后者仅测试用）。调用点：PRE_CALL（executor `:383`）、PRE_REASONING（`:504`）、POST_REASONING（`:729`）、POST_CALL（`:946`）、PRE_ACTING/POST_ACTING/BEFORE_/AFTER_TOOL_RESULT_PROCESSED（`AgentToolDispatcher`）、PRE_COMPACT（`AgentCompactionCoordinator`）。
- **LLM retry 循环无中间件集成**：`LlmCallCoordinator.doLlmCallWithRetry(...)`（`engine/LlmCallCoordinator.java:152-319`），`while(true)` retry at `:182`，per-attempt call `callChatWithTimeout` at `:185`。错误分类（W2e）/账号链（W2e-5）/provider failover（W2-4）均在循环内，但**中间件零介入**——仅 terminal failure 时 `invokeOnError`（`:327`）。PRE_REASONING/POST_REASONING（executor `:504`/`:729`）包裹**整个 retry 序列**，不包裹单次 attempt。
- **工具执行无 retry**：`AgentToolDispatcher.executeAllowedCalls(...)`（`engine/AgentToolDispatcher.java:176`）fan-out 循环（`:204`），无 retry loop；超时用 `CompletableFuture.orTimeout`（`:224`）→ `.exceptionally` 产错误结果。PRE_ACTING/POST_ACTING 包裹**整个 dispatch**，不包裹单个工具调用。
- **无 per-attempt 抽象**：`reentryCounters`（dispatcher `:185`）per-iteration 重置；`DEFAULT_MAX_REENTRIES = 3`（executor `:129`）。所有中间件均为 per-request scope。
- **agent.xdef**（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef:54-62`）声明 `<middlewares><middleware impl="!class-name" point="!string"/></middlewares>`，无 scope / 执行级概念。
- **已落地的相关能力**（正交，本计划复用不重做）：ThresholdBreaker 熔断（plan 210）、错误分类信号通路（W2e）、provider failover（W2-4）。这些是执行级**决策**，本计划补的是执行级**结构**。
- **真正 gap**：retry 时中间件不重新评估——attempt N 改变的状态（如工具调用消耗的配额、注入的 prompt）对 attempt N+1 的安全检查不可见。单层 per-request 中间件无法表达"每次尝试都检查"。

## Goals

- 执行级中间件在**每次 LLM 调用尝试**（retry loop 内每次 `callChatWithTimeout`）前后触发
- 执行级中间件在**每次工具调用尝试**（dispatch 内每个工具调用）前后触发
- **retry/resurrection 时执行级中间件重新评估**（安全/熔断检查重跑）——这是本计划的核心价值
- 会话级（现有 9 点）与执行级（新增）双层共存，零回归
- 声明式：agent.xdef 可声明中间件所属 scope（会话级 / 执行级）

## Non-Goals

- **不为工具执行新增 retry 机制**——执行级中间件对每个当前工具调用触发一次；未来若引入工具 retry，执行级中间件自然按 per-attempt 重新评估
- **不替换会话级中间件**——执行级是新增层，现有 9 点行为不变
- **不做声明式 filter chain 组装**（有序 ID 列表 + input/output 双链）——后继 plan `2026-08-01-1437-4`（W3-2）
- **不修改 `HookResult` 密封层级、不删除 `IAgentLifecycleHook`、不修改现有 `AgentLifecyclePoint` 枚举的已有 12 值语义**
- **约束裁定**：设计文档 §三（plan 296 写入）声明"不修改 `AgentLifecyclePoint` 枚举值"——该约束的 scope 是 plan 296 的洋葱链实现（在**已有**点上启用链式拦截）。W3-1 执行级中间件**天然需要新的触发时刻**（retry loop 内 per-attempt、工具 dispatch 内 per-tool-call，这些时刻当前无生命周期点）。本 plan 明确覆盖该约束：执行级触发点的建模是 W3-1 的核心交付物（Decision D1），Phase 3 将设计文档 §三约束更新为区分"会话级已有 12 点不变 + 执行级新增触发点"
- **不为 ON_ERROR / REASONING_CHUNK / POST_COMPACT 增加链式支持**——plan 296 follow-up，独立项
- 不引入字节级传递（nop 保持对象级类型化）

## Scope

### In Scope

- 执行级中间件 scope 概念建模（Decision D1）
- 执行级中间件在 LLM retry loop 内的触发（before/after each attempt）
- 执行级中间件在工具 dispatch 内的触发（before/after each tool call）
- retry 时重新评估的验证（执行级中间件在 attempt N+1 确实重新执行）
- agent.xdef 声明执行级中间件 scope
- `DefaultAgentEngine` / `AgentExecutorResolver` 装配路径
- 设计文档 §5.1 从方向性描述重写为最终架构决策

### Out Of Scope

- 工具 retry 机制（执行级中间件不依赖 retry 存在）
- 声明式 filter chain（W3-2 后继）
- ON_ERROR/REASONING_CHUNK/POST_COMPACT 链式（独立 follow-up）
- 跨 session / 跨进程执行级中间件状态共享

## Execution Plan

### Phase 1 — 执行级中间件契约与 scope 建模

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/middleware/`、`hook/`、`engine/AgentHookInvoker.java`、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`

- Item Types: `Decision | Proof`

**Decision D1**：执行级 scope 如何建模。

待裁定方向（执行时基于 live code 做最终裁定）：
- 方案 A：新增执行级生命周期点（如 `PRE_LLM_ATTEMPT`/`POST_LLM_ATTEMPT`/`PRE_TOOL_ATTEMPT`/`POST_TOOL_ATTEMPT`），复用现有 `IAgentMiddleware` 接口 + `MiddlewareChain`，中间件按 point 注册。最小接口改动，但 `AgentLifecyclePoint` 枚举需新增值。
- 方案 B：在中间件声明上增加 `scope` 属性（`session` | `execution`），会话级中间件走现有 9 点，执行级中间件走新的执行级触发点。复用接口，声明层区分。
- 方案 C：新增 `IExecutionMiddleware` 接口，与 `IAgentMiddleware` 平行。

裁定约束：
- 必须保持现有 9 点会话级中间件**零行为变更**（plan 296 既有测试全过）
- 必须使 retry loop 内的触发点**可注入、可测试**（不能硬编码在 coordinator 内部不可 mock）
- 倾向最小接口面积方案（nop 一贯风格：复用而非平行新建）

**Decision D2**：执行级中间件的 attempt 级上下文。

执行级中间件触发时需要 attempt 级信息（当前 attempt 编号、是否 retry、上次 attempt 的结果/错误分类）。当前 `LlmCallCoordinator` 的 `attempt` 变量是局部变量（`:182` 循环内），不暴露到 `HookContext` 或 `AgentExecutionContext`。`HookContext`（`hook/HookContext.java`）当前有 `lifecyclePoint`/`executionContext`/`data`(Map)/`toolName`/`toolCallId`。

裁定方向（执行时做最终选择）：
- 方案 a：`HookContext.data` Map 放 attempt 级键值（弱类型但零接口改动）
- 方案 b：`AgentExecutionContext` 新增 `getCurrentAttempt()`/`isRetry()`/`getLastAttemptResult()` 方法（强类型但改动面较大）

约束：attempt 编号、retry 标志、上次 attempt 错误分类三者必须可被中间件读取；倾向强类型方案 b（与 nop 风格一致），除非改动面评估后过大。

**Decision D3**：执行级中间件 Veto 与 retry loop 控制流的交互语义。

**核心问题**（review B1 发现）：当前 retry loop（`LlmCallCoordinator:182-313`）完全由 `retryPolicy.shouldRetry()` 返回的 `RetryOutcome`（RETRY/STOP/FALLBACK）驱动。执行级中间件返回 `HookResult.VetoResult` 是不同类型，当前 loop 无映射路径。

裁定约束（Veto 语义必须明确定义）：
- 执行级中间件在 **PRE_LLM_ATTEMPT** 返回 Veto → 该 attempt 视为失败 → 进入 retry 决策路径（由 retryPolicy 决定 RETRY/STOP/FALLBACK），**不是无条件 retry**（防无限循环：Veto + retryPolicy STOP 则终止）
- 执行级中间件在 **POST_LLM_ATTEMPT** 返回 Veto → 拒绝该 attempt 的结果 → 同上进入 retry 决策路径
- 执行级中间件 Veto 在**工具调用**侧 → 该工具调用产错误结果（不 retry 工具，因为工具无 retry 机制），不影响同 batch 其他工具调用
- Veto 必须有计数上限保护（复用 `DEFAULT_MAX_REENTRIES` 机制或新增执行级 Veto 上限），防执行级中间件无限 Veto 导致无限 retry

**D1 裁定（采纳方案 B，强化为独立 ExecutionPoint 枚举）**：在 `<middleware>` 声明上增加 `scope` 属性（默认 `session`，零回归）。会话级中间件走现有 9 个 `AgentLifecyclePoint`（不变）；执行级中间件走**新建的独立 `ExecutionPoint` 枚举**（`PRE_LLM_ATTEMPT`/`POST_LLM_ATTEMPT`/`PRE_TOOL_ATTEMPT`/`POST_TOOL_ATTEMPT`）。**复用** `IAgentMiddleware` 接口 + `MiddlewareChain`（接口零改动），声明层 + registry 存储 scope 维度区分。
- 拒绝方案 A（向 `AgentLifecyclePoint` 加执行级值）：会把两种 scope 的概念混入同一枚举，违反"已有 12 值语义不变"约束且语义混乱。
- 拒绝方案 C（平行新接口 `IExecutionMiddleware`）：nop 风格倾向复用而非平行新建，执行级与会话级的执行模型（洋葱链）完全相同，无需新接口。
- registry 用 scope 维度分离存储：会话级 `Map<AgentLifecyclePoint, List>`（不变）+ 执行级 `Map<ExecutionPoint, List>`（新增 `getExecutionMiddlewares`/`registerExecutionMiddleware`）。两 scope 永不交叉。
- §三约束覆盖说明：W3-1 明确覆盖 plan 296 §三"不修改 AgentLifecyclePoint 枚举值"约束——该约束的 scope 是 plan 296 的会话级洋葱链实现。W3-1 新建**独立的 `ExecutionPoint` 枚举**承载执行级触发点，`AgentLifecyclePoint` 的 12 个值（值与语义）完全不变。Phase 3 将设计文档 §三更新为"会话级 12 点不变 + 执行级新增独立 `ExecutionPoint` 触发点"。

**D2 裁定（强类型方案 b 的变体：新建 `AttemptContext` 挂在 `HookContext`）**：新建强类型 `AttemptContext` 类（`attempt`(int, 0-based) / `retry`(boolean, attempt>0) / `lastErrorClassification`(`ErrorClassification`, 首次为 null)），通过 `HookContext.getAttemptContext()`/`setAttemptContext()` 暴露，仅执行级中间件触发时填充（会话级为 null）。
- 拒绝方案 a（`HookContext.data` Map 弱类型）：违反 nop 强类型风格，中间件作者无法经类型系统发现 contract。
- 拒绝方案 b 原版（挂在 `AgentExecutionContext`）：`AgentExecutionContext` 是 per-request，attempt 是 retry loop 内的瞬态值，挂在 per-request 对象上语义错误且会跨 attempt 串状态。挂在 `HookContext`（per-invocation 载体）才是 attempt 级信息的正确归属。
- 满足约束（attempt 编号 + retry 标志 + 上次错误分类三者强类型可读）。

**D3 裁定（Veto → synthetic failed attempt → retry 决策路径 + veto 计数上限）**：
- PRE_LLM_ATTEMPT Veto：跳过 `callChatWithTimeout`，构造 synthetic 失败 attempt（`isSuccess=false`，error="vetoed by execution middleware: <reason>"），分类为 `NON_TRANSIENT`（安全否决非瞬态传输错误），喂入现有 `retryPolicy.shouldRetry()` 决策路径（由 retryPolicy 决 RETRY/STOP/FALLBACK，**非无条件 retry**）。
- POST_LLM_ATTEMPT Veto：拒绝已返回的 response，同上 synthetic 失败 + NON_TRANSIENT + retry 决策路径。
- 工具侧 Veto（PRE_TOOL_ATTEMPT/POST_TOOL_ATTEMPT）：该单工具调用产错误 result（`AiToolCallResult.errorResult`），不影响同 batch 其他工具调用（工具无 retry 机制）。
- **防无限循环**：`LlmCallCoordinator.MAX_EXECUTION_VETOES = 3`（与 `DEFAULT_MAX_REENTRIES` 一致），跨 attempt 累计 veto 次数，超限强制 fail-loud（构造 terminal failure，break + 抛出，不静默 continue）。这防止"中间件每次 veto + retryPolicy 每次 RETRY → 无限循环"。

- [x] D1: 执行级 scope 建模方案裁定（含 chosen approach + 理由 + 拒绝的替代方案；含设计文档 §三约束覆盖说明）
- [x] D2: HookContext attempt 级语义裁定（方案 a 或 b + 理由）
- [x] D3: 执行级中间件 Veto → retry loop 控制流映射语义裁定（含防无限循环机制）
- [x] 1.1 实现执行级 scope 契约（依 D1 选定方案）
- [x] 1.2 `IHookRegistry` / `DefaultHookRegistry` 支持执行级中间件注册与查询：**现有 `registerMiddleware(AgentLifecyclePoint, IAgentMiddleware)` 和 `getMiddlewares(AgentLifecyclePoint, String)` 只接受会话级 point**。执行级需要 scope 维度分离存储——要么新增重载方法（如 `registerExecutionMiddleware`/`getExecutionMiddlewares`），要么引入 scope 参数。会话级注册路径行为不变（零回归）
- [x] 1.3 agent.xdef 声明执行级中间件 scope（`<middleware>` 增加 scope 维度或新增执行级声明元素），codegen 生成对应模型字段
- [x] 1.4 `AgentExecutorResolver.resolveMiddlewares` 装配路径：按 scope 分流——会话级注册到现有 9 点，执行级注册到执行级 registry 区域
- [x] 1.5 `AgentHookInvoker` 新增执行级中间件触发方法（会话级 `executeWithMiddleware` 行为不变），供 `LlmCallCoordinator` 和 `AgentToolDispatcher` 调用
- [x] 1.6 单元测试：执行级中间件注册/查询/chain 构建；会话级中间件行为零回归（plan 296 既有 `TestMiddlewareChain` 全过）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 执行级中间件可在 registry 注册和查询，与会话级分离
- [x] 会话级中间件 9 点行为零回归（`TestMiddlewareChain` 全部通过）
- [x] 执行级中间件 registry 注册/查询与会话级分离（依 M4 描述的 scope 维度）
- [x] **无静默跳过**：未注册执行级中间件时，执行级触发点直通（no-op 零开销），不抛异常也不静默绕过逻辑；不可达分支抛 `UnsupportedOperationException`
- [x] agent.xdef 声明编译通过，codegen 生成执行级 scope 字段
- [x] D1/D2/D3 三个 Decision 均已裁定（含理由 + 拒绝的替代方案）
- [x] 若 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §5.1 更新为最终决策（D1/D2 裁定写入）；否则明确写 `No owner-doc update required`（Phase 3 统一更新 §5.1 为 final）
- [x] `ai-dev/logs/` 对应日期条目已更新（closure 阶段统一写入）

### Phase 2 — LLM 调用尝试集成

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java`

- Item Types: `Fix | Proof`

- [x] 2.1 在 `LlmCallCoordinator.doLlmCallWithRetry` retry loop 内（`callChatWithTimeout` 调用前后）接入执行级中间件触发点
- [x] 2.2 验证：首次 attempt 执行级中间件触发（before/after）
- [x] 2.3 验证：retry 时执行级中间件**重新触发**（attempt N+1 的 before/after 确实执行，携带 retry 信号 + 上次 attempt 结果，依 D2 裁定的 attempt 级上下文）
- [x] 2.4 验证：执行级中间件 Veto 按 D3 裁定语义映射到 retry loop（Veto → attempt 视为失败 → retryPolicy 决策 RETRY/STOP/FALLBACK，非无条件 retry），有防无限循环上限保护
- [x] 2.5 验证：与 W2e 错误分类信号通路、W2-4 provider failover 正交共存（执行级中间件在 attempt 级触发，错误分类/failover 在 retry 决策级触发，不冲突）

Exit Criteria:

- [x] 执行级中间件在 retry loop 内每次 attempt 前后触发，有单元测试证明
- [x] retry 时重新评估有测试证明（计数器/标志位验证 attempt N+1 中间件确实重新执行）
- [x] **端到端验证**：从 `ReActAgentExecutor.execute()` → `doLlmCallWithRetry` → 每次 attempt 经执行级中间件 → 错误后 retry → 中间件重新评估的完整路径已验证（`TestExecutionMiddlewareLlmRetry` 直接经 `LlmCallCoordinator.doLlmCallWithRetry` 入口端到端跑通；`ReActAgentExecutor` → `llmCoordinator.doLlmCallWithRetry` 接线在 `ReActAgentExecutor:645` 已有，复用同一 coordinator 实例）
- [x] **接线验证**：`LlmCallCoordinator` 确实在 retry loop 内调用了执行级中间件触发（经 `hookInvoker.executeExecutionMiddleware`），非仅类型存在（`TestExecutionMiddlewareLlmRetry.preAndPostLlmAttemptFireAroundSingleSuccessfulCall` 等通过 recording middleware 观察 attempt 级触发证明接线）
- [x] **无静默跳过**：retry 路径不跳过执行级中间件；执行级中间件 Veto 时显式进入 retry/fallback 而非静默 continue（Veto → NON_TRANSIENT 合成 → retryPolicy 决策；veto cap 超 fail-loud）
- [x] `ai-dev/logs/` 对应日期条目已更新（closure 阶段统一写入）

### Phase 3 — 工具调用尝试集成与收口

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java`、`ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md`

- Item Types: `Fix | Proof | Follow-up`

**Decision D4**：工具 dispatch 并行结构与执行级中间件的线程模型。

**核心问题**（review M1 发现）：`AgentToolDispatcher.executeAllowedCalls` 的结构是 fan-out 循环（`:204-237`）为所有工具创建并行 `CompletableFuture`（运行在线程池上），然后 join + 结果处理循环（`:276-399`）。执行级中间件 "before" 的触发时机有两种选择：
- 方案 a：在 fan-out 循环内、提交 future **之前**同步触发（在调用线程上，future 提交前）。优点：中间件在工具执行前确定性地运行；缺点：fan-out 中其他工具可能已开始执行，"before" 不是全局 before
- 方案 b：在 `CompletableFuture.supplyAsync` 内部、`toolManager.callTool` 之前触发（在池线程上）。优点：每个工具调用的 before/after 严格配对；缺点：中间件在池线程上运行，`HookContext` 需线程安全

裁定约束：倾向方案 a（同步 before + 异步工具执行 + 结果处理后 after），因为执行级中间件做安全检查应在工具执行前确定性完成，不应受线程池调度影响。

**Anti-Hollow 警告**（review M3 发现）：现有 `AgentToolDispatcher:282` 的 PRE_ACTING `executeWithMiddleware` 调用**返回值被丢弃**（未检查 `isVeto()`）。执行级中间件调用**绝不能**复用此模式——必须检查返回值，Veto 时按 D3 语义处理（工具调用产错误结果）。Phase 3 实现时须标注此区别。

**D4 裁定（采纳方案 a — 同步 before + 异步工具执行 + 同步 after）**：PRE_TOOL_ATTEMPT 在 fan-out 循环内、提交 future **前**同步触发（调用线程）；POST_TOOL_ATTEMPT 在结果处理循环内、join **后**、commit **前**同步触发（调用线程）。
- 拒绝方案 b（`CompletableFuture.supplyAsync` 内、池线程上触发）：安全检查应在工具执行前**确定性**完成，不受线程池调度影响；且 `HookContext` 线程安全需额外保证。
- 工具无 retry 机制：`AttemptContext` 恒为 attempt=0、非 retry。
- Anti-Hollow 红线已落实：PRE/POST_TOOL_ATTEMPT 返回值均显式检查 `isVeto()`（非丢弃），有测试 `TestExecutionMiddlewareToolDispatch` 对比验证（vetoed 工具不提交 future、result 替换为错误）。

- [x] 3.1 在 `AgentToolDispatcher` 单个工具调用执行前后接入执行级中间件触发点（per tool call，非 per dispatch batch；线程模型依 D4 裁定）
- [x] 3.2 验证：每个工具调用 attempt 执行级中间件触发（before/after）
- [x] 3.3 验证：执行级中间件 Veto 中止单个工具调用——**返回值被检查**（非丢弃），产错误结果，不影响同 batch 其他工具调用
- [x] 3.4 设计文档 `nop-ai-agent-middleware-design.md` §5.1 从方向性描述重写为最终架构决策（D1/D2/D3/D4 最终方案、会话级 vs 执行级双层表、触发点清单、retry 重评估语义、Veto 控制流映射、线程模型）；更新 §三约束（区分会话级 12 点不变 + 执行级新增触发点）；删除 "增量设计" 标记，标注 `final`
- [x] 3.5 roadmap W3-1 标记完成（`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md`）

Exit Criteria:

- [x] 执行级中间件在工具调用前后触发，有单元测试证明
- [x] **Anti-Hollow**：执行级中间件调用的返回值**被检查**（Veto 生效），非丢弃（与现有 PRE_ACTING `:282` 丢弃模式对比验证）
- [x] **端到端验证**：从 `ReActAgentExecutor.execute()` → `AgentToolDispatcher.executeAllowedCalls` → 每个工具调用经执行级中间件的完整路径已验证（`TestExecutionMiddlewareToolDispatch` 直接经 `executeAllowedCalls` 入口端到端跑通；`ReActAgentExecutor` → `dispatcher.executeAllowedCalls` 接线已存在）
- [x] **接线验证**：`AgentToolDispatcher` 确实在单个工具调用前后调用执行级中间件触发（非仅整个 batch 级），线程模型依 D4 裁定（recording middleware 观察 per-tool 触发 + vetoed 工具 callCount=0 证明接线）
- [x] 设计文档 §5.1 + §三为 final 状态（无 "Proposed/Current vs"、无方向性措辞，含最终双层表 + 触发点清单 + Veto 控制流 + 线程模型）
- [x] `ai-dev/logs/` 对应日期条目已更新（closure 阶段统一写入）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 执行级中间件在 LLM retry loop 和工具 dispatch 内均触发（per-attempt）
- [x] retry 时执行级中间件重新评估（核心价值，有端到端测试证明）
- [x] 会话级中间件零回归（plan 296 全部既有测试通过；全模块 3133 测试通过）
- [x] 与 W2e 错误分类、W2-4 provider failover 正交共存（无冲突）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 设计文档 §5.1 final 状态
- [x] 受影响 owner docs 已同步到 live baseline（`nop-ai-agent-middleware-design.md` §5.1 + §三 final）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（见 Closure section）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）执行级中间件触发点在 retry loop 和工具 dispatch 内确实被调用（不只是接口存在），（b）retry 时确实重新评估，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`（通过）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`（3133 测试全过）
- [x] checkstyle / 代码规范检查通过（改动文件无 violation）

## Deferred But Adjudicated

无。所有 in-scope 项均已交付。Non-Goals 中明确排除的项（工具 retry 机制、声明式 filter chain W3-2、ON_ERROR/REASONING_CHUNK/POST_COMPACT 链式、跨 session 状态共享）不在本 plan scope，记录在 Non-Blocking Follow-ups。

## Non-Blocking Follow-ups

- 执行级中间件的 ON_ERROR / REASONING_CHUNK / POST_COMPACT 链式支持 → plan 296 遗留 follow-up，独立项
- 执行级中间件状态跨 session 持久化 → 当前 per-request/per-attempt scope，跨重启可将来通过声明式配置实现
- 声明式 filter chain（W3-2）→ 后继 plan `2026-08-01-1437-4`，依赖本 plan 建立的 scope 概念

## Closure

Status Note: W3-1 双层执行级中间件已完整落地。3 个 Phase 全部 completed：Phase 1 契约/scope 建模（D1 独立 ExecutionPoint 枚举 + scope 属性；D2 强类型 AttemptContext 挂 HookContext；D3 veto→NON_TRANSIENT→retry 决策 + veto cap）、Phase 2 LLM retry loop 集成（PRE/POST_LLM_ATTEMPT 每次 attempt 触发，retry 时重评估）、Phase 3 工具 dispatch 集成（D4 同步 before/after，Anti-Hollow 返回值检查）+ 设计文档 §5.1 final。核心价值已由端到端测试证明（retry 时执行级中间件重新触发并携带上次分类）。会话级 plan 296 零回归（全模块 3133 测试通过）。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（general 类型，独立 fresh session，非实现阶段同一 session）
- Audit Session: ses_0420c87b7ffeIeIBI551kY6rgA
- Verdict: PASS — 逐条核对 live source 证据：

1. **触发点在 retry loop/dispatch 内确实被调用**（VERIFIED）：`LlmCallCoordinator.java:196-197`(PRE_LLM_ATTEMPT) / `:223-224`(POST_LLM_ATTEMPT) 在 while-loop(`:190`) 内、`callChatWithTimeout`(`:218`) 前后；`AgentToolDispatcher.java:203-205`(PRE_TOOL_ATTEMPT) 在 fan-out 循环内提交 future 前、`:295-296`(POST_TOOL_ATTEMPT) 在结果处理循环内 join 后。均非空、非注释。
2. **retry 时确实重新评估**（VERIFIED）：触发点在 while-loop body 内，每次 `continue` 重新触发 PRE；`lastClassification`(`:187`) 跨迭代保留并传入下次 AttemptContext(`:195`)。
3. **Anti-Hollow 工具侧 veto 返回值被检查**（VERIFIED）：`AgentToolDispatcher:297` `postToolResult.isVeto()` 替换 result；`:206` `preToolResult.isVeto()` 不提交 future；对比现有 `:304` PRE_ACTING 返回值丢弃模式。
4. **veto cap 防无限循环**（VERIFIED）：`MAX_EXECUTION_VETOES=3`(`:776`)，计数器超限 set fallbackExhausted fail-loud(`:205/:231`)，循环外抛出(`:379-382`)。
5. **会话级零回归**（VERIFIED）：`executeWithMiddleware(AgentLifecyclePoint,...)` 签名/路径不变；registry 双 map（`middlewares` keyed by AgentLifecyclePoint vs `executionMiddlewares` keyed by ExecutionPoint）分离。
6. **agent.xdef scope**（VERIFIED）：`agent.xdef:72` `scope="enum:session,execution|session"`；`_AgentMiddlewareModel.java` 有 `_scope`/`getScope` 字段。
7. **测试验证行为**（VERIFIED）：`TestExecutionMiddlewareLlmRetry`(8 tests，含 retry 重评估 + veto cap fail-loud)、`TestExecutionMiddlewareToolDispatch`(4 tests，含 veto 不丢弃 + 不影响 batch)、`TestExecutionMiddleware`(10 tests，registry 分离 + onion order)。

审计 minor 观察（非 gap）：PRE_LLM_ATTEMPT veto 时该次 iteration 不触发 POST_LLM_ATTEMPT（skipCall=true）——符合设计（POST 仅在调用返回时触发，PRE veto 无调用返回），retry 路径不受影响（PRE 下次 iteration 仍重新触发）。

Follow-up:

见 Non-Blocking Follow-ups（ON_ERROR/REASONING_CHUNK/POST_COMPACT 链式、跨 session 状态、声明式 filter chain W3-2）。
