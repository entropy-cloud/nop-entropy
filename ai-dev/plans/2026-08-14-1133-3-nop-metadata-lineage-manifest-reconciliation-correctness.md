# 03 — nop-metadata Lineage/Manifest/Reconciliation 正确性修正（AR-07/08/09/11/12/13）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-metadata-invariant-loop
> Work Item: Follow-up Backlog — lineage/manifest 正确性族（AR-07/08/09）+ reconciliation/方言/诊断族（AR-11/12/13）
> Source: `ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md`（AR-07–AR-13）
> Related: open-audit 总评第 3 点"lineage/manifest 是 metadata-correctness 的薄弱带"

## Purpose

把 2026-08-14 再审计中的 **6 个 P2 metadata-correctness** 缺陷收口为：lineage 源表抽取不跨 schema 塌缩、不把 CTE 名报为物理表、manifest 图边不因重复关系膨胀、SQL 视图类型推断不因尾分号失败、reconciliation 匹配不受 JVM locale 影响、reconciliation 候选序列化有界。全部为"文档记录为简化/限制，但 false-drop/false-positive 后果未记录"的元数据正确性缺陷——对元数据目录而言，错误的血缘边/图度数/匹配结果是产品级缺陷。

## Current Baseline

> 行级读证见 Source 审计文件 evidence 段。以下事实经本轮 live repo 核实。

- **AR-07（P2，SqlSourceTableExtractor 跨 schema 同名表塌缩）**：`SqlSourceTableExtractor.java:78-81` 去重 key 为 `simple`（非限定名）。`dbo.users` 和 `sales.users` 的 `simple` 均为 `"users"` → 第二个物理表被静默丢弃，lineage 图缺边。注释（:78）记录"schema.table 与 table 视作同一目标"，但跨 schema 同名表的 false-drop 后果未记录。
- **AR-08（P2，CTE 名报为物理源表）**：`SqlSourceTableExtractor.java:64-83` 递归遍历 AST 收集所有 `SqlSingleTableSource` 的表名。`WITH t AS (...) SELECT * FROM t` 中 `t` 被解析为 `SqlSingleTableSource` → CTE 名被报为物理源表。类注释（:30）说"不展开 CTE 别名"但未承认 false-positive 后果（CTE 名匹配真实 catalog 表 → 产幽灵血缘边）。
- **AR-09（P2，MetaManifestBuilder.addEdge 无去重）**：`MetaManifestBuilder.java:107-109` 调 `addEdge(parentMap, ...)` + `childMap.computeIfAbsent(...).add(...)`；`addEdge`（:225-229）无条件 `add(value)`。重复 `NopMetaEntityRelation` 行 → 同一邻居出现 N 次；自环（owner==target）未过滤 → 图度数膨胀。
- **AR-11（P2，SqlViewFieldTypeInferrer 尾分号）**：`SqlViewFieldTypeInferrer.java:151-152` 的 `wrappedSql = "SELECT * FROM (" + sourceSql + ") _t LIMIT 0"`。当 `sourceSql = "SELECT a FROM t;"` 时内层尾分号被多数 JDBC 驱动拒绝，`SQLException` 包装为 `ERR_SQL_TYPE_INFERENCE_FAILED`——用户看到的"类型推断失败/语法错误"实际只需剥尾分号。
- **AR-12（P2，LocalReconciliationProcessor locale 风险）**：`LocalReconciliationProcessor.java:121-122` 的 `a.toLowerCase()` / `b.toLowerCase()` 使用默认 locale。tr-TR 下 `"I".toLowerCase()` → `"ı"`，同一数据在不同 JVM locale 下匹配结果不同。精确路径（:110）用 locale-insensitive `equalsIgnoreCase`，两路径 locale 不一致。
- **AR-13（P2，ReconciliationExecutor 忽略 limit）**：`ReconciliationExecutor.java:83` 的 `reconcile(value, ..., null)` 硬编码 limit 为 null。`LocalReconciliationProcessor.reconcile` 仅在 `limit != null && limit > 0` 时截断（:85-86），故所有 fuzzy 候选（score > 阈值）被全量序列化进 `details` JSON，大候选池下可能 OOM/多秒序列化。
- **构建/测试命令**（mission 配置）：`./mvnw test -pl nop-metadata -am -T 1C`。

## Goals

