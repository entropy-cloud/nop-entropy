# nop-job Worker 分配策略设计

**日期**：2026-06-18
**范围**：nop-job 的 worker 选择、partition 分片、worker 侧资源限制、负载感知派发
**状态**：草案
**关联**：`invoker-design.md`（IJobTaskBuilder 扩展点）、`cluster-ha-design.md`（dispatcher 间分区）、`priority-design.md`（混合负载配合）、`01-architecture-baseline.md`（数据模型）

---

## 一、设计结论

nop-job 在**异构机器 + 混合负载（每日批次 + ad-hoc 任务）**场景下需要**三层机制配合**，作用点不同、互补共存：

1. **Worker 侧资源自限制**（与 count-based `maxConcurrency` 并存，作为主约束）
   - worker 每次 scan 前评估 `myReserved = Σ taskCost WHERE workerInstanceId = me AND status 非终态`，按 `ResourceVector` 拉取能装下的 task
   - **count-based `maxConcurrency` 保留为 hard ceiling 安全网**，资源限制是主约束
   - 这是异构机器**充分利用**的核心机制——32 核 worker 自然多拉，4 核 worker 自然少拉，无需 dispatcher 介入

2. **Dispatcher 侧负载感知派发**（模式 B Best-Fit，适用于 ad-hoc 和需要显式放置的任务）
   - dispatcher 显式指定 `task.workerInstanceId`，配合 fetch-side 过滤实现强制分配（**不是 advisory**）
   - 通过 `WorkerLoad`（查 task 表派生）+ `ResourceVector` 多维成本模型实现

3. **Hash-Range 分片**（模式 A，适用于可分片的大批次）
   - 复用 `StringHelper.shortHash` + `WeightedPartitionAssigner` + `IntRangeSet`
   - task 携带 `partitionRange` 字符串
   - 按 weight 切 range 已经能利用异构机器（配合 task 内部多线程）

```
                  ┌─────────────────────────────────────────────┐
                  │  dispatcher 侧                                │
                  │  ┌────────────────┐    ┌────────────────┐    │
   可分片批次  →  │  │ 模式 A           │    │ 模式 B           │ ← ad-hoc / 显式放置
                  │  │ PartitionTask    │    │ BestFit          │
                  │  │ Builder          │    │ Strategy         │
                  │  │ (按 weight 切)    │    │ (强制 workerInstanceId) │
                  │  └────────┬────────┘    └────────┬────────┘    │
                  └───────────┼─────────────────────┼────────────┘
                              │                     │
                              ▼                     ▼
                  ┌─────────────────────────────────────────────┐
                  │  task 表 (WAITING，带 cost 和 partitionRange) │
                  │  fetchWaitingTasks（opt-in）按 workerInstanceId 过滤 │
                  └─────────────────────────┬───────────────────┘
                                            │
                                            ▼
                  ┌─────────────────────────────────────────────┐
                  │  worker 侧（自拉，对等）                       │
                  │  ┌──────────────────────────────────────┐   │
                  │  │ JobWorkerScannerImpl                   │   │
                  │  │  1. myRemaining = capacity − reserved │   │ ← worker 侧资源限制
                  │  │  2. 拉取 fitting tasks                │   │
                  │  │  3. CAS grab                          │   │
                  │  │  (maxConcurrency 仍是 hard ceiling)    │   │
                  │  └──────────────────────────────────────┘   │
                  └─────────────────────────────────────────────┘
```

**其他结论**：

4. **`ServiceInstance` 与 `WorkerLoad` 是两个概念**：ServiceInstance 是静态元数据，WorkerLoad 是从 task 表派生的实时可分配资源。
5. **partition range 用字符串表达**：`NopJobTask.partitionRange` 存 `IntRangeSet.toString()` 格式。
6. **保持向后兼容**：新增 `dispatchMode` 字段，默认 `single`；未声明 capacity 的 worker 退化为现有 count-based 行为。
7. **`dispatchMode` 与 `executorKind` 共存**：`dispatchMode` 优先（设了就用），未设时回退到 `executorKind` 路由。详见 §3.3.6。

---

## 二、背景与动机

### 2.1 当前缺口

- `DefaultJobTaskBuilder` 把 `workerInstanceId` 写死为 coordinator 的 `AppConfig.hostId()`（`DefaultJobTaskBuilder.java:33`），不选择 worker
- `RpcBroadcastTaskBuilder` 给每个 healthy 实例 1:1 建一个 task（`RpcBroadcastTaskBuilder.java:75-88`），全员参与、与负载感知和 hash-range 分片无关（仍设置广播分片位 `shardingIndex`/`shardingTotal`，但那是广播语义，不是 hash-range 语义）
- `JobPartitionResolver` 用 `WeightedPartitionAssigner` 只算"**我自己**负责的 fire 分区范围"（`JobPartitionResolver.java:90`），没有用于把单个 fire 的任务分片给多个 worker
- `NopJobTask` 没有任何表达"我负责哪段 hash range"或"我消耗多少资源"的字段
- `fetchWaitingTasks` 不按 `workerInstanceId` 过滤，`tryLockTasksForExecute` 还会**覆盖** `workerInstanceId`（`JobTaskStoreImpl.java:65`）——这意味着 dispatcher 的"派给谁"决策在 competing-consumer 模型下无法强制执行，必须配合 fetch-side 过滤

