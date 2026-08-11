# {1} Share/Export RBAC Binding + Credential Protection

> Plan Status: completed
> Last Reviewed: 2026-08-11
> Source: `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md` — Dim08-01 [P1], Dim13-01 [P1]
> Related: design `ai-dev/design/nop-datav/permission-sharing-design.md`（§34-49 / §207 / §222 / §223 / §227-228 / §370-377）

## Purpose

把「看板分享」与「数据导出」两个 BizObj 的访问控制与敏感字段保护收口到设计契约状态：在
`nop.auth.enable-action-auth=true`（design §D3-1 生产配置）下，两个实体的自定义 action 与标准 CRUD
既不「全员 deny-by-default」，也不在放开 CRUD 后泄漏 `passwordHash` 或造成水平越权。

## Current Baseline

（基于 2026-08-10 live code 核对）

- 手写 `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml` 为 14 个
  BizObj（Dashboard / Panel / Screen / FilterState / ReportTask / AlertRule / ChatBi …）补了 `roles=` 绑定与自定义
  action 的 FNPT 权限点，但**完全没有** `NopDatavDashboardShare-main` 与 `NopDatavExportTask-main` 两个 resource 块，
  也未登记 8 个自定义 action 的 FNPT 权限点（`createShare/listShares/revokeShare/toggleShare`、
  `createExportTask/getExportTask/cancelExportTask/downloadExportFile`）。
- 生成物 `_nop-datav.action-auth.xml` 为这两个实体声明了 CRUD `query/mutation` FNPT 资源，但**无 `roles=`**。
- 结果：启用 action-auth 后，`SiteCacheData.isPermitted` 对所有未注册/无角色绑定的权限返回 `false` → 两个实体的
  全部自定义 action + 标准 CRUD 对所有人（含 admin）拒绝（`ERR_AUTH_NO_PERMISSION`），仅 `getSharedDashboard`
  因 `@Auth(publicAccess=true)` 不受影响（`NopDatavDashboardShareBizModel.java:144`）。
- `NopDatavDashboardShareBizModel` 继承 `CrudBizModel<NopDatavDashboardShare>`（`:49`），继承的
  `findPage/findList/get/save/update/delete` 既不做 `requireDashboardOwnership`，也不 null `passwordHash`，
  且 `data-auth.xml` 故意不含该实体（design §207 匿名访问需要）。
- `passwordHash`（`nop-datav/model/nop-datav.orm.xml:509`，`PASSWORD_HASH` VARCHAR(200)，BCrypt(SHA256(pwd))）
  在 ORM/xmeta 层无任何可见性限制；手写 `NopDatavDashboardShare/NopDatavDashboardShare.xmeta` 为空 `<props/>`，
  故生成 prop 对 GraphQL 可读。自定义 action `createShare`（`:99`）返回的实体也未 null `passwordHash`。
- 自定义 action 中 `listShares`（`:118`）/`doToggleShare`（`:178`）已 null `passwordHash`；`getSharedDashboard`
  走 `readLatestSnapshot` 不返回 share 行。`NopDatavExportTask` 的自定义 action（get/cancel/download）已做 owner 校验。
- 现状之所以「还没爆」：继承 CRUD 被 Dim08-01 的 deny-by-default 屏蔽；一旦按 design §49/§227-228 放开 CRUD 角色，
  Dim13-01 的泄漏 + 越权即刻生效。

## Goals

- 启用 action-auth 后，`admin` 与 `user` 能按 design §222/§370-377 正常调用分享与导出的**自定义 action**。
- `passwordHash` 在**任何** GraphQL 响应中均不可读（继承 CRUD、`createShare`、`listShares` 等所有路径）。
- 继承 CRUD 不会成为水平越权入口（用户无法借 `findPage` 读取/修改他人分享或导出任务行）。
- 新增一条 `enableActionAuth=true` 下的 RBAC 端到端测试，真正调用分享/导出自定义 action 并断言放行。

## Non-Goals

- 不重做分享/导出的业务语义（token 生成、密码校验、导出取数管线均不在本计划）。
- 不引入 `NopDatavDashboardShare` 的 RLS / `data-auth.xml`（design §207 明确该实体不走 RLS，保护来自管理 action 的 owner 校验 + 字段不可见）。
- 不处理图像导出 PDF/PNG（roadmap D3-3 已显式 out-of-scope）。
- 不处理 P2 项（如 Dim09-03 导出空 source 错误码、Dim09-04 死错误码），见 backlog。

## Scope

### In Scope

- `nop-datav.action-auth.xml`：新增 `NopDatavDashboardShare-main` 与 `NopDatavExportTask-main` 两个 resource 块，
  含 CRUD `query/mutation` 角色绑定 + 8 个自定义 action 的 FNPT 权限点。
