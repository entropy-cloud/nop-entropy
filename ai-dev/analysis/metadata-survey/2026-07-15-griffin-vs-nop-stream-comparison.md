# Apache Griffin vs nop-stream 流批处理对比分析

> Status: open
> Date: 2026-07-15
> Scope: 流批处理架构对比
> Goal: 对比 Apache Griffin 的流批设计与 nop-stream，识别可借鉴模式

---

## 一、架构对比

### 1.1 Apache Griffin 架构

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
│  │  ┌─────────────────┐  ┌──────────────────────┐ │ │
│  │  │ Batch Engine    │  │ Streaming Engine     │ │ │
│  │  │ (Spark Batch)   │  │ (Spark Streaming/    │ │ │
│  │  │                 │  │  Flink)              │ │ │
│  │  └─────────────────┘  └──────────────────────┘ │ │
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

**Griffin 特点**:
- **批流分离**: Batch Engine 和 Streaming Engine 是两个独立的执行引擎
- **基于 Spark**: 批处理使用 Spark Batch，流处理使用 Spark Streaming
- **质量验证为主**: 专注于数据质量验证场景

### 1.2 nop-stream 架构

```
┌─────────────────────────────────────────────────────┐
│                  nop-stream                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ API Layer    │  │ StreamModel  │  │ CEP       │ │
│  │ (DataStream) │  │ (图模型)     │  │ (模式匹配)│ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        五层执行管线                               │ │
│  │  StreamModel → StreamGraph → JobGraph          │ │
│  │              → PartitionedPlan → DeploymentPlan│ │
│  └────────────────────────┬───────────────────────┘ │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        执行引擎                                  │ │
│  │  ┌─────────────────┐  ┌──────────────────────┐ │ │
│  │  │ LOCAL           │  │ DISTRIBUTED          │ │ │
│  │  │ (线程池)        │  │ (多 TaskManager)     │ │ │
│  │  └─────────────────┘  └──────────────────────┘ │ │
│  └───────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

**nop-stream 特点**:
- **流批一体**: 同一套执行管线，支持批处理和流处理
- **声明式图模型**: StreamModel 是核心，支持 XDSL/Java API/Delta 三种入口
- **分布式 exactly-once**: 通过 epoch checkpoint + barrier 对齐实现

---

## 二、核心差异

### 2.1 流批处理模式

| 维度 | Apache Griffin | nop-stream |
|------|---------------|-----------|
| **流批关系** | 批流分离（两个引擎） | 流批一体（统一管线） |
| **执行引擎** | Spark Batch + Spark Streaming | 自研引擎（线程池/分布式） |
| **数据模型** | Measure（质量规则） | StreamModel（算子图） |
| **状态管理** | 无状态（每次重新计算） | 有状态（Checkpoint + 状态后端） |
| **Exactly-once** | 不保证 | 严格 Exactly-once |

### 2.2 设计目标

| 目标 | Apache Griffin | nop-stream |
|------|---------------|-----------|
| **核心场景** | 数据质量验证 | 通用流处理 |
| **批处理** | ✅ 主要场景 | ✅ 支持 |
| **流处理** | ✅ 支持 | ✅ 主要场景 |
| **CEP** | ❌ | ✅ 内置 |
| **分布式** | ✅ 基于 Spark | ✅ 自研 |

### 2.3 执行模型

**Apache Griffin 批处理**:
```java
// 批处理执行
public class BatchDQEngine {
    public DQResult execute(Measure measure, DataSource dataSource) {
        // 1. 加载数据到 Spark
        Dataset<Row> data = spark.read().jdbc(url, table, props);
        
        // 2. 执行规则（Spark SQL）
        Dataset<Row> result = data.sqlContext().sql(measure.getSql());
        
        // 3. 收集结果
        return collectResult(result);
    }
}
```

**Apache Griffin 流处理**:
```java
// 流处理执行
public class StreamingDQEngine {
    public void execute(Measure measure, StreamingContext context) {
        // 1. 创建 DStream
        JavaDStream<String> stream = context.socketTextStream(host, port);
        
        // 2. 实时验证
        stream.foreachRDD(rdd -> {
            Dataset<Row> data = spark.read().json(rdd);
            DQResult result = executeMeasure(measure, data);
            saveResult(result);
        });
    }
}
```

**nop-stream 执行**:
```java
// 统一执行管线
public class StreamExecution {
    public void execute(StreamModel model) {
        // 1. 编译 StreamModel
        StreamGraph streamGraph = compile(model);
        JobGraph jobGraph = optimize(streamGraph);
        PartitionedPlan plan = partition(jobGraph);
        DeploymentPlan deployment = deploy(plan);
        
        // 2. 执行（本地或分布式）
        if (deployment.getMode() == LOCAL) {
            executeLocal(deployment);
        } else {
            executeDistributed(deployment);
        }
    }
}
```

---

## 三、可借鉴模式

### 3.1 从 Griffin 借鉴

| 模式 | 说明 | nop-metadata 应用 |
|------|------|------------------|
| **Measure 模型** | JSON 声明式质量规则 | MetaQualityRule |
| **多维度度量** | profiling/accuracy/completeness | 扩展 ruleType |
| **数据剖析** | 统计指标收集 | MetaProfilingRule |
| **批流分离** | 简单场景不需要复杂状态管理 | 简单质量检查可复用 |

### 3.2 从 nop-stream 借鉴

| 模式 | 说明 | nop-metadata 应用 |
|------|------|------------------|
| **流批一体** | 统一执行管线 | 复用 nop-stream 执行质量检查 |
| **声明式模型** | XDSL 定义执行流程 | 质量检查流程声明式定义 |
| **状态管理** | Checkpoint + 状态后端 | 质量结果时序存储 |
| **Exactly-once** | 严格一次语义 | 质量检查幂等性 |

---

## 四、nop-metadata 与 nop-stream 的集成

### 4.1 集成场景

| 场景 | 说明 | 实现方式 |
|------|------|---------|
| **批处理质量检查** | 定时执行质量规则 | nop-batch + MetaQualityRule |
| **流处理质量检查** | 实时监控数据质量 | nop-stream + CEP |
| **数据剖析** | 定时收集统计信息 | nop-batch + MetaProfilingRule |

### 4.2 批处理集成

```
MetaQualityRule → nop-batch → SQL 执行 → MetaQualityResult
```

```java
// nop-batch 批处理器
public class QualityRuleProcessor implements IChunkProcessor {
    
