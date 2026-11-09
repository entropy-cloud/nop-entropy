# IoC 与配置注入

本页只保留与当前仓库 AI 开发最相关的 IoC 规则。

## 默认结论

1. 字段注入可以使用 `@Inject`，但字段不能是 `private`。
2. 配置注入使用 `@InjectValue`。
3. 常见配置文件是 `bootstrap.yaml`、`application.yaml`、`application-{profile}.yaml`。
4. 通过 `nop.profile` 激活 profile。
5. 需要明确依赖时，也可以使用 setter 注入。
6. 不要把 Spring 专有注解和 Spring AOP 用法当作当前仓库默认模式。

## 推荐写法

```java
public class MyComponent {
    @Inject
    protected MyDependency dependency;

    @InjectValue("@cfg:app.config-value|default-value")
    protected String configValue;

    @InjectValue("@cfg:app.timeout|30")
    protected int timeout;
}
```

## 配置文件与 profile

| 文件 | 作用 |
|------|------|
| `bootstrap.yaml` | 启动期基础配置，优先进入配置系统 |
| `application.yaml` | 应用主配置 |
| `application-{profile}.yaml` | 按 `nop.profile` 激活的环境覆盖配置 |

## 配置优先级

配置按 **source 优先级** 从高到低合并，同一 source 内 profile 专有值覆盖非 profile 值。**跨 source 时高优先级 source 直接生效，低优先级 source 的 profile 值不能覆盖高优先级 source 的非 profile 值**（与 Quarkus / Spring Boot 行为一致）。

| 优先级（高→低） | Source | 示例 |
|------|--------|------|
| 1 | 配置中心 | Nacos / Apollo |
| 2 | key file / props file | `keyfile:/...`, `propsfile:/...` |
| 3 | 环境变量 | `NOP_DATASOURCE_JDBC__URL=...` |
| 4 | **系统属性 `-D`** | `-Dnop.datasource.jdbc-url=...` |
| 5 | `bootstrap.yaml` | 启动期基础配置 |
| 6 | 扩展配置加载器 | SPI `IConfigSourceLoader` |
| 7 | `application.yaml` + `application-{profile}.yaml` | 应用主配置 |

**关键规则**：`-D` 系统属性可以覆盖 `application.yaml` 中的一切值，包括 `%dev.xxx` 等 profile 专有值。

### 环境变量命名规则

Nop 通过 `StringHelper.envToConfigVar()` 将环境变量名映射为配置键。映射规则（实现见 `EnvConfigSourceLoader.java`）：

| 环境变量中的模式 | 映射为配置键 | 说明 |
|---|---|---|
| `_` （单下划线） | `.` （点号） | 配置键的层级分隔符 |
| `__` （双下划线） | `-` （连字符） | 配置键中的 kebab-case 连字符 |
| `___` （三下划线） | `_` （下划线） | 配置键中原生的下划线 |

**示例：**

| 配置键 | 等价的 `-D` 参数 | 环境变量 |
|---|---|---|
| `nop.datasource.jdbc-url` | `-Dnop.datasource.jdbc-url=...` | `NOP_DATASOURCE_JDBC__URL=...` |
| `nop.web.render-mode` | `-Dnop.web.render-mode=...` | `NOP_WEB_RENDER__MODE=...` |
| `nop.profile` | `-Dnop.profile=...` | `NOP_PROFILE=...` |

**常见误区：**

- ❌ `NOP_WEB_RENDER_MODE` → 实际解析为 `nop.web.render.mode`（多了一个点，变成四级路径）
- ❌ `NOP_DATASOURCE_JDBC_URL` → 实际解析为 `nop.datasource.jdbc.url`（连字符变点）
- ✅ `NOP_WEB_RENDER__MODE` → 正确解析为 `nop.web.render-mode`
- ✅ `NOP_DATASOURCE_JDBC__URL` → 正确解析为 `nop.datasource.jdbc-url`

反向映射 `StringHelper.configVarToEnv()` 用于从配置键生成环境变量名，规则同上逆推。

### 激活 profile

最常见的是：

```yaml
nop:
  profile: dev
```

也可以通过启动参数设置 `-Dnop.profile=dev`。

## 通过命令行参数覆盖配置

系统属性（`-D` 参数）优先级高于 `application.yaml`（包括其 profile 覆盖），可直接覆盖任意 `nop.*` 配置项。

### 数据源覆盖

`application.yaml` 中的数据源配置：

