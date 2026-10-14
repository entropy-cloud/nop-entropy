# C10 测试保护力 — nop-stream-orchestration-thin（最后批次）

- 审计单元：`nop-stream-orchestration-thin`（nop-stream/nop-stream-flow + nop-stream/nop-stream-sql + nop-stream/nop-stream-fraud-example 的 src/test）
- 仓库根：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c，与前序维度基线一致）
- 日期：2026-10-14；维度：C10 测试保护力——评估对象不是"有没有测试"，而是"回归会不会红"
- 输入：Phase 4 复核后的缺陷清单（4 条 P1：C1-001 / C3-008 / C3-009 / C6-007；抽验 P2：C3-001/C6-001/C7-001 同点、C1-003），详情读各维度文件最终保留项节
- 盲审纪律：未读取 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/；读取范围 = 本审计目录内各维度文件、被审 3 模块全部 src/test 代码
- 变异推演口径：把"缺陷本体"当作变异体（等价于"正确实现合入后被回退"），问：现有哪个测试会红？答案三态——**会红 / 假保护（同区域有测试但变异体不被触发）/ 无测试**。无测试会红者登记"缺保护力"（P2，与缺陷本体分开计数）。

---

## 1. 既有并发测试盘点（3 模块测试清单 × 保护范围）

三模块 src/test 合计 **131 个 java 文件、约 599 个 @Test 方法**（逐文件清点）。按保护对象分层：

### 1.1 nop-stream-flow（66 文件，~340 @Test）——构建期为主，并发保护面 ≈ 0

| 测试簇 | 代表类（@Test 数） | 保护范围 |
|---|---|---|
| DSL 校验 / fail-fast | TestStreamConfValidator(16)、TestStreamModelLinter(11)、TestStreamModelDslBuilderFailFast(7)、TestStreamFlowAuditFixes(13)、TestStreamErrorCodeContract(4) 等 | 构建期 typed 错误、并行度守卫声明面（TestKeyedStageParallelismGuard(3)、TestEquiJoinParallelismInvariant(3)、TestSinkReachabilityGuard(2)）——全部单线程、build 期 |
| 窗口 / 触发器 / 水位声明 | TestTriggerDeclaration(11)、TestWindowAssignerBranches(5)、TestWatermarkIntervalNodeLevel(3)、TestStreamCheckpointAndWindowContract(12) 等 | checkpoint 六字段映射、窗口属性 fail-fast——构建期契约 |
| xpl 包装器 | TestXplFunctionWrappers(6) | XplMap/Filter/FlatMap/Sink/Source 包装器调用契约；**含 XplSourceFunction 的 ctx.isCancelled() 轮询取消模式（body 级、单线程）** |
| LOCAL E2E | TestAdvancedPipelineE2E(1)、TestUnionPipelineE2E(1)、TestSelfUnionPipelineE2E(1)、TestStreamModelDslBuilderE2E(3)、TestWi17SyncReplacementPatternE2E(3) | parallelism=1 的 env.execute() 全链路；Wi17SyncExternalCallFunction 用 Thread.sleep 模拟慢外部调用（单线程 E2E） |
| 并行度布线 | TestPerTransformParallelismWiring(4) | JobGraph 顶点并行度物化（**plan 侧，不执行**） |

**本模块没有任何多线程测试**（grep `Thread|ConcurrentHashMap|CountDownLatch|Atomic` 于 src/test：仅 TestXplFunctionWrappers 的 AtomicBoolean 单线程取消桩）。运行期共享可变状态（XplFunctionSupport.ROOT_SCOPE、Xpl*Function.bindings）全部无并发测试。

### 1.2 nop-stream-sql（32 文件，~155 @Test）——编译/求值单线程面，UDF 注册表零并发测试

| 测试簇 | 代表类（@Test 数） | 保护范围 |
|---|---|---|
| SQL 编译 | TestStreamSqlCompiler(39)、TestSqlTransformFragmentCompile(8)、TestSqlEmitMode(8) 等 | 编译产物文本/结构 |
| 查询 E2E | TestTumbleAggregateQueryE2E、TestSqlHopWindowE2E、TestSqlSessionWindowE2E、TestSqlLatenessE2E、TestJoinQueryE2E、TestUdfE2E 等（各 1） | parallelism=1 的 LOCAL 管线；**TestUdfE2E 是 UDF 经编译→求值全链路的唯一 E2E（单线程）** |
| 求值器 | TestStreamSqlExprCompiler(21)、TestStreamSqlAggregations(23)、TestWi9AggregateCatalog(8)、TestStreamAggregatorFunctionResolver(11) | 表达式语义、聚合器、聚合 resolver |
| UDF SPI | **TestStreamScalarFunctionResolver(9)** | register→eval、null 传播、unknown fail-fast（namesList 文案）、静态 call() 入口存在性、xpl 发射形态、close 幂等——**全部单线程（JUnit 主线程 register 后同线程 eval）** |

静态共享面（StreamScalarFunctionResolver.functions/defaultResolver、SqlRowAggregateOps.CACHE、RecordColumnAccess.GETTER_CACHE）**无任何并发测试**；CHM 缓存亦无并发首触测试（该两点现为安全实现，缺测试不立案，仅登记观测）。

### 1.3 nop-stream-fraud-example（33 文件，~104 @Test）——本单元并发保护力的实际承载者

**非 gated（正常 CI 执行）：**

| 测试类（@Test 数） | 并发/恢复相关保护范围 |
|---|---|
| **TestDirectoryFileSourceFunction(8)** | ①全量顺序发射、无终止符尾行；②**cursor 检查点→恢复跳过已消费前缀**（cancel-via-collect-hook 中断 + snapshot + **恢复到全新实例**）；③**空 map restore 不抹兄弟已恢复游标**（restore-vs-restore 守卫）；④目录缺失 fail-fast；⑤REPLAYABLE 声明；⑥坏游标状态 fail-fast；⑦**runEntered 双读者守卫**（双线程 + 线程状态确定性门）；⑧（L100 一次对未 run 实例的 cancel 死调用） |
| **TestReplayableCdcSourceFunction(5)** | ①有界完成语义（全量放完即 EOS）；②**offset 检查点→恢复**（runner 线程 + cancel 中断 + 生产 checkpoint 路径快照 + **恢复到全新实例**，竞态容忍式前缀断言）；③fresh start 不继承 stale registry offsets；④未命名 connector fail-fast；⑤REPLAYABLE 声明 |
| TestPerTransformParallelismE2E(1) | **P=4 map/sink 的共享无状态 bean**：CountDownLatch(2) 强制两子任务重叠 + 精确一次 multiset 断言 + 分布断言（ThreadingMap 为无状态 MapFunction，不触 RuntimeContext/keyed state） |
| TestXplSourceCancelE2E(1) | 经真实 runtime 取消面（checkpoint 超时 abort）的 xpl source 协作取消；@Timeout(120) 预算 |
| TestS1CdcPipelineE2E(3) / TestS1CdcRecoveryE2E(1) | S1 全链路精确告警行；**优雅两段式 checkpoint→restore**（bob 的 run-2 告警依赖 run-1 恢复的 keyed history——restore 敏感断言）；P=1（fraud-s1-cdc.stream.xml:21） |
| TestS2FileAggregationE2E(3) / TestS2RecoveryAndRescaleE2E(2) / TestS2OfflineReshardE2E(1) | S2 精确聚合 + exactly-once 目录契约（无 .tmp 残留）；**优雅两段式恢复（memory + RocksDB 双后端，W1 跨恢复边界）**；离线 maxParallelism reshard 后恢复；P=1（fraud-s2-file.stream.xml:13）、每次 run 新建 model/env/实例 |
| TestParallel2PcFileE2E(1) / TestParallel2PcJdbcE2E(1) | 2PC sink 并行形态 exactly-once（keyed-P>1 + 2PC）；目录输入 2 个 part 文件 |
| TestChaosKillPlanner(5) / TestExerciseSampler(8) / TestExerciseLoadGenerator(8) / TestExerciseParams(3) / TestSteppedThrottleSinks(7) / TestS1PatternDeclarations(5) / pattern 4 类(~25) / TestFraudAuditFixes(4) / TestDistributedScenarioSerialization(6) | 混沌计划纯函数、采样器/负载生成器/参数、throttle sink、S1 声明、4 个 pattern 函数（**直接喂 DemoKeyedStateStore，不经 UserHistoryEnricher**）、跨 JVM 序列化形状（含 500ms 轮询等 commit） |

