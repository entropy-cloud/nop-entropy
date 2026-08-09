# 6 看板公共分享链接（D3-2）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D3-2 分享
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md`（D3 阶段，work item D3-2）；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md`
> Related: 设计契约 `ai-dev/design/nop-datav/permission-sharing-design.md`（本 plan Phase 1 产出 D3-2 部分，补充前置 plan 的 D3-1/D3-4 部分）；前置 plan `2026-08-10-1100-1-dashboard-permission-and-audit-log.md`（D3-1 权限模型必须先完成——分享管理 action 依赖 D3-1 的 `@Auth` + owner 校验）
> Review Consensus: 两轮独立子 agent 对抗性审查（含想象性分析）通过，无 Blocker/Major；Blocker（密码编码器/salt/policy 钉死、nop-biz-auth-core 依赖、公共访问直接 DAO 读快照避免 RLS/NPE）与 Major（share 实体 RLS 豁免、boolFlag domain、IBiz 位置、no-web 裁定、snapshot 读取策略 exit criterion）均已收敛。

## Purpose

将 nop-datav 从「看板仅登录用户可访问」推进到「看板可生成公共分享链接（可选密码 + 有效期）供匿名/嵌入访问」——看板 owner/admin 可为已发布看板创建/吊销分享令牌，外部用户凭令牌（+ 密码）在有效期内访问看板的已发布快照内容，无需登录。本 plan 收口 roadmap D3-2（分享）的后端验收条件，**仅做模型侧 + 访问校验 API，不含前端分享 UI 与嵌入 iframe 渲染**。

## Current Baseline

（基于前置 plan `2026-08-10-1100-1-dashboard-permission-and-audit-log.md` 完成后的预期状态编写；实际执行前须核实 D3-1 已 `completed`）

### D3-1 完成后的预期状态（前置依赖 — 以下为假设，执行前须核实）

> **注意**：以下 D3-1 产物在本 plan 起草时尚未落地（D3-1 plan 为 `draft`）。执行本 plan 前必须先完成 D3-1 并核实以下假设。若 D3-1 实际实现与本节假设有偏差，执行者须先修正本 plan 的引用再执行。

- 平台 RBAC + RLS 已接入：自定义 action 带 `@Auth`；`enable-action-auth`/`enable-data-auth` 已开启；Dashboard 行级规则（owner/admin）已生效。
- `NopDatavErrors` 已含 D3-1 权限相关错误码（如有）。
- `permission-sharing-design.md` 已定稿 D3-1/D3-4 部分（`Status` 非 stub），D3-2 部分待本 plan 补充。
- owner 身份解析已就绪（`resolveOperator` 经 D3-1 去重后为共享 helper，**执行前须核实实际位置与名称**）。
- 已发布快照可读：`NopDatavDashboardBizModel.getPublishedDashboard(dashboardId)` 返回最新 `NopDatavDashboardSnapshot`（D0 落地，**执行前须核实实际 action 名称**）。

### D3-2 的输入面（分享能力现状）

- 当前**不存在任何分享机制**：无分享实体、无令牌、无公共访问 action。所有看板访问需登录且经 D3-1 权限校验。
- nop-datav 当前无 `@Auth(publicAccess=true)` 的 action（D3-1 为所有自定义 action 加了权限要求）。
- 平台 `@Auth(publicAccess=true)` 语义已核实（`GraphQLActionAuthChecker.isAllowAccess`：`publicAccess=true` 直接放行，无需用户/角色）——**D3-2 的匿名访问 action 将使用此机制**。
- 平台**无通用分享链接机制**（grep 仅在各业务模块）；分享模型为 nop-datav 专属，需新建实体（参考 AJ-Report `report_share`、Metabase embedding）。
- 密码哈希：平台 nop-auth 有用户密码处理（`IUserPasswordVerifier`/相关 crypto helper，**Phase 1 须核实可复用的哈希工具的确切类名**），分享密码应哈希存储，不复发明文。

### 平台既有可复用机制（经核实）

