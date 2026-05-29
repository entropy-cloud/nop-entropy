# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

nop-stream 模块的类型安全状况与流处理框架惯例一致。约131个 @SuppressWarnings("unchecked") 注解集中在状态后端序列化边界、运行时算子链接和连接器抽象层，是 Java 类型擦除下的合理选择。

### [维度15-01] Raw cast (Class) List.class and (Class) Lockable.class for MapStateDescriptor

- **文件**: `nop-stream/nop-stream-cep/.../CepOperator.java:206`, `SharedBuffer.java:95,102`
- **证据片段**:
  ```java
  elementQueueState = keyedStateStore.getMapState(
      new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  ```
- **严重程度**: P3
- **现状**: MapStateDescriptor 的 value 类型参数使用原始类型转换，因为 Java 无法直接表达 Class<List<X>>。
- **风险**: 低。注释已说明这是有意为之。类型安全通过使用侧保证。
- **建议**: 添加 @SuppressWarnings("rawtypes") 注解或修改 MapStateDescriptor 构造器接受 Class<?>。
- **误报排除**: 这是 Java 泛型限制下的标准模式，而非设计缺陷。仅建议添加注解以提高一致性。
- **复核状态**: 未复核

### [维度15-02] PaneState uses untyped Object for window and state fields

- **文件**: `nop-stream/nop-stream-core/.../windowing/PaneState.java:20,22`
- **严重程度**: P3
- **现状**: window 和 state 字段使用 Object 类型而非泛型参数。
- **建议**: 可考虑泛型化 PaneState<W, S>，但不阻塞。
- **误报排除**: PaneState 是 checkpoint 边界的序列化 DTO，Object 类型是务实选择。
- **复核状态**: 未复核

### [维度15-03] LastValue.type() raw cast without @SuppressWarnings

- **文件**: `nop-stream/nop-stream-core/.../accumulators/LastValue.java:23`
- **严重程度**: P3
- **现状**: `(Class) LastValue.class` 缺少 @SuppressWarnings("unchecked") 注解，与其他同类代码不一致。
- **建议**: 添加注解以保持一致性。
- **误报排除**: 仅是注解缺失，非功能性缺陷。
- **复核状态**: 未复核

### [维度15-04] MessageSourceFunction.run() casts untyped message to generic T

- **文件**: `nop-stream/nop-stream-connector/.../MessageSourceFunction.java:98`
- **严重程度**: P3
- **现状**: IMessageConsumer 回调的 Object msg 被直接转为 T。已有 @SuppressWarnings 注解。
- **建议**: 可考虑添加 Class<T> 构造器参数做运行时 instanceof 检查。
- **误报排除**: 这是桥接无类型消息 API 到有类型流的标准模式。
- **复核状态**: 未复核
