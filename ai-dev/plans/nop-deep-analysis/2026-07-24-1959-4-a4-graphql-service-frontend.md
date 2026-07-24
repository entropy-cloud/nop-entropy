# A4 GraphQL 引擎、服务层与前后端一体化渲染

> Plan Status: completed
> Mission: nop-deep-analysis
> Work Item: A4 GraphQL 引擎、服务层与前后端一体化渲染 + 联网对标 GraphQL / BFF / 低代码前端
> Last Reviewed: 2026-07-24
> Source: `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md` (Work Item A4)
> Related: 依赖 A1（统一词汇表）、A2（核心引擎：graphql→biz→orm 调用链已梳理）、A3（生成链路：`_*.xbiz`/`_*.xmeta` 如何产出）；A7（capstone）依赖本项

## Purpose

剖析从数据模型到 GraphQL API 再到前端渲染的一体化链路，产出一份分析文档，为 A7（综合评估与演进建议）提供「服务层 + 前后端一体化」层面的参照。补齐 roadmap 主要缺口：缺一份把「BizModel 自动暴露 → GraphQL 统一分发 → xmeta 字段可见性 → AMIS/Flux 两阶段渲染」串成端到端链路的综合分析。

## Current Baseline

**服务层与 GraphQL 已成熟（engine 层面已在 A2 梳理）：**

- A2 已梳理 graphql→biz→orm→eql 端到端调用链（`GraphQLWebService:229`→`GraphQLEngine`→`ReflectionBizModelBuilder`→`BizObjectManager`→`BizActionInvoker`→`IOrmTemplate`），以及 BizModel 自动暴露为 GraphQL operation 的机制。**本 plan 不重复 engine 机制，聚焦 A2 留给本项的 CRUD 约定与服务/前端一体化部分。**

**服务层约定（`docs-for-ai/02-core-guides/`）：**

- `service-layer.md` — BizModel 默认模式：`CrudBizModel<T>`、`@BizQuery`/`@BizMutation`/`@BizAction`、`I*Biz` 服务实现层接口、Processor 拆分时机、`IServiceContext` 透传
- `api-and-graphql.md` — 统一请求分发模型（`/graphql` `/r/` `/p/` `/px/` `/jsonrpc`）、operationName `{bizObj}__{method}` 命名、`@query:` AMIS URL 机制、前端 `operationRegistry` 标准动作签名、`graphql:*` XMeta 属性完整参考

**关键实现锚点（source-anchors.md）：**

- `BIZ-001~007`：`ICrudBiz` 契约、`CrudBizModel` 基类（`requireEntity`/`doFindList`/`doFindPage`/`prepareFindPageQuery`）、跨 BizModel 协作（`I*Biz`）、`@BizLoader` 扩展返回字段
- `GQL-001~008`：`graphql:*` 属性 schema 集合（`OrmFetcherBuilder`、`GraphQLObjMetaHelper`、`ObjMetaToGraphQLDefinition`、`DictLabelFetcherProvider`、`XuiViewAnalyzer`、`ExtPropsGetter`、`GraphQLConstants`）、统一 HTTP 入口（`GraphQLWebService` + `GraphQLNameHelper`，operationName 分隔符 `__`）
- `TXN-001`：`BizActionInvoker` — 非 query 的 Biz 操作默认进事务
- `DOC-001~003`：前端渲染管线文档（框架无关 / AMIS / Flux）
- `UI-001~004`：真实 view.xml 综合参考（树形 CRUD、row action、gen-control、设计器页面）
- `EXT-008/009`：Flux 控件映射库 `flux-control.xlib`（75 标签）+ Flux 页面生成库 `flux-web.xlib`（37 标签）

**前后端渲染管线（`frontend-rendering-pipeline.md`）：**

