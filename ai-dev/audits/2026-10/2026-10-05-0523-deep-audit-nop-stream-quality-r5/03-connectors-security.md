# 深度审计 R5 · 报告 03：nop-stream 连接器族正确性 + 安全审计

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`（工作树中无 nop-stream / nop-message 相关未提交改动）
- **范围**: `nop-stream/nop-stream-connector`（FileSource / FileSourceReader / FileTwoPhaseCommitSink / Message 源汇 + codec 面）、`nop-stream-connector-batch`、`nop-stream-connector-jdbc`（2PC sink、dmlMode、upsert 方言、JDBC checkpoint storage）、`nop-stream-connector-debezium`（+ `nop-message/nop-message-debezium` 包装层：DebeziumMessageSource / DebeziumEngineWrapper / NopStreamOffsetBackingStore / DebeziumEngineConfig / DebeziumEventConverter / DebeziumConfig / ChangeEvent）、`nop-stream-core` 的 `io.nop.stream.core.connector`（registry / catalog / prober / lookup）与 `io.nop.stream.core.credentials`，以及连接器契约所依赖的 `TwoPhaseCommitSinkFunction` / `StreamSinkOperator.processBarrier`。
- **方法**: 只读静态审计，以 live code 为准；逐文件全文阅读连接器族 main 源（约 30 个生产文件）+ 契约基类 + 5 个 beans.xml + SPI 描述符；对每个历史缺陷族（R5-CON-01/02/03/05、AR-03/AR-13/AR-14/AR-15、P1-9/15、R5-CON-07/08/10、WI7）逐条在 live 代码中定位修复点并验证其完备性；交叉核对 `docs-for-ai/03-modules/nop-stream-connectors.md`、`nop-stream-cdc-cookbook.md` 声明与代码/测试三方一致性；`escapeSQLName` 注入面下钻至 `DialectImpl` / `StringHelper` 实现。未运行 maven/测试（纪律约束），测试矛盾以文本级证据钉定。不审计 `_` 前缀生成产物。

---

## 发现总览

| 严重程度 | 数量 |
|---|---|
| P0 | 0 |
| P1 | 1 |
| P2 | 5 |
| P3 | 3 |

---

### [CON-01] 能力矩阵「代码级单一事实源」钉定测试与 live 描述符矛盾：debezium-cdc 并行度声明 SINGLE_INSTANCE vs PARALLEL
- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/connector/TestStreamConnectorRegistryDiscovery.java:132-135`；对照 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceConnectorFactory.java:36-38`
- **证据片段**: ```java
// TestStreamConnectorRegistryDiscovery.java（HEAD 现状）
assertDescriptor(ConnectorDirection.SOURCE, "debezium-cdc",
        "io.nop.stream.connector.debezium.DebeziumCdcSourceFunction",
        "REPLAYABLE", ConnectorParallelism.SINGLE_INSTANCE,
        ConnectorRecoverySemantic.OFFSET_CHECKPOINT, "config");
// DebeziumCdcSourceConnectorFactory.java（HEAD 现状，commit 1dad44a0c6 2026-10-02）
.parallelism(ConnectorParallelism.PARALLEL)
```
- **严重程度**: P1
- **现状**: `assertEquals(parallelism, d.getParallelism(), typeName)`（同测试 `:165`）直接比较枚举；`assertDescriptor` 无豁免路径。工厂于 2026-10-02（`1dad44a0c6`，WI4 B 层并行）把描述符 `SINGLE_INSTANCE -> PARALLEL`，但该钉定测试最后一次修改（`05e81257e6`，2026-10-03）未同步此断言。两者在 HEAD 不可同时成立。
- **风险**: 该测试类 Javadoc 与 `docs-for-ai/03-modules/nop-stream-connectors.md` 头部均声称它是能力矩阵的「代码级单一事实源」。现状二选一：测试当前必红（验证信号失效/被忽略——该提交消息只声明 "core 1652 + connector 50 测试全绿"，钉定测试宿主是 `nop-stream-runtime`），或该测试实际未进入验证路径。无论哪种，「矩阵被测试钉定」这一护栏声明已失效，后续任何能力声明回归（并行度/一致性/恢复语义翻转）都无法被该测试拦截，正是连接器族最依赖的回归面。
- **建议**: 把断言更新为 `ConnectorParallelism.PARALLEL`（与 live 工厂、owner doc 一致），并在 CI 路径上确认 `nop-stream-runtime` 测试套件被执行；同时把「描述符翻转必须同步钉定测试」写进 connectors.md 的维护规则。
- **信心水平**: 确定（文本级矛盾可直接验证；「测试必红」推断为很可能）
- **误报排除**: 不是「测试过期无人跑」的常见误报——两侧文件都在 HEAD 且未被工作树修改（`git status` 无 nop-stream 条目）；`assertDescriptor` 的枚举等值断言无任何版本化/条件豁免；beans 发现断言确认 `connector-debezium.beans.xml` 在发现集内，`find()` 解析到的就是该 live 工厂的描述符，不存在第二描述符来源。
- **复核状态**: 未复核

---

### [CON-02] DebeziumEngineWrapper.stop() 在引擎异步启动窗口内早退，泄漏已启动的引擎（binlog 连接 + 全局 worker 线程）
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:152-166, 188-204`
- **证据片段**: ```java
GlobalExecutors.globalWorker().execute(() -> {
    try {
        running.set(true);            // 在 worker 线程内才置位
        engine.run();
        ...
public synchronized void stop() {
    if (!running.get() || stopped.getAndSet(true)) {
        return;                       // running 尚未置位时直接早退，engine.close() 不会执行
    }
    ... engine.close();
```
- **严重程度**: P2
- **现状**: `start()` 把 `engine.run()` 提交到全局线程池，`running` 在 worker 线程内部才置 true。若 `stop()`（经 `DebeziumMessageSource.stop()` ← `Subscription.cancel()` / `DebeziumCdcSourceFunction.cancel()`）落在 `execute()` 之后、worker 实际执行之前，`!running.get()` 命中早退；随后 worker 正常执行 `engine.run()`，没有任何后续调用方会再关闭该引擎。
- **风险**: 任务取消/快速关停命中启动窗口时，嵌入引擎在任务死亡后继续运行：占住一个全局 worker 线程、持有源库 binlog/复制连接，持续消费且无人消费其事件；对 MySQL 还占着 server id 注册。失败监听此时已被 `run()` 的 finally 注销，泄漏完全静默。
- **建议**: 用独立于 `running` 的启动标记控制 stop 早退（如 `started.getAndSet(true)` 在 `start()` 同步段置位，`stop()` 依据它决定是否 close），或在 worker lambda 内检查 `stopped` 标志后自行 `engine.close()`。
- **信心水平**: 确定（竞态窗口存在性由代码顺序直接可证；实际触发频率低）
- **误报排除**: 不是「理论竞态、实际不可达」——`cancel()` 恰恰是「尽快停止」路径（配置探测后立即失败、drain、作业关停都会在启动后极短时间内触发 cancel），窗口虽小但路径真实；且该早退分支的本意（引擎已死则免 close）与启动窗口场景共用同一判断，是结构性混淆而非风格问题。
- **复核状态**: 未复核

---

