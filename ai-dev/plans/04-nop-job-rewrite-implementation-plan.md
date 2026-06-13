# nop-job 重写执行计划

> Plan Status: completed
**版本**：v1.0  
**日期**：2026-04-04  
**依据**：`ai-dev/design/nop-job/rewrite-design.md`  
**目标**：将 `nop-job` 从旧的以内存调度器为中心的实现，重构为以数据库为中心的 `schedule -> fire -> task` 三层模型，并与 `nop-retry` 清晰分层。

---

## 一、执行原则

### 1.1 约束

1. ORM 模型修改直接落在 `nop-job/model/nop-job.orm.xml`。
2. ORM 变更后通过 `nop-job/nop-job-codegen` 执行 `mvn install` 自动生成实体类和相关代码。
3. 生成代码后，只在非下划线文件中编写业务逻辑。
4. 先保证数据库模型和运行时链路正确，再补管理接口和页面。
5. `nop-job` 不内建 retry 逻辑，失败后仅通过桥接点对接 `nop-retry`。

### 1.2 交付顺序

按以下顺序推进：

1. 新 ORM 模型
2. 新 store 与 scanner
3. 新 engine 执行链路
4. 新 service 管理命令
5. 页面与元数据适配
6. 迁移与清理

---

## 二、目标结构

### 2.1 目标实体

首批落地以下实体：

1. `NopJobSchedule`
2. `NopJobFire`
3. `NopJobTask`

### 2.2 目标模块分工

| 模块 | 本次计划中的职责 |
|------|------------------|
| `nop-job/model` | 维护新的 ORM 模型 |
| `nop-job-codegen` | 根据 ORM 重新生成实体与元数据 |
| `nop-job-dao` | 实现 schedule/fire/task store |
| `nop-job-core` | 保留并提纯 trigger/calendars/cron 的纯计算能力 |
| `nop-job-coordinator` | 承载 planner/dispatcher/timeout checker 等协调逻辑 |
| `nop-job-worker` | 预留 worker 执行承载点，首版可先最小化实现 |
| `nop-job-service` | 提供管理命令与查询接口 |
| `nop-job-web` | 适配新的实体页面和权限 |
| `nop-job-meta` | 适配 i18n、字典、xmeta |

### 2.3 模块策略

`nop-job-coordinator` 和 `nop-job-worker` 当前基本为空，本次优先把它们用起来，而不是继续把所有运行时逻辑堆在 `nop-job-core` 或 `nop-job-dao`。

---

## 三、Phase 1：模型切换与编译基线重建

### 3.1 目标

在 `nop-job/model/nop-job.orm.xml` 中完成新旧模型切换，并在同一阶段把所有直接依赖旧实体名的生成代码、Biz 接口和 BizModel 同步切到新实体，确保阶段结束时 `nop-job` 仍然可编译。

### 3.2 具体步骤

1. 备份并阅读当前 `nop-job.orm.xml` 中可复用的 dict、domain 和通用字段。
2. 在 `nop-job.orm.xml` 中把旧实体切换为新实体：
   - `NopJobSchedule`
   - `NopJobFire`
   - `NopJobTask`
3. 为 `NopJobSchedule` 明确字段清单：
   - 标识与隔离：`jobScheduleId`、`namespaceId`、`groupId`、`jobName`
   - 定义信息：`displayName`、`description`、`executorKind`、`executorRef`、`jobParams`
   - trigger 信息：`triggerType`、`cronExpr`、`repeatIntervalMs`、`maxExecutionCount`、`minScheduleTime`、`maxScheduleTime`、`misfireThresholdMs`、`useDefaultCalendar`、`pauseCalendarSpec`
   - 运行控制：`scheduleStatus`、`blockStrategy`、`timeoutSeconds`、`retryPolicyId`
   - 调度游标：`partitionIndex`、`fireCount`、`activeFireCount`、`lastFireTime`、`lastEndTime`、`nextFireTime`、`lastFireStatus`
4. 为 `NopJobFire` 明确字段清单：
   - 标识与快照：`jobFireId`、`jobScheduleId`、`namespaceId`、`groupId`、`jobName`
   - 触发信息：`triggerSource`、`scheduledFireTime`、`triggeredBy`
   - 运行信息：`fireStatus`、`plannerInstanceId`、`dispatchInstanceId`、`startTime`、`endTime`、`durationMs`
   - 快照与桥接：`jobParamsSnapshot`、`executorSnapshot`、`retryPolicyId`、`retryRecordId`
   - 错误信息：`errorCode`、`errorMessage`
   - 分区控制：`partitionIndex`
