> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-5
> **Last Reviewed**: 2026-06-11
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-5, `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §5
> **Related**: 132-nop-ai-agent-executor-interface.md (L1-3), 131-nop-ai-agent-execution-context.md (L1-2 ✅), L1-1 (IAgentEngine)

# 133 ReActAgentExecutor — ReAct 循环核心实现

## Purpose

实现 `ReActAgentExecutor`，Agent 引擎的 ReAct 循环核心。这是 Layer 1 主链路中最关键的组件——所有上层能力（会话、Hook、权限、可靠性）都依赖这个执行器能正确完成"LLM 调用 → 工具执行 → 结果回灌 → 继续推理"的闭环。

## Prerequisites

**硬前置条件**：plan 132（L1-3 `IAgentExecutor` 接口创建）必须先完成。本计划的 Phase 1 需要实现 `IAgentExecutor` 接口，若该接口不存在则无法编译。执行顺序：plan 132 → 本 plan。

## Current Baseline

- `AgentExecutionContext`（L1-2 ✅）已存在：`io.nop.ai.agent.engine.AgentExecutionContext`，持有 agentModel, messages, sessionId, status, currentIteration, maxIterations, chatOptionsModel, tokensUsed, metadata, lastError, startTimeMs
- `AgentExecutionResult`（L1-2 ✅）已存在：`io.nop.ai.agent.engine.AgentExecutionResult`，含 `fromContext(AgentExecutionContext)` 工厂方法
- `AgentExecStatus` 枚举已存在：`io.nop.ai.agent.model.AgentExecStatus`，4 值（pending, running, completed, failed）
- `IAgentExecutor` 接口（L1-3）计划在 plan 132 中创建：`CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx)`
- `IChatService`（nop-ai-api）已存在：`io.nop.ai.api.chat.IChatService`，含 `callAsync(ChatRequest, ICancelToken)` 和 `call(ChatRequest, ICancelToken)`
- `ChatResponse`（nop-ai-api）已存在，含 `message`（ChatAssistantMessage）, `usage`（ChatUsage）, `finishReason`, `isSuccess()`, `getError()`
- `ChatAssistantMessage.hasToolCalls()` 已存在：返回是否有 tool calls
- `ChatToolCall`（nop-ai-api）已存在：`id`, `name`, `arguments`（Map<String, Object>）
- `ChatToolResponseMessage`（nop-ai-api）已存在：factory 方法 `fromToolCall(toolCall, result)` 和 `error(toolCallId, name, msg)`
- `ChatToolDefinition`（nop-ai-api）已存在：`of(name, description, parameters)` factory
- `ChatRequest`（nop-ai-api）已存在：含 `messages`, `options` 字段
- `ChatOptions`（nop-ai-api）已存在：含 provider, model, temperature, topP, maxTokens, tools, toolChoice 等
- `IToolManager`（nop-ai-toolkit）已存在：`io.nop.ai.toolkit.api.IToolManager`，含 `callTool(name, AiToolCall, ctx)` 和 `loadTool(name)`
- `AiToolModel`（nop-ai-toolkit）已存在：name, description, schema（XNode）
- `AiToolCall`（nop-ai-toolkit）已存在：toolName, input, explanation 字段
- `AiToolCallResult`（nop-ai-toolkit）已存在：status, output（body）, error（body）
- `IToolExecuteContext`（nop-ai-toolkit）已存在：getWorkDir(), getEnvs(), getCancelToken() 等方法
- `AgentModel.tools` 为 `Set<String>`（工具名集合）
- `AgentModel.chatOptions` 为 `ChatOptionsModel`（provider, model, temperature 等）
- `AgentModel.prompt` 为 `IPromptSyntaxNode`（有 `render(IEvalScope)` 方法返回渲染后字符串，也有 `getSource()` 返回原始模板；若 prompt 含变量需 IEvalScope，否则用 getSource()）
- **类型桥接关键差异**：
  - `ChatToolCall.id` 是 `String`（如 "call_abc123"），`AiToolCall.id` 是 `int`（自增序号）——不直接映射，需保留原始 ChatToolCall 用于回灌时关联 toolCallId
  - `ChatToolCall.arguments` 是 `Map<String, Object>`，`AiToolCall.input` 是 `String`——需 JSON 序列化
  - `AiToolCallResult.id` 是 `int`，`ChatToolResponseMessage.toolCallId` 是 `String`——不直接映射，使用原始 ChatToolCall.id 回灌
  - `AiToolModel.schema` 是 `XNode`（XML），`ChatToolDefinition.parameters` 是 `Map<String, Object>`（JSON Schema）——需 XNode → JSON Schema 转换
- `IToolExecuteContext` 需要 6 个方法：`getWorkDir()`, `getEnvs()`, `getExpireAt()`, `getCancelToken()`, `getFileSystem()` (`IToolFileSystem`), `getExecutor()` (`IThreadPoolExecutor`)。其中 `IToolFileSystem` 和 `IThreadPoolExecutor` 无默认实现，需由调用方提供
- `AgentExecutionResult.fromContext(ctx)` 始终将 finalMessage 设为 null（最终消息在 messages 列表中）
- `engine/` 包下无执行器实现——仅有 AgentExecutionContext 和 AgentExecutionResult 两个数据类
- `src/test/` 已有 4 个测试类（TestAgentExecutionContext, TestAgentExecutionResult, TestAgentModelLoading, TestAgentPlanRecordMapping）

## Goals

- `ReActAgentExecutor` 实现 `IAgentExecutor`，完成标准 ReAct 循环
- 循环流程：构建请求 → 调用 LLM → 检查工具调用 → 执行工具 → 回灌结果 → 继续推理
- 结束条件：无工具调用、达到 maxIterations、LLM 错误、不可恢复错误
- ChatToolCall → AiToolCall 桥接：name→toolName, arguments(Map)→input(JSON String), 保留原始 ChatToolCall 用于结果回灌
- AiToolCallResult → ChatToolResponseMessage 桥接：成功时 fromToolCall(原始toolCall, output.body), 失败时 error(原始toolCall.id, name, error.body)
- 工具定义构建：AgentModel.tools → IToolManager.loadTool → AiToolModel → ChatToolDefinition（schema XNode 转 JSON Schema Map；若 schema 为 null 则仅传 name+description）
- ChatOptionsModel → ChatOptions 转换（处理 null chatOptions，复制公共字段，附加 tools 和 toolChoice）
- IToolExecuteContext 简单实现（接受外部提供的 IToolFileSystem 和 IThreadPoolExecutor；未提供时返回 null，测试中 tools 无需这些依赖）
- 单元测试覆盖基本循环、多轮迭代、并行工具、结束条件、错误处理

## Non-Goals

- 不实现 Actor 模型、消息队列、followUp 外层循环（L1-1 IAgentEngine）
- 不实现 Hook 生命周期回调（before_reasoning 等，L1-12）
- 不实现 steering 消息注入
- 不实现 Context Compaction / Guardrail（L2-3, L2-7）
- 不实现 Permission / AccessChecker（L1-6, L1-7, L1-8）
- 不实现事件发布（AgentEventPublisher, L1-9）
- 不实现会话管理（AgentSession, L1-10）
- 不实现 Model Router / Retry / Circuit Breaker（L2-10, L3-1, L3-2）
- 不处理 IoC bean 注册（在 L1-1 DefaultAgentEngine 阶段统一注册）

## Scope

### In Scope

- `ReActAgentExecutor` 类（`io.nop.ai.agent.engine` 包）
- `SimpleToolExecuteContext` 类（`io.nop.ai.agent.engine` 包）——接受 IToolFileSystem/IThreadPoolExecutor 可选参数
- `ToolSchemaConverter` 辅助类（`io.nop.ai.agent.engine` 包）——AiToolModel.schema (XNode) → ChatToolDefinition.parameters (Map<String, Object>)
- ChatToolCall → AiToolCall 桥接方法（在 ReActAgentExecutor 内）：name→toolName, arguments→JSON input, 保留原始引用
- AiToolCallResult → ChatToolResponseMessage 桥接方法（在 ReActAgentExecutor 内）：成功用 fromToolCall, 失败用 error factory
- ChatOptionsModel → ChatOptions 转换方法：null-safe，复制公共字段，附加 tools/toolChoice
- IPromptSyntaxNode → system prompt 字符串：初始阶段使用 `getSource()`（返回原始模板）；变量解析（render(IEvalScope)）延后到 Session 集成阶段
- JUnit 5 单元测试（基于 mock IChatService 和 IToolManager）

### Out Of Scope

- IAgentEngine / Actor 模型（L1-1）
- Hook 生命周期（L1-12）
- Context Compaction（L2-3, L2-4）
- Content Guardrail（L2-7）
- Permission / AccessChecker（L1-6, L1-7, L1-8）
- AgentEventPublisher（L1-9）
- AgentSession（L1-10）
- Model Router（L2-10）
- Retry / Circuit Breaker（L3-1, L3-2）
- IoC bean 注册

## Execution Plan

### Phase 1 - ReActAgentExecutor 核心循环实现

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`

