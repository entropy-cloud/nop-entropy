# 1 ChatBI 生成工具数据集可见性收口（datav-generate-dashboard / datav-generate-screen）

> Plan Status: completed
> Mission: nop-datav
> Execution Order: 1 of 1（本批仅 1 份 remediation plan：本轮唯一 open audit 的唯一 P0）
> Last Reviewed: 2026-08-16
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析）达成共识（R1: 1 Major + 4 Minor 全部修复；R2: 修复逐项验证到位、无 Blocker/新增 Major，结论「可以转 active」），残余 2 个文案级 Minor 已按 R2 建议修正
> Source: `ai-dev/audits/nop-datav/2026-08-16-0719-open-audit-nop-datav.md` §AR-1 `[P0]`（ChatBI 生成工具绕过 P1-03 数据集可见性边界）
> Related: `ai-dev/plans/nop-datav/2026-08-15-2146-1-panel-subentity-auth-rbac-closure.md`（P1-03 可见性接线先例，已 completed 2026-08-16）；`ai-dev/design/nop-datav/permission-sharing-design.md` §D4；`ai-dev/design/nop-datav/ai-design.md` §6

## Purpose

把 AR-1 [P0] 收口：`datav-generate-dashboard` / `datav-generate-screen` 两个生成工具执行器补接 P1-03 已建立的数据集可见性判定（`ChatBiDatasetVisibility`，裁定 D4 选项 B），消除「非 admin 经 ChatBI 构造引用任意数据集的看板/大屏，再经自有看板查询路径（`PanelDataBinder`）外泄他人数据集数据」的完整授权链。修复后，「user 只能触达自己拥有的 + 已发布的」在 ChatBI 全部 6 个工具（list / describe / query / generate-dashboard / generate-screen / list-component-types）上一致成立，且 owner doc 中 D4 的承重前提（「非 admin 无法构造『引用他人数据集的看板』」）从「被打破」恢复为「真实成立」。

## Current Baseline

以下事实 2026-08-16 已对照 live repo（HEAD 60dd27a6e）逐项核实：

- **缺口本体**：
  - `DatavGenerateDashboardExecutor.validateAndPlanPanel`（`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateDashboardExecutor.java:275-280`）：数据集校验仅 `ds != null && status==1`，无可见性判定；`:275` 注释「按 RLS 由 DAO 处理」为不实陈述（nop-report 数据集无 DAO 层 RLS——P1-03 修复已在 `DatavListDatasetsExecutor.java:108` 落档该事实）。
  - `DatavGenerateScreenExecutor`（同目录 `DatavGenerateScreenExecutor.java`）：预加载循环 `:176-180` 与 `validateAndPlanWidget` 校验 `:292-300` 同款缺失。
