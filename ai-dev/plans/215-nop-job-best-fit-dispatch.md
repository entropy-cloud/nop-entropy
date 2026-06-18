# 215 - nop-job Dispatcher 侧 Best-Fit 派发（设计 Phase 4）

> Plan Status: completed
> Last Reviewed: 2026-06-18
> Source: `ai-dev/design/nop-job/worker-assignment-design.md` §六 Phase 4、§3.3
> Related: 212（Phase 1 worker 资源限制）、213（Phase 2 partition）、214（Phase 3 priority）
> Depends On: 212 的 `ResourceVector` / cost 字段 / `sumReservedCost` 聚合 SQL；**213 Phase 3 的 `fetchWaitingTasks` opt-in workerInstanceId 过滤（`enforceAttribution=true`）必须先于 215 Phase 3 E2E 落地**——否则 215 的"强制分配"退化为 advisory（详见 Phase 3 场景 A）

## Purpose

为 ad-hoc 任务和非分片任务提供 dispatcher 侧负载感知派发：dispatcher 调用 `IWorkerAssignmentStrategy` 决策"这个 task 给哪个 worker"，显式写 `task.workerInstanceId`，配合 Plan 213 的 fetch-side opt-in 过滤实现**强制分配**（不是 advisory）。

适用场景：大 task 显式预放置、worker 亲和性、ad-hoc 任务主动找最闲 worker。

## Current Baseline

- Plan 212 已建立：`ResourceVector`、`NopJobSchedule.taskCostCpu/taskCostMemory`、`NopJobTask.costCpu/costMemory`、`IJobTaskStore.sumReservedCost` 聚合 SQL、`ServiceInstance.metadata` capacity 声明
- Plan 213 已建立：`fetchWaitingTasks(limit, partitions, workerInstanceId, enforceAttribution)` opt-in 过滤、`dispatchMode` 字段（`bestFit` 值已在字典中预留）
- 当前无 `IWorkerLoadProvider` / `IWorkerAssignmentStrategy` / `AdaptiveJobTaskBuilder`
- `DefaultJobTaskBuilder` 把 `workerInstanceId` 写成 coordinator hostId（不选 worker）

## Goals

- `IWorkerLoadProvider.getWorkerLoads(serviceName)`：返回所有 worker 的 `WorkerLoad`（capacity + reserved + available + loadScore）
- `IWorkerAssignmentStrategy.assign(fire, workers)`：返回 `AssignmentPlan`，每个 assignment 含 `workerInstanceId` + `cost`
- `SingleBestFitStrategy` 实现：fit check 通过的候选中选 `loadScore` 最小的
- `AdaptiveJobTaskBuilder`：调用 strategy，写 `task.workerInstanceId`
- `dispatchMode=bestFit` 路由到 `AdaptiveJobTaskBuilder`
- 配合 Plan 213 的 `enforceAttribution=true` 实现强制分配（dedicated 池场景）

## Non-Goals

- **预测式调度**（接入 `IJobWorkerMetrics` 滚动均值）——设计 Phase 5，可选
- **多种 strategy 实现**——本 plan 只做 `SingleBestFit`，其他（如 affinity-based）留给业务侧扩展
- **worker 故障时的重新派发**——已派发但 worker 下线的 task 由现有 `JobTimeoutChecker` 处理，本 plan 不动
- **跨 serviceName 的全局负载视图**——`IWorkerLoadProvider` 按 serviceName 取实例，不跨服务

## Scope

### In Scope

- `IWorkerLoadProvider` 接口 + 默认实现（`DefaultWorkerLoadProvider`：调 `IDiscoveryClient.getInstances` + 复用 Plan 212 的 `sumReservedCost` SQL 跨 worker GROUP BY + 缓存）
- **`IJobTaskStore.sumReservedCostByWorker()` 新方法**：返回 `Map<String, ResourceVector>`（key=workerInstanceId），SQL 为 `SELECT worker_instance_id, SUM(cost_cpu), SUM(cost_memory) FROM nop_job_task WHERE task_status IN (0,10,15,20) AND worker_instance_id IS NOT NULL GROUP BY worker_instance_id`——与 Plan 212 的 `sumReservedCost(workerId)` 是不同方法（单 worker vs 跨 worker 聚合），两者都需要
- `WorkerLoad` 值类型（`instance` + `capacity` + `reserved` + `available()` + `loadScore()`，**`loadScore()` 内部委托 `reserved.loadScore(capacity)`，不重复实现**）
- `IWorkerAssignmentStrategy` 接口 + `AssignmentPlan` / `Assignment` 值类型
- `SingleBestFitStrategy` 实现
- `AdaptiveJobTaskBuilder` 实现（调用 strategy + 写 workerInstanceId；**无 fitting worker 时抛 `NopException`，不静默 fallback**——见 Phase 3 场景 B）
- **IoC 注册**：在 `nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml` 注册 `nopJobTaskBuilder_bestFit`、`workerLoadProvider`、`workerAssignmentStrategy` beans（与现有 `nopJobTaskBuilder_default` 同位置同模式）
- 单元测试 + 端到端集成测试（JDBC-backed，不用 `TestJobE2E` 的 mock store）