5. 为 `NopJobTask` 明确字段清单：
   - 标识与关联：`jobTaskId`、`jobFireId`、`taskNo`
   - 执行信息：`taskStatus`、`workerInstanceId`、`workerAddress`、`taskPayload`
   - 生命周期：`startTime`、`endTime`、`durationMs`
   - 结果信息：`resultPayload`、`errorCode`、`errorMessage`
   - 分区控制：`partitionIndex`
6. 为新实体补充字典：
   - schedule status
   - fire status
   - task status
   - trigger source
   - executor kind
   - block strategy
7. 增加必要索引：
   - schedule 扫描索引
   - fire 分发索引
   - task 查询索引
   - 唯一约束索引
8. 增加实体之间的 relation：
   - `JobFire -> JobSchedule`
   - `JobTask -> JobFire`
9. 执行 `nop-job-codegen` 的 `mvn install` 后，立即同步清理所有旧实体名直连代码，至少覆盖：
   - `nop-job-dao/src/main/java/io/nop/job/biz/*`
   - `nop-job-service/src/main/java/io/nop/job/service/entity/*`
10. 在这一阶段只允许保留 legacy 兼容说明，不允许保留会导致编译依赖旧实体类的代码。

### 3.3 涉及文件

1. `nop-job/model/nop-job.orm.xml`
2. `nop-job/model/nop-job.orm.java`（若项目保留手写模型镜像，则同步调整）
3. `nop-job-dao/src/main/java/io/nop/job/biz/*`
4. `nop-job-service/src/main/java/io/nop/job/service/entity/*`

### 3.4 验证

1. 在 `nop-job/nop-job-codegen` 执行 `mvn install`
2. 确认以下生成结果正确：
   - `nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/*`
   - `nop-job-dao/src/main/java/io/nop/job/dao/entity/*`
   - `nop-job/deploy/sql/*/_create_nop-job.sql`
3. 检查生成 SQL 是否符合新模型预期
4. 执行 `./mvnw test -pl nop-job -am -DskipTests` 或等价编译验证，确保 `nop-job` reactor 在阶段结束时不因旧实体名残留而失编

### 3.5 完成标准

1. 新实体代码可生成
2. 新 SQL 可生成且结构正确
3. `nop-job` 相关模块在阶段结束时保持可编译
4. 旧实体不再作为主路径存在；若短期保留 legacy 说明，也不能残留编译依赖

---

## 四、Phase 2：Trigger 计算层提纯

### 4.1 目标

保留 `cron + calendar + trigger 计算`，去掉它们对旧运行态的依赖，为新 planner 提供纯时间计算能力。

### 4.2 具体步骤

1. 新增只读上下文接口，例如 `ITriggerEvalContext`。
2. 把 `TriggerBuilder` 改造成面向只读上下文的构造器，或增加适配层。
3. 保留并复用：
   - `CronExpression`
   - `CronTrigger`
   - `PeriodicTrigger`
   - `LimitCountTrigger`
   - `LimitTimeTrigger`
   - `PauseCalendarTrigger`
   - `HandleMisfireTrigger`
4. 明确从 trigger 层移除：
   - `maxFailedCount` 的终止逻辑
   - `TriggerContextImpl` 的状态变更逻辑
   - `TriggerExecutorImpl` 的调度执行逻辑
5. 新增一个明确的时间计算入口，例如：
   - `JobTriggerCalculator.calculateNextFireTime(schedule, evalContext, now)`

### 4.3 涉及文件

1. `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/*`
2. `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`
3. `nop-job/nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java`

### 4.4 验证

1. 保留并运行 trigger 单元测试
2. 新增针对以下场景的测试：
   - cron
   - fixed-rate
   - fixed-delay
   - pause calendar
   - misfire
   - max execution count

### 4.5 完成标准

1. 新 planner 可以不依赖 `TriggerContextImpl` 计算下一次时间
2. trigger 计算测试全部通过

---

## 五、Phase 3：DAO 与 Store 重建

### 5.1 目标

实现新的数据库访问层，使 schedule/fire/task 成为数据库驱动的权威状态源。

### 5.2 具体步骤

