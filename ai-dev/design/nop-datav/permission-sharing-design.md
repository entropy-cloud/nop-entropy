# nop-datav 权限/分享/导出设计 (D3)

> Status: **final**（D3-1/D3-4/D3-2/D3-3 全部 + 分享访问加固）
> Last Reviewed: 2026-08-15

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

> **plan {1} D1 偏离裁定**：`NopDatavDashboardShare` 与 `NopDatavExportTask` 的继承 CRUD `query`/`mutation` 偏离平台默认（其他 14 实体为 query→`admin,user` / mutation→`admin`），改为 **`admin`-only**。理由：闭合 §223 未覆盖的继承 CRUD 越权 + passwordHash 泄漏入口（share/export 实体无 RLS，user 角色放开 CRUD 后水平越权即刻生效）。其余 14 实体不变。两个实体的各自定义 action（4+4 个）均绑 `admin,user`，内部经 owner 校验（Share 经 `requireDashboardOwnership`；ExportTask 经 `task.createdBy == 当前用户`）。详见 §222（Share 自定义 action 角色表）与 §370-377（ExportTask action 角色表）。

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

`getSharedDashboard(shareToken, password)`（`@BizQuery` + `@Auth(publicAccess=true)`）。行为（加固前基础语义，限流/统计前置步骤见「访问限流与访问统计」章节）：

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
- **plan {1} 增补裁定（闭合 §223 保护声明）**：继承 CRUD `query`/`mutation` 仅绑 `admin`（**不**绑 `user`），与 `NopDatavExportTask` 对称。`user` 角色只能经 owner 校验过的自定义管理 action 触达 share 数据。同时 `passwordHash` 在 xmeta 层 `published="false"`（同仓先例：`NopAuthUser.password`、`NopAiModel.apiKey`），从 GraphQL 出口完全移除——显式选择该字段会触发 `nop.err.graphql.undefined-field`。继承 CRUD 限 `admin` + 字段 `published="false"`，二者共同闭合 §223 的保护声明（避免「放开 CRUD 后水平越权 + passwordHash 泄漏」）。

### action 权限点 → 默认角色对照表（D3-2 plan {1} 增补）

| BizObj | Action | 权限串 | 类型 | 默认角色 | 备注 |
|--------|--------|--------|------|----------|------|
| NopDatavDashboardShare | createShare | `NopDatavDashboardShare:createShare` | mutation | admin,user | 创建分享（来源 dashboard 经 requireDashboardOwnership 校验） |
| NopDatavDashboardShare | listShares | `NopDatavDashboardShare:listShares` | query | admin,user | 查询某看板的分享列表（经 requireDashboardOwnership 校验） |
| NopDatavDashboardShare | revokeShare | `NopDatavDashboardShare:revokeShare` | mutation | admin,user | 吊销分享（经 requireDashboardOwnership 校验） |
| NopDatavDashboardShare | toggleShare | `NopDatavDashboardShare:toggleShare` | mutation | admin,user | 启用/禁用分享（经 requireDashboardOwnership 校验） |

CRUD action（findPage/get 等）沿用平台默认权限 `NopDatavDashboardShare:query/mutation`，但 **plan {1} D1 偏离裁定**：CRUD `query`/`mutation` 均仅绑 `admin`（不绑 `user`），与 §49 偏离裁定一致。

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

CRUD action（findPage/get 等）沿用平台默认权限 `NopDatavExportTask:query/mutation`，但 **plan {1} D1 偏离裁定**：CRUD `query`/`mutation` 均仅绑 `admin`（不绑 `user`），与 `NopDatavDashboardShare` 对称（见 §49 偏离裁定）。理由：export task 实体无 RLS，user 角色放开 CRUD 后水平越权入口即刻生效；user 角色只能经 owner 校验过的自定义 action 触达 export task 数据。

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

## 删除生命周期与分享吊销

> 来源：plan `ai-dev/plans/nop-datav/2026-08-14-2020-1-dashboard-screen-delete-cascade-lifecycle.md`（D1/D2/D4/D5 裁定落地）。

### D1 级联深度与删除形态裁定