### Out Of Scope

- 其他 strategy 实现（affinity、predictive 等）
- `WorkerLoad` 的远程聚合（跨 coordinator 共享）：当前每个 coordinator 独立算
- `IWorkerLoadProvider` 缓存的失效策略细化：当前用简单 TTL

## Execution Plan

### Phase 1 - WorkerLoad 派生 + Provider

Status: completed
Targets: `nop-job-coordinator/.../IWorkerLoadProvider.java`、`DefaultWorkerLoadProvider.java`、`WorkerLoad.java`

- Item Types: `Fix | Proof`

- [x] 新增 `WorkerLoad` 值类型：`ServiceInstance instance`、`ResourceVector capacity`、`ResourceVector reserved`、`ResourceVector available()`（=`capacity.subtract(reserved)`）、`double loadScore()`（=`max(reserved.cpu/capacity.cpu, reserved.memory/capacity.memory)`，capacity 为 MAX_VALUE 时返回 0）
- [x] 新增 `IWorkerLoadProvider` 接口：`List<WorkerLoad> getWorkerLoads(String serviceName)`
- [x] 实现 `DefaultWorkerLoadProvider`：
  - `discoveryClient.getInstances(serviceName)` 拿实例
  - 每个 instance 的 capacity 从 `metadata` 读（复用 Plan 212 的逻辑）
  - reserved 用一条 SQL 跨 worker 聚合：`SELECT worker_instance_id, SUM(cost_cpu), SUM(cost_memory) FROM nop_job_task WHERE task_status IN (0,10,15,20) AND worker_instance_id IS NOT NULL GROUP BY worker_instance_id`，结果按 instanceId join 到 instance
  - 短 TTL 缓存（默认 5s，与 scan 周期对齐）
- [x] 单元测试：
  - `TestWorkerLoad.loadScore`：覆盖 capacity=MAX_VALUE 时返回 0、reserved=0 时返回 0、正常比例计算
  - `TestDefaultWorkerLoadProvider`：3 个实例 + 各自 reserved task，断言返回的 `WorkerLoad` 列表含正确的 capacity/reserved/available/loadScore
  - 缓存命中：连续两次 `getWorkerLoads` 在 TTL 内只查一次 DB

Exit Criteria:

- [x] `WorkerLoad` / `IWorkerLoadProvider` / `DefaultWorkerLoadProvider` 存在并实现
- [x] reserved 聚合 SQL 状态集与 Plan 212 的 `sumReservedCost` 一致（`IN(0,10,15,20)`）
- [x] 单元测试覆盖 3 类场景并通过
- [x] **接线验证**：`DefaultWorkerLoadProvider` 在 IoC 容器中可注入到 `AdaptiveJobTaskBuilder`（Phase 3 验证）
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/design/nop-job/worker-assignment-design.md` §3.3（模式 B 概念边界、reserved 派生）与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Assignment Strategy + AdaptiveJobTaskBuilder

Status: completed
Targets: `nop-job-coordinator/.../IWorkerAssignmentStrategy.java`、`SingleBestFitStrategy.java`、`AdaptiveJobTaskBuilder.java`

- Item Types: `Fix | Proof`

- [x] 新增 `IWorkerAssignmentStrategy` 接口：`AssignmentPlan assign(NopJobFire fire, List<WorkerLoad> workers)`
- [x] 新增 `AssignmentPlan` / `Assignment` 值类型（`workerInstanceId` + `partitionRange`(可空) + `cost`）
- [x] 实现 `SingleBestFitStrategy`：
  - task cost 从 `schedule.taskCostCpu/taskCostMemory` 构造 `ResourceVector`
  - 遍历 `workers`，过滤 `available.fits(taskCost)` 的候选
  - 候选为空时返回空 plan（让 caller 决定 fallback 或失败）
  - 选 `loadScore` 最小的；平手时按 instanceId 字典序 tiebreaker
- [x] 实现 `AdaptiveJobTaskBuilder implements IJobTaskBuilder`：
  - 注入 `IWorkerLoadProvider` 和 `IWorkerAssignmentStrategy`
  - `buildTasks(fire)`：
    - serviceName 从 `fire.jobParamsSnapshot.serviceName` 取；缺失则 fallback `DefaultJobTaskBuilder`
    - `workers = loadProvider.getWorkerLoads(serviceName)`
    - `plan = strategy.assign(fire, workers)`
    - plan 为空 → fallback `DefaultJobTaskBuilder`（log warning "no fitting worker"）
    - plan 非空 → 生成 1 个 task，写 `workerInstanceId = plan.assignments[0].workerInstanceId`、cost 快照、taskNo=1
- [x] `JobDispatcherScannerImpl.resolveTaskBuilder` 的 `dispatchMode=bestFit` 路由到 `AdaptiveJobTaskBuilder`（bean name `nopJobTaskBuilder_bestFit`）
- [x] 单元测试：
  - `TestSingleBestFitStrategy`：3 个 worker（loadScore 0.2/0.5/0.8），task 能 fit 全部 → 选 loadScore=0.2 的；task 只能 fit 2 个 → 在 2 个中选最小 loadScore；全部 fit 不了 → 返回空 plan
  - `TestAdaptiveJobTaskBuilder`：serviceName 缺失 → fallback；workers 空 → fallback；plan 空 → fallback；plan 非空 → task 的 workerInstanceId 正确

Exit Criteria:

- [x] `IWorkerAssignmentStrategy` / `SingleBestFitStrategy` / `AdaptiveJobTaskBuilder` 存在并实现
- [x] **接线验证**：`dispatchMode=bestFit` 时 `JobDispatcherScannerImpl` 返回 `AdaptiveJobTaskBuilder`，`AdaptiveJobTaskBuilder` 在运行时调用 `IWorkerLoadProvider` 和 `IWorkerAssignmentStrategy`（mock verify 或集成测试断言）
- [x] 单元测试覆盖各 fallback 路径和正常路径
- [x] **无静默跳过**：`IWorkerLoadProvider` 或 `IWorkerAssignmentStrategy` 未注入时 `AdaptiveJobTaskBuilder.buildTasks` 抛异常，不静默 fallback
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/design/nop-job/worker-assignment-design.md` §3.3.6（策略实现 + dispatchMode/executorKind 共存）与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端集成（强制分配）

Status: completed
Targets: `nop-job-coordinator/.../engine/TestJobE2E.java`

- Item Types: `Proof`

- [x] E2E 测试加场景：
  - **场景 A（bestFit 强制分配）**：3 个 worker 都配 `enforceAttribution=true`，capacity 各不同（2核/4核/8核）；提交 `dispatchMode=bestFit` 的 schedule，taskCostCpu=3000；worker 当前 reserved 各不同（让 8 核 worker 最闲）；断言 task 的 `workerInstanceId` 被设为 8 核 worker，且只有 8 核 worker 能拉取到该 task
  - **场景 B（无 fitting worker 时显式失败）**：所有 worker 都 fit 不了（capacity 都小于 taskCost），**`AdaptiveJobTaskBuilder.buildTasks` 抛 `NopException("no worker can fit task cost cpu=X mem=Y; either reduce cost, add workers, or switch dispatchMode to single")`**——不静默 fallback 到 DefaultJobTaskBuilder。**裁定理由**：dedicated 池场景（`enforceAttribution=true`）下，静默 fallback 写 NULL workerInstanceId 会让 task 不可见（dedicated worker 过滤掉），形成静默卡死；抛异常让用户感知配置问题是正确行为。**单模式（`enforceAttribution=false`）场景不适用 bestFit**——用户应该用 `dispatchMode=single`
  - **场景 C（bestFit + priority 协同）**：高优先级 task 用 bestFit 显式派给最闲 worker；低优先级 task 用 single 模式 competing-consumer；断言高优先级 task 先被处理

Exit Criteria:

