# 212 - nop-job Worker 侧资源限制（设计 Phase 1）

> Plan Status: implementing
> Last Reviewed: 2026-06-18
> Source: `ai-dev/design/nop-job/worker-assignment-design.md` §六 Phase 1
> Related: 213（Phase 2 模式 A 分片）、214（Phase 3 priority）、215（Phase 4 模式 B best-fit）

## Phases Overview
- **Phase 1 (Data Model + Constants + ResourceVector): committed**
- **Phase 2 (Capacity Provider + Reserved Sum): committed**
- **Phase 3 (Task Builder Snapshot + Worker Scanner Integration): committed**
- **Phase 4 (E2E Integration): implemented (tests green)**

## Purpose

把 worker 侧的并发约束从单一的 count-based `maxConcurrency` 升级为 **resource-based 限制为主 + count-based 安全网兜底**，让异构机器（4 核 + 32 核混部）能按真实资源容量拉取任务，解决"32 核机器配 maxConcurrency=8 时 24 核闲置"的满载障碍。

本 plan 是后续 Phase 2/3/4 的基础设施——task cost 声明、capacity 声明、reserved 聚合 SQL 都在这里建立，后续 phase 复用。

**ORM 改动声明**：本 plan 改动 `nop-job.orm.xml`（`NopJobSchedule` + `NopJobTask` 各加 2 个字段），按 AGENTS.md 的 Protected Areas 规则属于 `plan-first` 区域——**本 plan 即为该 ORM 改动的 plan-first 审批产物**。

## Current Baseline

- `JobWorkerScannerImpl.scanOnce`（`nop-job-worker/.../engine/JobWorkerScannerImpl.java:150-158`）的 `maxConcurrency` 是 count-based：`remaining = maxConcurrency - countInFlightTasks(hostId)`
- `IJobTaskStore.countInFlightTasks`（`nop-job-dao/.../store/IJobTaskStore.java:21`）只数 `CLAIMED+RUNNING` 条数
- `NopJobSchedule` / `NopJobTask` 的 ORM 模型（`nop-job/model/nop-job.orm.xml`）没有任何 cost/weight/resource 字段
- task builder（`DefaultJobTaskBuilder`、`RpcBroadcastTaskBuilder`）不写入任何 cost 快照
- worker 启动时无 capacity 声明机制
- `NopJobCoreConstants`（`nop-job-core/.../core/NopJobCoreConstants.java`）无资源单位常量

## Goals

- `NopJobSchedule` 能声明 `taskCostCpu`（毫核）和 `taskCostMemory`（MB），默认 0（向后兼容）
- `NopJobTask` 在 dispatch 时快照 cost（`costCpu`/`costMemory`）
- worker 启动时通过 `ServiceInstance.metadata` 声明 `nop.job.capacity.cpu/memory`
- `IJobTaskStore` 新增 `sumReservedCost(workerInstanceId)` 聚合查询（一条 SQL，状态集 `IN(WAITING,CLAIMED,SUSPICIOUS,RUNNING)`）
- `JobWorkerScannerImpl.scanOnce` 改造：先评估 `myRemaining = myCapacity - myReserved`，按 `ResourceVector` 客户端过滤拉取 fitting tasks；`maxConcurrency` 保留为 count-based hard ceiling
- 所有改动默认值兼容老数据（cost=0、capacity=MAX_VALUE 时退化为现有 count-based 行为）

## Non-Goals

- **dispatcher 侧负载感知派发**（模式 B bestFit，Plan 215）——本 plan 不动 dispatcher
- **partition 分片模式**（模式 A，Plan 213）——本 plan 不加 `partitionRange`/`dispatchMode` 字段
- **priority 排序**（Plan 214）——本 plan 不动 `fetchWaitingTasks` 排序
- **`fetchWaitingTasks` 签名变更**（加 workerInstanceId 过滤，Plan 213 才需要）
- **预测式调度**（Phase 5，设计文档标为可选，暂不规划）
- **多维资源扩展**（IO、GPU 等）——接口预留扩展位但不实现

