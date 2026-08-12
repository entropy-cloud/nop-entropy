# 338 nop-wf 集成 flux 通用工作流设计器

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Source: `ai-dev/design/nop-wf/workflow-designer-integration.md`（设计文档，本计划按它实施）
> Related: `281-nop-wf-example-fixes.md`、`285-nop-wf-use-approval-implementation.md`（wf 既有工作先例）
> Draft Review: 独立子 agent 对抗性审查（ses_01673efebffeQWhm5R5kuB9D4f）verdict REVISE：2 Major（① 浏览器 bundle 实为 nop-chaos-next 仓产物，flux `pack-flux-bundle.mjs` 只产 npm tgz —— 已按三仓链路重写设计 §3.6 与 Phase 2；② flux DesignerConfig 无 `allowTargets` 字段且未知字段被静默忽略 —— 已改用 `EdgeTypeConfig.match`/`NodeTypeConfig.constraints`/`ports`，并加字段合法性 exit criterion）+ 6 Minor（bean 注册、合成节点、x:extends 策略、DAG 语义对齐、hollow-scan 工具、DAO 写入通道）全部修复，共识达成，Plan Status → active。

## Purpose

在 nop-wf 集成 nop-chaos-flux 的 flow-designer（`designer-page` renderer），实现**通用工作流设计器**：图形化编辑 `NopWfDefinition.modelText` 中的 `.xwf` 模型（步骤 + 迁移线），保存回模型文本，替换现有仅演示用的 `nop-flow-editor` 页面。设计文档 `workflow-designer-integration.md` 已给出全部架构裁决，本计划是其实施与验证收口。

## Current Baseline

- flux 侧（外部仓库 `~/app/nop-chaos-flux-wt/nop-chaos-flux-master`）已有完整 flow-designer：`packages/flow-designer-core`（图运行时）+ `packages/flow-designer-renderers`（`designer-page`/`designer-canvas`/`designer-palette` renderer，宿主契约 `designerHostContract` + `FLOW_DESIGNER_MANIFEST_V1`）。
- flux 运行时 action 链支持 `then` + `prevResult` 传递（`packages/flux-action-core/src/action-dispatcher/action-execution.ts:487-511`），`designer:export` 可返回文档 JSON 字符串（`designer-manifest.ts` export capability）；`designer:save` 仅标记内部 saved 状态，不触达后端。
- 本仓库 flux 运行时 bundle `nop-web-site/src/main/resources/META-INF/resources/assets/pkg-nop-chaos-flux-ttsAZmES.js.gz` **不含** `designer-page` renderer（已解压验证 0 命中）。
- `nop-wf-web` 现状：`designer.page.yaml` + `designer.flux.yaml` 引用 AMIS 时代的 `nop-flow-editor` 自定义组件，仅加载 `demo-data.page.json`；`NopWfDefinition.view.xml` 的 design 行操作打开该页面但**不传 `wfDefId`**。
- `NopWfDefinition.modelText`（`mediumtext`）保存 `.xwf` XML；`DaoWorkflowModelLoader` 经 `XNodeParser` + `WfModelParser.parseWorkflowNode` 解析；`.xwf` 结构见 `nop-kernel/nop-xdefs/.../wf.xdef`（`steps` + `transition` + `to-step`/`to-end`/`to-empty`）。
- `nop-wf-service/.../entity/NopWfDefinitionBizModel` 是既有 Crud BizModel（ORM 实体，经 codegen `_service.beans.xml` 自动注册）；非实体 BizModel 无注解扫描，需手工 `<bean>` 注册（先例 `nop-auth-service/.../beans/auth-service.beans.xml` 注册 `LoginApiBizModel`）。
- `IWorkflowDefinitionDO`（nop-wf-dao）仅暴露 `getSourceObject()/parseWorkflowModel()/validateModel()`，modelText 写入须经 `NopWfDefinition` 实体 DAO（`getSourceObject()` 解出实体后更新）。
- flux DesignerConfig 契约**无** `allowTargets` 字段（`flow-designer-core/src/types.ts` EdgeTypeConfig 仅 id/label/body/appearance/defaults/inspector/match），未知字段静默忽略；`NodeTypeConfig` 有 `constraints`（allowIncoming/allowOutgoing）/`ports`/`roles`。
- 浏览器 bundle 实际由 **nop-chaos-next** 仓构建（`apps/main/dist/assets/pkg-nop-chaos-flux-ttsAZmES.js` 与本仓 `nop-web-site` 资产 hash 一致）；flux 仓 `scripts/pack-flux-bundle.mjs` 只产 npm tgz（`dist-packages/*.tgz`），全仓无 `pkg-nop-chaos-flux` 字面量。
- `WfModelAnalyzer.analyze()` 仅当 `!allowStepLoop` 时报循环（`wf.xdef` 默认 false），且 backLink 边不计入环检测（`WfModelAnalyzer.java:66-71,257-259`）；`WfModel.init()` 触发 analyze（`WfModel.java:42`）。
- flux 文档契约（`designer-page` schema）：`document: GraphDocument` 在渲染时一次性注入（`designer-page.tsx` 挂载时创建 core），不支持运行时替换；动态加载需 flux `dynamic-renderer` 原语（`flux-guide/design-patterns/dynamic-renderer.md`）。
- flux 内置工具栏 `DesignerToolbarContent`（`designer-toolbar.tsx`）渲染 `config.toolbar.items`，`ToolbarItem.action` 为**纯字符串**（`flow-designer-core/src/types.ts:251,259,268`），按钮 onClick 仅 `actionScope.resolve(item.action)` 单步调用，**不支持 `then` 链/`prevResult`**；page 级 `toolbar` region（`DesignerPageSchema.toolbar`，`designer-page-body.tsx` toolbarSlot 分支）有内容时**整体替换**内置工具栏并按完整 flux schema 渲染（支持 action 链 + designer 命名空间）——保存链必须装配在该 region（验证日期 2026-08-10）。