```yaml
nop:
  datasource:
    driver-class-name: org.h2.Driver
    jdbc-url: jdbc:h2:./db/test
    username: sa
    password:
```

对应的命令行覆盖参数：

```bash
java -Dnop.datasource.jdbc-url=jdbc:h2:mem:e2e \
     -Dnop.datasource.driver-class-name=org.h2.Driver \
     -Dnop.datasource.username=sa \
     -Dnop.orm.init-database-schema=true \
     -jar app/target/quarkus-app/quarkus-run.jar
```

配置键到 `-D` 参数名的映射规则：YAML 嵌套层级用 `.` 连接，驼峰转 kebab-case。例如 `nop.datasource.jdbcUrl` → `nop.datasource.jdbc-url`。

**常见场景：E2E 测试使用内存数据库**

当 `application.yaml` 配置了文件型 H2（`jdbc:h2:./db/test`）时，多个应用实例会因文件锁冲突而启动失败。通过 `-Dnop.datasource.jdbc-url=jdbc:h2:mem:e2e` 切换到内存数据库可避免此问题，且无需修改配置文件。即使 `%dev` profile 中也定义了 `jdbc-url`，`-D` 参数仍能覆盖它。

```bash
java -Dquarkus.profile=dev \
     -Dnop.datasource.jdbc-url=jdbc:h2:mem:e2e \
     -jar app/target/quarkus-app/quarkus-run.jar
```

> **注意**：内存数据库的数据在 JVM 退出后即丢失。`%dev` profile 的 `init-database-schema: true` 会自动建表并初始化种子数据。如果未使用 `%dev` profile，需要显式设置 `-Dnop.orm.init-database-schema=true`。

### 实现锚点

- 配置键定义：`DaoConfigs.java`（`CFG_DATASOURCE_JDBC_URL` 等）
- 数据源 bean 绑定：`quarkus-defaults.beans.xml` → `${nop.datasource.jdbc-url}` 占位符
- 单元/集成测试中同样的机制：`AutoTestCase.java` 通过 `setTestConfig(DaoConfigs.CFG_DATASOURCE_JDBC_URL, "jdbc:h2:mem:" + uuid)` 设置随机内存库

## 为什么 `private` 不行

当前仓库源码中，`ClassModelBuilder` 在发现字段时会跳过 `private` 字段，因此 `@Inject private Foo foo;` 不会成为可靠写法。

## 配置相关的默认判断

| 场景 | 默认做法 |
|------|---------|
| 注入 bean | `@Inject` |
| 注入配置值 | `@InjectValue("@cfg:key\|default")` |

> `@cfg:` 在 IoC 容器中由 `ConfigExpressionProcessor` 处理；YAML / JSON / XML DSL 加载阶段的 `@cfg:` 由 `ValueResolverCompilerRegistry.DEFAULT` 处理（同一写法、不同实现）。完整说明见 `./value-resolvers.md`。
| 需要动态测试配置 | `@NopTestConfig(testConfigFile = ...)` |

## 模块与 bean 发现规则

1. Nop 应用侧 bean 发现默认是基于文件（`_vfs/**/*.beans.xml`），不是基于 Java classpath annotation scanning。`@BizModel`、`@Inject` 等注解只用于**元数据标记**和**字段注入**，不会触发自动 bean 注册。所有 bean 必须在 `beans.xml` 中有显式定义。
2. 因此，Spring 中的 `@Component` / `@Service` / `@Repository` 模式在此仓库中不存在——不应假设标注了 `@BizModel` 的类会被自动注册为 IoC bean。
3. 启用模块通过 `/{moduleId}/_module` 被发现（零字节标记文件）。详见 `vfs-and-resource-resolution.md` 的"模块发现与注册"章节。
4. app 容器自动加载的通常是 `/{moduleId}/beans/app.beans.xml` 和 `app-*.beans.xml`。
5. **关键约束：若模块在 `_vfs/{moduleId}/beans/` 下放置了 `app-*.beans.xml` 文件，则该模块 JAR 必须在 `_vfs/{moduleId}/_module` 处提供零字节标记文件，否则该模块的 beans 不会被自动发现。** 例如 `nop-job-local` 作为独立模块使用时，必须提供自己的 `_module` 文件（即使 `nop-job-dao`/`nop-job-service` 等也提供了同一 moduleId 的 `_module`——这是因为 `_module` 文件是 VFS 中唯一允许重复的资源类型，多个子模块可以为同一 moduleId 放置 `_module` 而不会冲突）。
6. 每个模块可选提供 `/{moduleId}/app.module.yaml`，包含 `version`、`displayName`、`description`、`dependsOn` 等元数据。详见 `vfs-and-resource-resolution.md` 的 `app.module.yaml` 章节。
7. `/nop/autoconfig/*.beans` 和 `nop.ioc.app-beans.files` 也可以补充 bean 文件。
8. `_service.beans.xml` 这类生成文件通常是被 `app-service.beans.xml` 导入，而不是自己被自动发现。
9. **所有以 `_` 开头的文件都是 codegen 管线自动生成的，不允许手动修改**。包括但不限于 `_service.beans.xml`、`_dao.beans.xml`、`_app.orm.xml`、`_gen/*.java` 等。如需定制 IoC 注册，修改对应的非下划线文件（如 `app-service.beans.xml`）。如需添加新 BizModel 但 `codegen` 尚未生成，在 `app-service.beans.xml` 中手动添加 bean 定义。

