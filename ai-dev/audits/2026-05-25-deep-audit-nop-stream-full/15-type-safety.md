# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] CepOperator 使用 raw type (Class) 传入 List.class，丢失泛型参数 IN

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:180`
- **证据片段**:
  ```java
  elementQueueState = stateStore.getMapState(
          new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  ```
- **严重程度**: P2
- **现状**: 通过 `(Class) List.class` 将 `Class<List>` 作为 raw type 传入，擦除了 `IN` 类型参数。
- **风险**: 恢复 checkpoint 时可能导致 `List<IN>` 中混入非 IN 类型的元素。
- **建议**: 引入 `ListStateDescriptor<IN>` 或 `TypeInformation<List<IN>>` 来保留完整的泛型信息。
- **误报排除**: raw cast 绕过了编译器的泛型检查，在反序列化场景下是可触发的运行时风险。
- **复核状态**: 未复核

---

### [维度15-02] SharedBuffer 使用 raw type (Class) 传入 Lockable.class，丢失 V / SharedBufferNode 泛型参数

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:97-108`
- **证据片段**:
  ```java
  this.eventsBuffer = stateStore.getMapState(
          new MapStateDescriptor<>(EVENTS_STATE_NAME, EventId.class, (Class) Lockable.class));
  this.entries = stateStore.getMapState(
          new MapStateDescriptor<>(ENTRIES_STATE_NAME, NodeId.class, (Class) Lockable.class));
  ```
- **严重程度**: P2
- **现状**: 两处 MapStateDescriptor 通过 `(Class) Lockable.class` 的 raw cast 将泛型信息擦除。
- **风险**: `Lockable` 内部的泛型类型信息丢失，恢复旧 checkpoint 时可能产生静默的类型不一致。
- **建议**: 为 `Lockable` 提供自定义 `TypeInformation`，或在序列化中增加 `@type` 鉴别字段。
- **误报排除**: 同一 raw type 模式在同一模块中出现两次，系统性影响 CEP 状态存储的类型安全性。
- **复核状态**: 未复核

---

### [维度15-03] LastValue.type() 返回未经参数化的 raw Class，破坏调用端的泛型安全

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/accumulators/LastValue.java:22-24`
- **证据片段**:
  ```java
  public static <V> Class<? extends SimpleAccumulator<V>> type() {
      return (Class) LastValue.class;
  }
  ```
- **严重程度**: P2
- **现状**: 方法签名承诺返回参数化的 Class，但实际返回 raw type。
- **风险**: 调用端的类型关系（T 与 SimpleAccumulator<T>）完全不受编译器保护。
- **建议**: 改为返回 `Class<? extends SimpleAccumulator<?>>` 或使用类型安全的工厂方法。
- **误报排除**: 公共 API 方法的返回值泛型安全性直接被 raw cast 破坏。
- **复核状态**: 未复核

---

### [维度15-04] WindowOperator.open() 用 Object.class 作为 ACC 类型令牌，ACC 类型信息在状态层完全丢失

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:225-228`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  Class<ACC> accType = (Class<ACC>) (Class<?>) Object.class;
  MapStateDescriptor<String, ACC> windowContentsDescriptor =
          new MapStateDescriptor<>("window-contents", String.class, accType);
  ```
- **严重程度**: P1
- **现状**: `WindowOperator<IN, ACC, OUT, K, W>` 声明了 ACC 类型参数，但硬编码为 `Object.class`。整个 WindowOperator 的状态管理链上没有类型约束。
- **风险**: 高概率回归风险点。checkpoint 序列化时无法推断 ACC 的实际类型，恢复时依赖运行时 instanceof 检查。
- **建议**: 在 `WindowOperator` 构造函数中增加 `Class<ACC> accClass` 参数。
- **误报排除**: ACC 类型令牌是 `Object.class` 意味着整个 WindowOperator 的状态管理没有类型约束。
- **复核状态**: 未复核

---

### [维度15-05] StreamReduceOperator 将 key 存储为 Object 类型，丢失泛型参数 K

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamReduceOperator.java:28-29`
- **证据片段**:
  ```java
  private transient Object currentKey;
  private transient Map<Object, T> values;
  ```
- **严重程度**: P2
- **现状**: `StreamReduceOperator<T>` 没有 key 类型参数 K，key 类型被完全丢弃。
- **风险**: JSON 序列化路径下 Integer → Long 的类型变化会导致 `values.get(key)` 查找失败。
- **建议**: 增加 key 类型参数 K，在 `restoreState` 中增加类型安全反序列化。
- **误报排除**: operator 明确要求在 keyed stream 上使用，但 key 类型信息被完全丢弃。
- **复核状态**: 未复核

---

### [维度15-06] WindowAggregationOperator.currentKeyField 为 Object 而非 K

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:41,179,354-362`
- **证据片段**:
  ```java
  private transient Object currentKeyField;
  public void setCurrentKey(Object key) { this.currentKeyField = key; }
  @SuppressWarnings("unchecked")
  private K resolveKey(IN value) throws Exception {
      if (currentKeyField != null) { return (K) currentKeyField; }
  }
  ```
- **严重程度**: P2
- **现状**: 声明了 K 类型参数，但 `currentKeyField` 为 `Object`，通过强制转换使用。
- **风险**: `currentKeyField` 被传递到 `WindowKey<K, W>` 中作为 map 的 key，类型不一致会影响 `equals/hashCode` 查找。
- **建议**: 将 `currentKeyField` 类型改为 `K`，在 `setCurrentKey` 中增加类型检查。
- **误报排除**: 被传递到 `WindowKey<K, W>` 中作为 map 的 key，类型不一致影响查找。
- **复核状态**: 未复核

---

### [维度15-07] PaneState 的 window 和 state 字段为 Object 类型

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/windowing/PaneState.java:20-22`
- **证据片段**:
  ```java
  @DataBean
  public class PaneState implements Serializable {
      private final PaneInfo paneInfo;
      private final Object window;
      private final Object state;
  ```
- **严重程度**: P3
- **现状**: `@DataBean` 的 `window` 和 `state` 字段都是 `Object`，影响 Nop 的 bean 自动映射。
- **风险**: 反序列化时类型信息完全依赖运行时猜测。
- **建议**: 改为 `PaneState<W extends Window, S>` 或至少将 `window` 类型改为 `Window`。
- **误报排除**: `@DataBean` 字段类型直接影响 Nop 的 JSON 序列化器。
- **复核状态**: 未复核

---

### [维度15-08] TypedNamespaceAndKey 和 ShardPrefixedKey 将 namespace/key 存储为 Object

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:1146-1153`
- **证据片段**:
  ```java
  protected static class TypedNamespaceAndKey implements Serializable {
      private final Object namespace;
      private final Object key;
  }
  ```
- **严重程度**: P2
- **现状**: `MemoryKeyedStateBackend<K>` 声明了 K 类型参数，但内部存储键中 K 被擦除为 Object。
- **风险**: JSON 反序列化改变了 key 的运行时类型（如 Integer → Long），导致状态查询失败。
- **建议**: 将内部类改为 `TypedNamespaceAndKey<N, K>` 或将 key 类型声明为 `K`。
- **误报排除**: 核心存储键类型声明为 Object 影响所有使用该后端的状态操作。
- **复核状态**: 未复核
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 15-01 | **保留 P2** | (Class) List.class raw cast确认，CEP状态恢复时可能导致类型不一致。 |
| 15-02 | **保留 P2** | 两处(Class) Lockable.class raw cast确认，系统性影响CEP共享缓冲区。 |
| 15-03 | **降级至 P3** | Java标准type-token模式，调用端通过SimpleAccumulator<V>接口保证类型安全。代码味道，非运行时风险。 |
| 15-04 | **保留 P1** | ACC类型令牌为Object.class确认。所有发现中最严重的类型安全问题。P1适当。 |
| 15-05 | **保留 P2** | Object currentKey确认。JSON反序列化路径下key类型完全丢失。注意：与15-06高度相似，建议合并。 |
| 15-06 | **保留 P2** | Object currentKeyField确认。与15-05同为key类型擦除，但影响WindowKey<K,W>查找。合并至15-05处理。 |
| 15-07 | **保留 P3** | Object window/state确认。@DataBean影响Nop JSON序列化器。 |
| 15-08 | **保留 P2** | TypedNamespaceAndKey namespace/key为Object确认。K擦除影响所有使用该后端的状态操作。 |