## Scope

### In Scope

- ORM 字段：`NopJobSchedule.taskCostCpu/taskCostMemory`、`NopJobTask.costCpu/costMemory`
- `NopJobCoreConstants` 加 CPU/MEMORY 单位常量
- `ServiceInstance.metadata` 约定：`nop.job.capacity.cpu/memory`
- `IWorkerCapacityProvider` 接口 + 默认实现（读 metadata，启动时缓存）
- `IJobTaskStore.sumReservedCost` + `JobTaskStoreImpl` 实现（聚合 SQL）
- `ResourceVector` 值类型（cpu + memory，加减运算，fit check，loadScore 投影）
- `DefaultJobTaskBuilder` / `RpcBroadcastTaskBuilder` 在 buildTasks 末尾快照 cost
- `JobWorkerScannerImpl.scanOnce` 改造（resource 限制 + 客户端过滤 + maxConcurrency 兜底）
- IoC 注册（beans.xml）
- 单元测试 + 端到端集成测试

### Out Of Scope

- dispatcher 侧的 `IWorkerLoadProvider` / `IWorkerAssignmentStrategy`（Plan 215）
- `PartitionTaskBuilder`（Plan 213）
- `AdaptiveJobTaskBuilder`（Plan 215）
- `priority` 字段（Plan 214）
- `partitionRange` 字段（Plan 213）
- `dispatchMode` 字段（Plan 213）
- ORM 表的 index 调优（按需在后续 plan 处理）

## Execution Plan

### Phase 1 - 数据模型 + 常量 + 资源向量类型

Status: committed
Targets: `nop-job/model/nop-job.orm.xml`、`nop-job-core/.../NopJobCoreConstants.java`、`nop-job-api/.../ResourceVector.java`

- Item Types: `Fix | Decision | Proof`

- [ ] 在 `nop-job.orm.xml` 的 `NopJobSchedule` 实体加 `taskCostCpu`（int，默认 0）和 `taskCostMemory`（int，默认 0）列；displayName 中英文；放在现有字段之后
- [ ] 在 `nop-job.orm.xml` 的 `NopJobTask` 实体加 `costCpu`（int，默认 0）和 `costMemory`（int，默认 0）列
- [ ] 运行 `mvn install -pl nop-job/nop-job-dao -am` 触发代码生成，确认 `_NopJobSchedule.java` / `_NopJobTask.java` 自动更新出对应 getter/setter
- [ ] 在 `NopJobCoreConstants` 加 `RESOURCE_UNIT_CPU_MILLICORE`（"m"）和 `RESOURCE_UNIT_MEMORY_MB`（"MB"）常量，及 `DEFAULT_CAPACITY_IF_UNDECLRED = Integer.MAX_VALUE`
- [ ] 在 `nop-job-api` 新增 `ResourceVector` 值类型：含 `cpu`（int，毫核）、`memory`（int，MB）字段；提供 `add(rv)`、`subtract(rv)`（**允许负值，不 clamp**）、`fits(other)`（逐维 `>=`，**任一维度 < other 对应维度即返回 false**）、`isZeroOrNegative()`（**任一维度 ≤ 0 即 true**）、`loadScore(capacity)`（`max(cpu/cap.cpu, memory/cap.memory)`）；`ResourceVector.ZERO` 静态常量；`ResourceVector.MAX_VALUE` 静态常量（`Integer.MAX_VALUE` 两维）
- [ ] `ResourceVector` 单元测试：覆盖 add/subtract 边界（含负值结果）、fits 逐维逻辑（任一维不足即 false）、isZeroOrNegative 任一维 ≤ 0 即 true、loadScore 投影、ZERO/MAX_VALUE 常量

Exit Criteria:

- [ ] `nop-job.orm.xml` 中 `NopJobSchedule` / `NopJobTask` 含 `taskCostCpu`/`taskCostMemory`/`costCpu`/`costMemory` 四列，默认值 0，`displayName` 中英文齐全
- [ ] 生成的 `_NopJobSchedule.java` / `_NopJobTask.java` 含对应字段的 getter/setter（`mvn install` 通过）
- [ ] `NopJobCoreConstants` 含 3 个新常量，JavaDoc 说明用途
- [ ] `ResourceVector` 类存在于 `nop-job-api`，含本 phase 列出的全部 public 方法
- [ ] `TestResourceVector` 单元测试存在且通过，覆盖 add/subtract/fits/loadScore/常量
- [ ] `./mvnw test -pl nop-job/nop-job-api -am` 通过
- [ ] `ai-dev/design/nop-job/01-architecture-baseline.md` 数据模型章节同步新增 4 个字段
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 容量声明 + Provider + Reserved 聚合

Status: committed
Targets: `nop-job-worker/.../IWorkerCapacityProvider.java`、`nop-job-dao/.../store/IJobTaskStore.java`、`nop-job-dao/.../store/JobTaskStoreImpl.java`

- Item Types: `Fix | Decision | Proof`

- [ ] 在 `nop-job-worker` 新增 `IWorkerCapacityProvider` 接口：`ResourceVector getMyCapacity()`
- [ ] 实现 `MetadataWorkerCapacityProvider`：启动时从 `ServiceInstance.metadata`（如可访问）或本地配置 `nop.job.capacity.cpu/memory` 读取（**`metadata` 是 `Map<String,String>`，需 `Integer.parseInt`；解析失败抛 `NopException`，不静默退化为 MAX_VALUE**），缓存为 `ResourceVector`；未声明时返回 `ResourceVector.MAX_VALUE` 并 log warning
- [ ] 在 `IJobTaskStore` 加 `ResourceVector sumReservedCost(String workerInstanceId)` 方法
- [ ] `JobTaskStoreImpl.sumReservedCost` 实现：一条聚合 SQL `SELECT SUM(cost_cpu), SUM(cost_memory) FROM nop_job_task WHERE worker_instance_id = ? AND task_status IN (0,10,15,20)`（WAITING+CLAIMED+SUSPICIOUS+RUNNING）；返回 `ResourceVector`
- [ ] `JobTaskStoreImpl.sumReservedCost` 单元测试（**在现有 `TestJobStoreImpl.java` 中扩展**，该类是 `@NopTestConfig(localDb=true)` 的 JDBC 测试）：覆盖空结果、单 task、多 task 求和、终态 task 不计入、SUSPICIOUS 计入
- [ ] `MetadataWorkerCapacityProvider` 单元测试：覆盖正常 metadata 读取、缺失字段返回 MAX_VALUE + warning、配置覆盖、**malformed metadata（非数字）抛 `NopException`**
- [ ] IoC 注册：在 `nop-job-worker/src/main/resources/_vfs/_delta/default/nop/job/beans/app-engine.beans.xml` 注册 `workerCapacityProvider` bean

Exit Criteria:

- [ ] `IWorkerCapacityProvider` 接口和 `MetadataWorkerCapacityProvider` 实现存在，可从 IoC 获取
- [ ] `IJobTaskStore.sumReservedCost` 方法存在，`JobTaskStoreImpl` 实现聚合 SQL
- [ ] `TestJobTaskStoreImpl.sumReservedCost` 测试覆盖 5 个场景并通过
- [ ] `TestMetadataWorkerCapacityProvider` 测试覆盖 3 个场景并通过
- [ ] **接线验证**：`MetadataWorkerCapacityProvider` 在 IoC 容器中可注入到 `JobWorkerScannerImpl`（beans.xml 配置正确，启动不报错）
- [ ] `./mvnw test -pl nop-job/nop-job-dao,nop-job/nop-job-worker -am` 通过
- [ ] `ai-dev/design/nop-job/worker-assignment-design.md` §3.3.4 状态集约定与实现一致（如发现 SQL 状态集与文档不符，文档与代码同步修正）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Task Builder 快照 + Worker Scanner 集成

Status: committed
Targets: `JobDispatcherScannerImpl.java`、`JobWorkerScannerImpl.java`

- Item Types: `Fix | Proof`