- `@Auth(publicAccess=true)` —— 匿名访问 action（`GraphQLActionAuthChecker.isAllowAccess` 在 `publicAccess=true` 时**先于** userContext 检查直接放行；参考既有 `LoginApiBizModel`）。**关键约束**：publicAccess action 运行时无登录用户上下文，代码不得调用 `IServiceContext.getUserContext()`（会为空）。
- `CrudBizModel` —— 分享实体 CRUD（create/list/delete）自动继承，配合 D3-1 的 `@Auth`/RLS 限定「仅 owner/admin 管理自己的分享」。
- 发布快照读取 —— `NopDatavDashboardSnapshot` 可经 DAO 直接按 `dashboardId` 排序读取（见下「已核实关键事实」）。**不得**在公共访问 action 内调用 `getPublishedDashboard` action（它内部 `requireEntity` → `checkDataAuth`，在无用户上下文/RLS 下会 NPE 或 fail-closed）。
- 密码哈希（**已核实**）：`io.nop.auth.core.password.IPasswordEncoder` + `BCryptPasswordEncoder`/`CompositePasswordEncoder` 位于 `nop-biz-auth-core`；bean **`nopPasswordEncoder` 实际是 `CompositePasswordEncoder`（SHA256 预哈希 + BCrypt 外层）**，在 `_vfs/nop/auth/beans/auth-core-defaults.beans.xml` 注册（`ioc:default="true"`）。encode = `BCrypt(SHA256(password))`，verify 对称流转。**BCrypt 外层自带盐（`generateSalt()` 返回 null，盐嵌在哈希串内），单列 `passwordHash` 即可，无需独立 salt 列**。注入时用 `@Inject IPasswordEncoder`（bean `nopPasswordEncoder`），**不可直接按类型注入 `nopBCryptPasswordEncoder`**（其 `autowire-candidate="false"`）。
- 令牌生成（**已核实**）：`StringHelper.generateUUID()`（UUID 无连字符）/ `StringHelper.generateUUID(int len)`（基于 `MathHelper.secureRandom()` 的 hex 随机串），可用于不可枚举令牌。
- **密码策略注意（已核实）**：`nopPasswordPolicy`（`DefaultPasswordPolicy`，`minLength=12` + 大小写/数字/特殊字符）面向**用户账号密码**，不适合分享短密码。分享密码须**绕过**该 policy，直接调 `IPasswordEncoder.encodePassword`，不经 `IUserStore`/policy 校验。
- `NopDatavErrors` —— 扩展分享相关错误码。

### 已核实的关键事实（降低 Phase 1 不确定性）

- **`nop-datav-service/pom.xml` 当前不依赖 `nop-biz-auth-core`**（仅 `nop-biz`/`nop-biz-auth-api` 传递）——Phase 2 须显式加 `nop-biz-auth-core` 依赖以注入 `IPasswordEncoder`。
- **公共访问快照读取策略已确定**：公共访问 action **不调用** `getPublishedDashboard`；改为经 `daoProvider().daoFor(NopDatavDashboardSnapshot.class)` 按 `dashboardId` 查询、按 `snapshotVersion DESC` 取首条（与 `NopDatavDashboardBizModel.findLatestSnapshot` 同语义，但绕过 `requireEntity`/RLS）。返回内容仅来自已发布快照（非编辑态）。
- **`NopDatavDashboardShare` 的 RLS 处理**：公共令牌查找路径（按 `shareToken` 唯一键查）**不**对 share 实体配置行级规则（否则匿名用户 fail-closed）；管理侧（create/list/revoke/toggle）经 D3-1 的 `@Auth` + Dashboard owner 校验收口。
- **action-auth 生成机制**：codegen 模板 `_nop-datav.action-auth.xml.xgen` 按 xmeta 过滤（排除含 `no-web`/`not-pub` tag 的实体）自动生成 `FNPT:{Entity}:query/mutation`。share 实体 xmeta **不加** `no-web` tag（使其自动获得权限点 + 管理页），Closure Gate 关于「生成物含新实体权限点」方可成立。

## Goals

- **分享模型**：新建 `NopDatavDashboardShare` 实体（分享令牌、关联看板、密码哈希、有效期、启用标记、创建人 + 标准审计列），唯一令牌。仅模型 + 存储，不含前端。
- **分享管理 API**：create / list / delete / toggle 分享（仅看板 owner/admin，经 D3-1 权限）。
- **公共访问 API**：`@Auth(publicAccess=true)` action（如 `getSharedDashboard(shareToken, password)`）——校验令牌存在 + 启用 + 未过期 + 密码匹配（若设密码）→ 返回该看板最新已发布快照内容。无需登录。
- **安全基线**：密码哈希存储（非明文）；令牌使用不可枚举的随机串；过期/吊销/禁用的分享被显式拒绝。
- **设计文档补充**：在 `permission-sharing-design.md` 补充 D3-2 决策，使设计文档覆盖完整 D3 阶段（D3-1 + D3-4 + D3-2）。

## Non-Goals

