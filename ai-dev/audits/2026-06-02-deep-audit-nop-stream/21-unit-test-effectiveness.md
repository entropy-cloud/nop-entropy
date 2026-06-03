# Dimension 21: Unit Test Effectiveness — nop-stream

## 第 1 轮（初审）

### [维度21-01] TestWindowOperatorBuilder has 4 assertNotNull-only tests

- **File**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorBuilder.java:67-122`
- **Severity**: P2
- **Current state**: 4 of 8 tests only check assertNotNull on built operator. No verification of correct configuration.
- **Risk**: Builder misconfiguration could go undetected.
- **Recommendation**: Extend to verify operator configuration fields or feed minimal element through.
- **Confidence**: Very likely
- **False positive exclusion**: The other 4 tests in same file demonstrate proper behavioral verification.
- **Review status**: Unreviewed

### Positive Findings

1. Tests verify behavior, not structure — strong assertion density
2. NFA tests exercise full CEP pipeline with match content verification
3. Checkpoint recovery tests verify data correctness, not just state survival
4. TestNFAState value-object tests are appropriate for checkpoint serialization
