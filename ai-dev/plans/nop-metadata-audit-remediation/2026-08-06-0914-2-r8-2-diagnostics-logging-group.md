# R8.2 诊断与日志组修复（AR-16, AR-23③, AR-23⑤, AR-23④）

> Plan Status: active
> Last Reviewed: 2026-08-06
> Mission: nop-metadata-audit-remediation
> Work Item: MR8（R8.2 诊断与日志组）
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-16、AR-23③⑤④）、`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR8 段 R8.2 行 + R8.0 裁决记录）
> Related: 执行顺序 `{2}` of 3（**依赖 R8.1 完成后执行**——R8.1 的 AR-13 修改 `QualityErrors.java` 声明 + AR-16 同在 `MetaQualityRuleExecutor.java` 相邻区域（:631-659 LOG 行），顺序执行避免交叉编辑）；启动门禁：R8.0 done。

## Purpose

修复诊断与日志链路的 4 个已确认缺陷（AR-16 敏感 SQL 日志、AR-23③ 索引 refresh 失败静默、AR-23⑤ 外部表结构读取错误归类 + NULL 精度归 0、AR-23④ 搜索 limit 负数直通引擎，R8.0 全部提级为 P1 修复）：日志不再落盘完整 SQL/custom_sql 字面量、索引重建失败可观测、外部表扫描错误可诊断、搜索 limit 显式失败。产出 = 代码修复 + 判别性回归测试 + arm-index/roadmap 终态。

## Current Baseline

经 2026-08-06 live repo 核对（finding 描述以审计报告为准；行号以 live 复核为准）：

- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` **970/0**（R8.0 收口口径）
- **AR-16**（`MetaQualityRuleExecutor.java:631/:647/:669`）：`LOG.info("qualityRule SQL: {}", sql)` ×2 + `LOG.info("qualityRule custom_sql: {}", sql)` ×1——custom_sql 文本内嵌敏感字面量（姓名/卡号等）随规则执行落日志；custom_sql 路径已有 `sqlHash`（:286-287 计算 + :287 details 写入）可替代；R6.2 已治理 rawJdbcUrl 同类面（先例）；`queryLong`/`queryTimestamp` 路径无既有 sqlHash 计算点（需新增）
- **AR-23③**（`NopMetaIndexBuilder.java:39-120`）：`buildFullIndex` 中 `searchEngine.refreshBlocking(topic)`（:109）失败仅 `LOG.warn`（:111）不入 `IndexResult`——索引 rebuild 报告"成功"但搜索仍读陈旧索引；同批 addDocs 失败已入 result（:96-105，failed += docs.size()），refresh 失败是唯一静默缺口；**计数语义约束**：refresh 失败时文档已 addDocs 成功，`indexed` 应如实反映已 addDocs 数，`failed += 1`（非 docs.size()）
- **AR-23⑤**（`ExternalTableStructureReader.java` + `ExternalColumnInfo.java`）：
  - `getTables` 扫描 SQLException（:81-84）统一抛 `ERR_DIALECT_NOT_SUPPORTED` + `.param(ARG_DATABASE_PRODUCT_NAME, "unknown")`——真实扫描故障（连接中断/权限）被误报为"方言不支持"；同文件 :118 `getDatabaseProductName` 失败也误报 ERR_DIALECT_NOT_SUPPORTED
  - `safeInt`（:155-158）`wasNull()` 时返回 0 → 列 `DECIMAL_DIGITS`/`COLUMN_SIZE` NULL 被归 0（精度伪造）；**关键约束**：`ExternalColumnInfo.precision/scale` 为基本类型 `int`（:13-14）——保留 NULL 需 `int → Integer`（service 模块 DTO，非 api 模块），连带影响 `NopMetaDataSourceBizModel.serializeColumns`（:547-557，`m.put("precision"/"scale", ...)` → JSON 输出 null）与存量 structure JSON 消费方（`MetaTableFieldResolver` buildSql 解析不读 precision/scale——已核实无引用；`NopMetaTable` buildSql JSON 文本消费面需评估）；**波及面**：`:104 setOrdinal(safeInt(...))` 也走 safeInt（ORDINAL_POSITION 无 NULL 语义，需 overload 或 null 合并保持 int）
