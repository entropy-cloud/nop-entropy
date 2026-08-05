# 血缘提取公开 API 显式抛错修复（SQL 解析失败不再降级为成功响应）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查 consensus——R1 `ses_02e78316dffeJRp86tM63DDYbi`（0 Blocker，2 Major：arm-index 收口面与 plan {3} 交叠/行不存在、Exit Criteria 预勾选 [x]；6 Minor——全部修复）；R2 `ses_02e6ef436ffeJz4a7yN4pD0laI`（0 Blocker，1 Major：反向执行序镜像守卫——已修复并联动 plan {3}；3 Minor 文本修正）。全部 Blocker/Major 清零，裁定可执行。
> Source: `ai-dev/audits/2026-08-05-0655-multi-audit-nop-metadata-audit-remediation.md`（[P1-03]）、`ai-dev/audits/2026-08-05-0655-open-audit-nop-metadata-audit-remediation.md`（[AR-03]）
> Related: 执行顺序 `{2}` of 3 — 与 `{1}`（SSRF）无代码文件域冲突，可并行；与 `{3}`（文档）在 arm-index/roadmap 收口面交叠：**11 个 P1 的首轮登记由 plan {3} Phase 3 完成（planned），本 plan Phase 4 仅更新自身行（`2026-08-05-0655#P1-03`/`#AR-03`）终态（planned→fixed）；若 {3} 未先行，则登记+终态一次完成**。收口段显式执行序：{3} Phase 3 登记先行；若本 plan 先行收口已写 fixed 终态，则 {3} 登记时对已 fixed 行不得回退（plan {3} Phase 3 含镜像守卫）；两 plan 收口段串行化（不并发编辑同一文件）
> Mission: nop-metadata-audit-remediation

## Purpose

修复血缘提取公开 API（`extractLineageFromSql` / `extractColumnLineageFromSql`）将"SQL 解析失败"降级为 HTTP 200 + 零边成功响应的问题：在 BizModel 边界对非空 `errors` 显式抛 `NopMetadataException`（错误码 + metaTableId 参数），并统一表级/列级两级空 SQL 行为，使公开行为符合 `docs-for-ai/03-modules/nop-metadata.md:161` 文档化的"无静默跳过"契约。

## Current Baseline

2026-08-05 live repo 核对：

- **`NopMetaLineageEdgeQueryAction.java`**（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java`）：
  - 表级 `extractLineageFromSql`（:160-166）：`try { refs = sqlExtractor.extract(sourceSql); } catch (NopException e) { LOG.error(...); errors.add(errorMap("sql_parse", e)); refs = Collections.emptyList(); }` —— **解析失败被吞为 errors 列表 + 空血缘**
  - 列级 `extractColumnLineageFromSql`（:218-222）：同类 catch → errors + emptyList
  - 表级空 sourceSql（:160-166 路径）被降级；**列级空 sourceSql（:211-213）显式抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`** —— 两级行为不一致
- **`NopMetaLineageEdgeBizModel.java`**（:106-131）：`extractLineageFromSql` / `extractColumnLineageFromSql` 均为 `@BizMutation` 公开方法；`dto.setErrors(r.errors); return dto;` —— **无 `r.errors` 非空检查**，errors 原样透传进 `data` 返回成功响应
- **调用方核实**：BizModel 是 QueryAction 两方法的唯一调用方（全仓 grep：无 xbiz/xpl/其他 BizModel/json5 快照调用这两个 mutation）——抛错变更零外部影响面
- **契约冲突点**：`docs-for-ai/03-modules/nop-metadata.md:161` 明确"SQL 解析失败 → 显式抛 `NopException` + ErrorCode，不静默空集"；单表操作不在文档豁免的批量操作名单（syncExternalTables / collectCatalog / executeCheckpoint）内
- **测试现状**：抽取器层单测钉死"必须抛异常"（`TestSqlColumnLineageExtractor` assertThrows），但异常被 QueryAction 吞掉未达 API 边界；audit + 复核 agent 确认**无测试钉死"errors 非空 → 成功"**（`TestNopMetaLineageEdgeBizModel.java` 全部 extract 测试用合法 SQL + assertFalse(hasError) 或 not-found 类 + assertTrue(hasError)，无冲突）
- **错误码在位**：`LineageErrors.java` 中 `ERR_LINEAGE_SQL_PARSE_FAILED`/`ERR_COL_LINEAGE_SQL_PARSE_FAILED`/`ERR_LINEAGE_SQL_SOURCE_EMPTY` 三码定义 + 占位符存在（经 `NopMetadataErrors extends ... LineageErrors` 聚合公开）；**注意**：`ERR_LINEAGE_SQL_SOURCE_EMPTY` 消息文案为列级措辞（"cannot extract column lineage"），表级复用需同步改文案（Phase 1 裁定）
- 绿色基线：`./mvnw test -pl nop-metadata -T 1C` 867 tests / 0 failures（service 口径；含 web 共 868，multi-audit 口径）

