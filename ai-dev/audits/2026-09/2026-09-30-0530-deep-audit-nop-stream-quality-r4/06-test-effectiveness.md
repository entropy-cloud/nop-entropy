# nop-stream 测试覆盖与单元测试有效性深度审计（R5 轮 · 维度 06：Test Effectiveness）

> 审计轮次：R4 深度审计后续 R5 轮（测试有效性维度）
> 审计日期：2026-09-30
> 审计范围：nop-stream-core / runtime / cep / rocksdb / flow / connector / connector-jdbc / connector-batch / connector-debezium 全部 src/test（fraud-example / quickstart 仅作旁证，不计入主统计）
> 审计基线：`ai-dev/skills/unit-test-antipatterns.md`（P-1..P-8）；以 live code（HEAD 0e67dba845 之后工作区）为准
> 方法：plan 366 Phase 2 九项修复 × 对应回归测试逐一"改回错误实现是否仍失败"推演 + 反模式全库扫描（含脚本化 assertNotNull-主导方法识别）+ 覆盖缺口定位 + 隔离/脚手架/快照核查。未修改任何代码、未运行任何测试。

---

## 0. 总体结论

- **plan 366 Phase 2 的 9 项修复（N1/N2/N3/N4/N5/A2'/B6'/S1/F1）全部有"能捕获回归"的聚焦测试，无一落入 P1（无法捕获任何真实 bug）。** 保护力最强的是 N1/N2/N5（分区惰性回放 + 复用 + permit 守恒，单元 + E2E 双层）与 N4/B6'/S1（typed 异常 + 负例控制断言）。
- 反模式层面，全库 3400 个 @Test 中确认低价值测试约 55 个（≈1.6%），另有约 8% 属"保护力弱的边际测试"。整体质量显著高于一般 Java 项目，与 R1–R4 连续四轮质量收敛的历史一致。
- 结构性短板集中在三处：**(a) 挂起型回归缺少超时护栏**（仅 6 个测试文件有 @Timeout，surefire `forkedProcessTimeoutInSeconds` 被注释）；**(b) 并发交错覆盖薄**（attachPendingReplay 的并发安全声明零测试，cep/rocksdb 几乎单线程）；**(c) 窗口语义两条用户可见路径零覆盖**（allowedLateness>0、迟到记录 side output）。
- 测试隔离总体良好：无端口绑定、无 @Order、无共享可变静态状态、H2 内存库逐类 UUID 命名、多 JVM 测试有系统属性门控。
- AutoTest 快照：nop-stream 下不存在 `_cases`/`.xaio` 快照目录，本项 N/A。

---

## 1. plan 366 新增测试保护力矩阵（逐修复推演）

推演方法：对每项修复，找出其核心逻辑位置，设想"把修复改回错误实现"，逐断言判定测试是否失败。

### R4-N1 惰性回放（pendingReplay）——保护力：强

- 测试：`nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/TestResultPartitionPendingReplay.java`（5 测试）+ `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestRegionRestartInternalEdgeE2E.java#materializedEdge_replaySetLargerThanQueueCapacity_doesNotBlockRestart`（1500 条 > 容量 1024）
- 生产面：`ResultPartition.java:91-118`（attachPendingReplay/pollPendingReplay）、`:368-414`（read 两个重载都先查 pending）、`:514+`（drainBufferedElements 排空 pending）
- 推演：
  - 若 `read(long,TimeUnit)` 去掉 pending 检查（R1 审查钉死的"漏 timeout 重载"回归）→ `finishedPartitionDeliversReplayThenEosOnBothReadOverloads` 第 90-92 行第一个断言即失败（读到队列残留 q0 而非 p1）。
  - 若 drain 不排空 pending → `captureIncludesPendingReplayAndKeepsSentinel` 断言 `assertEquals(4, captured.size())` 失败。
  - 若 attach 改回"全量灌入有界队列"（原 injectFront 形态）→ 单元测试 1 会**挂起而非失败**（见 [R5-TE-02]），但 E2E 大回放测试有 `@Timeout(60)` 兜底会失败。
  - 生产 wiring（SupervisionLoop 重建流程真实调用 attachPendingReplay）由 E2E 从任务失败驱动到 sink 输出，非单元直调——满足 Rule #23。
- 残余：四案例矩阵中 no-mat + running（生产者存活、非物化内部边）在 E2E 层无专项（`TestSupervisionLoopReconnectE2E` 覆盖的是 mat + running）；风险低（该案例回退即普通 pipeline 行为）。

### R4-N2 内部边分区复用 + MIDDLE writer 保留——保护力：强

- 测试：`TestRegionRestartInternalEdgeE2E#internalEdge_restartAfterProducerCompleted_skipsCompletedAndDrainsResidual`（:80-183）
- 推演：若 `SupervisionLoop.buildConsumerInvokableWithReplay`（:668+）改回"全新空分区"分支 → consumer 立即见 EOS、map 以 0 记录 COMPLETED → `assertEquals(2, sinkResults.size(), ...)`（:177-180）失败；若 MIDDLE writer 不保留 → 下游 0 记录 → 同一断言失败。均为确定性失败而非挂起（@Timeout(60) 双保险）。
- F1（SUCCESS-terminal 跳过）：`assertSame(sourceTaskBefore, tasks.get("ie-src-0"))`（:166-169）——若跳过逻辑被移除，重建后的 source 首写 finished 分区抛异常，作业进失败循环，assertSame 与终态断言双双失败。实例同一性断言是正确的钉法（比断言状态更强）。

### R4-N3 心跳过滤 finished 任务——保护力：强（双侧契约各钉一半）