## `<ioc:collect-beans>`：按类型/注解自动收集 Bean

Nop IoC 提供 `<ioc:collect-beans>` 标签，用于在 bean 定义中声明式地收集容器中所有符合条件的 bean，无需手写 `@Inject List<T>`。

### 基本用法

```xml
<bean id="myRegistry" class="...MyRegistry">
    <property name="providers">
        <!-- 按接口类型收集所有实现 -->
        <ioc:collect-beans by-type="io.nop.erp.api.IErpFinAcctDocProvider"/>
    </property>
    <property name="listeners">
        <!-- 按注解收集所有标注了 @MyAnnotation 的 bean -->
        <ioc:collect-beans by-annotation="io.nop.api.core.annotations.biz.BizModel"/>
    </property>
</bean>
```

### 属性说明

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `by-type` | class-name | 空 | 按接口/父类类型收集所有匹配的 bean |
| `by-annotation` | class-name | 空 | 按注解类型收集所有标注该注解的 bean |
| `only-concrete-classes` | boolean | false | 为 true 时忽略抽象类和接口代理 |
| `as-map` | boolean | false | 为 true 时返回 Map（key=bean id），为 false 时返回 List |
| `name-prefix` | string | 空 | 按 bean id 前缀过滤 |
| `include-tag` | word-set | 空 | 只收集包含指定 tag 的 bean |
| `exclude-tag` | word-set | 空 | 排除包含指定 tag 的 bean |
| `ioc:ignore-depends` | boolean | false | 为 true 时忽略依赖检查（用于打破循环依赖） |

### 排序规则

当 `as-map="false"`（默认）时，收集结果按每个 bean 定义上的 **`ioc:sort-order`** 属性升序排序，默认值为 `100`。

```xml
<bean id="myFirstInitializer" class="..." ioc:sort-order="90"/>
<bean id="mySecondInitializer" class="..."/>  <!-- 默认 100 -->
<!-- 收集结果：myFirstInitializer(90) → mySecondInitializer(100) -->
```

当 `as-map="true"` 时，结果为 `LinkedHashMap`，按 bean 注册顺序排列（不排序）。

### 仓库中的典型用法

```xml
<!-- 收集所有 BizModel（biz-defaults.beans.xml） -->
<ioc:collect-beans by-annotation="io.nop.api.core.annotations.biz.BizModel"
                   ioc:ignore-depends="true" only-concrete-classes="true"/>

<!-- 收集所有 ORM 拦截器（orm-defaults.beans.xml） -->
<ioc:collect-beans by-type="io.nop.orm.IOrmDaoListener" ioc:ignore-depends="true"/>

<!-- 收集所有工具执行器（ai-toolkit-defaults.beans.xml） -->
<ioc:collect-beans by-type="io.nop.ai.toolkit.api.IToolExecutor"/>
```

### `name-prefix` 收集与 optional 依赖（禁类签名引用）

- **SPI 多实现收集优先 `name-prefix`（纯字符串匹配 bean id，不加载类），而非 `by-type`**——`by-type` 需 `loadBeanClass` 加载类型；当实现类依赖 optional 依赖时，classpath 缺失会让收集（及容器反射 bean 全部 declared methods）抛 `NoClassDefFoundError`。
- **Maven `<optional>` 依赖的模块代码禁止在类签名（方法参数/返回/字段类型）中引用该依赖类型**——`BeanDefinitionBuilder.initFactoryBeans` 反射 bean 类全部方法，签名中的任何缺失类型都会在**类加载/反射期**崩溃（启动 `Failed to start` + 栈底 `Class.getDeclaredMethods0`），与运行时是否使用无关。
- 可选实现（如 Redis store）放独立 beans.xml + 条件激活（`ioc:if`），消费者只依赖接口 + `name-prefix` 前缀约定（平台先例：`nopJobInvoker_`（`03-modules/nop-job.md`）、`nopCodeRuleVariable_`（`03-runbooks/generate-business-code.md`））。
- 验证门：凡标 optional/可选的新功能，Exit Criteria 必须含"去掉该依赖时应用仍可启动"（复现类加载场景）。

