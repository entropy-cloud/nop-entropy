# 维度 06：测试覆盖与单元测试有效性

> 审计对象：nop-stream 全部 11 个子模块的测试资产（HEAD，2026-10-06）。
> 方法论依据：`ai-dev/skills/unit-test-antipatterns.md`（P-1..P-9），对每条命中做「核心逻辑改成错误实现后测试是否仍通过」的变异推演。
> 盲审纪律：未阅读 ai-dev/audits、plans、bugs、lessons、logs 下任何文件；全部证据来自 live code。

## 审计范围与方法

- 范围：797 个测试文件、约 3928 个 `@Test` 方法（grep 实测）。分布：core 1633 / runtime 1207 / cep 353 / flow 156 / rocksdb 137 / fraud-example 120 / sql 80 / connector 79 / jdbc 55 / debezium 55 / batch 53。
- 方法：
  1. 对照 8 个高风险面逐一核对直接测试（checkpoint 协调、恢复管道、2PC sink 故障注入、窗口合并/evictor/迟到、NFA、keyBy 路由 + maxParallelism、JobCoordinator HA、RocksDB 增量、MemoryStateSerDe 深拷贝）。
  2. 反模式 P-1..P-9 逐类机器扫描（`values().length` 枚举计数、getter/setter 命名、零断言测试、`assertNotNull` 密度、Restore/E2E 命名与实体一致性、P-9 公式自洽输入）+ 人工变异推演抽查。
  3. 隔离性：`@TestMethodOrder`、static 可变状态、`System.setProperty` 恢复、端口/目录共享、skip 登记面。
  4. 端到端保护力：以主 agent 提供的基线（4 个 fraud-example E2E Error、16 skipped）为事实起点，静态核对红灯缺口是否有绿色替代。
- 基线引用（主 agent 提供的可信事实）：`./mvnw test -fn` 于 HEAD：fraud-example 约 120 tests / 4 errors / 16 skipped，其余 10 模块全绿。

### 高风险面核对结论（第 1 步）

| 高风险面 | 直接测试 | 质量判断 |
|---|---|---|
| CheckpointCoordinator barrier/超时/多 epoch | `runtime/checkpoint/TestCheckpointCoordinator.java`（25 个方法：timeout aborts pending、maxConcurrent、storage failure no counter leak、abort idempotent…）、`TestMultiEpochCheckpointE2E`、`TestUnalignedCheckpoint*`、`TestPendingCheckpointStateMachine` | 行为级，@TempDir 隔离，合格 |
| GraphModelCheckpointExecutor restore/rescale | `execution/TestGraphModelCheckpointExecutor*`（EntryPinning、BuildSnapshotFromTaskState、rescale fail-fast 家族）、`integration/TestRealRoutingRescaleE2E`、`TestChannelStateRescaleE2E`、`TestKeyGroupRescaleDispatchE2E` | 真实路径覆盖强；但主测试文件本体含虚测（见 TE-01） |
| 2PC sink（Jdbc/File）故障注入 | `core/.../TestTwoPhaseCommitSinkFunction`（durable/non-durable epoch 混用、rollback 失败被吞、finishCommit 失败保留 pending）、`jdbc/TestJdbcTwoPhaseCommitSinkDeep`（幂等 commit、跨 epoch 幂等守卫、独立事务）、`connector/file/TestFileTwoPhaseCommitSink`（kill-recover exactly-once、并行 manifest 交错）、`TestE2EJdbcTwoPhaseCommitSink`、`TestCheckpointInheritedTwoPhaseCommitRestore`、`integration/TestTwoPhaseCommitSinkParallelismChangeRestoreE2E` | 高质量故障注入，含「删除 commit() 调用测试即失败」的反空洞注释与实证 |
| 窗口合并/evictor/迟到 | `TestSessionWindowAdvancedMerge`、`TestMergingWindowSet`、`TestWindowOperatorMergingCleanupInvariant`、`TestWindowedStreamSideOutputLateData`、`TestWindowOperatorAllowedLateness`、`TestE2EWindowAllowedLatenessFullAggregate`、evictor core+integration 双层 | 覆盖完备 |
| NFA 状态转移/afterMatchSkip/超时 | `cep/nfa/TestNFA`、`TestNFAExtended`、`nfa/aftermatch/TestAfterMatchSkipStrategies`、`TestNFAWindowTimeout`、`TestCepOperatorTimeout`、`TestCepSkipStrategyE2E`、sharedbuffer 9 个文件（含 release 对称不变量） | 覆盖完备 |
| keyBy 路由公式 + maxParallelism 一致性 | `TestKeyGroupAssignment`、`TestKeyGroupAssignmentClosedForm`、`TestKeyRoutingOwnershipParity`、`TestKeyedRoutingParityGuard`、`TestStampedMaxParallelismConsistency`、`TestRealRoutingRescaleE2E` | 全仓库最高质量区（见正面评估） |
| JobCoordinator leader/fencing/global recovery | `coordinator/` 25 个文件：LeaderElection、FencingEpochUnification、FailoverRestore、RecoveryConcurrency、StandbyStateMachine、TerminationMatrix、TerminalSavepointFailure 等 | 覆盖强（进程内 mock elector + mock RPC） |
| RocksDB 增量 checkpoint | `rocksdb/incremental/` 5 个文件：SnapshotStrategy（真实 embedded RocksDB + SST 内容哈希 + 引用计数）、RangeRestore、LifecycleAndIntegrity、RestoreAndBenchmark、BackendWiring、`TestRocksDBIncrementalRestoreFailFast` | 真实 RocksDB 实例，非 mock，合格 |
| MemoryStateSerDe 快照深拷贝 | `memory/TestMemoryStateSerDePointInTimeSnapshot`（ACC/POJO value/map key/state key/appending element 五类「快照后原地突变不得泄漏进快照」）、`TestMemoryStateSerdeRestoreGuards`、`TestStateSerdeFixtureStability` + `state-serde-fixtures/*.json` 真实落盘 fixture | R5 不可变性保护有直接回归钉 |

