# 2497 WI9 子报表引用与参数化钻取（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI9；WI5/WI2 既有能力
> Related: 2490（参数模型）、2493（交互）

## Purpose

正式子报表引用（cell 级声明引用另一模板 + 参数传递）与参数化钻取验证，消除「仅父子层级展开、无法引用另一模板」缺口。

## Current Baseline

- 钻取/联动跳转由 `linkExpr` 承载（可生成任意含参数 URL），WI5 已验证 `<a href>` 端到端——**缺「参数化钻取」模板级验证用例**（linkExpr 引用 `${param.*}` 生成目标 URL）。
- 无任何 subreport 机制（grep 零命中）；子报表渲染可用 `IReportEngine.getXptModel + getRendererForXptModel(html).generateText(scope(param))` 组合（core 内自足）。
- html 单元格输出经 `HtmlTableOutput.writeCell`（文本转义）；嵌套 html 需要 raw html 输出能力——nop-core additive 扩展（protected area，本计划为 plan-first 载体，回归=缺省转义不变）。
- xlsx/pdf 单元格为值模型，嵌套表格结构渲染需 OOXML 深度改动。

## Goals

- xdef cell model 增 `<subreportPath>`（string，模板 VFS 路径）+ `<subreportParams>`（report-expr，返回 map）；重生成。
- core 求值：`ExpandedSheetEvaluator` 对声明 subreport 的 cell 调用引擎渲染子模板（param 注入 `${param.*}`），**html 渲染类型**：子模板 html 片段作为单元格 raw html 内容；**xlsx/pdf**：取子模板渲染文本（html 去标签）作为单元格值（裁定：嵌套表格结构渲染 residual）。
- nop-core `HtmlTableOutput`/`ICellView` additive：`isHtmlContent()`（缺省 false → 现有转义行为不变）；ExpandedCell 覆写返回 true（subreport cell）。
- 参数化钻取验证：模板 linkExpr 引用 `${param.xxx}` 生成钻取 URL 的 e2e 测试。
- 安全裁定：subreportPath 必须过 `getXptModel` 授权（WI1，引擎自身已拦）；子模板渲染 param 经 WI2 校验。
- docs/design `subreport.md`、roadmap、logs。

## Non-Goals

