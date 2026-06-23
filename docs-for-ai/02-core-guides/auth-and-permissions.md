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

## 操作权限

### 概述

操作权限由 `nopActionAuthChecker` 控制，**默认关闭**（`nop.auth.enable-action-auth=false`）。

启用后，系统会检查用户角色是否拥有对应菜单/功能点的权限。菜单也会根据角色过滤，未授权的资源被标记为 DISABLED。

### action-auth.xml 结构

菜单和操作权限通过 `*.action-auth.xml` 文件声明为静态配置树。

**资源类型**：

| resourceType | 含义 | 用途 |
|-------------|------|------|
| `TOPM` | 一级菜单 | 侧边栏分组，显示为顶级菜单项 |
| `SUBM` | 子菜单 | 页面入口，对应一个后台管理页面 |
| `FNPT` | 功能点 | 细粒度权限（如查询、修改），挂载在 SUBM 下 |

**典型结构**：

```xml
<auth x:schema="/nop/schema/action-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <site id="main">
    <resource id="my-module" displayName="我的模块" icon="shield"
              resourceType="TOPM" orderNo="10000" routePath="/my-module"
              component="layouts/default/index">
      <children>
        <resource id="NopAuthUser-main" displayName="用户管理" icon="user-round"
                  component="AMIS" resourceType="SUBM"
                  url="/nop/auth/pages/NopAuthUser/main.page.yaml">
          <children>
            <resource id="FNPT:NopAuthUser:query" displayName="查询用户"
                      resourceType="FNPT">
              <permissions>NopAuthUser:query</permissions>
            </resource>
            <resource id="FNPT:NopAuthUser:mutation" displayName="修改用户"
                      resourceType="FNPT">
              <permissions>NopAuthUser:mutation</permissions>
            </resource>
          </children>
        </resource>
      </children>
    </resource>
  </site>
</auth>
```

**文件命名约定**：

| 文件 | 来源 | 可否手改 |
|------|------|---------|
| `_{moduleName}.action-auth.xml` | 代码生成 | 禁止 |
| `{moduleName}.action-auth.xml` | 手写/Delta 定制 | 允许，通过 `x:extends` 继承生成文件 |
| `app.action-auth.xml` | 应用层聚合入口 | 允许，通过 `x:extends` 继承本模块手写文件 |

> 运行时并非按模块扫描所有 `*.action-auth.xml`，而是只加载 `nop.auth.site-map.static-config-path` 指向的**单一文件**（默认 `/nop/main/auth/app.action-auth.xml`，各 app 在自己 `application.yaml` 中改写为 `/{moduleId}/auth/app.action-auth.xml`）。多模块的菜单聚合，是靠这一个聚合文件通过 `x:extends` 链合并各模块手写文件达成的，而非框架自动扫描。

### 菜单资源生成链路

ORM 模型通过 codegen 自动生成菜单资源：

1. `model/*.orm.xml` 中的实体 `ext:icon` → 生成为 SUBM 的 `icon` 属性
2. 根 `<orm ext:icon>` → 生成为 TOPM 的 `icon` 属性
3. 每个实体自动生成两个 FNPT：`{objName}:query` 和 `{objName}:mutation`
4. 生成的 `_*.action-auth.xml` 文件可被手写文件通过 `x:extends` 继承和扩展

### 删除测试菜单与新增业务菜单

codegen 生成的 `_*.action-auth.xml` 会带一个测试用的 TOPM 根（id 形如 `test-orm-{moduleName}`），下面挂着所有实体的 SUBM。正式业务菜单不会沿用这个结构，标准做法是在手写层 `{moduleName}.action-auth.xml`（`x:extends="_*.action-auth.xml"`）里：

1. 用 `<resource id="test-orm-{moduleName}" x:override="remove"/>` 删除测试 TOPM（连同其下自动生成的 SUBM）。
2. 新增真正的业务 TOPM（如「采购管理」），在它的 `<children>` 下重新组织 SUBM。SUBM 的 `id` 可以引用生成层已有的（如 `ErpPurOrder-main`）以继承其 `url`，也可以完全自定义。
3. 不需要的菜单项（如明细行 Line 实体）直接不列出，即从菜单隐藏（页面本身仍可通过头部单据下钻访问）。