### 2.2 为什么不能直接用 ServiceInstance.weight

`ServiceInstance.weight` 是注册时配置的静态权重。它表达不了：

- **已分配但未执行**的任务所占用的资源（WAITING/CLAIMED 状态的任务已落在某个 worker 名下但还没开始跑）
- worker 当前的**实时压力**（同样 weight 的两个 worker，一个闲置一个拥塞，应区别对待）
- **任务间的资源需求差异**（CPU 密集任务和 IO 密集任务按 weight 平均分显然不对）

把"实时负载"塞进 ServiceInstance 会污染 nop-cluster 全部下游消费者（LB、路由、限流），且引入"负载刷新频率"这种与服务发现语义无关的耦合。

### 2.3 maxConcurrency 是 count-based，这是异构机器满载的根本障碍

`JobWorkerScannerImpl.scanOnce`（`JobWorkerScannerImpl.java:150-158`）当前限制逻辑：

```
remaining = maxConcurrency − countInFlightTasks(hostId)
if remaining <= 0: return
effectiveBatchSize = min(batchSize, remaining)
```

`maxConcurrency` 是**任务条数**，假设了"所有 worker 同质、所有 task 同样大小"。在异构机器 + 混合负载场景下：

- 4 核 worker 配 `maxConcurrency=8` 会过载，配 `maxConcurrency=4` 又闲置
- 32 核 worker 配 `maxConcurrency=8`，跑 8 个小 task 时 24 核空闲，跑 8 个大 task 时可能 OOM
- worker 自拉模型决定了"机器忙不忙"由 worker 自己判断，dispatcher 完全不参与——**这是没有 dispatcher 侧负载感知的根本原因**

worker 侧的资源自限制（§3.4）和 dispatcher 侧的负载感知派发（§3.3 模式 B）**共同**解决这个矛盾。

---

## 三、核心设计

### 3.1 命名澄清：三处 "partition" 不可混淆

| 字段 | 所在表 | 含义 | 层次 |
|------|--------|------|------|
| `partitionIndex` | `NopJobFire` / `NopJobTask`（既有） | 单个 int，coordinator 按 `JobPartitionResolver` 过滤 fire | **dispatcher 间**分工 |
| `partitionRange`（新增） | `NopJobTask` | 字符串，本 task 覆盖的 hash range，由 `IntRangeSet.parse()` 还原 | **worker 间**分工 |
| `partitionIndex` | **业务表**（业务侧自加） | 业务记录的 hash bucket，值 = `StringHelper.shortHash(业务键)` | **业务记录**定位 |

框架只管前两个；第三个是业务侧的事，仅约定"用 `StringHelper.shortHash` 计算且值域为 [0, 32766]"。

### 3.2 模式 A：Hash-Range 分片（适用于可分片的大批次）

#### 3.2.1 整体数据流

```
1. 业务表加 partitionIndex 列（业务侧责任）
   partitionIndex = StringHelper.shortHash(业务键)  ∈ [0, 32766]

2. Schedule 配置：
   dispatchMode = "partition"
   partitionCount = N（或由 healthy 实例数决定）

3. fire 触发 → PartitionTaskBuilder.buildTasks(fire):
   a. serviceName 从 fire.jobParamsSnapshot.serviceName 取
      （与 RpcBroadcastTaskBuilder.java:50 同一来源）
   b. 选 N 个 worker（healthy 实例；可选 weight 排序）
   c. ranges = WeightedPartitionAssigner.assignPartitions(
                  IntRangeBean.shortRange(),     // [0, 32766]
                  selectedWorkers)               // 按 weight 切分
   d. 为每个 worker 生成 1 个 task：
        task.partitionRange     = ranges[i].toRangeSet().toString()
        task.workerInstanceId   = selectedWorkers[i].instanceId
        task.taskNo             = i + 1

4. task 写入 DB，worker 按 workerInstanceId 过滤拉取（§3.4.5），抢占执行

5. 业务侧 invoker 从 task 拿到 partitionRange → IntRangeSet.parse()
   SQL 加过滤：WHERE partitionIndex BETWEEN range.offset AND range.getLast()
   （注意：getLast() = offset + limit − 1，闭区间。与 JobTaskStoreImpl.addPartitionFilter
    使用 FilterBeans.between(prop, range.getStart(), range.getLast()) 的写法一致）
```

#### 3.2.2 框架侧职责（极小）

只新增一个 `PartitionTaskBuilder`（`IJobTaskBuilder` 的实现，按 `dispatchMode="partition"` 路由），核心逻辑两行：

```
List<IntRangeBean> ranges = weightedPartitionAssigner.assignPartitions(
        IntRangeBean.shortRange(), selectedWorkers);
// 每个 worker 一个 task：
//   task.partitionRange = ranges[i].toRangeSet().toString()
```

**不写任何 partition 切分代码**——`WeightedPartitionAssigner` 已经实现且有测试覆盖。

#### 3.2.3 业务侧职责

- 业务表加 `partitionIndex` 列，值 = `StringHelper.shortHash(业务键)`
- invoker 实现：从 task 上下文取 `partitionRange` 字符串 → `IntRangeSet.parse()` → 对每个 `IntRangeBean` 拼 SQL `WHERE partitionIndex BETWEEN range.offset AND range.getLast()` 过滤
- 这部分**完全是业务代码**，与 job 框架解耦

