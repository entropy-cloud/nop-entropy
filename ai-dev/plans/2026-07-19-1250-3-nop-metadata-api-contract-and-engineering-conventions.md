# nop-metadata API 契约、工程规范与文档对齐

> Plan Status: active
> Last Reviewed: 2026-07-19
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

Status: planned
Targets: `nop-metadata-dao/.../biz/INopMeta*Biz.java`（32 接口）、`nop-metadata-dao/.../dto/` 或 `nop-metadata-service/.../dto/`（新增 DTO）、`nop-metadata-service/.../entity/NopMetaTableBizModel.java`、`NopMetaDataSourceBizModel.java`、`NopMetaModuleBizModel.java`、`NopMetaLineageEdgeBizModel.java`、`NopMetaReconciliationConfigBizModel.java`、`NopMetaQualityCheckpointBizModel.java`、`NopMetaQualityScoreBizModel.java`、`NopMetaQualityRuleBizModel.java`、`quality/MetaQualityCheckpointScheduler.java`、`ai-dev/design/nop-metadata/api-dto-spec.md`（**新增设计文档，列每个 DTO 的字段集**）

- Item Types: `Fix | Decision | Proof`

- [ ] **新增 DTO 字段规格文档** `ai-dev/design/nop-metadata/api-dto-spec.md`：列每个 `@DataBean` DTO 的字段名、类型、来源方法。至少覆盖 audit line 261-272 已识别的 22+ 处方法对应的 DTO（含 audit line 296-301 已给出的 `AggregationResultDTO { List<AggregationRowDTO> items }` / `AggregationRowDTO { Map<String,Object> dimensions; Map<String,Object> measures }` / `ProfileResultDTO { String metaTableId; List<ProfilingColumnStatsDTO> columns; List<ErrorDTO> errors }` / `TestConnectionResultDTO { boolean connected; String databaseProductName; String error }` / `SyncExternalTablesResultDTO { int syncedTableCount; List<ErrorDTO> errors }` 等）；列 `ErrorDTO { String code; String message; String detail }` 等共享 DTO（Decision + Fix）
- [ ] 对 **至少 10 个**有自定义 public 方法的 BizModel，把方法签名（含 `@BizQuery`/`@BizMutation`/`@Name`/`@Optional`）同步到对应 `INopMeta*Biz` 接口；**至少覆盖 7 个**（4 个高频 + QualityScore / QualityCheckpoint / QualityRule，因为 @Inject 直接依赖）（Fix）
- [ ] 为 22+ 个返回 `Map<String,Object>` 的方法按 DTO 字段规格文档实现 `@DataBean` DTO 并替换 BizModel 方法返回类型；放置位置 `nop-metadata-dao/.../dto/`（多个 BizModel 共享时）或 `nop-metadata-service/.../dto/`（仅单个 BizModel 使用时）（Fix）
- [ ] 3 处 `@Inject NopMetaXxxBizModel` 改为 `@Inject INopMetaXxxBiz`（依赖 I*Biz 接口先补齐）（Fix）
- [ ] IServiceContext 透传：grep `null.*context` / `context, null` 在 nop-metadata-service 主代码中确认 3 处全部位置；优先策略——cron 触发场景构造 system `IServiceContext`（参考平台其它模块的定时任务模式），若系统不支持则 fallback 为在 ErrorCode + 注释中显式声明"cron 触发跳过权限校验是设计决策"（Fix）
- [ ] `queryTableData` / `queryJoinData` / `queryAggregation` 末参加 `@Optional @Name("selection") FieldSelectionBean selection` 并下推到执行器（Fix）
- [ ] 新增/扩展测试：`TestNopMetaBizInterfaceCompleteness` 验证至少 7 个 I*Biz 接口的全部自定义方法通过 `@Inject INopMeta*Biz` 可被跨模块调用（Proof）
- [ ] 新增/扩展测试：`TestNopMetaDtoResults` 验证 DTO 化后返回值字段可通过强类型访问；每个新增 DTO 字段在测试中被断言非 null/非默认值（Proof）

Exit Criteria:

