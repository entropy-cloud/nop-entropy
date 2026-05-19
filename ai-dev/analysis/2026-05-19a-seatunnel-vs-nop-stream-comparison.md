# SeaTunnel vs nop-stream 架构与功能深度对比分析

> Status: open
> Date: 2026-05-19
> Scope: Apache SeaTunnel（分布式数据集成平台）vs nop-stream（Nop 平台流处理引擎）+ nop-batch（Nop 平台批处理引擎）
> Conclusion: TBD

## Context

nop-stream 的 README 明确说明要"吸收部分 Apache SeaTunnel 项目的源码，将其改造为相对通用的、可用于一般性流处理的引擎"。本分析从源码和官方文档两个层面展开深度对比，回答以下问题：

1. SeaTunnel 的核心架构是什么？它的功能边界在哪里？
2. nop-stream 当前覆盖了 SeaTunnel 的哪些能力？还缺什么？
3. 如果要用 Nop 平台实现 SeaTunnel 的核心 ETL 功能，应该怎么做？

---

## 第一部分：SeaTunnel 架构总览

### 1.1 模块结构

```
seatunnel/
├── seatunnel-api/              核心 API 定义
│   ├── Source/Sink/Transform   三大抽象
│   ├── Table/Schema/DataType   类型系统
│   └── Connector SPI           连接器加载机制
├── seatunnel-engine/           自研执行引擎（Zeta）
│   ├── seatunnel-engine-common
│   ├── seatunnel-engine-client
│   ├── seatunnel-engine-server
│   └── seatunnel-engine-core
├── seatunnel-connectors-v2/    连接器实现（100+）
├── seatunnel-transforms-v2/    转换插件
├── seatunnel-e2e/              端到端测试
└── seatunnel-shade/            依赖隔离
```

### 1.2 核心抽象

SeaTunnel 的三大核心抽象构成了完整的 ETL 管道模型：

| 抽象 | 接口 | 职责 | 生命周期 |
|------|------|------|---------|
| **Source** | `SeaTunnelSource<T, SplitT, StateT>` | 数据读取 | createReader / createSplitEnumerator |
| **Transform** | `SeaTunnelTransform` | 数据转换 | map/flatMap |
| **Sink** | `SeaTunnelSink<T, StateT, CommitT, AggCommitT>` | 数据写出 | createWriter / createCommitter |

Source 进一步拆分为：
- **SourceSplitEnumerator**：负责发现和分配数据分片（split），类似 Flink 的 SourceCoordinator
- **SourceReader**：负责读取具体分片的数据

Sink 进一步拆分为：
- **SinkWriter**：负责写入单条/批量数据
- **SinkCommitter**：负责事务提交（实现 exactly-once）

### 1.3 类型系统

SeaTunnel 定义了独立于引擎的类型系统：

| 概念 | 类 | 说明 |
|------|-----|------|
| 行类型 | `SeaTunnelRow` | 类似数据库行，有序字段 |
| 数据类型 | `SeaTunnelDataType` | 基本类型 + 数组 + Map + Row |
| 表 schema | `TableSchemaInfo` | 字段列表 + 主键 + 分区信息 |
| Catalog | `Catalog` | 数据源元数据（表、库、列） |

### 1.4 Zeta 执行引擎

Zeta 是 SeaTunnel 自研的执行引擎（不依赖 Flink/Spark）：

| 维度 | 实现方式 |
|------|---------|
| 调度 | Master/Worker 模型，支持混合部署和分离部署 |
| 并行度 | 由 SourceSplitEnumerator 决定 split 数，每个 split 分配给一个 Reader |
| 数据交换 | 基于 SeaTunnel 的自定义数据传输层，支持内存和网络 |
| Checkpoint | 基于 barrier 对齐，支持 exactly-once |
| 资源管理 | Slot 机制，类似 Flink 的 slot 分配 |
| 管道编排 | DAG 执行，从 HOCON 配置构建 Pipeline |

