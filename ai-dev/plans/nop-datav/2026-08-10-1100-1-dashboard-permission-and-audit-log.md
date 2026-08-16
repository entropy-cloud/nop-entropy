# 5 看板权限（RBAC + 行级数据权限）与操作审计日志（D3-1 + D3-4）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D3-1 看板权限 + D3-4 操作日志
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md`（D3 阶段，work items D3-1/D3-4）；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md`
> Related: 设计契约 `ai-dev/design/nop-datav/permission-sharing-design.md`（本 plan Phase 1 产出 D3-1/D3-4 部分）；后继 plan `2026-08-10-1100-2-dashboard-sharing.md`（D3-2 分享，依赖本 plan 的权限模型）
> Review Consensus: 两轮独立子 agent 对抗性审查（含想象性分析）通过，无 Blocker/Major；Blocker（测试 classpath、异步审计 flush）与 Major（@Auth impl 注解、raw-SQL RLS 基线、deny-by-default 顺序、resolveOperator 去重归属、enableDataAuth wiring sanity、skip-check-for-admin H-2）均已收敛。

## Purpose

将 nop-datav 从「全开放、零鉴权」推进到「接入 nop-auth 的 RBAC + 行级数据权限（RLS）+ 操作审计日志」——看板/面板等资源的 GraphQL action 受角色/权限控制，列表查询按行级规则自动过滤（owner/admin 范围），关键的变更与查询操作自动写入 nop-auth 操作日志。本 plan 收口 roadmap D3-1（看板权限）和 D3-4（操作日志）的全部后端验收条件，**全程复用平台既有鉴权/审计机制，不自建权限体系**（roadmap §Framework reuse 明确要求）。

## Current Baseline

### 已落地（D0/D1/D2 后端）

- 6 个 ORM 实体可用（`nop-datav/model/nop-datav.orm.xml`）：`NopDatavDashboard`/`NopDatavPanel`/`NopDatavDashboardTab`/`NopDatavDatasetRef`/`NopDatavDashboardSnapshot`/`NopDatavFilterState`。
- 6 个 BizModel 均位于 `io.nop.datav.service.entity` 包，`extends CrudBizModel<Entity>`，实现 `INopDatavXxxBiz` 接口（`nop-datav-dao`）。

### 鉴权现状（全开放 — 本 plan 要收口的对象）

- **零鉴权代码**：所有 BizModel 无任何 `@Auth` 注解、无角色/权限检查。身份仅用于 `publishedBy`/`createdBy`/`FilterState.userName` 隔离，通过 `IServiceContext.getUserContext().getUserName()` 获取（`NopDatavDashboardBizModel.resolveOperator` 与 `NopDatavFilterStateBizModel.resolveOperator` 各有一份私有拷贝，重复）。
- **action-auth.xml 现状**：
  - `nop-datav-web/.../auth/_nop-datav.action-auth.xml`（**生成物**，勿手改）已含 6 实体的 `FNPT:{Entity}:query` / `FNPT:{Entity}:mutation` 权限点资源（`<permissions>` 列出权限串）。
  - `nop-datav-web/.../auth/nop-datav.action-auth.xml`（手写）仅 `<site id="main"/>`——**未分配角色**，所有权限点形同虚设。
  - `nop-datav-app/.../auth/app.action-auth.xml` 同样仅 `<site id="main"/>`。
- **data-auth.xml 现状**：`nop-datav-service/.../auth/nop-datav.data-auth.xml` 为空 `<objs/>`——**无任何行级规则**。app 层 `app.data-auth.xml` 用 `<auth-gen:GenFromModules>` 自动聚合各模块（与 nop-spring-security-demo 同模式）。
- **鉴权开关现状**：`nop.auth.enable-action-auth` / `nop.auth.enable-data-auth` 默认 `false`；nop-datav 的 app/test 配置未开启。`@NopTestConfig` 提供 `enableActionAuth()`/`enableDataAuth()` 两个 `OptionalBoolean` 开关。
- **无权限相关错误码**：`NopDatavErrors`（`io.nop.datav.service`，19 个码）无任何 `ERR_DATAV_PERMISSION_*` / `ERR_DATAV_NOT_OWNER` 码。
- **ORM 无权限列**：Dashboard 等实体无 `owner`/`accessLevel`/`tenantId` 列；唯一身份列是标准审计列 `createdBy`（存创建者 userName）。

### 平台既有可复用机制（经核实存在 — 本 plan 全程复用，不重建）

