# 2493 WI5 HTML 交互与响应式（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI5；`ai-dev/design/nop-report/html-acceptance-baseline.md`
> Related: 2492（分页打印与验收 harness）

## Purpose

html 渲染输出具备交互与响应式能力：超链接/钻取（验证既有链路）、列排序/折叠（渐进增强 script）、editorId/viewerId 交互钩子、窄屏响应式容器；行为以 headless 浏览器断言验收。

## Current Baseline
> 本计划为 nop-core Protected Area 改动（ICellView additive 扩展）的 plan-first 载体：设计记录于 `ai-dev/design/nop-report/html-pagination.md`，回归 = 缺省输出不变测试。

- `linkExpr` 已在展开期求值（`ExpandedSheetEvaluator:189-193` 写入 `ExpandedCell.linkUrl`）；`ExpandedCell implements IExcelCell extends ICellView`，`HtmlTableOutput.writeCell` 已将 linkUrl 输出为 `<a href>`——**超链接链路疑似已通，缺端到端验证测试**（roadmap「仅在模型层」的表述待实测修正）。
- 条件可见：`rowTestExpr`/`colTestExpr` 在展开期丢弃行/列（展开引擎既有能力），渲染层无需改动——需验证测试固化。
- `editorId`/`viewerId` 仅是 XptCellModel/XptXplModel 字段，HTML 输出不带任何交互属性；`ICellView` 接口无对应访问器（nop-core）。
- html 输出无排序/折叠交互、无响应式容器（无 viewport/overflow 处理）。
- WI4 已落验收 harness（`nop-format/nop-excel/src/test/html-acceptance/accept.mjs`，playwright@1.60/chromium-1223）。

## Goals

- 验证并固化既有能力：linkExpr→`<a href>` 端到端测试、rowTestExpr/colTestExpr 条件可见测试（core 层）。
- html 输出渐进增强交互（不依赖外部前端框架）：表头 `data-field` 可点击排序（数值优先、文本 fallback，重排 tbody）；**折叠分组键来源裁定：从展开层级派生（rowParent 父子结构）——子行输出 `data-expand-group="<父行标识>"`，父行输出折叠切换（aria-expanded），不新增 XDEF 指令（不动生成管线）**。排序/折叠由内嵌 script 实现：script 在文档解析时执行并做 document 级事件委托（按报表 id 守卫），**消费契约为文档级解析（file:// 直开 / iframe srcdoc / document.write）；innerHTML 注入不执行 script，属已知限制落 owner doc/design**。多行表头/合并表头下排序映射按 th 所在列的 data-field，无 data-field 的 th 不可排序（保守降级）。
- `editorId`/`viewerId` 渲染为单元格 `data-editor-id`/`data-viewer-id` 属性（前端挂接点）。
- 响应式：报表容器加 `<meta name="viewport">` 由调用方页面负责（裁定：渲染片段不输出 meta，回写 design html-pagination.md），容器 div 增加 `overflow-x:auto` 与窄屏媒体查询 CSS（列宽自适应，DOM/CSS 断言；harness 用 page.setViewportSize 断言）。
- owner doc / design / roadmap / logs 收口；交互行为纳入验收 harness。

## Non-Goals

- 报表间联动的业务路由（钻取目标 URL 由模板 linkExpr 表达）；后端排序（纯前端展示排序）；AMIS 页面集成；分页与排序的组合优化。

## Scope

### In Scope

- `nop-kernel/nop-core`：ICellView additive 扩展（protected area，回归面：缺省输出不变）
- `nop-format/nop-excel`：交互 CSS/JS 输出、data 属性渲染、响应式容器、测试
- `nop-report/nop-report-core`：既有 linkExpr/条件可见能力的验证测试
- `nop-format/nop-excel/src/test/html-acceptance/`：交互行为断言扩展
- docs/design/roadmap/logs

### Out Of Scope

- PDF/xlsx 输出交互；服务端排序接口；设计器（WI13）。

## Execution Plan

### Phase 1 - 既有能力验证 + 交互/响应式实现

Status: completed
Targets: `nop-report/nop-report-core`、`nop-format/nop-excel`

- Item Types: `Feature + Proof`

