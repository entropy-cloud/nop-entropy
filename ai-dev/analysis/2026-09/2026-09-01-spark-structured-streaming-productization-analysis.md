# Spark Structured Streaming 产品化分析（roadmap item 3）

> Status: resolved
> Date: 2026-09-01
> Scope: Apache Spark master @ `992b0905fa72d1d393936ff827e9c26514013964`（`git -C ~/sources/spark rev-parse HEAD`，`--depth 1` shallow clone，clone 于 2026-09-01，315MB）× plan 1 评估矩阵 7 维度（流处理相关子集）；nop-stream 产品化 P-REQ 候选（`SPS-` 前缀）
> Conclusion: Spark SS 产品化强项在 API/DX（3@high，DataFrame/SQL 增量统一模型 + 5 态触发器 + transformWithState 类型化状态 API）、运维监控（3@high，StreamingQueryListener 进度标准集 + Web UI Streaming tab + Prometheus servlet）、性能（3@high，AQE/codegen/Tungsten，但 AQE 与流式 stateful/Real-time 互斥——SPARK-53941）、部署（3@high，local/standalone/YARN/K8s + docker 工具链）；容错 2@high（WAL 检查点 + 双状态后端成熟，但故障恢复为整查询重启、无分区级 failover）。产出 9 条 `SPS-` 候选，建议归属 items 16/17/6/11。后续工作由 item 5 汇编。
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 3；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0753-2-competitor-source-productization-analysis.md` Phase 2；评估矩阵 `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md` §2

## Context

- roadmap item 3 要求获取 Spark 源码（允许仅 streaming 相关子集）并按 plan 1 矩阵做产品化分析；既有 `~/sources` 与 `ai-dev/analysis/` 无任何 Spark 相关报告（plan 1 §2.4 缺口清单确认全 7 维度未覆盖），本报告为首个 Spark SS 产品化证据源。
- 本报告所有源码证据锚定上述 commit SHA；分析范围限流处理相关子集（Structured Streaming / 状态 / 调度 / 部署 / 运维），Spark 非 streaming 部分（SQL 优化器全貌、shuffle 服务细节、ML/GraphX）不深挖（plan Non-Goals）。

## 附录 A：clone 策略 Decision（plan Phase 2 第 1 项）

**裁定：完整 `--depth 1` clone**（否决 partial clone：`--filter=blob:none` + sparse-checkout 限 streaming 子集）。

理由：

1. **磁盘非约束**：执行时 `df -h ~` 可用 95Gi；完整 depth-1 clone 实测 315MB（pack + 27312 文件 working tree），占比 <0.5%。
2. **可分析性**：partial clone 按需拉 blob，本分析是高频小文件读取（grep/read 证据取证），每次冷读走网络显著拖慢且依赖网络持续可用；sparse-checkout 需预先枚举目录，漏枚举即静默丢证据路径。
3. **7 维度证据路径覆盖逐项确认**（plan 要求 sparse 起始集之外必须显式裁定）：

| 维度 | 所需目录 | 起始集内？ |
|------|---------|-----------|
| API/DX | `sql/api/.../streaming/`（现代 API）、`sql/core/.../sql/classic/`（classic API）、`examples/`（scala 6 + python 7 个流式示例） | `examples/` **超出起始集**（起始集仅 `sql/core|api|catalyst|core|streaming|docs/|external/`），完整 clone 覆盖 |
| FT | `sql/core/.../execution/streaming/`（runtime/checkpointing/state/continuous/operators） | 是 |
| PERF（AQE） | `sql/core/.../execution/adaptive/`（AQE 主体）+ `sql/core/.../execution/WholeStageCodegenExec.scala`（codegen）+ `sql/catalyst` | 是 |
| DEPL | `resource-managers/kubernetes|yarn/`、`bin/`（docker-image-tool.sh）、`sbin/`（start-master/worker、decommission-worker.sh）、`conf/`（模板） | `resource-managers/`、`sbin/`、`conf/` **超出起始集**，完整 clone 覆盖 |
| OPS | `sql/core`（ProgressReporter/Listener）、`sql/core/.../streaming/ui/`、`docs/monitoring.md`、`conf/metrics.properties.template` | `conf/` **超出起始集**，完整 clone 覆盖 |
| CONN | `connector/`（kafka-0-10-sql/avro/protobuf/kinesis-asl）、`sql/catalyst/.../connector/`（DSv2 SPI） | `connector/` **超出起始集**（起始集只有 `external/`），完整 clone 覆盖 |
| DOC | `docs/`（structured-streaming-programming-guide.md、`docs/streaming/` 10 份、monitoring.md） | 是 |

结论：起始集缺 5 处证据目录（`examples/`、`resource-managers/`、`bin+sbin+conf/`、`connector/`），sparse 方案需事后追加且引入网络抖动；完整 clone 以 315MB 磁盘成本换取全部证据路径免剪枝，裁定采纳。

## 评分总表（plan 1 矩阵 §2.2 口径：分数@置信度）

| 维度 | 评分 | 一句话判定 |
|------|------|-----------|
| D1 API/DX | 3@high | DataFrame/SQL 增量统一模型（行业标杆）+ 5 态触发器 + transformWithState 类型化状态 API（Value/List/Map/TTState）；短板仅内置示例量少 |
| D2 连接器生态 | 2@high | DSv2 SPI 完整（catalog/write/distributions），但官方捆绑连接器少（kafka/avro/protobuf/kinesis），生态在仓库外 |
| D3 部署形态 | 3@high | local/standalone/YARN/K8s 四形态 + spark-submit 统一入口 + docker-image-tool + volcano 调度 + worker decommission |
| D4 运维监控 | 3@high | StreamingQueryListener 进度标准集 + Web UI Streaming tab + Prometheus servlet + 22 项 metrics 模板 |
| D5 容错语义 | 2@high | WAL 式 offset/commit 双日志 + 双状态后端 + schema 兼容检查成熟；但故障恢复=整查询重启（无分区级 failover），取最低主检查点 |
| D6 性能 | 3@high | AQE（15+ 规则）+ whole-stage codegen + Tungsten；明确边界：AQE 对 stateful/Real-time 强制关闭（SPARK-53941） |
| D7 文档 | 3@high | programming guide 巨细 + `docs/streaming/` 10 份专题（含 real-time-mode、transform-with-state、state-data-source、migration-guide、performance-tips） |

## Analysis

### D1 API/DX（必答子项：Structured Streaming API 设计）— 3@high

1. **核心模型（标杆）**：流 = 无界表上的增量 DataFrame 查询——`readStream`/`writeStream` 与批 API 同构（现代 API `sql/api/src/main/scala/org/apache/spark/sql/streaming/DataStreamReader.scala`、`DataStreamWriter.scala`；classic 兼容层 `sql/core/.../sql/classic/`）。SQL/DataFrame/Scala/Java/Python 多语言同语义。
2. **触发器一等 API（5 态）**：`sql/api/.../execution/streaming/Triggers.scala:57-125`——`OneTimeTrigger`、`AvailableNowTrigger`、`ProcessingTimeTrigger`、`ContinuousTrigger`、`RealTimeTrigger`（Spark 4.1 新增，见 §D5 模式取舍）。
3. **类型化状态 API（新产品线）**：transformWithState 家族——`sql/api/.../streaming/StatefulProcessor.scala` + `ValueState/ListState/MapState` + `TTLConfig`（状态 TTL 一等参数）+ `StatefulProcessorHandle`；执行侧 `sql/core/.../operators/stateful/transformwithstate/`。
4. **声明式编排边界**：无独立 job DSL——声明式即 SQL/DataFrame 本身；配置仅 spark-defaults。与 nop-stream 的 XDSL 是两条路线（nop-stream P-REQ 不由此产生）。
5. **错误体验**：`execution/streaming/StreamingErrors.scala`（结构化流式错误分类）；分析期校验（unsupported operator 在 analysis 阶段报错，如 streaming dedup/join 限制在 `MicroBatchExecution.scala:640-649` 的 containsStatefulOperator 白名单机制可见边界管理）。
6. **示例**：`examples/src/main/scala/org/apache/spark/examples/sql/streaming/`（6）+ `examples/src/main/python/sql/streaming/`（7）——量级偏少（相对竞品），靠 programming guide 内嵌示例补足。

### D2 连接器生态（选答）— 2@high

- 扩展 SPI：DataSource V2 完整体系在 `sql/catalyst/src/main/scala/org/apache/spark/sql/connector/`（catalog / write / distributions / expressions）——流批共用目录与写入契约。
- 官方捆绑窄面：`connector/` 仅 kafka-0-10(-sql/assembly/token-provider)、avro、protobuf、kinesis-asl、ganglia、profiler（+ `external/` 旧 DStream 附件）；主流源（jdbc、delta、iceberg 等）在第三方仓库。
- 质量保障：捆绑连接器有对应 IT（`connector/docker-integration-tests`）。
- 结论：SPI 完整但**仓库内生态薄**（2@high）；对照 nop-stream：DSv2 等价的 source/sink 契约一致性是 item 10 审计输入。

### D3 部署形态（必答）— 3@high

- 四形态矩阵：local 嵌入（`local[*]` master）、standalone 集群（`sbin/start-master.sh`/`start-worker(s).sh`/`stop-all.sh`）、YARN（`resource-managers/yarn/`）、K8s（`resource-managers/kubernetes/core/` + volcano 调度器支持 `kubernetes/core/volcano/`）。
- 统一提交入口：`bin/spark-submit`（--master/--deploy-mode client|cluster）。
- 容器工具链：`bin/docker-image-tool.sh` 构建 K8s 镜像 + `resource-managers/kubernetes/integration-tests/`（含 volcano 套件）。
- 生命周期运维：`sbin/decommission-worker.sh`（worker 优雅下线）、`sbin/start-history-server.sh`（历史服务）。
- 配置模板开箱：`conf/spark-defaults.conf.template`、`conf/metrics.properties.template`、`conf/workers.template`、`conf/spark-env.sh.template`。

### D4 运维监控（必答子项：运维/监控集成）— 3@high

1. **进度指标标准集**（StreamingQueryListener 事件 + StreamingQueryProgress）：`sql/core/.../runtime/ProgressReporter.scala:355-394,625-663`——`inputRowsPerSecond`、`processedRowsPerSecond`、per-source 输入行数、`stateOperators`（状态算子行数/时长/更新数）、`eventTimeStats`（含 watermark）、`durationMs` 分解、`sinkOutput`。事件 API：`sql/api/.../streaming/StreamingQueryListener.scala`（QueryStarted/Progress/Terminated）。
2. **Web UI Streaming tab**：`sql/core/.../streaming/ui/`（StreamingQueryTab/StreamingQueryPage/StreamingQueryStatisticsPage/StreamingQueryStatusListener）——活跃/完成查询列表 + 输入速率/处理速率/状态行数/批延迟时序图。
3. **标准暴露**：Dropwizard metrics 体系（`conf/metrics.properties.template` 22 项 source/sink 配置样例）+ Prometheus servlet（`docs/monitoring.md:942-943` `/metrics/executors/prometheus`、`:1192-1205` PrometheusServlet 端点表）+ REST JSON（`/api/v1/...`）。
4. **异步进度追踪**：`checkpointing/AsyncOffsetSeqLog.scala` + AsyncCommitLog（异步提交降低端到端延迟可见性缺口，测试 `AsyncProgressTrackingMicroBatchExecutionSuite`）。
5. **诊断**：`docs/monitoring.md`（指标/调试指南）、`StreamingQueryManager` 运行时枚举与 stop API、`ui/StreamingQueryStatusListener` 周期快照。

### D5 容错语义（必答子项：micro-batch vs continuous 双模式取舍、状态存储与 checkpoint 产品化）— 2@high

#### D5.1 执行模式取舍（核心必答）

- **三种执行模式并存**（`Triggers.scala` + `execution/streaming/runtime/MicroBatchExecution.scala` + `execution/streaming/continuous/ContinuousExecution.scala`）：
  1. micro-batch（默认，`ProcessingTimeTrigger`/`AvailableNowTrigger`/`OneTimeTrigger`）——WAL 检查点 + 状态恢复的完全语义，全部算子支持
  2. continuous（`ContinuousTrigger` + `continuous/ContinuousExecution.scala`，epoch 纪元协调器 `EpochCoordinator.scala`）——低延迟实验路线，算子面窄（continuous/ 目录仅 rate/textsocket 数据源配套），长期保持 experimental
  3. **Real-time Mode（Spark 4.1.0 新方向，`RealTimeTrigger`）**——`docs/streaming/real-time-mode.md`：「targets ultra-low end-to-end latency…supports stateless queries — projections, filters, unions, stream-static joins — and, starting in Spark 4.3.0, a first set of stateful operations」；明确面向欺诈检测/实时告警等运营负载
- **取舍结论（源码级）**：Spark 未在 continuous 上继续加注，而是新开 Real-time Mode 演进路线；且 **AQE 与流式互斥边界显式管理**——`MicroBatchExecution.scala:651-659`：`RealTimeTrigger` 与 stateful 算子均强制 `ADAPTIVE_EXECUTION_ENABLED=false`（SPARK-53941「We disable AQE for stateful workloads as of now」）。这是「双模式取舍」的直接证据：低延迟模式与自适应优化目前不可兼得，产品以 micro-batch + 完整语义为主轴。
- 对 nop-stream 含义：nop-stream 的持续处理模型（非 micro-batch）对应 Spark 的 continuous/Real-time 路线；Spark 承认该路线状态算子支持是渐进的（4.3 才有首批），nop-stream 全量状态算子 + 连续模型是差异化优势而非缺陷（P-REQ 不产生对标缺口，仅记录）。

#### D5.2 checkpoint 产品化

- WAL 双日志体系：`execution/streaming/checkpointing/`——`OffsetSeqLog`(+`AsyncOffsetSeqLog`) 记录进度、`CommitLog`(+`AsyncCommitLog`) 记录批次完成（幂等去重依据）、`HDFSMetadataLog` 基类（批次文件 + compact 压缩）、`CheckpointFileManager`(+`Checksum` 变体) 保证原子写与校验和、`CheckpointVersionManager` 版本化（升级兼容）。
- 文件 sink exactly-once：`ManifestFileCommitProtocol.scala`（manifest 目录式提交协议，重启后按 commit log 对齐文件可见性）。
- 恢复模型：checkpoint 目录 + 同 query 重启即恢复（offset 回放 + 状态恢复）；**无分区/region 级 failover——整查询重启**（对照 nop-stream 已有 region-based failover 为优势项）。

#### D5.3 状态存储产品化

- 双后端：`state/HDFSBackedStateStoreProvider.scala`（默认，全量快照版本化）+ `state/RocksDBStateStoreProvider.scala`（`RocksDB.scala`/`RocksDBFileManager`/`RocksDBMemoryManager`/`RocksDBStateMachine`/`RocksDBStateEncoder`）。
- 增量演进：`StateStoreChangelog.scala`（changelog checkpointing）、RocksDB 专用配置键版本化（`SQLConf.scala:3781` `spark.sql.streaming.stateStore.rocksdb.formatVersion`、`:3797` `mergeOperatorVersion`）、provider 可插拔（`SQLConf.scala:3276` `STATE_STORE_PROVIDER_CLASS`）。
- **schema 演进**：`state/StateSchemaCompatibilityChecker.scala`（状态 schema 变更兼容性检查，配合 `SchemaHelper`）。
- **离线运维**：`state/OfflineStateRepartitionRunner.scala`（不停作业离线状态重分区/重 scale，`OfflineStateRepartitionUtils/Errors`）+ `docs/streaming/structured-streaming-state-data-source.md`（状态数据源读写文档——状态可导出检查）。
- 协调：`StateStoreCoordinator.scala`（driver 侧实例定位）+ `StateStoreRDD`。

### D6 性能（必答子项：adaptive query execution）— 3@high

- AQE 完整实现：`sql/core/.../execution/adaptive/`（28 文件）——`AdaptiveSparkPlanExec`（运行时重规划主体）、`AQEOptimizer`、`CoalesceShufflePartitions`（分区合并）、`OptimizeSkewedJoin`/`OptimizeSkewInRebalancePartitions`（倾斜 join 优化）、`DemoteBroadcastHashJoin`（运行时统计降级 join 策略）、`ConvertSortMergeJoinToShuffledHashJoin`、`LogicalQueryStage`（阶段化物化）、`OptimizeShuffleWithLocalRead`。
- 执行底座：whole-stage codegen（`execution/WholeStageCodegenExec.scala`、`WholeStageCodegenEvaluatorFactory.scala`）+ Tungsten 内存模型（catalyst expressions）。
- **流式边界（诚实记录）**：AQE 对流式查询的 stateful 与 Real-time 强制关闭（§D5.1 引 SPARK-53941）；即 AQE 产品价值主要在批内/无状态流。
- 调优文档：`docs/streaming/performance-tips.md`、`docs/tuning-guide.md`（仓库存在，未逐行核读——置信度内属源码级路径证据）。

### D7 文档（选答）— 3@high

- 主指南：`docs/structured-streaming-programming-guide.md`（概念/模型/操作/语义全景）。
- 专题集：`docs/streaming/` 10 份——`index/getting-started/apis-on-dataframes-and-datasets/additional-information/performance-tips/real-time-mode/ss-migration-guide/structured-streaming-kafka-integration/structured-streaming-transform-with-state/structured-streaming-state-data-source`。
- 运维：`docs/monitoring.md`（指标体系 + REST + Prometheus 端点表）。
- 迁移：`ss-migration-guide.md`（版本间行为变更）——版本化文档实践。

## 必答维度结论映射（roadmap item 3 stage details 5 项 ↔ 本报告章节）

| roadmap item 3 必答项 | 本报告章节 | 结论摘要 |
|----------------------|-----------|---------|
| 1. micro-batch vs continuous 双模式的取舍 | §D5.1 | 三模式并存（micro-batch 主轴 + continuous 实验 + Real-time 4.1 新路线）；AQE 与 stateful/Real-time 互斥（SPARK-53941 源码级） |
| 2. adaptive query execution | §D6 | AQE 28 文件完整实现（倾斜/分区/ join 策略运行时优化）；流式 stateful 边界显式管理 |
| 3. 状态存储与 checkpoint 产品化 | §D5.2/§D5.3 | WAL 双日志（offset/commit，异步变体）+ 双状态后端（HDFS/RocksDB）+ schema 兼容检查 + 离线 repartition + 状态数据源 |
| 4. Structured Streaming API 设计 | §D1 | 无界表增量模型 + 5 态触发器 + transformWithState 类型化状态（Value/List/Map/TTL） |
| 5. 运维/监控集成 | §D4 | Listener 进度标准集 + Web UI Streaming tab + Prometheus servlet + metrics 模板 + 异步进度追踪 |

## P-REQ 候选条目（SPS-，供 item 5 汇编）

> 字段：要求陈述 / 可判定验收标准 / 源码证据指针（相对 `~/sources/spark`，锚定 SHA `992b0905`）/ 建议归属

- **SPS-1 流式进度指标标准集**
  - 要求：定义并暴露流作业进度标准指标（吞吐输入/处理速率、状态算子行数与时长、watermark、批次耗时分解）
  - 验收：指标枚举类/注册表落码且每指标有单测或 e2e 断言；文档列出全部指标名与语义
  - 证据：`sql/core/.../runtime/ProgressReporter.scala:355-394,625-663`、`sql/api/.../streaming/StreamingQueryListener.scala`
  - 归属：item 16
- **SPS-2 作业进度事件监听 API**
  - 要求：提供流作业生命周期事件（started/progress/terminated 等价物）的可注册监听接口
  - 验收：接口 + 至少一个内建监听器（UI/日志用）落码；第三方可扩展（SPI/注册式）
  - 证据：`sql/api/.../streaming/StreamingQueryListener.scala`、`runtime/StreamingQueryListenerBus.scala`
  - 归属：item 16
- **SPS-3 Web UI 流式页签**
  - 要求：提供（或裁定排除）作业列表 + 输入/处理速率 + 批延迟时序的 UI 页面
  - 验收：item 16 产出交付/排除裁定；若交付含 active/finished 查询列表与速率时序图
  - 证据：`sql/core/.../streaming/ui/{StreamingQueryTab,StreamingQueryPage,StreamingQueryStatisticsPage}.scala`
  - 归属：item 16（与 ST-7 同族，item 5 合并裁定）
- **SPS-4 metrics 配置模板开箱**
  - 要求：提供 metrics source/sink 配置模板文件（含常用 sink 注释样例），降低接入成本
  - 验收：模板文件随发行包/资源目录存在，覆盖 ≥3 类 sink 样例
  - 证据：`conf/metrics.properties.template`（22 项配置）
  - 归属：item 16
- **SPS-5 状态 schema 演进兼容检查**
  - 要求：状态存储 schema 变更时提供兼容性检查（不兼容即 fail-fast 并报告差异）
  - 验收：检查器类 + 不兼容场景测试用例（变更字段类型断言报错）
  - 证据：`state/StateSchemaCompatibilityChecker.scala`、`state/SchemaHelper.scala`
  - 归属：item 11（rocksdb/状态后端审计）
- **SPS-6 离线状态重分区工具对照**
  - 要求：核对 nop-stream 既有离线 reshard 工具与 Spark OfflineStateRepartitionRunner 的能力面（多分区策略/校验/错误报告），缺口转 D-GAP
  - 验收：item 11 审计报告含对照表（功能逐项 ✓/✗）
  - 证据：`state/OfflineStateRepartitionRunner.scala`、`OfflineStateRepartitionUtils.scala`
  - 归属：item 11（nop-stream 已有等价物，属核对型候选）
- **SPS-7 checkpoint 版本化与校验和**
  - 要求：checkpoint 存储格式带版本管理（升级兼容）与写入校验和/原子性保证
  - 验收：版本管理器 + 原子写/校验实现存在且有故障注入测试（部分写后重启可恢复）
  - 证据：`checkpointing/CheckpointVersionManager.scala`、`ChecksumCheckpointFileManager.scala`、`CheckpointFileManager.scala`
  - 归属：item 6（对照 nop-stream `ICheckpointStorage` 既有 LocalFile/JDBC 实现裁定增量）
- **SPS-8 触发器模型一等化**
  - 要求：触发语义（固定间隔/可用即批/一次性等）成为作业 API 的一等参数并文档化
  - 验收：触发器枚举/类族落码 + 每种触发语义至少一个测试；文档章节存在
  - 证据：`sql/api/.../execution/streaming/Triggers.scala:57-125`（5 态）
  - 归属：item 6（裁定 nop-stream 现有 trigger 语义覆盖面）
- **SPS-9 版本化迁移指南实践**
  - 要求：流作业/状态/checkpoint 的版本间行为变更维护迁移指南文档
  - 验收：`docs-for-ai/` 或设计文档存在迁移指南条目，首个版本覆盖 XDSL 与状态格式变更
  - 证据：`docs/streaming/ss-migration-guide.md`、`docs/streaming/structured-streaming-state-data-source.md`
  - 归属：item 17

## Conclusion

- Spark SS 的产品化启示对 nop-stream 集中在 **运维可观测（SPS-1/2/3/4）与状态产品化（SPS-5/6/7）** 两组；API 模型（DataFrame 增量统一）与 nop-stream 的 DataStream+XDSL 路线不同构，不产生直接对标 P-REQ（除触发器语义核对 SPS-8）。
- 明确的**反向结论（nop-stream 优势项，不设 P-REQ）**：① 分区级 failover/region recovery（Spark 仅整查询重启）；② AQE 与流式互斥 vs nop-stream 连续模型全量状态算子；③ 连接器生态组织（Spark 仓库内窄面）。这三项记录为 item 5 综合矩阵的对比素材。
- 被否决的方案：深挖 catalyst 优化器/shuffle 全链（否决原因：超出流处理产品化 scope，矩阵证据已足）；引入 Spark 风格 DataFrame 层（否决原因：路线不同构，nop-stream P-REQ 只取运维/状态实践）。
- 后续工作：本报告 9 条 SPS- 候选由 item 5（plan `2026-09-01-0753-3`）汇编。

## References

- `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md`（评估矩阵与置信度口径）
- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 3/16/17/11/6）
- 源码：`~/sources/spark@992b0905fa72d1d393936ff827e9c26514013964`（正文内相对路径）