- Item Types: `Proof`

- [x] 创建 `ReActAgentExecutor` 类，实现 `IAgentExecutor` 接口
- [x] 构造函数接受 `IChatService` 和 `IToolManager`
- [x] 创建 `SimpleToolExecuteContext` 实现 `IToolExecuteContext`：构造函数接受可选的 `IToolFileSystem` 和 `IThreadPoolExecutor`（未提供时返回 null）、必选的 `File workDir`
- [x] 创建 `ToolSchemaConverter` 辅助类：将 `AiToolModel.schema`（XNode）转换为 `Map<String, Object>`（JSON Schema format）。转换策略：遍历 XNode 属性和子节点，构建 "type"/"properties"/"required" 结构；若 schema 为 null 或转换失败，返回 null（ChatToolDefinition 允许 parameters 为 null）
- [x] 实现 `execute(AgentExecutionContext ctx)` 方法，返回 `CompletableFuture`（内部使用同步循环 + `CompletableFuture.completedFuture` 包装）
- [x] 循环入口：设置 ctx.status = running，提取 system prompt：使用 `agentModel.getPrompt().getSource()`（返回原始模板字符串；初始阶段不解析变量，因为 IEvalScope 尚未从上下文注入；变量解析在后续 L1-1 阶段随 Session 集成时完善）；若 prompt 为 null 则跳过
- [x] 构建 ChatOptions：若 agentModel.chatOptions 非 null，复制 provider/model/temperature/topP/maxTokens 等公共字段；否则创建空 ChatOptions。附加 tools（ChatToolDefinition 列表）和 toolChoice = "auto"
- [x] 构建 ChatToolDefinition 列表：遍历 AgentModel.tools → `IToolManager.loadTool(name)` 获取 AiToolModel → `ChatToolDefinition.of(name, description, ToolSchemaConverter.convert(schema))`
- [x] **循环体**（while currentIteration < maxIterations）：
  - 构建 ChatRequest(messages + options)
  - 调用 `IChatService.call(request, null)` 获取 ChatResponse
  - 若 response 不成功：ctx.status = failed, ctx.lastError = response.getError() → break
  - 追加 response.getMessage()（assistant message）到 ctx.messages
  - 若 response 无 tool calls：ctx.status = completed → break
  - 遍历 ChatToolCall 列表，对每个：
    - 桥接为 AiToolCall：`toolName = chatToolCall.getName()`, `input = chatToolCall.getArgumentsText()`（ChatToolCall 已有 JSON 序列化便利方法）
    - 保留原始 ChatToolCall 引用（用于结果回灌时关联 toolCallId）
    - 调用 `IToolManager.callTool(toolName, aiToolCall, toolExecuteContext).join()`
    - 成功时（`"success".equals(result.getStatus())`）：`ChatToolResponseMessage.fromToolCall(原始chatToolCall, result.getOutput() != null ? result.getOutput().getBody() : "")`
    - 失败时（`"failure".equals(result.getStatus())` 或 error 非 null）：`ChatToolResponseMessage.error(原始chatToolCall.getId(), 原始chatToolCall.getName(), result.getError() != null ? result.getError().getBody() : "unknown error")`
    - 追加 ChatToolResponseMessage 到 ctx.messages
  - currentIteration++
