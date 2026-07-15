# nop-metadata 数据质量扩展设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 数据质量规则扩展、数据剖析和验证执行
> Goal: 定义数据质量扩展模型，参考 Great Expectations 和 Apache Griffin
> Based on: Great Expectations Expectation Suite、Apache Griffin Measure 模型

---

## 一、设计决策

### 1.1 质量规则扩展

**决策**: 扩展 MetaQualityRule，支持更多规则类型和参数。

**扩展的 ruleType**:
- `profiling` — 数据剖析（统计分析）
- `schema` — Schema 一致性检查
- `fingerprint` — 数据指纹检查
- `custom_expectation` — 自定义 Expectation

### 1.2 数据剖析支持

**决策**: 参考 Apache Griffin，支持数据剖析（profiling）能力。

**剖析维度**:
- 值分布分析
- 统计指标（均值、中位数、标准差等）
- 异常值检测
- 数据新鲜度

### 1.3 验证执行模式

**决策**: 参考 Great Expectations 的 Checkpoint 模式，支持验证执行编排。

**执行模式**:
- 单规则执行
- 批量规则执行
- 定时执行
- 事件触发执行

---

## 二、质量规则扩展

### 2.1 扩展的 ruleType

```
MetaQualityRule                  — 质量规则定义（扩展）
  ├── ruleName / displayName
  ├── ruleType                   — 扩展的规则类型
  ├── entityType                 — "field" | "table" | "database"
  ├── entityId                   → MetaEntityField | MetaTable | MetaDataSource
  ├── severity                   — "error" | "warning" | "info"
  ├── sqlExpression              — 自定义 SQL 表达式
  ├── threshold                  — 阈值
  ├── params                     — JSON 参数
  ├── mostly                     — 容错比例（0.0~1.0，默认 1.0）
  ├── tags                       — 标签集合
  └── extConfig
```

### 2.2 完整的 ruleType 列表

| ruleType | 适用对象 | 说明 | params 示例 |
|----------|----------|------|-------------|
| `not_null` | field | 非空检查 | `{"threshold": 0.99}` |
| `unique` | field | 唯一性检查 | `{"sampleSize": 10000}` |
| `range` | field | 范围检查 | `{"min": 0, "max": 1000000}` |
| `regex` | field | 正则匹配 | `{"pattern": "^\\d{4}-\\d{2}-\\d{2}$"}` |
| `freshness` | table | 新鲜度检查 | `{"maxAgeMinutes": 60}` |
| `volume` | table | 行数检查 | `{"minRows": 1000, "maxRows": 10000000}` |
| `custom_sql` | table | 自定义SQL | `{"sql": "SELECT COUNT(*) FROM t WHERE ..."}` |
| **`profiling`** | table | 数据剖析 | `{"columns": ["col1", "col2"], "stats": ["mean", "median", "stddev"]}` |
| **`schema`** | table | Schema 检查 | `{"expectedColumns": [{"name": "id", "type": "integer"}]}` |
| **`fingerprint`** | table | 数据指纹 | `{"algorithm": "md5", "sampleSize": 1000}` |
| **`custom_expectation`** | table | 自定义 Expectation | `{"expectation": "expect_column_values_to_be_in_set", "kwargs": {...}}` |

### 2.3 mostly 容错参数

**说明**: `mostly` 参数表示允许的容错比例。例如 `mostly=0.99` 表示 99% 的值满足规则即可通过。

```json
{
  "ruleName": "order_id_not_null",
  "ruleType": "not_null",
  "entityType": "field",
  "entityId": "order_id",
  "mostly": 0.99,
  "severity": "warning"
}
```

**验证逻辑**:
```python
def validate_not_null(column_values, mostly=1.0):
    non_null_count = sum(1 for v in column_values if v is not None)
    total_count = len(column_values)
    pass_rate = non_null_count / total_count
    return pass_rate >= mostly
```

---

## 三、数据剖析（Profiling）

### 3.1 剖析规则定义

```
MetaProfilingRule                — 数据剖析规则
  ├── ruleName / displayName
  ├── tableId                    → MetaTable
  ├── columns[]                  — 要剖析的列（空表示所有列）
  │   └── columnName
  ├── stats[]                    — 要收集的统计指标
  │   └── "count" | "distinct_count" | "null_count" | "mean" | "median" |
  │       "stddev" | "min" | "max" | "percentiles" | "distribution"
  ├── sampleSize                 — 采样大小（可选）
  ├── schedule                   — 执行计划（可选）
  └── extConfig
```

