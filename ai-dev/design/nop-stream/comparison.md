# nop-stream 与 Flink / SeaTunnel 对比分析

> Status: active
> Created: 2026-05-19

## 1. 设计定位

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| **定位** | 通用分布式流处理引擎 | 分布式数据集成平台（ETL） | 简化流处理引擎 |
| **规模** | PB 级、集群部署 | 大规模 ETL 管道 | 单 JVM、中小规模 |
| **复杂度** | 极高（分布式一致性） | 高（Connector 生态） | 低（单线程同步） |
| **适用场景** | 实时数仓、CEP、流式分析 | 数据同步、ETL 管道 | 单流 ETL、CEP、窗口聚合 |

**设计决策**：nop-stream 不是 Flink 的替代品，而是针对特定场景的简化方案。目标是 20% 的功能覆盖 80% 的常用场景。

## 2. 架构对比

### 2.1 执行模型

| 维度 | Flink | nop-stream |
|------|-------|------------|
| 调度 | JobManager + TaskManager 分布式调度 | 单 JVM 内链式执行 |
| 并行 | 多 TaskManager 并行处理 | 单线程顺序处理（TaskExecutor 未对接） |
| 通信 | Netty RPC，RecordWriter/RecordReader | ChainingOutput 直接方法调用 |
| 部署 | YARN/K8s/Standalone | 嵌入式（作为库使用） |
| Checkpoint | 分布式 barrier 对齐 + 状态快照 | CheckpointCoordinator 存在但未对接 |
| 故障恢复 | 基于 checkpoint 的 exactly-once | 无（单 JVM 崩溃即丢失） |

### 2.2 API 层

| 维度 | Flink | nop-stream |
|------|-------|------------|
| DataStream API | ✅ 完整 | ✅ 子集（map/filter/flatMap/keyBy/sink） |
| 窗口 API | ✅ 完整（含 session window） | ✅ 部分实现（tumbling/sliding/count） |
| 状态 API | ✅ Value/List/Map/Reducing/Aggregating | ✅ Value/List/Map（仅内存实现） |
| CEP API | ✅ flink-cep 独立模块 | ✅ 直接从 flink-cep 剥离 |
| SQL API | ✅ Table API + SQL | ❌ 无 |
| Connectors | ✅ Kafka/JDBC/ES/... 生态丰富 | ❌ 无（仅有 Collection source / Print sink） |
| TwoInputStreamOperator | ✅ 支持双流 Join | ❌ 未实现 |

### 2.3 状态管理

| 维度 | Flink | nop-stream |
|------|-------|------------|
| Keyed State | 按 key-group 分区，支持重分布 | 按 (key, namespace) 分区，仅内存 |
| Operator State | 独立的状态后端 | 未实现 |
| State Backend | Memory/FS/RocksDB 多种 | 仅 Memory |
| 状态 TTL | ✅ 支持 | ❌ |
| 状态查询 | ✅ Queryable State | ❌ |

### 2.4 时间语义

| 维度 | Flink | nop-stream |
|------|-------|------------|
| 事件时间 | ✅ watermark 机制完整 | ⚠️ 接口存在但 watermark 传播未实现 |
| 处理时间 | ✅ | ✅ |
| Watermark 生成 | ✅ 多种策略（BoundedOutOfOrderness/单调/自定义） | ⚠️ TimestampsAndWatermarksOperator 存在但未接入 |
| 允许迟到 | ✅ allowedLateness | ✅ WindowOperator 支持 |

## 3. 与 SeaTunnel 的关系

### 3.1 README 中的定位

nop-stream 的 README 明确说明：

> 准备吸收部分 Apache SeaTunnel 项目的源码，将其改造为相对通用的、可用于一般性流处理的引擎，提供一个简易实现，同时提供一个 Flink 实现。

### 3.2 SeaTunnel 的核心模型

SeaTunnel 的核心是 Connector SPI：

| 概念 | SeaTunnel | nop-stream 对应 |
|------|-----------|-----------------|
| Source | SeaTunnelSource（有界/无界） | SourceFunction（仅无界） |
| Sink | SeaTunnelSink | SinkFunction |
| Transform | SeaTunnelTransform | MapFunction/FlatMapFunction |
| Connector | SPI 插件体系 | ❌ 无 Connector 概念 |
| Type System | SeaTunnelType（行类型） | TypeInformation（泛型） |
| Checkpoint | 支持 exactly-once | 未实现 |

### 3.3 nop-stream 相对于 SeaTunnel 的简化