**Gated（`-Dnop.stream.test.multi-jvm.enabled=true`，正常 CI 不执行）：** TestS1MultiJvmE2E(2)、TestS2MultiJvmE2E(2)、TestParallel2PcMultiJvmE2E(1)、TestScenarioBackpressureMultiJvmE2E(2)、**TestStabilityExerciseMultiJvm(8)**（TM kill/restart、SIGSTOP 分区、leader kill、soak 健康 + 队列深度泄漏启发式）、TestS2RestoreRescaleMultiJvmE2E(1)。

**并发相关测试面合计（非 gated）：约 10 个测试类 / 25 个用例**；其中真正多线程的仅 3 个（runEnteredGuard 双读者、CDC runner 线程中断、PerTransformParallelism 锁存器重叠）。

### 1.4 关键结构性观察（盘点结论）

1. **所有恢复/重跑类测试都落在"全新实例"上**：cursorCheckpointResumeSkipsConsumedPrefix（fraud 测试 L153 新建 `resumed`）、offsetCheckpointAndResumeSkipConsumedPrefix（L148 新建 `second`）、S1/S2 两段式 E2E（每 run 重 parse 模型重建 env）。**没有任何测试在 cancel/终止后重用同一 source 实例**——而本单元 4 条 P1 中有 3 条（C6-007、C1-003、C3-008）的触发前提恰是"跨 run 实例复用/共享"。
2. **所有 E2E 的源顶点 parallelism=1**（fraud-s1-cdc.stream.xml:21、fraud-s2-file.stream.xml:13 实核）；唯一 P>1 的 TestPerTransformParallelismE2E 用**无状态** bean——C3-008（P≥2 + 重启）、C3-009（P≥2 有状态 keyed UDF）的触发前置在测试面上不存在。
3. **没有任何失败注入测试**：三模块测试对"运行期故障"的唯一注入形态是目录缺失/坏状态 fail-fast（构建/初始化期）；没有把故障注入运行中 emitter/restore 路径并断言任务 FAILED 的用例——C3-001/C6-001/C7-001（失败伪装 EOS）与 C3-008（restore-vs-run）均属此空白。
4. 多 JVM gated 套件是唯一的"重启 + 大规模 + 泄漏启发式"面，但其重启形态是**跨 JVM 进程级 kill→重启（重新序列化出新实例）**，恰好绕开"同 JVM 同实例复用"这一缺陷窗口（LOCAL `SupervisionLoop.restartRegion` Phase-3 / 嵌入式 `deployTask` 槽位替换路径无任何测试，唯一接近的 TestXplSourceCancelE2E 只取消、不重启断言）。

---

## 2. 保护力矩阵（缺陷 × 现有测试 × 变异结论）

图例：✅会红 / ⚠️假保护（同区域有测试、变异体不被触发） / ❌无测试。P2 抽验项一并列入。

| 缺陷 | 相关既有测试（文件:行） | 变异推演：缺陷态/修复回退态下谁会红 | 结论 |
|---|---|---|---|
| **C1-001**（P1）StreamScalarFunctionResolver.functions LinkedHashMap 逐记录读 × register 任意线程写 | TestStreamScalarFunctionResolver（9 用例，全部单线程：setUp L26-33 注册→同线程 eval）；TestUdfE2E（1，P=1 单线程管线）；TestStreamSqlCompiler UDF 编译面 | 单线程 register-then-read 无交错，容器线程安全性无关紧要；namesList 只在单线程 fail-fast 路径被迭代。修复回退为 LinkedHashMap → 全绿 | **❌无测试 → 缺保护力**（C10-001） |
| **C3-008**（P1）initializeState REPLACE（cursors.clear()）与运行中兄弟 run() 交错→检查点位后记录重发 | emptyRestoredMapMustNotWipeSiblingRestoredCursors（restore-vs-restore，全程不调 run）；cursorCheckpointResumeSkipsConsumedPrefix（恢复进**全新实例**）；S2 恢复 E2E（P=1、每 run 新实例、restore 先于 run）；TestS2OfflineReshardE2E（离线顺序） | 相邻面（空 map 守卫、fresh-instance resume）有钉且会红——但缺陷本体的"非空 restore 撞正在推进的 run"交错在所有测试中结构性不存在：无 P≥2、无同实例 restore-vs-run、无 region 重启。修复回退 → 全绿 | **⚠️假保护（相邻面）→ 本体缺保护力**（C10-002） |
| **C3-009**（P1）共享 UDF 跨子任务键控状态污染（RuntimeContext last-writer-wins） | ScenarioTestSupport.java:164 / DistributedScenarioSupport.java:302 注册 enricher（仅用于 S1 P=1 管线）；pattern 4 类（直接测 pattern 函数，不经 enricher）；TestPerTransformParallelismE2E（P=4 但为**无状态** MapFunction，不触 RuntimeContext） | 无任何测试以 P≥2 执行有状态 keyed process UDF；污染行为（跨键串写）从未被执行。修复回退 → 全绿 | **❌无测试 → 缺保护力**（C10-003） |
| **C6-007**（P1）cancelled 标志无复位→cancel 后复用同实例零事件假成功 EOS | TestDirectoryFileSourceFunction 8 用例：唯一 cancel 使用是 L100（对从未 run 的实例，死调用）与 L104-137（partial 实例自 cancel 后弃用，恢复用 L153 **全新**实例） | "cancel 后同实例重跑"从未被执行——8 个用例全部 fresh instance。修复（run 入口复位 cancelled）回退 → 全绿。C6 复核已预判："现有单测……未覆盖 cancel 后同实例重跑，故测试面不会拦截回归" | **⚠️假保护（fresh-instance 面有钉）→ 本体缺保护力**（C10-004） |
| **C3-001/C6-001/C7-001**（P2 同点）ReplayEngine 失败伪装 EOS | TestReplayableCdcSourceFunction 5 用例：happy path（emitsAllEventsThenCompletesBoundedRun）+ cancel 路径（offsetCheckpointAndResume…，L130 `assertNull(error.get(),"interrupted run must not throw")` 钉 cancel 不抛） | 无失败注入（readReplayIndex NFE / writeReplayIndex store 故障从未被诱发）；失败与完成同走 finally-cancel，测试只能看到全绿。注意：cancel 路径钉与修复**兼容**（修复走 pendingError 不动 cancel 语义），故修复亦不红——双向无保护。旁证：轮询下界 `consumed>=3`（L138）仅在 emitter 于前 3 事件内死亡时红，失败发生在 ≥3 事件后则绿——非保护面 | **⚠️假保护 → 缺保护力**（C10-005） |
| **C1-003**（P2）runEntered CAS 守卫非粘性（deployTask 槽位替换重叠窗口内新尝试零发射） | runEnteredGuardPreventsDoubleEmission（L213-248，双线程并行首入） | 并行首入契约**有钉且会红**（若无守卫，second 的 ctx 会收到重发→L245 红）——但钉的是"声明契约"而非缺陷窗口；deployTask 重叠（attempt-2 在 attempt-1 finally 复位前启动）无测试。**反向约束**：若修复实现为"落败者等待后**继续读取**"，本测试会红——修复语义必须保持"并行首入落败者静默" | **部分保护：契约面会红 / 缺陷本体无测试**（C10-006） |
| C6-005（P2）subscribe 无重复订阅防护 | TestReplayableCdcSourceFunction（无 double-subscribe 用例） | 修复回退 → 全绿 | ❌无测试（顺带登记） |
| C6-002（P2）onCancelTasks 裸 ArrayList | 无 appendOnCancel 测试 | 同上 | ❌无测试（顺带登记） |
| C3-007（P3）取消无 interrupt 响应点 | 无 emitter 线程终止断言（runToCompletion L92-97 不等 emitter） | 同上 | ❌无测试（顺带登记） |
| C3-003/C1-004/C2-002（P2）sideInput 冻结快照无强制 | TestWi11SideInputDeclaration(12)：钉"构建期快照经 sideInputs 绑定可达"（只读消费），无写穿透用例 | 修复（unmodifiableMap 包装）→ 现有测试全绿（兼容）；缺陷态亦全绿 | ❌无测试（顺带登记，兼容修复） |
| C9-001~004（P3 热路径） | 无 JMH 基准（C9 维度已核实 nop-benchmark-stream 对本单元类 0 命中） | 性能回归不可观测 | ❌无基准（归入 C10-007 规模面） |

**矩阵规模**：6 个推演组（4 P1 + 2 P2 抽验组）× 约 12 个相关测试类；另顺带登记 5 个 P2/P3 观测。**缺陷本体"会红"数 = 0/6 组**（唯一会红面是 C1-003 的声明契约钉，且它约束修复方向而非捕获缺陷）。

---

