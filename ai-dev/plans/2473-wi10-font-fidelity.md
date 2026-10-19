# 2473 WI10 字体渲染保真（伪粗体/斜体合成 + 变体发现 + ToUnicode 钉住）

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI10，deps WI4 已完成）
> Related: `ai-dev/design/nop-report/pdf-font-strategy.md`、`ai-dev/plans/2471-wi8-border-fidelity.md`

## Purpose

bold/italic 请求在字体变体缺失时不再静默降级为常规体：伪粗体（fill+stroke）与伪斜体（shear 矩阵）渲染合成；变体发现补齐 -Oblique 命名；ToUnicode 复制/搜索正确性测试钉住。

## Current Baseline

2026-10-19 逐项核对 live repo：

- 变体发现：`FontManager.getFontResource`（:543-559）尝试 `/fonts/{-Bold|-BoldItalic|-Italic|base}.{ttf,otf}`（getFontFullName :182-195）；系统字体表含 Helvetica/Courier 的 Bold/Italic/BoldItalic 标准 14 变体（:80-83）。**真实变体发现已覆盖 -Bold/-Italic/-BoldItalic；缺 -Oblique/-BoldOblique 命名**。
- **伪粗体/斜体缺失**：`FontManager.getFont`（:145-169）在变体资源缺失时回退 base 字体/默认字体，bold/italic 请求被静默丢弃——粗体文本渲染为常规体。渲染层 `PdfStyleHelper.drawUnwrappedText/drawWrappedText` 无 renderingMode/shear 处理。
- ToUnicode：PDFBox `PDType0Font.load` 自动生成 ToUnicode CMap——文本提取（复制/搜索）正确性无测试钉住。
- 字体样式源：`ExcelFont.isBold()/isItalic()` → `PdfRenderer.getFont(ExcelFont)` → FontManager。绘制调用点：PdfTableRenderer:140（drawText）、PdfSheetRenderer.renderText:556。

## Goals

- `FontManager` 新增 `FontResolution`（font + syntheticBold/syntheticItalic 标志）：变体命中 → 标志 false；回退 base/默认 → 请求未被满足的维度置 true。
- 渲染：syntheticBold → `RenderingMode.FILL_STROKE`（lineWidth=fontSize/30）；syntheticItalic → 文本矩阵 shear（0.25 斜率）。两条绘制路径（unwrapped/wrapped）均生效。
- 变体发现补齐 `-Oblique`/`-BoldOblique` 命名。
- ToUnicode 钉住：导出含 CJK 文本的 PDF → PDFTextStripper 提取一致（复制/搜索等价）。

## Non-Goals

- 字体子集手动裁剪（PDType0Font.load 默认子集化已满足；不重写）。
- 字体文件入库（D3 保持）；WI18 能力矩阵；&B/&I HF 代码（已剥离，后续）。

## Scope

### In Scope

- `FontManager`：FontResolution 值对象 + resolveFont + -Oblique 发现。
- `PdfStyleHelper.drawText`（unwrapped/wrapped）+ 调用点：合成标志透传与应用。
- 测试：TestPdfFontFidelity——伪粗体（文本提取一致 + renderingMode 操作符断言）、伪斜体（矩阵 shear 或文本提取 + golden）、真实变体（Helvetica-Bold 命中无合成）、ToUnicode CJK 提取、-Oblique 发现；golden。
- `pdf-font-strategy.md`（D 条目追加）；roadmap/日志收口。

### Out Of Scope

- 内置 CJK 字体入库；WI18；&B/&I HF 代码。

## Execution Plan

### Phase 1 - 合成渲染与变体发现

Status: completed
Targets: `FontManager.java`、`PdfStyleHelper.java`、`PdfTableRenderer.java`、`PdfSheetRenderer.java`

- Item Types: `Feature`

