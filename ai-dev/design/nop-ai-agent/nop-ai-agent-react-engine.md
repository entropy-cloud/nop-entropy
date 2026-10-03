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

**L1-1 扩展：接口形态与 default 方法**

`IAgentEngine` 的 live 接口形态（锚点 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java`）：`sendMessage`（`:13`）与 `execute`（`:15`）是**抽象方法**；其余 session 生命周期方法均为 default 方法，**未实现该 API 的引擎 fail-fast 抛 `NopAiAgentException(ERR_AGENT_*_NOT_SUPPORTED)`**（`DefaultAgentEngine` 已全部实现）：

```java
public interface IAgentEngine extends AutoCloseable {
    // 消息入口（同步返回 ack，Agent 异步执行）
    AgentMessageAck sendMessage(AgentMessageRequest request);

    // 阻塞式便利入口：等待执行完成（返回完整结果）
    CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);

    // session 生命周期管理（default 抛 NopAiAgentException(ERR_AGENT_*_NOT_SUPPORTED)）
    default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_FORK_SESSION_NOT_SUPPORTED);
    }
    default AgentExecStatus getSessionStatus(String sessionId) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_GET_SESSION_STATUS_NOT_SUPPORTED);
    }
    default CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_CANCEL_SESSION_NOT_SUPPORTED);
    }

    // 已实现的恢复/生命周期入口（锚点：IAgentEngine.java 方法行）
    default CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RESUME_SESSION_NOT_SUPPORTED);
    }
    default CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RESTORE_SESSION_NOT_SUPPORTED);
    }
    default CompletableFuture<AgentExecutionResult> wakeSession(String sessionId) {
        throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "wakeSession not supported by this engine");
    }
    default SessionRestoreSummary restorePendingSessions(String approver, String reason) {
        throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RESTORE_PENDING_SESSIONS_NOT_SUPPORTED);
    }
    @Override
    default void close() throws Exception {
    }
}
```

设计理由：

- `execute()`: L1-9 `AgentEventPublisher` 尚未实现时，调用者无法通过事件机制获取执行结果。`execute()` 返回 `CompletableFuture`，允许调用者同步等待或异步回调获取结果。`execute` 是抽象方法（`IAgentEngine.java:15`），`DefaultAgentEngine` 实现（`DefaultAgentEngine.java:690`）内部使用 `CompletableFuture.supplyAsync()`（`:839`）将同步的 `ReActAgentExecutor.execute()` 包装为异步执行，不阻塞调用线程
- `forkSession/getSessionStatus/cancelSession`: Phase 2 的 Actor 生命周期管理，均已在 `DefaultAgentEngine` 中实现。`forkSession` 创建独立子 session，发布 `SESSION_FORKED` 事件；`getSessionStatus` 从 store 查询状态；`cancelSession` 支持 graceful/forced 两级语义（`DefaultAgentEngine.java:565`）。接口 default 方法仍抛 `ERR_AGENT_*_NOT_SUPPORTED` 以保护其他尚未覆盖的 `IAgentEngine` 实现
- **5 个已实现 API**（`DefaultAgentEngine` 委托 `AgentSessionLifecycle` 全部落地，锚点 `IAgentEngine.java` 方法行）：`resumeSession`（sticky-pause 恢复，`:79`，实现 `AgentSessionLifecycle.java:222`）、`restoreSession`（崩溃重启恢复，`:118`，实现 `:500`）、`wakeSession`（WAIT_FOR 条件唤醒，`:145`，实现 `:395`）、`restorePendingSessions`（启动批量扫描恢复，`:217`）、`close`（生命周期终止，`:240`）
- `sendMessage`/`execute` 关系：二者是同一 Actor 消息模型的两个对外视图——`sendMessage` 是 fire-and-forget 消息入口（立即返回 `AgentMessageAck`，经 mailbox 投递、followUp 队列），`execute` 是阻塞式结果入口（返回 `CompletableFuture<AgentExecutionResult>`）。connector 用 `execute()` 取响应文本，异步触发场景用 `sendMessage()` + 事件订阅（详见 `nop-ai-agent-channel-connector.md` §7.2）
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
- steering 检查点在 **round 边界**（现状）——每轮工具执行全部完成、工具结果写回消息列表之后、进入下一轮 LLM 调用之前，drain steering 队列并把消息 append 到消息列表（追加新消息，非修改历史），随后进入下一轮推理（实现锚点：`ReActAgentExecutor` 的 round 边界 drain，见 §5.2）
- steering 与长时工具等待的演进方向为**双相位唤醒 + 心跳**（已裁定目标形态，见 §5.5；与 `02-execution-model.md` §四一致）

### 5.2 ReAct 内层循环

内层循环的实际行为语义（与 `ReActAgentExecutor` 实现一致）：

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
 -> before_acting
 -> execute tools (支持并行)
 -> after_acting (per tool result)
 -> append tool response messages
 -> 检查 token budget: if exceeded, trigger compaction (见 reliability.md §7)
 -> 【round 边界】所有工具执行完成后 drain steering 队列（ctx.drainSteering()）
    -> 有 steering 消息: append 到消息列表, 进入下一轮推理（不跳过本轮已执行工具的结果）
    -> 无 steering 消息: 正常进入下一轮
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
      -> 引擎收到 reenter 时, 注入该消息 (用真实 tool_call_id, 保持配对), 跳过当前 tool 的真实结果,
         继续处理本批其余 tool 结果 (不丢弃), 全部处理完后在 iteration 层级注入重入标记消息
   -> append tool result messages
   -> 【新增】after_tool_result_processed:
      -> 允许返回 "reenter: <injection message>"
      -> 引擎收到 reenter 时, 设重入标志, 继续处理本批其余 tool 结果,
         全部处理完后在 iteration 层级注入重入标记消息
  -> check token budget
  -> next iteration
```

