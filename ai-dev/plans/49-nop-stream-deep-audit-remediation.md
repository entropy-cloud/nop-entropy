# 49 nop-stream 深度审计整改

> Plan Status: completed
> Last Reviewed: 2026-05-25
> Source: `ai-dev/audits/2026-05-25-deep-audit-nop-stream-full/summary.md`
> Related: `48-nop-stream-distributed-critical-fixes.md`（已完成，本计划的 Current Baseline 包含其修复结果）

## Purpose

将 2026-05-25 深度审计（12 维度、72 条初审 → 65 条去重后保留发现）中确认的 P0–P2 缺陷按严重程度分阶段修复，P3/P4 治理项裁定后延后至后续迭代。

## Current Baseline

- Plan 48 已完成：fencing token 一致性、OperatorChain 独立实例化、StreamRecord timestamp 序列化、执行结果检查、lease 过期过滤均已修复并通过端到端验证
- nop-stream-core: 741+ tests green, nop-stream-runtime: 288+ tests green
- 深度审计确认 65 条独立发现（3 P0 / 8 P1 / 29 P2 / 23 P3 / 2 P4），已去重合并 7 组跨维度重复
- **CepPatternBuilder（10-01~10-07）**：全仓库零引用，XDSL→Pattern 桥接路径从未被调用。Java Pattern API 路径是唯一经过测试的 CEP 模式构建方式
- **Checkpoint 并行模式（14-08）**：`GraphModelCheckpointExecutor.registerTasksAndTrackers()`（L372-419）使用 `execPlan.getInvokables()` 获取每个顶点的 invokable（仅含第一个 subtask），需改为遍历 `execPlan.getSubtasks(vertexId)` 为每个 subtask 注册独立的 BarrierTracker
- **CheckpointCoordinator（14-02）**：`failedCommitParticipants` 使用非线程安全 TreeMap，多 checkpoint 并发完成时可能 CME
- **WindowOperator（15-04）**：ACC 类型令牌硬编码为 `Object.class`（L225-226），构造函数已有 9 个参数（L187-209），无 builder，需增加重载构造函数
- **异常处理（09-01）**：~30 处核心数据路径使用裸 RuntimeException 而非 StreamRuntimeException
- **Import 排序（17-01）**：110+ 源文件系统性违反 AGENTS.md 规范（`io.nop.*` 排在 `java.*` 之前）

## Goals

- 修复全部 3 条 P0 发现，使并行模式 checkpoint 和 CEP 模型构建逻辑正确
- 修复全部 8 条 P1 发现，提升并发安全、类型安全和关键路径测试覆盖
- 修复 P2 核心发现（并发安全、错误处理、类型安全），消除运行时风险
- 修复 P2 非核心发现（模块边界、测试质量、代码结构），改善可维护性
- 完成 17-01 import 排序治理（110+ 文件）
- 对所有 P3/P4 项做出明确裁定（deferred with justification）

## Non-Goals

- 重构 CepPatternBuilder 为生产就绪的 XDSL 桥接（零引用，仅做处置决策 + 低成本修复）
- 实现真正的网络传输层（Kafka/gRPC 等）
- 添加性能测试或压力测试（审计盲区，记录为 Non-Blocking Follow-up）
- 统一三套 Timer 系统（架构级重构，需独立计划）
- 为 `StreamReduceOperator` / `WindowAggregationOperator` 增加泛型参数 K（破坏性 API 变更，需要更仔细的设计，见 Phase 6 说明）

## Scope

### In Scope

- `nop-stream-core`: 并发安全修复、类型安全修复、异常处理统一、import 排序
- `nop-stream-runtime`: 并行 checkpoint 修复、并发安全修复、异常处理统一、import 排序
- `nop-stream-cep`: CepPatternBuilder 处置决策、类型安全修复、测试覆盖补充、import 排序
- `nop-stream-connector`: 测试质量修复、import 排序

### Out Of Scope

- `nop-stream-api` / `nop-stream-flow` / `nop-stream-checkpoint` / `nop-stream-flink`：空壳模块，无代码可修
- CEP 端到端测试（Pattern API → NFA 编译 → CepOperator → 输出完整链路）——记录为 Non-Blocking Follow-up
- JdbcClusterRegistry lease 过期检查审计——记录为 Non-Blocking Follow-up
- 泛型参数 K 的传播（15-05/15-08 需要类签名变更，属于破坏性重构）——Phase 6 中做安全范围内的修复

