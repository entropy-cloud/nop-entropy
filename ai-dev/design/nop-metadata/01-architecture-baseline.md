# nop-metadata Architecture Baseline

> Status: draft
> Date: 2026-07-15

---

## 一、设计结论

1. **nop-metadata 是 nop-entropy 的新模块**，所有应用共享一个元数据目录。
2. **元数据来源**：Nop 平台模块的 `model/*.orm.xml`（导入），以及外部系统的表（扫描注册）。
3. **按模型类型分表**：MetaOrmModel / MetaApiModel（预留）/ MetaWfModel（预留），不共用一张表。
4. **版本管理的基本粒度是模块**：每个 MetaModule 就是一个版本（long PK），发布后版本号不可变。`status`（drafting | released | deprecated）管理生命周期，无 Release 表。版本对齐 Maven 打包/发布粒度。MetaModule 包含 Maven 坐标和 Git 信息，支持源码追溯。
5. **模型通过 `x:extends` 继承 base 模块**，自身只写 delta。每次导入同时存储 **delta 定义**（本模块声明的内容）和 **full 定义**（base + delta 合并后）。
6. **ORM 模型内容拆解为结构化实体**（MetaEntity/MetaEntityField/MetaEntityRelation/MetaDomain/MetaDict），字段级搜索和引用追踪。拆解时 `isDelta` 区分 delta 和 full。
7. **MetaOrmModel 保留 `sourceContent`**（原始 XML），用于重新解析或逐字比对。
8. **MetaTable 是面向用户的统一逻辑表**，可以包装 ORM 实体或 SQL 定义。指标（MetaTableMeasure）和跨表关联（MetaTableJoin）在 MetaTable 上叠加，不存到 MetaEntity。
9. **所有查询执行走现有 ORM 层** —— ORM 本身就是统一的数据库映射引擎，不引入额外 Driver/QuerySpace 抽象。
10. **保留 `NopReportDataset`**，它直接运行在 EQL 上，是另外一种数据获取记录。后续再考虑 ReportDataSet 的定位，当前先与 Meta 体系断开。
11. **Domain 定义归属模块**，支持通用域（isGlobal=true）的引用和拷贝机制。

---

## 二、核心对象

### 2.1 模块

模块是版本管理的基本粒度，对齐 Maven 打包/发布粒度。

```
MetaModule                      — 模块（版本管理基本粒度）
  ├── moduleId                  — "nop/auth"（唯一标识）
  ├── moduleName                — "nop-auth"
  ├── displayName               — "Nop 认证模块"
  ├── version                   — long，模块版本号（发布后不可变）
  ├── baseModuleId              → MetaModule（Delta 继承的 base 模块版本，null 表示无继承）
  ├── status                    — "drafting" | "released" | "deprecated"
  ├── importedAt                — 导入时间
  │
  ├── mavenGroupId              — Maven groupId（如 "io.nop"）
  ├── mavenArtifactId           — Maven artifactId（如 "nop-auth"）
  ├── mavenVersion              — Maven 版本号（如 "1.2.3"，与内部 version 对应）
  │
  ├── gitRepoPath               — Git 仓库路径（如 "/Users/abc/sources/nop-entropy"）
  ├── gitBranch                 — Git 分支（如 "main", "feature/xxx"）
  ├── gitCommitId               — Git commit hash（如 "abc1234"）
  │
  └── extConfig                 — 扩展属性 JSON
```

**Maven 映射**:
| Maven 概念 | nop-metadata 字段 |
|-----------|------------------|
| groupId | mavenGroupId |
| artifactId | mavenArtifactId |
| version | mavenVersion |
| packaging | 由模型类型决定（ORM = Java POJO） |

**Git 映射**:
| Git 概念 | nop-metadata 字段 |
|---------|------------------|
| 仓库路径 | gitRepoPath |
| 分支 | gitBranch |
| 提交 | gitCommitId |

状态流转：
```
drafting → released → deprecated
```

### 2.2 数据源

每个数据源对应一个 `querySpace` 名称，指向具体的物理数据库或连接。

```
MetaDataSource                    — 数据源定义（全局，吸收 NopReportDatasource）
  ├── querySpace                 — "default" | "report" | "log" 等（全局唯一）
  ├── name / displayName
  ├── datasourceType             — "jdbc" | "http" | "rest" | "file" ...（不限于数据库）
  ├── connectionConfig           — JSON 连接配置（JDBC 连接串、HTTP URL 等，按 type 解释）
  └── status                     — ACTIVE | DISABLED
```

