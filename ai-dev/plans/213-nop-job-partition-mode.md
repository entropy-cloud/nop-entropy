# 213 - nop-job Hash-Range 分片模式（设计 Phase 2）

> Plan Status: in progress
> Last Reviewed: 2026-06-18
> Source: `ai-dev/design/nop-job/worker-assignment-design.md` §六 Phase 2、§3.2
> Related: 212（Phase 1 worker 资源限制）、214（Phase 3 priority）、215（Phase 4 best-fit）
> Depends On: 212 的 ORM 字段（cost）和 `ResourceVector` 类型，但**不阻塞**——模式 A 用 `ServiceInstance.weight` 切 range，不读 cost

## Purpose

为大批次可分片任务提供框架级支持：dispatcher 调用 `WeightedPartitionAssigner` 把 `[0, 32766]` 的 short-hash range 按 worker weight 切成 N 段，每个 task 携带一段 `partitionRange` 字符串。业务方 invoker 解析 range 拼 SQL `WHERE partitionIndex BETWEEN offset AND getLast()`，无需自己实现切分逻辑。

**ORM 改动声明**：本 plan 改动 `nop-job.orm.xml`（`NopJobSchedule` 加 2 字段、`NopJobFire` 加 1 字段、`NopJobTask` 加 1 字段），按 AGENTS.md 属于 `plan-first` 区域——**本 plan 即为该 ORM 改动的 plan-first 审批产物**。

## Current Baseline

- `RpcBroadcastTaskBuilder`（`nop-job-coordinator/.../engine/RpcBroadcastTaskBuilder.java`）已实现 1:1 广播，给每个 healthy 实例一个 task，设置 `shardingIndex/shardingTotal/targetHost`——但是**全员参与、平均分、与 hash-range 分片语义不同**
- `WeightedPartitionAssigner`（`nop-cluster-core/.../assigner/WeightedPartitionAssigner.java`）已存在，按 `ServiceInstance.weight` 切 `IntRangeBean`，有测试覆盖
- `IntRangeBean.shortRange()` 返回 `[0, 32766]`；`IntRangeBean.toRangeSet().toString()` 序列化为 `"offset,limit"` 格式；`IntRangeSet.parse()` 反向
- `JobDispatcherScannerImpl.resolveTaskBuilder`（`nop-job-coordinator/.../engine/JobDispatcherScannerImpl.java:149-159`）按 `executorKind` 路由到 `nopJobTaskBuilder_<kind>` bean
- `NopJobTask` 没有 `partitionRange` 字段
- `NopJobSchedule` 没有 `dispatchMode` / `partitionCount` 字段
- `fetchWaitingTasks`（`nop-job-dao/.../store/JobTaskStoreImpl.java:44-52`）不按 `workerInstanceId` 过滤

## Goals

- 新增 `dispatchMode` 字段（`single`/`partition`/`broadcast`/`bestFit`，默认 `single`），按 `dispatchMode` 优先、`executorKind` 回退路由 task builder
- 新增 `PartitionTaskBuilder`：调 `WeightedPartitionAssigner.assignPartitions(IntRangeBean.shortRange(), selectedWorkers)`，为每个 worker 生成 1 个 task，写 `partitionRange`（`IntRangeSet.toString()` 格式）、`workerInstanceId`
- `NopJobTask` 加 `partitionRange` 字段（string，precision 400）
- `NopJobSchedule` 加 `partitionCount` 字段（int，默认 1）
- `fetchWaitingTasks` 加 opt-in `workerInstanceId` 过滤参数（`enforceAttribution` 默认 `false` 保留 competing-consumer）
- 业务方拿到 `partitionRange` 字符串后能 `IntRangeSet.parse()` 还原并拼 SQL

## Non-Goals

- **模式 B bestFit dispatcher 侧派发**（Plan 215）——`dispatchMode=bestFit` 路由留给 215
- **priority 排序**（Plan 214）
- **worker 侧资源限制**（Plan 212 已完成或并行进行）——模式 A 用 weight 切 range，不依赖 cost
- **业务侧 invoker 实现**——业务方自己读 `partitionRange` 拼 SQL，本 plan 只提供框架契约和文档示例
- **rebalance 后多段 range 的运行时合并**——序列化前调 `IntRangeSet.compact()` 即可，本 plan 不实现自动 rebalance

## Scope

### In Scope

