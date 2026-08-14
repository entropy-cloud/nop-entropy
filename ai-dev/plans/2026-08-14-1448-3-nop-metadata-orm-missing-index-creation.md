# nop-metadata ORM 缺失索引补建（F10 / F11 / F12）

> Plan Status: active
> > Last Reviewed: 2026-08-14
> > Mission: nop-metadata-invariant-loop
> > Work Item: Cycle 2 / 再审计 follow-up backlog — ORM 模型族（性能）
> > Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（F10/F11/F12）；审计源 `ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md`（F10/F11/F12）
> > Related: `2026-08-14-1448-1-...`（代码卫生）、`2026-08-14-1448-2-...`（safeProductName/诊断）。本计划 N=3 排最后：ORM 模型变更为 plan-first（AGENTS.md Protected Area），且 mission 授权要求"ORM 模型变更执行前人工确认"，故执行阶段需人工放行；草稿可先经独立 review 达共识。
> >
> > **范围变更说明（审查 Blocker-1）**：roadmap 原把 F13（quality-trend-direction dict 缺"retained for Java constants"注释）归入本族。独立审查核实 **F13 为 false positive**——`nop-metadata.orm.xml:104-105` 注释"以下两个 dict 无 column ext:dict 引用，但 Java 代码通过生成的常量引用其值，因此保留定义"已**同时覆盖** `checkpoint-action-type`（:106）与 `quality-trend-direction`（:111），注释早已存在；且"retained"注释只存在于 orm.xml 的 XML 注释，不在 `.dict.yaml`。故 F13 从本计划移除（见 Out Of Scope）。

## Purpose

为 nop-metadata ORM 模型中缺失索引的审计日志/软外键/批次键查询补建索引（F10-F12）。收口后：按 `entityType+entityId` 查审计事件、按软外键反查、按 `runId` 查质量结果不再全表扫描。全部为**源模型 `model/nop-metadata.orm.xml` 加性/扩列变更**（新增/扩展 `<index>`，不改列、不改 UK、不删字段），低风险。

## Current Baseline

> 事实为 2026-08-14 live repo 实测（`nop-metadata/model/nop-metadata.orm.xml` 源模型），独立审查逐实体 `<indexes>` 块已确认。

- **F10 NopMetaModelChangedEvent.entityId 无索引**：实体（:2857）有列 `entityId`（:2872）。现有 `<indexes>`（:2911-2922）：`IX_NOP_META_EVENT_TYPE_TIME(entityType,changeTime)`、`IX_NOP_META_EVENT_SOURCE(changeSource)`、`IX_NOP_META_EVENT_TX(transactionId)`——**entityId 不在任何索引**。审计日志高频查询 `WHERE entityType=? AND entityId=?` 走全表扫描。兄弟实体约定：`NopMetaQualityRule` 用 `IX_NOP_META_QRULE_ENTITY(entityType, entityId)`、`NopMetaTagLabel` 用 `IX_NOP_META_TAG_LABEL_ENTITY(entityType, entityId)`——均 `entityType` 前导组合。
- **F11 四个软外键列无索引**（逐实体 `<indexes>` 已确认无）：
  - `sourceModuleId`（NopMetaDomain，:1065）：索引仅 `IX_NOP_META_DOMAIN_MODEL(ormModelId)`。
  - `baseEntityId`（NopMetaTable，:1287）：`<indexes>`（:1434-1446）3 个索引均无 baseEntityId；UK（:1316-1319）亦无。
  - `entityFieldId`（NopMetaTableDimension，:1467）：索引 `IX_NOP_META_DIM_TABLE(metaTableId)` + `IX_NOP_META_DIM_BUSINESS_DOMAIN(businessDomainId)`，无 entityFieldId。
  - `entityFieldId`（NopMetaTableMeasure，:1545）：`<indexes>`（:1593-1600）2 个索引均无 entityFieldId；UK（:1601-1604）亦无。