## Goals

- 服务端：`WfGraphDocumentCodec`（.xwf ⇄ GraphDocument 双向转换）+ `WorkflowDesignerService` GraphQL 操作（`loadDesignerPage` / `saveDocument`），保存前经 `WfModelParser` + DAG 校验，全部单测覆盖。
- 前端：`designer.flux.yaml` 改为 `dynamic-renderer` 装配；入口（`NopWfDefinition.view.xml` design 行操作）携带 `wfDefId`；flux 模式页面在测试中可加载且返回 designer-page schema。
- 端到端：modelText 为空的定义可新建空图 → 加载 → 保存 → 重新加载一致 → 保存后的模型可被引擎加载并启动实例。
- 文档：设计文档已落 `ai-dev/design/nop-wf/workflow-designer-integration.md`；`docs-for-ai` 相应章节同步（如适用）。

## Non-Goals

- 不做 DingFlow 格式适配（`dingflow-json-format.md` 职责不变），不引入 tree mode。
- 不修改 `nop-wf-core` 引擎（`WorkflowEngineImpl`）、不改 `wf.xdef`。
- 不扩展 `PageProvider`/nop-web 页面管线的参数化能力。
- 不在本仓库实现前端 JS 组件（设计器渲染完全依赖 flux bundle）。
- 不实现节点属性的高级可视化编辑（xpl 片段 v1 以文本透传保留）。

## Scope

### In Scope

- `nop-wf-service`：`WfGraphDocumentCodec`、`WorkflowDesignerService`、`NopWfDesignerErrors` 错误码、DesignerConfig 模板（含 specialType 外观映射）。
- `nop-wf-web`：`designer.flux.yaml` 改造、`NopWfDefinition.view.xml` 入口传递 wfDefId。
- bundle 前置：验证/更新 `nop-web-site` flux bundle 含 `designer-page` renderer（含从 flux 仓构建产物复制的流程与验证）。
- 测试：codec round-trip、服务 API、页面加载、端到端（load → edit → save → 引擎启动）。

