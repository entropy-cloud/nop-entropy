# nop-metadata Reconciliation 设计

> Status: final
> Date: 2026-07-16
> Scope: nop-metadata 实体对账（Reconciliation）
> Goal: 定义 Reconciliation 对账模型与执行流程，把实体列值与候选知识库对齐
> Based on: OpenRefine Reconciliation、Wikidata Reconciliation API
> Implements: P4-5（plan `2026-07-16-0900-2`）

---

## 一、设计决策

### 1.1 定位

Reconciliation 是 nop-metadata 的数据治理能力之一：把某张逻辑表的某一列值，与一组候选实体
（候选知识库）逐行对齐，产出 MATCHED/UNMATCHED/MULTIPLE 判定，并支持人工确认。

### 1.2 服务模型（D1 裁定）

采用**可插拔 `IReconciliationService` 接口 + 内置 `LocalReconciliationService`（本地候选集匹配）**。

- 首版只实现本地匹配，候选来自 `NopMetaReconciliationEntity`（按 `entityType`+`identifierSpace` 过滤）。
- 接口入参显式包含 `matchStrategy`，使策略可由调用方控制；接口纯接口、无外部依赖，
  外部 HTTP 实现（OpenRefine/Wikidata 兼容端点 + 认证）可在后续作为新 impl 插入，不改执行器与 BizModel。
- **外部 HTTP 实现为 follow-up**：因其依赖外部可用性与认证（见 Open Questions），无法在 H2/AutoTest
  端到端验证。本地实现已使"实体可对账、结果可存、可人工确认"这一结果面成立。

### 1.3 对账粒度（D4 裁定）

首版为**单列对账**：`columnName`/`matchStrategy`/`targetEntityType` 挂在 `NopMetaReconciliationConfig`
顶层，一次对账只处理一列。设计文档原描述的表级 + 多列 `columns[]` 移到 follow-up。

### 1.4 明细存储形态（D3 裁定）

对账结果明细由**单行 `NopMetaReconciliationResult` 承载**：

- `statistics`（json-4000）：`totalRows`/`matchedRows`/`unmatchedRows`/`multipleMatches`/`matchRate`。
- `details`（mediumtext + stdDomain json）：per-row 数组，元素 `{rowIndex, originalValue, status,
  candidates[], selectedId}`。

对齐 `NopMetaProfilingResult.tableStats/columnStats`（mediumtext + json）先例；**不**拆子表
（超大表明细为 follow-up）。`details` 不复用 `NopMetaQualityResult.details`（后者 json-4000，容量不足以承载逐行明细）。

---

## 二、核心模型

> 实体定义以源码 `nop-metadata/model/nop-metadata.orm.xml` 为唯一事实源；本节只描述用途契约。

### 2.1 Reconciliation 配置（`NopMetaReconciliationConfig`）

单列对账配置。关键字段：

- `configName`/`displayName`：标识与显示名。
- `metaModuleId`（→NopMetaModule，可空）/`metaTableId`（→NopMetaTable）：所属模块与待对账逻辑表。
- `columnName`：待对账的列名（须在该表 `MetaTableFieldResolver` 解析出的可用字段集合内）。
- `identifierSpace`：标识符空间（如 Wikidata URI），用于过滤候选实体。
- `targetEntityType`（可空）：目标实体类型，用于过滤候选实体。
- `matchStrategy`（dict `meta/match-strategy`：`exact`/`fuzzy`）。
- `autoMatch`（bool）/`autoMatchThreshold`（double，0.0~1.0）：是否自动判定及阈值。
- `extConfig`（json-4000）+ 审计列 + version。

to-one：`metaTable`（join on metaTableId）、`metaModule`（join on metaModuleId）。索引 `IX_NOP_META_RECON_CONFIG_TABLE`(metaTableId)。