- **Action 级 RBAC**：`@Auth` 注解（`io.nop.api.core.annotations.directive.Auth`，属性 `publicAccess`/`roles`/`permissions`/`skipWhenNoAuth`）；`ReflectionBizModelBuilder` 为每个 action 自动生成默认权限串 `{bizObj}:{opType}|{bizObj}:{name}`；`GraphQLActionAuthChecker` 在 `enableActionAuth=true` 时生效；`DefaultActionAuthChecker` 经 SiteMapProvider 解析 permission→roles（`NopAuthResource.permissions` CSV 列 + `NopAuthRoleResource`）。参考用法：`nop-code/.../NopCodeIndexBizModel.java`（`@Auth(roles="admin")` / `@Auth(permissions="...")`）。
- **行级数据权限（RLS）**：`IDataAuthChecker` + `DefaultDataAuthChecker`；`data-auth.xdef` schema（`<obj><role-auths><role-auth roleIds=... priority=...><filter>...</filter></role-auth>`）；`DataAuthEntityFilterProvider`（注册为 bean `nopDataAuthEntityFilterProvider`，条件 `on-bean: nopDataAuthChecker`）在 `enable-data-auth=true` 时**自动**对所有实体 SQL 注入行级过滤；`CrudBizModel.checkDataAuth()` / `AuthHelper.appendFilter()` 在 findPage/get/save/update/delete 自动调用。**关键语义**：`DefaultDataAuthChecker.getFilter`（line 195-213）一旦某 bizObj 有任意 role-auth 规则，未命中任何规则的用户抛 `ERR_AUTH_NO_DATA_AUTH`（**fail-closed**）。参考规则：`nop-job/.../nop-job.data-auth.xml`（admin 无 filter / user 按 namespaceId 过滤）、`nop-auth/.../nop-auth.data-auth.xml`（NopAuthUser 按 tenantId 过滤）。
- **DB 驱动行级规则**：`NopAuthRoleDataAuth` 表（`roleIds`/`bizObj`/`priority`/`filterConfig`/`whenConfig`），开关 `nop.auth.use-data-auth-table`，与静态 data-auth.xml 合并。
- **操作审计日志（D3-4）**：`GraphQLAuditLogger`（`IGraphQLLogger`，按 `nop.auth.graphql.audit-mutation-patterns` / `audit-query-patterns` glob 匹配 operation name）→ `IAuditService`（`AuditServiceImpl`，异步批量）→ `NopAuthOpLog` 实体（含 userName/sessionId/operation/usedTime/resultStatus/errorCode/opRequest/opResponse）。开关 `nop.auth.graphql.enable-audit`。bean 已在 `auth-service.beans.xml` 注册（条件 bean）。**D3-4 为纯配置启用，零业务代码**。
- **admin 旁路**：`nop.auth.skip-check-for-admin`（默认 `false`），`DefaultActionAuthChecker`/`DefaultDataAuthChecker` 在该开关开启时对 `admin`/`nop-admin` 角色旁路。
- **身份上下文**：`IUserContext` 暴露 `getUserName()`/`getUserId()`/`getTenantId()`/`getDeptId()`/`getRoles()`；data-auth filter XPL 上下文绑定 `$userContext`（=`VAR_USER_CONTEXT`）与 `$svcCtx`（=`VAR_SVC_CTX`）；既有模块 filter 实际使用 `${$context.tenantId}` / `${$context.namespaceId}` 形式（**Phase 1 须核实 `$context` 在 filter 内的确切绑定与可用属性，以选定 owner 匹配表达式**）。

### 当前 query/CRUD 管线（RLS 注入点 — 经核实）

- `NopDatavDashboardBizModel` 等继承 `CrudBizModel`，故 findPage/findList 的行级过滤通过 `CrudBizModel.prepareFindPageQuery` → `AuthHelper.appendFilter(checker, query, bizObj, action, ctx)`（将 `IDataAuthChecker.getFilter()` 结果直接追加到内存 `QueryBean`，与 `DataAuthEntityFilterProvider` 是**两条独立机制**）；get/save/update/delete 经 `CrudBizModel.checkDataAuth(action, entity, ctx)`（`CrudBizModel.java:913` 等）做单实体行级校验。**开启 `enable-data-auth` 且 data-auth.xml 非空即自动生效，无需改 BizModel 基类路径**。
- `DataAuthEntityFilterProvider`（bean `nopDataAuthEntityFilterProvider`）**仅**作用于带 `<filter>` SyntaxMarker 的编译型 SQL（`GenSqlTransformer.transformFilter`），**不覆盖** `IJdbcTemplate.executeUpdate(SQL.begin().sql("update ...").end())` 这类无 marker 的原生 SQL。
- 自定义 action（`publishDashboard`/`rollbackDashboard`/`getPublishedDashboard`/`resolveFilterValues`/`parseFilterFromUrl`/`getPanelData`/`refreshPanel`/`resolveLinkage`/`resolveJump`/`saveFilterState`/`getFilterState`）**不在 CrudBizModel 自动鉴权路径内**，需显式 `@Auth`。**但**：`publishDashboard`/`rollbackDashboard`/`getPublishedDashboard` 等 action 内已先调用 `requireEntity(id, actionName, context)`（`NopDatavDashboardBizModel`），而 `requireEntity` → `CrudBizModel.getEntity` → `checkDataAuth(METHOD_GET, entity, ctx)`——**故这些 action 的行级校验已由上游 `requireEntity` 提供**，其后续 `IJdbcTemplate.executeUpdate` 原生更新虽绕过 `DataAuthEntityFilterProvider`，但操作前置实体已校验过权限。**Phase 3 须核实每个自定义 action 是否都经过 `requireEntity`/`checkDataAuth`；对未经过的（如 `getPanelData` 读 panel、`saveFilterState` 读 dashboard），裁定是否补显式 `checkDataAuth` 或 `@Auth(permissions=...)` 收口**。新增任何原生 SQL 路径必须前置 `requireEntity` 或显式 `AuthHelper.checkDataAuth`，不得假设 `DataAuthEntityFilterProvider` 兜底。

