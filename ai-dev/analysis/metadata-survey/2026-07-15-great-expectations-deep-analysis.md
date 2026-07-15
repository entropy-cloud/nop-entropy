# Great Expectations 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 Great Expectations 的数据质量验证框架，为 nop-metadata 的质量规则设计提供参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

Great Expectations (GX) 是一个领先的数据质量验证框架，提供声明式数据验证、数据文档生成和数据谱系追踪。本文档分析其核心设计模式，为 nop-metadata 的 MetaQualityRule 设计提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                  Great Expectations                   │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Expectations │  │ Checkpoints  │  │ Data Docs │ │
│  │ (验证规则)   │  │ (执行编排)   │  │ (文档)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        Execution Engine (执行引擎)               │ │
│  │  Pandas, Spark, SQL Alchemy                    │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
┌───────────▼──────┐ ┌─────▼─────┐ ┌───────▼──────┐
│   Data Source    │ │  Metadata │ │  Data Docs   │
│  (Pandas/Spark/  │ │  Store    │ │  (HTML/JSON) │
│   SQL)           │ │           │ │              │
└──────────────────┘ └───────────┘ └──────────────┘
```

### 2. 核心概念

- **Expectation**: 数据质量断言（如 "列A不应为空"）
- **Expectation Suite**: 一组相关的 Expectations
- **Checkpoint**: 验证执行点（运行 Expectations 并输出结果）
- **Validation Result**: 验证结果（通过/失败）
- **Data Docs**: 生成的数据质量文档

## 核心设计模式

### 模式 1：Expectation 声明式定义

**关键文件:**
- `great_expectations/expectations/core/`

**Expectation 类型:**

```python
# 基础 Expectation
class ExpectColumnValuesToNotBeNull(Expectation):
    """验证列值不为空"""
    column: str
    mostly: float = 1.0  # 允许的比例

class ExpectColumnValuesToBeUnique(Expectation):
    """验证列值唯一"""
    column: str
    mostly: float = 1.0

class ExpectColumnValuesToBeBetween(Expectation):
    """验证列值在范围内"""
    column: str
    min_value: Optional[float]
    max_value: Optional[float]
    mostly: float = 1.0

class ExpectColumnValuesToMatchRegex(Expectation):
    """验证列值匹配正则"""
    column: str
    regex: str
    mostly: float = 1.0

# 表级 Expectation
class ExpectTableRowCountToBeBetween(Expectation):
    """验证行数在范围内"""
    min_value: Optional[int]
    max_value: Optional[int]

class ExpectTableColumnCountToBeBetween(Expectation):
    """验证列数在范围内"""
    min_value: Optional[int]
    max_value: Optional[int]
```

**Expectation 定义示例:**

```python
# Python 定义
expectation_suite = ExpectationSuite(
    expectation_suite_name="orders_suite",
    expectations=[
        {
            "expectation_type": "expect_column_values_to_not_null",
            "kwargs": {"column": "order_id"},
            "meta": {"description": "订单ID不应为空"}
        },
        {
            "expectation_type": "expect_column_values_to_be_unique",
            "kwargs": {"column": "order_id"},
            "meta": {"description": "订单ID应唯一"}
        },
        {
            "expectation_type": "expect_column_values_to_be_between",
            "kwargs": {
                "column": "amount",
                "min_value": 0,
                "max_value": 1000000
            },
            "meta": {"description": "金额应在0-1000000之间"}
        }
    ]
)
```

**设计优势:**
- **声明式**: Expectation 定义清晰、可读
- **可组合**: Suite 可以包含多种 Expectation
- **元数据支持**: meta 字段支持自定义描述

---

### 模式 2：Checkpoint 执行编排

**关键文件:**
- `great_expectations/checkpoint/`

**Checkpoint 定义:**

```python
# Python 定义
checkpoint = SimpleCheckpoint(
    name="orders_checkpoint",
    run_name_template="%Y%m%d_%H%M%S",
    validations=[
        {
            "batch_request": {
                "datasource_name": "my_datasource",
                "data_asset_name": "orders"
            },
            "expectation_suite_name": "orders_suite"
        }
    ],
    action_list=[
        {
            "name": "store_validation_result",
            "action": {
                "class_name": "StoreValidationResultAction",
                "target_store_name": "validation_result_store"
            }
        },
        {
            "name": "update_data_docs",
            "action": {
                "class_name": "UpdateDataDocsAction"
            }
        }
    ]
)
```

**Checkpoint 执行流程:**

```python
class Checkpoint:
    """验证执行点"""
    
    def run(self, batch_request, expectation_suite):
        # 1. 加载数据
        batch = self.engine.load_batch(batch_request)
        
        # 2. 执行 Expectations
        results = []
        for expectation in expectation_suite.expectations:
            result = expectation.validate(batch)
            results.append(result)
        
        # 3. 存储结果
        validation_result = ValidationResult(
            success=all(r.success for r in results),
            results=results,
            statistics=self.compute_statistics(results)
        )
        
        # 4. 执行 actions
        for action in self.action_list:
            action.run(validation_result)
        
        return validation_result