**重入语义**：
- Hook 返回 `ReenterResult(message)` → 引擎注入该消息（BEFORE 分支用真实 tool_call_id 保持配对），设重入标志，继续处理本批全部 tool 结果（不丢弃任何一个，保持 tool_call_id 配对完整），全部处理完后在 iteration 层级注入一条重入标记 user 消息，进入下一轮 ReAct
- Hook 返回 `PassResult` → 继续正常流程
- 达配额上限（`DEFAULT_MAX_REENTRIES`，per-iteration 窗口）→ 降级为 PassResult + WARN 日志
- Hook 超时 → 默认 Pass（与现有超时策略一致）
- Phase 1：两个新 Hook 点默认无实现，不改变现有行为
- Phase 2：用于工具结果修复、结果评审等场景

**与 Skilled Hook 的关系**（见 `nop-ai-agent-hook-skill-engine.md`）：
- `before_tool_result_processed` / `after_tool_result_processed` 作为新的 Hook 事件类型加入引擎生命周期
- 与 before_reasoning / after_reasoning 等现有 Hook 地位相同
- 区别仅在于它们可以返回 `ReenterResult`，触发 ReAct 重入

### 5.5 长时工具等待与唤醒（已裁定目标形态）

数十分钟级工具（如构建命令）使纯 round 边界交互不可接受：等待期间模型零感知、steering 消息最长等到全部工具完成才被消费。裁定方向为**等待优先 + 双唤醒源**，round 同步等待保持为默认路径：

```
工具 fan-out 执行（现状不变）
 -> 等待期间 mailbox/steering 队列保持可见，心跳 timer 同步运行
 -> steering 到达（模型此刻空闲，无在飞 LLM 请求，无需打断机制）:
      构造瞬时请求 = 消息列表 + 运行中工具占位 + steering 消息
      → LLM 推理 → 响应入史 → 回到等待
 -> 心跳超阈值:
      构造心跳请求（运行中工具清单 + 已等待时长 + 沙箱增量输出缓冲尾部）
      → 模型判定继续等待 / 放弃 / 并行独立工作
 -> 工具自然完成:
      走既有 round 边界回填，进入下一轮（默认路径）
```

**占位三条不变量**（区别于入史式占位）：

1. 占位仅在请求构造期存在，不入 session 历史——"有 tool_call 无 tool_result"即 running，是可推导的派生态而非事实；
2. 真实结果到达后按 callId **原位插入**对应 tool_call 之后（非尾部追加），保证任意时刻重建的请求满足 provider 的 tool_result 相邻契约；
3. 崩溃恢复的开放 tool_call 集合由快照消息 × `TOOL_EXECUTION` checkpoint 差集判定（见 `nop-ai-agent-session-and-storage.md` §8.3）。

**超时分层**：机械超时（`toolTimeoutMs`，默认 300s）降级为兜底防线并按工具类型分层配置；卡死/死循环的主判定交由模型基于心跳进度证据（沙箱 capturedRef 增量输出缓冲已在位）判断。工具执行面的参数与证据来源见 `04-tool-invocation.md` §4.4。

**与后台模式的关系**：工具级后台模式（`runInBackground` 参数，模型显式后台化、完成通知入 mailbox）为同期裁定的第二形态，与占位唤醒正交互补、共享任务 registry/进度快照/通知桥构件，见 `04-tool-invocation.md` §4.5。

**实施契约**：等待期唤醒轮语义（计数归属、前置门槛集、guardrail 适用面、唤醒轮响应能力与持久化）、provider 请求构造契约、超时分层参数面、等待实现形态与 runInBackground 完整裁定的实施契约权威是 `nop-ai-agent-async-tool-execution.md`；本节保留目标形态概述。

