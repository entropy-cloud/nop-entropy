# Kafka Streams 产品化分析（roadmap item 4）

> Status: resolved
> Date: 2026-09-01
> Scope: Apache Kafka master @ `7434a60c9bafde4f0bcde30b608092bb6449d472`（`git -C ~/sources/kafka rev-parse HEAD`，`--depth 1` shallow clone，clone 于 2026-09-01，116MB；经历网络间歇失败后重试成功）× plan 1 评估矩阵 7 维度，分析范围以 `streams/` 子模块为主（broker/storage 不深挖，plan Non-Goals）；nop-stream 产品化 P-REQ 候选（`KS-` 前缀）
> Conclusion: Kafka Streams 是「库形态」产品化标杆：容错 3@high（exactly_once_v2 事务集成 + changelog 状态恢复 + StandbyTask 热备 + 可插拔异常策略）、API/DX 3@high（DSL/Processor 双 API + TopologyTestDriver + archetype 脚手架）、文档 3@high（7+18 份指南）；连接器生态 1@high（Kafka 绑定，by design）、部署 2@high（库形态无自有集群面）、运维监控 2@high（五级指标 + JMX + 重置工具，无 UI/REST——库形态把运维责任倒推给宿主应用）。产出 9 条 `KS-` 候选，建议归属 items 16/6/17。后续工作由 item 5 汇编。
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 4；plan `ai-dev/plans/nop-stream-productization/2026-09-01-0753-2-competitor-source-productization-analysis.md` Phase 3；评估矩阵 `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md` §2

## Context

- roadmap item 4 要求获取 Kafka 源码（streams 子模块为主）并按 plan 1 矩阵做产品化分析；核心命题是**库形态 vs 引擎形态的运维差异倒推**——nop-stream 当前是引擎形态（JobCoordinator/TaskManagerMain/MiniStreamCluster），Kafka Streams 是嵌入式库形态，其产品化取舍对 nop-stream 的运维边界设计有直接参照价值。
- 既有 `~/sources` 与 `ai-dev/analysis/` 无 Kafka Streams 报告（plan 1 §2.4 全维度缺口确认），本报告为首份。
- 所有源码证据锚定上述 commit SHA；clone 过程遇 GitHub 间歇连接失败（curl 通而 git 间歇超时），8 次重试循环内第 1 轮恢复成功——非 blocker，已克服。

## 评分总表（plan 1 矩阵 §2.2 口径：分数@置信度）

| 维度 | 评分 | 一句话判定 |
|------|------|-----------|
| D1 API/DX | 3@high | DSL + Processor 双 API + Scala 门面 + TopologyTestDriver 测试基建 + maven archetype 脚手架 |
| D2 连接器生态 | 1@high | 仅 Kafka topic 单一 source/sink（库形态设计使然，非缺陷但维度分实然为 1） |
| D3 部署形态 | 2@high | 库嵌入 = 宿主应用进程；水平扩展靠实例副本 + rebalance 协议；本地 state.dir + 进程文件锁；无自有集群/K8s 工具（by design） |
| D4 运维监控 | 2@high | 五级指标体系 + RocksDB 专用 recorder + JMX 默认暴露 + 应用重置工具；无 UI/REST（倒推给宿主） |
| D5 容错语义 | 3@high | exactly_once_v2 事务集成 + changelog 恢复（10 变体）+ StandbyTask 热备 + IQ Position 一致性边界 + 可插拔异常策略 |
| D6 性能 | 2@high | 读缓存 + 合并迭代器 + RocksDBConfigSetter 调优钩子 + memory-mgmt 指南；无官方基准数据 |
| D7 文档 | 3@high | docs/streams 7 份顶层 + developer-guide 18 份（含 app-reset-tool/manage-topics/memory-mgmt/rebalance-protocol） |

## Analysis

### D1 API/DX（选答）— 3@high

1. **双 API 体系**：高层 DSL（`streams/src/main/java/org/apache/kafka/streams/kstream/`——KStream/KTable/join/aggregate/windowing）+ 低层 Processor API（`processor/`）；Scala 门面独立模块 `streams/streams-scala/`。
2. **测试基建（标杆项）**：`streams/test-utils/` 模块的 TopologyTestDriver——拓扑级离线单测（不依赖 broker），DX 价值极高。
3. **脚手架**：maven archetype（`streams/quickstart/java/src/main/resources/archetype-resources/`——Pipe/LineSplit/WordCount 三模板）。
4. **示例**：`streams/examples/` 独立模块。
5. **拓扑自描述**：Topology 描述输出（`topology-description-plugin.md` 文档化插件接口）。
6. 类型/序列化透明度：Serde 抽象贯穿（state/StateSerdes.java）。

### D2 连接器生态（选答）— 1@high

