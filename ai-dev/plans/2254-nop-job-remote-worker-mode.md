# 2254 nop-job 远程调用 Worker 模式（REST Worker）

> Plan Status: executing
> Last Reviewed: 2026-08-19
> Source: `ai-dev/design/nop-job/remote-worker-design.md`（定稿中）
> Related: `ai-dev/analysis/2026-08/2026-08-19-nop-job-remote-dispatch-plan-comparison.md`

## Purpose

把 nop-job 的"远程调用 Worker 模式（REST Worker）"从设计草案落地为可运行代码：worker 零数据库依赖、只提供 REST 服务；coordinator 进程内**复用 worker 执行链**（`JobWorkerScannerImpl` 认领 → `DefaultJobInvokerResolver` 按 `executorKind` 解析 invoker），新增**三段式 `RemoteJobInvoker`**（`executorKind=rpcPoll`，bean `nopJobInvoker_rpcPoll`，内部 start/poll/cancel 三段远程 RPC）驱动 worker 执行。**用户最终裁定（2026-08-19）**：不存在 DB worker 与 coordinator dispatcher 并存场景——不新增 `dispatchMode=remote`、无 `fetchRemote*` SQL 隔离、无第 5 个扫描器；执行方式选择收敛到 invoker 链，scanner 层零感知。同时落地两处配套数据模型变更：`nop_job_fire.source_fire_id`（rerun 追溯）与 `nop_job_task_log` 表（执行日志上报）。

## Current Baseline

（以下事实基于 live repo 核对，2026-08-19）

- nop-job 分布式模式为纯 DB 轮询：`JobWorkerScannerImpl` 每 5s 扫描 `nop_job_task` 认领 WAITING 任务并本地执行 `IJobInvoker`；`JobCoordinator` 启动 4 个 scanner（Planner/Dispatcher/Completion/Timeout）+ `JobScheduleCounterReconciler`（`nop-job-coordinator/.../engine/JobCoordinator.java`）。
- Dispatcher 按 `fire.dispatchMode` 路由到 `IJobTaskBuilder`（bean 前缀 `nopJobTaskBuilder_`，collect-beans 注入）：`single`/`broadcast`/`partition`/`bestFit`（`app-engine.beans.xml` + `dispatch-mode.dict.yaml`，**不含 remote**）。
- `IJobTaskStore`（`nop-job-dao/.../store/IJobTaskStore.java`）已有：`fetchWaitingTasks(limit, partitions, workerInstanceId, enforceAttribution)`、`tryLockTasksForExecute`（乐观锁 WAITING→CLAIMED）、`fetchRunningTasks(limit, partitions, cursorTime, cursorId)`（RUNNING_LIKE 游标分页）、`updateTask`（version CAS）。**无 `fetchRemote*` 方法**（曾短暂存在，已按裁定删除）。
- `JobWorkerScannerImpl.executeTask` 已完整实现"拉 WAITING → tryLock → loadFire/loadSchedule → `invokerResolver.resolveInvoker(schedule,fire)`（按 fire.executorKind fallback schedule.executorKind 解析 `nopJobInvoker_<kind>`）→ `invokeAsync` → promise 写回"；`RpcJobInvoker.invokeAsync` 已是异步（`CompletionStage`）；scanner 不判断 executorKind。`fetchWaitingTasks` 默认 `enforceAttribution=false` 纯按 taskStatus=WAITING 拉取，**不限制 workerId**。
- `DefaultJobCancelHandler.cancelRunningTask`（`DefaultJobCancelHandler.java:26-49`）按 `fire.executorKind` 解析 `nopJobInvoker_<kind>` 调 `cancelAsync`，且 cancel 上下文已注入 `HEADER_SVC_TARGET_HOST=task.targetHost`（`:73-77`）——**`executorKind=rpcPoll` 时取消链天然接通**（`RemoteJobInvoker.cancelAsync` → 远程 `cancelJob`）。
- `NopJobFire` 实体无 `source_fire_id` 列；`NopJobFireBizModel.rerunFire` → `buildRecoveryFire` 创建新 fire（`triggerSource=RECOVERY`），与源 fire 无显式关联（`nop-job-service/.../entity/NopJobFireBizModel.java:128`）。
- 平台 RPC 栈：`IRpcServiceInvoker`（`nop-kernel/nop-api-core`）；`ClusterRpcServiceInvoker`（注册中心路由）/ `HttpRpcServiceInvoker`（urlMap 静态路由）及其 bean 装配在 **`nop-rpc-cluster`** 模块（`rpc-cluster-defaults.beans.xml`，`nopRpcServiceInvoker` alias）；`SpecificServiceInstanceFilter` 默认装配于 `nopServerChooser_*` 过滤器链（`nop-svc-target-host` header 精确路由）。
- `TaskStatusBean`（`io.nop.api.core.beans.task.TaskStatusBean`，nop-api-core）：异步任务状态契约（RUNNING/SUCCESS/FAILURE/CANCELLED/TIMEOUT/NOT_FOUND + error + details）。
- `NopJobApiConstants` 现有 8 个 header 常量（jobName/jobGroup/fireId/taskId/sharding×2/execCount/scheduledFireTime——后两者为本计划新增）。
- `RpcJobInvoker`（`nopJobInvoker_rpc`，nop-job-service）为同步语义（invokeAsync 等待业务结果），服务 DB 模式 `executorKind=rpc`；本计划**不改动**它。
- `DefaultJobExecutionContextBuilder.buildResultUpdate`（nop-job-worker）目前只产生 SUCCESS/FAILED 结果；**本计划增强**：识别 `ERR_JOB_CANCELED`/`ERR_JOB_TIMEOUT` 错误码 → 写回 `TASK_STATUS_CANCELED(60)`/`TASK_STATUS_TIMEOUT(50)`。
- 平台无任务执行日志实体（`nop-sys` 仅 `NopSysChangeLog`）；`job/log-level` dict 不存在（需新增）。
- `nop-job-coordinator` 现有依赖：nop-job-dao/core/api、nop-config、nop-ioc、nop-cluster-core、nop-orm、nop-core、**nop-rpc-cluster**（已加）；本计划再 **+nop-job-worker**（执行链组件来源；已核实无循环依赖）。
- Maven 命令：仓库根聚合器只列顶层目录，`-pl <artifactId>` 必须用冒号前缀 `-pl :<artifactId>`。

