# Nop AI ReAct Engine

## 1. 目标

本篇定义单 Agent 的 ReAct 执行引擎设计。

这里讨论的是 Java 执行引擎，不是 DSL。

## 2. 核心问题

ReAct 引擎需要解决：

1. 如何组织 reasoning -> acting 的循环
2. 如何把 LLM 输出转换成工具调用
3. 如何把工具结果写回消息历史
4. 如何处理结束条件、错误和中断

## 3. 推荐引擎接口

### 3.1 `IAgentExecutor`

建议抽象统一执行器接口：

```java
public interface IAgentExecutor {
    CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx);

    Flow.Publisher<AgentEvent> executeStream(AgentExecutionContext ctx);
}
```

### 3.2 `ReActAgentExecutor`

- 第一阶段核心执行器
- 实现标准 ReAct loop

## 4. 执行上下文

建议 `AgentExecutionContext` 包括：

- `AgentRuntimeModel agentModel`
- `List<ChatMessage> messages`
- `AgentPlan plan`
- `String sessionId`
- `ICancelToken cancelToken`
- `IToolExecuteContext toolContext`
- `ChatOptions effectiveOptions`
- 预算与状态字段

## 5. ReAct 主循环

推荐循环：

```text
build request
 -> before_reasoning
 -> call LLM
 -> after_reasoning
 -> extract tool calls
 -> if empty: finish
 -> before_acting
 -> execute tools
 -> after_acting (per tool result)
 -> append tool response messages
 -> next iteration
```

## 6. 结束条件

引擎必须显式处理：

- 无工具调用
- 达到 `maxIterations`
- 外部取消
- 不可恢复错误

## 7. 消息桥接

引擎需要两个关键桥接：

- request bridge
- tool result bridge

## 8. 与 Hook 的关系

ReAct 引擎只暴露少量关键生命周期点：

- `before_reasoning`
- `after_reasoning`
- `before_acting`
- `after_acting`
- `on_error`

Hook 负责增强，引擎负责主流程。

## 9. 本篇结论

ReAct 是当前阶段最合适的单 Agent Java 执行引擎模型。
