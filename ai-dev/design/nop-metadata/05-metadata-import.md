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

Catalog 从数据库收集运行时统计（行数/大小/索引/分区），参考 dbt Catalog。**Catalog 不是 Manifest 的来源**——Manifest 是逻辑元数据快照，Catalog 是物理运行时快照。两者独立实现：
- Catalog 收集时机：导入时 / 定时 / 手动 / 变更检测。
- Catalog 数据库适配：MySQL (`information_schema`)、PostgreSQL (`pg_class`) 等。

Catalog 的具体建模与收集实现属于 P2-4，不在本设计文档展开。

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
