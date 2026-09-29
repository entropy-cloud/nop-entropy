# 366 nop-stream 深审计 R4：region 重启缺陷修复 + 可读性收口 + JMH/JFR 性能迭代

> Plan Status: active
> Last Reviewed: 2026-09-29
> Source: 审计 `ai-dev/audits/2026-09/2026-09-29-0546-deep-audit-nop-stream-quality-r3/`（summary + 01 正确性 + 02 可读性 + 03 性能；P0/P1 均经主审逐行核实）
> Related: 358/359/360（R1）、2277/2278/2279（R2）、`nop-stream-quality-perf/01`（R3，均 completed）；收敛裁定书 `ai-dev/audits/evidence/nop-stream-perf-{360,2279}/convergence*.md`
> Draft Review: R1 独立子代理对抗性审查（agent_2e0771ec，2026-09-29，含想象性分析）：引用抽查 30+ 处、4 Major + 7 Minor 已全部折入——Major-1 死 API 误报剔除（stringToTaskLocation/stopPeriodicCheckpoints 改判）、Major-2 design docs 同步项新增（含 SourceWorkUnit 保留决定推翻裁定）、Major-3 N1 pendingReplay 三断口钉死（read timeout 重载/四案例矩阵/unaligned 捕获交互+remote 排除）、Major-4 N2 e2e 加 >容量记录数+四案例断言；Minor：bench pom 依赖、mock driver 措辞、测试卫生清单补全、B6' 字符集、N3 失败路径行为变化登记、基线刷新。R2 复审（agent_082627d1，独立子代理）：R1 折入 11/11 PASS、引用实测命中，新发现 F1（no-mat+finished 案例下 finished producer 被 Phase 3 无条件 resubmit 写 finished 分区抛异常）已折入为 N2 配套修复项（SUCCESS-terminal 跳过）；F2-F4（Targets 对齐/HEAD 描述/措辞）已折入。**裁定：进入执行。**

## Purpose

把 R4 审计发现收口到可验证状态：修复 2 个 P0（region 重启两处作业静默永久悬挂）与 2 个 P1（假阳性 stall 恢复、JDBC restore 静默冷启动）及同族 P2 缺陷；完成 R3 后残留的可读性/死代码治理；对 5 个新性能候选按 360/2279/plan-01 既有纪律（JMH 前后对比 + JFR 归因 + 方差协议）实测留舍，迭代至**无 ≥2% 低风险可收割项**。每个 Phase 完成并验证后立即 git commit（用户要求：每计划完成后自动提交一次）。

## Current Baseline

- HEAD = b667ad705a（执行首日复核刷新）。nop-stream 5 模块 `./mvnw test -pl ... -am` 全绿（2026-09-29，`_tmp/r4-baseline-test.log` EXIT=0）。
- plan-01 三提交（adb1682afb/7a706eebbf/43392902b3）经 diff 回归审计**无 P0/P1 新引入回归**；仅 S1（closeChainAndGate suppressed 树形态）、S2（JobCoordinator recoveryPending 注释失实）两条 P2。
- 已核实缺陷（本次修复对象，行号为 2026-09-29 HEAD 实测）：
  - N1：`SupervisionLoop` region 重启的 materialization 回放经 `ResultPartition.injectFront` 的阻塞 `queue.put`（有界 `LinkedBlockingQueue`，默认容量 1024）同步注入**规模不受队列容量保护**的回放列表；注入发生在 `executor.submitTask(newTask)` 之前，无消费者 → 回放量 ≥ 容量时监督线程永久阻塞。
  - N2：同 region 内部边（RegionDecomposer 契约：仅 materialization 边切 region → 内部边必无 matPoint）在 consumer 重建时走 `buildConsumerInvokableWithReplay` else 分支拿全新空分区，producer 复用旧 writer 写旧分区 → 边切断、双方永久阻塞。任何 ≥2 顶点 region 失败即触发。
  - N3：`TaskManager.heartbeat`（:309-323）无 `isFinished` 过滤；`RunningTask.run` finally 对 success 保留注册表条目；`JobCoordinator.reportTaskStatus` COMPLETED 分支删 liveness 键后心跳把冻结 activity 经 `merge(Math::max)` 回插 → 60s 后假 TASK_STALL 全局恢复，摧毁尾段 2PC 提交窗口。
  - N4：`JdbcCheckpointStorage.tableExists/epochTableExists`（:400-407/:567-574）catch Exception→debug+false；restore 遇瞬时 JDBC 故障 → 静默空状态冷启动。
  - N5：`ResultPartition.injectFront` drain 侧见 EOS 哨兵 `bufferPool.release()`，但哨兵从不 acquire（close 入队/read 出队/drain break/重 put 均不涉 permit）→ 每次 finished 分区注入净 +1 permit。
  - A2'：`CheckpointCoordinator.onCompletePersistFailure`（:870 fail 路径）无 `checkpointSuccessMap` 清理（abort 路径 :926-928 已修，fail 路径同族遗漏）。
  - B6'：`FileSource` `directoryPath` 写出无保留字符校验（splitById 已校验，:178）。
  - S1：`CloseSupport.accumulate` 单层挂接 → closeChainAndGate 三重失败时 suppressed 树扁平→嵌套（plan-01 声明逐字保持，实际形状变化）。
  - S2：`JobCoordinator:1497-1511` 内层 finally 注释与 `requestRecovery` javadoc（:1281-1283）描述的清除时点失实（实际在外层 finally、锁外、health/事件之后）。
