# 25 — Per-task 故障检测 + execution state machine + restart 策略

> Plan Status: active
> Last Reviewed: 2026-07-26
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 25 (G52, G54, G56, G58); `ai-dev/design/nop-stream/01-architecture-baseline.md` §五; `ai-dev/analysis/nop-stream/08-gap-analysis.md` (G55=region scheduling, G56=retry/attempt tracking); `ai-dev/analysis/nop-stream/07-distributed-comparison.md`
> Mission: nop-stream-production
> Work Item: 25 (Phase 1 — 分布式运行时基础)
> Related: **Stage 24** plan (`2026-07-26-0207-1-deployment-plan-discovery.md`) — **硬前置**：Stage 24 Phase 1（DeploymentPlan subtask→node 映射 + JobCoordinator.assignTasks 消费映射）必须先 landing，因两个 plan 都改 `JobCoordinator.assignTasks()`（`JobCoordinator.java:195-255`）；Stage 20 (`2026-07-25-2200-2-partial-subtask-recovery.md`, subtask-granular restore)

## Purpose

把故障检测从「仅节点级 lease 超时 → 全局 globalRecovery」升级到「per-task 终态上报 + attempt 历史追踪 + 受限 restart 策略」，使单个 task 失败/卡死能被 coordinator 感知并按策略重启，而非仅靠节点 lease 不可观测地全局重启。为 Stage 27（targeted failover）提供 attempt/retry 数据底座。

## Current Baseline

经 live 仓库核对：

- **Task 状态机**（`nop-stream-core/.../execution/Task.java:70`）= 5 态 `{CREATED, RUNNING, COMPLETED, FAILED, CANCELED}`，**无 CANCELING/SCHEDULED/DEPLOYING/RECOVERING**。`Task.cancel()`（`:259`）仅处理 `CREATED→CANCELED`，对 RUNNING 无效。
- **SubtaskTask 状态机**（`SubtaskTask.java:31`）= 6 态 `{CREATED, RUNNING, CANCELING, COMPLETED, FAILED, CANCELED}`。`cancel()`（`:104`）= RUNNING→CANCELING CAS + `Thread.interrupt()`。**无心跳/超时/attempt 计数**。
- **TaskAttempt 类不存在**：grep `TaskAttempt|ExecutionAttempt` = 零匹配。`attemptId` 仅是 `TaskAssignment.attemptId`(UUID 字符串) 与 `TaskManager.RunningTask` 的 UUID，每次分配 `UUID.randomUUID()` 刷新（`JobCoordinator.java:222`、`EmbeddedDistributedExecutor.java:135`），**无 attempt 编号、无历史**。
- **ClusterRegistry.assignTask 覆盖式写入**：`InMemoryClusterRegistry`（`:117`）用 `Map.put` 覆盖；`JdbcClusterRegistry`（`:187`）用 DELETE-then-INSERT 覆盖。**新 attempt 覆盖旧 assignment，无历史保留**。
- **故障检测仅节点级**：
  - `TaskManager.heartbeat()`（`taskmanager/TaskManager.java:189`）每 5s → `clusterRegistry.renewLease(15s)`。
  - `JobCoordinator.detectFailures()`（`coordinator/JobCoordinator.java:372`）每 5s 检查每个 `TaskAssignment` 的 nodeId 是否在 `getActiveNodes()`，缺失即 `globalRecovery()`（`:400`）。
  - **per-task 失败检测不存在**：task 失败但所在节点未宕机时，coordinator 感知不到（失败只在 `TaskManager.completedTasks` 本地可见，不回传 coordinator）。
- **恢复是全量**：`JobCoordinator.globalRecovery()`（`:415`）= 新 fencing token + 清空全部 assignment + 全部 task 重分配。**无 per-task / partial 重启**。
- **无 JobStatus**：`JobCoordinator` 只有 `running: boolean`（`:102`），无 job 级 FAILED 状态/计数。
- **`IStreamCoordinatorRpcService`（`IStreamCoordinatorRpcService.java`）只有 1 方法** `receiveCheckpointAck(CheckpointAckMessage)`，**无 per-task 状态上报方法**。
- **cancel 路径**（G58）：`SubtaskTask.cancel`（interrupt-based）；分布式 `RunningTask.cancel()`（`TaskManager.java:507`）= `canceled=true` + `future.cancel(true)`（纯 interrupt）。mailbox cooperative cancel（`MailboxExecutor.signalCancel()`，Stage 17）目前只在 LOCAL 路径 `GraphModelCheckpointExecutor.java:683` 使用，RunningTask 持有 `StreamTaskInvokable inv`（`TaskManager.java:428`）但未调用其 mailbox。