- Source/Sink = Kafka topic（`streams/src/main/java/org/apache/kafka/streams/` 全部 IO 面向 Kafka client）；无连接器目录/SPI/能力矩阵。
- 评分说明：矩阵按「官方连接器数量与主流数据源覆盖」检查点实然打 1；这是库形态的**设计选择**（Kafka 生态自身即连接层，外部系统经 Kafka Connect 桥接），记录为形态差异而非产品化缺口。

### D3 部署形态（必答子项：库形态 vs 引擎形态——核心命题）— 2@high

1. **库嵌入模型**：`KafkaStreams` 对象随宿主应用进程生命周期（`KafkaStreams.java` close/shutdown 钩子；`CloseOptions` 优雅关闭参数）；无 master/worker、无独立守护进程、无 spark-submit 等价物。
2. **水平扩展 = 实例副本 + 再均衡**：多实例同 `application.id` 组成逻辑集群，分区分配经 rebalance 协议（文档 `docs/streams/developer-guide/streams-rebalance-protocol.md` 专文）。
3. **本地状态管理**：`processor/internals/StateDirectory.java:89-151`——state.dir 根目录 + 应用子目录 + 进程文件（`StateDirectoryProcessFile`）做进程级状态目录锁。
4. **升级兼容矩阵（产品化亮点）**：`streams/upgrade-system-tests-0110/…/43`——**26 个跨版本升级系统测试模块**，保证旧版本状态/拓扑在新版本下的升级路径。
5. 运维责任边界：进程守护/K8s 编排/伸缩全部由宿主承担（引擎形态产品内建的 deploy 面，库形态显式让渡）。

### D4 运维监控（必答子项：liveness/健康暴露、运维模型倒推）— 2@high

1. **Liveness = 状态机 + 监听器**：`KafkaStreams.java:263-272` 七态状态机（CREATED/REBALANCING/RUNNING/PENDING_SHUTDOWN/NOT_RUNNING/PENDING_ERROR/ERROR），可注册 StateListener 观察迁移；`LagInfo.java` 暴露消费滞后（输入积压 = 库形态最关键的 liveness 信号）。
2. **五级指标体系**：`processor/internals/metrics/`——ThreadMetrics/TaskMetrics/ProcessorNodeMetrics/TopicMetrics/RebalanceListenerMetrics（`StreamsMetricsImpl` 统一实现，JMX 默认暴露，recording.level 分级：info/debug）。
3. **状态后端专用指标**：`state/internals/metrics/RocksDBMetricsRecorder.java`（RocksDB 内部统计：block cache/memtable/compaction 等）。
4. **运维工具**：`tools/src/main/java/org/apache/kafka/tools/StreamsResetter.java`（应用状态重置 CLI：清理 state.dir + 重置 offset，配套文档 `docs/streams/developer-guide/app-reset-tool.md`）。
5. **运维模型倒推结论（核心命题回答）**：库形态把「进程健康/重启/伸缩」倒推给宿主运行时（K8s liveness probe 由宿主暴露），自己只负责**逻辑健康**（状态机 + lag + 指标）。对照引擎形态（nop-stream/Flink/SeaTunnel/Spark）：后者必须内建进程编排 + REST + UI。nop-stream 作为引擎形态，KS- 候选只取「逻辑健康信号面」（KS-1/KS-2），不取进程编排项。

### D5 容错语义（必答子项：事务性 exactly-once 集成、状态存储 RocksDB 内嵌、interactive query 状态一致性）— 3@high

1. **事务性 exactly-once 集成**：`StreamsConfig.java:421` `EXACTLY_ONCE_V2 = "exactly_once_v2"`（`:1071` 校验 ∈ {AT_LEAST_ONCE, EXACTLY_ONCE_V2}；`:488` EOS 下默认 commit interval 收紧）——实现走 Kafka 事务生产者（read_committed 消费 + 事务提交 offset），端到端 EOS 语义**限定在 Kafka 进出**（这是库形态的语义边界：非 Kafka sink 无 EOS 承诺）。
2. **状态存储 RocksDB 内嵌**：
   - 可插拔后端：`Stores.java` + `KeyValueBytesStoreSupplier`/`SessionBytesStoreSupplier`/窗口 supplier 家族；默认 RocksDB（`state/internals/RocksDBStore.java`）+ 内存实现。
   - 用户调优钩子：`state/RocksDBConfigSetter.java`（用户自定义 RocksDB Options 注入点）。
   - **版本化状态**：`state/internals/RocksDBVersionedStore.java`（+ `RocksDbVersionedKeyValueBytesStoreSupplier`、`RestoreWriteBuffer`、`SegmentValueFormatter`）——按版本保留状态的存储类型。
   - **恢复机制**：changelog topic——`state/internals/ChangeLogging*BytesStore.java` 10 个变体（KeyValue/Timestamped/Session/Window/Versioned × headers 变体），写入即打 changelog，恢复 = 重放。
   - **热备**：`processor/internals/StandbyTask.java`——standby 任务持续追 changelog，failover 时秒级接管。