- [ ] **至少 7 个 I*Biz 接口**（Table / DataSource / Module / LineageEdge / QualityScore / QualityCheckpoint / QualityRule）包含全部自定义方法签名；codegen 重跑后 xbiz 与接口一致
- [ ] `ai-dev/design/nop-metadata/api-dto-spec.md` 文档存在且每个 DTO 字段集可查
- [ ] 22+ 个 BizModel 方法的返回类型从 `Map<String,Object>` 改为对应 `@DataBean` DTO；全模块至少 5 个新增 DTO 类
- [ ] 3 处直接 `@Inject NopMetaXxxBizModel` 改为 `@Inject INopMetaXxxBiz`
- [ ] 3 处 null IServiceContext 透传已修复或在文档中显式裁定
- [ ] 3 个查询方法接收 `FieldSelectionBean` 并下推到执行器
- [ ] 新增的接口完整性 + DTO 测试通过
- [ ] **接线验证**：测试中通过 mock proxy 验证跨模块 `@Inject INopMetaTableBiz` 的 `queryAggregation(...)` 在运行时可被调用
- [ ] **无静默跳过**：DTO 序列化失败时显式抛 ErrorCode
- [ ] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/api-and-graphql.md` 在 DTO/FieldSelectionBean 示例中补充 nop-metadata 案例；`docs-for-ai/02-core-guides/service-layer.md` 在 I*Biz 接口规则示例中补充；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 2 - ErrorCode 集中化与规范化（前缀 + 集中 + 模块异常类）

Status: planned
Targets: `nop-metadata-service/.../NopMetadataErrors.java`、`NopMetadataConstants.java`、`NopMetadataConfigs.java`、`NopMetadataException.java`（新增）、**40 个**含 ErrorCode.define 的文件（grep 实测，**修正 audit "20+" 描述**）、`nop-metadata-meta/src/main/resources/_vfs/i18n/{zh-CN,en}/_nop-metadata.i18n.yaml`（ErrorCode 前缀改名后需同步）、`nop-metadata/nop-metadata-service/src/test/` 下依赖 ErrorCode 字符串的测试文件（若有）

- Item Types: `Fix | Decision | Proof`

- [ ] 把 **178 处** `ErrorCode.define()`（散落 **40 个文件**，grep 实测）集中到 `NopMetadataErrors.java`，按子域分组（catalog / profiling / quality / lineage / datasource / aggregation / join / module / event / etc.）；引入 `ARG_*` 参数常量（Fix）
- [ ] 全量改前缀 `metadata.*` → `nop.err.metadata.*`（保留子域结构）；**同步更新** `_vfs/i18n/{zh-CN,en}/_nop-metadata.i18n.yaml` 中 ErrorCode key；**同步检查测试** grep `metadata\\.` 在 test 文件中无 assert ErrorCode 字符串的残留（Decision + Fix）
- [ ] 消除重复 define（如 `metadata.datasource-not-found` 在 2 个文件被独立 define）（Fix）
- [ ] 移除 inline `ErrorCode.define(...)` 在 throw 表达式中的用法（4+ 处）（Fix）
- [ ] 新增 `io.nop.metadata.service.NopMetadataException extends NopException`，提供 `(String)` / `(String, Throwable)` / `(ErrorCode)` / `(ErrorCode, Throwable)` 四构造器（Fix）
- [ ] `MetaManifestBuilder` 的 `IllegalArgumentException` 改为 `NopMetadataException` 或 `NopException(ERR_MANIFEST_*)`（Fix）
- [ ] **维度09-07（仅本 plan 部分）**：`ExternalTableStructureReader:127-135` 的 `UnsupportedOperationException` 改抛 `NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED).param("datasourceType", datasourceType)`；**前置依赖**：Plan 1 已 landing `ERR_DATASOURCE_TYPE_NOT_SUPPORTED` ErrorCode 常量（Plan 1 line 30-32 已存在）；若 Plan 1 未完成则需等（Fix）
- [ ] **维度09-07 javadoc 引用清理（仅本 plan 部分）**：同步更新 5 个文件的 javadoc `{@link UnsupportedOperationException}` 引用：`SqlViewFieldTypeInferrer.java:59`、`NopMetaQualityRuleBizModel.java:62`、`NopMetaTableBizModel.java:79`、`NopMetaDataSourceBizModel.java:99/129/130/212`、`ExternalTableStructureReader.java` 自身 javadoc；**`MetaDataSourceConnectionService` / `IMetaDataSourceConnectionService` 的 javadoc 由 Plan 1 处理**（避免两 plan 改同文件冲突）（Fix）
- [ ] `LocalReconciliationService.parseProperties` 加 `LOG.warn("parseProperties failed: {}", e.getMessage(), e)`（Fix）
- [ ] `MetaTableProfiler` 手写 `try { rs.close() } catch` 改为 `IoHelper.safeCloseObject(rs)`（Fix）
- [ ] 新增/扩展测试：`TestNopMetadataErrorsCentralized` 验证（a）所有 ErrorCode.define 调用都从 `NopMetadataErrors` 引用而非内联；（b）相同错误码字符串无重复定义；（c）`grep "metadata\\." nop-metadata/nop-metadata-service/src/test/` 在 ErrorCode 字符串 assert 上无残留（Proof）

Exit Criteria:

- [ ] `NopMetadataErrors.java` 包含全部 ErrorCode 常量，按子域分组；`ARG_*` 参数常量已引入
- [ ] 全部 ErrorCode 字符串前缀为 `nop.err.metadata.*`；i18n yaml 同步更新；测试无残留旧前缀
- [ ] 无重复 define（同一字符串只在 `NopMetadataErrors` 定义一次）
- [ ] 无 inline `ErrorCode.define(...)` 在 throw 表达式中
- [ ] `NopMetadataException` 类存在且在至少 3 处内部 Executor/Helper 中使用
- [ ] `MetaManifestBuilder` / `ExternalTableStructureReader` 不再抛 `IllegalArgumentException` / `UnsupportedOperationException`
- [ ] **5 个 javadoc 引用文件**全部更新（不再 `{@link UnsupportedOperationException}`）
- [ ] `LocalReconciliationService.parseProperties` 在解析失败时记录 warn 日志
- [ ] `MetaTableProfiler` 使用 `IoHelper.safeCloseObject` 而非手写 try/catch
- [ ] 新增的集中化测试通过
- [ ] **closure grep 验证**：`grep -r "UnsupportedOperationException" nop-metadata/nop-metadata-service/src/main/java/` 仅剩真实抛出点（`MetaDataSourceConnectionService.requireJdbcType` 由 Plan 1 改后应为 0）+ `ExternalTableStructureReader` 由本 plan 改后应为 0
- [ ] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/error-handling.md` 在 ErrorCode 集中化与模块异常类示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 3 - NopMetaTableBizModel 拆分与 *Service 改名（**MetaAggregationExecutor 拆分移到 successor plan**）