- **AR-23④**（`NopMetaSearchBizModel.java:66-82`）：`searchMetadata` 的 limit 仅处理 `null → 20` / `>100 → 100`，负数（<0）**直通** `request.setLimit(limit)` 进引擎——无显式错误码，违反 AR-09 确立的"显式拒绝不做静默钳制"分页契约先例（`ERR_PAGINATION_LIMIT_INVALID` 于 JoinErrors.java:101）
- 相关测试现状：`TestNopMetaSearchProcessor`（11 测，含 ListAppender 先例）、`TestNopMetaIndexBuilder`、`TestNopMetadataSearchIntegration`、`TestMetaQualityRuleExecutorCustomSqlSandbox`（19 测）；**既有** `TestExternalTableStructureReader`（`nop-metadata-service/.../sync/`，52 行，4 个方言门禁测试）——本 plan 扩展或并入该文件，不新建同名文件

## Goals

- AR-16：**裁定方案 (a) sqlHash**（queryLong/queryTimestamp 新增 `sqlHashOf(sql)` 计算，custom_sql 复用 :286-287）——三处 LOG 只记 sqlHash（完整 SQL 降 DEBUG 级或移除），可追溯性保持；方案 (b) 纯降 DEBUG 不可选（queryLong/queryTimestamp 路径无 details sqlHash，可追溯性丢失，与 Exit Criteria 矛盾）；回归测试断言日志不含 SQL 字面量、含 sqlHash
- AR-23③：`refreshBlocking` 失败写入 `IndexResult`——`failed += 1`（非 docs.size()）+ `errors` 含 refresh 信息，`indexed` 如实反映已 addDocs 数；判别性测试（mock refresh 抛错 → result.failed>0 + errors 含 refresh 信息 + indexed=docs.size()）
- AR-23⑤：`getTables`/`getDatabaseProductName` 扫描级异常与"方言不支持"区分——新错误码（如 `ERR_EXTERNAL_TABLE_SCAN_FAILED`，service 模块 NopMetadataErrors）携带真实异常消息/productName（不再硬编码 "unknown"）；`ExternalColumnInfo.precision/scale → Integer`（service 模块 DTO 形状变更，plan-first 声明），`safeInt` 返回 Integer（NULL → null），`serializeColumns` 输出 null 的兼容性评估；判别性测试
- AR-23④：`searchMetadata` limit < 0 → 显式错误码（如 `ERR_SEARCH_LIMIT_INVALID`，沿 AR-09 先例），不做静默钳制；判别性测试
- 每个修复带判别性回归测试（red 先于修复实测或至少行为断言可捕获回归）

## Non-Goals

- 不处理 R8.1 组的 AR-11/12/13/14/15（同文件不同行区域，R8.1 承接）
- 不做搜索索引架构变更（topic 划分、批量策略等）
- 不改变 `ExternalTableStructureReader` 的表结构读取逻辑本身（只改错误归类与 NULL 精度处理）
- 不统一搜索 limit 与查询分页的默认值（搜索 20/100 语义保持，仅加负数显式拒绝）
- 不改 `ExternalColumnInfo` 其它字段（仅 precision/scale → Integer）

## Scope

### In Scope

- `MetaQualityRuleExecutor.java`（AR-16 三处 LOG + 相关测试）
- `NopMetaIndexBuilder.java`（AR-23③ refresh 失败入 IndexResult + 相关测试）
- `ExternalTableStructureReader.java` + `ExternalColumnInfo.java`（AR-23⑤ 错误归类 + NULL 精度 + service 模块 DTO 形状变更 + 相关测试）
- `NopMetaDataSourceBizModel.serializeColumns`（AR-23⑤ 连带消费者适配）
- `NopMetaSearchBizModel.java`（AR-23④ limit 负数显式拒绝 + 相关测试）
- `NopMetadataErrors.java`（新错误码声明，若需要）
- `ai-dev/audits/arm-index-nop-metadata.md` §P2 + roadmap MR8 段终态更新

### Out Of Scope

- R8.1 组（AR-11/12/13/14/15）、R8.3 组（AR-18/19）、R8.4 组（AR-20/21/22/17 + AR-23①②⑨⑩）
- AR-23②（索引重建不清陈旧文档）——R8.4 组
- 日志框架全局配置调整（只改本模块内三处 LOG 调用点）
- `MetaTableFieldResolver` buildSql 解析逻辑（已核实不读 precision/scale；若评估发现实际消费面则同步更新 Scope 并声明）

## Execution Plan

### Phase 1 - AR-16 质量规则 SQL 日志脱敏