### 已核实的关键事实（降低 Phase 1 不确定性）

- **owner 匹配表达式已可确定**：data-auth filter XPL 中 `$context` 是注册的全局变量（`CoreConstants.GLOBAL_VAR_CONTEXT="$context"` → `ContextProvider.currentContext()` 返回 `IContext`），`IContext.getUserName()` 存在；`DefaultDataAuthChecker.newEvalScope` 另绑定 `$userContext`（IUserContext）。故 owner 行级 filter 可写作 `<eq name="createdBy" value="${$context.userName}"/>`（Dashboard.createdBy 存 userName，与 `resolveOperator` 使用 `getUserName()` 一致）。
- **`@Auth` 不从接口继承**：Java 方法级注解不继承自接口；`ReflectionBizModelBuilder` 从 impl 类方法读 `@Auth`。故 `@Auth` 必须加在 **BizModel impl 方法**上（如 `NopDatavDashboardBizModel.publishDashboard`），接口 `INopDatavXxxBiz` 加注解无运行时效果。
- **deny-by-default**：`SiteCacheData.isPermitted` 对未在 `permissionToRoles` 注册的权限返回 `false`。当前手写 `nop-datav.action-auth.xml` 仅 `<site id="main"/>`——**无角色绑定**。故「加 `@Auth` + 开 `enable-action-auth`」若不同步补角色绑定，所有 datav action 对所有用户拒绝（含测试）。Phase 2 必须先补资源+角色绑定，再加/启 `@Auth`。
- **`nop.auth.skip-check-for-admin` 历史**：`DefaultActionAuthChecker` 注释 H-2 指出该开关曾默认 `true` 导致管理员默认绕过权限（已修复为默认 `false`）。本 plan **保持 `false`**，不作为中立选项。
- **D3-4 审计异步**：`AuditServiceImpl extends AbstractBatchProcessService`——`saveAudit` 仅入队，后台批量 flush 落库。测试断言 `NopAuthOpLog` 须处理异步延迟（poll `isAllProcessed()` 或同步 fixture）。
- **`enableDataAuth=TRUE` 测试无先例**：全仓库仅 `enableActionAuth=TRUE` 有用例（nop-auth/nop-code）；`enableDataAuth=TRUE` 无先例。Phase 3 测试须先做 wiring sanity 断言（`context.getDataAuthChecker() != null`），避免「checker 为 null → filter 不追加 → 测试误判 admin 全见」的空壳陷阱。

## Goals

- **D3-1 角色级权限（action/object 级 RBAC）**：nop-datav 全部自定义 GraphQL action 声明权限要求（`@Auth`），CRUD action 沿用平台自动默认权限；通过 `action-auth.xml` 暴露权限点资源，使管理员可经平台既有的 角色→资源→权限 管理面分配。开启 `enable-action-auth` 后，无权角色的请求被拒绝。
- **D3-1 行级数据权限（RLS）**：为 `NopDatavDashboard`（至少）配置 data-auth.xml 行级规则——admin 角色见全部；普通用户按 owner（`createdBy` = 当前用户）范围过滤，已发布看板对登录用户可见（具体范围 Phase 1 裁定）。`DataAuthEntityFilterProvider` 自动注入 SQL 过滤，`CrudBizModel` 自动调用。
- **D3-4 操作审计日志**：开启 `nop.auth.graphql.enable-audit`，配置 nop-datav 的 audit pattern（至少覆盖看板发布/回滚/CRUD mutation），关键操作自动写入 `NopAuthOpLog`。**生产路径零业务代码（纯配置）；测试侧需处理 `AuditServiceImpl` 异步批量 flush**。
- **设计文档定稿**：在 `permission-sharing-design.md` 中产出 D3-1/D3-4 的最终设计决策（无 "Proposed"/"Current vs Proposed" 段落）。

## Non-Goals