## Goals

- BizModel 边界对非空 `errors` 显式抛错（表级 `ERR_LINEAGE_SQL_PARSE_FAILED` / 列级 `ERR_COL_LINEAGE_SQL_PARSE_FAILED`，param 带 metaTableId），不再以成功响应 + 零边返回
- 表级空 sourceSql 与列级一致显式抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`（消除两级行为不一致）
- API 级回归测试：非法 SQL → GraphQL 响应 `hasError()` + 精确错误码；空 SQL 两级一致；正常 SQL 抽取行为不回归
- 与文档契约对齐后确认 `nop-metadata.md:161` 无需变更（代码向文档收敛）

## Non-Goals

- 不改变 SQL 抽取器（`SqlColumnLineageExtractor` / `SqlSourceTableExtractor`）内部行为——其抛异常契约保持，仅修复上层吞异常
- 不改批量操作豁免名单（syncExternalTables / collectCatalog / executeCheckpoint 的 per-row errors 收集语义不动）
- 不处理 `LineageExtractResultDTO.sourceTables` 字段语义问题（AR-06，P2 归 backlog）
- 不改变 `extractMeasureLineage` / `recordLineage`（无 SQL 解析面）的现有行为（除非执行期核对发现同类降级——如发现，登记观察项而非扩 scope）

## Scope

### In Scope

- BizModel 层 `r.errors` 非空即抛（两个公开 mutation 入口）
- 表级空 sourceSql 显式抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`（与列级一致）
- API 级回归测试（非法 SQL + 空 SQL + 正常路径）
- 错误码复用既有定义（`ERR_LINEAGE_SQL_PARSE_FAILED` / `ERR_COL_LINEAGE_SQL_PARSE_FAILED` / `ERR_LINEAGE_SQL_SOURCE_EMPTY`，核实定义与占位符存在）
- arm-index / roadmap 对应行终态更新

### Out Of Scope

- SQL 抽取器实现细节（非本次目标）
- 其他 lineage 方法行为变更
- 其他模块

## Execution Plan

### Phase 1 - 设计裁定 + 错误码核对

Status: completed
Targets: `NopMetaLineageEdgeQueryAction.java` + `NopMetaLineageEdgeBizModel.java` + `LineageErrors.java`（证据读取）

- Item Types: `Decision | Proof`

- [x] **live 复核（Proof）**：读取两个入口的完整调用链（QueryAction :150-280 区域 + BizModel :106-131 + `LineageExtractResult` 结构 + errors 生成点），确认 errors 唯一来源为 SQL 解析失败 catch 分支（无其他合法 in-band errors 场景被误伤——复核 agent 已确认仅 :160-166/:218-222 两处 catch 写入，执行时复核）
- [x] **抛错边界裁定（Decision）**：在 BizModel 层抛（API 边界最近点，保证 GraphQL 返回错误而非 data.errors 透传）；错误码映射（表级 → `ERR_LINEAGE_SQL_PARSE_FAILED`、列级 → `ERR_COL_LINEAGE_SQL_PARSE_FAILED`）；param 携带 metaTableId；**errors 细节携带默认值**：`.param("error", errorMap 的 message)`（errorMap 内 message 为 `e.getMessage()`，原始异常链已在 QueryAction 内丢失——携带 message 保留排障信息，不允许只抛裸错误码）
- [x] **空 SQL 行为裁定（Decision）**：表级空 sourceSql 改显式抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`（与列级 :211-213 一致）；**该码消息文案为列级措辞（"cannot extract column lineage"），表级复用须同步把 `LineageErrors.java` 消息改为通用措辞**（无 i18n 资源、无测试断言消息文本，改文案零成本；执行时核实）
- [x] **错误码核对（Proof）**：`LineageErrors.java` 中三个错误码定义 + 占位符 + 描述存在（脚本核对），无缺失
- [x] 裁定记录 repo-observable（本 plan + arm-index 行）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 抛错边界/错误码映射/空 SQL 行为三项裁定完成，结论基于 live 复核
- [x] 确认无测试钉死"errors 非空 → 成功"（grep 测试断言复核）
- [x] `No owner-doc update required`（Phase 1 纯裁定，无代码变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 代码落地（BizModel 抛错 + 空 SQL 统一）

Status: completed
Targets: `NopMetaLineageEdgeBizModel.java` + `NopMetaLineageEdgeQueryAction.java` + `LineageErrors.java`（如需调整）

- Item Types: `Fix`

- [x] **BizModel 抛错（Fix）**：`extractLineageFromSql` / `extractColumnLineageFromSql` 在 `r.errors` 非空时抛 `NopMetadataException`（对应错误码 + metaTableId param + errors message 细节，按 Phase 1 裁定）——**不允许静默透传**（Minimum Rules #24）。修复后两 mutation 的 `DTO.errors` 在成功时恒空（非空即抛），字段成为死面；`extractMeasureLineage` 仍保留 in-band errors 语义——契约面收缩属有意（文档"无静默跳过"契约优先），记录于本 plan + arm-index
- [x] **空 SQL 统一（Fix）**：表级空 sourceSql 显式抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`——**守卫落点：在 QueryAction 表级 extract 调用（:160 try）之前镜像列级 :211-213 守卫**（空 SQL 检查先于 try/catch，否则空 SQL 会被 extractor 抛成 parse-failed 错误码，两级一致断言失败）
- [x] **接线验证（Fix，Minimum Rules #23）**：确认 BizModel → QueryAction 调用链上 errors 确实在 API 边界被拦截（测试断言抛错路径，非仅代码存在）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两个公开 mutation 在 errors 非空时抛错（不含空 errors 正常路径回归）
- [x] 表级/列级空 SQL 行为一致（均显式抛错）
- [x] **无静默跳过**：无 catch-and-return-empty / continue / 吞异常残留于公开路径
- [x] `No owner-doc update required`（代码向既有文档契约收敛，`nop-metadata.md:161` 已描述预期行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - API 级回归测试