- **P1-03 已正确接线的对照组**（修法锚点，均 2026-08-16 复核在位）：`DatavQueryDatasetExecutor.java:102-109`、`DatavDescribeDatasetExecutor.java:73-79`、`DatavListDatasetsExecutor.java:110-121`；判定单点 `ChatBiDatasetVisibility`（`resolveOperator` / `resolveAdmin` / `hasIdentity` / `isVisible`；无身份 fail-closed）。
- **身份来源关键差异**：两个 generate executor 各自有 `resolveOperator(context)`（dashboard `:197-206`，screen 同款），**回退 `NopDatavOperatorResolver.SYSTEM_OPERATOR`（"system"）**，仅用于落 createdBy——**不可**复用做可见性判定（否则 createdBy="system" 的数据集对所有人可见）。可见性身份必须取 `ChatBiDatasetVisibility.resolveOperator/resolveAdmin`（非 ChatBiToolExecuteContext 时返回 null/false，fail-closed）——与 describe/query 先例一致。
- **授权链五环（审计已逐环静态核实，本 plan 不重证，修复第 2 环即断链）**：action-auth `chatToDashboard`/`chatToScreen` roles=admin,user（`nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml:308-318`）→ executor 校验缺失（本 plan 修复点）→ 请求者是生成看板 owner，Dashboard RLS（`createdBy==userName OR publishStatus=10`，`nop-datav.data-auth.xml:9-16`）放行 → `PanelDataBinder.java:125-147` 按设计（裁定 D4 排除）无数据集归属检查 → 数据外泄。P0-02 修复后 Panel CRUD 已收敛 admin，generate 工具是**非 admin 唯一的 DatasetRef 绑定构造路径**。
- **错误码现状**：`ERR_DATAV_CHATBI_DATASET_NO_ACCESS`（`NopDatavErrors.java:618-622`，ARG_DATASET_SID + ARG_USER_NAME）与 `ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND`（`:659-663`）均已定义在案，无需新增。
- **工具 schema 文本**：`datav-generate-dashboard.tool.xml:74`、`datav-generate-screen.tool.xml`（同款段落）明文宣告「datasetSid MUST reference an existing active (status=1) dataset」即合法——修复后需同步为「对当前用户可见」，否则 schema 继续教唆 LLM 透传任意 sid。
- **owner doc 承重前提现状**：`permission-sharing-design.md` §D4 「PanelDataBinder 排除（definitive）」论证以「D2 收敛后 DatasetRef 的绑定与篡改面仅 admin 可达，非 admin 无法构造『引用他人数据集的看板』」收尾——该前提被 generate 路径打破，论证失效，需修正；`ai-design.md` §6（`:201-220`）消费方清单仅列 list/describe/query 三 executor，需补 generate 两 executor。
- **测试现状**：
  - 镜像模板：`TestDatavQueryDatasetExecutor.java:156-214`（`testQueryInvisibleDatasetReturnsExplicitError` + `testQueryVisibleForAdminAcrossCreators`）——executor 直调 + `ChatBiToolExecuteContext(null, operator, admin)` 显式身份，按仓库 ChatBI 测试口径（校验落点在 tool executor，不经 GraphQL 全链，LLM mock 基建无先例，见 plan 2146-1 Phase 4 裁定）。
  - **既有 fixture 迁移约束（实现时必知）**：`TestDatavGenerateDashboardExecutor` / `TestDatavGenerateScreenExecutor` 的 `newActiveSqlDataset` 助手固定 `createdBy="test"`，而正向用例以 `ChatBiToolExecuteContext(null, "gen-user")`（非 admin）调用——接线后这些正向用例会被可见性判定拒绝而变红；必须同步对齐 fixture（createdBy=调用 operator 或 admin context），属预期内的测试迁移，不是回归失败。
  - `TestNopDatavChatBiTransactionBoundary` / `TestNopDatavChatBiDashboardE2E` / `TestNopDatavChatBiScreenE2E` / `TestNopDatavChatBiScreenAction` 亦经 generate 路径（直调或经 BizModel 真实身份链路，E2E 类 CALLER 身份与 fixture `createdBy="test"` 同样不对齐），同类对齐需求由全模块测试跑绿统一收口；`TestDatavGenerateScreenUkHeuristic` 仅直调静态 UK 启发式、无数据集 fixture，不在对齐范围。
- **P2 边界**：本 audit 的 AR-2（setScreenThumbnail stale 响应）、AR-3（补偿清单登记窄洞）为 P2，已登记 follow-up backlog（#70/#71），不入本 plan；AR-1 建议 4（快照 `datasetRefs[].refDatasetId` 对匿名分享面的暴露评估）审计明示「独立裁定，不与本修复绑定」，登记 backlog #72，不入本 plan。

## Goals

- `datav-generate-dashboard` / `datav-generate-screen` 在接受任何 `datasetSid` 绑定前，经 `ChatBiDatasetVisibility.isVisible` 判定该数据集对当前身份（operator + admin，取自 `ChatBiToolExecuteContext`）可见；不可见 → 显式拒绝 `ERR_DATAV_CHATBI_DATASET_NO_ACCESS`（与 describe/query 同语义），不落库、不静默跳过。
- 可见性身份来源正确：使用 fail-closed 的 `ChatBiDatasetVisibility.resolveOperator/resolveAdmin`，不复用带 SYSTEM_OPERATOR 回退的 executor 内 `resolveOperator`。
- `DatavGenerateDashboardExecutor.java:275` 不实注释修正。
- 两个 generate 工具的 schema 描述文本与新的可见性契约一致（不再宣告「任意活跃数据集即可引用」）。
- 负向 + 正向回归测试：非 owner 非 admin 经 generate 工具绑定他人数据集被拒（含结构化错误码断言）；owner / admin 正常生成不受影响（无过度限制回归）。
- owner doc 收口：`ai-design.md` §6 消费方清单补全为 ChatBI 全部数据集消费工具（含 generate 两项），并新增「数据集 sid 消费面固定清单」；`permission-sharing-design.md` §D4 承重前提修正为真实成立（因 generate 已接线）。

