# 2492 WI4 HTML 分页打印保真（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI4；`ai-dev/design/nop-report/html-pagination.md`、`html-acceptance-baseline.md`
> Related: 2488（验收基线）、姊妹 roadmap PDF 分页语义（repeat-header-rows / 页脚占位符）

## Purpose

html 渲染类型具备浏览器打印分页保真：`@page` 页幅、按页分节的分页结构（续页重复表头）、每页页脚（页码占位符），与 PDF 分页语义同名同义；headless 浏览器截图 + DOM 断言验收。

## Current Baseline

- `HtmlReportRendererFactory` 输出连续表格流（`HtmlTableOutput` 已输出 `<thead>/<tbody>/<tfoot>`，行高以 pt 内联、可为 null 需 fallback `defaultRowHeight`）；`ExcelSheet.getPageSetup()`（`ExcelPageSetup`+`ExcelPageMargins`）承载打印设置；`ExcelPrintHelper.getPaperSize/calculatePrintArea` 在 nop-excel（PDF 渲染器同源）。
- PDF 侧分页语义（`&P` 页码/`&N` 总页数、`repeat-header-rows` 默认 1、`default-page-footer` 默认 true）已定型；HTML 侧无任何 `@page`/分页结构。golden PNG 入库有先例（nop-format/nop-chart-echarts test resources golden/*.png）。
- headless Chromium：本机 ms-playwright 缓存（chromium-1223/1243）；e2e workspace 锁 `@playwright/test ~1.60.0`。**执行前须实测** npx playwright@1.60 与缓存 revision 的匹配（必要时允许下载），锁版本策略写入验收 README。
- HTML 渲染出口在 nop-format/nop-excel；`HtmlTableOutput` 的行/单元格渲染方法为包私有且无行区间能力——**本计划裁定扩展 nop-core `HtmlTableOutput` 增加行区间输出 API（additive：`setRowRange(from,to)`，缺省行为不变）**。nop-core 属 protected area，本计划即 plan-first 载体：回归 = 既有 table HTML 输出测试不回归 + 新增区间输出测试，设计记录于 html-pagination.md。

## Goals

- html 渲染器：当 sheet 有 `pageSetup` 时输出分页打印结构——`@page { size; margin }`（取 `ExcelPrintHelper` 纸型/页边距）、按内容区高度把 tbody 行装箱为 `.xpt-page` 节、每节 `<table>` 复制表头行进 thead（续页重复表头，语义对齐 `repeat-header-rows`）、每页页脚（`&P`/`&N` 占位符求值，缺省页脚「第 &P / &N 页」；多 sheet 按 sheet 作用域各自编号，全局作用域留后续，登记 design）。**合并单元格裁定**：装箱断点不得落在 mergeDown 跨界行上——断点回退到合并块起始行之前（整块移入下一页）。**fit/scale 裁定**：分页模式 v1 按原始尺寸装箱、忽略 fitToWidth/fitToHeight/scale 并 log warn + 已知限制登记（PDF 侧 fit 语义不受影响）。无 pageSetup 时输出保持现状（不回归）。
- 验收工具：node + Playwright headless Chromium 打印媒介截图 + DOM 断言；**脚本与 golden 落被测模块测试树**（`nop-format/nop-excel/src/test/html-acceptance/` + `src/test/resources/golden/html-print/`），口径遵循 `html-acceptance-baseline.md`。
- owner doc / design `html-pagination.md` 状态收口、roadmap、logs。

## Non-Goals

- 列拆分（横向分页/关键列重复，`repeat-key-columns` 语义）——本 WI 只做纵向分页；横向留 WI4 后续或随 WI5（裁定记录 design）。
- PDF 侧任何改动；分页预览的交互 UI（WI5）。
- shtml 简化输出不分页（契约既定）。

## Scope

### In Scope

- `nop-format/nop-excel`：分页渲染（`HtmlReportRendererFactory`/新 helper）、`ExcelPrintHelper` 复用、测试
- `nop-format/nop-excel/src/test/html-acceptance/`（脚本 + README）与 `src/test/resources/golden/html-print/`（golden 资产）
- docs/design/roadmap/logs

### Out Of Scope

- 横向分页与关键列重复；nop-report-demo 页面集成；shtml。

## Execution Plan

### Phase 1 - 分页渲染实现

Status: completed
Targets: `nop-format/nop-excel`

- Item Types: `Feature`

- [x] nop-core `HtmlTableOutput` 增行区间 API（`setRowRange(from,to)` additive，缺省全表不变）+ 区间回归测试
- [x] `HtmlPagePagination`（新 helper，nop-excel renderer 包）：输入展开后 sheet（行高含 null→defaultRowHeight fallback、headerCount、pageSetup、mergeDown 结构）→ 输出 `@page` CSS、分页节（tbody 行按内容区高度装箱：headerCount 行每页复制进该页 thead；断点避让 mergeDown 跨界块）、页脚（&P/&N 求值，缺省「第 &P / &N 页」）
- [x] `HtmlReportRendererFactory.renderSheet`：sheet.getPageSetup() 非空时走分页输出，否则现有连续输出；CSS 增 `@media print` 分页规则（`.xpt-page { page-break-after: always }` 末页除外）与 `@page` 规则
- [x] nop-excel 单测：pageSetup 存在时输出含 `@page`、多页节、每节 thead、页脚 &P/&N 求值；无 pageSetup 输出与现状一致（回归断言）；行装箱边界（超高单行成页；mergeDown 跨界断点回退）
- [x] Exit Criteria: `./mvnw test -pl nop-format/nop-excel` 绿；`ai-dev/logs` 条目

### Phase 2 - headless 浏览器验收与收口

Status: completed
Targets: `nop-format/nop-excel/src/test/html-acceptance/`、docs

- Item Types: `Feature + Proof`

- [x] 验收脚本（node + Playwright，落 `nop-format/nop-excel/src/test/html-acceptance/`）：加载渲染 HTML → print 媒介 emulate → 逐 `.xpt-page` 截图 PNG（存 `src/test/resources/golden/html-print/`）+ DOM 断言（页数、每页 thead 行数、页脚文本）；golden diff（像素容差口径沿用基线文档）；README 说明运行方式、锁版本策略（实测 e2e 同款 ~1.60 与本机 chromium revision 匹配）、环境前提（CJK 字体包）
- [x] 用长表 demo 模板生成 HTML 实测跑通脚本，golden 入库（含已知环境差异说明：CJK 字体）
- [x] owner doc 能力面/已知限制（含横向分页、fit/scale 忽略两条裁定）、design `html-pagination.md` 修订「服务端不模拟分页」边界条款为「服务端按行高装箱 + 浏览器 page-break 消费，分页正确性以截图验收裁决」+ 状态 active、roadmap WI4 勾选、logs 收口

Exit Criteria:

- [x] 验收脚本在本机实跑通过（截图 + DOM 断言 + golden diff 全绿），命令与输出记入 logs
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：pageSetup 模板 → html 渲染 → headless 打印截图逐页含重复表头与页脚（golden 在容差内）
- [x] 无 pageSetup 输出不回归（单测断言）
- [x] owner doc + design 同步（横向分页裁定）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-format/nop-excel -am` 绿（触及 nop-report 时跑 `-pl nop-report/nop-report-core -am`）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Deferred But Adjudicated

### 横向分页与关键列重复（repeat-key-columns HTML 等价）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 纵向分页是长表打印主场景；横向分页需列宽装箱与列复制结构，独立成节不与本 WI 交付耦合（roadmap 未把横向列为本 WI 必交付，判定记录于 design）
- Successor Required: yes
- Successor Path: `ai-dev/backlog/nop-report-capability-roadmap.md`（WI4 后续或并入 WI5 执行计划）

## Non-Blocking Follow-ups

- golden 纳入 CI 自动化（当前手动/本机执行）
- 分页预览容器交互（工具栏翻页）归 WI5

## Closure

Status Note:
WI4 全部交付完成并经独立 closure audit（2026-10-22）逐项核验通过。审计中修复 design html-pagination.md 一.2 一处措辞自相矛盾（repeat-key-columns 误写为「等价落地」，已改为「后续交付」并指向边界条款；link checker 复跑退 0）。横向分页/关键列重复与 golden CI 化按 Deferred / Non-Blocking Follow-ups 留后续。

Completed:
- Phase 1（2026-10-22）：nop-core `HtmlTableOutput.setRowRange/setWriteTfoot`（additive，缺省全表）+ `TestHtmlTableOutputRowRange`；nop-excel `HtmlPagePagination`（@page CSS、装箱、mergeDown 断点避让、&P/&N）；`HtmlReportRendererFactory` 分页/非分页双路径 + `TestHtmlPagePagination` 5 用例
- Phase 2（2026-10-22）：`html-acceptance/accept.mjs` + README + golden（4 页 PNG）+ `GenHtmlPrintSample`；owner doc / design / roadmap WI4 / logs 收口

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（新会话 subagent，ZCode / GLM，2026-10-22）
- Evidence:
  1. Phase 1 代码核验 PASS：`HtmlTableOutput.java:55,60`（setRowRange/setWriteTfoot，缺省 rowTo=MAX_VALUE/writeTfoot=true）；`HtmlPagePagination.java`（buildPageCss/packPages/avoidMergeBreak/renderFooter &P&N）；`HtmlReportRendererFactory.renderSheet:134`（isPaginated 双路径，renderPaginatedSheet 含 @media print/.xpt-page 规则）。实跑 `./mvnw test -pl nop-kernel/nop-core -Dtest=TestHtmlTableOutputRowRange` 退 0（2 tests green，surefire 报告确认）；`./mvnw test -pl nop-format/nop-excel -Dtest='TestHtmlPagePagination,GenHtmlPrintSample'` 退 0（5 用例含无 pageSetup 回归断言）。
  2. Phase 2 实跑 PASS：`node nop-format/nop-excel/src/test/html-acceptance/accept.mjs`（仓库根目录）10/10 PASS（页数>1、每页重复表头、页脚、页码、全 tbody 行、golden page-1..4.png 逐字节比对），退 0。README 含运行方式/playwright@1.60 锁版本/CJK 字体前提。
  3. 文档 PASS：design html-pagination.md（装箱边界条款、fit/scale 忽略、&P/&N 缺省页脚、mergeDown 断点避让、横向分页后续交付、状态 active）内部一致（audit 中修复一处 repeat-key-columns 措辞矛盾）；owner doc `docs-for-ai/03-modules/nop-report.md` HTML 分页段含已知限制；roadmap WI4 已勾选；`ai-dev/logs/2026/10-22.md` WI4 条目在案；`node ai-dev/tools/check-doc-links.mjs --strict` 退 0（audit 前后各跑一次）。
  4. 契约一致 PASS：plan Goals（mergeDown 整块避让、fit/scale 忽略+warn 限制登记、&P/&N 缺省「第 &P / &N 页」、验收资产落被测模块测试树）与实现、design、owner doc 三方一致。
  5. 文本一致 PASS：plan Phase 1/2 Status completed、Exit Criteria 全勾与实测相符；Closure Gates 逐条核验后全勾。