3. **interactive query 状态一致性**：IQv2 查询 API（`streams/src/main/java/org/apache/kafka/streams/query/` 12 种 Query 类型：KeyQuery/RangeQuery/WindowKeyQuery/`VersionedKeyQuery`/`MultiVersionedKeyQuery` 等）；一致性由 **Position 边界**保证——`query/Position.java` + `PositionBound.java` + `StateQueryRequest`（查询可指定位置下界，拒绝落后于指定 offset 的副本返回旧数据）；查询目标路由 `KeyQueryMetadata.java`（按分区定位持有实例）。
4. **可插拔异常策略**：`errors/`——`DeserializationExceptionHandler`（`LogAndContinueExceptionHandler`/`LogAndFailExceptionHandler`）+ `ProductionExceptionHandler` + `InvalidStateStoreException`（IQ 在 rebalance 期间的显式失败而非静默错误数据）。

### D6 性能（选答）— 2@high

- 读缓存与合并迭代：`state/internals/MergedSortedCache*Iterator.java` 5 变体（cache + store 归并读）；`Metered*Store` 计量包装。
- 调优产品化：RocksDBConfigSetter（见 D5）+ commit interval 语义（EOS 默认 100ms vs AL 默认 30s，`StreamsConfig.java:488`）。
- 指南：`docs/streams/developer-guide/memory-mgmt.md`（内存模型专文）。
- 无官方基准数据 → 未达 3。

### D7 文档（选答）— 3@high

- 顶层 7 份：`docs/streams/{introduction,core-concepts,architecture,quickstart,tutorial,upgrade-guide,_index}.md`。
- developer-guide 18 份：write-streams-app/dsl-api/processor-api/datatypes/interactive-queries/**app-reset-tool**/manage-topics/**memory-mgmt**/config-streams/**streams-rebalance-protocol**/testing/running-app/security/scala-migration/topology-description-plugin/dsl-topology-naming/kafka-streams-group-sh。
- 覆盖「概念 → 开发 → 测试 → 运维 → 升级」全链路；升级指南 + 26 版本 upgrade-system-tests 与文档互相印证。

## 必答维度结论映射（roadmap item 4 stage details 5 项 ↔ 本报告章节）

| roadmap item 4 必答项 | 本报告章节 | 结论摘要 |
|----------------------|-----------|---------|
| 1. 库形态 vs 引擎形态的运维差异 | §D3 + §D4 第 5 点 | 库形态让渡进程编排给宿主，只内建逻辑健康（状态机/lag/指标）；引擎形态须全栈自建——nop-stream 取「逻辑健康信号面」不取进程编排 |
| 2. 事务性 exactly-once 集成 | §D5.1 | exactly_once_v2 = Kafka 事务生产者 + read_committed + 事务化 offset 提交；EOS 边界限定 Kafka 进出 |
| 3. 状态存储 RocksDB 内嵌 | §D5.2 | 默认 RocksDB + supplier 可插拔 + ConfigSetter 调优钩子 + 版本化 store + changelog 恢复 + StandbyTask 热备 |
| 4. interactive query | §D5.3 | IQv2 12 种 Query 类型 + Position/PositionBound 一致性边界 + KeyQueryMetadata 路由 |
| 5. liveness/健康暴露 | §D4.1 | 七态状态机 + StateListener + LagInfo + 五级 JMX 指标；无 REST/UI（倒推宿主） |

## P-REQ 候选条目（KS-，供 item 5 汇编）

> 字段：要求陈述 / 可判定验收标准 / 源码证据指针（相对 `~/sources/kafka`，锚定 SHA `7434a60c`）/ 建议归属

- **KS-1 流作业逻辑健康状态机**
  - 要求：引擎侧暴露作业/实例级逻辑健康状态机（含 REBALANCING/PENDING_ERROR 等中间态等价物）与可注册状态监听器
  - 验收：状态枚举 + 迁移合法性表落码且有单测；监听器接口存在并在 MiniStreamCluster e2e 中被断言调用
  - 证据：`streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java:263-272`（七态 + 迁移注释）
  - 归属：item 16
- **KS-2 分层指标模型（thread/task/算子/topic/store 五级）**
  - 要求：nop-stream metrics 暴露按层级组织（引擎线程/任务/算子/输入输出/状态后端各一组标准指标名）
  - 验收：指标命名分层规范文档化；每层至少 3 个指标有注册与单测；与 SPS-1 进度指标集合并裁定
  - 证据：`streams/.../processor/internals/metrics/{ThreadMetrics,TaskMetrics,ProcessorNodeMetrics,TopicMetrics,RebalanceListenerMetrics}.java` + `StreamsMetricsImpl.java`
  - 归属：item 16
