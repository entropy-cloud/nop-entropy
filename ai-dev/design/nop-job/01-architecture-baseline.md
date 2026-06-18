# nop-job 架构基线

**日期**：2026-04-04（更新于 2026-06-07）
**范围**：`nop-job` 模块
**状态**：active

---

## 一、设计结论

1. 系统采用 schedule/fire/task 三层模型，数据库是权威状态源
2. 核心对象有三个：`JobSchedule`（调度定义）、`JobFire`（触发批次）、`JobTask`（执行投递）
3. 运行时分为五层：控制面 → 批次层 → 投递层 → 执行层 → 重试桥接层
4. 集群扫描和 claim 模式复用 `nop-retry` 的成熟做法

## 二、模块划分

| 模块 | 职责 | 说明 |
|------|------|------|
| `nop-job-api` | DTO、SPI、公共常量 | 保留并整理 |
| `nop-job-core` | trigger/calendars/cron 纯计算库 | 缩小职责，trigger 计算逻辑和运行时调度逻辑必须分开 |
| `nop-job-engine` | planner、dispatcher、timeout、cancel | 承载运行时 |
| `nop-job-dao` | ORM、store 实现 | 按新表模型 |
| `nop-job-service` | BizModel、管理命令、查询接口 | 领域命令 |
| `nop-job-web` | xmeta、权限、页面 | 适配新实体 |
| `nop-job-retry-adapter` | 对接 `nop-retry` 的可选桥接模块 | 可选新增 |

如果暂时不想新增模块，`nop-job-engine` 也可以先落在 `nop-job-core` 的 `engine` 包下，但目标边界不变：**trigger 计算逻辑和运行时调度逻辑必须分开**。

## 三、运行时分层

```
Management API / BizModel
        |
        v
JobSchedule Service
        |
        +------------------------------+
        |                              |
        v                              v
Schedule Store                    Manual Fire Service
        |                              |
        v                              v
Job Planner Scanner ------------> JobFire
                                      |
                                      v
                               Job Dispatch Scanner
                                      |
                                      v
                                   JobTask
                                      |
                                      v
                             IJobInvoker / Executor SPI
                                      |
                                      v
                         Completion Processor / Timeout Checker
                                      |
                                      +----> JobFire / JobTask final state
                                      |
                                      +----> IJobRetryBridge (optional)
```

五层：

1. **控制面**：`JobSchedule`
2. **批次层**：`JobFire`
3. **投递层**：`JobTask`
4. **执行层**：`IJobInvoker`
5. **重试桥接层**：`IJobRetryBridge`，默认 no-op

## 四、核心对象职责契约

### 4.1 JobSchedule

`JobSchedule` 是调度定义聚合根，负责：

1. 保存执行器信息与参数快照来源。
2. 保存 trigger 定义。
3. 保存下一次触发游标 `nextFireTime`。
4. 保存运行汇总字段，例如 `fireCount`、`activeFireCount`、`lastEndTime`。

它不负责：

1. 保存单次执行结果详情。
2. 保存多次 task 投递记录。
3. 保存 retry 过程。

### 4.2 JobFire

`JobFire` 表示一次触发批次，来源可能是：

1. 定时调度。
2. 手工触发。
3. 后续扩展的恢复性触发。

它是调度与执行之间的边界对象。

### 4.3 JobTask

`JobTask` 表示一次具体投递执行。

即使 V1 只有单任务模式，仍保留这一层，原因如下：

1. 后续做广播/分片时不需要再改主模型。
2. 可以单独表达分发失败、执行失败、超时、取消。
3. `JobFire` 可以只表达批次最终结果，不被执行细节污染。

## 五、数据模型设计

### 5.1 `nop_job_schedule`

实体 `NopJobSchedule`。