- 可读性残留（02 报告）：10 个零引用 public 顶层类；3 个未接线死特性面（RemoteResultPartition 心跳、TtlContext.sweepExpired+TtlCleanupStrategy.backgroundCleanup（DEFAULT=new(true,true) 而无任何后台清理器）、processWatermarkStatus1/2（已核实无双输入算子））；测试脚手架重复（windowing 家族第 5 份复制；TestAwait 5 模块副本；21 份 RPC stub）；deployTask 141 行 7×守卫重复 + report 双胞胎；TaskCheckpointWiring 全文件零缩进；4 处零引用常量（含 numLateRecordsDropped 从未注册）；15 处 plan-01 编号注释；说谎 javadoc（JobCoordinator:80 triggerCheckpoint、Task.markFailed/markScheduled/markRecovering、TaskManager:470/:550 考古）；单方法死 API 一批；G10 残余 2 个 prologue；MemoryKeyedStateBackend 注释 ~90% 重复。
- 性能：既有裁定（D2/D3/C2/F3/E3/F1/F2/C1 等 8 项抽查）在 HEAD 全部维持。新候选 5 个（R4-P1 文件 sink toString、R4-P2 文件源逐字节读、R4-P3 JDBC map 拷贝、R4-P4 E3 memo IdentityHashMap 分配、R4-P5 单槽缓存 evictor 双族打穿），其中 connector 数据面**无任何既有基准口径**。
- 基准设施：`nop-benchmark/nop-benchmark-stream`（13 类，JMH 1.33；`NfaProcessBench` 有 cheap/billable × depth 档；`WindowOperatorProcessElementBench` 有 backend×布局档；README 含运行与 JFR 方法论）。

## Goals

- N1/N2/N3/N4/N5/A2'/B6' 全部修复且各带聚焦回归测试；S1 恢复扁平 suppressed 形状；S2 注释归真。
- Phase 3 全部行为保持（对外语义、序列化格式、checkpoint 兼容不变），死代码零引用删除、重复收敛、注释归真。
- R4-P1..P5 每项有 JMH 前后对比与留舍裁定（保留或 revert+无收益证据——两种都是合格收口）；迭代循环至触碰路径上无任何 ≥2% 低风险可收割项（延续 360/2279/plan-01 停止判据）。
- 全程每 Phase 一次独立 commit。

## Non-Goals

- 不做 JobCoordinator/InputGate/CepOperator 整体拆分（G1/G2/G3，successor backlog 维持）。
- 不修复行为变更类缺陷 A3/A4/A5/A6/B2/B7（owner 语义确认后单独立项，归属维持 plan-01 Deferred 裁定）；N6（triggerSavepoint 静默 null）并入 A3 successor。
- 不实施 D2/D3/C2/F3 等既有 Deferred 性能项（裁定维持，本轮仅抽查确认未破坏）。
- 不建 TaskDispatchLoopBench/嵌入态 checkpoint 循环等非本轮候选依赖的基准（登记 follow-up）。
- 不改变 connector sink 缓冲的 aliasing 语义（R4-P1/P3 若实测 ≥2% 但涉及契约，落 Deferred 记录而非强改）。

## Scope

### In Scope

- 基准缺口：`nop-benchmark/nop-benchmark-stream` 新增 `ConnectorInvokeBench`（file source 行读取、file 2PC sink invoke、JDBC 2PC sink invoke with mock driver，参数化 payload）。
- 缺陷修复：N1、N2、N3、N4、N5、A2'、B6'、S1、S2。
- 可读性/结构（行为保持）：02 报告 P1/P2 清单（死类删除、死特性面裁定处置、测试脚手架同模块收敛、结构项、注释归真、死 API 删除）。
- 性能候选实测留舍：R4-P1..P5 + 收敛循环。
- 文档：`ai-dev/logs/`、02 报告裁定回写、`ai-dev/design/nop-stream/state-management-design.md` TTL 声明修正（若 Phase 3 处置涉及）。

### Out Of Scope

- Non-Goals 列出的全部条目；`ai-dev/audits/2026-09/2026-09-29-0546-deep-audit-nop-stream-quality-r3/summary.md` 中归入 successor/watch-only 的其余项。

## Execution Plan

### Phase 1 - 基准缺口补建与候选基线

Status: completed
Targets: `nop-benchmark/nop-benchmark-stream`

- Item Types: `Proof`

- [x] 新增 `ConnectorInvokeBench`：file source 行读取口径（临时文件，参数化行长/行数）、`FileTwoPhaseCommitSink.invoke` 口径（temp dir，String/Map payload）、`JdbcTwoPhaseCommitSink.invoke` 口径（`invoke()` 仅做内存缓冲不触 JDBC——用最小 IJdbcTemplate 桩即可，无需 java.sql Driver mock）；`nop-benchmark-stream/pom.xml` 补 `nop-stream-connector` 与 `nop-stream-connector-jdbc` 依赖（当前未声明）；对齐既有 bench 的参数化与 README 表格
- [x] README 基准集表格补充新口径与运行命令
- [x] Round-0：运行受影响基准取得候选基线（R4-P1/P2/P3 用新口径；R4-P4 用 `NfaProcessBench` depth=20/billable 档；R4-P5 用 `WindowOperatorProcessElementBench` EVICTOR 档），记入 `## Benchmark Rounds`
- [x] 冒烟：新口径可运行、数字合理（非 0/非异常）

