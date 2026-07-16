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
10. **nop-report 是独立模块，不在本模块范围内**。`NopReportDataset` 直接运行在 EQL 上，是另一种数据获取记录，nop-metadata 不吸收、不废弃、不迁移它，两者保持独立。
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
MetaDataSource                    — 数据源定义（全局）
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
  ├── entityFieldId             → 字段引用（语义按 tableType 重载，见 §2.5.2 D2）
  ├── aggFunc                   — "sum" | "count" | "avg" | "min" | "max" | "countDistinct"
  ├── expression                — 表达式指标（entityFieldId 为 null 时使用，首版不校验内容）
  ├── format                    — "#,##0.00"
  └── currencyUnit              — "CNY" | "USD"

MetaTableDimension              — 表维度
  ├── tableId                   → MetaTable
  ├── dimensionName / displayName
  ├── entityFieldId             → 字段引用（语义按 tableType 重载，见 §2.5.2 D2）
  ├── dimensionType             — "categorical" | "temporal" | "geographical"（dict `meta/dimension-type`）
  ├── granularity               — 时间粒度（仅 dimensionType=temporal 生效；自由 string，文档约定值见 §2.5.2 D1）
  ├── format                    — 显示格式
  └── sortOrder                 — 排序

MetaTableFilter                 — 表过滤器
  ├── tableId                   → MetaTable
  ├── filterName / displayName
  ├── definition                — 条件树 JSON（对齐平台 TreeBean filter 树，见 §2.5.2 D1；列 domain `json-4000`）
  ├── isDefault                 — 是否默认过滤器（每表至多一个，唯一性首版强制，见 §2.5.2 D1）
  └── description

MetaTableJoin                   — 表关联
  ├── tableId                   → MetaTable
  ├── joinType                  — "inner" | "left" | "right"
  ├── leftEntityId / rightEntityId → MetaEntity（首版仅 entity 实体关联，见 §2.5.2 D2）
  ├── leftField / rightField    — 关联字段（字段名，属于对应实体的可用字段集合）
  └── alias                     — 右表别名