## Non-Goals

- 不改 `PanelDataBinder`（裁定 D4 排除依然成立——其论证前提由本 plan 修复恢复，而非改变排除结论本身）。
- 不改 `chatToDashboard`/`chatToScreen` 的 action-auth 角色绑定（roles=admin,user 是设计意图：user 可创作）。
- 不处理快照序列化 `datasetRefs[].refDatasetId` 的分享面暴露（独立裁定，backlog #72）。
- 不处理 AR-2 / AR-3（P2，backlog #70/#71）。
- 不引入 per-user 可见性到面板查询路径 / 不动 `DashboardPanelQueryCache`（D4 缓存键硬约束提醒维持原状）。
- 不做「@BizMutation 内原始 JDBC 写 + 实体重读」全模块清点（audit 总评方向 2，另行 follow-up）。

## Scope

### In Scope

- `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateDashboardExecutor.java`（可见性接线 + 注释修正）。
- `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateScreenExecutor.java`（可见性接线，覆盖预加载与校验两处检查点）。
- `nop-datav/nop-datav-service/src/main/resources/_vfs/nop/ai/tools/datav-generate-dashboard.tool.xml`、`datav-generate-screen.tool.xml`（描述文本同步）。
- 既有 generate 路径测试的 fixture/身份对齐迁移（`TestDatavGenerateDashboardExecutor`、`TestDatavGenerateScreenExecutor` 及其余经 generate 路径的测试类）。
- 新增可见性负向/正向回归测试（镜像 `TestDatavQueryDatasetExecutor` 可见性用例模式）。
- `ai-dev/design/nop-datav/ai-design.md` §6、`ai-dev/design/nop-datav/permission-sharing-design.md` §D4 修订。

### Out Of Scope

- Non-Goals 列出的全部方向。

## Execution Plan

### Phase 1 - 双 generate executor 可见性接线 + 回归测试（AR-1 修复本体）

Status: completed
Targets: `DatavGenerateDashboardExecutor.java`、`DatavGenerateScreenExecutor.java`、两个 generate `*.tool.xml`、generate 路径相关测试类

- Item Types: `Fix | Proof`

