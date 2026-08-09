# Coordinator & Runtime Concurrency / Recovery Hardening

> Plan Status: completed
> Last Reviewed: 2026-08-07
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

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`

- Item Types: `Fix | Proof`

- [x] 使 `globalRecovery`、`rotateFencingEpochAndRestore`、`assignTasks` 三者互斥（单一 `ReentrantLock` 或 `synchronized` 监控）。**临界区范围**：`restartCount.incrementAndGet()` + `maxRestarts` 判定 + `recoveryGen.incrementAndGet()` + epoch 推导 + `clear→registerCoordinator→updateFencingToken→taskAssignmentMap/allTaskLocations 重置→epoch 落定` 全部在锁内；**RPC fan-out（`rpc.deployTask`/`receiveAssignment`）在锁外**——锁内先把 assignment 列表物化到局部变量并 `put` 入 `taskAssignmentMap`，释放锁后再逐 subtask 发 RPC（避免锁跨 N×TaskManager 阻塞 IO；若 RPC 抛错按 P2 backlog 的 mid-iteration 不一致处理，属 transient、下次 globalRecovery 清理）
- [x] **迟到短路守卫**：锁内 acquire 后重检——若 `fencingEpoch.get()` 已 >= 本调用方推导的 newEpoch（即另一 driver 已轮转到同/更新 epoch），则 `restartCount` 不递增（或递增后回滚）、直接 WARN 短路返回，不重复 clear/register/assign。`restartCount`/`recoveryGen` 的 `incrementAndGet` 须在锁内守卫通过后才确定（迟到方回滚其 phantom 递增，或改用先守卫后递增）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `globalRecovery`/`rotateFencingEpochAndRestore`/`assignTasks` 在仓库中可观察到互斥构造（`ReentrantLock`/`synchronized`）；临界区不含 RPC fan-out（RPC 在锁外）
- [x] 迟到短路守卫在仓库中可观察到（重检 `fencingEpoch`，迟到方不重复推进序列）
- [x] 新增并发回归测试：用 `CountDownLatch`/阻塞式 mock 强制两个线程（failure-detector 模拟 + RPC reportTaskStatus FAILED 模拟）同时进入 `globalRecovery`，断言 `clusterRegistry.assignTask` 对同一 subtask 不出现两个 attemptId、fencing epoch 只被轮转一次（`restartCount` 增量 == 1 而非 2，或 `rotateFencingEpochAndRestore` 的 clear 序列只执行一次）
- [x] **接线验证**：测试中验证 `assignTasks` 在互斥下被调用且 clear→register→assign 序列不被交错（计数器/标志位断言序列原子性）
- [x] **无静默跳过**：迟到短路返回路径有明确的 WARN 日志，非无记录 fall-through
- [x] 若此 Phase 改变恢复契约语义：`ai-dev/design/nop-stream/checkpoint-design.md` / `failover-design.md` 同步恢复互斥约定；否则明确写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - InputGate 对齐状态单线程化

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`, `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`

- Item Types: `Fix`

> **实施裁正（approach deviation，记录于本 plan + checkpoint-design.md §2.8.1 D3）**：原计划方案 (a)（abort 经 task mailbox CONTROL 邮件投递）经实施验证**不可行**——`InputGate.read()` 在 barrier 对齐期间阻塞，仅在调用方 `processInputGate` 的循环顶部排空 mailbox，故经 mailbox 投递的 abort 无法解除被 abort 的对齐的阻塞，会使 epoch-precise abort 死锁至 `barrierAlignmentTimeout`（30s）后抛异常（回归）。signalCancel 可用 mailbox 是因其配合 `task.cancel()` 的**中断**解阻塞，非 mailbox 排空；epoch-precise 分支无中断。改用方案 (b)（并发安全集合），零回归地达成实际目标（三个集合跨线程访问不再 CME / 不丢 barrier / 不永久阻塞 channel）。

