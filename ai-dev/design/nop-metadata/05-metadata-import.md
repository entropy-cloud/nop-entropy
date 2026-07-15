# nop-metadata 元数据采集和导入设计

> Status: final
> Date: 2026-07-15（2026-07-16 P2-3 Manifest 落地后重写为最终设计状态）
> Scope: nop-metadata 元数据采集、导入、Manifest 快照生成
> Goal: 定义元数据导入流程和 Manifest 元数据快照机制
> Based on: dbt Manifest 模式

---

## 一、设计决策

### 1.1 导入粒度

**决策**: 元数据导入的基本粒度是**模块版本**，与 MetaModule 对齐。

**理由**:
- Maven 按模块打包/发布
- 版本管理在模块级别
- 导入时同时存储 delta 和 full 定义（见 `01-architecture-baseline.md` §4.1）

### 1.2 Manifest 快照

**决策**: 参考 dbt 的 Manifest 模式，为每个已导入模块版本生成完整元数据快照（NopMetaManifest，每模块版本一条）。

**Manifest 内容**（单行 JSON CLOB，存于 `NopMetaManifest.content`）:
- 模块元数据（metadata）
- 节点集合（nodes）—— 首版仅 entity 节点
- 数据源集合（sources）—— 来自 MetaDataSource / querySpace
- 依赖图（parentMap / childMap）—— 首版仅来自 MetaEntityRelation

### 1.3 Catalog 运行时元数据

Catalog（从数据库收集运行时统计：行数/大小/索引/分区）属于 P2-4，**不在 Manifest 范围内**。Manifest 是**逻辑元数据快照**（基于已导入的 ORM 元数据），Catalog 是**物理运行时元数据快照**。两者独立，由各自 Phase 实现。

---

## 二、元数据导入流程

### 2.1 导入触发时机

| 触发方式 | 说明 | 适用场景 |
|---------|------|---------|
| **构建时导入** | `mvn install` 后自动导入 | 开发环境 |
| **手动导入** | UI 上点击"导入模块" | 生产环境 |
| **定时同步** | 定时扫描已注册模块的变更 | 持续同步 |
| **Git Hook** | Git push 后自动触发 | CI/CD |

首版已实现**手动导入**（`NopMetaModule__importOrmModel` GraphQL mutation）。导入时自动触发 Manifest 生成、定时自动生成为 Non-Blocking Follow-up（见 `01-architecture-baseline.md`）。

### 2.2 导入流程（已实现）

```
orm.xml
  ├── 解析（filtered，不展开 x:extends）→ OrmModel(delta) → NopMetaOrmModel(isDelta=true)
  ├── 解析（展开 x:extends）            → OrmModel(full)  → NopMetaOrmModel(isDelta=false)
  └── 两者共用同一 metaModuleId，各自派生子实体（Entity/Field/Relation/UK/Index/Domain/Dict）
      同时为每个 NopMetaEntity 自动创建 NopMetaTable(tableType=entity)
```

导入解析与双重存储（delta + full）的完整规格见 `01-architecture-baseline.md` §4.1。

### 2.3 Manifest 生成时机

首版提供**手动 action**（`NopMetaModule__generateManifest(metaModuleId)`）。导入时自动触发的接线为 Non-Blocking Follow-up。

---

## 三、Manifest 元数据模型

### 3.1 存储形态

**决策（D2）**：单行 `NopMetaManifest`，内容为 JSON CLOB（`content` 列）。

| 方案 | 裁定 | 理由 |
|------|------|------|
| **A. 单行 JSON CLOB** | **选定** | 与 dbt manifest.json 对齐，自包含、易导出/喂给 AI，查询频率低 |
| B. 规范化多实体（ManifestNode/ManifestEdge） | 拒绝 | 增加表数量与 JOIN 成本，Manifest 整体消费为主 |

`content` 列定义：`domain="mediumtext"` + `stdDomain="json"`（VARCHAR 16777216，可装下整模块快照）。**不得用 `json-4000`**——4000 字符对整模块 Manifest 远不够。