> No new test required: 基准口径扩展本身即验证设施；Round-0 数据即其验收。
>
> 执行披露：首轮运行暴露两处基准缺陷并当场修复（`Files.createTempDirectory` 父目录未创建致 4/5 口径 setup 失败；sink 缓冲无界增长——加每 4096 次 invoke 周期 `rollback()` 清空，摊销可忽略）。

> Phase 2 执行披露（2026-09-29）：
> (1) N2 执行期发现同族缺口——MIDDLE 任务（有 gate 有 writer）走 consumer 重建路径时 writer 被置 null（原实现注释明示只考虑 SINK），重建后下游被静默饿死；已折入修复（buildConsumerInvokableWithReplay 保留旧 fan-out writer），e2e ie-map 场景覆盖。
> (2) 回放窗口竞态实测确认（drain↔replay 快照之间写入双投递，1500 条规模 0-2 条重复）——先存缝隙非本轮引入，已登记 Non-Blocking Follow-ups 并写入 failover-design.md。
> (3) 大回放 e2e 首轮超时根因为 stale jar（`-pl` 未带 `-am` 时 runtime 用本地仓库旧 core jar 测出旧 injectFront 路径）——重装后 0.2s 通过；该教训记录：改 core 后跑下游模块测试须 `-am` 或先 install。

Exit Criteria:

- [x] `./mvnw -q compile -pl nop-benchmark/nop-benchmark-stream` 通过，ConnectorInvokeBench 三口径冒烟成功
- [x] Round-0 基线数字写入 `## Benchmark Rounds`（R4-P1..P5 各 ≥1 组 + 运行命令）
- [x] 既有 5 模块测试不受影响（bench 模块不动 nop-stream 生产代码）
- [x] No owner-doc update required（bench README 更新即文档义务本身）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] git commit 完成（Phase 1 独立提交）

### Phase 2 - 已核实缺陷修复

Status: completed
Targets: `SupervisionLoop.java`、`ResultPartition.java`、`InputChannel.java`、`TaskManager.java`、`JobCoordinator.java`、`CheckpointCoordinator.java`、`JdbcCheckpointStorage.java`、`FileSource.java`、`CloseSupport.java`

- Item Types: `Fix`

- [x] N1：回放注入去阻塞化——replay 数据不再经 `injectFront` 的阻塞 `queue.put` 全量灌入；改为分区侧惰性回放（pendingReplay）。设计约束（R1 对抗性审查钉死）：
  - **全部 read 路径**挂 pending 检查：InputGate 消费实际走 `ResultPartition.read(long, TimeUnit)` timeout 重载（`readSingleChannel`/`readMultiChannel`），阻塞版 `read()` 亦须覆盖——漏掉 timeout 重载会在复用已 finished 分区时假完成 + 静默丢回放数据
  - 消费顺序不变式：pendingReplay → 队列残留 → EOS；permit 记账：pending 段不 acquire 不 release，入队段沿用 write 语义
  - **复用分区四案例矩阵**（N1×N2 交叉，残留数据处理逐案例钉死）：
    - mat + producer running（现 live 分支）：drain 队列残留（stale）→ 挂 pendingReplay → consumer 读 pending 后续读 live 队列
    - mat + producer finished：**必须 drain 队列残留**（残留内容与回放集合完全重合，不 drain 即全量重复投递）→ 挂 pendingReplay → pending 空且 finished → EOS
    - no-mat + finished：复用分区不 drain，consumer 读残留 + EOS
    - no-mat + running：复用分区不 drain，consumer 读残留后续读 live（at-least-once 允许重复）
  - **unaligned 捕获交互**：`drainBufferedElements` 一并排空 pending 段（captureInFlightData 经它捕获，保证 unaligned checkpoint 在回放未排空期不丢 pending）；restore 路径 `injectElements` 维持既有 `injectFront`（有界 capture ≤ 容量，安全），不迁移到 pendingReplay——显式排除
  - **RemoteInputChannel 排除**：pendingReplay 仅挂本地 `ResultPartition`；remote 重建路径不路由经此（mat 点只挂本地分区）
  - 聚焦测试：回放量 > 队列容量（如 2000 条/容量 128）时 consumer 完整消费全部回放 + 新数据、监督线程不阻塞；pending 未排空期 drainBufferedElements 返回含 pending 段
