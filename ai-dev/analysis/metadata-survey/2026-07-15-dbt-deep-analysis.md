# dbt 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 dbt 的元数据管理、血缘收集和测试框架，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

dbt (data build tool) 是一个现代数据转换工具，专注于 ELT 中的 T（Transform）。它通过 SQL 模型定义数据转换，内置元数据管理、数据血缘收集和数据质量测试。本文档分析其元数据相关设计模式，为 nop-metadata 提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                    dbt CLI / dbt Cloud               │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              dbt Core (Python)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Manifest     │  │ Graph        │  │ Runner    │ │
│  │ (元数据)     │  │ (依赖图)     │  │ (执行)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        adapters (数据库适配器)                    │ │
│  │  PostgreSQL, BigQuery, Snowflake, Redshift...  │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
┌───────────▼──────┐ ┌─────▼─────┐ ┌───────▼──────┐
│   Data Warehouse │ │  metadata │ │  artifacts   │
│  (PostgreSQL/    │ │  (manifest│ │  (run_results│
│   BigQuery/...)  │ │   catalog)│ │   catalog)   │
└──────────────────┘ └───────────┘ └──────────────┘
```

### 2. 存储层

- **Data Warehouse**: 主存储，dbt 不引入额外数据库
- **Manifest JSON**: 本地元数据存储（项目级）
- **Artifacts**: 运行结果存储（run_results.json, catalog.json）

## 核心设计模式

### 模式 1：Manifest 元数据模型

**关键文件:**
- `core/dbt/contracts/graph/nodes.py`
- `core/dbt/contracts/graph/manifest.py`

**Manifest 结构:**

```python
@dataclass
class Manifest:
    """项目完整元数据快照"""
    nodes: Dict[str, ModelNode]          # 模型节点
    sources: Dict[str, SourceNode]       # 源表节点
    metrics: Dict[str, MetricNode]       # 指标节点
    semantic_models: Dict[str, SemanticModel]  # 语义模型
    exposures: Dict[str, ExposureNode]   # 暴露节点
    macros: Dict[str, MacroCandidate]    # 宏
    disabled: Dict[str, List]            # 禁用的节点
    child_map: Dict[str, List[str]]      # 子节点映射
    parent_map: Dict[str, List[str]]     # 父节点映射
```

**节点类型:**

```python
@dataclass
class ModelNode(CompiledNode):
    """模型节点 - 核心元数据"""
    # 基础属性
    name: str
    database: str
    schema_: str
    identifier: str
    alias: str
    
    # 模型配置
    config: NodeConfig
    tags: List[str]
    meta: Dict[str, Any]           # 自定义元数据
    
    # 血缘
    sources: List[str]             # 依赖的源表
    refs: List[str]                # 依赖的模型
    
    # 文档
    description: str
    data_tests: List[str]          # 数据测试
    
    # 执行
    raw_code: str                  # 原始 SQL
    compiled_code: str             # 编译后 SQL
```

**设计优势:**
- **声明式元数据**: 所有元数据在 YAML/SQL 中声明
- **依赖图自动构建**: 从 refs/sources 自动推导依赖关系
- **自定义元数据**: `meta` 字段支持任意 JSON 扩展

---

### 模式 2：Source 和 Exposure 定义

**关键文件:**
- `core/dbt/contracts/graph/nodes.py`

**Source 定义:**

```yaml
# sources.yml
sources:
  - name: raw
    description: "原始数据源"
    database: my_database
    schema: raw
    loader: "airbyte"
    loaded_at_field: _airbyte_extracted_at
    freshness:
      warn_after: {count: 12, period: hour}
      error_after: {count: 24, period: hour}
    tables:
      - name: orders
        description: "订单表"
        columns:
          - name: id
            description: "订单ID"
            data_tests:
              - unique
              - not_null
```

**Exposure 定义:**

```yaml
# exposures.yml
exposures:
  - name: executive_dashboard
    type: dashboard
    description: "高管仪表盘"
    url: https://bi.company.com/dashboard/executive
    depends_on:
      - ref('fct_orders')
      - ref('dim_customers')
    owner:
      name: "数据团队"
      email: data@company.com
