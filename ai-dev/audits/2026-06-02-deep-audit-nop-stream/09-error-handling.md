# Dimension 09: Error Handling & Error Codes — nop-stream

## 第 1 轮（初审）

### [维度09-01] Snapshot error silently swallowed in AbstractStreamOperator.processBarrier

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/AbstractStreamOperator.java:262-285`
- **Evidence snippet**:
```java
} catch (Exception e) {
    snapshotError = e;
}
// ...
if (snapshotCallback != null) {
    if (snapshotResult != null) {
        snapshotCallback.accept(snapshotResult);
    } else if (snapshotError != null) {
        OperatorSnapshotResult failureResult = new OperatorSnapshotResult();
        snapshotCallback.accept(failureResult);   // error discarded here
    }
}
```
- **Severity**: P1
- **Current state**: When snapshotError != null but snapshotCallback is null, the snapshot error is completely lost — not logged, not rethrown. Even when callback is invoked, the error is discarded and an empty failureResult is passed.
- **Risk**: Silent data loss during checkpoint failures can lead to undiagnosable consistency issues.
- **Recommendation**: Always log the snapshot error (at minimum LOG.error). When callback is null, rethrow or at minimum log. When callback is present, pass the error to the callback.
- **Confidence**: Certain
- **False positive exclusion**: The error is caught, assigned to snapshotError, but then either discarded (no callback) or replaced with empty result (callback path).
- **Review status**: Unreviewed

### [维度09-02] ERR_CEP_MALFORMED_PATTERN thrown without .param() in 12+ locations

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/pattern/Pattern.java` (8x), `NFACompiler.java` (4x)
- **Evidence snippet**:
```java
throw new MalformedPatternException(ERR_CEP_MALFORMED_PATTERN);
// No .param(ARG_PATTERN_DETAIL, ...) provided
```
- **Severity**: P2
- **Current state**: ERR_CEP_MALFORMED_PATTERN has a `{patternDetail}` placeholder but 12+ throws never provide `.param(ARG_PATTERN_DETAIL, ...)`. Users always see "Malformed CEP pattern: {patternDetail}" with no diagnostic detail.
- **Risk**: Uninformative error messages make debugging pattern issues very difficult.
- **Recommendation**: Add `.param(ARG_PATTERN_DETAIL, ...)` with context (pattern name, constraint violated, etc.) to all throws.
- **Confidence**: Certain
- **False positive exclusion**: Only one location in CepPatternBuilder.java correctly uses .param(). All others omit it.
- **Review status**: Unreviewed

### [维度09-03] Bare UnsupportedOperationException in 6 core/CEP public API locations

- **Files**: WindowAggregationFunction.java:20, Trigger.java:103, ICheckpointExecutorFactory.java:77, RecordWriter.java:127, GroupPattern.java:44-57, RichIterativeCondition.java:51
- **Evidence snippet**:
```java
throw new UnsupportedOperationException("merge not implemented");
throw new UnsupportedOperationException("This trigger does not support merging.");
throw new UnsupportedOperationException("GroupPattern does not support where clause.");
```
- **Severity**: P2
- **Current state**: 6 locations in core/CEP public API use bare UnsupportedOperationException instead of StreamException with ErrorCode.
- **Risk**: Violates the two-tier error handling convention for public APIs.
- **Recommendation**: Replace with StreamException(ERR_STREAM_UNSUPPORTED) or MalformedPatternException(ERR_CEP_MALFORMED_PATTERN) with appropriate .param() context.
- **Confidence**: Certain
- **False positive exclusion**: Per the error handling guide, public APIs should use ErrorCode pattern.
- **Review status**: Unreviewed

### [维度09-04] StreamException constructed with e.getMessage() bypasses ErrorCode (3 locations)

- **File**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperatorBuilder.java:141-146, 207-211, 227-231`
- **Evidence snippet**:
```java
throw new io.nop.stream.core.exceptions.StreamException(e.getMessage(), e);
```
- **Severity**: P2
- **Current state**: Uses string-based constructor instead of ErrorCode constructor in 3 locations.
- **Risk**: Bypasses the ErrorCode system for error reporting.
- **Recommendation**: Use `StreamException(ERR_STREAM_OPERATOR_ERROR, e).param(ARG_OPERATOR_NAME, "reduceFunction")`.
- **Confidence**: Certain
- **False positive exclusion**: The string constructor bypasses the entire ErrorCode mechanism.
- **Review status**: Unreviewed

### Positive Findings
- 47 error codes in NopStreamErrors, 9 in NopCepErrors — all English-only
- Regression tests enforce English-only messages
- InterruptedException properly handled with Thread.currentThread().interrupt()
- Exception chain preservation is generally good