说明：
- **全局实体**，不属于任何模块。数据源是跨模块共享的基础设施。
- 每个 `querySpace` 对应一个数据源，全局唯一。ORM 实体通过自身 `querySpace` 字段引用，无需额外抽象层。
- 纯元数据用途：描述数据源信息，不负责运行时查询路由（ORM 已承担此职责）。
- **吸收 `NopReportDatasource`**：报表模块不再维护独立的数据源表，统一使用 `MetaDataSource`。原 `NopReportDatasourceAuth` 的角色级访问控制由 `nop-auth` 承担。

#### 2.2.1 连通性验证（testConnection）

数据源注册后通过 `testConnection` 验证连通性。本节落地 plan P2-1 的设计决策 D1/D2/D3。

**契约（D1）**：
- GraphQL mutation：`NopMetaDataSource__testConnection(dataSourceId)` → 返回 `Map<String,Object>`。
- 行为：按 `dataSourceId` 加载实体 → `status == DISABLED` 时抛 `metadata.datasource-disabled`（显式拒绝，不静默通过）→ 交连接服务按 `datasourceType` 分派建连 → 读 `DatabaseMetaData` → 关闭 → 返回。
- 成功返回 `{connected:true, databaseProductName, databaseProductVersion, driverName, driverVersion}`；建连失败（`SQLException`）catch 后返回 `{connected:false, error}`，**不向上抛**，使 GraphQL 调用方能拿到结构化失败结果。
- 实体不存在抛 `metadata.datasource-not-found`（不 NPE）。
- **仅支持 jdbc**：非 jdbc 类型（http/rest/file）由连接服务显式抛 `UnsupportedOperationException`，不静默返回成功。后续 http/rest/file 验证为 Non-Blocking Follow-up。

**connectionConfig 约定（D2，jdbc）**：
- 必填字段：`jdbcUrl`、`username`、`password`。可选：`driverClassName`（缺省时由 `DriverManager` 按 url 自动匹配）。
- `password` 允许空串（如 H2 默认空密码），按 key 存在性校验；`jdbcUrl`/`username` 须非空。
- 入口校验：缺必填字段抛 inline `ErrorCode(metadata.datasource-config-invalid)`，快速失败。
- 首版 `password` 以**明文**存于 `connectionConfig` JSON（与 free-form 存储一致）；加密/脱敏为独立 follow-up（见 Non-Goals），不引入加密体系以免范围蔓延。

**按需建连（架构基线约束）**：
- 连接服务 `MetaDataSourceConnectionService`（service 层，IoC bean）提供 **callback 式接口** `withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`——内部建连、执行 action、finally 关闭。另提供 `testConnect(...)` 一次性读元数据返回结果 Map。
- 连接通过平台 `SimpleDataSource`（`nop-dao`）从配置按需构建（不池化、**不注册**到 ORM `querySpace` 路由，符合"纯元数据用途"约束），同时服务 P2-1（testConnection）和后续 P2-2/P2-4/P2-6（open → N 条查询 → close）。

**方言识别（D3，不持久化）**：
- 成功时从 `DatabaseMetaData.getDatabaseProductName()` 识别方言，**仅放入返回 Map**，不写任何 ORM 列 / `extConfig`。
- 后续 P2-2 外部表同步需要方言时，由 P2-2 在建连后**自行**调用 `getDatabaseProductName()` 运行时获取，不依赖本能力写入的任何字段。

### 2.3 模型版本

每个模型类型独立一张表，不共用。MetaOrmModel 不再有 version 和 status 字段——这些在 MetaModule 上。

```
MetaOrmModel                    — ORM 模型（属于某个模块版本）
  ├── modelId                   — PK
  ├── moduleId                  → MetaModule
  ├── modelName                 — "nop-auth"（同一模块内分组 key）
  ├── isDelta                   — true: 本模块声明的 delta, false: 合并后的 full
  ├── sourceContent             — CLOB，orm.xml 原文
  └── importedAt

MetaApiModel                    — API 模型版本（预留，结构同上）
MetaWfModel                     — 工作流模型版本（预留，结构同上）
```

### 2.3.1 Manifest 元数据快照（P2-3）

参考 dbt Manifest 模式，为每个已导入模块版本生成自包含的元数据快照。

