# 2435 WI10 波次 CC-06/CC-17——checkpoint 并发重构（fan-out 移出 trigger 线程 + taskExecutor 有界 + 终态 RPC 异步）

> Plan Status: active
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md WI10 顺带项（CC-06/CC-17，r5-successors 既有登记）；roadmap 顺序约束「差分绿后独立 commit」——WI10 差分已绿（plan 2426），本 plan 即该独立承接
> Related: plan 2426（WI10，差分绿前提已满足）；r5-successors CC-06/CC-17/CC-10（先例）

## Purpose

落地 r5 两个 checkpoint 编排并发缺陷：CC-06（JC 周期触发线程内 N×阻塞 barrier RPC 扇出，`JobCoordinator#triggerCheckpoint` :943-958）与 CC-17（TM `taskExecutor` 无界队列 + `RunningTask` finally 内同步 `reportTerminalStatus`）。各带定向测试，与行为保持部分分离提交。

## Current Baseline

2026-10-11 实测（HEAD 1af2b98fbf）：

- CC-06 现场确认：`JobCoordinator#triggerCheckpoint`（:929-965）在**周期触发线程**（jc-periodic-checkpoint，scheduleWithFixedDelay :1272）内逐 nodeId 同步 `rpc.triggerCheckpoint(barrier, epoch)`——一个慢节点阻塞全部后续节点的 barrier 投递与下一 tick。failover 版 `sendCheckpointSignalToAllTaskManagers` 同型。
- CC-17 现场确认：①TM `taskExecutor = Executors.newFixedThreadPool(capacity)`（:228，内部无界队列）；②`RunningTask.run()` finally 末尾同步 `reportTerminalStatus(success)`（:186）——任务线程直接发终态 RPC。
- CC-10 先例（plan 369 Phase 4）：commitExecutor 有界队列（容量可配默认 1000）+ CallerRuns 背压 + `nop.stream.task.commitQueueDepth` 打点 + `TestTaskManagerCommitQueueBound`。
- 既有守护：`TestCommitFanoutOffMonitor2326`（monitor 不被 slow participant 阻塞——monitor 侧已有界，本 plan 补 trigger 线程侧 fan-out）；failover 11/11、TerminationMatrix、TestCommitFanoutOffMonitor2326 为回归面。
- 基线：core 1967/0/1、runtime 1203/0/11、flow 315/0/0。
- 本计划编号 2435 为当前最大+1（2432 本会话、2433/2434 并发占用）。

## Goals

- **CC-06**：barrier fan-out 移出触发线程——JC 持有单线程 `barrierFanoutExecutor`（顺序保序：同节点 barrier 按 epoch 序投递），trigger 线程提交 fan-out 任务后立即返回；失败处理语义保持（逐节点 try/catch + 无 RPC WARN）。
- **CC-17①**：`taskExecutor` 有界化——`ThreadPoolExecutor(capacity, capacity, 60s, LinkedBlockingQueue<>(taskQueueCapacity 可配默认 1000), factory, AbortPolicy)`；饱和时 deploy 失败 loud（typed 异常，拒绝静默排队饥饿）。
- **CC-17②**：终态 RPC 异步化——TM 持单线程 `terminalReportExecutor`（有界小队列 + CallerRuns 兜底：饱和时由垂死任务线程自发，保持投递不丢）；`RunningTask` finally 改经该 executor 提交。
- 定向测试：CC-06（慢节点 latch 不阻塞下一 tick，镜像 TestCommitFanoutOffMonitor2326 形态）；CC-17①（饱和 deploy typed 失败）；CC-17②（coordinator RPC 慢时不阻塞任务线程收尾 + 报告最终到达）。
- 回归面：failover 11/11、TerminationMatrix、TestCommitFanoutOffMonitor2326、TestRemoteDeployCheckpointWiringE2E、差分 harness、三模块基线不下降。

## Non-Goals

- 不改 CheckpointCoordinator monitor 内部（其无界性已由 2326 收口）；不改 commitExecutor（CC-10 已收）；不动 2PC/epoch 语义；不引入重试机制（接口级无超时契约保持——plan 369 裁定）。

## Scope

### In Scope

- `JobCoordinator`：barrierFanoutExecutor（生命周期随 stop()）+ triggerCheckpoint/failover 扇出异步化。
- `TaskManager`：taskExecutor 有界化（taskQueueCapacity 可配）+ terminalReportExecutor + RunningTask finally 改异步提交。
- 新定向测试 ×3；`ai-dev/backlog/nop-stream-r5-successors.md` CC-06/CC-17 收口标记；roadmap WI10 done 注记增补（承接落地）；daily log。