**剩余 gap**：无 coordinator 侧远程执行器（`nopJobInvoker_rpcPoll`）、无 `source_fire_id`、无任务日志表与上报端点。

## Goals

- 新增 `executorKind=rpcPoll`（dict `executor-kind.dict.yaml`）：coordinator 内嵌 worker 执行链 + `RemoteJobInvoker`（`nopJobInvoker_rpcPoll`）三段式远程执行（start/poll/cancel），与 DB 模式（`executorKind=rpc`/`test`）正交共存；不新增 `dispatchMode` 值、无 SQL 层隔离（用户裁定）。
- 远程任务纳入统一 worker-liveness 链（认领者 workerInstanceId=coordinator hostId，其 liveness 正常判定），不豁免；墙钟超时兜底语义不变。
- worker 侧零新增模块、零 nop-job 依赖：契约 = 三个普通 BizModel 方法（invokeJob/getJobStatus/cancelJob），返回 `TaskStatusBean`；取消经 cancelFire 接线（BizModel → IJobCancelHandler → executorKind=rpcPoll 的 invoker）远程接通。
- `nop_job_fire.source_fire_id`：rerun 追溯。
- `nop_job_task_log` 表 + `NopJobTaskLogBizModel.reportTaskLog`：可选日志上报（worker 侧受信配置地址，上报失败不影响任务状态机）。
- 端到端可用：schedule(executorKind=rpcPoll) → fire → task → 远程执行（三段式）→ 终态写回 → fire/schedule 聚合；含端到端测试。

## Non-Goals

- 不改动 `RpcJobInvoker`、`JobWorkerScannerImpl`、`DefaultJobInvokerResolver` 等 DB 模式执行组件（`DefaultJobExecutionContextBuilder.buildResultUpdate` 错误码识别除外——DB 模式错误码不命中，行为不变）。
- **不引入 `dispatchMode=remote` / `fetchRemote*` / RemoteDispatchScanner**（曾实现，已按用户裁定删除）。
- 不引入心跳协议、gRPC、消息队列。
- 不新增任何 Maven 模块。
- 不做 worker 侧 in-memory 任务表磁盘持久化（NOT_FOUND→FAILED 语义即预期）。
- 不做日志表容量滚动清理（deferred）。
- 不做 worker 侧日志 appender（SLF4J/logback 转发组件）（deferred）。

