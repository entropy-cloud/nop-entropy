# 维度15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] NFACompiler 中 Pattern 和 GroupPattern 使用 Raw Type

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:260-262`
- **证据片段**:
  ```java
  private void checkPatternNameUniqueness(final Pattern pattern) {  // raw Pattern
      if (pattern instanceof GroupPattern) {
          Pattern patternToCheck = ((GroupPattern) pattern).getRawPattern();  // raw cast
  ```
- **严重程度**: P2
- **现状**: `checkPatternNameUniqueness` 参数声明为 raw `Pattern`，内部 `(GroupPattern)` cast 也是 raw。编译器无法在调用点检查 `T` 一致性。
- **风险**: 如果 `Pattern`/`GroupPattern` 的泛型签名变更，此处不会得到编译期报错。
- **建议**: 改为 `Pattern<?, ?>` 和 `(GroupPattern<?, ?>)` 。
- **信心水平**: 高
- **误报排除**: 外围类已持有泛型 `T`，raw type 削弱了编译期类型检查。
- **复核状态**: 未复核

---

### [维度15-02] HeapInternalTimerService 泛型键类型被擦除为 Object

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:31-42`
- **证据片段**:
  ```java
  private final Triggerable<Object, N> triggerable;
  private final Supplier<Object> currentKeySupplier;
  
  private static class TimerEntry<N> {
      final Object key;  // should be <K>
  ```
- **严重程度**: P2
- **现状**: `HeapInternalTimerService<N>` 只有一个泛型参数，key 类型 `K` 被硬编码为 `Object`。使用方（如 `WindowOperator`）必须强制转换 key 为 `K`。
- **风险**: 存在运行时 `ClassCastException` 风险。
- **建议**: 增加 `<K>` 泛型参数：`HeapInternalTimerService<K, N>`。
- **信心水平**: 高
- **误报排除**: 这不是动态边界的合理权衡。Timer 的 key 类型在编译期已确定。
- **复核状态**: 未复核

---

### [维度15-03] Raw Class 字面量用于 MapStateDescriptor 构造

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:203-206`
- **证据片段**:
  ```java
  elementQueueState = keyedStateStore.getMapState(
      new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  ```
- **严重程度**: P3
- **现状**: Java class literal 不支持泛型参数化，`(Class) List.class` 与 `Class<List<...>>` 在运行时等价。注释已说明意图。
- **风险**: 低。这是 Java 类型系统的已知限制。
- **建议**: 无需修改，可考虑将 `MapStateDescriptor` 构造函数改为接受 `Class<?>` 以消除 raw cast。
- **信心水平**: 高
- **误报排除**: 这是不可避免的 Java 限制，Flink 原版代码也有相同模式。
- **复核状态**: 未复核

---

### [维度15-04] AbstractStreamOperator.getKeyedStateBackend() 不安全泛型窄化

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:92-95`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  public <K> IKeyedStateBackend<K> getKeyedStateBackend() {
      return (IKeyedStateBackend<K>) keyedStateBackend;
  }
  ```
- **严重程度**: P2
- **现状**: `keyedStateBackend` 声明为 `IKeyedStateBackend<?>`，`getKeyedStateBackend()` 通过 unchecked cast 返回 `IKeyedStateBackend<K>`。如果调用者假设的 `K` 与实际 keyType 不一致，会抛出 `ClassCastException`。
- **风险**: 所有继承 `AbstractStreamOperator` 的 operator 都受影响。
- **建议**: 可在 debug 模式下增加 `Class<K>` 参数做运行时类型校验。
- **信心水平**: 高
- **误报排除**: 这是流处理框架的常见模式（Flink 也采用相同方式），属于设计妥协。但缺少运行时校验是可改进的。
- **复核状态**: 未复核

---

### [维度15-05] LastValue.type() 返回类型不准确

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/accumulators/LastValue.java:22-24`
- **证据片段**:
  ```java
  public static <V> Class<? extends SimpleAccumulator<V>> type() {
      return (Class) LastValue.class;
  }
  ```
- **严重程度**: P3
- **现状**: 返回类型 `Class<? extends SimpleAccumulator<V>>` 通过 raw cast 实现，`V` 的类型安全依赖调用方。
- **风险**: 低。仅作为 Class token 传递，不影响运行时行为。
- **建议**: 可将返回类型放宽为 `Class<?>` 或 `Class<? extends SimpleAccumulator<?>>`。
- **信心水平**: 高
- **误报排除**: 仅影响静态类型签名。
- **复核状态**: 未复核

---

### [维度15-06] KeyContext 接口 key 类型为 Object

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/KeyContext.java:27-31`
- **证据片段**:
  ```java
  public interface KeyContext {
      void setCurrentKey(Object key);
      Object getCurrentKey();
  }
  ```
- **严重程度**: P3
- **现状**: 接口 key 类型为 `Object`，无法在编译期约束 key 类型。所有实现类丧失编译期类型检查。
- **风险**: 框架级接口，修改影响全部实现类。
- **建议**: 记录为设计债务。Flink 兼容层也需要适配。
- **信心水平**: 高
- **误报排除**: 这是框架级接口，Flink 也使用 Object。可视为动态边界的合理权衡。
- **复核状态**: 未复核
