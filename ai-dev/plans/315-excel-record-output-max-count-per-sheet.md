# 315 接线 ExcelRecordOutput.maxCountPerSheet 实现自动分 Sheet

> Plan Status: active
> Last Reviewed: 2026-07-23
> Source: `ExcelIOConfig.maxCountPerSheet` 死代码；`ExcelRecordOutput.write/newDataSheetWriter`
> Related: `314-nop-batch-exp-xlsx-export.md`（其 Deferred 段将本项列为 successor）

## Purpose

让 `ExcelRecordOutput` 真正读取 `ExcelIOConfig.maxCountPerSheet`：当单个 sheet 写入行数达到阈值时，自动关闭当前 sheet、新建下一个 sheet 继续写入，从而把超大数据自动分页到多个 sheet，避免触及 Excel 单 sheet 行上限（1,048,576）。

## Current Baseline

- `ExcelIOConfig.maxCountPerSheet` 默认 `20_0000`（`ExcelIOConfig.java:15`），但全仓库仅在其 getter/setter 中出现（grep 确认），`ExcelRecordOutput` 从不读取它 —— 是死代码。
- `ExcelRecordOutput.write`（`ExcelRecordOutput.java:115-122`）始终写入同一个 sheet：
  ```
  writeCount++; writeHeaders(); out.writeRow((int) writeCount, row);
  ```
  - `writeCount` 是全局计数器（`long`），兼作行索引，不随 sheet 重置。
  - 写入超过 Excel 行上限会失败。
- sheet 轮转的基础设施已存在但未被 `write` 调用：
  - `newDataSheetWriter()`（`:177-196`）：建新 sheet writer，但 sheet 名取自固定的 `config.getDataSheetName()`（默认 `"Data"`），多次调用会生成**重名 sheet**。
  - `closeDataSheetWriter()`（`:198-206`）：关闭当前 sheet writer。
  - 这两个方法目前仅在 `beginWrite`/`close` 中各调用一次。
- `ExcelOfficePackage.addSheet(index, sheetName, false)`（`ExcelOfficePackage.java:114`）调用 `workbook.addSheet`（`_ExcelWorkbook.java:164`），`KeyedList` 按 name 去重 —— 重名 sheet 会覆盖或冲突，生成的 xlsx 在 Excel 中打开会报修复错误。OOXML 规范要求 sheet 名唯一且 ≤ 31 字符。
- `writeHeaders()`（`:124-137`）由 `headersWritten` 标志守护，仅写一次。sheet 轮转后新 sheet 需要重新写表头。
- 读取侧 `ExcelRecordInput.beforeRead`（`ExcelRecordInput.java:86-98`）只 `parseSheet(config.getDataSheetName())` 单个 sheet，不遍历所有 sheet —— 读取多 sheet 导出文件需逐 sheet 调用 `parseSheet`。本计划**不改动读取侧**。
- 基础模板 `simple-data.xpt.xlsx`（`/nop/ooxml/templates/simple-data.xpt.xlsx`）包含一个 `"Data"` sheet 模型，`newDataSheetWriter` 通过 `xptModel.requireSheet(sheetName)` 取它的布局配置，所有数据 sheet 共享同一布局 —— 这是期望行为。
- 现有测试 `TestExcelResourceIO.testWrite`（`TestExcelResourceIO.java:111`）写入 10 行、未设 `maxCountPerSheet`，可作为不触发分页的回归基线。

## Goals

- `ExcelRecordOutput.write` 在当前 sheet 数据行数达到 `maxCountPerSheet` 时，自动轮转到新 sheet。
- 每个数据 sheet 拥有唯一名称（如 `Data`、`Data-2`、`Data-3`，或 `Data-1`、`Data-2`…）。
- 每个数据 sheet 独立包含表头行 + 数据行，行索引按 sheet 内部从 0（表头）开始正确编号。
- `getWriteCount()` 仍返回全局写入总数（行为不变）。
- 默认 `maxCountPerSheet = 20_0000` 不变。
- 生成的 xlsx 结构合法（sheet 名唯一），Excel 可直接打开无需修复。

## Non-Goals

- 不改动 `ExcelRecordInput`（读取侧）使其自动遍历多 sheet —— 读取多 sheet 文件需调用方逐 sheet `parseSheet`，这是独立需求。
- 不为 `BizExportTaskBuilder` 或 `ExportDbTool` 暴露 `maxCountPerSheet` 的配置入口（它们已通过 `ExcelIOConfig` 间接持有，本计划只接线 `ExcelRecordOutput` 内部逻辑；是否在更上层暴露配置属后续工作）。
- 不改动 `csv` / `sql` 路径。
- 不引入 Excel 模板报表（`.xpt.xlsx`）的 `<beginLoop>` sheet 循环机制 —— 那是报表引擎层的能力，与记录级流式写出正交。

## Scope

### In Scope

- `nop-report-core`：`ExcelRecordOutput` —— 接线 `maxCountPerSheet`、sheet 轮转、唯一命名、行索引重置、表头重写。
- `nop-report-core` 测试：新增多 sheet 自动分页回归测试。

### Out Of Scope

- `ExcelRecordInput` 多 sheet 读取（见 Non-Goals）。
- 上层（`BizExportTaskBuilder` / `ExportDbTool`）暴露 `maxCountPerSheet` 配置参数。
- `simple-data.xpt.xlsx` 模板结构变更。

