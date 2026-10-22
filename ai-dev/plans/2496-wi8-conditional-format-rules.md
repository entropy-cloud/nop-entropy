# 2496 WI8 条件格式规则引擎（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI8
> Related: 姊妹 roadmap PDF 绘制口径；2492 验收 harness

## Purpose

把单元格条件样式从单一 `styleIdExpr` 扩展为声明式规则集（表达式谓词 + 数值区间 + 样式），经既有 styleId 机制三端（xlsx/html/pdf）一致生效。

## Current Baseline

- `XptCellModel.styleIdExpr`（report-expr）在 `ExpandedSheetEvaluator:161-166` 求值 → `cell.setStyleId`；`ExpandedRow.styleIdExpr` 行级同理（:68）。styleId 经 namedStyles/xlsx 样式表机制三端消费（xlsx/html/pdf 渲染器同一 expanded 模型）。
- xpt.xml cell model 支持子元素形式声明（WI5 验证过 `<linkExpr>`）。
- nop-ooxml-xlsx **无** conditionalFormatting（dataBar/colorScale/iconSet）写出能力；新增即 OOXML 新部件 + html/pdf 可视化等价——工作量与姊妹 roadmap 的绘制保真同量级。
- 无条件格式规则相关测试。

## Goals

- xdef 扩展 `XptCellModel` 增 `<condStyles>` 规则集（有序，子元素形式）：`<condStyle styleId="..."/>` + `expr`（xpl-predicate 子元素，求值上下文注入局部变量 `cellValue`=当前单元格值，求值后清理）/ `min`/`max`（子元素数值，可单边；闭区间；min>max 视为不命中），expr 与区间二选一；规则按声明顺序取第一个命中者；全部未命中 → 保持原 styleId/styleIdExpr 结果。**隐式语义裁定：规则覆盖 styleId 后 number format 随之改变（渲染器按 styleId 取格式）——登记 design。**
- 求值挂点：`ExpandedSheetEvaluator` cell 级 styleIdExpr 之后执行（规则可覆盖 styleIdExpr）；行级不做（cell 级已覆盖典型场景）。**规则谓词异常 fail-loud**（NopException + ErrorCode + cell/sheet 参数，与 rowTestExpr 既有语义一致）。
- 预编译：xdef 扩展经 precompile 重生成 `_XptCellModel`/`XptCondStyleModel`；condStyles 谓词经 XptModelLoader/初始化器编译。
- 三端一致性：规则产物是 styleId（样式表机制既有路径）——html 全样式、xlsx 全样式（ExcelWriteSupport 消费 styleId）、**pdf 仅消费 styleId 的字体属性**（PdfTableRenderer.getStyleFont，登记 design 已知限制）。验证以 html class 断言 + xlsx 样式断言（pdf 不单测）。
- 数据条/色阶/图标集：**裁定为 residual**（需 OOXML conditionalFormatting 写出 + html/pdf 可视化等价，量级为独立交付），登记 design + roadmap Deferred。
- design `conditional-format.md`、owner doc、roadmap、logs 收口。

## Non-Goals

- 数据条/色阶/图标集实现（Deferred）；行级规则；公式型 Excel 原生条件格式导出。

## Scope

### In Scope

- `nop-kernel/nop-xdefs` excel-table.xdef（cell model condStyles）、`nop-format/nop-excel` 重生成、`nop-report/nop-report-core` 求值 + 测试
- `ai-dev/design/nop-report/conditional-format.md`、owner doc、roadmap、logs

### Out Of Scope

- 数据条/色阶/图标集；行级规则；设计器 UI。

## Execution Plan

### Phase 1 - 模型扩展与求值

Status: completed
Targets: `nop-kernel/nop-xdefs`、`nop-format/nop-excel`、`nop-report/nop-report-core`

- Item Types: `Feature`