### 3.2 快照粒度

**决策（D1）**：每模块版本一条 Manifest（`metaModuleId` 关联 NopMetaModule）。

| 方案 | 裁定 | 理由 |
|------|------|------|
| **A. 每模块版本一条** | **选定** | 与模块版本管理粒度对齐；base 版本删除后该模块 full 定义仍完整存储（§3.3 不变量），Manifest 仍可查 |
| B. 全局一条 | 拒绝 | 违反架构 §3.3 版本不变量（版本删除影响），且不利于按模块增量生成 |

### 3.3 NopMetaManifest 实体

```
NopMetaManifest                  — 模块元数据快照（每模块版本一条）
  ├── manifestId                 — PK（seq）
  ├── metaModuleId               → NopMetaModule（快照所属模块版本）
  ├── manifestVersion            — long，Manifest 自身版本号（同一模块版本可重新生成，递增）
  ├── generatedAt                — 生成时间
  ├── nopMetadataVersion         — 生成时 nop-metadata 平台版本
  ├── content                    — JSON CLOB（mediumtext + stdDomain json），快照正文
  └── 审计列（version / createdBy / createTime / updatedBy / updateTime / remark）
```

### 3.4 Manifest JSON 结构

```json
{
  "metadata": {
    "moduleId": "nop/auth",
    "moduleVersion": 1,
    "manifestVersion": 1,
    "generatedAt": "2026-07-15T10:00:00Z",
    "nopMetadataVersion": "2.0.0-SNAPSHOT"
  },
  "nodes": {
    "entity.nop.auth.NopMetaUser": {
      "uniqueId": "entity.nop.auth.NopMetaUser",
      "resourceType": "entity",
      "name": "NopMetaUser",
      "entityName": "io.nop.auth.dao.entity.NopMetaUser",
      "tableName": "nop_auth_user",
      "displayName": "用户",
      "tagSet": "pub",
      "querySpace": "default"
    }
  },
  "sources": {
    "default": {
      "name": "default",
      "querySpace": "default"
    }
  },
  "parentMap": {
    "entity.nop.auth.NopMetaUserRole": ["entity.nop.auth.NopMetaUser"]
  },
  "childMap": {
    "entity.nop.auth.NopMetaUser": ["entity.nop.auth.NopMetaUserRole"]
  }
}
```

说明：
- `metadata.moduleId` 取 NopMetaModule.moduleId（业务标识，含 `/`，如 `nop/auth`，**不做归一化**——这是模块的业务标识）。
- `nodes` 的 **key** 与节点 `uniqueId` 使用归一化 moduleId（见 §五 D4），如 `entity.nop.auth.NopMetaUser`。
- 首版 `nodes` 仅含 **entity** 节点（`resourceType: "entity"`）。table/measure/exposure 节点为后续 Phase。
- `sources` 首版来自模块内实体引用到的 querySpace（及全局 NopMetaDataSource）；当前为 querySpace 维度。
- `parentMap` / `childMap` 首版仅来自 MetaEntityRelation（见 §五）。

### 3.5 Manifest 用途

| 用途 | 说明 |
|------|------|
| **依赖分析** | 通过 parentMap/childMap 分析依赖关系 |
| **影响分析** | 列变更影响哪些下游表 |
| **搜索增强** | 为搜索引擎提供完整元数据 |
| **AI 集成** | 为 AI Agent 提供上下文（自包含 JSON，可直接喂给 LLM） |
| **文档生成** | 自动生成数据字典 |

---

## 四、Catalog 运行时元数据（P2-4，独立 Phase）

Catalog 从数据库收集运行时统计（行数/大小/索引/分区），参考 dbt Catalog。**Catalog 不是 Manifest 的来源**——Manifest 是逻辑元数据快照，Catalog 是物理运行时快照。两者独立实现（见 §1.3）。

### 4.1 存储形态（D1，方案 A）