#### 3.2.4 `partitionRange` 字符串格式

`NopJobTask.partitionRange` 是 string 列，值是 `IntRangeSet.toString()`（`IntRangeSet.java:64-66`）。每个 `IntRangeBean.toString()`（`IntRangeBean.java:76-82`）格式为 `"offset,limit"`：

- **单段（最常见）**：`"100,500"` → offset=100, limit=500，覆盖 **[100, 600)**，即 100..599 这 500 个值
- **多段**（rebalance 后可能出现非连续）：`"100,500|600,400"` 用 `|` 分隔（`IntRangeSet.parse` 在 `IntRangeSet.java:55` 用 `|` 切分）
- **空字符串 / null**：不参与分片（向后兼容 `single` 模式的 task）

`IntRangeSet.parse(s)` 与 `IntRangeSet.toString()` 严格双向，**直接复用，不自定义格式**。

**精度建议 400**：多段 range 序列化后字符数会增长（每段约 13 字符），`precision 100` 在 rebalance 多次后可能溢出。建议 `precision 400`，并在序列化前调用 `IntRangeSet.compact()`（`IntRangeSet.java:107`）合并相邻段以缩短字符串。

#### 3.2.5 模式 A 的 N 怎么定 + 与 Phase 1 的独立性

两种策略，可在 `PartitionTaskBuilder` 配置：

- **固定 N**：从 `schedule.partitionCount` 取，按 weight 切。适合"任务大小预估稳定"的场景。
- **动态 N = healthy 实例数**：从 `IDiscoveryClient.getInstances(serviceName)` 取 healthy && enabled 集合，N = size。`serviceName` 来源同 §3.2.1。

**模式 A 按 weight 切 range 已经能利用异构机器**——32 核 weight=800 拿 80% 数据，4 核 weight=100 拿 10%，各自 task 内部多线程并行。

**关键**：模式 A **不依赖** Phase 1 的 cost 声明（`taskCostCpu`/`taskCostMemory`）。它用 `ServiceInstance.weight` 切 range，不读 cost 字段。模式 A 可以独立于 Phase 1 实施，甚至可以先于 Phase 1 落地。模式 A 解决"一个批次怎么分片"，**解决不了**"多个 task 抢同一个 worker 时谁先、占多少"——后者由 §3.3 模式 B 和 §3.4 worker 侧资源自限制解决。

### 3.3 模式 B：Best-Fit 单 task 派发（适用于 ad-hoc 和显式放置场景）

适用于 ad-hoc 任务、非分片任务、需要"显式指定 worker"的场景（大 task 预放置、worker 亲和性）。**与模式 A 正交**，两者可同时启用（不同 schedule 走不同 `dispatchMode`）。

#### 3.3.1 概念边界

| 概念 | 来源 | 表达 | 生命周期 |
|------|------|------|---------|
| ServiceInstance | `IDiscoveryClient` | addr/port/weight/healthy/enabled/metadata | 注册时静态 |
| WorkerCapacity | worker 启动声明（经 metadata） | `ResourceVector` | 启动时静态 |
| TaskCost | `NopJobSchedule` 配置 | `ResourceVector` | schedule 定义时静态 |
| WorkerReserved | task 表派生 | `ResourceVector`（Σ 非终态 task 的 cost） | 随 task 状态变化 |
| WorkerLoad | dispatcher 派生 | `(instance, capacity, reserved, available)` | 实时派生 |

**不可违反的边界**：

- **ServiceInstance 不持有 WorkerLoad**。ServiceInstance 的语义是"我是谁、我在哪、我能不能用"，不是"我现在多忙"。
- **WorkerLoad 不写回数据库**。它是派生值，真源是 task 表 + capacity 配置。允许 short-TTL 缓存，但不能成为独立真源。
- **TaskCost 在 dispatch 时落表为 NopJobTask.costCpu/costMemory**。这是为了 reserved 查询能用一条聚合 SQL 算出。

#### 3.3.2 多维成本模型 `ResourceVector`

- 至少 `cpu`、`memory` 两维，接口可扩展（IO、GPU 等留扩展位，不预定义）
- 单位约定：cpu 用毫核（1000 = 1 核），memory 用 MB。约定写在 `NopJobCoreConstants`（`nop-job-core`，coordinator 和 worker 都依赖此模块），常量化
- 缺失维度按 0 处理，不报错（向后兼容未配置的任务/worker）
- 算术运算：加（Σ reserved）、减（capacity − reserved）、标量投影 `loadScore()`

**fit check（硬约束）逐维比较**：

```
worker.available.cpu >= task.cost.cpu
AND worker.available.memory >= task.cost.memory
```

**rank（软偏好）投影成单个 double**：

```
loadScore = max(reserved.cpu / capacity.cpu, reserved.memory / capacity.memory)
```

#### 3.3.3 接口契约

放在 `nop-job-coordinator`，仅定义职责：