- [x] excel-table.xdef cell model 增 `<condStyles>`（list of condStyle：styleId + expr/min/max）→ precompile 重生成 `_XptCellModel`/`_XptCondStyleModel`
- [x] 求值：ExpandedSheetEvaluator cell 级——styleIdExpr 先、condStyles 后（首中覆盖）；expr 注入 `cellValue` 局部变量；min/max 数值区间（cell 值可转数值时比较，否则不命中）
- [x] core 测试：expr 规则命中/未命中、区间命中/边界/非数值不命中、规则顺序首中、与 styleIdExpr 共存（styleIdExpr 先被规则覆盖）、无规则不回归

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core -am` 绿（含重生成编译）
- [x] html 渲染断言规则样式生效（cell class/style 含目标 styleId）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 文档收口

Status: completed
Targets: docs/design/roadmap/logs

- Item Types: `Decision + Proof`

- [x] design `conditional-format.md`（规则语法/求值顺序/异常 fail-loud/number-format 随 styleId 覆盖/pdf 字体子集/兼容性结论）；owner doc 能力面更新；**roadmap WI8 条目同任务内改写为「表达式/区间规则集」并新增 WI17（数据条/色阶/图标集，deps WI8，todo）**——勾选 WI8 不虚报；logs 收口

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：声明规则模板 → 渲染 → 命中样式可见（html 断言）
- [x] 无静默跳过（规则求值异常 fail-loud 或跳过该规则继续——执行时裁定并登记）
- [x] owner doc + design 同步；roadmap WI8 改写 + WI17 新增（residual 拆分）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] `check-plan-checklist.mjs --strict` 收口运行

## Deferred But Adjudicated

### 数据条/色阶/图标集

- Classification: `watch-only residual`
- Why Not Blocking Closure: 需 OOXML conditionalFormatting 写出部件 + html/pdf 可视化等价（绘制保真量级，属姊妹 roadmap 能力面）；表达式/区间规则已覆盖条件样式主场景
- Successor Required: yes
- Successor Path: `ai-dev/backlog/nop-report-capability-roadmap.md` WI17（条件格式视觉编码，todo）

## Non-Blocking Follow-ups

- 行级条件规则；规则管理 UI

## Closure

Status Note: WI8 表达式/区间条件格式规则集已落地并三端经 styleId 机制消费（pdf 字体子集）；数据条/色阶/图标集拆分至 roadmap WI17（Deferred 已裁定）。闭环时独立审计发现的 owner doc 列表格式回归、Deferred Successor 字段与 WI17 矛盾、TestEmptyBeanContainer 自泄漏、空 @AfterAll 已由审计方当场修复并复跑绿。

Completed: 2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（对抗性审查 + closure audit，fresh session）
- Evidence:
  - Exit Criteria Phase 1：`./mvnw test -pl nop-report/nop-report-service -am`（含 core 全部 117 用例）BUILD SUCCESS，0 Failures/0 Errors（TestCondStyles 3 用例、TestEmptyBeanContainer 1 用例在列）；html 断言（TestCondStyles.testRulesFirstMatchWins 断言 class 含 high/mid/low-style 且不含 base-style）PASS；logs 条目 PASS
  - Exit Criteria Phase 2：`node ai-dev/tools/check-doc-links.mjs --strict` 退 0；design `conditional-format.md`（语法/fail-loud/number-format 随 styleId 覆盖/pdf 字体子集/兼容性）与 live 代码一致；owner doc `docs-for-ai/03-modules/nop-report.md` 能力面为 🟡 受限 bullet（审计修正格式回归）；roadmap WI8 已改写为「表达式/区间规则集」并勾选、WI17 新增（模块表补 17 行、消孤儿空行）
  - Closure Gates：端到端 PASS（xpt 模板 → ReportEngine → html 断言）；无静默跳过 PASS（谓词异常 fail-loud：`ExpandedSheetEvaluator.matches` NopException 带 cell/sheet 参数 rethrow / `nop.err.xpt.cond-style-expr-error` 包装；TestCondStyles.testExprErrorFailLoud 验证）；Anti-Hollow PASS（`scan-hollow-implementations.mjs --module nop-report/nop-report-core --severity high` 退 0；applyCondStyles 由 evaluateCell:171 运行时调用，调用链 xpt 渲染入口 → evaluateCell → applyCondStyles 连通）
  - 跨测试泄漏修复核验：TestReportAuthHook/TestReportDataSecurityHook 的 restore 一律 registerInstance（original==null 时注册 EmptyBeanContainer=未注册语义）——与 rowTestExpr 无关的 flaky 根因（maskResult fake provider 泄漏污染 TestMaxExpandRows 断言）确认修复；TestEmptyBeanContainer 回归用例覆盖（审计补 @AfterAll 恢复 prev 容器，防测试自身泄漏）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2496-wi8-conditional-format-rules.md --strict` 退 0
  - Deferred 分类检查：数据条/色阶/图标集为 watch-only residual + 显式 successor（roadmap WI17），无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（行级条件规则、规则管理 UI 见 Non-Blocking Follow-ups；视觉编码归 roadmap WI17）