1. 删除或停用旧的 `DaoJobSchedulerStore` 逻辑。
2. 在 `nop-job-dao` 中新增以下 store 接口：
   - `IJobScheduleStore`
   - `IJobFireStore`
   - `IJobTaskStore`
3. 在 `nop-job-dao` 中新增对应实现类：
   - `JobScheduleStoreImpl`
   - `JobFireStoreImpl`
   - `JobTaskStoreImpl`
4. 参考 `nop-retry` 的 store 模式实现：
   - `fetchDueSchedules(limit, partitions)`
   - `tryLockSchedulesForPlan(records, timeout)`
   - `fetchWaitingFires(limit, partitions)`
   - `tryLockFiresForDispatch(records, timeout)`
5. 引入乐观锁批量 claim 模式，避免多节点重复计划/分发。
6. 为 `schedule -> fire`、`fire -> task` 的核心更新提供原子事务方法。
7. 如果 Phase 1 仍保留了任何 legacy 表结构兼容字段，本阶段要把 store 明确为只面向新实体工作，禁止继续读写旧实体。

### 5.3 涉及文件

1. `nop-job-dao/src/main/java/io/nop/job/dao/store/*`
2. `nop-job-dao/src/main/java/io/nop/job/dao/entity/*`

### 5.4 验证

1. 新增 store 单元测试或集成测试
2. 验证并发 claim 下不会重复计划或重复分发
3. 验证唯一索引约束生效

### 5.5 完成标准

1. 可以从数据库获取待计划 schedule
2. 可以从数据库获取待分发 fire
3. 多节点并发下 claim 行为正确

---

## 六、Phase 4：Coordinator 运行时链路

### 6.1 目标

把 planner、dispatcher、completion、timeout checker 放入 `nop-job-coordinator`，并在同一阶段完成应用装配，使新的协调执行链路真正被 `nop-job-app` 加载。

### 6.2 具体步骤

1. 在 `nop-job-coordinator` 中新增 planner scanner：
   - 周期扫描 schedule
   - claim 后生成 fire
2. 在 `nop-job-coordinator` 中新增 dispatcher scanner：
   - 周期扫描 fire
   - claim 后生成 task
3. 新增 completion processor：
   - 聚合 task 结果
   - 更新 fire 最终状态
   - 回写 schedule 汇总字段
4. 新增 timeout checker：
   - 扫描运行超时 task
   - 调用 cancel SPI
   - 标记超时状态
5. 首版只实现 `SINGLE` 执行模式。
6. 在 schedule 层实现 block strategy：
   - `DISCARD`
   - `OVERLAY`
   - `PARALLEL`
7. 明确 coordinator 的 Bean 暴露方式：
   - 新增 `beans.xml`
   - 或在现有模块资源中注册对应 Bean
8. 修改 `nop-job/nop-job-app/pom.xml`，把 `nop-job-coordinator` 纳入应用依赖。
9. 如果 scanner/processor 需要 worker 侧能力，明确通过 `nop-job-worker` 暴露接口并同步接入 `nop-job-app`。

### 6.3 fixed-delay 特殊处理

这一阶段必须显式实现：

1. `CRON/FIXED_RATE/ONCE` 在 planner 阶段推进 `next_fire_time`
2. `FIXED_DELAY` 在 fire 完成后由 completion processor 推进 `next_fire_time`

### 6.4 涉及文件

1. `nop-job-coordinator/src/main/java/**`
2. `nop-job-coordinator/src/main/resources/**`
3. `nop-job/nop-job-app/pom.xml`
4. 必要的 `beans.xml` 与启动配置

### 6.5 验证

1. 创建启用中的 schedule 后，planner 能生成 fire
2. dispatcher 能把 fire 转成 task
3. 执行结束后 schedule 汇总字段正确更新
4. fixed-delay 行为符合预期
5. `nop-job-app` 启动后能实际装配 coordinator 中的 scanner/processor Bean

### 6.6 完成标准

1. 新运行时链路可闭环运行
2. `nop-job-app` 已接入新的 coordinator 装配
3. 不再依赖 `DefaultJobScheduler`

---

## 七、Phase 5：Worker 与执行器接入

### 7.1 目标

让新的 task 执行逻辑统一通过 `IJobInvoker` 完成，并为后续 worker 独立部署预留结构。

### 7.2 具体步骤

