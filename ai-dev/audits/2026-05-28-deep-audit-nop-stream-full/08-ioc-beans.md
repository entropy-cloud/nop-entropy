# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### 零发现

#### 检查范围

| 检查项 | 结果 |
|--------|------|
| beans.xml 文件 | 0 |
| _module 文件 | 0 |
| @Inject 注解 | 0 |
| @InjectValue 注解 | 0 |
| @Autowired (Spring) | 0 |
| @Value (Spring) | 0 |
| import jakarta.inject.* | 0 |
| import org.springframework.* | 0 |

nop-stream 使用手动构造模式和 Java SPI（ServiceLoader）进行对象装配，不依赖 NopIoC 或 Spring IoC。这是引擎内部模块的正确做法。唯一资源文件是 META-INF/services/io.nop.stream.core.execution.IDeploymentPlanProvider 的 SPI 注册。