- [x] 异常处理：catch 异常 → ctx.status = failed, ctx.lastError = exception.getMessage() → break
- [x] 返回 `CompletableFuture.completedFuture(AgentExecutionResult.fromContext(ctx))`

Exit Criteria:

- [x] `ReActAgentExecutor.java` 存在于 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`
- [x] 类实现 `IAgentExecutor` 接口，包含 `execute(AgentExecutionContext): CompletionStage<AgentExecutionResult>`
- [x] `SimpleToolExecuteContext.java` 存在于同一包下，实现 `IToolExecuteContext`（workDir 必选，IToolFileSystem/IThreadPoolExecutor 可选返回 null）
- [x] `ToolSchemaConverter.java` 存在于同一包下，实现 XNode → Map<String, Object> 转换
- [x] ChatToolCall → AiToolCall 桥接：name→toolName, arguments(Map)→input(JSON String)，原始 ChatToolCall 保留用于回灌
- [x] AiToolCallResult → ChatToolResponseMessage 桥接：成功用 `fromToolCall(原始toolCall, output.body)`, 失败用 `error(原始toolCall.id, name, error.body)`
- [x] ChatOptionsModel → ChatOptions：null-safe 转换，附加 tools 和 toolChoice
- [x] ReAct 循环：while (currentIteration < maxIterations) 先检查迭代上限再调 LLM；无 tool calls → completed；LLM error → failed
- [x] **端到端验证**: N/A — Phase 2 mock 测试覆盖完整 ReAct 循环路径
- [x] **接线验证**: IChatService.call 和 IToolManager.callTool 在循环中被调用（Phase 2 mock verify 证明）
- [x] **无静默跳过**: 工具执行失败时以 ChatToolResponseMessage.error 回灌；LLM 失败时设 failed 状态；无空方法体
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required（设计文档 `nop-ai-agent-react-engine.md` 已定义行为语义）
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 3 统一更新

### Phase 2 - 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestReActAgentExecutor.java`

