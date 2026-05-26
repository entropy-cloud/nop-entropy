# nop-stream 组件分解与开发路线

> Status: active
> Updated: 2026-05-26（基于 live repo 审计更新：已完成项标记 ✅，剩余工作重写为动态规划模式）
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

### 2.2 分布式运行时组件

> **Updated: 2026-05-24** — Plan 47 已实现核心分布式执行框架。

| # | 组件 | 对应模块 | 职责 | 状态 |
|---|------|---------|------|------|
| D1 | **DeploymentMode + IStreamExecutionDispatcher** | nop-stream-core (execution 包) | 部署模式枚举 + 执行分发 SPI 接口 | ✅ 已实现 |
| D2 | **SubtaskTask** | nop-stream-core (execution 包) | 分布式模式下按 Subtask 实例执行的 Task 变体 | ✅ 已实现 |
| D3 | **IStreamTaskRpcService** | nop-stream-runtime (rpc 包) | TaskManager 暴露给 Coordinator 的强类型控制面接口 | ✅ 已实现 |
| D4 | **IStreamCoordinatorRpcService** | nop-stream-runtime (rpc 包) | Coordinator 暴露给 TaskManager 的强类型控制面接口 | ✅ 已实现 |
| D5 | **TaskManager** | nop-stream-runtime (taskmanager 包) | 管理本节点 TaskExecutor，实现 IStreamTaskRpcService | ✅ 已实现 |
| D6 | **JobCoordinator** | nop-stream-runtime (coordinator 包) | 持有 canonical plan，分发 subtask，实现 IStreamCoordinatorRpcService | ✅ 已实现 |
| D7 | **EmbeddedDistributedExecutor** | nop-stream-runtime (execution 包) | 嵌入式分布式执行器，创建 N 个 TaskManager + JobCoordinator | ✅ 已实现 |
| D8 | **InMemoryClusterRegistry** | nop-stream-runtime (cluster 包) | 嵌入式模式下的集群注册表（CoordinatorInfo、NodeInfo、LeaseInfo） | ✅ 已实现 |
| D9 | **RemoteGraphExecutionPlanBuilder** | nop-stream-runtime (execution 包) | 构建跨节点的 RemoteGraphExecutionPlan | ✅ 已实现 |

**设计要点**：
- 不使用适配器模式——`TaskManager IS-A IStreamTaskRpcService`，`JobCoordinator IS-A IStreamCoordinatorRpcService`
- 嵌入式分布式模式下，TaskManager 和 JobCoordinator 在同一 JVM 内通过直接 Java 调用通信
- 真正分布式部署时，由 Nop RPC 框架为接口生成远程代理
- SourceEnumerator（split-based source）延迟到后续 Plan 实现

### 2.3 连接器与集成组件

| # | 组件 | 对应模块 | 职责 | 依赖 |
|---|------|---------|------|------|
| C7 | **连接器** | nop-stream-connector | Source/Sink 适配：nop-batch Loader/Consumer、IMessageService、Debezium CDC | 依赖 C1, nop-batch-core |
| C8 | **欺诈检测示例** | nop-stream-fraud-example | 端到端使用范例 | 依赖 C6 |

### 2.4 规划组件（保留 pom 占位）

| # | 组件 | 对应模块 | 规划职责 |
|---|------|---------|---------|
| P1 | **公共 API** | nop-stream-api | 从 core 分离纯接口，使 runtime/cep 不依赖 core 实现 |
| P2 | **Checkpoint 独立** | nop-stream-checkpoint | 从 runtime 分离 checkpoint 协调器和存储 |
| P3 | **Flink 后端** | nop-stream-flink | 将 Transformation 映射到 Flink DataStream |
| P4 | **流编排** | nop-stream-flow | 基于 XLang DSL 的声明式流任务编排 |

### 2.5 依赖关系

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
- `assignTimestampsAndWatermarks()` 在 `execute()` 中通过图模型路径的 StreamGraph 自动插入 TimestampsAndWatermarksOperator

#### C2 编译管线