Status: planned
Targets: `MetaQualityRuleExecutor.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [ ] AR-16 Decision：裁定方案 (a) sqlHash——`queryLong`/`queryTimestamp` 新增 `sqlHashOf(sql)` 计算（对齐 :286-287 既有实现），`judgeCustomSql` 复用既有 sqlHash；三处 `LOG.info` 只输出 sqlHash（完整 SQL 降 DEBUG 或移除）；记录裁定理由（方案 (b) 纯降 DEBUG 丢失可追溯性，不可选）
- [ ] AR-16 修复：三处 LOG 调用点改造；确认 details 仍含 sqlHash（custom_sql 路径保持 :287）
- [ ] 判别性测试：执行含敏感字面量的 custom_sql 规则 → 日志（ListAppender 捕获）不含 SQL 原文 + 含 sqlHash；沿 `TestNopMetaSearchProcessor` 既有 ListAppender 断言先例
- [ ] 回归：`TestMetaQualityRuleExecutorCustomSqlSandbox` / 质量规则既有测试全绿

Exit Criteria:

- [ ] 三处 LOG 调用点不再输出完整 SQL/custom_sql 字面量（代码审查 + 判别性测试断言双证据）
- [ ] sqlHash 可追溯性保持（details 或日志含 sqlHash；queryLong/queryTimestamp 路径新增计算实证）
- [ ] **无静默跳过**：脱敏不吞掉异常/结果，只改日志内容（Minimum Rules #24）
- [ ] 本 Phase 改日志行为但模块文档无日志格式契约 → `No owner-doc update required` 显式记录
- [ ] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 2 - AR-23⑤ 外部表结构读取错误归类 + NULL 精度

Status: planned
Targets: `ExternalTableStructureReader.java` + `ExternalColumnInfo.java` + `NopMetaDataSourceBizModel.java` + `NopMetadataErrors.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [ ] AR-23⑤ Decision：`ExternalColumnInfo.precision/scale int → Integer`（service 模块 DTO 形状变更，plan-first 声明——非 api 模块公共面）；评估 `serializeColumns`（:553-554）输出 null 的兼容性（structure JSON 消费方 = `MetaTableFieldResolver` buildSql 解析——已核实不读 precision/scale；`NopMetaTable.buildSql` JSON 文本消费面核对无类型化读取），记录评估结论
- [ ] AR-23⑤ 错误归类：`getTables` 扫描 SQLException（:81-84）改抛新错误码（如 `ERR_EXTERNAL_TABLE_SCAN_FAILED`，NopMetadataErrors）+ 真实异常消息/productName（不再 "unknown"）；`:118 getDatabaseProductName` 失败同理；方言门禁路径（:134-140，实际抛 `ERR_DATASOURCE_TYPE_NOT_SUPPORTED`）保持不动（不误伤）
- [ ] AR-23⑤ NULL 精度：`safeInt` 改返回 Integer（wasNull → null）或新增 Integer overload——`:100/:101 setPrecision/setScale` 传 null（Integer 字段），`:104 setOrdinal` 保持 int（null 合并或保底 0，ORDINAL_POSITION 无 NULL 语义）；NULL 精度不再归 0
- [ ] 判别性测试：在既有 `TestExternalTableStructureReader`（4 测）扩展——mock getTables 抛 SQLException → 错误码为扫描错误非方言错误 + 异常消息保留；mock DECIMAL_DIGITS NULL → scale 为 null 非 0；方言不支持 → 仍方言错误码（不误伤）；`serializeColumns` null 输出 JSON 合法性断言；修复前 red 实测
- [ ] 回归：`TestNopMetaDataSourceBizModel` / syncExternalTables 既有测试全绿

Exit Criteria:

- [ ] 扫描故障与方言不支持区分实测（错误码 + 参数判别）；NULL 精度不再归 0（Integer null 实证）
- [ ] service 模块 DTO 形状变更已声明 plan-first + 消费者影响评估记录（serializeColumns JSON 输出 null 兼容实证）
- [ ] **无静默跳过**：NULL 精度保留 null 是显式语义（不伪造值），非静默吞异常（Minimum Rules #24）
- [ ] 新错误码同步至 arm-index / 错误码清单（若模块文档含错误码清单）
- [ ] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 3 - AR-23③ 索引 refresh 失败入 IndexResult + AR-23④ 搜索 limit 负数显式拒绝

Status: planned
Targets: `NopMetaIndexBuilder.java` + `NopMetaSearchBizModel.java` + 相关测试

