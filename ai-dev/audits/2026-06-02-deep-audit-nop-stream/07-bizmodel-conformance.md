# Dimension 07: BizModel Conformance — nop-stream Module Audit

## Dimension Assessment: NOT APPLICABLE (by design)

nop-stream is a **streaming computation engine** modeled after Apache Flink's DataStream API. No @BizModel, @BizQuery, @BizMutation, @BizLoader, beans.xml, xmeta, or graphql files exist. The module uses a programmatic builder-pattern API instead.

**What was checked (zero results for all):**
- `@BizModel`: 0 files
- `@BizQuery`, `@BizMutation`: 0 files
- `@BizLoader`, `@BizSaver`: 0 files
- `@Inject`, `@InjectValue`: 0 files
- `beans.xml` files: 0
- `*.xmeta` files: 0

**Actual API architecture**: StreamExecutionEnvironment → DataStream → KeyedStream → WindowedStream (Flink-style), CEP API, SPI-based service loading.

## 第 1 轮（初审）

### [维度07-01] nop-stream-api is an empty placeholder module with no source code

- **File**: `nop-stream/nop-stream-api/pom.xml:12-15`
- **Evidence snippet**:
```xml
    <artifactId>nop-stream-api</artifactId>
    <!-- placeholder, planned but not implemented -->
    <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
```
- **Severity**: P2
- **Current state**: The module is declared as a child of nop-stream but contains zero source files. Only a placeholder pom.xml.
- **Risk**: Other modules declaring dependency on nop-stream-api will get an empty JAR, causing class-not-found errors. Creates misleading module structure.
- **Recommendation**: Either populate with public-facing interfaces and have core depend on it, or remove from reactor.
- **Confidence**: Certain
- **False positive exclusion**: The module exists in the reactor pom, is published as an artifact, and creates real dependency confusion.
- **Review status**: Unreviewed

### [维度07-02] KeyedStreamImpl dual-constructor delegation creates fragile branching throughout the class

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:39-98`
- **Evidence snippet**:
```java
    public KeyedStreamImpl(
            StreamExecutionEnvironment environment,
            Transformation<T> transformation,
            KeySelector<T, KEY> keySelector) {
        super(environment, transformation);
        this.keySelector = keySelector;
        this.parentStream = null;
    }

    public KeyedStreamImpl(DataStream<T> parentStream, KeySelector<T, KEY> keySelector) {
        super(parentStream instanceof DataStreamImpl ? ((DataStreamImpl<T>) parentStream).getEnvironment() : null,
              parentStream instanceof DataStreamImpl ? ((DataStreamImpl<T>) parentStream).getTransformation() : null);
        this.keySelector = keySelector;
        this.parentStream = parentStream;
    }

    @Override
    public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
        if (parentStream != null) {
            return parentStream.map(mapper);
        }
        return super.map(mapper);
    }
```
- **Severity**: P2
- **Current state**: Two constructors — every overridden method (map, filter, flatMap, transform, print, print(SinkFunction), collect, sink, keyBy) contains `if (parentStream != null)` branch. 9 duplicated if/else branches.
- **Risk**: If a new method is added and overridden, forgetting the guard causes NullPointerException at runtime.
- **Recommendation**: Unify to single construction path. Use template-method or strategy pattern to eliminate duplicated branches.
- **Confidence**: Certain
- **False positive exclusion**: Systematic pattern where every method must follow the same branching discipline. A missed branch causes NPE at runtime.
- **Review status**: Unreviewed

### [维度07-03] SingleOutputStreamOperatorImpl.forceNonParallel() is a documented no-op

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperatorImpl.java:46-52`
- **Evidence snippet**:
```java
    @Override
    public SingleOutputStreamOperator<T> forceNonParallel() {
        // In a full implementation, this would set the parallelism to 1
        // and mark the operator as non-parallelizable
        // For now, we just return this instance
        return this;
    }
```
- **Severity**: P1
- **Current state**: Public API method on `SingleOutputStreamOperator` interface does nothing — silently returns `this` without affecting execution.
- **Risk**: Users calling `stream.forceNonParallel()` assume parallelism=1, but it runs at default parallelism. Can produce incorrect results for operations requiring global ordering. Silent failure is particularly dangerous.
- **Recommendation**: Either implement the method (set parallelism=1 on transformation) or throw `UnsupportedOperationException` / `StreamException` to make incompleteness explicit.
- **Confidence**: Certain
- **False positive exclusion**: Public interface method, not internal. Users will naturally call it expecting it to work.
- **Review status**: Unreviewed