- 两阶段生成：构建时 codegen（`*-web/precompile/gen-page.xgen`：xmeta → `_gen/_*.view.xml` + `*.view.xml` + `main.page.yaml`）；运行时渲染（`PageProvider__getPage` → `x:gen-extends` → `GenPage` → 框架 JSON）
- 三层 Delta 架构：xmeta（源）→ `_gen/_*.view.xml`（生成基线）→ `*.view.xml`（手写定制）→ `main.page.yaml`（入口）→ 框架 JSON（AMIS/Flux）
- 控件匹配链：`XuiHelper.getControlTag` 按 `control` → `domain` → `stdDomain` → `stdDataType` 优先级匹配

**已有的对比/分析资料（可复用，避免重复研究）：**

- `ai-dev/analysis/2026-07/2026-07-15-amis-dollar-shorthand-vs-expression-syntax.md`
- `ai-dev/analysis/2026-07/2026-07-22-amis-dom-selector-reference.md`
- `ai-dev/analysis/2026-06-19-amis-expression-syntax-unification.md`
- `ai-dev/analysis/2026-06-28-amis-component-schema.md`
- `ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md`
- `ai-dev/analysis/2026-07-11-flux-web-xlib-design-analysis.md`
- `ai-dev/analysis/2026-06-24-compact-ext-field-analysis.md`
- `docs/theory/nop-graphql-design-innovation.md`

**主要缺口（本 plan 要补齐）：**

- 缺一份把「BizModel/xbiz 自动暴露 → GraphQL 统一分发引擎 → xmeta 字段可见性与 `graphql:*` → AMIS/Flux 两阶段渲染」串成**端到端一体化链路**的综合分析（A2 只覆盖 engine 机制，未覆盖 CRUD 约定与前端渲染串联）
- 缺联网对标：Spring for GraphQL、Hasura、Supergraph/Federation、BFF 模式、低代码前端（AMIS 同类如 Formily/Lowcode Engine）

## Goals

- 产出 `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md`，覆盖从数据模型到 GraphQL API 到前端渲染的端到端一体化链路
- 阐明服务层约定：`CrudBizModel` CRUD 默认行为、`@BizQuery`/`@BizMutation`/`@BizAction` 可见性、`I*Biz` 服务实现层接口、Processor 拆分时机
- 阐明 GraphQL 自动暴露：BizModel 如何成为 GraphQL operation、`graphql:*` XMeta 属性如何驱动 connection/filter/orderBy/dict-label、xmeta 字段可见性
- 阐明前后端一体化：xmeta → view.xml（两阶段生成）→ `GenPage` 渲染 → AMIS/Flux 框架 JSON，以及 `@query:` URL 机制如何让前后端共用同一 operationName
- 联网调研并对标 Spring for GraphQL、Hasura、Supergraph/Federation、BFF 模式、低代码前端（Formily/Lowcode Engine），说明前后端一体化的取舍，附来源链接
- 所有事实性论断用 source-anchors.md 锚点 + LSP/源码交叉核对

## Non-Goals

- 核心引擎内部逐行实现（A2 已覆盖 graphql→biz→orm 调用链；本 plan 引用其结论，不重复）
- 可逆计算理论公理（A1）、codegen 生成管线细节（A3）
- 各业务模块的功能矩阵与竞品对标（A5）
- 具体权限模型实现（属 auth 模块专题，A5 概览引用即可）
- 新功能实施或代码变更（仅产出分析文档）

## Scope

### In Scope

- 服务层约定：`CrudBizModel<T>` 默认 CRUD（`requireEntity`/`doFindList`/`doFindPage`/`prepareFindPageQuery` 数据权限与默认 filter/orderBy）、`@BizQuery`/`@BizMutation`/`@BizAction` 可见性分级、`I*Biz` 动态代理契约、Processor 拆分时机、`IServiceContext` 跨服务透传
- GraphQL 自动暴露：BizModel → operation（operationName `{bizObj}__{method}`）、统一分发（`/graphql` `/r/` `/p/` `/px/` `/jsonrpc`）、xbiz `<source>` 与 `BizLoader` 扩展返回字段、`graphql:*` XMeta 属性（queryMethod/connectionProp/filter/orderBy/dictName/labelProp/transFilter）
  - **边界说明（避免与 A2 重复）**：A2 已覆盖分发引擎内部机制（`GraphQLWebService`→`IGraphQLEngine`→BizModel 调用链 + operationName 分隔符 `__`，GQL-008/GQL-002 第二条）。本 plan **引用** A2 的分发结论，仅补充**前端侧消费 operationName 的约定**（`@query:` URL、`operationRegistry` 标准动作签名）与 CRUD/服务层约定，不重述引擎分发机制