- TaskManager 侧：`TestTaskManagerLivenessAndReporting#heartbeatSkipsFinishedTaskWhoseRegistryEntryIsRetained`（:158-192）——等真实 COMPLETED 报告后调 heartbeat，逐 batch 断言 v-done 不出现（:185-189）。若 `TaskManager.java:326` 的 `isFinished()` 过滤被删 → liveness batch 含 frozen 条目 → 失败。
- Coordinator 侧：`TestJobCoordinatorPerTaskFailure`（:184-186）断言 COMPLETED 报告清除 liveness 条目。
- 残余：复合场景"回插 + 60s 假 TASK_STALL"没有端到端计时测试（等待 60s 不可接受，两侧各自钉死已是合理折衷）——记入本节但不单独立 finding。

### R4-N4 JDBC 目录失败响亮——保护力：强

- 测试：`TestJdbcCheckpointStorage#testRestorePathFailsLoudWhenCatalogQueryFails`（:673-698）。动态 Proxy 只对 `existsTable` 抛异常、其余转发真实 H2。
- 推演：若 `JdbcCheckpointStorage.tableExists()/epochTableExists()`（:409-417/:577-585）改回 catch→false → `getLatestCheckpoint` 返回 null 而非抛 → `assertThrows` 两处均失败；detail 消息锚点（"checkpoint table existence" / "epoch ledger table existence"）与生产代码逐字对应，防错误信息静默漂移。
- 两条 restore 读路径（checkpoint 表 + epoch 账本）都覆盖。

### R4-N5 injectFront 删除 / permit 守恒——保护力：强

- 测试：`TestResultPartitionPendingReplay#replayPathHoldsNoPoolPermits`（:131-150）——4 permit 池，逐阶段断言 `getGlobalAvailableCapacity()`（写 2 条 = 2、attach 后仍 2、回放消费完仍 2、队列消费完回满 4）。
- 推演：若 pending 路径引入任何 acquire/release，第 140/145 行断言即失败。API 已整体删除（全仓 injectFront 零命中，重编译期即防护），本测试守护的是"未来实现不 reintroduce permit 记账"。

### R4-A2' persist-failure 清理 checkpointSuccessMap——保护力：强

- 测试：`TestCheckpointAbortMarkerCleanup#persistFailureDropsTerminalMarkerWhenNoCommitFailed`（:146-198）。
- 亮点：不走直调——用真实覆写 `storeCheckPoint` 抛 `CheckpointStorageException` 的 LocalFileCheckpointStorage，经 coordinator `acknowledgeTask` 走完整完成路径触发 inline persist 失败（:180-188 注释明确说明"直接 acknowledge pending 会绕过 coordinator 完成路径"）。
- 推演：若 `CheckpointCoordinator.java:869-878` 的 fail 路径清理被删 → `checkpointSuccessMap` 残留 → :192-194 `assertFalse(containsKey)` 失败。同文件 `abortKeepsMarkerWhileCommitRetriesPending`（:90-112）钉住反向不变式（重试期 marker 必须存活），两测试合起来把清理时序完全钉死——这是清理类修复测试的范本。

### R4-B6' directoryPath 保留字符校验——保护力：强

- 测试：`TestFileSourceAuditFixes#enumeratorStateSerializerRejectsNewlineInDirectoryPathButAllowsPipe`（:244-268）。
- 正例（\n、\r\n 必须 IOException 且消息含 "line-separator"）+ **负例控制**（'|' 合法必须 `assertDoesNotThrow`）。若校验被删 → 正例失败；若校验被扩大到 '|' → 负例失败。与 splitById 的"刻意更窄集合"契约（`FileSource.java:182-184` 注释）双向钉死。

### S1 CloseSupport 扁平 suppressed 树——保护力：强

- 测试：`TestCloseSupport#accumulateFlattensCloseAllSuppressionChain`（:28-42）——`firstError.suppressed=[X,Y]` 顺序 + assertSame 实例；`accumulateNullCases`（:57-65）含自累积 no-op。
- 推演：若改回 plan-01 嵌套形状 → `assertEquals(2, firstError.getSuppressed().length)` 失败。形状契约（`CloseSupport.java:81-91` javadoc）与断言一致。

### 汇总裁定

| 修复 | 测试 | 改回错误实现后 | 裁定 |
|---|---|---|---|
| N1 | PendingReplay 5 测试 + E2E 大回放 | 单元挂起（无超时）/ E2E 60s 失败 | 强（带 TE-02 残余） |
| N2 | InternalEdgeE2E 测试 1 | sink 计数断言确定性失败 | 强 |
| F1 | 同上 assertSame | 失败循环 + assertSame 失败 | 强 |
| N3 | Liveness 测试 + PerTaskFailure | liveness batch 断言失败 | 强 |
| N4 | JdbcStorage N4 测试 | assertThrows 双路失败 | 强 |
| N5 | permit 守恒测试 | 容量断言失败 | 强 |
| A2' | AbortMarkerCleanup A2' 测试 | containsKey 断言失败 | 强 |
| B6' | AuditFixes B6' 测试 | 正例/负例双向失败 | 强 |
| S1 | TestCloseSupport | suppressed 长度断言失败 | 强 |

**结论：plan 366 无 P1 级（无法捕获回归）新增测试。** 发现集中在同文件内的弱测试与结构性护栏缺失（下节）。

---

## 2. 发现清单

