# 维度 07：可读性/结构/命名/死代码

- 审计对象：`nop-stream/`（11 个子模块，main 共 771 个 Java 文件、约 118k 行；测试 800 个文件）
- 审计基线：HEAD（工作区 live code），盲审纪律下未读取 ai-dev/ 下任何既有审计/计划/日志文件；本文所有证据均来自本轮独立 grep/read。
- 审计日期：2026-10-06

## 审计范围与方法

| 子项 | 方法 |
|---|---|
| 超大文件职责 | 对 14 个 >1000 行的非生成文件逐个提取方法级行号地图（`_gen/` 生成物按 Hard Stop 排除），判定可分离职责块 |
| 死代码 | 脚本枚举 2118 个非 `@Override` public 方法签名 + 209 个 `public static final` 常量；对每个名字做全仓（`*.java/*.xml/*.xpl/*.xdef/*.xmeta/*.xlib/*.xbiz`，含 test 与 nop-benchmark，排除 `/target/`、`_gen`）`call-like` 出现次数统计，`occurrences ≤ defs` 者进入候选，再逐一用 `grep -rn "\bNAME\b"` 直接复核 |
| 命名一致性 | restore/recover、stop/shutdown/close/terminate、shard/maxParallelism、checkpoint/snapshot 动词全量 tally + 语义边界抽查；错误码前缀分布统计 |
| 卫生 | System.out/err、printStackTrace、TODO/FIXME/XXX/HACK、通配符 import、空 catch、吞异常（自写 Python 大括号配对扫描 61 个原始命中逐一人工复核）、>5 行注释代码块（145 个连续注释块逐一启发式过滤） |
| import | Python 全量扫描 main 源文件 import 分组顺序（java-first / nop-first / static 位置 / 未使用 import） |
| 深挖第 2 轮 | `start*/open*/register*/trigger*/init*/install*` 全量 0 引用扫描；declarativeRegistries 特性链路验证；@SuppressWarnings 密度分布（305 处，全部为 unchecked/rawtypes，无压制语义错误区）；误报排除复核（RPC DTO 反序列化 setter、DataBean、配置 DEFAULT_* 字段初始化器） |

## 第 1 轮（初审）

### [RD-01] JobCoordinator 承担 6 类可分离职责，属结构性超聚合（God Coordinator）

**文件+行号**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`（2866 行）

**证据片段**（方法级行号地图，节选自该文件实测）：
```java
572:  public void start()                          // 生命周期 + 674: startFailureDetector + 685: stop()
831:  public void assignTasks()                    // 任务分配（含 AssignmentPlanner 派发）
902:  public PendingCheckpoint triggerCheckpoint() // 检查点触发 + 974: collectAck + 1015: receiveCheckpointAck
1031: public void reportTaskStatus(...)           // 状态上报 + 1157: reportNodeTaskLiveness（存活跟踪）
1422: public void detectFailures()                // 失败检测（约 151 行单方法）
1670: public void globalRecovery(...)             // 恢复编排（三个重载，1692 起约 149 行）+ 1956: rotateFencingEpochCoreLocked
2080: LeaderEpochHandler.becomeLeader/becomeFollower // HA 选主 + 2106: activateAsLeader + 2263: deactivateToStandby
2293: public void terminate(JobTerminationMode)   // 终止模式族（CANCEL/DRAIN/SUSPEND/EXPORT_SAVEPOINT）
2583-2855: 约 30 组 getter/setter                  // 约 270 行纯访问器
```

**严重程度**：P2

**现状**：单类同时承担 (1) 生命周期/HA 选主与 fencing epoch 轮转、(2) 任务分配与部署派发、(3) 检查点触发与 ACK 门禁、(4) 任务状态上报与存活跟踪、(5) 失败检测与全局恢复编排、(6) 终止模式族（含 terminal-savepoint 导出），外加约 270 行可配置项访问器。字段约 60 个，横跨 AtomicLong recoveryGen、liveness map、deployTransportFailures、recoveryLock 等多个并发域。

**风险**：恢复路径（requestRecovery/globalRecovery）与选主状态机（activateAsLeader/rotateFencingEpochCoreLocked）共享同一批可变字段，任何一处锁序变化都可能引入竞态；新职责（如 stall 恢复、deploy grace period）继续落入同一文件，2866 行已超出单次可靠 review 的认知窗口；测试只能以整类为粒度搭建夹具。

**建议**：按现有包结构自然切分：`CoordinatorHaFacade`（选主/fencing）、`RecoveryOrchestrator`（globalRecovery + attempt counters）、`TaskReportAggregator`（reportTaskStatus/liveness）、`CoordinatorCheckpointGate`（triggerCheckpoint/collectAck 门禁，本已委托 CheckpointCoordinator，仅门禁逻辑留在本类）。访问器块可下沉为 `JobCoordinatorOptions` 不可变配置对象。可保持对外门面不变、纯移动式重构。

**信心水平**：高（方法地图与行号全部实测；职责判定基于方法级行号区间，不依赖行数本身）

**误报排除**：不报告 WindowOperator/NFA/StreamSqlCompiler 等同尺寸文件——它们是单一算子/编译器概念的内聚实现（Flink 同源结构亦如此）；仅 JobCoordinator 的职责块之间存在明确的可分离边界（每块有独立字段域与独立下游）。

**复核状态**：未复核

---

### [RD-02] OpsJobManager.startGovernance() 是零调用的生产死入口——治理清扫（含 checkpoint 历史修剪）在生产永不运行

**文件+行号**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:326-345, 348-395, 412-414`

**证据片段**：
```java
326:    public synchronized void startGovernance() {
327:        if (governanceSweeper != null) {
328:            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "governance sweeper already started");
330:        governanceSweeper = Executors.newSingleThreadScheduledExecutor(
331:                NopStreamThreadFactory.named("stream-ops-governance"));
332:        governanceSweeper.scheduleAtFixedRate(this::governanceSweep, ...);
348:    public synchronized int governanceSweep() {          // 唯一生产调用点 = 上面的死调度
381:            ....pruneOldestCheckpointHistory(history.size() - expiredFrom);
```
全仓 grep（含 test、XML/XPL、nop-benchmark）：`startGovernance` 仅 1 处命中（定义本身）。

**严重程度**：P2

**现状**：`startGovernance()` 在整个仓库（main/test/资源文件/benchmark）中没有任何调用者。因此 `governanceSweeper` 字段、调度器创建、以及 `stop()` 中的 shutdownNow 分支（412-414）均不可达；`governanceSweep()` 在生产只能通过测试中的直接方法调用执行（TestOpsRestLifecycleE2E:293、TestGovernanceSweepContainment）。由此连锁：`CheckpointCoordinator.pruneOldestCheckpointHistory`（:1297）的唯一生产调用链也在 `governanceSweep` 内——生产路径上 checkpoint 历史/Job 记录的按上限与按保留期修剪永远不会发生。`StreamGovernanceConfig` 的 4 个配置键（KEY_CLEANUP_INTERVAL_MS、KEY_CHECKPOINT_HISTORY_* 、KEY_JOB_RECORD_RETENTION_MINUTES，`StreamGovernanceConfig.java:25-37`）随之成为无生产效果的配置面。

