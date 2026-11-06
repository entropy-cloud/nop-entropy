# nop-stream 流处理引擎 Owner Doc

> 定位：nop-stream 的运维与使用契约 owner doc（item 16 交付的落点）。
> 本文只记录**已实现**的用户可见契约；架构决策记录属于平台内部设计文档，不在本文展开。
> 用户面文档族（使用指南 / 连接器目录 / CDC cookbook / 迁移指南 / 快速起步脚手架）见 `03-modules/nop-stream-user-guide.md` 首部导航——本文与其分工：**怎么运行运维** 在本文，**怎么用起来** 在用户文档族。

## 模块路由

| 子模块 | 职责 |
|---|---|
| `nop-stream-core` | 核心 API（DataStream、算子、状态后端契约、checkpoint 协议类型） |
| `nop-stream-runtime` | 分布式运行时（JobCoordinator / TaskManager / 控制面 RPC / 运维面） |
| `nop-stream-cep` | CEP 模式匹配 |
| `nop-stream-rocksdb` | RocksDB 状态后端 |
| `nop-stream-connector{,-batch,-jdbc,-debezium}` | 连接器族 |
| `nop-stream-connector-kafka` | Kafka 原生连接器（KafkaSourceFunction + KafkaTwoPhaseCommitSink exactly-once sink） |
| `nop-stream-format-avro` | Avro/Schema Registry payload 格式层（Confluent wire format，当前主要消费面 = Kafka 连接器） |
| `nop-stream-flow` | XDSL 流程集成 |
| `nop-stream-sql` | 声明式 SQL 窄范围接口（`.stream.xml` 顶层 `<sql>` 元素编译宿主；使用文档 `03-modules/nop-stream-sql.md`） |
| `nop-stream-fraud-example` | 复合场景示例（S1/S2 + 分布式演练） |

## 定时器异常语义（fail-fast，P1-6.7-007，plan 2387）

`TimerServiceManager.advanceWatermark/fireProcessingTimeTimers` 对注册的 timer service 逐个尝试：任一失败时**首个异常在全部 service 尝试完毕后抛出**（后续失败 `addSuppressed` 附带）——定时器触发失败必须上抛致任务失败，不允许「失败 service 被跳过 + watermark 照常转发」的静默丢发射。`ProcessingTimeServiceDriver` 的 mail action 不二次吞该异常（经 `TaskMailbox.drainAndRun` 传播至任务循环，与 `TaskProcessingTimeService.fireDueTimers` 直接路径同语义；受检异常按 `ERR_STREAM_OPERATOR_ERROR` 包装为 unchecked）。

## Source reader 恢复时序契约（P1-6.7-008，plan 2387）

`SourceReaderOperator.restoreState` 支持恢复路径的 restore-先于-open 时序（`SupervisionLoop.rebuildTask` 对新建链恢复时 reader 尚未创建）：per-split 位点暂存于 operator，`open()` 内按「reader 创建 → enumerator 交付 flush（追加语义）→ restore flush（**替换语义**，后到者为准）→ `reader.start()`」顺序 flush——`SourceReader.restoreState` 契约为 "Called before start() on recovery"。恢复→重开→续读的 reader 侧位点不丢。

## 双流等值 join 语义注记（P1-2.8-001/002/003，plan 2389）

EquiJoinOperator（hash/window 双形态）三条契约：①NULL 等值键不与任何记录配对（SQL NULL 语义，含另一 NULL），OUTER 形态按未匹配补齐、INNER 随水位修剪丢弃，缓冲键以保留哨兵承载（不与字面量 "null" 键碰撞）；②补齐前缘=水位——仅 ts≤watermark 的未匹配记录被补齐，乱序未越界记录留下轮，配对恰一次（exactly-once）；③恢复路径 restoreState 先于 open（rebuild 链 transient 未建）时快照暂存、open 时合并，合并推进插入序号保证 restore 后新增元素不覆盖恢复条目。规范性设计档为平台内部文档（ai-dev/design/nop-stream/ 下 join-operator 档 §3，按 docs-for-ai 边界规则不以路径引用）。

## 可观测性与运维面

### 分层指标标准集（P-REQ-1 / P-REQ-8）

命名规范：`nop.stream.<layer>.<name>`（snake_case；Prometheus 输出时 `.` → `_`）。指标总是注册进 nop-stream 进程级组合注册表（`io.nop.stream.core.metrics.StreamMetricsRegistries`）；是否暴露由运维 HTTP 端点配置决定（见下）。**本表是指标名与语义的唯一权威名表。**

**engine 层（作业/集群/协调器）** — 更新点：JobCoordinator / CheckpointCoordinator 真实生命周期路径

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.engine.nodes.active` | gauge | —（cluster 级） | 集群注册表当前活跃节点数 |
| `nop.stream.engine.checkpoints.completed` | counter | jobId | durable checkpoint 完成数 |
| `nop.stream.engine.checkpoints.failed` | counter | jobId | checkpoint 持久化失败数 |
| `nop.stream.engine.checkpoints.aborted` | counter | jobId | checkpoint 中止数（含超时/恢复中止） |
| `nop.stream.engine.checkpoint.duration` | timer | jobId | checkpoint 触发→durable 完成时长 |
| `nop.stream.engine.checkpoint.size.bytes` | gauge | jobId | 最新完成 checkpoint 大小（字节） |
| `nop.stream.engine.recoveries.total` | counter | jobId | 全局恢复（globalRecovery）次数 |
| `nop.stream.engine.barrierFanoutDrops.total` | counter | jobId | barrier fan-out 每节点队列饱和丢弃数（CRA 8.41 R4-01 W-10，plan 2534 裁定 B.3：有界队列 100 满/丢弃该 epoch 的该节点 barrier 发送——checkpoint 超时 abort 兜底；伴随限频 WARN，W-13 三态抑制） |

**task 层（任务）** — 更新点：TaskManager 部署/取消/失败/终态上报路径

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.task.deployed.total` | counter | nodeId | 该节点已部署任务数（assignment + deployTask 两路径） |
| `nop.stream.task.cancelled.total` | counter | nodeId | 该节点被取消任务数 |
| `nop.stream.task.failures.total` | counter | nodeId | 该节点任务失败数（部署失败 + 运行失败终态） |
| `nop.stream.task.running` | gauge | nodeId | 该节点当前在跑任务数（不含已完成；plan 358 起 gauge 随 TaskManager.stop 释放，节点重启后注册新 supplier） |
| `nop.stream.task.ackSendFailures.total` | counter | nodeId | checkpoint ACK 有限重试（3 次）耗尽后的失败数（plan 358 Fix-6；超时 abort 兜底仍存在） |
| `nop.stream.task.commitQueueDepth` | gauge | nodeId | 2PC commit 专用执行器等待队列当前深度（plan 369 Phase 4 R5-CC-10 有界队列积压观测；随 TaskManager.stop 释放） |
| `nop.stream.task.commitCallerRuns.total` | counter | nodeId | commit 队列打满后回退调用方线程执行（CallerRuns，不丢通知）的次数（plan 369 Phase 4 R5-CC-10 饱和打点） |