**决策**：per-table 结构化快照行 `NopMetaCatalog`，真实列承载核心统计 + `details` JSON 承载扩展/方言特定字段。Catalog 核心用途是"按表查行数/大小/索引状态"、"排序找最大表"、"趋势监控"，结构化列才可查可排序；单 CLOB（如 Manifest）不利于这类查询。

`details` 列用 `domain="mediumtext"` + `stdDomain="json"`（**不得用 `json-4000`**——列级统计/方言特定字段可能超长，对齐 Manifest D2 决策）。`details` 首版实际承载：`unavailable` 字段名数组 + 方言特定字段（如 DB 产品名）；列级统计**不在首版**（与 P2-7 数据剖析重叠）。

### 4.2 NopMetaCatalog 实体

```
NopMetaCatalog                    — 物理运行时统计快照（每表每收集一次一条，时序追加）
  ├── metaCatalogId               — PK（seq）
  ├── metaTableId                 → NopMetaTable（mandatory，被统计的逻辑表）
  ├── rowCount                    — long，表行数（SELECT COUNT(*) 结果）
  ├── sizeBytes                   — long, nullable，表物理大小（方言特定，不可用时 null）
  ├── indexCount                  — int, nullable，索引数量（JDBC getIndexInfo 统计）
  ├── partitionCount              — int, nullable，分区数（方言特定，不可用时 null）
  ├── lastModified                — timestamp, nullable，表最后修改时间（方言特定，不可用时 null）
  ├── details                     — JSON CLOB（mediumtext + stdDomain json）
  │                                  承载：unavailable 字段名数组、方言特定字段（DB 产品名等）
  ├── collectedAt                 — timestamp, mandatory，本次收集时间（时序键）
  └── 审计列（version / createdBy / createTime / updatedBy / updateTime / remark）
```

**时序语义**：重复收集同一表追加为新的快照行（`collectedAt` 区分），不覆盖旧行——支持"最近一次"与"趋势"查询。索引 `IX_NOP_META_CATALOG_TABLE` on `metaTableId` 支持按表查询；时序查询用 `(metaTableId, collectedAt)` 组合。

### 4.3 收集范围（D1）

**决策**：首版仅 `tableType=external` 类型表。外部表有明确注册数据源（P2-1/P2-2），`collectCatalog(dataSourceId)` 连接该数据源 → 找到该 querySpace 下的 external NopMetaTable → 按其 `tableName` 收集统计。

entity/sql 类型表收集（需 `querySpace → 数据源` 解析，entity 的 querySpace 由引用实体决定）为显式 Non-Blocking Follow-up，不阻塞 external Catalog 结果面成立。

### 4.4 统计获取与降级策略（D1）

| 统计项 | 获取方式 | 便携性 | 不可用时处理 |
|--------|---------|--------|-------------|
| **rowCount** | `SELECT COUNT(*) FROM <限定表名>` | 全方言便携（含 H2） | 必有，无降级 |
| **indexCount** | `DatabaseMetaData.getIndexInfo(catalog, schemaPattern, table, ...)` | 标准 JDBC，全方言便携 | 必有，无降级 |
| **sizeBytes** | 方言特定（MySQL `information_schema.TABLES.DATA_LENGTH`、PG `pg_class.relpages`） | 首版不实现 | null + `details.unavailable` 标记 |
| **partitionCount** | 方言特定（MySQL `information_schema.PARTITIONS`、PG `pg_partitioned_table`） | 首版不实现 | null + `details.unavailable` 标记 |
| **lastModified** | 方言特定（MySQL/PG 系统视图） | 首版不实现（H2 `LAST_MODIFICATION` 是 BIGINT 计数器非时间戳） | null + `details.unavailable` 标记 |

**降级规则（禁止静默跳过/伪造）**：
- 不可用统计记 `null`，并在 `details.unavailable` 显式列出该字段名（如 `["sizeBytes","partitionCount","lastModified"]`），**不静默跳过整行，也不伪造 0**。
- 单表失败（SQL 异常）收集到返回结果的 `errors` 不中断整批（`orm().clearSession()` 隔离失败态，对齐 P2-2 模式）。

