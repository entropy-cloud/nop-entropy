# 1 nop-datav 权限收口：Panel 锚点修复与子实体/ChatBI/快照面收敛

> Plan Status: completed
> Mission: nop-datav
> Execution Order: 1 of 3（本批 3 份 remediation plan；先修权限面——它是最暴露的越权读写面，且 P0-01 是 open-audit AR-3 资源放大器的直接放大前提）
> Last Reviewed: 2026-08-16
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析）达成共识，Blocker/Major 全部修复后转 active；审查记录见 `ai-dev/logs/2026/08-15.md`
> Source: `ai-dev/audits/nop-datav/2026-08-15-1913-multi-audit-nop-datav.md` P0-01 / P0-02 / P1-01 / P1-02 / P1-03 / P1-10（权限收口不闭环系统性主题）；仓库内正确先例 `NopDatavExportTaskBizModel.requireSourceAccess`
> Related: `ai-dev/plans/nop-datav/2026-08-10-2025-1-share-export-rbac-and-credential-protection.md`（Share/ExportTask 同类收敛先例，已完成）；`ai-dev/design/nop-datav/permission-sharing-design.md`

## Purpose

把 nop-datav 权限面的 6 个已确认越权/表面积缺陷收口：Panel 标准 CRUD mutation 越权（P0-01）、面板数据查询权限锚点错位（P0-02）、5 个只读子实体泄露他人草稿（P1-01）、FilterState 通用 CRUD 绕过 userName 隔离（P1-02）、ChatBI 数据集可见性未接线（P1-03）、快照/告警状态表暴露全量 mutation 面绕过领域单写点（P1-10）。全部修复后，「user 只能触达自己拥有的 + 已发布的」这一设计声明在 CRUD 面与自定义 action 面都成立，并有 RBAC/data-auth 回归测试锚定。

## Current Baseline

以下事实 2026-08-15 已对照 live repo 核实：

- `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`：
  - `FNPT:NopDatavPanel:query` 与 `FNPT:NopDatavPanel:mutation` 均为 `admin,user`（`:112-113`）——全模块唯一 mutation=`admin,user` 的实体（FilterState 除外，`:94-95` 同样 query+mutation=`admin,user`）。
  - 5 个只读子实体 query=`admin,user`：`NopDatavDashboardSnapshot:query`（`:73`）、`NopDatavDashboardTab:query`（`:80`）、`NopDatavDatasetRef:query`（`:87`）、`NopDatavScreenWidget:query`（`:194`）、`NopDatavScreenSnapshot:query`（`:201`）；对应 mutation 均 `admin`（`:74/:202`，AlertState mutation `admin` 在 `:272`）。
- `nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/auth/nop-datav.data-auth.xml` 仅有 6 个 obj（Dashboard/Screen/ReportTask/ReportDelivery/AlertRule/AlertState，`:5/:19/:36/:47/:61/:72`）——Panel、FilterState、5 个子实体均无行级规则；平台 `DefaultDataAuthChecker`（nop-auth）对 `objAuth == null` 直接放行（审计已逐环独立复核）。
- `NopDatavPanelBizModel.java`：仅覆写 `doDeleteEntity`（alert 级联停用，`:63-70`）；`getPanelData`（`:93-105`）、`refreshPanel`（`:107-118`）、`resolveLinkage`（`:120-131`）、`resolveJump`（`:133-144`）四个自定义 action 均以 `requireEntity(Panel)` 为权限锚点，**无 panel→dashboard 归属校验**；Panel 无 RLS → 校验恒通过。panelId 可经 `NopDatavPanel__findPage` 全量枚举（query=`admin,user`，无过滤）。
- 仓库内正确先例（修法锚点）：
  - `NopDatavExportTaskBizModel.requireSourceAccess`（`:377-402`）：`checker.isPermitted("NopDatavDashboard", action, dashboard, context)` 按 sourceType 锚定父实体行级权限。
  - `NopDatavDashboardOwnerGuard`（`io/nop/datav/service/NopDatavDashboardOwnerGuard.java:22`）：owner 守卫工具类。
  - 批量查询路径正确锚定 Dashboard：`NopDatavDashboardBizModel`（getDashboardData 先 requireEntity(Dashboard) 走 RLS）。
