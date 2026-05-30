# 维度 15：类型安全与泛型使用

### [维度15-01] StreamComponents.getBean() 缺少 instanceof 类型校验

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:135-142`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  public <T> T getBean(String id, Class<T> clazz) {
      Object bean = windowingStrategies.get(id);
      if (bean == null) {
          throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Bean not found: " + id);
      }
      return (T) bean;
  }
  ```
- **严重程度**: P2
- **现状**: `getBean()` 接收 `Class<T> clazz` 参数但完全没有使用它来做类型校验。方法仅检查 `bean == null`，然后直接执行 `(T) bean` 强转。
- **风险**: 如果存储的对象不是 `clazz` 的实例，调用者将得到 `ClassCastException`，且堆栈指向使用位置而非根因位置。
- **建议**: 在返回前添加 `clazz.cast(bean)` 或至少添加 `if (!clazz.isInstance(bean))` 的显式检查。
- **信心水平**: 确定
- **误报排除**: `Class<T>` 参数已传入但未使用，这是一个明确的契约缺失。
- **复核状态**: 未复核

### [维度15-02] StreamReduceOperator.restoreState() 反序列化值未经类型校验直接强转

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java:97-118`
- **证据片段**:
  ```java
  Object value = entry.get("value");
  if (key == null || value == null) {
      LOG.warn("Skipping restore entry with null key or value");
      continue;
  }
  values.put(key, (T) value);  // value 来自 JSON 反序列化，类型为 Object
  ```
- **严重程度**: P2
- **现状**: `(T) value` 直接将 JSON 反序列化后的 `Object` 强转为 `T`。经过 JSON round-trip 后，`T` 的实际运行时类型可能已经改变（例如 `Long` 可能变成 `Integer`）。
- **风险**: 当 checkpoint 数据经过 JSON 序列化/反序列化后，`value` 的实际类型可能与 `T` 不匹配，导致后续使用时抛出 `ClassCastException`。
- **建议**: 使用 `getOperatorState(String, Class<T>)` 或 `JsonTool.parseBeanFromText` 进行显式类型转换。
- **信心水平**: 很可能
- **误报排除**: 防御只覆盖了 null 值，没有覆盖类型漂移。JSON round-trip 导致的类型改变是实际可触发的场景。
- **复核状态**: 未复核

### [维度15-03] MemoryStateSerDe.wrapInAccumulator() 将 Object 直接强转为泛型 T

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:328-340`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  private <T> SimpleAccumulator<T> wrapInAccumulator(Object value, Class<? extends SimpleAccumulator<T>> accumulatorClass) {
      SimpleAccumulator<T> acc = accumulatorClass.getDeclaredConstructor().newInstance();
      if (value != null) {
          acc.add((T) value);
      }
      return acc;
  }
  ```
- **严重程度**: P2
- **现状**: `value` 来自反序列化的 checkpoint 数据（`Object` 类型），`acc.add((T) value)` 是未经校验的强转。同文件中的 `deserializeValue` 方法有 `type.isInstance(obj)` 检查，但此方法没有采用相同模式。
- **风险**: 如果 checkpoint 数据中的值类型与 accumulator 期望的类型不匹配，可能产生类型不匹配的静默错误。
- **建议**: 引入类型信息到序列化格式中，或使用与 `deserializeValue` 相同的 `type.isInstance(obj)` 检查模式。
- **信心水平**: 很可能
- **误报排除**: 同文件中的 `deserializeValue` 方法展示了正确的做法（`type.isInstance(obj)` 检查），但 `wrapInAccumulator` 没有采用相同模式。
- **复核状态**: 未复核

### [维度15-04] WindowAggregationOperator 反序列化中多处 Object -> 泛型参数的 unchecked cast

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:444-470`
- **证据片段**:
  ```java
  target.put(wk, (ACC) acc);   // (ACC) cast
  target.put(wk, (ACC) value); // (ACC) cast
  ```
- **严重程度**: P2
- **现状**: 方法接收 `keyClass` 和 `windowClass` 参数用于反序列化 `K` 和 `W`，但 `ACC` 的类型信息完全丢失。多处将 `Object` 强转为 `ACC`。
- **风险**: 当 accumulator 内部维护的值类型与期望的 `ACC` 不匹配时，后续操作可能返回错误类型的数据。
- **建议**: 在序列化格式中增加 ACC 类型信息，反序列化时使用 `JsonTool.parseBeanFromText` 进行显式转换。
- **信心水平**: 很可能
- **误报排除**: `K` 和 `W` 有类型参数做类型安全的反序列化，但 `ACC` 没有等效机制，是不对称的类型安全缺陷。
- **复核状态**: 未复核

### [维度15-05] KeyContext 接口使用 Object 而 IKeyedStateBackend<K> 使用泛型 K

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/KeyContext.java:27-31`
- **证据片段**:
  ```java
  public interface KeyContext {
      void setCurrentKey(Object key);
      Object getCurrentKey();
  }
  ```
- **严重程度**: P3
- **现状**: `KeyContext` 使用 `Object` 作为 key 类型，但实际的状态后端 `IKeyedStateBackend<K>` 使用泛型 `K`。迫使所有桥接代码执行 unchecked cast。
- **风险**: 继承自 Flink 的设计决策。在 nop-stream 的单进程模型下，实际运行时风险较低。
- **建议**: 考虑将 `KeyContext` 泛型化为 `KeyContext<K>`。
- **信心水平**: 确定
- **误报排除**: 不是泛型擦除问题（key 类型在运行时完全可用），而是接口设计层面的类型丢失。
- **复核状态**: 未复核

### [维度15-06] CepOperator/SharedBuffer 使用原始类型 (Class) 绕过 MapStateDescriptor 泛型检查

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:203-206`
- **证据片段**:
  ```java
  elementQueueState = keyedStateStore.getMapState(
          new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  ```
- **严重程度**: P3
- **现状**: Java 泛型擦除的固有局限。代码注释中明确标注了 "raw cast intentional - type erased at runtime"。
- **风险**: 风险可控。`Class` 对象仅作为序列化 hint 使用。
- **建议**: 维持现状。
- **信心水平**: 确定
- **误报排除**: 纳入报告仅为完整性。
- **复核状态**: 未复核