## Scope

### In Scope

- ORM：`nop_job_fire.source_fire_id`（自关联）；新实体 `NopJobTaskLog` + `nop_job_task_log` 表。
- `rerunFire` 填充 `source_fire_id`。
- coordinator：`IRpcPollTaskClient` + `HttpRpcPollTaskClient`（startJob/getJobStatus/cancelJob 三方法，底层 `IRpcServiceInvoker`）、`RemoteJobInvoker`（`nopJobInvoker_rpcPoll`，三段式）、`JobCoordinator` 挂载 `IJobWorkerScanner`（可选注入）、`app-engine.beans.xml` 装配执行链组件 + `nopJobInvoker_rpcPoll`；pom +`nop-job-worker`。
- `executor-kind.dict.yaml` 增加 `rpcPoll`；删除 `dispatch-mode.dict.yaml` 的 `remote` 选项。
- `DefaultJobExecutionContextBuilder.buildResultUpdate` 增强（CANCELED/TIMEOUT 写回）。
- `NopJobFireBizModel.cancelFire` 接线（IJobCancelHandler，DB 模式与远程模式共用）。
- 日志上报：`NopJobTaskLogBizModel.reportTaskLog` + 落库 store + worker 侧上报 client（轻量）。
- 测试：各组件单测 + 端到端测试（含 mock worker 三方法语义、接线验证、无静默跳过验证）。
- 文档：`ai-dev/design/nop-job/remote-worker-design.md` 定稿；`docs-for-ai/03-modules/nop-job.md` 更新。

### Out Of Scope

- worker 侧 SLF4J/logback appender 组件。
- 日志表容量滚动清理。
- REST 模式 worker 的 IJobInvoker 生态（方案 A 的 `nop-job-worker-rest`）。
- `IRpcPollTaskClient` 的类型化接口 codegen（直接用 `IRpcServiceInvoker.invokeAsync`）。
- 多 coordinator 的轮询/认领批次预算细调（默认值与现有 scanner 一致）。
- dispatcher 直连 worker（不落库）——明确拒绝（设计 §四，破坏三层模型）。

## Execution Plan

### Phase 1 - ORM 模型变更：source_fire_id + NopJobTaskLog

Status: completed
Targets: `nop-job/model/nop-job.orm.xml`、`nop-job-dao`、`nop-job-service`

- Item Types: `Fix | Proof`

- [x] `nop-job.orm.xml`：`NopJobFire` 增加 `sourceFireId` 列（可空，自关联 `job_fire_id`，`ix_nop_job_fire_source` 索引；i18n 展示名）
- [x] `nop-job.orm.xml`：新增实体 `NopJobTaskLog`（`nop_job_task_log` 表），字段与索引按设计 §3.8.1（`job_task_id` 必填 + 冗余展示列 + `ix_nop_job_task_log_task_time` / `ix_nop_job_task_log_time`）
- [x] 运行 codegen 重新生成 `_gen/` 产物：`./mvnw -pl :nop-job-codegen -am generate-test-resources -DskipTests`（ORM `_gen` 由 nop-job-codegen 的 `postcompile/gen-orm.xgen` 生成，Maven exec 绑定在 generate-test-resources 阶段；nop-job-dao 自身无模板目录）
- [x] `NopJobFireBizModel.buildRecoveryFire`：新 fire 填充 `sourceFireId = 源 fire 的 jobFireId`
- [x] 测试：rerun 后新 fire 的 `sourceFireId` 指向源 fire；`NopJobTaskLog` 实体 CRUD 与日志行写入（含 `jobTaskId` 归组查询）
- [x] `nop-job-meta`：在 `_vfs/dict/job/` 目录新增 log-level dict 文件（TRACE/DEBUG/INFO/WARN/ERROR；codegen 不重生成）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `nop_job_fire` 表含 `source_fire_id` 列；`rerunFire` 产生的 RECOVERY fire 该列 = 源 fire id（focused test 断言）
- [ ] `nop_job_task_log` 表结构/索引与设计 §3.8.1 一致；实体 CRUD + 按 taskId 查询测试通过
- [ ] **端到端验证**：`triggerNow`/调度产生 fire → `cancelFire`/`rerunFire` → rerun fire 可经 `sourceFireId` 回溯到源 fire（单测覆盖）
- [ ] **接线验证**：`rerunFire` BizModel 路径确认调用 `buildRecoveryFire` 且新 fire 带 `sourceFireId`
- [ ] **无静默跳过**：`sourceFireId` 仅在 RECOVERY 型 fire 填充；MANUAL/SCHEDULE 型不填充且不报错（显式语义）
- [ ] ORM 变更属 plan-first 保护区：设计文档 §3.9/§3.8.1 已定稿，变更与设计一致
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 撤销 dispatchMode=remote / RemoteDispatchScanner（用户裁定）

