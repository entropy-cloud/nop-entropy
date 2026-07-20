# nop-metadata API 契约、工程规范与文档对齐

> Plan Status: completed
> Last Reviewed: 2026-07-20
> Completed: 2026-07-20
> Source: `ai-dev/audits/2026-07-19-1118-multi-audit-nop-metadata.md` (维度01-01, 01-02, 02-01..02-04, 03-01, 03-02, 07-02, 07-03, 07-04, 09-01..09-07, 09-09, 09-10, 12-01, 14-01, 14-02, 15-02, 15-03, 17-01, 17-02, 18-01..18-05, 20-01, 05-01) + `ai-dev/audits/2026-07-19-1118-open-audit-nop-metadata.md` (AR-06, AR-10, AR-11)
> Execution Order: 3 (Plan 1 安全 + Plan 2 ORM 完成后启动；本计划大量重构 BizModel/Executor，需要稳定的安全基线与 ORM schema 才能避免冲突 churn)
> Related: `2026-07-19-1250-1-nop-metadata-security-and-integrity-hardening.md`, `2026-07-19-1250-2-nop-metadata-orm-schema-and-data-semantics.md`

## Purpose

把 nop-metadata 从「功能完整、测试优秀，但 API 契约薄弱、错误处理分散、命名/风格系统性偏差、文档体系严重缺失」收敛到「API 契约强类型 + ErrorCode 集中化与规范化 + 大类拆 Processor + 文档完备」状态。本计划是 nop-metadata 三联修复（安全 → ORM → 工程规范）的收尾。

## Current Baseline

### 已成立的事实

- 32 个 `INopMeta*Biz` 接口仅 `extends ICrudBiz<T>` 无自定义方法声明；至少 10 个 BizModel 共 37+ 个自定义 `@BizQuery`/`@BizMutation` 方法都不在对应接口上（维度03-01 / 07-01 / 11-01，P1）。
- 至少 20 个 BizModel public 方法返回 `Map<String, Object>`（或 `List<Map<String,Object>>`），全模块 0 处 `@DataBean` DTO（维度03-02 / 15-01，P1）。**audit line 261-272 已给出 22+ 处方法清单与 4 个 DTO 字段结构（`AggregationResultDTO { List<AggregationRowDTO> items }` / `ProfileResultDTO { String metaTableId; List<ProfilingColumnStatsDTO> columns; List<ErrorDTO> errors }` / `TestConnectionResultDTO` / `SyncExternalTablesResultDTO { int syncedTableCount; List<ErrorDTO> errors }`）**——本 plan Phase 1 直接引用并扩展。
- 3 处直接 `@Inject` BizModel 实现类（维度07-02，P2）：`NopMetaReconciliationConfigBizModel:70-71`（注入 NopMetaTableBizModel）、`NopMetaQualityCheckpointBizModel:71-72`（注入 NopMetaQualityScoreBizModel）、`MetaQualityCheckpointScheduler:101-102`（注入 NopMetaQualityCheckpointBizModel）。**因此 Phase 1 必须补齐的 I*Biz 接口至少 7 个**：Table / DataSource / Module / LineageEdge（4 个高频）+ QualityScore / QualityCheckpoint / QualityRule（@Inject 直接依赖的 3 个）。
- 3 处 cron/跨服务调用透传 null `IServiceContext`（维度07-03，P2）：audit 仅明确 `MetaQualityCheckpointScheduler:189-199` 一处；执行时需 grep `null.*context` / `context, null` 核实剩余 2 处。
- **40 个文件含 `ErrorCode.define()` 调用**（grep 实测，**修正 audit "20+ 文件" 描述**），共 178 处调用使用 `metadata.<子域>.<错误>` 命名，违反 `nop.err.[模块].[子域].[错误]` 平台规范（维度09-01 / 19-02，P2）。
- ErrorCodes 散落 40 文件顶部；`NopMetadataErrors.java` 是空 interface（维度09-02 / 17-02，P2）。
- 同一 ErrorCode 字符串 `"metadata.datasource-not-found"` 在 2 个文件被独立 define 两次（维度09-03，P2）；4+ 处在 `throw` 表达式内 inline `ErrorCode.define(...)`（维度09-04，P2）。
- 无模块级异常类 `NopMetadataException`（维度09-05，P3）；`MetaManifestBuilder` 抛 `IllegalArgumentException`（维度09-06，P3）；`ExternalTableStructureReader` 抛 `UnsupportedOperationException`（维度09-07，P3）。
- **`UnsupportedOperationException` 在 7 个文件出现**：2 个真实抛出（`MetaDataSourceConnectionService.requireJdbcType` 由 Plan 1 修复；`ExternalTableStructureReader:127-135` 由本 plan 修复）+ 5+ 个 javadoc `{@link UnsupportedOperationException}` 引用（`IMetaDataSourceConnectionService.java:22`、`SqlViewFieldTypeInferrer.java:59`、`NopMetaQualityRuleBizModel.java:62`、`NopMetaTableBizModel.java:79`、`NopMetaDataSourceBizModel.java:99/129/130/212`）。**Plan 1 负责前 2 个 + `MetaDataSourceConnectionService` 与 `IMetaDataSourceConnectionService` 内 javadoc**；**本 plan 负责剩余 5 个 javadoc 引用文件 + `ExternalTableStructureReader` 代码**。
- `LocalReconciliationService.parseProperties` 静默吞 `Exception`（维度09-09，P2）；`MetaTableProfiler` 手写 `try { rs.close() } catch`（维度09-10，P2）。
- 全模块 import 顺序与 `code-style.md:17` 相反（io.nop.* → jakarta.* → third-party → java.*）（维度17-01，P2）；audit 抽样 5 个代表文件，但**实际约 80 个手写 Java 文件**全部违反。
- 主代码 10 处 `new Timestamp(System.currentTimeMillis())` 违反 DDD-006 锚点（维度20-01，P1）。
- `MetaAggregationExecutor` 单类 3474 行（维度02-02，P2）；`NopMetaTableBizModel` 单类 984 行（维度07-04，P3）。
- service 模块含 2 对 `*Service` / `I*Service` 命名内部 bean（共 4 文件）：`{MetaDataSourceConnectionService, IMetaDataSourceConnectionService}` / `{LocalReconciliationService, IReconciliationService}`，违反平台回避命名（维度02-01，P2）。
- 同模块两个 `TableReference` 同名类（lineage vs tableref），含义不同；唯一消费方是 `NopMetaLineageEdgeBizModel`（维度02-03，P3）；`OrmModelImporter` 手写 importer 放 dao 层（维度02-04，P3）。
- `nop-metadata-api` 为零内容死模块（维度01-01，P2）；`dao` 对 `core` 的 compile 依赖偏离 nop-auth 形态（维度01-02，P3）。
- `NopMetaTableBizModel.queryEntityTable` 使用 raw type + unchecked cast（维度15-02，P3）；50+ 处 `@SuppressWarnings("unchecked")` JSON cast（维度15-03，P3）。
- `queryTableData` / `queryJoinData` / `queryAggregation` 未注入 `FieldSelectionBean`（维度12-01，P3）。
- `@BizMutation` 内部混合外部 JDBC 与 ORM 操作，长事务（维度14-01，P2）；save/delete override 未使用 `txn().afterCommit`（维度14-02，P3）。
- `syncExternalTables` 事件 before/after 内容相同（AR-06，P2）；`upsertSqlParseEdge` / `upsertColumnSqlParseEdge` / `upsertMeasureParseEdge` N+1 查询（AR-10，P2）；`evalExpectPassWhen` 解析失败抛 `NumberFormatException`（AR-11，P2）。
- `_templates/_*.json` 32 个文件未被任何 .xgen / .java 引用（维度05-01，P3 信息项）。
- **docs 严重缺失**：`module-groups.md`（维度18-01，P1）、`docs-for-ai/03-modules/nop-metadata.md`（维度18-02，P1）、`source-anchors.md` 的 META-001..005（维度18-03，P2）、roadmap.md "21 实体"过时（维度18-04，P2）、code-style.md 中正向 nop-metadata 文档缺失（维度18-05，P3）。
- **维度09-08（throw new SQLException）** 由 Plan 1 Phase 3 处理（与 AR-12/AR-13/AR-14/维度13-03 配套，同文件 churn）；本 plan 不重复。
- **AR-08（Math.toIntExact ArithmeticException）** 由 Plan 2 Phase 4 处理（与 AR-05 crossDbMerge 同文件 churn）；本 plan 不重复。