## 3. 缺保护登记发现条目

### [nop-stream-orchestration-thin-C10-001] C1-001 缺保护：UDF 注册表无任何并发测试，变异体（LinkedHashMap 回退）全绿

**文件**：nop-stream/nop-stream-sql/src/test/java/io/nop/stream/sql/eval/TestStreamScalarFunctionResolver.java:25-117（全部 9 用例）；缺陷本体 nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/eval/StreamScalarFunctionResolver.java:46-51,88-97

**证据片段**：

```java
@BeforeEach
public void setUp() {
    resolver = new StreamScalarFunctionResolver();
    resolver.register("reverse", ...);          // 单线程注册
    resolver.registerAsDefault();
}
@Test public void registeredFunctionEvaluates() {
    assertEquals("cba", eval("reverse(a)", rec("a", "abc")));   // 同线程读
}
```

**严重程度**：缺保护力 = P2（缺陷本体 C1-001 为 P1，分开计数）

**并发场景**：`register()` 写者（任意线程）× `call()` 逐记录读者（subtask 线程）的数据竞争；`namesList()` 迭代 × put 的 CME 变体。

**触发时序（=变异场景）**：正确实现（CHM/CopyOnWrite/生命周期守卫）合入后回退为 LinkedHashMap——现有 9 个用例 + TestUdfE2E 全部在 JUnit 主线程 register-then-eval，单线程序内 happens-before 天然成立，容器实现与并发性完全无关，**零测试变红**。缺陷当前即存活且全绿，等价证明：测试面对该缺陷无任何检出力。

**现状**：无并发测试；无 namesList 并发迭代用例；无运行期 register（default 已被消费后换绑）用例。

**风险**：C1-001 修复后回归（有人"简化"回 LinkedHashMap 或移除守卫）不可检出；owner doc 侧若按 C1-001 建议文档化生命周期，契约也无测试锚。

**建议**：新增 `TestStreamScalarFunctionResolverConcurrency`——(a) **压测 trip-wire**：写者线程循环 register 新函数，K 个读者线程循环 `StreamScalarFunctionResolver.call(已注册名/新名)`，带 latch 同时启动，跑 10^5 量级操作，断言零异常（CME/ISE/NPE）且"once visible always resolvable"；(b) `namesList()` 与 register 并发的无 CME 子用例；(c) **契约锚**：断言"default 注册后继续 register 新名，静态 call() 可解析"（若修复选择"启动期冻结 + 运行期 fail-fast"语义，则 (c) 改为断言 typed 拒绝——测试形态必须与修复选定的契约一致，二选一显式钉住）。

**失效面枚举**：`StreamScalarFunctionResolver.register/call/namesList/registeredNames/registerAsDefault/setDefault`；xpl 发射通道（SqlScalarXplPrinter 产物逐记录 call）；TestUdfE2E 同族。

**信心水平**：高（单线程事实可直接从测试代码核对）。

**误报排除**：不要求"并发测试必然复现 UB"——登记的是"无任何测试对该缺陷有检出力"这一事实；CHM 化后压测全绿即可作为回归锚，不依赖压测在缺陷态必红（数据竞争是概率性的，trip-wire 定位是提高检出概率 + 钉契约）。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：独立 grep 证实 nop-stream-sql src/test 零并发原语（仅 SqlTestSink synchronizedList fixture），9 用例单线程 register→eval 实核；建议压测形态可行——register/call/namesList/静态 call 均可从同包测试直达）

---

### [nop-stream-orchestration-thin-C10-002] C3-008 缺保护：restore-vs-run 游标清零窗口无测试，现有测试只钉 restore-vs-restore 与 fresh-instance 恢复

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestDirectoryFileSourceFunction.java:164-184（emptyRestoredMap 守卫用例）、:152-161（fresh-instance 恢复）；缺陷本体 nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:275-284（非空 REPLACE）

**证据片段**：

```java
// 现有守卫用例只覆盖 restore-vs-restore（全程无 run() 在跑）
@Test public void emptyRestoredMapMustNotWipeSiblingRestoredCursors() {
    source.initializeState(seeded);      // 非空
    source.initializeState(empty);       // 空 map 不抹
    ... assertEquals(42L, after.get("/data/part-000.txt"), ...);
}
// 缺陷本体路径（非空 restore 清掉正在推进的 run 的游标）无任何用例触达
```

**严重程度**：缺保护力 = P2（缺陷本体 C3-008 为 P1，分开计数）

**并发场景**：region 重启 Phase-3"提交一个、继续恢复下一个"流水线下，task-1 的非空 initializeState REPLACE 与 task-0 运行中的 advanceCursor 交错→已完成文件游标持续滞后→后续 checkpoint 携带滞后源位点→恢复重放已反映窗口。

**触发时序（=变异场景）**：修复（restore 仅在实例静默时 REPLACE / epoch 代次守卫 / 编排侧统一 submit）合入后回退——现有测试全部不满足触发前置：无并行 ≥2 的 source 测试、无同实例 restore-vs-run 交错、S2 恢复 E2E 每次 run 重建实例且 restore 严格先于 run、gated 多 JVM 重启跨进程序列化出新实例。**零测试变红**。

**现状**：emptyRestoredMapMustNotWipeSiblingRestoredCursors 对"空 map 守卫"有真保护（若该守卫被删，L182 断言红）——恰是 C3-008 复核确认的"守卫只防 restore-vs-restore"的那一半；缺的是另一半。

**风险**：修复回归不可检出；且该缺陷的触发前置（P≥2 + region 重启）正是当前测试矩阵的覆盖空洞（盘点观察 1/2），同族未来缺陷（如 DebeziumCdcSourceFunction/NopStreamOffsetBackingStore.setOffsets 兄弟面，C3-008 已移交）同样无测试可拦。

**建议**：新增 `TestDirectoryFileSourceFunctionRestoreVsRun`（单元级、确定性）：共享单实例；线程 A 以 lineDelayMs 步进 run() 并经 collect-hook 确认已推进越过检查点位（CountDownLatch 门）；主线程对同一实例 initializeState(检查点位旧状态)；断言——按修复选定契约二选一：(a) restore 被拒绝/延迟（`runEntered` 为 true 时不 REPLACE），或 (b) 其后 snapshotState 的游标 ≥ 已推进位（游标单调）。缺陷态下 (b) 红（快照回退），修复后绿。

**失效面枚举**：`DirectoryFileSourceFunction.initializeState/snapshotState/advanceCursor`；运行时窗口 `SupervisionLoop.restartRegion` Phase-3（nop-stream-runtime，编排侧修复时回归锚在本测试）；兄弟面 `DebeziumCdcSourceFunction`/`NopStreamOffsetBackingStore.setOffsets`（移交单元，同型测试建议随迁）。

**信心水平**：高（测试面结构可直接核对；触发前置在全部 131 个测试文件中不存在——盘点逐类过）。

**误报排除**：不把 emptyRestoredMap 用例记为"无保护"——它对自身契约有效；缺保护判定仅针对 C3-008 复核修正后的违约路径（restore-vs-run）。跨 JVM 串行化路径（runEntered/cursors 重建为新实例）不需要本测试，已在误报面剔除。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：emptyRestoredMap 用例（:164-184）实核全程无 run()——restore-vs-restore 假保护形态成立；恢复落点 :153 全新实例实核；建议测试可直接落为单元测试——注：单文件形态的断言 (b) 红窗依赖 lineDelayMs pacing，稳健红形态建议采用多文件"已完成文件"变体（该文件本 run 不再推进，回退持久化））

---

### [nop-stream-orchestration-thin-C10-003] C3-009 缺保护：有状态 keyed process UDF 在 P≥2 下零测试，唯一 P>1 E2E 用无状态 bean

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestPerTransformParallelismE2E.java:60-79（ThreadingMap：`perThreadCounts` CHM + latch，**不触 RuntimeContext/keyed state**）；:33-52（javadoc 自述 map bean "shared across subtasks"）；对照 ScenarioTestSupport.java:164（enricher 仅注册进 P=1 的 S1 管线）；缺陷本体 nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/UserHistoryEnricher.java:45-54

**证据片段**：

```java
// 全单元唯一 P>1 执行测试的共享 bean：无状态、不经 getRuntimeContext()
public static final class ThreadingMap implements MapFunction<String, String> {
    final Map<Long, Integer> perThreadCounts = new ConcurrentHashMap<>();
    final CountDownLatch twoSubtasks = new CountDownLatch(2);
    public String map(String value) { ... twoSubtasks.await(20, TimeUnit.SECONDS); return value; }
}
```

**严重程度**：缺保护力 = P2（缺陷本体 C3-009 为 P1，分开计数）