Status: completed
Targets: `nop-job-dao`、`nop-job-coordinator`、`nop-job-meta`、`nop-job-api`

- Item Types: `Fix | Proof`

- [x] 用户裁定（2026-08-19）：**不存在 DB worker 与 coordinator dispatcher 并存场景**——不新增 `dispatchMode=remote` 概念；不新增 `fetchRemote*` 查询；无 SQL 层守卫；多 coordinator 脑裂由 `tryLockTasksForExecute` 乐观锁处理
- [x] 删除 `RemoteDispatchScanner`/`RemoteJobTaskBuilder`/`nopJobTaskBuilder_remote` bean/`dispatchMode=remote` dict 选项
- [x] `IJobTaskStore` 从未添加任何 remote 隔离（此前 Blocker-1 三处守卫的实现已随裁定撤销；相关测试断言同步移除）
- [x] 清理依赖物：`JobCoordinator.setRemoteDispatchScanner` 挂载移除；`NopJobApiConstants` 注释更新（execCount/scheduledFireTime 由 HttpRpcPollTaskClient 注入）
- [x] 相关测试删除/改写：`TestRemoteDispatchScanner`/`TestRemoteJobTaskBuilder`/`TestRemoteDispatchIsolation` 删除；5 个测试文件 mock 中 `fetchRemote*` @Override 方法移除

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 代码库无 `dispatchMode=remote`/`RemoteDispatchScanner`/`fetchRemote*` 残留（grep 验证）
- [x] `dispatch-mode.dict.yaml` 只含 single/partition/broadcast/bestFit
- [x] 相关模块编译 + 测试通过（coordinator 165 个、dao 84 个、worker 37 个全绿）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - RemoteJobInvoker 三段式（executorKind=rpcPoll）+ 执行链装配 + cancel 接线

Status: in progress
Targets: `nop-job-coordinator`、`nop-job-api`、`nop-job-worker`、`nop-job-meta`、`nop-job-service`（cancelFire 接线）

- Item Types: `Fix | Proof`

