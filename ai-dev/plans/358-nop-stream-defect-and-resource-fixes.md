# 358 nop-stream 正确性与资源缺陷修复

> Plan Status: completed
> Last Reviewed: 2026-09-26
> Source: 审计 `ai-dev/audits/2026-09/2026-09-26-0546-deep-audit-nop-stream-quality/`（summary.md P0/P1/P2 清单 + 04-concurrency-resources-connectors.md；全部 P0/P1 已经独立复核代理逐条裁决确认）
> Related: 359（可读性/结构整改）、360（JMH+JFR 性能迭代）

## Purpose

修复 nop-stream 审计确认的 live 正确性与资源缺陷：资源泄漏、启动失败不回收、静默丢数据/静默降级、控制面阻塞、unsafe 并发初始化、持久化原子性缺口。修复后行为可观察地收敛：该 fail 的 fail（typed 异常），该关的关（订阅/线程/meter），该重试的有限重试。

## Current Baseline

- 基线编译绿：`./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-flow,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-connector -am` 通过（2026-09-26 本任务实测）。
- 已确认缺陷清单（file:line 以审计 04 报告为准，行号误差 ±10）：
  1. RemoteInputChannel 订阅永不关闭：`RemoteInputChannel.java:248` subscribe，close():472-485 全仓 main 零调用方 → 每次部署/重部署泄漏一个 IMessageService 订阅。
  2. EmbeddedDistributedExecutor.java:142-152：tm.start() 在 try 之外，装配失败时已启动 TaskManager 不回收（RpcDistributedExecutor.java:359-407 teardownOnStartupFailure 为先例）。
  3. TaskManager.java:1131-1160 + JobCoordinator.java:1139-1181：2PC finishCommit（JDBC/文件提交）同步执行在 message-service 派发线程 / coordinator ACK 线程，慢提交阻塞心跳与 ACK 派发。
  4. TaskManager.java:269-312：heartbeat() 后半段 runningTasks 循环不在 try/catch 内，未捕获异常永久杀死 scheduleAtFixedRate 心跳。
  5. RemoteResultPartition.java:189-193：有界流 EOS 发送失败仅 WARN；close(:182) 已 stopHeartbeat 且生产 builder（RemoteGraphExecutionPlanBuilder.java:176）传 channelTimeoutMs=0 禁用超时兜底 → 下游 read 可永等。
  6. TaskManager.java:760-780：sendCheckpointAck 失败仅 LOG.error 无重试，仅靠 checkpoint 超时 abort 兜底。
  7. RemoteInputChannel.java:458-466,483,427：injectElements/close/captureInFlightData 的 queue.offer 返回值被忽略 → 恢复注入可静默丢数据、close 时 EOS 无法入队致 reader 永久阻塞。
  8. DataPlaneMessageServiceAdapter.java:116-136：不可解码记录仅 WARN 后丢弃，绕过 RemoteInputChannel 的 decodeError fail-fast 机制。
  9. EngineMetrics.java:44-53 与 TaskNodeMetrics.java:35,42-43,85-90：BY_JOB/BY_NODE/GAUGE_STATE_REFS 无 remove 路径；同名 meter 重复注册返回旧实例（TM 重启后 gauge 指向旧对象）。
  10. JdbcTwoPhaseCommitSink.java:180-189,220-240：initialized/dialect/insertDataSql 普通字段，saveState（任务线程）与 commit（提交通知线程）并发首次初始化为 unsafe lazy-init。
  11. JdbcTwoPhaseCommitSink.java:431-455：JDBC 批无上限（整 epoch 一次性 executeBatch，大 epoch OOM/驱动超限）。
  12. FileTwoPhaseCommitSink.java:238-250,463-490,536-550：temp 写入与 manifest 原子替换无 fsync/force，OS 崩溃后可能出现"manifest 已提交而数据丢失"。
  13. WindowOperatorFactoryImpl.java:198-237：dummy serializer `copy()` 返回原引用、`createInstance()` 失败静默 return null、`isImmutableType()` 恒 true（全仓无活跃调用点，属契约错误+潜在别名风险）。

