# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] WindowedStreamImpl 中 (Class<K>) (Class<?>) Object.class 绕过类型系统

- **文件**: `nop-stream-core/.../datastream/WindowedStreamImpl.java:164-225`
- **证据片段**:
  ```java
  (Class<T>) (Class<?>) Object.class,
  keyedStream.getKeySelector(), (Class<K>) (Class<?>) Object.class);
  ```
  4 处完全相同的模式（apply/aggregate/reduce/process 方法）。
- **严重程度**: P2
- **现状**: 当使用 IWindowOperatorFactory 路径时，所有类型参数 Class 被强转为 Object.class。工厂收到的 Class<T>/Class<K> 实际都是 Object.class。
- **风险**: 如果工厂实现依赖 Class 做 cast/isInstance/反射，类型错误只在运行时暴露。
- **建议**: 从 TypeInformation 中提取实际 Class，或在接口 Javadoc 中标注参数可能为 Object.class。
- **信心水平**: 确定 (95%)
- **误报排除**: 内置工厂实现不依赖这些参数，所以目前无 bug。但是结构性类型安全缺陷。
- **复核状态**: 未复核

### [维度15-02] OperatorSnapshotResult/TaskStateSnapshot 使用 Map<String,Object> 无类型校验

- **文件**: `nop-stream-core/.../checkpoint/OperatorSnapshotResult.java:22-24,81-121`
- **证据片段**:
  ```java
  private final Map<String, Object> operatorStates;
  public <T> T getOperatorState(String name, Class<T> typeClass) {
      Object value = operatorStates.get(name);
      return typeClass.cast(value);  // ClassCastException if type mismatch
  }
  ```
- **严重程度**: P2
- **现状**: checkpoint 系统使用 Map<String,Object> 作为通用状态容器。写入不记录类型，读取只做 Class.cast()。
- **风险**: 写入/读取类型不匹配会 ClassCastException；merge() 方法直接 putAll 可能静默覆盖。
- **建议**: 写入时记录类型，读取时校验类型匹配。merge() 加 key 冲突检测。
- **信心水平**: 很可能 (90%)
- **误报排除**: 流处理框架的 checkpoint 系统天然需要动态类型，但缺少任何校验风险偏高。
- **复核状态**: 未复核

### [维度15-05] WindowOperator.restoreState 缺少 key 类型校验

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:391-409`
- **证据片段**:
  ```java
  if (restored instanceof Map) {
      Map<?, ?> restoredMap = (Map<?, ?>) restored;
      for (Map.Entry<?, ?> entry : restoredMap.entrySet()) {
          if (!(entry.getValue() instanceof SimpleAccumulator)) { ... }
          // 不检查 key 是否为 String
      }
      this.triggerAccumulators = (Map<String, SimpleAccumulator<?>>) restored;
  }
  ```
- **严重程度**: P3
- **现状**: 检查了 value 类型但不检查 key 类型。
- **建议**: 增加 `entry.getKey() instanceof String` 检查。
- **信心水平**: 很可能 (85%)
- **误报排除**: 防弹性问题，正常流程中 key 确实是 String。
- **复核状态**: 未复核

### [维度15-06] WindowAggregationOperator 反序列化无 null class 防护

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:630-704`
- **证据片段**:
  ```java
  K key = (K) JsonTool.parseBeanFromText(parts[0], keyClass);
  W window = (W) JsonTool.parseBeanFromText(parts[1], windowClass);
  ```
- **严重程度**: P2
- **现状**: keyClass/windowClass 可以为 null（行 209-218 显示），传 null 给 parseBeanFromText 后果取决于 JsonTool 实现。
- **风险**: null class 导致反序列化返回原始 JSON 类型，后续 (K) 强转可能失败。
- **建议**: 入口处对 keyClass==null 或 windowClass==null 抛出明确异常。
- **信心水平**: 很可能 (90%)
- **误报排除**: null class 本身是配置错误，应更早报错。
- **复核状态**: 未复核

## 已排除项

- MemoryStateSerDe 的 Class<Object> 强转：Java 反射 API 固有限制，ClassNameValidator + isInstance() 已足够。
- MemoryKeyedStateBackend 的 Map<String,Object>：registerStateType 已提供一层保护。
- SharedBuffer 的 (Class) Lockable.class：Java 泛型擦除已知限制。
- AbstractStreamOperator 的 IKeyedStateBackend<?> → IKeyedStateBackend<Object>：标准通配符处理。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 15-01 | P2 | WindowedStreamImpl.java | Object.class 绕过类型系统 |
| 15-02 | P2 | OperatorSnapshotResult.java | Map<String,Object> 无类型校验 |
| 15-05 | P3 | WindowOperator.java | restoreState 缺少 key 类型校验 |
| 15-06 | P2 | WindowAggregationOperator.java | 反序列化无 null class 防护 |
