# Dimension 20: Cross-Module Contract Consistency — nop-stream

## 第 1 轮（初审）

### [维度20-01] StreamConnectors public API exposes optional dependency types

- **File**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/StreamConnectors.java:41-52`
- **Severity**: P2
- **Current state**: nop-batch-core types (IBatchConsumerProvider, IBatchLoaderProvider) appear in public method signatures despite being declared optional.
- **Risk**: API surface suggests nop-batch-core is required when it is optional.
- **Recommendation**: Add Javadoc clearly stating the nop-batch-core dependency requirement. This is acceptable since the class IS specifically for batch integration.
- **Confidence**: Very likely
- **False positive exclusion**: The class is specifically designed for batch integration, so the dependency leak is intentional.
- **Review status**: Unreviewed

### Positive Findings

1. nop-stream-core public API is interface-driven and stable (54 public interfaces)
2. Optional dependencies properly scoped (not leaking into core public API)
3. @Internal annotations guard runtime-specific implementation classes
