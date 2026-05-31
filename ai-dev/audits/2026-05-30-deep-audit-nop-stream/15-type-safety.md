# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] CepPatternBuilder 全类使用原始 Pattern 类型，丢失泛型约束

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:28-145`
- **证据片段**:
  ```java
  @SuppressWarnings("rawtypes")
  public Pattern buildFromModel(CepPatternModel patternModel) {
      Pattern pattern = buildGroupPattern(patternModel);
      if (patternModel.getWithin() != null)
          pattern = pattern.within(patternModel.getWithin());
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 该类从 XML 模型构建 `Pattern<T,F>` 对象，但模型层不携带 Java 泛型参数，导致 7 处 rawtypes 抑制。编译器无法验证条件是否与事件类型匹配。
- **风险**: 若 XML 模型中 where 表达式产生的条件类型与事件类型不匹配，在 NFA 匹配阶段才暴露 ClassCastException。
- **建议**: 为 CepPatternBuilder 增加 Class<T> eventClass 构造参数，缩小 rawtype 范围。
- **信心水平**: 很可能
- **误报排除**: 这是模型驱动构建（XML → Pattern）的固有挑战，非设计错误。
- **复核状态**: 未复核

### [维度15-02] SharedBuffer 构造器通过 raw Class 绕过 MapStateDescriptor 泛型约束

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:84-102`
- **证据片段**:
  ```java
  @SuppressWarnings({"unchecked", "rawtypes"})
  public SharedBuffer(...) {
      this.eventsBuffer = stateStore.getMapState(
              new MapStateDescriptor<EventId, Lockable<V>>(
                      EVENTS_STATE_NAME, EventId.class, (Class) Lockable.class));
  }
  ```
- **严重程度**: P2
- **现状**: Java 泛型擦除使 Lockable<V>.class 不存在，通过双重转型绕过。
- **风险**: 若未来接入基于 Class 参数做类型校验的后端，可能产生 ClassCastException。
- **建议**: 在 MapStateDescriptor 构造函数 Javadoc 中注明约束。
- **信心水平**: 很可能
- **误报排除**: 泛型擦除的已知限制，与 Flink 原版保持一致。
- **复核状态**: 未复核

### [维度15-03] CepOperator 用 (Class) List.class 作 MapState value 类型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:186-212`
- **证据片段**:
  ```java
  elementQueueState = keyedStateStore.getMapState(
          new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  ```
- **严重程度**: P2
- **现状**: 实际存储 List<IN>，但只传了 List.class，丢失了 IN 类型信息。
- **风险**: checkpoint 恢复时若序列化数据格式不兼容，可能包含非 IN 类型元素。
- **建议**: 引入 TypeToken 替代 bare Class（长期）。
- **信心水平**: 很可能
- **误报排除**: 与发现 15-02 同一根因（Java 泛型擦除）。
- **复核状态**: 未复核

### [维度15-04] AbstractStreamOperator.getKeyedStateBackend() 调用方选择泛型参数

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:92-94`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  public <K> IKeyedStateBackend<K> getKeyedStateBackend() {
      return (IKeyedStateBackend<K>) keyedStateBackend;
  }
  ```
- **严重程度**: P2
- **现状**: 调用方决定 K 的类型，但 keyedStateBackend 由 stateBackend.createKeyedStateBackend(keyClass) 创建，K 可能不一致。
- **风险**: 若引入类型敏感的后端实现，可能导致 ClassCastException。
- **建议**: 将 unchecked cast 缩小到构造器赋值处，存储为 IKeyedStateBackend<?>。
- **信心水平**: 很可能
- **误报排除**: 当前 MemoryKeyedStateBackend 基于 Object 存储，规避了此问题。
- **复核状态**: 未复核

### [维度15-05] KeyedStreamImpl.sum/min/max 对无约束泛型 T 施行 unchecked 数值转型

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:178-192`
- **证据片段**:
  ```java
  ReduceFunction<T> reducer = (v1, v2) -> {
      if (v1 instanceof Integer) return (T) (Integer) (((Integer) v1) + ((Number) v2).intValue());
      if (v1 instanceof Long) return (T) (Long) (((Long) v1) + ((Number) v2).longValue());
      throw new UnsupportedOperationException("sum(int field) requires Number elements");
  };
  ```
- **严重程度**: P3
- **现状**: T 无 Number 上界约束，但 sum 方法通过运行时类型检查执行数值操作。
- **风险**: 类型签名误导，运行时而非编译时失败。
- **建议**: 与 Flink API 一致的已知限制。在方法 Javadoc 中加 @throws 说明。
- **信心水平**: 确定
- **误报排除**: 与 Flink API 保持一致的已知限制。
- **复核状态**: 未复核

### [维度15-06] NFACompiler 中 Pattern F→T 条件转型

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:598, 949, 996, 1007, 1020, 1025`
- **证据片段**:
  ```java
  final IterativeCondition<T> untilCondition =
          (IterativeCondition<T>) currentPattern.getUntilCondition();
  ```
- **严重程度**: P3
- **现状**: Pattern<F extends T> 的条件转型，在 F extends T 约束下逻辑安全。
- **风险**: 若未来修改 Pattern 的 F 上界约束后可能引入错误。
- **建议**: 在注释中注明转型安全原因。
- **信心水平**: 确定
- **误报排除**: F extends T 约束使转型逻辑安全。
- **复核状态**: 未复核

### [维度15-07] MemoryStateSerDe 恢复路径将 Class.forName 标为 Class<Object>

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:131-375`
- **证据片段**:
  ```java
  Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
  ```
- **严重程度**: P3
- **现状**: 反射加载的类标记为 Class<Object>，实际可能是 Long、String 等。
- **风险**: 当前通过 JSON 反序列化恢复，不会导致运行时错误。
- **建议**: 低优先级。内部实现类，不在公共 API 中。
- **信心水平**: 确定
- **误报排除**: 基于反射的序列化框架普遍存在此模式。
- **复核状态**: 未复核

### [维度15-08] StreamRecord.replace() 就地变更泛型参数

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/StreamRecord.java:110-132`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  public <X> StreamRecord<X> replace(X element) {
      this.value = (T) element;
      return (StreamRecord<X>) this;
  }
  ```
- **严重程度**: P3
- **现状**: 就地变更 this.value 的类型，违反 Java 泛型类型安全假设。代码已标注 Warning。
- **风险**: 新增代码若保留旧引用将产生 ClassCastException。当前调用方通常立即丢弃旧引用。
- **建议**: 在方法签名上添加更明确的约束说明。
- **信心水平**: 确定
- **误报排除**: 从 Apache Flink 继承的模式，代码已充分标注 Warning。
- **复核状态**: 未复核