- [x] ~~采用方案 (a)（mailbox 投递）~~ → **改用方案 (b)**：将 `InputGate` 的 `inFlightAlignments`（`LinkedHashMap`→`ConcurrentHashMap`）、`abortedBarriers`/`blockedChannels`（`HashSet`→`ConcurrentHashMap.newKeySet()`）、`BarrierAlignment.receivedChannels`/`blockedChannels`（`HashSet`→`ConcurrentHashMap.newKeySet()`）改为并发安全结构；`oldestAligning()` 按 min checkpointId 选取（`ConcurrentHashMap` 不保插入序）。`abortBarrierAlignment` 保持直接调用（它是解除对齐阻塞的机制）。使三个集合及内部 channel 集合的跨线程迭代/修改不再抛 `ConcurrentModificationException`
- [x] 消除 task 线程迭代 `inFlightAlignments` 与 abort 线程并发修改之间的 `ConcurrentModificationException` 路径（`markFinishedChannel`/`handleBarrierNonRecursive`/`oldestAligning` 的迭代与 `abortBarrierAlignment` 的 remove 现均为弱一致/并发安全）

Exit Criteria:

- [x] ~~`abortBarrierAlignment` 经 mailbox CONTROL 邮件~~ → **方案 (b)**：三个集合（含 `BarrierAlignment` 内部集合）改为并发安全结构（仓库可观察 `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()`）；abort 线程的 `remove`/`add` 不再与 task 线程迭代并发 CME。接线验证：`GraphModelCheckpointExecutor.registerLocalAbortHandler` 仍直接调 `abortBarrierAlignment`（解阻塞机制不变），集合并发安全化由 `InputGate` 字段声明承载
- [x] 新增回归测试：`TestInputGateMailboxAbort`——reader 线程迭代对齐状态时，abort 线程并发 `abortBarrierAlignment`，断言不抛 `ConcurrentModificationException`、reader 不因永久阻塞而卡死、最终无泄漏 in-flight alignment
- [x] **接线验证**：代码审查确认 `registerLocalAbortHandler` 的 abort 信号直接到达 `InputGate.abortBarrierAlignment`（解阻塞），`InputGate` 集合并发安全化使跨线程迭代/修改不 CME（`abort handler` 与 `read()` 迭代经并发集合串行化）
- [x] `ai-dev/design/nop-stream/checkpoint-design.md` §2.8.1 D3 abort 路径段落更新为并发安全集合（方案 b）+ 记录方案 (a) mailbox 被拒原因（read 阻塞不排空 mailbox）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - TaskManager 许可泄漏修复

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`

- Item Types: `Fix | Proof`

- [x] 移除 `deployTask` 重占用 slot 路径（`:428`）的额外 `capacitySemaphore.acquireUninterruptibly()`（`:404` 的 acquire 已为新任务提供许可；`:424` 释放为旧 slot 平衡）

Exit Criteria:

- [x] `TaskManager.deployTask` 重占用 slot 路径的 semaphore 许可账目守恒（代码审查：新 acquire 1 次 + 旧 release 1 次 = 净 0 变化对旧 slot，净 -1 对新 slot 由 `:404` 承担）
- [x] 新增回归测试（锚定现有 taskmanager 测试目录）：对同一 occupied slot 连续 `deployTask` `capacity+1` 次，断言 `capacitySemaphore.availablePermits()` 稳定（不随次数单调下降）
- [x] **无静默跳过**：N/A（移除一行，无新增分支）
- [x] `No owner-doc update required`（纯内部许可账目修正，无契约/行为语义变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - SupervisionLoop waitForTerminal fail-loud

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java`, `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java`

- Item Types: `Fix | Proof`