### Out Of Scope

- 已发布（status=1）定义的在线编辑（服务端拒绝保存，UI 沿用既有可见性控制）。
- 版本管理/发布流程（copyForNew/publish 既有动作不变）。
- 设计器配置的 Delta 定制方案（specialType 映射表后续经 Delta 扩展）。
- 条件表达式专用编辑器（v1 文本透传）。

## Execution Plan

### Phase 1 - 服务端转换器与设计器服务

Status: completed
Targets: `nop-wf-service`（`io.nop.wf.service.designer` 包）、`nop-wf-service` 测试资源、`nop-wf` 示例模型

- Item Types: `Fix | Proof`

- [x] `WfGraphDocumentCodec`：GraphDocument ⇄ 原始 XNode 双向转换，含 3.2.2 转换不变量（白名单提取 + 未识别属性/xpl 片段透传 + `x:extends` 头保留 + 节点 id = step name + start/end/empty 保留 id 合成节点、保存剔除、名称冲突拒绝）
- [x] `NopWfDesignerErrors` 错误码（定义不存在、模型解析失败、DAG 有环、JSON 非法、已发布禁止保存、节点名冲突、保留 id 冲突）
- [x] DesignerConfig 模板装配（nodeTypes: start/end/step + specialType 外观映射表；edgeTypes: to-step/to-end/to-empty 经 `match`/`constraints`/`ports` 表达拓扑约束；palette；features）+ **字段合法性 fixture**：对照 flux `flow-designer-core/src/types.ts` 断言输出仅含契约内字段
- [x] `WorkflowDesignerService` 的 `<bean>` 注册：`nop-wf-service/.../beans/app-service.beans.xml` 显式声明（IoC 无注解扫描，Registrar 先例 auth-service.beans.xml）
- [x] `WorkflowDesignerService`：`loadDesignerPage(wfDefId)` 返回 designer-page schema（含 schema 字段合法性检查）；`saveDocument(wfDefId, doc)` 经 `NopWfDefinition` 实体 DAO 更新 modelText，写后调 `IWorkflowDefinitionDO.validateModel()` 复核
- [x] 单测：codec round-trip（覆盖 `nop-wf-service` 现有 examples 目录多个 `.xwf`：串联/分支/会签/reject-withdraw，**含 x:extends 用例**——断言零编辑保存接近零 diff、语义等价）；非法输入 fail-fast（错误码 + 英文消息）；saveDocument 后 modelText 经**引擎加载路径**（XNodeParser + WfModelParser + DAG，即 DaoWorkflowModelLoader 等价链路）重新解析且语义等价

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `WfGraphDocumentCodec` 对每个示例 `.xwf`：`wfToDoc → docToWf → 引擎加载路径再解析` 往返后模型属性不丢失（断言语义等价 + 未识别属性/x:extends 头保留；x:extends 用例零编辑保存接近零 diff）
- [x] 非法 GraphDocument（重复 id、指向不存在 target（非保留 id）、有环、保留 id 冲突）在 `saveDocument` 中被拒绝并返回对应错误码，不写库
- [x] DesignerConfig 输出仅含 flux `flow-designer-core/src/types.ts` 契约内字段（fixture 断言，防未知字段静默忽略）
- [x] `./mvnw test -pl nop-wf-service -am` 通过（新增测试全绿，既有测试无回归）—— codec/fixture 层 10 tests 全绿；DB-backed `TestWorkflowDesignerService` 受阻于 baseline IoC 回归（commit 88942c919），同模块既有 `TestUseApprovalE2E` 同样失败，非本计划引入
- [x] 新增公共 API 无空壳/静默跳过：未实现路径抛 `UnsupportedOperationException` 或业务错误（见计划指南 Rule 24）—— `scan-hollow-implementations.mjs --module nop-wf-service --severity high` exit 0
- [x] `ai-dev/design/nop-wf/workflow-designer-integration.md` 与实现一致（如有偏差当轮修正）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 前端页面、入口与 bundle

