# nop-job 任务优先级设计

**日期**：2026-06-18
**范围**：nop-job 的任务调度优先级（WAITING task 的拉取排序）
**状态**：active（§3.2.1 索引/filesort 评估 AR-92 已收口）
**关联**：`01-architecture-baseline.md`（核心流程）、`worker-assignment-design.md`（正交维度）、`block-strategy-design.md`（正交维度）、`ai-dev/plans/269-nop-job-dispatch-config-index-quality.md`（AR-92）、`ai-dev/plans/267-nop-job-resource-limit-worker-correctness.md`（AR-95 priority null→0 归一）

---

## 一、设计结论

1. **任务优先级只做"排队优先级"一种语义**：worker 拉 WAITING task 时按优先级排序拉取。**不做抢占**（已 RUNNING 的 task 不能被踢）、**不做配额保留**（`maxConcurrency` 不为高优先级预留 slot）。

2. **优先级定义在 `NopJobSchedule`**（int，默认 0，数值越大优先级越高）。dispatch 时快照到 `NopJobTask.priority`，便于 SQL 排序不 join schedule 表。**不在 fire 层加**，因为同一 schedule 的 fire 优先级应一致。

3. **影响范围极小**：仅修改 `JobTaskStoreImpl.fetchWaitingTasks` 的排序，从 `createTime ASC, jobTaskId ASC` 改为 `priority DESC, createTime ASC, jobTaskId ASC`。**不影响**：worker 抢占（CAS 仍先到先得）、worker 选择（与 `worker-assignment-design.md` 正交）、block 策略、retry。

4. **保持向后兼容**：`priority` 默认 0，老数据全部为 0，排序等价于现有 FIFO 行为。

5. **数值约定**：整数，**值越大优先级越高**。允许负值（低于默认）。**不预定义"优先级档位"**（如 P0/P1/P2），由业务自行约定语义。

---

## 二、背景与动机

### 2.1 当前缺口

`JobTaskStoreImpl.fetchWaitingTasks` 当前排序：`createTime ASC, jobTaskId ASC`（`JobTaskStoreImpl.java:49-50`），纯 FIFO。

所有 WAITING task 在 DB 队列里完全平等，无法表达"这个 job 比那个 job 紧急"。worker 抢占时（`tryLockTasksForExecute`）按 fetch 顺序 CAS，所以 FIFO 排序直接决定了"谁先被抢"——加优先级只需改 fetch 顺序，无需动抢占逻辑。

### 2.2 为什么优先级定义在 schedule 层

- **fire 层不合适**：fire 是 schedule 在某个时间点的触发快照，同一 schedule 的多次 fire 语义上没有"这次触发比上次紧急"的需求。如果真有，应该是 schedule 改了优先级后再触发新 fire。
- **task 层独立配置不合适**：分片模式下（`worker-assignment-design.md` 模式 A）同一 fire 的 N 个 task 是 batch 创建的，它们之间设不同优先级没有意义。priority 应该是 schedule 级别的属性。
- **task 层需要 priority 列**：但仅作为 schedule 的快照，目的是让 `fetchWaitingTasks` 排序时不必 join schedule 表。

---

## 三、核心设计

### 3.1 数据模型

**`NopJobSchedule` 新增字段**：

| 字段 | 类型 | 默认 | 用途 |
|------|------|------|------|
| `priority` | int | 0 | 数值越大优先级越高，允许负值 |

**`NopJobTask` 新增字段**（dispatch 时从 schedule 快照）：

| 字段 | 类型 | 默认 | 用途 |
|------|------|------|------|
| `priority` | int | 0 | dispatch 时从 schedule 快照，用于 `fetchWaitingTasks` 排序 |

ORM 改动属于 `plan-first` 区域，按 AGENTS.md 流程处理。

### 3.2 fetchWaitingTasks 排序契约

`JobTaskStoreImpl.fetchWaitingTasks` 的排序契约：

```
ORDER BY priority DESC, createTime ASC, jobTaskId ASC
```

- `priority DESC`：高优先级先被 worker 看到
- `createTime ASC`：同优先级时，先触发的先被拉取（保留 FIFO 公平性）
- `jobTaskId ASC`：同 createTime 的最后 tiebreaker，保证排序稳定

### 3.2.1 索引与 filesort 评估（AR-92）

`fetchWaitingTasks` 的实际 SQL 形态（`JobTaskStoreImpl.fetchWaitingTasks:56-77`）：

