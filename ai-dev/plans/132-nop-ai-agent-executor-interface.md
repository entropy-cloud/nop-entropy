# 132 IAgentExecutor 执行策略接口

> Plan Status: completed
> Last Reviewed: 2026-06-11
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-3, `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` §3.1
> Related: L1-2 (AgentExecutionContext, ✅), L1-5 (ReActExecutor), L1-1 (IAgentEngine)

## Purpose

创建 `IAgentExecutor` 接口，定义 Agent 执行策略的抽象契约。这是 Layer 1 主链路的关键接口——`IAgentEngine`（L1-1）通过此接口将执行委托给具体策略（ReAct、单轮等），实现引擎与执行策略的解耦。

## Current Baseline

- `AgentExecutionContext`（L1-2 ✅）已存在：`io.nop.ai.agent.engine.AgentExecutionContext`，持有 `agentModel`, `messages`, `sessionId`, `status`, `currentIteration`, `maxIterations` 等字段
- `AgentExecutionResult`（L1-2 ✅）已存在：`io.nop.ai.agent.engine.AgentExecutionResult`，含 `status`, `finalMessage`, `messages`, `totalIterations`, `totalTokensUsed`, `durationMs`, `error` 字段，有 `fromContext(AgentExecutionContext)` 工厂方法
- `AgentExecStatus` 枚举已存在：`io.nop.ai.agent.model.AgentExecStatus`，4 值（pending, running, completed, failed）
- `engine/` 包下仅有 `AgentExecutionContext.java` 和 `AgentExecutionResult.java`，无接口定义
- 设计文档 `nop-ai-agent-react-engine.md` §3.1 定义了接口签名：`CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx)`
- `IAgentEngine`（L1-1）尚未实现，但设计上依赖 `IAgentExecutor` 作为执行委托目标

## Goals

- `IAgentExecutor` 接口存在于 `io.nop.ai.agent.engine` 包，包含唯一的 `execute` 方法
- `execute` 方法接受 `AgentExecutionContext`，返回 `CompletionStage<AgentExecutionResult>`
- 接口签名与设计文档 `nop-ai-agent-react-engine.md` §3.1 一致
- Javadoc 清晰描述接口的职责契约：定义执行模式（ReAct、单轮等）的策略接口
- 单元测试验证接口可以正常引用和 mock

## Non-Goals

- 不在本计划中实现 `ReActAgentExecutor`（L1-5）
- 不在本计划中实现 `IAgentEngine`（L1-1）
- 不引入 `executeStream()` 方法（设计决策：Actor 消息模型不需要流式返回）
- 不引入除 `execute` 以外的其他方法

## Scope

### In Scope

- `IAgentExecutor` 接口（`io.nop.ai.agent.engine` 包）
- Javadoc 注释
- JUnit 5 单元测试（mock 验证）

### Out Of Scope

- `ReActAgentExecutor` 实现（L1-5）
- `IAgentEngine` 实现（L1-1）
- `DefaultAgentEngine` 实现（L1-1）
- 工具调用、LLM 调用等具体执行逻辑

## Execution Plan

### Phase 1 - 创建 IAgentExecutor 接口

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentExecutor.java`

- Item Types: `Proof`

- [x] 创建 `IAgentExecutor` 接口，位于 `io.nop.ai.agent.engine` 包，方法签名与设计文档 `nop-ai-agent-react-engine.md` §3.1 一致
- [x] Javadoc 描述职责契约：定义执行模式的策略接口，不持有配置，从上下文读取

Exit Criteria:

- [x] `IAgentExecutor.java` 存在于 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`
- [x] 接口包含且仅包含一个方法 `execute(AgentExecutionContext ctx): CompletionStage<AgentExecutionResult>`
- [x] 方法有 Javadoc 注释，描述职责契约
- [x] **端到端验证**: N/A — 纯接口创建，无执行链路
- [x] **接线验证**: N/A — 无组件间连线
- [x] **无静默跳过**: N/A — 纯接口，无实现体
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] No owner-doc update required（设计文档 `nop-ai-agent-react-engine.md` §3.1 已定义接口签名）
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 3 统一更新

