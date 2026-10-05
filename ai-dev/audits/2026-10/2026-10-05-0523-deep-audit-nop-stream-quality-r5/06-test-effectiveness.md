# 06 测试覆盖与单元测试有效性深度审计（nop-stream，R5 轮报告 06 号）

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`
- **审计依据**: `ai-dev/skills/unit-test-antipatterns.md`（P-1..P-8）、`docs-for-ai/02-core-guides/testing.md`、`AGENTS.md`
- **抽样口径**: 全量 781 个测试文件（nop-stream-core 256 / runtime 283 / flow 42 / cep 65 / sql 18 / rocksdb 26 / connector 15 / connector-batch 9 / connector-jdbc 8 / connector-debezium 14 / fraud-example 45），合计约 3906 个 `@Test` 方法（grep 计数）。**逐方法深读约 30 个测试类 + 4 个测试支撑类**，覆盖全部 11 个子模块，优先：checkpoint 协调（ReproC2/C3、BoundedRetry、ParticipantContraction、TerminalSavepointFailure、RecoveryRobustness）、SupervisionLoop/TaskManager 并发（CommitQueueBound、AbortWiring）、memory/RocksDB backend（TtlSweep、RestoreKeyRematerialization、AuditFixes）、CepOperator/NFA（ProcessingTimeBucketDrain、TestNFA、CepOperatorBasic）、窗口算子（PerWindowStateCleanup、WM1）、JDBC 2PC sink（SinkDml、LedgerNamespace）、Debezium source（Sharding、ErrorPropagation、SourceCompletion）、rescale/reshard E2E（MaxParallelismReshardMigrationE2E）、flow/sql/fraud/connector-batch 各抽样 2-4 类。横切 grep：@Disabled、@Timeout、Thread.sleep、裸 `future.get()`、@TestMethodOrder、断言密度、零断言文件、JUnit4 残留。另对照 plan 369 Phase 1-4 与 plan 368 各 Phase、plan 2306（44 项）修复 commit 的 diff/stat，对修复批次回归测试逐个做变异式推演。**本报告为只读分析，未运行 maven/测试；所有"变异式推演"均为静态推理，非实际执行变异。**

---

## 总体结论

nop-stream 测试面整体质量**显著高于一般同规模项目**：修复批次（plan 368/369/2306）新增的聚焦回归测试绝大多数做到了"断言钉住修复语义 + 失败信息解释 pre-fix 行为"，并且大量使用确定性 fake clock、TestAwait 轮询、错误码断言、不变量表测试。抽样中**未发现 P1 级（变异后仍通过且无任何捕获能力）的纯假测试**。主要问题集中在：(1) 少量"PRE-FIX repro"测试只钉住缺陷机制而非修复语义，回退修复仍绿；(2) C3 修复的分布式 tombstone 接线完全没有测试（变异删除该行全仓仍绿）；(3) 异步测试基建规则（testing.md 明文要求的 @Timeout / 带超时 get()）只在 2026-09 之后的测试中执行，2026-08 及以前的长尾测试违规；(4) 少量常量镜像测试与绕过引擎的半集成测试。

---

## 发现条目

### [TE-1] ReproC2/ReproC3 "PRE-FIX repro" 测试钉住缺陷机制而非修复语义——回退修复后测试仍绿（命中 P-4 的镜像变体：测试镜像"缺陷常量"而非行为契约）
- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/ReproC2TerminalSavepointSkippedOnInFlightCollision.java:65-79`；`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/ReproC3CompletedTaskStallsCheckpoints.java:86-126`
- **证据片段**: ```java
// ReproC2 —— 断言的是 pre-fix 缺陷机制本身：
PendingCheckpoint terminal = coordinator.tryTriggerPendingCheckpoint(CheckpointType.TERMINAL_SAVEPOINT);
assertNull(terminal, "PRE-FIX BUG confirmed: terminal savepoint trigger silently skipped"
        + " (null) while a periodic checkpoint is in flight");
// ReproC3 —— 故意不调用 markTaskCompleted，然后断言参与者集合未收缩：
PendingCheckpoint p2 = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
assertEquals(2, p2.getNumberOfNotAcknowledgedTasks(),
        "PRE-FIX BUG confirmed: the completed task is still a checkpoint participant");```
- **严重程度**: P2
- **现状**: 两个类在 javadoc 中自我声明为 "PRE-FIX repro"。修复（C2 的 `triggerCheckpointBounded` 包装、C3 的调用方接线）落在**调用方**，`tryTriggerPendingCheckpoint` 的 null-返回/未收缩语义修复后保持不变，因此这两个测试在修复前、修复后同样通过——它们钉住的是协调器层面的机制，不是修复。
- **风险**: 变异式推演：**回退 C2/C3 修复（把调用方改回 silent-skip / 删除 markTaskCompleted 接线），这两个 repro 全部保持绿色**——按 anti-patterns 的判定标准属于"对修复语义的假保护"。若未来重构删掉了配套的 `TestCheckpointTriggerBoundedRetry` / `TestE2EBoundedJobCheckpointAfterPartialCompletion`，仓库里将只剩一组"永远绿"的 repro，制造已保护的错觉。它们能捕获的真实 bug 仅限协调器核心行为本身的退化（例如 null 语义变化），边际保护存在但与修复无关。
- **建议**: (1) 给两个 repro 类加 `@Tag("pre-fix-repro")` 并在类 javadoc 指明"保护本修复的测试是 X/Y"；(2) 或将其改写为双向测试——同一场景分别断言 raw 语义（null/不收缩）与 bounded 语义（重试成功/收缩），使单文件内同时钉住机制与修复。
- **信心水平**: 确定（diff 与 javadoc 均证实修复在调用方；测试只触达 `tryTriggerPendingCheckpoint`）
- **误报排除**: C2/C3 修复整体并非无保护——`TestCheckpointTriggerBoundedRetry`（重试成功/预算耗尽 typed error）、`TestE2EBoundedJobCheckpointAfterPartialCompletion`（本地 SupervisionLoop 扫描接线，checkpointId>=5 断言）是真正的行为级保护；本条仅针对 repro 两个类自身的保护力。
- **复核状态**: 未复核