- [x] 在 `NopStreamErrors` 新增一个**独立**错误码（`ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`）以区别于已被 `:255` 使用的 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`（region-restart 预算耗尽），避免可观测性混淆
- [x] 修改 `waitForTerminal`（`:463-477`）超时分支：不再静默 fall-through，改为抛 `StreamException(ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT)`（local/embedded 路径下经 `GraphModelCheckpointExecutor.submitAndRun` → `env.execute()` 上浮给调用方；该 executor 无 `JobCoordinator` 引用、无 `globalRecovery()` 调用点，故 local 路径即 fail-loud 到 API）。distributed/HA 路径下，task 异常转化为 FAILED 报告经 `reportTaskStatus` + `autoRecoverOnFailedReport`（默认 true）触发 coordinator 恢复——此处只须保证不静默重建第二个 task 实例，恢复语义由 coordinator 侧承担

Exit Criteria:

- [x] `waitForTerminal` 超时路径在仓库中可观察到 fail-loud（抛 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`），不再静默 WARN 后继续重建
- [x] 新增错误码在 `NopStreamErrors` 中定义，且与 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED` 区分（二者在 ops dashboard 上可辨）
- [x] 新增回归测试（锚定现有 `TestSupervisionLoopRestartLimitConfig.java` fixture）：构造一个卡在不可中断段的 task（mock `isFinished()` 持续返回 false 超过 budget），触发 region restart，断言抛出 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT` 而非静默重建并提交第二个 task 实例（断言 `executor.submitTask` 未被再次调用）
- [x] **无静默跳过**：超时分支显式失败（plan guide #24 满足）
- [x] `ai-dev/design/nop-stream/failover-design.md` 更新 waitForTerminal 超时语义（fail-loud 取代静默 fall-through）+ 区分两个错误码
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 四个 in-scope 确认 live defect（JobCoordinator race / InputGate cross-thread / TaskManager permit / SupervisionLoop zombie）均已修复
- [x] 每项均有针对性回归/并发测试证明此前断裂路径被守住（非仅"编译通过"）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
  - 注：`[x]` 满足时间延后到独立 CLOSURE_VERIFY audit（plan `2026-08-09-1330-1-coordinator-runtime-recovery-hardening-closure-audit.md`）完成日（2026-08-09）；EXECUTE-pass evidence（上方 `Closure Audit Evidence` `:163`）记录实现验证，独立审计 evidence 见 `### Independent Closure-Audit (CLOSURE_VERIFY)`。原 Follow-up deferred-flip 条目（下方）已由该独立审计 plan 完成。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）互斥/abort 投递/许可账目/fail-loud 在运行时确实生效（不只是类型/方法存在），（b）无空方法体/静默跳过作为正常实现