    @Inject
    private MetaQualityRuleRepository ruleRepository;
    
    @Inject
    private MetaQualityResultRepository resultRepository;
    
    @Override
    public void process(MetaQualityRule rule) {
        // 1. 执行 SQL
        DQResult dqResult = executeRule(rule);
        
        // 2. 存储结果
        MetaQualityResult result = new MetaQualityResult();
        result.setRuleId(rule.getId());
        result.setExecuteTime(Instant.now());
        result.setStatus(dqResult.getStatus());
        result.setActualValue(dqResult.getActualValue());
        result.setExpectedValue(dqResult.getExpectedValue());
        
        resultRepository.save(result);
    }
}
```

### 4.3 流处理集成

```
数据流 → nop-stream CEP → 异常检测 → 告警
```

```java
// nop-stream CEP 模式匹配
public class QualityAnomalyPattern {
    
    // 定义异常模式：连续 3 次质量检查失败
    public Pattern<QualityResult, ?> definePattern() {
        return Pattern.<QualityResult>begin("start")
            .where(result -> "fail".equals(result.getStatus()))
            .timesOrMore(3)
            .within(Time.minutes(5));
    }
}
```

---

## 五、总结

### 5.1 Apache Griffin 适合的场景

- **简单质量验证**: 不需要复杂状态管理
- **批处理为主**: 数据仓库质量检查
- **基于 Spark**: 已有 Spark 集群环境

### 5.2 nop-stream 适合的场景

- **流处理为主**: 实时数据监控
- **复杂状态管理**: 需要 Exactly-once 语义
- **CEP 场景**: 复杂事件模式匹配

### 5.3 nop-metadata 的选择

| 场景 | 推荐方案 |
|------|---------|
| 简单质量检查 | 复用 nop-batch（参考 Griffin 的 Measure 模式） |
| 实时质量监控 | 复用 nop-stream + CEP |
| 数据剖析 | 复用 nop-batch（定时收集统计信息） |

**关键决策**: nop-metadata 不需要自己实现流批引擎，而是**复用 nop-stream 和 nop-batch**，专注于元数据模型和业务逻辑。
