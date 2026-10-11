# 2384 full-code-review R1.18 — nop-report 双修复（P1-3.6-001 removeCell offset 符号 + P1-3.6-002 权限表休眠声明）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.18；findings `P1-3.6-001`（维度12-01/check:103）+ `P1-3.6-002`（13-01），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.6-report-deep-audit.md`
> Related: P2-3.6-004 测试缺口为 advisory 同批（removeCell 回归测试已在本 plan 落地，随修即消化）；nop-datav 双轨对账归 R2.0 注记
> Owner Doc: `docs-for-ai/03-modules/nop-report.md`

## Purpose

收口 nop-report 两条 P1：①`CellRowExpander`/`CellColExpander.removeCell` 展开区域起点使用减号（offset≠0 时删除错误的行/列）——与同类 `extendCells`/`duplicateCell` 的 `+offset` 语义对齐+补 offset≠0 回归测试；②角色级权限表全仓零消费——按审计处方选项 (2) 显式休眠：module doc 能力声明改为「预留未接线」+ 权限实体标注，消除「配置即安全」误导。

## Current Baseline

- **P1-3.6-001**：`CellRowExpander.removeCell:42` `startIndex = cell.getRowIndex() - getRowExpandOffset()`（CellColExpander:39 同构为 col 版）——而同类 `extendCells`/`duplicateCell` 的展开区域起点均为 `rowIndex + expandOffset`（Row:78/:110-111，Col 同构）。offset≠0 时 removeCell 删除 `[rowIndex-offset, rowIndex-offset+span)` 而 expand 区域实际在 `[rowIndex+offset, ...)`——删错行/列。`check:103` still-live 无处置块，本轮升级立项。既有测试 `TestCellExpanderRemoveCell` 仅覆盖 offset=0（不能暴露符号错误）+ 边界钳制。
- **P1-3.6-002**：`NopReportDefinitionAuth`/`NopReportDatasourceAuth`/`NopReportDatasetAuth` 三张权限表全套存在（orm/dao/BizModel/api/web 菜单），但渲染入口 `ReportEngine.getXptModel/getRenderer*` 与 xpt-rt.xlib 数据集执行零角色判定——权限表「只写不读」。`docs-for-ai/03-modules/nop-report.md:11` 把「角色级报表与数据源访问权限」列为平台能力=契约误导。审计处方二选一：实现角色判定（feature 级）或显式休眠声明。**裁定：选项 (2)**——auth 接线为独立 feature（需要 IUserContext 契约设计+datav 双轨对账），本 plan 做显式休眠声明；实现接线为后续 feature 计划（非 follow-up 降级，是 scope 划分：本 plan 属修复批）。
- 测试基建：nop-batch…（本 plan 指 nop-report-core 已有 `TestCellExpanderRemoveCell` 布景可直接扩展）。

## Goals

- removeCell 起点 `-`→`+`（Row/Col 两处），offset≠0 行为与 extend/duplicate 对齐；offset=0 行为零变化。
- offset≠0 回归测试（Row/Col 各一：offset>0 时移除区域随 offset 右/下移，不再删除模板行/列自身）。
- nop-report.md 能力声明改为「预留未接线」+ 三权限表标注；勘误记录援引 P1-3.6-002 与后续 feature 计划指向。
- nop-report-core 测试全绿。

## Non-Goals

- 实现 auth 角色判定接线（feature 级，独立后续计划——非降级，scope 划分见上）。
- P2-3.6 全部条目（台账行）；ExcelImage calcSize 并发写（P2-3.6-001 台账行）。
- nop-datav 权限双轨的对账动作（归 R2.0 注记）。

## Scope

### In Scope

- nop-report-core：`CellRowExpander.removeCell`/`CellColExpander.removeCell` 符号修正
- nop-report-core 测试：offset≠0 回归（扩展 TestCellExpanderRemoveCell）
- owner doc：nop-report.md 能力声明勘正

### Out Of Scope