**operator 层（算子）** — 更新点：StreamTaskInvokable 数据面热路径（LOCAL 与 REMOTE 两条执行路径均注入）

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.operator.records.in.total` | counter | jobId/vertexId/subtask | 从输入通道分发进算子链的记录数（MIDDLE/SINK 角色） |
| `nop.stream.operator.records.out.total` | counter | jobId/vertexId/subtask | 经本任务 RecordWriter 发射的记录数 |
| `nop.stream.operator.processing.time` | timer | jobId/vertexId/subtask | 单记录算子链处理时长（输入分发计时） |
| `nop.stream.operator.time.busy.ratio` | gauge | jobId/vertexId/subtask | **忙占比（WI16）**：=(processingTime.sum − emitTime.sum)/elapsed.sum——emit 嵌套于 processing，减法严格；elapsed=0 时 NaN；**任务结束后整组 task meter 条目移除（抓取序列消失）——CRA 8.41 R4-01 W-14（plan 2534 裁定 E.2）起取代旧「恒 NaN 序列」契约；依赖 NaN 哨兵的下游看板/告警需迁移** |
| `nop.stream.operator.time.backpressured.ratio` | gauge | jobId/vertexId/subtask | **背压占比（WI16）**：=emitTime.sum/elapsed.sum（emit 即「生产侧背压代理」） |
| `nop.stream.operator.time.idle.ratio` | gauge | jobId/vertexId/subtask | **空闲占比（WI16）**：=idleTime.sum/elapsed.sum（idle = read 返回空的迭代切片） |
| `nop.stream.operator.time.idle.nanos` | counter | jobId/vertexId/subtask | idle 切片累计（纳秒；WI16） |
| `nop.stream.operator.time.elapsed.nanos` | counter | jobId/vertexId/subtask | 主循环迭代切片累计（纳秒，占比分母；WI16）。**残差口径**：busy+backpressured+idle 可小于 1——watermark 处理/side-output 发射/barrier 写出等段不计入任何态（other 残差） |
| `nop.stream.operator.watermark` | gauge | jobId/vertexId/subtask | 输入 gate 合并 watermark 当前值（WI16；**首 watermark 前 = NaN** 哨兵，不用 MIN_VALUE 以免毒化聚合；任务结束后条目移除、抓取序列消失——W-14 裁定 E.2） |
| `nop.stream.operator.watermark.channel.lag` | gauge | jobId/vertexId/subtask | **级联滞后（WI16）**：max(非 idle 通道 wm) − merged wm（两侧同剔除 idle 语义）；滞后上升 = 有通道领先而另一通道钉住 min |
| `numLateRecordsDropped` | counter | operator/subtask（W-15 稳定键） | 迟到丢弃记录数：WindowOperator（跳过窗口且超 allowedLateness、无迟到侧输出）与 CepOperator（事件时间戳 ≤ 当前 watermark、无迟到侧输出）的丢弃路径递增（plan 366 Phase 3 接线）。**键形（CRA 8.41 R4-01 W-15，plan 2534 裁定 E.3）**：有 TaskLocation 时 `jobId.pipeline.vertex`+taskIndex（既有 W-M1 键形保留）；无 location 回退 `window`+subtask 标签；identityHashCode 键废除；算子 close() 移除本实例 meter。命名例外：保留 Flink 兼容的算子级指标名，未套 `nop.stream.*` 前缀 |

**io 层（输入输出）** — 更新点：source 发射点 + 跨任务 writer 发射点

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.io.records.consumed.total` | counter | jobId/vertexId/subtask | source 侧喂入管线的记录数（SOURCE/SELF_CONTAINED 角色） |
| `nop.stream.io.records.emitted.total` | counter | jobId/vertexId/subtask | 发往下游任务的记录数（跨任务发射） |
| `nop.stream.io.emit.time` | timer | jobId/vertexId/subtask | 跨任务发射（writer.emit）本身耗时——**生产侧背压代理**：下游交换队列满时 emit 阻塞，该 timer 抬升即量化输出侧背压 |
| `nop.stream.io.channel.queue.fill.ratio` | gauge | jobId/edgeId/sourceSubtask/targetSubtask | **队列填充比（WI16）**：size/capacity clamp [0,1]——`queue.size` 同一 holder 上的派生读数（非第二探针；scrape 侧免硬编码容量），同 bind/close 生命周期；local 路径由 `ResultPartition.size()/getTotalCapacity()` 推导（不建直测面） |
| `nop.stream.io.channel.queue.size` | gauge | jobId/edgeId/sourceSubtask/targetSubtask | 跨任务通道（`RemoteInputChannel`）本地消费队列当前深度——数据面通道水位**直测**（item 32；Prometheus wire 名 `nop_stream_io_channel_queue_size`）。**注册范围（D2b）**：仅对激活订阅的通道注册（协调器零订阅形态与 construct-only 镜像通道不注册，避免整面恒 0 噪声）；**重注册/关闭语义（D2c）**：恢复重建经可变 holder 重绑既有 meter（无 micrometer 静默丢弃/冻结值），通道关闭释放绑定置 0；**聚合口径**：per-edge/max 为采集侧关注口径（最差通道即背压信号，sum 随通道矩阵规模缩放）；无独立溢出 gauge（溢出经 `ERR_STREAM_CHANNEL_OVERFLOW` typed 失败可观察，指标面保持最小） |

**state 层（状态后端，P-REQ-8）** — 更新点：RocksDBKeyedStateBackend 打开路径（RocksDB aggregated properties 直读；个别属性在特定 RocksDB 构建下不可用时该 gauge 值为 NaN 并告警一次[8.43 兑现：once-guard 形态——首失败 WARN 一次、后续降 debug；state 层 gauge 标签与摘除语义见下方 CRA 8.43 行为块]）

| 指标名 | 类型 | 语义 |
|---|---|---|
| `nop.stream.state.rocksdb.block.cache.usage` | gauge | block cache 已用字节 |
| `nop.stream.state.rocksdb.block.cache.pinned` | gauge | block cache pinned 字节 |
| `nop.stream.state.rocksdb.memtable.usage.total` | gauge | 全部 memtable 大小 |
| `nop.stream.state.rocksdb.estimate.num.keys` | gauge | 估算键数 |
| `nop.stream.state.rocksdb.compaction.pending.bytes` | gauge | 待 compaction 字节 |
| `nop.stream.state.rocksdb.compactions.running` | gauge | 进行中 compaction 数 |

job/cluster/node 指标族视图映射：job 族 = 任一 `jobId` 标签维度聚合；node 族 = `nodeId` 标签维度（task 层全部 + operator/io 层按部署节点归并）；cluster 族 = engine 层无 job 维度指标（`nodes.active` 等）。

### 作业进度事件监听（P-REQ-2）

- 监听接口：`io.nop.stream.runtime.event.StreamJobEventListener`（经 `JobCoordinator.addJobEventListener` 注册；监听器异常被捕获记录，不影响主路径）。
- 事件类型（`StreamJobEvent.EventType`）：`JOB_STARTED` / `CHECKPOINT_COMPLETED` / `CHECKPOINT_FAILED` / `CHECKPOINT_ABORTED` / `RECOVERY_STARTED` / `RECOVERY_COMPLETED` / `JOB_FAILED` / `JOB_CANCELED` / `JOB_FINISHED` / `JOB_DEGRADED`。
- 事件负载：jobId、类型、时间戳、checkpointId（如适用）、durationMs、sizeBytes、cause（失败原因）。
- 内建 `LoggingJobEventListener`：每事件一行 `nop-stream job event:` 前缀日志（多 JVM 集群子进程日志可按此前缀检索）。
- 「进度」等价物：连续流模型下无批次边界，CHECKPOINT_* 与 RECOVERY_* 事件流即进度锚点。

### `/metrics` 暴露与 metrics 配置（P-REQ-3 / P-REQ-4）

- **运维 HTTP 端点**（`io.nop.stream.runtime.ops.StreamOpsHttpServer`，JDK 内建 HttpServer；同一实现宿主于 **coordinator 进程与 TaskManager 进程**——多 JVM 模式下每进程一个本地 pull 端点）：
  - `GET /metrics`：全部 `nop.stream.*` meter 的 Prometheus 抓取端点。默认输出 **TextFormat 0.0.4**（`text/plain; version=0.0.4`）；请求头 `Accept: application/openmetrics-text` 协商输出 **OpenMetrics** 格式（`application/openmetrics-text; version=1.0.0`，以 `# EOF` 结尾）。
  - `GET /jobs/{jobId}/checkpoints`：checkpoint 观测查询（见下节；JC 形态）。
  - `GET /jobs/{id}/vertices/{v}/subtasks/{i}/state`（keyed-state 查询，TM 形态）：**CRA 8.41 R4-01 W-09（plan 2534 裁定 D.2）起快照经 mailbox 交接由任务线程产出**（与 checkpoint 写线程同侧，消除跨线程撕裂/CME）；HTTP 线程有界等待（配置键 `nop.stream.ops.state-query-timeout-ms`，默认 5000，≤0 启动期 typed fail-fast）。任务卡死/已停应答 typed 503 `STATE_QUERY_TIMEOUT`，未知/已结束任务 404 `TASK_NOT_FOUND`，其余读取失败 500 `STATE_QUERY_FAILED`——不再以 404 毯盖全部 `StreamException`。
  - 指标族覆盖 job（`jobId` 标签维度）/ cluster（`nop.stream.engine.nodes.active` 等）/ node（`nodeId` 标签维度）三级。