**拒绝了**：
- 全量事件循环形态（工具完成即唤醒 + 输入记账不变量驱动循环推进）——等待期唤醒场景下模型空闲，请求取消机制无用武之地，round 语义解体的改造面与收益不成比例；
- session 事件化（不可变事件溯源 + 投影去重承载占位/替换）——占位入史后真实结果只能二次追加，在重放流中制造同 callId 双份结果的残留清理问题；快照+journal 范式内三条不变量即可承载，存储结构零新增；
- 纯机械超时路线——对数十分钟真实负载直接不可用，且无法区分慢任务与死锁。

实施编排见 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（WI2–WI5），实施契约见 `nop-ai-agent-async-tool-execution.md`。


**等待期 steering 唤醒（WI5 Phase A，契约权威 `nop-ai-agent-async-tool-execution.md` §三.3）**：heartbeat 等待模式下，round 边界 drain 之上新增等待期 steering 唤醒——steering 到达（200ms 感知）即以占位瞬时请求处理新输入，drain-commit 在 LLM 成功后执行，失败留队列重试；round 边界 drain 保留为兜底消费点（heartbeat 关闭模式下是唯一消费点）。两条消费路径共享单消费者纪律（仅 ReAct 线程 poll）。

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

ReAct 引擎暴露核心 7 个生命周期点（`AgentLifecyclePoint` 全 11 点定义见 `02-execution-model.md` §5.1；此处 7 点 = Layer 1 核心 5 点 + 2 个 TOOL_RESULT_PROCESSED 重入点，为全量 11 点的子集）：

- `before_reasoning`
- `after_reasoning`
- `before_acting`
- `after_acting`
- `on_error`
- `before_tool_result_processed`（允许 ReAct 重入，见 §5.4）
- `after_tool_result_processed`（允许 ReAct 重入，见 §5.4）

Hook 负责增强，引擎负责主流程。

## 9. Actor 状态机与 ReAct 循环的映射

AgentActor 的生命周期状态（计划文档 actor-runtime-vision §3.2，尚未创建）与 ReAct 双循环的对应关系：

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
5. **【MiMoCode 吸收】取消语义**：取消**不是独立 Actor 状态**（`AgentActorStatus` 仅 7 值——`CREATED`/`READY`/`RUNNING`/`IDLE`/`FAILED`/`RECOVERING`/`STOPPED`，无 `cancelling`），而是经 `AgentExecutionContext` 取消标志 + `ICancelToken.interrupt` 实现的过渡行为：
   - `graceful`（`cancelSession(forced=false)`）：设置 ctx 取消标志（volatile），不中断当前正在执行的 tool，等它完成后停止，ReAct 主循环在下一迭代边界（LLM 调用前）检测到标志退出，记录取消原因到 session metadata
   - `forced`（`cancelSession(forced=true)`）：在 graceful 基础上经 `ICancelToken.interrupt` 中断执行线程，阻塞调用（如 `CompletableFuture.allOf(...).join()` 或 LLM 调用）抛 `InterruptedException`，被 ReAct 的 catch 块识别为取消（取消标志为 true）而非失败
   - 取消后会话状态置 `cancelled`（`AgentExecStatus.cancelled`），不进入 `failed`；Actor 终态进入 `stopped`
   - `IAgentEngine.cancelSession(sessionId, reason, forced)` default 抛 `ERR_AGENT_CANCEL_SESSION_NOT_SUPPORTED`（`IAgentEngine.java:49-51`），`DefaultAgentEngine` 已实现（`DefaultAgentEngine.java:565`）

## 10. 本篇结论

ReAct 是当前阶段最合适的单 Agent Java 执行引擎模型。



## 流式管线可行性评估与裁定（WI23，plan 2305）

> **裁定：不立项**（2026-10-03，plan 2305 close）。重引入条件与 successor 出口见文末。

### 评估基线（live @ `4272ecfb3a`，2026-10-03）

dsh 比对报告（D1-4，2026-09 快照）判 nop"无流式路径"——按当前 HEAD 需分层复核：**基础设施层已有流式面**（`IChatService.callStream` Flow.Publisher<ChatStreamChunk>、`ChatServiceImpl` 流式实现+聚合器 :400、5 个 dialect `parseStreamChunk`、流式错误恢复（ChatServiceImpl.parseStreamError :463 调 dialect.parseErrorResponse）、gateway 流式 failover 全链路 FailoverStreamFlow）；**引擎层无流式路径**（ReActAgentExecutor/SingleTurnExecutor/LlmCallCoordinator 零 `callStream` 引用，非流式 `call`/`callAsync` 入口是引擎 LLM 调用唯一路径（LlmCallCoordinator:787,790 调 chatService.call））；REASONING_CHUNK 已于 2026-09-15 审计修复中移除（AgentLifecyclePoint 无该值）。另有流式 usage 解析缺口（OpenAI/Gemini/Ollama 流式不解析 usage；Anthropic 流式手写不走 parseUsage→raw 不捕获）——WI21（plan 2303）registered residual，承接出口即本裁定。