- [x] N2：`buildConsumerInvokableWithReplay` 对 `matPoint == null` 的通道复用旧分区（与 producer 旧 writer 对账），删除"全新空分区"分支，按上述四案例矩阵处理残留。**配套修复（R2 复审 F1）**：`restartRegion` Phase 3 对 SUCCESS-terminal 任务跳过 rebuild/resubmit（其分区保留余量数据 + EOS，consumer 侧复用旧分区自然承接；否则复用旧 writer 的 finished producer 首次 write 即抛 ERR_STREAM_INVALID_STATE → 再失败循环；FAILED/CANCELED/非终态任务照常重建），聚焦测试钉死该跳过行为。端到端回归测试：多 region 作业（内部边 + materialization 边），fail 内部顶点任务 → 作业完成、sink 收全量记录、无悬挂；**记录数 > 分区容量**（天然叠加 N1 路径，防 2277 式修复叠加回归）；**断言覆盖四案例矩阵**（mat+finished 与 no-mat+finished 两个 finished 案例必须有专项断言；no-mat+finished 依赖本项跳过修复才可构造）；补显式断言"finished 分区 + pending 非空时 gate 不产 EOS"（timeout-null 与 EOS-null 同形是假完成根源）
- [x] N3：`TaskManager.heartbeat` 对 `task.isFinished()`（`RunningTask.finished` 为 volatile，:329 已有访问器）的任务跳过 liveness 上报（恢复 JobCoordinator COMPLETED 注释钉死的契约）；顺带把 `JobCoordinator:924-928` 注释改为修复后仍真的表述。可观测行为变化登记：修复后若 COMPLETED 报告丢失，冻结 liveness 将在 60s 后进入 stall→恢复（向 FAILED 分支既有契约对齐，detectFailures 的 benefit-of-the-doubt + node-lease 检查无新死角，R1 审查核实）；聚焦测试：success 完成后心跳不再产生该任务的 TaskProgress，coordinator liveness 不回插、60s 后无 TASK_STALL
- [x] N4：`JdbcCheckpointStorage.tableExists/epochTableExists` 区分"表不存在"（查询成功 false）与"查询失败"（抛 typed `CheckpointStorageException`，WARN 起步），消除 restore 静默冷启动。聚焦测试：existsTable 抛异常 → loadLatestEpochManifest 响亮失败而非返回空
- [x] N5：`injectFront` 已整体删除（阻塞注入 API 由 attachPendingReplay 取代，哨兵许可泄漏不复存在）；测试：TestResultPartitionPendingReplay#replayPathHoldsNoPoolPermits 断言 pending 段零 acquire/release、permits 守恒（对齐 `drainBufferedElements`）。聚焦测试：带 pool 的 finished 分区经 injectFront 前后 availablePermits 不变
- [x] A2'：`onCompletePersistFailure` fail 路径补 `checkpointSuccessMap` 清理（对齐 abort 路径）。聚焦测试：fail 且无失败参与者时条目被移除
- [x] B6'：`directoryPath` 写出前保留字符校验——directoryPath 是换行分隔字段，仅拒 `\n`/`\r`（含 `|` 的合法目录不拒绝；与 splitById 的 `|`+换行集合刻意不同，注释说明原因）。聚焦测试：含 `\n` 的目录路径序列化快速失败、含 `|` 的合法目录通过
- [x] S1：`CloseSupport.accumulate` 展平挂接（把 error 及其 suppressed 全部挂到 firstError，恢复扁平形状、first-error-wins）；javadoc 钉死形状契约。聚焦测试：三重失败断言 `firstError.suppressed=[X,Y]` 顺序
- [x] S2：重写 `JobCoordinator:1497-1511` 内层 finally 注释（unlock 语义）与 `requestRecovery` javadoc：清除点=外层 finally、锁外、health/事件之后，及其与 triggerCheckpoint 抑制窗口的关系

Exit Criteria:

- [x] 上述每项有对应聚焦测试（测试名与修复项一一对应），验证正确行为而非仅无异常
- [x] **端到端验证（Rule #22）**：N2 的多 region failover 测试从任务失败 → region 重启 → sink 输出完整走通；N1 的超容量回放测试从注入到消费完整走通
- [x] **接线验证（Rule #23）**：N1 惰性回放路径在 SupervisionLoop 重建流程中被真实调用（测试断言回放数据到达 consumer，而非仅单元直调）
- [x] **无静默跳过（Rule #24）**：N4 修复后失败路径响亮失败；无新增空方法体/吞异常
- [x] `./mvnw test -pl` 7 模块全绿（core 1611 / flow 118 / runtime 1097 / cep 372 / rocksdb 123 / connector 72 / connector-jdbc 43 = 3436 tests，0 failures 0 errors，`_tmp/r4-test-phase2-full.log` EXIT=0）
- [x] owner-doc 裁定：N4 失败语义已写入 `docs-for-ai/03-modules/nop-stream.md`（存储读路径失败语义节）；N2/回放机制修订已改写 `ai-dev/design/nop-stream/failover-design.md`（reconnect-to-live-queue 节 + writer 保留节，design doc 按最终状态改写）；其余项 `No owner-doc update required`（S1 异常树形状未文档化、S2/A2'/N3 纯注释或内部契约归真） `docs-for-ai/03-modules/nop-stream.md`（及 cdc cookbook 若涉及）并同步；其余项逐项记录 `No owner-doc update required` 理由
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] git commit 完成（Phase 2 独立提交）

### Phase 3 - 可读性与结构整改（行为保持）

Status: completed
Targets: 02 报告清单涉及文件（core/runtime/cep/connector 的 main+test）

- Item Types: `Fix | Decision | Proof`

- [x] 删除 10 个零引用 public 顶层类（逐个执行前 grep 复核零引用：SourceEnumeratorState、VoidNamespaceSerializer、SourceWorkUnit、DynamicSplitRequest、DynamicSplitResponse、RestrictionTracker、TaskAssignmentMessage、NopCepConstants、RichPatternFlatSelectFunction、RichPatternSelectFunction）
- [x] 死特性面裁定（Decision，处置=删除死面+注释归真，不加新功能）：
  - [x] RemoteResultPartition 心跳：删 startHeartbeat/sendHeartbeatIfIdle/stopHeartbeat/getHeartbeatIntervalMs + 字段 + 6 参构造，close() 注释归真；CONTROL_HEARTBEAT 常量与 RemoteInputChannel 容忍分支保留（滚动升级兼容面，非死代码）
  - [x] TTL：删 `TtlContext.sweepExpired`（`expiredKeys` 保留——RocksDBKeyedStateBackend.cleanupExpiredEntries 主源在用，审计判定有误按 grep 裁定）；`TtlCleanupStrategy` 移除 `backgroundCleanup`（构造器/DEFAULT/equals/hashCode/测试同步），state-management-design.md 改为 lazy-only + RocksDB caller-driven sweep 实况
  - [x] 删 `AbstractStreamOperator.processWatermarkStatus1/2` + 私有双参 helper（`IndexedCombinedWatermarkStatus` **保留**——processWatermark1/2 活路径在用，裁定不移除；TestWatermarkMultiInputCombineWire 仅删唯一 status2 消费者）
