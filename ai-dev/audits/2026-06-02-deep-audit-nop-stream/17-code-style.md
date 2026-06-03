# Dimension 17: Code Style & Conventions — nop-stream

## 第 1 轮（初审）

### [维度17-01] 603 unused imports across 434 main Java files

- **File**: Multiple files, worst offenders: WindowOperator.java (47/53 unused), CepOperator.java (38 unused), DataStreamImpl.java (18 unused)
- **Severity**: P2
- **Current state**: Massive accumulation of unused imports in Flink-ported files.
- **Risk**: Misleading dependencies, harder code navigation.
- **Recommendation**: Run IDE "organize imports" or automated cleanup; add to CI.
- **Confidence**: Certain
- **False positive exclusion**: Analysis checks actual symbol usage.
- **Review status**: Unreviewed

### [维度17-02] Duplicate PrintSink implementations

- **File**: PrintSink.java and PrintSinkFunction.java in nop-stream-core
- **Severity**: P2
- **Current state**: Two nearly identical PrintSink classes implementing same interface with same logic.
- **Recommendation**: Deprecate one (preferably PrintSink).
- **Confidence**: Very likely
- **False positive exclusion**: Both implement SinkFunction<T> with same System.out.println logic.
- **Review status**: Unreviewed

### [维度17-03] 2 import ordering violations

- **File**: InternalSingleValueProcessWindowFunction.java, InternalIterableProcessWindowFunction.java
- **Severity**: P2
- **Current state**: java.* imports placed after io.nop.* imports (Flink port artifacts).
- **Recommendation**: Reorder to match convention (java.* → jakarta.* → third-party → io.nop.*).
- **Confidence**: Certain
- **False positive exclusion**: Automated check confirmed only 2 violations in 434 files.
- **Review status**: Unreviewed