Status: planned
Targets: `nop-metadata-service/.../entity/NopMetaTableBizModel.java`（拆分）、`nop-metadata-service/.../connection/{MetaDataSourceConnectionService,IMetaDataSourceConnectionService}.java`（改名）、`nop-metadata-service/.../reconciliation/{LocalReconciliationService,IReconciliationService}.java`（改名）、`_service.beans.xml` 上游 / `app-service.beans.xml`

- Item Types: `Fix | Proof`

> **范围调整**：原计划同时拆分 `MetaAggregationExecutor`（3474 行 → 7 Processor）和 `NopMetaTableBizModel`（984 行）。MetaAggregationExecutor 拆分缺乏设计支撑（Processor 边界、共享状态、调用图均未定），且现有测试 `TestNopMetaAggregationBizModel` + 3 个相关测试共 2591+ 行包路径变动会让回归测试集体失效。**本 phase 仅拆 `NopMetaTableBizModel`**；`MetaAggregationExecutor` 拆分移到 successor plan `2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（**待起草**），需先补 `ai-dev/design/nop-metadata/aggregation-processor-split.md` 设计文档。

- [ ] 拆 `NopMetaTableBizModel`（984 行）抽出 `MetaTableQueryExecutor`（承载 `queryEntityTable` / `queryExternalTable` / `querySqlTable` + SQL 拼接 + JDBC 执行）；BizModel 仅做表加载、entityName 校验、错误上下文附加（目标 BizModel 行数 ≤ 500）（Fix）
- [ ] `MetaDataSourceConnectionService` / `IMetaDataSourceConnectionService` 改名为 `MetaDataSourceConnectionProcessor` / `IMetaDataSourceConnectionProcessor`（或 `*Manager` / `*Provider`）；同步 bean id / class / `@Inject` 字段（Fix）
- [ ] `LocalReconciliationService` / `IReconciliationService` 改名为 `LocalReconciliationProcessor` / `IReconciliationProcessor`（Fix）
- [ ] 现有测试全绿（拆分不应改变行为）（Proof）
- [ ] 新增测试覆盖 `MetaTableQueryExecutor` 至少一条 happy path + 一条 error path（Proof）

Exit Criteria:

- [ ] `NopMetaTableBizModel` 行数显著降低（目标 ≤ 500 行）；`MetaTableQueryExecutor` 承载查询逻辑
- [ ] 2 对 `*Service` / `I*Service`（共 4 文件）命名全部改为 `*Processor` / `*Manager`；`app-service.beans.xml` 中 bean id 与 class 一致
- [ ] 拆分后所有现有测试通过；每个新 Executor/Processor 有覆盖测试
- [ ] **无静默跳过**：拆分过程中无方法被遗漏（所有原 public 方法在新结构中可被调用）
- [ ] **接线验证**：测试中通过 mock/spy 验证拆分后的 `MetaTableQueryExecutor` 在 BizModel 入口被运行时调用
- [ ] **successor plan 已起草**：`2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（Plan Status: draft），引用本 plan 的 Non-Goals，包含"先补 design doc 再启动"前置条件
- [ ] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/service-layer.md` 在 Processor 拆分示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 4 - 风格与代码规范统一（System.currentTimeMillis + import + TableReference + OrmModelImporter + 死模块 + raw type）

Status: planned
Targets: 10 处 `new Timestamp(System.currentTimeMillis())` / `System.currentTimeMillis()`、5+ 个手写文件的 import、`lineage/TableReference.java`、`dao/model/OrmModelImporter.java`、`nop-metadata-api/`、`nop-metadata-core/`、`NopMetaTableBizModel.queryEntityTable`、3 个高频 JSON 字段对应 BizModel

- Item Types: `Fix | Decision | Proof`

- [ ] 10 处主代码 `new Timestamp(System.currentTimeMillis())` → `CoreMetrics.currentTimestamp()`；`System.currentTimeMillis()` → `CoreMetrics.currentTimeMillis()`（Fix）
- [ ] 5+ 个 audit 抽样文件 import 按 `java.* → jakarta.* → third-party → io.nop.*` 重排；**audit 维度17-01 自承"全模块一致违反"**，本 plan 范围裁定为"audit 抽样 5 文件 + 其余作为 follow-up"（避免单 plan 一次重排 80 文件引入巨大 churn）；Exit Criteria 用 grep 机械验证至少 5 个抽样文件符合（Fix）
- [ ] `lineage/TableReference` 重命名为 `SqlTableReference`；更新唯一消费方 `NopMetaLineageEdgeBizModel` 的 import（Fix）
- [ ] `OrmModelImporter` 移到 `nop-metadata-service/.../service/importer/` 或重命名为 `OrmModelAssembler`（Decision + Fix）
- [ ] 从 `nop-metadata/pom.xml` 的 `<modules>` 移除 `nop-metadata-api`，删除子目录（Decision + Fix）
- [ ] `_NopMetadataCoreConstants` 合并到 `io.nop.metadata.dao._NopMetadataDaoConstants`，移除 `-core` 子模块（Decision + Fix）；或文档化其定位差异
- [ ] `NopMetaTableBizModel.queryEntityTable` 的 raw type cast 补单元测试覆盖（Fix）
- [ ] 为 `extConfig` / `actions` / `validations` 三个高频 JSON 字段引入 `@DataBean`（如 `CheckpointExtConfig { String schedule; boolean autoScore; }`），让 JsonOrmComponent 直接反序列化为强类型 bean（Fix）
- [ ] `_templates/_*.json` 裁定：确认是否被 `nop-cli gen` 使用；若不需要，移除；若是约定，加 README（Decision）
- [ ] 新增测试：`TestCoreMetricsUsage` 验证模块主代码无 `System.currentTimeMillis()` / `new Timestamp(System.currentTimeMillis())` 残留（Proof）
- [ ] 新增/扩展测试：`TestCheckpointExtConfigDataBean` 验证 extConfig JSON 可反序列化为强类型 bean（Proof）

Exit Criteria:

- [ ] 10 处 `System.currentTimeMillis()` / `new Timestamp(...)` 全部替换为 `CoreMetrics.*`
- [ ] 5+ 个手写文件 import 顺序符合 `code-style.md:17`
- [ ] `lineage/TableReference` 重命名为 `SqlTableReference`；消费方 import 已更新
- [ ] `OrmModelImporter` 位置/命名已裁定
- [ ] `nop-metadata-api` 子模块已移除；`<modules>` 中无该条目
- [ ] `nop-metadata-core` 子模块已裁定（合并或文档化）
- [ ] `extConfig` / `actions` / `validations` 三个高频 JSON 字段对应 `@DataBean` 已引入；至少 3 处 `@SuppressWarnings("unchecked")` 被消除
- [ ] `_templates/_*.json` 已裁定
- [ ] 新增的 CoreMetrics / DataBean 测试通过
- [ ] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/code-style.md` 在 import / 时间 API 示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 5 - 长事务、事件一致性、N+1 与 evalExpectPassWhen 配套修复

