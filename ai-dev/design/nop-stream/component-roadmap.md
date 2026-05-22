# nop-stream 组件分解与开发路线

> Status: active
> Created: 2026-05-21
> Parent: `ai-dev/design/nop-stream/README.md`

---

## 1. 定位重申

nop-stream 是 Nop 平台的流处理引擎，定位为**可分布式执行的 Flink 简化版**。不是嵌入式玩具，不是设计原型，不是 MVP。

**核心取舍**：保留 Flink DataStream API 概念和流处理语义，去除复杂 Join、广播流、异步算子等高级特性，聚焦于单流窗口聚合 + CEP 模式匹配 + Checkpoint 容错。

**与 Nop 平台的关系**：nop-stream 是 Nop 平台的正式模块，使用 Nop 平台的标准基础设施——nop-dao 的 `IJdbcTemplate` + `IDialect` 做多数据库适配，nop-batch 的 `IBatchLoader`/`IBatchConsumer` 做数据源接入，nop-xlang 的 `IEvalFunction` 做 CEP 条件表达式。

---

## 2. 组件分解

nop-stream 按职责划分为 **6 个核心组件** 和 **4 个规划组件**。每个组件有明确的职责边界、对外接口和依赖关系。

### 2.1 核心组件

| # | 组件 | 对应模块 | 职责 | 依赖 |
|---|------|---------|------|------|
| C1 | **流式 API** | nop-stream-core (datastream 包) | DataStream / KeyedStream / WindowedStream DSL，Transformation DAG 构建 | 无外部依赖 |
| C2 | **编译管线** | nop-stream-core (graph/jobgraph/execution 包) | Transformation → StreamGraph → JobGraph → ExecutionPlan 四层编译 + 算子链化优化 | 依赖 C1 |
| C3 | **算子运行时** | nop-stream-core (operators 包) + nop-stream-runtime | 算子基类、内置算子（map/filter/sink/source）、WindowOperator、定时器服务 | 依赖 C4 |
| C4 | **状态管理** | nop-stream-core (common/state 包) | IStateBackend / IKeyedStateBackend 接口 + Memory 实现 | 无外部依赖 |
| C5 | **Checkpoint** | nop-stream-core (checkpoint 包) + nop-stream-runtime (checkpoint 包) | Barrier 传播、协调器、持久化存储（基于 IJdbcTemplate 多数据库） | 依赖 C3, C4 |
| C6 | **CEP 引擎** | nop-stream-cep | NFA 编译、SharedBuffer、Pattern API、声明式模型 | 依赖 C1, nop-xlang |

### 2.2 连接器与集成组件

| # | 组件 | 对应模块 | 职责 | 依赖 |
|---|------|---------|------|------|
| C7 | **连接器** | nop-stream-connector | Source/Sink 适配：nop-batch Loader/Consumer、IMessageService、Debezium CDC | 依赖 C1, nop-batch-core |
| C8 | **欺诈检测示例** | nop-stream-fraud-example | 端到端使用范例 | 依赖 C6 |

### 2.3 规划组件（保留 pom 占位）

| # | 组件 | 对应模块 | 规划职责 |
|---|------|---------|---------|
| P1 | **公共 API** | nop-stream-api | 从 core 分离纯接口，使 runtime/cep 不依赖 core 实现 |
| P2 | **Checkpoint 独立** | nop-stream-checkpoint | 从 runtime 分离 checkpoint 协调器和存储 |
| P3 | **Flink 后端** | nop-stream-flink | 将 Transformation 映射到 Flink DataStream |
| P4 | **流编排** | nop-stream-flow | 基于 XLang DSL 的声明式流任务编排 |

### 2.4 依赖关系