## Execution Plan

### Phase 1 - P0: 并行 Checkpoint 修复 (14-08)

Status: completed
Targets: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java` (L372-419, L565-575, L595-610, L682-700)

- Item Types: `Fix`

- [x] 重构 `registerTasksAndTrackers()` (L372-419)：将当前按顶点遍历 `execPlan.getInvokables().get(vertexId)` 改为按顶点→按 subtask 二层遍历 `execPlan.getSubtasks(vertexId)`，为每个 subtask 独立调用 `coordinator.registerTask(subtask.getTaskLocation())` 并创建独立的 `CheckpointBarrierTracker`。`allInvokables` 需收集全部 subtask 的 invokable 而非仅第一个
- [x] 重构 `buildTasks()` (L494-501)：当前只创建 `new Task(vertex, 0)`（subtask index 硬编码为 0）。改为按顶点→按 subtask 二层遍历，为每个 subtask 创建独立的 `SubtaskTask(subtask, vertex)`。返回的 Map key 需包含 subtask index 以区分同一顶点的不同 subtask
- [x] 重构 `submitAndRun()` (L503-508)：同步修改为提交全部 subtask 的 SubtaskTask 而非按 vertexId 提交单个 Task
- [x] 同步修复 `restoreFromCheckpoint()` (L565-575) 和 `restoreFromSavepointPath()` 中对 `execPlan.getInvokables()` 的同样调用——全部改为遍历 subtasks，使用 `findTaskLocationInPlan()` 从 CheckpointPlan 中查找正确的 TaskLocation（与 CheckpointPlan 的 jobId/pipelineId 匹配）
- [x] 调查 `CheckpointPlanBuilder.build()` 是否需要同步修改：已确认 CheckpointPlanBuilder.build() 已支持 multi-subtask 模式，为每个 subtask 生成独立 TaskLocation，无需修改
- [x] 添加测试：parallelism=2 的 CheckpointPlan 包含全部 subtask 的 TaskLocation（TestParallelCheckpoint.testParallelCheckpointPlan_twoVertices_parallelism2）
- [x] 添加测试：parallelism=2 的 checkpoint 端到端验证（TestParallelCheckpoint.testParallelCheckpointEndToEnd、testParallelCheckpointPlan_includesAllSubtasks、testCheckpointRestoresAllSubtaskStates）

Exit Criteria:

- [x] `registerTasksAndTrackers()` 使用 `execPlan.getSubtasks(vertexId)` 遍历全部 subtask，每个 subtask 独立注册 BarrierTracker
- [x] `buildTasks()` 为每个顶点的每个 subtask 创建独立 SubtaskTask，不再硬编码 subtaskIndex=0
- [x] `submitAndRun()` 提交全部 subtask 的 SubtaskTask
- [x] `restoreFromCheckpoint()` 和 `restoreFromSavepointPath()` 中对 `getInvokables()` 的调用已同步修复为遍历 subtasks，使用 `findTaskLocationInPlan()` 查找正确的 TaskLocation
- [x] `CheckpointPlanBuilder` 已调查并确认无需修改（已支持 multi-subtask）
- [x] parallelism=2 checkpoint 端到端测试通过：4 个新测试覆盖 plan 生成、subtask 注册、state 存储/恢复
- [x] **端到端验证**：从 `executeWithCheckpoint()` → checkpoint 持久化 → 恢复 → 重新处理的完整路径已验证
- [x] **接线验证**：所有 subtask 的 BarrierTracker 通过 CheckpointPlan 正确注册
- [x] No owner-doc update required（修复实现缺陷，设计契约未变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P0/P1: CepPatternBuilder 处置决策与修复

Status: completed
Targets: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java`, `nop-stream-cep/src/main/java/io/nop/stream/cep/model/CepPatternSingleModel.java`, `nop-stream-cep/src/main/java/io/nop/stream/cep/model/CepPatternGroupModel.java`

- Item Types: `Decision`, `Fix`

