# Coordinator & Runtime Concurrency / Recovery Hardening

> Plan Status: active
> Last Reviewed: 2026-08-04
> Draft Review: independent sub-agent review passed (Blocker B1 resolved + Majors M1-M5 resolved; re-review verdict YES, no remaining Blockers). Session ses_0360b79fdfferi1mudpSqhIVUl.
> Source: `ai-dev/audits/nop-stream-production/2026-08-02-2107-multi-audit-nop-stream-production.md` (P0 JobCoordinator race, P1 InputGate cross-thread mutation, P1 TaskManager permit leak, P1 SupervisionLoop zombie-task)
> Related: Execution order `{1}` of 3 — most foundational; unblocks Plans {2} and {3} by closing the coordinator/recovery races that corrupt state the other plans build on.

## Purpose

收口 nop-stream 控制面与运行时恢复路径上四个并发正确性 / 资源泄漏 / 静默跳过缺陷：JobCoordinator 恢复路径无同步（P0）、InputGate 被跨线程并发修改（P1）、TaskManager 重部署漏放 semaphore 许可（P1）、SupervisionLoop waitForTerminal 超时后静默重建导致 zombie task（P1）。这四个缺陷都在故障/恢复场景下破坏 exactly-once 与资源稳定性，是本 mission 生产硬化的核心目标。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 `2026-08-02-2107-multi-audit` 一致，已二次确认文件路径与行号）：

- **P0 JobCoordinator 恢复竞态**：`JobCoordinator.globalRecovery()`（`:889`）从两个并发来源可达：(a) `failureDetector` 单线程调度器上的 `detectFailures()`（`:868`），(b) RPC server 线程池上 `reportTaskStatus()` 收到 FAILED 报告且 `autoRecoverOnFailedReport=true`（`:207`，默认 true；`:201-202` Javadoc）。`globalRecovery`、`rotateFencingEpochAndRestore`（`:920` 调用）、`assignTasks` 三者均非 `synchronized`；`taskAssignmentMap` 是 `ConcurrentHashMap`、`allTaskLocations` 是 `ConcurrentHashMap.newKeySet()`（`Set<TaskLocation>`）但 clear→register→assign 多步序列不原子。两个并发 recovery driver 可交错 clear/put、向同一 TaskManager 推送不同 fencing epoch、对同一 subtask 用两个 attemptId 调用 `clusterRegistry.assignTask`。
- **P1 InputGate 跨线程修改**：`inFlightAlignments`（`LinkedHashMap`，`:90`）、`abortedBarriers`（`HashSet`，`:99`）、`blockedChannels`（`HashSet`，`:107`）均为非线程安全集合。task 线程在 `handleBarrierNonRecursive`（`:575-633`）和 `markFinishedChannel`（`:640-660`）中迭代/修改；checkpoint-coordinator 的超时/ACK 线程经 `GraphModelCheckpointExecutor.registerLocalAbortHandler`（`:850`）调用 `abortBarrierAlignment`（`:692-702`）并发修改同一集合。可抛 `ConcurrentModificationException`、丢失 barrier、永久 block channel。**可行性已确认**：abort 调用点（`:863`）同一 lambda 内已通过 `invokable.getMailboxExecutor().signalCancel()` 触达 mailbox，故 abort 经 mailbox control-mail 投递（方案 a）可行——`MailboxExecutor.getMailbox()` 返回 `TaskMailbox`，`TaskMailbox.put(Mail)` 允许任意线程调用（`TaskMailbox.java:18,51`），`Mail.control(...)` 是 CONTROL 优先级邮件工厂（`MailboxExecutor.java:91`）。
- **P1 TaskManager 许可泄漏**：`TaskManager.deployTask`（`:417-429`）。`:404` 已为新任务 `tryAcquire`（–1）；`:424` 释放旧 slot 许可（+1）并预置 `semaphoreReleased=true` 使旧任务 finally（`:713-715`）不再释放；`:428` 又 `acquireUninterruptibly()`（–1）。净 –1（一次无匹配释放的额外 acquire）。每次命中已占用 slot 的 recovery 永久收缩有效容量 1，`capacity` 次后节点 wedged。
- **P1 SupervisionLoop zombie task**：`SupervisionLoop.waitForTerminal`（`:463-477`）。协作式 cancel 后轮询 ≤10s；若旧任务卡在不可中断段，等待超时后 `LOG.warn` 并 **静默 fall-through**，调用方在 `:431-440` 用 `deepCopy()` 重建任务并 `executor.submitTask(newTask)`，两个生产者线程写同一 `ResultPartition` 的 `LinkedBlockingQueue`，race on `currentMaterializationEpoch`，破坏 exactly-once。**注**：`ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`（`NopStreamErrors.java:375`）**已被使用**于 `SupervisionLoop.java:255` 的 region-restart 预算耗尽路径（不同于 `waitForTerminal` 超时），故本 Phase 须引入**新的**错误码以避免可观测性混淆。`SupervisionLoop` 由 `GraphModelCheckpointExecutor.submitAndRun`（`:813-820`）调用，该 executor **无** `JobCoordinator` 引用、**无** `globalRecovery()` 调用点，故 local/embedded 路径下异常经 `env.execute()` 上浮；distributed 路径下 task FAILED 报告经 `reportTaskStatus` + `autoRecoverOnFailedReport` 触发 coordinator 恢复（见 design）。