**并发场景**：parallelism ≥2 时 ProcessOperator 跨子任务共享同一 UDF 实例，各子任务 open() 无同步覆写共享 RuntimeContext→keyed 状态收敛单一 backend→跨键/跨子任务静默污染。

**触发时序（=变异场景）**：根因修复（nop-stream-core ProcessOperator 对含状态 RichFunction 按子任务深拷贝/装配每子任务 RuntimeContext）合入后回退——现有测试零红：S1/S2 E2E 均 P=1；pattern 测试直接喂 DemoKeyedStateStore 不经 enricher；TestPerTransformParallelismE2E 的 P=4 bean 无 keyed 状态，污染无从表现。**零测试变红**。

**现状**：UserHistoryEnricher 的唯一测试触达是作为 S1 P=1 管线的 bean（其告警行断言间接覆盖 P=1 正确性）；其 keyed-state 语义（count/total/lastCity）无任何直接单测，更无 P≥2 隔离测试。

**风险**：core 侧根因修复（C3-009 已移交 nop-stream-core 单元）落地后无本单元回归锚；若仅落单元侧防御修复（enricher 在 open() 缓存句柄），共享 backend 污染依旧且完全无观测。作为文档钦点样例，该空缺还会放大同模式的用户代码风险。

**建议**：新增 `TestUserHistoryEnricherParallelKeyedStateIsolation`——LOCAL env，keyBy(user) + process(有状态 KeyedProcessFunction bean) 声明 parallelism=2，HASH 边投放两组不相交用户的交错事件（保证两个子任务都有数据，可用 PerTransform 的 latch 技巧强制重叠）；断言每 key 富化结果仅反映自身事件（count == 该 key 事件数、lastCity == 该 key 末城）。**注意**：该测试在缺陷态红；单元侧防御修复后仍红（共享 backend 未消）、core 根因修复后才绿——定位为根因修复的回归锚，归属登记时注明。

**失效面枚举**：`UserHistoryEnricher`（唯一 KeyedProcessFunction UDF）；core 根因面 `ProcessOperator.copyForSubtask/open`、`AbstractUdfStreamOperator.open`（nop-stream-core，移交）；未来任何含状态 keyed-process UDF。

**信心水平**：高。

**误报排除**：TestPerTransformParallelismE2E 对"共享 bean 跨子任务执行"本身有真保护（perThreadCounts 断言）——但保护的是共享契约而非状态隔离，不构成 C3-009 保护力；不要求在本单元测 core 内部，测试对象是用户可见的状态隔离契约。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：ThreadingMap（:60-79）实核无状态、不触 getRuntimeContext/keyed state；grep 证实 UserHistoryEnricher 零直接单测（唯一触达 = ScenarioTestSupport:164 / DistributedScenarioSupport:302 注册进 P=1 管线）、无任何 P≥2 keyed 隔离测试；建议测试形态可行（LOCAL env + keyBy 声明均有既有先例））

---

### [nop-stream-orchestration-thin-C10-004] C6-007 缺保护：8 个用例全部 fresh instance，"cancel 后同实例重跑"零覆盖

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestDirectoryFileSourceFunction.java:94-161（唯一 cancel 中断用例的恢复落点是 L153 全新实例 `resumed`）、:100（L100 对未 run 实例的 cancel 为死调用）；缺陷本体 nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:82,110-130,230-233

**证据片段**：

```java
// 唯一的"cancel 中断→恢复"用例：恢复用新实例
DirectoryFileSourceFunction resumed = new DirectoryFileSourceFunction(input.toString());  // L153
TaskStateSnapshot state = new TaskStateSnapshot(null, 1L);
state.putOperatorState(CURSORS_KEY, new TreeMap<>(cursors));
resumed.initializeState(state);
resumed.run(resumedCtx);                       // 全新实例，cancelled=false 天然成立
```

**严重程度**：缺保护力 = P2（缺陷本体 C6-007 为 P1，分开计数）

**并发场景**：teardown 必经 cancel（SubtaskTask finally → StreamSourceOperator.close → sourceFunction.cancel()），区域重启经 deepCopy 共享同一实例（本类非 sharded）→重启 run 循环顶 `if (cancelled) return` 零事件 EOS 假成功。

**触发时序（=变异场景）**：修复（run() 入口 `this.cancelled = false`，与生产基类 DebeziumCdcSourceFunction.java:230-237 复位四件套对齐）合入后回退——现有 8 用例无一在 cancel 后重跑同一实例：cursorCheckpointResumeSkipsConsumedPrefix 的 partial 实例自 cancel 后仅做 snapshot 即弃用；emitsAllLinesInOrder 等全部一次 run；E2E 每段 run 新建模型与实例；gated 多 JVM 重启跨进程出新实例。**零测试变红**——这正是 C6 维度复核的原话预判（"测试面不会拦截回归"）。

**现状**：测试对 fresh-instance 的 resume 语义有真保护（resumed 必须从 L3 续读，回退游标逻辑会红）；缺陷本体（跨 run cancelled 残留）无保护。

**风险**：C6-007 修复回归不可检出；同族"代际守卫缺失"模式（C6-006 已驳回的 emitter 面、XplSourceFunction.running 不复位但 run 不读）均无测试观测，未来新的生命周期标志缺陷仍会静默通过。

**建议**：新增 `TestDirectoryFileSourceFunctionCancelThenRerun`（C6 复核建议原文的测试化）——run 至自然完成（或 collect-hook 中 cancel），随后**同一实例**再 run：按 REPLAYABLE 契约断言重发（修复选定语义二选一钉住：游标续读重发剩余 vs 全量重发）。缺陷态 0 事件→红；修复后绿。同时建议在该测试中一并断言重跑后 snapshotState 游标仍单调（防复位修复引入 cursor 回退回归）。

**失效面枚举**：`DirectoryFileSourceFunction.run/cancel`；运行时复用契约面（StreamSourceOperator.close、SupervisionLoop.restartRegion、deployTask 槽位替换）；S2 全部模型 E2E。

**信心水平**：高（8 用例逐条核对，无同实例重跑形态）。

**误报排除**：L100 的 `source.cancel()` 不构成覆盖（实例从未 run，也不复跑）；TestXplSourceCancelE2E 钉的是 xpl source 的 runtime 取消面，不触 DirectoryFileSourceFunction，且不重启断言。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：三模块测试全部 `.cancel()` 使用点独立枚举核实——:100 死调用、:115 partial 自 cancel 后弃用、TestReplayableCdcSourceFunction:128 恢复用 :148 全新实例，无任何同实例 cancel 后重跑；"C6 复核原话预判"经回溯 C6-round3.md:66 属实；可行性注记：须采"run 中 cancel"变体方能在缺陷态红——自然完成后 cancelled=false 不触发缺陷）

---

### [nop-stream-orchestration-thin-C10-005] C3-001/C6-001/C7-001（同点）缺保护：ReplayEngine 失败注入零测试，现有测试钉 happy/cancel 两态恰与修复兼容

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestReplayableCdcSourceFunction.java:99-106（happy path）、:109-162（cancel/offset resume，L130 `assertNull(error.get(),"interrupted run must not throw")`）；缺陷本体 nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:165-190

**证据片段**：

```java
runner.start();
while (firstCtx.collected.size() < 3 && runner.isAlive()) { Thread.sleep(5); }   // L125-126
first.cancel();
runner.join(3000);
assertNull(error.get(), "interrupted run must not throw");                        // L130
int consumed = firstCtx.collected.size();
assertTrue(consumed >= 3 && consumed <= specs.size(), ...);                       // L138 竞态容忍断言
```

**严重程度**：缺保护力 = P2（缺陷本体终裁 P2，同口径计数）

**并发场景**：emitter 线程 `runReplay` 的任何异常（readReplayIndex 对损坏索引 NFE / writeReplayIndex store 故障）落入 catch-all 仅 LOG，finally 与正常完成同走 cancel()→任务以干净 EOS 收尾。

**触发时序（=变异场景）**：修复（失败写 pendingError 通道、仅成功路径 cancel）合入后回退为 catch-all+cancel——现有 5 用例零红：唯一能区分失败与完成的断言面是"任务是否抛出"，而没有任何测试注入失败源（offset store 内容不可注入、索引状态自产自销、action/collect 路径的失败已被父类 lambda 覆盖为 FAILED——即终裁否证的主触发面）。L130 的 `assertNull` 钉的是 **cancel 路径不抛**（正确语义，修复不动它）→ 修复态全绿；缺陷态也全绿。双向无检出力。