### `ioc:lazy-property` 与 `bean-init-self-wait`

- **`ioc:lazy-property` 属性在 bean 创建阶段不可用**：懒加载属性首次访问时才解析；`@PostConstruct` 内若触发事务/使用该依赖（如 `DataInitInitializer` 开事务），属性未注入 → NPE。
- **`bean-init-self-wait`**：bean 初始化同线程重入自等待（如 sessionFactory ↔ sequenceGenerator 相互依赖）。断环手段：`ioc:lazy-property` 延迟一方，或 `ioc:ignore-depends` 显式忽略依赖检查。诊断：启动日志含 `bean-init-self-wait` 且指向相互引用的两个 bean。

## 模型装载与退役代际并发契约

平台模型装载与共享池资源退役的并发行为契约（实现锚点见 `../04-reference/source-anchors.md` `RETIRE-001`/`RETIRE-002`/`LOCK-001`/`LOCK-002`；选型与语义权威 = AI 开发记录区 concurrency 设计集 kernel-retirement-generation-and-model-load-locking.md，按边界规则此处不直接链接；本节为使用方口径）。

### 模型循环依赖：类型化收敛（不再死锁/SOE）

- 模型装载统一不变式：**监视器内只做同 key 去重 + 占位，装载体在监视器外**（`ResourceCacheEntry` 装载状态机 + `LoadWaitGraph` 等待图环检测；XPL 标签编译层同不变式单飞）。
- 循环依赖三形态全部类型化报错，无永久死锁、无 StackOverflowError 路径：同线程重入环 `nop.err.core.component.resource-cache.load-reentrant-loop`；跨线程等待环 `...load-circular-wait`；检测竞态漏检由有界等待兜底 `...load-wait-timeout`（携带等待链快照）。三码均携带 `resourcePath`。
- **xdef 循环引用错误迁移（对外可见变化）**：xdef 循环引用由旧错误码 `ref-not-allow-circular-reference` 收敛为 `load-reentrant-loop`——旧码依赖的装载器重入路径已消除、不再触达；迁移先例 `TestXDefRef.testLoop`。对循环引用做错误码断言的测试/工具需按新码对齐。
- 配置键 `nop.core.component.resource-cache.load-wait-timeout`（默认 60s；`≤0` = 不设上界档）。
- 装载失败占位复位不驻留：失败后下次访问重新装载（与缓存失效门语义共存，见 `../04-reference/source-anchors.md` `CACHE-001`）。

### 退役代际：旧代资源延迟关闭（grace 契约）

- 共享池/句柄/缓存类资源（`RoundRobinSupplier` 连接池、VFS zip 句柄、模型缓存条目）的替换点**只发布新代、不内联关闭旧代**：旧代登记进 `ResourceRetirementQueue` 延迟退役；已交付在用资源自登记起存活 ≥ 宽限期。
- 配置键 `nop.commons.concurrent.retirement.grace-period`（默认 5s；`≤0` = 关闭档，退化为同步立即关闭的现行语义）。
- 配置刷新链（`refreshConfig` → resize 缩容）：被裁剪的共享连接不再被热关，取用窗口无瞬时命令失败尖峰。Lettuce 使用面契约：`getConnection`/`getAsyncCommands`/`getSyncCommands` 返回的共享连接**调用方不得 close**（显式 close 不走 autoReconnect、直接杀死共享连接；pubsub 为独立建连、调用方拥有、豁免此契约）。
- VFS 热刷新（DevTool refresh）：重建发布新 store 后旧 zip 句柄延迟关闭，在飞读者宽限期内完成读取。

## 插件生命周期与配置源生命周期并发契约

插件双轨（VFS `*.plugin.xml` 轨 / uber-jar 轨）生命周期锁面、状态机失败落定与恢复入口，以及配置源（jdbc/file）的构造序与关闭互斥契约（实现锚点见 `../04-reference/source-anchors.md` `PLG-010`/`PLG-011`/`PLG-012`/`CFG-002`；选型与语义权威 = AI 开发记录区 concurrency 设计集 plugin-lifecycle-and-config-source-hardening.md，按边界规则此处不直接链接；本节为使用方口径）。