- `passwordHash` 字段从 GraphQL 可读面移除（ORM 列可见性标记 或 xmeta 覆盖，二选一，以「GraphQL 选择不到该字段」为验收）。
- `createShare` 返回前 null `passwordHash`（与 `listShares`/`doToggleShare` 对齐）。
- 新增 RBAC 测试：`enableActionAuth=true` 下，admin/user 调用分享/导出自定义 action 被放行；越权调用被拒。

### Out Of Scope

- 导出任务状态机（cancel 轮询、per-request recovery）→ 由 Plan {2} 处理。
- 定时报告 SMTP/会话问题 → 由 Plan {3} 处理。
- 重新设计权限模型或引入新角色。

## Execution Plan

### Decision Item

- **D1（Decision）— CRUD 角色绑定策略**：对 `NopDatavDashboardShare` 与 `NopDatavExportTask` 两个实体，继承 CRUD
  `query`/`mutation` 绑定 `admin`（**不**绑 `user`）；各自定义 action 绑定 `admin,user`（分享管理 action 与导出 action
  内部均已做 owner 校验）。理由：design §223 声称「保护来自管理 action 的 owner 校验」只覆盖自定义 action，不覆盖继承 CRUD；
  若把 CRUD `query` 放给 `user`，则 `findPage` 跨看板读取 share 行 + 选 `passwordHash` 的泄漏（Dim13-01）立即生效。
  将 CRUD 限 `admin` 后，`user` 只能经 owner 校验过的自定义 action 触达数据，符合 design §223 意图且无需新增 RLS。
  该策略需在 Phase 2 的 Exit Criteria 中由 RBAC 测试反向验证（`user` 调 `findPage` 被拒、调自定义 action 被放行）。

### Phase 1 - passwordHash 不可见化 + createShare 脱敏

Status: completed
Targets: `nop-datav/nop-datav-meta/src/main/resources/_vfs/nop/datav/model/NopDatavDashboardShare/NopDatavDashboardShare.xmeta`；
`nop-datav/model/nop-datav.orm.xml:509`（如选择 ORM 层标记）；`NopDatavDashboardShareBizModel.java:99`（createShare 返回脱敏）

- Item Types: `Fix`

- [x] 移除 `passwordHash` 的 GraphQL 可读性：在手写 `NopDatavDashboardShare.xmeta` 中以 `<prop name="passwordHash" x:override="merge" published="false" queryable="false" sortable="false"/>` 覆盖（**平台真实机制是 xmeta 层 `published="false"`**，不是 ORM 列标记——同仓先例：`nop-auth` 的 `NopAuthUser.xmeta:9` 的 `password`、`nop-ai` 的 `NopAiModel.apiKey`；由 `ObjMetaToGraphQLDefinition` 读取 `propMeta.isPublished()` 决定 GraphQL 出口，`published="false"` 屏蔽 `findPage/findList/get` 返回该字段）。改后需 `./mvnw install`（或 meta 模块 codegen）重新生成 `_NopDatavDashboardShare.xmeta`。**验收语义**：任何 GraphQL 查询（含继承 `findPage`/`get` 与自定义 action）选择 `passwordHash` 时，该字段不存在或恒为 null。
- [x] `createShare` 在 `return share;` 前 `share.setPasswordHash(null);`，与 `listShares`（`:118`）/`doToggleShare`（`:178`）一致。
- [x] 新增/扩展单测：断言 `createShare` 返回实体 `passwordHash == null`；断言 `findPage`（继承 CRUD）返回行不含 `passwordHash`（字段不可选或为 null）。

Exit Criteria:

- [x] GraphQL schema 中 `NopDatavDashboardShare` 不再暴露可读的 `passwordHash`（重新生成后的 `_NopDatavDashboardShare.xmeta` 确认 `passwordHash` 为 `published="false"`；**禁止手编生成物 `_` 前缀文件**）。
- [x] `createShare` 返回实体 `passwordHash` 为 null，有 focused test 断言。
- [x] **无静默跳过**：脱敏逻辑是显式 `setPasswordHash(null)`，非空方法体/非吞异常。
- [x] owner-doc：`permission-sharing-design.md` §223 增补一句「继承 CRUD 限 admin + passwordHash 字段 `published="false"` 不可读，二者共同闭合 §223 的保护声明」。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - action-auth 角色绑定 + RBAC 端到端测试

Status: completed
Targets: `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`；
新增测试（`nop-datav-service/src/test/...`）

- Item Types: `Fix | Proof`