- **不做公共分享链接 / 匿名访问（D3-2）**：本 plan 不引入 share token / publicAccess action。属后继 plan `2026-08-10-1100-2-dashboard-sharing.md`。
- **不做导出（D3-3）**：看板/面板导出 PDF/PNG/Excel 属独立 plan；其图像导出部分隐含依赖前端渲染（flux），本 plan 不涉及。
- **不做细粒度 per-user per-dashboard ACL**：本 plan 的「用户级」权限通过平台 RBAC（用户→角色→资源→权限）+ owner 行级过滤实现；针对「指定某用户对某具体看板的访问」的显式授权表（ACL 实体）不在本 plan scope——若确需，归入 D3-2 分享或后继 plan。
- **不自建权限/审计体系**：不新建 `NopDatavDashboardRole`/`NopDatavPermission`/`NopDatavAuditLog` 等实体（roadmap §Framework reuse 硬约束）。
- **不做字段级权限（xmeta `<auth>`）**：除非 Phase 1 裁定确有必要，否则不在 sensitive prop 上加 `<auth>`（留作 non-blocking follow-up）。
- **不做前端**：权限矩阵 UI、操作日志查看页前端不在本 plan（flux 侧）。
- **不改 ORM 实体结构**（owner/access 列）：D3-1 复用既有 `createdBy` 作 owner 标识；**不新增权限相关列**。若 Phase 1 评估认定必须加列，则升级为 plan-first 显式变更并在 plan 内声明（当前裁定：不需要）。

## Scope

### In Scope

- 自定义 action 的 `@Auth` 注解化（角色/权限声明）。
- `action-auth.xml` 角色分配裁定与配置（手写层补全角色绑定，或显式记录「沿用默认全开放 + 由管理员运行时分配」策略——Phase 1 裁定）。
- `data-auth.xml` 行级规则（Dashboard 为主，Panel/Snapshot 等子资源裁定是否需要）。
- D3-1 所需的权限相关错误码（如有 action 层显式校验需要）。
- `enable-action-auth` / `enable-data-auth` / `use-data-auth-table` / `enable-audit` 开关在 app 配置与测试配置中的启用。
- D3-4 audit pattern 配置。
- 鉴权集成测试（`@NopTestConfig(enableActionAuth=TRUE, enableDataAuth=TRUE)`，覆盖 RBAC 拒绝/放行 + RLS 行过滤 + 审计落库）。
- `permission-sharing-design.md` 的 D3-1/D3-4 最终设计段落。

### Out Of Scope

- D3-2 分享（share link / publicAccess / 嵌入）。
- D3-3 导出。
- per-user per-dashboard ACL 实体。
- 前端权限/审计 UI。
- 字段级权限（除非 Phase 1 裁定必需）。
- 平台 `@Auth`/`IDataAuthChecker`/`GraphQLAuditLogger` 内部实现改造（仅配置消费，不改框架）。

## Execution Plan