```

**设计优势:**
- **可配置执行**: Checkpoint 定义验证执行逻辑
- **Action 模式**: 结果处理可扩展（存储、通知、文档更新）
- **批处理支持**: 支持增量验证

---

### 模式 3：Validation Result 模型

**关键文件:**
- `great_expectations/core/expectation_validation_result.py`

**Validation Result 结构:**

```python
@dataclass
class ValidationResult:
    """验证结果"""
    success: bool                    # 是否通过
    results: List[ExpectationValidationResult]  # 各 Expectation 结果
    statistics: ResultStatistics      # 统计信息
    meta: Optional[Dict]              # 元数据
    evaluation_parameters: Dict       # 评估参数
    run_name: str                     # 运行名称
    run_time: datetime                # 运行时间

@dataclass
class ExpectationValidationResult:
    """单个 Expectation 验证结果"""
    success: bool
    expectation_config: ExpectationConfig
    result: Dict                     # 验证结果详情
    meta: Optional[Dict]

@dataclass
class ResultStatistics:
    """统计信息"""
    evaluated_expectations: int      # 评估的 Expectation 数量
    successful_expectations: int     # 通过的数量
    unsuccessful_expectations: int   # 失败的数量
    success_percent: float           # 通过率
```

**Validation Result 示例:**

```json
{
  "success": false,
  "results": [
    {
      "success": true,
      "expectation_config": {
        "expectation_type": "expect_column_values_to_not_null",
        "kwargs": {"column": "order_id"}
      },
      "result": {
        "observed_value": 1000000,
        "element_count": 1000000,
        "missing_count": 0,
        "missing_percent": 0.0
      }
    },
    {
      "success": false,
      "expectation_config": {
        "expectation_type": "expect_column_values_to_be_between",
        "kwargs": {"column": "amount", "min_value": 0, "max_value": 1000000}
      },
      "result": {
        "observed_value": 1500000,
        "element_count": 1000000,
        "unexpected_count": 5000,
        "unexpected_percent": 0.5
      }
    }
  ],
  "statistics": {
    "evaluated_expectations": 50,
    "successful_expectations": 48,
    "unsuccessful_expectations": 2,
    "success_percent": 96.0
  }
}
```

**设计优势:**
- **结构化结果**: 结果包含详细信息
- **统计信息**: 提供通过率等统计
- **可追溯**: 每个结果关联 Expectation 配置

---

### 模式 4：Data Docs 文档生成

**关键文件:**
- `great_expectations/render/`

**Data Docs 类型:**

```python
class DataDocsRenderer:
    """数据文档渲染器"""
    
    def render(self, validation_results):
        # 1. 渲染 Expectation Suite 文档
        suite_doc = self.render_suite(validation_results.suite)
        
        # 2. 渲染 Validation Result 文档
        result_doc = self.render_result(validation_results)
        
        # 3. 渲染统计摘要
        stats_doc = self.render_statistics(validation_results.statistics)
        
        return DataDoc(
            suite=suite_doc,
            result=result_doc,
            statistics=stats_doc
        )