| 关键字段 | 说明 |
|---------|------|
| `job_schedule_id` | 主键 |
| `namespace_id` | 命名空间，和 `nop-retry` 保持一致 |
| `group_id` | 逻辑分组 |
| `job_name` | 作业唯一名，在 `namespace_id + group_id` 内唯一 |
| `schedule_status` | `DISABLED/ENABLED/PAUSED/COMPLETED/ARCHIVED` |
| `executor_kind` | 执行器类型，V1 先支持 `BEAN` |
| `dispatch_mode` | 派发模式快照（single/partition/broadcast/bestFit），从 schedule 快照（Plan 213） |
| `trigger_type` | `CRON/FIXED_RATE/FIXED_DELAY/ONCE` |
| `block_strategy` | `DISCARD/OVERLAY/PARALLEL` |
| `timeout_seconds` | 执行超时时间 |
| `retry_policy_id` | 可选，对接 `nop_retry_policy.sid` |
| `partition_index` | 调度分区 |
| `fire_count` / `active_fire_count` | 运行汇总 |
| `next_fire_time` | 下一次触发时间（调度扫描核心索引字段） |
| `task_cost_cpu` | 任务 CPU 开销（毫核），dispatch 时快照到 task，用于 worker 侧资源限制（Plan 212） |
| `task_cost_memory` | 任务内存开销（MB），同上 |
| `dispatch_mode` | 派发模式（single/partition/broadcast/bestFit），默认 single，控制 task builder 路由（Plan 213） |
| `partition_count` | 分片数量（int，默认 1），partition 模式下决定切分 worker 数（Plan 213） |
| `priority` | 任务优先级（int，默认 0，值越大优先级越高），影响 fetchWaitingTasks 排序（Plan 214） |
| `version` | 乐观锁 |

索引：

| 索引 | 说明 |
|------|------|
| `uk_nop_job_schedule_ns_group_name` | `namespace_id + group_id + job_name` 唯一 |
| `ix_nop_job_schedule_scan` | `schedule_status + partition_index + next_fire_time` |
| `ix_nop_job_schedule_executor` | `executor_ref` |

关键说明：
- `next_fire_time` 是调度扫描的核心索引字段。
- `active_fire_count` 用于快速判断 block strategy。
- `retry_policy_id` 只是桥接点，不代表 job 模块自己负责 retry。
- 不再保留旧模型中的 `max_failed_count`、`max_consec_failed_count`，避免和 `nop-retry` 重叠。

### 5.2 `nop_job_fire`

实体 `NopJobFire`。

| 关键字段 | 说明 |
|---------|------|
| `job_fire_id` | 主键 |
| `job_schedule_id` | 所属 schedule |
| `trigger_source` | `SCHEDULE/MANUAL/RECOVERY` |
| `fire_status` | `WAITING/DISPATCHING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELED` |
| `planner_instance_id` | 哪个 planner 产生 |
| `dispatch_instance_id` | 哪个 dispatcher 接管 |
| `scheduled_fire_time` | 本次理论触发时间 |
| `job_params_snapshot` | 任务参数快照 |
| `executor_snapshot` | 执行器快照 |
| `retry_policy_id` | retry 策略快照 |
| `retry_record_id` | 外部 retry 记录 ID，可空 |
| `partition_index` | 分区索引 |
| `version` | 乐观锁 |

索引：

| 索引 | 说明 |
|------|------|
| `uk_nop_job_fire_schedule_time_source` | `job_schedule_id + scheduled_fire_time + trigger_source` 防止重复计划 |
| `ix_nop_job_fire_dispatch_scan` | `fire_status + partition_index + scheduled_fire_time` |
| `ix_nop_job_fire_schedule` | `job_schedule_id + scheduled_fire_time` |
| `ix_nop_job_fire_retry` | `retry_record_id` |

### 5.3 `nop_job_task`

实体 `NopJobTask`。

