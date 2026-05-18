# nop-job 重写设计文档

**日期**：2026-04-04  
**范围**：`nop-job/` 全模块重写方案  
**目标**：把当前以内存调度器为中心的 `nop-job` 重写为以数据库为中心的调度执行系统，同时复用现有 trigger 定义逻辑和 cron 表达式实现，并与 `nop-retry` 清晰分层。

---

## 一、设计结论

`nop-job` 应重写为一个**数据库驱动的 schedule/fire/task 三层模型**：

1. `JobSchedule` 负责保存调度定义和下一次触发游标。
2. `JobFire` 负责表示一次具体的触发批次。
3. `JobTask` 负责表示一次具体执行投递，哪怕 V1 只有单任务模式，也保留这一层。

这次重写的核心决策如下：

1. **彻底放弃当前以内存 `DefaultJobScheduler` 为主、数据库为辅的运行模型**。
2. **保留 trigger 定义模型、calendar 逻辑、`CronExpression` 实现**，但把它们降格为纯计算组件。
3. **`nop-job` 不再内建重试编排逻辑**，失败重试交由 `nop-retry`，`nop-job` 只保留失败事件与桥接点。
4. **不复用任何外部项目的代码实现**，只吸收业内成熟调度系统常见的领域拆分方式：命名空间/分组、任务定义与执行批次分离、执行器与调度记录分离。
5. **删除当前 `definition + instance + instance_his + assignment` 的旧模型**，替换为新的 `schedule + fire + task` 模型。

---

## 二、现状问题

当前 `nop-job` 的根本问题不是“没有 ORM 文件”，而是**运行时并不真正以数据库为权威状态源**。

### 2.1 当前实现的具体问题

| 问题 | 代码证据 | 影响 |
|------|----------|------|
| 持久化接口未完成 | `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/DaoJobSchedulerStore.java` 中 `loadJobDetail()` 直接返回 `null`，`saveInstanceState()` 为空 | 数据库状态无法支撑真实调度 |
| 调度中心以内存为主 | `nop-job/nop-job-core/.../DefaultJobScheduler.java` 使用 `ConcurrentHashMap<String, JobExecution>` 保存运行态 | 重启恢复、主从切换、集群一致性都不可靠 |
| 运行态持久化链路空转 | `TriggerContextImpl.onChange()` 会调用 `jobStore.saveInstanceState(this)`，但 store 为空实现 | 表面上支持持久化，实际上无效 |
| 运行模型混乱 | `NopJobInstance` 同时承载“待调度状态”和“执行状态”；`NopJobInstanceHis` 只是复制一份历史表 | 定义层、触发层、执行层语义没有分开 |
| 集群分区设计不完整 | `DaoJobSchedulerStore.addPartitionFilter()` 只有在 `enableCluster=false` 时才读 `NopJobAssignment`；集群模式下没有形成稳定的分区隔离 | 多节点扫描时容易重复调度 |
| 调度分发器不存在 | `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/queue/DaoJobPlanDispatcher.java` 是空类 | 没有完整的“计划 -> 分发 -> 执行”链路 |
| Service 层只有 CRUD | `NopJobDefinitionBizModel`、`NopJobInstanceBizModel` 等仅继承 `CrudBizModel`，没有领域方法 | 外部只能改表，不能管理调度生命周期 |
| retry 与 job 边界混乱 | `TriggerSpec.maxFailedCount` 与 `TriggerExecutorImpl.onException()` 把失败终止逻辑写进 job 触发器 | 与 `nop-retry` 职责重叠 |

### 2.2 现有模型为什么不适合继续修补

当前模型的问题不是“少几个方法”，而是方向错了：

1. `JobDefinition` 和 `JobInstance` 没有形成清晰的控制面/运行面分层。
2. 调度游标存在内存中的 `TriggerContextImpl`，而不是数据库中的 schedule 记录。
3. `JobInstance` 既像“下一次待执行记录”，又像“当前执行状态快照”，导致语义不断打架。
4. 一旦需要集群安全、超时恢复、手工触发、取消、失败追踪，就会继续把更多状态堆进一个类里。

因此本次不建议在现有表结构上继续补丁式演化，而应直接切到新的领域模型。

---

## 三、设计原则

### 3.1 数据库是权威状态源

运行时任何关键状态都必须能从数据库恢复：

1. 哪些 schedule 处于启用状态。
2. 每个 schedule 的下一次触发时间是什么。
3. 哪些 fire 正在等待分发或执行。
4. 哪些 task 正在运行、失败、超时、取消。

### 3.2 trigger 只负责“算时间”，不负责“保存状态”

当前 trigger 包里的 `CronTrigger`、`PeriodicTrigger`、`PauseCalendarTrigger`、`HandleMisfireTrigger` 等，适合作为**纯调度时间计算器**复用。

不再复用的部分是：