## Goals

- 上述 13 项缺陷全部修复并各有 focused 测试（或按裁定收敛为显式契约）。
- 数据面失败语义统一：EOS 发送失败、解码失败、注入溢出均 fail-typed/毒丸，不再静默丢弃。
- 资源生命周期闭环：任务结束后订阅被取消（有测试断言）、启动失败回收 TM、metrics 随 job/node 生命周期释放。
- 2PC commit 不再占用控制面派发线程，失败重试语义保持。

## Non-Goals

- 不做巨型类抽离与方法拆分（→359）；不做性能优化与基准（→360）。
- 不改 checkpoint 协议、barrier 线格式、checkpoint 存储格式。
- 不改 WindowOperator 窗口算法逻辑（除 dummy serializer 契约修正这一无活跃调用点的点）。
- 不把 engine 底座可辩护的容错路径（LOG.error+下轮重试兜底的 periodic 触发等，见审计 02"可接受"清单）改成败快。

## Scope

### In Scope

- `nop-stream/nop-stream-runtime`（transport/taskmanager/metrics/execution/coordinator）
- `nop-stream/nop-stream-connector-jdbc`、`nop-stream/nop-stream-connector`
- `nop-stream/nop-stream-runtime` 的 WindowOperatorFactoryImpl（契约修正）

### Out Of Scope

- `nop-stream-core`（除测试外不改）、`nop-stream-cep`、`nop-stream-rocksdb`、`nop-stream-flow`
- FileSourceReader 锁内 I/O 缩界（P3，watch-only residual，见 Non-Blocking Follow-ups）
- TaskManager 容量 16 硬编码配置化（→ Non-Blocking Follow-ups）

## Execution Plan

### Phase 1 - 数据面传输正确性（runtime/transport）

Status: completed
Targets: `RemoteInputChannel`、`RemoteResultPartition`、`DataPlaneMessageServiceAdapter`、`InputGate`/任务关闭链（InputGate 现无 close/release 方法，需新增释放入口并接入任务取消/关闭链）

- Item Types: `Fix`

- [x] Baseline 1：执行本 Phase 前记录 `./mvnw test -pl nop-stream/nop-stream-runtime -am` 的通过/失败基线（写入 daily log）——1061 run / 0 fail / 10 skipped，BUILD SUCCESS（2026-09-26）
- [x] Fix-1 订阅生命周期：任务/输入门关闭路径接入 channel 释放，任务结束后后端订阅被取消（InputGate.close + InputChannel.close 默认 no-op[local 无需释放裁定] + StreamTaskInvokable 四个 invoke finally 接线 closeInputGate）
- [x] Fix-5 EOS fail-fast：EOS 发送失败从仅 WARN 升级为 typed 失败（close() 抛 ERR_STREAM_STATE_ERROR + getEosSendError() 可观察；经 RecordWriter.close 聚合重抛 → 任务失败 → job 级取消链解锁下游）
- [x] Fix-8 解码毒丸：DataPlaneMessageServiceAdapter 不可解码记录改为向 channel 投递 typed 失败（新增 WireDecodeFailureAware 接口，RemoteInputChannel.EnvelopeConsumer 实现并置 decodeError+finished+哨兵），reader 侧 fail-fast；非 aware 消费者保留 discard+WARN 回退
- [x] Fix-7 offer 返回值：injectElements/close/captureInFlightData 及 EnvelopeConsumer 全部 EOS 哨兵 offer 均校验返回值（flagOverflow/enqueueTerminalSentinel），失败 fail-typed 或保证 reader 经 checkChannelError 被唤醒

Exit Criteria:

- [x] 新增 focused 测试：(a)-(d) 全部落地于 TestRemoteTransportLifecycle（7 条，含 (a) close 链 cancel 计数断言、(b) EOS typed + getEosSendError、(c) wire 解码失败 reader typed、(d) 满队列注入 typed 丢弃禁止 + close 唤醒）
- [x] Fix-5 下游解阻验证：传播机制 = EOS 发送失败 → close() 抛 ERR_STREAM_STATE_ERROR → RecordWriter.close 聚合重抛（RecordWriter.java:250-276 既有语义）→ closeOutputWriters/任务 finally 失败 → SubtaskTask 上报 FAILED → JobCoordinator 失败路径取消任务链 → 下游 reader 线程被 cancel/interrupt 解除阻塞；TestRemoteTransportLifecycle 断言 partition typed + TestRpcDistributedExecutorE2E/TestSupervisionLoop* 既有失败传播测试保持全绿（代码追踪记录见本条与 daily log）
- [x] **端到端验证**（Minimum Rules #22）：`./mvnw test -pl nop-stream/nop-stream-runtime` 1079 全绿（含 TestDataPlaneKafkaBackendE2E/TestRemoteDataExchange/TestEmbeddedDistributedExecution 等端到端路径）
- [x] **无静默跳过**（Minimum Rules #24）：Fix-5/7/8 新分支全部 typed 异常/毒丸/计数，无空 catch（closure audit 抽查）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿且不少于 Baseline 1 的通过数（1079 ≥ 1061）
- [x] No owner-doc update required（内部失败语义，docs-for-ai 未承诺 discard 策略；已复核 03-modules/nop-stream.md 无相反承诺）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 执行器与任务生命周期（runtime）

Status: completed
Targets: `EmbeddedDistributedExecutor`、`TaskManager`、`JobCoordinator`、`EngineMetrics`、`TaskNodeMetrics`

- Item Types: `Fix`

- [x] Fix-2 启动回收：EmbeddedDistributedExecutor 装配失败路径回收已启动 TaskManager（对齐 RpcDistributedExecutor.teardownOnStartupFailure 语义）
- [x] Fix-4 心跳守卫：TaskManager.heartbeat() 全方法体受 try/catch 保护，异常记日志且不终止调度
- [x] Fix-3 2PC 移出派发线程：TaskManager 增加专用单线程 tm-commit executor，notifyCheckpointComplete 转投执行；【实现裁定】coordinator 侧 registerDistributedCommitForwarder 的 fan-out 本身是轻量 RPC 派发，其阻塞源正是 TM 侧同步提交——TM 异步化后 RPC/embedded 两路径均已解除阻塞，coordinator 侧无需第二层 executor（失败重试语义不变，见 daily log 裁定记录）
- [x] Fix-6 ACK 有限重试：TaskManager.sendCheckpointAck 增加有界重试（指数退避），耗尽后 error 日志+失败计数，不阻塞调用方
- [x] Fix-9 metrics 生命周期：EngineMetrics/TaskNodeMetrics 提供 job/node 级释放路径（remove meter + 清理 refs），同 id 重复注册不再返回失效旧 gauge

Exit Criteria:

- [x] 新增 focused 测试：(a) TestEmbeddedStartupTeardown（装配失败后 tm.isRunning()=false + 健康路径回归）；(b) TestPlan358LifecycleHardening.heartbeatLoopSurvivesUnexpectedLivenessException（爆炸后 3+ 拍仍在跳）；(c) checkpointCommitRunsOnDedicatedCommitExecutor（断言 tm-commit- 线程）；(d) ackSendRetriesTransientFailures（2 失败后第 3 次成功）；(e) TestMetricsLifecycle（release 后同 id 重注册取新 supplier/新实例）
- [x] **接线验证**（Minimum Rules #23）：(a) commitExecutor 被 notifyCheckpointComplete 调用——测试断言线程名 tm-commit-*；(b) releaseJob 挂 JobCoordinator.stop(:651)、releaseNode 挂 TaskManager.stop——测试 TestMetricsLifecycle 走生产 releaseNode 路径断言 registry 移除；TestEmbeddedStartupTeardown 验证 stop 被真实触发
- [x] Fix-3 异步化设计裁定（异常回流=operator 内 catch→既有 coordinator retryFailedCommits/次epoch subsuming；顺序保证=单线程 commit executor 串行保序）记入 daily log
- [x] **端到端验证**（Minimum Rules #22）：runtime 1079 全绿（TestDistributedExactlyOnce/TestCepCheckpointRestoreE2E 等保持通过）；connector-jdbc 43 全绿
- [x] **无静默跳过**（Minimum Rules #24）：新增失败分支显式记账（ackSendFailed 计数 + error 日志），无吞异常
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿且不少于 Baseline 1（1079 ≥ 1061）
- [x] No owner-doc update required（内部生命周期语义不变；新增指标名已回写 docs 指标名表）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 连接器与契约（connector-jdbc / connector / runtime factory）

