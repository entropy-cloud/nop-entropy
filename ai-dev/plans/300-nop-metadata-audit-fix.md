# 300 nop-metadata 审计修复

> Plan Status: draft
> Last Reviewed: 2026-07-20
> Source: `ai-dev/audits/2026-07-20-1554-deep-audit-nop-metadata/summary.md`、`ai-dev/audits/2026-07-20-1554-deep-audit-nop-metadata/adversarial-review.md`
> Related: `ai-dev/plans/293-nop-metadata-design-consistency-fix.md`
> Draft Review: R1 已完成（独立 explore 子 agent，3 BLOCKER + 3 HIGH + 2 MEDIUM 已修复）

## Purpose

收口 2026-07-20 nop-metadata 深度审计和对抗性审查中发现的高中优先级问题（P2 及以上），消除已确认的代码缺陷、契约缺口和可维护性债务。

## Current Baseline

审计生成 34 个唯一发现问题（去重后）。severity 分布经三次独立子 agent 复核达成共识：
- **P0**: 0
- **P1**: 2 项（summary.md §三）— 独立复核后降级为 P3（原因见 summary.md §二共识表：无跨模块调用方 + action-auth 默认关闭）
- **P2**: 11 项（01-01 依赖违规、02-01/02-02 大文件、03-02 缺 IServiceContext、04-01 propId 跳序、04-04 命名不一致、08-01 缺 xmlns、09-01 ErrorCode 前缀、09-02 静默吞异常、13-01/13-02 权限、14-02 竞态条件、16-01/16-02 空存根、17-01 缺版权头、18-01 文档偏离、19-01/19-02 命名不一致、20-01 冗余依赖、21-01 Thread.sleep）
- **P3**: 3 项（04-02 SQL 保留字、04-03 cascade-delete、14-03 afterCommit）
- **关闭(驳回)**: 1 项（AR-02 custom_sql 黑名单绕过—事实错误）
- **P4(通知性)**: 2 项（15-01 冗余注解、16-01/16-02 降级后）
- **零发现维度**: 3 个（05-生成管线、06-Delta、10-XDSL）

## Goals

- 修复已确认的代码级缺陷和契约缺口
- 消除 ErrorCode 集中化/可维护性技术债务的核心项
- 降低 MetaAggregationExecutor 超大类（3468行）的维护成本
- 消除已确认的安全防御缺口
- 清理空存根文件和残留代码

## Non-Goals

- 非本审计发现的设计新增或功能增强（走 roadmap 计划）
- 已驳回的发现项（AR-02 custom_sql 黑名单、14-01 dispatch 注释）
- XMeta 生成模板重构（独立于审计修复的专项）
- ORM 模型结构变更（如新实体/新列，属 Protected Area）

## Scope

### In Scope

- I\*Biz 接口声明完整性（03-01/07-01）
- ErrorCode 集中化迁移（09-01/19-01）
- 超大文件拆分（02-01 MetaAggregationExecutor, 02-02 MetaJoinExecutor）
- 依赖违规修复（01-01 dao→core compile 依赖）
- ORM 模型微观修复（04-01 NopMetaDictItem propId 跳序, 04-04 tableId→metaTableId 命名统一）
- 安全与数据完整性加固：connectionConfig 写入阻断（13-03）、upsertExternalTable 竞态条件（14-02）
- 空存根和残留文件清理（16-01/16-02）
- 命名空间/版权头修复（08-01/17-01）
- Map<String,Object> 返回类型逐步迁移（03-03）
- 测试反模式修复（21-01 Thread.sleep）
- 文档状态更新（18-02 design doc status）

### Out Of Scope

- 全模块 @Auth 注解系统化引入（需安全策略评估后独立计划）
- DataAuth 覆盖率扩展到全部 30+ 实体（策略决策，非修复）
- MetaQualityScorer 异常处理重构（低风险路径，P3 级）
- ORM 模型 cascade-delete 补全（P3 级，需业务影响评估）
- SQL 注入防御系统级重构（AR-02 已关闭/驳回，现有 toUpperCase()+黑名单防御正常运作）