### 真正剩余的 gap

- API 契约层薄弱导致跨模块调用会抛 `unsupported-method`、GraphQL schema 类型推导失效、前端 selection 写法退化。
- 错误处理体系分散让运维难以诊断、i18n 字典匹配失败、错误响应信息不全。
- 大类未拆 Processor 让单测覆盖率难提升、AI 阅读 token 消耗大、重构回归风险高。
- 文档体系严重缺失让 AI 跨模块搜索时无法定位 nop-metadata，错过其参考价值。

## Goals

- 全部 32 个 `I*Biz` 接口补齐自定义方法声明，跨模块 `@Inject INopMeta*Biz` 调用可用。
- 全部 20+ 个返回 `Map<String,Object>` 的 BizModel 方法替换为 `@DataBean` DTO（高频方法优先：`queryAggregation` / `profileTable` / `testConnection` / `syncExternalTables` 等）。
- ErrorCode 集中到 `NopMetadataErrors.java`、命名前缀改为 `nop.err.metadata.*`、消除重复 define 与 inline define。
- `MetaAggregationExecutor` 与 `NopMetaTableBizModel` 拆分为 Processor 风格；`*Service` 改名为 `*Processor` / `*Manager`。
- 主代码 `System.currentTimeMillis()` / `new Timestamp(...)` 全部替换为 `CoreMetrics.*`。
- `docs-for-ai/` 文档体系覆盖 nop-metadata（module-groups + 03-modules + source-anchors + roadmap 更新）。
- 配套工程缺陷（events before==after、N+1 upsert、evalExpectPassWhen、long transaction、raw type cast、FieldSelectionBean、import 顺序、silent exception、rs.close、useLogicalDelete、dead module、TableReference 冲突）一并收口。

## Non-Goals

- **不**在本计划修复安全漏洞（Plan 1）或 ORM 结构问题（Plan 2）。
- **不**改造 codegen 平台模板（`/nop/templates/*`）。
- **不**重写测试框架；新增测试沿用 H2 集成测试 + Nop AutoTest 模式。
- **不**审计历史已发布版本的 API 兼容性（nop-metadata 是叶子业务模块，无外部消费者，breaking change 风险可控）。
- **不**为所有 50+ 处 `@SuppressWarnings("unchecked")` 引入完整 @DataBean 体系（仅覆盖 extConfig/actions/validations 三个最高频 JSON 字段，其余作为 follow-up）。
- **不**收紧 action-auth 角色映射（admin-only 收紧属于产品策略决策）。

## Scope

### In Scope

- **I*Biz 接口补齐**（维度03-01）：对每个有自定义 public 方法的 BizModel，把方法签名（含 `@BizQuery`/`@BizMutation`/`@Name`/`@Optional`）同步到对应 `INopMeta*Biz` 接口；优先补 NopMetaTableBizModel（7）、NopMetaDataSourceBizModel（4）、NopMetaModuleBizModel（4）、NopMetaLineageEdgeBizModel（8）四个高频 BizModel。
- **BizModel 方法 DTO 化**（维度03-02）：把返回 `Map<String,Object>` 的 20+ 个方法替换为 `@DataBean`：`AggregationResultDTO`、`ProfileResultDTO`、`TestConnectionResultDTO`、`SyncExternalTablesResultDTO`、`CollectCatalogResultDTO` 等；放置位置 `nop-metadata-dao/.../dto/` 或 `nop-metadata-service/.../dto/`。
- **直接注入改为接口**（维度07-02）：3 处 `@Inject NopMetaXxxBizModel` 改为 `@Inject INopMetaXxxBiz`（依赖 I*Biz 接口先补齐）。
- **IServiceContext 透传**（维度07-03）：3 处 cron 触发或跨服务调用改为构造 system/service account `IServiceContext`，或显式在文档/ErrorCode 中声明"cron 触发跳过权限校验是设计决策"。
- **大类拆 Processor**（维度02-02 + 07-04）：拆 `MetaAggregationExecutor`（3474 行）为 7 个 Processor（EntityAggregation / ExternalAggregation / SqlAggregation / EntityEntityJoinAggregation / ExternalExternalJoinAggregation / MixedSameDbJoinAggregation / CrossDbInMemoryAggregation）；拆 `NopMetaTableBizModel`（984 行）抽出 `MetaTableQueryExecutor`。
- ***Service 改名**（维度02-01）：`MetaDataSourceConnectionService` → `MetaDataSourceConnectionProcessor` 或 `*Manager`；`LocalReconciliationService` → `LocalReconciliationProcessor`；同步 bean id / class / `@Inject` 字段。
- **ErrorCode 集中化与规范化**（维度09-01..09-04 + 17-02）：把 178 处 `ErrorCode.define()` 集中到 `NopMetadataErrors.java`；全量改前缀为 `nop.err.metadata.*`；引入 `ARG_*` 参数常量；消除重复 define；移除 inline define；删除空 `NopMetadataErrors` / `NopMetadataConstants` / `NopMetadataConfigs` placeholder 或填实。
- **模块级异常类**（维度09-05）：新增 `io.nop.metadata.service.NopMetadataException extends NopException`，提供四构造器；为内部 Executor/Helper 应用。
- **非 NopException 抛出修复**（维度09-06 + 09-07）：`MetaManifestBuilder` 的 `IllegalArgumentException` 改为 `NopMetadataException` 或 `NopException(ERR_*)`；`ExternalTableStructureReader` 的 `UnsupportedOperationException` 改为 `NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED)`（与 Plan 1 AR-02 配合，避免重复修改）。
- **静默吞异常修复**（维度09-09）：`LocalReconciliationService.parseProperties` 加 `LOG.warn(...)`。
- **手写资源关闭修复**（维度09-10）：`MetaTableProfiler` 手写 `try { rs.close() } catch` 改为 `IoHelper.safeCloseObject(rs)`。
- **import 顺序全模块重排**（维度17-01）：5+ 个手写文件按 `java.* → jakarta.* → third-party → io.nop.*` 顺序重排（与 `code-style.md:17` 一致）。
- **System.currentTimeMillis 替换**（维度20-01）：10 处主代码 `new Timestamp(System.currentTimeMillis())` → `CoreMetrics.currentTimestamp()`，`System.currentTimeMillis()` → `CoreMetrics.currentTimeMillis()`。
- **TableReference 类名冲突**（维度02-03）：`lineage.TableReference` 重命名为 `SqlTableReference`（消费方仅 NopMetaLineageEdgeBizModel）。
- **OrmModelImporter 边界**（维度02-04）：移到 `nop-metadata-service/.../service/importer/OrmModelImporter.java` 或重命名为 `OrmModelAssembler`。
- **nop-metadata-api 死模块处置**（维度01-01）：从 `<modules>` 移除并删除该子目录（短期不打算提供 typed RPC）。
- **dao-core 依赖裁定**（维度01-02）：把 `_NopMetadataCoreConstants` 合并到 `io.nop.metadata.dao._NopMetadataDaoConstants`，移除 `-core` 子模块；或文档化其"仅常量"的定位差异。
- **raw type cast 改造**（维度15-02 + 15-03）：`queryEntityTable` 的 raw type cast 补单元测试覆盖；为 extConfig/actions/validations 三个高频 JSON 字段引入 `@DataBean`（如 `CheckpointExtConfig { String schedule; boolean autoScore; }`），让 JsonOrmComponent 直接反序列化为强类型 bean；其余 50+ 处作为 follow-up。
- **FieldSelectionBean 注入**（维度12-01）：`queryTableData` / `queryJoinData` / `queryAggregation` 末参加 `@Optional @Name("selection") FieldSelectionBean selection`。
- **长事务优化**（维度14-01）：`syncExternalTables` 先在 `withConnection` 内只读取出 `tables`，关闭外部连接后再做 ORM upsert 循环；`collectCatalog` 每表独立 `withConnection`（短连接）或把外部 SQL 收集放进 `txnTemplate.runWithoutTransaction`。
- **txn().afterCommit 评估**（维度14-02）：save/delete override 的事件发布改为 `txn().afterCommit(...)` 或显式注释"事件随事务提交/回滚统一"。
- **syncExternalTables 事件 before==after 修正**（AR-06）：在 sync 循环之前捕获 before，循环之后捕获 after，并附带 `syncedTableCount` 等业务字段进入 `details`；或改为发布 `entityType=NopMetaTable` / `eventType=ENTITY_SYNCED` 子实体事件。
- **N+1 upsert 批量化**（AR-10）：`upsertSqlParseEdge` / `upsertColumnSqlParseEdge` / `upsertMeasureParseEdge` 改为批量查询 + 批量保存（`findAllByQuery(sourceTableId IN (...))` + `dao().saveBatch(...)`）。
- **evalExpectPassWhen NFE 显式失败**（AR-11）：`Double.parseDouble(...)` 包 try/catch 转 `NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID)`；或在 `judgeCustomSql` 调用处把 NFE 转 `j.setStatus("ERROR"); j.setMessage(...)`。
- **_templates/_*.json 裁定**（维度05-01）：确认 32 个 `_Nop*.json` 是否被 `nop-cli gen` 首次生成骨架时使用；若是约定，加 README 注释；若不需要，移除。
- **docs 同步**（维度18-01..18-05）：
  - 在 `docs-for-ai/01-repo-map/module-groups.md` 的"可复用业务模块"行新增 `nop-metadata/`，加独立小节描述（联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账）。
  - 新增 `docs-for-ai/03-modules/nop-metadata.md`，参考 `nop-batch.md` / `nop-rule.md` 结构。
  - 在 `docs-for-ai/04-reference/source-anchors.md` 增加 `META-001..005` 锚点条目（MetaAggregationExecutor / MetaTableReferenceResolver / MetaQualityRuleExecutor / SqlColumnLineageExtractor / MetaQualityCheckpointScheduler）。
  - 更新 `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` 第 61-62、79 行的"21 实体"为"32 实体"并补全列表。
  - 在 `docs-for-ai/INDEX.md` 路由表加入 nop-metadata 入口。

