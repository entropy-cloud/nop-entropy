# nop-metadata 审计目标集（Audit Target Set）

> 产出方：plan `2026-08-13-1930-1`（Cycle 1 / I0 — 不变式盘点与基线，Phase 1）；Cycle 2 / I1' 增补：plan `2026-08-15-0820-1`（§5 silent-wrong-result 目标集 + §1.3 计数漂移校正）
> 实测日期：2026-08-13（Cycle 1）/ 2026-08-15（Cycle 2 增补与校正，live repo，非记忆）
> 上游 roadmap：`ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0 / Cycle 2 I1'）
> 消费者：I1（表完备性门禁基准）、I2（按不变式审计的全集）、invariant-catalog（覆盖率回填）、Cycle 2 I2'/I3'（red list 裁决全集）

## 目的

为 I1/I2 提供「全集」基准：

1. **方法全集** = nop-metadata 全部变更型入口面（`@BizQuery` / `@BizMutation` 注解方法 + 显式接受 `limit` 入参的 public 方法）。**不**是全部 `public` 符号（service 下实测 723 个 `public`，多为 helper/getter，不属审计目标面）。
2. **ORM 全集** = 39 entity × 37 unique-key，逐条标注是否带 `constraint` 属性。
3. 提供可由独立 `rg`/`grep` 复现的命令，使任何计数声明都可被复核。

## 基线校正记录（重要 — 虚假关闭教训实证）

> 本节遵循 plan Phase 2 的元规则：**不变式判定以 live 实测为准，commit message / 旧 completion note 不足为据**（引 AR-06 虚假关闭先例）。

| 声明来源 | 旧值（记忆/旧文档） | live 实测值（2026-08-13） | 复现命令 |
|---|---|---|---|
| unique-key 缺 `constraint` 数 | plan baseline 称「1 个缺失」 | **0 个缺失**（37/37 全带 `constraint` + `columns`） | `node -e '…'`（见 §3 复现脚本） |

**说明**：plan `Current Baseline`（line 19）与 Phase 1 Exit Criteria（line 76）均沿用旧值「1 missing」，但 live XML-aware 核对（逐 `<unique-key>` 元素，含跨行）显示 **37/37 全部带 `constraint=` 与 `columns=`**。Lesson 09 记录的「36 缺 constraint」已于 R3.19（plan-2026-08-05-0746-2 Phase 4）补齐，此后模型新增的第 37 个 unique-key 也已带属性。**以 live 0 为准，不以旧文档 1 为准。** 这正是闭环入口要钉死的事实基线：unique-key 族当前 red list = 0（不变式仍需沉淀以防回退，但 I2 不会从此族产出违规项）。

---

## §1 方法全集（变更型入口面）

### 1.1 入口面计数汇总

| 口径 | 计数 | 复现命令 |
|---|---|---|
| `@BizModel` 注解类（main） | **40**（39 entity BizModel + 1 `NopMetaSearchBizModel`） | `rg -c '^\s*@BizModel' nop-metadata/nop-metadata-service/src/main/java \| awk -F: '{s+=$2} END{print s}'` |
| `@BizQuery` 注解方法 | **13** | `rg -c '^\s*@BizQuery' nop-metadata/nop-metadata-service/src/main/java \| awk -F: '{s+=$2} END{print s}'` |
| `@BizMutation` 注解方法 | **30** | `rg -c '^\s*@BizMutation' nop-metadata/nop-metadata-service/src/main/java \| awk -F: '{s+=$2} END{print s}'` |
| `@BizAction` 注解方法 | **0**（nop-metadata 不使用此注解） | `rg -c '^\s*@BizAction' nop-metadata/nop-metadata-service/src/main/java` |
| 显式接受 `limit` 入参的 public 方法 | **4**（全部为 `@BizQuery`） | `rg -n '@Name\("limit"\)' nop-metadata/nop-metadata-service/src/main/java` |
| **变更型入口面合计**（`@BizQuery`+`@BizMutation`） | **43** | — |
| service 下 `public` 符号（参考，非目标面） | 723 | `rg -c 'public ' nop-metadata/nop-metadata-service/src/main/java \| awk -F: '{s+=$2} END{print s}'` |

> web/core/app 实测零 `@BizModel`（BizModel 全部位于 nop-metadata-service），符合 plan baseline。

### 1.2 入口方法清单（类 → 方法 → limit-taking）

#### `@BizQuery` 方法（13）

| 类 | 方法 | limit-taking |
|---|---|---|
| NopMetaTableBizModel | `previewSqlFields` | — |
| NopMetaTableBizModel | `resolveTableFields` | — |
| NopMetaTableBizModel | `queryTableData` | ✅ limit |
| NopMetaTableBizModel | `queryJoinData` | ✅ limit |
| NopMetaTableBizModel | `queryAggregation` | ✅ limit |
| NopMetaDataContractBizModel | `checkContractReadOnly` | — |
| NopMetaSearchBizModel | `searchMetadata` | ✅ limit |
| NopMetaQualityRuleBizModel | `judgeByRuleId` | — |
| NopMetaDataProductBizModel | `getLinkedAssets` | — |
| NopMetaLineageEdgeBizModel | `getUpstream` | — |
| NopMetaLineageEdgeBizModel | `getDownstream` | — |
| NopMetaLineageEdgeBizModel | `getLineagePath` | — |
| NopMetaLineageEdgeBizModel | `getImpactAnalysis` | — |

#### `@BizMutation` 方法（30）

| 类 | 方法 |
|---|---|
| NopMetaQualityCheckpointBizModel | `executeCheckpoint` |
| NopMetaReconciliationResultBizModel | `confirmMatch` |
| NopMetaReconciliationResultBizModel | `batchConfirmMatches` |
| NopMetaDataContractBizModel | `checkContract` |
| NopMetaSearchBizModel | `rebuildSearchIndex` |
| NopMetaTableBizModel | `profileTable` |
| NopMetaTableBizModel | `createSqlTable` |
| NopMetaLineageEdgeBizModel | `recordLineage` |
| NopMetaLineageEdgeBizModel | `extractLineageFromSql` |
| NopMetaLineageEdgeBizModel | `extractColumnLineageFromSql` |
| NopMetaLineageEdgeBizModel | `extractMeasureLineage` |
| NopMetaModuleBizModel | `importOrmModel` |
| NopMetaModuleBizModel | `importOrmModels` |
| NopMetaModuleBizModel | `releaseModule` |
| NopMetaModuleBizModel | `generateManifest` |
| NopMetaDataSourceBizModel | `testConnection` |
| NopMetaDataSourceBizModel | `syncExternalTables` |
| NopMetaDataSourceBizModel | `collectCatalog` |
| NopMetaDataSourceBizModel | `collectCatalogForTable` |
| NopMetaReconciliationConfigBizModel | `executeReconciliation` |
| NopMetaProfilingRuleBizModel | `executeProfilingRule` |
| NopMetaQualityRuleBizModel | `executeQualityRule` |
| NopMetaQualityRuleBizModel | `executeQualityRulesForDataSource` |
| NopMetaQualityScoreBizModel | `computeQualityScore` |
| NopMetaDataProductBizModel | `linkAsset` |
| NopMetaDataProductBizModel | `unlinkAsset` |
| NopMetaTagLabelBizModel | `propagateTags` |
| NopMetaTagLabelBizModel | `suggestTags` |
| NopMetaQualityResultBizModel | `approve` |
| NopMetaQualityResultBizModel | `reject` |

### 1.3 「是否含 catch」— catch 块目标集（静默吞异常族穷举面）

> 静默吞异常族的扫描面 = **全部 catch 块**（**125 个，分布在 45 个文件**，2026-08-15 live 重测），而非仅入口方法。下表为逐文件 catch 块分布（穷举目标集）。入口方法自身的 catch 由其所在文件的计数覆盖。
>
> **2026-08-15 计数漂移校正（plan `2026-08-15-0820-1` Phase 1 D3）**：2026-08-13 基线为 130 catch / 46 文件，live 重测 = **125 / 45**（漂移 −5）。原因：2026-08-14 死码/诊断退化清扫批次删除了带 catch 的死方法/死分支——F16 死方法 `NopMetaQualityRuleBizModel.resolveDataSourceOrThrow`（3→2）、F14/AR-14 批次（`TableReferenceExecutor` 6→5、`NopMetaDataSourceBizModel` 5→4、`SqlViewFieldTypeInferrer` 3→2、`NopMetaProfilingRuleBizModel` 1→0，commit `08b1ef556` 等）。**以 live 125 为准，不照抄旧值 130**。注意口径：rg 原始计数 126 含 1 处 javadoc 伪站点（`MetaTableProfiler.java:223` 注释内 `catch (SQLException)` 文字），门禁扫描器剥离注释后实扫 125。

| 文件（service 相对路径） | catch 块数 |
|---|---|
| search/NopMetaIndexBuilder.java | 11 |
| quality/MetaQualityRuleExecutor.java | 9 |
| quality/MetaQualityCheckpointScheduler.java | 7 |
| profiling/MetaTableProfiler.java | 7（rg 原始 8，含 1 javadoc 伪站点） |
| entity/NopMetaModuleBizModel.java | 6 |
| connection/MetaDataSourceConnectionProcessor.java | 6 |
| tableref/TableReferenceExecutor.java | 5 |
| query/AggregationHelper.java | 5 |
| entity/NopMetaQualityCheckpointBizModel.java | 5 |
| quality/MetaQualityCheckpointExecutor.java | 4 |
| entity/NopMetaDataSourceBizModel.java | 4 |
| entity/AutoClassificationProcessor.java | 4 |
| contract/MetaContractChecker.java | 4 |
| security/HostSecurityUtil.java | 3 |
| quality/CheckpointActionDispatcher.java | 3 |
| entity/NopMetaTagLabelBizModel.java | 3 |
| entity/NopMetaLineageEdgeQueryAction.java | 3 |
| entity/LineageTagPropagationProcessor.java | 3 |
| sync/ExternalTableStructureReader.java | 2 |
| sqlview/SqlViewFieldTypeInferrer.java | 2 |
| search/NopMetaSearchProcessor.java | 2 |
| query/MetaJoinExecutor.java | 2 |
| entity/NopMetaTableFilterBizModel.java | 2 |
| entity/NopMetaQualityRuleBizModel.java | 2 |
| tableref/MetaTableReferenceResolver.java | 1 |
| sqlview/SqlSelectFieldExtractor.java | 1 |
| reconciliation/LocalReconciliationProcessor.java | 1 |
| query/MetaTableQueryExecutor.java | 1 |
| query/MetaAggregationExecutor.java | 1 |
| query/JoinMixedSideResolver.java | 1 |
| query/ExternalAggregationProcessor.java | 1 |
| query/EntityEntityJoinAggregationProcessor.java | 1 |
| query/DefaultFilterApplicator.java | 1 |
| query/CrossDbFieldResolver.java | 1 |
| quality/QualityAlertWorkflowProcessor.java | 1 |
| quality/MetaQualityScorer.java | 1 |
| lineage/SqlSourceTableExtractor.java | 1 |
| lineage/SqlColumnLineageExtractor.java | 1 |
| field/MetaTableFieldResolver.java | 1 |
| event/MetaModelChangedEventPublisher.java | 1 |
| entity/NopMetaTableQueryAction.java | 1 |
| entity/NopMetaReconciliationResultBizModel.java | 1 |
| entity/NopMetaReconciliationConfigBizModel.java | 1 |
| entity/NopMetaGlossaryTermBizModel.java | 1 |
| entity/NopMetaEntityBizModel.java | 1 |
| **合计** | **125（45 文件）** |

复现：`rg -c "catch\s*\(" nop-metadata/nop-metadata-service/src/main/java | sort -t: -k2 -nr`（原始口径 126 = 125 真实 + 1 javadoc 伪站点）

### 1.4 limit 引用面（limit 负值校验族穷举面）

> limit 负值校验族的穷举目标 = **4 个显式接受 `limit` 入参的 public 方法**（§1.2 已标 ✅ limit）。service 下「引用 `limit`」的文件共 29 个，但大多为内部钳制/分页 helper，**入口拒绝点**只在这 4 个 public 入口方法上。

| 类 | 方法 | 入口拒绝现状（live） |
|---|---|---|
| NopMetaTableBizModel | `queryTableData` | `normalizeQueryLimit`：null/≤0 → 缺省值（静默封顶语义，MA7.4-03 裁定） |
| NopMetaTableBizModel | `queryJoinData` | `normalizeJoinQueryLimit`：`<0` → 抛 `ERR_PAGINATION_LIMIT_INVALID`（AR-09 已修） |
| NopMetaTableBizModel | `queryAggregation` | `normalizeJoinQueryLimit`：`<0` → 抛 `ERR_PAGINATION_LIMIT_INVALID`（AR-09 已修） |
| NopMetaSearchBizModel | `searchMetadata` | `<0` → 抛 `ERR_SEARCH_LIMIT_INVALID`（AR-23④ 已修，沿 AR-09 先例） |

复现（29 文件面）：`rg -l "limit" nop-metadata/nop-metadata-service/src/main/java | wc -l`
复现（4 入口方法）：`rg -n '@Name\("limit"\)' nop-metadata/nop-metadata-service/src/main/java`

### 1.5 敏感字面量脱敏族穷举面

> 目标 = error/log message 构造点（`.param(...)` / `LOG.info|warn|error(...)` 字面量入参）。历史命中点：`MetaDataSourceConnectionProcessor`（ARG_RAW_JDBC_URL）、`MetaQualityRuleExecutor`（SQL 字面量）、`MetaModelChangedEventPublisher`（Map 分支敏感列）。精确扫描面由 I1 的 `check-sensitive-literal-leak.mjs` 定义（敏感字面量 = raw JDBC URL / 内嵌 SQL 原文 / 凭据串）。

---

## §2 ORM 全集（39 entity × 37 unique-key）

### 2.1 39 entity 清单

> 复现：`rg -c '<entity ' nop-metadata/model/nop-metadata.orm.xml`（= 39）
> 全部 className = `io.nop.metadata.dao.entity.NopMeta*`。

NopMetaModule, NopMetaDataSource, NopMetaOrmModel, NopMetaSemanticType, NopMetaEntity, NopMetaEntityField, NopMetaEntityRelation, NopMetaEntityUniqueKey, NopMetaEntityIndex, NopMetaDomain, NopMetaDict, NopMetaDictItem, NopMetaTable, NopMetaTableDimension, NopMetaTableMeasure, NopMetaTableFilter, NopMetaTableJoin, NopMetaPipeline, NopMetaLineageEdge, NopMetaQualityRule, NopMetaQualityResult, NopMetaQualityCheckpoint, NopMetaManifest, NopMetaCatalog, NopMetaProfilingRule, NopMetaProfilingResult, NopMetaDataContract, NopMetaReconciliationConfig, NopMetaReconciliationResult, NopMetaReconciliationEntity, NopMetaQualityScore, NopMetaModelChangedEvent, NopMetaGlossary, NopMetaGlossaryTerm, NopMetaClassification, NopMetaTag, NopMetaTagLabel, NopMetaBusinessDomain, NopMetaDataProduct.

### 2.2 37 unique-key 清单（全部带 `constraint` + `columns`，0 缺失）

> 复现（计数）：`rg -c '<unique-key name=' nop-metadata/model/nop-metadata.orm.xml`（= 37）
> 复现（完整性，XML-aware，逐元素核对 constraint + columns）：见 §3 复现脚本 → 输出 `missing constraint or columns: 0`

| unique-key name | columns | constraint | 所属 entity |
|---|---|---|---|
| UK_NOP_META_MODULE_ID_VER | moduleId,moduleVersion | ✓ | NopMetaModule |
| UK_NOP_META_DS_QUERY_SPACE | querySpace | ✓ | NopMetaDataSource |
| UK_NOP_META_DATA_SOURCE_NAME | name | ✓ | NopMetaDataSource |
| UK_NOP_META_ORM_MODEL_MODULE_NAME | metaModuleId,modelName,isDelta | ✓ | NopMetaOrmModel |
| UK_NOP_META_SEM_TYPE_NAME | typeName | ✓ | NopMetaSemanticType |
| UK_NOP_META_ENTITY_MODEL_NAME | ormModelId,entityName | ✓ | NopMetaEntity |
| UK_NOP_META_FIELD_ENTITY_NAME | metaEntityId,fieldName | ✓ | NopMetaEntityField |
| UK_NOP_META_REL_ENTITY_NAME | metaEntityId,relationName | ✓ | NopMetaEntityRelation |
| UK_NOP_META_UK_ENTITY_NAME | metaEntityId,ukName | ✓ | NopMetaEntityUniqueKey |
| UK_NOP_META_IDX_ENTITY_NAME | metaEntityId,indexName | ✓ | NopMetaEntityIndex |
| UK_NOP_META_DOMAIN_MODEL_NAME | ormModelId,domainName | ✓ | NopMetaDomain |
| UK_NOP_META_DICT_MODEL_NAME | ormModelId,dictName | ✓ | NopMetaDict |
| UK_NOP_META_DICT_ITEM_VALUE | metaDictId,itemValue | ✓ | NopMetaDictItem |
| UK_NOP_META_TABLE_MODULE_NAME | metaModuleId,tableName,isDelta,metaSchema | ✓ | NopMetaTable |
| UK_NOP_META_DIM_TABLE_NAME | metaTableId,dimensionName | ✓ | NopMetaTableDimension |
| UK_NOP_META_MEASURE_TABLE_NAME | metaTableId,measureName | ✓ | NopMetaTableMeasure |
| UK_NOP_META_FILTER_TABLE_NAME | metaTableId,filterName | ✓ | NopMetaTableFilter |
| UK_NOP_META_JOIN_TABLE_ALIAS | metaTableId,alias | ✓ | NopMetaTableJoin |
| UK_NOP_META_PIPELINE_MODULE_NAME | metaModuleId,pipelineName | ✓ | NopMetaPipeline |
| UK_NOP_META_LINEAGE_EDGE_SRC_TGT_TYPE | sourceTableId,sourceColumn,targetTableId,targetColumn | ✓ | NopMetaLineageEdge |
| UK_NOP_META_QRULE_NAME | ruleName | ✓ | NopMetaQualityRule |
| UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE | checkpointId,runId,qualityRuleId | ✓ | NopMetaQualityResult |
| UK_NOP_META_QCHECKPOINT_NAME | checkpointName | ✓ | NopMetaQualityCheckpoint |
| UK_NOP_META_MANIFEST_MODULE_VER | metaModuleId,manifestVersion | ✓ | NopMetaManifest |
| UK_NOP_META_PROFRULE_TABLE_NAME | metaTableId,ruleName | ✓ | NopMetaProfilingRule |
| UK_NOP_META_CONTRACT_NAME | contractName | ✓ | NopMetaDataContract |
| UK_NOP_META_RECONCILIATION_CONFIG_NAME | configName | ✓ | NopMetaReconciliationConfig |
| UK_NOP_META_RECONCILIATION_ENTITY_ID | entityId,entityType | ✓ | NopMetaReconciliationEntity |
| UK_NOP_META_GLOSSARY_NAME | name | ✓ | NopMetaGlossary |
| UK_NOP_META_GLOSSARY_TERM_FQN | fullyQualifiedName | ✓ | NopMetaGlossaryTerm |
| UK_NOP_META_GLOSSARY_TERM_G_FQN | glossaryId,fullyQualifiedName | ✓ | NopMetaGlossaryTerm |
| UK_NOP_META_CLASSIFICATION_NAME | name | ✓ | NopMetaClassification |
| UK_NOP_META_TAG_FQN | fullyQualifiedName | ✓ | NopMetaTag |
| UK_NOP_META_TAG_CLS_FQN | classificationId,fullyQualifiedName | ✓ | NopMetaTag |
| UK_NOP_META_TAG_LABEL | entityType,entityId,tagId,source | ✓ | NopMetaTagLabel |
| UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME | parentDomainId,name | ✓ | NopMetaBusinessDomain |
| UK_NOP_META_DATA_PRODUCT_DOMAIN_NAME | businessDomainId,name | ✓ | NopMetaDataProduct |

**missing-constraint 条目数 = 0**（live 实测，非历史照抄）。

### 2.3 DDL 物化旁证

`nop-metadata/deploy/sql/{mysql,oracle,postgresql}/_create_nop-metadata.sql` 三方言均发射 `UNIQUE` 约束（mysql `_create` 含 42 处 `unique`、`_add_tenant` 含 41 处）。复现：`rg -ci 'unique' nop-metadata/deploy/sql/mysql/_create_nop-metadata.sql`。这印证 37/37 constraint 属性已穿过 DDL 发射门（`ddl.xlib:81-82`）物化为部署层约束。

---

## §3 复现脚本（unique-key 完整性 XML-aware 核对）

```bash
node -e '
const fs = require("fs");
const txt = fs.readFileSync("nop-metadata/model/nop-metadata.orm.xml","utf8");
const blocks = txt.match(/<unique-key\b[^>]*?\/>/gs) || [];
console.log("unique-key self-closing blocks matched:", blocks.length);
let missing = 0;
for (const b of blocks) {
  const name = (b.match(/name="([^"]+)"/)||[])[1];
  if (!/constraint=/.test(b) || !/columns=/.test(b)) {
    missing++;
    console.log("MISSING:", name);
  }
}
console.log("missing constraint or columns:", missing);
'
# 预期输出: matched 37, missing 0
```

---

## §4 目标方法的「已审计轮次」归属

> 尽量回溯到具体 audit-finding-ID；无法回溯的标「未单独审计」。完整证据链见 `arm-index-nop-metadata.md`。

| 失败族 | 已命中目标（audit-finding-ID） | 证据定位 |
|---|---|---|
| 静默吞异常 | P2-06 `AggregationHelper.java:496-506`（R6.4 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:101` |
| 静默吞异常 | P2-07 `NopMetaModuleBizModel.java:217-234`（R6.4 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:102` |
| 静默吞异常 | P2-09 `NopMetaTagLabelBizModel.java:128-129`（R6.4 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:103` |
| 静默吞异常 | P2-01 `NopMetaSearchProcessor.java:56-66/:77-87`（R6.5 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:104` |
| 静默吞异常 | P2-02 `AutoClassificationProcessor.java:129-134`（R6.5 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:105` |
| 静默吞异常 | P2-04 `MetaQualityCheckpointExecutor.java:349-358` 等 4 处（R6.5 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:106` |
| 静默吞异常 | AR-21 `AutoClassificationProcessor`+`LineageTagPropagationProcessor.java:177-188`（R8.4a fixed） | `ai-dev/audits/arm-index-nop-metadata.md:45` |
| limit 负值 | AR-09 `NopMetaTableBizModel` queryJoinData/queryAggregation（R7.3 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:130` |
| limit 负值 | AR-23④ `NopMetaSearchBizModel.java:66-70` searchMetadata（R8.2 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:51` |
| 敏感字面量 | P2-12 `MetaDataSourceConnectionProcessor.java:225-247` ARG_RAW_JDBC_URL（R6.2 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:98` |
| 敏感字面量 | AR-16 `MetaQualityRuleExecutor.java:631,647,669` SQL→sqlHash（R8.2 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:36` |
| 敏感字面量 | AR-23⑩ `MetaModelChangedEventPublisher.java:201-203` Map 分支（R8.4b fixed） | `ai-dev/audits/arm-index-nop-metadata.md:54` |
| unique-key constraint | MA7.3-01 / P2-MA6.6-001 / R3.19（36 UK 补 constraint，已 fixed） | `ai-dev/audits/arm-index-nop-metadata.md:300` + `ai-dev/lessons/09-ddl-unique-key-silent-absence.md` |
| 虚假关闭（元） | AR-06（R3.14 虚假关闭 → R7.3 实修） | `ai-dev/audits/arm-index-nop-metadata.md:129` |

**未单独审计**：其余 service/processor 内部方法（非 `@BizQuery`/`@BizMutation` 入口）的 catch 块——这是 I2 对抗探查要补的盲区面（修实例不修类别的复发温床）。

---

## §5 Cycle 2 silent-wrong-result 目标集（plan `2026-08-15-0820-1` Phase 1 D3）

> 2026-08-15 live 实测。目标面 = `nop-metadata-service/src/main/java` 全部 main 代码（与 INV-SILENT-SWALLOW 同面）。权威计数以门禁扫描器 `check-silent-wrong-result.mjs` 为准（注释/字符串剥离）；rg 命令为辅助复核（不剥离注释，含伪站点——逐节注明差异）。

### 5.1 INV-LOCALE — 默认 locale case-mapping 站点

**live 计数 = 40 站点 / 10 文件**（扫描器口径；rg 原始 41 含 1 处 javadoc 伪站点 `LocalReconciliationProcessor.java:124`）。

| 文件（service 相对路径） | 站点数 |
|---|---|
| lineage/SqlColumnLineageExtractor.java | 10 |
| profiling/MetaTableProfiler.java | 7 |
| connection/MetaDataSourceConnectionProcessor.java | 5 |
| entity/NopMetaLineageEdgeQueryAction.java | 5 |
| quality/CheckpointActionDispatcher.java | 4 |
| quality/MetaQualityRuleExecutor.java | 4 |
| lineage/SqlSourceTableExtractor.java | 2 |
| catalog/MetaCatalogCollector.java | 1 |
| contract/MetaContractChecker.java | 1 |
| sync/ExternalTableStructureReader.java | 1 |
| **合计** | **40（10 文件）** |

复现（rg 原始口径 41）：`rg '\.(toLowerCase|toUpperCase)\(\)' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*' | wc -l`
复现（扫描器口径 40，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule locale --format json | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>console.log(JSON.parse(d).hitCount))'`