- [x] core 验证测试：linkExpr 单元格展开后 `ExpandedCell.linkUrl` 求值 + html 渲染含 `<a href="...">`（钻取 URL 语义同）；rowTestExpr 假值行/列被剔除（条件可见固化）
- [x] nop-excel 交互输出：报表容器 div 增加 `xpt-interactive` class 与内嵌 `<script>`（渐进增强：th 点击排序 tbody 数值/文本、折叠组切换 aria-expanded）；排序/折叠仅在有交互标记时激活
- [x] editorId/viewerId：HtmlTableOutput 单元格输出 `data-editor-id`/`data-viewer-id`（cell model 有值时）
- [x] 响应式：容器 CSS `overflow-x:auto` + `@media (max-width:768px)` 字号/列宽自适应规则
- [x] nop-excel 单测：交互 script/属性/CSS 输出断言（html 字符串级）

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core -am` 与 `-pl nop-format/nop-excel -am` 绿
- [x] 无静默跳过：交互 script 在无标记表格上不激活（回归断言）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 浏览器行为验收与收口

Status: completed
Targets: `nop-format/nop-excel/src/test/html-acceptance/`

- Item Types: `Feature + Proof`

- [x] accept.mjs 扩展交互断言：点击表头排序后行序变化（DOM）、折叠组点击后 aria-expanded/hidden 变化、窄屏 viewport 下容器 CSS 生效；golden 重录（含交互态截图至少一张）
- [x] owner doc 能力面/已知限制（含「innerHTML 注入不执行交互 script」限制与消费契约）、design `html-pagination.md`（消费契约 + viewport 裁定 + ICellView 扩展记录）/roadmap WI5 勾选 + Milestone「HTML 打印与交互可用」派生勾选、logs 收口
- [x] 交互脚本与排序语义说明落 owner doc（排序比较规则：数值优先、文本 fallback）

Exit Criteria:

- [x] 验收脚本实跑全绿（含交互断言），命令与输出记入 logs
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Execution Notes（收口对照）

- 排序实现为「th 点击按列号排序（数值优先/文本 fallback）」，未输出 th `data-field`（列号即映射，免 nop-core 二次扩展）；语义与 plan 目标一致。
- 条件可见（rowTestExpr/colTestExpr）为展开期既有能力（TestExpandedSheetEvaluator 已覆盖钳制路径），本计划以 linkExpr/editorId/viewerId e2e 测试固化渲染层接线；未新增展开期测试。
- 交互行为验收落 `nop-format/nop-excel/src/test/html-acceptance/interact.mjs`（独立 screen 媒介 page + interact-fixture.html 静态标记 fixture）；折叠标记派生由 core 单测 TestExpandedRowExpandGroup 固化。
- 实现为幂等 script 安装（`window.__xptInteractive` 文档级守卫）替代 plan 初稿设想的容器 `xpt-interactive` class 标记；「无标记不激活」语义由「无排序头/折叠标记时自然不激活」达成，行为等价。

## Closure Gates

- [x] 端到端：模板 linkExpr/排序/折叠/响应式在 headless 浏览器行为断言全绿
- [x] 无回归：无交互标记输出与 WI4 前结构兼容（分页 golden 不变或重录说明）
- [x] owner doc + design 同步（含 roadmap「仅在模型层」表述勘误）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-format/nop-excel -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 排序/折叠的可配置开关（按报表粒度）

## Closure

Status Note: WI5 已完成。实现、测试、验收、文档收口均经独立 closure audit（2026-10-22）逐条核验通过；详见 Closure Audit Evidence。遗留：本次改动尚未 git commit（不阻塞审计结论，但需尽快提交）。

Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor subagent（新会话，GLM-5.3-Flash via ZCode），2026-10-22
- Evidence（live repo 逐条核验）:
  1. Phase 1 PASS——`ICellView`/`IRowView` additive default 方法（editorId/viewerId=null、expandGroup=null/isExpandGroupHeader=false）实证于 git diff；`ExpandedCell.getEditorId/getViewerId`、`ExpandedRow.getExpandGroup`（rowParent 派生 `row-<rowIndex>`）与 `isExpandGroupHeader`（全表反查父行）覆写到位；`HtmlTableOutput` 输出 `data-editor-id`/`data-viewer-id`/`data-expand-group`/`data-expand-toggle`；`HtmlReportRendererFactory.renderResponsiveCss`（overflow-x:auto + max-width:768px）与 `renderInteractiveScript`（幂等 window 守卫 + 文档级事件委托）落在 sheet div 内、与分页输出共存。`./mvnw test -pl nop-report/nop-report-core -Dtest='TestHtmlInteractionWiring,TestExpandedRowExpandGroup'`：Tests run 5, Failures 0, BUILD SUCCESS。
  2. Phase 2 PASS——`node nop-format/nop-excel/src/test/html-acceptance/interact.mjs` 7 断言全 PASS，exit 0（排序升序/组标记存活/折叠隐藏 2 行/aria-expanded=false/展开恢复/窄屏 overflow-x auto/窄屏字号 12px）；`node .../accept.mjs` 分页 golden 回归 10 断言全 PASS（4 页 golden 逐页比对），exit 0。
  3. 契约一致 PASS——折叠组 rowParent 派生、ICellView additive、消费契约（文档级解析 / innerHTML 注入不执行 script 已知限制）、viewport 调用方负责四处（plan Goals / 实现 / design html-pagination.md 交互段 / owner doc nop-report.md「HTML 交互与响应式」段）口径一致。
  4. 文档 PASS——design html-pagination.md 交互/契约/viewport/ICellView 段在；owner doc 含交互/响应式段 + 已知限制 + linkExpr 链路表述更新；roadmap WI5 勾选 + Milestone「HTML 打印与交互可用」勾选；logs/2026/10-22.md 有 WI5 条目（含两轮审查裁定与实跑记录）。audit 中修复：roadmap WI5 行残留「（当前仅模型层）」陈旧表述改为已渲染为 data 属性的表述；plan 内 interact.mjs 相对链接改全路径。`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors、exit 0。
  5. 文本一致 PASS——plan Phase Status 均 completed、Exit Criteria 勾选与实证相符；Execution Notes 如实披露排序按列号（未输出 th data-field）与条件可见不新增展开期测试两处偏离；audit 另补记 xpt-interactive class → 幂等 window 守卫的实现等价偏离。
  6. 无回归 PASS——renderSheet 中 renderResponsiveCss/renderInteractiveScript 与 isPaginated 分页分支顺序共存（分页 golden 逐页 PASS 即为兼容证据）；`./mvnw test -pl nop-format/nop-excel -Dtest=TestHtmlPagePagination`：Tests run 5, Failures 0, BUILD SUCCESS。
