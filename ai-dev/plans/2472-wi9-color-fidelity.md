# 2472 WI9 颜色保真

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI9，deps WI8 已完成）
> Related: `ai-dev/plans/2471-wi8-border-fidelity.md`、`ai-dev/design/nop-report/pdf-pagination.md`

## Purpose

填充/字体/边框三路颜色统一解析与渲染：统一解析器覆盖 8 位 hex/rgba/0x/裸 hex/命名色，alpha 通道实际渲染（PDF ExtGState），渐变填充裁定。

## Current Baseline

2026-10-19 逐项核对 live repo：

- **双解析器分歧**：填充/字体走 `ColorHelper.toNormalizedRgb`（`#`/`0x` 前缀、8 位剥 alpha、1-2 位补零；**rgba() 直接 IllegalArgumentException → 导出崩溃**）；边框走 `PdfStyleHelper.parseColor`（7 命名色/#/rgb()/rgba()；**不支持 0x 前缀、裸 hex**（如模型 "0xFF0000" 或 "FF0000" → 静默 null → 黑色））。
- **indexed/theme**：解析期已转 rgb 字符串（`StylesPartParser._parseColor` → PredefinedColors.getByIndex / themes.getThemeColor），渲染端不见 indexed/theme 形态——回归测试钉住即可。
- **alpha 渲染**：现渲染（setCellBackground/drawUnwrappedText/drawBorderLine）用 `setNonStrokingColor/setStrokingColor(PDDeviceRGB)` 不携带 alpha——8 位/rgba 的 alpha 通道被静默丢弃。PDF alpha 需 ExtGState（ca/CA）。
- **渐变**：`ExcelStyle` 无渐变字段；xlsx 渐变填充解析不存在——纯裁定项。
- 测试：`TestColorHelper`/`TestPredefinedColorsIndex`（nop-excel）；PDF 渲染色测试仅 drawBorder 颜色间接。

## Goals

- `ColorHelper` 增加规范解析入口 `parseRgba(String) → int[]{r,g,b,a}`（覆盖：7 命名色、`#RRGGBB`/`#AARRGGBB`、`0x` 前缀（6/8 位）、裸 hex（6/8 位）、1-2 位补零、`rgb()`/`rgba()`；非法返回 null 不抛）；`toNormalizedRgb` 改为基于 parseRgba（rgb/rgba 分支补齐，非法回退黑色——**修复 rgba 填充崩溃**）。
- `PdfStyleHelper.parseColor` 委托 ColorHelper（消除双解析器分歧）；alpha<255 的填充/字体/边框渲染经 ExtGState（ca/CA）实际生效。
- 渐变填充裁定（预计 defer，记 D11）。
- 测试：解析单测全形态；渲染测试（8 位 alpha 填充混色采样、rgba 边框、indexed/theme 回归、alpha=255 无 ExtGState 不变）；golden。

## Non-Goals

- 渐变填充渲染（裁定 defer）；WI18 能力矩阵；HTML/XLSX 渲染器。

## Scope

### In Scope

- `ColorHelper.parseRgba` + `toNormalizedRgb` 修复；`PdfStyleHelper.parseColor` 委托 + ExtGState alpha。
- 测试：解析全形态、alpha 混色渲染、回归。
- D11 裁定；roadmap/日志收口。

### Out Of Scope

- 渐变渲染实现；WI18；HTML/XLSX。

## Execution Plan

### Phase 1 - 统一解析与 ExtGState alpha

Status: completed
Targets: `nop-format/nop-excel/.../color/ColorHelper.java`、`nop-report/nop-report-pdf/.../utils/PdfStyleHelper.java`

- Item Types: `Fix | Feature`

- [x] **Fix**：`ColorHelper.toNormalizedRgb` 补 rgba() 支持（现 rgba 填充崩溃）+ **返回 fresh 数组**（调用方 ColorModificationHelper:129-142 原地 mutate，禁止共享常量数组；chart 非法色回退黑行为保留——回归钉）；`parseRgba` 新增规范入口 `int[4]{r,g,b,a}`（非法 null 不抛；named 色复刻 AWT 精确值：green=0,128,0/gray=128,128,128）。
- [x] `PdfStyleHelper.parseColor` 委托 `ColorHelper.parseRgba`（保留 black 等命名色语义）。
- [x] **Feature**：alpha 渲染——alpha<255 时创建 PDExtendedGraphicsState（ca/CA）+ `setGraphicsStateParameters`（PDResources.add 自动注册 gsN 去重；按页按 alpha 值缓存实例）；**gs 作用域经 saveGraphicsState/restoreGraphicsState 收口**（防 alpha 泄漏到后续绘制——泄漏断言测试）；填充/字体/边框三路在 alpha<255 时应用。
- [x] 测试：解析全形态单测（nop-excel：named 精确值/rgba 截断语义钉死）；TestPdfColorFidelity——8 位 alpha 填充混色采样（#80FF0000 叠黄底=橙 (255,127,0) ±2）、rgba 边框渲染、indexed/theme 回归（现有 xlsx fixture）、alpha=255 无 gs 操作符（PDFStreamParser 扫描）、gs 泄漏断言（填充后同页后续绘制不受 alpha 影响）、fresh 数组回归钉、golden。