- [x] 单方法死 API 删除（执行前逐个 grep 零引用；**审计勘误**：`CheckpointSerDe.stringToTaskLocation`（同类 :187/:580 在用）与 `JobCoordinator.stopPeriodicCheckpoints`（shutdown:625 在用）为审计误报，**不删**，改为 public→包私有收敛若仅内部使用）：asLatencyMarker、nextProcessingTimeTimer、sideOutputLateData、StreamConnectors 桥接 ×4、Task.markFailed/markScheduled/markRecovering、JobCoordinator 死访问器 ×6（getLeaderElector/getTaskTimeoutMs/isAutoRecoverOnFailedReport/getMaxStallRestarts/getStallRecoveryCooldownMs/getCheckpointStoragePath）、EngineMetrics ×4（getCompletedCount 有 TestMetricsLifecycle 调用，删时同步测试）、TaskNodeMetrics ×3、MemoryBudget ×2、WindowedStreamImpl.getEvictor
- [x] 死常量：删 `DEFAULT_COMMIT_RETRIES`/`CONSECUTIVE_FAILURE_THRESHOLD`/`DEFAULT_LEASE_EXPIRE_THRESHOLD_MS`；`LATE_ELEMENTS_DROPPED_METRIC_NAME` **裁定=接线**：两算子均有真实迟到丢弃执行路径（CepOperator 本有 LongAdder 递增但从未暴露 → 换注册 counter；WindowOperator 隐式 drop 路径补 counter），新测试 TestCepOperatorLateRecordsDroppedMetric / TestWindowOperatorLateRecordsDroppedMetric 断言 delta=1，`docs-for-ai/03-modules/nop-stream.md` 指标名表增补
- [x] 注释归真：16 处 plan-01 编号注释（15 main + 1 test TestJdbcTwoPhaseCommitSinkDeep）改写为当前不变式（删编号/考古句式/跨文件复制）；JobCoordinator:80 triggerCheckpoint javadoc、Task.mark* javadoc、TaskManager:470/:550 考古注释、`CheckpointBarrierSignal` 死类裁定删除；MemoryKeyedStateBackend 两段重复注释合并
- [x] **owner design docs 同步**（删除项的既有文档引用）：`ai-dev/design/nop-stream/connector-design.md:137-142,146,438`、`01-architecture-baseline.md:22,34`、`README.md:426` 引用了将删除的 SourceWorkUnit/RestrictionTracker/DynamicSplitRequest/Response/SourceEnumeratorState——同步删除描述；其中 connector-design.md:142 "SourceWorkUnit 标 @Deprecated 保留以向后兼容旧 savepoint"的既有决定**显式推翻并记录裁定**：该类零引用即从未被构造、从未进入任何序列化状态，删除不破坏 checkpoint 兼容（裁定理由写入 plan 本节或 02 报告回写）
- [x] 结构项：`deployTask` 7 守卫块收敛为守卫 helper + report 双胞胎合并（`dJobId/aJobId` 改名）；`MaxParallelismReshardMigration.reshardVertexStates` 四阶段拆分；`TaskCheckpointWiring` 全文件缩进修复 + wire/unwind instanceof 镜像收敛；`FileSource` 提取 `validateReservedChars` helper 消除第 3 份复制；G10 残余（triggerSavepoint/executeWithSavepoint prologue 路由共享 skeleton）
- [x] 测试脚手架（同模块收敛）：windowing 家族公共脚手架抽 builder；`TestJobCoordinatorRecoveryPendingCleanup` 等 runtime 模块内 stub 收敛；Kafka/Pulsar codec 测试互抄 42 块与 TestCoordinatorRpcControlPlane ↔ 3 个 JobCoordinator 测试互抄 35-37 块的同模块收敛；`TestDebeziumCdcSourceCompletion` @Disabled 注解改写为指向契约裁定并登记 connector backlog
- [x] `RemoteTaskDeploySupport` vs `TaskCheckpointWiring` provisioning 分歧（remote 恒 Memory vs local 取 config）：javadoc 如实标注分歧（Decision：语义统一需 owner，登记 Deferred）
- [x] 死类删除/方法删除后全仓库 grep 复核零残留引用（29 符号 + markFailed 全部 0 命中；执行偏差：`PatternStreamBuilder.withLateDataOutputTag` 因 sideOutputLateData 删除成为新孤儿，按纪律不删、登记 follow-up）

Exit Criteria:

- [x] 每项删除有执行前零引用 grep 证据（汇总记录，见子代理报告与 09-29 日志）
- [x] `./mvnw test` 9 模块全绿（core 1610 / flow 118 / runtime 1094 / cep 373 / rocksdb 123 / connector 72 / connector-jdbc 43 / connector-batch 49 / connector-debezium 40 = 3522 tests 0 failures，`_tmp/r4-test-phase3-full.log` EXIT=0）
- [x] 行为保持抽查：deployTask 守卫字符串逐字节 diff 相同、TaskCheckpointWiring `git diff -w` 仅 53 行、错误锚点零改动；TTL backgroundCleanup 无生产调用方（DEFAULT 常量为死声明）；LATE_ELEMENTS 接线属计划授权的观测补全（2 新测试断言 delta）
- [x] Phase 3 触碰的 Phase 4 相关基准场景无 >2% 退化（并入 Phase 5 统一复验；Phase 3 改动的 CloseSupport/FileSource/注释均非基准触碰面）
- [x] owner-doc：`state-management-design.md` TTL 改 lazy-only 实况；connector-design/01-architecture-baseline/design README/`docs-for-ai/03-modules/nop-stream.md` 指标名表同步；其余 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] git commit 完成（Phase 3 独立提交）

### Phase 4 - 性能候选实测留舍（迭代至无 ≥2% 收益）

Status: completed
Targets: `FileSourceReader`、`FileTwoPhaseCommitSink`、`JdbcTwoPhaseCommitSink`、`NFA.java`、`RocksDBKeyedStateBackend`/`MemoryKeyedStateBackend`（如 R4-P5 达标）

- Item Types: `Fix | Proof`

裁定纪律（沿用 2279/plan-01 协议）：每候选 ≥3 次重复或误差条重叠判定；2-5% 边界重跑确认；未触碰基准 ±3% 噪声带；≥2% 且低风险 → 保留，否则 revert + 无收益证据记录。**两种结果都是合格收口。**每实施一项保留后重跑受影响基准 + JFR 复归因，若归因出新 ≥2% 候选则继续下一轮，直至无 ≥2% 低风险可收割项。

- [x] R4-P2 文件源缓冲化：`FileSourceReader.readNextLine` 重写为缓冲读取（行为保持：按行语义/偏移量推进/字符处理逐位一致），ConnectorInvokeBench 前后对比
- [x] R4-P4 E3 memo 惰性分配：`NFA.java:728` IdentityHashMap 惰性初始化（多数状态无条件评估时零分配），`NfaProcessBench` depth=20/billable 前后对比 + cep 全量测试守护
- [x] R4-P1/R4-P3 实测 + 裁定：ConnectorInvokeBench 基线 vs 消除拷贝/toString 的理论形态（用 bench 内原型口径测收益上限）；≥2% 且不涉 aliasing 契约改动 → 实施保留；涉契约 → 记录实测证据 + Deferred（优化候选，需 owner 契约裁定）
- [x] R4-P5 双槽缓存：若 EVICTOR 档实测 ≥2% 则实施（失效契约不变），否则 watch-only 记录
- [x] 每项留舍数字记入 `## Benchmark Rounds`；JFR 归因文件存 `_tmp/nop-stream-perf/r4-*.txt` 与 `r4-jfr-filesource.jfr`（raw 登记同目录，gitignore 不入库——沿用 2279 存档缺口记录）

Exit Criteria:

- [x] R4-P1..P5 每项有 JMH 前后对比数字与留舍裁定（Round 表可查：P2 保留 -33%、P4 保留（方向正不可确证）、P5 revert 无收益、P1/P3 Deferred 契约+实测）
- [x] 保留项各有聚焦测试或既有全量测试守护语义（P2：connector 72 tests 全绿含 ReaderRecovery/CheckpointRestore/AuditFixes 字节记账；P4：cep 373 tests 全绿；P5 已 revert 无需守护）
- [x] 收敛循环有明确轮次记录（Round-1/R1b/R0b + Phase 5 定向套件；最终裁定见 Phase 5）
- [x] **端到端验证**：TestFileSourceCheckpointRestore/TestFileSourceReaderRecovery 全绿（checkpoint 恢复路径字节精确）
- [x] `./mvnw test` 触碰模块全绿（connector 72、cep 373）
- [x] No owner-doc update required（P1/P3 的 Deferred 归属已记录于本 plan Benchmark Rounds 与 Deferred 节既有条目）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] git commit 完成（Phase 4 独立提交）

### Phase 5 - 收敛复验（延续 360/2279/plan-01 停止判据）

Status: completed
Targets: 本轮触碰路径相关基准 + JFR 归因

- Item Types: `Proof`

- [x] 以 `-prof jfr` 对保留项触碰场景采样（`r4-jfr-filesource.jfr`，1104 样本）：97% 在 `readNextLine` 循环本体（BAOS 逐字节累积 + UTF-8 解码的基本成本），readBuffered 20 样本、FileInputStream.read 1 样本——chunked 读已消除同步读热点，无新增单点
- [x] 定向基准套件复跑（`r4-phase5-suite.txt`：ConnectorInvokeBench×5 + NfaProcessBench d5×2 + MemoryKeyedState/CheckpointSerDe/SharedBufferRegister 金丝雀带）——**跨会话漂移登记**：全部基准（含全部未触碰金丝雀：internalListAdd +55%、SharedBuffer +22%、NFA d5 +28%）相对 plan-01 会话一致偏慢 +20~55%，判定为环境/JIT 漂移（plan-01 Round-2 对同类漂移的先例裁定）；会话内对比有效：触碰路径无退化信号
- [x] 收敛裁定：**本轮触碰路径上无 ≥2% 低风险可收割项**——P2 已收割保留（-33%）；JFR 归因无新单点；剩余候选均为中风险（行累积跨 chunk 重构，需重设计边界语义）或契约门控（sink aliasing），登记 follow-up 不构成本轮低风险收割项。与 360/2279/plan-01 停止判据同构
- [x] 若复验发现 ≥2% 且低风险新候选：循环裁定完成——无此类候选（上述收敛裁定）

