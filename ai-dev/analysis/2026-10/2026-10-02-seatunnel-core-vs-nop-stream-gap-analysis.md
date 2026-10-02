# SeaTunnel 核心功能 vs nop-stream 缺口调研

> Status: resolved
> Date: 2026-10-02
> Scope: `~/sources/seatunnel`（dev @ a1084015，2026-10-01 最新） vs `nop-stream/` + `nop-message/nop-message-debezium`
> Conclusion: nop 平台确已有 Debezium 集成（嵌入式引擎、offset 进 checkpoint）、完整流处理能力（窗口/CEP/watermark/双状态后端）和真 barrier 协议 checkpoint（对齐+unaligned、savepoint、restore 端到端 wired、E2E kill-recover 验证）；但与 SeaTunnel 核心功能对比，缺口集中在连接器生态（8 工厂 vs ~84 连接器）、CDC 深度（现状无并行快照——Debezium 3.5+ 已内置线程级并行快照，升级可解，但框架级分布式 chunk 并行与断点续传仍需自建；无 schema evolution 协调、3 种库 vs 9+）、Catalog/类型系统元数据层、集群生产化（无 K8s/YARN、生产进程入口在 test sources）四项；流计算语义（窗口/CEP/状态）反而是 nop 超出 SeaTunnel 的方向——SeaTunnel 是数据集成引擎，本不做窗口聚合与 CEP。

## Context

- 决策点：评估「用 nop-stream 实现 SeaTunnel 核心功能」的可行性，回答三个事实问题：(1) nop 是否已有 Debezium 集成？(2) 是否已有 stream 处理能力？(3) 是否包括 checkpoint？
- 前置动作：`~/sources/seatunnel` 已从 5dbfb37（2026-08-31）fast-forward 到 a1084015（2026-10-01）。
- 方法：两侧均基于源码逐文件核查（非文档转述），关键论断附文件路径证据。

## 调研目标

- [x] 核实 nop 平台 Debezium / stream / checkpoint 三大能力的真实实现深度
- [x] 拆解 SeaTunnel（最新 dev）的核心功能支柱及其实现细节
- [x] 逐支柱对比，给出缺口清单、分级与结构性原因
- [x] 分场景判定「能否满足核心功能」

## 一、事实核查：nop 平台三大能力现状

### 1.1 Debezium 集成：**已有，嵌入式引擎路线，深度集成 checkpoint**

- 结构：`nop-message/nop-message-debezium`（Debezium 封装层，Debezium **2.4.0.Final**）+ `nop-stream/nop-stream-connector-debezium`（流连接器，`DebeziumCdcSourceFunction.java` 503 行）。
- 用的是 `DebeziumEngine.create(JsonByteArray.class)` **嵌入式引擎**（`engine/DebeziumEngineWrapper.java`），不是 Kafka Connect 集群，不依赖外部 Kafka。
- 关键设计：**offset 即 checkpoint 状态**。offset 存于自定义 `NopStreamOffsetBackingStore`（内存 map，经 `offset.storage` 属性 + 反射注入，按 connector name 的 static registry 桥接 Debezium 2.4.0 Builder API 缺口）；`snapshotState()` 把 offsets 写入 `OperatorSnapshotResult`，`initializeState()` 恢复后引擎从 checkpoint 的 DB 位点续传（`CheckpointedSourceFunction` 契约，`nop-stream-core/.../functions/source/CheckpointedSourceFunction.java`）。
- 支持范围：`DebeziumConfig.connectorType` = **mysql / postgres / sqlserver** 三种；`snapshotMode=initial`（快照+增量由引擎承担）；schema change 事件可开关（默认关）；schema history 仅支持**文件路径**；凭证走 `credential:{id}#{field}` 引用、引擎侧瞬时解密。
- 错误传播已闭环：collector 异常与引擎终死都会置 `pendingError` → `run()` rethrow，任务 FAILED 而非静默断流。
- 测试：9 文件 / 42 个 @Test，含真 E2E（`TestDebeziumCdcCheckpoint.testCdcCheckpointKillRecoverNoDuplicates`：跑→cancel→snapshot→新实例 restore→断言恢复续跑且跨两次运行无重复 key）。**但全部测试用 `MockCdcMessageSource` 替身，无任何 Testcontainers/真实数据库集成测试**。
- Debezium 版本对比：nop 用 2.4.0.Final（较新）；SeaTunnel CDC 反而锁在 **1.9.8.Final**（`~/sources/seatunnel/seatunnel-connectors-v2/connector-cdc/pom.xml:47`），但绕过 Engine 黑盒、直接复用 Debezium 内核类并打补丁（自带 `io/debezium/{connector/base, heartbeat, relational}` 补丁包），换来并行快照与 schema 协调能力。两条路线的取舍见 §4。
- **勘误（2026-10-02 补查）**：Debezium **3.5.0.Final**（2026 年春 GA）起内置**单表 chunk 级多线程并行 initial snapshot**（`snapshot.max.threads`，默认 1 关闭；旧版 2.3 起已有 `snapshot.max.threads` 多表并行）。即「Debezium 不支持并行快照」只在 nop 当前钉住的 2.4.0 + 默认配置下成立，升级 3.5+ 可经配置获得线程级并行 initial snapshot——但这是**单进程内线程并行**，非框架级分布式 chunk 分发，且 initial snapshot 中途失败不可从 chunk 断点续传（chunk 进度不进 offset）。增量（信号驱动）快照的 chunk 处理至今仍是单线程交错流式。详见 G2 勘误。