Status: completed
Targets: `JdbcTwoPhaseCommitSink`、`FileTwoPhaseCommitSink`、`WindowOperatorFactoryImpl`

- Item Types: `Fix`

- [x] Fix-10 JDBC safe init：ensureInitialized 线程安全化（同锁覆盖 saveState 与 commit 两个首次入口）
- [x] Fix-11 JDBC 批分段：executeBatch 按 maxBatch（默认 1000，可配置）分段执行，语义不变
- [x] Fix-12 fsync：FileTwoPhaseCommitSink 在 manifest 原子替换前对 temp 数据文件与 manifest 做 force（掉电窗口闭合；性能影响限于每 checkpoint 一次提交，可接受）
- [x] Fix-13 dummy serializer 契约：createInstance 失败抛 typed 异常替代静默 null；copy/isImmutableType 契约诚实化（无活跃调用点，最小修正并注释）

Exit Criteria:

- [x] 新增 focused 测试：(a) TestJdbcConcurrencyAndBatchSegments.concurrentFirstInitializationIsSafe（barrier 并发 init + 提交功能验证）；(b) largeEpochIsCommittedInSegmentsWithIdenticalResult（maxBatch=2、5 行分段=整批结果 + ledger 记录）；(c) manifest force 路径：现有 file 2PC 测试 69 条全绿 + 代码追踪（doCommitLocked:forcePath(tempPath)→move→updateManifestAtomically 内 forcePath→move→forceDirectoryQuietly，路径必经）；(d) TestWindowDummySerializerContract.createInstanceFailsTypedException
- [x] **端到端验证**（Minimum Rules #22）：TestDistributedExactlyOnce/TestFileTwoPhase* 系列在 runtime 1079 与 connector 69 全绿中覆盖
- [x] **无静默跳过**（Minimum Rules #24）：Fix-13 createInstance 抛 ERR_STREAM_SERIALIZATION，无静默 null 路径
- [x] `./mvnw test -pl nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector -am` 全绿（43 + 69）
- [x] No owner-doc update required（exactly-once 承诺是既有文档契约，本 Phase 使实现更接近契约而非改变契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria 全部 `[x]` 后，按 plan guide 流程执行 closure audit。

- [x] 13 项已确认 live defect 全部修复或有显式裁定记录（不允许降级为 advisory）
- [x] 数据面无新增静默丢弃路径（Fix-5/7/8 验证记录在案）
- [x] 资源生命周期闭环有测试证据（订阅/meter/TM）
- [x] 三个 Phase 的 focused verification 全部完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步，或明确 No owner-doc update required（三 Phase 各有裁定）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 订阅关闭链从任务关闭入口到 messageService.cancel 运行时连通；(b) 2PC executor 被真实调用；(c) scan-hollow-implementations 无 high/critical 发现
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无——13 项全部为 in-scope Fix，不设延期项）

## Non-Blocking Follow-ups