### 1.5 配置方式

SeaTunnel 使用 HOCON 格式声明式定义管道：

```
env {
  execution.parallelism = 4
  job.mode = "STREAMING"
}

source {
  Kafka {
    topic = "user_events"
    bootstrap.servers = "localhost:9092"
    result_table_name = "user_events"
    schema = {
      fields {
        user_id = "bigint"
        event_type = "string"
        event_time = "timestamp"
      }
    }
  }
}

transform {
  Sql {
    source_table_name = "user_events"
    result_table_name = "filtered"
    sql = "SELECT user_id, event_type, event_time FROM user_events WHERE event_type = 'purchase'"
  }
}

sink {
  Jdbc {
    source_table_name = "filtered"
    url = "jdbc:mysql://localhost:3306/analytics"
    table = "purchase_events"
    user = "root"
    password = "xxx"
    primary_keys = ["user_id"]
  }
}
```

---

## 第二部分：功能维度对比

### 2.1 核心能力矩阵

| 能力维度 | SeaTunnel | nop-stream | nop-batch |
|---------|-----------|------------|-----------|
| **Source 类型** | 100+ 连接器（Kafka/JDBC/ES/HDFS/...） | Collection Source 仅内存 | JDBC/ORM/Excel/CSV/File |
| **Sink 类型** | 100+ 连接器 | Print Sink 仅控制台 | JDBC/ORM/Excel/CSV/File |
| **Transform** | SQL / FieldMapper / 自定义 | map/filter/flatmap | Processor 链 + Delta 表达式 |
| **类型系统** | SeaTunnelRow + SeaTunnelDataType | Java 泛型 + TypeInformation | 泛型 |
| **Schema 管理** | 自动推断 + 手动定义 + Catalog | 无 | ORM 实体映射 |
| **批流统一** | ✅ BATCH + STREAMING 模式 | 仅流式 | 仅批式 |
| **并行度** | ✅ 多 Reader 并行读取 | ❌ 单线程 | ✅ 多线程分片 |
| **Checkpoint** | ✅ barrier 对齐 exactly-once | 代码存在未对接 | 有状态恢复机制 |
| **SQL Transform** | ✅ 内置 SQL 引擎 | ❌ | ❌（但可用 Delta 表达式） |
| **CEP** | ❌ | ✅ NFA + SharedBuffer | ❌ |
| **窗口操作** | ❌ | ✅ 简化 Flink 窗口 | ❌（但可按 chunk 处理） |
| **声明式配置** | ✅ HOCON | ❌ Java API | ✅ XML/XDSL |
| **多源 Join** | ✅ 通过 result_table_name | ❌ | ❌ |
| **Schema Evolution** | ✅ | ❌ | ORM 实体迁移 |
| **Connector 插件** | ✅ SPI 动态加载 | ❌ | 固定实现 |
| **执行引擎选择** | ✅ Zeta / Flink / Spark | 仅自研 | 仅自研 |
| **CDC 支持** | ✅ MongoDB-CDC / MySQL-CDC | ❌ | ❌ |

### 2.2 架构维度对比

| 架构维度 | SeaTunnel | nop-stream | nop-batch |
|---------|-----------|------------|-----------|
| **部署模型** | 独立集群 / 嵌入 Flink/Spark | 嵌入式 Java 库 | 嵌入式 Java 库 |
| **执行模型** | DAG + 分布式 Task | 单线程 chain push | 多线程 chunk processing |
| **数据交换** | 内存 + 网络 | 直接方法调用 | 内存队列 |
| **调度器** | Zeta Master（选举制） | 无（直接执行） | nop-job（可选） |
| **状态后端** | Memory / File | Memory | 数据库（通过 ORM） |
| **容错** | Checkpoint + 重启 | 无 | 跳过/重试 + 历史记录 |
| **事务** | TwoPhaseCommitSinkFunction | ❌ | 批量提交 |
| **资源隔离** | Slot 机制 | 无 | 线程池 |

