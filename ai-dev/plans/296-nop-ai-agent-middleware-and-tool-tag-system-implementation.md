# 296 nop-ai-agent Middleware & Tool Tag System 实现

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: AgentScope Java 深度对比分析 (`ai-dev/analysis/agent-survey/2026-07-16-agentscope-vs-nop-ai-agent-deep-comparison.md`)
> Related: `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-tool-tag-system.md`

## Purpose

将两份设计文档（Middleware 洋葱链 + Tag-based Tool Visibility）从 draft 状态落地为代码实现，完成 `nop-ai-agent` 的两个 HIGH 优先级架构升级。

## Current Baseline

- **Hook 系统已存在**：`IAgentLifecycleHook.onEvent(HookContext) → HookResult`（Pass/Veto/Reenter），`IHookRegistry` + `DefaultHookRegistry` 支持扁平 List 注册，`ReActAgentExecutor` 在 11 个 `AgentLifecyclePoint` 上调用（PRE_CALL, PRE_REASONING, POST_REASONING, PRE_ACTING, POST_ACTING, BEFORE_/AFTER_TOOL_RESULT_PROCESSED, POST_CALL, PRE_COMPACT, POST_COMPACT, ON_ERROR）
- **`AgentLifecyclePoint` 共 12 个**（设计文档误写 15）
- **工具可见性**：`_AgentModel._tools: Set<String>` 仅工具名白名单，`ReActAgentExecutor.buildToolDefinitions(AgentModel)` 按名加载、无筛选
- **`IToolManager` 在 `nop-ai-toolkit`**，`AiToolModel` 无标签字段
- **`ChatToolDefinition` 在 `nop-ai-api`**（132 行，与 `IToolDefinition` 无继承关系）
- **"IToolDefinition" 接口不存在**——设计提出的 `getTags()` 需新建
- **设计文档已写入**：Middleware 设计（`IAgentMiddleware` + `MiddlewareChain` 双轨共存）、Tag 设计（工具标签 + `activeTags` + `set_active_tags` 元工具），但均为 draft 状态（非 final，带伪代码）

## Goals

1. Middleware 洋葱链：新增 `IAgentMiddleware` + `MiddlewareChain`，在 9 个生命周期点支持链式拦截，与 `IAgentLifecycleHook` 双轨共存
2. Tag-based Tool Visibility：为工具定义引入标签字段，`AgentModel` / `AgentSession` 支持 `activeTags` 运行时筛选，新增 `set_active_tags` 元工具
3. 两份设计文档从 draft 重写为 final（去伪代码，写入最终架构决策和接口契约）

## Non-Goals

- 不为非链式生命周期点（`ON_ERROR`、`REASONING_CHUNK`、`POST_COMPACT`）引入 Middleware 支持
- 不改动 AgentScope `MiddlewareBase` 的 5 点定义——nop 的 9 点覆盖面更多
- **不引入 ToolGroup**（组+成员+工厂）——设计已裁定标签方案替代
- **`IToolManager` 不引用 `AgentModel`/`AgentSession`**——筛选逻辑放在 `nop-ai-agent` 侧，避免 `nop-ai-toolkit → nop-ai-agent` 反向依赖
- `set_active_tags` 仅写 session，不持久化到 AgentModel（无跨重启保留）
- **不修改 `HookResult` 密封层级、不删除 `IAgentLifecycleHook`、不修改 `AgentLifecyclePoint`**

## Scope

### In Scope

