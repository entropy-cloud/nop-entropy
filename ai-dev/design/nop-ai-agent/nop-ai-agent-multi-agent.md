# Nop AI Agent 多 Agent 并行协同设计

## 1. 目标

本篇定义多个 Agent 并行执行时的协同机制——如何检测冲突、如何协调资源竞争、以及引擎层提供的协同原语。

核心定位：nop-ai-agent 面向大规模无人值守自动化，多 Agent 并行是常见场景。引擎必须提供冲突检测能力，但自动冲突解决应推迟到后期阶段。

## 2. 设计定位

本篇属于策略层设计，解决以下问题：

1. 多个 Agent 并行执行时可能产生哪些冲突
2. 引擎层提供哪些冲突检测和协调机制
3. 协同策略的可扩展接口

本篇不定义具体的锁实现或调度算法——这些属于源码范畴。

## 3. 冲突分类

### 3.1 文件写冲突

**场景**：Agent A 和 Agent B 同时修改同一文件。

**检测方式**：工具执行前检查文件写意图。

**期望行为**：

1. 引擎维护一个"写意图注册表"（Phase 1 的简化机制）
2. 工具执行写操作前，先注册写意图
3. 如果检测到冲突（同一文件已有其他 Agent 的写意图），委托给 `IConflictStrategy` 处理（见 §4.4）
4. 默认策略 `FailFastStrategy`：报错中止
5. 扩展策略 `CoordinationBusStrategy`：通过协调信道广播 scope_claim/operation_intent，实现 LLM 智能协调 + 引擎级预警（见 §4）

### 3.2 共享资源竞争

**场景**：Agent A 和 Agent B 同时执行 `pnpm test`，争抢 CPU/内存/端口。

**检测方式**：工具执行时的资源声明。

**期望行为**：

1. 工具可以通过上下文声明资源需求（如"需要独占端口 3000"）
2. 引擎检查当前资源使用情况
3. 默认策略：不自动协调，依赖外部调度
4. 扩展策略：资源调度队列（通过 `IConflictStrategy` 扩展或独立 `IResourceScheduler` 接口）

### 3.3 上下文依赖冲突

**场景**：Agent A 的 Plan 依赖 Agent B 的输出，但 Agent B 尚未完成。

**检测方式**：Plan 中的任务依赖声明。

**期望行为**：这属于 Plan 引擎的调度范畴，不是 Agent 引擎的核心职责。Agent 引擎只负责单个 Agent 的执行循环。

## 4. 协调信道（Coordination Bus）

### 4.1 设计思路

多 Agent 并发协调的核心机制是**公共协调信道**：Agent 在执行操作前，通过信道广播自己的意图，其他 Agent 通过消息流感知这些意图并自行调整。

这不是被动的冲突检测（操作时才发现冲突），而是**主动意图广播**——Agent 看到其他人的计划后，LLM 可以主动避让或调整工作顺序。

### 4.2 消息类型

| 消息类型 | 触发时机 | 内容 | 作用 |
|---------|---------|------|------|
| `scope_claim` | Agent 启动或接受新任务时 | sessionId, agentName, scopeDescription, resourcePatterns | 广播工作范围（"我打算改 src/core/ 下的文件"） |
| `operation_intent` | 工具执行前 | sessionId, agentName, operation, resources, estimatedDuration | 广播具体操作意图（"我要编辑 Foo.java"） |
| `operation_done` | 工具执行后 | sessionId, agentName, operation, resources, result | 通告操作完成 |
| `scope_release` | Agent 完成任务或释放范围 | sessionId, agentName | 释放工作范围 |
| `conflict_alert` | 引擎检测到潜在冲突时 | conflictType, conflictingAgents, resources | 引擎级冲突预警（兜底机制） |

### 4.3 注入机制

协调消息通过以下方式进入 Agent 的上下文流：