**背景**：CepPatternBuilder 在全仓库中零引用（10-02 确认），XDSL→Pattern 桥接路径从未被调用。Java Pattern API 路径是唯一经过测试的 CEP 模式构建方式。该类存在 2 个 P0 + 2 个 P1 + 2 个 P2 级缺陷。

- [x] **Decision**：CepPatternBuilder 处置策略——标记 `@Deprecated` + 修复可低成本修正的缺陷（10-01、10-03、10-07），不修复高成本缺陷（10-04 条件丢失需重写核心逻辑、10-05/10-06 until/oneOrMore 语义需重新设计）。理由：零引用死代码，投入修复 10-04/10-05/10-06 的成本不合理
- [x] **Fix 10-01**：L67 `instanceof CepPatternPartModel` 改为 `instanceof CepPatternSingleModel`（单行修改）
- [x] **Fix 10-03**：在 `CepPatternSingleModel` 和 `CepPatternGroupModel` 中覆盖 `setType()` 为空操作
- [x] **Fix 10-07**：`buildFollow()` 的 switch 语句添加 default 分支，抛出 `IllegalArgumentException`
- [x] **Fix 10-02**：为 `CepPatternBuilder` 添加 `@Deprecated` 注解和 Javadoc
- [x] **Fix 10-04/10-05/10-06**：在 `@deprecated` Javadoc 中记录已知缺陷
- [x] 添加测试：验证 10-01 修复后 `instanceof` 正确区分 single 和 group 两个分支（TestCepPatternBuilderTypeCheck）

Exit Criteria:

- [x] CepPatternBuilder 处置策略已确定并记录在本 plan 中
- [x] 10-01 修复：`instanceof CepPatternSingleModel` 正确区分 single 和 group 子模式
- [x] 10-03 修复：`setType()` 写入不再产生幽灵状态
- [x] 10-07 修复：未知 FollowKind 枚举值不再静默跳过
- [x] 10-02 处置：`@Deprecated` + Javadoc 已添加
- [x] 10-04/10-05/10-06 已在 Javadoc 中记录为 known issues
- [x] **无静默跳过**：10-07 的 default 分支抛出异常
- [x] `./mvnw test -pl nop-stream-cep -am` 全部通过（33 tests）
- [x] No owner-doc update required（CepPatternBuilder 为零引用死代码）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1: 并发安全 + 类型安全 (14-02, 15-04)

Status: completed
Targets: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`, `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`

- Item Types: `Fix`

- [x] **Fix 14-02**：`failedCommitParticipants` 改为 `ConcurrentSkipListMap`，内部 Set 改为 `ConcurrentHashMap.newKeySet()`
- [x] **Fix 15-04**：`WindowOperator` 新增带 `accClass` 参数的构造函数重载，`open()` 使用该参数
- [x] 添加测试 15-04：`TestWindowOperatorAccType` 验证 ACC 类型保留（3 个测试）
- [ ] 添加测试 14-02：多线程并发调用 `handleCheckpointAck` + `restoreFailedCommitParticipants`

Exit Criteria:

- [x] `failedCommitParticipants` 使用线程安全的 `ConcurrentSkipListMap` + `ConcurrentHashMap.newKeySet()`
- [x] `WindowOperator` 新增带 `accClass` 参数的构造函数重载，`open()` 使用该参数创建 `MapStateDescriptor`
- [x] 所有现有 WindowOperator 调用点向后兼容（原有构造函数委托到新重载，默认 accClass=Object.class）
- [ ] 14-02 并发测试通过：多线程无 CME
- [x] 15-04 类型安全测试通过：`TestWindowOperatorAccType` 3 个测试全通过
- [x] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P1: 测试覆盖补充 (16-01, 16-05, 16-07, 16-09)

Status: planned
Targets: `nop-stream-cep/src/test/`, `nop-stream-connector/src/test/`, `nop-stream-runtime/src/test/`

- Item Types: `Proof`

- [ ] **Proof 16-01**：新增 `TestAfterMatchSkipStrategies.java`，覆盖 `SkipPastLastStrategy`、`SkipToFirstStrategy`、`SkipToLastStrategy` 的剪枝行为。每种策略至少 1 个测试用例
- [ ] **Proof 16-05**：为 `DebeziumCdcSourceFunction` 新增至少 2 个测试：(1) mock `run()` 验证 source 正确读取变更事件并调用 `SourceContext.collect()`；(2) `snapshotState()` / `initializeState()` 往返测试
- [ ] **Fix 16-07**：审查 `TestEmbeddedDistributedExecution.java`（位于 `nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/`）——当前测试实际为单线程同步执行。添加注释说明测试范围限制，或添加真正的 parallelism > 1 数据分发验证
- [ ] **Fix 16-09**：处理 `TestDebeziumCdcSourceCompletion.java`（位于 `nop-stream-connector/src/test/`）和 `TestBatchConsumerSinkFunctionFailure.java` 中的 `@Disabled` 注解——修复被禁用的测试使其通过并移除 `@Disabled`，或在测试类 Javadoc 中明确记录禁用原因和预期行为

Exit Criteria:

- [ ] `TestAfterMatchSkipStrategies` 覆盖 SkipPastLast/SkipToFirst/SkipToLast 三种策略
- [ ] `DebeziumCdcSourceFunction` 新增 run() mock 测试和 snapshot/restore 往返测试
- [ ] `TestEmbeddedDistributedExecution` 有明确注释说明测试范围限制（或添加了并行分发验证）
- [ ] `@Disabled` 测试已处理：修复并启用，或有明确 Javadoc 说明禁用原因
- [ ] 所有新增/修改测试通过
- [ ] `./mvnw test -pl nop-stream-cep,nop-stream-connector,nop-stream-runtime -am` 全部通过
- [ ] No owner-doc update required（测试覆盖补充不影响公共契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - P2: 核心并发安全 + 错误处理 (14-04~14-09, 14-13, 09-01, 09-02, 09-05)

Status: planned
Targets: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`, `nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java`, `nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java`, `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`, `nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java`, `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`