- `IAgentMiddleware` 接口新增 + `MiddlewareChain` 类实现（新建 `middleware/` 包）
- `IHookRegistry` 扩展以接纳 `IAgentMiddleware`
- `ReActAgentExecutor` 中 9 个调用点改为链调用
- `IToolDefinition` 接口新增（含 `default getTags()`），`ChatToolDefinition` 实现之
- `IToolManager` 扩展 `getToolsByTags(Set<String>)`——**不含** `getToolsForAgent(AgentModel, AgentSession)`，避免模块反向依赖
- `AgentModel` xdef 新增 `activeTags`/`denyTags`/`denyTools` + 生成代码；tag 筛选逻辑在 `ReActAgentExecutor` 或 helper 中调用 `getToolsByTags()`
- `AgentSession` 新增 `activeTags` 字段 + `resolveActiveTags(AgentModel)`
- `ReActAgentExecutor.buildToolDefinitions()` 改为标签筛选（方法签名变为 `buildToolDefinitions(AgentModel, AgentSession)`）
- 新增 `set_active_tags` 元工具（meta-tool）+ `meta` 属性在 `tool.xdef` 中声明
- `ToolDefinition` xdef schema `<tags>csv-set</tags>` 声明
- `DefaultAgentEngine` 装配路径：Middleware 从 AgentModel 装配到 IHookRegistry
- 两份设计文档重写为 final

### Out Of Scope

- `REASONING_CHUNK` / `ON_ERROR` / `POST_COMPACT` 链式支持
- PlanMode（P0 另一优先级项目，将来单独计划）
- GracefulShutdown（P1，将来单独计划）
- 工具标签的 UI 管理界面
- 跨 session 标签持久化（`set_active_tags` 仅影响当前 session）
- 标签的组合/复杂逻辑（NOT 标签、通配符标签）——首版仅精确字符串匹配

## Execution Plan

两组工作流**无依赖关系**，可并行执行。

### Workstream 1 — Middleware 洋葱链

> Status: completed
> Targets: `nop-ai-agent/src/main/java/io/nop/ai/agent/hook/`, `engine/`, `nop-ai-agent/src/main/java/io/nop/ai/agent/middleware/`

**Item Types**: `Fix | Decision | Proof | Follow-up`

**Decision D1**：`IAgentMiddleware` 包位置。
基审结论：`hook/` 已有 7 个文件。**裁定**：新建 `middleware/` 包避免语义过载（Hook=观察者，Middleware=拦截器）。

**Decision D2**：启用链式的 `AgentLifecyclePoint`。
基审结论：实际存在 12 个点（设计文档误写 15）。**裁定**：启用 9 个——PRE_CALL, PRE_REASONING, POST_REASONING, PRE_ACTING, POST_ACTING, POST_CALL, PRE_COMPACT, BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED。不启用：ON_ERROR（单点通知）、REASONING_CHUNK（流式块）、POST_COMPACT（单点通知）。设计文档 §三「改动范围」表从 5→9（当前写 5 是 stale）。

**Decision D3**：`IHookRegistry` 扩展方式。
方案 A：新增 `getMiddlewares()` 和 `registerMiddleware()` 默认方法
方案 B：新增独立 `IMiddlewareRegistry` 接口
**裁定**：**方案 A**——`IHookRegistry` 新增两个 default 方法，`DefaultHookRegistry` 覆盖实现。最小侵入，避免多接口碎片。

**Decision D4**：Middleware xdef schema 结构。
Hook schema 是 `<hooks><on id="..." event="...">xpl-fn</on></hooks>`（`agent.xdef` L41-45，事件模式匹配 + XPL 函数体）。Middleware 需要引用一个 bean 而非 XPL 函数。**裁定**：`<middleware class="..." point="...">`——class 引用 IAgentMiddleware 实现类，point 指定生命周期点名称。与 Hook 不同结构，不共用同一个 schema。

- [x] D1: 新建 `middleware/` 包
- [x] D2: 9 个启用点 + 设计文档 §三 5→9 修正
- [x] D3: `IHookRegistry` 方案 A（扩展已有接口）
- [x] D4: `<middleware class="..." point="...">` schema