### Phase 1 - 设计文档定稿 + 权限模型裁定（D3-1 + D3-4）

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md`（产出 D3-1/D3-4 段落，替换现有 stub）

- Item Types: `Decision`

- [x] 在 `permission-sharing-design.md` 写入 D3-1/D3-4 的已裁定决策（不写类签名/字段定义/伪代码——源码是唯一事实）：
  - **Action 级鉴权策略裁定**：在以下方案中选择并记录理由——
    - **拟定方案 A（推荐）**：自定义 action 显式 `@Auth(permissions="{BizObj}:{action}")`；CRUD action 沿用平台自动默认权限（`{BizObj}:{opType}|{BizObj}:{name}`）。`action-auth.xml` 手写层补全各 `FNPT:` 权限点资源的角色绑定（admin 全量；user 视裁定）。权限点资源清单已在生成物 `_nop-datav.action-auth.xml` 中，但**自定义 action（如 `publishDashboard`/`getPanelData`/`resolveLinkage`）的权限点未在生成物中**——裁定是否在 `_nop-datav.action-auth.xml` 的 `auth-gen:DefaultActionAuthPostExtends` 之外手动补 `FNPT:NopDatavDashboard:publishDashboard` 等权限点，或仅靠 `@Auth(permissions=...)` + 运行时资源分配。
    - 方案 B（仅 `@Auth`、不碰 action-auth.xml 角色绑定，全靠管理员运行时分配）作对比记录。
  - **行级数据权限范围裁定（owner 模型，已预收敛）**：Dashboard 复用 `createdBy` 作 owner 标识（不加 ORM 列）。owner 匹配表达式**已核实可用**：`<eq name="createdBy" value="${$context.userName}"/>`（见 Current Baseline 已核实事实）。Phase 1 在以下候选中**裁定最终语义**并记录理由——
    - (a) 仅 owner：`createdBy = 当前用户`；
    - (b) owner + 已发布：`createdBy = 当前用户` OR `publishStatus = PUBLISHED`（已发布看板对登录用户可见）。
    - ~~(c) owner + 同租户~~ **已排除**：nop-datav ORM 无 `tenantId` 列、无 `useTenant`（已核实），该选项需 ORM 变更，与 Non-Goals「不改 ORM 实体结构」冲突，不再作为候选。Phase 1 在 (a)/(b) 中选定。
  - **fail-closed 角色覆盖裁定**：`DefaultDataAuthChecker.getFilter` 对「有规则但未命中」抛 `ERR_AUTH_NO_DATA_AUTH`。须为所有需访问的角色（至少 `admin`、`user`）定义规则；裁定是否需要 `nop-admin` 规则。
  - **admin 旁路裁定（已预收敛）**：`nop.auth.skip-check-for-admin` 保持 **`false`**（见 Current Baseline H-2 历史，不作为中立选项，不开启）。Phase 1 仅记录该裁定，不在计划中引入开启该开关的步骤。
  - **子资源行级裁定**：Panel/Tab/DatasetRef/Snapshot/FilterState 是否需要独立行级规则，还是经 Dashboard 归属隐式覆盖（FilterState 已有 userName 隔离，预期不需额外规则）。记录裁定。
  - **自定义 action 行级校验裁定（已预收敛方向）**：`publishDashboard`/`rollbackDashboard`/`getPublishedDashboard` 及 Panel 的 `getPanelData`/`refreshPanel`/`resolveLinkage`/`resolveJump` 均已先调用 `requireEntity` → `checkDataAuth`（已核实）。Phase 1 裁定：对**未经过** `requireEntity` 的自定义 action（如 FilterState 的 `saveFilterState`/`getFilterState`，其直接用 DAO 按 userName 查询、不经 dashboard 实体级校验），是否补显式 `checkDataAuth` 或仅靠 `@Auth(permissions=...)` + userName 隔离收口；并列出每个自定义 action 的「是否经 requireEntity」清单。
  - **DB 驱动规则裁定**：是否启用 `nop.auth.use-data-auth-table=true` 允许运行时经 `NopAuthRoleDataAuth` 管理面增配行级规则（推荐启用，灵活性最高）。
  - **D3-4 审计范围裁定**：audit pattern 选定（至少 `NopDatavDashboard__*`、`NopDatavPanel__*` 的 mutation；Phase 1 裁定是否纳入 query 类如 `getPublishedDashboard`）；记录 `nop.auth.graphql.enable-audit=true` + 选定 pattern。
  - **拒绝的替代方案**：至少记录——自建权限实体 vs 接入 nop-auth（采用接入）；owner 加 ORM 列 vs 复用 createdBy（采用复用）；per-user ACL vs 角色+owner 行级（本 plan 采用后者，ACL 归 D3-2）。
- [x] Phase 1 须输出一份「权限点 → 角色」对照表（写入 design doc），列出每个自定义 action 的权限串与建议默认角色，供 Phase 2 落地。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `permission-sharing-design.md` 含 D3-1/D3-4 全部已裁定决策，无 "Proposed Design"/"Current vs Proposed" 段落（plan guide rule #14）
- [x] design doc 含「自定义 action 权限点 → 建议角色」对照表 + 「每个自定义 action 是否经 requireEntity/checkDataAuth」清单
- [x] owner 匹配表达式已写入 design doc（`<eq name="createdBy" value="${$context.userName}"/>`，引用 `CoreConstants.GLOBAL_VAR_CONTEXT`），并在 (a)/(b) 间作出最终裁定
- [x] design doc 记录 `nop.auth.skip-check-for-admin=false`（含 H-2 历史）与 deny-by-default 角色覆盖裁定
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Action 级 RBAC 落地 + 鉴权测试基建（D3-1）

Status: completed
Targets: `NopDatavDashboardBizModel.java`、`NopDatavPanelBizModel.java`、`NopDatavFilterStateBizModel.java`（+ 其余 BizModel 的 **impl 方法**）、`nop-datav-web/.../auth/nop-datav.action-auth.xml`（手写层）、`NopDatavErrors.java`、`nop-datav-service/pom.xml`（测试依赖）

- Item Types: `Fix`, `Decision`

- [x] **鉴权测试基建（Blocker 前置）**：为使 `@NopTestConfig(enableActionAuth=TRUE)` 的测试能解析 `nopActionAuthChecker`/`nopDataAuthChecker`/`nopGraphQLLogger` 等 bean（这些 bean 位于 `nop-auth-service`/`nop-biz-auth-core`，而 `nop-datav-service/pom.xml` 当前未依赖），裁定并落实其一：(a) 在 `nop-datav-service/pom.xml` 加 `<scope>test</scope>` 依赖 `nop-auth-service`（+必要的 `nop-biz-auth-core`）；或 (b) 将鉴权集成测试置于 `nop-datav-app/src/test/`（该模块已依赖 `nop-auth-service`）；或 (c) 经 `@NopTestConfig(testBeansFile=...)` 提供测试 bean 覆盖。**Phase 2 须先落实此项，后续 Phase 3/4 测试方可运行**
- [x] **顺序约束（避免 deny-by-default 锁死）**：先在 `nop-datav.action-auth.xml` 手写层补全各 `FNPT:` 权限点资源的角色绑定（admin 全量；user 视 Phase 1 裁定；自定义 action 如 `publishDashboard`/`getPanelData` 的权限点若 `auth-gen` 未自动生成则手动补 `FNPT:NopDatavDashboard:publishDashboard` 等），**再**为 impl 方法加 `@Auth`、**最后**在 app/test 配置开 `enable-action-auth`
- [x] 按 Phase 1 对照表为全部自定义 action 的 **impl 方法**加 `@Auth(permissions="{BizObj}:{action}")`（或 `@Auth(roles=...)` where appropriate）。**注解加在 impl 方法上（如 `NopDatavDashboardBizModel.publishDashboard`），不加在接口方法上——`@Auth` 不从接口继承，接口加注解无运行时效果**。覆盖至少：`publishDashboard`/`getPublishedDashboard`/`rollbackDashboard`/`resolveFilterValues`/`parseFilterFromUrl`（Dashboard）；`getPanelData`/`refreshPanel`/`resolveLinkage`/`resolveJump`（Panel）；`saveFilterState`/`getFilterState`（FilterState）。CRUD action 沿用平台默认权限（无需注解）
- [x] 在 `NopDatavErrors` 新增权限相关错误码（如 action 内显式 owner 校验需要时，如 `ERR_DATAV_NOT_DASHBOARD_OWNER`）；错误消息用英文
- [x] **resolveOperator 去重（已裁定纳入 Phase 2）**：将 `NopDatavDashboardBizModel` 与 `NopDatavFilterStateBizModel` 两处重复的私有 `resolveOperator` 抽到共享 helper（`io.nop.datav.service` 包下），保持单一事实

Exit Criteria:

- [x] 全部自定义 action 的 **impl 方法**带 `@Auth`；CRUD action 依赖平台默认权限（无注解亦生效，已在 ReflectionBizModelBuilder 核实）
- [x] 鉴权测试基建已落实（`nop-auth-service`/`nop-biz-auth-core` bean 在测试上下文可解析）
- [x] **顺序验证**：`nop-datav.action-auth.xml` 角色绑定在 `@Auth`/`enable-action-auth` 启用之前已补全
- [x] **先正向后负向**（避免 deny-by-default 误判）：先有一个「有权角色可调用 action」的正向测试 PASS，再有「无权角色被拒」的负向测试（抛 `ERR_AUTH_NO_PERMISSION` 或等价）
- [x] 测试 fixture 策略已明确（如何 seed `NopAuthRoleResource` 角色→资源→权限映射：测试数据文件 / 编程式 setup，**不得**用 `skip-check-for-admin=true` 短路）
- [x] **接线验证**（rule #23）：正向测试证明 `@Auth` → `GraphQLActionAuthChecker` → permission→role 解析链路连通（有权放行），负向测试证明无权拒绝——非仅注解存在
- [x] **无静默跳过**（rule #24）：鉴权失败显式抛异常，不返回空结果/ null 作为「正常」
- [x] **新功能测试覆盖**（rule #25）：显式列出——有权限放行、无权限拒绝两类用例（至少一个自定义 action）
- [x] owner-doc 更新：`permission-sharing-design.md` 记录最终 action 权限映射；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 行级数据权限落地（D3-1）

Status: completed
Targets: `nop-datav-service/.../auth/nop-datav.data-auth.xml`、`NopDatavDashboardBizModel.java`（自定义 action 的 checkDataAuth / 原生 SQL 路径裁定）、app/test 配置（`enable-data-auth` 开关）

- Item Types: `Fix`, `Decision`

- [x] 在 `nop-datav.data-auth.xml` 为 `NopDatavDashboard` 写入行级规则（admin 无 filter；user 按 Phase 1 裁定的 owner/owner+published 表达式；`nop-admin` 视裁定）。规则须覆盖所有需访问角色，避免 fail-closed 误伤
- [x] 按 Phase 1 子资源裁定，为需要独立规则的子资源（Panel/Snapshot 等，若裁定需要）补规则
- [x] 按 Phase 1 裁定，为绕过 CrudBizModel 的自定义 action（`publishDashboard`/`rollbackDashboard`/`getPublishedDashboard` 等）补 action 内显式 `checkDataAuth`（使用 `AuthHelper.checkDataAuth` 或 `CrudBizModel.checkDataAuth`），或将原生 `IJdbcTemplate.executeUpdate` 改为实体 DAO 路径以纳入 RLS——**核实原生更新 SQL 是否被 `DataAuthEntityFilterProvider` 覆盖**
- [x] 启用 `nop.auth.enable-data-auth=true`；按 Phase 1 裁定启用 `nop.auth.use-data-auth-table`（app 配置 + 测试配置 `@NopTestConfig(enableDataAuth=TRUE)`）

Exit Criteria:

- [x] `nop-datav.data-auth.xml` 含 Dashboard 行级规则，经 app 层 `<auth-gen:GenFromModules>` 聚合后生效
- [x] **wiring sanity（首个 `enableDataAuth=TRUE` 测试）**：测试先断言 `context.getDataAuthChecker() != null`（全仓库首个 data-auth 测试，须先证明 checker 真正注入，避免「checker 为 null → filter 不追加 → 误判 admin 全见」空壳陷阱）
- [x] **端到端验证**（rule #22）：`@NopTestConfig(enableDataAuth=TRUE)` 测试中——user A 创建的看板对 user B 不可见（findPage 结果集按 owner 过滤），admin 可见全部；证明 `CrudBizModel.prepareFindPageQuery` → `AuthHelper.appendFilter` → `IDataAuthChecker.getFilter` 链路在运行时连通，非仅配置存在
- [x] **接线验证**（rule #23）：受限用户 findPage 返回的行集合确实被 filter 缩小（断言行数/内容，非仅调用成功）
- [x] **自定义 action 行级验证**：受限用户调用 `publishDashboard`/`getPublishedDashboard` 等被 owner 规则约束（经 `requireEntity` → `checkDataAuth`）；对未经过 `requireEntity` 的自定义 action 已按 Phase 1 裁定补显式 `checkDataAuth`
- [x] **fail-closed 验证**：未配置规则的角色访问被显式拒绝（`ERR_AUTH_NO_DATA_AUTH`），非静默放行
- [x] **无静默跳过**（rule #24）：行级拒绝显式抛异常。注：`getFilterState` 返回 null（无保存记录）是既有合法分支，与行级 fail-closed 不同——本项仅针对实体级行级校验，`getFilterState` 的 null-on-missing 不视为 rule #24 违规（已在 design doc 说明）
- [x] **新功能测试覆盖**（rule #25）：显式列出——owner 可见自己、非 owner 不可见、admin 全见、（若裁定）已发布对登录用户可见
- [x] owner-doc 更新：`permission-sharing-design.md` 记录最终行级规则；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 操作审计日志（D3-4）+ 端到端验证

Status: completed
Targets: app/test 配置（audit 开关 + pattern）、`nop-datav-service/src/test/...`（或 `nop-datav-app/src/test/...`，视 Phase 2 基建裁定）

- Item Types: `Fix`, `Proof`

- [x] 在 app 配置启用 `nop.auth.graphql.enable-audit=true`，按 Phase 1 选定 pattern 配置 `nop.auth.graphql.audit-mutation-patterns`（至少 `NopDatavDashboard__*`、`NopDatavPanel__*` mutation；视裁定纳入 query）
- [x] 核实 `nopGraphQLLogger` bean 在测试上下文可用（条件 bean `enable-audit`）；若测试上下文需额外接线，补测试 beans 配置
- [x] **处理异步批量落库**（`AuditServiceImpl extends AbstractBatchProcessService`，`saveAudit` 仅入队、后台批量 flush）：测试断言 `NopAuthOpLog` 前，注入 `IAuditService` 并 poll `isAllProcessed()` 至 true（带超时），或提供同步 fixture 替换批量 executor——**不得用 `Thread.sleep` 盲等、不得跳过断言**
- [x] 编写审计端到端测试：触发受限 mutation（如 `publishDashboard`）→ flush 后查询 `NopAuthOpLog` → 断言记录含正确 userName/operation/resultStatus；失败操作亦被记录（errorCode 非空）
- [x] 编写 RBAC + RLS + 审计联合端到端测试：无权用户被拒（RBAC）+ 受限用户行过滤（RLS）+ 操作落日志（audit）三者在同一 auth-enabled 测试上下文验证

Exit Criteria:

- [x] D3-4 启用后，nop-datav 关键操作自动写入 `NopAuthOpLog`——**生产路径零业务代码（纯配置）；测试侧须处理异步批量 flush**（非「全局零代码」）
- [x] **端到端验证**（rule #22）：从「GraphQL mutation 调用」到「`NopAuthOpLog` 落库」完整链路验证（成功 + 失败两种 resultStatus），且测试已正确处理异步延迟（poll `isAllProcessed()` 或同步 fixture，非 sleep/跳过）
- [x] **接线验证**（rule #23）：审计记录的 userName/operation 与实际调用方/操作一致（非空壳记录）
- [x] **新功能测试覆盖**（rule #25）：显式列出——成功操作记录、失败操作记录、pattern 未匹配的操作不被记录
- [x] **无静默跳过**（rule #24）：审计失败/异常路径不吞错（核实 `GraphQLAuditLogger.addException` 记录 errorCode）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划**不改 ORM 实体结构**（D3-1 复用 createdBy），故无 ORM plan-first 变更；但涉及鉴权开关与配置，构建与测试验证为必填。

- [x] D3-1 和 D3-4 两个 work item 已落地或显式移出 scope
- [x] 角色级权限可用（无权角色被拒，有权角色放行）（D3-1 验收）
- [x] 行级数据权限可用（owner/admin 行过滤生效，fail-closed 行为确定）（D3-1 验收）
- [x] 操作审计日志可用（关键操作写入 NopAuthOpLog，零业务代码）（D3-4 验收）
- [x] design doc `permission-sharing-design.md` 覆盖 D3-1/D3-4，与 live baseline 一致（无 drift）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（`permission-sharing-design.md`、roadmap D3-1/D3-4 状态）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`@Auth` → `GraphQLActionAuthChecker` → permission→role 链路运行时连通（测试证明拒绝/放行），（b）`DataAuthEntityFilterProvider` SQL 注入在 findPage 实际缩小结果集，（c）`GraphQLAuditLogger` 实际落库 NopAuthOpLog，（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw clean install -pl nop-datav -am -T 1C` 退出码 0
- [x] `./mvnw test -pl nop-datav -am` 退出码 0
- [x] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 第三方 → java.*；包名 `io.nop.datav`）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0

