# C10 并发正确性的测试保护力 — nop-stream-core-runtime

- **审计日期**: 2026-10-14
- **维度**: C10（并发/资源/规模缺陷的测试保护力）——本 campaign 最后批次
- **被审单元**: `nop-stream/nop-stream-core` 与 `nop-stream/nop-stream-runtime` 的 `src/test`（合计 652 个测试文件：core 322 / runtime 330）
- **输入**: 本 campaign C1–C5、C7–C9 八个维度复核后的缺陷清单（16 条 P0/P1 登记 + 各维度关键 P2）。按盲审纪律，未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`，`ai-dev/audits/` 下仅读本 campaign 的 8 个维度文件。
- **方法**: 变异推演（对每条已保留缺陷回答「补上正确实现后，若有人回退它，现有哪个测试会红？」）+ 时序敏感测试审查 + 资源泄漏钩子盘点 + 规模边界测试盘点。逐文件直读了约 20 个关键测试的断言体（非仅看文件名），包括 TestTwoPhaseCommitSinkFunction、TestCachingTableLookup、TestTaskExecutor、TestTaskManagerCommitQueueBound、TestConcurrencyWindows2326、TestFencingTokenRejection、TestCommitFanoutOffMonitor2326、TestCheckpointAbortMarkerCleanup、TestResultPartitionOverflowBypass、TestResultPartitionDeadlock、TestMaterializationPoint、TestTaskMailbox、TestProcessingTimeServiceDriver、TestCheckpointBarrierTrackerConcurrency、TestSourceReaderOperatorRestoreBeforeOpen、TestJobCoordinatorStandbyStateMachine、TestStreamOpsHttpServer、TestKeyedStateQueryE2E、TestInMemoryClusterRegistryAttemptHistory、TestInMemoryClusterRegistryFixes 等。

---

## 一、既有并发/资源/规模测试盘点

### 1.1 直接以并发正确性为断言对象的测试（保护力主干）

| 测试 | 保护范围 | 判定 |
|---|---|---|
| `core/execution/TestCheckpointBarrierTrackerConcurrency` | 多 epoch ACK 独立、exactly-once 回调（`@RepeatedTest(10)` 压力放大）、`getCurrentCheckpointId` 并发变异下 CME-free | 强（有 400 轮 hammer + 重复 10 次） |
| `core/execution/TestTaskMailbox` | 多生产者单消费者（4×500 mail）、control 优先级、close 后 take 退出 | 强（原语面） |
| `core/common/functions/sink/TestTwoPhaseCommitSinkFunction` | restore 语义（durable/non-durable 分流）+ 10 线程 finishCommit 仅断言不抛 CME | 中（见 C10-011：并发测试零判别力） |
| `core/connector/lookup/TestCachingTableLookup` | TTL/空值穿透/后台刷新/并发上限双断言（overlap≥2 且 ≤cap）/刷新失败保旧值/maxEntries 驱逐/close 幂等 | 强（队列深度与 in-flight 去重面除外，见 C10-003） |
| `runtime/checkpoint/TestCommitFanoutOffMonitor2326` | R6 CC-01 pin：慢 participant 的 finishCommit 不得阻塞 coordinator monitor（latch seam + monitor 探针） | 强（非饱和窗口，见 C10-012） |
| `runtime/checkpoint/TestCheckpointAbortMarkerCleanup` | abort 后 marker 清理时序 + 失败参与者入重试环、marker 不得提前清除 | 强（上界/出口面除外，见 C10-018） |
| `runtime/taskmanager/TestConcurrencyWindows2326` | R6 CC-02 pin（stale generation 不挤位，`selectDisplacedTask` 决策函数）+ CC-04 pin（末任务退出释放 source coordinator registry） | 中（receiveAssignment 重复槽位分支与 installInvokable 无 epoch 校验未覆盖，见 C10-010；文件 javadoc 自认 CC-03/同族确定性 pin 需 latch seam，登记为 follow-up） |
| `runtime/taskmanager/TestTaskManagerCommitQueueBound` | R5-CC-10 pin：commit 队列有界 + CallerRuns 回退 + 不丢通知 + 计数/depth gauge | 强（stop 竞速窗口除外，见 C10-016） |
| `runtime/taskmanager/TestFencingTokenRejection` | stale token 的 assignment/checkpoint 双入口 typed 拒绝（anti-hollow：回退为 warn+return 即红） | 强（进程内面） |
| `runtime/cluster/TestInMemoryClusterRegistryFixes` | renewLease 并发（10 线程）、租约过期/驱逐 | 中（history 读写锁面无并发测试） |
| `core/execution/TestResultPartitionDeadlock`、`TestResultPartitionOverflowBypass` | close 满队背压（负向窗口 staysTrue）、溢出 bypass 不阻塞、无物化时保持阻塞语义 | 强——但 overflow bypass 测试把缺陷行为钉为正确（见 C10-005 假保护判定） |
| `core/operators/TestSourceReaderOperatorRestoreBeforeOpen` | P1-6.7-008 pin：restore 先于 open 的位点不丢、flush 先于 start | 强（该缺陷面） |
| `runtime/coordinator/TestJobCoordinatorRecoveryConcurrency`、`TestJobCoordinatorStandbyStateMachine`、`TestJdbcLeaderElector*` | 恢复并发、standby 状态机、选主 epoch 单调 | 中（standby 只断言新 trigger 被拒，见 C10-009） |
| `runtime/execution/TestRpcDistributedExecutorFailureTeardown` | R-19 pin：startJob 失败后心跳线程必须消失（`Thread.getAllStackTraces` 名前缀扫描） | 强（线程泄漏钩子范式，见 §三） |

### 1.2 资源/背压/边界语义测试（C8 相关面）

TestBufferPool 系列、TestBufferPoolWiring、TestBufferPoolResultPartitionIntegration、TestRemoteInputChannelQueueFull、TestChannelQueueFillRatioGauge、TestChannelQueueGauges（D2c close-release + 重绑定）、TestCheckpointTerminationBudget、TestCheckpointTriggerBoundedRetry、TestJobCoordinatorStallRecoveryBudget、TestCheckpointMinPauseAndFailureCounter、TestTaskTeardownCloseChain、TestSinkSelfContainedCloseSuppression、TestTaskManagerDaemon（daemon 标志 + 线程存在性）、TestFailJobCancelsAssignedTasks。

### 1.3 明显缺位的测试面（本维度登记的根据）

- `LocalSourceCoordinator` 没有任何并发驱动测试（全仓 2 处直接构造均在单线程 wiring/E2E 测试中；`snapshotState` 与 `registerReader/requestSplits` 从未被两线程交叠驱动）。
- `TaskExecutor` 的 LOCAL 拓扑（Σsubtask > 池大小）死锁场景无测试（现有测试全部用 no-op invokable + 池≥任务数）。
- `PollingJdbcMessageService` 无专属单测；multijvm 两个测试把它当真实传输用，但无 LIMIT/backlog/表清理断言。
- `OpsJobManager` 无 submit 失败→孤儿协调器的测试（TestGovernanceSweepContainment 未覆盖 submit 路径）。
- `StreamOpsHttpServer` 无请求体上界、无 stop 后线程清理断言；`/jobs` 提交的 E2E（TestOpsRestLifecycleE2E）只走功能正路径。
- `SideOutputRegistry`、`BasicTypeInfo`、`KeyedStreamImpl` 聚合热路径、`WebhookAlertChannel.delivered`、`MicrometerStreamTaskMetrics` 的 meter 移除，均无专属测试。

---

## 二、保护力矩阵（缺陷 × 测试 × 变异结论）

变异结论口径：**会红** = 存在断言正确行为的测试，回退实现即失败；**假保护** = 现有测试把缺陷行为钉为「正确」，修复必须同步改测试（负保护）；**无测试** = 无任何测试会红（缺保护，P2 立项见 §五）。跨维度重复登记的同一根因合并为一行（括号内为同根因编号）。

| # | 缺陷 ID（根因） | 严重度 | 现有最近测试 | 变异推演结论 | 说明 |
|---|---|---|---|---|---|
| M1 | C1-001 / C2-001 / C3-001（LocalSourceCoordinator 锁纪律） | P1×3 | TestSourceApiRouting、TestSourceEnumeratorManifestWiring（均单线程） | **无测试** | 全仓无线程交叠驱动 addReader/requestSplits vs snapshotState；→ C10-001 |
| M2 | C1-006 / C7-002（分配账本只增不删） | P1+P2 | TestInMemoryClusterRegistryAttemptHistory、TestClusterRegistryConsistencyInvariant | **会红（API 面）/ 无测试（接线面）** | `removeTaskAssignmentClearsHistory` 钉住 API 语义；生产 globalRecovery 不清账本的缺陷本体（零调用方）无测试会红；→ C10-020 |
| M3 | C4-001 / C8-013（LOCAL 池死锁，已知 r5 CC-05） | P1×2 | TestTaskExecutor（no-op 任务，池≥任务数） | **无测试** | 无 Σsubtask>cores 拓扑、无阻塞 sink、无 awaitCompletion 超时路径；→ C10-002 |
| M4 | C4-002 / C3-002 / C8-014（刷新队列无界 + 无 in-flight 去重） | P1+P2 | TestCachingTableLookup | **无测试（积压面）** | 并发上限双断言在位（回退并发上限会红），但慢 delegate 下排队深度/重复入队无任何断言；→ C10-003 |
| M5 | C4-009（submit 失败孤儿协调器） | P1 | TestOpsRestLifecycleE2E、TestGovernanceSweepContainment | **无测试** | 全部走成功路径；无 submit 抛错→线程/池/registry 清理断言；→ C10-004 |
| M6 | C5-001 / C7-001（materialization store 无界） | P1×2 | TestMaterializationPoint（round-trip 语义） | **无测试** | 无 store 大小上界/淘汰断言；→ C10-005 |
| M7 | C8-001（overflow-bypass 架空背压） | P1 | TestResultPartitionOverflowBypass | **假保护** | 测试断言「bypass 后写入不阻塞且全部进 store」——把缺陷行为钉为交付语义；修复（保留池许可或有界 store）会使该测试红，需同步改写；→ C10-005 |
| M8 | C5-010 / C8-002（poll 无 LIMIT 全量物化） | P1×2 | TestMultiJvmExactlyOnceRecovery（真实传输） | **无测试** | 无积压→单 poll 物化量的边界断言；→ C10-006 |
| M9 | C8-003 / C7-003（msg_queue INSERT-only 账本） | P1+P2 | 同上 | **无测试** | 无表行数/cleanup 断言；→ C10-007 |
| M10 | C8-011 / C4-004（2 线程 poller 共享饥饿） | P1+P2 | TestMultiJvm*、TestLaunchMainScopeWiring | **无测试** | 无「慢数据面垄断派发线程→控制面延迟」断言；→ C10-008 |
| M11 | C7-011（standby 降级不取消在途 persist） | P1 | TestJobCoordinatorStandbyStateMachine | **无测试（残余面）** | `testActiveModeTriggerCheckpointSucceedsAndStandbyRejects` 只断言 standby 拒绝**新** trigger；在途 persist 取消/复核无断言；→ C10-009 |
| M12 | C1-004（两阶段分配 epoch 守卫残留） | P2 | TestConcurrencyWindows2326、TestFencingTokenRejection | **无测试（残留分支）** | CC-02 pin 只钉 `selectDisplacedTask` 决策函数与 deployTask compute；receiveAssignment 重复槽位分支与 installInvokable 的无 epoch 校验无测试；→ C10-010 |
| M13 | C1-005 / C2-007（finishCommit TOCTOU 双重提交） | P2+P3 | TestTwoPhaseCommitSinkFunction.testConcurrentFinishCommitNoConcurrentModificationException | **无测试（判别力为零）** | 10 线程用**互不相同的 epochId**、只断言不抛异常；补原子 remove-then-commit 后该测试仍绿；同 epoch 并发双提交无断言；→ C10-011 |
| M14 | C2-003 / C3-003（commit-fanout CallerRuns 拉回 monitor） | P2×2 | TestCommitFanoutOffMonitor2326 | **无测试（饱和窗口）** | CC-01 pin 保护「整体内联回退」变异（会红），但饱和回退（CallerRuns 命中）路径无测试：participant 只 sleep 600ms，队列 1000 永不饱和；→ C10-012 |
| M15 | C2-005（triggerCheckpoint 持 monitor 直通快照） | P2 | TestCheckpointBarrierTrackerConcurrency、TestSourcePullBarrierInjection | **无测试（争用面）** | 并发 ACK exactly-once 有强保护；快照耗时与 ACK 串行化的持锁时长无断言；→ C10-013 |
| M16 | C3-009（queryable-state 跨线程快照 CME） | P2 | TestKeyedStateQueryE2E、TestKeyedStateSnapshotAccessor | **无测试（竞争面）** | E2E 只在静态状态下查询；无「任务线程持续写 + 查询线程并发读」交叠；→ C10-014 |
| M17 | C4-006（LOCAL 取消路径不对称） | P2 | TestBufferPoolWiring 等 | **无测试** | 无「interrupt 后 finally 不 cancel 子任务→消费者自旋」断言；→ C10-015 |
| M18 | C4-012（stop 竞速窗口静默丢 2PC 通知） | P2 | TestTaskManagerCommitQueueBound | **无测试（shutdown 态）** | CallerRuns 计数 pin 只覆盖非 shutdown 态；isShutdown 分支静默丢弃无测试；→ C10-016 |
| M19 | C5-004 / C8-008（提交端点无大小上限） | P2×2 | TestStreamOpsHttpServer、TestOpsRestLifecycleE2E | **无测试** | 无超大 body→413/拒绝断言；→ C10-017 |
| M20 | C5-006（failedCommitParticipants 条件无界） | P2 | TestCheckpointAbortMarkerCleanup | **无测试（上界面）** | 「marker 不得提前清除」有 anti-hollow pin（回退提前清除会红）；持续失败下条目累积无上界/出口断言；→ C10-018 |
| M21 | C8-009（duplicate 静默吞没 + submit 拒绝不清理） | P2 | TestTaskManager.testDuplicateAssignmentIgnored、testCancelTaskDoesNotDoubleReleaseSemaphore | **部分：会红（去重位面）/ 无测试（上报与清理面）** | 重复分配不挤位、cancel 不双释放有保护；duplicate 分支零上报、拒绝路径许可/登记泄漏无断言；→ C10-019 |
| M22 | C7-004 / C8-004 / C5-005（timer mail 无去抖 + 邮箱无界） | P2×3 | TestProcessingTimeServiceDriver、TestTaskMailbox | **无测试（积压面）** | 「timer 未到期不发 mail」「shutdown 后不发」有保护；「timer 到期但任务线程长阻塞→mail 以 10Hz 累积」无断言；→ C10-021 |
| M23 | C1-002 / C5-007 / C7-006（SideOutputRegistry 注册滞留/串台） | P2×3 | TestSideOutputChainingE2E、TestWindowedStreamSideOutputLateData | **无测试（生命周期面）** | 功能链路有保护；跨 job 同名 tag 串台、终态注册滞留、remove 零接线无断言；→ C10-022 |
| M24 | C7-007（metrics releaseHolder 不移除条目/meter） | P2 | TestMicrometerStreamTaskMetrics | **无测试** | 只测注册与计数，无 release 后 meter 移除断言（对照：ChannelQueueGauges 的 D2c release 有测试）；→ C10-023 |
| M25 | C9-001（聚合热路径每记录全量反射） | P2 | TestWindowTranslation 等功能测试 | **无测试（性能面）** | 行为回退有功能测试保护；「补上 Field 缓存」无任何测试/基准门禁会红（BG-3 基准缺口已登记）；→ C10-024 |
| M26 | C9-004（SourceReaderOperator 空闲 yield 自旋） | P2 | TestStreamSourceOperator | **无测试** | 无空闲 CPU 占用/驻停行为断言（BG-4 基准缺口已登记）；→ C10-025 |
| M27 | C1-003（BasicTypeInfo static HashMap 竞态） | P2 | 仅单线程功能引用 | 无测试（理论竞态面） | 未立项（C1 维度关键 P2 取 C1-004/005）；登记建议随 C10-010 的守卫测试形态合并考虑 |
| M28 | C2-004（registry 读路径不加锁） | P2 | TestInMemoryClusterRegistry* | 无测试（并发读面） | 未立项（C2 维度关键 P2 取 C2-003/005）；理论风险未具体化 |
| M29 | C9-003（驱逐 O(n) 热路径扫描） | P2 | TestCachingTableLookup.maxEntriesEvictsOldestLoadedVictim | **会红（正确性面）/ 无测试（性能面）** | 驱逐语义回退会红；驱逐算法复杂度无基准门禁（BG-2） |
| M30 | C8-005（反序列化失败静默跳过） | P2 | 无专属测试 | 无测试 | 未立项（C8 关键 P2 取 C8-009 及合并根因）；建议与 C10-006 的 poll 测试形态合并 |
| M31 | C4-003 / C2-009 / C7-013 / C5-012 等其余 P2/P3 | P2/P3 | 各自功能测试 | 无测试/部分 | 未逐一立项：C4-003（stop 后线程清理）可复用 TestTaskManagerDaemon 的 `threadExists` 钩子；C2-009 已有 TestCheckpointMetricsSynchronizedSnapshot 钉锁形状；C7-013/C5-012 建议随修复批补小规模参数化测试 |

**矩阵规模**：覆盖缺陷登记 31 行（16 条 P1 + 15 条关键 P2，按去重根因合并为 31 个矩阵行 / 24 个独立根因）。其中：**有保护（会红）** 2 行的部分面（M2 API 面、M21 去重面）+ M29 正确性面；**假保护** 1 行（M7）；**无测试（缺保护）** 25 项立项（§五）。16 条 P1 登记中：**0 条有完整回归保护**，1 条假保护（C8-001），其余 15 条的缺陷本体回退后无任何现有测试会红。

---

## 三、时序敏感测试审查

**总体纪律良好**：`testsupport/TestAwait`（core 与 runtime 各一份）提供 `until/staysTrue/elapsed/untilThreadParked` 四种形态——正向等待带 60s 死锁保护、负向窗口（staysTrue）用于「必须持续阻塞」类断言（TestResultPartitionDeadlock、TestResultPartitionOverflowBypass.noMaterialization、TestTaskMailbox.take-blocks 均采用）。全仓 652 个测试文件共 175 处 `Thread.sleep`，绝大多数位于带 deadline 的轮询循环内，符合「时间语义必须流逝的场景」豁免。

残留的时序敏感形态（按风险排序）：

1. **单次裸 sleep 承载负向断言**（平台性脆弱）：`TestProcessingTimeServiceDriver.testNoMailWhenTimerNotYetDue:115`（sleep(60) 后断言邮箱空）、`testShutdownStopsFiring:158`（sleep(60) 后断言邮箱空）、`testStartIsIdempotent:178`（sleep(30)）——慢机器上 60ms 窗口可能足以让 fire mail 落入，偶发翻红；建议改 `TestAwait.staysTrue`。
2. **测试自己 catch 掉竞争**（superficially-pass）：`TestTwoPhaseCommitSinkFunction.testConcurrentFinishCommitNoConcurrentModificationException:196` 对每个工作线程 `catch (Throwable t) { failed.set(true) }` 且只断言不抛——状态被竞争破坏但不抛异常的情形全数放行（与 C10-011 同源）；`TestTaskExecutor.testTaskCannotCancelAfterStart:304-318` 明确容忍任意时序（「仅验证不抛」），判别力为零。
3. **时钟粒度依赖**：`TestCachingTableLookup.maxEntriesEvictsOldestLoadedVictim:240-242` 用 `Thread.sleep(5)` 制造最老受害者次序，粗粒度时钟（Windows ~15ms）下两次 load 可能同戳，偶发翻红。
4. **hold 超时静默降级**：`TestCachingTableLookup.StubLookup.lookup` 的 holdKeys 阻塞带 5s deadline 后自动放行——若被测实现死锁超过 5s，测试会以「假绿」通过而非失败；建议 deadline 到期时置失败标志。
5. **无 Awaitility 依赖**：全仓一致使用自研 deadline-poll，无「未等待异步结果就断言」的系统性问题；`TestCheckpointBarrierTrackerConcurrency` 的 `@RepeatedTest(10)` + 400 轮 hammer 是本模块竞争测试的正确范式。

---

## 四、资源泄漏测试钩子盘点

**已有钩子**（可作为补测试的范式）：

| 钩子 | 出处 | 覆盖 |
|---|---|---|
| `Thread.getAllStackTraces` 名前缀扫描 | TestRpcDistributedExecutorFailureTeardown（R-19：startJob 失败后心跳线程必须消失）、TestTaskManagerDaemon（`threadExists` + daemon 标志） | 线程泄漏 |
| gauge close-release 断言 | TestChannelQueueGauges（D2c：旧 channel 关闭后 gauge 归零不冻结 + 重绑定跟随） | gauge 生命周期（仅 ChannelQueueGauges；兄弟 DbBacklogGauges 无对应测试，C1-009） |
| registry 条目释放 pin | TestConcurrencyWindows2326（CC-04：末任务退出释放 SourceCoordinatorRegistry 条目）+ `clearForTest()/unregister` teardown 钩子 | 进程级注册表（仅该 registry；SideOutputRegistry 只有 clearForTest 无泄漏断言） |
| 队列深度 gauge + 容量断言 | TestTaskManagerCommitQueueBound（depth ≤ capacity + gauge 注册） | 有界队列可观测 |
| permit 守恒断言 | TestTaskManager.testCancelTaskDoesNotDoubleReleaseSemaphore、TestRemoteInputChannelQueueFull、TestBufferPool 系列 | 信号量/buffer 许可 |

**缺口**（对应 §五登记）：materialization store 大小无断言（C10-005）、msg_queue 表行数无断言（C10-007）、WebhookAlertChannel.delivered 无上界/清理断言、MicrometerStreamTaskMetrics 无 release 后 meter 移除断言（C10-023）、OpsJobManager submit 失败后无线程/池泄漏断言（C10-004，可直接复用 R-19 钩子范式）、StreamOpsHttpServer stop 后无 executor 线程断言（C4-003）、LocalFileCheckpointStorage 无 .tmp 残留清扫断言（C7-013）。

---

## 五、大规模边界测试盘点与缺保护登记

**已有小规模参数化边界测试**：ResultPartition 容量 2/4 满界（OverflowBypass/Deadlock）、commit 队列 capacity=2 饱和（CommitQueueBound）、channel queue 满（RemoteInputChannelQueueFull）、缓存 maxEntries=2 驱逐（CachingTableLookup）、BufferPool 容量、checkpoint 终止预算/最小间隔。范式在位、覆盖点选择性良好。

**边界缺位**：TaskExecutor Σsubtask>cores（C10-002）、mailbox 高水位（C10-021）、poll 积压 LIMIT（C10-006）、materialization store 容量（C10-005）、HTTP body 上限（C10-017）、刷新队列深度（C10-003）。以下按统一格式登记「缺保护」项（P2，与缺陷本体分开计数）。

---

### [nop-stream-core-runtime-C10-001] 缺保护：C1-001 / C2-001 / C3-001（LocalSourceCoordinator 锁纪律）
- **关联缺陷**: C1-001、C2-001、C3-001（同一根因的三维度登记，均 P1）：`LocalSourceCoordinator` 的 `registerReader→addReader`（:186）与 `ReaderChannel.requestSplits→handleSplitRequest`（:260）在 `enumeratorLock` 之外直调枚举器，与持锁的 `snapshotState`（:297）三态并存。
- **现有测试盘点**: 全仓仅 2 处直接构造 `LocalSourceCoordinator`（TestConcurrencyWindows2326:129、TestSourceEnumeratorManifestWiring:306），均为单线程 wiring/E2E；`snapshotState` 的测试引用全部单线程。没有任何测试从两个线程交叠驱动 addReader/requestSplits 与 snapshotState。
- **变异推演结论**: 补上「全入口持 enumeratorLock」的正确实现后，现有全部测试保持绿；同理回退修复（把锁去掉）也不会有任何测试红。并行 source 启动竞态（双份分配/快照撕裂/CME）完全无回归保护。
- **建议测试形态**: 仿 TestCommitFanOutOffMonitor2326 的 latch-seam 范式：用 DebeziumTableSplitEnumerator（在库 CME 受害者，`shareBySubtask` 为普通 LinkedHashMap）作枚举器，线程 A 在 `snapshotState` 遍历中途 latch 阻塞，线程 B 调 `registerReader`/`requestSplits`，断言要么结构不变式成立（快照与投递互斥）要么 typed 异常；配合 `@RepeatedTest` 压力放大。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-002] 缺保护：C4-001 / C8-013（LOCAL 池死锁，已知未修复）
- **关联缺陷**: C4-001、C8-013（同一根因，P1×2，已知 r5 CC-05）：core `TaskExecutor` 固定池=核数 + 无界队列 + 长驻任务阻塞有界输出队列，Σsubtask>核数时池内互等，宿主线程永久挂死且三层检测全盲。
- **现有测试盘点**: `TestTaskExecutor` 全部用 no-op/failing invokable，池大小（2/4）≥ 提交任务数，`awaitCompletion` 均能在 5s 内返回；`TestResultPartitionDeadlock` 只覆盖单 partition 的 close 背压。无任何测试构造「下游阻塞 + 拓扑规模超池」的 LOCAL 执行。
- **变异推演结论**: 补上有界队列+容量守卫/拒绝（或容量预检 fail-fast）后无测试会红；回退守卫同样无测试会红。这是 16 条 P1 中后果最重（宿主永久挂死）却完全无保护的一条。
- **建议测试形态**: 小规模参数化：池大小=1、两 vertex 链（上游生产 2 条、下游阻塞 sink），断言 `execute()` 在超时预算内 typed 失败（容量守卫正确实现）而非无限挂起；配 `TestAwait.staysTrue` 负向窗口验证挂死形态可被检测面观测。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-003] 缺保护：C4-002 / C3-002 / C8-014（CachingTableLookup 刷新队列无界 + 无 in-flight 去重）
- **关联缺陷**: C4-002（P1）、C3-002（P2）、C8-014（P2，同一类三个维度登记）：后台刷新 `newFixedThreadPool` 队列无界、`scanAndDispatch` 无 per-key 在途去重，delegate 慢/挂死时重复刷新任务线性累积。
- **现有测试盘点**: `TestCachingTableLookup.refreshConcurrencyOverlapsAndStaysCapped` 是在库最佳并发测试之一（overlap≥2 且 ≤cap 双断言 + latch 释放）；但排队深度、同 key 重复入队、恢复期倾泻三个面均无断言。StubLookup 的 hold 带 5s deadline 自动放行（见 §三.4），恰好掩盖挂死形态。
- **变异推演结论**: 补上 in-flight 去重 + 有界队列后，现有 9 个测试全绿（并发上限断言对去重不敏感）；回退去重亦无测试红。
- **建议测试形态**: holdKeys 长时间持住 delegate（≥2 个扫描周期），统计 stub.loadCount 增量断言「同 key 至多一次在途刷新」（去重正确实现下不随扫描轮数增长）；再断言挂起期间刷新队列深度有界。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-004] 缺保护：C4-009（OpsJobManager submit 失败孤儿协调器）
- **关联缺陷**: C4-009（P1）：`OpsJobManager.submit` 的 `start()/assignTasks()/startPeriodicCheckpoints()→jobs.put` 序列无 try/finally 补偿，失败窗口泄漏 `jc-failure-detector` 线程 + CheckpointCoordinator 池组，同 jobId 重提交产生双活协调器。
- **现有测试盘点**: TestOpsRestLifecycleE2E 只走成功提交（201 RUNNING）生命周期；TestGovernanceSweepContainment 未覆盖 submit 抛错路径；全仓无「AssignmentPlanner typed 抛错→submit 清理」断言。
- **变异推演结论**: 补上 try/finally 补偿（teardownOnStartupFailure 形态，RpcDistributedExecutor 已有先例）后无测试会红；回退补偿亦无测试红。同族先例 R-19 有测试（TestRpcDistributedExecutorFailureTeardown），本类是「修实例不修类别」的未覆盖兄弟点。
- **建议测试形态**: 注入 assignTasks 抛错的 stub registry，断言 submit 抛出后 `Thread.getAllStackTraces` 无 `jc-failure-detector-<jobId>` 残留、checkpoint 池已 shutdown、重复 submit 第二次不再产生双活（复用 R-19 钩子范式）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-005] 缺保护：C5-001 / C7-001 / C8-001（materialization store 无界 + overflow-bypass 架空背压）
- **关联缺陷**: C5-001（P1）、C7-001（P1）、C8-001（P1，同一根因族）：`InMemoryMaterializationPoint` append-only 无淘汰；`ResultPartition.enqueueWithBackpressure` 队列满即释放池许可、记录仅存 store，背压被旁路。
- **现有测试盘点**: TestMaterializationPoint 只测 round-trip/seal/防御拷贝语义；**TestResultPartitionOverflowBypass 把缺陷行为钉为交付语义**——`overflowBypass_producerDoesNotBlockAndDataLandsInMaterializationStore:41-108` 断言 14 条溢出记录全部驻留 store 且池许可被释放，javadoc 明言「failover-design §9.4」的期望行为。
- **变异推演结论**: 这是全矩阵唯一的**假保护**：修复（store 有界/截断或保留池许可）必然使该测试红，修复批必须同步改写其断言；在改写之前，该测试持续「保护」缺陷行为，任何回归防线都不存在。
- **建议测试形态**: 新增参数化边界测试：store 写入量超过声明上界时断言 typed 淘汰/截断或池许可不释放（背压生效）；保留 bypass 不阻塞的既有正确半边（producer 线程不 WAITING），改钉「不阻塞且有界」双不变式。
- **严重程度**: P2（缺保护立项；含 1 条假保护判定）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-006] 缺保护：C5-010 / C8-002（PollingJdbcMessageService poll 无 LIMIT 全量物化）
- **关联缺陷**: C5-010、C8-002（同一根因，P1×2）：`poll()` SELECT 无 LIMIT，`readRows` 将 cursor 之后全部行（含 payload byte[]）物化进 ArrayList，cursor=0 引导 + 无 ChannelBacklogProbe 使 WI6 门控不可达。
- **现有测试盘点**: `PollingJdbcMessageService` 无专属单测；multijvm 两个测试（TestMultiJvmExactlyOnceRecovery、TestMultiJvmCoordinatorFailover）将其作为真实传输驱动，均小消息量、无积压构造，无任何物化量断言。
- **变异推演结论**: 补上 LIMIT/批量上限后无测试会红；回退 LIMIT 亦无测试红。
- **建议测试形态**: 内存 JDBC（或 H2）预置 N 行积压（N 取小规模如 100），断言单次 poll 交付 ≤ LIMIT 且游标推进可续；再加 cursor=0 引导场景断言首订阅不一次性物化全表。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-007] 缺保护：C8-003 / C7-003（msg_queue INSERT-only 无界账本）
- **关联缺陷**: C8-003（P1）、C7-003（P2）：`nop_stream_msg_queue` INSERT-only，唯一删除路径 `clearTopics` 为 test helper 零调用方，无运行时保留/清理路径。
- **现有测试盘点**: 同 C10-006（无专属测试；multijvm 用后不清理也不断言表行数）。C7-002 旁证：`TestClusterRegistryConsistencyInvariant` 对 cluster registry 的账本有一致性断言，但对 msg_queue 无对应物。
- **变异推演结论**: 补上保留/TTL/清理路径后无测试会红；清理路径回退（重归 INSERT-only）亦无测试红。
- **建议测试形态**: 小规模参数化：发送 N 条 + 全部消费后，断言 ack/cleanup 调用后表行数有界（或按 TTL 过期可清）；配合 limit 断言与 C10-006 共用一个 JDBC 测试夹具。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-008] 缺保护：C8-011 / C4-004（2 线程 poller 共享饥饿）
- **关联缺陷**: C8-011（P1）、C4-004（P2）：launch 共享 2 线程调度池承载全部订阅 poll，慢数据面（每行 ≤10s × 无界批量）垄断派发线程，控制面（barrier/deploy/cancel）无界期饥饿。
- **现有测试盘点**: TestLaunchMainScopeWiring 只断言装配形态；multijvm 测试无「慢数据面 + 控制面并发」构造；无任何时延/隔离断言。
- **变异推演结论**: 补上控制/数据面池隔离（或 per-submission 超时）后无测试会红；回退共享亦无测试红。
- **建议测试形态**: 两个订阅共用服务：订阅 A 的 consumer 阻塞在慢行上，订阅 B（控制 topic）发消息，断言 B 的交付时延有上界（TestAwait deadline 形态）；隔离修复后该断言即成为回归门。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-009] 缺保护：C7-011（standby 降级不取消在途 checkpoint persist）
- **关联缺陷**: C7-011（P1）：`deactivateToStandby` 仅翻状态位、注释断言「在途 checkpoint 不会提交」，而 CheckpointCoordinator 全文无 active/fencing 复核，慢持久化 + 跨 JVM 失换下僵尸 standby 成为持久平面第二写者。
- **现有测试盘点**: `TestJobCoordinatorStandbyStateMachine.testActiveModeTriggerCheckpointSucceedsAndStandbyRejects:388-400` 只断言 standby 拒绝**新** trigger（返回 null、无 barrier）；「active 期已触发的 checkpoint 在降级后是否被 abort/其 persist 完成后是否被拦」无任何测试。TestConcurrencyWindows2326 javadoc 自认 CC-03 的确定性 pin 需要 latch seam、登记为 follow-up——同族缺口在本条同样存在。
- **变异推演结论**: 补上降级时取消在途 pending + persist 完成回调的 fencing 复核后无测试会红；回退复核（让 standby 完成提交）亦无测试红。
- **建议测试形态**: latch seam：active 触发 checkpoint→persist 阻塞在 storage latch→deactivateToStandby→放行 persist→断言 finishCommit 扇出不发生（或 typed 拒绝）；对照 TestCheckpointAbortMarkerCleanup 的 async 等待范式。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-010] 缺保护：C1-004（两阶段分配路径 epoch 守卫残留）
- **关联缺陷**: C1-004（P2）：`TaskManager.receiveAssignment` 重复槽位分支（:565-570）只看 key 不比较 epoch、`installInvokable`（:617-631）无 epoch 校验——deployTask 已按 R6 CC-02 治理，此为残留缺口。
- **现有测试盘点**: `TestConcurrencyWindows2326.staleGenerationCandidateDoesNotDisplaceNewerIncumbent` 钉住 `selectDisplacedTask` 静态决策函数（回退为无条件 put 会红）；`TestFencingTokenRejection` 钉住 receiveAssignment 顶层 token 不匹配拒绝。但「同 key 重复分配携带旧 epoch」「installInvokable 陈旧 epoch」两条残留路径无测试。
- **变异推演结论**: 补上重复槽位分支与 installInvokable 的 epoch 比较（复用 CC-02 同款治理）后，现有测试全绿（决策函数未动）；回退新守卫亦无测试红。
- **建议测试形态**: 直接复用 TestConcurrencyWindows2326 的 TaskManager+Recording stubs 夹具：先 install epoch=2 的任务，再投递同 key epoch=1 的重复分配，断言占位者不被置换且 typed 上报（复用 CC-17 的 report 通道断言）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-011] 缺保护：C1-005 / C2-007（TwoPhaseCommitSinkFunction TOCTOU 双重提交）
- **关联缺陷**: C1-005（P2）、C2-007（P3）：`finishCommit` 的 containsKey→commit→remove 三步跨锁非原子，并发通知/重试下同 epoch 可双重 commit，安全性依赖子类幂等。
- **现有测试盘点**: `testConcurrentFinishCommitNoConcurrentModificationException:176-208` 是全文件唯一并发测试，但 10 线程使用**互不相同的 epochId**（`5*(i+1)`），且只断言「不抛异常」（catch Throwable 仅置标志）——它保护的是 CME 已修复这一历史事实，对「同 epoch 双重 commit」零判别力。其余 9 个测试全部单线程 restore 语义（这部分保护良好）。
- **变异推演结论**: 补上原子 remove-then-commit（或 per-epoch 幂等守卫）后，现有并发测试仍绿（不同 epoch 各提交一次）；回退为 TOCTOU 亦无测试红。叠加 §三.2 的「测试自己 catch 掉竞争」形态，属于典型的 superficially-pass。
- **建议测试形态**: 两线程对**同一** epochId 并发 finishCommit，TestSink 的 commit 记录断言 `committedEpochs 中该 epoch 至多出现一次`（守卫正确实现下），并把工作线程异常改为 fail-fast 上抛（修掉 catch 掉竞争）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-012] 缺保护：C2-003 / C3-003（commit-fanout CallerRuns 饱和拉回 monitor）
- **关联缺陷**: C2-003（P2）、C3-003（P2，同缺陷跨维度重复登记）：`CheckpointCoordinator` commit-fanout 执行器（1 线程 + LBQ(1000) + CallerRuns）三处 execute 全在持 coordinator monitor 栈上，饱和时 sink commit I/O 内联回 monitor 线程并打破 finishCommit 去重的单线程假设。
- **现有测试盘点**: `TestCommitFanoutOffMonitor2326` 是 R6 CC-01 的高质量 pin（latch seam + monitor 探针 + 600ms 慢 participant），mutation probe 明确「移回内联即红」；但 participant 只有一次慢 commit，队列 1000 永不饱和，CallerRuns 分支从未被测试触达。`TestTaskManagerCommitQueueBound` 为 TM 侧（TaskManager.java）同形态测试——**CheckpointCoordinator 侧没有对应物**。
- **变异推演结论**: 把 CC-01 修复中的 CallerRuns 兜底改为「饱和即 typed 拒绝/计数」（缺陷的正确修复方向之一）后，CC-01 pin 仍绿；饱和窗口的内联执行回归亦无测试红。
- **建议测试形态**: 仿 TestTaskManagerCommitQueueBound：容量调小（构造器/包私有 seam），首个 commit 阻塞，灌满队列后触发下一轮 fanout，断言 `commitCallerRuns` 类计数器递增且 monitor 探针在饱和回退下仍不阻塞（或断言 typed 拒绝行为，与修复方向对齐）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-013] 缺保护：C2-005（triggerCheckpoint 持 monitor 直通快照序列化）
- **关联缺陷**: C2-005（P2）：`CheckpointBarrierTracker.triggerCheckpoint` 全程 synchronized，SourceReaderOperator finished/mailbox==null 回退分支在调用方（持 TRK 监视器）线程上直接 `injectBarrier→snapshotState` 全量序列化，ACK 被快照时长串行化。
- **现有测试盘点**: `TestCheckpointBarrierTrackerConcurrency` 对 ACK 侧并发保护强（exactly-once + CME-free hammer），`TestSourcePullBarrierInjection` 覆盖 barrier 注入正确性；但所有 mock operator 的 snapshot 都立即返回空结果，「慢快照持有监视器」的持锁时长面无断言。
- **变异推演结论**: 补上「快照移出监视器/异步化」后现有测试全绿；回退（快照重新入锁）亦无测试红——与 C10-012 同构：正确性回退有保护、持锁时长/争用回归无保护。
- **建议测试形态**: 慢快照 operator（saveState latch 阻塞）+ 另一线程并发 ACK，断言 ACK 在快照窗口内不被阻塞（monitor 探针范式，同 TestCommitFanoutOffMonitor2326）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-014] 缺保护：C3-009（queryable-state 跨线程无同步快照）
- **关联缺陷**: C3-009（P2）：`StreamOpsHttpServer` 4 线程 worker → `TaskManager.keyedStateSnapshot` 直接解引用 invokable 无 mailbox 交接 → MemoryKeyedStateBackend 裸 HashMap 双层无锁迭代 vs 任务线程逐条写：CME/撕裂快照/漏读。
- **现有测试盘点**: `TestKeyedStateQueryE2E` 覆盖端点功能（200/503/404），`TestKeyedStateSnapshotAccessor` 覆盖访问器；但查询都发生在任务静默或停止后，无「任务线程持续写 + HTTP 查询并发」交叠。
- **变异推演结论**: 补上 mailbox 交接/快照同步后现有测试全绿；回退为裸读亦无测试红。
- **建议测试形态**: keyed 任务持续写（几百 key 循环），另一线程高频 GET state 端点，断言查询要么成功且内部一致（key 集合自洽）要么 typed 拒绝，绝不 500（CME 以未捕获形态逸出的现状即缺陷签名）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-015] 缺保护：C4-006（LOCAL 取消路径不对称）
- **关联缺陷**: C4-006（P2）：`StreamExecutionEnvironment` 中断后 finally 仅 graceful shutdown + closeBufferPool，不 cancel 子任务；消费者任务 50ms 轮询无终态出口，线程持连接自旋至 JVM 退出。
- **现有测试盘点**: core 侧无任何 `shutdownLocalExecution`/中断取消路径测试（grep 零命中）；runtime 侧 TestFailJobCancelsAssignedTasks 覆盖的是 runtime 取消通道（SupervisionLoop.cancelTaskWithMailbox），LOCAL 嵌入形态无对应物。
- **变异推演结论**: 补上 finally 中的 cancel 遍历后无测试会红；回退亦无测试红。
- **建议测试形态**: LOCAL execute 阻塞消费者任务，主线程 interrupt 后断言 `Thread.getAllStackTraces` 中任务线程在超时预算内退出（复用 TestTaskManagerDaemon 的 threadExists 钩子范式）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-016] 缺保护：C4-012（stop 竞速窗口静默丢弃 2PC commit 通知）
- **关联缺陷**: C4-012（P2）：`saturatedCommitQueuePolicy` 的 `if (!executor.isShutdown()) r.run()` 在 shutdown 态静默丢弃且计数/日志失真；TaskManagerMain stop→close 窗口敞开。
- **现有测试盘点**: `TestTaskManagerCommitQueueBound` 把非 shutdown 态的 CallerRuns 行为钉得很死（回退丢通知/丢计数即红）；但全部通知发生在 tm 存活期，无「stop() 后/竞速中投递通知」的断言。
- **变异推演结论**: 补上 shutdown 态的 typed 处理（显式计数+WARN 或抛出）后现有测试全绿；回退静默丢弃亦无测试红。
- **建议测试形态**: tm.stop() 后调 `notifyCheckpointComplete`，断言行为可观测（计数器递增或 typed 异常），禁止静默返回；竞速窗口用 stop 与通知并发的 @RepeatedTest 压力形态。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-017] 缺保护：C5-004 / C8-008（提交端点无大小上限）
- **关联缺陷**: C5-004（P2）、C8-008（P2，同一根因）：`StreamOpsHttpServer.handleSubmit` 的 `readAllBytes` 无 Content-Length 校验/限量读取，单请求 ≈3× body 峰值击穿 coordinator 堆。
- **现有测试盘点**: TestStreamOpsHttpServer 只测 metrics/404/405 语义；TestOpsRestLifecycleE2E 的 /jobs POST 全部为小 body 正路径；StreamOpsConfig 无 body 上限配置项也无测试。
- **变异推演结论**: 补上 413/限量读取后无测试会红；回退（移除上限检查）亦无测试红。
- **建议测试形态**: 配置小上限（如 1KB）后 POST 超限 body，断言 413/typed 拒绝且连接不被完整读入；负向窗口断言不需要真实大 body（Content-Length 预检即可触发）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-018] 缺保护：C5-006（failedCommitParticipants 条件无界）
- **关联缺陷**: C5-006（P2，已知未修复·部分）：参与者 finishCommit 持续失败时 `failedCommitParticipants`/`checkpointSuccessMap` 按 checkpoint 周期单调累积，无条目上限与重试轮次升级出口。
- **现有测试盘点**: `TestCheckpointAbortMarkerCleanup.abortKeepsMarkerWhileCommitRetriesPending` 有高质量 anti-hollow pin——「marker 在重试未决期不得被清除」（回退提前清除会红）；但只覆盖单个 checkpoint 窗口，持续失败多轮下的累积与出口无断言。
- **变异推演结论**: 补上条目上限/升级出口后现有测试全绿（单窗口行为不变）；回退上限亦无测试红。
- **建议测试形态**: FailingParticipant 持续失败 N 个 checkpoint 周期，断言 `failedCommitParticipants.size()` 有界（正确实现）或达到升级出口（注销/typed 告警）；保留既有 anti-hollow pin 不动。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-019] 缺保护：C8-009（duplicate 分配静默吞没 + submit 拒绝不清理）
- **关联缺陷**: C8-009（P2）：两个 duplicate 分支静默（无 `reportAssignmentFailure`）；`receiveAssignment`/`deployTask` 两处 submit catch 上报但不 release 许可/移除登记（zombie）。
- **现有测试盘点**: `TestTaskManager.testDuplicateAssignmentIgnored:190-204` 断言重复分配后 runningTaskCount=1（去重不挤位面有保护）；`testCancelTaskDoesNotDoubleReleaseSemaphore` 保护 cancel 许可守恒。但 duplicate 分支的「零上报」、submit 拒绝后的许可/登记清理均无断言。
- **变异推演结论**: 给 duplicate 分支加上 WARN+typed 上报、给拒绝路径加上许可回收后，现有测试全绿（计数断言不变）；回退为静默亦无测试红。
- **建议测试形态**: RecordingMessageService 已具备捕获上报消息的能力（sent 列表）：投递重复分配后断言 FAILED 类上报消息到达；拒绝路径后断言槽位许可数恢复且 registry 登记可被后续 attempt 复用。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-020] 缺保护：C1-006 / C7-002（分配账本清理接线缺失）
- **关联缺陷**: C1-006（P1）、C7-002（P2）：`removeTaskAssignment` 生产零调用方，`globalRecovery` 无 registry 账本清理，G56 注释宣称的清理路径不存在；JDBC 平面跨重启单调增长。
- **现有测试盘点**: `TestInMemoryClusterRegistryAttemptHistory.removeTaskAssignmentClearsHistory:90-98` 在 API 单元面保护良好（清理实现回退会红）；`TestClusterRegistryConsistencyInvariant` 做一致性巡检；但「JobCoordinator.globalRecovery 调用清理」的接线面无任何测试（TestJobCoordinatorRecovery* 系列无账本清理断言）。
- **变异推演结论**: 把 removeTaskAssignment 接进 globalRecovery 后无测试会红；同理该接线回退（现状）亦无测试红——缺陷本体在接线层，单元测试保护不了。
- **建议测试形态**: JobCoordinator 级：注册带 N 次尝试历史的任务→fail→globalRecovery→断言 `getAttemptHistory` 为空（InMemory）或 JDBC 行被清/标记过期；JdbcClusterRegistry 用内存/嵌入式库。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-021] 缺保护：C7-004 / C8-004 / C5-005（timer fire mail 无去抖 + 邮箱无界）
- **关联缺陷**: C7-004（P2）、C8-004（P2）、C5-005（P2，同一根因）：`ProcessingTimeServiceDriver.run()` 每 100ms tick 对到期 timer 无条件 `mailbox.put`，无「已 pending 即跳过」去抖；TaskMailbox 双 CLQ 无容量无 QueueOverflowPolicy；慢 sink 背压期 10Hz 慢性累积。
- **现有测试盘点**: `TestProcessingTimeServiceDriver` 保护了「未到期不发」「shutdown 后不发」「回调在任务线程」三个面；`TestTaskMailbox` 保护原语线程安全。但「timer 已到期且 fire mail 仍在邮箱未被消费→driver 不得再投」的在途去重面无测试（所有测试都及时 drain）。
- **变异推演结论**: 补上去抖后现有测试全绿；回退去抖亦无测试红。
- **建议测试形态**: 注册到期 timer 后**不 drain** 邮箱，跨 3 个 tick 周期断言 `mailbox.size() <= 1`（正确实现）——负向窗口用 `TestAwait.staysTrue`；再 drain 一次断言恰好触发一次回调。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-022] 缺保护：C1-002 / C5-007 / C7-006（SideOutputRegistry 注册滞留与跨 job 串台）
- **关联缺陷**: C1-002（P2）、C5-007（P2）、C7-006（P2）：进程级 static `CONSUMERS` 无 job 作用域、remove 零生产接线；跨 job 同名 tag 静默错投 + 终态注册滞留。
- **现有测试盘点**: `TestSideOutputChainingE2E`、`TestWindowedStreamSideOutputLateData` 覆盖单 job 功能链路；teardown 只用 `clearForTest()`。无「两 job 同 tag id」串台测试、无「job 终态后条目被移除」测试。
- **变异推演结论**: 补上 job 作用域/生命周期接线后现有测试全绿；回退亦无测试红。
- **建议测试形态**: 两个 jobId 各注册同 tag 的 sink，断言各自消费互不串投（作用域正确实现下）；作业 stop 后断言 `registry` 条目可被 GC/被显式移除（配合 `clearForTest` 之外的正式 remove API）。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-023] 缺保护：C7-007（releaseHolder 不移除 meter/条目；关联 C1-009）
- **关联缺陷**: C7-007（P2）：`MicrometerStreamTaskMetrics.releaseHolder` 仅置空 holder，无 `TASK_HOLDERS.remove`、无 meter 移除，jobId churn 下每作业净增约 11 个 meter 永驻；关联 C1-009（P3）：DbBacklogGauges 无 close 释放。
- **现有测试盘点**: `TestMicrometerStreamTaskMetrics` 只测注册/计数/组合 registry 的 add/remove member（那是 CompositeRegistry 的 remove，不是 holder 生命周期）；对照兄弟 `TestChannelQueueGauges`（D2c close-release 有完整 pin），本类的 release 面零测试。
- **变异推演结论**: 补上 release 后 meter 移除后现有测试全绿；回退亦无测试红。
- **建议测试形态**: create→release 同一 holder key，断言 `registry.find(...).tags(...).meter()` 为 null 且 TASK_HOLDERS 不再持有（对照 TestChannelQueueGauges 的 awaitGaugeValue 范式）；DbBacklogGauges 补 D2c 同款 close 归零 pin。
- **严重程度**: P2（缺保护立项）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-024] 缺保护：C9-001（聚合热路径每记录全量反射拷贝）
- **关联缺陷**: C9-001（P2）：`KeyedStreamImpl.copyBean`/FieldAggregationReducer 每记录重扫构造器 + 重建 Field 数组 + 逐字段 setAccessible，仅聚合目标 Field 有缓存。
- **现有测试盘点**: TestWindowTranslation、TestWindowRoundTripInvariant 等功能测试保护聚合行为正确性（回退 copy 语义/算错会红）；但「补上反射元数据缓存」这类正确实现的性能回归无任何门禁——单测不计时延，nop-benchmark 的对应基准不存在（本 campaign C9 已登记 BG-3 基准缺口）。
- **变异推演结论**: 缓存实现回退为每记录反射时无任何测试/基准会红。
- **建议测试形态**: JMH 基准（`FieldAggregationReducerBench`，挂 nop-benchmark-stream，对照 `StreamElementCodecRoundTripBench` 惯例），输入 2-3 字段 accumulator，比较缓存/不缓存档吞吐；CI 不强制门禁，但作为 BG-3 的落地形态登记。
- **严重程度**: P2（缺保护立项，性能面）
- **信心水平**: 确定
- **复核状态**: 未复核

### [nop-stream-core-runtime-C10-025] 缺保护：C9-004（SourceReaderOperator 空闲 yield 自旋）
- **关联缺陷**: C9-004（P2）：FLIP-27 源循环空闲分支 `Thread.yield()` 只让步不阻塞，空闲 SOURCE 子任务持续烧满 1 核；与同模块 InputGate 的 parkNanos(10ms) 形态不一致。
- **现有测试盘点**: TestStreamSourceOperator 等功能测试不观测空闲行为；无任何「空闲期 CPU 占用/驻停」断言（BG-4 基准缺口已登记）。
- **变异推演结论**: 改为 parkNanos 后无测试会红；回退为 yield 亦无测试红。
- **建议测试形态**: 行为面：空闲 reader 循环运行 500ms，采样自身线程 CPU 时间断言低于阈值（park 实现下显著低于 yield）；或退而求其次断言空闲路径调用了可注入的 waiting 策略（策略 seam + stub 断言 park 被调用）。基准面挂 nop-benchmark-stream。
- **严重程度**: P2（缺保护立项，性能面）
- **信心水平**: 很可能（行为断言形态对调度器敏感，建议以 seam 注入为主）
- **复核状态**: 未复核

---

## 最终保留项

| 编号 | 严重程度 | 关联缺陷 | 一句话摘要 | 复核状态 |
|---|---|---|---|---|
| nop-stream-core-runtime-C10-001 | P2 | C1-001/C2-001/C3-001 | LocalSourceCoordinator 锁纪律缺陷零并发测试保护：全仓无线程交叠驱动 addReader/requestSplits vs snapshotState | 未复核 |
| nop-stream-core-runtime-C10-002 | P2 | C4-001/C8-013 | LOCAL 池死锁（Σsubtask>cores）无任何拓扑规模测试，16 条 P1 中后果最重的无保护项 | 未复核 |
| nop-stream-core-runtime-C10-003 | P2 | C4-002/C3-002/C8-014 | CachingTableLookup 并发上限有双断言 pin，但刷新队列深度/in-flight 去重零断言 | 未复核 |
| nop-stream-core-runtime-C10-004 | P2 | C4-009 | OpsJobManager submit 失败孤儿协调器无测试；同族 R-19 已有线程泄漏钩子范式可复用 | 未复核 |
| nop-stream-core-runtime-C10-005 | P2 | C5-001/C7-001/C8-001 | materialization 无界增长无上界测试；TestResultPartitionOverflowBypass 为假保护（钉住缺陷行为） | 未复核 |
| nop-stream-core-runtime-C10-006 | P2 | C5-010/C8-002 | PollingJdbcMessageService 无专属测试，poll LIMIT 边界零保护 | 未复核 |
| nop-stream-core-runtime-C10-007 | P2 | C8-003/C7-003 | msg_queue 账本无表行数/清理断言 | 未复核 |
| nop-stream-core-runtime-C10-008 | P2 | C8-011/C4-004 | 2 线程 poller 饥饿无隔离/时延断言 | 未复核 |
| nop-stream-core-runtime-C10-009 | P2 | C7-011 | standby 只测拒绝新 trigger，在途 persist 取消面零保护（javadoc 自认需 latch seam follow-up） | 未复核 |
| nop-stream-core-runtime-C10-010 | P2 | C1-004 | CC-02 pin 只覆盖 selectDisplacedTask 决策函数，receiveAssignment 重复槽位/installInvokable 残留路径无守卫测试 | 未复核 |
| nop-stream-core-runtime-C10-011 | P2 | C1-005/C2-007 | finishCommit 并发测试用互异 epochId 且仅断言不抛——对同 epoch 双重提交零判别力（superficially-pass） | 未复核 |
| nop-stream-core-runtime-C10-012 | P2 | C2-003/C3-003 | CC-01 pin 保护非饱和内联回退；CallerRuns 饱和窗口无测试（TM 侧有 CommitQueueBound、CC 侧无对应物） | 未复核 |
| nop-stream-core-runtime-C10-013 | P2 | C2-005 | BarrierTracker 正确性回退有强保护，持锁时长/争用回归无保护 | 未复核 |
| nop-stream-core-runtime-C10-014 | P2 | C3-009 | queryable-state 端点只有静态功能 E2E，并发读写交叠零保护 | 未复核 |
| nop-stream-core-runtime-C10-015 | P2 | C4-006 | LOCAL 中断取消路径零测试（runtime 侧有 TestFailJobCancelsAssignedTasks、LOCAL 侧无对应物） | 未复核 |
| nop-stream-core-runtime-C10-016 | P2 | C4-012 | commit 队列非 shutdown 态 CallerRuns 有强 pin，shutdown 竞速窗口静默丢弃零保护 | 未复核 |
| nop-stream-core-runtime-C10-017 | P2 | C5-004/C8-008 | 提交端点 body 上限零测试（无 413/限量断言） | 未复核 |
| nop-stream-core-runtime-C10-018 | P2 | C5-006 | failedCommitParticipants「不得提前清除」有 anti-hollow pin，持续失败累积上界零保护 | 未复核 |
| nop-stream-core-runtime-C10-019 | P2 | C8-009 | duplicate 去重不挤位有保护；零上报与拒绝路径许可/登记清理零保护 | 未复核 |
| nop-stream-core-runtime-C10-020 | P2 | C1-006/C7-002 | 账本清理 API 有单元 pin，globalRecovery 接线零保护（缺陷本体在接线层） | 未复核 |
| nop-stream-core-runtime-C10-021 | P2 | C7-004/C8-004/C5-005 | timer mail 去抖/邮箱上界零保护（未到期不发、shutdown 不发已有保护） | 未复核 |
| nop-stream-core-runtime-C10-022 | P2 | C1-002/C5-007/C7-006 | SideOutputRegistry 单 job 功能有保护，跨 job 串台与终态滞留零保护 | 未复核 |
| nop-stream-core-runtime-C10-023 | P2 | C7-007（关联 C1-009） | MicrometerStreamTaskMetrics release 面（对照兄弟 ChannelQueueGauges 的 D2c pin）与 DbBacklogGauges close 面零保护 | 未复核 |
| nop-stream-core-runtime-C10-024 | P2 | C9-001 | 聚合反射热路径行为有功能保护，性能回归无基准门禁（BG-3） | 未复核 |
| nop-stream-core-runtime-C10-025 | P2 | C9-004 | SourceReaderOperator 空闲自旋零行为断言（BG-4） | 未复核 |

**复核汇总**：25 条缺保护登记（P2，与缺陷本体分开计数），覆盖 16 条 P1 登记全部 + 13 条关键 P2 的保护缺口面；矩阵另记录 3 个「会红（部分面）」与 1 个「假保护」（C8-001，唯一需要修复批同步改写测试的条目）。本维度未产生 P0/P1 缺保护——但 16 条 P1 中 0 条具备完整回归保护，其中 C4-001/C8-013（LOCAL 池死锁）与 C1-001 族（source 协调器锁纪律）是保护缺口与缺陷后果双高的优先补测项。

---

## 维度复核结论

- **复核人**: 独立复核子代理（独立会话，2026-10-14）。方法：重读测试源码逐断言验证 + 全仓 grep 盘点 + 格式/ID 全量核对；不修改既有正文。
- **「有保护/假保护」4 行全部验证成立**：
  - M2「会红（API 面）」成立：`TestInMemoryClusterRegistryAttemptHistory.removeTaskAssignmentClearsHistory:90-98` 确断言 remove 后 history 为空且 latest 为 null，清理实现回退即红。
  - M21「会红（去重位面）」成立：`TestTaskManager.testDuplicateAssignmentIgnored`（:190）断言重复分配后 runningTaskCount=1；`testCancelTaskDoesNotDoubleReleaseSemaphore` 断言 cancel 后许可 ≤ 容量。
  - M29「会红（正确性面）」成立：`TestCachingTableLookup.maxEntriesEvictsOldestLoadedVictim:232-252` 断言超容驱逐 + 最老受害者重委托（loadCount+1）。
  - M7「假保护」成立：`TestResultPartitionOverflowBypass.overflowBypass_producerDoesNotBlockAndDataLandsInMaterializationStore:41-108` 确断言 14 条溢出记录全部驻留 store 且 producer 不阻塞，javadoc :24 明言 failover-design §9.4——把缺陷行为钉为交付语义，修复必然红，须同步改写。
- **「无保护」抽验 10 条全部实质成立**（C10-001/002/004/005/006/007/008/009/020 全部 9 条 P1 关联 + C10-011 加验）：
  - C10-001：三个直接构造/引用 `LocalSourceCoordinator` 的测试文件 `new Thread` 计数均为 0，全单线程，无线程交叠驱动结论成立。C10-011 加验精确成立：并发测试 :176-208 用 `5L*(i+1)` 互异 epochId、`catch Throwable` 仅置标志、只断言不抛，对同 epoch 双重提交零判别力。
  - C10-002：`TestTaskExecutor` 池 2/4、invokable 全为 no-op/立即抛，全部 `awaitCompletion(5s)` 内返回；:304-318 确为「仅验证不抛」。无 Σsubtask>cores 拓扑测试。
  - C10-004：`TestOpsRestLifecycleE2E` 全部为 201/400/404/409 语义路径（unknown-factory 的 400 发生在 start() 之前，不产生协调器）；`TestGovernanceSweepContainment` 仅 1 个 fresh-manager 冒烟测试。无 submit 中途失败→清理断言。
  - C10-005：`TestMaterializationPoint` 7 个测试全为 round-trip/seal/防御拷贝语义，bound/evict/capacit 关键词零命中。
  - C10-006：`PollingJdbcMessageService` 无专属单测（7 个引用文件均为传输/装配用途），LIMIT/backlog 断言零命中。
  - C10-007：`nop_stream_msg_queue` 在测试中仅出现于 `MiniStreamCluster.dropStreamTablesQuietly`（夹具重置，非运行时清理路径，非断言）；multijvm 的 `COUNT(*)` 断言只针对 `nop_stream_task_assignment` 表。
  - C10-008：`TestLaunchMainScopeWiring` 仅断言类加载 scope + Hikari 驱动转发；fraud 示例唯一时延断言（`TestXplSourceCancelE2E:116-123`）属 LOCAL cancel 预算，非 2 线程 poller 控制面隔离面。
  - C10-009：`testActiveModeTriggerCheckpointSucceedsAndStandbyRejects`（@Test :386）确只断言 standby 拒绝新 trigger（返回 null、无 barrier）与 active 成功；全文件 9 个测试均无「在途 persist 降级后取消/拦截」断言。
  - C10-020：API 面 pin 与接线面零断言均属实（全部 `getAttemptHistory` 的 coordinator 测试断言只钉「追加/保留/单调」，无一断言 globalRecovery 后账本被清）。
- **格式与关联 ID 全量核查（25/25）**：25 条均为统一「标题+7 字段」（关联缺陷/现有测试盘点/变异推演结论/建议测试形态/严重程度/信心水平/复核状态）结构，关联缺陷 ID 与矩阵 M1–M26 行 25/25 一一对应，无错配。行数口径附注：每条正文 8 行（含空行分隔 9 行），低于 `concurrency-resource-audit-prompt.md` 附录 A「发现条目最少 10 行」的字面值；但附录 A 的强制字段（文件/证据片段/并发场景/触发时序/失效面枚举/误报排除）针对**代码缺陷发现**条目设计，C10 维度正文（同文件 :329-347）为本维度规定了不同输出契约（保护力矩阵 + 缺保护按 P2 立项），本文件按该契约执行且字段无缺漏——判定格式对本条目类型完整，行数偏差不构成降级/驳回理由，仅记录为口径备注。
- **更正与限定（不影响保留判定）**：
  1. 文件头「core 322 / runtime 330」两计数互换：实测 core 330 / runtime 322（合计 652 正确）。
  2. C10-001「全仓仅 2 处直接构造」枚举不完整：`TestSourceApiRouting:288,290` 还有一处工厂 lambda 双构造（registry 幂等测试，单线程）——实质结论（无并发交叠驱动）不受影响。
  3. C10-020 变异推演句需限定：「把 removeTaskAssignment 接进 globalRecovery 后无测试会红」仅对**正确形态**（终态 job 清理，不动活跃 job 的追加语义）成立——`TestJobCoordinatorPerTaskFailure:111-126` 钉住 globalRecovery 后 history **+1**、`TestJobCoordinatorAttemptSeedOnTakeover` 钉住历史单调保留，naive「全清」接线会红。缺保护判定本身不变：无任何测试断言清理接线存在。
- **三态判定统计**：保留 25（其中 C10-001、C10-020 保留附更正/限定）、降级 0、驳回 0。无一条登记的关联缺陷实际存在会红的保护测试，故无「误判无保护」驳回项。
- **跑通验证**：本复核为文档结论复核，未修改任何产品代码或测试，无需构建。

## 最终保留项（维度复核）

| 编号 | 复核判定 | 严重程度 | 关联缺陷 | 复核备注 |
|---|---|---|---|---|
| nop-stream-core-runtime-C10-001 | 保留（附更正） | P2 | C1-001/C2-001/C3-001 | 无并发交叠测试结论成立；构造点枚举更正为 3 文件 4 处（含 TestSourceApiRouting:288/290，均单线程） |
| nop-stream-core-runtime-C10-002 | 保留 | P2 | C4-001/C8-013 | TestTaskExecutor 池≥任务数、no-op invokable 属实，:304-318「仅验证不抛」属实 |
| nop-stream-core-runtime-C10-003 | 保留 | P2 | C4-002/C3-002/C8-014 | 与 M29 抽验一致：并发上限/驱逐面有 pin，队列深度/去重面零断言 |
| nop-stream-core-runtime-C10-004 | 保留 | P2 | C4-009 | E2E 与 sweep 测试均无 submit 中途失败路径（400 发生在 start() 前），无清理断言 |
| nop-stream-core-runtime-C10-005 | 保留 | P2 | C5-001/C7-001/C8-001 | M7 假保护判定逐断言成立（:41-108）；materialization 无上界断言成立 |
| nop-stream-core-runtime-C10-006 | 保留 | P2 | C5-010/C8-002 | 无专属单测、无 LIMIT/积压断言，属实 |
| nop-stream-core-runtime-C10-007 | 保留 | P2 | C8-003/C7-003 | msg_queue 仅夹具 DROP 重置，无行数/清理断言，属实 |
| nop-stream-core-runtime-C10-008 | 保留 | P2 | C8-011/C4-004 | 装配测试无时延面；唯一时延断言属 LOCAL cancel 预算，非 poller 隔离面 |
| nop-stream-core-runtime-C10-009 | 保留 | P2 | C7-011 | standby 只断言拒绝新 trigger（:386-403），在途 persist 面零断言，属实 |
| nop-stream-core-runtime-C10-010 | 保留 | P2 | C1-004 | 与 TestConcurrencyWindows2326 CC-02 pin 范围抽验一致（决策函数面） |
| nop-stream-core-runtime-C10-011 | 保留 | P2 | C1-005/C2-007 | 加验逐行成立：互异 epochId + catch Throwable + 仅断言不抛（superficially-pass） |
| nop-stream-core-runtime-C10-012 | 保留 | P2 | C2-003/C3-003 | CC-01 pin（RepeatedTest:196/400 轮:277）与饱和窗口缺口叙述一致 |
| nop-stream-core-runtime-C10-013 | 保留 | P2 | C2-005 | 正确性面强保护、持锁时长面零断言的结构性结论成立 |
| nop-stream-core-runtime-C10-014 | 保留 | P2 | C3-009 | 端点 E2E 仅静态状态查询，无并发读写交叠 |
| nop-stream-core-runtime-C10-015 | 保留 | P2 | C4-006 | LOCAL 中断取消路径零测试（runtime 取消通道有对应物） |
| nop-stream-core-runtime-C10-016 | 保留 | P2 | C4-012 | 非 shutdown 态强 pin 与 shutdown 态缺口二分成立 |
| nop-stream-core-runtime-C10-017 | 保留 | P2 | C5-004/C8-008 | 端点测试全为小 body 正路径与 HTTP 语义码，无 413/限量断言 |
| nop-stream-core-runtime-C10-018 | 保留 | P2 | C5-006 | anti-hollow pin 与累积上界缺口的二分成立 |
| nop-stream-core-runtime-C10-019 | 保留 | P2 | C8-009 | 去重位面 pin（:190）与零上报/清理面缺口的二分成立 |
| nop-stream-core-runtime-C10-020 | 保留（附限定） | P2 | C1-006/C7-002 | 接线面零断言成立；变异句限定：正确形态（终态清理）无测试红，naive 全清会红 PerTaskFailure +1 pin（见复核结论.3） |
| nop-stream-core-runtime-C10-021 | 保留 | P2 | C7-004/C8-004/C5-005 | 「未到期不发/shutdown 不发」测试在位（:115/:158 属实），去抖面零断言 |
| nop-stream-core-runtime-C10-022 | 保留 | P2 | C1-002/C5-007/C7-006 | 单 job 功能链路有保护、跨 job 串台/终态滞留零断言 |
| nop-stream-core-runtime-C10-023 | 保留 | P2 | C7-007（关联 C1-009） | release 面（对照 ChannelQueueGauges D2c）零测试，与 C1-009 复核结论互证 |
| nop-stream-core-runtime-C10-024 | 保留 | P2 | C9-001 | 行为面功能保护与性能面无门禁（BG-3）的二分成立 |
| nop-stream-core-runtime-C10-025 | 保留 | P2 | C9-004 | 空闲行为零断言（BG-4）；信心水平「很可能」标注恰当 |

**维度复核汇总**：25 条缺保护登记 → 保留 25（C10-001、C10-020 附更正/限定）、降级 0、驳回 0。矩阵 4 个非「无测试」判定行（M2 API 面、M21 去重面、M29 正确性面、M7 假保护）逐断言验证全部成立；M7 假保护（C8-001 overflow-bypass）确认为全矩阵唯一需修复批同步改写测试的条目。更正 2 处事实性偏差（测试文件计数互换、C10-001 构造点枚举），均不影响判定。
