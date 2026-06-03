# Dimension 08: IoC & Bean Configuration — nop-stream

## 第 1 轮（初审）

### [维度08-01] ICheckpointExecutorFactory SPI Registration Is Dead Code

- **File**: `nop-stream/nop-stream-runtime/src/main/resources/META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`
- **Evidence snippet**:
```
io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl
```
- **Severity**: P2
- **Current state**: SPI file registers `CheckpointExecutorFactoryImpl` but no code in nop-stream ever calls `ServiceLoader.load(ICheckpointExecutorFactory.class)`. The factory is instead wired via static setter `StreamExecutionEnvironment.setCheckpointExecutorFactory()`. The SPI file is dead code and the two wiring strategies are inconsistent.
- **Risk**: If a user adds nop-stream-runtime to classpath expecting checkpoint support to "just work" (as IDeploymentPlanProvider does), it will not. The `checkpointExecutorFactory` will remain null.
- **Recommendation**: Either add ServiceLoader-based auto-discovery consistent with IDeploymentPlanProvider, or remove the dead SPI file and document manual registration.
- **Confidence**: Certain
- **False positive exclusion**: Confirmed by searching all ServiceLoader.load() calls in the module — only IDeploymentPlanProvider uses it.
- **Review status**: Unreviewed

### [维度08-02] IWindowOperatorFactory Is Never Wired in Production Flow

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:26-33`
- **Severity**: P3
- **Current state**: `StreamComponents.windowOperatorFactory` is always null in production. The factory is only set in test code. All window operations fall back to deprecated inline WindowAggregationFunction code paths.
- **Risk**: The runtime WindowOperator implementation is never used in production.
- **Recommendation**: Add SPI-based auto-discovery or setter on StreamExecutionEnvironment.
- **Confidence**: Very likely
- **False positive exclusion**: Confirmed by checking all setWindowOperatorFactory() calls — only test code invokes it.
- **Review status**: Unreviewed

### Positive Findings
- `IDeploymentPlanProvider` SPI is correctly implemented with ServiceLoader consumption and fallback
- Zero NopIoC/Spring artifacts found (no beans.xml, _module, @Inject, @Autowired)
- EmbeddedDistributedExecutor correctly uses manual wiring (requires IMessageService constructor arg)
