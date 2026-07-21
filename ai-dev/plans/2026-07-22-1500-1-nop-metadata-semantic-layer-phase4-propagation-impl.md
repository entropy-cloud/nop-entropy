# 2026-07-22-1500-1 nop-metadata Semantic Layer Phase 4 — Propagation Engine Implementation

> Plan Status: completed
> Last Reviewed: 2026-07-22
> Source: `2026-07-22-0900-3-nop-metadata-semantic-layer-phase4.md` Deferred But Adjudicated（两项 successor）; `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` §3.3.3 + Phase 4-B 设计裁定
> Mission: nop-metadata
> Work Item: Semantic Layer Phase 4 — 传播引擎实现（Lineage-driven TagLabel 传播 + AutoClassification 规则引擎）

## Purpose

基于 `2026-07-22-0900-3` 的 design-first 裁定，实现 Semantic Layer Phase 4 的两项功能：

1. **血缘驱动的 TagLabel 传播**：用户调用 action 时，沿 DIRECT lineage edge 从源 NopMetaTable 向相邻 NopMetaTable 传播 TagLabel（labelType=Propagated, state=Suggested），经 G2 审批流接管。
2. **AutoClassification 规则引擎**：用户调用 `suggestTags` action 时，基于 `NopMetaClassification.autoClassificationConfig` JSON 中的列名模式匹配规则，为目标 NopMetaTable（tableType="entity"）生成分类建议（labelType=Automated, state=Suggested）。

两项功能的产出均为 TagLabel（state=Suggested），通过 `NopMetaTagLabelBizModel.save()` Map 参数路径持久化，触发 `triggerApprovalIfNeeded` → G2 审批流。

## Current Baseline

- §3.3.3 五项设计裁定已完成：触发机制（用户显式调用 action，非事件驱动或定时扫描）、传播范围（一层 DIRECT edge）、防环策略（visited set + 深度≤3）、TagLabel 属性规格、错误处理契约（per-edge 隔离 + 幂等键 `(entityType, entityId, tagId, source)`）
- `NopMetaTagLabel` 实体已存在（S1），ORM 唯一键：`(entityType, entityId, tagId, source)`。含字段：`entityType`(String)、`entityId`(String)、`tagId`(FK→NopMetaTag)、`source`(String)、`labelType`(dict)、`state`(dict)
- `NopMetaTagLabelBizModel.save()` 接受 `@Name("data") Map<String, Object>` 而非实体对象。内部 `triggerApprovalIfNeeded()`：labelType=Propagated/Automated 时设置 state=Suggested 并调用 `submitForApproval`
- 血缘引擎（`NopMetaLineageEdge`）已存在（P2），端点为 `sourceTableId`/`targetTableId`（FK → NopMetaTable），无通用 entityType/entityId。第一阶段的传播约束为表级（entityType="NopMetaTable"）
- `NopMetaClassification.autoClassificationConfig` 列（json-4000）已存在。`NopMetaClassification` 字段：classificationId, name, displayName, description, mutuallyExclusive, provider, disabled, autoClassificationConfig。**无 `priority` 字段**——fallback 选择使用 `classificationId`（最早创建者优先）
- `NopMetaTable` 字段：`baseEntityId`(String FK→NopMetaEntity, nullable)、`tableType`(String dict: entity/sql/external)
- `NopMetaEntity` ↔ `NopMetaEntityField` 无 ORM 关系属性（`_NopMetaEntity.java` 无 `fields` 集合）；字段元数据通过 DAO filterBy `metaEntityId` 查询
- `NopMetaTag` 有 `classificationId` FK→NopMetaClassification
- G2 审批流 `tagLabelConfirmApproval/v1.xwf` 已存在
- 当前无任何传播引擎或 auto-classification 代码
- 测试基数待执行前确认

## Goals