**结论：8 个高风险面全部存在直接测试，且大多数达到行为级/故障注入级/真实路径级。覆盖广度不是本仓库的短板；本维度发现集中在「少数虚测」「红灯缺口」「默认跳过面」三类。**

---

## 第 1 轮（初审）

### [TE-01] GraphModelCheckpointExecutor 终止派发（DRAIN/SUSPEND→TERMINAL_SAVEPOINT）被 config-getter 镜像虚测「保护」——变异存活

**文件**：`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestGraphModelCheckpointExecutor.java`（全文 165 行，对 1625 行主类）；主类 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java` L267（调用点）、L478-502（实现）。

**证据片段**（测试 L79-96）：
```java
    /**
     * 16-01: Verify that handleJobTermination DRAIN mode triggers TERMINAL_SAVEPOINT
     * checkpoint type.
     *
     * <p>This tests the config dispatch logic: when jobTerminationMode=DRAIN,
     * the executor should trigger a terminal savepoint. We verify this indirectly
     * by checking that a CheckpointConfig with DRAIN mode is properly configured.
     */
    @Test
    void testHandleJobTerminationDrainModeConfig() {
        CheckpointConfig config = CheckpointConfig.builder()
                .jobTerminationMode(JobTerminationMode.DRAIN)
                .checkpointEnabled(true)
                .build();

        assertEquals(JobTerminationMode.DRAIN, config.getJobTerminationMode());
        assertTrue(config.isCheckpointEnabled());
    }
```
javadoc 声称验证「DRAIN mode triggers TERMINAL_SAVEPOINT」，方法体只断言了自己刚 set 进 builder 的值（set 什么 get 什么）。L102-111 的 SUSPEND 版本同构；L116-121 `testDefaultTerminationModeIsCancel` 是默认常量镜像。

**变异推演**：把 `handleJobTermination` 的 DRAIN/SUSPEND 分支整体删除（改为直接 CANCEL、从不触发 terminal savepoint，L495/L502 的 `triggerTerminalSavepoint` 不再被调用）——本文件 8 个测试全部继续通过。javadoc 声称的保护完全不存在。

**严重程度**：P1（对被声称的保护目标而言：测试无法捕获任何真实 bug；对整个文件而言保留边际价值 → 按条目判 P1，整改成本低）。

**现状**：executor 主入口 `executeWithCheckpoint` 的 DRAIN/SUSPEND 终止派发分支在生产上承担「有界作业跑完排干在途数据再落 terminal savepoint」的语义（L267 在主 try 块内、`submitAndRun` 之后），是 DRAIN 语义的正确性关键路径。

**风险**：终止派发回归（如 TERMINAL_SAVEPOINT 被误发成普通 CHECKPOINT、或 DRAIN 不再等待在途数据）时，全套测试仍绿；下游依赖 terminal savepoint 类型的恢复/审计工具会静默拿到错误类型。

**缓解（必须如实记录）**：JobCoordinator 路径的终止语义已被 `coordinator/TestJobCoordinatorTerminationMatrix.java` 以真实 `coordinator.terminate(mode)` 驱动并断言 5 个维度（barrier 类型/JOB_FINISHED 事件/健康迁移/jobStatus/是否停机），该路径有保护；无保护的是 **executor 本体路径** `handleJobTermination`（独立于 coordinator 的本地执行入口）。

**建议**：新增一条 executor 级测试：以 DRAIN 模式驱动 `executeWithCheckpoint`（小拓扑 + bounded source），断言 storage 中最终 checkpoint 的 `CheckpointType == TERMINAL_SAVEPOINT`；SUSPEND 同理。删除或降级 L87-121 的三个 config-getter 测试为附带断言。

**信心水平**：高（证据为全文通读 + 变异推演；全仓 grep 确认无其他测试以 `jobTerminationMode` 驱动 executor 路径）。

**误报排除**：曾怀疑 `TestE2EBoundedJobCheckpointAfterPartialCompletion`/`TestSavepointEndToEnd` 覆盖该分支，grep `jobTerminationMode|DRAIN|SUSPEND|TERMINAL` 均无命中，排除。

**复核状态**：未复核

---

### [TE-02] testDrainModeTriggersTerminalSavepoint / testSuspendModeTriggersSavepoint：测试自行触发 checkpoint，生产派发未被驱动（命中 P-2/P-6）

**文件**：`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestFingerprintAndTerminationMode.java` L253-258、L291-293。

**证据片段**（L256-259）：
```java
        // Trigger a TERMINAL_SAVEPOINT (as DRAIN mode would)
        PendingCheckpoint terminal = coordinator.tryTriggerPendingCheckpoint(CheckpointType.TERMINAL_SAVEPOINT);
        assertNotNull(terminal, "DRAIN mode should trigger a terminal savepoint");
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, terminal.getCheckpointType());
```
断言消息说「DRAIN mode should trigger a terminal savepoint」，实际是测试代码自己调用 `tryTriggerPendingCheckpoint(TERMINAL_SAVEPOINT)`——生产代码（无论哪个模式的派发逻辑）根本没有参与。SUSPEND 版本 L291-293 同构（`// Trigger a SAVEPOINT (as SUSPEND mode would)`）。

**变异推演**：删除生产代码中所有「模式 → checkpoint 类型」映射（DRAIN 也发普通 CHECKPOINT）→ 两个测试仍通过，因为触发方是测试自己。同文件 L214-244 的 `testDefaultJobTerminationModeIsCancel`/`testSetJobTerminationMode`/`testBuilderJobTerminationMode` 是 builder 往返（其中 `setJobTerminationMode(null)→CANCEL` 的 fallback 断言有边际价值，保留）。

**严重程度**：P2（保护力弱但有边际价值：测试确实验证了 coordinator 对 TERMINAL_SAVEPOINT/SAVEPOINT 类型的 ack→complete→retain 全流程，只是与「模式派发」无关）。

**现状**：与 TE-01 同根：模式派发的两条生产路径（executor 与 JobCoordinator）中，只有 JobCoordinator 被真实驱动（TerminationMatrix），coordinator 级与 executor 级的「模式派发」测试都是自我触发/镜像。