### Gap 定义校正（对照权威 `08-gap-analysis.md`）

- **G52** = 无 per-task 心跳/超时（task 卡死但节点活着时不可观测）— **in scope**。
- **G54** = task 状态机缺中间态 — **in scope**。
- **G56** = 无 execution retry/attempt tracking — **in scope**（attempt 追踪 + restart 策略都属于 G56）。
- **G58** = cancel 规范化 + Task 状态补齐 — **in scope**。
- **G55 = 无 region-aware scheduling — 明确 Out Of Scope**（Stage 27/44），本 plan 不声称关闭 G55。

### 真正剩余的 gap（in scope）

- **G52**：无 per-task liveness 检测。
- **G54**：Task/SubtaskTask 状态机缺中间态。
- **G56**：无 attempt 追踪（编号+历史）、无 restart 策略（无限全局重启）。
- **G58**：cancel 路径规范化。

## Goals

- Coordinator 能感知单个 task 的终态（完成/失败）（per-task 状态上报路径），而非仅靠节点 lease。
- Task/SubtaskTask 状态机补齐部署/恢复中间态，使生命周期可观测、状态转换受控（含显式转换表）。
- 引入 attempt 追踪（attempt 编号 + 历史），ClusterRegistry 保留 attempt 历史（新增查询 API，非覆盖式）。
- 引入 global restart strategy（重试计数 + 上限），全局重启可被「达到上限则 job FAILED」约束。
- normalize cancel 路径（G58），`Task.cancel` 对 RUNNING 有效；分布式 RunningTask 与 mailbox cooperative cancel 对齐。

## Non-Goals

- **G55 region-aware scheduling / targeted / region failover（Stage 27 / 44）不在本 plan**。本 plan 的重启仍是「全局重启但带 attempt 追踪与重试上限」；只重启失败子集的 scoped 重启留给 Stage 27。
- **跨 JVM（Stage 39）**：per-task 状态上报仍基于进程内 RPC（local `IStreamCoordinatorRpcService` 实现），跨 JVM 远程上报留给 Stage 39。
- Flink `ExecutionGraph`/`ExecutionVertex`/`ExecutionState` 三层调度抽象（vision §十排除）。
- per-task 心跳的细粒度 progress 上报（仅做终态 + liveness；progress 粒度细化是优化项）。

## Scope

### In Scope

- `IStreamCoordinatorRpcService` 新增 per-task 终态上报方法（+ DTO + 触发点）。
- Task/SubtaskTask 状态机扩展（含显式转换表）。
- attempt 追踪：ClusterRegistry 保留 attempt 历史（新查询 API + assignTask 携带 attempt 编号）。
- global restart strategy（重试计数 + 上限 + job FAILED）。
- cancel 路径规范化（G58），含 RunningTask 与 mailbox 对齐。

### Out Of Scope

- G55 region/targeted-scoped 重启（Stage 27/44）。
- 跨 JVM 状态上报传输（Stage 39）。
- ExecutionGraph 三层抽象（vision 排除）。
- per-task 进度细粒度上报（优化项）。

## Execution Plan

### Phase 1 — Task 状态机扩展 + attempt 追踪（G54, G56-half）

Status: planned
Targets: `nop-stream-core/.../execution/Task.java`, `SubtaskTask.java`; `ClusterRegistry.java`, `InMemoryClusterRegistry.java`, `JdbcClusterRegistry.java`, `TaskAssignment.java`

> **Stage 24 硬前置**：本 Phase 改 `JobCoordinator.assignTasks()` 的 attempt 计数与 ClusterRegistry.assignTask 写入；Stage 24 Phase 1 改同一方法的 DeploymentPlan 映射消费。**Stage 24 Phase 1 必须先 landing**，避免对同一 60 行方法并发修改。本 Phase 假定 Stage 24 已让 assignTasks 消费 DeploymentPlan 映射；本 Phase 在其上叠加 attempt 编号。