### 1.2 Stream 处理能力：**已有，语义覆盖接近 Flink 子集**

- 数据模型：`StreamRecord<T>` 泛型记录 + `StreamElement` 家族（Watermark/WatermarkStatus/CheckpointBarrier/SideOutput），无统一强类型系统（对比见 §2.1）。
- 窗口：Tumbling/Sliding/Session（Event+Processing 双时间域）/Global + 8 种 trigger + 3 种 evictor + allowedLateness + 迟到 side output（`nop-stream-core/.../windowing/`、`nop-stream-runtime/.../operators/windowing/WindowOperator.java`）。
- CEP：完整 Flink 风格 NFA（`nop-stream-cep/.../nfa/NFA.java`、shared buffer、DeweyNumber、aftermatch 跳过策略）+ Pattern API + 声明式 `pattern.xdef`（量词/时间约束/连续性）。**限制：CEP × RocksDB 组合当前不可用，CEP 限 Memory 后端**（`nop-stream/README.md:20` 自曝）。
- Watermark/事件时间：完整移植 Flink 体系（WatermarkStrategy、乱序容忍、idleness、alignment），XDSL 有 `timestampsAndWatermarks` 节点。
- 状态后端：Memory 全套（Value/Map/List/Reducing/Aggregating + TTL）+ `nop-stream-rocksdb`（keyed state 落 RocksDB、key-group 分片、支持 rescale 重材料化、与 Memory 后端 checkpoint 字节兼容）；**operator state 仍仅 Memory**。
- 反压：BufferPool（Semaphore）+ 边级 FlowControlPolicy/queueCapacity/receiveWindow + TaskMailbox 线程模型，有多 JVM 背压 E2E 测试。
- 缺失算子：无 interval join / stream-stream join（仅 union + sideInputs）；无 top-N/去重等高层算子。
- 作业定义：XDSL `stream.xdef`（234 行）声明 checkpoint/窗口/CEP/edges 分区/流控，三入口（XDSL/Java DataStream API/Delta）合成同一 canonical `StreamModel`（含 fingerprint）。

### 1.3 Checkpoint：**已有，真 barrier 协议，restore 端到端 wired**

