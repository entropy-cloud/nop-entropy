# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] CountTrigger 核心算子几乎无有效测试

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/windowing/triggers/TestCountTrigger.java:8-15`
- **证据片段**:
  ```java
  @Test
  void testCountTriggerCannotMerge() {
      CountTrigger<TimeWindow> trigger = CountTrigger.of(5);
      assertFalse(trigger.canMerge(), "CountTrigger.canMerge() should return false");
  }
  ```
- **严重程度**: P2
- **现状**: 仅 1 个测试方法验证 canMerge() 返回 false，onElement 的核心计数-触发逻辑完全未覆盖。
- **风险**: CountTrigger 是窗口触发器核心组件，核心行为无测试保护。
- **建议**: 添加 onElement 计数递增、达到阈值后 FIRE、clear 重置的测试。
- **信心水平**: 确定
- **误报排除**: 不是误报——生产代码 44-53 行的核心逻辑完全无测试。
- **复核状态**: 未复核

### [维度16-02] TestWindowOperatorBasic 名不副实，仅测试基础数据结构属性

- **文件**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorBasic.java:23-72`
- **严重程度**: P2
- **现状**: 类名暗示测试 WindowOperator 基础功能，实际只测试 TimeWindow getter/intersects 和 assigner/trigger 创建。
- **建议**: 重命名为 TestWindowAssignersAndTriggers 或补充 WindowOperator 行为测试。
- **信心水平**: 确定
- **误报排除**: 不是误报——TestWindowOperatorBehavior 才是真正的算子行为测试。
- **复核状态**: 未复核

### [维度16-03] TestProcessingGuarantee 全部 4 个测试仅验证固定布尔返回值

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestProcessingGuarantee.java:9-32`
- **严重程度**: P3
- **现状**: 全部 4 个方法验证枚举常量的固定布尔属性（P-2 元数据测试）。
- **建议**: 可保留但标记为低价值。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

### [维度16-04] TestCheckpointConfig.testSettersAndGetters 纯 getter/setter 往返测试

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/checkpoint/TestCheckpointConfig.java:40-61`
- **严重程度**: P3
- **现状**: 典型的 P-1 反模式，每个 setter 后立即 assertEqual 对应 getter。
- **建议**: 低优先级改进。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

### [维度16-05] TestWindowedStreamAggregation 中 assertNotNull 模式过于宽泛

- **文件**: `nop-stream-core/src/test/java/io/nop/stream/core/datastream/TestWindowedStreamAggregation.java:46-118`
- **严重程度**: P3
- **现状**: 3 个测试方法使用相同的 assertNotNull(result) + assertNotNull(tx) 模式，仅额外检查 transformation name。
- **建议**: 添加 DAG 拓扑正确性验证。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

## 测试亮点

- TestCheckpointCoordinator 覆盖了正常/错误/并发/幂等/超时/清理场景，是模块标杆
- TestWindowOperatorBehavior 边界条件测试精确到位
- TestNFAExtended 覆盖了复杂 CEP 模式（oneOrMore/optional/times/greedy）
- TestLockable 测试了并发安全性
- TestGreedy 验证贪婪 vs 非贪婪行为差异