1. 明确 task 到 `IJobInvoker` 的映射规则。
2. 在 `nop-job-worker` 中放置最小执行承载逻辑，或提供统一执行 facade。
3. 保持对现有 `jobInvoker` / `executor_ref` 的兼容。
4. 统一封装：
   - invokeAsync
   - cancelAsync
   - 执行结果到 `JobFireResult` 或新结果对象的转换
5. 记录执行节点信息回写到 `nop_job_task.worker_instance_id`。

### 7.3 涉及文件

1. `nop-job-worker/src/main/java/**`
2. `nop-job-worker/src/main/resources/**`
3. `nop-job-api/src/main/java/io/nop/job/api/execution/*`
4. `nop-job/nop-job-app/pom.xml`（若 worker 模块需要被应用直接装配）

### 7.4 验证

1. 现有基于 `IJobInvoker` 的业务执行器无需大改即可接入
2. cancel/timeout 场景可正常回调到 invoker

### 7.5 完成标准

1. 新 task 可稳定执行
2. 与旧 `IJobInvoker` 保持兼容

---

## 八、Phase 6：Service 管理命令与查询接口

### 8.1 目标

用领域命令替代当前只有 CRUD 的 service 层。

### 8.2 具体步骤

1. 为以下实体生成新 BizModel：
   - `NopJobSchedule`
   - `NopJobFire`
   - `NopJobTask`
2. 在 `nop-job-service` 中新增 Processor 或服务类，承载以下命令：
   - `enableSchedule`
   - `disableSchedule`
   - `pauseSchedule`
   - `resumeSchedule`
   - `triggerNow`
   - `cancelFire`
   - `archiveSchedule`
3. 新增查询接口：
   - schedule 列表/详情
   - fire 列表/详情
   - task 列表/详情
   - runtime summary

### 8.3 涉及文件

1. `nop-job-service/src/main/java/io/nop/job/service/entity/*`
2. `nop-job-service/src/main/java/io/nop/job/service/processor/*`
3. `nop-job-dao/src/main/java/io/nop/job/biz/*`

### 8.4 验证

1. BizModel 命令可直接操控 schedule 生命周期
2. `triggerNow` 不影响正常 trigger 游标
3. `cancelFire` 可取消活动态 fire，并正确回写 fire/task/schedule 状态

### 8.5 完成标准

1. service 层不再只是 CRUD 包装
2. 管理命令具备完整领域语义
3. 当前已完成的最小命令集包括：
   - `enableSchedule`
   - `disableSchedule`
   - `pauseSchedule`
   - `resumeSchedule`
   - `triggerNow`
   - `cancelFire`
   - `rerunFire`
   - `archiveSchedule`
4. 当前已验证的关键语义包括：
   - `enableSchedule` 仅允许 `DISABLED -> ENABLED`
   - `disableSchedule` 允许 `ENABLED / PAUSED -> DISABLED`，并且对 `DISABLED` 幂等
   - `pauseSchedule` 仅允许 `ENABLED -> PAUSED`，并且对 `PAUSED` 幂等
   - `resumeSchedule` 仅允许 `PAUSED -> ENABLED`
   - `archiveSchedule` 允许 `ENABLED / DISABLED / PAUSED / COMPLETED -> ARCHIVED`，并且对 `ARCHIVED` 幂等
   - `archiveSchedule` 会清空 `next_fire_time`
   - `triggerNow` 创建 `MANUAL` fire，复用 block strategy，且不推进定时 trigger 游标
   - `cancelFire` 仅允许取消 `WAITING / DISPATCHING / RUNNING` fire
   - `cancelFire` 对 scheduled `FIXED_DELAY` fire 会补算下一次 `next_fire_time`
   - `cancelFire` 对 manual fire 不推进 schedule 游标
   - `rerunFire` 仅允许对终态 fire 重跑：`SUCCESS / FAILED / TIMEOUT / CANCELED`
   - `rerunFire` 基于原 fire 的参数快照与执行器快照创建 `RECOVERY` fire
   - `rerunFire` 不推进 schedule 的定时 trigger 游标，但仍复用当前 schedule 的 block strategy
   - `rerunFire` 对 `ARCHIVED / COMPLETED` schedule 不允许执行

---

## 九、Phase 7：Web、Meta 与权限适配

### 9.1 目标

让管理端能够基于新实体工作。

### 9.2 具体步骤

1. 更新 `nop-job-meta` 中的 i18n、dict 和 xmeta。
2. 更新 `nop-job-web` 中的页面模型、菜单、权限配置。
3. 移除旧 `definition/instance/instance_his/assignment` 页面入口。
4. 新增新实体的页面入口：
   - 调度定义
   - 触发批次
   - 执行任务