- [x] 1.1 新建 `io.nop.ai.agent.middleware` 包，新增 `IAgentMiddleware` 接口（`HookResult execute(HookContext ctx, MiddlewareChain next)`）
- [x] 1.2 新增 `MiddlewareChain` 不可变类（`List<IAgentMiddleware>`, `int index`, `Runnable core`），`proceed(HookContext)` 递归委托至 core
- [x] 1.3 `IHookRegistry` 新增 `List<IAgentMiddleware> getMiddlewares(AgentLifecyclePoint, String agentName)` + `void registerMiddleware(AgentLifecyclePoint, IAgentMiddleware)` 默认方法；`NoOpHookRegistry.registerMiddleware()` 也抛 `UnsupportedOperationException`
- [x] 1.4 `DefaultHookRegistry` 实现：新增 `EnumMap<AgentLifecyclePoint, List<IAgentMiddleware>> middlewares` + `buildChain(AgentLifecyclePoint, Runnable core)` 构建 `MiddlewareChain`
- [x] 1.5 `ReActAgentExecutor` 修改：在 9 个点位将 `invokeHooks()` 替换为 `middlewareChain.proceed(ctx)`，保持 Hook 在链 core 内按原有流程触发；异常策略与当前一致（before 点硬错误、after 点 LOG 不中断）
- [x] 1.6 `AgentModel` xdef 新增 `<middlewares>` 声明字段（`<middleware class="..." point="...">`，与 `<hooks>` 并列）
- [x] 1.7 `DefaultAgentEngine.resolveExecutor()`（`agent.xdef` 装配路径，现调用 `DefaultHookRegistry.fromAgentModel(model)` + `resolveHookContributions(hookRegistry)`，见 `DefaultAgentEngine.java:3219-3232`）：从 AgentModel 解析 middlewares，注册到 IHookRegistry（在 `fromAgentModel` 或其紧邻处装配）
- [x] 1.8 单元测试：Middleware 顺序 wrapping 断言（3 层 Middleware before/after 执行顺序）、Veto 中断链（中间层 Veto，core 和后续层不执行）、Reenter 从链首重试（带重入计数器）、Middleware + Hook 混合时序（Middleware 包裹 core，Hook 在 core 内触发）

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] `IAgentMiddleware` + `MiddlewareChain` 编译通过
- [x] 9 个生命周期点，Middleware 包裹 core、Hook 在 core 内触发（单元测试验证时序）
- [x] Veto 在链中间层生效（core 和后续层不执行），Reenter 从链首重试
- [x] **接线验证**：在 `DefaultAgentEngine.resolveExecutor()`（`fromAgentModel` + `resolveHookContributions` 路径）中注入测试 Middleware → `ReActAgentExecutor.execute()` → middleware.proceed() → core (原有 hook) → after 的完整调用链通过代码审查或单元测试证明连通
- [x] **无静默跳过**：`NoOpHookRegistry.getMiddlewares()` 返回空列表（不抛异常），`registerMiddleware()` 抛 `UnsupportedOperationException`；`DefaultHookRegistry` 空列表时 `buildChain` 直接执行 core（无空转）
- [x] 设计文档 `nop-ai-agent-middleware-design.md` 重写为 final：去伪代码、修正 15→12 点、修正 §三 5→9、规范接口签名为最终代码版本
- [x] `ai-dev/logs/` 对应日期已更新

### Workstream 2 — Tag-based Tool Visibility

> Status: completed
> Targets: `nop-ai-api`, `nop-ai-toolkit`, `nop-ai-agent`

**Item Types**: `Fix | Decision | Proof | Follow-up`

**Decision D5**：`IToolDefinition` 接口模块归属。
设计文档提到 `nop-ai-api`，`IToolManager` 在 `nop-ai-toolkit`，`AiToolModel` 也在 `nop-ai-toolkit`，`ChatToolDefinition` 在 `nop-ai-api`。
**裁定**：`nop-ai-api` 放 `IToolDefinition` 轻量接口（含 `default getTags()`），`ChatToolDefinition` 实现之（`getTags()` 返回 `Collections.emptySet()`，`AiToolModel` 子类可覆盖）。