- TaskManager 容量 16 硬编码配置化（optimization candidate；Why Not Blocking Closure：不属缺陷，16 满足现有点位规模，配置化属 API 面扩大需独立裁定）
- FileSourceReader pollNext 锁内阻塞 I/O 缩界（watch-only residual；Why Not Blocking Closure：P3 级且锁为游标一致性刻意设计，无 successor plan，后续按需立计划；不在 360 范围内）
- ACK 重试耗尽后的进阶策略（失败队列/协调器反向查询）（optimization candidate；Why Not Blocking Closure：超时 abort 兜底仍存在，重试已覆盖瞬时失败主场景）
- WindowedStreamImpl/PendingCheckpoint 等公共 API 层 StreamException 无 ErrorCode 的规范补全（→ 移入 359 Phase 3 处理，非本计划 scope）

## Closure

Status Note: 13 项已确认 live defect 全部修复且各有 focused 测试；三 Phase Exit Criteria 与 Closure Gates 逐条核验通过。独立子 agent 首轮审计 REJECT 指出 `InputGate.close()`（Fix-1 新增公有方法）未登记进 nop-stream 不变量门禁清单（TestInvariantTableCompleteness 确定性失败），已补登记 `ai-dev/audits/nop-stream-invariants/gate-inventory.json` 并以真实 `-am` 三模块口径复跑全绿后放行。
Completed: 2026-09-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_9f57f31e-259e-4075-9e04-215615373fb1）
- Audit Session: agent_9f57f31e-259e-4075-9e04-215615373fb1（首轮 REJECT→修复→复核放行）
- Evidence:
  - Phase 1：Fix-1 调用链追通（StreamTaskInvokable 四 finally:718/762/796/846 → InputGate.close:404 → RemoteInputChannel.close:499 → subscription.cancel），TestRemoteTransportLifecycle 7/7 实测绿；Fix-5 typed（RemoteResultPartition:198-211）；Fix-7 全 offer 校验；Fix-8 WireDecodeFailureAware 双路径转发——全部 PASS
  - Phase 2：Fix-2（execute:152-193 启动段入 try + teardown 判空，测试断言 isRunning=false）；Fix-3（tm-commit 线程名断言实测绿）；Fix-4（heartbeat 外层守卫 :302/:342 + 爆炸后 3 拍存活测试）；Fix-6（3 次重试 + ackSendFailed 计数断言）；Fix-9（releaseJob 挂 JobCoordinator.stop:653、releaseNode 挂 TaskManager.stop:266，gauge 旧 supplier 失效修复实测）——全部 PASS
  - Phase 3：Fix-10 双检锁（initialized volatile :123）；Fix-11 分段（writeDataRows:486-496 单事务多段）；Fix-12 fsync 顺序 forcePath→move→forcePath→move→dir force 与计划一致；Fix-13（isImmutableType=false :227、typed 抛出 :240-244）——全部 PASS
  - Anti-Hollow：订阅关闭链/2PC executor/释放路径运行时连通（代码追踪 + 计数 stub 断言）；scan-hollow-implementations --severity high 退出码 0；grep 无旧 WARN-only EOS 路径残留
  - 门禁：`./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector -am` BUILD SUCCESS（runtime 1079 / jdbc 43 / connector 69 / core 含 TestInvariantTableCompleteness 10/10，全绿）；`node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0
  - Deferred 分类检查：4 项 follow-up 均为 optimization/watch-only/capacity 配置化/错误码移交 359，无 in-scope live defect 降级（审计逐项核实）
  - 首轮审计发现的证据口径问题已更正：1079 为 runtime-only 计数，`-am` 口径全绿以本 closure 复跑（BUILD SUCCESS，见上）为准
  - Doc-sync：docs-for-ai/03-modules/nop-stream.md 指标名表已同步（ackSendFailures.total + running gauge 生命周期语义）

Follow-up:

- TaskManager 容量 16 硬编码配置化（optimization candidate）
- FileSourceReader pollNext 锁内阻塞 I/O 缩界（watch-only residual）
- ACK 重试耗尽后进阶策略（optimization candidate；超时 abort 兜底仍在）
- 错误码补全已移交 359 Phase 3 承接
