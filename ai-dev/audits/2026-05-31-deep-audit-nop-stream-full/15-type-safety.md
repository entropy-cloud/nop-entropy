# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] CepPatternBuilder 全类使用原始类型 Pattern（8 处 @SuppressWarnings("rawtypes")）

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:28-193`
- **证据片段**:
  ```java
  @SuppressWarnings("rawtypes")
  public Pattern buildFromModel(CepPatternModel patternModel) {
      Pattern pattern = buildGroupPattern(patternModel);
  ```
- **严重程度**: P2
- **现状**: 整个 CepPatternBuilder 的 8 个方法全部使用 raw Pattern，丢失全部泛型信息。
- **风险**: Pattern 链上的类型不匹配只能在运行时暴露。
- **建议**: CepPatternModel 来自 XML 动态配置，编译期类型信息不可用是设计取舍。可在模型层增加运行时类型校验。
- **信心水平**: 很可能
- **误报排除**: Nop 平台动态边界的合理设计取舍，但整类 8 处 raw type 是代码气味。
- **复核状态**: 未复核

### [维度15-02] CepOperator 使用 Object.class 作为 key 类型创建状态后端

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:193,204`
- **证据片段**:
  ```java
  this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(Object.class);
  keyedStateStore = new MemoryKeyedStateBackend<>(Object.class);
  ```
- **严重程度**: P2
- **现状**: CepOperator<IN, KEY, OUT> 已声明 KEY 泛型参数，但使用 Object.class 而非 KEY 的 class token。
- **风险**: 切换到持久化后端时 key 序列化/反序列化可能出错。当前 MemoryKeyedStateBackend 直接返回 key 不做转换，暂无影响。
- **建议**: 通过构造函数传入 keyClass 参数。
- **信心水平**: 很可能
- **误报排除**: 不是误报——与 KEY 泛型参数的设计意图矛盾。
- **复核状态**: 未复核

### [维度15-03] KeyedStreamImpl 的 sum/min/max 使用不安全的类型窄化转换

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:178-191,200-212,221-233`
- **证据片段**:
  ```java
  // 行 183-188
  ReduceFunction<T> reducer = (v1, v2) -> {
      if (v1 instanceof Integer) return (T) (Integer) (((Integer) v1) + ((Number) v2).intValue());
      if (v1 instanceof Long) return (T) (Long) (((Long) v1) + ((Number) v2).longValue());
      throw new StreamException(ERR_STREAM_NUMBER_REQUIRED);
  };
  ```
- **严重程度**: P2
- **现状**: T 不是 Number 子类时运行时才报错。Flink 原始 API 有类似设计。
- **风险**: API 层面的类型安全缺陷，但有前置错误码保护。
- **建议**: Flink 兼容 API 的固有设计限制，低优先级改进。
- **信心水平**: 确定
- **误报排除**: Flink 兼容 API 的设计限制。
- **复核状态**: 未复核

### [维度15-04] WindowAggregationOperator 反序列化中使用大量未校验的泛型强转

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:599-673`
- **证据片段**:
  ```java
  K key = (K) JsonTool.parseBeanFromText(parts[0], keyClass);
  W window = (W) JsonTool.parseBeanFromText(parts[1], windowClass);
  target.put(wk, (ACC) value); // 从 Object 强转 ACC
  ```
- **严重程度**: P2
- **现状**: 从 JSON Map<String, Object> 反序列化时无 isInstance 检查，依赖 JsonTool 的正确性。
- **风险**: 快照数据损坏或类型不匹配时产生堆污染。
- **建议**: 添加反序列化结果的运行时类型检查。
- **信心水平**: 很可能
- **误报排除**: 序列化/反序列化层是类型安全的传统薄弱点。
- **复核状态**: 未复核

### [维度15-05] MemoryKeyedStateBackend 使用 Map<String, Object> 存储状态

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:63,120-198`
- **证据片段**:
  ```java
  private final Map<String, Object> states = new HashMap<>();
  @SuppressWarnings("unchecked")
  ValueState<T> state = (ValueState<T>) states.get(stateProperties.getName());
  ```
- **严重程度**: P2
- **现状**: 所有状态存取都需要 unchecked cast，是大量类型安全问题的根源。
- **风险**: 无法在编译期防止向 states map 中放入错误类型。
- **建议**: 长期考虑改为类型安全的 heterogeneous container 模式。
- **信心水平**: 很可能
- **误报排除**: Java 泛型擦除在状态框架中的经典设计问题，Flink 原版也有类似设计。
- **复核状态**: 未复核

### [维度15-06~12] 其他低优先级泛型问题（P3）

- NFACompiler 中 11 处 @SuppressWarnings("unchecked") 用于条件类型转换
- SharedBuffer/CepOperator 使用 (Class) Lockable.class 绕过泛型 token
- StreamTaskInvokable 使用原始类型进行 operator 链接线
- 多个 Trigger 实现使用 Trigger<Object, W> 硬编码元素类型
- StreamMessageEnvelope 的 payload 字段使用 Object 类型
- MemoryStateSerDe 的序列化方法返回 Object
- WindowAggregationOperator 中 MergingWindowAssigner 使用 Object 而非 K

以上均为 Flink 兼容 API 的设计限制或 Java 泛型擦除的固有局限，标记为 P3。