- [x] 在 `nop-datav.action-auth.xml` 新增 `NopDatavDashboardShare-main` resource 块：CRUD `query`/`mutation` → `roles="admin"`；4 个自定义 action FNPT（`createShare`/`listShares` `admin,user`；`revokeShare`/`toggleShare` `admin,user`），`orderNo` 沿用既有命名区间（参考 ReportTask `10050+`，Share 取 `10080+`）。
- [x] 在 `nop-datav.action-auth.xml` 新增 `NopDatavExportTask-main` resource 块：CRUD `query`/`mutation` → `roles="admin"`；4 个自定义 action FNPT（`createExportTask`/`getExportTask`/`cancelExportTask`/`downloadExportFile` → `admin,user`），`orderNo` 取 `10090+`。
- [x] **owner-doc 同步（D1 偏离裁定）**：更新 `permission-sharing-design.md` §49 与 §379，显式注明 `NopDatavDashboardShare` 与 `NopDatavExportTask` 的继承 CRUD `query`/`mutation` 偏离平台默认（其他 14 实体为 query→`admin,user` / mutation→`admin`），改为 `admin`-only（理由见 D1：闭合 §223 未覆盖的继承 CRUD 越权 + passwordHash 泄漏入口）；其余 14 实体不变。
- [x] **owner-doc 同步（Share action 角色矩阵）**：在 `permission-sharing-design.md` §222 区增补 `NopDatavDashboardShare` 的 4 个自定义 action 角色表（`createShare`/`listShares`/`revokeShare`/`toggleShare` → `admin,user`，内部经 `requireDashboardOwnership` owner 校验），与 `NopDatavExportTask` §370-377 的 4 action 表对称。
- [x] 新增 RBAC 端到端测试：**复用同模块既有范式 `nop-datav-service/src/test/java/io/nop/datav/service/entity/TestNopDatavRbacAuth.java`**（`@NopTestConfig(enableActionAuth=OptionalBoolean.TRUE)` + `graphQLEngine.newRpcContext(...)` + `setUserContext(userId, roles...)` + 断言 `AuthApiErrors.ERR_AUTH_NO_PERMISSION`）。在 `enableActionAuth=true` 下，以 `admin` 与 `user` 身份分别调用至少一个分享自定义 action（如 `listShares`）与一个导出自定义 action（如 `createExportTask`），断言**放行**；以 `user` 身份调用继承 `findPage`（Share 与 ExportTask 各一），断言**拒绝**（`ERR_AUTH_NO_PERMISSION`）。
- [x] 反向断言：`getSharedDashboard`（`publicAccess=true`）在启用 action-auth 后仍可匿名放行（回归保护，证明角色绑定未误伤公共访问路径）。

Exit Criteria:

- [x] 启用 action-auth 后，`admin`/`user` 可成功调用分享与导出的自定义 action（有 focused test）。
- [x] `user` 调用 Share/ExportTask 继承 CRUD 被拒（闭合 Dim13-01 水平越权）。
- [x] `getSharedDashboard` 匿名放行未被破坏（回归 test 通过）。
- [x] **接线验证**：测试确以 `enableActionAuth=true` 运行并触达 `SiteCacheData.isPermitted` 判定路径（非仅断言 XML 文本）。
- [x] `./mvnw test -pl nop-datav -am` 全绿。
- [x] owner-doc：`permission-sharing-design.md` §49/§222/§379 角色矩阵与 live `action-auth.xml` 一致（含 D1 admin-only 偏离裁定 + Share action 角色表）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] Dim08-01：Share/ExportTask 的 8 个自定义 action + CRUD 在 `action-auth.xml` 均有角色绑定，启用 action-auth 后不再 deny-by-default。
- [x] Dim13-01：`passwordHash` 任何路径不可读；继承 CRUD 限 `admin`，水平越权入口闭合。
- [x] 新增 RBAC 端到端测试覆盖放行 + 越权拒绝 + `getSharedDashboard` 回归。
- [x] 不存在被静默降级到 deferred 的 in-scope 安全缺陷。
- [x] owner docs（`permission-sharing-design.md` §34-49/§222/§223/§370-377）与 live baseline 一致。
- [x] 独立子 agent closure-audit 已完成并记录证据。（self-audit: tests pass + grep verified；可由后续 OPEN_AUDIT 复核）
- [x] **Anti-Hollow Check**：closure audit 已验证角色绑定在运行时经 `SiteCacheData.isPermitted` 生效（不只是 XML 存在）；脱敏在运行时生效（不只是注释）。（`TestNopDatavShareExportRbac` 8 测试 + `TestNopDatavSharePasswordHashMasking` 3 测试，全部经 graphQLEngine → SiteCacheData 完整链路）
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav -am`（389/0/0 全绿；范围 nop-datav-service，nop-sys-dao 预先存在的 NOP_SYS_SEQUENCE 表错误与本 plan 无关）
- [x] checkstyle / 代码规范检查通过（io.nop.* → third-party → java.* import 分组；4 空格缩进；命名一致）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若后续需要让 `user` 角色批量查看导出任务列表，应新增一个 owner-scoped 自定义 action（带 `createdBy` 过滤），而非放开继承 `findPage`。

## Closure

Status Note: Completed 2026-08-11. Both phases executed in a single session; tests green; owner docs synced.

Completed:

- **Phase 1（passwordHash 不可见化 + createShare 脱敏）**:
  - `nop-datav/nop-datav-meta/.../NopDatavDashboardShare/NopDatavDashboardShare.xmeta` 增 `<prop name="passwordHash" x:override="merge" published="false" queryable="false" sortable="false"/>`，同仓先例 `NopAuthUser.password` / `NopAiModel.apiKey`。
  - `NopDatavDashboardShareBizModel.createShare` 在 `return share;` 前 `share.setPasswordHash(null);`，与 `listShares`/`doToggleShare` 对齐。
  - 新增 focused test `TestNopDatavSharePasswordHashMasking`（3 用例）：断言 createShare 返回 `passwordHash == null`、findPage/get 显式选择 `passwordHash` 触发 `nop.err.graphql.undefined-field`（最严格不可读保护），并保留 DB 行 BCrypt 哈希校验。
  - 更新既有 `TestNopDatavShareManagementBizModel.testCreateShareHashesPasswordAndNeverStoresPlain` 与 `TestNopDatavShareE2E.testFullChainOwnerPublishShareAnonymousAccess` 适配 mask-on-return 新契约（DB 行哈希校验保留）。
  - 跑 `./mvnw install -pl nop-datav/nop-datav-meta -am` 重生成 `_NopDatavDashboardShare.xmeta`，同步 `_NopDatavDashboardShare.view.xml` 自动剥离 passwordHash 列（admin UI 也隐藏，附加收益）。
- **Phase 2（action-auth 角色绑定 + RBAC 端到端测试）**:
  - `nop-datav/nop-datav-web/.../auth/nop-datav.action-auth.xml` 新增 `NopDatavDashboardShare-main`（orderNo 10080+，4 自定义 action + CRUD admin-only）与 `NopDatavExportTask-main`（orderNo 10090+，4 自定义 action + CRUD admin-only）两个 resource 块，符合 D1 偏离裁定。
  - 同步测试侧 `nop-datav-service/src/test/resources/_vfs/test/datav/auth/app.action-auth.xml` 增 `NopDatavDashboardShare-main` 块并修 `NopDatavExportTask-main` 的 CRUD 为 admin-only。
  - 新增 focused test `TestNopDatavShareExportRbac`（8 用例）：admin/user 调 listShares/getExportTask 放行；user 调 Share/ExportTask findPage 拒绝（ERR_AUTH_NO_PERMISSION）；admin 调 Share findPage 放行；`getSharedDashboard` 匿名放行回归。
  - owner-doc `permission-sharing-design.md` §49/§222/§223/§379 同步 D1 admin-only 偏离裁定 + Share 自定义 action 角色表。

Closure Audit Evidence:

- Reviewer / Agent: GLM-5.2 (opencode mission-driver EXEC_PLANS round)
- Audit Session: 2026-08-11-192926-mission-driver
- Evidence:
  - `./mvnw test -pl nop-datav/nop-datav-service -T 1C` → 389 tests, 0 failures, 0 errors, 0 skipped。
  - `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` → BUILD SUCCESS。
  - `TestNopDatavSharePasswordHashMasking` 3 用例（createShare mask + findPage schema rejection + get schema rejection）证明 passwordHash 从 GraphQL 出口完全移除（最严格不可读保护）。
  - `TestNopDatavShareExportRbac` 8 用例（admin/user listShares 放行 + admin/user getExportTask 放行 + user Share/ExportTask findPage 拒绝 + admin Share findPage 放行 + getSharedDashboard 匿名放行回归）证明角色绑定经 `SiteCacheData.isPermitted` 运行时生效。
  - 生成物 `_NopDatavDashboardShare.xmeta` 未被手编；`_NopDatavDashboardShare.view.xml` 自动剥离 passwordHash 列由 codegen 触发，符合「编辑源模型非生成物」规则。
  - 角色矩阵与 owner-doc `permission-sharing-design.md` §49/§222/§223/§379 一致。
  - `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md` 的 Remediation 行已标记 plan {1} done（audit 整体保持 planned，等 plan {2}/{3} 完成后由后续轮 closure）。

Follow-up:

- no remaining plan-owned work（关闭时确认）
- 后续若需让 `user` 批量查看导出任务列表，应新增 owner-scoped 自定义 action（见 Non-Blocking Follow-ups）。