## Execution Plan

### Phase 1 - 契约修复与代码清理

Status: planned
Targets: `nop-metadata-dao/biz/`、`nop-metadata-service/entity/`、`nop-metadata-service/beans/`

Item Types: `Fix`

- [ ] 1a: 向 `INopMetaReconciliationConfigBiz` 添加 `executeReconciliation(@Name("configId") String configId, IServiceContext context)` 方法声明
- [ ] 1b: 向 `INopMetaReconciliationResultBiz` 添加 `confirmMatch(...)` 和 `batchConfirmMatches(...)` 方法声明
- [ ] 1c: 向 `INopMetaLineageEdgeBiz` 的 4 个查询方法（getUpstream/getDownstream/getLineagePath/getImpactAnalysis）添加 `IServiceContext context` 末参
- [ ] 1d: 删除 `NopMetadataConfigs.java` 和 `NopMetadataConstants.java` 空存根文件
- [ ] 1e: 在 `app-service.beans.xml` 添加 `xmlns:ioc="ioc"` 命名空间声明
- [ ] 1f: 在缺失版权头的文件（NopMetaDataSourceBizModel, NopMetaTableBizModel, CheckpointActionDispatcher 等）补充标准版权头

Exit Criteria:
> - 所有 I\*Biz 接口的自定义 public 方法均已在接口声明（grep `@BizMutation|@BizQuery` 在接口文件中验证无遗漏）
> - 空存根文件已删除（grep `NopMetadataConfigs|NopMetadataConstants` 在非 `_gen/` 路径下零匹配）
> - `app-service.beans.xml` XML 语法验证通过
> - 所有 `nop-metadata-service/src/main/java/` 下的 `.java` 文件（排除 `_gen/`）均含标准版权头（`grep -L "Copyright"` 验证零缺失）
> - No owner-doc update required: I\*Biz 接口变更是内部契约补齐，不改变公开 API

### Phase 2 - ErrorCode 集中化迁移

> 注意：Phase 2（ErrorCode 迁移）和 Phase 3（大文件拆分）操作的文件有重叠（MetaAggregationExecutor.java, MetaJoinExecutor.java）。
> **推荐顺序**：先执行 Phase 2（ErrorCode 迁移），再执行 Phase 3（文件拆分）。
> 理由：拆分后新文件中的 import/ErrorCode 引用需从迁移后状态开始，避免两阶段间来回修改。

Status: planned
Targets: `nop-metadata-service/` 全线 Java 文件

Item Types: `Fix`

- [ ] 2a: 审计 `NopMetadataErrors.java` 现有 8 个 ErrorCode，确认命名规范（`nop.err.metadata.*`）
- [ ] 2b: 将 `MetaAggregationExecutor.java` 中 15 个内联 ErrorCode 迁移至 `NopMetadataErrors.java`，统一前缀
- [ ] 2c: 将 `NopMetaTableBizModel.java` 中 13 个内联 ErrorCode 迁移
- [ ] 2d: 将 `NopMetaDataSourceBizModel.java`、`FilterToSqlTranslator.java`、`MetaQualityRuleExecutor.java`、`MetaJoinExecutor.java`、`SqlColumnLineageExtractor.java` 中的内联 ErrorCode 迁移
- [ ] 2e: 确认全部迁移后 `NopMetadataErrors.java` 中 ErrorCode 前缀统一为 `nop.err.metadata.*`，全模块无 `metadata.xxx` 格式的内联定义残留
- [ ] 2f: 删除各文件中迁移后遗留的 `static final ErrorCode ERR_XXX = ErrorCode.define(...)` 字段

Exit Criteria:
> - 全模块 0 个 `ErrorCode.define("metadata.` 调用（不含 `nop.err.metadata.`）
> - `NopMetadataErrors.java` 新增 ErrorCode 数量与移除数一致
> - 编译通过，测试通过

### Phase 3 - 超大文件拆分