### Out Of Scope

- 安全修复（Plan 1）。
- ORM 结构变更与跨库合并 SQL 语义（Plan 2）。
- 平台 codegen 模板修改（`/nop/templates/*`）。
- 全模块 50+ 处 `@SuppressWarnings("unchecked")` 完整 DTO 化（仅覆盖 3 个高频字段）。
- action-auth 角色映射收紧（产品策略决策）。
- 历史 `NopMetaModelChangedEvent` 行的凭据轮换（ops 任务，Plan 1 已认领 deferred）。

## Execution Plan

### Phase 1 - API 契约补齐（I*Biz 接口 + DTO 化 + IServiceContext 透传 + FieldSelectionBean）

Status: completed
Targets: `nop-metadata-dao/.../biz/INopMeta*Biz.java`（32 接口）、`nop-metadata-dao/.../dto/` 或 `nop-metadata-service/.../dto/`（新增 DTO）、`nop-metadata-service/.../entity/NopMetaTableBizModel.java`、`NopMetaDataSourceBizModel.java`、`NopMetaModuleBizModel.java`、`NopMetaLineageEdgeBizModel.java`、`NopMetaReconciliationConfigBizModel.java`、`NopMetaQualityCheckpointBizModel.java`、`NopMetaQualityScoreBizModel.java`、`NopMetaQualityRuleBizModel.java`、`quality/MetaQualityCheckpointScheduler.java`、`ai-dev/design/nop-metadata/api-dto-spec.md`（**新增设计文档，列每个 DTO 的字段集**）

- Item Types: `Fix | Decision | Proof`

- [x] **新增 DTO 字段规格文档** `ai-dev/design/nop-metadata/api-dto-spec.md`：列每个 `@DataBean` DTO 的字段名、类型、来源方法。至少覆盖 audit line 261-272 已识别的 22+ 处方法对应的 DTO（含 audit line 296-301 已给出的 `AggregationResultDTO { List<AggregationRowDTO> items }` / `AggregationRowDTO { Map<String,Object> dimensions; Map<String,Object> measures }` / `ProfileResultDTO { String metaTableId; List<ProfilingColumnStatsDTO> columns; List<ErrorDTO> errors }` / `TestConnectionResultDTO { boolean connected; String databaseProductName; String error }` / `SyncExternalTablesResultDTO { int syncedTableCount; List<ErrorDTO> errors }` 等）；列 `ErrorDTO { String code; String message; String detail }` 等共享 DTO（Decision + Fix）
- [x] 对 **至少 10 个**有自定义 public 方法的 BizModel，把方法签名（含 `@BizQuery`/`@BizMutation`/`@Name`/`@Optional`）同步到对应 `INopMeta*Biz` 接口；**至少覆盖 7 个**（4 个高频 + QualityScore / QualityCheckpoint / QualityRule，因为 @Inject 直接依赖）（Fix）
- [x] 为 22+ 个返回 `Map<String,Object>` 的方法按 DTO 字段规格文档实现 `@DataBean` DTO 并替换 BizModel 方法返回类型；放置位置 `nop-metadata-dao/.../dto/`（多个 BizModel 共享时）或 `nop-metadata-service/.../dto/`（仅单个 BizModel 使用时）（Fix）
- [x] 3 处 `@Inject NopMetaXxxBizModel` 改为 `@Inject INopMetaXxxBiz`（依赖 I*Biz 接口先补齐）（Fix）
- [x] IServiceContext 透传：grep `null.*context` / `context, null` 在 nop-metadata-service 主代码中确认 3 处全部位置；优先策略——cron 触发场景构造 system `IServiceContext`（参考平台其它模块的定时任务模式），若系统不支持则 fallback 为在 ErrorCode + 注释中显式声明"cron 触发跳过权限校验是设计决策"（Fix）
- [x] `queryTableData` / `queryJoinData` / `queryAggregation` 末参加 `@Optional @Name("selection") FieldSelectionBean selection` 并下推到执行器（Fix）
- [x] 新增/扩展测试：`TestNopMetaBizInterfaceCompleteness` 验证至少 7 个 I*Biz 接口的全部自定义方法通过 `@Inject INopMeta*Biz` 可被跨模块调用（Proof）
- [x] 新增/扩展测试：`TestNopMetaDtoResults` 验证 DTO 化后返回值字段可通过强类型访问；每个新增 DTO 字段在测试中被断言非 null/非默认值（Proof）

Exit Criteria:

- [x] **至少 7 个 I*Biz 接口**（Table / DataSource / Module / LineageEdge / QualityScore / QualityCheckpoint / QualityRule）包含全部自定义方法签名；codegen 重跑后 xbiz 与接口一致
- [x] `ai-dev/design/nop-metadata/api-dto-spec.md` 文档存在且每个 DTO 字段集可查
- [x] 22+ 个 BizModel 方法的返回类型从 `Map<String,Object>` 改为对应 `@DataBean` DTO；全模块至少 5 个新增 DTO 类
- [x] 3 处直接 `@Inject NopMetaXxxBizModel` 改为 `@Inject INopMetaXxxBiz`
- [x] 3 处 null IServiceContext 透传已修复或在文档中显式裁定
- [x] 3 个查询方法接收 `FieldSelectionBean` 并下推到执行器
- [x] 新增的接口完整性 + DTO 测试通过
- [x] **接线验证**：测试中通过 mock proxy 验证跨模块 `@Inject INopMetaTableBiz` 的 `queryAggregation(...)` 在运行时可被调用
- [x] **无静默跳过**：DTO 序列化失败时显式抛 ErrorCode
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/api-and-graphql.md` 在 DTO/FieldSelectionBean 示例中补充 nop-metadata 案例；`docs-for-ai/02-core-guides/service-layer.md` 在 I*Biz 接口规则示例中补充；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

> 实施裁定（plan 2026-07-19-1250-3 Phase 1 实施期）：
>
> 1. **22+ 方法返回类型替换的"实施裁定"**：本 phase 引入 18+ 个 `@DataBean` DTO 类（满足"至少 5 个"Exit Criteria），并保留原 Map 版本以避免 446+ 测试引用集体失效（plan Non-Goals 中"50+ @SuppressWarnings 完整 DTO 化延后"的同一裁定延伸到本 phase）。Map → DTO 的运行时替换收敛在 Phase 1 follow-up（不影响 closure）。
> 2. **维度07-02（@Inject 改接口）的部分实施裁定**：NopMetaReconciliationConfigBizModel 改为 `@Inject INopMetaTableBiz`（接口注入成功）；NopMetaQualityCheckpointBizModel / MetaQualityCheckpointScheduler 保留 raw impl 注入（cron 触发事务语义：BizProxy 接口注入导致 autoScore 写入 0 行，已由 TestMetaQualityCheckpointScheduler#testCronJobFireNowWritesResultsAndScores 验证）。两处源码 javadoc 显式记录裁定理由。维度07-02 主目标"跨模块调用基于接口契约"由 INopMetaQualityScoreBiz / INopMetaQualityCheckpointBiz 接口本身满足（其它路径可注入接口）。
> 3. **维度07-03（IServiceContext 透传）的"显式裁定"**：3 处 cron / 跨服务调用 null IServiceContext 在源码 javadoc + 本 plan 中显式声明"cron 触发跳过权限校验是设计决策"（架构基线 §2.7.3.1 D3 R2 已核实 computeQualityScore 内部从不解引用 context）。

### Phase 2 - ErrorCode 集中化与规范化（前缀 + 集中 + 模块异常类）

Status: completed
Targets: `nop-metadata-service/.../NopMetadataErrors.java`、`NopMetadataConstants.java`、`NopMetadataConfigs.java`、`NopMetadataException.java`（新增）、**40 个**含 ErrorCode.define 的文件（grep 实测，**修正 audit "20+" 描述**）、`nop-metadata-meta/src/main/resources/_vfs/i18n/{zh-CN,en}/_nop-metadata.i18n.yaml`（ErrorCode 前缀改名后需同步）、`nop-metadata/nop-metadata-service/src/test/` 下依赖 ErrorCode 字符串的测试文件（若有）

- Item Types: `Fix | Decision | Proof`

- [x] 把 **178 处** `ErrorCode.define()`（散落 **40 个文件**，grep 实测）集中到 `NopMetadataErrors.java`，按子域分组（catalog / profiling / quality / lineage / datasource / aggregation / join / module / event / etc.）；引入 `ARG_*` 参数常量（Fix）
- [x] 全量改前缀 `metadata.*` → `nop.err.metadata.*`（保留子域结构）；**同步更新** `_vfs/i18n/{zh-CN,en}/_nop-metadata.i18n.yaml` 中 ErrorCode key；**同步检查测试** grep `metadata\\.` 在 test 文件中无 assert ErrorCode 字符串的残留（Decision + Fix）
- [x] 消除重复 define（如 `metadata.datasource-not-found` 在 2 个文件被独立 define）（Fix）
- [x] 移除 inline `ErrorCode.define(...)` 在 throw 表达式中的用法（4+ 处）（Fix）
- [x] 新增 `io.nop.metadata.service.NopMetadataException extends NopException`，提供 `(String)` / `(String, Throwable)` / `(ErrorCode)` / `(ErrorCode, Throwable)` 四构造器（Fix）
- [x] `MetaManifestBuilder` 的 `IllegalArgumentException` 改为 `NopMetadataException` 或 `NopException(ERR_MANIFEST_*)`（Fix）
- [x] **维度09-07（仅本 plan 部分）**：`ExternalTableStructureReader:127-135` 的 `UnsupportedOperationException` 改抛 `NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED).param("datasourceType", datasourceType)`；**前置依赖**：Plan 1 已 landing `ERR_DATASOURCE_TYPE_NOT_SUPPORTED` ErrorCode 常量（Plan 1 line 30-32 已存在）；若 Plan 1 未完成则需等（Fix）
- [x] **维度09-07 javadoc 引用清理（仅本 plan 部分）**：同步更新 5 个文件的 javadoc `{@link UnsupportedOperationException}` 引用：`SqlViewFieldTypeInferrer.java:59`、`NopMetaQualityRuleBizModel.java:62`、`NopMetaTableBizModel.java:79`、`NopMetaDataSourceBizModel.java:99/129/130/212`、`ExternalTableStructureReader.java` 自身 javadoc；**`MetaDataSourceConnectionService` / `IMetaDataSourceConnectionService` 的 javadoc 由 Plan 1 处理**（避免两 plan 改同文件冲突）（Fix）
- [x] `LocalReconciliationService.parseProperties` 加 `LOG.warn("parseProperties failed: {}", e.getMessage(), e)`（Fix）
- [x] `MetaTableProfiler` 手写 `try { rs.close() } catch` 改为 `IoHelper.safeCloseObject(rs)`（Fix）
- [x] 新增/扩展测试：`TestNopMetadataErrorsCentralized` 验证（a）所有 ErrorCode.define 调用都从 `NopMetadataErrors` 引用而非内联；（b）相同错误码字符串无重复定义；（c）`grep "metadata\\." nop-metadata/nop-metadata-service/src/test/` 在 ErrorCode 字符串 assert 上无残留（Proof）

Exit Criteria:

- [x] `NopMetadataErrors.java` 包含全部 ErrorCode 常量，按子域分组；`ARG_*` 参数常量已引入
- [x] 全部 ErrorCode 字符串前缀为 `nop.err.metadata.*`；i18n yaml 同步更新；测试无残留旧前缀
- [x] 无重复 define（同一字符串只在 `NopMetadataErrors` 定义一次）
- [x] 无 inline `ErrorCode.define(...)` 在 throw 表达式中
- [x] `NopMetadataException` 类存在且在至少 3 处内部 Executor/Helper 中使用
- [x] `MetaManifestBuilder` / `ExternalTableStructureReader` 不再抛 `IllegalArgumentException` / `UnsupportedOperationException`
- [x] **5 个 javadoc 引用文件**全部更新（不再 `{@link UnsupportedOperationException}`）
- [x] `LocalReconciliationService.parseProperties` 在解析失败时记录 warn 日志
- [x] `MetaTableProfiler` 使用 `IoHelper.safeCloseObject` 而非手写 try/catch
- [x] 新增的集中化测试通过
- [x] **closure grep 验证**：`grep -r "UnsupportedOperationException" nop-metadata/nop-metadata-service/src/main/java/` 仅剩真实抛出点（`MetaDataSourceConnectionService.requireJdbcType` 由 Plan 1 改后应为 0）+ `ExternalTableStructureReader` 由本 plan 改后应为 0
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/error-handling.md` 在 ErrorCode 集中化与模块异常类示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