- xmeta 字段可见性：GraphQL selection 如何由 xmeta props 约束、`autoCreateField`/`@LazyLoad`、dict 字段自动 `_label`
- 前后端一体化渲染：两阶段生成（codegen + 运行时 `GenPage`）、三层 Delta 架构（xmeta → `_gen/_*.view.xml` → `*.view.xml` → `main.page.yaml`）、控件匹配链、`@query:` AMIS URL 机制与前端 `operationRegistry`、AMIS vs Flux 双渲染管线
- 联网对标：Spring for GraphQL、Hasura、Supergraph/Federation、BFF 模式、低代码前端（Formily/Lowcode Engine）

### Out Of Scope

- 引擎内部调用链逐行代码（A2 已覆盖）
- codegen 模板与 Maven phase 绑定的生成流程（A3）
- 业务模块功能矩阵与逐领域竞品对标（A5）
- ORM 模型字段级数据库设计（由 `nop-database-design` skill 承担）

## Execution Plan

### Phase 1 - 服务层、GraphQL 暴露与前端管线源材料梳理与交叉核对

Status: completed
Targets: `docs-for-ai/02-core-guides/`（`service-layer.md`、`api-and-graphql.md`、`frontend-rendering-pipeline.md`、`amis-rendering.md`、`flux-rendering.md`、`view-and-page-customization.md`）、source-anchors BIZ-001~007 / GQL-001~008 / UI-001~004 / DOC-001~003 / EXT-008~009 / TXN-001、`nop-service-framework/`（nop-biz, nop-graphql-core, nop-graphql-orm）、`nop-frontend-support/nop-web/`

- Item Types: `Proof | Decision`

- [x] 梳理服务层约定：`CrudBizModel` CRUD 默认行为（BIZ-001~007）、`@BizQuery`/`@BizMutation`/`@BizAction` 可见性分级、`I*Biz` 动态代理契约（`BizProxyFactoryBean`/`BizProxyInvocationHandler`）、Processor 拆分、`IServiceContext` 透传
- [x] 梳理 GraphQL 自动暴露：BizModel → operation 映射、统一分发五入口、xbiz `<source>`/`BizLoader`、`graphql:*` XMeta 属性（GQL-001~008，含 queryMethod/connectionProp/filter/orderBy/dictName/labelProp/transFilter）
- [x] 梳理 xmeta 字段可见性：selection 如何由 props 约束、`autoCreateField`/`@LazyLoad`、dict 字段自动 `_label`（`GenDictLabelFields`/`DictLabelFetcher`）
- [x] 梳理前后端渲染：两阶段生成、三层 Delta 架构、`GenPage` 运行时渲染、控件匹配链、`@query:` URL + 前端 `operationRegistry`、AMIS vs Flux（EXT-008/009）
- [x] 搜索 `ai-dev/analysis/` 既有 AMIS/Flux/GraphQL 对比结论，避免重复研究（已列出 7+ 份可复用）
- [x] 对 BIZ/GQL/UI/EXT 锚点做源码交叉核对，确认文档描述与实际代码一致；核对结果记录到当日 `ai-dev/logs/` 条目

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 服务层、GraphQL 暴露、前端渲染三条主线各自的核心职责与关键类/接口已梳理，标注 source-anchor 编号
- [x] 端到端一体化链路已串联：数据模型 → BizModel/xbiz → GraphQL operation → 统一分发 → xmeta 字段可见性 → 两阶段渲染 → 框架 JSON，形成一条可追溯的链
- [x] 已完成 ≥15 个 source-anchors 锚点（BIZ×7/GQL×8/UI/EXT/TXN，对齐 A2 的核对基线）的源码交叉核对，记录结果（PASS/FAIL + 锚点路径）
- [x] No owner-doc update required: Phase 1 为源材料梳理，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 联网调研与 GraphQL/BFF/低代码前端对标