Status: planned
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/query/MetaAggregationExecutor.java`

Item Types: `Fix`

- [ ] 3a: 从 `MetaAggregationExecutor.java` 提取 `MemAggAccumulator` 及其 5 个实现子类到独立文件 `MemAggAccumulator.java`
- [ ] 3b: 提取跨库聚合相关类（`CrossDbFieldResolver`, `CrossDbField`, `CrossDbMeasureSpec`, `CrossDbDimensionSpec`）到 `CrossDbAggregationHelper.java`
- [ ] 3c: 提取 JOIN 聚合解析类（`JoinFieldResolver`, `JoinExternalSideResolver`, `JoinMixedSideResolver`, `JoinMeasureSpec`, `JoinDimensionSpec`, `JoinField`）到 `JoinAggregationHelper.java`
- [ ] 3d: 从 `MetaJoinExecutor.java` 提取跨库拼接逻辑（`executeEntityEntityCrossDbMerge`, `executeTableEndpointCrossDbMerge`, `executeMixedCrossDbMerge`, `crossDbMerge`, `verifyCrossDbKeyTypeConsistency`）到 `CrossDbJoinMerger.java`
- [ ] 3e: 原文件保留 public 入口方法，内部委托到新抽取类（零行为变更）
- [ ] 3f: 确认所有引用点（包括测试）更新到新类路径后编译通过

Exit Criteria:
> - `MetaAggregationExecutor.java` 行数降至 1500 行以下
> - `MetaJoinExecutor.java` 行数降至 800 行以下
> - 全量测试通过，与拆分前结果一致

### Phase 4a - 安全与数据完整性加固

Status: planned
Targets: `NopMetaDataSourceBizModel.java`、`NopMetaDataSource.xmeta`

Item Types: `Fix`

- [ ] 4a1: 在 `NopMetaDataSource.xmeta` 留存层补充 `<prop name="connectionConfig" published="false" insertable="false" updatable="false"/>`，阻断 GraphQL 写入路径（当前仅有 published=false，写入侧未受限）
- [ ] 4a2: 在 `NopMetaDataSourceBizModel.upsertExternalTable` 中添加数据库级唯一约束兜底（ALTER TABLE nop_meta_table ADD CONSTRAINT uk_meta_table_module_schema UNIQUE(meta_module_id, table_name, schema)），消除并发竞态条件
- [ ] 4a3: 确认 `MetaQualityRuleExecutor` 的 custom_sql 黑名单在 `toUpperCase()` 后匹配的正确性（已有防御，增加注释说明安全边界）
- [ ] 4a4: 确认 `FilterToSqlTranslator` 标识符白名单正则 `^[A-Za-z_][A-Za-z0-9_]*$` 对 schema/table/column 的全覆盖

Exit Criteria:
> - `connectionConfig` 的 GraphQL 写入路径已彻底阻断（confirm: `grep 'connectionConfig' NopMetaDataSource.xmeta` 输出含 `insertable="false" updatable="false"`）
> - 数据库约束生效且幂等 upsert 路径测试通过（唯一约束 `uk_meta_table_module_schema` 已确认存在）
> - `MetaQualityRuleExecutor` 和 `FilterToSqlTranslator` 中已补充安全边界注释说明
> - No owner-doc update required: xmeta 变更属内部安全加固，不改变公开 API 契约

### Phase 4b - ORM 模型微观修复与依赖清理

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-dao/pom.xml`

Item Types: `Fix`

- [ ] 4b1: 修复 `NopMetaDictItem` 实体 `isDelta` 列的 propId（当前 propId=17 跳序，改为 propId=11，后续审计字段 propId 依次改为 12-17）
- [ ] 4b2: 统一 `NopMetaProfilingRule.tableId`→`metaTableId`（与模型中其他 12 个引用 NopMetaTable 的实体一致）
- [ ] 4b3: 移除 `nop-metadata-dao/pom.xml` 中对 `nop-metadata-core` 的 compile 依赖，将 `NopMetadataCoreConstants` 中 `MODULE_STATUS_DRAFTING` 常量合并到 `NopMetadataDaoConstants`（消除规则 2 违规）
- [ ] 4b4: 确认重新 codegen 后实体常量更新，编译通过