- **JC 侧启用**：配置键 `nop.stream.ops.http.*`（见下）或 launch 参数 `opsHttpPort=<port>` / `opsHttpBind=<addr>`（`JobCoordinatorMain`，默认关闭；**main-scope 生产入口**——classpath 装配见「运维手册」首注）。
- **TM 侧启用（item 32，多 JVM 观察面）**：`TaskManagerMain` launch 参数 `opsHttpPort=<port>` / `opsHttpBind=<addr>`（默认 0 = 关闭，显式开启语义与 JC 一致；**main-scope 生产入口**——classpath 装配见「运维手册」首注）。task/operator/io 层指标与通道队列水位 gauge（`nop.stream.io.channel.queue.size`）可从 TM 进程直接刮取——多 JVM 背压量化不再依赖 JC 面代理。TM 进程不承载作业注册表：`/jobs` 族维持既有结构化错误语义（list/submit/stop → 503 `REGISTRY_UNAVAILABLE`/`SUBMIT_UNAVAILABLE`/`STOP_UNAVAILABLE`，detail/checkpoints → 404 `JOB_NOT_FOUND`），非统一 404 毯盖。端口须避开 JC 端口（JC 默认 8901；分布式演练 JC 用 8931，演练装置自独立基址 8941 起为 TM 分配 base+i）。
- **暴露形态裁定（item 32 D1）**：TM 指标暴露采用**每 TM 进程本地 pull 端点**，拒绝 TM→JC push transport。理由：pull 模型与 Prometheus 架构一致（每进程一个 scrape target）、零 RPC 契约面扩张（不动 `IStreamCoordinatorRpcService`）、同机分布式演练可直接刮取、与 JC 端点行为对称；push 需新增 RPC 契约 + JC 侧跨进程聚合/基数管理，收益不抵复杂度。跨机 TM 场景的联邦/远程写入属部署侧配置（Prometheus federation / remote write），非引擎职责。
- **配置键**（JC 配置键默认关闭，未启用时无任何 HTTP 监听——显式关闭语义；TM 侧同一配置语义经 launch 参数承载）：
  - `nop.stream.ops.http.enabled`（默认 `false`）
  - `nop.stream.ops.http.port`（默认 `8901`；`0` = 临时端口，测试用）
  - `nop.stream.ops.http.bind`（默认 `127.0.0.1`；跨机采集改为 `0.0.0.0` 时**必须**同时配置 token——非 loopback bind 无 token 拒绝启动，F-09b 硬约束）
  - `nop.stream.ops.http.token`（默认无 = 零认证，仅 loopback 合法；Bearer token，全端点校验，缺失/错误 → 401）
  - `nop.stream.ops.metrics.enabled`（默认 `true`；单独关闭指标暴露）
- **Prometheus 挂载语义**：端点启用时 PrometheusMeterRegistry 挂为进程组合注册表成员；挂载前累计的计数不回放（Prometheus 成员标准语义）——生产部署应在启动作业前启用端点。采集侧掉线重连不丢增量（组合注册表 base 成员始终累计）。
- **周期 sink**（`io.nop.stream.runtime.ops.StreamMetricsReporter`）：
  - `nop.stream.metrics.log.enabled`（默认 `false`）+ `nop.stream.metrics.log.interval-ms` + `nop.stream.metrics.log.target=stdout|file` + `nop.stream.metrics.log.file`（target=file 时必填；原子重写 `.tmp` → `ATOMIC_MOVE`）。
- **配置模板**：`nop-stream-runtime/src/main/resources/_vfs/nop/stream/conf/metrics.properties.template`（含 prometheus-pull / log / file 三类引擎内建 sink 注释样例 + JMX/PushGateway 外部接入模式备忘）。

### checkpoint 运维观测（P-REQ-6）

- **overview**（`GET /jobs/{jobId}/checkpoints` 响应的 `overview` 段）：完成/失败/中止计数、最新 checkpoint 大小与时长、最近时间戳、最新失败原因（`failureCause`）——数据来自 `CheckpointMetrics` 快照。
- **history**（同端点 `history` 段）：有界观测历史（新est 在前；默认上限 100 条，`CheckpointCoordinator.setCheckpointHistoryMaxEntries` 可调）。每条含 `checkpointId` / `status`（COMPLETED|FAILED|ABORTED）/ `triggerTimestamp` / `durationMs` / `sizeBytes` / `failureCause`（FAILED/ABORTED 必带）/ `recordedAt`。
- 历史由真实完成/失败/中止路径记录（与 durable checkpoint 存储保留策略解耦——观测面记录 vs 存储面保留）。
- **manifest 完整性与版本（Stage 51）**：持久化的 epoch manifest 携带 `stateFormatVersion`（与序列化信封同源的单一版本真值 `io.nop.stream.core.checkpoint.CheckpointFormatVersions`）与 `checksum`（canonical 序列化去 checksum 键后 SHA-256，`CheckpointSerDe` 咽喉写入/校验，LocalFile 与 JDBC 双存储自动覆盖）。restore 读取时：checksum 存在即校验，不匹配 → typed `ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH`（jobId/epochId/期望与实际值）；版本高于当前或双版本面不一致 → typed `ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED`。旧 manifest（无两字段）照常恢复（跳过校验，显式 legacy 容忍）。
- **checkpoint body 完整性（F-10b，plan 2026-09-04-1326-3）**：`.checkpoint` body（主恢复路径）同样携带 canonical `checksum`——同一 Stage 51 机制经 `CheckpointSerDe.CHECKPOINT_FIELD_ORDER` 扩展到 body 顶层 map。恢复侧**先验后析**（checksum-before-deserialize）：checksum 失配 → typed `ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH`，篡改/截断的 body 在任何内嵌 payload（含 `__java_bytes__`）进入反序列化前即被拒绝；旧 body（无 checksum 字段）照常恢复（legacy 容忍，与 manifest 同策略）。证明：`TestCheckpointBodyChecksum`（round-trip/头部篡改/内嵌 payload 篡改/非字符串 checksum/legacy 容忍）。
- **存储读路径失败语义（plan 366）**：`JdbcCheckpointStorage` 的表存在性目录查询**失败即响亮失败**（typed `CheckpointStorageException`）——查询成功且表不存在才视为"尚无 checkpoint"（合法首跑空恢复）。目录故障（连接丢失/权限）不会把有状态作业静默降级为空状态冷启动。证明：`TestJdbcCheckpointStorage#testRestorePathFailsLoudWhenCatalogQueryFails`。
- **原生反序列化白名单（F-10a，JEP 290）**：`JavaStreamSerializer`（`__java_bytes__` 载体，如 CEP NFA/SharedBuffer 状态）在 `readObject` 前设置 `ObjectInputFilter` 白名单——基线前缀 `io.nop.*` / `java.*` / `javax.*` / `jakarta.*`（含数组）；白名单外类 → typed `ERR_STREAM_CLASS_NOT_ALLOWED`（携带迁移提示）。**用户自定义状态类兼容口径**：第三方包前缀的 `Serializable` 状态经系统属性 `nop.stream.state.deserialize.allowed-prefixes`（逗号分隔前缀表，如 `com.mycompany.stream.state.`）声明放行，无需改代码；不声明即拒绝（显式 breaking 语义，见 migration-guide）。证明：`TestStreamDeserializationFilter`（基线 round-trip/白名单外拒绝/逃生口放行）。

### REST 运维 API（P-REQ-5）

生命周期端点宿主于同一运维 HTTP 端点（`StreamOpsHttpServer`；单作业 launch 模式经 `JobCoordinatorMain` 的 `opsHttpPort` 启用——**main-scope 生产入口**，classpath 装配见「运维手册」首注；多作业模式由 main-scope `OpsJobManager` 提供 submit/stop）。错误返回结构化 body（`{"error": <CODE>, "message": ..., "status": <http>}`），未知作业 404 / 非法参数 400 / 重复提交 409——无静默空响应。

**最小认证（F-09b，plan 2026-09-04-1326-3）**：配置了 `nop.stream.ops.http.token` 时全端点（`/metrics`、`/jobs` 族、catch-all）校验 `Authorization: Bearer <token>`——缺失/错误凭据 → 结构化 401 `UNAUTHORIZED`，无静默放行路径。**bind 非 loopback 时 token 为硬性要求**：`nop.stream.ops.http.bind` 非 `127.0.0.1`/`localhost`/`::1` 且未配 token → server 拒绝启动（fail-fast，跨机暴露不允许无认证）。loopback 默认保持零认证（back-compat）。

| 方法+路径 | 语义 | 成功响应 |
|---|---|---|
| `GET /jobs` | 运行中作业列表（本进程受理的全部作业 + 状态） | `{jobs: [{jobId, jobStatus, running, restartCount, fencingEpoch}]}` |
| `POST /jobs` | 提交作业（body = `JobSubmissionSpec` JSON：`{jobId, pipelineFactoryClass, params?}`）。工厂引用语义：coordinator 进程受理，经与 launch 完全一致的路径构建并启动（工厂失败 fail-fast，无 trivial 管线回落） | 201 + 作业摘要 |
| `POST /jobs/{jobId}/stop?mode=CANCEL\|DRAIN\|SUSPEND` | 停止作业（四态终止模式的 CANCEL/DRAIN/SUSPEND 入口——SUSPEND 自 WI9/plan 2333 起放行，terminal savepoint 后作业 FINISHED；EXPORT_SAVEPOINT 仍走 coordinator RPC，REST 传参即 400；`RECOVERING` 窗口内返回 409 `JOB_STATE_CONFLICT`，恢复完成后重试） | 200 + 作业摘要 |
| `GET /jobs/{jobId}` | 作业详情：逻辑健康状态（`health`，见下节）、jobStatus、failureCause、restartCount、checkpointOverview（计数/最新时长） | 200 + 详情 JSON |
| `GET /jobs/{jobId}/checkpoints` | checkpoint 观测（见上节） | 200 + overview/history |
| `GET /jobs/{jobId}/threaddump` | 线程诊断：coordinator 进程全线程栈文本 | 200 `text/plain` |