1. `TriggerExecutorImpl`
2. `TriggerContextImpl`
3. `DefaultJobScheduler`
4. `JobExecution`
5. `IJobScheduleStore` 当前语义

### 3.3 job 和 retry 必须分层

`nop-job` 负责：

1. 什么时候触发。
2. 触发后生成什么 fire/task。
3. 执行结果如何落库。
4. 如何暂停、恢复、取消、超时回收。

`nop-retry` 负责：

1. 失败后的退避与再次执行。
2. 重试记录。
3. 死信。
4. 回调。

因此新设计中**不再把 `maxRetryTimes`、`retryInterval` 一类字段塞进 job 定义表**。

### 3.4 先做最小正确架构，再扩展高级模式

V1 以正确性优先：

1. 先把 `SINGLE` 执行模式做好。
2. 数据模型预留 `task` 层，为后续广播、分片扩展留口。
3. 先不引入 workflow。
4. 先不引入多语言执行器体系。

---

## 四、领域参考与边界约束

### 4.1 参考的功能拆分

借鉴的是**领域拆分**，不是代码实现：

1. 命名空间 + 分组的逻辑隔离思路。
2. 任务定义与执行批次分离。
3. fire/batch 与 task 进一步分离。
4. 任务执行器独立于调度定义。
5. 管理面和运行面分层。

### 4.2 明确不采用的内容

1. 不照搬任何外部项目的实现代码。
2. 不照搬它的大量 mapper/handler/actor 组织方式。
3. 不把 retry 配置继续内嵌到 job 表里。
4. 不把 workflow 和 job 一起进入 V1。
5. 不复制它的高复杂度状态机与大量中间对象。

### 4.3 本方案的主动约束

| 主题 | 约束 | 新 nop-job 方案 |
|------|------|-----------------|
| retry 配置位置 | 不与独立 retry 系统重复建模 | 通过 `retryPolicyId` 或失败桥接点与 `nop-retry` 集成 |
| 执行实现 | 不引入另一套复杂执行框架 | 复用 Nop 现有 `IJobInvoker` SPI |
| 集群扫描 | 优先采用项目内已验证模式 | 复用 `nop-retry` 的扫描/分区/锁定模式 |
| 功能范围 | 控制首版复杂度 | V1 仅重写 job |

---

## 五、哪些现有代码可以复用

### 5.1 建议复用

以下代码值得直接复用或轻度重构后复用：

| 组件 | 位置 | 复用方式 |
|------|------|----------|
| `CronExpression` / `ICronExpression` | `nop-job-core/.../utils/` | 直接复用 |
| `CalendarBuilder` 与 calendar 实现 | `nop-job-core/.../calendar/` | 直接复用 |
| `TriggerSpec` / `ITriggerSpec` 字段语义 | `nop-job-api/.../spec/` | 作为新 schedule 的 trigger DTO，但 `maxFailedCount` 在新架构中废弃不用 |
| `CronTrigger` / `PeriodicTrigger` / `LimitCountTrigger` / `LimitTimeTrigger` / `PauseCalendarTrigger` / `HandleMisfireTrigger` | `nop-job-core/.../trigger/` | 改造为纯计算器链 |
| `IJobInvoker` / `JobFireResult` | `nop-job-api/.../execution/` | 尽量保留，降低业务 job 迁移成本 |

### 5.2 建议重构后复用

1. `TriggerBuilder` 可以保留，但要把它依赖的上下文接口缩成只读的 `TriggerEvalContext`。
2. `JobSpec` 可以保留一部分字段，但需要去掉当前“内存调度器专用”的假设。
3. `JobDaoHelper` 的“Definition -> TriggerSpec”映射逻辑可以借鉴，但不能继续围绕旧表。

### 5.3 建议废弃

以下代码不建议继续沿用：

1. `DefaultJobScheduler`
2. `IJobScheduler` 当前语义
3. `IJobScheduleStore` 当前语义
4. `TriggerContextImpl`
5. `TriggerExecutorImpl`
6. `JobExecution`
7. `DaoJobSchedulerStore`
8. `NopJobAssignment`
9. `NopJobInstanceHis`

---

## 六、总体架构

### 6.1 新架构总览