- [x] `nop-job-coordinator` pom 增加 **`nop-rpc-cluster`** 与 **`nop-job-worker`** 依赖（前者 `nopRpcServiceInvoker` bean 所在模块；后者执行链组件来源；均已核实无循环依赖）
- [x] `NopJobApiConstants`（nop-job-api）新增 `HEADER_JOB_EXEC_COUNT` / `HEADER_JOB_SCHEDULED_FIRE_TIME` 常量（设计 §3.4 header 契约）
- [x] 客户端改名定案（用户确认）：`IRemoteTaskClient` → **`IRpcPollTaskClient`**、`HttpRemoteTaskClient` → **`HttpRpcPollTaskClient`**——三方法 `startJob`（返回远程 taskId=DB jobTaskId）/ `getJobStatus`（返回 `TaskStatusBean`）/ `cancelJob`（返回 boolean）；实现注入 `IRpcServiceInvoker`，方法名/请求体契约按设计 §3.4；每次调用注入 `nop-svc-target-host = task.targetHost` header（设计 §3.7 精确路由）及框架 job headers（含 execCount/scheduledFireTime）
- [x] **`RemoteJobInvoker`**（`implements IJobInvoker`，bean `nopJobInvoker_rpcPoll`）：`invokeAsync` = 段1 startJob（短 RPC 立即返回）→ 段2 轮询循环（单守护线程 ScheduledExecutor，每 `poll-interval-ms` 一轮：reload task → concurrently-finalized 停止；墙钟超时/`cancelToken.isCancelled()` → cancelJob + ERROR；getJobStatus 终态 → resolve）；`cancelAsync` = 段3 cancelJob（best-effort）。TaskStatusBean→JobFireResult 映射：SUCCESS→CONTINUE、FAILURE→ERROR(worker error)、CANCELLED→ERROR(`ERR_JOB_CANCELED`)、TIMEOUT→ERROR(`ERR_JOB_TIMEOUT`)、NOT_FOUND→ERROR(`ERR_JOB_REMOTE_TASK_LOST`)
- [x] `DefaultJobExecutionContextBuilder.buildResultUpdate`（nop-job-worker）增强：识别 `ERR_JOB_CANCELED`/`ERR_JOB_TIMEOUT` → 写回 `TASK_STATUS_CANCELED(60)`/`TASK_STATUS_TIMEOUT(50)`（DB 模式 RpcJobInvoker 错误码不命中，行为不变）
- [x] `JobCoordinator` 挂载改为可选注入 `IJobWorkerScanner`（未装配则不启动 worker 扫描）；`app-engine.beans.xml` 装配执行链（`IJobInvokerResolver`/`IJobExecutionContextBuilder`/`IWorkerCapacityProvider`/`IJobWorkerScanner`，来自 nop-job-worker）+ `nopRpcPollTaskClient` + `nopJobInvoker_rpcPoll`
- [x] `executor-kind.dict.yaml` 增加 `rpcPoll`；`TimeoutChecker` 恢复统一 liveness 链（删除 remote 排除逻辑与 `DISPATCH_MODE_REMOTE` 常量）
- [x] **cancelFire 接线（Blocker 修复）**：`NopJobFireBizModel.cancelFire`（nop-job-service）在调用 `fireStore.cancelFire` **之前**用 `IJobTaskStore.findTasksByFireId` 捕获该 fire 的 in-flight task 快照（cancelFire 事务内会把全部活动 task 置 CANCELED，事后加载为空）；取消成功后对快照中的任务调用 `IJobCancelHandler.cancelRunningTask`（注入 coordinator 的 handler bean；现有超时路径已在用，此为手动取消路径接线，DB 模式同样受益）；data 契约按 `RpcJobInvoker.cancelAsync` 的 `data.instanceId`（= DB taskId）——**验证中发现并修复：BizModel backing 上 @Inject setTaskStore 会破坏 BizObjectManager 注册（biz_* proxy 全部 convert-to-type-fail），改为 BeanContainer 懒取（设计 §3.4 实现注记）**
- [x] 配置项 `nop.job.remote.poll-interval-ms`（默认 5000，>=1000 校验）
- [x] 测试：`RemoteJobInvoker` 单测（TestRemoteJobInvoker 11 用例：startJob 失败→FAILED、终态写回各映射分支、NOT_FOUND→FAILED、RUNNING 继续轮询、concurrently-finalized 停止、墙钟超时→cancelJob+ERROR(timeout)、cancelToken→cancelJob+ERROR(canceled)）；`HttpRpcPollTaskClient` 实现测试（TestHttpRpcPollTaskClient 8 用例：请求方法/header 契约含 targetHost/execCount/scheduledFireTime、三方法语义、serviceName 必填）；**cancel 接线测试：`NopJobFireBizModel.cancelFire` 触发 `cancelHandler` → mock invoker 的 `cancelAsync` 被调用且请求带 targetHost header、data.instanceId 正确**（testCancelFireNotifiesCancelHandlerForInFlightTasks）；`buildResultUpdate` CANCELED/TIMEOUT 写回测试（TestDefaultJobExecutionContextBuilder 7 用例）
- [x] **端到端验证**：in-process mock worker（实现三方法语义：startJob 返回 taskId → RUNNING → SUCCESS；记录调用序列）→ 完整链路：WAITING fire(executorKind=rpcPoll) → dispatcher → 内嵌 worker scanner 认领 → RemoteJobInvoker startJob → 轮询 getJobStatus → task SUCCESS 写回 → fire 聚合（testE2E_rpcPoll_fullChain）；超时路径（mock worker 挂起 → 墙钟判定 → cancelJob + task TIMEOUT，testE2E_rpcPoll_wallClockTimeout）
- [x] **精确路由验证**：mock `IRpcServiceInvoker` 记录请求 header，断言 `HttpRpcPollTaskClient` 每次调用注入 `nop-svc-target-host = task.targetHost`（TestHttpRpcPollTaskClient.testStartJob_contract/testGetJobStatus_contract/testCancelJob_contract）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 端到端测试从用户入口（创建 fire/task）到最终输出（task/fire 终态 + 结果透传）完整跑通（Anti-Hollow #22）
- [x] **接线验证**：真实 app-engine.beans.xml 容器装配下 `IJobWorkerScanner`（JobWorkerScannerImpl）依赖链可解析、`nopJobInvoker_rpcPoll`（RemoteJobInvoker）与 `IRpcPollTaskClient`（HttpRpcPollTaskClient）bean 注册成功（TestJobDispatcherContainerWiring.testRpcPollWorkerChainWiredFromBeans）；端到端测试断言 dispatcher→scanner→invoker→client→worker→写回各环节被调用
- [x] **liveness 统一验证（Blocker-2 回归）**：远程任务与普通任务一致走 worker-liveness 链（workerInstanceId 不在集合 → SUSPICIOUS）；模拟 naming service 存活集合不含认领者的场景断言标记 SUSPICIOUS（testWorkerLiveness_remoteTaskMarkedSuspicious）
- [x] **cancel 接线验证（Blocker）**：`NopJobFireBizModel.cancelFire` → `IJobCancelHandler.cancelRunningTask` → 经 executorKind 解析 invoker → `cancelAsync` 被调用（testCancelFireNotifiesCancelHandlerForInFlightTasks；同时 DB 模式取消路径未回归——service 43 用例全绿）
- [x] **无静默跳过**：invoker 各分支（startJob 失败/超时/NOT_FOUND/并发终结/轮询瞬态失败）均有显式处理或显式失败，无空实现（Anti-Hollow #24）
- [x] 错误码契约：`nop.err.job.remote-invoke-failed`、`nop.err.job.remote-task-lost` 已定义并测试覆盖
- [x] 多 coordinator 并存语义：认领段乐观锁（tryLockTasksForExecute）既有测试覆盖（TestJobFireStoreRace/TestJobCoordinatorScanner）
- [x] `docs-for-ai/03-modules/nop-job.md` 更新（REST 模式说明、worker 契约、配置项）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 执行日志上报（可选行为）