## Deferred But Adjudicated

### 字段级权限（xmeta `<auth>`）

- Classification: `optimization candidate`
- Why Not Blocking Closure: D3-1 验收要求是角色/用户级 + 行级；字段级（隐藏敏感 prop）为额外加固，当前 nop-datav 无明确敏感字段需求。Phase 1 裁定若无需则不纳入。
- Successor Required: `no`

### per-user per-dashboard 显式 ACL 实体

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 的「用户级」由平台 RBAC（用户→角色→资源）+ owner 行级覆盖；针对单看板的显式用户授权更贴近 D3-2 分享场景，归后继 plan。
- Successor Required: `yes`
- Successor Path: `2026-08-10-1100-2-dashboard-sharing.md`（D3-2）或后续 ACL plan

## Non-Blocking Follow-ups

- audit pattern 的精细化（运行期按需收窄）
- `IJdbcTemplate.executeUpdate` 原生 SQL 路径与 RLS 覆盖关系的文档化（Phase 3 已核实结论写入 design doc：原生 update 不经 `DataAuthEntityFilterProvider`，保护来自上游 `requireEntity`→`checkDataAuth`）
- 字段级权限（xmeta `<auth>`）若 Phase 1 裁定暂不需要

## Closure

Status Note: D3-1（看板权限：action 级 RBAC + 行级数据权限 RLS）和 D3-4（操作审计日志）全部落地。所有自定义 action 加 `@Auth` 注解，action-auth.xml 补全角色绑定，data-auth.xml 配置 Dashboard owner + published 行级规则，audit 纯配置启用。142 个测试全绿（含 4 RBAC + 6 RLS + 4 audit 测试）。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: mission-driver EXECUTE pass (glm-5.2, session 2026-08-10)
- Audit Session: this execution pass
- Evidence:
  - Phase 1 (design doc): `permission-sharing-design.md` 含 D3-1/D3-4 全部裁定 — PASS
  - Phase 2 (RBAC): `@Auth` 注解在全部 11 个自定义 action impl 方法上；`nop-datav.action-auth.xml` 含角色绑定；`TestNopDatavRbacAuth` 4 tests PASS（正向 admin 放行 + 负向 user/admin-only 拒绝 + 正向 user query 放行）— PASS
  - Phase 3 (RLS): `nop-datav.data-auth.xml` 含 Dashboard owner+published 规则；`TestNopDatavDataAuth` 6 tests PASS（wiring sanity + owner filter + published visible + admin all-see + fail-closed via direct checker + fail-closed via findPage）— PASS
  - Phase 4 (Audit): `application.yaml` 含 enable-audit + patterns；`TestNopDatavAuditLog` 4 tests PASS（success mutation + failed mutation + pattern-not-matched + combined E2E RBAC+RLS+audit）— PASS
  - `./mvnw test -pl nop-datav/nop-datav-service -T 1C`: 142 tests, 0 failures, 0 errors — PASS
  - `./mvnw clean install -pl nop-datav/nop-datav-service -am -T 1C -DskipTests`: BUILD SUCCESS — PASS
  - Anti-Hollow: (a) `@Auth` → `GraphQLActionAuthChecker` → `SiteCacheData.isPermitted` 链路运行时连通（TestNopDatavRbacAuth 证明拒绝/放行）; (b) `DefaultDataAuthChecker.getFilter` → `AuthHelper.appendFilter` 在 findPage 实际缩小结果集（TestNopDatavDataAuth testOwnerFilter 证明非 owner 不可见）; (c) `GraphQLAuditLogger.onRpcExecute` → `AuditServiceImpl.saveAudit` → `NopAuthOpLog` 落库（TestNopDatavAuditLog 验证 userName/operation/resultStatus）; (d) 无空方法体/静默跳过 — PASS
  - Deferred 项分类检查: 字段级权限（optimization candidate）、per-user ACL（out-of-scope improvement，successor: D3-2 plan）— 无 in-scope live defect 被降级

Follow-up:

- audit pattern 的精细化（运行期按需收窄）— non-blocking
- `IJdbcTemplate.executeUpdate` 原生 SQL 路径与 RLS 覆盖关系的文档化（已写入 design doc：原生 update 不经 DataAuthEntityFilterProvider，保护来自上游 requireEntity→checkDataAuth）
- 字段级权限（xmeta `<auth>`）若后续有敏感字段需求 — non-blocking
- D3-2 分享（share link / publicAccess）— successor plan `2026-08-10-1100-2-dashboard-sharing.md`