- 机制：`nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`（2000+ 行）实现周期触发 + barrier 对齐（`CheckpointBarrierTracker`），支持 **unaligned checkpoint**（单 in-flight + channel state）、有界重试、abort 传播、minPause/maxConcurrent 背压语义、异步快照。这不是「简单同步快照」，是接近 Flink 的 barrier 协议。
- 存储：`LocalFileCheckpointStorage` + `JdbcCheckpointStorage`（`runtime/checkpoint/storage/`），另有增量快照基础设施（SharedStateRegistry/SST 校验）。
- **restore 全链路 wired，非只 snapshot**：`GraphModelCheckpointExecutor` 在任务提交**前**执行 `restoreFromCheckpoint()`；savepoint 走 `executeWithSavepoint` + `triggerSavepoint`，且支持改并行度的 reshard（`materializeKeyGroupOwnership` + `MaxParallelismReshardMigration`）；`JobCoordinator` failover 从持久存储重建并推进 epoch。
- Exactly-once：`ProcessingGuarantee` 默认 `STRICT_EXACTLY_ONCE`，build 期 fail-fast 校验「REPLAYABLE source + 2PC sink」组合；2PC sink 两个实现——`JdbcTwoPhaseCommitSink`（每 checkpoint epoch 一个事务，ledger 表 `stream_epoch_ledger_v2` 按 `(namespace, epoch_id, subtask_id)` 幂等防重，提交前查 ledger）与 file（epoch 文件 + manifest）。恢复不重复提交由 `TwoPhaseCommitSinkFunction.restoreFromEpoch` 保证。
- 分布式：`RpcDistributedExecutor`（控制面真 RPC + `deployTask` 远端重建 + epoch fencing）+ `JdbcClusterRegistry`/`JdbcLeaderElector`（DB 选主）+ 跨 JVM 数据面（DB/Pulsar/Kafka codec）。多 JVM kill/恢复 E2E 测试存在（`TestMultiJvmExactlyOnceRecovery` 等，需系统属性手动开启），但其自述 note 承认：完整跨 JVM source→keyBy→sink exactly-once 断言仍是基础设施级验证，未收口。
- 生产化缺口（README 自曝 + 代码核实）：**独立生产进程入口 `JobCoordinatorMain`/`TaskManagerMain` 位于 `src/test/java`**；无 K8s/YARN 部署编排、无 HPA；无对象存储/HDFS checkpoint 后端。

**小结：三个问题的答案都是「是」，且实现深度显著高于「demo 级」**——checkpoint 有 kill-recover E2E、Debezium offset 真实进 checkpoint、流语义覆盖窗口/CEP/状态。短板在生态广度与生产化外围（下文逐条展开）。

## 二、SeaTunnel 核心功能拆解（dev @ a1084015）

规模基线：`seatunnel-api` 273 个 main 文件；`seatunnel-engine` 589 个；`seatunnel-connectors-v2` **2644 个 main 文件 / 约 84 个连接器目录**；CDC 子系统 11 个模块 284 个文件。六大支柱：

