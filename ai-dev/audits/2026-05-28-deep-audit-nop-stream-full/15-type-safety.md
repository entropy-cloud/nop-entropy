# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] NFACompiler 内部方法使用 raw Pattern 类型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:246,260,262`
- **证据片段**:
  ```java
  // Line 246
  Pattern patternToCheck = currentPattern;
  // Line 260
  private void checkPatternNameUniqueness(final Pattern pattern) {
  ```
- **严重程度**: P3
- **现状**: NFAFactoryCompiler 内部私有方法使用不带泛型参数的 raw Pattern 类型。从 Apache Flink 移植的代码。
- **风险**: 编译器 raw type 警告。运行时无实际风险（仅调用 getName()/getPrevious() 等不依赖泛型的方法）。
- **建议**: 将签名改为 `Pattern<T, ?>` 以消除警告。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度15-02] NFA.open()/close() 使用 raw IterativeCondition 类型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:193,206`
- **证据片段**:
  ```java
  // Line 193
  IterativeCondition condition = transition.getCondition();
  ```
- **严重程度**: P3
- **现状**: NFA<T> 的 open() 和 close() 方法取出 IterativeCondition 时未指定泛型参数 <T>。
- **风险**: 编译器 raw type 警告。运行时无风险（后续调用不依赖泛型参数）。
- **建议**: 改为 `IterativeCondition<T> condition = transition.getCondition();`。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度15-03] NFA.ConditionContext 使用已废弃的 Collections.EMPTY_LIST

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:927`
- **证据片段**:
  ```java
  return elements == null
          ? Collections.EMPTY_LIST.<T>iterator()
          : elements.iterator();
  ```
- **严重程度**: P3
- **现状**: 使用 Java 1.2 的 raw type 字段 Collections.EMPTY_LIST，应使用 Collections.emptyList()。
- **风险**: 编译器 raw type 警告。功能无影响。
- **建议**: 替换为 `Collections.<T>emptyList().iterator()`。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度15-04] KeyedStreamImpl sum/min/max 方法使用大量 unchecked cast

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:174-231`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  public SingleOutputStreamOperator<T> sum(int field) {
      ReduceFunction<T> reducer = (v1, v2) -> {
          if (v1 instanceof Integer) return (T) (Integer) (((Integer) v1) + ((Number) v2).intValue());
          if (v1 instanceof Long) return (T) (Long) (((Long) v1) + ((Number) v2).longValue());
          // ...
  ```
- **严重程度**: P2
- **现状**: sum(int), min(int), max(int) 使用 instanceof + 显式强转处理不同数值类型。T 无法在编译期约束为 Number 或 Comparable。不支持 Short/Byte/BigDecimal。
- **风险**: 如果 T 不在预设类型中会抛异常而非编译期报错。min/max 中 (Comparable<T>) 强转可能 ClassCastException。
- **建议**: 短期：在方法 Javadoc 中标注 T 的约束。中期：增加 Short/Byte/BigDecimal 分支或运行时类型校验。
- **误报排除**: 部分可排除——流处理框架中聚合函数对原始类型的动态分派是常见性能优化模式（Flink 原版也类似）。
- **复核状态**: 未复核

## 已验证合规项

- 集合类全部正确参数化，无 raw Map/List/Set 字段
- StateDescriptor 层次结构完整参数化
- 核心接口（DataStream, KeyedStream, WindowedStream, StreamOperator）泛型精度良好
- 约 90 处 @SuppressWarnings("unchecked") 使用均有合理上下文
- instanceof + cast 模式均为标准 equals/tagged union/功能检测，无不合理使用
- SharedBuffer 的 raw Class 强转是泛型擦除下的经典取舍，已有注释说明
- OperatorSnapshotResult 和 MemoryKeyedStateBackend 的 Object 存储是异构容器的合理模式