Status: planned
Targets: `NopMetaDataSourceBizModel.syncExternalTables` / `collectCatalog`、`NopMetaModuleBizModel.save/delete`、`NopMetaTableBizModel.save/delete`、`NopMetaLineageEdgeBizModel.upsertSqlParseEdge/upsertColumnSqlParseEdge/upsertMeasureParseEdge`、`MetaQualityRuleExecutor.evalExpectPassWhen`

- Item Types: `Fix | Proof`

- [ ] `syncExternalTables` 改为先在 `withConnection` 内只读取出 `tables`，关闭外部连接后再做 ORM upsert 循环（Fix）
- [ ] `collectCatalog` 改为每表独立 `withConnection`（短连接），或把外部 SQL 收集放进 `txnTemplate.runWithoutTransaction`（Fix）
- [ ] **save/delete override 事件发布裁定**：采用 `txn().afterCommit(...)`（**单值裁定**，理由：未来若接入消息系统无须再改一次；当前事件表写入仍在事务内以保证回滚一致，`afterCommit` 仅触发"通知/外部副作用"部分）（Fix）
- [ ] **syncExternalTables 事件 before/after 修正**（AR-06）：采用"在 sync 循环之前捕获 before，循环之后捕获 after，并附带 `syncedTableCount` 进入 `details`"（**单值裁定**，理由：保留主实体事件类型，避免改事件 schema 带来的下游订阅者破坏；`entityType=NopMetaTable` + `eventType=ENTITY_SYNCED` 改 schema 移到 successor plan 评估）（Fix）
- [ ] 3 个 upsert*Edge 方法改为批量查询 + 批量保存（`findAllByQuery(sourceTableId IN (...))` + `dao().saveBatch(...)`）（Fix）
- [ ] **evalExpectPassWhen NFE 修复裁定**：采用"在 `evalExpectPassWhen` 内 try/catch `NumberFormatException` 改抛 `NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID)`"（**单值裁定**，理由：与模块内其它 ErrorCode 路径一致；调用处的 `j.setStatus("ERROR")` 模式留给"业务可恢复"路径，但 `expectPassWhen` 配置错误属于规则定义问题，应快速失败）（Fix）
- [ ] 新增/扩展测试：`TestSyncExternalTablesEventConsistency` 验证 before != after 且含 `syncedTableCount`；`TestLineageEdgeBatchUpsert` 验证批量保存下 50 列 SQL 视图抽取事务耗时显著低于 N+1（Proof）
- [ ] 新增测试：`TestEvalExpectPassWhenErrorPath` 验证 `expectPassWhen="gt abc"` 显式抛 ErrorCode（而非破坏整个 checkpoint）（Proof）