```
WHERE taskStatus = 0  [WAITING]
  AND partitionIndex BETWEEN ? AND ?        -- 仅当 partitions 非空（addPartitionFilter BETWEEN 范围谓词）
ORDER BY priority DESC, createTime ASC, jobTaskId ASC
LIMIT ?
```

现有索引（`nop-job.orm.xml` NopJobTask.indexes）：

- `IX_NOP_JOB_TASK_RUN_SCAN(taskStatus, partitionIndex, createTime)`

**结论：filesort 不可消除（watch-only residual）。** 理由（非"合理等效"，是索引结构事实）：

1. `taskStatus` 是等值谓词（`= WAITING`），可用作索引前导列。
2. `partitionIndex` 是 **BETWEEN 范围谓词**（`addPartitionFilter` 产 `partitionIndex BETWEEN offset AND last`）。复合索引中**范围列之后的所有列不能用于满足 ORDER BY**（MySQL/PostgreSQL/Oracle 的一致行为：range scan 打破了后续列的有序性）。
3. 因此把 `priority` 加入 `IX_NOP_JOB_TASK_RUN_SCAN`（无论置于 `partitionIndex` 之前或之后）都无法让 `priority DESC` 走索引排序：
   - 置于 `partitionIndex` 之后 → 被范围列阻断（理由 2）。
   - 置于 `partitionIndex` 之前 → 索引无法再用 `partitionIndex` 做范围定位，且 `taskStatus` 之后再是 `priority` 时，`partitionIndex` BETWEEN 仍需回表/filesort。
4. 无分区过滤（单节点全分区）时，`IX(taskStatus, priority DESC, createTime)` 理论上可消 filesort，但这会引入一条额外的写时维护索引，且与有分区过滤路径行为不一致（两套路径）。代价/收益不划算——`fetchWaitingTasks` 带 `LIMIT batchSize`，filesort 是有界排序（bounded），非全表排序。

故**不新增 priority 复合索引**，归类为 `watch-only residual`：filesort 为有界代价（batchSize 限制），不影响正确性；若未来 batchSize 显著增大或高并发 worker 抢占成为瓶颈，再评估专用索引。

**跨库 NULL 排序一致性：由 Plan 267 AR-95 的 null→0 归一兜底，不依赖索引。** `priority` 列 `defaultValue=0`（`nop-job.orm.xml:423`），且 dispatcher 在落库前对 schedule 可空 `priority` 做 null→0 归一（`JobDispatcherScannerImpl.normalizeCost`），故 `NopJobTask.priority` **永不为 NULL**。因此 MySQL（NULLs first）/PostgreSQL（NULLs last）/Oracle（NULLs last）的 NULL 排序差异在本列上不会触发——排序输入恒为非空 int，跨库行为一致。

### 3.3 dispatch 时的快照

所有 task builder（`DefaultJobTaskBuilder` / `PartitionTaskBuilder` / `RpcBroadcastTaskBuilder` / 未来的 `AdaptiveJobTaskBuilder`）在 `buildTasks` 时：

```
task.setPriority(schedule.getPriority());
```

与现有的 `task.setPartitionIndex(fire.getPartitionIndex())` 同样的快照模式。一旦 task 创建，其 priority 固化，后续 schedule.priority 修改不影响已存在的 task。

### 3.4 不影响的部分（明确边界）

| 模块 | 是否受影响 | 说明 |
|------|----------|------|
| `tryLockTasksForExecute` | ❌ | CAS 仍按 fetch 顺序竞争。优先级高的先被 fetch 就够了——抢占模型下"先看到=先抢到" |
| `IJobTaskBuilder` 各实现 | ❌（核心） | task builder 只管"生成几个 task"。仅在 `buildTasks` 末尾加一行 `setPriority` 快照 |
| `IWorkerAssignmentStrategy`（best-fit 模式） | ❌（默认） | 优先级与 worker 选择正交。可选增强：`loadScore` 平手时拿 `priority` 做 tiebreaker，但不强制 |
| `maxConcurrency` | ❌ | 不为高优先级预留配额（那是"scarcity priority"语义，见 §四拒绝项 #2） |
| `JobTimeoutChecker` / `JobCompletionProcessor` | ❌ | 与超时检测、完成处理无关 |
| block 策略（`block-strategy-design.md`） | ❌ | 与"同 schedule 已有 fire 在跑时怎么办"无关 |
| retry 桥接（`retry-integration-design.md`） | ❌ | retry 重试时沿用原 task 的 priority 快照，不重新读 schedule |

