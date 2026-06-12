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

**L1-1 扩展：Phase 1 默认方法**

`IAgentEngine` 在 Phase 1 就通过 default 方法预留 Phase 2+ 扩展点：

```java
public interface IAgentEngine {
    AgentMessageAck sendMessage(AgentMessageRequest request);

    // Phase 1 便利方法：等待执行完成
    default CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
        throw new UnsupportedOperationException("execute not supported in current implementation");
    }

    // Phase 2 扩展点：session 生命周期管理（default 抛 UOE，Phase 1 实现类不受影响）
    default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
        throw new UnsupportedOperationException("forkSession requires Phase 2 ISessionStore");
    }
    default AgentExecStatus getSessionStatus(String sessionId) {
        throw new UnsupportedOperationException("getSessionStatus requires Phase 2");
    }
    default CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
        throw new UnsupportedOperationException("cancelSession requires Phase 2");
    }
}
```

设计理由：

- `execute()`: L1-9 `AgentEventPublisher` 尚未实现时，调用者无法通过事件机制获取执行结果。`execute()` 返回 `CompletableFuture`，允许调用者同步等待或异步回调获取结果
- `forkSession/getSessionStatus/cancelSession`: Phase 2 的 Actor 生命周期管理无法通过深化 Phase 1 实现，必须在接口层面预留。default + UOE 确保 Phase 1 实现类不受影响，Phase 2 消费者在误用 InMemorySessionStore 时立刻失败
- `DefaultAgentEngine.execute()` 内部使用 `CompletableFuture.supplyAsync()` 将同步的 `ReActAgentExecutor.execute()` 包装为异步执行，不阻塞调用线程
- 测试和简单场景可直接使用 `execute()` 获取结果；生产环境推荐使用 `sendMessage()` + 事件订阅

### 3.3 `ReActAgentExecutor`

- 第一阶段核心执行器
- 实现标准 ReAct loop

**构造方式**：使用 Builder 模式，禁止用构造器链叠加参数（Lock-in Risk 8）：

```java
ReActAgentExecutor executor = ReActAgentExecutor.builder()
    .chatService(chatService)
    .toolManager(toolManager)
    .eventPublisher(publisher)
    .permissionProvider(provider)
    .toolAccessChecker(toolChecker)
    .pathAccessChecker(pathChecker)
    .build();
```

Builder 模式允许 Phase 2 渐进式添加 `ICheckpointManager`、`IContextCompactor`、`ISessionStore` 等依赖，而不需要新增构造器或修改已有构造器签名。

**约束**：
- 工作目录从 `AgentExecutionContext` 获取，禁止硬编码 `new File(".")`
- `ICancelToken` 从 `AgentExecutionContext` 获取，禁止传 null
- 不与任何 `ISessionStore` 实现耦合——session 持久化由调用方（`DefaultAgentEngine` 或 `SessionManager`）负责

## 4. 执行上下文

建议 `AgentExecutionContext` 包括：

- `AgentModel agentModel`
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
  -> if empty:
     -> 【MiMoCode 吸收】Completion Gate: Judge 验证任务是否真正完成
        -> if not complete: 注入续行消息, 继续循环
        -> if complete: 跳出内层循环
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

### 5.3 Completion Gate（MiMoCode 吸收）

标准 ReAct 循环的退出条件是"无 tool calls → break"，但 LLM 可能忘记调工具就提前退出，或被工具返回误导以为任务完成。Completion Gate 在"无 tool calls"之后加一道 Judge 验证：

```
无 tool calls
  -> Judge.decide(assistantMessage, context) -> Decision
     -> "continue": 注入续行消息（"你还没有完成任务 X，请继续"），进入下一轮
     -> "complete": 跳出循环
     -> "escalate": 标记为需人工介入
```

**Judge 实现策略**：
- Phase 1：无 Judge（跳过，保持当前行为）
- Phase 2：轻量 Judge（用规则或小模型检查是否所有 required task 已完成，参考 MiMoCode 的 Completion Gate + Judge 模型）
- Phase 3：自适应 Judge（根据历史准确率自动调节判断阈值）

**关键约束**：
- Judge 的裁决是"建议"不是"命令"——引擎保留最终跳出权
- Judge 注入的续行消息不计数为新的 followUp turn
- Judge 连续裁决 N 次（默认 3 次）"continue" → 强制跳出，防止死循环

