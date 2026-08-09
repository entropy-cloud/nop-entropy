# nop-datav 权限/分享/导出设计 (D3)

> Status: **final**（D3-1/D3-4 部分；D3-2 分享、D3-3 导出由后继 plan 产出）
> Last Reviewed: 2026-08-10

## 概述

nop-datav 的权限体系**全程复用 nop-auth 既有机制**（roadmap §Framework reuse 硬约束），不自建权限/审计实体。本文档覆盖 D3-1（看板权限：action 级 RBAC + 行级数据权限 RLS）和 D3-4（操作审计日志）的最终设计决策。

- **D3-1 Action 级 RBAC**：自定义 GraphQL action 显式 `@Auth(permissions=...)`；CRUD action 沿用平台 `ReflectionBizModelBuilder` 自动生成的默认权限串。通过 `action-auth.xml` 暴露权限点资源并绑定默认角色，`enable-action-auth=true` 后生效。
- **D3-1 行级数据权限（RLS）**：在 `data-auth.xml` 为 `NopDatavDashboard` 配置 owner 行级规则（admin 无 filter；user 按 `createdBy` = 当前用户 OR 已发布过滤）。`DataAuthEntityFilterProvider` / `CrudBizModel` 自动注入。
- **D3-4 操作审计日志**：纯配置启用 `nop.auth.graphql.enable-audit=true` + audit-mutation-patterns，`GraphQLAuditLogger` 自动记录到 `NopAuthOpLog`。零业务代码。

## D3-1 Action 级 RBAC

### 权限模型

| 层 | 机制 | 来源 |
|----|------|------|
| 权限声明 | `@Auth(permissions="{BizObj}:{action}")` 注解在 **impl 方法** | `io.nop.api.core.annotations.directive.Auth` |
| CRUD 默认权限 | `ReflectionBizModelBuilder` 自动生成 `{bizObj}:{opType}` | 平台内置，无需注解 |
| 权限点→资源 | `action-auth.xml` 中 `<resource><permissions>` | `auth-gen:DefaultActionAuthPostExtends` 生成物 + 手写层补充 |
| 资源→角色 | `action-auth.xml` 中 `roles=` 属性（静态）/ `NopAuthRoleResource` 表（动态运行时分配） | 管理员经平台管理面分配 |
| 运行时校验 | `GraphQLActionAuthChecker.check` → `IActionAuthChecker.isPermissionSetSatisfied` → `SiteMapProvider.isPermitted` | `enable-action-auth=true` 时 `GraphQLEngine` 自动注入 checker |

### 裁定：`@Auth` 注解策略（方案 A）

自定义 action 显式声明 `@Auth(permissions="{BizObj}:{action}")`。注解加在 **BizModel impl 方法**上（如 `NopDatavDashboardBizModel.publishDashboard`），**不加在接口方法上**——`@Auth` 是 Java 方法级注解，不从接口继承（Java 语言规范 §9.6.4），`ReflectionBizModelBuilder` 从 impl 类方法读注解。

拒绝方案 B（仅 `@Auth`、不碰 action-auth.xml 角色绑定）：`SiteCacheData.isPermitted` 对未注册的 permission→roles 返回 `false`（deny-by-default），若不在 action-auth.xml 补角色绑定，加 `@Auth` + 开 `enable-action-auth` 会导致所有 datav action 对所有用户拒绝。

### 自定义 action 权限点 → 建议默认角色对照表

| BizObj | Action | 权限串 | 类型 | 默认角色 | 备注 |
|--------|--------|--------|------|----------|------|
| NopDatavDashboard | publishDashboard | `NopDatavDashboard:publishDashboard` | mutation | admin | 发布看板，管理员操作 |
| NopDatavDashboard | getPublishedDashboard | `NopDatavDashboard:getPublishedDashboard` | query | admin,user | 查看已发布看板 |
| NopDatavDashboard | rollbackDashboard | `NopDatavDashboard:rollbackDashboard` | mutation | admin | 回滚看板，管理员操作 |
| NopDatavDashboard | resolveFilterValues | `NopDatavDashboard:resolveFilterValues` | query | admin,user | 解析筛选值 |
| NopDatavDashboard | parseFilterFromUrl | `NopDatavDashboard:parseFilterFromUrl` | query | admin,user | URL 筛选解析 |
| NopDatavPanel | getPanelData | `NopDatavPanel:getPanelData` | query | admin,user | 查询面板数据 |
| NopDatavPanel | refreshPanel | `NopDatavPanel:refreshPanel` | mutation | admin,user | 刷新面板数据 |
| NopDatavPanel | resolveLinkage | `NopDatavPanel:resolveLinkage` | query | admin,user | 图表联动 |
| NopDatavPanel | resolveJump | `NopDatavPanel:resolveJump` | query | admin,user | 图表跳转 |
| NopDatavFilterState | saveFilterState | `NopDatavFilterState:saveFilterState` | mutation | admin,user | 保存筛选状态（按 userName 隔离） |
| NopDatavFilterState | getFilterState | `NopDatavFilterState:getFilterState` | query | admin,user | 获取筛选状态（按 userName 隔离） |

