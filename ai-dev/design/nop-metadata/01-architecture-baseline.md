# nop-metadata Architecture Baseline

> Status: draft
> Date: 2026-07-15

---

## 一、设计结论

1. **nop-metadata 是 nop-entropy 的新模块**，所有应用共享一个元数据目录。
2. **元数据来源**：Nop 平台模块的 `model/*.orm.xml`（导入），以及外部系统的表（扫描注册）。
3. **按模型类型分表**：MetaOrmModel / MetaApiModel（预留）/ MetaWfModel（预留），不共用一张表。
4. **每个 MetaOrmModel 就是一个版本**（long PK），发布后版本号不可变。`status`（drafting | released | deprecated）管理生命周期，无 Release 表。
5. **模型通过 `x:extends` 继承 base 模型**，自身只写 delta。每次导入同时存储 **delta 定义**（本模块声明的内容）和 **full 定义**（base + delta 合并后）。
6. **ORM 模型内容拆解为结构化实体**（MetaEntity/MetaEntityField/MetaEntityRelation/MetaDomain/MetaDict），字段级搜索和引用追踪。拆解时 `isDelta` 区分 delta 和 full。
7. **MetaOrmModel 保留 `sourceContent`**（原始 XML），用于重新解析或逐字比对。
8. **MetaTable 是面向用户的统一逻辑表**，可以包装 ORM 实体或 SQL 定义。指标（MetaTableMeasure）和跨表关联（MetaTableJoin）在 MetaTable 上叠加，不存到 MetaEntity。
9. **所有查询执行走现有 ORM 层** —— ORM 本身就是统一的数据库映射引擎，不引入额外 Driver/QuerySpace 抽象。
10. **废弃 `NopReportDataset`/`NopReportSubDataset`/`NopReportDatasetRef`**，报表只存 `tableId`。

---

## 二、核心对象

### 2.1 模块

```
MetaModule                      — 模块（如 nop-auth）
  ├── moduleId                  — "nop/auth"
  ├── moduleName                — "nop-auth"
  └── displayName
```

### 2.2 模型版本

每个模型类型独立一张表，不共用。MetaOrmModel 每条记录就是一个版本。

```
MetaOrmModel                    — ORM 模型版本（发布后 version 不可变）
  ├── moduleId                  → MetaModule
  ├── modelName                 — "nop-auth"（同一模块同一类型下分组 key）
  ├── version                   — long，发布后不可变
  ├── isDelta                   — true: 本模块声明的 delta, false: 合并后的 full
  ├── baseModelId               → MetaOrmModel（isDelta=true 时指向 base 版本的 full 记录）
  ├── sourceContent             — CLOB，orm.xml 原文
  ├── status                    — drafting | released | deprecated
  └── importedAt

MetaApiModel                    — API 模型版本（预留，结构同上）
MetaWfModel                     — 工作流模型版本（预留，结构同上）
```

### 2.3 ORM 模型内容（来自 `model/*.orm.xml`）

所有子实体带 `isDelta` 标记（true=本模块声明的 delta, false=合并后的 full）：

