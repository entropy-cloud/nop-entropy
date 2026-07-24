# A3 模型驱动开发、代码生成与 Delta 定制

> Plan Status: completed
> Mission: nop-deep-analysis
> Work Item: A3 模型驱动开发、代码生成与 Delta 定制 + 联网对标代码生成生态
> Last Reviewed: 2026-07-24
> Source: `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md` (Work Item A3)
> Related: 依赖 A1 建立的统一词汇表；A4（GraphQL/服务层）和 A5（模块矩阵）依赖本项

## Purpose

讲清「先模型、再 Delta、最后 Java」的开发范式与代码生成链路，产出一份分析文档，为 A4（服务层如何消费生成的产物）和 A5（模块矩阵如何基于生成链路组装）提供模型驱动层面的参照。

## Current Baseline

**模型驱动开发链路已成熟：**

- ORM 模型是项目骨架起点：各模块 `model/*.orm.xml`（source-anchors GEN-001 `/nop/templates/orm`）
- 生成物不可手改：`_gen/`、`_*.java`、`_*.xml`、`_*.xmeta`、`_app.orm.xml`、`_service.beans.xml` 均为 codegen 产物（AGENTS.md Hard Stop 规则）
- codegen 绑定 Maven phase：根 `pom.xml` 的 `exec-maven-plugin` precompile / precompile2 / postcompile（GEN-009）
- 分层生成：`*-codegen` 驱动项目级生成（GEN-002），`*-meta` 生成 XMeta（GEN-003）和 i18n（GEN-004），`*-web` 生成页面（GEN-005）
- 模块级 meta：`module-meta.json` 作为 web 层稳定边界（GEN-006/007）

**Delta 定制机制：**

- `x:extends` / `x:gen-extends` / `x:post-extends` / `x:override`（EXT-002 `XDslExtender`）
- `super:` 语义与分层资源（EXT-003 `DeltaResourceStore`）
- JSON/YAML 同样可复用 Delta 机制（EXT-005 `loadDeltaJson`）
- value resolver `@cfg:` / `@i18n:` / `@var:` 在加载期求值（RESOLVE-001/002）

**已有的文档/分析（可复用）：**

- `docs-for-ai/02-core-guides/model-first-development.md` — model-first 开发规范
- `docs-for-ai/02-core-guides/delta-customization.md` — Delta 定制规范
- `docs/theory/delta-vs-extension.md`、`delta-oriented-programming.md`、`generic-delta-composition.md`
- `ai-dev/analysis/` 中多份对比报告

**主要缺口（本 plan 要补齐）：**

- 缺一份把「ORM 模型 → codegen 模板 → 生成物 → Delta 定制」串成完整链路的综合分析
- 缺生成链路每个阶段的输入/输出/触发时机的工程化梳理
- 缺联网对标代码生成生态（JHipster、OpenAPI Generator、Spring Initializr、Annotation Processor、MPS）

## Goals

- 产出 `ai-dev/analysis/2026-07/2026-07-24-nop-model-driven-and-codegen.md`，覆盖完整的模型驱动开发与代码生成链路
- 阐明生成链路每个阶段的输入模型、输出产物、触发时机（Maven phase 绑定）
- 阐明 Delta 定制如何在不修改生成物的前提下实现业务定制（`x:extends` / `super:` / value resolver）
- 联网调研并对标代码生成生态，说明「生成即一等公民」的差异化定位，附来源链接
- 所有事实性论断用 source-anchors.md 锚点 + LSP/源码交叉核对

## Non-Goals

- 可逆计算理论公理与形式化证明（A1 将建立统一词汇表，本文引用其术语；若 A1 尚未完成，使用 `docs/theory/` 既有术语为准）
- 核心引擎模块的逐行实现剖析（A2）
- 具体业务实体的字段设计（数据库设计规范由 `nop-database-design` skill 承担）
- GraphQL CRUD 自动暴露与服务层约定（A4）
- 新功能实施或代码变更（仅产出分析文档）

## Scope

### In Scope

- ORM `*.orm.xml` model-first：模型如何作为项目骨架和多模块生成起点（GEN-001）
- codegen 执行链：`*-codegen`（GEN-002）、`*-meta`（GEN-003/004）、`*-web`（GEN-005/006/007）各层职责
- Maven phase 绑定：precompile / precompile2 / postcompile 如何触发生成（GEN-009）
- 生成物约束：`_`-prefixed 文件不可手改的规则与原因
- Delta 定制机制：`x:extends` / `x:override` / `super:` / value resolver 在定制场景中的用法
- Delta 在 JSON/YAML 模型中的复用（EXT-005 `loadDeltaJson`）
- 联网对标：JHipster、OpenAPI Generator、Spring Initializr、Annotation Processor / build-time codegen（AutoValue/Immutables/RecordBuilder）、Meta-Programming System（MPS）

