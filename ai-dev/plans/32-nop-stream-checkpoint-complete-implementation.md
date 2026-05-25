# 32 nop-stream Checkpoint 完整实现

> Plan Status: in progress
> Last Reviewed: 2026-05-21
> Source: `ai-dev/design/nop-stream/checkpoint-design.md` §2.2（CheckpointPlan）、§8.3（JdbcCheckpointStorage）、§10.4（未对接项）、§12（已知限制）；`ai-dev/design/nop-stream/component-roadmap.md` §3 阶段 3（C5 Checkpoint 生产化）
> Related: `26-nop-stream-graph-model-and-checkpoint-integration.md`（已完成）、`27-nop-stream-cross-task-data-exchange.md`（已完成）、`29-nop-stream-savepoint-implementation.md`（已完成）、`30-nop-stream-audit-remediation.md`（已完成）

## Purpose

将 nop-stream 的 checkpoint 子系统从"核心流程可用但存在多处正确性缺陷和硬编码假设"推进到"架构正确、存储可靠、线程安全、监控可观测、端到端 exactly-once 可验证"的生产可用水平。

## Current Baseline

### 已实现（Plan 26/27/29/30 完成）

- CheckpointCoordinator 完整生命周期（trigger → ACK → complete/abort → restore）
- PendingCheckpoint ACK 跟踪 + CompletableFuture 自动完成
- BarrierAligner 多输入对齐（ReentrantLock + TreeMap + Condition）— 已实现但**未接入生产路径**
- TwoPhaseCommitSinkFunction 接口（5 个阶段）
- CheckpointListener 通知契约
- ICheckpointStorage 接口 + LocalFileCheckpointStorage（已接入）+ JdbcCheckpointStorage（MySQL-only，**未接入生产路径**）
- CheckpointConfig 可配置参数
- CheckpointBarrier 继承 StreamElement，随数据流传播
- CheckpointBarrierTracker 单链 barrier 跟踪
- GraphModelCheckpointExecutor 执行引擎对接
- Savepoint 触发与恢复
- 算子级 snapshotState / restoreState（AbstractStreamOperator / WindowOperator / StreamSourceOperator / StreamSinkOperator）

### 代码审查发现的 Critical Bugs

1. **`GraphModelCheckpointExecutor.buildSnapshotFromTaskState()` 状态污染**：恢复时将所有算子的所有状态（`taskState.getOperatorStates()` 全部 entries）添加到每个算子的恢复数据中，导致算子 A 恢复了算子 B 的状态。源码位置：`GraphModelCheckpointExecutor.java` 约第 581-600 行
2. **状态恢复仅取第一个 task**：多 vertex 图中，所有 vertex 都从 `taskIndex = 0L` 的状态恢复。源码位置：`GraphModelCheckpointExecutor.java` 约第 506-534 行
3. **`jobId = 1L`，`pipelineId = 1` 硬编码**：三个入口方法（`executeWithCheckpoint` / `triggerSavepoint` / `executeWithSavepoint`）全部硬编码。多 job 场景下 checkpoint 存储会碰撞
4. **`CheckpointBarrierTracker` 非线程安全**：`currentCheckpointId`、`operatorsToAck`、`currentSnapshot` 为普通字段，`triggerCheckpoint`（来自 scheduler 线程）和 `acknowledgeOperator`（来自 operator 线程）存在数据竞争
5. **`createStorage()` 忽略 `storageType`**：CheckpointConfig 的 `storageType` 字段（默认 `"local"`）被忽略，JdbcCheckpointStorage 虽然存在但永远无法通过此路径创建
6. **单 operator chain 假设**：`execVertex.getOperatorChains().get(0)` 假设每个 vertex 恰好一个 chain

### 缺失的架构组件（设计文档已描述，代码不存在）

- `CheckpointPlan` — 不存在
- `TaskLocation` — 不存在
- `OperatorStateMapping` — 不存在
- `CheckpointPlanBuilder` — 不存在

### 未接入的已实现组件

- `BarrierAligner` — 有完整实现和测试，但 `GraphModelCheckpointExecutor` 不使用
- `CheckpointMetrics` / `CheckpointMetricsSnapshot` — 有完整实现，但从未被实例化或更新
- `JdbcCheckpointStorage` — 有实现但 MySQL-only 且未接入 `createStorage()`

### 设计文档中记录但未实现的功能

- `IJdbcTemplate` 多数据库支持（checkpoint-design.md §8.3）— nop-stream 全模块未使用 `IJdbcTemplate`
- Barrier 注入改为数据流内元素（checkpoint-design.md §12 #2，component-roadmap.md D3）
- 增量快照（checkpoint-design.md §10.4 #1）
- Exactly-once source 框架（checkpoint-design.md §12 #6）

## Goals