## Goals

- JobCoordinator 三个恢复方法互斥：同一时刻只有一个 recovery driver 推进 rotate-epoch→register→assign 序列；迟到的调用方短路返回（看到新 epoch 已轮转）。
- InputGate 的可变对齐状态变为单线程所有权：abort 信号经 mailbox 控制邮件投递到 task 线程（对齐 design mailbox-thread 不变量），或集合替换为并发安全结构 + 同步迭代。
- TaskManager `deployTask` 重占用 slot 路径的 semaphore 许可账目守恒（无净增减）。
- SupervisionLoop `waitForTerminal` 超时后 fail-loud（引入独立错误码 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`，区别于已用于 region-restart 预算耗尽的 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`），local 路径经 `env.execute()` 上浮、distributed 路径经 FAILED 报告 + `autoRecoverOnFailedReport` 触发 coordinator 恢复，不再静默重建 zombie。
- 每个修复配一条针对性的回归/并发测试，证明此前断裂的路径被守住。

## Non-Goals

- Checkpoint / state backend / CEP 状态正确性（Plan {2}）。
- SPI / 文档 drift、`_module` 标记、空心测试（Plan {3}）。
- 全部 P2 项（已归入 roadmap Follow-up Backlog）。
- 重写 JobCoordinator 为单线程 actor 模型（仅引入互斥监控，不重构整体架构）。

## Scope

### In Scope

- `JobCoordinator.java`：`globalRecovery` / `rotateFencingEpochAndRestore` / `assignTasks` 互斥 + 迟到短路。
- `InputGate.java`：对齐状态单线程化（mailbox 投递 abort）或并发安全集合 + 同步迭代。
- `TaskManager.java`：移除重占用 slot 路径的额外 `acquireUninterruptibly`。
- `SupervisionLoop.java`：`waitForTerminal` 超时分支 fail-loud。
- 每项的针对性回归/并发测试。

### Out Of Scope

- `CheckpointCoordinator.setTasksToAcknowledge` 竞态（P2，归 backlog）。
- `JobCoordinator.assignTasks` mid-iteration RPC 抛错的不一致（P2，归 backlog）。
- `JobCoordinator.failJob` 不取消 in-flight task（P2，归 backlog）。

## Execution Plan