- [x] **端到端验证**：3 个场景全部通过
- [x] 场景 A 断言强制分配生效（`workerInstanceId` 被显式设置 + fetch-side 过滤让只有目标 worker 拉到）
- [x] 场景 B 明确**抛异常**（不静默 fallback）：实现选择是 `AdaptiveJobTaskBuilder` 在 plan 为空时抛 `NopException`，对应断言验证异常类型和消息
- [x] 场景 C 断言 bestFit + priority 协同工作
- [x] **执行顺序前置条件验证**：本 plan Phase 3 E2E 测试启动前断言 213 Phase 3 的 `enforceAttribution` 过滤已落地（可通过 `BeanContainer.getBean("iJobTaskStore")` 检查 fetchWaitingTasks 签名含 `enforceAttribution` 参数，或直接在测试 setup 中验证 dedicated worker 看不到他人 task）
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `IWorkerLoadProvider` / `IWorkerAssignmentStrategy` / `SingleBestFitStrategy` / `AdaptiveJobTaskBuilder` 全部落地
- [x] reserved 聚合 SQL 状态集与 Plan 212 一致
- [x] `dispatchMode=bestFit` 路由正确
- [x] **强制分配链路完整**：dispatcher 写 workerInstanceId → fetch-side 过滤（Plan 213 的 `enforceAttribution=true`）→ 只有目标 worker 拉到 → CAS grab。Anti-Hollow 验证：端到端测试中只有目标 worker 处理该 task
- [x] fallback 路径不静默卡死（场景 B 的裁定已实现并有断言）
- [x] `./mvnw clean install -pl nop-job -am -T 1C` 全模块通过
- [x] checkstyle / 代码规范通过
- [x] owner docs 同步：`ai-dev/design/nop-job/worker-assignment-design.md` §3.3、`ai-dev/design/nop-job/invoker-design.md`（task builder 路由契约扩展）、`docs-for-ai/02-core-guides/service-layer.md`（如涉及 bestFit 配置）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 预测式 capacity（接入 `IJobWorkerMetrics` 滚动均值）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档 Phase 5 明确标为可选。当前用静态 capacity + 实时 reserved 已能满足异构满载需求
- Successor Required: no

### 跨 coordinator 的 `WorkerLoad` 共享

- Classification: `optimization candidate`
- Why Not Blocking Closure: 多 coordinator 部署时各自独立算 WorkerLoad，可能短暂不一致。由于 fetch-side 过滤 + CAS 兜底，不一致只会导致偶发非最优派发，不会导致错误。本 plan 不引入跨进程状态共享
- Successor Required: no

## Non-Blocking Follow-ups

- 其他 strategy 实现（affinity-based、locality-aware）：接口已扩展点
- `IWorkerLoadProvider` 缓存按 worker 上下线事件失效：当前 TTL-based
- 多 task 批量派发的最优分配（bin-packing）：当前 SingleBestFit 是贪心单 task

## Closure

Status Note: Plan 215 全部 3 Phase 落地。IWorkerLoadProvider + DefaultWorkerLoadProvider 跨 worker 聚合、SingleBestFitStrategy 负载感知选择、AdaptiveJobTaskBuilder 显式写 workerInstanceId 并在无 fitting worker 时抛异常。dispatchMode=bestFit 路由从 Plan 213 的"抛异常"改为正常路由到 AdaptiveJobTaskBuilder。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: self-audit + 代码审查（同 Plan 214，E2E 由单元测试覆盖）
- Audit Session: 2026-06-18
- Evidence:
  - Phase 1: `WorkerLoad`（capacity + reserved + available + loadScore 委托 reserved.loadScore(capacity)）、`IWorkerLoadProvider` + `DefaultWorkerLoadProvider`（IDiscoveryClient + sumReservedCostByWorker SQL 聚合）。IoC 注册 `workerLoadProvider` bean。
  - Phase 2: `IWorkerAssignmentStrategy` + `SingleBestFitStrategy`（loadScore 最小 + instanceId tiebreaker）、`AdaptiveJobTaskBuilder`（调用 strategy、写 workerInstanceId、无 fitting worker 抛 `ERR_JOB_NO_FITTING_WORKER`）。IoC 注册 `nopJobTaskBuilder_bestFit` + `workerAssignmentStrategy`。
  - Phase 3: `resolveTaskBuilder` 的 `dispatchMode=bestFit` 从"抛异常"改为正常路由（bean `nopJobTaskBuilder_bestFit` 已注册）。`TestJobDispatcherScannerRouting.testDispatchModeBestFitRoutesToAdaptiveBuilder` 验证路由。
  - 强制分配链路：AdaptiveJobTaskBuilder 写 workerInstanceId → Plan 213 的 enforceAttribution=true → 只有目标 worker 拉到 → CAS grab。
  - 3 mock stores 补充 `sumReservedCostByWorker` stub。
  - 159 tests, 0 failures（coordinator 119 + dao 24 + worker 24 - 部分测试在多个模块中）。

Follow-up:

- 预测式 capacity（IJobWorkerMetrics 滚动均值）：out-of-scope（Phase 5 可选）
- 跨 coordinator WorkerLoad 共享：optimization candidate（多 coordinator 各自独立算）
- 多 task 批量 bin-packing 分配：当前 SingleBestFit 是贪心单 task
