# nop-datav 权限/分享/导出设计 (D3)

> Status: **final**（D3-1/D3-4/D3-2 部分；D3-3 导出由后继 plan 产出）
> Last Reviewed: 2026-08-10

## 概述

nop-datav 的权限体系**全程复用 nop-auth 既有机制**（roadmap §Framework reuse 硬约束），不自建权限/审计实体。分享能力（D3-2）为 nop-datav 专属实体（参考 AJ-Report `report_share` / Metabase embedding），不复用通用机制（平台无通用分享链接机制）。本文档覆盖 D3-1（看板权限：action 级 RBAC + 行级数据权限 RLS）、D3-4（操作审计日志）、D3-2（公共分享链接）的最终设计决策。

- **D3-1 Action 级 RBAC**：自定义 GraphQL action 显式 `@Auth(permissions=...)`；CRUD action 沿用平台 `ReflectionBizModelBuilder` 自动生成的默认权限串。通过 `action-auth.xml` 暴露权限点资源并绑定默认角色，`enable-action-auth=true` 后生效。
- **D3-1 行级数据权限（RLS）**：在 `data-auth.xml` 为 `NopDatavDashboard` 配置 owner 行级规则（admin 无 filter；user 按 `createdBy` = 当前用户 OR 已发布过滤）。`DataAuthEntityFilterProvider` / `CrudBizModel` 自动注入。
- **D3-4 操作审计日志**：纯配置启用 `nop.auth.graphql.enable-audit=true` + audit-mutation-patterns，`GraphQLAuditLogger` 自动记录到 `NopAuthOpLog`。零业务代码。
- **D3-2 公共分享链接**：新建 `NopDatavDashboardShare` 独立实体（一行一分享链接：token / 密码哈希 / 有效期 / 启用标记）。管理侧 action（create/list/revoke/toggle）经 D3-1 的 `@Auth` + Dashboard owner 校验收口；公共访问 action `getSharedDashboard(shareToken, password)` 用 `@Auth(publicAccess=true)` 匿名放行，校验 token+密码+有效期+启用后**直接经 DAO 读 `NopDatavDashboardSnapshot`**（不经 RLS/`requireEntity`）返回已发布快照。

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

## D3-2 公共分享链接

### 分享存储方案裁定：独立实体（A）

新建 `NopDatavDashboardShare` 独立实体（一行一分享链接）。理由：(1) 一个看板可有多个分享链接（不同密码/有效期/用途）；(2) 吊销单条不影响其他；(3) 与 AJ-Report `report_share` 模式一致；(4) 支持 list/delete/toggle 管理语义最直接。

拒绝方案 B（Dashboard 新增 `shareConfig` JSON 列存分享数组）：单看板多链接时管理/吊销/唯一令牌约束复杂，JSON 不利索引与唯一性。

### 实体列约定（行为规格）

`NopDatavDashboardShare`（表 `nop_datav_share`）：

- `shareId`(PK, VARCHAR, tagSet `seq`)
- `shareToken`(唯一键 VARCHAR，`StringHelper.generateUUID()` 生成不可枚举随机串)
- `dashboardId`(FK + 索引，关联 `NopDatavDashboard`)
- `passwordHash`(可空 VARCHAR，BCrypt 哈希非明文，单列无需 salt——见密码哈希策略)
- `expireTime`(可空 TIMESTAMP，null=永不过期)
- `enabled`(domain `boolFlag`，TINYINT，默认 true——与既有 `delFlag` 同 domain 约定)
- 标准审计列（`createdBy`/`createTime`/`updatedBy`/`updateTime`/`version`/`delFlag`/`remark`）

### 令牌生成策略

使用 `io.nop.commons.util.StringHelper.generateUUID()`（UUID 无连字符的 32 位 hex 随机串）生成不可枚举随机令牌，**不复用顺序 ID 作令牌**（防枚举）。

### 密码哈希策略

采用 bean `nopPasswordEncoder`（`CompositePasswordEncoder`：SHA256 预哈希 + BCrypt 外层，`encodePassword`=`BCrypt(SHA256(password))`，`passwordMatches` 对称流转）。BCrypt 外层自带盐（`generateSalt()` 返回 null，盐嵌在哈希串内），**单 `passwordHash` 列即可，无需独立 salt 列**。

- 密码可选：`passwordHash = null` 表示无需密码；存储仅哈希，校验经 `IPasswordEncoder.passwordMatches` 比对，非明文。
- **分享密码绕过 `nopPasswordPolicy`**：该 policy（`DefaultPasswordPolicy`，`minLength=12` + 大小写/数字/特殊字符）面向用户账号强密码，不适合分享短密码。直接调 `IPasswordEncoder.encodePassword`，不经 `IUserStore`/policy 校验。
- 注入：`@Inject IPasswordEncoder`（按类型注入，bean `nopPasswordEncoder` 为 `ioc:default="true"` 的 `CompositePasswordEncoder`；`nopBCryptPasswordEncoder` 的 `autowire-candidate="false"` 故不可直接按类型注入它）。

### 吊销 vs 软删语义

- `revokeShare(shareId)` / `toggleShare(shareId, enabled)`：置 `enabled=false`（软禁用，不删行，保留审计痕迹）。
- 实体级删除走标准 `delFlag` 软删（CrudBizModel 既有 delete action）。
- 公共访问对 `enabled=false` 显式拒绝（错误码 `ERR_DATAV_SHARE_DISABLED`）。

### 公共访问 action 契约

`getSharedDashboard(shareToken, password)`（`@BizQuery` + `@Auth(publicAccess=true)`）。行为：

