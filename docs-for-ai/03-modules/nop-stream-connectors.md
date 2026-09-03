# nop-stream 连接器目录与能力矩阵

> 定位：nop-stream 现有连接器族的**逐连接器能力矩阵**（方向 / 交付语义 / 并行度 / 恢复语义）+ **SPI 注册中心类型名清单**（item 19 / P-REQ-28）。
> 使用指引（语义组合规则、XDSL/Java 接线）见 `03-modules/nop-stream-user-guide.md`「连接器使用指引」；CDC 专项操作手册见 `03-modules/nop-stream-cdc-cookbook.md`。
> 本表每行语义标注均与 live 代码核对一致（核对锚点 = 行为级测试，2026-09-03 全量复核；SPI 能力声明的代码级单一事实源 = `nop-stream-runtime` 测试 `TestStreamConnectorRegistryDiscovery`，2026-09-03 起）。

## 模块清单

| 模块 | 组件 |
|---|---|
| `nop-stream-connector` | `FileSource`、`FileSourceReader`、`FileTwoPhaseCommitSink`、`MessageSourceFunction`、`MessageSinkFunction` |
| `nop-stream-connector-jdbc` | `JdbcTwoPhaseCommitSink`（+ `JdbcTwoPhaseCommitSinkBuilder`） |
| `nop-stream-connector-debezium` | `DebeziumCdcSourceFunction` |
| `nop-stream-connector-batch` | `BatchLoaderSourceFunction`、`BatchConsumerSinkFunction`（nop-batch 桥接） |

## SPI 注册中心与类型名

连接器端点组件经 **SPI 工厂**注册（`nop-stream-core` `io.nop.stream.core.connector.registry` 包，item 19 / P-REQ-28）。类型名命名空间为**方向作用域**——`(方向, 类型名)` 唯一；本期 8 个内建工厂**均无别名**。注册载体 = 各连接器模块 `_vfs/nop/stream/beans/connector-*.beans.xml` 中的无状态工厂 bean（NopIoC 承载，仅显式装配时加载，不进全局 app 容器）。

| 方向 | 类型名 | 端点组件 | 工厂类（代码锚点） | beans.xml |
|---|---|---|---|---|
| source（FLIP-27 split） | `file` | `FileSource` | `io.nop.stream.connector.file.FileSourceConnectorFactory` | `connector-file.beans.xml` |
| source | `message` | `MessageSourceFunction` | `io.nop.stream.connector.MessageSourceConnectorFactory` | `connector-message.beans.xml` |
| source | `debezium-cdc` | `DebeziumCdcSourceFunction` | `io.nop.stream.connector.debezium.DebeziumCdcSourceConnectorFactory` | `connector-debezium.beans.xml` |
| source | `batch-loader` | `BatchLoaderSourceFunction` | `io.nop.stream.connector.batch.BatchLoaderSourceConnectorFactory` | `connector-batch.beans.xml` |
| sink | `file` | `FileTwoPhaseCommitSink` | `io.nop.stream.connector.file.FileTwoPhaseCommitSinkConnectorFactory` | `connector-file.beans.xml` |
| sink | `message` | `MessageSinkFunction` | `io.nop.stream.connector.MessageSinkConnectorFactory` | `connector-message.beans.xml` |
| sink | `jdbc-2pc` | `JdbcTwoPhaseCommitSink` | `io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSinkConnectorFactory` | `connector-jdbc.beans.xml` |
| sink | `batch-consumer` | `BatchConsumerSinkFunction` | `io.nop.stream.connector.batch.BatchConsumerSinkConnectorFactory` | `connector-batch.beans.xml` |

**注册资格集**：仅端点组件（上表 8 个）。`FileSourceReader`（由 `FileSource.createReader()` 内部构造）、`JdbcTwoPhaseCommitSinkBuilder`（fluent builder，产物已注册）等非端点组件显式不注册（不注册理由：非独立实例化端点 / 非端点辅助物）。

**单一事实源**：能力描述符的交付语义声明必须等于端点实例的 `getSourceConsistency()`/`getSinkConsistency()`；`PLANNING_GATE_PARALLELISM_1` ⟺ `TwoPhaseCommitSinkFunction` 实例（与规划期门禁同键）。由发现测试与 catalog 探测运行期双重钉定。