### 9.3 涉及文件

1. `nop-job-meta/src/main/resources/_vfs/**`
2. `nop-job-web/src/main/resources/_vfs/**`

### 9.4 验证

1. 新页面可查看 schedule/fire/task
2. 命令按钮能触发 enable/pause/resume/triggerNow

### 9.5 完成标准

1. 管理端基于新模型可用
2. 旧页面入口移除或标记弃用

### 9.6 当前进展

当前已完成第一批适配：

1. `nop-job` 导航入口已收敛到新三层模型页面：
   - `NopJobSchedule`
   - `NopJobFire`
   - `NopJobTask`
2. 旧入口已从手写 `action-auth` 覆写层中移除：
   - `NopJobAssignment`
   - `NopJobDefinition`
   - `NopJobInstance`
   - `NopJobInstanceHis`
3. `NopJobSchedule` 页面已暴露领域动作按钮：
   - `triggerNow`
   - `enableSchedule`
   - `disableSchedule`
   - `pauseSchedule`
   - `resumeSchedule`
   - `archiveSchedule`
4. `NopJobFire` 页面已暴露领域动作按钮：
   - `cancelFire`
   - `rerunFire`
5. `NopJobFire` / `NopJobTask` 页面已移除新增、编辑、删除等不符合运行态语义的 CRUD 操作入口。
6. `nop-job-meta` 已补充对应中英文动作文案。
7. 已补充基础下钻入口：
   - `schedule -> fire`
   - `fire -> task`
8. 已补充基础 runtime summary drawer：
   - `schedule`：概览 + 关联 fire 列表
   - `fire`：概览 + 关联 task 列表
9. 已补充 task 侧基础体验：
   - `task`：概览 + 所属 fire 摘要
   - `fire` 支持直接查看所属 `schedule`
10. 已补基础查看体验优化：
    - `schedule.view` 中将参数/日历/描述字段调整为长文本展示
    - `fire.view` 中将参数快照/执行器快照/错误消息调整为长文本展示
    - `task.view` 中将 payload/result/error 调整为长文本展示

当前限制：

1. 本轮主要完成页面入口、按钮与权限点接线。
2. 尚未补更细粒度的运行态展示，例如：
   - 更丰富的详情页跳转与聚合信息
   - 更丰富的聚合卡片/指标展示

---

## 十、Phase 8：与 nop-retry 的桥接

### 10.1 目标

为 job 失败后接入 `nop-retry` 提供清晰桥接点，但不把 retry 内建到 job 引擎里。

### 10.2 具体步骤

1. 在 `nop-job-api` 或 `nop-job-core` 中定义桥接接口：
   - `IJobRetryBridge`
2. 提供默认 no-op 实现。
3. 在 completion processor 中，当 fire 失败且存在 `retry_policy_id` 时发出桥接调用。
4. 可选新增 `nop-job-retry-adapter` 模块，基于 `IRetryEngine` 或 retry store 完成实际接入。

### 10.3 验证

1. 无桥接实现时，job 失败不影响主流程
2. 有桥接实现时，失败 fire 能创建 retry record

### 10.4 完成标准

1. `nop-job` 与 `nop-retry` 边界清晰
2. 桥接是可选模块，不污染主引擎

---

## 十一、Phase 9：迁移、清理与收尾

### 11.1 目标

完成从旧模型到新模型的切换，并收敛遗留代码。该阶段的前提是前面阶段已经让新链路可运行，迁移只负责切换与清理，不再承担基础实现职责。

### 11.2 具体步骤

1. 在切换窗口前，编写 `NopJobDefinition -> NopJobSchedule` 的迁移脚本或迁移工具。
2. 如果需要保留历史数据，补充 `NopJobInstance/NopJobInstanceHis -> NopJobFire/Task` 的历史迁移策略；若不迁运行态，文档中明确冻结规则。
3. 在确认新链路可运行后，再停用旧 `DefaultJobScheduler` 入口和旧 beans 注册。
4. 对 `NopJobInstance/NopJobInstanceHis/NopJobAssignment` 做弃用或删除处理。
5. 清理旧 DAO/store/queue 中不再使用的实现。
6. 视情况移除旧 API，或保留兼容层并标注 deprecated。

### 11.3 验证