- Item Types: `Proof`

- [x] 创建 `TestReActAgentExecutor` 测试类，使用 mock IChatService 和 IToolManager
- [x] 测试 1（testNoToolCallImmediateReturn）：LLM 返回无 tool calls 的 response → 状态 completed，1 iteration，final message 非 null
- [x] 测试 2（testSingleToolCallLoop）：LLM 返回 1 个 tool call → 工具执行成功 → LLM 再返回无 tool calls → 状态 completed，2 iterations，消息历史包含 assistant + tool response + assistant
- [x] 测试 3（testMultipleToolCalls）：LLM 返回 2 个 tool calls → 均顺序执行成功 → 结果回灌 → 继续
- [x] 测试 4（testMaxIterationsReached）：设置 maxIterations = 2，LLM 每次返回 tool calls → 第 2 次后循环终止，iterations == 2，状态 completed
- [x] 测试 5（testLlmCallFailure）：IChatService 返回错误 response → 状态 failed，error 非 null
- [x] 测试 6（testToolExecutionError）：工具执行返回失败结果（status="failure", error.body 非 null）→ 使用 ChatToolResponseMessage.error 回灌到消息历史（验证 toolCallId 为原始 String id），循环继续

Exit Criteria:

- [x] `TestReActAgentExecutor.java` 存在于 `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`
- [x] 6 个测试方法全部通过
- [x] **端到端验证**: 测试 1-4 覆盖从 execute() 入口到 AgentExecutionResult 返回的完整 ReAct 循环路径
- [x] **接线验证**: mock verify 确认 IChatService.callAsync 和 IToolManager.callTool 被调用
- [x] **无静默跳过**: 测试 6 验证工具失败以 error 形式回灌到消息历史而非静默忽略
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 3 统一更新