- [x] **端到端验证（Rule #22）**：至少一条 E2E 测试覆盖"checkpoint 进行中触发 task 故障 → 恢复运行 → sink 输出相对 source 恰好一次"（可锚定现有 recovery/ exactly-once E2E fixture，如 `TestMultiJvmExactlyOnceRecovery` 或 in-process recovery 测试，断言四个修复未通过未预见交互破坏 exactly-once）
- [x] `./mvnw compile -pl nop-stream -am -T 1C`
- [x] `./mvnw test -pl nop-stream -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

- **Phase 2 方案 (a) mailbox 投递不可行 → 改用方案 (b) 并发安全集合**（Classification: scope correction；Why Not Blocking: 方案 (a) 经实施验证会使 epoch-precise abort 死锁至 alignment timeout，方案 (b) 零回归达成实际目标；Successor Required: 无——并发集合 + add-before-remove 顺序已闭合 CME 与 re-creation race）。

## Non-Blocking Follow-ups

- 本 plan 的 P2 邻接项（`setTasksToAcknowledge` 竞态、`assignTasks` mid-iteration RPC 不一致、`failJob` 不取消 in-flight task）已归入 roadmap Follow-up Backlog，不阻塞本 plan closure。

## Closure

Status Note: 四个 in-scope P0/P1 并发/资源/静默跳过缺陷均已修复并配针对性回归测试；全量 `./mvnw test -pl nop-stream` 绿。Phase 2 因方案 (a) 不可行改用方案 (b)（已记录裁定）。
Completed: 2026-08-07

Closure Audit Evidence:

- Reviewer / Agent: EXECUTE pass (opencode, ses mission-driver 2026-08-06-225554). 独立 closure-audit（roadmap work-item-1 `done` flip 的前置）属后续 CLOSURE_VERIFY mission step，非 EXECUTE 范围；EXECUTE 已客观验证以下全部 Exit Criteria / Closure Gates。
- **P0 JobCoordinator 恢复竞态**：`recoveryLock`（`ReentrantLock`）串行化 `globalRecovery`/`rotateFencingEpochCoreLocked`/`prepareAssignmentsLocked`；RPC fan-out（`executeAssignmentFanOut`）在锁外；迟到守卫（`epochAtEntry` snapshot + 锁内重检）WARN 短路。回归测试 `TestJobCoordinatorRecoveryConcurrency`（2 test）：两线程并发 `globalRecovery` → `restartCount` delta==1、`recoveryGen` delta==1、`assignTask` 恰 2 次（无 duplicate attemptId）、working-set 一致。
- **P1 InputGate 跨线程**：`inFlightAlignments`(`ConcurrentHashMap`)、`abortedBarriers`/`blockedChannels`/`BarrierAlignment.{receivedChannels,blockedChannels}`(`ConcurrentHashMap.newKeySet()`)、`oldestAligning` 按 min checkpointId、`abortBarrierAlignment` add-before-remove（闭合 re-creation race）。回归测试 `TestInputGateMailboxAbort`（1 test）：reader 迭代 + abort 线程并发 → 无 CME、无泄漏 in-flight alignment。接线：`registerLocalAbortHandler` 直接调 `abortBarrierAlignment`（解阻塞机制不变），集合并发安全化由 `InputGate` 字段承载。
- **P1 TaskManager 许可泄漏**：移除 `deployTask` 重占用 slot 路径的额外 `capacitySemaphore.acquireUninterruptibly()`。回归测试 `TestTaskManager.testRedeployToOccupiedSlotDoesNotLeakPermit`：failed redeploy 后 `availablePermits()==capacity`（修复前为 capacity-1）。
- **P1 SupervisionLoop zombie**：`waitForTerminal` 超时抛 `ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`（独立于 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`），package-private + budget 参数。回归测试 `TestSupervisionLoopZombieTaskTimeout`（2 test）：超时 fail-loud + 正常完成不抛。
- **Anti-Hollow**：(a) 互斥（`recoveryLock` 真正 lock/unlock，非空方法）、abort（并发集合真改类型 + add-before-remove 真改顺序，测试覆盖 re-creation race）、许可（真删一行，availablePermits 断言）、fail-loud（真抛异常，非空 catch）均在运行时经测试生效；(b) 无空方法体/静默跳过作为正常实现（迟到守卫 WARN、waitForTerminal throw、abort catch 均显式）。
- **端到端（Rule #22）**：`./mvnw test -pl nop-stream` 全绿（nop-stream-core 1384 tests / nop-stream-runtime 751 tests / 其余模块全 SUCCESS，0 failures）。in-process distributed exactly-once：`TestDistributedExactlyOnce`（7/7）、`TestSupervisionLoopConsistentCut`（3/3）、`TestSupervisionLoopReconnectE2E`（2/2）通过——覆盖 checkpoint 期间 task 故障 → 恢复 → exactly-once。`TestMultiJvmExactlyOnceRecovery` 为 multi-JVM 测试（标准 suite skipped，需专用 runner），由 in-process distributed 测试覆盖本 gate。
- **Owner docs**：`failover-design.md` §2.3（恢复互斥约定）+ waitForTerminal fail-loud；`checkpoint-design.md` §2.8.1 D3（abort 并发安全 + 方案 a 拒绝理由）。Phase 3 `No owner-doc update required`。
- **Build/规范**：`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` BUILD SUCCESS；`./mvnw test -pl nop-stream -T 1C` BUILD SUCCESS（全模块）；imports 分组、4-space 缩进符合 AGENTS.md。

### Independent Closure-Audit (CLOSURE_VERIFY)

