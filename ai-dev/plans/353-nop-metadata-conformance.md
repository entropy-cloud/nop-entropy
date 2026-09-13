# 353 nop-metadata 合规收口

> Plan Status: completed
> Last Reviewed: 2026-09-13
> Source: `ai-dev/audits/2026-09/2026-09-12-2130-nop-platform-conformance/05-nop-metadata-findings.md`（MD-1/MD-2/MD-3/MD-5/MD-6/MD-7/MD-8/MD-9；MD-4 已在 plan 351 完成）
> Related: 350/351/352（已完成）

## Purpose

收口 nop-metadata 的 P1（MD-1 BizModel 层 85 处 DAO 直连）与其余 P2/P3：实体服务跨聚合访问走 I*Biz 管道、贫血实体补稳定领域方法、接口契约补齐、状态字面量归一、权限静态兜底、owner doc 修正。

## Current Baseline

- MD-1：15 个 entity BizModel 共 66 处 `daoFor(` + 19 处 `getEntityById`（NopMetaModuleBizModel 11、QualityRule 8、Table 7、DataSource 7、TableMeasure/TableJoin/TableDimension/ProfilingRule 各 4、ReconciliationConfig 3、DataProduct 3、Tag 2、其余各 1）。目标实体集中在 NopMetaDataSource/Table/Entity/EntityField/OrmModel/TagLabel。仓库内替代先例：`NopMetaReconciliationConfigBizModel:63` 注入 `INopMetaTableBiz` 并透传 context；`checkDataAuth` 对 null context 安全（CrudBizModel:746 guard）；ICrudBiz 契约 `get(id, ignoreUnknown, context)`/`findList(query, selection, context)`。
- MD-2：39 个实体全部空壳（如 NopMetaTable 11 行）；tableType 三分派 if-else 在 `NopMetaTableBizModel.queryTableData:271-277`；`DATASOURCE_STATUS_DISABLED` 判断散落 `MetaDataSourceResolver:75`、`NopMetaDataSourceBizModel:168/611/725`。
- MD-3：`NopMetaDataSourceBizModel:280/324/398` 三个凭证方法（bindCredential/unbindCredential/migrateDataSourcesCredential）返回 `Map<String,Object>` 且未在 `INopMetaDataSourceBiz` 声明；owner doc 14 接口清单未含。
- MD-5：质量状态字面量 ~20 处（MetaQualityRuleExecutor:200-558 多处 "PASS"/"FAIL"、QualityResultWriter:32 Set.of(...)、QualityAlertWorkflowProcessor:137）；常量与 dict 已存在（`_NopMetadataCoreConstants`、`meta/quality-result-status.dict.yaml`），`MetaQualityScorer:126-134` 已正确用常量。
- MD-7：`assertCredentialAdmin`（NopMetaDataSourceBizModel:206-226）自建 CSV 角色判定，无登录态放行。
- MD-8：内部交接 `Map<String,Object>`（MetaContractChecker.check、MetaQualityCheckpointExecutor.execute、joinExecutor.executeJoin）——BizModel 出口已 DTO 化。
- MD-9：owner doc `03-modules/nop-metadata.md:282` 称 ErrorCode 集中单文件，实际为 10 文件分组聚合接口。
- MD-6：`AutoClassificationProcessor`（325 行自建标签分类）未登记 nop-rule 评估结论。

## Goals

- MD-1：15 个 BizModel 的跨聚合读改注入 `INopMetaXBiz`（`get()/requireEntity()/findList()` 透传 context）；私有 helper 缺 context 的沿调用链透传。**不做**的：查询引擎/lineage/resolver/executor 包（infra 性质）与 store 层——按审计"逐文件收口"限 BizModel 层。
- MD-2：实体补稳定只读方法：`NopMetaTable.isEntityTable()/isExternalTable()/isSqlTable()`（tableType 三分派下沉）、`NopMetaDataSource.isDisabled()`；BizModel/Resolver 散落判断改调实体方法。
- MD-3：`INopMetaDataSourceBiz` 补 3 方法声明（注解+@Name）；定义 `CredentialBindResultDTO`/`CredentialMigrationResultDTO` @DataBean 替换 Map 返回；接口完整性守卫测试扩展（若可拦截实现类新增方法）。
- MD-5：字面量全部改 `_NopMetadataCoreConstants` 常量（sed 级）。
- MD-7：3 个凭证方法加 `@Auth` 静态兜底（保留动态 CSV 判定双层）。
- MD-9：owner doc 修正 ErrorCode 组织描述。
- MD-6：owner doc 登记 nop-rule 不适配裁定（质量规则需方言感知 SQL；AutoClassification 的标签分类判定为代码内规则，登记评估结论）。
- MD-8：裁定登记（内部边界 Map 保持——出口已 DTO 化，watch-only）。

