# BI 平台元数据管理模式对比分析

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 Superset、Metabase、DataEase 的元数据管理设计，为 nop-metadata 提供参考
> Based on: Superset、Metabase 源码分析

---

## Context

BI 平台的核心能力之一是元数据管理——如何发现、组织、查询数据源的表、字段、指标。本文档分析 Superset、Metabase、DataEase 的元数据管理模式，为 nop-metadata 的 MetaTable/MetaTableMeasure 设计提供参考。

---

## 一、Apache Superset

### 1.1 核心元数据模型

```
┌─────────────────────────────────────────────────────┐
│                    Superset                           │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Database     │  │ SqlaTable    │  │ TableColumn│ │
│  │ (数据源)     │  │ (逻辑表)     │  │ (字段)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        SqlMetric (指标)                          │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
┌───────────▼──────┐ ┌─────▼─────┐ ┌───────▼──────┐
│   SQLAlchemy     │ │  Cache    │ │  Query       │
│   (ORM)          │ │  (Redis)  │ │  Engine      │
└──────────────────┘ └───────────┘ └──────────────┘
```

### 1.2 关键模型

**SqlaTable (逻辑表)**:

```python
class SqlaTable(Model):
    """数据库表/视图"""
    
    # 基础信息
    table_name = Column(String(256))
    schema_ = Column(String(256))
    database_id = Column(Integer, ForeignKey('dbs.id'))
    
    # 元数据
    description = Column(Text)
    column_formats = Column(JSON)      # 列格式配置
    metrics = Column(JSON)             # 预定义指标
    time_column = Column(String(256))  # 时间列
    
    # 关联
    columns = relationship('TableColumn', back_populates='table')
    sql_metrics = relationship('SqlMetric', back_populates='table')
```

**TableColumn (字段)**:

```python
class TableColumn(AuditMixinNullable, Model):
    """表字段"""
    
    # 基础信息
    column_name = Column(String(256))
    type = Column(String(64))          # 数据类型
    table_id = Column(Integer, ForeignKey('tables.id'))
    
    # 元数据
    description = Column(Text)
    is_dttm = Column(Boolean)          # 是否时间列
    is_filterable = Column(Boolean)    # 是否可过滤
    is_groupby = Column(Boolean)       # 是否可分组
    
    # 格式
    column_format = Column(JSON)       # 显示格式
    python_date_format = Column(String(64))  # 日期格式
```

**SqlMetric (指标)**:

```python
class SqlMetric(AuditMixinNullable, Model):
    """SQL 指标"""
    
    # 基础信息
    metric_name = Column(String(256))
    metric_type = Column(String(64))   # sql | aggregated
    table_id = Column(Integer, ForeignKey('tables.id'))
    
    # 定义
    expression = Column(Text)          # SQL 表达式
    description = Column(Text)
    
    # 格式
    d3format = Column(String(64))      # D3 格式
    warning_text = Column(Text)
```

### 1.3 元数据同步机制

```python
class BaseMetadataSync:
    """元数据同步基类"""
    
    def sync(self, database):
        """同步数据库元数据"""
        
        # 1. 获取数据库连接
        engine = self.get_engine(database)
        
        # 2. 获取表列表
        tables = self.get_tables(engine)
        
        # 3. 同步每个表
        for table_name in tables:
            self.sync_table(database, table_name)
    
    def sync_table(self, database, table_name):
        """同步单个表"""
        
        # 1. 获取表信息
        table_info = self.get_table_info(database, table_name)
        
        # 2. 获取列信息
        columns = self.get_columns(database, table_name)
        
        # 3. 更新或创建 SqlaTable
        table = self.get_or_create_table(database, table_name)
        table.description = table_info.get('description')
        table.time_column = self.detect_time_column(columns)
        
        # 4. 更新或创建 TableColumn
        for col_info in columns:
            column = self.get_or_create_column(table, col_info['name'])
            column.type = col_info['type']
            column.is_dttm = self.is_datetime(col_info['type'])
            column.is_filterable = True
            column.is_groupby = True
        
        # 5. 检测并创建默认指标
        self.detect_metrics(table, columns)
```

### 1.4 指标定义模式

```python
# 内置指标类型
METRIC_TYPES = {
    'count': {
        'expression': 'COUNT(*)',
        'description': 'Total count'
    },
    'count_distinct': {
        'expression': 'COUNT(DISTINCT {column})',
        'description': 'Distinct count'
    },
    'sum': {
        'expression': 'SUM({column})',
        'description': 'Sum'
    },
    'avg': {
        'expression': 'AVG({column})',
        'description': 'Average'
    }
}

# 自定义 SQL 指标
CUSTOM_METRICS = {
    'revenue': {
        'expression': 'SUM(amount * quantity)',
        'description': 'Total revenue',
        'd3format': ',.2f'
    }
}
```