### [R5-TE-01] 永真断言：liveness 冒烟测试的断言恒为真

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/taskmanager/TestTaskManagerLivenessAndReporting.java:96-122`
- 证据片段（:113-121）：
  ```java
  TestAwait.elapsed("task thread enter run loop window", 150);
  taskManager.heartbeat();
  // Either: still running → 1 liveness entry, or: already completed → 0 entries
  // Both are valid; the key invariant is no NPE and no negative count.
  assertTrue(coordinatorRpc.livenessBatches.size() >= 0);
  ```
- 严重程度：P2（命中 P-5；`List.size() >= 0` 恒真）
- 现状：方法名声称"HeartbeatReportsLivenessWhenInvokableInstalled"，实际对 liveness 报告内容零断言——任务已完成的竞态下 0 条、运行中 1 条都被接受，断言本身永不失败。同文件其余测试（含 N3 回归）质量很高，此测试是文件内洼地。
- 风险：若 heartbeat 的 liveness 装配（`reportNodeTaskLiveness` 调用点、invokable 非空判断）整体脱落，此测试仍绿；只能靠 `idleSinkTaskHeartbeatReportsFreshAliveness`（该测试有真实断言）部分兜底，但"运行中数据任务上报进度"这一主路径无强断言。
- 建议：要么改为确定性双测（阻塞型 invokable 保证 heartbeat 时任务 running → 断言恰 1 条且 vertexId 正确；完成型 invokable → 断言 0 条），要么删除并把"无 NPE"合并进相邻测试。
- 信心水平：高（断言文本即证据）。
- 误报排除：`TestAwait.elapsed` 只是固定等待，不构成行为断言；注释自认"Both are valid"，非笔误。

### [R5-TE-02] N1 回归形态是"挂起"，但单元测试无 @Timeout 护栏

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/TestResultPartitionPendingReplay.java:56-73`（及全类）
- 证据片段：
  ```java
  @Test
  void replayLargerThanQueueCapacityDrainsWithoutBlocking() throws Exception {
      ResultPartition partition = new ResultPartition(128);
      partition.write(new StreamRecord<>("q0"));
      partition.attachPendingReplay(records("p", 2000));
  ```
  类上无 `@Timeout`。对照根 pom `/Users/abc/app/nop-entropy-wt/nop-entropy-master/pom.xml:184-187`：
  ```xml
  <!--                    <forkCount>4</forkCount>-->
  <!--                    <reuseForks>true</reuseForks>-->
  <!--                    <forkedProcessExitTimeoutInSeconds>60</forkedProcessExitTimeoutInSeconds>-->
  ```
  （`forkedProcessTimeoutInSeconds` 未配置。）
- 严重程度：P2
- 现状：N1 的原始缺陷形态是监督线程永久阻塞。若该形态在未来重构中回归（如 attach 又变成同步灌队列），本单元测试在元素 129 处永久阻塞——它自己不会失败，会挂住 fork。E2E 大回放测试（runtime 模块）有 `@Timeout(60)` 会失败，但同轮构建中 core 的挂起 fork 会先拖死构建（无 fork 级超时兜底）。
- 风险：CI 挂死而非红；定位成本高（表现为构建超时被外部 kill，无 surefire 报告）。
- 建议：给 `TestResultPartitionPendingReplay` 类级 `@Timeout(30)`（JUnit 5.9+ 类级超时线程化执行）；同时恢复根 pom `forkedProcessTimeoutInSeconds`（如 900s）作为全库护栏。这与 [R5-TE-15] 是同一主题的两层修复。
- 信心水平：高。
- 误报排除：E2E 测试已有 @Timeout(60)（`TestRegionRestartInternalEdgeE2E.java:79/:193/:280`），本条仅指 core 单元测试与构建层护栏缺失。

### [R5-TE-03] CEP qualifier 家族 8 个测试只断言 assertNotNull，qualifier 语义零验证

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/TestCepPatternBuilderModel.java`（testTimesQualifier / testTimesOrMoreQualifier / testConsecutiveQualifier / testAllowCombinationsQualifier / testGreedyQualifier / testOptionalQualifier / testSubtypeQualifier / testFollowKindNotFollowedBy）
- 证据片段（testTimesQualifier 全文）：
  ```java
  void testTimesQualifier() {
      CepPatternModel model = new CepPatternModel();
      model.setStart("start");
      CepPatternSingleModel step = new CepPatternSingleModel();
      step.setName("start");
      step.setTimes(io.nop.api.core.beans.IntRangeBean.build(2, 4));
      model.addPart(step);
      CepPatternBuilder builder = new CepPatternBuilder();
      Pattern<?, ?> pattern = builder.buildFromModel(model);
      assertNotNull(pattern);
  }
  ```
- 严重程度：P2（命中 P-5 + P-3）
- 现状：8 个测试各构造一种 qualifier 模型后仅断言 pattern 非空。若 builder 把 `times(2,4)` 静默降级为单次、把 `greedy` 丢成 `optional`，全部测试仍绿——qualifier 到 NFA 状态机的翻译语义完全未被此家族验证（真正的语义验证在 `TestAfterMatchSkipStrategies`/`TestNFA`，但它们不从 model 层驱动）。
- 风险：model→builder 翻译层回归（XDSL 声明的 qualifier 失效）无 model 层守护；此类 bug 用户可见（模式匹配结果错）且无异常。
- 建议：每个 qualifier 测试补最少行为断言（如 times(2,4) 用 1/2/3 个事件喂 NFA 断言 0/1/1 匹配；greedy vs optional 用重复事件区分匹配数），或断言生成的 Pattern/State 图谱关键属性。
- 信心水平：高。
- 误报排除：`assertNotNull` 在此并非"构造可能返回 null 的防御"——`buildFromModel` 对非法模型抛异常而非返回 null（异常路径由 TestCepPatternBuilderTypeCheck 覆盖），故断言确无区分力。

### [R5-TE-04] EmbeddedDistributedExecutor 构造器测试退化为"new 出来不为 null"

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestEmbeddedDistributedExecutor.java`
- 证据片段：
  ```java
  void testDefaultConstructorUsesDefaultNodeCount() {
      EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(new TestMessageService());
      assertNotNull(executor);
  }
  ```
  （testCustomNodeCountConstructor / testCustomTimeoutConstructor 同形态，方法名声称验证默认 nodeCount/timeout，实际无一断言参数生效。）
