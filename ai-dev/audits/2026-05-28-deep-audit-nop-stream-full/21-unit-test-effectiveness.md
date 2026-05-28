# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] Transformation 测试系列是 getter/setter 往返测试，保护力极弱

- **文件**: 
  - `nop-stream-core/.../transformation/TestOneInputTransformation.java` (438 行)
  - `nop-stream-core/.../transformation/TestSinkTransformation.java` (397 行)
  - `nop-stream-core/.../transformation/TestSourceTransformation.java` (361 行)
  - `nop-stream-core/.../transformation/TestPartitionTransformation.java` (309 行)
- **证据片段**:
  ```java
  // TestOneInputTransformation.java - 典型的 getter/setter 往返
  void testBasicConstructionWithoutKeySelector() {
      assertEquals("test-name", transformation.getName());
      assertEquals(4, transformation.getParallelism());
  }
  // 编译期保证的测试
  void testExtendsPhysicalTransformation() {
      assertTrue(transformation instanceof PhysicalTransformation);
  }
  ```
- **严重程度**: P1 (命中反模式 P-1)
- **现状**: 约 1,505 行测试代码，绝大多数是构造器参数 → getter 返回值的往返验证。testExtendsPhysicalTransformation 和 testSerialization 是编译期已保证的类型检查。
- **风险**: 核心逻辑改为错误实现后这些测试仍可能通过（因为只验证 getter/setter 对称性）。
- **建议**: 将测试资源转移到 PartitionRouter、基础算子、累加器等缺少测试的组件。
- **误报排除**: 不是误报。验证了"改成错误实现"假设——大部分测试无法捕获业务逻辑错误。
- **复核状态**: 未复核

### [维度21-02] TestCheckpointType 等测试元数据属性而非行为

- **文件**: 
  - `nop-stream-core/.../checkpoint/TestCheckpointType.java` (57 行)
  - `nop-stream-core/.../checkpoint/TestProcessingGuarantee.java` (33 行)
  - `nop-stream-core/.../checkpoint/TestJobTerminationContext.java` (39 行)
- **严重程度**: P2 (命中反模式 P-2)
- **现状**: 测试枚举的硬编码属性值（如 isAuto() 返回 true），属于元数据验证。
- **建议**: 保留作为 contract documentation，但不应作为主要测试投入方向。
- **误报排除**: 有边际价值——防止新枚举值被意外添加时忘记设置属性。
- **复核状态**: 未复核

## 正面发现（高质量测试范例）

- **TestStreamGraphGenerator** (544行): 多种拓扑、幂等性、错误输入、分区转换
- **TestWindowOperatorCorrectness** (587行): 回归测试，直接标注 bug 修复
- **TestNFA** (178行): CEP 核心匹配语义，begin/next/followedBy 模式
- **TestCheckpointEndToEnd** (403行): 完整 lifecycle 测试
- **TestBatchConsumerSinkFunction** (103行): 简洁有效，覆盖所有关键路径

## 有效测试 vs 低价值测试比例

| 类别 | 估算行数 | 占比 |
|------|---------|------|
| 高保护力测试 | ~8,000 | 21% |
| 中等保护力测试 | ~10,000 | 35% |
| 低价值测试 | ~6,000 | 21% |
| 边际价值测试 | ~3,000 | 10% |
| 辅助文件 | ~11,845 | 13% |
