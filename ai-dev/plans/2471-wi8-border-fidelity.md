# 2471 WI8 边框线宽/线型保真

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI8，deps WI0 已完成）
> Related: `ai-dev/design/nop-report/pdf-pagination.md`

## Purpose

边框线宽/线型按模型真实值渲染：以 `OfficeBorderStyle.weight`（解析期自 OfficeLineStyle 规范 weight 表写入，对齐 Excel 语义 thin=1/medium=2/thick=3）驱动线宽，替换渲染层 0.5/1/1.5/2pt 硬编码；DOUBLE 间距与虚线节距随宽度比例化。

## Current Baseline

2026-10-19 逐项核对 live repo：

- 模型：`OfficeBorderStyle`（color/type/weight）；`StylesPartParser.parseBorderStyle`（:151-166）解析 xlsx border style 后 `setWeight(type.getWeight())`——枚举规范 WEIGHT 表（OfficeLineStyle.java:53-66）：全部样式缺省 1，medium(0x2)=2、thick(0x5)=3、medium-dashed(0x8)=2、medium-dash-dot(0xA)=2、medium-dash-dot-dot(0xC)=2（**对齐 Excel：thin=1pt、medium=2pt、thick=3pt**）。
- 渲染硬编码：`PdfStyleHelper.getLineWidth`（:391-399）switch 映射 HAIR=0.5/SINGLE=1/MEDIUM=1.5/THICK=2/DOUBLE=1.5——**忽略模型 weight，且 MEDIUM/THICK 与 Excel 语义不符**（1.5/2 vs 2/3）。
- DOUBLE：`drawDoubleLine` 双线偏移 = ±lineWidth（总宽 3×lineWidth）；虚线 DASHED {3}/DOTTED {1}/DASH_DOT 等固定节距不随宽度缩放。
- 缩放：线宽随内容流 CTM 自动缩放（PDF stroke 语义），WI2 缩放管线无需补偿——验证钉住。
- 测试：`TestPdfRenderDefects` 有 DOUBLE 边框虚线复位测试（testDoubleBorderResetsDashPattern）；无线宽专项测试。

## Goals

- `getLineWidth` 优先用 `borderStyle.getWeight()`（>0 时），否则回退 type 规范 weight（type 为 null → 1f）——渲染线宽与 Excel thin/medium/thick 语义一致。
- DOUBLE 双线间距 = lineWidth（±偏移不变，随宽度比例化）；虚线节距随 lineWidth 比例化（DASHED {3×w}、DOTTED {w}、DASH_DOT 等按 w 缩放，保留最小节距下限避免 hair 线糊死）。
- WI2 缩放对线宽的影响测试钉住（fit/scale 下线宽随 CTM 等比）。

## Non-Goals

- 颜色解析（WI9）；斜线/合并单元格边框模型（不动）；xlsx 写回。
- OfficeLineStyle 枚举 WEIGHT 表调整（hair=1 已是模型事实，渲染层对 HAIR 用 0.5f 视觉修正并记 D10——或直接用 weight，实施时按视觉对比裁定，记 D10）。

## Scope

### In Scope

- `PdfStyleHelper.getLineWidth`/`drawBorderLine`（宽度来源 + 虚线节距比例化）。
- 测试：TestPdfBorderFidelity——weight 驱动线宽（thin/medium/thick 渲染宽度采样 1:2:3 比例）、DOUBLE 间距比例、虚线节距、缩放线宽补偿、golden。
- `pdf-pagination.md` D10；roadmap/日志收口。

### Out Of Scope

- WI9 颜色；WI18 能力矩阵。

## Execution Plan

### Phase 1 - weight 驱动线宽与节距

Status: completed
Targets: `nop-report/nop-report-pdf/.../utils/PdfStyleHelper.java`

- Item Types: `Fix`