提交语义细节：重复 jobId → 409；`pipelineFactoryClass` 缺失/不存在/不实现 `ClusterPipelineFactory` → 400（显式错误，无静默回落）；body 非 JSON → 400。**类加载加固（F-09a，plan 2026-09-04-1326-3）**：工厂类名先行窄化守卫（JDK/internal 包前缀 `java.`/`javax.`/`jakarta.`/`jdk.`/`sun.`/`com.sun.` 与数组描述符 → 400 typed 拒绝），类加载改为 `Class.forName(name, false, loader)` 先接口检查后初始化——任意 FQCN 不再触发静态初始化器。**第三方工厂前缀保留**（裁定方案 i）：`pipelineFactoryClass` 是文档化的用户可扩展契约，未套用平台 `ClassNameValidator` 白名单——`com.mycompany.*` 等第三方工厂不受影响（接口检查兜底）。

### 状态重置工具（P-REQ-10）

- 工具类：`io.nop.stream.runtime.maintain.StreamStateResetTool.reset(jobId, checkpointBaseDir, sourceReplayable, clusterRegistry?)`——清理 `LocalFileCheckpointStorage` 布局 `<base>/<jobId>/`（durable checkpoint + epoch manifest + 其携带的 source cursor——位点随 durable 状态一并重置），同 jobId 重新拉起即全新重跑（可重放 source 从起点重读）。
- **拒绝语义（无静默清空）**：`sourceReplayable=false` → 显式报错（不可重放 source 重置即丢数据）；注册表存在活跃 coordinator → 显式报错（先 stop）；状态目录不存在 → 显式报错（防拼错路径静默成功）。
- **jobId 路径校验（F-08，plan 2026-09-04-1326-3）**：reset 是破坏性递归删除，jobId 在拼接基目录前过 storage 侧同款两段校验——① 字符集守卫（`[a-zA-Z0-9_-]+`，合法 storage jobId 构造上即匹配）不满足 → typed 拒绝（`../other-job`、编码变体、绝对路径等 typo 不可能静默删除兄弟作业状态）；② canonical 包含校验（resolved 路径必须在 checkpointBaseDir 之内，纵深防御）。合法 jobId 零损失（全部通过）。证明：`TestStreamStateResetTool.refusesDotDotJobIdAndPreservesSiblingState` / `refusesEncodedAndAbsolutePathJobIds`。
- 入口收敛（`StreamMaintenanceMain`，与离线 reshard 同一工具族，共享「先校验、后动作、结果报告」语义）：
  ```bash
  # reset-state：重置作业本地状态
  <java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reset-state \
      jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true
  # reshard：离线 max-parallelism 迁移（MaxParallelismReshardMigration）
  <java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reshard \
      oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>
  ```

### 提交前校验（conf-validate / dry-run，P-REQ-13/14）

入口收敛在 `StreamMaintenanceMain` 维护工具族（校验核心逻辑在 `nop-stream-flow` validate 包，入口仅做参数解析与调用）：

```bash
# conf-validate：不启动作业完成层 1+2 校验；--connect 追加层 3 连通性探测
<java> io.nop.stream.runtime.maintain.StreamMaintenanceMain conf-validate \
    file=<VFS 或本地路径> [--connect]
# dry-run：等价 conf-validate --connect（P-REQ-13 提交前连通性验证）
<java> io.nop.stream.runtime.maintain.StreamMaintenanceMain dry-run file=<path>
```

- **bean 来源（XDSL 模式）**：CLI 无额外参数时回落全局容器（`GlobalBeanFunctionResolver`）；嵌入/测试形态经 `StreamConfValidateCommand.run(args, resolver)` 传入程序化 resolver（`InMemoryBeanFunctionResolver`，S1/S2 场景形态）或显式装配容器包装（`BeanContainerFunctionResolver.of(IBeanContainer)`，与连接器注册中心装配同款模式）。
- **连接器模式（库级 API）**：`StreamConfValidator.validateConnector(direction, typeName, params, catalog, connect)`——经 item 19 SPI 注册中心按类型名解析，参数按能力描述符字段级校验（缺失必填/未知参数逐条含参数名），catalog 由宿主显式装配传入。
- **分层语义**：层 1 = XDSL 按 `stream.xdef` 字段级校验（未知元素/属性、类型错误、必填缺失）；层 2 = 完整图构建不 execute（bean 解析、类型匹配、FL-1 拒绝面、xpl 编译全部触达）/ 连接器模式 = 描述符参数校验 + 工厂构造探测；层 3 = 逐 source/sink 端点连通性探测（能力接口 → FLIP-27 → 2PC 基契约 → 显式 SKIP 分派）。
- **exit code 契约**：`0` = 通过（允许含显式 SKIP 项）；`1` = 校验失败（任一 FAIL 条目）；`2` = 用法错误（参数缺失/文件不存在）。
- **错误输出**：逐条结构化 `[FAIL|SKIP][layer N] <元素/端点> option '<选项名>': <错误码> — <消息> [@ <file>:<line>]`——选项名（属性/bean/参数名）必含；`file:line` 位置锚点在层 1/层 2 错误携带失败声明位置时渲染（无位置来源不伪造，item 29）。错误码族：`nop.err.stream.bean-not-found` / `connector-param-unknown` / `connector-param-required` / `connectivity-check-failed` / `connectivity-not-supported` 等。
- **探测副作用红线**：dry-run 不产生作业正常运行本身不会创建的对象。豁免的预期幂等对象：JDBC 2PC 台账**表**（幂等 DDL）、file sink 输出目录；绝不产生的残留：台账**行**、epoch 终文件、订阅位点、offset 写入（focused 测试逐族断言）。

**逐族探测能力表**（探测语义与副作用红线的权威表——设计与理由记录见平台内部设计文档）：

| 连接器族 | 探测语义 | 残留红线 |
|---|---|---|
| jdbc-2pc sink | `beginTransaction()+initializeLedgerTable()+rollback()`（方言/querySpace + 物理连接） | 台账表豁免；台账行绝不产生 |
| file 2PC sink | begin+rollback（构造期已建输出目录） | 无文件写入（目录豁免） |
| file source（FLIP-27） | `createEnumerator()+start()`（no-op 投递上下文）目录可达性 | 只读扫描，不分配 split |
| batch-loader source | `loaderProvider.setup()` + 关闭 loader | 不消费批数据 |
| batch-consumer sink | 构造级已验证（构造即 `consumerProvider.setup()`），探测断言构造结果 | setup 语义由构造路径承担 |
| debezium-cdc source | 参数级（connector name typed 校验）+ 凭据引用解密可达；**不拉引擎、不连库** | 无 offset 写入、无订阅 |
| message source/sink | **显式 SKIP**（`IMessageService` 无平台 health-check API；可达性如实呈现为不可判定） | —（不探测） |
| 其余/第三方 | 未实现探测契约 = 显式 SKIP 报告项（`connectivity-not-supported`），非静默通过 | — |


**数据采样（WI4，2026-10-07）**：`sample type=<typeName> param=value... [limit=<n>]`（connector 校验模式）——registry 解析 SOURCE 工厂 → 参数规则校验（required/未知/INT/allowedValues/pattern，sensitive 值脱敏，EXPERIMENTAL 端点非阻断 warning）→ 只读采样。仅接受 SOURCE 类型（SINK 类型名 = typed direction-mismatch FAIL，exit 1）；SOURCE 族中实现 `SampleableSource` 的端点（当前内建 = `file`）返回真实记录，未实现者（message/debezium-cdc/batch-loader）显式 SKIP（`nop.err.stream.sample-not-supported`）。副作用红线与 dry-run 探测同族（只读，不分配 split/不订阅/不拉引擎/不消费批数据）。classpath 须含 nop-ioc（反射装配注册容器；缺失时 typed 报错）与目标连接器模块 jar。exit 契约：0 = 采样成功或显式 SKIP，1 = 校验失败，2 = 用法错误。

**模型感知 lint（xdsl roadmap WI3，2026-10-09）**：`lint file=<path> [--connect]`——最佳实践预检层（区别于 conf-validate 的提交硬校验）：无消费方声明**一次报齐**（WARNING，`nop.err.stream.*` 同源码；builder fail-fast 仍是最终权威并以 FAIL 折叠进同一报告）+ R4 类型声明/R5 水位供给/R2 声明式优先退化提示/R3 custom 状态归属提示；R6（幂等组合预检，待 WI15 端点能力面）/R13（场景注册表比对，待 WI21）/R14（维表接线形态，待 WI15 params 面）以显式 SKIP 注记表达休眠检查面（非静默）。`--connect` 透传连通探测（对齐 conf-validate 层 3）。实现：`StreamModelLinter`（flow/validate，复用 DslModelParser 解析链与 `ValidationIssue`/报告基建）+ `StreamLintCommand`（runtime/maintain，参数形态与 bean 来源三形态同 conf-validate）。exit 契约：0 = 无 FAIL（WARNING/SKIP 允许），1 = 有 FAIL（含 xdef 解析失败），2 = 用法错误。规则面清单的单一事实源 = `nop-stream-user-guide.md` 声明面支持矩阵（WI1 表 B）。