```
IWorkerLoadProvider:
    List<WorkerLoad> getWorkerLoads(String serviceName)
    // 从 IDiscoveryClient 拿实例 + 查 task 表聚合 reserved + 应用缓存
    // 聚合 SQL 同时供 worker 侧 §3.4 使用

IWorkerAssignmentStrategy:
    AssignmentPlan assign(NopJobFire fire, List<WorkerLoad> workers)
    // 决定本次 fire 的任务如何分配到 worker

WorkerLoad (值类型):
    ServiceInstance instance
    ResourceVector capacity
    ResourceVector reserved
    ResourceVector available()    // = capacity − reserved
    double loadScore()            // 排名用标量投影

AssignmentPlan / Assignment (值类型):
    String workerInstanceId
    IntRangeBean partitionRange   // best-fit 场景一般为 null
    ResourceVector cost
```

#### 3.3.4 reserved 派生的实现约束 + 状态集统一定义

`IWorkerLoadProvider` 实现必须：

- **一条聚合 SQL，按 workerInstanceId 分组求和**，禁止 N+1：

```sql
SELECT worker_instance_id, SUM(cost_cpu), SUM(cost_memory)
FROM nop_job_task
WHERE task_status IN (0, 10, 15, 20)   -- WAITING, CLAIMED, SUSPICIOUS, RUNNING（全部非终态）
  AND worker_instance_id IS NOT NULL
GROUP BY worker_instance_id
```

- **状态归属（统一定义，dispatcher 侧和 worker 侧共用）**：
  - **计入 reserved**：`WAITING(0)`、`CLAIMED(10)`、`SUSPICIOUS(15)`、`RUNNING(20)`——全部非终态
  - **不计入**：`SUCCESS(30)` / `FAILED(40)` / `TIMEOUT(50)` / `CANCELED(60)`——终态
  - **SUSPICIOUS 计入的理由**：SUSPICIOUS 语义是"可能还在跑"（与 `JobTaskStoreImpl.fetchRunningTasks` 在 `JobTaskStoreImpl.java:76-77` 把 SUSPICIOUS 与 RUNNING/CLAIMED 并列查询一致），从资源角度看它**仍占用 worker 的执行能力**。不计入反而会让 worker 低估自身负载、过度拉取，比"预算虚高"风险更大

- **与 `countInFlightTasks` 的状态集不对称（故意为之，不要统一）**：
  - `countInFlightTasks`（`JobTaskStoreImpl.java:101-102`）：`IN (CLAIMED=10, RUNNING=20)`，仅"正在执行"
  - `sumReservedCost`（§3.3.4）：`IN (WAITING=0, CLAIMED=10, SUSPICIOUS=15, RUNNING=20)`，"全部已归因"
  - **不对称的理由**：count-based `maxConcurrency` 度量"我正在并行执行几个"（concurrency），WAITING task 还没开始不算并发；resource-based 度量"我被承诺/已归因多少资源占用"（commitment），WAITING task 已被 dispatcher 指向某 worker，应占该 worker 的资源预算。**统一两个状态集会破坏各自语义**：把 WAITING 计入 countInFlight 会让 worker 过早停止拉取（pull-deadlock）；把 WAITING 排除出 sumReserved 会让 dispatcher 派发决策基于过时数据（虚高可用）

- **WAITING 计入的归因依赖**：WAITING task 计入 reserved 的前提是它的 `workerInstanceId` 已经指向某个 worker。在 `single` 模式下 `DefaultJobTaskBuilder` 把 `workerInstanceId` 写成 coordinator 的 hostId（`DefaultJobTaskBuilder.java:33`），所以**非 co-deployed 场景下 worker 自查 reserved 看不到这些 WAITING task**——这是 §3.4.4 提到的"single 模式受益"的边界条件。在 `partition`/`bestFit` 模式下 WAITING task 在 dispatch 时就指向目标 worker，reserved 能正确反映

- **缓存策略**：TTL 与 scan 周期对齐（默认 5s），单次 scan batch 内复用
- **容忍 `workerInstanceId` 为空的行**（历史数据），跳过不计入任何 worker

**关键复用**：这条 SQL（同一状态集 `IN (0,10,15,20)`）同时服务于 §3.3 dispatcher 选 worker 和 §3.4 worker 自评估 remaining。

#### 3.3.5 容量声明

Worker 启动时通过 `ServiceInstance.metadata` 携带：

- `nop.job.capacity.cpu`（毫核）
- `nop.job.capacity.memory`（MB）

**不引入 `IWorkerCapacityRegistry`**：capacity 是静态的，与 instance 生命周期一致，放 metadata 足够。与 `cluster-ha-design.md` "复用服务发现，不新建心跳机制"原则一致。

未声明 capacity 的 worker 视为"无限容量"（`Integer.MAX_VALUE`），仅供 backward compat；启用资源限制时启动检测到未声明应 warning log。

#### 3.3.6 策略实现 + dispatchMode/executorKind 共存

`SingleBestFit`：fit check 通过的候选中选 `loadScore` 最小的。

**`dispatchMode` 与 `executorKind` 的共存规则**：

- 当前路由靠 `executorKind` → bean `nopJobTaskBuilder_<executorKind>`（`JobDispatcherScannerImpl.java:149-159`），值有 `test`/`rpc`/`rpcBroadcast`
- 引入 `dispatchMode` 后，**优先级**：`dispatchMode` 非空时按 `dispatchMode` 路由，否则回退 `executorKind`
- `dispatchMode="broadcast"` 与 `executorKind="rpcBroadcast"` 都路由到 `RpcBroadcastTaskBuilder`——`dispatchMode` 是更细粒度的表达，老配置不动继续工作