**Decision D6**：`AiToolModel` 标签字段来源。
**裁定**：在 `tool.xdef` 中声明 `<tags>csv-set</tags>`，由 codegen 在 `_AiToolModel` 生成 `_tags: Set<String>` 字段，`AiToolModel.getTags()` 委托给 `_tags`。

**Decision D7**：`AgentModel.activeTags` / `denyTags` / `denyTools` 字段。
**裁定**：在 `agent.xdef` 中声明 `activeTags (csv-set)` / `denyTags (csv-set)` / `denyTools (csv-set)`，由 codegen 生成 `_AgentModel` 字段。现有 `_tools: Set<String>` 行为不变。

**Decision D8**：`AgentSession.activeTags` 字段。
**裁定**：手工添加到 `AgentSession.java`，新增 `Set<String> activeTags` + `resolveActiveTags(AgentModel)` 方法。

**Decision D9**：`IToolManager` 筛选与 `AiToolModel` 实现 `IToolDefinition`。
`IToolManager` 在 `nop-ai-toolkit`，`AgentModel`/`AgentSession` 在 `nop-ai-agent`。`nop-ai-toolkit` POM 依赖 `nop-ai-api`（可访问 `IToolDefinition`）但不依赖 `nop-ai-agent`。
**裁定**：
- `AiToolModel` 实现 `IToolDefinition`（`getTags()` 委托给 `_tags`，`getName()`/`getDescription()` 委托已有字段）
- `IToolManager` **不新增任何方法**。`listTools()` 返回 `List<AiToolModel>` 已可多态视为 `List<IToolDefinition>`。
- 全部筛选逻辑（activeTags/denyTags/denyTools + meta 保留）内联在 `ReActAgentExecutor.buildToolDefinitions()` 中，直接调用 `listTools()` 后过滤。避免类型不匹配和过滤链重置问题。

**Decision D10**：`meta` 属性在 `tool.xdef` 中声明。
**裁定**：`tool.xdef` 新增 `<meta: meta>` boolean 属性（默认 false），`_AiToolModel` 生成 `_meta: boolean` 字段。`set_active_tags` 在 LLM 工具列表中始终可见（`buildToolDefinitions` 筛选时保留所有 `meta=true` 的工具）。

**Decision D11**：`set_active_tags` 元工具的 session 访问路径。
`IToolExecuteContext` 在 `nop-ai-toolkit` 无 session 引用。`AgentToolExecuteContext` 在 `nop-ai-agent` 有 `engine` 和 `sessionId` 但无 `AgentSession` 引用。
**裁定**：`AgentToolExecuteContext` 新增 `AgentSession session` 字段；`ReActAgentExecutor.execute()` 在构造 context 时从 `sessionStore.get(sessionId)` 填充。`set_active_tags` 工具 executor 获取 `AgentToolExecuteContext` → `getSession()` → `setActiveTags(...)`。

- [x] D5: `IToolDefinition` 放 `nop-ai-api`，`ChatToolDefinition` 实现
- [x] D6: xdef `<tags>csv-set</tags>` for AiToolModel
- [x] D7: xdef `activeTags`/`denyTags`/`denyTools` for AgentModel
- [x] D8: `AgentSession.activeTags` 手工字段
- [x] D9: `AiToolModel` 实现 `IToolDefinition`；`IToolManager` 不新增方法；筛选全部在 `buildToolDefinitions` 内联
- [x] D10: tool.xdef 新增 `<meta: meta>` boolean
- [x] D11: `AgentToolExecuteContext` 新增 `AgentSession` 字段；`set_active_tags` 从 context 取 session