- ChatBI：`DatavListDatasetsExecutor.java:97-99` 注释「owner RLS 由 DAO 层/查询上下文处理」为不实陈述（查询仅 `status=ACTIVE` 过滤，`:99`）；`DatavQueryDatasetExecutor.java:104-108` 按入参 datasetSid 直接执行数据集 SQL。**nop-report 侧数据集权限现状（审查已核实）**：现行 `nop-report/model/nop-report.orm.xml`（8 实体）不含 `NopReportDatasetAuth`——过时残骸包括生成物 `nop-report/nop-report-dao/src/main/java/io/nop/report/dao/entity/_gen/_NopReportDatasetAuth.java`、非生成保留类 `NopReportDatasetAuth.java`、空壳 `NopReportDatasetAuthBizModel`（无 beans.xml 注册，对比 `NopReportDatasetBizModel` 确注册于 `_service.beans.xml`）、空壳 xbiz/xmeta/pages 与 action-auth 残留条目（均死代码，无表、无 DDL、无消费方）；nop-report 自身 data-auth 为空文件、NopReportDatasetBizModel 为裸 CrudBizModel（无消费先例）。`NopReportDataset` 实体有 `createdBy` 列（选项 B 的落点）。
- D4 修复路径前提：在本 plan scope（仅 nop-datav 文件）内，可落地的主案是 **createdBy/admin 可见性过滤**（选项 B）；「消费 NopReportDatasetAuth ACL」（选项 A）需要 nop-report 跨模块变更（恢复 ORM 实体 + DDL + codegen），**不在本 plan scope**——若 Phase 1 裁定选 A，必须显式移出并立 successor，不得默认可达。
- 薄 BizModel：`NopDatavDashboardSnapshotBizModel` / `NopDatavScreenSnapshotBizModel` / `NopDatavAlertStateBizModel` 均为裸 `CrudBizModel`（各约 11-16 行），`__save/__update/__delete/__batchDelete` 全量暴露（mutation=admin）；手写 xbiz 定制层为空壳未裁剪。这些数据 feeds 公共匿名访问路径 `getSharedDashboard`（消费 snapshotContent）与 `rollbackDashboard`。
- 设计文档 `ai-dev/design/nop-datav/permission-sharing-design.md` §51 明文裁定「其余 14 实体 mutation→`admin`」（Panel/FilterState 被遗漏）；§53-65 声称「Panel 归属 Dashboard 已覆盖行级校验」与 live 行为不符（owner-doc drift，随本 plan 修正）；§69 声明 FilterState「仅查询/更新自己的记录」。
- 测试现状：`TestNopDatavRbacAuth`（经 `IGraphQLEngine` + `enableActionAuth` 走完整 @Auth→action-auth.xml 链）、`TestNopDatavDataAuth`、`TestNopDatavScreenDataAuth`、`TestNopDatavShareExportRbac` 为既有测试模式；**均未覆盖 Panel / FilterState / 5 子实体 / 快照 mutation 面 / ChatBI 数据集可见性**（审计确认测试缺口佐证越权未被发现）。

## Goals

- `FNPT:NopDatavPanel:mutation` 收敛为 `admin`（对齐设计 §51 与其余 14 实体），user 无法再经标准 CRUD 篡改/删除任意面板（P0-01）。
- getPanelData/refreshPanel/resolveLinkage/resolveJump 四个 action 在取得 Panel 后补 panel→Dashboard 归属校验（镜像 `requireSourceAccess` 先例）：非 owner 且看板未发布 → 拒绝；Dashboard 的 `createdBy OR publishStatus=10` RLS 在面板级入口同样生效（P0-02）。
- 5 个只读子实体 query 不再向 user 暴露他人草稿内容（P1-01，收敛方式经 Phase 1 裁定）。
- FilterState 通用 CRUD 不再绕过 userName 隔离（P1-02，设计 §69 语义落地：query 行级隔离 + mutation 收敛，经 Phase 1 裁定具体组合）。
- ChatBI `datav-list-datasets` / `datav-query-dataset` / 面板查询路径接入数据集可见性校验；不实注释修正（P1-03）。
- `NopDatavDashboardSnapshot__*` / `NopDatavScreenSnapshot__*` / `NopDatavAlertState__*` 的 mutation 面不再绕过领域唯一写入点（P1-10，裁剪方式经 Phase 1 裁定）。
- 每一收口都有负向 RBAC/data-auth 回归测试（经 `IGraphQLEngine` 全链），防止回退。

