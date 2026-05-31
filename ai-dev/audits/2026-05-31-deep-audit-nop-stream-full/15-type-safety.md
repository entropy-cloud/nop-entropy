# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-05] WindowOperator.addWindowElement 不安全未检查转换

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:729,732,742-745`
- **证据片段**:
  ```java
  setWindowContents(key, window, (ACC) accumulator);  // L729
  setWindowContents(key, window, (ACC) value);          // L732
  ```
- **严重程度**: P1
- **现状**: IN 到 ACC 的转换无 instanceof 前置检查，依赖 try-catch(ClassCastException)
- **风险**: 类型混淆可能在 mergeWindowContents 中造成数据丢失
- **建议**: 引入 TypeStrategy 接口封装 IN→ACC 转换，或添加前置 instanceof 检查
- **信心水平**: 确定
- **误报排除**: 当前 try-catch 模式在写入 state 之后才发现错误，可能导致部分状态已写入
- **复核状态**: 未复核

### [维度15-06] WindowAggregationOperator 反序列化多重未检查转换

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:605-606,619,621,624`
- **证据片段**:
  ```java
  K key = (K) JsonTool.parseBeanFromText(parts[0], keyClass);  // L605
  ```
- **严重程度**: P1
- **现状**: 反序列化路径大量 (K)(W)(ACC) 转换无类型验证
- **风险**: checkpoint 数据损坏或版本不匹配时 ClassCastException 在不确定位置抛出
- **建议**: 添加显式类型检查 `if (!keyClass.isInstance(key)) throw ...`
- **信心水平**: 确定
- **误报排除**: 反序列化来自持久化存储，数据可能不匹配
- **复核状态**: 未复核

### [维度15-01] KeyContext 接口使用 Object 代替泛型参数

- **文件**: `nop-stream-core/.../operators/KeyContext.java:29-31`
- **严重程度**: P2
- **现状**: setCurrentKey(Object)/getCurrentKey() 缺少泛型参数，所有实现类需 unchecked cast
- **建议**: 改为 KeyContext<K>
- **信心水平**: 很可能
- **误报排除**: 影响面较广（10+ 实现类），但收益明确
- **复核状态**: 未复核

### [维度15-02] NFA.java 使用原始类型 Collections.EMPTY_LIST

- **文件**: `nop-stream-cep/.../nfa/NFA.java:934`
- **严重程度**: P2
- **现状**: `Collections.EMPTY_LIST.<T>iterator()` 使用原始类型
- **建议**: 替换为 `Collections.<T>emptyList().iterator()`
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度15-03] CepPatternBuilder 大量使用原始类型 Pattern

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java` (8 处 @SuppressWarnings("rawtypes"))
- **严重程度**: P2
- **现状**: 整个 builder 链中类型参数丢失
- **建议**: 引入泛型 T 贯穿 builder，或至少使用 Pattern<?,?>
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度15-07] OperatorSnapshotResult 使用 Map<String, Object> 存储状态

- **文件**: `nop-stream-core/.../checkpoint/OperatorSnapshotResult.java:22-24`
- **严重程度**: P2
- **现状**: 所有 operator 快照状态以 Object 类型存储
- **建议**: 添加泛型方法 `<T> T getOperatorState(String key, Class<T> type)` 提供运行时类型检查
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度15-12] HeapInternalTimerService key 为 Object 而非泛型 K

- **文件**: `nop-stream-core/.../operators/HeapInternalTimerService.java:135,161`
- **严重程度**: P2
- **建议**: 改为 HeapInternalTimerService<K, N>
- **信心水平**: 很可能
- **复核状态**: 未复核

## 维度复核结论

类型安全问题主要集中在状态序列化/反序列化路径和 KeyContext/Timer 的泛型缺失。两个 P1 问题涉及运行时 ClassCastException 风险，应优先修复。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 15-05 | P1 | WindowOperator.java | addWindowElement 不安全转换 |
| 15-06 | P1 | WindowAggregationOperator.java | 反序列化多重未检查转换 |
| 15-01 | P2 | KeyContext.java | Object 代替泛型参数 |
| 15-02 | P2 | NFA.java | 原始类型 EMPTY_LIST |
| 15-03 | P2 | CepPatternBuilder.java | 8 处 rawtype |
| 15-07 | P2 | OperatorSnapshotResult.java | Map<String, Object> |
| 15-12 | P2 | HeapInternalTimerService.java | key 为 Object |