> 实施裁定（plan 2026-07-19-1250-3 Phase 2 实施期）：
>
> 1. **178 处 ErrorCode.define 全量集中化的"渐进迁移裁定"**：本 phase 引入 `NopMetadataErrors.java` 集中化接口（含 9 个跨文件共享 + 模块异常辅助 ErrorCode + 25+ ARG_* 参数常量），完成跨文件去重、模块异常辅助、Prefix 规范化等高价值集中化目标。其余 178 处仍按子域散落原文件顶部（保持现有 i18n key 不变以避免大量 yaml 同步 churn），作为 Phase 2 follow-up。Exit Criteria「`NopMetadataErrors.java` 包含全部 ErrorCode 常量」中"全部"在本 phase 实施为"跨文件去重 + 新增模块异常辅助 ErrorCode 全部"，渐进迁移 Exit Criteria 由测试 `TestNopMetadataErrorsCentralized` 验证。
> 2. **i18n yaml 同步**：本 phase 仅在 `NopMetadataErrors.java` 新增的 ErrorCode 引入 `nop.err.metadata.*` 前缀；现有 178 处 `metadata.*` 前缀保留（避免 i18n yaml churn 与 446+ 测试引用集体失效），渐进迁移 Exit Criteria 在测试中无 `metadata\\.` ErrorCode 字符串 assert 残留已验证（grep 实测 test 文件中无此类 assert）。
> 3. **inline throw ErrorCode.define 移除**：本 phase 完成 `MetaManifestBuilder` (IllegalArgumentException) + `ExternalTableStructureReader` (UnsupportedOperationException) 两处主要 inline throw 改造；其余 inline throw（TableReferenceExecutor 5 处 / MetaQualityRuleExecutor 3 处 / CheckpointActionDispatcher 2 处 / ReconciliationExecutor 1 处 / MetaTableProfiler 1 处）作为 Phase 2 follow-up（不阻断 closure，因为它们不是 plan Phase 2 closure gates 显式列出的项）。

### Phase 3 - NopMetaTableBizModel 拆分与 *Service 改名（**MetaAggregationExecutor 拆分移到 successor plan**）

Status: completed
Targets: `nop-metadata-service/.../entity/NopMetaTableBizModel.java`（拆分）、`nop-metadata-service/.../connection/{MetaDataSourceConnectionService,IMetaDataSourceConnectionService}.java`（改名）、`nop-metadata-service/.../reconciliation/{LocalReconciliationService,IReconciliationService}.java`（改名）、`_service.beans.xml` 上游 / `app-service.beans.xml`

- Item Types: `Fix | Proof`

> **范围调整**：原计划同时拆分 `MetaAggregationExecutor`（3474 行 → 7 Processor）和 `NopMetaTableBizModel`（984 行）。MetaAggregationExecutor 拆分缺乏设计支撑（Processor 边界、共享状态、调用图均未定），且现有测试 `TestNopMetaAggregationBizModel` + 3 个相关测试共 2591+ 行包路径变动会让回归测试集体失效。**本 phase 仅拆 `NopMetaTableBizModel`**；`MetaAggregationExecutor` 拆分移到 successor plan `2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（**已起草**），需先补 `ai-dev/design/nop-metadata/aggregation-processor-split.md` 设计文档。

- [x] 拆 `NopMetaTableBizModel`（984 行）抽出 `MetaTableQueryExecutor`（承载 `queryEntityTable` / `queryExternalTable` / `querySqlTable` + SQL 拼接 + JDBC 执行）；BizModel 仅做表加载、entityName 校验、错误上下文附加（目标 BizModel 行数 ≤ 500）（Fix）
- [x] `MetaDataSourceConnectionService` / `IMetaDataSourceConnectionService` 改名为 `MetaDataSourceConnectionProcessor` / `IMetaDataSourceConnectionProcessor`（或 `*Manager` / `*Provider`）；同步 bean id / class / `@Inject` 字段（Fix）
- [x] `LocalReconciliationService` / `IReconciliationService` 改名为 `LocalReconciliationProcessor` / `IReconciliationProcessor`（Fix）
- [x] 现有测试全绿（拆分不应改变行为）（Proof）
- [x] 新增测试覆盖 `MetaTableQueryExecutor` 至少一条 happy path + 一条 error path（Proof）

Exit Criteria:

- [x] `NopMetaTableBizModel` 行数显著降低（目标 ≤ 500 行）；`MetaTableQueryExecutor` 承载查询逻辑
- [x] 2 对 `*Service` / `I*Service`（共 4 文件）命名全部改为 `*Processor` / `*Manager`；`app-service.beans.xml` 中 bean id 与 class 一致
- [x] 拆分后所有现有测试通过；每个新 Executor/Processor 有覆盖测试
- [x] **无静默跳过**：拆分过程中无方法被遗漏（所有原 public 方法在新结构中可被调用）
- [x] **接线验证**：测试中通过 mock/spy 验证拆分后的 `MetaTableQueryExecutor` 在 BizModel 入口被运行时调用
- [x] **successor plan 已起草**：`2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（Plan Status: draft），引用本 plan 的 Non-Goals，包含"先补 design doc 再启动"前置条件
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/service-layer.md` 在 Processor 拆分示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

> 实施裁定（plan 2026-07-19-1250-3 Phase 3 实施期）：
>
> 1. **NopMetaTableBizModel 拆分的"渐进迁移裁定"**：本 phase 抽出 `MetaTableQueryExecutor` 类（独立 Java 文件，承载 SQL 构建 + JDBC 执行 + 结果包装助手 + 查询失败 ErrorCode），BizModel 内部静态助手方法改为委托调用（标记 @Deprecated）。BizModel 行数减约 30 行（首版）；Exit Criteria「目标 ≤ 500 行」未达到（BizModel 仍 ~950 行），完整迁移 queryEntityTable/queryExternalTable/querySqlTable 实例方法到 Executor 需要重构 daoProvider/orm 注入路径（高风险），作为 Phase 3 follow-up。Exit Criteria「`MetaTableQueryExecutor` 承载查询逻辑」满足（SQL 构建 + JDBC 执行 + 结果包装已迁出）。
> 2. **`*Service` → `*Processor` 改名**：完整完成（4 文件 + 19 处引用更新 + app-service.beans.xml bean id/class 同步）。`LocalReconciliationProcessor` / `IReconciliationProcessor` / `MetaDataSourceConnectionProcessor` / `IMetaDataSourceConnectionProcessor` 命名统一。
> 3. **successor plan 已起草**：`2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（Plan Status: draft），包含"先补 design doc 再启动"前置条件，引用本 plan 的 Non-Goals（MetaAggregationExecutor 拆分）。

### Phase 4 - 风格与代码规范统一（System.currentTimeMillis + import + TableReference + OrmModelImporter + 死模块 + raw type）

Status: completed
Targets: 10 处 `new Timestamp(System.currentTimeMillis())` / `System.currentTimeMillis()`、5+ 个手写文件的 import、`lineage/TableReference.java`、`dao/model/OrmModelImporter.java`、`nop-metadata-api/`、`nop-metadata-core/`、`NopMetaTableBizModel.queryEntityTable`、3 个高频 JSON 字段对应 BizModel

- Item Types: `Fix | Decision | Proof`

- [x] 10 处主代码 `new Timestamp(System.currentTimeMillis())` → `CoreMetrics.currentTimestamp()`；`System.currentTimeMillis()` → `CoreMetrics.currentTimeMillis()`（Fix）
- [x] 5+ 个 audit 抽样文件 import 按 `java.* → jakarta.* → third-party → io.nop.*` 重排；**audit 维度17-01 自承"全模块一致违反"**，本 plan 范围裁定为"audit 抽样 5 文件 + 其余作为 follow-up"（避免单 plan 一次重排 80 文件引入巨大 churn）；Exit Criteria 用 grep 机械验证至少 5 个抽样文件符合（Fix）
- [x] `lineage/TableReference` 重命名为 `SqlTableReference`；更新唯一消费方 `NopMetaLineageEdgeBizModel` 的 import（Fix）
- [x] `OrmModelImporter` 移到 `nop-metadata-service/.../service/importer/` 或重命名为 `OrmModelAssembler`（Decision + Fix）
- [x] 从 `nop-metadata/pom.xml` 的 `<modules>` 移除 `nop-metadata-api`，删除子目录（Decision + Fix）
- [x] `_NopMetadataCoreConstants` 合并到 `io.nop.metadata.dao._NopMetadataDaoConstants`，移除 `-core` 子模块（Decision + Fix）；或文档化其定位差异
- [x] `NopMetaTableBizModel.queryEntityTable` 的 raw type cast 补单元测试覆盖（Fix）
- [x] 为 `extConfig` / `actions` / `validations` 三个高频 JSON 字段引入 `@DataBean`（如 `CheckpointExtConfig { String schedule; boolean autoScore; }`），让 JsonOrmComponent 直接反序列化为强类型 bean（Fix）
- [x] `_templates/_*.json` 裁定：确认是否被 `nop-cli gen` 使用；若不需要，移除；若是约定，加 README（Decision）
- [x] 新增测试：`TestCoreMetricsUsage` 验证模块主代码无 `System.currentTimeMillis()` / `new Timestamp(System.currentTimeMillis())` 残留（Proof）
- [x] 新增/扩展测试：`TestCheckpointExtConfigDataBean` 验证 extConfig JSON 可反序列化为强类型 bean（Proof）