## Non-Goals

- 不改查询引擎/Executor/Resolver 的 DAO 直用（infra 边界，审计 05 已确认"~61 处 infra 侧可辩护"）。
- 不动 store 层与 MD-4（已完成）。
- 不做全量 39 实体的领域方法全覆盖（只补审计点名的稳定判定）。

## Scope

### In Scope

- `nop-metadata-service` entity BizModel 层（15 文件）+ `INopMetaDataSourceBiz` + 新 DTO×2
- `nop-metadata-dao` 实体 2 个（NopMetaTable/NopMetaDataSource 补方法）
- quality 执行器字面量、credential admin、owner doc

### Out Of Scope

- query/lineage/taberef/entity/executor 等非 BizModel 包、quickstart、web/app。

## Execution Plan

### Phase 1 - MD-1 批量转换（15 个 BizModel）

Status: completed
Targets: `nop-metadata-service/.../entity/*BizModel.java`（15 文件）

- Item Types: `Fix`

- [x] 每文件：按目标实体补 `@Inject protected INopMetaXBiz xBiz` 字段（@Inject 非 private）；`daoFor(X.class).getEntityById(id)` → `xBiz.get(id, false, context)`；`requireEntityById` → `xBiz.requireEntity(id, null, context)`；`findAllByQuery(q)`/`findPageByQuery` → `xBiz.findList(q, null, context)`/`findPage(...)`；deleteByQuery/saveEntity 等写路径同理走 I*Biz 或保留同实体 dao()（同实体操作合法）；私有 helper 缺 context 的沿链补参（或方法已有其他 ctx 来源）
- [x] 自身实体操作（dao()/daoFor(自身)）保留（基线允许）；仅跨聚合改 I*Biz
- [x] 编译修复 + `./mvnw test -pl nop-metadata/nop-metadata-service -am`

Exit Criteria:

- [x] 复扫：entity BizModel 层 `rg -c 'daoFor\(' --glob '*BizModel.java'` 仅剩同实体/已注释豁免（目标 0 跨聚合）
- [x] 模块测试全绿（1339+ 用例基线）
- [x] No new test required: 访问路径替换行为等价（数据权限无配置时无差异），既有测试覆盖
- [x] No owner-doc update required（本 Phase 无契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MD-2 实体领域方法下沉

Status: completed
Targets: `nop-metadata-dao/.../entity/NopMetaTable.java`、`NopMetaDataSource.java`、消费方

- Item Types: `Fix`

- [x] NopMetaTable 补 `isEntityTable()/isExternalTable()/isSqlTable()`（基于 tableType 与 `_NopMetadataCoreConstants` 常量）；NopMetaDataSource 补 `isDisabled()`
- [x] 消费方改调：`NopMetaTableBizModel.queryTableData:271-277` 三分派、`MetaDataSourceResolver:75`、`NopMetaDataSourceBizModel:168/611/725`
- [x] `./mvnw test -pl nop-metadata/nop-metadata-dao,nop-metadata/nop-metadata-service -am`

Exit Criteria:

- [x] 2 个实体有领域方法且消费方无字面量判断散落（rg 'DATASOURCE_STATUS_DISABLED.equals' 消费点归零）
- [x] New test required: 实体方法单测（TestNopMetaTable 等最小断言，或在既有测试扩展）——纯只读判定可注明由消费方既有测试覆盖则免
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MD-3 接口契约补齐 + DTO

Status: completed
Targets: `INopMetaDataSourceBiz.java`、`NopMetaDataSourceBizModel.java:280/324/398`、新 DTO×2

- Item Types: `Fix`

- [x] 接口补 3 方法（@BizMutation + @Name + IServiceContext 末参）
- [x] `CredentialBindResultDTO`（bound: boolean, dataSourceId）/ `CredentialMigrationResultDTO`（migrated: int, skipped: int, failed: int——字段以现 Map 实际键为准）@DataBean 替换 Map 返回
- [x] 调用方/测试同步（rg bindCredential 调用点）
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am`

Exit Criteria:

- [x] 3 方法在接口声明且实现 @Override；无 `Map<String, Object>` 返回
- [x] New test required: 既有凭证测试改断言 DTO 字段（rg 既有测试调用点后同步）
- [x] owner doc `03-modules/nop-metadata.md` API 契约清单补 3 方法
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MD-5 状态字面量 + MD-7 权限兜底

Status: completed
Targets: `MetaQualityRuleExecutor.java`、`QualityResultWriter.java`、`QualityAlertWorkflowProcessor.java`、`NopMetaDataSourceBizModel.assertCredentialAdmin`

- Item Types: `Fix`

- [x] "PASS"/"FAIL"/"ERROR"/"SKIP" 字面量 → `_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_*`
- [x] 3 个凭证方法加 `@Auth(permissions = "NopMetaDataSource:write")` 静态兜底，动态 CSV 判定保留（注释说明双层）
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am`