Exit Criteria:

- [x] 解析单测全绿（全形态含 rgba 填充不再崩溃）
- [x] 渲染测试全绿且 alpha 混色采样正确（红色 50% 透明叠黄底 → 橙色实测）
- [x] `./mvnw test -pl nop-format/nop-excel,nop-report/nop-report-pdf -am` 全绿；既有回归绿
- [x] owner-doc（D11）由 Phase 2 承接
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md D11：统一解析归属（ColorHelper 规范入口）、indexed/theme 解析期转换说明、alpha 经 ExtGState 语义、渐变填充 defer 裁定。
- [x] roadmap WI9 勾选；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI9 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 统一解析与 alpha 渲染有 focused 测试证明
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2472-wi9-color-fidelity.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 渐变填充渲染

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: ExcelStyle 无渐变字段、xlsx 渐变解析不存在，仓库模板零消费者；PDF 渐变需 AxShading 独立实现。
- Successor Required: `no`
- Successor Path: 出现渐变模板需求时立项（解析 + PDShading 渲染）。

## Non-Blocking Follow-ups

- indexed 色遗留表（PredefinedColors 0x40-0x7F 前景/背景相关项）依赖运行时上下文的精确语义（出现需求时调研）。

## Closure

Status Note: WI9 完成：parseRgba 规范解析（全形态 + AARRGGBB alpha 提取 + named AWT 精确值 + fresh 数组）、toNormalizedRgb rgba 崩溃修复、parseColor 委托消除双解析器分歧、ExtGState alpha 渲染（save 先于 gs 注入——初版 gs 在 save 前注入导致泄漏的偏差已修）、测试 6+3=9 个全绿。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_6c571466-972f-4c21-b9a7-279cad16a120 初审 + agent_c70657a7-df7a-48f7-a4e8-1ca13afd6d3a Blocker 修复复核）
- Audit Session: agent_6c571466（初审）/ agent_c70657a7（复核），2026-10-19
- Evidence:
  - 初审发现 Blocker：parseColor 委托勾选不实（旧 switch/Color.decode 未替换）——已修复为真委托（PdfStyleHelper.java:417-422）+ 补边框 0x/裸 hex 回归测试 testBorderColorHexPrefixForms
  - 复核确认：委托落实（全文件无旧 switch 残留）、回归测试真实像素断言、D11 措辞一致、TestPdfColorFidelity 7/7 + TestPdfBorderFidelity 7/7 + TestPdfHeaderFooterRender 9/9 fresh run 绿
  - 行为链其余各项初审全 PASS：parseRgba 全形态、fresh 数组回归钉、gs 顺序（save 先于注入）三路核对、CURRENT_PAGE 配对、alpha=255 不注入、模块全量 BUILD SUCCESS
  - 工具（初审实跑）：check-doc-links exit 0；scan-hollow nop-report-pdf exit 0
  - 审计 Minor（已处置）：ExtGState"去重"措辞修正（每次新建实例，alpha 单元格稀有）；javadoc 错挂修正（parseAlpha/applyAlpha 各归其位）
- Deferred 项分类检查：渐变 defer 诚实（ExcelStyle/解析路径确无 gradient）；无 in-scope live defect 被降级

Follow-up:

- ExtGState 按 alpha 值去重缓存（alpha 单元格密集场景出现时优化）
- indexed 色遗留表语义调研（见 Non-Blocking Follow-ups）

### 实现期关键偏差记录

- 初版 rgba 分支用 parseComponents(int) 解析四段——alpha 为浮点 0.5 导致 parseInt 失败返回 null；改为 rgba 分支专用解析（RGB int + alpha float，保留既有截断语义 floor(a*255)）。
- ExtGState 初版 gs 注入在 saveGraphicsState 之前——gs 修改当前态导致 alpha 泄漏到后续绘制（alpha 作用域收口测试捕捉）；改为 save 先于 gs 注入。
