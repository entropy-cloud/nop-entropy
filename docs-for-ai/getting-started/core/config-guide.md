# 配置管理指南

## 核心概念

Nop平台使用灵活的配置管理系统，支持从多种来源加载配置。配置文件**只支持YAML格式**，通过`@InjectValue`注解注入配置值到Bean中。

## 配置加载机制

### 配置文件名称

Nop平台支持以下固定名称的配置文件：

- **`bootstrap.yaml`** - 启动配置，最先加载
- **`application.yaml`** - 应用主配置，默认位置为 `classpath:application.yaml`
- **`application-{profile}.yaml`** - 环境配置，例如 `application-dev.yaml`、`application-prod.yaml`
- **`nop.config.yaml`** - Nop平台特定配置

**重要说明**：
- 配置文件**必须使用 `.yaml` 或 `.yml` 格式**，优先使用 `.yaml`
- 不存在 `app.yaml` 这样的文件名，需要使用 `application.yaml`
- 支持通过 `nop.config.location` 参数指定自定义配置文件路径
- 支持通过 `nop.config.bootstrap-location` 参数指定自定义启动配置路径
- 支持通过 `nop.config.additional-location` 参数指定额外配置文件（会覆盖主配置）

### 配置加载顺序

配置按以下顺序加载，**先加载的优先级更高**（会覆盖后加载的配置）：

1. **System.getenv()** - 环境变量
2. **System.getProperties()** - Java系统属性（`-D`参数）
3. **bootstrap.yaml** - 启动配置文件（可通过 `nop.config.bootstrap-location` 指定）
4. **配置中心** - `{nop.application.name}-{nop.profile}.yaml`
5. **配置中心** - `{nop.application.name}.yaml`
6. **配置中心** - `{nop.product.name}.yaml`
7. **K8s SecretMap** - 通过 `nop.config.key-config-source.paths` 指定
8. **K8s ConfigMap** - 通过 `nop.config.props-config-source.paths` 指定
9. **数据库配置表** - 如果配置了 `nop.config.jdbc.jdbc-url` 等参数
10. **扩展配置文件** - 通过 `nop.config.additional-location` 指定
11. **主配置文件** - `application.yaml`（可通过 `nop.config.location` 指定）
12. **Profile配置文件** - `application-{profile}.yaml`，优先级高于 `application.yaml`
13. **Profile前缀配置项** - 例如 `%dev.a.b.c` 会覆盖 `a.b.c`

### Profile配置

Profile用于在不同环境使用不同配置。

**激活Profile**：

```bash
# 通过系统属性
java -Dnop.profile=dev -jar app.jar

# 通过环境变量
export NOP_PROFILE=dev
java -jar app.jar

# 通过配置文件
nop:
  profile: dev
```

**配置文件加载顺序**：

如果配置了 `nop.profile=dev` 和 `nop.profile.parent=mysql,nacos`，则配置文件加载顺序为：

```
application-dev.yaml -> application-mysql.yaml -> application-nacos.yaml -> application.yaml
```

**注意**：先加载的配置优先级更高，会覆盖后面的配置。

### 环境变量配置

可以通过环境变量传递配置，环境变量名会按照以下规则转换为配置键：

- `.` → `_`
- `-` → `__`
- `_` → `___`

例如：
- 配置键 `nop.auth.sso.server-url`
- 对应环境变量 `NOP_AUTH_SSO_SERVER__URL`

**示例**：

```yaml
# application.yaml
database:
  password: ${DB_PASSWORD:default_password}
```

```bash
# 通过环境变量传递
export DB_PASSWORD=my_secret_password
```

## @InjectValue 注解使用

### 基本语法

`@InjectValue` 注解用于将配置值注入到Bean字段或方法参数中。

```java
import io.nop.api.core.annotations.ioc.InjectValue;

public class MyService {

    // 注入简单配置值
    @InjectValue("@cfg:my.app.name")
    protected String appName;

    // 注入整数值
    @InjectValue("@cfg:my.app.timeout")
    protected int timeout;

    // 注入布尔值
    @InjectValue("@cfg:my.app.enabled")
    protected boolean enabled;

    // 注入配置值，带默认值
    @InjectValue("@cfg:my.app.max-size|100")
    protected int maxSize;
}
```

**重要**：`@Inject` 不能用于 `private` 字段，建议使用 `protected` 或包私有字段，或使用 setter 注入。

### 配置值语法

#### 1. 基本引用

```java
@InjectValue("@cfg:nop.config.bean.base-path")
protected String basePath;
```

#### 2. 默认值语法

```java
@InjectValue("@cfg:my.api.timeout|30")
protected int timeout; // 如果配置不存在，默认值为30
```

#### 3. 环境变量引用

```java
@InjectValue("@env:APP_ENV")
protected String env;
```

#### 4. 系统属性引用

```java
@InjectValue("@sys:user.home")
protected String userHome;
```

#### 5. 组合语法

可以组合使用多个来源，按顺序查找，找到即返回：

```java
@InjectValue("@cfg:my.config|@env:MY_CONFIG|default_value")
protected String myConfig;
```

查找顺序：配置 → 环境变量 → 默认值

### 支持的数据类型