```
        ┌─────────────────────────────────────────┐
        │             C8 示例                      │
        └────────────────┬────────────────────────┘
                         │
        ┌────────────────▼────────────────────────┐
        │             C6 CEP                       │ ← nop-xlang
        └────────────────┬────────────────────────┘
                         │
   ┌─────────────────────┼────────────────────┐
   │                     │                    │
┌──▼───────┐      ┌──────▼──────┐     ┌──────▼──────┐
│ C5 Check │      │ C7 连接器    │     │ P4 流编排   │
│ point    │      │             │     │   (规划)    │
└──┬───┬───┘      └──────┬──────┘     └─────────────┘
   │   │                 │
   │   └────────┬────────┘
   │            │
┌──▼────────────▼───┐
│   C3 算子运行时    │
│  (core + runtime)  │
└────────┬──────────┘
         │
┌────────▼──────────┐
│   C4 状态管理      │
└────────┬──────────┘
         │
┌────────▼──────────┐
│ C2 编译管线        │
└────────┬──────────┘
         │
┌────────▼──────────┐
│   C1 流式 API      │
└───────────────────┘
```

**关键约束**：
- C1 是基础层，不依赖任何 nop-stream 子组件
- C5 依赖 C3（算子 snapshot/restore）和 C4（状态后端）
- C6 (CEP) 只依赖 C1，不依赖 C3/C5
- C7 (连接器) 只依赖 C1 和 nop-batch-core
- **C5 的持久化存储必须通过 `IJdbcTemplate` + `IDialect` 实现多数据库支持，禁止直接使用 `java.sql.Connection`**

---

## 3. 每个组件的开发标准

### 设计原则

1. **先设计接口**：每个组件先定义清晰、稳定的接口契约，经过评审后再写实现
2. **接口不是占位符**：接口设计必须考虑完整的使用场景、错误处理、扩展性。不能先写一个空方法以后再补
3. **实现可以渐进**：接口定稿后，先写最核心的实现路径，经测试验证后逐步丰富
4. **测试覆盖核心路径**：每个接口至少有一个测试验证基本契约（正常路径 + 异常路径）
5. **符合 Nop 平台惯例**：使用 `NopException` + `ErrorCode` 做错误处理，使用 `IConfigReference` 做配置，使用 `@Internal` 标注内部 API

### 各组件的设计要求

#### C1 流式 API

**已完成度**：高。DataStream / KeyedStream / Transformation DAG 已稳定。

**待完善**：
- `WindowedStreamImpl.apply/aggregate/reduce` 需要实现，不能抛 UnsupportedOperationException。这需要 C3 提供一个 `WindowOperatorFactory` 接口（core 定义），runtime 模块实现
   - `assignTimestampsAndWatermarks()` 在 `execute()` 中通过图模型路径的 StreamGraph 自动插入 TimestampsAndWatermarksOperator

#### C2 编译管线

**已完成度**：高。四层编译（Transformation → StreamGraph → JobGraph → ExecutionPlan）和算子链化已实现。

**待完善**：
- `SimpleStreamOperatorFactory.createStreamOperator()` 返回同一对象——parallelism > 1 时状态不隔离。需要改为每次创建新实例
- StreamGraphGenerator 中的 `PartitionOperatorFactory.createStreamOperator()` 返回 null，这个设计需要改为在 JobGraphGenerator 阶段正确处理分区节点

#### C3 算子运行时

**已完成度**：中。内置算子（map/filter/flatMap/source/sink）完整。WindowOperator 存在多处正确性问题。

**必须修复的核心问题**：
- `WindowOperator.addWindowElement()` 的累加器类型腐蚀——首个元素直接存为裸值而非创建累加器初始值
- `MergingWindowSet.persist()` 是空操作——合并窗口的映射关系在 checkpoint 后丢失
- `WindowOperator.getSimpleAccumulator()` 返回 null——违反接口契约

#### C4 状态管理

**已完成度**：接口完整，Memory 实现基本可用。

**设计要求**：
- 接口层次（`IStateBackend → IKeyedStateBackend → IInternalStateBackend`）保持不变——这是正确的分层，为后续 Redis/RocksDB 预留扩展点
- `MemoryKeyedStateBackend` 的快照序列化需要从 Java 序列化改为 `JsonTool` 序列化——与 Nop 平台惯例一致，可读、可调试、可跨版本
- `ValueStateDescriptor(name, TypeInformation)` 构造器丢弃 typeInfo 参数——必须修复

#### C5 Checkpoint

