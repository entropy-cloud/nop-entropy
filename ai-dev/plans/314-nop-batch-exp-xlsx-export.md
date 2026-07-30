# 314 为 ExportDbTool 增加 xlsx 导出格式

> Plan Status: completed
> Last Reviewed: 2026-07-30
> Source: 用户需求；代码考察见 `ExportDbTool.newConsumer`、`BizExportTaskBuilder.newExcelWriter`、`ExcelResourceIO`
> Related: 无

## Purpose

让 `nop-batch-exp` 的 `ExportDbTool`（即 CLI `export-db` 命令）支持把数据库表导出为 `.xlsx` 文件，使其与已有的 `csv` / `csv.gz` / `sql` 格式处于同等地位。

## Current Baseline

- `ExportDbTool.newConsumer`（`nop-batch-exp/.../ExportDbTool.java:331-350`）是唯一的格式分派点，目前写死三个分支：
  - `"sql"` → `GenInsertSqlRecordIO`
  - `"csv"` / `"csv.gz"` → `CsvResourceRecordIO`
  - 其它 → 抛 `nop.err.dbtool.invalid-exp-format`
- xdef schema（`/nop/schema/db/export-db.xdef:4`）注释写明 `exportFormats` "可以是 csv, csv.gz 和 sql"。属性类型是 `csv-set`，本身不限制取值，所以新增 `xlsx` 无需改属性类型。
- 可复用的 Excel 写出器已存在：`io.nop.report.core.record.ExcelResourceIO`（位于 `nop-report-core`），被 `BizExportTaskBuilder.newExcelWriter`（`nop-batch-biz/.../BizExportTaskBuilder.java:81-98`）用于业务实体导出，内部用流式 `ExcelRecordOutput` 写临时文件再打包 zip，内存友好。
- `nop-batch-exp` 的 `pom.xml` 当前**不依赖** `nop-report-core`。
- `ExportTableConfig.getExportFileName(format)`（`ExportTableConfig.java:29-31`）返回 `name + "." + format`，所以 `xlsx` 格式会自动得到 `tableName.xlsx`。
- 现有导出测试 `test.export-db.xml` 连接外部 MySQL，不适合作为无外部依赖的回归测试；但 `TestImportDbTool`（同模块）用 `@NopTestConfig(localDb = true)` + H2 的模式可参照。
- `ExcelRecordOutput` 中 `ExcelIOConfig.maxCountPerSheet`（默认 20w）是已配置但未接线的字段（写入时不读取），即单 sheet 不自动分页。本计划**不处理**自动分 sheet。

## Goals

- `ExportDbTool` 能识别 `exportFormats` 中的 `xlsx`，将每张表导出为一个 `.xlsx` 文件。
- 导出的 `.xlsx` 可被 `ExcelResourceIO.openInput` 正常读回，行数和字段值与源表一致。
- 复用已有 `ExcelResourceIO`，不新造 Excel 写出逻辑。
- xdef schema 注释更新，反映 `xlsx` 为合法格式。
- 提供一条不依赖外部数据库的端到端回归测试。

## Non-Goals

- 不实现按行数自动分多 sheet（`maxCountPerSheet` 接线）。这是 `ExcelRecordOutput` 层面的独立改动，与本工具的格式扩展正交。
- 不修改 CLI 命令行参数（`CliExportDbCommand` 无需改动，格式由 config 驱动）。
- 不新增 Excel 模板（`.xpt.xlsx`）报表能力——本工具是裸表导出，不是报表渲染。
- 不改动 `csv` / `csv.gz` / `sql` 已有路径的行为。

## Scope

### In Scope

- `nop-batch-exp` 模块：`ExportDbTool` 新增 `xlsx` 分支 + 新增 `newExcelConsumer` 方法。
- `nop-batch-exp/pom.xml`：新增 `nop-report-core` 依赖。
- `nop-xdefs` 模块：`export-db.xdef` 注释更新。
- `nop-batch-exp` 测试：新增端到端导出测试（H2 local DB）。

### Out Of Scope

- `ExcelRecordOutput` 的多 sheet 自动分页（见 Non-Goals）。
- `BizExportTaskBuilder` 路径（已支持 xlsx，无需改动）。
- CLI 层 `CliExportDbCommand`（格式完全由 config 驱动）。

## Execution Plan

### Phase 1 - 接线 xlsx 导出并端到端验证

Status: completed
Targets: `nop-batch-exp/.../ExportDbTool.java`, `nop-batch-exp/pom.xml`, `nop-xdefs/.../export-db.xdef`, `nop-batch-exp/src/test/...`