```
NopMetaManifest                  — 模块元数据快照（每模块版本一条）
  ├── manifestId                 — PK（seq）
  ├── metaModuleId               → NopMetaModule
  ├── manifestVersion            — long，同模块版本下重新生成时递增
  ├── generatedAt                — 生成时间
  ├── nopMetadataVersion         — 生成时平台版本
  ├── content                    — JSON CLOB（mediumtext + stdDomain json）
  │                                正文体：metadata + nodes{entity} + sources + parentMap + childMap
  └── 审计列
```

**存储形态（D2）**：单行 JSON CLOB（`content` 列 `domain="mediumtext"` + `stdDomain="json"`），与 dbt manifest.json 对齐，自包含、易导出/喂给 AI。**不得用 `json-4000`**。

**快照粒度（D1）**：每模块版本一条（`metaModuleId` 关联），与模块版本管理粒度对齐，符合 §3.3 版本不变量。

**依赖图（首版 D3/D4）**：
- 来源仅限 MetaEntityRelation（表/实体级）。列级（SQL 解析，P3）、血缘（P2-5）、指标依赖不在首版。
- 边为 entity→entity。
- 节点 uniqueId：`entity.<moduleId 归一化(slash→dot)>.<简单类名>`，moduleId 取 NopMetaModule.moduleId（业务标识）。
- relation→边 resolution：`refEntityName`(className) → 反查 NopMetaEntity(className) → 其模块 → uniqueId。跨模块/未导入引用记为 `unresolved:<className>`（不丢边、不静默跳过）。

完整规格见 `05-metadata-import.md` §三/§五。生成入口为 `NopMetaModule__generateManifest(metaModuleId)` GraphQL mutation；moduleId 不存在快速失败。

### 2.4 ORM 模型内容（来自 `model/*.orm.xml`）

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
  ├── domainName / displayName / description
  ├── stdDomain / stdDataType / stdSqlType / precision / scale
  ├── validationPattern         — 校验正则（可选）
  ├── defaultValue              — 默认值表达式（可选）
  ├── isGlobal                  — 是否为通用域（可被其他模块引用）
  ├── sourceModuleId            → MetaModule（拷贝时追溯来源）
  ├── tagSet
  └── extConfig

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

### 2.5 逻辑表（BI 语义层）

MetaTable 是对用户暴露的统一概念——用户看到的"可以查的表"，有三种来源：

| tableType | 含义 | 字段来源 |
|-----------|------|---------|
| `entity` | 包装一个 ORM 实体 | 直接从 MetaEntityField 拉取，不重复存储 |
| `sql` | 用户用 SQL 定义的视图 | 从 SQL 解析或用户手动录入 |
| `external` | 已注册外部数据源扫描注册的物理表 | 扫描结果序列化为 JSON 存入 `buildSql`（见 §2.5.1） |

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
- **tableType=external 的字段**：扫描结果（列名/类型/可空/注释/序号）序列化为 JSON 数组存入 `buildSql`（见 §2.5.1）。
- **MetaTableMeasure** 的 `entityFieldId` 指向 MetaEntityField（entity 类型）或解析 SQL 得到的字段（sql 类型，用字符串名引用）。
- **MetaTableJoin** 的 `leftEntityId/rightEntityId` 指向 MetaEntity，`leftField/rightField` 指向字段名。关联条件是用户按需定义的，不依赖 ORM 模型已有的 MetaEntityRelation。

#### 2.5.1 外部表建模（syncExternalTables）

本节落地 plan P2-2 的设计决策 D1/D2。外部表是从已注册 jdbc 数据源（P2-1）扫描注册的物理表，不属于任何平台 ORM 模块，也没有 MetaEntity/OrmModel 来源。

**建模决策 D1（方案 A + 子方案 A2）**：

外部表归属与字段存储三方案经 live repo 核查裁定：
- **方案 C（放宽 `NopMetaTable.metaModuleId` 为 nullable）**：拒绝。`metaModuleId` mandatory 是现有不变量（`nop-metadata.orm.xml` NopMetaTable 列定义 `mandatory="true"`），放宽需迁移评估且破坏既有契约。
- **方案 B（复用 `tableType="entity"`，为每个外部表合成 MetaEntity + 字段进 NopMetaEntityField）**：拒绝。`NopMetaEntity.ormModelId` 已 mandatory（需再造合成 OrmModel），且 `NopMetaEntityField.metaEntityId` mandatory 指向 MetaEntity——合成链路成本高、引入"无来源"的合成实体污染目录。
- **方案 A（选定）**：引入 `tableType="external"`；新建系统模块 `nop/meta-external`（status=RELEASED）作为外部表归属，所有外部表 `metaModuleId` 指向它，保持 `metaModuleId mandatory` 不变量。
  - 字段存储子方案 **A2（选定）**：扫描到的列结构序列化为 JSON 存入 `buildSql`（mediumtext，外部表无"合成 SQL"语义，`buildSql` 复用为列快照）。**子方案 A1（新建轻量字段存储实体）被拒绝**——它属于 ORM 模型结构变更（Protected Area），且字段级单独寻址（血缘/质量引用）属 P2-5/P2-6 范围，本 plan 不需要。