**已完成度**：中。核心流程已实现，但需要引入 CheckpointPlan 解耦拓扑信息，并修复多个正确性 bug。

**关键设计——引入 CheckpointPlan**：

当前 checkpoint 逻辑与执行引擎耦合（`GraphModelCheckpointExecutor` 直接遍历执行计划推导拓扑信息），导致：状态键名碰撞（BUG 1）、恢复时状态路由错误（BUG 4）、无法支持分布式执行。需要引入 `CheckpointPlan` 显式建模 checkpoint 需要的拓扑信息。

CheckpointPlan 的职责：
- 记录 source task 列表（barrier 注入点）
- 记录所有 task 列表（ACK 跟踪集合）
- 记录每个 task 的算子状态映射（恢复时的状态路由）
- 在 GraphExecutionPlan 构建阶段一次性生成，checkpoint 子系统只读使用

这个设计参考了 Apache SeaTunnel 的 `CheckpointPlan`。详见 `checkpoint-design.md` §2.2。

**关键设计要求——JdbcCheckpointStorage 重新设计**：

当前实现直接使用 `java.sql.Connection` + MySQL DDL，这不符合 Nop 平台规范。重新设计要求：

| 维度 | 当前（错误） | 目标（正确） |
|------|------------|------------|
| 数据库访问 | `DataSource.getConnection()` | `IJdbcTemplate` 注入 |
| SQL 构建 | 字符串拼接 | `SQL.begin()` 构建器 |
| DDL | MySQL 专用（`AUTO_INCREMENT` 等） | `IDialect` 类型映射 + 方言感知 |
| 分页 | `LIMIT 1` | `IJdbcTemplate.findPage()` |
| 事务管理 | 手动 `commit/rollback` | `ITransactionTemplate.runInTransaction()` |
| 表存在检查 | `INFORMATION_SCHEMA` | `IJdbcTemplate.existsTable()` |
| ID 生成 | `AUTO_INCREMENT` | 应用侧生成（checkpointId 已有 AtomicLong） |

`JdbcCheckpointStorage` 的正确依赖关系：
```
JdbcCheckpointStorage
  ├── IJdbcTemplate (NopIoC 注入)
  ├── String querySpace (可配置，默认 "default")
  └── 通过 IJdbcTemplate 获取 IDialect，自动适配 MySQL/PostgreSQL/Oracle/H2/DM 等
```

**必须修复的 Bug**：

| Bug | 严重度 | 描述 | CheckpointPlan 是否解决 |
|-----|--------|------|------------------------|
| Keyed state 碰撞 | 严重 | 所有算子的 keyed state 用同一个 key 写入 Map，互相覆盖 | ✅ `keyedStateStorageKey` 为每个算子分配唯一键名（如 `"operator-0-keyed"`）。但 `IKeyedStateBackend` 也需按算子隔离（独立修复） |
| Barrier 注入线程安全 | 严重 | 从独立线程注入 barrier，与 source 线程竞争 | 不解决——需修改 source 算子的 barrier 检查机制 |
| 状态恢复路由错误 | 中等 | 恢复时把所有状态发给每个算子 | ✅ `stateMappings` 精确路由，dual-state 通过 `operatorStateKey` + `keyedStateStorageKey` 双键解决 |
| CheckpointBarrierTracker 无重叠保护 | 中等 | triggerCheckpoint 覆盖进行中的状态 | 不直接解决，需 Coordinator 层面修复 |

#### C6 CEP 引擎

**已完成度**：高。NFA、SharedBuffer、Pattern API、声明式模型都是 nop-stream 中最成熟的部分。

**待完善**：
- 需要实现 `DslModelParser` 对接 `pattern.xdef`——当前 XDEF schema 和 `_gen` 模型类存在但无加载器
- `CepOperator` 的 `initializeState()` 被注释掉，需要对接 C4 的 `IKeyedStateBackend` 而非自建 `SimpleKeyedStateStore`

#### C7 连接器

**已完成度**：高。5 个适配器已实现。

**设计正确**：通过 `IBatchLoader`/`IBatchConsumer` 桥接 nop-batch，一个适配器覆盖所有数据源。CDC 和消息队列通过 `IMessageService` 独立适配。

