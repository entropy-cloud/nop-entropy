# 维度08：IoC 与 Bean 配置

## 第 1 轮（初审）

### 检查范围说明

nop-stream 不是标准业务模块，其 IoC 使用非常轻量：

- **beans.xml 文件**：nop-stream-core 和 nop-stream-cep 各有一个 `META-INF/services/` SPI 配置文件（`IDeploymentPlanProvider` 和 `ICheckpointExecutorFactory`），但无传统 beans.xml 配置。
- **@Inject 使用**：仅在 nop-stream-runtime 中的 `GraphModelCheckpointExecutor` 和 `CheckpointCoordinator` 有少量使用，均为 protected 字段，符合 NopIoC 规范。
- **@InjectValue 使用**：`NopCepConfigs` 使用 `@InjectValue` 注入配置值，格式正确。
- **_module 文件**：无传统 `_module` 文件，模块注册通过 SPI 实现。

**结论**：nop-stream 的 IoC 使用极其有限，主要依赖 SPI（ServiceLoader）模式而非 NopIoC beans.xml。无违规发现。

### 零发现确认

- 无手写修改 _service.beans.xml 等生成文件（无 beans.xml）✓
- @Inject 字段均为 protected ✓
- 无误用 Spring 注解（@Value/@Autowired）✓
- bean 命名遵循平台约定 ✓