Exit Criteria:

- [ ] `syncExternalTables` 与 `collectCatalog` 不再在 `@BizMutation` 默认事务内长时间持有外部 JDBC 连接
- [ ] save/delete override 的事件发布使用 `txn().afterCommit(...)` 或显式注释（已裁定为 afterCommit）
- [ ] `syncExternalTables` 事件 before/after 内容不同且包含 `syncedTableCount`
- [ ] 3 个 upsert*Edge 方法使用批量查询与批量保存
- [ ] `evalExpectPassWhen` 解析失败显式抛 ErrorCode（已裁定为 `ERR_QUALITY_EXPECT_PASS_WHEN_INVALID`）
- [ ] 新增的测试通过
- [ ] **无静默跳过**：批量保存失败、解析失败、事件发布失败在失败路径上显式抛 ErrorCode
- [ ] 受影响 owner docs 同步：`docs-for-ai/02-core-guides/service-layer.md` 在事务模式 / 事件一致性示例中补充 nop-metadata 案例；或明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

### Phase 6 - docs 文档体系补齐

Status: planned
Targets: `docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/nop-metadata.md`（新增）、`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/INDEX.md`、`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`

- Item Types: `Fix | Proof`

- [ ] 在 `module-groups.md` 的"可复用业务模块"行新增 `nop-metadata/`，加独立小节描述（联邦式元数据 / BI 语义层 / 血缘 / 质量 / 对账）（Fix）
- [ ] 新增 `docs-for-ai/03-modules/nop-metadata.md`，参考 `nop-batch.md` / `nop-rule.md` 结构（定位、核心概念、典型使用场景、与 design 文档的链接）（Fix）
- [ ] 在 `source-anchors.md` 增加 `META-001..005` 锚点（MetaAggregationExecutor / MetaTableReferenceResolver / MetaQualityRuleExecutor / SqlColumnLineageExtractor / MetaQualityCheckpointScheduler）（Fix）
- [ ] 更新 `roadmap.md` 第 61-62、79 行的"21 实体"为"32 实体"并补全实体列表（Fix）
- [ ] 在 `INDEX.md` 路由表加入 nop-metadata 入口（Fix）
- [ ] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 验证 0 broken links（Proof）

