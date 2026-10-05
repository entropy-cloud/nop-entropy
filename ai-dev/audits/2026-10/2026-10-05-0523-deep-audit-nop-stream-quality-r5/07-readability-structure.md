# 07 可读性/结构/命名/死代码深度审计 — nop-stream（R5 轮）

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`（2026-10-05 00:07:55 +0800）
- **审计维度**: 手册维度 02（模块职责与文件边界）+ 17（代码风格）+ 19（命名一致性）
- **审计对象**: nop-stream 全部子模块 `src/main`（约 11.7 万行 / 748 个非生成 Java 文件）：nop-stream-core（408 文件 / 48570 行）、nop-stream-runtime（114 / 30212）、nop-stream-cep（74 / 11683）、nop-stream-flow（92 / 10306）、nop-stream-sql（14 / 3090）、nop-stream-rocksdb（19 / 4494）、connector 四件（23 / 5216）、nop-stream-fraud-example（21 / 3256）、quickstart 模板（非 maven 模块）。
- **纪律**: 只读分析，未修改任何产品代码；未读取 ai-dev/audits、plans、bugs、lessons 下任何文件。

## 方法

1. **超大文件扫描**: `find nop-stream -path "*/src/main/*" -name "*.java" | xargs wc -l | sort -rn | head -35`，对 >800 行类逐个输出方法/段落大纲判断职责数。
2. **死代码双口径**: 口径 A = 自写脚本对全部 main+test 源码做 `name(` 与 `::name` 出现次数 ≤ 声明数判定（含 test 作 usage 面）；口径 B = 对每个候选名单独全仓 grep（`--include=*.java/*.xml/*.xdef/*.xpl/*.xdsl`，含 test 与全部资源）。常数扫描同理（`static final` 声明 vs 全仓出现次数）。反射面人工排除：`@DataBean` JSON 序列化 setter/getter、ServiceLoader SPI 接口、Java 序列化 `readResolve`、`ForTest` 且 test 有调用的钩子。
3. **重复代码**: memory 与 rocksdb 两个 SerDe 的同名方法集合比对（comm）+ restoreMapState 双侧逐行阅读；connector 工厂样板抽样比对。
4. **命名一致性**: 概念词族计数（subtask/task/taskInstance、epoch/generation、pipeline/graph）+ 构造点/调用点交叉验证。
5. **import 分组**: 全模块 648 文件扫描，规范序 = io.nop.* → 第三方 → java.* → static 最后（AGENTS.md 口径）；与 nop-auth/nop-rule/nop-job 基线对比。
6. **注释质量**: TODO/FIXME/注释掉代码块 grep + "Item N/Stage N/P-REQ-N" 历史编号计数 + javadoc 与 pom/代码行为交叉核对。
7. **public 面收窄**: 自写脚本找「全仓（main+test）零跨包引用」的 public 类型，再人工排除反射/SPI/序列化面。
8. ** provenance 判定**: 通过 license 头区分 Flink 移植文件（182 个，Apache 2.0 头）与 Nop 原生文件，移植文件的固有引擎复杂度不计为问题。

### 超大文件 Top30（>600 行，`_` 前缀生成物已排除）

| 行数 | 文件 |
|-----|------|
| 2845 | runtime/coordinator/JobCoordinator.java |
| 2073 | runtime/operators/windowing/WindowOperator.java |
| 1822 | runtime/checkpoint/CheckpointCoordinator.java |
| 1591 | runtime/execution/GraphModelCheckpointExecutor.java |
| 1453 | cep/operator/CepOperator.java |
| 1326 | runtime/execution/SupervisionLoop.java |
| 1301 | core/execution/InputGate.java |
| 1200 | core/execution/task/StreamTaskInvokable.java |
| 1194 | connector-jdbc/JdbcTwoPhaseCommitSink.java |
| 1153 | cep/nfa/NFA.java |
| 1123 | cep/nfa/compiler/NFACompiler.java |
| 1110 | flow/builder/StreamModelDslBuilder.java |
| 1068 | rocksdb/RocksDBKeyedStateBackend.java |
| 1044 | sql/compile/StreamSqlCompiler.java |
| 1017 | flow/model/_gen/_StreamModel.java（生成物，非审计对象） |
| 1016 | runtime/taskmanager/TaskManager.java |
| 985  | runtime/checkpoint/storage/CheckpointSerDe.java |
| 974  | runtime/checkpoint/storage/JdbcCheckpointStorage.java |
| 949  | flow/builder/AdvancedTransforms.java |
| 894  | core/execution/GraphExecutionPlan.java |
| 807  | core/environment/StreamExecutionEnvironment.java |
| 773  | cep/pattern/Pattern.java |
| 771  | core/common/state/backend/memory/MemoryStateSerDe.java |
| 763  | core/exceptions/NopStreamErrors.java |
| 729  | core/jobgraph/JobGraphGenerator.java |
| 723  | rocksdb/RocksDBSnapshotSerDe.java |
| 706  | connector-debezium/DebeziumCdcSourceFunction.java |
| 691  | runtime/transport/RemoteInputChannel.java |
| 658  | core/graph/StreamGraphGenerator.java |
| 655  | runtime/checkpoint/storage/LocalFileCheckpointStorage.java |

**职责判定摘要**：WindowOperator / NFA / NFACompiler / CepOperator / CheckpointCoordinator / InputGate / StreamTaskInvokable 等 182 个 Apache-license Flink 移植文件的体量属引擎固有复杂度（Flink 同名类本身 1000+ 行），不立案。原生的 runtime 协调层（JobCoordinator、SupervisionLoop、ops 包）与 connector 2PC sink 是本次结构性发现集中地。

---

## 发现

### [RD-1] JobCoordinator 2845 行 god-class：单类承载 8 类职责且 60+ 处内联全限定名
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:90-2845`（内联 FQN 集中于 440-520、627-724、1247-1248、1323-1382、1699-1803）
- **证据片段**: ```java
public class JobCoordinator implements IStreamCoordinatorRpcService {
    // 段落注释自证职责数：Lifecycle / Task Assignment / Checkpoint /
    // Failure Detection & Recovery / Termination / Status 六大节 +
    // leadership/HA + health + eventBus + metrics
    private volatile io.nop.stream.runtime.rpc.RemotePipelineSpec pipelineSpec;
    private final io.nop.stream.runtime.event.StreamJobEventBus jobEventBus =
            new io.nop.stream.runtime.event.StreamJobEventBus();
    private final io.nop.stream.runtime.health.JobHealthStateMachine health;
    // globalRecovery(RecoveryCause) 单方法 1661-2060 行，约 400 行
    ```
- **严重程度**: P2
- **现状**: 2845 行、约 100 个方法，`// =====` 段落注释自证 6 个功能节；另承载 HA 选主监听、health 状态机、事件总线、指标、RPC 分发。`globalRecovery(RecoveryCause)` 单方法约 400 行（1661-2060），`detectFailures` 约 150 行（1413-1564）。类内 60+ 处以内联全限定名引用 `io.nop.stream.runtime.event.StreamJobEvent.EventType`、`io.nop.stream.runtime.metrics.EngineMetrics`、`io.nop.stream.runtime.health.*`——尽管 `StreamJobEvent` 已在 import 列表中（第 66 行），嵌套枚举 EventType 完全可用短名。
- **风险**: 恢复/选主/checkpoint 触发/任务分配共享同一把锁与同一批状态字段，任何一节改动都要求理解全文；内联 FQN 使方法签名与事件分发代码视觉噪音极大，review 成本高；后续继续往该类加职责的引力场已经形成（failure detection、termination、status 全在同一文件）。
- **建议**: 按已有段落注释拆边界：(1) `globalRecovery`/`requestRecovery`/`detectFailures`/重启预算字段 → `RecoveryManager`（约 700 行，输入 JobCoordinator 引用）；(2) `startPeriodicCheckpoints`/`stopPeriodicCheckpoints`/`registerDistributedCommitForwarder`/`registerDistributedAbortHandler` → 并入已有的 `CheckpointCoordinator`（JobCoordinator 已持有其引用，getCheckpointCoordinator() 第 1226 行）；(3) 内联 FQN 全部改 import 短名（一次机械替换）。属 P2：可读性维护成本真实且持续增长，但不改变行为。
- **信心水平**: 确定
- **误报排除**: 不把 Flink 移植文件的体量当问题——本类是 Nop 原生代码（非 Apache license 头）；段落注释说明作者已意识到拆分边界，只是未实施。
- **复核状态**: 未复核

### [RD-2] ops 运维面在生产代码零接线：governance 调度器从未启动、指标上报器无生产构造点
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:326-347`；`StreamMetricsReporter.java:44-56`；`StreamOpsHttpServer.java:69-79`
- **证据片段**: ```java
    // ==================== governance (P-REQ-11) ====================

    /** Starts the periodic governance sweep. */
    public synchronized void startGovernance() {
        if (governanceSweeper != null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "governance sweeper already started");
        }
        governanceSweeper = Executors.newSingleThreadScheduledExecutor(
                NopStreamThreadFactory.named("stream-ops-governance"));
        governanceSweeper.scheduleAtFixedRate(this::governanceSweep, ...
    // 全仓 main 代码 0 处调用 startGovernance；governanceSweep 仅被
    // TestOpsRestLifecycleE2E.java:293 直接调用
    ```
- **严重程度**: P2
- **现状**: `startGovernance()` 在 src/main 内 0 调用（grep `startGovernance` 全仓：仅声明处 + `governanceSweep` 被 1 个测试直调）；`StreamMetricsReporter` 在 src/main 无构造点（仅 TestMetricsExposureE2E 构造）；`StreamOpsHttpServer` 只被 src/test 下的 `JobCoordinatorMain`/`TaskManagerMain` 启动器引用，src/main 的两个 main（`StreamMaintenanceMain`、`FraudDetectionDemo`）均不涉及。即 P-REQ-11 声称的「周期治理清扫（checkpoint 历史/终态任务记录修剪）」在任何一条仓库内生产路径上都不会运行。
- **风险**: 嵌入方拿到 OpsJobManager 后合理假设治理清扫随服务启动而运行，实际必须自己记得调 `startGovernance()`——漏调则历史记录无界增长；后续开发者会误以为该能力已被端到端验证（实际只有测试直调 sweep 方法）。
- **建议**: 二选一并落注释：(a) 在 `StreamOpsHttpServer` 启动序列中显式调用 `startGovernance()`（配置开关控制），使「起 server = 起治理」成为不变式；(b) 若刻意留白给嵌入方，则在 `startGovernance` javadoc 与 ops README 中写明「必须由宿主显式启动」，并让 `governanceSweep` 的 javadoc 不再自称 periodic。
- **信心水平**: 确定（调用面事实）；对「是否有仓库外启动器」为很可能（本仓内无任何线索）
- **误报排除**: 已检查 `_vfs` beans.xml 与全部 `*.xml`（无 init-method 反射接线）；OpsJobManager 非 @DataBean，无反射启动面。
- **复核状态**: 未复核

### [RD-3] 私有/包内死方法簇：双口径验证 0 调用（约 15 处确定项）
- **文件**: 多处，代表点：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryMapState.java:72-79`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/SubtaskPlanBuilder.java:79-82`；`core/connector/registry/StreamConnectorConfig.java:75`；`core/datastream/WindowedStreamImpl.java:194`；`core/environment/StreamExecutionEnvironment.java:145`；`cep/pattern/conditions/BooleanConditions.java:36`；`core/common/eventtime/WatermarkOutputMultiplexer.java:98`；`core/common/buffer/PerKeyOrderedBuffer.java:133,201`；`core/common/state/KeyExtractorFunction.java:45`；`core/common/state/PriorityComparator.java:49`；`core/windowing/utils/TimestampedValue.java:106`；`cep/PatternStreamBuilder.java:107`；`core/model/StreamComponents.java:295,156`
- **证据片段**: ```java
// MemoryMapState.java:72 —— private 方法，全仓（含 test）0 调用；
// 且 put() 第 91-93 行把同一段 writeEviction+computeIfAbsent 逻辑又手写了一遍
    private Map<UK, UV> getOrCreateMap() {
        TypedNamespaceAndKey k = currentKey();
        if (ttl != null) {
            ttl.writeEviction(k, storage);
        }
        return storage.computeIfAbsent(k, key -> new HashMap<>());
    }
// SubtaskPlanBuilder.java:79 —— public 便捷包装，唯一"引用"是两个测试里的注释文字
    public StreamTaskInvokable buildSubtaskInvokable(TaskDeploymentDescriptor descriptor) {
        return buildSubtaskPlan(descriptor).getInvokable();
    }
```
- **严重程度**: P2
- **现状**: 口径 A（main+test 全文 `name(`/`::name` 计数 ≤ 声明数）筛出 168 个候选，剔除 @DataBean 反射 setter/getter、接口实现、Java 序列化钩子、test 在用的 ForTest 钩子后余约 15 个确定项：`MemoryMapState.getOrCreateMap`(private)、`SubtaskPlanBuilder.buildSubtaskInvokable`(public, 仅注释提及)、`StreamConnectorConfig.requireInt`(与 nop-datav 同名方法同名不同类)、`WindowedStreamImpl.setDefaultFactory`、`StreamExecutionEnvironment.createLocalEnvironment`、`BooleanConditions.falseFunction`、`WatermarkOutputMultiplexer.unregisterOutput`、`PerKeyOrderedBuffer.trimToCount/seqFor`（后者仅 javadoc 提及）、`KeyExtractorFunction.forKeyedObjects`、`PriorityComparator.forPriorityComparableObjects`、`TimestampedValue.from`、`PatternStreamBuilder.withLateDataOutputTag`、`StreamComponents.addDeclarativeEntry`(WI21)/`getStateMigrationFunctions`。上一轮治理批次确有清理痕迹（MemoryStateSerDe javadoc 自述 "item 21 D-1 convergence" 收敛点），本轮为**新残留**而非旧账未清。
- **风险**: `getOrCreateMap` 型死私有方法最危险：它体内是正确的 TTL 写路径，`put()` 却没复用它——下一个改 TTL 语义的人可能只改 `getOrCreateMap` 而漏掉 `put` 的复制粘贴版本，产生状态不一致。`buildSubtaskInvokable` 型公开便捷包装则诱导新调用方绕过 `buildSubtaskPlan` 返回完整 plan 的契约（TM 侧 checkpoint wiring 需要完整 plan）。
- **建议**: 分两步：先删纯死项（falseFunction、seqFor、forKeyedObjects、forPriorityComparableObjects、TimestampedValue.from、withLateDataOutputTag、unregisterOutput、trimToCount、addDeclarativeEntry、getStateMigrationFunctions、createLocalEnvironment、setDefaultFactory、requireInt、buildSubtaskInvokable）；`MemoryMapState.getOrCreateMap` 不删而是让 `put()` 复用它，消除 TTL 写路径双份实现。复现 grep：`grep -rn "private.*getOrCreateMap\|buildSubtaskInvokable(" nop-stream --include="*.java" | grep -v target`（口径 B 逐名全仓 0 命中）。
- **信心水平**: 确定（双口径一致 + 反射面逐一排除）
- **误报排除**: `VoidNamespace.readResolve`（Java 序列化）、`JobSubmissionSpec.setXxx`（@DataBean JSON 反序列化）、`paneTrackingForTest`/`newListWindowStateForTest`/`elementTimestampsStateForTest`/`getLastReceivedTime`/`mergeRestoredBuffer`（test 在用）、`WindowedStreamImpl.getWindowAssigner`（test 在用，区别于 WindowOperator 同名方法）均已排除。
- **复核状态**: 未复核

### [RD-4] 8 个零引用常量（含一组「config key 三件套」缺二）
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamMetricsReporter.java:49-50`；`core/operators/ChainingStrategy.java:58`；`runtime/launch/ClusterLaunchConfig.java:38`；`runtime/transport/StreamTopicNaming.java:55`；`core/streamrecord/watermark/Watermark.java:46`；`core/common/state/shard/KeyGroup.java:48`；`flow/builder/AdvancedTransforms.java:102`
- **证据片段**: ```java
    public static final String KEY_ENABLED = "nop.stream.metrics.log.enabled";
    public static final String KEY_INTERVAL_MS = "nop.stream.metrics.log.interval-ms"; // 全仓 0 引用
    public static final String KEY_TARGET = "nop.stream.metrics.log.target";           // 全仓 0 引用
    public static final String KEY_FILE = "nop.stream.metrics.log.file";
    // StreamTopicNaming.java:55
    static final Pattern LEGAL_TOPIC_PATTERN = Pattern.compile("[a-zA-Z0-9._-]{1,249}"); // 0 引用
    // KeyGroup.java:48
    public static final int UPPER_BOUND_MAX_PARALLELISM = 1 << 15;                       // 0 引用
```
- **严重程度**: P2
- **现状**: 脚本对全部 `static final` 常量做「全仓出现次数 ≤ 声明数」判定得 8 项，逐一全仓 grep（java/xml/xdef/xpl/xdsl 含 test）确认为 0 引用：`ChainingStrategy.DEFAULT_CHAINING_STRATEGY`、`ClusterLaunchConfig.KEY_COORDINATOR_ID`、`StreamMetricsReporter.KEY_INTERVAL_MS`、`StreamMetricsReporter.KEY_TARGET`、`StreamTopicNaming.LEGAL_TOPIC_PATTERN`、`Watermark.UNINITIALIZED`、`KeyGroup.UPPER_BOUND_MAX_PARALLELISM`、`AdvancedTransforms.XDEF_DEFAULT_ALLOWED_LATENESS`（private）。
- **风险**: `KEY_INTERVAL_MS/KEY_TARGET` 与存活的 `KEY_ENABLED/KEY_FILE` 并排，读代码者会以为四键是同一线的配置契约（实际两个键的字符串没人消费，配置真实读取路径在别处）——按死键名写配置不生效；`LEGAL_TOPIC_PATTERN` 声明了 topic 合法性规则却从不校验，构成「看起来有校验」的错觉；`UPPER_BOUND_MAX_PARALLELISM` 暗示的 1<<15 上界没有任何强制点。
- **建议**: 删除 8 项；若 `LEGAL_TOPIC_PATTERN`/`UPPER_BOUND_MAX_PARALLELISM` 想保留为「文档化常量」，把它们真正用于校验（topic 命名 / parallelism 上界检查）或挪进 javadoc，不留无消费面的 `static final`。
- **信心水平**: 确定（双口径一致）
- **误报排除**: `SERIAL_VERSION_UID`/`LOG` 类通用项已过滤；字符串值（如 "nop.stream.metrics.log.interval-ms"）另按原文串全仓 grep 亦 0 命中，排除按字符串值引用的可能。
- **复核状态**: 未复核

### [RD-5] LatencyMarker 全链路休眠脚手架：0 实例化、终端处理被注释
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:384-397`（另涉 `streamrecord/LatencyMarker.java` 全文、`operators/KeyExtractingOutput.java:57`、`TimestampedCollector.java:102`、`ChainingOutput.java:137`、`Input.java:59-62`、`Output.java:76`、`execution/InputGate.java`（latency marker 分支））
- **证据片段**: ```java
    public void processLatencyMarker1(LatencyMarker latencyMarker) throws Exception {
        //this.latencyStats.reportLatency(marker);          // 唯一的处理逻辑被注释
    }

    public void processLatencyMarker2(LatencyMarker latencyMarker) throws Exception {
    }

    // 下游转发仍在：
    public void emitLatencyMarker(LatencyMarker marker) {
        this.output.emitLatencyMarker(marker);
    }
```
- **严重程度**: P2
- **现状**: `grep -rn "new LatencyMarker" nop-stream --include="*.java"`（含 test）0 命中——没有任何 source 或 runtime 组件产生过 latency marker；转发链（emitLatencyMarker → processLatencyMarker，跨 7+ 文件）与 `LatencyMarker` 类本身、`InputGate` 的 marker 分支均为 Flink 移植的休眠管道，终点的 `latencyStats.reportLatency` 被注释掉且 `latencyStats` 字段已不存在。
- **风险**: 这是典型的「半删半留」状态：占接口/转发/类/分支共约 10 个文件的面，读者会以为延迟统计是受支持特性并寻找开启方式；未来重构 output 链（如加新 Output 装饰器）必须继续实现无意义的 emitLatencyMarker 转发。
- **建议**: 二选一：(a) 整体摘除 latency-marker 管道（接口方法、转发、类、InputGate 分支），需要时按 Flink 新版 latency marker 设计重新引入；(b) 若保留扩展点，删掉被注释的 `//this.latencyStats...` 行并在 `LatencyMarker` javadoc 写明「当前无生产者，预留」。倾向 (a)：Flink 自身也已用 LatencyMarker → 向 Metrics 方案迁移。
- **信心水平**: 确定（0 实例化为强证据）
- **误报排除**: 已确认 `emitLatencyMarker` 的所有"调用点"均为转发实现而非产生点；`Output.java:76` 是从 StreamElement 分派，源头仍需有人构造 marker。
- **复核状态**: 未复核

### [RD-6] runtime→flow 依赖已成立，两处 javadoc 仍宣称「runtime 不能依赖 flow」
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/RemotePipelineResolver.java:19-25`；`runtime/transport/SubtaskPlanBuilder.java:99-103`；佐证 `nop-stream/nop-stream-runtime/pom.xml`（nop-stream-flow 依赖项及其上方注释）
- **证据片段**: ```java
// RemotePipelineResolver.java javadoc：
 * Item 14 (composite-scenario distributed): rebuilds a {@link JobGraph} on the
 * TaskManager side from a {@link RemotePipelineSpec}. The runtime module cannot
 * depend on the flow/XDSL modules, so implementations are provided via
 * {@link java.util.ServiceLoader} by whoever owns the declaration format ...
// runtime/pom.xml（同文件下方另一段注释自证依赖成立）：
        <!-- ... conf-validate / dry-run entry lives in the maintenance entry family
             (this module) while the validation core lives in nop-stream-flow ...
             First stream-module compile dependency of runtime beyond core. -->
        <artifactId>nop-stream-flow</artifactId>
```
- **严重程度**: P2
- **现状**: runtime 的 pom 直接 compile 依赖 nop-stream-flow（StreamConfValidateCommand.java:22-24 三个 flow import 为证，注释还标注这是 "First stream-module compile dependency of runtime beyond core"）。但 `RemotePipelineResolver` 与 `SubtaskPlanBuilder` 两处 javadoc 仍以「runtime 不能依赖 flow/XDSL 模块」作为 ServiceLoader 间接层的存在理由。
- **风险**: 理由失实则架构决策不可信：后续读者要么误以为 runtime 仍是「零 flow 依赖」的纯净部署单元（打包裁剪决策会错），要么据此推定 ServiceLoader 间接层只是历史包袱而误删——而它对 XDSL 声明格式的解耦仍有真实价值。
- **建议**: 改写两处 javadoc 的动机段：间接层保留的真实理由是「XDSL 声明格式由 flow/上层场景模块拥有，runtime 不应知道具体声明语法」，而非「不能依赖」；顺带在 pom 的 flow 依赖注释里交叉引用 RemotePipelineResolver 说明两种依赖关系并存的原因。
- **信心水平**: 确定
- **误报排除**: 无——pom、import、javadoc 三方文本互相矛盾是事实层面。
- **复核状态**: 未复核

### [RD-7] RemotePipelineResolver SPI 全仓唯一实现位于 fraud-example 的 src/test
- **文件**: `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/ScenarioXdslPipelineResolver.java`（注册文件 `nop-stream-fraud-example/src/test/resources/META-INF/services/io.nop.stream.runtime.rpc.RemotePipelineResolver`）
- **证据片段**: ```text
$ grep -rln "implements RemotePipelineResolver" . --include="*.java" | grep -v target
nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/ScenarioXdslPipelineResolver.java
$ cat nop-stream-fraud-example/src/test/resources/META-INF/services/io.nop.stream.runtime.rpc.RemotePipelineResolver
io.nop.stream.fraud.scenario.ScenarioXdslPipelineResolver
```
- **严重程度**: P2
- **现状**: XDSL 远程部署路径的关键环节（TM 侧从 RemotePipelineSpec 重建 JobGraph）在仓库内没有任何 src/main 实现；唯一实现和唯一 META-INF/services 注册都在示例模块的 test 树。任何非 fraud 场景想把 XDSL 声明的 pipeline 远程部署到多 JVM，会在 `SubtaskPlanBuilder.resolvePipelineSpec`（fail-fast "no resolver"）处失败。
- **风险**: 「XDSL → 远程分布式部署」表面是 runtime 主路径能力（TaskDeploymentDescriptor/RemotePipelineSpec/RemotePipelineResolver 一整套类型都在 src/main），实际是仅fraud 测试可达的能力；下一个基于 runtime 构建 distributed 部署的人会在集成晚期才撞上缺失的 resolver。与 RD-6 叠加后误导性更强（javadoc 说按 SPI 提供，实际无人提供生产实现）。
- **建议**: 三选一并写入 owner doc：(a) 把 `ScenarioXdslPipelineResolver` 的通用部分（StreamExecutionEnvironment#buildJobGraph 路径）上移到 nop-stream-flow 的 src/main 并注册 services 文件（flow 本就拥有声明格式，且 runtime 已依赖 flow，无新增环）；(b) 保持 SPI 现状但在 runtime 的 RemotePipelineSpec javadoc 与 docs-for-ai 中显式写「当前仓库内无生产实现，须自行注册」；(c) 若 XDSL 远程部署尚未 ready，给 TaskDeploymentDescriptor.pipelineSpec 路径加 @Internal/实验标注。倾向 (a)。
- **信心水平**: 确定（实现位置）；对「是否有意仅作示例」为很可能
- **误报排除**: 已排除 XML 形式注册（`find nop-stream -path "*META-INF/services*" -not -path "*/target/*"` 仅 3 个文件，另两个是 core 的 checkpoint/deployment SPI，各有 src/main 实现）。
- **复核状态**: 未复核

### [RD-8] MemoryStateSerDe 与 RocksDBSnapshotSerDe 平行实现：8 状态族 × 2 份骨架代码
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`（771 行）与 `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java`（723 行）
- **证据片段**: ```java
// 两侧共享的方法名集合（comm 比对）：
// embedSchemaFingerprint loadAccumulatorClass loadClass resolveTypeName
// resolveAggregateFunction restoreAggregatingState restoreAppendingState
// restoreListState restoreMapState restoreReducingState restoreState
// restoreValueState snapshotKeyedState snapshotOneState snapshotState
// writeListPayload ...
// MemoryStateSerDe.java:398
    private void restoreMapState(...) {
        final Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        String keyTypeName = resolveTypeName(stateInfo, "mapKeyTypeName", "mapKeyType");
        ...
        MapValuePairValidator.forEachValidPair(e, stateName, (mapKeyObj, mapValueObj) -> { ...
// RocksDBSnapshotSerDe.java:501 —— 同一 resolveTypeName 双键回退 + 同一 forEachValidPair，
// 仅存储机制不同（Map vs ColumnFamily + byte[] key 编码）
```
- **严重程度**: P2
- **现状**: 两侧 javadoc 互认「byte-compatible，同样 8 个 stateType 分支」，且共享注释条目（"item 24 parity guards converged across Memory/RocksDB serdes"）说明团队已经为对齐付出过多轮人工同步。17 个同名方法为骨架级重复；真正差异只在存储适配（LinkedHashMap storage vs RocksDB CF + KeyEncoder）。
- **风险**: 快照格式每加一个字段/分支/校验（如 TimeWindow namespace 校验、ContainerValueCodec 包裹）都要双写并保证逐字节兼容——这是当前 repo 中漂移代价最高的重复面；校验逻辑已经出现过需要「人工收敛」的先例（item 24 注释自证），下次格式演进仍会重复。
- **建议**: 不必追求合并类，收敛格式协议：在 nop-stream-core 抽一个 `StateSnapshotFamilyCodec`（stateType → {resolveTypeInfo, decodeEntries, encodeEntries} 的 8 行注册表 + 与存储无关的 entry 循环），memory/rocksdb 各自只实现 entry 写读适配器（约 200 行/侧）。`resolveTypeName` 双键回退、MapValuePairValidator 调用点、ContainerValueCodec 分支全部落在一处。若暂不做，至少把「两侧必须同步修改」写成两个类头部互相的 `@see` 契约（现 RocksDBSnapshotSerDe 已有 byte-compatible 声明，MemoryStateSerDe 侧补对称声明）。
- **信心水平**: 确定（重复面）；对「收敛收益大于改造成本」为很可能
- **误报排除**: 不视 connector 工厂（File/Jdbc/Batch 三个 IStreamSinkFactory 实现）为有害重复——那是声明式 SPI 样板，每 factory 仅 ~60 行且参数表本就不同。
- **复核状态**: 未复核

### [RD-9] import 分组热点：60/648 文件违反规范序，主导模式是 static import 不在最后
- **文件**: 热点：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:10-16`；`core/jobgraph/JobVertex.java:10-20`；`runtime/coordinator/JobCoordinator.java:33-72`；全列表见方法节（60 文件）
- **证据片段**: ```java
// StreamOpsHttpServer.java:10-20
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_ALREADY_HOSTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ILLEGAL_HEALTH_TRANSITION;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;   // static 再次插入
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import java.io.IOException;
// JobVertex.java:10-20 —— java.* 先于 io.nop.*，static 又混在 io.nop 之后
import java.io.Serializable;
import java.util.ArrayList;
...
import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
```
- **严重程度**: P3
- **现状**: 按规范序（io.nop → 第三方 → java.*，static 最后）扫描 nop-stream main 648 文件，60 个（9.2%）存在乱序；模式分布：static→jdk 33、static→nop 20、jdk→nop 5、static→第三方 2。对照组 nop-auth+nop-rule+nop-job 的主导违规是第三方在 io.nop 前（92 处）——即 nop-stream 的违规模式自成一类（static 混插），非全仓普遍风格。
- **风险**: 纯一致性/可读性成本；但当静态导入夹在中间时，`ERR_STREAM_*` 与普通类引用的视觉区分度下降，review 时易漏掉新增静态依赖。
- **建议**: 一次性机械排序（IDE optimize imports 按 AGENTS.md 分组配置），热点先清 StreamOpsHttpServer、JobVertex、JobCoordinator 三个高频改动文件；不要求逐文件人肉修。
- **信心水平**: 确定
- **误报排除**: 仅计组间乱序，不计组内字母序；`_` 前缀生成物与 test 未计入。
- **复核状态**: 未复核

### [RD-10] TaskLocation.taskIndex 与全模块 49 文件的 subtaskIndex 同概念异名
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/TaskLocation.java:23-39`；混用实证 `core/execution/task/SubtaskTask.java:216`、`runtime/taskmanager/RunningTask.java:79`
- **证据片段**: ```java
// TaskLocation.java —— 字段与访问器都叫 taskIndex
    private final int taskIndex;
    public int getTaskIndex() { return taskIndex; }
// RunningTask.java:79 —— 把 subtaskIndex 灌进 taskIndex 参数
        this.taskLocation = new TaskLocation(jobId, "pipeline-0", vertexId, subtaskIndex);
// SubtaskTask.java:216 —— 同一行内标签叫 subtask、访问器叫 getTaskIndex
        return String.format("%s [subtask %d]", subtask.getVertexId(), subtask.getTaskIndex());
```
- **严重程度**: P3
- **现状**: 同一概念（vertex 内的子任务序号）在 TaskLocation/Subtask/Task 三个类中叫 `taskIndex/getTaskIndex()`，其余 49 个文件用 `subtaskIndex`（`subtask` 词族共 62 文件，`taskInstance` 0 命中）；TaskLocation 的构造点 RunningTask 直接把 `subtaskIndex` 变量赋给 `taskIndex` 形参。
- **风险**: checkpoint 恢复路径上（TaskStateSnapshot.getTaskIndex() → 按 index 对位恢复）与 reshard/分配路径（subtaskIndex）做比对时命名不一致会诱发 off-by-one 类误读；`SubtaskTask` 的格式化字符串已显示作者需要用人类语言二次澄清。
- **建议**: 若担心 checkpoint 序列化兼容（TaskLocation 若有历史快照落盘则字段名敏感），保留字段、仅加 `@Deprecated` 的 `getSubtaskIndex()` 别名并迁移调用方（全仓 `getTaskIndex()` 仅 7 处）；新代码统一 subtaskIndex。
- **信心水平**: 确定（同值同义有构造点实证）
- **误报排除**: `Task`/`Subtask` 类名本身（Flink 移植命名）不算问题，只算 index 概念的漂移。
- **复核状态**: 未复核

### [RD-11] 「epoch」一词承载 4 种语义，fencingEpoch 与 checkpoint epochId 无词面区分
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:2060-2064`（deriveHaFencingEpoch）、`core/checkpoint/TaskEpochSnapshot.java`（checkpoint 轴）对照 `runtime/cluster/TaskIdentity.java`、`runtime/ops/JobSubmissionSpec` 之外的 materialization 轴（OverWindowOperator `currentMaterializationEpoch`）
- **证据片段**: ```java
// JobCoordinator.java:2060 —— fencing epoch 是两个 epoch 语义的合成
    public static long deriveHaFencingEpoch(long leaderEpochValue, long recoveryGen) {
// 同模块另一轴：checkpoint/ledger 的 epochId（PendingCheckpoint/EpochManifest/
// CheckpointAckMessage 均用 epochId），以及 OverWindowOperator 的
// private volatile long currentMaterializationEpoch
```
- **严重程度**: P3
- **现状**: 「epoch」词族至少 4 个正交概念：checkpoint epoch（`epochId`，barrier/ACK/ledger 轴）、HA leader epoch（`LeaderEpoch.getEpoch()`）、恢复代（`recoveryGen`）、窗口物化代（`currentMaterializationEpoch`）；`fencingEpoch = f(leaderEpoch, recoveryGen)` 又是合成值。checkpoint ACK 消息（CheckpointAckMessage）同时带 epochId 语义字段与 fencingEpoch 字段，阅读时只能靠注释区分。
- **风险**: 故障排查时（尤其 reshard/恢复竞态）容易把 checkpoint epoch 与 fencing epoch 混淆——两者单调性来源、失效时机完全不同；这是真实发生过此类混淆的高危域（checkpoint/恢复）。
- **建议**: 不建议大规模重命名（跨 RPC/存储序列化）；最小动作：在 `NopStreamErrors` 或一份 glossary 注释中固化术语表（epochId=checkpoint 序号 / fencingEpoch=HA+恢复合成栅栏 / recoveryGen=恢复代 / materializationEpoch=over-window 物化代），并让 CheckpointAckMessage 的两个字段 javadoc 互相引用以示区分。
- **信心水平**: 很可能（语义重载为事实，混淆概率为推断）
- **误报排除**: Flink 自身也有 epoch/generation 多义，移植部分的 LeaderEpoch 属移植词汇不计。
- **复核状态**: 未复核

### [RD-12] 17 个零跨包引用的 public 类型（public 面收窄候选簇）
- **文件**: 代表：`cep/nfa/aftermatch/NoSkipStrategy.java`、`SkipPastLastStrategy.java`、`SkipToNextStrategy.java`；`cep/nfa/sharedbuffer/SharedBufferEdge.java`；`core/common/eventtime/WatermarkAlignmentParams.java`、`NoWatermarksGenerator.java`、`RecordTimestampAssigner.java`、`WatermarksWithIdleness.java`；`core/common/typeinfo/SimpleSerializer.java`；`core/datastream/SideOutputDataStream.java`；`core/windowing/assigners/WindowStagger.java`；`runtime/alert/WebhookAlertPayload.java`；`sql/compile/CompileErrors.java`、`SqlScalarXplPrinter.java`；全 17 项见方法节
- **证据片段**: ```text
脚本口径：public 类型在其包外（全部 main 包 + 全部 test）总引用数 == 包内引用数
=> PUBLIC_TYPES_WITH_ZERO_CROSS_PACKAGE_REFS=17
// 例：NoSkipStrategy 仅被 AfterMatchSkipStrategy 工厂在包内引用；
// WatermarkAlignmentParams 仅被 watermark 对齐器包内引用
```
- **严重程度**: P3
- **现状**: 648 个 public 类型中 17 个在 main+test 全域内无跨包引用。多数是「策略实现类只经工厂/枚举暴露」的合理封装习惯没做完（NoSkipStrategy 等三个 AfterMatchSkipStrategy 实现、Watermarks 三个生成器）；少数可能是预留 API（SideOutputDataStream、WindowStagger）。
- **风险**: 框架模块的 public 即兼容承诺：这 17 个类一旦被外部依赖，后续重构（如改 SharedBufferEdge 结构）就被无谓绑死；同时扩大读者需要理解的面。
- **建议**: 分三档处理：纯包内策略实现（NoSkipStrategy/SkipPastLastStrategy/SkipToNextStrategy/SharedBufferEdge/SimpleSerializer/CompileErrors/SqlScalarXplPrinter/WebhookAlertPayload/RichCompositeIterativeCondition/MemoryArrayListState）改 package-private 或收构造器；事件时间生成器三件检查是否该像 Flink 一样经 public 工厂方法暴露（若是则保留 public 类但加 @Internal）；SideOutputDataStream/WindowStagger 若为计划 API 则保持现状并在 javadoc 标注实验态。
- **信心水平**: 很可能（静态引用面已排除反射；CEP 的策略类可能经 XDSL 字符串引用，需在实施时再查 `skip-strategy` 等声明字段）
- **误报排除**: 仅报成簇模式；未把 service/RPC 接口（有 XML/SERVICE 注册面）计入——已逐项核对 META-INF/services 与 `_vfs` beans 文件。
- **复核状态**: 未复核

### [RD-13] 历史治理编号注释大量沉淀：501 处 "Item N / Stage N / P-REQ-N"（190 个文件）
- **文件**: 分布全域；样例：`core/common/state/backend/memory/MemoryStateSerDe.java`（7 处）、`connector/file/FileTwoPhaseCommitSink.java`（5 处）、`sql/compile/SqlScalarXplPrinter.java`（WI17/WI9）、`rocksdb/RocksDBSnapshotSerDe.java`（Stage 34）
- **证据片段**: ```java
// MemoryStateSerDe.java:87
 * Single-point (item 21 D-1 convergence) snapshot/restore serde for ...
// FileTwoPhaseCommitSink.java:439
 * Item 20 (P-REQ-13, D3 file-2pc row): pre-submit connectivity probe — ...
// RocksDBSnapshotSerDe.java:73
        // Stage 34: stamp the binary key-layout version for observability and ...
// StreamMetricsReporter.java:48
    /** ST-6 裁定（2026-09-13，审计 plan 357）：保留引擎自建调度线程——... */
```
- **严重程度**: P3
- **现状**: `grep -rn "Item [0-9]\|item [0-9]\|Stage [0-9]\|P-REQ-[0-9]\|P1-[0-9]\|ST-0[0-9]" nop-stream --include="*.java" | grep -v test | wc -l` = 501，覆盖 190 个文件。编号指向既往治理批次的 item/阶段号，多数无文件内或邻近注释可解；个别带日期与 plan 号（ST-6 裁定），大部分仅裸编号。
- **风险**: 三个月后裸编号即成不可解引用：读者无法判断 "item 21 D-1" 的约束是否仍生效，也不敢删相关代码（宁可信其有）；编号继续累积还会随批次号膨胀（Item、WI、ST、P-REQ、P1-xx、D3-⑥ 六套记法并存）。
- **建议**: 不回改存量（成本过高），立增量规则：新注释必须自带语义（写约束本身），编号只作可选尾注（如 `（audit 2026-09 R4 item 21）`）；在模块 README 或 docs-for-ai 明确该规则。同时把六套记法收敛为一套前缀。
- **信心水平**: 确定（计数可复现）；对「编号在 ai-dev 计划中可解」不作判断（审计纪律禁读该目录）
- **误报排除**: 仅统计确定匹配模式；未把 Flink 移植文件自带的 Apache 注释计入。
- **复核状态**: 未复核

### [RD-14] SubtaskPlanBuilder 孤儿 javadoc 块：两个连续 /** */ 挂同一方法
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/SubtaskPlanBuilder.java:88-103`
- **证据片段**: ```java
     * invokable. The full plan is returned because the TM-side checkpoint wiring
     * ({@code RemoteTaskDeploySupport}) needs it to build the checkpoint plan
     * and to run the rescale-aware restore ...
     */                                  // 第一个 javadoc 到此结束
    /**
     * Item 14 (composite-scenario distributed): resolves the JobGraph for a
     * descriptor — ...
     */                                  // 第二个 javadoc 才挂在 buildSubtaskPlan 上
    public DeployedSubtaskPlan buildSubtaskPlan(TaskDeploymentDescriptor descriptor) {
```
- **严重程度**: P3
- **现状**: `buildSubtaskPlan` 前叠了两个 javadoc 块：第一块描述旧契约（返回完整 plan + invokable），第二块是重写后的新契约（只讲 spec 解析）；工具/IDE 只认最后一块，第一块成死文本。
- **风险**: 两块描述同一方法的不同时期契约，读者需自行考古哪段仍准确；旧块中的 `@return` 语义（"the locally-built invokable"）与方法当前实际返回的 `DeployedSubtaskPlan`（含完整 plan）已不一致，属「描述已删除行为」的过时注释。
- **建议**: 合并为一个 javadoc：保留 Item 14 块为主体，把旧块中仍正确的「返回完整 plan 是 TM 侧 checkpoint wiring 所需」合并为 `<p>` 段，删除其余。
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 未复核

---

## 检查范围

- **已审**（只读）: nop-stream 全部 11 个子模块 `src/main`（748 个非生成 Java 文件 / 117,276 行，含 quickstart 模板目录的参考阅读）；配套 `_vfs/nop/stream/beans/*.beans.xml`、`META-INF/services` 注册文件、各模块 pom.xml 的依赖方向；死代码双口径扫描脚本输出（`_tmp/dead3.py`、`_tmp/import_check2.py`、常数扫描）。
- **明确排除**: `_*` 前缀生成物（含 `_gen/_StreamModel.java`）与全部 `src/test`（test 仅作 usage 佐证面）；182 个 Apache-license Flink 移植文件的固有引擎复杂度（windowing/nfa/cep/state/eventtime 等，见方法节 provenance 判定）；`nop-stream/quickstart`（非 maven 模块的脚手架模板）。
- **纪律遵守**: 未修改任何产品代码；未运行 maven/测试；未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；扫描用临时脚本置于 `_tmp/`。
- **复核提示**: RD-3/RD-4 的结论依赖静态引用计数，删除前请实施方以编译 + 全量 `./mvnw test -pl nop-stream -am` 复验；RD-12 实施前需补查 XDSL/声明文件对策略类按字符串引用的可能。

---

## 维度复核结论

复核人：R5 独立维度复核子代理（只读分析，HEAD 同 `bd089444`）。对 RD-1..14 逐条独立重跑关键 grep / 读取原文，两个口径（标识符计数、字符串值计数，含 test 与 XML/resources 面）均自行复现。结论：**14 项全部保留，0 驳回、0 降级；其中 4 项（RD-1、RD-5、RD-10、RD-11）带证据修正，2 项（RD-2、RD-4）复核后比原文更强**。带修正的发现以本节修正为准，原发现正文未改动。

### 逐条裁决

- **RD-1 → 保留（P2），附证据修正。** 核心事实全部复核成立：2845 行精确；方法计数实测 99（原文约 100）；`// ====` 六个段落节（550/800/872/1390/2256/2451 行）逐一确认；非 Apache 头原生代码属实。修正两处夸大数字：(1) 内联 FQN 实测 `io.nop.*` 非 import 行共 **39 处**，非"60+"；(2) `globalRecovery(RecoveryCause)` 实际起于 1683 行、下一个类级方法 `notifyRecoveryStartedSafely` 在 1832 行，单方法约 **150 行**——"1661-2060 约 400 行"是把恢复辅助方法簇整体计入了单方法。god-class 定性不受影响（2845 行/99 方法/6+ 职责已足），维持 P2。

- **RD-2 → 保留（P2），复核后更强。** 全部复现：`startGovernance` 全仓（java+xml+beans，含 test）**仅声明 1 处、任何调用（含测试）为 0**——比原文"main 0 调用"更强；`governanceSweep` 生产 0 调用、仅 TestOpsRestLifecycleE2E.java:293 直调属实；`new StreamMetricsReporter` 全仓仅 TestMetricsExposureE2E.java:190；`StreamOpsHttpServer` 在 src/main 仅自引用（字段+构造参数），全部外部引用均在 src/test（JobCoordinatorMain/TaskManagerMain/Test*）；`OpsJobManager` 无任何 `*.xml`/beans 接线（grep 0 命中）。生产零接线结论确凿。

- **RD-3 → 保留（P2）。** 具名的 15 个成员逐一全仓 grep（剔除注释后）：每个均**仅声明处 1 次命中、0 代码调用点**。`MemoryMapState.getOrCreateMap`（72-78）与 `put()`（99-104）的 `writeEviction+computeIfAbsent` 双份实现逐行核对属实。微小口径偏差：`buildSubtaskInvokable` 的"两个测试注释"中 TestTaskManager.java:370 实为断言消息字符串而非注释——不构成代码调用，死代码结论不变。getOrCreateMap 型 TTL 双路径隐患论证成立，维持 P2。

- **RD-4 → 保留（P2），复核后更强。** 8 项常量逐一按标识符全仓 grep（java/xml/xdef/xpl/xdsl/yml/properties，含 test）均**仅声明 1 命中**；字符串值口径 `nop.stream.metrics.log.interval-ms` 亦仅声明 1 命中。两点补充：(1) `StreamMetricsReporter.KEY_ENABLED` 复核同样为 0 引用（文件内仅第 69 行用了 KEY_FILE），原文称其"存活"不确——死常量实为 **9 项**，发现方向不变且更强；(2) `nop.stream.metrics.log.target` 字符串出现在随包模板 `src/main/resources/_vfs/nop/stream/conf/metrics.properties.template`（41/50 行样例键）与 TestMetricsConfigTemplate 断言中——键被文档化/测试断言但**无任何生产读取点**，恰恰强化"按模板配键不生效"的风险叙述，不构成反证。

- **RD-5 → 保留（P2），附证据修正。** `new LatencyMarker` 全仓（含 test）0 命中复现（exit=1）；`latencyStats` 在 AbstractStreamOperator 全文仅存在于被注释的第 394 行、字段确已删除；转发链实证（StreamTaskInvokable:1130/1190 等 emitLatencyMarker 转发实现）。修正一处引用点：**InputGate.java 无任何 latency marker 分支**（全文仅两处无关的 "latency" javadoc 用词），原文文件清单中该引用点剔除；marker 分派实际位于 `StreamElement.isLatencyMarker`（62-63 行）。休眠管道结论不变，维持 P2。

- **RD-6 → 保留（P2）。** 三方文本逐字复核成立：RemotePipelineResolver.java:19-21 "The runtime module cannot depend on the flow/XDSL modules"；SubtaskPlanBuilder.java:100-101 同句；runtime/pom.xml 26-30 行 compile 依赖 nop-stream-flow 且注释原文 "First stream-module compile dependency of runtime beyond core."；佐证 StreamConfValidateCommand.java:22-24 三个 flow import 精确吻合。矛盾事实确凿，无反证空间。

- **RD-7 → 保留（P2）。** `implements RemotePipelineResolver` 全仓唯一命中 fraud-example src/test；META-INF/services 注册文件全仓唯一且位于同模块 test resources；fail-fast 点 `resolvePipelineSpec`（SubtaskPlanBuilder:167-180）实证。结论确凿。

- **RD-8 → 保留（P2）。** 771/723 行数精确成立；两侧方法名 comm 比对实测 **16 个共享同名方法**（14 个骨架方法 + snapshotState/restoreState，原文计 17，差额系签名口径）；"item 24 parity guards" 收敛注释双侧存在（Memory 3 处 + RocksDB 1 处）自证人工同步负担。重复面与漂移代价论证成立，维持 P2。

- **RD-9 → 保留（P3）。** 独立扫描（687 个含 import 的 src/main 文件，规则=static 必须位于全部非 static import 之后）得 **92 文件违规**，与原文 60/648 属不同统计口径但同模式同量级；热点样例 StreamOpsHttpServer.java:16-20、JobVertex.java:16-21 import 块与原文引文逐字吻合（static 居首/中、java.* 先于 io.nop.*）。风格类 P3 恰当。

- **RD-10 → 保留（P3），附计数修正。** 三处代码实证逐字吻合：TaskLocation `taskIndex/getTaskIndex()`、RunningTask:79 将 `subtaskIndex` 灌入 `taskIndex` 形参、SubtaskTask:216 标签 subtask + 访问器 getTaskIndex 同行混用。计数修正：`.getTaskIndex()` 全仓 74 处（多数为 Subtask 接收者），TaskLocation 接收者约 10 处而非"仅 7 处"；含 `subtaskIndex` 的文件实测 85 个。概念分裂事实成立，迁移成本被低估但不改变 P3 定级与建议方向。

- **RD-11 → 保留（P3），附两处证据修正。** 成立部分：`deriveHaFencingEpoch = leaderEpochValue * EPOCH_SCALE + recoveryGen`（JobCoordinator:2060-2062）合成语义实证；`epochId` 作为 checkpoint 轴命名存在于 EpochManifest/ChannelState/ICheckpointStorage 等 10+ src/main 文件；LeaderEpoch/recoveryGen 两轴属实——epoch 术语过载内核成立。修正：(1) `currentMaterializationEpoch` 实际位于 `core/execution/ResultPartition.java:165`（barrier 物化语义），**OverWindowOperator 全文 0 处 epoch**，原文归因错误；(2) `CheckpointAckMessage` 实际字段为 `checkpointId + fencingEpoch`（第 29/32 行），**并无 epochId 字段**，"ACK 同时带 epochId 与 fencingEpoch 只能靠注释区分"的具体混淆叙述被证伪。术语表建议仍有效，维持 P3。

- **RD-12 → 保留（P3）。** 抽查具名 16 个类型：全部仅出现于单一包内（NoSkipStrategy 3 文件全在 cep/nfa、CompileErrors 3 文件全在 sql/compile、WebhookAlertPayload 2 文件全在 runtime/alert 等），零跨包引用模式成立；原文对 CEP 策略类可能经 XDSL 字符串引用的保留意见已注明。P3 恰当。

- **RD-13 → 保留（P3）。** 原式 grep 复现 **504 处 / 223 文件**（原文 501/190，行数口径一致、文件数差异系过滤粒度，同量级）；样例注释（item 21 D-1 / Item 20 P-REQ-13 / Stage 34 / ST-6 裁定）逐一命中属实。增量规则建议合理，P3 恰当。

- **RD-14 → 保留（P3）。** SubtaskPlanBuilder.java 83-103 行两个连续 `/** */` 块悬挂于 104 行 `buildSubtaskPlan` 之上，逐行核实；旧块 `@return the locally-built invokable` 与当前返回 `DeployedSubtaskPlan` 不符属实。P3 恰当。

### 最终保留项

| 编号 | 裁决 | 等级 | 修正/备注 |
|------|------|------|-----------|
| RD-1 | 保留 | P2 | FQN 实测 39 处（非 60+）；globalRecovery(RecoveryCause) 约 150 行（非 400），400 行系方法簇口径 |
| RD-2 | 保留 | P2 | 更强：startGovernance 全仓（含 test）0 调用；ops 面整簇仅测试可达 |
| RD-3 | 保留 | P2 | 15 成员逐一 0 代码调用点；TestTaskManager:370 为断言字符串非注释（不影响结论） |
| RD-4 | 保留 | P2 | 更强：KEY_ENABLED 亦 0 引用，死常量实为 9 项；模板含样例键但无读取点 |
| RD-5 | 保留 | P2 | 修正：InputGate 无 latency marker 分支，引用点剔除；0 实例化 + 注释终点成立 |
| RD-6 | 保留 | P2 | 无修正，三方文本逐字复核成立 |
| RD-7 | 保留 | P2 | 无修正，唯一实现/注册均在 fraud-example src/test |
| RD-8 | 保留 | P2 | 共享同名方法实测 16（原文 17，签名口径差）；771/723 行精确 |
| RD-9 | 保留 | P3 | 独立口径 92/687 违规（原文 60/648），同模式同量级 |
| RD-10 | 保留 | P3 | 计数修正：TaskLocation.getTaskIndex 约 10 处（非 7）、subtaskIndex 文件 85（非 49）；混用实证不变 |
| RD-11 | 保留 | P3 | 证据修正：materializationEpoch 在 ResultPartition 非 OverWindowOperator；ACK 消息为 checkpointId+fencingEpoch（无 epochId 字段）；术语过载内核成立 |
| RD-12 | 保留 | P3 | 抽查 16 类型全部单包，成立 |
| RD-13 | 保留 | P3 | 复现 504 处/223 文件（原文 501/190，同量级） |
| RD-14 | 保留 | P3 | 无修正，双 javadoc 块逐行核实 |