| 关键字段 | 说明 |
|---------|------|
| `job_task_id` | 主键 |
| `job_fire_id` | 所属 fire |
| `task_no` | fire 内部序号，V1 固定从 1 开始 |
| `task_status` | `WAITING/CLAIMED/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELED` |
| `worker_instance_id` | 实际执行节点 |
| `task_payload` | 投递参数快照 |
| `result_payload` | 结果摘要，可空 |
| `partition_index` | 分区索引 |
| `cost_cpu` | dispatch 时从 `schedule.taskCostCpu` 快照，用于 worker 侧 reserved 资源聚合（Plan 212） |
| `cost_memory` | dispatch 时从 `schedule.taskCostMemory` 快照，同上 |
| `partition_range` | 分片模式下的 hash range（IntRangeBean.toString 格式），业务方解析后拼 SQL（Plan 213） |
| `priority` | dispatch 时从 `schedule.priority` 快照，影响 worker fetch 排序（Plan 214） |
| `version` | 乐观锁 |

索引：

| 索引 | 说明 |
|------|------|
| `uk_nop_job_task_fire_no` | `job_fire_id + task_no` |
| `ix_nop_job_task_run_scan` | `task_status + partition_index + create_time` |
| `ix_nop_job_task_fire` | `job_fire_id` |

### 5.4 旧表处理

| 旧表 | 处理建议 |
|------|----------|
| `nop_job_definition` | 迁移到 `nop_job_schedule` |
| `nop_job_instance` | 废弃，迁移为 `nop_job_fire` / `nop_job_task` |
| `nop_job_instance_his` | 废弃，新模型统一保留历史，无需单独历史表 |
| `nop_job_assignment` | 删除，改为动态分区分配 |

## 六、trigger 复用方案

### 6.1 复用目标

本次只复用 trigger 的**定义和时间计算逻辑**，不复用它的运行时状态机。

直接复用的语义：cron 表达式、fixed-rate / fixed-delay、`maxExecutionCount`、`minScheduleTime` / `maxScheduleTime`、`misfireThreshold`、`pauseCalendars` / `useDefaultCalendar`。

不再视为 trigger 语义的内容：`maxFailedCount`、`TriggerContextImpl` 里的运行状态迁移、`JobFinished` / `JobFailed` 的内存状态控制。

### 6.2 新的 trigger 计算接口

新增只读上下文 `ITriggerEvalContext`，把现有 `ITrigger` 的实现逐步改造为只依赖这个上下文，或者提供适配器。

### 6.3 fixed-delay 的特殊处理

`fixedDelay` 不能像 cron/fixed-rate 一样在"计划阶段"直接算出下一次触发时间，因为它依赖**上一次执行结束时间**。

- `CRON/FIXED_RATE/ONCE` 在 planner 创建 fire 时直接推进 `next_fire_time`。
- `FIXED_DELAY` 在 planner 创建 fire 后把 `next_fire_time` 置空。
- 当该 fire 最终结束时，由 completion processor 使用 `lastEndTime + repeatInterval` 重新计算下一次时间。

### 6.4 misfire 语义

沿用当前 trigger 的核心语义：
1. 如果落后时间超过 `misfireThreshold`，则跳过该次触发。
2. 跳过的是"计划机会"，不是"执行次数"。
3. 被 skip 的机会不会增加 `fireCount`。

## 七、核心流程设计

### 7.1 保存与启用 schedule

**保存流程**：
1. 校验 `job_name`、`executor_ref`、trigger 配置。
2. 把表单数据转换为 `TriggerSpec`。
3. 使用复用后的 `JobTriggerCalculator` 计算首次 `next_fire_time`。
4. 保存 `nop_job_schedule`。

**启用流程**：
1. `schedule_status` 切为 `ENABLED`。
2. 如果 `next_fire_time` 为空，重新根据当前时间计算。
3. 不直接创建 fire，只等待 planner 扫描。

### 7.2 Planner 扫描流程

planner 的职责是：**把到期的 schedule 转成 fire**。

仿照 `nop-retry` 的 scanner/store 结构：`fetchDueSchedules` → `tryLockSchedulesForPlan` → `planLockedSchedules`。

关键点：
1. `schedule -> fire` 的转换必须放在单事务中。
2. 插入 fire 时要依赖唯一索引防重。
3. schedule 的 version 必须参与更新，防止多节点重复计划。
4. `partition_index` 由 schedule 继承到 fire。

### 7.3 Dispatch 扫描流程