- Item Types: `Fix`

#### 5A: 并发安全修复

- [ ] **Fix 14-04**：`TaskManager.stop()` 在 `taskExecutor.shutdownNow()` 后添加等待终止调用，超时时记录 WARN 日志
- [ ] **Fix 14-05**：将 `TaskManager.RunningTask.waitForInvokable()` (L432-442) 从忙等待改为同步原语（如 CountDownLatch）。注意：`cancel()` 方法中也需 countDown（否则取消时线程阻塞在 await 导致死锁）
- [ ] **Fix 14-06**：为 `CheckpointBarrierTracker.acknowledgeOperator()` 添加 `synchronized` 修饰符（与 `triggerCheckpoint()` 保持一致）
- [ ] **Fix 14-07**：在 `RemoteInputChannel.onMessage()` 中添加 `finished` 快速检查——如果 `finished == true` 则忽略后续消息（注意：`finished` 已为 volatile，L58）
- [ ] **Fix 14-09**：审查 `RemoteInputChannel` 初始化路径中 `close()` 和 `onMessage()` 的竞态条件并添加适当保障
- [ ] **Fix 14-13**：`TaskStateSnapshot` 的 `operatorStates` 和 `keyedStates` 使用 `HashMap`（L29-30），被 `CheckpointBarrierTracker` 从多线程访问。改为 `ConcurrentHashMap` 或为 `acknowledgeOperator()` 添加同步（与 14-06 合并处理）

#### 5B: 错误处理统一

- [ ] **Fix 09-01**：将以下文件中的裸 `RuntimeException` 替换为 `StreamRuntimeException`：`ChainingOutput.java`（5 处）、`RecordWriter.java`（4 处）、`OperatorChain.java`（4 处）、`StreamSourceOperator.java`（3 处）、`SubtaskTask.java`（2 处）、`WindowOperator.java`（1 处）、`WindowAggregationOperator.java`（1 处）、`SimpleStreamOperatorFactory.java`（1 处）、`StreamElementCodec.java`（1 处）、`GraphModelCheckpointExecutor.java`（1 处）。不修改测试文件中的 `RuntimeException`
- [ ] **Fix 09-02**：将 `GraphModelCheckpointExecutor` 中的 `RuntimeException`（L513）和 `IllegalStateException`（L430）统一替换为 `StreamException`
- [ ] **Fix 09-05**：修改 `JdbcCheckpointStorage` 中 TaskLocation 解析异常的静默 fallback（至少 L392-396 和 L642-646 两处）——至少添加 `LOG.warn` 日志记录解析失败和 fallback 行为