### Out Of Scope

- monitor 内部、重试/超时机制、 DIV 冻结项、其他 r5 条目。

## Execution Plan

### Phase 1 - CC-06 fan-out 异步化

Status: planned
Targets: `JobCoordinator`

- Item Types: `Fix`（CC-06——r5 既有缺陷）

- [ ] `barrierFanoutExecutor`（单线程，daemon，名 jc-barrier-fanout-<jobId>）随构造创建、stop() 关闭；`triggerCheckpoint` 与 failover 版扇出改为提交任务（逐节点循环体原样搬入，异常语义保持）
- [ ] 定向测试：新 `TestCheckpointBarrierFanoutAsync`（coordinator + RecordingRpcService：节点 0 的 triggerCheckpoint 阻塞 latch → 周期 tick 不停摆（第二 tick 期间节点 0 仍阻塞）→ 释放后两 tick 的 barrier 都投递）

Exit Criteria:

- [ ] 定向测试绿（慢节点不阻塞触发线程）+ 既有 coordinator/failover/TerminationMatrix 族绿
- [ ] 差分 harness 复跑绿

### Phase 2 - CC-17 taskExecutor 有界 + 终态 RPC 异步

Status: planned
Targets: `TaskManager`、`RunningTask`

- Item Types: `Fix`（CC-17——r5 既有缺陷）

- [ ] taskExecutor：`ThreadPoolExecutor(capacity, capacity, 60s, LinkedBlockingQueue<>(taskQueueCapacity 默认 1000), factory, AbortPolicy)`；deploy 捕获 RejectedExecutionException → typed StreamException（含 jobId/vertex 细节）；stop() 语义保持
- [x] terminalReportExecutor：单线程 queue(64)，拒绝 handler **始终内联**（含 shutdown 后——审查 Major-3）；stop() 关闭序=最后；RunningTask finally 改经 owner.submitTerminalReport 提交
- [ ] 定向测试：①饱和——capacity=1 + queue=1 时第三个 deploy typed 失败；②异步终态——coordinator reportTaskStatus 阻塞 latch 下任务线程在有限时间内退出（不阻塞）+ latch 释放后报告到达（记录于 RecordingRpcService）

Exit Criteria:

- [ ] 三定向测试绿 + 既有 TM/commit 族（TestTaskManagerCommitQueueBound/TestCommitFanoutOffMonitor2326/TerminationMatrix/RemoteDeploy E2E）绿
- [ ] C1 独立提交（仅并发行为变更，本 plan 即 roadmap 要求的独立 commit——差分前提已在 WI10 满足）

### Phase 3 - 回归 + 台账 + 收口

Status: planned
Targets: r5-successors、roadmap、daily log

- Item Types: `Proof`

- [ ] 三模块基线（core/runtime/flow 不下降）+ 差分 harness + fixture restore 复跑
- [ ] r5-successors CC-06/CC-17 收口标记（plan 2435 + 定向测试名）；roadmap WI10 done 注记增补「CC 承接落地（plan 2435）」；daily log；doc-links
- [ ] C2 收口提交（台账 + 本 plan Closure）

Exit Criteria:

- [ ] 基线不下降 + 双门复跑绿
- [ ] 台账/roadmap/log 更新一致
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2435-wi10-wave-cc06-cc17-checkpoint-concurrency.md --strict` exit 0

## Closure Gates

- [ ] CC-06/CC-17 落地且三定向测试绿；回归面（failover 11/11、TerminationMatrix、2326、RemoteDeploy E2E）全绿
- [ ] 三模块基线不下降 + 差分 harness/fixture 复跑绿
- [ ] r5 台账 + roadmap 注记 + daily log 一致
- [ ] 独立 closure audit（fresh session）证据入 Closure 段
- [ ] **Anti-Hollow Check**：audit 验证 fan-out 真实经 barrierFanoutExecutor（线程名证据）、终态报告真实经 terminalReportExecutor（定向测试断言到达）、无空方法体
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2435-wi10-wave-cc06-cc17-checkpoint-concurrency.md --strict` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0
- [ ] 路径限定提交（C1/C2）

## Deferred But Adjudicated

### 终态 RPC 逐任务顺序保证（跨任务全局顺序）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 单线程 terminalReportExecutor 已保证同 JVM 内 FIFO；跨 TM 的到达顺序由 coordinator 按 (vertex, subtask) 处理，不依赖全局序——现有协调语义无全局顺序需求记录。
- Successor Required: `no`
- Successor Path: 如未来出现全局序需求，归 r5-successors 新条目。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- （关闭时填写）