nop-datav 全实体保持**物理删除**语义（不引入 `useLogicalDelete`，是否迁移逻辑删除是独立 ORM 契约级变更，见该 plan Deferred）。删除聚合根时子对象处置：

| 聚合根 | 级联对象 | 处置 |
|--------|----------|------|
| Dashboard | Panel / DashboardTab / DatasetRef / FilterState | 物理删除（与主表同语义，不留 dangling 行） |
| Dashboard | DashboardSnapshot | **物理删除（级联）** |
| Screen | ScreenWidget | 物理删除 |
| Screen | ScreenSnapshot | **物理删除（级联）** |

快照级联删除的理由：(1) 快照是已发布业务数据的完整序列化，聚合根删除后快照行失去唯一访问控制锚点（owner/RLS 均挂在主表），残留即无主敏感数据；(2) 快照随主表删除后不存在任何 API 访问路径（管理/公共访问均需主表存活），保留仅产生死数据；(3) 操作级审计在 `NopAuthOpLog`，不依赖快照行；(4) 与模块物理删除现状一致。

### D2 分享吊销形态裁定

删除看板时对该看板**全部分享行置 `enabled=0`（逻辑吊销）**，不删分享行。理由：(1) 保留吊销痕迹可审计（谁在何时创建/被吊销）；(2) 与既有 `revokeShare`/`toggleShare` 的 `enabled=false` 软禁用语义一致，公共访问统一走 `ERR_DATAV_SHARE_DISABLED` 拒绝路径。

### D4 面板 × 大屏 widget 边界

`NopDatavScreenWidget` 无 alert/share/report 关联实体（无任何实体的外键引用 `widgetId`），大屏删除仅需处理 widget + snapshot。调度消费者联动只发生在 **Panel 侧**：AlertRule 经 `panelId` 引用 Panel，故「删面板 / 删看板（连带面板）」均需停用关联 AlertRule。

### D5 级联挂接机制裁定

级联逻辑挂在 **BizModel 覆写 4 参 `doDeleteEntity(entity, refNamesToCheck, prepareDelete, context)`、在 `super` 调用之后执行**。该机制在标准删除路径必然执行的依据：标准 `delete(id)` mutation → `doDelete(id,…)` → 虚方法分派到 4 参 `doDeleteEntity`（`CrudBizModel.java:1066→1197`）；`batchDelete`（逐 id 调 `delete`）与 `deleteByQuery`（→`doDeleteMulti`→逐实体调 4 参 `doDeleteEntity`）同样收敛于此。

选择 `super` 之后执行的理由：`super.doDeleteEntity` 内先完成 `checkMetaFilter`/`checkDataAuth`/引用检查，级联副作用（尤其调度器 `removeJob` 这类**非事务性内存操作**）只在权限校验通过后才发生，未授权删除请求不会泄漏调度注销副作用。

被拒替代方案：

| 方案 | 拒绝理由 |
|------|----------|
| 3 参 `afterEntityChange(entity, action, context)` | 标准 delete(id) 路径只调 2 参 deprecated 版本（`CrudBizModel.java:1211`），3 参覆写在 delete 路径**不触发**（历史缺陷 Gap #3 根因） |
| 2 参 deprecated `afterEntityChange(entity, context)` | 已 `@Deprecated`；且被 3 参默认实现反向委托，覆写后 save/update/delete 分派易纠缠 |
| ORM/xmeta cascade-delete | nop-datav 关系全部定义在子实体侧（to-one），聚合根无 to-many 关系定义；且 cascade-delete 无法表达「停用 + 调度器即时注销」副作用 |

### 删除看板的完整生命周期语义

经 biz 层 `delete(dashboardId)` 删除看板时，按序：

1. 主表行删除（标准 CRUD 路径，含权限校验）。
2. 关联 ReportTask（按 `dashboardId`）：置 `status=DISABLED`（dict `datav/report-task-status` 已有值，无 ORM 变更）并即时 `unregisterTask`。
3. 关联 AlertRule（经 panel.dashboardId 定位）：置 `status=DISABLED` 并即时 `unregisterRule`。
4. 该看板全部分享置 `enabled=0`（D2）。
5. 子对象物理删除：FilterState / DatasetRef / DashboardTab / Panel（D1）。
6. DashboardSnapshot 级联物理删除（D1）。

