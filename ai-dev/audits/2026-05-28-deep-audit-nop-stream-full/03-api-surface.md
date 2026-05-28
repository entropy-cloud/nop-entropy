# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] StreamComponents 公共 API 使用 Map<String, Object> 而非类型安全结构

- **文件**: `nop-stream-core/.../model/StreamComponents.java:24-26,46-53`
- **证据片段**:
  ```java
  private final Map<String, Object> transforms;
  private final Map<String, Object> streams;
  // ...
  public Object getTransform(String id) { return transforms.get(id); }
  ```
- **严重程度**: P2
- **现状**: StreamComponents 暴露 7 个 Map<String, Object> 公共字段。getTransform(id) 等返回原始 Object。实际外部未调用，是预留给未来扩展的占位 API。
- **风险**: 外部调用者无法在编译期获知值类型。
- **建议**: 将 getter 降级为 package-private 或改为更具体类型。
- **误报排除**: 排除序列化层的 Map<String,Object>——那些是内部实现细节。
- **复核状态**: 未复核

### [维度03-02] StreamOperator.snapshotState() 存在重复的 Javadoc 注释块（英文+中文）

- **文件**: `nop-stream-core/.../operators/StreamOperator.java:119-136`
- **证据片段**:
  ```java
  /**
   * Called to draw a state snapshot from the operator. (英文，描述旧签名)
   */
  /**
   * 执行状态快照 (中文，与实际签名匹配)
   */
  default OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
  ```
- **严重程度**: P2
- **现状**: 两个连续 Javadoc 块。英文描述的是"runnable future"返回值语义，与实际 OperatorSnapshotResult 不符。
- **风险**: 误导开发者理解方法契约。
- **建议**: 删除过时的英文注释块，仅保留与实际签名匹配的注释。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度03-03] CepPatternBuilder.buildCondition() 返回原始类型 IterativeCondition

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java:134-141`
- **证据片段**:
  ```java
  private IterativeCondition buildCondition(IEvalFunction action) {
      return new IterativeCondition() {
          @Override
          public boolean filter(Object value, Context ctx) { ... }
      };
  }
  ```
- **严重程度**: P2
- **现状**: 返回原始类型 IterativeCondition，内部匿名类 filter 参数也是 Object。
- **风险**: 编译器无法捕获类型不匹配。
- **建议**: 添加 @SuppressWarnings("rawtypes") 并说明原因（XPL 函数的动态特性）。
- **误报排除**: CEP 模型来自 XML 声明式定义，运行时类型在编译期不可知。
- **复核状态**: 未复核

### [维度03-04] KeyedStream.sum/min/max(int field) 在 field != 0 时抛 UnsupportedOperationException

- **文件**: `nop-stream-core/.../datastream/KeyedStreamImpl.java:176-209`
- **证据片段**:
  ```java
  public SingleOutputStreamOperator<T> sum(int field) {
      if (field != 0) {
          throw new UnsupportedOperationException("sum(int field) with field != 0 requires Tuple types");
      }
  ```
- **严重程度**: P2
- **现状**: 接口定义了 sum(int) 但实现仅支持 field==0。接口契约未说明此限制。
- **风险**: 调用者期望任意 field index 可用，运行时才发现限制。
- **建议**: 在接口 Javadoc 中说明限制，或移除 int field 重载。
- **误报排除**: 无。
- **复核状态**: 未复核

## 已验证合规项

- 未发现死 API：所有公共方法均有调用者
- 宥泛异常声明（throws Exception）是流处理框架的领域惯例
- API 表面积合理