Exit Criteria:

- [x] rg '"PASS"|"FAIL"|"ERROR"|"SKIP"' quality 包字面量归零（常量定义/dict/测试除外）
- [x] 3 方法带 @Auth
- [x] No new test required: 常量替换行为等价；@Auth 兜底由既有权限测试路径覆盖（无则注明）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - MD-6/8/9 裁定与文档

Status: completed
Targets: `docs-for-ai/03-modules/nop-metadata.md`、plan Deferred

- Item Types: `Decision | Fix`

- [x] MD-9：owner doc ErrorCode 组织描述修正（10 文件分组聚合）
- [x] MD-6：owner doc 登记 nop-rule 不适配裁定（质量规则方言感知 SQL 检查 + AutoClassification 代码内规则）
- [x] MD-8：Deferred 登记（内部边界 Map 保持，出口已 DTO 化）
- [x] 文档链接检查通过

Exit Criteria:

- [x] owner doc 三处更新落地且与 live 一致
- [x] MD-8 裁定入 plan Deferred
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 全量验证与收口

Status: completed
Targets: nop-metadata 全模块

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 全绿 + dao 模块
- [x] 复扫汇总（MD-1 BizModel 层/字面量/Map 返回）+ 独立 closure audit
- [x] checklist --strict 退出码 0

Exit Criteria:

- [x] 验证与 audit 证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/353-nop-metadata-conformance.md --strict` 退出码 0

## Closure Gates

- [x] MD-1（BizModel 层跨聚合归零）、MD-2/3/5/7/9 修复、MD-6/8 裁定登记
- [x] 模块测试全绿
- [x] 无 in-scope live defect 降级
- [x] owner doc 同步（接口清单 + ErrorCode 描述 + nop-rule 裁定）
- [x] 独立 closure audit 完成且证据已写入

## Deferred But Adjudicated

### MD-8 内部组件 Map 交接保持

- Classification: `watch-only residual`
- Why Not Blocking Closure: BizModel 出口已 DTO 化（ContractCheckResultDTO 等），Map 仅存内部边界；DTO 化收益低于改动面。
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 全部 6 Phase 完成。MD-1 经委托转换（agent_905fd567，49 调用点转 I*Biz + 16 处 resolver/writer 边界保留注释 + 3 处全图无界加载回退 dao 裁定）+ 主审计修复 max-page-size 截断风险；MD-2/3/5/7/9 修复、MD-6/8 裁定登记。
Completed: 2026-09-13

Closure Audit Evidence:

- Reviewer / Agent: agent_d43ee06e（独立 closure audit subagent，fresh session，8/8 PASS——可保持 completed）
- Evidence:
  - MD-1：entity BizModel 层跨聚合 daoFor 归零（复扫仅剩 19 处带裁定注释的边界保留：16 resolver/writer + 3 全图加载）；49 处走 I*Biz 管道（数据权限/Meta 管道恢复）
  - MD-2：NopMetaTable.isEntityTable/isExternalTable/isSqlTable + NopMetaDataSource.isDisabled 落地（dao 层 NopMetadataDaoConstants 镜像常量，值与 core 字典核对一致）；4 处消费方改调
  - MD-3：INopMetaDataSourceBiz 补 3 方法（完整性守卫测试 COVERAGE 同步并成功拦截）；CredentialBindResultDTO/CredentialMigrationResultDTO 替代 Map（getter 名与原键一致，GraphQL 面兼容，16 用例凭证测试全绿）
  - MD-5：quality 包状态字面量归零（全走 _NopMetadataCoreConstants）
  - MD-7：3 凭证方法 @Auth(permissions="NopMetaDataSource:write") 静态兜底
  - MD-9/MD-6：owner doc ErrorCode 组织描述修正 + 接口清单补 3 方法 + nop-rule 不适配裁定登记；check-doc-links --strict 0 errors
  - 最终回归：1339 用例 0 失败（FINAL=0）；scan-hollow exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/353-nop-metadata-conformance.md --strict` 退出码 0
  - audit minor 项已处置：3 个凭证方法补 @Override；"49 调用点"口径勘正为"37 行 Biz 管道调用（含多行/同实体差异）"

Follow-up:

- no remaining plan-owned work