### [TE-2] C3 修复的分布式腿（JobCoordinator.reportTaskStatus → checkpointCoordinator.markTaskCompleted）零测试保护，变异删除该行全仓仍绿
- **文件**: 生产 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1086-1092`；对照测试 `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/coordinator/TestJobCoordinatorRecoveryRobustness.java:366-427`
- **证据片段**: ```java
// JobCoordinator.reportTaskStatus —— C3 分布式 tombstone 接线（生产代码）：
try {
    checkpointCoordinator.markTaskCompleted(new TaskLocation(
            jobId, "pipeline-0", report.getVertexId(), report.getSubtaskIndex()));
} catch (Exception e) { LOG.warn("Failed to contract completed task ..."); }
// REG-02 测试只断言 liveness map，从不断言 checkpoint 参与者集合收缩：
coordinator.reportTaskStatus(new TaskStatusReport(JOB_ID, "source", 0, 1,
        TaskStatusReport.TerminalState.COMPLETED, null, frozen, ...));
assertEquals(0, coordinator.getSubtaskLivenessCount(), "COMPLETED removes the entry");```
- **严重程度**: P2
- **现状**: 全仓 grep `markTaskCompleted` 在测试中的出现点只有 runtime/checkpoint 包的 4 个测试类，全部是**直接对 CheckpointCoordinator 调用**（单元级），没有任何测试经由 `JobCoordinator.reportTaskStatus` 或 `SupervisionLoop` 的完成事件间接触发参与者收缩。`TestJobCoordinatorRecoveryRobustness` 的 REG-02 两条用例持有一个真实 `CheckpointCoordinator`（`newCoordinator()` 已注入）却只断言 `subtaskLiveness`。
- **风险**: 变异式推演：**删除 JobCoordinator.java:1088 的 `markTaskCompleted` 调用（或让 catch 吞掉后不再收缩），全套 781 个测试文件预计仍然全绿**——分布式路径下已完成任务将持续占用 checkpoint ACK 集合，每个后续 epoch 等满 checkpointTimeout 后 abort（正是 C3 记录的 600s 超时 abort 循环），而测试无法发现。顺带：该行硬编码 `"pipeline-0"`（本地路径用 AssignmentPlanner 注册的同一 family），多 pipeline 任务下会把收缩打错 family——同样无测试可拦截。
- **建议**: 在 `TestJobCoordinatorRecoveryRobustness`（已有真实 CheckpointCoordinator 注入）补一条用例：`reportTaskStatus(COMPLETED)` 后断言 `checkpointCoordinator` 下一 epoch 的 `getNumberOfNotAcknowledgedTasks()` 收缩 1；并为 SupervisionLoop 的本地扫描接线（SupervisionLoop.java:370）保留现有 E2E（`TestE2EBoundedJobCheckpointAfterPartialCompletion` 已覆盖本地腿，勿删）。
- **信心水平**: 很可能（grep 未能穷尽反射/字符串引用，但同类接线测试的命名与内容均未命中）
- **误报排除**: `TestCheckpointParticipantContraction` 等对收缩语义本身的单元覆盖是充分的，缺口仅在"谁负责调用"的接线层；`ReproC3` 明确注释了"故意不调用"。
- **复核状态**: 未复核

### [TE-3] fraud-example 三个 pattern 测试类的 testConstants() 是常量镜像测试（命中 P-2/P-4）
- **文件**: `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/pattern/TestRapidTransactionPattern.java:102-112`；`TestAccountTakeoverPattern.java:215-224`（同模式）
- **证据片段**: ```java
@Tag("low-value")
@Test
public void testConstants() {
    assertEquals(new BigDecimal("1000"), RapidTransactionPattern.getAmountThreshold(),
        "Amount threshold should be 1000");
    assertEquals(30, RapidTransactionPattern.getTimeWindowSeconds(),
        "Time window should be 30 seconds");
    assertEquals("RAPID_TRANSACTION", RapidTransactionPattern.getFraudType(),
        "Fraud type should be RAPID_TRANSACTION");
}```
- **严重程度**: P2
- **现状**: 断言的就是实现返回的常量本身，测试与实现"说的是同一件事"。团队已意识到并打了 `@Tag("low-value")`，但测试仍在套件中运行并通过 CI 门禁。
- **风险**: 变异式推演：把 `getAmountThreshold()` 的返回改成任何值，测试会失败——但失败对应的不是 bug（阈值调整是正常产品变更），测试只是镜像常量，没有独立验证能力；同时它不能发现阈值语义错误（如窗口单位秒/分混用）。按 anti-patterns 判级："会失败但断言镜像常量"→P2。
- **建议**: 删除 testConstants，将阈值断言合并进真正经过 NFA 的行为测试（`TestGeographicAnomalyPatternFix`/`TestRapidTransactionPattern` 若有 NFA 级用例则在其中附带断言阈值边界：恰好 30s 内两笔 → 命中，31s → 不命中），让常量经输入/输出间接钉住。
- **信心水平**: 确定
- **误报排除**: `@Tag("low-value")` 表明这是有意保留的冒烟级断言而非无意识产物；fraud 模块其余测试（NFA 级 Fix 测试、scenario E2E）质量良好。
- **复核状态**: 未复核

### [TE-4] TestGeographicAnomalyPattern 主体绕过 CEP 引擎手工构造 match map，检测语义只有 companion "Fix" 测试在守护（命中 P-3 的变体：只测数据变换、不测匹配条件）
- **文件**: `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/pattern/TestGeographicAnomalyPattern.java:42-86`；对照 `TestGeographicAnomalyPatternFix.java:82-85`（经 NFACompiler + NFA 真实匹配）
- **证据片段**: ```java
// 手工构造本应由 CEP IterativeCondition 产生的匹配结果：
Map<String, List<TransactionEvent>> match = new HashMap<>();
match.put("city1", List.of(firstEvent));
match.put("city2", List.of(secondEvent));
FraudAlert alert = GeographicAnomalyPattern.generateAlert(match);
assertNotNull(alert, "Alert should not be null");   // P-5 弱断言混入
```
- **严重程度**: P3
- **现状**: 该类 5 个方法全部把 `generateAlert` 当纯函数测，"不同城市+同一用户"的检测条件（写在 pattern 的 IterativeCondition 里）完全不经过引擎。真正的条件语义由 `TestGeographicAnomalyPatternFix`（走真实 Pattern + NFA）覆盖；两个类并存。
- **风险**: 单看原类，变异式推演：把 IterativeCondition 改成"同城市也告警"（真实缺陷），本类全绿——检测语义假保护。由于 Fix 类存在，模块级保护成立，故降为 P3（结构问题：同类内一半方法提供假信心，误导后来者以为条件已在此验证）。
- **建议**: 在 TestGeographicAnomalyPattern 类 javadoc 标注"条件语义由 Fix 类守护"；或把 generateAlert 断言合并进 Fix 类的匹配用例后删除原类。
- **信心水平**: 确定
- **误报排除**: 同模块 `TestAccountTakeoverPattern` 的 `testGenerateAlertRejectsDifferentUsers` 等负路径测试部分弥补了拒绝语义（尽管同样绕过引擎）。
- **复核状态**: 未复核

### [TE-5] TestCheckpointAbortWiring：assertThrows 后的 assertNotNull 同义反复（P-5）+ 墙钟宽限期断言 + 源内长 sleep，脆弱且慢
- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointAbortWiring.java:130-148、154-213`
- **证据片段**: ```java
StreamException thrown = assertThrows(StreamException.class, () ->
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "...", config));
long elapsed = System.currentTimeMillis() - startTime;
// Wiring verification: ...
assertNotNull(thrown);   // assertThrows 非 null 返回后永真（P-5）
long grace = 15_000L;
assertTrue(elapsed < checkpointInterval + checkpointTimeout + grace, ...);```
- **严重程度**: P3
- **现状**: abort 接线验证思路正确（checkAbortMarker 只有 abort 处理器置位才抛异常），但 (1) `assertNotNull(thrown)` 是 assertThrows 已保证的同义反复；(2) 存活断言是 `elapsed < interval+timeout+15s` 的墙钟上界，慢 CI 下 1.5s 超时 + 调度延迟可能越界造成 flake；(3) 源函数用 `Thread.sleep(200)×1000` / `Thread.sleep(5000)` 循环，类上无 `@Timeout`，违反 testing.md 异步防挂起规则第 1/5 条。
- **风险**: 变异式推演：把 abort 处理器改成"只标记不取消任务"，`elapsed` 上界断言仍可能通过（取决于卡住多久），测试保护力集中在"最终抛 StreamException"一点；调度抖动则造成假红。真正风险是 CI 长尾波动而非漏报。
- **建议**: 删除 `assertNotNull(thrown)`；上界断言放宽为"在超时+固定宽限内完成"的同时加下界断言（>= timeout，证明确实经过了超时路径）或改用 TestAwait 轮询 abort 标志；类加 `@Timeout(60)`。
- **信心水平**: 确定（代码可见）；flake 频率为有趣的猜测（未运行测试）
- **误报排除**: 该测试的"验证 abort 处理器确实被调用"的机制（checkAbortMarker 抛错）本身是有效设计，不是纯时序测试。
- **复核状态**: 未复核