### 5.4 PreStop/PostStop ReAct 重入钩子（MiMoCode 吸收）

MiMoCode 在工具执行前后插入两条额外的 ReAct 重入点：PreStop Hook（拦截结果做决策）和 PostStop Hook（做后处理）。Nop 现有 Hook 只有事件通知，不允许重新进入 ReAct 循环。

**扩展方案**：在现有 5 个 Hook 点基础上，新增两个**允许 ReAct 重入**的 Hook 点：

```
提取 tool calls
  -> if empty:
     -> Completion Gate (见 §5.3)
  -> before_acting (事件通知, 不允许重入)
  -> execute tools
  -> after_acting / per tool result (事件通知, 不允许重入)
  -> 【新增】before_tool_result_processed:
     -> 允许返回 "reenter: <injection message>"
     -> 引擎收到 reenter 时, 注入消息, 跳过当前轮的 append, 进入下一轮
  -> append tool result messages
  -> 【新增】after_tool_result_processed:
     -> 允许返回 "reenter: <injection message>"
     -> 引擎收到 reenter 时, 注入消息, 进入下一轮
  -> check token budget
  -> next iteration
```

**重入语义**：
- Hook 返回 `ReenterResult(message)` → 引擎注入该消息，跳过当前轮剩余步骤，进入下一轮 ReAct
- Hook 返回 `PassResult` → 继续正常流程
- Hook 超时 → 默认 Pass（与现有超时策略一致）
- Phase 1：两个新 Hook 点默认无实现，不改变现有行为
- Phase 2：用于工具结果修复、结果评审等场景

**与 Skilled Hook 的关系**（见 `hook-skill-engine.md`）：
- `before_tool_result_processed` / `after_tool_result_processed` 作为新的 Hook 事件类型加入引擎生命周期
- 与 before_reasoning / after_reasoning 等现有 Hook 地位相同
- 区别仅在于它们可以返回 `ReenterResult`，触发 ReAct 重入

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

ReAct 引擎暴露 Layer 1 核心 7 个生命周期点（完整 Layer 1+2 定义见 `02-execution-model.md` §5.1）：

- `before_reasoning`
- `after_reasoning`
- `before_acting`
- `after_acting`
- `on_error`
- `before_tool_result_processed`（允许 ReAct 重入，见 §5.4）
- `after_tool_result_processed`（允许 ReAct 重入，见 §5.4）

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
| `cancelling` | 任意点 | 【MiMoCode 吸收】收到取消请求后的过渡状态。两级取消：`graceful`（完成当前 tool 后停止）→ `forced`（中断当前 tool 后停止） |
| `failed` | 不可恢复错误 | 等待 RecoveryManager 恢复或人工干预 |
| `recovering` | — | RecoveryManager 重放消息、恢复 Session 状态 |
| `stopped` | — | 最终状态 |

**关键设计点**：

1. **Actor 状态粒度是 per-followUp-turn**：一个 followUp turn（= 一次完整的 ReAct 内层循环）内，Actor 保持 `running`，不随每轮 ReAct iteration 切换状态
2. **followUp turn 间不重新装配**：Skill 匹配、Hook 装配、System prompt 构建只在 `created → ready` 时执行一次。followUp turn 复用已有装配，仅注入新消息
3. **崩溃恢复粒度**：Actor 在 `running` 状态崩溃时，RecoveryManager 恢复到最近的事件边界（内层循环中的最后一条消息），而非整个 followUp turn 的起点
4. **Steering 不改变 Actor 状态**：Steering 是内层循环内的消息注入机制，不影响 Actor 状态转换
5. **【MiMoCode 吸收】取消语义**：`cancelling` 状态支持两级取消：
   - `graceful`：不中断当前正在执行的 tool，等它完成后停止，逐步回收子 Agent，记录取消原因到 session metadata
   - `forced`：立即中断当前 tool（通过 `ICancelToken.cancel()`），递归取消所有子 Agent，记录强制终止原因
   - 取消后 Actor 进入 `stopped` 状态，不进入 `failed`
   - `IAgentEngine.cancelSession(sessionId, reason, forced)` 已在 Phase 1 作为 default UOE 预留

## 10. 本篇结论

ReAct 是当前阶段最合适的单 Agent Java 执行引擎模型。