### 2.3 管道编排对比

**SeaTunnel**：声明式 HOCON → 解析为 Pipeline → 编译为 DAG → 分发到 Worker 执行

```
HOCON Config
  → LogicalDag (source → transform → sink)
    → PhysicalDag (按并行度展开)
      → TaskExecution (Worker 上执行)
```

**nop-stream**：Java API → Transformation DAG → 折叠为线性链 → 单线程执行

```
env.addSource().map().keyBy().window().aggregate().sink()
  → Transformation DAG
    → Chain (折叠为一条链)
      → Source.push → Sink
```

**nop-batch**：XDSL → BatchTaskBuilder → Loader → Processor → Consumer 循环

```
batch.task.xml
  → BatchTaskBuilder
    → Loader (按分片加载)
      → Processor (逐条处理)
        → Consumer (批量写入)
          → 循环直到所有分片处理完
```

---

## 第三部分：SeaTunnel 的 Connector 生态

### 3.1 Top 10 连接器能力

| 连接器 | Source 能力 | Sink 能力 | 并行读取 | Exactly-Once | CDC |
|--------|-----------|----------|---------|-------------|-----|
| **Kafka** | ✅ 分区消费 | ✅ 分区写入 | ✅ 按 partition | ✅ | ✅ |
| **JDBC** | ✅ 分片查询 | ✅ 批量写入 | ✅ 按分片 | ✅ 事务 | ✅ |
| **Elasticsearch** | ✅ scroll 查询 | ✅ bulk 写入 | ✅ 按 shard | ✅ | ❌ |
| **HDFS/S3** | ✅ 文件分割 | ✅ 文件分区 | ✅ 按文件 | ✅ | ❌ |
| **MongoDB** | ✅ 分片查询 | ✅ bulk 写入 | ✅ | ✅ | ✅ |
| **Redis** | ✅ scan | ✅ pipeline 写入 | ✅ | ❌ | ❌ |
| **HTTP** | ✅ 分页请求 | ✅ webhook | ⚠️ | ❌ | ❌ |
| **File (CSV/JSON)** | ✅ 文件分割 | ✅ 文件写入 | ✅ | ✅ | ❌ |
| **ClickHouse** | ✅ 分片查询 | ✅ 批量写入 | ✅ | ✅ | ❌ |
| **Iceberg/Hudi** | ✅ snapshot 读 | ✅ merge 写入 | ✅ | ✅ | ✅ |

### 3.2 连接器 SPI 机制

SeaTunnel 通过 Java SPI 机制动态加载连接器：

1. 每个连接器是一个独立 Maven 模块
2. 通过 `@AutoService(SeaTunnelSource.class)` 或 `@AutoService(SeaTunnelSink.class)` 注册
3. 运行时通过 `ConnectorInstanceFactory` 按配置中的连接器标识查找实现类
4. 连接器配置通过 HOCON 中的 `source.{ConnectorName}` 段定义

### 3.3 连接器配置示例

**Kafka → JDBC 同步**：

```hocon
env { job.mode = "STREAMING" }

source {
  Kafka {
    bootstrap.servers = "kafka:9092"
    topic = "orders"
    schema = { fields { order_id = "bigint", amount = "decimal(10,2)" } }
  }
}

sink {
  Jdbc {
    url = "jdbc:mysql://mysql:3306/warehouse"
    table = "orders"
    primary_keys = ["order_id"]
    batch_size = 1000
  }
}
```

---

## 第四部分：SeaTunnel 的 Transform 能力

### 4.1 内置 Transform