**现状**：happy path 有精确钉（事件数与首尾 id）；cancel/offset-resume 有竞态容忍的精确钉（消费 ≥3 且恢复数严格 == 剩余）；失败路径无任何用例。L138 下界 `consumed>=3` 提供一个微弱的意外红利（emitter 在前 3 事件内死亡会红），但失败发生在 ≥3 事件后（终裁的残余触发面形态）不红。

**风险**：合并处置（7.1 批次裁决 C3-001/C6-001/C7-001 同点）修复后，"失败通道"语义无回归锚；S1 场景作为 exactly-once 回归锚点，其失败语义失真会继续掩盖生产 source 复制该反模式。

**建议**：新增 `TestReplayableCdcSourceFunctionFailureIsLoud`——失败注入选终裁确认的真实残余面：run 前向 offset 状态/注册表写入非数字 replay index（`Long.parseLong` 必抛 NFE），runner 线程 run()，断言 **run() 以异常收尾（FAILED 语义）而非干净返回**，且不呈现"部分事件 + 正常返回"；保留 L130 cancel 路径用例不动（修复兼容性锚）。

**失效面枚举**：`ReplayableCdcSourceFunction$ReplayEngine.runReplay` 全部 catch/finally 路径；S1 CDC 源失败语义；TestReplayableCdcSourceFunction 族。

**信心水平**：高（5 用例逐条核对；失败注入面按 C3/C6 复核终裁口径圈定）。

**误报排除**：不要求注入 collect 下游失败——该主触发已被复核否证（父类 lambda 转 pendingError），测试注入它反而钉不住缺陷；不把 L130 `assertNull` 记为"钉住缺陷"——它钉的 cancel 非抛语义是正确的且与修复兼容。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：失败注入零命中独立证实——三模块测试无任何向 offset store 写坏 replay index 或注入 emitter 故障的用例；L125-127/L130/L138 逐行实核；注入面可行——REPLAY_INDEX_KEY 包级可见且测试同包、NopStreamOffsetBackingStore.set 公开、缺陷态 run 干净返回可断言红）

---

### [nop-stream-orchestration-thin-C10-006] C1-003 缺保护（附唯一修复约束钉）：并行首入契约有测试且会红，deployTask 重叠窗口零覆盖

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestDirectoryFileSourceFunction.java:213-248（runEnteredGuardPreventsDoubleEmission）；缺陷本体 nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:111-130（守卫 finally 非粘性复位）

**证据片段**：

```java
first.start();
// 确定性信号：等 first 真正进入读取阻塞点后再启动 second（子任务互斥语义）
while (first.isAlive() && first.getState() != Thread.State.WAITING
        && first.getState() != Thread.State.TIMED_WAITING && ...deadline) { Thread.sleep(20); }
secondReader.start();  secondReader.join(3000);  first.join(3000);
assertTrue(firstCtx.get().collected.isEmpty(), "the second concurrent subtask must not re-emit the directory");
```

**严重程度**：缺保护力 = P2（缺陷本体 C1-003 为 P2，同口径）

**并发场景**：分布式恢复 deployTask 槽位替换不等待旧线程退出→attempt-2 的 run() 在 attempt-1 尚未执行 finally 复位时 CAS 落败→静默零发射完成（有界源提前 EOF）。

**触发时序（=变异场景）**：分两面。(a) **契约面（会红）**：若删除/破坏 CAS 守卫，本测试红（second 会重发目录）——这是全部矩阵中唯一真实的 trip-wire；(b) **缺陷本体（无测试）**：守卫非粘性窗口（attempt-2 落败静默）需要"attempt-1 已决定退出但 finally 未执行"的重叠注入，无任何测试构造该时序——修复回退为零红。(c) **反向约束**：若修复实现为"落败者自旋等待后**继续读取**"，本测试将红——修复语义必须保持"并行首入落败者静默"，或同步修订本测试并明示契约变更。

**现状**：并行首入契约被钉（且用了线程状态确定性门，非裸 sleep——时序质量好）；join(3000) 返回值未检查（软点，见 §4）；重叠窗口无覆盖。

**风险**：修复若按 C1-003 建议的"等待后继续"形态实现，会与既有钉冲突而在评审期才暴露；修复若只覆盖 LOCAL 路径（C1 复核已证 LOCAL restartRegion 不触发），分布式 deployTask 残余暴露面无任何回归观测。

**建议**：新增 `TestDirectoryFileSourceFunctionRestartOverlap`——需要最小测试缝（如包级可见的"读循环退出后、finally 复位前"钩子，或子类覆写 emitRemaining 末尾注入 barrier）：attempt-1 经 cancel 钩子即将退出并**阻塞在 finally 前**，attempt-2 启动 run()；断言 attempt-2 不以"零事件正常完成"收尾（等待后续读 / 等待 attempt-1 退出后续读 / typed 拒绝——按修复选定契约钉）。若不愿加缝，最低成本替代：cancel 后立即同实例重跑 100 次压测，断言零次零事件 EOS（概率性 trip-wire，作为 C10-004 测试的补充而非替代）。

**失效面枚举**：`DirectoryFileSourceFunction.runEntered` 守卫；运行时 deployTask 槽位替换面（nop-stream-core/nop-stream-runtime，移交）；与 C6-007 修复共享"跨 run 生命周期"契约面（两修复须联调测试）。

**信心水平**：高（测试结构可直读；重叠窗口不可达性由 C1 复核对运行时源码的修正结论支撑）。

**误报排除**：不把本测试记为"无保护"——它对声明契约有真保护并构成修复约束；登记的是缺陷本体（restart 窗口语义）无检出力。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：契约钉实核——L245 断言目标为 second 读者 ctx，删 CAS 守卫必红；join(3000) 返回值未检查软点属实（:243-244）；重叠时序零覆盖、触发路径与 C1 复核修正（deployTask 槽位替换）一致；低成本替代形态（cancel 后同实例重跑 ×100）可行）

---

### [nop-stream-orchestration-thin-C10-007] 大规模/边界包络缺口：单元级单文件、8KB 块边界与空目录零覆盖；规模面全部押在 gated 多 JVM 套件上

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestDirectoryFileSourceFunction.java:66-71（writeInput 恒写单个 part-000.txt）；nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:157（8KB 定长 buffer）、:132-139（discoverFiles）

**证据片段**：

```java
private Path writeInput(String... lines) throws IOException {
    Path input = tempDir.resolve("input-" + System.nanoTime());
    Files.createDirectories(input);
    Files.write(input.resolve("part-000.txt"), String.join("\n", lines).getBytes());  // 恒 1 个文件
    return input;
}
```

**严重程度**：缺保护力 = P2（规模/边界包络缺口，与单缺陷解耦登记）

**并发场景**：多文件游标快照/恢复、跨文件发射顺序、8KB 块边界上的 cursor resume——都是 C3-008 修正机制（"**已完成文件**游标持续滞后"）的直接作用面；当前测试矩阵对其无钉。

**触发时序（=变异场景）**：(a) discoverFiles 多文件**顺序**或 per-file 游标隔离回归——单元级 8 用例全部单文件，不会红；E2E（TestS2RecoveryAndRescaleE2E 3 文件、TestParallel2PcFileE2E 2 文件）有间接覆盖但其断言为集合/聚合形（groupedOutput 按 user|window 求和、assertExactlyOnceOutput 按 set），**跨文件发射顺序回归不红**；(b) 单行跨 8KB buffer 边界的 cursor 数学回归——现用例行长均个位字节，resume 断言虽精确（mid-file cursor）但碰不到块边界，不会红；(c) 空目录（零文件 → 零事件 EOS）行为未定义未测——missingDirectoryFailsFast 只覆盖目录**不存在**；空目录 EOS 正是 C6-007 假成功的形状，无契约测试强制其可观测性。

**现状**：规模/混沌面存在但全部 gated（`-Dnop.stream.test.multi-jvm.enabled=true`，6 个测试类 16 用例，正常 CI 不执行）；ExerciseSampler 的 jam/leak 启发式只在 gated soak 中运行；C9 维度已登记本单元热路径无 JMH 基准（nop-benchmark-stream 0 命中）。owner doc 未为三模块声明行数包络（C1 维度核实），测试亦无小规模参数化模拟包络边界。

**风险**：C3-008 修复若含"已完成文件游标"相关逻辑，回归不可检出；边界回归（多文件顺序/块边界）静默通过；CI 对泄漏/规模问题零观测（全部押在手工 gated 套件）。