### [TE-6] 异步/并发测试基建规则长尾违规：@Timeout 仅 9/781 文件，裸 `future.get()` 约 30 处（testing.md 明文要求全部带超时）
- **文件**: 例证 `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/cluster/TestJdbcLeaderElector.java:90,109,150,211,259`；`.../checkpoint/TestCheckpointCoordinator.java:141,203` 等
- **证据片段**: ```java
// TestJdbcLeaderElector —— 测试主线程裸等待，无超时：
LeaderEpoch epoch = elector.whenElectionCompleted().toCompletableFuture().get();
// TestCheckpointCoordinator —— 同样模式：
CompletedCheckpoint completed = pending.getCompletableFuture().get();```
- **严重程度**: P3
- **现状**: `docs-for-ai/02-core-guides/testing.md`"异步与并发测试的防挂起规则"要求类级 `@Timeout(10)` 与每个 `Future.get()` 带超时。2026-09 之后的修复批次测试（TestCheckpointTriggerBoundedRetry、ReproC3、CommitQueueBound 等）已严格执行（`get(5, TimeUnit.SECONDS)`）；但 2026-08 及以前的老测试长尾约 30 处裸 `get()`、仅 9 个文件使用 `@Timeout`。
- **风险**: 选举/检查点完成的任何回归（例如 CC-15 类回归导致选举永不完成）会让测试**无限挂起**而非失败，CI 表现为超时杀进程、丢整批结果，掩盖真正的缺陷定位信息。挂起还会占用 fork 线程拖慢全量套件。
- **建议**: 一次性横切整改：为含异步等待的测试类补 `@Timeout`，`sed` 级替换裸 `.get()` → `.get(10, TimeUnit.SECONDS)`（约 30 处，机械可做）；可在 code review checklist 增加一条 grep 门禁。
- **信心水平**: 确定（grep 计数：@Timeout 9 文件；裸 get() 与 Future 相关行约 30 处）
- **误报排除**: 裸 `get()` 中相当一部分位于已由 latcher 保证可达的路径（如 election latcher 先 countDown），单点挂起概率低；本条是基建健壮性问题而非正确性缺陷。
- **复核状态**: 未复核

### [TE-7] TestDebeziumCdcSourceCompletion 整类 @Disabled：source 自然完成/取消驱动完成契约完全没有可执行测试（覆盖缺口）
- **文件**: `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumCdcSourceCompletion.java:26-34`
- **证据片段**: ```java
@Disabled("Contract mismatch: DebeziumCdcSourceFunction.run() is an unbounded source — it "
        + "only returns on cancel() or truncateForDrain(), never on natural end. The test's "
        + "expectation (natural completion without cancel) contradicts that contract; the "
        + "test needs a redesign toward the cancel-driven contract. Tracked in the connector "
        + "backlog; see plan 366 follow-up.")
public class TestDebeziumCdcSourceCompletion {
```
- **严重程度**: P3
- **现状**: 全仓唯一一个 @Disabled（grep 全 nop-stream 仅此一处）。禁用理由清晰（契约不匹配）、已登记 backlog，是诚实做法；但结果是 `run()` 的返回/完成语义自 plan 366 起持续无测试。
- **风险**: 完成路径是 source 生命周期中"取消/完成"这一半的契约面：后续若 `truncateForDrain` 或 cancel 协作逻辑回归（如 cancel 后 run 不返回、或 bounded 场景提前返回），现有套件（ErrorPropagation 只测失败传播、Sharding 只测部署期路由）不会失败。
- **建议**: 按 disable 说明重写为 cancel 驱动契约测试：起 source → 注入事件 → cancel() → 断言 run 在有限时间返回且 offset 已持久化（复用模块内 TestAwait 风格）；在 connector backlog 项关闭时同步翻绿。
- **信心水平**: 确定
- **误报排除**: Debezium 真库 E2E（TestDebeziumRealMysqlCdc 等）覆盖了 kill/restore 路径，但它们是 Docker 条件测试（见 TE-8），不构成对本契约的常规回归面。
- **复核状态**: 未复核

