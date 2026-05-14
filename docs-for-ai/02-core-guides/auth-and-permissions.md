# 认证与权限控制

Nop 平台的认证和权限控制分为三层：HTTP 路径认证、操作权限、数据权限。

## 默认结论

1. HTTP 路径认证由 `nopAuthFilterConfig` bean 控制，默认静态资源（js/css/html 等）全部公开。
2. 操作权限由 `nopActionAuthChecker` 控制，默认关闭（`nop.auth.enable-action-auth=false`）。
3. 数据权限由 `nopDataAuthChecker` 控制，自动附加到查询条件中。
4. 开发调试时可用 `-Dnop.auth.service-public=true` 跳过认证。

## HTTP 路径认证

### 判定逻辑

`AuthFilterConfig#isPublicPath(path)` 的判定顺序：

1. 匹配 `publicPaths` → 公开
2. 匹配 `loginUrl` → 公开
3. 匹配 `authPaths` → 需要认证
4. 都没匹配 → 取 `defaultPublic` 值（默认 `true`）

**关键**：`defaultPublic=true` 意味着所有不在 `authPaths` 中的路径都公开访问。这是为了让静态资源（前端 js/css/html/images 等）无需登录即可加载。

### 路径模式

| 模式 | 含义 | 认证要求 |
|------|------|---------|
| `/graphql*` | GraphQL 端点 | 需认证 |
| `/r/*` | REST API（BizModel 方法） | 需认证 |
| `/p/*` | 指定 contentType 的内容 | 需认证 |
| `/f/*` | 文件上传下载 | 需认证 |
| `/jsonrpc` | JSON-RPC 调用 | 需认证 |
| `/px/*` | 分布式服务代理 | 需认证 |
| `/r/LoginApi_*` | 登录相关 API | 公开 |
| `/q/health*`、`/q/metrics*` | 健康检查、指标 | 公开 |
| 其他所有路径 | 静态资源 | 公开 |

模式使用 `*` 通配符（`StringHelper.matchSimplePattern`），不是正则。

### 新增公开路径

在 Delta 层覆盖 `nopAuthFilterConfig` bean：

```xml
<!-- _delta/default/nop/auth/beans/auth-service.beans.xml -->
<beans>
    <bean id="nopAuthFilterConfig" class="io.nop.auth.core.filter.AuthFilterConfig">
        <property name="defaultPublic" value="true"/>
        <property name="publicPaths">
            <list>
                <value>/r/LoginApi_*</value>
                <value>/q/health*</value>
                <value>/q/metrics*</value>
                <value>/r/MyPublicApi_*</value>
            </list>
        </property>
        <property name="authPaths">
            <list>
                <value>/graphql*</value>
                <value>/r/*</value>
                <value>/p/*</value>
                <value>/f/*</value>
                <value>/jsonrpc</value>
                <value>/px/*</value>
            </list>
        </property>
        <property name="servicePaths">
            <list>
                <value>/graphql*</value>
                <value>/r/*</value>
                <value>/p/*</value>
                <value>/f/*</value>
                <value>/jsonrpc</value>
                <value>/px/*</value>
            </list>
        </property>
        <property name="loginUrl" value="@cfg:nop.auth.login-url|/index.html#/login"/>
        <property name="logoutUrl" value="/r/LoginApi__logout"/>
        <property name="servicePublic" value="@cfg:nop.auth.service-public|false"/>
    </bean>
</beans>
```

### 新增前端静态资源目录

前端静态资源放在 `META-INF/resources/` 下，路径不在 `authPaths` 中，自动按 `defaultPublic=true` 公开。**不需要为新的前端目录额外配置匿名访问权限**。

## 开发调试配置

### 跳过登录认证

启动参数：`-Dnop.auth.service-public=true`

`servicePublic=true` 时，未登录访问服务路径会自动创建 `sys` 用户上下文，不返回 401。

### 完全移除认证

从 pom.xml 移除 `nop-auth-web` 和 `nop-auth-service` 依赖，完全去掉认证过滤器。但同时失去用户管理和登录页。

### 关键配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.auth.http-server-filter.enabled` | `true` | 是否启用认证过滤器 |
| `nop.auth.service-public` | `false` | 服务路径是否免认证 |
| `nop.auth.login-url` | `/index.html#/login` | 登录页 URL |
| `nop.auth.enable-action-auth` | `false` | 是否启用操作权限检查 |
| `nop.auth.graphql.enable-audit` | `false` | 是否启用 GraphQL 审计日志 |
| `nop.auth.auto-refresh-token` | `true` | 是否自动刷新 token |
| `nop.auth.quarkus-dev-public` | (未设置) | 开发模式下公开 `/q/*` 路径 |

## 数据权限

数据权限通过 `nopDataAuthChecker` 自动附加到查询条件中。`CrudBizModel#prepareFindPageQuery` 会在查询预处理阶段追加数据权限过滤。

详见 `service-layer.md` 中查询预处理相关内容。

## 源码锚点

| 组件 | 路径 |
|------|------|
| 认证过滤器 | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml` (bean `nopAuthHttpServerFilter`) |
| 认证配置 | 同上 (bean `nopAuthFilterConfig`) |
| 配置类 | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java` |
| 过滤器实现 | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java` |

## 相关文档

- `debugging-and-diagnostics.md` — 调试时跳过认证的方法
- `service-layer.md` — 数据权限在查询中的体现
- `ioc-and-config.md` — IoC bean 覆盖与配置注入