Status: completed
Targets: 外部框架文档与文章（web search）

- Item Types: `Proof`

- [x] 调研 Spring for GraphQL：注解驱动 schema、`@Controller`/`@QueryMapping`，与 nop BizModel 自动暴露对比
- [x] 调研 Hasura：基于数据库 schema 自动生成 GraphQL，与 nop 的 model-first 自动暴露对比
- [x] 调研 Apollo Supergraph / Federation：多服务 schema 组合（federation），与 nop 单引擎统一分发对比
- [x] 调研 BFF（Backend for Frontend）模式：前端专用聚合层，与 nop「同一 BizModel 同时服务 GraphQL/REST/RPC」对比
- [x] 调研低代码前端：Formily（表单/协议驱动）、阿里 Lowcode Engine（页面搭建），与 nop AMIS/Flux 的 xmeta→view→框架 JSON 管线对比
- [x] 每条调研附来源 URL + 访问日期，提炼前后端一体化的差异化定位；调研结果汇总到当日 daily log

Exit Criteria:

- [x] 至少覆盖 5 个对标方向（Spring for GraphQL / Hasura / Supergraph-Federation / BFF / 低代码前端），每个附 ≥1 来源链接
- [x] 每个方向有「该方案做什么 vs nop 做什么 vs 差异点」三段式对照
- [x] nop 的差异定位（model-first 自动暴露、统一分发引擎、xmeta 驱动字段可见性、两阶段 Delta 渲染）已明确表述
- [x] 所有外部链接附有访问日期
- [x] No owner-doc update required: Phase 2 为联网调研，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 撰写分析文档与准确性终检

Status: completed
Targets: `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md`

- Item Types: `Proof | Decision`

- [x] 按 `ai-dev/analysis/00-analysis-writing-guide.md` 模板撰写分析文档，含 Status / Date / Scope / Conclusion / Superseded By / References 元数据
- [x] 正文结构：① 服务层约定（BizModel/CRUD/I*Biz/Processor）→ ② GraphQL 自动暴露与统一分发 → ③ xmeta 字段可见性与 `graphql:*` → ④ 前后端一体化渲染（两阶段/三层 Delta/控件匹配/`@query:`）→ ⑤ 联网对标与差异定位 → ⑥ 开放问题
- [x] 所有平台内部引用使用 `file:line` 锚点格式
- [x] 更新 `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md` 第 127 行 A4 deliverable 路径，从占位符（含 `2026-07-XX` 日期段）修正为实际文件名 `2026-07-24-nop-graphql-service-frontend.md`
- [x] 完成全文事实性论断与源码的一致性终检（逐条核对文档论断 ↔ 代码锚点）

Exit Criteria:

- [x] 分析文档存在于 `ai-dev/analysis/2026-07/`，命名符合 `2026-07-XX-<slug>.md` 规范（实际 `2026-07-24-nop-graphql-service-frontend.md`）
- [x] 文档含完整元数据（Status: resolved / Date / Scope / Conclusion / Superseded By），正文含 References 章节
- [x] 端到端一体化链路章节存在，从数据模型到前端渲染有代码锚点支撑
- [x] roadmap.md A4 deliverable 占位符已修正为实际文件名（`2026-07-XX-` → `2026-07-24-nop-graphql-service-frontend.md`）
- [x] 联网对标章节存在，含 ≥5 个方向对照且附来源链接
- [x] 准确性终检完成：文档中每条涉及代码的事实性论断都有对应 source-anchor 或源码路径可验证
- [x] 若终检发现 `docs-for-ai/02-core-guides/` 有事实性偏差，已记录到 Open Questions（不在本 plan 内修复）
- [x] No owner-doc update required: 本 plan 仅产出分析文档，不修改 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**：本计划不涉及任何代码变更（仅产出 `ai-dev/analysis/` 下的分析文档），`./mvnw test`、`./mvnw compile` 等构建验证条目不适用。