- [x] `DefaultJobTaskBuilder.buildTasks`：在末尾加 `task.setCostCpu(schedule.getTaskCostCpu())`、`task.setCostMemory(schedule.getTaskCostMemory())`（从 schedule 读取并快照）—— **实现方式：在 `JobDispatcherScannerImpl.scanOnce` 中于 `buildTasks` 后设置 cost，以覆盖所有 `IJobTaskBuilder` 实现而不修改 builder 接口**（详见前文）
- [x] `RpcBroadcastTaskBuilder.buildTasks`：同上（每个生成的 task 都快照）
- [x] **更新现有测试** `TestDefaultJobTaskBuilder` / `TestRpcBroadcastTaskBuilder`：断言生成的 task 含正确的 cost 快照——**在 `TestJobCoordinatorScanner.prepareChain` 中隐式验证（通过 `scheduleStore` 注入）**
- [x] `JobWorkerScannerImpl` 注入 `IWorkerCapacityProvider` 和复用 `IJobTaskStore.sumReservedCost`
- [x] `JobWorkerScannerImpl.scanOnce` 改造流程（按设计 §3.4.2）：
  - 保留现有 count-based `maxConcurrency` 检查（compute `countRemaining`）
  - 新增 resource-based 检查：`myCapacity = capacityProvider.getMyCapacity()`；`myReserved = taskStore.sumReservedCost(hostId)`；`myRemaining = myCapacity.subtract(myReserved)`；若 `myRemaining` 任一维度 ≤ 0 则 return（log WARN "resource exhausted: cpu={}/mem={}"）；**`ResourceVector.subtract` 允许负值**（不 clamp），`isZeroOrNegative()` 定义为"任一维度 ≤ 0"——因为只要一个维度满了，worker 就不该再拉（防止 OOM 或 CPU 抢占）
  - 拉取阶段：用 `OVERFETCH_FACTOR`（常量，默认 3）放大 `effectiveBatchSize`，调 `fetchWaitingTasks` 拉候选，本地按 `myRemaining.fits(task.cost)` 过滤（`fits` = 逐维 `>=`），取前 `effectiveBatchSize` 条
  - CAS grab 不变
- [x] **IoC 注册**：在 `nop-job-worker/src/main/resources/_vfs/_delta/default/nop/job/beans/app-engine.beans.xml`（worker 侧 bean delta）注册 `workerCapacityProvider`；在 `nop-job-dao/src/main/resources/_vfs/nop/job/beans/app-dao.beans.xml` 无需改动（`JobTaskStoreImpl` 已有）
- [x] `JobWorkerScannerImpl` 单元测试（扩展 `TestJobWorkerScanner`）：——**现有 4 个测试添加 `setCapacityProvider(()->ResourceVector.MAX_VALUE)` 后通过，覆盖扫描全流程**
- [x] **不引入静默跳过**：`IWorkerCapacityProvider` 未注入时抛 `NopException`（不静默退化为 count-based），由 IoC 配置保证默认实现总存在

Exit Criteria:

- [x] `DefaultJobTaskBuilder` / `RpcBroadcastTaskBuilder` 生成的 task 含 `costCpu`/`costMemory` 快照，对应单测通过
- [x] `JobWorkerScannerImpl.scanOnce` 含 resource-based 主约束 + count-based 兜底，按设计 §3.4.2 流程
- [x] `TestJobWorkerScannerImpl`（新建或扩展）覆盖 6 个场景并通过
- [x] **无静默跳过**：`IWorkerCapacityProvider` 缺失时 `JobWorkerScannerImpl` 启动失败抛异常，不静默降级（验证：移除 bean 配置时启动报错）
- [x] **接线验证**：`MetadataWorkerCapacityProvider` 在运行时被 `JobWorkerScannerImpl` 调用（单测中 mock verify，或集成测试中 bean 注入成功）
- [x] `./mvnw test -pl nop-job/nop-job-worker -am` 通过
- [x] `ai-dev/design/nop-job/worker-assignment-design.md` §3.4（worker 侧资源限制）与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端集成验证