### [CON-03] NopStreamOffsetBackingStore 恢复为 put-merge 而非 replace：同 JVM 恢复时 registry 中较新的分区级 offset 幸存，可造成跳读丢数据
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:85-90, 189-199`；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:393-398`
- **证据片段**: ```java
// initializeState（有 checkpoint 的恢复路径）—— 不 clear，直接绑到既有共享 map：
this.offsetStore = NopStreamOffsetBackingStore.forConnector(resolveConnectorName());
this.offsetStore.setOffsets(restored);
// forConnector: REGISTRY.computeIfAbsent(connectorName, ...) —— JVM 内上次运行留下的 map 原样复用
// setOffsets: 逐条 data.put(...) —— 仅覆盖 restored 中存在的键，多余的陈旧键全部幸存
```
- **严重程度**: P2
- **现状**: AR-03 的 `clearConnector` 只在「无 checkpoint / checkpoint 无 `cdc-offsets` 条目」两条首启路径调用（`DebeziumCdcSourceFunction.java:370-385`）。恢复路径对同一 connector name 绑定到 static registry 中**既有的** map 并 put-merge。同分区键（server+partition 相同字节）会被恢复值覆盖，但 restored map 中缺失的分区键保留 registry 里的较新 offset。
- **风险**: 同 JVM 内 region restart 且从较老 checkpoint 恢复时：若崩溃前引擎已为某分区（如新表被通配 include-list 捕获、或 B 层重分片后的新分片键）提交过 offset，而最后一次 durable checkpoint 早于该 offset 的产生，恢复后该分区直接从 registry 的较新位点续读——checkpoint 之后、崩溃之前产生且未及提交下游的那段变更被**静默跳过**（违反 REPLAYABLE 的「从 checkpoint 位点续读」契约；2PC sink 无法找回从未投递的数据）。触发条件为三重叠加（同 JVM 恢复 + 崩溃落在两个 checkpoint 之间 + 分区键在 checkpoint 后新增），概率低但损失方向是不可再生的丢失。
- **建议**: 有 checkpoint 的恢复路径同样先 `clearConnector(name)` 再 `setOffsets`（replace 语义），使 nop checkpoint 对 offset map 具有整表取代权；行为级测试补「registry 有较新分区键 + 从老 checkpoint 恢复 → 引擎从 checkpoint 位点/快照开始」。
- **信心水平**: 很可能（merge 语义确定；丢失场景需特定三重条件）
- **误报排除**: 不是 AR-03 的重复报告——AR-03 只钉定了整体缺失路径（state==null / raw==null），本条是**非空恢复路径**的 merge-vs-replace 缺口，两者互补；也不是「restored 覆盖同键所以没问题」——同键覆盖成立，问题恰恰出在 restored 缺键时 registry 值幸存，这正是「checkpoint 早于该分区首个 offset 提交」的崩溃窗口。
- **复核状态**: 未复核

---

### [CON-04] File 2PC sink：epoch 数据文件在 commit 时才 fsync，且 recordCount 从不校验——OS 崩溃窗口内可静默提交截断文件
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:238-250, 301-317`；`FilePendingCommit.java:69-75`
- **证据片段**: ```java
// saveState：写 temp 文件，无 fsync；checkpoint 的 pendingCommits 只含 tempPath/count 元数据
writeLines(tempPath, currentBuffer);
getPendingCommits().put(epochId, new FilePendingCommit(tempPath.toString(), count, subtaskIndex));
// doCommitLocked：恢复重提交路径，此时才 forcePath —— 崩溃丢失的尾部已无法挽回
forcePath(tempPath);
Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
```
- **严重程度**: P2
- **现状**: durable checkpoint 只承载 `FilePendingCommit`（路径 + recordCount），epoch 数据唯一载体是 temp 文件；`writeLines`（BufferedWriter，无 force）在 saveState 内完成，而 fsync 推迟到 commit 的 rename 之前。进程崩溃安全（页缓存保留），但 OS 崩溃可丢失未刷盘尾部；恢复后的重提交把幸存内容原样 rename + 记 manifest——`recordCount` 字段在生产代码中零读取（grep 证实仅有反序列化 setter 路径）。
- **风险**: OS 级崩溃后，一个「已 committed」的 epoch 文件可能比 checkpoint 元数据声称的行数少，且无任何检出手段（exactly-once 的「不丢」在此窗口退化为不可验证）。数据内容不在 checkpoint 内，这是结构性缺口而非实现瑕疵。
- **建议**: 二选一或并用：(1) `writeLines` 完成后立即 `forcePath(tempPath)`（fsync 前移到 checkpoint 确认 durable 之前，使 checkpoint durable ⟺ 数据 durable）；(2) `doCommitLocked` 在 rename 前核对 temp 文件行数 == `recordCount`，不符即 typed 失败，把静默截断转化为显式错误。
- **信心水平**: 确定（代码事实）；实际数据丢失需 OS 崩溃，概率低
- **误报排除**: 不是「把不优雅当问题」——现有注释已系统性地为 manifest/目录做了 fsync（`forcePath(tempManifest)`、`forceDirectoryQuietly`），唯独承载数据本身的 epoch temp 文件漏在 checkpoint durable 之前；`recordCount` 作为唯一可自检字段被序列化进每个 pending 条目却无生产读者，说明自检本是设计意图而未闭环。
- **复核状态**: 未复核

---

### [CON-05] JDBC 台账命名空间回退（tableName）使 R5-CON-01 缺陷族在直连构造路径保留残余碰撞面
- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:390-396, 418-420, 643-659`
- **证据片段**: ```java
public JdbcTwoPhaseCommitSink<IN> copyForSubtask(TaskLocation location) {
    return new JdbcTwoPhaseCommitSink<>(..., location.getJobId() + "|" + location.getVertexId(), ...);
}
// 直连构造（builder.build()）时 ledgerNamespace == null：
private String effectiveLedgerNamespace() {
    return ledgerNamespace != null ? ledgerNamespace : tableName;   // 回退 = 目标表名
}
```
- **严重程度**: P2
- **现状**: R5-CON-01 修复（namespace= `jobId|vertexId`）只在身份管线（`OperatorChain.copyForSubtask(TaskLocation)`，`OperatorChain.java:277` → `StreamSinkOperator.java:86-89`）部署路径上生效。经 builder/构造器直连构造（嵌入场景、无部署身份）时回退到 `tableName`：两个并发作业写**同一目标表**、共享同一台账库时，`(tableName, epochId, 0)` 三元键几乎必然互撞（epoch 均从 1 起），第二个作业的 `commit` 命中 guard → WARN + 跳过数据写。
- **风险**: 嵌入式用户双作业共表场景下，第二个作业的全部数据被幂等 guard 静默吞掉（仅一条 WARN），恰是 R5-CON-01 要消灭的「guard 误伤他支路写入」模式在回退命名空间下的复活。身份管线内修复完备性已验证无缺口。
- **建议**: 直连构造路径在 `beginTransaction/checkConnection` 时检测台账中存在同 `(tableName, epoch)` 但不同 namespace 的行时升级为显式错误（或文档明示「直连构造 + 共表 + 共台账库」为不支持组合并要求显式传入 namespace）。
- **信心水平**: 确定（碰撞路径代码级可证）；触发依赖嵌入式使用方式
- **误报排除**: 不是对 R5-CON-01 修复本身的否定——部署管线（生产主路径）已由 namespace 三元键完全修复且有 `TestJdbcTwoPhaseCommitLedgerNamespace` 钉定；本条是修复完备性审计所要求核查的**回退分支**：fallback 注释自称「still disambiguates same-job branches」，但未覆盖「不同作业同表」这一与原缺陷同构的碰撞。
- **复核状态**: 未复核

---

