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

- `FailFastStrategy`（默认）— 检测到冲突直接拒绝。Phase 1 使用此实现
- `CoordinationBusStrategy`（扩展）— 通过协调信道广播 scope_claim/operation_intent，实现 LLM 智能协调 + 引擎级预警。通过 XDSL 配置切换

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

父子 Agent 通过 `call-agent` 的输入/输出传递消息。这是同步的请求-响应模式。

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
| Phase 1 | `call-agent` 工具 + 引擎级 fail-fast |
| Phase 2 | 协调信道（scope_claim/operation_intent）+ LLM 智能协调 |
| Phase 3 | Nop Flow 图编排 + Agent 节点 + 协调信道集成 |
| Phase 4 | 自适应编排（协调器 Agent + 协调信道） |

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