### 插件生命周期锁契约（双锁单向序）

- 两把锁、单向获取：manager 编排锁（`reconcileLock`，所有 `loadPlugin/unloadPlugin/activatePlugin/deactivatePlugin/reconcile/reloadPlugin` 全程持有）→ 单插件生命周期锁（`lifecycleLock`，`load/unload/activate/deactivate/start/stop/updateConfig` 的状态迁移全程持有）。两轨对齐：jar 轨 `AbstractPlugin` 与 VFS 轨同形入锁、生命周期字段同为 volatile。
- **硬规则**：持有 `lifecycleLock` 期间不触发 `reconcile`。`updateConfig` 的变更回调（`onConfigChanged` = manager reconcile）一律在最外层临界区退出后派发；`start(config)` 路径的回调因此落在 activate **之后**（含 activate 抛错路径仍派发）——reconcile 见到的是终态而非激活中的插件。
- **用户代码契约**：插件 activator、effect dispose、bean destroy-method 内**不得**调用 `IPluginManager` 生命周期 API 或 `reconcilePlugins`（这些用户代码在 `lifecycleLock` 内执行，回调 manager 会与编排锁构成反向获取）。
- 消费侧复合读（`getScope`/`getService`/命令路由）统一为「取引用 → 复核状态」双检，deactivate 翻转窗内一律得到 `nop.err.plugin.inactive`（不再出现 NOT_STARTED/ISE 混型）；双检不承诺在途请求排空（quiescence 非本契约范围）。

### 插件状态机：失败落定与恢复入口

- 六态不变（`UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED`）；`ACTIVATING/DEACTIVATING` 只在临界区内存在，任何失败路径（effect dispose 抛错、bean destroy 抛错、activator 抛错、rollback 抛错、`CoreInitialization.destroy` 抛错）在退出临界区前落定终态——activate 失败落 `FAILED`，**deactivate 失败亦落 `FAILED`（不回退 `ACTIVATED`：半停容器不可信）**，`lastActivationError` 记因。
- `FAILED` 态语义扩展：定义上 scope/container 引用**可能非 null**（失败路径保留原引用供诊断与恢复；成功路径清理后置空）。读到 `FAILED` 且字段非 null = 残留半停容器待恢复。
- **恢复入口** `PluginManagerImpl.forceUnload(pluginId)`（实现类公共方法，**不在** `IPluginManager` 接口上）：受理前置态 `{ACTIVATING, DEACTIVATING, FAILED, LOADED}` → 尽力逐段清理残留 scope/container → `UNLOADED` → manager 侧 holder 移除/索引清理/classLoader 关闭/尾部 reconcile；对 `ACTIVATED` 抛类型化异常（须先走 `deactivatePlugin` 正常通道，不强杀健康容器）；`UNLOADED`/不存在幂等返回。既有 `unload/activate` 守卫零改动，`forceUnload` 是中间态唯一合法出口。
- jar 轨加载失败（坏包：缺 `plugin.json`、类加载失败、配置拉取失败）在 holder 建立前即确定性释放 `PluginClassLoader` 与 jar 句柄（重试不累积）；holder 建立后的 `unloadPlugin` 失败保留 classLoader 可重试（两类失败终态语义不同）。

### 配置源（jdbc/file）构造序与关闭契约

- **先加载成功再调度**：`JdbcConfigSource`/`AbstractFileConfigSource`（含 `KeyFileConfigSource`/`PropsFileConfigSource`）构造器先同步执行初始 `loadConfig`，成功后才注册周期刷新任务（调度槽 `globalTimer` + 任务体 `executeOn(globalWorker)` 卸载形态不变）。初始加载失败 → 构造抛错且**零残留周期任务**；jdbc 变体同时关闭已建连接。
- **close 与刷新互斥**：`JdbcConfigSource.close()` 与刷新任务体/变更回调同 monitor 串行。停机时若有在途刷新，`close` 等待该轮完成（上界 = 一轮 SQL[受 `queryTimeout` 约束] + 用户回调时长），换取连接零泄漏；`close` 后任何重建连接路径命中 `closed` 守卫抛类型化异常（防退化守卫，正常路径不可达）。
- 变更回调 per-callback 隔离（异常不杀刷新链）与 R1-02 的卸载/超时语义不受本契约影响。

### Shell 子进程兜底（nop-shell）