```

**文档内容:**

```
# 数据质量文档

## Expectation Suite: orders_suite

| Expectation | 描述 | 状态 |
|-------------|------|------|
| expect_column_values_to_not_null | order_id 不应为空 | ✅ 通过 |
| expect_column_values_to_be_unique | order_id 应唯一 | ✅ 通过 |
| expect_column_values_to_be_between | amount 应在 0-1000000 | ❌ 失败 |

## 最近验证结果

| 运行时间 | 状态 | 通过率 |
|---------|------|--------|
| 2026-07-15 10:00 | 失败 | 96% |
| 2026-07-14 10:00 | 通过 | 100% |

## 趋势

- 通过率趋势图
- 失败 Expectation 分析
```

**设计优势:**
- **自动生成文档**: 从验证结果自动生成文档
- **多格式输出**: HTML、JSON、Markdown
- **历史追踪**: 追踪验证历史和趋势

---

### 模式 5：数据谱系追踪

**关键文件:**
- `great_expectations/expectations/core/`

**谱系元数据:**

```python
class Expectation:
    """Expectation 基类"""
    
    meta: Dict[str, Any]  # 支持自定义元数据
    
    # 谱系相关元数据
    def get_lineage_metadata(self):
        return {
            "expectation_type": self.expectation_type,
            "column": self.kwargs.get("column"),
            "table": self.kwargs.get("table"),
            "data_source": self.data_source,
            "created_at": self.created_at,
            "updated_at": self.updated_at
        }
```

**谱系查询:**

```python
class LineageTracker:
    """谱系追踪器"""
    
    def trace_column(self, table, column):
        """追踪列的谱系"""
        # 查找引用此列的 Expectations
        expectations = self.find_expectations(table, column)
        
        # 构建谱系图
        lineage = LineageGraph()
        for exp in expectations:
            lineage.add_edge(
                source=exp.data_source,
                target=f"{table}.{column}",
                expectation=exp
            )
        
        return lineage
```

**设计优势:**
- **元数据扩展**: 通过 meta 字段存储谱系信息
- **列级追踪**: 支持列级数据质量追踪
- **可追溯**: 追踪数据质量规则的来源

---

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **声明式质量规则**: Expectation 定义清晰、可读、可组合
2. **Validation Result 模型**: 结构化验证结果 + 统计信息
3. **Checkpoint 执行模式**: 可配置的验证执行编排
4. **Data Docs 文档生成**: 从验证结果自动生成文档
5. **谱系元数据**: 通过 meta 字段存储谱系信息

### 与 nop-metadata 的对比

| 能力 | Great Expectations | nop-metadata |
|------|-------------------|-------------|
| 质量规则定义 | Expectation Suite | MetaQualityRule |
| 验证执行 | Checkpoint | 扩展 MetaQualityResult |
| 结果存储 | Validation Result Store | MetaQualityResult |
| 文档生成 | Data Docs | 复用 nop-report |
| 谱系追踪 | meta 字段 | MetaLineageEdge |

### nop-metadata 可复用的模式

1. **Expectation 类型化**: MetaQualityRule 的 ruleType 扩展
2. **mostly 参数**: 支持容错比例（如 99% 非空）
3. **Validation Result 统计**: 扩展 MetaQualityResult 的统计字段
4. **Checkpoint 模式**: 验证执行编排的标准化

## Open Questions

- [ ] nop-metadata 的质量规则是否需要支持 mostly 容错比例？
- [ ] 验证结果是否需要支持趋势分析？
- [ ] 质量文档是否需要自动生成？

## References

- [Great Expectations GitHub](https://github.com/great-expectations/great_expectations)
- [Great Expectations 文档](https://docs.greatexpectations.io/)
- 源码: `great_expectations/expectations/core/`
- 源码: `great_expectations/checkpoint/`