**维护/探测工具入口**（`StreamConnectorCatalog`，落 core）：装配 + 枚举 + 探测用法：

```java
// 1. 装配：发现 classpath 上全部 connector-*.beans.xml 并构建注册容器
BeanContainerBuilder builder = new BeanContainerBuilder(null);
StreamConnectorCatalog.discoverConnectorBeansResources().forEach(builder::addResource);
IBeanContainerImplementor container = builder.build("stream-connectors");
container.start();

// 2. 枚举清单 + 能力（方向/交付语义/并行度/恢复语义/参数规格）
StreamConnectorCatalog catalog = StreamConnectorCatalog.of(container);
System.out.println(catalog.renderListing());

// 3. 探测：类型名 → 注册中心解析 → 工厂构造 → 能力一致性校验 → 关闭
Map<String, Object> params = Map.of("outputDir", "/tmp/out");
ConnectorProbeResult result = catalog.probe(ConnectorDirection.SINK, "file",
        new StreamConnectorConfig("file", params));
```

未知类型名 / 方向错配 = fail-fast typed error（含已注册清单）；字段级 conf 校验**不在**此处（item 20 conf-validate 边界）。XDSL 声明形态（bean 引用 + 内联 xpl）本期不变，类型化声明暂未引入（引入时将一并裁定 `bean`/类型名冲突与 params 消费语义）。

## 能力矩阵