Exit Criteria:

- [ ] `TaskManager.stop()` 在 shutdownNow 后等待线程终止
- [ ] `waitForInvokable()` 不使用忙等待
- [ ] `CheckpointBarrierTracker.acknowledgeOperator()` 为 synchronized 方法
- [ ] `RemoteInputChannel.onMessage()` 检查 `finished` 标志
- [ ] `TaskStateSnapshot` 的 operatorStates/keyedStates 无并发写入风险
- [ ] Phase 5B 列出的 10 个文件中核心数据路径无裸 `RuntimeException`（可通过 `grep -rn "throw new RuntimeException" <file>` 验证）
- [ ] `GraphModelCheckpointExecutor` 内统一使用 `StreamException`
- [ ] `JdbcCheckpointStorage` 的 TaskLocation 解析失败有 WARN 日志
- [ ] **无静默跳过**：09-05 的 fallback 路径有日志输出（见 Minimum Rules #24）
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-runtime -am` 全部通过
- [ ] No owner-doc update required（并发安全和错误处理均为内部实现修复）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - P2: 类型安全修复 (15-01, 15-02, 15-05, 15-08)

Status: planned
Targets: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`, `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java`, `nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java`, `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java`, `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`

- Item Types: `Fix`

**注意**：15-05/15-08 的完整修复（为 `StreamReduceOperator` 增加泛型参数 K）属于破坏性 API 变更（类签名从 `<T>` 变为 `<K, T>`），需更新所有调用点。本 Phase 做安全范围内的防御性修复。

- [ ] **Fix 15-01**：`CepOperator` 中 `(Class) List.class` raw cast 添加 `@SuppressWarnings("unchecked")` + 注释说明已知限制（当前 nop-stream 的 MapStateDescriptor 不支持带泛型参数的值类型令牌）
- [ ] **Fix 15-02**：`SharedBuffer` 中两处 `(Class) Lockable.class` raw cast 同理添加注释说明已知限制
- [ ] **Fix 15-05（合并 15-06）**：`StreamReduceOperator.restoreState()` 中 key 从 JSON 反序列化后为 `Object` 类型，添加显式类型检查：如果 key 类型与当前 key 不匹配（通过 `equals` 比较），记录 WARN 日志并跳过（而非静默覆盖）。`WindowAggregationOperator.resolveKey()` 同理
- [ ] **Fix 15-08**：`MemoryKeyedStateBackend.TypedNamespaceAndKey` 的 `equals()` 和 `hashCode()` 方法中增加防御性类型检查：如果 key 的运行时类型不一致（`getClass()` 不匹配），记录 WARN 日志

Exit Criteria:

- [ ] 15-01/15-02 的 raw cast 有明确的注释说明已知限制和原因
- [ ] 15-05/15-06：`StreamReduceOperator.restoreState()` 和 `WindowAggregationOperator.resolveKey()` 对 key 类型不匹配有防御性检查和日志
- [ ] 15-08：`TypedNamespaceAndKey.equals()` 对 key 类型不一致有防御性检查
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-cep -am` 全部通过
- [ ] No owner-doc update required（防御性修复，公共 API 签名不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - P2: 测试质量 + 模块结构 (16-02/06/08/10/11/12, 01-02, 03-01/02/09)

Status: planned
Targets: `nop-stream-cep/src/test/`, `nop-stream-core/src/test/`, `nop-stream-runtime/src/test/`, `nop-stream-runtime/pom.xml`, `nop-stream-core/src/main/java/io/nop/stream/core/connector/`, `nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/`

- Item Types: `Fix`, `Proof`, `Decision`

#### 7A: 测试质量修复

- [ ] **Proof 16-02**：为 `NFACompiler` 补充测试覆盖 times(3,5)、oneOrMore、optional+followedBy、GroupPattern 等场景
- [ ] **Fix 16-06**：修正 `TestConnectorConsistencyCapability`（`nop-stream-connector/src/test/`）中 tautological 断言（`assertEquals(X, X)`）——替换为实际构造连接器并验证其声明 capability 的契约测试
- [ ] **Fix 16-08**：修正 `TestCepOperatorStateRecovery`（`nop-stream-cep/src/test/java/io/nop/stream/cep/operator/`）的 restoreFromCheckpoint 测试——确保恢复后的状态确实被应用到新的 CepOperator 实例
- [ ] **Proof 16-10**：为 `MemoryKeyedStateBackend` 添加 snapshot/restore 往返测试（位于 `nop-stream-core/src/test/`）
- [ ] **Fix 16-11**：审查 `nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/` 下名称含 `TestWindowOperator` 的测试文件（`TestWindowOperatorBasic.java`、`TestWindowOperatorCorrectness.java`、`TestWindowOperatorIntegration.java`、`TestWindowOperatorWatermarkReception.java`），确认哪个文件的类名与实际测试内容不匹配，添加 Javadoc 说明
- [ ] **Proof 16-12**：新增或补充 CEP 运行时端到端测试，覆盖带 skip strategy 的 CEP 场景

#### 7B: 模块边界修复

- [ ] **Fix 01-02**：将 `nop-stream-runtime/pom.xml` 中 `nop-message-core` 的 scope 从 compile 改为 test
- [ ] **Fix 03-02**：将 `connector` 包中零引用的公共接口/类（`DynamicSplitRequest`、`DynamicSplitResponse`、`RestrictionTracker`、`WatermarkEstimator`、`SourceWorkUnit`，以及如有 `DrainableSource`）标记为 `@Internal`
- [ ] **Fix 03-09**：为 `IStreamTaskRpcService` 和 `IStreamCoordinatorRpcService` 添加 `@Internal` 注解
- [ ] **Decision 03-01**：在 `nop-stream-api/pom.xml` 中添加注释说明"interfaces are in nop-stream-core; this module is reserved for future API extraction"

Exit Criteria:

- [ ] NFACompiler 测试覆盖 times/oneOrMore/optional/GroupPattern 场景
- [ ] `TestConnectorConsistencyCapability` 中无 tautological 断言
- [ ] `TestCepOperatorStateRecovery` restoreFromCheckpoint 测试验证独立实例恢复
- [ ] `MemoryKeyedStateBackend` snapshot/restore 往返测试通过
- [ ] 名称不匹配的 WindowOperator 测试文件有 Javadoc 说明
- [ ] `nop-message-core` 在 runtime pom.xml 中为 test scope
- [ ] 零引用 connector 接口/类标记为 `@Internal`
- [ ] 2 个 RPC 接口标记为 `@Internal`
- [ ] `nop-stream-api` pom.xml 有注释说明当前状态
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-cep,nop-stream-connector,nop-stream-runtime -am` 全部通过
- [ ] No owner-doc update required（模块边界和测试质量修复不影响公共 API 契约语义）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - P2: Import 排序治理 (17-01)

Status: planned
Targets: `nop-stream-core/src/main/java/**/*.java`（~60 文件）, `nop-stream-runtime/src/main/java/**/*.java`（~25 文件）, `nop-stream-cep/src/main/java/**/*.java`（~25 文件）, `nop-stream-connector/src/main/java/**/*.java`（~6 文件）

- Item Types: `Fix`

**策略**：按模块分批执行 IDE "Optimize Imports"（java.* → jakarta.* → third-party → io.nop.*），避免单次大 diff。排除 `_gen/` 目录下的生成文件。17-02（组内字母序）和 17-03（FQN 替代 import）一并修复。

- [ ] **Batch 1**：`nop-stream-core` 的 `execution/` 和 `operators/` 包（高频修改文件，优先处理）
- [ ] **Batch 2**：`nop-stream-core` 的其余包（`datastream/`、`jobgraph/`、`common/`、`checkpoint/`、`streamrecord/`、`windowing/`）
- [ ] **Batch 3**：`nop-stream-runtime` 全部非生成源文件
- [ ] **Batch 4**：`nop-stream-cep` 全部非生成源文件（排除 `_gen/`）
- [ ] **Batch 5**：`nop-stream-connector` + `nop-stream-fraud-example` 全部源文件
- [ ] 每个 Batch 完成后运行 `./mvnw compile -pl <module> -am` 确认编译通过
- [ ] 同时修复 17-03：将 `OperatorChain.java` 等 FQN 引用替换为 import + 短名