CRUD action（findPage/findList/get/save/update/delete）沿用平台默认权限 `{BizObj}:query` / `{BizObj}:mutation`，已在 `_nop-datav.action-auth.xml` 生成物中注册，无需额外注解。

### 自定义 action 行级校验清单（是否经 requireEntity → checkDataAuth）

| BizObj | Action | 调用 requireEntity | 行级校验来源 | 裁定 |
|--------|--------|-------------------|-------------|------|
| NopDatavDashboard | publishDashboard | ✅ `requireEntity(id, "publishDashboard", ctx)` | Dashboard 实体 checkDataAuth | 已覆盖，无需补 |
| NopDatavDashboard | getPublishedDashboard | ✅ `requireEntity(id, "getPublishedDashboard", ctx)` | Dashboard 实体 checkDataAuth | 已覆盖 |
| NopDatavDashboard | rollbackDashboard | ✅ `requireEntity(id, "rollbackDashboard", ctx)` | Dashboard 实体 checkDataAuth | 已覆盖 |
| NopDatavDashboard | resolveFilterValues | ✅ `requireEntity(id, "resolveFilterValues", ctx)` | Dashboard 实体 checkDataAuth | 已覆盖 |
| NopDatavDashboard | parseFilterFromUrl | ✅ `requireEntity(id, "parseFilterFromUrl", ctx)` | Dashboard 实体 checkDataAuth | 已覆盖 |
| NopDatavPanel | getPanelData | ✅ `requireEntity(id, "getPanelData", ctx)` | Panel 实体 checkDataAuth | 已覆盖 |
| NopDatavPanel | refreshPanel | ✅ `requireEntity(id, "refreshPanel", ctx)` | Panel 实体 checkDataAuth | 已覆盖 |
| NopDatavPanel | resolveLinkage | ✅ `requireEntity(id, "resolveLinkage", ctx)` | Panel 实体 checkDataAuth | 已覆盖 |
| NopDatavPanel | resolveJump | ✅ `requireEntity(id, "resolveJump", ctx)` | Panel 实体 checkDataAuth | 已覆盖 |
| NopDatavFilterState | saveFilterState | ❌ 直接用 DAO 按 userName + dashboardId 查询 | userName 隔离（FilterState.userName = 当前用户） | 不经实体级 checkDataAuth，靠 userName 隔离收口 |
| NopDatavFilterState | getFilterState | ❌ 直接用 DAO 按 userName + dashboardId 查询 | userName 隔离 | 同上；返回 null（无保存记录）是合法分支，非 fail-closed |

**裁定**：所有 Dashboard/Panel 自定义 action 已通过 `requireEntity` → `CrudBizModel.getEntity` → `checkDataAuth(action, entity, ctx)` 覆盖行级校验，无需补显式 `checkDataAuth`。FilterState 通过 `userName` 列隔离（仅查询/更新自己的记录），不依赖实体级 RLS。

### 原生 SQL 路径与 RLS 覆盖

`publishDashboard`/`rollbackDashboard` 内部使用 `IJdbcTemplate.executeUpdate(SQL.begin()...end())` 原生 SQL 更新主表。**`DataAuthEntityFilterProvider` 仅覆盖带 `<filter>` SyntaxMarker 的编译型 SQL，不覆盖原生 SQL**。但这些 action 前置已调用 `requireEntity` → `checkDataAuth`（实体级校验），故操作主体已通过权限校验。原生 update 不构成绕过：保护来自上游 `requireEntity`→`checkDataAuth`。

## D3-1 行级数据权限（RLS）

### Owner 标识

复用标准审计列 `createdBy`（存创建者 userName）。**不新增 ORM 权限列**（与 Non-Goals「不改 ORM 实体结构」一致）。`createdBy` 的值来自 `resolveOperator(context)`（= `context.getUserContext().getUserName()`），与 owner 匹配表达式一致。