```

**设计优势:**
- **Source 元数据**: 描述数据来源，支持新鲜度检查
- **Exposure 元数据**: 描述数据消费方（报表、API、仪表盘）
- **依赖追踪**: 自动建立数据血缘（源 → 模型 → 消费方）

---

### 模式 3：Manifest 编译和序列化

**关键文件:**
- `core/dbt/parser/manifest.py`
- `core/dbt/contracts/graph/manifest.py`

**Manifest 构建流程:**

```python
class ManifestLoader:
    """加载和构建 Manifest"""
    
    def load(self) -> Manifest:
        # 1. 解析项目文件
        manifest = self.parse_project()
        
        # 2. 解析宏
        manifest = self.parse_macros(manifest)
        
        # 3. 解析模型
        manifest = self.parse_models(manifest)
        
        # 4. 构建依赖图
        manifest.build_parent_child_maps()
        
        # 5. 验证元数据
        manifest.validate()
        
        return manifest
    
    def serialize(self, manifest: Manifest):
        """序列化到 manifest.json"""
        manifest.write_artifact(
            self.project.project_root,
            "manifest.json",
            manifest
        )
```

**Manifest JSON 结构:**

```json
{
  "metadata": {
    "dbt_version": "1.7.0",
    "project_name": "my_project",
    "project_id": "abc123",
    "generated_at": "2026-07-15T10:00:00Z"
  },
  "nodes": {
    "model.my_project.fct_orders": {
      "unique_id": "model.my_project.fct_orders",
      "resource_type": "model",
      "name": "fct_orders",
      "package_name": "my_project",
      "database": "analytics",
      "schema": "public",
      "identifier": "fct_orders",
      "tags": ["finance", "daily"],
      "meta": {"owner": "data-team"},
      "description": "订单事实表",
      "columns": {...},
      "raw_code": "...",
      "compiled_code": "..."
    }
  },
  "sources": {...},
  "child_map": {
    "model.my_project.fct_orders": ["exposure.my_project.executive_dashboard"]
  },
  "parent_map": {
    "model.my_project.fct_orders": ["source.my_project.raw.orders"]
  }
}
```

**设计优势:**
- **完整快照**: manifest.json 包含项目所有元数据
- **依赖图预计算**: parent_map/child_map 在构建时计算
- **版本化元数据**: 每次运行生成新的 manifest

---

### 模式 4：Catalog 元数据收集

**关键文件:**
- `core/dbt/adapters/base/impl.py`
- `core/dbt/contracts/relation.py`

**Catalog 收集:**

```python
class BaseAdapter:
    """数据库适配器基类"""
    
    def get_catalog(self, relation: BaseRelation) -> AgateTable:
        """从数据库获取元数据"""
        # 查询 information_schema 或等效系统表
        return self.execute_macro(
            'get_catalog',
            schema=relation.schema,
            manifest=manifest
        )
```

**Catalog JSON 结构:**

```json
{
  "metadata": {...},
  "nodes": {
    "model.my_project.fct_orders": {
      "unique_id": "model.my_project.fct_orders",
      "metadata": {
        "type": "VIEW",
        "schema": "public",
        "name": "fct_orders"
      },
      "columns": [
        {
          "name": "order_id",
          "idx": 0,
          "type": "integer",
          "comment": "订单ID",
          "nullable": false
        }
      ],
      "stats": {
        "row_count": 1000000,
        "bytes": 52428800
      }
    }
  }
}
```

**设计优势:**
- **运行时元数据**: 从数据库实时收集表结构
- **Schema 演进追踪**: 对比 manifest 和 catalog 发现变更
- **统计信息**: 收集行数、大小等统计信息

---

### 模式 5：数据测试框架

**关键文件:**
- `core/dbt/contracts/graph/nodes.py`
- `tests/functional/testing/`

**内置数据测试:**

```yaml
# 模型定义中的数据测试
columns:
  - name: order_id
    data_tests:
      - unique
      - not_null
  
  - name: amount
    data_tests:
      - not_null
      - dbt_utils.accepted_range:
          min_value: 0
          max_value: 1000000