- 严重程度：P2（命中 P-5 + P-6：名字声称参数语义，正文只是实例化冒烟）
- 现状：三个测试合起来只证明构造器不抛异常；`nodeCount`/`timeout` 是否被保存、是否被后续行为读取，零断言。
- 风险：构造参数静默被忽略（如 config 传 4 实际恒用默认 1）不可捕获——这正是名字所声称要防的 bug。
- 建议：断言可观测状态（getter 或首个依赖该参数的行为），或将三测试合并为一个"构造不抛"冒烟并如实命名。
- 信心水平：高。
- 误报排除：已确认类中无其他断言方法（脚本扫描 assertNotNull-only 命中）。

### [R5-TE-05] B1 接线测试只证"方法存在"，不证"真的被调用"；配套功能测试是模拟构造，同样兜不住

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/source/TestSourceEnumeratorManifestWiring.java:147-184`；关联 `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-connector/src/test/java/io/nop/stream/connector/file/TestFileSourceCheckpointRestore.java:100-119`
- 证据片段（wiring 测试，7 个 assertNotNull、0 个行为断言）：
  ```java
  void registerSourceApiVerticesMethodIsWiredIntoCreateCoordinator() throws Exception {
      // B1 Anti-Hollow fix: verify that createCoordinator(...) exists
      // AND calls registerSourceApiVertices(coordinator, jobGraph). ...
      Method createMethod = Class.forName("io.nop.stream.runtime.execution.GraphModelCheckpointExecutor")
              .getDeclaredMethod("createCoordinator", ...);
      assertNotNull(createMethod, "createCoordinator(jobId, ..., JobGraph) must exist (B1 fix)");
      ...
      java.lang.reflect.Field f = CheckpointCoordinator.class.getDeclaredField("registeredSourceVertexIds");
  ```
  关联功能测试注释与正文：
  ```java
  // Construct the manifest-section entry that CheckpointCoordinator writes.
  io.nop.stream.core.SourceEnumeratorSnapshot manifestEntry =
          new io.nop.stream.core.SourceEnumeratorSnapshot(ser.getVersion(), bytes);
  ```
- 严重程度：P2（命中 P-4/P-6；测试名"IsWiredInto"宣称的是调用关系，反射只能证存在性）
- 现状：B1 缺陷本体是"createCoordinator 没有调用 registerSourceApiVertices → manifest source 节恒空 → enumerator 状态丢失"。若有人删掉那一行调用：本测试 7 个断言全绿；而注释中援引的"functional end-to-end coverage"（TestFileSourceCheckpointRestore）实际是**自行构造 manifest 条目做序列化往返**（:116-119），并不驱动真实 source-API 顶点过 coordinator 落 manifest，同样抓不到调用删除。
- 风险：B1 类回归（ enumerator 状态恢复丢失）当前无任何一层测试可捕获——这是本次审计发现的保护力空洞中最接近 P1 的一条（定为 P2：当前代码正确，仅守护缺失）。
- 建议：补一个真驱动测试：JobGraph 含 source-API 顶点 → buildEpochManifest → 断言 manifest 含非空 source-enumator 节（或对 createCoordinator 用可观测副作用的 coordinator 桩断言 registerSourceEnumeratorVertex 被调用）。
- 信心水平：高（两处证据均为现场读取）。
- 误报排除：TestFileSourceE2E 走数据面但不检 manifest 节；未发现其他真实驱动 manifest 写入断言的测试。

### [R5-TE-06] 纯 getter/setter 往返测试三处

- 文件与证据：
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointConfig.java:46-66`：
    ```java
    void testSettersAndGetters() {
        config.setCheckpointEnabled(false);
        assertFalse(config.isCheckpointEnabled());
        config.setCheckpointInterval(30000L);
        assertEquals(30000L, config.getCheckpointInterval());
        ...
    ```
  - 同文件 `:210` `testMaxRestartsPerRegionSetter`（setter 值回读；负数拒绝已有独立测试 :227，故本测试无增量）
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestFingerprintAndTerminationMode.java` `testCoordinatorFingerprintGetterSetter`
- 严重程度：P2（命中 P-1）
- 现状：三处均为"set 什么 get 什么"，无校验逻辑参与（校验逻辑已有独立测试：testMaxRestartsPerRegionRejectsNegative、testValidateUnalignedConfigRejectsThresholdNotBelowTimeout）。按技能文件判定标准（"把核心逻辑改成错误的，测试应该失败"——这些 setter 若被删，编译即失败）无独立验证意义。
- 风险：极低；主要成本是维护噪音与虚假覆盖感。
- 建议：删除或并入 builder/默认值测试作附带检查（同文件 testBuilder 已覆盖同字段集）。
- 信心水平：高。
- 误报排除：CheckpointConfig 的 setter 无副作用（无事件、无派生字段）；有副作用的 setter 不在本清单。

### [R5-TE-07] toString/hashCode/equals 镜像家族（16 处，集中于 core）

- 文件（代表）：
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/jobgraph/TestJobGraph.java:242-249`
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointBarrier.java:86-95`
  - `windowing/triggers/TestContinuousProcessingTimeTrigger.java:260-265`、`TestPurgingTrigger.java:169`、`TestContinuousEventTimeTrigger.java:349`、`TestDeltaTrigger.java:151`、`windowing/assigners/TestTumblingEventTimeWindows.java:90`、`TestSlidingProcessingTimeWindows.java:125` 等（全库 `void testToString|testHashCode|testEquals` 名共 16 处）
- 证据片段（TestCheckpointBarrier）：
  ```java
  void testToString() {
      CheckpointBarrier barrier = new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT);
      String str = barrier.toString();
      assertTrue(str.contains("id=1"));
      assertTrue(str.contains("timestamp=1000"));
  ```
- 严重程度：P3（命中 P-2：测试的是常量/格式镜像，改实现格式必须同步改测试）
- 现状：这些测试确实断言了具体内容（比 assertNotNull 强），但验证的是"toString 含我 toString 写的字段"——与实现同语反复。唯一真实价值是防止 toString 变空串/抛异常。
- 风险：低；格式有意演进时产生虚假红。
- 建议：保留一类合并版"诊断输出契约"测试（每个包一个，断言非空 + 含类名即可），删除逐字段镜像断言。
- 信心水平：高。
- 误报排除：若某 toString 参与日志检索契约（有文档承诺字段名），逐字段断言才升级为合理——未发现此类文档。

### [R5-TE-08] 测试名宣称 FAILED 上报路径，实际测的是 cancel 不产生假 FAILED

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/taskmanager/TestTaskManagerLivenessAndReporting.java:268-293`
- 证据片段：
  ```java
  void runningTaskFinallyReportsFailedStatusOnException() throws Exception {
      ...
      // Install an invokable that throws on invoke() — we cannot easily construct
      // one without a real operator chain. Instead, install null and observe the
      // 30s timeout path is too long for unit test. Instead simulate failure by
      // sending a cancel — canceled tasks do NOT report ...
      taskManager.cancelTask("job-1", "v-fail", 0, token);
  ```
- 严重程度：P3（命中 P-6 + P-3：名字与行为不符）
- 现状：invokable 抛异常 → finally 上报 FAILED 的路径在本测试中完全未执行（作者自注"cannot easily construct"），实际断言的是 cancel 后 150ms 内无假 FAILED（这本身是有价值的负向不变式）。真正的 FAILED 覆盖在 `TestJobCoordinatorPerTaskFailure`（注释已指路）。
- 风险：读者按名字索引 FAILED 路径覆盖会被误导；TM 侧"异常 → FAILED 报告"链路（RunningTask.run catch 分支）实际无 TaskManager 级测试。
- 建议：改名为 `cancelTaskProducesNoSpuriousFailedReport`；FAILED 链路用抛异常的 OperatorChain（如内嵌 failing MapFunction，参照 TestRegionRestartInternalEdgeE2E 的注入法）补真测。
- 信心水平：高。
- 误报排除：同文件 :283-285 `TestAwait.staysTrue(...)` 是真断言，本条只针对命名与覆盖错位。

### [R5-TE-09] 窗口语义两条用户可见路径零覆盖：allowedLateness>0 与迟到记录 side output

- 文件：生产面 `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:672-674, :1274-1276`；测试面证据为全库 grep：
  ```java
  if (isSkippedElement && isElementLate(element)) {
      if (lateDataOutputTag != null) {
          output.collect(lateDataOutputTag, element);   // ← 无任何测试驱动
  ```
  ```java
  protected boolean isElementLate(StreamRecord<IN> element) {
      return element.getTimestamp() ... && (element.getTimestamp() + allowedLateness
  ```
  全库测试对 `lateDataOutputTag` 的引用全部是构造参数透传且实参恒 null（TestWindowOperatorAccType/TestPaneInfoAndAccumulationMode/TestTimerCheckpointRestoreE2E 等）；`allowedLateness` 实参全部为 0。唯一的迟到测试 `TestWindowOperatorLateRecordsDroppedMetric` 注释自述走的是 "No late-data output tag: late records are dropped"。
- 严重程度：P2（命中 P-3）
- 现状：`allowedLateness>0` 时"窗口触发后、清理前到达的记录应被接受/再聚合"的语义，以及迟到记录路由到 side output 的语义，均无任何行为测试。叠加 plan 366 Phase 3 已登记 `PatternStreamBuilder.withLateDataOutputTag` 成为零引用孤儿 API——这组特性处于"实现存在、入口孤儿、测试为零"的三重无守护状态。
- 风险：用户配置 allowedLateness 或 late output tag 后行为静默错误（丢记录或双计）；回归不可见。
- 建议：两条测试：(1) allowedLateness=N 时窗口 fire 后、watermark < maxTimestamp+N 的记录进入窗口；(2) 配置 lateDataOutputTag 后迟到记录出现在 side output 且计数正确。同时随孤儿 API 裁定一并处理。
- 信心水平：高。
- 误报排除：`TestSideOutputChainingE2E`/`TestWindowOperatorIntegration` 的 side output 是普通 transform side output，非迟到路径；cep 侧 TestCepOperatorLateRecordsDroppedMetric 只测计数不测 side output。

### [R5-TE-10] NFA 超时 × after-match skip 策略组合无交叉测试

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/aftermatch/TestAfterMatchSkipStrategies.java`（无任何 timeout 相关内容，grep "timeout" 零命中）；`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestNFAWindowTimeout.java`（无 skipStrategy 引用）；`TestCepSkipStrategyE2E.java` 同样零 timeout 交叉。
- 证据片段（TestAfterMatchSkipStrategies 方法清单，:145-195）：
  ```java
  void noSkipProducesAllOverlappingMatches() throws Exception ...
  void skipPastLastEventProducesSingleMatch() throws Exception ...
  void skipToNextProducesSameStartExcludedMatches() throws Exception ...
  void strategiesProduceDifferentMatchCountsOnSameInput() throws Exception ...
  void testSkipToMissingElementThrowsTypedExceptionWithPatternName() ...
  ```
- 严重程度：P2（命中 P-3）
- 现状：skip 策略在 happy path 上四策略对比 + typed 异常覆盖良好；窗口超时单独覆盖良好；但"部分匹配在 window 边界超时时，SKIP_PAST_LAST/SKIP_TO_NEXT 如何剪枝 shared buffer / 超时匹配是否仍按策略去重"这一组合域（Flink 曾经的 bug 高发区）没有测试。
- 风险：超时清理与策略剪枝的交互回归（超时匹配重复投递或泄漏）不可捕获。
- 建议：在 TestCepSkipStrategyE2E 增加一档：within + 事件流在窗口边界截断，断言四种策略各自超时输出集合。
- 信心水平：中高（基于 grep 交叉为零；不排除既有 E2E 间接覆盖，但未发现）。
- 误报排除：TestGreedy/TestNotPattern 覆盖策略与 NOT 组合，不含时间窗口维度。

### [R5-TE-11] 并发交错覆盖薄：attachPendingReplay 并发安全声明零测试；cep/rocksdb 几乎无多线程测试

- 文件：生产面 `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:85-118`；测试面：全库仅 `TestResultPartitionPendingReplay.java` 一个文件引用 `attachPendingReplay`，且全部单线程顺序调用。
- 证据片段（生产 javadoc 声明的并发契约）：
  ```java
   * Attaches replay elements to be delivered ahead of all queue content.
   * O(1), never blocks, takes ownership of the collection. May be called
   * before the consumer starts (region restart rebuild) or concurrently with
   * reads (channel-state restore).
  ```
  与 pollPendingReplay 的竞态论证（:98-105 "Deliberately does NOT null out the field ... a concurrent attach ... could swap in a fresh queue between our poll and the null-out"）——该竞态决策无测试。
  模块多线程测试文件数：core 31、runtime 36、**cep 1、rocksdb 0**。
- 严重程度：P2
- 现状：N1 修复的并发论证（consumer 读期间 restore 线程 attach 新 pending 队列，字段不得清空）是典型的"注释即规范"，没有任何 stress 测试或交错测试守护；若未来有人"优化"掉该设计（null out + 丢失并发 attach），全库测试仍绿。cep（NFA/SharedBuffer 的并发恢复）与 rocksdb 后端完全单线程测试。
- 风险：低概率高危害——channel-state restore 期间挂回放是 unaligned checkpoint 恢复的真实路径；并发类回归一旦发生将以生产环境偶发丢数据形式出现。
- 建议：补一个双线程 stress 测试：线程 A 循环 read，线程 B 周期性 attachPendingReplay 不同序列，断言总交付集合 = 各 attach 集合并集且无丢失（al-low-dup 语义下做计数下界断言）。
- 信心水平：中高（并发测试无法穷举，但当前为零是事实）。
- 误报排除：E2E failover 测试有跨线程（监督线程/任务线程），但 attach 与 read 的并发窗未被任何测试同时驱动。

### [R5-TE-12] Coordinator 侧 RPC stub 录制语义出现家族内漂移（IStreamTaskRpcService 收敛后的新点）

- 文件：
  - `TestTaskManagerLivenessAndReporting.java:352-379` `CapturingCoordinatorRpc`：`livenessBatches.add(new ArrayList<>(progress))`（保留 batch 边界）
  - `TestPlan358LifecycleHardening.java:230-246` `RecordingRpc`：`liveness.addAll(progress)`（拍平）
  - `TestTaskManager.java:1174-1194` `MockCoordinatorRpcService`：`livenessReports.addAll(progress)`（拍平）
- 证据片段（对照）：
  ```java
  // CapturingCoordinatorRpc —— batch 边界保留
  public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
      livenessBatches.add(new ArrayList<>(progress));
  }
  // RecordingRpc —— 拍平
  public void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress) {
      liveness.addAll(progress);
  }
  ```
- 严重程度：P3（结构/漂移隐患）
- 现状：plan 366 已把 IStreamTaskRpcService 家族收敛到 `CoordinatorTestSupport`（21 个消费者），但 coordinator 方向（IStreamCoordinatorRpcService）的 stub 仍在 3+ 个测试类各自内联，且对同一方法有两种录制语义。N3 类测试依赖 batch 边界（逐 batch 断言 noneMatch），拍平版无法表达该断言——语义差异真实存在而非风格差异。
- 风险：新测试随手复制拍平版后写 batch 级断言会得到错误通过/失败；跨类修同一接口方法签名时需改 4 处。
- 建议：把 IStreamCoordinatorRpcService 录制桩也收进 CoordinatorTestSupport（提供 batch 视图 + 拍平视图两个 getter），或至少统一录制语义。
- 信心水平：高。
- 误报排除：`TestStreamControlRpc.java` 是 RPC 布线测试（双端实现），不属于录制桩家族，已排除。

### [R5-TE-13] TestAwait 五模块逐字节复制（已知 deferred，实测当前零漂移）

- 文件：`nop-stream-{core,runtime,connector,connector-batch,connector-debezium}/src/test/java/**/testsupport/TestAwait.java`（5 份）
- 证据：`diff nop-stream-core/.../TestAwait.java nop-stream-runtime/.../TestAwait.java` 输出仅首行 package 声明，`DIFF-EXIT=0`（逐字节相同）。
- 严重程度：P3
- 现状：plan 366 Deferred 节已登记"跨模块测试脚手架合并需 test-jar 拓扑变更"。本轮实测五份当前一致，无 mock 语义漂移；风险随时间累积（一处修 bug 其余四处不跟）。
- 建议：维持 deferred 归属不变；在 test-infra successor 落地时一次性收敛。无需本轮动作。
- 信心水平：高。
- 误报排除：已确认 diff 无内容差异，非"部分漂移"。

### [R5-TE-14] `>= 0` 型永真断言残留（多为相邻强断言的冗余，个别独立成测）

- 文件与证据：
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/rocksdb/incremental/TestRocksDBIncrementalBackendWiring.java:84` `assertTrue(result.getSstFileCount() >= 0);`（相邻有 marker key/目录存在真断言）
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/integration/TestDataStreamPipeline.java:103` `assertTrue(result.getExecutionTime() >= 0);`（相邻有 results 内容强断言）
  - `TestCheckpointHistory.java:96`、`TestWatermarkIntervalNodeLevel.java:130`（其前一行 `assertEquals(0L, env.getWatermarkInterval(), ...)` 已是真断言）、`TestAsyncSnapshotPipeline.java:336`（其前 `assertEquals(0, observedNegative.get())` 才是本体）
  - `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/state/shard/TestStateShardBoundary.java:36/:51`（`shardId >= 0` + `< 8` 双断言，构成区间校验，判定为有效，列出仅为完整性）
- 严重程度：P3（命中 P-5，边缘形态）
- 现状：绝大多数 `>= 0` 出现在真断言之后作为冗余收尾，无独立危害；真正独立成测的永真断言只有 [R5-TE-01] 一处。
- 风险：低；噪音。
- 建议：顺手删除冗余行即可，不单独立项。
- 信心水平：高。
- 误报排除：TestStateShardBoundary 的 `>= 0` 与 `< 8` 组合是完整区间断言，不计入。

### [R5-TE-15] 全库仅 6 个测试文件有 @Timeout + surefire 无 fork 级超时：挂起型回归的系统性护栏缺失

- 文件：根 `/Users/abc/app/nop-entropy-wt/nop-entropy-master/pom.xml:173-200`（surefire 配置：forkCount=4 / reuseForks=true / parallel=classes / threadCount=1；`forkedProcessTimeoutInSeconds` 未配置）；全库 grep `@Timeout` 仅命中 6 文件（TestFanOutBoundedE2E、TestMailboxExecutor、TestTaskMailbox、TestRegionRestartInternalEdgeE2E、fraud-example 2 文件）。
- 证据片段：见 [R5-TE-02] 引用的 pom 注释块。
- 严重程度：P2
- 现状：nop-stream 是重并发系统（监督循环、心跳、mailbox、2PC），R2-R4 轮已三次修复"永久悬挂"级缺陷（N1/N2、C1 EOS 失败关链顺序等）。该缺陷族的历史回归形态全部是 hang。618 个测试文件中 612 个对 hang 零防护，构建层也无兜底——一次挂起回归的代价是整个 CI 通道阻塞至外部超时。
- 风险：高影响低概率；发生时排查成本大（无 surefire 报告、fork 被 kill）。
- 建议：(1) 根 pom 配 `forkedProcessTimeoutInSeconds`（900s 量级）；(2) 对已知 hang 敏感面（execution/transport/taskmanager 家族含阻塞 read 的测试）加类级 @Timeout。不必全库撒 @Timeout（会引入误报）。
- 信心水平：高。
- 误报排除：`parallel=classes` + `threadCount=1` 意味着单 JVM 内类串行，一个挂起测试确实会独占该 fork；forkCount=4 只能并行挂起 4 个类后整体停滞。

### [R5-TE-16] 教科书式枚举存在性测试（技能文件反例的实例）

- 文件：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/common/state/backend/memory/TestMemoryOperatorStateBackend.java:27-33`
- 证据片段：
  ```java
  @Test
  void testRedistributionModeEnumExists() {
      assertNotNull(RedistributionMode.NONE);
      assertNotNull(RedistributionMode.UNION);
      assertNotNull(RedistributionMode.BROADCAST);
      assertNotNull(RedistributionMode.SPLIT_DISTRIBUTE);
  }
  ```