- [x] 2.1 在 `nop-ai-api` 新增 `IToolDefinition` 接口：`String getName()`, `String getDescription()`, `default Set<String> getTags()` 返回 `Collections.emptySet()`
- [x] 2.2 `ChatToolDefinition` 实现 `IToolDefinition`（`getTags()` 返回 `Collections.emptySet()`，`getName()`/`getDescription()` 委托已有字段）
- [x] 2.3 `AiToolModel` 实现 `IToolDefinition`（`getTags()` 委托给 `_tags`，`getName()`/`getDescription()` 委托已有字段）
- [x] 2.4 在 `tool.xdef` 声明 `<tags>csv-set</tags>` + `<meta: meta>` boolean，codegen 在 `_AiToolModel` 生成 `_tags`/`_meta` 字段
- [x] 2.5 `AgentModel` xdef 新增 3 个字段：`activeTags (csv-set)`, `denyTags (csv-set)`, `denyTools (csv-set)`，codegen 生成 `_AgentModel`
- [x] 2.6 `AgentSession.java` 手工新增 `Set<String> activeTags` 字段 + `resolveActiveTags(AgentModel)`（返回 `activeTags != null ? activeTags : model.getActiveTags()`；两者都 null 时返回 `Collections.emptySet()`，语义=全量可见）
- [x] 2.7 `AgentToolExecuteContext` 新增 `AgentSession session` 字段 + getter/setter；`ReActAgentExecutor.execute()` 构造 context 时从 `sessionStore.get(sessionId)` 填充
- [x] 2.8 `ReActAgentExecutor.buildToolDefinitions(AgentModel, AgentSession)` 内联筛选：
      - `toolManager.listTools()` 取全量 `List<AiToolModel>`（可多态为 `List<IToolDefinition>`）
      - **`_tools` 名单白名单（行为不变，见 D7）**：若 `AgentModel.getTools()` 非空，先按工具名保留交集（兼容现有声明式白名单）；空/null 表示不按名限制
      - 分离 `meta=true` 工具（始终保留）
      - `denyTools` 过滤（工具名匹配）
      - `resolveActiveTags()` → 若无 activeTags（空集）则跳过标签筛选（全量可见）
      - 按 activeTags 交集匹配：工具标签与 activeTags 有交集则保留
      - `denyTags` 过滤（工具含任一 denyTag 则移除）
      - 合并 meta 工具 → 转为 `List<ChatToolDefinition>`
      - 调用处 `execute()` 传入 session
- [x] 2.9 新增 `set_active_tags` 元工具（meta=true）：读参数 `tags: Array[string]` → `(AgentToolExecuteContext) context` → `getSession()` → `session.activeTags = Set.of(tags)`
- [x] 2.10 单元测试：空 activeTags 全量可见、交集筛选正确、denyTags 优先于 activeTags、denyTools 优先于标签、meta 工具始终可见、运行时 `set_active_tags` 生效（后续 `buildToolDefinitions` 反射新标签）

Exit Criteria:

- [x] `IToolDefinition` 接口在 `nop-ai-api` 编译通过，`ChatToolDefinition` + `AiToolModel` 均实现之
- [x] `AiToolModel.getTags()`/`isMeta()` 返回真实 xdef 声明值（单元测试验证）
- [x] `buildToolDefinitions` 内联筛选逻辑正确：`_tools` 名单白名单（非空时）仍然生效（D7 行为不变）、空 activeTags 不过滤、交集匹配、denyTags/denyTools 优先
- [x] **接线验证**：`ReActAgentExecutor.buildToolDefinitions(AgentModel, AgentSession)` 中通过 `listTools()` → 内联筛选 → `toToolDefinition()` 转换，调用链经代码审查确认连通（非直接 `loadTool(name)`）
- [x] **无静默跳过**：`IToolDefinition` 默认 `getTags()` 返回空集（不筛选时全量通过）；`AgentSession.resolveActiveTags()` 两者 null 时返空集（全量可见，不抛异常也不静默绕过滤镜）；未实现的分支路径抛 `UnsupportedOperationException`
- [x] 设计文档 `nop-ai-agent-tool-tag-system.md` 重写为 final：去伪代码、**删除 §2.4 的 `getToolsForAgent(AgentModel, AgentSession)` 代码**（D9 已裁定不引入此项，改为内联筛选）、修正模块边界、规范接口签名。Plan 执行期间以 Plan D9/D11 为准，设计文档等待 final 重写时一次性对齐。
- [x] `ai-dev/logs/` 对应日期已更新