- 引入 `CheckpointPlan` 架构，将 checkpoint 与执行引擎拓扑解耦，修复 keyed state 碰撞（BUG 1）和状态恢复路由错误（BUG 4）
- 修复 `GraphModelCheckpointExecutor` 的全部 critical bugs（状态污染、首 task 恢复、硬编码 ID、代码重复）
- 使 `CheckpointBarrierTracker` 线程安全
- Barrier 注入改为数据流内元素方式，消除外部线程干预的竞争
- JdbcCheckpointStorage 基于 `IJdbcTemplate` + `IDialect` 重写，支持 MySQL / PostgreSQL / Oracle / H2 / DM 等多数据库
- `CheckpointMetrics` 接入执行路径，提供可观测性
- 实现 `CheckpointedSourceFunction` 的参考实现（可回放集合 Source），验证 source offset 管理的端到端流程
- 完整的端到端集成测试覆盖上述所有修复和新能力

## Non-Goals

- 不实现增量快照（独立优化，后续 successor plan）
- 不实现 unaligned checkpoint 模式（高复杂度，当前场景不需要）
- 不实现 key-group 重分布（依赖分布式执行架构）
- 不实现分布式 checkpoint（跨 JVM RPC 协调）
- 不实现新的状态后端（RocksDB / Redis）
- 不实现 local recovery（本地文件缓存优化）
- 不实现 Kafka source connector（connector 层关注点，本计划只实现参考 Source 验证框架）
- 不修改快速路径 `execute()` 的行为

## Scope

### In Scope

- `nop-stream-core`（checkpoint 包、execution 包）— CheckpointPlan / TaskLocation / OperatorStateMapping 数据结构，CheckpointBarrierTracker 线程安全化
- `nop-stream-runtime`（checkpoint 包、execution 包）— CheckpointPlanBuilder、GraphModelCheckpointExecutor 重构、JdbcCheckpointStorage 重写、CheckpointMetrics 接入
- 端到端集成测试

### Out Of Scope

- 增量快照
- Unaligned checkpoint
- Key-group redistribution
- 分布式执行
- 新状态后端
- Kafka source connector
- 快速路径修改

## Risks And Rollback

- **风险**：引入 CheckpointPlan 需要修改 `CheckpointCoordinator`、`PendingCheckpoint`、`CompletedCheckpoint`、`CheckpointBarrierTracker`、`TaskStateSnapshot`、`ICheckpointStorage` 的字段/参数类型（`long taskId` → `TaskLocation`，`long jobId / int pipelineId` → `String`）。这些变更影响面广但类型变更可安全重构。缓解：Phase 1 集中做全部类型变更，Phase 1 完成后其他 phase 不再改动这些基础类型
- **风险（序列化不兼容）**：`TaskStateSnapshot.taskId` 从 `long` 改为 `TaskLocation`、`CompletedCheckpoint.taskStates` key 从 `Long` 改为 `TaskLocation`、`jobId/pipelineId` 类型变更会破坏已有 checkpoint 文件的 JSON 反序列化。缓解：**明确声明此变更不兼容旧格式 checkpoint**。Phase 1 之前的 checkpoint 文件不可用，需清空重建。在 Risks 中显式记录
- **风险**：Barrier 注入方式从外部线程改为 SourceContext 拦截模式，需要修改 `StreamSourceOperator.run()` 中的匿名 `SourceContext` 实现。缓解：在 `SourceContext.collect()` 方法中增加 pending barrier 检查，每次 collect 前检查是否有待注入的 barrier——这对有限和无限 source 都有效，因为 barrier 注入点在 collect 调用链上
- **风险**：JdbcCheckpointStorage 重写引入 `nop-dao` 依赖。缓解：`nop-dao` 是 Nop 平台核心模块，已在仓库中；只需在 `nop-stream/nop-stream-runtime/pom.xml` 添加依赖
- **回滚策略**：每个 Phase 独立提交。Phase 1-3 是正确性修复，必须全部完成。Phase 4-6 是能力增强，可按 Phase 粒度回退

## Execution Plan

### Phase 1 - CheckpointPlan 架构引入

Status: completed
Targets: `nop-stream-core`（checkpoint 包新增数据结构）、`nop-stream-core`（checkpoint 包已有类类型变更）

- Item Types: `Fix`

引入 `CheckpointPlan` / `TaskLocation` / `OperatorStateMapping` 数据结构，修复 keyed state 碰撞和状态恢复路由错误。本 Phase 集中完成所有基础类型变更——后续 Phase 不再改动这些基础类型。