- **KS-3 状态后端专用指标 recorder**
  - 要求：RocksDB 状态后端暴露内部统计（block cache/memtable/compaction 等级）
  - 验收：recorder 类 + 指标注册存在；e2e 或单测断言非空读数
  - 证据：`streams/.../state/internals/metrics/RocksDBMetricsRecorder.java`
  - 归属：item 16（或并入 item 11 rocksdb 审计范围）
- **KS-4 可插拔异常处理策略**
  - 要求：反序列化/处理/写出异常的策略可声明式选择（跳过并记录 vs 快速失败），默认 fail-fast
  - 验收：策略接口 + ≥2 内建实现 + 每实现一个行为测试；配置键文档化
  - 证据：`streams/.../errors/{DeserializationExceptionHandler,LogAndContinueExceptionHandler,LogAndFailExceptionHandler,ProductionExceptionHandler}.java`
  - 归属：item 6（对照 nop-stream 现有错误处理语义裁定增量）
- **KS-5 Standby 热备副本裁定输入**
  - 要求：以 Kafka Streams StandbyTask 为参照，裁定 nop-stream 是否引入状态热备副本（缩短 failover 状态恢复窗口）
  - 验收：item 6 的 D-GAP 清单含 standby 条目且 go/defer/exclude 三态裁定 + 依据
  - 证据：`streams/.../processor/internals/StandbyTask.java` + changelog 恢复（`state/internals/ChangeLogging*BytesStore.java` 10 变体）
  - 归属：item 6
- **KS-6 IQ 一致性边界（Position bound）裁定输入**
  - 要求：若 item 6 裁定 nop-stream 引入查询接口，须采用位置边界式一致性（查询拒绝落后数据）；否则显式 exclude
  - 验收：D-GAP 清单含 IQ 条目与三态裁定；若 go，则查询 API 带 position/bound 参数并有测试
  - 证据：`streams/src/main/java/org/apache/kafka/streams/query/{Position,PositionBound,StateQueryRequest}.java`
  - 归属：item 6
- **KS-7 作业状态重置工具**
  - 要求：提供作业/应用级状态重置 CLI（清理本地状态 + 重置输入位点），支持全新重跑
  - 验收：工具类 + 手册章节存在；e2e 演示重置后从起点正确重放
  - 证据：`tools/src/main/java/org/apache/kafka/tools/StreamsResetter.java` + `docs/streams/developer-guide/app-reset-tool.md`
  - 归属：item 16
- **KS-8 跨版本升级兼容测试基建**
  - 要求：建立状态格式/checkpoint 跨版本升级的系统测试（旧版本产物 → 新版本恢复）
  - 验收：至少 1 条升级路径的自动化测试存在（或 D-GAP 显式裁定 defer 并说明版本策略）
  - 证据：`streams/upgrade-system-tests-0110 … 43`（26 个版本模块）
  - 归属：item 6（裁定）→ 执行可落 item 14/15
- **KS-9 脚手架 archetype**
  - 要求：提供 nop-stream 快速起步工程脚手架（maven archetype 或等价模板 + 3 个入门示例拓扑）
  - 验收：脚手架生成工程可 `mvn test` 通过；示例含 source→transform→sink 最小链路
  - 证据：`streams/quickstart/java/src/main/resources/archetype-resources/`（Pipe/LineSplit/WordCount）
  - 归属：item 17

## Conclusion

- Kafka Streams 的产品化启示对 nop-stream 是**运维信号面（KS-1/2/3/7）与容错策略面（KS-4/5/8）**；其库形态本身不是 nop-stream 的对齐目标（引擎形态已定），但「逻辑健康 vs 进程健康」的责任分界是 item 16 设计运维 API 时的直接参照。
- 明确**不取的实践**：单连接器绑定（by design 不适用）；进程编排让渡（引擎形态须自建）；Kafka 事务型 EOS（nop-stream 的 2PC sink 框架已覆盖非 Kafka sink 的 EOS，语义面更宽——记录为 item 5 对比素材）。
- 被否决的方案：深挖 broker/storage（plan Non-Goals）；将 CONN 1@high 解读为产品化缺陷（否决原因：库形态设计选择，评分按矩阵实然口径并显式标注语境）。
- 后续工作：本报告 9 条 KS- 候选由 item 5（plan `2026-09-01-0753-3`）汇编。

## References

- `ai-dev/analysis/2026-09/2026-09-01-research-asset-inventory-and-evaluation-framework.md`（评估矩阵与置信度口径）
- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 4/16/6/17）
- 源码：`~/sources/kafka@7434a60c9bafde4f0bcde30b608092bb6449d472`（正文内相对路径）