### 3.2 剖析结果模型

```
MetaProfilingResult              — 数据剖析结果
  ├── ruleId                     → MetaProfilingRule
  ├── snapshotTime               — 快照时间
  ├── tableId                    → MetaTable
  │
  ├── tableStats                 — 表级统计
  │   ├── rowCount               — 行数
  │   ├── sizeBytes              — 大小（字节）
  │   └── lastModified           — 最后修改时间
  │
  └── columnStats[]              — 列级统计
      ├── columnName
      ├── dataType               — 数据类型
      ├── totalCount             — 总数
      ├── distinctCount          — 唯一值数
      ├── nullCount              — 空值数
      ├── emptyCount             — 空字符串数
      │
      ├── numericStats           — 数值列统计（可选）
      │   ├── minValue
      │   ├── maxValue
      │   ├── meanValue
      │   ├── medianValue
      │   ├── stddevValue
      │   └── percentiles        — 百分位数 {"25": 100, "50": 250, "75": 500}
      │
      ├── stringStats            — 字符串列统计（可选）
      │   ├── minLength
      │   ├── maxLength
      │   ├── avgLength
      │   └── topValues          — Top N 值 [{"value": "xxx", "count": 100}]
      │
      └── distribution           — 值分布（可选）
          ├── buckets            — 分桶边界 [0, 100, 500, 1000]
          └── counts             — 每桶计数 [1000, 2000, 3000, 500]
```

### 3.3 剖析结果示例

```json
{
  "ruleId": "profiling_orders",
  "snapshotTime": "2026-07-15T10:00:00Z",
  "tableId": "orders",
  "tableStats": {
    "rowCount": 1000000,
    "sizeBytes": 52428800
  },
  "columnStats": [
    {
      "columnName": "order_id",
      "dataType": "bigint",
      "totalCount": 1000000,
      "distinctCount": 999999,
      "nullCount": 1,
      "numericStats": {
        "minValue": 1,
        "maxValue": 1000000,
        "meanValue": 500000.5,
        "medianValue": 500000
      }
    },
    {
      "columnName": "amount",
      "dataType": "decimal(10,2)",
      "totalCount": 1000000,
      "distinctCount": 999999,
      "nullCount": 0,
      "numericStats": {
        "minValue": 0.01,
        "maxValue": 999999.99,
        "meanValue": 500.0,
        "medianValue": 250.0,
        "stddevValue": 1000.0,
        "percentiles": {"25": 100.0, "50": 250.0, "75": 500.0, "95": 2000.0}
      }
    }
  ]
}
```

---

## 四、验证执行模式

### 4.1 Checkpoint 模型

参考 Great Expectations 的 Checkpoint 模式：

```
MetaQualityCheckpoint            — 质量验证检查点
  ├── checkpointName / displayName
  ├── moduleId                   → MetaModule
  │
  ├── validations[]              — 验证配置列表
  │   └── ValidationConfig
  │       ├── ruleIds            — 要执行的规则 ID 列表
  │       ├── tableIds           — 要验证的表 ID 列表
  │       └── includeInherited   — 是否包含继承的规则
  │
  ├── schedule                   — 执行计划（可选）
  │   ├── cronExpression         — Cron 表达式
  │   └── timezone               — 时区
  │
  ├── actions[]                  — 执行动作
  │   └── Action
  │       ├── actionType         — "store" | "notify" | "update_docs"
  │       ├── config             — JSON 配置
  │       └── enabled
  │
  └── status                     — "active" | "paused" | "disabled"
```

### 4.2 Checkpoint 执行流程

