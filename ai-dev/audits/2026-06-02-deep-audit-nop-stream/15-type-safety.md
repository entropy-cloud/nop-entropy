# Dimension 15: Type Safety & Generics Usage — nop-stream

## 第 1 轮（初审）

### [维度15-01] KeyContext interface uses raw Object instead of generic <K>

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/KeyContext.java:27-31`
- **Evidence snippet**:
```java
public interface KeyContext {
    void setCurrentKey(Object key);
    Object getCurrentKey();
}
```
- **Severity**: P2
- **Current state**: Forces every implementor (16+ classes) to accept/return Object and perform unsafe casts.
- **Risk**: No compile-time key type safety across the entire operator chain.
- **Recommendation**: Generify to KeyContext<K>.
- **Confidence**: Certain
- **False positive exclusion**: 16+ implementors confirmed by code search.
- **Review status**: Unreviewed

### [维度15-02] Pervasive Map<String, Object> for state storage

- **Files**: OperatorSnapshotResult.java, TaskStateSnapshot.java, WindowAggregationState.java, MemoryKeyedStateBackend.java
- **Severity**: P2 (systemic pattern)
- **Current state**: All checkpoint state stored as Map<String, Object>. Every consumer performs unchecked casts. 15+ call sites affected.
- **Risk**: ClassCastException at runtime instead of compile errors.
- **Recommendation**: Parameterize where possible or add runtime type-checking in registration methods.
- **Confidence**: Certain
- **False positive exclusion**: The Map<String, Object> pattern appears in 4 core data structures.
- **Review status**: Unreviewed

### [维度15-03] JobGraphGenerator raw type escape hatch

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java:397-413`
- **Evidence snippet**:
```java
@SuppressWarnings({"unchecked", "rawtypes"})
private StreamOperator<?> createOperatorFromFactory(StreamNode node) {
    StreamOperatorFactory rawFactory = (StreamOperatorFactory) factory;
```
- **Severity**: P1
- **Current state**: Uses raw StreamOperatorFactory and TypeInformation to work around wildcard capture. Solvable with a private helper method with captured type variable.
- **Risk**: Raw-type escape propagates widely (3 call sites).
- **Recommendation**: Introduce `private <T> StreamOperator<T> createOperatorTyped(StreamOperatorFactory<T> factory, TypeInformation<T> outputType)`.
- **Confidence**: Certain
- **False positive exclusion**: The raw type usage is avoidable with a proper generic method.
- **Review status**: Unreviewed

### [维度15-04] (Class<T>) (Class<?>) Object.class double-cast anti-pattern (11 sites)

- **Files**: WindowOperatorBuilder.java, WindowOperator.java, WindowedStreamImpl.java
- **Severity**: P2
- **Current state**: Appears 11 times across 3 files. Bypasses compiler type checking to pass Object.class where specific Class<T> is expected.
- **Risk**: Downstream code using the class token for serialization will silently produce incorrect results.
- **Recommendation**: Thread type tokens through the DSL layer.
- **Confidence**: Certain
- **False positive exclusion**: The double-cast pattern is a well-known anti-pattern for erasure workarounds.
- **Review status**: Unreviewed

### [维度15-05] CepPatternBuilder and NFACompiler use raw Pattern types (22 @SuppressWarnings)

- **Files**: CepPatternBuilder.java (8), NFACompiler.java (14)
- **Severity**: P2
- **Current state**: 22 raw-type suppressions. Pattern<T, F> type parameters routinely erased.
- **Recommendation**: At minimum, narrow to Pattern<T, ?> since T is available from context.
- **Confidence**: Certain
- **False positive exclusion**: 22 suppressions in 2 files is excessive even for a streaming engine.
- **Review status**: Unreviewed
