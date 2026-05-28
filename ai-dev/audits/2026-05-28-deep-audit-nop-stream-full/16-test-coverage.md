# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] PartitionRouter 及其三个实现完全没有单元测试

- **文件**: `nop-stream-core/.../execution/PartitionRouter.java`, `HashPartitionRouter.java`, `ForwardPartitionRouter.java`, `RebalancePartitionRouter.java`
- **严重程度**: P1
- **现状**: 数据分区的核心路由逻辑零测试。HashPartitionRouter 使用 Math.abs(channel % numPartitions)，当 channel == Integer.MIN_VALUE 时 Math.abs() 仍返回负数（经典 hash bug）。RebalancePartitionRouter 使用 AtomicLong 递增，同样存在溢出风险。
- **风险**: 分区逻辑错误导致数据丢失或重复处理，但没有测试能捕获。
- **建议**: 为 PartitionRouter 工厂方法和三个实现类添加单元测试，覆盖边界值（Integer.MIN_VALUE, numPartitions=1, 负数 channel 等）。
- **误报排除**: 不是误报。路由逻辑是引擎正确性的基石。
- **复核状态**: 未复核

### [维度16-02] core/operators 包 34 个源文件仅 13 个测试，基础算子缺直接测试

- **文件**: `nop-stream-core/.../operators/` 目录
- **严重程度**: P2
- **现状**: StreamFilter, StreamFlatMap, StreamMap, StreamSinkOperator, StreamSourceOperator 等无直接测试。部分通过 E2E 测试间接覆盖。
- **风险**: 基础算子的独立行为（异常传播、生命周期管理）缺少测试保护。
- **建议**: 为 StreamFilter, StreamFlatMap, StreamMap 添加基本单元测试。
- **误报排除**: 部分间接覆盖存在，但不足以捕获算子级别的 bug。
- **复核状态**: 未复核

### [维度16-03] common 子包测试不足 — accumulators 15 源文件仅 1 测试，eventtime 18 源文件仅 3 测试

- **文件**: `nop-stream-core/.../common/accumulators/`, `common/eventtime/`
- **严重程度**: P2
- **现状**: 累加器是窗口计算核心，水位线生成器影响数据处理正确性，但测试薄弱。
- **风险**: 累加器/水位线逻辑错误不易被发现。
- **建议**: 为关键累加器和水位线策略添加测试。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度16-04] CEP pattern/conditions 包 9 个源文件完全没有测试

- **文件**: `nop-stream-cep/.../pattern/conditions/` 目录
- **严重程度**: P2
- **现状**: RichAndCondition, RichOrCondition, RichNotCondition 等条件组合逻辑无专门测试。
- **建议**: 为条件组合的 AND/OR/NOT 语义添加测试。
- **误报排除**: 无。
- **复核状态**: 未复核

## 已验证合规项

- 测试框架统一使用 JUnit 5，框架选择一致
- 总测试方法数 1,309，测试/源码行数比合理（0.73-2.03）
- 高质量测试示例：TestStreamGraphGenerator, TestWindowOperatorCorrectness, TestNFA, TestCheckpointEndToEnd
- 错误路径测试：core 有 92 个 assertThrows，分布合理