### 4.5 schema 限定策略（D1）

**已知建模缺口**：NopMetaTable **无 schema/dbSchema 列**（实体列只有 `tableName`）。P2-2 的 `ExternalTableStructureReader` 扫描时拿到 `TABLE_SCHEM` 但未持久化。⇒ Catalog 收集 `SELECT COUNT(*) FROM <表>` 时无法从目录恢复每张外部表的物理 schema。

**首版策略**：`collectCatalog` 的 `schemaPattern` 参数用于限定 COUNT/索引查询的物理 schema——
- 传入时用 `<schemaPattern>.<tableName>`；
- 不传时依赖连接默认 schema（H2=PUBLIC、单 schema 数据源正常）。

**已知限制（Non-Blocking Follow-up）**：多 schema 数据源下不同 schema 同名表无法区分。彻底解决需为 NopMetaTable 增加 schema 列，属 P2-2 实体 Protected Area 变更，不在本设计范围。

### 4.6 collectCatalog action 契约（D2）

- **落点**：action 放在 `NopMetaDataSourceBizModel`（与现有 `testConnection`/`syncExternalTables` 一致——三者均以 `dataSourceId` 为入口键）。GraphQL mutation 名为 `NopMetaDataSource__collectCatalog`。
- **签名**：`@BizMutation collectCatalog(@Name("dataSourceId") String id, @Name("schemaPattern") String schemaPattern, IServiceContext context)` → 返回 `Map{collectedCount: int, errors: [...]}`。
- **`schemaPattern` 语义**：限定 COUNT/索引查询的物理 schema（见 §4.5），**不过滤 NopMetaTable 行**（schema 不存于该表）；null 时依赖连接默认 schema。
- **行为**：加载 NopMetaDataSource → 不存在抛 `metadata.datasource-not-found`（不 NPE）→ `status==DISABLED` 抛 `metadata.datasource-disabled`（不静默通过）→ **复用 P2-1 `withConnection` callback 建连**（callback 内遍历该 querySpace 的 external NopMetaTable + 收集统计）→ 写入 NopMetaCatalog（每次追加新行，`collectedAt=now`）→ 单表失败收集 errors 不中断 → callback 结束自动释放连接。
- **非 jdbc 类型**：连接服务显式抛 `UnsupportedOperationException`（继承 P2-1/P2-2 行为，不静默成功）。

---

## 五、依赖图计算

### 5.1 依赖关系类型（首版范围裁定）

| 依赖类型 | 说明 | 来源 | 首版范围 |
|---------|------|------|---------|
| **表/实体级依赖** | 实体 A 引用实体 B | MetaEntityRelation | **纳入**（D3） |
| 列级依赖 | 列 A 依赖列 B | SQL 解析 | 不纳入（随 P3 SQL 视图解析） |
| 血缘依赖 | 数据从 A 流向 B | MetaLineageEdge | 不纳入（随 P2-5 血缘采集） |
| 指标依赖 | 指标 A 使用指标 B | MetaTableMeasure | 不纳入（随 P3 指标管理） |

### 5.2 边连接的节点类型

首版 relation 推导的边为 **`entity` 节点 → `entity` 节点**（实体级依赖图）。

> 设计 §3.4 示例中 table→source 的边（表依赖数据源）属于另一类，**首版不纳入**，避免节点层混淆。

### 5.3 节点 uniqueId 与边 resolution 规格（D4）

**uniqueId 中模块标识取值**：取 **`NopMetaModule.moduleId`**（业务标识，如 `nop/auth`，在唯一键 `UK_NOP_META_MODULE_ID_VER` 内），**不取** `moduleName`（显示名）也不取 seq PK `metaModuleId`。

**slash→dot 归一化**：`moduleId` 含 `/`（如 `nop/auth`），代入点分隔 uniqueId 前须将 `/` 归一化为 `.`（→ `nop.auth`），使 uniqueId 按 `.` split 不碎。