- `ShellRunner.run` 的任何退出路径（正常/超时/stdin 断管/中断/收集线程抛错/调度拒绝）均保证已创建子进程被销毁（锚点 `SHELL-001`）：`destroy()` 后有界等待 2s 未退出升级 `destroyForcibly()`；stderr 读取线程随进程销毁经 EOF 退出，不再 pin 住 `cachedThreadPool` 线程。`timeout=0` 的无限等待悬挂为相邻缺陷，不在本契约范围。

## IoC 并发启动与配置发布协议并发契约

IoC 容器 `concurrent-start` 启动图的失败传导、bean 创建/初始化等待的环检出与有界化，以及配置引用（`IConfigReference`）热刷新的原子发布契约；连带 ORM 分片迁移路由热替换与写门集群语义（实现锚点见 `../04-reference/source-anchors.md` `IOC-004`/`IOC-005`/`CFG-003`/`ORM-MIG-001`；选型与语义权威 = AI 开发记录区 concurrency 设计集 ioc-startup-graph-and-config-publish-protocol.md，按边界规则此处不直接链接；本节为使用方口径）。

### 并发启动：join + 有界 + 失败传导

- `nop.ioc.concurrent-start=true` 时，app 容器启动在 `IocCoreInitializer` 启动序内**同步 join** 异步启动图（配置键 `nop.ioc.app-beans-container.start-join-timeout`，默认 60s；`≤0` = 不设上界档）。任一 bean 启动失败（含 `Error`、成功回调内抛错、无超时 pending）统一收敛为**失败终态**：在飞/孤儿启动任务取消 → 容器 stop（已建 bean 销毁）→ 失败标记 → 类型化异常抛出。
- 传导链：`IocCoreInitializer.initialize()` 抛出 → `CoreInitialization.initialize()` 原样重抛 → `NopApplication.run()` 主任务不执行、进程非零退出。standalone 宿主**无代码改动**即获得该语义；quarkus/spring 宿主的健康检查/退出码联动属宿主集成面（见 `../01-repo-map/module-groups.md` 宿主模块），不在本契约内。
- 失败后 `awaitStartFinished()` 快速失败（不再静默返回）；失败终态收敛幂等（成功回调路径已收敛的不重复 stop）。

### bean 创建/初始化等待：环检出 + 有界兜底

- 同一机制单点：bean 定义级创建等待（`getBean0` 临界区）与 `ProducedBeanInstance` 跨实例初始化等待均登记到平台等待图（与模型装载 `LoadWaitGraph` 同一载体），等待前检环——命中即抛类型化环错误（携带等待链快照与双方 beanId），不再永久死锁；检出竞态漏检由有界等待兜底（配置键 `nop.ioc.bean-init.wait-timeout`，默认 60s；`≤0` = 不设上界档，两域共用）。
- 构建期拓扑序与 `nop.ioc.allow-cycle`（默认 `true`）**维持现状**：type-injection / factoryBean / 表达式等运行期才可见的依赖边**不进入**拓扑序与 `ioc:init-order` 校验（现网可启动的配置零破坏），只在运行期等待点参与环检出。
- 60s 默认档对合法的超长 bean 构造（>60s）会以超时错误失败：这是对死锁确定性暴露的有意取舍；此类场景调大键值或设 `≤0` 逃生档。同线程递归 `getBean` 的可重入与自依赖 fail-fast 行为不变。

### 配置引用热刷新：单一不可变 holder

- `IConfigReference` 的 `location + provider` 收拢为一个不可变 holder，刷新链（`DefaultConfigProvider` 配置源变更 / `updateConfigValue` / `assignConfigValue` / `reset`）以**单次 volatile 发布**替换整代：读侧要么见全旧代、要么见全新代，不再出现「新 location + 旧 provider」撕裂快照，刷新后 `get()` 立即可见。
- `IConfigReference` 接口签名零变更；`defaultValue` 为独立字段（启动期写定、只读），`get()` 在 provider 值为 `null` 时回落 `defaultValue` 的语义不变。
- 下游消费（JDBC/文件配置源刷新链、ORM 分片路由 `shardSelector` 热替换）依赖本发布协议获得可见性；`SessionFactoryImpl.shardSelector` 为 volatile 发布，`rebuildRouting` 读回确认失配即记 `gateFailed`。

### ORM 迁移写门：租约 fail-open（集群语义）