- Reviewer / Agent: 独立 fresh session closure-audit（opencode, plan `ai-dev/plans/nop-stream-independent-audit/2026-08-09-1330-1-coordinator-runtime-recovery-hardening-closure-audit.md`）。非 production plan 实现者（EXECUTE pass ses `2026-08-06-225554`），非 successor audit plan `2026-08-09-1252-1` / `2026-08-09-1300-1` 实现者。
- Audit Session: ses mission-driver 2026-08-09-125203-mission-driver（CLOSURE_VERIFY step，fresh context）。
- 逐条 production plan Exit Criterion / Closure Gate 验证结果（live repo 2026-08-09 二次核对，文件:行号锚定）：
  - **Gate `:137`（四个 live defect 均已修复）→ PASS**。P0 JobCoordinator：`recoveryLock`（`ReentrantLock`，`JobCoordinator.java:248`）三 lock 站点——`assignTasks()`（`:513`/lock `:544`/unlock `:548`）、`globalRecovery()`（`:1012`/lock `:1023`/unlock `:1074`）、`activateAsLeader(LeaderEpoch)`（`:1224`/lock `:1253`/unlock `:1261`）；锁内 callee `prepareAssignmentsLocked`（`:546/:1072/:1259`）+ `rotateFencingEpochCoreLocked`（`:1069/:1258`，定义 `:1113`，Javadoc `:1087` "Must be called while holding recoveryLock"）；fan-out 锁外（`:550/:1077/:1263`）；迟到守卫 `globalRecovery:1020` snapshot + `:1031-1036` 锁内重检 WARN 短路（非 fall-through）。P1 InputGate：`inFlightAlignments`（`:99` `ConcurrentHashMap`）、`abortedBarriers`（`:111` `ConcurrentHashMap.newKeySet()`）、`blockedChannels`（`:124` `ConcurrentHashMap.newKeySet()`）、`BarrierAlignment.{receivedChannels,blockedChannels}`（`:790/:791` `ConcurrentHashMap.newKeySet()`）；`abortBarrierAlignment`（`:728`）add-before-remove（`:729 add → :730 remove`）。P1 TaskManager：`deployTask`（`:367`）entry `tryAcquire`（`:404`）为 redeploy 唯一 acquire；re-occupy 块（`:426-435`）只 `release` 旧 permit（`:433`），无 `acquireUninterruptibly` 重 acquire（Javadoc `:418-425` 注 "That extra acquire is removed"，净 permit 变化 0）。P1 SupervisionLoop：`waitForTerminal`（`:492`）超时 `throw new StreamException(ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT)`（`:503`，非静默 fall-through）；`NopStreamErrors.java:397-402` 定义该码且与 `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`（`:375-378`）区分（distinct code + message + params，Javadoc `:393-395`）。
  - **Gate `:138`（针对性回归/并发测试，非仅"编译通过"）→ PASS**。四测试均含真实硬断言、非空壳（Rule #24）：`TestJobCoordinatorRecoveryConcurrency`（`assertEquals(restartCount delta==1)` `:177-179`、`assignTaskCount==2` `:191-193`、`countKey==1` `:198-200`、`fencingEpoch==currentEpoch` `:245-246`）；`TestInputGateMailboxAbort`（`assertNull(err)` `:132-133`、`assertEquals(barrierCount, aborts)` `:134-135`、`assertTrue(inFlight empty)` `:139-141`、`assertFalse(permanent block)` `:148-149`）；`TestTaskManager.testRedeployToOccupiedSlotDoesNotLeakPermit`（`assertEquals(0, runningTaskCount)` `:357-358`、`assertEquals(2, availablePermits==capacity)` `:359-361`）；`TestSupervisionLoopZombieTaskTimeout`（`assertThrows(StreamException)` `:54-56`、`errorCode contains supervision-zombie-task-timeout` `:59-61`、`getMessage contains taskKey` `:64-65`、`waitForTerminal 不抛 on terminal` `:80-81`）。无空方法体 / `continue` 跳过 / 吞异常。
  - **Gate `:139`（无被静默降级到 deferred/follow-up 的 in-scope defect）→ PASS**。Phase 2 方案 (a) → (b) 已在 `Deferred But Adjudicated` 显式裁定（scope correction，非静默降级）；P2 邻接项显式归 `Non-Blocking Follow-ups`（非 in-scope defect）。
  - **Gate `:140`（owner docs 同步）→ PASS**。`failover-design.md` §2.3 + `checkpoint-design.md` §2.8.1 D3 已更新（production plan Phase 1-3 evidence）；Phase 3 `No owner-doc update required`。
  - **Gate `:141`（独立 closure-audit 已完成并记录证据）→ PASS（由本节满足）**。该 gate 此前被勾选但其 evidence（`:163`）是 EXECUTE self-pass 且明示 deferred（文本矛盾 M3）——见该 gate 行注记。本 `### Independent Closure-Audit (CLOSURE_VERIFY)` 子段即该 gate 的独立审计 evidence 满足。
  - **Gate `:142`（Anti-Hollow Check）→ PASS**。运行时调用链触达 confirmed（非仅类型存在）：`recoveryLock` 被 `globalRecovery`（`:1012`）+ `activateAsLeader`（`:1224`）调用；`abortBarrierAlignment` 被 `GraphModelCheckpointExecutor.registerLocalAbortHandler`（`:830` 定义，`:858` 调用，5 call sites `:128/:196/:271/:336/:400`）→ checkpoint abort 入口调用；`deployTask` permit 路径被 recovery redeploy 经 `executeAssignmentFanOut`（`:671`，`:674` 调 deployTask，由 `assignTasks:550`/`globalRecovery:1077`/`activateAsLeader:1263` 触发）；`waitForTerminal` 被 `SupervisionLoop.restartRegion`（`:382`，`:437` 调用，rebuild phase `:441-449` 之前）触达。无空方法体/静默跳过作为正常实现。
  - **Gate `:143`（端到端验证 Rule #22）→ PASS（fresh run 2026-08-09）**。`TestDistributedExactlyOnce`（7 tests / 0 fail）、`TestSupervisionLoopConsistentCut`（3 tests / 0 fail）、`TestSupervisionLoopReconnectE2E`（2 tests / 0 fail）全 PASS——覆盖 checkpoint 期间 task 故障 → 恢复 → exactly-once。
  - **Gate `:144`（`./mvnw compile -pl nop-stream -am`）→ PASS**。`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` BUILD SUCCESS（408 modules，0 failures，2026-08-09T17:36:57+08:00）。
  - **Gate `:145`（`./mvnw test -pl nop-stream -am`）→ PASS（fresh run 2026-08-09）**。BUILD SUCCESS（全模块，nop-stream-runtime `Tests run: 762, Failures: 0, Errors: 0, Skipped: 8`，2026-08-09T17:40:27+08:00）。
  - **Gate `:146`（checkstyle / 代码规范）→ PASS**。imports 分组（io.nop.* → 第三方 → java.*）、4-space 缩进符合 AGENTS.md。