- [x] `FontManager.FontResolution`（值对象：PDFont font、boolean syntheticBold、syntheticItalic）+ `resolveFont(fontName, bold, italic, doc)`：现有解析链上判定——getSystemFont/getFontResource 命中变体名 → 非 synthetic；回退 base/默认 → 请求维度未被满足 → synthetic 标志 true。**判定须比对实际命中资源路径/规范化键**（getFontResource 探测序 base.ttf 先于变体 .otf；findDiscoveredFont 会剥离 - 后缀——只看非空会把 base 命中误判为变体命中）。既有 `getFont` 委托 resolveFont 取 font（零行为变化）。
- [x] **fontForText 交互（审查 MAJOR-1）**：`PdfRenderer.fontForText` 在字形缺失时切换 CJK 回退字体（regular）——切换后 synthetic 标志必须按请求维度重置为 synthetic（回退字体无变体）；FontResolution 由 PdfRenderer 暴露（resolveFont + fontForText 协同），渲染层拿到的标志始终与最终绘制字体一致。
- [x] `getFontFullName` 补 `-BoldOblique`/`-Oblique` 命名尝试（italic → -Oblique/-Italic、bold+italic → -BoldOblique/-BoldItalic）。
- [x] 绘制：`PdfStyleHelper.drawText` 增带 FontResolution 的重载；unwrapped/wrapped 路径——syntheticBold → `setRenderingMode(RenderingMode.FILL_STROKE)` + `setLineWidth(max(0.3, fontSize/30))`（w 操作符在 BT/ET 外发出；**同时 setStrokingColor(textColor) + setLineDashPattern(空) 重置**——审查 MAJOR-2：同表 drawBorderLine 在 alpha=255 时不 save/restore，stroking color/dash 泄漏会让伪粗体描边继承红色虚线）；syntheticItalic → `setTextMatrix(new Matrix(1, 0, 0.25f, 1, x, y))` 替代 newLineAtOffset（行向量 shear，y-up 顶部右倾方向正确；wrapped 路径逐行同样替换）。调用点（PdfTableRenderer.renderCell/PdfSheetRenderer.renderText:624）改走 resolveFont 重载。
- [x] 测试：TestPdfFontFidelity——(a) 伪粗体：Helvetica 常规 + bold 请求 → 内容流含 Tr(FILL_STROKE) 操作符 + 文本提取一致 + **描边色继承断言（先红虚线后伪粗体文本，提取一致且无红色描边——MAJOR-2 回归钉）**；(b) 真实变体：Helvetica + bold → 命中 HELVETICA_BOLD（无 FILL_STROKE）；(c) 伪斜体：italic 请求 → 文本渲染（提取一致 + golden）；(d) ToUnicode：**CJK 文本导出 → PDFTextStripper 提取一致（钉在捆绑 /fonts/default.ttf，避开康熙部首映射字形；逐字符断言风格沿用 TestPdfFontResolution:100-102）**；(e) golden 伪粗体页。

Exit Criteria:

- [x] 测试全绿且 (a)-(e) 覆盖；合成标志判定单测（变体命中 vs 回退）
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿；TestPdfSheetRenderFixes/TestPdfRenderDefects/TestPdfFontResolution 回归绿
- [x] owner-doc（pdf-font-strategy.md）由 Phase 2 承接
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 1）：

- resolveFont 判定与 getFontResource 候选序一致化：变体资源判定提取 hasVariantResource（fullName + italic 时 -Oblique），消除"oblique 资源被加载但标志报 synthetic"的判定/加载分歧；bold-only 不探测 -Oblique（regular oblique 不得当 bold 变体，plan 原文 bold-only 探测 -Oblique 的读法被否）。
- fullNameHasStyle 改子串匹配（"Helvetica-BoldItalic" 需同时判中 Bold 与 Italic，"-Italic" 前缀匹配漏判复合名——测试捕捉后修）。
- PdfRenderer 增 FontResolution 解析缓存（fontResolutionCache，与 fontCache 同键）——首版 resolveFont(ExcelFont) 绕过缓存会逐单元格重复 PDType0Font.load（内存/性能回归），测试未捕捉、代码审查自纠。
- 内容流操作符实测：PDFBox 3 setStrokingColor(PDColor) 发 CS+SC（非 RG）；测试扫描器按 SC/RG/G/K 兼容。
- 测试扫描器须跳过绘制后的 renderingMode 还原操作符（"0 Tr"），只认首个 "2 Tr"。
- closure audit Minor-1 同批修复（2026-10-19）：hasVariantResource 与 getFontResource 共用 fontResourceCandidates 候选列表，消除混合扩展名布局（base.ttf + 仅 .otf 变体）下判定/加载分歧；TestPdfFontFidelity+TestFontManager 复跑 13 全绿。