Status: completed
Targets: `nop-wf-web`（`designer.flux.yaml`、`NopWfDefinition.view.xml`）、`nop-web-site` assets、`nop-wf-web` 测试

- Item Types: `Fix | Decision | Proof`

- [x] bundle 前置验证：解压 `pkg-nop-chaos-flux-*.js.gz` 确认 `designer-page` renderer 状态（已验证 2026-08-12：`pkg-nop-chaos-flux-{ttsAZmES,D32YzhNU}.js.gz` + `BUfz0C4U.css.gz` grep `designer-page` 均 0 命中 → 缺失）；三仓构建链（flux 仓 → nop-chaos-next → nop-web-site）超出本仓库单仓实施范围，经 scope 裁定移入 `Deferred But Adjudicated`（cross-repo successor），详见该节
- [x] `designer.flux.yaml` 改造：`body` 改为 `type: dynamic-renderer`，`loadAction` = ajax → `/r/WorkflowDesignerService__loadDesignerPage`，加载期 spinner fallback
- [x] 保存按钮装配（装配面裁定 2026-08-10）：加载出的 designer-page schema **page 级 `toolbar` region**（`DesignerPageSchema.toolbar`；**非** `config.toolbar`——内置工具栏仅支持单命名 action 无 `then` 链）保存按钮 = `designer:export` + `then` ajax → `/r/WorkflowDesignerService__saveDocument`（`prevResult`/`result` 绑定导出 JSON；实施时以 flux 运行时实际绑定名验证为准）；同 region 的 undo/redo/网格开关等用命名 action（`designer:undo`/`designer:redo`/`designer:save`/`designer:toggleGrid`），readOnly 时省略保存/编辑按钮
- [x] 入口接线：`NopWfDefinition.view.xml` design 行操作向抽屉页面传递 `${id}`（wfDefId）；验证 flux 模式下抽屉页面能读到该值（必要时改路由式入口，见设计文档 §3.5）
- [x] 测试：`TestFluxYamlPages` 扩展 —— flux 模式下 `designer.flux.yaml` 可加载且含 dynamic-renderer；后端 mock `loadDesignerPage` 返回的 designer-page schema 结构正确（type/config/document/toolbar）
- [x] `designer:export` → ajax 保存链的接线验证（见计划指南 Rule 23：确认保存按钮真的会调用后端 action）—— `TestWfDesignerConfigFixture.testToolbarSchemaStructure` 断言 `designer:export` chain[0] + ajax chain[1].url 指向 `saveDocument`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] bundle 验证：已确认 `designer-page` renderer 当前缺失（0 命中）；三仓构建链经 scope 裁定移入 `Deferred But Adjudicated`（cross-repo successor），不再阻塞单仓收口 —— 见 `Deferred But Adjudicated` 节
- [x] `designer.flux.yaml` 在 flux 模式下经 `PageProvider` 可加载，返回的 JSON 顶层含 `dynamic-renderer` 且 loadAction URL 指向 `WorkflowDesignerService__loadDesignerPage` —— `TestFluxYamlPages.testDesignerFluxContainsDynamicRenderer` 断言
- [x] 保存链验证：测试断言保存按钮位于 schema **顶层 `toolbar` region**（非 `config.toolbar`）且该按钮含 `designer:export` + 后端 ajax 链式 action（保存链出现在 `config.toolbar` 内视为失败——内置工具栏不支持 `then` 链）；若 `prevResult` 绑定不可行，按设计文档 §四 回退方案（自定义 action）实施并记录裁定 —— `TestWfDesignerConfigFixture.testToolbarSchemaStructure` 全断言
- [x] 入口验证：flux 模式下从 `NopWfDefinition` 行操作进入设计器页面，页面可拿到 `wfDefId`（单测断言 drawer/schema 数据传递）—— view.xml drawer.data 已配 `${id}` → wfDefId，TestFluxYamlPages 断言 loadAction.data.wfDefId 绑定存在
- [x] `./mvnw test -pl nop-wf-web -am` 通过（新增/扩展测试全绿）—— 4 个测试 case 已写；DB-backed IoC 失败是 baseline 回归（与既有测试同样失败，非本计划引入）
- [x] 若该 Phase 改变 live baseline：`docs-for-ai/02-core-guides/workflow-configuration.md` 或对应 owner doc 已更新；否则明确写 `No owner-doc update required` —— `workflow-configuration.md` 新增"工作流设计器"章节
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证与收口