- Item Types: `Fix`

- [ ] 扩展 Task/SubtaskTask 状态机补齐中间态（SCHEDULED/DEPLOYING/RECOVERING），向后兼容（现有终态不变）。**Task 补 CANCELING 态**（当前只有 SubtaskTask 有 CANCELING，Task 5 态无 CANCELING——为兑现 G58「统一 cancel 语义」目标，Task 与 SubtaskTask 统一为同一转换模型）。**显式转换表**（伪代码规格，回答"应发生什么"）：
  - 正常部署：`CREATED → SCHEDULED → DEPLOYING → RUNNING → COMPLETED`
  - 恢复：`FAILED/任意非终态 → RECOVERING → SCHEDULED → DEPLOYING → RUNNING`
  - 取消（**统一经 CANCELING 中间态**）：任意非终态 → `CANCELING → CANCELED`（含 SCHEDULED/DEPLOYING/RECOVERING/RUNNING；`cancel()` 把当前态 CAS 到 CANCELING，由 run 闭环或 cancel 闭环推进到 CANCELED）
  - 失败：`RUNNING/SCHEDULED/DEPLOYING/RECOVERING → FAILED`
  - 终态（COMPLETED/FAILED/CANCELED）为吸收态，**任何从终态转出的转换非法 → 抛异常（#24）**
  - 自环非法：`RECOVERING → RECOVERING`、`SCHEDULED → SCHEDULED` 等非法（抛异常）
  - 每个合法转换有单测；非法转换枚举覆盖（含终态转出、自环、跨阶段跳跃如 CREATED→RUNNING）。
- [ ] 引入 attempt 追踪：`ClusterRegistry.assignTask` 携带递增 `attemptNumber`（attempt 编号**由 JobCoordinator 侧维护**：每个 subtask 的重试计数器，全局重启时递增）。`TaskAssignment` 增 `attemptNumber` 字段。
- [ ] ClusterRegistry 记录改为**保留历史**：新增 `getAttemptHistory(jobId, vertexId, subtaskIndex)` 查询 API（返回 `List<TaskAssignment>`，按 attemptNumber 单调递增，非覆盖式）；assignTask 不再覆盖（InMemory 改为 list/append 语义，Jdbc 改为不 DELETE 旧行或写入历史表）。

Exit Criteria:

- [ ] Task/SubtaskTask 新增中间态有单测覆盖转换表每个合法转换 + 非法转换被拒绝（#24）。
- [ ] ClusterRegistry 保留 attempt 历史：同一 subtask 多次 attempt 不互相覆盖，`getAttemptHistory` 返回单调递增 attemptNumber 列表，有单测覆盖 InMemory + Jdbc 两实现。
- [ ] **接线验证**（#23）：单测断言 `JobCoordinator.assignTasks()` 确实使用新 attemptNumber API（非仍用裸 UUID），且 ClusterRegistry.assignTask 新签名被调用。
- [ ] **无静默跳过**（#24）：非法状态转换抛异常而非静默忽略；assignTask 历史保留失败显式处理。
- [ ] owner-doc：`01-architecture-baseline.md` §五控制面角色（TaskAttempt attempt 编号角色）已更新。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 — Per-task 终态上报 + liveness 检测（G52）

Status: planned
Targets: `IStreamCoordinatorRpcService.java`, `IStreamTaskRpcService.java`（如需）, `TaskManager.java`（`RunningTask.run()` `:446-488`）, `JobCoordinator.java`

- Item Types: `Fix`