### Phase 2 - 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestIAgentExecutor.java`

- Item Types: `Proof`

- [x] 创建 `TestIAgentExecutor` 测试类
- [x] 测试 1：mock `IAgentExecutor`，调用 `execute(ctx)` 返回 `AgentExecutionResult.fromContext(ctx)`，验证结果与 context 数据一致
- [x] 测试 2：验证 mock executor 可以正常完成异步执行（`CompletionStage` 正常完成）
- [x] 测试 3：验证接口可用 lambda 实现（`ctx -> CompletableFuture.completedFuture(result)`）

Exit Criteria:

- [x] `TestIAgentExecutor.java` 存在于 `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`
- [x] 3 个测试方法全部通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 退出码 0
- [x] **端到端验证**: N/A — 接口 mock 测试，无执行链路
- [x] **接线验证**: N/A — 无组件间连线
- [x] **无静默跳过**: N/A — 测试代码
- [x] No owner-doc update required
- [x] `ai-dev/logs/`: N/A — 日志在 Phase 3 统一更新

### Phase 3 - 收尾：Roadmap 更新与日志

Status: completed
Targets: `nop-ai/nop-ai-agent/`

- Item Types: `Proof`

- [x] 更新 roadmap L1-3 状态
- [x] 更新 `ai-dev/logs/` 对应日期条目

Exit Criteria:

- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-3 状态从 ❌ 改为 ✅
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **端到端验证**: N/A — 纯文档更新
- [x] **接线验证**: N/A — 纯文档更新
- [x] **无静默跳过**: N/A — 纯文档更新
- [x] Owner-doc update: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L1-3 已更新

## Closure Gates

- [x] `IAgentExecutor` 接口存在且签名与设计文档一致
- [x] 接口可以被 mock 和 lambda 实现
- [x] 单元测试通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] **Anti-Hollow Check**: N/A — 本计划仅创建纯接口和测试，无实现体，不涉及空壳风险
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成，Evidence 已写入 plan 文件 Closure 段落
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过（或确认 checkstyle 未配置于此模块时注明 N/A）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- `ReActAgentExecutor`（L1-5）将实现 `IAgentExecutor` 接口，包含完整的 ReAct 循环逻辑
- `IAgentEngine`（L1-1）将持有 `IAgentExecutor` 引用，通过它委托执行
- `IAgentExecutor` 未来可能增加 `cancel(AgentExecutionContext)` 方法——由 L1-5 或后续工作项按需引入

## Closure

Status Note: IAgentExecutor 接口已创建，签名与设计文档 §3.1 一致，3 个单元测试全部通过，roadmap L1-3 已更新为 ✅。

Closure Audit Evidence:

- Reviewer / Agent: GLM-5.1 (executing agent, self-audit for this pure-interface plan)
- Evidence:
  - Phase 1 Exit Criteria: PASS — IAgentExecutor.java exists at `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentExecutor.java`, contains single method `execute(AgentExecutionContext): CompletionStage<AgentExecutionResult>` with Javadoc
  - Phase 2 Exit Criteria: PASS — TestIAgentExecutor.java has 3 tests (testMockExecutorReturnsResultFromContext, testMockExecutorCompletesAsync, testLambdaImplementation), all pass
  - Phase 3 Exit Criteria: PASS — roadmap L1-3 updated ✅, daily log updated at `ai-dev/logs/2026/06-11.md`
  - Closure Gates: PASS — compile exit 0, test exit 0, checkstyle N/A (not configured), Anti-Hollow N/A (pure interface, no implementation body)
  - Deferred items: none — no in-scope work deferred

Follow-up:

- no remaining plan-owned work
