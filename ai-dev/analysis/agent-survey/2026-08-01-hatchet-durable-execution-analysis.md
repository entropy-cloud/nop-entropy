# Hatchet Durable Execution 引擎深度分析 & Nop AI Agent 检查点/恢复体系

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/hatchet`（hatchet-dev/hatchet，Postgres 上的 Durable Execution 引擎，Go）vs `nop-ai-agent`（reliability 包：DBCheckpointManager + 恢复协议）
> Conclusion:

## 一、总览

**Hatchet** 是一个基于 Postgres 的 Durable Execution 工作流引擎（Go，7.6k⭐）：不依赖 Kafka/Redis/Temporal，单 Postgres 即跑通事件驱动工作流、任务调度、重试、durable sleep 和长时间运行 agent。其核心设计智慧是：**把持久化状态与运行状态分离**（`v1_task` 定义 vs `v1_task_runtime` 运行版本）、**用 idempotency_key 容忍非确定性**、**等待不占资源**（条件注册到引擎）。

| 维度 | Hatchet v1 | Nop AI Agent |
|------|-----------|--------------|
| 状态存储 | Postgres（v1_task_runtime 多版本行 + durable event log 表） | ai_agent_checkpoint 表（单行覆盖） |
| 恢复粒度 | invocation 级（活动结果缓存回放） | 检查点快照（LLM_TURN/TOOL_EXECUTION/COMPACTION） |
| 非确定性处理 | idempotency_key + NonDeterminismError | 无（仅 messageCount 校验） |
| 长时等待 | durable sleep + WaitForEvent + eviction | 无（LLM 循环无长等待原语） |
| 调度 | 乐观调度 + 槽位模型 + 租约 | 会话接管锁 + 租约续期 |
| 部署 | 单 Postgres（分区表 + 触发器） | JDBC 数据库（nop 生态） |

**核心结论先行**：Hatchet 是 nop-ai-agent `DBCheckpointManager` 从"快照备份"向"可恢复执行状态"演进的最佳参考。最值得落地的三点：**多版本行 + 恢复语义**、**事件日志三分类（RUN/WAIT_FOR/MEMO）**、**idempotency_key 非确定性检测**。

## 二、Context（调研背景）

- **为什么需要这个分析**：nop-ai-agent 的 checkpoint 系统 save 侧已实现（ReActAgentExecutor.java:649 / AgentToolDispatcher.java:341 / AgentCompactionCoordinator.java:119），但 restore 侧延迟（AgentSessionLifecycle.java:446 仅作验证）；且 checkpoint 是单行覆盖、无版本化、无等待语义。7 月博客文章《Hatchet Harness：Postgres 上的 Durable Execution 引擎深度解析》详细拆解了其机制。
- **要回答的问题**：Hatchet 的 durable execution 设计如何补强 nop 的检查点/恢复/长时任务？
- **约束**：nop 是 Java 单进程 + JDBC，LLM 调用不可确定性重放（与 Hatchet 的纯函数 worker 不同）。

## 三、核心机制详解

### 3.1 数据模型（sql/schema/v1-core.sql）

- **`v1_task`**：pk `(id, inserted_at)`，含 queue/action_id/step_id/input JSONB/retry_count/is_durable/idempotency_key，按日期 RANGE 分区。
- **`v1_task_runtime`**：pk `(task_id, task_inserted_at, retry_count)`，含 worker_id/timeout_at/evicted_at——**同一任务多版本共存**，天然支持重试与恢复。
- **`v1_durable_event_log_file`**：每 durable task 一行，持 `latest_node_id / latest_branch_id / latest_invocation_count`——写入 entry 前必须 `FOR UPDATE` 锁该行以串行化递增。
- **`v1_durable_event_log_entry`**：pk `(durable_task_id, branch_id, node_id)`，kind 枚举 `RUN / WAIT_FOR / MEMO`，含 `idempotency_key BYTEA`、`is_satisfied / satisfied_at / child_task_external_id / result_payload_external_id`。

### 3.2 事件日志写入路径（非完整事件溯源）

```
Worker 发请求（WaitFor/Memo/TriggerRuns）
  → getAndLockLogFile(FOR UPDATE) → IncrementLogFileInvocationCounts
  → 计算 node_id / branch_id → getOrCreateEventLogEntries（按 idempotency_key 查重）
```

- **MEMO 即缓存**：`Memo(key, fn)` 时先查已满足 entry，命中直接返回缓存 payload 不重执行。
- **非确定性检测**：`createIdempotencyKey` 由「事件类型 + 条件/输入」生成，若 node_id 处已有不同 key 的 entry → `NonDeterminismError` 通知 worker。
- **陈旧调用**：invocation count 不匹配 → `StaleInvocationError` → evict。

### 3.3 条件满足与回调

```
条件满足来源：Sleep 到期 / 用户事件（CEL 表达式求值）/ 子任务完成
  → 产出 SatisfiedEntry → DispatchCallbacks
  → worker 在线：直接投递 EntryCompleted
  → 已 evict：发 DurableRestoreTaskMessage → RestoreEvictedTasks 重新入队