**与 `IJobTaskBuilder` 的协作**：

- `dispatchMode = single`（默认）或未设 → 走 `executorKind` 路由，通常落到 `DefaultJobTaskBuilder`
- `dispatchMode = partition` → `PartitionTaskBuilder`（模式 A），不调用 strategy
- `dispatchMode = broadcast` → `RpcBroadcastTaskBuilder`，不调用 strategy
- `dispatchMode = bestFit` → `AdaptiveJobTaskBuilder` 调用 `IWorkerAssignmentStrategy`

**为什么 worker 选择不直接放进 `IJobTaskBuilder`**：`IJobTaskBuilder` 决定"一次 fire 生成几个 task、每个 task 的形态"，worker 选择是正交维度。分开后可以独立演化。

### 3.4 Worker 侧资源自限制（异构机器满载的核心机制）

#### 3.4.1 问题与目标

`JobWorkerScannerImpl.scanOnce` 当前的 `maxConcurrency` 是 count-based（`JobWorkerScannerImpl.java:150-158`）。在异构机器场景下，count-based 限制无法表达"32 核机器能装下更多任务"。

**目标**：worker 每次 scan 前评估"我还能装下多少"，按 `ResourceVector` 拉取能 fit 的 task。32 核 worker 自然多拉，4 核 worker 自然少拉，**无需 dispatcher 介入**。

#### 3.4.2 改造后的 scanOnce 流程

```
JobWorkerScannerImpl.scanOnce():

  // 1. count-based 安全网（保留 maxConcurrency 作为 hard ceiling）
  if maxConcurrency > 0:
    runningCount = taskStore.countInFlightTasks(hostId)
    countRemaining = maxConcurrency - runningCount
    if countRemaining <= 0: return
  else:
    countRemaining = batchSize

  // 2. resource-based 主约束
  myCapacity = workerCapacityProvider.getMyCapacity()   // ResourceVector，启动时从 metadata 读
  myReserved = taskStore.sumReservedCost(hostId)         // 复用 §3.3.4 的聚合 SQL，scope 到本 worker
  myRemaining = myCapacity - myReserved

  if myRemaining.isZeroOrNegative(): return              // 资源已满

  // 3. 拉取 + 客户端过滤（保序，避免 SQL-side 过滤破坏 FIFO/priority）
  //    先按 priority DESC, createTime ASC, taskId ASC 拉 top-N（与 priority-design 对齐）
  //    再本地按 myRemaining 过滤，跳过 fit 不了的 task
  effectiveBatchSize = min(countRemaining, batchSize)
  candidates = taskStore.fetchWaitingTasks(effectiveBatchSize * OVERFETCH_FACTOR, partitions)
  fittingTasks = candidates.filter(t -> myRemaining.fits(t.cost)).take(effectiveBatchSize)

  // 4. CAS grab（不变）
  lockedTasks = taskStore.tryLockTasksForExecute(fittingTasks, hostId, lockTimeoutMs)
  for task in lockedTasks: executeTask(task)
```

#### 3.4.3 关键设计要点

| 要点 | 说明 |
|------|------|
| **`maxConcurrency` 不去掉** | 保留为 count-based hard ceiling 安全网。资源限制是**主约束**，条数限制是**兜底**，defense in depth |
| **per-scan 评估，不 per-task** | 一个 scan batch 内 `myRemaining` 评估一次，不去逐 task 重算。CAS 失败的 task 不影响下一次 scan。**例外（AR-83 修正）**：对**新负载**（非自身归因的候选）逐 task 递减 `myRemaining`，使同一次 scan 的累计认领不超过 capacity；对**自身归因**的候选不递减（其 cost 已在 reserved 中） |
| **race 由 CAS 兜底** | fetch 和 CAS 之间其他 worker 可能抢走，CAS 失败就静默跳过，与现有模型一致 |
| **stale reserved 可接受** | 本次 scan 拿到的 reserved 不含本 batch 刚 CAS 的 task。下一次 scan 会反映。短暂高估不致灾难 |
| **未声明 cost 的 task** | costCpu/costMemory = 0 → always fit。只受 `maxConcurrency` 约束，**完全向后兼容** |
| **未声明 capacity 的 worker** | capacity = MAX_VALUE → 总是 fit。退化为现有 count-based 行为 |
| **客户端过滤而非 SQL 过滤** | SQL-side `WHERE cost_cpu <= ?` 会跳过队首大 task、破坏 `priority DESC, createTime ASC` 顺序，导致大 task 饿死。客户端过滤保序，但用 `OVERFETCH_FACTOR`（如 3-5x）多拉再筛 |
| **自身归因候选去重计（AR-83）** | `fetchWaitingTasks` 在 `enforceAttribution=true` 时返回 `workerInstanceId=self OR NULL` 的候选，其中 self 归因的 WAITING 候选 cost 已计入 `sumReservedCost(hostId)`（WAITING ∈ RESERVED_TASK_STATUSES）。若 fit 校验再减一次，则单个任务 cost 被算两遍，使 `cost > capacity/2` 的任务永不被认领。修正：fit 校验时对 `workerInstanceId == hostId` 的候选把其自身 cost 加回 `myRemaining`（消除双重计算），空闲 worker 即可认领接近 capacity 的大任务。**共享常量 `RESERVED_TASK_STATUSES` 不变**（dispatcher best-fit 决策依赖 WAITING 计入），double-count 仅在 worker scanner 内部解决 |