```
MetaEntity                      — ORM 实体
  ├── modelId                   → MetaOrmModel
  ├── isDelta
  ├── entityName / tableName / displayName / className
  ├── tagSet / querySpace / persistDriver
  ├── useTenant / useRevision / useLogicalDelete / notGenCode
  ├── createrProp / createTimeProp / updaterProp / updateTimeProp
  ├── versionProp / deleteFlagProp / deleteVersionProp
  ├── dbCatalog / dbSchema / dbPkName / sqlText
  └── extConfig                 — 扩展属性 JSON（包含 ui:/biz:/orm: 命名空间属性）

MetaEntityField                 — 字段
  ├── entityId                  → MetaEntity
  ├── isDelta
  ├── fieldName / columnCode / propId
  ├── stdDataType / stdSqlType / precision / scale
  ├── mandatory / primary / lazy / insertable / updatable
  ├── domain / stdDomain / fixedValue / sqlText / defaultValue
  ├── tagSet / displayName / nativeSqlType / comment
  └── extConfig

MetaEntityRelation              — 关系
  ├── entityId                  → MetaEntity
  ├── isDelta
  ├── relationName / relationType        — "to-one" | "to-many"
  ├── refEntityName / refPropName
  ├── cascadeDelete / autoCascadeDelete / queryable
  ├── tagSet / embedded / notGenCode
  └── joinConditions[]
       └── leftProp / leftValue / rightProp / rightValue

MetaEntityUniqueKey             — 唯一键
  ├── entityId                  → MetaEntity
  ├── isDelta
  ├── ukName / displayName
  ├── columns                   — 逗号分隔的字段名列表
  ├── constraint                — 数据库约束名
  └── tagSet

MetaEntityIndex                 — 索引
  ├── entityId                  → MetaEntity
  ├── isDelta
  ├── indexName / displayName
  ├── indexType / unique
  └── indexColumns[]
       ├── fieldName
       └── desc                  — boolean

MetaDomain                      — 域定义
  ├── modelId                   → MetaOrmModel
  ├── isDelta
  ├── domainName / stdDomain / stdDataType / stdSqlType / precision / scale

MetaDict                        — 字典
  ├── modelId                   → MetaOrmModel
  ├── isDelta
  ├── dictName / label / valueType / locale
  ├── static / normalized / tagSet / deprecated / internal
  └── MetaDictItem[]
       ├── itemValue / itemLabel / itemCode
       ├── group / description
       ├── sortOrder
       └── deprecated / internal
```

### 2.4 逻辑表（BI 语义层）

MetaTable 是对用户暴露的统一概念——用户看到的"可以查的表"，有两种来源：

| tableType | 含义 | 字段来源 |
|-----------|------|---------|
| `entity` | 包装一个 ORM 实体 | 直接从 MetaEntityField 拉取，不重复存储 |
| `sql` | 用户用 SQL 定义的视图 | 从 SQL 解析或用户手动录入 |

```
MetaTable                       — 逻辑表
  ├── moduleId                  → MetaModule（所属模块）
  ├── tableName / displayName
  ├── tableType                 — "entity" | "sql"
  ├── querySpace                — 查询空间（tableType=sql 时指定，entity 时由引用的实体决定）
  ├── sourceSql                 — SQL 文本（tableType=sql）
  ├── baseEntityId              → MetaEntity（可选，标注"主要实体"，仅用于 UI 默认展开）
  └── buildSql                  — 合成后的完整 SQL

MetaTableMeasure                — 表指标
  ├── tableId                   → MetaTable
  ├── measureName / displayName
  ├── entityFieldId             → MetaEntityField（引用的实体字段）
  ├── aggFunc                   — "sum" | "count" | "avg" | "min" | "max" | "countDistinct"
  ├── format                    — "#,##0.00"
  └── currencyUnit              — "CNY" | "USD"

MetaTableJoin                   — 表关联
  ├── tableId                   → MetaTable
  ├── joinType                  — "inner" | "left" | "right"
  ├── leftEntityId / rightEntityId → MetaEntity
  ├── leftField / rightField    — 关联字段
  └── alias                     — 右表别名
```

说明：

- **tableType=entity 的字段**：不单独存储 MetaTableField。MetaTable 通过引用的 MetaEntity（baseEntityId）和 MetaTableJoin 推断可用字段，字段定义从 MetaEntityField 动态拉取。
- **tableType=sql 的字段**：由 `sourceSql` 在运行时解析 SELECT 子句得到，不单独存储。
- **MetaTableMeasure** 的 `entityFieldId` 指向 MetaEntityField（entity 类型）或解析 SQL 得到的字段（sql 类型，用字符串名引用）。
- **MetaTableJoin** 的 `leftEntityId/rightEntityId` 指向 MetaEntity，`leftField/rightField` 指向字段名。关联条件是用户按需定义的，不依赖 ORM 模型已有的 MetaEntityRelation。