Status: planned
Targets: `nop-job-service`、`nop-job-dao`（复用 Phase 1 的 `NopJobTaskLog`）、`nop-job-api`（worker 侧上报 client）、`nop-job-meta`（log-level dict）

- Item Types: `Fix | Proof`

- [ ] `nop-job-meta`：在 `_vfs/dict/job/` 目录新增 log-level dict 文件（TRACE/DEBUG/INFO/WARN/ERROR，设计 §3.8.1 引用；与 executor-kind 等同构的独立维护 dict，codegen 不重生成）
- [ ] `NopJobTaskLogBizModel.reportTaskLog`（批量端点，`/r/` 入口）：校验 instanceId（= taskId）/level/logTime，落库 `nop_job_task_log`（冗余展示列从 task 快照填充），返回批量接收结果
- [ ] worker 侧轻量上报 client：放 **`nop-job-api`**（`io.nop.job.api.log` 包，新增 `nop-http-api` 依赖——仅接口模块，worker 引入 nop-job-api 仍不含 dao/orm 链）；地址 = worker 侧配置 `nop.job.log.report-url`，未配置则功能显式关闭；失败 best-effort 不抛业务异常
- [ ] 测试：上报端点批量落库 + 按 instanceId 查询；未配置地址时 client 为显式关闭（可观测语义，非静默吞错）；上报失败不影响主链路（调用方不感知异常）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `reportTaskLog` 批量写入 `nop_job_task_log`，日志行按 `taskId` 可查询（focused test）
- [ ] 日志上报与任务状态机完全解耦：上报端点异常不影响 task/fire 状态流转（端到端测试断言）
- [ ] **无静默跳过**：未配置上报地址时 client 显式关闭（可观测日志/配置状态），不是吞异常的空实现
- [ ] 设计 §3.8 与实现一致；`docs-for-ai/03-modules/nop-job.md` 的日志上报说明同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 设计定稿与文档收口

Status: planned
Targets: `ai-dev/design/nop-job/remote-worker-design.md`、`docs-for-ai/03-modules/nop-job.md`

- Item Types: `Fix | Follow-up`