Exit Criteria:

- [ ] `module-groups.md` 含 nop-metadata 条目
- [ ] `docs-for-ai/03-modules/nop-metadata.md` 文件存在且结构完整
- [ ] `source-anchors.md` 含 META-001..005
- [ ] `roadmap.md` 实体数量与列表正确（32 实体）
- [ ] `INDEX.md` 含 nop-metadata 路由条目
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
- [ ] **端到端验证**：从一个全新的 AI 会话提问"如何用 nop-metadata 做联邦式元数据"，能通过 INDEX.md → module-groups.md → 03-modules/nop-metadata.md → design 文档完整导航
- [ ] `ai-dev/logs/2026/07-19.md` 追加本 phase 进度

## Closure Gates

- [ ] 所有 in-scope confirmed P1 API/契约缺陷（维度03-01、03-02、18-01、18-02、20-01）已修复
- [ ] 所有 in-scope P2 工程规范缺陷（维度02-01、02-02 部分[NopMetaTableBizModel 拆分]、07-02、07-03、09-01..09-04、09-09、09-10、14-01、17-01 部分[5 文件抽样]、17-02、18-03、18-04、AR-06、AR-10、AR-11）已修复
- [ ] 所有 in-scope P3 命名/重构（维度01-01、01-02、02-03、02-04、07-04、09-05、09-06、09-07 部分[ExternalTableStructureReader + 5 javadoc]、12-01、14-02、15-02、15-03 部分[3 个高频字段]、18-05、05-01）已修复或显式裁定
- [ ] `I*Biz` 接口、DTO、ErrorCode、Processor 命名在仓库中可观察
- [ ] docs 体系完整（module-groups + 03-modules + source-anchors + INDEX + roadmap 均更新）
- [ ] **维度02-02（MetaAggregationExecutor 拆分）已移到 successor plan `2026-07-19-1250-4-nop-metadata-aggregation-processor-split.md`（Plan Status: draft），需补 design doc 后启动**
- [ ] **维度04-08（delFlag domain）由 Plan 2 裁定；维度09-08（throw new SQLException）由 Plan 1 处理；维度13-03（custom_sql 沙箱）由 Plan 1 处理；AR-08（Math.toIntExact）由 Plan 2 处理** —— 这些不在本 plan Closure Gates 范围
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope confirmed contract defect 或 owner-doc drift
- [ ] 受影响的 owner docs 已同步（已合并入本计划 Phase 6）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`I*Biz` 接口在跨模块调用路径上运行时可被代理调用（不只是接口存在）、（b）拆分后的 Processor 在 BizModel 入口运行时被调用（不只是类存在）、（c）ErrorCode 集中化后所有 throw 路径都引用 `NopMetadataErrors.*` 常量（不只是常量定义）
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C`
- [ ] `./mvnw test -pl nop-metadata -am -T 1C`
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

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

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码为 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
  - Anti-Hollow 检查结果：<<端到端调用链追踪结果>>
  - Deferred 项分类检查：<<确认无 in-scope contract defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