---

## 4. 开发顺序

开发按依赖关系从底层到上层，每个阶段产出可测试、可集成的成果。

### 阶段 1：修复基础设施（C4 状态管理 + C3 核心算子修复）

**目标**：让状态管理和窗口算子的核心路径正确工作。

**工作项**：

1. **C4：修复 `MemoryKeyedStateBackend`**
   - 修复 `MemoryInternalAppendingState.add()` 累加器不重置的问题
   - 修复 `ValueStateDescriptor(name, TypeInfo)` 丢弃 typeInfo 的问题
   - 修复 `currentNamespace` 标记为 `transient` 导致反序列化后为 null 的问题
   - 快照序列化从 Java 序列化改为 `JsonTool`
   - 编写测试：累加连续 add、快照/恢复、namespace 切换

2. **C3：修复 `WindowOperator` 核心正确性**
   - 修复 `addWindowElement()` 累加器类型腐蚀
   - 修复 `mergeWindowContents()` 静默吞 ClassCastException
   - 修复 `MergingWindowSet.persist()` 空操作
   - 修复 `getSimpleAccumulator()` 返回 null
   - 修复 `KeySelectorPartitioner` 的 null key 和 `Integer.MIN_VALUE` 问题
   - 编写测试：单 key 聚合正确性、多 key 隔离、合并窗口合并、checkpoint 恢复

3. **C3：修复 `SimpleStreamOperatorFactory`**
   - 改为每次 `createStreamOperator()` 创建新实例
   - 编写测试：多次调用返回不同实例、parallelism > 1 场景

**交付标准**：
- `WindowOperator` 的聚合、合并窗口、checkpoint 恢复测试全部通过
- `MemoryKeyedStateBackend` 的累加器正确性测试通过

### 阶段 2：API 粘合层（C1 + C3 对接）

**目标**：用户可以通过标准 DataStream API 完成完整的 `source → keyBy → window → aggregate → sink` 流程，无需手动构建 WindowOperator。

**工作项**：

1. **C1：设计 `WindowOperatorFactory` 接口**（core 模块定义）
   - 接口方法：`createOperator(WindowAssigner, Trigger, ...)` 等
   - 这个接口使得 core 的 `WindowedStreamImpl` 可以创建 runtime 的 `WindowOperator`，而不需要 core 依赖 runtime
   - 通过 SPI 或 `StreamExecutionEnvironment.registerOperatorFactory()` 注册

2. **C1：实现 `WindowedStreamImpl.apply/aggregate/reduce`**
   - 内部通过 `WindowOperatorFactory` 创建 `WindowOperator`
   - 调用 `transform()` 注册到 Transformation DAG
   - 编写端到端测试：`env.addSource().keyBy().window().aggregate().sink()` 完整流程

3. **C1：修复 `assignTimestampsAndWatermarks` 在 Fast Path 中的处理**
   - `instantiateOperators()` 需要处理 `TimestampsAndWatermarksTransformation`
   - 编写测试：带 watermark 的窗口聚合端到端测试

**交付标准**：
- 用户可以通过标准 API 写出完整的窗口聚合程序
- 带事件时间的窗口聚合正确触发

### 阶段 3：Checkpoint 生产化（C5）

**目标**：Checkpoint 子系统达到生产可用水平——CheckpointPlan 解耦、多数据库存储、线程安全、正确恢复。

**工作项**：

1. **C5：实现 CheckpointPlan + CheckpointPlanBuilder**
   - 定义 `CheckpointPlan`、`TaskLocation`、`OperatorStateMapping` 数据结构（core 模块）
   - 实现 `CheckpointPlanBuilder`：从 `GraphExecutionPlan` 中提取 source task、all tasks、state mappings（runtime 模块）
   - 修改 `CheckpointCoordinator` 构造器接收 `CheckpointPlan`，不再隐式遍历执行计划
   - 修改 `CheckpointBarrierTracker.acknowledgeOperator()`：用 `OperatorStateMapping.operatorStateKey` 代替硬编码 `"keyed-state"`（修复 BUG 1）
   - 修改 `buildSnapshotFromTaskState()`：按 `stateMappings` 精确路由状态（修复 BUG 4）
   - 编写测试：多算子链的 keyed state 不互相覆盖、恢复时状态精确路由