### [维度07-04] FieldAggregationReducer uses reflection with setAccessible(true) — JPMS compatibility risk

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:267-273`
- **Evidence snippet**:
```java
        private void initField(Class<?> clazz) throws NoSuchFieldException {
            if (fieldAccessor == null) {
                Field f = findField(clazz, fieldName);
                f.setAccessible(true);
                fieldAccessor = f;
            }
        }
```
- **Severity**: P2
- **Current state**: `FieldAggregationReducer` (used by `sum(String)`, `min(String)`, `max(String)`) uses `Field.setAccessible(true)`. The `fieldAccessor` field is `transient`.
- **Risk**: (1) In JPMS environments, `setAccessible(true)` can throw `InaccessibleObjectException`. (2) After serialization/deserialization in distributed streaming, lazy re-initialization may fail with `NoSuchFieldException` during rolling upgrades. (3) Mutates accumulator in-place.
- **Recommendation**: Consider using a functional approach (Function/BiConsumer) instead of reflection, or use Nop platform's built-in property access utilities.
- **Confidence**: Very likely
- **False positive exclusion**: Concrete runtime risk in JPMS environments. Project uses Java 21.
- **Review status**: Unreviewed

### [维度07-05] Ten @Deprecated classes without removal plan or migration path

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/` (multiple files)
- **Evidence snippet**:
```java
@Deprecated
public class WindowAggregationOperator<T, ACC, OUT, KEY, W extends Window>
```
- **Severity**: P3
- **Current state**: 10 `@Deprecated` classes in core (ReduceAggregationFunction, ProcessWindowAggregationFunction, WindowAggregationState, WindowAggregationOperator, etc.). None have `@deprecated` Javadoc specifying removal timeline or replacement. Some are still used internally as fallback paths.
- **Risk**: Misleading deprecation — classes are used in `WindowedStreamImpl` fallback paths.
- **Recommendation**: Either remove `@Deprecated` from actively-used fallback classes, or add Javadoc `@deprecated` tags pointing to replacement API.
- **Confidence**: Certain
- **False positive exclusion**: Production code in core module, used in main execution path as fallback.
- **Review status**: Unreviewed

### [维度07-06] DataStream interface missing overloaded methods with explicit TypeInformation

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStream.java:40-61` vs `DataStreamImpl.java:145-189`
- **Evidence snippet**:
```java
// Interface:
    <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper);

// Implementation adds overloads not in interface:
    public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper, TypeInformation<R> typeInfo) {
        return transform("Map", typeInfo, new StreamMap<>(mapper));
    }
```
- **Severity**: P2
- **Current state**: `DataStream` interface only declares `map(MapFunction)` without `TypeInformation` overloads. `DataStreamImpl` provides additional public methods not in the interface.
- **Risk**: Users programming against `DataStream` interface cannot access explicit-type overloads. Must downcast to `DataStreamImpl`, breaking abstraction.
- **Recommendation**: Add `TypeInformation`-overloaded variants to the `DataStream` interface.
- **Confidence**: Certain
- **False positive exclusion**: Concrete API completeness issue. KeyedStreamImpl delegates through the DataStream interface, so explicit-type methods not accessible on delegated path.
- **Review status**: Unreviewed