### [TE-8] Debezium 真库 CDC 闭环测试依赖 assumeTrue(DOCKER_AVAILABLE) 静默跳过，CI 无 Docker 时整条真库回归面为零且不可见
- **文件**: `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumRealMysqlCdc.java:71`；`TestDebeziumRealPostgresCdc.java:61`；`TestDebeziumRealMysqlParallel.java:71`；`TestDebeziumCrossDbSyncPipeline.java:80`
- **证据片段**: ```java
org.junit.jupiter.api.Assumptions.assumeTrue(DOCKER_AVAILABLE,
        "...");   // 无 Docker 环境：整个测试类静默 VESSEL_SKIPPED
```
- **严重程度**: P3
- **现状**: 4 个真库测试类（MySQL kill/restore 不重不丢、PG 全谱 CDC、并行快照、跨库同步——历史上揪出过 4 个真产品缺陷的面）在无 Docker 的 CI/本地环境全部静默跳过。assumeTrue 是标准做法，但仓库没有"跳过率上报"或"CI 必须带 Docker"的门禁约定。
- **风险**: CDC 数据面（EventConverter、schema history、UPSERT_DELETE 镜像）的回归只能被 H2 级单元测试部分拦截；converter 数值宽度 CCE 这类曾被真库测试抓住的缺陷形态，在无 Docker 环境会直接漏进主干。跳过不可见意味着"绿"并不代表"真库面被验证"。
- **建议**: 在 CI 流水线声明一个强制 Docker 的 job（哪怕夜间频率），并在测试报告输出 skipped 计数；或为这 4 类加 `@Tag("docker")` 便于门禁区分。
- **信心水平**: 确定（代码可见）；"CI 是否恒有 Docker"为有趣的猜测（仓库内未见 CI 配置约束）
- **误报排除**: assumeTrue 优于 @Disabled（有 Docker 时仍会跑）；connector-jdbc 2PC/ledger 测试用 H2 自建表，不依赖 Docker，2PC 语义面不受本条影响。
- **复核状态**: 未复核

### [TE-9] 覆盖缺口：unaligned checkpoint 的分布式穿线路径生产代码自述未接线、未测试；core 的 streamrecord/configuration 包零测试
- **文件**: 生产自述 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/TransportDurability.java:53-62`；缺口包 `nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/`（7 类）、`.../core/configuration/`（1 类）
- **证据片段**: ```java
// TransportDurability javadoc（生产代码自述测试缺口）：
// "the current distributed plan builders ... do not thread the unaligned-checkpoint
//  flags into the remote InputGates, so the fail-fast in validateUnalignedConfig()
//  currently fires on the LOCAL launch chain."
// 包级统计（find 对照）：core/streamrecord：0 个测试文件；core/configuration：0 个测试文件
```
- **严重程度**: P2
- **现状**: (1) unaligned checkpoint 的 channel-state 捕获在单测/本地 E2E 层有覆盖（TestUnalignedCheckpointBackpressure/MultiInput、TestChannelStateCapture/RestoreIsolation），但分布式 plan builder 不穿线 unaligned 标志——生产 javadoc 自己承认 fail-fast 只在本地链生效，分布式路径的声明（C4 的 DURABLE/NON_DURABLE）目前"没有启动期牙齿"；(2) `streamrecord` 包（StreamRecord/Watermark/SideOutputElement 等核心数据载体）与 `configuration` 包无任何直接测试（部分经算子测试间接覆盖）。
- **风险**: 若未来分布式端启用 unaligned 配置，静默降级为 aligned 或在恢复期丢 in-flight channel state——无测试报警。streamrecord 类是纯数据载体（P-1 规则下本就不必单测），间接覆盖可接受，风险低；unaligned 分布式缺口是真实的高风险恢复路径缺口。
- **建议**: 按 javadoc 的 TODO 语义补一条分布式 plan builder 的契约测试（断言 remote InputGate 收到 durability/unaligned 声明，或断言显式拒绝），在接线落地前先把"当前不穿线"钉成显式契约防静默变更。
- **信心水平**: 确定（生产 javadoc 自述 + 包级 find 统计）
- **误报排除**: TestRemoteDeployCheckpointWiringE2E、TestRemoteDataExchange、TestDataPlaneKafkaBackendE2E 覆盖了分布式部署与数据面主路径，缺口仅限 unaligned/durability 声明穿线；runtime/deploy、maintain 包实际各有 1-2 个测试类（同包测试无需 import，此前按 import 误判为 0，已更正）。
- **复核状态**: 未复核

### [TE-10] 固定 Thread.sleep 长尾：个别新测试对 nanotime 门/重试节奏做了毫秒级墙钟假设，慢 CI 下有温和 flake 倾向（P-7 无关，纯时序脆弱）
- **文件**: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/state/backend/memory/TestMemoryStateTtlSweep.java:126-133`；`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointTriggerBoundedRetry.java:84-100`
- **证据片段**: ```java
try {
    Thread.sleep(5); // let the nanotime gate elapse (deterministic in practice)
} catch (InterruptedException e) { ... }
backend.setCurrentKey("unrelated"); // the periodic trigger point
```
- **严重程度**: P3
- **现状**: 横切统计：94 个测试文件共 187 处 `Thread.sleep`，绝大多数在可接受形态（TestAwait 轮询 20ms、确定性阻塞-释放模式的 park 探测、带 deadline 的自旋）。少数对真实墙钟做毫秒级假设：TtlSweep 的自动触发用 `sleep(5)` 等 1ms nanotime 门——JVM 调度抖动超过 5ms 时门已过期，测试仍绿；风险方向是"门未过期时 setCurrentKey 不触发 sweep → assertTrue(storage.isEmpty()) 假红"。BoundedRetry 的 `sleep(300)` 后 ACK 是主动制造的延迟下界（断言 `>= 250ms` 已按此设计），属正确用法。
- **风险**: 假红方向为主（CI 抖动→偶发失败→信任损耗），无漏报风险；量级小（点状 2-3 处）。
- **建议**: TtlSweep 该用例改为 `TestAwait.until("gate elapsed", () -> nanotime 差值 > interval)` 或将 interval 与 sleep 解耦（提供 `setTtlSweepClockForTesting`）；其余保持现状。
- **信心水平**: 很可能（未实际复跑；判断基于 nanotime 门与 sleep 的语义差）
- **误报排除**: 大多数 sleep 用法符合 testing.md 第 4/5 条（sleep + 标志位、自旋+短 sleep），测试基建整体成熟（TestAwait 提供 until/staysTrue/untilThreadParked 全套确定性等待原语）。
- **复核状态**: 未复核