## Non-Goals

- P2 安全加固项（外链 scheme 白名单 P2-06、分享限流单节点残余 P2-07、CSV 公式注入 P2-08）——已在 follow-up backlog，不随本 plan。
- 不引入新的 RLS 框架/中间件；只用既有 action-auth + data-auth + BizModel guard 三种机制。
- 不改造前端权限交互（nop-datav-web pages 为生成物；后端收口后前端无需配合变更的判定在 Phase 1 逐项核实）。
- 不处理事务边界/资源上界（AR-1/AR-2/AR-3/P1-04/P1-05/P1-06 → plan 2）、DDL 物化/级联删除/错误码/测试卫生（P0-03/P1-07/08/09/11/12 → plan 3）。

## Scope

### In Scope

- `nop-datav.action-auth.xml`（Panel mutation、FilterState、5 子实体 query 的角色绑定调整）。
- `nop-datav.data-auth.xml`（FilterState 等新增行级规则，按 Phase 1 裁定）。
- `NopDatavPanelBizModel`（四 action 归属校验）。
- `DatavListDatasetsExecutor` / `DatavQueryDatasetExecutor`（数据集可见性校验 + 注释修正）；如裁定需要，`PanelDataBinder` 数据集可达性检查点。
- 快照表/AlertState 的 xbiz/xmeta mutation 面裁剪（按 Phase 1 裁定）。
- RBAC/data-auth 回归测试（扩展既有 4 个测试类或新增）。
- `ai-dev/design/nop-datav/permission-sharing-design.md` 修订（§53-65 错误陈述纠正 + 本轮收口裁定落档）。

### Out Of Scope

- Non-Goals 列出的全部方向；Panel query 角色绑定若 Phase 1 裁定维持现状，则其收敛不属本 plan（仅记录裁定）。

## Execution Plan