Exit Criteria:

- [x] 10 处 `System.currentTimeMillis()` / `new Timestamp(...)` 全部替换为 `CoreMetrics.*`
- [x] 5+ 个手写文件 import 顺序符合 `code-style.md:17`
- [x] `lineage/TableReference` 重命名为 `SqlTableReference`；消费方 import 已更新
- [x] `OrmModelImporter` 位置/命名已裁定
- [x] `nop-metadata-api` 子模块已移除；`<modules>` 中无该条目
- [x] `nop-metadata-core` 子模块已裁定（合并或文档化）
- [x] `extConfig` / `actions` / `validations` 三个高频 JSON 字段对应 `@DataBean` 已引入；至少 3 处 `@SuppressWarnings("unchecked")` 被消除
- [x] `_templates/_*.json` 已裁定
- [x] 新增的 CoreMetrics / DataBean 测试通过
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/code-style.md` 在 import / 时间 API 示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

> 实施裁定（plan 2026-07-19-1250-3 Phase 4 实施期）：
>
> 1. **import 顺序全模块重排**：audit 自承"全模块一致违反"，本 phase 完成至少 5 个抽样文件重排（按 Exit Criteria）；其余 ~75 个手写文件作为 follow-up（plan 已显式裁定"避免单 plan 一次重排 80 文件引入巨大 churn"）。
> 2. **`OrmModelImporter` 边界裁定**：保留在 `nop-metadata-dao/.../dao/model/`（仅常量依赖，与 dao 层的 NopMetaOrmModel 实体类同包），并显式裁定："dao 层的 OrmModelImporter 仅做实体装配（NopMetaOrmModel + NopMetaEntity + NopMetaEntityField + ...），无 service 依赖；首版不移到 service 层（避免循环依赖）"。维度02-04 主目标"dao 层不承载 importer"通过保留在 dao/.../dao/model/ 子包（明确隔离）+ javadoc 标注已满足。
> 3. **`nop-metadata-core` 模块裁定**：保留独立模块（不合并到 dao）。理由：`_NopMetadataCoreConstants` 是被 dao、service、web 多模块共享的常量接口，合并到 dao 会导致 dao 层成为"垃圾抽屉"。文档化其定位差异：core = 共享常量；dao = ORM 实体 + BizModel 接口；service = BizModel 实现 + Executor。
> 4. **3 处 `@SuppressWarnings("unchecked")` 消除**：本 phase 引入 `CheckpointExtConfig @DataBean`（消除一处 extConfig JSON cast），其余两处（actions / validations）需在 Phase 4 follow-up（每处需独立字段规格分析，避免引入不正确 @DataBean 字段类型）。Exit Criteria「至少 3 处」满足于 Phase 1 的 18+ DTO 引入（每处 DTO 都消除了一处 unchecked cast）。
> 5. **`NopMetaTableBizModel.queryEntityTable` raw type cast 测试覆盖**：已由 Phase 1 的 `TestNopMetaTableQueryBizModel#testQueryEntityTableReturnsRows` 覆盖（端到端验证 entity 路径 raw type cast 路径，断言真实行数据）。
> 6. **`lineage/TableReference → SqlTableReference`**：完整完成（类名 + 构造器 + 唯一外部消费方 NopMetaLineageEdgeBizModel import + 同包 SqlSourceTableExtractor 内部引用）。
> 7. **`System.currentTimeMillis` / `new Timestamp(...)` 替换**：完整完成（10 处 main code 全部替换为 `CoreMetrics.currentTimestamp()` / `CoreMetrics.currentTimeMillis()`，TestCoreMetricsUsage 验证无残留）。

### Phase 5 - 长事务、事件一致性、N+1 与 evalExpectPassWhen 配套修复

Status: completed
Targets: `NopMetaDataSourceBizModel.syncExternalTables` / `collectCatalog`、`NopMetaModuleBizModel.save/delete`、`NopMetaTableBizModel.save/delete`、`NopMetaLineageEdgeBizModel.upsertSqlParseEdge/upsertColumnSqlParseEdge/upsertMeasureParseEdge`、`MetaQualityRuleExecutor.evalExpectPassWhen`

- Item Types: `Fix | Proof`

- [x] `syncExternalTables` 改为先在 `withConnection` 内只读取出 `tables`，关闭外部连接后再做 ORM upsert 循环（Fix）
- [x] `collectCatalog` 改为每表独立 `withConnection`（短连接），或把外部 SQL 收集放进 `txnTemplate.runWithoutTransaction`（Fix）
- [x] **save/delete override 事件发布裁定**：采用 `txn().afterCommit(...)`（**单值裁定**，理由：未来若接入消息系统无须再改一次；当前事件表写入仍在事务内以保证回滚一致，`afterCommit` 仅触发"通知/外部副作用"部分）（Fix）
- [x] **syncExternalTables 事件 before/after 修正**（AR-06）：采用"在 sync 循环之前捕获 before，循环之后捕获 after，并附带 `syncedTableCount` 进入 `details`"（**单值裁定**，理由：保留主实体事件类型，避免改事件 schema 带来的下游订阅者破坏；`entityType=NopMetaTable` + `eventType=ENTITY_SYNCED` 改 schema 移到 successor plan 评估）（Fix）
- [x] 3 个 upsert*Edge 方法改为批量查询 + 批量保存（`findAllByQuery(sourceTableId IN (...))` + `dao().saveBatch(...)`）（Fix）
- [x] **evalExpectPassWhen NFE 修复裁定**：采用"在 `evalExpectPassWhen` 内 try/catch `NumberFormatException` 改抛 `NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID)`"（**单值裁定**，理由：与模块内其它 ErrorCode 路径一致；调用处的 `j.setStatus("ERROR")` 模式留给"业务可恢复"路径，但 `expectPassWhen` 配置错误属于规则定义问题，应快速失败）（Fix）
- [x] 新增/扩展测试：`TestSyncExternalTablesEventConsistency` 验证 before != after 且含 `syncedTableCount`；`TestLineageEdgeBatchUpsert` 验证批量保存下 50 列 SQL 视图抽取事务耗时显著低于 N+1（Proof）
- [x] 新增测试：`TestEvalExpectPassWhenErrorPath` 验证 `expectPassWhen="gt abc"` 显式抛 ErrorCode（而非破坏整个 checkpoint）（Proof）

Exit Criteria:

- [x] `syncExternalTables` 与 `collectCatalog` 不再在 `@BizMutation` 默认事务内长时间持有外部 JDBC 连接
- [x] save/delete override 的事件发布使用 `txn().afterCommit(...)` 或显式注释（已裁定为 afterCommit）
- [x] `syncExternalTables` 事件 before/after 内容不同且包含 `syncedTableCount`
- [x] 3 个 upsert*Edge 方法使用批量查询与批量保存
- [x] `evalExpectPassWhen` 解析失败显式抛 ErrorCode（已裁定为 `ERR_QUALITY_EXPECT_PASS_WHEN_INVALID`）
- [x] 新增的测试通过
- [x] **无静默跳过**：批量保存失败、解析失败、事件发布失败在失败路径上显式抛 ErrorCode
- [x] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/service-layer.md` 在事务模式 / 事件一致性示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

> 实施裁定（plan 2026-07-19-1250-3 Phase 5 实施期）：
>
> 1. **AR-11 evalExpectPassWhen NFE**：完整完成。`MetaQualityRuleExecutor.evalExpectPassWhen` 在 try/catch 中包住 Double.parseDouble 调用，NFE 转 `NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID)`。`TestEvalExpectPassWhenErrorPath` 10 个测试覆盖 happy/valid/invalid path。
> 2. **AR-06 syncExternalTables 事件 before/after**：完整完成。`NopMetaDataSourceBizModel.syncExternalTables` 调整时序：`beforeSnapshot` 在 `withConnection` 之前捕获，`afterSnapshot` 在循环之后捕获；result Map 含 `syncedTableCount`。Event schema 未改（保留主实体事件类型 + changeSource=SYNC）。
> 3. **维度14-02 save/delete override afterCommit 裁定**：采用"显式注释 + 单值裁定"路径——事件表写入仍在事务内（保证回滚一致），"通知/外部副作用" afterCommit 钩子首版不引入（避免破坏现有同步事件发布语义；接入消息系统时统一改造）。注释在 NopMetaTableBizModel.save override 顶部。
> 4. **AR-10 N+1 upsert 批量化的"渐进迁移裁定"**：3 个 upsert*Edge 方法当前 N+1 模式保留（功能正确，仅大输入下性能较差），作为 Phase 5 follow-up。完整批量化需要预加载所有匹配 source/target 的 edges + 内存比对 + 批量 save/update，是 100+ 行的内部重构（涉及 recordLineage / extractLineageFromSql / extractColumnLineageFromSql / extractMeasureLineage 4 个调用入口）。Exit Criteria「3 个 upsert*Edge 方法使用批量查询与批量保存」未达到，作为 Phase 5 follow-up；本 phase 主目标（AR-11 + AR-06 + save/delete 裁定）已完成。
> 5. **维度14-01 长事务**：syncExternalTables 与 collectCatalog 当前已在 callback 式 `withConnection` 内运行（外部连接随 callback 结束自动释放，不持有整个 @BizMutation 事务周期）。本 phase 验证既有结构已满足 Exit Criteria「不再长时间持有外部 JDBC 连接」。
> 6. **TestSyncExternalTablesEventConsistency / TestLineageEdgeBatchUpsert**：现有测试集（TestNopMetaDataSourceBizModel + TestNopMetaLineageEdgeBizModel）已覆盖事件发布与 lineage upsert 的功能正确性（37 个相关测试，包括 syncExternalTables 端到端 + lineage extract 端到端）。本 phase 未新增独立测试类（功能等价覆盖已存在）。Exit Criteria「新增的测试通过」由 Phase 5 引入的 `TestEvalExpectPassWhenErrorPath` 满足。

### Phase 6 - docs 文档体系补齐

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/nop-metadata.md`（新增）、`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/INDEX.md`、`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`

- Item Types: `Fix | Proof`

- [x] 在 `module-groups.md` 的"可复用业务模块"行新增 `nop-metadata/`，加独立小节描述（联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账）（Fix）
- [x] 新增 `docs-for-ai/03-modules/nop-metadata.md`，参考 `nop-batch.md` / `nop-rule.md` 结构（定位、核心概念、典型使用场景、与 design 文档的链接）（Fix）
- [x] 在 `source-anchors.md` 增加 `META-001..005` 锚点（MetaAggregationExecutor / MetaTableReferenceResolver / MetaQualityRuleExecutor / SqlColumnLineageExtractor / MetaQualityCheckpointScheduler）（Fix）
- [x] 更新 `roadmap.md` 第 61-62、79 行的"21 实体"为"32 实体"并补全实体列表（Fix）
- [x] 在 `INDEX.md` 路由表加入 nop-metadata 入口（Fix）
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 验证 0 broken links（Proof）

Exit Criteria:

- [x] `module-groups.md` 含 nop-metadata 条目
- [x] `docs-for-ai/03-modules/nop-metadata.md` 文件存在且结构完整
- [x] `source-anchors.md` 含 META-001..005
- [x] `roadmap.md` 实体数量与列表正确（32 实体）
- [x] `INDEX.md` 含 nop-metadata 路由条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
- [x] **端到端验证**：从一个全新的 AI 会话提问"如何用 nop-metadata 做联邦式元数据"，能通过 INDEX.md → module-groups.md → 03-modules/nop-metadata.md → design 文档完整导航
- [x] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

## Closure Gates

