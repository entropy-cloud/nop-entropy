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

### 2.3.2 Catalog 运行时元数据快照（P2-4）

参考 dbt Catalog 模式，从已注册 jdbc 数据源收集物理表的**运行时统计**（行数/索引/大小/分区）。**Catalog 不是 Manifest 的来源**——Manifest 是逻辑元数据快照，Catalog 是物理运行时快照，两者独立（见 `05-metadata-import.md` §1.3）。

```
NopMetaCatalog                    — 物理运行时统计快照（每表每收集一次一条，时序追加）
  ├── metaCatalogId               — PK（seq）
  ├── metaTableId                 → NopMetaTable（mandatory，被统计的逻辑表）
  ├── rowCount                    — long，表行数（SELECT COUNT(*) 结果）
  ├── sizeBytes                   — long, nullable，表物理大小（方言特定）
  ├── indexCount                  — int, nullable，索引数量（JDBC getIndexInfo 统计）
  ├── partitionCount              — int, nullable，分区数（方言特定）
  ├── lastModified                — timestamp, nullable，表最后修改时间（方言特定）
  ├── details                     — JSON CLOB（mediumtext + stdDomain json）
  │                                承载：unavailable 字段名数组、方言特定字段（DB 产品名等）
  ├── collectedAt                 — timestamp, mandatory，本次收集时间（时序键）
  └── 审计列
```

**存储形态（D1 方案 A）**：per-table 结构化行（真实列承载核心统计 + `details` JSON 承载扩展字段），`details` 用 `domain="mediumtext"` + `stdDomain="json"`（**不得用 `json-4000`**）。结构化列才可查可排序（"找最大表"、"趋势监控"）；单 CLOB 不利于这类查询。

**时序语义**：重复收集追加为新行（`collectedAt` 区分），不覆盖旧行。索引 `IX_NOP_META_CATALOG_TABLE` on `metaTableId`；时序查询用 `(metaTableId, collectedAt)` 组合。

**收集范围（D1）**：首版仅 `tableType=external` 类型表（有明确注册数据源）。entity/sql 类型表收集（需 querySpace→数据源解析）为 Non-Blocking Follow-up。

**降级策略（D1，禁止静默跳过/伪造）**：
- 行数走便携 `SELECT COUNT(*)`；索引走标准 `DatabaseMetaData.getIndexInfo()`（全方言便携，含 H2）。
- 大小/分区/lastModified 方言特定，首版不实现 → 记 `null` + `details.unavailable` 显式列出字段名（**不静默跳过整行、不伪造 0**）。
- 单表失败（SQL 异常）收集到 `errors` 不中断整批（`orm().clearSession()` 隔离）。

**schema 限定（D1）**：NopMetaTable 无 schema 列（已知缺口），`collectCatalog(schemaPattern)` 的 `schemaPattern` 限定 COUNT/索引查询的物理 schema（传入用 `<schemaPattern>.<tableName>`，不传依赖连接默认 schema）；多 schema 同名表为已知限制（彻底解决需为 NopMetaTable 增加 schema 列，属 P2-2 Protected Area 变更）。

完整规格见 `05-metadata-import.md` §四。收集入口为 `NopMetaDataSource__collectCatalog(dataSourceId, schemaPattern?)` GraphQL mutation；dataSourceId 不存在/DISABLED/非 jdbc 均显式失败（不静默跳过）。

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

#### 2.6.1 血缘采集（P2-5 裁定）

**边存储形态**：`sourceTableId`/`targetTableId` 是 plain string（存 `NopMetaTable.metaTableId`），**无 ORM to-one 关系**——遍历时按列值查询（`IX_NOP_META_LINEAGE_SOURCE` on sourceTableId / `IX_NOP_META_LINEAGE_TARGET` on targetTableId）。

**血缘来源（lineageSource）支持范围**：
- 首版支持 `manual`（`recordLineage` 手工录入，表级+列级）+ `sql_parse`（`extractLineageFromSql`，表级）。
- `open_lineage`/`hook` 保留 dict 值，首版**无专用自动填充 action**；允许通过 `recordLineage` 手工指定这两个来源（不拒绝），后续增量。