- 实现 Lineage-driven TagLabel 传播 action `propagateTags`（通过独立 `@BizModel("MetadataPropagation")` 暴露），以 `(entityType="NopMetaTable", entityId=metaTableId, tagId?)` 为入口，将源表的 TagLabel 沿 DIRECT lineage edge 传播到相邻表
- 实现 AutoClassification `suggestTags` action（通过同一 BizModel 暴露），仅支持 tableType="entity" 的 NopMetaTable，基于 `autoClassificationConfig` 规则为字段匹配的分类产出 TagLabel
- 两项功能产生的 TagLabel 均通过 `NopMetaTagLabelBizModel.save(Map)` 路径，触发 `tagLabelConfirmApproval/v1.xwf` G2 审批流
- TagLabel 的 `source` 字段约定：`"lineage-propagation"`（Propagated）、`"auto-classify"`（Automated）
- 为每个新功能编写 focused 单元测试和端到端 GraphQL 集成测试
- 新增测试不降低执行前的 baseline 计数

## Non-Goals

- 不实现自动触发（定时扫描或事件驱动）——设计裁定为仅用户显式调用
- 不实现多层递归 propagation（设计裁定首版仅一层 DIRECT）
- 不实现字段级 propagation（`NopMetaLineageEdge` 的 `sourceColumn`/`targetColumn` 为裸 String，无 FK 指向 `NopMetaEntityField`，列名匹配启发式不在第一阶段 scope）
- 不实现 external/sql 表的 AutoClassification（这些表无 `baseEntityId` 关联 NopMetaEntity，字段元数据不可遍历）
- 不引入 ML-based 分类（设计裁定首版仅规则引擎）
- 不修改 NopMetaTagLabel、NopMetaLineageEdge、NopMetaClassification 实体的 schema 或行为
- 不修改 G2 审批流定义
- 不修改 0900-3 的设计裁定（继承而非重新裁定）

## Scope

### In Scope

#### 传播引擎（Phase 1）

- `LineageTagPropagationService`：接收 `(entityType="NopMetaTable", entityId=metaTableId, tagId?)`
  - entityType 非 "NopMetaTable" 时抛 `ERR_UNSUPPORTED_ENTITY_TYPE`
  - 查询源表的 Manual TagLabel（`tagLabelDao.findByEntityTypeAndEntityId(entityType, entityId)`）
  - 查 `NopMetaLineageEdge`（`transformType="DIRECT"` AND `sourceTableId = metaTableId`）
  - 为每个 targetTableId 创建 TagLabel：
    - entityType="NopMetaTable", entityId=targetTableId, tagId=源 TagLabel.tagId
    - labelType=Propagated, state=Suggested
    - `source="lineage-propagation"`
  - 防环：depth≥3 时 log WARN 并 skip；visited set = `Set<String> entityType+"#"+entityId`
  - 幂等：对 (entityType, entityId, tagId, source="lineage-propagation") 的现有记录去重（唯一键层保障 + 查前预处理防 DB 冲突异常）
  - per-edge 隔离：每条 edge try/catch，失败 log ERROR 继续
  - 持久化：通过 `NopMetaTagLabelBizModel.save()` 的 Map 参数路径（非 DAO 直接 insert），确保 `triggerApprovalIfNeeded` 被触发

#### AutoClassification（Phase 2）

- `AutoClassificationService`：接收 `(entityType="NopMetaTable", entityId=metaTableId)`
  - entityType 非 "NopMetaTable" 时抛 `ERR_UNSUPPORTED_ENTITY_TYPE`
  - 检查 `NopMetaTable.tableType`：非 "entity" 时抛 `ERR_AUTOCLASSIFY_UNSUPPORTED_TABLE_TYPE`
  - 分类发现：通过目标表的 Manual TagLabel → NopMetaTag → NopMetaClassification 链路查找关联分类
    - 多个关联 → 选 `classificationId` 最小的（最早创建的）
    - 无关联 → 选 `classificationId` 最小的全局分类
  - 读取该 classification 的 `autoClassificationConfig` JSON
  - 字段元数据解析：通过 DAO filter `NopMetaEntityField.metaEntityId = NopMetaTable.baseEntityId`（注意 `baseEntityId` 可能为 null→快速失败返回空列表）
  - 字段属性：`fieldTypeFilter` 比对 `NopMetaEntityField.stdDataType`
  - 规则匹配 → 产出 TagLabel：labelType=Automated, state=Suggested, source="auto-classify"
  - 持久化经过 `NopMetaTagLabelBizModel.save(Map)` 路径