1. 按 `shareToken` 经 DAO 唯一键加载 share（**不调 `requireEntity`/不经 RLS**——share 实体无行级规则）。
2. 校验 `enabled`（false → `ERR_DATAV_SHARE_DISABLED`）。
3. 校验 `expireTime`（非空且 <= now → `ERR_DATAV_SHARE_EXPIRED`）。
4. 密码校验：若 `passwordHash` 非空，要求传入 `password`（空/null → `ERR_DATAV_SHARE_PASSWORD_REQUIRED`）经 `passwordEncoder.passwordMatches` 比对（不匹配 → `ERR_DATAV_SHARE_PASSWORD_MISMATCH`）；`passwordHash` 为空则忽略 password。
5. **直接经 `daoProvider().daoFor(NopDatavDashboardSnapshot.class)` 按 `dashboardId` 查询、`snapshotVersion DESC` 取首条**（不调用 `getPublishedDashboard`、不经 `requireEntity`/RLS——否则匿名用户 fail-closed 或 NPE）。无快照 → `ERR_DATAV_SNAPSHOT_NOT_FOUND`。
6. 返回快照内容（仅已发布内容，非编辑态）。

### 匿名访问 RLS 处理

公共访问 action 是 `publicAccess`，运行时无登录用户上下文——**不对 `NopDatavDashboardShare` 配置行级规则**（`data-auth.xml` 仅含 `NopDatavDashboard`，share 实体无 `<obj>` 条目，否则匿名用户 fail-closed）。

- 代码**不得**调用 `IServiceContext.getUserContext()`（`publicAccess` action 运行时为空）。
- 公共访问直接读 share（唯一键）+ 快照实体（已序列化内容，不触发 Dashboard 行级 filter）。
- 安全含义：公共访问仅返回已发布快照（非编辑态）。

### 平台 publicAccess 机制

`GraphQLActionAuthChecker.isAllowAccess` 在 `publicAccess=true` 时**先于** userContext 检查直接放行（参考既有 `LoginApiBizModel`），无需用户/角色。这是 D3-2 匿名访问 action 的放行机制。

### 管理侧权限裁定

分享管理 API（`createShare`/`listShares`/`revokeShare`/`toggleShare`）用 D3-1 的 `@Auth(permissions="NopDatavDashboardShare:{action}")` + Dashboard owner 校验：

- `createShare(dashboardId, ...)`：经 `requireEntity(dashboardId, ...)` → `checkDataAuth` 校验当前用户为该 Dashboard 的 owner/admin（复用 D3-1 RLS 链路）；非 owner → `ERR_AUTH_NO_DATA_AUTH` 或 `ERR_DATAV_NOT_DASHBOARD_OWNER`。
- `listShares(dashboardId)` / `revokeShare(shareId)` / `toggleShare(shareId, enabled)`：同样前置 Dashboard owner 校验（通过 share 的 `dashboardId` 反查 Dashboard 再校验）。
- CRUD action（findPage/get/save/update/delete）沿用平台默认权限串 + D3-1 RLS（但 share 实体本身无行级规则，保护来自管理 action 内显式 Dashboard owner 校验）。

### action-auth 生成

`NopDatavDashboardShare` xmeta **不加** `no-web` tag，使其在 `_nop-datav.action-auth.xml` 获得 `FNPT:NopDatavDashboardShare:query/mutation` 权限点资源 + 管理页（与既有 6 实体同模式）。

### 已发布前提

分享访问要求看板至少有一个已发布快照；无快照时公共访问返回 `ERR_DATAV_SNAPSHOT_NOT_FOUND`（复用既有错误码）。

## 拒绝的替代方案

| 替代方案 | 拒绝理由 |
|----------|----------|
| 自建权限实体（`NopDatavDashboardRole`/`NopDatavPermission`/`NopDatavAuditLog`） | roadmap §Framework reuse 硬约束：「权限/角色用户/行级数据权限/操作日志 → 复用 nop-auth，不自建权限体系」 |
| owner 加 ORM 列（`owner`/`accessLevel`） | 复用既有 `createdBy` 即可满足 owner 标识，新增列违反 Non-Goals「不改 ORM 实体结构」 |
| per-user per-dashboard ACL 实体 | 本 plan 的用户级由平台 RBAC（用户→角色→资源）+ owner 行级覆盖；针对单看板的显式用户授权归 D3-2 分享 plan |
| 启用 `skip-check-for-admin=true` | H-2 历史已修复为默认 false，admin 通过显式 data-auth 规则获全量可见，非 skip 旁路 |
| 分享存 Dashboard `shareConfig` JSON 列（方案 B） | 单看板多链接时管理/吊销/唯一令牌约束复杂，JSON 不利索引与唯一性；采用独立实体 `NopDatavDashboardShare`（方案 A） |
| 分享令牌 = 看板 ID | 可枚举，安全风险高；采用 `StringHelper.generateUUID()` 随机不可枚举串 |
| 分享密码明文存储 | 安全基线要求哈希；采用 `nopPasswordEncoder`（BCrypt + SHA256 复合），单 `passwordHash` 列 |
| 公共访问调用 `getPublishedDashboard` action | 该 action 内部 `requireEntity` → `checkDataAuth`，在无用户上下文/RLS 下 NPE 或 fail-closed；公共访问直接经 DAO 读 `NopDatavDashboardSnapshot` |
| 对 `NopDatavDashboardShare` 配置行级规则 | 匿名用户 fail-closed；公共访问唯一键查找无需 RLS，管理侧经 Dashboard owner 校验收口 |

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