- 写门 `OrmMigrationWriteGate` 的 closed 态经配置源传播到各实例；每个实例**自观察到 closed 起计租约**（配置键 `nop.orm.migration.write-gate-lease-seconds`，默认 1800s；`≤0` = 关闭租约档即现行永久关门语义）。租约到期该实例自动 fail-open（恢复写）并 **WARN 留痕**——到期是显式运维信号，不是静默恢复。配置 true→false 时租约清零，再次 closed 重新起租。
- **残余风险（显式接受）**：若租约在 VERIFY 中段到期而迁移未完成，写直达旧分片路由（双写窗）。硬条款：租约默认值必须 ≥ 迁移全程预期时长；收到到期 WARN 须人工介入（abort 或延长）。
- 编排器 `runVerify`/`confirmSwitch` 在关键窗口复核门态：窗口内门被翻开（含 fail-open）→ 迁移状态行记 `gateFailed` + 类型化失败，不静默继续。多实例操作顺序：先在全部实例观察到 closed（配置源刷新到位）再进入 VERIFY；`completeSwitch`/`abort` 开门经配置源传播。


## 平台并发原语纪律（plan 2547 R4-13）

本平台并发原语（`nop-commons` 的锁 / 调度 / 池 / 缓存）遵循以下纪律；触及这些原语时须遵守。

- **复合操作必须单原子域**：`ConcurrentHashMap` 的「读-判-改」多步操作一律改 `compute`/`computeIfAbsent`，禁止裸 `get` + 后续 `put/remove`（`RateLimitExecutorImpl.debounce` 修复为此形态）。**但 `computeIfAbsent` 内不得做重活**（编译 / 类加载 / 大对象构建）——需要时用 `CompletableFuture` 占位：`computeIfAbsent` 只放占位，构建在锁外完成后 `complete`/`completeExceptionally`。**判定「是否本次新建」必须用 `boolean[]`/`AtomicBoolean`**——`computeIfAbsent` 的返回值区分不出既有与新建。
- **中断语义透传**：`IResourceLock` 家族的 `lockInterruptibly()` / `tryLock(time,unit)` 在等待中被中断时抛 `InterruptedException`（不是 `NopException`）。底层 `tryLockWithLease` 保持不抛受检异常（源兼容），中断信息以 `InterruptedException` 作为 cause 保留，由上层识别重抛。
- **回调不得在监视器内执行**：`CycleSynchronizer` 的回调列表在锁内**快照**、锁外逐个执行，且每个回调独立 `try/catch`（一个失败不中断其余、不改写 promise 终态）。周期代号（generation）保证旧周期的延迟收尾不会清掉新周期状态。
- **线程池 `destroy()` 语义**：`waitForTasksToCompleteOnShutdown` 与 `awaitTerminationSeconds` 已生效——**仅当二者同时满足（true && >0）**才「停止接收 → 有界等待 → 超时强制终止」；否则保持 `shutdown()`（不等待、不中断在飞任务）。未 `init()` 就 `destroy()` 抛 `nop.err.commons.concurrent.thread-pool-not-initialized`（不再裸 NPE）。
  - **注意**：`GlobalExecutors.register()` 会 destroy 旧池；若该池配置了有界等待，注册热更新路径可能阻塞——配置热更新场景请避免在该池上开启 `waitForTasksToCompleteOnShutdown`。
- **拒绝与丢弃必须显式**：`SequentialTaskExecutor.execute` 在队列满时抛 `RejectedExecutionException` 并 WARN（不静默丢任务）；`AbstractBatchProcessService.destroy` 先置 `accepting=false`、等消费循环把队列 drain 空、再 cancel；超时残量逐条记录丢弃清单后告警。**绕过 `AbstractBatchProcessService.send()` 直调 `getQueue().send()` 的生产方不在 drain 契约覆盖内。**
- **`execute` 路径的上下文卫生**：`DefaultThreadPoolExecutor.execute` 在任务结束后 `detachContext()`，防止池线程上的 `IContext` 跨任务驻留。**不清中断标志**（周期任务守卫依赖中断传递）。`DefaultScheduledExecutor` / `BindScheduledExecutor` 的 `execute` 不包装——其命令由 `PeriodicTaskHelper` 绝缘守卫管理，外层包装会让拒绝回调重复触发。
- **异步缓存 executor**：`CacheConfig.executor` 可指定异步加载/刷新所用 executor；缺省 `null` 沿用 Caffeine 的 `ForkJoinPool.commonPool()`。I/O 型 async 缓存建议显式指定独立池。
- **`IAsyncCache.getAllAsync` 契约（按实现分叉，非普遍承诺）**：未命中 key 的行为取决于实现与配置——
  `LocalCache` + `async=true`：**整体失败**（Caffeine 拒 `k->null`，抛 `NullMapCompletionException`，不加载、无部分结果）；
  `LocalCache` + `async=false` + **有 loader**：**加载缺失 key**并返回完整 map；
  `LocalCache` 无 loader / `MapCache` / `ISyncCache` 的默认实现（如 `EhcacheCache`/`HazelcastCache`）：**仅返回已缓存条目**（缺席 map）。
  需要「miss 即加载」用 `computeIfAbsentAsync`/`getAsync`；只要已缓存条目且不希望触发任何加载，**优先用 `getAllPresent`**（语义最明确）。