### Phase 1 - 收口策略裁定

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md`（裁定落档）

- Item Types: `Decision | Fix`（§53-65 owner-doc drift 纠正属已确认 drift，按 guide 规则 15 归 `Fix`，不得随 Decision 混同）

- [x] D1 Panel query 面裁定：`NopDatavPanel:query` 是否随 mutation 一并收敛（P0-02 的枚举向量根因是 `__findPage` 无过滤全量可读他人 panelConfig，与 P1-01 同类泄露）。裁定时必须核实运行时读路径不依赖 `Panel__findPage/__get`（getDashboardData/getPublishedDashboard 经 DAO 内部加载/快照）以及前端/编辑器消费面（nop-datav-web 生成 pages、flux 编辑器路径），给出「收敛 admin」或「保留 + 过滤」的明确结论与理由。**若倾向「保留 + 过滤/间接 RLS」分支，必须先核实机制可行性**：全仓 data-auth.xml 无跨实体嵌套过滤先例（filter 仅引用本 obj 列），「dashboardId→父表 RLS」能否用 data-auth DSL 表达未经验证，不可行时需 BizModel prepareQuery 覆写并说明理由
- [x] D2 5 个只读子实体 query 收敛方式裁定：收敛 `admin`（镜像 Share/ExportTask D1 先例，审计首选建议）或补 `dashboardId/screenId → 父表` 间接 RLS（机制可行性核实要求同 D1）；逐实体核实无 user 侧 GraphQL 消费（含生成 xbiz/xmeta 暴露面）
- [x] D3 FilterState 收口组合裁定：设计 §51（mutation→admin）与 §69（userName 隔离）同时成立的组合——倾向 query 保留 `admin,user` + data-auth `eq userName ${$context.userName}`、mutation 收敛 `admin`（用户写路径已由自定义 `saveFilterState` 覆盖）；给出最终组合
- [x] D4 ChatBI 数据集可见性模型裁定：**主案=选项 B**（`NopReportDataset.createdBy`/admin 过滤：list 枚举过滤 + query 执行前可达性校验 + 评估 `PanelDataBinder` 数据集可达性检查是否纳入）；`PanelDataBinder` 检查点必须给出**纳入/排除的 definitive 结论**——排除时须对照审计 P1-03「datav 全部查询路径」口径，论证面板路径经 Dashboard RLS（plan Phase 2 归属校验）传递授权成立，不得留「待评估」；选项 A（消费 `NopReportDatasetAuth` ACL）因需 nop-report 跨模块变更（实体已不在现行 ORM 模型，见 Baseline）默认不可达——仅在裁定显式立项 successor 时采用；同时修正 `DatavListDatasetsExecutor.java:97-99` 不实注释（`Fix`）
- [x] D5 快照表/AlertState mutation 面裁剪方式裁定，候选三选一（均须核实无合法 admin 消费，含测试与生成页面）：(a) 手写 xbiz 删除 mutation action；(b) xmeta 禁写；(c) BizModel 覆写 save/update/delete/batchDelete 方法抛结构化异常（guide 规则 24 标准做法）。**裁定必须附 schema 层不可达证据**（GraphQL operation 面真实移除或显式拒绝的验证方式）——xbiz `x:override="remove"` 与 xmeta 禁写在全仓均无先例，机制有效性未经验证，不能只核实「无消费」就假定「已移除」
- [x] Fix（owner-doc drift）：裁定结论写入 `permission-sharing-design.md`，同时纠正 §53-65「Panel 已覆盖行级校验」错误陈述，落档本轮各实体最终权限矩阵

Exit Criteria:

- [x] D1-D5 均有明确裁定并落档 `permission-sharing-design.md`；每条裁定引用 live 代码/消费面核实事实，非假设
- [x] §53-65 错误陈述已纠正，无两套裁定并存
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No new test required: 纯 Decision/文档 Phase，行为测试落在 Phase 2-5

### Phase 2 - Panel 锚点修复（P0-01 + P0-02）

Status: completed
Targets: `nop-datav.action-auth.xml`、`NopDatavPanelBizModel.java`、`TestNopDatavRbacAuth.java`、`TestNopDatavDataAuth.java`

- Item Types: `Fix`

- [x] Fix P0-01：`FNPT:NopDatavPanel:mutation` 角色绑定收敛为 `admin`；若 D1 裁定 query 一并收敛则同步执行（D1 裁定 = query 一并收敛，已同步执行，production + test action-auth 两处）
- [x] Fix P0-02：`getPanelData/refreshPanel/resolveLinkage/resolveJump` 在 `requireEntity(Panel)` 后补 panel→Dashboard 归属校验（镜像 `NopDatavExportTaskBizModel.requireSourceAccess` 既有实现：owner 或已发布放行，否则抛 `ERR_AUTH_NO_DATA_AUTH`——与先例一致的**数据权限**错误码；`ERR_AUTH_NO_PERMISSION` 仅用于 P0-01 的 RBAC 角色拒绝断言，两者不得混用）；ScreenWidget 侧如有对称入口一并核实处理（核实结论：Screen 全部自定义 action 均锚定 `requireEntity(Screen)`（有 RLS），无 widget 锚点对称入口，无需处理）
- [x] 负向 RBAC 回归：user 角色调 `NopDatavPanel__update/__delete/__save` → `ERR_AUTH_NO_PERMISSION`；admin 照常（扩展 `TestNopDatavRbacAuth`）（覆盖 `__update` mutation 拒绝 + `__findPage` query 拒绝（D1）+ admin `__update` 放行三态）
- [x] 归属校验回归：user A 对 user B 的未发布看板面板调 `getPanelData/refreshPanel/resolveLinkage/resolveJump` → 拒绝；对 B 的已发布看板面板 → 放行；owner 对自己的未发布看板面板 → 放行（扩展 `TestNopDatavDataAuth` 或新增 PanelDataAuth 测试）（四 action 拒绝态 + 已发布四 action 放行态 + owner 未发布放行态，全部经 `IGraphQLEngine` 全链）

Exit Criteria:

- [x] `nop-datav.action-auth.xml` 中 Panel mutation（及 D1 裁定的 query）不再绑定 `user`；生成物如受影响已同步重建（本 Phase 无 DDL 变更，预期无——`clean install -pl nop-datav -am -DskipTests` BUILD SUCCESS 无漂移）
- [x] 四 action 均有归属校验代码路径；越权请求返回结构化权限错误（非静默空结果）
- [x] 新增测试覆盖「非 owner 未发布 → 拒绝 / 已发布 → 放行 / owner → 放行」三态与 mutation 角色负向，全部经 `IGraphQLEngine` 全链执行
- [x] `./mvnw test -pl nop-datav -am` 通过（含新增测试）（service 模块 563/563 全绿）
- [x] `permission-sharing-design.md` 权限矩阵已同步（Phase 1 已建段落）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 子实体与 FilterState 收口（P1-01 + P1-02）

Status: completed
Targets: `nop-datav.action-auth.xml`、`nop-datav.data-auth.xml`、`TestNopDatavRbacAuth.java`、`TestNopDatavDataAuth.java`

- Item Types: `Fix`

- [x] Fix P1-01：按 D2 裁定对 `NopDatavDashboardSnapshot/DashboardTab/DatasetRef/ScreenWidget/ScreenSnapshot` 的 query 执行收敛（默认：query→`admin`）；如裁定补间接 RLS 则在 data-auth.xml 增补对应 obj 规则（D2 裁定 = 收敛 admin，production + test action-auth 两处已执行）
- [x] Fix P1-02：按 D3 裁定对 FilterState 执行收口（默认：query 保留 + data-auth `eq userName`；mutation→`admin`）（production/test data-auth.xml 增 FilterState obj + production/test action-auth mutation 收敛）
- [x] 负向回归：user 读取他人未发布看板的 tabConfig/DatasetRef/widgetConfig/snapshotContent → 不可达（收敛 admin 时为 `ERR_AUTH_NO_PERMISSION`；间接 RLS 时为空结果/拒绝，按裁定断言）；user 经 `NopDatavFilterState__findPage/__get` 仅见自己的记录、`__save/__delete` 被拒（5 子实体 findPage 拒绝 + FilterState findPage 行级隔离 + `__get` 他人行 ERR_AUTH_NO_DATA_AUTH + `__save` mutation 拒绝；admin 正向对照）
- [x] 正向回归：既有自定义 action（saveFilterState/getFilterState、看板运行时读路径 getDashboardData）行为不变（不因收口误伤合法路径）（全模块 568/568 全绿，含 TestNopDatavFilterStateBizModel/TestNopDatavDashboardDataBatchE2E 等既有路径）

Exit Criteria:

- [x] action-auth/data-auth 变更后，5 子实体与 FilterState 的越权路径全部有负向测试锚定（经 `IGraphQLEngine` 全链）
- [x] 既有合法路径（看板/大屏运行时加载、自定义 FilterState action）回归全绿
- [x] `./mvnw test -pl nop-datav -am` 通过（service 模块 568/568 全绿）
- [x] `permission-sharing-design.md` 权限矩阵已同步（Phase 1 已建段落）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - ChatBI 数据集可见性接线（P1-03）

Status: completed
Targets: `DatavListDatasetsExecutor.java`、`DatavQueryDatasetExecutor.java`、（按 D4 裁定）`PanelDataBinder.java`、`ai-dev/design/nop-datav/ai-design.md`

- Item Types: `Fix`

- [x] Fix P1-03：按 D4 裁定实现数据集可见性校验（主案选项 B：`NopReportDataset.createdBy`/admin 可见性）——`datav-list-datasets` 枚举结果按可见性过滤；`datav-query-dataset` 执行前校验目标数据集可达性，不可达抛结构化错误（错误码沿用/新增按仓库惯例）；不可达时显式失败而非静默过滤（list 侧过滤、query 侧拒绝）（新增 `ERR_DATAV_CHATBI_DATASET_NO_ACCESS`；list SQL 下推 createdBy 过滤 + 无身份 fail-closed；query/describe 执行前显式拒绝——describe 执行器为 D4 一致扩展，同属数据集 schema 泄露面；身份经 `ChatBiToolExecuteContext` operator+admin 显式传递，`ChatBiDatasetVisibility` 单点判定）
- [x] 修正 `DatavListDatasetsExecutor.java:97-99` 不实注释为真实机制描述（注释改为「可见性由本 executor 显式实施，nop-report 数据集无 DAO 层 RLS」）
- [x] 回归：user 对无权限数据集 `datav-query-dataset` → 拒绝；list 枚举不含不可见数据集；admin 全量可见；数据集 SQL 本身的参数化绑定行为不变（不碰 PanelSqlBuilder）。**测试口径**：ChatBI 校验落点在 tool executor（不在 @Auth→action-auth→data-auth→BizModel guard 链上），按仓库既有 ChatBI executor 直调单测模式（`TestDatavQueryDatasetExecutor` 等）+ 显式用户上下文构造验证可见性过滤；不要求经 GraphQL 全链（LLM mock 基建无先例）（+4 用例：list 三态过滤（alice 仅己/admin 全量/无身份空）、describe 拒绝+owner/admin 放行、query 拒绝带结构化错误码、query admin 跨 createdBy 放行）

Exit Criteria:

- [x] ChatBI 两个执行器均有可见性校验代码路径 + 正负向测试（按 D4 裁定方案构造测试数据——主案选项 B 下为不同 createdBy 的 `NopReportDataset` 行，无 ACL 表依赖）（list/query/describe 三 executor 均覆盖）
- [x] 不实注释已删除/改写
- [x] `ai-dev/design/nop-datav/ai-design.md`（或 D4 指定 owner doc）已登记数据集可见性契约与缓存键耦合提醒（open-audit AR-7 指出：一旦可见性按用户生效，`DashboardPanelQueryCache` 键必须按用户失效或绕过——该提醒必须在接入时显式落地或显式排除缓存路径）（§6 重写：可见性契约 + AR-7 缓存键硬约束提醒落档——当前可见性仅 ChatBI executor 路径生效、不触缓存；面板路径显式排除并论证）
- [x] `./mvnw test -pl nop-datav -am` 通过（service 模块 572/572 全绿，含 80 个 ChatBI 相关用例）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 快照/告警状态 mutation 面裁剪（P1-10）

Status: completed
Targets: （按 D5 裁定）手写 xbiz 或 xmeta：`NopDatavDashboardSnapshotBizModel` / `NopDatavScreenSnapshotBizModel` / `NopDatavAlertStateBizModel` 对应定制层、`TestNopDatavRbacAuth.java`

- Item Types: `Fix`

- [x] Fix P1-10：按 D5 裁定裁剪 `NopDatavDashboardSnapshot__save/__update/__delete/__batchDelete`、`NopDatavScreenSnapshot__*`（mutation）、`NopDatavAlertState__*`（mutation）的 API 暴露面（快照 append-only、AlertState 状态机唯一写入点为 AlertEvaluator）（D5 = 方案 c：新增共享基类 `NopDatavSingleWriterCrudBizModel` 覆写全部 13 个标准 mutation 入口抛结构化 `ERR_DATAV_SNAPSHOT_STD_MUTATION_NOT_ALLOWED` / `ERR_DATAV_ALERT_STATE_STD_MUTATION_NOT_ALLOWED`，三个 BizModel 改继承）
- [x] 负向回归：admin 调被裁剪的 mutation action → 不可达（action 不存在或明确拒绝，按裁剪机制断言）；`publishScreen/rollbackDashboard/getSharedDashboard` 等领域写入点照常（既有测试回归）（新增 `TestNopDatavSingleWriterMutationPruning` 6 用例：Snapshot save/update 拒绝、ScreenSnapshot delete 拒绝、AlertState update/batchDelete 拒绝（均断言结构化错误码，admin 角色经 action-auth 后到达 BizModel 守卫）+ Snapshot findPage 查询面回归；领域写入点回归由既有 publish/rollback/alert/share E2E 全绿覆盖）
- [x] 若裁剪影响生成的 I*Biz 接口/xbiz 生成物，按生成管线惯例同步重建（不手改 `_gen` 产物）（无影响：未改 ORM 模型/xmeta/I*Biz，`clean install -pl nop-datav -am -DskipTests` BUILD SUCCESS 无漂移）

Exit Criteria:

- [x] 三个实体的标准 mutation 入口从 GraphQL API 面移除或显式拒绝，领域单写点（publish/rollback/AlertEvaluator）不受影响（显式拒绝：13 入口全量覆写抛结构化错误码，负向测试经 `IGraphQLEngine` 全链锚定错误码）
- [x] 负向回归 + 领域写入点回归全绿
- [x] `./mvnw test -pl nop-datav -am` 通过；`./mvnw clean install -pl nop-datav -am -DskipTests` 生成管线无漂移（578/578 + BUILD SUCCESS）
- [x] `permission-sharing-design.md` 已登记裁剪裁定（Phase 1 D5 段落 + 最终权限矩阵 mutation 列「显式拒绝」）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P0-01/P0-02/P1-01/P1-02/P1-03/P1-10 六项 live defect 全部修复且有负向权限回归锚定（任一越权路径残留则不得关闭）（closure audit 逐项 PASS：Panel query/mutation 收敛、四 action 归属校验、5 子实体 query 收敛、FilterState data-auth+mutation 收敛、ChatBI 三 executor 可见性、13 mutation 入口显式拒绝；负向断言全部锚定错误码）
- [x] 分层验证口径成立：RBAC 角色面（action-auth 绑定）、data-auth 行级面、BizModel guard 面（Panel 归属校验）的回归均经 `IGraphQLEngine` 全链执行（@Auth→action-auth→data-auth→BizModel guard），非仅服务层直调；ChatBI tool executor 面按 executor 级测试 + 显式用户上下文验证（校验点不在 GraphQL guard 链上，不适用全链口径）（RbacAuth/DataAuth/SingleWriterMutationPruning 均经 graphQLEngine.newRpcContext→executeRpcAsync；ChatBI 经 executor 直调 + `ChatBiToolExecuteContext` 显式身份）
- [x] 既有合法路径（看板/大屏运行时、Share/ExportTask、自定义 action）回归全绿，无误伤（datav-service 578/578 全绿，含 publish/rollback/share/export/alert E2E）
- [x] `permission-sharing-design.md` §53-65 错误陈述已纠正、最终权限矩阵落档，无文档-代码漂移残留（closure audit 第 8 项 PASS：D1-D5 段落 + 17 实体矩阵 + drift 标注）
- [x] 不存在被降级为 follow-up 的 in-scope 越权缺陷（Deferred But Adjudicated = 无；closure audit 第 9 项 PASS）
- [x] 独立子 agent closure audit 已完成并写入 Closure 段落（含 Anti-Hollow 检查：归属校验真实被执行而非死代码）（fresh session `ses_ff7e5d880ffeElwhq5KgP50wUN`，verdict CLOSABLE，9/9 PASS）
- [x] `./mvnw test -pl nop-datav -am` 通过（BUILD SUCCESS，reactor 59 模块全绿，datav-service 578/578）
- [x] `./mvnw clean install -pl nop-datav -am -DskipTests` 通过（生成物同步，无漂移）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）

## Deferred But Adjudicated

（无——本 plan 范围内无允许延期的已确认缺陷）

## Non-Blocking Follow-ups

- open-audit AR-7 缓存键耦合：**已随本 plan Phase 4 显式裁定并落档**（`ai-design.md` §6：可见性仅 ChatBI executor 路径生效、不触 `DashboardPanelQueryCache`，缓存键无需按用户失效；未来引入面板路径 per-user 可见性时必须同时按用户失效或绕过缓存——硬约束提醒，非当前 debt）。
- P2-06/P2-07/P2-08 安全加固残余 → `ai-dev/backlog/nop-datav-audit-followups.md`。

## Closure

Status Note: 六项权限缺陷（P0-01/P0-02/P1-01/P1-02/P1-03/P1-10）全部修复并有负向回归锚定；D1-D5 裁定落档 permission-sharing-design.md（含 17 实体最终权限矩阵与 §53-65 owner-doc drift 纠正）；独立 closure audit 9/9 PASS（CLOSABLE）；全部 Closure Gates 勾选。生成管线无漂移（未改 ORM/xmeta/I*Biz，`_gen` 产物零触碰）。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，research-only）`ses_ff7e5d880ffeElwhq5KgP50wUN`
- Evidence:
  - P0-01 PASS：production `nop-datav.action-auth.xml:130-131` Panel query/mutation 均 `roles="admin"`；test mirror 同步（`app.action-auth.xml:59-66`）
  - P0-02 PASS（非死代码）：`NopDatavPanelBizModel.java:108-121` `requirePanelDashboardAccess`（加载父 Dashboard → `checker.isPermitted("NopDatavDashboard",...)` → 拒绝抛 `ERR_AUTH_NO_DATA_AUTH`），四 action 代码路径逐一核实（:133/:146/:162/:176，均在 requireEntity 之后）；三态测试经 IGraphQLEngine 全链（非 owner 未发布拒 / 已发布放行 / owner 放行）
  - P1-01 PASS：5 子实体 query 收敛 admin（production :77/:85/:93/:213/:224 + test mirror）；5 个 `__findPage` 负向断言 ERR_AUTH_NO_PERMISSION
  - P1-02 PASS：FilterState mutation=admin + data-auth `eq userName`（production `nop-datav.data-auth.xml:86-96` + 双侧镜像）；findPage 隔离 + `__get` 他人行拒 + `__save` 拒
  - P1-03 PASS：list SQL 下推 createdBy 过滤 + 无身份 fail-closed（`DatavListDatasetsExecutor:111-121`）；query/describe 执行前显式拒绝 `nop.err.datav.chatbi-dataset-no-access`（query :96-106 先于 doQuery、describe :71-80 先于 parseFields）；不实注释已移除；身份接线链完整（BizModel :125-126 → loop.run 四处 → `ChatBiToolCallingLoop:165` 构造带 operator+admin 的 context → executor 读取）；`ai-design.md` §6 契约 + AR-7 缓存键硬约束提醒落档
  - P1-10 PASS：`NopDatavSingleWriterCrudBizModel` 13 个 mutation 入口全量 @Override 抛结构化异常（编译器强制真实覆写）；3 BizModel 改继承；领域写入点 DAO 直写不受影响（`NopDatavDashboardBizModel:246`/`NopDatavScreenBizModel:130`/`AlertEvaluator:216,236`）；pruning 测试 admin 经 action-auth 后被 BizModel 守卫拒绝（证明守卫在真实路径上）
  - Tests Anti-Hollow PASS：审计期间独立复跑 5 个权限测试类 41/41 green，全部断言错误码非仅无异常
  - Docs PASS：D1-D5 段落（permission-sharing-design.md:145-200）+ 17 实体矩阵 + §53-65 drift 标注（:129）
  - Deferred 检查 PASS：无 in-scope 缺陷降级
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（全部勾选 + Closure Evidence 已写入）
  - Anti-Hollow 检查：`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 findings）；归属校验/可见性接线链经调用链追踪 + 测试断言双重验证
  - 验证命令：`./mvnw test -pl nop-datav -am`（reactor BUILD SUCCESS，datav-service 578/578）；`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS；`check-doc-links.mjs --strict` 退出码 0

Follow-up:

- no remaining plan-owned work（P2-06/07/08 等非阻塞残余已登记 `ai-dev/backlog/nop-datav-audit-followups.md`；AR-7 已显式裁定落档非 debt）