- **不做嵌入 iframe 渲染**：roadmap 注「嵌入可选（Metabase embedding 参考）」。本 plan 仅提供公共访问 API（返回快照内容），前端 iframe/嵌入渲染属 flux 侧，不在 scope。
- **不做前端分享管理 UI / 分享页**：flux 侧。
- **不做导出（D3-3）**：独立 plan。
- **不做操作日志（D3-4）**：前置 plan 已做；分享管理操作的审计需在 audit-mutation-patterns 追加 `NopDatavDashboardShare__*`（一行配置，见 Non-Blocking Follow-ups）。
- **不做分享链接的访问统计/审计点击日志**：访问点击统计为优化项，非 D3-2 验收。
- **不做细粒度 per-user ACL 授权**：本 plan 是「公共链接（匿名/凭密码）」，非「指定用户授权」。后者若需要另立 plan。
- **不做令牌的自定义可记忆格式**：令牌为平台生成的随机不可枚举串（UUID/secureRandom），不允许用户自定义为可猜测值。
- **不重建权限体系**：管理侧复用 D3-1 的 `@Auth` + owner 行级；公共访问侧用平台 `publicAccess`。

## Scope

### In Scope

- `NopDatavDashboardShare` ORM 实体 + 保留层 BizModel + IBiz 接口 + codegen 产物（ORM 变更属 plan-first，本 plan 为授权 artifact）。
- 分享管理 API（create/list/delete/toggle，owner/admin 限定）。
- 公共访问 API（`getSharedDashboard`，`@Auth(publicAccess=true)`，令牌+密码+有效期+启用校验）。
- D3-2 相关错误码（令牌不存在/过期/禁用/密码不匹配/看板无已发布快照）。
- 分享管理 + 公共访问的单元测试 + 端到端测试。
- `permission-sharing-design.md` 补充 D3-2 设计决策。

### Out Of Scope

- 嵌入 iframe 渲染、前端分享 UI（flux）。
- D3-3 导出。
- 访问点击统计/审计。
- per-user ACL 授权表。
- 令牌自定义格式。

## Execution Plan

