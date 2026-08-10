# 338 nop-wf 集成 flux 通用工作流设计器

> Plan Status: active
> Last Reviewed: 2026-08-10
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

Status: planned
Targets: `nop-wf-service`（`io.nop.wf.service.designer` 包）、`nop-wf-service` 测试资源、`nop-wf` 示例模型

- Item Types: `Fix | Proof`

- [ ] `WfGraphDocumentCodec`：GraphDocument ⇄ 原始 XNode 双向转换，含 3.2.2 转换不变量（白名单提取 + 未识别属性/xpl 片段透传 + `x:extends` 头保留 + 节点 id = step name + start/end/empty 保留 id 合成节点、保存剔除、名称冲突拒绝）
- [ ] `NopWfDesignerErrors` 错误码（定义不存在、模型解析失败、DAG 有环、JSON 非法、已发布禁止保存、节点名冲突、保留 id 冲突）
- [ ] DesignerConfig 模板装配（nodeTypes: start/end/step + specialType 外观映射表；edgeTypes: to-step/to-end/to-empty 经 `match`/`constraints`/`ports` 表达拓扑约束；palette；features）+ **字段合法性 fixture**：对照 flux `flow-designer-core/src/types.ts` 断言输出仅含契约内字段
- [ ] `WorkflowDesignerService` 的 `<bean>` 注册：`nop-wf-service/.../beans/app-service.beans.xml` 显式声明（IoC 无注解扫描，Registrar 先例 auth-service.beans.xml）
- [ ] `WorkflowDesignerService`：`loadDesignerPage(wfDefId)` 返回 designer-page schema（含 schema 字段合法性检查）；`saveDocument(wfDefId, doc)` 经 `NopWfDefinition` 实体 DAO 更新 modelText，写后调 `IWorkflowDefinitionDO.validateModel()` 复核
- [ ] 单测：codec round-trip（覆盖 `nop-wf-service` 现有 examples 目录多个 `.xwf`：串联/分支/会签/reject-withdraw，**含 x:extends 用例**——断言零编辑保存接近零 diff、语义等价）；非法输入 fail-fast（错误码 + 英文消息）；saveDocument 后 modelText 经**引擎加载路径**（XNodeParser + WfModelParser + DAG，即 DaoWorkflowModelLoader 等价链路）重新解析且语义等价

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `WfGraphDocumentCodec` 对每个示例 `.xwf`：`wfToDoc → docToWf → 引擎加载路径再解析` 往返后模型属性不丢失（断言语义等价 + 未识别属性/x:extends 头保留；x:extends 用例零编辑保存接近零 diff）
- [ ] 非法 GraphDocument（重复 id、指向不存在 target（非保留 id）、有环、保留 id 冲突）在 `saveDocument` 中被拒绝并返回对应错误码，不写库
- [ ] DesignerConfig 输出仅含 flux `flow-designer-core/src/types.ts` 契约内字段（fixture 断言，防未知字段静默忽略）
- [ ] `./mvnw test -pl nop-wf-service -am` 通过（新增测试全绿，既有测试无回归）
- [ ] 新增公共 API 无空壳/静默跳过：未实现路径抛 `UnsupportedOperationException` 或业务错误（见计划指南 Rule 24）
- [ ] `ai-dev/design/nop-wf/workflow-designer-integration.md` 与实现一致（如有偏差当轮修正）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 前端页面、入口与 bundle

Status: planned
Targets: `nop-wf-web`（`designer.flux.yaml`、`NopWfDefinition.view.xml`）、`nop-web-site` assets、`nop-wf-web` 测试

- Item Types: `Fix | Decision | Proof`