# 自定义数据测试
data_tests:
  - unique_combination_of_columns:
      combination_of_columns:
        - order_id
        - product_id
```

**测试结果存储:**

```json
// run_results.json
{
  "metadata": {...},
  "results": [
    {
      "unique_id": "test.my_project.not_null_orders_order_id.abcd1234",
      "status": "pass",
      "timing": [...],
      "execution_time": 0.123,
      "message": null
    }
  ],
  "summary": {
    "total": 50,
    "passed": 48,
    "failed": 2
  }
}
```

**设计优势:**
- **声明式测试**: 在 YAML 中定义测试
- **测试即文档**: 测试描述了数据质量期望
- **测试结果可追踪**: 每次运行记录测试结果

---

### 模式 6：指标和语义模型

**关键文件:**
- `core/dbt/contracts/graph/nodes.py`

**指标定义:**

```yaml
# metrics.yml
metrics:
  - name: order_count
    type: simple
    description: "订单数量"
    label: "订单数"
    model: ref('fct_orders')
    expression: count(*)
    filters:
      - field: status
        operator: '!='
        value: 'cancelled'
    config:
      grain: day

  - name: revenue
    type: derived
    description: "总收入"
    label: "收入"
    expression: "{{ metric('order_count') }} * {{ metric('avg_order_value') }}"
    metrics:
      - metric_name: order_count
      - metric_name: avg_order_value
```

**语义模型定义:**

```yaml
# semantic_models.yml
semantic_models:
  - name: orders
    defaults:
      agg_time_dimension: order_date
    description: "订单语义模型"
    model: ref('fct_orders')
    entities:
      - name: order_id
        type: primary
      - name: customer_id
        type: foreign
    dimensions:
      - name: order_date
        type: time
        type_params:
          time_granularity: day
      - name: status
        type: categorical
    measures:
      - name: order_count
        agg: count
        expr: order_id
      - name: revenue
        agg: sum
        expr: amount
```

**设计优势:**
- **指标即代码**: 指标定义存储在版本控制中
- **语义层**: 技术元数据到业务语义的映射
- **指标复用**: 指标可以在多个报表中复用

---

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **Manifest 元数据模型**: 项目完整元数据快照 + 依赖图预计算
2. **Source/Exposure 元数据**: 数据来源和消费方的标准化描述
3. **Catalog 运行时元数据**: 从数据库实时收集表结构和统计信息
4. **声明式数据测试**: 在 YAML 中定义数据质量规则
5. **指标和语义模型**: 技术元数据到业务语义的映射

### 与 nop-metadata 的对比

| 能力 | dbt | nop-metadata |
|------|-----|-------------|
| 元数据存储 | manifest.json (文件) | MetaOrmModel (数据库) |
| 依赖图 | parent_map/child_map | MetaLineageEdge |
| 数据测试 | 内置测试 + 自定义宏 | MetaQualityRule |
| 指标 | 指标定义文件 | MetaTableMeasure |
| 语义模型 | semantic_models.yml | MetaTable |

### nop-metadata 可复用的模式

1. **Manifest 快照模式**: 导入 ORM 模型时生成完整元数据快照
2. **依赖图预计算**: 导入时计算 parent_map/child_map
3. **Catalog 收集**: 从数据库收集运行时元数据
4. **测试即元数据**: 数据质量规则作为元数据一等公民

## Open Questions

- [ ] nop-metadata 是否需要支持 dbt manifest.json 的导入？
- [ ] 指标定义是否需要支持 dbt 的简单/派生/复合类型？
- [ ] 语义模型是否需要作为独立实体？

## References

- [dbt GitHub](https://github.com/dbt-labs/dbt-core)
- [dbt 文档](https://docs.getdbt.com/)
- 源码: `core/dbt/contracts/graph/nodes.py`
- 源码: `core/dbt/contracts/graph/manifest.py`
