# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] CountTrigger 核心逻辑完全未被测试（P-3：happy path 缺失）

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/windowing/triggers/TestCountTrigger.java:8-15`
- **证据片段**:
  ```java
  @Test
  void testCountTriggerCannotMerge() {
      CountTrigger<TimeWindow> trigger = CountTrigger.of(5);
      assertFalse(trigger.canMerge());
  }
  ```
- **严重程度**: P2
- **现状**: onElement 的核心计数递增、阈值触发、clear 重置行为完全无测试。
- **风险**: 核心触发逻辑变更无测试保护。
- **建议**: 添加完整的 onElement 行为测试。
- **信心水平**: 确定
- **误报排除**: 不是误报——与维度16-01是同一问题的不同角度。
- **复核状态**: 未复核

### [维度21-02] TestStreamGraphGenerator 中 assertNotNull 滥用（30+ 处）

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/graph/TestStreamGraphGenerator.java:74-78,91-103`
- **证据片段**:
  ```java
  assertNotNull(streamGraph);
  assertNotNull(sourceNode);
  ```
- **严重程度**: P2
- **现状**: 30+ 处 assertNotNull 仅验证对象非 null，不验证任何属性。一个 new Object() 就能让几乎所有断言通过。
- **建议**: 添加节点属性验证（operator、parallelism、分区器类型等）。
- **信心水平**: 确定
- **误报排除**: 不是误报——assertNotNull 对复杂对象几乎无保护力。
- **复核状态**: 未复核

### [维度21-03] fraud-example 中 4 处 testConstants/testGetters（P-2 元数据测试）

- **文件**: TestRapidTransactionPattern.java:100-107, TestAccountTakeoverPattern.java:212-217, TestGeographicAnomalyPattern.java:167-172, TestUnusualAmountPattern.java:100-103
- **严重程度**: P3
- **现状**: 断言 getFraudType() 返回固定字符串和配置参数返回固定数字，常量改了测试也跟着改。
- **建议**: 已标记 @Tag("low-value")，可接受。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

### [维度21-04] TestNFAStateNameHandler.testStateNameDelim 测试常量值（P-2/P-4）

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFAStateNameHandler.java:117-121`
- **严重程度**: P3
- **现状**: 直接断言 STATE_NAME_DELIM 常量值为 ":"，与实现高度耦合。
- **建议**: 已标记 @Tag("low-value")，可接受。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

### [维度21-05] 大量测试方法名不表达预期行为（P-6）

- **文件**: TestNFAExtended, TestCheckpointCoordinator, TestPendingCheckpoint 等多个文件
- **严重程度**: P3
- **现状**: 方法名如 testOptional(), testTimes(), testShutdown() 不表达预期行为。
- **建议**: 长期改进。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

## 测试亮点

- TestLockable 使用 CountDownLatch 同步 20 个线程验证并发安全
- TestGreedy 精确验证贪婪 vs 非贪婪行为差异
- TestNFAExtended 覆盖复杂 CEP 模式