#### 3.4.4 与模式 B 的关系 + single 模式的边界条件

| 维度 | §3.3 模式 B（dispatcher 侧） | §3.4 worker 侧资源限制 |
|------|----------------------------|----------------------|
| 作用点 | dispatcher `IWorkerAssignmentStrategy.assign()` | worker `JobWorkerScannerImpl.scanOnce()` |
| 决策 | "这个 task 给哪个 worker" | "我这个 worker 还能拉多少" |
| 数据源 | 全体 worker 的 `WorkerLoad` | 仅本 worker 的 capacity + reserved |
| 共用机制 | `ResourceVector` + §3.3.4 聚合 SQL（同一状态集） | 同上 |
| 必需性 | 仅 `dispatchMode=bestFit` 时启用 | **所有 dispatchMode 都受益**（前提见下） |

**single 模式受益的边界条件**：`DefaultJobTaskBuilder` 把 `workerInstanceId` 写成 coordinator 的 hostId（`DefaultJobTaskBuilder.java:33`）。在 **co-deployed（worker 和 coordinator 同 JVM）** 场景下，worker 自查 `sumReservedCost(hostId)` 能看到这些 WAITING task；在**非 co-deployed** 场景下看不到（WAITING task 归在 coordinator 名下）。所以"single 模式也受益"的准确表述是：**single 模式下 worker 侧资源限制对 CLAIMED+RUNNING 永远生效，对 WAITING 仅在 co-deployed 时生效**。`partition`/`bestFit` 模式下 WAITING task 在 dispatch 时就指向目标 worker，reserved 能完整反映。

#### 3.4.5 fetch-side 过滤：模式 B 强制分配的执行路径

**问题**：competing-consumer 模型下，`fetchWaitingTasks` 不按 `workerInstanceId` 过滤（`JobTaskStoreImpl.java:44-52`），且 `tryLockTasksForExecute` 会**覆盖** `workerInstanceId` 为抢到者的 hostId（`JobTaskStoreImpl.java:65`）。这意味着 dispatcher 的"派给谁"决策如果不配合 fetch-side 改动，就只是 **advisory（建议性）** 的，任何 worker 都能抢走。

**解决**：`fetchWaitingTasks` 增加可选的 `workerInstanceId` 过滤参数，**过滤是 opt-in 的**（默认关闭，保留 competing-consumer 向后兼容）：

```
// 改造后的 fetchWaitingTasks 签名
List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions,
                                   String workerInstanceId, boolean enforceAttribution)

// enforceAttribution = false（默认，向后兼容）：
//   WHERE task_status = WAITING [AND partition filter]
//   → 不过滤 workerInstanceId，所有 WAITING task 对所有 worker 可见（competing-consumer 保留）

// enforceAttribution = true（partition/bestFit 模式专用 worker 池启用）：
//   WHERE task_status = WAITING
//     AND (workerInstanceId = ? OR workerInstanceId IS NULL)
//   → 只看到显式派给自己或无主的 task
```

**配置约定**：worker 启动配置 `nop.job.fetch.enforce-attribution`（默认 `false`）。

- **`false`（默认）**：保留 competing-consumer，所有 WAITING task 可见。适用于 co-deployed 部署、混跑多种 dispatchMode 的通用 worker 池
- **`true`**：只拉取显式派给自己（`workerInstanceId = me`）或真正无主（`workerInstanceId IS NULL`）的 task。适用于 dedicated worker 池（专门跑 partition/bestFit task），避免被 single 模式的 task 干扰

**关于 single 模式 + 非 co-deployed 的已知限制**：`DefaultJobTaskBuilder` 把 `workerInstanceId` 写成 coordinator 的 hostId（`DefaultJobTaskBuilder.java:33`，用作 SUSPICIOUS 检测的哨兵值）。在非 co-deployed 部署下：

- `enforceAttribution = false`：其他 worker 仍能看到这些 task（competing-consumer），`tryLockTasksForExecute` 会把 `workerInstanceId` 改写成抢到者的 hostId——SUSPICIOUS 检测此后按新 hostId 工作。这是当前代码的既有行为，**本设计保持不变**
- `enforceAttribution = true`：这些 task 对其他 worker 不可见（既不 `= me` 也不 `IS NULL`）。这意味着 dedicated worker 池**不适合承接 single 模式的 task**——`DefaultJobTaskBuilder.java:27-32` 的注释本就声明"仅在 co-deployed 时正确"。dedicated 池应只承接 `dispatchMode = partition | bestFit` 的 schedule

这一改动让模式 A 和模式 B 的"显式派给"决策**真正强制执行**（在 dedicated 池中），而不是 advisory。`tryLockTasksForExecute` 仍负责 CAS 串行化。

### 3.5 数据模型变更

**`NopJobSchedule` 新增字段**：