| 组件 | 方向 | 交付语义（含依据） | 并行度 | 恢复语义（cursor/offset checkpoint 路径） |
|---|---|---|---|---|
| `FileSource`（SPI `file` source） | source（有界，FLIP-27 风格 split-based） | **at-least-once**（source 边界）：per-split 字节光标 checkpoint，恢复后从光标重放；无事务读。`notifyCheckpointComplete` 为 no-op（文件 offset 天然幂等） | **支持并行**（split 按 discovery index 轮询分配到 subtask，`FileSplitEnumerator`） | 双通道状态：reader 侧 split 列表 + 光标入 operator state（key `source-reader-splits`）；enumerator 侧发现/分配/完成状态经 `SourceEnumeratorSnapshot` 随 epoch manifest 持久化 |
| `FileSourceReader` | source reader | 同 `FileSource`（字节精确光标：`currentOffset` = 下一读取字节位，按 LF/CRLF/CR 精确计字节，跨平台一致） | 随 source | 恢复后 `openSplit` seek 到 checkpoint 字节位，`activeBytesConsumed` 以恢复基线播种——首个快照不回退光标（`TestFileSourceAuditFixes.snapshotAfterFirstPostRestorePollAdvancesFromRestoredBase`） |
| `FileTwoPhaseCommitSink`（SPI `file` sink） | sink（文件） | **exactly-once（两阶段提交）**：`getSinkConsistency()=TWO_PHASE_COMMIT`；每 epoch 写 `.epoch-N.tmp` → `saveState` 先落盘 → checkpoint durable 后 `ATOMIC_MOVE` 到终文件 + 原子更新 `manifest.properties`；commit 幂等（manifest 键守卫）；崩溃在 rename 与 manifest 之间只修复 manifest | **规划期门禁 P=1**（见下节门禁）；subtask>0 有独立后缀副本机制（防御纵深，测试钉定） | pending 事务（tempPath/recordCount/subtaskIndex）入 checkpoint（key `pending-commits`）；恢复时 durable-未提交 epoch 重提交、未 durable epoch abort；杀恢复 exactly-once 由 `TestFileTwoPhaseCommitSink.testFileSinkKillRecoverExactlyOnce` 钉定 |
| `MessageSourceFunction`（SPI `message` source） | source（无界） | **AT_LEAST_ONCE（声明）**：`getSourceConsistency()=AT_LEAST_ONCE`；适配器本身无 ack/offset 逻辑，实际语义由注入的 `IMessageService` 后端承载——Kafka（手动 commitSync、失败 seek 回退）= at-least-once；Pulsar/SysDao 同级；**LocalMessageService 无订阅者即丢弃 = 实际 best-effort**。`onMessage` 首错捕获后从 `run()` 重抛（不假成功） | **支持并行**：`{topic}-{subtaskIndex}` 逐分区主题模式（producer 侧须写分区主题） | **无 offset checkpoint**（非 `CheckpointedSourceFunction`）——重启后重投递行为完全由后端订阅语义决定（Kafka group offset / Pulsar subscription / SysDao 租约） |
| `MessageSinkFunction`（SPI `message` sink） | sink（消息） | **AT_LEAST_ONCE（声明）**：每条记录同步 `messageService.send`（阻塞 send future）；无 checkpoint 参与、无去重。实际语义后端承载（Kafka `acks=all,retries=3` = at-least-once；Local 后端无订阅者丢弃 = best-effort） | 无并行限制 | 无状态（per-record 直发）；不参与 checkpoint |
| `JdbcTwoPhaseCommitSink`（SPI `jdbc-2pc` sink） | sink（JDBC 表） | **exactly-once（两阶段提交）**：`TWO_PHASE_COMMIT`；每 epoch = 一个独立 JDBC 事务（数据行 batch + `stream_epoch_ledger` 台账行同事务提交）；`saveState` 先把缓冲移入 pendingCommits（本 checkpoint 捕获本 epoch，防滞后一档）；commit 幂等（台账 `(epoch_id, subtask_id)` 守卫）；失败回滚 | **规划期门禁 P=1**（见下节）；per-subtask 台账键隔离机制存在（测试钉定，防御纵深） | pending 事务入 checkpoint；恢复时 durable-未提交重提交（台账幂等）、未 durable abort（"data was never written"）；`TestJdbcTwoPhaseCommitSinkDeep.testRestoreFromEpochDurableReCommit` / `testRestoreFromEpochNonDurableAbort` 钉定 |
| `JdbcTwoPhaseCommitSinkBuilder` | （构建器） | fluent builder：`jdbcTemplate/tableName/ledgerTableName/columns/addColumn/recordMapper/build`（build 恒 subtaskIndex=0）；SPI 工厂 `jdbc-2pc` 经此 builder 构造 | — | — |
| `DebeziumCdcSourceFunction`（SPI `debezium-cdc` source） | source（CDC，无界 + 可 DRAIN） | **REPLAYABLE（offset checkpoint，可重放）**：`getSourceConsistency()=REPLAYABLE`；Debezium engine offset 经 `NopStreamOffsetBackingStore` 桥接，恢复后从 checkpoint 位点续读（不丢不重） | 单实例（connector name 必填——未命名共享 `_default_` 桶 fail-fast） | offset map（base64 TreeMap）入 operator state（key `cdc-offsets`）；首次启动（无状态）走 fresh snapshot（默认 `snapshot.mode=initial`），有状态恢复从位点续读；杀恢复零重复由 `TestDebeziumCdcCheckpoint.testCdcCheckpointKillRecoverNoDuplicates` 钉定。详见 CDC cookbook |
| `BatchLoaderSourceFunction`（SPI `batch-loader` source） | source（有界，nop-batch 桥接） | **AT_LEAST_ONCE（声明）**：适配 `IBatchLoaderProvider`；发射计数 offset checkpoint + `seek()`。注意：`seek` 只置计数器字段，`run` 不跳过记录——实际断点续读取决于 `IBatchLoader` 实现 | 无并行限制 | 发射计数 offset 入 operator state（key `source-offset`）；恢复时 `restoreState` 解析并调 `seek(offset)`；算子级 round-trip 由 `TestBatchLoaderSourceFunction.testStreamSourceOperatorCheckpointRestoreWithBatchLoader` 钉定 |
| `BatchConsumerSinkFunction`（SPI `batch-consumer` sink） | sink（nop-batch 桥接） | **IDEMPOTENT（声明）**：缓冲 + 批 flush 到 `IBatchConsumerProvider`；非事务——exactly-once 责任在消费端幂等。flush 失败抛错且**保留缓冲重试**；`close()` flush 失败显式报「data may be lost」 | 无并行限制（单线程契约，见类 Javadoc） | 无 checkpoint 参与（非 `CheckpointParticipant`）；失败重试靠缓冲保留 |