- Item Types: `Fix | Proof`

- [ ] AR-23③：`refreshBlocking` 失败（:109-111）→ `result.setFailed(result.getFailed() + 1)` + `result.setErrors(...)`（含 refresh 信息），`result.setIndexed(docs.size())` 保持（文档已 addDocs 成功）；与 addDocs 失败路径（:96-105）的计数语义区分
- [ ] AR-23④：`searchMetadata` limit < 0 → 显式错误码（如 `ERR_SEARCH_LIMIT_INVALID`，NopMetadataErrors，沿 AR-09 `ERR_PAGINATION_LIMIT_INVALID` 先例），在设置 request 之前拒绝；null 默认 20 / >100 封顶 100 语义保持
- [ ] 判别性测试：AR-23③ —— mock searchEngine.refreshBlocking 抛错 → IndexResult.failed>0 + errors 含 refresh 信息 + indexed==docs.size()（修复前静默）；AR-23④ —— limit=-1 → 显式错误码异常（不直通引擎），limit=null/50/200 语义回归；修复前 red 实测
- [ ] 回归：`TestNopMetaIndexBuilder` / `TestNopMetaSearchProcessor` / `TestNopMetadataSearchIntegration` 全绿

Exit Criteria:

- [ ] refresh 失败可观测（IndexResult 断言实证：failed>0 + indexed 如实）；搜索 limit 负数显式拒绝（错误码断言实证）
- [ ] **接线验证**：refresh 失败路径从 buildFullIndex 到 IndexResult 的运行时连通性已实测（mock 抛错 → 断言）（Minimum Rules #23）
- [ ] **无静默跳过**：refresh 失败不再仅 LOG.warn；limit 负数不再直通引擎（Minimum Rules #24）
- [ ] 新错误码同步至 arm-index / 错误码清单
- [ ] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 4 - 收口

Status: planned
Targets: roadmap MR8 段 + arm-index §P2 + 全量验证

- Item Types: `Fix | Proof`

- [ ] roadmap MR8 段 R8.2 行 → done（注明 4 项 finding 终态 + 测试计数基线变化）
- [ ] arm-index §P2 AR-16 / AR-23③⑤④ → fixed（含修复 commit 引用）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（记录计数基线）
- [ ] 独立子 agent closure audit（fresh session）PASS + Closure 段证据写入

Exit Criteria:

- [ ] roadmap MR8 段与 arm-index §P2 双向一致（AR-16 + AR-23③⑤④ 逐条可追溯）
- [ ] 全量测试通过（0 failures/errors/skipped）+ 工具验证 exit 0
- [ ] 独立 closure audit READY_TO_CLOSE（含 Anti-Hollow 调用链追踪）
- [ ] `ai-dev/logs/2026/08-06.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [ ] AR-16 + AR-23③⑤④ 四个已确认 live defect 全部修复（判别性测试 red→green 证据在案）
- [ ] 无已确认 live defect / contract drift 被降级到 deferred / follow-up
- [ ] 错误码新增/变更与 arm-index / 模块文档一致（若适用）；service 模块 DTO 形状变更（int→Integer）消费者兼容实证在案
- [ ] 必要 focused verification 已完成（每项 finding 至少一条判别性测试）
- [ ] 独立子 agent / 独立审阅者 closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）refresh 失败入 IndexResult / limit 拒绝 / 日志脱敏在运行时真实连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw test -pl nop-metadata -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] checkstyle / 代码规范检查通过（历史惯例：插件仅 -Pqa profile，按仓库惯例）

## Deferred But Adjudicated

（无 —— 本 plan 4 项 finding 全部 in-scope 修复，无归类项）

## Non-Blocking Follow-ups

- AR-23②（索引重建不清陈旧文档）由 R8.4 组承接（roadmap R8.4 行）
- 日志脱敏裁定记录（sqlHash vs DEBUG 方案选择理由）写入本 plan Phase 1 决策记录
- **登记同类敏感面**（watch-only，非 AR-16 审计范围）：`judgeCustomSql` :291 `j.getDetails().put("sql", sql)` 仍把完整 custom_sql（含敏感字面量）持久化进 `NopMetaQualityResult.details`——与 AR-16 日志面同族但落盘面不同（DB 持久化），建议随未来质量结果 schema 治理批次评估 sqlHash 化

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

（关闭时由独立子 agent 填写）

Follow-up:

（关闭时填写）
