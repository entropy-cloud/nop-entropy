# 2495 WI7 大数据集渲染防线与性能基线（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI7
> Related: 2492（HTML 分页）、性能基线要求

## Purpose

为大数据集报表建立防线与基线：全局展开行数上限（fail-fast 防 OOM）+ 超限告警、迭代式导出链路（游标分页 → ExcelSheetData 迭代 → xlsx 落盘）端到端验证、长表渲染性能基线测试防回归。

## Current Baseline

- 模板级 `expandMaxCount` 已存在（`AbstractCellExpander` 用 `LimitIterator` 限每单元格展开数），但**无全局行数上限**——恶意/失误模板仍可展开百万行导致 OOM。
- `IReportEngine.getRendererForExcelData(Iterator<ExcelSheetData>, template)` 已支持迭代式 xlsx 生成（sheet 数据迭代驱动，非整表入内存）；`JdbcDataSet` 流式取数（保持连接 + streamingFetchSize）已存在。
- html 渲染按行写 Writer（`HtmlTableOutput` 行级输出）；WI4 分页按行高装箱。
- 无任何长表性能基线测试。

## Goals

- core 全局防线：`NopReportCoreConfigs`（varRef 惯例）`nop.report.max-expand-rows`（默认 100_000）——**计数器放 `ExpandedTable`：`insertEmptyRow` 处检查**（行增长唯一 choke point；仅计 needInsert 新增行，expandInplaceCount 复用行不计），超限抛 `nop.err.xpt.max-expand-rows-exceeded`（展开引擎错误归 XptErrors 族；param: 上限）+ LOG.warn；模板级 expandMaxCount 语义不变。
- 迭代式导出端到端：service 测试验证「游标分页读数据（分页 SQL）→ ExcelSheetData 迭代 → getRendererForExcelData → xlsx 落盘」链路，行数正确、内存不随总量线性增长（懒消费口径见 Execution Notes：构造 renderer 零消费 + 全部拉取发生在 generateToFile 期间；批次数不构成证明）。
- 性能基线：core/service 长表渲染基线测试（如 20k 行 html 分页渲染 + 10k 行 xlsx 迭代导出），宽松时延预算（防回归锚点，记录基线值到 logs）。
- 显式裁定：完整惰性展开（expand-on-write 架构，展开引擎逐行消费流式数据集）**不做**——登记 design 已知限制 + roadmap residual（deps WI16 显式裁定口径）；本 WI 交付「防线 + 迭代导出 + 基线」。

## Non-Goals

- 展开引擎惰性化重构；pdf 流式（姊妹 roadmap 已完成其口径）；大数据集聚合下推（BI 层职责）。

## Scope

### In Scope

- `nop-report/nop-report-core`：全局行数上限守卫 + 错误码 + 测试 + 基线测试
- `nop-report/nop-report-service`：迭代式导出 e2e 测试
- `nop-format/nop-excel`：20k 行 html 分页渲染基线（分页实现 `HtmlPagePagination` 所在模块，随实现落位）
- design（html-pagination 或 result-cache 同级新建 `large-dataset.md` 或并入 owner doc——执行时定一处）、owner doc、roadmap、logs

### Out Of Scope

- XDEF/模板指令扩展；pdf 流式；分页下推 SQL。

## Execution Plan

### Phase 1 - 全局行数上限守卫

Status: completed
Targets: `nop-report/nop-report-core`

- Item Types: `Feature`

