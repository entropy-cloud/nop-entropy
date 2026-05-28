# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

**检查结论：未发现问题。**

### 检查范围

- 所有子模块：core, runtime, cep, connector, fraud-example
- 搜索项：beans.xml, @Inject, @Autowired, @Value, @InjectValue, @Component, @Service, @Configuration, _module, org.springframework

### 检查结果

1. **无 beans.xml 文件**：全文搜索确认 nop-stream 不包含任何 beans.xml 或 _module 文件
2. **无 IoC 注解**：0 处使用 @Inject/@Autowired/@Value/@InjectValue/@Component/@Service
3. **无 Spring 依赖**：0 处 import org.springframework.*
4. **SPI 文件正确**：`META-INF/services/io.nop.stream.core.execution.IDeploymentPlanProvider` 格式正确，实现类存在且实现对应接口
5. **扩展点通过构造函数/setter 装配**：IDeploymentPlanProvider (SPI), ICheckpointExecutorFactory (setter), IStateBackend (构造函数), ICheckpointStorage (构造函数)——全部显式传递，无 IoC 容器依赖

nop-stream 作为框架引擎模块，采用纯 Java SPI + 构造函数/setter 装配模式，完全符合其模块定位。