| Transform | 能力 | 使用场景 |
|-----------|------|---------|
| **Sql** | SQL 查询（基于 Calcite） | 复杂过滤、聚合、Join |
| **FieldMapper** | 字段映射、重命名、删除 | Schema 对齐 |
| **Copy** | 字段复制 | 数据衍生 |
| **Replace** | 字符串替换 | 数据清洗 |
| **Split** | 字段拆分 | 数据标准化 |
| **FilterRowKind** | CDC 事件过滤 | 仅保留 INSERT/UPDATE |
| **Filter** | 行过滤 | 条件筛选 |
| **LLM** | AI 数据增强 | 智能数据填充 |
| **JsonPath** | JSON 字段提取 | 嵌套数据处理 |

### 4.2 SQL Transform 能力

SeaTunnel 的 SQL Transform 基于 Apache Calcite 实现，支持：
- SELECT / WHERE / GROUP BY / HAVING
- JOIN（多 source 通过 result_table_name 关联）
- 聚合函数（SUM/AVG/COUNT/MAX/MIN）
- 窗口函数
- 子查询

---

## 第五部分：nop-batch 作为 ETL 引擎

### 5.1 nop-batch 已具备的 ETL 能力

nop-batch 模块已经具备了一个完整的 ETL 管道框架：

| 概念 | nop-batch 接口 | 对应 SeaTunnel |
|------|---------------|---------------|
| Source | `IBatchLoaderProvider` | `SeaTunnelSource` |
| Transform | `IBatchProcessorProvider` | `SeaTunnelTransform` |
| Sink | `IBatchConsumerProvider` | `SeaTunnelSink` |
| 分片 | `IBatchRequestGenerator` + split | `SourceSplitEnumerator` |
| 状态 | `IBatchStateStore` | Checkpoint State |
| 历史 | `IBatchRecordHistoryStore` | 无对应（Nop 独有） |
| 聚合 | `IBatchAggregator` | SQL Aggregate |
| 过滤 | `IBatchRecordFilter` | Filter Transform |

### 5.2 nop-batch DSL

nop-batch 已有 XDSL 声明式配置（比 SeaTunnel 的 HOCON 更强大）：

| 配置模型 | 职责 |
|---------|------|
| `BatchLoaderModel` | 数据源配置 |
| `BatchJdbcReaderModel` | JDBC 读取配置 |
| `BatchOrmReaderModel` | ORM 实体读取配置 |
| `BatchFileReaderModel` | 文件读取配置 |
| `BatchExcelReaderModel` | Excel 读取配置 |
| `BatchConsumerModel` | 数据消费配置 |
| `BatchJdbcWriterModel` | JDBC 写入配置 |
| `BatchOrmWriterModel` | ORM 实体写入配置 |
| `BatchFileWriterModel` | 文件写入配置 |
| `BatchExcelWriterModel` | Excel 写入配置 |
| `BatchChunkProcessorBuilderModel` | 数据处理配置 |

### 5.3 nop-batch 已覆盖的 ETL 场景

| 场景 | nop-batch 支持情况 |
|------|-------------------|
| JDBC → JDBC 数据同步 | ✅ JdbcReader → Processor → JdbcWriter |
| JDBC → Excel 导出 | ✅ OrmReader → Processor → ExcelWriter |
| Excel → JDBC 导入 | ✅ ExcelReader → Processor → JdbcWriter |
| CSV → JDBC 导入 | ✅ FileReader → Processor → JdbcWriter |
| 多线程并行处理 | ✅ 按分片并行 Loader |
| 批量提交 | ✅ 按批次 Consumer |
| 错误跳过/重试 | ✅ SkipBatchConsumer / RetryBatchConsumer |
| 处理历史记录 | ✅ IBatchRecordHistoryStore |
| 速率控制 | ✅ RateLimitConsumer |
| DSL 声明式编排 | ✅ batch.task.xml |

---

## 第六部分：用 Nop 平台实现 SeaTunnel 核心功能的路径

### 6.1 总体策略

**不克隆 SeaTunnel，而是利用 Nop 平台已有的能力组合实现等价功能。**