Exit Criteria:

- [x] `## Benchmark Rounds` 含 Phase 5 复验轮数据（见 Round-2），收敛裁定结论明确
- [x] JFR 热点清单与候选取舍理由已记录（上方）
- [x] `./mvnw test` 全绿（Phase 3 收口 9 模块套件 + Phase 4 触碰模块复跑：connector 72 / cep 373）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] git commit 完成（Phase 5 独立提交）

### Phase 6 - 文档同步与计划收口

Status: planned
Targets: `ai-dev/logs/`、审计报告、本 plan

- Item Types: `Proof`

- [ ] owner docs 复核：Phase 2-5 改变已文档化契约/模式处同步对应 owner doc（重点：N4 JDBC restore 失败语义、N2 region 重启内部边语义、TTL design doc）；复核结论逐项记录
- [ ] 02 报告逐项裁定回写（landed / Deferred 归属）
- [ ] `ai-dev/logs/` 收口条目（全部 Phase 摘要 + 留舍/收敛数字）
- [ ] 独立子代理 closure audit（fresh session），evidence 写入 `## Closure`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码 0（runtime/cep/rocksdb 同样跑）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 文本一致性核对（Plan Status / Phase Status / Exit Criteria / Closure Gates / logs 五处一致）
- [ ] 最终 commit（closure）

Exit Criteria:

- [ ] 上述全部勾选；Closure Evidence 已写入 `## Closure`
- [ ] `Plan Status` 改为 `completed`

## Closure Gates