```

说明：

- **tableType=entity 的字段**：不单独存储 MetaTableField。MetaTable 通过引用的 MetaEntity（baseEntityId）和 MetaTableJoin 推断可用字段，字段定义从 MetaEntityField 动态拉取。
- **tableType=sql 的字段**：由 `sourceSql` 在运行时解析 SELECT 子句得到，不单独存储（解析方案见 §4.2.1）。
- **tableType=external 的字段**：扫描结果（列名/类型/可空/注释/序号）序列化为 JSON 数组存入 `buildSql`（见 §2.5.1）。
- **字段引用的统一解析入口**：`NopMetaTable__resolveTableFields(metaTableId)` 按 tableType 分派返回可用字段列表（entity/external/sql），是 Measure/Dimension/Join 字段引用校验的基础（见 §2.5.2 D2）。
- **MetaTableMeasure** 的 `entityFieldId` 字段引用语义按 tableType 重载（见 §2.5.2 D2）。`expression` 型指标（`entityFieldId` 为 null）跳过字段引用校验，`expression` 内容首版不校验（Non-Goal）。
- **MetaTableDimension** 的 `dimensionType` 区分普通/时间/地理维度；`granularity`（时间粒度）仅在 `dimensionType=temporal` 时生效，为自由 string（文档约定值，无 dict 约束）。
- **MetaTableFilter** 的 `definition` 为对齐平台 TreeBean filter 树的条件结构 JSON（见 §2.5.2 D1）。
- **MetaTableJoin** 的 `leftEntityId/rightEntityId` 指向 MetaEntity，`leftField/rightField` 指向字段名。关联条件是用户按需定义的，不依赖 ORM 模型已有的 MetaEntityRelation。首版仅校验 entity 实体关联（sql/external 表 join 语义为 follow-up）。

#### 2.5.2 BI 语义层校验与字段解析（P3-2~P3-5 裁定）

本节落地 plan 0700-2 的设计决策 D1（Dimension/Filter 语义规格）+ D2（字段引用校验范围/字段集合/存储方式）。

**D1 — Dimension / Filter 语义规格（补齐 §2.5 gap）**：

- **Dimension.dimensionType** 取值语义（对齐 dict `meta/dimension-type` 现值）：
  - `categorical`：普通分类维度（如地区、产品类目）。`granularity` 不生效。
  - `temporal`：时间维度（如订单日期、创建时间）。`granularity` 生效，指定时间粒度。
  - `geographical`：地理维度（如国家、城市）。`granularity` 不生效。
- **Dimension.granularity 值域**：`granularity` 列为 plain string、**无 dict 约束**（裁定为**自由 string + 文档约定**，不新增 dict，避免 meta 模块资源变更）。文档约定值：`year` / `quarter` / `month` / `week` / `day` / `hour`（语义为时间维度聚合桶，P4 查询执行时翻译为对应 SQL DATE_TRUNC/函数）。非约定值不拒绝（自由 string），仅 P4 执行时若不支持则显式失败。
- **Filter.definition JSON 结构**：裁定对齐平台 **TreeBean filter 树**（非整个 QueryBean；QueryBean 含 limit/offset/orderBy，过滤只是其 `filter` 子树）。结构：`{type, name?, value?, children?}`，由 `FilterBeans` 构建的标准条件树——
  - 叶子条件：`{type:"eq"|"ne"|"gt"|"ge"|"lt"|"le"|"like"|"in"|"between"|..., name:<字段名>, value:<值>}`。
  - 组合条件：`{type:"and"|"or", children:[<子条件>...]}`；`{type:"not", children:[<单子条件>]}`。
  - 反序列化校验：`JsonTool.parseBeanFromText(definition, TreeBean.class)`，不可反序列化或结构非法 → 显式失败抛 inline ErrorCode（不静默存入）。
- **Filter.definition 列容量**：`definition` 列为 `json-4000`（precision 4000）。复杂嵌套条件序列化超 4000 字符时由列约束显式失败（不截断、不静默存入截断后的脏数据）。
- **Filter.isDefault 唯一性**：裁定首版**强制每表至多一个默认过滤器**（`isDefault=true` 在同一 `metaTableId` 下唯一）。保存 `isDefault=true` 时校验该表已无其他 `isDefault=true` 的过滤器，违反显式失败。`isDefault=false` 不受限。默认过滤器的运行时自动应用在 P4 查询执行（Non-Blocking Follow-up）。

**D2 — 字段引用校验范围 + 跨表字段集合 + 存储方式**：

- **校验落点**：裁定**引入 save override 新模式**——在 Measure/Dimension/Filter/Join 的 BizModel 中重写 `CrudBizModel.save(Map, IServiceContext)`，在持久化前执行校验。理由：`save` 是 GraphQL mutation 的统一入口，override 一次覆盖所有 save 路径（UI/GraphQL/xbiz），无需新增自定义 action 名、不破坏既有 CRUD 契约。校验通过后委托 `super.save(...)` 走默认持久化逻辑。
- **可用字段集合范围（按 tableType 分派）**：
  - `entity` 表：**手动 query**——`NopMetaTable.baseEntityId` 为 plain string 列、无 ORM relation；`NopMetaEntity` 亦无 fields to-many。按 `baseEntityId` 作 `metaEntityId` 查 `NopMetaEntityField` 集合。**`baseEntityId` 为 null（ORM nullable）时显式失败抛 inline ErrorCode**（不静默空集、不静默存入悬空引用）。
  - `external` 表：解析 `buildSql` JSON（结构：JSON 数组，元素 key 含 `columnName`/`dataType`/`nullable` 等，见 §2.5.1 + `NopMetaDataSourceBizModel.serializeColumns`）取 `columnName` 集合。JSON 损坏/非数组 → 显式失败。
  - `sql` 表：调 P3-1 SELECT 字段解析器（`SqlSelectFieldExtractor`，plan 0700-1 产出）解析 `sourceSql` 得字段名集合。解析失败路径（非 SELECT/多语句/通配符/空）显式失败（见 §4.2.1）。
- **Measure/Dimension 字段引用存储方式（硬前置子项裁定）**：裁定**复用 `entityFieldId` 列存字段引用，语义按 tableType 重载**（option a）：
  - `entity` 表：`entityFieldId` 存 `NopMetaEntityField.metaEntityFieldId`（主键硬匹配，校验该 ID 属于 `baseEntityId` 实体的字段集合）。
  - `external` / `sql` 表：`entityFieldId` 存**字段名字符串**（语义重载——external 存 `buildSql` JSON 中的 `columnName`，sql 存 SELECT 解析出的字段名）。校验字段名属于该表可用字段集合。
  - 拒绝 option b（字段名存 `extConfig`）：`extConfig` 为自由扩展 JSON，语义承载字段引用会与扩展属性混淆，且 `entityFieldId` 列已存在、语义明确（"字段引用"），重载比新增存储位更内聚。
  - **expression 型 Measure**：`entityFieldId` 为 null（表达式指标，用 `expression` 列）时跳过字段引用校验，`expression` 内容首版不校验（Non-Goal）。
- **Join 字段校验范围**：首版**仅 entity 实体关联**——`leftEntityId`/`rightEntityId` 校验对应 `NopMetaEntity` 存在；`leftField` 属于 `leftEntityId` 实体字段集合、`rightField` 属于 `rightEntityId` 实体字段集合（经 entity→`NopMetaEntityField` 解析）。`joinType` 已由 dict 校验。sql/external 表的 join 语义为 follow-up（item 裁定首版范围）。
- **resolveTableFields 与 plan 0700-1 的 ownership**：plan 0700-1 的 `resolveTableFields` 为 **sql-only** 版本；本裁定将其**扩展为全 tableType**（entity/external/sql 分派）。entity/external 分派独立可用；sql 分派复用 0700-1 的解析器。
- **降级（不静默通过）**：字段集合解析失败（sql sourceSql 不可解析 / external buildSql JSON 损坏 / entity baseEntityId null）→ 显式失败抛 inline ErrorCode（不静默跳过校验、不静默存入悬空引用、不吞异常）。

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

#### 2.6.1 血缘采集（P2-5 表级 + P2-5+ 列级裁定）

**边存储形态**：`sourceTableId`/`targetTableId` 是 plain string（存 `NopMetaTable.metaTableId`），**无 ORM to-one 关系**——遍历时按列值查询（`IX_NOP_META_LINEAGE_SOURCE` on sourceTableId / `IX_NOP_META_LINEAGE_TARGET` on targetTableId）。

**血缘来源（lineageSource）支持范围**：
- 首版支持 `manual`（`recordLineage` 手工录入，表级+列级）+ `sql_parse`（`extractLineageFromSql` 表级 + `extractColumnLineageFromSql` 列级）。
- `open_lineage`/`hook` 保留 dict 值，首版**无专用自动填充 action**；允许通过 `recordLineage` 手工指定这两个来源（不拒绝），后续增量。

**sql_parse 表级范围与解析器（P2-5 裁定）**：
- 范围：**表级**——从 `NopMetaTable.sourceSql`（tableType=sql）的 FROM/JOIN 子句抽取源表引用，匹配目录 `NopMetaTable.tableName`，创建表级边（sourceColumn/targetColumn 留空）。target = 该 sql 表自身。
- 解析器：复用平台 `nop-orm-eql` 的 `EqlASTParser.parseFromText(text)`（纯语法解析，返回 `SqlProgram` AST）。依赖裁定：`nop-orm`（`nop-metadata-dao` 依赖它）已直接依赖 `nop-orm-eql`，故 AST 解析器**已传递可用**于 `nop-metadata-service`，无需新增 pom 依赖，也无循环依赖（`nop-orm-eql` 仅依赖 `nop-orm-model`+`nop-dao`+`nop-core`，不反向依赖 `nop-orm`）。纯语法解析不绑定 ORM session——session 绑定的 `resolvedTableMeta` 解析是独立 compile 阶段，此处不调用，仅取 `SqlTableName.getName()`/`getFullName()`（含 schema 前缀）。
- 限制（显式记录）：不展开 CTE 别名、子查询别名、动态 SQL；未匹配到目录表的引用记 unresolved（不丢）。
- 幂等：按 `(sourceTableId, targetTableId, lineageSource='sql_parse')` 且 `sourceColumn IS NULL` 去重 upsert（更新而非无限追加）。`sourceColumn IS NULL` 过滤隔离列级边——见列级隔离裁定。

**sql_parse 列级范围与解析器（P2-5+ 裁定，D1/D2/D3）**：

> 列级 sql_parse 解析 SELECT 输出列→源表源列映射。独立 action `extractColumnLineageFromSql(metaTableId)`，与表级 `extractLineageFromSql` 并列（D2：独立 action，语义清晰、不破坏表级既有契约）。

- **列引用归属解析（D1，仅用句法字段）**：从 FROM/JOIN 的 `SqlSingleTableSource` 手建 `scopeName(alias 或表名) → simpleTableName` 映射；对 projection 表达式内每个 `SqlColumnName` 用 `getOwner().getName()` 查映射归属源表。**不得依赖 resolution 字段**（`getTableSource()`/`getResolvedOwner()`/`getResolvedTableMeta()` 纯 parse 后为 null，会 NPE）。表达式列（`a+b`）walk expr 子树收集所有 `SqlColumnName` 节点（`forEachChild` 已递归）。
- **可解析形态（首版支持）**：
  - 限定符列引用 `SELECT t.col` / `SELECT t.col AS out`：`owner.getName()` 查 alias→table 映射归属，sourceColumn=col，targetColumn=out(或 col)。
  - 无别名列引用 `SELECT col`：**仅当 FROM 仅一张源表时**归属该唯一源表；**FROM 多表时一律不可归属**（纯句法无法判断 col 属于哪张表——多表即歧义，非仅同名列歧义），进 unresolved/transformType=derived。
  - 表达式列 `SELECT a+b AS total`：transformType=derived，walk expr 子树收集所有列引用节点，各列按其 owner 归属。一个表达式输出列引用 N 个源列 → 产出 N 条 derived 边。
- **transformType 判定（D3，AST 节点类型匹配）**：直接列引用（projection expr 为 `SqlColumnName`）→ `direct`；表达式/函数包裹列（非聚合）→ `derived`；聚合函数检测**优先 `expr instanceof SqlAggregateFunction`**（非函数名白名单）→ `aggregated`。`transformExpr`（ORM 列名）首版留空（derived/aggregated 记原文为 follow-up）。
- **列存在性不校验（软引用裁定）**：sourceColumn/targetColumn 存列名字符串原样，不校验列是否真实存在（对齐 external 列级软引用裁定）。
- **CTE/子查询范围（D3）**：首版支持「直查 FROM 源表 [JOIN 源表]」的列映射；CTE 别名列穿透、子查询内层列穿透、`SELECT *` 通配符 → 显式记 unresolved（不伪造列映射、不静默丢弃）。
- **幂等键（D3）**：列级边按 `(sourceTableId, targetTableId, sourceColumn, targetColumn, lineageSource='sql_parse')` 五元组去重 upsert（比表级多 sourceColumn/targetColumn，不含 transformType，重抽更新 transformType）。
- **表级/列级 upsert 查询隔离（D2，In Scope）**：表级 `upsertSqlParseEdge` 查询补 `sourceColumn IS NULL` 过滤（让表级只匹配表级边）；列级 upsert 用列级五元组。二者共存各管各的幂等，重跑表级不误更新列级边。列级 action 不强制先建表级边（独立语义）。

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

#### 2.7.3 质量检查点编排（Checkpoint）— P2-8

质量检查点（`NopMetaQualityCheckpoint`）把「命名验证集 = 一组规则×表的验证配置」收口为一个可手动批量执行的对象，产出执行摘要。使质量执行从 §2.7.1 的「单规则 / 按数据源」扩展到「用户自定义命名验证集」，为质量评分（`06-data-quality-extended.md` §五）和后续定时调度（follow-up）提供稳定的批量执行入口。设计意图来源 `06-data-quality-extended.md` §四（Checkpoint 模型 + 执行流程 + 执行动作），本节为落地裁定。

**模型结构（D1）**：单实体 `NopMetaQualityCheckpoint`，验证/动作配置存 JSON 列（**非独立子实体**，与 §设计结论 #9「不引入额外抽象层」一致）：
- `checkpointId`(PK) / `checkpointName` / `displayName` / `metaModuleId`(→NopMetaModule，optional) / `description`
- `validations`(mediumtext+json)：`[{ruleIds:[...], tableIds:[...]}]`（一个 checkpoint 可含多组验证配置）
- `actions`(json-4000)：`[{actionType:"store", enabled:true}]`（首版仅 store）
- `status`(dict `meta/checkpoint-status`：ACTIVE/PAUSED/DISABLED，**大写对齐 status 类 dict 惯例**如 quality-result-status) / `extConfig`(json) + 审计列
- 索引 `IX_NOP_META_QCHECKPOINT_MODULE`(metaModuleId)；to-one 关系 Checkpoint→Module
- 不建 validations/actions 子实体（JSON 列）；不存 schedule（cron 定时为 follow-up，非本 plan）

**规则选择语义（D2）**：规则集 = ∪（每组 validations 的（显式 `ruleIds`）∪（`tableIds` 下挂载的 `NopMetaQualityRule where entityId ∈ tableIds`））；**去重**（同一 ruleId 多组配置/多次命中只执行一次）；`entityType=database` 规则在执行时按既有 §2.7.1 D1 写 SKIP 结果行（**不剔除**，保持与单规则一致语义）。ruleId 不存在 / tableId 不存在 → 记入摘要 `errors` 不中断（per-item 隔离）。解析后规则集为空 → **显式失败**抛 inline ErrorCode `metadata.checkpoint-no-rules`（不静默返回空集、不伪造零计数）。跨模块 `includeInherited` 规则继承解析为 follow-up（不保留无法解析的 flag，避免 hollow）。

**执行机制 + 复用（D3）**：新增无状态 `MetaQualityCheckpointExecutor`（`.../service/quality/`），内部**复用既有 §2.7.1 单规则执行路径**——resolve 目标表（任意 tableType）→ `MetaTableReferenceResolver.resolve` → `TableReferenceExecutor.execute` → `MetaQualityRuleExecutor.judge` → 写一行 `NopMetaQualityResult`。checkpoint executor **不自建连接**（连接由 `TableReferenceExecutor` 按 ref 形态分派）、**不重写判定逻辑**（judge 算法不在本层重复）。per-rule try/catch + `flushSession/clearSession` 失败隔离（对齐 `executeQualityRulesForDataSource` 模式：失败规则进 errors、已 flush 结果保留、后续规则继续）。结果写入逻辑 `appendQualityResult` 提取为共享 helper（`service/quality/QualityResultWriter`）供单规则路径与 checkpoint 路径共用，避免重复造轮子（不复制逻辑、不提升 BizModel 私有方法可见性污染边界）。

**动作边界（D4）**：`actionType=store` 随每条规则结果**自动生效**（写 QualityResult 行即 store）。`actions` 为空/null 视为合法（等价仅 store，store 为隐式默认）。配置了 store 之外的动作类型（notify/webhook/update_docs）且 `enabled=true` → executeCheckpoint 时**显式失败**抛 inline ErrorCode `metadata.checkpoint-action-not-supported`（不静默跳过、不伪造执行）。notify/webhook/update_docs 实现为独立 follow-up。

**手动触发 + 状态门禁（D5）**：`executeCheckpoint` 仅手动触发（GraphQL `@BizMutation` action）；`status=PAUSED/DISABLED` 的 checkpoint 执行时**显式失败**抛 inline ErrorCode（不静默跳过、不静默返回空摘要）。cron 定时调度为 follow-up（`06-data-quality-extended.md` §1.3，nop-job/nop-batch 适配）。

**执行摘要**：返回 `{checkpointId, executedCount, passCount, failCount, errorCount, results:[...], errors:[...]}`，计数与写入的 QualityResult 行一致（pass/fail/error 计数源自 judgment.status）。

**标识符注入防护**：checkpoint 本层不拼接 SQL（判定 SQL 全在 §2.7.1 D3 的 judge 内），无新增注入面。

**与 §七（拒绝额外抽象层）的关系**：Checkpoint 复用既有 ORM + table-reference + judge 执行链，不引入额外 Driver/QuerySpace/动作框架抽象层。store 之外的动作不走「可插拔动作框架」，而走「配置后显式失败」的最简路径（待 follow-up 按动作类型独立设计）。

#### 2.7.4 质量评分（QualityScore）— P2-9

质量评分（`NopMetaQualityScore`）把 §2.7.1 的「单条规则 PASS/FAIL」上升到「可量化的维度健康度 + 总分 + 趋势」，为逻辑表（`NopMetaTable`）计算可解释的质量评分。使数据质量从「规则级 pass/fail 列表」收口到「per-table 时序评分行」，参考 Apache Griffin 的评分维度模型（`06-data-quality-extended.md` §五为设计意图来源，本节为落地裁定）。

**模型结构（D1，v1 table 级）**：单实体 `NopMetaQualityScore`，评分对象为 `NopMetaTable`（质量规则挂载点，§2.7.1 D1）：
- `qualityScoreId`(PK) / `metaTableId`(→NopMetaTable.metaTableId，mandatory，**v1 唯一支持**；entity 级评分见 Deferred) / `scoreTime`(mandatory，时序键) / `overallScore`(double 0~100，mandatory)
- `dimensionScores`(mediumtext+json)：`{completeness, accuracy, consistency, timeliness, uniqueness}` 各 0~100 或 null（对齐 Manifest/Catalog/Profiling 的 JSON 列决策）
- `ruleSummary`(json-4000)：`{totalRules, passedRules, failedRules, errorRules, skipRules}`（SKIP 单列，不计入 failed）
- `trend`(json-4000)：`{previousScore, changeRate, trendDirection(improving/stable/degrading, dict meta/quality-trend-direction，小写对齐类型/分类类 dict 惯例)}`
- `extConfig`(json) + 审计列；to-one 关系 Score→Table；索引 `IX_NOP_META_QSCORE_TABLE`(metaTableId, scoreTime) 时序查询
- entity 级评分（NopMetaEntity 维度）为 Deferred（需额外 entity→table 规则解析路径）；不引入独立子实体存维度分（JSON 列，与 §设计结论 #9 一致）

**维度映射 ruleType → dimension（D2）**：
- `not_null` → completeness；`volume` → completeness
- `unique` → uniqueness
- `range` → accuracy；`regex` → accuracy
- `freshness` → timeliness
- `custom_sql` → consistency（默认；可通过 `rule.extConfig.dimension` 覆盖，覆盖值不在五维内则计 consistency）

**结果状态计入（D2）**：PASS 计通过；FAIL/ERROR 计未通过（**ERROR 保守计未通过**）；**SKIP 不计入任何维度的分子分母**（单列 `ruleSummary.skipRules`）。

**无规则维度 / SKIP-only 维度降级（D2，对齐 Profiling 降级铁律）**：
- 某维度**无任何挂载规则** → `dimensionScores` 该维度 null + 显式 `unavailable=["no-rules"]` 标记（不伪造 0/100）
- 某维度有规则但其最新结果**全为 SKIP**（无任何可计数 PASS/FAIL/ERROR）→ 该维度视为不可评，`dimensionScores` 该维度 null + `unavailable=["skipped"]` 标记（**不计 0、不产生 NaN**，与"无规则维度"同等降级处理）

**计算公式（D3）**：维度分 = 该维度内 PASS/(PASS+FAIL+ERROR) × 100（SKIP 排除在分母外；SKIP-only 维度 → null，见 D2）；总分 = Σ(非 null 维度的维度分 × 权重) / Σ(非 null 维度的权重)（**仅对非 null 维度归一化权重**）；默认权重（design 06 §5.2）：completeness 0.3 / accuracy 0.3 / consistency 0.2 / timeliness 0.1 / uniqueness 0.1。**全部维度 null**（对象无任何可评规则或全 SKIP）→ 显式失败抛 inline ErrorCode（不静默返回 0、不伪造）。

**时间窗口（D4）**：默认取每条规则**最新一条** QualityResult（按 `executeTime DESC` 取首）参与评分；不支持历史窗口聚合（follow-up）。**规则无任何 QualityResult（从未执行）→ 视为不可评分，按 SKIP 等价处理**（不计入维度分子分母，计入 `ruleSummary.skipRules`），不静默忽略。

**趋势（D5，先查后写）**：读取同 (metaTableId) 上一条 QualityScore（按 `scoreTime DESC` 取首，**此时新行尚未写入**），changeRate = overall − previous；trendDirection：|changeRate| < 阈值(默认 1.0) → stable，>0 → improving，<0 → degrading；无历史 → trend null + 标记。

**不可评路径显式失败（D6）**：metaTableId 不存在（NopMetaTable 查不到）/ 表无任何挂载规则 / 所有规则最新结果全 SKIP（全维度 null）/ 算出全维度 null → 显式失败抛 inline ErrorCode（不静默 0 分、不伪造）。

**执行机制 + 入口**：新增无状态 `MetaQualityScorer`（`.../service/quality/`）：读 QualityResult → 维度聚合 → 加权 → 趋势 → 返回结构化 score（不自建连接，纯读 + 计算）。BizModel action `computeQualityScore(metaTableId, context)`（`NopMetaQualityScoreBizModel`，`@BizMutation`）写新 QualityScore 行，返回 `{scoreId, overallScore, dimensionScores, ruleSummary, trend}`。**基于 ProfilingResult 的评分**为 Deferred（剖析产出统计值非 pass/fail，映射语义不同）；**运行时维度权重覆盖**为 Deferred（首版全局默认权重）。

**标识符注入防护**：评分层不拼接 SQL（纯读 QualityResult + 计算），无注入面。

**与 §七（拒绝额外抽象层）的关系**：评分复用既有 QualityResult（§2.7.1 写入）+ NopMetaTable（§2.5 挂载点），不引入额外评分引擎抽象层。维度映射为静态表 + extConfig 覆盖，不走「可插拔维度策略框架」（待 follow-up 按需独立设计）。

### 2.8 元数据变更事件模型（P-event）

本节落地 plan 2026-07-17-0228-1 的设计决策 D1（持久化 + 存储形态）+ D2（发布机制 + 消费路径）+ D3（发布范围 + 批量粒度）。把 roadmap `未建模实体` 表的最后一个实体 `MetaModelChangedEvent` 从未建模推进到 landed：建模 + 事件发布 + 至少一个事件消费路径，使元数据变更可被通知、审计、下游同步。完整设计规格见 `10-event-model.md`（final）。

**模型结构（D1，持久化到 DB）**：单实体 `NopMetaModelChangedEvent`（时序追加行，不覆盖）：

- `modelChangedEventId`(PK, seq) / `eventType`(dict `meta/change-event-type`：ENTITY_CREATED|ENTITY_UPDATED|ENTITY_DELETED) / `entityType`(NopMetaModule|NopMetaTable|NopMetaDataSource|...) / `entityId` / `entityName`
- `changeSource`(IMPORT|UI|API|SYNC，**plain string + 文档约定**，对齐 dimension-type/granularity 模式，dict 化为 follow-up)
- `beforeSnapshot`(mediumtext+stdDomain=json，仅 UPDATE/DELETE 时有值) / `afterSnapshot`(mediumtext+stdDomain=json，仅 CREATE/UPDATE 时有值) — **不得 json-4000**，对齐 Manifest/Catalog/Profiling 的 JSON 列决策
- `changedBy` / `changeTime`(mandatory) / `transactionId`(nullable，批次/单操作 correlation key) / `extConfig`(json-4000) + 审计列
- 索引 `IX_NOP_META_EVENT_TYPE_TIME`(entityType, changeTime) 时序查询 + `IX_NOP_META_EVENT_SOURCE`(changeSource)

**存储形态裁定**：事件**持久化到 DB**（非纯内存）。理由：审计日志需可追溯历史；纯内存事件重启后丢失；持久化事件天然支持 GraphQL query（审计/下游拉取）。收口 Open Question「事件是否持久化？」→ 是。

**发布机制 + 消费路径（D2）**：

- **发布机制（主路径）**：**直接 DB 写入事件行**——事件发布 helper（`service/event/MetaModelChangedEventPublisher`，IoC bean，`@Inject IEntityDao<NopMetaModelChangedEvent>`）在写路径内 `saveEntity` 一条 `NopMetaModelChangedEvent`。理由：不依赖 `IMessageService` 订阅者注册机制（首版无订阅者）、最简单可测、与审计日志目标一致、可被 GraphQL query 直接暴露。
- **IMessageService overlay（可选，已 live 核实可用）**：`nop-metadata-service` pom 依赖链 `nop-metadata-service → nop-sys-dao` 传递 `SysDaoMessageService`（`implements IMessageService`，来自 `nop-message-core`），可直接 `@Inject`。首版**不强制**叠加，topic 命名 `nop-metadata.{entityType}.changed` 为 follow-up。
- **消费路径（首版至少一条）**：**GraphQL query 查询事件历史**（审计/下游拉取）。`NopMetaModelChangedEvent` CRUD 自动暴露后，`__findPage` 可按 `entityType`/`changeSource`/`changeTime`/`transactionId` 过滤查询。收口「至少一条消费路径可用」且不需要推送基建。

收口 Open Question「是否 GraphQL Subscription？」→ 首版用 query（拉取）非 subscription（推送）；subscription 依赖推送基建为 follow-up。

**发布范围 + 批量粒度（D3）**：

- **范围（首版）**：覆盖关键元数据写路径：
  - 关键 mutation action（持久化成功后调 helper 写主实体级事件行）：`importOrmModel`（IMPORT）/ `releaseModule`（版本发布）/ `syncExternalTables`（SYNC）/ `createSqlTable`（UI/API）。
  - 核心实体（`NopMetaModule` / `NopMetaTable`）通用 CRUD 走 **save override（CREATE+UPDATE）+ delete override（DELETE）**——二者独立，save 不覆盖 delete。其余实体作为 follow-up。
- **批量粒度（显式裁定，收口 Open Question「批量导入是否合并事件？」）**：主实体级记录（不逐子实体、不合并丢失）：
  - `importOrmModel` 记 1 行 Module CREATED 事件（changeSource=IMPORT）；`syncExternalTables` 记 1 行 DataSource UPDATED 事件（changeSource=SYNC，「外部表已同步」）。
  - 子实体细粒度事件（per-row Entity/Field/Table）**deferred**（避免一次导入产生数十行事件 + 大量快照 JSON 膨胀）。
  - 同一批操作共享同一 `transactionId`（correlation key），支持未来关联同批的细粒度扩展。
- **单操作 transactionId**：单次 save/delete override 触发的事件生成 per-op UUID 作为 transactionId。
- **beforeSnapshot 获取**：save override 在调 `super.save()` **前**按 PK 加载旧状态（null=CREATE，非 null=UPDATE）；delete override 在调 `super.delete()` **前**按 PK 加载旧状态。实体不存在（DELETE 已删）则不发事件。
- **事件行写入时序（关键，避免幽灵事件）**：**事件行在 super.save/super.delete 成功后写入**——before 快照在 super.save **前加载**，event 行在 super.save **成功后持久化**。避免业务写失败/事务回滚时产生幽灵事件（事件行已写但业务写未成功）。

**失败路径（显式，不静默）**：快照序列化失败等异常显式抛 inline ErrorCode（不静默吞掉、不静默跳过事件发布）。不伪造缺失快照：ENTITY_CREATED 有 afterSnapshot、ENTITY_UPDATED 有 before+after、ENTITY_DELETED 有 before。

**Out-of-Scope（follow-up）**：UI 实时推送（WebSocket/SSE）/ GraphQL Subscription（依赖推送基建）/ 搜索索引自动更新（需搜索引擎）/ 全量 32 实体 CRUD 事件覆盖（首版关键路径 + 核心实体）/ 分布式事件总线 + 可靠投递 + 跨进程（首版事件与业务写同事务或紧邻写后）/ 事件清理/归档策略 / `changeSource` dict 化。

**与 §七（拒绝额外抽象层）的关系**：事件模型复用既有 ORM 持久化 + GraphQL CRUD 自动暴露，不引入独立 EventBus 类（平台无独立 EventBus，首版直接 DB 写为主路径，IMessageService 为可选 overlay）、不引入事件总线/可靠投递/跨进程抽象层（follow-up）、不引入推送基建抽象层（follow-up）。事件发布 helper 为无状态 service 层 IoC bean（`@Inject IEntityDao`），不自造连接、不复制持久化逻辑。

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

### 4.4 查询执行（P4-1 裁定，落地 D1/D2）

本节落地 plan 0800-1 的设计决策 D1（单表查询三路分派）+ D2（sql 表 querySpace 归属）。这是 P4 联邦查询的执行地基，对任意 `tableType`（entity/external/sql）的逻辑表返回行数据。与 §设计结论 #9 + §七 一致——所有查询走现有 ORM 层，实体 `querySpace` 字段已承担路由，**不引入额外 Driver/QuerySpace 抽象层**。

**统一查询入口（D1）**：落点 `NopMetaTableBizModel`（操作对象是逻辑表，与 profileTable/createSqlTable 入口风格一致）：

- `@BizQuery queryTableData(metaTableId, filter?, limit?, offset?, context)` → 返回 `Map{tableType, totalCount?, items:[{行数据}]}`。`filter` 为平台 **TreeBean filter 树**（与 §2.5.2 D1 `MetaTableFilter.definition` 同结构，非整个 QueryBean；过滤只是 QueryBean 的 `filter` 子树）。`limit`/`offset` 为可选分页。

**D1 — 三路分派（按 tableType）**：

| tableType | 执行机制 | querySpace 归属 | 失败语义 |
|-----------|---------|----------------|---------|
| `entity` | 经平台 ORM：按 `entityName` 取其 `IOrmEntityDao`（`daoProvider().dao(entityName)`）→ `findAllByQuery(query)`（filter/limit/offset 委托 `QueryBean`）→ 按实体列名投影转 `Map`（列名取自 `IEntityModel.getColumns()`）。注：不使用 `orm().findListByQuery(QueryBean)`，因其经 MdxQueryExecutor 要求 `QueryBean.fields` 非空（字段投影/聚合入口），非"取全部实体行"语义 | 来自 `NopMetaEntity.querySpace`（import 时写入 `NopMetaTable.querySpace`）；ORM 内部按实体 querySpace 路由 | **实体未注册于运行时 `IOrmSessionFactory` 时显式失败抛 inline ErrorCode（不静默空集）**——经 `orm().isValidEntityName(entityName)` 前置校验 |
| `external` | 经 `IMetaDataSourceConnectionService.withConnection` 跑限定表名的原生 SQL（`SELECT ... FROM <table> [WHERE] [LIMIT/OFFSET]`） | querySpace→`NopMetaDataSource`（D2 解析） | querySpace 无数据源/DISABLED/非 jdbc（由 withConnection 抛）显式失败 |
| `sql` | 经 `withConnection` 执行 `sourceSql`（包一层子查询 `SELECT * FROM (<sourceSql>) _t [WHERE] [LIMIT/OFFSET]`） | 见 D2 | querySpace null/无数据源/DISABLED/非 jdbc/sourceSql 不可解析 显式失败 |

返回字段集合与 `MetaTableFieldResolver`（§2.5.2 D2）对应 tableType 分派一致。entity 路径前置——实体须注册于运行时 `IOrmSessionFactory`（即 `orm().isValidEntityName(entityName) == true`），否则显式失败抛 inline ErrorCode（**不静默空集**）。

**filter→WHERE 翻译 + 注入防护（external/sql 路径）**：复用 §2.7.1 D3 标识符注入防护——列名/表名/schema 名为 SQL 标识符，拼接前必须通过白名单正则 `^[A-Za-z_][A-Za-z0-9_]*$` 校验；比较值（eq/gt/in/between 等的 value）使用 PreparedStatement 参数绑定。首版支持 TreeBean 标准叶子条件（eq/ne/gt/ge/lt/le/like/in/between/is-null/not-null）+ 组合条件（and/or/not）。

**方言范围（external/sql 路径）**：首版分页与 WHERE 翻译限定 H2 / MySQL / PostgreSQL（`LIMIT/OFFSET` 便携语法）。其他方言（由 `DatabaseMetaData.getDatabaseProductName()` 运行时识别）执行时**显式失败抛 inline ErrorCode**（不静默跳过、不伪造结果）。

**D2 — sql 表 querySpace 归属裁定**：

- **首版规则：sql 表 `querySpace` 必须非 null 且匹配到一个 `NopMetaDataSource`**。`querySpace` 为 null 或匹配不到 `NopMetaDataSource` 时**显式失败抛 inline ErrorCode**（不静默空集、不伪造路由）。
- **"平台 ORM querySpace fallback" 分支首版不做**（移入 Non-Blocking Follow-up）。理由：无清晰机制对平台 querySpace 跑任意用户 SQL 文本（平台 querySpace 由 `IOrmSessionFactory` 管理，无通用 JDBC 连接入口跑裸 SQL）；首版强制 sql 表显式关联一个已注册外部数据源。
- querySpace→数据源解析由共享组件 `MetaDataSourceResolver`（`.../service/datasource/`）承担：`NopMetaDataSource.querySpace == 目标 querySpace` 的 `findFirstByQuery` 首条（多匹配取首条，首版不强制唯一性、不记 warning，与 baseline §2.7.1 D1 现状一致）。找不到/DISABLED 显式失败抛 inline ErrorCode。

**querySpace→数据源解析共享组件**：`MetaDataSourceResolver.resolveActiveOrThrow(IEntityDao<NopMetaDataSource>, querySpace)` 返回 ACTIVE 数据源；querySpace null/无匹配→`metadata.datasource-resolve-not-found`；匹配到 DISABLED→`metadata.datasource-resolve-disabled`；多匹配→首条（`findFirstByQuery`）。本组件独立实现，**不强制重构既有三处 `resolveDataSourceOrThrow` 重复**（NopMetaTableBizModel profiling / NopMetaQualityRuleBizModel / NopMetaProfilingRuleBizModel，见 plan 0800-1 Non-Blocking Follow-up）——既有实现行为正确（取首条、显式失败），重复不构成 live defect。

**失败路径显式化（不静默空集、不吞异常，对齐 Minimum Rules #24）**：表不存在 / querySpace 无数据源 / DISABLED / 非 jdbc（由 withConnection 抛）/ sql querySpace null 或无匹配 / 实体未注册 / 不支持的方言 / sourceSql 不可解析 / 未知 tableType 均显式失败抛 inline ErrorCode。

> 0800-1 单表查询范围到此。跨表 JOIN（P4-2）+ 指标/维度聚合（P4-3）见下两节（plan 0800-2 落地）。entity/sql 表的 Catalog/质量/剖析执行扩展见 §4.4.3（plan 1905-1 落地）。

#### 4.4.3 Catalog/Quality/Profiling 执行覆盖扩展（P2 执行覆盖，落地 D1-D5）

本节落地 plan 1905-1 的设计决策 D1（entity 路径执行机制）+ D2（sql 路径执行机制）+ D3（共享 table-reference 分派契约）+ D4（能力边界）+ D5（sql 视图合成列处理）。把反复 deferred 的「entity/sql 类型表的 Catalog 收集 / 质量规则执行 / 数据剖析」从 follow-up 推进到 landed：三大执行器（`MetaCatalogCollector` / `MetaQualityRuleExecutor` / `MetaTableProfiler`）的覆盖范围从 external-only 扩展到 entity + sql 类型表，收口"任意 tableType 的逻辑表都可目录化、可质量检查、可剖析"。

**D1 — entity 路径执行机制（平台 JDBC Connection 复用现有 executor）**：

entity 表取**平台事务 JDBC Connection**（`IJdbcTransaction.getConnection()`，经 `ITransactionTemplate.runInTransaction(entityQuerySpace, SUPPORTS, txn -> ...)` 取平台库连接）+ 物理表名 `NopMetaEntity.tableName`，**原样传入现有三大 executor**（不经 EQL、不重写统计逻辑）。

- **理由（不选 `orm().executeQuery`）**：`orm().executeQuery` 经 EQL 编译器校验函数名——`STDDEV_SAMP` / `FORMATDATETIME` 等被 EQL 判为 unknown-function（§4.4.2 D7 已实测 FORMATDATETIME 被拒）。entity 路径若走 EQL 会丢 STDDEV 等聚合函数，无法满足"统计能力与 external 一致、无降级"。
- entity 物理表在平台库，平台 Connection 直达（`IJdbcTransaction.getConnection()` 返回平台事务的 JDBC 连接，`JdbcTemplateImpl` 等已用此取平台连接；nop-metadata-dao 依赖 nop-orm→nop-dao，故平台 Connection 可达）。
- 现有 executor 的 STDDEV_SAMP/median/percentile/distribution 全方言精确逻辑零修改可用——同一 executor 核心，统计口径与 external 完全一致。
- 平台 querySpace 取 `NopMetaEntity.querySpace`（null 时回退 `DaoConstants.DEFAULT_QUERY_SPACE`）。

**D2 — sql 路径执行机制**：

sql 表经 `MetaDataSourceResolver` 解析 querySpace→`NopMetaDataSource`，`withConnection` 对 `(<sourceSql>) _t` 子查询执行（与 §4.4 queryTableData/聚合 sql 路径一致）。失败路径：querySpace null/无数据源/DISABLED/非 jdbc/sourceSql 不可解析 显式失败抛 inline ErrorCode（不静默空集、不伪造路由）。

**D3 — 共享 table-reference 分派契约**：

抽取共享 table-reference 解析器 `MetaTableReferenceResolver`（`.../service/tableref/`），输入 `NopMetaTable` → 产出三态 `TableReference` 之一，供三大 executor 统一消费：

| tableType | Connection 来源 | table 名/子查询 | 字段集合来源 |
|-----------|----------------|-----------------|-------------|
| `external` | `withConnection`（querySpace→NopMetaDataSource） | 物理 `tableName` | `DatabaseMetaData.getColumns`（运行时读） |
| `entity` | 平台 `IJdbcTransaction.getConnection()`（平台 querySpace） | 物理 `entity.tableName` | `DatabaseMetaData.getColumns`（运行时读，平台连接上） |
| `sql` | `withConnection`（querySpace→NopMetaDataSource） | `(<sourceSql>) _t` 子查询 | `MetaTableFieldResolver` AST 解析（`getColumns` 对子查询不适用） |

executor 内部不再硬编码 external-only，而是按 reference 形态执行：external/entity 走标识符白名单 + `qualifyTable`；sql 走子查询包装（sourceSql 为用户显式提供，同 custom_sql 已知显式风险，不解析不改写、不拼标识符）。reference 不可解析（表不存在/实体未注册/DISABLED/非 jdbc/sourceSql 不可解析）显式失败抛 inline ErrorCode。

**D4 — 能力边界（entity/sql/external 能力集一致）**：

因 entity 路径复用平台 Connection + 同一 executor 核心，**entity/sql/external 三者的统计/检查能力集合完全一致**——无 entity 专属降级。

- `custom_sql` 在 entity 路径 **supported**（raw SQL 跑在物理表上，同 external）。
- 不可执行路径（表不存在/实体未注册/DISABLED/非 jdbc/sourceSql 不可解析）**显式失败抛 inline ErrorCode**，不静默 SKIP、不伪造 unavailable、不静默空集。
- `entityType=database` 质量规则仍 SKIP（带 details 标记，与 §2.7.1 D1 一致）——但 table-level 的 entity/sql 表上挂载的 field/table 规则可执行。

**D5 — sql 视图合成列名处理**：

sql 视图无别名表达式列产 `<expr_N>` 合成名（§4.2.1），含 `<>` 通不过标识符白名单 `^[A-Za-z_][A-Za-z0-9_]*$`。field 级检查/剖析对该列**显式 SKIP + details 标记 `reason=derived-column-skipped`**（不整表失败、不伪造、不静默跳过）；table 级检查不受影响。

> 三大执行器的覆盖范围裁定（D1-D5）收口到此。entity/sql 路径的端到端验证见 plan 1905-1（H2 本地，真实产出 Catalog rowCount/indexCount、Quality 检测结果、Profiling STDDEV/median/distribution 值）。

#### 4.4.1 跨表 JOIN 执行（P4-2 裁定，落地 D3/D4/D5）

本节落地 plan 0800-2 的设计决策 D3（跨表 JOIN 路由）+ D4（同库 JOIN 机制）+ D5（跨库应用层拼接契约）。`MetaTableJoin`（§2.5）建模为 `leftEntityId`/`rightEntityId`（→`MetaEntity`）+ `leftField`/`rightField`（字段名字符串）+ `alias` + `joinType`。**首版 JOIN 执行聚焦 entity-entity**（join 模型仅引用 entity；sql/external 表无 entity，无法作为 join 端点——其 join 能力为 follow-up）。

**JOIN 入口**：`@BizQuery queryJoinData(metaTableId, joinId, filter?, limit?, offset?, context)`，落点 `NopMetaTableBizModel`。`metaTableId` 为左表（join 所属逻辑表），`joinId` 指定 `NopMetaTableJoin`。`filter`/`limit`/`offset` 同单表语义。返回 `Map{items:[{行数据}]}`。

**D3 — 跨表 JOIN 路由（按左右 querySpace 是否相同分派）**：

querySpace 解析规则：entity 表用 `MetaEntity.querySpace`（左右 entity 各自从其 `NopMetaEntity` 取）；external/sql 表用表自身 `querySpace`（首版 join 端点仅 entity，故 external/sql querySpace 仅在 follow-up 的 external join 中出现）。

| 条件 | 路由 | 机制 |
|------|------|------|
| 左右 querySpace 相同 | **同库 JOIN**（D4） | 单库连接内执行 JOIN |
| 左右 querySpace 不同 | **跨库拼接**（D5） | 应用层各取数后内存合并 |

本裁定关闭 §八 待定问题「MetaTableJoin 跨表关联路由」（左右表所属数据源不同时的路由）：**不同 querySpace → 应用层拼接**，不引入跨库 JOIN 引擎。

**D4 — 同库 JOIN 机制**：

- **entity-entity 同库**：经平台 ORM session 执行原生 JOIN SQL——载体 `orm().executeQuery(SQL, range, callback)`（**entity 表 querySpace 由 ORM 管理、不经 NopMetaDataSource，故不能用 withConnection**）。SQL 文本为**物理表 + 物理列**：左/右物理表取自 `MetaEntity.tableName`，`leftField`/`rightField`（属性名字符串）解析回物理列 `NopMetaEntityField.columnCode`（按 `metaEntityId + fieldName` 查 `NopMetaEntityField`）。SQL 构造时 `SQL.allowUnderscoreName(true)` 使 EQL 编译器接受下划线物理表名（EQL 将 `NOP_META_*` 解析为对应实体）。**不使用 `QueryBean.innerJoin/leftJoin`**——经核验 `QueryBean.join` 是 MDX 风格的内存 dimField 对齐合并（`MdxQuerySplitter` 不读 `conditions`/`joinType`），**不是关系型字段对 JOIN**，无法表达 ad-hoc `leftField=rightField` 关联；故 entity 同库 JOIN 走原生 SQL（与 D6 entity 聚合一致的执行载体）。**前置校验**：左右两个实体均须注册于运行时 `IOrmSessionFactory`（`orm().isValidEntityName(entityName)`），否则显式失败抛 inline ErrorCode（不静默空集）。
- **external/sql 同库 JOIN（机制定义，首版 join 端点仅 entity）**：经 `withConnection`（querySpace→NopMetaDataSource）生成原生 `JOIN` SQL（标识符经 §2.7.1 D3 白名单 `^[A-Za-z_][A-Za-z0-9_]*$` + 值参数绑定）。
- **`joinType=right` 首版全局显式不支持**：抛 inline ErrorCode（见 D5）。

**D5 — 跨库应用层拼接契约**：

左右各走 §4.4 单表查询（`queryTableData`）取 `List<Map>` → 内存按 join key 合并。明确：

- **join key 匹配**：按 `leftField`/`rightField` 列值**字符串相等**匹配（`String.valueOf(leftVal).equals(String.valueOf(rightVal))`）。跨库类型差异由调用方建模保证；首版**不做隐式类型转换**，不匹配即不关联。
- **结果 schema**：左表列 + 右表列。右表列名与左表冲突时加 `<alias>.` 前缀（`alias` 取自 `NopMetaTableJoin.alias`；alias 为空时用 `right`）。
- **分页**：跨库拼接首版**不保证 LIMIT/OFFSET 全局语义**（内存合并无全局序）——明确文档化为已知限制；limit/offset 仅作为合并后结果集的**截断提示**（取前 limit 行，从 offset 起），调用方如需精确分页应在单表侧先行过滤。
- **规模上限**：单侧结果集行数上限 `MetaJoinExecutor.MAX_CROSS_DB_ROWS`（默认 10000，可调）；超限显式失败抛 inline ErrorCode（防 OOM，不静默截断）。
- **`joinType` 语义**：`inner`（仅保留匹配行）/`left`（保留左表全部 + 右表匹配列，未匹配右列填 null）/`right`（**首版显式不支持**——抛 inline ErrorCode，不静默降级为 left、不静默返回左表全集）。跨库 right 语义（保留右表全部）与同库 right 一致不支持。

**默认过滤器自动应用**（收口 0700-2 Non-Blocking Follow-up）：JOIN/聚合/单表查询执行前，由共享 `DefaultFilterApplicator` 自动注入该表 `isDefault=true` 的 `NopMetaTableFilter.definition`（TreeBean）到 filter 树（与用户 filter AND 合并）。单表（0800-1）/JOIN/聚合（0800-2）共用同一 helper。

**失败路径显式化**（Minimum Rules #24）：无 join 定义 / leftField/rightField 不匹配（无法解析物理列）/ 实体未注册 / 规模超限 / `joinType=right` / 未知 joinType 均显式失败抛 inline ErrorCode（不静默返回左表全集、不吞异常）。

#### 4.4.2 指标/维度聚合查询（P4-3 裁定，落地 D6/D7）

本节落地 plan 0800-2 的设计决策 D6（聚合执行路径）+ D7（granularity→SQL 分桶翻译表）。

**聚合入口**：`@BizQuery queryAggregation(metaTableId, measures, dimensions, filter?, limit?, offset?, context)`，落点 `NopMetaTableBizModel`。`measures`/`dimensions` 为选定 `NopMetaTableMeasure`/`NopMetaTableDimension` 的 name 列表（`List<String>`）。返回 `Map{items:[{维度值, 指标聚合值}]}`。

**D6 — 聚合执行路径**：

聚合查询须把 `GROUP BY` 维度 + `aggFunc` 指标下沉到 SQL（`GroupFieldBean` 仅 `owner`+`name` 无法表达 DATE_TRUNC 等变换，故不走 ORM QueryBean 聚合）。

| tableType | 执行载体 | 字段解析 |
|-----------|---------|---------|
| `external`/`sql` | `withConnection` 跑原生聚合 SQL（`SELECT <维度>, <agg>(<指标列>) FROM ... GROUP BY <维度>`） | 列名取 `MetaTableFieldResolver` 解析的 field name |
| `entity` | `orm().executeQuery(SQL, range, callback)` 跑原生聚合 SQL（**物理表 + 物理列**，`allowUnderscoreName(true)`） | `entityFieldId`（主键）解析回**物理列 `NopMetaEntityField.columnCode`**；物理表取自 `MetaEntity.tableName` |

`aggFunc` 翻译：`sum`→`SUM(col)`、`count`→`COUNT(col)`、`avg`→`AVG(col)`、`min`→`MIN(col)`、`max`→`MAX(col)`、`countDistinct`→`COUNT(DISTINCT col)`。标识符经 §2.7.1 D3 白名单 + 值参数绑定。

**ORM 隐式过滤旁路裁定**：原生聚合 SQL 不应用 ORM 隐式过滤（租户/逻辑删除/版本）。entity 路径经 `orm().executeQuery` 时，聚合 SQL 是物理表直查，**绕过 ORM 实体隐式过滤**。首版策略——对启用了 `useTenant`/`useLogicalDelete` 的 entity，聚合 action 在 ErrorCode/文档中显式提示"原生 SQL 聚合不应用隐式过滤"；首版**不限制**（允许执行），由调用方知晓此语义。该裁定写入本节，不静默忽略。

**`expression` 型 Measure 首版显式不支持**：`MetaTableMeasure.expression` 非空时抛 inline ErrorCode（不静默跳过、不当 0 返回、不伪造）。

**D7 — granularity→SQL 分桶翻译表**：

`MetaTableDimension.dimensionType=temporal` 时按 `granularity` 生成分桶表达式。首版以测试库 H2 的可用函数为准。**注意**：entity 路径经 `orm().executeQuery` 时 EQL 编译器校验函数名（如 `FORMATDATETIME` 被判为 unknown-function），故**时间分桶（需 DATE_TRUNC/FORMATDATETIME 等函数）首版仅在 external/sql 路径（withConnection 原生 SQL）完整支持**；entity 路径的时间维度首版仅支持 EQL 已知函数（如 `DATE(col)` 日级），完整 granularity 分桶下沉到 SQL 为 follow-up（受 EQL 函数白名单限制）。

granularity→分桶表达式表（external/sql 路径，withConnection 原生 SQL）：

| granularity | H2 / PostgreSQL | MySQL |
|-------------|-----------------|-------|
| `year` | `DATE_TRUNC('year', col)` | `DATE_FORMAT(col, '%Y-01-01')` |
| `quarter` | `DATE_TRUNC('quarter', col)` | `DATE_FORMAT(col, '%Y-%m-01')`（注：quarter 精度首版按月初分桶，跨方言/区域差异为已知限制） |
| `month` | `DATE_TRUNC('month', col)` | `DATE_FORMAT(col, '%Y-%m-01')` |
| `week` | `DATE_TRUNC('week', col)` | `DATE_FORMAT(col, '%Y-%m-%d')`（注：week 起始日跨方言/区域有差异，首版不保证一致） |
| `day` | `DATE_TRUNC('day', col)` | `DATE_FORMAT(col, '%Y-%m-%d')` |
| `hour` | `DATE_TRUNC('hour', col)` | `DATE_FORMAT(col, '%Y-%m-%d %H:00:00')` |

- 非约定 granularity 值（不在上表）→ 显式失败抛 inline ErrorCode（§2.5.2 D1）。
- 方言不在首版支持集（H2/MySQL/PostgreSQL）→ 显式失败抛 inline ErrorCode（不静默）。

**默认过滤器自动应用**：聚合查询执行前同样由 `DefaultFilterApplicator` 注入 `isDefault=true` 过滤器（与 JOIN/单表共用）。

**失败路径显式化**：表不存在 / 无选定 measure 或 dimension / entityFieldId 无法解析物理列 / aggFunc 不支持 / granularity 不约定 / 方言不支持 / expression 型 measure / 实体未注册 均显式失败抛 inline ErrorCode。

---

## 五、与 nop-dyn 的关系

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

---

## 八、待定问题

- `isDelta=true/false` 用同一张表（列区分）还是两张表？
- ~~SQL 视图字段解析：走 `EXPLAIN` 还是 `SELECT ... LIMIT 0` 还是用户手动录入？~~ **已裁定（P3-6，2026-07-16）**：字段名/别名走 AST 解析（复用 `EqlASTParser`，与血缘先例一致，可移植、无需连接）；字段类型首版仅名不取类型（方案 A，`type=null` 不伪造），LIMIT 0 经 ResultSetMetaData 取类型（方案 B）为 follow-up。详见 §4.2.1。
- ~~MetaTableJoin 跨表关联时，左右表所属数据源不同（例如 ORM 的 MySQL 表和 SQL 定义的 ClickHouse 表），查询执行如何路由？~~ **已裁定（P4-2，2026-07-16）**：按左右 `querySpace` 是否相同分派——同库走单库 JOIN（D4），跨库（不同 querySpace）走应用层拼接（D5，各取数后内存按 join key 合并）。详见 §4.4.1。
- 通用 Domain 的来源：是单独维护还是从现有 ORM 模型提取？
- ~~数据契约的 SLA 定义格式：JSON Schema vs 自定义 DSL？~~ **已裁定（P4-4，2026-07-16）**：`schema` 列存 JSON Schema 文档（mediumtext + stdDomain json，首版仅存储不执行逐行校验），`sla` 列存结构化 JSON（json-4000 + stdDomain json，约定键 refreshFrequency/maxLatency/retention）。拒绝自定义 DSL（详见 `04-data-governance.md` §2.3 D1 裁定 + §5.2 D2 检查语义）。