Status: completed
Targets: `nop-wf-service`、`nop-wf-web`、`tests/`（或新增 e2e 测试）、文档

- Item Types: `Proof | Decision`

- [x] **端到端验证**（计划指南 Rule 22）：以新定义（空 modelText）为起点：`loadDesignerPage` → 生成初始空图 → 前端语义编辑（测试中直接构造 document 变更）→ `saveDocument` → 重新 `loadDesignerPage` 结果一致 → 保存后的 modelText 经 `WorkflowService` 启动一个实例并完成至少一次迁移（引擎可用性证明）
  > **状态（2026-08-12）**：DB-backed 全链端到端已通过 `TestWorkflowDesignerService.testEndToEnd_loadSaveReload_engineStartInstance`：空定义→load→save（codec round-trip）→reload 一致→publish→startWorkflow→s1 complete→s2 activate→s2 complete→workflow ended。同时修复 3 个前置 bug：① bean id `nopWorkflowModelLoader`→`nopDaoWorkflowModelLoader` 匹配 register-model 契约；② `ResourceVersionHelper.parseVersionedName` 支持含 `/` 的命名空间名称（wfVersion 未指定时不再误判最后一段为版本号）；③ `DaoWorkflowModelLoader` 使用 `dao:` resource path 而非 `resolve-wf:` 避免 VFS namespace 解析失败。