```python
class QualityCheckpoint:
    """质量验证检查点"""
    
    def execute(self, checkpoint):
        """执行检查点"""
        
        # 1. 加载验证配置
        validations = checkpoint.validations
        
        # 2. 执行验证
        results = []
        for validation in validations:
            rules = self.load_rules(validation.ruleIds)
            tables = self.load_tables(validation.tableIds)
            
            for rule in rules:
                for table in tables:
                    result = self.execute_rule(rule, table)
                    results.append(result)
        
        # 3. 执行动作
        for action in checkpoint.actions:
            if action.enabled:
                self.execute_action(action, results)
        
        # 4. 存储结果
        self.store_results(results)
        
        return results
    
    def execute_rule(self, rule, table):
        """执行单个规则"""
        
        # 获取表数据
        data = self.load_table_data(table)
        
        # 根据规则类型执行
        if rule.ruleType == "not_null":
            return self.validate_not_null(rule, data)
        elif rule.ruleType == "unique":
            return self.validate_unique(rule, data)
        elif rule.ruleType == "range":
            return self.validate_range(rule, data)
        elif rule.ruleType == "profiling":
            return self.execute_profiling(rule, data)
        # ... 其他规则类型
```

### 4.3 执行动作

| 动作类型 | 说明 | 配置示例 |
|---------|------|---------|
| `store` | 存储验证结果 | `{"target": "meta_quality_result"}` |
| `notify` | 发送通知 | `{"channel": "email", "recipients": ["data@company.com"]}` |
| `update_docs` | 更新数据文档 | `{"format": "html", "outputPath": "/docs/quality"}` |
| `webhook` | 调用 Webhook | `{"url": "https://hooks.company.com/quality", "method": "POST"}` |

---

## 五、质量评分

### 5.1 质量评分模型

```
MetaQualityScore                — 质量评分
  ├── entityId                   → MetaTable | MetaEntity
  ├── scoreTime                  — 评分时间
  ├── overallScore               — 总分（0~100）
  │
  ├── dimensionScores            — 维度评分
  │   ├── completeness           — 完整性评分
  │   ├── accuracy               — 准确性评分
  │   ├── consistency            — 一致性评分
  │   ├── timeliness             — 及时性评分
  │   └── uniqueness             — 唯一性评分
  │
  ├── ruleResults                — 规则执行结果汇总
  │   ├── totalRules             — 总规则数
  │   ├── passedRules            — 通过规则数
  │   ├── failedRules            — 失败规则数
  │   └── errorRules             — 错误规则数
  │
  └── trend                      — 趋势数据
      ├── previousScore          — 上次评分
      ├── changeRate             — 变化率
      └── trendDirection         — "improving" | "stable" | "degrading"
```

### 5.2 评分计算

```python
class QualityScorer:
    """质量评分器"""
    
    def calculate_score(self, entity_id):
        """计算质量评分"""
        
        # 1. 获取规则结果
        results = self.get_rule_results(entity_id)
        
        # 2. 计算维度评分
        completeness = self.calculate_completeness(results)
        accuracy = self.calculate_accuracy(results)
        consistency = self.calculate_consistency(results)
        timeliness = self.calculate_timeliness(results)
        uniqueness = self.calculate_uniqueness(results)
        
        # 3. 计算总分
        overall = (
            completeness * 0.3 +
            accuracy * 0.3 +
            consistency * 0.2 +
            timeliness * 0.1 +
            uniqueness * 0.1
        )
        
        # 4. 获取趋势
        previous_score = self.get_previous_score(entity_id)
        trend = self.calculate_trend(overall, previous_score)
        
        return MetaQualityScore(
            entityId=entity_id,
            overallScore=overall,
            dimensionScores={
                "completeness": completeness,
                "accuracy": accuracy,
                "consistency": consistency,
                "timeliness": timeliness,
                "uniqueness": uniqueness
            },
            trend=trend
        )
```

---

## 六、与 metadata-survey 的对比

| 能力 | Great Expectations | Apache Griffin | nop-metadata |
|------|-------------------|---------------|-------------|
| 质量规则 | Expectation Suite | Measure | MetaQualityRule (扩展) |
| 容错比例 | mostly 参数 | - | mostly 参数 |
| 数据剖析 | - | profiling | MetaProfilingRule |
| 验证执行 | Checkpoint | Engine | MetaQualityCheckpoint |
| 质量评分 | - | - | MetaQualityScore |
| 文档生成 | Data Docs | - | 复用 nop-report |

## Open Questions

- [ ] 质量评分的维度权重是否可配置？
- [ ] 数据剖析是否需要支持增量剖析？
- [ ] Checkpoint 是否需要支持流式验证？