| 能力 | SeaTunnel | nop-stream |
|------|-----------|------------|
| 多源 Join | ✅ | ❌ 仅单流处理 |
| Schema 管理 | ✅ 行类型系统 | ❌ 泛型 + TypeInformation |
| Connector 生态 | ✅ 100+ 连接器 | ❌ 无 |
| 批流统一 | ✅ Batch + Streaming | 仅 Streaming |
| 声明式配置 | ✅ HOCON 配置文件 | ❌ 纯 Java API |
| 引擎适配 | ✅ Flink/Spark/Zeta | 规划中（nop-stream-flink） |

### 3.4 nop-stream 的独特价值定位

nop-stream 不是 Flink/SeaTunnel 的替代品，而是在不同维度上的取舍：

| 场景 | 选 Flink/SeaTunnel | 选 nop-stream |
|------|--------------------|----|
| 大规模数据同步（TB+） | ✅ 分布式并行 | ❌ |
| 需要丰富 Connector 生态 | ✅ Kafka/JDBC/ES... | ❌ 仅 Collection Source |
| 嵌入式事件模式识别 | ❌ 需独立集群 | ✅ 作为库直接嵌入 |
| CEP 欺诈/异常检测 | ❌ SeaTunnel 不支持 | ✅ NFA + SharedBuffer |
| 与 Nop 生态深度集成 | ❌ | ✅ 未来通过 nop-stream-flow 与 NopFlow 编排 |
| 零部署、零配置启动 | ❌ 需部署集群 | ✅ `new StreamExecutionEnvironment()` 即可 |

**核心判断**：如果需要分布式处理能力，直接用 Flink。nop-stream 解决的是"我只需要在应用内做窗口聚合或事件模式匹配，不想引入一个流处理集群"这个需求。

## 4. 简化决策总结

### 4.1 保留的核心概念

以下概念与 Flink 一致，是 nop-stream 的核心抽象：

| 概念 | 保留原因 |
|------|---------|
| DataStream API | 业界标准，学习成本低 |
| Transformation DAG | 流处理程序的基本表示 |
| KeyedStream + keyBy | 有状态计算的基础 |
| Window（Assigner/Trigger/Evictor） | 窗口聚合是核心需求 |
| StreamOperator 生命周期 | 算子开发的规范 |
| 状态后端抽象 | 支持不同存储实现 |
| CEP（NFA + SharedBuffer） | 最成熟的子模块，独立可用 |

### 4.2 去除的复杂性

| 去除的 Flink 概念 | 原因 |
|---|---|
| JobManager / TaskManager | 单 JVM 不需要分布式调度 |
| Netty RPC | 单线程不需要网络通信 |
| Key-group 分区 | 不需要 key 重分布 |
| Slot sharing / slot sharing group | 不需要资源隔离 |
| Checkpoint barrier 对齐 | 不需要分布式一致性（虽然代码存在） |
| StreamTask / MailboxThread | 单线程不需要线程模型 |
| OperatorChain / ChainingStrategy.ALWAYS | 始终链化，无需策略选择 |
| TypeExtractor 自动类型推断 | 简化为 UnknownTypeInformation |
| Side Output | 简化，通过 flatMap 替代 |

### 4.3 规划但未实现的能力

| 能力 | 规划模块 | 目标 |
|------|---------|------|
| Flink 后端适配 | nop-stream-flink | nop-stream API → Flink DataStream |
| 声明式编排 | nop-stream-flow | XDSL 定义流处理管道 |
| 公共 API 抽取 | nop-stream-api | 接口与实现解耦 |
| 完整 Checkpoint | nop-stream-checkpoint | 状态持久化 + 故障恢复 |
| 双流 Join | — | hash join、interval join |
| ORM 集成 | — | 基于 nop-orm 的 lookup join |

## 5. 能力边界

### 5.1 nop-stream 能做什么

- ✅ 单流 ETL：map / filter / flatMap
- ✅ 窗口聚合：tumbling / sliding / count window
- ✅ CEP 模式匹配：基于 NFA 的复杂事件处理
- ✅ 欺诈检测/异常检测等事件模式识别场景
- ✅ 嵌入式使用：作为 Java 库在应用内运行

### 5.2 nop-stream 不能做什么（当前）

- ❌ 分布式并行处理
- ❌ 多流 Join
- ❌ Exactly-once 语义
- ❌ 故障恢复
- ❌ 完整的事件时间语义（watermark 未实现）
- ❌ 大规模数据处理
- ❌ Connector 生态（Kafka/JDBC/ES 等）