### [SEC-01] extraProperties 可在守卫之后覆盖引擎属性：connectorType 白名单与「无静默内存 schema history」两个已声明不变式可被绕过
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java:53, 103-104, 126-148`
- **证据片段**: ```java
props.setProperty("connector.class", connectorClass);      // 白名单钉定（:53）
...
applySchemaHistory(props, config);                          // schema.history.internal 钉定（:104）
if (config.getExtraProperties().containsKey("offset.storage")) { throw ... }   // 仅两个守卫
if (processingThreads != null && !"1".equals(...)) { throw ... }
...
for (Map.Entry<String, String> entry : config.getExtraProperties().entrySet()) {
    props.setProperty(entry.getKey(), entry.getValue());    // 最后执行，可覆盖以上一切
}
```
- **严重程度**: P2
- **现状**: `offset.storage` 与 `record.processing.threads` 有类型化守卫（WI7），但 `extraProperties` 合并循环在所有钉定之后执行且无键黑名单：`connector.class=io.debezium.connector.sqlserver.SqlServerConnector` 可静默绕过 mysql/postgres 白名单（白名单在 `getConnectorClass` 的 fail-fast 只约束 `connectorType` 字段），`schema.history.internal=...MemorySchemaHistory` 可绕过「schema history 必配、无静默内存」裁定，`snapshot.mode`/`name`/`topic.prefix`/`database.*` 亦同。
- **风险**: 文档钉定的两个安全/正确性不变式（cdc-cookbook「机制总览」前两条）对持有 extraProperties 的配置面不成立：配置错误不再「构建期 fail-fast」而是静默以白名单外连接器/内存 history 运行——offset 唯一真相源与 DDL 回放上下文随之失效，且表现为运行期才暴露的难排查故障。属于「白名单未命中项原样透传」审计点的直接命中。
- **建议**: 在合并循环前置键守卫：拒绝（或显式白名单放行）`connector.class`、`schema.history.internal`、`name`、`topic.prefix`、`offset.*`；至少对 `connector.class` 与 `schema.history.internal` 复用 WI7 的类型化拒绝。
- **信心水平**: 确定（属性覆盖顺序为代码事实）
- **误报排除**: 不是「extraProperties 本来就是逃生门」——同类逃生门中真正危险的两把锁（offset.storage、processing threads）已被显式钉死，证明设计意图是「钉定危险键、放行其余」，只是钉定集合不完备；本条只主张把同级的两个已声明不变式补进同一守卫，不主张收紧整个透传面。
- **复核状态**: 未复核

---

### [CON-06] batch-loader 能力描述符声明 PARALLEL，与运行期单写入者门禁及 owner doc「单写入者」裁定三方不一致
- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceConnectorFactory.java:34-44`；对照 `BatchLoaderSourceFunction.java:189-199` 与 `docs-for-ai/03-modules/nop-stream-connectors.md:88`
- **证据片段**: ```java
// 工厂描述符（与钉定测试 TestStreamConnectorRegistryDiscovery:134-138 一致地声明 PARALLEL）
.parallelism(ConnectorParallelism.PARALLEL)
// 端点运行期契约（R5-CON-05）：
public void validateParallelism(int parallelism) {
    if (parallelism > 1) {
        throw new StreamException(ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)... // 只允许 parallelism=1
```
- **严重程度**: P3
- **现状**: 描述符与钉定测试都声明 `PARALLEL`，运行期 `GraphExecutionPlan.java:527` / `RemoteGraphExecutionPlanBuilder.java:344` 在部署期 typed 拒绝 P>1，owner doc 矩阵写「单写入者（R5-CON-05）」。三方中描述符是离群值（方向与 CON-01 相反：这次是声明**高**于实现）。
- **风险**: 运行期无危害（门禁兜底、fail-fast）；但 `StreamConnectorCatalog.renderListing`/conf-validate 的声明面会告诉用户 batch-loader 支持并行，部署期才被拒——声明面与探测面误导配置决策，且破坏「描述符 = 能力单一事实源」的元规则。
- **建议**: 描述符改为新语义（如 `SINGLE_INSTANCE`）或在 `ConnectorParamDescriptor` 之外为 ParallelismCheckable 增加声明通道；同步更新钉定测试与 catalog 渲染。
- **信心水平**: 确定
- **误报排除**: 不是 CON-01 的重复——CON-01 是测试落后于 live 描述符（钉定失效），本条是 live 描述符落后于运行期门禁与 owner doc（声明面漂移）；两者方向相反、修复动作不同（一个改测试，一个改描述符），且本条已确认运行期门禁真实存在（两处生产调用点），无数据重复投递风险。
- **复核状态**: 未复核

---

### [CON-07] Debezium 事件转换无时间/数值类型还原，时间列经 dmlMode 落库在提交期以方言错误失败循环；`(Long) ts_ms` 直转与同文件防御风格不一致
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEventConverter.java:53-70, 84-89`；绑定面 `JdbcTwoPhaseCommitSink.java:1025-1037`（`ps.setObject(index, value)`）
- **证据片段**: ```java
Long tsMs = (Long) payload.get("ts_ms");            // 直接强转（数值宽度 CCE 家族）
...
// 同文件对 binlog/lsn 却做了宽度归一：
Long binlogPosition = asLong(source.get("pos"));    // “JSON 数值经解析可能落到 Integer/Long 任一宽度”
```
- **严重程度**: P3
- **现状**: 引擎以 `JsonByteArray` 输出，`DebeziumEngineConfig.buildProperties` 未设置/钉定 `time.precision.mode`、`decimal.handling.mode`；after/before/key 列值以 JSON 原生类型（Integer/Long/Double/String）进入 `ChangeEvent`，JDBC sink 的 keyed/insert 路径 `setObject` 原样绑定。DATETIME/TIMESTAMP/DECIMAL 列会以数值形态到达驱动，主流方言在提交期抛 SQLException → epoch commit 失败 → 任务失败重启循环。
- **风险**: 失败可见（非静默错写），但错误暴露在 checkpoint 提交期而非配置期，运维侧表现为「作业反复重启」而无指向性诊断；CDC cookbook 对 dmlMode 的描述未声明该类型边界。`ts_ms` 现值恒 13 位（必为 Long）故强转暂不触发 CCE，但与同文件 `asLong` 防御理由（Integer/Long 宽度漂移）自相矛盾，是该历史缺陷族的残留入口。
- **建议**: (1) `ts_ms` 改走 `asLong`；(2) 在 `buildProperties` 显式钉定 `time.precision.mode=connect`（时间转字符串）或在 JdbcTwoPhaseCommitSink 绑定层提供 temporal/decimal 归一钩子；(3) CDC cookbook「常见故障表」补充该症状与处置。
- **信心水平**: 确定（转换缺失为代码事实）；「提交期失败循环」的具体方言行为为很可能
- **误报排除**: 不是把「功能缺失」当缺陷——审计口径明列「codec：类型还原边界（数值宽度 CCE 历史缺陷族）、时间类型」为检查项；`asLong` 注释证明团队已承认宽度漂移并在元数据字段修复，本条指出同一未修复面在数据字段与 ts_ms 上仍然存在且无文档边界声明。
- **复核状态**: 未复核

---

### [SEC-02] Debezium 配置标识符直传第三方引擎内部 SQL（schemaHistoryJdbcTable / signalDataCollection 等），平台侧无标识符校验
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumConfig.java:130-137, 316-318`；透传点 `DebeziumEngineConfig.java:169-180, 187-189, 121-124`
- **证据片段**: ```java
if (config.getSchemaHistoryJdbcTable() != null) {
    props.setProperty("schema.history.internal.jdbc.table.name", config.getSchemaHistoryJdbcTable());
}
...
if (config.getSignalDataCollection() != null) {
    props.setProperty("signal.data.collection", config.getSignalDataCollection());
}
```
- **严重程度**: P3
- **现状**: `schemaHistoryJdbcTable`（经 `qualifySchemaHistoryPerInstance` 追加 `_N` 后缀后）与 `signalDataCollection` 原样交给 Debezium，其 `JdbcSchemaHistory` 自行拼接建表/查询 SQL；nop 平台对自身 SQL 面执行 `escapeSQLName` 纪律，但对交给引擎的标识符无等价校验（非法字符只会在引擎内部以 SQL 错误暴露）。同类：`extraProperties` 任意键值直传引擎 Properties。
- **风险**: 输入方是部署面受信配置（流水线作者），非外部攻击者可控，实际为加固项：错误输入的诊断位置在第三方库深处；B 层实例化时 `_N` 后缀还可能撞上用户已用同名后缀的表。
- **建议**: 在 `DebeziumConfig` 校验层对标识符类字段（schemaHistoryJdbcTable、schemaHistoryStore 的值域）做字符白名单校验，typed fail-fast；B 层后缀拼接改为不冲突的分隔策略。
- **信心水平**: 确定（透传事实）；实际可利用性低
- **误报排除**: 不与 SEC-01 重复——SEC-01 是「守卫集不完备导致白名单不变式可被绕过」（正确性/安全违约），本条是「透传值缺平台级标识符校验」（加固边界）；输入威胁模型也不同（前者针对不变式完整性，后者针对第三方 SQL 面卫生）。
- **复核状态**: 未复核

---

## 已验证通过的关键路径（历史缺陷族复核）