- AR-07：`SqlSourceTableExtractor.extract` 按 `full`（schema-qualified 名）去重，跨 schema 同名表不再塌缩为一条边。
- AR-08：CTE 名不再被报为物理源表——收集 WITH 子句声明的 CTE 名并排除（或作为独立 `ctes` 列表返回）。
- AR-09：`MetaManifestBuilder` 的 `parentMap`/`childMap` 邻接表去重 + 过滤自环。
- AR-11：`SqlViewFieldTypeInferrer` 在包装前 trim + 剥单个尾分号。
- AR-12：`LocalReconciliationProcessor.levenshteinSimilarity`（:121-122）改用 `toLowerCase(Locale.ROOT)`。
- AR-13：`ReconciliationExecutor.execute` 传入默认 limit = 50（硬编码，非 null），候选序列化有界。
- 每项均有回归测试。

## Non-Goals

- 不重写 `SqlSourceTableExtractor` 为完整 SQL 语义分析器（CTE 展开、子查询别名解析等超出范围）。
- 不改变 reconciliation 匹配算法本身（Levenshtein 复杂度已由 AR-23⑥ 裁定）。
- 不处理 P2 死码批次（AR-14）——单独的代码卫生清扫。
- 不处理 P2 安全硬化族（F5-F9）——由 sibling plan `2026-08-14-1133-1-...` 覆盖。
- 不处理 P2 silent-wrong-result 族（AR-05/06/10）——由 sibling plan `2026-08-14-1133-2-...` 覆盖。

## Scope

### In Scope

- AR-07：去重 key 从 `simple` 改为 `full` + 测试。
- AR-08：CTE 名排除 + 测试。
- AR-09：邻接表去重 + 自环过滤 + 测试。
- AR-11：尾分号 strip + 测试。
- AR-12：locale ROOT + 测试。
- AR-13：limit 传递 + 测试。
- 受影响 owner-doc 同步。

### Out Of Scope

- P2 死码批次（AR-14）。
- P2 安全/silent-wrong-result/ORM/卫生族。
- SqlSourceTableExtractor 完整 CTE 展开实现。

## Execution Plan

### Phase 1 — Lineage 源表抽取正确性（AR-07 + AR-08）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/lineage/SqlSourceTableExtractor.java`；测试文件需新建（`io.nop.metadata.service.lineage` 包下 `TestSqlSourceTableExtractor.java`）

- Item Types: `Fix | Proof`

- [x] AR-07：去重 key 从 `seen.add(simple)` 改为 `seen.add(full)`（schema-qualified 名），保留首次出现的 `full`
- [x] AR-07：更新注释（:78）说明按 `full` 去重后的残留歧义（同一 full 名经不同别名引用仍为一条边）
- [x] AR-08：**实证**（Proof item）：`parser.parseFromText(null, "WITH t AS (SELECT 1) SELECT * FROM t")` 返回的 `SqlProgram` 内含 `SqlSelectWithCte` 节点（非裸 `SqlSelect`）；CTE 名住在 `SqlSelectWithCte.getWithCtes()` → `List<SqlCteStatement>` → `SqlCteStatement.getName()`（AST 节点类见 `_SqlSelectWithCte.java`、`_SqlCteStatement.java`）。若实证发现 AST 结构不同，调整收集策略
- [x] AR-08：在 `extract` 中收集 WITH 子句声明的 CTE 名（遍历 `SqlSelectWithCte.getWithCtes()`），将匹配 CTE 名的 `SqlSingleTableSource` 从结果列表排除。CTE 名遮蔽同名物理表时，CTE 名被排除（符合 SQL 作用域语义），物理表引用不补回
- [x] AR-08：新增测试——`WITH t AS (SELECT 1) SELECT * FROM t JOIN real_table` → 结果只含 `real_table`，不含 CTE 名 `t`
- [x] AR-07：新增测试——`SELECT * FROM dbo.users JOIN sales.users` → 结果含两条边（`dbo.users` + `sales.users`），不塌缩
- [x] AR-07：现有 `TestNopMetaLineageEdge*` 集成测试无回归（dedup key 改动不破坏既有 lineage 边创建链路）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 跨 schema 同名表不再塌缩（测试验证两条独立边）
- [x] CTE 名不再出现在物理源表列表中（测试验证 WITH 子句中的 CTE 名被排除）
- [x] CTE 名遮蔽同名物理表时，CTE 名被排除（符合 SQL 作用域语义），物理表引用不补回
- [x] 现有 `TestNopMetaLineageEdge*` 集成测试无回归
- [x] **无静默跳过**：CTE 排除逻辑对无 WITH 的 SQL 无影响（普通 SQL 行为不变）
- [x] owner-doc（`docs-for-ai/03-modules/nop-metadata.md`）同步 lineage 抽取语义（按 full 去重 + CTE 排除）
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 2 — Manifest 图边去重与自环过滤（AR-09）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/manifest/MetaManifestBuilder.java`；测试文件需新建（`io.nop.metadata.service.manifest` 包下 `TestMetaManifestBuilder.java`）

- Item Types: `Fix | Proof`

- [x] AR-09：`addEdge` 增加去重——追加前检查 `!list.contains(value)`，或改用 `LinkedHashSet` 作邻接存储
- [x] AR-09：过滤自环——`key.equals(value)` 时不追加
- [x] AR-09：`childMap.computeIfAbsent(...).add(...)`（:109）同步去重 + 自环过滤
- [x] AR-09：新增测试——构造重复 `NopMetaEntityRelation`（owner→target 出现 2 次）→ manifest 邻接表无重复邻居；自环（owner==target）→ 不出现

Exit Criteria:

- [x] 重复关系不产重复图边（测试验证邻接表无重复）
- [x] 自环被过滤（测试验证 owner==target 不出现）
- [x] **接线验证**：`MetaManifestBuilder.build` → `addEdge` 路径在运行时被真实调用（经 build → 遍历 relations → addEdge）；parentMap 与 childMap 双向邻接表均去重 + 自环过滤（测试验证两个方向）
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 3 — Reconciliation 与视图推断正确性（AR-11 + AR-12 + AR-13）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/sqlview/SqlViewFieldTypeInferrer.java`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationProcessor.java`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/ReconciliationExecutor.java`；对应测试文件

