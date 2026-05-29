# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] ICheckpointExecutorFactory lacks SPI registration unlike IDeploymentPlanProvider

- **文件**: `nop-stream-core/.../StreamExecutionEnvironment.java` (static setter), `nop-stream-runtime/` (no META-INF/services)
- **严重程度**: P2
- **现状**: IDeploymentPlanProvider 使用 ServiceLoader 自动发现，而 ICheckpointExecutorFactory 使用静态 setter 手动注册。nop-stream-runtime 未提供 META-INF/services 文件。如果无人调用 setter，checkpoint 功能静默退化为非 checkpoint 本地执行。
- **风险**: "开发环境正常、生产环境失败"场景——添加 runtime JAR 但忘记调用 setter 导致 checkpoint 功能不激活。
- **建议**: 在 nop-stream-runtime 中添加 META-INF/services 文件，或显著文档化手动注册需求。
- **误报排除**: 不是过度工程要求。同一模块中已有 SPI 自动发现的先例（IDeploymentPlanProvider），ICheckpointExecutorFactory 的不一致是真实的契约差异。
- **复核状态**: 未复核
