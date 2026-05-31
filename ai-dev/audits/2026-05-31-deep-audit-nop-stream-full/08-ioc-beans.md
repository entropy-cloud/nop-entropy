# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### 零发现

**检查范围声明**：

| 检查项 | 结果 |
|--------|------|
| beans.xml 文件 | 未发现 |
| @Inject / @InjectValue 注入 | 未发现 |
| Spring 注解误用 (@Value/@Autowired/@Bean/@Component) | 未发现 |
| _module 文件注册 | 未发现 |
| BeanContainer / IBeanContainer | 未发现 |

nop-stream 使用标准 Java SPI（ServiceLoader）实现模块间解耦，而非 NopIoC 容器。这是合理的设计决策：
- `IDeploymentPlanProvider` SPI: runtime 提供 DeploymentPlanProviderImpl
- `ICheckpointExecutorFactory` SPI: runtime 提供 CheckpointExecutorFactoryImpl
- 当 classpath 上无 runtime 时，core 内置 DefaultDeploymentPlanProvider 作为回退