Status: completed
Targets: `TestNopMetaLineageEdgeBizModel.java`（或同族测试文件）

- Item Types: `Fix | Proof`

- [x] **非法 SQL 正用例（Fix）**：非法 SQL 经 GraphQL mutation（或 BizModel 直接调用，按既有测试风格）→ 断言 `resp.hasError()`（或异常传播）为真 + 精确错误码 + param 含 metaTableId；**断言"不再返回成功响应 + edgeCount=0"**（区分性断言）
- [x] **空 SQL 用例（Fix）**：表级空 sourceSql → 抛 `ERR_LINEAGE_SQL_SOURCE_EMPTY`；列级空 sourceSql → 同错误码（两级一致断言）
- [x] **正常路径回归（Fix）**：合法 SQL 抽取仍返回 edgeCount + 无 errors（既有正路径用例不回归）
- [x] **既有测试核对（Proof）**：grep 全部调用 `extractLineageFromSql` / `extractColumnLineageFromSql` 的测试，确认无测试依赖"errors 非空仍返回成功"（audit 已确认无，执行时复核）
- [x] 全量回归：`./mvnw test -pl nop-metadata -T 1C`（0 failures）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：从公开 mutation 入口 → 非法 SQL → 错误响应（非 200+零边）的完整路径已断言（见 Minimum Rules #22 语义：入口到出口行为链完整）
- [x] 非法 SQL / 空 SQL / 正常 SQL 三类用例全绿且为区分性断言
- [x] 既有抽取器层 assertThrows 测试与 API 层新测试互补（两层都钉死契约）
- [x] `./mvnw test -pl nop-metadata -T 1C` 全绿（0 failures）
- [x] `No owner-doc update required`（契约未变，实现向文档收敛）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（arm-index 终态 + closure audit）

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [x] arm-index-nop-metadata.md 本 plan 对应 P1 行（`2026-08-05-0655#P1-03` / `#AR-03`）终态（fixed + 本 plan 引用 + 修复摘要 + 测试证据）——**首轮登记由 plan {3} Phase 3 完成（planned），本 Phase 仅更新终态；若 {3} 未先行，则登记+终态一次完成**（收口段显式执行序见 Related；ID 用轮次限定格式防与历史 AR-03 混淆）
- [x] roadmap 对应工作项行更新（如适用；登记段由 plan {3} Phase 3 建立）
- [x] 独立子 agent closure audit（fresh session）逐项核对，证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index + roadmap 终态一致可追溯
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -T 1C` 全绿（0 failures）
- [x] 无静默降级：契约漂移为 fixed，无 live defect 被降级
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 血缘提取公开 API 对 SQL 解析失败显式抛错（非成功响应 + 零边），表级/列级行为一致
- [x] API 级回归测试落地（非法 SQL hasError + 精确错误码 + 空 SQL 两级一致 + 正常路径不回归）
- [x] 与 `docs-for-ai/03-modules/nop-metadata.md:161` 契约对齐（代码向文档收敛，无文档变更需求）
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）BizModel → QueryAction errors 拦截链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -T 1C`
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）

## Deferred But Adjudicated

