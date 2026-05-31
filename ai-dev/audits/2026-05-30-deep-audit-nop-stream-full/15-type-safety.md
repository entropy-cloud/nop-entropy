# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### 检查范围

423 个 main source Java 文件。124 处 `@SuppressWarnings("unchecked")`，3 处原始类型使用。

### [维度15-01] StreamComponents.getBean() 提供 Class<T> 参数但未使用

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:138`
- **证据片段**:
```java
@Internal
@SuppressWarnings("unchecked")
public <T> T getBean(String id, Class<T> clazz) {
    Object bean = windowingStrategies.get(id);
    if (bean == null) {
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Bean not found: " + id);
    }
    return (T) bean;  // clazz 未使用
}
```
- **严重程度**: P3
- **现状**: `clazz` 参数已提供但未调用 `clazz.cast(bean)`，类型不匹配时错误延迟到下游。
- **风险**: 调用者传入了类型约束但方法完全忽略，降低了类型安全保证。
- **建议**: 改为 `return clazz.cast(bean)`，利用 Class<T> 做运行时类型检查。
- **信心水平**: 确定
- **误报排除**: 参数已声明但未使用是可验证的代码缺陷。
- **复核状态**: 未复核

### [维度15-02] MessageSourceFunction.run() 对消息体盲目转型

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:110`
- **证据片段**:
```java
subscription = messageService.subscribe(effectiveTopic, new IMessageConsumer() {
    @Override
    public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
        ctx.collect((T) msg);  // 盲目转型，无运行时检查
        return null;
    }
});
```
- **严重程度**: P3
- **现状**: msg 类型完全由外部消息系统决定，无 instanceof 防御。
- **风险**: ClassCastException 在下游算子处理时才抛出，错误定位困难。
- **建议**: 增加 instanceof 防御性检查或在方法注释中明确类型安全假设。
- **信心水平**: 很可能
- **误报排除**: 无检查的盲目转型在真实消息系统中可导致运行时错误。
- **复核状态**: 未复核

---

### 整体评价

核心接口（SourceFunction<T>、SinkFunction<T>、StreamOperator<OUT>、KeyedStream<T, KEY>等）泛型使用正确且一致。124 处 @SuppressWarnings 集中在状态序列化、类型擦除后端、算子连线等流处理框架的经典泛型逃逸口，属业界普遍做法。

## 维度复核结论

（待复核）

## 最终保留项

（待复核后填写）