```
每轮 ReAct 迭代前:
  engine.inject_coordination_messages(agent.context, since=lastIteration)
  → 作为 coordination 类型消息注入
  → Agent 的 LLM 在推理时能看到其他 Agent 的意图和操作
```

- 协调消息不参与 compaction（标记为 `pinned`）
- 注入量有上限（最近 N 条或最近 T 时间窗口内），避免上下文膨胀
- Agent 的 system prompt 包含协调指令："注意 coordination 消息，主动避让其他 Agent 的工作范围"

### 4.4 分层策略

| 层次 | 机制 | 说明 |
|------|------|------|
| **L1: LLM 智能协调** | 协调信道 + 注入 | Agent 看到 scope_claim/operation_intent 后主动调整 |
| **L2: 引擎级预警** | conflict_alert | 引擎检测到 scope 交叉时主动广播预警 |
| **L3: 引擎级 fail-fast** | 操作前检查 | scope_claim 的资源模式冲突时直接拒绝（安全兜底） |

**接口抽象**：三层策略通过 `IConflictStrategy` 接口统一：

```
IConflictStrategy:
  ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing)
```

- `FailFastStrategy`（默认）— 检测到冲突直接拒绝。Phase 1 使用此实现。**已落地**（plan 214 / L2-13a）：`io.nop.ai.agent.conflict.FailFastStrategy` + `InMemoryWriteIntentRegistry` 已接线到 `DefaultAgentEngine` / `ReActAgentExecutor` dispatch path（Layer 3 approval gate 之后、`allowedCalls.add` 之前的冲突检测步骤）
- `CoordinationBusStrategy`（扩展）— 通过协调信道广播 scope_claim/operation_intent，实现 LLM 智能协调 + 引擎级预警。通过 XDSL 配置切换。**Successor**（依赖 `IMessageService` topic 基础设施 + L4-8 Actor Runtime）

**渐进式增强路径**：引擎通过 `IConflictStrategy` 接口调用，不直接包含 if-branching。Phase 1 注册 `FailFastStrategy`；Phase 2 替换为 `CoordinationBusStrategy`。引擎代码不变。

### 4.5 与 IMessageService 的关系

协调信道的底层传输使用 Nop 的 IMessageService：

```
topic: "agent.coordination.{projectId}"
  ├── scope_claim events
  ├── operation_intent events
  ├── operation_done events
  ├── scope_release events
  └── conflict_alert events
```

- 所有 Agent 实例订阅同一个 project topic
- 消息持久化到 Event Log（可审计、可回溯）
- Phase 1 可用进程内消息队列，Phase 2+ 可扩展为分布式

### 4.6 资源模式匹配

`scope_claim` 和 `operation_intent` 的 `resources` 字段支持模式匹配：

```json
{
  "type": "scope_claim",
  "sessionId": "sess-001",
  "agentName": "agent-refactor",
  "scopeDescription": "重构 core 模块的错误处理",
  "resourcePatterns": [
    "src/main/java/io/nop/core/**/*.java",
    "src/test/java/io/nop/core/**/*.java"
  ]
}
```

引擎通过模式匹配检测 scope 交叉，决定是否触发 `conflict_alert`。

## 5. Agent 间的通信

### 5.1 父子通信

> **本节是 AI/读者消歧节**。"call-agent 是同步 fork+exec" 的早期理解是**不完整**——`CallAgentExecutor`（`io.nop.ai.agent.tool`）内有**两条独立路径并存**，由 `IAgentMessenger` 是否功能化决定走哪一条。本节明确双路径的语义、触发条件、跨进程能力。

父子 Agent 通过 `call-agent` 工具传递消息。`CallAgentExecutor`（`io.nop.ai.agent.tool`，plan 224）保留全部 session-mode 解析（continue/fork/create-new），parent permission constraint 传播，delegation depth 自动递增（MAX_DELEGATION_DEPTH 默认4）。两条路径**共享同一份**不可变载荷契约（`CallAgentRequestPayload` / `CallAgentResponsePayload`）——可观察结果（sub-session ID +最终消息 + 错误状态）一致。