```text
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

### 6.2 运行时分层

1. **控制面**：`JobSchedule`
2. **批次层**：`JobFire`
3. **投递层**：`JobTask`
4. **执行层**：`IJobInvoker`
5. **重试桥接层**：`IJobRetryBridge`，默认 no-op

### 6.3 推荐模块拆分

| 模块 | 职责 | 说明 |
|------|------|------|
| `nop-job-api` | DTO、SPI、公共常量 | 保留并整理 |
| `nop-job-core` | trigger/calendars/cron 纯计算库 | 缩小职责 |
| `nop-job-engine` | planner、dispatcher、timeout、cancel | 新增模块，承载运行时 |
| `nop-job-dao` | ORM、store 实现 | 按新表模型重写 |
| `nop-job-service` | BizModel、管理命令、查询接口 | 重写 |
| `nop-job-web` | xmeta、权限、页面 | 适配新实体 |
| `nop-job-retry-adapter` | 对接 `nop-retry` 的可选桥接模块 | 可选新增 |

如果暂时不想新增模块，`nop-job-engine` 也可以先落在 `nop-job-core` 的 `engine` 包下，但目标边界不变：**trigger 计算逻辑和运行时调度逻辑必须分开**。

---

## 七、领域模型

### 7.1 JobSchedule

`JobSchedule` 是调度定义聚合根，负责：

1. 保存执行器信息与参数快照来源。
2. 保存 trigger 定义。
3. 保存下一次触发游标 `nextFireTime`。
4. 保存运行汇总字段，例如 `fireCount`、`activeFireCount`、`lastEndTime`。

它不负责：

1. 保存单次执行结果详情。
2. 保存多次 task 投递记录。
3. 保存 retry 过程。

### 7.2 JobFire

`JobFire` 表示一次触发批次，来源可能是：

1. 定时调度。
2. 手工触发。
3. 后续扩展的恢复性触发。

它是调度与执行之间的边界对象。

### 7.3 JobTask

`JobTask` 表示一次具体投递执行。

即使 V1 只有单任务模式，仍保留这一层，原因如下：

1. 后续做广播/分片时不需要再改主模型。
2. 可以单独表达分发失败、执行失败、超时、取消。
3. `JobFire` 可以只表达批次最终结果，不被执行细节污染。

---

## 八、数据模型设计

## 8.1 `nop_job_schedule`

建议新建实体 `NopJobSchedule`，表名 `nop_job_schedule`。

### 关键字段

| 字段 | 说明 |
|------|------|
| `job_schedule_id` | 主键 |
| `namespace_id` | 命名空间，和 `nop-retry` 保持一致 |
| `group_id` | 逻辑分组，和 `nop-retry` 保持一致 |
| `job_name` | 作业唯一名，在 `namespace_id + group_id` 内唯一 |
| `display_name` | 显示名 |
| `description` | 描述 |
| `schedule_status` | `DISABLED/ENABLED/PAUSED/COMPLETED/ARCHIVED` |
| `executor_kind` | 执行器类型，例如 `BEAN`、`RPC`，V1 先支持 `BEAN` |
| `executor_ref` | 执行器引用，V1 对应现有 `jobInvoker` |
| `job_params` | JSON 参数 |
| `trigger_type` | `CRON/FIXED_RATE/FIXED_DELAY/ONCE` |
| `cron_expr` | cron 表达式 |
| `repeat_interval_ms` | 固定间隔 |
| `max_execution_count` | 最大执行次数 |
| `min_schedule_time` | 最早触发时间 |
| `max_schedule_time` | 最晚触发时间 |
| `misfire_threshold_ms` | misfire 阈值 |
| `use_default_calendar` | 是否叠加系统 calendar |
| `pause_calendar_spec` | calendar JSON |
| `block_strategy` | `DISCARD/OVERLAY/PARALLEL` |
| `timeout_seconds` | 执行超时时间 |
| `retry_policy_id` | 可选，对接 `nop_retry_policy.sid` |
| `partition_index` | 调度分区 |
| `fire_count` | 已实际创建的 fire 数 |
| `active_fire_count` | 当前未结束 fire 数 |
| `last_fire_time` | 上次计划时间 |
| `last_end_time` | 上次结束时间 |
| `next_fire_time` | 下一次触发时间 |
| `last_fire_status` | 最近一次 fire 的最终状态 |
| `version` | 乐观锁 |
| 审计字段 | `created_by/create_time/updated_by/update_time` |

### 索引建议

| 索引 | 说明 |
|------|------|
| `uk_nop_job_schedule_ns_group_name` | `namespace_id + group_id + job_name` 唯一 |
| `ix_nop_job_schedule_scan` | `schedule_status + partition_index + next_fire_time` |
| `ix_nop_job_schedule_executor` | `executor_ref` |

### 关键说明

1. `next_fire_time` 是调度扫描的核心索引字段。
2. `active_fire_count` 用于快速判断 block strategy。
3. `retry_policy_id` 只是桥接点，不代表 job 模块自己负责 retry。
4. 不再保留旧模型中的 `max_failed_count`、`max_consec_failed_count`，避免和 `nop-retry` 重叠。

## 8.2 `nop_job_fire`

建议新建实体 `NopJobFire`，表名 `nop_job_fire`。

### 关键字段

| 字段 | 说明 |
|------|------|
| `job_fire_id` | 主键 |
| `job_schedule_id` | 所属 schedule |
| `namespace_id` | 命名空间 |
| `group_id` | 分组 |
| `job_name` | 冗余快照，便于查询 |
| `trigger_source` | `SCHEDULE/MANUAL/RECOVERY` |
| `scheduled_fire_time` | 本次理论触发时间 |
| `fire_status` | `WAITING/DISPATCHING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELED` |
| `planner_instance_id` | 哪个 planner 产生 |
| `dispatch_instance_id` | 哪个 dispatcher 接管 |
| `start_time` | 实际开始执行时间 |
| `end_time` | 实际结束时间 |
| `duration_ms` | 执行时长 |
| `job_params_snapshot` | 任务参数快照 |
| `executor_snapshot` | 执行器快照 |
| `retry_policy_id` | retry 策略快照 |
| `retry_record_id` | 外部 retry 记录 ID，可空 |
| `error_code` | 错误码 |
| `error_message` | 错误消息 |
| `triggered_by` | 手工触发人或系统来源 |
| `partition_index` | 分区索引 |
| `version` | 乐观锁 |
| 审计字段 | 标准审计字段 |

### 索引建议

| 索引 | 说明 |
|------|------|
| `uk_nop_job_fire_schedule_time_source` | `job_schedule_id + scheduled_fire_time + trigger_source` 防止重复计划 |
| `ix_nop_job_fire_dispatch_scan` | `fire_status + partition_index + scheduled_fire_time` |
| `ix_nop_job_fire_schedule` | `job_schedule_id + scheduled_fire_time` |
| `ix_nop_job_fire_retry` | `retry_record_id` |

## 8.3 `nop_job_task`

建议新建实体 `NopJobTask`，表名 `nop_job_task`。

### 关键字段

| 字段 | 说明 |
|------|------|
| `job_task_id` | 主键 |
| `job_fire_id` | 所属 fire |
| `task_no` | fire 内部序号，V1 固定从 1 开始 |
| `task_status` | `WAITING/CLAIMED/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELED` |
| `worker_instance_id` | 实际执行节点 |
| `worker_address` | 地址快照 |
| `task_payload` | 投递参数快照 |
| `start_time` | 开始时间 |
| `end_time` | 结束时间 |
| `duration_ms` | 时长 |
| `result_payload` | 结果摘要，可空 |
| `error_code` | 错误码 |
| `error_message` | 错误消息 |
| `partition_index` | 分区索引 |
| `version` | 乐观锁 |
| 审计字段 | 标准审计字段 |

### 索引建议

| 索引 | 说明 |
|------|------|
| `uk_nop_job_task_fire_no` | `job_fire_id + task_no` |
| `ix_nop_job_task_run_scan` | `task_status + partition_index + create_time` |
| `ix_nop_job_task_fire` | `job_fire_id` |

## 8.4 不再保留的旧表

| 旧表 | 处理建议 |
|------|----------|
| `nop_job_definition` | 迁移到 `nop_job_schedule` |
| `nop_job_instance` | 废弃，迁移为 `nop_job_fire` / `nop_job_task` |
| `nop_job_instance_his` | 废弃，新模型统一保留历史，无需单独历史表 |
| `nop_job_assignment` | 删除，改为动态分区分配 |

---

## 九、trigger 复用方案

## 9.1 复用目标

本次只复用 trigger 的**定义和时间计算逻辑**，不复用它的运行时状态机。

### 直接复用的语义

1. cron 表达式。
2. fixed-rate / fixed-delay。
3. `maxExecutionCount`。
4. `minScheduleTime` / `maxScheduleTime`。
5. `misfireThreshold`。
6. `pauseCalendars` / `useDefaultCalendar`。

### 不再视为 trigger 语义的内容

1. `maxFailedCount`
2. 当前 `TriggerContextImpl` 里的运行状态迁移
3. `JobFinished` / `JobFailed` 的内存状态控制

如果为了兼容管理端或旧接口暂时继续保留 `TriggerSpec.maxFailedCount` 字段，也只允许作为过渡期字段存在，在新引擎中必须忽略，不能再参与调度终止判断。

## 9.2 新的 trigger 计算接口

建议新增只读上下文：

```java
public interface ITriggerEvalContext {
    long getFireCount();
    long getLastScheduledTime();
    long getLastEndTime();
    long getMinScheduleTime();
    long getMaxScheduleTime();
    long getMaxExecutionCount();
    boolean isScheduleCompleted();
}
```

然后把现有 `ITrigger` 的实现逐步改造为只依赖这个上下文，或者提供适配器，把 `ITriggerEvalContext` 包装成当前 `ITriggerContext` 需要的只读子集。

## 9.3 fixed-delay 的特殊处理

`fixedDelay` 不能像 cron/fixed-rate 一样在“计划阶段”直接算出下一次触发时间，因为它依赖**上一次执行结束时间**。

因此新设计中：

1. `CRON/FIXED_RATE/ONCE` 在 planner 创建 fire 时直接推进 `next_fire_time`。
2. `FIXED_DELAY` 在 planner 创建 fire 后把 `next_fire_time` 置空。
3. 当该 fire 最终结束时，由 completion processor 使用 `lastEndTime + repeatInterval` 重新计算下一次时间。

这是复用当前 `PeriodicTrigger(fixedDelay=true)` 语义时必须显式处理的差异点。

## 9.4 misfire 语义

沿用当前 trigger 的核心语义：

1. 如果落后时间超过 `misfireThreshold`，则跳过该次触发。
2. 跳过的是“计划机会”，不是“执行次数”。
3. 被 skip 的机会不会增加 `fireCount`。

---

## 十、核心流程设计

## 10.1 保存与启用 schedule

### 保存流程

1. 校验 `job_name`、`executor_ref`、trigger 配置。
2. 把表单数据转换为 `TriggerSpec`。
3. 使用复用后的 `JobTriggerCalculator` 计算首次 `next_fire_time`。
4. 保存 `nop_job_schedule`。

### 启用流程

1. `schedule_status` 切为 `ENABLED`。
2. 如果 `next_fire_time` 为空，重新根据当前时间计算。
3. 不直接创建 fire，只等待 planner 扫描。

## 10.2 Planner 扫描流程

planner 的职责是：**把到期的 schedule 转成 fire**。

建议完全仿照 `nop-retry` 的 scanner/store 结构：

1. `fetchDueSchedules(limit, partitions)`
2. `tryLockSchedulesForPlan(schedules, planningTimeoutMs)`
3. `planLockedSchedules(lockedSchedules)`

### planner 伪代码

```text
loop:
  dueSchedules = store.fetchDueSchedules(batchSize, partitions)
  lockedSchedules = store.tryLockSchedulesForPlan(dueSchedules)
  for schedule in lockedSchedules:
    if schedule.status != ENABLED:
      continue

    if schedule.activeFireCount > 0 and schedule.blockStrategy == DISCARD:
      schedule.nextFireTime = calculator.nextAfterSkip(schedule)
      store.updateSchedule(schedule)
      continue

    if schedule.activeFireCount > 0 and schedule.blockStrategy == OVERLAY:
      cancelActiveFires(schedule)

    fire = createFire(schedule)
    store.saveFire(fire)
    store.updateScheduleAfterPlan(schedule, fire)