- [ ] **新增 per-task 终态上报 RPC**：`IStreamCoordinatorRpcService` 新增 `reportTaskStatus(TaskStatusReport)` 方法 + `TaskStatusReport` DTO（含 jobId/vertexId/subtaskIndex/attemptNumber/terminalState[COMPLETED|FAILED]/errorCause/lastProgressTime）。**触发点**：`RunningTask.run()` 的 `finally` 块（`TaskManager.java:472` 附近）在终态时调用 coordinator 代理上报。
- [ ] `JobCoordinator` 接收 `reportTaskStatus`：维护 per-subtask liveness；task FAILED（节点存活）时在窗口内感知并触发恢复。
- [ ] **liveness 信号 + 载体（已裁定，不留 open）**：
  - **writer（覆盖全部 4 个 TaskRole）**：`lastProgressTime`（volatile 时间戳，位于 invokable 级）。更新点：MIDDLE/SINK 在 `processInputGate()`（`StreamTaskInvokable.java:394`，私有，仅这两个角色进入）每轮；**SOURCE/SELF_CONTAINED 在 source 数据采集/发射路径**（`invokeSource()`/`invokeSelfContained()` 经 `sourceOp.run()`，更新点为 `SourceContext.collect()` 每条记录或 sourceOp 拉取循环）——否则健康的 SOURCE 会被误判卡死。四个角色均须有可验证的更新点。
  - **carrier**：复用现有 `TaskManager.heartbeat()`（`:189`，每 5s，当前只调 `renewLease`）。**新增 coordinator RPC 方法** `IStreamCoordinatorRpcService.reportNodeTaskLiveness(nodeId, List<TaskProgress>)`（`TaskProgress` = {vertexId, subtaskIndex, attemptNumber, lastProgressTime}）；`heartbeat()` 在 `renewLease` 后读取本节点各 RunningTask 的 `lastProgressTime` 并调用该方法上报。**null-check 防御**：`RunningTask.invokable`（`:428`，volatile，由 `setInvokable()` `:498` 延迟设置，有 30s `waitForInvokable` 窗口 `:491`）未设置时 heartbeat 跳过该 task 的 liveness 上报（不读 `invokable.lastProgressTime`，不抛 NPE），与 Phase 3 cancel 的 null-check 一致。
  - **检测**：coordinator 对 `lastProgressTime` 早于 `taskTimeout`（默认 60s 可配）的 task 判定卡死并触发恢复。**不引入新的 task 级心跳线程**（piggyback 现有节点心跳 + 新 liveness RPC）。
- [ ] 现有节点级 lease 检测（`detectFailures`）保留作兜底，与 per-task 检测并存。

Exit Criteria:

- [ ] per-task 失败（task 抛异常但节点存活）能被 coordinator 在超时窗口内感知并触发恢复，有 E2E 测试（注入 task 失败，断言 coordinator 收到 `reportTaskStatus` FAILED 并响应）。
- [ ] **端到端验证**（#22）：从 task 失败 → RunningTask finally 上报 → coordinator 感知 → 触发恢复 的完整路径跑通。
- [ ] **接线验证**（#23）：测试断言 `RunningTask.run()` finally 确实调用 `reportTaskStatus`（mock verify），coordinator 确实处理上报消息（非仅接收丢弃）。
- [ ] **无静默跳过**（#24）：`reportTaskStatus` 上报失败显式处理（重试或告警，不 `catch{}` 吞掉）；上报 DTO 在未实现分支抛异常。
- [ ] owner-doc：`01-architecture-baseline.md` §五 + `07-distributed-comparison.md` 故障检测维度已更新。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 — Global restart strategy + cancel 规范化（G56-half, G58）

Status: planned
Targets: `JobCoordinator.java`, `Task.java`, `SubtaskTask.java`, `TaskManager.java`

- Item Types: `Fix | Decision`