**已完成度**：高。四层编译（Transformation → StreamGraph → JobGraph → ExecutionPlan）和算子链化已实现。

**待完善**：
- StreamGraphGenerator 中的 `PartitionOperatorFactory.createStreamOperator()` 返回 null，这个设计需要改为在 JobGraphGenerator 阶段正确处理分区节点
- 多 sink 管线、union 管线测试
- 跨 Task 数据交换端到端验证
- `KeySelectorPartitioner` 多并行度 key hash 分区

#### C3 算子运行时

**已完成度**：高。内置算子（map/filter/flatMap/source/sink）完整。WindowOperator 已修复。

**已修复**（Plan 51）：
- ✅ `WindowOperator.addWindowElement()` 累加器类型腐蚀 — 已通过 `createAccumulatorForWindow()` 修复
- ✅ `MergingWindowSet.persist()` — 已完整实现（比较 initialMapping 后持久化变更）
- ✅ `WindowAggregationOperator.getSimpleAccumulator()` — 对 ReducingStateDescriptor 正常工作
- ✅ `SimpleStreamOperatorFactory.createStreamOperator()` — 已改为序列化深拷贝
- ✅ `WindowedStreamImpl.apply/aggregate/reduce` — 已全部实现

#### C4 状态管理

**已完成度**：高。接口完整，Memory 实现可用。

**设计要求**：
- 接口层次（`IStateBackend → IKeyedStateBackend → IInternalStateBackend`）保持不变——这是正确的分层，为后续 Redis/RocksDB 预留扩展点
- Keyed state 通过 `(namespace, key)` 复合键隔离，每个算子有独立 IKeyedStateBackend 实例

#### C5 Checkpoint

**已完成度**：中高。核心流程已实现，CheckpointPlan + CheckpointPlanBuilder 已存在。

**已修复**：
- ✅ `JdbcCheckpointStorage` 已改用 `IJdbcTemplate`
- ✅ CheckpointPlan + CheckpointPlanBuilder 已实现
- ✅ Barrier 注入已改为 source-pull 模式（`LinkedBlockingQueue` + source 线程 `injectPendingBarrier`）
- ✅ Keyed state 碰撞已通过 `(namespace, key)` 复合键解决

**待完善**（需要审计验证）：
- CheckpointCoordinator 是否已通过 CheckpointPlanBuilder 使用 CheckpointPlan
- 多算子链 keyed state 快照/恢复端到端测试
- 并行模式 checkpoint 正确性测试
- 多次 checkpoint 循环后恢复验证
- GraphModelCheckpointExecutor 代码重复清理

#### C6 CEP 引擎

**已完成度**：高。NFA、SharedBuffer、Pattern API、声明式模型都是 nop-stream 中最成熟的部分。

**待完善**：
- `CepOperator` 使用自建 `SimpleKeyedStateStore`，需对接 C4 的标准 `IKeyedStateBackend`
- 需要实现 `DslModelParser` 对接 `pattern.xdef`——当前 XDEF schema 和 `_gen` 模型类存在但无加载器
- runtime → cep 幽灵依赖需移除

#### C7 连接器

**已完成度**：高。5 个适配器已实现。

**设计正确**：通过 `IBatchLoader`/`IBatchConsumer` 桥接 nop-batch，一个适配器覆盖所有数据源。CDC 和消息队列通过 `IMessageService` 独立适配。

---

## 4. 开发方法：审计-规划-执行循环

**不在本文档中预设完整的阶段工作项清单**。原因是：经过 Plans 42-51 的大量迭代，roadmap 中描述的很多 bug 已经修复，预设的静态工作项与实际代码状态严重脱节。

**正确的做法是动态规划**：

1. **审计当前代码**：运行构建和测试，检查各组件的实际完成度
2. **拟定一个 plan**：基于审计结果，只规划**当前最紧迫的一个可交付单元**
3. **执行 plan**：编码、测试、验证
4. **plan 完成后，回到步骤 1**：重新审计代码，重新评估剩余工作，拟定下一个 plan
5. **重复直到所有设计目标达成**

### 审计检查清单

