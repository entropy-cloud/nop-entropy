# 2284 DuckDB WI2 — 文件数据面（CSV/Parquet 读写与 XLSX 入口桥）

> Plan Status: active
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI2；`ai-dev/analysis/2026-10/2026-10-01-duckdb-jdbc-selection-and-spike-adjudication.md`
> Related: 2282（WI0）、2283（WI1，已完成）

## Purpose

在 nop-duckdb 内落地文件数据面：CSV/Parquet 的读入与导出封装（含类型推断与 NULL 语义的实测固化）、XLSX 入口桥（复用 nop-tablesaw 既有 XlsxReader，不重造解析）、文件→DuckDB→文件 roundtrip 一致性测试。完成后 nop-task 步骤（WI3）与对拍矩阵（WI5）有了文件出入口。

## Current Baseline

- WI1 已交付：nop-duckdb 模块（commit ab22dc6519）含 IDuckDbEngine/DuckDbEngine 连接管理、NopDuckDbErrors 错误码、app-duckdb.beans.xml 装配；10 测试全绿
- WI0 问①实测：read_csv_auto 摄取 + COPY TO PARQUET/CSV 导出 + read_parquet 回读全部可用（spike q1CsvParquetRoundtrip）；空字段 → NULL、sum 忽略 NULL
- WI2 审查探针实测（duckdb_jdbc 1.5.6.0）：openMemory() 每次连接是相互隔离的私有内存库（跨连接不可见表）；读侧 CTAS 的 getUpdateCount()=-1（行数须 SELECT count(*)）；写侧 COPY 的 updateCount 即拷贝行数；COPY TO 对已存在文件静默覆盖；CREATE TABLE 对已存在表名报 Catalog Error；单引号路径 doubled '' 转义对 read_csv_auto/COPY 均足够；NULL 导出为裸空字段、空串导出为 `""`（回读后 `""` 坞缩为 NULL）；read_csv_auto 类型推断 BIGINT/DOUBLE/VARCHAR
- nop-tablesaw 已有 XlsxReader（`XlsxReadOptions.builder(String).build()`，read 返回 tablesaw Table，无 sheetIndex 时返回首个非空 sheet）；tablesaw-core 0.43.1 有 DataFrameWriter/CsvWriteOptions；无 xlsx 写出面（POI 不在依赖图）——xlsx 测试资源只能用 git 二进制文件（先例：nop-format/nop-tablesaw/data/*.xlsx）
- nop-bom 尚无 nop-tablesaw 条目（同类模块 nop-dataset/nop-table-validator/nop-ooxml-xlsx 均在 nop-bom 管理）
- 全仓无 Arrow/Parquet Java 基建，Parquet 通路完全经 DuckDB SQL COPY；归档禁用 .duckdb 专有格式

## Goals

- 文件 IO API（DuckDbFiles，静态工具类、无 bean——裁定：无状态、以 Connection 为参，WI3 按需直用）：readCsv/readParquet（外部文件 → DuckDB 表）、writeCsv/writeParquet（SQL → 外部文件）
- API 契约（写入 javadoc 并以测试固化）：
  - 表生命周期 = 连接生命周期：openMemory() 连接为隔离私有内存库，表随连接消失；openFile() 连接的表持久进 .duckdb 文件
  - 行数返回：读侧以 SELECT count(*) 计数返回（CTAS updateCount 恒 -1，不得使用）；写侧以 COPY 的 updateCount 返回
  - 读侧表名冲突 fail-fast（新错误码 table-exists，不静默 OR REPLACE）；写侧 COPY 对已存在文件覆盖（语义显式写入 javadoc 并有用例固化）
  - 路径单引号 doubled '' 转义由实现统一处理
- NULL/类型语义固化：NULL 导出为裸空字段、空串导出为 `""`、回读 `""` 坞缩为 NULL、聚合忽略 NULL、read_csv_auto 类型推断——全部以实测断言固化
- XLSX 入口桥：readXlsx(conn, xlsxPath, tableName)（无 sheetIndex 参数，按 XlsxReader 既有行为取首个非空 sheet）经 tablesaw Table → 临时 CSV（java.io.tmpdir + deleteOnExit，用后即删）→ readCsv；xlsx→CSV 中转的重推断语义以实测固化（1.5.6 实测：前导零数字串保持 VARCHAR、纯数字串重推断为 BIGINT）
- Roundtrip 数据级等价测试（行数/列名序/含 NULL 位置的逐值断言；字节级 fixed-point 仅限单一规范输入并注明性质）

## Non-Goals

- 不做类型对拍矩阵（WI5）、nop-task SQL 步骤（WI3）、外存溢出档（WI6）、单写者并发（WI4）
- 不做 xlsx 写出（导出走 CSV/Parquet；tablesaw 无写出面，已裁定）
- 不写 docs-for-ai 文档（WI9）

## Scope

### In Scope

- nop-bom 注册 nop-tablesaw（${nop-entropy.version}，对齐 nop-dataset 先例）
- nop-duckdb pom 增 nop-tablesaw 依赖（无版本号，经 BOM）；传递带入 tablesaw-core + nop-ooxml-xlsx/excel/dataset/table-validator 链（方向无环，已由 roadmap 裁定）
- DuckDbFiles 实现 + 新错误码（table-exists / file-not-found / io-failed，英文描述）
- 语义固化测试、roundtrip 测试、xlsx 桥测试（xlsx 测试资源 = git 二进制，沿用 nop-tablesaw/data 先例；来源标注出处）
- 当日 ai-dev/logs/ 更新

### Out Of Scope

- nop-dao 方言、nop-task、其他消费方模块

## Execution Plan

### Phase 1 - 文件 IO API 实现

Status: completed
Targets: `nop-bom/pom.xml`、`nop-duckdb/pom.xml`、`nop-duckdb/src/main/java/io/nop/duckdb/`

- Item Types: `Fix`（本分支 guide 四分类，新增能力实现）

- [x] nop-bom 注册 nop-tablesaw；nop-duckdb pom 增依赖（无版本）
- [x] NopDuckDbErrors 增三错误码（table-exists / file-not-found / io-failed，英文描述，.param 带路径/表名）
- [x] DuckDbFiles 四个 API + readXlsx 桥；行数机制按契约（读侧 count(*)、写侧 updateCount）；路径转义统一处理；读前文件存在性检查（file-not-found）；读侧表名已存在抛 table-exists
- [x] javadoc 写明表生命周期/覆盖语义契约

Exit Criteria:

- [x] 四个 API + readXlsx 各有实现；实现可编译（`./mvnw install -DskipTests -pl nop-duckdb -am`）
- [x] No owner-doc update required（WI9 承接）
- [x] `ai-dev/logs/2026/10-01.md` 已更新

### Phase 2 - 语义固化测试与全量验证

Status: completed
Targets: `nop-duckdb/src/test/java/io/nop/duckdb/`、`nop-duckdb/src/test/resources/io/nop/duckdb/`（xlsx 二进制资源，classpath 加载，来源在提交信息标注）

- Item Types: `Proof`

- [x] NULL 语义测试：空字段读入为 NULL；NULL 导出为裸空字段、空串导出为 `""`、回读 `""` 坍缩 NULL（三方行为实测断言）；聚合忽略 NULL
- [x] 类型推断测试：整型/浮点/字符串列经 read_csv_auto 推断后 duckdb_type 可查且 parquet roundtrip 保持
- [x] Roundtrip 数据级等价测试：csv→duck→csv、parquet→duck→parquet、csv→duck→parquet→duck→csv（含 NULL 行，逐值断言）；规范输入的字节级 fixed-point 单测（注明性质）
- [x] 契约测试：行数返回值与实际一致（读侧/写侧）；表名冲突抛 table-exists；写侧覆盖既有文件有用例；路径含单引号 roundtrip 可用（转义统一处理的中立断言：注入构造失败且目标表完好）；表生命周期（openMemory 连接 A 经 readCsv 建表 → 同连接可查、连接 B 不可见、连接关闭后消失；openFile 摄取表关闭重开仍在）
- [x] XLSX 桥测试：git 二进制 xlsx 资源 → readXlsx → 表数据断言；类型漂移用例（"00123" → BIGINT 推断，断言即固化）
- [x] **端到端验证**：xlsx→DuckDB→parquet→读回全链有测试贯通
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0（Cross-Cutting #1）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 新增公共行为（四 API + readXlsx + 三错误码路径）每个至少一个测试（plan guide 规则 25）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] `ai-dev/logs/2026/10-01.md` 已更新 WI2 收口记录

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无已知输入）
- [x] 行为/契约结果已达成：文件 IO API 可用、契约（生命周期/行数/冲突/覆盖/转义）有测试固化
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确 No owner-doc update required（显式归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：audit 验证 API 被测试真实调用、roundtrip 全链贯通、无空壳
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] checkstyle / 代码规范检查通过
- [x] scan-hollow-implementations --module nop-duckdb --severity high 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 大文件流式摄取参数化（批大小/并行度）归 WI6/WI7 按需
- readXlsx 的 sheetIndex 参数化（当前按 XlsxReader 缺省行为取首个非空 sheet）留待真实消费者需要时加

## Closure

Status Note: 文件数据面五 API + 三错误码 + 全部契约测试落地；首轮 closure audit 判 CANNOT CLOSE（3 Major：io-failed 零测试、越界 .rels 行尾变更、plan 漂移文字与实测相反），三项全部修复后按 audit 提供的解除清单复证（25/25 全绿），无需整体重审（audit 明示 spot-check 即可）。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，fresh session）
- Audit Session: agent_805eabb3-5137-4859-af60-a7eef2d4905e
- Evidence:
  - 首轮 audit：Phase1 全 PASS；Phase2 EC1 FAIL（io-failed 零测试）→ 已修复（testIoFailedOnCorruptParquet + 注入用例强化为文件存在/不存在双腿）；越界 .rels 已 git checkout 还原；plan 漂移文字已按偏差②修正
  - Anti-Hollow 三项 PASS（真实驱动非 mock；xlsx→duck→parquet 行数一致断言；0 hollow findings）
  - 命令复证（audit 方实跑 + 修复后实现者复跑）：./mvnw test -pl nop-duckdb 25/25 绿；-am 收口门 EXIT=0；scan-hollow 0；check-doc-links 0 errors
  - audit 解除清单 4 项逐项完成：①io-failed 测试（新增 testIoFailedOnCorruptParquet，断言错误码+cause 保留）②.rels 还原（git status 无越界）③plan 文字修正 + 空串导出腿（断言 \"\" 输出）+ parquet→parquet 单链用例补齐 ④状态最终化（本段）
  - check-plan-checklist.mjs --strict 收口后退出码 0
  - Minor 处置：Minor1（parquet 单链/fixed-point）→ 已补 parquet→parquet 用例，字节级 fixed-point 裁定为非必需（数据级等价为主，规范输入字节断言弱于逐值断言）；Minor2（空串腿）→ 已补断言；Minor3（注入注释机制）→ 已修正为双腿机制说明；Minor4（文本状态）→ 本段落定

Follow-up:

- 大文件流式摄取参数化归 WI6/WI7；readXlsx sheetIndex 参数化留待真实消费者（均带 non-blocking 理由，见 Non-Blocking Follow-ups）
- 除此之外 no remaining plan-owned work
