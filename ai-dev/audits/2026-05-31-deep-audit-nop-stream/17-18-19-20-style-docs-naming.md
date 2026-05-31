# 维度 17/18/19/20：代码风格、文档、命名、跨模块

## 维度 17：代码风格与规范

- 17-01 (P2): sourceIDs/sinkIDs 应为 sourceIds/sinkIds（StreamGraph.java）
- 17-02 (P2): TimeBehaviour 枚举用 PascalCase 而非 UPPER_SNAKE_CASE（PatternStreamBuilder.java）
- 17-03 (P2): FraudDetectionDemo 同时使用 System.out 和 LOG
- 17-04 (P2): CepOperator.java 未使用的 import java.util.HashSet
- 17-05 (P2): 4处冗余 NopStreamErrors 非 static import
- 17-06 (P2): 约 10+ 文件 import 分组不规范

## 维度 18：文档-代码一致性

- 18-01 (P1): nop-stream 在 docs-for-ai/ 导航体系中完全缺位（INDEX.md、module-groups.md 均未提及）
- 18-02 (P2): source-anchors.md 无任何 nop-stream 实现锚点
- 18-06 (P3): error-handling.md 只提及 2 个构造器变体，实际有 4 个
- 一致确认：README.md 模块状态、五层管线描述、Quick Start 示例、外部引用均与代码一致

## 维度 19：命名与术语一致性

- 19-01 (P3): SinkFunction.consume vs TwoPhaseCommitSinkFunction.invoke
- 19-02 (P3): 三个同名 TimerService 接口（已废弃的 core/time、InternalTimerService、cep/time）
- 19-03 (P3): ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP 错误码字符串语义模糊
- 19-04~11 (信息): FollowKind camelCase vs AfterMatchSkipStrategyKind UPPER_SNAKE_CASE、CEP 错误码前缀策略、StreamConstants 空类等

## 维度 20：跨模块契约一致性

- 20-01 (P3): nop-stream-api 空壳，接口未分离
- 20-02 (P2): BatchConsumerSinkFunction 未覆盖 SinkFunction.finish()，刷出在 close() 中
- 20-03 (P3): 连接器依赖 batch-core 内部实现类（BatchChunkContextImpl）
- 20-04 (P3): DebeziumCdcSourceFunction 使用 ICancellable 与 IMessageSubscription 设计不统一
- 20-05 (P3): JdbcCheckpointStorage/Registry 硬编码方言特定 DDL
- 20-06 (P3): NopStreamConfigs 不存在，缺少框架级集中配置
- 20-07/08 (无问题): nop-dao provided scope 正确、nop-message-core test scope 正确