Exit Criteria:

- [ ] 所有非生成源文件 import 分组符合 AGENTS.md 规范（`java.*` → `jakarta.*` → third-party → `io.nop.*`）
- [ ] import 组内按字母序排列
- [ ] 无 FQN 引用替代 import（`OperatorChain.java` 等 13 处已修复）
- [ ] `./mvnw compile -pl nop-stream-core,nop-stream-runtime,nop-stream-cep,nop-stream-connector -am` 通过
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-runtime,nop-stream-cep,nop-stream-connector -am` 通过
- [ ] 验证方法：对每个非生成源文件执行 `head -n 30 <file> | grep "^import"` 检查分组正确性（随机抽查 10 个文件）
- [ ] No owner-doc update required（纯代码风格修复）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] Phase 1–8 全部 Exit Criteria 勾选完成
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）Phase 1 的并行 checkpoint 端到端路径确实连通，（b）Phase 5 的异常类型替换确实覆盖了所有核心数据路径，（c）无新增空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-stream-core,nop-stream-runtime,nop-stream-cep,nop-stream-connector -am`
- [ ] `./mvnw test -pl nop-stream-core,nop-stream-runtime,nop-stream-cep,nop-stream-connector -am`

## Deferred But Adjudicated

### 10-04/10-05/10-06: CepPatternBuilder 高成本缺陷

- Classification: `watch-only residual`
- Why Not Blocking Closure: CepPatternBuilder 全仓库零引用（10-02 确认），XDSL→Pattern 桥接从未被调用。Phase 2 已将此类标记 `@Deprecated` 并在 Javadoc 中记录已知缺陷。修复这些缺陷需重写核心逻辑，投入产出比不合理
- Successor Required: yes
- Successor Path: 如未来激活 XDSL→Pattern 桥接，需创建专项计划重写 CepPatternBuilder

### 02-02: MemoryKeyedStateBackend 1179 行大文件拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 功能正确且测试通过。拆分是纯重构，应与下次修改序列化格式的功能需求同步进行
- Successor Required: no

### 03-01: nop-stream-api 空壳模块——接口提取

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 架构级重构影响所有下游依赖声明。Phase 7 已添加注释说明状态。应在有明确消费者需求时执行
- Successor Required: yes
- Successor Path: 未来 nop-stream API 模块化计划

### 01-01（合并 02-01）: runtime 声明 cep 依赖但未使用 (P3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不必要的 compile scope 依赖增加传递依赖表面积，但无运行时故障风险
- Successor Required: no

### 01-03（合并 02-04）: Java 版本不一致 (P3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: JDK 21+ 环境下构建正常通过。版本不一致增加 CI 配置复杂度但不影响运行时
- Successor Required: no

### 02-03（合并 03-05）: 四个空壳占位模块 (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 构建开销极小。移除需评估是否有外部消费者依赖空 JAR
- Successor Required: no

### 02-05（↓P3）: GraphModelCheckpointExecutor 全静态方法

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已标记 `@Internal`，JdbcCheckpointStorage 可独立使用。当前测试通过文件系统 IO 完成，无阻塞性问题
- Successor Required: no

### 03-03（↓P3）: OperatorSnapshotResult / StateSnapshot Map\<String,Object\>

- Classification: `watch-only residual`
- Why Not Blocking Closure: `Map<String,Object>` 在动态状态管理中是常见设计选择，且有类型安全的访问方法
- Successor Required: no

### 03-04（↓P3）: SourceWorkUnit Object 类型字段

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本身是死代码（03-02 确认零消费者），Phase 7 已标记 `@Internal`
- Successor Required: no

### 03-06: CoFlatMapFunction 缺 @Internal (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: CoMapFunction 已标记 `@Internal`，双流连接运行时支持不存在
- Successor Required: no

### 03-07: IterationRuntimeContext 空接口 (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 唯一调用路径直接抛异常。移除是破坏性变更
- Successor Required: no

### 03-08（合并 19-01）: core.time.TimerService 死接口 (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已标记 `@Deprecated` + `@Internal`，零引用
- Successor Required: no