#### 5.1.1 call-agent 双路径对比

| 维度 | **sync fork+exec**（默认） | **async mailbox**（plan 224） |
|---|---|---|
| 触发条件 | shipped 默认（`NoOpAgentMessenger`） | 功能性 `IAgentMessenger` 接线 |
| 物理位置 | 同进程 `IAgentEngine.execute().orTimeout()` | 经 `IMessageService.request(envelope, timeout)`） |
| 跨进程能力 | ❌ 仅同进程 | ✅ 可经 `DBMessageService`（plan 224）跨 JVM |
| 父→子载荷 | `CallAgentRequestPayload` 不可变对象 | 同（REQUEST 信封 payload） |
| 子→父响应 | 子 agent execute() 返回值 | `CallAgentResponsePayload`（RESPONSE 信封 payload） |
| 超时机制 | `engine.execute().orTimeout(timeoutMs)` | `IMessageService.request(envelope, timeoutMs)`（在 `LocalAgentMessenger` 内 `.orTimeout`）） |
| 派发代码位置 | `CallAgentExecutor.executeSubAgent()` | `CallAgentExecutor.executeViaMessenger()` |
| 分支点 | `CallAgentExecutor.dispatch()` 第 280 行（`messenger instanceof NoOpAgentMessenger` 判）） | 同 |

**关键判定**（`CallAgentExecutor.java:280-285`）：

```java
IAgentMessenger messenger = agentCtx.getMessenger();
if (messenger != null && !(messenger instanceof NoOpAgentMessenger)) {
    return executeViaMessenger(...);  // async mailbox
}
return executeSubAgent(...);          // sync fork+exec
```

#### 5.1.2 sync fork+exec 详解（默认行为）

子 Agent 在**父 Agent 同一进程内同步运行**。Session 模式由 `call-agent` 工具参数决定：
- 传入 `sessionId` → `continue` 模式（续接已有 session）
- 传入 `sessionId` + `inheritContext=true` + `agentId="self"` → `fork` 模式（先 `engine.forkSession()` 获得 childSessionId 再执行）
- 都不传 → `create-new` 模式（全新 session）

#### 5.1.3 async mailbox 详解（plan 224）

当 `IAgentMessenger` 功能化时（默认 `NoOpAgentMessenger`），`CallAgentExecutor` 走 mailbox 路径：

1. `executeViaMessenger()` 构造 `CallAgentRequestPayload` 不可变对象（targetAgentId / input / resolvedSessionId / childMetadata / timeoutMs）
2. 包装 `AgentMessageEnvelope`（senderId / targetTopic=`agent.call-agent` / correlationId / AgentMessageKind.REQUEST / payload）
3. `messenger.request(envelope, timeoutMs)` 投递到 `agent.call-agent` topic
4. 引擎在 `setMessenger` 时注册 call-agent handler（`engine.execute().orTimeout().join()` 执行子 agent）
5. handler 返回 `CallAgentResponsePayload`，future 正常 resolve；超时或失败时 future 异常 → 工具结果错误状态

**关键洞察**：async 路径的子 agent 仍然是同引擎执行——`call-agent` **不是**"委派到外部进程"。它只是把"父 → 子 invoke"的控制流异步化、可跨进程投递（如果 messenger backend 是 `DBMessageService`）。**真正跨进程 + 外部进程 agent 委派不在 call-agent 的设计意图内**。

#### 5.1.4 何时选哪条路径

| 场景 | 推荐路径 | 理由 |
|---|---|---|
| shipped 默认（无 messenger 配置） | sync fork+exec | 零回归、延迟低 |
| 单 JVM 多 agent | async mailbox（接线 `LocalAgentMessenger`） | 与 `send-message` 一致的 inbox 语义 |
| 跨 JVM 多 agent（如分布式部署） | async mailbox（接线 `DBMessageService`） | 跨进程可达 + 至少一次语义 |
| 严格子 agent 同步结果需要 | sync fork+exec | 不依赖 messenger 异常处理 |
| 需要 audit 链路 | async mailbox | `correlationId` 在 mailbox 信封中天然携带 |