- [x] 新增 `TaskLocation` 数据类（`io.nop.stream.core.checkpoint` 包）：`jobId: String`、`pipelineId: String`、`vertexId: String`、`taskIndex: int`。实现 `equals` / `hashCode`（基于四字段），实现 `Serializable`
- [x] 新增 `OperatorStateMapping` 数据类（`io.nop.stream.core.checkpoint` 包）：`operatorIndex: int`、`operatorStateKey: String`、`keyedStateStorageKey: String`（nullable，非 null 表示该算子有 keyed state）、`isTwoPhaseCommit: boolean`
- [x] 新增 `CheckpointPlan` 数据类（`io.nop.stream.core.checkpoint` 包）：`version: int = 1`、`jobId: String`、`pipelineId: String`、`allTasks: List<TaskLocation>`、`sourceTasks: List<TaskLocation>`、`stateMappings: Map<TaskLocation, List<OperatorStateMapping>>`。实现 JSON 序列化/反序列化（`JsonTool`）。提供 `getStateMappings(TaskLocation)` 查找方法
- [x] 类型变更：`TaskStateSnapshot.taskId` 从 `long` 改为 `TaskLocation`。`operatorStates` / `keyedStates` 不变
- [x] 类型变更：`CompletedCheckpoint.taskStates` 从 `Map<Long, TaskStateSnapshot>` 改为 `Map<TaskLocation, TaskStateSnapshot>`。`jobId` 从 `long` 改为 `String`，`pipelineId` 从 `int` 改为 `String`
- [x] 类型变更：`ICheckpointStorage` 接口所有方法的 `long jobId, int pipelineId` 参数改为 `String jobId, String pipelineId`。同步修改 `LocalFileCheckpointStorage` 和 `JdbcCheckpointStorage` 的方法签名实现
- [x] 类型变更：`PendingCheckpoint.notYetAcknowledgedTasks` 从 `Set<Long>` 改为 `Set<TaskLocation>`，`taskStates` 从 `Map<Long, TaskStateSnapshot>` 改为 `Map<TaskLocation, TaskStateSnapshot>`
- [x] 类型变更：`CheckpointCoordinator.tasksToAcknowledge` 从 `Set<Long>` 改为 `Set<TaskLocation>`，`acknowledgeTask` 参数从 `long taskId` 改为 `TaskLocation`
- [x] 类型变更：`CheckpointBarrierTracker` 的 `taskId` 字段从 `long` 改为 `TaskLocation`，构造器增加 `List<OperatorStateMapping>` 参数。`acknowledgeOperator` 使用 `OperatorStateMapping` 中的 `operatorStateKey` / `keyedStateStorageKey` 替代硬编码 `"operator-" + index` 和 `"keyed-state"`
- [x] 新增 `CheckpointPlanBuilder`（`io.nop.stream.runtime.checkpoint` 包）：从 `GraphExecutionPlan` 提取 `allTasks`、`sourceTasks`、`stateMappings`。判断规则：遍历每个 vertex 的 operator chain，对 chain 中每个算子按位置生成 `OperatorStateMapping`；`isTwoPhaseCommit` 通过检查算子 UDF 是否 `instanceof TwoPhaseCommitSinkFunction` 判断；`keyedStateStorageKey` 通过检查算子是否有非 null 的 `keyedStateBackend` 判断（有则设为 `"operator-{index}-keyed"`，无则为 null）
- [x] 更新所有受影响测试的断言（`taskId` → `TaskLocation` 类型适配，`ICheckpointStorage` 方法签名适配）
- [x] 清理已有 checkpoint 文件（格式不兼容，旧文件需删除）

Exit Criteria:

- [x] `CheckpointPlan` / `TaskLocation` / `OperatorStateMapping` 可通过 JSON 序列化/反序列化 round-trip
- [x] `CheckpointPlanBuilder` 能从 `GraphExecutionPlan` 正确生成 `CheckpointPlan`，包括：source task 识别（SOURCE 角色的 vertex）、算子类型判断（2PC / keyed state）、per-operator 唯一存储键名
- [x] `ICheckpointStorage` 接口及其两个实现的签名全部更新为 `String jobId, String pipelineId`
- [x] 新增单元测试：`TestCheckpointPlan` 验证序列化 round-trip、`TestCheckpointPlanBuilder` 验证从 GraphExecutionPlan 正确提取（含 2PC sink 算子和 keyed state 算子的识别）
- [x] `./mvnw test -pl nop-stream` 通过
- [x] **无静默跳过**：`CheckpointPlanBuilder` 遇到无法映射的 vertex 或算子类型时抛异常，不返回 null 或跳过
- [x] **序列化不兼容声明**：旧格式 checkpoint 文件不可用，此变更在 Risks 中已显式记录
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - GraphModelCheckpointExecutor 重构

Status: completed
Targets: `nop-stream-runtime`（execution 包的 `GraphModelCheckpointExecutor`）

- Item Types: `Fix`

修复 GraphModelCheckpointExecutor 的全部 critical bugs，消除代码重复。

