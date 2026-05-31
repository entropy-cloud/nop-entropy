# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] Checkpoint 状态容器全部使用 Map<String, Object> -- 缺少类型安全的状态注册表

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/TaskStateSnapshot.java:24-25,75-80`
- **证据片段**:
  ```java
  private final Map<String, Object> operatorStates;
  private final Map<String, Object> keyedStates;
  public void putOperatorState(String name, Object state) { operatorStates.put(name, state); }
  public Object getOperatorState(String name) { return operatorStates.get(name); }
  ```
- **严重程度**: P1
- **现状**: TaskStateSnapshot、OperatorSnapshotResult、TaskEpochSnapshot 三个核心 checkpoint 容器使用 Map<String, Object>。有类型化的 getOperatorState(name, Class<T>) 重载但无调用方强制使用。
- **风险**: 消费端手动 cast（WindowAggregationOperator:159, StreamReduceOperator:107），类型错误仅运行时发现。状态名为硬编码字符串，无编译期校验。
- **建议**: (1) 将无类型 getOperatorState(String) 标记 @Deprecated，推动 getOperatorState(String, Class<T>)。(2) 引入 StateName<T> 类型安全常量。
- **信心水平**: 确定
- **误报排除**: 已有类型化方法但全部调用点用无类型版本。
- **复核状态**: 未复核

### [维度15-02] KeyContext 接口使用原始 Object 丢失 Key 类型参数

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/KeyContext.java:29-31`
- **证据片段**:
  ```java
  public interface KeyContext {
      void setCurrentKey(Object key);
      Object getCurrentKey();
  }
  ```
- **严重程度**: P1
- **现状**: KeyContext 是 keyed stream 核心接口，key 类型被擦除为 Object。所有实现者持有 Object currentKey 字段。
- **风险**: 实现者可传入错误 key 类型，直到下游使用时才 ClassCastException。key 类型与 StateBackend K 不匹配时状态读写静默错乱。
- **建议**: KeyContext 改为 KeyContext<K>，AbstractStreamOperator<OUT> 接受 <K, OUT> 两个类型参数。
- **信心水平**: 很可能
- **误报排除**: nop-stream 是简化版框架，operator 链在编译期能确定 key 类型。
- **复核状态**: 未复核

