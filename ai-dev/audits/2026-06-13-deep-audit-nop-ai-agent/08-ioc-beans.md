# 维度 08：IoC 与 Bean 配置 — nop-ai-agent

**目标模块**: `nop-ai/nop-ai-agent`
**审计日期**: 2026-06-13
**基线确认**: 本模块为纯库（library），无 `_service.beans.xml`、无手写 `app-service.beans.xml`、无 `_module` 文件。DI 由调用方完成。审计重点转向 Java 代码中的注入模式与 Bean 约定。

## 第 1 轮（初审）

### 基线扫描摘要

| 检查项 | 工具 | 结果 |
|--------|------|------|
| `@Inject` 字段注入 | grep src/main/java | **0 命中** |
| `@InjectValue` 配置注入 | grep | **0 命中** |
| Spring 误用（`@Autowired`/`@Value`/`@Component`/.../`org.springframework.*`） | grep | **0 命中** |
| `jakarta.inject` / `javax.inject` JSR-330 | grep | **0 命中** |
| JSR-250（`@Singleton`/`@PostConstruct`/`@PreDestroy`） | grep | **0 命中** |
| 容器静态访问（`BeanContainer.`/`AppContainerHolder`/`injectBean`/`getInstance(`） | grep | **0 命中**（`DefaultAgentEngine.java:314` 的 `ResourceComponentManager.instance()` 是 nop-core 平台 API） |
| `_service.beans.xml` / `app-service.beans.xml` | glob | **0 文件** |
| `_module` 文件 | glob | **0 文件** |
| XML 中 `<bean>` / `<ioc-config>` / `ioc:` | grep `*.xml` | **0 命中**（pom.xml 中 `nop-` 前缀均为 Maven artifactId） |

### 关键证据（合规）

`io/nop/ai/agent/engine/DefaultAgentEngine.java:39-121`:
```java
public class DefaultAgentEngine implements IAgentEngine {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
    private final IChatService chatService;
    private final IToolManager toolManager;
    ...
    public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                               ISessionStore sessionStore, IPermissionProvider permissionProvider,
                               IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker,
                               IContentGuardrail contentGuardrail, IModelRouter modelRouter,
                               IContextCompactor contextCompactor) {
        this.chatService = chatService;
        ...
        this.permissionProvider = permissionProvider != null ? permissionProvider : new AllowAllPermissionProvider();
        ...
```
判定：构造器链 + null-defense 默认值，完全由调用方装配。无字段注入、无注解。

`ReActAgentExecutor.java:67-119`: 纯 POJO + Builder，private 构造器，无可疑注入点。

**Null-Object 单例（5 处，全部合规）**: PassThroughModelRouter / NoOpToolCallRepairer / NoOpHookRegistry / NoOpContentGuardrail / NoOpContextCompactor 均为 `static final INSTANCE` + private 构造器 + 无状态/不可变。属标准 Java 库设计模式（Effective Java 第 3 条），不构成"绕过 IoC"问题。

### 发现条目

**无发现。**

### 合规性总结

1. 步骤 1-3（beans.xml）：N/A —— 本模块无任何 beans.xml。
2. 步骤 4（Java 注入方式）：全模块 `@Inject`/`@InjectValue`/`@Autowired`/`@Value`/JSR-250/JSR-330 import 均 0 命中。NopIoC 私有字段注入陷阱在此模块**不存在触发条件**。
3. 步骤 5-7（命名/module/import）：N/A。
4. 步骤 8（循环依赖）：N/A。
5. 扩展项（"应注册为 Bean 但缺失的类"）：未发现期望通过 IoC 装配但缺少配置的类；DefaultAgentEngine 等关键类全部以构造器/Builder 暴露，是库设计的正确形态。
6. 扩展项（"静态单例是否不一致地绕过 IoC"）：5 处单例模式一致（Null-Object），无"半 IoC 半静态"现象。

## 复核结论表

| 维度 | 条目数 | 严重程度分布 | 复核状态 |
|------|--------|-------------|----------|
| 08 | **0** | — | 未复核 |

信心水平：高。手工与 grep 双重扫描覆盖完整；维度 08 所有 8 个执行步骤均因前置条件缺失（无 beans.xml、无注解）而天然满足。