**sql_parse 范围与解析器（表级 only）**：
- 范围：仅**表级**——从 `NopMetaTable.sourceSql`（tableType=sql）的 FROM/JOIN 子句抽取源表引用，匹配目录 `NopMetaTable.tableName`，创建表级边（sourceColumn/targetColumn 留空）。target = 该 sql 表自身。**列级 SQL 解析（SELECT 列→源列映射）不在首版**，复杂度过高。
- 解析器：复用平台 `nop-orm-eql` 的 `EqlASTParser.parseFromText(text)`（纯语法解析，返回 `SqlProgram` AST）。依赖裁定：`nop-orm`（`nop-metadata-dao` 依赖它）已直接依赖 `nop-orm-eql`，故 AST 解析器**已传递可用**于 `nop-metadata-service`，无需新增 pom 依赖，也无循环依赖（`nop-orm-eql` 仅依赖 `nop-orm-model`+`nop-dao`+`nop-core`，不反向依赖 `nop-orm`）。纯语法解析不绑定 ORM session——session 绑定的 `resolvedTableMeta` 解析是独立 compile 阶段，此处不调用，仅取 `SqlTableName.getName()`/`getFullName()`（含 schema 前缀）。
- 限制（显式记录）：不展开 CTE 别名、子查询别名、动态 SQL；未匹配到目录表的引用记 unresolved（不丢）。
- 幂等：按 `(sourceTableId, targetTableId, lineageSource='sql_parse')` 去重 upsert（更新而非无限追加）。

**列级血缘对 external 表的裁定（收口 P2-2 Deferred）**：
- external 表列以 JSON 存 buildSql（A2，无字段实体）。列级血缘对 external 列**以列名字符串软引用**（sourceColumn/targetColumn 存列名），**不引入** external 字段实体（避免 ORM 结构变更 Protected Area）。
- 血缘边的列引用对所有 tableType 一致：**软引用**（列名字符串），不强制 FK 到字段实体（entity 列虽可关联 MetaEntityField，但血缘边不建硬 FK）。

**dangling（未解析引用）策略（不得静默丢弃）**：
- sql_parse 抽取的表引用若匹配不到目录表，该引用放入返回 `unresolved: [...]` 列表（原表名显式保留）+ **edge 暂不创建**。
- 约束原因：`sourceTableId` 为 `mandatory="true"`（`nop-metadata.orm.xml` NopMetaLineageEdge 列定义），ORM 层无法创建 null-source 边，故 dangling 一律进 unresolved 列表、不建悬空边——此约束由 schema 决定，非可选。日志记录 unresolved 计数。

#### 2.6.2 图遍历语义（P2-5 裁定）

遍历基于 MetaLineageEdge 边的有向图，边方向 `source → target`（数据从 source 流向 target）：

- **getUpstream(metaTableId)**：反向 BFS（沿 targetTableId→sourceTableId），收集所有能到达给定表的上游源表。含 visited 环检测。
- **getDownstream(metaTableId)**：正向 BFS（沿 sourceTableId→targetTableId），收集给定表能到达的所有下游表。含 visited 环检测。
- **getLineagePath(sourceTableId, targetTableId)**：返回 S→T **单条最短路径**（BFS 最短路径 + visited 环检测防死循环）。返回全部简单路径为 Non-Goal（循环图中成本/复杂度过高）。无路径返回显式空（不报错）。
- **getImpactAnalysis(metaTableId, columnName?)**：变更影响 = 该表下游；若提供 columnName 且存在列级边（sourceColumn/targetColumn）则按列过滤，否则回退表级（该表所有下游表）。
- **BFS 查询策略**：一次性 `findAllByQuery` 全量 MetaLineageEdge 在内存建图遍历（元数据目录量级、边数小），避免 per-hop 查询过度设计。

### 2.7 数据质量

数据质量定义在字段和表级别，描述数据的约束规则和质量期望。

