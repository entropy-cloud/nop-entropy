# Nop AI Agent Runtime Semantics

## 1. 目标

本篇不再作为“主设计文档”，而是专门解释：已有 DSL 在 runtime 中如何被解释。

它在当前目录中属于“语义映射层”，位于 DSL 层与 Java 引擎层之后。

## 2. 核心原则

Runtime 语义文档只回答：

1. `agent.xdef` 的字段如何影响运行时
2. `agent-plan.xdef` 如何参与执行和恢复
3. `tool.xdef` / `tool-call.xdef` 如何进入工具调用链

它不重复 schema 字段列表。

## 3. `agent.xdef` 的 runtime 解释

### 3.1 `prompt`

- 作为 Agent 的基础 system prompt
- 在运行时可叠加 Skill、Plan、Compression 等附加指令

### 3.2 `chatOptions`

- 作为本 Agent 的默认聊天配置
- 被请求级参数覆盖

### 3.3 `tools`

- 决定当前 Agent 可见的工具集合
- 运行时据此加载工具定义并向模型暴露工具能力

### 3.4 `constraints`

- `maxIterations` -> ReAct 最大轮数
- `toolTimeoutSeconds` -> 工具默认超时
- `maxParallelTools` -> 单轮工具并发上限
- `tokenCompressionThreashold` -> 压缩检查阈值

### 3.5 `hooks`

- 在 runtime 的关键生命周期点被触发
- 事件匹配、优先级、失败传播由 Hook 语义决定

## 4. `agent-plan.xdef` 的 runtime 解释

- `goal` 用于保留总任务意图
- `currentPhase` 和 `status` 用于恢复和进度追踪
- `phases/tasks/subTasks` 用于结构化任务推进
- `questions/decisions/errors/additionalNotes` 用于保留认知轨迹和恢复信息

其中应明确区分：

- runtime 强消费并强校验的 hard contract 部分
- AI 可自由维护的 narrative 部分

plan completion 的关键规则建议是：

- 只要 hard contract 未满足，runtime 就不能允许 plan 结束

当前阶段要明确区分：

- DSL 已能表达 plan 结构
- runtime 不一定已经实现全自动依赖调度

## 5. 工具 DSL 的 runtime 解释

### 5.1 `tool.xdef`

- `schema` 和 `examples` 用于 XML tool calling 提示和解析
- `description` 用于工具说明和模型提示

### 5.2 `tool-call.xdef`

- `id` 用于事件和结果关联
- `timeoutMs` 覆盖默认工具超时
- 未知 tag 负载进入具体执行器参数解析

### 5.3 `call-tools.xdef`

- 表示一轮 acting 中的批量工具调用
- `paralllel` 和 `maxConcurrency` 影响执行策略

### 5.4 `call-agent.tool.xml`

- `call-agent` 是一类特殊工具，而不是旁路协议
- `agentId`、`sessionId`、`skills`、`inheritContext` 的解释应以真实 `.tool.xml` 为准
- `agent-result` 是其结果载体

runtime 在这里要做的，是解释这些已有字段，而不是重新定义另一套 `call-agent` DSL

## 6. 事件语义

Runtime 在流式模式下建议产生这些稳定事件：

- `TextChunk`
- `ThinkingChunk`
- `ToolCallStart`
- `ToolCallComplete`
- `AgentResult`
- `AgentError`
- `AgentInterrupted`

这些事件是 runtime 对 DSL 执行过程的外部可观察投影。

## 7. Hook 语义

建议固定：

- `before_reasoning`
- `after_reasoning`
- `before_acting`
- `after_acting`
- `on_error`

其中 `after_acting` 固定为单个工具结果回调，而不是整批回调。

## 8. 本篇结论

Runtime 文档的正确位置是“解释 DSL”，而不是取代 DSL。

先有 DSL，再有 runtime 语义；先讲 schema，再讲执行解释。