- **F12 NopMetaQualityResult.runId 不在任何索引前导列**：实体（:2039 起）有列 `runId`（:2086）。索引仅 `IX_NOP_META_QRESULT_RULE(qualityRuleId,executeTime)`；UK `UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE(checkpointId,runId,qualityRuleId)` 中 runId 是**第二列**（非前导）。按 runId 单独查（无 checkpointId 限定）无法用此 UK，走全表扫描。
- **生成产物**：`nop-metadata-dao/.../_app.orm.xml` 为生成产物（`_` 前缀），**不得手编**；改源模型后 `./mvnw install` 重新生成。索引新增/扩展后 DDL 生成会增加/修改对应 `CREATE INDEX`。

## Goals

- F10：扩展 `IX_NOP_META_EVENT_TYPE_TIME` 由 `(entityType, changeTime)` → `(entityType, entityId, changeTime)`（覆盖 entityType+entityId 查询，与兄弟实体约定一致，避免冗余第二索引）。
- F11：四个软外键列各新增单列索引。
- F12：`NopMetaQualityResult` 新增以 `runId` 为前导列的索引 `(runId, executeTime)`。
- 全部经源模型变更 + 重新生成 + 构建测试全绿。

## Non-Goals

- **重设计实体结构 / 列 / UK** —— 仅加索引，不改列定义、不删字段、不改 UK。
- **F13（dict 注释）** —— false positive（见上方范围变更说明），移出本计划。
- **F14-F19 / F17 / AR-14** —— 归计划 1 / 计划 2。
- **数据迁移 / 回填** —— 索引新建由 DDL 自动完成，无数据迁移。
- **全仓其它模块的索引治理** —— 仅 nop-metadata。

## Scope

### In Scope

- F10/F11/F12：在 `model/nop-metadata.orm.xml` 源模型为缺失索引补建（1 扩展 + 4 新增 + 1 新增 = 6 处）。
- 重新生成 `_app.orm.xml` + 构建测试验证。

### Out Of Scope

- 实体结构/列/UK 变更。
- F13（false positive，见范围变更说明）。
- 其它 follow-up backlog 项。

## Execution Plan