- [ ] bundle 前置：解压 `pkg-nop-chaos-flux-*.js.gz` 确认 `designer-page` renderer 存在；若缺失，执行三仓构建链：① flux 仓 `flux-bundle/src/index.tsx` 注册 `registerFlowDesignerRenderers` → `pack-flux-bundle.mjs` 产 tgz → ② nop-chaos-next 仓导入 tgz 重建 `apps/main` → ③ 复制 `apps/main/dist/assets/pkg-nop-chaos-flux-*.js`（+ 设计器 CSS）到 `nop-web-site` 并 gzip，两个仓均记录来源 commit hash
- [ ] `designer.flux.yaml` 改造：`body` 改为 `type: dynamic-renderer`，`loadAction` = ajax → `/r/WorkflowDesignerService__loadDesignerPage`，加载期 spinner fallback
- [ ] 保存按钮装配：加载出的 designer-page schema 中 toolbar 保存按钮 = `designer:export` + `then` ajax → `/r/WorkflowDesignerService__saveDocument`（`prevResult`/`result` 绑定导出 JSON；实施时以 flux 运行时实际绑定名验证为准）
- [ ] 入口接线：`NopWfDefinition.view.xml` design 行操作向抽屉页面传递 `${id}`（wfDefId）；验证 flux 模式下抽屉页面能读到该值（必要时改路由式入口，见设计文档 §3.5）
- [ ] 测试：`TestFluxYamlPages` 扩展 —— flux 模式下 `designer.flux.yaml` 可加载且含 dynamic-renderer；后端 mock `loadDesignerPage` 返回的 designer-page schema 结构正确（type/config/document/toolbar）
- [ ] `designer:export` → ajax 保存链的接线验证（见计划指南 Rule 23：确认保存按钮真的会调用后端 action）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] bundle 验证通过：设计器 renderer 已注册（grep `designer-page` 命中 或 运行时渲染测试通过）；若发生 bundle 复制，记录了来源版本 hash
- [ ] `designer.flux.yaml` 在 flux 模式下经 `PageProvider` 可加载，返回的 JSON 顶层含 `dynamic-renderer` 且 loadAction URL 指向 `WorkflowDesignerService__loadDesignerPage`
- [ ] 保存链验证：页面 schema 中保存按钮包含 `designer:export` 与后端 ajax 提交（测试断言 schema 结构）；若 `prevResult` 绑定不可行，按设计文档 §四 回退方案（自定义 action）实施并记录裁定
- [ ] 入口验证：flux 模式下从 `NopWfDefinition` 行操作进入设计器页面，页面可拿到 `wfDefId`（单测断言 drawer/schema 数据传递）
- [ ] `./mvnw test -pl nop-wf-web -am` 通过（新增/扩展测试全绿）
- [ ] 若该 Phase 改变 live baseline：`docs-for-ai/02-core-guides/flux-rendering.md` 或对应 owner doc 已更新；否则明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证与收口

Status: planned
Targets: `nop-wf-service`、`nop-wf-web`、`tests/`（或新增 e2e 测试）、文档

- Item Types: `Proof | Decision`

- [ ] **端到端验证**（计划指南 Rule 22）：以新定义（空 modelText）为起点：`loadDesignerPage` → 生成初始空图 → 前端语义编辑（测试中直接构造 document 变更）→ `saveDocument` → 重新 `loadDesignerPage` 结果一致 → 保存后的 modelText 经 `WorkflowService` 启动一个实例并完成至少一次迁移（引擎可用性证明）
- [ ] 错误路径端到端：构造有环图（非 backLink 回边，`allowStepLoop` 缺省 false）保存 → 引擎加载路径校验拒绝 + 错误消息不落库；已发布定义保存 → 拒绝
- [ ] closure 前置自查：全量 `./mvnw clean install -pl nop-wf -am -T 1C` 通过；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] 文档收口：`docs-for-ai`（workflow-configuration 或新章节）记录设计器入口、API 契约与保存语义；`ai-dev/design` 与实现核对一致；`ai-dev/logs` 收口记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 端到端用例通过：空定义 → 编辑 → 保存 → 重载一致 → 引擎启动实例成功（测试断言完整链路，非组件级单测）
- [ ] 接线验证：`dynamic-renderer` 确实调用 `loadDesignerPage`（测试断言 URL/action 触发），保存按钮确实提交 `saveDocument`（断言后端 action 被调用并落库）
- [ ] 所有新增公共方法在未实现路径显式失败（无静默跳过、无空壳实现）
- [ ] `ai-dev/design/nop-wf/workflow-designer-integration.md` + `docs-for-ai` 相关文档与 live 行为一致
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口记录，含 closure audit 证据引用）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] codec 往返保真 + save 校验 + 引擎可加载的行为契约已达成（见 Phase 1/3 Exit Criteria）
- [ ] 受影响的 owner docs 已同步到 live baseline（`docs-for-ai` + `ai-dev/design`），或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（fresh session，见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` Closure Audit Rule）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）dynamic-renderer → loadDesignerPage → 保存链在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-wf-service --severity high` exit 0（`scan-hollow-implementations.mjs --module nop-wf-web --severity high` 若模块无关可省，以工具支持为准）
- [ ] `./mvnw clean install -pl nop-wf -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/338-nop-wf-flux-designer-integration.md --strict` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

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

Status Note: 实施完成后填写。
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent，fresh session）
- Evidence: （逐条 Exit Criterion / Closure Gate 验证结果 + task id + anti-hollow 检查）

Follow-up:

- 待 closure audit 后填写。