```

### 3.4 Worker 侧回放与 Eviction

- worker 通过 `DurableEvictionManager`（默认 1s 检查）把等待中的 durable run 标记 `evicted_at`，释放槽位。
- 恢复时从 `latest_invocation_count+1` 重新执行，已满足 entry 从日志直接读缓存结果——**事件日志即持久化状态**。

### 3.5 与 Temporal 的关键差异

| 维度 | Hatchet v1 | Temporal |
|---|---|---|
| 状态存储 | Postgres | 历史服务（事件溯源） |
| 重放 | invocation 级（粒度粗，worker 不必纯函数） | 事件级确定性重放（必须纯函数） |
| 调度 | 乐观调度（创建即分配） | 轮询 worker 拉取 |
| 非确定性 | idempotency_key 检测 | 事件顺序校验 |
| 部署 | 单 PG 极简 | 多服务较重 |

**核心洞察**：Hatchet 把「执行历史」仅作为 durable 等待/缓存的辅助，非确定性由 idempotency_key 检测 + eviction 从 invocation 边界恢复——这对 LLM agent（不可重放）是更务实的模型。

## 四、优缺点

### 优点

1. 单 Postgres 全链路（无 Kafka/Redis 依赖），部署运维极简。
2. 乐观调度降低端到端延迟（事件→执行省一次消息往返）。
3. 所有状态可 SQL 查询、可事务保证（match+satisfy+create 同一事务）。
4. durable 能力按需启用（is_durable），普通任务零额外开销。
5. 分区表 + 触发器 + LISTEN/NOTIFY 等 PG 原语深度利用。

### 缺点

1. 事件日志非完整事件溯源（只有 RUN/WAIT_FOR/MEMO 三类），不能精确逐事件重放。
2. 全局节点计数靠行锁（FOR UPDATE log file）→ 高并发下热点行。
3. 双写（core + OLAP）+ 消息队列补偿逻辑复杂。
4. 租约机制（30s/5s 心跳）在实例抖动时可能短暂双主。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

nop 现状：`DBCheckpointManager`（ai_agent_checkpoint 表，混合列布局）、`Checkpoint{seq/watermark/type/toolName/callId/messageCount/tokenEstimate}`。

### 5.1 可直接借鉴的设计

**① 多版本行 + 恢复语义**（对应 `v1_task_runtime` 复合主键）
nop 现在 checkpoint 是单行覆盖；建议引入 `(sessionId, seq)` 版本化写入，让 restore 可以回退到任意水位，而不是只取最新。

**② 事件日志作为「等待/缓存」而非完整重放**（对应 durable event log）
- `RUN`（工具执行意图 + child_task_external_id）→ 对应 nop 的 TOOL_EXECUTION checkpoint。
- `WAIT_FOR`（等待条件：用户输入/睡眠/事件）→ **nop 目前缺失**，是 LLM 循环中长等待的关键。
- `MEMO`（结果缓存）→ 对应 checkpoint 的 result 缓存，重跑时命中即跳过（`AlreadyExisted` 语义）。

**③ 非确定性检测 = idempotency_key**
nop 每次保存 checkpoint 时计算 hash（工具名+参数+上下文指纹），restore 时若同水位 key 不一致 → 拒绝该 checkpoint，降级为 session 重放。这比现在只比较 messageCount 强得多。

**④ 乐观提交 + 事务内一致**（对应 `UpdateTasksToAssigned` 的 `ON CONFLICT DO UPDATE WHERE evicted_at IS NOT NULL`）
checkpoint 保存改为 `INSERT ... ON CONFLICT (session_id, seq) DO UPDATE` 的幂等 upsert，天然支持并发重试。

**⑤ 队列+退避重试**（对应 `v1_retry_queue_item`）
`StandardRetryPolicy` 借鉴「指数退避 + 上限 + retry_after 时间戳驱动的重试队列」。

**⑥ 心跳/租约让位**（对应 eviction）
多实例场景下 `DBCheckpointManager` 加租约字段（lease_expires_at），类似 `AcquireOrExtendLeases` 原子抢占 session 所有权。

### 5.2 不建议照搬的

- **行锁串行化 node_id**（FOR UPDATE log file）：nop 单会话写入频率低，用 `(session_id, seq)` 唯一约束 + 冲突重试即可。
- **分支回放（branch_point）**：LLM agent 不适合确定性分支回放，保持「checkpoint 快照 + journal 追加」双轨。
- **分区分表**：nop 单机场景不需要 PG 分区。

### 5.3 具体落地建议

```
ai_agent_checkpoint 扩展（对齐 Hatchet v1_durable_event_log_entry）：
  - 增加 idempotency_key 列（hash(toolName + callId + 输入指纹)）
  - 增加 parent_call_id（对应 child_task_external_id，支持嵌套工具/子会话）
  - 增加 is_failure / error_message 列（对应 child_task_is_failure）
  - seq 作为 (session_id, seq) 复合主键的一部分，检查点按 seq 追加而非覆盖
  - 增加 wait_for 条件 JSONB（用户输入/超时/事件，对应 WAIT_FOR entry）
  - 写入用幂等 upsert（ON CONFLICT (session_id, seq) DO NOTHING/UPDATE）
```

## 六、结论

- **Hatchet 最大设计智慧**：持久化状态与运行状态分离、idempotency_key 容忍非确定性、等待不占资源。
- nop 的 checkpoint 系统正处在从「快照备份」向「可恢复执行状态」演进的关键节点；先落地「多版本行 + idempotency_key + WAIT_FOR 语义」三点。
- 后续工作：指向 `ai-dev/design/nop-ai-agent-reliability.md` 的检查点扩展设计。

## Open Questions

- [ ] nop 的 restore 语义是否支持回退到任意水位（多版本行），还是只恢复最新？
- [ ] WAIT_FOR 条件（用户输入/事件）在 nop 的 ReAct 循环中如何注册与唤醒？
- [ ] idempotency_key 的 hash 内容与冲突策略（拒绝 vs 跳过）？

## References

- `~/ai/hatchet/sql/schema/v1-core.sql`、`pkg/repository/durable_events.go`、`pkg/scheduling/v1/`、`pkg/worker/context.go`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`
- `ai-dev/analysis/2026-06-18-fault-tolerance-deep-dive.md`
- `ai-dev/analysis/agent-survey/2026-06-08-agent-storage-and-analytics-survey.md`