**节点 `<name>` 取值**：取实体 className 的**简单类名**（最后一段，如 `NopMetaUser`）。live 数据中 `NopMetaEntity.entityName` 列存储的是 full className（导入时由 `OrmModelImporter.buildEntity` 写入 `em.getName()`），直接拼入 uniqueId 会使 className 重复出现；因此 uniqueId 的 name 段取 `StringHelper.simpleClassName(className)`。

**节点 uniqueId**：`entity.<moduleId 归一化>.<简单类名>`（首版仅 entity 节点）。
- 例：moduleId=`nop/auth`、className=`io.nop.auth.dao.entity.NopMetaUser` → `entity.nop.auth.NopMetaUser`。

**relation → 边 resolution**：`NopMetaEntityRelation.refEntityName` 存的是被引用实体的 **className**（如 `io.nop.metadata.dao.entity.NopMetaModule`）。resolution 算法：
1. 用 `refEntityName`（className）全局反查 `NopMetaEntity`（`WHERE className = refEntityName`）；
2. 取其所属模块（经 `metaEntity.ormModelId → NopMetaOrmModel.metaModuleId → NopMetaModule.moduleId`）；
3. 归一化 moduleId + 简单类名 → 目标节点 uniqueId。

> 注：`NopMetaEntity` 当前无 className 索引，首版全表反查可接受；性能不足后续加 IX。

**跨模块/未导入引用（dangling）降级策略（不得静默丢弃）**：被引用实体无法解析到节点时，该边目标端记为 `unresolved:<className>` 显式保留在 parentMap/childMap 中，并在生成日志中记录 unresolved 计数；**不静默跳过、不丢边**。

### 5.4 边方向语义

对每条 MetaEntityRelation（实体 E 的关系，`refEntityName` 指向实体 F）：
- E 依赖 F（E 引用 F）。
- `parentMap[E_uniqueId]` 追加 `F_uniqueId`（E 的父/上游是 F）。
- `childMap[F_uniqueId]` 追加 `E_uniqueId`（F 的子/下游是 E）。
- 无关系的节点：parentMap/childMap 中显式置为空数组（不静默跳过）。

### 5.5 依赖图查询

依赖图存于 Manifest 的 parentMap/childMap JSON 中，消费方（依赖分析、影响分析）直接读 JSON：
- `getParents(nodeId)` ← `parentMap[nodeId]`
- `getChildren(nodeId)` ← `childMap[nodeId]`
- `getAncestors(nodeId)` / `getDescendants(nodeId)` ← BFS 遍历 parentMap / childMap

首版不实现图遍历 API（消费方按需自行遍历 JSON）；增量更新、循环依赖检测为 Non-Blocking Follow-up。

---

## 六、与 dbt 的对比

| 能力 | dbt | nop-metadata |
|------|-----|-------------|
| 元数据快照 | manifest.json | NopMetaManifest（每模块版本一条 JSON CLOB） |
| 依赖图 | parent_map/child_map | content.parentMap/childMap（首版来自 MetaEntityRelation） |
| Catalog 收集 | catalog.json | P2-4 MetaCatalog（独立 Phase） |
| 运行时元数据 | 从数据库收集 | P2-4 |
| 变更检测 | schema 演进 | 版本对比（full 定义已完整存储） |

## Open Questions（首版已裁定）

- ~~Manifest 快照是否需要版本化？~~ → **已裁定**：`manifestVersion` 列记录同模块版本下的重新生成递增；模块版本层面的版本化对齐 MetaModule。
- ~~快照粒度（全局 vs 每模块）？~~ → **已裁定 D1**：每模块版本一条。
- ~~存储形态（JSON CLOB vs 多表）？~~ → **已裁定 D2**：JSON CLOB 单行。
- ~~节点 id 与边 resolution 规格？~~ → **已裁定 D4**：见 §5.3。
- Catalog 收集增量更新、依赖图循环依赖检测 → 仍为 Open，归属各自 Phase（P2-4 / 后续优化），不阻塞 Manifest 首版。