```java
// 字符串
@InjectValue("@cfg:app.name")
protected String appName;

// 数值类型
@InjectValue("@cfg:app.timeout")
protected int timeout;

@InjectValue("@cfg:app.ratio")
protected double ratio;

@InjectValue("@cfg:app.price")
protected BigDecimal price;

// 布尔类型
@InjectValue("@cfg:app.debug")
protected boolean debug;

// 支持的真值：true, 1, yes, on
// 支持的假值：false, 0, no, off

// 数组类型
@InjectValue("@cfg:app.allowed.origins")
protected String[] allowedOrigins;

// List类型
@InjectValue("@cfg:app.import.packages")
protected List<String> importPackages;

// Map类型
@InjectValue("@cfg:app.custom.props")
protected Map<String, String> customProps;
```

## 配置文件示例

### application.yaml

```yaml
# 应用基本信息
app:
  name: MyNopApp
  version: 1.0.0

# 服务器配置
server:
  port: 8080
  host: 0.0.0.0

# 数据库配置
nop:
  datasource:
    jdbc-url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: ${DB_PASSWORD:default_password}
    pool:
      max-size: 10
      min-idle: 2

# 日志配置
log:
  level: INFO
  file: /var/log/app.log

# 缓存配置
cache:
  enabled: true
  ttl: 3600
  max-size: 1000
```

### application-dev.yaml（开发环境）

```yaml
nop:
  datasource:
    jdbc-url: jdbc:mysql://localhost:3306/devdb

log:
  level: DEBUG
```

### application-prod.yaml（生产环境）

```yaml
nop:
  datasource:
    jdbc-url: jdbc:mysql://prod-host:3306/proddb

log:
  level: WARN
```

## 动态配置

### 配置监听

监听配置变化：

```java
import io.nop.config.IConfigProvider;
import io.nop.config.IConfigValueChangeListener;
import io.nop.api.core.annotations.ioc.Inject;

@Inject
protected IConfigProvider configProvider;

public void setupConfigListener() {
    configProvider.addChangeListener("my.app.timeout",
        new IConfigValueChangeListener() {
            @Override
            public void onConfigChanged(String key, Object oldValue, Object newValue) {
                log.info("Config changed: {} = {} -> {}", key, oldValue, newValue);
                // 处理配置变化
            }
        });
}
```

### IConfigReference（动态配置引用）

```java
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;

// 创建配置引用
static final IConfigReference<Boolean> CFG_USE_CACHE =
    AppConfig.varRef("global.use_cache", true);

public void myFunc() {
    if (CFG_USE_CACHE.get()) {
        // 使用缓存的逻辑
    }
}
```

当配置值变化时，`CFG_USE_CACHE.get()` 会自动返回最新值。

### 热更新配置

某些配置支持运行时热更新，无需重启应用：

```java
// 获取当前配置
Object currentValue = configProvider.getConfigValue("my.app.timeout");

// 更新配置（如果配置中心支持）
configProvider.updateConfig("my.app.timeout", 60);
```

## 常见配置参数

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| `nop.profile` | 激活的Profile | - |
| `nop.profile.parent` | 父Profile（多个用逗号分隔） | - |
| `nop.application.name` | 应用名称 | - |
| `nop.product.name` | 产品名称 | - |
| `nop.config.location` | 主配置文件路径 | `classpath:application.yaml` |
| `nop.config.bootstrap-location` | 启动配置文件路径 | `classpath:bootstrap.yaml` |
| `nop.config.additional-location` | 额外配置文件路径 | - |
| `nop.config.enabled` | 是否启用配置中心 | true |
| `nop.debug` | 启用调试功能，输出更多日志 | false |
| `nop.orm.init-database-schema` | 空库时自动创建表 | false |
| `nop.auth.login.allow-create-default-user` | 用户表为空时自动创建默认账户 | false |

## 最佳实践

### 1. 使用@cfg前缀

```java
// ✅ 推荐
@InjectValue("@cfg:app.timeout")
protected int timeout;

// ❌ 不推荐
@InjectValue("app.timeout")
protected int timeout;
```

### 2. 提供合理的默认值

```java
// ✅ 推荐
@InjectValue("@cfg:app.timeout|30")
protected int timeout;

// ❌ 不推荐
@InjectValue("@cfg:app.timeout")
protected int timeout;
```

### 3. 配置键命名规范

```yaml
# ✅ 推荐：层级化
app:
  name: MyApp
  timeout: 30
  max-size: 100

# ❌ 不推荐：扁平化
app.name: MyApp
app.timeout: 30
app.max-size: 100
```

### 4. 敏感信息使用环境变量

```yaml
# ✅ 推荐
database:
  password: ${DB_PASSWORD:}

# ❌ 不推荐
database:
  password: "123456"
```

### 5. 使用配置验证

```java
import jakarta.annotation.PostConstruct;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.ErrorCode;

public class MyService {

    @InjectValue("@cfg:app.timeout")
    protected int timeout;

    @PostConstruct
    public void validateConfig() {
        if (timeout <= 0 || timeout > 300) {
            throw new NopException(ErrorCode.define("err_invalid_config"))
                .param("key", "app.timeout")
                .param("value", timeout)
                .description("timeout must be between 1 and 300 seconds");
        }
    }
}
```

## 相关文档

- [IoC容器指南](./ioc-guide.md)
- [错误处理指南](./exception-guide.md)
- [事务管理指南](./transaction-guide.md)
- [官方配置文档](../../../../dev-guide/config.md)