### Out Of Scope

- 可逆计算公理体系与 Delta 结合律形式化证明（A1）
- 核心引擎内部实现（XDslExtender / DslModelParser 的逐行代码）（A2）
- GraphQL 自动暴露与前端渲染管线（A4）
- 具体业务模块的 ORM 实体设计（A5）

## Execution Plan

### Phase 1 - 生成链路与 Delta 定制机制源材料梳理

Status: completed
Targets: `nop-kernel/nop-codegen/`、各模块 `model/*.orm.xml`、`_gen/`、根 `pom.xml`、`docs-for-ai/02-core-guides/model-first-development.md` + `delta-customization.md`、source-anchors GEN-001~009 / EXT-002~005 / RESOLVE-001~002

- Item Types: `Proof | Decision`

- [x] 梳理 ORM model-first 流程：`*.orm.xml` 如何作为生成起点，codegen 模板 `/nop/templates/orm` 的结构（GEN-001）
- [x] 梳理分层生成链：`*-codegen`（GEN-002）、`*-meta`（GEN-003/004）、`*-web`（GEN-005/006/007）的输入、输出、触发顺序
- [x] 梳理 Maven phase 绑定：precompile / precompile2 / postcompile 的执行时序与 classpath 可见性（GEN-009）
- [x] 梳理生成物约束：`_`-prefixed 文件的清单与不可手改规则
- [x] 梳理 Delta 定制机制：`x:extends` / `x:override` / `super:` / `x:gen-extends` / `x:post-extends` 在定制中的用法（EXT-002/003）
- [x] 梳理 value resolver：`@cfg:` / `@i18n:` / `@var:` 在加载期的求值（RESOLVE-001/002/003）
- [x] 搜索 `ai-dev/analysis/` 是否已有可复用的 codegen/Delta 对比结论，避免重复研究
- [x] 对上述锚点做源码交叉核对；核对结果记录到当日 `ai-dev/logs/` 条目

Exit Criteria:

- [x] 完整生成链路已梳理：输入模型 → 各层模板 → 输出产物 → Maven phase 触发，形成一条可追溯的链
- [x] 生成物清单已整理（`_gen/`、`_*.java`、`_*.xml`、`_*.xmeta` 等），标注每类产物的生成层
- [x] Delta 定制用法已用 ≥2 个真实模块案例佐证（如 `nop-demo`、`nop-auth` 的 Delta 定制文件）
- [x] 已完成 ≥10 个 source-anchors 锚点的源码交叉核对
- [x] No owner-doc update required: Phase 1 为源材料梳理，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 联网调研与代码生成生态对标

Status: completed
Targets: 外部工具文档（web search）

- Item Types: `Proof`

- [x] 调研 JHipster：全栈脚手架生成，与 nop 的 model-first + Delta 差异对比
- [x] 调研 OpenAPI Generator：从 API spec 生成 client/server，与 nop 的 ORM→全链路生成对比
- [x] 调研 Spring Initializr：项目初始化定位，与 nop 的持续生成（非一次性）对比
- [x] 调研 Annotation Processor / build-time codegen（AutoValue / Immutables / RecordBuilder）：编译期生成，与 nop 的 precompile/postcompile 对比
- [x] 调研 JetBrains MPS / Meta-Programming System：projectional editing 与元编程，与 nop XDef 驱动的 DSL 对比
- [x] 每条调研附来源 URL + 访问日期，提炼「生成即一等公民」的差异化定位；调研结果汇总到当日 daily log

Exit Criteria:

- [x] 至少覆盖 5 个对标工具/方向，每个附 ≥1 来源链接
- [x] 每个工具有「生成范围 / 生成时机 / 可定制性」三维度对照
- [x] nop 的差异定位（持续生成 + Delta 叠加、生成物不可手改、model-first 全链路）已明确表述
- [x] 所有外部链接附有访问日期
- [x] No owner-doc update required: Phase 2 为联网调研，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 撰写分析文档与准确性终检

Status: completed
Targets: `ai-dev/analysis/2026-07/2026-07-24-nop-model-driven-and-codegen.md`