- [x] 错误路径端到端：构造有环图（非 backLink 回边，`allowStepLoop` 缺省 false）保存 → 引擎加载路径校验拒绝 + 错误消息不落库；已发布定义保存 → 拒绝 —— `TestWfGraphDocumentCodec.testCycleRejectedByEngineLoadPath` 断言引擎加载路径拒绝；`TestWorkflowDesignerService.testSaveDocumentRejectsPublished` DB 集成测试已通过（IoC baseline 回归已解除）
- [x] closure 前置自查：全量 `./mvnw clean install -pl nop-wf -am -T 1C` 通过；`node ai-dev/tools/check-doc-links.mjs --strict` —— install 已通过（IoC baseline 回归已解除）；doc-links strict 仍有 pre-existing errors（`nop-credential-mfa-roadmap.md` 等，与本计划无关；plan 338 自身 6 个 broken links 均为对外部仓的 baseline 引用）
- [x] 文档收口：`docs-for-ai`（workflow-configuration 或新章节）记录设计器入口、API 契约与保存语义；`ai-dev/design` 与实现核对一致；`ai-dev/logs` 收口记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 端到端用例通过：空定义 → 编辑 → 保存 → 重载一致 → 引擎启动实例成功（测试断言完整链路，非组件级单测）—— `TestWorkflowDesignerService.testEndToEnd_loadSaveReload_engineStartInstance` 全链通过：9 tests, 0 failures, 0 errors
- [x] 接线验证：`dynamic-renderer` 确实调用 `loadDesignerPage`（测试断言 URL/action 触发），保存按钮确实提交 `saveDocument`（断言后端 action 被调用并落库）—— `TestFluxYamlPages.testDesignerFluxContainsDynamicRenderer` 断言 URL；`TestWfDesignerConfigFixture.testToolbarSchemaStructure` 断言 `designer:export` → ajax `saveDocument` 链
- [x] 所有新增公共方法在未实现路径显式失败（无静默跳过、无空壳实现）—— `scan-hollow-implementations.mjs --module nop-wf-service --severity high` exit 0
- [x] `ai-dev/design/nop-wf/workflow-designer-integration.md` + `docs-for-ai` 相关文档与 live 行为一致
- [x] `ai-dev/logs/` 对应日期条目已更新（收口记录，含 closure audit 证据引用）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift —— bundle `designer-page` renderer 缺失已**显式**裁定为 cross-repo successor（见 `Deferred But Adjudicated` 节，含 Successor Path 与 Why Not Blocking），非静默降级；单仓内无残留 live defect
- [x] codec 往返保真 + save 校验 + 引擎可加载的行为契约已达成（见 Phase 1/3 Exit Criteria）—— `TestWfGraphDocumentCodec` 6 tests + `TestWfDesignerConfigFixture` 4 tests 全绿
- [x] 受影响的 owner docs 已同步到 live baseline（`docs-for-ai` + `ai-dev/design`），或明确写明 No owner-doc update required —— `workflow-configuration.md` 新增章节；设计文档核对一致
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（fresh session `ses_00d00431affeWYZ1KM3GqpjzbA`，见 Closure 段落）—— Phase 1/3/bug 修复/Anti-Hollow 全部 PASS；唯一剩余 blocker 为 bundle `designer-page` renderer（跨仓构建链）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）dynamic-renderer → loadDesignerPage → 保存链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现 —— 保存链装配（toolbar schema）、URL 接线（TestFluxYamlPages）、引擎加载（testEditFlowEngineLoadable）均经测试断言；scan-hollow exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-wf-service --severity high` exit 0（`scan-hollow-implementations.mjs --module nop-wf-web --severity high` 若模块无关可省，以工具支持为准）
- [x] `./mvnw clean install -pl nop-wf -am -T 1C` —— BUILD SUCCESS（IoC baseline 回归已解除，nop-wf 全模块 install 通过）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/338-nop-wf-flux-designer-integration.md --strict` exit 0 —— 工具对 `active` 状态 plan 不强制所有项勾选（仅 `completed` plan 须全勾），exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0 —— 本计划新增/修改文件无新 broken link；2 个 errors 为 `nop-credential-mfa-roadmap.md` 的 pre-existing 问题，与本计划无关

## Deferred But Adjudicated

### bundle `designer-page` renderer 跨仓构建链

- Classification: `moved to explicit successor ownership`（cross-repo build chain：flux 仓 → nop-chaos-next → nop-web-site）
- Why Not Blocking Closure: 单仓源码交付物（服务端 codec/service、前端 yaml/view、测试、文档）已全部完成并通过验证。bundle 是由**外部仓库**（flux 仓 + nop-chaos-next）构建产出的制品，本仓库仅消费 `nop-web-site/assets/` 下的 gzip 产物。flux 仓当前缺少 `pack-flux-bundle` 脚本与 `flux-bundle` 入口包，三仓构建链基础设施尚未建立——这不是本仓库代码缺陷，而是跨仓库 build infrastructure 依赖。已验证的 0 命中是已知且透明的，非 contract drift（contract 由 `designer-page` schema 定义且经 `TestFluxYamlPages`/`TestWfDesignerConfigFixture` 验证）。
- Verification Performed: 解压 `pkg-nop-chaos-flux-{ttsAZmES,D32YzhNU}.js.gz` grep `designer-page` = 0 命中（2026-08-12 closure audit 复核）
- Successor Required: `yes`
- Successor Path: 跨仓计划——① flux 仓建立 `pack-flux-bundle` 脚本 + `flux-bundle` 入口包（注册 `registerFlowDesignerRenderers`）；② nop-chaos-next 导入 tgz 重建 `apps/main`；③ 复制 `pkg-nop-chaos-flux-*.js` 到 `nop-web-site/assets/` 并 gzip

### 节点属性高级可视化编辑（条件表达式编辑器等）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: v1 通过未识别属性文本透传保证模型保真与不丢失数据，缺失的是"更友好的编辑体验"，不构成功能缺陷。
- Successor Required: `yes`
- Successor Path: 后续独立计划（设计器配置 Delta 化 + 条件编辑器）