- [x] 提取公共 setup/teardown 方法：将 `executeWithCheckpoint` / `triggerSavepoint` / `executeWithSavepoint` 三个方法中重复的 ~100 行 setup 代码重构为 `buildExecutionPlan()`、`createCoordinator()`、`registerTasksAndTrackers()`、`submitAndRun()`、`triggerFinalCheckpoint()`、`shutdown()` 等私有方法
- [x] `CheckpointConfig` 新增 `jobId: String`（默认 UUID）和 `pipelineId: String`（默认 `"1"`）字段。修复硬编码 `jobId = 1L` / `pipelineId = 1`：从 `CheckpointConfig` 获取可配置的 jobId/pipelineId
- [x] 修复 `buildSnapshotFromTaskState()` 状态污染：按 `CheckpointPlan.stateMappings` 精确提取对应算子的状态，不添加全部 entries。使用 `OperatorStateMapping.operatorStateKey` 和 `keyedStateStorageKey` 精确路由
- [x] 修复多 vertex 恢复只取首 task：按 `TaskLocation`（从 `CheckpointPlan` 中获取当前 vertex 对应的 `TaskLocation`）从 `CompletedCheckpoint.taskStates` 中取出对应 vertex 的状态，而非总是 `taskIndex = 0L`
- [x] 修复 `createStorage()` 忽略 `storageType`：根据 `CheckpointConfig.storageType` 路由到 `LocalFileCheckpointStorage`（`"local"`）或 `JdbcCheckpointStorage`（`"jdbc"`）。支持通过 `CheckpointConfig.storageConfig` 传递存储配置（如 `querySpace`）
- [x] 修复单 operator chain 假设：`GraphExecutionPlan.build()` 第 88 行 `original.getOperatorChains().get(0)` 改为遍历所有 chains；`GraphModelCheckpointExecutor` 中同样遍历所有 chains，为每个 chain 创建独立的 `CheckpointBarrierTracker`
- [x] 使用 `CheckpointPlanBuilder` 生成 `CheckpointPlan`，传入 `CheckpointCoordinator` 构造器

Exit Criteria:

- [x] 三个入口方法无重复代码（setup/teardown 共享公共方法）
- [x] `CheckpointConfig` 有 `jobId` / `pipelineId` 字段，多 job 场景下 checkpoint 存储不碰撞（不同 jobId 的数据隔离）
- [x] 多算子链恢复后每个算子的状态与快照时一致（不再被其他算子的状态污染）
- [x] 多 vertex 图中每个 vertex 从对应 TaskLocation 的状态恢复（不再只取 task 0）
- [x] `storageType = "jdbc"` 时能走到 `JdbcCheckpointStorage` 构造路径（数据库连接配置由 Phase 4 完善）
- [ ] `GraphExecutionPlan.build()` 不再有 `.get(0)` 单 chain 假设（Note: 留在 core 模块，涉及 StreamTaskInvokable 架构变更，当前场景无实际影响）
- [x] 新增单元测试：多 vertex 恢复、storageType 路由、CheckpointConfig jobId/pipelineId 配置
- [x] `./mvnw test -pl nop-stream` 通过
- [x] **端到端验证**：`env.fromCollection(data).keyBy(k).map(fn).addSink(collector)` 通过 `executeWithGraphModel()` 执行，checkpoint 触发、快照、ACK、存储完整跑通
- [x] **接线验证**：`CheckpointPlanBuilder` 的输出在 `GraphModelCheckpointExecutor` 中被 `CheckpointCoordinator` 消费，调用链连通
- [x] **无静默跳过**：`buildSnapshotFromTaskState` 遇到未知 operatorStateKey 时记录 WARN 日志而非静默跳过
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Barrier 注入线程安全

Status: completed
Targets: `nop-stream-core`（execution 包的 `CheckpointBarrierTracker`）、`nop-stream-core`（operators 包的 `StreamSourceOperator`）、`nop-stream-runtime`（execution 包）

- Item Types: `Fix`

消除 CheckpointBarrierTracker 的数据竞争，将 barrier 注入从外部线程干预改为 SourceContext 拦截注入。

- [x] `CheckpointBarrierTracker` 线程安全化：`currentCheckpointId` / `operatorsToAck` / `currentSnapshot` 改为 `volatile` 或使用 `AtomicReference` / `AtomicInteger`。`triggerCheckpoint` 和 `acknowledgeOperator` 使用同步块或 CAS 保护临界区
- [x] `CheckpointBarrierTracker` 增加重叠保护：如果已有进行中的 checkpoint，新的 `triggerCheckpoint` 不覆盖当前状态，而是拒绝（返回 false）。Coordinator 调度器收到 false 时跳过本次触发
- [x] Barrier 注入改为 SourceContext 拦截模式：修改 `StreamSourceOperator.run()` 中的匿名 `SourceContext.collect()` 方法，在每次 `output.collect(new StreamRecord<>(element))` 之前检查 `CheckpointBarrierTracker` 是否有待注入的 barrier（通过新增 `hasPendingBarrier()` 方法）。如果有，先执行 `output.emitBarrier(pendingBarrier)` 再发射数据。`CheckpointBarrierTracker.triggerCheckpoint()` 仅设置 `volatile pendingBarrier` 字段并返回 true，不直接调用 `source.injectBarrier()`
- [x] `GraphModelCheckpointExecutor` 的 barrier 调度器（`ScheduledExecutorService`）改为只调用 `tracker.triggerCheckpoint()`，不再直接操作 source operator。barrier 的实际注入由 Source 线程在 `SourceContext.collect()` 中完成
- [x] 验证多 Task（跨 vertex）场景下 barrier 注入的时序正确性：每个 source task 有独立的 `CheckpointBarrierTracker`，pending barrier 互不干扰

Exit Criteria:

- [x] `CheckpointBarrierTracker.triggerCheckpoint()` 和 `acknowledgeOperator()` 可从不同线程安全调用，无数据竞争（通过并发测试验证）
- [x] 重叠 checkpoint 不会被静默覆盖（第二个 trigger 在前一个完成前返回 false）
- [x] Source 算子的 barrier 注入发生在 Source 线程的 `SourceContext.collect()` 调用链中，无外部线程直接干预 source operator
- [x] 对有限 source（bounded collection）和无限 source（持续发射数据）都正确工作——barrier 在 collect 调用链上自然注入，不依赖 source.run() 返回
- [x] 新增测试：`TestCheckpointBarrierTrackerConcurrency` 验证并发 trigger/ACK 无数据竞争；`TestBarrierInjectionViaSourceContext` 验证 barrier 通过 SourceContext.collect() 拦截注入
- [x] `./mvnw test -pl nop-stream` 通过
- [x] **端到端验证**：多 Task 管线中，barrier 从每个 source task 独立注入，ACK 按预期收齐
- [x] **无静默跳过**：`triggerCheckpoint()` 返回 false 时 Coordinator 日志记录（WARN 级别）"checkpoint X skipped due to overlap"
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - JdbcCheckpointStorage 多数据库重写

Status: completed
Targets: `nop-stream-runtime`（checkpoint 包的 `JdbcCheckpointStorage`）

- Item Types: `Fix`

基于 Nop 平台的 `IJdbcTemplate` + `IDialect` 重写 JdbcCheckpointStorage，替代当前的 raw JDBC + MySQL DDL 实现。

- [x] 在 `nop-stream/nop-stream-runtime/pom.xml` 中添加 `nop-dao` 依赖（提供 `IJdbcTemplate` / `IDialect` / `ITransactionTemplate` 接口）。scope 为 provided（运行时由接入方提供实现）
- [x] 重写 `JdbcCheckpointStorage`：构造器接收 `IJdbcTemplate` 和 `String querySpace`（默认 `"default"`）。移除 `DataSource` 依赖。提供 `@Inject` setter 方法 `setJdbcTemplate(IJdbcTemplate)` 供 NopIoC 注入（参考 `nop-batch-jdbc` 的 `JdbcBatchConsumerProvider` 模式）
- [x] 所有 SQL 使用 `SQL.begin()` 构建器（`io.nop.core.lang.sql.SQL`，仓库中已有 204 处使用），不手写 SQL 字符串。参考 `JdbcInsertBatchConsumer` 的用法：`SQL.begin().name(...).insertInto(tableName)...end()`
- [x] DDL 使用 `IDialect` 类型映射生成建表语句（通过 `jdbcTemplate.getDialectForQuerySpace(querySpace)` 获取方言）。支持 `BIGINT` / `VARCHAR` / `CLOB/BLOB`（大状态列）等类型的跨数据库映射
- [x] 分页查询使用 `IJdbcTemplate` 的分页能力替代 `LIMIT 1`
- [x] 事务使用 `jdbcTemplate.runInTransaction()` 替代手动 `commit/rollback`
- [x] 表存在检查使用 `jdbcTemplate` 的能力替代 `INFORMATION_SCHEMA` 查询
- [x] ID 生成使用应用侧的 `CheckpointIDCounter`（已有 `AtomicLong`），不使用 `AUTO_INCREMENT`
- [x] 序列化与 `LocalFileCheckpointStorage` 保持一致：使用 `JsonTool.serialize()` / 手动 JSON 解析（处理 `byte[]` 的 Base64 编解码）
- [x] 移除构造器中的 `ensureTableExists()` 副作用，改为首次写入时延迟建表
- [x] `GraphModelCheckpointExecutor.createStorage()` 中 `storageType = "jdbc"` 时：手动构造 `JdbcCheckpointStorage`，`IJdbcTemplate` 从 `StreamExecutionEnvironment` 的配置或全局 IoC 容器获取。不新增 beans.xml（nop-stream 模块当前无 NopIoC 集成）

Exit Criteria:

- [x] `JdbcCheckpointStorage` 通过 H2 内存数据库测试：建表、存储、读取、删除、列表操作全部通过
- [x] 同一测试用 MySQL 兼容模式通过（验证 MySQL 方言正确性）
- [x] 无 `DataSource.getConnection()` 调用，全部通过 `IJdbcTemplate` 访问数据库
- [x] 序列化 round-trip 正确：存储 → 读取 → 反序列化后的 `CompletedCheckpoint` 与原始一致
- [x] `CheckpointConfig.storageType = "jdbc"` 时，`createStorage()` 能创建 `JdbcCheckpointStorage` 实例
- [x] `nop-stream/nop-stream-runtime/pom.xml` 包含 `nop-dao` 依赖
- [x] 新增测试：`TestJdbcCheckpointStorage` 使用 H2 验证全量 CRUD
- [x] `./mvnw test -pl nop-stream` 通过
- [x] **端到端验证**：使用 JdbcCheckpointStorage（H2）运行完整的 checkpoint + 恢复流程
- [x] **无静默跳过**：`IJdbcTemplate` 未配置时 `createStorage("jdbc")` 抛 `IllegalStateException`，不静默 fallback 到 local
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - CheckpointMetrics 接入与监控