- Item Types: `Fix | Decision | Proof`

- [x] (Decision) 在 `nop-batch-exp/pom.xml` 新增 `nop-report-core` 依赖——复用已验证的 `ExcelResourceIO`，而非新造写出逻辑
- [x] (Fix) 在 `ExportDbTool` 新增 `newExcelConsumer(String resourcePath, List<String> fields)` 方法：构造 `ExcelResourceIO<Map<String,Object>>`，设置 `headers = fields`，用 `newResourceConsumer(recordIO, resourcePath)` 包成 `ResourceRecordConsumerProvider`，模式对齐 `newCsvConsumer`（`ExportDbTool.java:352-359`）
- [x] (Fix) 在 `ExportDbTool.newConsumer` 的格式分派循环中新增 `"xlsx"` 分支，调用 `newExcelConsumer`（`ExportDbTool.java:335-345`）
- [x] (Fix) 更新 `/nop/schema/db/export-db.xdef:4` 注释：`exportFormats` 合法值增加 `xlsx`
- [x] (Proof) 新增端到端测试 `TestExportDbTool`（参照 `TestImportDbTool` 的 H2 local DB 模式）：建表 → 插入若干行 → 用 `exportFormats="xlsx"` 执行 `ExportDbTool` → 用 `ExcelResourceIO.openInput` 读回生成的 `.xlsx` → 断言行数与字段值一致
- [x] (Proof) 新增测试用 config `test.export-db-xlsx.xml`（H2 连接、`exportFormats="xlsx"`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `exportFormats="xlsx"` 时，每张表在 `outputDir` 下生成 `<tableName>.xlsx` 文件
- [x] 生成的 `.xlsx` 可被 `ExcelResourceIO.openInput` 读回，读回的记录数 == 插入行数，字段值匹配
- [x] `exportFormats="xlsx,csv"` 时同时生成两种文件（验证 `MultiBatchConsumerProvider` 多格式共存）
- [x] **端到端验证**：从 config 加载（`CliExportDbCommand` 同款 `ResourceComponentManager.loadComponentModel` 路径）→ `ExportDbTool.execute()` → 磁盘上生成 `.xlsx` → 读回校验，完整跑通
- [x] **接线验证**：`ExportDbTool.newConsumer` 在运行时确实进入 `xlsx` 分支并调用 `newExcelConsumer`（通过生成的 `.xlsx` 文件存在且可读来证明，而非仅类型存在）
- [x] **无静默跳过**：未知格式仍抛 `nop.err.dbtool.invalid-exp-format`，未被改成静默 `continue` 或空实现
- [x] **新功能测试**：`TestExportDbTool` 显式覆盖 `xlsx` 导出 + 读回校验（不只是"原有测试通过"）
- [x] `./mvnw test -pl nop-batch-exp -am` 通过
- [x] `docs-for-ai/` 无需更新（`nop-batch-exp` 的导出工具不在现有 owner doc 覆盖范围内；格式说明已在 xdef 注释中体现）—— `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ExportDbTool` 支持 `xlsx` 格式，与 `csv`/`csv.gz`/`sql` 同等地位
- [x] xlsx 导出为流式写出（`ExcelRecordOutput`），不对超大数据额外改造
- [x] 行为/契约结果已达成：未知格式仍 fail-fast
- [x] 端到端 + 接线 focused verification 已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] owner doc 裁定已记录（`No owner-doc update required`）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：`xlsx` 分支在运行时被调用链触达（生成的 `.xlsx` 文件可读回），无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-batch-exp -am`
- [x] `./mvnw test -pl nop-batch-exp -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### ExcelRecordOutput 多 sheet 自动分页

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `ExcelIOConfig.maxCountPerSheet` 是 `ExcelRecordOutput` 内部的字段接线问题，与 `ExportDbTool` 增加 `xlsx` 格式正交。`xlsx` 格式本身已可用（单 sheet 支撑到 Excel 行上限 1,048,576）。多 sheet 分页应作为独立改动处理。
- Successor Required: `yes`
- Successor Path: 待定（如需要再建 plan）

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: Phase 1 已完成：pom.xml 新增 nop-report-core 依赖；ExportDbTool 新增 newExcelConsumer + xlsx 分支；xdef 注释更新；新增 TestExportDbTool 端到端测试（H2 localDB），读回验证行数与字段值一致。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: opencode (mission-driver)
- Evidence: `./mvnw test -pl nop-batch/nop-batch-exp -am` 全部通过；TestExportDbTool.testExportXlsx 覆盖建表→导出 xlsx→ExcelResourceIO 读回验证行数+字段值
