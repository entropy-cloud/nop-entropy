# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-stream（流处理引擎框架模块）
- **审核日期**: 2026-05-30
- **执行维度**: 全部 21 个维度（维度 12 因无 GraphQL 层判定为不适用）
- **目标范围**: nop-stream 下 10 个子模块（core、cep、runtime、connector、fraud-example + 4 个空占位符 + api 空壳），630 个 Java 文件（423 主源文件 + 207 测试文件），90,924 行代码

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01-依赖图 | 1 | 4 | 0 | 4 | 0 | 0 |
| 02-模块职责 | 1 | 1 | 0 | 1 | 0 | 0 |
| 03-API表面 | 1 | 3 | 0 | 3 | 0 | 0 |
| 04-数据模型 | 1 | 4 | 0 | 4 | 1 (P1→P2) | 0 |
| 05-生成管线 | 1 | 1 | 0 | 1 | 0 | 0 |
| 06-Delta定制 | 1 | 0 | 0 | 0 | 0 | 0 |
| 07-服务接口 | 1 | 3 | 0 | 3 | 0 | 0 |
| 08-IoC配置 | 1 | 0 | 0 | 0 | 0 | 0 |
| 09-错误处理 | 1 | 2 | 0 | 2 | 0 | 0 |
| 10-XDSL | 1 | 0 | 0 | 0 | 0 | 0 |
| 11-模型对齐 | 1 | 0 | 0 | 0 | 0 | 0 |
| 12-GraphQL | N/A | 0 | 0 | 0 | 0 | 0 |
| 13-安全 | 1 | 1 | 0 | 1 | 0 | 0 |
| 14-异步事务 | 1 | 0 | 0 | 0 | 0 | 0 |
| 15-类型安全 | 1 | 2 | 0 | 2 | 0 | 0 |
| 16-测试覆盖 | 1 | 0 | 0 | 0 | 0 | 0 |
| 17-代码风格 | 1 | 1 | 0 | 1 | 0 | 0 |
| 18-文档一致性 | 1 | 0 | 0 | 0 | 0 | 0 |
| 19-命名一致性 | 1 | 2 | 0 | 2 | 0 | 0 |
| 20-跨模块契约 | 1 | 0 | 0 | 0 | 0 | 0 |
| 21-测试有效性 | 1 | 1 | 0 | 1 | 0 | 0 |
| **合计** | | **25** | **0** | **25** | **1** | **0** |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 10 | API 封装（3）、状态管理（4）、模块治理（2）、SPI 一致性（1） |
| P3 | 15 | 命名风格、import 分组、@Internal 标注、测试标记、依赖精确性 |

## 关键发现摘要

### P2 发现（10 项）

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| 01-01 | 依赖图 | runtime/pom.xml | runtime main 代码硬引用 nop-dao (provided)，需文档化运行时 classpath 要求 |
| 01-04 | 依赖图 | pom.xml | fraud-example 示例模块参与主构建发布，与项目惯例不一致 |
| 02-01 | 模块职责 | WindowAggregationOperator.java | 825 行文件混合 ~200 行自定义 JSON 序列化逻辑（25%） |
| 03-01 | API表面 | AbstractStreamOperator.java | 23 个框架内部方法应为 protected 而非 public（修复需跨模块重构） |
| 03-02 | API表面 | ReduceFunction/AggregateFunction/WindowFunction | 用户面向接口标注 @Internal，与 MapFunction/FilterFunction 不一致 |
| 04-01 | 数据模型 | NFAState/CepOperator/MemoryStateSerDe | CEP NFAState 整个对象图不可 JSON 反序列化，checkpoint 恢复失败（已标注未完成功能） |
| 04-02 | 数据模型 | StateSnapshot/CompletedCheckpoint | 内部可变 Map 直接暴露，同项目中已有 unmodifiableMap 正确模式 |
| 04-03 | 数据模型 | MemoryKeyedStateBackend | states 使用非线程安全 HashMap，与 TaskStateSnapshot 的 ConcurrentHashMap 不一致 |
| 04-04 | 数据模型 | CheckpointCoordinator | currentFingerprint 字段声明在第 515 行，远离其余字段（第 31-53 行） |
| 07-01 | 服务接口 | ICheckpointExecutorFactory SPI | SPI 文件存在但从未被 ServiceLoader 加载，与 IDeploymentPlanProvider 不一致 |
| 09-01 | 错误处理 | KeyedStreamImpl | 6 处公共 API 方法使用 UnsupportedOperationException 而非 ErrorCode |

### P3 发现（15 项）