- 严重程度：P2（命中 P-1——与 `ai-dev/skills/unit-test-antipatterns.md` 第 36-42 行反例逐字同型）
- 现状：枚举成员编译期即存在，断言永不失败；RedistributionMode 若有业务方法（fallback/解析）应有行为测试——未发现。
- 风险：无真实风险；纯维护噪音与虚假覆盖。
- 建议：删除；若 enum 有 fromValue/解析逻辑改为测那个。
- 信心水平：高。
- 误报排除：全库仅此一处 `*EnumExists` 命名（已全库 grep），无家族性问题。

---

## 3. 覆盖缺口综合评估（任务项 3）

| 缺口域 | 现状 | 结论 |
|---|---|---|
| connector 故障注入 | JDBC 2PC sink deep 覆盖 abort/恢复重提交/幂等/混合 durable（TestJdbcTwoPhaseCommitSinkDeep :186-426，含 corrupt blob）；file sink 有 kill-recover exactly-once；batch 有 TestBatchConsumerSinkFunctionFailure；pre-submit probe 有专测 | **覆盖良好**，无明显缺口 |
| checkpoint 恢复半途失败 | half-written manifest（TestLocalFileCheckpointStorage:235）、corrupt JDBC blob → checksum mismatch（TestJdbcCheckpointStorage:302-343）、fingerprint mismatch、serde restore guards（TestMemoryStateSerdeRestoreGuards） | **覆盖良好** |
| NFA 超时/跳过策略组合 | 各自覆盖好，**组合为零** | 缺口 → [R5-TE-10] |
| 并发交错 | core/runtime 有 31/36 个多线程文件（含 barrier 一致性、pending 检查点竞态、leader 选举竞态）；但 pendingReplay 并发 attach 为零；cep 1 个、rocksdb 0 个 | 缺口 → [R5-TE-11] |
| 窗口语义边界（乱序/迟到/水位对齐） | 迟到丢弃计数（delta=1.0 断言）、watermark 多输入合并、session 合并、overflow 边界（TestWindowOverflow）覆盖好；**allowedLateness>0 与迟到 side output 为零** | 缺口 → [R5-TE-09] |