```xml
<auth x:extends="_erp-pur.action-auth.xml" ...>
  <site id="main">
    <resource id="test-orm-erp-pur" x:override="remove"/>
    <resource id="erp-pur" displayName="采购管理" icon="shopping-cart" resourceType="TOPM" ...>
      <children>
        <resource id="erp-pur-sourcing" displayName="采购寻源" icon="search" resourceType="SUBM" ...>
          <children>
            <resource id="ErpPurRfq-main" displayName="采购询价单" icon="message-circle-question"
                      component="AMIS" resourceType="SUBM"
                      url="/erp/pur/pages/ErpPurRfq/main.page.yaml"/>
          </children>
        </resource>
      </children>
    </resource>
  </site>
</auth>
```

### 操作权限检查流程

1. GraphQL 引擎对每个请求调用 `GraphQLActionAuthChecker.check()`
2. 读取字段定义上的 `ActionAuthMeta`（包含 `publicAccess`、`roles`、`permissions`）
3. 如果需要权限检查，调用 `DefaultActionAuthChecker.isPermitted(permission, context)`
4. 检查逻辑：`permissionToRoles` 映射表 → 当前用户是否拥有对应角色
5. `admin` 和 `nop-admin` 角色默认跳过所有操作权限检查（`nop.auth.skip-check-for-admin=true`）

### 关键配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.auth.enable-action-auth` | `false` | 启用后菜单和操作权限检查生效 |
| `nop.auth.skip-check-for-admin` | `true` | admin/nop-admin 角色跳过操作权限检查 |
| `nop.auth.site-map.static-config-path` | `/nop/main/auth/app.action-auth.xml` | site-map 加载的唯一 action-auth 文件。**改了菜单没生效，头号排查点**：确认改的是这个配置指向的文件。各 app 在 `application.yaml` 改写为 `/{moduleId}/auth/app.action-auth.xml`。 |
| `nop.auth.site-map.cache-timeout` | `10m` | site-map 合并结果缓存时长。调试时可调小（如 `1s`）以便频繁看到变更。 |
| `nop.auth.site-map.support-debug` | `false` | 仅写入返回给前端的 `SiteMapBean.supportDebug` 字段，控制前端调试行为。**与 `_dump` 输出无关**——dump 只看 `nop.debug=true`。 |

### site-map 加载时机与调试 dump

- **加载时机**：`SiteMapProviderImpl` 用 `IResourceLoadingCache`（按 locale、可租户隔离），合并 `static-config-path` 指向的 action-auth.xml 与数据库动态配置。debug 模式下（`nop.debug=true`）`@PostConstruct init()` 会主动触发一次预加载，及早暴露 action-auth.xml 合并问题；非 debug 模式为懒加载，首次调用 `getSiteMap` 等方法时才合并。
- **调试 dump**：debug 模式下合并完成后，最终菜单树输出到 `_dump/nop-app/nop/main/site/{locale}-menu.yaml`（如 `zh-CN-menu.yaml`、`en-menu.yaml`）。**注意文件名是 `*-menu.yaml`，不是 `action-auth.xml`**——`DslModelParser` 解析 action-auth.xml 的过程不单独 dump，只 dump 合并后的最终菜单树。改了菜单后想看运行时实际结构，看这个文件。
- 前端拉取菜单：通过 `SiteMapApiBizModel` 暴露的 GraphQL/REST 接口（`LoginApi__login` 登录后调用）。

## 数据权限

### 概述

数据权限通过 `nopDataAuthChecker` 自动附加到查询条件中。`CrudBizModel#prepareFindPageQuery` 会在查询预处理阶段追加数据权限过滤。

### data-auth.xml 结构

数据权限规则通过 `*.data-auth.xml` 文件声明：

