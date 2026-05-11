# Nop AI Agent Engine

## 1. 目标

本篇定义 `nop-ai-agent` 的 Java 引擎层总体设计。

需要明确一件事：

- DSL 和底层执行引擎是两个层面
- `xdef` 负责描述配置形态
- Java 引擎负责执行、调度、状态管理和生命周期控制

因此，设计文档不能只有 DSL，也必须有引擎层设计。

## 2. 引擎层的职责

Java 引擎层负责下面这些事情：

1. 装载 DSL 配置并构建运行时对象
2. 驱动单 Agent 的执行循环
3. 管理消息、工具、Hook、Skill、Plan、Memory 的协作
4. 提供事件、取消、中断、状态和结果模型
5. 连接会话存储、安全控制和可靠性策略

不属于引擎层职责的内容：

- 定义 DSL schema
- 重写工具 DSL
- 改写 `xdef` 本身

## 3. 分层关系

建议把整体分成四层：

### 3.1 DSL Layer

- `agent.xdef`
- `agent-plan.xdef`
- `tool.xdef`
- `tool-call.xdef`
- `call-agent.tool.xml`

### 3.2 Engine Layer

- Agent 执行接口
- ReAct loop
- Hook / Skill 装配
- Session runtime

### 3.3 Mapping Layer

- DSL -> runtime object
- runtime event -> external result
- tool result -> message history

### 3.4 Policy Layer

- security
- reliability
- storage strategy

## 4. 引擎层核心对象

建议引擎层至少有这些核心对象：

### 4.1 `IAgentEngine`

- 顶层执行入口
- 负责启动一次 Agent 执行

### 4.2 `AgentRuntimeModel`

- 由 `agent.xdef` 装载后得到的运行时配置对象
- 是 DSL 到引擎的映射结果，而不是原始 XML 本身

### 4.3 `AgentExecutionContext`

- 表示一次执行会话的内存态上下文
- 包括消息、session、plan、预算、状态、取消令牌等

### 4.4 `IAgentExecutor`

- 负责执行某种 Agent 运行模式
- 第一阶段主要实现 ReAct executor

### 4.5 `AgentResultPublisher`

- 负责把内部状态变化投影成流式事件或最终结果

## 5. 引擎层设计原则

### 5.1 配置和执行分离

- DSL 描述“应该怎样配置”
- 引擎对象描述“实际怎样运行”

### 5.2 一次执行一个上下文

单次 Agent 执行必须有独立的 `AgentExecutionContext`。

### 5.3 消息和工具统一回路

引擎设计必须保证：

- LLM 输出和工具结果最终都回到统一消息历史

### 5.4 Hook/Skill 是引擎扩展点

它们不是单独平台，而是挂接在引擎生命周期上的扩展机制。

## 6. 第一阶段建议固定的引擎边界

建议第一阶段明确下面这些边界：

1. 只做单 Agent 执行引擎
2. 主执行模型为 ReAct
3. 事件和结果由统一 publisher 负责
4. Hook/Skill 不重写主循环，只能增强主循环
5. Session runtime 与持久化策略分开

## 7. 本篇结论

`nop-ai-agent` 的设计必须同时有：

- DSL 设计
- Java 引擎设计

两者不是替代关系，而是配置层与执行层的分工关系。