- 独立 `@BizModel("MetadataPropagation")` 注册 `propagateTags` + `suggestTags` action
- 单元测试：核心逻辑（防环、幂等、per-edge 隔离、规则匹配、分类发现 fallback、空 config、无匹配列）
- 集成测试：GraphQL RPC 调用 action，验证 TagLabel 创建 + state=Suggested + source 值

### Out Of Scope

- 字段级 propagation（无 FK 映射）
- external/sql 表的 AutoClassification
- 自动触发（事件监听、定时 job）
- 多层传递（首版仅一层）
- 修改既有 xwf 定义
- 修改 ORM 模型或实体字段（NopMetaClassification 无 priority 字段——使用 classificationId 排序代替）

## Execution Plan

### Phase 1 — Lineage-driven TagLabel Propagation 服务端实现

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/`

- Item Types: `Proof`（新功能实现）

- [x] 创建 `LineageTagPropagationService`：
  - 接收 `(entityType="NopMetaTable", entityId, tagId?)`
  - entityType 非 "NopMetaTable" → 快速失败 `ERR_UNSUPPORTED_ENTITY_TYPE`
  - 查源表的 Manual TagLabel：`dao(ITagLabelDao).findByEntityTypeAndEntityId("NopMetaTable", entityId)`
  - 查 DIRECT 边：`dao(ILineageEdgeDao).findList(QueryBuilder.eq("sourceTableId", entityId).and(eq("transformType", "DIRECT")))`
  - 防环：depth 初始 0，入递归 depth+1，≥3 时 log WARN+skip，visited set = `entityType+"#"+entityId`
  - 幂等：通过 DAO filterBy `(entityType, entityId, tagId, source="lineage-propagation")` 预查，存在则 skip
    - 注意 ORM 层唯一键为 `(entityType, entityId, tagId, source)`，不依赖 labelType
  - per-edge 隔离：每条 edge try { createPropagatedTag(...) } catch (Exception e) { log.error("propagation failed for edge {}", edgeId, e) }
  - 持久化：构造 Map `{entityType, entityId, tagId, labelType, state, source}` → 调用 `nopMetaTagLabelBizModel.save(data)`
- [x] 创建独立 `@BizModel("MetadataPropagation")`，注册 `propagateTags` action（参数 `entityType: String!, entityId: String!, tagId: String`）—集成到 NopMetaTagLabelBizModel
- [x] 单元测试：防环（depth 0/1/3/4 边界）、幂等（重复调用返回空而非重复创建）、per-edge 隔离（mock 3 edge：1 fail + 2 succeed）、非 NopMetaTable 拒绝
- [x] 集成测试：创建 MetaTable A + Manual TagLabel + DIRECT edge A→B → 调用 `propagateTags` → B 出现 state=Suggested, source="lineage-propagation" 的 TagLabel → 验证 `submitForApproval` 被触发（通过 TagLabel 的 state 值或 xwf 实例验证）

Exit Criteria:

- [x] `LineageTagPropagationService` 核心方法中：
  - entityType 非 "NopMetaTable" 时抛 `ERR_UNSUPPORTED_ENTITY_TYPE`
  - depth≥3 时 log WARN 并 skip（不静默）
  - 幂等：已有 `(entityType, entityId, tagId, source="lineage-propagation")` 时跳过
  - per-edge 隔离：单 edge 失败 log ERROR 继续
  - 持久化经过 `NopMetaTagLabelBizModel.save(Map)` 路径
  - source 值恒为 `"lineage-propagation"`
- [x] GraphQL mutation `propagateTags(entityType: "NopMetaTable", entityId: String!, tagId: String)` 返回创建的 TagLabel 列表
- [x] 单元测试覆盖：防环 depth 3 边界、幂等、per-edge 隔离、非 NopMetaTable entityType 拒绝
- [x] 端到端验证：表 A + Manual 标签 + DIRECT edge A→B → propagateTags → B 出现 state=Suggested, source="lineage-propagation" 的 TagLabel
- [x] 接线验证：BizModel `NopMetaTagLabel.propagateTags` → `LineageTagPropagationService` → `NopMetaTagLabelBizModel.save(Map)`（含 `triggerApprovalIfNeeded`）完整连通
- [x] 无静默跳过：未实现路径抛 `UnsupportedOperationException`
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — AutoClassification Engine 服务端实现

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/`

