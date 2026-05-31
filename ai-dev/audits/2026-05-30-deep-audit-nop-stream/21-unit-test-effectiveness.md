# 维度 21：单元测试有效性

## 关键发现

### [21-01] 恒真断言 — 测试永远通过

- **反模式**: P-5, P-3
- **文件**: `TestNFAExtended.java:626`
- **证据片段**:
  ```java
  assertTrue(matches.isEmpty() || !matches.isEmpty());
  ```
- **严重程度**: P1（伪装成 timeout 行为测试，实际什么都没验证）

### [21-02] 弱断言 >= 1 无法区分正确与错误匹配数量 (7处)

- **反模式**: P-5, P-3
- **文件**: `TestNFAExtended.java:247,274,355,474,497,521,645`
- **严重程度**: P2

### [21-03] 纯 getter/setter 往返测试 (~6处)

- **反模式**: P-1
- **严重程度**: P3

### [21-04] 枚举成员遍历/toString 测试 (~15处)

- **反模式**: P-2
- **严重程度**: P3

### [21-05] assertNotNull 过度使用 (535处，部分为唯一断言)

- **反模式**: P-5
- **严重程度**: P3

### [21-06] 方法名不表达预期行为

- **反模式**: P-6
- **文件**: TestNFAExtended.java, TestWindowOperatorCorrectness.java:542 等
- **严重程度**: P2（testMergeTypeIncompatibilityThrowsException 名不副实，实际只测 happy path）

### [21-07] JSON 序列化只测 happy path

- **反模式**: P-3
- **文件**: TestWindowJsonSerialization.java
- **严重程度**: P2

### [21-08] 枚举 ordinal 耦合测试

- **反模式**: P-4
- **文件**: TestConnectorConsistencyCapability.java:53-71
- **严重程度**: P2

## 高质量测试（正面发现）

- TestCheckpointParticipantIntegration (777行): 精确动作序列断言 ✓
- TestWindowOperatorCorrectness (587行): 精确值断言 + 边界条件 ✓
- TestWindowOperatorIntegration (412行): watermark 边界测试 ✓
- TestBarrierAligner (170行): 完整状态转换覆盖 ✓
- TestCheckpointCoordinator (304行): 存储失败 + 计数器泄漏测试 ✓
- TestCheckpointConcurrencySafety (148行): 并发竞态测试 ✓

## 低价值测试比例

| 分类 | 估算方法数 | 占比 |
|------|-----------|------|
| 高价值 | ~1,100 | 76% |
| 中等价值 | ~200 | 14% |
| 低价值 | ~141 | 10% |

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 21-01 | P1 | TestNFAExtended.java:626 | 恒真断言 |
| 21-02 | P2 | TestNFAExtended.java | 弱断言 >= 1 (7处) |
| 21-06 | P2 | 多文件 | 方法名不表达预期行为 |
| 21-07 | P2 | TestWindowJsonSerialization | 只测 happy path |
| 21-08 | P2 | TestConnectorConsistencyCapability | ordinal 耦合 |
| 21-03~21-05 | P3 | 多文件 | getter/setter/枚举/assertNotNull (低价值) |