| 缺陷族 | 复核结论 |
|---|---|
| R5-CON-01 台账命名空间 | 部署管线修复完备（`jobId|vertexId` 三元键 + guard 命中 WARN 化 + `TestJdbcTwoPhaseCommitLedgerNamespace`）；残余仅在直连构造回退分支（CON-05） |
| R5-CON-02 collect 异常吞噬 | 修复在位：`DebeziumCdcSourceFunction.run()` 订阅消费者捕获首错入 `pendingError`，wait 循环退出后重抛（`:234-251, 266-270`）；`DebeziumMessageSource.dispatchEvent` 的 LOG.error 吞噬层因此对 stream 路径不可达，仅对非 stream 的多订阅者残留 |
| R5-CON-03 引擎线程死亡静默 | 修复在位：worker `catch` 与 `CompletionCallback(success=false)` 双路径 `notifyEngineFailure` → name-keyed listener → `pendingError` → 任务 FAILED（`DebeziumEngineWrapper.java:157-165, 216-230`）；listener 注册先于引擎创建、finally 注销且 null 安全 |
| 2PC 端点协议 | `StreamSinkOperator.processBarrier` 确认 saveState 先于 prepareCommit（`:114-127`），与 JDBC/File sink 的 saveState-first override 匹配；`finishCommit` subsuming commit、`restoreFromEpoch` 的 durable-重提交/非 durable-abort 分界、`setPendingCommits` Long 键归一（JSON 往返 CCE 防御）均在位 |
| R5-CON-05 batch-loader 单写入者 | `ParallelismCheckable` 门禁在两处 plan builder 生产调用点生效（`GraphExecutionPlan.java:527`）；seek/skip-shortfall/负 offset 三路径 typed（仅描述符声明漂移，CON-06） |
| AR-03 首启陈旧 offset | 整体缺失路径 clearConnector 在位；分区级 merge 残余见 CON-03 |
| offset 提交与投递原子性 | 引擎在 dispatch（同步 collect）返回后经 OffsetCommitPolicy 提交 offset，store 快照 ≤ 已投递 → REPLAYABLE 的 at-least-once 边界成立，无「store 领先投递」丢失方向 |
| 文件 sink 提交协议 | temp→ATOMIC_MOVE→manifest 锁内串行（JVM ReentrantLock + FileLock 双层）、rename/manifest 间崩溃的 manifest 修复分支、abort 清 temp、非 durable epoch 不可能留下孤儿终文件（commit 仅在 checkpoint durable 后触发）均在位；OS 崩溃 fsync 窗口见 CON-04 |

---

## 检查范围

- `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/`：`MessageSourceFunction`、`MessageSinkFunction`、`MessageSourceConnectorFactory`、`MessageSinkConnectorFactory`、`file/`（FileSource、FileSourceReader、FileSplit、FileSplitEnumerator、FileSplitEnumeratorState、FileTwoPhaseCommitSink、FilePendingCommit、两工厂）全文精读。
- `nop-stream/nop-stream-connector-jdbc/src/main/java/`：JdbcTwoPhaseCommitSink（全文）、JdbcTwoPhaseCommitSinkBuilder、JdbcTwoPhaseCommitSinkConnectorFactory。
- `nop-stream/nop-stream-connector-debezium/src/main/java/`：DebeziumCdcSourceFunction（全文）、DebeziumCdcSourceConnectorFactory。
- `nop-stream/nop-stream-connector-batch/src/main/java/`：BatchLoaderSourceFunction、BatchConsumerSinkFunction、两工厂。
- `nop-message/nop-message-debezium/src/main/java/`：DebeziumMessageSource、engine/DebeziumEngineWrapper、engine/NopStreamOffsetBackingStore、engine/DebeziumEngineConfig、engine/DebeziumEventConverter、DebeziumConfig、ChangeEvent、DebeziumConstants（ChangeEventMetadata/DebeziumErrors 走读）。
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/`：registry 全包（StreamConnectorRegistry、StreamConnectorConfig、StreamConnectorCatalog、Connector*Descriptor/枚举、各工厂接口）、StreamConnectivityProber、ConnectivityCheckable/Outcome、lookup/ITableLookup；`credentials/StreamCredentialSupport`。
- 契约依赖面（跨界只读核对）：`common/functions/sink/TwoPhaseCommitSinkFunction`、`operators/StreamSinkOperator`（processBarrier/copyForSubtask）、`jobgraph/OperatorChain`、`execution/GraphExecutionPlan`、`nop-api-core` `IMessageService/IMessageSender`、`nop-dao` `DialectImpl.escapeSQLName`、`nop-commons` `StringHelper`（quoteDupEscapeString/isValidSimpleVarName）、`runtime/checkpoint/storage/JdbcCheckpointStorage`（审计范围声明的 checkpoint storage 项）。
- 声明面核对：5 个 `connector-*.beans.xml`、8 个工厂描述符、`TestStreamConnectorRegistryDiscovery` 断言、`docs-for-ai/03-modules/nop-stream-connectors.md`、`nop-stream-cdc-cookbook.md`。
- 未覆盖：nop-stream-runtime/flow/cep 的算子内部（非本报告范围）；gated 真库集成测试行为（未运行）；Debezium 3.7 引擎内部实现（以外部库黑盒对待）。

## 安全五项检查结论

1. **SQL 注入 — 通过**。`JdbcTwoPhaseCommitSink` 全部 SQL 拼接点（insert/upsert/delete/ledger DDL 与 DML）标识符均经 `d.escapeSQLName(...)`，值全部参数化（`bindRow`/`ps.setString/Long/Int/Timestamp`）；下钻核验 `DialectImpl.escapeSQLName`（`nop-persistence/nop-dao/.../DialectImpl.java:480-503`）：非简单变量名/保留字一律 `quoteDupEscapeString`（嵌入 quote 字符双写，`StringHelper.java:2220-2230`），`isValidSimpleVarName` 排除特殊字符，无前缀匹配绕过。`JdbcCheckpointStorage` 表名为内部常量、值参数化、去重键判定仅用结构化 SQLState/vendorCode（R5-CON-08 在位）。`querySpace` 仅作连接/方言查找键，不入 SQL。
2. **白名单双向断言 — 不通过（SEC-01）**。`connectorType` 白名单与 `schema.history.internal` 钉定可被 `extraProperties` 在守卫之后覆盖绕过（`DebeziumEngineConfig.java` 守卫先于合并循环）；已钉定的 `offset.storage`、`record.processing.threads` 两条守卫本身实现正确。未发现其他静默放行回退分支（file/debezium 的 probe 路径、`StreamConnectivityProber` 未命中契约时显式 SKIP 而非 PASS，符合「不静默通过」要求）。
3. **凭证处理 — 通过（附边界说明）**。`StreamCredentialSupport` 语法解析 fail-fast（空 id/field typed 拒绝）、provider 缺失 fail-closed（`ERR_STREAM_CREDENTIAL_PROVIDER_MISSING`）、provider 失败包装 typed 且仅携带引用串不携带明文；连接器侧持久化面（DebeziumConfig 序列化、checkpoint、copyForSubtask 序列化拷贝）只携带 `credential:{id}#{field}` 引用，解密仅存在于 `effectiveEngineConfig()` 的瞬态副本；`checkConnection` 的可达性判定以「解密成功」为准且不回显明文。边界：以明文（非引用）配置的 `databasePassword` 会随 Serializable config 进入 checkpoint 状态文件——这是 owner doc「凭据引用与明文边界」已声明的取舍，非缺陷，但建议文档在 dmlMode 示例中同步提示。
4. **主机白名单规范化 — 不适用/通过**。连接器族内不存在 URL/host 白名单校验器：`database.hostname`、`schemaHistoryJdbcUrl` 直传引擎/JDBC 驱动（部署面受信输入），无 userinfo/IPv6/十进制 IP 解析面可被规范化绕过。若未来引入连接白名单，需按本项标准自审规范化。
5. **用户输入直传底层 — 部分通过**。读取面：R5-CON-10 分裂路径防逃逸检查在位（`FileSourceReader.openSplit:222-230`，`Path.startsWith` 为逐组件比较，无 `/data/dir` vs `/data/dir-evil` 前缀绕过），生产构造器必传 sourceDirectory；无 JNDI/反射类加载入口（`offset.storage` 类名由平台钉定，extraProperties 的 `connector.class` 反射实例化属 SEC-01 范畴）。写/透传面：file sink `outputDir`、file source `directoryPath` 为部署配置直传（连接器本职）；`extraProperties` 透传与 `schemaHistoryJdbcTable` 直传引擎内部 SQL 分别记为 SEC-01、SEC-02。

---

## 深挖第 2 轮追加