### 改造面（四层）

| 层 | 内容 | 关键交互 |
|----|------|----------|
| 引擎接线 | ReActAgentExecutor/SingleTurnExecutor/LlmCallCoordinator 换流式调用 + 流式治理面 | guardrail（内容级 guardrail 目前按完整响应评估——token 流需要增量或缓冲语义重审）；WI2 唤醒轮/占位渲染语义与 token 流的交互（占位是请求构造期派生态，流式下工具等待期无 token 流产生，交互面小）；steering 打断（abort 中断固化——dsh interrupted:true 形态）；IRetryPolicy/熔断 × 流式重试语义（流中途失败能否重放——本评估遗漏项，只会加大成本、强化不立项） |
| 事件管线 | REASONING_CHUNK 重引入 + AgentEvent payload（WI9 类型化契约的流式扩展） | 2026-09-15 移除的根因是"声明未接线的死点"——重引入的前提是完整消费链（引擎→事件→订阅者），非仅加回枚举值 |
| 持久化 | dsh 整流记录持久化（assistant/attempt 结算一次性落盘，token 级重放保真）vs nop 快照+journal 范式 | nop 现有 TOOL_EXECUTION checkpoint + 消息历史承载结算后事实；token 级重放需要新存储形态（attempt 级 append 流）——与 WI14 增量写路径的交互需评估 |
| dialect | 流式 usage 解析补齐（OpenAI/Gemini/Ollama 流式无 usage；Anthropic 流式手写不走 parseUsage） | WI21 residual；若不立项则该缺口随重启条件封存 |

### 成本/收益

- **收益**：UI 可感知流式（agent 输出逐 token 呈现）；token 级重放保真（审计/调试）；首 token 延迟可观测。
- **成本**：四层改造（引擎接线 + 事件管线重引入 + 持久化新形态 + dialect 补齐）；流式治理语义重审（guardrail 增量评估、steering 打断与中断固化）；**双路径维护**（非流式路径必须保留——治理面/测试面/无流式需求的嵌入方）；无生产需求下全部成本前置。

### 裁定

**不立项**。核心理由：①无已登记生产需求（当前 nop 消费场景——自主 agent 循环、channel 驱动、任务型调用——均无 token 流外露面；gateway 流式服务于直连 chat 而非 agent 循环）；②基础设施层（IChatService/dialect/gateway）已具备流式面，引擎接线的边际成本虽可控但四层治理语义重审（guardrail/事件/持久化）在无消费方验证下是纯负债；③WI2-WI5 异步执行模型裁定（等待优先、唤醒源仅 steering/心跳）与流式正交——流式不解决长任务等待问题。**重启条件**（可判定，满足其一即复评）：①生产流式 UI 消费场景立项（agent 输出需逐 token 呈现给终端用户）；②流式 usage 观测需求落地（WI21 residual 的消费方出现）；③dsh 范式的重放保真在审计需求中出现实证场景（以审计需求立项记录为登记载体，对齐 WI18 先例的可判定精度）。重启时 successor = 新 plan/roadmap（本文件不承接实施）；dialect 流式 usage 补齐可独立先行（不依赖引擎接线，归 successor plan 首个 slice）。

## 注入语义三分（WI8，plan 2289）

**语义**：注入队列条目携带 `InjectionSemantics`——STEER（打断合流：等待期唤醒轮消费，fallback 到 round 边界）/ FOLLOWUP（空闲追加：仅 round 边界消费，等待期不算"空闲"，延迟上界=工具策略硬上限）/ INJECT（背景注入：仅 round 边界静默入史，不进唤醒请求快照、不触发唤醒）。

**消费与持久化**：唤醒 probe（`hasQueuedInjection(STEER)`）只对 STEER 唤醒；唤醒轮快照/commit 只取 STEER；round 边界 drain 消费全部语义。三个提交点（drainCommitSteering 含 guardrail 分支 + round 边界）后立即 `persistInjectedMessages`（replaceMessages+save，worker 线程单写者）；终态残留 drain 在 fromContext 前捕获最后一轮 LLM 期间到达的注入。离线注入（会话非 running）走 `AgentSession.pendingInjections` staging（随 session 持久化），`buildBaseExecutionContext` 合并进新队列并清空。

**事件化**：`INJECTION_QUEUE_UPDATED`（action=enqueued|drained + semantics + pendingCount），三发布点（ctx 入队与排空经 nullable publisher 桥接、actor 入队经 runtime optional publisher——NoOp 默认静默=登记边界）；契约权威与边界（mailbox 内存丢失、pre-drain 残留、FIFO 驱逐）见 `nop-ai-agent-session-and-storage.md` 双层幂等与注入持久化节。