```

### 关键点

1. `schedule -> fire` 的转换必须放在单事务中。
2. 插入 fire 时要依赖唯一索引防重。
3. schedule 的 version 必须参与更新，防止多节点重复计划。
4. `partition_index` 由 schedule 继承到 fire。

## 10.3 Dispatch 扫描流程

dispatcher 的职责是：**把 WAITING fire 变成 task，并交给执行器**。

### V1 行为

1. 一个 fire 只生成一个 task。
2. 一个 task 对应一次 `IJobInvoker.invokeAsync()`。

### 后续扩展

1. 广播模式：一个 fire 生成多个 task。
2. 分片模式：一个 fire 生成多个 shard task。

## 10.4 执行完成流程

当 task 执行结束时：

1. 更新 `nop_job_task` 为成功、失败、超时或取消。
2. 聚合更新 `nop_job_fire` 最终状态。
3. 事务内更新 `nop_job_schedule` 的汇总字段：
   - `active_fire_count`
   - `last_end_time`
   - `last_fire_status`
   - 如果是 `FIXED_DELAY`，此时重新计算 `next_fire_time`
4. 如果存在 `retry_policy_id`，把失败事件交给 `IJobRetryBridge`

## 10.5 手工触发

手工触发不修改 trigger 定义，只创建一条 `trigger_source=MANUAL` 的 fire。

### 规则

1. 手工触发默认使用当前 schedule 的参数快照。
2. 可允许传入覆盖参数，覆盖值只作用于当前 fire/task。
3. 手工触发同样受 block strategy 控制。
4. 手工触发不会推进定时 trigger 的 `next_fire_time`。
5. 当前实现中：
   - `ARCHIVED` / `COMPLETED` schedule 不允许 `triggerNow`
   - `DISABLED` / `PAUSED` / `ENABLED` schedule 允许 `triggerNow`
   - `triggerNow` 会创建 `WAITING` 的 manual fire，并更新 `fire_count` / `active_fire_count`
   - `triggered_by` 优先从 `IServiceContext` 获取用户，取不到时回落为 `system`

## 10.6 暂停、恢复、禁用、归档

### 暂停

1. `schedule_status = PAUSED`
2. 不创建新的 fire
3. 已运行 fire/task 不自动取消
4. 当前实现中 `pauseSchedule` 仅允许 `ENABLED -> PAUSED`
5. 当前实现中对已 `PAUSED` 的 schedule 再次执行 `pauseSchedule` 为幂等返回

### 恢复

1. `schedule_status = ENABLED`
2. 重新计算 `next_fire_time`
3. 当前实现中 `resumeSchedule` 仅允许 `PAUSED -> ENABLED`

### 禁用

1. `schedule_status = DISABLED`
2. 不再参与 planner 扫描
3. 当前实现中 `disableSchedule` 仅允许从 `ENABLED / PAUSED -> DISABLED`
4. 当前实现中对已 `DISABLED` 的 schedule 再次执行 `disableSchedule` 为幂等返回

### 启用

1. `schedule_status = ENABLED`
2. 当前实现中 `enableSchedule` 仅允许 `DISABLED -> ENABLED`
3. 当 `next_fire_time` 为空时会重新计算

### 归档

1. `schedule_status = ARCHIVED`
2. 不再允许恢复
3. 仅保留查询能力
4. 当前实现中 `archiveSchedule` 允许 `ENABLED / DISABLED / PAUSED / COMPLETED -> ARCHIVED`
5. 当前实现中对已 `ARCHIVED` 的 schedule 再次执行 `archiveSchedule` 为幂等返回
6. 当前实现中归档时会清空 `next_fire_time`

## 10.7 超时与取消

建议单独提供 `JobTimeoutChecker`：

1. 扫描 `RUNNING` 且超过 `timeout_seconds` 的 task。
2. 调用 `IJobInvoker.cancelAsync()`。
3. 超时后将 task/fire 标记为 `TIMEOUT`。
4. 对 fixed-delay schedule，此次结束仍要进入 completion processor，才能恢复下一次调度。

当前已落地的手工取消语义：

1. `cancelFire(fireId)` 只允许取消活动态 fire：`WAITING / DISPATCHING / RUNNING`。
2. 取消时会原子更新：
   - `fire.fire_status = CANCELED`
   - 未结束 `task.task_status = CANCELED`
   - `schedule.active_fire_count` 递减
   - `schedule.last_end_time = cancelTime`
   - `schedule.last_fire_status = CANCELED`
3. 如果是 scheduled `FIXED_DELAY` fire，取消后会立即补算 `schedule.next_fire_time`。
4. 如果是 manual fire，取消不会推进 schedule 的定时 trigger 游标。
5. `RUNNING` fire 若其下 task 已全部进入终态，则不再允许手工取消。
6. 为避免竞态：
   - dispatcher 只会把仍处于 `DISPATCHING` 的 fire 转成 task/running
   - worker 只会执行仍处于 `CLAIMED` 的 task

---

## 十一、与 nop-retry 的集成设计

> **详细设计已迁移到**：`ai-dev/design/nop-job/retry-integration-design.md`
> 本节保留分层原则概述，接口签名、调用时机、优先级策略、回调约定、模块依赖详见上述文档。

## 11.1 分层原则

`nop-job` 不直接实现：

1. 重试次数控制。
2. 退避算法。
3. 死信。
4. 回调。

这些能力全部交给 `nop-retry`。

## 11.2 桥接方式概述

通过 `IJobRetryBridge` 接口（定义在 nop-job-api）桥接：

- 默认实现 `NoOpJobRetryBridge` 返回 null
- 可选模块 `nop-job-retry-adapter` 提供基于 `IRetryEngine` 的实现
- `retryPolicyId` 优先级：`fire.retryPolicyId` > `schedule.retryPolicyId` > 不触发重试
- 调用时机：`JobCompletionProcessorImpl` 在 fire 失败时调用桥接

## 11.3 为什么不用 job 内建 retry 字段

如果把以下字段继续放进 job 表：

1. `maxRetryTimes`
2. `retryInterval`
3. `backoffStrategy`
4. `deadLetter`

就会和 `nop-retry` 形成重复系统。

## 11.4 `retryPolicyId` 的定位

`retryPolicyId` 只是**声明式桥接点**，表示：

1. 该 job 失败后应该交给哪个 retry policy。
2. `nop-job` 自己不解释 policy 细节。
3. `nop-job` 只负责抛失败事件并保存 `retry_record_id`。

---

## 十二、集群与分区设计

> **详细设计已迁移到**：`ai-dev/design/nop-job/cluster-ha-design.md`
> 本节保留分区字段定义和扫描策略，HA/选主/抖动防护等详见上述文档。

## 12.1 复用 nop-retry 的分区模式

新 `nop-job` 应复用 `nop-retry` 已验证的模式：

1. scanner 周期扫描。
2. 数据库批量获取候选记录。
3. 乐观锁批量 claim。
4. `IDiscoveryClient + PartitionAssignHelper` 动态计算分区（集成 nop-cluster 基础设施）。

不再保留 `nop_job_assignment` 这类静态分配表。分区分配不依赖 Leader 节点，每个 Scanner 通过 `PartitionAssignHelper.getMyRange(sortedServers, myInstanceId)` 独立计算。

## 12.2 分区字段

`partition_index` 建议在创建 schedule 时就写入：

```text
partition_index = hash(namespace_id + ':' + group_id + ':' + job_name) % partition_count
```

这样：

1. schedule 扫描可以按分区过滤。
2. fire/task 继承该字段，便于同分区处理。
3. 与 `nop-retry` 的扫描思路一致。

## 12.3 Planner 与 Dispatcher 是否同分区

推荐同分区：

1. planner 扫 schedule 的 `partition_index`
2. dispatcher 扫 fire/task 的 `partition_index`
3. 同一分区尽量由同一组节点消费，降低乱序和热点

## 12.4 集群 HA 与动态分区

参见 `cluster-ha-design.md`，要点：

1. **不需要选主**：集成 `IDiscoveryClient` + `PartitionAssignHelper`，每节点独立计算 partition（纯函数）
2. **防抖**：stabilization window（30s）+ 乐观锁兜底（partition 重叠不会导致数据错误）
3. **接管延迟**：服务发现超时(~30s) + stableWindowMs(30s) ≈ 60s
4. **无单点**：不依赖 Leader，所有节点对等计算

---

## 十三、API 与 Service 层设计

## 13.1 管理命令

建议提供以下领域命令，而不是只暴露 CRUD：

1. `enableSchedule(scheduleId)`
2. `disableSchedule(scheduleId)`
3. `pauseSchedule(scheduleId)`
4. `resumeSchedule(scheduleId)`
5. `triggerNow(scheduleId, overrideParams)`
6. `cancelFire(fireId)`
7. `rerunFire(fireId)`
8. `archiveSchedule(scheduleId)`

当前已落地：

1. `enableSchedule(scheduleId)`
2. `disableSchedule(scheduleId)`
3. `pauseSchedule(scheduleId)`
4. `resumeSchedule(scheduleId)`
5. `triggerNow(scheduleId, overrideParams)`
6. `cancelFire(fireId)`
7. `rerunFire(fireId)`
8. `archiveSchedule(scheduleId)`

`rerunFire` 当前实现语义：

1. 仅允许对终态 fire 执行：`SUCCESS / FAILED / TIMEOUT / CANCELED`
2. 基于原 fire 的 `jobParamsSnapshot` 与 `executorSnapshot` 创建新 fire，而不是回到当前 schedule 定义重新取值
3. 新 fire 的 `trigger_source = RECOVERY`
4. 新 fire 不推进 schedule 的定时 trigger 游标
5. 新 fire 仍复用当前 schedule 的 block strategy
6. `ARCHIVED / COMPLETED` schedule 不允许执行 `rerunFire`

## 13.2 查询接口

建议提供以下查询：

1. `getScheduleDetail(scheduleId)`
2. `findSchedulePage(query)`
3. `findFirePage(scheduleId, query)`
4. `findTaskPage(fireId, query)`
5. `getRuntimeSummary(scheduleId)`

## 13.3 在 Nop 中的落点

按 Nop 的推荐分层：

1. ORM：定义 `NopJobSchedule`、`NopJobFire`、`NopJobTask`
2. Service：`CrudBizModel + Processor`
3. 复杂流程：planner、dispatcher、completion、timeout checker 放在 engine/processor 中

不应把这些复杂流程塞进 `BizModel`。

## 13.4 当前管理端适配进展

当前已完成第一批 Web/Meta 适配：

1. 管理导航已优先暴露新实体：`NopJobSchedule`、`NopJobFire`、`NopJobTask`。
2. 旧的 `definition / instance / instance_his / assignment` 页面入口已从手写导航层移除。
3. `NopJobSchedule` 列表页已接入领域命令按钮：
   - `triggerNow`
   - `enableSchedule`
   - `disableSchedule`
   - `pauseSchedule`
   - `resumeSchedule`
   - `archiveSchedule`
4. `NopJobFire` 列表页已接入领域命令按钮：
   - `cancelFire`
   - `rerunFire`
5. `NopJobFire` / `NopJobTask` 作为运行态实体，页面已去掉新增、编辑、删除等不合理 CRUD 操作入口。
6. 为页面动作新增了中英文文案与独立功能点权限：
   - `NopJobSchedule:triggerNow`
   - `NopJobSchedule:enableSchedule`
   - `NopJobSchedule:disableSchedule`
   - `NopJobSchedule:pauseSchedule`
   - `NopJobSchedule:resumeSchedule`
   - `NopJobSchedule:archiveSchedule`
   - `NopJobFire:cancelFire`
   - `NopJobFire:rerunFire`
7. 已补基础下钻：
   - 从 schedule 行打开关联 fire 列表
   - 从 fire 行打开关联 task 列表
8. 已补基础 runtime summary：
   - schedule runtime summary：概览 + 关联 fire
   - fire runtime summary：概览 + 关联 task
9. 已补 task 侧最小闭环：
   - task runtime summary：概览 + 所属 fire 摘要
   - fire 可直接上钻查看所属 schedule
10. 已补运行态详情可读性优化：
    - 对 JSON/长文本字段优先使用长文本只读控件展示
    - 当前已覆盖 `jobParams`、`jobParamsSnapshot`、`executorSnapshot`、`taskPayload`、`resultPayload`、`errorMessage` 等高频阅读字段

---

## 十四、迁移方案

## 14.1 迁移总体策略

推荐采用**新表并行 + 一次切换**，不建议在旧表上直接做兼容演化。

### 原因

1. 旧表语义本身已经不清晰。
2. 旧运行态并不可信，继续兼容只会把脏语义带入新系统。
3. 新模型和旧模型不是字段级增量关系，而是聚合边界变化。

## 14.2 数据迁移映射

| 旧实体 | 新实体 | 迁移规则 |
|--------|--------|----------|
| `NopJobDefinition` | `NopJobSchedule` | 迁移定义字段与 trigger 字段 |
| `NopJobInstance` | 不直接迁移为单一实体 | 运行中状态不可信，切换窗口内清空或人工冻结 |
| `NopJobInstanceHis` | `NopJobFire`（可选） | 只迁历史，不迁运行态 |
| `NopJobAssignment` | 删除 | 改用动态分区 |

## 14.3 切换步骤

1. 新建 ORM、SQL、xmeta、BizModel。
2. 编写旧 `definition -> schedule` 的迁移脚本。
3. 上线只读管理页验证新表内容。
4. 停掉旧 scheduler。
5. 执行迁移脚本。
6. 启动新 planner/dispatcher。
7. 观察 fire/task 正常落库后，旧表进入只读保留期。

---

## 十五、测试方案

## 15.1 单元测试

必须保留并扩展当前 trigger 相关测试，重点覆盖：

1. cron
2. fixed-rate
3. fixed-delay
4. pause calendars
5. misfire
6. maxExecutionCount

## 15.2 Store/事务测试

需要新增：

1. `fetchDueSchedules + tryLockSchedulesForPlan` 并发测试
2. `fetchWaitingFires + tryLockFiresForDispatch` 并发测试
3. 唯一索引防重测试
4. version 乐观锁测试

## 15.3 集成测试

至少覆盖以下场景：

1. 创建 schedule 后自动生成 fire。
2. 手工触发不影响 `next_fire_time`。
3. fixed-delay 在 fire 完成后才生成下一次调度。
4. `DISCARD` 策略下不并发创建 fire。
5. `OVERLAY` 策略下旧 fire 被取消，新 fire 正常进入等待。
6. 执行超时后 task/fire 状态正确。
7. `retryPolicyId` 存在时失败事件能桥接到 `nop-retry`。

## 15.4 回归测试

围绕现有 `IJobInvoker` 写一组兼容测试，确保旧业务 invoker 不需要全部重写。

---

## 十六、分阶段实施建议

## Phase 1：建模与 trigger 提纯

1. 新建 `NopJobSchedule/NopJobFire/NopJobTask` ORM。
2. 提取 trigger 计算上下文，保留 `CronExpression` 与 calendar。
3. 删除旧 `assignment` 依赖。

## Phase 2：最小可运行引擎

1. 实现 planner scanner。
2. 实现 dispatcher scanner。
3. 实现 `SINGLE` 模式 task 执行。
4. 实现手工触发、暂停、恢复、禁用。

## Phase 3：运行态完善

1. timeout checker
2. cancel 流程
3. 历史查询与管理页面
4. 数据清理任务

## Phase 4：可选扩展

1. `nop-job-retry-adapter`
2. `BROADCAST` / `SHARDING` 执行模式
3. executor registry
4. dashboard summary

---

## 十七、关键设计决策汇总

1. **新权威实体是 `JobSchedule`，不是 `JobInstance`。**
2. **`JobFire` 是一次触发批次，`JobTask` 是一次具体执行。**
3. **保留 trigger 定义与 cron 实现，但触发器只算时间，不再持有运行态。**
4. **retry 从 job 中剥离，失败后通过桥接点接入 `nop-retry`。**
5. **集群扫描和 claim 模式复用 `nop-retry` 的成熟做法。**
6. **V1 先做 `SINGLE` 模式，但从第一天起保留 fire/task 分层。**

---

## 十八、最终建议

这次 `nop-job` 重写不应该是“把旧类再整理一下”，而应该是一次明确的架构切换：

1. 旧 `DefaultJobScheduler` 体系整体退场。
2. `nop-job-core` 只保留 trigger/calendars/cron 这种纯算法能力。
3. 新运行时严格按 `schedule -> fire -> task` 建模。
4. `nop-retry` 成为失败后的唯一重试系统。
5. 外部调度系统只作为抽象功能拆分参考，不作为实现蓝本。

如果按这个方向重写，`nop-job` 会从“难以补救的半内存调度器”变成“可以恢复、可观察、可集群化、边界清晰的数据库调度系统”。