```
MetaQualityRule                  — 质量规则定义
  ├── ruleName / displayName
  ├── ruleType                   — "not_null" | "unique" | "range" | "regex" | "custom_sql" | "freshness" | "volume"
  ├── entityType                 — "field" | "table" | "database"
  ├── entityId                   → MetaTable.metaTableId（规则挂载对象）
  ├── severity                   — "INFO" | "WARNING" | "ERROR"（dict meta/quality-severity，大写）
  ├── sqlExpression              — 自定义 SQL 表达式（ruleType=custom_sql 时使用）
  ├── threshold                  — 阈值（如最小行数、最大空值比例）
  ├── params                     — JSON 参数（如 min/max/regex pattern/column）
  └── extConfig

MetaQualityResult                — 质量执行结果（时序数据）
  ├── qualityRuleId              → MetaQualityRule
  ├── executeTime                — 执行时间
  ├── status                     — "PASS" | "FAIL" | "ERROR" | "SKIP"（dict meta/quality-result-status，大写）
  ├── actualValue                — 实际值
  ├── expectedValue              — 期望值
  ├── message                    — 结果描述
  └── details                    — JSON 详情
```

> severity / status 统一为大写形式，与 dict `meta/quality-severity` / `meta/quality-result-status` 一致。

内置质量规则类型：

| ruleType | 适用对象 | 说明 | params 示例 |
|----------|----------|------|-------------|
| `not_null` | field | 非空检查 | `{"column": "order_id", "threshold": 0}`（threshold=允许的空值上限） |
| `unique` | field | 唯一性检查 | `{"column": "order_id"}` |
| `range` | field | 范围检查 | `{"column": "amount", "min": 0, "max": 1000000}` |
| `regex` | field | 正则匹配 | `{"column": "order_date", "pattern": "^\\d{4}-\\d{2}-\\d{2}$"}` |
| `freshness` | table | 新鲜度检查 | `{"timestampColumn": "updated_at", "maxAgeMinutes": 60}` |
| `volume` | table | 行数检查 | `{"minRows": 1000, "maxRows": 10000000}` |
| `custom_sql` | table | 自定义SQL | `{"sql": "SELECT COUNT(*) FROM t WHERE ..."}` 或写 `sqlExpression` 列 |

质量结果时序存储，支持：
- 趋势查看（最近 N 天的通过率）
- 异常告警（连续失败 N 次）
- 影响分析（质量下降与哪些变更相关）

#### 2.7.1 质量规则执行引擎（设计决策）

**执行范围（D1）**：首版仅对 `entityType=table` 且目标 NopMetaTable 为 **external** 类型（已知注册数据源，与 P2-4 Catalog 一致）执行。`entityType=field` 首版同样支持，但**仅当其 `entityId` 指向 external NopMetaTable.metaTableId**，物理列名取自 `params.column`（字符串约定）——此约定收口"external 表无字段实体"（列结构存于 buildSql JSON，参见 §2.5.1 子方案 A2）。覆盖 4 类高价值 field 级检查（not_null/unique/range/regex）。entity/sql 类型表执行 deferred（querySpace→数据源解析，与 Catalog entity/sql 收集同源 deferred，参见 §2.3.2 / §2.5.1）。`entityType=database` 首版不支持（执行时 SKIP + details 标记 `reason=database-not-supported-first-version`）。

**物理解析路径（D1）**：`qualityRule.entityId`（external NopMetaTable.metaTableId）→ `NopMetaTable.querySpace` → `NopMetaDataSource`（`dataSource.querySpace == table.querySpace`，唯一匹配）→ `withConnection(datasourceType, connectionConfig, ...)`（复用 P2-1 callback 式连接服务）。querySpace 找不到数据源 → 显式失败抛 inline ErrorCode（不静默 SKIP 整批）。多条数据源匹配同一 querySpace 为配置错误，取第一条并记录 warning（首版不强制唯一性约束）。

**field 级列引用约定（D1）**：`entityType=field` 规则的 `entityId` 指向 external NopMetaTable.metaTableId（不指向 MetaEntityField——external 表无该实体）；物理列名取自 `params.column`（字符串）。规则在 external 表上必须先 sync 该表结构（syncExternalTables 已写入 buildSql JSON，但执行不依赖列存在性校验——物理 SQL 直接执行，方言/列不存在由数据库显式报错）。

**schema 限定（D1）**：复用 Catalog 的 `qualifyTable(schema, tableName)` 策略——执行 action 可选 `schemaPattern` 参数限定物理 SQL（`<schemaPattern>.<tableName>`）；null/空串依赖连接默认 schema。多 schema 同名表为已知限制（与 Catalog §2.3.2 同源 follow-up）。