---

## 二、Metabase

### 2.1 核心元数据模型

```
┌─────────────────────────────────────────────────────┐
│                    Metabase                           │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Database     │  │ Table        │  │ Field     │ │
│  │ (数据源)     │  │ (逻辑表)     │  │ (字段)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        Metric (指标)                             │ │
│  └────────────────────────┬───────────────────────┘ │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        Segment (数据分片)                        │ │
│  └───────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 2.2 关键模型

**Database (数据源)**:

```clojure
;; Clojure 数据模型
(defrecord Database
  [id
   name
   engine           ;; 数据库类型 (h2, postgres, mysql...)
   details          ;; 连接配置 (JSON)
   description
   features         ;; 支持的特性
   created_at
   updated_at])
```

**Table (逻辑表)**:

```clojure
(defrecord Table
  [id
   db_id            ;; 所属数据库
   name             ;; 表名
   schema           ;; Schema
   description
   display_name
   entity_type      ;; 实体类型 (entity, event, metric)
   created_at
   updated_at])
```

**Field (字段)**:

```clojure
(defrecord Field
  [id
   table_id         ;; 所属表
   name             ;; 字段名
   display_name     ;; 显示名称
   description
   database_type    ;; 数据库类型
   base_type        ;; 基础类型 (type/Text, type/Number...)
   semantic_type    ;; 语义类型 (type/PK, type/FK, type/Category...)
   visibility_type  ;; 可见性 (normal, hidden, sensitive)
   settings         ;; 配置 (JSON)
   created_at
   updated_at])
```

**Metric (指标)**:

```clojure
(defrecord Metric
  [id
   table_id         ;; 所属表
   name
   description
   definition       ;; 指标定义 (MBQL)
   archived         ;; 是否归档
   created_at
   updated_at])
```

**Segment (数据分片)**:

```clojure
(defrecord Segment
  [id
   table_id
   name
   description
   definition       ;; 筛选条件 (MBQL)
   archived
   created_at
   updated_at])
```

### 2.3 元数据同步机制

```clojure
;; 元数据同步流程
(defn sync-table! [database table-name]
  ;; 1. 获取数据库元数据
  (let [metadata (driver/describe-table database table-name)]
    
    ;; 2. 创建或更新表
    (db/upsert! Table
      {:db_id (:id database)
       :name table-name
       :schema (:schema metadata)
       :description (:description metadata)})
    
    ;; 3. 同步字段
    (doseq [field-info (:fields metadata)]
      (sync-field! table field-info))
    
    ;; 4. 检测语义类型
    (detect-semantic-types! table)))
```

### 2.4 语义类型系统

```clojure
;; Metabase 的语义类型定义
(def semantic-types
  {:type/PK           "主键"
   :type/FK           "外键"
   :type/Name         "名称"
   :type/Title        "标题"
   :type/Description  "描述"
   :type/URL          "URL"
   :type/Image        "图片"
   :type/Category     "分类"
   :type/Number       "数字"
   :type/Currency     "货币"
   :type/Percentage   "百分比"
   :type/Date         "日期"
   :type/DateTime     "日期时间"
   :type/Time         "时间"
   :type/Longitude    "经度"
   :type/Latitude     "纬度"
   :type/ZipCode      "邮编"
   :type/Phone        "电话"
   :type/Email        "邮箱"})
```

### 2.5 AI 集成 (Metabot)

```clojure
;; Metabot AI 查询流程
(defn metabot-query [question]
  ;; 1. 理解问题
  (let [intent (analyze-intent question)]
    
    ;; 2. 查找相关表
    (let [tables (find-relevant-tables intent)]
      
      ;; 3. 构建 MBQL 查询
      (let [query (build-mbql-query intent tables)]
        
        ;; 4. 执行查询
        (execute-query query)))))
```

---

## 三、DataEase

### 3.1 核心元数据模型

```
┌─────────────────────────────────────────────────────┐
│                    DataEase                           │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Datasource   │  │ DatasetTable │  │ DatasetTableField │
│  │ (数据源)     │  │ (数据集表)   │  │ (字段)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        DatasetTableField (指标/维度)             │ │
│  └───────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 3.2 关键模型

**Datasource (数据源)**:

```java
public class Datasource {
    private String id;
    private String name;
    private String type;           // mysql, postgres, excel...
    private String configuration;  // 连接配置 (JSON)
    private String description;
    private Long createTime;
    private Long updateTime;
}
```

**DatasetTable (数据集表)**:

```java
public class DatasetTable {
    private String id;
    private String datasourceId;
    private String name;
    private String tableName;
    private String type;           // db, excel, api
    private String description;
    private List<DatasetTableField> fields;
}
```

**DatasetTableField (字段)**:

```java
public class DatasetTableField {
    private String id;
    private String tableId;
    private String name;
    private String fieldName;
    private String type;           // text, number, date...
    private String groupType;      // d, q, t, u (维度/指标/时间/无)
    private String agg;            // 聚合方式
    private String expression;     // 计算表达式
    private Boolean quickFilter;
    private Boolean checked;
    private Boolean extField;
    private String description;
}
```

### 3.3 字段分组类型

```java
public enum FieldGroupType {
    DIMENSION("d"),    // 维度
    QUOTA("q"),        // 指标
    TIME("t"),         // 时间
    UNDEFINED("u");    // 未定义
}
```

---

## 四、对比分析

### 4.1 核心模型对比

| 维度 | Superset | Metabase | DataEase | nop-metadata |
|------|----------|----------|----------|-------------|
| **数据源** | Database | Database | Datasource | MetaDataSource |
| **逻辑表** | SqlaTable | Table | DatasetTable | MetaTable |
| **字段** | TableColumn | Field | DatasetTableField | MetaEntityField |
| **指标** | SqlMetric | Metric | DatasetTableField(agg) | MetaTableMeasure |
| **数据分片** | - | Segment | - | MetaTableFilter |

### 4.2 元数据同步对比

| 能力 | Superset | Metabase | DataEase | nop-metadata |
|------|----------|----------|----------|-------------|
| **自动发现** | ✅ SQLAlchemy | ✅ Driver | ✅ JDBC | ✅ ORM 导入 |
| **增量同步** | ✅ | ✅ | ✅ | ✅ |
| **语义类型** | ❌ | ✅ 语义类型 | ✅ 分组类型 | ✅ Domain |
| **描述信息** | ✅ | ✅ | ✅ | ✅ |

### 4.3 指标定义对比

| 能力 | Superset | Metabase | DataEase | nop-metadata |
|------|----------|----------|----------|-------------|
| **预定义指标** | ✅ SqlMetric | ✅ Metric | ✅ Field.agg | ✅ MetaTableMeasure |
| **自定义 SQL** | ✅ | ✅ | ✅ | ✅ |
| **聚合函数** | ✅ | ✅ MBQL | ✅ | ✅ |
| **格式化** | ✅ d3format | ✅ | ✅ | ✅ |

---

## 五、对 nop-metadata 的启示

### 5.1 可借鉴的设计

| 设计 | 来源 | nop-metadata 应用 |
|------|------|------------------|
| **语义类型系统** | Metabase | MetaEntityField 的 semanticType |
| **字段分组** | DataEase | MetaTableMeasure 的 dimension/metric 区分 |
| **指标定义** | Superset | MetaTableMeasure 的 aggFunc + expression |
| **数据源同步** | 全部 | MetaDataSource 的 syncStatus |
| **自动发现** | 全部 | ORM 模型导入 + Catalog 收集 |

### 5.2 nop-metadata 的独特优势

| 优势 | 说明 |
|------|------|
| **ORM 原生集成** | 直接从 ORM 模型导入，无需额外同步 |
| **Delta 版本管理** | 支持模块级别的版本管理 |
| **统一逻辑表** | MetaTable 支持 entity 和 sql 两种类型 |
| **扩展属性** | extConfig 支持自定义元数据 |

### 5.3 需要补充的能力

| 能力 | 说明 | 优先级 |
|------|------|--------|
| **语义类型** | 字段的语义类型标记 | P1 |
| **数据源同步** | 外部数据源的元数据同步 | P2 |
| **指标格式化** | 指标的显示格式配置 | P2 |
| **数据分片** | 数据子集的筛选条件 | P3 |

---

## Open Questions

- [ ] nop-metadata 是否需要支持外部数据源的自动同步？
- [ ] 语义类型是否需要与 MetaDomain 集成？
- [ ] 指标格式化是否需要支持 D3 格式？

## References

- [Superset GitHub](https://github.com/apache/superset)
- [Metabase GitHub](https://github.com/metabase/metabase)
- [DataEase GitHub](https://github.com/dataease/dataease)
- 源码: `superset/connectors/sqla/models.py`
- 源码: `metabase/src/metabase/models/`
