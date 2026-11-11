# 2524 docx roadmap WI9：统一出口与 docx 模板编译

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI9）+ docs-for-ai/02-core-guides/word-document-model.md
> Related: ai-dev/plans/2518–2523

## Purpose

静态 docx 与含模板语义的动态文档统一生成出口；把现有 `WordTemplateParser`/`XptWordTemplateParser` 机制模型化（docx → 模型 → 展开 → 输出链路），保留高级 `xpl` 逃生口与原始 OOXML 扩展保留。

## Current Baseline

- 静态出口：`OfficeDocModelWriter`（模型 → docx，WI5）。
- 动态模型链路（WI3/WI4/WI7/WI8）：`OfficeDocModelParser`（docx → 模型，模板语义内嵌：c:if/c:for/expr:/xpl:/xpt:table）→ `OfficeDocModelExpander`（展开 + 图片绑定）→ `OfficeDocModelWriter`（docx）/`OfficeDocPdfRenderer`（PDF）。
- 旧链路（保留不改）：`WordTemplateParser`（docx → XPL 模板 → 生成 docx，支持完整 xpl 标签）、`XptWordTemplateParser`（xpt:table → GenXptTable）。
- 逃生口现状：模型链路对解析期无法结构化表达的形态具名 skip（`getLastParseSkips()`）；doc.model 保留 importLibs/beforeGen/afterGen 等 xpl 字段（xdef 已定义）。
- 各阶段类分散：调用方需自行串接 parse→expand→bind→generate。

## Goals

- 统一出口 `WordDocTemplates`（nop-report-docx，与 Expander 同域）：
  - `generate(PathOrBytes docx|OfficeDocModel, Map dataScope)` → docx bytes：内部串接（按需解析）→ expand → bind → writer；
  - `renderPdf(...)` → PDF bytes（串接 PDF 渲染器）；
  - 输入既接受 docx 字节也接受模型；静态模型（无模板语义）直接生成。
- xpl 逃生口与原始 OOXML 保留口径成文：模型链路 skip 的形态不丢失信息——`getParseSkips` 暴露给出口结果（生成结果携带 skips 元数据）；旧链路仍可用于需要完整 xpl 的场景（出口提供 `generateViaLegacyTemplate` 透传？裁定见 Phase 1）。
- 模板编译链路测试：docx（含模板标注）→ 出口 → 生成 docx（展开后）→ 再解析断言；静态与动态同一入口。
- 原始 OOXML 扩展保留：doc.xdef 未建模的 OOXML 部件（如自定义部件）在出口的保留行为裁定并实现（裁定见 Phase 1）。

## Non-Goals

- 旧链路类行为改动；PDF 渲染器/Expander/Writer/Parser 内部改动（仅串接）。
- GraphQL/REST 服务面；前端。

## Scope

### In Scope

- `nop-report/nop-report-docx`：统一出口类 + 编译链路测试 + pom 依赖裁定（PDF 出口的依赖归属）。
- owner doc / 设计文档决策 / roadmap / 日志。

### Out Of Scope

- 见 Non-Goals；另：doc-model、xdef、ooxml-docx 零改动。

## Execution Plan

### Phase 1 - 出口层决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（设计文档「统一出口层决策」节，§21）：
  - U1 出口落位与 PDF 依赖：出口类落 nop-report-docx；docx 出口为主 API。**PDF 出口裁定：`WordDocPdfTemplates` 落 nop-report-pdf 侧**（其 render 已在该模块；nop-report-docx 对 nop-report-pdf 维持 WI7 的 test 依赖不升级 compile——新增 compile 传递面为 pdfbox/nop-pdf/jts-core/nop-chart-report 全链，成本高于收益；两个出口类各自贴近其渲染依赖）。
  - U2 逃生口口径：模型链路解析 skip 不阻断出口（结果携带 skips 列表，调用方可查）；完整 xpl/复杂标注场景保留旧链路 `WordTemplateParser` 为「高级逃生口」，出口提供 `generateViaLegacyTemplate(docx, scope)` 透传（零包装逻辑，仅命名入口）。原始 OOXML 部件保留裁定：模型未建模部件（customXml/fontTable 等）在模型链路生成时不保留（模型即边界），文档如实声明。
  - U3 静态/动态同入口：输入无模板语义 → 跳过 expand/bind 直接生成（幂等）。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 U1–U3 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 统一出口实现

Status: completed
Targets: `nop-report/nop-report-docx/src/main/java/io/nop/report/docx/`, `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/doc/`

- Item Types: `Feature`

- [x] `WordDocTemplates`（nop-report-docx）：generate/generateViaLegacyTemplate + 输入自适应（bytes/model）+ 结果对象（docx bytes + skips + 表格展开计数 + 图片绑定计数）。
- [x] `WordDocPdfTemplates`（nop-report-pdf）：renderPdf（模型/docx bytes → PDF bytes）+ 结果对象携带 PDF 渲染 warnings（getLastWarnings 对称可观测）。
- [x] 结果元数据：skips（解析具名跳过透传）、表格展开计数、图片绑定计数（可观测性）。
- [x] 错误处理两档惯例；入口参数校验 fail-fast。

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿（既有零回归）；`./mvnw test -pl nop-report/nop-report-pdf` 可编译可测试（新出口类落地）。
- [x] 无静默跳过：非法输入 fail-fast。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 编译链路测试

