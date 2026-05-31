# 维度 20：跨模块契约一致性

## 发现

### [维度20-01] IStreamExecutionDispatcher 无 SPI ServiceLoader 文件（P2）
- **文件**: runtime 无 META-INF/services/io.nop.stream.core.execution.IStreamExecutionDispatcher
- **现状**: 与 ICheckpointExecutorFactory/IDeploymentPlanProvider 的 SPI 模式不一致，需手动注入。

### [维度20-02] SinkFunction.consume() 实现窄化 throws Exception（P3）
- **文件**: connector/BatchConsumerSinkFunction.java:60, MessageSinkFunction.java:42

### [维度20-03] connector 缺 test-jar 依赖（P3）
- **文件**: nop-stream-connector/pom.xml

### [维度20-04] Batch/Message 连接器持有非序列化字段（P1→P2 实际影响待评估）
- **文件**: connector/BatchConsumerSinkFunction.java, MessageSinkFunction.java
- **现状**: StreamFunction extends Serializable 但 IBatchConsumer/IMessageService 非 Serializable。LOCAL 模式无影响，DISTRIBUTED 模式会 NotSerializableException。

### [维度20-05] SPI 注册正确匹配接口契约（确认通过）
### [维度20-06] ICheckpointExecutorFactory default 方法 javadoc 与实现不一致（P3）
### [维度20-07] fraud-example 直接使用 CEP NFA 内部类而非公共 API（P3）