- Fresh regression-test run（2026-08-09）：
  - runtime: `./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C -Dtest=TestJobCoordinatorRecoveryConcurrency,TestSupervisionLoopZombieTaskTimeout,TestTaskManager#testRedeployToOccupiedSlotDoesNotLeakPermit` → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`（BUILD SUCCESS）。
  - core: `./mvnw test -pl nop-stream/nop-stream-core -am -T 1C -Dtest=TestInputGateMailboxAbort` → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`（BUILD SUCCESS）。
- Anti-Hollow 检查结果：四个修复在运行时调用链上被触达（见 Gate `:142`）；端到端恢复路径连通（故障 → `globalRecovery` recoveryLock 互斥 → `rotateFencingEpochCoreLocked` fencing 轮转 → `executeAssignmentFanOut` redeploy → exactly-once，由 3 个 E2E 测试 PASS 证明），非仅组件级单测通过。
- Deferred 项分类检查：`Deferred But Adjudicated` 仅 Phase 2 方案裁定（已裁定 non-blocking，successor 不需要）；`Non-Blocking Follow-ups` 仅 P2 邻接项（非 in-scope）。无 in-scope defect 被静默降级。
- 结论：production plan `2026-08-04-2300-1` 的全部 Closure Gates 经独立 fresh-session closure-audit 核对均 PASS；roadmap work-item-1 的 "done only after independent closure-audit evidence" 前置已由本节满足。

Follow-up:

- roadmap work-item-1 由 `planned` → `done` 的 flip 受 roadmap 规则约束（"done only after independent closure-audit evidence"），属后续 CLOSURE_VERIFY mission step。**RESOLVED 2026-08-09**：独立 closure-audit 由 plan `2026-08-09-1330-1-coordinator-runtime-recovery-hardening-closure-audit.md` 完成（fresh session，全部 Closure Gates PASS，evidence 见 `### Independent Closure-Audit (CLOSURE_VERIFY)`）；roadmap work-item-1 `done` flip 已由该 plan 执行。
- Phase 2 方案 (a) mailbox 投递若将来重构 `InputGate.read()` 为非阻塞（循环内排空 mailbox），可重新评估；当前方案 (b) 充分。
- 本 plan 的 P2 邻接项（`setTasksToAcknowledge` 竞态、`assignTasks` mid-iteration RPC 不一致、`failJob` 不取消 in-flight task）仍归 roadmap Follow-up Backlog。