- ORM：`NopJobSchedule.dispatchMode`（string + 字典 `job/dispatch-mode`）、`NopJobSchedule.partitionCount`（int，默认 1）、**`NopJobFire.dispatchMode`（string，schedule 快照）**、`NopJobTask.partitionRange`（string，precision 400）
- `dispatchMode` 快照接线：4 处（`JobPlannerScannerImpl:230`、`JobScheduleStoreImpl:213`、`NopJobScheduleBizModel:245`、`NopJobFireBizModel:138`，仿 `executorKind` 现有模式）
- 字典：`job/dispatch-mode` 字典定义（single/partition/broadcast/bestFit）
- `PartitionTaskBuilder` 类 + 路由（bean `nopJobTaskBuilder_partition`，注册在 `_vfs/nop/job/beans/app-engine.beans.xml`）
- `JobDispatcherScannerImpl.resolveTaskBuilder` 改造：`dispatchMode` 优先，未设或 `single` 时回退到现有 `executorKind` 路由；`bestFit` 在 Plan 215 落地前显式抛异常
- `fetchWaitingTasks` 签名加 `workerInstanceId` + `enforceAttribution` 参数；`JobTaskStoreImpl` 实现对应 SQL 分支
- 单元测试（在 `TestJobStoreImpl`、`TestJobWorkerScanner` 等现有测试类中扩展）+ 端到端集成测试

### Out Of Scope

- `IWorkerLoadProvider` / `IWorkerAssignmentStrategy` / `AdaptiveJobTaskBuilder`（Plan 215）
- `priority` 字段及 `fetchWaitingTasks` 排序变更（Plan 214）
- 业务侧 invoker 代码（业务方负责）
- `partitionRange` 的多段 rebalance 合并算法（用现有 `IntRangeSet.compact()` 即可）

## Execution Plan

### Phase 1 - 数据模型 + 字典 + 路由改造

Status: completed
Targets: `nop-job/model/nop-job.orm.xml`、字典、`JobDispatcherScannerImpl.java`

- Item Types: `Fix | Decision`

- [x] `nop-job.orm.xml` 的 `NopJobSchedule` 加 `dispatchMode`（string，精度 30，默认 `single`，displayName 中英文）、`partitionCount`（int，默认 1）
- [x] **`nop-job.orm.xml` 的 `NopJobFire` 也加 `dispatchMode`**（string，精度 30，默认 null，作为 schedule 的快照）—— 与 `executorKind` 的双表模式一致（`executorKind` 在 Schedule 和 Fire 上都有）
- [x] `nop-job.orm.xml` 的 `NopJobTask` 加 `partitionRange`（string，precision 400，默认 null）
- [x] 新增字典 `job/dispatch-mode`（值：`single`/`partition`/`broadcast`/`bestFit`，对应 displayName）
- [x] `mvn install -pl nop-job/nop-job-dao -am` 触发代码生成，确认 `_NopJobSchedule`/`_NopJobFire`/`_NopJobTask` 的 getter/setter 出现
- [x] **`dispatchMode` 快照接线（4 处，仿 `executorKind` 现有模式）**：
  - `JobPlannerScannerImpl.java:230` 附近：`fire.setDispatchMode(schedule.getDispatchMode())`
  - `JobScheduleStoreImpl.java:213` 附近：`newFire.setDispatchMode(schedule.getDispatchMode())`
  - `NopJobScheduleBizModel.java:245` 附近：`fire.setDispatchMode(schedule.getDispatchMode())`
  - `NopJobFireBizModel.java:138` 附近：`fire.setDispatchMode(schedule.getDispatchMode())`
- [x] `JobDispatcherScannerImpl.resolveTaskBuilder` 改造：先查 `fire.getDispatchMode()`；非空且非 `single` 时按 dispatchMode 路由（`partition` → bean `nopJobTaskBuilder_partition`）；**`bestFit` 此时 bean 不存在，必须显式抛 `NopException("dispatchMode=bestFit not yet implemented")`，不静默 fallback**（防止 Plan 215 未落地时静默退化）；未设或 `single` 时回退现有 `executorKind` 路由
- [x] `DefaultJobTaskBuilder` / `RpcBroadcastTaskBuilder` 行为不变，作为 fallback 路径保留

Exit Criteria:

- [x] ORM 含 4 个新字段（Schedule 2 个 + Fire 1 个 + Task 1 个），字典 `job/dispatch-mode` 存在且含 4 个值
- [x] **`fire.dispatchMode` 在 4 个 snapshot 站点都被正确设置**（端到端验证：创建 schedule with `dispatchMode=partition` → 触发 fire → 查 fire 行的 dispatchMode 列非 null）
- [x] `JobDispatcherScannerImpl.resolveTaskBuilder` 按 `fire.dispatchMode` 优先路由，未设时回退 `executorKind`
- [x] **`dispatchMode=bestFit` 在 Plan 215 落地前抛异常（不静默 fallback）**——验证：提交 `dispatchMode=bestFit` 的 schedule 触发 fire 时抛 `NopException`，不静默走 `DefaultJobTaskBuilder`
- [x] 单元测试覆盖：`dispatchMode=partition` 路由到 `PartitionTaskBuilder`、`dispatchMode=single/未设` 走 `executorKind`、`dispatchMode=bestFit`（Plan 215 前）抛异常、`dispatchMode=broadcast` 路由到 `RpcBroadcastTaskBuilder`
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/design/nop-job/01-architecture-baseline.md` 数据模型 + 核心流程章节同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - PartitionTaskBuilder 实现

Status: completed
Targets: `nop-job-coordinator/.../engine/PartitionTaskBuilder.java`

- Item Types: `Fix | Proof`

- [x] 新增 `PartitionTaskBuilder implements IJobTaskBuilder`
- [x] `buildTasks(fire)` 流程（按设计 §3.2.1）：
  - 从 `fire.jobParamsSnapshot.serviceName` 取服务名（与 `RpcBroadcastTaskBuilder.java:50` 一致）；缺失则 fallback 到 `DefaultJobTaskBuilder`
  - `discoveryClient.getInstances(serviceName)` 拿 healthy && enabled 实例；空则 fallback
  - 选 N：`schedule.partitionCount > 0` 时取前 N 个实例；否则 N = healthy 实例数
  - 调 `weightedPartitionAssigner.assignPartitions(IntRangeBean.shortRange(), selectedWorkers)` 得到 `List<IntRangeBean>`
  - 为每个 worker 生成 1 个 task：`partitionRange = ranges[i].toRangeSet().toString()`（单段 range 不需要 `compact()`，`IntRangeSet.size()==1` 时 `compact()` 返回 `this`，等价）、`workerInstanceId = selectedWorkers[i].instanceId`、`taskNo = i+1`、`shardingIndex=i`、`shardingTotal=N`
  - **cost 快照（如 Plan 212 已落地）**：`task.setCostCpu(schedule.getTaskCostCpu())` / `task.setCostMemory(schedule.getTaskCostMemory())`。**Plan 212 未落地时跳过此步**（Plan 213 不强依赖 212，但若 212 已落地则自动受益）
- [x] 注入 `IDiscoveryClient`（nullable）和 `WeightedPartitionAssigner`（默认实现）
- [x] **IoC 注册**：在 `nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml` 加 `<bean id="nopJobTaskBuilder_partition" class="io.nop.job.coordinator.engine.PartitionTaskBuilder"/>`（与现有 `nopJobTaskBuilder_default`/`nopJobTaskBuilder_rpcBroadcast` 同位置同模式）
- [x] 单元测试 `TestPartitionTaskBuilder`：
  - serviceName 缺失 → fallback DefaultJobTaskBuilder
  - discoveryClient null → fallback
  - instances 空 / 全 unhealthy → fallback
  - 3 个 healthy 实例 + partitionCount=3 → 3 个 task，partitionRange 各不同，合起来覆盖 [0, 32766]
  - partitionCount=2 但 3 个实例 → 取前 2 个实例
  - partitionCount 未设 → N = healthy 实例数
  - 序列化格式正确：`IntRangeSet.parse(task.partitionRange)` 能反向还原

Exit Criteria:

- [x] `PartitionTaskBuilder` 存在并实现上述流程
- [x] **接线验证**：`JobDispatcherScannerImpl.resolveTaskBuilder` 在 `dispatchMode=partition` 时返回 `PartitionTaskBuilder`（通过 bean name `nopJobTaskBuilder_partition`）
- [x] `TestPartitionTaskBuilder` 覆盖 7 个场景并通过
- [x] partitionRange 字符串格式与 `IntRangeSet.toString()` 一致，`parse()` 严格反向（断言）
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/design/nop-job/worker-assignment-design.md` §3.2.1 数据流与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - fetchWaitingTasks opt-in 过滤 + 端到端

Status: completed
Targets: `IJobTaskStore.java`、`JobTaskStoreImpl.java`、`JobWorkerScannerImpl.java`、E2E 测试

- Item Types: `Fix | Proof`

- [x] `IJobTaskStore.fetchWaitingTasks` 签名扩展：加 `String workerInstanceId` 和 `boolean enforceAttribution` 参数（保留旧重载委托新方法，传 null/false）
- [x] `JobTaskStoreImpl.fetchWaitingTasks` 实现：
  - `enforceAttribution=false`（默认）：SQL 不加 `workerInstanceId` 过滤（保留 competing-consumer）
  - `enforceAttribution=true`：SQL 加 `AND (worker_instance_id = ? OR worker_instance_id IS NULL)`