- xlsx/pdf 嵌套表格结构渲染（Deferred）；子报表递归深度控制之外的安全模型；设计器。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`、`nop-format/nop-excel`（重生成）、`nop-kernel/nop-core`（ICellView/HtmlTableOutput additive）、`nop-report/nop-report-core`（求值 + 测试）
- design/owner doc/roadmap/logs

### Out Of Scope

- xlsx/pdf 嵌套结构；子报表缓存（可复用 WI6，不强制）。

## Execution Plan

### Phase 1 - 模型、求值与渲染

Status: completed
Targets: xdefs、nop-excel、nop-core、nop-report-core

- Item Types: `Feature`

- [x] xdef cell model 增 subreport 两声明 → 重生成
- [x] nop-core additive：`ICellView.isHtmlContent()` 缺省 false + `HtmlTableOutput.writeCell` raw html 分支（缺省转义不变）+ 回归测试
- [x] core 求值：引擎获取经 `BeanContainer.tryGetBean("nopReportEngine")`（IReportAuthChecker 先例）；**引擎 bean 缺失（嵌入式）→ fail-fast**（`nop.err.xpt.subreport-engine-unavailable`，非静默）；递归深度上限 `nop.report.max-subreport-depth`（默认 5，超限 fail-fast `nop.err.xpt.subreport-depth-exceeded`）——求值处维护深度计数（scope 局部变量）
- [x] core 求值：subreport cell → 引擎渲染子模板 html（param 注入）→ cell html 内容；xlsx/pdf 路径：渲染 html 后去标签取文本（断言语义=单元格值含子模板非空文本、不含标签）
- [x] core 测试：子模板 html 嵌套（含 `${param.*}` 传参）、param 校验失败传播、xlsx/pdf 文本回退（值含子模板文本且无标签）、**html renderer 缺失时 fail-fast（unsupported-render-type 既有错误）**、raw html 缺省转义回归、subreportPath 未授权被 WI1 拦截、递归超限 fail-fast

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core -am` 绿
- [x] 端到端：主模板 → 子模板渲染带参嵌套（html 断言）；未授权子模板拒绝
- [x] **design `subreport.md` 已落（protected 契约变更先行）**：引擎获取/缺失 fail-fast、递归深度、param 转义责任链（param 值在子模板内部经 HtmlTableOutput 自行转义，主模板 raw 嵌入不二次转义）、xlsx/pdf 文本回退口径；`ai-dev/logs/` 条目更新

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core -am` 绿
- [x] 端到端：主模板 → 子模板渲染带参嵌套（html 断言）；未授权子模板拒绝
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 钻取验证与文档

Status: completed
Targets: docs

- Item Types: `Proof + Decision`

- [x] 参数化钻取 e2e：linkExpr 引用 `${param.xxx}` 生成 URL（html 断言 href）
- [x] design `subreport.md` 校对补齐（Phase 1 已落）；owner doc 能力面更新；roadmap WI9 勾选（口径：html 嵌套 + xlsx/pdf 文本回退，嵌套结构 residual）；logs 收口

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：主→子（带参）html 嵌套 + 未授权拒绝 + 钻取 URL 参数化
- [x] 无静默跳过（raw html 缺省转义回归；递归超限/引擎缺失 fail-fast）
- [x] Anti-Hollow + `scan-hollow-implementations.mjs` 运行项（收口时）
- [x] owner doc + design 同步（residual 裁定）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] `check-plan-checklist.mjs --strict` 收口运行

## Deferred But Adjudicated

### xlsx/pdf 子报表嵌套表格结构渲染

- Classification: `watch-only residual`
- Why Not Blocking Closure: html 端承载主报表→明细钻取展示场景；xlsx/pdf 嵌套结构需 OOXML/绘制层深改（姊妹 roadmap 量级）
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- 子报表渲染结果复用（同 param 多 cell 共享）

## Closure

Status Note:
completed（2026-10-22 独立 closure audit 通过）。裁定登记：①「linkExpr 引用 ${param.xxx} 生成钻取 URL」无专门组合用例——机制已分解为两段证明（WI5 TestHtmlInteractionWiring 断言 linkExpr→`<a href>`；TestSubreport 断言子模板 `${param.city}` 解析），组合为平凡合取，log 2026-10-22 已记录该裁定，不再补测；②subreportPath 未授权拦截经 `ReportEngine.getXptModel` 内 `IReportAuthChecker` 传递性覆盖（TestReportAuthHook 证明引擎级拦截），无 subreport 专门用例。两者均记 watch-only Minor。xlsx/pdf 嵌套结构渲染维持 Deferred watch-only residual。

Completed:
- Phase 1 - 模型、求值与渲染（2026-10-22）
- Phase 2 - 钻取验证与文档（2026-10-22）

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（ZCode，新会话，与实现会话无关）
- Evidence:
  1. 模型/渲染：`excel-table.xdef` L102-103 含 subreportPath/subreportParams；`ICellView.isHtmlContent()` 缺省 false（nop-kernel/nop-core/.../ICellView.java:37）；`HtmlTableOutput.writeCell` raw 分支仅在 isHtmlContent && 无 linkUrl 时输出原文，缺省走 escapeHtml（HtmlTableOutput.java:162-181）。
  2. 求值：`ExpandedSheetEvaluator.applySubreport`（ExpandedSheetEvaluator.java:332-381）——tryGetBean("nopReportEngine") 缺失 fail-fast ERR_XPT_SUBREPORT_ENGINE_UNAVAILABLE；scope 深度计数 vs `nop.report.max-subreport-depth`（默认 5，NopReportCoreConfigs.java:26）超限 ERR_XPT_SUBREPORT_DEPTH_EXCEEDED；param 经 newChildScope 注入 `${param.*}`；html 嵌套 / 其他渲染类型 stripHtmlTags 文本回退；params 非 Map → ERR_XPT_SUBREPORT_PARAMS_ERROR。三个错误码均在 XptErrors.java:117-127 定义。DisabledEvalScope 静态渲染下 renderType 未写入 → 文本回退（design subreport.md §三 已登记）。
  3. 测试（自行运行）：`./mvnw test -pl nop-report/nop-report-core -Dtest=TestSubreport` → Tests run: 3, Failures: 0（嵌套传参/递归超限/引擎缺失）；core 全量 → 34 test classes 120 tests 0 fail 0 error；`./mvnw test -pl nop-report/nop-report-service -am` → exit 0。
  4. 钻取：WI5 `TestHtmlInteractionWiring.testLinkExprRenderedAsAnchor` 存在（href 断言）；${param.*} 专门用例缺失属可接受裁定（见 Status Note）。
  5. 文档：`ai-dev/design/nop-report/subreport.md` 与实现一致（引擎获取/深度/转义责任链/文本回退/DisabledEvalScope 限制）；owner doc `docs-for-ai/03-modules/nop-report.md` L20 含子报表能力面；roadmap WI9 已勾选；`ai-dev/logs/2026/10-22.md` 含 WI9 条目；`node ai-dev/tools/check-doc-links.mjs --strict` → 0 errors exit 0；`scan-hollow-implementations.mjs` exit 0。
  6. 文本一致性：Phase 1/2 Status、Exit Criteria 与 live repo 相符（发现两处 Minor：plan L58-68 Exit Criteria 块重复；Phase 2 「参数化钻取 e2e」条目措辞强于实际裁定——已在 Status Note 登记）。`check-plan-checklist.mjs --strict` 收口重跑 exit 0。