- [x] Fix（dashboard）：`DatavGenerateDashboardExecutor` 数据集校验点接入可见性判定——身份取 `ChatBiDatasetVisibility.resolveOperator(context)` / `resolveAdmin(context)`（在 `executeAsync` 解析并传入校验路径；**禁止**复用本类 `resolveOperator` 的 SYSTEM_OPERATOR 回退），数据集已加载且活跃但 `!isVisible` → 显式拒绝，错误体含 `nop.err.datav.chatbi-dataset-no-access` 错误码（错误体形态镜像 describe/query 先例：errorCode + datasetSid + userName 内嵌 error body；载体选择由实现裁定，错误体三要素可观测即可）；不存在/非活跃仍走既有 `ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND`（与 describe/query 先例的错误码分工一致）；无身份（fail-closed）同样拒绝
- [x] Fix（screen）：`DatavGenerateScreenExecutor` 同款接线，**覆盖全部数据集检查点**——预加载循环与 `validateAndPlanWidget` 的「补查一次」回退路径都必须被可见性判定覆盖（预加载 cache 命中与补查两来源统一在 `validateAndPlanWidget` 判定点收口），不得留任一未判定的旁路，也不得以「预加载时静默过滤 → 校验时误报 NOT_FOUND」方式实现
- [x] Fix（注释）：`DatavGenerateDashboardExecutor.java:275` 不实注释改为真实机制描述（镜像 `DatavListDatasetsExecutor` P1-03 修正后的注释口径）
- [x] Fix（schema 文本）：两个 generate `.tool.xml` 的 datasetSid 约束描述同步为「must reference an existing active dataset **visible to the current user**; otherwise ERR_DATAV_CHATBI_DATASET_NO_ACCESS」（保留 NOT_FOUND 分工表述）
- [x] Fix（测试迁移）：既有 generate 正向用例 fixture/身份对齐（`newActiveSqlDataset` 的 createdBy 与调用 operator 对齐，或按用例语义改 admin context）——这是接线后的预期迁移，逐类核对不误判为回归
- [x] Proof（负向回归，镜像 `testQueryInvisibleDatasetReturnsExplicitError`）：非 owner 非 admin（如 alice）经 `datav-generate-dashboard` / `datav-generate-screen` 绑定他人数据集（createdBy=bob）→ 失败，错误体含 `nop.err.datav.chatbi-dataset-no-access` 结构化错误码；**且不落库**（无 Dashboard/Screen/DatasetRef/Panel 残留，镜像既有「事务无半成品」断言模式）；无身份 context 同样拒绝（fail-closed）
- [x] Proof（正向回归，无过度限制，镜像 `testQueryVisibleForAdminAcrossCreators`）：owner 对自己数据集正常生成；admin 跨 createdBy 正常生成；两 executor 各覆盖

Exit Criteria:

- [x] 两个 generate executor 的每个接受 `datasetSid` 的代码路径均有过可见性判定；不存在「加载成功 + 活跃 + 不可见 → 放行」分支（代码走查 + 负向测试双证）
- [x] 不可见拒绝的错误体含 `nop.err.datav.chatbi-dataset-no-access` 结构化错误码（非静默、非 NOT_FOUND 误报），且无落库残留
- [x] 可见性身份非 SYSTEM_OPERATOR 回退（代码走查确认取自 `ChatBiDatasetVisibility`）
- [x] 新增测试列出验证矩阵：{alice 拒绝, owner 放行, admin 放行, 无身份拒绝} × {dashboard, screen}
- [x] **接线验证**（Minimum Rules #23）：负向用例证明可见性判定在 generate 真实执行路径被调用（拒绝即证据），非仅类型存在
- [x] 既有 generate 路径测试全部迁移对齐，`./mvnw test -pl nop-datav -am` 全绿（含新增用例；记录通过数）
- [x] owner-doc 归属声明：本 Phase 改变 executor 行为与工具 schema 契约，对应 owner doc（`ai-design.md` §6 / `permission-sharing-design.md` §D4）更新显式归属本 plan Phase 2；若 plan 在本 Phase 后中断，daily log 必须记录 doc 同步未完成状态（防 doc-code 漂移无主）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 收口（D4 承重前提修正 + 消费面固定清单）

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md` §D4、`ai-dev/design/nop-datav/ai-design.md` §6

- Item Types: `Fix`（owner-doc drift：§D4 前提被 AR-1 打破后的表述失效、§6 消费方清单不完整，均为已确认 drift，按 guide 规则 15 归 `Fix`）

- [x] Fix：`permission-sharing-design.md` §D4「PanelDataBinder 排除（definitive）」论证修正——原文「D2 收敛后 DatasetRef 的绑定与篡改面仅 admin 可达」更新为「D2 收敛 + generate 工具可见性接线（本 plan）后」，明确记录 AR-1 教训：排除论证的承重前提必须随新消费工具出现而复核
- [x] Fix：`ai-design.md` §6 消费方清单补全——list / describe / query / **generate-dashboard / generate-screen** 全部列为可见性实施点（generate 侧拒绝语义：显式 `ERR_DATAV_CHATBI_DATASET_NO_ACCESS`）
- [x] Fix：按 audit 总评方向 1，在 `ai-design.md` §6（或 §D4，择一落点、另一处交叉引用）新增「数据集 sid 全部消费面固定清单」：ChatBI 6 工具 + `PanelDataBinder`（经 Dashboard RLS 传递）+ 快照序列化 `datasetRefs[].refDatasetId`（暴露面，指向 backlog #72 裁定），后续新增数据消费工具时必须对照该清单补接线
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] §D4 不再存在已被打破的前提表述；修正后的论证与 Phase 1 落地行为一致（generate 拒绝语义已写入）
- [x] §6 消费方清单与 live 代码一致（6 工具全列，含两 generate）
- [x] 消费面固定清单存在且覆盖 ChatBI 工具 + PanelDataBinder + 快照序列化三类面，含 backlog #72 交叉引用
- [x] No new test required: 纯文档 Phase，行为测试在 Phase 1

## Closure Gates

- [x] AR-1 缺口已修复：两个 generate executor 无绕过路径，负向测试锚定（Phase 1 Exit Criteria 全勾）
- [x] 不实注释已修正；schema 文本与新契约一致
- [x] 既有测试迁移完成，无因本修复误伤的用例
- [x] owner doc（§D4 + §6 + 消费面清单）已同步到 live baseline
- [x] 不存在被静默降级的 in-scope 项（AR-1 建议 1/2/3 全部落地；建议 4 显式移出至 backlog #72 并注明独立裁定）
- [x] 独立子 agent closure-audit 已完成并写入 Closure 段落证据
- [x] **Anti-Hollow Check**：可见性判定在 generate 运行时路径被真实调用（负向测试为证），无空壳/静默跳过
- [x] `./mvnw compile` / `./mvnw test -pl nop-datav -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Non-Blocking Follow-ups