SeaTunnel 解决的核心问题是：**声明式定义 ETL 管道 + 可插拔连接器 + 执行引擎**。

Nop 平台的对应方案：

| SeaTunnel 概念 | Nop 平台对应 | 现状 |
|---------------|------------|------|
| 连接器 SPI | nop-batch DSL 的 Loader/Consumer 插件 | 已有 JDBC/ORM/Excel/CSV |
| 声明式配置 | batch.task.xml (XDSL) | ✅ 已实现 |
| SQL Transform | XLang Delta 表达式 / 未来嵌入 Calcite | Delta 已有，SQL 未嵌入 |
| 类型系统 | ORM 实体 (orm.xml) | ✅ 已实现 |
| Schema 管理 | ORM 实体 + 自动建表 | ✅ 已实现 |
| 执行引擎 | nop-batch (批式) + nop-stream (流式) | 批式已实现，流式早期 |
| 调度器 | nop-job | ✅ 已实现 |
| Checkpoint | nop-batch 状态恢复 | 部分实现 |

### 6.2 缺失能力的实现路径

#### 6.2.1 Kafka 连接器（优先级最高）

**目标**：实现 Kafka Source 和 Kafka Sink，用于实时数据同步。

**实现路径**：
1. 在 nop-stream-core 中新增 `KafkaSourceFunction` 实现 `SourceFunction<T>`
2. 在 nop-stream-core 中新增 `KafkaSinkFunction` 实现 `SinkFunction<T>`
3. 利用 Nop 的 IoC 容器管理 Kafka Producer/Consumer 生命周期
4. Schema 映射通过 `TypeInformation` → ORM 实体完成

**与 SeaTunnel 的差距**：
- SeaTunnel 的 Kafka Source 有精细的 split 分配和 checkpoint 机制
- Nop 版本在初始阶段可以简化为：单线程消费 + 定期 commit offset

#### 6.2.2 多源 Join

**目标**：支持两个数据源的 Join 操作。

**实现路径**：
1. 基于 nop-stream 的 KeyedStream 实现 hash join：主流 keyBy → 查找维度表（通过 nop-orm）
2. 对于批量 Join：利用 nop-batch 的 `BatchLoaderDispatcherModel` 实现多 Loader 合并
3. 对于实时 Join：在 nop-stream 中实现 `LookupJoinOperator`，基于 KeyedState 缓存维度表

**与 SeaTunnel 的差距**：
- SeaTunnel 通过 SQL Transform + result_table_name 实现声明式 Join
- Nop 版本需要编程式定义 Join 逻辑（但可以通过 XDSL 封装为声明式）

#### 6.2.3 SQL Transform

**目标**：支持 SQL 语法的数据转换。

**实现路径**：
1. **短期方案**：利用 XLang 的 Delta 表达式实现等价功能（字段映射、过滤条件、计算列）
2. **中期方案**：嵌入 Apache Calcite 作为 SQL 解析引擎，将 SQL 转换为 nop-batch 的 Processor 链
3. **长期方案**：利用 Nop 的 report engine（报表引擎）实现 SQL 查询能力

#### 6.2.4 连接器扩展框架

**目标**：像 SeaTunnel 一样支持 SPI 动态加载连接器。

**实现路径**：
1. 利用 Nop 的 IoC 容器（`@Inject` + beans.xml）代替 Java SPI
2. 定义 `IStreamSourceFactory` 和 `IStreamSinkFactory` 接口
3. 每个连接器作为独立的 beans.xml 配置注册
4. 在 XDSL 配置中通过 `ref` 引用连接器

#### 6.2.5 CDC（变更数据捕获）

**目标**：支持 MySQL/MongoDB CDC 实时同步。

**实现路径**：
1. **短期**：利用数据库的 binlog 或者 trigger 机制，通过定时轮询模拟 CDC
2. **中期**：集成 Debezium Engine（嵌入式模式），作为 nop-stream 的 SourceFunction
3. **长期**：基于 nop-stream 的 Checkpoint 机制实现 exactly-once CDC

