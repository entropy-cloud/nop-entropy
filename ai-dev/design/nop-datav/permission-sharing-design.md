# nop-datav 权限/分享/导出设计 (D3)

> Status: **final**（D3-1/D3-4/D3-2/D3-3 全部）
> Last Reviewed: 2026-08-10

## 概述

nop-datav 的权限体系**全程复用 nop-auth 既有机制**（roadmap §Framework reuse 硬约束），不自建权限/审计实体。分享能力（D3-2）为 nop-datav 专属实体（参考 AJ-Report `report_share` / Metabase embedding），不复用通用机制（平台无通用分享链接机制）。本文档覆盖 D3-1（看板权限：action 级 RBAC + 行级数据权限 RLS）、D3-4（操作审计日志）、D3-2（公共分享链接）、D3-3（数据导出）的最终设计决策。

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

## D3-3 数据导出（CSV / Excel 异步任务 + 限额）

### 概述

用户可对单个面板（或看板下全部「需数据集」面板）发起数据导出（CSV / Excel），后端经 D1 数据绑定管线（`PanelDataBinder.queryPanelData`）取数，异步生成文件、经 nop-file 落盘、按限额控制并发与行数，完成后提供 owner-only 下载。**数据导出完全复用 D1 取数管线，不重写查询**；权限完全继承 D3-1 面板/看板行级权限，不经公共分享链路。

### 实体列约定（行为规格）

`NopDatavExportTask`（表 `nop_datav_export_task`）：

- `taskId`(PK, VARCHAR, tagSet `seq`)
- `sourceType`(VARCHAR，dict `datav/export-source`：`panel` / `dashboard`)
- `sourceId`(VARCHAR，来源 panelId / dashboardId)
- `format`(VARCHAR，dict `datav/export-format`：`csv` / `xlsx`；`pdf`/`png` 在 dict 中不登记，请求时按字符串判定显式拒绝)
- `status`(int，dict `datav/export-status`：0=pending、10=running、20=succeeded、30=failed、40=cancelled)
- `params`(CLOB JSON，导出请求参数快照，供异步线程脱离请求上下文重放查询)
- `fileRecordId`(VARCHAR，可空，成功后写入 `NopFileRecord.fileId`)
- `rowCount`(LONG，可空，导出数据行数)
- `errorMsg`(VARCHAR，可空，失败原因)
- 标准审计列（`createdBy`/`createTime`/`updatedBy`/`updateTime`/`version`/`delFlag`/`remark`）

`createdBy` 复用 D3-1 owner 标识语义（= 任务发起人 userName），下载校验直接比对 `task.createdBy == 当前用户`。

### 状态机裁定

状态机：`pending(0) → running(10) → succeeded(20) | failed(30) | cancelled(40)`，单向推进，不允许回退。迁移条件与持久化时机：

| 迁移 | 触发点 | 持久化时机 |
|------|--------|-----------|
| → pending | `createExportTask` 同步 INSERT 任务（并发限额校验通过后） | 请求线程内 `saveEntityDirectly`，返回 taskId |
| pending → running | 异步执行体进入时 | 异步线程内 `runInNewSession` 开启 session 后立即更新 |
| running → succeeded | 取数 + 写出 + `IFileStore.saveFile` 全部成功 | 异步线程内写入 fileRecordId/rowCount |
| running → failed | 取数/写出/落盘任一异常，或行数超限 | 异步线程内 catch 写入 errorMsg |
| running → cancelled | `cancelExportTask` 置标志位，执行体轮询检测后中止 | cancel 请求线程 + 执行体双重写入 |

`cancelled` 是终态：cancel 请求把 DB 状态从 running→cancelled 后，执行体检测到标志位后仅写 errorMsg("cancelled by user") 不再改状态。pending 态 cancel 直接转 cancelled。

### 异步执行机制裁定（含线程模型）

- **执行器**：复用平台 `GlobalExecutors.globalWorker()`（`IThreadPoolExecutor`，daemon 线程池，不自建线程池）。经 `submit(Callable)` 提交，返回 `CompletableFuture`。
- **ORM session 获取**：异步线程内无 `IServiceContext`/请求线程绑定的 `IOrmSession`。执行体内经注入的 `IOrmTemplate`（`@Inject` 到 BizModel，闭包捕获）调 `ormTemplate.runInNewSession(session -> { ... })` 开新 session 写任务状态、调 `IFileStore.saveFile`（其内部 `dao.saveEntity` 同样依赖此 session）。
- **cancel 中断机制**：`CompletableFuture.cancel(true)` 在 `IThreadPoolExecutor` 实现下仅置完成标志（mayInterruptIfRunning 不强中断线程，见 `DefaultThreadPoolExecutor`）。执行体内主动轮询一个 per-task 的 `volatile boolean cancelled` 标志（按 taskId 缓存在 `ConcurrentHashMap<String,Boolean>`），在取数前/写出循环每批检测，命中即抛 `NopException(ERR_DATAV_EXPORT_FAILED)`("cancelled by user") 终止。
- **创建即返回**：`createExportTask` INSERT pending 任务后立即提交执行器并返回 taskId，不阻塞请求线程等结果；客户端经 `getExportTask` 轮询状态。