**建议**：① 单元级补 `TestDirectoryFileSourceFunctionMultiFile`：≥3 文件、断言跨文件发射顺序（字典序或声明的 discoverFiles 顺序）+ 跨文件 checkpoint/restore（文件 A 完成 + 文件 B 中途的快照保真）；② 补 `TestDirectoryFileSourceFunctionBufferBoundary`：构造 ≥8KB 单行与恰在块边界的换行，断言 resume 不丢不重；③ 补空目录用例：断言零事件 EOS 的声明行为（打点/日志或显式契约），为 C6-007 族提供形状探针；④ 三个用例均为纯参数化小规模形态，无需 gated 运行。

**失效面枚举**：`DirectoryFileSourceFunction.discoverFiles/emitRemaining/snapshotState/initializeState` 多文件面；S2 E2E 的顺序盲区（集合断言）；gated 套件的 CI 缺席面。

**信心水平**：高（writeInput 单文件、E2E 断言形态均可直读核对）。

**误报排除**：多文件并非零覆盖（E2E 有 2-3 文件输入）——登记的是"顺序/边界语义无钉"而非"多文件未执行"；不把 E2E 集合断言记为缺陷——那是 exactly-once 目录契约的正确断言形态，只是不覆盖顺序。

**复核状态**： 复核保留（独立复核子代理 2026-10-14：writeInput 恒单文件（:66-71）、主代码 8KB buffer（:157）与 discoverFiles（:132-139）、无空目录用例全部实核；多文件仅存在于 E2E（TestS2RecoveryAndRescaleE2E 3 文件、TestParallel2PcFileE2E 2 文件、TestS2FileAggregationE2E）且断言为 Set 集合形（:122-141 实核）——顺序/块边界无钉、误报排除表述准确；三个建议用例均可直接落为参数化单元测试）

---

### [nop-stream-orchestration-thin-C10-008] 资源泄漏检测钩子缺失：emitter 线程终止、订阅注册表、cancel 回调族全部无观测（P1 级资源缺陷的建议测试形态）

**文件**：nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/TestReplayableCdcSourceFunction.java:92-97（runToCompletion 不等 emitter 线程）；泄漏启发式仅存在于 gated 面 ExerciseSampler.java:252-305 + TestStabilityExerciseMultiJvm.java:977

**证据片段**：

```java
private static List<ChangeEvent> runToCompletion(ReplayableCdcSourceFunction source) throws Exception {
    CollectingContext ctx = new CollectingContext();
    source.initializeState(null);
    source.run(ctx);            // 返回即断言；emitter "fraud-s1-replay-cdc" 是否退出无人观测
    return ctx.collected;
}
```

**严重程度**：缺保护力 = P2（测试基建缺口；关联 P1 资源缺陷 = C6-007，其行为测试形态已在 C10-004 单列）

**并发场景**：daemon emitter 线程的幽灵窗口（C3-007）、重复订阅第二 emitter（C6-005）、onCancelTasks 注册表竞态（C6-002/C6-004 的关联面）——三模块 CI 测试无线程存活/计数、无 GC/WeakReference、无注册表规模断言；唯一泄漏检测（队列深度单调增长启发式）在 gated 多 JVM soak 内。

**触发时序（=变异场景）**：上述任一 P2/P3 修复回退——零测试变红（矩阵顺带登记行）。对 P1 级资源缺陷（本单元即 C6-007，生命周期/半成功形态而非经典内存泄漏），行为测试形态见 C10-004；本条登记资源面的基建缺空。

**现状**：runToCompletion 语义上容忍 emitter 在 finishLingerMs 内滞留（协作式取消声明，合理），但"最终必须退出"无界也无断言——若未来修复把 park 上界调大或引入非 daemon 线程，无测试感知。

**风险**：C3-007/C6-002/C6-005 修复后无回归锚；线程治理回归（忘记 daemon、park 无界化）静默通过；泄漏问题只能在 gated soak 中被发现（CI 不可见）。

**建议**：在 TestReplayableCdcSourceFunction 增加一个廉价钩子用例 `emitterThreadTerminatesAfterBoundedRun`：runToCompletion 后按线程名查找 `fraud-s1-replay-cdc`，deadline（如 5s，> emitDelayMs+finishLingerMs 上界）内断言终止——保护 C3-007 修复（interrupt 引入后的及时退出）与线程治理基本面；若 C6-005 修复选定 typed 拒绝语义，补 double-subscribe 断言用例钉契约。

**失效面枚举**：`ReplayableCdcSourceFunction$ReplayEngine.subscribe/runReplay`、`ReplaySubscription`；gated 套件外的全部 CI 资源观测面。

**信心水平**：中高（"无任何线程终止/泄漏断言"经 grep 全测试目录核实；建议形态的 deadline 取值需按 fixture 参数校准）。

**误报排除**：daemon 线程滞留本身非缺陷（C3-007 已按 P3 定界）；本条不要求 CI 检测瞬时幽灵窗口，只要求"最终退出"有界可断言；ExerciseSampler 启发式不算缺失（它存在但 gated——缺口是 CI 可见性，不是完全没有）。

**复核状态**： 复核保留（附引用勘误）（独立复核子代理 2026-10-14：runToCompletion（:92-97）不观测 emitter、全测试目录无线程名/存活/泄漏断言（grep `fraud-s1-replay-cdc|appendOnCancel|.subscribe(` 零命中）核实；泄漏启发式仅在 gated（ExerciseSampler + TestStabilityExerciseMultiJvm:977 `assertFalse(queueDepthUnboundedGrowth(...))`）核实。勘误：并发场景中"C6-002/C6-004 的关联面"应为"C6-002/C3-004"——C6-004 为 GETTER_CACHE，与 onCancelTasks 注册表无关，该面在异步链维度的登记为 C3-004；勘误不触及立案事实）

---

## 4. 时序敏感/资源泄漏/大规模测试审查

### 4.1 时序敏感测试（Thread.sleep 同步 / 异步断言 / 竞争自吞）

| # | 位置 | 形态 | 裁定 |
|---|---|---|---|
| 1 | TestReplayableCdcSourceFunction.java:125-127 | `while (collected.size()<3 && runner.isAlive()) Thread.sleep(5)` 轮询 + cancel + **断言加宽容忍 cancel 竞态**（L131-139 带注释的合法竞态论证：consumed∈[3,size]） | **校准良好，非 false-pass**：保留了下界 ≥3 与恢复数精确相等两处有牙断言；竞态容忍有显式文档。附带观察：下界仅在 emitter 前 3 事件内死亡时提供意外保护（见 C10-005） |
| 2 | TestDirectoryFileSourceFunction.java:236-242 | 线程状态确定性门（WAITING/TIMED_WAITING，60s deadline）替代裸 sleep 启动第二读者 | **良好实践**；软点：`first.join(3000)`/`secondReader.join(3000)` 返回值未检查，首线程挂死时测试仍会以 second 空断言通过（目标契约仍有牙，但"first 正常完成"未被钉） |
| 3 | TestPerTransformParallelismE2E.java:76 | `twoSubtasks.await(20, SECONDS)` 返回值忽略 | **慢失败非假通过**：单子任务退化时 20s 停滞后 perThreadCounts 断言必红；代价是回归反馈慢 |
| 4 | TestDistributedScenarioSerialization.java:341-347、TestS2MultiJvmE2E.java:170-176 | deadline（60s）+ 轮询 Thread.sleep(500) + **循环后 assert** | 正确形态（deadline 到期必断言，非 sleep-and-assume） |
| 5 | TestExerciseSampler.java:99,199、SteppedThrottleWindowAssigner/ThrottledScenarioSinks 的 sleep | fixture/限流步进（确定性或限速语义） | 非同步依赖，不构成时序敏感风险 |

**命中统计**：检视 sleep/轮询同步点 5 处（另 2 处在 gated 套件内同形态）；**未等待异步结果就断言的命中 = 0**；**测试自吞竞争（superficially-pass）= 0**（两处竞态均以"加宽断言 + 文档"而非 catch 吞掉的方式处理）；软点 2 处（#2 join 未检查、#3 await 返回值忽略，均有后续兜底断言，不立案仅登记）。

### 4.2 资源泄漏测试

- CI 可见（非 gated）的泄漏/资源断言：**0**。无线程存活/计数断言、无 GC/WeakReference 断言、无注册表规模断言、无 stream/连接关闭断言（2PC 的 `assertNoTempResidue`（TestS2FileAggregationE2E:191-195）是唯一资源残留类断言，值得肯定——但它只覆盖 sink 临时文件面）。
- gated 面存在队列深度 jam/leak 启发式（ExerciseSampler:252-305，含"增长须与 epoch 停滞耦合"的防误报设计）——设计良好但 CI 不可见（C10-008）。
- P1 级资源缺陷（C6-007）的建议测试形态：行为级 cancel-then-rerun（C10-004），资源级 emitter 终止断言（C10-008）。

