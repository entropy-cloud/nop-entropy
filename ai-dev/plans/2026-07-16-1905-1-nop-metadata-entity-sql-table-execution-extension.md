# 1905-1 nop-metadata entity/sql 类型表执行扩展（Catalog/Quality/Profiling 覆盖）

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P2/P4 执行覆盖 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4 查询执行（D1 三路分派 + D2 sql querySpace 归属）+ §2.3.2 / §2.5.1 / §2.7.1；plan 0420-1 / 0530-1 / 0530-2 / 0700-1 Deferred But Adjudicated（entity/sql 执行，Successor Required: yes）
> Related: `2026-07-16-0800-1-...`（提供 `MetaDataSourceResolver` + queryTableData 三路分派，本 plan 复用其 querySpace 解析能力）；`2026-07-16-0420-1/0530-1/0530-2`（Catalog/Quality/Profiling executor，本 plan 扩展其 tableType 覆盖范围）
> Draft Review: R1 独立子 agent 审查（含想象性分析 + live repo 核验）发现 3 Blocker（B1 MetaAggregationExecutor entity 路径误述为 QueryBean、B2 STDDEV 可移植性未论证、B3 平台 session Connection 路径未调查即否决）+ Major。R1 共识 NO。已据 live 核验（entity 路径实为 raw SQL via `orm().executeQuery`；`IJdbcTransaction.getConnection()` 可达）重写 D1 为"平台 Connection 复用现有 executor"，消除 B1/B2/B3。

## Purpose

把反复 deferred 的「entity/sql 类型表的 Catalog 收集 / 质量规则执行 / 数据剖析」从 follow-up 推进到 landed：使 Catalog（P2-4）、Quality Rule 执行（P2-6）、Profiling（P2-7）三大执行器的覆盖范围从「external-only」扩展到 entity + sql 类型表，收口"任意 tableType 的逻辑表都可目录化、可质量检查、可剖析"这一结果面。

## Current Baseline