---

## 修复批次测试保护力矩阵（变异式推演）

> 方法：对照 plan 369 P1-P4 / plan 368 各 Phase / plan 2306 的 diff，逐个回答"把修复回退，哪个测试会红？"。✔=会红（有保护）；✖=仍绿（假保护）；—=未纳入本轮抽样。

| 修复项 | 配套回归测试 | 回退修复后 |
|---|---|---|
| C2 bounded retry（coordinator 层） | TestCheckpointTriggerBoundedRetry（重试成功/NO_TASKS 短路/预算耗尽 typed error） | ✔ 三条用例分别红 |
| C2 分布式失败路径（failJob loud） | TestJobCoordinatorTerminalSavepointFailure | ✔ FAILED/FINISHED/health 断言红 |
| C2 repro | ReproC2TerminalSavepointSkippedOnInFlightCollision | ✖（见 TE-1） |
| C3 参与者收缩（coordinator 原语） | TestCheckpointParticipantContraction、TestCheckpointInheritedSegmentRefCount、TestCheckpointInheritedTwoPhaseCommitRestore | ✔ 参与者计数断言红 |
| C3 本地接线（SupervisionLoop 扫描） | TestE2EBoundedJobCheckpointAfterPartialCompletion（checkpointId>=5 + 全量 task-state 断言） | ✔ 红 |
| C3 分布式接线（reportTaskStatus tombstone） | （无） | ✖ 无任何测试（见 TE-2） |
| C3 repro | ReproC3CompletedTaskStallsCheckpoints | ✖（见 TE-1） |
| G-3 per-window state 清理 | TestWindowOperatorPerWindowStateCleanup（purge/cleanup/merge-retire 三路径） | ✔ CLEARED_STATE_VALUES 断言红 |
| W-M1 late-drop counter 按 subtask 隔离 | ReproWM1LateDropCounterSharedAcrossInstances、TestWindowOperatorLateDropMetricScope | ✔（注：assertNotNull(counter) 断言在 Micrometer `Search.counter()` 对缺失 meter 返回 null 语义下非同义反复；计数断言独立兜底） |
| ST-02 restore 键重材料化（RocksDB） | TestRocksDBRestoreKeyRematerialization（Bean/Date 键可读可写 + 分区守恒 30 键） | ✔ 读 miss → assertEquals 红 |
| ST-02（memory） | TestMemoryRescaleNonPrimitiveKeyRestore | —（未抽样，命名与 RocksDB 版同构，很可能 ✔） |
| ST-12 memory TTL sweep | TestMemoryStateTtlSweep（裸存储表断言/幂等/无 CME/自动触发/禁用开关/TTL-free 零回扫） | ✔ 五条用例红 |
| CEP-01 PT 排水 ledger 断链 | TestCepOperatorProcessingTimeBucketDrain（三轮定时器 + ledger 清空断言） | ✔ 第二轮断言红 |
| CC-15 epoch 单调 | TestJdbcLeaderElectorEpochMonotonicity（真 H2，租约行删除后 epoch > 历史最大） | ✔ 红 |
| CC-10 commit 队列有界 | TestTaskManagerCommitQueueBound（确定性饱和/CallerRuns 计数/gauge/typed fail-fast） | ✔ 红 |
| CC-07/CC-01/REG-02（coordinator 并发） | TestJobCoordinatorRecoveryRobustness | ✔ liveness/中断标志断言红（但见 TE-2 的 C3 侧缺口） |
| WI5 dmlMode 2PC | TestJdbcTwoPhaseCommitSinkDml（同 key 折叠、last-write-wins、UPSERT_DELETE、ledger 幂等重提交 SENTINEL 不落库） | ✔ 红 |
| 2306 ledger namespace | TestJdbcTwoPhaseCommitLedgerNamespace（同 job 双分支不互锁/跨 job 隔离/版本化表名） | ✔ 红 |
| WI4 Debezium 分片 | TestDebeziumCdcSharding（确定性路由、负 hash floorMod、零分片 fail-fast、config 不污染） | ✔ 红 |

**矩阵结论**: 修复批次 19 项抽样中 17 项具备行为级真保护（回退必红），2 项 repro 假保护（TE-1），1 条接线腿完全裸奔（TE-2）。修复批次的测试文化（javadoc 写明 pre-fix 行为、断言错误码、断言"pre-fix 会如何"）显著优于仓库平均水平。

---

## 覆盖缺口清单（高风险生产路径，无/弱测试）

1. **C3 分布式 tombstone 接线**（JobCoordinator.reportTaskStatus → markTaskCompleted）：零测试（TE-2，P2）。
2. **unaligned checkpoint 分布式穿线**：生产自述未接线、无契约测试（TE-9，P2）。
3. **Debezium source 完成/取消契约**：整类 @Disabled（TE-7，P3）。
4. **取消/中断路径的 JobCoordinator 覆盖偏薄但非零**：CC-11（interrupt 标志恢复）已有专测；`stop()`/shutdown 与 checkpoint 并发取消的组合路径主要靠 TestCheckpointCoordinator.testShutdown 级别的单测，分布式取消 E2E 未见。
5. **core/streamrecord、core/configuration**：零直接测试（低风险，数据载体+配置聚合，间接覆盖）。
6. 正面确认：异常路径（serde 损坏 TestCheckpointSerDeCorruptRecord、JdbcDuplicateKeyDetection、路径穿越 TestLocalFileCheckpointStoragePathTraversal）、恢复路径（reshard E2E 全谱、增量恢复 TestRocksDBIncrementalRangeRestore、恢复重材料化）、并发路径（RecoveryConcurrency、CheckpointConcurrencySafety、CommitQueueBound）均有实质测试。