### `LineageExtractResultDTO.sourceTables` 字段语义（AR-06）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 全仓 0 消费方（audit grep 证实），当前无运行时影响；字段语义与名称相反属契约缺陷但无人读取；归 Follow-up Backlog（本 mission P2 批次），非本 plan in-scope
- Successor Required: `no`（随 backlog 处理）
- Successor Path: —

## Non-Blocking Follow-ups

- no remaining plan-owned work（in-band errors 语义仅剩 `extractMeasureLineage`（有意保留，记录于 Phase 2 + arm-index 终态）；`LineageExtractResultDTO.sourceTables` 字段语义（AR-06）已在 Deferred But Adjudicated 段归 backlog）

## Closure

Status Note: 4 Phase 全执行；BizModel 边界 errors 非空即抛 + 表级/列级空 SQL 统一 + 5 个 API 级回归测试；`./mvnw test -pl nop-metadata -am -T 1C` 894/0 全绿；arm-index P1-03/AR-03 终态 fixed + roadmap R5.2 done；独立子 agent closure audit PASS；check-plan-checklist --strict exit 0 + scan-hollow exit 0。
Completed: 2026-08-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，read-only）`ses_02e2d16ccffeHzWZnzW9tROWV4`
- Evidence:
  - Phase 1 Exit Criteria：PASS（live 复核：errors 唯一来源 QueryAction :165/:221 两处 catch；三错误码定义+占位符在位（LineageErrors.java:10/22/41）；无测试钉死"errors 非空→成功"）
  - Phase 2 Exit Criteria：PASS（BizModel `checkNoParseErrors`（:111-119）在 DTO 构造前抛——表级 ERR_LINEAGE_SQL_PARSE_FAILED :126 / 列级 ERR_COL_LINEAGE_SQL_PARSE_FAILED :141，param metaTableId + error 细节；QueryAction 表级空 SQL 守卫前置 try（:158-160，镜像列级 :213-216）；LineageErrors.java:41-43 通用措辞；抽取器未改（non-goal 遵守）；diff = +121 insertions / 0 deletions）
  - Phase 3 Exit Criteria：PASS（`TestNopMetaLineageEdgeBizModel` +5：testExtractLineageFromSqlParseFailureExplicitError :811-840（GraphQL hasError + getData()==null 区分性断言 + 精确错误码 + BizModel 直接调用 assertThrows + getParam("metaTableId")/("error") 接线断言）、testExtractColumnLineageParseFailureExplicitError :843-863、testExtractLineageFromSqlEmptySourceSqlExplicitError :869-884、testExtractColumnLineageEmptySourceSqlExplicitError :887-902、testEmptySourceSqlTableAndColumnLevelConsistent :905-919（两级同错误码）；既有 29 用例零删除；抽取器层 assertThrows（TestSqlColumnLineageExtractor）与 API 层双层互补）
  - Phase 4 Exit Criteria：PASS（arm-index :128 P1-03 / :129 AR-03 → fixed（plan-2026-08-05-1842-2 引用 + 修复摘要 + 测试证据）；roadmap :213 R5.2 → done；无 live defect 被降级）
  - Closure Gates：12/12 PASS（含 Anti-Hollow：GraphQL mutation → BizModel :121-126 → QueryAction :144-169（catch :167 errors.add）→ LineageExtractResult → checkNoParseErrors :111-119 → throw → GraphQL 错误响应全链代码追踪连通；运行时由新测试从 mutation 入口断言）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0（0 Critical/High）
  - 全量验证：`./mvnw test -pl nop-metadata -am -T 1C` → **894 tests / 0 failures / 0 errors / 0 skipped**（889 基线 + 5），BUILD SUCCESS（首跑 nop-stream-rocksdb 计时基准 flaky 1 项与本次改动无关——单跑 ratio=0.35 PASS，复跑全绿）
  - Deferred 项分类检查：Deferred 仅 `LineageExtractResultDTO.sourceTables` 语义（AR-06，P2 归 backlog，0 消费方）；in-band errors 保留面仅 extractMeasureLineage（有意收缩，记录于 arm-index 终态）
  - checkstyle：模块无独立 checkstyle 配置（历史惯例 "checkstyle N/A"），mvn 构建默认检查通过；maven checkstyle 插件对全模块 + 上游模块报 8462 条既有违规（含未改动文件），非本 plan 引入、非构建门禁

Follow-up:

- no remaining plan-owned work（见 Non-Blocking Follow-ups）

## Optional Sections

- `## Risks And Rollback`：若既有测试意外依赖"errors 透传"语义（Phase 1/3 已复核确认无），回滚 = 还原 BizModel 两处抛错 + QueryAction 空 SQL 分支；改动集中于两个文件，commit 级可逆
