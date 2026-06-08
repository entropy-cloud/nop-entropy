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
}
```

> **设计决策**：移除 `executeStream()` 方法。nop-ai-agent 采用 Actor 消息模型——引擎内部以完整消息粒度执行，事件通过 `AgentEventPublisher` 独立发布，外部订阅者通过 `IMessageService` 的 topic 订阅机制接收事件。不需要通过 `Flow.Publisher` 返回值拉取事件流。

### 3.2 `IAgentEngine` — Actor 消息入口

`IAgentEngine` 是外部调用者（API/Gateway/IM Channel）与 Agent 交互的顶层接口，遵循 Actor 消息模型：

```java
public interface IAgentEngine {
    /**
     * 向 Agent 发送消息。
     * - 如果给定 sessionId，复用已有 Session 的 AgentActor
     * - 如果 sessionId 为空，创建新 Session + 新 AgentActor
     * - 立即返回 ack（含 sessionId），Agent 异步执行
     * - 执行结果通过 AgentEventPublisher 异步推送
     */
    AgentMessageAck sendMessage(AgentMessageRequest request);
}

public class AgentMessageRequest {
    String sessionId;       // 空=新建 Session，非空=复用已有 Session
    String agentName;       // Agent 配置名（agent.xml name）
    String userMessage;     // 用户消息内容
    Map<String, Object> metadata;  // 可选元数据（channel 类型、租户信息等）
}

public class AgentMessageAck {
    String sessionId;       // 新建或复用的 Session ID
    String status;          // "accepted" — 消息已投递到 Actor Mailbox
}
```

**交互流程**：

```
调用者                        IAgentEngine                     AgentActor
  │                                │                              │
  ├── sendMessage(request) ──────→ │                              │
  │                                ├── lookupOrCreateActor(sid)   │
  │                                ├── actor.mailbox.offer(msg)   │
  │←── ack(sessionId, "accepted") │                              │
  │                                │                              ├── dispatch message
  │                                │                              ├── ReAct loop...
  │                                │                              │
  │  （通过 AgentEventPublisher 订阅事件）                         │
  │←── AgentResult event ─────────┤←── publish(AgentResult) ────┤
  │←── AgentError event ──────────┤←── publish(AgentError) ─────┤
```

**设计要点**：
- `sendMessage` 是同步方法，立即返回，不等待 Agent 执行完毕
- 调用者通过 `AgentEventPublisher` 订阅事件（topic: `agent.{sessionId}.events`），接收执行过程中的事件和最终结果
- 同一 sessionId 的多次 `sendMessage` 调用会依次投递到同一 AgentActor 的 Mailbox，Actor 串行处理
- AgentActor 执行过程中收到新消息，进入 followUp 队列，当前 ReAct 循环结束后处理

### 3.3 `ReActAgentExecutor`

- 第一阶段核心执行器
- 实现标准 ReAct loop

## 4. 执行上下文

建议 `AgentExecutionContext` 包括：

- `AgentModel agentModel`
- `List<CanonicalMessage> messages`
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

ReAct 引擎暴露 Layer 1 核心 5 个生命周期点（完整 Layer 1+2 定义见 `02-execution-model.md` §5.1）：

- `before_reasoning`
- `after_reasoning`
- `before_acting`
- `after_acting`
- `on_error`

Hook 负责增强，引擎负责主流程。

## 9. Actor 状态机与 ReAct 循环的映射

AgentActor 的生命周期状态（`actor-runtime-vision.md` §3.2）与 ReAct 双循环的对应关系：

| Actor 状态 | ReAct 循环位置 | 说明 |
|-----------|---------------|------|
| `created` | — | 收到初始请求，加载 AgentModel，初始化 Session |
| `ready` | — | 准备就绪，即将进入执行 |
| `running` | 内层 ReAct 循环执行中 | 包含 LLM 调用、工具执行、Steering 检查。每轮 ReAct iteration 不改变 Actor 状态 |
| `idle` | 内层循环结束 + followUp 为空 | Agent 完成一轮执行，等待新消息（followUp 或外部 sendMessage） |
| `running` | followUp 消息注入 → 新内层循环 | `idle` → `running`，不重新初始化 Session |
| `failed` | 不可恢复错误 | 等待 RecoveryManager 恢复或人工干预 |
| `recovering` | — | RecoveryManager 重放消息、恢复 Session 状态 |
| `stopped` | — | 最终状态 |

**关键设计点**：

1. **Actor 状态粒度是 per-followUp-turn**：一个 followUp turn（= 一次完整的 ReAct 内层循环）内，Actor 保持 `running`，不随每轮 ReAct iteration 切换状态
2. **followUp turn 间不重新装配**：Skill 匹配、Hook 装配、System prompt 构建只在 `created → ready` 时执行一次。followUp turn 复用已有装配，仅注入新消息
3. **崩溃恢复粒度**：Actor 在 `running` 状态崩溃时，RecoveryManager 恢复到最近的事件边界（内层循环中的最后一条消息），而非整个 followUp turn 的起点
4. **Steering 不改变 Actor 状态**：Steering 是内层循环内的消息注入机制，不影响 Actor 状态转换

## 10. 本篇结论

ReAct 是当前阶段最合适的单 Agent Java 执行引擎模型。