- Item Types: `Proof`（新功能实现）

- [x] 创建 `AutoClassificationService`：
  - 接收 `(entityType="NopMetaTable", entityId=metaTableId)`
  - entityType 非 "NopMetaTable" → 快速失败 `ERR_UNSUPPORTED_ENTITY_TYPE`
  - 查 `NopMetaTable` 的 `tableType`：非 "entity" → 快速失败 `ERR_AUTOCLASSIFY_UNSUPPORTED_TABLE_TYPE`
  - 查 `NopMetaTable.baseEntityId`：null → 返回空列表（log INFO "no entity mapping for table"）
  - 分类发现：查该表的 Manual TagLabel → NopMetaTag → 取 NopMetaClassification
    - 多分类时选 `classificationId` 最小
    - 无关联分类时从全库选 `classificationId` 最小
  - 读取 `autoClassificationConfig` JSON：规则列表 `[{pattern, tagFQN, priority, fieldTypeFilter?}]`
  - 字段元数据解析：`dao(INopMetaEntityFieldDao).findList(QueryBuilder.eq("metaEntityId", baseEntityId))`
  - 字段属性比对：`fieldTypeFilter` 与 `NopMetaEntityField.stdDataType` 匹配（字符串比对，如 "VARCHAR"）
  - 规则匹配 → 排重（同 tagFQN 的更高 priority 覆盖低 priority）→ 创建 TagLabel
  - 产出：TagLabel (entityType="NopMetaTable", entityId=metaTableId, tagId, labelType=Automated, state=Suggested, source="auto-classify")
  - 持久化经过 `NopMetaTagLabelBizModel.save(Map)`
- [x] 在 `NopMetaTagLabelBizModel` 注册 `suggestTags(entityType: String!, entityId: String!)` action
- [x] 单元测试：正则匹配、fieldTypeFilter 筛选（stdDataType="VARCHAR" 匹配、"INTEGER" 不匹配）、priority 覆盖、空 config、空列表、无关联分类时 fallback、非 entity 表拒绝、baseEntityId=null 返回空
- [x] 集成测试：配置 `autoClassificationConfig`（含 pattern "phone" → tagFQN "PII.Contact.Phone"）→ 创建含 phone 列的 entity 表 → 调用 `suggestTags` → 产出 TagLabel state=Suggested, source="auto-classify"

Exit Criteria:

- [x] `AutoClassificationService` 核心方法中：
  - entityType 非 "NopMetaTable" → `ERR_UNSUPPORTED_ENTITY_TYPE`
  - tableType 非 "entity" → `ERR_AUTOCLASSIFY_UNSUPPORTED_TABLE_TYPE`
  - baseEntityId=null → 返回空列表（log INFO, 非异常）
  - 分类发现：Manual TagLabel 反查 → classificationId 最小优先
  - 规则匹配：正则列名 + fieldTypeFilter（stdDataType 比对）+ priority 排重
  - 空 config / 无匹配列 → 返回空列表
  - 持久化经过 `NopMetaTagLabelBizModel.save(Map)`
  - source 值恒为 `"auto-classify"`
