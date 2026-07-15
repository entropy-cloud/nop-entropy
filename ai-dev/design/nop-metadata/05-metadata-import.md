# nop-metadata 元数据采集和导入设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 元数据采集、导入和 Catalog 收集
> Goal: 定义元数据导入流程和 Catalog 运行时元数据收集机制
> Based on: dbt Manifest 模式、Apache Griffin 数据剖析

---

## 一、设计决策

### 1.1 导入粒度

**决策**: 元数据导入的基本粒度是**模块版本**，与 MetaModule 对齐。

**理由**:
- Maven 按模块打包/发布
- 版本管理在模块级别
- 导入时同时存储 delta 和 full 定义

### 1.2 Manifest 快照

**决策**: 参考 dbt 的 Manifest 模式，导入时生成完整元数据快照。

**Manifest 内容**:
- 模块元数据（MetaModule）
- ORM 模型元数据（MetaOrmModel）
- 实体元数据（MetaEntity/Field/Relation）
- 域和字典（MetaDomain/MetaDict）
- 依赖图（parent_map/child_map）

### 1.3 Catalog 运行时元数据

**决策**: 参考 dbt 的 Catalog 模式，从数据库收集运行时元数据。

**Catalog 内容**:
- 表结构（列名、类型、约束）
- 统计信息（行数、大小）
- 索引信息
- 分区信息

---

## 二、元数据导入流程

### 2.1 导入触发时机

| 触发方式 | 说明 | 适用场景 |
|---------|------|---------|
| **构建时导入** | `mvn install` 后自动导入 | 开发环境 |
| **手动导入** | UI 上点击"导入模块" | 生产环境 |
| **定时同步** | 定时扫描已注册模块的变更 | 持续同步 |
| **Git Hook** | Git push 后自动触发 | CI/CD |

### 2.2 导入流程

```
orm.xml → 解析 → MetaOrmModel(isDelta=true)
               → MetaOrmModel(isDelta=false, 合并后)
               → MetaEntity/Field/Relation/Dict (跟随 MetaOrmModel)
               → MetaTable (自动为每个 MetaEntity 创建)
               → 依赖图计算
               → Manifest 快照生成
```

### 2.3 解析过程

```python
class OrmModelImporter:
    """ORM 模型导入器"""
    
    def import_module(self, module_path):
        # 1. 加载模块元数据
        module_meta = self.load_module_meta(module_path)
        
        # 2. 解析 orm.xml
        orm_models = self.parse_orm_xml(module_path)
        
        # 3. 处理 Delta 继承
        for model in orm_models:
            if model.has_extends():
                base_model = self.find_base_model(model.extends)
                full_model = self.merge_models(base_model, model)
                
                # 存储 delta
                self.store_model(model, is_delta=True)
                # 存储 full
                self.store_model(full_model, is_delta=False)
            else:
                self.store_model(model, is_delta=True)
                self.store_model(model, is_delta=False)
        
        # 4. 拆解为结构化实体
        for model in orm_models:
            self.decompose_to_entities(model)
        
        # 5. 生成 MetaTable
        for entity in entities:
            self.create_meta_table(entity)
        
        # 6. 计算依赖图
        self.compute_dependency_graph()
        
        # 7. 生成 Manifest 快照
        self.generate_manifest(module_meta)
```

---

## 三、Manifest 元数据模型

### 3.1 Manifest 结构

```
Manifest                        — 项目完整元数据快照
  ├── moduleId                  → MetaModule
  ├── version                   — Manifest 版本号
  ├── generatedAt               — 生成时间
  ├── dbtVersion                — nop-metadata 版本
  │
  ├── nodes                     — 所有节点（实体、表、指标等）
  │   └── Node[]
  │       ├── uniqueId          — 唯一标识
  │       ├── resourceType      — "entity" | "table" | "measure" | "exposure"
  │       ├── name
  │       ├── database / schema / identifier
  │       ├── tags / meta
  │       └── description
  │
  ├── sources                   — 数据源
  │   └── Source[]
  │       ├── name
  │       ├── database / schema
  │       ├── freshness
  │       └── tables[]
  │
  ├── exposures                 — 数据消费方
  │   └── Exposure[]
  │       ├── name
  │       ├── type              — "dashboard" | "api" | "notebook"
  │       ├── url
  │       └── dependsOn[]
  │
  ├── parentMap                 — 父节点映射
  │   └── Map<nodeId, List<nodeId>>
  │
  └── childMap                  — 子节点映射
      └── Map<nodeId, List<nodeId>>
```