每次规划前，用以下检查清单评估各组件的真实状态：

#### C1 流式 API
- [ ] `DataStreamImpl` / `KeyedStreamImpl` / `WindowedStreamImpl` 所有公共方法不抛 UnsupportedOperationException
- [ ] `assignTimestampsAndWatermarks()` 在图模型路径中自动插入

#### C2 编译管线
- [ ] `SimpleStreamOperatorFactory` 为每个并行实例创建独立拷贝
- [ ] 多 sink 管线正确编译（多个 JobVertex）
- [ ] Union 管线正确编译
- [ ] 跨 Task 数据交换端到端正确
- [ ] `KeySelectorPartitioner` 多并行度 key hash 分区正确

#### C3 算子运行时
- [ ] `WindowOperator` 累加器在首个元素时正确创建
- [ ] `MergingWindowSet.persist()` 持久化合并窗口映射
- [ ] 所有算子的生命周期方法（setup/open/snapshotState/initializeState/finish）正常工作

#### C4 状态管理
- [ ] `IKeyedStateBackend` 按 (namespace, key) 复合键隔离状态
- [ ] 每个算子有独立的 IKeyedStateBackend 实例
- [ ] 状态快照/恢复 round-trip 正确

#### C5 Checkpoint
- [ ] `CheckpointCoordinator` 使用 `CheckpointPlanBuilder` 生成的 `CheckpointPlan`
- [ ] `JdbcCheckpointStorage` 使用 `IJdbcTemplate`（不是 `java.sql.Connection`）
- [ ] 多算子链 keyed state 快照/恢复不互相覆盖
- [ ] 并行模式 checkpoint 正确
- [ ] 多次 checkpoint 循环后恢复验证

#### C6 CEP
- [ ] `CepOperator` 使用标准 `IKeyedStateBackend`（不是自建 `SimpleKeyedStateStore`）
- [ ] 可从 `.pattern.xml` 加载 CEP Pattern（通过 `DslModelParser`）
- [ ] CEP 状态 checkpoint/恢复正确

#### C7 连接器
- [ ] `BatchLoaderSourceFunction` 支持资源清理和 checkpoint 恢复
- [ ] `BatchConsumerSinkFunction` 支持 TwoPhaseCommit（exactly-once）

#### C8 示例
- [ ] fraud-example 无条件恒真、硬编码等 bug
- [ ] 展示窗口 + CEP 组合用法

#### 整体
- [ ] `./mvnw clean install -pl nop-stream -am -T 1C` 全量构建通过
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全量测试通过
- [ ] 端到端管线（source → 算子 → sink）从入口到出口完整跑通

### 规划优先级指引

当审计发现多个待完善项时，按以下优先级排序：

1. **构建/测试失败** — 最高优先级，阻塞一切
2. **正确性 bug** — 状态丢失、数据错误等
3. **接线缺失** — 组件存在但未被调用（空壳问题）
4. **功能补全** — 接口存在但实现不完整
5. **测试覆盖** — 功能工作但缺少测试
6. **代码清理** — 幽灵依赖、重复代码等

---

## 5. 已知技术债

| 问题 | 状态 | 优先级 |
|------|------|--------|
| `runtime` 依赖 `cep`（零代码引用） | 未修复 | P2 |
| 4 个空壳模块（api/checkpoint/flink/flow） | 保留占位 | P3 |
| `JdbcCheckpointStorage` 已改用 `IJdbcTemplate` | ✅ 已修复 | — |
| `WindowOperator` 累加器类型腐蚀 | ✅ 已修复（Plan 51） | — |
| `MergingWindowSet.persist()` 空操作 | ✅ 已修复（Plan 51） | — |
| `SimpleStreamOperatorFactory` 返回同一实例 | ✅ 已修复（Plan 51） | — |
| `WindowedStreamImpl` apply/aggregate/reduce | ✅ 已修复（Plan 51） | — |
| Barrier 注入线程安全 | ✅ 已修复（source-pull 模式） | — |
| Keyed state 碰撞 | ✅ 已修复（(namespace, key) 复合键） | — |

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