> 设计草案中的 `columns[]`（多列）、`serviceUrl`（归外部 HTTP impl）、`schemaSpace`/`schedule`
> 首版不启用，标注为 follow-up。

### 2.2 Reconciliation 结果（`NopMetaReconciliationResult`）

一次执行的结果行。关键字段：

- `configId`（→Config）/`metaTableId`（→NopMetaTable）/`executeTime`：归属与时序。
- `statistics`（json-4000）：见 §1.4。
- `details`（mediumtext + stdDomain json，per-row 数组）：见 §1.4。每个 per-row 元素：
  - `rowIndex`：执行快照内的行下标（首版语义绑定本次执行快照，见 §四人工确认注）。
  - `originalValue`：该行 `columnName` 列的原始值。
  - `status`：`MATCHED`/`UNMATCHED`/`MULTIPLE`/`MANUAL`（dict `meta/reconciliation-status`）。
  - `candidates[]`：候选列表 `{entityId, entityName, entityType, score, properties}`。
  - `selectedId`：人工确认后选中的实体 ID。
- `extConfig`（json-4000）+ 审计列 + version。

to-one：`config`、`metaTable`。索引 `IX_NOP_META_RECON_RESULT_CONFIG`(configId, executeTime)（时序）。

### 2.3 Reconciliation 实体（`NopMetaReconciliationEntity`）

候选实体缓存，作为本地匹配器的候选集来源，也作为外部匹配结果未来的缓存落点。关键字段：

- `entityId`（业务标识）/`entityName`/`entityType`/`identifierSpace`：实体的检索维度。
- `properties`（json-4000）：实体属性。
- `lastSyncedAt`：最后同步时间。
- `extConfig`（json-4000）+ 审计列 + version。

索引 `IX_NOP_META_RECON_ENTITY_TYPE`(entityType, identifierSpace)（候选检索）。

---

## 三、对账流程

### 3.1 标准 Reconciliation API（外部 HTTP 参考，首版不实现）

外部 HTTP impl 未来兼容的请求/响应契约（首版本地实现等价语义）：

- 请求：`{query, type, limit}`。
- 响应：`{result:[{id, name, type[], score, properties:[]}]}`。

### 3.2 判定规则（D5 裁定，status 判定单一事实源）

对每行，按候选列表与 config 判定 status：

- 候选为空 → **UNMATCHED**。
- 恰 1 候选且 `score >= autoMatchThreshold` → **MATCHED**（`selectedId` = 该候选）。
- 恰 1 候选且 `score < autoMatchThreshold` → **MULTIPLE**（候选仍列出，交人工确认）。
- 候选 ≥ 2 → **MULTIPLE**（最高分候选仍列出，由人工确认选择）。
- `autoMatch = false` 时，有候选一律 → **MULTIPLE**（交人工）。

> 该规则替换了草案中 `evaluateMatch` 的 Java 实现；行为语义以本节为准。

### 3.3 执行流程（行为契约）

对账执行入口为 `NopMetaReconciliationConfigBizModel.executeReconciliation(configId)`（`@BizMutation`）：

1. 加载 config；config 不存在 → 显式失败（抛 ErrorCode，不 NPE）。
2. 校验 `columnName` 在目标表 `MetaTableFieldResolver` 解析字段集合内；非法 → 显式失败。
3. BizModel 注入并调用 `NopMetaTableBizModel.queryTableData(metaTableId, null, null, null, context)`
   取得 `items`（行列表，`List<Map>`）。`queryTableData` 失败 → 显式失败（不吞异常）。
4. 把 `items` 传入**纯组件 `ReconciliationExecutor.execute(config, items)`**：
   - 执行器不持有 BizModel、不伪造 context、不复制取数逻辑。
   - 逐行按 `config.columnName` 取值 → 调 `IReconciliationService` 取候选 → 按 §3.2 判定 status。
   - 汇总 `statistics` + `details` → 写一行 `NopMetaReconciliationResult` 并返回。