| 字段 | 类型 | 默认 | 用途 | 模式 |
|------|------|------|------|------|
| `dispatchMode` | string（字典 `job/dispatch-mode`） | `single` | 路由到 task builder | 全部 |
| `partitionCount` | int | 1 | 切分数量（N） | A |
| `taskCostCpu` | int | 0 | 任务 CPU 开销（毫核） | B / worker-side |
| `taskCostMemory` | int | 0 | 任务内存开销（MB） | B / worker-side |

**`NopJobTask` 新增字段**：

| 字段 | 类型 | 默认 | 用途 | 模式 |
|------|------|------|------|------|
| `partitionRange` | string（precision 400） | null | `IntRangeSet.toString()`，如 `"100,500"` 或 `"100,500\|600,400"` | A |
| `costCpu` | int | 0 | dispatch 时从 schedule 快照 | B / worker-side |
| `costMemory` | int | 0 | dispatch 时从 schedule 快照 | B / worker-side |

字段都默认空/0，老数据与未配置的任务均能工作。**`costCpu`/`costMemory` 是 §3.3 模式 B 和 §3.4 worker 侧资源限制的共同前提**——必须先有 cost 声明，两套机制才能工作。ORM 改动属于 `plan-first` 区域。

---

## 四、拒绝了什么

1. **pack 多维资源进单个 long**：pack 只在无锁 CAS 热路径有价值，job 调度每秒一次，无性能收益；且 pack 要么退化成加权标量（丢失 fit check 的多维语义），要么强加字典序优先。

2. **把 WorkerLoad 写进 ServiceInstance**：ServiceInstance 是 nop-cluster 多处复用的标准模型，承载实时负载会污染下游所有消费者。

3. **由 worker 进程上报实时资源占用**：跨进程心跳、上报频率、丢失节点的清理复杂度大；task 表已经是真源，一条聚合 SQL 即可算出。

4. **每个 worker 上报自己的 reserved**：dispatcher 完全可以从 task 表算出，worker 上报会导致两份真源不一致。

5. **新增 `IWorkerCapacityRegistry`**：静态配置放 `ServiceInstance.metadata` 足够。

6. **在 `IJobTaskBuilder` 内部做 worker 选择**：混淆"task 形态"和"worker 选择"两个正交维度。

7. **重新发明 partition 切分逻辑**：`WeightedPartitionAssigner` + `IntRangeBean.shortRange` + `IntRangeSet` 已经完整覆盖，且测试覆盖。模式 A 不写任何切分代码。

8. **自定义 partition range 字符串格式**：`IntRangeSet.toString()` 已是稳定序列化格式，`parse()` 反向严格一致。直接复用。

9. **把模式 A 和模式 B 耦合在一起设计**：分片场景不需要 `ResourceVector` / `WorkerLoad` 的全部能力，best-fit 场景不需要 hash range。两种模式独立设计、独立启用。

10. **去掉 `maxConcurrency`，完全靠 resource-based 限制**：count-based 是简单可靠的安全网，防止"task 都没声明 cost 时无限拉取"。资源限制是主约束，条数限制是兜底，两者共存不冲突。

11. **worker 侧资源限制依赖 dispatcher 通知**：违背当前 competing-consumer 对等模型。worker 应该能独立从 task 表算出自己的 reserved，不需要 dispatcher 推送。

12. **SQL-side cost 过滤**（`WHERE cost_cpu <= ?`）：会跳过队首大 task、破坏 `priority DESC, createTime ASC` 顺序，导致大 task 饿死。改用客户端过滤（fetch 多拉再筛），保留顺序语义。大 task 长期 fit 不进去应通过模式 B 显式预放置解决。

13. **模式 B 作为 advisory（建议性）派发**：competing-consumer 模型下，不配合 fetch-side 过滤的"派给谁"决策会被任意 worker 抢走，模式 B 失去意义。必须配合 §3.4.5 的 `fetchWaitingTasks` workerInstanceId 过滤（`enforceAttribution=true` 的 dedicated 池场景）实现强制分配。

---

## 五、与已有设计的关系

- **`invoker-design.md`**：`IJobTaskBuilder` 扩展点保持不变。模式 A 新增 `PartitionTaskBuilder`，模式 B 新增 `AdaptiveJobTaskBuilder`，通过 `dispatchMode`（新字段）与 `executorKind`（既有字段）共存路由，详见 §3.3.6。
- **`cluster-ha-design.md`**：`JobPartitionResolver` 与本设计正交——前者决定"哪些 fire 归我 dispatcher 处理"，后者决定"我处理的这个 fire 给哪个 worker"。**模式 A 是 `cluster-ha-design.md` 中 `PartitionAssignHelper` 纯函数分区思想从 dispatcher 间延伸到 worker 间**。
- **`priority-design.md`**：**正交但混合负载场景下协同收益最大**。priority 管"先后"，本设计管"给谁/装多少"。priority 让 ad-hoc 跳过批次 FIFO，本设计让 ad-hoc 落到合适的 worker、worker 按资源限制拉取。两者可独立部署、各自产生价值，同时部署时收益叠加。**注意交互**：priority + 客户端 cost 过滤（§3.4.3）共存在同一 fetch 流程，客户端过滤的"跳过 fit 不了的"会先于 priority 生效——高优先级大 task 在 worker 资源不足时仍会被跳过，此时应通过模式 B 显式预放置。
- **`metrics-design.md`**：将来 `IJobWorkerMetrics` 的滚动窗口可以接入 §3.3 `IWorkerLoadProvider` 作为"预测式 capacity"输入，MVP 不做。
- **`01-architecture-baseline.md`**：`NopJobSchedule` / `NopJobTask` 表结构变更、`JobWorkerScannerImpl.scanOnce` 改造、`JobTaskStoreImpl.fetchWaitingTasks` 签名变更落地时需同步到架构基线层数据模型和核心流程章节。

