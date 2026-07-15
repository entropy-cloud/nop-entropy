# Apache Griffin 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 Apache Griffin 的数据质量框架，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

Apache Griffin 是 Apache 基金会的数据质量项目，支持批处理和流处理的数据质量验证。它提供数据剖析、数据验证和数据清洗能力，适合大规模数据场景。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                  Apache Griffin                       │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Measure      │  │ Service      │  │ UI        │ │
│  │ (质量规则)   │  │ (REST API)   │  │ (管理)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        Engine (执行引擎)                         │ │
│  │  Batch Engine, Streaming Engine                │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
┌───────────▼──────┐ ┌─────▼─────┐ ┌───────▼──────┐
│   Data Sources   │ │  Metadata │ │  External    │
│  (Hive/Spark/    │ │  Store    │ │  Systems     │
│   JDBC)          │ │  (MySQL)  │ │  (Kafka)     │
└──────────────────┘ └───────────┘ └──────────────┘
```

### 2. 存储层

- **Metadata Store**: MySQL，存储规则、度量和结果
- **Data Sources**: Hive、Spark、JDBC
- **External Systems**: Kafka（流处理）

## 核心设计模式

### 模式 1：Measure 模型（质量规则）

**关键文件:**
- `measure/src/main/java/org/apache/griffin/measure/rule/`

**Measure 定义:**

```json
{
  "name": "row_count_check",
  "process.type": "batch",
  "rule.type": "accuracy",
  "rule.metric": {
    "name": "row_count",
    "type": "count",
    "field": "*"
  },
  "rule.definition": {
    "actual": {
      "type": "sparksql",
      "sparksql": "SELECT COUNT(*) AS cnt FROM source_table"
    },
    "expected": {
      "type": "sparksql",
      "sparksql": "SELECT COUNT(*) AS cnt FROM target_table"
    }
  },
  "rule.detail": {
    "tolerance": 0.01
  }
}
```

**Measure 类型:**

| 类型 | 说明 | 示例 |
|------|------|------|
| **profiling** | 数据剖析 | 列统计、分布 |
| **accuracy** | 准确性验证 | 数据对比 |
| **completeness** | 完整性验证 | 非空检查 |
| **uniqueness** | 唯一性验证 | 去重检查 |
| **timeliness** | 及时性验证 | 数据新鲜度 |

**设计优势:**
- **JSON 声明式**: 规则用 JSON 定义
- **多引擎支持**: 批处理和流处理
- **可扩展**: 支持自定义规则类型

---

### 模式 2：Metric 模型（度量）

**关键文件:**
- `measure/src/main/java/org/apache/griffin/measure/metric/`

**Metric 定义:**

```json
{
  "name": "source_table_completeness",
  "metric.type": "accuracy",
  "metric.values": [
    {
      "column": "order_id",
      "completeness": 0.9999
    },
    {
      "column": "amount",
      "completeness": 0.9995
    }
  ],
  "metric.update.timestamp": 1689000000000
}
```

**Metric 结构:**

```java
public class Metric {
    private String name;
    private MetricType type;
    private List<MetricValue> values;
    private long updateTimestamp;
    
    // 统计信息
    private double completeness;  // 完整性
    private double accuracy;      // 准确性
    private double uniqueness;   // 唯一性
    private double timeliness;   // 及时性
}
```

**设计优势:**
- **多维度度量**: 支持多种质量维度
- **时序存储**: 支持趋势分析
- **标准化格式**: JSON 格式易于集成

---

### 模式 3：Rule 执行引擎

**关键文件:**
- `engine/src/main/java/org/apache/griffin/measure/`

**批处理执行:**

```java
public class BatchDQEngine implements DQEngine {
    
    public DQResult execute(Measure measure, DataSource dataSource) {
        // 1. 加载数据
        Dataset<Row> data = loadData(dataSource);
        
        // 2. 执行规则
        List<Metric> metrics = new ArrayList<>();
        
        switch (measure.getRuleType()) {
            case PROFILING:
                metrics = executeProfiling(data, measure);
                break;
            case ACCURACY:
                metrics = executeAccuracy(data, measure);
                break;
            case COMPLETENESS:
                metrics = executeCompleteness(data, measure);
                break;
            case UNIQUENESS:
                metrics = executeUniqueness(data, measure);
                break;
        }
        
        // 3. 构建结果
        return DQResult.builder()
            .measure(measure)
            .metrics(metrics)
            .success(validateThresholds(metrics, measure))
            .build();
    }
}
```

**流处理执行:**

```java
public class StreamingDQEngine implements DQEngine {
    