- [x] `getLineWidth`：weight>0 用 weight；否则 type != null 用 type.getWeight()（顺带修复现 switch 对 null type 的 NPE）；否则 1f。**HAIR 裁定**：Item 1 即 weight 驱动，HAIR(weight=1) 渲染为 1pt（替换现 0.5f 视觉修正），Excel 0.25pt 校准留 Follow-up。
- [x] 虚线节距比例化：DASHED {3w}、DOTTED {w}（w<1 时下限 0.5）、DASH_DOT {3w,1w,1w,1w}、DASH_DOT_DOT 类推；DOUBLE 偏移维持 ±lineWidth（随宽度比例化天然成立）。
- [x] 测试：TestPdfBorderFidelity——(a) **主断言查内容流 `w` 操作符精确值**（PDFStreamParser/forEachOperator，先例 TestPdfSheetRenderFixes:201-218）：thin=1/medium=2/thick=3pt；(b) 像素层 110dpi 只断言严格序 thin<medium<thick（AA 下绝对窗口重叠，比例断言不稳）；(c) DOUBLE 间距随宽度比例（w 操作符 + 像素带）；(d) DASHED 节距随 w 比例（dash 操作符数组断言）；(e) 缩放线宽：**显式 setScale(50)**（不用 fit 推导，避开最小字号钳制）+ 300dpi 渲染断言 scaled thin 宽 < unscaled thin 宽（亚像素下弱断言）。

Exit Criteria:

- [x] 测试全绿且 (a)-(d) 覆盖
- [x] **视觉优先红线**：golden（thin/medium/thick/DOUBLE/虚线）+ 人工抽查
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿；既有 TestPdfSheetRenderFixes 回归绿
- [x] owner-doc（D10）由 Phase 2 承接
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md D10：线宽来源（weight 优先/type 回退）、Excel pt 语义对照、HAIR 0.5 视觉修正裁定、虚线节距比例化与下限、缩放 CTM 补偿说明。
- [x] roadmap WI8 勾选；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI8 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 线宽/节距行为有 focused 测试证明（比例断言非 smoke）
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2471-wi8-border-fidelity.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- hair 线型 Excel 0.25pt 视觉再校准（出现真实模板需求时）。

## Closure

Status Note: WI8 完成：getLineWidth weight 驱动（weight>0 → type 规范 weight → 1，顺带修复 null type NPE）、虚线节距随线宽比例化（下限 0.5）、DOUBLE 偏移随宽度比例；测试 7 个（w 操作符精确 1/2/3pt、类型回退、像素严格序、DOUBLE 外扩、dash 节距 3w、缩放 CTM 等比、golden 四张）。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_7b07114e-267c-4af3-8428-bebead623b09）
- Audit Session: agent_7b07114e-267c-4af3-8428-bebead623b09（2026-10-19）
- Evidence:
  - 回退链逐行核对（PdfStyleHelper.java:397-406；null type NPE 已消除）；WEIGHT 表实证（OfficeLineStyle.java:53-74）；节距比例化各 case 一致
  - 测试 7/7 审计 fresh run 绿；TestPdfSheetRenderFixes 6/6 回归绿（含 DOUBLE 虚线复位）；dash 数组断言 i-2 直接 COSArray 与 PDFStreamParser 实证一致
  - golden 四张基线存在（人工抽查代理证据）；像素扫描垂直线厚方法正确（水平游程初版错误已在偏差记录）
  - 工具（审计实跑）：check-doc-links exit 0；scan-hollow nop-report-pdf exit 0
  - 审计 Minor（watch-only）：dash 测试第二嵌套分支死代码可删；日志已补人工抽查记录行
- Deferred 项分类检查：hair 0.25pt 校准 Follow-up 诚实；无 in-scope live defect 被降级

Follow-up:

- hair 线型 Excel 0.25pt 视觉再校准（出现真实模板需求时）
- dash 测试死代码分支清理（cosmetic）

### 实现期关键偏差记录

- PDFStreamParser 对 `[6 6] 0 d` 的解析：dash 数组为直接 COSArray（元素 COSInt），非嵌套双层结构——断言索引经实证修正（i-2 处）。
- 像素宽度断言首版误用水平游程（水平边框线满宽 183px 恒定）——改为垂直扫描线厚（固定列过边框 y=60pt）。