2. **C5：重新设计 `JdbcCheckpointStorage`**
   - 基于 `IJdbcTemplate` + `IDialect` 实现，支持 MySQL/PostgreSQL/Oracle/H2/DM 等
   - 使用 `SQL.begin()` 构建所有 SQL
   - 使用 `IJdbcTemplate.existsTable()` 检查表是否存在
   - 使用 `IDialect` 类型映射生成建表 DDL
   - 使用 `ITransactionTemplate` 管理事务
   - 通过 NopIoC 注册为 bean
   - 编写测试：使用 H2 内存数据库验证多数据库兼容性

3. **C5：修复 Barrier 注入线程安全问题**
   - Barrier 应作为数据流内的特殊元素注入，而非从外部线程干预
   - Source 算子在 `run()` 循环中检查 `volatile` 标志或 `BlockingQueue`，需要时在 source 线程上注入 barrier
   - 编写测试：并发 checkpoint 触发不丢数据

4. **C5：统一执行路径**
   - `execute()` 已统一走图模型路径，无需单独维护快速路径
   - 移除 `executeWithGraphModel()` 方法（已由 `execute()` 统一替代）
   - 清理 `GraphModelCheckpointExecutor` 中的代码重复

**交付标准**：
- `CheckpointPlan` 从 `GraphExecutionPlan` 正确生成并通过测试
- 多算子链的 keyed state 快照/恢复正确（BUG 1 修复）
- 状态恢复精确路由到对应算子（BUG 4 修复）
- Checkpoint 存储支持至少 MySQL 和 H2（通过 `IJdbcTemplate` 自动适配）
- 多次 checkpoint 循环后恢复验证正确性

### 阶段 4：CEP 完善（C6）

**目标**：CEP 模块完全对接 Nop 平台的状态管理和 DSL 加载。

**工作项**：

1. **C6：`CepOperator` 对接 `IKeyedStateBackend`**
   - 移除自建的 `SimpleKeyedStateStore`，使用 C4 的标准状态后端
   - 恢复 `initializeState()` 的初始化逻辑
   - 编写测试：多 key CEP 匹配、checkpoint 恢复后 CEP 状态正确

2. **C6：实现 `DslModelParser` 对接**
   - 使用 Nop 的 `DslModelParser` 加载 `.pattern.xml` 文件
   - 编写测试：从 XML 加载 pattern 并执行匹配

3. **C6：移除 runtime → cep 幽灵依赖**
   - 从 `nop-stream-runtime/pom.xml` 移除对 `nop-stream-cep` 的依赖
   - 清理 `nop-stream-runtime` 中的 `CepWindowOperator` 及相关死代码

**交付标准**：
- CEP 通过标准状态后端管理状态
- 可以从 XML 文件加载 CEP Pattern

### 阶段 5：连接器增强 + 示例重写（C7 + C8）

**目标**：连接器覆盖实际 ETL 场景，示例代码展示正确用法。

**工作项**：

1. **C7：增强 `BatchLoaderSourceFunction`**
   - 添加资源清理（loader 的 close/cleanup）
   - 支持 `CheckpointedSourceFunction`（记录读取位置，支持恢复）
   - 编写测试：大文件读取 + checkpoint 恢复

2. **C7：增强 `BatchConsumerSinkFunction`**
   - 实现 `TwoPhaseCommitSinkFunction` 适配（基于 `ITransactionTemplate`）
   - 编写测试：exactly-once 写入

3. **C8：重写 fraud-example**
   - 修复所有已知的示例 bug（条件恒真、事件类型不匹配、硬编码平均值等）
   - 展示正确的 CEP 使用方式
   - 展示与 runtime 模块的集成（窗口 + CEP 组合）

**交付标准**：
- 从数据库/文件读取 → 流处理 → 写入数据库的端到端 ETL 管线可用
- 示例代码无误导

### 阶段 6：编译管线增强（C2）