> 本决策不引入任何新 ORM 实体或新列，仅向 `meta/table-type` dict 新增 `external` 枚举值 + 运行时初始化系统模块行，不属于 Protected Area 结构变更。

**系统模块 `nop/meta-external`**：
- 全局唯一（`moduleId="nop/meta-external"`），`status=RELEASED`，由 `syncExternalTables` 在首次同步时惰性创建（若已存在则复用）。
- 外部表的 `querySpace` 取自数据源的 `querySpace`；`(metaModuleId, tableName)` 复合键用于幂等 upsert（`tableName` 上无唯一约束、跨 schema 可能同名，故复合键去重）。

**syncExternalTables 契约（D2）**：
- GraphQL mutation：`NopMetaDataSource__syncExternalTables(dataSourceId, schemaPattern?)` → 返回 `Map<String,Object>`（`{syncedTableCount: int, errors: [...]}`）。`schemaPattern` 可选，限定扫描的 schema。
- 行为：按 `dataSourceId` 加载 NopMetaDataSource → 不存在抛 `metadata.datasource-not-found`（不 NPE）→ `status == DISABLED` 抛 `metadata.datasource-disabled`（不静默通过）→ **复用 P2-1 callback 式连接服务 `withConnection(...)`**（callback 内由 `DatabaseMetaData.getDatabaseProductName()` 运行时取方言 + 执行结构读取）→ 按 D1 方案写入 MetaTable（`tableType=external`，`buildSql` 存列 JSON）→ 幂等 upsert（按 `(metaModuleId, tableName)` 复合键去重）→ 单表失败收集到 `errors` 不中断整批（`orm().clearSession()` 隔离）→ callback 结束自动释放连接。
- **方言范围**：首版支持 MySQL / PostgreSQL / H2（结构读取走标准 JDBC `DatabaseMetaData.getTables()` / `getColumns()`，跨方言可移植，等价于 `information_schema.COLUMNS` 信息）。其余方言（ClickHouse `system.columns`、Oracle 等）在读取器入口显式抛 `UnsupportedOperationException`（快速失败，非静默跳过），多方言全覆盖为 follow-up。
- **非 jdbc 类型**：连接服务显式抛 `UnsupportedOperationException`（继承 P2-1 行为，不静默成功）。

### 2.6 数据血缘

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

### 2.7 数据质量

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

版本管理的基本粒度是模块，不是单个模型。Delta 继承发生在模块之间。

```
nop-auth v1 (released)             baseModuleId = null
nop-auth v2 (drafting)             baseModuleId = null (新主版本)

nop-app-mall v1 (released)         baseModuleId = nop-auth v1
nop-app-mall v2 (drafting)         baseModuleId = nop-auth v2
```

导入 nop-app-mall v2 时：

```
1. 解析原始 orm.xml（不展开 x:extends）→ 写入 isDelta=true
2. 定位 baseModuleId = nop-auth v2（查找 released 版本的 MetaModule）
3. 加载 base 模块下的 full 定义
4. 应用 x:extends 合并 → 写入 isDelta=false (full)
```

### 3.2 查看能力

| 视图 | 查询 | 用途 |
|------|------|------|
| 模块当前版本 | `moduleId=? AND status='released'` | 获取最新已发布版本 |
| Delta 定义 | `moduleId=? AND version=? AND isDelta=true` | 本模块自己声明了什么 |
| Full 定义 | `moduleId=? AND version=? AND isDelta=false` | 合并后完整模型 |
| 版本历史 | `moduleId=? ORDER BY version DESC` | 版本演进时间线 |
| 版本对比 | 两个版本的 full 定义 diff | 变更影响分析 |

### 3.3 版本不变量

- 每个版本的 full 定义完整存储，不动态合并。base 版本删除后 full 仍可查。
- 外部系统的表不参与版本管理（周期同步，没有版本概念）。
- 模块版本号发布后不可变（无法修改或删除 released 记录）。
- 版本管理粒度对齐 Maven 打包/发布（一个模块版本 = 一次发布）。

---

## 四、模型导入