## 配置值加密 `@sec:`

配置文件中的静态敏感值（数据库密码、第三方密钥等）可用 `@sec:` 前缀加密，启动期自动解密。

### 机制

- 配置值以 `@sec:` 前缀标记（常量 `CommonConstants.SEC_VALUE_PREFIX`，`nop-commons/.../CommonConstants.java:33`）。
- `ConfigStarter`（`nop-config/.../starter/ConfigStarter.java:449`）装配 `DefaultConfigValueEnhancer` + `AESTextCipher`（`:473`）。
- `DefaultConfigValueEnhancer.doEnhance()`（`nop-config/.../enhancer/DefaultConfigValueEnhancer.java:67`）检测到 `@sec:` 前缀时，用 `AESTextCipher.decrypt()` 解密后续密文。
- 密文格式 `v1:{base64(iv || ciphertext+tag)}`（AES-256-GCM + PBKDF2-SHA256），由 `AESTextCipher`（`nop-commons/.../crypto/impl/AESTextCipher.java`）处理。

### 加密密钥配置

| 配置项 | 说明 |
|--------|------|
| `nop.config.encrypt-key` | AES 主密钥（passphrase） |
| `nop.config.encrypt-salt-key` | 盐值 |
| `nop.config.encrypt-concat-iv` | IV 拼接方式 |

> **生产部署前置条件**：`@sec:` 加密必须配置 enc-key（`nop.config.encrypt-key` 或
> `nop.crypt.default-enc-key`，建议同时配置 salt）。encKey 为空时框架不阻断（兼容
> 开发/测试模式），但派生出的密钥可被任何持有源码的人公开推导，不具保密性，且首次
> 派生时会输出一次性 WARN（`nop.crypt.empty-enc-key`）。带 `enc` 标签的 ORM 列加密
> （`nopOrmColumnBinderEnhancer`）同理——默认装配不注入密钥，生产部署必须显式配置。

### 用法

在 yaml/properties 中写：

```yaml
nop:
  integration:
    feishu:
      app-secret: "@sec:v1:..."   # 加密后的密文
```

注入侧（Java bean）正常用 `@cfg:` 注入，`DefaultConfigValueEnhancer` 在配置加载期已解密为明文。消费样例见 `nop-integration-feishu/.../client/FeishuCredentials.java`（Javadoc 说明 `@sec:` → `DefaultConfigValueEnhancer` + `AESTextCipher`，`appSecret`/`encryptKey` 字段可承载 `@sec:` 密文）。

### `@sec:` vs 加密凭证库（nop-credential）

| | `@sec:`（本节） | nop-credential 凭证库 |
|---|---|---|
| **服务对象** | 配置文件静态值（启动期一次解密） | DB 行级动态数据（运行期写入/读取） |
| **管理能力** | 无（仅配置值替换） | 类型注册 / 连通性测试 / 密钥轮换 / 引用计数 / 脱敏展示 |
| **典型场景** | `oss.secret-key`、`feishu.appSecret` 等配置项 | `NopAiModel.apiKey`、数据源密码等 DB 列 |

> 判断规则：密钥写在**配置文件**里、不随业务运行期变化 → 用 `@sec:`；密钥存在 **DB 业务行**里、需要运行期管理与轮换 → 用 nop-credential 凭证库（`ICredentialProvider`）。详见 `../03-modules/nop-credential.md`。

## 不要默认传播的模式

1. Spring `@Value`
2. Spring `@Aspect/@Around` 样式的 AOP 示例
3. `@Inject private Foo foo;`

## 相关文档

- `./value-resolvers.md` — `@cfg:` / `@i18n:` 等 `@`-prefix value resolver 在 YAML/JSON/XML DSL 加载阶段的通用机制
- `./testing.md`
- `./debugging-and-diagnostics.md`
- `../04-reference/source-anchors.md`
