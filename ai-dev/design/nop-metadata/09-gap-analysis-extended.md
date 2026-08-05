# nop-metadata 功能缺口分析

> Status: draft
> Date: 2026-07-15
> Scope: 基于 metadata-survey 的全面功能对比
> Goal: 识别 nop-metadata 与参考平台之间的功能差距，确定补充完善方向

---

## 一、分析方法

基于对以下平台的深度分析，对比 nop-metadata 的功能覆盖：

| 类别 | 平台 | 关键能力 |
|------|------|---------|
| **元数据管理** | DataHub, OpenMetadata, Atlas, Marquez, Amundsen | 实体模型、血缘、搜索、事件 |
| **数据质量** | Great Expectations, Apache Griffin | 质量规则、验证执行、数据剖析 |
| **数据转换** | dbt | Manifest、Source/Exposure、指标 |
| **数据清洗** | OpenRefine | Reconciliation、Operation、History |
| **BI 语义层** | Superset, Metabase, DataEase | 语义类型、指标定义、数据分片 |
| **AI 集成** | PandasAI, OpenMetadata MCP | 自然语言查询、Agent 代理 |

---

## 二、nop-metadata 现有能力

| 能力 | 设计文档 | 状态 |
|------|---------|------|
| 元数据目录 | 01-architecture-baseline.md | ✅ |
| BI 语义层（指标/维度/过滤器） | 01-architecture-baseline.md | ✅ |
| 版本管理（Delta + 模块级） | 03-version-management.md | ✅ |
| 数据治理（血缘/质量/契约） | 04-data-governance.md | ✅ |
| 元数据导入（Manifest 模式） | 05-metadata-import.md | ✅ |
| 数据质量扩展（剖析/评分） | 06-data-quality-extended.md | ✅ |
| AI 集成（GraphQL 自动暴露） | 07-ai-integration.md | ✅ |
| Reconciliation 对账 | 08-reconciliation.md | ✅ |
| Catalog 收集 | `collectCatalog`/`collectCatalogForTable` + `MetaCatalogCollector`（service/catalog/，05-metadata-import.md §四） | ✅ 已有能力 |
| 数据源同步 | 手工触发 | ✅ 已有能力 |

---

## 三、功能缺口分析

### 3.1 缺失的核心功能（P0）

| # | 功能 | 状态 | 说明 |
|---|------|------|------|
| 1 | **事件模型** | ✅ 已设计 | 元数据变更通知，支撑 UI 实时更新和下游同步（10-event-model.md） |
| 2 | **语义类型系统** | ✅ 已设计 | 字段的语义类型标记（PK、FK、Name、Date 等）（01-architecture-baseline.md） |

### 3.2 需要明确的设计（P1）

| # | 功能 | 说明 |
|---|------|------|
| 3 | **Manifest 快照实现** | 导入 ORM 模型时生成完整元数据快照（05-metadata-import.md） |
| 4 | **质量检查执行方式** | 已裁定：BizModel action + withConnection callback 执行 MetaQualityRule（`NopMetaQualityRuleBizModel.executeQualityRule`），不引入 nop-batch 验证框架（06-data-quality-extended.md §1.3 明确否决 nop-batch） |

### 3.3 不需要设计的功能

| 功能 | 原因 |
|------|------|
| 数据源自动同步 | 手工触发即可，后续通过 CDC（Debezium）+ nop-stream 实现增量同步 |
| Catalog 收集 | 已有 `MetaCatalogCollector`（`collectCatalog`/`collectCatalogForTable`，见 05-metadata-import.md §四） |
| OpenLineage 标准集成 | 解决业务问题即可，不遵循小众标准 |
| 临时表血缘 | 中间表建模后是普通血缘，不建模则在 transformExpression 中记录 |

---

## 四、待补充设计

### 4.1 事件模型

```
MetaModelChangedEvent            — 元数据变更事件
  ├── eventType                  — "ENTITY_CREATED" | "ENTITY_UPDATED" | "ENTITY_DELETED"
  ├── entityType                 — "MetaEntity" | "MetaTable" | "MetaOrmModel"
  ├── entityId                   — 变更实体 ID
  ├── changeSource               — "IMPORT" | "UI" | "API" | "SYNC"
  ├── beforeSnapshot             — 变更前快照（JSON）
  ├── afterSnapshot              — 变更后快照（JSON）
  ├── changedBy                  — 操作人
  ├── changeTime                 — 变更时间
  └── extConfig
```

实现路径：利用 Nop 的 EventBus 机制，在 MetaEntityRepository 写入后发布事件。

### 4.2 语义类型系统

```
MetaSemanticType                 — 语义类型定义（全局）
  ├── typeName                   — "type/PK" | "type/FK" | "type/Name" | "type/Date" | ...
  ├── displayName                — "主键" | "外键" | "名称" | "日期" | ...
  ├── description                — 类型描述
  ├── applicableTo               — 适用的数据类型列表
  └── extConfig
```

在 MetaEntityField 中增加：
```
MetaEntityField
  ├── ...（现有字段）
  ├── semanticType               → MetaSemanticType（语义类型）
  └── ...
```

### 4.3 Manifest 快照

Manifest 是在导入 ORM 模型时生成的元数据快照，包含：

```
MetaManifest                     — 元数据快照
  ├── moduleId                   → MetaModule
  ├── manifestVersion            — 快照版本号
  ├── generatedAt                — 生成时间
  ├── nodes[]                    — 所有节点（MetaEntity, MetaTable, MetaMeasure 等）
  ├── parentMap                  — 父节点映射（依赖关系）
  ├── childMap                   — 子节点映射
  └── statistics                 — 统计信息
```

实现路径：手动 action `NopMetaModule__generateManifest(metaModuleId)`（`NopMetaModuleBizModel.generateManifest`）；`importOrmModel` 不自动触发，导入时自动生成为 Non-Blocking Follow-up（05-metadata-import.md §2.3）。

### 4.4 质量检查执行方式

已裁定为 **BizModel action + withConnection callback**，不采用独立的批处理验证框架（`06-data-quality-extended.md` §1.3 明确否决 nop-batch；早期「复用 nop-batch」草案为被否决选项，不作为当前设计）：

```
MetaQualityRule → NopMetaQualityRuleBizModel.executeQualityRule（withConnection callback 内执行 SQL）→ MetaQualityResult
```

- nop-batch/nop-job 仅作为「定时调度」的后续可选适配（`06-data-quality-extended.md` §1.3），非执行主路径。

---

## 五、实现路径

### Phase 1: 核心功能完善（P0）

- [x] 实现事件模型（MetaModelChangedEvent + EventBus）— 10-event-model.md
- [x] 补充语义类型系统（MetaSemanticType）— 01-architecture-baseline.md

### Phase 2: 实现细节完善（P1）

- [x] 实现 Manifest 快照生成 — 05-metadata-import.md
- [x] 设计质量检查执行方式（BizModel action + withConnection callback，否决 nop-batch）— 06-data-quality-extended.md

---

## 六、与 nop-metadata 定位的匹配度

| nop-metadata 定位 | 匹配度 |
|------------------|--------|
| 元数据目录 | ✅ 高 |
| BI 语义层 | ✅ 高 |
| 数据治理 | ✅ 高 |
| 实体对账 | ✅ 高 |
| AI 集成 | ✅ 高 |
| 数据血缘 | ✅ 高 |

**结论**: nop-metadata 的定位与参考平台高度匹配，核心功能覆盖良好。主要缺口在事件模型和语义类型系统。