### Phase 2 - 裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-font-strategy.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-font-strategy.md 追加：伪粗体 FILL_STROKE lineWidth=fontSize/30、伪斜体 shear 0.25、FontResolution 语义、-Oblique 发现、ToUnicode 说明。
- [x] roadmap WI10 勾选；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI10 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 合成渲染有 focused 测试证明（操作符/矩阵断言非 smoke）
- [x] 受影响 owner docs 已同步（pdf-font-strategy.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2473-wi10-font-fidelity.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 伪斜体剪切系数可配置化（出现需求时）；&B/&I HF 代码透传（WI5 遗留）。
- 变体资源存在但损坏时 synthetic 标志仍报"已满足"（closure audit Minor-2，观察级：损坏字体边缘场景，失败处理沿用既有 loadFont 回退形态，不另立缺陷）。

## Closure

Status Note: 六项审计全 PASS（接线/语义/无静默跳过/测试有效性/文档一致性/遗留检查），无 Blocker/Major；Minor-1 候选序分歧已同批修复并复跑绿。nop-report-pdf 全量 106 测试绿；全量构建以恢复遍收口（nop-lint-js NodeTscBridgeTest 环境依赖失败与 nop-xlang TestLeaseLifecycle 预存缺陷均属其他工作流范围，证据见本节）。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_2c5b8488-0877-4150-beb9-b0c47778c81e（fresh session，与实现不同 task）
- Evidence:
  - 接线验证 PASS：renderCell（PdfTableRenderer.java:139-145）/renderText（PdfSheetRenderer.java:628-636）/HF runs 延迟路径（PdfRenderer.java:307-323、:343-345）三路标志透传；旧无标志重载零生产调用者；无绕过路径
  - 语义正确性 PASS：resolveFont 六分支逐条核对（FontManager.java:183-225），findDiscoveredFont 剥后缀回退被 exactFile 相等守卫击败；fontForText 按请求维度重置（PdfRenderer.java:102-108）
  - 无静默跳过 PASS：scan-hollow-implementations exit 0（18 文件）；逐方法目检无空实现
  - 测试有效性 PASS：TestPdfFontFidelity 10 测试对应 plan (a)-(e)；MAJOR-2 回归钉场景成立（drawBorderLine alpha=255 不 save/restore，PdfStyleHelper.java:408-411）；golden 基线已建
  - 文档一致性 PASS：D5 逐条对实现一致；roadmap WI10 勾选；Phase 状态与 live code 相符；check-plan-checklist/check-doc-links exit 0
  - Minor-1（hasVariantResource 候选序）：同批修复（fontResourceCandidates 共享），修复后 TestPdfFontFidelity+TestFontManager 13 全绿
  - 构建证据：`./mvnw test -pl nop-report/nop-report-pdf` 106 tests 0 failures；全量 `./mvnw clean install -T 1C` 恢复遍——nop-report-pdf/nop-report-demo SUCCESS（build4 日志 _tmp/wi10-recovery-build4.log），尾部模块恢复遍 build5；排除项均为他工作流范围：TestLeaseLifecycle（xdbg plan 2469 引入的预存稳定失败，单跑复现）、TestDebugGatewayE2E/TestReplService（-T 1C 资源竞争 flaky，单跑 35 全绿）、NodeTscBridgeTest（Node tsc 桥接环境依赖）

Follow-up:

- TestLeaseLifecycle 稳定失败属 xdbg roadmap（plan 2469 引入），已如实上报，非本 plan 范围