**风险**：长驻集群的 Ops 观测数据（checkpoint 历史、job 记录）只增不减，属于资源泄漏类后果；更隐蔽的是运维按 `StreamGovernanceConfig` 文档配置保留期后会以为修剪已生效——死入口制造"看起来有治理"的错觉。

**建议**：三选一并留痕：(a) 在 StreamOpsHttpServer/OpsJobManager 启动路径真正调用 `startGovernance()`；(b) 若治理清扫属后续计划特性，给 `startGovernance` 加 `@Deprecated` + javadoc 说明 wiring 缺口，避免读者误认为已生效；(c) 删除调度器 wiring，保留 `governanceSweep()` 为显式手动接口。同时补一条"启动即接线"的回归测试（断言 stop() 后 sweeper 为 null 之类）以防回归。

**信心水平**：高（全仓 0 引用为脚本 + 直接 grep 双重验证；`governanceSweep` 的全部调用点已逐一枚举）

**误报排除**：已排除反射/XDSL 字符串引用（全仓含 xml/xpl/xdef/xmeta 扫描）；已排除 RPC 暴露路径（StreamOpsHttpServer 无对应 endpoint 调用）；测试直接调用 `governanceSweep()` 不构成生产 wiring。

**复核状态**：未复核

---

### [RD-03] 死 public 方法簇：约 35 个方法全仓 0 调用（逐一 grep 验证）

**文件+行号**：分散于 nop-stream-core/runtime/cep/rocksdb/connector（完整清单见下表，行号均为实测定义行）

**证据片段**（验证方法示例，其余同法）：
```bash
$ grep -rn --include="*.java" "\bisPipelined\b" nop-stream/ nop-benchmark/
nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/ResultPartitionType.java:101:    public boolean isPipelined() {
# 仅定义处 1 次命中 => 0 调用
```
扫描口径：2118 个非 `@Override` public 方法 → `NAME(` 全仓 call-like 计数 ≤ 定义数者 66 项 → 逐一排除下述"误报排除"类别后保留下表。

| # | 死成员（定义处） | 类别 |
|---|---|---|
| 1 | `ResultPartitionType.isPipelined/isBounded/isBlocking`（:101/:116/:127） | 枚举谓词 |
| 2 | `AbstractStreamOperator.processLatencyMarker1/processLatencyMarker2`（:384/:388） | Flink 双输出遗留（基方法 processLatencyMarker 存活） |
| 3 | `LatencyMarker.getMarkedTime`（:55） | 遗留 getter（latency marker 功能整体未接线） |
| 4 | `OutputTag.isResponsibleFor`（:66） | 谓词 |
| 5 | `StreamGraph.getNumberOfNodes/containsNode`（:252/:273） | getter/谓词 |
| 6 | `OperatorChain.getNumberOfOperators`（:219） | getter |
| 7 | `WindowedStreamImpl.getLateDataOutputTag`（:150） | getter（late-data OutputTag 实际经 builder 传递） |
| 8 | `StreamExecutionEnvironment.createLocalEnvironment/getExecutionDispatcher`（:149/:280） | 工厂/访问器 |
| 9 | `StreamExecutionResult.getAccumulatorResults/getAccumulatorResult`（:73/:83/:96） | 结果访问器 |
| 10 | `PerKeyOrderedBuffer.seqFor`（:326） | 访问器 |
| 11 | `RecordWriter.getOutputStatus`（:286） | 状态访问器 |
| 12 | `DeploymentPlan.getMemoryBudget`（:77） | getter |
| 13 | `TaskExecutor.getSubtaskTaskCount`（:244） | 计数访问器 |
| 14 | `StreamTaskInvokable.getTaskMetricsHandle`（:381） | 访问器 |
| 15 | `MemoryKeyedStateBackend.getTtlSweepIntervalMillis`（:283） | getter |
| 16 | `StateShard.getHashPolicy`（:84） | getter |
| 17 | `MemoryArrayListState.getCopyOfList`（:72） | 拷贝访问器 |
| 18 | `WatermarkOutputMultiplexer.unregisterOutput`（:98） | 注销对称方法 |
| 19 | `WatermarkAlignmentParams.getMaxAllowedWatermarkDrift/getWatermarkGroup/getUpdateInterval`（:46/:50/:54） | watermark 对齐休眠特性访问器 |
| 20 | `BooleanConditions.falseFunction`（:36） | 常量函数工厂 |
| 21 | `CepOperator.getCepRuntimeContext`（:1444） | 访问器 |
| 22 | `SharedBufferCacheConfig.getCacheStatisticsInterval` + `cacheStatisticsInterval` 字段（:35/:51） | 死配置面（详见 RD-06） |
| 23 | `LocalSourceCoordinator.getFinishedSplits/getRegisteredReaders/getPendingSplitIdsByReader`（:294/:315/:323） | 死诊断 API（详见 RD-05） |
| 24 | `OpsJobManager.getTerminalAt`（:319） | 访问器 |
| 25 | `StreamControlRpcProxyFactory.getServiceInterface`（:99） | 访问器 |
| 26 | `FileSource.getMaxLineBytes`（:84） | getter |
| 27 | `OverWindowOperator.bufferedKeys/keyedStore`（:307/:343，javadoc 自述"test/diagnostic"） | 测试诊断访问器（却 0 测试引用） |
| 28 | `RocksDBStateBackend.getDbPath/getOptionConfig`（:89/:111） | getter |
| 29 | `RocksDBKeyedStateBackend.getCheckpointBaseDir`（:875） | getter（内部直接读字段） |
| 30 | `RemoteInputChannel.getChannelTimeoutMs/getEnqueueOfferTimeoutMs`（:396/:565） | getter |
| 31 | `JoinSideRecord.setEquiKey`（:57，类仅由 EquiJoinOperator 构造器构建） | 死 setter（非序列化类） |
| 32 | `ConnectorProbeResult.getDeliverySemantic/isAutoClosed`（:51/:64） | 探针结果访问器 |
| 33 | `StreamComponents.addDeclarativeEntry/getDeclarativeRegistries/getStateMigrationFunctions`（:295/:329/:156） | 特性存活但这三个变体 0 调用（写侧走 mergeDeclarativeRegistries，见深挖轮） |

**严重程度**：P2（按维度默认上限；单项多为 P3 量级，合并成簇后体现系统性"API 面腐化"）

**现状**：上述每个成员在 `nop-stream/ + nop-benchmark/` 范围 `\bNAME\b` 全词 grep 均只命中定义行；其中 8 项进一步用全仓（所有目录）grep 复核仍为 1 次命中。死 getter/谓词多为 Flink 移植时带入的 API 形状或"为将来接线"预留。