### 3.2 Manifest JSON 示例

```json
{
  "metadata": {
    "moduleId": "nop/auth",
    "version": 1,
    "generatedAt": "2026-07-15T10:00:00Z",
    "nopMetadataVersion": "1.0.0"
  },
  "nodes": {
    "entity.nop.auth.ErpAcctScheme": {
      "uniqueId": "entity.nop.auth.ErpAcctScheme",
      "resourceType": "entity",
      "name": "ErpAcctScheme",
      "database": "default",
      "schema": "public",
      "identifier": "erp_acct_scheme",
      "tags": ["finance", "accounting"],
      "meta": {"owner": "finance-team"},
      "description": "会计方案",
      "columns": [...]
    },
    "table.nop.auth.ErpAcctScheme": {
      "uniqueId": "table.nop.auth.ErpAcctScheme",
      "resourceType": "table",
      "name": "ErpAcctScheme",
      "tableType": "entity",
      "baseEntityId": "entity.nop.auth.ErpAcctScheme"
    }
  },
  "sources": {
    "source.nop.auth.default": {
      "name": "default",
      "database": "nop_erp",
      "schema": "public"
    }
  },
  "parentMap": {
    "table.nop.auth.ErpAcctScheme": ["source.nop.auth.default"],
    "table.nop.auth.ErpAcctJournal": ["source.nop.auth.default"]
  },
  "childMap": {
    "source.nop.auth.default": ["table.nop.auth.ErpAcctScheme", "table.nop.auth.ErpAcctJournal"]
  }
}
```

### 3.3 Manifest 用途

| 用途 | 说明 |
|------|------|
| **依赖分析** | 通过 parentMap/childMap 分析依赖关系 |
| **影响分析** | 列变更影响哪些下游表 |
| **搜索增强** | 为搜索引擎提供完整元数据 |
| **AI 集成** | 为 AI Agent 提供上下文 |
| **文档生成** | 自动生成数据字典 |

---

## 四、Catalog 运行时元数据

### 4.1 Catalog 收集时机

| 时机 | 说明 |
|------|------|
| **导入时** | 导入 ORM 模型时自动收集 |
| **定时收集** | 定时从数据库收集最新结构 |
| **手动收集** | UI 上点击"刷新元数据" |
| **变更检测** | 检测数据库结构变更 |

### 4.2 Catalog 数据模型

```
MetaCatalog                     — 运行时元数据快照
  ├── tableId                   → MetaTable
  ├── snapshotTime              — 快照时间
  ├── database / schema / tableName
  │
  ├── columns[]                 — 列信息
  │   ├── columnName
  │   ├── dataType              — 数据库类型
  │   ├── isNullable
  │   ├── defaultValue
  │   ├── comment
  │   └── ordinalPosition
  │
  ├── stats                     — 统计信息
  │   ├── rowCount              — 行数
  │   ├── sizeBytes             — 大小（字节）
  │   ├── avgRowSize            — 平均行大小
  │   └── lastModified          — 最后修改时间
  │
  ├── indexes[]                 — 索引信息
  │   ├── indexName
  │   ├── indexType             — "btree" | "hash" | "gin" | ...
  │   ├── isUnique
  │   └── columns[]
  │
  └── partitions[]              — 分区信息（可选）
      ├── partitionName
      ├── partitionType         — "range" | "list" | "hash"
      └── partitionValues
```

### 4.3 Catalog 收集实现