| 编号 | 维度 | 摘要 |
|------|------|------|
| 01-02 | 依赖图 | connector 的 nop-message-core optional 依赖在 main 代码中未使用，应改为 test |
| 01-03 | 依赖图 | 4 个空占位符模块在主构建中增加维护噪声 |
| 03-03 | API表面 | 5 个 Function 接口冗余声明 Serializable |
| 05-01 | 生成管线 | CEP XDSL 模型 XML round-trip 无测试覆盖 |
| 07-02 | 服务接口 | fraud-example 硬编码 DEMO STUB + UserTransactionHistory 未被引用 |
| 07-03 | 服务接口 | @Internal 标注在核心函数接口上使用不一致（StreamFunction internal 但 SourceFunction 不标记） |
| 09-02 | 错误处理 | CEP 子模块 10+ 处 MalformedPatternException 使用字符串而非 ErrorCode |
| 13-01 | 安全 | SimpleTypeSerializer.deserialize() 未调用 ClassNameValidator（纵深防御缺失） |
| 15-01 | 类型安全 | StreamComponents.getBean() 的 Class<T> 参数未使用 |
| 15-02 | 类型安全 | MessageSourceFunction 消息体盲目转型无 instanceof 检查 |
| 17-01 | 代码风格 | 92+175 个文件 import 分组不符合 AGENTS.md 规范 |
| 19-01 | 命名 | sourceIDs/sinkIDs 不符合 camelCase 约定 |
| 19-02 | 命名 | ShardPrefixedKey 同名类出现在两个包中 |
| 21-01 | 测试有效性 | TestCheckpointBarrier/TestProcessingGuarantee 低价值测试未标记 @Tag("low-value") |

## 总评

nop-stream 模块整体质量较高，是一个设计成熟的流处理引擎框架。

**优点**：
1. **架构清晰**：core→cep/runtime/connector 分层干净，无循环依赖，依赖方向正确
2. **错误处理优秀**：完整的异常层次（StreamException/StreamRuntimeException/CheckpointStorageException/MalformedPatternException）+ 43 个结构化 ErrorCode，无空 catch 块、无 .printStackTrace()、无中文错误消息
3. **并发安全成熟**：CheckpointCoordinator/TaskManager/JobCoordinator 大量使用 CAS/ConcurrentHashMap/AtomicReference，竞态条件得到系统性防护
4. **测试覆盖充分**：202 个测试文件，核心路径（窗口/CEP/checkpoint/状态）E2E 测试链路完整
5. **生成管线完整**：XDEF → codegen → 手写扩展 → Builder 桥接的完整链路无断裂
6. **安全基线良好**：SQL 全参数化、ClassNameValidator 白名单、路径遍历双重保护、fencing token 防脑裂

**主要改进方向**：
1. **API 封装**：AbstractStreamOperator 的 23 个 public 方法应收缩为 protected，需跨模块重构（03-01）
2. **@Internal 一致性**：函数接口的 @Internal 标注策略需要统一审查（03-02、07-03）
3. **CEP Checkpoint 序列化**：NFAState 对象图不可 JSON 反序列化，需要在 CEP 声明生产就绪前修复（04-01）
4. **状态管理防御性**：可变 Map 暴露和非线程安全 HashMap 需要加固（04-02、04-03）
5. **模块治理**：fraud-example 应从主构建中移除，空占位符模块应考虑 profile 化（01-03、01-04）

## 优先修复建议

1. **[P2-高优先]** 统一审查 `@Internal` 标注策略：移除 ReduceFunction/AggregateFunction/WindowFunction 上的 @Internal（03-02），同步审查 StreamFunction 和其他函数接口（07-03）
2. **[P2-高优先]** 修复 CEP checkpoint 序列化：为 NFAState/ComputationState/EventId/NodeId/DeweyNumber 实现自定义 TypeSerializer 或 Jackson 注解（04-01）
3. **[P2-中优先]** 将 fraud-example 从主构建中移除（移到 nop-demo 或 profile 化）（01-04）
4. **[P2-中优先]** 收缩 AbstractStreamOperator 的方法可见性（需要重构 GraphModelCheckpointExecutor/CheckpointPlanBuilder/StreamTaskInvokable 的调用方式）（03-01）
5. **[P2-中优先]** 统一可变 Map 防御：StateSnapshot/CompletedCheckpoint/OperatorSnapshotResult/TaskStateSnapshot 返回 unmodifiableMap（04-02）
6. **[P2-低优先]** WindowAggregationOperator 序列化逻辑提取（02-01）、ICheckpointExecutorFactory SPI 修复（07-01）、KeyedStreamImpl ErrorCode 统一（09-01）

## 本次审核盲区自评

1. **深挖轮次未执行**：由于初审发现以 P2/P3 为主，未进行追加深挖轮次。可能遗漏了深度隐藏的问题。
2. **性能热点未审计**：未检查窗口算子、NFA、SharedBuffer 在高吞吐场景下的性能瓶颈。
3. **Flink 兼容性未深度对比**：只做了表层对比，未逐方法验证 nop-stream 与 Apache Flink 的 API 兼容性。
4. **runtime 模块的分布式场景**：TestDistributedExactlyOnce 等测试使用了模拟而非真实分布式环境，真实的网络分区/节点故障场景未审计。
5. **connector 模块的集成测试**：Debezium/MQ 连接器的实际集成测试覆盖未验证。