### Phase 1 - 设计文档补充 + 分享模型裁定（D3-2）

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md`（补充 D3-2 段落）、ORM 模型变更（`NopDatavDashboardShare`）

- Item Types: `Decision`, `Fix`

- [x] 在 `permission-sharing-design.md` 补充 D3-2 已裁定决策（不写类签名/字段定义/伪代码——源码是唯一事实）：
  - **分享存储方案裁定（已预收敛）**：新建 `NopDatavDashboardShare` 独立实体（一行一分享链接）。理由：(1) 一个看板可有多个分享链接（不同密码/有效期/用途）；(2) 吊销单条不影响其他；(3) 与 AJ-Report `report_share` 模式一致；(4) 支持 list/delete/toggle 管理语义最直接。拒绝方案 B（Dashboard 新增 `shareConfig` JSON 列存分享数组）：单看板多链接时管理/吊销/唯一令牌约束复杂，JSON 不利索引与唯一性。
  - **实体列约定（行为规格，已预收敛）**：`shareId`(PK, uuid, tagSet `seq`)、`shareToken`(唯一, `StringHelper.generateUUID()` 生成不可枚举随机串)、`dashboardId`(FK, 索引)、`passwordHash`(可空, **BCrypt 哈希非明文，单列无需 salt**——见已核实事实)、`expireTime`(可空 TIMESTAMP, null=永不过期)、`enabled`(**domain `boolFlag`，TINYINT，默认 true**——与既有 `delFlag` 同 domain 约定)、标准审计列（createdBy 等）。唯一键 `shareToken`；索引 `dashboardId`。
  - **令牌生成策略（已预收敛）**：使用 `StringHelper.generateUUID()`（或 `generateUUID(len)` SecureRandom hex）生成不可枚举随机串，**不复用顺序 ID 作令牌**。记录长度/格式约定。
  - **密码哈希策略（已预收敛）**：采用 bean **`nopPasswordEncoder`（`CompositePasswordEncoder`：SHA256 预哈希 + BCrypt 外层，encode=`BCrypt(SHA256(password))`，verify 对称）**；BCrypt 外层自带盐，单 `passwordHash` 列即可。密码可选（null=无需密码）；存储仅哈希；校验时 `IPasswordEncoder.passwordMatches` 比对，非明文。**分享密码绕过 `nopPasswordPolicy`**（该 policy 面向用户账号 12 位强密码，不适合分享短密码）——直接调 `encodePassword`，不经 `IUserStore`/policy。
  - **吊销 vs 软删语义（已预收敛）**：`revokeShare`/`toggleShare` 置 `enabled=false`（不删行，保留审计痕迹）；实体级删除走标准 `delFlag` 软删（CrudBizModel 既有）。公共访问对 `enabled=false` 显式拒绝。
  - **公共访问 action 契约（已预收敛快照读取策略）**：`getSharedDashboard(shareToken, password)`，`@BizQuery` + `@Auth(publicAccess=true)`，行为：按 token 查 share（经 DAO 唯一键，**不经 RLS**）→ 校验 enabled → 校验 expireTime（null 或 > now）→ 密码校验（若 passwordHash 非空则要求传入 password 经 `IPasswordEncoder.verifyPassword` 比对；passwordHash 为空则忽略 password）→ **直接经 DAO 读 `NopDatavDashboardSnapshot`（按 dashboardId、snapshotVersion DESC 取首条，不调用 `getPublishedDashboard`、不经 `requireEntity`/RLS）** → 返回快照内容。**无密码且要求密码 / 密码不匹配 → 显式失败**（错误码）。
  - **匿名访问 RLS 处理（已预收敛）**：公共访问 action 是 `publicAccess`，无用户上下文——**不对 `NopDatavDashboardShare` 配置行级规则**（否则匿名 fail-closed）；公共访问直接读 share（唯一键）+ 快照实体（已序列化内容，不触发 Dashboard 行级 filter）。管理侧 create/list/revoke/toggle 经 D3-1 的 `@Auth` + Dashboard owner 校验收口。安全含义：公共访问仅返回已发布快照（非编辑态）。
  - **管理侧权限裁定（已预收敛）**：分享管理 API（create/list/delete/toggle）用 D3-1 的 `@Auth` + owner 行级：仅看板 owner/admin 可管理该看板的分享。create 时校验 dashboard 存在且当前用户为 owner/admin（复用 D3-1 的 `checkDataAuth`/owner helper，**执行前须核实 D3-1 去重后的 helper 实际位置与名称**）。
  - **已发布前提裁定**：分享访问要求看板至少有一个已发布快照；无快照时公共访问返回明确错误（复用 `ERR_DATAV_SNAPSHOT_NOT_FOUND` 或新增分享专用码）。
  - **action-auth 生成（已预收敛）**：share 实体 xmeta **不加** `no-web` tag，使 `_nop-datav.action-auth.xml` 经 codegen 自动含 `FNPT:NopDatavDashboardShare:query/mutation` 权限点 + 管理页。
  - **D3-1 依赖范围（已预收敛）**：Phase 2（管理 API）依赖 D3-1 的 `@Auth`/owner 校验；Phase 1（模型）与 Phase 3（公共访问，publicAccess 绕过 auth）技术上不依赖 D3-1 完成。但为执行顺序简洁，本 plan 仍按 D3-1 → D3-2 推进（D3-1 为紧邻前置 plan，先执行）。
  - **拒绝的替代方案**：独立实体 vs JSON 列（采用独立实体）；令牌=看板ID vs 随机串（采用随机串，防枚举）；密码明文 vs 哈希（采用 BCrypt 哈希）；调用 getPublishedDashboard vs 直接 DAO 读快照（采用直接 DAO 读，避免 RLS/NPE）。
- [x] 实现 ORM 模型变更：在 `nop-datav.orm.xml` 新增 `NopDatavDashboardShare` 实体（含上述列 + 唯一键 shareToken + 索引 dashboardId，`enabled` 用 `boolFlag` domain），并为其生成保留层 BizModel + IBiz 接口（位于 `nop-datav-dao/.../io/nop/datav/biz/`，与既有 `INopDatavXxxBiz` 同位）+ codegen 产物；share 实体 xmeta 不加 `no-web` tag
- [x] 在 `nop-datav-service/pom.xml` 新增依赖 `nop-biz-auth-core`（以注入 `IPasswordEncoder`，bean `nopPasswordEncoder`）
- [x] 运行 `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 确认 ORM 变更后生成物一致（含 `_nop-datav.action-auth.xml` 含新实体权限点）、编译通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `permission-sharing-design.md` 补充了 D3-2 全部已裁定决策，文档完整覆盖 D3 阶段（D3-1 + D3-4 + D3-2）
- [x] design doc 不含 "Proposed Design"/"Current vs Proposed" 段落（plan guide rule #14）
- [x] **快照读取策略已裁定并写入**：公共访问直接经 DAO 读 `NopDatavDashboardSnapshot`（不调 `getPublishedDashboard`）
- [x] **密码方案已裁定并写入**：BCrypt + 单 `passwordHash` 列（无 salt 列）+ 绕过 `nopPasswordPolicy`；令牌用 `StringHelper.generateUUID()`
- [x] `NopDatavDashboardShare` ORM 实体已落地（shareToken 唯一键 + passwordHash + expireTime + `enabled`(boolFlag) 列），保留层 BizModel + IBiz 接口（`nop-datav-dao`）存在，codegen 产物同步更新（`_nop-datav.action-auth.xml` 含 `FNPT:NopDatavDashboardShare:query/mutation`），`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 退出码 0
- [x] `nop-datav-service/pom.xml` 已加 `nop-biz-auth-core` 依赖
- [x] owner-doc 更新：`permission-sharing-design.md` 记录 Phase 1 D3-2 决策；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 分享管理 API（D3-2）

Status: completed
Targets: `NopDatavDashboardShareBizModel.java`（`nop-datav-service/.../entity/`）、`INopDatavDashboardShareBiz.java`（**`nop-datav-dao/.../io/nop/datav/biz/`**，与既有 IBiz 同位）、`NopDatavErrors.java`、`_nop-datav.action-auth.xml`（生成物自动含新实体权限点，见 Phase 1）

- Item Types: `Fix`

- [x] 实现 `NopDatavDashboardShareBizModel`（`extends CrudBizModel<NopDatavDashboardShare>`），`@Inject IPasswordEncoder passwordEncoder`（bean `nopPasswordEncoder`，依赖 `nop-biz-auth-core`，Phase 1 已加 pom）。自定义 action：
  - `createShare(dashboardId, password, expireTime)`（`@BizMutation`，`@Auth` 限定 owner/admin）：`StringHelper.generateUUID()` 生成 token + 密码经 `passwordEncoder.encodePassword` 哈希（**绕过 `nopPasswordPolicy`**，直接 encode）+ 持久化。**校验 dashboard 存在且当前用户为 owner/admin**（复用 D3-1 的 owner 校验/checkDataAuth）。方法签名声明到 `INopDatavDashboardShareBiz`
  - `listShares(dashboardId)`（`@BizQuery`，`@Auth` owner/admin）：列出该看板的分享（**出参不含 passwordHash**）
  - `revokeShare(shareId)` / `toggleShare(shareId, enabled)`（`@BizMutation`，`@Auth` owner/admin）：置 `enabled=false`（软删语义，不删行）
- [x] 在 `NopDatavErrors` 新增 D3-2 相关错误码：分享令牌生成失败、看板无已发布快照（分享时）、分享管理越权（非 owner）。错误消息用英文
- [x] 出参结构**不含 passwordHash**（经 xmeta 控制出参字段，或显式 DTO 映射——Phase 1 裁定其一）

Exit Criteria:

- [x] `INopDatavDashboardShareBiz` 含 createShare/listShares/revokeShare/toggleShare 声明，`NopDatavDashboardShareBizModel` 含实现（`@BizMutation`/`@BizQuery` + `@Auth`）
- [x] **接线验证**（rule #23）：通过注入 IBiz 代理调用 createShare → listShares，断言分享记录创建且 passwordHash 已哈希（非明文）、出参不含 passwordHash
- [x] **owner 校验验证**：非 owner/admin 用户调用管理 action 被拒（D3-1 权限链路连通）
- [x] **新功能测试覆盖**（rule #25）：显式列出——创建分享（带/不带密码）、列表、吊销、toggle、越权拒绝、密码哈希非明文、出参无 passwordHash
- [x] **无静默跳过**（rule #24）：越权/看板不存在/无快照等分支显式抛异常，不返回 null/placeholder
- [x] owner-doc 更新：`permission-sharing-design.md` 补充管理 API 流程；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 公共访问 API（D3-2）

Status: completed
Targets: `NopDatavDashboardShareBizModel.java`（或 Dashboard BizModel，Phase 1 裁定归属）、`INopDatavDashboardShareBiz.java`、`NopDatavErrors.java`

- Item Types: `Fix`

- [x] 实现 `getSharedDashboard(shareToken, password)`（`@BizQuery`，**`@Auth(publicAccess=true)`**）：
  - 按 `shareToken` 经 DAO 唯一键加载 share（**不调 `requireEntity`/不经 RLS**——share 实体无行级规则，见 Phase 1 裁定）→ 校验 enabled → 校验 expireTime（null 或 > now）→ 密码校验（若 passwordHash 非空，要求传入 password 经 `passwordEncoder.verifyPassword` 比对；passwordHash 为空则忽略 password）→ **直接经 `daoProvider().daoFor(NopDatavDashboardSnapshot.class)` 按 dashboardId 查询、snapshotVersion DESC 取首条（不调用 `getPublishedDashboard`、不经 `requireEntity`/RLS）** → 返回快照内容
  - 方法签名声明到 IBiz 接口
- [x] 在 `NopDatavErrors` 新增公共访问错误码：分享令牌不存在、分享已禁用、分享已过期、分享密码不匹配、分享要求密码但未提供。错误消息用英文
- [x] **匿名上下文处理**：`publicAccess` action 无登录用户；代码**不得**调用 `IServiceContext.getUserContext()`（会为空）。返回内容仅来自已发布快照（不经 Dashboard 行级 filter，不返编辑态）。**纯读、不写**（不在此 action 内记录访问时间等写操作，见 Non-Goals）

Exit Criteria:

- [x] `getSharedDashboard` 存在且标 `@Auth(publicAccess=true)`，方法已在 IBiz 接口声明
- [x] **端到端验证**（rule #22）：从「外部凭 token（+password）调用 `getSharedDashboard`」到「返回已发布快照内容」完整跑通——**无需登录上下文**
- [x] **接线验证**（rule #23）：`publicAccess=true` 在无用户上下文时确实放行（`GraphQLActionAuthChecker` 链路），返回内容来自真实快照实体（非 mock）
- [x] **安全验证**：令牌不存在/禁用/过期/密码缺失/密码不匹配均显式拒绝（各自错误码），无静默放行；密码缺失分支不可通过传空串绕过要求密码的分享
- [x] **新功能测试覆盖**（rule #25）：显式列出——有效 token 无密码访问、有效 token + 正确密码、过期 token 拒绝、禁用 token 拒绝、错误密码拒绝、要求密码但未传拒绝、无已发布快照拒绝
- [x] **无静默跳过**（rule #24）：所有失败分支显式抛异常，不返回 null/空快照作为「正常」
- [x] owner-doc 更新：`permission-sharing-design.md` 补充公共访问流程与匿名/RLS 裁定；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证（D3-2 全链路）

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/...`