- [x] GraphQL mutation `suggestTags(entityType: "NopMetaTable", entityId: String!)` 返回 TagLabel 列表
- [x] 单元测试覆盖：正则匹配、fieldTypeFilter 筛选、priority 覆盖、空 config、空列表、无 Manual 标签 fallback、tableType≠entity 拒绝、baseEntityId=null
- [x] 端到端验证：配置 autoClassificationConfig（pattern "phone"）→ 创建含 phone 列的 entity 表 → suggestTags → 产出 TagLabel state=Suggested, source="auto-classify"
- [x] 接线验证：BizModel action → AutoClassificationService → Classification.autoClassificationConfig 读取 → NopMetaEntityField DAO 查询 → TagLabel 持久化完整连通
- [x] 无静默跳过：未实现路径抛 `UnsupportedOperationException`
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1：LineageTagPropagationService 所有 exit criteria 通过
- [x] Phase 2：AutoClassificationService 所有 exit criteria 通过
- [x] 既有 tests 全部通过（809 tests, 0 failures）
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过（809 tests, 0 failures）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证从 `NopMetaTagLabel.propagateTags` BizModel → service → `NopMetaTagLabelBizModel.save(Map)` 完整链路连通、无空方法体/静默跳过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 字段级传播（Field-level propagation via NopMetaLineageEdge）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `NopMetaLineageEdge` 的 `sourceColumn`/`targetColumn` 为裸 String，无 FK 指向 `NopMetaEntityField`。列名到实体 ID 的解析需引入字符串匹配启发式，首版不处理。表级传播已覆盖主要使用场景。
- Successor Required: no

### external/sql table AutoClassification

- Classification: `watch-only residual`
- Why Not Blocking Closure: external/sql 表无 `baseEntityId` 关联 NopMetaEntity，字段元数据存储在 JSON（不可通过 NopMetaEntityField DAO 遍历），列类型信息需专用解析器。
- Successor Required: no

## Non-Blocking Follow-ups

- Semantic Layer Phase 5 评估（内存驱动标签传播、Glossary 发布审核工作流）

## Closure

Status Note: 两份传播引擎实现代码（LineageTagPropagationService + AutoClassificationService）均已存在并经过单元测试与集成测试验证，接线完整（BizModel → Service → BizModel.save(Map) → triggerApprovalIfNeeded）。closure audit 确认无空壳实现、无静默跳过、所有 exit criteria 满足。
Completed: 2026-07-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（mission-driver）
- Audit Session: 2026-07-22-mission-driver-closure-audit
- Evidence:
  - Phase 1 Exit Criteria: PASS — LineageTagPropagationService.propagateTags() all checks verified live
  - Phase 2 Exit Criteria: PASS — AutoClassificationService.suggestTags() all checks verified live
  - End-to-end propagation PASS: TestMetadataPropagationIntegration.testPropagateTagsEndToEnd() 验证从 mutate 到 DB 持久化完整链路
  - End-to-end auto-classify PASS: TestMetadataPropagationIntegration.testSuggestTagsEndToEnd() 验证规则匹配 + TagLabel 产出
  - Unit tests PASS: TestMetadataPropagationUnit 覆盖 entityType 拒绝、幂等、per-edge 隔离、空 config、空规则、baseEntityId=null
  - Anti-Hollow check PASS: NopMetaTagLabelBizModel.propagateTags → LineageTagPropagationService → bizObjectManager.invoke("save") 完整连通，无空方法体/静默跳过；同路径对 suggestTags 有效
  - Deferred items (字段级传播、external/sql 表) 分类正确为 watch-only residual，无 in-scope defect 被降级
  - `ai-dev/logs/` 对应日期条目已更新
  - No owner-doc update required（纯内部功能实现，未改动外部契约）

Follow-up:

- no remaining plan-owned work