    public void executeStreaming(Measure measure, StreamingContext context) {
        // 1. 创建流处理作业
        JavaStreamingContext jssc = context.getStreamingContext();
        
        // 2. 接收数据流
        JavaReceiverInputDStream<String> stream = 
            jssc.socketTextStream("localhost", 9999);
        
        // 3. 实时验证
        stream.foreachRDD(rdd -> {
            Dataset<Row> data = parseData(rdd);
            DQResult result = execute(measure, data);
            saveResult(result);
        });
        
        // 4. 启动流处理
        jssc.start();
    }
}
```

**设计优势:**
- **批流一体**: 支持批处理和流处理
- **可扩展引擎**: 支持 Spark、Flink
- **实时验证**: 流数据实时质量检查

---

### 模式 4：DQ Result 模型

**关键文件:**
- `service/src/main/java/org/apache/griffin/core/quality/`

**DQ Result 结构:**

```json
{
  "id": "result_123",
  "measure.name": "row_count_check",
  "process.type": "batch",
  "status": "SUCCESS",
  "start.time": 1689000000000,
  "end.time": 1689000060000,
  "metrics": [
    {
      "name": "row_count",
      "type": "count",
      "value": 1000000,
      "threshold": 0.99
    }
  ],
  "detail": {
    "total": 1000000,
    "passed": 999999,
    "failed": 1,
    "pass.rate": 0.999999
  }
}
```

**DQ Result 查询:**

```sql
-- 查询历史结果
SELECT * FROM dq_result 
WHERE measure_name = 'row_count_check'
AND start_time > DATE_SUB(NOW(), 7)
ORDER BY start_time DESC;

-- 查询通过率趋势
SELECT 
    DATE(start_time) as dt,
    AVG(detail.pass_rate) as avg_pass_rate
FROM dq_result
WHERE measure_name = 'row_count_check'
GROUP BY DATE(start_time)
ORDER BY dt;
```

**设计优势:**
- **结构化存储**: 关系型数据库存储
- **时序查询**: 支持历史趋势查询
- **统计信息**: 详细的验证统计

---

### 模式 5：数据剖析（Profiling）

**关键文件:**
- `measure/src/main/java/org/apache/griffin/measure/rule/dq/`

**数据剖析规则:**

```json
{
  "name": "column_profiling",
  "rule.type": "profiling",
  "rule.metric": {
    "type": "profiling",
    "columns": ["order_id", "amount", "status"]
  },
  "rule.definition": {
    "type": "sparksql",
    "sparksql": """
      SELECT 
        COUNT(*) as total_count,
        COUNT(DISTINCT order_id) as distinct_count,
        SUM(CASE WHEN amount IS NULL THEN 1 ELSE 0 END) as null_count,
        AVG(amount) as avg_amount,
        MIN(amount) as min_amount,
        MAX(amount) as max_amount,
        STDDEV(amount) as stddev_amount
      FROM source_table
    """
  }
}
```

**剖析结果:**

```json
{
  "column": "amount",
  "statistics": {
    "total_count": 1000000,
    "distinct_count": 999999,
    "null_count": 1,
    "empty_count": 0,
    "min_value": 0.01,
    "max_value": 999999.99,
    "mean": 500.0,
    "median": 250.0,
    "stddev": 1000.0,
    "percentiles": {
      "25": 100.0,
      "50": 250.0,
      "75": 500.0,
      "95": 2000.0,
      "99": 5000.0
    }
  },
  "distribution": {
    "buckets": [0, 100, 500, 1000, 5000, 10000, 50000, 100000],
    "counts": [100000, 200000, 300000, 200000, 100000, 50000, 30000, 20000]
  }
}
```

**设计优势:**
- **全面统计**: 支持多种统计指标
- **分布分析**: 支持数据分布可视化
- **异常检测**: 支持异常值检测

---

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **Measure 模型**: JSON 声明式质量规则定义
2. **多维度度量**: profiling/accuracy/completeness/uniqueness/timeliness
3. **批流一体引擎**: 支持批处理和流处理
4. **数据剖析**: 丰富的统计指标和分布分析
5. **时序结果存储**: 支持趋势分析

### 与 nop-metadata 的对比

| 能力 | Apache Griffin | nop-metadata |
|------|---------------|-------------|
| 质量规则 | Measure (JSON) | MetaQualityRule |
| 度量类型 | profiling/accuracy/... | ruleType 枚举 |
| 执行引擎 | 批处理/流处理 | 扩展支持 |
| 结果存储 | 关系型数据库 | MetaQualityResult |
| 数据剖析 | 内置支持 | 可扩展 |

### nop-metadata 可复用的模式

1. **Measure 模型**: 质量规则的 JSON 声明式定义
2. **度量类型扩展**: profiling/accuracy/completeness/uniqueness/timeliness
3. **数据剖析**: 扩展 MetaQualityRule 支持统计分析
4. **批流一体**: 考虑流处理场景的支持

## Open Questions

- [ ] nop-metadata 是否需要支持数据剖析（profiling）？
- [ ] 质量规则是否需要支持流处理场景？
- [ ] 统计指标是否需要作为独立实体？

## References

- [Apache Griffin GitHub](https://github.com/apache/griffin)
- [Apache Griffin 文档](https://griffin.apache.org/docs/quick-start.html)
- 源码: `measure/src/main/java/org/apache/griffin/measure/`
- 源码: `engine/src/main/java/org/apache/griffin/measure/`