Exit Criteria:
> - `NopMetaDictItem` 的 propId 序列连续无跳跃（`grep propId nop-metadata.orm.xml | grep NopMetaDictItem -A 30` 验证）
> - 全模型统一使用 `metaTableId` 作为 NopMetaTable 的外键列名（grep `TABLE_ID.*NopMetaTable` 零匹配，排除 profiling_rule）
> - `nop-metadata-dao/pom.xml` 中无 `nop-metadata-core` 依赖（grep 验证）
> - `OrmModelImporter.java` 引用更新后编译通过
> - No owner-doc update required: ORM 模型修复不影响公开 API

### Phase 5 - 文档与测试维护

Status: planned
Targets: `ai-dev/design/nop-metadata/`、`nop-metadata-service/src/test/`

Item Types: `Fix`

- [ ] 5a: 更新 `ai-dev/design/nop-metadata/README.md` 状态从 draft 为 active，更新阶段描述与实际代码一致
- [ ] 5b: 修复 `TestNopMetaTableBizModel.testProfileTimeSeriesAppend` 中的 `Thread.sleep(1100)`，改为基于查询时间戳排序的断言策略（不引入 IClock 接口——仅用于测试的接口会增加生产代码复杂度；改为收集前后两次 profile 结果的时间戳并断言 `collectedAt` 严格递增）
- [ ] 5c: 确认 Phase 1-4 所有变更的测试覆盖

Exit Criteria:
> - `ai-dev/design/nop-metadata/README.md` 的 `Status` 已从 `draft` 改为 `active`，阶段描述与实际代码一致
> - `testProfileTimeSeriesAppend` 中无 `Thread.sleep` 调用（grep 验证）
> - 本 plan 所有变更的测试通过

## Deferred But Adjudicated

| 项 | 原问题 | Classification | Why Not Blocking | Successor Required | Successor Path |
|----|-------|---------------|-----------------|-------------------|----------------|
| 全模块 @Auth 系统化引入 | 13-01 缺少细粒度权限注解 | out-of-scope improvement | action-auth 默认关闭 + 粗粒度 mutation 权限兜底。引入 @Auth 需安全策略统一评估 | No | 安全策略评估完成后启动 |
| DataAuth 覆盖全部实体 | 13-02 仅 3/30+ 实体有行级权限 | optimization candidate | 关键实体（DataSource/Checkpoint/Event）已保护。扩展策略需业务评估 | No | 需业务安全需求明确后 |
| ORM 模型 cascade-delete | 04-03 子表级联缺失 | optimization candidate | 当前手动清理模式工作正常。P3 级 | No | 归入下次 ORM 模型维护 |
| MetaQualityScorer 异常处理 | 09-02 静默吞异常 | watch-only residual | catch 返回 null 仅影响单维度评分，不扩散。低风险路径 | No | 如出现相关 bug 报告再处理 |
| Map<String,Object> 返回值迁移 | 03-03 21 个方法 | optimization candidate | 大部分返回非结构化数据（外部表/动态 SQL 结果），无法使用固定 DTO。仅固定结构方法（computeQualityScore/checkContract）可迁移 | No | 标记为 P3 后续 |

## Closure Gates

- [ ] Phase 1 全部 Exit Criteria 满足，测试通过
- [ ] Phase 2 全部 Exit Criteria 满足，测试通过
- [ ] Phase 3 全部 Exit Criteria 满足，测试通过
- [ ] Phase 4 全部 Exit Criteria 满足，测试通过
- [ ] Phase 4b 全部 Exit Criteria 满足，ORM codegen 后编译通过
- [ ] Phase 5 全部 Exit Criteria 满足，测试通过
- [ ] 全量 `./mvnw test -pl nop-metadata-service -am` 通过
- [ ] docs-for-ai 中与本 plan 变更相关的文档已更新
- [ ] ai-dev/logs/2026-07-20.md 已记录本 plan 的 closure 状态
- [ ] 独立子 agent closure audit 已执行并确认