**目标**：支持多链管线、并行度 > 1。

**工作项**：

1. **C2：修复 `JobGraphGenerator` 链节点映射**
   - 修复 `buildNodeToVertexMap()` 只映射链头的问题
   - 编写测试：多 sink 管线、union 管线

2. **C2：支持 `ResultPartition` / `InputGate` 的跨 Task 数据交换**
   - 当前为内存队列，验证多 Task 场景的正确性
   - 编写测试：source → map → [跨 Task] → window → sink

3. **C2：`KeySelectorPartitioner` 支持多并行度**
   - 实现基于 key hash 的数据分区
   - 编写测试：parallelism > 1 时状态隔离正确

**交付标准**：
- 多 sink、多 Task 的管线正确执行
- 并行度 > 1 时数据按 key 正确分区

---

## 5. 模块 POM 依赖清理

### 必须立即清理

| 问题 | 操作 |
|------|------|
| `runtime` 依赖 `cep`（零代码引用） | 从 `runtime/pom.xml` 移除 |
| `JdbcCheckpointStorage` 直接使用 `DataSource` | 改为依赖 `nop-dao-jdbc`（通过 `IJdbcTemplate`） |
| `Configuration` 接口为空 | 对接 Nop 的 `IConfigReference` |

### 长期清理

| 问题 | 操作 |
|------|------|
| 4 个空壳模块在 pom.xml 中 | 保留占位但标注"规划中"注释 |
| `operator`（单数）包残留 | 统一到 `operators`（复数）包 |
| ~200 行注释代码（WindowOperator） | 在阶段 1 修复过程中清理 |

---

## 6. 设计决策记录

### D1：Checkpoint 存储为什么必须基于 IJdbcTemplate 而非 DataSource

**选了什么**：`JdbcCheckpointStorage` 通过 `IJdbcTemplate` + `IDialect` 访问数据库。

**为什么**：
- `IJdbcTemplate` 封装了连接管理、事务管理、方言适配、分页——直接用 `DataSource` 需要自己处理所有这些
- Nop 平台的 18 种方言（MySQL/PostgreSQL/Oracle/H2/DM/DB2/MariaDB/DuckDB 等）已经通过 `.dialect.xml` 定义完毕，不需要为每种数据库写适配代码
- `nop-batch-jdbc` 已经验证了这个模式——同一个 `JdbcBatchConsumerProvider` 在所有数据库上工作

**拒绝了什么**：
- 直接 `DataSource.getConnection()` + 手写 SQL——违反 Nop 平台规范，维护成本高，只支持 MySQL
- ORM（`IOrmTemplate`）——checkpoint 存储只有一张简单表，不需要 ORM 的 session/cache 开销

### D2：为什么 WindowedStreamImpl 需要一个 Factory 接口而非直接依赖 runtime

**选了什么**：core 模块定义 `WindowOperatorFactory` 接口，runtime 模块提供实现，通过注册机制连接。

**为什么**：
- 依赖方向必须是 `runtime → core`，不能反向
- core 的 `WindowedStreamImpl` 需要创建 `WindowOperator`（在 runtime 中）
- Factory 接口模式保持了依赖的单向性

**拒绝了什么**：
- 把 WindowOperator 移到 core——违反分层设计，core 不应包含重型算子实现
- 在 WindowedStreamImpl 中抛异常让用户自己构建——当前状态，不可接受

### D3：为什么 Barrier 注入必须改为数据流内元素

**选了什么**：Barrier 作为数据流内的特殊元素注入，而非从外部线程干预。

**为什么**：
- 当前实现从 `ScheduledExecutorService` 线程调用 `source.injectBarrier()`，与 source 算子的 `run()` 线程存在竞争
- 数据记录和 barrier 处理会不确定地交错，违反"barrier 前的数据在 barrier 之前处理完"的核心保证
- Flink 的做法是 barrier 作为流元素在 source 的 `collect()` 调用中自然注入——nop-stream 应遵循同一模式

**拒绝了什么**：
- 对 source 算子加锁——增加复杂度，降低吞吐
- 单线程假设——不符合"可分布式执行"的目标定位