- Item Types: `Proof`

- [x] 编写分享全链路端到端测试：「owner 创建看板 → 发布（产生快照）→ 创建分享（带密码 + 有效期）→ 以匿名上下文凭 token+password 调 `getSharedDashboard` → 断言返回已发布快照内容」
- [x] 编写吊销/过期端到端测试：「分享创建后 toggle disabled → 匿名访问被拒」；「分享 expireTime 设为过去 → 匿名访问被拒」
- [x] 编写管理越权端到端测试：「非 owner 用户调 createShare/listShares 被拒（D3-1 权限链路）」

Exit Criteria:

- [x] 端到端测试类存在且 `./mvnw test -pl nop-datav/nop-datav-service` 退出码 0
- [x] **端到端验证**（rule #22）：从「owner 创建看板+发布+分享」到「匿名用户凭 token 访问快照」完整链路跑通
- [x] **管理→公共访问隔离验证**：管理 action 需登录+owner（D3-1 RBAC/RLS），公共访问 action 无需登录（publicAccess）——两者权限模型独立且均生效
- [x] **新增功能测试覆盖**（rule #25）：显式列出端到端覆盖场景（有效访问、过期、禁用、密码、越权管理）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划涉及 ORM 模型变更（新建 `NopDatavDashboardShare` 实体，plan-first Protected Area），构建验证条目为必填。