- backlog #70（AR-2，P2）：`setScreenThumbnail` 生产路径 stale 响应（detached-copy 或 setter 同步修正 + graphQLEngine mutation 路径回归）——独立响应契约问题，不阻塞本 plan closure。
- backlog #71（AR-3，P2）：补偿清单登记依赖 handler 端 JSON 解析成功的窄洞——低概率边界，独立修。
- backlog #72（AR-1 建议 4，待裁定）：快照 `datasetRefs[].refDatasetId` 对匿名/登录分享面的 sid 暴露是否收敛——审计明示独立裁定，不与本修复绑定。
- audit 总评方向 2（未编号）：「@BizMutation 内原始 JDBC 写 + 实体重读/返回」全模块模式清点——建议后续 backlog 立项。
- closure audit Minor（2026-08-16）：`NopDatavOperatorResolver.resolveOperator(IServiceContext)` 的 SYSTEM_OPERATOR 回退使无身份生产调用呈现 operator="system"（与 P1-03 describe/query 同链路、`@Auth` 角色门内不可匿名触达；非本 plan 引入）——后续若收紧建议与 describe/query 先例统一裁定。
- closure audit Minor（2026-08-16）：`DatavGenerateDashboardExecutor.validateAndPlanPanel` 的 `seenDatasetSidsCache` 形参未使用（修复前既有死参数，保守无害——每 panel 全量重校验）——后续清理顺带处理。

## Closure