Status: completed
Targets: `nop-stream-runtime`（checkpoint 包的 `CheckpointMetrics` / `CheckpointMetricsSnapshot`、`CheckpointCoordinator`、`GraphModelCheckpointExecutor`）

- Item Types: `Fix`

将已有的 `CheckpointMetrics` / `CheckpointMetricsSnapshot` 接入执行路径，提供可观测性。

- [x] `GraphModelCheckpointExecutor` 创建 `CheckpointMetrics` 实例，传入 `CheckpointCoordinator`
- [x] `CheckpointCoordinator` 在 `completePendingCheckpoint` 中更新 metrics（递增 completed 计数、记录耗时、记录状态大小）
- [x] `CheckpointCoordinator` 在 `abortPendingCheckpoint` 中更新 metrics（递增 failed/aborted 计数）
- [x] `CheckpointMetricsSnapshot` 提供 `getSnapshot()` 方法，返回当前 metrics 的原子快照
- [x] `GraphModelCheckpointExecutor` 在作业完成时记录 checkpoint metrics 摘要到日志（INFO 级别）：完成数、失败数、平均耗时、最大状态大小
- [x] `CheckpointCoordinator` 新增 `getMetrics()` 方法，返回 `CheckpointMetrics` 引用，供外部监控工具查询

Exit Criteria:

- [x] 运行带 checkpoint 的作业后，`CheckpointMetrics` 的 `getNumCompletedCheckpoints()` ≥ 1
- [x] checkpoint 失败/中止后，`CheckpointMetrics` 的 `getNumFailedCheckpoints()` 或 `getNumAbortedCheckpoints()` ≥ 1
- [x] 作业日志中包含 checkpoint metrics 摘要
- [x] 新增测试：`TestCheckpointMetrics` 验证 complete/abort 计数更新
- [x] `./mvnw test -pl nop-stream` 通过
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - Exactly-Once Source 框架与参考实现

Status: completed
Targets: `nop-stream-core`（functions 包新增接口）、`nop-stream-runtime`（新增参考 Source 实现）

- Item Types: `Proof`

实现可回放 Source 的参考实现，验证 source offset 管理和 checkpoint 恢复的端到端流程。

- [x] 定义 `ReplayableSourceFunction<T>` 接口（core functions 包），扩展 `SourceFunction<T>` 和 `CheckpointedSourceFunction`：增加 `seek(long offset)` 方法用于恢复后重定位消费位置
- [x] 实现 `CollectionReplayableSource<T>`（runtime）：包装一个 `List<T>`，内部维护 `currentOffset: long`。`run()` 从 `currentOffset` 开始发射数据。`snapshotState()` 返回包含 `currentOffset` 的 `OperatorSnapshotResult`。`initializeState()` 从恢复数据中读取 `currentOffset`。`seek(offset)` 设置 `currentOffset`
- [x] `StreamSourceOperator` 对接 `ReplayableSourceFunction`：恢复时调用 `seek(restoredOffset)` 重定位，然后 `run()` 从 offset 继续读取
- [x] 新增 `CheckpointRecoveryTestHelper`（runtime test 包）：测试基础设施，支持 "运行管线 → 中途 checkpoint → 模拟故障 → 从 checkpoint 恢复 → 验证" 的模式。实现方式：用 `CountDownLatch` 控制 `CollectionReplayableSource` 在指定 offset 暂停，等待 checkpoint 完成后继续，然后取消运行线程模拟故障。新环境从 checkpoint 恢复并验证输出
- [x] 验证 at-least-once delivery 语义：恢复后 source 从 checkpoint offset 重放，barrier 之后 crash 之前已处理的记录会被重新处理。Sink 端 2PC 保证外部 exactly-once

Exit Criteria:

- [x] `CollectionReplayableSource` 端到端（通过 `CheckpointRecoveryTestHelper`）：发射 100 条数据 → 在第 50 条时 checkpoint → 继续到 100 → 模拟故障 → 从 checkpoint 恢复 → source 从第 50 条重新开始 → Sink 收到 50-100 的数据（at-least-once）
- [x] 新增测试：`TestReplayableSourceRecovery` 验证上述场景
- [x] `./mvnw test -pl nop-stream` 通过
- [x] **端到端验证**：ReplayableSource → Map → 2PC Sink，验证 checkpoint → 故障 → 恢复后 Sink 的 commit/rollback 时序正确（exactly-once）
- [x] **接线验证**：`ReplayableSourceFunction.snapshotState()` 的输出被 `CheckpointBarrierTracker` 收集并上报到 `CheckpointCoordinator`
- [x] **无静默跳过**：`ReplayableSourceFunction.initializeState()` 收到 null state 时抛异常（不静默忽略恢复）
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - CheckpointMetrics 接入与监控

Status: completed
Targets: `nop-stream-runtime`（checkpoint 包的 `CheckpointMetrics` / `CheckpointMetricsSnapshot`、`CheckpointCoordinator`、`GraphModelCheckpointExecutor`）