### 凭据引用与明文边界（P-REQ-14）

- **引用语法**：`credential:{credentialId}#{field}`（如 `credential:mysql-prod#password`）——置于连接器配置字段值处（CDC 族必达点 = `DebeziumConfig.databaseUser/databasePassword`；SPI 工厂程序化参数 `credentialProvider`（OBJECT，optional）注入平台 provider）。语法解析与解密：`io.nop.stream.core.credentials.StreamCredentialSupport`，经 nop-credential 唯一解密点 `ICredentialProvider`（kms-vault 等后端经接口透明）。
- **密文驻留与解密时点**：引用串持久驻留 Serializable 配置（序列化/checkpoint 恢复路径携带引用而非明文，跨 JVM 恢复后可再解密）；解密只发生在引擎侧瞬态路径（CDC 连接器构造引擎前的瞬态解密副本），原配置对象与序列化路径永不含明文。provider 为 transient 字段——每 JVM 装配注入（构造参数或 `setCredentialProvider`）。
- **fail-closed**：配置含引用而 provider 缺失（`credential-provider-missing`）、凭据不存在/已软删（`credential-unresolved`）、引用格式错（`credential-ref-invalid`）= 显式 typed 错误，绝不静默空串。明文不进日志/报告/异常消息。
- **凭据可达性结论**（dry-run）：解密成功即「凭据可达」；不依赖 `testCredential()`（平台 W2 桩恒返回 success=false，依赖它会产生系统性假阴性）。
- **encrypt 等价物**：凭据经 nop-credential 平台面（CRUD 管理）写入，落库即 `cv1:` 密文（平台内唯一解密点）；不提供作业配置整文件加密（破坏 XDef 校验/Delta/diff，裁定拒绝 SeaTunnel EncryptConfigServlet 形态）。

### 历史与日志生命周期治理（P-REQ-11）

治理配置（`StreamGovernanceConfig`，coordinator 侧周期扫描裁剪观测历史 + 终态作业记录；durable checkpoint 存储保留沿用 `CheckpointConfig.maxRetainedCheckpoints`，与观测面解耦）：

| 配置键 | 默认值 | 语义 |
|---|---|---|
| `nop.stream.ops.checkpoint-history.max-entries` | `100` | checkpoint 观测历史最大条数 |
| `nop.stream.ops.checkpoint-history.retention-minutes` | `1440` | 观测历史保留时长（与条数双约束） |
| `nop.stream.ops.job-record.retention-minutes` | `1440` | 终态作业记录保留时长 |
| `nop.stream.ops.governance.cleanup-interval-ms` | `300000` | 治理扫描周期（5 分钟） |

### 账本保留策略（R01 成员，plan 2510 裁定⑦⑧）

nop-stream 两条 DB 账本由 `LedgerRetentionConfig`（`io.nop.stream.runtime.ledger`）承载的形态 A 周期清理（参数语义 SSOT = 仓库 ai-dev/design/concurrency/ledger-retention-policy.md §3.1/§3.4 成员传导行，平台内部文档——按 docs-for-ai 边界规则不直接链接；缺省值/键名/校验与 SSOT 零分叉，参数修订只走该文档）：

| face | 账本 | 清理语义 | 配置键 |
|---|---|---|---|
| `assignment` | `nop_stream_task_assignment`（分配账本） | 终态 job 定界清理（终态集 CANCEL/DRAIN/SUSPEND 进程内白名单主谓词 + `assigned_at` 账龄 TTL 兜底，均排除活跃 job；零 DDL） | `nop.stream.ledger.assignment.retention-days`（30，0=禁用）/ `.cleanup-interval`（1h，下限 60s）/ `.delete-batch-size`（1000） |
| `msgqueue` | `nop_stream_msg_queue` | **消费后可删**：主谓词 = `id ≤ 进程内 per-topic 已派发最大 id`（订阅启动 MAX(id) bootstrap 不作清理位点），与 retention TTL **双谓词取交**——TTL 不独立启用删除；进程重启后派发位点归零重建，重建到历史水位前清理**保守暂停**（滞后清理不误删未消费行） | `nop.stream.ledger.msgqueue.retention-days`（30，0=禁用）/ `.cleanup-interval`（1h，下限 60s）/ `.delete-batch-size`（1000） |

非法配置（负 retention / interval < 60s / batch-size ≤ 0）装配期 fail-fast。未消费积压的增长控制不归 retention（retention 不删未派发行），归「DB 数据面生产者背压」门控（见下文）。

### 逻辑健康状态机（P-REQ-7）

`io.nop.stream.runtime.health.StreamJobHealth` 七态（KS 七态参照按 nop-stream 连续流 + 全局恢复语义裁剪），由 `JobCoordinator` 真实生命周期事件驱动（start / globalRecovery / failJob / terminate / durable checkpoint 完成回调），非定时推断：

| 状态 | 语义（KS 参照） | 进入事件 |
|---|---|---|
| `CREATED` | 已构造未启动 | 初始态 |
| `RUNNING` | 正常服务 | `start()`；`DEGRADED` 经下一 durable checkpoint 完成回愈 |
| `RECOVERING` | 拓扑重建中（≈ REBALANCING） | `globalRecovery()` 开始 |
| `DEGRADED` | 仍在服务但存在未收敛故障痕迹（≈ PENDING_ERROR，重启计数 > 0） | 恢复完成 |
| `FAILED` | 终态：failJob（含恢复 cap 耗尽） | `failJob()` |
| `CANCELED` | 终态：terminate(CANCEL) | `terminate(CANCEL)` |
| `FINISHED` | 终态：terminate(DRAIN/SUSPEND) 完成 | `terminate(DRAIN/SUSPEND)` |

- **迁移合法性表**（唯一合法迁移；非法迁移 fail-fast 抛 `IllegalStateException`，非静默忽略）：`CREATED→RUNNING`；`RUNNING→{RECOVERING, FAILED, CANCELED, FINISHED}`；`RECOVERING→{DEGRADED, FAILED}`；`DEGRADED→{RUNNING, RECOVERING, CANCELED, FINISHED}`；终态无出边。
- **监听器**：`JobCoordinator.addHealthListener(JobHealthListener)`（每次合法迁移回调；监听器异常被捕获记录，不影响主路径）。进入 `DEGRADED` 同时经事件总线派发 `JOB_DEGRADED` 事件（告警路由输入）。
- **观测面**：每次迁移输出日志 `job health transition: job=<id> <from> -> <to> (cause=...)`（多 JVM 进程日志可按此前缀检索）；REST `GET /jobs/{jobId}` 响应含 `health` 字段。
- **stop 与恢复窗口**：`RECOVERING` 窗口内 stop 无合法迁移（毫秒级窗口），REST `POST /jobs/{jobId}/stop` 返回 `409 JOB_STATE_CONFLICT`（显式冲突，恢复完成后重试即可）；`failJob` 对已终态作业为可观察 no-op（WARN 日志）。

### 告警与事件外发（P-REQ-12）

- `IAlertChannel` 抽象 + `AlertService`（作业事件监听器）：按路由表把故障语义事件外发到全部配置渠道——`JOB_FAILED`→ERROR、`RECOVERY_STARTED`→WARN、`JOB_DEGRADED`→WARN（进度类事件不路由）。渠道异常按渠道记录 WARN，不影响作业控制路径。
- 内建渠道：`LoggingAlertChannel`（结构化日志，`nop-stream alert:` 前缀，默认启用）；`WebhookAlertChannel`（HTTP POST JSON，JDK HttpClient；异步有界队列投递——慢/不可达 webhook 不阻塞控制路径，队列满丢弃并 WARN）。
- 接线：`OpsJobManager`（多作业运维面，submit 时注册到每个作业）；`JobCoordinatorMain`（launch 路径，`alertWebhookUrl` 参数启用 webhook 渠道）。
- 配置键（`AlertService.fromProperties`）：

| 配置键 | 默认值 | 语义 |
|---|---|---|
| `nop.stream.alert.logging.enabled` | `true` | 日志渠道开关 |
| `nop.stream.alert.webhook.enabled` | `false` | webhook 渠道开关 |
| `nop.stream.alert.webhook.url` | —（启用时必填，缺失 fail-fast） | webhook 接收端点（http/https） |
| `nop.stream.alert.webhook.timeout-ms` | `5000` | 单次投递超时 |
| `nop.stream.alert.webhook.retries` | `2` | 失败重试次数 |
| `nop.stream.alert.webhook.backoff-ms` | `200` | 重试退避间隔（2026-09-12 审计 ST-4：原固定 200ms 改可配，默认保持） |