- Non-Goals 全部

## Execution Plan

### Phase 1 - removeCell 符号修正 + 回归测试 + 权限休眠声明

Status: completed
Targets: `CellRowExpander`、`CellColExpander`、`TestCellExpanderRemoveCell`、`docs-for-ai/03-modules/nop-report.md`

- Item Types: `Fix`

- [x] `CellRowExpander.removeCell:42`/`CellColExpander.removeCell:39`：`- getRow/ColExpandOffset()` → `+ getRow/ColExpandOffset()`（注释援引 P1-3.6-001 与 extendCells/duplicateCell 同语义）
- [x] 回归测试（扩展 TestCellExpanderRemoveCell）：offset=1、span=1、表格 3 行——修复后移除区域为 [rowIndex+1, rowIndex+2)（不再含 rowIndex 自身所在行）；Row/Col 各一；offset=0 既有两用例保持零变化
- [x] **判别验证**：stash 符号修正后 offset≠0 用例 FAIL（移除区域错位）——恢复后绿
- [x] nop-report.md `:11` 能力声明改为「角色级报表与数据源访问权限（**预留未接线**——权限表可配置但渲染与取数链路当前不校验，见勘误记录）」；`:28`/`:33` 实体表两行加「（预留未接线）」标注；勘误记录援引 P1-3.6-002 + 后续 feature 指向
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] offset≠0 回归测试绿（判别验证记录：stash 后 2 用例 Errors=2 FAIL，恢复后 4/4 绿）
- [x] offset=0 行为零变化（既有用例绿）
- [x] nop-report-core 全量测试绿（86 tests 0 failures）
- [x] nop-report.md 勘正在位且 doc-links 0
- [x] 收口动作（roadmap/index/log）落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.6-001 符号修正 + P1-3.6-002 按处方选项(2) 显式休眠声明）
- [x] 行为/契约结果已达成（offset≠0 判别回归+offset=0 零变化）
- [x] 必要 focused verification 已完成（nop-report-core 全量测试）
- [x] 不存在被静默降级的 in-scope live defect（auth 接线为 feature scope 划分，非降级——休眠声明本身即处方要求的收敛）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（轻量 plan：工具门禁+三方文本一致性核对，见 Closure 段）
- [x] `./mvnw test -pl nop-report/nop-report-core` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 报表 auth 角色判定接线（feature 计划，独立立项；本 plan 的休眠声明为其前置诚实基线）
- P2-3.6 台账行（R1.0 P2 裁决通道）

## Closure

Status Note: P1-3.6-001 符号修正（Row/Col removeCell 起点 -offset→+offset，与 extendCells/duplicateCell 同语义）+ offset≠0 判别回归（stash 判别 FAIL→恢复绿）+ P1-3.6-002 按处方选项(2) 显式休眠声明（nop-report.md 三处：能力声明+两实体行标注「预留未接线」）。auth 接线为 feature scope 划分（后续独立计划），非降级。nop-report-core 86 tests 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 轻量单点 plan——独立性由工具门禁+判别测试+三方文本一致性核对承担（判别测试 stash FAIL→恢复绿为最强判别证据；check-doc-links --strict=0；check-plan-checklist --strict=0；roadmap/index/log 交付摘要一致）
- Evidence:
  - CellRowExpander/CellColExpander removeCell 起点 +offset（注释援引 P1-3.6-001）；git diff 仅符号与注释两处
  - TestCellExpanderRemoveCell 4/4（offset=0 既有 2 用例零变化 + offset≠0 新增 2 用例）；stash 判别 FAIL（2 Errors）→恢复 4/4 绿
  - nop-report.md :11 能力声明+:28/:33 实体行「预留未接线」标注在位；doc-links --strict=0
  - nop-report-core 86 tests 0 failures

Follow-up:

- 报表 auth 角色判定接线（feature 计划独立立项；休眠声明为其前置诚实基线）
- P2-3.6 台账行（R1.0 P2 裁决通道）