- **轮次日期**: 2026-10-05（第 2 轮子代理，基于第 1 轮发现继续深挖；HEAD 同第 1 轮 `bd089444`，工作树无 nop-stream / nop-message 改动）
- **方法**: 只读静态审计。沿第 1 轮暴露面下钻：(1) JDBC 2PC ledger 键构造全链路（`JdbcTwoPhaseCommitSink` 全文 + `TwoPhaseCommitSinkFunction` + `StreamSinkOperator` + `OperatorChain.deepCopy` + `SupervisionLoop.rebuildTask` + `GraphExecutionPlan.createSubtasks` + `AssignmentPlanner`/`RemoteTaskDeploySupport`/`RunningTask` + `CheckpointIDCounter` + `MaxParallelismReshardMigration`/`RescaleStateAssembler`/`GraphModelCheckpointExecutor` rescale 守卫）；(2) source/sink 生命周期异常安全（`OperatorChain.open/finish/close`、`AbstractStreamOperator.close`、`FileSourceReader`、`FileTwoPhaseCommitSink`、`DebeziumCdcSourceFunction.run/cancel/truncateForDrain`、`DebeziumMessageSource`、`DebeziumEngineWrapper` 全文、`BatchConsumerSinkFunction.close`）；(3) offset 协议（`NopStreamOffsetBackingStore` 全文、`DebeziumMessageSource` 订阅/停止路径、`DebeziumEventConverter` 全文）；(4) 第 1 轮未覆盖面（batch 批语义、file source 行切分边界与 codec、`StreamConnectorRegistry`、`ITableLookup` 实现面、JDBC sink 连接生命周期）。

### 本轮核查后确认无新问题的面（防重查负面清单 · 第 2 轮）

| 检查面 | 结论 |
|---|---|
| 2PC sink 顶点 parallelism 变更恢复 | typed 拒绝在位：`GraphModelCheckpointExecutor:1317-1322`（`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED`，`isVertex2PcSink` 经 `StreamSinkOperator`(AbstractUdfStreamOperator) 覆盖部署形态，且不 gate 在 `vertexKeyed` 上）；scale-up 的 keyed 顶点由 `assertNoOperatorStateOnScaleUp` fail-fast |
| region restart 身份稳定性 | `SupervisionLoop.rebuildTask:837-847` 显式以旧部署 location 重建链，2PC ledger namespace 跨重启稳定（与第 1 轮负面清单一致） |
| `OperatorChain.open/finish/close` | open 失败回收已打开算子 + suppressed；finish/close 全算子尝试 + suppressed 聚合——协议自身异常安全 |
| `StreamConnectorRegistry` 并发注册 | build-once 不可变 map，重复 typeName/alias fail-fast（含 alias 环），无运行期注册面 |
| `ITableLookup` | 接口无任何生产实现（仅测试），无缓存一致性面可审 |
| JDBC sink 连接生命周期 | 每 epoch 独立 pooled connection，finally 内 rollback→setAutoCommit(true)→close 三段各自吞错；guard check+insert 同事务，并发同键提交由 PK 冲突→重试→guard 命中收敛 |
| batch 连接器批语义 | flush 失败保留缓冲 + typed 抛出（fail→replay→at-least-once，声明 IDEMPOTENT 一致）；loader skip-shortfall/负 offset typed（第 1 轮已钉） |
| file source 行切分边界 | LF/CRLF/lone CR 三终止符、`maxLineBytes` fail-fast、末行无终止符返回、跨 8KB 缓冲多字节字符、AR-15-② 光标同监视器推进、AR-15-① 精确 channel 定位 + size 校验、AR-15-③ endOffset cap——均无缺陷（codec 边界除外，见 CON-11） |
| Debezium offset 持久化协议 | base64 TreeMap 往返无损；「空 map checkpoint + 陈旧 registry」是 CON-03 merge 缺口的退化形态，修复建议（replace 语义）已覆盖，不另立条目 |

---

### [CON-08] File 2PC sink 的 epoch/manifest 键无任何作业/分支命名空间：跨运行复用 outputDir 时新数据被 guard 静默吞掉（R5-CON-01 缺陷族在 file sink 上完整存活）
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:285-302, 571-586`；对照 `common/functions/sink/TwoPhaseCommitSinkFunction.java:150-152`（基类 `copyForSubtask(TaskLocation)` 默认只传 taskIndex）与 `runtime/checkpoint/CheckpointIDCounter.java:25`（全新提交从 0 重新起算）
- **证据片段**: ```java
// doCommitLocked —— 键 = (epochId, subtaskSuffix)，无 job/branch 分量：
Path finalPath = finalPath(ownerSubtask, checkpointId);      // epoch-<id>[.sK].txt
String manifestEntryKey = manifestKey(ownerSubtask, checkpointId);
if (manifest.containsKey(manifestEntryKey)) {
    getPendingCommits().remove(checkpointId);
    return;                                                   // 静默跳过，无任何 WARN
}
if (Files.exists(finalPath)) {
    manifest.setProperty(manifestEntryKey, finalPath.toString());  // 静默“采纳”既有文件
    updateManifestAtomically(manifest); ...
}
// FileTwoPhaseCommitSink 未覆盖 copyForSubtask(TaskLocation)——R5-CON-01 的
// jobId|vertexId 命名空间管线对 file sink 不可达（file-sink 对应物缺失）
```
- **严重程度**: P2
- **现状**: manifest 键与终文件名的唯一性域是「一次作业运行内的 (epoch, subtaskIndex)」。checkpoint id 计数器每次全新提交重置（仅同 coordinator restore 时前移），因此顺序重跑（或第二个作业/同一作业第二条 sink 分支）复用同一 `outputDir` 时：新运行 epoch 1..N 必然命中 manifest guard → 数据被静默吞掉（JDBC sink 在 R5-CON-01 修复中把 guard 命中升级为 WARN，file sink 无对应升级）；若 manifest 丢失而终文件在（部分清理），`final-exists` 分支把上一运行的文件静默采纳为本运行的已提交数据。AR-14 的 manifest 锁使该碰撞从竞态变成确定性静默 skip。
- **风险**: 与 R5-CON-01 同构的「幂等 guard 误伤他支路写入」失败模式，且比 CON-05（JDBC 直连回退）更宽：部署管线内同样不设防（无命名空间通道）、guard 命中零日志、失败方向是静默丢数据——exactly-once 声明在「outputDir 复用」这一并不显然非法的配置下退化为静默丢失。
- **建议**: 覆盖 `copyForSubtask(TaskLocation)`，把 `jobId|vertexId`（或其哈希）纳入 temp/final 文件名与 manifest 键（或在 `outputDir` 下按身份派生子目录）；guard 命中与 final-exists 采纳路径补 WARN 级日志；owner doc 明示「同一 outputDir 只允许一个 2PC file sink 运行体（含跨运行）」。
- **信心水平**: 确定（键结构、计数器重置、guard 静默路径均可静态验证）
- **误报排除**: 不是 CON-04（fsync/recordCount 自检缺口）也不是 AR-14（同作业并行 subtask 的锁竞态）——本条是键的跨运行/跨写者唯一性缺失；也不是负面清单已验证的「manifest 修复分支」本身——该分支对自家 rename 后崩溃是正确的，问题在于它无法区分「自家 rename 后崩溃」与「他者运行的同名 epoch 文件」。
- **复核状态**: 未复核

---

### [CON-09] JDBC ledger namespace 的 jobId 分量存在两个值族（raw jobName vs sanitized jobId）：跨执行模式恢复同一 checkpoint 时幂等 guard 失效，durable-but-uncommitted epoch 重复提交
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:447-453`；对照 `core/graph/PartitionedPlanGenerator.java:64-72` + `core/checkpoint/StorageJobIds.java:44-66`、`runtime/coordinator/AssignmentPlanner.java:159-194`、`runtime/deploy/RemoteTaskDeploySupport.java:100-117`、`runtime/taskmanager/RunningTask.java:79`
- **证据片段**: ```java
// 本地执行（部署身份 = ledger namespace 来源）：
TaskLocation taskLocation = new TaskLocation(
        jobGraph.getJobName(), "pipeline-0", vertexId, taskIndex);   // RAW 作业名
// coordinator/remote 执行（同一字段的另一值族）：
String jobId = descriptor.getJobId();   // = PartitionedPlan.jobId = sanitizeJobId(jobName)
//   含空格/CJK 等不安全字符时 = "<sanitized>-<hash6>"；默认作业名 "Streaming Job" 即触发
// JdbcTwoPhaseCommitSink.copyForSubtask(TaskLocation)：
//   namespace = location.getJobId() + "|" + location.getVertexId()
```
- **严重程度**: P3
- **现状**: R5-CON-01 修复把 ledger 幂等键锚定在部署 `TaskLocation.jobId` 上，但该字段在两条部署路径上取值不同：`GraphExecutionPlan.createSubtasks` 用 raw `jobGraph.getJobName()`，coordinator/remote 家族（`AssignmentPlanner`/`RemoteTaskDeploySupport`/`RunningTask`）用 `PartitionedPlanGenerator` D1b（AR-1）的 `sanitizeJobId(jobName)` 产物。模式内稳定（region restart 显式保留旧 location，`SupervisionLoop:837-847`），但同一作业跨执行模式恢复（如本地运行产生 checkpoint 后经 OpsJobManager 提交 remote 并 restore durable checkpoint）时 namespace 漂移。
- **风险**: 跨模式恢复时 durable-but-uncommitted epoch 的重提交在新 namespace 下 guard miss → 目标表写入重复行（恰好击穿「恢复重提交靠幂等 guard 去重」的前提）；同时台账表按 namespace 变体积累无法归因的行。默认作业名含空格使两值族必然分叉，非边缘配置。
- **建议**: 统一部署 location 的 jobId 值族（`GraphExecutionPlan:450` 改用与 `PartitionedPlan` 相同的 `sanitizeJobId` 产物，或 ledger namespace 显式改从 checkpoint-plan 家族取 jobId）；补一个「同一 jobGraph 经两条部署路径产出相同 ledger namespace」的钉定测试。
- **信心水平**: 确定（两构造点取值差异代码级可证）；跨模式恢复场景为很可能
- **误报排除**: 不是对 R5-CON-01 修复的否定——身份**管线**（deepCopy 传递）完备性第 1 轮已验证；本条是管线的**输入值**在不同执行模式下不相等，属本轮「parallelism/重启/恢复后身份稳定性」核查面的直接命中。也不是 `SupervisionLoop:811-827` 已文档化的「checkpoint 家族 vs exec 家族」问题——那是 ACK 路由（有 `findTaskLocationInPlan` 桥接），ledger namespace 无等价桥接。
- **复核状态**: 未复核