### 4.1 ORM 模型导入

```
orm.xml
  ├── 解析（DslNodeLoader ResolvePhase.filtered，不展开 x:extends）→ OrmModel(delta) → MetaOrmModel(isDelta=true)
  └── 解析（OrmModelLoader.loadFromResource，展开 x:extends）      → OrmModel(full)  → MetaOrmModel(isDelta=false)
```

导入分两次解析，产生双重存储结构：1 × NopMetaModule → 2 × NopMetaOrmModel（isDelta=true + isDelta=false，共用同一 `metaModuleId`）→ 各自的子实体集（Entity/Field/Relation/UK/Index/Domain/Dict）。同时为每个 MetaEntity 自动创建对应的 MetaTable（tableType=entity，跟随 module 不区分 isDelta）。

- **无 x:extends 的模块**：delta 定义和 full 定义内容完全相同，仍存储两份（保持查询一致性），由 `hasExtends` 检测后直接复用 full 模型作为 delta。
- **有 x:extends 的模块**：delta 定义仅含本模块声明的内容（未合并 base），full 定义为 base + delta 合并后的完整模型。
- **delta 解析降级**：若 `DslNodeLoader.loadDslNodeFromResource(filtered)` + `DslModelParser.parseWithXDef` 解析失败，delta 回退为 full 模型（内容相同），不阻塞导入。

`sourceContent` 存储原始 orm.xml 内容（CLOB），两份 NopMetaOrmModel 记录共用同一 sourceContent，可用于重解析或 diff。

`baseModuleId` 在导入时从 `x:extends` 属性推导：解析 extends 路径 → 加载 base orm.xml 获取 appId → 推导 base moduleId → 查找已导入的 base MetaModule。若 base 模块尚未导入，baseModuleId 设为 null（不阻塞导入）。

### 4.2 SQL 视图创建

用户在 UI 上输入 SQL，系统：
1. 创建 MetaTable（tableType=sql），写入 sourceSql
2. 运行时解析 SELECT 子句获取字段列表，不单独存储

### 4.3 模块发现

注册式（配置 `beans.xml`）或自动扫描 `_module` 文件。模块版本管理对齐 Maven 打包/发布粒度。

---

## 五、与 nop-report / nop-dyn 的关系

### 5.1 nop-report

```
改造前: NopReportDefinition → datasetRefs → NopReportDataset → subDatasets
        NopReportDatasource → datasourceAuths
改造后: NopReportDefinition → tableId → MetaTable
        MetaDataSource（吸收 NopReportDatasource）
```

废弃清单：

| 废弃实体 | 替代 | 说明 |
|---------|------|------|
| `NopReportDataset` | `MetaTable` | 数据集 → 逻辑表 |
| `NopReportSubDataset` | `MetaTableJoin` | 子数据集关联 → 表关联 |
| `NopReportDatasetRef` | `NopReportDefinition.tableId` | 直接引用 |
| `NopReportDatasetAuth` | `nop-auth` | 角色级访问控制 |
| `NopReportDatasource` | `MetaDataSource` | 数据源定义统一管理 |
| `NopReportDatasourceAuth` | `nop-auth` | 角色级访问控制 |

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
| QuerySpace + Driver 运行时抽象 | ORM 本身就是统一数据库映射引擎，实体 querySpace 已承担路由，不需要另加一层 |
| 仅存 delta 不存 full | base 版本删除后 full 不可查 |
| 版本用 Git tag 驱动 | 耦合 Git |
| 按模型类型共用一张表 | 各自子实体结构不同，分开更清晰 |
| 单独的 MetaOrmModelRelease 实体 | 模型记录数有限，`status` 字段足够管理生命周期 |
| 版本放在 MetaOrmModel 上 | Maven 按模块打包/发布，一个模块只有一个版本 |
| 废弃 NopReportDataset | NopReportDataset 直接运行在 EQL 上，是另外一种数据获取记录，保留 |

---

## 八、待定问题

- `isDelta=true/false` 用同一张表（列区分）还是两张表？
- SQL 视图字段解析：走 `EXPLAIN` 还是 `SELECT ... LIMIT 0` 还是用户手动录入？
- MetaTableJoin 跨表关联时，左右表所属数据源不同（例如 ORM 的 MySQL 表和 SQL 定义的 ClickHouse 表），查询执行如何路由？
- 通用 Domain 的来源：是单独维护还是从现有 ORM 模型提取？
- 数据契约的 SLA 定义格式：JSON Schema vs 自定义 DSL？