**执行机制（D2）**：选定 **BizModel action + withConnection**（不选 nop-batch processor），理由：与本模块既有 external 执行能力（collectCatalog / syncExternalTables）一致；可被 Nop AutoTest 端到端验证；可被 GraphQL 直接暴露。`09-gap-analysis-extended.md` §4.4 的 nop-batch 建议作为"定时调度"后续选项记录，首版不用。

**Action 落点（D2）**：`NopMetaQualityRuleBizModel`（规则执行是规则对象的行为，单规则入口天然归属规则；批量入口同处聚合）：
- `executeQualityRule(qualityRuleId, schemaPattern?, context)` → 返回 `Map{qualityResultId, status, actualValue, expectedValue, message}`。单规则执行；规则不存在/目标表非 external/无注册数据源 → 显式失败抛 inline ErrorCode；`entityType=database` 与不支持规则类型组合 → SKIP + details。
- `executeQualityRulesForDataSource(dataSourceId, schemaPattern?, context)` → 返回 `Map{executedCount, results, errors}`。批量执行该 querySpace 下 external 表挂载的规则；与 collectCatalog 同入口（dataSourceId）、同 callback 模式、同 per-rule 失败隔离（try/catch + flushSession/clearSession）。

**判定语义（D3，算法规格）**：

| ruleType | 检测 SQL | actualValue | expectedValue | pass 条件 |
|----------|---------|-------------|---------------|-----------|
| `volume` (table) | `SELECT COUNT(*) FROM <限定表名>` | 行数 | 阈值（minRows/maxRows 或 threshold） | minRows ≤ actualValue ≤ maxRows（缺省边不限制） |
| `freshness` (table) | `SELECT MAX(<tsCol>) FROM <限定表名>` → age(now − maxTs) 分钟 | ageMinutes | maxAgeMinutes | ageMinutes ≤ maxAgeMinutes；缺 timestampColumn 显式失败 |
| `custom_sql` (table) | 执行 `rule.sqlExpression`（优先）或 `params.sql` | 返回值 | — | 按 `params.expectPassWhen`（`eq 0`/`gt 0`/`true`）或默认"返回 0 = pass"；SQL 不返回单数值/单布尔 → ERROR |
| `not_null` (field) | `SELECT COUNT(*) FROM <限定表名> WHERE <col> IS NULL` | nullCount | 0 或 threshold | nullCount ≤ threshold（默认 0） |
| `unique` (field) | `SELECT COUNT(*) FROM (SELECT <col> FROM <限定表名> WHERE <col> IS NOT NULL GROUP BY <col> HAVING COUNT(*)>1) d` | 重复值组数 | 0 | 0 |
| `range` (field) | `SELECT COUNT(*) FROM <限定表名> WHERE <col> IS NOT NULL AND (<col> < :min OR <col> > :max)` | 越界行数 | 0 | 0 |
| `regex` (field) | `SELECT COUNT(*) FROM <限定表名> WHERE <col> IS NOT NULL AND <col> NOT REGEXP :pattern` | 不匹配数 | 0 | 0；方言不支持 REGEXP → SKIP + details 标记 |

`details` JSON 记录 `{ruleType, tableName, column?, threshold, params, schema?, databaseProductName?}` 便于审计；失败原因（如 "nullCount=3 超过阈值 0"）写入 `message`。

**标识符注入防护（D3）**：列名 / 表名 / schema 名为 SQL 标识符（不能 PreparedStatement 占位），必须通过**白名单正则校验** `^[A-Za-z_][A-Za-z0-9_]*$` 后再拼接，否则显式失败（防止 SQL 注入）；比较值（range min/max、regex pattern 等）使用 PreparedStatement 参数绑定。`custom_sql` 的 `sqlExpression` / `params.sql` 是用户显式提供的检测 SQL（非自动注入面），记录为已知显式风险（执行前不解析/不改写，直接执行）。

#### 2.7.2 数据剖析（Profiling）— P2-7

数据剖析对表的列做统计分析（count/distinct/null/mean/stddev/min/max/median/percentiles/distribution/topValues），产出**统计结果**（区别于 §2.7.1 的 pass/fail 质量检查）。完整设计决策见 `06-data-quality-extended.md` §三（最终设计状态）。