**风险**：死 public API 是对外契约的一部分——使用者可能依赖 `isPipelined()`、`createLocalEnvironment()` 等成员，后续重构时这些成员会阻止内部简化或被误认为已接线；测试诊断类访问器（bufferedKeys/keyedStore、getRegisteredReaders）标着"用于测试"却无任何测试使用，属于自欺式 API。总量 35+ 表明缺少死代码回收机制。

**建议**：按"先删测试诊断类、再删 Flink 移植遗留、最后评估 public API 面"的顺序分批清理；对确想保留为公共 API 形状的成员（如 `createLocalEnvironment`），补 javadoc 标注"预留、当前无内部调用"或加 `@Internal`。批量删除前跑一次 benchmark 模块编译确认（本审计已含 nop-benchmark 引用检查，均为 0）。

**信心水平**：高（双脚本 + 直接 grep 逐项验证；唯一不确定的是未来外部使用意图，已在建议中处理）

**误报排除**：(1) RPC/DataBean DTO 的 setter（CheckpointAckMessage.setTaskLocation/setStateSnapshot、TaskStatusReport.set*、JobStatusResponse.set*、TaskAssignment.setAssignedAt、CoordinatorInfo.setCoordinatorId、NodeInfo.set*/getLastHeartbeatAt、LeaseInfo.setLeaseStartAt/setLeaseExpireAt、TaskDeploymentDescriptor.setDeploymentPlan）——这些类经 RPC/JSON 反射填充，setter 是反序列化钩子，全部排除；(2) fraud-example 演示模块的 DTO setter（AlertSummaryRow/TxSummaryRow/FraudAlert/EnrichedTransaction 等）——demo 应用 + 可能为模板序列化面，单独归为低优先级模式，不列入本表；(3) 带 `@Override` 的实现方法；(4) 接口声明匹配造成的假 0（本表各项已确认无接口声明命中）。

**复核状态**：未复核

---

### [RD-04] @Deprecated 兼容成员在其兼容窗口内零消费者，属无效保留

**文件+行号**：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateBackend.java:92-95`
- `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBStateBackend.java:106-109`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/WatermarkEstimator.java:25-34`

**证据片段**：
```java
// MemoryStateBackend.java:92
@Deprecated
public int getShardCount() {
    return maxParallelism;      // 全仓 \bgetShardCount\b 命中：本定义 + MemoryKeyedStateBackend/RocksDBKeyedStateBackend
}                               // 的同名包级方法 + 1 个测试调用 RocksDBStateBackend 版本；MemoryStateBackend 版本 0 调用
// WatermarkEstimator.java:24
@Internal
@Deprecated
public interface WatermarkEstimator extends Serializable { ... }   // 全仓 0 实现、0 引用（仅自身与 1 处 javadoc 提及）
```

**严重程度**：P2

**现状**：两个工厂类的 `@Deprecated getShardCount()` 中，MemoryStateBackend 版本全仓 0 调用；RocksDBStateBackend 版本仅被自身模块 1 个测试（TestRocksDBBackendSkeleton:38）调用——生产调用方实际调用的是 `MemoryKeyedStateBackend.getShardCount`/`RocksDBKeyedStateBackend.getShardCount`（包级，非 deprecated）。`WatermarkEstimator` 是 Stage 49 D1 defer 的占位接口，javadoc 自述"保留接口以便 successor plan 实现时不破坏既有类查找"，但 0 实现 0 引用。

**风险**："为兼容保留"却无人兼容——deprecated 名义使读者以为存在存量调用方因而加倍小心；真正的内部命名（shard）继续扩散（见 RD-13）。占位接口若无 successor plan 跟踪，会长期滞留。

**建议**：直接删除 MemoryStateBackend.getShardCount；RocksDBStateBackend.getShardCount 将 TestRocksDBBackendSkeleton 改断言 `getMaxParallelism()` 后删除；WatermarkEstimator 若无对应 backlog 条目则删除（javadoc 中的 connector-design.md §4.0 D1 已充分记录 defer 决策，删除后仍可从设计文档恢复）。

**信心水平**：高

**误报排除**：已确认包级 `MemoryKeyedStateBackend.getShardCount`（:564）/`RocksDBKeyedStateBackend.getShardCount`（:466）有存活调用方（MemoryStateSerDe:100、RocksDBSnapshotSerDe:184/284），不在删除范围。

**复核状态**：未复核

---

### [RD-05] LocalSourceCoordinator 诊断 API 返回恒空数据且零调用，构成误导性死代码