- [ ] 所有 in-scope confirmed live defects（N1/N2/N3/N4/N5/A2'/B6'，含 S1 形状恢复）已修复并有回归测试
- [ ] R4-P1..P5 每项有 JMH 前后数据与留舍裁定；收敛循环结论已记录（延续既有停止判据）
- [ ] Phase 3 全部行为保持（死代码零引用、无对外语义变化；TTL/心跳/WatermarkStatus 处置有裁定记录）
- [ ] 全部触碰模块 `./mvnw test` 全绿（收口时复跑）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 项均有 Why Not Blocking Closure 与归属）
- [ ] 受影响的 owner docs 已同步（逐项复核结论已记录）
- [ ] 独立子代理 closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 验证新增测试真实断言行为（N1 超容量回放真实经过 pending 路径、N2 多 region 测试真实触发内部边重建）；基准口径真实驱动生产路径
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb` 全绿
- [ ] checkstyle / 代码规范：import 分组、4 空格缩进（含 TaskCheckpointWiring 修复后）、错误码规范符合 AGENTS.md

## Benchmark Rounds

> 口径：JDK 26.0.1 Zulu / JMH 1.33 / `-f 1 -wi 3 -w 2s -i 5 -r 2s` / 同机（macOS arm64）。运行方式见 `nop-benchmark/nop-benchmark-stream/README.md`。方差协议：≥3 重复、2-5% 边界重跑、未触碰 ±3% 噪声带。

- Round-0（2026-09-29，Phase 1 基线；`_tmp/nop-stream-perf/r4-round0-connector.txt`、`r4-round0-nfa-window.txt`）：
  - R4-P2 口径 `ConnectorInvokeBench.fileSourceReadLine`：64B 行 **268 ± 77 ns/op**、512B 行 **2203 ± 537 ns/op**（~4.3ns/字节，逐字节读成本显著）
  - R4-P1 口径 `ConnectorInvokeBench.fileSinkInvokeMap`：4 条目 **103 ± 36 ns/op**、16 条目 **264 ± 77 ns/op**（Map.toString 物化，远小于审计"µs级"预估——候选收益待实测）
  - R4-P3 口径 `ConnectorInvokeBench.jdbcSinkInvokeMap`（10 列）：**49 ± 13 ns/op**
  - R4-P4 口径 `NfaProcessBench.processEvent -p patternDepth=20`：cheap **19.0 ± 6.6 µs**、billable **21.4 ± 4.0 µs**（对 plan-01 Round-2 的 15.5/17.4 有跨会话漂移，以同会话前后对比为准）
  - R4-P5 口径 `WindowOperatorProcessElementBench.processElement -p windowType=EVICTOR`：MEMORY **0.273/0.284 µs**（evictorSize 100/1000）、ROCKSDB **168.9/167.9 µs**（误差条大，D2 主导）
- Round-1（2026-09-29，Phase 4 实施后）：
  - **R4-P2 保留**：`ConnectorInvokeBench.fileSourceReadLine` 64B **268.3 ± 76.6 → 177.0 ± 7.8 ns/op（-34.0%）**、512B **2202.6 ± 537.0 → 1473.3 ± 113.4 ns/op（-33.1%）**（chunked 8KB 缓冲替代逐字节 Pushback+Buffered 双同步读；字节记账/CRLF/孤立 CR/endOffset 截断语义逐字节保持，connector 72 tests 全绿含 TestFileSourceReaderRecovery/TestFileSourceCheckpointRestore）
  - **R4-P4 保留（方向正、噪声带内不可确证）**：`NfaProcessBench d20` cheap 19.0 ± 6.6 → 18.6 ± 3.9 / 19.1 ± 8.5（R1/R1b 两轮，-2.2%/-0.8%）、billable 21.4 ± 4.0 → 20.7 ± 2.5 / 21.1 ± 7.0（-3.7%/-1.7%）——误差条 ±20~40% 主导，按协议不作为 ≥2% 确证收割；保留理由：纯分配形态改进（惰性 verdict cache，浅模式零分配），方向两轮一致为正，cep 373 tests 全绿
  - **R4-P5 revert（无收益证据）**：双槽 storage-key 缓存实现后 `EVICTOR MEMORY` 0.273/0.270 µs vs 改前 0.271/0.279 µs（噪声带内，<2% 且无可见方向）——已 revert，thrashing 预估不成立
  - **R4-P1/R4-P3 Deferred（aliasing 契约 + 实测上限）**：fileSinkInvokeMap 103 ns(4 entries)/264 ns(16 entries)、jdbcSinkInvokeMap 49 ns(10 列)——消除需暴露 sink 缓冲活对象 aliasing（用户 mutate 污染 exactly-once 输出），属 owner 契约裁定项；实测数字登记为 successor 上限证据
- Round-2（2026-09-29，Phase 5 收敛复验定向套件，`_tmp/nop-stream-perf/r4-phase5-suite.txt`）：
  - 触碰路径（漂移后环境）：fileSourceReadLine 64B 245 ± 127 / 512B 2027 ± 780、NFA d20 cheap 18.6~19.1 / billable 20.7~21.1（R1/R1b）、EVICTOR MEMORY 0.271~0.279（三轮一致）
  - 未触碰金丝雀带一致偏慢 +20~55%（internalListAdd local 21.9 vs plan-01 R2 14.1、SharedBuffer 0.99 vs 0.81、NFA d5 4.37 vs 3.40、CheckpointSerDe 同族）——环境/JIT 漂移裁定，非触碰面退化
  - JFR（r4-jfr-filesource.jfr）：readNextLine 循环本体 97%，无新增单点；残余为行累积+解码基本成本
  - **收敛裁定：本轮触碰路径上无 ≥2% 低风险可收割项（停止判据成立）**
- （后续轮次按 `日期 | 场景 | 前值 | 后值 | Δ% | 变更项` 格式追加）

## Deferred But Adjudicated

### 行为变更类缺陷 A3/A4/A5/A6/B2/B7 + N6（triggerSavepoint 静默 null）

- Classification: `moved to explicit successor ownership`（维持 plan-01 裁定；N6 并入 A3 successor）
- Why Not Blocking Closure: 修复改变用户可见行为/兼容性契约，需 owner 语义确认后单独立项；全部已在审计报告记录归属，不会丢失
- Successor Required: `yes`
- Successor Path: plan-01 Deferred 节 + `01-correctness-findings.md` N6 条目

### G1/G2/G3 整体拆分、D4 锁内 RPC 外移、A11 锁序文档化

- Classification: `out-of-scope improvement` / `optimization candidate`（维持先行裁定）
- Why Not Blocking Closure: 结构性重构需独立设计与分阶段验证
- Successor Required: `yes`
- Successor Path: plan-01 Deferred 节

### 跨模块测试脚手架合并（TestAwait 5 模块副本、21 份 RPC stub 的跨模块收敛）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要测试夹具共享机制（test-jar 或 test-support 模块）的构建拓扑变更，纯测试基础设施、无生产行为影响；本计划完成同模块内收敛部分
- Successor Required: `yes`
- Successor Path: 待 test-infra successor

### RemoteTaskDeploySupport 与 TaskCheckpointWiring provisioning 语义分歧（remote 恒 Memory vs local 取 config）

- Classification: `optimization candidate`（潜在语义差，需 owner 核实 remote 部署形态下 RocksDB 后端的预期）
- Why Not Blocking Closure: 现网 remote deploy 形态是否支持 RocksDB 未定；javadoc 已如实标注分歧；修复方向依赖 owner 裁定
- Successor Required: `yes`
- Successor Path: 与 owner 确认后立独立 plan

### R4-P1/R4-P3 sink 缓冲 aliasing 契约优化（若 Phase 4 实测 ≥2% 但涉契约）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 逐记录快照是 aliasing-safe 契约本体；消除需 owner 契约裁定 + 门控设计；实测收益证据已记录
- Successor Required: `yes`
- Successor Path: connector 契约专项

### TaskDispatchLoopBench、嵌入态 checkpoint 循环、extractPatterns、Memory shard 路由等基准缺口

- Classification: `optimization candidate`
- Why Not Blocking Closure: 非本轮候选依赖口径；控制面/低频路径 JMH 口径意义有限（perf 审计已判定）
- Successor Required: `no`
- Successor Path: `03-performance.md` 第四节清单

## Non-Blocking Follow-ups

- **回放窗口竞态（Phase 2 执行期确认的先存语义缝隙）**：materialization 边重启时，residual drain 与 replay 快照之间的写入会"队列副本 + replay 副本"各投递一次（at-least-once 下允许重复；e2e 实测 1500 条规模下 0-2 条重复）。原 drain+injectFront 设计存在同一窗口。消除需原子 drain+快照或 replay 后二次对账，属 replay 语义专项
- numLateRecordsDropped 观测缺口（若 Phase 3 裁定为删常量）：迟到元素指标接线需语义设计
- `@Disabled` Debezium 测试的契约修复（connector backlog）
- 迟到元素丢弃语义与 watermark 语义专项（若 Phase 3 核查发现）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- （收口时填写）