### 2.5 数据血缘

血缘描述数据从哪里来、经过什么处理、流向哪里。nop-metadata 支持表级和列级血缘。

```
MetaLineageEdge                  — 血缘边
  ├── sourceTableId              → MetaTable（源表）
  ├── targetTableId              → MetaTable（目标表）
  ├── sourceColumn               — 源列名（可选，空表示表级血缘）
  ├── targetColumn               — 目标列名（可选，空表示表级血缘）
  ├── transformType              — "direct" | "derived" | "aggregated"
  ├── transformExpression        — 转换表达式（如 "CONCAT(first_name, last_name)"）
  ├── lineageSource              — "manual" | "sql_parse" | "open_lineage" | "hook"
  ├── pipelineId                 → MetaPipeline（可选，关联的处理管道）
  ├── confidence                 — 置信度 0.0~1.0
  └── extConfig                  — 扩展属性

MetaPipeline                     — 数据处理管道
  ├── moduleId                  → MetaModule
  ├── pipelineName / displayName
  ├── pipelineType              — "etl" | "sql" | "api" | "manual"
  ├── sourceSql                 — 处理逻辑 SQL（可选）
  ├── schedule                  — 调度表达式（可选）
  └── extConfig
```

血缘查询支持：
- **向上追溯**: 给定目标表，查找所有上游源表
- **向下追踪**: 给定源表，查找所有下游消费者
- **影响分析**: 列级变更影响范围（哪些下游列会受影响）
- **路径查找**: 两表之间的血缘路径

### 2.6 数据质量

数据质量定义在字段和表级别，描述数据的约束规则和质量期望。

```
MetaQualityRule                  — 质量规则定义
  ├── ruleName / displayName
  ├── ruleType                   — "not_null" | "unique" | "range" | "regex" | "custom_sql" | "freshness" | "volume"
  ├── entityType                 — "field" | "table"
  ├── entityId                   → MetaEntityField | MetaTable（规则挂载对象）
  ├── severity                   — "error" | "warning" | "info"
  ├── sqlExpression              — 自定义 SQL 表达式（ruleType=custom_sql 时使用）
  ├── threshold                  — 阈值（如最小行数、最大空值比例）
  ├── params                     — JSON 参数（如 min/max/regex pattern）
  └── extConfig

MetaQualityResult                — 质量执行结果（时序数据）
  ├── ruleId                     → MetaQualityRule
  ├── executeTime                — 执行时间
  ├── status                     — "pass" | "fail" | "error" | "skip"
  ├── actualValue                — 实际值
  ├── expectedValue              — 期望值
  ├── message                    — 结果描述
  └── details                    — JSON 详情
```

内置质量规则类型：

| ruleType | 适用对象 | 说明 | params 示例 |
|----------|----------|------|-------------|
| `not_null` | field | 非空检查 | `{"threshold": 0.99}` (99%非空) |
| `unique` | field | 唯一性检查 | `{"sampleSize": 10000}` |
| `range` | field | 范围检查 | `{"min": 0, "max": 1000000}` |
| `regex` | field | 正则匹配 | `{"pattern": "^\\d{4}-\\d{2}-\\d{2}$"}` |
| `freshness` | table | 新鲜度检查 | `{"maxAgeMinutes": 60}` |
| `volume` | table | 行数检查 | `{"minRows": 1000, "maxRows": 10000000}` |
| `custom_sql` | table | 自定义SQL | `{"sql": "SELECT COUNT(*) FROM t WHERE ..."}` |

质量结果时序存储，支持：
- 趋势查看（最近 N 天的通过率）
- 异常告警（连续失败 N 次）
- 影响分析（质量下降与哪些变更相关）

---

## 三、Delta 版本管理

### 3.1 Delta 链

```
nop-auth v2 (full)             baseModelId = null
nop-app-mall v2 (delta)        baseModelId = nop-auth v2 (full)
nop-app-mall v2 (full)         baseModelId = null
```