**文件+行号**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:294, 315-335`

**证据片段**：
```java
323:    public Map<Integer, List<String>> getPendingSplitIdsByReader() {
325:        Map<Integer, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<Integer, ReaderChannel<T>> entry : readerChannels.entrySet()) {
                List<String> ids = new ArrayList<>();
                // Snapshot via reflection-free path: pendingCount tells us if any are buffered
                // but not their ids (the channel doesn't expose them to the outside). Tests
                // that need ids should wait for delivery ...
                result.put(entry.getKey(), ids);     // 永远放入空列表
320:    }
315:    /** Returns a snapshot of currently registered reader subtask indices. Used in tests to verify wiring. */
316:    public Set<Integer> getRegisteredReaders() { ... }   // javadoc 声称 "Used in tests"，实际 0 测试引用
```

**严重程度**：P2

**现状**：三个诊断方法（getFinishedSplits:294、getRegisteredReaders:315、getPendingSplitIdsByReader:323）全仓 0 调用。其中 `getPendingSplitIdsByReader` 的返回值语义已被掏空：对每个 reader 恒放空列表（注释自承拿不到 id），方法名与返回内容不符。

**风险**：调用者（若未来接线）会以为拿到的是 pending split id 集合，实际恒为空——典型的"看起来有观测、实际无观测"；javadoc "Used in tests" 与事实（无测试使用）不符，误导维护者以为删了会破坏测试。

**建议**：删除三个方法；若确需 pending-split 观测，让 ReaderChannel 暴露真实 id 快照后再提供该方法（名称与内容一致）。

**信心水平**：高

**误报排除**：已排除测试引用（全仓含 src/test 扫描为 0）；已排除反射（内部类私有字段访问不走这三个 public 方法）。

**复核状态**：未复核

---

### [RD-06] 同一配置概念两个家：SharedBufferCacheConfig.cacheStatisticsInterval 是死配置面，真实读取走 NopCepConfigs

**文件+行号**：`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/configuration/SharedBufferCacheConfig.java:35,51-53`；对照 `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:627-628`

**证据片段**：
```java
// SharedBufferCacheConfig.java:51
public Duration getCacheStatisticsInterval() { return cacheStatisticsInterval; }   // 全仓 0 调用
// CepOperator.java:627（真实的统计间隔来源）
private void registerCacheStatisticsTimer() {
    java.time.Duration interval = NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL.get();
    this.cacheStatsIntervalMs = interval.toMillis();
```
同文件中 `getEventsBufferCacheSlots/getEntryCacheSlots` 存活（SharedBuffer.java:202/210 调用），仅 interval 这一项无消费者。

**严重程度**：P3

**现状**：`SharedBufferCacheConfig` 构造时从 `CEP_CACHE_STATISTICS_INTERVAL` 全局配置读入 interval 并存为字段，但运行时统计定时器（CepOperator）绕过该 config 直接再读全局配置。字段+getter 形成第二份永不消费的配置面。

**风险**：运维若通过定制该 config bean（@DataBean 可被注入/替换）调整统计间隔，不会生效——配置改了没反应；同类概念两个家还会随时间漂移出不同默认值。

**建议**：删除 `cacheStatisticsInterval` 字段+getter（构造器第三参数同步移除，或保留构造签名但字段只透传给真正消费方）；或让 SharedBuffer/CepOperator 统一经由 SharedBufferCacheConfig 消费三项配置，二选一。

**信心水平**：高

**误报排除**：已排除测试消费（TestCepOperatorCacheStatistics:190 构造该 config 但断言走的是 operator 行为而非该 getter）；已排除 Nop JSON 序列化消费（该对象从不序列化出进程）。

**复核状态**：未复核

---

### [RD-07] StreamBackendCapability 的三个能力维度与四个访问器无任何消费者，能力模型名存实亡

**文件+行号**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamBackendCapability.java:29-62`；唯一消费方 `StreamRequirementValidator.java:31-40`

**证据片段**：
```java
32:    private final boolean supportsDistributedExecution;   // 构造后无人读
33:    private final boolean supportsRemoteStateService;     // 构造后无人读
34:    private final boolean supportsRescale;                // 构造后无人读
43:    public Set<StreamRequirement> getSupportedRequirements() { ... }          // 0 调用
47:    public boolean isSupportsDistributedExecution() { ... }                   // 0 调用
51:    public boolean isSupportsRemoteStateService() { ... }                     // 0 调用
55:    public boolean isSupportsRescale() { ... }                                // 0 调用
// StreamRequirementValidator.validate(...) 唯一消费：capability.supports(requirement)
```
全仓构造点：`StreamExecutionEnvironment.java:377` 与测试，均传 `localRuntime()`（后三参恒 false）。

**严重程度**：P3

**现状**：能力模型声明了分布式执行/远程状态服务/rescale 三个维度，但唯一校验路径只读 `supportedRequirements` 集合；三个布尔维度连同四个访问器构成"声明了能力、无人裁决能力"的空转结构。

**风险**：读者会以为选择分布式执行后端时会经过 `isSupportsDistributedExecution` 裁决，实际不存在该裁决点——能力声明与运行时行为脱节；后续实现者可能在错误假设上叠加逻辑。

**建议**：短期删除三个布尔字段+四个访问器（保留 `supports(StreamRequirement)` 与 `localRuntime()`）；待分布式执行裁决点真正落地时再按需恢复字段（模型很薄，恢复成本为零）。

**信心水平**：高

**误报排除**：已排除 @DataBean JSON 序列化消费（该对象仅在内存中传给 validator，从不序列化）；已排除测试消费（仅构造，不读访问器）。

**复核状态**：未复核

---

### [RD-08] import 分组惯例分裂：338 个文件 java-first vs 286 个 io.nop-first，且存在 static import 插入分组中部的明确违规

**文件+行号**（违规样例）：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:20-30`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamRequirementValidator.java:24-27`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperatorFactoryImpl.java:10-26`（io.nop 组内乱序）

**证据片段**：
```java
// TwoPhaseCommitSinkFunction.java:20-30（static 插在 io.nop 与 java 之间，java.* 先于 org.slf4j）
import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_UNSUPPORTED;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 子模块分布实测：core 186 java-first/139 nop-first、cep 43/20、rocksdb 18/1、flow 4/50、runtime 45/56
```

**严重程度**：P3

**现状**：AGENTS.md 约定 `io.nop.* → third-party → java.*`、静态导入最后，并称"与代码库实际惯例一致"；实测 nop-stream main 中 338 个文件以 `import java.*` 开头、286 个以 `io.nop.*` 开头、25 个以第三方开头——模块内部本身就分裂，且约半数文件与文档约定相悖（core/cep/rocksdb 为 Flink 移植风格 java-first，flow/sql 偏平台惯例）。另有一批文件把 static import 混排在非静态分组中间、或 java.* 置于 org.slf4j 之前（如上例）。

**风险**：无法靠肉眼或单一 grep 规则统一 review import；IDE 按 AGENTS.md 规则 optimize imports 会对半数文件产生大面积 diff，掩盖真实改动（与"避免 noisy refactors"直接冲突）。

**建议**：先在模块内做一次裁决（建议：对外贡献面大的 core/cep 统一为文档惯例 io.nop-first，配 Checkstyle/ImportOrder 规则固化），或反过来修订 AGENTS.md 承认 java-first 为 nop-stream 局部惯例——两个方向都可，关键是消除"文档与代码各执一词"。static import 混排属无争议违规，可随下次触碰对应文件时顺手修复（避免专门跑一次全量格式化）。

**信心水平**：高（分布数据来自全量脚本统计）

**误报排除**：未把"组内字母序"计入违规（AGENTS.md 只约定分组不约定组内排序）；`_gen/`、quickstart 模板（@packagePath@）已排除。

**复核状态**：未复核

---

### [RD-09] StreamModelDslBuilder 将约 320 行 DAG 校验套件与转换构建逻辑混装，可分离出独立校验器

**文件+行号**：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:457-780`（校验块），对照 :805-1082（构建块）

**证据片段**（方法行号实测）：
```java
457:  private List<StreamEdgeModel> validateDag(...)
511:  private void validateSinkReachability(...)
571:  private List<StreamTransformModel> topologicalOrderTransforms(...)   // 约 211 行段起点
628:  private void validateEdgeDeclarations(...)
691:  private void validateJoinDeclarations(...)
782:  private static int countKeyExprs(...)
-- 构建侧 --
805:  private Object buildTransform(...)
863:  private <T> DataStream<T> buildSource(...)
934-989: buildMap/buildFilter/buildFlatMap/buildKeyBy/buildSink
```

**严重程度**：P3

**现状**：validateDag → validateSinkReachability → topologicalOrderTransforms → validateEdgeDeclarations → validateJoinDeclarations 五个方法构成一条自足的静态校验流水线（输入 StreamModel，输出错误/拓扑序），与 buildTransform 族的运行时构建职责无共享状态（均经参数传递），是天然的 `StreamModelGraphValidator` 提取对象。整个文件 1202 行中该块约占 1/4。

**风险**：校验规则调整（如新增 flow-control 约束）与构建逻辑调整落在同一文件，review 与测试都互相牵连；文件头部 import 面（校验用 vs 构建用）混杂。

**建议**：提取 `io.nop.stream.flow.validate` 包下 `StreamModelDagValidator`（该包已存在 StreamConfValidator，归属自然）；DslBuilder 保留调用点。纯移动式重构，无行为变化。

**信心水平**：中高（方法行号与参数传递方式实测；未验证私有工具方法是否有隐藏共享字段——从签名看均静态/参数化）

**误报排除**：不把"行数多"本身作为问题报告；该文件不在 >1000 行但职责清晰的名单内被判可分——依据是校验块自成闭环且与构建块无状态共享。

**复核状态**：未复核

---

### [RD-10] CheckpointCoordinator 内嵌持久化执行块 + 双 setAbortHandler 重载（"Legacy"自标签）

**文件+行号**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:668-940, 1056, 338-348`

**证据片段**：
```java
668:  private synchronized void completePersistSynchronously(...)
697:  private void executePersistAsync(...)
732:  private void executeIncrementalPersistAsync(...)
798:  private List<StateSegmentDescriptor> buildAndMaterializeSegments(...) throws IOException
848:  private void releaseIncrementalSegments(...)
868:  private IncrementalSnapshotResult extractIncrementalResult(...)
887:  private IncrementalSnapshotResult reconstructFromMap(...)
1056: private ExecutorService createPersistExecutor()
337:  /** Legacy abort callback without the reason (see {@link #setAbortHandler(BiConsumer)}). */
338:  public void setAbortHandler(java.util.function.Consumer<Long> handler) { ... }
346:  public void setAbortHandler(java.util.function.BiConsumer<Long, String> handler) { ... }
```

**严重程度**：P3

**现状**：协调器在触发/ACK/超时职责之外内嵌了完整的持久化执行器（同步/异步/增量三条持久化路径 + 段物化 + 线程池），约 270 行；该块与协调状态机的唯一耦合点是 CompletedCheckpoint 入参，可提取为 `CheckpointPersister`。同文件 `setAbortHandler` 双重载并存，单参版本自标签 "Legacy"，且生产代码 TaskCheckpointWiring:323 仍用单参版。

**风险**：增量持久化路径（executeIncrementalPersistAsync/reconstructFromMap）的失败语义需要在协调器锁（synchronized）内推演，隔离后可单独测试；"Legacy"重载与带 reason 重载并存让新调用方难以抉择（两个生产调用点已各选一边）。

**建议**：提取 `CheckpointPersister`（持有 persistExecutor，入参 CompletedCheckpoint/segments）；将 TaskCheckpointWiring:323 迁移到 BiConsumer 版本后删除 Consumer 版重载。

**信心水平**：中高（持久化块与状态机的耦合点经签名检查为 CompletedCheckpoint/segments 传值；未运行时验证）

**误报排除**：不报告 CheckpointCoordinator 的触发/ACK/超时主职责——那是协调器的本职。

**复核状态**：未复核

---

### [RD-11] 死常量簇：7 个 `public static final`（含 1 个 private）全仓 0 引用

**文件+行号**：
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingStrategy.java:58`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroup.java:48`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/watermark/Watermark.java:46`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/ClusterLaunchConfig.java:38`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamMetricsReporter.java:49-50`
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:107`（private）

**证据片段**：
```java
// Watermark.java:46
public static final Watermark UNINITIALIZED = new Watermark(Long.MIN_VALUE);   // 全仓唯一命中
// StreamMetricsReporter.java:49-50
public static final String KEY_INTERVAL_MS = "nop.stream.metrics.log.interval-ms";  // 全仓唯一命中
public static final String KEY_TARGET      = "nop.stream.metrics.log.target";       // 全仓唯一命中
// KeyGroup.java:48
public static final int UPPER_BOUND_MAX_PARALLELISM = 1 << 15;                 // 全仓唯一命中
```

**严重程度**：P3

**现状**：209 个 public static final 常量全量扫描后，以下 7 项在全仓（java/xml/xpl/xdef/xmeta，含 test 与 benchmark）仅出现定义 1 次：`ChainingStrategy.DEFAULT_CHAINING_STRATEGY`、`KeyGroup.UPPER_BOUND_MAX_PARALLELISM`、`Watermark.UNINITIALIZED`、`ClusterLaunchConfig.KEY_COORDINATOR_ID`、`StreamMetricsReporter.KEY_INTERVAL_MS`、`StreamMetricsReporter.KEY_TARGET`、`AdvancedTransforms.XDEF_DEFAULT_ALLOWED_LATENESS`（private，第 2 轮深挖补充发现）。

**风险**：`KEY_INTERVAL_MS/KEY_TARGET` 形似配置键（字符串本身就是 "nop.stream.metrics.log.*" 形态），运维按键名写配置不会生效；`UPPER_BOUND_MAX_PARALLELISM` 暗示存在 1<<15 上界校验，实际无任何强制点；`Watermark.UNINITIALIZED` 与 `MAX_WATERMARK` 并排，读者会以为存在未初始化哨兵路径。

**建议**：删除 7 项；`UPPER_BOUND_MAX_PARALLELISM` 若意在文档化上界，改为在 `setMaxTargetParallelism` 类入口真正校验，或挪入 javadoc；`KEY_*` 若意在文档化配置键，将其纳入真实读取路径（StreamMetricsReporter 的配置解析处）或删除。

**信心水平**：高（口径：全仓 `\bNAME\b` 全词 grep 均仅定义 1 命中；CheckpointConfig.DEFAULT_* 等同形常量已核实为字段初始化器消费、不在列）

**误报排除**：`WatermarkAlignmentParams.WATERMARK_ALIGNMENT_DISABLED`（WatermarkStrategy:87 调用）、`MicrometerStreamTaskMetrics.METRIC_*/TAG_*`（同文件使用+测试断言）等初筛疑似项均已核实存活并排除。

**复核状态**：未复核

---

### [RD-12] 核心运行时存在一批 130–165 行的超长方法，集中在本轮已判超聚合的两个类及其协作方

**文件+行号**（行号为方法签名行，长度为括号内的大致跨度，均实测）：
- `WindowOperator.java:447 open()` 约 162 行、`:1686 mergeWindowContents()` 约 164 行
- `JobCoordinator.java:1422 detectFailures()` 约 151 行、`:1692 globalRecovery(cause)` 约 149 行
- `TaskManager.java:575 deployTask()` 约 135 行
- `SupervisionLoop.java:473 restartRegion()` 约 132 行
- `JobCoordinator.java:1031 reportTaskStatus()` 约 126 行

**证据片段**（以 detectFailures 为例的结构观察）：
```java
1422:  public void detectFailures() {          // 依次内联：
        // 节点租约过期判定 → 单任务存活判定 → stall 判定 → deploy grace 期豁免
        // → 每种失败各内联一段"触发恢复"的收敛逻辑（约 151 行单方法）
1573:  public void requestRecovery() { ... }
```

**严重程度**：P3

**现状**：全仓扫描（成员签名间距启发式 + 人工核对）得到 9 个 130+ 行方法，全部落在 nop-stream-runtime/core 的执行与协调核心；这些方法共同特点是"多分支失败/生命周期语义 + 大段解释性注释"，拆分收益真实但需配合 RD-01/RD-10 的类级拆分才不至变成搬运。

**风险**：单方法内多职责分支使断点调试与局部 review 困难；修改任一分支需理解全方法的状态推进顺序；长方法内的注释块承担了本应由私有方法名承担的结构表达。

**建议**：随 RD-01/RD-10 拆分时一并处理：`detectFailures` 按"租约/存活/stall"三类失败拆为三个判定方法；`open()`/`mergeWindowContents` 抽取 state-namespace 计算助手；`deployTask` 抽取 deployment descriptor 组装段。不要求专项重构，作为后续触碰这些方法的伴随项。

**信心水平**：中（行数用签名间距近似并人工抽查核对；精确到行级别的边界未逐行验证）

**误报排除**：gap 启发式会把 javadoc/嵌套类计入跨度，已人工核对上表各项确为方法体长度；StreamSqlCompiler.java:97 的 388 行 gap 经核对为多个独立方法 + 常量块，非单方法，已排除。

**复核状态**：未复核

---

### [RD-13] 命名一致性：shard/maxParallelism 双词汇并存（含废弃层），collectAck/receiveCheckpointAck 为接口适配双名

**文件+行号**：
- `MemoryStateBackend.java:92 / RocksDBStateBackend.java:106`（deprecated shard 名）vs `MemoryKeyedStateBackend.java:564 / RocksDBKeyedStateBackend.java:466`（存活 shard 名）vs `getMaxParallelism`（新名）
- `JobCoordinator.java:974 collectAck` vs `:1015 receiveCheckpointAck`（@Override IStreamCoordinatorRpcService）

**证据片段**：
```java
// JobCoordinator.java:1015
@Override
public void receiveCheckpointAck(CheckpointAckMessage ack) {
    collectAck(ack);        // 两个名字，一个语义
}
// shard 词汇实测：nop-stream main 中 shard 相关标识符 228 处
// （StateShard/ShardPrefixedKey/computeShardId/getStateShardCount/getShards/...），
// 而公共 API 已改用 maxParallelism/key-group 词汇
```

**严重程度**：P3

**现状**：(1) "shard" 与 "maxParallelism/key-group" 指同一概念（key-group 分片数），公共 API 已完成改名但内部 228 处 shard 词汇未跟进，且 deprecated `getShardCount` 的 javadoc 已解释等价关系——边界有文档但词汇未收敛；(2) `collectAck`/`receiveCheckpointAck` 是"领域名 vs RPC 接口名"的适配对，属可接受但无注释说明二者关系；(3) 其余动词族边界经抽查清晰：`close()`=资源（AutoCloseable）、`stop()`=组件循环、`shutdown()`=执行器/协调器终态、`terminate(mode)`=作业终止模式族、`restore*`=从检查点恢复状态、`recover*`=作业级失败恢复（TwoPhaseCommitSinkFunction.recover 事务恢复），未发现混用造成语义歧义的实点；(4) 错误码双族 `ERR_STREAM_*`（1372 处，NopStreamErrors）与 `ERR_CEP_*`（68 处，NopCepErrors）按子模块划分一致，与平台前缀习惯（模块缩写前缀）兼容，不构成问题。

**风险**：双词汇使 grep 维护成本翻倍（查 shard 漏 key-group、反之亦然）；新代码无从判断该用哪套词汇，shard 会继续扩散。

**建议**：在 `docs-for-ai` 流处理引擎一节或 NopStreamErrors/IKeyedStateBackend javadoc 中固定术语表（"shard == key group；新代码一律用 maxParallelism/key-group"）；`collectAck` 处补一行注释"RPC 入口 receiveCheckpointAck 委托至此"。词汇级重命名（228 处）不建议专项做，随自然触碰收敛。

**信心水平**：高（动词族边界经逐类抽查；shard 计数为实测）

**误报排除**：`TwoPhaseCommitSinkFunction.recover(checkpointId)` 参数未使用（委托 rollback+beginTransaction）曾疑似命名问题，经查其语义为"按 epoch 恢复事务上下文"，参数预留与 restoreFromEpoch 语义分层有关，不属于本维度缺陷（参数未用问题归接口设计维度）。

**复核状态**：未复核

---

## 深挖第 2 轮追加

第 2 轮围绕第 1 轮发现的模式做同类扫描，结论如下（新增发现 2 项已并入 RD-03/RD-11 表格并标注）：

1. **同类"死入口"扫描**：对全部 `start*/open*/register*/trigger*/init*/install*` 非 @Override public 方法做 0 引用扫描（全仓 java 语料），命中 3 项：`registerReader`（LocalSourceCoordinator:141，2 次命中=定义+1 处存活调用）、`registerCommitQueueDepthGauge`（TaskNodeMetrics:148，2 次命中=定义+存活调用）、`startGovernance`（1 次命中，唯一真死入口）。**结论：RD-02 不是孤例模式泛滥，是单点缺陷。**
2. **declarativeRegistries 链路复核**（防 RD-03 误杀活特性）：`StreamExecutionEnvironment.java:127` 调用 `mergeDeclarativeRegistries(declaredRegistries)`，测试 TestStreamComponentsRegistryWiring 经 `getDeclarativeEntry` 读回——特性本身存活；仅 `addDeclarativeEntry/getDeclarativeRegistries/getStateMigrationFunctions` 三个访问器变体 0 调用，保留在 RD-03 #33。
3. **@SuppressWarnings 密集区检查**：全模块 305 处（unchecked 291 + rawtypes 14），密度最高为 WindowOperator(22)/MemoryStateSerDe(18)/RocksDBSnapshotSerDe(17)/NFACompiler(14)；抽查未发现用 @SuppressWarnings 掩盖真实逻辑错误的区域，未立案。
4. **DTO setter 排除复核**：对 RPC DTO（CheckpointAckMessage/TaskStatusReport/JobStatusResponse/TaskDeploymentDescriptor 等）的 0 调用 setter 逐一确认其类参与 RPC/JSON 反序列化往返（如 CheckpointAckMessage 由 CheckpointAckSender 发送、协调端接收），全部排除出死代码表——避免"误杀反序列化钩子"。
5. **补充死常量**：`AdvancedTransforms.XDEF_DEFAULT_ALLOWED_LATENESS`（private，:107，0 引用）在第 2 轮验证 RD-03 时发现，已并入 RD-11。
6. **深挖确认无问题项**：printStackTrace=0、TODO/FIXME=0、通配符 import=0、空 catch 块=0、>5 行注释掉的代码块=0（145 个长注释块逐一过滤后均为解释性注释）；StreamTaskInvokable 8 处、RocksDBKeyedStateBackend.close 8 处、StreamOpsHttpServer 5 处 catch 均为"捕获→汇总/转换→重抛或响应"的有意模式，非吞异常；61 个吞异常原始命中中 53 个为中断恢复/数值回退默认值模式，其余已逐一并入上述结论。

## 最终保留项（表格索引）

| ID | 严重程度 | 一句话描述 | 主要位置 |
|---|---|---|---|
| RD-01 | P2 | JobCoordinator 承担选主/分配/检查点门禁/存活/恢复/终止 6 类可分离职责 | runtime/coordinator/JobCoordinator.java |
| RD-02 | P2 | startGovernance() 零调用：治理清扫与历史修剪生产永不运行，配置键无效果 | runtime/ops/OpsJobManager.java:326 |
| RD-03 | P2 | 约 35 个 public 方法全仓 0 调用（含误导性测试诊断访问器） | 分散（见表） |
| RD-04 | P2 | @Deprecated getShardCount×2 与 WatermarkEstimator 占位接口零消费者 | core/rocksdb/connector |
| RD-05 | P2 | getPendingSplitIdsByReader 恒返回空列表的误导性死诊断 API | core/source/coordinator/LocalSourceCoordinator.java:323 |
| RD-06 | P3 | cacheStatisticsInterval 死配置面，真实路径直读 NopCepConfigs | cep/configuration/SharedBufferCacheConfig.java:51 |
| RD-07 | P3 | StreamBackendCapability 三个能力维度+四个访问器无消费者 | core/model/StreamBackendCapability.java:29 |
| RD-08 | P3 | import 惯例分裂 338/286/25 + static import 混排违规 | 全模块（样例 TwoPhaseCommitSinkFunction.java:20） |
| RD-09 | P3 | StreamModelDslBuilder 内嵌约 320 行可分离 DAG 校验套件 | flow/builder/StreamModelDslBuilder.java:457-780 |
| RD-10 | P3 | CheckpointCoordinator 内嵌持久化执行块 + Legacy setAbortHandler 双重载 | runtime/checkpoint/CheckpointCoordinator.java:668-940,338 |
| RD-11 | P3 | 7 个死常量（含形似配置键的 KEY_* 与暗示校验的 UPPER_BOUND） | core/runtime/flow（见表） |
| RD-12 | P3 | 9 个 130–165 行超长方法集中于运行时核心 | WindowOperator/JobCoordinator/TaskManager/SupervisionLoop |
| RD-13 | P3 | shard/maxParallelism 双词汇并存；collectAck/receiveCheckpointAck 双名适配 | core/rocksdb（228 处 shard 词汇） |

**分布统计**：P2 × 5（RD-01~05），P3 × 8（RD-06~13）。本轮卫生面整体优秀（printStackTrace/TODO/空 catch/注释代码块/通配符 import 均为 0），结构性问题集中在 runtime 协调核心的超聚合与公共 API 面的死代码腐化。

---

## 维度复核结论（独立复核员，2026-10-06）

复核方法：对全部死代码类发现做独立反证搜索（全仓无文件类型过滤 grep、静态导入/方法引用/字符串反射/XML-XPL 资源引用/nop-benchmark/nop-stream 之外模块六路排查）；对结构类发现逐行核对方法地图与职责块边界；对 RD-08 用独立脚本全量重算；对 RD-12/13 抽样实测行号。历史对照仅读 R5 summary.md。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| RD-01 | 保留（P2） | 存量未修（R5 同族项已登记，按惯例待后续治理批次） | 2866 行实测精确；抽查 18 个方法行号（start:572…terminate:2293）全部逐行命中；六职责块各有独立关注点与并发面（fencing epoch 轮转/recoveryLock+liveness map/checkpoint ACK 门禁/终止模式族），结构分离依据成立，非"行数多"式立案 |
| RD-02 | 保留（P2，反证后更强） | 存量未修（R5 同族项已登记） | 全仓无类型过滤 grep 仅命中定义+文档；无 XML/XPL/beans/反射字符串命中；StreamOpsHttpServer 无 governance endpoint；`pruneOldestCheckpointHistory` 生产唯一调用链确认在 sweepOnce 内（OpsJobManager:381/384）。**新增加重证据**：`ai-dev/design/nop-stream/distributed-runbook.md:147` 以现在时文档化"`startGovernance()` 启动扫描"及各保留期默认值——文档宣称已生效与代码死入口构成活的 doc-contract 冲突，"看起来有治理"风险坐实 |
| RD-03 | 保留（P2，剔除 1 项） | 存量未修（R5 同族项已登记） | 独立复核 32/35 个表项：全部仅定义 1 次命中（含 nop-benchmark、静态导入、XML/XPL、nop-stream 之外全仓），无一例翻案。**但 #31 `JoinSideRecord.setEquiKey` 须剔除**：该类是 `@DataBean`+Serializable，javadoc（:39-43，R5 KR-7 修复引入）明确自述"no-arg constructor + setters so the buffer's serializable form can re-hydrate values after a JSON round trip (parseBeanFromText contract)"——正是初审自身误报排除标准中的反序列化钩子，初审"非序列化类"判断有误。实际死方法约 34 个，结论量级不变 |
| RD-04 | 降级 P2→P3 | 存量未修 | 事实全部核实：MemoryStateSerDe:100 的 backend 静态类型为 `MemoryKeyedStateBackend<?>`、RocksDBSnapshotSerDe:184/284 为 `RocksDBKeyedStateBackend<?>`——deprecated 公共版本确无生产调用；TestRocksDBBackendSkeleton:38 确为 RocksDBStateBackend 版本唯一调用者；WatermarkEstimator 0 实现 0 引用。**降级理由**：(1) 实际面仅 2 个单行别名方法+1 个空接口，无运行时效应；(2) WatermarkEstimator 并非无主占位——connector-design.md §4.0 D1 与 plan 2026-08-04-0900-1 明确记录"defer（v1 Non-Goal successor）、Successor Required: yes"，属有据可查的裁定保留，初审"删除"建议与该裁定冲突（其建议中"若无对应 backlog 条目则删除"的条件实际上不成立）。初判 P2 虚高 |
| RD-05 | 保留（P2） | 存量未修（R5 同族项已登记） | 三方法 0 调用独立复现；getPendingSplitIdsByReader（:323-333）实测对每个 reader 恒 put 空 ArrayList、注释自承拿不到 id——方法名与返回内容不符属实；getRegisteredReaders javadoc "Used in tests" 而 0 测试引用属实。与 RD-03 #23 是同组成员，本条独立立案的增量理由（恒空语义陷阱）成立 |
| RD-06 | 保留（P3） | 疑同 R5 RD-6（两轮 07 报告编号对齐），R5 已将其归入 plan 2317 文档批次 | getter+字段 0 消费独立复现；CepOperator:627 直读 `NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL` 属实；SharedBuffer.java:202 确认只用 getEventsBufferCacheSlots；类标 `@DataBean`（:31）可注入替换——"定制不生效"风险成立 |
| RD-07 | 保留（P3） | 存量未修 | 4 个访问器全仓 0 调用独立复现；三个布尔字段仅在构造器写入、全类无读取点（supports() 只读 supportedRequirements）；构造点仅 `localRuntime()`（StreamExecutionEnvironment:377+测试），三布尔恒 false 属实；对象仅在内存传给 validator、无序列化出进程路径，@DataBean 序列化误报排除成立 |
| RD-08 | 保留（P3） | 存量未修 | 独立脚本全量重算**精确复现**：nop-stream main java-first=338 / nop-first=286 / 第三方-first=25；分模块 core 186/139、cep 43/20、rocksdb 18/1、flow 4/50、runtime 45/56 全部逐项吻合。TwoPhaseCommitSinkFunction 实测 static import 混排（:11-12 夹在 io.nop:10 与 java.*:13-15 之间、java.* 先于 org.slf4j:17、:20-25 第二个 io.nop 组）——模式属实但初审引用行号":20-30"不精确（实际 :10-18）；StreamRequirementValidator 实际 import 在 :10-22（初审引":24-27"不精确）。另：初审把 WindowOperatorFactoryImpl"io.nop 组内乱序"列为违规样例，与其误报排除自述"组内字母序不计违规"自相矛盾（组内乱序不构成违规证据），但主论点（分组惯例分裂）不受影响 |
| RD-09 | 保留（P3） | 存量未修 | 文件 1202 行精确；6 个校验方法行号（457/511/571/628/691/782）全部命中；校验块经参数传递（byId/upstreams/edges 入参）、与 buildTransform 族（805 起）无共享状态可见——可分离性成立 |
| RD-10 | 保留（P3） | 存量未修 | 8 个持久化块方法行号（668/697/732/798/848/868/887/1056）全部精确命中；setAbortHandler 双重载 :338/:346 与 "Legacy" javadoc :337 逐字吻合；生产调用点实测 JobCoordinator:2537 用 BiConsumer 版、runtime/execution/TaskCheckpointWiring.java:323 用 Consumer（Legacy）版——"两个生产调用点各选一边"属实（初审引用缺包名 execution/，行号正确） |
| RD-11 | 保留（P3） | 存量未修 | 7 个常量逐一独立 grep（含静态导入、XML、benchmark、全仓）：全部仅定义 1 次命中。KEY_INTERVAL_MS/KEY_TARGET 风险**被初审低估而非虚高**：`nop.stream.metrics.log.*` 键出现在 metrics.properties.template（:39-49）与 TestMetricsConfigTemplate 断言中，但全仓无任何代码读取这些键——StreamMetricsReporter 仅被测试以硬编码参数构造（TestMetricsExposureE2E:190），整个模板键面都是装饰性的 |
| RD-12 | 保留（P3，计数修正） | 存量未修 | 抽查 5 个样本：open 447-606≈160 行 ✓、mergeWindowContents 1686-1838+≈153+ 行（近似 ✓）、deployTask 575-701≈127 行（近似 ✓）、detectFailures 1422-1573≈151 行 ✓、reportTaskStatus 1031-1157≈126 行 ✓。**修正**：restartRegion 实测 473-578≈106 行，非初审声称的约 132 行（签名间距启发式吞入了后续 javadoc 块），"9 个 130+ 行方法"应为 8 个。方法簇本身存在，结论保留 |
| RD-13 | 保留（P3） | 存量未修 | receiveCheckpointAck(:1015)→collectAck(:974) 委托关系逐行确认、确无注释说明配对；shard 词汇量独立计数约 296 处（core+rocksdb+runtime main，初审 228 为较窄口径），量级与结论一致；deprecated getShardCount javadoc 等价关系已在 RD-04 复核中确认存在 |

复核统计：13 条全部保留（0 驳回），1 条降级（RD-04 P2→P3），3 条带修正备注（RD-03 剔除 #31、RD-08 样例行号不精确、RD-12 计数 9→8）。死代码类发现经六路反证搜索无一例主结论翻案，仅 RD-03 #31 一项属初审自身误报排除标准内的漏排；RD-02 因设计文档宣称生效而证据进一步加强。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| RD-01 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java | 2866 行 God Coordinator：选主/fencing、分配、检查点门禁、存活跟踪、失败恢复、终止模式六职责共享可变字段，应按块拆分 | 存量未修（R5 同族已登记，待治理批次） |
| RD-02 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java:326 | startGovernance() 零调用致治理清扫与历史修剪生产永不运行，且 distributed-runbook.md:147 已文档化为生效行为 | 存量未修（R5 同族已登记） |
| RD-03 | P2 | 分散（core/runtime/cep/rocksdb/connector，约 34 项清单见初审表，剔除 #31 setEquiKey） | 约 34 个 public 方法全仓 0 调用（32 项逐一经反证搜索核实），公共 API 面系统性腐化 | 存量未修（R5 同族已登记） |
| RD-04 | P3（降级） | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateBackend.java:93 等 | 两个 @Deprecated getShardCount 别名零生产消费；WatermarkEstimator 为有裁定记录的 defer 占位（不建议贸然删除） | 存量未修 |
| RD-05 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:323 | getPendingSplitIdsByReader 恒返回空列表且三诊断方法零调用，javadoc "Used in tests" 与事实不符 | 存量未修（R5 同族已登记） |
| RD-06 | P3 | nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/configuration/SharedBufferCacheConfig.java:51 | cacheStatisticsInterval 死配置面，真实读取绕过 config 直读 NopCepConfigs，@DataBean 定制不生效 | 疑同 R5 RD-6，已归 plan 2317 文档批次 |
| RD-07 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamBackendCapability.java:29 | 三个能力布尔维度构造后无人读、四个访问器零调用，能力声明与运行时裁决脱节 | 存量未修 |
| RD-08 | P3 | 全模块（总量 338 java-first / 286 nop-first / 25 其他，已精确复现） | import 分组惯例分裂过半 + static import 混排违规，IDE 统一格式化将产生大面积噪音 diff | 存量未修 |
| RD-09 | P3 | nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:457-780 | 约 320 行自足 DAG 校验流水线与构建逻辑同文件，可纯移动式提取 StreamModelDagValidator | 存量未修 |
| RD-10 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:668-940,338 | 协调器内嵌约 270 行持久化执行块可提取 CheckpointPersister；Legacy setAbortHandler 单参版仍被 TaskCheckpointWiring:323 使用 | 存量未修 |
| RD-11 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamMetricsReporter.java:49-50 等 7 处 | 7 个死常量零引用；metrics.properties.template 的 nop.stream.metrics.log.* 键整体无消费面（比初审描述更严重） | 存量未修 |
| RD-12 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:447 等 | 8 个（复核修正，原 9）约 105-160 行超长方法集中于运行时核心，随 RD-01/RD-10 拆分伴随处理 | 存量未修 |
| RD-13 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:974,1015 | shard/maxParallelism 双词汇并存（约 300 处 shard 标识符）；collectAck/receiveCheckpointAck 双名适配无注释 | 存量未修 |