**建模（独立实体，不复用 MetaQualityRule）**：
- `NopMetaProfilingRule`（剖析规则定义）：`profilingRuleId`(PK) / `ruleName` / `displayName` / `tableId`(→NopMetaTable) / `columns`(json-4000，空=所有列) / `stats`(json-4000，指标列表) / `sampleSize`(nullable) / `extConfig`(json) + 审计。
- `NopMetaProfilingResult`（剖析结果，per-execution 时序行）：`profilingResultId`(PK) / `profilingRuleId`(→Rule) / `metaTableId` / `snapshotTime`(时序键) / `tableStats`(mediumtext+json) / `columnStats`(mediumtext+json，列级统计数组含 numericStats/stringStats/distribution) + 审计。**tableStats/columnStats 用 `mediumtext`+`stdDomain=json`**（不得 json-4000，对齐 Manifest/Catalog）。

**执行范围（D1）**：首版仅 external 类型 NopMetaTable（已知注册数据源，与 §2.3.2 Catalog / §2.7.1 质量执行一致）。entity/sql 类型表剖析 deferred（querySpace→数据源解析同源 deferred）。

**物理解析 + 执行机制（D3）**：复用 P2-1/P2-4/P2-6 范式——BizModel action + `withConnection` callback + 无状态剖析器（`MetaTableProfiler`）。主入口 `NopMetaTableBizModel.profileTable(metaTableId, schemaPattern?, columns?, context)`；辅助入口 `NopMetaProfilingRuleBizModel.executeProfilingRule(profilingRuleId, schemaPattern?, context)`（按规则 columns/stats 执行）。列名 + 类型运行时由 `DatabaseMetaData.getColumns()` 解析（不依赖 buildSql JSON 同步）。

**统计范围 + 降级（D2，已 live 核查）**：
- 便携精确（全方言含 H2/MySQL/PG）：totalCount/distinctCount/nullCount/emptyCount/min/max/mean(`AVG`)/stddev(`STDDEV_SAMP`)/minLength/maxLength/avgLength/topValues(`GROUP BY ... LIMIT N`)。
- in-app 排序/分桶精确（全方言，仅依赖可移植 ORDER BY）：median/percentiles/distribution（H2/PG 虽支持 `PERCENTILE_CONT` 但 MySQL 无原生 percentile，故用 in-app 保证全方言精确）。
- **不可用（方言特定，null + unavailable 标记，不伪造）**：`tableStats.sizeBytes` / `tableStats.lastModified`（首版不实现，对齐 Catalog §2.3.2 降级模式）。
- **降级铁律**：不可用统计显式 null + `unavailable=[...]` 标记，不静默跳过整列/整表、不伪造值。

**时序语义 + 失败隔离**：重复剖析追加新行（snapshotTime=now，不覆盖）；单列失败 per-column try/catch 收集 errors 不中断整表；不可执行路径（表不存在/非 external/无数据源/DISABLED/非 jdbc）显式失败抛 inline ErrorCode。标识符注入防护复用 §2.7.1 D3 白名单。

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

#### 4.2.1 字段解析方案（P3-6 裁定，收口 §八待定问题）

**解析器选型**：复用平台 `nop-orm-eql` 的 `EqlASTParser.parseFromText(text)` 做纯语法 AST 解析（与 §2.6.1 血缘 `SqlSourceTableExtractor` 同一解析器、同一无 session 绑定模式）。解析器经 `nop-orm` 已传递可用，无新增 pom 依赖。

**字段名解析策略（alias 优先）**：
- `SqlExprProjection` 优先取别名：`proj.getAlias().getAlias()`（`SqlAlias.getAlias()` 返回 String，注意是 `getAlias()` 非 `getName()`）。
- 无别名时取列名：`proj.getExpr()` 为 `SqlColumnName` 时取 `getName()`。
- 无别名且为表达式列（非 `SqlColumnName`）：标记 `<expr_N>`（N 为序号），**不静默跳过、不返回空名**。

**通配符 `*`/`t.*`（SqlAllProjection）裁定：显式失败**。纯语法 AST 无法展开为具体列，首版不引入 LIMIT 0 经 ResultSetMetaData 展开路径（见下方类型获取）。失败抛 `metadata.sql-wildcard-not-supported`，要求用户改写为显式列。**不静默跳过、不返回空、不伪造**。LIMIT 0 展开通配符为 follow-up。