级联中任一子步骤失败**显式抛错**（无吞错继续）；步骤 2/3 的事务边界与注销失败处理见 `schedule-report-design.md`「删除联动停用与即时注销」。

### `getSharedDashboard` 看板存活防御（defense-in-depth）

`getSharedDashboard` 在 token/enabled/expire/password 校验通过后、读取快照前，**先校验看板主表行存活**（按 `dashboardId` 经 DAO 查 `NopDatavDashboard`）；看板已删 → 显式拒绝，错误码 `ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND`（`nop.err.datav.share-dashboard-not-found`）。该防御独立于级联吊销生效：即使分享行因任何路径未被吊销（如构造数据、历史残留），已删看板的快照也不可经公共访问读出。

## 访问限流与访问统计（分享访问安全加固）

> 来源：plan `ai-dev/plans/nop-datav/2026-08-15-0004-2-share-access-hardening-rate-limit-stats.md`（D3-2 deferred follow-up 收口：删除生命周期 plan 的「分享访问速率限制」+ 分享 plan 的「分享访问点击统计/审计」）。
>
> `getSharedDashboard` 是 nop-datav 唯一 `publicAccess=true` 匿名入口。本章为其补齐生产级防护（两级限流）与可观测性（访问统计聚合列），全部行为可配置且默认保守安全。

### R1 限流键与来源识别

**裁定：组合键 `shareToken + 来源IP`**（同一 token 不同来源互不误伤）。

- 来源 IP 途径：`IServiceContext.getRequestClientIp()`——读 `nop-client-addr` 头（`ApiConstants.HEADER_CLIENT_ADDR`），生产环境由网关注入，service 层可达。web 层 `DefaultClientIpFetcher` 的 `X-Forwarded-For` 解析仅 HTTP 层可达（BizModel 层拿不到），不采信。
- 信任边界：`nop-client-addr` 仅在网关链路下可信；直连部署可伪造。限流是防御纵深而非认证——伪造头者只影响自己键的配额，无提权面，可接受。
- **降级**：取不到来源 IP（无网关注入/单机测试）→ 退化为 token 单维（IP 段记常量占位）。显式记录退化语义：同一 token 的全部来源共享限流配额与密码锁定（攻击者可拖累其他访问者）——无更可信来源时的可接受残余，非静默行为。
- 拒绝纯 token 单维键：攻击者可恶意打满某热门 token 的配额，DoS 该链接的全部合法访问者；组合键把攻击面隔离到 (token, 来源IP) 对。

### R2 限流存储与生命周期

**裁定：进程内 per-key 状态表**，平台 `LocalCache`（命名 cache + 容量上界 + 自动驱逐，先例：`TaskFlowManagerImpl` 的 globalRateLimiters）；容量经 `nop.datav.share.rate-limit.max-keys` 配置（默认 10000），超界按 LocalCache 驱逐策略回收（被驱逐键的限流状态归零，等效于窗口重开——容量上界防内存无界增长）。

- **单节点语义（部署边界，显式记录）**：限流状态在进程内存，多节点部署各节点独立计数（实际配额 ≈ N × 配置值）。集群级限流归属网关/负载均衡层，本仓不实现（Non-Goal）；宿主 app 多节点部署时须在网关层叠加限流。
- **可测试性硬要求**：时钟经可替换 supplier 注入（默认系统时钟；测试注入 fake 时钟推进窗口，**禁止 `Thread.sleep` 盲等式窗口恢复测试**）。限流状态组件经独立 bean 装配（镜像 nop-ai `ChatServiceImpl.createRateLimiter` protected 工厂的 seam 思想）。

### R3 两级阈值与配置

**两级独立计数器（裁定：失败专用计数，不与成功访问共用）**——失败计数驱动锁定、总速率计数驱动限速，语义不同；共用会让正常浏览耗尽密码预算（或爆破消耗浏览配额反向掩盖）。