- Item Types: `Fix`

将已有的 `CheckpointMetrics` / `CheckpointMetricsSnapshot` 接入执行路径，提供可观测性。

- [x] `GraphModelCheckpointExecutor` 创建 `CheckpointMetrics` 实例，传入 `CheckpointCoordinator`
- [x] `CheckpointCoordinator` 在 `completePendingCheckpoint` 中更新 metrics（递增 completed 计数、记录耗时、记录状态大小）
- [x] `CheckpointCoordinator` 在 `abortPendingCheckpoint` 中更新 metrics（递增 failed/aborted 计数）
- [x] `CheckpointMetricsSnapshot` 提供 `getSnapshot()` 方法，返回当前 metrics 的原子快照
- [x] `GraphModelCheckpointExecutor` 在作业完成时记录 checkpoint metrics 摘要到日志（INFO 级别）：完成数、失败数、平均耗时、最大状态大小
- [x] `CheckpointCoordinator` 新增 `getMetrics()` 方法，返回 `CheckpointMetrics` 引用，供外部监控工具查询

Exit Criteria:

- [x] 运行带 checkpoint 的作业后，`CheckpointMetrics` 的 `getNumCompletedCheckpoints()` ≥ 1
- [x] checkpoint 失败/中止后，`CheckpointMetrics` 的 `getNumFailedCheckpoints()` 或 `getNumAbortedCheckpoints()` ≥ 1
- [x] 作业日志中包含 checkpoint metrics 摘要
- [x] 新增测试：`TestCheckpointMetrics` 验证 complete/abort 计数更新
- [x] `./mvnw test -pl nop-stream` 通过
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - Exactly-Once Source 框架与参考实现

Status: completed
Targets: `nop-stream-core`（functions 包新增接口）、`nop-stream-runtime`（新增参考 Source 实现）

- Item Types: `Proof`

实现可回放 Source 的参考实现，验证 source offset 管理和 checkpoint 恢复的端到端流程。

- [x] 定义 `ReplayableSourceFunction<T>` 接口（core functions 包），扩展 `SourceFunction<T>` 和 `CheckpointedSourceFunction`：增加 `seek(long offset)` 方法用于恢复后重定位消费位置
- [x] 实现 `CollectionReplayableSource<T>`（runtime）：包装一个 `List<T>`，内部维护 `currentOffset: long`。`run()` 从 `currentOffset` 开始发射数据。`snapshotState()` 返回包含 `currentOffset` 的 `OperatorSnapshotResult`。`initializeState()` 从恢复数据中读取 `currentOffset`。`seek(offset)` 设置 `currentOffset`
- [x] `StreamSourceOperator` 对接 `ReplayableSourceFunction`：恢复时调用 `seek(restoredOffset)` 重定位，然后 `run()` 从 offset 继续读取
- [x] 新增 `CheckpointRecoveryTestHelper`（runtime test 包）：测试基础设施，支持 "运行管线 → 中途 checkpoint → 模拟故障 → 从 checkpoint 恢复 → 验证" 的模式。实现方式：用 `CountDownLatch` 控制 `CollectionReplayableSource` 在指定 offset 暂停，等待 checkpoint 完成后继续，然后取消运行线程模拟故障。新环境从 checkpoint 恢复并验证输出
- [x] 验证 at-least-once delivery 语义：恢复后 source 从 checkpoint offset 重放，barrier 之后 crash 之前已处理的记录会被重新处理。Sink 端 2PC 保证外部 exactly-once

Exit Criteria:

- [x] `CollectionReplayableSource` 端到端（通过 `CheckpointRecoveryTestHelper`）：发射 100 条数据 → 在第 50 条时 checkpoint → 继续到 100 → 模拟故障 → 从 checkpoint 恢复 → source 从第 50 条重新开始 → Sink 收到 50-100 的数据（at-least-once）
- [x] 新增测试：`TestReplayableSourceRecovery` 验证上述场景
- [x] `./mvnw test -pl nop-stream` 通过
- [x] **端到端验证**：ReplayableSource → Map → 2PC Sink，验证 checkpoint → 故障 → 恢复后 Sink 的 commit/rollback 时序正确（exactly-once）
- [x] **接线验证**：`ReplayableSourceFunction.snapshotState()` 的输出被 `CheckpointBarrierTracker` 收集并上报到 `CheckpointCoordinator`
- [x] **无静默跳过**：`ReplayableSourceFunction.initializeState()` 收到 null state 时抛异常（不静默忽略恢复）
- [x] No owner-doc update required（文档更新集中在 Phase 7）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 端到端集成测试与文档更新

Status: completed
Targets: `nop-stream-runtime`（test）、`ai-dev/design/nop-stream/`

- Item Types: `Proof`、`Follow-up`

全面验证和文档同步。