---

## 四、拒绝了什么

1. **抢占式优先级（preemption）**：已 RUNNING 的 task 无法安全回滚（业务侧可能已经写了部分数据），强行中断会破坏一致性。当前模型只支持"等超时"或"业务方主动支持 cancel"。抢占留给将来的"可回滚任务"场景，不在 MVP。

2. **配额保留式优先级（scarcity priority）**：`maxConcurrency` 满时为高优先级 task 预留 slot，需要 worker 知道"未来可能有高优先级 task 来"，引入预测和配置复杂度。当前所有 task 在 worker 看来都是平等的 WAITING，"预留"违背模型简洁性。如果业务真需要，可以**把高优先级 task 放到独立的 worker pool**（`serviceName` 不同），物理隔离，比软预留更可靠。

3. **优先级档位（P0/P1/P2）**：预定义档位是 false friend——业务对"紧急"的定义千差万别（财务月底结账 vs 用户触发的事件），框架不应假设通用档位。int 字段 + 业务自定义语义更灵活。

4. **fire 级别 priority**：同一 schedule 的 fire 应共享优先级。如果业务需要"这次触发特别紧急"，应该新建一个独立的高优先级 schedule，而不是给单次 fire 打标。

5. **task 级别独立配置 priority**：分片模式下同一 fire 的 N 个 task 是 batch 创建的，差异化 priority 无意义。task 的 priority 字段是 schedule 的快照，不是独立配置点。

6. **把 priority 塞进 `worker-assignment-design.md`**：优先级管"先后"，worker 分配管"给谁"，两个正交维度混在一起会让两份文档都失去焦点。优先级自身决策面小（一个字段 + 一处排序），独立文档反而能讲清"不做什么"（non-goals 多于 goals）。

7. **动态优先级老化（aging）**：基于 task 等待时长自动提升 priority（防止低优先级 task 饿死）。这是真实需求但引入"何时提升、提升多少"的参数空间，MVP 不做。低优先级 task 饿死的极端场景应通过业务侧"为长跑任务设独立 schedule"解决。

---

## 五、与已有设计的关系

- **`01-architecture-baseline.md`**：核心流程的"dispatch → worker fetch → execute"链路中，**fetch 步骤的排序规则**改变。需同步到架构基线层数据模型和核心流程章节。
- **`worker-assignment-design.md`**：**正交但混合负载场景下协同收益最大**。priority 管"先后"（ad-hoc 先出队），worker-assignment 管"给谁/装多少"（ad-hoc 落到合适的 worker、worker 按资源限制拉取）。两者可独立部署、各自产生价值——priority 单独部署已能改善 ad-hoc 延迟，worker 资源限制单独部署已能改善异构机器利用率；同时部署时收益叠加。**一处交互需注意**：worker-assignment §3.4.2 的客户端 cost 过滤与 priority 排序共存在同一 fetch 流程，客户端过滤会先于 priority 生效——高优先级大 task 在 worker 资源不足时仍会被跳过，此时应通过模式 B（`dispatchMode=bestFit`）显式预放置，而不是指望 priority 排队解决。
- **`block-strategy-design.md`**：正交关系。block 策略处理"同 schedule 已有 fire 在跑"（同一 schedule 内部），priority 处理"不同 schedule 之间谁先"（schedule 之间）。
- **`rate-limiting-design.md`**：正交关系。限流是 worker 侧的并发控制，priority 是 dispatcher→worker 之间的排序。

---

## 六、不做的演进（明确 non-goals）

- **跨 worker 的全局优先级协调**：需要中心化调度器，违背当前 competing-consumer 模型（每个 worker 独立从 DB 拉）
- **动态优先级（aging）**：见 §四拒绝项 #7
- **优先级队列分组**（高优先级走单独表/连接池）：增加运维复杂度，物理隔离 worker pool 已能达到同样效果
- **基于 resource-aware 的优先级**（高优先级 task 来了重新触发 `IWorkerAssignmentStrategy` 重排）：把 priority 和 worker 选择耦合，违反 §四拒绝项 #6 的正交原则

**当前 FIFO + 单维 priority 已能覆盖绝大多数场景**。剩余边缘场景应通过业务侧手段解决（独立 schedule、独立 worker pool、业务侧任务分发层等）。