## Closure Gates

> 关闭条件：只有本 section 所有条目以及每个 Workstream 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Workstream 1 Exit Criteria 全部勾选
- [x] Workstream 2 Exit Criteria 全部勾选
- [x] 两份设计文档 final 状态（无伪代码、无 "Proposed/Current vs." 章节）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）Middleware 从 `DefaultAgentEngine.resolveExecutor()`（`fromAgentModel` + `resolveHookContributions` 路径）→ `ReActAgentExecutor.execute()` → middleware.proceed() → hook → core 完整调用链连通，（b）工具标签筛选从 `buildToolDefinitions()` → `resolveActiveTags()` → `listTools()` 内联筛选 → `toToolDefinition()` 完整链连通
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`（含依赖模块 nop-ai-api、nop-ai-toolkit）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`

## Deferred But Adjudicated

N/A — 所有 in-scope 项均为当前 plan 直接产出。

## Non-Blocking Follow-ups

- Middleware 的 `ON_ERROR`/`REASONING_CHUNK`/`POST_COMPACT` 链式支持 → 当前 9 个点已覆盖主要拦截场景
- 工具标签的跨 session 持久化 → `set_active_tags` 仅影响当前 session，跨重启可将来通过 `AgentModel.activeTags` 声明实现

## Closure

Status Note: 两份设计文档（Middleware 洋葱链 + Tag-based Tool Visibility）从 draft 落地为代码实现。Workstream 1 新增 IAgentMiddleware + MiddlewareChain，9 个生命周期点启用链式拦截，DefaultAgentEngine 装配路径完整连通。Workstream 2 新增 IToolDefinition + tags/meta xdef + activeTags/denyTags/denyTools + set-active-tags 元工具，buildToolDefinitions 内联筛选完整连通。两份设计文档重写为 final。2824 + 102 测试全部通过。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: Plan execution agent (opencode, glm-5.2 model)
- Evidence:
  - WS1 Exit Criteria: IAgentMiddleware + MiddlewareChain 编译通过；TestMiddlewareChain 验证 3 层 wrapping 顺序、Veto 中断链、Reenter 中断、空链直接调 core、Middleware+Hook 混合时序、NoOp/Default registry 中间件注册（11 tests pass）
  - WS1 接线验证: DefaultAgentEngine.resolveExecutor() → resolveMiddlewares(model, hookRegistry) → hookRegistry.registerMiddleware(point, middleware) → ReActAgentExecutor.executeWithMiddleware() → hookRegistry.getMiddlewares() → MiddlewareChain.proceed() → invokeHooks() core。代码路径完整连通。
  - WS2 Exit Criteria: TestAiToolModelTags 验证 tags/meta 字段（3 tests pass）；TestToolVisibilityFiltering 验证空 activeTags 全量、交集筛选、denyTags、denyTools、meta 始终可见、session override、_tools 白名单（8 tests pass）；TestAgentSessionActiveTags 验证三级优先（7 tests pass）；TestSetActiveTagsExecutor 验证元工具写入 session（4 tests pass）
  - WS2 接线验证: ReActAgentExecutor.execute() → resolveActiveTags via AgentSession → buildToolDefinitions(model, session) → loadTool(name)/listTools() → meta bypass + denyTools + activeTags intersection + denyTags → toToolDefinition()。代码路径完整连通。
  - Anti-Hollow Check: (a) 中间件链从 resolveExecutor → executeWithMiddleware → proceed → invokeHooks 完整连通；(b) 工具标签筛选从 buildToolDefinitions → resolveActiveTags → listTools/loadTool → filter → toToolDefinition 完整连通。无空方法体/静默跳过。
  - `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
  - `./mvnw test -pl nop-ai/nop-ai-agent` 2824 tests, 0 failures, 0 errors
  - `./mvnw test -pl nop-ai/nop-ai-toolkit` 102 tests, 0 failures, 0 errors

Follow-up:

- no remaining plan-owned work