---

## 测试基建评估

- **AutoTest 快照模式**: nop-stream 全模块 **0 个文件**使用 JunitAutoTestCase/@NopTestConfig——流计算域全部使用进程内 fake/嵌入式运行时（H2、LocalFileCheckpointStorage、LocalMessageService、MiniStreamCluster），与 docs-for-ai"外部依赖优先 stub/fake"的默认原则一致，无快照回放漂移风险面。
- **隐式依赖（P-7）**: 未发现测试间顺序依赖（无 @TestMethodOrder/@Order；抽样类均在 @BeforeEach 自建状态或显式 `CLEARED_STATE_VALUES.clear()`）。静态可变共享仅见于少数测试内自用集合，风险可控。
- **@Disabled/手动 skip**: @Disabled 全仓 1 处（TE-7，有记录）；Assumptions 跳过 4 处（TE-8）；无 silently-catch-all 风格的 skip。
- **Thread.sleep**: 94 文件/187 处，整体克制（TE-10）。
- **@Timeout / 裸 get()**: 长尾违规（TE-6）。
- **零断言测试文件**: 0 个（grep 校验全部 @Test 文件含 assert/fail/throw）。
- **JUnit4 残留**: 0。
- **测试支撑设施**: TestAwait（until/elapsed/staysTrue/untilThreadParked + 60s 死锁保护）是亮点；CoordinatorTestSupport、flow/testing、cep/CepTestUtils 均为真实可复用 fake。

## 比例统计（含口径声明）

- **口径**: 以"逐方法深读 + 变异式推演"的约 30 个测试类（约 210 个 @Test 方法，占总量约 5.4%）为样本，按子模块分层抽样（每模块 2-6 类，修复批次全查），外推到 781 文件/约 3906 方法。**比例是外推估计，非全量逐方法复核。**
- **强保护测试（回退核心逻辑必红、断言语义明确）**: 抽样中约 72%；外推约 70-75%。修复批次新增测试内部高达 ~90%（19 项抽样 17 项真保护）。
- **边际价值测试（只验证基本集成/常量镜像/弱断言，但有捕获力）**: 抽样约 22%；外推约 20-25%（典型：TaskIdentityDtoRoundTripTest 属 DTO 序列化契约、TestTransportDurability 属分类器分支、TestStreamGraphGenerator 的 assertNotNull 群但另有 69 条结构断言）。
- **低价值/假保护测试**: 抽样约 6%；外推约 3-6%（testConstants 类、repro 双子、@Disabled 类、TestGeographicAnomalyPattern 非引擎路径部分）。
- **P1（变异后仍通过且无任何捕获能力）**: 抽样 0 个。仓库不存在 assertNotNull 遍历/getter-setter 遍历型的纯水测试。
- **横切数据**: @Disabled 1、Docker assumeTrue 4 类、@Timeout 9 文件、Thread.sleep 187 处/94 文件、裸 future.get() 约 30 处、零断言文件 0、JunitAutoTestCase 0。

## 检查范围

- **模块与文件数（src/test .java）**: nop-stream-core 256、nop-stream-runtime 283、nop-stream-flow 42、nop-stream-cep 65、nop-stream-sql 18、nop-stream-rocksdb 26、nop-stream-connector 15、nop-stream-connector-batch 9、nop-stream-connector-jdbc 8、nop-stream-connector-debezium 14、nop-stream-fraud-example 45；合计 781；@Test 合计约 3906。
- **深读测试类（≥半文）**: ReproC2、ReproC3、TestCheckpointTriggerBoundedRetry、TestJobCoordinatorTerminalSavepointFailure、TestE2EBoundedJobCheckpointAfterPartialCompletion、TestJobCoordinatorRecoveryRobustness（REG-02/CC 段+setup）、TestTaskManagerCommitQueueBound、TestCheckpointAbortWiring、TestWindowOperatorPerWindowStateCleanup、ReproWM1LateDropCounterSharedAcrossInstances、TestMemoryStateTtlSweep、TestRocksDBRestoreKeyRematerialization、TestCepOperatorProcessingTimeBucketDrain、TestNFA、TestJdbcTwoPhaseCommitSinkDml、TestJdbcLeaderElectorEpochMonotonicity、TestTransportDurability、TestGeographicAnomalyPattern、TestDebeziumCdcSharding、TaskIdentityDtoRoundTripTest、TestStreamGraphGenerator（断言统计+方法体抽样）、TestFingerprintAndTerminationMode、TestAwait、TestRapidTransactionPattern、TestAccountTakeoverPattern、TestDebeziumCdcSourceCompletion（@Disabled 段）。
- **统计/对照性检查（未逐行深读）**: TestCheckpointCoordinator（方法清单+断言统计）、TestCepOperatorBasic、TestJdbcTwoPhaseCommitLedgerNamespace、TestDebeziumCdcSourceErrorPropagation、TestMaxParallelismReshardMigrationE2E、TestRocksDBAuditFixes、flow/sql/connector-batch 全量文件清单与断言密度扫描、fraud scenario 目录清点。
- **生产代码对照**: CheckpointCoordinator（markTaskCompleted/triggerCheckpointBounded 签名）、JobCoordinator.java:1070-1100、SupervisionLoop.java:289-370（grep 定位）、TransportDurability 全文、运行时包结构（deploy/maintain/transport/metrics）、core 包-测试对照矩阵。
- **横切 grep**: @Disabled、@Timeout、Thread.sleep（187）、Assumptions、@TestMethodOrder/@Order、assertNotNull 密度 Top15、零断言文件、JUnit4 import、裸 future.get()、JunitAutoTestCase/@NopTestConfig。
- **未覆盖（明示）**: 781 文件中约 750 个仅经命名/断言密度统计触及；multijvm（MiniStreamCluster/进程派生）与 quickstart 未深读；未运行任何测试（只读纪律）。

---

## 维度复核结论

> 复核人：R5 轮「测试覆盖与单元测试有效性」独立维度复核子代理。方法：对 TE-1..TE-10 逐条重读被引测试文件与生产代码，重跑关键 grep（markTaskCompleted / reportTaskStatus / @Timeout / 裸 get() / @Disabled / Assumptions / Thread.sleep），并对 TE-2 做了独立的"删一行全仓是否仍绿"变异式推演（只读静态推理，未运行 maven）。核对基准：`ai-dev/skills/unit-test-antipatterns.md` P-1..P-8、`docs-for-ai/02-core-guides/testing.md` 防挂起规则（L475-484）。原发现内容未做任何修改。