### 4.3 大规模测试

- CI 用例规模：S2 E2E ~25 行输入、并行度 E2E 40 元素、CDC fixture ≤10 事件、目录输入 1-3 文件——全部小 fixture，无参数化放大。
- 规模面（ExerciseLoadGenerator 事件泵、soak/chaos 轮次、throttle 梯度）全部 gated 多 JVM；`nop-benchmark-stream` 存在但对本单元热路径类 0 覆盖（C9 维度核实）。
- 包络边界的小规模模拟缺口：多文件顺序、8KB 块边界、空目录（C10-007 三条建议）。

---

## 5. 统计与覆盖声明

### 统计

| 项 | 数值 |
|---|---|
| 盘点测试面 | 3 模块 131 个测试 java 文件 / ~599 @Test；并发相关测试面 ~10 类 / ~25 用例（非 gated），真多线程用例 3 个 |
| 保护力矩阵 | 6 个推演组（4 P1 + 2 P2 抽验组）× ~12 相关测试类；缺陷本体"会红" = **0/6 组** |
| 缺保护登记条目 | **8 条**（C10-001~008，均为 P2 缺保护力，与缺陷本体分开计数） |
| 4 条 P1 变异结论 | C1-001 无测试；C3-008 假保护（相邻面有钉、本体无测试）；C3-009 无测试；C6-007 假保护（fresh-instance 面有钉、本体无测试） |
| 时序敏感命中 | sleep/轮询点检视 5+2 处；false-pass 命中 0；软点登记 2（join/await 返回值未检查，有兜底断言） |
| 资源泄漏钩子 | CI 可见 0（唯一残留断言 = 2PC .tmp；唯一泄漏启发式 gated）；建议测试形态 2 类（行为级 C10-004 / 观测级 C10-008） |

### 核心结论

1. **本单元测试矩阵对 4 条 P1 并发缺陷的本体检出力为零**。结构性根因有三：(a) 恢复/重跑测试全部落在全新实例上，"同实例跨 run 复用"——C6-007/C1-003/C3-008 的共同触发前提——无任何测试构造；(b) 全部 E2E 源顶点 parallelism=1，唯一 P>1 测试用无状态 bean——C3-008/C3-009 的前置不存在；(c) 零失败注入——C3-001/C6-001/C7-001 的失败通道无观测。现有的相邻面钉（fresh-instance resume、空 map 守卫、并行首入 CAS、cancel 不抛）都是真保护，但保护的是声明契约的相邻半边。
2. **唯一的反向 trip-wire**：runEnteredGuardPreventsDoubleEmission 钉死"并行首入落败者静默"，对 C1-003/C6-007 修复的语义选型构成硬约束（修复不得使落败者继续读取），修复评审时须显式核对该测试的兼容性。
3. 建议的 8 个测试（C10-001~008 各建议节）均为单元级/参数化小规模形态，不需要 gated 运行即可入 CI；其中 C10-004（cancel 后同实例重跑）与 C10-002（restore-vs-run）是 P1 修复收口的最小回归面，C6 复核已分别点名要求。

### 覆盖声明

- 三模块 src/test 全部 131 文件清单化（@Test 计数逐类核对）；对缺陷相关测试全文精读 8 个（TestStreamScalarFunctionResolver、TestDirectoryFileSourceFunction、TestReplayableCdcSourceFunction、TestPerTransformParallelismE2E、TestXplSourceCancelE2E、TestS1CdcRecoveryE2E、TestS2RecoveryAndRescaleE2E、TestXplFunctionWrappers），其余经 @Test 清单 + 定向 grep（Thread.sleep/CountDownLatch/await/parallelism/leak/cancel/Disabled|EnabledIf）覆盖。
- 变异推演的输入（4 P1 + 2 P2 组）全部取自 Phase 4 复核后的最终保留项（C1-shared-mutable-state.md、C3-async-chains.md、C3-round2.md、C6-resource-lifecycle.md、C6-round3.md、C2-C9-spot-checks.md），触发时序以各维度复核修正后的机制为准（C1-003 = deployTask 槽位替换；C3-008 = 检查点位后重发的修正机制；C6-007 = 纯顺序陈旧）。
- 未执行任何测试（静态审计）；测试"当前全绿"的断言基于缺陷均为"未修复"登记与测试形态推演，未跑 CI 验证——如需实证可按 §3 各建议节先补测试（缺陷态应红）再修缺陷（转绿），即可同时完成修复验证与回归锚落地。
- cra-index.md 现状对照（仅引用编号）：本单元各维度复核均登记"新发现、未修复"，cra-index 无本单元既有条目（据 C1/C6/C2-C9 复核节），故不存在"已知未修复"项与本次缺保护登记的重复计数问题。
- 盲审纪律遵守：未读取 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：C10 维度独立复核子代理（与初审盲审者不同角色；职责为逐条验证与三态判定，不补充新发现；本节与最终保留项为 append-only 追加，不改前文）。
- 复核日期：2026-10-14；仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c，与初审基线一致）。
- 复核输入：本文件全部 8 条缺保护登记 + §1 盘点 / §2 保护力矩阵 / §4 时序审查 / §5 统计；各维度文件最终保留项节（C1-shared-mutable-state.md、C3-async-chains.md、C6-resource-lifecycle.md、C2-C9-spot-checks.md）+ C6-round3.md（C10-004 引文回溯）。
- 复核方法：C10-001/002/004/005 四条按零命中/假保护口径独立重做——全文精读 TestDirectoryFileSourceFunction、TestReplayableCdcSourceFunction、TestStreamScalarFunctionResolver、TestPerTransformParallelismE2E，并独立 grep 三模块 src/test（并发原语、`.cancel()` 全部使用点、失败注入形态、Thread.sleep/await 轮询点、泄漏/线程终止断言、UserHistoryEnricher 触达）；其余四条经同类定向 grep + 主代码锚点核对（DirectoryFileSourceFunction、ReplayableCdcSourceFunction、StreamScalarFunctionResolver、UserHistoryEnricher、DebeziumCdcSourceFunction、NopStreamOffsetBackingStore、fraud-s1-cdc/fraud-s2-file.stream.xml）；8 条引用的缺陷编号与判级逐一比对各维度最终保留项；未读取 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/。

### 逐条判定表