```python
class CatalogCollector:
    """Catalog 收集器"""
    
    def collect(self, meta_table):
        """收集表的运行时元数据"""
        
        # 1. 获取数据库连接
        db_type = meta_table.querySpace.dbType
        connection = self.get_connection(meta_table.querySpace)
        
        # 2. 收集列信息
        columns = self.collect_columns(connection, meta_table)
        
        # 3. 收集统计信息
        stats = self.collect_stats(connection, meta_table)
        
        # 4. 收集索引信息
        indexes = self.collect_indexes(connection, meta_table)
        
        # 5. 收集分区信息
        partitions = self.collect_partitions(connection, meta_table)
        
        # 6. 存储 Catalog
        catalog = MetaCatalog(
            tableId=meta_table.id,
            snapshotTime=datetime.now(),
            columns=columns,
            stats=stats,
            indexes=indexes,
            partitions=partitions
        )
        
        self.store_catalog(catalog)
        return catalog
```

### 4.4 数据库适配

| 数据库 | 列信息查询 | 统计信息查询 |
|--------|-----------|-------------|
| MySQL | `information_schema.COLUMNS` | `information_schema.TABLES` |
| PostgreSQL | `information_schema.COLUMNS` | `pg_class` + `pg_stat_user_tables` |
| ClickHouse | `system.columns` | `system.parts` |
| Hive | `information_schema.COLUMNS` | `PARTITIONS` |

---

## 五、依赖图计算

### 5.1 依赖关系类型

| 依赖类型 | 说明 | 来源 |
|---------|------|------|
| **表级依赖** | 表 A 引用表 B | MetaEntityRelation |
| **列级依赖** | 列 A 依赖列 B | SQL 解析 |
| **血缘依赖** | 数据从 A 流向 B | MetaLineageEdge |
| **指标依赖** | 指标 A 使用指标 B | MetaTableMeasure |

### 5.2 依赖图存储

```python
class DependencyGraph:
    """依赖图"""
    
    def __init__(self):
        self.parent_map = {}  # nodeId → [parentIds]
        self.child_map = {}   # nodeId → [childIds]
    
    def add_edge(self, parent_id, child_id):
        """添加依赖边"""
        if parent_id not in self.child_map:
            self.child_map[parent_id] = []
        self.child_map[parent_id].append(child_id)
        
        if child_id not in self.parent_map:
            self.parent_map[child_id] = []
        self.parent_map[child_id].append(parent_id)
    
    def get_parents(self, node_id):
        """获取父节点"""
        return self.parent_map.get(node_id, [])
    
    def get_children(self, node_id):
        """获取子节点"""
        return self.child_map.get(node_id, [])
    
    def get_ancestors(self, node_id, depth=None):
        """获取所有祖先节点"""
        ancestors = set()
        queue = [(node_id, 0)]
        
        while queue:
            current, current_depth = queue.pop(0)
            if depth and current_depth >= depth:
                continue
            
            for parent in self.get_parents(current):
                if parent not in ancestors:
                    ancestors.add(parent)
                    queue.append((parent, current_depth + 1))
        
        return ancestors
    
    def get_descendants(self, node_id, depth=None):
        """获取所有后代节点"""
        descendants = set()
        queue = [(node_id, 0)]
        
        while queue:
            current, current_depth = queue.pop(0)
            if depth and current_depth >= depth:
                continue
            
            for child in self.get_children(current):
                if child not in descendants:
                    descendants.add(child)
                    queue.append((child, current_depth + 1))
        
        return descendants
```

---

## 六、与 metadata-survey 的对比

| 能力 | dbt | nop-metadata |
|------|-----|-------------|
| 元数据快照 | manifest.json | Manifest |
| 依赖图 | parent_map/child_map | DependencyGraph |
| Catalog 收集 | catalog.json | MetaCatalog |
| 运行时元数据 | 从数据库收集 | MetaCatalog |
| 变更检测 | schema 演进 | Catalog 版本对比 |

## Open Questions

- [ ] Manifest 快照是否需要版本化？
- [ ] Catalog 收集是否需要支持增量更新？
- [ ] 依赖图是否需要支持循环依赖检测？