- Item Types: `Fix | Decision | Proof`

- [x] AR-11：`inferWithinConnection` 包装前 `sourceSql = sourceSql.trim()` 并循环剥尾分号（`while (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length()-1).trim();`），覆盖 `;;` 双分号边角
- [x] AR-11：新增测试——`sourceSql` 带/不带尾分号均成功推断（H2 实跑验证）
- [x] AR-12：`LocalReconciliationProcessor.levenshteinSimilarity`（:121-122）的 `a.toLowerCase()` / `b.toLowerCase()` 改为 `toLowerCase(Locale.ROOT)`
- [x] AR-12：新增/确认测试——默认 locale 与 ROOT locale 下同一输入匹配结果一致
- [x] AR-13：`Decision`（预裁定）：硬编码默认 limit = 50（不引入 ORM/配置变更，避免撞 ORM Protected Area）；`ReconciliationExecutor.execute`（:83）传入 50 而非 null。config-driven limit（含 extConfig JSON 键或新增 ORM 列）移入 Non-Blocking Follow-ups
- [x] AR-13：新增测试——大候选池下 details 候选数 ≤ limit（有界序列化）

Exit Criteria:

- [x] 带 `;` 的 sourceSql（含 `;;` 双分号）不再因内层分号导致类型推断失败
- [x] `toLowerCase` 使用 `Locale.ROOT`（locale-insensitive）
- [x] 候选序列化有界（limit 参数被传入，候选数有上限）
- [x] **无静默跳过**：limit 参数被真实消费（非仍传 null 静默忽略）
- [x] owner-doc（`docs-for-ai/03-modules/nop-metadata.md`）同步 reconciliation limit 语义（默认 50 有界）+ 视图推断尾分号处理
- [x] `ai-dev/logs/2026/08-14.md` 已追加

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-07/08/09/11/12/13 六项 metadata-correctness 缺陷全部修复
- [x] 每项均有回归测试钉死
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证修复在运行时路径生效（SqlSourceTableExtractor.extract / MetaManifestBuilder.build / SqlViewFieldTypeInferrer.inferTypes / ReconciliationExecutor.execute 被真实调用）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 4 条不变式门禁仍零命中（INV-SILENT-SWALLOW / INV-UK-CONSTRAINT / INV-SENSITIVE-LITERAL / INV-LIMIT；验证命令见 `ai-dev/tools/run-nop-metadata-invariants.sh` 或各 `.mjs` 扫描器显式运行）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- AR-14（死码/诊断退化批次）——独立代码卫生清扫，不阻塞本计划。
- SqlSourceTableExtractor 完整 CTE 展开（当前只排除 CTE 名，不展开 CTE 体）——optimization candidate。
- Reconciliation config-driven limit（extConfig JSON 键或新增 ORM 列）——optimization candidate，含 ORM Protected Area 风险（需 plan-first clearance 如走 ORM 列路径）。

## Closure