### Phase 1 - JobCoordinator 恢复路径互斥化

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`

- Item Types: `Fix | Proof`

- [ ] 使 `globalRecovery`、`rotateFencingEpochAndRestore`、`assignTasks` 三者互斥（单一 `ReentrantLock` 或 `synchronized` 监控）。**临界区范围**：`restartCount.incrementAndGet()` + `maxRestarts` 判定 + `recoveryGen.incrementAndGet()` + epoch 推导 + `clear→registerCoordinator→updateFencingToken→taskAssignmentMap/allTaskLocations 重置→epoch 落定` 全部在锁内；**RPC fan-out（`rpc.deployTask`/`receiveAssignment`）在锁外**——锁内先把 assignment 列表物化到局部变量并 `put` 入 `taskAssignmentMap`，释放锁后再逐 subtask 发 RPC（避免锁跨 N×TaskManager 阻塞 IO；若 RPC 抛错按 P2 backlog 的 mid-iteration 不一致处理，属 transient、下次 globalRecovery 清理）
- [ ] **迟到短路守卫**：锁内 acquire 后重检——若 `fencingEpoch.get()` 已 >= 本调用方推导的 newEpoch（即另一 driver 已轮转到同/更新 epoch），则 `restartCount` 不递增（或递增后回滚）、直接 WARN 短路返回，不重复 clear/register/assign。`restartCount`/`recoveryGen` 的 `incrementAndGet` 须在锁内守卫通过后才确定（迟到方回滚其 phantom 递增，或改用先守卫后递增）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `globalRecovery`/`rotateFencingEpochAndRestore`/`assignTasks` 在仓库中可观察到互斥构造（`ReentrantLock`/`synchronized`）；临界区不含 RPC fan-out（RPC 在锁外）
- [ ] 迟到短路守卫在仓库中可观察到（重检 `fencingEpoch`，迟到方不重复推进序列）
- [ ] 新增并发回归测试：用 `CountDownLatch`/阻塞式 mock 强制两个线程（failure-detector 模拟 + RPC reportTaskStatus FAILED 模拟）同时进入 `globalRecovery`，断言 `clusterRegistry.assignTask` 对同一 subtask 不出现两个 attemptId、fencing epoch 只被轮转一次（`restartCount` 增量 == 1 而非 2，或 `rotateFencingEpochAndRestore` 的 clear 序列只执行一次）
- [ ] **接线验证**：测试中验证 `assignTasks` 在互斥下被调用且 clear→register→assign 序列不被交错（计数器/标志位断言序列原子性）
- [ ] **无静默跳过**：迟到短路返回路径有明确的 WARN 日志，非无记录 fall-through
- [ ] 若此 Phase 改变恢复契约语义：`ai-dev/design/nop-stream/checkpoint-design.md` / `failover-design.md` 同步恢复互斥约定；否则明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - InputGate 对齐状态单线程化

Status: planned
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`

- Item Types: `Fix`

- [ ] 采用方案 (a)（可行性已确认）：将 `GraphModelCheckpointExecutor.registerLocalAbortHandler`（`:850`）中对 `inputGate.abortBarrierAlignment(abortedCheckpointId)` 的直接调用，替换为经 task mailbox 投递的 CONTROL 优先级邮件——`invokable.getMailboxExecutor().getMailbox().put(Mail.control(() -> inputGate.abortBarrierAlignment(abortedCheckpointId), "abort-barrier"))`（`TaskMailbox.put` 允许任意线程调用，CONTROL 邮件在 task 线程 `processAvailableMails` 时先于 NORMAL 排空，见 `ai-dev/design/nop-stream/mailbox-design.md` 与 `MailboxExecutor.java:91` 的 `signalCancel` 同型用法）。使 `inFlightAlignments`/`abortedBarriers`/`blockedChannels` 三个集合恢复 task 线程单线程所有权
- [ ] 消除 task 线程迭代 `inFlightAlignments` 与 abort 线程并发修改之间的 `ConcurrentModificationException` 路径

Exit Criteria:

- [ ] `abortBarrierAlignment` 的调用经 mailbox CONTROL 邮件在 task 线程内执行（仓库可观察 `Mail.control(...)` + `getMailbox().put(...)` 替换原直接调用）；三个集合不再被跨线程并发修改
- [ ] 新增回归测试：task 线程迭代对齐状态时，另一线程经 mailbox 投递 abort，断言不抛 `ConcurrentModificationException`、不丢失/重复 barrier、channel 不被永久 block
- [ ] **接线验证**：测试或代码审查确认 `GraphModelCheckpointExecutor.registerLocalAbortHandler`（`:850`）的 abort 信号确实经 `Mail.control` + `TaskMailbox.put` 到达 task 线程内的 `InputGate.abortBarrierAlignment`（而非仅集合类型变更）
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` abort 路径段落更新为 mailbox-control-mail 投递（符合 mailbox-design.md 的 task-thread 不变量）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - TaskManager 许可泄漏修复

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`

- Item Types: `Fix | Proof`

