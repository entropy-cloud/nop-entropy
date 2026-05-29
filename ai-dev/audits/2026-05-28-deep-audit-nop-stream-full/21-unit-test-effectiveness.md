# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestSharedBuffer tests only cover basic registration -- no retrieval, lookup, or edge-case testing

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBuffer.java`
- **严重程度**: P2
- **现状**: SharedBuffer 是 CEP 引擎中最复杂的数据结构之一，但测试仅覆盖基本注册和缓存大小计数。无检索、查找、锁定语义或其在实际模式匹配中的角色测试。
- **建议**: 添加 SharedBuffer 检索、锁定/解锁语义和边界条件的测试。
- **误报排除**: SharedBuffer 是 CEP 引擎的核心共享缓冲区，其引用计数和缓存淘汰逻辑是关键正确性保障。
- **复核状态**: 未复核

### [维度21-02] ~20-25% of test methods are low-value (P-1 getter/constant assertions)

- **文件**: TestDeweyNumber, TestWindowingModel, fraud-example testConstants() 方法
- **严重程度**: P3
- **现状**: 约35-40个测试方法是 getter/常量往返测试或元数据属性测试，保护力弱。
- **建议**: 低优先级清理，不阻塞。
- **误报排除**: 这是测试有效性分布问题，不是功能性缺陷。
- **复核状态**: 未复核

### 测试有效性总体评估

- 总测试：318个
- 有效行为测试：约75-80%（~150-155个方法）
- 低价值测试：约20-25%（~35-40个方法）
- 关键路径测试质量高：NFA, NFACompiler, Pattern, CheckpointCoordinator
- 测试盲区：ComputationState, CepOperator late-data, NFA window timeout（见维度16）