5. 空候选 → 体现在结果的 UNMATCHED（非整体异常、不静默 pass）。

> 取数接线裁定（B2 方案 b）：仓库无跨 BizModel 注入另一 BizModel 的生产先例，
> 且 `INopMetaTableBiz` 是空接口（无 queryTableData 声明）。首版由 config BizModel
> 注入 `NopMetaTableBizModel` 具体类调用 `queryTableData`。提取共享 table-data fetcher
> （重构 queryTableData）为 Non-Blocking Follow-up。

### 3.4 人工确认（行为契约）

人工确认入口落在 `NopMetaReconciliationResultBizModel`（`@BizMutation`）：

- `confirmMatch(resultId, rowIndex, selectedEntityId)`：更新 `details[rowIndex].status=MANUAL` + `selectedId`。
- `batchConfirmMatches(resultId, selections)`：批量执行上述更新。
- 越界 `rowIndex` / result 不存在 → 显式失败（不静默忽略）。

> 注：`rowIndex` 为 `details` JSON 数组下标，首版语义绑定本次执行快照（重排/分页漂移为
> follow-up，可后续引入 stable rowKey）。

---

## 四、匹配策略（D2 裁定）

首版策略集：

| 策略 | dict 值 | 语义 |
|------|---------|------|
| exact | `exact` | 完全相等 → score=1.0，否则 0 |
| fuzzy | `fuzzy` | levenshtein 归一化相似度（忽略大小写） |

候选经 `LocalReconciliationService` 按 `entityType`+`identifierSpace` 从 `NopMetaReconciliationEntity`
检索，按策略计算 score，排序后取 limit。候选为空 → 返回空列表（不静默伪造候选）。

**phonetic/semantic 移 follow-up**（需额外算法/模型依赖）。设计草案的 `matchParams`（algorithm/threshold/ignoreCase/ignoreDiacritics）首版不作为独立列；threshold 复用 `config.autoMatchThreshold`，ignoreCase 内置为 true。

---

## 五、属性扩展（follow-up）

属性扩展（`expandProperties` 把候选属性写回表）为首版 follow-up，不在 P4-5 范围内。
设计草案中相关内容（配置/执行）首版不落地，待需要时补设计。

---

## 六、GraphQL API

3 个实体经 `CrudBizModel` 自动暴露 `findPage`/`findList`/`get`/`save`/`delete`（`registerShortName=true`）。
自定义 action（均 `@BizMutation`）：

- `NopMetaReconciliationConfig__executeReconciliation(configId)` → `NopMetaReconciliationResult`。
- `NopMetaReconciliationResult__confirmMatch(resultId, rowIndex, selectedEntityId)`。
- `NopMetaReconciliationResult__batchConfirmMatches(resultId, selections)`。

---

## 七、与 OpenRefine 的对比

| 能力 | OpenRefine | nop-metadata |
|------|-----------|-------------|
| 对账配置 | JSON 配置 | `NopMetaReconciliationConfig`（持久化） |
| 对账结果 | 内存存储 | `NopMetaReconciliationResult`（持久化 + 时序） |
| 外部实体 | 实时查询 | `NopMetaReconciliationEntity`（本地缓存）+ 可插拔外部 HTTP（follow-up） |
| 匹配策略 | 内置 | exact/fuzzy（phonetic/semantic follow-up） |
| 人工确认 | UI 操作 | GraphQL action |

---

## Open Questions（follow-up）

- Reconciliation 服务是否需要支持认证？→ 归外部 HTTP impl follow-up（本地实现无需）。
- 对账结果是否需要支持版本化？→ follow-up（首版时序行 + 明细 JSON 已承载单次结果）。
- 是否需要支持流式对账（大数据量场景）？→ follow-up（超大表明细存储优化一并考虑）。
- 外部实体缓存是否需要定时刷新？→ follow-up（首版候选集由测试/管理手动维护）。