## 4. 测试隔离评估（任务项 4）

- 静态共享状态：未发现测试类间共享的可变静态字段（grep 命中均为 static helper 方法）。无 @Order/@TestMethodOrder/MethodOrderer 使用。**通过**。
- 端口：nop-stream 主模块测试零端口绑定（grep `new ServerSocket` 零命中）；多 JVM 场景测试由 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")` 门控（TestParallel2PcMultiJvmE2E:79 等），默认构建不抢端口。**通过**。
- 临时目录：全面使用 `@TempDir`（TestCheckpointAbortMarkerCleanup、TestFileSourceAuditFixes 等）；connector-jdbc H2 内存库逐类 UUID 命名（`"jdbc:h2:mem:" + getClass().getSimpleName() + StringHelper.generateUUID()`，7 处）——在 forkCount=4 / parallel=classes 并行模型下安全。源码字符串中出现的 `"/tmp/..."` 均为序列化元数据值（FileSplit 路径、storage config 示例），无真实文件 IO。**通过**。
- 并行模型：`parallel=classes, threadCount=1, reuseForks=true`——类间跨 JVM 并行、JVM 内串行；CoreInitialization 仅 @BeforeAll 幂等初始化、无 reinitialize。**模型与现存资源用法匹配**；唯一结构性暴露是缺 hang 护栏（[R5-TE-15]）。
- AutoTest 快照（任务项 6）：`find nop-stream -name "_cases" -o -name "*.xaio"` 零命中——nop-stream 不使用 AutoTest 快照体系，一致性检查 N/A。