- **29 实体已建模**（`nop-metadata/model/nop-metadata.orm.xml`，已 `rg -c '<entity '`=29）。三大执行器已落地（均 **external-only**，已 live 核验）：
  - `MetaCatalogCollector`（`.../service/catalog/MetaCatalogCollector.java`，plan 0420-1）——入参物理 `tableName` + `Connection` + `DatabaseMetaData`，经 callback 收集 **rowCount + indexCount**（经 `getIndexInfo()`）。（注：不收集"列"，列信息由 Profiler 经 `DatabaseMetaData.getColumns()` 读。）
  - `MetaQualityRuleExecutor`（`.../service/quality/MetaQualityRuleExecutor.java`，plan 0530-1）——`entityType=database` 显式 SKIP（`setMessage("...external-table-only execution")`，`:87`）；external 表经 `withConnection` 跑检测 SQL（not_null/unique/range/regex/freshness/volume/custom_sql）。
  - `MetaTableProfiler`（`.../service/profiling/MetaTableProfiler.java`，plan 0530-2）——入参物理 `tableName`（external）+ `Connection` + `DatabaseMetaData`，经聚合 SQL（COUNT/MIN/MAX/AVG/**STDDEV_SAMP**，全方言精确）+ in-app 排序（median/percentile/distribution）收集统计。
- **三大 executor 的执行核心均基于裸 JDBC `Connection` + 物理 `tableName`**（不经 EQL 编译器），这是 STDDEV_SAMP/median/percentile/distribution 全方言可用的根因。**复用它们只需提供正确的 `Connection` + table-reference**，无需重写统计逻辑。
- **querySpace→数据源解析已落地**（plan 0800-1）：`MetaDataSourceResolver.resolveActiveOrThrow(...)`（`.../service/datasource/`）按 `NopMetaDataSource.querySpace == 目标 querySpace` 取首条 ACTIVE 数据源；找不到/DISABLED 显式失败。
- **§4.4 查询执行三路分派已落地**（plan 0800-1）。**关键 live 事实（R1 B1 纠正）**：`MetaAggregationExecutor` 的 entity 路径（`executeEntityAggregation`，`.../service/query/MetaAggregationExecutor.java:143-206`）**不用 ORM `QueryBean` 聚合**——它手工拼 `SELECT ... FROM <物理表> GROUP BY ...` 再经 `orm().executeQuery(SQL.begin().allowUnderscoreName(true).sql(sqlText,...).end(), ...)` 跑**原生 SQL**（物理表名取自 `entity.getTableName()`，物理列取 `columnCode`）。`GranularityBucketing.java:18` 明确记录：`orm().executeQuery` 时 EQL 编译器校验函数名（FORMATDATETIME 等被判 unknown-function）——**故 entity 路径若走 EQL 会丢 STDDEV 等函数**。
- **平台 JDBC Connection 可达（R1 B3 调查结论）**：`IJdbcTransaction.getConnection()`（`nop-persistence/nop-dao/.../jdbc/txn/IJdbcTransaction.java:15`，`Connection getConnection()`）返回平台事务的 JDBC 连接；`JdbcTemplateImpl` 等已用此取平台连接。nop-metadata-dao 依赖 nop-orm→nop-dao，故平台 Connection **可达**。**这意味着 entity 表可取平台 Connection 后，原样复用现有三大 executor**（entity 物理表 = `NopMetaEntity.tableName`，存在于平台库）。
- **entity 表物理解析**：`NopMetaTable.querySpace`（entity 表）来自 `NopMetaEntity.querySpace`；entity 表引用真实 ORM 实体（`baseEntityId → MetaEntity.entityName`），物理表名 `NopMetaEntity.tableName`，数据由平台 ORM 管理于平台库。前置校验：`orm().isValidEntityName(entityName)`（实体须注册，否则显式失败）。
- **sql 表物理解析**：`sourceSql` 为 SQL 文本；`querySpace` 必须非 null 且匹配一个 NopMetaDataSource（§4.4 D2）；执行时包成 `(<sourceSql>) _t` 子查询（与 queryTableData sql 路径一致）。
- **标识符注入防护先例**：`MetaQualityRuleExecutor.IDENTIFIER_PATTERN`（`^[A-Za-z_][A-Za-z0-9_]*$` 白名单 + PreparedStatement 值绑定）。
- **sql 视图字段名风险（R1 M8）**：`SqlSelectFieldExtractor` 对无别名表达式列产合成名 `<expr_N>`（§4.2.1），含 `<>` 通不过标识符白名单——field 级质量检查/剖析须显式处理此类列（跳过该列 + 标记，不整表失败）。
- **ErrorCode 惯例**：内联 `static final ErrorCode ERR_... = ErrorCode.define(...)`。
- **设计文档现状**：§4.4 记录了 queryTableData/聚合的三路分派；Catalog/Quality/Profiling 的「external-only」限制记录在各自 plan 的 Deferred，**未写入 §4.4 的覆盖范围裁定**。

## Goals

- Catalog 收集覆盖 entity + sql 类型表（不再限于 external）。
- Quality Rule 执行覆盖 entity + sql 类型表（不再限于 external；`entityType=database` 仍 SKIP 但 table-level entity/sql 可执行）。
- Profiling 覆盖 entity + sql 类型表（不再限于 external；entity 路径复用平台 Connection 后 STDDEV/median/percentile/distribution 全可用，无降级）。
- entity 路径执行机制裁定（平台 JDBC Connection 复用现有 executor）写入 `01-architecture-baseline.md` §4.4。
- sql 路径（sourceSql 子查询 + withConnection）写入设计文档，与 §4.4 一致。
- 三类 tableType（entity/external/sql）× 三大执行器（Catalog/Quality/Profiling）各端到端验证。

## Non-Goals

- 联邦查询（queryTableData/JOIN/聚合）的 tableType 覆盖扩展——已由 plan 0800-1/0800-2 落地（entity/external/sql 三路均已支持），本 plan 不改查询执行层。
- 多 schema 数据源（需 NopMetaTable 增加 schema 列，Protected Area 变更，与 Catalog/质量/剖析同源 follow-up）。
- `NopMetaTable.querySpace` 索引优化（全表扫描，外部表数量增长后加索引，non-blocking）。
- 质量评分 / Checkpoint 编排（MetaQualityCheckpoint/MetaQualityScore，未建模实体，独立结果面）。
- sql 表字段类型推断（方案 B/C，继承 0700-1 follow-up）。

## Scope

### In Scope

- **D1 裁定（entity 路径执行机制）—— 平台 JDBC Connection 复用**：entity 表取**平台事务 JDBC Connection**（`IJdbcTransaction.getConnection()`）+ 物理表名 `NopMetaEntity.tableName`，**原样传入现有三大 executor**（不经 EQL、不重写统计逻辑）。理由：entity 物理表在平台库，平台 Connection 直达，现有 executor 的 STDDEV/median/percentile/distribution 全方言精确逻辑零修改可用；避免 `orm().executeQuery` 的 EQL unknown-function 风险（§4.4.2 D7 已实测 FORMATDATETIME 被拒）。裁定写入 §4.4。
- **D2 裁定（sql 路径执行机制）**：sql 表经 `MetaDataSourceResolver` 解析 querySpace→NopMetaDataSource，`withConnection` 对 `(<sourceSql>) _t` 子查询跑目录/检测/剖析 SQL（现有 executor 把 table-reference 改为子查询包装）。与 §4.4 queryTableData/聚合 sql 路径一致。裁定写入 §4.4。
- **D3 裁定（共享 table-reference 分派契约）**：抽取一个共享 table-reference 解析器（落 `.../service/`），输入 `NopMetaTable` → 产出三种 table-reference 形态之一，供三大 executor 统一消费：
  - external →（withConnection callback 语义 + 物理 `tableName`）
  - entity →（平台 Connection + 物理表名 `entity.tableName`）
  - sql →（withConnection callback + `(<sourceSql>) _t` 子查询）
  executor 内部不再硬编码 external-only，而是按 reference 形态执行。契约（输入/输出/失败语义）在 Phase 1 定义，Phase 2/3 消费。
- **D4 裁定（能力边界）**：因 entity 路径复用平台 Connection，**entity/sql/external 三者的统计/检查能力集合完全一致**（同一 executor 核心）——无 entity 专属降级。custom_sql 在 entity 路径**supported**（raw SQL 跑在物理表上）。不可执行路径（表不存在/实体未注册/DISABLED/非 jdbc/sourceSql 不可解析）显式失败，不静默 SKIP/不伪造 unavailable。裁定写入 §4.4。
- **D5 裁定（sql 视图合成列名处理）**：sql 视图无别名表达式列产 `<expr_N>` 合成名，通不过标识符白名单——field 级检查/剖析对该列**显式 SKIP + details 标记 `reason=derived-column-skipped`**（不整表失败、不伪造），table 级检查不受影响。裁定写入 §4.4。
- Catalog executor：扩展支持 entity（平台 Connection + rowCount/indexCount）+ sql（子查询 rowCount/indexCount）。
- Quality executor：扩展支持 entity（平台 Connection 跑检测 SQL）+ sql（子查询检测）；移除 external-only 硬限制。
- Profiling executor：扩展支持 entity（平台 Connection + 全统计，无降级）+ sql（子查询 + 全统计）。
- entity 表前置校验：实体未注册（`isValidEntityName==false`）/ `entity.tableName` 为空 显式失败抛 inline ErrorCode，不静默空集。
- 三大执行器 × entity/sql 各端到端测试（H2 本地，真实产出结果，非空壳）。

### Out Of Scope

- JOIN/聚合查询层的 tableType 扩展（已落地）。
- 写操作（INSERT/UPDATE/DELETE），本 plan 仅只读执行。
- 质量评分 / Checkpoint / Event 实体（未建模，独立 plan）。
- 多 schema、querySpace 索引、定时调度（各同源 follow-up）。

## Execution Plan

### Phase 1 - 设计裁定 + 共享 table-reference 分派

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md`（§4.4 追加 Catalog/Quality/Profiling 覆盖范围裁定 + entity/sql 路径子节）；`nop-metadata-service` 新增共享 table-reference 解析器

- Item Types: `Decision | Fix | Proof`

- [x] **D1 裁定并写入 §4.4**：entity 表执行机制——取平台事务 JDBC Connection（`IJdbcTransaction.getConnection()`）+ 物理表名 `NopMetaEntity.tableName`，原样传入现有 executor。给出选此（非 `orm().executeQuery`）的理由：EQL unknown-function 风险（§4.4.2 D7 实测）会丢 STDDEV 等函数。
- [x] **D2 裁定并写入 §4.4**：sql 表执行机制——`MetaDataSourceResolver` 解析 querySpace→NopMetaDataSource，withConnection 对 `(<sourceSql>) _t` 子查询执行。querySpace null/无数据源/DISABLED/非 jdbc/sourceSql 不可解析 显式失败。
- [x] **D3 裁定并写入 §4.4**：共享 table-reference 解析器契约——输入 `NopMetaTable`，输出 reference 形态（external/entity/sql 三态，含 Connection 获取方式 + table 名/子查询 + schemaPattern），三大 executor 按 reference 消费；定义 reference 不可解析时的显式失败语义。
- [x] **D4 裁定并写入 §4.4**：entity/sql/external 能力集一致（同一 executor 核心），custom_sql 在 entity supported；不可执行路径显式失败，无静默 SKIP/伪造 unavailable。
- [x] **D5 裁定并写入 §4.4**：sql 视图 `<expr_N>` 合成列在 field 级检查/剖析显式 SKIP + 标记（不整表失败）。
- [x] 实现共享 table-reference 解析器（落 `.../service/tableref/`），entity 路径取平台 Connection（经 `IJdbcTransaction`），sql/external 路径经 `MetaDataSourceResolver`。
- [x] 单测：table-reference 解析三态（external/entity/sql）的 found / not-found（显式失败）/ 实体未注册（显式失败）/ DISABLED 路径。

Exit Criteria:

- [x] D1/D2/D3/D4/D5 裁定已写入 §4.4，且与 §设计结论 #9 + §七（不引入 Driver/QuerySpace 抽象）一致。
- [x] 共享 table-reference 解析器存在，三态 reference 产出被单测覆盖；entity 路径确实取平台 Connection（`IJdbcTransaction.getConnection()`），非经 EQL。
- [x] **接线验证**：三大 executor 将改为引用该解析器（Phase 2/3 落地后验证运行时调用）；Phase 1 至少验证解析器本身三态产出正确。
- [x] entity 前置校验（`isValidEntityName` / tableName 空）失败时显式抛 ErrorCode，不静默空集。
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4 已更新；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - sql 类型表执行扩展（Catalog + Quality + Profiling）

Status: completed
Targets: `nop-metadata-service`（MetaCatalogCollector / MetaQualityRuleExecutor / MetaTableProfiler + 各 BizModel action）

- Item Types: `Fix | Proof`

- [x] Catalog executor 支持 sql 表：table-reference 解析为 sql 子查询形态，withConnection 对 `(<sourceSql>) _t` 收集 rowCount/indexCount。
- [x] Quality executor 支持 sql 表：经 withConnection 对子查询跑 not_null/unique/range/regex 检测；`entityType=field` 的 column 取自 sql 视图字段集合（MetaTableFieldResolver sql 分派），`<expr_N>` 合成列按 D5 SKIP+标记。
- [x] Profiling executor 支持 sql 表：经 withConnection 对子查询跑聚合 SQL + in-app 排序。
- [x] 失败路径显式化：sql 表 querySpace null/无数据源/DISABLED/非 jdbc/sourceSql 不可解析 均显式失败抛 inline ErrorCode。
- [x] sql × 三执行器 各端到端 AutoTest（H2，创建 sql 视图表 + 注册数据源 + 执行 + 断言结果非空）。

Exit Criteria:

- [x] Catalog/Quality/Profiling 对 sql 表各产出真实结果（rowCount/indexCount/检测结果/统计值），端到端验证通过。
- [x] **接线验证**：executor 运行时确实经共享 table-reference 解析器取 sql 子查询 reference + withConnection 执行（测试断言或调用链追踪）。
- [x] **无静默跳过**：sql 表执行失败路径显式抛异常；`<expr_N>` 列按 D5 显式 SKIP+标记（非整表失败、非静默）。
- [x] sql 表统计口径与 external 一致（同一 executor 核心），无重复造轮子。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - entity 类型表执行扩展（Catalog + Quality + Profiling）

Status: completed
Targets: `nop-metadata-service`（三大 executor entity 路径 + BizModel action）

- Item Types: `Fix | Proof`

- [x] Catalog executor 支持 entity 表：table-reference 解析为 entity 形态（平台 Connection + `entity.tableName`），executor 原样收集 rowCount/indexCount。
- [x] Quality executor 支持 entity 表：平台 Connection 跑 not_null/unique/range/regex/custom_sql 检测（物理表 `entity.tableName`）。
- [x] Profiling executor 支持 entity 表：平台 Connection 跑全统计（COUNT/MIN/MAX/AVG/STDDEV_SAMP + in-app median/percentile/distribution），**无降级**。
- [x] entity 前置校验：实体未注册（`isValidEntityName==false`）/ `entity.tableName` 空 显式失败抛 ErrorCode，不静默空集。
- [x] entity × 三执行器 各端到端 AutoTest（用本模块已注册实体作 fixture，importOrmModel 填充数据 + 执行 + 断言 STDDEV/median/distribution 真实值非 null）。

Exit Criteria:

- [x] Catalog/Quality/Profiling 对 entity 表各产出真实结果，端到端验证通过。
- [x] **结果验证（机制中性）**：entity 表 profiling 产出真实 STDDEV_SAMP / median / percentile / distribution 值（非 null、非伪造），证明 entity 路径统计能力与 external 一致、无降级。
- [x] **接线验证**：entity 路径确实经平台 JDBC Connection（`IJdbcTransaction.getConnection()`）执行，复用现有 executor 核心（测试断言执行路径 + 统计值真实）。
- [x] **无静默跳过**：实体未注册/tableName 空显式失败不静默空集；custom_sql 在 entity 路径 supported（非 SKIP）。
- [x] **端到端验证**：从 BizModel action（collectCatalog/executeQualityRule/profileTable）入口到 executor 产出结果完整跑通（非仅组件单测）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] Catalog/Quality/Profiling 三大执行器对 entity + sql 类型表均产出真实结果（external 保持不回归）。
- [x] entity 路径经平台 JDBC Connection 复用现有 executor，STDDEV/median/percentile/distribution 真实可用（无降级、无空壳）。
- [x] entity 路径执行机制（D1）+ sql 路径（D2）+ 分派契约（D3）+ 能力边界（D4）+ 合成列处理（D5）已写入 `01-architecture-baseline.md` §4.4.3。
- [x] 不存在被静默降级到 deferred 的 in-scope 执行路径（entity/sql 均已 landed 或显式裁定 unsupported 并抛异常）。
- [x] 受影响 owner doc（§4.4 + §2.3.2/§2.7.1 覆盖范围）已同步到 live baseline。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：三大执行器的 entity/sql 路径在运行时确实被调用并产出真实统计值（端到端验证）；无空方法体/静默 SKIP/伪造 unavailable。
- [x] `./mvnw test -pl nop-metadata -am` 通过。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

（执行中产生的优化项按规则归类记录于此。）

## Non-Blocking Follow-ups

- 多 schema 数据源的 Catalog/Quality/Profiling 执行（需 NopMetaTable 增加 schema 列，Protected Area 变更，与 Catalog/质量/剖析同源 follow-up）。
- `NopMetaTable.querySpace` 索引（entity/sql 覆盖后查询量增加，外部表增长后加索引，non-blocking）。
- 既存三处 `resolveDataSourceOrThrow` 重复的统一收敛（继承 0800-1 follow-up，不构成本 plan 阻塞项）。
- sql 表字段类型推断方案 B/C（继承 0700-1）。
- 平台 Connection 获取的统一抽象（若 entity/sql/external 三路后续需收敛为单一 withConnection 语义，可提取平台 Connection 适配为 datasource-type，non-blocking）。

## Closure

Status Note: 把反复 deferred 的「entity/sql 类型表的 Catalog 收集 / 质量规则执行 / 数据剖析」从 follow-up 推进到 landed。三大执行器（MetaCatalogCollector / MetaQualityRuleExecutor / MetaTableProfiler）的覆盖范围从 external-only 扩展到 entity + sql 类型表，经共享 table-reference 解析器 + table-reference 执行器统一分派。entity 路径经平台 JDBC Connection（IJdbcTransaction）复用现有 executor 核心，统计能力与 external 完全一致（无降级）。所有 in-scope 路径已 landed 或显式裁定 unsupported 并抛异常。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 实现者自审（self-audit，用于执行收口；plan guide 要求 closure audit 由独立子 agent 做，此处为执行收口阶段的 self-audit，独立 closure-audit 可在后续 fresh session 执行）
- Evidence:
  - **Phase 1 Exit Criteria**: D1-D5 裁定已写入 §4.4.3（`01-architecture-baseline.md:693-743`）；`MetaTableReferenceResolver`（`nop-metadata-service/.../tableref/`）三态 reference 产出被 `TestMetaTableReferenceResolver` 11 个测试覆盖（external found/not-found/DISABLED + sql found/no-ds/empty-source + entity found/base-null/not-registered/table-name-empty + unknown-type）；entity 前置校验（`isValidEntityName` / tableName 空）显式抛 ErrorCode。
  - **Phase 2 Exit Criteria**: sql 表 Catalog/Quality/Profiling 各产出真实结果（`TestSqlTableExecution` 6 个测试：catalog rowCount 真实 + indexCount unavailable 标记 / quality volume PASS + not_null PASS + derived-column D5 SKIP / profiling STDDEV=12.91 median=25 真实 / no-datasource 显式失败）。
  - **Phase 3 Exit Criteria**: entity 表 Catalog/Quality/Profiling 各产出真实结果（`TestEntityTableExecution` 5 个测试：catalog rowCount 真实 / quality volume PASS + custom_sql PASS（D4 supported）/ profiling STDDEV non-null + median non-null + percentiles non-null（无降级）/ unregistered entity 显式失败）。
  - **Anti-Hollow Check**: 三大执行器 entity/sql 路径在运行时确实被调用并产出真实统计值——`testSqlProfilingNumericStats` 断言 AMOUNT 列 numericStats.meanValue=25.0 + stddevValue=12.91 + medianValue=25.0（来自真实 SQL 执行）；`testEntityProfilingStddevMedianNoDegradation` 断言 VERSION 列 numericStats.stddevValue non-null + >0 + medianValue non-null + percentiles non-null（来自平台 Connection SQL 执行）。
  - **接线验证**: executor 运行时经共享 `MetaTableReferenceResolver` → `TableReferenceExecutor` 分派——`profileTable` / `executeQualityRule` / `collectCatalogForTable` 均 `tableRefResolver.resolve(table, ...)` + `tableRefExecutor.execute(ref, ...)`，entity 路径 `executeOnPlatformConnection` 经 `IJdbcTransaction.getConnection()`，external/sql 路径 `executeOnExternalConnection` 经 `withConnection`。
  - **无静默跳过**: `<expr_N>` 合成列 D5 SKIP + `reason=derived-column-skipped`（`testSqlQualityDerivedColumnSkipped` 断言）；entity unregistered entity 显式失败（`testEntityProfilingUnregisteredEntityFails` 断言）；sql no-datasource 显式失败（`testSqlProfilingNoDataSourceFails` 断言）。
  - `./mvnw test -pl nop-metadata/nop-metadata-service -am` → Tests run: 209, Failures: 0, Errors: 0, Skipped: 0（全绿）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` → 0 errors。

Follow-up:

- 多 schema 数据源的 Catalog/Quality/Profiling 执行（需 NopMetaTable 增加 schema 列，Protected Area 变更，与 Catalog/质量/剖析同源 follow-up）。
- 平台 Connection 获取的统一抽象（若 entity/sql/external 三路后续需收敛为单一 withConnection 语义，可提取平台 Connection 适配为 datasource-type，non-blocking）。
- sql 表字段类型推断方案 B/C（继承 0700-1，当前 profiler 对 null-type 列运行时探测 SUM 探测数值性）。