### 5.2 INV-NARROW — narrowing-cast 站点（防回退，预期零）

**live 计数 = 0**（防回退零点）。live 仅存 2 处 `(long)` 强转（`MetaContractChecker.java:384/:395`）均为 AR-01 修复后的正确形态（操作数加括号、先乘后取整），不命中；`(long) from + limit` 等 widening 场景（int/long 操作数）不命中。

复现（候选面，需人工区分 widening）：`rg -n '\((?:long|int|short)\)\s*[A-Za-z_$][A-Za-z0-9_$.]*\s*(?:\*|/|%|\+|-)' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*'`
复现（扫描器口径 0，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule narrowing-cast`

### 5.3 INV-CONTAINS-CLASSIFY — String-receiver `.contains(` 站点

**live 计数 = 19 站点（14 行）/ 6 文件**。

| 文件（service 相对路径） | 站点数 | 语义初判（终判归 I3'） |
|---|---|---|
| profiling/MetaTableProfiler.java | 9（:276×3/:277×2/:278×2 AR-06 消息线索；:567 `isStringType` 子串分类——AR-05 兄弟残留；:595 派生列名标记） | 混合 |
| sync/ExternalTableStructureReader.java | 2（:148 方言分类 `p.contains("mysql")`） | 分类 |
| field/ExpressionMeasureValidator.java | 2（:223/:237 限定名判定 `ident.contains(".")`） | 解析 |
| quality/MetaQualityRuleExecutor.java | 4（:388/:391 sandbox 探测；:646 派生列名标记；:810 消息线索） | 混合 |
| connection/MetaDataSourceConnectionProcessor.java | 1（:246 危险参数 blocklist `lower.contains(dangerous)`） | 安全探测 |
| entity/NopMetaModuleBizModel.java | 1（:358 delta 判定 `sourceContent.contains("x:extends")`） | 结构探测 |
| **合计** | **19（6 文件）** | |

> 检测边界（诚实声明）：receiver 为方法调用（如 `resolveAllowedWebhookHosts().contains(...)`、`:326` 集合 receiver）不命中——静态不可判定其元素语义；集合类型 receiver（`Set/List` 声明）全等查询不命中。

复现（粗筛 `.contains(` 全量 58 处，含集合 receiver）：`rg -n '\.contains\(' nop-metadata/nop-metadata-service/src/main/java --glob '*.java' | wc -l`
复现（扫描器口径 19，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule contains-classify`

### 5.4 INV-DELIM-KEY — 分隔符拼接复合键站点

**live 计数 = 6 站点 / 3 文件**（2026-08-15 扫描器实测；初测 rg 粗筛 3 处为引用转义遗漏，以扫描器为准）。

| 文件:行 | 形态 |
|---|---|
| entity/LineageTagPropagationProcessor.java:85 | `visited.add(entityType + "#" + entityId)` —— 内联拼接直接作 Set 键 |
| entity/LineageTagPropagationProcessor.java:136 | `String visitKey = ENTITY_TYPE_NOP_META_TABLE + "#" + targetId` —— *Key 变量拼接 |
| entity/LineageTagPropagationProcessor.java:152 | `visited.contains(ENTITY_TYPE_NOP_META_TABLE + "#" + ...)` —— 成员查询内联拼接 |
| entity/AutoClassificationProcessor.java:144 | `String warnKey = getClassificationId() + "\|" + pattern` —— *Key 变量拼接 |
| entity/NopMetaLineageEdgeQueryAction.java:258 | `String key = sourceId + "\|" + sourceColumn + "\|" + targetColumn` —— 三段 *Key 变量拼接 |
| entity/NopMetaLineageEdgeQueryAction.java:487 | `map.put(sourceTableId + "\|" + sourceColumn + "\|" + targetColumn, e)` —— 三段内联拼接直接作 map 键 |

> AR-03 修复点（group-key 改结构性键）不命中。检测边界：lambda key extractor 内联拼接需流分析，不命中（watch 边界）。

复现（rg 转义注意，`|` 须双转义）：`rg -n '\+ "\\\|" \+' nop-metadata/nop-metadata-service/src/main/java --glob '*.java'`
复现（扫描器口径 6，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule delim-key`

复现（拼接形态粗筛）：`rg -n '\+ "(\\||:|;|,|#|@)" \+' nop-metadata/nop-metadata-service/src/main/java`
复现（扫描器口径 6，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule delim-key`

### 5.5 INV-BIGDEC — Number→BigDecimal 经 double 站点

**live 计数 = 2 站点 / 2 文件**。

| 文件:行 | 形态 |
|---|---|
| query/MemoryOrderByComparator.java:132 | `BigDecimal.valueOf(((Number) v).doubleValue())` —— 无整数路由 + String 静默返回 null |
| query/MemoryFilterEvaluator.java:356 | 同上（私有拷贝） |

> **受保护正确形态（不命中）**：`query/AggregationHelper.java:554` `java.math.BigDecimal.valueOf(n.doubleValue())` —— 所在 `toBigDecimal` 方法体含 `.longValue()` 整数路由信号（`:551`，AR-10 修复形态）。朴素 pattern 扫描（命中 3 处）与基线 2 处的矛盾由此消除：检测规则按「方法体含 longValue 路由信号」显式排除。

复现（`BigDecimal.valueOf` 全量 5 处，含受保护形态）：`rg -n 'BigDecimal\.valueOf' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*'`
复现（扫描器口径 2，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule bigdec-precision`

### 5.6 汇总

| 子族 | live 站点 | 文件数 | 门禁规则 |
|---|---|---|---|
| locale | 40 | 10 | `locale` |
| narrowing-cast | 0（防回退） | 0 | `narrowing-cast` |
| contains-classify | 19 | 6 | `contains-classify` |
| delim-key | 6 | 3 | `delim-key` |
| bigdec-precision | 2 | 2 | `bigdec-precision` |
| **合计** | **67** | 16（去重） | 单门禁 5 规则 |

---

## 引用

- `ai-dev/audits/arm-index-nop-metadata.md`（nop-metadata 审计索引，全部 finding-ID 可定位）
- `ai-dev/lessons/09-ddl-unique-key-silent-absence.md`（unique-key constraint 族 lesson）
- `nop-metadata/model/nop-metadata.orm.xml`（ORM 全集 source of truth）
- `nop-metadata/nop-metadata-service/src/main/java/`（方法全集 source of truth）
- `initial-red-list-cycle2.md` + `baseline-cycle2/`（Cycle 2 初始快照与对账基线，plan `2026-08-15-0820-1` Phase 2 交付）