### 进程重启 running 任务清理裁定（含触发机制）

- **触发 bean**：`NopDatavExportTaskBizModel` 实现 `IInitializer`（`io.nop.core.initialize.IInitializer`，平台 IoC 启动后调用 `initialize()`），在初始化时扫描所有 `status=running` 的任务转 `failed`，errorMsg 记 `"interrupted by process restart"`。
- 不静默挂起：重启后无任何执行体可恢复这些任务的内存 cancel 标志，必须显式标记终态。
- pending 任务保留 pending（执行体尚未启动，重启后无执行体处理——同 running 一并转 failed，因提交的 `CompletableFuture` 已随 JVM 退出丢失）。
- 实现裁定：**重启时 status ∈ {pending, running} 的任务全部转 failed**（reason="interrupted by process restart"），无一挂起。

### 限额裁定（含行数限额的防 OOM 策略）

阈值经 `NopDatavConfigs` 配置：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.datav.export.max-rows` | `100000` | 单任务最大导出行数 |
| `nop.datav.export.max-concurrent-per-user` | `3` | 单用户并发（pending+running）任务数上限 |
| `nop.datav.export.file-max-length` | `104857600`(100MB) | `IFileStore.saveFile` 的 maxLength 上限 |

- **并发限额**：`createExportTask` 同步查询当前用户 status ∈ {pending, running} 的任务数，超过 `max-concurrent-per-user` 抛 `ERR_DATAV_EXPORT_CONCURRENCY_LIMIT`（快速失败，不排队）。
- **行数限额防 OOM 裁定（关键）**：`PanelDataBinder.queryPanelData` 当前一次性把结果全量加载到 `List<Map>`（`PanelDataBinder.java:148-155`），"取数后"校验无法防 OOM。采用 **(a) SQL LIMIT 探测 + (b) 流式写出计数** 双保险：
  - **(a) 取数阶段**：导出路径不直接调 `queryPanelData` 的全量分支，而是构造带 `LIMIT (maxRows+1)` 的探测 SQL（在 `PanelSqlBuilder.build` 产出的 SQL 外层包 `select * from ({sql}) t limit {maxRows+1}`，仅导出路径生效，不影响 `getPanelData` 运行时查询）。结果行数 > maxRows 即抛 `ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED`（防 OOM + 防文件过大，不拉全量到内存）。
  - **(b) 写出阶段**：行计数到 maxRows 即中止写出（双保险，防 (a) 被绕过如未来改取数方式）。
- 看板级导出：每个面板独立按 maxRows 校验，任一面板超限即整任务 failed（不静默截断该面板）。

### 文件存储与下载裁定（含 IFileStore 集成链路）

- **落盘链路**：执行体先写出临时 `IResource`（`InMemoryTextResource` 或 `PathResource`）→ 构造 `UploadRequestBean(inputStream, fileName, length, mimeType)`，设 `bizObjName=nopDatavExportTask`、`bizObjId=taskId` → 调 `IFileStore.saveFile(bean, NopDatavConfigs.EXPORT_FILE_MAX_LENGTH)` 返回 fileId → fileId 存入任务 `fileRecordId` 列。`nopFileStore`（`DaoResourceFileStore`，`ioc:default="true"`）随 nop-file-dao 依赖拉入。
- **下载链路**：`downloadExportFile` owner 校验通过后调 `IFileStore.getFile(fileId)→IFileRecord.getResource()`，返回 `IResource`（经 `IFileRecord.getResource()`），由上层序列化为下载流。不用 `getFileLink`（返回相对 URL，需前端拼接，不适合后端直接返回文件）。
- **owner 校验落点**：`downloadExportFile` action 内比对 `task.createdBy == NopDatavOperatorResolver.resolveOperator(context)`，非 owner 抛 `ERR_DATAV_EXPORT_NOT_OWNER`。文件记录的 `bizObjId=taskId` 与任务一致，无跨任务串用风险。

### 格式裁定

- **CSV**：`CsvResourceRecordIO.openOutput(IResource, encoding)` 产出 `IRecordOutput`，写出 headers + rows。UTF-8 + **手写 BOM**：写出前先写 `0xEF 0xBB 0xBF` 三字节（`openOutput` 的 encoding 不自动写 BOM），保证 Excel 打开 CSV 中文不乱码。mimeType=`text/csv`。
- **Excel/xlsx**：从 `{columns, rows}` 构造 `ExcelWorkbook`（单 sheet：表头行 + 数据行；看板级多面板：多 sheet，每面板一 sheet）→ `ExcelHelper.saveExcel(IResource, ExcelWorkbook)` 写出。mimeType=`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`。
- **columns+rows → ExcelWorkbook 映射**：`ExcelWorkbook wb = new ExcelWorkbook(); ExcelSheet sheet = new ExcelSheet(); sheet.setName(panelName); ExcelTable table = sheet.getTable();` 表头行 `ExcelRow` 每个 column 一个 `ExcelCell(value=columnName)`；数据行每行一个 `ExcelRow`，按 columns 顺序取 `row.get(col)` 设 cell value；`table.addRow(...)` 累加；`wb.getSheets().add(sheet)`。
- 看板级导出：xlsx 多 sheet（每面板一 sheet，sheet 名=面板 displayName）；csv 单文件仅支持单面板，看板级 csv 为每面板独立 taskId（看板级导出强制 xlsx，csv 请求看板级导出抛 `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`）。

### 看板级导出裁定（含面板遍历与 needsDataset 判定）

- 按 `dashboardId` 经 DAO `QueryBean(filter=eq dashboardId)` 查 panel 列表。
- 逐个经 `PanelTypeMapping.toComponentType(panel.getPanelType())` + `PanelComponentRegistry.requireComponent(ct).getMetadata().isNeedsDataset()` 过滤——仅导出 `needsDataset=true` 的面板（chart/table/metric/pivot_table/map），跳过 text/iframe/container。
- 无 needsDataset 面板时抛 `ERR_DATAV_PANEL_NOT_FOUND`("no exportable panels in dashboard")（不静默返回空文件）。

### 权限边界裁定

- **请求线程（createExportTask / cancelExportTask）**：对来源 panel/dashboard 经 `requireEntity` → `checkDataAuth` 继承 D3-1 RLS。panel 来源：`NopDatavPanelBizModel` 链路的 `requireEntity`；dashboard 来源：`NopDatavDashboardBizModel` 链路的 `requireEntity`。
- **异步执行体不再做 RLS**：来源 ownership 已在请求线程校验。dashboard 级导出校验的是 dashboard ownership，其下 panel 同属该 dashboard，无需二次校验。异步线程无 `IServiceContext`，也无法做 RLS。
- **下载（downloadExportFile）**：仅任务 owner（`task.createdBy == 当前用户`），非 owner 抛 `ERR_DATAV_EXPORT_NOT_OWNER`。admin 角色无下载旁路（任务为发起人私有产物；admin 可经管理页 CRUD 查看任务记录，但下载文件仍限 owner——若运维需 admin 下载，可后续扩展）。
- **自定义 action 权限串**：`@Auth(permissions="NopDatavExportTask:{action}")` + action-auth.xml 角色绑定（与 D3-1 同模式）。

### action 权限点 → 默认角色对照表（D3-3 增补）

| BizObj | Action | 权限串 | 类型 | 默认角色 | 备注 |
|--------|--------|--------|------|----------|------|
| NopDatavExportTask | createExportTask | `NopDatavExportTask:createExportTask` | mutation | admin,user | 发起导出（来源经 requireEntity 校验） |
| NopDatavExportTask | getExportTask | `NopDatavExportTask:getExportTask` | query | admin,user | 查询自己任务状态 |
| NopDatavExportTask | cancelExportTask | `NopDatavExportTask:cancelExportTask` | mutation | admin,user | 取消自己任务 |
| NopDatavExportTask | downloadExportFile | `NopDatavExportTask:downloadExportFile` | query | admin,user | 下载自己任务产物 |

CRUD action（findPage/get 等）沿用平台默认权限 `NopDatavExportTask:query/mutation`。

### 图像导出 out-of-scope 裁定

PDF/PNG 图像导出依赖前端或无头浏览器渲染器（nop-chaos-flux 未产出），后端无可复用渲染内核（`nop-report-pdf` 是 XPT 表格渲染，非图表图像渲染）。**本设计显式拒绝图像导出**：`createExportTask` 收到 `format ∈ {pdf, png}` 时直接抛 `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`，不做空壳/静默跳过。归后继 plan（依赖渲染能力）。

### 管理页可见性裁定

导出任务实体 `NopDatavExportTask` 的 xmeta **不加** `no-web` tag，使其在 `_nop-datav.action-auth.xml` 获得 `FNPT:NopDatavExportTask:query/mutation` 权限点资源 + 管理页（与既有 7 实体同模式，依赖 codegen 生成物）。本设计不为管理页写自定义前端，仅依赖 codegen 生成物。

### D3-3 测试策略（详见对应 plan Phase 4）

- 端到端：`createExportTask(panel, csv)` → 轮询 `getExportTask` 至 succeeded（带超时 poll）→ `downloadExportFile` → 断言文件内容与 `getPanelData` 同源数据一致。
- 限额：超行数 / 超并发 / image 格式 均快速失败（错误码断言）。
- owner/权限：非 owner 下载拒绝、下载未完成任务拒绝。
- 状态机：failed 路径（构造查询失败）、cancelled 路径。
- 异步轮询用带超时 poll（`awaitility` 风格的手写 poll 循环 + 超时断言），不用 `Thread.sleep` 盲等。