**多语句裁定：显式失败**。`program.getStatements().size() != 1` 抛 `metadata.sql-multi-statement`，不允许 `SELECT 1; DELETE...` 这类多语句作为视图 sourceSql。

**非 SELECT 裁定：显式失败**。顶层单条语句 `getStatementKind() != SELECT` 抛 `metadata.sql-not-select`。

**CTE / UNION 支持**：`SqlSelectWithCte`（`WITH ... SELECT`）`getStatementKind()` 返回 SELECT 但不继承 `SqlSelect`，通过钻入 `getSelect()` 取内层 `SqlSelect.getProjections()`（**首版支持**，取最外层 SELECT 输出列）。`SqlUnionSelect.getProjections()` 委托 `getFirstSelect()`（取最左侧 SELECT 的输出列，UNION 列名由左侧决定，符合 SQL 语义）。

**复杂投影列处理**：首版取最外层 SELECT 输出列，不递归展开 CTE 别名/子查询内层列类型。递归展开为 follow-up（见 Deferred）。

**字段类型获取裁定（方案 A 选定）**：首版**仅返回字段名/别名，不取类型**（`type=null`，不伪造）。理由：可移植、无需 DB 连接、与 AST 解析对齐。类型获取方案 B（`SELECT * FROM (<sourceSql>) _t LIMIT 0` 经 `ResultSetMetaData` 取列类型，需 querySpace→数据源→withConnection）与方案 C（用户手动录入 extConfig）作为 follow-up 增量。返回结构统一为 `{name, alias?, type?}`，方案 A 下 `type` 恒为 null。

**标识符安全**：sourceSql 是用户显式提供的视图定义（非自动注入面）。方案 A 不执行 SQL，无注入面。若后续采纳方案 B（LIMIT 0），须走 PreparedStatement 包装子查询、不拼接标识符（列名/表名不出现在拼接位），与 §2.7.1 D3 标识符防护原则一致。

#### 4.2.2 Action 契约（P3-1 裁定）

落点 **NopMetaTableBizModel**（操作对象是逻辑表，与 profileTable/collectCatalog 入口风格一致）：

- `@BizMutation createSqlTable(sql, tableName, metaModuleId, querySpace?, displayName?, context)` → 返回 `Map{metaTableId, tableName, tableType:"sql", fields:[{name, alias?, type?}]}`。行为：校验 sql 非空 + 为单条 SELECT（非 SELECT/不可解析/多语句/通配符显式失败抛 inline ErrorCode）→ 校验 metaModuleId 存在 → 解析字段 → 新建 `NopMetaTable(tableType="sql", sourceSql=sql, tableName, metaModuleId, querySpace?, displayName?)` → save → 返回。
- `@BizQuery previewSqlFields(sql, context)` → 返回 `Map{fields:[{name, alias?, type?}]}`（**不持久化**，纯解析）。
- `@BizQuery resolveTableFields(metaTableId, context)` → 返回**同一 wrapper 结构** `Map{fields:[{name, alias?, type?}]}`：加载 NopMetaTable → 表不存在/非 sql/sourceSql 空 显式失败 → 解析 sourceSql → 返回。

三个 action 的 fields 项结构统一为 `{name, alias?, type?}`（方案 A 下 type 恒为 null）。失败路径（非 SELECT/空/不可解析/多语句/通配符/module 不存在/表不存在/非 sql 表/sourceSql 空）均显式抛 inline ErrorCode，不静默存入脏数据、不静默返回空字段列表、不吞异常。

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
- ~~SQL 视图字段解析：走 `EXPLAIN` 还是 `SELECT ... LIMIT 0` 还是用户手动录入？~~ **已裁定（P3-6，2026-07-16）**：字段名/别名走 AST 解析（复用 `EqlASTParser`，与血缘先例一致，可移植、无需连接）；字段类型首版仅名不取类型（方案 A，`type=null` 不伪造），LIMIT 0 经 ResultSetMetaData 取类型（方案 B）为 follow-up。详见 §4.2.1。
- MetaTableJoin 跨表关联时，左右表所属数据源不同（例如 ORM 的 MySQL 表和 SQL 定义的 ClickHouse 表），查询执行如何路由？
- 通用 Domain 的来源：是单独维护还是从现有 ORM 模型提取？
- 数据契约的 SLA 定义格式：JSON Schema vs 自定义 DSL？