## 硬门禁：2PC sink 并行度

任何 `TwoPhaseCommitSinkFunction` 族 sink（file/jdbc 内建 + 用户子类）在**有效并行度 > 1** 时被 `StreamGraphGenerator` 规划期 fail-fast 拒绝：

- 错误码：`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`（`nop.err.stream.2pc-sink-parallelism-not-supported`）
- 消息语义：exactly-once 输出仅在 parallelism=1 被证明；P>1 会静默丢数据，显式拒绝而非降级
- 钉定测试：`TestStreamGraphGenerator.testTwoPhaseCommitSinkAtParallelism2IsRejected`（含错误码与参数断言）/ `testTwoPhaseCommitSinkAtParallelism1BuildsGraph` / `testNonTwoPhaseCommitSinkAtParallelism2IsAllowed`（门禁只针对 2PC sink）
- 内建 sink 的 `copyForSubtask` 逐 subtask 隔离机制存在且被测试钉定（`TestJdbcTwoPhaseCommitSinkParallelIsolation` 等），但**不解除**该门禁——并行 2PC 属后继能力

## 语义组合速查

| 目标 | source | sink | 结果 |
|---|---|---|---|
| 端到端 exactly-once | `REPLAYABLE`（Debezium CDC）/ cursor-checkpoint（File） | `TWO_PHASE_COMMIT`（File/JDBC 2PC sink） | exactly-once（`StreamRequirementValidator` 对 `STRICT_EXACTLY_ONCE` 管线 build 期校验此组合，违配 fail-fast） |
| at-least-once | message/batch source | message/batch sink | at-least-once / 幂等消费（组合不满足 STRICT_EXACTLY_ONCE，校验器显式拒绝而非静默降级） |

## 恢复验证锚点索引（行为级测试）

| 组件 | 测试（模块 `src/test/java` 下） |
|---|---|
| FileSource/Reader | `TestFileSourceAuditFixes`（kill-recover exactly-once、恢复后光标推进、CR/LF 字节精确）、`TestFileSourceCheckpointRestore`（enumerator/reader 状态 round-trip）、`TestFileSource` |
| FileTwoPhaseCommitSink | `TestFileTwoPhaseCommitSink`（kill-recover exactly-once、幂等 commit、manifest 修复、abort、coordinator 驱动 commit） |
| JdbcTwoPhaseCommitSink | `TestJdbcTwoPhaseCommitSinkDeep`（saveState 捕获本 epoch、幂等、durable/未 durable 恢复、subsuming commit、独立事务）、`TestJdbcTwoPhaseCommitSinkSkeleton`、`TestJdbcTwoPhaseCommitSinkParallelIsolation` |
| MessageSource/Sink | `TestMessageAdapters`、`TestMessageSourceFunctionRestart`、`TestMessageSourceFunctionThreadSafety`、`TestConnectorResourceManagement` |
| DebeziumCdcSourceFunction | `TestDebeziumCdcCheckpoint`（offset round-trip ×3、注入链、kill-recover 零重复、首启不吃陈旧 offset）、`TestDebeziumCdcSourceFunction`、`TestDebeziumCdcSourceCompletion`、`TestDebeziumResourceManagement` |
| BatchLoader/BatchConsumer | `TestBatchLoaderSourceFunction`（offset/seek/算子 round-trip）、`TestBatchConsumerSinkFunction*`（缓冲/flush/close/失败保留重试）、`TestConnectorConsistencyCapability`（声明矩阵 + STRICT 校验） |
| **SPI 注册中心** | `nop-stream-core` `TestStreamConnectorRegistry`（注册机制：枚举/别名优先级/重复/描述符校验/未知类型/方向错配/必填参数）；各连接器模块 `Test*ConnectorFactor*`（工厂构造 + 实例级单一事实源对齐）；`nop-stream-runtime` `TestStreamConnectorRegistryDiscovery`（**全量注册发现 + 能力矩阵逐项断言——本页矩阵的代码级单一事实源**）、`TestStreamConnectorRegistryE2E`（registry 构造端点 → `env.execute()` → 输出，Anti-Hollow 接线证明） |