## 5. 脚手架与 stub 语义漂移评估（任务项 5）

- IStreamTaskRpcService 家族：`CoordinatorTestSupport.RecordingTaskRpcService` 单点收敛，21 个消费者——收敛达成，未见新漂移。
- **新漂移点在 coordinator 方向**：3 个内联 IStreamCoordinatorRpcService stub 录制语义不一致（batch 保留 vs 拍平）→ [R5-TE-12]。
- TestAwait 五副本字节相同（[R5-TE-13]，deferred 归属维持）。
- windowing 家族第 5 份复制已由 plan 366 Phase 3 抽 builder（WindowingTestSupport 存在且被 29 个 runtime windowing 测试使用）。
- stub 与真实实现一致性抽查：`CapturingCoordinatorRpc`/`RecordingTaskRpcService` 均为纯录制、无分支逻辑，不存在"mock 比真实宽松"的漂移面；`IdleInputGate`（TestTaskManagerLivenessAndReporting:250-265）覆写 read/isAllFinished 属有意的测试替身且注释说明，判定合理。

## 6. 统计：有效 vs 低价值测试（任务项 7）

规模（@Test 注解计数，脚本统计）：

| 模块 | @Test | assertThrows | assertNotNull | 多线程测试文件 | 低价值估计 |
|---|---|---|---|---|---|
| nop-stream-core | 1560 | 312 | 443 | 31 | ~18（1.2%） |
| nop-stream-runtime | 1062 | 227 | 763 | 36 | ~15（1.4%） |
| nop-stream-cep | 345 | 74 | 159 | 1 | ~17（5%） |
| nop-stream-rocksdb | 123 | 24 | 18 | 0 | ~1（<1%） |
| nop-stream-flow | 105 | 36 | 32 | — | ~1（1%） |
| nop-stream-connector | 72 | 27 | 17 | — | ~1（1%） |
| nop-stream-connector-jdbc | 43 | 14 | 12 | — | ~1（2%） |
| nop-stream-connector-batch | 49 | 27 | 2 | — | ~0 |
| nop-stream-connector-debezium | 40 | 15 | 12 | — | ~0 |
| 合计 | **3399** | 756 | 1440 | — | **~55（≈1.6%）** |