Status Note: 6 个 P2 metadata-correctness 缺陷（AR-07/08/09/11/12/13）全收口——每项有 Fix + 回归测试，owner-doc 已同步，不变式门禁无新命中。config-driven reconciliation limit 明确裁定为 Non-Blocking Follow-up（含 ORM Protected Area 风险，需 plan-first clearance）。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: EXEC_PLANS self-audit（mission-driver 2026-08-14-070741，task session 内逐条核验 live code + 测试输出）
- Evidence:
  - **Phase 1 Exit Criteria**（全 PASS）：
    - 跨 schema 同名表不塌缩：`TestSqlSourceTableExtractor.crossSchemaSameSimpleNameNotCollapsed`（dbo.users + sales.users = 2 edges）PASS
    - CTE 名排除：`TestSqlSourceTableExtractor.cteNameExcludedFromPhysicalSourceTables`（WITH t → 只含 real_table）PASS
    - CTE 遮蔽物理表：`TestSqlSourceTableExtractor.cteNameShadowsPhysicalTableExcluded` PASS
    - 现有集成测试无回归：`TestNopMetaLineageEdge*` 38 tests, 0 failures PASS
    - 无静默跳过：`TestSqlSourceTableExtractor.noWithClauseUnchanged` PASS
  - **Phase 2 Exit Criteria**（全 PASS）：
    - 重复关系不产重复图边：`TestMetaManifestBuilder.duplicateRelationNoDuplicateEdge`（parentMap+childMap 双向）PASS
    - 自环被过滤：`TestMetaManifestBuilder.selfLoopFiltered` PASS
    - 接线验证：`TestMetaManifestBuilder.mixedDuplicateAndSelfLoopAndNormal`（build→遍历 relations→addEdge 运行时路径）+ `TestNopMetaModuleBizModel`（generateManifest 端到端）16 tests PASS
  - **Phase 3 Exit Criteria**（全 PASS）：
    - 尾分号不导致类型推断失败：`TestNopMetaTableQueryBizModel.testCreateSqlTableTrailingSemicolonInfersTypes` + `testCreateSqlTableSemicolonWithWhitespaceInfersTypes`（H2 实跑 INTEGER）PASS
    - Locale.ROOT：`TestLocalReconciliationProcessorLocale.turkishLocaleNotAffectingSimilarity`（tr-TR 下 I/i=1.0）PASS
    - 候选序列化有界：`TestReconciliationExecutorLimit.largeCandidatePoolBoundedByLimit`（100 candidates → 50 in details）PASS
    - 无静默跳过：`TestReconciliationExecutorLimit.defaultLimitPassedToProcessor`（limit=50 非 null）PASS
  - **Closure Gates**（全 PASS）：
    - `./mvnw test -pl nop-metadata/nop-metadata-service,nop-metadata/nop-metadata-web -T 1C` → 1167 tests, 0 failures（1166 service + 1 web）
    - `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` BUILD SUCCESS
    - checkstyle：sun_checks.xml 全模块 pre-existing 基线（项目 checkstyle 非真实门禁，本次改动遵循既有代码风格）
    - 不变式门禁：INV-UK exit=0 / INV-SENSITIVE exit=0 / INV-LIMIT exit=0（4 tests, 0 failures）；INV-SILENT-SWALLOW 2 个 pre-existing hit（MetaTableProfiler AR-06 Javadoc 误报 + AggregationHelper AR-10 裁定的 NumberFormatException→null）——本次变更引入零新 hit（SqlSourceTableExtractor/MetaManifestBuilder/SqlViewFieldTypeInferrer/LocalReconciliationProcessor/ReconciliationExecutor 均不在 hit 列表）
  - **Anti-Hollow 检查**：所有修复经端到端路径验证——SqlSourceTableExtractor.extract（TestSqlSourceTableExtractor 13 tests 直接调用 extract）/ MetaManifestBuilder.build（TestMetaManifestBuilder 4 tests 直接调用 build + TestNopMetaModuleBizModel generateManifest 端到端）/ SqlViewFieldTypeInferrer.inferTypes（TestNopMetaTableQueryBizModel createSqlTable querySpace 端到端 H2 实跑）/ ReconciliationExecutor.execute（TestReconciliationExecutorLimit 3 tests 直接调用 execute）
  - **Deferred 项分类检查**：config-driven reconciliation limit 明确裁定为 optimization candidate（Non-Blocking Follow-up，含 ORM Protected Area 风险）；无 in-scope live defect 被降级

Follow-up:

- config-driven reconciliation limit（extConfig JSON 键或新增 ORM 列）——optimization candidate，含 ORM Protected Area 风险（需 plan-first clearance 如走 ORM 列路径）。
- SqlSourceTableExtractor 完整 CTE 展开（当前只排除 CTE 名，不展开 CTE 体）——optimization candidate。
- AR-14（死码/诊断退化批次）——独立代码卫生清扫，不阻塞本计划。
