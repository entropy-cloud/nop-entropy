# 2026-09-02 Distributed Scenario Path Engine Defects (item 14)

roadmap item 14（复合场景分布式落地）执行中发现并就地修复的分布式路径缺陷。来源 plan：
`ai-dev/plans/nop-stream-productization/2026-09-01-2217-3-composite-scenario-distributed-verification.md`。

## Problem

场景管线（S1/S2 XDSL）首次以真实多 JVM（`MiniStreamCluster` spawn JC + 2/3 TM）部署时：

- checkpoint 完全不产生：`triggerCheckpoint` RPC 发出后无 ACK，TM 日志出现
  「No barrier tracker for ...」/「no matching in-flight epoch」。
- kill TM 后恢复的 checkpoint 全部超时；每个 timeout abort 把重部署的任务再取消一遍，
  restart 配额耗尽、prepared sink 数据被 abort 掉（cancel/recover 级联）。
- 恢复重放后输出重复（CDC 源 offset 从 0 重放）。
- 有界运行末尾最后一批输出丢失（任务完成后提交通知无处投递）。
- 陈旧 attempt 线程退出时把 replacement 的注册表项删掉（新任务对
  triggerCheckpoint/cancelTask 不可见）。
- 完成态任务保留注册表项后，`EmbeddedDistributedExecutor`/`RpcDistributedExecutor`
  等待「running 计数归零」永久超时（默认测试套件 6 个测试 60s 超时）。

## Diagnostic Method

- 多 JVM 黑盒定位难（子进程日志分散）：用 `MiniStreamCluster` 的 per-process 日志 +
  共享库 `nop_stream_task_assignment` 行观察 fencing/assignment 状态。
- 「无 ACK」先查 JC 侧扇出（发现扇出面只有 source 节点，非 source 节点的 tracker 无
  in-flight epoch 注册，barrier ACK 被 drop）→ 再查 TM 侧接收（发现 remote-deploy 路径
  从未装配 `CheckpointBarrierTracker`，三个空白：tracker/后端/restore）。
- 「恢复重放重复」对照 LOCAL 恢复测试（无此问题）→ 差异在 remote-deploy 不走
  `restoreOperatorsFromState`，`ICheckpointedFunction.initializeState` 从未触发，CDC
  offset store 保持 transient null，每 epoch 快照空 map。
- 「timeout 即取消」读 abort handler 历史：控制通道设计意图是 snapshot-failure 才
  cancel；timeout 是常规背压事件。S2 kill 演练（租约到期 ~15s 窗口）稳定复现级联。
- 「计数归零超时」由默认套件回归暴露（6 个 embedded/rpc distributed 测试），根因是新
  加的完成态保留条目改变了 `runningTasks.size()` 语义——消费方 `getRunningTaskCount()`
  的完成检测被破坏。

## Root Cause

1. remote-deploy 路径（`TaskManager.deployTask`）从未接线 checkpoint 管线：无状态后端、
   无 barrier tracker、无 restore-on-deploy（LOCAL 路径的 `wireTaskCheckpointPipeline`
   没有远程对等物）。
2. barrier 扇出面错误地只取 source 节点；tracker 的 in-flight epoch 注册必须发生在
   barrier 经数据面到达之前的每个承载节点上。
3. abort handler 不区分 timeout 与 snapshot-failure，timeout 也取消全部任务。
4. 新鲜启动（无 durable 状态）的 subtask 不流经 `restoreOperatorsFromState`，
   `initializeState` 语义缺失。
5. 完成态任务注销使「EOS 后、最后 checkpoint 前」reach 2PC sink 的数据失去提交通息
   投递目标；陈旧 attempt 的 finally 无条件 `remove(key)` 会删掉 replacement 的条目。
6. 完成态保留条目后 `getRunningTaskCount()` 仍返回 `runningTasks.size()`（含保留项），
   完成检测语义漂移。

## Fix

- 新增 `RemoteTaskDeploySupport.wireDeployedSubtask`（TM 侧三步接线：状态后端预置 →
  checkpoint plan → tracker + RPC ACK 回传 → manifest-first restore-on-deploy，restore
  支持 KeyGroupRange 路由的 restore-time rescale）；`GraphModelCheckpointExecutor` 增加
  per-subtask 过滤的恢复入口与 deploy-restore 指纹校验入口。
- `JobCoordinator.triggerCheckpoint` barrier 扇出改为全部承载节点；
  `registerDistributedCommitForwarder`/`notifyCheckpointComplete` 打通 durable 后跨 JVM
  的 2PC 提交通知（失败重试 + subsuming commit 兜底）。
- abort handler 改为 reason 感知：timeout 只丢弃 epoch（任务继续），snapshot-failure
  才 cancelTask；global recovery 时 `abortAllPendingCheckpoints` 释放 pending 槽位。
- 新鲜 subtask 调 `restoreState(null)` 镜像 LOCAL 空恢复语义（触发
  `initializeState`）。
- 完成态任务保留注册表项（尾部提交），陈旧 attempt 改条件 `remove(key, this)`；
  `getRunningTaskCount()` 只统计未到终态的任务（新增 `finished` 标志）。
- JC 重启恢复：launch 路径启动时恢复最新 durable checkpoint 并把 id counter 推进到其后
  （防低位 epoch 重发）。

## Tests

- `nop-stream-runtime/src/test/.../taskmanager/TestTaskManager.java`
  （`testStaleAttemptExitDoesNotRemoveReplacementRegistryEntry`：确定性复现陈旧 attempt
  晚于 replacement 退出的竞态，断言 replacement 条目存活且可被 trigger 触达）
- `nop-stream-fraud-example/src/test/.../scenario/TestDistributedScenarioSerialization.java`
  （spec 可序列化 + 跨构建同构；`s1FullTopologyRunsViaRemoteDeployInProcess`：全 S1 拓扑
  经 remote-deploy 控制面 + checkpoint + 分布式提交通知的进程内证明——多 JVM 前置接线验证）
- `TestS1MultiJvmE2E`/`TestS2MultiJvmE2E`（C0/C1 gated：durable manifest 断言 = checkpoint
  跨 JVM 真实完成；kill/恢复/fencing/幂等重提交端到端）
- `TestS2RestoreRescaleMultiJvmE2E`（跨集群恢复：JC 恢复 + TM per-subtask 恢复 + 幂等
  无重复）——TM 日志「restored from durable epoch N」为恢复链路的直接证据
- 默认套件回归（6 个 distributed executor 测试）随 `getRunningTaskCount()` 修复转绿

## Affected Files

- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/deploy/RemoteTaskDeploySupport.java`（新增）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/SubtaskPlanBuilder.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/RemotePipelineSpec.java`、`RemotePipelineResolver.java`、`TaskDeploymentDescriptor.java`、`IStreamTaskRpcService.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/launch/JobCoordinatorMain.java`（pipelineFactoryClass seam）

## Notes For Future Refactors

- `getRunningTaskCount()` 语义 = 「未到终态的任务数」；完成态保留条目是 2PC 尾部提交的
  依赖，重构注册表时不得回退为 `size()`。
- checkpoint timeout abort 不得升级为取消任务（常规背压事件）；取消保留给
  snapshot-failure（可能的状态不一致）。
- barrier 扇出面 = 全部承载节点（tracker 注册先于 barrier 到达）；恢复窗口内
  `recoveryPending` 抑制触发（触发 RPC 行必须严格晚于新 deployment 行）。