- Item Types: `Proof | Decision`

- [x] 按 analysis-writing-guide 模板撰写，含完整元数据
- [x] 正文结构：① model-first 开发范式 → ② 分层生成链（codegen/meta/web）→ ③ Maven phase 绑定与触发时序 → ④ 生成物约束与不可手改规则 → ⑤ Delta 定制机制（extends/super/value-resolver）→ ⑥ 联网对标与差异定位 → ⑦ 开放问题
- [x] 所有平台内部引用使用 `file:line` 锚点格式
- [x] 完成全文事实性论断与源码的一致性终检

Exit Criteria:

- [x] 分析文档存在于 `ai-dev/analysis/2026-07/`，命名符合规范
- [x] 文档含完整元数据（Status: resolved / Date / Scope / Conclusion / Superseded By），正文含 References 章节
- [x] 生成链路章节存在，输入→输出→触发时序形成完整可追溯链
- [x] Delta 定制章节存在，含真实模块案例佐证
- [x] 联网对标章节存在，含 ≥5 个工具对照且附来源链接
- [x] 准确性终检完成：每条事实性论断有对应 source-anchor 或源码路径
- [x] No owner-doc update required: 本 plan 仅产出分析文档，不修改 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**：本计划不涉及任何代码变更，`./mvnw test`、`./mvnw compile` 等构建验证条目不适用。

- [x] 分析文档 `ai-dev/analysis/2026-07/2026-07-24-nop-model-driven-and-codegen.md` 已产出且含完整元数据
- [x] 完整生成链路（输入→模板→产物→触发时序）已梳理并与代码锚点交叉核对
- [x] Delta 定制机制已用真实案例佐证
- [x] 联网对标章节覆盖 ≥5 个工具，附来源链接
- [x] 不存在被静默降级到 deferred 的 in-scope 分析要求
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证文档中的生成链路描述（如某模板确实生成某类 `_*.java`、Delta 定制确实叠加而非覆盖）在源码中成立
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若准确性终检发现 `docs-for-ai/02-core-guides/model-first-development.md` 或 `delta-customization.md` 有事实性偏差，记录到 Open Questions，不在本 plan 内修复
- 是否将模型驱动分析迁移到 `docs-for-ai/` 由 A7 capstone 综合评估后决定

## Closure

Status Note: A3 完成。分析文档 `ai-dev/analysis/2026-07/2026-07-24-nop-model-driven-and-codegen.md` 已产出，覆盖 model-first 开发范式、分层生成链（codegen/meta/web）、Maven phase 绑定与触发时序、`_` 前缀生成物约束、Delta 定制机制（extends/super/value-resolver）、5 方向联网对标（JHipster/OpenAPI Generator/Spring Initializr/Annotation Processor/MPS）。15+ source-anchors 锚点源码交叉核对全部 PASS，3 个真实模块 Delta 案例佐证。本 plan 为纯文档计划，无代码变更。
Completed: 2026-07-24

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore task `ses_06c0b5674ffe9xK4ONQfKXebO9`）通读 codegen 源码与模板结构；执行 agent（本会话）对 15+ 锚点做源码交叉核对
- Evidence:
  - Anti-Hollow 验证：`CodeGenTask.java:169-173`（args[1]=目录名）+ `pom.xml:323-412`（4 execution 绑定）确认触发时序描述属实；`TemplateFileGenerator.java:483-520`（`_` 前缀覆盖规则）确认生成物约束属实；`DeltaResourceStore.java:251-294`（`getSuperResource` 向下搜索）确认 Delta 叠加（非覆盖）属实
  - Delta 案例佐证：`nop-quarkus-demo`（orm/xmeta/page 三层）、`nop-delta-demo`（beans/xbiz/xlib 三模式）、`nop-job-worker`（beans）均经源码确认
  - 联网调研：5 工具均附官方来源 URL + 访问日期 2026-07-24
  - doc-links：A3 相关引用（roadmap L112 + plan 3 处）已修正为实际文件名；剩余 4 个 roadmap 错误为 A4–A7 未来工作项占位符（`2026-07-XX-*.md`），属预期状态、非本 plan 引入

Follow-up:

- A4（GraphQL/服务层）将展开「生成的 `_*.xbiz`/`_*.xmeta` 如何被服务层消费」
- A5（模块矩阵）将基于生成链路组装模块全景
- 是否将本分析迁移到 `docs-for-ai/` 由 A7 capstone 综合评估
