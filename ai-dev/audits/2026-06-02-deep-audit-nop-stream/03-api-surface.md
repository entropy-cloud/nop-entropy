# Dimension 03: API Surface & Contract Consistency — nop-stream

## 第 1 轮（初审）

### [维度03-01] WindowedStream incorrectly extends DataStream

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/WindowedStream.java:34`
- **Evidence snippet**:
```java
public interface WindowedStream<T, K, W extends Window> extends DataStream<T> {
    <R> SingleOutputStreamOperator<R> apply(WindowFunction<T, R, K, W> function);
    <ACC, R> SingleOutputStreamOperator<R> aggregate(AggregateFunction<T, ACC, R> function);
}
```
- **Severity**: P2
- **Current state**: WindowedStream extends DataStream, meaning a WindowedStream can be used wherever DataStream is expected, including calling map(), filter(), keyBy() etc. which would silently bypass windowing. In Apache Flink, WindowedStream does NOT extend DataStream.
- **Risk**: User could write stream.keyBy(...).window(...).map(...) expecting windowed behavior but getting plain map.
- **Recommendation**: Remove `extends DataStream<T>` from WindowedStream. It should be standalone with only window-specific operations.
- **Confidence**: Certain
- **False positive exclusion**: Flink's WindowedStream explicitly avoids this design.
- **Review status**: Unreviewed