- **有效测试（核心逻辑改错会失败）：≈ 90%**（含 plan 366/plan 01/plan 358 等历轮聚焦回归，质量普遍为"断言具体值/具体状态"级别）。
- **边际测试（保护力弱但有存在价值：冒烟、wiring 存在性、诊断格式钉）：≈ 8%**（CEP qualifier 家族、builder assertNotNull 家族、toString 家族、存在性 gate 等）。
- **低价值测试（无法捕获任何真实 bug）：≈ 1.6%（约 55 个）**，构成：assertNotNull-only/实例化冒烟 ~30、toString/hashCode/equals 镜像 ~16、纯 getter/setter 往返 ~3、独立永真断言 1、枚举存在性 1、其余零散 ~4。
- assertThrows/@Test 全库比率 22%——错误路径测试基数健康；模块内最低的是 connector-jdbc（14/43=33% 实际不低）与 cep（74/345=21%），最高是 connector-batch（27/49=55%）。
- 模块质量排序（本维度）：connector-batch ≈ rocksdb ≈ flow > connector ≈ connector-jdbc > core ≈ runtime > **cep（assertNotNull-only 密度最高，见 [R5-TE-03]/[R5-TE-04] 同型集中）**。

## 7. 发现分布汇总

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 11 | R5-TE-01、02、03、04、05、06、09、10、11、15、16 |
| P3 | 5 | R5-TE-07、08、12、13、14 |
| 合计 | 16 | — |

> 严重程度口径（按任务定义）：P1=测试无法捕获任何真实 bug；P2=保护力弱但有边际价值；P3=命名/结构问题。plan 366 九项修复的回归测试全部达到"改回错误实现会失败"标准，无一入 P1。

## 8. 建议优先级

1. **高**：[R5-TE-05] 补 B1 真驱动测试（当前唯一"实现正确但无任何层可捕获回归"的空洞）。
2. **高**：[R5-TE-15]/[R5-TE-02] 恢复 surefire fork 超时 + 给 hang 敏感面加 @Timeout（一次性低风险工程）。
3. **中**：[R5-TE-09]/[R5-TE-10] 补 allowedLateness/side-output 与 timeout×skip 两条语义测试。
4. **中**：[R5-TE-11] pendingReplay 并发 attach stress 测试。
5. **低（顺手清理）**：[R5-TE-01]/[R5-TE-03]/[R5-TE-04]/[R5-TE-06]/[R5-TE-16] 重写或删除；[R5-TE-08] 改名；[R5-TE-12] stub 语义统一；[R5-TE-07]/[R5-TE-14] 冗余断言清理。