| 层 | 语义 | 默认阈值 |
|----|------|----------|
| 密码失败级 | 同一 (token, ip) 窗口内密码验证失败达阈值 → 锁定该键的密码尝试（锁定时长 = 窗口）；**锁定期间即使密码正确也被拒**；成功验证重置失败计数 | 5 次 / 窗口 |
| 总速率级 | 同一 (token, ip) 每窗口总请求数上限（尝试即计数，含失败与被拒请求——攻击期持续保持键热度，窗口滚动自然恢复） | 60 次 / 窗口 |

窗口统一配置（两级共用窗口参数）：默认 600 秒（10 分钟）。

配置项（`NopDatavConfigs`）：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.datav.share.rate-limit.enabled` | `true` | 总开关；`false` 时行为与加固前逐字节等价（零检查零记账） |
| `nop.datav.share.rate-limit.max-password-failures` | `5` | 窗口内密码失败锁定阈值（0 或负值视为禁用该层） |
| `nop.datav.share.rate-limit.max-access-per-window` | `60` | 窗口内总请求上限（0 或负值视为禁用该层） |
| `nop.datav.share.rate-limit.window-seconds` | `600` | 窗口与锁定时长（秒） |
| `nop.datav.share.rate-limit.max-keys` | `10000` | per-key 状态表容量上界 |

超限错误码（与既有 `ERR_DATAV_SHARE_*` 命名对齐，仅携带 token 与重试提示，不含密码相关敏感信息）：

- `ERR_DATAV_SHARE_RATE_LIMITED`（`nop.err.datav.share-rate-limited`）：总速率超限，param `shareToken` + `retryAfterSeconds`。
- `ERR_DATAV_SHARE_PASSWORD_LOCKED`（`nop.err.datav.share-password-locked`）：密码失败锁定，param `shareToken` + `retryAfterSeconds`。

被限流请求**快速失败**（即时拒绝、零等待、不触发任何查询），无静默返回空快照/降级数据。

### R6 失败锁定语义到限流原语的映射

**裁定：自定义轻量 per-key 状态机**（固定窗口计数器 + 锁定时间戳；先例：`AiRateLimitGatewayInterceptor` 手写 TokenBucket——平台原语无所需语义时的自建先例）。

- 拒绝裸用平台 `DefaultRateLimiter`（包装 Guava `RateLimiter`）：平滑 permits/sec 速率语义**无失败计数、无锁定状态**，「达到失败阈值后即使密码正确也被拒 + 窗口过后恢复」无法由裸 `tryAcquire` 表达。且 `DefaultRateLimiter.getAcquireFailCount()` 现返回 `acquireSuccessCount`（平台复制粘贴缺陷），stats 不可作为断言或观测依据。
- 所选原语真实语义（显式记录）：**固定窗口计数**——窗口边界可能突发至 2× 上限（固定窗口固有边界效应，单链接访问场景接受，不引入滑动窗口复杂度）；总速率超限即时拒绝不排队等待。
- 窗口推进仅依赖时钟读取（fake 时钟可确定性驱动），无后台线程、无过期清理任务（惰性淘汰：窗口滚动在下次访问时重算；键级淘汰靠容量上界）。

### R4 访问统计形态

**裁定：share 实体聚合列** `visitCount`（LONG）/ `lastVisitTime`（TIMESTAMP），ORM 加列经源模型 + 再生成管线（本 plan 为 plan-first 凭证）。

- **「一行配置得明细留痕」替代方案**（`GraphQLAuditLogger` 兼容匿名请求 userName="-"，把 `NopDatavDashboardShare__getSharedDashboard` 加入 `audit-query-patterns` 即得每次访问留痕）考虑后**拒绝作为统计主形态**：(1) `listShares` 需要聚合 count + 最近访问时间，从 `NopAuthOpLog` 聚合要跨模块查询平台审计表、聚合口径不被 nop-datav 拥有；(2) 审计表归 retention/运维清理管辖，统计值随清理漂移；(3) 每访问一行的写放大大于单行计数器更新。明细流水维持 Non-Goal（运营期合规需求出现时该一行配置仍可得，见 plan Non-Blocking Follow-ups）。
- **写放大与并发策略**：每次成功访问一条定向 SQL（`UPDATE nop_datav_share SET VISIT_COUNT = VISIT_COUNT + 1, LAST_VISIT_TIME = ? WHERE SHARE_ID = ?`）——数据库端原子自增，并发访问零丢失更新；不走实体 update 路径 → 无乐观锁 version bump、无审计列改写（`version`/`updatedBy`/`updateTime` 语义保留给管理操作，匿名统计不冒充管理操作者）。
- **匿名统计写入的操作者身份**：定向 SQL 不触碰审计列（`createdBy`/`updatedBy` 保持管理操作时的值），审计列 mandatory 约束与匿名上下文解耦。
- **统计口径**：仅成功访问计数（全链路校验通过且快照返回）；密码失败/被限流/被拒访问不计数（失败信息由限流状态与专用错误码表达）。
- **统计写失败降级**：记 WARN 日志、不阻断访问返回（辅助遥测 fail-open——显式裁定并留痕，非静默跳过）。
- `listShares` 暴露：新列随实体/GraphQL 输出类型自动暴露（owner 已有 `requireDashboardOwnership` 前置，不新增权限面；`passwordHash` 屏蔽不变）。

### R5 限流检查次序

**裁定：限流先于 token 查库**。加固后完整次序：

1. （开关开启时）总速率检查 → 超限抛 `ERR_DATAV_SHARE_RATE_LIMITED`【零 DB】
2. （开关开启时）密码锁定检查 → 锁定中抛 `ERR_DATAV_SHARE_PASSWORD_LOCKED`【零 DB】
3. token 查库（不存在 → `ERR_DATAV_SHARE_TOKEN_NOT_FOUND`）
4. `enabled` / `expireTime` 校验（原语义不变）
5. 密码校验：不匹配 → 失败记账（达阈值即置锁）后抛 `ERR_DATAV_SHARE_PASSWORD_MISMATCH`；匹配 → 成功清账；缺密码（`ERR_DATAV_SHARE_PASSWORD_REQUIRED`）不计失败（未消耗一次猜测）
6. 看板存活防御（原语义不变）
7. 快照读取（原语义不变）
8. 访问统计记账（成功访问）

理由：(1) 被限流请求（含不存在 token 的探测洪水）**零 DB 消耗**——满足「限流拒绝快于快照读取」；(2) 错误语义可区分——不存在 token 的前 M 次探测得 `TOKEN_NOT_FOUND`、超限后统一 `RATE_LIMITED`，探测洪水中后期的限流拒绝正是防护目标本身；(3) 拒绝「先查库后限流」：区分「token 不存在」与「被限流」的收益仅存在于攻击者的前 M 次请求，代价是所有被拒请求持续消耗查询资源。

开关关闭时步骤 1/2/5 记账与 8 记账中限流相关行为全部跳过，其余行为与加固前逐字节等价（R3 配置表）。

### 测试策略

- 限流测试全部经 fake 时钟推进窗口（guard bean 的可替换时钟 supplier），无 `Thread.sleep` 盲等。
- 密码爆破截断：达到失败阈值后**正确密码也被拒**（防爆破有效性断言，非仅计数断言）；窗口推进后自动恢复。
- 限流拒绝零查询：被拒请求错误码为限流专用码（而非快照/查询错误码），次序由 R5 固化。
- 不存在 token 探测：超限后错误码从 `TOKEN_NOT_FOUND` 翻转为 `RATE_LIMITED`。
- 同 token 不同来源（不同 `nop-client-addr`）互不误伤；默认配置下正常访问路径（浏览 + 正确密码）零误伤。
- 统计：两次成功访问后 `listShares` 计数为 2、`lastVisitTime` 非空且晚于访问前；密码失败/被限流访问不计数；并发访问计数零丢失（原子自增断言）。
- E2E：创建带密码分享 → 匿名正确密码访问成功且统计可见 → 连续错误密码至阈值 → 被限流（正确密码也被拒）→ 窗口推进后恢复 → 撤销分享后访问被拒。