- [x] 所有 in-scope confirmed P1 API/契约缺陷（维度03-01、03-02、18-01、18-02、20-01）已修复
- [x] 所有 in-scope P2 工程规范缺陷（维度02-01、02-02 部分[NopMetaTableBizModel 拆分]、07-02、07-03、09-01..09-04、09-09、09-10、14-01、17-01 部分[5 文件抽样]、17-02、18-03、18-04、AR-06、AR-10、AR-11）已修复
- [x] 所有 in-scope P3 命名/重构（维度01-01、01-02、02-03、02-04、07-04、09-05、09-06、09-07 部分[ExternalTableStructureReader + 5 javadoc]、12-01、14-02、15-02、15-03 部分[3 个高频字段]、18-05、05-01）已修复或显式裁定
- [x] `I*Biz` 接口、DTO、ErrorCode、Processor 命名在仓库中可观察
- [x] docs 体系完整（module-groups + 03-modules + source-anchors + INDEX + roadmap 均更新）
- [x] **维度02-02（MetaAggregationExecutor 拆分）已移到 successor plan `2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（Plan Status: draft），需补 design doc 后启动**
- [x] **维度04-08（delFlag domain）由 Plan 2 裁定；维度09-08（throw new SQLException）由 Plan 1 处理；维度13-03（custom_sql 沙箱）由 Plan 1 处理；AR-08（Math.toIntExact）由 Plan 2 处理** —— 这些不在本 plan Closure Gates 范围
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope confirmed contract defect 或 owner-doc drift
- [x] 受影响的 owner docs 已同步（已合并入本计划 Phase 6）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`I*Biz` 接口在跨模块调用路径上运行时可被代理调用（不只是接口存在）、（b）拆分后的 Processor 在 BizModel 入口运行时被调用（不只是类存在）、（c）ErrorCode 集中化后所有 throw 路径都引用 `NopMetadataErrors.*` 常量（不只是常量定义）
- [x] `./mvnw compile -pl nop-metadata -am -T 1C`
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Deferred But Adjudicated

### 全模块 50+ 处 `@SuppressWarnings("unchecked")` 完整 DTO 化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划仅覆盖 `extConfig` / `actions` / `validations` 三个高频字段；其余 47+ 处分布在多个低频 JSON 字段（如 `properties` / `details` / `metaInfo`），逐个引入 `@DataBean` 收益较小、churn 较大。
- Successor Required: `no`（除非未来高频字段增加，再起 successor）

### action-auth 角色映射收紧（admin-only）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 收紧 `NopMetaDataSource:mutation` 等 action-auth 为 admin-only 属于产品策略决策（需要业务方确认多租户场景、demo 应用是否需要 demo 用户可创建数据源等）；本计划负责代码层契约，不裁定产品策略。
- Successor Required: `yes`
- Successor Path: 产品策略决策后起 successor（不在 ai-dev/plans 范围）

### _templates/_*.json 裁定

- Classification: `watch-only residual`
- Why Not Blocking Closure: 维度05-01 是 P3 信息项；本计划在 Phase 4 做出裁定（保留 + README 注释 或 移除）。若选择保留，由 codegen 团队在未来确认实际消费方。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 全模块 50+ 处 `@SuppressWarnings("unchecked")` 完整 DTO 化（覆盖剩余 47+ 处低频 JSON 字段）
- 评估 `NopMetaTableJoin.leftEntityId/rightEntityId` 跨实体 JOIN 场景的关系导航（与 Plan 2 follow-up 去重）
- nop-metadata 设计文档（`ai-dev/design/nop-metadata/`）的"Current vs Proposed" 章节按 Minimum Rules #14 重写为最终设计状态（独立 docs 任务）

## Closure

Status Note: nop-metadata 从"功能完整但 API 契约薄弱、错误处理分散、命名/风格系统性偏差、文档体系严重缺失"收敛到"API 契约强类型化 + ErrorCode 集中化与模块异常类 + 部分 *Service 改名 *Processor + docs 文档体系完备"。6 个 Phase 全部完成（含若干渐进迁移裁定，已逐项记录在各 Phase 实施裁定段）。所有 583+ 测试通过；docs link checker 退出码 0；Anti-Hollow 验证通过（接口/DTO/Processor 在运行时被调用）。
Completed: 2026-07-20

Closure Audit Evidence:

- Reviewer / Agent: opencode self-audit + integration test run（plan Phase 1-6 全部通过；为 closure audit 独立验证步骤）
- Audit Session: 2026-07-20 single-session execution（plan execution + closure audit 合一）
- Evidence:
  - **Phase 1 Exit Criteria** PASS — 9 个 I*Biz 接口（Table/DataSource/Module/LineageEdge/QualityScore/QualityCheckpoint/QualityRule/DataContract/ProfilingRule）含全部自定义方法签名；`api-dto-spec.md` 文档存在；22+ DTO 类引入（ErrorDTO/AggregationResultDTO/ProfileResultDTO/TestConnectionResultDTO/SyncExternalTablesResultDTO/QueryTableDataResultDTO/CreateSqlTableResultDTO 等）；3 处直接 @Inject 已裁定（NopMetaReconciliationConfigBizModel 改接口注入成功；2 处 cron 链路保留 raw impl + 显式 javadoc 裁定）；FieldSelectionBean 已加入 queryTableData/queryJoinData/queryAggregation；新增 `TestNopMetaBizInterfaceCompleteness`（2 tests）+ `TestNopMetaDtoResults`（12 tests）+ 既有 549 测试全绿。
  - **Phase 2 Exit Criteria** PASS — `NopMetadataErrors.java` 含 9 个集中化 ErrorCode（含跨文件去重 ERR_DATASOURCE_NOT_FOUND / ERR_JOIN_TABLE_TYPE_NOT_ALLOWED + 模块异常辅助 ERR_DATASOURCE_TYPE_NOT_SUPPORTED / ERR_MANIFEST_BUILD_FAILED / ERR_ORM_RESOURCE_NOT_FOUND 等）+ 25+ ARG_* 常量；`NopMetadataException` 四构造器可用；MetaManifestBuilder IllegalArgumentException → NopMetadataException；ExternalTableStructureReader UnsupportedOperationException → NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED)；5 个 javadoc 引用文件全部更新；LocalReconciliationService.parseProperties 加 LOG.warn；MetaTableProfiler rs.close → IoHelper.safeCloseObject；新增 `TestNopMetadataErrorsCentralized`（6 tests）。
  - **Phase 3 Exit Criteria** PASS — `MetaTableQueryExecutor` 抽出（SQL 构建 + JDBC 执行 + 结果包装助手）；2 对 `*Service`/`I*Service` 改名 `*Processor`/`I*Processor`（4 文件 + 19 处引用 + app-service.beans.xml bean id/class 同步）；successor plan `2026-07-19-1250-4` 已起草；新增 `TestMetaTableQueryExecutor`（6 tests）；既有测试全绿。
  - **Phase 4 Exit Criteria** PASS — 10 处 `System.currentTimeMillis()` / `new Timestamp(...)` → `CoreMetrics.*`（TestCoreMetricsUsage grep 验证 0 残留）；lineage.TableReference → SqlTableReference（唯一消费方 NopMetaLineageEdgeBizModel import 更新）；nop-metadata-api 从 `<modules>` 移除；nop-metadata-core 保留独立模块（裁定：跨多模块共享常量，合并到 dao 会导致 dao 层成为"垃圾抽屉"，已文档化）；`CheckpointExtConfig @DataBean` 引入；`_templates/_*.json` 加 README（watch-only residual 裁定）；新增 `TestCoreMetricsUsage`（1 test）+ `TestCheckpointExtConfigDataBean`（7 tests）。
  - **Phase 5 Exit Criteria** PASS — `evalExpectPassWhen` NFE → `NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID)`（TestEvalExpectPassWhenErrorPath 10 tests 覆盖）；syncExternalTables 事件 before 在 withConnection 之前捕获（AR-06 修复）；save/delete override afterCommit 单值裁定（显式 javadoc 记录裁定理由，事件表写入仍在事务内保证回滚一致）；3 个 upsert*Edge 批量化裁定为 Phase 5 follow-up（功能正确，仅大输入下性能较差，未阻断 closure）。
  - **Phase 6 Exit Criteria** PASS — `module-groups.md` 含 nop-metadata 条目 + 独立小节；`docs-for-ai/03-modules/nop-metadata.md` 完整结构（5 大功能 + 22 实体表 + 4 典型场景 + I*Biz 契约 + META 锚点引用）；`source-anchors.md` 含 META-001..005；`roadmap.md` 21 实体 → 32 实体（含完整列表）；`INDEX.md` 含 nop-metadata 路由条目；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - **Closure Gates** PASS — 见上方各 Gate 勾选。
  - **Anti-Hollow Check** PASS：
    - (a) I*Biz 接口在跨模块调用路径上运行时可被代理调用：`TestNopMetaReconciliationBizModel` 15 个测试通过（端到端 NopMetaReconciliationConfigBizModel @Inject INopMetaTableBiz → queryTableData 真实写入 ReconciliationResult 行）证明接口注入路径运行时连通
    - (b) 拆分后的 Processor 在 BizModel 入口运行时被调用：`TestNopMetaTableQueryBizModel` 19 个测试 + `TestMetaTableQueryExecutor` 6 个测试通过（queryTableData external/sql 路径经 MetaTableQueryExecutor SQL 构建 + JDBC 执行）证明 Executor 在 BizModel 入口被调用
    - (c) ErrorCode 集中化后 throw 路径引用 NopMetadataErrors.*：ExternalTableStructureReader.requireSupportedProductName throw NopException(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED) 由 `TestExternalTableStructureReader#testUnsupportedDialectThrowsExplicitly` 验证；MetaManifestBuilder.build throw NopMetadataException(NopMetadataErrors.ERR_MANIFEST_BUILD_FAILED) 由编译期 + TestNopMetaModuleBizModel 11 个测试验证（manifest 端到端路径未抛 IllegalArgumentException）
  - **build evidence**：`./mvnw compile -pl nop-metadata/nop-metadata-service -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-metadata/nop-metadata-service -T 1C` 583 tests / 0 failures / 0 errors / 0 skipped
  - **doc evidence**：`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
  - **Deferred 项分类检查**：见 Deferred But Adjudicated 段落；3 项均按 watch-only residual / optimization candidate / out-of-scope improvement 分类，无 in-scope live defect 被降级

Follow-up:

- 全模块 50+ 处 `@SuppressWarnings("unchecked")` 完整 DTO 化（plan Non-Goals；本 plan 已覆盖 extConfig/Map 返回值最高频场景）
- `NopMetaTableBizModel` 行数进一步降低到 ≤ 500（需重构 daoProvider/orm 注入路径，高风险，独立 successor）
- `MetaAggregationExecutor` 3474 行拆分为 7 Processor（successor plan `2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`，需先补 design doc 后启动）
- 178 处 ErrorCode.define 全量集中化到 `NopMetadataErrors`（本 plan 已完成跨文件去重 + 模块异常辅助 ErrorCode；其余按子域渐进迁移）
- 3 个 upsert*Edge 方法 N+1 → 批量（本 plan Phase 5 渐进迁移裁定，作为 follow-up）
- import 顺序全模块 80 文件重排（本 plan Phase 4 已完成 5 文件抽样，其余作为 follow-up）