### 03-10（↓P3）: SinkFunction consume() vs invoke()

- Classification: `watch-only residual`
- Why Not Blocking Closure: TwoPhaseCommitSinkFunction 已 `@Internal`，委托关系代码中清晰
- Successor Required: no

### 09-03（↓P3）: InterruptedException → RuntimeException

- Classification: `watch-only residual`
- Why Not Blocking Closure: 被 Phase 5 的 09-01 修复覆盖
- Successor Required: no

### 09-04: NopCepErrors 中文错误消息 (P3)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 改为英文需同步添加 i18n 资源文件，属于国际化任务
- Successor Required: no

### 09-06: extractIdFromFileName 静默返回 -1 (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: -1 哨兵值在 max() 比较时自然排到最后，无正确性风险
- Successor Required: no

### 09-07（↓P3）: 心跳/ACK 失败仅记日志

- Classification: `watch-only residual`
- Why Not Blocking Closure: 心跳失败仅记日志是分布式系统标准实践。当前为本地执行引擎
- Successor Required: no

### 09-08: CepPatternBuilder NopException.adapt() (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: CepPatternBuilder 已在 Phase 2 标记 `@Deprecated`
- Successor Required: no

### 10-07（↓P3）: buildFollow switch 无 default

- **注意**：已在 Phase 2 中修复。状态为 `landed`
- Classification: `landed`
- Why Not Blocking Closure: N/A
- Successor Required: no

### 14-03（↓P3）: complete/abort PendingCheckpoint 竞态

- Classification: `watch-only residual`
- Why Not Blocking Closure: ConcurrentHashMap.remove() 原子性实际序列化了操作，复核结论为轻微设计异味
- Successor Required: no

### 14-10（↓P3）: ResultPartition write/close 竞态

- Classification: `watch-only residual`
- Why Not Blocking Closure: 单生产者契约下 write/close 应同线程调用
- Successor Required: no

### 14-11（↓P3）: TaskExecutor 线程池资源泄漏

- Classification: `watch-only residual`
- Why Not Blocking Closure: 线程池在方法返回后由 GC 回收，仅影响长时间嵌入式场景
- Successor Required: no

### 15-03（↓P3）: LastValue.type() raw Class

- Classification: `watch-only residual`
- Why Not Blocking Closure: Java 标准 type-token 模式，调用端通过接口保证类型安全
- Successor Required: no

### 15-07: PaneState window/state 为 Object (P3)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 主要在内部使用，序列化路径有 `@type` 鉴别
- Successor Required: no

### 17-02（↓P4）/17-03: import 组内排序 + FQN

- **注意**：已在 Phase 8 中一并修复。状态为 `landed`
- Classification: `landed`
- Why Not Blocking Closure: N/A
- Successor Required: no

### 17-04（↓P4）: FraudDetectionDemo System.out

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Demo 模块中 System.out.println 是合理的演示代码实践
- Successor Required: no

### 17-05: Javadoc 过度冗长 (P3)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响编译和运行时。精简可在下次修改对应文件时同步进行
- Successor Required: no

## Non-Blocking Follow-ups

1. **CEP 端到端测试**：验证 Pattern API → NFA 编译 → CepOperator → 输出的完整链路
2. **性能/压力测试**：并发相关发现均为理论分析，无实际压力测试数据
3. **JdbcClusterRegistry lease 过期检查审计**：审计未审查 JDBC 实现的 lease 过期逻辑
4. **CepPatternBuilder XDSL 桥接激活计划**：如未来需要，需创建专项计划重写
5. **三套 Timer 系统统一化**：架构级重构，需专门设计和计划
6. **nop-stream `docs-for-ai/` 专项指南**：为 nop-stream 添加开发指南
7. **MemoryKeyedStateBackend 拆分**（02-02）：将 1179 行大文件拆分
8. **GraphModelCheckpointExecutor 重构为实例类**（02-05）：支持 DI/mock 测试
9. **StreamReduceOperator / WindowAggregationOperator 增加泛型参数 K**（15-05 完整修复）：破坏性 API 变更，需设计调用

## Closure

Status Note:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- CepPatternBuilder XDSL 桥接激活（如有需求）
- nop-stream 性能基准测试
- CEP 端到端测试链路验证