## Execution Plan

### Phase 1 - 接线 maxCountPerSheet 并端到端验证

Status: planned
Targets: `nop-report-core/.../record/ExcelRecordOutput.java`, `nop-report-core/src/test/...`

- Item Types: `Fix | Proof`

- [ ] 在 `ExcelRecordOutput` 中引入**当前 sheet 内行计数器**（与全局 `writeCount` 分离），用于判断是否达到 `maxCountPerSheet`
- [ ] 在 `write()` 中：写入前判断当前 sheet 行数是否已达阈值；达到则调用 `closeDataSheetWriter()` + `newDataSheetWriter()`，并重置 per-sheet 行计数器与 `headersWritten` 标志
- [ ] 修改 `newDataSheetWriter()` 使 sheet 名唯一化：首个用 `config.getDataSheetName()`（默认 `Data`），后续追加序号（如 `Data-2`、`Data-3`），且总长 ≤ 31 字符（OOXML 限制）
- [ ] 确保 `out.writeRow` 的行索引使用 per-sheet 计数器（表头在 row 0、数据从 row 1 起），而非全局 `writeCount`
- [ ] 确保 `getWriteCount()` 返回全局总数不变
- [ ] 新增测试 `testWriteMultiSheet`（参照 `TestExcelResourceIO.testWrite`）：设 `maxCountPerSheet` 为小值（如 3），写入 7 行，验证生成 xlsx 含 3 个数据 sheet（3+3+1），每个 sheet 均有表头，总数据行 == 7
- [ ] 确认 `TestExcelResourceIO.testWrite`（未设 `maxCountPerSheet`，10 行 < 默认 20w）仍生成单 sheet —— 回归不破坏

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 设 `maxCountPerSheet=K` 写入 `M` 行（`M > K`）时，生成 xlsx 含 `ceil(M/K)` 个数据 sheet
- [ ] 每个 sheet 的数据行数 ≤ K，所有 sheet 数据行数之和 == M
- [ ] 每个 sheet 均含表头行（row 0），数据行从 row 1 起，行索引正确无错位
- [ ] 多个 sheet 的名称互不相同且均 ≤ 31 字符；Excel 打开无修复提示
- [ ] `maxCountPerSheet` 默认值（20w）未改变；未显式设置时行为与改动前一致（单 sheet）
- [ ] `getWriteCount()` 返回全局写入总数，未被 per-sheet 逻辑污染
- [ ] **端到端验证**：`ExcelResourceIO.openOutput` → `beginWrite` → 多次 `write`（超过阈值）→ `endWrite` → `close` → 产出的 `.xlsx` 含多个数据 sheet，逐 sheet 读回校验行数与字段值
- [ ] **接线验证**：`write()` 在运行时确实读取 `config.getMaxCountPerSheet()` 并触发 `closeDataSheetWriter`/`newDataSheetWriter`（通过多 sheet 文件实际生成来证明，非仅字段被引用）
- [ ] **无静默跳过**：`maxCountPerSheet <= 0` 时有明确处理（视为不分页/忽略阈值，而非空实现或静默 continue）
- [ ] **新功能测试**：`testWriteMultiSheet` 显式覆盖分页阈值、sheet 数量、per-sheet 表头、总行数
- [ ] `./mvnw test -pl nop-report-core -am` 通过
- [ ] `docs-for-ai/` 无需更新（`ExcelRecordOutput` 内部行为，非公开 API 契约变化）—— `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `maxCountPerSheet` 不再是死代码，`write` 运行时实际读取并据此分页
- [ ] 多 sheet 输出结构合法（唯一名、≤31 字符、Excel 可直接打开）
- [ ] per-sheet 行索引与表头正确，全局计数不受影响
- [ ] 端到端 + 接线 focused verification 已完成
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [ ] owner doc 裁定已记录（`No owner-doc update required`）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：`write()` → 阈值判断 → `closeDataSheetWriter`/`newDataSheetWriter` 调用链在运行时确实连通（多 sheet 文件实际生成），无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl nop-report-core -am`
- [ ] `./mvnw test -pl nop-report-core -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### ExcelRecordInput 多 sheet 自动遍历读取

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `maxCountPerSheet` 是写出侧配置；本计划只接线 `ExcelRecordOutput`。读取侧 `ExcelRecordInput` 已支持按名 `parseSheet` 单 sheet，调用方可逐 sheet 读取，不阻碍写出功能成立。读取多 sheet 是独立需求。
- Successor Required: `no`（如需要可另起，但不阻塞本计划）

### 上层暴露 maxCountPerSheet 配置入口

- Classification: `optimization candidate`
- Why Not Blocking Closure: `BizExportTaskBuilder` / `ExportDbTool` 已通过 `ExcelIOConfig` 间接持有该字段；`ExcelRecordOutput` 内部接线后，上层只要设置 `ExcelIOConfig.maxCountPerSheet` 即生效。是否在 config 模型/xdef 属性层显式暴露属易用性优化，不阻碍核心功能。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 待 Phase 1 完成并经独立 closure audit 后填写
Completed: 待定

Closure Audit Evidence:

- Reviewer / Agent: 待定
- Evidence: 待 closure audit 填写