### [维度15-03] HeapInternalTimerService 强制 Triggerable<Object, N>，丢失 Key 泛型

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:31-32,161-162`
- **证据片段**:
  ```java
  private final Triggerable<Object, N> triggerable;
  private static class HeapInternalTimer<N> implements InternalTimer<Object, N> {
      private final Object key;
  ```
- **严重程度**: P1
- **现状**: HeapInternalTimerService 将 key 泛型硬编码为 Object。InternalTimer<K,N> 接口已正确定义了 K 参数。
- **风险**: timer 回调 getKey() 返回 Object，需强制转换。TimerEntry.equals() 跨类型 key 比较可能产生不对称行为。
- **建议**: 改为 HeapInternalTimerService<K, N>，接受 Triggerable<K, N>。与 [15-02] 同根。
- **信心水平**: 很可能
- **误报排除**: InternalTimer<K,N> 接口已正确定义 K，HeapInternalTimerService 选择忽略。
- **复核状态**: 未复核

### [维度15-04] MemoryKeyedStateBackend.states 使用 Map<String, Object>

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:63`
- **证据片段**:
  ```java
  private final Map<String, Object> states = new HashMap<>();
  @SuppressWarnings("unchecked")
  ValueState<T> state = (ValueState<T>) states.get(stateProperties.getName());
  ```
- **严重程度**: P2
- **现状**: 7 个 getXxxState() 方法都从 Map<String, Object> 读取并 unchecked cast，共 7 处 @SuppressWarnings("unchecked")。
- **风险**: 不同泛型参数的 state 同名时静默返回错误类型。
- **建议**: 用 Map<String, StateSnapshot> 或引入 StateHolder 接口替代 Object。
- **信心水平**: 很可能
- **误报排除**: 可以用 Map<String, ?> + 类型安全 accessor 避免 unchecked cast。
- **复核状态**: 未复核

### [维度15-05] StreamComponents 7 个组件映射全部 Map<String, Object>

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:25-31`
- **证据片段**:
  ```java
  private final Map<String, Object> transforms;
  private final Map<String, Object> streams;
  private final Map<String, Object> windowingStrategies;
  public Object getStream(String id) { return streams.get(id); }
  ```
- **严重程度**: P2
- **现状**: 7 个组件映射全部是 Map<String, Object>，存取方法全部接受/返回 Object。
- **风险**: 消费者必须自己 cast；没有编译期保障。
- **建议**: 引入类型安全的注册方法或注册时携带 Class<?> 元数据。
- **信心水平**: 很可能
- **误报排除**: 不是 XDSL 动态解析代码——是 Java 层面的组件注册表。
- **复核状态**: 未复核

### [维度15-06] SourceEnumeratorState.discoveryCursor 使用 Object 类型

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/SourceEnumeratorState.java:25,55`
- **证据片段**:
  ```java
  private final Object discoveryCursor;
  public Object getDiscoveryCursor() { return discoveryCursor; }
  ```
- **严重程度**: P2
- **现状**: discoveryCursor 语义是"source 的游标位置"，但类型完全擦除。
- **风险**: JSON 反序列化后类型信息可能丢失（Long → Integer）。
- **建议**: 改为泛型 SourceEnumeratorState<T> 或使用 Serializable/Number 约束。
- **信心水平**: 很可能
- **误报排除**: 是 checkpoint 持久化的数据 bean，可以在设计时约束类型。
- **复核状态**: 未复核

### [维度15-07] TwoPhaseCommitSinkFunction.pendingCommits 使用 Map<Long, Object>

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:31,57-61`
- **证据片段**:
  ```java
  private Map<Long, Object> pendingCommits;
  public Map<Long, Object> getPendingCommits() { return pendingCommits; }
  ```
- **严重程度**: P2
- **现状**: pendingCommits 的 value 是 Object（语义为事务句柄），基类不约束类型。
- **建议**: 改为 TwoPhaseCommitSinkFunction<IN, TX>，Flink 原版就是泛型的。
- **信心水平**: 确定
- **误报排除**: Flink 原版 TwoPhaseCommitSinkFunction<IN, TXN, CONTEXT> 就是泛型的。
- **复核状态**: 未复核

### [维度15-08] WindowAggregationOperator 反序列化使用大量 unchecked cast

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:599-673`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  private void deserializeWindowState(Map<String, Object> data, Class<?> keyClass, ...) {
      String accType = (String) map.get("@type");
      Object accValue = map.get("value");
      SimpleAccumulator<Object> acc = (SimpleAccumulator<Object>) Class.forName(accType)...
      acc.add(accValue);
      target.put(wk, (ACC) acc);  // unchecked cast
  ```
- **严重程度**: P2
- **现状**: 3 个反序列化方法共 10 处 @SuppressWarnings("unchecked")，通过 JSON Map stringly-typed 字段做反序列化。
- **建议**: 引入 TypedAccumulatorDescriptor 携带 accumulatorClass + valueClass。
- **信心水平**: 很可能
- **误报排除**: 框架完全可以设计类型化的 accumulator descriptor。
- **复核状态**: 未复核

### [维度15-09] StreamSinkOperator 大量 instanceof 分支缺少接口抽象

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:62-145`
- **证据片段**:
  ```java
  if (userFunction instanceof CheckpointParticipant) {
      // ...
  } else if (userFunction instanceof TwoPhaseCommitSinkFunction) {
      ((TwoPhaseCommitSinkFunction<?>) userFunction).preCommit(barrier.getId());
  }
  ```
- **严重程度**: P2
- **现状**: 5 个生命周期方法都有相同的三路 instanceof 分支，共约 15 个检查。
- **风险**: 新增 sink function 类型需修改所有 5 处。cast 可能出错。
- **建议**: 提取 SinkFunctionAdapter 接口或使用 visitor/strategy 模式。
- **信心水平**: 确定
- **误报排除**: 可以通过接口设计消除的代码坏味道。
- **复核状态**: 未复核

### [维度15-10] CepOperator/SharedBuffer 使用 raw Class cast 绕过泛型

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:210-211`
- **证据片段**:
  ```java
  elementQueueState = keyedStateStore.getMapState(
      new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  ```
- **严重程度**: P2
- **现状**: (Class) List.class 是 raw cast，将 Class<List> 当作 Class<Lockable<V>> 传入。
- **风险**: MapStateDescriptor 内部用此 Class 做反序列化，丢失元素类型信息。
- **建议**: MapStateDescriptor 应接受 TypeSerializer 或 TypeInformation 而非 Class。
- **信心水平**: 很可能
- **误报排除**: Flink 用 TypeSerializer 解决此问题。
- **复核状态**: 未复核

### [维度15-11] NFACompiler 中 11 处 @SuppressWarnings("unchecked")

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java` (全文件 1099 行)
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  // line 851:
  Pattern<T, F> inner = ((GroupPattern<T, F>) currentPattern).getRawPattern();
  ```
- **严重程度**: P2
- **现状**: 11 处 unchecked cast，主要是 Pattern 子类型下转型。
- **风险**: pattern 层次结构变化时 cast 失败产生 ClassCastException 而非有意义的错误消息。
- **建议**: 引入 visitor 模式处理 Pattern 子类型分发。
- **信心水平**: 很可能
- **误报排除**: Pattern 继承层次固定（Pattern / GroupPattern），可用多态替代 instanceof。
- **复核状态**: 未复核

### [维度15-12] WindowAggregationOperator.setCurrentKey 的 unchecked cast 到 K

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:230-233`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  @Override
  public void setCurrentKey(Object key) {
      this.currentKeyField = (K) key;
  }
  ```
- **严重程度**: P2
- **现状**: 从 KeyContext 接口（Object key）直接 unchecked cast 到 K。同文件 resolveKey() 有防御性类型检查但 setCurrentKey 没有。
- **建议**: 增加 runtime type check：keyType.isInstance(key)。
- **信心水平**: 确定
- **误报排除**: resolveKey() 已有类型检查但 setCurrentKey 没有——不一致。
- **复核状态**: 未复核

### [维度15-13] WindowOperator 构造函数使用 double-cast (Class<ACC>) (Class<?>) Object.class

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:200-201`
- **证据片段**:
  ```java
  (Class<ACC>) (Class<?>) Object.class
  ```
- **严重程度**: P2
- **现状**: 9 参数构造函数将 accClass 默认设为 Object.class，通过 double-cast 绕过编译器检查。
- **风险**: accClass 实际是 Object.class，state backend 按此反序列化得到 Object 而非 ACC。
- **建议**: 移除便利构造函数或要求调用者显式传入 accClass。
- **信心水平**: 确定
- **误报排除**: double-cast 是公认的绕过类型系统的 code smell。
- **复核状态**: 未复核

## 根因分析

约 70% 的发现源于两个系统性设计选择：
1. **KeyContext 使用 Object 而非泛型 K**（影响 #02, #03, #12, #17）
2. **Checkpoint/State 容器使用 Map<String, Object> 而非类型化注册表**（影响 #01, #04, #05, #06, #07, #08）