### Phase 3 - 收尾：Roadmap 更新与日志

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`, `ai-dev/logs/`

- Item Types: `Follow-up`

- [x] 更新 roadmap L1-5 状态从 ❌ 改为 ✅
- [x] 更新 `ai-dev/logs/` 对应日期条目

Exit Criteria:

- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-5 行状态已更新
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **端到端验证**: N/A — 纯文档更新
- [x] **接线验证**: N/A — 纯文档更新
- [x] **无静默跳过**: N/A — 纯文档更新
- [x] Owner-doc update: roadmap 已更新

## Closure Gates

- [x] ReActAgentExecutor 实现完整的 ReAct 循环（LLM → tool calls → execute → loop → result）
- [x] ChatToolCall → AiToolCall 桥接正确：name→toolName, arguments→JSON input, 原始引用保留
- [x] AiToolCallResult → ChatToolResponseMessage 桥接正确：成功用 fromToolCall, 失败用 error factory, toolCallId 正确关联
- [x] ToolSchemaConverter 实现 XNode → JSON Schema Map 转换（schema 为 null 时返回 null）
- [x] 结束条件全部正确处理（无 tool calls → completed, max iterations → completed, LLM error → failed, exception → failed）
- [x] 工具失败以 ChatToolResponseMessage.error 回灌到消息历史，非静默忽略
- [x] 单元测试覆盖 6 个场景（基本循环、单工具、并行、max iterations、LLM 失败、工具失败）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] **Anti-Hollow Check**: closure audit 已验证（a）IChatService 和 IToolManager 在 mock 测试中被实际调用（b）ReAct 循环在测试中完成至少一轮完整迭代（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成，Evidence 已写入 plan 文件 Closure 段落
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过（或确认 checkstyle 未配置于此模块时注明 N/A）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Hook 生命周期回调（L1-12）将增强 ReAct 循环的扩展性（before_reasoning, after_reasoning, before_acting, after_acting, on_error）
- Context Compaction（L2-3, L2-4）将添加 token 超限时的上下文压缩
- IAgentEngine（L1-1）将提供 Actor 入口，管理 Session 和 followUp 外层循环
- AgentEventPublisher（L1-9）将发布循环内的事件供外部订阅
- Steering 消息注入将在内层循环中支持外部干预
- 取消令牌的实际取消逻辑需 ICancelToken 传递和检查
- IoC bean 注册将在 L1-1 DefaultAgentEngine 阶段统一处理

## Closure

Status Note: All 3 phases completed. ReActAgentExecutor implements the full ReAct loop with 6 unit tests passing. Roadmap L1-5 updated to ✅.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (explore agent, task session ses_148e9f879ffeKlX6KZGriF1r0M) — independent closure audit performed 2026-06-11; re-audit by independent sub-agent (ses_148a52401ffe0XExiTFsts1Wfl) on 2026-06-11 confirmed PASS
- Evidence:
  - Phase 1 Exit Criteria: 14/14 PASS — ReActAgentExecutor.java (226 lines), SimpleToolExecuteContext.java (68 lines), ToolSchemaConverter.java (102 lines) all exist in `io.nop.ai.agent.engine`; implements IAgentExecutor with execute() returning CompletionStage<AgentExecutionResult>; ChatToolCall→AiToolCall bridge at lines 114-116 (name→toolName, getArgumentsText()→input); AiToolCallResult→ChatToolResponseMessage bridge at lines 123-135 (success: fromToolCall, error: error() factory); ChatOptionsModel→ChatOptions at lines 202-225 (null-safe field copy + tools + autoToolChoice); ReAct loop at line 71 while(currentIteration < maxIterations); no silent no-ops
  - Phase 2 Exit Criteria: 7/7 PASS — TestReActAgentExecutor.java (348 lines) with 6 @Test methods: testNoToolCallImmediateReturn, testSingleToolCallLoop, testMultipleToolCalls, testMaxIterationsReached, testLlmCallFailure, testToolExecutionError; all have meaningful assertions (status, iterations, callCount, toolCallId preservation)
  - Phase 3 Exit Criteria: 6/6 PASS — roadmap L1-5 ✅ at line 127; daily log at ai-dev/logs/2026/06-11.md
  - Closure Gates: 14/14 PASS (after independent re-audit)
  - Anti-Hollow Check: CLEAN — IChatService.call() at ReActAgentExecutor.java line 75 (delegates to callAsync); IToolManager.callTool at lines 118-119; tests 2-4-6 exercise ≥1 full iteration; ToolSchemaConverter has 77 lines of real XNode→Map conversion; SimpleToolExecuteContext getters return constructor-assigned values; no empty method bodies, no swallowed exceptions
  - `./mvnw test -pl nop-ai/nop-ai-agent -am` exit code 0 (BUILD SUCCESS, 49 tests, 0 failures)
  - checkstyle: N/A — module does not have checkstyle configuration

Follow-up:

- no remaining plan-owned work