### TE-1 —— 保留（P2）
理由：证据行号准确（ReproC2 测试体 L65-79、ReproC3 L86-126，引文逐字吻合）。修复落点在调用方已独立证实：C2 的 `triggerCheckpointBounded` 调用点在 JobCoordinator.java:2373 与 GraphModelCheckpointExecutor.java:415/531/773；C3 的接线在 SupervisionLoop.java:370（本地）与 JobCoordinator.java:1088-1089（分布式）。两个 repro 只触达 `tryTriggerPendingCheckpoint` 的原语语义（碰撞返回 null / 无通知不收缩），回退调用方修复后确实保持绿色——"钉机制不钉修复"的判定与 P-4 镜像变体归类成立。"误报排除"段列出的真保护测试（TestCheckpointTriggerBoundedRetry 三用例、TestE2EBoundedJobCheckpointAfterPartialCompletion 的 checkpointId>=5 断言）均实证存在。P2 恰当（有边际协调器语义保护，非 P1）。

### TE-2 —— 保留（P2），附一处附带论断更正
理由：核心主张经独立复核成立。(1) 全仓 grep 证实 `markTaskCompleted` 在测试中的出现点仅 4 个文件：TestCheckpointParticipantContraction、TestCheckpointInheritedSegmentRefCount、TestCheckpointInheritedTwoPhaseCommitRestore（三者均为直接对 CheckpointCoordinator 原语调用）+ ReproC3（仅注释提及，故意不调用）；(2) 所有经由 `JobCoordinator.reportTaskStatus(COMPLETED)` 的测试（RecoveryRobustness REG-02 两条 L377-427、DeployGracePeriod L156-161、PerTaskFailure L150-186）只断言 liveness/recovery 计数，无一断言 checkpoint 参与者收缩；(3) 关键加固证据：REG-02 测试的 CheckpointCoordinator 从未调用 `setTasksToAcknowledge`（无注册 ACK 集），即使接线存在 `markTaskCompleted` 也是 no-op（CheckpointCoordinator.java:1361-1363 对未注册位置直接忽略）——该测试结构上不可能拦截此变异；(4) 本地腿 E2E（TestE2EBoundedJobCheckpointAfterPartialCompletion）走嵌入执行 + SupervisionLoop，不经 JobCoordinator；(5) multijvm 支撑设施（MultiJvmTestSupport.waitForDurableManifest）只在无任务完成的无界运行期断言 durable manifest，fraud scenario 与 multijvm 套件均非有界完成→checkpoint 断言；(6) TestJobCoordinatorTerminalSavepointFailure 完全不含 reportTaskStatus/COMPLETED/markTaskCompleted。综上"删除 JobCoordinator.java:1088 全仓预计仍绿"在静态推演下成立，P2 恰当（C3 记录的 600s timeout-abort 生产事故路径的接线层零保护）。**更正**：附带论断"硬编码 pipeline-0 多 pipeline 任务下会把收缩打错 family"不成立——AssignmentPlanner.java:194 注册 ACK 集时同样硬编码 "pipeline-0"，且 JobCoordinator 为单 plan（单 pipeline）实例，注册方与 tombstone 方 family 按构造一致；该附带句应视为未证实，不影响本条核心结论。

### TE-3 —— 保留（P2）
理由：证据逐字吻合（TestRapidTransactionPattern L102-111；TestAccountTakeoverPattern 实际 L215-222，引文区间 215-224 含下个方法 @Test 行，属无害偏差）。第三处 testConstants（TestGeographicAnomalyPattern L169-176）经独立查找确认存在，与"三个 pattern 测试类"的表述一致。形态与 P-4 的判例（`assertEquals(0.7, SIMILARITY_THRESHOLD)`）完全同构，P-2/P-4 归类成立；`@Tag("low-value")` 属实。按本报告判级口径（"会失败但断言镜像常量"→P2）恰当。

### TE-4 —— 保留（P3），附一处推演修正
理由：结构性事实全部证实——TestGeographicAnomalyPattern 全部 7 个 @Test 均未经过 NFA/CEP 引擎（5 个 generateAlert 纯函数测试 + pattern 结构 + testConstants），引文行号准确（L42-86）；检测条件（同用户+不同城市）确实写在 GeographicAnomalyPattern 的 city2 IterativeCondition 中；TestGeographicAnomalyPatternFix 经 NFACompiler + nfa.process 真实引擎驱动（runPattern L79-110）。**推演修正**：原发现举例的变异"同城市也告警"实际上在 Fix 类中同样存活——Fix 类的两条用例均未向引擎喂入同城负例（contiguous 用例全是异城对，interleaved 用例断言的是跨用户连续性破坏）。即该特定变异下模块级保护并不成立，原发现"由于 Fix 类存在，模块级保护成立"对所举变异而言偏乐观；这反而强化了本条（同城拒绝边界连 Fix 类也未钉住），但不改变 P3 定级与建议。P3 恰当。

### TE-5 —— 保留（P3）
理由：三个子主张全部实证：(1) `assertNotNull(thrown)` 在 L139，位于 assertThrows（L132-133）之后，同义反复成立（P-5）；(2) 墙钟上界断言 L142-144 与 L208-210（interval+timeout+15s grace）属实；(3) 源函数 Thread.sleep(200)×1000（L61）、sleep(1000)/sleep(5000) 循环（L78/L166），类上确无 @Timeout，违反 testing.md 防挂起规则第 1 条（L479）。"checkAbortMarker 机制本身有效"的误报排除公允。flake 频率已自我标注为猜测。P3 恰当。

### TE-6 —— 保留（P3）
理由：全部计数独立复现——`@Timeout` 恰好 9 个测试文件；裸 future `.get()`（`toCompletableFuture().get()` / `getCompletableFuture().get()` 模式）29 处，与"约 30 处"吻合；testing.md 防挂起规则第 1/2 条实在于 L479-480；例证行号逐一核实（TestJdbcLeaderElector.java:90,109,150,211,259、TestCheckpointCoordinator.java:141,203 全部命中）。判级 P3（基建健壮性而非正确性缺陷）与"误报排除"中对 latcher 已保证可达路径的让步均恰当。