1. 迁移后的 schedule 可正常参与新 planner 扫描
2. 旧入口停用后不会影响新链路
3. 切换窗口后不再出现重复计划或旧新双写

### 11.4 完成标准

1. 新旧模型完成切换
2. 旧运行时链路不再是默认路径

---

## 十二、测试与验证清单

### 12.1 必跑命令

建议每个关键阶段至少执行：

1. `mvn install` in `nop-job/nop-job-codegen`
2. `./mvnw test -pl nop-job/nop-job-core`
3. `./mvnw test -pl nop-job/nop-job-dao`
4. `./mvnw test -pl nop-job/nop-job-service`
5. 关键里程碑后执行：`./mvnw test -pl nop-job -am`
6. Phase 1 结束后至少执行一次 `nop-job` reactor 编译验证，确保旧实体引用已清零
7. Phase 4 结束后至少执行一次 `nop-job-app` 启动或集成验证，确认 coordinator 已被装配

### 12.2 关键测试场景

1. cron 触发
2. fixed-rate 触发
3. fixed-delay 触发
4. pause calendar
5. misfire skip
6. 手工触发
7. `DISCARD` / `OVERLAY` / `PARALLEL`
8. timeout + cancel
9. 多节点并发 claim
10. retry bridge

---

## 十三、风险与应对

### 风险 1：旧代码耦合过深，删除困难

应对：

1. 先引入新链路并让其默认生效
2. 再逐步停用旧入口

### 风险 2：fixed-delay 语义容易被误实现

应对：

1. 单独写集成测试
2. 强制在 completion processor 更新 `next_fire_time`

### 风险 3：实体切换后 web/meta 生成内容较多

应对：

1. 先确保 service 和 engine 可用
2. 页面适配放在后半段集中处理

### 风险 4：retry 边界再次混乱

应对：

1. 首版只保留 `retry_policy_id`
2. 不在 `nop-job` 表中重新引入 retry 次数、间隔、退避策略字段

---

## 十四、推荐实施顺序

按实际执行建议顺序：

1. Phase 1：ORM 重建
2. Phase 2：Trigger 计算层提纯
3. Phase 3：DAO 与 Store 重建
4. Phase 4：Coordinator 运行时链路
5. Phase 5：Worker 与执行器接入
6. Phase 6：Service 管理命令与查询接口
7. Phase 7：Web、Meta 与权限适配
8. Phase 8：与 `nop-retry` 的桥接
9. Phase 9：迁移、清理与收尾

---

## 十五、里程碑定义

### Milestone 1：模型切换完成

标志：

1. `nop-job.orm.xml` 已切到 `schedule/fire/task`
2. codegen 成功
3. 新 SQL 正确生成
4. `nop-job` 相关模块可编译，旧实体名依赖已清理

### Milestone 2：引擎闭环可运行

标志：

1. planner 能生成 fire
2. dispatcher 能生成 task
3. task 执行结果能回写 fire/schedule
4. `nop-job-app` 已实际加载新协调链路

### Milestone 3：管理面可用

标志：

1. schedule 生命周期命令可用
2. 页面可查看 schedule/fire/task

### Milestone 4：旧链路退场

标志：

1. 默认路径不再依赖 `DefaultJobScheduler`
2. 旧实体和旧 store 不再承担主职责

---

## 十六、最终完成标准

满足以下条件视为本次重写完成：

1. `nop-job` 运行时以数据库为权威状态源。
2. 调度、触发、执行三层模型明确分离。
3. trigger 逻辑仅负责时间计算。
4. retry 职责完全交给 `nop-retry`。
5. 旧内存调度器不再是默认实现。
6. 新模型可以支持后续广播、分片和更复杂执行模式扩展。

## Closure

Status Note: Plan completed — nop-job rewritten to DB-centric schedule→fire→task model across all 9 phases.

Closure Audit Evidence:

- Reviewer / Agent: automated (closure-verify)
- Evidence:
  - Phase 1: ORM model switched to NopJobSchedule/NopJobFire/NopJobTask, codegen passing
  - Phase 2-5: Trigger pure computation, DAO/Store, Coordinator runtime, Worker/Invoker — all implemented
  - Phase 6: Service layer with domain commands (enable/disable/pause/resume/triggerNow/cancelFire/rerunFire/archiveSchedule)
  - Phase 7: Web/Meta adapted, old entity entries removed
  - Phase 8: Retry bridge interface defined with no-op default
  - Phase 9: Migration and cleanup completed