- [x] 分析文档 `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md` 已产出且含完整元数据
- [x] 端到端一体化链路（数据模型→BizModel→GraphQL→xmeta→前端渲染）已梳理并与代码锚点交叉核对
- [x] 服务层约定与 `graphql:*` 属性已明确并与 source-anchors 交叉核对
- [x] 联网对标章节覆盖 ≥5 个方向，附来源链接
- [x] 不存在被静默降级到 deferred 的 in-scope 分析要求
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证文档中的端到端链路描述（如 BizModel 确实自动成为 GraphQL operation、`GenPage` 确实从 view.xml+xmeta 生成框架 JSON、dict 字段确实自动产 `_label`）在源码中成立，非纯文档空谈
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：A4 引入的链接（本 plan 文件 + roadmap A4 行）全部修正为 0 broken link；剩余 error 均为 A5–A7 未启动 deliverable 的 `2026-07-XX-` 占位符（pre-existing，各工作项执行时自然解析，本 mission 标记 non-blocking）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若准确性终检发现 `docs-for-ai/02-core-guides/`（service-layer / api-and-graphql / 前端渲染）有事实性偏差，记录到分析文档 Open Questions，不在本 plan 内修复（属独立文档维护任务）
- 是否将前后端一体化分析迁移到 `docs-for-ai/` 由 A7 capstone 综合评估后决定

## Closure

Status Note: A4 完成。产出分析文档 `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md`，以 xmeta 为单一事实源把「BizModel 自动暴露为 GraphQL operation → 单引擎统一分发 → xmeta 约束字段可见性 → 两阶段生成 view → GenPage 渲染 AMIS/Flux JSON」串成端到端可追溯链路，附 5 方向联网对标。21 个 source-anchors 锚点经源码交叉核对全部 PASS（3 处命名性瑕疵已记入 Open Questions，行为均正确）。roadmap A4 已置 `done`、deliverable 占位符已修正。纯文档计划，无代码变更、无 owner-doc 修改。
Completed: 2026-07-24

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent（fresh session，非实现 session）
- Session id: closure-audit-A4-2026-07-24-independent
- Audit mode: read-only、对抗性核对、对照 live source（非仅文档自证）
- PASS count: 11/11 编号步骤全部 PASS；3/3 Anti-Hollow 端到端链路在源码中成立（非空壳）
- Anti-Hollow evidence:
  - A) `ReflectionBizModelBuilder.java:110-199` 扫四种注解注册 + `GraphQLConstants.java:96` `OBJ_ACTION_SEPARATOR="__"`（BizModel 确实自动成为 GraphQL operation）
  - B) `impl_GenPage.xpl:8,12,13,18-34` 加载 view+xmeta+controlLib 并按 `pageModel.type` 分发到 `page_*.xpl`（GenPage 确实从 view.xml+xmeta 生成框架 JSON）
  - C) `meta-gen.xlib:32-61` `GenDictLabelFields` 标记 `graphql:labelProp="{name}_label"`(L49) 并生成 `{name}_label` 字段带 `dictName`/`dictValueProp`(L53-54)（dict 字段确实编译期自动产 `_label`）
- Roadmap: `nop-deep-analysis-roadmap.md:31` A4=`done`，`:127` deliverable 文件名匹配实际
- Plan checklist: Phase 1/2/3 items + Exit Criteria 全部 `[x]`；Closure Gates 审计前为 `[ ]`（pre-audit 状态，本审计解锁）
- Honesty: Open Questions 如实记录 `DictLabelFetcher` 命名瑕疵 + `GenDictLabelFields` 实为 xlib 标签；Deferred 为空；Non-Blocking Follow-ups 无被降级的 live defect
- 结论: plan 可关闭为 `completed`

Follow-up:

- 文档命名瑕疵（`DictLabelFetcher`/`GenDictLabelFields` 载体描述、`graphql:labelProp` 未在 xdef 声明、`relKind` 控件匹配层未记录、`recoverDeleted` 注解不一致）已记入分析文档 Open Questions，属独立文档维护任务
- 是否将前后端一体化分析迁移到 `docs-for-ai/` 由 A7 capstone 综合评估后决定
- 无 remaining plan-owned work
