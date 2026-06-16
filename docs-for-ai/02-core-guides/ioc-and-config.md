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
| 3 | 环境变量 | `NOP_DATASOURCE_JDBC_URL=...` |
| 4 | **系统属性 `-D`** | `-Dnop.datasource.jdbc-url=...` |
| 5 | `bootstrap.yaml` | 启动期基础配置 |
| 6 | 扩展配置加载器 | SPI `IConfigSourceLoader` |
| 7 | `application.yaml` + `application-{profile}.yaml` | 应用主配置 |

**关键规则**：`-D` 系统属性可以覆盖 `application.yaml` 中的一切值，包括 `%dev.xxx` 等 profile 专有值。

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
| 注入配置值 | `@InjectValue("@cfg:key|default")` |
| 需要动态测试配置 | `@NopTestConfig(testConfigFile = ...)` |

## 模块与 bean 发现规则

1. Nop 应用侧 bean 发现默认是基于文件，不是基于 Java classpath scanning。
2. 启用模块通过 `/{moduleId}/_module` 被发现（零字节标记文件）。详见 `vfs-and-resource-resolution.md` 的"模块发现与注册"章节。
3. app 容器自动加载的通常是 `/{moduleId}/beans/app.beans.xml` 和 `app-*.beans.xml`。
4. 每个模块可选提供 `/{moduleId}/app.module.yaml`，包含 `version`、`displayName`、`description`、`dependsOn` 等元数据。详见 `vfs-and-resource-resolution.md` 的 `app.module.yaml` 章节。
5. `/nop/autoconfig/*.beans` 和 `nop.ioc.app-beans.files` 也可以补充 bean 文件。
6. `_service.beans.xml` 这类生成文件通常是被 `app-service.beans.xml` 导入，而不是自己被自动发现。
7. **所有以 `_` 开头的文件都是 codegen 管线自动生成的，不允许手动修改**。包括但不限于 `_service.beans.xml`、`_dao.beans.xml`、`_app.orm.xml`、`_gen/*.java` 等。如需定制 IoC 注册，修改对应的非下划线文件（如 `app-service.beans.xml`）。如需添加新 BizModel 但 `codegen` 尚未生成，在 `app-service.beans.xml` 中手动添加 bean 定义。

## 不要默认传播的模式

1. Spring `@Value`
2. Spring `@Aspect/@Around` 样式的 AOP 示例
3. `@Inject private Foo foo;`

## 相关文档

- `./testing.md`
- `./debugging-and-diagnostics.md`
- `../04-reference/source-anchors.md`