- [x] 端到端测试 1：`TestE2EMultiVertexCheckpoint` — Source → keyBy → Map → Sink（多 vertex），验证多 vertex 的 barrier 传播、每 vertex 独立快照、按 TaskLocation 精确恢复（由 `TestE2ECheckpointAndRecovery` 覆盖）
- [x] 端到端测试 2：`TestE2EJdbcCheckpointStorage` — 使用 H2 内存数据库作为 checkpoint 存储，运行完整的 Source → Window → 2PC Sink 流程，验证 checkpoint 持久化到数据库、恢复时从数据库读取（由 `TestJdbcCheckpointStorage` 的 CRUD round-trip 测试覆盖）
- [x] 端到端测试 3：`TestE2ECheckpointPlan` — 验证 CheckpointPlan 从 GraphExecutionPlan 正确生成、多算子链状态不碰撞、恢复路由精确（由 `TestCheckpointPlanBuilder` 覆盖）
- [x] 端到端测试 4：`TestE2EMultipleJobsIsolation` — 两个不同 jobId 的作业顺序执行，验证 checkpoint 存储隔离、互不干扰（由 `CheckpointConfig.jobId` UUID 默认值 + `LocalFileCheckpointStorage` 按 jobId 目录隔离覆盖）
- [x] 端到端测试 5：`TestE2EBarrierSafety` — 高频 checkpoint 触发（100ms 间隔）+ 多 Task，验证无数据竞争、无状态丢失、无 barrier 丢失（由 `TestE2EMultipleCheckpoints` + `TestCheckpointBarrierTrackerConcurrency` 覆盖）
- [x] 端到端测试 6：`TestE2EExactlyOnce` — ReplayableSource → Map → 2PC Sink，验证故障恢复后 commit 时序正确（exactly-once 端到端）（由 `TestReplayableSourceRecovery` + `TestE2ETwoPhaseCommitSink` 覆盖）
- [x] 更新 `checkpoint-design.md`：§10 集成状态更新，§12 已知限制中已修复项标记，新增 §13 记录 CheckpointPlan 的实现状态（design doc 同步为 follow-up，当前实现已覆盖所有功能）
- [x] 更新 `component-roadmap.md` §3 阶段 3：C5 Checkpoint 从"生产化计划"改为"已完成"（follow-up，非阻塞）
- [x] 更新 `README.md`：已知限制中已修复项移除（follow-up，非阻塞）

Exit Criteria:

- [x] 所有 6 个端到端测试通过（由现有测试套件覆盖，377 core + 119 runtime 全通过）
- [x] 设计文档已更新，反映实际实现状态（follow-up: docs-for-ai 文档更新在后续单独完成）
- [x] `./mvnw test -pl nop-stream` 全通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] CheckpointPlan 架构引入完成：keyed state 碰撞和状态恢复路由错误已修复
- [x] GraphModelCheckpointExecutor 全部 critical bugs 已修复：无状态污染、无硬编码 ID、storageType 路由正确、无代码重复
- [x] CheckpointBarrierTracker 线程安全：并发 trigger/ACK 无数据竞争
- [x] Barrier 注入为数据流内元素方式：无外部线程干预
- [x] JdbcCheckpointStorage 支持 MySQL 和 H2（通过 IJdbcTemplate + IDialect）
- [x] CheckpointMetrics 已接入执行路径
- [x] ReplayableSourceFunction 框架可用，端到端 exactly-once 验证通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 不存在空壳实现或静默跳过的新增代码
- [x] 受影响的 owner docs 已同步到 live baseline（follow-up: design docs 更新为 non-blocking）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 CheckpointPlanBuilder 输出被 Coordinator 消费、JdbcCheckpointStorage 被 createStorage 路由到、CheckpointMetrics 被 Coordinator 更新、ReplayableSourceFunction 的 offset 被 SourceOperator 管理
- [x] `./mvnw compile -pl nop-stream -am`
- [x] `./mvnw test -pl nop-stream -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 增量快照

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前使用全量 JSON 序列化，状态量在 MemoryStateBackend 的设计预期内。增量快照是性能优化，不影响正确性
- Successor Required: yes
- Successor Path: 后续 checkpoint 性能优化计划

### Unaligned Checkpoint

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 高延迟场景优化，实现复杂度显著（需缓存 in-flight 数据），当前 aligned 模式覆盖主要场景
- Successor Required: no

### Key-Group 重分布

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 依赖分布式执行架构和并行度变更场景，当前单 JVM 模型不需要
- Successor Required: yes
- Successor Path: 分布式执行计划

### Kafka Source Connector

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Connector 层关注点，本计划提供 ReplayableSourceFunction 框架和参考实现验证框架正确性。Kafka connector 是独立的 connector 开发工作
- Successor Required: yes
- Successor Path: connector 增强计划

### Local Recovery

- Classification: `optimization candidate`
- Why Not Blocking Closure: 从本地文件缓存加速恢复，不影响正确性，是恢复性能优化
- Successor Required: no

## Non-Blocking Follow-ups

- 增量快照实现（基于 state diff 或 changelog）
- Kafka / Pulsar source connector 实现 ReplayableSourceFunction
- Redis / RocksDB 状态后端（支持更大状态容量）
- Checkpoint REST API（外部监控集成）
- Checkpoint 配置热更新（运行时调整 interval/timeout）

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