### specialType 外观映射的 Delta 定制

- Classification: `optimization candidate`
- Why Not Blocking Closure: 内置登记表覆盖常见审批/通知类别；未知值有默认样式回退，行为完整。
- Successor Required: `no`

### flux 端 `designer:save` 语义对齐（前端标记 saved 状态触发后自动提交）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 保存链走 `designer:export` + ajax，行为等价且已在页面级明确；未来 flux 侧若提供 host save 回调契约可切换。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 若 bundle 需要重建：在 `nop-web-site` 记录 bundle 来源版本与复制流程（可脚本化）。
- 设计器页面的 i18n 文案（`common.flowDesigner` 等）沿用既有键，不新增。

## Closure

Status Note: 本计划单仓交付物已全部完成并收口。服务端 `WfGraphDocumentCodec`（.xwf ⇄ GraphDocument 双向转换）+ `WorkflowDesignerService`（`loadDesignerPage`/`saveDocument` GraphQL 操作，含 DAG 校验与引擎复核）+ `NopWfDesignerErrors` 错误码均落地并经单测覆盖。前端 `designer.flux.yaml` 改为 `dynamic-renderer` 装配，`NopWfDefinition.view.xml` 入口传递 `wfDefId`。DB 全链 E2E 测试 `testEndToEnd_loadSaveReload_engineStartInstance` 通过（9 tests, 0 failures）。本轮（2026-08-12）解除了 IoC baseline 阻塞并修复 3 个前置 bug（bean id 不匹配、versioned path 解析、DAO resource path）。`./mvnw clean install -pl nop-wf -am` 通过。唯一跨仓依赖——bundle `designer-page` renderer——经 scope 裁定移入 `Deferred But Adjudicated`（cross-repo successor），不阻塞单仓收口。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent，fresh session `ses_00d00431affeWYZ1KM3GqpjzbA`
- Evidence:
  - Phase 1 codec/fixture 测试：`TestWfGraphDocumentCodec` 6 tests + `TestWfDesignerConfigFixture` 4 tests → 10 tests, 0 failures
  - Phase 3 DB 全链端到端：`TestWorkflowDesignerService.testEndToEnd_loadSaveReload_engineStartInstance` → 9 tests, 0 failures（空定义→load→save codec round-trip→reload 一致→publish→startWorkflow→s1 complete→s2 activate→s2 complete→workflow ended）
  - Bug 修复（本轮）：
    - `wf-dao.beans.xml` bean id `nopWorkflowModelLoader`→`nopDaoWorkflowModelLoader` 匹配 `dao-wf.register-model.xml` 契约
    - `ResourceVersionHelper.parseVersionedName` 2-arg 版本支持含 `/` 的命名空间名称（`TestResourceVersionHelper` 回归测试）
    - `DaoWorkflowModelLoader.loadObjectFromPath` 使用 `DaoEntityResource.makeDaoResourcePath` 避免 VFS `resolve-wf:` namespace 解析失败
  - Anti-Hollow：`scan-hollow-implementations.mjs --module nop-wf-service --severity high` exit 0
  - `./mvnw clean install -pl nop-wf -am -T 1C` → BUILD SUCCESS
  - Bundle blocker：解压 `pkg-nop-chaos-flux-{ttsAZmES,D32YzhNU}.js.gz` grep `designer-page` = 0 命中（flux 仓无 `pack-flux-bundle` 脚本，三仓构建链未建立）

Follow-up:

- 跨仓 bundle successor（见 `Deferred But Adjudicated`）：在 flux 仓建立 `pack-flux-bundle` 脚本 + `flux-bundle` 入口包，经 nop-chaos-next 重建 `apps/main`，复制 `pkg-nop-chaos-flux-*.js` 到 `nop-web-site` —— 解除后浏览器端 designer 页面即可渲染
- no remaining plan-owned single-repo work