导入 nop-app-mall v2 时：

```
1. 解析原始 orm.xml（不展开 x:extends）→ 写入 isDelta=true
2. 定位 baseModelId = nop-auth v2 (查找 full 记录)
3. 加载 base 的 full 定义
4. 应用 x:extends 合并 → 写入 isDelta=false (full)
```

### 3.2 查看能力

| 视图 | 查询 | 用途 |
|------|------|------|
| Delta 定义 | `isDelta=true AND modelId=?` | 本模块自己声明了什么 |
| Full 定义 | `isDelta=false AND modelId=?` | 合并后完整模型 |
| Base 对比 | 递归 baseModelId 的 full | 和 base 比多了什么 |

### 3.3 版本不变量

- 每个版本的 full 定义完整存储，不动态合并。base 版本删除后 full 仍可查。
- 外部系统的表不参与版本管理（周期同步，没有版本概念）。
- 版本号发布后不可变（无法修改或删除 released 记录）。

---

## 四、模型导入

### 4.1 ORM 模型导入

```
orm.xml
  ├── 解析一次（不展开 x:extends）→ 写入 isDelta=true
  └── 解析一次（展开 x:extends）→ 写入 isDelta=false
```

导入分两次解析，利用现有 `DslModelParser`。同时为每个 MetaEntity 自动创建对应的 MetaTable（tableType=entity）。

`sourceContent` 存储原始 orm.xml 内容（CLOB），可用于重解析或 diff。

### 4.2 SQL 视图创建

用户在 UI 上输入 SQL，系统：
1. 创建 MetaTable（tableType=sql），写入 sourceSql
2. 运行时解析 SELECT 子句获取字段列表，不单独存储

### 4.3 模块发现

注册式（配置 `beans.xml`）或自动扫描 `_module` 文件。

---

## 五、与 nop-report / nop-dyn 的关系

### 5.1 nop-report

```
改造前: NopReportDefinition → datasetRefs → NopReportDataset → subDatasets
改造后: NopReportDefinition → tableId → MetaTable
```

### 5.2 nop-dyn

```
nop-dyn:       运行时自定义实体 → 存到 nop_dyn_entity
nop-metadata:  平台 ORM 模型目录 + SQL 视图 + 逻辑表（指标/关联）
```

互补。nop-dyn 的 `OrmModelToDynEntityMeta` 可作为导入 ORM 模型的参考。nop-dyn 创建的动态实体也可以注册到 nop-metadata 被目录化管理。

---

## 六、模块依赖

```
nop-metadata-api           — DTO（无依赖）
nop-metadata-dao           — nop-orm, nop-metadata-api
nop-metadata-service       — nop-metadata-dao
nop-metadata-web           — nop-metadata-service
```

---

## 七、拒绝了什么

| 方案 | 理由 |
|------|------|
| MetaTable 冗余存储 entity 字段 | entity 字段已存在于 MetaEntityField，版本变化时还会不一致。动态拉取更简单 |
| MetaDataSource + QuerySpace + Driver | ORM 本身就是统一数据库映射引擎，不需要另加一层 |
| 仅存 delta 不存 full | base 版本删除后 full 不可查 |
| 版本用 Git tag 驱动 | 耦合 Git |
| 按模型类型共用一张表 | 各自子实体结构不同，分开更清晰 |
| 单独的 MetaOrmModelRelease 实体 | 模型记录数有限，`status` 字段足够管理生命周期 |

---

## 八、待定问题

- `isDelta=true/false` 用同一张表（列区分）还是两张表？
- baseModelId 链的深度限制？深层 Delta（A→B→C→D）的 diff 展示方式？
- SQL 视图字段解析：走 `EXPLAIN` 还是 `SELECT ... LIMIT 0` 还是用户手动录入？
- MetaTableJoin 跨表关联时，左右表所属数据源不同（例如 ORM 的 MySQL 表和 SQL 定义的 ClickHouse 表），查询执行如何路由？
