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

## 5. 双循环与 ReAct 主循环

### 5.1 双循环模型

ReAct 引擎采用双循环结构（参见 `02-execution-model.md`）：

**外层循环（followUp 循环）**：

- Agent 完成一次完整执行后，检查 followUp 队列
- 如果有排队的后续消息 → 注入消息，启动新的内层循环
- 如果没有 → Agent 执行结束，发布最终结果

**内层循环（steering + ReAct 循环）**：

- 标准 ReAct 循环（见 §5.2）
- 每轮工具执行后检查 steering 队列
- steering 消息注入后跳过剩余工具，进入下一轮推理

### 5.2 ReAct 内层循环

推荐内层循环的行为语义：

```
build request
 -> before_reasoning
 -> call LLM (返回完整 assistant message)
 -> after_reasoning
 -> extract tool calls
 -> if empty: 跳出内层循环
 -> check steering queue:
    -> if has steering: 注入 steering, 跳出当前轮
 -> before_acting
 -> execute tools (支持并行)
 -> after_acting (per tool result)
 -> append tool response messages
  -> check token budget: if exceeded, trigger compaction (见 reliability.md §7)
  -> next iteration
```

循环粒度是完整消息：引擎在收到 LLM 的完整响应后才做决策，不在流式输出过程中做判断。

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