```xml
<data-auth x:schema="/nop/schema/data-auth.xdef">
  <objs>
    <obj name="NopAuthUser">
      <role-auths>
        <!-- admin 角色：无过滤 -->
        <role-auth id="admin" roleIds="nop-admin"/>

        <!-- 普通用户：只能看到同租户数据 -->
        <role-auth id="default" roleIds="user">
          <filter>
            <eq name="tenantId" value="${$context.tenantId}"/>
          </filter>
        </role-auth>
      </role-auths>
    </obj>
  </objs>
</data-auth>
```

**关键概念**：

| 属性 | 说明 |
|------|------|
| `obj name` | 匹配 bizObj 名称（通常是实体短名） |
| `role-auth.roleIds` | CSV 角色集合，用户拥有其中任一角色时匹配此规则 |
| `role-auth.priority` | 优先级，数值越高越先检查（默认 100） |
| `role-auth.filter` | XPL 节点，生成 QueryBean 过滤条件 |
| `role-auth.check` | XPL 断言，用于单实体访问检查 |
| `role-auth.when` | XPL 断言，动态判断规则是否适用 |

### 数据权限应用点

1. **查询过滤**：`CrudBizModel.prepareFindPageQuery()` → `AuthHelper.appendFilter()` → 自动追加 SQL 过滤条件
2. **实体检查**：`CrudBizModel.checkDataAuth()` → `dataAuthChecker.isPermitted()` → 单条记录访问控制
3. **SQL 级过滤**：`DataAuthEntityFilterProvider` 在 ORM 层注入数据权限条件

### 默认行为

| 场景 | 行为 |
|------|------|
| bizObj 无任何规则 | `isPermitted` 返回 `true`，`getFilter` 返回 `null`（无限制） |
| 有规则但用户角色不匹配 | `isPermitted` 返回 `false`（拒绝），`getFilter` 抛异常 |

### 数据库驱动的数据权限

默认关闭（`nop.auth.use-data-auth-table=false`）。启用后，`NopAuthRoleDataAuth` 表中的规则与静态 XML 规则合并。

### 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.auth.use-data-auth-table` | `false` | 是否启用数据库表驱动的数据权限 |
| `nop.auth.data-auth-config-path` | `/nop/main/auth/app.data-auth.xml` | 静态数据权限配置路径 |

## 平台默认权限实体

| 实体 | 表 | 用途 |
|------|---|------|
| NopAuthUser | `nop_auth_user` | 用户账户 |
| NopAuthRole | `nop_auth_role` | 角色（支持复合角色 childRoleIds） |
| NopAuthUserRole | `nop_auth_user_role` | 用户-角色映射 |
| NopAuthResource | `nop_auth_resource` | 菜单/功能点资源 |
| NopAuthRoleResource | `nop_auth_role_resource` | 角色-资源映射 |
| NopAuthRoleDataAuth | `nop_auth_role_data_auth` | 角色-数据权限规则 |
| NopAuthDept | `nop_auth_dept` | 部门层级 |
| NopAuthTenant | `nop_auth_tenant` | 多租户 |

内置角色：`admin`（管理员）、`nop-admin`（系统超级管理员）、`user`（普通用户，菜单过滤时始终放行）。

## 源码锚点

| 组件 | 路径 |
|------|------|
| 认证过滤器 | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml` (bean `nopAuthHttpServerFilter`) |
| 认证配置 | 同上 (bean `nopAuthFilterConfig`) |
| 配置类 | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java` |
| 过滤器实现 | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java` |
| 操作权限检查器 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultActionAuthChecker.java` |
| 数据权限检查器 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultDataAuthChecker.java` |
| SiteMap 提供者 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteMapProviderImpl.java` |
| GraphQL 权限检查 | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLActionAuthChecker.java` |
| action-auth schema | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/action-auth.xdef` |
| data-auth schema | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/data-auth.xdef` |
| 资源类型字典 | `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/resource-type.dict.yaml` |
| ORM codegen 模板 | `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/src/main/resources/_vfs/{moduleId}/auth/_{moduleName}.action-auth.xml.xgen` |

## 相关文档

- `debugging-and-diagnostics.md` — 调试时跳过认证的方法
- `service-layer.md` — 数据权限在查询中的体现
- `ioc-and-config.md` — IoC bean 覆盖与配置注入
- `model-first-development.md` — 菜单图标生成链路