- [ ] `remote-worker-design.md` 状态从"草案"改为定稿（删除残留草案表述，Open Questions 中已定案项移除/标注）
- [ ] `docs-for-ai/03-modules/nop-job.md`：新增 REST 模式章节（启用方式、worker 三方法契约、日志上报配置、与 DB 模式共存规则）
- [ ] `docs-for-ai/INDEX.md`/`04-reference/source-anchors.md`：如涉及路由/锚点变更则同步
- [ ] 文档链接检查 `node ai-dev/tools/check-doc-links.mjs --strict`：本次变更文件 0 错误
- [ ] 全量构建 `./mvnw clean install -T 1C` 通过（或受影响的 nop-job 模块族 `./mvnw -pl :nop-job-coordinator,:nop-job-dao,:nop-job-service -am clean test`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 设计文档定稿且与 live 代码一致（无"Proposed/Current"残留）
- [ ] `docs-for-ai` 三处文档（模块页/INDEX/锚点）检查通过，`check-doc-links.mjs --strict` 对本次新增/修改文件 0 错误
- [ ] 构建验证：`./mvnw -pl :nop-job-coordinator,:nop-job-dao,:nop-job-service -am test`（及受影响模块）全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope confirmed live defects 已修复（本计划无已知 pre-existing defect，新增代码无空壳/静默跳过）
- [ ] 行为/契约结果已达成：`executorKind=rpcPoll` 端到端可用；`source_fire_id` 追溯生效；日志上报可选可用
- [ ] 必要 focused verification 已完成（各 Phase 测试 + 端到端 + 接线验证）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs（design 定稿、`docs-for-ai/03-modules/nop-job.md`、INDEX、source-anchors）已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）远程调度调用链（schedule→fire→task→scanner→invokerResolver→RemoteJobInvoker→IRpcPollTaskClient→worker→写回）运行时连通，（b）无空方法体/静默跳过/no-op
- [ ] `./mvnw -pl :nop-job-coordinator,:nop-job-dao,:nop-job-service -am test` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2254-nop-job-remote-worker-mode.md --strict` 退出码 0

## Deferred But Adjudicated

### worker 侧日志 SLF4J/logback appender

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 业务代码可显式调用上报 client 完成日志上报（Phase 4 已提供）；appender 只是便利组件，不改变契约与状态机语义
- Successor Required: `no`

### 日志表容量滚动清理

- Classification: `optimization candidate`
- Why Not Blocking Closure: V1 日志量可控（可选功能，未配置不上报）；`ix_nop_job_task_log_time` 索引已预留
- Successor Required: `no`

### worker in-memory 任务表磁盘持久化（PowerJob 本地 H2 式）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: NOT_FOUND→FAILED 是设计既定语义（worker 重启即任务失败，由超时链/轮询发现），不阻塞核心契约
- Successor Required: `no`

### `IRpcPollTaskClient` 类型化接口 codegen（api-model 集成）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 直接 `IRpcServiceInvoker.invokeAsync` 已满足契约；类型化接口是开发体验增强
- Successor Required: `no`

## Non-Blocking Follow-ups

- 日志上报端点鉴权策略（平台标准认证 vs token）——实施时按设计 §3.10 默认取平台标准认证 + 可选 token，无需额外决策
- 轮询段/认领段批次预算细调（默认值与现有 scanner 一致，性能调优留给压测后）
- bestFit 负载核算（`sumReservedCost`/`countInFlightTasks`）未排除 remote 归因任务：remote 任务在认领前以目标实例 id 归因，会短暂计入该实例预留负载（≤1 扫描周期，认领后覆盖为 coordinator id）——watch-only residual，不影响正确性
- remote schedule 未配 `executorKind=rpc` 时取消仅置 DB 状态、不远程中断（`DefaultJobCancelHandler` 解析不到 invoker 记 debug 日志）：fire/task 状态 CANCELED 可观测，属配置责任；后续可在创建期校验或提升日志级别——watch-only residual

## Closure

Status Note: 待执行完成后填写
Completed: 待定

Closure Audit Evidence:

- Reviewer / Agent: 待独立子 agent 执行
- Evidence: 待填写（各 Exit Criteria PASS/FAIL、checklist 工具退出码、Anti-Hollow 调用链追踪、deferred 分类检查）

Follow-up:

- 待填写