**默认行为不变化**：shipped 默认（`NoOpAgentMessenger`）下零回归，与 plan 224 之前的 fork+exec 行为逐行一致。

### 5.2 兄弟通信

**决策**：Phase 1 通过父 Agent 中转；Phase 2 通过协调信道（§4）实现间接感知。

**Phase 1**：兄弟 Agent 不直接通信，通过父 Agent 中转。

**Phase 2**：兄弟 Agent 通过协调信道的 scope_claim / operation_intent 间接感知彼此的工作范围。这不是直接消息通信，而是通过公共协调消息流实现协作可见性。

### 5.3 全局协调器

**决策**：Phase 1 不引入全局协调器 Agent。

**理由**：全局协调器本身是一个 Agent，引入它需要先解决"谁协调协调器"的问题。Phase 1 用简单的注册表和 fail-fast 策略即可。

**拒绝了**：Phase 1 引入全局协调器 Agent。理由是自举问题需要先解决，简单 fail-fast 在无人值守场景下更可预测。

**Phase 2+ 考虑**：如果需要更复杂的协调，可以引入一个专门的"协调 Agent"角色，通过 Nop Flow 编排。

## 6. 与 Nop Flow 的关系

多 Agent 编排的自然演进方向是与 Nop Flow 集成：

| 阶段 | 编排方式 |
|------|---------|
| Phase 1 ✅ | `call-agent` 工具（fork+exec via `IAgentEngine.execute()`）+ `send-message` 工具（fire-and-forget via `IAgentMessenger.send()`）+ 引擎级 fail-fast |
| Phase 2 🟡 | call-agent 异步 mailbox 模型 foundational 已落地（plan 224：经 `IAgentMessenger.request()` 投递 REQUEST 到引擎级 topic + 引擎级 handler）+ 协调信道（scope_claim/operation_intent）+ LLM 智能协调（协调信道仍为 successor）|
| Phase 3 | Nop Flow 图编排 + Agent 节点 + 协调信道集成 |
| Phase 4 | 自适应编排（协调器 Agent + 协调信道） |

> Phase 1 已交付：`call-agent` 采用 fork+exec 模型（直接调用 `IAgentEngine.execute()` 同步等待子 Agent 完成），`send-message` 采用 fire-and-forget 模型（通过 `IAgentMessenger.send()` 投递到目标 inbox topic）。基于 mailbox 的 call-agent 模型（发 REQUEST 到目标 topic、等待 handler 响应）的 **foundational 已由 plan 224 落地**：`CallAgentExecutor` 在功能性 `IAgentMessenger` 可用时经 `IAgentMessenger.request()` 投递 REQUEST 到引擎级 `agent.call-agent` topic，引擎在 `setMessenger` 时 idempotent 注册 call-agent handler 执行子 Agent 并返回 RESPONSE（`CallAgentRequestPayload`/`CallAgentResponsePayload` 不可变载荷契约）；shipped 默认（`NoOpAgentMessenger`）保留 fork+exec 零回归。per-session inbox 路由（发 REQUEST 到 Callee inbox topic 而非引擎级 topic）+ 异步非阻塞 handler + 跨进程路由仍为 Actor Runtime successor。

参考 solon-ai 的做法：Agent 作为 Solon Flow 的 NamedTaskComponent。Nop 可以将 Agent 作为 Nop Flow 的节点类型，通过 Flow 图定义多 Agent 编排逻辑。

## 7. 虚拟 Shell 的协同考量

nop-ai-shell 提供虚拟 shell 执行，每个命令行解析为单个指令。在多 Agent 并行场景下：

**决策**：每个 Agent 的 shell 执行在独立的工作目录中。

