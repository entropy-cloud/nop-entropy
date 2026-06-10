# nop-auth — 认证与权限模块

## 功能概览

完整的用户认证与授权系统，无需自行实现登录功能。

- 用户管理（注册、登录、密码重置）
- RBAC 角色权限（用户→角色→资源）
- 菜单/按钮级权限控制
- 数据级权限（行级过滤）
- 部门/用户组/岗位体系
- 多租户支持
- SSO 单点登录、OAuth2
- 操作审计日志
- 外部登录方式（微信等）

## 默认用户

| 配置项 | 值 |
|--------|-----|
| 默认用户名 | `nop` |
| 默认密码 | `123` |
| 自动创建条件 | 用户表为空时 |
| 控制配置 | `nop.auth.login.allow-create-default-user` |

开发模式跳过登录：`-Dnop.auth.service-public=true`

## 内置角色

| 角色 | 说明 |
|------|------|
| `admin` | 管理员，跳过所有权限检查 |
| `nop-admin` | 系统超级管理员，跳过所有权限检查 |
| `user` | 普通用户，始终通过菜单过滤 |

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopAuthUser | `nop_auth_user` | 用户账号 |
| NopAuthRole | `nop_auth_role` | 角色（支持复合角色 via childRoleIds） |
| NopAuthUserRole | `nop_auth_user_role` | 用户-角色映射 |
| NopAuthResource | `nop_auth_resource` | 菜单/按钮/功能点资源树 |
| NopAuthRoleResource | `nop_auth_role_resource` | 角色-资源映射 |
| NopAuthRoleDataAuth | `nop_auth_role_data_auth` | 角色数据权限规则 |
| NopAuthSite | `nop_auth_site` | 子站点（多站点支持） |
| NopAuthDept | `nop_auth_dept` | 部门层级 |
| NopAuthGroup | `nop_auth_group` | 用户组 |
| NopAuthPosition | `nop_auth_position` | 岗位 |
| NopAuthTenant | `nop_auth_tenant` | 多租户 |
| NopAuthSession | `nop_auth_session` | 会话日志 |
| NopAuthOpLog | `nop_auth_op_log` | 操作审计日志 |
| NopAuthExtLogin | `nop_auth_ext_login` | 外部登录方式 |
| NopOAuthAuthorization | `nop_oauth_authorization` | OAuth2 授权记录 |
| NopOAuthRegisteredClient | `nop_oauth_registered_client` | OAuth2 客户端注册 |
| NopOAuthUserConsent | `nop_oauth_user_consent` | OAuth2 用户同意 |

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-auth-api` | API DTO 与接口定义 |
| `nop-auth-dao` | ORM 实体与 DAO |
| `nop-auth-service` | 业务逻辑（登录、权限校验、数据权限） |
| `nop-auth-web` | Web 层与 AMIS 页面 |
| `nop-auth-sso` | SSO 集成 |
| `nop-oauth` | OAuth2 服务端 |

## 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.auth.service-public` | `false` | `true` 时跳过登录 |
| `nop.auth.enable-action-auth` | `false` | 是否启用操作权限检查 |
| `nop.auth.login.allow-create-default-user` | `true` | 用户表为空时自动创建 nop 用户 |
| `nop.auth.defaultPublic` | `true` | 所有路径默认公开，仅 authPaths 中的需认证 |

## 认证路径规则

- 需认证路径：`/graphql*`、`/r/*`、`/p/*`、`/f/*`、`/jsonrpc`、`/px/*`
- 公开路径：`/r/LoginApi_*`、`/q/health*`、`/q/metrics*`

## 源码锚点

| 组件 | 路径 |
|------|------|
| 认证过滤器 | `nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml` |
| 操作权限检查 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultActionAuthChecker.java` |
| 数据权限检查 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultDataAuthChecker.java` |
| 站点地图 | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/sitemap/SiteMapProviderImpl.java` |
| ORM 模型 | `nop-auth/model/nop-auth.orm.xml` |

## 相关文档

- `../02-core-guides/auth-and-permissions.md`
- `../reusable-modules-overview.md`