### owner 匹配表达式

```xml
<or>
    <eq name="createdBy" value="${$context.userName}"/>
    <eq name="publishStatus" value="10"/>
</or>
```

- `$context` 是平台全局变量（`CoreConstants.GLOBAL_VAR_CONTEXT = "$context"`），在 data-auth filter 的 XPL 上下文中绑定到 `ContextProvider.currentContext()` 返回的 `IContext`。`$context` 是 XPL 编译期已注册的全局变量，故 filter 表达式编译通过。
- `IContext.getUserName()` 存在；在生产环境中由 HTTP auth filter 从 JWT token 填充。
- `$userContext`（`VAR_USER_CONTEXT = "userContext"`）在 `DefaultDataAuthChecker.newEvalScope` 运行期绑定 `IUserContext`，但非 XPL 全局变量，filter 表达式编译期不可用。
- `createdBy` 列的值由 `NopDatavOperatorResolver.resolveOperator` 写入（优先 `context.getUserContext().getUserName()`），与 `$context.userName` 在生产请求链路中一致（均来自同一身份源）。
- `publishStatus = 10` = `PUBLISH_STATUS_PUBLISHED`（已发布看板对登录用户可见）。

### 行级语义裁定：(b) owner + 已发布

选择 **(b)** `createdBy = 当前用户 OR publishStatus = PUBLISHED`。

理由：
- (a) 仅 owner 过于严格——已发布看板对普通用户不可见，不符合 BI 场景（看板发布后应可被查看）。
- (b) owner + 已发布符合 DataEase/Superset 等参考实现的「编辑者可见 + 已发布共享」语义。
- (c) owner + 同租户已排除：nop-datav ORM 无 `tenantId` 列、无 `useTenant`，需 ORM 变更，与 Non-Goals 冲突。

### fail-closed 角色覆盖

`DefaultDataAuthChecker.getFilter`（:195-213）一旦某 bizObj 有任意 role-auth 规则，未命中任何规则的用户抛 `ERR_AUTH_NO_DATA_AUTH`（fail-closed）。故须覆盖所有需访问角色：

| 角色 | 规则 | 说明 |
|------|------|------|
| admin | 无 filter（全见） | 管理员全量可见 |
| user | `createdBy = $context.userName OR publishStatus = 10` | owner + 已发布 |

`nop-admin` 角色：nop-datav 上下文无单独 nop-admin 规则（与 nop-auth.data-auth.xml 的 NopAuthUser 按 `nop-admin` 规则不同）。若部署环境使用 `nop-admin` 角色，经 `NopAuthRoleDataAuth` DB 驱动规则或管理员运行时配置增补。

### admin 旁路裁定

`nop.auth.skip-check-for-admin` 保持 **`false`**（默认值）。

历史（H-2）：该开关曾默认 `true` 导致管理员默认绕过权限（已修复为默认 `false`）。本 plan **不开启该开关**。admin 角色通过 data-auth.xml 的显式「无 filter」规则获得全量可见，而非通过 skip-check 旁路。

### 子资源行级裁定

| 子资源 | 是否需要独立 RLS | 理由 |
|-------|-----------------|------|
| NopDatavPanel | 否 | 通过 Panel 实体的 checkDataAuth 经 `requireEntity` 校验（Panel 归属 Dashboard） |
| NopDatavDashboardTab | 否 | 纯 CRUD，经 Dashboard 归属隐式覆盖 |
| NopDatavDatasetRef | 否 | 纯 CRUD，经 Dashboard 归属隐式覆盖 |
| NopDatavDashboardSnapshot | 否 | 纯 CRUD + 经 Dashboard `publishDashboard`/`getPublishedDashboard` 入口校验 |
| NopDatavFilterState | 否 | 已有 `userName` 列隔离（仅查/改自己的记录） |

### DB 驱动规则

启用 `nop.auth.use-data-auth-table=true`，允许运行时经 `NopAuthRoleDataAuth` 管理面增配行级规则（与静态 data-auth.xml 合并，`DefaultDataAuthChecker.loadDataAuthModel` :97-100）。灵活性最高。

## D3-4 操作审计日志

### 审计范围

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `nop.auth.graphql.enable-audit` | `true` | 启用 GraphQL 审计 |
| `nop.auth.graphql.audit-mutation-patterns` | `NopDatavDashboard__*,NopDatavPanel__*,NopDatavFilterState__*` | 所有 datav mutation 操作 |
| `nop.auth.graphql.audit-query-patterns` | `NopDatavDashboard__getPublishedDashboard` | 关键查询（已发布看板查看） |