---

## 六、演进路径

按"缺口大小 × 收益广度"排序，每一步都可独立落地、独立产生价值。**Phase 之间有依赖关系，但不是严格线性**：

### Phase 1：基础设施 + worker 侧资源限制（覆盖最大缺口）

**收益**：异构机器满载问题立即缓解。`dispatchMode=single` 也对 CLAIMED+RUNNING 生效。

**必要改动清单**（实测，非"加几个字段"）：

- 数据模型：`NopJobSchedule.taskCostCpu/taskCostMemory`、`NopJobTask.costCpu/costMemory`（4 个 ORM 字段）
- worker capacity 声明：`ServiceInstance.metadata` 加 `nop.job.capacity.cpu/memory`
- worker 侧读取 capacity：新增 `WorkerCapacityProvider`（读 metadata，启动时缓存）
- task store 新方法：`IJobTaskStore.sumReservedCost(workerInstanceId)` + 聚合 SQL（§3.3.4）
- task builder 改造：所有 `IJobTaskBuilder` 实现在 `buildTasks` 末尾加 `task.setCostCpu(schedule.getTaskCostCpu())` / `setCostMemory(...)` 快照
- worker 侧 `JobWorkerScannerImpl.scanOnce` 改造：§3.4.2 流程（含客户端过滤）
- `NopJobCoreConstants` 加单位常量（CPU 毫核、MEMORY MB）

**不需要**：`IWorkerLoadProvider`、`IWorkerAssignmentStrategy`、`fetchWaitingTasks` 签名变更（Phase 2 才需要）、`PartitionTaskBuilder`（Phase 2 才需要）

### Phase 2：模式 A 分片（可分片批次）

**收益**：可分片批次按 weight 切 range，配合 Phase 1 的 worker 资源限制，异构机器利用率进一步提升。

**必要改动**：

- 数据模型：`NopJobSchedule.partitionCount`、`NopJobTask.partitionRange`、`NopJobSchedule.dispatchMode`（3 个 ORM 字段 + 字典）
- 新增 `PartitionTaskBuilder`：调 `WeightedPartitionAssigner` + 写 `partitionRange`
- `fetchWaitingTasks` 加 `workerInstanceId` 过滤参数（§3.4.5）
- `job/dispatch-mode` 字典定义

**与 Phase 1 的关系**：**模式 A 不依赖 Phase 1 的 cost 声明**（用 `ServiceInstance.weight` 切 range，不读 cost）。模式 A 可以先于或并行于 Phase 1 实施。但若两者都做完，模式 A 的 task 会自动享受 Phase 1 的 worker 资源限制。

### Phase 3：priority（混合负载排队）

**收益**：ad-hoc 任务不再被批次 FIFO 卡住。

**必要改动**：见 `priority-design.md` —— `NopJobTask.priority` + `fetchWaitingTasks` 排序变更。

**与 Phase 1 的交互**：priority 的 `priority DESC, createTime ASC` 排序与 Phase 1 的客户端 cost 过滤共存在同一 fetch 流程（§3.4.2 步骤 3）。客户端过滤保序，priority 在过滤前生效。

### Phase 4：模式 B dispatcher 侧 best-fit（ad-hoc 智能派发）

**收益**：ad-hoc 任务主动找最空闲的 worker，不再靠 worker 自拉碰运气；大 task 显式预放置。

**必要改动**：

- 新增 `IWorkerLoadProvider`（复用 Phase 1 的 reserved 聚合 SQL，跨 worker GROUP BY）
- 新增 `IWorkerAssignmentStrategy` + `SingleBestFit` 实现
- 新增 `AdaptiveJobTaskBuilder`（调用 strategy，写 `task.workerInstanceId`）
- `dispatchMode="bestFit"` 路由

**依赖 Phase 1**：模式 B 需要 cost 声明（taskCostCpu/Memory）和 reserved 聚合 SQL，这些都在 Phase 1 建立。**依赖 Phase 2 的 `fetchWaitingTasks` workerInstanceId 过滤**（§3.4.5）——否则模式 B 是 advisory 的（§四 #13）。

### Phase 5：预测式调度（可选）

`IWorkerLoadProvider` 接入 `IJobWorkerMetrics` 滚动均值，提前预估 worker 排队时间。

**为什么 Phase 1 排第一**：异构机器满载是你场景的核心痛点，Phase 1 改造面虽不小（见上清单）但**收益最广**（所有 task、所有 dispatchMode 都受益）。模式 A 和模式 B 都建立在其之上（模式 B 共享 reserved SQL，模式 A 共享 fetch-side 改造）。

每一步对应一个独立 plan，不预先承诺时间。