> **自建调度线程裁定（审计 ST-6，2026-09-13）**：治理清扫（OpsJobManager sweeper）、指标周期上报（StreamMetricsReporter）、webhook 投递线程为引擎自建——流引擎是自包含运行时（可独立于 nop-job 部署），周期任务随引擎生命周期；宿主集成 nop-job 时可外置触发（successor 候选）。barrier/心跳/checkpoint 超时等引擎底座计时属引擎内部边界。

## 并发契约（plan 2510 R2-07，裁定①②⑤⑩使用面投影）

> 唯一设计裁决权威 = 仓库 ai-dev/design/concurrency/stream-core-concurrency-and-resource-envelope.md（平台内部文档——按 docs-for-ai 边界规则不直接链接）。本节只记录用户/运维可见契约。

- **enumerator 锁纪律（裁定①）**：`LocalSourceCoordinator` 的 `addReader` / `handleSplitRequest` / `snapshotState` / `close` 全部在 `enumeratorLock` 内互斥——并行 source 启动不再出现双份 split 分配或快照撕裂；`enumeratorLock` 为叶锁（锁内禁 I/O / 用户回调 / 再入其他监视器）。
- **LOCAL 池双守卫（裁定②）**：提交期 Σsubtask > poolSize → typed fail-fast（错误消息带 Σsubtask/poolSize 与处置指引：下调并行度或上调 poolSize；合法的有界小数据作业若在队列不填满即跑完也会被拒——这是显式取舍，互等死锁的危害无界且不可诊断）；运行期 `runLocal` 经有界等待接入（覆盖全部 LOCAL execute 入口），超时 = abort（cancel 全部 task future + 关停执行器 + typed 异常，不悬挂不残留）。配置键 `nop.stream.local.await-completion-timeout-ms`（缺省 300000；`0` = 显式禁用运行期守卫，仅留守卫 a；负值装配期 fail-fast）。
- **standby 降级第四层防线（裁定⑤）**：coordinator 降级 standby 时主动 abort 在途 pending checkpoint，且 persist 完成/commit 扇出入口复核 active/fencing 状态——降级后才完成的在途 checkpoint 被丢弃不 commit + 失败计数；僵尸 standby 不再成为 checkpoint 持久平面的第二写者。既有三层防线（collectAck 门控/pending 超时/新 leader abort）零改写。
- **materialization store 溢出语义（裁定⑩）**：pool+materialization 象限 queue 满时 producer **阻塞背压**（无静默 bypass、无许可丢失）；store 仅承担 checkpoint 恢复记录语义。store 上界 = `nop.stream.materialization.store-max-entries`：**未配置或 ≤0 = 不设上界**（恢复正确性缺省——store 是在途未提交记录的恢复镜像；跨 1024/4096/16384… 档位输出一次 WARN + size 指标）；**显式配置 >0 = fail-loud 上界**（超界抛 `ERR_STREAM_MATERIALIZE_STORE_OVERFLOW`，运维强约束可选项）。
- **②⑩交叠运维声明**：经 `nop.stream.local.await-completion-timeout-ms=0` 显式禁用运行期守卫时，materialization 象限回归阻塞 put——消费者停摆引发的时序型互等死锁风险由运维自担（禁用守卫即显式接受该面；提交期守卫恒在不受影响）。

- **LOCAL 中断取消语义（CRA 8.41 R4-01 W-11，plan 2534 裁定 D.3）**：LOCAL teardown（finally）在 graceful 关停前先做**协作式取消遍历**（mailbox signalCancel + cancel 中断 → 有界终态等待，预算配置键 `nop.stream.local.cancel-await-timeout-ms`，默认 10000，`0`=禁用等待仅发信号，负值装配期 fail-fast），随后 `shutdownNow` 兜底——阻塞在用户代码中的任务（如 50ms 轮询消费者）从「中断后无限自旋持连接」变为到达 CANCELED 终态退出；正常完成路径取消遍历为幂等 no-op（零行为变化）。
- **停机 2PC 通知语义（W-12，裁定 C.3）**：TaskManager/CheckpointCoordinator 的 commit 通知执行器在 **shutdown 窗口内改为 always-inline**（调用方线程执行，CC-17 先例推广）——stop 竞速窗口不再静默丢弃 finishCommit 通知；饱和窗口语义不变（TM CallerRuns 维持 R5-CC-10 契约、CC 溢出补投），`nop.stream.task.commitCallerRuns.total` 仅在真实 inline 时递增。
- **周期失败日志治理（W-13，裁定 E.1）**：九周期失败日志点+两补遗（poll/consumer/heartbeat/stale-heartbeat/retry-commit/persist-failed/barrier-inject/retention-cleanup/leader-elector + msgqueue cleanup/metrics-reporter/barrier-fanout-drop）统一**三态抑制**（首条全量 → 30s 窗口聚合摘要 → 恢复 cleared），故障期日志量从 50ms 节奏的 ~140 万条/小时降为首条+每窗口一条摘要；计数与兜底语义（poll 重试/abort 兜底等）零变化——只限频不吞异常。

## 编排薄层并发契约（plan 2512 R2-09，裁定①②③④使用面投影）

> 唯一设计裁决权威 = 仓库 ai-dev/design/concurrency/stream-orchestration-thin-concurrency.md（平台内部文档——按 docs-for-ai 边界规则不直接链接）。本节只记录用户/运维可见契约。

- **SQL 标量 UDF 注册表（裁定①）**：UDF 注册表为 `ConcurrentHashMap`——`register` 任意线程任意时刻可调，注册新名即时生效，覆写同名即时影响后续逐记录查表（CHM 免疫并发读撕裂/CME）；default resolver 为进程级单例语义，运行期换绑（重复 `registerAsDefault`/`setDefault` 非空换绑）**放行 + WARN 留痕**（日志键 `nop.stream.sql.udf.default-rebound`，含新旧 resolver 标识），换绑后已编译作业无需重编译即对后续记录生效，一致性由应用自担。双路径解析表述以 `nop-stream-sql.md` §3a 为准（≤3 行摘要：Java 内嵌路径编译期一次解析；声明面逐记录查表，换绑即时生效）。
- **UDF RuntimeContext per-subtask（裁定③）**：`ProcessOperator` 对 RichFunction 形态 UDF 在 `copyForSubtask` 做序列化 round-trip 私有化（core util `SerializationRoundTripCopy`）——每 subtask 私有 UDF 实例与 RuntimeContext/keyed backend，parallelism ≥2 不再收敛单一 backend（有状态 keyed UDF 跨键零污染）；**非 Serializable 的 RichFunction UDF 部署期 typed 失败**（指引实现 Serializable，无静默共享回退——与跨进程部署序列化约束同源，属声明过的行为变更面）；非 RichFunction UDF 维持引用透传（无 context 写入面，零回归）。
- **REPLAYABLE 目录文件源跨 run 复用（裁定②④）**：`DirectoryFileSourceFunction` 为共享实例契约（runtime 跨 subtask/跨 region 重启复用同一实例）——run 入口 CAS 成功后复位 `cancelled`（上一 run 的取消位不再静默饿死重启 run 的零事件假成功；并行双入落败者仍早退）；位点恢复为 per-file **max 增量合并**（不 REPLACE 清零），checkpoint 位点不回退、恢复重放不丢不重。详细语义归设计文档，REPLAYABLE 声明以此两半边（复位完整 + 位点单调）为成立前提。

## 运维手册（分布式模式操作）

> 本节是 nop-stream 运维操作的权威速查（与 live 行为一致性由 gated 测试矩阵背书：REST 生命周期/指标暴露/重置重放/健康告警 e2e）。
>
> **入口类与 classpath 装配（WI1 后现状，plan 2328）**：`JobCoordinatorMain` / `TaskManagerMain` 及其支撑类（`SharedJdbcInfrastructure`/`PollingJdbcMessageService`）自 2026-10 起**在 `nop-stream-runtime` main sources**（`io.nop.stream.runtime.launch` 包）——发布 runtime jar 直接含入口，`java -cp <classpath> io.nop.stream.runtime.launch.JobCoordinatorMain ...` 即可启动。classpath 装配两种方式：① 启动脚本 `nop-stream/nop-stream-runtime/src/main/scripts/nop-stream-cluster.sh`（`coordinator|taskmanager|classpath` 子命令；`dependency:build-classpath -DincludeScope=runtime` 装配发布依赖面并缓存，`NOP_STREAM_CLASSPATH` 可整体覆盖，`NOP_STREAM_EXTRA_CLASSPATH` 追加 JDBC 驱动 jar——发布依赖面不含驱动，生产数据库驱动须自带，`jdbcDriver=<驱动类名>` 指定驱动）；② 手工 `mvn -pl nop-stream/nop-stream-runtime dependency:build-classpath -DincludeScope=runtime` 后拼 runtime jar。`StreamMaintenanceMain`（reset-state/reshard/conf-validate）与 `OpsJobManager`+REST 多作业模式同属 main-scope 入口。SIGTERM 优雅关闭经 shutdown hook（外部信号退出码 143 = JVM 信号默认处置；程序内 shutdown 完成退出 0）。与 `nop-stream-user-guide.md`「入口类现状」一致。