dispatcher 的职责是：**把 WAITING fire 变成 task，并交给执行器**。

V1 行为：一个 fire 只生成一个 task，一个 task 对应一次 `IJobInvoker.invokeAsync()`。

后续扩展：广播模式（一个 fire 生成多个 task）、分片模式（一个 fire 生成多个 shard task）。

### 7.4 执行完成流程

当 task 执行结束时：
1. 更新 `nop_job_task` 为成功、失败、超时或取消。
2. 聚合更新 `nop_job_fire` 最终状态。
3. 事务内更新 `nop_job_schedule` 的汇总字段。
4. 如果存在 `retry_policy_id`，把失败事件交给 `IJobRetryBridge`。

### 7.5 手工触发

手工触发不修改 trigger 定义，只创建一条 `trigger_source=MANUAL` 的 fire。

规则：
1. 手工触发默认使用当前 schedule 的参数快照。
2. 可允许传入覆盖参数，覆盖值只作用于当前 fire/task。
3. 手工触发同样受 block strategy 控制。
4. 手工触发不会推进定时 trigger 的 `next_fire_time`。
5. `ARCHIVED` / `COMPLETED` schedule 不允许 `triggerNow`。
6. `triggered_by` 优先从 `IServiceContext` 获取用户，取不到时回落为 `system`。

### 7.6 暂停、恢复、禁用、归档

| 操作 | 状态转换 | 关键行为 |
|------|---------|---------|
| 暂停 | `ENABLED → PAUSED` | 不创建新 fire，已运行 fire/task 不自动取消 |
| 恢复 | `PAUSED → ENABLED` | 重新计算 `next_fire_time` |
| 禁用 | `ENABLED/PAUSED → DISABLED` | 不再参与 planner 扫描 |
| 启用 | `DISABLED → ENABLED` | `next_fire_time` 为空时重新计算 |
| 归档 | 任何 → `ARCHIVED` | 清空 `next_fire_time`，不再允许恢复，仅保留查询 |

### 7.7 超时与取消

**超时**：`JobTimeoutChecker` 扫描 RUNNING 且超过 `timeout_seconds` 的 task → 调用 `IJobInvoker.cancelAsync()` → 标记 TIMEOUT。

**手工取消**（已落地）：
1. `cancelFire(fireId)` 只允许取消活动态 fire：`WAITING / DISPATCHING / RUNNING`。
2. 取消时原子更新 fire/task/schedule 状态。
3. scheduled `FIXED_DELAY` fire 取消后会补算 `schedule.next_fire_time`。
4. manual fire 取消不会推进 schedule 的定时 trigger 游标。

## 八、API 与 Service 层

### 8.1 管理命令

提供领域命令而非 CRUD：

1. `enableSchedule(scheduleId)`
2. `disableSchedule(scheduleId)`
3. `pauseSchedule(scheduleId)`
4. `resumeSchedule(scheduleId)`
5. `triggerNow(scheduleId, overrideParams)`
6. `cancelFire(fireId)`
7. `rerunFire(fireId)` — 基于原 fire 的快照创建新 fire（`trigger_source=RECOVERY`）
8. `archiveSchedule(scheduleId)`

### 8.2 在 Nop 中的落点

1. ORM：定义 `NopJobSchedule`、`NopJobFire`、`NopJobTask`
2. Service：`CrudBizModel + Processor`
3. 复杂流程：planner、dispatcher、completion、timeout checker 放在 engine/processor 中

不应把这些复杂流程塞进 `BizModel`。

## 九、与已有设计的关系

| 主题 | 文档 |
|------|------|
| 设计原则、non-goals、约束、不变量 | `00-vision.md` |
| Invoker 路由体系 | `invoker-design.md` |
| 阻塞策略 | `block-strategy-design.md` |
| 集群 HA 与动态分区 | `cluster-ha-design.md` |
| retry 桥接 | `retry-integration-design.md` |
| Metrics 命名和埋点规范 | `metrics-design.md` |
| 限流设计 | `rate-limiting-design.md` |