| 登记编号 | 判定 | 一句话理由 | 现状 |
|---|---|---|---|
| C10-001 | **保留** | 独立 grep 证实 nop-stream-sql src/test 零并发原语（唯一命中 = SqlTestSink 的 synchronizedList fixture），TestStreamScalarFunctionResolver 9 用例全部 JUnit 主线程 register→eval（setUp :26-33 实核），TestUdfE2E 单 @Test 无 Thread/parallelism，namesList 仅单线程用例触达——"LinkedHashMap 回退全绿"推演成立 | 缺保护成立；本体 C1-001（P1）新发现、未修复，现状继承 |
| C10-002 | **保留** | emptyRestoredMapMustNotWipeSiblingRestoredCursors（:164-184）实核全程仅 initializeState×2 + snapshot、无任何 run() 在跑——确为 restore-vs-restore 假保护形态；恢复落点 :153 为全新实例；全测试目录无同实例 restore-vs-run 交错 | 缺保护成立；本体 C3-008（P1，复核修正机制）新发现、未修复，现状继承 |
| C10-003 | **保留** | ThreadingMap（:60-79）实核为无状态 MapFunction（CHM 计数 + latch，不触 getRuntimeContext/keyed state）；UserHistoryEnricher 零直接单测（唯一触达 = ScenarioTestSupport:164 / DistributedScenarioSupport:302 注册进 P=1 管线），pattern 测试直喂 DemoKeyedStateStore——P≥2 keyed 隔离零覆盖成立 | 缺保护成立；本体 C3-009（P1）新发现、未修复，现状继承 |
| C10-004 | **保留** | 三模块测试全部 `.cancel()` 使用点独立枚举：仅 TestDirectoryFileSourceFunction:100（未 run 实例死调用）、:115（partial 匿名子类 collect-hook 自 cancel 后弃用）、TestReplayableCdcSourceFunction:128（cancel 后恢复用 :148 全新实例）——无任何 cancel 后同实例重跑；"C6 复核原话预判"经回溯 C6-round3.md:66 属实 | 缺保护成立；本体 C6-007（P1）新发现、未修复，现状继承 |
| C10-005 | **保留** | 失败注入零命中独立证实（三模块测试无任何向 offset store 写坏 replay index / 注入 emitter 故障的用例，parseLong 命中均为合法输入解析）；L125-127 轮询、L130 assertNull（cancel 不抛）、L138 竞态容忍断言逐行核实，与修复（pendingError 通道）兼容——"双向无检出力"成立 | 缺保护成立；本体 C3-001/C6-001/C7-001（终裁均 P2，7.1 并案）新发现、未修复，现状继承 |
| C10-006 | **保留** | runEnteredGuardPreventsDoubleEmission（:213-248）实核：线程状态确定性门 + L245 断言目标为 second 读者 ctx——删守卫必红（契约钉成立）；join(3000) 返回值未检查软点属实（:243-244）；"attempt-1 已决退出、finally 未复位"重叠时序无任何测试构造，与 C1 复核修正路径（deployTask 槽位替换）一致 | 缺保护成立（附唯一修复约束钉）；本体 C1-003（P2）新发现、未修复，现状继承 |
| C10-007 | **保留** | writeInput（:66-71）恒写单个 part-000.txt、主代码 8KB buffer（:157）与 discoverFiles（:132-139）、无空目录用例（missingDirectoryFailsFast 仅覆盖目录不存在）全部实核；多文件仅存在于 E2E（TestS2RecoveryAndRescaleE2E 3 文件、TestParallel2PcFileE2E 2 文件、TestS2FileAggregationE2E）且断言为 Set 集合形（:122-141 实核）——顺序/块边界无钉、误报排除表述准确 | 缺保护成立（规模/边界包络缺口独立立案）；关联缺陷本体（C3-008 等）新发现、未修复 |
| C10-008 | **保留（附引用勘误）** | runToCompletion（:92-97）返回即断言、全测试目录无线程名/存活/泄漏断言（grep `fraud-s1-replay-cdc\|appendOnCancel\|.subscribe(` 于测试零命中；两处 isAlive 均为已检视轮询门）核实；泄漏启发式仅在 gated（ExerciseSampler 队列深度启发式 + TestStabilityExerciseMultiJvm:977 `assertFalse(queueDepthUnboundedGrowth(...))`）核实。勘误：并发场景中"C6-002/C6-004 的关联面"应为"C6-002/C3-004"——C6-004 为 GETTER_CACHE，与 onCancelTasks 注册表无关（该面在异步链维度的登记为 C3-004）；勘误不触及立案事实 | 缺保护成立（测试基建缺口）；关联缺陷本体（C3-007/C6-002/C6-005）新发现、未修复 |

**复核统计**：保留 8 / 降级 0 / 驳回 0（其中 1 条附编辑性引用勘误：C10-008，见上表及下节勘误 4）。

### 清洁声明/矩阵行抽查结果

- **§2 矩阵 6 推演组全部独立复核成立，"缺陷本体会红 = 0/6"结论维持**：C1-001/C3-009 无测试（grep 证实）、C3-008/C6-007/C3-001 组的假保护形态逐一实核（相邻面钉真实存在且与本体窗口结构性分离）；唯一 trip-wire（C1-003 契约钉）的断言方向（second ctx 空，L245）与"约束修复语义"的反向约束推演均与测试代码及 C1 复核修正（deployTask 路径）一致。
- **顺带登记 5 行核实成立**：C6-005（无 double-subscribe 用例）、C6-002（无 appendOnCancel 用例）、C3-007（runToCompletion 不等 emitter、无线程终止断言）均零命中证实；C3-003/C1-004/C2-002 行——TestWi11SideInputDeclaration 实测 12 @Test、全部构建期声明校验、无写穿透用例；C9 行——C2-C9 抽查文件"基准缺口登记（C9）"节在案（nop-benchmark 仓扫无 stream 求值基准）。
- **§4.1 时序表 5 行全部成立、0 false-pass 维持**：行 1（L125-127 轮询 + 竞态容忍带注释断言）、行 2（线程状态确定性门 + join 软点）、行 3（await 忽略但 perThreadCounts 断言兜底，"慢失败非假通过"分析核实）、行 4（TestDistributedScenarioSerialization:343 与 TestS2MultiJvmE2E:174-179 deadline + 循环后 throw）、行 5（TestExerciseSampler:99/199 deadline 轮询 + 循环后 assert、SteppedThrottleWindowAssigner:52 fixture）逐点实核；软点 2 处（join 返回值未检查、await 返回值忽略）如实登记、不立案恰当。
- **§1.3 gated 口径核实**：6 类 16 用例（2+2+1+2+8+1）精确吻合；"并发相关测试面 ~10 类/25 用例（非 gated）、真多线程 3 个"量级与实核一致。

### 统计与表述勘误（不影响任何立案）

1. **§1/§5 "131 个测试 java 文件"与实测不符**：三模块 src/test 合计 **149 个 java 文件**（flow 66 / sql 38 / fraud 45），其中含 @Test 的文件 **118 个**（53/34/31）——初审的 per-module 口径不一致（flow=66 为全部 java 文件数，sql/fraud 两个数与两种口径均不合）；**@Test 计数 599 精确吻合**、gated 16 用例精确吻合。该偏差不动摇任何登记（全部证据锚定具体文件行号）。
2. **§1.1 括注枚举不全**：flow src/test 的 `Thread|ConcurrentHashMap|CountDownLatch|Atomic` 命中不止 TestXplFunctionWrappers——Wi17SyncExternalCallFunction 有单线程 ExecutorService 辅助线程（"wi17-external-client"）+ CHM/AtomicInteger，另有 3 个测试类用 AtomicInteger 计数器；但"无多线程并发断言测试"实质成立（辅助线程为串行单线程池、无跨线程交错断言）。
3. **§4.1 "另 2 处在 gated 套件内同形态"计数偏差**：gated 测试类内 Thread.sleep 仅 1 处（TestS2MultiJvmE2E:174），另一处应指 StabilityExerciseSupport 的 soak pacing（fixture 类）——同形态结论不变。
4. **C10-008 引用勘误**："C6-002/C6-004"应为"C6-002/C3-004"（详见逐条判定表）。
5. **§5 覆盖声明"C3-008 = 检查点位后重发的修正机制"措辞偏松**：C3 复核修正后的机制是"后续 checkpoint 携带滞后源位点→恢复重放重叠计入"（非即时重发）；C10-002 登记正文已按修正机制表述，此概述行以正文与 C3 复核节为准。

## 最终保留项

（保留 8 / 降级 0 / 驳回 0。现状统一继承各维度终裁：8 条对应的缺陷本体均为"新发现，未修复"，缺保护登记随本体维持 open。）

| 编号 | 对应缺陷（终判级） | 建议测试形态（一句话） |
|---|---|---|
| C10-001 | C1-001（P1） | resolver 并发压测 trip-wire：写者循环 register × K 读者 call（latch 同起、10^5 量级零异常）+ namesList 并发无 CME 子用例 + default 换绑契约锚 |
| C10-002 | C3-008（P1） | 同实例 restore-vs-run：线程 A lineDelayMs 步进 run 越过检查点位，主线程对同一实例 initializeState 旧状态，断言快照游标单调（多文件"已完成文件"变体为稳健红形态） |
| C10-003 | C3-009（P1） | LOCAL env P=2 keyBy + 有状态 KeyedProcessFunction 隔离测试：HASH 边投放两组不相交用户交错事件，断言每 key 富化仅反映自身事件（core 根因修复回归锚，缺陷态红） |
| C10-004 | C6-007（P1） | cancel 后同实例重跑：run 中经 collect-hook cancel（自然完成变体不触发缺陷），同实例再 run 断言重发 + 重跑后快照游标单调 |
| C10-005 | C3-001/C6-001/C7-001（P2 并案） | 失败注入：向 offset store 写非数字 replay index 后 run，断言 run 以异常收尾（FAILED 语义）而非干净 EOS；保留 L130 cancel 不抛锚 |
| C10-006 | C1-003（P2） | 重叠窗口注入：attempt-1 阻塞于 finally 复位前 + attempt-2 run 断言非零事件 EOS；低成本替代 = cancel 后立即同实例重跑 ×100 压测断言零次零事件 EOS |
| C10-007 | 规模/边界包络缺口（解耦登记） | MultiFile（≥3 文件跨文件顺序 + 跨文件 checkpoint 保真）/ BufferBoundary（≥8KB 单行块边界 resume 不丢不重）/ 空目录 EOS 契约探针，纯参数化入 CI |
| C10-008 | 测试基建缺口（关联 C3-007 P3 / C6-002 P2 / C6-005 P2） | emitter 线程有界终止断言（按名查 fraud-s1-replay-cdc，deadline 内终止）+ C6-005 修复定型后补 double-subscribe 契约用例 |