### 启动作业

1. 预置共享存储（H2 AUTO_SERVER 库或等价 JDBC；JDBC 2PC sink 需预建数据表 + ledger 表）。
2. 先启 TM 后启 JC（`<java>` = `java -cp <classpath>`，classpath 装配见本节首注；或直接用启动脚本 `nop-stream-cluster.sh taskmanager|coordinator`）：`TaskManagerMain nodeId=<id> jdbcUrl=<url> topicNamespace=<ns> [opsHttpPort=<port> opsHttpBind=<addr>]`；`JobCoordinatorMain jobId=<id> jdbcUrl=<url> topicNamespace=<ns> checkpointBaseDir=<dir> expectedNodeIds=tm-0,tm-1 [pipelineFactoryClass=<fqcn>] [opsHttpPort=<port>] [alertWebhookUrl=<url>]`。TM 侧 `opsHttpPort` 启用进程本地指标端点（item 32；须避开 JC 端口）。
3. 恢复语义：JC 启动时自动恢复最新 durable checkpoint 并推进 id counter（防 shadow-window）；`pipelineFactoryClass` 构建失败 fail-fast 不回落 trivial 管线。**存储身份与默认目录（AR-1）**：存储 jobId = 消毒后的作业名（`StorageJobIds.sanitizeJobId`，非 `[a-zA-Z0-9_-]` 字符替换 + 稳定 hash 后缀）；默认基目录 `${java.io.tmpdir}/nop-stream-checkpoints`（embedded/RPC/本地同约定，系统属性 `nop-stream.checkpoint.storage.dir` 可覆写）——**默认目录禁用自动恢复**（防跨作业污染），kill/restart 恢复必须显式传 `checkpointBaseDir`/`storageProperty("path")`。裁定与机制详见 平台内部 checkpoint 设计文档（ai-dev/design/nop-stream/ 目录，按 docs-for-ai 边界规则不直接链接） §8.1.4（ai-dev/design/nop-stream/ 目录，平台内部文档，按 docs-for-ai 边界规则不直接链接）。
4. 多作业模式（可选）：coordinator 进程内 `OpsJobManager` + REST `POST /jobs` 提交（工厂引用语义，见 REST 契约）。

### 停止作业

- REST：`POST /jobs/{jobId}/stop?mode=CANCEL`（立即取消）或 `mode=DRAIN`（终态 checkpoint 后停止）。`RECOVERING` 窗口内返回 409，恢复完成后重试。
- coordinator RPC 直连形态：`terminate(SUSPEND)`（savepoint 后挂起，可恢复）/ `terminate(EXPORT_SAVEPOINT)`（导出 savepoint，作业继续）。
- 终态：`CANCELED` / `FINISHED`（健康状态机终态，无出边）。

### 状态重置（全新重跑）

`StreamMaintenanceMain reset-state jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true`（前置校验与拒绝语义见「状态重置工具」节；e2e：重置后同 jobId 从起点完整重放）。

### 恢复操作（savepoint 等价）

- **自动**：TM 失败 → FAILED 报告/租约到期 → global recovery（fencing epoch 轮转 + 重新 assignment + TM 侧从最近 durable checkpoint 恢复）；健康状态 `RUNNING → RECOVERING → DEGRADED`，下一 durable checkpoint 完成回愈 `RUNNING`。
- **跨集群恢复（stop-the-world 重启/rescale）**：停集群 → 同 jobId + 同 checkpoint 目录重拉（TM 数可变，新 generation 用更大 `fencingEpoch`）。
- **SUSPEND 恢复**：`terminate(SUSPEND)` 产生终态 savepoint；以同 jobId + 同 checkpointBaseDir 重启即从 savepoint 恢复（等价 savepoint-恢复入口）。
- **离线 reshard**：`StreamMaintenanceMain reshard oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>`。

### DB 数据面生产者背压（WI6，2026-10-07）

- **启用**：launch 参数 `-Dnop.stream.data.db.backlog.limit=<n>`（job 级；0/缺省 = 关闭，零侵入）。仅对 DB（SysDao）数据面通道生效——`DataPlaneMessageServiceAdapter` 解包后探测裸后端的 `ChannelBacklogProbe` 能力：`SysDaoMessageService`（按数据 topic 的 WAITING+CLAIMED 行数）与 **launch DB 通道 `PollingJdbcMessageService`（plan 2510 裁定⑧(b)——backlog 口径 = 未消费行数 `COUNT(*) WHERE topic=? AND id > 派发 cursor`）** 均已实现该能力，`-Dnop.stream.data.db.backlog.limit` 对两条 DB 通道真实可达；Kafka/Pulsar/内存通道无该能力，配了 limit 会在启动日志声明一次后禁用（诚实 no-op，非静默）。
- **语义**：生产者发送前检查该 topic 未消费积压深度，达到上限则**锁外有界等待**（100ms 分片，可中断；不持 sendLock——close/EOS 不被阻塞放大）；等待 10s（D2 同款缺省）仍超限 = typed 失败 `nop.err.stream.send-gate-timeout` → task 失败重启（持续过载的显式语义，非静默丢弃）。barrier/watermark 与数据同门（in-band，对齐屏障语义下排队属预期背压）。
- **积压记账**：生产者侧本地计数器随每次发送自增（突发精确受限），DB COUNT 仅用于观察消费进度（TTL 200ms 缓存 + 达限时逐片强刷）。
- **观测**：`nop.stream.io.db.backlog` gauge（topic 标签；门控启用时注册；区别于 `nop.stream.io.channel.queue.size` 的消费侧本地队列口径）。
- **注意**：启用门控时应先起消费者或接受部署窗口内下游未订阅导致的门控失败重试（nop_sys 裁定：无订阅者 topic 的事件永不被扫描，WAITING 行全数计入积压）。
- 测试锚点：`TestRemoteResultPartitionBackpressureGate`（fake probe 五路径 + 锁外契约，共 6 用例）、`TestDataPlaneSysDaoBackendE2E.gatedProducerBoundsTableDepthWhileConsumerLags`（真后端端到端：深度受控 + 消费收敛 + 精确一次）。

### 引擎级变更元数据（WI7/plan 2336）

- **载体与缺省语义**：`StreamRecord` 携带可选 `rowKind`（`io.nop.api.core.stream.RowKind`：INSERT/UPDATE_BEFORE/UPDATE_AFTER/DELETE）与 `tableId`；**null rowKind = INSERT-only append（历史缺省，全链路零行为变化）**。copy/copyTo 携带元数据；`replace()` 原地变异保留元数据（与 timestamp 同侧带契约——清除方向才是静默降级；当前无任何算子在复用型记录上设置元数据）。equals/hashCode 不纳入元数据。
- **桥接面（源发射点）**：value 实现 `io.nop.api.core.stream.ChangeSemanticView`（api-core 共享内核接口）即被自动盖到构建的记录上——`StreamSourceOperator`（collect/collectWithTimestamp）与 `SourceReaderOperator`（Source API 路径）两个桥接点。`ChangeEvent`（nop-message-debezium）已实现该视图：c/r→INSERT、u→UPDATE_AFTER、d→DELETE、null/未知→INSERT（防御；原 operation 仍随 value 供 sink 自行拒绝）、tableId=metadata 表名。ChangeEvent wire JSON 新增两可选属性（后向兼容）。
- **守卫面（sink 入口）**：`StreamSinkOperator.processElement` 对带非 INSERT rowKind 的记录检查 `CdcChangeAwareSink` 标记——未实现 = typed fail-fast（`nop.err.stream.sink-not-change-aware`，含 sink 类名与 rowKind），不静默降级为 append。jdbc-2pc 全模式实现该标记（keyed 模式从 value 推导操作；INSERT 模式对 ChangeEvent u/d 自有 typed 拒绝）；file/message/batch-consumer/kafka-2pc 不实现（透传语义 sink——对带非 INSERT 元数据的记录 fail-fast = R4 预期行为；kafka 透传 CDC 流为 WI7 起的新增失败面，如实声明）。
- **存活边界（如实声明）**：元数据存活 = 源桥接点 + 前向传递（copy/原记录前向）+ 跨进程 envelope 与 checkpoint 在途恢复（`StreamMessageEnvelope.rowKind/tableId` 可选字段，null 缺省后向兼容）；**新建记录型算子（map/flatMap/reduce/join/window/cep）丢弃元数据**（逐算子桥接归后续）；side-output sink 经 SideOutputRegistry 直连，不在守卫面。