**理由**：

- 避免文件系统层面的冲突
- 类似操作系统的进程工作目录隔离
- Agent 的文件操作都相对于自己的工作目录

**边界条件**：

- 如果 Agent 需要访问共享目录（如项目根目录），通过环境信息显式声明
- Shell 工具的上下文注入当前 Agent 的工作目录

## 8. 子 Agent Compaction 隔离

### 8.1 问题

父 Agent 执行 compaction 时，可能影响正在运行的子 Agent：

- 父 Agent 的 CompactionEntry 会标记 `firstKeptEntryId`，丢弃父 Agent 认为可压缩的消息
- 但子 Agent 的 `call-agent` 调用结果可能被父 Agent 误判为"可压缩的中间工具输出"
- 子 Agent 自身也可能触发 compaction，两者的压缩窗口可能交叉

### 8.2 设计决策

**规则**：父 Agent compaction 不得影响正在运行的子 Agent session。

具体机制：

1. **独立 Event Log**：每个子 Agent 拥有独立的 session 和 `events.jsonl`，父 Agent 的 compaction 只操作自己的 event log
2. **状态键过滤**（`excluded_state_keys`）：父 Agent compaction 时，`call-agent` 返回的子 Agent 结果被标记为 `pinned`，不会被 Layer 1/2 裁剪
3. **引用完整性**：父 Agent summary 引用子 Agent 结果时，使用 `subAgentSessionId` 引用而非内联复制，确保子 Agent 数据不依赖父 Agent 的压缩周期
4. **生命周期保护**：子 Agent 运行期间，父 Agent 的 compaction 对 `call-agent` 相关 entry 只执行 Layer 0（截断过大输出），不执行 Layer 2+（裁剪/摘要）

### 8.3 Phase 分配

- Phase 1：规则 1（独立 session）天然满足
- Phase 2：规则 2-3（pinned 标记 + 引用完整性）
- Phase 3：规则 4（生命周期保护，需 Actor Runtime 的子 Agent 状态感知）

## 9. 演进方向

本篇定义的 Phase 1 策略（引擎级 fail-fast + 父 Agent 中转）是**最小可行方案**。

Phase 2+ 的具体架构设计见 **`nop-ai-agent-actor-runtime-vision.md`**，主要演进点：

| 维度 | 本篇 (Phase 1) | Actor Runtime (Phase 2+) |
|------|---------------|--------------------------|
| 进程模型 | 单 Agent 调用 | Virtual Thread Actor 并行 |
| 通信 | call-agent 同步 | IMessageService 协调信道 |
| 协调 | 引擎级 fail-fast | LLM 智能协调 + 引擎级预警 + fail-fast |
| 状态 | 内存 | DB 持久化 + 事务保护 |
| 团队 | 无 | TeamManager + TeamSpec DSL |
| 恢复 | 无 | RecoveryManager 自动恢复 |
| 隔离 | 无 | 多租户 + 用户级配额 |
| 子 Agent compaction | 独立 session（天然隔离） | pinned 标记 + 生命周期保护 |

## 10. 与现有文档的关系

| 本篇内容 | 相关文档 | 关系 |
|---------|---------|------|
| call-agent 并行 | call-agent-dsl.md | 本篇定义并行语义，call-agent-dsl.md 定义 DSL |
| 协调信道（§4） | actor-runtime-vision.md ResourceGuard | §4 定义协调协议，ResourceGuard 是 Phase 2+ 实现载体 |
| Phase 1 fail-fast | tool-dsl.md | 工具执行前的注册行为（简化版协调） |
| Flow 编排 | nop-ai-agent-roadmap.md | 本篇定义演进路径 |
| 上下文隔离 | nop-ai-agent-context-model.md | 并行时的上下文独立性保证 + 协调消息注入 |
| Phase 2+ 架构 | nop-ai-agent-actor-runtime-vision.md | 本篇 Phase 2+ 的具体实现方案 |