**风险**：错误信息会让维护者误以为模式派发已被钉住；两处虚测 + 一处误名测试叠加，形成「termination mode 已有三层测试」的虚假信心。

**建议**：将两个方法改名为 `testTerminalSavepointTypeFlowsThroughAckToCompletedCheckpoint` / `testSavepointTypeFlowsThroughAckToCompletedCheckpoint`（诚实命名），模式派发保护并入 TE-01 建议的 executor 级测试。

**信心水平**：高。

**误报排除**：确认该文件不在 multi-jvm/docker 门控内（默认运行、全绿，正因如此虚测不易暴露）。

**复核状态**：未复核

---

### [TE-03] 4 个 fraud-example E2E 红灯 vs R5 FLOW-5 守卫：fixture 未随守卫更新，三类端到端场景失去绿色保护（套件自相矛盾）

**文件**：
- 守卫：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java` L1124、L1153（`ERR_STREAM_EDGE_PARTITION_UNSUPPORTED`，keyed-stage 并行度 ≠ 上游路由并行度时构建期拒绝）；
- 守卫单元钉：`nop-stream-flow/src/test/java/io/nop/stream/flow/builder/TestKeyedStageParallelismGuard.java`（存在且有效）；
- 红灯测试：`nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestPerTransformParallelismE2E.java`、`TestParallel2PcFileE2E.java`、`TestParallel2PcJdbcE2E.java`、`TestS2OfflineReshardE2E.java`；
- 冲突 fixture：`fraud-example/src/test/resources/_vfs/nop/stream/test/fraud-per-transform-parallelism.stream.xml` L11+L18（`parallelism="1"` 流级、`<map ... parallelism="4"/>`）、L24（`<edge ... partition="HASH" keyExpr="event"/>`）；`fraud-parallel-2pc-file.stream.xml`（流级 1、map/sink 2）、`fraud-parallel-2pc-jdbc*.stream.xml`（流级 1、map/sink 3）。

**证据片段**（守卫实现 L1124-1134）：
```java
            // R5 FLOW-5: a keyed stage sized differently from its upstream
            // routing parallelism rides FORWARD edges (channel-0 concentration)
            // and splits from the key-group ownership after a rescale. Typed
            // reject at build time instead of a silent false-green.
            if (targetParallelism != ((DataStreamImpl) keyed).getTransformation().getParallelism()) {
                throw new StreamException(ERR_STREAM_EDGE_PARTITION_UNSUPPORTED)
```
而红灯 fixture 的拓扑（源 P=1 --HASH--> map P=4）恰好是该守卫拒绝的形状：`parallelism="1"` 流级 + HASH 边 + 目标 4。

**严重程度**：P2（对测试有效性维度：HEAD 上 `./mvnw test -fn` 在 fraud-example 必红——这不是弱测试，是测试套件内部自相矛盾：flow 单测钉住「这种 DSL 必须被拒绝」，fraud E2E 依赖「这种 DSL 必须能跑」）。

**现状**：4 个 E2E Error（主 agent 基线），死的不是坏测试而是三类独有保护面：
1. **per-transform 并行度运行时分布断言**——`TestPerTransformParallelismE2E` 用双线程 latch 强制 >1 个 subtask 真实处理数据（防止「结构假绿」），这个运行时分布验证在 flow 的 `TestPerTransformParallelismWiring` 中只有 JobGraph 结构级替代（断言顶点并行度数值），无「多 subtask 真的分到数据」的运行时替代；
2. **并行 2PC file/jdbc 全链路**（P=2/P=3 的 XDSL→execute→sink 输出）——runtime 侧 `TestTwoPhaseCommitSinkParallelismChangeRestoreE2E` 覆盖的是「并行度变更 restore fail-fast / 同并行度恢复」，不是「多并行度 2PC 正常提交」的业务链路；
3. **S2 offline reshard 端到端**——runtime 的 rescale E2E（TestRealRoutingRescaleE2E 等）覆盖 dispatch/restore，但 S2 场景（离线 reshard 流程）无绿色替代。

**风险**：三类场景的真实回归（如 hash 边在 P>1 下集中到单 subtask、并行 2PC manifest 竞争、reshard 后状态-流量分裂）在默认构建中不可见；更直接的风险是仓库处于「测试必红」状态，`-fn` 掩护下红灯被常态化后新回归混入红灯噪音。

**建议**：二选一并立即执行：(a) 按守卫新语义改写 4 个 fixture（如源并行度与 keyed stage 对齐，用 REBALANCE 语义的替代拓扑或等并行度 + keyBy transform 表达原意图）；(b) 若原拓扑是合法需求，则守卫过严，需回改守卫。无论哪个方向，不允许 4 红灯带病留存。

**信心水平**：高（守卫代码、fixture、基线三方互证）。

**误报排除**：确认 4 个 fixture 均含 `partition="HASH"` + 流级并行度 1 + 目标并行度 >1 的守卫违规形状；确认 `TestKeyedStageParallelismGuard` 单测与守卫行为一致（守卫本身有保护，缺口只在 fixture 侧）。

**复核状态**：未复核

---

### [TE-04] 分布式多进程 E2E 与真实引擎 E2E 全部默认跳过：默认构建的分布式协调保护面为零（16 + 8 + 5 + 1 个 skip）

**文件**：
- fraud-example 16 个 skip（与主 agent 基线的 16 skipped 精确对账）：`TestStabilityExerciseMultiJvm.java`（8 个 @Test，L77）、`TestS1MultiJvmE2E.java`（2，L73）、`TestScenarioBackpressureMultiJvmE2E.java`（2，L54）、`TestS2MultiJvmE2E.java`（2）、`TestParallel2PcMultiJvmE2E.java`（1）、`TestS2RestoreRescaleMultiJvmE2E.java`（1），全部 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`；
- runtime multijvm 8 个（同系统属性门控）：`TestMultiJvmExactlyOnceRecovery`（1）、`TestMultiJvmCoordinatorFailover`（3）、`TestMiniStreamClusterProcessSpawn`（3）、`TestMultiJvmHealthStateAndAlerts`（1）；
- debezium 5 个 docker 门控（`assumeTrue(DOCKER_AVAILABLE)`，如 `TestDebeziumRealMysqlCdc.java` L71）；
- 1 个 `@Disabled`：`connector-debezium/.../TestDebeziumCdcSourceCompletion.java` L26-30，理由为「run() 是无界 source、测试期望自然完成与契约矛盾，需重设计，tracked in connector backlog」。

**证据片段**（TestS1MultiJvmE2E.java L66-73）：
```java
 * <p>Gated by {@code -Dnop.stream.test.multi-jvm.enabled=true}; run manually:
 ...
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
```

**严重程度**：P2（有边际价值：门控是显式登记的（javadoc 写明手动运行命令、skip 语义「SKIP 可见，不伪造通过」），且 CI 无法承载多 JVM 属常见取舍；但结果事实是——**默认构建下，跨进程 leader failover、跨 JVM exactly-once 恢复、反压、稳定性演练的真实协调路径零执行**）。

**现状**：叠加 TE-03 后，fraud-example 默认运行的有效 E2E 从 120 缩水到约 100（16 skip + 4 error）；这 100 个中还有多少是单进程 mock 协调、多少是真双进程，未逐一展开，但进程级协调只有 multi-jvm 包覆盖（已全部 skip）。

**风险**：分布式回归（RPC 消息不兼容、多进程日志/manifest 竞争、进程spawn 参数漂移）只在手动门控运行时暴露；若无人定期手动执行 `-Dnop.stream.test.multi-jvm.enabled=true`，该 25 个测试退化为「永久 skip 的存在性声明」。`@Disabled` 的 completion 测试记录的是未解决的契约张力（自然完成 vs cancel 驱动），backlog 无期限即无收敛压力。

**建议**：为 multi-jvm 门控建立最低执行节奏（如夜间或发版前流水线显式开启），并在某个可 grep 的位置（模块 README 或 pom 注释）登记门控清单与最近一次执行日期；`@Disabled` 的 backlog 项给出到期条件。

**信心水平**：高（skip 计数与基线 16/16 精确对账；全部门控点经 grep 清点）。

**误报排除**：曾核对 `@Tag("low-value")` 是否被 surefire 排除造成额外 skip——`nop-stream/pom.xml` 与根 pom 均无 `excludedGroups`，排除；`TestLeaderElector` 等无断言文件为 fixture 而非测试，排除。

**复核状态**：未复核

---

### [TE-05] testFullPipelineCheckpointAndRestore 名不符实：捕获快照后 run2 从未 restore（命中 P-6）

**文件**：`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestE2ECheckpointAndRecovery.java` L193-300。

**证据片段**（L264-294，run2 全部内容）：
```java
        StreamSourceOperator<Integer> sourceOp2 = new StreamSourceOperator<>(source2);
        StreamMap<Integer, Integer> mapOp2 = new StreamMap<>(x -> x * 10);
        StreamSinkOperator<Integer> sinkOp2 = new StreamSinkOperator<>(new SinkFunction<Integer>() { ... });

        sourceOp2.setOutput(new ChainingOutput<>(mapOp2));
        mapOp2.setOutput(new ChainingOutput<>(sinkOp2));

        sourceOp2.open();
        mapOp2.open();
        sinkOp2.open();
        sourceOp2.run();

        assertEquals(Arrays.asList(60, 70, 80, 90, 100), run2Results);
```
`capturedSnapshot`（L197 捕获、L214 等待完成）在 run2 中没有任何 `restoreState(capturedSnapshot.get())` 调用；run2 是全新无状态管道。方法名与文件名承诺「CheckpointAndRestore」，实际只测了「checkpoint 可完成 + 第二段独立管道可跑」。

**变异推演**：彻底破坏 operator restore 管线（`restoreState` 变 no-op）→ 本测试仍通过。

**严重程度**：P3（命名/结构问题；同文件 L85 的 `testOperatorStateSnapshotAndRestore` 与 L349 的 `testLongKeyedStateJobRestartRestoresSameKeys` 是真实 barrier→snapshot→存储→coordinator 重启→restore→逐值断言的全链路，restore 保护实际存在，本条只扣命名误导分）。

**现状**：该方法位于名为 TestE2ECheckpointAndRecovery 的文件中，是读者寻找「checkpoint→恢复→重处理」全链路时的第一入口，误导成本集中在认知层。

**风险**：维护者误以为「全管道 checkpoint+restore」已覆盖而跳过补测；真正覆盖全链路的两个测试方法名不含 E2E/FullPipeline 字样，检索性差。

**建议**：改名为 `testFullPipelineCheckpointCapturesSnapshotAndSecondRunStartsFresh`，或补真 restore（把 capturedSnapshot 注入 run2 的 operator 再断言重处理语义）。后者为优。

**信心水平**：高（全文 301-430 行无任何对 capturedSnapshot 的 restore 引用，已通读）。

**误报排除**：确认不是「restore 隐藏在 AbstractStreamOperator.open() 内部」——run2 的三个 operator 均未接收 snapshot 参数，构造即无状态，排除。

**复核状态**：未复核

---

## 深挖第 2 轮追加

第 2 轮基于初审模式（虚测/自我触发/误名/镜像常量/skip 清理面）做了四组同模式扫查：

1. **零断言测试扫描**：全仓 797 个测试文件中仅 7 个文件无断言，其中 6 个是 `TestAwait`/`TestOutput`/`TestSourceFunction` 等 fixture（按 Test 前缀命名的测试支撑类，非测试），1 个是 `TestLeaderElector`（ILeaderElector 的确定性测试替身）。**无真实零断言测试。**
2. **「Restore 命名但不 restore」扫描**：对全部 `*Restore*.java` 按 restore 引用数/@Test 数排序检查，最低者（TestRestoreKeyRematerialization 8:6、TestCepStateRestoreAndContinue 9:2 等）均有实质 restore 调用。**除 TE-05 外无同类命中。**
3. **E2E 命名膨胀扫描**：以「无任何执行入口（execute/processBarrier/processElement/triggerCheckpoint/dispatch 等）」为特征扫出 10 个候选，逐一抽查后归类为两种：(a) operator 级快照往返测试借 E2E 之名（`core/checkpoint/TestE2EOperatorStateCheckpoint`、`TestE2EOperatorStateRedistribution`、`runtime/execution/TestE2EBuildSnapshotFromTaskState`、`TestE2EStorageTypeRouting` 等）——本体有效，命名夸张；(b) 被 `@EnabledIfSystemProperty` 门控的 multi-jvm/transport 文件（已计入 TE-04）。**新增 1 条 P3 命名发现（TE-06）。**
4. **镜像常量/low-value 全量清点**：`@Tag("low-value")` 共 33 处（ceiling：TestNFAStateNameHandler 4、TestWindowingModel 3、TestCheckpointType 3、TestDeweyNumber 3、sliding assigners 各 2、transformation 各 2、triggers 3、fraud patterns 4、其余单点）；`void testToString` 13 个。逐类抽查后：多数被标 low-value 的测试实际含解析/边界行为（如 `getOriginalNameFromInternal("stateA:123")` 的后缀剥离），团队标注偏保守；真 P-2 镜像仅 `testStateNameDelim`（断言 `STATE_NAME_DELIM == ":"`）、fraud `testConstants`（断言阈值常量）、`TestGraphModelCheckpointExecutor`/`TestFingerprintAndTerminationMode` 的 config 往返（已计入 TE-01/TE-02）。**并入 TE-07 口径条目。**

### [TE-06] E2E 命名膨胀：operator 级快照往返测试冠以 E2E/FullPipeline 之名（命中 P-6）

**文件**：`nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestE2EOperatorStateCheckpoint.java`（全文，如 L33-65）、`TestE2EOperatorStateRedistribution.java`、`nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestE2EBuildSnapshotFromTaskState.java`、`TestE2EStorageTypeRouting.java` 等。

**证据片段**（TestE2EOperatorStateCheckpoint L37-65）：
```java
    @Test
    void testCheckpointedFunctionWithListStateEndToEnd() throws Exception {
        MemoryStateBackend stateBackend = new MemoryStateBackend();
        ...
        ListState<String> listState = store.getListState(new ListStateDescriptor<>("elements", String.class));
        listState.add("first");
        ...
        OperatorSnapshotResult snapshot = opBackend.snapshotState(1);
        ...
        freshBackend.restoreState(snapshot);
        ...
        assertEquals(3, items.size());
```
链路是「state 写入 → backend 快照 → 新 backend restore → 逐值断言」，无作业/管道/env 参与。测试本身有效（snapshot/restore 行为正确性），「E2E」一词在这里指组件内端到端，与 fraud-example/runtime integration 中「env.execute 到 sink 输出」的 E2E 语义冲突。

**严重程度**：P3（命名/检索问题；本体测试断言具体值与数量，无 P-1/P-5 缺陷）。

**现状**：约 10 个文件的 E2E 后缀表示「组件级闭环」而非「作业级链路」。

**风险**：维护者据名索骥评估「作业级 E2E 覆盖」时高估保护面；与 TE-05 反向叠加（有的 E2E 不执行作业、有的 E2E 名不含 E2E），覆盖地图不可信。

**建议**：统一命名口径：作业级（env.execute/executor 入口）保留 E2E；组件级闭环改为 `TestXxxSnapshotRestoreCycle` 之类。纯重命名，无行为风险。

**信心水平**：高。

**误报排除**：`TestE2EMultipleJobsIsolation`、`TestCheckpointDualPlaneRetentionE2E` 等在第一轮粗筛中命中，复核后确认存在真实执行入口（首次 grep 模式遗漏），已排除，不计入本条。

**复核状态**：未复核

---

### [TE-07] 低价值测试口径与整体配比：33 个自标记 low-value + 13 个 testToString + 约 10 个虚测/镜像，占比约 1.5%（命中 P-1/P-2/P-3 的量化面）

**文件**（抽样举证）：
- `nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFAStateNameHandler.java` L116-121：
```java
    @Tag("low-value")
    @Test
    public void testStateNameDelim() {
        assertEquals(":", NFAStateNameHandler.STATE_NAME_DELIM);
        assertTrue("stateA:0".contains(NFAStateNameHandler.STATE_NAME_DELIM));
    }
```
- `nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/pattern/TestRapidTransactionPattern.java` L100-107（`testConstants`：断言 `getAmountThreshold()==1000`、`getTimeWindowSeconds()==30`）；
- `nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointConfig.java` L45-49（在代码注释中自证历史整改：「the former testSettersAndGetters pure setter/getter round-trip was removed (TE-06, R5 test-effectiveness audit)」——纯 getter/setter 清理已在 HEAD 之前的整改中落地）。

**严重程度**：P3（口径/结构问题）。

**现状**：
- 总量：约 3928 个 `@Test`；自标记 `@Tag("low-value")` 33 个；`testToString` 13 个（断言 toString 含类名/字段值，P-3 级）；虚测/镜像约 10 个（TE-01 的 3 个、TE-02 的 2 个 + 2 个 builder 往返、TE-05 的 1 个、零散 2 个）。
- **有效 : 低价值 ≈ 98.5% : 1.5%**（口径：以「变异后会失败的断言至少一条」为有效下限；把 33 个自标记 + 13 个 toString + 约 10 个虚测全部计入低价值分母上限 56/3928≈1.4%，取整 1.5%）。
- 关键事实：`@Tag("low-value")` 只标记不排除——`nop-stream/pom.xml` 与根 pom 均无 `excludedGroups`/`groups` 配置，这些测试照常运行计数。标记是诚实的，但没有强制力，属于半措施。

**风险**：无直接风险（占比极小、多数仍有边际价值）；风险仅在 tag 的语义漂移——若未来有人误以为 tag 即豁免（如计入覆盖率时剔除），与现状不符。

**建议**：不删（清理收益低于 churn 成本）；若要收口，二选一：在 nop-stream/pom 显式 `excludedGroups=low-value` 让标记生效，或在技能文档中声明该 tag 仅为评审标注。保持现状亦可接受。

**信心水平**：高（三组计数均为 grep 实测；样本逐一人工核对）。

**误报排除**：`TestCheckpointConfig.testDefaultValues` 等默认值钉虽是镜像形态，但对配置类属契约钉（默认值变更常是行为变更），不计入低价值；`TestPartitionRouter.testForwardPartitionRouter_alwaysReturns0` 虽被标 low-value，实为 FORWARD 路由的完整契约，不计入。

**复核状态**：未复核

---

## 正面评估（审计必须同时报告保护力强的实证）

以下抽查均通过「改成错误实现会失败」推演，作为本维度的基线结论：

1. **keyBy 路由 P-9 风险已被三重防线覆盖**：`TestKeyGroupAssignmentClosedForm`（测试内重写遗留 O(P) 扫描作独立参考实现，全网格 39×P×g 对拍 + `spotChecks` 硬编码黄金值 + `notTheFlinkFormula` 反「简化」钉）；`TestKeyRoutingOwnershipParity`（路由入口与属主入口逐类型对拍）；`TestRealRoutingRescaleE2E`（真实 `executeWithCheckpoint` 派发 + **反镜像数据断言** `assertTrue(mismatchesAgainstOwnership > 0, "test data must distinguish the ownership formula from the legacy raw-hash formula")`——这是 P-9 替代做法的标准实现）。R5 担心的「路由公式与属主公式分裂」在 HEAD 有有效钉子。
2. **2PC 故障注入为真实故障注入**：`TestTwoPhaseCommitSinkFunction` 注释自带变异证明（「Removing the commit() call in restoreFromEpoch makes this test fail」），覆盖 durable/non-durable epoch 混用、rollback 失败不炸恢复、finishCommit 失败保留 pending 供 subsuming、并发 finishCommit；`TestFileTwoPhaseCommitSink` 有 kill-recover exactly-once 与并行 subtask manifest 交错；JDBC 侧有跨 epoch 幂等守卫与逐 commit 独立事务。
3. **CheckpointCoordinator 行为矩阵**：timeout aborts pending、maxConcurrent（含 config 遵从）、storage 失败不漏计数器、abort 幂等、consecutive trigger failure reset、shutdown 通知 abort——均以真实 coordinator + LocalFileCheckpointStorage(@TempDir) 驱动。
4. **JobCoordinator HA**：25 个测试文件覆盖 leader 选举、fencing epoch 统一、standby 状态机、failover restore、recovery 并发、terminal savepoint 失败路径（`TestJobCoordinatorTerminalSavepointFailure` 补齐 matrix 的失败面）、终止矩阵 5 维参数化（真实 `terminate(mode)` + auto-ACK mock TM + 明确注明失败面归属）。
5. **MemoryStateSerDe 不可变性**：五类「快照后原地突变不得泄漏」的负向断言（ACC、POJO value、map key、state key、appending element），配 `state-serde-fixtures/*.json` 落盘 fixture（真实序列化形态输入，非内存直通——符合 P-9 替代做法）。
6. **RocksDB 增量**：真实 `RocksDB.open` + flush + SST 内容哈希（64 字符断言）+ 引用计数 + 增量差集（`assertFalse(newOnes.isEmpty())`、`assertTrue(after.containsAll(before))`）。
7. **隔离性纪律良好**：无 `@TestMethodOrder`；`System.setProperty` 使用点均有 finally/@AfterEach 恢复（逐一核对 5 处）；`@TempDir` 广泛使用；`localhost:xxxx` 均为 mock registry 字符串非真实绑定；`nop-stream/pom.xml` 设 `forkedProcessTimeoutInSeconds=300` 并注释了标定依据（挂死护栏）；multi-jvm/docker 门控全部显式登记理由与手动运行命令（skip 可见、不伪造通过）。
8. **优先级分配正确**：core(1633)+runtime(1207) 占 72% 且集中在执行/checkpoint/状态后端（高风险面），CEP 353 全部押在 NFA/sharedbuffer/operator 状态；数据模型（@DataBean）测试极少（model/ 仅 3 个文件）——与反模式技能的优先级排序（核心算法 > 集成点 > 防御 > 数据模型 > 常量）一致。

## 最终保留项（表格索引）

| ID | 标题 | 命中反模式 | 严重程度 | 模块/文件 |
|---|---|---|---|---|
| TE-01 | executor 终止派发（DRAIN/SUSPEND→TERMINAL_SAVEPOINT）被 config-getter 镜像虚测「保护」，变异存活 | P-1、P-2 | P1 | runtime `TestGraphModelCheckpointExecutor.java` L87-121 vs `GraphModelCheckpointExecutor.java` L267/L478-502 |
| TE-02 | DRAIN/SUSPEND 模式测试自我触发 checkpoint，生产派发未被驱动 | P-2、P-6 | P2 | runtime `TestFingerprintAndTerminationMode.java` L256-258、L291-293 |
| TE-03 | 4 个 fraud E2E 红灯 vs R5 FLOW-5 守卫：fixture 未更新，并行 2PC/运行时分布/reshard 端到端保护面丢失，套件自相矛盾 | （覆盖缺口，非反模式命中） | P2 | flow `StreamModelDslBuilder.java` L1124/L1153；fraud 4 个 `*E2E.java` + 4 个 `.stream.xml` fixture |
| TE-04 | 多进程/真实引擎 E2E 全部默认跳过（16 fraud multi-jvm + 8 runtime multijvm + 5 debezium docker + 1 @Disabled 契约悬置） | （skip 清理面） | P2 | fraud 6 个 `*MultiJvm*.java`；runtime `multijvm/`；debezium 5 个门控 + `TestDebeziumCdcSourceCompletion.java` L26 |
| TE-05 | testFullPipelineCheckpointAndRestore 从不 restore，名不符实 | P-6 | P3 | runtime `TestE2ECheckpointAndRecovery.java` L193-300 |
| TE-06 | E2E 命名膨胀：约 10 个组件级闭环测试冠以 E2E 之名 | P-6 | P3 | core `TestE2EOperatorStateCheckpoint.java` 等 |
| TE-07 | 低价值占比约 1.5%（33 自标记 low-value + 13 testToString + 约 10 虚测/镜像），tag 无强制力 | P-1、P-2、P-3 | P3 | 全仓；举证 `TestNFAStateNameHandler.java` L116-121 等 |

**总计 7 项：P1 × 1，P2 × 3，P3 × 3。**

---

## 维度复核结论（独立复核员，与初审无关）

复核方法：逐条亲读被引测试与被测代码；TE-01 做独立变异推演（静态推理）；全仓 grep 复核驱动面与缓解面；TE-03 追加 git 时序核查（守卫提交 58895e4e95 / 3a271f8880 之后 fraud-example 零提交）与全 nop-stream fixture 形状扫描。未运行 mvn（按复核纪律）。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| TE-01 | **保留 P1** | 新发现 | 变异推演独立成立：`handleJobTermination` 为 private、仅 `executeWithCheckpoint` L267 单点调用；全仓无任何测试以 DRAIN/SUSPEND 驱动 `executeWithCheckpoint`；删除 `triggerTerminalSavepoint` 调用后本文件全部测试（实为 9 个，初审写 8 为小误计，不影响结论）与全仓测试均存活。TerminationMatrix 缓解属实（`@ParameterizedTest` DRAIN/SUSPEND→TERMINAL_SAVEPOINT，真实 `coordinator.terminate(mode)`）。三处虚测引文逐字核对无误 |
| TE-02 | **保留 P2** | 新发现 | 两个测试确系自我触发：`tryTriggerPendingCheckpoint(TERMINAL_SAVEPOINT/SAVEPOINT)` 由测试显式调用，且 grep 证实 `CheckpointCoordinator` 根本不读取 `jobTerminationMode`——config 里的 DRAIN/SUSPEND 对 coordinator 零影响，断言消息误导性成立。L216-243 builder 往返中 `null→CANCEL` fallback 断言（L233-234）确有边际价值 |
| TE-03 | **保留 P2（附证据修正）** | R5 修复（plan 2313 FLOW-5 守卫）落地后的新残余；红灯为主 agent 基线已知、未修复 | 守卫 L1124/L1153 代码、守卫单测形状、4 个 fixture 违规形状三方互证成立；git 证实守卫落地后 fraud-example 零提交。**但修正两点**：(1) 守卫致红仅可静态确证 3 个测试（PerTransform/File/Jdbc 各 1 个 @Test）——第 4 个被点名的 `TestS2OfflineReshardE2E` 使用 `fraud-s2-file.stream.xml`（全 FORWARD 边、内联 keyBy 无并行度声明），按守卫代码静态推理**不可能**触发 FLOW-5，初审「误报排除」中「确认 4 个 fixture 均含 partition=HASH+流级 1+目标>1」对该 fixture 失实；其 Error 根因另有他因（本次复核未能静态定位，亦未运行验证）。(2)「S2 离线 reshard 无绿色替代」偏重：runtime 侧 `TestMaxParallelismReshardMigrationE2E` 已绿覆盖迁移工具全离线链路（staged savepoint→reshard→reload→restore→正确性），失去绿色保护的是 fraud S2 场景链（真实场景 savepoint→迁移→恢复→exactly-once 输出）而非迁移工具本身。套件自相矛盾的核心结论不受影响 |
| TE-04 | **保留 P2** | 已知未修复（显式登记的门控取舍；R5 盲区自评已提「分布式多 JVM 故障注入覆盖薄」） | 计数逐项精确对账：fraud 6 类 @EnabledIfSystemProperty 共 8+2+2+1+2+1=16，与基线 16 skipped 精确吻合；runtime multijvm 4 类 1+3+3+1=8（`MiniStreamCluster` 为 fixture 0 测试，未计入，正确）；debezium「5 个 docker 门控」按测试计为 5（4 文件，`TestDebeziumRealMysqlParallel` 经 `@BeforeEach` assumeTrue 门控 2 个测试）；`@Disabled` L26 属实；nop-stream/pom 与根 pom 均无 excludedGroups，属实 |
| TE-05 | **保留 P3** | 新发现 | run2（L256-291）确为全新无状态管道，`capturedSnapshot` 仅在 L196/231/253-254 出现（声明/捕获/断言），无任何 restore 注入；变异「restoreState 变 no-op」下本测试不受影响，推演成立。L120/L410 证实同文件两个真实 restore 测试存在，「只扣命名误导分」的 P3 定级恰当 |
| TE-06 | **保留 P3** | 新发现 | 抽查引用的 4 个文件（TestE2EOperatorStateCheckpoint/Redistribution、TestE2EBuildSnapshotFromTaskState、TestE2EStorageTypeRouting）均无 env/作业级执行入口，证据成立。两点保留意见：(a)「约 10 个」为近似值，宽口径扫描（无 StreamExecutionEnvironment/execute/barrier 入口的 `*E2E*` 文件）上限达 30+，其中 alert/ops/transport/supervision 等子系统级 E2E 命名可辩护，种群边界模糊；(b) 初审以「存在真实执行入口」排除 `TestE2EMultipleJobsIsolation` 略牵强——该文件实为 coordinator 级 trigger→ack→complete 测试，无作业级入口，按本条自身口径应属命名膨胀，属边界裁定分歧，不改变结论 |
| TE-07 | **保留 P3** | R5 已修复的新残余（R5 TE-6 getter/setter 清理已在 HEAD 落地，本条量化其残余） | 计数全部实测吻合：`@Tag("low-value")` 33 处、`testToString` 13 个；`TestCheckpointConfig` L46 自证历史整改注释属实；`TestNFAStateNameHandler` L118-121 与 `TestRapidTransactionPattern` L104-108 引文逐字核对无误；两 pom 无 excludedGroups（tag 无强制力）属实。血缘提示：R5 六号报告亦有一条被驳回的 TE-7（「不再重开」），本条为更保守的量化口径且建议「保持现状亦可接受」，实质不构成重开诉讼 |

### P1 重点复核详情（TE-01 变异推演独立复核与裁定）

**变异设定**：删除 `GraphModelCheckpointExecutor.handleJobTermination` 中 DRAIN/SUSPEND 分支的 `triggerTerminalSavepoint` 调用（L495/L502），或将两分支改道 CANCEL 的 `triggerFinalCheckpoint`。

**逐断言静态推演**（本文件 9 个 @Test 逐一过）：

1. `testCreateStorageDefaultPath` / `testCreateStorageCustomPath` / `testCreateStorageUnknownTypeThrows` / `testCreateStorageJdbcThrows`：仅驱动静态 `createStorage(config)`，与 `handleJobTermination` 无任何耦合 → 存活。
2. `testHandleJobTerminationDrainModeConfig`（L88-96）/ `testHandleJobTerminationSuspendModeConfig`（L103-111）/ `testDefaultTerminationModeIsCancel`（L117-121）：仅构造 `CheckpointConfig` 并断言自身 getter 往返；被测方法为 private 且仅被 `executeWithCheckpoint` L267 调用，三个测试从未进入执行器 → 存活。
3. `testRestoreFromSavepointPathNoSavepointStartsFresh` / `testRestoreFromSavepointPathEmptyDirectoryStartsFresh`：仅查询空 storage 的 `getLatestCheckpoint` → 存活。

**全仓爆炸半径核查**：`handleJobTermination` 全仓仅 1 处调用（L267）。穷举所有设置 `JobTerminationMode.DRAIN/SUSPEND` 的测试文件——`TestJobCoordinator*` 五件（coordinator 自有 `terminate` 实现，不经 executor）、`TestJobTerminationContext`（core 配置类）、`TestFingerprintAndTerminationMode`（TE-02 自触发 + builder 往返）、本文件三虚测——**无一以 DRAIN/SUSPEND 驱动 `executeWithCheckpoint`**。全部 `executeWithCheckpoint` 调用方（14 个测试文件逐一核对）均用默认 CANCEL，走未变异的 `triggerFinalCheckpoint` 分支。**变异后全仓测试套件零新增失败，推演成立。**

**缓解面核实**：`TestJobCoordinatorTerminationMatrix` 以 `@ParameterizedTest` 钉住 DRAIN→TERMINAL_SAVEPOINT、SUSPEND→TERMINAL_SAVEPOINT（L56-58），真实调用 `coordinator.terminate(mode)`（L132），断言含 JOB_FINISHED 事件维度（L140）——coordinator 路径确有真实保护，初审的缓解披露如实。无保护的是 executor 本体路径，且三个虚测 javadoc 声称的保护目标正是该路径。

**裁定**：**保留 P1**。对「被声称的保护目标」（executor 终止派发）而言变异完全存活、测试价值为零，且虚测以「16-01」编号与行为级 javadoc 伪装成行为测试，误导成本高于普通镜像测试；整改成本低（新增一条 executor 级 DRAIN/SUSPEND 测试 + 删三个 config 测试）。初审唯一瑕疵：测试总数为 9 非 8（不影响任何结论）。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| TE-01 | P1 | `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestGraphModelCheckpointExecutor.java`（L88-121 三虚测）vs `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`（L267/L482-510） | executor 终止派发（DRAIN/SUSPEND→TERMINAL_SAVEPOINT）仅被 config-getter 往返虚测「覆盖」，变异推演全仓存活；coordinator 路径另有 TerminationMatrix 真实保护 | 新发现 |
| TE-02 | P2 | `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestFingerprintAndTerminationMode.java`（L248-278、L283-313） | DRAIN/SUSPEND 模式测试自我触发 checkpoint（CheckpointCoordinator 根本不读 jobTerminationMode），断言消息把类型往返伪装成模式派发保护 | 新发现 |
| TE-03 | P2（附证据修正） | `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`（L1124/L1153）；`nop-stream-fraud-example` 的 `TestPerTransformParallelismE2E`/`TestParallel2PcFileE2E`/`TestParallel2PcJdbcE2E` + 4 个 `.stream.xml` fixture | R5 FLOW-5 守卫落地后 3 个 fraud E2E fixture 未同步更新致构建期必红，套件自相矛盾（flow 单测钉「必须拒绝」、fraud E2E 依赖「必须能跑」），per-transform 运行时分布与并行 2PC 链路失去绿色保护；S2 成员的守卫归因不成立（其 fixture 全 FORWARD 无违规形状）、迁移工具本身有 `TestMaxParallelismReshardMigrationE2E` 绿色替代 | R5 修复落地后的新残余（红灯已知、未修复） |
| TE-04 | P2 | fraud 6 个 `*MultiJvm*.java`；`nop-stream-runtime/.../multijvm/` 4 类；debezium 4 文件 5 测试 docker 门控；`TestDebeziumCdcSourceCompletion.java` L26 | 默认构建下跨进程协调/exactly-once/反压/真实引擎 E2E 零执行（16+8 skip 精确对账 + 5 docker + 1 @Disabled），门控显式登记但无最低执行节奏约定 | 已知未修复（显式登记取舍） |
| TE-05 | P3 | `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestE2ECheckpointAndRecovery.java`（L193-299） | testFullPipelineCheckpointAndRestore 从不 restore 捕获的快照，run2 为全新无状态管道，名不符实；同文件 L85/L349 有真实 restore 全链路 | 新发现 |
| TE-06 | P3 | `nop-stream-core/.../TestE2EOperatorStateCheckpoint.java` 等 4+ 组件级闭环文件 | 组件级 snapshot/restore 往返测试冠以 E2E 之名，与作业级 E2E 语义冲突，覆盖地图检索不可信（计数为近似、边界模糊） | 新发现 |
| TE-07 | P3 | 全仓；举证 `TestNFAStateNameHandler.java` L118-121 等 | 低价值测试占比约 1.5%（33 自标记 + 13 toString + 约 10 虚测），tag 无 pom 强制力；建议保持现状或登记 tag 语义 | R5 已修复后的新残余（量化） |

**复核统计：7 条全部保留（7 保留 / 0 降级 / 0 驳回）；P1×1、P2×3、P3×3 与初审一致。证据修正 2 处（TE-01 测试计数 9 非 8；TE-03 第 4 红灯成员守卫归因不成立 + S2 绿色替代表述偏重），均不改变发现主体与定级。**