Status Note: AR-1 [P0] 收口——两个 generate executor 的全部 `datasetSid` 接受路径（dashboard 单校验点 / screen 预加载 cache 命中 + 补查双来源收口于 `validateAndPlanWidget`）均经 `ChatBiDatasetVisibility.isVisible` 判定（身份 fail-closed，非 SYSTEM_OPERATOR 回退），不可见显式拒绝 `ERR_DATAV_CHATBI_DATASET_NO_ACCESS`（错误体三要素 errorCode + datasetSid + userName）且不落库；NOT_FOUND 分工、tool schema/system prompt 文本、§D4 承重前提与 §6 消费面清单全部同步；{拒绝/owner/admin/无身份}×{dashboard,screen} 8 用例 + 6 测试类 fixture 迁移全绿。无剩余 plan-owned work（AR-1 建议 4 显式移出 backlog #72；P2 项 #70/#71 不入本 plan）。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，research-only closure audit），task id `ses_ff4fae961ffemkLS4o6tLdlqsF`
- Evidence:
  - Phase 1 Exit Criteria 7/7 PASS：可见性判定全路径覆盖（dashboard `DatavGenerateDashboardExecutor.java:291-298`；screen 预加载不静默过滤 `:175-190`、双来源收口 `:304-319`；`doCreate` 重读仅取 dsName 无判定面）；NO_ACCESS 三要素错误体（`ValidationError.datasetNoAccess` dash `:525-530` / screen `:622-627` + `formatValidationError`）+ 无落库断言（dash test `:369-375` / screen test `:417-421`）；身份取 `ChatBiDatasetVisibility`（dash `:122-123` / screen `:124-125`），executor 内 `resolveOperator`（SYSTEM_OPERATOR 回退）仅用于 createdBy；8 用例矩阵在位（dash `:347,382,402,425` / screen `:395,428,449,473`）；接线验证 = 直调真实 `executeAsync` 的拒绝证据；fixture 迁移 6 类逐一核实（gen-user/screen-gen-user/dash-e2e-user/screen-e2e-user/screen-action-user/p105-user）；surefire（2026-08-16 22:34 fresh）GenerateDashboard 16/0、GenerateScreen 21/0、模块 629/0/0。
  - Phase 2 Exit Criteria 4/4 PASS：§D4 前提修正 + AR-1 教训落档（`permission-sharing-design.md:169`，裁定清单 `:167` 含 generate 两工具）；§6 消费方清单 = 6 工具（`ai-design.md:204-208`，`list-component-types` 显式标注无 sid 面 `:220`）；消费面固定清单三类面 + backlog #72 交叉引用（`ai-design.md:215-226`）；纯文档 Phase 无新增测试要求。
  - Closure Gates：不实注释零残留（chatbi/ 目录 "按 RLS 由 DAO 处理" 0 命中；真实机制注释 dash `:286-290` / screen `:313-316`）；tool schema 双文件 description + schemaJson 均含 "visible to the current user" + 双错误码分工（dashboard `:44,:74` / screen `:65,:110`），`ChatBiSystemPrompt.java:70,128` 同步；AR-1 建议 1/2/3 落地、建议 4 显式 backlog #72（`nop-datav-audit-followups.md:152`）；action-auth 保持 roles=admin,user（Non-Goal 未越界）；Anti-Hollow：scan-hollow 0 findings + 无静默 no-op 模式。
  - 对抗性旁路排查（审计结论）：预加载无过滤、`needsDataset=false` 的 rogue datasetSid 不存储（dash `doCreate:370` / screen `:438-442` → null）、`seenDatasetSidsCache` 未用于跳过校验（每 panel 全量重校验）、生产身份链 BizModel→Loop→context 完整（`NopDatavChatBiBizModel.java:398-408`）——无「加载成功 + 活跃 + 不可见 → 放行」路径。
  - 工具退出码：`check-plan-checklist.mjs <plan> --strict` 退出码 0（closure 后复跑）；`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 findings）；`check-doc-links.mjs --strict` 退出码 0（6 warning 均为无关 plan 338 既有项）。
  - 构建验证：`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS；`./mvnw test -pl nop-datav -am -T 1C` reactor BUILD SUCCESS（datav-service 629/0/0；首轮 `nop-stream-rocksdb` 计时基准在 -T 1C 负载下环境抖动，单跑通过 ratio 0.058，与本 plan 无关）。
  - Deferred 项分类检查：#70/#71 为审计已裁定 P2（Why Not Blocking：独立响应契约/低概率边界，均有 backlog 登记）；#72 审计明示独立裁定不与本修复绑定；closure audit 3 项 Minor（BizModel 侧 SYSTEM_OPERATOR 回退为 P1-03 同链路既有项、死参数为修复前既有、TOCTOU 与 describe/query 先例一致）均非 in-scope live defect，已记入 Non-Blocking Follow-ups。
- Verdict: **CLOSABLE**（审计独立结论，2026-08-16）

Follow-up:

- no remaining plan-owned work；non-blocking 项见上方 Non-Blocking Follow-ups（#70/#71/#72 + audit 总评方向 2 + 2 项 closure-audit Minor）。