---

### [CON-10] debezium-cdc source 顶点的 parallelism 变更恢复无 typed 守卫：scale-down 静默丢弃 trimmed subtask 的 cdc-offsets，表重路由后从快照重读（重复），与 2PC sink 的同场景拒绝裁定不对称
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1317-1322, 1353-1360`；对照 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:120-121, 488-527`（表哈希分片）与 `:349-399`（offset 按 subtask 存取）
- **证据片段**: ```java
// 2PC sink 顶点：parallelism 变更恢复 typed 拒绝（R5 裁定：不能重分布就拒绝）
if (oldParallelism > 0 && oldParallelism != newParallelism && isVertex2PcSink(newSubtasks)) {
    throw new StreamException(ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED)...
}
// debezium-cdc source 顶点（非 keyed → rescale=false）：逐 subtask 1:1 lookup，无对应守卫
taskState = stateLookup.lookup(taskLocation);
// 而表→subtask 归属 = floorMod(hash(table), totalParallelism)（B 层），cdc-offsets 却按 subtask index 1:1 持久化
```
- **严重程度**: P3
- **现状**: debezium-cdc 声明 `PARALLEL` + `OFFSET_CHECKPOINT`（WI4 B 层表分片），checkpoint 的 `cdc-offsets` 按 subtask index 切分持久化，表归属却按 tableIncludeList 哈希路由——两者仅在 parallelism 不变时对齐。parallelism 变更后恢复：scale-up 在逐 subtask lookup 处 late-fail（有 typed 错误，但发生在恢复中段而非部署期）；scale-down 无任何守卫，trimmed subtask 的全部 offset 被静默丢弃——按新 parallelism 重哈希后路由到幸存 subtask 的表在恢复的 offset map 中无对应分区键，引擎从快照/最早可用位点重读。
- **风险**: 缩容恢复后重路由表静默产生全量重复事件：下游 INSERT 语义 sink → 重复行；UPSERT 语义 sink 被幂等吸收（无症状）。与平台自身在同一恢复场景对 2PC sink 的「不能重分布即 typed 拒绝」裁定不一致——同一原则未覆盖 index 分片的 source offset 状态。
- **建议**: 对携带非空 `cdc-offsets` 的 source 顶点在恢复期同样 typed 拒绝 parallelism 变更（复用 `isVertex2PcSink` 的检测点模式），或将 offset 状态改为按表键（分片域本身）而非 subtask index 持久化；`validateParallelism` 钩子可承载部署期前置校验。
- **信心水平**: 确定（分片域错位 + 守卫缺失代码级可证）；实际触发需有意变更 CDC source 并行度并从 checkpoint 恢复
- **误报排除**: 不是 CON-03——CON-03 是同 JVM static registry 的 merge-vs-replace（陈旧 offset 幸存 → 跳读**丢失**方向），修复建议为 replace 语义；本条是跨 JVM 新恢复中「offset 状态按 index、归属按表哈希」的结构性错位 → **重复**方向，replace 语义修复不了它。也不是 CON-01/CON-06 的声明漂移——运行期守卫本身缺失。
- **复核状态**: 未复核

---