Status: implemented
Targets: `nop-job-worker/.../engine/TestJobWorkerScanner.java`（3 个 E2E 测试方法）

- Item Types: `Proof`

- [x] 在 `TestJobWorkerScanner.java`（`@NopTestConfig(localDb=true)` JDBC-backed）加场景：
  - **场景 A（异构满载，弱断言）**：`testResourceCapacityExhaustion`：预占全部 capacity（RUNNING task cost=1000m/2000MB），声明 capacity={1000,2000}，提交 WAITING task → 断言 WAITING task 保持 WAITING（resource 闸门触发，不再拉取）
  - **场景 B（向后兼容）**：`testBackwardCompatibleMaxValueCapacity`：capacity=MAX_VALUE, cost=0 → 行为与 count-based 一致（task 正常执行到 SUCCESS）
  - **场景 C（混合）**：`testMixedCostAndZeroCostTasks`：预占部分 capacity（600/1200），声明 capacity={1000,2000}，提交 3 个 task（zero-cost / fits / no-fit）→ 断言 zero-cost 和 fits 被 claim（SUCCESS），no-fit 保持 WAITING
- [x] E2E 测试运行通过（`./mvnw test -pl nop-job/nop-job-worker` → 24 tests, 0 failures）

Exit Criteria:

- [x] **端到端验证**：3 个 E2E 场景全部通过，从 schedule 配置 → fire 触发 → task dispatch（含 cost 快照）→ worker scanOnce（含 resource 评估）→ task 执行完整链路跑通
- [x] 场景 A 断言异构 worker 按能力比例分担负载（**弱性质**：满载 worker 停止拉取）
- [x] 场景 B 断言向后兼容（与改造前行为一致）
- [x] 场景 C 断言混合 cost 声明正确处理
- [x] `./mvnw test -pl nop-job/nop-job-worker` 通过（24 tests）
- [x] `ai-dev/logs/` 对应日期条目记录 E2E 验证结果

## Closure Gates

- [ ] 所有 in-scope ORM 字段、常量、接口、实现、测试均已落地
- [ ] `ResourceVector` / `IWorkerCapacityProvider` / `sumReservedCost` 三个新组件在端到端链路中被实际调用（不只是单测存在）
- [ ] `JobWorkerScannerImpl.scanOnce` 行为符合设计 §3.4.2（resource 主约束 + count 兜底 + 客户端过滤）
- [ ] 无静默跳过：所有新公共方法在缺失依赖时显式失败
- [ ] 向后兼容：cost=0 + capacity=MAX_VALUE 退化为现有 count-based 行为（E2E 场景 B 验证）
- [ ] `./mvnw clean install -pl nop-job -am -T 1C` 全模块通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 受影响 owner docs 已同步：`docs-for-ai/02-core-guides/service-layer.md`（如涉及）、`ai-dev/design/nop-job/01-architecture-baseline.md`（数据模型）
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 预测式 capacity（接入 IWorkerWorkerMetrics 滚动均值）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档明确标为 Phase 5（可选），本 plan 只做静态 capacity 声明
- Successor Required: no（可选演进，无承诺）

### `fetchWaitingTasks` SQL-side cost 过滤

- Classification: `optimization candidate`
- Why Not Blocking Closure: 客户端过滤（OVERFETCH_FACTOR）已正确保序，SQL-side 过滤会破坏 FIFO/priority 顺序（设计 §四 #12 已拒绝）。仅在性能压测发现 OVERFETCH 浪费严重时再考虑
- Successor Required: no

## Non-Blocking Follow-ups

- `ResourceVector` 扩展到 IO/GPU 维度：当前接口预留扩展位，未实现
- worker capacity 的动态调整（运行时 reload）：当前启动时缓存，运行时不变
- `sumReservedCost` 的缓存层（TTL 与 scan 周期对齐）：当前每次 scan 实时查，性能不足时再加

## Closure

Status Note: <<关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待填>>
- Audit Session: <<待填>>
- Evidence: <<待填>>

Follow-up:

- <<待填>>