- [x] `JobWorkerScannerImpl.scanOnce` 改造：从配置 `nop.job.fetch.enforce-attribution`（默认 false）读 `enforceAttribution`，传给 `fetchWaitingTasks`；workerInstanceId 总是传 `AppConfig.hostId()`
- [x] IoC 配置：worker beans.xml 暴露 `nop.job.fetch.enforce-attribution` 配置项
- [x] `TestJobTaskStoreImpl.fetchWaitingTasks` 扩展：覆盖 `enforceAttribution=true` 时只看到自己的 task + 无主 task；`false` 时看到全部 WAITING task
- [x] 端到端测试（扩展 `TestJobE2E` 或新建）：
  - **场景 A（partition 模式 + enforceAttribution=true）**：3 个 worker 配 `enforceAttribution=true`，提交 `dispatchMode=partition` 的 schedule，partitionCount=3；断言每个 worker 只看到属于自己的那 1 个 task（partitionRange 各不同）
  - **场景 B（向后兼容）**：worker 配 `enforceAttribution=false`（默认），提交 `dispatchMode=single` 的 schedule；断言行为与改造前一致（任意 worker 可抢）
  - **场景 C（混合）**：worker 配 `enforceAttribution=true`，同时存在 single 模式 task（workerInstanceId=coordinator）和 partition 模式 task（workerInstanceId=各自）；断言只看到自己的 partition task + 无主 task，**看不到 single 模式 task**（验证 dedicated 池隔离）

Exit Criteria:

- [x] `fetchWaitingTasks` 新签名存在，旧调用方通过旧重载兼容
- [x] `enforceAttribution` 默认 false，保留 competing-consumer 向后兼容
- [x] **端到端验证**：3 个 E2E 场景全部通过，从 schedule 配置 → dispatch → worker fetch（含过滤）→ 执行完整链路
- [x] 场景 C 断言 dedicated 池隔离生效（`enforceAttribution=true` 的 worker 看不到 single 模式的 coordinator-attributed task）
- [x] **无静默跳过**：`enforceAttribution=true` 的 worker 在 dedicated 池场景下如果完全没有派给自己的 task，不静默退化为抢别人的，而是返回空（让超时检测等机制处理）
- [x] `./mvnw test -pl nop-job -am -T 1C` 全模块通过
- [x] `ai-dev/design/nop-job/worker-assignment-design.md` §3.4.5（fetch-side 过滤 opt-in 语义）与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `dispatchMode` 字段 + `job/dispatch-mode` 字典落地
- [x] `PartitionTaskBuilder` 复用 `WeightedPartitionAssigner`，不重新发明切分逻辑
- [x] `partitionRange` 字符串格式严格匹配 `IntRangeSet.toString()`/`parse()` 双向
- [x] `fetchWaitingTasks` opt-in `enforceAttribution` 过滤默认 false，向后兼容
- [x] dedicated 池（`enforceAttribution=true`）与 single 模式 task 隔离正确（E2E 场景 C）
- [x] `./mvnw clean install -pl nop-job -am -T 1C` 全模块通过
- [x] checkstyle / 代码规范通过
- [x] owner docs 同步：`docs-for-ai/02-core-guides/service-layer.md`（如涉及 dispatchMode 配置说明）、`ai-dev/design/nop-job/01-architecture-baseline.md`、`ai-dev/design/nop-job/invoker-design.md`（task builder 路由契约）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### `partitionRange` 多段 range 的运行时 rebalance

- Classification: `optimization candidate`
- Why Not Blocking Closure: 序列化前调 `IntRangeSet.compact()`（`IntRangeSet.java:107`）已合并相邻段。运行时 rebalance 需要 dispatcher 跟踪 worker 上下线事件，复杂度高，初版不做
- Successor Required: no

### `bestFit` dispatchMode 路由

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 字典中预留 `bestFit` 值，但 `AdaptiveJobTaskBuilder` 在 Plan 215 实现。本 plan 只确保 `dispatchMode=bestFit` 路由失败时抛清晰异常（不静默 fallback），不实现 bestFit 本身
- Successor Required: yes（Plan 215）

## Non-Blocking Follow-ups

- `PartitionTaskBuilder` 的 worker 选择策略扩展（按负载过滤、按地理亲和）：当前只按 healthy && enabled
- `partitionCount` 与 healthy 实例数不一致时的策略（当前取前 N 个，可扩展为按 weight 选 top-N）

## Closure

Status Note: <<关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待填>>
- Audit Session: <<待填>>
- Evidence: <<待填>>

Follow-up:

- <<待填>>