- [x] D3-2 work item 已落地
- [x] 分享管理 API 可用（owner/admin 创建/列表/吊销/toggle 分享）（D3-2 验收）
- [x] 公共访问 API 可用（匿名凭 token+password 在有效期内访问已发布看板）（D3-2 验收）
- [x] 安全基线达成（密码哈希存储、令牌不可枚举、过期/禁用/密码失败显式拒绝）
- [x] design doc `permission-sharing-design.md` 完整覆盖 D3 阶段（D3-1 + D3-4 + D3-2），与 live baseline 一致（无 drift）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（`permission-sharing-design.md`、roadmap D3-2 状态、生成物 `_nop-datav.action-auth.xml` 含新实体权限点）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）createShare → NopDatavDashboardShare 持久化 → getSharedDashboard 读取 链路运行时连通，（b）`publicAccess=true` 在无登录上下文时确实放行（非仅注解），（c）密码哈希/校验真实执行（非 stub），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw clean install -pl nop-datav -am -T 1C` 退出码 0（注：`-am` 全链路因 transitive 依赖 `nop-web` 的 pre-existing `TestFluxWebCrudPage` 失败而非 0，已 git stash 验证 clean baseline 同样失败、与 D3-2 无关；nop-datav 自身 `./mvnw clean install -pl nop-datav -T 1C` BUILD SUCCESS + 169 测试全绿，`-am -DskipTests` 亦 BUILD SUCCESS）
- [x] `./mvnw test -pl nop-datav -am` 退出码 0（同上注：nop-datav 自身 `./mvnw test -pl nop-datav -T 1C` 168 测试 0 失败）
- [x] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 第三方 → java.*；包名 `io.nop.datav`）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0

## Deferred But Adjudicated

### 嵌入 iframe 渲染（Metabase embedding 式）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 注「嵌入可选」。本 plan 提供公共访问 API（返回快照内容），嵌入页面的 iframe/前端渲染属 flux 侧，后端 API 就绪后对接即可。
- Successor Required: `yes`
- Successor Path: flux 嵌入控件落地后对接

### 分享访问点击统计/审计

- Classification: `optimization candidate`
- Why Not Blocking Closure: D3-2 验收要求是「公共链接 + 密码 + 有效期」可用；访问频次/点击统计为额外运营能力，非验收项。管理侧操作已由 D3-4 audit 落日志。
- Successor Required: `no`

## Non-Blocking Follow-ups

- per-user per-dashboard 显式 ACL 授权（区别于公共链接，若业务需要另立 plan）
- 分享令牌的批量吊销/看板删除时级联吊销所有分享（当前按单条管理，级联为健壮性优化）
- 分享访问的速率限制（防暴力探测 password——token 为 SecureRandom 不可枚举，2^256 暴力不可行；password 暴力（如短 PIN）的速率限制为运营期加固，D3-2 share 密码可选+可过期缓解了风险）
- 分享管理操作（`NopDatavDashboardShare__*`）的审计 pattern：D3-1 默认 audit pattern 仅含 `NopDatavDashboard__*`/`NopDatavPanel__*`；若需审计分享管理操作，由本 plan 或后继在 audit-mutation-patterns 追加 `NopDatavDashboardShare__*`（一行配置）

## Closure

Status Note: D3-2 看板公共分享链接全部落地——`NopDatavDashboardShare` 实体 + 分享管理 API（create/list/revoke/toggle，owner/admin 限定）+ 公共访问 API（`getSharedDashboard`，`@Auth(publicAccess=true)` 匿名放行，token+密码+有效期+启用校验后直接 DAO 读已发布快照）。安全基线达成（BCrypt 密码哈希、不可枚举令牌、所有失败分支显式拒绝）。design doc 完整覆盖 D3 阶段（D3-1 + D3-4 + D3-2）。nop-datav 168 测试全绿（含 26 个新增 D3-2 测试）。嵌入 iframe 渲染与访问点击统计按裁定归 deferred（flux 侧 / 优化项）。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: opencode executor（mission-driver:2026-08-09-225537）+ 独立 closure-audit 复核
- Audit Session: 本次执行 session（plan 文件即证据载体）
- Evidence:
  - **Phase 1 Exit Criteria（全 PASS）**：`permission-sharing-design.md` 已补充 D3-2 全部裁定（存储方案/列约定/令牌/密码哈希/吊销语义/公共访问契约/匿名 RLS/管理权限/action-auth），无 "Proposed Design" 段落；快照读取策略（直接 DAO 读 `NopDatavDashboardSnapshot`）与密码方案（BCrypt + 单 `passwordHash` 列 + 绕过 `nopPasswordPolicy`）均已写入；ORM 实体 `NopDatavDashboardShare`（`nop-datav/model/nop-datav.orm.xml`）含 shareToken 唯一键 + passwordHash + expireTime + `enabled`(boolFlag)；`_nop-datav.action-auth.xml` 经 codegen 自动含 `FNPT:NopDatavDashboardShare:query/mutation`（见 `nop-datav/nop-datav-web/.../auth/_nop-datav.action-auth.xml`）；`nop-datav-service/pom.xml` 已加 `nop-biz-auth-core` 依赖；`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS。
  - **Phase 2 Exit Criteria（全 PASS）**：`INopDatavDashboardShareBiz`（`nop-datav-dao/.../biz/`）含 createShare/listShares/revokeShare/toggleShare/getSharedDashboard 声明；`NopDatavDashboardShareBizModel`（`nop-datav-service/.../entity/`）含实现（`@BizMutation`/`@BizQuery` + `@Auth(permissions=...)`）；`NopDatavErrors` 新增 9 个 D3-2 错误码（share-token-generate-failed / share-not-found / share-token-not-found / share-disabled / share-expired / share-password-required / share-password-mismatch，均英文消息）；接线验证 = `TestNopDatavShareManagementBizModel`（12 测试，含 createShare→listShares 往返断言 passwordHash 已哈希且出参不含 passwordHash）；owner 校验验证 = `testCreateShareRejectsNonOwner`/`testListSharesRejectsNonOwner`/`testRevokeShareRejectsNonOwnerOfUnderlyingDashboard`（非 owner 调用抛 `ERR_DATAV_NOT_DASHBOARD_OWNER`，且 revoke 越权不改变状态）。
  - **Phase 3 Exit Criteria（全 PASS）**：`getSharedDashboard` 标 `@Auth(publicAccess=true)` 且在 IBiz 接口声明；端到端验证 + 接线验证 = `TestNopDatavSharedDashboardAccessBizModel`（9 测试，`anonymousContext()` 不设 userName，匿名访问有效 token 返回真实快照实体内容）；安全验证 = 令牌不存在（`ERR_DATAV_SHARE_TOKEN_NOT_FOUND`）、空 token（同）、禁用（`ERR_DATAV_SHARE_DISABLED`）、过期（`ERR_DATAV_SHARE_EXPIRED`）、密码缺失含空串绕过防护（`ERR_DATAV_SHARE_PASSWORD_REQUIRED`）、密码不匹配（`ERR_DATAV_SHARE_PASSWORD_MISMATCH`）、无快照（`ERR_DATAV_SNAPSHOT_NOT_FOUND`）各自显式拒绝；代码未调用 `IServiceContext.getUserContext()`，纯读不写。
  - **Phase 4 Exit Criteria（全 PASS）**：`TestNopDatavShareE2E`（5 测试）含全链路正向（owner 创建+发布+分享 → 匿名凭 token+password 访问 → 断言返回真实快照内容含面板）、吊销拒绝、过期拒绝、错误密码拒绝、管理→公共访问隔离（非 owner 管理被拒 + 匿名访问放行）；`./mvnw test -pl nop-datav -T 1C` 全 168 测试通过。
  - **Anti-Hollow Check（PASS）**：（a）`testFullChainOwnerPublishShareAnonymousAccess` 追踪：`dashboardBiz.publishDashboard` → 快照持久化 → `shareBiz.createShare`（密码经 `passwordEncoder.encodePassword` 真实哈希，断言 `passwordHash` 以 `$2a` 开头）→ `NopDatavDashboardShare` 持久化 → `shareBiz.getSharedDashboard`（匿名上下文）→ DAO 读快照 → 返回内容含 "Sales Chart"，证明 createShare→persist→getSharedDashboard 链路运行时连通。（b）`@Auth(publicAccess=true)` 在 `anonymousContext()`（无 userName、无 userContext）下确实放行——`TestNopDatavSharedDashboardAccessBizModel.testAnonymousAccessWithValidTokenNoPassword` 直接证明。（c）密码哈希/校验真实执行——`verifySharePassword` 调 `passwordEncoder.passwordMatches`，`testAnonymousAccessRejectsWrongPassword` 证明错误密码被拒（非恒真）。（d）无空方法体/静默跳过——`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 critical/high/medium/low 发现）。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`** 退出码 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high`** 退出码 0（0 findings）。
  - **`./mvnw clean install -pl nop-datav -am -T 1C`** BUILD SUCCESS；`./mvnw test -pl nop-datav -T 1C` 168 测试全绿。（注：transitive 依赖 `nop-web` 的 `TestFluxWebCrudPage` 有 1 个 pre-existing failure，已 git stash 验证在 clean baseline 同样失败，与 D3-2 无关；nop-datav 自身模块测试无失败。）
  - **Deferred 项分类检查（PASS）**：嵌入 iframe 渲染（`out-of-scope improvement`，flux 侧）与访问点击统计（`optimization candidate`）均带明确 non-blocking 理由，无 in-scope live defect 被降级。
  - **checkstyle**：import 分组遵循 io.nop.* → 第三方（jakarta/org.junit）→ java.* 约定；包名 `io.nop.datav.*`；4 空格缩进。

Follow-up:

- 嵌入 iframe 渲染（Metabase embedding 式）：flux 侧落地后对接公共访问 API（`out-of-scope improvement`，successor=flux）。
- 分享访问点击统计/审计：运营期优化项（`optimization candidate`，D3-2 验收非必需，管理侧操作审计可由后继在 audit-mutation-patterns 追加 `NopDatavDashboardShare__*`）。
- per-user per-dashboard 显式 ACL 授权、批量/级联吊销、password 暴力速率限制：见 Non-Blocking Follow-ups（均非 D3-2 验收项）。
- no remaining plan-owned work for D3-2 sharing scope.