1. **引擎无关连接器 API + 类型系统**：`SeaTunnelRow`（`Object[]` + `tableId` 多表标识 + `RowKind` changelog 语义）+ `SeaTunnelDataType`/`CatalogTable`（20+ 元数据类）+ `SeaTunnelSource/SourceReader/SourceSplitEnumerator`（split 枚举协议、`restoreEnumerator`）+ `SeaTunnelSink/SinkWriter/SinkCommitter/SinkAggregatedCommitter`（双层 2PC）+ 13 个 schema evolution 事件类。API 正处 CatalogTable 迁移期（旧 type 接口全部 @Deprecated）。
2. **Zeta 引擎**：Hazelcast IMDG 集群（无 master 单点，选主自带）、DAG 四级规划（Logical→Execution→Physical→CheckpointPlan）、`TaskExecutionService`（1748 行）、slot/资源管理器（StandaloneResourceManager + 调度策略）、REST + Vue3 engine-ui、客户端（含连接器 jar 动态上传 `ConnectorPackageClient`）、按 jobId 类加载隔离 + `plugin-mapping.properties` jar 级插件发现。
3. **Checkpoint/容错**：`CheckpointCoordinator`（1645 行）+ barrier 传播（`SourceFlowLifeCycle.triggerBarrier` 在 checkpoint 锁内 snapshot→ack→转发）+ `PendingCheckpoint` 收齐 ack；存储可插拔（**local-file / hdfs / s3 / oss / cos / gcs**）；savepoint/恢复模式（NONE/SAVEPOINT/CHECKPOINT）；pipeline 级重试（`job.retry.times` 默认 3）；HA 靠 Hazelcast 选主 + IMap 状态副本在新 master 恢复全部作业；无独立 RestartStrategy 类体系。
4. **连接器生态**：~84 连接器（file/jdbc/kafka/pulsar/es/iceberg/paimon/hive/hudi/doris/starrocks/clickhouse/redis/mongodb/向量库等），plugin-mapping 98 source + 85 sink 映射；22 个 transform 包（sql/copy/jsonpath/encrypt 等）；Catalog SPI 分散在各连接器（无顶层 catalog 模块）；SaveMode（建表/清数据策略）作业启动前执行。
5. **CDC（connector-cdc）**：**不采用** DebeziumEngine，直接复用 Debezium 内核（自研 `MySqlSnapshotChangeEventSource` 等）+ **自研无锁并行增量快照**（`ChunkSplitter` 按主键切块、`HybridSplitAssigner` 快照→增量切换、低/高水位对齐合并）；offset/已完成快照信息/**DDL history 全部存进引擎 checkpoint**（`EmbeddedDatabaseHistory`）；schema 变更用特有 `SCHEMA_CHANGE_BEFORE/AFTER` checkpoint 阻塞数据流协调 DDL，JDBC sink 侧执行 DDL；9+ 数据库（mysql/oracle/postgres/sqlserver/db2/mongodb/oceanbase/opengauss/tidb/vitess）；JDBC exactly-once 基于 **XA**（`JdbcExactlyOnceSinkWriter` + XaGroupOps 悬挂事务恢复）。
6. **配置/作业**：HOCON `env/source/transform/sink` 块 + `job.mode`（BATCH/STREAM）+ dry-run 校验 + savepoint 停止；无内置调度（需外挂）。

## 三、逐支柱对比与缺口

| # | 支柱 | SeaTunnel | nop-stream | 缺口评级 |
|---|------|-----------|------------|---------|
| 1 | 连接器生态 | ~84 连接器、98+85 映射、22 transform | 8 个内建工厂：source= file/message/debezium-cdc/batch-loader，sink= file/message/jdbc-2pc/batch-consumer | **P0 最大缺口** |
| 2 | CDC 深度 | 内核复用 + 分布式并行快照（chunk 跨 subtask）+ schema evolution 协调 + 9+ 库 + XA | 嵌入式引擎（钉 2.4.0：无并行快照；Debezium 3.5+ 有单进程线程级并行，升级可解）、3 库、schema 事件仅透传、无 DDL 协调执行、schema history 仅文件 | **P0**（可部分经升级 Debezium 收窄） |
| 3 | 类型系统/Catalog | SeaTunnelRow + RowKind changelog + CatalogTable + 多表 tableId | `StreamRecord<T>` 泛型，无统一类型系统、无 Catalog 抽象、无 SchemaSaveMode/DataSaveMode | **P1** |
| 4 | checkpoint 机制 | barrier + 存储插件（hdfs/s3/oss/cos/gcs）+ savepoint/HA | barrier + **unaligned** + savepoint + 改并行度 reshard；存储仅 local-file/JDBC | **基本持平**（机制 nop 略强，存储选项 nop 少） |
| 5 | exactly-once sink | SinkCommitter 双层 + JDBC XA | TwoPhaseCommitSinkFunction + JDBC epoch ledger 幂等 / file epoch+manifest | 持平（路线不同：XA 需 DB 支持，ledger 需建表，各有前提） |
| 6 | 集群/部署 | Hazelcast 集群、slot/资源管理、REST/UI、client、jar 上传、按 job 类加载隔离 | RpcDistributedExecutor + DB 注册/选主/epoch fencing；生产进程入口在 test sources；无 K8s/YARN/HPA；无 UI（有 ops HTTP）；无 jar 动态上传 | **P1（生产化）** |
| 7 | 作业 DSL/提交面 | HOCON + dry-run + savepoint CLI + REST 提交 | XDSL（checkpoint/window/cep/edges/流控语义更丰富）+ Delta 定制 + fingerprint | 持平偏 nop（声明语义强），提交运维面弱 |
| 8 | 流计算语义 | **无**窗口/CEP/keyed 状态聚合 | 完整窗口/CEP/watermark/双状态后端/TTL | **nop 反超**（SeaTunnel 不做流计算） |

### 缺口明细与原因

- **G1 连接器生态（8 vs ~84）**：缺 kafka/pulsar 直连 source、jdbc batch source、es/iceberg/paimon/clickhouse/doris/redis/mongodb 等全部生态 sink。原因不是架构不能，而是纯工程量：SeaTunnel 的 2644 个连接器文件是 Apache 顶级项目多年社区积累；nop-stream 自 2026-08-01 以来 193 条 commit 全部是内部质量迭代（plan 366/368/369 审计-修复），尚未进入连接器扩张期。另有一个前置技术债：连接器 API 有两代（legacy `SourceFunction` 已冻结、FLIP-27 式 `Source/SplitEnumerator/SourceReader` 新代就位但存量连接器未迁移），扩生态前必须先统一到新代，否则每加一个连接器都在加深欠账。
- **G2 CDC 深度**：(a) 无并行快照（**勘误**：这是 nop 钉住 2.4.0 + 默认配置的现状，而非 Debezium 的能力上限——Debezium 2.3 起支持 `snapshot.max.threads` 多表并行 initial snapshot，3.5.0.Final 起 GA 单表 chunk 级多线程并行；但增量/信号驱动快照的 chunk 处理至今单线程，且 Debezium 原生并行是单进程线程级，initial snapshot 中途失败不能从 chunk 断点续传）。SeaTunnel 的护城河在框架级：ChunkSplitter 把 chunk 作为 split 分发给多 subtask **跨进程/跨节点**并行、chunk 完成状态进引擎 checkpoint 支持断点续传、低/高水位协议把并行快照与增量流合并（connector-cdc 284 个文件的主体）——这三点升级 Debezium 版本无法消解；(b) schema evolution 只有「事件开关」，没有 SeaTunnel 的 SCHEMA_CHANGE checkpoint 阻塞协调 + sink 侧 DDL 执行，上游 DDL 会直接断流或数据错位；(c) schema history 仅文件路径，多节点/容器化场景不可靠；(d) 数据库覆盖 3 vs 9+。注意一个反向细节：**Debezium 版本 nop（2.4.0）新于 SeaTunnel（1.9.8）**——SeaTunnel 为内核改造自由被旧版锁死，nop 的 Engine 黑盒路线反而升级容易（升至 3.5+ 即得线程级并行快照），这是两种路线的代价交换（详见 §4）。
- **G3 类型系统/Catalog 元数据层**：SeaTunnelRow 的 `RowKind`（INSERT/UPDATE_BEFORE/UPDATE_AFTER/DELETE）+ `tableId` + CatalogTable 是多表同步、CDC 下游正确应用变更（upsert/delete 语义）、自动建表（SaveMode）的元数据基础。nop 的 `StreamRecord<T>` 让 CDC 变更语义只能编码在消息体内部约定里，无法做引擎级的多表路由、投影下推、类型转换链（DB 类型→引擎类型→目标 DDL）。这是实现 G1 生态连接器之前的**结构性前置**——没有 CatalogTable 抽象，每个 sink 都要自己猜 schema。
- **G4 集群生产化**：nop 的分布式骨架（RPC 控制面 + DB 选主 + epoch fencing + 跨 JVM 数据面）是真的且有测试，但 (a) 生产进程入口在 test sources（`JobCoordinatorMain`/`TaskManagerMain` 在 `src/test/java/.../launch/`），无打包/启动脚本/容器镜像；(b) 无 K8s/YARN 编排与 HPA（README 自曝）；(c) 无 slot/资源隔离模型（SeaTunnel 有 slot service + resource manager）；(d) checkpoint 存储缺对象存储/HDFS 后端（对云上部署是硬前提）。原因：nop 的部署策略是复用平台已有的 IMessageService/DB 基础设施而非引入 Hazelcast，方向自洽，但外围编排层还没补。
- **G5 可观测性/运维面**：nop 有 StreamOpsHttpServer/健康状态机/告警渠道，SeaTunnel 有 REST + 完整 Vue3 UI + 作业事件日志 + 实时指标聚合。nop 有运维 API 未见 UI 与连接器 jar 动态上传/客户端 SDK 生态。

## 四、分场景判定：能否满足核心功能

| 场景 | 判定 | 依据 |
|------|------|------|
| A. 单/少数库 CDC 管道（mysql/postgres/sqlserver → jdbc/file/message），中小吞吐，单机或小集群 | **基本满足**，前提是接受大表初始化串行（可经升级 Debezium 3.5+ + `snapshot.max.threads` 缓解）+ 无真实 DB 集成测试的风险 | checkpoint kill-recover E2E、offset 进 checkpoint、2PC ledger 幂等均有测试佐证；G2a（并行快照）/G2c（schema history）是吞吐与运维风险 |
| B. 多源异构数据集成平台（SeaTunnel 主战场：数入湖、多库汇聚、批量同步） | **不满足** | G1（76+ 连接器缺失）+ G3（无 Catalog/多表语义）是硬缺口 |
| C. 流式计算（风控规则、CEP、窗口聚合、有状态流处理） | **不适用该对比**——SeaTunnel 不提供此能力，nop-stream 反而是超集 | SeaTunnel 引擎无窗口/CEP/keyed 聚合概念 |
| D. 云上/容器化弹性部署 | **不满足** | G4：无 K8s/YARN、生产进程入口未发布、无对象存储 checkpoint |

### 为什么（结构性原因）

1. **定位差异**：SeaTunnel 是「数据集成引擎」（ETL 通道，source→transform→sink，无状态计算），Zeta 的 slot/类加载隔离/jar 上传全为连接器生态服务；nop-stream 是「平台内嵌流处理」（借 Flink 语义，为风控/CDC 场景服务）。两边核心功能交集其实只有 CDC 管道与 checkpoint 容错这一块。
2. **CDC 路线交换**：Engine 黑盒（nop）= 升级容易、开发快、但单线程无并行快照、schema 协调受限于引擎回调；内核复用（SeaTunnel）= 并行快照与 DDL 协调自由、但被 Debezium 1.9.8 锁死多年、升级要拖着补丁包走。选哪条取决于要不要并行快照，没有免费午餐。
3. **生态是时间函数**：连接器数量、Catalog 抽象、UI/客户端这类「广度资产」只能靠长期投入，与 checkpoint/窗口这类「深度资产」可以一次设计到位不同。nop-stream 当前处于深度打磨收尾期（193 条 commit 全是质量迭代），广度扩张尚未开始。

## 五、与当前项目的关系

**可借鉴（若要收窄差距）：**
- G3 先行：引入 CatalogTable 级元数据抽象（含 RowKind 变更语义与 tableId）是 G1/G2 的共同前置，且与 nop 现有元数据体系（nop-metadata）天然契合。
- checkpoint 存储加一个 S3 协议后端（覆盖 s3/oss/cos 大多数云）成本低于 hdfs 全家桶。
- 新代 Source API（FLIP-27 式）迁移完成后再扩连接器，避免两代并存加深欠账——这与 SeaTunnel 自身正在经历的 CatalogTable 迁移教训一致。
- CDC 若要上强度（按代价升序）：① 升级 Debezium 至 3.5+ 并开启 `snapshot.max.threads`（单表 chunk 线程级并行 initial snapshot，纯配置+兼容性校验，需重验 `NopStreamOffsetBackingStore` 反射 wiring 与 3.x breaking changes）；② Debezium 原生增量快照（信号表触发，2.4 经 `DebeziumConfig.extraProperties` 理论可开启，chunk 进度存于 offset 可随 checkpoint 恢复——**未经验证，需真实 DB 集成测试确认**）；③ 重量路线 SeaTunnel 式内核复用（框架级分布式 chunk 并行 + 断点续传 + schema 协调，代价是被版本锁死）。

**不可借鉴：**
- Hazelcast 全家桶（IMap 状态存储、Operation RPC、强耦合集群）：nop 已选择 IMessageService + DB 注册的自洽路线，引入 Hazelcast 会造成两套集群基础设施。
- HOCON 配置体系：XDSL + Delta 的声明能力（checkpoint/窗口/CEP/流控/ fingerprint）强于 HOCON 的 env/source/transform/sink，无需倒退。

## Conclusion

- 三个事实问题答案均为**是**：nop 已有 Debezium 集成（嵌入式引擎 2.4.0、3 种库、offset 进 checkpoint、kill-recover E2E）、stream 处理能力（窗口/CEP/watermark/Memory+RocksDB 状态后端/反压）、checkpoint（barrier 对齐+unaligned、savepoint、restore 端到端 wired、LocalFile+JDBC 存储）。
- 「用 nop-stream 实现 SeaTunnel 核心功能」按场景分级：CDC 管道场景基本满足（有吞吐与测试深度前提）；数据集成平台场景不满足，P0 缺口为连接器生态（8 vs ~84）与 CDC 深度（无并行快照、无 schema evolution 协调），P1 前置为 Catalog/类型系统元数据层与集群生产化（K8s/YARN/进程入口/对象存储 checkpoint）；流计算场景 nop 反超。
- 被否决的方案：引入 Hazelcast 替换现有分布式骨架（与 IMessageService/DB 注册路线冲突，维护双基础设施）；回退 XDSL 到 HOCON（声明能力倒退）。
- 后续工作：若决定收窄缺口，建议按 G3（Catalog 元数据抽象）→ G1（新代 API 统一后扩连接器）→ G2（增量快照路线选型）顺序拆 plan；本文不预设立场，结论可被后续 design 文档推翻。

## Open Questions

- [ ] Debezium 原生增量快照经 `extraProperties` + 信号表开启后，进度是否能随 nop checkpoint offset 正确恢复？（需真实 DB 集成测试，当前 42 个测试全为 mock）
- [ ] 升级 Debezium 2.4.0 → 3.5+/3.7 的兼容面：`NopStreamOffsetBackingStore` 反射注入链、`snapshot.max.threads` 并行快照在嵌入式引擎（`DebeziumEngine`）路径下是否等效生效、3.x offset 格式/API breaking changes 对 checkpoint 恢复旧位点的影响
- [ ] nop-stream 生产进程入口（`JobCoordinatorMain`/`TaskManagerMain`）从 test sources 提升为正式模块的时机与打包形态（fat-jar? 容器镜像?）
- [ ] `StreamRecord<T>` 是否值得升级为带 RowKind/tableId 的引擎级记录类型，还是仅在 CDC 连接器层约定消息信封（影响 G3 实施深度）

## References

- `nop-stream/README.md`（自曝短板：K8s/YARN/HPA 缺失、CEP×RocksDB 不可用）
- `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-cdc-cookbook.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`
- `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`
- `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/`（DebeziumEngineWrapper / NopStreamOffsetBackingStore）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/`
- SeaTunnel：`~/sources/seatunnel` @ a1084015 — `seatunnel-engine/seatunnel-engine-server/.../checkpoint/CheckpointCoordinator.java`、`seatunnel-connectors-v2/connector-cdc/`（增量快照体系）、`seatunnel-connectors-v2/connector-cdc/pom.xml:47`（Debezium 1.9.8.Final）
- 外部：[Apache SeaTunnel](https://github.com/apache/seatunnel)、[Debezium incremental snapshots](https://debezium.io/documentation/reference/transformations/incremental-snapshot.html)、[Debezium 3.5.0.Beta1 发布公告（并行 chunk 快照）](https://debezium.io/blog/2026/02/26/debezium-3-5-beta1-released)、[Debezium 3.7 Final 发布公告（2026-09-29，当前 latest）](https://debezium.io/blog/2026/09/29/debezium-3-7-final-released)