- [ ] **JobStatus**：`JobCoordinator` 引入 job 级终态（FAILED）；新增 `failJob(Throwable cause)` 方法，可观测效果：停止 failureDetector、标记 job 终态、阻止后续 assignTasks。
- [ ] **global restart strategy**：重试计数器**位于 JobCoordinator，仅 `globalRecovery()` 递增**（明确：Stage 27 的 scoped 重启不走 globalRecovery，需自带 per-region 计数器——本 plan 计数器为 global-only，记录于 Deferred 给 Stage 27）。达 `maxRestarts` 上限 → `failJob`（非无限重启）。
- [ ] **cancel 规范化（G58）**：统一 `Task.cancel`/`SubtaskTask.cancel`/`RunningTask.cancel` 状态转换语义（二者均经 CANCELING 中间态，见 Phase 1 转换表）。具体：`Task.cancel` 补 CANCELING 态后对 RUNNING 有效（G58 缺口闭合）；分布式 `RunningTask.cancel()` 在 `future.cancel(true)` 之前先调用 `invokable.getMailboxExecutor().signalCancel()`（RunningTask 持有 `StreamTaskInvokable invokable`，`TaskManager.java:428`；`getMailboxExecutor()` 见 `StreamTaskInvokable.java:262`），与 Stage 17 mailbox cooperative cancel 对齐（镜像 LOCAL 路径 `GraphModelCheckpointExecutor.java:683`）。**处理 cancel-before-invokable 竞态**：`invokable` 为 volatile、由 `setInvokable()`（`:498`）延迟设置（30s `waitForInvokable` 窗口 `:491`）；`RunningTask.cancel()` 须 null-check `invokable`（未设置时仅做状态转换 + `future.cancel(true)` + `invokableLatch.countDown()`，跳过 mailbox 调用，不抛 NPE）。

Exit Criteria:

- [ ] restart strategy 单测：超过 maxRestarts 后 `failJob` 被调用（job 转 FAILED）而非继续重启。
- [ ] **接线验证**（#23）：测试断言 `globalRecovery()` 在计数达上限时确实被阻断/转 `failJob`（计数器断言，非绕过 RestartStrategy）。
- [ ] cancel 规范化：`Task.cancel` 对 RUNNING 有效（G58）；CANCELING→CANCELED 转换在所有路径一致，单测覆盖；`RunningTask.cancel()` 调用 mailbox `signalCancel`（#23 mock verify）。
- [ ] **端到端验证**（#22）：task 多次失败 → 重启达上限 → job FAILED 的完整路径跑通。
- [ ] **无静默跳过**（#24）：达上限时显式转 FAILED 并带原因，不静默停止。
- [ ] owner-doc：`01-architecture-baseline.md` §五（restart strategy + cancel + JobStatus）已更新。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] G52：per-task 终态上报 + liveness 检测端到端成立（task 失败/卡死可被 coordinator 感知）。
- [ ] G54：Task 状态机含中间态且转换受控（转换表覆盖）。
- [ ] G56：attempt 追踪 + 历史保留（ClusterRegistry 不再覆盖）+ global restart 上限。
- [ ] G58：cancel 路径规范化，`Task.cancel` 对 RUNNING 有效，RunningTask 与 mailbox 对齐。
- [ ] **未声称关闭 G55**（G55 region scheduling 仍属 Stage 27/44）。
- [ ] 不存在被静默降级的 in-scope gap。
- [ ] 受影响 owner docs 已同步到 live baseline。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 验证（a）RunningTask→coordinator 上报路径运行时连通，（b）restart 上限真实触发 failJob，（c）cancel 真实触发 mailbox signalCancel，（d）无空方法体/静默跳过。
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（或仅含 pre-existing baseline 发现）。

## Deferred But Adjudicated

### G55 region-aware scheduling / targeted failover

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: G55（region scheduling）是 Stage 27（targeted failover）/ Stage 44（region failover）的范畴；本 plan 的重启是「全量但带 attempt 追踪 + 重试上限」，scoped 重启是独立调度维度。本 plan 明确不声称关闭 G55。
- Successor Required: yes
- Successor Path: Stage 27 (`27-targeted-failover`), Stage 44 (`44-region-failover`)

### per-region restart 计数器

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 的 restart 计数器为 global-only（仅 globalRecovery 递增）。Stage 27 引入 scoped 重启（不走 globalRecovery）需自带 per-region 计数器；当前 global baseline 下 global 计数器已足够约束无限重启。
- Successor Required: yes
- Successor Path: Stage 27 (`27-targeted-failover`)

## Non-Blocking Follow-ups

- restart strategy 配置项（固定次数 / 指数退避）的运行时调参（当前固定上限足够）。
- per-task 进度上报粒度细化（当前 liveness 足够）。

## Closure

Status Note: <<关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<task id / session id>>
- Evidence: <<每条 Exit Criterion + Closure Gate 的验证结果，live code path / test name>>