pattern 使用 `StringHelper.matchSimplePatternSet`（glob `*` 匹配）。GraphQL operation name 格式为 `{BizObj}__{action}`（如 `NopDatavDashboard__publishDashboard`）。

### 异步批量落库

`AuditServiceImpl extends AbstractBatchProcessService`：`saveAudit` 入队 → 后台线程批量 flush → `NopAuthOpLog.batchSaveEntities`。

测试须处理异步延迟：注入 `IAuditService`（`AuditServiceImpl` 实例），poll `isAllProcessed()`（`queue.size()==0 && processingCount==0`）至 true 带超时，**不用 `Thread.sleep` 盲等**。

### 生产路径零业务代码

D3-4 为纯配置启用：`GraphQLAuditLogger`（`IGraphQLLogger`）→ `IAuditService`（`AuditServiceImpl`）→ `NopAuthOpLog`。bean 已在 `auth-service.beans.xml` 注册（条件 bean `enable-audit`）。nop-datav 无任何审计业务代码。

## 拒绝的替代方案

| 替代方案 | 拒绝理由 |
|----------|----------|
| 自建权限实体（`NopDatavDashboardRole`/`NopDatavPermission`/`NopDatavAuditLog`） | roadmap §Framework reuse 硬约束：「权限/角色用户/行级数据权限/操作日志 → 复用 nop-auth，不自建权限体系」 |
| owner 加 ORM 列（`owner`/`accessLevel`） | 复用既有 `createdBy` 即可满足 owner 标识，新增列违反 Non-Goals「不改 ORM 实体结构」 |
| per-user per-dashboard ACL 实体 | 本 plan 的用户级由平台 RBAC（用户→角色→资源）+ owner 行级覆盖；针对单看板的显式用户授权归 D3-2 分享 plan |
| 启用 `skip-check-for-admin=true` | H-2 历史已修复为默认 false，admin 通过显式 data-auth 规则获全量可见，非 skip 旁路 |

## 测试策略

### 鉴权测试基建

nop-datav-service 测试需 `nopActionAuthChecker`/`nopDataAuthChecker`/`nopGraphQLLogger`/`nopAuditService` bean（均位于 `nop-auth-service`），故在 nop-datav-service 的 `pom.xml` 添加 `<scope>test</scope>` 依赖 `nop-auth-service`。

测试配置（`testConfigFile`）：
- `nop.auth.site-map.static-config-path: /nop/datav/auth/app.action-auth.xml`
- `nop.auth.data-auth-config-path: /nop/datav/auth/app.data-auth.xml`

### 测试矩阵

| 测试 | @NopTestConfig | 验证点 |
|------|----------------|--------|
| RBAC 正向 | `enableActionAuth=TRUE` | 有权角色（admin）可调用 action |
| RBAC 负向 | `enableActionAuth=TRUE` | 无权角色被拒（`ERR_AUTH_NO_PERMISSION`） |
| RLS wiring sanity | `enableDataAuth=TRUE` | `context.getDataAuthChecker() != null`（首个 enableDataAuth 测试须证明 checker 注入） |
| RLS owner 过滤 | `enableDataAuth=TRUE` | user A 创建的看板对 user B 不可见（findPage 按 owner 过滤） |
| RLS admin 全见 | `enableDataAuth=TRUE` | admin 可见全部 |
| RLS fail-closed | `enableDataAuth=TRUE` | 未配置规则的角色被拒（`ERR_AUTH_NO_DATA_AUTH`） |
| Audit 成功 | `enable-audit=true` | mutation 触发后 `NopAuthOpLog` 含记录（poll `isAllProcessed()`） |
| Audit 失败 | `enable-audit=true` | 失败操作亦被记录（errorCode 非空） |
| 联合 E2E | 全部启用 | RBAC 拒绝 + RLS 过滤 + audit 落库同上下文验证 |

测试 fixture **不得**用 `skip-check-for-admin=true` 短路。角色→资源映射经 action-auth.xml 的 `roles=` 静态属性提供（无需 seed `NopAuthRoleResource` 表）。

## resolveOperator 去重

`NopDatavDashboardBizModel` 与 `NopDatavFilterStateBizModel` 各有一份相同的私有 `resolveOperator(IServiceContext)` 方法。抽到共享 helper `NopDatavOperatorResolver.resolveOperator(IServiceContext)`（`io.nop.datav.service` 包），保持单一事实。