### Phase 1 — 缺失索引补建（F10 + F11 + F12）

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`（源模型，非 `_app.orm.xml` 生成产物）

- Item Types: `Fix | Decision`

> **ORM 源模型变更 = plan-first + 人工确认（mission 授权）**：只改 `model/nop-metadata.orm.xml`，改后 `./mvnw install -pl nop-metadata -am -DskipTests` 重新生成 `_app.orm.xml` / `_gen`，再跑测试。**禁止手编 `_app.orm.xml` 或 `_gen/`**。
>
> **索引设计裁定（审查 Major-1/Major-2）**：
> - **F10**：**扩展** `IX_NOP_META_EVENT_TYPE_TIME` 的列清单由 `(entityType, changeTime)` → `(entityType, entityId, changeTime)`（在两列间插入 `entityId`）。理由：审计日志查询模式为 `WHERE entityType=? AND entityId=? [ORDER BY changeTime]`（实证见 `TestNopMetaModelChangedEvent.java:357-362` `findEvents(entityType, entityId) ORDER BY changeTime DESC`），entityType 前导覆盖纯类型查询、entityType+entityId 覆盖实体级查询、changeTime 尾列支持排序——单索引服务三种模式，且与兄弟实体 `IX_NOP_META_QRULE_ENTITY(entityType, entityId)` / `IX_NOP_META_TAG_LABEL_ENTITY(entityType, entityId)` 约定一致，避免新建冗余索引。索引名保留 `IX_NOP_META_EVENT_TYPE_TIME`（名称为标签，列清单为优化器所用；重命名会增加无谓 DDL churn）。**Trade-off**：扩展后 `entityType + changeTime`（无 entityId）的纯时间范围扫描失去 changeTime 索引排序支持——经核实当前无生产查询走该模式（生产/测试查询均为 entityType+entityId 或 entityType+changeSource），取舍合理。对已部署库此为 schema migration（DROP + CREATE INDEX），非 data migration。
> - **F11**：四个软外键列各建单列索引：`IX_NOP_META_DOMAIN_SOURCE_MODULE(sourceModuleId)`、`IX_NOP_META_TABLE_BASE_ENTITY(baseEntityId)`、`IX_NOP_META_DIM_ENTITY_FIELD(entityFieldId)` [NopMetaTableDimension]、`IX_NOP_META_MEASURE_ENTITY_FIELD(entityFieldId)` [NopMetaTableMeasure]。
> - **F12**：新增 `IX_NOP_META_QRESULT_RUN(runId, executeTime)`——runId 前导（按批次过滤），executeTime 尾列（按执行时间排序）。与现有 `IX_NOP_META_QRESULT_RULE(qualityRuleId, executeTime)` 正交（一个按规则、一个按批次）。
>
> **索引命名**：沿用现有约定 `IX_NOP_META_<ENTITY>_<COL>`，与兄弟索引风格一致（已核实现有命名模式）。

- [ ] F10：`NopMetaModelChangedEvent` 的 `IX_NOP_META_EVENT_TYPE_TIME` 列清单扩展为 `(entityType, entityId, changeTime)`
- [ ] F11：NopMetaDomain 新增 `IX_NOP_META_DOMAIN_SOURCE_MODULE(sourceModuleId)`
- [ ] F11：NopMetaTable 新增 `IX_NOP_META_TABLE_BASE_ENTITY(baseEntityId)`
- [ ] F11：NopMetaTableDimension 新增 `IX_NOP_META_DIM_ENTITY_FIELD(entityFieldId)`
- [ ] F11：NopMetaTableMeasure 新增 `IX_NOP_META_MEASURE_ENTITY_FIELD(entityFieldId)`
- [ ] F12：NopMetaQualityResult 新增 `IX_NOP_META_QRESULT_RUN(runId, executeTime)`
- [ ] 重新生成：`./mvnw install -pl nop-metadata -am -DskipTests`，确认 `_app.orm.xml` 含新增/扩展索引（grep 证据）

Exit Criteria:

- [ ] 源模型 `model/nop-metadata.orm.xml` 含 F10 扩展（1）+ F11 新增（4）+ F12 新增（1）= 6 处索引变更，命名遵循约定
- [ ] 生成产物 `_app.orm.xml` 经重新生成含对应索引（非手编；`rg -n "IX_NOP_META_(EVENT_TYPE_TIME|DOMAIN_SOURCE_MODULE|TABLE_BASE_ENTITY|DIM_ENTITY_FIELD|MEASURE_ENTITY_FIELD|QRESULT_RUN)" nop-metadata/nop-metadata-dao` 命中且 EVENT_TYPE_TIME 列清单含 entityId）
- [ ] 未手编任何 `_` 前缀文件（git diff 仅源模型 + 生成产物差异）
- [ ] **无静默跳过**：不得用注释/TODO 代替实际索引声明；新增/扩展索引必须出现在生成 DDL
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C` 通过
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（索引加性变更不破坏行为）
- [ ] `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` → exit 0（防回退，加索引不影响 UK 完备性门禁）
- [ ] 若索引命名/语义改变 live baseline：相关 `docs-for-ai/`（如 `03-modules/nop-metadata.md` 索引说明，若有）已同步；否则写 No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划为 ORM 源模型加性索引。保留构建 + 测试 + UK 门禁验证。ORM 变更属 plan-first，执行前需人工确认（mission 授权）。

- [ ] 6 处缺失索引已在源模型补建并经重新生成落到 `_app.orm.xml`（grep 证据，含 F10 扩展列清单）
- [ ] 未手编任何 `_` 前缀生成产物（git diff 仅源模型 + 重新生成产物）
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [ ] `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` → exit 0
- [ ] `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` → exit 0（防回退）
- [ ] 不存在被静默降级到 deferred 的 in-scope 项
- [ ] 受影响 owner docs 已同步，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **生成产物一致性验证**：closure audit 验证新增/扩展索引确实出现在生成 DDL/`_app.orm.xml`（非仅源模型声明而未生成）；无空占位/TODO 代替索引
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（本计划无 deferred 项。）

## Non-Blocking Follow-ups

- **F13 标记为 false positive**：建议在 roadmap follow-up backlog 与审计源 `2026-08-14-0707-multi-audit-...md`（F13）标注"false positive——orm.xml:104-105 已覆盖"，避免后续重复追踪。属文档治理，不阻塞本计划。
- 其它软外键列/查询热点的索引治理（如本批之外的列）属独立增强。
- F14-F19 → 归 `2026-08-14-1448-1`；F17+AR-14 → 归 `2026-08-14-1448-2`。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<待独立 fresh session closure audit 填写>>
- Evidence: <<待填写>>