- [x] `NopReportCoreConfigs`（varRef 惯例）`nop.report.max-expand-rows`（默认 100000）；`ExpandedTable.insertEmptyRow` 处计数检查（唯一 choke point；仅计新增插入行、复用行不计），超限抛 `nop.err.xpt.max-expand-rows-exceeded` + LOG.warn
- [x] design `large-dataset.md` 在本 Phase 内落（protected 行为变更先于/伴随核心改动）：行数上限语义、迭代导出口径、惰性展开裁定
- [x] core 测试：超限 fail-fast（错误码断言）、上限内不受影响、模板级 expandMaxCount 仍生效（两道防线并存）、复用行不计数

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core` 绿
- [x] 无静默跳过：超限必抛错（fail-fast 断言）
- [x] design `large-dataset.md` 已落（行数上限/迭代导出/惰性展开裁定）；`ai-dev/logs/` 条目更新

### Phase 2 - 迭代导出 e2e 与性能基线

Status: completed
Targets: `nop-report/nop-report-service`、docs

- Item Types: `Feature + Proof`

- [x] service e2e：分页游标（3 页 SQL limit/offset）→ ExcelSheetData 迭代 → xlsx 落盘；断言行数与懒消费（口径见 Execution Notes，偏离原文「交错时序」的裁定已登记）；粒度语义：每页=一个 xlsx sheet（sheet 内非流式）
- [x] 性能基线测试：20k 行 html 分页渲染 + xlsx 迭代导出（程序化构造 ExcelWorkbook+expand 模板），时延宽松预算（<60s，基线值记 logs）；跳过机制用 `Assumptions.assumeTrue(系统属性)`（沿 nop-report-pdf 先例；仓库 surefire 无 excludedGroups 配置，仅 @Tag 不生效）
- [x] design `large-dataset.md` 校对补齐（Phase 1 已落）；owner doc 能力面/已知限制（含宽表基线缺口与勾选口径）；roadmap WI7 勾选（口径=长表子项 + 背压防线，宽表 residual 登记）；logs 收口

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] e2e 断言全绿（行数 + 懒消费双证据，口径见 Execution Notes）；基线值记录 logs
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 超限 fail-fast 端到端（模板触限 → 错误码）；迭代导出端到端（游标 → xlsx 行数正确）
- [x] 无静默跳过（超限抛错非截断）
- [x] Anti-Hollow：计数器在行插入 choke point 真实生效（超限 e2e 触发，非事后统计）
- [x] `node ai-dev/tools/check-plan-checklist.mjs --strict` 与 `scan-hollow-implementations.mjs`（收口时运行）
- [x] owner doc + design 同步（惰性展开裁定登记）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Execution Notes

- 懒消费证明口径偏离裁定（closure audit 后收口）：原文「fetch/sheetWritten 交错时序断言」不可实现——`ExcelTemplate` 未对外暴露逐 sheet 写出事件。实现口径=「构造 renderer 零消费迭代器 + 全部拉取发生在 generateToFile 期间」：排除实测怀疑过的失败模式（构造期全量物化）；generateToDir 循环逐 sheet 消费由代码结构保证。design `large-dataset.md` 设计结论 2 与「迭代导出口径」节已同步。
- 模板级 expandMaxCount 语义实测为 LimitIterator 快败（抛错）而非静默截断；`TestMaxExpandRows.testTemplateExpandMaxCountCoexists` 按抛错断言（全局上限保持默认 10 万，失败只能来自模板级限制）。
- 复用行不计数直接证据：`testReuseRowsNotCounted`（max=2 下 `makeRow(1)` 复用已有行不触发检查、`makeRow(2)` 新增插入抛 `nop.err.xpt.max-expand-rows-exceeded`）。

## Deferred But Adjudicated

### 完整惰性展开（expand-on-write）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 展开引擎惰性化是架构级重构（展开语义依赖全表坐标：SUM 公式/父子层级/合并单元格），收益场景（>百万行）可先以 max-expand-rows 防线 + 分页游标迭代导出覆盖；roadmap WI16 允许显式裁定，裁定落 design
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- 宽表（列拆分）基线（状态以 Deferred But Adjudicated 裁定为准）；行数上限按 sheet 独立配置

## Closure

Status Note:
WI7 交付完成：全局展开行数上限防线（`nop.report.max-expand-rows`，默认 10 万，fail-fast 于 `ExpandedTable.insertEmptyRow` choke point）+ 迭代式 xlsx 导出端到端（游标分页 → ExcelSheetData 迭代 → `getRendererForExcelData`，懒消费双证据）+ 长表性能基线 2 项（html 20k 分页 15ms / xlsx 10k×1000 页 1.35s，预算 60s，`-Dnop.test.large-dataset=true` 启用）。完整惰性展开（expand-on-write）显式裁定不做，登记 Deferred But Adjudicated 与 design 已知限制；宽表（列拆分）基线为 residual。收口前独立审计发现 2 Major（core 测试覆盖面缺复用行/expandMaxCount 并存用例；懒消费「交错时序」断言不可实现且三处口径不一致），已修复：补 `TestMaxExpandRows` 2 用例（4/4 绿，3 次复跑稳定）、design/plan 口径同步为实现口径并落 Execution Notes 偏离裁定。

Completed:
- 2026-10-22：Phase 1 + Phase 2 全部条目与 Exit Criteria 完成；两个 Major 审计发现修复后由独立 closure auditor 复核通过，Plan Status 置 completed。

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（新会话，ZCode/GLM-5.3-Flash，2026-10-22 两轮：首轮发现 2 Major；次轮复核修复）
- Evidence:

  1. `./mvnw test -pl nop-report/nop-report-core -Dtest=TestMaxExpandRows`：Tests run: 4, Failures: 0（3 次复跑稳定）；全量 core 113/113 绿（一次全量运行曾现 1 flaky failure，复跑全量绿，未能归因，登记 watch）。
  2. `./mvnw test -pl nop-report/nop-report-service -Dtest=TestLargeDatasetExport`（无 flag）：2 run, 1 skipped（基线按 Assumptions），绿；`-Dnop.test.large-dataset=true` 抽验 10k 行 1000 页 1354ms 绿；html 基线 `TestHtmlPagePagination`（nop-excel）20k 行 15ms 绿。
  3. `./mvnw test -pl nop-report/nop-report-service`：27 run, 0 failures（1 skipped=基线）；`-am` 依赖构建成功。
  4. Anti-Hollow：`ExpandedTable.checkMaxExpandRows()` 挂 `insertEmptyRow`（`ExpandedTable.java:322-333,362-363`）；`testOverLimitFailFast` 从渲染入口触发（模板 expand 6 行 vs max=3），warn 日志实测输出；`testReuseRowsNotCounted` 直接验证复用行不经过检查；`scan-hollow-implementations.mjs` 对本 plan 触及文件零发现（仓库整体既有基线噪音不计）。
  5. 契约一致：fail-fast 拒截断、迭代导出口径（构造期零消费 + generate 期间拉取）、惰性展开 watch-only residual、宽表 residual——plan/design/roadmap/owner doc 四处一致（design `large-dataset.md` 设计结论 2 与三节已按实现口径更新）。
  6. 文档门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退 0；`check-plan-checklist.mjs --strict` 于本文件收口后复跑退 0；roadmap WI7 已勾选且口径注明；`ai-dev/logs/2026/10-22.md` WI7 条目在案。