### TE-7 —— 驳回（覆盖缺口主张被反证；残留仅为死代码清理备忘）
理由：反证充分且在同一模块内。TE-7 声称"source 自然完成/**取消驱动完成**契约完全没有可执行测试"，且风险段断言"若 cancel 后 run 不返回……现有套件（ErrorPropagation 只测失败传播、Sharding 只测部署期路由）不会失败"。但同模块的 TestDebeziumCdcSourceFunction（未禁用）已实质性覆盖取消/排水驱动的完成契约并带线程终止断言：`testRunAfterCancelReEntersLoop` L388-390 断言 cancel 后 run 返回（`assertFalse(runner1.isAlive(), "first run must terminate after cancel()")`）、L409-411 二次 cancel 后返回、L404-405 正向钉住无 cancel 时 run 持续存活（"instead of returning immediately (silent EOS)"——即无界不自然返回语义有正向可执行钉住）；`testTruncateForDrainStopsSource` L301-304 断言 truncateForDrain 后 run 返回；另有 `testCancelAfterDrainDoesNotThrow`、`testDrainingFlagResetOnReRun`；offset 持久化另有 TestDebeziumCdcCheckpoint。被 @Disabled 的唯一场景（自然结束）按禁用说明本身即违反当前契约（run 只在 cancel/truncateForDrain 返回），不属于应钉住的契约面。故"完成契约覆盖缺口"的前提不成立，P3 定级失去对象。残留价值仅为：应删除或按 cancel 驱动契约重写该死测试类（卫生问题，不构成发现）。

### TE-8 —— 保留（P3）
理由：4 个真库测试类的 assumeTrue 行号逐一核实无误（MysqlCdc:71、PostgresCdc:61、MysqlParallel:71、CrossDbSync:80），全仓 Assumptions 恰好这 4 处。assumeTrue 优于 @Disabled、"CI 是否恒有 Docker 未见配置约束"的口径自我限定恰当。跳过不可见性是真问题，P3 恰当。

### TE-9 —— 保留（P2），附行号更正
理由：核心主张全部证实：(1) TransportDurability 生产 javadoc 自述分布式 plan builder 不穿线 unaligned 标志、fail-fast 仅本地链生效（原文逐字存在），且运行时层 TestUnalignedCheckpointBackpressure/MultiInput、TestChannelStateCapture 存在而分布式穿线无契约测试；(2) core/streamrecord（4 类 + watermark 子包 2 类）与 core/configuration（1 类）确认零直接测试文件（find 为空；93 个测试文件 import streamrecord 属间接覆盖，与原发现"间接覆盖可接受，风险低"的自评一致）。**更正**：所引生产自述行号应为 TransportDurability.java:36-44（非 53-62），引文内容逐字无误，属行号陈旧而非证据错误；streamrecord 类计数实为 6（原记 7），差异不影响结论。unaligned 分布式缺口维持 P2。

### TE-10 —— 保留（P3，定级下缘），附机制说明
理由：证据行号准确（TestMemoryStateTtlSweep L126-133 的 sleep(5)+1ms nanotime 门、TestCheckpointTriggerBoundedRetry L84-100 的 sleep(300) 属"主动延迟下界 + >=250ms 断言"的正确用法，与原发现自评一致）；横切计数精确复现（187 处/94 文件）。**机制说明**：所担心的假红方向（"门未过期 → setCurrentKey 不触发 sweep → isEmpty 断言假红"）要求 sleep(5) 在 nanoTime 上不足 1ms 即返回——Thread.sleep 不会提前正常返回（仅中断可提前），故该具体假红场景概率趋近于零，实际风险低于"温和"的表述；且原发现的变异方向描述（抖动超 5ms → 门已过期 → 仍绿）本身即为绿灯方向。保留为 P3 下缘的依据：sleep 与 nanotime 的耦合确属可解耦的脆弱写法，建议（TestAwait/注入 clock）有效，但不应被读作现实 flake 源。

### 复核总评
10 条中 9 条证据经独立重验成立（其中 3 条带非实质性更正：TE-2 附带论断、TE-4 变异举例、TE-9/TE-10 的机制与行号校正），1 条驳回（TE-7，其"取消驱动完成契约零覆盖"的前提被同模块 TestDebeziumCdcSourceFunction 的可执行断言直接反证）。原报告的抽样口径声明、比例统计的自我限定（"外推估计，非全量逐方法复核"）与保护力矩阵抽查部分（C2/C3 行、TestCheckpointTriggerBoundedRetry、本地腿 E2E）均与代码事实相符。原报告"抽样中未发现 P1 纯假测试"的总体结论在复核后维持。

## 最终保留项

| 编号 | 裁决 | 最终等级 | 一句话理由 |
|---|---|---|---|
| TE-1 | 保留 | P2 | 修复在调用方已实证，repro 回退修复仍绿，钉机制不钉修复成立 |
| TE-2 | 保留（附带论断更正） | P2 | 独立变异推演证实分布式 tombstone 接线零可达测试；"多 pipeline family 打错"附带句撤回 |
| TE-3 | 保留 | P2 | 三处 testConstants 逐字证实，P-4 镜像判例同构 |
| TE-4 | 保留（推演修正） | P3 | 绕过引擎属实；所举变异连 Fix 类也拦不住，问题略重于原文但定级不变 |
| TE-5 | 保留 | P3 | 同义反复断言、墙钟上界、缺 @Timeout 三项全部实证 |
| TE-6 | 保留 | P3 | 计数精确复现（@Timeout 9 文件、裸 get() 29 处），testing.md 规则实证 |
| TE-7 | **驳回** | — | 取消/排水驱动完成契约已被 TestDebeziumCdcSourceFunction L301-304/L388-411 可执行断言覆盖；残留仅为删除死测试类的卫生备忘 |
| TE-8 | 保留 | P3 | 4 处 assumeTrue 行号逐一核实，跳过不可见性成立且自我限定恰当 |
| TE-9 | 保留（行号更正） | P2 | 生产 javadoc 自述证实（实际 L36-44）；两 core 包零直接测试证实 |
| TE-10 | 保留（P3 下缘） | P3 | 证据属实但具体假红机制（sleep 早返回）概率趋零，不应读作现实 flake 源 |