### 6.3 声明式 ETL 管道设计

利用 Nop 的 XDSL 能力，可以设计一个声明式 ETL 管道配置（类似 SeaTunnel 的 HOCON）：

```
设计方案（利用现有 nop-batch DSL 扩展）:

batch.task.xml 中已有:
  - <loader> 定义数据源（JDBC/ORM/Excel/File）
  - <processor> 定义数据处理
  - <consumer> 定义数据写出（JDBC/ORM/Excel/File）

需要扩展:
  - 新增 <kafka-source> 和 <kafka-sink> 配置元素
  - 新增 <stream-source> 和 <stream-sink> 统一流式管道
  - 新增 <lookup-join> 声明式 Join
  - 扩展 <processor> 支持 SQL 表达式
```

### 6.4 nop-stream + nop-batch 分工

| 能力类型 | 使用 nop-stream | 使用 nop-batch |
|---------|----------------|----------------|
| 实时流式处理 | ✅ Kafka → Transform → Sink | ❌ |
| 事件模式识别 | ✅ CEP (NFA + SharedBuffer) | ❌ |
| 窗口聚合 | ✅ WindowOperator | ❌ |
| 批量数据同步 | ❌ | ✅ Loader → Processor → Consumer |
| 文件导入导出 | ❌ | ✅ Excel/CSV Reader/Writer |
| 定时调度 | ❌ | ✅ 配合 nop-job |
| 多线程并行 | ❌（当前） | ✅ 分片并行 |
| 容错/重试 | ❌ | ✅ 跳过/重试/历史 |

---

## 第七部分：结论与建议

### 7.1 核心判断

SeaTunnel 和 nop-stream/nop-batch 解决的是**不同层次的问题**：

- **SeaTunnel**：分布式数据集成平台，重点是**连接器生态 + 分布式执行 + Exactly-once**
- **nop-stream**：轻量流处理引擎，重点是**单流窗口操作 + CEP**
- **nop-batch**：批处理引擎，重点是**Loader→Processor→Consumer 管道 + DSL 声明式编排**

### 7.2 可借鉴的 SeaTunnel 设计

| 设计点 | 借鉴方式 | 优先级 |
|--------|---------|--------|
| Source/Sink 抽象 | 在 nop-stream 中建立等价接口体系 | P0 |
| 连接器 SPI | 利用 NopIoC 代替 Java SPI，保留可插拔性 | P1 |
| 声明式配置 | 扩展 nop-batch 的 XDSL，覆盖流式管道 | P1 |
| 分片读取 | 参考 SourceSplitEnumerator 设计 | P2 |
| Schema 自动推断 | 利用 ORM 实体元数据 + 类型推断 | P2 |

### 7.3 不建议照搬的 SeaTunnel 设计

| 设计点 | 原因 |
|--------|------|
| 独立类型系统 (SeaTunnelRow) | Nop 已有 ORM 实体体系，不需要额外的行类型 |
| Zeta 执行引擎 | 过于复杂，Nop 的场景不需要分布式执行 |
| HOCON 配置格式 | Nop 已有 XDSL（XML+Delta），更灵活且支持差异化定制 |
| 100+ 连接器 | 大部分场景用 JDBC/Kafka/Excel/CSV 已足够 |

---

## Open Questions

- [ ] Kafka 连接器的初始版本是否需要支持 exactly-once？还是 at-least-once 即可？
- [ ] SQL Transform 是否有必要嵌入 Calcite？还是 XLang Delta 表达式已足够覆盖大部分场景？
- [ ] nop-stream 和 nop-batch 的流批统一如何设计？是否有统一的 Source/Sink 抽象？
- [ ] 连接器开发规范如何定义？需要提供哪些基类和工具？