- **SideOutputRegistry 作用域（CRA 8.42 R4-02 W-07/FAM-2）**：注册表从进程级 static 改为 **per-execution 实例**（挂在 `StreamExecutionEnvironment` 上，容器寿命=执行上下文寿命；env executed-once 守卫使「同 env 重执行」结构不可能）——跨作业 tag 顶替与旧 consumer 复活缺陷闭合。**嵌入模式（embedded distributed）显式收紧**：`getSideOutput(tag).sink(...)` 闭包注册在分进程远程路径语义不变；同 JVM embedded 形态下注册作用域从 JVM-local 收紧为 execution-context-local（同宿主重新提交同 tag 的旧 consumer 不再复活）——依赖旧 JVM 级共享的部署形态需迁移。
- **submit 请求体上界（W-01）**：运维 HTTP `POST /jobs` 请求体默认 1MiB 上界（配置键 `nop.stream.ops.http.max-body-bytes`，≤0 装配期 fail-fast）——超限零读取即答 typed 413 `SUBMIT_BODY_TOO_LARGE`（Content-Length 预检 + chunked 有界兜底双检）。
- **数据面记录失败 typed 通道（W-05）**：数据面 topic（`nop-stream.data.` 前缀，与裁定⑥ 分界同构）反序列化失败/消费失败经 `WireDecodeFailureAware` 订阅方（`onWireDecodeFailure`）typed 升级为通道终态（cursor 不前移）；非 aware 消费者为 fail-loud wedge（typed `ERR_STREAM_DATA_PLANE_RECORD_FAILED` 抛出、head-of-line 持续可见）——控制面 topic 维持 warn+skip 既有语义；`WireDecodeFailureAware` 的使用面自本批起放宽覆盖 payload 级失败（接口本体零改动）。
- **LOCAL teardown zombie 清理（W-06）**：submit 拒绝路径（receiveAssignment/deployTask 的 executor 拒绝 catch）现按既有 build-failure 同款清理（槽位条件 remove + cancel + 许可 CAS 归位）——拒绝不再留下僵尸槽位/许可泄漏；`taskDeployed` 计数后置到部署成功之后（zombie 不再计入）。
- **2PC commit 重试升级出口（W-03/C5-006）**：participant 的 finishCommit 连续失败达到阈值（配置键 `nop.stream.checkpoint.commit-retry-escalation-threshold`，缺省 6，≤0 在阈值解析时点 typed fail-fast[2535 closure audit Minor-2 措辞订正——解析于首次重试轮，非装配期]）后，该参与者被从 checkpoint participant 集注销并触发一次 typed 升级失败（`ERR_STREAM_COMMIT_RETRY_ESCALATED` → coordinator failJob，幂等）——修复前账本按 checkpoint 频率无界累积且每轮全表重试放大；既有语义零变化面：单轮成功恢复路径（CLEARED）、abort 门控、重试 WARN 抑制。
- **RocksDB 状态层指标与包络（CRA 8.43 R4-03）**：state 层 gauge 以 `backend=<recorder 序号>` 单标签注册（per-subtask 互异，backend close 幂等摘除——消除多 subtask 静默去重与 close 后永久 NaN）；个别 RocksDB 属性不可用时 WARN 一次、后续降 debug（「告警一次」声明兑现为 once-guard 形态）。新旋钮族：checkpoint 快照预算耦合校验（`CheckpointConfig.snapshotDeclaredEntries` builder/setter 字段——声明条目数，0=旁路，**零配置键接线**[closure audit Minor-5 订正]）+速率常数与 `nop.stream.rocksdb.snapshot-hash-policy`（new-only 缺省/full opt-out）；CEP 匹配缓冲 `nop.cep.pipeline.match-buffer.max-events`（缺省 100000，typed 拒绝=watermark 停滞/缺失 within 的可见信号）；2PC 双旋钮（descriptor 参数 camelCase：`maxEpochRecords`/`maxEpochLines`=100000、`maxPendingEpochs`=8[closure audit Minor-5 键形订正]）与语句超时（`statement-timeout-seconds`=60）、file manifest 锁期限（60s）——各旋钮 ≤0 构造期 typed fail-fast；push→pull 桥字节界 16MB 为字符串/字节载体直测下界口径（非字符串/字节载体计 0——字节界守卫对其不设防，以运维观测为准）。
- **规模与内存包络（CRA 8.42 W-04/C5-012 声明降级）**：`MemoryBudget`/`MemoryBudgetMonitor` 为 **RECORD-ONLY, NOT ENFORCED**（`@Deprecated(since="8.42")`）——live 内存边界由 `EdgeConfig` 逐边容量（queueCapacity/receiveWindowBytes）与 LOCAL 池守卫（`nop.stream.local.await-completion-timeout-ms` 等）承载；「按 totalBytes 分配 50%/推算/网络缓冲」的预算算式从未按真实记录大小执行，不构成容量硬保证。状态管理设计文档 §9 已加同款状态注记。
- 测试锚点：`TestStreamRecordChangeMetadata`、`TestChangeMetadataBridgeAndGuard`、`TestStreamElementCodecChangeMetadata`、`TestChannelStateChangeMetadata`、`TestChangeEventSemanticView`。

### SUSPEND 数据语义（WI9/plan 2333）

- **两策略契约**：SUSPEND = terminal savepoint + 作业 FINISHED（可恢复）。恢复（同 jobId 重提交，显式 storage path）后的停机期数据处理由重提交时 CheckpointConfig 的 `suspendDataPolicy` 决定：`REPLAY_GAP`（缺省 = 现状补算——REPLAYABLE source 从 savepoint offset 重放停机期数据）与 `SKIP_GAP`（跳过——恢复后 source seek 到 latest，停机期数据不重放）。
- **逐源支持矩阵**：SKIP_GAP 当前仅支持 `CollectionReplayableSource`（seek(size())）；其余 REPLAYABLE source（batch-loader/debezium/目录文件）恢复即 typed 拒绝 `nop.err.stream.skip-gap-unsupported-source`（无 latest 语义——响亮失败优于错误跳过）。REPLAY_GAP 对全部 REPLAYABLE source 可用。
- **策略不进 manifest**：策略随重提交的 CheckpointConfig 携带（缺省 REPLAY_GAP），不持久化——每次重提交须显式设置，否则回落补算。
- **REST resume 边界**：REST submit 对已存在 jobId（含 FINISHED）返回 409 且无 remove 端点——REST SUSPEND 后**无法经 REST 重提交恢复**；恢复走嵌入式 `env.execute(jobName, savepointPath)` 或 `JobCoordinatorMain` 同 jobId 重启（自动恢复 latest durable）。REST 作业（OpsJobManager 自建 config）当前无法表达 SKIP_GAP（不对称，如实声明）。
- **非 REPLAYABLE source**：无 offset 可 seek/replay，SUSPEND 恢复行为由各 source 自身恢复语义决定（方向实测前不作契约承诺）。
- 测试锚点：`TestSuspendDataPolicy`（补算/跳过/typed 拒绝三路径）、`TestOpsRestLifecycleE2E`（SUSPEND 放行 + EXPORT_SAVEPOINT 400 钉子）。

### 指标采集与告警配置速查

- Prometheus：`nop.stream.ops.http.enabled=true` + `port`（默认 8901）→ `GET /metrics`（TextFormat 0.0.4 / OpenMetrics 协商）；配置模板 `nop-stream-runtime/src/main/resources/_vfs/nop/stream/conf/metrics.properties.template`。多 JVM 模式：JC 端点 + 各 TM 端点（`TaskManagerMain opsHttpPort=<port>`——main-scope 生产入口；每 TM 一个 scrape target；端口避开 JC 的 8901/8931，演练装置自 8941 起分配）。
- 数据面背压观察：TM 面直读 `nop_stream_io_channel_queue_size`（通道水位 gauge，per-edge max 为关注口径）与 `nop_stream_io_emit_time_seconds_*`；`nop_stream_msg_queue` 表 COUNT 作 append-write 后端的对照代理——该表现带保留清理（消费后可删：已派发位点 + TTL 双谓词取交，见「账本保留策略」节），表深不再单调增长，COUNT 同时反映未消费积压（门控口径同源）。
- 周期 sink：`nop.stream.metrics.log.*`（stdout/file）。
- 告警：`nop.stream.alert.*`（见「告警与事件外发」节）；coordinator 进程日志检索锚点：`nop-stream job event:`（事件）、`job health transition:`（健康迁移）、`nop-stream alert:`（告警外发）。
- 治理：`nop.stream.ops.checkpoint-history.*` / `nop.stream.ops.job-record.*` / `nop.stream.ops.governance.cleanup-interval-ms`（见「历史与日志生命周期治理」节）。

### 故障排查速查

- `GET /jobs/{jobId}`：health/jobStatus/failureCause/restartCount/checkpointOverview 一屏定位。
- `GET /jobs/{jobId}/checkpoints`：checkpoint overview + history（失败记录含 `failureCause`）。
- `GET /jobs/{jobId}/threaddump`：coordinator 进程全线程栈。
- 指标族：engine 层（checkpoint 计数/时长/恢复数）→ task 层（部署/失败/在跑）→ operator/io 层（记录吞吐）→ state 层（RocksDB 统计），名表见「分层指标标准集」节。