Status: completed
Targets: `nop-report/nop-report-docx/src/test/java/io/nop/report/docx/`

- Item Types: `Proof`

- [x] `TestWordDocTemplates`：
  - 动态 docx（xpt 表格 + c:for + 图片绑定，程序化构造模型 → writer → bytes）→ generate → 生成 docx 再解析断言展开结果（端到端：入口点到最终输出）；
  - 静态模型同入口直接生成（幂等断言：无模板语义两次生成一致）；
  - renderPdf 出口（WordDocPdfTemplates，nop-report-pdf 模块内测试）→ 文本抽取含展开数据；
  - generateViaLegacyTemplate：真实模板 docx 经旧链路生成成功（逃生口存活证明；test-tpl.docx 在 nop-ooxml-docx test 资源跨模块不可达——在本模块 test 资源复制一份或程序化构造，实现期裁定并报告）；
  - skips 元数据透传断言。
- [x] 既有用例零回归。

Exit Criteria:

- [x] generate/generateViaLegacyTemplate（nop-report-docx）与 renderPdf（nop-report-pdf）各有端到端断言（接线验证）。
- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿；`./mvnw test -pl nop-report/nop-report-pdf` 全绿（新出口类）。
- [x] owner doc 统一出口契约更新；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI9 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 绿；`./mvnw test -pl nop-report/nop-report-pdf` 绿；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-report/nop-report-docx --severity high 与 --module nop-report/nop-report-pdf --severity high 退出码均 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- （已修）WI8 图片绑定污染输入模板 assetRef 的 live defect——本计划探针发现，绑定前物化 image 块副本修复 + 回归测试。
- 单调用 PDF 出口（bytes→PDF 一步）需 pdf→docx compile 边（方案 c），依赖裁定维持现状。

## Closure

Status Note: WI9 三 Phase 完成：WordDocTemplates（generate bytes/model 双入参 + generateViaLegacyTemplate 透传 + WordDocOutput{bytes,skips,展开/绑定计数}）；WordDocPdfTemplates（nop-report-pdf 侧，renderPdf(OfficeDocModel)+WordDocPdfOutput{pdf,warnings}）——方案 (b) pdf compile 依赖 nop-report-docx 被 Maven reactor 环证伪（test 边计入环），按预案回退 (a)：PDF 出口只收模型，bytes→PDF 走 expandToModel→renderPdf 链（诚实记录：无法结构性校验已展开，未展开模板按原样渲染并测试钉住）；U3 静态判定按字段级（解析器总挂 doc.model 壳）+ 出口固定 zip entry time 字节确定性（mutation 探针验证承重）。新用例 11+回归测试；nop-report-docx 43、report-pdf 147、docx 98 全绿。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_880df453-9929-4533-82ad-cdc01a1bb0a9）
- Audit Session: agent_880df453-9929-4533-82ad-cdc01a1bb0a9
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §21 双出口类/逃生口零包装/OOXML 模型即边界/幂等四条与实现一致，且已按方案 (a) PDF 出口形态成文）；Phase 2 PASS（WordDocTemplates generate bytes/model 双入参 + legacy 透传零包装实读确认 + WordDocOutput{bytes,skips,计数}；WordDocPdfTemplates renderPdf(OfficeDocModel) + render-model-required 错误码；U3 静态字段级判定；出口固定 zip entry time 且 assertZipEntryTimesAreFixed 逐 entry 断言）；Phase 3 PASS（TestWordDocTemplates 7 用例：动态端到端含 xpt/c:for/图片绑定/输入未污染断言、bytes 入口真实标注 docx + skips 透传、静态幂等字节一致、逃生口断言「求值非回显」；TestWordDocPdfTemplates 4 用例文本 + warnings + fail-fast）。
  - Closure Gates：`./mvnw test -pl nop-report/nop-report-docx -am` 独立实跑 exit 0（43 用例 0 skipped）；`./mvnw test -pl nop-report/nop-report-pdf` exit 0（147）；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` exit 0（98）；check-doc-links --strict exit 0；scan-hollow 两模块 0 发现；依赖环方案 (b) 被 reactor 环证伪（_tmp/wi9/exp-b-cycle.log）回退 (a) 有实证。
  - Anti-Hollow：出口被端到端测试从入口 bytes/model 驱动到生成输出；无空方法体/no-op。
  - 附带修复（WI9 探针发现的 WI8 live 缺陷）：图片绑定改写共享 image 块 assetRef 污染输入模板——bindBlocks ListIterator + bindImage 物化副本修复 + testBindingDoesNotMutateInputImageBlock 回归。
  - Audit fixes（APPROVED-WITH-FIX 已落实）：M1 owner doc §12 renderPdf 签名单参修正；S1 Status Note 计数 42→43。
  - Deferred/Follow-up 诚实性：方案 c（单调用 PDF 出口）依赖裁定维持现状登记；已修缺陷在 Follow-up 标注（已修）。
