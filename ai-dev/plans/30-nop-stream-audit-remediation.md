# 30 nop-stream 审计发现修复

> Plan Status: completed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/audits/2026-05-20-adversarial-review-nop-stream/` (Round 1 + Round 2 + Known Issues Confirmation)
> Related: Plan 24 (代码清理与包结构重组, completed), Plan 26 (Graph Model 与 Checkpoint 集成, completed)

## Purpose

修复 2026-05-20 对抗性审查中确认的全部 live defect 和 contract drift，将 nop-stream 的窗口聚合、状态管理、执行路径、connector、示例代码收口到正确可用的状态。

## Current Baseline

- nop-stream-core / nop-stream-runtime / nop-stream-connector / nop-stream-fraud-example 四个有源码模块
- nop-stream-cep 已完善，不在本计划范围
- 4 个空壳模块（api/checkpoint/flink/flow）保留 pom.xml 占位，不做填充
- 已完成的清理工作：operator/operators 双包统一（Plan 24）、EvictingWindowOperator/CepWindowOperator 已删除
- 审计发现按优先级分为 P0-P3，共 41 个（含 12 个已知未修复 + 29 个新发现）
- 当前所有 `./mvnw test -pl nop-stream` 通过（56 个测试类），但测试覆盖存在盲区（聚合、合并窗口、checkpoint 恢复等路径无覆盖）
- **优先级提升说明**：K15（window.toString namespace）从审计 P2 提升到本计划 P0（与 N12 叠加导致 checkpoint 恢复后 key 间数据错乱）；N8（getSimpleAccumulator 返回 null）从审计 P3 提升到 P1（API 契约违背）；N16（restoreState 非序列化 ACC）从审计 P2 提升到 P0（与 N3 配套，checkpoint 路径完整链）

## Goals

- 修复全部 P0 级问题（8 个）：窗口聚合正确性链（N1+N17）、merge 静默吞异常（N2）、MergingWindowSet 持久化（N3）、snapshotState 分区（N12）、namespace 碰撞（K15）、execute() watermark（N19）、restoreState 序列化失败（N16）
- 修复全部 P1 级问题（7 个）：API 契约违背、side output 丢弃、资源管理、getSimpleAccumulator null
- 修复 P2 级问题中的 live defect（10 个）
- 清理 P3 级死代码和示例问题（11 个）
- 为修复项补充 focused verification（嵌入各 Phase）

## Non-Goals

- nop-stream-cep 模块的任何修改
- 分布式执行、网络传输层的实现
- 新功能开发（Kafka connector、CDC 增强、SQL Transform 等）
- 性能优化（PriorityQueue 替换为 TreeSet 等）— 归入 optimization candidate
- 重构包结构（已在 Plan 24 完成）
- 填充 4 个空壳模块

## Scope

### In Scope

- nop-stream-core：状态管理修复、execute() 路径修复、API 契约修复
- nop-stream-runtime：WindowOperator 聚合修复、checkpoint 修复、TimerService 修复
- nop-stream-connector：资源管理修复、生命周期修复
- nop-stream-fraud-example：示例代码正确性修复

### Out Of Scope

- nop-stream-cep（已完善）
- 空壳模块填充
- 性能优化（watch-only residual）
- 双执行模型统一（design-level decision，归入 deferred）

## Risks And Rollback

- **Checkpoint 格式不兼容**：Phase 1 修改了 `windowNamespace()` 编码和 `snapshotState` 序列化结构，已有 checkpoint 数据（如有）在修复后不兼容，需清空重新构建。当前 nop-stream 无生产 checkpoint，影响可控。
- **Watermark 行为变化**：Phase 2 使 fast path 的 watermark 从"完全不工作"变为"以 200ms 间隔工作"（Phase 4 才可配置），依赖 watermark 不存在的代码可能受影响。
- 回退策略：每个 Phase 独立提交，可按 Phase 粒度 revert。

---

## Execution Plan

### Phase 1 - P0: WindowOperator 聚合正确性链 + checkpoint 序列化修复

Status: completed
Targets: `WindowOperator.java`, `MemoryKeyedStateBackend.java`, `MergingWindowSet.java`

Item Types: `Fix`

修复窗口聚合从状态管理层到算子层的完整正确性链，以及 checkpoint 序列化/恢复的 key 分区和 namespace 问题：

- [x] N17: 修复 `MemoryInternalAppendingState.add()` 累加器不重置 — 每次 add 前重置或重建累加器，确保连续调用不会膨胀
- [x] N1: 修复 `WindowOperator.addWindowElement()` SimpleAccumulator 类型腐蚀 — 首元素应创建累加器初始值而非存入裸 IN 值
- [x] N2: 修复 `mergeWindowContents()` 静默吞 ClassCastException — 改为记录 WARN 日志，或使用正确的类型转换逻辑
- [x] K15: 修复 `windowNamespace()` 使用 window.toString()（从 P2 提升：与 N12 叠加导致 checkpoint 恢复后 key 间 namespace 碰撞） — 改为使用类型安全的 namespace 标识
- [x] N12: 修复 `WindowOperator.snapshotState()` 未按 key 分区 — 序列化时保留 key+namespace 的完整映射，restore 时正确还原
- [x] N16: 修复 `WindowOperator.restoreState()` 对非 Serializable ACC 抛 NotSerializableException（从 P2 提升：与 N3 配套形成 checkpoint 完整链） — 改为 JSON 序列化或标注 ACC 必须实现 Serializable
- [x] N7: `emitWindowContents` 隐式依赖 triggerContext.key — 将 key 作为显式参数传入方法签名（消除隐式耦合陷阱）
- [x] N3: 修复 `MergingWindowSet.persist()` 空操作 — 实现 mapping 状态的实际持久化，确保 checkpoint 恢复后合并窗口信息不丢失

Exit Criteria:

- [x] `MemoryInternalAppendingState.add()` 连续 3 次累加结果与手动计算一致（单元测试）
- [x] 窗口聚合单元测试：2 个以上元素通过 `addWindowElement` + `getWindowContents` 验证累加结果正确
- [x] `mergeWindowContents` 合并两个子窗口后结果正确，异常路径有 WARN 日志（单元测试）
- [x] `windowNamespace()` 不再依赖 `toString()`，相同边界不同类型 Window 不碰撞（单元测试）
- [x] Checkpoint snapshot/restore 测试：多 key 多窗口场景下 restore 后每个 key 的窗口内容独立正确
- [x] 使用未实现 Serializable 的自定义 ACC 类型时，checkpoint 不抛 NotSerializableException（或文档明确标注要求）
- [x] `emitWindowContents` 方法签名包含 key 参数，无隐式状态依赖
- [x] `MergingWindowSet.persist()` 后 restore 的 mapping 与原始一致（单元测试）
- [x] 已有 checkpoint 数据（如有）不保证兼容，修复后需清空重建（在 Risks 中已声明）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P0: execute() watermark 路径修复

Status: completed
Targets: `StreamExecutionEnvironment.java`

Item Types: `Fix`

- [x] N19: 修复 `execute()` 路径不处理 `TimestampsAndWatermarksTransformation` — 在 `executePipeline()` 的 `instantiateOperators()` 中增加对此 transformation 类型的处理，实例化 watermark operator 并接入 chain。修复后 watermark 在 fast path 工作，间隔暂为硬编码 200ms（K9 将在 Phase 4 可配置化）

Exit Criteria:

- [x] `execute()` 路径的 watermark 测试：调用 `assignTimestampsAndWatermarks()` 后至少一条 watermark 事件通过 output.collect() 发出，且 currentOutputWatermark >= 测试数据的最小时间戳
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1: API 契约修复 + side output + 注释清理

Status: completed
Targets: `ValueStateDescriptor.java`, `ChainingOutput.java`, `StreamSourceOperator.java`, `DataStreamImpl.java`, `WindowOperator.java`

Item Types: `Fix`

- [x] N22: 修复 `ValueStateDescriptor(name, TypeInformation)` 丢弃 typeInfo — 将 typeInfo 正确传递给 StateDescriptor
- [x] N23: 修复 `StreamSourceOperator.run()` 正常完成后调用 cancel() — 移除 run() 后的 cancel() 调用，cancel 只由中断路径使用
- [x] N29: 修复 `KeySelectorPartitioner.partition()` null key NPE 和 Integer.MIN_VALUE 负数 — null 时使用固定分区；使用 `(hash & Integer.MAX_VALUE) % numPartitions` 避免溢出
- [x] K10: 修复 `ChainingOutput` side output 静默丢弃 — 添加 WARN 日志，而非静默丢弃
- [x] K4: `WindowedStreamImpl` 核心 API 不可用 — 标注 `@Deprecated` 并在 Javadoc 说明 "requires WindowOperatorFactory, not yet implemented"，运行时异常消息改为可操作提示。完整实现超出本计划范围（见 Non-Goals "不做新功能开发"）
- [x] K6: 清理 `WindowOperator` 大量注释残留（~200 行）— 删除已确认不再需要的注释代码块
- [x] N8: 修复 `WindowOperator.Context.getSimpleAccumulator` 返回 null（从 P3 提升到 P1：API 契约违背） — 抛 UnsupportedOperationException 并附说明文字，不返回 null

Exit Criteria:

- [x] `ValueStateDescriptor(name, typeInfo)` 构造后 `getValueType()` 不返回 null（单元测试）
- [x] `StreamSourceOperator` run 正常完成后不再调用 cancel()（单元测试）
- [x] `KeySelectorPartitioner` null key 不抛 NPE，Integer.MIN_VALUE hash 不产生负数分区（单元测试）
- [x] `ChainingOutput.collect(OutputTag, StreamRecord)` 不再静默丢弃，有 WARN 日志
- [x] `WindowedStreamImpl` 的 `apply/aggregate/reduce` 标注 `@Deprecated`，异常消息包含可操作提示
- [x] `WindowOperator.java` 中无大段注释残留（>5 行的注释块不超过 5 处）
- [x] `WindowOperator.Context.getSimpleAccumulator()` 抛 UnsupportedOperationException（不返回 null）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4a - P2: 状态管理 + 工厂模式修复

Status: completed
Targets: `MemoryKeyedStateBackend.java`, `SimpleStreamOperatorFactory.java`, `WindowOperatorTimerService.java`, `StreamExecutionEnvironment.java`

Item Types: `Fix`

- [x] N20: 修复 `checkpointExecutorFactory` static 全局字段 — 改为实例字段
- [x] N28: 修复 `SimpleStreamOperatorFactory` 返回同一对象 — 每次 `createStreamOperator()` 返回新实例
- [x] K11: 修复 `WindowOperatorTimerService` timer key=null — 在注册 timer 时从 KeyContext 获取当前 key
- [x] N25: 修复 `MemoryMapState` 丢弃 descriptor — 保存 descriptor，支持 getDefaultValue
- [x] N26/N35: `MemoryInternalAppendingState` — accumulator 标记 transient 并在 rebind 时重建；restore 后 `currentNamespace` 为 null 问题（设默认 namespace 或首次操作前 assert）
- [x] N18: `KeyedStreamImpl(parentStream, keySelector)` 构造器 environment=null — 从 parentStream 获取 environment，或标注 `@Internal` 限制使用

Exit Criteria:

- [x] `checkpointExecutorFactory` 为实例字段，不同 environment 互不干扰（测试验证）
- [x] `SimpleStreamOperatorFactory.createStreamOperator()` 两次调用返回不同对象
- [x] `WindowOperatorTimerService` 注册的 timer `getKey()` 不返回 null（单元测试）
- [x] `MemoryMapState` 保留 descriptor 引用
- [x] snapshot/restore 后 accumulator 从干净状态开始，不携带序列化前残留值（单元测试）
- [x] snapshot/restore 后首次 get/put 操作不使用 null namespace（单元测试）
- [x] `KeyedStreamImpl(parentStream, keySelector)` 不再传入 null environment，或已标注 `@Internal`
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4b - P2: Connector + 执行路径修复

Status: completed
Targets: `BatchLoaderSourceFunction.java`, `DebeziumCdcSourceFunction.java`, `TimestampsAndWatermarksOperator.java`, `JdbcCheckpointStorage.java`, `StreamSinkOperator.java`

Item Types: `Fix`

- [x] N9: 修复 `BatchLoaderSourceFunction` loader 资源未关闭 — run() 结束时关闭 loader
- [x] N10: 修复 `DebeziumCdcSourceFunction` Thread.sleep 轮询 — 改用 `CountDownLatch`，确保 cancel 后 200ms 内退出
- [x] N27: 修复 `WindowedStreamImpl` 传入 null WindowAssignerContext — 传入有效 context 或做空安全处理
- [x] K9-partial(=N33): `TimestampsAndWatermarksOperator` watermarkInterval 200ms 硬编码 — 改为可配置参数
- [x] K14: `JdbcCheckpointStorage` MySQL 方言 — 标注 `@Internal` + Javadoc "MySQL only, design prototype"
- [x] N41: 修复 `StreamSinkOperator.restoreState()` 无条件 rollback — savepoint 恢复时不应 rollback，应根据 snapshot 中记录的事务状态决定
- [x] N24/N31: 修复 `extractKeySelectors` + `wireOperatorChain` 索引映射（同一 bug 的两个侧面） — 确保索引映射在 PartitionTransformation 非标准位置时也正确

Exit Criteria:

- [x] `BatchLoaderSourceFunction.run()` 结束后 loader 被关闭（测试验证）
- [x] `DebeziumCdcSourceFunction.cancel()` 后 200ms 内 run() 退出（测试验证）
- [x] `WindowedStreamImpl` 不传入 null context
- [x] `TimestampsAndWatermarksOperator` watermark 间隔可配置
- [x] `JdbcCheckpointStorage` 有 MySQL-only `@Internal` 标注
- [x] `StreamSinkOperator.restoreState()` savepoint 恢复不执行 rollback（单元测试）
- [x] `extractKeySelectors` 在连续 PartitionTransformation 场景下索引映射正确（单元测试）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5a - P3: 死代码清理 + API 健壮性

Status: completed
Targets: `nop-stream-core`, `nop-stream-runtime`

Item Types: `Fix | Follow-up`

- [x] K17: 删除 `time.TimerService` 零引用接口（84 行）或标注 `@Deprecated` + `@Internal`
- [x] K20: 删除或保留 `Configuration` 空接口 — 如保留则标注 `@Internal` + Javadoc
- [x] K24: 清理确认无用的死代码 — 未使用的 Accumulator（614 行）标注 `@Internal`，未使用的 Function 接口标注 `@Internal`
- [x] N21: `DataStreamImpl.map()/flatMap()` 类型信息丢失 — 增加 `map(mapper, TypeInformation)` 重载
- [x] N34: `UnknownTypeInformation` 实现 `Serializable`
- [x] N40: 移除 `DataStreamImpl` 的 `implements Serializable`（environment 不可序列化）
- [x] K19: 4 个空壳模块 pom.xml 添加注释 `<!-- placeholder, planned but not implemented -->`

Exit Criteria:

- [x] `time.TimerService` 要么已删除，要么有 `@Internal` + `@Deprecated` 标注
- [x] `Configuration` 要么已删除，要么有 `@Internal` 标注
- [x] 未使用的 Accumulator/Function 接口都有 `@Internal` 标注
- [x] `DataStreamImpl` 有带 TypeInformation 参数的 `map`/`flatMap` 重载
- [x] `UnknownTypeInformation` 可序列化
- [x] `DataStreamImpl` 不再声称 Serializable
- [x] 4 个空壳模块 pom.xml 有 placeholder 注释
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5b - P3: 示例代码修复

Status: completed
Targets: `nop-stream-fraud-example`

Item Types: `Fix`

- [x] N4: 修复 `GeographicAnomalyPattern` — 将城市比较逻辑从 generateAlert 移入 CEP IterativeCondition
- [x] N5: 修复 `MockTransactionGenerator` "PASSWORD_CHANGE" → "CHANGE_PASSWORD"
- [x] N6: 修复 `UnusualAmountPattern` — 接入 `UserTransactionHistory` 做真实的用户历史平均值计算，或明确标注为 demo stub
- [x] N14: 修复 `RapidTransactionPattern` — 增加同一用户检查（使用 IterativeCondition + ctx.getEventsForPattern）
- [x] N11: 修复 `FraudDetectionDemo` — 统一使用 `MockTransactionGenerator` 替代内联数据创建

Exit Criteria:

- [x] `GeographicAnomalyPattern` 的 CEP 条件实际比较城市（只有不同城市才匹配）
- [x] `MockTransactionGenerator` 的事件类型与 Pattern 定义一致（全部用 CHANGE_PASSWORD）
- [x] `UnusualAmountPattern` 要么接入真实状态管理，要么有明确的 demo stub 标注
- [x] `RapidTransactionPattern` 检查同一用户
- [x] `FraudDetectionDemo` 运行成功，使用 `MockTransactionGenerator` 生成数据
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 端到端回归测试

Status: completed
Targets: 全部 nop-stream 模块

Item Types: `Proof`

各 Phase 的 focused verification 已嵌入各 Phase 的 Exit Criteria。本 Phase 只负责全量回归：

- [x] 全量回归测试：`./mvnw test -pl nop-stream -am`
- [x] 修复 N37: `TestEndToEndPipeline` 的 operator 不处理数据 — 添加实际数据处理验证，或标注为 graph-structure-only 测试
- [x] 修复 N38: `TestE2ESimplePipeline` — 增加基于预期结果的断言，而非仅比较两个实现的一致性

Exit Criteria:

- [x] `./mvnw test -pl nop-stream -am` BUILD SUCCESS，0 failures
- [x] `./mvnw compile -pl nop-stream -am` 无编译错误
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

---

## Closure Gates

- [x] 全部 P0 级 live defect（N1, N2, N3, N7, N12, N16, N17, K15, N19）已修复
- [x] 全部 P1 级 contract drift（K4, K6, K10, N8, N22, N23, N29）已修复
- [x] 全部 P2 级修复项已完成
- [x] P3 死代码已清理或标注，示例代码已修正
- [x] 每个修复项都有 focused test case（嵌入各 Phase Exit Criteria）
- [x] 全部审计发现（Round 1 N1-N16, Round 2 N17-N41, Known Issues K1-K24）要么已修复，要么在 `Deferred But Adjudicated` 中有显式裁定，不存在未处理的发现
- [x] 不存在被静默降级到 deferred / follow-up 的 live defect
- [x] `./mvnw test -pl nop-stream -am` BUILD SUCCESS
- [x] `./mvnw compile -pl nop-stream -am` 无编译错误
- [x] checkstyle / 代码规范检查通过
- [x] 受影响的 owner docs 已同步（或明确 No owner-doc update required）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

### K18(=N36): 双执行模型不统一

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: design-level 架构决策，不是 live defect。两条路径各有关注场景。统一需要 `IStreamExecutor` 接口和完整的策略模式，超出修复范围。
- Successor Required: yes
- Successor Path: 未来 nop-stream 架构改进计划

### K23: PriorityQueue.removeIf O(n) 性能

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前单线程、低 key cardinality 场景下性能可接受。优化不影响正确性。
- Successor Required: no

### K7: JobGraphGenerator 链节点映射 bug

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已标注"设计原型，未接入执行路径"。主流 `execute()` 不使用此路径。graph model 路径成为主流时需修复。
- Successor Required: no

### K13-partial: LocalFileCheckpointStorage catch ignored

- Classification: `watch-only residual`
- Why Not Blocking Closure: 序列化已改善，剩余 2 处 catch ignored 在正常文件系统下不触发。
- Successor Required: no

### K8-partial: CheckpointCoordinator 无自动注册

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已提供 registerTask API，自动注册需对接 JobGraph 的 task 发现机制。
- Successor Required: no

### N13: WindowOperator 状态管理类空壳（AbstractPerWindowStateStore 等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 三个内部类是内部 API，WindowContext 不暴露给外部消费者。`windowState()` 返回 IKeyedStateBackend 并设置了 namespace，功能可用但绕过了类型安全的 state store 层。完整实现需重写 PerWindowStateStore，超出修复范围。
- Successor Required: no

### N15: DebeziumCdcSourceFunction 模块耦合

- Classification: `watch-only residual`
- Why Not Blocking Closure: connector 模块整体为 design prototype，模块化改造（可选依赖、SPI 隔离）应随架构演进一起做。当前硬编码 import 在编译期就暴露问题，比运行时 SPI 加载失败更容易排查。
- Successor Required: no

### N30: Transformation.id 使用全局 AtomicInteger

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前单 JVM 单 job 场景下无影响。ID 冲突只在同一 JVM 创建大量 environment 并序列化/反序列化 DAG 时才可能发生，超出当前使用模式。
- Successor Required: no

### N32: StreamSinkOperator 被 KeyExtractingOutput 包装但不使用 key context

- Classification: `watch-only residual`
- Why Not Blocking Closure: sink 不访问 keyed state，key context 被设置但无消费者。未来 sink 需要 keyed state 时需修复。
- Successor Required: no

### K12: BarrierAligner findCompletedCheckpointId() O(N*M)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 已从 Thread.sleep 改为 Condition.await（部分修复），findCompletedCheckpointId 仍 O(N*M) 但此组件无生产引用，仅在测试中使用。
- Successor Required: no

### N39: MemoryKeyedStateBackend 序列化无容量管理

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前无大状态场景。状态大时 OOM 的风险在 MemoryStateBackend 的设计预期内（纯内存后端）。
- Successor Required: no

### K2/K3/K16/K21/K22: nop-stream-cep 模块已知问题

- Classification: `out-of-scope (nop-stream-cep, 已完善)`
- Why Not Blocking Closure: nop-stream-cep 已在本计划 Non-Goals 中明确排除，这些问题不影响 nop-stream 其他模块的正确性。
- Successor Required: no

## Non-Blocking Follow-ups

- `DataStreamImpl.map()/flatMap()` 增加基于反射或 lambda 序列化的自动类型推断（当前只增加手动传参的重载）
- `MemoryKeyedStateBackend` 序列化容量管理（大状态场景下的分片机制）
- `WindowedStreamImpl` 的 `apply/aggregate/reduce` 完整实现（当前 Phase 3 只做标注，完整实现需 WindowOperatorFactory）

## Closure

Status Note: All 8 phases completed and independently verified. `./mvnw test -pl nop-stream -am` BUILD SUCCESS with 0 failures. All P0-P3 audit findings addressed or honestly deferred.

Closure Audit Evidence:

- Reviewer / Agent: independent houyi subagent (closure audit session)
- Evidence: All 6 phases verified against live code with file:line references. All exit criteria pass. BUILD SUCCESS confirmed. Deferred items honestly classified with no live defects hidden. Full audit report recorded in execution session.

Follow-up:

- WindowedStreamImpl apply/aggregate/reduce full implementation (requires WindowOperatorFactory)
- DataStreamImpl automatic type inference via reflection/lambda serialization
- MemoryKeyedStateBackend serialization capacity management for large state
