# nop-stream 连接器目录与能力矩阵

> 定位：nop-stream 现有连接器族的**逐连接器能力矩阵**（方向 / 交付语义 / 并行度 / 恢复语义）。
> 使用指引（语义组合规则、XDSL/Java 接线）见 `03-modules/nop-stream-user-guide.md`「连接器使用指引」；CDC 专项操作手册见 `03-modules/nop-stream-cdc-cookbook.md`。
> 本表每行语义标注均与 live 代码核对一致（核对锚点 = 行为级测试，2026-09-03 全量复核）。

## 模块清单

| 模块 | 组件 |
|---|---|
| `nop-stream-connector` | `FileSource`、`FileSourceReader`、`FileTwoPhaseCommitSink`、`MessageSourceFunction`、`MessageSinkFunction` |
| `nop-stream-connector-jdbc` | `JdbcTwoPhaseCommitSink`（+ `JdbcTwoPhaseCommitSinkBuilder`） |
| `nop-stream-connector-debezium` | `DebeziumCdcSourceFunction` |
| `nop-stream-connector-batch` | `BatchLoaderSourceFunction`、`BatchConsumerSinkFunction`（nop-batch 桥接） |

## 能力矩阵

| 组件 | 方向 | 交付语义（含依据） | 并行度 | 恢复语义（cursor/offset checkpoint 路径） |
|---|---|---|---|---|
| `FileSource` | source（有界，FLIP-27 风格 split-based） | **at-least-once**（source 边界）：per-split 字节光标 checkpoint，恢复后从光标重放；无事务读。`notifyCheckpointComplete` 为 no-op（文件 offset 天然幂等） | **支持并行**（split 按 discovery index 轮询分配到 subtask，`FileSplitEnumerator`） | 双通道状态：reader 侧 split 列表 + 光标入 operator state（key `source-reader-splits`）；enumerator 侧发现/分配/完成状态经 `SourceEnumeratorSnapshot` 随 epoch manifest 持久化 |
| `FileSourceReader` | source reader | 同 `FileSource`（字节精确光标：`currentOffset` = 下一读取字节位，按 LF/CRLF/CR 精确计字节，跨平台一致） | 随 source | 恢复后 `openSplit` seek 到 checkpoint 字节位，`activeBytesConsumed` 以恢复基线播种——首个快照不回退光标（`TestFileSourceAuditFixes.snapshotAfterFirstPostRestorePollAdvancesFromRestoredBase`） |
| `FileTwoPhaseCommitSink` | sink（文件） | **exactly-once（两阶段提交）**：`getSinkConsistency()=TWO_PHASE_COMMIT`；每 epoch 写 `.epoch-N.tmp` → `saveState` 先落盘 → checkpoint durable 后 `ATOMIC_MOVE` 到终文件 + 原子更新 `manifest.properties`；commit 幂等（manifest 键守卫）；崩溃在 rename 与 manifest 之间只修复 manifest | **规划期门禁 P=1**（见下节门禁）；subtask>0 有独立后缀副本机制（防御纵深，测试钉定） | pending 事务（tempPath/recordCount/subtaskIndex）入 checkpoint（key `pending-commits`）；恢复时 durable-未提交 epoch 重提交、未 durable epoch abort；杀恢复 exactly-once 由 `TestFileTwoPhaseCommitSink.testFileSinkKillRecoverExactlyOnce` 钉定 |
| `MessageSourceFunction` | source（无界） | **AT_LEAST_ONCE（声明）**：`getSourceConsistency()=AT_LEAST_ONCE`；适配器本身无 ack/offset 逻辑，实际语义由注入的 `IMessageService` 后端承载——Kafka（手动 commitSync、失败 seek 回退）= at-least-once；Pulsar/SysDao 同级；**LocalMessageService 无订阅者即丢弃 = 实际 best-effort**。`onMessage` 首错捕获后从 `run()` 重抛（不假成功） | **支持并行**：`{topic}-{subtaskIndex}` 逐分区主题模式（producer 侧须写分区主题） | **无 offset checkpoint**（非 `CheckpointedSourceFunction`）——重启后重投递行为完全由后端订阅语义决定（Kafka group offset / Pulsar subscription / SysDao 租约） |
| `MessageSinkFunction` | sink（消息） | **AT_LEAST_ONCE（声明）**：每条记录同步 `messageService.send`（阻塞 send future）；无 checkpoint 参与、无去重。实际语义后端承载（Kafka `acks=all,retries=3` = at-least-once；Local 后端无订阅者丢弃 = best-effort） | 无并行限制 | 无状态（per-record 直发）；不参与 checkpoint |
| `JdbcTwoPhaseCommitSink` | sink（JDBC 表） | **exactly-once（两阶段提交）**：`TWO_PHASE_COMMIT`；每 epoch = 一个独立 JDBC 事务（数据行 batch + `stream_epoch_ledger` 台账行同事务提交）；`saveState` 先把缓冲移入 pendingCommits（本 checkpoint 捕获本 epoch，防滞后一档）；commit 幂等（台账 `(epoch_id, subtask_id)` 守卫）；失败回滚 | **规划期门禁 P=1**（见下节）；per-subtask 台账键隔离机制存在（测试钉定，防御纵深） | pending 事务入 checkpoint；恢复时 durable-未提交重提交（台账幂等）、未 durable abort（"data was never written"）；`TestJdbcTwoPhaseCommitSinkDeep.testRestoreFromEpochDurableReCommit` / `testRestoreFromEpochNonDurableAbort` 钉定 |
| `JdbcTwoPhaseCommitSinkBuilder` | （构建器） | fluent builder：`jdbcTemplate/tableName/ledgerTableName/columns/addColumn/recordMapper/build`（build 恒 subtaskIndex=0） | — | — |
| `DebeziumCdcSourceFunction` | source（CDC，无界 + 可 DRAIN） | **REPLAYABLE（offset checkpoint，可重放）**：`getSourceConsistency()=REPLAYABLE`；Debezium engine offset 经 `NopStreamOffsetBackingStore` 桥接，恢复后从 checkpoint 位点续读（不丢不重） | 单实例（connector name 必填——未命名共享 `_default_` 桶 fail-fast） | offset map（base64 TreeMap）入 operator state（key `cdc-offsets`）；首次启动（无状态）走 fresh snapshot（默认 `snapshot.mode=initial`），有状态恢复从位点续读；杀恢复零重复由 `TestDebeziumCdcCheckpoint.testCdcCheckpointKillRecoverNoDuplicates` 钉定。详见 CDC cookbook |
| `BatchLoaderSourceFunction` | source（有界，nop-batch 桥接） | **AT_LEAST_ONCE（声明）**：适配 `IBatchLoaderProvider`；发射计数 offset checkpoint + `seek()`。注意：`seek` 只置计数器字段，`run` 不跳过记录——实际断点续读取决于 `IBatchLoader` 实现 | 无并行限制 | 发射计数 offset 入 operator state（key `source-offset`）；恢复时 `restoreState` 解析并调 `seek(offset)`；算子级 round-trip 由 `TestBatchLoaderSourceFunction.testStreamSourceOperatorCheckpointRestoreWithBatchLoader` 钉定 |
| `BatchConsumerSinkFunction` | sink（nop-batch 桥接） | **IDEMPOTENT（声明）**：缓冲 + 批 flush 到 `IBatchConsumerProvider`；非事务——exactly-once 责任在消费端幂等。flush 失败抛错且**保留缓冲重试**；`close()` flush 失败显式报「data may be lost」 | 无并行限制（单线程契约，见类 Javadoc） | 无 checkpoint 参与（非 `CheckpointParticipant`）；失败重试靠缓冲保留 |

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