- [ ] 移除 `deployTask` 重占用 slot 路径（`:428`）的额外 `capacitySemaphore.acquireUninterruptibly()`（`:404` 的 acquire 已为新任务提供许可；`:424` 释放为旧 slot 平衡）

Exit Criteria:

- [ ] `TaskManager.deployTask` 重占用 slot 路径的 semaphore 许可账目守恒（代码审查：新 acquire 1 次 + 旧 release 1 次 = 净 0 变化对旧 slot，净 -1 对新 slot 由 `:404` 承担）
- [ ] 新增回归测试（锚定现有 taskmanager 测试目录）：对同一 occupied slot 连续 `deployTask` `capacity+1` 次，断言 `capacitySemaphore.availablePermits()` 稳定（不随次数单调下降）
- [ ] **无静默跳过**：N/A（移除一行，无新增分支）
- [ ] `No owner-doc update required`（纯内部许可账目修正，无契约/行为语义变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - SupervisionLoop waitForTerminal fail-loud

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java`

- Item Types: `Fix | Proof`

- [ ] 在 `NopStreamErrors` 新增一个**独立**错误码（如 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`）以区别于已被 `:255` 使用的 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`（region-restart 预算耗尽），避免可观测性混淆
- [ ] 修改 `waitForTerminal`（`:463-477`）超时分支：不再静默 fall-through，改为抛 `StreamException(ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT)`（local/embedded 路径下经 `GraphModelCheckpointExecutor.submitAndRun` → `env.execute()` 上浮给调用方；该 executor 无 `JobCoordinator` 引用、无 `globalRecovery()` 调用点，故 local 路径即 fail-loud 到 API）。distributed/HA 路径下，task 异常转化为 FAILED 报告经 `reportTaskStatus` + `autoRecoverOnFailedReport`（默认 true）触发 coordinator 恢复——此处只须保证不静默重建第二个 task 实例，恢复语义由 coordinator 侧承担

Exit Criteria:

- [ ] `waitForTerminal` 超时路径在仓库中可观察到 fail-loud（抛 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`），不再静默 WARN 后继续重建
- [ ] 新增错误码在 `NopStreamErrors` 中定义，且与 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED` 区分（二者在 ops dashboard 上可辨）
- [ ] 新增回归测试（锚定现有 `TestSupervisionLoopRestartLimitConfig.java` fixture）：构造一个卡在不可中断段的 task（mock `isFinished()` 持续返回 false 超过 10s budget），触发 region restart，断言抛出 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT` 而非静默重建并提交第二个 task 实例（断言 `executor.submitTask` 未被再次调用）
- [ ] **无静默跳过**：超时分支显式失败（plan guide #24 满足）
- [ ] `ai-dev/design/nop-stream/failover-design.md` 更新 waitForTerminal 超时语义（fail-loud 取代静默 fall-through）+ 区分两个错误码
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 四个 in-scope 确认 live defect（JobCoordinator race / InputGate cross-thread / TaskManager permit / SupervisionLoop zombie）均已修复
- [ ] 每项均有针对性回归/并发测试证明此前断裂路径被守住（非仅"编译通过"）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）互斥/abort 投递/许可账目/fail-loud 在运行时确实生效（不只是类型/方法存在），（b）无空方法体/静默跳过作为正常实现
- [ ] **端到端验证（Rule #22）**：至少一条 E2E 测试覆盖"checkpoint 进行中触发 task 故障 → 恢复运行 → sink 输出相对 source 恰好一次"（可锚定现有 recovery/ exactly-once E2E fixture，如 `TestMultiJvmExactlyOnceRecovery` 或 in-process recovery 测试，断言四个修复未通过未预见交互破坏 exactly-once）
- [ ] `./mvnw compile -pl nop-stream -am -T 1C`
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。）

## Non-Blocking Follow-ups

- 本 plan 的 P2 邻接项（`setTasksToAcknowledge` 竞态、`assignTasks` mid-iteration RPC 不一致、`failJob` 不取消 in-flight task）已归入 roadmap Follow-up Backlog，不阻塞本 plan closure。

## Closure

Status Note: （关闭时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写，含每条 Exit Criterion / Closure Gate 验证结果、check-plan-checklist 与 scan-hollow 退出码）

Follow-up:

- （关闭时填写；confirmed live defect 不得出现在这里）