### [CON-11] FileSourceReader 硬编码 UTF-8 且参数面无 charset 通道、无边界声明：非 UTF-8 输入静默 U+FFFD 损坏（与 CON-07 同族的未声明 codec 边界）
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:311, 326, 339`（`out.toString(StandardCharsets.UTF_8)`）；对照 `FileSourceConnectorFactory.java:36-39`（仅 `directoryPath` 参数）与 `FileTwoPhaseCommitSink.java:107-108, 158`（sink 侧 `charsetName` 可配且可序列化）
- **证据片段**: ```java
if (b == '\n') {
    return out.toString(StandardCharsets.UTF_8);   // 解码字符集硬编码，无配置、无声明
}
...
return out.size() == 0 ? null : out.toString(StandardCharsets.UTF_8);
// 工厂描述符 params: 只有 required("directoryPath", ...)
```
- **严重程度**: P3
- **现状**: 读取面按 UTF-8 解码，工厂参数、`ConnectorParamDescriptor` 描述与 owner doc 均未声明「UTF-8-only」；写面（file 2PC sink）charset 可配。非 UTF-8 输入（GBK/Latin-1 文本、内嵌二进制）经 `ByteArrayOutputStream.toString(UTF_8)` 解码时无效序列静默替换为 U+FFFD：作业不报错、行数正确、内容损坏，完全不可归因。
- **风险**: 同 CON-07 的失败形态——错误不在配置期或读取期暴露而在数据质量上显形；`escape`/`checksum` 等读取面防御（R5-CON-10/R5-CON-07）都已闭环，唯独字符集边界无声明，用户按 sink 侧经验（可配 charset）推断 source 亦可配，会得到静默乱码。
- **建议**: 最小闭环——描述符 param 描述与 `docs-for-ai/03-modules/nop-stream-connectors.md` 明示 UTF-8-only；完整闭环——增加 `charset` 参数（复用 sink 侧「存名字、懒解析」的序列化先例），非法字节 fail-fast 或替换计数可观测。
- **信心水平**: 确定
- **误报排除**: 不是把「功能缺失」当缺陷——本轮审计口径明列「file connector 的 codec 与行切分边界」为检查项，且行切分边界本身（三终止符、maxLineBytes、末行、跨缓冲多字节）已核验无缺陷另记于负面清单；本条只主张「静默损坏数据的编码边界必须被声明或可配」。与 CON-07 不重复：那是 debezium 事件的时间/数值类型还原，本条是 file 字节流→字符的解码域。
- **复核状态**: 未复核

---

## 维度复核结论

- **复核人**: 独立维度复核子代理（R5「连接器 + 安全」第 3 轮）
- **复核日期**: 2026-10-05
- **复核方法**: 只读静态复核。对全部 12 条发现（CON-01..11、SEC-01/02）逐条重读 live code 验证证据行号、现状、风险推演与严重程度；P1（CON-01）另取 git 提交时间线与 owner doc 裁定依据深验证；「零读者」类论断（CON-04 recordCount、负面清单 ITableLookup）独立全仓 grep 复核；安全五项结论与负面清单关键路径逐项抽查（`DialectImpl.escapeSQLName`、`StreamCredentialSupport` fail-closed、`StreamConnectivityProber` SKIP、`StreamSinkOperator.processBarrier` 顺序、`SupervisionLoop` 区域重启身份、`GraphModelCheckpointExecutor` 2PC 守卫）。未发现需驳回或降级的发现；未发现复核过程引入的新缺陷条目。

### P1 深验证（CON-01）

矛盾三方在 HEAD 逐字核实，无一松动：

1. **钉定测试断言**：`TestStreamConnectorRegistryDiscovery.java:130-133` 对 `debezium-cdc` 断言 `ConnectorParallelism.SINGLE_INSTANCE`；`assertDescriptor` 经 `:165` `assertEquals(parallelism, d.getParallelism(), typeName)` 直接比较枚举，无任何豁免路径。
2. **live 工厂描述符**：`DebeziumCdcSourceConnectorFactory.java:38` `.parallelism(ConnectorParallelism.PARALLEL)`（注释明言 B 层表路由）。测试宿主 `nop-stream-runtime` 的 pom 含 `nop-stream-connector-debezium` 测试依赖（pom.xml:70），`testDiscoversAllFiveConnectorBeansFiles` 确认 `connector-debezium.beans.xml` 进入发现集，且该 beans.xml 唯一注册 `DebeziumCdcSourceConnectorFactory`——不存在第二描述符来源。`testCapabilityMatrixAlignedWithDesignAdjudication` 在 HEAD **必红**。
3. **git 时间线**：工厂翻转提交 `1dad44a0c6`（2026-10-02）提交消息明言「描述符 SINGLE_INSTANCE->PARALLEL」且验证声明仅为「core 1652 + connector 50 测试全绿」——钉定测试宿主 `nop-stream-runtime` 不在其中；测试最后一次修改 `05e81257e6`（2026-10-03，晚于工厂翻转）只改了 `jdbc-2pc` 的 required params（WI5 dmlMode），debezium 行未同步。即：测试在翻转之后仍被维护过，唯独漏掉此断言。
4. **既定裁定方向**：owner doc `docs-for-ai/03-modules/nop-stream-connectors.md:29` 明言「已裁定例外：debezium-cdc 的 B 层表路由并行……（**2026-10-02 用户裁定**）」，`:87` 能力矩阵钉定 PARALLEL 并以 `TestDebeziumCdcSharding` 为行为锚点。**声明面/实现面（PARALLEL）是既定裁定，测试该更新**——与原报告建议一致。

结论：**保留 P1**。这不是「测试过期」普通噪声：该测试 Javadoc 与 owner doc 头部均声称其为能力矩阵「代码级单一事实源」，现状必红或被排除出验证路径意味着护栏已死，任何并行度/一致性/恢复语义回归都无法被拦截，且暴露 runtime 测试套件可能不在该提交的验证回路中。P1 恰当。

### 逐条裁决

| 编号 | 裁决 | 复核理由（live code 独立验证） |
|---|---|---|
| CON-01 | **保留 P1** | 见上方 P1 深验证。三方矛盾、git 证据、裁定方向全部核实。 |
| CON-02 | **保留 P2** | `start()` 在 worker lambda 内才置 `running`（`DebeziumEngineWrapper.java:152-154`），`stop()` 的 `!running.get()` 早退（`:189`）。复核补充强化证据：`DebeziumMessageSource.stop()` 调用 `engineWrapper.stop()` 后**立即置 `engineWrapper = null`**（`DebeziumMessageSource.java:168-180`），而 `run()` finally 的二次 `source.stop()`（`DebeziumCdcSourceFunction.java:282-290`）因此对已 null 的 wrapper 无操作——不存在「第二次 stop 补关」的补救路径，早退即永久泄漏；listener 已在 finally 注销（`:272`），后续引擎死亡完全静默。风险推演成立，P2 恰当。 |
| CON-03 | **保留 P2** | `clearConnector` 仅经 `newFreshOffsetStore` 在 `state==null`（`:374`）与 `raw==null`（`:383`）两条首启路径调用；非空恢复路径 `:397-398` `forConnector`（`REGISTRY.computeIfAbsent` 复用既有 map）+ `setOffsets`（逐条 `data.put` 的 put-merge，`:189-199`）。restored 缺键时 registry 较新 offset 幸存 → 跳读丢失方向成立。同 JVM region restart 场景真实（`SupervisionLoop` 显式复用同 JVM 实例与实例名）。三重触发条件使概率低，但违反「nop checkpoint 为 offset 唯一真相源」声明且丢失不可再生，P2 恰当。 |
| CON-04 | **保留 P2** | `writeLines`（`FileTwoPhaseCommitSink.java:615-629`）为 BufferedWriter 写入、无任何 force；saveState（`:238-250`）仅写 temp + 元数据；`forcePath` 只出现在 `doCommitLocked` rename 之前；而 manifest temp、manifest rename、目录 fsync 均已系统性存在——唯独承载数据的 epoch 文件漏在 checkpoint durable 之前。**recordCount 零生产读者经全仓 grep 独立证实**：仅 `FilePendingCommit` 自身字段/getter/setter/toString 与 `FileTwoPhaseCommitSink.java:409-411` 反序列化 setter 路径，无任何生产读取点。P2 恰当。 |
| CON-05 | **保留 P2** | `JdbcTwoPhaseCommitSinkBuilder.build()` 直连构造传 `ledgerNamespace = null`（Builder `:138-139`）；`effectiveLedgerNamespace()` 回退 `tableName`（`:418-420`）；guard 命中 → WARN + 跳过数据写（`:649-667`）。部署管线 namespace=`jobId|vertexId`（`:390-396`）修复完备无异议。回退注释自称「still disambiguates same-job branches」确未覆盖「不同作业同表」，与 R5-CON-01 同构的碰撞面真实存在。P2 恰当。 |
| SEC-01 | **保留 P2** | `DebeziumEngineConfig.buildProperties`：`connector.class` 钉定（`:53`，白名单 mysql/postgres fail-fast `:220-232`）、`schema.history.internal` 钉定（`:104`，jdbc/file 两态、无静默内存）均先于 `extraProperties` 合并循环（`:146-148`，最后执行、无键黑名单）；WI7 两条守卫（`:128-140`）证明「钉定危险键」是既定设计意图，钉定集不完备成立。白名单双向断言「不通过」结论站得住。P2 恰当。 |
| CON-06 | **保留 P3** | 描述符 PARALLEL（`BatchLoaderSourceConnectorFactory.java:37`）与钉定测试（Test `:134-137`）一致地声明 PARALLEL；运行期 `validateParallelism` P>1 typed 拒绝（`BatchLoaderSourceFunction.java:189-199`）在两处生产调用点生效（`GraphExecutionPlan.java:527`、`RemoteGraphExecutionPlanBuilder.java:344`——实际位于 `runtime/transport/`，原报告未写目录但文件名与行号精确）；owner doc `:88` 钉定「单写入者（R5-CON-05）」。声明面离群、运行期门禁兜底、无数据风险——P3 恰当（与 CON-01 方向相反、修复动作不同，不构成重复）。 |
| CON-07 | **保留 P3** | `(Long) payload.get("ts_ms")` 直转（`DebeziumEventConverter.java:59`）与同文件 `asLong` 防御及宽度漂移注释（`:84-92`）并存；`buildProperties` 全文确认未设置 `time.precision.mode`/`decimal.handling.mode`；JDBC 绑定面 `bindRow` `ps.setObject` 原样透传（`JdbcTwoPhaseCommitSink.java:1025-1037`）。代码事实全部成立，「提交期失败循环」的方言行为已按「很可能」恰当对冲。P3 恰当。 |
| SEC-02 | **保留 P3** | `DebeziumConfig` 的 `schemaHistoryJdbcTable`/`signalDataCollection` 为纯字段（`:132/:137`），全类无任何标识符校验（无 Pattern/isValidSimpleVarName 等）；透传点证实（`DebeziumEngineConfig.java:177-178/:122-123`），B 层 `_N` 后缀拼接证实（`DebeziumCdcSourceFunction.java:669-672`）。威胁模型为受信部署配置，加固定位准确，与 SEC-01 不重复。P3 恰当。 |
| CON-08 | **保留 P2** | manifest 键 = `epochId + subtaskSuffix`（`FileTwoPhaseCommitSink.java:571-577`），唯一性域确为「单次运行内 (epoch, subtask)」；guard 命中分支静默 remove+return **零日志**（`:293-297`）；final-exists 分支静默采纳（`:299-304`）；`FileTwoPhaseCommitSink` 仅覆盖 `copyForSubtask(int)`（`:186`），未覆盖 TaskLocation 变体，基类默认仅传 taskIndex 且 javadoc 明言「file 2PC sink 是 index-only 子类」（`TwoPhaseCommitSinkFunction.java:150-152`）；`CheckpointIDCounter` 全新提交从 0 起算（`:24-26`）。跨运行/跨写者碰撞 → 静默丢数据成立，比 CON-05 更宽（部署管线同样无通道）。P2 恰当。 |
| CON-09 | **保留 P3** | 本地路径 raw `jobGraph.getJobName()`（`GraphExecutionPlan.java:449-451`）vs coordinator/remote 家族 `sanitizeJobId` 产物（`PartitionedPlanGenerator.java:64-72` + `StorageJobIds.java:44-66`，含空格/CJK 时追加 `-hash6`，默认作业名必然分叉）；`AssignmentPlanner:187`/`RemoteTaskDeploySupport:114`/`RunningTask:79` 均取 sanitized 值族。**决定性佐证**：`RemoteTaskDeploySupport.java:108-112` 注释自认「locally-built plan's locations … derive from the JobGraph name, **which differs from the coordinator's jobId**」——两值族分叉是平台已知的既存事实，ACK 路径有桥接而 ledger namespace 无。模式内稳定性（`SupervisionLoop:839-847`）与跨模式漂移风险推演均成立。P3 恰当。 |
| CON-10 | **保留 P3** | 2PC sink 的 parallelism 变更 typed 拒绝在位（`GraphModelCheckpointExecutor.java:1317-1322`，且注释明言「deliberately does NOT gate on vertexKeyed」）；source 顶点走 `stateLookup.lookup` 1:1（`:1358-1360`）无对应守卫；`cdc-offsets` 按 subtask index 持久化（`DebeziumCdcSourceFunction.snapshotState:350-364`）vs 表归属 `floorMod(hash, totalParallelism)`（`:488-490, :506-514`）仅在 parallelism 不变时对齐；`validateParallelism`（`:438-467`）不核对恢复态。scale-down 静默丢 offset → 重哈希重快照重复方向成立，与 CON-03（丢失方向、同 JVM merge）不重复。P3 恰当。 |
| CON-11 | **保留 P3** | `FileSourceReader.java:311/:326/:339` 三处硬编码 `toString(StandardCharsets.UTF_8)`（无效序列静默 U+FFFD）；`FileSourceConnectorFactory.java:36-39` 仅 `directoryPath` 参数、无 charset 通道、描述未声明 UTF-8-only；sink 侧 `charsetName` 以名字存储可配（`FileTwoPhaseCommitSink.java:107-112`）。读写面 codec 不对称 + 静默损坏 + 无声明成立。P3 恰当。 |

### 安全五项检查结论复核

1. **SQL 注入 — 通过：维持**。`JdbcTwoPhaseCommitSink` 全部标识符拼接点（insert/upsert 三方言/delete/ledger DDL+DML，`:762-770, :1067-1153`）逐一经 `d.escapeSQLName`；下钻 `DialectImpl.escapeSQLName`（`nop-persistence/nop-dao/.../dialect/impl/DialectImpl.java:480-505`）：空名 typed 拒绝、保留字/非简单变量名/下划线前缀一律 `quoteDupEscapeString` 双写转义，无前缀匹配绕过。值全部参数化（`bindRow` `:1025-1037`）。结论成立。
2. **白名单双向断言 — 不通过（SEC-01）：维持**，见 SEC-01 裁决。
3. **凭证处理 — 通过（附边界说明）：维持**。`StreamCredentialSupport` provider 缺失 fail-closed（`ERR_STREAM_CREDENTIAL_PROVIDER_MISSING`，`:83`）证实；引用串持久化/瞬态解密边界证实（`DebeziumCdcSourceFunction.effectiveEngineConfig:623-652`，序列化路径仅携引用）。
4. **主机白名单规范化 — 不适用/通过：维持**。连接器族内未发现任何 URL/host 白名单校验器，`database.hostname`/`schemaHistoryJdbcUrl` 直传引擎/驱动，无规范化解析面。
5. **用户输入直传底层 — 部分通过：维持**。`FileSourceReader.openSplit` 包含性检查在位（`:222-230`，`Path.startsWith` 逐组件比较、先检包含性后探文件），与结论一致。

### 负面清单（已验证通过的关键路径）抽查

R5-CON-02 修复（`run()` pendingError 捕获/重抛，`:234-251, :266-270`）、R5-CON-03 修复（wrapper catch + CompletionCallback 双路径 notify，`:157-165, :216-230`）、processBarrier saveState 先于 prepareCommit（`StreamSinkOperator.java:108-127`）、2PC sink parallelism 变更 typed 拒绝（`:1317-1322`）、region restart 身份稳定（`SupervisionLoop.java:839-847` 注释明言 ledger namespace 跨重启一致）、`ITableLookup` 无生产实现（全仓 grep 仅接口文件 + 测试）、`StreamConnectivityProber` 未命中契约显式 SKIP 而非静默 PASS（`:24`）、batch-loader 门禁双调用点——**全部抽查吻合，负面清单可信**。

### 复核元结论

12 条发现（P1×1、P2×5、P3×6——原文总览表计 P2×5/P3×3 为第 1 轮口径，第 2 轮追加 CON-08..11 后实际 P3×6）全部保留，无驳回、无降级；严重程度经独立推演全部恰当。证据行号抽查精确（唯一瑕疵：CON-06 引用 `RemoteGraphExecutionPlanBuilder.java:344` 未写 `transport/` 目录、CON-09 引用 `SupervisionLoop:837-847` 实为 `:839-847`，均不影响结论）。建议最终汇总按上表收录。

## 最终保留项

| 编号 | 严重程度 | 一句话结论 | 裁决 |
|---|---|---|---|
| CON-01 | P1 | 能力矩阵钉定测试断言 SINGLE_INSTANCE 与 live 工厂 PARALLEL 不可同时成立，护栏必红/失效；工厂侧为 2026-10-02 用户裁定，测试该更新 | 保留 |
| CON-08 | P2 | File 2PC sink 键无作业命名空间：outputDir 复用时新运行数据被 manifest guard 静默吞掉（R5-CON-01 族在 file sink 完整存活，guard 零日志） | 保留 |
| CON-02 | P2 | DebeziumEngineWrapper.stop() 启动窗口早退泄漏引擎，且 wrapper 置 null 后无任何补救关闭路径，泄漏静默 | 保留 |
| CON-03 | P2 | offset 恢复为 put-merge 非 replace：同 JVM 恢复时 registry 较新分区 offset 幸存 → 静默跳读丢数据 | 保留 |
| CON-04 | P2 | epoch 数据文件 commit 时才 fsync、recordCount 零生产读者：OS 崩溃窗口可静默提交截断文件 | 保留 |
| CON-05 | P2 | JDBC ledger namespace 回退 tableName：直连构造 + 双作业共表 + 共台账库时 guard 误伤（R5-CON-01 残余面） | 保留 |
| SEC-01 | P2 | extraProperties 后置合并无键黑名单：connector.class 白名单与「无静默内存 schema history」两声明不变式可被绕过 | 保留 |
| CON-06 | P3 | batch-loader 描述符声明 PARALLEL 与运行期单写入者门禁、owner doc 三方不一致（声明面漂移） | 保留 |
| CON-09 | P3 | ledger namespace 的 jobId 两值族（raw jobName vs sanitized）：跨执行模式恢复幂等 guard 失效 → 重复提交 | 保留 |
| CON-10 | P3 | debezium-cdc source 并行度变更恢复无 typed 守卫：scale-down 静默丢 offset → 重路由表重复投递，与 2PC sink 裁定不对称 | 保留 |
| CON-07 | P3 | Debezium 事件无时间/数值类型还原 + ts_ms 直转与 asLong 防御不一致：提交期方言失败循环、诊断无指向 | 保留 |
| SEC-02 | P3 | schemaHistoryJdbcTable/signalDataCollection 等标识符直传引擎内部 SQL，平台侧无校验（加固项） | 保留 |
| 安全五项 | 结论 | SQL 注入通过 / 白名单不通过（SEC-01）/ 凭证通过（附边界）/ 主机白名单不适用 / 直传部分通过——五项结论全部维持 | 维持 |
| 负面清单 | 抽查 | 8 项关键路径抽查全部吻合，可信 | 维持 |
